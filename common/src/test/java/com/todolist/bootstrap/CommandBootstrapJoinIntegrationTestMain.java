package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestMinecraftServer;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestServerPlayer;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.TodoListCommon;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令系统加入项目集成测试入口，负责拆分执行 join 相关离线用例，降低主测试入口的维护压力。
 */
public final class CommandBootstrapJoinIntegrationTestMain {

    /**
     * join 集成测试方法名单，按当前约定顺序串行运行，保证回归语义不变。
     */
    private static final List<String> JOIN_CASE_METHOD_NAMES = List.of(
            "shouldRequestJoinProjectSuccessfully",
            "shouldReportNoReviewerOnlineWhenRequestingJoinProjectSuccessfully",
            "shouldApproveJoinRequestAfterNoReviewerWasOnlineInitiallySuccessfully",
            "shouldDenyJoinRequestAfterNoReviewerWasOnlineInitiallySuccessfully",
            "shouldPreferOwnerReviewerOverLeadAndFallbackOperatorSuccessfully",
            "shouldPreferOwnerUuidManagerOverOtherProjectManagersSuccessfully",
            "shouldNotifyAllOnlineProjectManagersWhenOwnerUuidIsInvalidSuccessfully",
            "shouldClearPendingJoinRequestAfterApprovalByAnotherProjectManagerSuccessfully",
            "shouldClearPendingJoinRequestAfterDenialByAnotherProjectManagerSuccessfully",
            "shouldAllowReapplyAfterCrossManagerDenialSuccessfully",
            "shouldRejectDuplicateReapplyWithoutRenotifyingProjectManagersSuccessfully",
            "shouldReapplyWithOnlyRemainingOnlineProjectManagerSuccessfully",
            "shouldApproveReapplyAfterNoProjectManagerWasOnlineSuccessfully",
            "shouldDenyReapplyAfterNoProjectManagerWasOnlineSuccessfully",
            "shouldRejectReapplyApprovalAfterProjectManagerRemovalSuccessfully",
            "shouldRejectReapplyDenialAfterProjectManagerRemovalSuccessfully",
            "shouldRejectReapplyApprovalAfterProjectManagerDemotionSuccessfully",
            "shouldRejectReapplyDenialAfterProjectManagerDemotionSuccessfully",
            "shouldAllowReapplyApprovalByOperatorAfterProjectManagerDemotionSuccessfully",
            "shouldAllowReapplyDenialByOperatorAfterProjectManagerDemotionSuccessfully",
            "shouldPreferRestoredOwnerProjectManagerOnReapplySuccessfully",
            "shouldFallbackToRemainingProjectManagerAfterRestoredOwnerGoesOfflineSuccessfully",
            "shouldReturnToRestoredOwnerReviewerWhenOwnerComesBackOnlineSuccessfully",
            "shouldClearPendingJoinRequestAfterFallbackManagerApprovalWhenRestoredOwnerReturnsSuccessfully",
            "shouldClearPendingJoinRequestAfterFallbackManagerDenialWhenRestoredOwnerReturnsSuccessfully",
            "shouldClearPendingJoinRequestAfterRestoredOwnerApprovalSuccessfully",
            "shouldClearPendingJoinRequestAfterRestoredOwnerDenialSuccessfully",
            "shouldApproveReapplyAfterRestoredOwnerAndManagersGoOfflineSuccessfully",
            "shouldDenyReapplyAfterRestoredOwnerAndManagersGoOfflineSuccessfully",
            "shouldAllowOperatorApprovalAfterOwnerUuidRestoredSuccessfully",
            "shouldAllowOperatorDenialAfterOwnerUuidRestoredSuccessfully",
            "shouldClearPendingJoinRequestAfterOperatorApprovalWithRestoredOwnerSuccessfully",
            "shouldClearPendingJoinRequestAfterOperatorDenialWithRestoredOwnerSuccessfully",
            "shouldClearPendingJoinRequestForOperatorAfterRestoredOwnerApprovalSuccessfully",
            "shouldClearPendingJoinRequestForOperatorAfterRestoredOwnerDenialSuccessfully",
            "shouldAllowReapplyApprovalByRemainingManagerAfterOperatorDenialSuccessfully",
            "shouldAllowReapplyDenialByRemainingManagerAfterOperatorDenialSuccessfully",
            "shouldAllowReapplyApprovalByRestoredOwnerAfterOperatorDenialSuccessfully",
            "shouldAllowReapplyDenialByRestoredOwnerAfterOperatorDenialSuccessfully",
            "shouldClearPendingJoinRequestAfterOperatorApprovalWhenRemainingManagerReturnsSuccessfully",
            "shouldClearPendingJoinRequestAfterOperatorDenialWhenRemainingManagerReturnsSuccessfully",
            "shouldClearPendingJoinRequestAfterOperatorApprovalWhenRestoredOwnerReturnsSuccessfully",
            "shouldClearPendingJoinRequestAfterOperatorDenialWhenRestoredOwnerReturnsSuccessfully",
            "shouldAllowJoinApprovalBySecondaryProjectManagerSuccessfully",
            "shouldAllowJoinDenialBySecondaryProjectManagerSuccessfully",
            "shouldPreferLeadReviewerOverFallbackOperatorSuccessfully",
            "shouldFallbackToLeadWhenOwnerUuidIsInvalidSuccessfully",
            "shouldFallbackToOperatorWhenLeadUuidIsInvalidSuccessfully",
            "shouldSkipInvalidLeadAndNotifyOtherOnlineLeadSuccessfully",
            "shouldNotifyAllOnlineLeadsBeforeFallbackOperatorSuccessfully",
            "shouldAllowJoinApprovalByOnlineOperatorSuccessfully",
            "shouldAllowJoinDenialByOnlineOperatorSuccessfully",
            "shouldRejectApprovingOwnJoinRequestSuccessfully",
            "shouldRejectJoinAcceptForRegularMemberSuccessfully",
            "shouldRejectJoinDenyForRegularMemberSuccessfully",
            "shouldRejectJoinAcceptAfterLeadDemotionSuccessfully",
            "shouldRejectJoinDenyAfterLeadRemovalSuccessfully",
            "shouldIgnoreCorruptedOwnerUuidReviewerAndKeepRealManagerApprovalSuccessfully",
            "shouldAllowJoinRequestWhenCorruptedOwnerUuidMatchesApplicantSuccessfully",
            "shouldFallbackToMemberTableManagerWhenOwnerUuidMissingSuccessfully",
            "shouldRejectJoinAcceptForInvalidApplicantUuidSuccessfully",
            "shouldRejectJoinDenyForInvalidApplicantUuidSuccessfully",
            "shouldRejectJoinDecisionWithoutPendingRequestSuccessfully",
            "shouldRejectDuplicateJoinProjectRequestSuccessfully",
            "shouldRejectJoinProjectForInvalidProjectSuccessfully",
            "shouldRejectJoinProjectInSingleplayerSuccessfully",
            "shouldApproveJoinRequestSuccessfully",
            "shouldApproveJoinRequestAfterApplicantReconnectSuccessfully",
            "shouldDenyJoinRequestAfterApplicantReconnectSuccessfully",
            "shouldRejectJoinDecisionAfterProjectRemovalSuccessfully",
            "shouldRejectJoinDenyAfterProjectRemovalSuccessfully",
            "shouldClearPendingJoinRequestWhenDeletingProjectViaPacketSuccessfully",
            "shouldClearPendingJoinRequestWhenApplicantIsAddedViaPacketSuccessfully",
            "shouldClearPendingJoinRequestWhenApplicantIsAddedManuallySuccessfully",
            "shouldRejectRepeatedJoinAcceptAfterApprovalSuccessfully",
            "shouldRejectRepeatedJoinDenyAfterDenialSuccessfully",
            "shouldKeepJoinDecisionsIsolatedAcrossProjectsSuccessfully",
            "shouldListTeamTasksForJoinedMemberSuccessfully",
            "shouldPaginateTeamTaskListForJoinedMemberSuccessfully",
            "shouldPaginateTeamTaskListWithPrevForJoinedMemberSuccessfully",
            "shouldDenyJoinRequestSuccessfully",
            "shouldAllowJoinRequestAgainAfterDenialSuccessfully",
            "shouldRejectTeamTaskListAfterJoinDeniedSuccessfully",
            "shouldRejectTaskMoreAfterDeniedTeamTaskListWithoutSessionSuccessfully",
            "shouldRejectTaskPrevAfterDeniedTeamTaskListWithoutSessionSuccessfully"
    );

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandBootstrapJoinIntegrationTestMain() {
    }

    /**
     * 程序入口，初始化命令测试环境后，顺序执行拆分出的 join 相关用例。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当任一 join 用例失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        invokeBootstrapEnvironment();
        for (String methodName : JOIN_CASE_METHOD_NAMES) {
            runJoinCase(methodName);
        }
    }

    /**
     * 复用原有命令集成测试环境初始化逻辑，保证拆分后的 join 用例仍运行在同一套基建上。
     */
    private static void invokeBootstrapEnvironment() {
        CommandBootstrapIntegrationTestMain.bootstrapEnvironment();
    }

    /**
     * 执行单个 join 集成测试用例，并沿用现有的测试日志格式输出结果。
     *
     * @param methodName 待执行的用例方法名
     * @throws Exception 用例执行失败时向上抛出异常
     */
    private static void runJoinCase(String methodName) throws Exception {
        CommandTestSupport.runTestCase(
                CommandBootstrapJoinIntegrationTestMain.class.getSimpleName() + "." + methodName,
                () -> invokeJoinCaseMethod(methodName)
        );
    }

    /**
     * 优先调用拆分后的本地 join 用例；若尚未迁移，则回退到原测试类中的旧实现。
     *
     * @param methodName 待执行的用例方法名
     * @throws Exception 目标方法执行失败时向上抛出异常
     */
    private static void invokeJoinCaseMethod(String methodName) throws Exception {
        Method localMethod = findLocalJoinCaseMethod(methodName);
        if (localMethod != null) {
            invokeReflectedMethod(localMethod, methodName);
            return;
        }
        Method legacyMethod = CommandBootstrapIntegrationTestMain.class.getDeclaredMethod(methodName);
        invokeReflectedMethod(legacyMethod, methodName);
    }

