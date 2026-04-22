package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.todolist.TodoListCommon;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestMinecraftServer;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestServerPlayer;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.task.Task;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令系统项目相关集成测试入口，负责拆分执行 HUD、星标、确认流与权限模式等项目链路用例。
 */
public final class CommandBootstrapProjectIntegrationTestMain {

    /**
     * 项目相关集成测试方法名单，按既有顺序串行执行，保证回归语义不变。
     */
    private static final List<String> PROJECT_CASE_METHOD_NAMES = List.of(
            "shouldSetHudVisibilityByCommand",
            "shouldSyncHudVisibilityFromClientPacketSuccessfully",
            "shouldRejectInvalidHudValue",
            "shouldStarAndUnstarVisibleProject",
            "shouldReturnAlreadyStarredForStarredProjectSuccessfully",
            "shouldReturnNotStarredForUnstarredProjectSuccessfully",
            "shouldRejectMissingProjectWhenStarring",
            "shouldRequestProjectRemoveConfirmSuccessfully",
            "shouldExpireTaskCleanConfirmWithoutPendingRequest",
            "shouldRejectProjectRemoveConfirmWithoutPendingRequest",
            "shouldRejectInvalidProjectMemberRoleValue",
            "shouldRejectEditCommandForViewOnlyPlayer",
            "shouldHideTaskAddCommandForViewOnlyPlayer",
            "shouldRejectProjectRenameForViewOnlyPlayer",
            "shouldAllowViewCommandForViewOnlyPlayer",
            "shouldRejectEditCommandForNonOpPlayerWhenOpOnly",
            "shouldAllowEditCommandForOperatorWhenOpOnly",
            "shouldShowCurrentCommandAccessModeSuccessfully",
            "shouldSetCommandAccessModeByAdminCommandSuccessfully",
            "shouldRejectInvalidCommandAccessModeValue",
            "shouldCreateTeamProjectSuccessfully",
            "shouldListTeamProjectForOtherPlayerSuccessfully",
            "shouldRestrictTeamCommandsInSingleplayerSuccessfully",
            "shouldRejectInvalidProjectListModeSuccessfully",
            "shouldRejectProjectCreateForInvalidScopeSuccessfully",
            "shouldRejectProjectRenameForMissingProjectSuccessfully",
            "shouldAllowTeamCommandsWhenLanPublishedSuccessfully",
            "shouldAllowTeamCommandsWhenLanPublishedForOtherPlayerSuccessfully",
            "shouldHideCurrentTeamProjectWhenSwitchingToSingleplayerSuccessfully",
            "shouldRejectSelectingTeamProjectInSingleplayerSuccessfully",
            "shouldSelectAndListProjectsSuccessfully",
            "shouldRejectMissingProjectWhenSelectingSuccessfully",
            "shouldShowEmptyCurrentProjectListSuccessfully",
            "shouldShowEmptyStarProjectListSuccessfully",
            "shouldRenamePersonalProjectSuccessfully",
            "shouldEnableTeamProjectMemberCreateSuccessfully",
            "shouldDisableTeamProjectMemberCreateSuccessfully",
            "shouldRejectProjectMemberCreateForPersonalProjectSuccessfully",
            "shouldRejectProjectMemberCreateForMissingProjectSuccessfully",
            "shouldRejectProjectMemberCreateForInvalidValueSuccessfully",
            "shouldRejectProjectMemberCreateForRegularMemberSuccessfully",
            "shouldAddProjectMemberSuccessfully",
            "shouldRejectProjectMemberAddWithInvalidTargetSuccessfully",
            "shouldRejectProjectMemberAddWhenTargetAlreadyExistsSuccessfully",
            "shouldRejectProjectMemberAddForRegularMemberSuccessfully",
            "shouldPromoteProjectMemberRoleSuccessfully",
            "shouldRejectProjectMemberRoleWhenTargetMissingSuccessfully",
            "shouldRejectLeadChangingProjectManagerRoleSuccessfully",
            "shouldRejectLeadChangingOwnRoleSuccessfully",
            "shouldRejectRegularMemberChangingOwnRoleSuccessfully",
            "shouldRejectProjectMemberRoleForRegularMemberSuccessfully",
            "shouldRemoveProjectMemberSuccessfully",
            "shouldRejectProjectMemberRemoveWhenTargetMissingSuccessfully",
            "shouldRejectLeadRemovingProjectManagerSuccessfully",
            "shouldRejectManagerRemovingSelfSuccessfully",
            "shouldRejectLeadRemovingSelfSuccessfully",
            "shouldRejectProjectMemberRemoveForRegularMemberSuccessfully",
            "shouldRemovePersonalProjectAfterConfirmSuccessfully",
            "shouldClearCurrentProjectStateAfterProjectRemovalSuccessfully",
            "shouldClearStarredProjectStateAfterProjectRemovalSuccessfully",
            "shouldClearTeamProjectStateForOnlineMembersAfterRemovalSuccessfully",
            "shouldKeepCurrentAndStarredProjectStateAfterProjectRenameSuccessfully",
            "shouldSanitizeDirtyProjectPlayerStateOnJoinSuccessfully",
            "shouldSanitizeOfflineMemberProjectStateAfterTeamRemovalSuccessfully",
            "shouldRestoreTeamProjectStateDifferentlyAcrossServerModesSuccessfully",
            "shouldClearCurrentAndStarredStateWhenSameProjectRemovedSuccessfully",
            "shouldKeepCurrentAndStarredStateWhenSameProjectRenamedSuccessfully",
            "shouldKeepProjectStateButRestrictTasksAfterMemberRemovalSuccessfully",
            "shouldKeepProjectStateAfterMemberRoleDemotionSuccessfully",
            "shouldSanitizeMixedPersonalAndTeamProjectStateAcrossServerModesSuccessfully",
            "shouldKeepProjectListOrderingStableAcrossListModesSuccessfully",
            "shouldIsolatePendingProjectRemoveConfirmationsBetweenPlayersSuccessfully",
            "shouldListProjectsAfterReloadSuccessfully",
            "shouldListCurrentAndStarredProjectsAfterReloadSuccessfully",
            "shouldSanitizeCurrentAndStarredTeamProjectsAfterReloadIntoSingleplayerSuccessfully",
            "shouldRestoreTeamProjectStateWhenPublishingLanWithoutReconnectSuccessfully",
            "shouldKeepHiddenTeamStarredStateAfterSingleplayerProjectSelectionSuccessfully",
            "shouldSeedMissingProjectStateFromRequestSyncSuccessfully",
            "shouldPersistTeamProjectMemberCreateSettingAfterReloadSuccessfully",
            "shouldPersistProjectMemberRolesAndPermissionsAfterReloadSuccessfully"
    );

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandBootstrapProjectIntegrationTestMain() {
    }

