package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.project.ProjectSaveDebouncer;
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
        shouldCompletePersonalTaskSuccessfully();
        shouldRemovePersonalTaskSuccessfully();
        shouldRejectMissingPersonalTaskWhenRemoving();
        shouldListCompletedPersonalTasksSuccessfully();
        shouldAddTaskToTeamProjectSuccessfully();
        shouldRejectTeamTaskAddForMemberWhenMemberCreateDisabledSuccessfully();
        shouldAllowTeamTaskAddForMemberWhenMemberCreateEnabledSuccessfully();
        shouldListTasksByProjectSuccessfully();
        shouldListCompletedTeamTasksSuccessfully();
        shouldPaginatePersonalTaskListWithMoreAndPrevSuccessfully();
        shouldPaginateProjectTaskListWithMoreSuccessfully();
        shouldKeepLatestTaskListSessionBetweenPersonalAndProjectQueriesSuccessfully();
        shouldResetTaskPaginationAfterSwitchingQuerySuccessfully();
        shouldSwitchTaskPaginationSessionBetweenTeamProjectsSuccessfully();
        shouldRejectTaskMoreWithoutListSession();
        shouldRejectTaskPrevOnFirstPage();
        shouldRejectTaskMoreAfterLastPage();
        shouldCleanCompletedPersonalTasksInCurrentProjectSuccessfully();
        shouldCleanCompletedTasksInCurrentTeamProjectSuccessfully();
        shouldCleanCompletedTasksInAllTeamProjectsSuccessfully();
        shouldCleanCompletedTasksInStarredTeamProjectsSuccessfully();
        shouldRejectCleaningStarredTeamTasksWithForeignProjectSuccessfully();
        shouldSetHudVisibilityByCommand();
        shouldSyncHudVisibilityFromClientPacketSuccessfully();
        shouldRejectInvalidHudValue();
        shouldStarAndUnstarVisibleProject();
        shouldReturnAlreadyStarredForStarredProjectSuccessfully();
        shouldReturnNotStarredForUnstarredProjectSuccessfully();
        shouldRejectMissingProjectWhenStarring();
        shouldExpireTaskCleanConfirmWithoutPendingRequest();
        shouldRejectProjectRemoveConfirmWithoutPendingRequest();
        shouldRequestProjectRemoveConfirmSuccessfully();
        shouldRejectInvalidProjectMemberRoleValue();
        shouldRejectEditCommandForViewOnlyPlayer();
        shouldHideTaskAddCommandForViewOnlyPlayer();
        shouldRejectProjectRenameForViewOnlyPlayer();
        shouldAllowViewCommandForViewOnlyPlayer();
        shouldRejectEditCommandForNonOpPlayerWhenOpOnly();
        shouldAllowEditCommandForOperatorWhenOpOnly();
        shouldShowCurrentCommandAccessModeSuccessfully();
        shouldSetCommandAccessModeByAdminCommandSuccessfully();
        shouldRejectInvalidCommandAccessModeValue();
        shouldPreserveCommandAccessModeAfterExternalConfigEdit();
        shouldCreateTeamProjectSuccessfully();
        shouldListTeamProjectForOtherPlayerSuccessfully();
        shouldRestrictTeamCommandsInSingleplayerSuccessfully();
        shouldAllowTeamCommandsWhenLanPublishedForOtherPlayerSuccessfully();
        shouldHideCurrentTeamProjectWhenSwitchingToSingleplayerSuccessfully();
        shouldRejectSelectingTeamProjectInSingleplayerSuccessfully();
        shouldSelectAndListProjectsSuccessfully();
        shouldRejectMissingProjectWhenSelectingSuccessfully();
        shouldShowEmptyCurrentProjectListSuccessfully();
        shouldShowEmptyStarProjectListSuccessfully();
        shouldRenamePersonalProjectSuccessfully();
        shouldEnableTeamProjectMemberCreateSuccessfully();
        shouldDisableTeamProjectMemberCreateSuccessfully();
        shouldRejectProjectMemberCreateForPersonalProjectSuccessfully();
        shouldRejectProjectMemberCreateForMissingProjectSuccessfully();
        shouldRejectProjectMemberCreateForInvalidValueSuccessfully();
        shouldRejectProjectMemberCreateForRegularMemberSuccessfully();
        shouldAddProjectMemberSuccessfully();
        shouldRejectProjectMemberAddWithInvalidTargetSuccessfully();
        shouldRejectProjectMemberAddWhenTargetAlreadyExistsSuccessfully();
        shouldRejectProjectMemberAddForRegularMemberSuccessfully();
        shouldPromoteProjectMemberRoleSuccessfully();
        shouldRejectProjectMemberRoleWhenTargetMissingSuccessfully();
        shouldRejectLeadChangingProjectManagerRoleSuccessfully();
        shouldRejectLeadChangingOwnRoleSuccessfully();
        shouldRejectRegularMemberChangingOwnRoleSuccessfully();
        shouldRejectProjectMemberRoleForRegularMemberSuccessfully();
        shouldRemoveProjectMemberSuccessfully();
        shouldRejectProjectMemberRemoveWhenTargetMissingSuccessfully();
        shouldRejectLeadRemovingProjectManagerSuccessfully();
        shouldRejectManagerRemovingSelfSuccessfully();
        shouldRejectLeadRemovingSelfSuccessfully();
        shouldRejectProjectMemberRemoveForRegularMemberSuccessfully();
        shouldRemovePersonalProjectAfterConfirmSuccessfully();
        shouldClearCurrentProjectStateAfterProjectRemovalSuccessfully();
        shouldClearStarredProjectStateAfterProjectRemovalSuccessfully();
        shouldClearTeamProjectStateForOnlineMembersAfterRemovalSuccessfully();
        shouldKeepCurrentAndStarredProjectStateAfterProjectRenameSuccessfully();
        shouldSanitizeDirtyProjectPlayerStateOnJoinSuccessfully();
        shouldSanitizeOfflineMemberProjectStateAfterTeamRemovalSuccessfully();
        shouldRestoreTeamProjectStateDifferentlyAcrossServerModesSuccessfully();
        shouldRestoreTeamProjectStateWhenPublishingLanWithoutReconnectSuccessfully();
        shouldSeedMissingProjectStateFromRequestSyncSuccessfully();
        shouldKeepHiddenTeamStarredStateAfterSingleplayerProjectSelectionSuccessfully();
        shouldClearCurrentAndStarredStateWhenSameProjectRemovedSuccessfully();
        shouldKeepCurrentAndStarredStateWhenSameProjectRenamedSuccessfully();
        shouldKeepProjectStateButRestrictTasksAfterMemberRemovalSuccessfully();
        shouldKeepProjectStateAfterMemberRoleDemotionSuccessfully();
        shouldSanitizeMixedPersonalAndTeamProjectStateAcrossServerModesSuccessfully();
        shouldKeepProjectListOrderingStableAcrossListModesSuccessfully();
        shouldIsolatePendingProjectRemoveConfirmationsBetweenPlayersSuccessfully();
        shouldIsolatePendingTaskCleanConfirmationsBetweenPlayersSuccessfully();
        shouldListProjectsAfterReloadSuccessfully();
        shouldListCurrentAndStarredProjectsAfterReloadSuccessfully();
        shouldSanitizeCurrentAndStarredTeamProjectsAfterReloadIntoSingleplayerSuccessfully();
        shouldListPersonalTasksAfterReloadSuccessfully();
        shouldListTeamTasksAfterReloadSuccessfully();
        shouldPersistTeamProjectMemberCreateSettingAfterReloadSuccessfully();
        shouldPersistProjectMemberRolesAndPermissionsAfterReloadSuccessfully();
        shouldRequestJoinProjectSuccessfully();
        shouldRejectDuplicateJoinProjectRequestSuccessfully();
        shouldRejectJoinDecisionWithoutPendingRequestSuccessfully();
        shouldRejectJoinProjectInSingleplayerSuccessfully();
        shouldApproveJoinRequestAfterApplicantReconnectSuccessfully();
        shouldRejectJoinDecisionAfterProjectRemovalSuccessfully();
        shouldRejectJoinDenyAfterProjectRemovalSuccessfully();
        shouldClearPendingJoinRequestWhenDeletingProjectViaPacketSuccessfully();
        shouldApproveJoinRequestSuccessfully();
        shouldRejectRepeatedJoinAcceptAfterApprovalSuccessfully();
        shouldRejectRepeatedJoinDenyAfterDenialSuccessfully();
        shouldKeepJoinDecisionsIsolatedAcrossProjectsSuccessfully();
        shouldListTeamTasksForJoinedMemberSuccessfully();
        shouldPaginateTeamTaskListForJoinedMemberSuccessfully();
        shouldPaginateTeamTaskListWithPrevForJoinedMemberSuccessfully();
        shouldDenyJoinRequestSuccessfully();
        shouldAllowJoinRequestAgainAfterDenialSuccessfully();
        shouldRejectTeamTaskListAfterJoinDeniedSuccessfully();
        shouldRejectTaskMoreAfterDeniedTeamTaskListWithoutSessionSuccessfully();
        shouldRejectTaskPrevAfterDeniedTeamTaskListWithoutSessionSuccessfully();
        shouldRejectMissingTeamTaskWhenClaiming();
        shouldRejectClaimingAssignedTeamTaskWithoutPermission();
        shouldRejectAbandoningOthersTaskWithoutPermission();
        shouldRejectInvalidAssignmentTargetByProject();
        shouldRejectClaimingAssignedTeamTaskAfterManagerAssignmentSuccessfully();
        shouldAllowManagerAndLeadClaimingUnassignedTeamTasksSuccessfully();
        shouldClaimUnassignedTeamTaskSuccessfully();
        shouldAbandonSelfAssignedTeamTaskSuccessfully();
        shouldAssignTeamTaskToMemberSuccessfully();
        shouldAllowLeadAssigningTeamTaskSuccessfully();
        shouldReassignTeamTaskToAnotherMemberSuccessfully();
        shouldKeepTaskStateConsistentWhenManagerReassignsClaimedTaskSuccessfully();
        shouldKeepIndependentTeamTaskStateAcrossMultipleMemberOperationsSuccessfully();
        shouldRejectAssigningCompletedTeamTaskSuccessfully();
        shouldRejectClaimingCompletedTeamTaskSuccessfully();
        shouldRejectLeadAssigningAndClaimingCompletedTeamTaskSuccessfully();
        shouldRejectCompletingUnassignedTeamTaskForRegularMemberSuccessfully();
        shouldRejectCompletingTaskAssignedToOtherMemberSuccessfully();
        shouldAllowLeadCompletingAssignedTeamTaskSuccessfully();
        shouldReturnAlreadyCompletedForCompletedTeamTaskSuccessfully();
        shouldRejectAbandoningCompletedAssignedTeamTaskSuccessfully();
        shouldCompleteAssignedTeamTaskSuccessfully();
        shouldRejectRemovingTeamTaskForMemberSuccessfully();
        shouldAllowLeadRemovingTeamTaskSuccessfully();
        shouldAllowLeadRemovingTaskAssignedToProjectManagerSuccessfully();
        shouldRemoveCompletedAssignedTeamTaskByManagerSuccessfully();
        shouldRemoveTeamTasksAcrossAssignedAndCompletedMatrixSuccessfully();
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
     * 校验 personal task done 会真实把个人任务标记为完成。
     */
    private static void shouldCompletePersonalTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000135", "personal-done-user", false);
        Task task = createPersonalTask("Done by command", false, Task.Priority.MEDIUM, null);
        savePersonalTasks(player, task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task done " + task.getId(), createSource(0, player));
        assertEquals(1, result, "task done 成功时应返回成功");
        assertEquals(Boolean.TRUE, findPersonalTaskByTitle(player, "Done by command").isCompleted(), "task done 未将任务标记为完成");
    }

    /**
     * 校验 personal task remove 会真实删除个人任务。
     */
    private static void shouldRemovePersonalTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000236", "personal-remove-user", false);
        Task task = createPersonalTask("Remove by command", false, Task.Priority.MEDIUM, null);
        savePersonalTasks(player, task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task remove " + task.getId(), createSource(0, player));
        assertEquals(1, result, "task remove 成功时应返回成功");
        assertNull(findPersonalTaskByTitleOrNull(player, "Remove by command"), "task remove 未删除个人任务");
    }

    /**
     * 校验 personal task remove 对不存在任务会返回 not_found。
     */
    private static void shouldRejectMissingPersonalTaskWhenRemoving() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000237", "personal-remove-missing-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task remove missing-personal-task", source);
        assertEquals(0, result, "删除不存在的个人任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.remove.not_found", "个人 task remove 不存在任务的错误键不正确");
    }

    /**
     * 校验 personal task list completed 只输出已完成任务。
     */
    private static void shouldListCompletedPersonalTasksSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000134", "completed-list-user", false);
        savePersonalTasks(
                player,
                createPersonalTask("Completed report", true, Task.Priority.HIGH, null),
                createPersonalTask("Open draft", false, Task.Priority.MEDIUM, null)
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task list completed", source);
        assertEquals(1, result, "task list completed 成功时应返回成功");
        assertContainsText(source.getSuccessMessages(), "Completed report", "task list completed 未输出已完成任务");
        assertNotContainsText(source.getSuccessMessages(), "Open draft", "task list completed 不应输出未完成任务");
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
    /**
     * 校验普通成员在成员创建关闭时不能新增团队任务。
     */
    private static void shouldRejectTeamTaskAddForMemberWhenMemberCreateDisabledSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000250", "manager-addp-disabled", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000251", "member-addp-disabled", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-addp-disabled", "Team Add Disabled");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        project.setAllowMemberCreate(false);
        TodoListCommon.getProjectManager().updateProject(project);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo task addp team-addp-disabled \"Denied Team Task\" \"x\" denied", source);
        assertEquals(0, result, "成员创建关闭时普通成员 addp 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.add.no_permission_team", "成员创建关闭时 addp 的错误键不正确");
        assertNull(findTeamTaskByTitleOrNull("Denied Team Task"), "失败的 addp 不应写入团队任务");
    }

    /**
     * 校验普通成员在成员创建开启时可以新增团队任务。
     */
    private static void shouldAllowTeamTaskAddForMemberWhenMemberCreateEnabledSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000252", "manager-addp-enabled", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000253", "member-addp-enabled", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-addp-enabled", "Team Add Enabled");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        project.setAllowMemberCreate(true);
        TodoListCommon.getProjectManager().updateProject(project);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task addp team-addp-enabled \"Member Created Team Task\" \"Created by member\" enabled", createSource(0, member, server));
        assertEquals(1, result, "成员创建开启时普通成员 addp 应返回成功");
        Task savedTask = findTeamTaskByTitleOrNull("Member Created Team Task");
        assertNotNull(savedTask, "成员创建开启时 addp 未写入团队任务");
        assertEquals(project.getId(), savedTask.getProjectId(), "成员创建开启时 addp 未绑定正确项目");
    }

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
     * 校验团队任务列表在 completed 过滤下只输出已完成任务。
     */
    private static void shouldListCompletedTeamTasksSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000136", "team-completed-list-user", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "team-completed-list-project", "Team Completed List Project");
        Task completedTask = createTeamTask(project.getId(), "Completed Team Task");
        completedTask.setCompleted(true);
        Task openTask = createTeamTask(project.getId(), "Open Team Task");
        saveTeamTasks(completedTask, openTask);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo task listp team-completed-list-project completed", source);
        assertEquals(1, result, "task listp completed 成功时应返回成功");
        assertContainsText(source.getSuccessMessages(), "Completed Team Task", "task listp completed 未输出已完成团队任务");
        assertNotContainsText(source.getSuccessMessages(), "Open Team Task", "task listp completed 不应输出未完成团队任务");
    }

    /**
     * 校验个人任务列表超过 10 条时可以通过 more/prev 翻页查看。
     */
    private static void shouldPaginatePersonalTaskListWithMoreAndPrevSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000036", "personal-page-user", false);
        savePersonalTasks(player, createPagedPersonalTasks("Personal Page Task ", 12, null));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack firstPageSource = createSource(0, player);
        int firstPageResult = dispatcher.execute("todo task list", firstPageSource);
        assertEquals(1, firstPageResult, "task list 第一页查询应返回成功");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.summary", "task list 第一页未输出汇总消息");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task list 第一页未输出页码状态");
        assertEquals(10, countMessagesContaining(firstPageSource.getSuccessMessages(), "Personal Page Task "), "task list 第一页应显示 10 条任务");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "task list 第一页未输出下一页按钮");

        CapturingCommandSourceStack secondPageSource = createSource(0, player);
        int secondPageResult = dispatcher.execute("todo task more", secondPageSource);
        assertEquals(1, secondPageResult, "todo task more 应返回成功");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task more 未输出页码状态");
        assertEquals(2, countMessagesContaining(secondPageSource.getSuccessMessages(), "Personal Page Task "), "task more 第二页应只显示剩余 2 条任务");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.prev_button", "task more 第二页未输出上一页按钮");

        CapturingCommandSourceStack previousPageSource = createSource(0, player);
        int previousPageResult = dispatcher.execute("todo task prev", previousPageSource);
        assertEquals(1, previousPageResult, "todo task prev 应返回成功");
        assertContainsMessageKey(previousPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task prev 未输出页码状态");
        assertEquals(10, countMessagesContaining(previousPageSource.getSuccessMessages(), "Personal Page Task "), "task prev 返回后应再次显示 10 条任务");
        assertContainsMessageKey(previousPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "task prev 返回第一页后未输出下一页按钮");
    }

    /**
     * 校验按项目查看任务时，超过 10 条也可以继续翻页查看剩余任务。
     */
    private static void shouldPaginateProjectTaskListWithMoreSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000037", "project-page-user", false);
        addOwnedPersonalProject(owner, "page-project", "Page Project");
        savePersonalTasks(owner, createPagedPersonalTasks("Project Page Task ", 12, "page-project"));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack firstPageSource = createSource(0, owner);
        int firstPageResult = dispatcher.execute("todo task listp page-project", firstPageSource);
        assertEquals(1, firstPageResult, "task listp 第一页查询应返回成功");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.project.list.summary", "task listp 第一页未输出项目汇总消息");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task listp 第一页未输出页码状态");
        assertEquals(10, countMessagesContaining(firstPageSource.getSuccessMessages(), "Project Page Task "), "task listp 第一页应显示 10 条任务");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "task listp 第一页未输出下一页按钮");

        CapturingCommandSourceStack secondPageSource = createSource(0, owner);
        int secondPageResult = dispatcher.execute("todo task more", secondPageSource);
        assertEquals(1, secondPageResult, "task listp 后的 task more 应返回成功");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task listp 后的 task more 未输出页码状态");
        assertEquals(2, countMessagesContaining(secondPageSource.getSuccessMessages(), "Project Page Task "), "task listp 后的 task more 应只显示剩余 2 条任务");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.prev_button", "task listp 第二页未输出上一页按钮");
    }

    /**
     * 校验最近一次列表查询会覆盖旧的翻页会话，避免 task list 与 task listp 串线。
     */
    private static void shouldKeepLatestTaskListSessionBetweenPersonalAndProjectQueriesSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000137", "task-session-user", false);
        addOwnedPersonalProject(owner, "session-project", "Session Project");
        List<Task> tasks = new ArrayList<>();
        tasks.addAll(List.of(createPagedPersonalTasks("Personal Session Task ", 12, null)));
        tasks.addAll(List.of(createPagedPersonalTasks("Project Session Task ", 12, "session-project")));
        savePersonalTasks(owner, tasks.toArray(new Task[0]));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task list", createSource(0, owner)), "task list 应返回成功");

        CapturingCommandSourceStack projectSource = createSource(0, owner);
        int projectResult = dispatcher.execute("todo task listp session-project", projectSource);
        assertEquals(1, projectResult, "task listp 应返回成功");
        assertEquals(10, countMessagesContaining(projectSource.getSuccessMessages(), "Project Session Task "), "task listp 第一页应显示 10 条项目任务");

        CapturingCommandSourceStack nextPageSource = createSource(0, owner);
        int nextPageResult = dispatcher.execute("todo task more", nextPageSource);
        assertEquals(1, nextPageResult, "最新会话的 task more 应返回成功");
        assertEquals(2, countMessagesContaining(nextPageSource.getSuccessMessages(), "Project Session Task "), "task more 应继续项目列表会话的第二页");
        assertNotContainsText(nextPageSource.getSuccessMessages(), "Personal Session Task", "task more 不应串到旧的个人列表会话");
    }

    /**
     * 校验切换筛选条件后会从第一页重新开始翻页。
     */
    private static void shouldResetTaskPaginationAfterSwitchingQuerySuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000148", "task-filter-reset-user", false);
        List<Task> tasks = new ArrayList<>();
        tasks.add(createPersonalTask("Completed Reset Task 1", true, Task.Priority.MEDIUM, null));
        tasks.add(createPersonalTask("Completed Reset Task 2", true, Task.Priority.MEDIUM, null));
        tasks.add(createPersonalTask("Completed Reset Task 3", true, Task.Priority.MEDIUM, null));
        tasks.addAll(List.of(createPagedPersonalTasks("Open Reset Task ", 9, null)));
        savePersonalTasks(player, tasks.toArray(new Task[0]));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task list", createSource(0, player)), "task list 应返回成功");
        assertEquals(1, dispatcher.execute("todo task more", createSource(0, player)), "第二页 task more 应返回成功");

        CapturingCommandSourceStack completedSource = createSource(0, player);
        int completedResult = dispatcher.execute("todo task list completed", completedSource);
        assertEquals(1, completedResult, "切换到 completed 过滤后应返回成功");
        assertEquals(3, countMessagesContaining(completedSource.getSuccessMessages(), "Completed Reset Task "), "切换过滤后应重新回到第一页并只显示已完成任务");
        assertNotContainsText(completedSource.getSuccessMessages(), "Open Reset Task", "切换过滤后不应继续沿用旧查询结果");

        CapturingCommandSourceStack moreSource = createSource(0, player);
        int moreResult = dispatcher.execute("todo task more", moreSource);
        assertEquals(0, moreResult, "切换过滤后 task more 应基于新结果判断页码");
        assertContainsMessageKey(moreSource.getFailureMessages(), "command.todolist.task.list.page.already_last", "切换过滤后的 task more 错误键不正确");
    }

    /**
     * 校验未先执行 task list/listp 时，task more 会返回无会话错误。
     */
    /**
     * 校验两个团队项目之间切换 listp 会覆盖旧翻页会话，后续 more/prev 只跟随最新项目。
     */
    private static void shouldSwitchTaskPaginationSessionBetweenTeamProjectsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000282", "team-session-switch-manager", false);
        TestMinecraftServer server = createServer(manager);
        Project alphaProject = addTeamProject(manager, "team-session-alpha", "Team Session Alpha");
        Project betaProject = addTeamProject(manager, "team-session-beta", "Team Session Beta");
        List<Task> tasks = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            tasks.add(createTeamTask(alphaProject.getId(), "Alpha Session Task " + index));
            tasks.add(createTeamTask(betaProject.getId(), "Beta Session Task " + index));
        }
        saveTeamTasks(tasks.toArray(Task[]::new));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack alphaFirstPageSource = createSource(0, manager, server);
        assertEquals(1, dispatcher.execute("todo task listp " + alphaProject.getId(), alphaFirstPageSource), "第一个团队项目 listp 应返回成功");
        assertEquals(10, countMessagesContaining(alphaFirstPageSource.getSuccessMessages(), "Alpha Session Task "), "第一个团队项目第一页应显示 10 条任务");

        CapturingCommandSourceStack alphaSecondPageSource = createSource(0, manager, server);
        assertEquals(1, dispatcher.execute("todo task more", alphaSecondPageSource), "第一个团队项目 task more 应返回成功");
        assertEquals(2, countMessagesContaining(alphaSecondPageSource.getSuccessMessages(), "Alpha Session Task "), "第一个团队项目第二页应只显示 2 条任务");

        CapturingCommandSourceStack betaFirstPageSource = createSource(0, manager, server);
        assertEquals(1, dispatcher.execute("todo task listp " + betaProject.getId(), betaFirstPageSource), "切换到第二个团队项目 listp 应返回成功");
        assertEquals(10, countMessagesContaining(betaFirstPageSource.getSuccessMessages(), "Beta Session Task "), "第二个团队项目第一页应显示 10 条任务");
        assertNotContainsText(betaFirstPageSource.getSuccessMessages(), "Alpha Session Task", "切换项目后的第一页不应残留旧项目任务");

        CapturingCommandSourceStack betaSecondPageSource = createSource(0, manager, server);
        assertEquals(1, dispatcher.execute("todo task more", betaSecondPageSource), "第二个团队项目 task more 应返回成功");
        assertEquals(2, countMessagesContaining(betaSecondPageSource.getSuccessMessages(), "Beta Session Task "), "第二个团队项目第二页应只显示 2 条任务");
        assertNotContainsText(betaSecondPageSource.getSuccessMessages(), "Alpha Session Task", "切换项目后的 task more 不应串到旧项目");

        CapturingCommandSourceStack betaPrevSource = createSource(0, manager, server);
        assertEquals(1, dispatcher.execute("todo task prev", betaPrevSource), "第二个团队项目 task prev 应返回成功");
        assertEquals(10, countMessagesContaining(betaPrevSource.getSuccessMessages(), "Beta Session Task "), "第二个团队项目 task prev 应回到第一页");
        assertNotContainsText(betaPrevSource.getSuccessMessages(), "Alpha Session Task", "切换项目后的 task prev 不应串到旧项目");
    }

    private static void shouldRejectTaskMoreWithoutListSession() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000138", "page-no-session-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task more", source);
        assertEquals(0, result, "无分页会话时 task more 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.list.page.no_session", "task more 无会话错误键不正确");
    }

    /**
     * 校验第一页继续执行 task prev 会返回已经在第一页的错误。
     */
    private static void shouldRejectTaskPrevOnFirstPage() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000139", "page-first-user", false);
        savePersonalTasks(player, createPagedPersonalTasks("Prev Page Task ", 12, null));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int firstPageResult = dispatcher.execute("todo task list", createSource(0, player));
        assertEquals(1, firstPageResult, "task list 第一页查询应返回成功");

        CapturingCommandSourceStack source = createSource(0, player);
        int result = dispatcher.execute("todo task prev", source);
        assertEquals(0, result, "第一页继续 task prev 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.list.page.already_first", "task prev 第一页错误键不正确");
    }

    /**
     * 校验最后一页继续执行 task more 会返回已经在最后一页的错误。
     */
    private static void shouldRejectTaskMoreAfterLastPage() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000141", "page-last-user", false);
        savePersonalTasks(player, createPagedPersonalTasks("Last Page Task ", 12, null));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int firstPageResult = dispatcher.execute("todo task list", createSource(0, player));
        assertEquals(1, firstPageResult, "task list 第一页查询应返回成功");
        int secondPageResult = dispatcher.execute("todo task more", createSource(0, player));
        assertEquals(1, secondPageResult, "task more 第二页查询应返回成功");

        CapturingCommandSourceStack source = createSource(0, player);
        int result = dispatcher.execute("todo task more", source);
        assertEquals(0, result, "最后一页继续 task more 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.list.page.already_last", "task more 最后一页错误键不正确");
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

    /**
     * 校验 team current 分支会清理当前团队项目中的已完成任务。
     */
    private static void shouldCleanCompletedTasksInCurrentTeamProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000154", "clean-team-current-user", false);
        TestMinecraftServer server = createServer(manager);
        Project currentProject = addTeamProject(manager, "clean-team-current-project", "Clean Team Current Project");
        Project otherProject = addTeamProject(manager, "clean-team-other-project", "Clean Team Other Project");
        Task completedInCurrent = createTeamTask(currentProject.getId(), "Completed in current team");
        completedInCurrent.setCompleted(true);
        Task incompleteInCurrent = createTeamTask(currentProject.getId(), "Incomplete in current team");
        Task completedElsewhere = createTeamTask(otherProject.getId(), "Completed in other team");
        completedElsewhere.setCompleted(true);
        saveTeamTasks(completedInCurrent, incompleteInCurrent, completedElsewhere);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project select " + currentProject.getId(), createSource(0, manager, server)), "team current clean 前的 project select 应返回成功");
        int requestResult = dispatcher.execute("todo task clean team current completed", createSource(0, manager, server));
        assertEquals(1, requestResult, "team current clean 请求确认时应返回成功");
        int confirmResult = dispatcher.execute("todo task clean confirm", createSource(0, manager, server));
        assertEquals(1, confirmResult, "team current clean confirm 应返回成功");

        assertNull(findTeamTaskByTitleOrNull("Completed in current team"), "team current clean 应删除当前团队项目中的已完成任务");
        assertNotNull(loadTeamTaskByTitle("Incomplete in current team"), "team current clean 不应删除当前团队项目中的未完成任务");
        assertNotNull(loadTeamTaskByTitle("Completed in other team"), "team current clean 不应删除其他团队项目中的任务");
    }

    /**
     * 校验 team all 分支会清理项目经理拥有的所有团队项目中的已完成任务。
     */
    private static void shouldCleanCompletedTasksInAllTeamProjectsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000155", "clean-team-all-user", false);
        TestMinecraftServer server = createServer(manager);
        Project alphaProject = addTeamProject(manager, "clean-team-all-alpha", "Clean Team All Alpha");
        Project betaProject = addTeamProject(manager, "clean-team-all-beta", "Clean Team All Beta");
        Task alphaDone = createTeamTask(alphaProject.getId(), "Alpha done team task");
        alphaDone.setCompleted(true);
        Task betaDone = createTeamTask(betaProject.getId(), "Beta done team task");
        betaDone.setCompleted(true);
        Task betaOpen = createTeamTask(betaProject.getId(), "Beta open team task");
        saveTeamTasks(alphaDone, betaDone, betaOpen);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int requestResult = dispatcher.execute("todo task clean team all completed", createSource(0, manager, server));
        assertEquals(1, requestResult, "team all clean 请求确认时应返回成功");
        int confirmResult = dispatcher.execute("todo task clean confirm", createSource(0, manager, server));
        assertEquals(1, confirmResult, "team all clean confirm 应返回成功");

        assertNull(findTeamTaskByTitleOrNull("Alpha done team task"), "team all clean 应删除 alpha 项目中的已完成任务");
        assertNull(findTeamTaskByTitleOrNull("Beta done team task"), "team all clean 应删除 beta 项目中的已完成任务");
        assertNotNull(loadTeamTaskByTitle("Beta open team task"), "team all clean 不应删除未完成任务");
    }

    /**
     * 校验 team star 分支只会清理星标团队项目中的已完成任务。
     */
    private static void shouldCleanCompletedTasksInStarredTeamProjectsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000156", "clean-team-star-user", false);
        TestMinecraftServer server = createServer(manager);
        Project starredProject = addTeamProject(manager, "clean-team-star-project", "Clean Team Star Project");
        Project unstarredProject = addTeamProject(manager, "clean-team-unstarred-project", "Clean Team Unstarred Project");
        ProjectPackets.setHudStarredProjectIds(manager, List.of(starredProject.getId()));
        Task starredDone = createTeamTask(starredProject.getId(), "Starred done team task");
        starredDone.setCompleted(true);
        Task starredOpen = createTeamTask(starredProject.getId(), "Starred open team task");
        Task unstarredDone = createTeamTask(unstarredProject.getId(), "Unstarred done team task");
        unstarredDone.setCompleted(true);
        saveTeamTasks(starredDone, starredOpen, unstarredDone);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int requestResult = dispatcher.execute("todo task clean team star completed", createSource(0, manager, server));
        assertEquals(1, requestResult, "team star clean 请求确认时应返回成功");
        int confirmResult = dispatcher.execute("todo task clean confirm", createSource(0, manager, server));
        assertEquals(1, confirmResult, "team star clean confirm 应返回成功");

        assertNull(findTeamTaskByTitleOrNull("Starred done team task"), "team star clean 应删除星标团队项目中的已完成任务");
        assertNotNull(loadTeamTaskByTitle("Starred open team task"), "team star clean 不应删除星标团队项目中的未完成任务");
        assertNotNull(loadTeamTaskByTitle("Unstarred done team task"), "team star clean 不应删除未星标团队项目中的任务");
    }

    /**
     * 校验 team star 分支在星标里混入无权限团队项目时会拒绝整次清理。
     */
    private static void shouldRejectCleaningStarredTeamTasksWithForeignProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000159", "clean-team-star-denied-user", false);
        TestServerPlayer otherOwner = createPlayer("00000000-0000-0000-0000-000000000160", "clean-team-star-other-owner", false);
        TestMinecraftServer server = createServer(manager, otherOwner);
        Project ownedProject = addTeamProject(manager, "owned-star-team-project", "Owned Star Team Project");
        Project foreignProject = addTeamProject(otherOwner, "foreign-star-team-project", "Foreign Star Team Project");
        ProjectPackets.setHudStarredProjectIds(manager, List.of(ownedProject.getId(), foreignProject.getId()));
        Task ownedTask = createTeamTask(ownedProject.getId(), "Owned starred done task");
        ownedTask.setCompleted(true);
        Task foreignTask = createTeamTask(foreignProject.getId(), "Foreign starred done task");
        foreignTask.setCompleted(true);
        saveTeamTasks(ownedTask, foreignTask);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo task clean team star completed", source);
        assertEquals(0, result, "混入无权限星标团队项目时 clean 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.clean.team_permission_denied", "无权限星标团队项目的 clean 错误键不正确");
        assertNotNull(loadTeamTaskByTitle("Owned starred done task"), "失败的 team star clean 不应删除有权限项目任务");
        assertNotNull(loadTeamTaskByTitle("Foreign starred done task"), "失败的 team star clean 不应删除无权限项目任务");
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
     * 校验客户端上报 HUD 显隐状态后，服务端会持久化并同步相同状态。
     */
    private static void shouldSyncHudVisibilityFromClientPacketSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000307", "hud-packet-user", false);
        TestMinecraftServer server = createServer(player);
        List<Boolean> syncedStates = new ArrayList<>();
        ProjectPackets.setServerPacketSender((target, channelId, buf) -> {
            if (!ProjectPackets.SYNC_HUD_VISIBILITY_ID.equals(channelId)) {
                return;
            }
            net.minecraft.network.FriendlyByteBuf copy = new net.minecraft.network.FriendlyByteBuf(buf.copy());
            syncedStates.add(copy.readBoolean());
        });

        net.minecraft.network.FriendlyByteBuf packet = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        packet.writeBoolean(false);
        ProjectPackets.onSetHudVisibilityPacket(server, player, packet);

        assertEquals(Boolean.FALSE, ProjectPackets.isHudVisible(player), "客户端上报 HUD 隐藏后服务端状态未更新");
        ProjectPlayerStateStorage.ProjectPlayerState storedState = new ProjectPlayerStateStorage().loadPlayerState(player.getUUID());
        assertEquals(Boolean.FALSE, storedState.isHudVisible(), "客户端上报 HUD 隐藏后持久化状态未更新");
        assertEquals(Boolean.TRUE, syncedStates.contains(Boolean.FALSE), "客户端上报 HUD 隐藏后未向客户端回推同步结果");
        ProjectPackets.setServerPacketSender((target, channelId, buf) -> {
        });
    }

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
    /**
     * 校验重复星标已星标项目会返回 already_starred。
     */
    private static void shouldReturnAlreadyStarredForStarredProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000279", "star-already-user", false);
        addOwnedPersonalProject(player, "already-starred-project", "Already Starred Project");
        ProjectPackets.setHudStarredProjectIds(player, List.of("already-starred-project"));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project star already-starred-project", source);
        assertEquals(1, result, "重复星标项目应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.project.star.already_starred", "重复星标项目的提示键不正确");
        assertListEquals(List.of("already-starred-project"), ProjectPackets.getHudStarredProjectIds(player), "重复星标不应改动星标列表");
    }

    /**
     * 校验取消未星标项目会返回 not_starred。
     */
    private static void shouldReturnNotStarredForUnstarredProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000280", "unstar-empty-user", false);
        addOwnedPersonalProject(player, "not-starred-project", "Not Starred Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project unstar not-starred-project", source);
        assertEquals(1, result, "取消未星标项目应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.project.unstar.not_starred", "取消未星标项目的提示键不正确");
        assertListEquals(List.of(), ProjectPackets.getHudStarredProjectIds(player), "取消未星标项目不应写入星标列表");
    }

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
     * 校验 project remove 首次执行时只输出确认提示，不会立即删除项目。
     */
    private static void shouldRequestProjectRemoveConfirmSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000014", "project-remove-request-user", false);
        addOwnedPersonalProject(owner, "remove-request-project", "Remove Request Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, owner);

        int result = dispatcher.execute("todo project remove remove-request-project", source);
        assertEquals(1, result, "project remove 请求确认时应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.project.remove.confirm_hint", "project remove 请求确认未输出提示");
        assertNotNull(TodoListCommon.getProjectManager().getProject("remove-request-project"), "project remove 请求确认阶段不应立即删除项目");
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
     * 校验未发起确认请求时直接执行 project remove confirm 会返回超时错误。
     */
    private static void shouldRejectProjectRemoveConfirmWithoutPendingRequest() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000017", "project-remove-confirm-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project remove confirm", source);
        assertEquals(0, result, "未确认的 project remove confirm 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.remove.confirm_expired", "project remove confirm 超时错误键不正确");
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
     * 校验 VIEW_ONLY 模式下的非 OP 玩家仍可执行查看类命令。
     */
    private static void shouldHideTaskAddCommandForViewOnlyPlayer() throws Exception {
        resetState(ModConfig.CommandAccessMode.VIEW_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000018", "view-hidden-add-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        try {
            dispatcher.execute("todo task add \"Denied task\" \"x\" test", createSource(0, player));
            throw new AssertionError("VIEW_ONLY 涓嬬殑 task add 涓嶅簲瑙ｆ瀽鎴愬姛");
        } catch (CommandSyntaxException exception) {
            assertContainsText(
                    List.of(Component.literal(exception.getMessage())),
                    "Incorrect argument for command",
                    "VIEW_ONLY 涓嬬殑 task add 搴旇〃鐜颁负 Brigadier 瀛愬懡浠や笉鍙"
            );
        }
        assertNull(findPersonalTaskByTitleOrNull(player, "Denied task"), "VIEW_ONLY 涓嬬殑 task add 涓嶅簲鍐欏叆浠诲姟");
    }

    /**
     * 鏍￠獙 VIEW_ONLY 妯″紡涓嬬殑 project rename 浼氳鏉冮檺鎷掔粷銆?
     */
    private static void shouldRejectProjectRenameForViewOnlyPlayer() throws Exception {
        resetState(ModConfig.CommandAccessMode.VIEW_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000120", "view-rename-user", false);
        addOwnedPersonalProject(player, "view-only-rename-project", "Before View Rename");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project rename view-only-rename-project After View Rename", source);
        assertEquals(0, result, "VIEW_ONLY 涓嬬殑 project rename 搴旇繑鍥炲け璐?");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.permission_denied", "VIEW_ONLY 涓嬬殑 project rename 鏉冮檺鎷掔粷閿欒閿笉姝ｇ‘");
        assertEquals("Before View Rename", TodoListCommon.getProjectManager().getProject("view-only-rename-project").getName(), "VIEW_ONLY 涓嬬殑 project rename 涓嶅簲淇敼椤圭洰鍚嶇О");
    }

    /**
     * 鏍￠獙 VIEW_ONLY 妯″紡涓嬬殑闈?OP 鐜╁浠嶅彲鎵ц鏌ョ湅绫诲懡浠ゃ€?     */
    private static void shouldAllowViewCommandForViewOnlyPlayer() throws Exception {
        resetState(ModConfig.CommandAccessMode.VIEW_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000117", "view-only-list-user", false);
        savePersonalTasks(player, createPersonalTask("Visible task", false, Task.Priority.MEDIUM, null));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task list", source);
        assertEquals(1, result, "VIEW_ONLY 下的查看命令应返回成功");
        assertContainsText(source.getSuccessMessages(), "Visible task", "VIEW_ONLY 下的 task list 未输出任务");
    }

    /**
     * 校验 OP_ONLY 模式下的非 OP 玩家会被编辑命令拒绝。
     */
    private static void shouldRejectEditCommandForNonOpPlayerWhenOpOnly() throws Exception {
        resetState(ModConfig.CommandAccessMode.OP_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000118", "op-only-denied-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task clean confirm", source);
        assertEquals(0, result, "OP_ONLY 下的非 OP 编辑命令应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.permission_denied", "OP_ONLY 下的权限拒绝错误键不正确");
    }

    /**
     * 校验 OP_ONLY 模式下的 OP 仍可执行编辑类命令。
     */
    private static void shouldAllowEditCommandForOperatorWhenOpOnly() throws Exception {
        resetState(ModConfig.CommandAccessMode.OP_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000119", "op-only-operator-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task add \"Allowed task\" \"x\" test", createSource(2, player));
        assertEquals(1, result, "OP_ONLY 下的 OP 编辑命令应返回成功");
        assertNotNull(findPersonalTaskByTitle(player, "Allowed task"), "OP_ONLY 下的 OP 未成功写入任务");
    }

    /**
     * 校验管理员可以查看当前 commandAccessMode。
     */
    private static void shouldShowCurrentCommandAccessModeSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.OP_ONLY);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(2, null);

        int result = dispatcher.execute("todo admin command-access", source);
        assertEquals(1, result, "查看 commandAccessMode 应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.command_access_mode.current", "查看 commandAccessMode 未输出当前模式消息");
    }

    /**
     * 校验管理员可以通过命令切换 commandAccessMode，并同步写入配置文件。
     */
    private static void shouldSetCommandAccessModeByAdminCommandSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.OP_ONLY);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(2, null);

        int result = dispatcher.execute("todo admin command-access view_only", source);
        assertEquals(1, result, "通过命令切换 commandAccessMode 应返回成功");
        assertEquals(ModConfig.CommandAccessMode.VIEW_ONLY, ModConfig.getInstance().getCommandAccessMode(), "commandAccessMode 未被命令切换为 VIEW_ONLY");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.command_access_mode.success", "切换 commandAccessMode 未输出成功消息");
        assertContainsText(readConfigFileLines(), "\"commandAccessMode\": \"VIEW_ONLY\"", "配置文件中的 commandAccessMode 未被写入 VIEW_ONLY");
    }

    /**
     * 校验管理员命令会拒绝非法的 commandAccessMode 值。
     */
    private static void shouldRejectInvalidCommandAccessModeValue() throws Exception {
        resetState(ModConfig.CommandAccessMode.OP_ONLY);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(2, null);

        int result = dispatcher.execute("todo admin command-access guest", source);
        assertEquals(0, result, "非法 commandAccessMode 值应返回失败");
        assertEquals(ModConfig.CommandAccessMode.OP_ONLY, ModConfig.getInstance().getCommandAccessMode(), "非法 commandAccessMode 值不应修改现有配置");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.command_access_mode.invalid_value", "非法 commandAccessMode 值未返回正确错误键");
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
     * 校验团队项目创建后，对其他普通玩家在 project list all 中也是可见的。
     */
    private static void shouldListTeamProjectForOtherPlayerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000149", "manager-project-visible", false);
        TestServerPlayer viewer = createPlayer("00000000-0000-0000-0000-000000000150", "viewer-project-visible", false);
        TestMinecraftServer server = createServer(manager, viewer);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int createResult = dispatcher.execute("todo project create team Visible Team Project", createSource(0, manager, server));
        assertEquals(1, createResult, "project create team 应返回成功");

        CapturingCommandSourceStack source = createSource(0, viewer, server);
        int listResult = dispatcher.execute("todo project list all", source);
        assertEquals(1, listResult, "其他玩家执行 project list all 应返回成功");
        assertContainsText(source.getSuccessMessages(), "Visible Team Project", "其他玩家未在 project list all 中看到团队项目");
    }

    /**
     * 校验单机未开局域网时，团队项目相关命令会被正确限制。
     */
    private static void shouldRestrictTeamCommandsInSingleplayerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000151", "singleplayer-team-user", false);
        TestMinecraftServer singleplayerServer = createSingleplayerServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack createCommandSource = createSource(0, player, singleplayerServer);
        int createResult = dispatcher.execute("todo project create team Hidden Team Project", createCommandSource);
        assertEquals(0, createResult, "单机未开局域网时创建团队项目应返回失败");
        assertContainsMessageKey(createCommandSource.getFailureMessages(), "command.todolist.project.create.singleplayer_forbidden", "单机团队项目创建错误键不正确");

        Project hiddenProject = addTeamProject(player, "hidden-team-project", "Hidden Team Project");
        saveTeamTasks(createTeamTask(hiddenProject.getId(), "Hidden Team Task"));

        CapturingCommandSourceStack listProjectSource = createSource(0, player, singleplayerServer);
        int listProjectResult = dispatcher.execute("todo project list all", listProjectSource);
        assertEquals(1, listProjectResult, "单机 project list all 应返回成功");
        assertNotContainsText(listProjectSource.getSuccessMessages(), "Hidden Team Project", "单机未开局域网时不应在 project list all 中显示团队项目");

        CapturingCommandSourceStack listTaskSource = createSource(0, player, singleplayerServer);
        int listTaskResult = dispatcher.execute("todo task listp hidden-team-project", listTaskSource);
        assertEquals(0, listTaskResult, "单机未开局域网时查看团队任务应返回失败");
        assertContainsMessageKey(listTaskSource.getFailureMessages(), "command.todolist.task.project.not_found", "单机团队任务查看错误键不正确");
    }

    /**
     * 校验单机已开局域网后，团队命令会重新可用。
     */
    private static void shouldAllowTeamCommandsWhenLanPublishedSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000161", "lan-team-user", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000162", "lan-team-applicant", false);
        TestMinecraftServer lanServer = createServer(false, true, manager, applicant);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int createResult = dispatcher.execute("todo project create team Lan Team Project", createSource(0, manager, lanServer));
        assertEquals(1, createResult, "开局域网后创建团队项目应返回成功");
        Project createdProject = findProjectByName("Lan Team Project");
        assertNotNull(createdProject, "开局域网后未创建团队项目");

        int selectResult = dispatcher.execute("todo project select " + createdProject.getId(), createSource(0, manager, lanServer));
        assertEquals(1, selectResult, "开局域网后选择团队项目应返回成功");

        int joinResult = dispatcher.execute("todo join project " + createdProject.getId(), createSource(0, applicant, lanServer));
        assertEquals(0, joinResult, "项目经理自己 join project 应返回失败");
    }

    /**
     * 校验团队项目在专用服可作为 current 展示，但切换到单机未开局域网后会被隐藏。
     */
    /**
     * 验证单机已开放局域网后，团队项目对其他玩家可见，且普通玩家可以正常发起加入申请。
     */
    private static void shouldAllowTeamCommandsWhenLanPublishedForOtherPlayerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000164", "lan-team-manager-visible", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000165", "lan-team-applicant-visible", false);
        TestMinecraftServer lanServer = createServer(false, true, manager, applicant);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int createResult = dispatcher.execute("todo project create team Lan Published Team Project", createSource(0, manager, lanServer));
        assertEquals(1, createResult, "开局域网后创建团队项目应返回成功");
        Project createdProject = findProjectByName("Lan Published Team Project");
        assertNotNull(createdProject, "开局域网后未创建团队项目");

        CapturingCommandSourceStack listSource = createSource(0, applicant, lanServer);
        int listResult = dispatcher.execute("todo project list all", listSource);
        assertEquals(1, listResult, "开局域网后其他玩家 project list all 应返回成功");
        assertContainsText(listSource.getSuccessMessages(), "Lan Published Team Project", "开局域网后其他玩家未在 project list all 中看到团队项目");

        int joinResult = dispatcher.execute("todo join project " + createdProject.getId(), createSource(0, applicant, lanServer));
        assertEquals(1, joinResult, "开局域网后普通玩家 join project 应返回成功");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.request_received", "开局域网后未通知项目经理有新申请");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "开局域网后未向申请人回写发送结果");
    }

    private static void shouldHideCurrentTeamProjectWhenSwitchingToSingleplayerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000157", "current-team-boundary-user", false);
        TestMinecraftServer dedicatedServer = createServer(player);
        addTeamProject(player, "boundary-team-project", "Boundary Team Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int selectResult = dispatcher.execute("todo project select boundary-team-project", createSource(0, player, dedicatedServer));
        assertEquals(1, selectResult, "专用服下选择团队项目应返回成功");

        CapturingCommandSourceStack dedicatedSource = createSource(0, player, dedicatedServer);
        int dedicatedListResult = dispatcher.execute("todo project list current", dedicatedSource);
        assertEquals(1, dedicatedListResult, "专用服下 project list current 应返回成功");
        assertContainsText(dedicatedSource.getSuccessMessages(), "Boundary Team Project", "专用服下 project list current 未输出团队项目");

        TestMinecraftServer singleplayerServer = createSingleplayerServer(player);
        CapturingCommandSourceStack singleplayerSource = createSource(0, player, singleplayerServer);
        int singleplayerResult = dispatcher.execute("todo project list current", singleplayerSource);
        assertEquals(1, singleplayerResult, "单机 project list current 应返回成功");
        assertContainsMessageKey(singleplayerSource.getSuccessMessages(), "command.todolist.project.list.current.empty", "单机下当前团队项目应表现为空");
        assertNotContainsText(singleplayerSource.getSuccessMessages(), "Boundary Team Project", "单机下不应继续显示团队 current 项目");
    }

    /**
     * 校验 project select 与 project list 可以联动输出当前项目和星标项目。
     */
    /**
     * 校验单机未开局域网时不能直接选择团队项目。
     */
    private static void shouldRejectSelectingTeamProjectInSingleplayerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000281", "singleplayer-select-team-user", false);
        TestMinecraftServer singleplayerServer = createSingleplayerServer(player);
        addTeamProject(player, "singleplayer-team-select-project", "Singleplayer Team Select");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player, singleplayerServer);

        int result = dispatcher.execute("todo project select singleplayer-team-select-project", source);
        assertEquals(0, result, "单机选择团队项目应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.select.not_found", "单机选择团队项目的错误键不正确");
    }

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

    /**
     * 校验选择不存在项目会返回 not_found。
     */
    private static void shouldRejectMissingProjectWhenSelectingSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000282", "project-select-missing-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, owner);

        int result = dispatcher.execute("todo project select missing-select-project", source);
        assertEquals(0, result, "选择不存在项目应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.select.not_found", "选择不存在项目的错误键不正确");
    }

    /**
     * 校验 current 项目为空时返回 current.empty。
     */
    private static void shouldShowEmptyCurrentProjectListSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000283", "project-current-empty-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, owner);

        int result = dispatcher.execute("todo project list current", source);
        assertEquals(1, result, "current 为空时 project list current 仍应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.project.list.current.empty", "current 为空时的提示键不正确");
    }

    /**
     * 校验星标项目为空时返回 star.empty。
     */
    private static void shouldShowEmptyStarProjectListSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000284", "project-star-empty-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, owner);

        int result = dispatcher.execute("todo project list star", source);
        assertEquals(1, result, "星标为空时 project list star 仍应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.project.list.star.empty", "星标为空时的提示键不正确");
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
     * 校验团队项目可以成功关闭成员可创建任务开关。
     */
    private static void shouldDisableTeamProjectMemberCreateSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000238", "manager-member-create-off", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "member-create-off-project", "Member Create Off");
        project.setAllowMemberCreate(true);
        TodoListCommon.getProjectManager().updateProject(project);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo project member-create member-create-off-project off", createSource(0, manager, server));
        assertEquals(1, result, "project member-create off 成功时应返回成功");
        assertEquals(Boolean.FALSE, TodoListCommon.getProjectManager().getProject("member-create-off-project").isAllowMemberCreate(), "member-create off 未关闭 allowMemberCreate");
    }

    /**
     * 校验个人项目不支持成员创建开关。
     */
    private static void shouldRejectProjectMemberCreateForPersonalProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000276", "owner-member-create-personal", false);
        TestMinecraftServer server = createServer(owner);
        addOwnedPersonalProject(owner, "member-create-personal-project", "Member Create Personal");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, owner, server);

        int result = dispatcher.execute("todo project member-create member-create-personal-project on", source);
        assertEquals(0, result, "个人项目执行 member-create 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member_create.team_only", "个人项目 member-create 的错误键不正确");
    }

    /**
     * 校验不存在项目执行成员创建开关会返回 not_found。
     */
    private static void shouldRejectProjectMemberCreateForMissingProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000277", "manager-member-create-missing", false);
        TestMinecraftServer server = createServer(manager);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo project member-create missing-member-create-project on", source);
        assertEquals(0, result, "不存在项目执行 member-create 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member_create.not_found", "不存在项目 member-create 的错误键不正确");
    }

    /**
     * 校验成员创建开关拒绝非法值。
     */
    private static void shouldRejectProjectMemberCreateForInvalidValueSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000278", "manager-member-create-invalid", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "member-create-invalid-project", "Member Create Invalid");
        project.setAllowMemberCreate(false);
        TodoListCommon.getProjectManager().updateProject(project);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo project member-create member-create-invalid-project maybe", source);
        assertEquals(0, result, "非法值 member-create 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member_create.invalid_value", "非法值 member-create 的错误键不正确");
        assertEquals(Boolean.FALSE, TodoListCommon.getProjectManager().getProject("member-create-invalid-project").isAllowMemberCreate(), "失败的 member-create 不应改动 allowMemberCreate");
    }

    /**
     * 校验普通成员不能修改团队项目的成员创建开关。
     */
    private static void shouldRejectProjectMemberCreateForRegularMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000239", "manager-member-create-denied", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000240", "member-member-create-denied", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "member-create-denied-project", "Member Create Denied");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo project member-create member-create-denied-project on", source);
        assertEquals(0, result, "普通成员修改成员创建开关应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member_create.no_permission", "普通成员 member-create 的错误键不正确");
        assertEquals(Boolean.FALSE, TodoListCommon.getProjectManager().getProject("member-create-denied-project").isAllowMemberCreate(), "失败的 member-create 不应修改 allowMemberCreate");
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
     * 校验项目经理向团队项目添加非法目标时会返回 invalid_target。
     */
    private static void shouldRejectProjectMemberAddWithInvalidTargetSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000262", "manager-add-member-invalid", false);
        TestMinecraftServer server = createServer(manager);
        addTeamProject(manager, "member-add-invalid-project", "Member Add Invalid");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo project member add member-add-invalid-project ghost-user", source);
        assertEquals(0, result, "member add 非法目标应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.add.invalid_target", "member add 非法目标的错误键不正确");
    }

    /**
     * 校验项目经理重复添加已有成员时会返回 already_exists。
     */
    private static void shouldRejectProjectMemberAddWhenTargetAlreadyExistsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000263", "manager-add-member-exists", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000264", "member-add-member-exists", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "member-add-exists-project", "Member Add Exists");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo project member add member-add-exists-project member-add-member-exists", source);
        assertEquals(0, result, "member add 重复添加已有成员应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.add.already_exists", "member add 已存在成员的错误键不正确");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(member.getStringUUID()), "失败的 member add 不应改动已有成员角色");
    }

    /**
     * 校验普通成员不能向团队项目添加成员。
     */
    private static void shouldRejectProjectMemberAddForRegularMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000241", "manager-add-member-denied", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000242", "member-add-member-denied", false);
        TestServerPlayer target = createPlayer("00000000-0000-0000-0000-000000000243", "target-add-member-denied", false);
        TestMinecraftServer server = createServer(manager, member, target);
        Project project = addTeamProject(manager, "member-add-denied-project", "Member Add Denied");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo project member add member-add-denied-project target-add-member-denied", source);
        assertEquals(0, result, "普通成员添加项目成员应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.add.no_permission", "普通成员 member add 的错误键不正确");
        assertEquals(null, project.getMemberRole(target.getStringUUID()), "失败的 member add 不应把目标成员加入项目");
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
     * 校验项目经理修改不存在成员角色时会返回 target.not_found。
     */
    private static void shouldRejectProjectMemberRoleWhenTargetMissingSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000265", "manager-role-missing", false);
        TestMinecraftServer server = createServer(manager);
        addTeamProject(manager, "member-role-missing-project", "Member Role Missing");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo project member role member-role-missing-project 00000000-0000-0000-0000-000000000266 lead", source);
        assertEquals(0, result, "member role 不存在成员应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.target.not_found", "member role 不存在成员的错误键不正确");
    }

    /**
     * 校验 lead 不能修改项目经理的角色。
     */
    private static void shouldRejectLeadChangingProjectManagerRoleSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000267", "manager-role-lead-denied", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000268", "lead-role-lead-denied", false);
        TestMinecraftServer server = createServer(manager, lead);
        Project project = addTeamProject(manager, "member-role-lead-denied-project", "Member Role Lead Denied");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, lead, server);

        int result = dispatcher.execute("todo project member role member-role-lead-denied-project " + manager.getStringUUID() + " member", source);
        assertEquals(0, result, "lead 修改项目经理角色应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.role.no_permission", "lead 修改项目经理角色的错误键不正确");
        assertEquals(Project.ProjectRole.PROJECT_MANAGER, project.getMemberRole(manager.getStringUUID()), "失败的 member role 不应改动项目经理角色");
    }

    /**
     * 校验 lead 不能修改自己的角色。
     */
    private static void shouldRejectLeadChangingOwnRoleSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000269", "manager-role-self-denied", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000270", "lead-role-self-denied", false);
        TestMinecraftServer server = createServer(manager, lead);
        Project project = addTeamProject(manager, "member-role-self-denied-project", "Member Role Self Denied");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, lead, server);

        int result = dispatcher.execute("todo project member role member-role-self-denied-project " + lead.getStringUUID() + " member", source);
        assertEquals(0, result, "lead 修改自己角色应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.role.no_permission", "lead 修改自己角色的错误键不正确");
        assertEquals(Project.ProjectRole.LEAD, project.getMemberRole(lead.getStringUUID()), "失败的 member role 不应改动 lead 自己的角色");
    }

    /**
     * 校验普通成员不能修改团队项目成员角色。
     */
    /**
     * 校验普通成员不能通过 member role 修改自己的角色。
     */
    private static void shouldRejectRegularMemberChangingOwnRoleSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000283", "manager-role-member-self", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000284", "member-role-self", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "member-role-member-self-project", "Member Role Member Self");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo project member role member-role-member-self-project " + member.getStringUUID() + " lead", source);
        assertEquals(0, result, "普通成员修改自己角色应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.role.no_permission", "普通成员修改自己角色的错误键不正确");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(member.getStringUUID()), "失败的 member role 不应改动普通成员自己的角色");
    }

    private static void shouldRejectProjectMemberRoleForRegularMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000244", "manager-role-denied", false);
        TestServerPlayer actor = createPlayer("00000000-0000-0000-0000-000000000245", "member-role-denied-actor", false);
        TestMinecraftServer server = createServer(manager, actor);
        Project project = addTeamProject(manager, "member-role-denied-project", "Member Role Denied");
        String targetMemberUuid = "00000000-0000-0000-0000-000000000246";
        project.addMember(actor.getStringUUID(), Project.ProjectRole.MEMBER, actor.getName().getString());
        project.addMember(targetMemberUuid, Project.ProjectRole.MEMBER, "member-role-denied-target");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, actor, server);

        int result = dispatcher.execute("todo project member role member-role-denied-project " + targetMemberUuid + " lead", source);
        assertEquals(0, result, "普通成员修改成员角色应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.role.no_permission", "普通成员 member role 的错误键不正确");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(targetMemberUuid), "失败的 member role 不应修改目标成员角色");
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
     * 校验项目经理移除不存在成员时会返回 target.not_found。
     */
    private static void shouldRejectProjectMemberRemoveWhenTargetMissingSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000271", "manager-remove-missing", false);
        TestMinecraftServer server = createServer(manager);
        addTeamProject(manager, "member-remove-missing-project", "Member Remove Missing");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo project member remove member-remove-missing-project 00000000-0000-0000-0000-000000000272", source);
        assertEquals(0, result, "member remove 不存在成员应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.target.not_found", "member remove 不存在成员的错误键不正确");
    }

    /**
     * 校验 lead 不能移除项目经理。
     */
    private static void shouldRejectLeadRemovingProjectManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000273", "manager-remove-lead-denied", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000274", "lead-remove-lead-denied", false);
        TestMinecraftServer server = createServer(manager, lead);
        Project project = addTeamProject(manager, "member-remove-lead-denied-project", "Member Remove Lead Denied");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, lead, server);

        int result = dispatcher.execute("todo project member remove member-remove-lead-denied-project " + manager.getStringUUID(), source);
        assertEquals(0, result, "lead 移除项目经理应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.remove.no_permission", "lead 移除项目经理的错误键不正确");
        assertEquals(Project.ProjectRole.PROJECT_MANAGER, project.getMemberRole(manager.getStringUUID()), "失败的 member remove 不应移除项目经理");
    }

    /**
     * 校验项目经理不能把自己从项目中移除。
     */
    private static void shouldRejectManagerRemovingSelfSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000275", "manager-remove-self-denied", false);
        TestMinecraftServer server = createServer(manager);
        Project project = addTeamProject(manager, "member-remove-self-denied-project", "Member Remove Self Denied");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo project member remove member-remove-self-denied-project " + manager.getStringUUID(), source);
        assertEquals(0, result, "项目经理移除自己应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.remove.no_permission", "项目经理移除自己的错误键不正确");
        assertEquals(Project.ProjectRole.PROJECT_MANAGER, project.getMemberRole(manager.getStringUUID()), "失败的 member remove 不应移除项目经理自己");
    }

    /**
     * 校验普通成员不能从团队项目移除其他成员。
     */
    /**
     * 校验 lead 不能通过 member remove 将自己移出团队项目。
     */
    private static void shouldRejectLeadRemovingSelfSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000285", "manager-remove-lead-self", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000286", "lead-remove-self", false);
        TestMinecraftServer server = createServer(manager, lead);
        Project project = addTeamProject(manager, "member-remove-lead-self-project", "Member Remove Lead Self");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, lead, server);

        int result = dispatcher.execute("todo project member remove member-remove-lead-self-project " + lead.getStringUUID(), source);
        assertEquals(0, result, "lead 自删应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.remove.no_permission", "lead 自删的错误键不正确");
        assertEquals(Project.ProjectRole.LEAD, project.getMemberRole(lead.getStringUUID()), "失败的 member remove 不应移除 lead 自己");
    }

    private static void shouldRejectProjectMemberRemoveForRegularMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000247", "manager-remove-denied", false);
        TestServerPlayer actor = createPlayer("00000000-0000-0000-0000-000000000248", "member-remove-denied-actor", false);
        TestMinecraftServer server = createServer(manager, actor);
        Project project = addTeamProject(manager, "member-remove-denied-project", "Member Remove Denied");
        String targetMemberUuid = "00000000-0000-0000-0000-000000000249";
        project.addMember(actor.getStringUUID(), Project.ProjectRole.MEMBER, actor.getName().getString());
        project.addMember(targetMemberUuid, Project.ProjectRole.MEMBER, "member-remove-denied-target");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, actor, server);

        int result = dispatcher.execute("todo project member remove member-remove-denied-project " + targetMemberUuid, source);
        assertEquals(0, result, "普通成员移除其他成员应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.member.remove.no_permission", "普通成员 member remove 的错误键不正确");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(targetMemberUuid), "失败的 member remove 不应移除目标成员");
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

    /**
     * 批量创建一组用于分页测试的个人任务，并通过微小延迟保证排序顺序稳定。
     *
     * @param titlePrefix 任务标题前缀
     * @param count 任务数量
     * @param projectId 关联项目 ID，null 表示不绑定项目
     * @return 生成好的测试任务数组
     * @throws Exception 线程休眠被中断时抛出异常
     */
    private static Task[] createPagedPersonalTasks(String titlePrefix, int count, String projectId) throws Exception {
        Task[] tasks = new Task[count];
        for (int index = 0; index < count; index++) {
            tasks[index] = createPersonalTask(titlePrefix + (index + 1), false, Task.Priority.MEDIUM, projectId);
            Thread.sleep(2L);
        }
        return tasks;
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
     * 从团队任务存储中按标题读取任务，不存在时返回 null。
     *
     * @param title 任务标题
     * @return 命中的团队任务；不存在时返回 null
     * @throws Exception 读取失败时抛出异常
     */
    private static Task findTeamTaskByTitleOrNull(String title) throws Exception {
        for (Task task : TodoListCommon.getTaskStorage().loadTeamTasks()) {
            if (task != null && title.equals(task.getTitle())) {
                return task;
            }
        }
        return null;
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
        return createServer(true, false, onlinePlayers);
    }

    /**
     * 创建未开放局域网的单机服务端桩。
     *
     * @param onlinePlayers 在线玩家
     * @return 单机服务端桩
     */
    private static TestMinecraftServer createSingleplayerServer(TestServerPlayer... onlinePlayers) {
        return createServer(false, false, onlinePlayers);
    }

    /**
     * 创建可配置 dedicated/published 状态的测试服务端。
     *
     * @param dedicated 是否为专用服务端
     * @param published 是否已开放局域网
     * @param onlinePlayers 在线玩家
     * @return 测试服务端桩
     */
    private static TestMinecraftServer createServer(boolean dedicated, boolean published, TestServerPlayer... onlinePlayers) {
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
                    player.testServer = server;
                    playerList.players.add(player);
                    playerList.playersByUuid.put(player.getUUID(), player);
                    playerList.playersByName.put(player.getName().getString(), player);
                }
            }
            server.testPlayerList = playerList;
            server.testDedicatedServer = dedicated;
            server.testPublished = published;
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
    private static void flushProjectSaves(MinecraftServer server) {
        ProjectSaveDebouncer.flushNow(server);
    }

    /**
     * 妯℃嫙鈥滈噸杩涗笘鐣屸€濆悗鐨勫叕鍏卞眰閲嶈浇锛岄噸鏂板垵濮嬪寲瀛樺偍骞朵粠纾佺洏鎭㈠椤圭洰鏁版嵁銆?
     */
    private static void reloadPersistentState() {
        TodoListCommon.init();
        TodoListCommon.reloadProjectsFromStorage();
    }

    /**
     * 鑾峰彇褰撳墠娴嬭瘯鍛藉悕绌洪棿涓嬬殑涓汉椤圭洰鎸佷箙鍖栨枃浠惰矾寰勩€?
     */
    private static Path getPersonalProjectsFilePath() {
        return DataPathProvider.getProjectsDir().resolve("projects.dat");
    }

    /**
     * 获取指定玩家的项目状态持久化文件路径。
     *
     * @param player 测试玩家
     * @return 玩家项目状态文件路径
     */
    private static Path getProjectPlayerStateFilePath(TestServerPlayer player) {
        return DataPathProvider.getProjectPlayersDir().resolve(player.getUUID().toString() + ".dat");
    }

    /**
     * 鑾峰彇褰撳墠娴嬭瘯鍛藉悕绌洪棿涓嬬殑鍥㈤槦椤圭洰鎸佷箙鍖栨枃浠惰矾寰勩€?
     */
    private static Path getTeamProjectsFilePath() {
        return DataPathProvider.getProjectsDir().resolve("team_projects.dat");
    }

    /**
     * 鑾峰彇鎸囧畾鐜╁鐨勪釜浜轰换鍔℃寔涔呭寲鏂囦欢璺緞銆?
     */
    private static Path getPersonalTaskFilePath(TestServerPlayer player) {
        return DataPathProvider.getTaskPlayersDir().resolve(player.getUUID().toString() + ".dat");
    }

    /**
     * 鑾峰彇褰撳墠娴嬭瘯鍛藉悕绌洪棿涓嬬殑鍥㈤槦浠诲姟鎸佷箙鍖栨枃浠惰矾寰勩€?
     */
    private static Path getTeamTaskFilePath() {
        return TodoListCommon.getTaskStorage().getDataDirectoryPath().resolve("team_tasks.dat");
    }

    /**
     * 鏍￠獙 project remove 浜屾纭鎴愬姛鍚庝細鍒犻櫎椤圭洰骞舵竻鐞嗗悓椤圭洰浠诲姟銆?
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
     * 验证删除当前个人项目后，玩家 current 状态会立刻清空，并在重载后保持净化结果。
     */
    private static void shouldClearCurrentProjectStateAfterProjectRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000166", "project-remove-current-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create personal Remove Current Project", createSource(0, owner, server)), "创建待删除当前项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create personal Keep Starred Project", createSource(0, owner, server)), "创建保留星标项目应返回成功");

        Project removableCurrentProject = findProjectByName("Remove Current Project");
        Project keepStarredProject = findProjectByName("Keep Starred Project");
        assertNotNull(removableCurrentProject, "未找到待删除当前项目");
        assertNotNull(keepStarredProject, "未找到保留星标项目");

        assertEquals(1, dispatcher.execute("todo project select " + removableCurrentProject.getId(), createSource(0, owner, server)), "设置当前项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + keepStarredProject.getId(), createSource(0, owner, server)), "设置保留星标项目应返回成功");

        assertEquals(1, dispatcher.execute("todo project remove " + removableCurrentProject.getId(), createSource(0, owner, server)), "发起删除当前项目请求应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, owner, server)), "确认删除当前项目应返回成功");

        assertEquals(null, ProjectPackets.getActiveProjectId(owner), "删除当前项目后应立即清空 current 状态");
        assertEquals(List.of(keepStarredProject.getId()), ProjectPackets.getHudStarredProjectIds(owner), "删除当前项目后不应误清空其他星标项目");

        flushProjectSaves(server);
        reloadPersistentState();

        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedOwner);

        assertEquals(null, ProjectPackets.getActiveProjectId(reloadedOwner), "重载后不应恢复已删除的当前项目");
        assertEquals(List.of(keepStarredProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "重载后应保留未删除的星标项目");
    }

    /**
     * 验证删除星标个人项目后，玩家 starred 状态会被定向清理，并在重载后保持净化结果。
     */
    private static void shouldClearStarredProjectStateAfterProjectRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000167", "project-remove-star-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create personal Keep Current Project", createSource(0, owner, server)), "创建保留当前项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create personal Remove Starred Project", createSource(0, owner, server)), "创建待删除星标项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create personal Keep Another Starred Project", createSource(0, owner, server)), "创建保留星标项目应返回成功");

        Project keepCurrentProject = findProjectByName("Keep Current Project");
        Project removableStarredProject = findProjectByName("Remove Starred Project");
        Project keepAnotherStarredProject = findProjectByName("Keep Another Starred Project");
        assertNotNull(keepCurrentProject, "未找到保留当前项目");
        assertNotNull(removableStarredProject, "未找到待删除星标项目");
        assertNotNull(keepAnotherStarredProject, "未找到保留星标项目");

        assertEquals(1, dispatcher.execute("todo project select " + keepCurrentProject.getId(), createSource(0, owner, server)), "设置保留当前项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + removableStarredProject.getId(), createSource(0, owner, server)), "设置待删除星标项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + keepAnotherStarredProject.getId(), createSource(0, owner, server)), "设置保留星标项目应返回成功");

        assertEquals(1, dispatcher.execute("todo project remove " + removableStarredProject.getId(), createSource(0, owner, server)), "发起删除星标项目请求应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, owner, server)), "确认删除星标项目应返回成功");

        assertEquals(keepCurrentProject.getId(), ProjectPackets.getActiveProjectId(owner), "删除星标项目后不应误清空当前项目");
        assertEquals(List.of(keepAnotherStarredProject.getId()), ProjectPackets.getHudStarredProjectIds(owner), "删除星标项目后应仅保留剩余星标项目");

        flushProjectSaves(server);
        reloadPersistentState();

        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedOwner);

        assertEquals(keepCurrentProject.getId(), ProjectPackets.getActiveProjectId(reloadedOwner), "重载后应保留未删除的当前项目");
        assertEquals(List.of(keepAnotherStarredProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "重载后不应恢复已删除的星标项目");
    }

    /**
     * 验证删除团队项目后，在线成员的 current/star 状态会一起被清理，并在重载后保持净化结果。
     */
    private static void shouldClearTeamProjectStateForOnlineMembersAfterRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000168", "team-remove-state-manager", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000169", "team-remove-state-member", false);
        TestMinecraftServer server = createServer(manager, member);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Remove Shared Team Project", createSource(0, manager, server)), "创建待删除团队项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create team Keep Shared Team Project", createSource(0, manager, server)), "创建保留团队项目应返回成功");

        Project removableTeamProject = findProjectByName("Remove Shared Team Project");
        Project keepTeamProject = findProjectByName("Keep Shared Team Project");
        assertNotNull(removableTeamProject, "未找到待删除团队项目");
        assertNotNull(keepTeamProject, "未找到保留团队项目");
        removableTeamProject.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        keepTeamProject.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());

        assertEquals(1, dispatcher.execute("todo project select " + removableTeamProject.getId(), createSource(0, manager, server)), "经理设置当前团队项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + keepTeamProject.getId(), createSource(0, manager, server)), "经理设置保留团队星标应返回成功");
        assertEquals(1, dispatcher.execute("todo project select " + removableTeamProject.getId(), createSource(0, member, server)), "成员设置当前团队项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + removableTeamProject.getId(), createSource(0, member, server)), "成员设置待删除团队星标应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + keepTeamProject.getId(), createSource(0, member, server)), "成员设置保留团队星标应返回成功");

        assertEquals(1, dispatcher.execute("todo project remove " + removableTeamProject.getId(), createSource(0, manager, server)), "发起删除团队项目请求应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, manager, server)), "确认删除团队项目应返回成功");

        assertEquals(null, ProjectPackets.getActiveProjectId(manager), "删除团队项目后经理的 current 状态应被清空");
        assertEquals(List.of(keepTeamProject.getId()), ProjectPackets.getHudStarredProjectIds(manager), "删除团队项目后经理的其他星标应保留");
        assertEquals(null, ProjectPackets.getActiveProjectId(member), "删除团队项目后成员的 current 状态应被清空");
        assertEquals(List.of(keepTeamProject.getId()), ProjectPackets.getHudStarredProjectIds(member), "删除团队项目后成员的其他星标应保留");

        flushProjectSaves(server);
        reloadPersistentState();

        TestServerPlayer reloadedManager = createPlayer(manager.getStringUUID(), manager.getName().getString(), false);
        TestServerPlayer reloadedMember = createPlayer(member.getStringUUID(), member.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedManager, reloadedMember);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedManager);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedMember);

        assertEquals(null, ProjectPackets.getActiveProjectId(reloadedManager), "重载后经理不应恢复已删除的团队当前项目");
        assertEquals(List.of(keepTeamProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedManager), "重载后经理应保留未删除的团队星标");
        assertEquals(null, ProjectPackets.getActiveProjectId(reloadedMember), "重载后成员不应恢复已删除的团队当前项目");
        assertEquals(List.of(keepTeamProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedMember), "重载后成员应保留未删除的团队星标");
    }

    /**
     * 验证项目改名后，玩家 current/star 状态仍指向同一项目，列表展示会更新为最新名称。
     */
    private static void shouldKeepCurrentAndStarredProjectStateAfterProjectRenameSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000170", "project-rename-state-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create personal Rename Current Before", createSource(0, owner, server)), "创建当前项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create personal Rename Star Before", createSource(0, owner, server)), "创建星标项目应返回成功");

        Project currentProject = findProjectByName("Rename Current Before");
        Project starredProject = findProjectByName("Rename Star Before");
        assertNotNull(currentProject, "未找到待改名的当前项目");
        assertNotNull(starredProject, "未找到待改名的星标项目");

        assertEquals(1, dispatcher.execute("todo project select " + currentProject.getId(), createSource(0, owner, server)), "设置当前项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + starredProject.getId(), createSource(0, owner, server)), "设置星标项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project rename " + currentProject.getId() + " Rename Current After", createSource(0, owner, server)), "重命名当前项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project rename " + starredProject.getId() + " Rename Star After", createSource(0, owner, server)), "重命名星标项目应返回成功");

        assertEquals(currentProject.getId(), ProjectPackets.getActiveProjectId(owner), "项目改名后 current 状态不应改变项目 ID");
        assertEquals(List.of(starredProject.getId()), ProjectPackets.getHudStarredProjectIds(owner), "项目改名后 starred 状态不应改变项目 ID");

        CapturingCommandSourceStack currentSource = createSource(0, owner, server);
        assertEquals(1, dispatcher.execute("todo project list current", currentSource), "改名后 project list current 应返回成功");
        assertContainsText(currentSource.getSuccessMessages(), "Rename Current After", "改名后 project list current 未展示新名称");

        CapturingCommandSourceStack starSource = createSource(0, owner, server);
        assertEquals(1, dispatcher.execute("todo project list star", starSource), "改名后 project list star 应返回成功");
        assertContainsText(starSource.getSuccessMessages(), "Rename Star After", "改名后 project list star 未展示新名称");

        flushProjectSaves(server);
        reloadPersistentState();

        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedOwner);
        assertEquals(currentProject.getId(), ProjectPackets.getActiveProjectId(reloadedOwner), "重载后不应丢失改名项目的 current 状态");
        assertEquals(List.of(starredProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "重载后不应丢失改名项目的 starred 状态");
    }

    /**
     * 验证玩家状态文件中混入重复、空白和不存在的项目 ID 时，进服恢复会自动净化并写回。
     */
    private static void shouldSanitizeDirtyProjectPlayerStateOnJoinSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000171", "dirty-project-state-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create personal Dirty Keep Project", createSource(0, owner, server)), "创建保留项目应返回成功");
        Project keepProject = findProjectByName("Dirty Keep Project");
        assertNotNull(keepProject, "未找到用于净化的保留项目");
        flushProjectSaves(server);

        ProjectPlayerStateStorage storage = new ProjectPlayerStateStorage();
        storage.savePlayerState(
                owner.getUUID(),
                new ProjectPlayerStateStorage.ProjectPlayerState(
                        "missing-current-project",
                        List.of(keepProject.getId(), "missing-star-project", keepProject.getId(), " ", keepProject.getId()),
                        true
                )
        );

        reloadPersistentState();

        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedOwner);

        assertEquals(null, ProjectPackets.getActiveProjectId(reloadedOwner), "脏 current 项目 ID 应在进服时被清空");
        assertEquals(List.of(keepProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "脏 starred 项目列表应在进服时去重并移除无效项目");

        reloadPersistentState();

        TestServerPlayer sanitizedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer sanitizedServer = createServer(sanitizedOwner);
        ProjectPackets.onPlayerJoin(sanitizedServer, sanitizedOwner);

        assertEquals(null, ProjectPackets.getActiveProjectId(sanitizedOwner), "净化后的 current 状态不应在二次重载后恢复脏数据");
        assertEquals(List.of(keepProject.getId()), ProjectPackets.getHudStarredProjectIds(sanitizedOwner), "净化后的 starred 状态不应在二次重载后恢复脏数据");
    }

    /**
     * 验证团队项目删除时，即使成员离线，其持久化的 current/star 状态也会在下次进服时被净化。
     */
    private static void shouldSanitizeOfflineMemberProjectStateAfterTeamRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000172", "offline-team-remove-manager", false);
        TestServerPlayer offlineMember = createPlayer("00000000-0000-0000-0000-000000000173", "offline-team-remove-member", false);
        TestMinecraftServer server = createServer(manager);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Offline Remove Team", createSource(0, manager, server)), "创建待删除团队项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create team Offline Keep Team", createSource(0, manager, server)), "创建保留团队项目应返回成功");

        Project removableTeamProject = findProjectByName("Offline Remove Team");
        Project keepTeamProject = findProjectByName("Offline Keep Team");
        assertNotNull(removableTeamProject, "未找到待删除团队项目");
        assertNotNull(keepTeamProject, "未找到保留团队项目");
        removableTeamProject.addMember(offlineMember.getStringUUID(), Project.ProjectRole.MEMBER, offlineMember.getName().getString());
        keepTeamProject.addMember(offlineMember.getStringUUID(), Project.ProjectRole.MEMBER, offlineMember.getName().getString());

        ProjectPlayerStateStorage storage = new ProjectPlayerStateStorage();
        storage.savePlayerState(
                offlineMember.getUUID(),
                new ProjectPlayerStateStorage.ProjectPlayerState(
                        removableTeamProject.getId(),
                        List.of(removableTeamProject.getId(), keepTeamProject.getId()),
                        true
                )
        );

        assertEquals(1, dispatcher.execute("todo project remove " + removableTeamProject.getId(), createSource(0, manager, server)), "发起删除离线成员关联团队项目请求应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, manager, server)), "确认删除离线成员关联团队项目应返回成功");

        flushProjectSaves(server);
        reloadPersistentState();

        TestServerPlayer reloadedManager = createPlayer(manager.getStringUUID(), manager.getName().getString(), false);
        TestServerPlayer reloadedOfflineMember = createPlayer(offlineMember.getStringUUID(), offlineMember.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedManager, reloadedOfflineMember);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedOfflineMember);

        assertEquals(null, ProjectPackets.getActiveProjectId(reloadedOfflineMember), "离线成员下次进服时不应恢复已删除的团队当前项目");
        assertEquals(List.of(keepTeamProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOfflineMember), "离线成员下次进服时应只保留未删除的团队星标项目");
    }

    /**
     * 校验 join project 会向项目审批人发送加入申请提示。
     */
    /**
     * 验证同一份团队状态文件在专用服、局域网和单机三种环境下恢复结果符合预期。
     */
    private static void shouldRestoreTeamProjectStateDifferentlyAcrossServerModesSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000174", "server-mode-restore-user", false);
        TestMinecraftServer dedicatedServer = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Restore Team Mode Project", createSource(0, owner, dedicatedServer)), "创建团队项目应返回成功");
        Project teamProject = findProjectByName("Restore Team Mode Project");
        assertNotNull(teamProject, "未找到环境恢复测试所需的团队项目");
        flushProjectSaves(dedicatedServer);

        ProjectPlayerStateStorage storage = new ProjectPlayerStateStorage();

        storage.savePlayerState(owner.getUUID(), new ProjectPlayerStateStorage.ProjectPlayerState(teamProject.getId(), List.of(teamProject.getId()), true));
        reloadPersistentState();
        TestServerPlayer dedicatedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedDedicatedServer = createServer(dedicatedOwner);
        ProjectPackets.onPlayerJoin(reloadedDedicatedServer, dedicatedOwner);
        assertEquals(teamProject.getId(), ProjectPackets.getActiveProjectId(dedicatedOwner), "专用服应恢复团队 current 状态");
        assertEquals(List.of(teamProject.getId()), ProjectPackets.getHudStarredProjectIds(dedicatedOwner), "专用服应恢复团队 starred 状态");

        storage.savePlayerState(owner.getUUID(), new ProjectPlayerStateStorage.ProjectPlayerState(teamProject.getId(), List.of(teamProject.getId()), true));
        reloadPersistentState();
        TestServerPlayer lanOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer lanServer = createServer(false, true, lanOwner);
        ProjectPackets.onPlayerJoin(lanServer, lanOwner);
        assertEquals(teamProject.getId(), ProjectPackets.getActiveProjectId(lanOwner), "局域网应恢复团队 current 状态");
        assertEquals(List.of(teamProject.getId()), ProjectPackets.getHudStarredProjectIds(lanOwner), "局域网应恢复团队 starred 状态");

        storage.savePlayerState(owner.getUUID(), new ProjectPlayerStateStorage.ProjectPlayerState(teamProject.getId(), List.of(teamProject.getId()), true));
        reloadPersistentState();
        TestServerPlayer singleplayerOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer singleplayerServer = createSingleplayerServer(singleplayerOwner);
        ProjectPackets.onPlayerJoin(singleplayerServer, singleplayerOwner);
        assertEquals(null, ProjectPackets.getActiveProjectId(singleplayerOwner), "单机应清空团队 current 状态");
        assertEquals(List.of(), ProjectPackets.getHudStarredProjectIds(singleplayerOwner), "单机应清空团队 starred 状态");
    }

    /**
     * 验证同一个项目同时是 current 和 starred 时，删除项目会一次性清空两份状态。
     */
    private static void shouldClearCurrentAndStarredStateWhenSameProjectRemovedSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000175", "same-project-remove-state-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create personal Remove Same State Project", createSource(0, owner, server)), "创建同一状态项目应返回成功");
        Project sameProject = findProjectByName("Remove Same State Project");
        assertNotNull(sameProject, "未找到删除同一状态测试所需项目");

        assertEquals(1, dispatcher.execute("todo project select " + sameProject.getId(), createSource(0, owner, server)), "设置 current 应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + sameProject.getId(), createSource(0, owner, server)), "设置 starred 应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove " + sameProject.getId(), createSource(0, owner, server)), "发起删除请求应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, owner, server)), "确认删除应返回成功");

        assertEquals(null, ProjectPackets.getActiveProjectId(owner), "删除同一状态项目后应清空 current");
        assertEquals(List.of(), ProjectPackets.getHudStarredProjectIds(owner), "删除同一状态项目后应清空 starred");

        flushProjectSaves(server);
        reloadPersistentState();
        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedOwner);
        assertEquals(null, ProjectPackets.getActiveProjectId(reloadedOwner), "重载后不应恢复已删除项目的 current");
        assertEquals(List.of(), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "重载后不应恢复已删除项目的 starred");
    }

    /**
     * 验证同一个项目同时是 current 和 starred 时，改名只更新展示名称，不会破坏状态绑定。
     */
    private static void shouldKeepCurrentAndStarredStateWhenSameProjectRenamedSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000176", "same-project-rename-state-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create personal Rename Same State Project", createSource(0, owner, server)), "创建同一状态项目应返回成功");
        Project sameProject = findProjectByName("Rename Same State Project");
        assertNotNull(sameProject, "未找到改名同一状态测试所需项目");

        assertEquals(1, dispatcher.execute("todo project select " + sameProject.getId(), createSource(0, owner, server)), "设置 current 应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + sameProject.getId(), createSource(0, owner, server)), "设置 starred 应返回成功");
        assertEquals(1, dispatcher.execute("todo project rename " + sameProject.getId() + " Rename Same State Project After", createSource(0, owner, server)), "改名应返回成功");

        assertEquals(sameProject.getId(), ProjectPackets.getActiveProjectId(owner), "改名后 current 应保持原项目 ID");
        assertEquals(List.of(sameProject.getId()), ProjectPackets.getHudStarredProjectIds(owner), "改名后 starred 应保持原项目 ID");

        CapturingCommandSourceStack currentSource = createSource(0, owner, server);
        assertEquals(1, dispatcher.execute("todo project list current", currentSource), "改名后 project list current 应返回成功");
        assertContainsText(currentSource.getSuccessMessages(), "Rename Same State Project After", "改名后 current 未展示新名称");

        CapturingCommandSourceStack starSource = createSource(0, owner, server);
        assertEquals(1, dispatcher.execute("todo project list star", starSource), "改名后 project list star 应返回成功");
        assertContainsText(starSource.getSuccessMessages(), "Rename Same State Project After", "改名后 starred 未展示新名称");
    }

    /**
     * 验证团队成员被移除后，项目仍可见，但任务访问权限会收紧。
     */
    private static void shouldKeepProjectStateButRestrictTasksAfterMemberRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000177", "member-remove-visibility-manager", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000178", "member-remove-visibility-user", false);
        TestMinecraftServer server = createServer(manager, member);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Removed Member Visible Team", createSource(0, manager, server)), "创建团队项目应返回成功");
        Project teamProject = findProjectByName("Removed Member Visible Team");
        assertNotNull(teamProject, "未找到成员移除测试所需团队项目");
        teamProject.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        saveTeamTasks(createTeamTask(teamProject.getId(), "Removed Member Hidden Task"));

        assertEquals(1, dispatcher.execute("todo project select " + teamProject.getId(), createSource(0, member, server)), "成员设置 current 应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + teamProject.getId(), createSource(0, member, server)), "成员设置 starred 应返回成功");
        assertEquals(1, dispatcher.execute("todo project member remove " + teamProject.getId() + " " + member.getStringUUID(), createSource(0, manager, server)), "移除成员应返回成功");

        assertEquals(teamProject.getId(), ProjectPackets.getActiveProjectId(member), "成员移除后 current 不应被清空");
        assertEquals(List.of(teamProject.getId()), ProjectPackets.getHudStarredProjectIds(member), "成员移除后 starred 不应被清空");

        CapturingCommandSourceStack currentSource = createSource(0, member, server);
        assertEquals(1, dispatcher.execute("todo project list current", currentSource), "成员移除后 project list current 应返回成功");
        assertContainsText(currentSource.getSuccessMessages(), "Removed Member Visible Team", "成员移除后团队项目仍应可见");

        CapturingCommandSourceStack taskSource = createSource(0, member, server);
        assertEquals(0, dispatcher.execute("todo task listp " + teamProject.getId(), taskSource), "成员移除后 task listp 应被拒绝");
        assertContainsMessageKey(taskSource.getFailureMessages(), "command.todolist.task.project.permission_denied", "成员移除后任务权限错误键不正确");
    }

    /**
     * 验证团队成员从 lead 降为 member 后，已有 current/star 状态保持不变，任务查看权限仍然可用。
     */
    private static void shouldKeepProjectStateAfterMemberRoleDemotionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000179", "member-demote-state-manager", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000180", "member-demote-state-user", false);
        TestMinecraftServer server = createServer(manager, member);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Demoted Member Team", createSource(0, manager, server)), "创建团队项目应返回成功");
        Project teamProject = findProjectByName("Demoted Member Team");
        assertNotNull(teamProject, "未找到成员降权测试所需团队项目");
        teamProject.addMember(member.getStringUUID(), Project.ProjectRole.LEAD, member.getName().getString());
        saveTeamTasks(createTeamTask(teamProject.getId(), "Demoted Member Visible Task"));

        assertEquals(1, dispatcher.execute("todo project select " + teamProject.getId(), createSource(0, member, server)), "成员设置 current 应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + teamProject.getId(), createSource(0, member, server)), "成员设置 starred 应返回成功");
        assertEquals(1, dispatcher.execute("todo project member role " + teamProject.getId() + " " + member.getStringUUID() + " member", createSource(0, manager, server)), "成员降权应返回成功");

        assertEquals(teamProject.getId(), ProjectPackets.getActiveProjectId(member), "成员降权后 current 不应变化");
        assertEquals(List.of(teamProject.getId()), ProjectPackets.getHudStarredProjectIds(member), "成员降权后 starred 不应变化");

        CapturingCommandSourceStack taskSource = createSource(0, member, server);
        assertEquals(1, dispatcher.execute("todo task listp " + teamProject.getId(), taskSource), "成员降权后 task listp 仍应可用");
        assertContainsText(taskSource.getSuccessMessages(), "Demoted Member Visible Task", "成员降权后仍应能看到团队任务");
    }

    /**
     * 验证玩家状态文件同时混入个人项目与团队项目脏引用时，会按当前环境正确净化。
     */
    private static void shouldSanitizeMixedPersonalAndTeamProjectStateAcrossServerModesSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000181", "mixed-project-state-user", false);
        TestMinecraftServer dedicatedServer = createServer(owner);

        addOwnedPersonalProject(owner, "mixed-keep-personal", "Mixed Keep Personal");
        Project keepPersonalProject = TodoListCommon.getProjectManager().getProject("mixed-keep-personal");
        Project keepTeamProject = addTeamProject(owner, "mixed-keep-team", "Mixed Keep Team");
        assertNotNull(keepPersonalProject, "未找到混合净化测试所需的个人项目");
        assertNotNull(keepTeamProject, "未找到混合净化测试所需的团队项目");
        flushProjectSaves(dedicatedServer);

        ProjectPlayerStateStorage storage = new ProjectPlayerStateStorage();
        ProjectPlayerStateStorage.ProjectPlayerState dirtyState = new ProjectPlayerStateStorage.ProjectPlayerState(
                keepTeamProject.getId(),
                List.of(
                        "missing-personal-project",
                        keepPersonalProject.getId(),
                        "missing-team-project",
                        keepTeamProject.getId(),
                        keepPersonalProject.getId(),
                        keepTeamProject.getId()
                ),
                true
        );

        storage.savePlayerState(owner.getUUID(), dirtyState);
        reloadPersistentState();
        TestServerPlayer dedicatedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedDedicatedServer = createServer(dedicatedOwner);
        ProjectPackets.onPlayerJoin(reloadedDedicatedServer, dedicatedOwner);
        assertEquals(keepTeamProject.getId(), ProjectPackets.getActiveProjectId(dedicatedOwner), "专用服应保留有效的团队 current 项目");
        assertListEquals(
                List.of(keepPersonalProject.getId(), keepTeamProject.getId()),
                ProjectPackets.getHudStarredProjectIds(dedicatedOwner),
                "专用服应保留并去重有效的个人与团队 starred 项目"
        );

        storage.savePlayerState(owner.getUUID(), dirtyState);
        reloadPersistentState();
        TestServerPlayer singleplayerOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer singleplayerServer = createSingleplayerServer(singleplayerOwner);
        ProjectPackets.onPlayerJoin(singleplayerServer, singleplayerOwner);
        assertEquals(null, ProjectPackets.getActiveProjectId(singleplayerOwner), "单机应清空混合脏状态里的团队 current 项目");
        assertListEquals(
                List.of(keepPersonalProject.getId()),
                ProjectPackets.getHudStarredProjectIds(singleplayerOwner),
                "单机应只保留仍可用的个人 starred 项目"
        );
    }

    /**
     * 验证 project list all 与 project list star 会按统一排序规则稳定输出项目列表。
     */
    private static void shouldKeepProjectListOrderingStableAcrossListModesSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000182", "project-list-order-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        addOwnedPersonalProject(owner, "personal-old-project", "Personal Old Project");
        addOwnedPersonalProject(owner, "personal-zeta-project", "Personal Zeta Project");
        addOwnedPersonalProject(owner, "personal-alpha-project", "Personal Alpha Project");
        addOwnedPersonalProject(owner, "personal-new-project", "Personal New Project");
        Project teamOldProject = addTeamProject(owner, "team-old-project", "Team Old Project");
        Project teamNewProject = addTeamProject(owner, "team-new-project", "Team New Project");

        Project personalOldProject = TodoListCommon.getProjectManager().getProject("personal-old-project");
        Project personalZetaProject = TodoListCommon.getProjectManager().getProject("personal-zeta-project");
        Project personalAlphaProject = TodoListCommon.getProjectManager().getProject("personal-alpha-project");
        Project personalNewProject = TodoListCommon.getProjectManager().getProject("personal-new-project");
        assertNotNull(personalOldProject, "未找到排序测试所需的旧个人项目");
        assertNotNull(personalZetaProject, "未找到排序测试所需的 Zeta 个人项目");
        assertNotNull(personalAlphaProject, "未找到排序测试所需的 Alpha 个人项目");
        assertNotNull(personalNewProject, "未找到排序测试所需的新个人项目");

        personalOldProject.setCreatedAt(1_000L);
        personalZetaProject.setCreatedAt(2_000L);
        personalAlphaProject.setCreatedAt(2_000L);
        personalNewProject.setCreatedAt(4_000L);
        teamOldProject.setCreatedAt(1_000L);
        teamNewProject.setCreatedAt(4_000L);

        CapturingCommandSourceStack allSource = createSource(0, owner, server);
        assertEquals(1, dispatcher.execute("todo project list all", allSource), "project list all 应返回成功");
        assertTextAppearsBefore(allSource.getSuccessMessages(), "Personal New Project", "Personal Alpha Project", "project list all 应先按 createdAt 倒序排列个人项目");
        assertTextAppearsBefore(allSource.getSuccessMessages(), "Personal Alpha Project", "Personal Zeta Project", "相同 createdAt 的个人项目应再按名称排序");
        assertTextAppearsBefore(allSource.getSuccessMessages(), "Personal Zeta Project", "Personal Old Project", "较新的个人项目应排在较旧个人项目之前");
        assertTextAppearsBefore(allSource.getSuccessMessages(), "Personal Old Project", "Team New Project", "个人项目应整体排在团队项目之前");
        assertTextAppearsBefore(allSource.getSuccessMessages(), "Team New Project", "Team Old Project", "团队项目应按 createdAt 倒序排列");

        ProjectPackets.setHudStarredProjectIds(
                owner,
                List.of(teamOldProject.getId(), personalZetaProject.getId(), personalAlphaProject.getId(), teamNewProject.getId())
        );
        CapturingCommandSourceStack starSource = createSource(0, owner, server);
        assertEquals(1, dispatcher.execute("todo project list star", starSource), "project list star 应返回成功");
        assertTextAppearsBefore(starSource.getSuccessMessages(), "Personal Alpha Project", "Personal Zeta Project", "project list star 应对相同 createdAt 的个人项目按名称排序");
        assertTextAppearsBefore(starSource.getSuccessMessages(), "Personal Zeta Project", "Team New Project", "project list star 应先显示个人项目再显示团队项目");
        assertTextAppearsBefore(starSource.getSuccessMessages(), "Team New Project", "Team Old Project", "project list star 应对团队项目按 createdAt 倒序排列");
    }

    /**
     * 验证 project remove confirm 的待确认状态按玩家隔离，彼此不会串线。
     */
    private static void shouldIsolatePendingProjectRemoveConfirmationsBetweenPlayersSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer firstOwner = createPlayer("00000000-0000-0000-0000-000000000183", "remove-confirm-owner-a", false);
        TestServerPlayer secondOwner = createPlayer("00000000-0000-0000-0000-000000000184", "remove-confirm-owner-b", false);
        TestMinecraftServer server = createServer(firstOwner, secondOwner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create personal Remove Isolation Project A", createSource(0, firstOwner, server)), "玩家 A 创建个人项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create personal Remove Isolation Project B", createSource(0, secondOwner, server)), "玩家 B 创建个人项目应返回成功");
        Project firstProject = findProjectByName("Remove Isolation Project A");
        Project secondProject = findProjectByName("Remove Isolation Project B");
        assertNotNull(firstProject, "未找到玩家 A 的删除隔离项目");
        assertNotNull(secondProject, "未找到玩家 B 的删除隔离项目");

        assertEquals(1, dispatcher.execute("todo project remove " + firstProject.getId(), createSource(0, firstOwner, server)), "玩家 A 发起 project remove 应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove " + secondProject.getId(), createSource(0, secondOwner, server)), "玩家 B 发起 project remove 应返回成功");

        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, firstOwner, server)), "玩家 A 确认删除应返回成功");
        assertNull(TodoListCommon.getProjectManager().getProject(firstProject.getId()), "玩家 A 确认后应删除自己的项目");
        assertNotNull(TodoListCommon.getProjectManager().getProject(secondProject.getId()), "玩家 A 确认后不应误删玩家 B 的项目");

        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, secondOwner, server)), "玩家 B 确认删除应返回成功");
        assertNull(TodoListCommon.getProjectManager().getProject(secondProject.getId()), "玩家 B 确认后应删除自己的项目");
    }

    /**
     * 验证 task clean confirm 的待确认状态按玩家隔离，彼此不会串线。
     */
    private static void shouldIsolatePendingTaskCleanConfirmationsBetweenPlayersSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer firstPlayer = createPlayer("00000000-0000-0000-0000-000000000185", "task-clean-owner-a", false);
        TestServerPlayer secondPlayer = createPlayer("00000000-0000-0000-0000-000000000186", "task-clean-owner-b", false);
        TestMinecraftServer server = createServer(firstPlayer, secondPlayer);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        savePersonalTasks(
                firstPlayer,
                createPersonalTask("First Completed Task", true, Task.Priority.MEDIUM, null),
                createPersonalTask("First Open Task", false, Task.Priority.MEDIUM, null)
        );
        savePersonalTasks(
                secondPlayer,
                createPersonalTask("Second Completed Task", true, Task.Priority.MEDIUM, null),
                createPersonalTask("Second Open Task", false, Task.Priority.MEDIUM, null)
        );

        assertEquals(1, dispatcher.execute("todo task clean personal all completed", createSource(0, firstPlayer, server)), "玩家 A 发起 task clean 应返回成功");
        assertEquals(1, dispatcher.execute("todo task clean personal all completed", createSource(0, secondPlayer, server)), "玩家 B 发起 task clean 应返回成功");

        assertEquals(1, dispatcher.execute("todo task clean confirm", createSource(0, firstPlayer, server)), "玩家 A 确认清理应返回成功");
        assertNull(findPersonalTaskByTitleOrNull(firstPlayer, "First Completed Task"), "玩家 A 确认后应清理自己的已完成任务");
        assertNotNull(findPersonalTaskByTitleOrNull(firstPlayer, "First Open Task"), "玩家 A 确认后不应清理自己的未完成任务");
        assertNotNull(findPersonalTaskByTitleOrNull(secondPlayer, "Second Completed Task"), "玩家 A 确认后不应清理玩家 B 的已完成任务");

        assertEquals(1, dispatcher.execute("todo task clean confirm", createSource(0, secondPlayer, server)), "玩家 B 确认清理应返回成功");
        assertNull(findPersonalTaskByTitleOrNull(secondPlayer, "Second Completed Task"), "玩家 B 确认后应清理自己的已完成任务");
        assertNotNull(findPersonalTaskByTitleOrNull(secondPlayer, "Second Open Task"), "玩家 B 确认后不应清理自己的未完成任务");
    }

    private static void shouldListProjectsAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000142", "reload-project-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int createResult = dispatcher.execute("todo project create personal Reload Personal Project", createSource(0, owner, server));
        assertEquals(1, createResult, "project create personal 搴旇繑鍥炴垚鍔?");
        flushProjectSaves(server);
        assertEquals(Boolean.TRUE, Files.exists(getPersonalProjectsFilePath()), "project create personal 鍚庡簲鍐欏叆涓汉椤圭洰鏂囦欢");

        reloadPersistentState();

        assertNotNull(findProjectByName("Reload Personal Project"), "閲嶆柊鍒濆鍖栧悗搴旇鑳介噸鏂板姞杞戒釜浜洪」鐩?");
        CapturingCommandSourceStack source = createSource(0, owner, createServer(owner));
        int listResult = createDispatcher().execute("todo project list all", source);
        assertEquals(1, listResult, "閲嶈浇鍚?project list all 搴旇繑鍥炴垚鍔?");
        assertContainsText(source.getSuccessMessages(), "Reload Personal Project", "閲嶈浇鍚?project list all 鏈緭鍑哄凡鎸佷箙鍖栫殑椤圭洰");
    }

    /**
     * 验证玩家当前项目与星标项目在重载并重新进服后仍可正确恢复。
     */
    private static void shouldListCurrentAndStarredProjectsAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000145", "reload-project-state-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(
                1,
                dispatcher.execute("todo project create personal Reload Current Project", createSource(0, owner, server)),
                "project create personal 应返回成功"
        );
        assertEquals(
                1,
                dispatcher.execute("todo project create personal Reload Starred Project", createSource(0, owner, server)),
                "第二个 project create personal 应返回成功"
        );
        Project currentProject = findProjectByName("Reload Current Project");
        Project starredProject = findProjectByName("Reload Starred Project");
        assertNotNull(currentProject, "重载前未找到当前项目测试数据");
        assertNotNull(starredProject, "重载前未找到星标项目测试数据");

        assertEquals(
                1,
                dispatcher.execute("todo project select " + currentProject.getId(), createSource(0, owner, server)),
                "project select 应返回成功"
        );
        assertEquals(
                1,
                dispatcher.execute("todo project star " + starredProject.getId(), createSource(0, owner, server)),
                "project star 应返回成功"
        );

        flushProjectSaves(server);
        assertEquals(Boolean.TRUE, Files.exists(getProjectPlayerStateFilePath(owner)), "project state 应写入玩家状态文件");

        reloadPersistentState();

        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedServer, reloadedOwner);

        assertEquals(currentProject.getId(), ProjectPackets.getActiveProjectId(reloadedOwner), "重载后未恢复当前项目");
        assertEquals(List.of(starredProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "重载后未恢复星标项目");

        CapturingCommandSourceStack currentSource = createSource(0, reloadedOwner, reloadedServer);
        int currentResult = createDispatcher().execute("todo project list current", currentSource);
        assertEquals(1, currentResult, "重载后 project list current 应返回成功");
        assertContainsText(currentSource.getSuccessMessages(), "Reload Current Project", "重载后 project list current 未输出当前项目");

        CapturingCommandSourceStack starSource = createSource(0, reloadedOwner, reloadedServer);
        int starResult = createDispatcher().execute("todo project list star", starSource);
        assertEquals(1, starResult, "重载后 project list star 应返回成功");
        assertContainsText(starSource.getSuccessMessages(), "Reload Starred Project", "重载后 project list star 未输出星标项目");
        assertNotContainsText(starSource.getSuccessMessages(), "Reload Current Project", "重载后 project list star 不应输出未星标项目");
    }

    /**
     * 鏍￠獙涓汉浠诲姟鍐欑洏鍚庨噸鏂板垵濮嬪寲锛屼粛鍙互閫氳繃 task list 璇诲彇鍒板凡鏈変换鍔°€?
     */
    /**
     * 验证团队项目的 current/star 状态在切换到单机环境时只会做运行期净化，
     * 不会永久写坏持久化文件，因此重新回到支持团队项目的环境后仍可恢复。
     */
    private static void shouldSanitizeCurrentAndStarredTeamProjectsAfterReloadIntoSingleplayerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000163", "reload-singleplayer-sanitize-user", false);
        TestMinecraftServer dedicatedServer = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(
                1,
                dispatcher.execute("todo project create team Reload Singleplayer Current Team", createSource(0, owner, dedicatedServer)),
                "创建当前团队项目应返回成功"
        );
        assertEquals(
                1,
                dispatcher.execute("todo project create team Reload Singleplayer Star Team", createSource(0, owner, dedicatedServer)),
                "创建星标团队项目应返回成功"
        );

        Project currentProject = findProjectByName("Reload Singleplayer Current Team");
        Project starredProject = findProjectByName("Reload Singleplayer Star Team");
        assertNotNull(currentProject, "未找到用于单机净化的当前团队项目");
        assertNotNull(starredProject, "未找到用于单机净化的星标团队项目");

        assertEquals(
                1,
                dispatcher.execute("todo project select " + currentProject.getId(), createSource(0, owner, dedicatedServer)),
                "设置团队当前项目应返回成功"
        );
        assertEquals(
                1,
                dispatcher.execute("todo project star " + starredProject.getId(), createSource(0, owner, dedicatedServer)),
                "设置团队星标项目应返回成功"
        );

        flushProjectSaves(dedicatedServer);
        assertEquals(Boolean.TRUE, Files.exists(getProjectPlayerStateFilePath(owner)), "团队项目状态应写入玩家状态文件");

        reloadPersistentState();

        TestServerPlayer singleplayerOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer singleplayerServer = createSingleplayerServer(singleplayerOwner);
        ProjectPackets.onPlayerJoin(singleplayerServer, singleplayerOwner);

        assertEquals(null, ProjectPackets.getActiveProjectId(singleplayerOwner), "切到单机后应清空团队当前项目");
        assertEquals(List.of(), ProjectPackets.getHudStarredProjectIds(singleplayerOwner), "切到单机后应清空团队星标项目");

        CapturingCommandSourceStack singleplayerCurrentSource = createSource(0, singleplayerOwner, singleplayerServer);
        int singleplayerCurrentResult = createDispatcher().execute("todo project list current", singleplayerCurrentSource);
        assertEquals(1, singleplayerCurrentResult, "单机环境下 project list current 应返回成功");
        assertContainsMessageKey(singleplayerCurrentSource.getSuccessMessages(), "command.todolist.project.list.current.empty", "单机环境下当前团队项目应表现为空");

        CapturingCommandSourceStack singleplayerStarSource = createSource(0, singleplayerOwner, singleplayerServer);
        int singleplayerStarResult = createDispatcher().execute("todo project list star", singleplayerStarSource);
        assertEquals(1, singleplayerStarResult, "单机环境下 project list star 应返回成功");
        assertContainsMessageKey(singleplayerStarSource.getSuccessMessages(), "command.todolist.project.list.star.empty", "单机环境下团队星标项目应表现为空");

        reloadPersistentState();

        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedDedicatedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedDedicatedServer, reloadedOwner);

        assertEquals(currentProject.getId(), ProjectPackets.getActiveProjectId(reloadedOwner), "重新进入支持团队项目的环境后应恢复团队当前项目");
        assertEquals(List.of(starredProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "重新进入支持团队项目的环境后应恢复团队星标项目");
    }

    /**
     * 验证本地单机世界在发布局域网后，服务端会重新恢复玩家的团队项目状态，而不要求玩家重连。
     */
    private static void shouldRestoreTeamProjectStateWhenPublishingLanWithoutReconnectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000291", "lan-republish-state-user", false);
        TestMinecraftServer dedicatedServer = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Republish Current Team", createSource(0, owner, dedicatedServer)), "创建局域网恢复 current 项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project create team Republish Star Team", createSource(0, owner, dedicatedServer)), "创建局域网恢复 starred 项目应返回成功");
        Project currentProject = findProjectByName("Republish Current Team");
        Project starredProject = findProjectByName("Republish Star Team");
        assertNotNull(currentProject, "未找到局域网恢复测试所需的当前项目");
        assertNotNull(starredProject, "未找到局域网恢复测试所需的星标项目");

        assertEquals(1, dispatcher.execute("todo project select " + currentProject.getId(), createSource(0, owner, dedicatedServer)), "设置局域网恢复 current 项目应返回成功");
        assertEquals(1, dispatcher.execute("todo project star " + starredProject.getId(), createSource(0, owner, dedicatedServer)), "设置局域网恢复 starred 项目应返回成功");
        flushProjectSaves(dedicatedServer);

        reloadPersistentState();

        TestServerPlayer singleplayerOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer singleplayerServer = createSingleplayerServer(singleplayerOwner);
        ProjectPackets.onPlayerJoin(singleplayerServer, singleplayerOwner);
        assertEquals(null, ProjectPackets.getActiveProjectId(singleplayerOwner), "未发布局域网前不应恢复团队 current 项目");
        assertEquals(List.of(), ProjectPackets.getHudStarredProjectIds(singleplayerOwner), "未发布局域网前不应恢复团队 starred 项目");

        singleplayerServer.testPublished = true;
        ProjectPackets.onRequestSyncProjectsPacket(
                singleplayerServer,
                singleplayerOwner,
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())
        );

        assertEquals(currentProject.getId(), ProjectPackets.getActiveProjectId(singleplayerOwner), "发布局域网后应立即恢复团队 current 项目");
        assertEquals(List.of(starredProject.getId()), ProjectPackets.getHudStarredProjectIds(singleplayerOwner), "发布局域网后应立即恢复团队 starred 项目");
    }

    /**
     * 验证单机环境里选择个人项目等交互再次写盘时，不会把暂时隐藏的团队星标状态永久覆盖掉。
     */
    private static void shouldKeepHiddenTeamStarredStateAfterSingleplayerProjectSelectionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000292", "singleplayer-star-preserve-user", false);
        TestMinecraftServer dedicatedServer = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Preserve Hidden Star Team", createSource(0, owner, dedicatedServer)), "创建隐藏星标保留团队项目应返回成功");
        Project teamProject = findProjectByName("Preserve Hidden Star Team");
        assertNotNull(teamProject, "未找到隐藏星标保留测试所需团队项目");

        assertEquals(1, dispatcher.execute("todo project star " + teamProject.getId(), createSource(0, owner, dedicatedServer)), "设置隐藏星标保留团队项目应返回成功");
        flushProjectSaves(dedicatedServer);

        reloadPersistentState();

        TestServerPlayer singleplayerOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer singleplayerServer = createSingleplayerServer(singleplayerOwner);
        ProjectPackets.onPlayerJoin(singleplayerServer, singleplayerOwner);
        assertEquals(List.of(), ProjectPackets.getHudStarredProjectIds(singleplayerOwner), "单机运行期不应直接暴露团队星标项目");

        addOwnedPersonalProject(singleplayerOwner, "singleplayer-visible-personal", "Singleplayer Visible Personal");
        Project personalProject = TodoListCommon.getProjectManager().getProject("singleplayer-visible-personal");
        assertNotNull(personalProject, "未找到用于模拟单机 GUI 选中逻辑的个人项目");
        ProjectPackets.setActiveProjectId(singleplayerOwner, personalProject.getId());

        reloadPersistentState();

        TestServerPlayer reloadedOwner = createPlayer(owner.getStringUUID(), owner.getName().getString(), false);
        TestMinecraftServer reloadedDedicatedServer = createServer(reloadedOwner);
        ProjectPackets.onPlayerJoin(reloadedDedicatedServer, reloadedOwner);

        assertEquals(List.of(teamProject.getId()), ProjectPackets.getHudStarredProjectIds(reloadedOwner), "单机里选择个人项目后，重新回到支持团队项目的环境仍应恢复团队星标项目");
    }

    private static void shouldListPersonalTasksAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000143", "reload-personal-task-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int addResult = dispatcher.execute("todo task add \"Reload Personal Task\" \"Persist me\" persist", createSource(0, player));
        assertEquals(1, addResult, "task add 搴旇繑鍥炴垚鍔?");
        assertEquals(Boolean.TRUE, Files.exists(getPersonalTaskFilePath(player)), "task add 鍚庡簲鍐欏叆涓汉浠诲姟鏂囦欢");

        reloadPersistentState();

        assertNotNull(findPersonalTaskByTitle(player, "Reload Personal Task"), "閲嶆柊鍒濆鍖栧悗搴旇鑳戒粠鎸佷箙鍖栨枃浠朵腑璇诲洖涓汉浠诲姟");
        CapturingCommandSourceStack source = createSource(0, player);
        int listResult = createDispatcher().execute("todo task list", source);
        assertEquals(1, listResult, "閲嶈浇鍚?task list 搴旇繑鍥炴垚鍔?");
        assertContainsText(source.getSuccessMessages(), "Reload Personal Task", "閲嶈浇鍚?task list 鏈緭鍑哄凡鎸佷箙鍖栫殑涓汉浠诲姟");
    }

    /**
     * 鏍￠獙鍥㈤槦椤圭洰涓庡洟闃熶换鍔″啓鐩樺悗閲嶆柊鍒濆鍖栵紝浠嶅彲閫氳繃 task listp 璇诲彇鍒板洟闃熶换鍔°€?
     */
    private static void shouldListTeamTasksAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000144", "reload-team-task-user", false);
        TestMinecraftServer server = createServer(manager);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int createResult = dispatcher.execute("todo project create team Reload Team Project", createSource(0, manager, server));
        assertEquals(1, createResult, "project create team 搴旇繑鍥炴垚鍔?");
        Project createdProject = findProjectByName("Reload Team Project");
        assertNotNull(createdProject, "搴旇鑳芥壘鍒板垰鍒涘缓鐨勫洟闃熼」鐩?");
        flushProjectSaves(server);
        assertEquals(Boolean.TRUE, Files.exists(getTeamProjectsFilePath()), "project create team 鍚庡簲鍐欏叆鍥㈤槦椤圭洰鏂囦欢");

        int addResult = dispatcher.execute("todo task addp " + createdProject.getId() + " \"Reload Team Task\" \"Persist team\" persist", createSource(0, manager, server));
        assertEquals(1, addResult, "task addp 搴旇繑鍥炴垚鍔?");
        assertEquals(Boolean.TRUE, Files.exists(getTeamTaskFilePath()), "task addp 鍚庡簲鍐欏叆鍥㈤槦浠诲姟鏂囦欢");

        reloadPersistentState();

        CapturingCommandSourceStack source = createSource(0, manager, createServer(manager));
        int listResult = createDispatcher().execute("todo task listp " + createdProject.getId(), source);
        assertEquals(1, listResult, "閲嶈浇鍚?task listp 搴旇繑鍥炴垚鍔?");
        assertContainsText(source.getSuccessMessages(), "Reload Team Task", "閲嶈浇鍚?task listp 鏈緭鍑哄凡鎸佷箙鍖栫殑鍥㈤槦浠诲姟");
    }

    /**
     * 校验 requestSyncProjects 在服务端尚无玩家状态时，会用客户端种子初始化 active/star/HUD 状态。
     */
    private static void shouldSeedMissingProjectStateFromRequestSyncSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000308", "sync-seed-user", false);
        TestMinecraftServer server = createServer(owner);
        addOwnedPersonalProject(owner, "sync-seed-active", "Sync Seed Active");
        addOwnedPersonalProject(owner, "sync-seed-star", "Sync Seed Star");
        assertEquals(Boolean.FALSE, Files.exists(getProjectPlayerStateFilePath(owner)), "测试前不应已有玩家项目状态文件");

        net.minecraft.network.FriendlyByteBuf packet = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        packet.writeBoolean(true);
        packet.writeUtf("sync-seed-active");
        packet.writeInt(1);
        packet.writeUtf("sync-seed-star");
        packet.writeBoolean(false);
        ProjectPackets.onRequestSyncProjectsPacket(server, owner, packet);

        assertEquals("sync-seed-active", ProjectPackets.getActiveProjectId(owner), "requestSyncProjects 未用客户端种子初始化 activeProjectId");
        assertListEquals(List.of("sync-seed-star"), ProjectPackets.getHudStarredProjectIds(owner), "requestSyncProjects 未用客户端种子初始化星标项目");
        assertEquals(Boolean.FALSE, ProjectPackets.isHudVisible(owner), "requestSyncProjects 未用客户端种子初始化 HUD 可见性");
        ProjectPlayerStateStorage.ProjectPlayerState storedState = new ProjectPlayerStateStorage().loadPlayerState(owner.getUUID());
        assertEquals("sync-seed-active", storedState.getActiveProjectId(), "客户端种子未写入玩家项目状态文件");
        assertListEquals(List.of("sync-seed-star"), storedState.getHudStarredProjectIds(), "客户端种子星标项目未写入玩家项目状态文件");
        assertEquals(Boolean.FALSE, storedState.isHudVisible(), "客户端种子 HUD 可见性未写入玩家项目状态文件");
    }

    private static void shouldPersistTeamProjectMemberCreateSettingAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000287", "reload-member-create-manager", false);
        TestMinecraftServer server = createServer(manager);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Reload Member Create Project", createSource(0, manager, server)), "创建 member-create 重载项目应返回成功");
        Project createdProject = findProjectByName("Reload Member Create Project");
        assertNotNull(createdProject, "未找到 member-create 重载测试项目");

        assertEquals(1, dispatcher.execute("todo project member-create " + createdProject.getId() + " on", createSource(0, manager, server)), "开启 member-create 应返回成功");
        assertEquals(Boolean.TRUE, createdProject.isAllowMemberCreate(), "开启 member-create 后项目状态应立即更新");
        flushProjectSaves(server);
        assertEquals(Boolean.TRUE, Files.exists(getTeamProjectsFilePath()), "member-create 应写入团队项目文件");

        reloadPersistentState();

        Project reloadedProject = TodoListCommon.getProjectManager().getProject(createdProject.getId());
        assertNotNull(reloadedProject, "重载后未恢复 member-create 测试项目");
        assertEquals(Boolean.TRUE, reloadedProject.isAllowMemberCreate(), "重载后 member-create 设置应保持开启");
    }

    /**
     * 校验成员角色变化在重载后仍会保持，并继续影响 lead/member 的权限判断。
     */
    private static void shouldPersistProjectMemberRolesAndPermissionsAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000288", "reload-role-manager", false);
        TestServerPlayer leadCandidate = createPlayer("00000000-0000-0000-0000-000000000289", "reload-role-lead", false);
        TestServerPlayer memberCandidate = createPlayer("00000000-0000-0000-0000-000000000290", "reload-role-member", false);
        TestMinecraftServer server = createServer(manager, leadCandidate, memberCandidate);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo project create team Reload Role Project", createSource(0, manager, server)), "创建角色重载项目应返回成功");
        Project createdProject = findProjectByName("Reload Role Project");
        assertNotNull(createdProject, "未找到角色重载测试项目");

        assertEquals(1, dispatcher.execute("todo project member add " + createdProject.getId() + " " + leadCandidate.getName().getString(), createSource(0, manager, server)), "添加 lead 候选成员应返回成功");
        assertEquals(1, dispatcher.execute("todo project member add " + createdProject.getId() + " " + memberCandidate.getName().getString(), createSource(0, manager, server)), "添加普通成员应返回成功");
        assertEquals(1, dispatcher.execute("todo project member role " + createdProject.getId() + " " + leadCandidate.getStringUUID() + " lead", createSource(0, manager, server)), "提升 lead 候选成员应返回成功");

        Task leadTask = createTeamTask(createdProject.getId(), "Reload Lead Permission Task");
        Task memberTask = createTeamTask(createdProject.getId(), "Reload Member Permission Task");
        saveTeamTasks(leadTask, memberTask);
        flushProjectSaves(server);
        assertEquals(Boolean.TRUE, Files.exists(getTeamProjectsFilePath()), "成员角色变化应写入团队项目文件");

        reloadPersistentState();

        Project reloadedProject = TodoListCommon.getProjectManager().getProject(createdProject.getId());
        assertNotNull(reloadedProject, "重载后未恢复角色测试项目");
        assertEquals(Project.ProjectRole.LEAD, reloadedProject.getMemberRole(leadCandidate.getStringUUID()), "重载后 lead 角色应保持不变");
        assertEquals(Project.ProjectRole.MEMBER, reloadedProject.getMemberRole(memberCandidate.getStringUUID()), "重载后普通成员角色应保持不变");

        TestServerPlayer reloadedManager = createPlayer(manager.getStringUUID(), manager.getName().getString(), false);
        TestServerPlayer reloadedLead = createPlayer(leadCandidate.getStringUUID(), leadCandidate.getName().getString(), false);
        TestServerPlayer reloadedMember = createPlayer(memberCandidate.getStringUUID(), memberCandidate.getName().getString(), false);
        TestMinecraftServer reloadedServer = createServer(reloadedManager, reloadedLead, reloadedMember);
        CommandDispatcher<CommandSourceStack> reloadedDispatcher = createDispatcher();

        int leadAssignResult = reloadedDispatcher.execute("todo task assignp " + createdProject.getId() + " " + leadTask.getId() + " " + reloadedMember.getStringUUID(), createSource(0, reloadedLead, reloadedServer));
        assertEquals(1, leadAssignResult, "重载后 lead 的 assignp 应仍然可用");
        Task reloadedLeadTask = loadTeamTaskById(leadTask.getId());
        assertEquals(reloadedMember.getStringUUID(), reloadedLeadTask.getAssigneeUuid(), "重载后 lead assignp 未写入目标成员 UUID");

        CapturingCommandSourceStack deniedSource = createSource(0, reloadedMember, reloadedServer);
        int memberAssignResult = reloadedDispatcher.execute("todo task assignp " + createdProject.getId() + " " + memberTask.getId() + " " + reloadedLead.getStringUUID(), deniedSource);
        assertEquals(0, memberAssignResult, "重载后普通成员 assignp 应返回失败");
        assertContainsMessageKey(deniedSource.getFailureMessages(), "command.todolist.task.project.permission_denied", "重载后普通成员 assignp 的错误键不正确");
        Task reloadedMemberTask = loadTeamTaskById(memberTask.getId());
        assertEquals(null, reloadedMemberTask.getAssigneeUuid(), "失败的普通成员 assignp 不应写入 assigneeUuid");
        assertEquals(null, reloadedMemberTask.getAssigneeName(), "失败的普通成员 assignp 不应写入 assigneeName");
    }

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
     * 校验单机未开局域网时会显式拒绝 join project。
     */
    /**
     * 验证未发起 join project 时，join accept 会显式拒绝审批。
     */
    private static void shouldRejectJoinDecisionWithoutPendingRequestSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000187", "manager-join-no-pending", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000188", "applicant-join-no-pending", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-no-pending-project", "Join No Pending Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        int result = dispatcher.execute("todo join accept join-no-pending-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, result, "无待审批记录时 join accept 应返回失败");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.no_pending_request", "join accept 缺少待审批记录时错误键不正确");
    }

    /**
     * 验证重复执行 join project 时会拒绝重复申请，且不会重复通知审批人。
     */
    private static void shouldRejectDuplicateJoinProjectRequestSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000195", "manager-join-duplicate", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000196", "applicant-join-duplicate", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-duplicate-project", "Join Duplicate Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-duplicate-project", createSource(0, applicant, server)), "首次 join project 应返回成功");
        int firstNotificationCount = countMessagesContaining(manager.getClientMessages(), "message.todolist.project.join.request_received");

        assertEquals(1, dispatcher.execute("todo join project join-duplicate-project", createSource(0, applicant, server)), "重复 join project 当前仍应走命令成功返回");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.already_requested", "重复 join project 未提示已提交过申请");
        assertEquals(firstNotificationCount, countMessagesContaining(manager.getClientMessages(), "message.todolist.project.join.request_received"), "重复 join project 不应再次通知审批人");
    }

    private static void shouldRejectJoinProjectInSingleplayerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000158", "singleplayer-join-user", false);
        TestMinecraftServer server = createSingleplayerServer(applicant);
        addTeamProject(applicant, "singleplayer-join-project", "Singleplayer Join Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, applicant, server);

        int result = dispatcher.execute("todo join project singleplayer-join-project", source);
        assertEquals(0, result, "单机未开局域网时 join project 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "message.todolist.project.join.singleplayer_forbidden", "单机 join project 错误键不正确");
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

        assertEquals(1, dispatcher.execute("todo join project join-approve-project", createSource(0, applicant, server)), "join project 应返回成功");
        int result = dispatcher.execute("todo join accept join-approve-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(1, result, "join accept 成功时应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "join accept 未将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "join accept 未通知申请人审批通过");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.approved", "join accept 未通知审批人审批结果");
    }

    /**
     * 校验申请通过后，成员视角可以查看团队任务列表。
     */
    /**
     * 验证同一申请人在不同项目上的 join 审批结果彼此隔离。
     */
    /**
     * 验证申请人临时离线时审批会保留待处理状态，重新上线后仍可审批通过。
     */
    private static void shouldApproveJoinRequestAfterApplicantReconnectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000197", "manager-join-reconnect", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000198", "applicant-join-reconnect", false);
        TestMinecraftServer initialServer = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-reconnect-project", "Join Reconnect Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-reconnect-project", createSource(0, applicant, initialServer)), "join project 应返回成功");

        TestServerPlayer offlineManager = createPlayer(manager.getStringUUID(), manager.getName().getString(), false);
        TestMinecraftServer offlineServer = createServer(offlineManager);
        int offlineResult = dispatcher.execute("todo join accept join-reconnect-project " + applicant.getStringUUID(), createSource(0, offlineManager, offlineServer));
        assertEquals(0, offlineResult, "申请人离线时 join accept 应返回失败");
        assertContainsMessageKey(offlineManager.getClientMessages(), "message.todolist.project.join.applicant_offline", "申请人离线时审批提示不正确");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "申请人离线时不应被提前加入项目");

        TestServerPlayer onlineManager = createPlayer(manager.getStringUUID(), manager.getName().getString(), false);
        TestServerPlayer reconnectedApplicant = createPlayer(applicant.getStringUUID(), applicant.getName().getString(), false);
        TestMinecraftServer onlineServer = createServer(onlineManager, reconnectedApplicant);
        int onlineResult = dispatcher.execute("todo join accept join-reconnect-project " + applicant.getStringUUID(), createSource(0, onlineManager, onlineServer));
        assertEquals(1, onlineResult, "申请人重新上线后 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "申请人重新上线后应成功加入项目");
        assertContainsMessageKey(reconnectedApplicant.getClientMessages(), "message.todolist.project.join.accepted", "申请人重新上线后未收到通过提示");
    }

    /**
     * 校验加入申请发出后如果项目已被删除，后续 join accept 会返回项目无效错误。
     */
    private static void shouldRejectJoinDecisionAfterProjectRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000204", "manager-join-project-removed", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000205", "applicant-join-project-removed", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-project-removed", "Join Project Removed");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-project-removed", createSource(0, applicant, server)), "项目删除前 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove join-project-removed", createSource(0, manager, server)), "删除项目前置确认应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, manager, server)), "删除项目确认应返回成功");
        assertNull(TodoListCommon.getProjectManager().getProject("join-project-removed"), "确认删除后团队项目应被移除");

        int result = dispatcher.execute("todo join accept join-project-removed " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, result, "项目已删除后 join accept 应返回失败");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.invalid_project", "项目已删除后的 join accept 错误键不正确");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "项目已删除后不应再通知申请人加入成功");
    }

    /**
     * 校验加入申请发出后如果项目已被删除，后续 join deny 也会返回项目无效错误。
     */
    private static void shouldRejectJoinDenyAfterProjectRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000213", "manager-join-deny-project-removed", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000214", "applicant-join-deny-project-removed", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-deny-project-removed", "Join Deny Project Removed");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-project-removed", createSource(0, applicant, server)), "项目删除前 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove join-deny-project-removed", createSource(0, manager, server)), "删除项目前置确认应返回成功");
        assertEquals(1, dispatcher.execute("todo project remove confirm", createSource(0, manager, server)), "删除项目确认应返回成功");

        int result = dispatcher.execute("todo join deny join-deny-project-removed " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, result, "项目已删除后 join deny 应返回失败");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.invalid_project", "项目已删除后的 join deny 错误键不正确");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "项目已删除后不应再通知申请人被拒绝");
    }

    /**
     * 校验通过网络删除项目时，也会清理待审批加入请求缓存。
     */
    private static void shouldClearPendingJoinRequestWhenDeletingProjectViaPacketSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000309", "packet-delete-manager", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000310", "packet-delete-applicant", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "packet-delete-join-project", "Packet Delete Join Project");

        ProjectPackets.requestJoinProject(server, applicant, project.getId());
        applicant.getClientMessages().clear();
        manager.getClientMessages().clear();

        net.minecraft.network.FriendlyByteBuf deletePacket = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        deletePacket.writeUtf(project.getId());
        ProjectPackets.onDeleteProjectPacket(server, manager, deletePacket);
        assertNull(TodoListCommon.getProjectManager().getProject(project.getId()), "网络删除项目后项目应不存在");

        addTeamProject(manager, project.getId(), "Packet Delete Join Project Recreated");
        ProjectPackets.requestJoinProject(server, applicant, project.getId());

        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.already_requested", "网络删除项目后不应残留待审批缓存导致重复申请被拒绝");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.request_received", "网络删除并重建同 ID 项目后应允许重新发起加入申请");
    }

    private static void shouldRejectRepeatedJoinAcceptAfterApprovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000220", "manager-join-approve-repeat", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000221", "applicant-join-approve-repeat", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-approve-repeat-project", "Join Approve Repeat Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-approve-repeat-project", createSource(0, applicant, server)), "棣栨 join project 搴旇繑鍥炴垚鍔?");
        assertEquals(1, dispatcher.execute("todo join accept join-approve-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server)), "棣栨 join accept 搴旇繑鍥炴垚鍔?");

        int repeatedResult = dispatcher.execute("todo join accept join-approve-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, repeatedResult, "閲嶅 join accept 搴旇繑鍥炲け璐?");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.already_member", "閲嶅 join accept 鐨勯敊璇敭涓嶆纭?");
    }

    /**
     * 校验审批拒绝后重复执行 join deny 会因无待审批记录而失败。
     */
    private static void shouldRejectRepeatedJoinDenyAfterDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000222", "manager-join-deny-repeat", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000223", "applicant-join-deny-repeat", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-deny-repeat-project", "Join Deny Repeat Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-repeat-project", createSource(0, applicant, server)), "棣栨 join project 搴旇繑鍥炴垚鍔?");
        assertEquals(1, dispatcher.execute("todo join deny join-deny-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server)), "棣栨 join deny 搴旇繑鍥炴垚鍔?");

        int repeatedResult = dispatcher.execute("todo join deny join-deny-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, repeatedResult, "閲嶅 join deny 搴旇繑鍥炲け璐?");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.no_pending_request", "閲嶅 join deny 鐨勯敊璇敭涓嶆纭?");
    }

    private static void shouldKeepJoinDecisionsIsolatedAcrossProjectsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000189", "manager-join-isolated-a", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000190", "manager-join-isolated-b", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000191", "applicant-join-isolated", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project acceptedProject = addTeamProject(firstManager, "join-isolated-accepted", "Join Isolated Accepted");
        Project deniedProject = addTeamProject(secondManager, "join-isolated-denied", "Join Isolated Denied");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-isolated-accepted", createSource(0, applicant, server)), "第一个 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join project join-isolated-denied", createSource(0, applicant, server)), "第二个 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join accept join-isolated-accepted " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个 join accept 应返回成功");
        assertEquals(1, dispatcher.execute("todo join deny join-isolated-denied " + applicant.getStringUUID(), createSource(0, secondManager, server)), "第二个 join deny 应返回成功");

        assertEquals(Project.ProjectRole.MEMBER, acceptedProject.getMemberRole(applicant.getStringUUID()), "通过的项目应将申请人加入成员列表");
        assertEquals(null, deniedProject.getMemberRole(applicant.getStringUUID()), "被拒绝的项目不应将申请人加入成员列表");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "申请人未收到通过提示");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "申请人未收到拒绝提示");
    }

    private static void shouldListTeamTasksForJoinedMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000152", "manager-team-list-member", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000153", "applicant-team-list-member", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-visible-project", "Join Visible Project");
        Task task = createTeamTask(project.getId(), "Joined Member Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-visible-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join accept join-visible-project " + applicant.getStringUUID(), createSource(0, manager, server)), "join accept 应返回成功");

        CapturingCommandSourceStack source = createSource(0, applicant, server);
        int result = dispatcher.execute("todo task listp join-visible-project", source);
        assertEquals(1, result, "加入后的成员查看团队任务应返回成功");
        assertContainsText(source.getSuccessMessages(), "Joined Member Task", "加入后的成员未看到团队任务");
    }

    /**
     * 校验项目经理可以通过 join deny 拒绝加入申请且不会把申请人加入项目。
     */
    /**
     * 校验加入后的成员查看大量团队任务时，也能按页翻页浏览。
     */
    private static void shouldPaginateTeamTaskListForJoinedMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000224", "manager-team-page-member", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000225", "applicant-team-page-member", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-page-project", "Join Page Project");
        List<Task> tasks = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            tasks.add(createTeamTask(project.getId(), "Joined Member Page Task " + index));
        }
        saveTeamTasks(tasks.toArray(Task[]::new));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-page-project", createSource(0, applicant, server)), "鍒嗛〉鍓?join project 搴旇繑鍥炴垚鍔?");
        assertEquals(1, dispatcher.execute("todo join accept join-page-project " + applicant.getStringUUID(), createSource(0, manager, server)), "鍒嗛〉鍓?join accept 搴旇繑鍥炴垚鍔?");

        CapturingCommandSourceStack firstPageSource = createSource(0, applicant, server);
        int firstPageResult = dispatcher.execute("todo task listp join-page-project", firstPageSource);
        assertEquals(1, firstPageResult, "鎴愬憳 task listp 绗竴椤靛簲杩斿洖鎴愬姛");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.project.list.summary", "鎴愬憳 task listp 绗竴椤垫湭杈撳嚭椤圭洰姹囨€绘秷鎭?");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "鎴愬憳 task listp 绗竴椤垫湭杈撳嚭椤电爜鐘舵€?");
        assertEquals(10, countMessagesContaining(firstPageSource.getSuccessMessages(), "Joined Member Page Task "), "鎴愬憳 task listp 绗竴椤靛簲鏄剧ず 10 鏉′换鍔?");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "鎴愬憳 task listp 绗竴椤垫湭杈撳嚭涓€涓嬮〉鎸夐挳");

        CapturingCommandSourceStack secondPageSource = createSource(0, applicant, server);
        int secondPageResult = dispatcher.execute("todo task more", secondPageSource);
        assertEquals(1, secondPageResult, "鎴愬憳 task more 绗簩椤靛簲杩斿洖鎴愬姛");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "鎴愬憳 task more 绗簩椤垫湭杈撳嚭椤电爜鐘舵€?");
        assertEquals(2, countMessagesContaining(secondPageSource.getSuccessMessages(), "Joined Member Page Task "), "鎴愬憳 task more 绗簩椤靛簲鍙樉绀哄墿浣?2 鏉′换鍔?");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.prev_button", "鎴愬憳 task more 绗簩椤垫湭杈撳嚭涓婁竴椤垫寜閽?");
    }

    /**
     * 校验团队 listp 翻到下一页后，task prev 可以回到第一页。
     */
    private static void shouldPaginateTeamTaskListWithPrevForJoinedMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000291", "manager-team-prev-member", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000292", "applicant-team-prev-member", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-prev-page-project", "Join Prev Page Project");
        List<Task> tasks = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            tasks.add(createTeamTask(project.getId(), "Joined Prev Page Task " + index));
        }
        saveTeamTasks(tasks.toArray(Task[]::new));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-prev-page-project", createSource(0, applicant, server)), "翻页前 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join accept join-prev-page-project " + applicant.getStringUUID(), createSource(0, manager, server)), "翻页前 join accept 应返回成功");
        assertEquals(1, dispatcher.execute("todo task listp join-prev-page-project", createSource(0, applicant, server)), "团队 listp 第一页应返回成功");
        assertEquals(1, dispatcher.execute("todo task more", createSource(0, applicant, server)), "团队 task more 第二页应返回成功");

        CapturingCommandSourceStack previousPageSource = createSource(0, applicant, server);
        int previousPageResult = dispatcher.execute("todo task prev", previousPageSource);
        assertEquals(1, previousPageResult, "团队 task prev 应返回成功");
        assertContainsMessageKey(previousPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "团队 task prev 未输出页码状态");
        assertEquals(10, countMessagesContaining(previousPageSource.getSuccessMessages(), "Joined Prev Page Task "), "团队 task prev 返回后应再次显示 10 条任务");
        assertContainsMessageKey(previousPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "团队 task prev 返回第一页后未输出下一页按钮");
    }

    private static void shouldDenyJoinRequestSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000042", "manager-join-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000142", "applicant-join-deny", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-deny-project", "Join Deny Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-project", createSource(0, applicant, server)), "join project 应返回成功");
        int result = dispatcher.execute("todo join deny join-deny-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(1, result, "join deny 成功时应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "join deny 不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "join deny 未通知申请人审批拒绝");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.rejected", "join deny 未通知审批人审批结果");
    }

    /**
     * 校验申请被拒绝后，申请人无法继续查看该团队项目的任务列表。
     */
    private static void shouldAllowJoinRequestAgainAfterDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000228", "manager-join-reapply", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000229", "applicant-join-reapply", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-reapply-project", "Join Reapply Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-reapply-project", createSource(0, applicant, server)), "绗竴娆?join project 搴旇繑鍥炴垚鍔?");
        assertEquals(1, dispatcher.execute("todo join deny join-reapply-project " + applicant.getStringUUID(), createSource(0, manager, server)), "绗竴娆?join deny 搴旇繑鍥炴垚鍔?");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "琚嫆缁濆悗鐢宠浜轰笉搴斿姞鍏ラ」鐩?");

        assertEquals(1, dispatcher.execute("todo join project join-reapply-project", createSource(0, applicant, server)), "琚嫆缁濆悗鍐嶆 join project 搴旇繑鍥炴垚鍔?");
        assertEquals(1, dispatcher.execute("todo join accept join-reapply-project " + applicant.getStringUUID(), createSource(0, manager, server)), "鍐嶆鐢宠鍚庣殑 join accept 搴旇繑鍥炴垚鍔?");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "鍐嶆鐢宠鍚庡簲鎴愬姛鍔犲叆椤圭洰");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "鍐嶆鐢宠閫氳繃鍚庢湭閫氱煡鐢宠浜?");
    }

    private static void shouldRejectTeamTaskListAfterJoinDeniedSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000146", "manager-join-deny-list", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000147", "applicant-join-deny-list", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-deny-list-project", "Join Deny List Project");
        saveTeamTasks(createTeamTask(project.getId(), "Denied Access Task"));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-list-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertEquals(
                1,
                dispatcher.execute("todo join deny join-deny-list-project " + applicant.getStringUUID(), createSource(0, manager, server)),
                "join deny 应返回成功"
        );

        CapturingCommandSourceStack source = createSource(0, applicant, server);
        int result = dispatcher.execute("todo task listp join-deny-list-project", source);
        assertEquals(0, result, "被拒绝的申请人查看团队任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "被拒绝后查看团队任务的错误键不正确");
    }

    /**
     * 校验申请被拒绝后，非成员执行 task more 不会意外继承团队列表分页会话。
     */
    private static void shouldRejectTaskMoreAfterDeniedTeamTaskListWithoutSessionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000230", "manager-join-deny-more", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000231", "applicant-join-deny-more", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-deny-more-project", "Join Deny More Project");
        List<Task> tasks = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            tasks.add(createTeamTask(project.getId(), "Denied Page Task " + index));
        }
        saveTeamTasks(tasks.toArray(Task[]::new));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-more-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertEquals(
                1,
                dispatcher.execute("todo join deny join-deny-more-project " + applicant.getStringUUID(), createSource(0, manager, server)),
                "join deny 应返回成功"
        );

        CapturingCommandSourceStack listSource = createSource(0, applicant, server);
        int listResult = dispatcher.execute("todo task listp join-deny-more-project", listSource);
        assertEquals(0, listResult, "被拒绝的申请人查看团队任务应返回失败");
        assertContainsMessageKey(listSource.getFailureMessages(), "command.todolist.task.project.permission_denied", "被拒绝后查看团队任务的错误键不正确");

        CapturingCommandSourceStack moreSource = createSource(0, applicant, server);
        int moreResult = dispatcher.execute("todo task more", moreSource);
        assertEquals(0, moreResult, "无有效分页会话时 task more 应返回失败");
        assertContainsMessageKey(moreSource.getFailureMessages(), "command.todolist.task.list.page.no_session", "被拒绝后执行 task more 的错误键不正确");
    }

    /**
     * 校验非成员在被拒绝后执行 task prev 也不会意外继承团队列表分页会话。
     */
    private static void shouldRejectTaskPrevAfterDeniedTeamTaskListWithoutSessionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000293", "manager-join-deny-prev", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000294", "applicant-join-deny-prev", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-deny-prev-project", "Join Deny Prev Project");
        List<Task> tasks = new ArrayList<>();
        for (int index = 1; index <= 12; index++) {
            tasks.add(createTeamTask(project.getId(), "Denied Prev Task " + index));
        }
        saveTeamTasks(tasks.toArray(Task[]::new));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-prev-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join deny join-deny-prev-project " + applicant.getStringUUID(), createSource(0, manager, server)), "join deny 应返回成功");

        CapturingCommandSourceStack listSource = createSource(0, applicant, server);
        assertEquals(0, dispatcher.execute("todo task listp join-deny-prev-project", listSource), "被拒绝后 task listp 应返回失败");
        assertContainsMessageKey(listSource.getFailureMessages(), "command.todolist.task.project.permission_denied", "被拒绝后 task listp 的错误键不正确");

        CapturingCommandSourceStack prevSource = createSource(0, applicant, server);
        int prevResult = dispatcher.execute("todo task prev", prevSource);
        assertEquals(0, prevResult, "无有效分页会话时 task prev 应返回失败");
        assertContainsMessageKey(prevSource.getFailureMessages(), "command.todolist.task.list.page.no_session", "被拒绝后执行 task prev 的错误键不正确");
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
    /**
     * 验证项目经理先指派任务后，其他普通成员不能再 claim 该任务。
     */
    private static void shouldRejectClaimingAssignedTeamTaskAfterManagerAssignmentSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000199", "manager-claim-assigned", false);
        TestServerPlayer firstMember = createPlayer("00000000-0000-0000-0000-000000000200", "member-claim-assigned-a", false);
        TestServerPlayer secondMember = createPlayer("00000000-0000-0000-0000-000000000201", "member-claim-assigned-b", false);
        TestMinecraftServer server = createServer(manager, firstMember, secondMember);
        Project project = addTeamProject(manager, "team-claim-assigned", "Claim Assigned");
        project.addMember(firstMember.getStringUUID(), Project.ProjectRole.MEMBER, firstMember.getName().getString());
        project.addMember(secondMember.getStringUUID(), Project.ProjectRole.MEMBER, secondMember.getName().getString());
        Task task = createTeamTask(project.getId(), "Assigned Before Claim Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task assignp team-claim-assigned " + task.getId() + " " + firstMember.getStringUUID(), createSource(0, manager, server)), "先执行 assignp 应返回成功");
        CapturingCommandSourceStack source = createSource(0, secondMember, server);
        int result = dispatcher.execute("todo task claimp team-claim-assigned " + task.getId(), source);
        assertEquals(0, result, "任务已被指派后其他成员 claimp 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "已指派任务的 claimp 错误键不正确");
        assertEquals(firstMember.getStringUUID(), loadTeamTaskById(task.getId()).getAssigneeUuid(), "失败的 claimp 不应改写原有 assignee");
    }

    /**
     * 校验项目经理与 lead 都可以领取未分配的团队任务。
     */
    private static void shouldAllowManagerAndLeadClaimingUnassignedTeamTasksSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000295", "manager-claim-role", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000296", "lead-claim-role", false);
        TestMinecraftServer server = createServer(manager, lead);
        Project project = addTeamProject(manager, "team-claim-role", "Claim Role");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        Task managerTask = createTeamTask(project.getId(), "Manager Claimable Task");
        Task leadTask = createTeamTask(project.getId(), "Lead Claimable Task");
        saveTeamTasks(managerTask, leadTask);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task claimp team-claim-role " + managerTask.getId(), createSource(0, manager, server)), "项目经理 claimp 未分配任务应返回成功");
        assertEquals(1, dispatcher.execute("todo task claimp team-claim-role " + leadTask.getId(), createSource(0, lead, server)), "lead claimp 未分配任务应返回成功");

        Task savedManagerTask = loadTeamTaskById(managerTask.getId());
        assertEquals(manager.getStringUUID(), savedManagerTask.getAssigneeUuid(), "项目经理 claimp 未写入 assigneeUuid");
        assertEquals(manager.getName().getString(), savedManagerTask.getAssigneeName(), "项目经理 claimp 未写入 assigneeName");

        Task savedLeadTask = loadTeamTaskById(leadTask.getId());
        assertEquals(lead.getStringUUID(), savedLeadTask.getAssigneeUuid(), "lead claimp 未写入 assigneeUuid");
        assertEquals(lead.getName().getString(), savedLeadTask.getAssigneeName(), "lead claimp 未写入 assigneeName");
    }

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
     * 校验项目经理可以把同一条团队任务从一个成员重新指派给另一个成员。
     */
    /**
     * 校验 lead 角色可以指派团队任务。
     */
    private static void shouldAllowLeadAssigningTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000254", "manager-assign-lead", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000255", "lead-assign-lead", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000256", "member-assign-lead", false);
        TestMinecraftServer server = createServer(manager, lead, member);
        Project project = addTeamProject(manager, "team-assign-lead", "Assign Lead");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Lead Assignable Team Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task assignp team-assign-lead " + task.getId() + " " + member.getStringUUID(), createSource(0, lead, server));
        assertEquals(1, result, "lead assignp 应返回成功");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(member.getStringUUID(), savedTask.getAssigneeUuid(), "lead assignp 未写入 assigneeUuid");
        assertEquals(member.getName().getString(), savedTask.getAssigneeName(), "lead assignp 未写入 assigneeName");
    }

    private static void shouldReassignTeamTaskToAnotherMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000206", "manager-reassign-ok", false);
        TestServerPlayer firstMember = createPlayer("00000000-0000-0000-0000-000000000207", "member-reassign-a", false);
        TestServerPlayer secondMember = createPlayer("00000000-0000-0000-0000-000000000208", "member-reassign-b", false);
        TestMinecraftServer server = createServer(manager, firstMember, secondMember);
        Project project = addTeamProject(manager, "team-reassign-success", "Reassign Success");
        project.addMember(firstMember.getStringUUID(), Project.ProjectRole.MEMBER, firstMember.getName().getString());
        project.addMember(secondMember.getStringUUID(), Project.ProjectRole.MEMBER, secondMember.getName().getString());
        Task task = createTeamTask(project.getId(), "Reassignable Team Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task assignp team-reassign-success " + task.getId() + " " + firstMember.getStringUUID(), createSource(0, manager, server)), "第一次 assignp 应返回成功");
        assertEquals(1, dispatcher.execute("todo task assignp team-reassign-success " + task.getId() + " " + secondMember.getStringUUID(), createSource(0, manager, server)), "重新 assignp 应返回成功");

        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(secondMember.getStringUUID(), savedTask.getAssigneeUuid(), "重新 assignp 后 assigneeUuid 应更新为第二个成员");
        assertEquals(secondMember.getName().getString(), savedTask.getAssigneeName(), "重新 assignp 后 assigneeName 应更新为第二个成员");
    }

    /**
     * 校验团队任务先被成员领取后，再被经理重新指派时，任务归属和后续可操作人会一起切换。
     */
    private static void shouldKeepTaskStateConsistentWhenManagerReassignsClaimedTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000215", "manager-reassign-claimed", false);
        TestServerPlayer firstMember = createPlayer("00000000-0000-0000-0000-000000000216", "member-reassign-claimed-a", false);
        TestServerPlayer secondMember = createPlayer("00000000-0000-0000-0000-000000000217", "member-reassign-claimed-b", false);
        TestMinecraftServer server = createServer(manager, firstMember, secondMember);
        Project project = addTeamProject(manager, "team-reassign-claimed", "Reassign Claimed");
        project.addMember(firstMember.getStringUUID(), Project.ProjectRole.MEMBER, firstMember.getName().getString());
        project.addMember(secondMember.getStringUUID(), Project.ProjectRole.MEMBER, secondMember.getName().getString());
        Task task = createTeamTask(project.getId(), "Claim Then Reassign Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task claimp team-reassign-claimed " + task.getId(), createSource(0, firstMember, server)), "成员先 claimp 应返回成功");
        assertEquals(1, dispatcher.execute("todo task assignp team-reassign-claimed " + task.getId() + " " + secondMember.getStringUUID(), createSource(0, manager, server)), "经理重新 assignp 应返回成功");

        Task reassignedTask = loadTeamTaskById(task.getId());
        assertEquals(secondMember.getStringUUID(), reassignedTask.getAssigneeUuid(), "重新指派后 assigneeUuid 应切换到第二个成员");
        assertEquals(secondMember.getName().getString(), reassignedTask.getAssigneeName(), "重新指派后 assigneeName 应切换到第二个成员");

        CapturingCommandSourceStack firstMemberSource = createSource(0, firstMember, server);
        int firstMemberDoneResult = dispatcher.execute("todo task donep team-reassign-claimed " + task.getId(), firstMemberSource);
        assertEquals(0, firstMemberDoneResult, "原领取成员在被重新指派后 donep 应返回失败");
        assertContainsMessageKey(firstMemberSource.getFailureMessages(), "command.todolist.task.project.permission_denied", "原领取成员被重新指派后的 donep 错误键不正确");

        int secondMemberDoneResult = dispatcher.execute("todo task donep team-reassign-claimed " + task.getId(), createSource(0, secondMember, server));
        assertEquals(1, secondMemberDoneResult, "新 assignee 执行 donep 应返回成功");
        Task completedTask = loadTeamTaskById(task.getId());
        assertEquals(Boolean.TRUE, completedTask.isCompleted(), "新 assignee 完成后任务应被标记为已完成");
        assertEquals(secondMember.getStringUUID(), completedTask.getAssigneeUuid(), "完成后 assigneeUuid 应保持为新成员");
    }

    /**
     * 校验已领取任务的成员可以成功完成团队任务。
     */
    /**
     * 验证多成员对不同团队任务的操作彼此独立，不会串改其他任务状态。
     */
    private static void shouldKeepIndependentTeamTaskStateAcrossMultipleMemberOperationsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000192", "manager-task-isolated", false);
        TestServerPlayer firstMember = createPlayer("00000000-0000-0000-0000-000000000193", "member-task-isolated-a", false);
        TestServerPlayer secondMember = createPlayer("00000000-0000-0000-0000-000000000194", "member-task-isolated-b", false);
        TestMinecraftServer server = createServer(manager, firstMember, secondMember);
        Project project = addTeamProject(manager, "team-task-isolated", "Team Task Isolated");
        project.addMember(firstMember.getStringUUID(), Project.ProjectRole.MEMBER, firstMember.getName().getString());
        project.addMember(secondMember.getStringUUID(), Project.ProjectRole.MEMBER, secondMember.getName().getString());

        Task claimedTask = createTeamTask(project.getId(), "Isolated Claimed Task");
        Task assignedTask = createTeamTask(project.getId(), "Isolated Assigned Task");
        saveTeamTasks(claimedTask, assignedTask);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task claimp team-task-isolated " + claimedTask.getId(), createSource(0, firstMember, server)), "第一个成员 claimp 应返回成功");
        assertEquals(1, dispatcher.execute("todo task assignp team-task-isolated " + assignedTask.getId() + " " + secondMember.getStringUUID(), createSource(0, manager, server)), "项目经理 assignp 应返回成功");
        assertEquals(1, dispatcher.execute("todo task donep team-task-isolated " + claimedTask.getId(), createSource(0, firstMember, server)), "第一个成员 donep 应返回成功");

        Task savedClaimedTask = loadTeamTaskById(claimedTask.getId());
        Task savedAssignedTask = loadTeamTaskById(assignedTask.getId());
        assertEquals(Boolean.TRUE, savedClaimedTask.isCompleted(), "被完成的任务应标记为已完成");
        assertEquals(firstMember.getStringUUID(), savedClaimedTask.getAssigneeUuid(), "被完成的任务应保留原领取人");
        assertEquals(secondMember.getStringUUID(), savedAssignedTask.getAssigneeUuid(), "另一条任务应保留被指派的成员");
        assertEquals(Boolean.FALSE, savedAssignedTask.isCompleted(), "另一条任务不应被串改为已完成");
    }

    /**
     * 校验已完成的团队任务不能再执行 assignp，且不会改写 assignee。
     */
    private static void shouldRejectAssigningCompletedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000209", "manager-assign-completed", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000210", "member-assign-completed", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-assign-completed", "Assign Completed");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Completed Assign Task");
        task.setCompleted(true);
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, manager, server);

        int result = dispatcher.execute("todo task assignp team-assign-completed " + task.getId() + " " + member.getStringUUID(), source);
        assertEquals(0, result, "已完成任务执行 assignp 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "已完成任务的 assignp 错误键不正确");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(null, savedTask.getAssigneeUuid(), "失败的 assignp 不应写入 assigneeUuid");
        assertEquals(null, savedTask.getAssigneeName(), "失败的 assignp 不应写入 assigneeName");
    }

    /**
     * 校验已完成的团队任务不能再执行 claimp，且不会改写 assignee。
     */
    private static void shouldRejectClaimingCompletedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000211", "manager-claim-completed", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000212", "member-claim-completed", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-claim-completed", "Claim Completed");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Completed Claim Task");
        task.setCompleted(true);
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo task claimp team-claim-completed " + task.getId(), source);
        assertEquals(0, result, "已完成任务执行 claimp 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "已完成任务的 claimp 错误键不正确");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(null, savedTask.getAssigneeUuid(), "失败的 claimp 不应写入 assigneeUuid");
        assertEquals(null, savedTask.getAssigneeName(), "失败的 claimp 不应写入 assigneeName");
    }

    /**
     * 校验已完成的团队任务再次执行 donep 时会返回已完成提示，并保持原状态不变。
     */
    /**
     * 校验普通成员不能直接完成未分配给自己的团队任务。
     */
    /**
     * 校验 lead 对已完成任务执行 assignp 与 claimp 时都会被拒绝，并保持原状态不变。
     */
    private static void shouldRejectLeadAssigningAndClaimingCompletedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000297", "manager-completed-lead", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000298", "lead-completed-lead", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000299", "member-completed-lead", false);
        TestMinecraftServer server = createServer(manager, lead, member);
        Project project = addTeamProject(manager, "team-completed-lead", "Completed Lead");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Lead Completed Conflict Task");
        task.setCompleted(true);
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack assignSource = createSource(0, lead, server);
        int assignResult = dispatcher.execute("todo task assignp team-completed-lead " + task.getId() + " " + member.getStringUUID(), assignSource);
        assertEquals(0, assignResult, "lead 对已完成任务 assignp 应返回失败");
        assertContainsMessageKey(assignSource.getFailureMessages(), "command.todolist.task.project.permission_denied", "lead 对已完成任务 assignp 的错误键不正确");

        CapturingCommandSourceStack claimSource = createSource(0, lead, server);
        int claimResult = dispatcher.execute("todo task claimp team-completed-lead " + task.getId(), claimSource);
        assertEquals(0, claimResult, "lead 对已完成任务 claimp 应返回失败");
        assertContainsMessageKey(claimSource.getFailureMessages(), "command.todolist.task.project.permission_denied", "lead 对已完成任务 claimp 的错误键不正确");

        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(Boolean.TRUE, savedTask.isCompleted(), "失败的 assignp/claimp 不应改动已完成状态");
        assertEquals(null, savedTask.getAssigneeUuid(), "失败的 assignp/claimp 不应写入 assigneeUuid");
        assertEquals(null, savedTask.getAssigneeName(), "失败的 assignp/claimp 不应写入 assigneeName");
    }

    private static void shouldRejectCompletingUnassignedTeamTaskForRegularMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000257", "manager-done-unassigned", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000258", "member-done-unassigned", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-done-unassigned", "Done Unassigned");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Unassigned Team Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo task donep team-done-unassigned " + task.getId(), source);
        assertEquals(0, result, "普通成员完成未分配团队任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "普通成员 donep 未分配任务的错误键不正确");
        assertEquals(Boolean.FALSE, loadTeamTaskById(task.getId()).isCompleted(), "失败的 donep 不应把任务标记为已完成");
    }

    /**
     * 校验 lead 角色可以完成已分配的团队任务。
     */
    /**
     * 校验普通成员不能完成指派给其他成员的团队任务。
     */
    private static void shouldRejectCompletingTaskAssignedToOtherMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000300", "manager-done-other", false);
        TestServerPlayer firstMember = createPlayer("00000000-0000-0000-0000-000000000301", "member-done-other-a", false);
        TestServerPlayer secondMember = createPlayer("00000000-0000-0000-0000-000000000302", "member-done-other-b", false);
        TestMinecraftServer server = createServer(manager, firstMember, secondMember);
        Project project = addTeamProject(manager, "team-done-other", "Done Other");
        project.addMember(firstMember.getStringUUID(), Project.ProjectRole.MEMBER, firstMember.getName().getString());
        project.addMember(secondMember.getStringUUID(), Project.ProjectRole.MEMBER, secondMember.getName().getString());
        Task task = createTeamTask(project.getId(), "Assigned To Other Team Task");
        task.setAssigneeUuid(firstMember.getStringUUID());
        task.setAssigneeName(firstMember.getName().getString());
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, secondMember, server);

        int result = dispatcher.execute("todo task donep team-done-other " + task.getId(), source);
        assertEquals(0, result, "普通成员完成别人任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "普通成员 donep 别人任务的错误键不正确");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(Boolean.FALSE, savedTask.isCompleted(), "失败的 donep 不应把任务标记为已完成");
        assertEquals(firstMember.getStringUUID(), savedTask.getAssigneeUuid(), "失败的 donep 不应改动 assigneeUuid");
        assertEquals(firstMember.getName().getString(), savedTask.getAssigneeName(), "失败的 donep 不应改动 assigneeName");
    }

    private static void shouldAllowLeadCompletingAssignedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000259", "manager-done-lead", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000260", "lead-done-lead", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000261", "member-done-lead", false);
        TestMinecraftServer server = createServer(manager, lead, member);
        Project project = addTeamProject(manager, "team-done-lead", "Done Lead");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Lead Completable Team Task");
        task.setAssigneeUuid(member.getStringUUID());
        task.setAssigneeName(member.getName().getString());
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task donep team-done-lead " + task.getId(), createSource(0, lead, server));
        assertEquals(1, result, "lead donep 应返回成功");
        assertEquals(Boolean.TRUE, loadTeamTaskById(task.getId()).isCompleted(), "lead donep 未将任务标记为已完成");
    }

    private static void shouldReturnAlreadyCompletedForCompletedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000218", "manager-done-completed", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000219", "member-done-completed", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-done-completed", "Done Completed");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Already Completed Team Task");
        task.setAssigneeUuid(member.getStringUUID());
        task.setAssigneeName(member.getName().getString());
        task.setCompleted(true);
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo task donep team-done-completed " + task.getId(), source);
        assertEquals(1, result, "已完成任务再次 donep 应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.task.done.already_completed", "已完成任务再次 donep 未返回已完成提示");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(Boolean.TRUE, savedTask.isCompleted(), "再次 donep 后任务仍应保持已完成");
        assertEquals(member.getStringUUID(), savedTask.getAssigneeUuid(), "再次 donep 后 assigneeUuid 不应变化");
        assertEquals(member.getName().getString(), savedTask.getAssigneeName(), "再次 donep 后 assigneeName 不应变化");
    }

    /**
     * 验证团队任务一旦完成，后续 abandonp 会被拒绝，且不会清空 assignee。
     */
    private static void shouldRejectAbandoningCompletedAssignedTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000202", "manager-abandon-completed", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000203", "member-abandon-completed", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-abandon-completed", "Abandon Completed");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Completed Assigned Task");
        task.setAssigneeUuid(member.getStringUUID());
        task.setAssigneeName(member.getName().getString());
        task.setCompleted(true);
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo task abandonp team-abandon-completed " + task.getId(), source);
        assertEquals(0, result, "已完成任务执行 abandonp 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "已完成任务的 abandonp 错误键不正确");
        Task savedTask = loadTeamTaskById(task.getId());
        assertEquals(member.getStringUUID(), savedTask.getAssigneeUuid(), "失败的 abandonp 不应清空 assigneeUuid");
        assertEquals(member.getName().getString(), savedTask.getAssigneeName(), "失败的 abandonp 不应清空 assigneeName");
    }

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
     * 校验普通成员不能删除团队任务。
     */
    private static void shouldRejectRemovingTeamTaskForMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000232", "manager-remove-member-denied", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000233", "member-remove-member-denied", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-remove-member-denied", "Remove Member Denied");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Member Remove Denied Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, member, server);

        int result = dispatcher.execute("todo task removep team-remove-member-denied " + task.getId(), source);
        assertEquals(0, result, "普通成员删除团队任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.project.permission_denied", "普通成员 removep 的错误键不正确");
        assertNotNull(loadTeamTaskById(task.getId()), "失败的 removep 不应删除团队任务");
    }

    /**
     * 校验 lead 角色可以删除团队任务。
     */
    private static void shouldAllowLeadRemovingTeamTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000234", "manager-remove-lead-allowed", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000235", "lead-remove-allowed", false);
        TestMinecraftServer server = createServer(manager, lead);
        Project project = addTeamProject(manager, "team-remove-lead-allowed", "Remove Lead Allowed");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        Task task = createTeamTask(project.getId(), "Lead Removable Team Task");
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task removep team-remove-lead-allowed " + task.getId(), createSource(0, lead, server));
        assertEquals(1, result, "lead 删除团队任务应返回成功");
        assertNull(findTeamTaskByTitleOrNull("Lead Removable Team Task"), "lead removep 后不应再保留已删除的团队任务");
    }

    /**
     * 校验项目经理可以成功删除团队任务。
     */
    /**
     * 校验项目经理仍可删除已完成且已指派的团队任务。
     */
    /**
     * 校验 lead 可以删除指派给项目经理的团队任务。
     */
    private static void shouldAllowLeadRemovingTaskAssignedToProjectManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000303", "manager-remove-owner-task", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000304", "lead-remove-owner-task", false);
        TestMinecraftServer server = createServer(manager, lead);
        Project project = addTeamProject(manager, "team-remove-owner-task", "Remove Owner Task");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        Task task = createTeamTask(project.getId(), "Manager Assigned Removable Task");
        task.setAssigneeUuid(manager.getStringUUID());
        task.setAssigneeName(manager.getName().getString());
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task removep team-remove-owner-task " + task.getId(), createSource(0, lead, server));
        assertEquals(1, result, "lead 删除指派给项目经理的任务应返回成功");
        assertNull(findTeamTaskByTitleOrNull("Manager Assigned Removable Task"), "lead removep 后不应再保留指派给项目经理的任务");
    }

    private static void shouldRemoveCompletedAssignedTeamTaskByManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000226", "manager-remove-completed-assigned", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000227", "member-remove-completed-assigned", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-remove-completed-assigned", "Remove Completed Assigned");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());
        Task task = createTeamTask(project.getId(), "Completed Assigned Removable Task");
        task.setAssigneeUuid(member.getStringUUID());
        task.setAssigneeName(member.getName().getString());
        task.setCompleted(true);
        saveTeamTasks(task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task removep team-remove-completed-assigned " + task.getId(), createSource(0, manager, server));
        assertEquals(1, result, "椤圭洰缁忕悊鍒犻櫎宸插畬鎴愪笖宸叉寚娲句换鍔℃椂搴旇繑鍥炴垚鍔?");
        assertNull(findTeamTaskByTitleOrNull("Completed Assigned Removable Task"), "removep 鍚庝笉搴斿啀淇濈暀宸插垹闄ょ殑鍥㈤槦浠诲姟");
    }

    /**
     * 校验项目经理可以删除已完成/未完成、已指派/未指派四种组合下的团队任务。
     */
    private static void shouldRemoveTeamTasksAcrossAssignedAndCompletedMatrixSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000305", "manager-remove-matrix", false);
        TestServerPlayer member = createPlayer("00000000-0000-0000-0000-000000000306", "member-remove-matrix", false);
        TestMinecraftServer server = createServer(manager, member);
        Project project = addTeamProject(manager, "team-remove-matrix", "Remove Matrix");
        project.addMember(member.getStringUUID(), Project.ProjectRole.MEMBER, member.getName().getString());

        Task openUnassignedTask = createTeamTask(project.getId(), "Matrix Open Unassigned Task");
        Task openAssignedTask = createTeamTask(project.getId(), "Matrix Open Assigned Task");
        openAssignedTask.setAssigneeUuid(member.getStringUUID());
        openAssignedTask.setAssigneeName(member.getName().getString());

        Task completedUnassignedTask = createTeamTask(project.getId(), "Matrix Completed Unassigned Task");
        completedUnassignedTask.setCompleted(true);

        Task completedAssignedTask = createTeamTask(project.getId(), "Matrix Completed Assigned Task");
        completedAssignedTask.setCompleted(true);
        completedAssignedTask.setAssigneeUuid(member.getStringUUID());
        completedAssignedTask.setAssigneeName(member.getName().getString());

        saveTeamTasks(openUnassignedTask, openAssignedTask, completedUnassignedTask, completedAssignedTask);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo task removep team-remove-matrix " + openUnassignedTask.getId(), createSource(0, manager, server)), "删除未完成未指派任务应返回成功");
        assertEquals(1, dispatcher.execute("todo task removep team-remove-matrix " + openAssignedTask.getId(), createSource(0, manager, server)), "删除未完成已指派任务应返回成功");
        assertEquals(1, dispatcher.execute("todo task removep team-remove-matrix " + completedUnassignedTask.getId(), createSource(0, manager, server)), "删除已完成未指派任务应返回成功");
        assertEquals(1, dispatcher.execute("todo task removep team-remove-matrix " + completedAssignedTask.getId(), createSource(0, manager, server)), "删除已完成已指派任务应返回成功");
        assertEquals(0, TodoListCommon.getTaskStorage().loadTeamTasks().size(), "removep 完整矩阵执行后团队任务列表应为空");
    }

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
     * 校验启动后手动修改 commandAccessMode 配置，不会被其他配置保存写回默认值。
     */
    private static void shouldPreserveCommandAccessModeAfterExternalConfigEdit() throws Exception {
        writeConfigCommandAccessMode(ModConfig.CommandAccessMode.OP_ONLY);
        ModConfig.load();
        assertEquals(ModConfig.CommandAccessMode.OP_ONLY, ModConfig.getInstance().getCommandAccessMode(), "初始加载的 commandAccessMode 应为 OP_ONLY");

        writeConfigCommandAccessMode(ModConfig.CommandAccessMode.VIEW_ONLY);
        ModConfig.getInstance().setHudShowWhenEmpty(true);

        assertEquals(ModConfig.CommandAccessMode.VIEW_ONLY, ModConfig.getInstance().getCommandAccessMode(), "其他配置保存不应覆盖手动修改的 commandAccessMode");
        assertContainsText(readConfigFileLines(), "\"commandAccessMode\": \"VIEW_ONLY\"", "配置文件中的 commandAccessMode 应保持 VIEW_ONLY");
    }

    /**
     * 将测试配置文件中的 commandAccessMode 更新为指定值，用于模拟玩家在游戏外手动编辑配置。
     *
     * @param accessMode 要写入的命令权限模式
     * @throws Exception 读写配置失败时抛出异常
     */
    private static void writeConfigCommandAccessMode(ModConfig.CommandAccessMode accessMode) throws Exception {
        String config = Files.readString(getConfigFilePath(), StandardCharsets.UTF_8);
        String updated = config.replaceAll("\"commandAccessMode\"\\s*:\\s*\"[A-Z_]+\"", "\"commandAccessMode\": \"" + accessMode.name() + "\"");
        Files.writeString(getConfigFilePath(), updated, StandardCharsets.UTF_8);
    }

    /**
     * 读取测试环境的配置文件内容，便于断言写盘结果。
     *
     * @return 配置文件内容，以单元素列表返回，便于复用现有断言工具
     * @throws Exception 读取失败时抛出异常
     */
    private static List<Component> readConfigFileLines() throws Exception {
        return List.of(Component.literal(Files.readString(getConfigFilePath(), StandardCharsets.UTF_8)));
    }

    /**
     * 获取命令测试使用的配置文件路径。
     *
     * @return 配置文件路径
     */
    private static Path getConfigFilePath() {
        return TEST_GAME_DIR.resolve("config").resolve("todolist.json");
    }

    /**
     * 创建命令测试专用的临时游戏目录。
     *
     * @return 临时目录路径
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
     * 断言消息列表中不包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 不应出现的翻译键
     * @param message 失败提示
     */
    private static void assertNotContainsMessageKey(List<Component> messages, String translationKey, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(translationKey)) {
                throw new AssertionError(message + " actual=" + messages);
            }
        }
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
     * 统计消息列表中包含指定文本片段的消息数量。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本片段
     * @return 命中的消息数量
     */
    private static int countMessagesContaining(List<Component> messages, String expectedText) {
        int count = 0;
        for (Component component : messages) {
            if (component != null && component.getString().contains(expectedText)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 断言两个对象相等。
     *
     * @param expected 期望值
     * @param actual 实际值
     * @param message 失败提示
     */
    /**
     * 断言消息列表中包含两个文本片段，且前者出现在后者之前。
     *
     * @param messages 消息列表
     * @param earlierText 应先出现的文本
     * @param laterText 应后出现的文本
     * @param message 失败提示
     */
    private static void assertTextAppearsBefore(List<Component> messages, String earlierText, String laterText, String message) {
        int earlierIndex = findMessageIndexContaining(messages, earlierText);
        int laterIndex = findMessageIndexContaining(messages, laterText);
        if (earlierIndex < 0 || laterIndex < 0 || earlierIndex >= laterIndex) {
            throw new AssertionError(message + " actual=" + messages);
        }
    }

    /**
     * 查找首个包含指定文本片段的消息索引。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本片段
     * @return 命中的消息索引；未命中时返回 -1
     */
    private static int findMessageIndexContaining(List<Component> messages, String expectedText) {
        for (int index = 0; index < messages.size(); index++) {
            Component component = messages.get(index);
            if (component != null && component.getString().contains(expectedText)) {
                return index;
            }
        }
        return -1;
    }

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
        private MinecraftServer testServer;

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
         * 返回测试玩家绑定的服务端对象。
         *
         * @return 测试服务端对象
         */
        @Override
        public MinecraftServer getServer() {
            return testServer;
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
        private boolean testDedicatedServer = true;
        private boolean testPublished;

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
         * @return 当前测试服务端的 dedicated 标记
         */
        @Override
        public boolean isDedicatedServer() {
            return testDedicatedServer;
        }

        /**
         * 标记测试服务端未发布局域网。
         *
         * @return 当前测试服务端的 published 标记
         */
        @Override
        public boolean isPublished() {
            return testPublished;
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