    /**
     * 在当前拆分文件中查找已迁移的 join 用例方法。
     *
     * @param methodName 待查找的方法名
     * @return 找到时返回方法对象，否则返回空
     */
    private static Method findLocalJoinCaseMethod(String methodName) {
        try {
            Method method = CommandBootstrapJoinIntegrationTestMain.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /**
     * 统一反射执行静态测试方法，并解包真实异常，避免日志被反射异常噪声污染。
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
            throw new IllegalStateException("执行 join 集成测试方法失败: " + methodName, cause);
        } catch (IllegalAccessException illegalAccessException) {
            throw new IllegalStateException("无法访问 join 集成测试方法: " + methodName, illegalAccessException);
        }
    }

    /**
     * 重置命令测试全局状态，保证每个 join 用例相互隔离。
     *
     * @param accessMode 命令访问模式
     */
    private static void resetState(ModConfig.CommandAccessMode accessMode) {
        CommandBootstrapIntegrationTestMain.resetState(accessMode);
    }

    /**
     * 创建 join 测试使用的命令分发器。
     *
     * @return 已注册命令树的分发器
     */
    private static CommandDispatcher<CommandSourceStack> createDispatcher() {
        return CommandBootstrapIntegrationTestMain.createDispatcher();
    }

    /**
     * 创建捕获型命令源，便于断言命令返回消息。
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
     * 创建团队项目并设置 owner。
     *
     * @param owner 项目 owner
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @return 新建的团队项目
     */
    private static Project addTeamProject(TestServerPlayer owner, String projectId, String projectName) {
        return CommandBootstrapIntegrationTestMain.addTeamProject(owner, projectId, projectName);
    }

    /**
     * 创建在线测试服务器。
     *
     * @param onlinePlayers 当前在线玩家
     * @return 测试服务器实例
     */
    private static TestMinecraftServer createServer(TestServerPlayer... onlinePlayers) {
        return CommandBootstrapIntegrationTestMain.createServer(onlinePlayers);
    }

    /**
     * 创建未开放局域网的单机测试服务器。
     *
     * @param onlinePlayers 当前在线玩家
     * @return 单机测试服务器
     */
    private static TestMinecraftServer createSingleplayerServer(TestServerPlayer... onlinePlayers) {
        return CommandBootstrapIntegrationTestMain.createSingleplayerServer(onlinePlayers);
    }

    /**
     * 创建绑定到指定项目的团队任务。
     *
     * @param projectId 项目 ID
     * @param title 任务标题
     * @return 初始化后的团队任务
     */
    private static Task createTeamTask(String projectId, String title) {
        return CommandBootstrapIntegrationTestMain.createTeamTask(projectId, title);
    }

    /**
     * 将团队任务写入测试存储。
     *
     * @param tasks 需要保存的团队任务
     * @throws Exception 保存失败时抛出异常
     */
    private static void saveTeamTasks(Task... tasks) throws Exception {
        CommandBootstrapIntegrationTestMain.saveTeamTasks(tasks);
    }

    /**
     * 断言消息列表中包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 目标翻译键
     * @param message 断言失败提示
     */
    private static void assertContainsMessageKey(List<Component> messages, String translationKey, String message) {
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(messages, translationKey, message);
    }

    /**
     * 断言消息列表中不包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 目标翻译键
     * @param message 断言失败提示
     */
    private static void assertNotContainsMessageKey(List<Component> messages, String translationKey, String message) {
        CommandBootstrapIntegrationTestMain.assertNotContainsMessageKey(messages, translationKey, message);
    }

    /**
     * 统计消息列表中包含目标文本的条目数量。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本
     * @return 命中数量
     */
    private static int countMessagesContaining(List<Component> messages, String expectedText) {
        return CommandBootstrapIntegrationTestMain.countMessagesContaining(messages, expectedText);
    }

    /**
     * 断言两个值相等。
     *
     * @param expected 期望值
     * @param actual 实际值
     * @param message 断言失败提示
     */
    private static void assertEquals(Object expected, Object actual, String message) {
        CommandBootstrapIntegrationTestMain.assertEquals(expected, actual, message);
    }

    /**
     * 断言对象为空。
     *
     * @param value 实际对象
     * @param message 断言失败提示
     */
    private static void assertNull(Object value, String message) {
        CommandBootstrapIntegrationTestMain.assertNull(value, message);
    }

    /**
     * 断言消息列表中包含指定文本片段。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本
     * @param message 断言失败提示
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
     * 验证普通团队项目 join 请求会通知在线项目经理并回写申请人结果。
     *
     * @throws Exception 命令执行失败时抛出异常
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
     * 验证没有任何在线审批人时，join project 会提示 no_reviewer_online。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldReportNoReviewerOnlineWhenRequestingJoinProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000320", "manager-join-offline-reviewer", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000321", "applicant-join-offline-reviewer", false);
        addTeamProject(offlineManager, "join-no-reviewer-project", "Join No Reviewer Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        TestMinecraftServer server = createServer(applicant);

        int result = dispatcher.execute("todo join project join-no-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, result, "无人在线审批时 join project 仍应返回命令成功");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "无人在线审批时 join project 未提示 no_reviewer_online");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "无人在线审批时 join project 不应提示 sent_named");
    }

    /**
     * 验证首次申请时无人在线，后续审批人上线后仍可完成通过审批。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldApproveJoinRequestAfterNoReviewerWasOnlineInitiallySuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000364", "manager-join-late-approve", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000365", "applicant-join-late-approve", false);
        Project project = addTeamProject(manager, "join-late-approve-project", "Join Late Approve Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        TestMinecraftServer requestServer = createServer(applicant);
        int requestResult = dispatcher.execute("todo join project join-late-approve-project", createSource(0, applicant, requestServer));
        assertEquals(1, requestResult, "首次无人在线审批时 join project 仍应返回命令成功");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "首次无人在线审批时应提示 no_reviewer_online");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "首次无人在线审批时不应提示 sent_named");

        TestMinecraftServer approvalServer = createServer(manager, applicant);
        int approveResult = dispatcher.execute("todo join accept join-late-approve-project " + applicant.getStringUUID(), createSource(0, manager, approvalServer));
        assertEquals(1, approveResult, "审批人后续上线后 join accept 应仍可通过");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "后续审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "后续审批通过后申请人未收到 accepted");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.approved", "后续审批通过后审批人未收到 approved");
    }

    /**
     * 验证首次申请时无人在线，后续审批人上线后仍可完成拒绝审批。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldDenyJoinRequestAfterNoReviewerWasOnlineInitiallySuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000366", "manager-join-late-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000367", "applicant-join-late-deny", false);
        Project project = addTeamProject(manager, "join-late-deny-project", "Join Late Deny Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        TestMinecraftServer requestServer = createServer(applicant);
        int requestResult = dispatcher.execute("todo join project join-late-deny-project", createSource(0, applicant, requestServer));
        assertEquals(1, requestResult, "首次无人在线审批时 join project 仍应返回命令成功");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "首次无人在线审批时应提示 no_reviewer_online");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "首次无人在线审批时不应提示 sent_named");

        TestMinecraftServer approvalServer = createServer(manager, applicant);
        int denyResult = dispatcher.execute("todo join deny join-late-deny-project " + applicant.getStringUUID(), createSource(0, manager, approvalServer));
        assertEquals(1, denyResult, "审批人后续上线后 join deny 应仍可通过");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "后续审批拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "后续审批拒绝后申请人未收到 denied");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.rejected", "后续审批拒绝后审批人未收到 rejected");
    }

    /**
     * 验证 owner 在线时，reviewer 重新收敛到 owner，而不是继续通知 lead 或 OP。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldPreferOwnerReviewerOverLeadAndFallbackOperatorSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000340", "manager-join-owner-reviewer", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000341", "lead-join-owner-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000342", "op-join-owner-reviewer-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000343", "applicant-join-owner-reviewer", false);
        TestMinecraftServer server = createServer(owner, lead, operator, applicant);
        Project project = addTeamProject(owner, "join-owner-reviewer-project", "Join Owner Reviewer Project");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-owner-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, result, "owner 在线时 join project 应返回命令成功");
        assertContainsMessageKey(owner.getClientMessages(), "message.todolist.project.join.request_received", "owner 在线时未收到 join project 审批通知");
        assertNotContainsMessageKey(lead.getClientMessages(), "message.todolist.project.join.request_received", "owner 在线时不应继续通知 lead");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 在线时不应继续通知兜底 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "owner 在线时申请人应收到 sent_named");
    }

    /**
     * 验证 ownerUuid 对应经理在线时，会优先通知该经理而不是其他 PROJECT_MANAGER。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldPreferOwnerUuidManagerOverOtherProjectManagersSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000387", "manager-join-owner-primary", false);
        TestServerPlayer secondaryManager = createPlayer("00000000-0000-0000-0000-000000000388", "manager-join-owner-secondary", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000389", "op-join-owner-secondary-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000390", "applicant-join-owner-secondary", false);
        TestMinecraftServer server = createServer(owner, secondaryManager, operator, applicant);
        Project project = addTeamProject(owner, "join-owner-secondary-manager-project", "Join Owner Secondary Manager Project");
        project.addMember(secondaryManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondaryManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-owner-secondary-manager-project", createSource(0, applicant, server));
        assertEquals(1, result, "owner 与第二个项目经理同时在线时 join project 应返回命令成功");
        assertContainsMessageKey(owner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 对应的真实项目经理应收到 join reviewer 通知");
        assertNotContainsMessageKey(secondaryManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 对应项目经理已在线时不应继续通知第二个 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 对应项目经理已在线时不应回退通知 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "ownerUuid 对应项目经理已在线时申请人应收到 sent_named");
    }

    /**
     * 验证未收到自动 reviewer 通知的第二个 PROJECT_MANAGER 仍可手动执行 join accept。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldAllowJoinApprovalBySecondaryProjectManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000391", "manager-join-owner-approve-primary", false);
        TestServerPlayer secondaryManager = createPlayer("00000000-0000-0000-0000-000000000392", "manager-join-owner-approve-secondary", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000393", "applicant-join-owner-approve-secondary", false);
        TestMinecraftServer server = createServer(owner, secondaryManager, applicant);
        Project project = addTeamProject(owner, "join-owner-secondary-approve-project", "Join Owner Secondary Approve Project");
        project.addMember(secondaryManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondaryManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-owner-secondary-approve-project", createSource(0, applicant, server)), "重复项目经理场景中 join project 应返回成功");
        assertContainsMessageKey(owner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 对应项目经理应先收到 reviewer 通知");
        assertNotContainsMessageKey(secondaryManager.getClientMessages(), "message.todolist.project.join.request_received", "第二个 PROJECT_MANAGER 不应收到自动 reviewer 通知");

        int approveResult = dispatcher.execute("todo join accept join-owner-secondary-approve-project " + applicant.getStringUUID(), createSource(0, secondaryManager, server));
        assertEquals(1, approveResult, "第二个 PROJECT_MANAGER 手动执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "第二个 PROJECT_MANAGER 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "第二个 PROJECT_MANAGER 审批通过后申请人应收到 accepted");
        assertContainsMessageKey(secondaryManager.getClientMessages(), "message.todolist.project.join.approved", "第二个 PROJECT_MANAGER 审批通过后应收到 approved");
    }

    /**
     * 验证未收到自动 reviewer 通知的第二个 PROJECT_MANAGER 仍可手动执行 join deny。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldAllowJoinDenialBySecondaryProjectManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer owner = createPlayer("00000000-0000-0000-0000-000000000394", "manager-join-owner-deny-primary", false);
        TestServerPlayer secondaryManager = createPlayer("00000000-0000-0000-0000-000000000395", "manager-join-owner-deny-secondary", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000396", "applicant-join-owner-deny-secondary", false);
        TestMinecraftServer server = createServer(owner, secondaryManager, applicant);
        Project project = addTeamProject(owner, "join-owner-secondary-deny-project", "Join Owner Secondary Deny Project");
        project.addMember(secondaryManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondaryManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-owner-secondary-deny-project", createSource(0, applicant, server)), "重复项目经理场景中 join project 应返回成功");
        assertContainsMessageKey(owner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 对应项目经理应先收到 reviewer 通知");
        assertNotContainsMessageKey(secondaryManager.getClientMessages(), "message.todolist.project.join.request_received", "第二个 PROJECT_MANAGER 不应收到自动 reviewer 通知");

        int denyResult = dispatcher.execute("todo join deny join-owner-secondary-deny-project " + applicant.getStringUUID(), createSource(0, secondaryManager, server));
        assertEquals(1, denyResult, "第二个 PROJECT_MANAGER 手动执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "第二个 PROJECT_MANAGER 执行 join deny 后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "第二个 PROJECT_MANAGER 执行 join deny 后申请人应收到 denied");
        assertContainsMessageKey(secondaryManager.getClientMessages(), "message.todolist.project.join.rejected", "第二个 PROJECT_MANAGER 执行 join deny 后应收到 rejected");
    }

    /**
     * 验证 owner 不在线但 lead 在线时，会优先通知 lead 而不是兜底 OP。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldPreferLeadReviewerOverFallbackOperatorSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000336", "manager-join-lead-reviewer", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000337", "lead-join-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000338", "op-join-reviewer-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000339", "applicant-join-lead-reviewer", false);
        TestMinecraftServer server = createServer(lead, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-lead-reviewer-project", "Join Lead Reviewer Project");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-lead-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, result, "lead 在线时 join project 应返回命令成功");
        assertContainsMessageKey(lead.getClientMessages(), "message.todolist.project.join.request_received", "lead 在线时未收到 join project 审批通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "lead 在线时不应再通知兜底 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "lead 在线时申请人应收到 sent_named");
    }

    /**
     * 验证 ownerUuid 无效时，会回退通知在线 lead，而不是误落到 OP。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldFallbackToLeadWhenOwnerUuidIsInvalidSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000353", "manager-join-invalid-owner-reviewer", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000354", "lead-join-invalid-owner-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000355", "op-join-invalid-owner-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000356", "applicant-join-invalid-owner-reviewer", false);
        TestMinecraftServer server = createServer(lead, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reviewer-project", "Join Invalid Owner Reviewer Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-invalid-owner-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, result, "ownerUuid 非法时 join project 应返回命令成功");
        assertContainsMessageKey(lead.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 非法时未回退通知在线 lead");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 非法且有可用 lead 时不应通知兜底 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "ownerUuid 非法时申请人应收到 sent_named");
    }

    /**
     * 验证 ownerUuid 无效时，会先通知成员表中的全部在线项目经理，再考虑 OP 兜底。
     *
     * @throws Exception 命令执行失败时抛出异常
     */
    private static void shouldNotifyAllOnlineProjectManagersWhenOwnerUuidIsInvalidSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000397", "manager-join-invalid-owner-multi-manager", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000398", "manager-a-join-invalid-owner-reviewer", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000399", "manager-b-join-invalid-owner-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000400", "op-join-invalid-owner-multi-manager", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000401", "applicant-join-invalid-owner-multi-manager", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-multi-manager-project", "Join Invalid Owner Multi Manager Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-invalid-owner-multi-manager-project", createSource(0, applicant, server));
        assertEquals(1, result, "ownerUuid 无效且存在多个在线 PROJECT_MANAGER 时 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 无效时第一个在线 PROJECT_MANAGER 应收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 无效时第二个在线 PROJECT_MANAGER 也应收到 reviewer 通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 无效且已有在线 PROJECT_MANAGER 时不应继续回退通知 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "ownerUuid 无效且已有在线 PROJECT_MANAGER 时申请人应收到 sent_named");
    }
    /**
     * Validates that once one online project manager approves a request, other managers can no longer deny the cleared pending request.
     */
    private static void shouldClearPendingJoinRequestAfterApprovalByAnotherProjectManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000402", "manager-join-invalid-owner-cross-approve", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000403", "manager-a-join-invalid-owner-cross-approve", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000404", "manager-b-join-invalid-owner-cross-approve", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000405", "applicant-join-invalid-owner-cross-approve", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-cross-approve-project", "Join Invalid Owner Cross Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-cross-approve-project", createSource(0, applicant, server)), "ownerUuid 无效且有多个在线 PROJECT_MANAGER 时 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "第一个在线 PROJECT_MANAGER 应收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "第二个在线 PROJECT_MANAGER 也应收到 reviewer 通知");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-cross-approve-project " + applicant.getStringUUID(), createSource(0, firstManager, server));
        assertEquals(1, approveResult, "第一个在线 PROJECT_MANAGER 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "首次审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "首次审批通过后申请人应收到 accepted");

        int staleDenyResult = dispatcher.execute("todo join deny join-invalid-owner-cross-approve-project " + applicant.getStringUUID(), createSource(0, secondManager, server));
        assertEquals(0, staleDenyResult, "待审批记录被清理后另一个 PROJECT_MANAGER 的 join deny 应返回失败");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.already_member", "申请人已被加入项目后重复 join deny 应提示 already_member");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "重复 join deny 失败后不应移除已加入的申请人");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "重复 join deny 失败后不应再通知申请人 denied");
    }