    /**
     * 程序入口，初始化命令测试环境后，顺序执行拆分出的项目相关用例。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当任一用例失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        invokeBootstrapEnvironment();
        for (String methodName : PROJECT_CASE_METHOD_NAMES) {
            runProjectCase(methodName);
        }
    }

    /**
     * 复用原有命令集成测试环境初始化逻辑。
     */
    private static void invokeBootstrapEnvironment() {
        CommandBootstrapIntegrationTestMain.bootstrapEnvironment();
    }

    /**
     * 执行单个项目集成测试用例，并沿用现有日志格式输出结果。
     *
     * @param methodName 待执行的用例方法名
     * @throws Exception 用例执行失败时向上抛出异常
     */
    private static void runProjectCase(String methodName) throws Exception {
        CommandTestSupport.runTestCase(
                CommandBootstrapProjectIntegrationTestMain.class.getSimpleName() + "." + methodName,
                () -> invokeProjectCaseMethod(methodName)
        );
    }

    /**
     * 优先调用拆分后的本地项目用例；若尚未迁移，则回退到旧实现。
     *
     * @param methodName 待执行的用例方法名
     * @throws Exception 目标方法执行失败时向上抛出异常
     */
    private static void invokeProjectCaseMethod(String methodName) throws Exception {
        Method localMethod = findLocalProjectCaseMethod(methodName);
        if (localMethod != null) {
            invokeReflectedMethod(localMethod, methodName);
            return;
        }
        Method legacyMethod = CommandBootstrapIntegrationTestMain.class.getDeclaredMethod(methodName);
        invokeReflectedMethod(legacyMethod, methodName);
    }

