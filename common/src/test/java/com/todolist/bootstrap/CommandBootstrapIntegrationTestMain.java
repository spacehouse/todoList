package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.task.Task;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 命令系统真实集成自检入口，使用 Brigadier 分发器执行最小可行命令链路。
 */
public final class CommandBootstrapIntegrationTestMain {
    private static final Unsafe UNSAFE = loadUnsafe();
    private static final Path TEST_GAME_DIR = createTestGameDir();
    private static int storageNamespaceCounter;

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandBootstrapIntegrationTestMain() {
    }

    /**
     * 程序入口，执行全部离线命令集成测试。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当任一测试失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        bootstrapEnvironment();
        shouldRegisterTodoAlias();
        shouldExecuteHelpThroughAlias();
        shouldAddPersonalTaskSuccessfully();
        shouldListPersonalTasksWithFilters();
        shouldAddTaskToTeamProjectSuccessfully();
        shouldListTasksByProjectSuccessfully();
        shouldCleanCompletedPersonalTasksInCurrentProjectSuccessfully();
        shouldSetHudVisibilityByCommand();
        shouldRejectInvalidHudValue();
        shouldStarAndUnstarVisibleProject();
        shouldRejectMissingProjectWhenStarring();
        shouldExpireTaskCleanConfirmWithoutPendingRequest();
        shouldRejectInvalidProjectMemberRoleValue();
        shouldRejectEditCommandForViewOnlyPlayer();
        shouldPreserveCommandAccessModeAfterExternalConfigEdit();
        shouldCreateTeamProjectSuccessfully();
        shouldSelectAndListProjectsSuccessfully();
        shouldRenamePersonalProjectSuccessfully();
        shouldEnableTeamProjectMemberCreateSuccessfully();
        shouldAddProjectMemberSuccessfully();
        shouldPromoteProjectMemberRoleSuccessfully();
        shouldRemoveProjectMemberSuccessfully();
        shouldRemovePersonalProjectAfterConfirmSuccessfully();
        shouldRequestJoinProjectSuccessfully();
        shouldApproveJoinRequestSuccessfully();
        shouldDenyJoinRequestSuccessfully();
        shouldRejectMissingTeamTaskWhenClaiming();
        shouldRejectClaimingAssignedTeamTaskWithoutPermission();
        shouldRejectAbandoningOthersTaskWithoutPermission();
        shouldRejectInvalidAssignmentTargetByProject();
        shouldClaimUnassignedTeamTaskSuccessfully();
        shouldAbandonSelfAssignedTeamTaskSuccessfully();
        shouldAssignTeamTaskToMemberSuccessfully();
        shouldCompleteAssignedTeamTaskSuccessfully();
        shouldRemoveTeamTaskSuccessfully();
    }

    /**
     * 初始化测试运行环境，确保配置路径与全局单例可在离线环境下使用。
     */
    private static void bootstrapEnvironment() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
        DataPathProvider.setGameDirSupplier(() -> TEST_GAME_DIR);
        DataPathProvider.resetStorageNamespace();
        TodoListCommon.init();
        ModConfig.getInstance().setCommandAccessMode(ModConfig.CommandAccessMode.FULL);
        ProjectPackets.setServerPacketSender((player, channelId, buf) -> {
        });
    }

    /**
     * 校验根命令与别名均已注册到真实 Brigadier 分发器。
     */
    private static void shouldRegisterTodoAlias() {
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        assertNotNull(dispatcher.getRoot().getChild("todo"), "todo 根命令未注册");
        assertNotNull(dispatcher.getRoot().getChild("todolist"), "todolist 别名未注册");
    }

    /**
     * 校验别名命令可以真实执行并输出帮助信息。
     */
    private static void shouldExecuteHelpThroughAlias() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, null);
        int result = dispatcher.execute("todolist help", source);
        assertEquals(1, result, "别名 help 命令执行结果错误");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.help.title", "help 命令未输出标题");
    }

    /**
     * 校验 HUD 显隐命令会真实修改服务端记录的 HUD 状态。
     */
    /**
     * 校验个人任务新增命令会写入标题、描述、标签和当前激活项目。
     */
    private static void shouldAddPersonalTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000032", "personal-add-user", false);
        addOwnedPersonalProject(player, "focus-project", "Focus Project");
        ProjectPackets.setActiveProjectId(player, "focus-project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task add \"Write docs\" \"Prepare release notes\" docs,release", createSource(0, player));
        assertEquals(1, result, "task add 成功时应返回成功");

        Task savedTask = findPersonalTaskByTitle(player, "Write docs");
        assertEquals("Prepare release notes", savedTask.getDescription(), "task add 未写入描述");
        assertEquals("focus-project", savedTask.getProjectId(), "task add 未绑定当前激活项目");
        assertEquals(Boolean.TRUE, savedTask.getTags().contains("docs"), "task add 未写入 docs 标签");
        assertEquals(Boolean.TRUE, savedTask.getTags().contains("release"), "task add 未写入 release 标签");
    }

    /**
     * 校验个人任务列表命令支持按状态、优先级和文本进行筛选。
     */
    private static void shouldListPersonalTasksWithFilters() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000033", "personal-list-user", false);
        savePersonalTasks(
                player,
                createPersonalTask("Release report", true, Task.Priority.HIGH, null),
                createPersonalTask("Draft changelog", false, Task.Priority.MEDIUM, null),
                createPersonalTask("Fix typo", true, Task.Priority.LOW, null)
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task list completed high report", source);
        assertEquals(1, result, "task list 过滤成功时应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.task.list.summary", "task list 未输出汇总消息");
        assertContainsText(source.getSuccessMessages(), "Release report", "task list 未输出匹配任务标题");
        assertNotContainsText(source.getSuccessMessages(), "Draft changelog", "task list 不应输出未匹配的未完成任务");
        assertNotContainsText(source.getSuccessMessages(), "Fix typo", "task list 不应输出未匹配的低优先级任务");
    }

    /**
     * 校验 addp 可以向团队项目新增任务并写入团队任务存储。
     */
    private static void shouldAddTaskToTeamProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000034", "manager-addp", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "team-add-project", "Team Add");
        project.setAllowMemberCreate(true);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task addp team-add-project \"Team task\" \"Prepare sync\" sync,meeting", createSource(0, manager, server));
        assertEquals(1, result, "task addp 成功时应返回成功");

        Task savedTask = loadTeamTaskByTitle("Team task");
        assertEquals(Task.Scope.TEAM, savedTask.getScope(), "task addp 未写入团队任务作用域");
        assertEquals("team-add-project", savedTask.getProjectId(), "task addp 未写入项目 ID");
        assertEquals(Boolean.TRUE, savedTask.getTags().contains("sync"), "task addp 未写入 sync 标签");
        assertEquals(Boolean.TRUE, savedTask.getTags().contains("meeting"), "task addp 未写入 meeting 标签");
    }

    /**
     * 校验 listp 仅输出指定项目下符合筛选条件的任务。
     */
    private static void shouldListTasksByProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000035", "project-list-user", false);
        addOwnedPersonalProject(owner, "focus-list-project", "Focus List Project");
        addOwnedPersonalProject(owner, "other-list-project", "Other List Project");
        savePersonalTasks(
                owner,
                createPersonalTask("Focus draft", false, Task.Priority.MEDIUM, "focus-list-project"),
                createPersonalTask("Focus done", true, Task.Priority.HIGH, "focus-list-project"),
                createPersonalTask("Other task", false, Task.Priority.MEDIUM, "other-list-project")
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, owner);

        int result = dispatcher.execute("todo task listp focus-list-project incomplete medium Focus", source);
        assertEquals(1, result, "task listp 过滤成功时应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.task.project.list.summary", "task listp 未输出项目汇总消息");
        assertContainsText(source.getSuccessMessages(), "Focus draft", "task listp 未输出匹配的项目任务");
        assertNotContainsText(source.getSuccessMessages(), "Focus done", "task listp 不应输出已完成任务");
        assertNotContainsText(source.getSuccessMessages(), "Other task", "task listp 不应输出其他项目任务");
    }

    /**
     * 校验 clean confirm 成功后只会清理当前项目下匹配状态的个人任务。
     */
    private static void shouldCleanCompletedPersonalTasksInCurrentProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000036", "clean-current-user", false);
        addOwnedPersonalProject(player, "clean-project", "Clean Project");
        addOwnedPersonalProject(player, "keep-project", "Keep Project");
        ProjectPackets.setActiveProjectId(player, "clean-project");
        savePersonalTasks(
                player,
                createPersonalTask("Completed in current", true, Task.Priority.MEDIUM, "clean-project"),
                createPersonalTask("Incomplete in current", false, Task.Priority.MEDIUM, "clean-project"),
                createPersonalTask("Completed elsewhere", true, Task.Priority.MEDIUM, "keep-project")
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack requestSource = createSource(0, player);

        int requestResult = dispatcher.execute("todo task clean personal current completed", requestSource);
        assertEquals(1, requestResult, "task clean 请求确认时应返回成功");
        assertContainsMessageKey(requestSource.getSuccessMessages(), "command.todolist.task.clean.confirm_hint", "task clean 未输出确认提示");

        int confirmResult = dispatcher.execute("todo task clean confirm", createSource(0, player));
        assertEquals(1, confirmResult, "task clean confirm 成功时应返回成功");

        List<Task> tasks = loadPersonalTasks(player);
        assertEquals(2, tasks.size(), "task clean confirm 后剩余任务数量不正确");
        assertNotNull(findPersonalTaskByTitle(player, "Incomplete in current"), "task clean 不应删除当前项目中的未完成任务");
        assertNotNull(findPersonalTaskByTitle(player, "Completed elsewhere"), "task clean 不应删除其他项目中的任务");
        assertNull(findPersonalTaskByTitleOrNull(player, "Completed in current"), "task clean 应删除当前项目中的已完成任务");
    }

    private static void shouldSetHudVisibilityByCommand() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000011", "hud-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int offResult = dispatcher.execute("todo hud set off", createSource(0, player));
        assertEquals(1, offResult, "hud set off 应返回成功");
        assertEquals(Boolean.FALSE, ProjectPackets.isHudVisible(player), "hud set off 未关闭 HUD");

        int onResult = dispatcher.execute("todo hud set on", createSource(0, player));
        assertEquals(1, onResult, "hud set on 应返回成功");
        assertEquals(Boolean.TRUE, ProjectPackets.isHudVisible(player), "hud set on 未开启 HUD");
    }

    /**
     * 校验 HUD 显式开关命令会正确拒绝非法输入。
     */
    private static void shouldRejectInvalidHudValue() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000012", "hud-invalid", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo hud set maybe", source);
        assertEquals(0, result, "非法 HUD 值应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.hud.invalid_value", "非法 HUD 值未返回正确错误键");
    }

    /**
     * 校验项目星标与取消星标命令会真实修改 HUD 星标项目列表。
     */
    private static void shouldStarAndUnstarVisibleProject() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000013", "star-user", false);
        addOwnedPersonalProject(player, "alpha-project", "Alpha Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int starResult = dispatcher.execute("todo project star alpha-project", createSource(0, player));
        assertEquals(1, starResult, "项目星标命令应返回成功");
        assertListEquals(List.of("alpha-project"), ProjectPackets.getHudStarredProjectIds(player), "项目星标状态未写入");

        int unstarResult = dispatcher.execute("todo project unstar alpha-project", createSource(0, player));
        assertEquals(1, unstarResult, "项目取消星标命令应返回成功");
        assertListEquals(List.of(), ProjectPackets.getHudStarredProjectIds(player), "项目取消星标后列表未清空");
    }

    /**
     * 校验星标不存在项目时会返回标准 not_found 错误。
     */
    private static void shouldRejectMissingProjectWhenStarring() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000014", "star-missing", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project star missing-project", source);
        assertEquals(0, result, "星标不存在项目应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.star.not_found", "不存在项目的星标错误键不正确");
    }

    /**
     * 校验未发起确认请求时直接执行 clean confirm 会返回超时错误。
     */
    private static void shouldExpireTaskCleanConfirmWithoutPendingRequest() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000015", "clean-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task clean confirm", source);
        assertEquals(0, result, "未确认的 clean confirm 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.clean.confirm_expired", "clean confirm 超时错误键不正确");
    }

    /**
     * 校验项目成员角色命令会拒绝非法角色值。
     */
    private static void shouldRejectInvalidProjectMemberRoleValue() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000016", "manager-user", false);
        addTeamProjectForMemberRole(player, "team-alpha", "Team Alpha", "00000000-0000-0000-0000-000000000116");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project member role team-alpha 00000000-0000-0000-0000-000000000116 manager", source);
        assertEquals(0, result, "非法成员角色应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.role.invalid_value", "非法成员角色错误键不正确");
    }

    /**
     * 校验 VIEW_ONLY 模式下的非 OP 玩家会被编辑类命令拒绝。
     */
    private static void shouldRejectEditCommandForViewOnlyPlayer() throws Exception {
        resetState(ModConfig.CommandAccessMode.VIEW_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000017", "view-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task clean confirm", source);
        assertEquals(0, result, "VIEW_ONLY 下的编辑命令应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.permission_denied", "权限拒绝错误键不正确");
    }

    /**
     * 校验个人项目可以成功改名。
     */
    /**
     * 校验 project create 可以创建团队项目并将执行者登记为项目经理。
     */
    private static void shouldCreateTeamProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000037", "manager-create-project", false);
        TestMinecraftServer server = createServer(manager);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo project create team Team Created Project", createSource(0, manager, server));
        assertEquals(1, result, "project create team 成功时应返回成功");

        Project createdProject = findProjectByName("Team Created Project");
        assertNotNull(createdProject, "project create 未创建目标项目");
        assertEquals(Project.Scope.TEAM, createdProject.getScope(), "project create 未写入团队项目作用域");
        assertEquals(manager.getStringUUID(), createdProject.getOwnerUuid(), "project create 未写入项目拥有者");
        assertEquals(Project.ProjectRole.PROJECT_MANAGER, createdProject.getMemberRole(manager.getStringUUID()), "project create 未将执行者设置为项目经理");
    }

    /**
     * 校验 project select 与 project list 可以联动输出当前项目和星标项目。
     */
    private static void shouldSelectAndListProjectsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000038", "project-select-user", false);
        addOwnedPersonalProject(owner, "selected-project", "Selected Project");
        addOwnedPersonalProject(owner, "starred-project", "Starred Project");
        ProjectPackets.setHudStarredProjectIds(owner, List.of("starred-project"));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int selectResult = dispatcher.execute("todo project select selected-project", createSource(0, owner));
        assertEquals(1, selectResult, "project select 成功时应返回成功");
        assertEquals("selected-project", ProjectPackets.getActiveProjectId(owner), "project select 未写入激活项目");

        CapturingCommandSourceStack currentSource = createSource(0, owner);
        int currentResult = dispatcher.execute("todo project list current", currentSource);
        assertEquals(1, currentResult, "project list current 成功时应返回成功");
        assertContainsText(currentSource.getSuccessMessages(), "Selected Project", "project list current 未输出当前项目");

        CapturingCommandSourceStack starSource = createSource(0, owner);
        int starResult = dispatcher.execute("todo project list star", starSource);
        assertEquals(1, starResult, "project list star 成功时应返回成功");
        assertContainsText(starSource.getSuccessMessages(), "Starred Project", "project list star 未输出星标项目");
        assertNotContainsText(starSource.getSuccessMessages(), "Selected Project", "project list star 不应输出未星标项目");
    }

    private static void shouldRenamePersonalProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000027", "owner-rename", false);
        addOwnedPersonalProject(owner, "rename-project", "Before Rename");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo project rename rename-project After Rename", createSource(0, owner));
        assertEquals(1, result, "project rename 成功时应返回成功");
        assertEquals("After Rename", TodoListCommon.getProjectManager().getProject("rename-project").getName(), "project rename 未更新项目名称");
    }

    /**
     * 校验团队项目可以成功开启成员可创建任务开关。
     */
    private static void shouldEnableTeamProjectMemberCreateSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000028", "manager-member-create", false);
        TestMinecraftServer server = createServer(manager);
        addTeamProject(manager, "member-create-project", "Member Create");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo project member-create member-create-project on", createSource(0, manager, server));
        assertEquals(1, result, "project member-create 成功时应返回成功");
        assertEquals(Boolean.TRUE, TodoListCommon.getProjectManager().getProject("member-create-project").isAllowMemberCreate(), "member-create 未开启 allowMemberCreate");
    }

    /**
     * 校验项目经理可以成功添加项目成员。
     */
    private static void shouldAddProjectMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000029", "manager-add-member", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000129", "member-add-member", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "member-add-project", "Member Add");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo project member add member-add-project member-add-member", createSource(0, manager, server));
        assertEquals(1, result, "project member add 成功时应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(member.getStringUUID()), "member add 未将成员加入项目");
    }

    /**
     * 校验项目经理可以成功提升成员角色。
     */
    private static void shouldPromoteProjectMemberRoleSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000030", "manager-role-member", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "member-role-project", "Member Role");
        String memberUuid = "00000000-0000-0000-0000-000000000130";
        project.addMember(memberUuid, Project.ProjectRole.MEMBER, "member-role-user");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo project member role member-role-project " + memberUuid + " lead", createSource(0, manager, server));
        assertEquals(1, result, "project member role 成功时应返回成功");
        assertEquals(Project.ProjectRole.LEAD, project.getMemberRole(memberUuid), "member role 未将成员提升为 lead");
    }

    /**
     * 校验项目经理可以成功移除项目成员。
     */
    private static void shouldRemoveProjectMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000031", "manager-remove-member", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "member-remove-project", "Member Remove");
        String memberUuid = "00000000-0000-0000-0000-000000000131";
        project.addMember(memberUuid, Project.ProjectRole.MEMBER, "member-remove-user");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo project member remove member-remove-project " + memberUuid, createSource(0, manager, server));
        assertEquals(1, result, "project member remove 成功时应返回成功");
        assertEquals(null, project.getMemberRole(memberUuid), "member remove 未移除目标成员");
    }

    /**
     * 重置测试用的全局状态，避免不同场景之间相互污染。
     *
     * @param accessMode 当前场景要使用的命令权限模式
     */
    private static void resetState(ModConfig.CommandAccessMode accessMode) {
        storageNamespaceCounter++;
        DataPathProvider.setStorageNamespace("command-test-" + storageNamespaceCounter);
        TodoListCommon.init();
        ModConfig.getInstance().setCommandAccessMode(accessMode);
    }

    /**
     * 创建真实 Brigadier 分发器并注册命令树。
     *
     * @return 已注册命令树的分发器
     */
    private static CommandDispatcher<CommandSourceStack> createDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandBootstrap.register(dispatcher, null, null);
        return dispatcher;
    }

    /**
     * 创建带消息捕获能力的命令源，便于断言反馈内容。
     *
     * @param permissionLevel 命令源权限等级
     * @param player 绑定的假玩家，可为 null
     * @return 捕获型命令源
     */
    private static CapturingCommandSourceStack createSource(int permissionLevel, ServerPlayer player) {
        return new CapturingCommandSourceStack(permissionLevel, player);
    }

    /**
     * 创建带最小服务端上下文的命令源，供团队任务成功分支复用。
     *
     * @param permissionLevel 命令权限等级
     * @param player 当前执行玩家
     * @param server 最小服务端桩对象
     * @return 捕获型命令源
     */
    private static CapturingCommandSourceStack createSource(int permissionLevel, ServerPlayer player, MinecraftServer server) {
        return new CapturingCommandSourceStack(permissionLevel, player, server);
    }

    /**
     * 创建最小假玩家对象，供命令集成测试复用。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @param operator 是否视为 OP
     * @return 已初始化好的假玩家
     */
    private static TestServerPlayer createPlayer(String uuid, String name, boolean operator) {
        try {
            TestServerPlayer player = (TestServerPlayer) UNSAFE.allocateInstance(TestServerPlayer.class);
            player.testUuid = UUID.fromString(uuid);
            player.testName = name;
            player.testOperator = operator;
            player.testClientMessages = new ArrayList<>();
            return player;
        } catch (InstantiationException e) {
            throw new IllegalStateException("无法创建测试玩家实例", e);
        }
    }

    /**
     * 向项目管理器注入一个当前玩家可见的个人项目。
     *
     * @param owner 玩家
     * @param projectId 项目 ID
     * @param projectName 项目名称
     */
    private static void addOwnedPersonalProject(TestServerPlayer owner, String projectId, String projectName) {
        Project project = new Project(projectName, Project.Scope.PERSONAL, owner.getStringUUID());
        project.setId(projectId);
        TodoListCommon.getProjectManager().addProject(project);
    }

    /**
     * 向项目管理器注入一个可用于成员角色测试的团队项目。
     *
     * @param owner 项目经理
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @param memberUuid 待调整角色的成员 UUID
     */
    private static void addTeamProjectForMemberRole(TestServerPlayer owner, String projectId, String projectName, String memberUuid) {
        Project project = addTeamProject(owner, projectId, projectName);
        project.addMember(memberUuid, Project.ProjectRole.MEMBER, "member-user");
    }

    /**
     * 向项目管理器注入一个团队项目，并将 owner 设为项目经理。
     *
     * @param owner 项目经理
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @return 已加入项目管理器的项目对象
     */
    private static Project addTeamProject(TestServerPlayer owner, String projectId, String projectName) {
        Project project = new Project(projectName, Project.Scope.TEAM, owner.getStringUUID());
        project.setId(projectId);
        project.addMember(owner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, owner.getName().getString());
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 将指定团队任务写入测试用团队任务存储。
     *
     * @param tasks 需要保存的团队任务
     * @throws Exception 保存失败时抛出异常
     */
    private static void saveTeamTasks(Task... tasks) throws Exception {
        TodoListCommon.getTaskStorage().saveTeamTasks(List.of(tasks));
    }

    /**
     * 创建一条绑定到指定团队项目的测试任务。
     *
     * @param projectId 项目 ID
     * @param title 任务标题
     * @return 已初始化的团队任务
     */
    /**
     * 将指定个人任务写入测试玩家的个人任务存储。
     *
     * @param player 测试玩家
     * @param tasks 需要保存的个人任务
     * @throws Exception 保存失败时抛出异常
     */
    private static void savePersonalTasks(TestServerPlayer player, Task... tasks) throws Exception {
        TodoListCommon.getTaskStorage().savePlayerTasks(player.getUUID(), List.of(tasks));
    }

    /**
     * 读取测试玩家的个人任务列表。
     *
     * @param player 测试玩家
     * @return 个人任务列表
     * @throws Exception 读取失败时抛出异常
     */
    private static List<Task> loadPersonalTasks(TestServerPlayer player) throws Exception {
        return TodoListCommon.getTaskStorage().loadPlayerTasks(player.getUUID());
    }

    /**
     * 创建一条可用于个人任务命令测试的任务数据。
     *
     * @param title 任务标题
     * @param completed 是否完成
     * @param priority 任务优先级
     * @param projectId 关联项目 ID
     * @return 初始化后的测试任务
     */
    private static Task createPersonalTask(String title, boolean completed, Task.Priority priority, String projectId) {
        Task task = new Task(title, "");
        task.setCompleted(completed);
        task.setPriority(priority);
        task.setScope(Task.Scope.PERSONAL);
        task.setProjectId(projectId);
        return task;
    }

    private static Task createTeamTask(String projectId, String title) {
        Task task = new Task(title, "");
        task.setScope(Task.Scope.TEAM);
        task.setProjectId(projectId);
        return task;
    }

    /**
     * 从团队任务存储中读取指定任务。
     *
     * @param taskId 任务 ID
     * @return 命中的任务对象
     * @throws Exception 读取失败时抛出异常
     */
    private static Task loadTeamTaskById(String taskId) throws Exception {
        for (Task task : TodoListCommon.getTaskStorage().loadTeamTasks()) {
            if (task != null && taskId.equals(task.getId())) {
                return task;
            }
        }
        throw new AssertionError("未找到预期的团队任务，taskId=" + taskId);
    }

    /**
     * 创建团队任务成功链路所需的最小服务端桩对象。
     *
     * @param onlinePlayers 当前在线玩家列表
     * @return 可用于命令测试的最小服务端
     */
    /**
     * 从团队任务存储中按标题读取任务。
     *
     * @param title 任务标题
     * @return 命中的团队任务
     * @throws Exception 读取失败时抛出异常
     */
    private static Task loadTeamTaskByTitle(String title) throws Exception {
        for (Task task : TodoListCommon.getTaskStorage().loadTeamTasks()) {
            if (task != null && title.equals(task.getTitle())) {
                return task;
            }
        }
        throw new AssertionError("未找到预期的团队任务，title=" + title);
    }

    /**
     * 从个人任务存储中按标题读取任务，不存在时返回 null。
     *
     * @param player 测试玩家
     * @param title 任务标题
     * @return 命中的个人任务，未命中时返回 null
     * @throws Exception 读取失败时抛出异常
     */
    private static Task findPersonalTaskByTitleOrNull(TestServerPlayer player, String title) throws Exception {
        for (Task task : loadPersonalTasks(player)) {
            if (task != null && title.equals(task.getTitle())) {
                return task;
            }
        }
        return null;
    }

    /**
     * 从个人任务存储中按标题读取任务，不存在时抛出断言异常。
     *
     * @param player 测试玩家
     * @param title 任务标题
     * @return 命中的个人任务
     * @throws Exception 读取失败时抛出异常
     */
    private static Task findPersonalTaskByTitle(TestServerPlayer player, String title) throws Exception {
        Task task = findPersonalTaskByTitleOrNull(player, title);
        if (task == null) {
            throw new AssertionError("未找到预期的个人任务，title=" + title);
        }
        return task;
    }

    /**
     * 按项目名称查找刚创建的项目。
     *
     * @param projectName 项目名称
     * @return 命中的项目，未命中时返回 null
     */
    private static Project findProjectByName(String projectName) {
        for (Project project : TodoListCommon.getProjectManager().getAllProjects()) {
            if (project != null && projectName.equals(project.getName())) {
                return project;
            }
        }
        return null;
    }

    private static TestMinecraftServer createServer(TestServerPlayer... onlinePlayers) {
        try {
            TestMinecraftServer server = (TestMinecraftServer) UNSAFE.allocateInstance(TestMinecraftServer.class);
            TestPlayerList playerList = (TestPlayerList) UNSAFE.allocateInstance(TestPlayerList.class);
            playerList.players = new ArrayList<>();
            playerList.playersByUuid = new LinkedHashMap<>();
            playerList.playersByName = new LinkedHashMap<>();
            if (onlinePlayers != null) {
                for (TestServerPlayer player : onlinePlayers) {
                    if (player == null) {
                        continue;
                    }
                    playerList.players.add(player);
                    playerList.playersByUuid.put(player.getUUID(), player);
                    playerList.playersByName.put(player.getName().getString(), player);
                }
            }
            server.testPlayerList = playerList;
            return server;
        } catch (InstantiationException e) {
            throw new IllegalStateException("无法创建测试服务端实例", e);
        }
    }

    /**
     * 校验 claimp 在任务不存在时会返回 not_found。
     */
    /**
     * 校验 project remove 二次确认成功后会删除项目并清理同项目任务。
     */
    private static void shouldRemovePersonalProjectAfterConfirmSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000039", "project-remove-user", false);
        addOwnedPersonalProject(owner, "remove-project", "Remove Project");
        savePersonalTasks(
                owner,
                createPersonalTask("Task in removed project", false, Task.Priority.MEDIUM, "remove-project"),
                createPersonalTask("Task to keep", false, Task.Priority.MEDIUM, "keep-project")
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack requestSource = createSource(0, owner);

        int requestResult = dispatcher.execute("todo project remove remove-project", requestSource);
        assertEquals(1, requestResult, "project remove 请求确认时应返回成功");
        assertContainsMessageKey(requestSource.getSuccessMessages(), "command.todolist.project.remove.confirm_hint", "project remove 未输出确认提示");

        int confirmResult = dispatcher.execute("todo project remove confirm", createSource(0, owner));
        assertEquals(1, confirmResult, "project remove confirm 成功时应返回成功");
        assertEquals(null, TodoListCommon.getProjectManager().getProject("remove-project"), "project remove confirm 后项目仍然存在");
        assertNull(findPersonalTaskByTitleOrNull(owner, "Task in removed project"), "project remove 未清理被删除项目的任务");
        assertNotNull(findPersonalTaskByTitle(owner, "Task to keep"), "project remove 错误清理了其他项目任务");
    }

    /**
     * 校验 join project 会向项目审批人发送加入申请提示。
     */
    private static void shouldRequestJoinProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000040", "manager-join-request", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000140", "applicant-join-request", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-request-project", "Join Request Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-request-project", createSource(0, applicant, server));
        assertEquals(1, result, "join project 成功时应返回成功");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.request_received", "join project 未通知审批人");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "join project 未向申请人回写发送结果");
    }

    /**
     * 校验项目经理可以通过 join accept 批准加入申请。
     */
    private static void shouldApproveJoinRequestSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000041", "manager-join-approve", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000141", "applicant-join-approve", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-approve-project", "Join Approve Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join accept join-approve-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(1, result, "join accept 成功时应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "join accept 未将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "join accept 未通知申请人审批通过");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.approved", "join accept 未通知审批人审批结果");
    }

    /**
     * 校验项目经理可以通过 join deny 拒绝加入申请且不会把申请人加入项目。
     */
    private static void shouldDenyJoinRequestSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000042", "manager-join-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000142", "applicant-join-deny", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-deny-project", "Join Deny Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join deny join-deny-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(1, result, "join deny 成功时应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "join deny 不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "join deny 未通知申请人审批拒绝");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.rejected", "join deny 未通知审批人审批结果");
    }

    private static void shouldRejectMissingTeamTaskWhenClaiming() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000018", "claim-missing", false);
        addTeamProject(manager, "team-claim-missing", "Claim Missing");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager);

        int result = dispatcher.execute("todo task claimp team-claim-missing missing-task", source);
        assertEquals(0, result, "claimp 对不存在任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.done.not_found", "claimp 不存在任务错误键不正确");
    }

    /**
     * 校验普通成员不能领取已分配给他人的团队任务。
     */
    private static void shouldRejectClaimingAssignedTeamTaskWithoutPermission() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000019", "manager-claim", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000119", "member-claim", false);
        Project project = addTeamProject(manager, "team-claim-denied", "Claim Denied");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Assigned Task");
        task.setAssigneeUuid("00000000-0000-0000-0000-000000000219");
        task.setAssigneeName("other-user");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member);

        int result = dispatcher.execute("todo task claimp team-claim-denied " + task.getId(), source);
        assertEquals(0, result, "普通成员领取他人任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "claimp 权限拒绝错误键不正确");
    }

    /**
     * 校验普通成员不能放弃他人已分配的团队任务。
     */
    private static void shouldRejectAbandoningOthersTaskWithoutPermission() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000020", "manager-abandon", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000120", "member-abandon", false);
        Project project = addTeamProject(manager, "team-abandon-denied", "Abandon Denied");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Abandon Task");
        task.setAssigneeUuid("00000000-0000-0000-0000-000000000220");
        task.setAssigneeName("other-user");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member);

        int result = dispatcher.execute("todo task abandonp team-abandon-denied " + task.getId(), source);
        assertEquals(0, result, "普通成员放弃他人任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "abandonp 权限拒绝错误键不正确");
    }

    /**
     * 校验 assignp 会拒绝项目外目标成员。
     */
    private static void shouldRejectInvalidAssignmentTargetByProject() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000021", "manager-assign", false);
        Project project = addTeamProject(manager, "team-assign-invalid", "Assign Invalid");
        Task task = createTeamTask(project.getId(), "Assignable Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager);

        int result = dispatcher.execute("todo task assignp team-assign-invalid " + task.getId() + " ghost-user", source);
        assertEquals(0, result, "assignp 对非法目标应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.assign.invalid_target", "assignp 非法目标错误键不正确");
    }

    /**
     * 校验普通成员可以成功领取未分配的团队任务。
     */
    private static void shouldClaimUnassignedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000022", "manager-claim-ok", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000122", "member-claim-ok", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-claim-success", "Claim Success");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Claimable Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task claimp team-claim-success " + task.getId(), createSource(0, member, server));
        assertEquals(1, result, "claimp 成功领取应返回成功");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(member.getStringUUID(), savedTask.getAssigneeUuid(), "claimp 未写入 assigneeUuid");
        assertEquals(member.getName().getString(), savedTask.getAssigneeName(), "claimp 未写入 assigneeName");
    }

    /**
     * 校验普通成员可以成功放弃分配给自己的团队任务。
     */
    private static void shouldAbandonSelfAssignedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000023", "manager-abandon-ok", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000123", "member-abandon-ok", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-abandon-success", "Abandon Success");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Self Assigned Task");
        task.setAssigneeUuid(member.getStringUUID());
        task.setAssigneeName(member.getName().getString());
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task abandonp team-abandon-success " + task.getId(), createSource(0, member, server));
        assertEquals(1, result, "abandonp 成功放弃应返回成功");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(null, savedTask.getAssigneeUuid(), "abandonp 后 assigneeUuid 应清空");
        assertEquals(null, savedTask.getAssigneeName(), "abandonp 后 assigneeName 应清空");
    }

    /**
     * 校验项目经理可以成功将团队任务指派给项目成员。
     */
    private static void shouldAssignTeamTaskToMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000024", "manager-assign-ok", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000124", "member-assign-ok", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-assign-success", "Assign Success");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Assignable Team Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task assignp team-assign-success " + task.getId() + " " + member.getStringUUID(), createSource(0, manager, server));
        assertEquals(1, result, "assignp 成功指派应返回成功");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(member.getStringUUID(), savedTask.getAssigneeUuid(), "assignp 未写入目标成员 UUID");
        assertEquals(member.getName().getString(), savedTask.getAssigneeName(), "assignp 未写入目标成员名称");
    }

    /**
     * 校验已领取任务的成员可以成功完成团队任务。
     */
    private static void shouldCompleteAssignedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000025", "manager-done-ok", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000125", "member-done-ok", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-done-success", "Done Success");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Completable Team Task");
        task.setAssigneeUuid(member.getStringUUID());
        task.setAssigneeName(member.getName().getString());
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task donep team-done-success " + task.getId(), createSource(0, member, server));
        assertEquals(1, result, "donep 成功完成应返回成功");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(Boolean.TRUE, savedTask.isCompleted(), "donep 未将任务标记为已完成");
    }

    /**
     * 校验项目经理可以成功删除团队任务。
     */
    private static void shouldRemoveTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000026", "manager-remove-ok", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "team-remove-success", "Remove Success");
        Task task = createTeamTask(project.getId(), "Removable Team Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task removep team-remove-success " + task.getId(), createSource(0, manager, server));
        assertEquals(1, result, "removep 成功删除应返回成功");
        assertEquals(0, TodoListCommon.getTaskStorage().loadTeamTasks().size(), "removep 后团队任务列表应为空");
    }

    /**
     * 创建命令测试专用的临时游戏目录。
     *
     * @return 临时目录路径
     */
    /**
     * 鏍￠獙鍚姩鍚庢墜鍔ㄤ慨鏀?commandAccessMode 閰嶇疆锛屼笉浼氳鍏朵粬閰嶇疆淇濆瓨鍐欏洖榛樿鍊笺€?
     */
    private static void shouldPreserveCommandAccessModeAfterExternalConfigEdit() throws Exception {
        writeConfigCommandAccessMode(ModConfig.CommandAccessMode.OP_ONLY);
        ModConfig.load();
        assertEquals(ModConfig.CommandAccessMode.OP_ONLY, ModConfig.getInstance().getCommandAccessMode(), "鍒濆鍔犺浇鐨?commandAccessMode 搴斾负 OP_ONLY");

        writeConfigCommandAccessMode(ModConfig.CommandAccessMode.VIEW_ONLY);
        ModConfig.getInstance().setHudShowWhenEmpty(true);

        assertEquals(ModConfig.CommandAccessMode.VIEW_ONLY, ModConfig.getInstance().getCommandAccessMode(), "鍏朵粬閰嶇疆淇濆瓨涓嶅簲瑕嗙洊鎵嬪姩淇敼鐨?commandAccessMode");
        assertContainsText(readConfigFileLines(), "\"commandAccessMode\": \"VIEW_ONLY\"", "閰嶇疆鏂囦欢涓殑 commandAccessMode 搴斾繚鎸?VIEW_ONLY");
    }

    /**
     * 灏嗘祴璇曢厤缃枃浠朵腑鐨?commandAccessMode 鏇存柊涓烘寚瀹氬€硷紝鐢ㄤ簬妯℃嫙鐜╁鍦ㄦ父鎴忓鎵嬪姩缂栬緫閰嶇疆銆?
     *
     * @param accessMode 瑕佸啓鍏ョ殑鍛戒护鏉冮檺妯″紡
     * @throws Exception 璇诲啓閰嶇疆澶辫触鏃舵姏鍑哄紓甯?
     */
    private static void writeConfigCommandAccessMode(ModConfig.CommandAccessMode accessMode) throws Exception {
        String config = Files.readString(getConfigFilePath(), StandardCharsets.UTF_8);
        String updated = config.replaceAll("\"commandAccessMode\"\\s*:\\s*\"[A-Z_]+\"", "\"commandAccessMode\": \"" + accessMode.name() + "\"");
        Files.writeString(getConfigFilePath(), updated, StandardCharsets.UTF_8);
    }

    /**
     * 璇诲彇娴嬭瘯鐜鐨勯厤缃枃浠跺唴瀹癸紝渚夸簬鏂█鍐欑洏缁撴灉銆?
     *
     * @return 閰嶇疆鏂囦欢鍐呭锛堜互涓€涓厓绱犲垪琛ㄨ繑鍥烇紝澶嶇敤宸叉湁鏂█宸ュ叿锛?
     * @throws Exception 璇诲彇澶辫触鏃舵姏鍑哄紓甯?
     */
    private static List<Component> readConfigFileLines() throws Exception {
        return List.of(Component.literal(Files.readString(getConfigFilePath(), StandardCharsets.UTF_8)));
    }

    /**
     * 鑾峰彇鍛戒护娴嬭瘯浣跨敤鐨勯厤缃枃浠惰矾寰勩€?
     *
     * @return 閰嶇疆鏂囦欢璺緞
     */
    private static Path getConfigFilePath() {
        return TEST_GAME_DIR.resolve("config").resolve("todolist.json");
    }

    /**
     * 鍒涘缓鍛戒护娴嬭瘯涓撶敤鐨勪复鏃舵父鎴忕洰褰曘€?
     *
     * @return 涓存椂鐩綍璺緞
     */
    private static Path createTestGameDir() {
        try {
            Path dir = Files.createTempDirectory("todolist-command-tests");
            Files.createDirectories(dir.resolve("config"));
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建命令测试目录", e);
        }
    }

    /**
     * 反射读取 Unsafe，用于无构造创建假玩家实例。
     *
     * @return Unsafe 单例
     */
    private static Unsafe loadUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法访问 Unsafe", e);
        }
    }

    /**
     * 断言消息列表中包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 目标翻译键
     * @param message 失败提示
     */
    private static void assertContainsMessageKey(List<Component> messages, String translationKey, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(translationKey)) {
                return;
            }
        }
        throw new AssertionError(message + " actual=" + messages);
    }

    /**
     * 断言消息列表中包含指定文本片段。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本
     * @param message 失败提示
     */
    private static void assertContainsText(List<Component> messages, String expectedText, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(expectedText)) {
                return;
            }
        }
        throw new AssertionError(message + " actual=" + messages);
    }

    /**
     * 断言消息列表中不包含指定文本片段。
     *
     * @param messages 消息列表
     * @param unexpectedText 不应出现的文本
     * @param message 失败提示
     */
    private static void assertNotContainsText(List<Component> messages, String unexpectedText, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(unexpectedText)) {
                throw new AssertionError(message + " actual=" + messages);
            }
        }
    }

    /**
     * 断言两个对象相等。
     *
     * @param expected 期望值
     * @param actual 实际值
     * @param message 失败提示
     */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    /**
     * 断言两个字符串列表相等。
     *
     * @param expected 期望列表
     * @param actual 实际列表
     * @param message 失败提示
     */
    private static void assertListEquals(List<String> expected, List<String> actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    /**
     * 断言对象非空。
     *
     * @param value 实际对象
     * @param message 失败提示
     */
    private static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言对象为空。
     *
     * @param value 实际对象
     * @param message 失败提示
     */
    private static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " actual=" + value);
        }
    }

    /**
     * 捕获命令反馈消息的最小命令源实现。
     */
    private static final class CapturingCommandSourceStack extends CommandSourceStack {
        private final List<Component> successMessages = new ArrayList<>();
        private final List<Component> failureMessages = new ArrayList<>();

        /**
         * 创建捕获型命令源。
         *
         * @param permissionLevel 命令权限等级
         * @param player 绑定的玩家实体
         */
        private CapturingCommandSourceStack(int permissionLevel, ServerPlayer player) {
            super(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permissionLevel, "command-test", Component.literal("command-test"), null, player);
        }

        /**
         * 创建带最小服务端上下文的捕获型命令源。
         *
         * @param permissionLevel 命令权限等级
         * @param player 绑定的玩家实体
         * @param server 最小服务端桩对象
         */
        private CapturingCommandSourceStack(int permissionLevel, ServerPlayer player, MinecraftServer server) {
            super(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permissionLevel, "command-test", Component.literal("command-test"), server, player);
        }

        /**
         * 捕获成功反馈消息。
         *
         * @param textSupplier 成功消息供应器
         * @param broadcastToOps 是否广播给管理员
         */
        @Override
        public void sendSuccess(Supplier<Component> textSupplier, boolean broadcastToOps) {
            successMessages.add(textSupplier.get());
        }

        /**
         * 捕获失败反馈消息。
         *
         * @param component 失败消息
         */
        @Override
        public void sendFailure(Component component) {
            failureMessages.add(component);
        }

        /**
         * 获取成功消息列表。
         *
         * @return 成功消息列表
         */
        private List<Component> getSuccessMessages() {
            return successMessages;
        }

        /**
         * 获取失败消息列表。
         *
         * @return 失败消息列表
         */
        private List<Component> getFailureMessages() {
            return failureMessages;
        }
    }

    /**
     * 最小假玩家实现，仅覆盖当前集成测试需要访问的玩家信息。
     */
    private static class TestServerPlayer extends ServerPlayer {
        private UUID testUuid;
        private String testName;
        private boolean testOperator;
        private List<Component> testClientMessages;

        /**
         * 构造方法仅用于满足编译要求，运行时通过 Unsafe 绕过。
         */
        private TestServerPlayer() {
            super(null, null, null);
            throw new UnsupportedOperationException("请通过 createPlayer 创建测试玩家");
        }

        /**
         * 返回测试玩家 UUID。
         *
         * @return 玩家 UUID
         */
        @Override
        public UUID getUUID() {
            return testUuid;
        }

        /**
         * 返回测试玩家 UUID 字符串。
         *
         * @return 玩家 UUID 字符串
         */
        @Override
        public String getStringUUID() {
            return testUuid == null ? "" : testUuid.toString();
        }

        /**
         * 返回测试玩家显示名称。
         *
         * @return 玩家名称组件
         */
        @Override
        public Component getName() {
            return Component.literal(testName == null ? "" : testName);
        }

        /**
         * 返回测试玩家权限结果。
         *
         * @param level 请求的权限等级
         * @return 是否具备权限
         */
        @Override
        public boolean hasPermissions(int level) {
            return testOperator || level <= 0;
        }

        /**
         * 捕获发送到客户端的提示消息，供加入项目审批链路断言使用。
         *
         * @param component 客户端消息
         * @param actionBar 是否显示为 action bar
         */
        @Override
        public void displayClientMessage(Component component, boolean actionBar) {
            if (testClientMessages == null) {
                testClientMessages = new ArrayList<>();
            }
            testClientMessages.add(component);
        }

        /**
         * 返回捕获到的客户端消息列表。
         *
         * @return 客户端消息列表
         */
        private List<Component> getClientMessages() {
            if (testClientMessages == null) {
                testClientMessages = new ArrayList<>();
            }
            return testClientMessages;
        }
    }

    /**
     * 最小测试服务端实现，仅覆盖团队任务成功分支需要访问的能力。
     */
    private static class TestMinecraftServer extends MinecraftServer {
        private TestPlayerList testPlayerList;

        /**
         * 构造方法仅用于满足编译要求，运行时通过 Unsafe 绕过。
         */
        private TestMinecraftServer() {
            super(null, null, null, null, Proxy.NO_PROXY, null, null, null);
            throw new UnsupportedOperationException("请通过 createServer 创建测试服务端");
        }

        /**
         * 返回测试用玩家列表。
         *
         * @return 玩家列表桩对象
         */
        @Override
        public PlayerList getPlayerList() {
            return testPlayerList;
        }

        /**
         * 立即执行提交到服务端事件循环的任务，避免测试依赖异步调度。
         *
         * @param runnable 待执行任务
         */
        @Override
        public void execute(Runnable runnable) {
            if (runnable != null) {
                runnable.run();
            }
        }

        /**
         * 与 execute 保持一致，立即执行可选任务。
         *
         * @param runnable 待执行任务
         */
        @Override
        public void executeIfPossible(Runnable runnable) {
            execute(runnable);
        }

        /**
         * 标记测试服务端为专用服务端，避免团队项目被视为单人模式不可用。
         *
         * @return 始终返回 true
         */
        @Override
        public boolean isDedicatedServer() {
            return true;
        }

        /**
         * 标记测试服务端未发布局域网。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean isPublished() {
            return false;
        }

        /**
         * 初始化服务端，测试环境无需执行。
         *
         * @return 始终返回 true
         */
        @Override
        protected boolean initServer() {
            return true;
        }

        /**
         * 返回 OP 权限等级。
         *
         * @return 固定返回 4
         */
        @Override
        public int getOperatorUserPermissionLevel() {
            return 4;
        }

        /**
         * 返回函数编译权限等级。
         *
         * @return 固定返回 2
         */
        @Override
        public int getFunctionCompilationLevel() {
            return 2;
        }

        /**
         * 指示是否广播 RCON 输出。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        /**
         * 填充服务端系统报告，测试环境直接返回入参。
         *
         * @param report 系统报告
         * @return 原始系统报告
         */
        @Override
        public net.minecraft.SystemReport fillServerSystemReport(net.minecraft.SystemReport report) {
            return report;
        }

        /**
         * 返回限速阈值。
         *
         * @return 固定返回 0
         */
        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        /**
         * 标记测试服务端不使用 epoll。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean isEpollEnabled() {
            return false;
        }

        /**
         * 标记命令方块能力关闭。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean isCommandBlockEnabled() {
            return false;
        }

        /**
         * 指示管理员广播开关。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        /**
         * 判断是否为单人房主，测试环境始终返回 false。
         *
         * @param profile 玩家档案
         * @return 始终返回 false
         */
        @Override
        public boolean isSingleplayerOwner(com.mojang.authlib.GameProfile profile) {
            return false;
        }
    }

    /**
     * 最小测试玩家列表实现，支持按 UUID、名称和在线列表查询。
     */
    private static class TestPlayerList extends PlayerList {
        private List<ServerPlayer> players;
        private Map<UUID, ServerPlayer> playersByUuid;
        private Map<String, ServerPlayer> playersByName;

        /**
         * 构造方法仅用于满足编译要求，运行时通过 Unsafe 绕过。
         */
        private TestPlayerList() {
            super(null, null, null, 0);
            throw new UnsupportedOperationException("请通过 createServer 创建测试玩家列表");
        }

        /**
         * 返回在线玩家列表。
         *
         * @return 在线玩家列表
         */
        @Override
        public List<ServerPlayer> getPlayers() {
            return players == null ? List.of() : players;
        }

        /**
         * 按 UUID 查找在线玩家。
         *
         * @param uuid 玩家 UUID
         * @return 匹配的玩家，找不到时返回 null
         */
        @Override
        public ServerPlayer getPlayer(UUID uuid) {
            return playersByUuid == null ? null : playersByUuid.get(uuid);
        }

        /**
         * 按名称查找在线玩家。
         *
         * @param name 玩家名称
         * @return 匹配的玩家，找不到时返回 null
         */
        @Override
        public ServerPlayer getPlayerByName(String name) {
            return playersByName == null ? null : playersByName.get(name);
        }
    }
}