    /**
     * Validates that once one online project manager denies a request, other managers can no longer approve the cleared pending request.
     */
    private static void shouldClearPendingJoinRequestAfterDenialByAnotherProjectManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000406", "manager-join-invalid-owner-cross-deny", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000407", "manager-a-join-invalid-owner-cross-deny", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000408", "manager-b-join-invalid-owner-cross-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000409", "applicant-join-invalid-owner-cross-deny", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-cross-deny-project", "Join Invalid Owner Cross Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-cross-deny-project", createSource(0, applicant, server)), "ownerUuid 无效且有多个在线 PROJECT_MANAGER 时 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "第一个在线 PROJECT_MANAGER 应收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "第二个在线 PROJECT_MANAGER 也应收到 reviewer 通知");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-cross-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, server));
        assertEquals(1, denyResult, "第一个在线 PROJECT_MANAGER 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "首次审批拒绝后申请人仍不应被加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "首次审批拒绝后申请人应收到 denied");

        int staleApproveResult = dispatcher.execute("todo join accept join-invalid-owner-cross-deny-project " + applicant.getStringUUID(), createSource(0, secondManager, server));
        assertEquals(0, staleApproveResult, "待审批记录被清理后另一个 PROJECT_MANAGER 的 join accept 应返回失败");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.no_pending_request", "申请已被拒绝后重复 join accept 应提示 no_pending_request");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "重复 join accept 失败后申请人仍不应被加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "重复 join accept 失败后不应再通知申请人 accepted");
    }

    /**
     * 校验无效 ownerUuid 且存在多个项目经理时，首次被其中一人拒绝后仍可重新申请，并由另一位项目经理审批通过。
     */
    private static void shouldAllowReapplyAfterCrossManagerDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000410", "manager-join-invalid-owner-reapply", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000411", "manager-a-join-invalid-owner-reapply", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000412", "manager-b-join-invalid-owner-reapply", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000413", "applicant-join-invalid-owner-reapply", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-project", "Join Invalid Owner Reapply Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-project", createSource(0, applicant, server)), "ownerUuid 无效且有多个在线 PROJECT_MANAGER 时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "首次被拒绝后申请人仍不应被加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "首次被拒绝后申请人应收到 denied");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "再次申请后第一个在线 PROJECT_MANAGER 应重新收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "再次申请后第二个在线 PROJECT_MANAGER 也应重新收到 reviewer 通知");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "再次申请后申请人应收到 sent_named");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-project " + applicant.getStringUUID(), createSource(0, secondManager, server));
        assertEquals(1, approveResult, "再次申请后另一位 PROJECT_MANAGER 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "再次申请通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "再次申请通过后申请人应收到 accepted");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.approved", "再次申请通过后审批人应收到 approved");
    }

    /**
     * 校验无效 ownerUuid 的多项目经理场景下，重申请挂起期间重复 join project 不会再次通知所有项目经理。
     */
    private static void shouldRejectDuplicateReapplyWithoutRenotifyingProjectManagersSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000414", "manager-join-invalid-owner-reapply-duplicate", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000415", "manager-a-join-invalid-owner-reapply-duplicate", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000416", "manager-b-join-invalid-owner-reapply-duplicate", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000417", "applicant-join-invalid-owner-reapply-duplicate", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-duplicate-project", "Join Invalid Owner Reapply Duplicate Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-duplicate-project", createSource(0, applicant, server)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-duplicate-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-duplicate-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        int firstNotificationCount = countMessagesContaining(firstManager.getClientMessages(), "message.todolist.project.join.request_received");
        int secondNotificationCount = countMessagesContaining(secondManager.getClientMessages(), "message.todolist.project.join.request_received");
        assertEquals(1, firstNotificationCount, "再次申请时第一个在线 PROJECT_MANAGER 应只收到一次 reviewer 通知");
        assertEquals(1, secondNotificationCount, "再次申请时第二个在线 PROJECT_MANAGER 应只收到一次 reviewer 通知");

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-duplicate-project", createSource(0, applicant, server)), "重申请挂起期间重复 join project 当前仍应返回命令成功");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.already_requested", "重申请挂起期间重复 join project 应提示 already_requested");
        assertEquals(firstNotificationCount, countMessagesContaining(firstManager.getClientMessages(), "message.todolist.project.join.request_received"), "重复重申请不应再次通知第一个在线 PROJECT_MANAGER");
        assertEquals(secondNotificationCount, countMessagesContaining(secondManager.getClientMessages(), "message.todolist.project.join.request_received"), "重复重申请不应再次通知第二个在线 PROJECT_MANAGER");
    }

    /**
     * 校验多个项目经理场景里首次拒绝后，若第二次申请时只剩一个项目经理在线，会只通知剩余在线审批人并允许其完成审批。
     */
    private static void shouldReapplyWithOnlyRemainingOnlineProjectManagerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000418", "manager-join-invalid-owner-reapply-online-change", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000419", "manager-a-join-invalid-owner-reapply-online-change", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000420", "manager-b-join-invalid-owner-reapply-online-change", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000421", "applicant-join-invalid-owner-reapply-online-change", false);
        TestMinecraftServer initialServer = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-online-change-project", "Join Invalid Owner Reapply Online Change Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-online-change-project", createSource(0, applicant, initialServer)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-online-change-project " + applicant.getStringUUID(), createSource(0, firstManager, initialServer)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer reapplyServer = createServer(secondManager, applicant);
        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-reapply-online-change-project", createSource(0, applicant, reapplyServer));
        assertEquals(1, reapplyResult, "审批人在线集合变化后再次 join project 应返回命令成功");
        assertNotContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "已离线的第一个 PROJECT_MANAGER 不应在重申请时继续收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "仍在线的第二个 PROJECT_MANAGER 应在重申请时收到 reviewer 通知");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "审批人在线集合变化后的重申请应向申请人回写 sent_named");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "仍有在线 PROJECT_MANAGER 时重申请不应提示 no_reviewer_online");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-online-change-project " + applicant.getStringUUID(), createSource(0, secondManager, reapplyServer));
        assertEquals(1, approveResult, "剩余在线 PROJECT_MANAGER 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "剩余在线 PROJECT_MANAGER 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "剩余在线 PROJECT_MANAGER 审批通过后申请人应收到 accepted");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.approved", "剩余在线 PROJECT_MANAGER 审批通过后应收到 approved");
    }

    /**
     * 校验多个项目经理场景里首次拒绝后，若第二次申请时没有任何项目经理在线，会先提示 no_reviewer_online，随后仍允许重新上线的项目经理完成审批。
     */
    private static void shouldApproveReapplyAfterNoProjectManagerWasOnlineSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000422", "manager-join-invalid-owner-reapply-no-reviewer", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000423", "manager-a-join-invalid-owner-reapply-no-reviewer", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000424", "manager-b-join-invalid-owner-reapply-no-reviewer", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000425", "applicant-join-invalid-owner-reapply-no-reviewer", false);
        TestMinecraftServer initialServer = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-no-reviewer-project", "Join Invalid Owner Reapply No Reviewer Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-no-reviewer-project", createSource(0, applicant, initialServer)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-no-reviewer-project " + applicant.getStringUUID(), createSource(0, firstManager, initialServer)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer offlineReapplyServer = createServer(applicant);
        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-reapply-no-reviewer-project", createSource(0, applicant, offlineReapplyServer));
        assertEquals(1, reapplyResult, "重申请时没有项目经理在线也应返回命令成功包裹");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "重申请时没有项目经理在线应提示 no_reviewer_online");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "重申请时没有项目经理在线不应提示 sent_named");
        assertNotContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请时没有项目经理在线不应误通知第一个 PROJECT_MANAGER");
        assertNotContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请时没有项目经理在线不应误通知第二个 PROJECT_MANAGER");

        TestMinecraftServer lateApprovalServer = createServer(secondManager, applicant);
        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-no-reviewer-project " + applicant.getStringUUID(), createSource(0, secondManager, lateApprovalServer));
        assertEquals(1, approveResult, "重新上线的 PROJECT_MANAGER 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "重新上线的 PROJECT_MANAGER 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "重新上线的 PROJECT_MANAGER 审批通过后申请人应收到 accepted");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.approved", "重新上线的 PROJECT_MANAGER 审批通过后应收到 approved");
    }

    /**
     * 校验多个项目经理场景里首次拒绝后，若第二次申请时没有任何项目经理在线，会先提示 no_reviewer_online，随后仍允许重新上线的项目经理完成拒绝。
     */
    private static void shouldDenyReapplyAfterNoProjectManagerWasOnlineSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000426", "manager-join-invalid-owner-reapply-no-reviewer-deny", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000427", "manager-a-join-invalid-owner-reapply-no-reviewer-deny", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000428", "manager-b-join-invalid-owner-reapply-no-reviewer-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000429", "applicant-join-invalid-owner-reapply-no-reviewer-deny", false);
        TestMinecraftServer initialServer = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-no-reviewer-deny-project", "Join Invalid Owner Reapply No Reviewer Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-no-reviewer-deny-project", createSource(0, applicant, initialServer)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-no-reviewer-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, initialServer)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer offlineReapplyServer = createServer(applicant);
        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-reapply-no-reviewer-deny-project", createSource(0, applicant, offlineReapplyServer));
        assertEquals(1, reapplyResult, "重申请时没有项目经理在线也应返回命令成功包裹");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "重申请时没有项目经理在线应提示 no_reviewer_online");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "重申请时没有项目经理在线不应提示 sent_named");
        assertNotContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请时没有项目经理在线不应误通知第一个 PROJECT_MANAGER");
        assertNotContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请时没有项目经理在线不应误通知第二个 PROJECT_MANAGER");

        TestMinecraftServer lateDenialServer = createServer(secondManager, applicant);
        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-no-reviewer-deny-project " + applicant.getStringUUID(), createSource(0, secondManager, lateDenialServer));
        assertEquals(1, denyResult, "重新上线的 PROJECT_MANAGER 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "重新上线的 PROJECT_MANAGER 拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "重新上线的 PROJECT_MANAGER 拒绝后申请人应收到 denied");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.rejected", "重新上线的 PROJECT_MANAGER 拒绝后应收到 rejected");
    }

    /**
     * 校验重申请已经挂起后，若最初的项目经理被移出项目，其旧 join accept 权限会失效，而剩余项目经理仍可完成审批。
     */
    private static void shouldRejectReapplyApprovalAfterProjectManagerRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000430", "manager-join-invalid-owner-reapply-removed-reviewer", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000431", "manager-a-join-invalid-owner-reapply-removed-reviewer", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000432", "manager-b-join-invalid-owner-reapply-removed-reviewer", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000433", "applicant-join-invalid-owner-reapply-removed-reviewer", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-removed-reviewer-project", "Join Invalid Owner Reapply Removed Reviewer Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-removed-reviewer-project", createSource(0, applicant, server)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-removed-reviewer-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-removed-reviewer-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第一个在线 PROJECT_MANAGER 应重新收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第二个在线 PROJECT_MANAGER 也应重新收到 reviewer 通知");

        project.removeMember(firstManager.getStringUUID());
        assertEquals(null, project.getMemberRole(firstManager.getStringUUID()), "移除后的第一个 PROJECT_MANAGER 不应继续保留项目角色");

        CapturingCommandSourceStack removedManagerSource = createSource(0, firstManager, server);
        int removedApproveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-removed-reviewer-project " + applicant.getStringUUID(), removedManagerSource);
        assertEquals(0, removedApproveResult, "被移出项目的旧 PROJECT_MANAGER 执行 join accept 应返回失败");
        assertContainsMessageKey(removedManagerSource.getFailureMessages(), "command.todolist.permission_denied", "被移出项目后的旧 PROJECT_MANAGER 执行 join accept 错误键不正确");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "旧 PROJECT_MANAGER 审批失败后不应提前把申请人加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "旧 PROJECT_MANAGER 审批失败后不应通知申请人 accepted");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-removed-reviewer-project " + applicant.getStringUUID(), createSource(0, secondManager, server));
        assertEquals(1, approveResult, "剩余 PROJECT_MANAGER 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "剩余 PROJECT_MANAGER 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "剩余 PROJECT_MANAGER 审批通过后申请人应收到 accepted");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.approved", "剩余 PROJECT_MANAGER 审批通过后应收到 approved");
    }

    /**
     * 校验重申请已经挂起后，若最初的项目经理被移出项目，其旧 join deny 权限会失效，而剩余项目经理仍可完成拒绝。
     */
    private static void shouldRejectReapplyDenialAfterProjectManagerRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000434", "manager-join-invalid-owner-reapply-removed-reviewer-deny", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000435", "manager-a-join-invalid-owner-reapply-removed-reviewer-deny", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000436", "manager-b-join-invalid-owner-reapply-removed-reviewer-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000437", "applicant-join-invalid-owner-reapply-removed-reviewer-deny", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-removed-reviewer-deny-project", "Join Invalid Owner Reapply Removed Reviewer Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-removed-reviewer-deny-project", createSource(0, applicant, server)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-removed-reviewer-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-removed-reviewer-deny-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第一个在线 PROJECT_MANAGER 应重新收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第二个在线 PROJECT_MANAGER 也应重新收到 reviewer 通知");

        project.removeMember(firstManager.getStringUUID());
        assertEquals(null, project.getMemberRole(firstManager.getStringUUID()), "移除后的第一个 PROJECT_MANAGER 不应继续保留项目角色");

        CapturingCommandSourceStack removedManagerSource = createSource(0, firstManager, server);
        int removedDenyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-removed-reviewer-deny-project " + applicant.getStringUUID(), removedManagerSource);
        assertEquals(0, removedDenyResult, "被移出项目的旧 PROJECT_MANAGER 执行 join deny 应返回失败");
        assertContainsMessageKey(removedManagerSource.getFailureMessages(), "command.todolist.permission_denied", "被移出项目后的旧 PROJECT_MANAGER 执行 join deny 错误键不正确");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "旧 PROJECT_MANAGER 拒绝失败后不应改动申请人成员状态");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "旧 PROJECT_MANAGER 拒绝失败后不应通知申请人 denied");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-removed-reviewer-deny-project " + applicant.getStringUUID(), createSource(0, secondManager, server));
        assertEquals(1, denyResult, "剩余 PROJECT_MANAGER 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "剩余 PROJECT_MANAGER 拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "剩余 PROJECT_MANAGER 拒绝后申请人应收到 denied");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.rejected", "剩余 PROJECT_MANAGER 拒绝后应收到 rejected");
    }

    /**
     * 校验重申请已经挂起后，若最初的项目经理被降为普通成员，其旧 join accept 权限会失效，而剩余项目经理仍可完成审批。
     */
    private static void shouldRejectReapplyApprovalAfterProjectManagerDemotionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000438", "manager-join-invalid-owner-reapply-demoted-reviewer", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000439", "manager-a-join-invalid-owner-reapply-demoted-reviewer", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000440", "manager-b-join-invalid-owner-reapply-demoted-reviewer", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000441", "applicant-join-invalid-owner-reapply-demoted-reviewer", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-demoted-reviewer-project", "Join Invalid Owner Reapply Demoted Reviewer Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-reviewer-project", createSource(0, applicant, server)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-demoted-reviewer-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-reviewer-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第一个在线 PROJECT_MANAGER 应重新收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第二个在线 PROJECT_MANAGER 也应重新收到 reviewer 通知");

        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.MEMBER, firstManager.getName().getString());
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(firstManager.getStringUUID()), "被降权后的原 PROJECT_MANAGER 应变为普通成员");

        CapturingCommandSourceStack demotedManagerSource = createSource(0, firstManager, server);
        int demotedApproveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-demoted-reviewer-project " + applicant.getStringUUID(), demotedManagerSource);
        assertEquals(0, demotedApproveResult, "被降权的旧 PROJECT_MANAGER 执行 join accept 应返回失败");
        assertContainsMessageKey(demotedManagerSource.getFailureMessages(), "command.todolist.permission_denied", "被降权的旧 PROJECT_MANAGER 执行 join accept 错误键不正确");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "旧 PROJECT_MANAGER 审批失败后不应提前把申请人加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "旧 PROJECT_MANAGER 审批失败后不应通知申请人 accepted");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-demoted-reviewer-project " + applicant.getStringUUID(), createSource(0, secondManager, server));
        assertEquals(1, approveResult, "剩余 PROJECT_MANAGER 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "剩余 PROJECT_MANAGER 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "剩余 PROJECT_MANAGER 审批通过后申请人应收到 accepted");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.approved", "剩余 PROJECT_MANAGER 审批通过后应收到 approved");
    }

    /**
     * 校验重申请已经挂起后，若最初的项目经理被降为普通成员，其旧 join deny 权限会失效，而剩余项目经理仍可完成拒绝。
     */
    private static void shouldRejectReapplyDenialAfterProjectManagerDemotionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000442", "manager-join-invalid-owner-reapply-demoted-reviewer-deny", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000443", "manager-a-join-invalid-owner-reapply-demoted-reviewer-deny", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000444", "manager-b-join-invalid-owner-reapply-demoted-reviewer-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000445", "applicant-join-invalid-owner-reapply-demoted-reviewer-deny", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-demoted-reviewer-deny-project", "Join Invalid Owner Reapply Demoted Reviewer Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-reviewer-deny-project", createSource(0, applicant, server)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-demoted-reviewer-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-reviewer-deny-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第一个在线 PROJECT_MANAGER 应重新收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第二个在线 PROJECT_MANAGER 也应重新收到 reviewer 通知");

        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.MEMBER, firstManager.getName().getString());
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(firstManager.getStringUUID()), "被降权后的原 PROJECT_MANAGER 应变为普通成员");

        CapturingCommandSourceStack demotedManagerSource = createSource(0, firstManager, server);
        int demotedDenyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-demoted-reviewer-deny-project " + applicant.getStringUUID(), demotedManagerSource);
        assertEquals(0, demotedDenyResult, "被降权的旧 PROJECT_MANAGER 执行 join deny 应返回失败");
        assertContainsMessageKey(demotedManagerSource.getFailureMessages(), "command.todolist.permission_denied", "被降权的旧 PROJECT_MANAGER 执行 join deny 错误键不正确");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "旧 PROJECT_MANAGER 拒绝失败后不应改动申请人成员状态");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "旧 PROJECT_MANAGER 拒绝失败后不应通知申请人 denied");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-demoted-reviewer-deny-project " + applicant.getStringUUID(), createSource(0, secondManager, server));
        assertEquals(1, denyResult, "剩余 PROJECT_MANAGER 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "剩余 PROJECT_MANAGER 拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "剩余 PROJECT_MANAGER 拒绝后申请人应收到 denied");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.rejected", "剩余 PROJECT_MANAGER 拒绝后应收到 rejected");
    }

    /**
     * 校验重申请已经挂起后，若全部项目经理都被降为普通成员，在线 OP 仍可作为兜底审批人完成 join accept。
     */
    private static void shouldAllowReapplyApprovalByOperatorAfterProjectManagerDemotionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000446", "manager-join-invalid-owner-reapply-demoted-op-approve", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000447", "manager-a-join-invalid-owner-reapply-demoted-op-approve", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000448", "manager-b-join-invalid-owner-reapply-demoted-op-approve", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000449", "op-join-invalid-owner-reapply-demoted-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000450", "applicant-join-invalid-owner-reapply-demoted-op-approve", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-demoted-op-approve-project", "Join Invalid Owner Reapply Demoted Op Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-op-approve-project", createSource(0, applicant, server)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-demoted-op-approve-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-op-approve-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第一个在线 PROJECT_MANAGER 应重新收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第二个在线 PROJECT_MANAGER 也应重新收到 reviewer 通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "项目经理仍在线时不应自动通知兜底 OP");

        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.MEMBER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.MEMBER, secondManager.getName().getString());
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(firstManager.getStringUUID()), "第一个原 PROJECT_MANAGER 被降权后应变为普通成员");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(secondManager.getStringUUID()), "第二个原 PROJECT_MANAGER 被降权后应变为普通成员");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-demoted-op-approve-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, approveResult, "在线 OP 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "在线 OP 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "在线 OP 审批通过后申请人应收到 accepted");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.approved", "在线 OP 审批通过后应收到 approved");
    }

    /**
     * 校验重申请已经挂起后，若全部项目经理都被降为普通成员，在线 OP 仍可作为兜底审批人完成 join deny。
     */
    private static void shouldAllowReapplyDenialByOperatorAfterProjectManagerDemotionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000451", "manager-join-invalid-owner-reapply-demoted-op-deny", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000452", "manager-a-join-invalid-owner-reapply-demoted-op-deny", false);
        TestServerPlayer secondManager = createPlayer("00000000-0000-0000-0000-000000000453", "manager-b-join-invalid-owner-reapply-demoted-op-deny", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000454", "op-join-invalid-owner-reapply-demoted-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000455", "applicant-join-invalid-owner-reapply-demoted-op-deny", false);
        TestMinecraftServer server = createServer(firstManager, secondManager, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-demoted-op-deny-project", "Join Invalid Owner Reapply Demoted Op Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, secondManager.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-op-deny-project", createSource(0, applicant, server)), "首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-demoted-op-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "第一个在线 PROJECT_MANAGER 首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        secondManager.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-demoted-op-deny-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第一个在线 PROJECT_MANAGER 应重新收到 reviewer 通知");
        assertContainsMessageKey(secondManager.getClientMessages(), "message.todolist.project.join.request_received", "重申请后第二个在线 PROJECT_MANAGER 也应重新收到 reviewer 通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "项目经理仍在线时不应自动通知兜底 OP");

        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.MEMBER, firstManager.getName().getString());
        project.addMember(secondManager.getStringUUID(), Project.ProjectRole.MEMBER, secondManager.getName().getString());
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(firstManager.getStringUUID()), "第一个原 PROJECT_MANAGER 被降权后应变为普通成员");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(secondManager.getStringUUID()), "第二个原 PROJECT_MANAGER 被降权后应变为普通成员");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-demoted-op-deny-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, denyResult, "在线 OP 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "在线 OP 拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "在线 OP 拒绝后申请人应收到 denied");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.rejected", "在线 OP 拒绝后应收到 rejected");
    }

    /**
     * 校验 ownerUuid 从脏数据修回在线项目经理后，重申请的 reviewer 通知会重新收敛到该 owner，而不会继续扩散给其他项目经理或 OP。
     */
    private static void shouldPreferRestoredOwnerProjectManagerOnReapplySuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000456", "manager-join-invalid-owner-reapply-restored-owner-offline", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000457", "manager-a-join-invalid-owner-reapply-restored-owner", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000458", "manager-b-join-invalid-owner-reapply-restored-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000459", "op-join-invalid-owner-reapply-restored-owner", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000460", "applicant-join-invalid-owner-reapply-restored-owner", false);
        TestMinecraftServer server = createServer(firstManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-restored-owner-project", "Join Invalid Owner Reapply Restored Owner Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 脏数据时第一个在线 PROJECT_MANAGER 应先收到 reviewer 通知");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 脏数据时第二个在线 PROJECT_MANAGER 也应先收到 reviewer 通知");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应只通知对应的在线 PROJECT_MANAGER");
        assertNotContainsMessageKey(firstManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后不应继续通知其他在线 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后不应继续通知兜底 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "ownerUuid 修复后申请人应收到 sent_named");
    }

    /**
     * 校验 ownerUuid 修复到在线项目经理后，若该 owner 随后离线，重申请会重新回退到仍在线的其他项目经理，而不会误落到 OP。
     */
    private static void shouldFallbackToRemainingProjectManagerAfterRestoredOwnerGoesOfflineSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000481", "manager-join-invalid-owner-restored-offline-fallback-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000482", "manager-a-join-invalid-owner-restored-offline-fallback", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000483", "manager-b-join-invalid-owner-restored-offline-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000484", "op-join-invalid-owner-restored-offline-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000485", "applicant-join-invalid-owner-restored-offline-fallback", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-offline-fallback-project", "Join Invalid Owner Restored Offline Fallback Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-offline-fallback-project", createSource(0, applicant, initialServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 脏数据时其他在线 PROJECT_MANAGER 应收到 reviewer 通知");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 脏数据时后续会被修回的 PROJECT_MANAGER 也应先收到 reviewer 通知");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-offline-fallback-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-offline-fallback-project", createSource(0, applicant, initialServer)), "ownerUuid 修复且 owner 在线时重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时应优先通知 owner");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时不应继续通知其他 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时不应通知 OP");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer ownerOfflineServer = createServer(remainingManager, operator, applicant);
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-offline-fallback-project " + applicant.getStringUUID(), createSource(2, operator, ownerOfflineServer)), "清理 owner 在线时的挂起申请应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-offline-fallback-project", createSource(0, applicant, ownerOfflineServer));
        assertEquals(1, reapplyResult, "owner 修复后又离线时再次重申请应返回命令成功");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "owner 离线后应重新回退通知仍在线的其他 PROJECT_MANAGER");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "离线的 owner 不应继续收到 reviewer 通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "仍有在线 PROJECT_MANAGER 时不应回退通知 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "owner 离线后的重申请应向申请人回写 sent_named");
    }

    /**
     * 校验 ownerUuid 修复后若 owner 先离线再重新上线，reviewer 通知会从其他在线项目经理重新收敛回 owner 优先。
     */
    private static void shouldReturnToRestoredOwnerReviewerWhenOwnerComesBackOnlineSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000494", "manager-join-invalid-owner-restored-return-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000495", "manager-a-join-invalid-owner-restored-return", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000496", "manager-b-join-invalid-owner-restored-return-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000497", "op-join-invalid-owner-restored-return", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000498", "applicant-join-invalid-owner-restored-return", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-return-project", "Join Invalid Owner Restored Return Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-project", createSource(0, applicant, initialServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-return-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-project", createSource(0, applicant, initialServer)), "ownerUuid 修复且 owner 在线时重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时应优先通知 owner");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时不应继续通知其他 PROJECT_MANAGER");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-return-project " + applicant.getStringUUID(), createSource(0, restoredOwner, initialServer)), "清理 owner 在线时的挂起申请应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer ownerOfflineServer = createServer(remainingManager, operator, applicant);
        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-project", createSource(0, applicant, ownerOfflineServer)), "owner 离线时重申请应返回命令成功");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "owner 离线时应回退通知其他在线 PROJECT_MANAGER");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 离线时不应继续收到 reviewer 通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 离线且仍有在线 PROJECT_MANAGER 时不应通知 OP");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-return-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOfflineServer)), "清理 owner 离线时的挂起申请应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer ownerBackOnlineServer = createServer(remainingManager, restoredOwner, operator, applicant);
        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-return-project", createSource(0, applicant, ownerBackOnlineServer));
        assertEquals(1, reapplyResult, "owner 重新上线后的重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 重新上线后应重新收敛回 owner reviewer");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "owner 重新上线后不应继续通知其他 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 重新上线后不应通知 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "owner 重新上线后的重申请应向申请人回写 sent_named");
    }
    /**
     * 验证 OP 清理旧申请并拒绝后，restored owner 回来时新一轮 reapply 会重新收敛到 owner 并可完成通过审批。
     */
    private static void shouldAllowReapplyApprovalByRestoredOwnerAfterOperatorDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000566", "manager-join-invalid-owner-op-reapply-owner-approve-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000567", "manager-a-join-invalid-owner-op-reapply-owner-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000568", "manager-b-join-invalid-owner-op-reapply-owner-approve-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000569", "op-join-invalid-owner-op-reapply-owner-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000570", "applicant-join-invalid-owner-op-reapply-owner-approve", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-op-reapply-owner-approve-project", "Join Invalid Owner Op Reapply Owner Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-owner-approve-project", createSource(0, applicant, initialServer)), "首次 join project 应成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-owner-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-owner-approve-project", createSource(0, applicant, operatorOnlyServer)), "OP 清理旧申请后的再次 join project 应成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-owner-approve-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer)), "OP 拒绝挂起的新申请应成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer ownerReturnServer = createServer(remainingManager, restoredOwner, operator, applicant);
        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-op-reapply-owner-approve-project", createSource(0, applicant, ownerReturnServer));
        assertEquals(1, reapplyResult, "restored owner 回来后的 reapply 应成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "restored owner 应收到新的 reviewer 通知");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "owner 回来后不应继续通知 remainingManager");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 回来后不应继续通知 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "reapply 时申请人应收到 sent_named");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-op-reapply-owner-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerReturnServer));
        assertEquals(1, approveResult, "restored owner 执行 join accept 应成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "restored owner 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "申请人应收到 accepted");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.approved", "restored owner 应收到 approved");
    }

    /**
     * 验证 OP 清理旧申请并拒绝后，restored owner 回来时新一轮 reapply 会重新收敛到 owner 并可完成拒绝审批。
     */
    private static void shouldAllowReapplyDenialByRestoredOwnerAfterOperatorDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000571", "manager-join-invalid-owner-op-reapply-owner-deny-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000572", "manager-a-join-invalid-owner-op-reapply-owner-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000573", "manager-b-join-invalid-owner-op-reapply-owner-deny-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000574", "op-join-invalid-owner-op-reapply-owner-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000575", "applicant-join-invalid-owner-op-reapply-owner-deny", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-op-reapply-owner-deny-project", "Join Invalid Owner Op Reapply Owner Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-owner-deny-project", createSource(0, applicant, initialServer)), "首次 join project 应成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-owner-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-owner-deny-project", createSource(0, applicant, operatorOnlyServer)), "OP 清理旧申请后的再次 join project 应成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-owner-deny-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer)), "OP 拒绝挂起的新申请应成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();

        TestMinecraftServer ownerReturnServer = createServer(remainingManager, restoredOwner, operator, applicant);
        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-op-reapply-owner-deny-project", createSource(0, applicant, ownerReturnServer));
        assertEquals(1, reapplyResult, "restored owner 回来后的 reapply 应成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "restored owner 应收到新的 reviewer 通知");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "owner 回来后不应继续通知 remainingManager");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 回来后不应继续通知 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "reapply 时申请人应收到 sent_named");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-op-reapply-owner-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerReturnServer));
        assertEquals(1, denyResult, "restored owner 执行 join deny 应成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "restored owner 拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "申请人应收到 denied");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.rejected", "restored owner 应收到 rejected");
    }

    /**
     * 校验在线 OP 清理旧申请后，只要 remainingManager 重新上线，申请人的新一轮 reapply 会重新收敛到 remainingManager，并可正常审批通过。
     */
    private static void shouldAllowReapplyApprovalByRemainingManagerAfterOperatorDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000546", "manager-join-invalid-owner-op-reapply-manager-approve-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000547", "manager-a-join-invalid-owner-op-reapply-manager-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000548", "manager-b-join-invalid-owner-op-reapply-manager-approve-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000549", "op-join-invalid-owner-op-reapply-manager-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000550", "applicant-join-invalid-owner-op-reapply-manager-approve", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-op-reapply-manager-approve-project", "Join Invalid Owner Op Reapply Manager Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-manager-approve-project", createSource(0, applicant, initialServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-manager-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-manager-approve-project", createSource(0, applicant, operatorOnlyServer)), "owner 与其他项目经理都离线时重申请应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-manager-approve-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer)), "在线 OP 清理旧申请应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        TestMinecraftServer managerReturnServer = createServer(remainingManager, applicant);

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-op-reapply-manager-approve-project", createSource(0, applicant, managerReturnServer));
        assertEquals(1, reapplyResult, "remainingManager 回来后重新申请应返回命令成功");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "remainingManager 回来后应重新接收 reviewer 通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "已有在线项目经理时不应继续通知 OP");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 仍离线时不应误通知 restored owner");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "remainingManager 回来后的新申请应向申请人回写 sent_named");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-op-reapply-manager-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, managerReturnServer));
        assertEquals(1, approveResult, "remainingManager 回来后执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "remainingManager 回来后审批通过应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "remainingManager 回来后审批通过应通知申请人 accepted");
    }

    /**
     * 校验在线 OP 清理旧申请后，只要 remainingManager 重新上线，申请人的新一轮 reapply 也可重新收敛到 remainingManager 并正常拒绝。
     */
    private static void shouldAllowReapplyDenialByRemainingManagerAfterOperatorDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000551", "manager-join-invalid-owner-op-reapply-manager-deny-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000552", "manager-a-join-invalid-owner-op-reapply-manager-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000553", "manager-b-join-invalid-owner-op-reapply-manager-deny-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000554", "op-join-invalid-owner-op-reapply-manager-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000555", "applicant-join-invalid-owner-op-reapply-manager-deny", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-op-reapply-manager-deny-project", "Join Invalid Owner Op Reapply Manager Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-manager-deny-project", createSource(0, applicant, initialServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-manager-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-op-reapply-manager-deny-project", createSource(0, applicant, operatorOnlyServer)), "owner 与其他项目经理都离线时重申请应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-op-reapply-manager-deny-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer)), "在线 OP 清理旧申请应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        TestMinecraftServer managerReturnServer = createServer(remainingManager, applicant);

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-op-reapply-manager-deny-project", createSource(0, applicant, managerReturnServer));
        assertEquals(1, reapplyResult, "remainingManager 回来后重新申请应返回命令成功");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "remainingManager 回来后应重新接收 reviewer 通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "已有在线项目经理时不应继续通知 OP");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 仍离线时不应误通知 restored owner");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "remainingManager 回来后的新申请应向申请人回写 sent_named");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-op-reapply-manager-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, managerReturnServer));
        assertEquals(1, denyResult, "remainingManager 回来后执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "remainingManager 回来后拒绝不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "remainingManager 回来后拒绝应通知申请人 denied");
    }
    /**
     * 校验 owner 与其他项目经理都离线并由在线 OP 审批通过后，remainingManager 回来重复 join deny 会命中 already_member。
     */
    private static void shouldClearPendingJoinRequestAfterOperatorApprovalWhenRemainingManagerReturnsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000536", "manager-join-invalid-owner-restored-op-manager-return-approve-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000537", "manager-a-join-invalid-owner-restored-op-manager-return-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000538", "manager-b-join-invalid-owner-restored-op-manager-return-approve-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000539", "op-join-invalid-owner-restored-op-manager-return-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000540", "applicant-join-invalid-owner-restored-op-manager-return-approve", false);
        TestMinecraftServer ownerOnlineServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-op-manager-return-approve-project", "Join Invalid Owner Restored Op Manager Return Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-op-manager-return-approve-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-op-manager-return-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOnlineServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-op-manager-return-approve-project", createSource(0, applicant, operatorOnlyServer));
        assertEquals(1, reapplyResult, "owner 与其他项目经理都离线时重申请应返回命令成功包裹");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 与其他项目经理都离线时应自动通知在线 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "在线 OP 自动接管 reviewer 时申请人应收到 sent_named");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-op-manager-return-approve-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer));
        assertEquals(1, approveResult, "在线 OP 手动 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "在线 OP 手动审批通过后应将申请人加入项目");

        TestMinecraftServer managerReturnServer = createServer(remainingManager, operator, applicant);
        int staleDenyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-op-manager-return-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, managerReturnServer));
        assertEquals(0, staleDenyResult, "OP 已清理待审批记录后 remainingManager 的 join deny 应返回失败");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.already_member", "申请人已被加入项目后 remainingManager 重复 join deny 应提示 already_member");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "remainingManager 重复 join deny 失败后不应回滚申请人的成员状态");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "remainingManager 重复 join deny 失败后不应再通知申请人 denied");
    }

    /**
     * 校验 owner 与其他项目经理都离线并由在线 OP 拒绝后，remainingManager 回来重复 join accept 会命中 no_pending_request。
     */
    private static void shouldClearPendingJoinRequestAfterOperatorDenialWhenRemainingManagerReturnsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000541", "manager-join-invalid-owner-restored-op-manager-return-deny-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000542", "manager-a-join-invalid-owner-restored-op-manager-return-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000543", "manager-b-join-invalid-owner-restored-op-manager-return-deny-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000544", "op-join-invalid-owner-restored-op-manager-return-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000545", "applicant-join-invalid-owner-restored-op-manager-return-deny", false);
        TestMinecraftServer ownerOnlineServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-op-manager-return-deny-project", "Join Invalid Owner Restored Op Manager Return Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-op-manager-return-deny-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-op-manager-return-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOnlineServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-op-manager-return-deny-project", createSource(0, applicant, operatorOnlyServer));
        assertEquals(1, reapplyResult, "owner 与其他项目经理都离线时重申请应返回命令成功包裹");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 与其他项目经理都离线时应自动通知在线 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "在线 OP 自动接管 reviewer 时申请人应收到 sent_named");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-op-manager-return-deny-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer));
        assertEquals(1, denyResult, "在线 OP 手动 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "在线 OP 手动拒绝后不应将申请人加入项目");

        TestMinecraftServer managerReturnServer = createServer(remainingManager, operator, applicant);
        int staleApproveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-op-manager-return-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, managerReturnServer));
        assertEquals(0, staleApproveResult, "OP 已清理待审批记录后 remainingManager 的 join accept 应返回失败");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.no_pending_request", "申请已被 OP 清理后 remainingManager 重复 join accept 应提示 no_pending_request");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "remainingManager 重复 join accept 失败后不应把申请人重新加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "remainingManager 重复 join accept 失败后不应再通知申请人 accepted");
    }
    /**
     * 校验 owner 与其他项目经理都离线时会自动回退通知在线 OP，且 OP 审批通过后 restored owner 回来重复 join deny 会命中 already_member。
     */
    private static void shouldClearPendingJoinRequestAfterOperatorApprovalWhenRestoredOwnerReturnsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000526", "manager-join-invalid-owner-restored-op-return-approve-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000527", "manager-a-join-invalid-owner-restored-op-return-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000528", "manager-b-join-invalid-owner-restored-op-return-approve-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000529", "op-join-invalid-owner-restored-op-return-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000530", "applicant-join-invalid-owner-restored-op-return-approve", false);
        TestMinecraftServer ownerOnlineServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-op-return-approve-project", "Join Invalid Owner Restored Op Return Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-op-return-approve-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-op-return-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOnlineServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-op-return-approve-project", createSource(0, applicant, operatorOnlyServer));
        assertEquals(1, reapplyResult, "owner 与其他项目经理都离线时重申请应返回命令成功包裹");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 与其他项目经理都离线时应自动通知在线 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "在线 OP 自动接管 reviewer 时申请人应收到 sent_named");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-op-return-approve-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer));
        assertEquals(1, approveResult, "在线 OP 手动 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "在线 OP 手动审批通过后应将申请人加入项目");

        TestMinecraftServer ownerReturnServer = createServer(restoredOwner, operator, applicant);
        int staleDenyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-op-return-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerReturnServer));
        assertEquals(0, staleDenyResult, "OP 已清理待审批记录后 restored owner 的 join deny 应返回失败");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.already_member", "申请人已被加入项目后 restored owner 重复 join deny 应提示 already_member");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "restored owner 重复 join deny 失败后不应回滚申请人的成员状态");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "restored owner 重复 join deny 失败后不应再通知申请人 denied");
    }

    /**
     * 校验 owner 与其他项目经理都离线时会自动回退通知在线 OP，且 OP 拒绝后 restored owner 回来重复 join accept 会命中 no_pending_request。
     */
    private static void shouldClearPendingJoinRequestAfterOperatorDenialWhenRestoredOwnerReturnsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000531", "manager-join-invalid-owner-restored-op-return-deny-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000532", "manager-a-join-invalid-owner-restored-op-return-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000533", "manager-b-join-invalid-owner-restored-op-return-deny-owner", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000534", "op-join-invalid-owner-restored-op-return-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000535", "applicant-join-invalid-owner-restored-op-return-deny", false);
        TestMinecraftServer ownerOnlineServer = createServer(remainingManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-op-return-deny-project", "Join Invalid Owner Restored Op Return Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-op-return-deny-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-op-return-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOnlineServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());
        TestMinecraftServer operatorOnlyServer = createServer(operator, applicant);

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-op-return-deny-project", createSource(0, applicant, operatorOnlyServer));
        assertEquals(1, reapplyResult, "owner 与其他项目经理都离线时重申请应返回命令成功包裹");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "owner 与其他项目经理都离线时应自动通知在线 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "在线 OP 自动接管 reviewer 时申请人应收到 sent_named");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-op-return-deny-project " + applicant.getStringUUID(), createSource(2, operator, operatorOnlyServer));
        assertEquals(1, denyResult, "在线 OP 手动 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "在线 OP 手动拒绝后不应将申请人加入项目");

        TestMinecraftServer ownerReturnServer = createServer(restoredOwner, operator, applicant);
        int staleApproveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-op-return-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerReturnServer));
        assertEquals(0, staleApproveResult, "OP 已清理待审批记录后 restored owner 的 join accept 应返回失败");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.no_pending_request", "申请已被 OP 清理后 restored owner 重复 join accept 应提示 no_pending_request");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "restored owner 重复 join accept 失败后不应把申请人重新加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "restored owner 重复 join accept 失败后不应再通知申请人 accepted");
    }
    /**
     * 校验 leadUuid 非法且没有其他可用 reviewer 时，join project 会回退通知在线 OP。
     */
    private static void shouldFallbackToOperatorWhenLeadUuidIsInvalidSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000357", "manager-join-invalid-lead-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000358", "op-join-invalid-lead-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000359", "applicant-join-invalid-lead-reviewer", false);
        TestMinecraftServer server = createServer(operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-lead-reviewer-project", "Join Invalid Lead Reviewer Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember("not-a-valid-lead-uuid", Project.ProjectRole.LEAD, "dirty-lead");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-invalid-lead-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, result, "leadUuid 非法时 join project 应返回命令成功");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "leadUuid 非法时未回退通知在线 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "leadUuid 非法时申请人应收到 sent_named");
    }

    /**
     * 校验 lead 列表里混入脏 UUID 时，join project 仍会继续扫描并通知后续可用的在线 lead。
     */
    private static void shouldSkipInvalidLeadAndNotifyOtherOnlineLeadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000360", "manager-join-mixed-lead-reviewer", false);
        TestServerPlayer validLead = createPlayer("00000000-0000-0000-0000-000000000361", "lead-join-mixed-lead-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000362", "op-join-mixed-lead-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000363", "applicant-join-mixed-lead-reviewer", false);
        TestMinecraftServer server = createServer(validLead, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-mixed-lead-reviewer-project", "Join Mixed Lead Reviewer Project");
        java.util.Map<String, Project.ProjectRole> members = new java.util.LinkedHashMap<>();
        members.put(offlineManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER);
        members.put("not-a-valid-lead-uuid", Project.ProjectRole.LEAD);
        members.put(validLead.getStringUUID(), Project.ProjectRole.LEAD);
        project.setMembers(members);
        java.util.Map<String, String> memberNames = new java.util.LinkedHashMap<>();
        memberNames.put(offlineManager.getStringUUID(), offlineManager.getName().getString());
        memberNames.put("not-a-valid-lead-uuid", "dirty-lead");
        memberNames.put(validLead.getStringUUID(), validLead.getName().getString());
        project.setMemberNames(memberNames);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-mixed-lead-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, result, "lead 列表混入非法 UUID 时 join project 应返回命令成功");
        assertContainsMessageKey(validLead.getClientMessages(), "message.todolist.project.join.request_received", "非法 leadUuid 之后的在线 lead 仍应收到审批通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "已有后续在线 lead 可用时不应回退通知兜底 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "跳过非法 leadUuid 后申请人应收到 sent_named");
    }

    /**
     * 校验 owner 不在线且有多个 lead 在线时，会通知所有在线 lead，并且不会再落到兜底 OP。
     */
    private static void shouldNotifyAllOnlineLeadsBeforeFallbackOperatorSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000348", "manager-join-multi-lead-reviewer", false);
        TestServerPlayer firstLead = createPlayer("00000000-0000-0000-0000-000000000349", "lead-a-join-reviewer", false);
        TestServerPlayer secondLead = createPlayer("00000000-0000-0000-0000-000000000350", "lead-b-join-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000351", "op-join-multi-lead-fallback", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000352", "applicant-join-multi-lead-reviewer", false);
        TestMinecraftServer server = createServer(firstLead, secondLead, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-multi-lead-reviewer-project", "Join Multi Lead Reviewer Project");
        project.addMember(firstLead.getStringUUID(), Project.ProjectRole.LEAD, firstLead.getName().getString());
        project.addMember(secondLead.getStringUUID(), Project.ProjectRole.LEAD, secondLead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project join-multi-lead-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, result, "多个 lead 在线时 join project 应返回命令成功");
        assertContainsMessageKey(firstLead.getClientMessages(), "message.todolist.project.join.request_received", "第一个 lead 在线时未收到 join project 审批通知");
        assertContainsMessageKey(secondLead.getClientMessages(), "message.todolist.project.join.request_received", "第二个 lead 在线时未收到 join project 审批通知");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "已有在线 lead 时不应继续通知兜底 OP");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "多个 lead 在线时申请人应收到 sent_named");
    }

    /**
     * 校验 owner 或 lead 不在线时，在线 OP 可以作为兜底审批人通过 join accept 完成审批。
     */
    private static void shouldAllowJoinApprovalByOnlineOperatorSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000330", "manager-join-op-reviewer", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000331", "op-join-reviewer", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000332", "applicant-join-op-reviewer", false);
        TestMinecraftServer server = createServer(operator, applicant);
        Project project = addTeamProject(offlineManager, "join-op-reviewer-project", "Join Op Reviewer Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int requestResult = dispatcher.execute("todo join project join-op-reviewer-project", createSource(0, applicant, server));
        assertEquals(1, requestResult, "在线 OP 作为兜底审批人时 join project 应返回成功");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "在线 OP 未收到 join project 审批通知");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "在线 OP 存在时申请人应收到 sent_named");

        int approveResult = dispatcher.execute("todo join accept join-op-reviewer-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, approveResult, "在线 OP 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "在线 OP 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "在线 OP 审批通过后申请人未收到 accepted");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.approved", "在线 OP 审批通过后未收到 approved");
    }

    /**
     * 校验 owner 或 lead 不在线时，在线 OP 可以作为兜底审批人通过 join deny 拒绝申请。
     */
    private static void shouldAllowJoinDenialByOnlineOperatorSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000333", "manager-join-op-deny", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000334", "op-join-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000335", "applicant-join-op-deny", false);
        TestMinecraftServer server = createServer(operator, applicant);
        Project project = addTeamProject(offlineManager, "join-op-deny-project", "Join Op Deny Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int requestResult = dispatcher.execute("todo join project join-op-deny-project", createSource(0, applicant, server));
        assertEquals(1, requestResult, "在线 OP 作为兜底审批人时 join project 应返回成功");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "在线 OP 未收到 join project 拒绝用审批通知");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "在线 OP 存在时申请人应收到 sent_named");

        int denyResult = dispatcher.execute("todo join deny join-op-deny-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, denyResult, "在线 OP 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "在线 OP 审批拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "在线 OP 审批拒绝后申请人未收到 denied");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.rejected", "在线 OP 审批拒绝后未收到 rejected");
    }

    /**
     * 校验申请人不能审批自己的 join project 请求。
     */
    private static void shouldRejectApprovingOwnJoinRequestSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000322", "manager-join-self-approve", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000323", "applicant-join-self-approve", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-self-approve-project", "Join Self Approve Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-self-approve-project", createSource(0, applicant, server)), "申请加入项目应先返回命令成功");

        int result = dispatcher.execute("todo join accept join-self-approve-project " + applicant.getStringUUID(), createSource(2, applicant, server));
        assertEquals(0, result, "申请人审批自己的 join request 应返回失败");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.cannot_approve_self", "申请人自审时错误键不正确");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "申请人自审失败后不应被加入项目");
    }

    /**
     * 校验普通成员不能通过命令执行 join accept，且不会污染待审批状态。
     */
    private static void shouldRejectJoinAcceptForRegularMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000324", "manager-join-accept-permission", false);
        TestServerPlayer reviewer = createPlayer("00000000-0000-0000-0000-000000000325", "member-join-accept-permission", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000326", "applicant-join-accept-permission", false);
        TestMinecraftServer server = createServer(manager, reviewer, applicant);
        Project project = addTeamProject(manager, "join-accept-permission-project", "Join Accept Permission Project");
        project.addMember(reviewer.getStringUUID(), Project.ProjectRole.MEMBER, reviewer.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-accept-permission-project", createSource(0, applicant, server)), "join project 应返回成功");

        CapturingCommandSourceStack deniedSource = createSource(0, reviewer, server);
        int deniedResult = dispatcher.execute("todo join accept join-accept-permission-project " + applicant.getStringUUID(), deniedSource);
        assertEquals(0, deniedResult, "普通成员 join accept 应返回失败");
        assertContainsMessageKey(deniedSource.getFailureMessages(), "command.todolist.permission_denied", "普通成员 join accept 的错误键不正确");
        assertNotContainsMessageKey(reviewer.getClientMessages(), "message.todolist.project.join.no_permission", "命令层拒绝 join accept 时不应再进入业务 no_permission 分支");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "普通成员 join accept 失败时不应通知申请人已通过");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "普通成员 join accept 失败后不应修改申请人成员状态");

        assertEquals(1, dispatcher.execute("todo join accept join-accept-permission-project " + applicant.getStringUUID(), createSource(0, manager, server)), "未授权 join accept 失败后项目经理仍应可以审批通过");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "未授权 join accept 失败后待审批记录应保持可审批");
    }

    /**
     * 校验普通成员不能通过命令执行 join deny，且不会污染待审批状态。
     */
    private static void shouldRejectJoinDenyForRegularMemberSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000327", "manager-join-deny-permission", false);
        TestServerPlayer reviewer = createPlayer("00000000-0000-0000-0000-000000000328", "member-join-deny-permission", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000329", "applicant-join-deny-permission", false);
        TestMinecraftServer server = createServer(manager, reviewer, applicant);
        Project project = addTeamProject(manager, "join-deny-permission-project", "Join Deny Permission Project");
        project.addMember(reviewer.getStringUUID(), Project.ProjectRole.MEMBER, reviewer.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-permission-project", createSource(0, applicant, server)), "join project 应返回成功");

        CapturingCommandSourceStack deniedSource = createSource(0, reviewer, server);
        int deniedResult = dispatcher.execute("todo join deny join-deny-permission-project " + applicant.getStringUUID(), deniedSource);
        assertEquals(0, deniedResult, "普通成员 join deny 应返回失败");
        assertContainsMessageKey(deniedSource.getFailureMessages(), "command.todolist.permission_denied", "普通成员 join deny 的错误键不正确");
        assertNotContainsMessageKey(reviewer.getClientMessages(), "message.todolist.project.join.no_permission", "命令层拒绝 join deny 时不应再进入业务 no_permission 分支");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "普通成员 join deny 失败时不应通知申请人已被拒绝");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "普通成员 join deny 失败后不应修改申请人成员状态");

        assertEquals(1, dispatcher.execute("todo join deny join-deny-permission-project " + applicant.getStringUUID(), createSource(0, manager, server)), "未授权 join deny 失败后项目经理仍应可以审批拒绝");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "未授权 join deny 失败后申请人仍不应被加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "项目经理补充执行 join deny 后申请人应收到拒绝提示");
    }

    /**
     * 校验 lead 在申请挂起期间被降权为普通成员后，不能继续执行 join accept。
     */
    private static void shouldRejectJoinAcceptAfterLeadDemotionSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000374", "manager-join-lead-demotion", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000375", "lead-join-lead-demotion", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000376", "applicant-join-lead-demotion", false);
        TestMinecraftServer server = createServer(manager, lead, applicant);
        Project project = addTeamProject(manager, "join-lead-demotion-project", "Join Lead Demotion Project");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-lead-demotion-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo project member role join-lead-demotion-project " + lead.getStringUUID() + " member", createSource(0, manager, server)), "项目经理降权 lead 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(lead.getStringUUID()), "lead 降权后应变为普通成员");

        CapturingCommandSourceStack deniedSource = createSource(0, lead, server);
        int deniedResult = dispatcher.execute("todo join accept join-lead-demotion-project " + applicant.getStringUUID(), deniedSource);
        assertEquals(0, deniedResult, "被降权的 lead 执行 join accept 应返回失败");
        assertContainsMessageKey(deniedSource.getFailureMessages(), "command.todolist.permission_denied", "被降权的 lead 执行 join accept 错误键不正确");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "被降权的 lead 审批失败时不应通知申请人已通过");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "被降权的 lead 审批失败后不应修改申请人成员状态");

        assertEquals(1, dispatcher.execute("todo join accept join-lead-demotion-project " + applicant.getStringUUID(), createSource(0, manager, server)), "lead 降权后项目经理仍应可以完成 join accept");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "项目经理补充执行 join accept 后应将申请人加入项目");
    }

    /**
     * 校验 lead 在申请挂起期间被移出项目后，不能继续执行 join deny。
     */
    private static void shouldRejectJoinDenyAfterLeadRemovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000377", "manager-join-lead-removal", false);
        TestServerPlayer lead = createPlayer("00000000-0000-0000-0000-000000000378", "lead-join-lead-removal", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000379", "applicant-join-lead-removal", false);
        TestMinecraftServer server = createServer(manager, lead, applicant);
        Project project = addTeamProject(manager, "join-lead-removal-project", "Join Lead Removal Project");
        project.addMember(lead.getStringUUID(), Project.ProjectRole.LEAD, lead.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-lead-removal-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo project member remove join-lead-removal-project " + lead.getStringUUID(), createSource(0, manager, server)), "项目经理移除 lead 应返回成功");
        assertEquals(null, project.getMemberRole(lead.getStringUUID()), "lead 被移除后不应继续留在成员列表");

        CapturingCommandSourceStack deniedSource = createSource(0, lead, server);
        int deniedResult = dispatcher.execute("todo join deny join-lead-removal-project " + applicant.getStringUUID(), deniedSource);
        assertEquals(0, deniedResult, "被移除的 lead 执行 join deny 应返回失败");
        assertContainsMessageKey(deniedSource.getFailureMessages(), "command.todolist.permission_denied", "被移除的 lead 执行 join deny 错误键不正确");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "被移除的 lead 审批失败时不应通知申请人被拒绝");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "被移除的 lead 审批失败后不应修改申请人成员状态");

        assertEquals(1, dispatcher.execute("todo join deny join-lead-removal-project " + applicant.getStringUUID(), createSource(0, manager, server)), "lead 被移除后项目经理仍应可以完成 join deny");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "项目经理补充执行 join deny 后申请人仍不应被加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "项目经理补充执行 join deny 后申请人应收到拒绝提示");
    }

    /**
     * 校验脏 ownerUuid 指向非成员时，不应把 join reviewer 通知和审批权限错误授予 outsider。
     */
    private static void shouldIgnoreCorruptedOwnerUuidReviewerAndKeepRealManagerApprovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000380", "manager-join-corrupted-owner", false);
        TestServerPlayer outsider = createPlayer("00000000-0000-0000-0000-000000000381", "outsider-join-corrupted-owner", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000382", "applicant-join-corrupted-owner", false);
        TestMinecraftServer server = createServer(manager, outsider, applicant);
        Project project = addTeamProject(manager, "join-corrupted-owner-project", "Join Corrupted Owner Project");
        project.setOwnerUuid(outsider.getStringUUID());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-corrupted-owner-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertNotContainsMessageKey(outsider.getClientMessages(), "message.todolist.project.join.request_received", "脏 ownerUuid 指向的 outsider 不应收到 join reviewer 通知");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.request_received", "真实项目经理应继续收到 join reviewer 通知");

        CapturingCommandSourceStack outsiderSource = createSource(0, outsider, server);
        int outsiderResult = dispatcher.execute("todo join accept join-corrupted-owner-project " + applicant.getStringUUID(), outsiderSource);
        assertEquals(0, outsiderResult, "脏 ownerUuid 指向的 outsider 执行 join accept 应返回失败");
        assertContainsMessageKey(outsiderSource.getFailureMessages(), "command.todolist.permission_denied", "脏 ownerUuid 指向的 outsider 执行 join accept 错误键不正确");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "outsider 审批失败时不应通知申请人已通过");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "outsider 审批失败后不应修改申请人成员状态");

        assertEquals(1, dispatcher.execute("todo join accept join-corrupted-owner-project " + applicant.getStringUUID(), createSource(0, manager, server)), "真实项目经理仍应可以完成 join accept");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "真实项目经理补充审批后应将申请人加入项目");
    }

    /**
     * 校验脏 ownerUuid 指向申请人本人但其并非成员时，join project 不会被误判为 already_member。
     */
    private static void shouldAllowJoinRequestWhenCorruptedOwnerUuidMatchesApplicantSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000383", "manager-join-owner-applicant", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000384", "applicant-join-owner-applicant", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-owner-applicant-project", "Join Owner Applicant Project");
        project.setOwnerUuid(applicant.getStringUUID());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack joinSource = createSource(0, applicant, server);
        int joinResult = dispatcher.execute("todo join project join-owner-applicant-project", joinSource);
        assertEquals(1, joinResult, "脏 ownerUuid 指向申请人时 join project 仍应返回成功");
        assertNotContainsMessageKey(joinSource.getFailureMessages(), "message.todolist.project.join.already_member", "脏 ownerUuid 指向申请人时不应误判 already_member");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.request_received", "脏 ownerUuid 指向申请人时真实项目经理仍应收到审批通知");

        assertEquals(1, dispatcher.execute("todo join accept join-owner-applicant-project " + applicant.getStringUUID(), createSource(0, manager, server)), "脏 ownerUuid 指向申请人时真实项目经理仍应可以完成 join accept");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "审批通过后申请人应被正常加入项目");
    }

    /**
     * 校验 ownerUuid 被清空后，成员表里的真实项目经理仍会继续承担 join reviewer 与审批职责。
     */
    private static void shouldFallbackToMemberTableManagerWhenOwnerUuidMissingSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000385", "manager-join-missing-owner", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000386", "applicant-join-missing-owner", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-missing-owner-project", "Join Missing Owner Project");
        project.setOwnerUuid("");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack joinSource = createSource(0, applicant, server);
        int joinResult = dispatcher.execute("todo join project join-missing-owner-project", joinSource);
        assertEquals(1, joinResult, "ownerUuid 为空时 join project 仍应返回成功");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 为空时真实项目经理仍应收到审批通知");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "ownerUuid 为空且真实项目经理在线时申请人应收到 sent_named");
        assertNotContainsMessageKey(joinSource.getFailureMessages(), "message.todolist.project.join.no_reviewer_online", "ownerUuid 为空但真实项目经理在线时不应提示 no_reviewer_online");

        int approveResult = dispatcher.execute("todo join accept join-missing-owner-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(1, approveResult, "ownerUuid 为空时真实项目经理仍应可以完成 join accept");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "ownerUuid 为空时审批通过后申请人应被正常加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "ownerUuid 为空时申请人应收到 accepted");
    }

    /**
     * 校验命令层会拒绝非法 applicantUuid 的 join accept 输入，并且不会误落到 no_pending_request 分支。
     */
    private static void shouldRejectJoinAcceptForInvalidApplicantUuidSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000344", "manager-join-invalid-accept", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000345", "applicant-join-invalid-accept", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-invalid-accept-project", "Join Invalid Accept Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-accept-project", createSource(0, applicant, server)), "非法 UUID 校验前的 join project 应返回成功");

        CapturingCommandSourceStack invalidSource = createSource(0, manager, server);
        int invalidResult = dispatcher.execute("todo join accept join-invalid-accept-project not-a-uuid", invalidSource);
        assertEquals(0, invalidResult, "非法 applicantUuid 的 join accept 应返回失败");
        assertContainsMessageKey(invalidSource.getFailureMessages(), "command.todolist.join.invalid_applicant_uuid", "非法 applicantUuid 的 join accept 错误键不正确");
        assertNotContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.no_pending_request", "非法 applicantUuid 的 join accept 不应误落到 no_pending_request");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "非法 applicantUuid 的 join accept 不应通知申请人已通过");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "非法 applicantUuid 的 join accept 失败后不应改动申请人成员状态");

        assertEquals(1, dispatcher.execute("todo join accept join-invalid-accept-project " + applicant.getStringUUID(), createSource(0, manager, server)), "非法 applicantUuid 的 join accept 失败后仍应可用真实 UUID 完成审批");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "真实 UUID 的 join accept 应将申请人加入项目");
    }

    /**
     * 校验命令层会拒绝非法 applicantUuid 的 join deny 输入，且不会误落到业务 no_pending_request 分支。
     */
    private static void shouldRejectJoinDenyForInvalidApplicantUuidSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000346", "manager-join-invalid-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000347", "applicant-join-invalid-deny", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-invalid-deny-project", "Join Invalid Deny Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-deny-project", createSource(0, applicant, server)), "非法 UUID 校验前的 join project 应返回成功");

        CapturingCommandSourceStack invalidSource = createSource(0, manager, server);
        int invalidResult = dispatcher.execute("todo join deny join-invalid-deny-project not-a-uuid", invalidSource);
        assertEquals(0, invalidResult, "非法 applicantUuid 的 join deny 应返回失败");
        assertContainsMessageKey(invalidSource.getFailureMessages(), "command.todolist.join.invalid_applicant_uuid", "非法 applicantUuid 的 join deny 错误键不正确");
        assertNotContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.no_pending_request", "非法 applicantUuid 的 join deny 不应误落到 no_pending_request");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "非法 applicantUuid 的 join deny 不应通知申请人已被拒绝");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "非法 applicantUuid 的 join deny 失败后不应改动申请人成员状态");

        assertEquals(1, dispatcher.execute("todo join deny join-invalid-deny-project " + applicant.getStringUUID(), createSource(0, manager, server)), "非法 applicantUuid 的 join deny 失败后仍应可用真实 UUID 完成拒绝");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "真实 UUID 的 join deny 不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "真实 UUID 的 join deny 应通知申请人被拒绝");
    }

    /**
     * 校验未发起 join project 时，join accept 会显式拒绝审批。
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

    /**
     * 校验单机模式下无法加入项目。
     */
    /**
     * 验证 join project 在项目不存在时会提示 invalid_project。
     */
    private static void shouldRejectJoinProjectForInvalidProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000415", "join-invalid-project-user", false);
        TestMinecraftServer server = createServer(applicant);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo join project missing-join-project", createSource(0, applicant, server));
        assertEquals(1, result, "不存在项目的 join project 命令链路应保持可执行");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.invalid_project", "join project 不存在项目提示键不正确");
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
     * 验证申请人临时离线时 join deny 会保留待处理状态，重新上线后仍可审批拒绝。
     */
    private static void shouldDenyJoinRequestAfterApplicantReconnectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000368", "manager-join-deny-reconnect", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000369", "applicant-join-deny-reconnect", false);
        TestMinecraftServer initialServer = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-deny-reconnect-project", "Join Deny Reconnect Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-reconnect-project", createSource(0, applicant, initialServer)), "join project 应返回成功");

        TestServerPlayer offlineManager = createPlayer(manager.getStringUUID(), manager.getName().getString(), false);
        TestMinecraftServer offlineServer = createServer(offlineManager);
        int offlineResult = dispatcher.execute("todo join deny join-deny-reconnect-project " + applicant.getStringUUID(), createSource(0, offlineManager, offlineServer));
        assertEquals(0, offlineResult, "申请人离线时 join deny 应返回失败");
        assertContainsMessageKey(offlineManager.getClientMessages(), "message.todolist.project.join.applicant_offline", "申请人离线时 join deny 提示不正确");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "申请人离线时不应被提前加入项目");

        TestServerPlayer onlineManager = createPlayer(manager.getStringUUID(), manager.getName().getString(), false);
        TestServerPlayer reconnectedApplicant = createPlayer(applicant.getStringUUID(), applicant.getName().getString(), false);
        TestMinecraftServer reconnectedServer = createServer(onlineManager, reconnectedApplicant);
        int onlineResult = dispatcher.execute("todo join deny join-deny-reconnect-project " + applicant.getStringUUID(), createSource(0, onlineManager, reconnectedServer));
        assertEquals(1, onlineResult, "申请人重新上线后 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "申请人重新上线后 join deny 不应将成员加入项目");
        assertContainsMessageKey(reconnectedApplicant.getClientMessages(), "message.todolist.project.join.denied", "申请人重新上线后未收到拒绝提示");
        assertContainsMessageKey(onlineManager.getClientMessages(), "message.todolist.project.join.rejected", "申请人重新上线后审批人未收到拒绝结果");
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

    /**
     * 校验通过 add member 网络包手动补加成员时，也会清理挂起中的 join request。
     */
    private static void shouldClearPendingJoinRequestWhenApplicantIsAddedViaPacketSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000372", "manager-join-packet-add-clear", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000373", "applicant-join-packet-add-clear", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-packet-add-clear-project", "Join Packet Add Clear Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-packet-add-clear-project", createSource(0, applicant, server)), "join project 应先返回成功");

        net.minecraft.network.FriendlyByteBuf addPacket = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        addPacket.writeUtf(project.getId());
        addPacket.writeUtf(applicant.getStringUUID());
        addPacket.writeUtf(applicant.getName().getString());
        ProjectPackets.onAddMemberPacket(server, manager, addPacket);
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "网络包补加成员后申请人应已在项目成员列表");

        assertEquals(1, dispatcher.execute("todo project member remove join-packet-add-clear-project " + applicant.getStringUUID(), createSource(0, manager, server)), "移除网络包加入的成员应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "移除后申请人不应继续留在项目成员列表");

        int staleApproveResult = dispatcher.execute("todo join accept join-packet-add-clear-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, staleApproveResult, "网络包加成员后旧 join request 不应继续可审批");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.no_pending_request", "网络包加成员后旧 join request 应提示 no_pending_request");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "网络包加成员清理旧请求后不应再通知申请人 accepted");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "网络包加成员清理旧请求后不应再次把申请人加入项目");
    }

    /**
     * 校验挂起中的 join request 在项目经理手动添加成员后会被清理，避免旧申请在成员移除后继续生效。
     */
    private static void shouldClearPendingJoinRequestWhenApplicantIsAddedManuallySuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000370", "manager-join-manual-add-clear", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000371", "applicant-join-manual-add-clear", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-manual-add-clear-project", "Join Manual Add Clear Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-manual-add-clear-project", createSource(0, applicant, server)), "join project 应先返回成功");
        assertEquals(1, dispatcher.execute("todo project member add join-manual-add-clear-project applicant-join-manual-add-clear", createSource(0, manager, server)), "项目经理手动补加成员应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "手动补加成员后申请人应已在项目成员列表");

        assertEquals(1, dispatcher.execute("todo project member remove join-manual-add-clear-project " + applicant.getStringUUID(), createSource(0, manager, server)), "移除手动加入的成员应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "移除后申请人不应继续留在项目成员列表");

        int staleApproveResult = dispatcher.execute("todo join accept join-manual-add-clear-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, staleApproveResult, "手动加成员后旧 join request 不应继续可审批");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.no_pending_request", "手动加成员后旧 join request 应提示 no_pending_request");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "旧 join request 被清理后不应再通知申请人 accepted");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "旧 join request 被清理后不应再次把申请人加入项目");
    }

    /**
     * 校验审批通过后重复执行 join accept 会因为申请人已是成员而失败。
     */
    private static void shouldRejectRepeatedJoinAcceptAfterApprovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000220", "manager-join-approve-repeat", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000221", "applicant-join-approve-repeat", false);
        TestMinecraftServer server = createServer(manager, applicant);
        addTeamProject(manager, "join-approve-repeat-project", "Join Approve Repeat Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-approve-repeat-project", createSource(0, applicant, server)), "首次 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join accept join-approve-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server)), "首次 join accept 应返回成功");

        int repeatedResult = dispatcher.execute("todo join accept join-approve-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, repeatedResult, "重复 join accept 应返回失败");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.already_member", "重复 join accept 的错误键不正确");
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

        assertEquals(1, dispatcher.execute("todo join project join-deny-repeat-project", createSource(0, applicant, server)), "首次 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join deny join-deny-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server)), "首次 join deny 应返回成功");

        int repeatedResult = dispatcher.execute("todo join deny join-deny-repeat-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(0, repeatedResult, "重复 join deny 应返回失败");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.no_pending_request", "重复 join deny 的错误键不正确");
    }

    /**
     * 验证同一申请人在不同项目上的 join 审批结果彼此隔离。
     */
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

    /**
     * 校验申请通过后，成员视角可以查看团队任务列表。
     */
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

        assertEquals(1, dispatcher.execute("todo join project join-page-project", createSource(0, applicant, server)), "分页前 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join accept join-page-project " + applicant.getStringUUID(), createSource(0, manager, server)), "分页前 join accept 应返回成功");

        CapturingCommandSourceStack firstPageSource = createSource(0, applicant, server);
        int firstPageResult = dispatcher.execute("todo task listp join-page-project", firstPageSource);
        assertEquals(1, firstPageResult, "成员 task listp 第一页应返回成功");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.project.list.summary", "成员 task listp 第一页未输出项目汇总消息");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "成员 task listp 第一页未输出页码状态");
        assertEquals(10, countMessagesContaining(firstPageSource.getSuccessMessages(), "Joined Member Page Task "), "成员 task listp 第一页应显示 10 条任务");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "成员 task listp 第一页未输出下一页按钮");

        CapturingCommandSourceStack secondPageSource = createSource(0, applicant, server);
        int secondPageResult = dispatcher.execute("todo task more", secondPageSource);
        assertEquals(1, secondPageResult, "成员 task more 第二页应返回成功");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "成员 task more 第二页未输出页码状态");
        assertEquals(2, countMessagesContaining(secondPageSource.getSuccessMessages(), "Joined Member Page Task "), "成员 task more 第二页应只显示剩余 2 条任务");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.prev_button", "成员 task more 第二页未输出上一页按钮");
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

        assertEquals(1, dispatcher.execute("todo join project join-deny-project", createSource(0, applicant, server)), "join project 应返回成功");
        int result = dispatcher.execute("todo join deny join-deny-project " + applicant.getStringUUID(), createSource(0, manager, server));
        assertEquals(1, result, "join deny 成功时应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "join deny 不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "join deny 未通知申请人审批拒绝");
        assertContainsMessageKey(manager.getClientMessages(), "message.todolist.project.join.rejected", "join deny 未通知审批人审批结果");
    }

    /**
     * 校验申请被拒绝后，申请人可以再次发起加入并在下一轮通过审批。
     */
    private static void shouldAllowJoinRequestAgainAfterDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000228", "manager-join-reapply", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000229", "applicant-join-reapply", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-reapply-project", "Join Reapply Project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-reapply-project", createSource(0, applicant, server)), "第一次 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join deny join-reapply-project " + applicant.getStringUUID(), createSource(0, manager, server)), "第一次 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "被拒绝后申请人不应加入项目");

        assertEquals(1, dispatcher.execute("todo join project join-reapply-project", createSource(0, applicant, server)), "被拒绝后再次 join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join accept join-reapply-project " + applicant.getStringUUID(), createSource(0, manager, server)), "再次申请后的 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "再次申请后应成功加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "再次申请通过后未通知申请人");
    }

    /**
     * 校验申请被拒绝后，申请人无法继续查看该团队项目的任务列表。
     */
    private static void shouldRejectTeamTaskListAfterJoinDeniedSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000146", "manager-join-deny-list", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000147", "applicant-join-deny-list", false);
        TestMinecraftServer server = createServer(manager, applicant);
        Project project = addTeamProject(manager, "join-deny-list-project", "Join Deny List Project");
        saveTeamTasks(createTeamTask(project.getId(), "Denied Access Task"));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-deny-list-project", createSource(0, applicant, server)), "join project 应返回成功");
        assertEquals(1, dispatcher.execute("todo join deny join-deny-list-project " + applicant.getStringUUID(), createSource(0, manager, server)), "join deny 应返回成功");

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
        assertEquals(1, dispatcher.execute("todo join deny join-deny-more-project " + applicant.getStringUUID(), createSource(0, manager, server)), "join deny 应返回成功");

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