    /**
     * 在当前拆分文件中查找已迁移的项目用例方法。
     *
     * @param methodName 待查找的方法名
     * @return 找到时返回方法对象，否则返回空
     */
    private static Method findLocalProjectCaseMethod(String methodName) {
        try {
            Method method = CommandBootstrapProjectIntegrationTestMain.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /**
     * 统一反射执行静态测试方法，并解包真实异常。
     *
     * @param method 待执行的方法
     * @param methodName 待执行的方法名
     * @throws Exception 目标方法抛出受检异常时向上转抛
     */
    private static void invokeReflectedMethod(Method method, String methodName) throws Exception {
        method.setAccessible(true);
        try {
            method.invoke(null);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable cause = invocationTargetException.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof AssertionError assertionError) {
                throw assertionError;
            }
            throw new IllegalStateException("执行项目集成测试方法失败: " + methodName, cause);
        } catch (IllegalAccessException illegalAccessException) {
            throw new IllegalStateException("无法访问项目集成测试方法: " + methodName, illegalAccessException);
        }
    }

    /**
     * 重置命令测试全局状态。
     *
     * @param accessMode 命令访问模式
     */
    private static void resetState(ModConfig.CommandAccessMode accessMode) {
        CommandBootstrapIntegrationTestMain.resetState(accessMode);
    }

    /**
     * 创建命令分发器。
     *
     * @return 已注册命令树的分发器
     */
    private static CommandDispatcher<CommandSourceStack> createDispatcher() {
        return CommandBootstrapIntegrationTestMain.createDispatcher();
    }

    /**
     * 创建测试玩家。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @param operator 是否为操作员
     * @return 构造好的测试玩家
     */
    private static TestServerPlayer createPlayer(String uuid, String name, boolean operator) {
        return CommandBootstrapIntegrationTestMain.createPlayer(uuid, name, operator);
    }

    /**
     * 创建测试服务器。
     *
     * @param onlinePlayers 在线玩家
     * @return 测试服务器实例
     */
    private static TestMinecraftServer createServer(TestServerPlayer... onlinePlayers) {
        return CommandBootstrapIntegrationTestMain.createServer(onlinePlayers);
    }

    /**
     * 创建单机服务端桩。
     *
     * @param onlinePlayers 在线玩家
     * @return 单机服务端实例
     */
    private static TestMinecraftServer createSingleplayerServer(TestServerPlayer... onlinePlayers) {
        return CommandBootstrapIntegrationTestMain.createSingleplayerServer(onlinePlayers);
    }

    /**
     * 创建可指定专用服与局域网状态的测试服务端。
     *
     * @param dedicated 是否为专用服
     * @param published 是否已开放局域网
     * @param onlinePlayers 在线玩家
     * @return 测试服务端实例
     */
    private static TestMinecraftServer createServer(boolean dedicated, boolean published, TestServerPlayer... onlinePlayers) {
        return CommandBootstrapIntegrationTestMain.createServer(dedicated, published, onlinePlayers);
    }

    /**
     * 创建捕获型命令源。
     *
     * @param permissionLevel 权限等级
     * @param player 命令执行玩家
     * @return 可捕获消息的命令源
     */
    private static CapturingCommandSourceStack createSource(int permissionLevel, ServerPlayer player) {
        return CommandBootstrapIntegrationTestMain.createSource(permissionLevel, player);
    }

    /**
     * 创建绑定服务器上下文的捕获型命令源。
     *
     * @param permissionLevel 权限等级
     * @param player 命令执行玩家
     * @param server 所属服务器
     * @return 可捕获消息的命令源
     */
    private static CapturingCommandSourceStack createSource(int permissionLevel, ServerPlayer player, MinecraftServer server) {
        return CommandBootstrapIntegrationTestMain.createSource(permissionLevel, player, server);
    }

    /**
     * 创建个人项目。
     *
     * @param owner 项目 owner
     * @param projectId 项目 ID
     * @param projectName 项目名称
     */
    private static void addOwnedPersonalProject(TestServerPlayer owner, String projectId, String projectName) {
        CommandBootstrapIntegrationTestMain.addOwnedPersonalProject(owner, projectId, projectName);
    }

    /**
     * 创建用于成员角色测试的团队项目。
     *
     * @param owner 项目 owner
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @param memberUuid 成员 UUID
     */
    private static void addTeamProjectForMemberRole(TestServerPlayer owner, String projectId, String projectName, String memberUuid) {
        CommandBootstrapIntegrationTestMain.addTeamProjectForMemberRole(owner, projectId, projectName, memberUuid);
    }

    /**
     * 创建团队项目。
     *
     * @param owner 项目经理
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @return 创建好的团队项目
     */
    private static Project addTeamProject(TestServerPlayer owner, String projectId, String projectName) {
        return CommandBootstrapIntegrationTestMain.addTeamProject(owner, projectId, projectName);
    }

    /**
     * 保存个人任务。
     *
     * @param player 任务所属玩家
     * @param tasks 需要保存的任务
     * @throws Exception 保存失败时抛出异常
     */
    private static void savePersonalTasks(TestServerPlayer player, Task... tasks) throws Exception {
        CommandBootstrapIntegrationTestMain.savePersonalTasks(player, tasks);
    }

    /**
     * 创建个人任务。
     *
     * @param title 任务标题
     * @param completed 是否已完成
     * @param priority 优先级
     * @param projectId 所属项目 ID
     * @return 初始化后的个人任务
     */
    private static Task createPersonalTask(String title, boolean completed, Task.Priority priority, String projectId) {
        return CommandBootstrapIntegrationTestMain.createPersonalTask(title, completed, priority, projectId);
    }

    /**
     * 创建团队任务。
     *
     * @param projectId 项目 ID
     * @param title 任务标题
     * @return 初始化后的团队任务
     */
    private static Task createTeamTask(String projectId, String title) {
        return CommandBootstrapIntegrationTestMain.createTeamTask(projectId, title);
    }

    /**
     * 保存团队任务。
     *
     * @param tasks 待保存任务
     * @throws Exception 保存失败时抛出异常
     */
    private static void saveTeamTasks(Task... tasks) throws Exception {
        CommandBootstrapIntegrationTestMain.saveTeamTasks(tasks);
    }

    /**
     * 立即刷新项目相关防抖保存。
     *
     * @param server 当前测试服务端
     */
    private static void flushProjectSaves(MinecraftServer server) {
        CommandBootstrapIntegrationTestMain.flushProjectSaves(server);
    }

    /**
     * 重载项目持久化状态。
     */
    private static void reloadPersistentState() {
        CommandBootstrapIntegrationTestMain.reloadPersistentState();
    }

    /**
     * 按项目名称查找项目。
     *
     * @param projectName 项目名称
     * @return 命中的项目；不存在时返回 null
     */
    private static Project findProjectByName(String projectName) {
        return CommandBootstrapIntegrationTestMain.findProjectByName(projectName);
    }

    /**
     * 读取个人项目持久化文件路径。
     *
     * @return 个人项目文件路径
     */
    private static Path getPersonalProjectsFilePath() {
        return CommandBootstrapIntegrationTestMain.getPersonalProjectsFilePath();
    }

    /**
     * 读取玩家项目状态文件路径。
     *
     * @param player 目标玩家
     * @return 玩家项目状态文件路径
     */
    private static Path getProjectPlayerStateFilePath(TestServerPlayer player) {
        return CommandBootstrapIntegrationTestMain.getProjectPlayerStateFilePath(player);
    }

    /**
     * 读取团队项目持久化文件路径。
     *
     * @return 团队项目文件路径
     */
    private static Path getTeamProjectsFilePath() {
        return CommandBootstrapIntegrationTestMain.getTeamProjectsFilePath();
    }

    /**
     * 按任务 ID 读取团队任务。
     *
     * @param taskId 任务 ID
     * @return 命中的团队任务
     * @throws Exception 读取失败时抛出异常
     */
    private static Task loadTeamTaskById(String taskId) throws Exception {
        return CommandBootstrapIntegrationTestMain.loadTeamTaskById(taskId);
    }

    /**
     * 读取配置文件内容，便于断言写盘结果。
     *
     * @return 配置文件行列表
     * @throws Exception 读取失败时抛出异常
     */
    private static List<Component> readConfigFileLines() throws Exception {
        return CommandBootstrapIntegrationTestMain.readConfigFileLines();
    }

    /**
     * 按标题查找玩家个人任务，找不到时返回 null。
     *
     * @param player 目标玩家
     * @param title 任务标题
     * @return 命中的任务；若不存在则返回 null
     * @throws Exception 读取失败时抛出异常
     */
    private static Task findPersonalTaskByTitleOrNull(TestServerPlayer player, String title) throws Exception {
        return CommandBootstrapIntegrationTestMain.findPersonalTaskByTitleOrNull(player, title);
    }

    /**
     * 按标题查找玩家个人任务，要求必须存在。
     *
     * @param player 目标玩家
     * @param title 任务标题
     * @return 命中的任务
     * @throws Exception 读取失败时抛出异常
     */
    private static Task findPersonalTaskByTitle(TestServerPlayer player, String title) throws Exception {
        return CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, title);
    }