/**
     * 校验 owner 再次离线后若由 remainingManager 先执行 join accept，随后 restored owner 回来重复 join deny 会命中 already_member，不会回滚已通过的成员状态。
     */
    private static void shouldClearPendingJoinRequestAfterFallbackManagerApprovalWhenRestoredOwnerReturnsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000518", "manager-join-invalid-owner-restored-return-cross-approve-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000519", "manager-a-join-invalid-owner-restored-return-cross-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000520", "manager-b-join-invalid-owner-restored-return-cross-approve-owner", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000521", "applicant-join-invalid-owner-restored-return-cross-approve", false);
        TestMinecraftServer ownerOnlineServer = createServer(remainingManager, restoredOwner, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-return-cross-approve-project", "Join Invalid Owner Restored Return Cross Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-cross-approve-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-return-cross-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOnlineServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-cross-approve-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应优先通知 restored owner");

        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-return-cross-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerOnlineServer)), "restored owner 清理在线申请应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        TestMinecraftServer ownerOfflineServer = createServer(remainingManager, applicant);

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-cross-approve-project", createSource(0, applicant, ownerOfflineServer)), "owner 再次离线后重申请应返回命令成功");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "owner 再次离线后应回退通知 remainingManager");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 再次离线后不应继续通知 restored owner");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-return-cross-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOfflineServer));
        assertEquals(1, approveResult, "remainingManager 接管后执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "remainingManager 接管审批通过后应将申请人加入项目");

        TestMinecraftServer ownerReturnServer = createServer(remainingManager, restoredOwner, applicant);
        int staleDenyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-return-cross-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerReturnServer));
        assertEquals(0, staleDenyResult, "remainingManager 已清理待审批记录后 restored owner 的 join deny 应返回失败");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.already_member", "申请人已被加入项目后 restored owner 重复 join deny 应提示 already_member");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "restored owner 重复 join deny 失败后不应回滚申请人的成员状态");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "restored owner 重复 join deny 失败后不应再通知申请人 denied");
    }

/**
     * 校验 owner 再次离线后若由 remainingManager 先执行 join deny，随后 restored owner 回来重复 join accept 会命中 no_pending_request，不会重新放行已清理的申请。
     */
    private static void shouldClearPendingJoinRequestAfterFallbackManagerDenialWhenRestoredOwnerReturnsSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000522", "manager-join-invalid-owner-restored-return-cross-deny-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000523", "manager-a-join-invalid-owner-restored-return-cross-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000524", "manager-b-join-invalid-owner-restored-return-cross-deny-owner", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000525", "applicant-join-invalid-owner-restored-return-cross-deny", false);
        TestMinecraftServer ownerOnlineServer = createServer(remainingManager, restoredOwner, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-return-cross-deny-project", "Join Invalid Owner Restored Return Cross Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-cross-deny-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-return-cross-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOnlineServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-cross-deny-project", createSource(0, applicant, ownerOnlineServer)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应优先通知 restored owner");

        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-return-cross-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerOnlineServer)), "restored owner 清理在线申请应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        TestMinecraftServer ownerOfflineServer = createServer(remainingManager, applicant);

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-return-cross-deny-project", createSource(0, applicant, ownerOfflineServer)), "owner 再次离线后重申请应返回命令成功");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "owner 再次离线后应回退通知 remainingManager");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 再次离线后不应继续通知 restored owner");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-return-cross-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, ownerOfflineServer));
        assertEquals(1, denyResult, "remainingManager 接管后执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "remainingManager 接管拒绝后不应将申请人加入项目");

        TestMinecraftServer ownerReturnServer = createServer(remainingManager, restoredOwner, applicant);
        int staleApproveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-return-cross-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, ownerReturnServer));
        assertEquals(0, staleApproveResult, "remainingManager 已清理待审批记录后 restored owner 的 join accept 应返回失败");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.no_pending_request", "申请已被 remainingManager 清理后 restored owner 重复 join accept 应提示 no_pending_request");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "restored owner 重复 join accept 失败后不应把申请人重新加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "restored owner 重复 join accept 失败后不应再通知申请人 accepted");
    }