    /**
     * 断言消息列表包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 目标翻译键
     * @param message 失败提示
     */
    private static void assertContainsMessageKey(List<Component> messages, String translationKey, String message) {
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(messages, translationKey, message);
    }

    /**
     * 断言消息列表包含指定文本。
     *
     * @param messages 消息列表
     * @param expectedText 期望文本
     * @param message 失败提示
     */
    private static void assertContainsText(List<Component> messages, String expectedText, String message) {
        CommandBootstrapIntegrationTestMain.assertContainsText(messages, expectedText, message);
    }

    /**
     * 断言消息列表不包含指定文本。
     *
     * @param messages 消息列表
     * @param unexpectedText 不应出现的文本
     * @param message 失败提示
     */
    private static void assertNotContainsText(List<Component> messages, String unexpectedText, String message) {
        CommandBootstrapIntegrationTestMain.assertNotContainsText(messages, unexpectedText, message);
    }

    /**
     * 断言两个值相等。
     *
     * @param expected 期望值
     * @param actual 实际值
     * @param message 失败提示
     */
    private static void assertEquals(Object expected, Object actual, String message) {
        CommandBootstrapIntegrationTestMain.assertEquals(expected, actual, message);
    }

    /**
     * 断言两个列表相等。
     *
     * @param expected 期望列表
     * @param actual 实际列表
     * @param message 失败提示
     */
    private static void assertListEquals(List<String> expected, List<String> actual, String message) {
        CommandBootstrapIntegrationTestMain.assertListEquals(expected, actual, message);
    }

    /**
     * 断言消息列表里某段文本先于另一段文本出现。
     *
     * @param messages 待检查的消息列表
     * @param earlierText 预期更早出现的文本
     * @param laterText 预期更晚出现的文本
     * @param message 断言失败提示
     */
    private static void assertTextAppearsBefore(List<Component> messages, String earlierText, String laterText, String message) {
        int earlierIndex = -1;
        int laterIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            String plainText = messages.get(i).getString();
            if (earlierIndex < 0 && plainText.contains(earlierText)) {
                earlierIndex = i;
            }
            if (laterIndex < 0 && plainText.contains(laterText)) {
                laterIndex = i;
            }
        }
        assertEquals(Boolean.TRUE, earlierIndex >= 0, message + "，未找到前置文本: " + earlierText);
        assertEquals(Boolean.TRUE, laterIndex >= 0, message + "，未找到后置文本: " + laterText);
        assertEquals(Boolean.TRUE, earlierIndex < laterIndex, message);
    }

    /**
     * 断言对象非空。
     *
     * @param value 实际值
     * @param message 失败提示
     */
    private static void assertNotNull(Object value, String message) {
        CommandBootstrapIntegrationTestMain.assertNotNull(value, message);
    }

    /**
     * 断言对象为空。
     *
     * @param value 实际值
     * @param message 失败提示
     */
    private static void assertNull(Object value, String message) {
        CommandBootstrapIntegrationTestMain.assertNull(value, message);
    }

    /**
     * 校验通过命令可以切换 HUD 显隐状态。
     */
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

    /**
     * 校验非法 HUD 值会被拒绝。
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

    /**
     * 校验星标不存在项目时会返回 not_found。
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
     * 校验 VIEW_ONLY 模式下的 task add 会表现为不可见子命令。
     */
    private static void shouldHideTaskAddCommandForViewOnlyPlayer() throws Exception {
        resetState(ModConfig.CommandAccessMode.VIEW_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000018", "view-hidden-add-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        try {
            dispatcher.execute("todo task add \"Denied task\" \"x\" test", createSource(0, player));
            throw new AssertionError("VIEW_ONLY 下的 task add 不应解析成功");
        } catch (CommandSyntaxException exception) {
            assertContainsText(
                    List.of(Component.literal(exception.getMessage())),
                    "Incorrect argument for command",
                    "VIEW_ONLY 下的 task add 应表现为 Brigadier 子命令不可见"
            );
        }
        assertNull(findPersonalTaskByTitleOrNull(player, "Denied task"), "VIEW_ONLY 下的 task add 不应写入任务");
    }

    /**
     * 校验 VIEW_ONLY 模式下的 project rename 会被权限拒绝。
     */
    private static void shouldRejectProjectRenameForViewOnlyPlayer() throws Exception {
        resetState(ModConfig.CommandAccessMode.VIEW_ONLY);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000120", "view-rename-user", false);
        addOwnedPersonalProject(player, "view-only-rename-project", "Before View Rename");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project rename view-only-rename-project After View Rename", source);
        assertEquals(0, result, "VIEW_ONLY 下的 project rename 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.permission_denied", "VIEW_ONLY 下的 project rename 权限拒绝错误键不正确");
        assertEquals("Before View Rename", TodoListCommon.getProjectManager().getProject("view-only-rename-project").getName(), "VIEW_ONLY 下的 project rename 不应修改项目名称");
    }

    /**
     * 校验 VIEW_ONLY 模式下的非 OP 玩家仍可执行查看类命令。
     */
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
    /**
     * 验证 project list 在传入非法模式时会返回 invalid_mode。
     */
    private static void shouldRejectInvalidProjectListModeSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000410", "project-list-invalid-mode-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project list invalid-mode", source);
        assertEquals(0, result, "非法 project list 模式应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.list.invalid_mode", "project list 非法模式错误键不正确");
    }

    /**
     * 验证 project create 在传入非法 scope 时会返回 invalid_scope。
     */
    private static void shouldRejectProjectCreateForInvalidScopeSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000411", "project-create-invalid-scope-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project create invalid-scope Invalid Scope Project", source);
        assertEquals(0, result, "非法 project create scope 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.create.invalid_scope", "project create 非法 scope 错误键不正确");
        assertNull(findProjectByName("Invalid Scope Project"), "非法 scope 的 project create 不应创建项目");
    }

    /**
     * 验证 project rename 在项目不存在时会返回 not_found。
     */
    private static void shouldRejectProjectRenameForMissingProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000412", "project-rename-missing-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo project rename missing-rename-project Renamed Missing Project", source);
        assertEquals(0, result, "不存在项目的 project rename 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.project.rename.not_found", "project rename 不存在项目错误键不正确");
    }

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

        int joinResult = dispatcher.execute("todo join project " + createdProject.getId(), createSource(0, manager, lanServer));
        if (joinResult == 1) {
            assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.already_member", "项目经理自己 join project 未提示已是成员");
            return;
        }
        assertEquals(0, joinResult, "项目经理自己 join project 应返回失败");
    }

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

    /**
     * 校验团队项目在专用服可作为 current 展示，但切换到单机未开局域网后会被隐藏。
     */
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

    /**
     * 校验个人项目可以成功改名。
     */
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

    /**
     * 校验普通成员不能修改团队项目成员角色。
     */
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

    /**
     * 校验普通成员不能从团队项目移除其他成员。
     */
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

        assertEquals(null, ProjectPackets.getActiveProjectId(owner), "删除当前项目后应清空 current 状态");
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
     * 验证重载后仍可列出已持久化的个人项目。
     */
    private static void shouldListProjectsAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000142", "reload-project-user", false);
        TestMinecraftServer server = createServer(owner);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int createResult = dispatcher.execute("todo project create personal Reload Personal Project", createSource(0, owner, server));
        assertEquals(1, createResult, "project create personal 应返回成功");
        flushProjectSaves(server);
        assertEquals(Boolean.TRUE, Files.exists(getPersonalProjectsFilePath()), "project create personal 应写入个人项目文件");

        reloadPersistentState();

        assertNotNull(findProjectByName("Reload Personal Project"), "重载后应能找到已持久化的个人项目");
        CapturingCommandSourceStack source = createSource(0, owner, createServer(owner));
        int listResult = createDispatcher().execute("todo project list all", source);
        assertEquals(1, listResult, "project list all 应返回成功");
        assertContainsText(source.getSuccessMessages(), "Reload Personal Project", "project list all 应输出持久化项目");
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
     * 验证团队 current/star 状态在切到单机时只做运行期净化，返回支持团队项目的环境后仍可恢复。
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
     * 验证单机世界发布局域网后，服务端会重新恢复团队项目状态而不需要玩家重连。
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

        singleplayerServer.setTestPublished(true);
        ProjectPackets.onRequestSyncProjectsPacket(
                singleplayerServer,
                singleplayerOwner,
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())
        );

        assertEquals(currentProject.getId(), ProjectPackets.getActiveProjectId(singleplayerOwner), "发布局域网后应立即恢复团队 current 项目");
        assertEquals(List.of(starredProject.getId()), ProjectPackets.getHudStarredProjectIds(singleplayerOwner), "发布局域网后应立即恢复团队 starred 项目");
    }

    /**
     * 验证单机环境里再次写盘时，不会把暂时隐藏的团队星标状态永久覆盖掉。
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

    /**
     * 验证 requestSyncProjects 在服务端尚无玩家状态时，会用客户端种子初始化 active/star/HUD。
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

    /**
     * 验证团队项目 member-create 设置在重载后仍会保持。
     */
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
     * 验证成员角色变化在重载后仍会保持，并继续影响 lead/member 的权限判断。
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
}