/**
     * 校验 ownerUuid 修复且 restored owner 在线时若由 owner 先执行 join accept，其他项目经理随后重复 join deny 会命中 already_member，不会回滚已通过的成员状态。
     */
    private static void shouldClearPendingJoinRequestAfterRestoredOwnerApprovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000500", "manager-join-invalid-owner-restored-owner-cross-approve-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000501", "manager-a-join-invalid-owner-restored-owner-cross-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000502", "manager-b-join-invalid-owner-restored-owner-cross-approve", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000503", "applicant-join-invalid-owner-restored-owner-cross-approve", false);
        TestMinecraftServer server = createServer(remainingManager, restoredOwner, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-owner-cross-approve-project", "Join Invalid Owner Restored Owner Cross Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-cross-approve-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-owner-cross-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, server)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-cross-approve-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应优先通知 restored owner");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后不应继续通知 remainingManager");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-owner-cross-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, server));
        assertEquals(1, approveResult, "restored owner 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "restored owner 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "restored owner 审批通过后申请人应收到 accepted");

        int staleDenyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-owner-cross-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, server));
        assertEquals(0, staleDenyResult, "restored owner 清理待审批记录后 remainingManager 的 join deny 应返回失败");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.already_member", "申请人已被加入项目后 remainingManager 重复 join deny 应提示 already_member");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "remainingManager 重复 join deny 失败后不应回滚申请人的成员状态");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "remainingManager 重复 join deny 失败后不应再通知申请人 denied");
    }

/**
     * 校验 ownerUuid 修复且 restored owner 在线时若由 owner 先执行 join deny，其他项目经理随后重复 join accept 会命中 no_pending_request，不会重新放行已清理的申请。
     */
    private static void shouldClearPendingJoinRequestAfterRestoredOwnerDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000504", "manager-join-invalid-owner-restored-owner-cross-deny-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000505", "manager-a-join-invalid-owner-restored-owner-cross-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000506", "manager-b-join-invalid-owner-restored-owner-cross-deny", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000507", "applicant-join-invalid-owner-restored-owner-cross-deny", false);
        TestMinecraftServer server = createServer(remainingManager, restoredOwner, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-owner-cross-deny-project", "Join Invalid Owner Restored Owner Cross Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-cross-deny-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-owner-cross-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, server)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-cross-deny-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应优先通知 restored owner");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后不应继续通知 remainingManager");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-owner-cross-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, server));
        assertEquals(1, denyResult, "restored owner 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "restored owner 拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "restored owner 拒绝后申请人应收到 denied");

        int staleApproveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-owner-cross-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, server));
        assertEquals(0, staleApproveResult, "restored owner 清理待审批记录后 remainingManager 的 join accept 应返回失败");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.no_pending_request", "申请已被 restored owner 清理后 remainingManager 重复 join accept 应提示 no_pending_request");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "remainingManager 重复 join accept 失败后不应把申请人重新加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "remainingManager 重复 join accept 失败后不应再通知申请人 accepted");
    }

/**
     * 校验 ownerUuid 修复后若 owner 与其他项目经理都离线，重申请会重新落到 no_reviewer_online，随后仍可由重新上线的项目经理完成审批通过。
     */
    private static void shouldApproveReapplyAfterRestoredOwnerAndManagersGoOfflineSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000486", "manager-join-invalid-owner-restored-all-offline-approve-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000487", "manager-a-join-invalid-owner-restored-all-offline-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000488", "manager-b-join-invalid-owner-restored-all-offline-owner", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000489", "applicant-join-invalid-owner-restored-all-offline-approve", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-all-offline-approve-project", "Join Invalid Owner Restored All Offline Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-all-offline-approve-project", createSource(0, applicant, initialServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-all-offline-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-all-offline-approve-project", createSource(0, applicant, initialServer)), "ownerUuid 修复且 owner 在线时重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时应优先通知 owner");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时不应继续通知其他 PROJECT_MANAGER");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        TestMinecraftServer allOfflineServer = createServer(applicant);

        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-all-offline-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, initialServer)), "清理 owner 在线时的挂起申请应返回成功");

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-all-offline-approve-project", createSource(0, applicant, allOfflineServer));
        assertEquals(1, reapplyResult, "owner 与其他项目经理都离线时重申请仍应返回命令成功包裹");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "owner 与其他项目经理都离线时应提示 no_reviewer_online");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "owner 与其他项目经理都离线时不应提示 sent_named");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "其他项目经理离线时不应收到 reviewer 通知");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 离线时不应收到 reviewer 通知");

        TestMinecraftServer lateApprovalServer = createServer(remainingManager, applicant);
        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-all-offline-approve-project " + applicant.getStringUUID(), createSource(0, remainingManager, lateApprovalServer));
        assertEquals(1, approveResult, "重新上线的 PROJECT_MANAGER 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "重新上线的 PROJECT_MANAGER 审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "重新上线的 PROJECT_MANAGER 审批通过后申请人应收到 accepted");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.approved", "重新上线的 PROJECT_MANAGER 审批通过后应收到 approved");
    }

/**
     * 校验 ownerUuid 修复后若 owner 与其他项目经理都离线，重申请会重新落到 no_reviewer_online，随后仍可由重新上线的项目经理完成拒绝。
     */
    private static void shouldDenyReapplyAfterRestoredOwnerAndManagersGoOfflineSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000490", "manager-join-invalid-owner-restored-all-offline-deny-offline", false);
        TestServerPlayer remainingManager = createPlayer("00000000-0000-0000-0000-000000000491", "manager-a-join-invalid-owner-restored-all-offline-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000492", "manager-b-join-invalid-owner-restored-all-offline-deny-owner", false);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000493", "applicant-join-invalid-owner-restored-all-offline-deny", false);
        TestMinecraftServer initialServer = createServer(remainingManager, restoredOwner, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-all-offline-deny-project", "Join Invalid Owner Restored All Offline Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(remainingManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, remainingManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-all-offline-deny-project", createSource(0, applicant, initialServer)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-all-offline-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, initialServer)), "首次 join deny 应返回成功");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-all-offline-deny-project", createSource(0, applicant, initialServer)), "ownerUuid 修复且 owner 在线时重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时应优先通知 owner");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复且 owner 在线时不应继续通知其他 PROJECT_MANAGER");

        remainingManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        applicant.getClientMessages().clear();
        TestMinecraftServer allOfflineServer = createServer(applicant);

        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-all-offline-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, initialServer)), "清理 owner 在线时的挂起申请应返回成功");

        int reapplyResult = dispatcher.execute("todo join project join-invalid-owner-restored-all-offline-deny-project", createSource(0, applicant, allOfflineServer));
        assertEquals(1, reapplyResult, "owner 与其他项目经理都离线时重申请仍应返回命令成功包裹");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.no_reviewer_online", "owner 与其他项目经理都离线时应提示 no_reviewer_online");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.sent_named", "owner 与其他项目经理都离线时不应提示 sent_named");
        assertNotContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.request_received", "其他项目经理离线时不应收到 reviewer 通知");
        assertNotContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "owner 离线时不应收到 reviewer 通知");

        TestMinecraftServer lateDenialServer = createServer(remainingManager, applicant);
        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-all-offline-deny-project " + applicant.getStringUUID(), createSource(0, remainingManager, lateDenialServer));
        assertEquals(1, denyResult, "重新上线的 PROJECT_MANAGER 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "重新上线的 PROJECT_MANAGER 拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "重新上线的 PROJECT_MANAGER 拒绝后申请人应收到 denied");
        assertContainsMessageKey(remainingManager.getClientMessages(), "message.todolist.project.join.rejected", "重新上线的 PROJECT_MANAGER 拒绝后应收到 rejected");
    }

/**
     * 校验 ownerUuid 已修回在线项目经理后，在线 OP 虽然不会再收到自动 reviewer 通知，但仍可按当前全局管理员语义手动完成 join accept。
     */
    private static void shouldAllowOperatorApprovalAfterOwnerUuidRestoredSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000461", "manager-join-invalid-owner-reapply-restored-owner-op-offline", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000462", "manager-a-join-invalid-owner-reapply-restored-owner-op", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000463", "manager-b-join-invalid-owner-reapply-restored-owner-op", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000464", "op-join-invalid-owner-reapply-restored-owner-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000465", "applicant-join-invalid-owner-reapply-restored-owner-op", false);
        TestMinecraftServer server = createServer(firstManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-restored-owner-op-project", "Join Invalid Owner Reapply Restored Owner Op Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-op-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应通知对应的在线 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后 OP 不应再收到自动 reviewer 通知");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-restored-owner-op-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, approveResult, "ownerUuid 修复后在线 OP 手动执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "在线 OP 手动审批通过后应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "在线 OP 手动审批通过后申请人应收到 accepted");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.approved", "在线 OP 手动审批通过后应收到 approved");
    }

/**
     * 校验 ownerUuid 已修回在线项目经理后，在线 OP 虽然不会再收到自动 reviewer 通知，但仍可按当前全局管理员语义手动完成 join deny。
     */
    private static void shouldAllowOperatorDenialAfterOwnerUuidRestoredSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000466", "manager-join-invalid-owner-reapply-restored-owner-op-deny-offline", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000467", "manager-a-join-invalid-owner-reapply-restored-owner-op-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000468", "manager-b-join-invalid-owner-reapply-restored-owner-op-deny", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000469", "op-join-invalid-owner-reapply-restored-owner-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000470", "applicant-join-invalid-owner-reapply-restored-owner-op-deny", false);
        TestMinecraftServer server = createServer(firstManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-restored-owner-op-deny-project", "Join Invalid Owner Reapply Restored Owner Op Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-deny-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-op-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-deny-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应通知对应的在线 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后 OP 不应再收到自动 reviewer 通知");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-op-deny-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, denyResult, "ownerUuid 修复后在线 OP 手动执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "在线 OP 手动拒绝后不应将申请人加入项目");
        assertContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "在线 OP 手动拒绝后申请人应收到 denied");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.rejected", "在线 OP 手动拒绝后应收到 rejected");
    }

/**
     * 校验 ownerUuid 修复后若在线 OP 先手动审批通过，随后 owner 再执行 join deny 会命中 already_member，而不会回滚已通过的成员状态。
     */
    private static void shouldClearPendingJoinRequestAfterOperatorApprovalWithRestoredOwnerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000471", "manager-join-invalid-owner-reapply-restored-owner-op-cross-approve-offline", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000472", "manager-a-join-invalid-owner-reapply-restored-owner-op-cross-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000473", "manager-b-join-invalid-owner-reapply-restored-owner-op-cross-approve", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000474", "op-join-invalid-owner-reapply-restored-owner-cross-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000475", "applicant-join-invalid-owner-reapply-restored-owner-op-cross-approve", false);
        TestMinecraftServer server = createServer(firstManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-restored-owner-op-cross-approve-project", "Join Invalid Owner Reapply Restored Owner Op Cross Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-cross-approve-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-op-cross-approve-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-cross-approve-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应通知对应的在线 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后 OP 不应再收到自动 reviewer 通知");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-restored-owner-op-cross-approve-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, approveResult, "在线 OP 手动执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "在线 OP 审批通过后应将申请人加入项目");

        int staleDenyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-op-cross-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, server));
        assertEquals(0, staleDenyResult, "待审批记录被 OP 清理后 owner 的 join deny 应返回失败");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.already_member", "申请人已被加入项目后 owner 重复 join deny 应提示 already_member");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "owner 重复 join deny 失败后不应移除已加入的申请人");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "owner 重复 join deny 失败后不应再通知申请人 denied");
    }

/**
     * 校验 ownerUuid 修复后若在线 OP 先手动拒绝，随后 owner 再执行 join accept 会命中 no_pending_request，而不会重新放行已清理的申请。
     */
    private static void shouldClearPendingJoinRequestAfterOperatorDenialWithRestoredOwnerSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000476", "manager-join-invalid-owner-reapply-restored-owner-op-cross-deny-offline", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000477", "manager-a-join-invalid-owner-reapply-restored-owner-op-cross-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000478", "manager-b-join-invalid-owner-reapply-restored-owner-op-cross-deny", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000479", "op-join-invalid-owner-reapply-restored-owner-cross-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000480", "applicant-join-invalid-owner-reapply-restored-owner-op-cross-deny", false);
        TestMinecraftServer server = createServer(firstManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-reapply-restored-owner-op-cross-deny-project", "Join Invalid Owner Reapply Restored Owner Op Cross Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-cross-deny-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-op-cross-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-reapply-restored-owner-op-cross-deny-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应通知对应的在线 PROJECT_MANAGER");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后 OP 不应再收到自动 reviewer 通知");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-reapply-restored-owner-op-cross-deny-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(1, denyResult, "在线 OP 手动执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "在线 OP 拒绝后不应将申请人加入项目");

        int staleApproveResult = dispatcher.execute("todo join accept join-invalid-owner-reapply-restored-owner-op-cross-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, server));
        assertEquals(0, staleApproveResult, "待审批记录被 OP 清理后 owner 的 join accept 应返回失败");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.no_pending_request", "申请已被 OP 拒绝后 owner 重复 join accept 应提示 no_pending_request");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "owner 重复 join accept 失败后申请人仍不应被加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "owner 重复 join accept 失败后不应再通知申请人 accepted");
    }

/**
     * 校验 ownerUuid 修复后若 restored owner 先审批通过，在线 OP 随后重复 join deny 会命中 already_member，而不会回滚已通过的成员状态。
     */
    private static void shouldClearPendingJoinRequestForOperatorAfterRestoredOwnerApprovalSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000508", "manager-join-invalid-owner-restored-owner-op-stale-approve-offline", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000509", "manager-a-join-invalid-owner-restored-owner-op-stale-approve", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000510", "manager-b-join-invalid-owner-restored-owner-op-stale-approve", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000511", "op-join-invalid-owner-restored-owner-stale-approve", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000512", "applicant-join-invalid-owner-restored-owner-op-stale-approve", false);
        TestMinecraftServer server = createServer(firstManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-owner-op-stale-approve-project", "Join Invalid Owner Restored Owner Op Stale Approve Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-op-stale-approve-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-owner-op-stale-approve-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-op-stale-approve-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应优先通知 restored owner");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后 OP 不应收到自动 reviewer 通知");

        int approveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-owner-op-stale-approve-project " + applicant.getStringUUID(), createSource(0, restoredOwner, server));
        assertEquals(1, approveResult, "restored owner 执行 join accept 应返回成功");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "restored owner 审批通过后应将申请人加入项目");

        int staleDenyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-owner-op-stale-approve-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(0, staleDenyResult, "restored owner 清理待审批记录后 OP 的 join deny 应返回失败");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.already_member", "申请人已被加入项目后 OP 重复 join deny 应提示 already_member");
        assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(applicant.getStringUUID()), "OP 重复 join deny 失败后不应回滚申请人的成员状态");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.denied", "OP 重复 join deny 失败后不应再通知申请人 denied");
    }

/**
     * 校验 ownerUuid 修复后若 restored owner 先执行 join deny，在线 OP 随后重复 join accept 会命中 no_pending_request，而不会重新放行已清理的申请。
     */
    private static void shouldClearPendingJoinRequestForOperatorAfterRestoredOwnerDenialSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer offlineManager = createPlayer("00000000-0000-0000-0000-000000000513", "manager-join-invalid-owner-restored-owner-op-stale-deny-offline", false);
        TestServerPlayer firstManager = createPlayer("00000000-0000-0000-0000-000000000514", "manager-a-join-invalid-owner-restored-owner-op-stale-deny", false);
        TestServerPlayer restoredOwner = createPlayer("00000000-0000-0000-0000-000000000515", "manager-b-join-invalid-owner-restored-owner-op-stale-deny", false);
        TestServerPlayer operator = createPlayer("00000000-0000-0000-0000-000000000516", "op-join-invalid-owner-restored-owner-stale-deny", true);
        TestServerPlayer applicant = createPlayer("00000000-0000-0000-0000-000000000517", "applicant-join-invalid-owner-restored-owner-op-stale-deny", false);
        TestMinecraftServer server = createServer(firstManager, restoredOwner, operator, applicant);
        Project project = addTeamProject(offlineManager, "join-invalid-owner-restored-owner-op-stale-deny-project", "Join Invalid Owner Restored Owner Op Stale Deny Project");
        project.setOwnerUuid("not-a-valid-owner-uuid");
        project.addMember(firstManager.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, firstManager.getName().getString());
        project.addMember(restoredOwner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, restoredOwner.getName().getString());
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-op-stale-deny-project", createSource(0, applicant, server)), "ownerUuid 脏数据时首次 join project 应返回命令成功");
        assertEquals(1, dispatcher.execute("todo join deny join-invalid-owner-restored-owner-op-stale-deny-project " + applicant.getStringUUID(), createSource(0, firstManager, server)), "首次 join deny 应返回成功");

        firstManager.getClientMessages().clear();
        restoredOwner.getClientMessages().clear();
        operator.getClientMessages().clear();
        applicant.getClientMessages().clear();
        project.setOwnerUuid(restoredOwner.getStringUUID());

        assertEquals(1, dispatcher.execute("todo join project join-invalid-owner-restored-owner-op-stale-deny-project", createSource(0, applicant, server)), "ownerUuid 修复后重申请应返回命令成功");
        assertContainsMessageKey(restoredOwner.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后应优先通知 restored owner");
        assertNotContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.request_received", "ownerUuid 修复后 OP 不应收到自动 reviewer 通知");

        int denyResult = dispatcher.execute("todo join deny join-invalid-owner-restored-owner-op-stale-deny-project " + applicant.getStringUUID(), createSource(0, restoredOwner, server));
        assertEquals(1, denyResult, "restored owner 执行 join deny 应返回成功");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "restored owner 拒绝后不应将申请人加入项目");

        int staleApproveResult = dispatcher.execute("todo join accept join-invalid-owner-restored-owner-op-stale-deny-project " + applicant.getStringUUID(), createSource(2, operator, server));
        assertEquals(0, staleApproveResult, "restored owner 清理待审批记录后 OP 的 join accept 应返回失败");
        assertContainsMessageKey(operator.getClientMessages(), "message.todolist.project.join.no_pending_request", "申请已被 restored owner 清理后 OP 重复 join accept 应提示 no_pending_request");
        assertEquals(null, project.getMemberRole(applicant.getStringUUID()), "OP 重复 join accept 失败后不应把申请人重新加入项目");
        assertNotContainsMessageKey(applicant.getClientMessages(), "message.todolist.project.join.accepted", "OP 重复 join accept 失败后不应再通知申请人 accepted");
    }
}
