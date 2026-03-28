package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.todolist.TodoListCommon;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestMinecraftServer;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestServerPlayer;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.task.Task;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令系统任务集成测试入口，负责拆分执行 add、list、pagination、claim、assign、done、removep 等任务链路用例。
 */
public final class CommandBootstrapTaskIntegrationTestMain {

    /**
     * 团队任务集成测试方法名单，按当前约定顺序串行运行，保证回归语义不变。
     */
    private static final List<String> TASK_CASE_METHOD_NAMES = List.of(
            "shouldAddTaskToTeamProjectSuccessfully",
            "shouldRejectTeamTaskAddForMemberWhenMemberCreateDisabledSuccessfully",
            "shouldAllowTeamTaskAddForMemberWhenMemberCreateEnabledSuccessfully",
            "shouldListTasksByProjectSuccessfully",
            "shouldListCompletedTeamTasksSuccessfully",
            "shouldPaginateProjectTaskListWithMoreSuccessfully",
            "shouldKeepLatestTaskListSessionBetweenPersonalAndProjectQueriesSuccessfully",
            "shouldResetTaskPaginationAfterSwitchingQuerySuccessfully",
            "shouldSwitchTaskPaginationSessionBetweenTeamProjectsSuccessfully",
            "shouldCleanCompletedPersonalTasksInCurrentProjectSuccessfully",
            "shouldCleanCompletedTasksInCurrentTeamProjectSuccessfully",
            "shouldCleanCompletedTasksInAllTeamProjectsSuccessfully",
            "shouldCleanCompletedTasksInStarredTeamProjectsSuccessfully",
            "shouldRejectCleaningStarredTeamTasksWithForeignProjectSuccessfully",
            "shouldRejectTaskCleanConfirmAfterCurrentProjectChangesSuccessfully",
            "shouldRejectTaskCleanWhenNothingMatchesSuccessfully",
            "shouldCleanIncompletePersonalTasksInCurrentProjectSuccessfully",
            "shouldRejectTaskCleanForInvalidScopeSuccessfully",
            "shouldRejectTaskCleanForInvalidProjectSelectorSuccessfully",
            "shouldRejectTaskCleanForInvalidStatusSuccessfully",
            "shouldRejectTaskMoreWithoutListSession",
            "shouldRejectTaskPrevOnFirstPage",
            "shouldRejectTaskMoreAfterLastPage",
            "shouldRejectMissingTeamTaskWhenClaiming",
            "shouldRejectClaimingAssignedTeamTaskWithoutPermission",
            "shouldRejectAbandoningOthersTaskWithoutPermission",
            "shouldRejectInvalidAssignmentTargetByProject",
            "shouldRejectClaimingAssignedTeamTaskAfterManagerAssignmentSuccessfully",
            "shouldAllowManagerAndLeadClaimingUnassignedTeamTasksSuccessfully",
            "shouldClaimUnassignedTeamTaskSuccessfully",
            "shouldAbandonSelfAssignedTeamTaskSuccessfully",
            "shouldAssignTeamTaskToMemberSuccessfully",
            "shouldAllowLeadAssigningTeamTaskSuccessfully",
            "shouldReassignTeamTaskToAnotherMemberSuccessfully",
            "shouldKeepTaskStateConsistentWhenManagerReassignsClaimedTaskSuccessfully",
            "shouldKeepIndependentTeamTaskStateAcrossMultipleMemberOperationsSuccessfully",
            "shouldRejectAssigningCompletedTeamTaskSuccessfully",
            "shouldRejectClaimingCompletedTeamTaskSuccessfully",
            "shouldRejectLeadAssigningAndClaimingCompletedTeamTaskSuccessfully",
            "shouldRejectCompletingUnassignedTeamTaskForRegularMemberSuccessfully",
            "shouldRejectCompletingTaskAssignedToOtherMemberSuccessfully",
            "shouldAllowLeadCompletingAssignedTeamTaskSuccessfully",
            "shouldReturnAlreadyCompletedForCompletedTeamTaskSuccessfully",
            "shouldRejectAbandoningCompletedAssignedTeamTaskSuccessfully",
            "shouldCompleteAssignedTeamTaskSuccessfully",
            "shouldRejectRemovingTeamTaskForMemberSuccessfully",
            "shouldAllowLeadRemovingTeamTaskSuccessfully",
            "shouldAllowLeadRemovingTaskAssignedToProjectManagerSuccessfully",
            "shouldRemoveCompletedAssignedTeamTaskByManagerSuccessfully",
            "shouldRemoveTeamTasksAcrossAssignedAndCompletedMatrixSuccessfully",
            "shouldRemoveTeamTaskSuccessfully",
            "shouldIsolatePendingTaskCleanConfirmationsBetweenPlayersSuccessfully",
            "shouldListPersonalTasksAfterReloadSuccessfully",
            "shouldListTeamTasksAfterReloadSuccessfully"
    );

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandBootstrapTaskIntegrationTestMain() {
    }

    /**
     * 程序入口，初始化命令测试环境后，顺序执行拆分出的团队任务相关用例。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当任一任务用例失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        invokeBootstrapEnvironment();
        for (String methodName : TASK_CASE_METHOD_NAMES) {
            runTaskCase(methodName);
        }
    }

    /**
     * 复用原有命令集成测试环境初始化逻辑，保证拆分后的任务用例仍运行在同一套基建上。
     */
    private static void invokeBootstrapEnvironment() {
        CommandBootstrapIntegrationTestMain.bootstrapEnvironment();
    }

    /**
     * 执行单个团队任务集成测试用例，并沿用现有的测试日志格式输出结果。
     *
     * @param methodName 待执行的用例方法名
     * @throws Exception 用例执行失败时向上抛出异常
     */
    private static void runTaskCase(String methodName) throws Exception {
        CommandTestSupport.runTestCase(
                CommandBootstrapTaskIntegrationTestMain.class.getSimpleName() + "." + methodName,
                () -> invokeTaskCaseMethod(methodName)
        );
    }

    /**
     * 优先调用拆分后的本地任务用例；若尚未迁移，则回退到原测试类中的旧实现。
     *
     * @param methodName 待执行的用例方法名
     * @throws Exception 目标方法执行失败时向上抛出异常
     */
    private static void invokeTaskCaseMethod(String methodName) throws Exception {
        Method localMethod = findLocalTaskCaseMethod(methodName);
        if (localMethod != null) {
            invokeReflectedMethod(localMethod, methodName);
            return;
        }
        Method legacyMethod = CommandBootstrapIntegrationTestMain.class.getDeclaredMethod(methodName);
        invokeReflectedMethod(legacyMethod, methodName);
    }

    /**
     * 在当前拆分文件中查找已迁移的任务用例方法。
     *
     * @param methodName 待查找的方法名
     * @return 找到时返回方法对象，否则返回空
     */
    private static Method findLocalTaskCaseMethod(String methodName) {
        try {
            Method method = CommandBootstrapTaskIntegrationTestMain.class.getDeclaredMethod(methodName);
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
            throw new IllegalStateException("执行团队任务集成测试方法失败: " + methodName, cause);
        } catch (IllegalAccessException illegalAccessException) {
            throw new IllegalStateException("无法访问团队任务集成测试方法: " + methodName, illegalAccessException);
        }
    }

    /**
     * 重置命令测试全局状态，保证每个任务用例相互隔离。
     *
     * @param accessMode 命令访问模式
     */
    private static void resetState(ModConfig.CommandAccessMode accessMode) {
        CommandBootstrapIntegrationTestMain.resetState(accessMode);
    }

    /**
     * 创建任务测试使用的命令分发器。
     *
     * @return 已注册命令树的分发器
     */
    private static CommandDispatcher<CommandSourceStack> createDispatcher() {
        return CommandBootstrapIntegrationTestMain.createDispatcher();
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
     * 创建团队项目。
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
     * @param tasks 需要保存的团队任务
     * @throws Exception 保存失败时抛出异常
     */
    private static void saveTeamTasks(Task... tasks) throws Exception {
        CommandBootstrapIntegrationTestMain.saveTeamTasks(tasks);
    }

    /**
     * 保存个人任务。
     *
     * @param player 任务所属玩家
     * @param tasks 需要保存的个人任务
     * @throws Exception 保存失败时抛出异常
     */
    private static void savePersonalTasks(TestServerPlayer player, Task... tasks) throws Exception {
        CommandBootstrapIntegrationTestMain.savePersonalTasks(player, tasks);
    }

    /**
     * 立即刷新项目与任务相关的防抖保存。
     *
     * @param server 当前测试服务器
     */
    private static void flushProjectSaves(MinecraftServer server) {
        CommandBootstrapIntegrationTestMain.flushProjectSaves(server);
    }

    /**
     * 重新加载持久化状态，模拟测试中的重启效果。
     */
    private static void reloadPersistentState() {
        CommandBootstrapIntegrationTestMain.reloadPersistentState();
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
     * 批量创建用于分页测试的个人任务。
     *
     * @param titlePrefix 标题前缀
     * @param count 数量
     * @param projectId 所属项目 ID
     * @return 构造好的任务数组
     * @throws Exception 创建失败时抛出异常
     */
    private static Task[] createPagedPersonalTasks(String titlePrefix, int count, String projectId) throws Exception {
        return CommandBootstrapIntegrationTestMain.createPagedPersonalTasks(titlePrefix, count, projectId);
    }

    /**
     * 读取指定团队任务。
     *
     * @param taskId 任务 ID
     * @return 命中的团队任务
     * @throws Exception 读取失败时抛出异常
     */
    private static Task loadTeamTaskById(String taskId) throws Exception {
        return CommandBootstrapIntegrationTestMain.loadTeamTaskById(taskId);
    }

    /**
     * 按标题读取团队任务。
     *
     * @param title 任务标题
     * @return 命中的任务
     * @throws Exception 读取失败时抛出异常
     */
    private static Task loadTeamTaskByTitle(String title) throws Exception {
        return CommandBootstrapIntegrationTestMain.loadTeamTaskByTitle(title);
    }

    /**
     * 读取指定玩家的个人任务列表。
     *
     * @param player 目标玩家
     * @return 个人任务列表
     * @throws Exception 读取失败时抛出异常
     */
    private static List<Task> loadPersonalTasks(TestServerPlayer player) throws Exception {
        return CommandBootstrapIntegrationTestMain.loadPersonalTasks(player);
    }

    /**
     * 按标题查询玩家个人任务，找不到时返回 null。
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
     * 按标题查询玩家个人任务，要求必须存在。
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
     * 按名称查找项目。
     *
     * @param projectName 项目名称
     * @return 命中的项目；不存在时返回 null
     */
    private static Project findProjectByName(String projectName) {
        return CommandBootstrapIntegrationTestMain.findProjectByName(projectName);
    }

    /**
     * 获取个人任务持久化文件路径。
     *
     * @param player 目标玩家
     * @return 个人任务文件路径
     */
    private static Path getPersonalTaskFilePath(TestServerPlayer player) {
        return CommandBootstrapIntegrationTestMain.getPersonalTaskFilePath(player);
    }

    /**
     * 获取团队任务持久化文件路径。
     *
     * @return 团队任务文件路径
     */
    private static Path getTeamTaskFilePath() {
        return CommandBootstrapIntegrationTestMain.getTeamTaskFilePath();
    }

    /**
     * 获取团队项目持久化文件路径。
     *
     * @return 团队项目文件路径
     */
    private static Path getTeamProjectsFilePath() {
        return CommandBootstrapIntegrationTestMain.getTeamProjectsFilePath();
    }

    /**
     * 断言消息列表中包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 目标翻译键
     * @param message 断言失败提示
     */
    private static void assertContainsMessageKey(List<net.minecraft.network.chat.Component> messages, String translationKey, String message) {
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(messages, translationKey, message);
    }

    /**
     * 断言消息列表包含指定文本。
     *
     * @param messages 消息列表
     * @param expectedText 期望文本
     * @param message 失败提示
     */
    private static void assertContainsText(List<net.minecraft.network.chat.Component> messages, String expectedText, String message) {
        CommandBootstrapIntegrationTestMain.assertContainsText(messages, expectedText, message);
    }

    /**
     * 断言消息列表不包含指定文本。
     *
     * @param messages 消息列表
     * @param unexpectedText 非期望文本
     * @param message 失败提示
     */
    private static void assertNotContainsText(List<net.minecraft.network.chat.Component> messages, String unexpectedText, String message) {
        CommandBootstrapIntegrationTestMain.assertNotContainsText(messages, unexpectedText, message);
    }

    /**
     * 统计消息列表中包含指定文本的条数。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本
     * @return 命中的消息条数
     */
    private static int countMessagesContaining(List<net.minecraft.network.chat.Component> messages, String expectedText) {
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
     * @param value 实际值
     * @param message 失败提示
     */
    private static void assertNull(Object value, String message) {
        CommandBootstrapIntegrationTestMain.assertNull(value, message);
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
     * 按标题查询团队任务，便于断言删除后的持久化状态。
     *
     * @param title 任务标题
     * @return 命中的任务；若不存在则返回 null
     * @throws Exception 读取任务失败时抛出异常
     */
    private static Task findTeamTaskByTitleOrNull(String title) throws Exception {
        return CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull(title);
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

    /**
     * 校验未先执行 task list/listp 时，task more 会返回无会话错误。
     */
    /**
     * 验证 task clean confirm 在当前项目变化后会返回已失效错误。
     */
    private static void shouldRejectTaskCleanConfirmAfterCurrentProjectChangesSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000408", "clean-confirm-invalidated-user", false);
        addOwnedPersonalProject(player, "clean-invalidated-project-a", "Clean Invalidated Project A");
        addOwnedPersonalProject(player, "clean-invalidated-project-b", "Clean Invalidated Project B");
        ProjectPackets.setActiveProjectId(player, "clean-invalidated-project-a");
        savePersonalTasks(
                player,
                createPersonalTask("Completed in invalidated current", true, Task.Priority.MEDIUM, "clean-invalidated-project-a"),
                createPersonalTask("Completed in switched current", true, Task.Priority.MEDIUM, "clean-invalidated-project-b")
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int requestResult = dispatcher.execute("todo task clean personal current completed", createSource(0, player));
        assertEquals(1, requestResult, "task clean 请求确认阶段应返回成功");

        ProjectPackets.setActiveProjectId(player, "clean-invalidated-project-b");
        CapturingCommandSourceStack confirmSource = createSource(0, player);
        int confirmResult = dispatcher.execute("todo task clean confirm", confirmSource);
        assertEquals(0, confirmResult, "当前项目变化后 task clean confirm 应返回失败");
        assertContainsMessageKey(confirmSource.getFailureMessages(), "command.todolist.task.clean.confirm_invalidated", "task clean confirm 失效错误键不正确");
        assertNotNull(findPersonalTaskByTitleOrNull(player, "Completed in invalidated current"), "已失效的 task clean confirm 不应误删原项目任务");
    }

    /**
     * 验证 task clean 在没有任何命中任务时会返回 nothing_to_clean。
     */
    private static void shouldRejectTaskCleanWhenNothingMatchesSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000409", "clean-nothing-user", false);
        addOwnedPersonalProject(player, "clean-nothing-project", "Clean Nothing Project");
        ProjectPackets.setActiveProjectId(player, "clean-nothing-project");
        savePersonalTasks(
                player,
                createPersonalTask("Open task only", false, Task.Priority.MEDIUM, "clean-nothing-project")
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task clean personal current completed", source);
        assertEquals(0, result, "没有命中任务时 task clean 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.clean.nothing_to_clean", "task clean 空结果错误键不正确");
        assertNotNull(findPersonalTaskByTitleOrNull(player, "Open task only"), "nothing_to_clean 场景不应修改原任务");
    }

    /**
     * 验证 task clean 可以按 incomplete 清理当前个人项目中的未完成任务。
     */
    private static void shouldCleanIncompletePersonalTasksInCurrentProjectSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000416", "clean-incomplete-user", false);
        addOwnedPersonalProject(player, "clean-incomplete-project", "Clean Incomplete Project");
        addOwnedPersonalProject(player, "clean-incomplete-keep-project", "Clean Incomplete Keep Project");
        ProjectPackets.setActiveProjectId(player, "clean-incomplete-project");
        savePersonalTasks(
                player,
                createPersonalTask("Incomplete in current", false, Task.Priority.MEDIUM, "clean-incomplete-project"),
                createPersonalTask("Completed in current", true, Task.Priority.MEDIUM, "clean-incomplete-project"),
                createPersonalTask("Incomplete in other project", false, Task.Priority.MEDIUM, "clean-incomplete-keep-project")
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int requestResult = dispatcher.execute("todo task clean personal current incomplete", createSource(0, player));
        assertEquals(1, requestResult, "incomplete task clean 请求确认阶段应返回成功");
        int confirmResult = dispatcher.execute("todo task clean confirm", createSource(0, player));
        assertEquals(1, confirmResult, "incomplete task clean confirm 应返回成功");

        assertNull(findPersonalTaskByTitleOrNull(player, "Incomplete in current"), "task clean incomplete 应删除当前项目中的未完成任务");
        assertNotNull(findPersonalTaskByTitleOrNull(player, "Completed in current"), "task clean incomplete 不应删除当前项目中的已完成任务");
        assertNotNull(findPersonalTaskByTitleOrNull(player, "Incomplete in other project"), "task clean incomplete 不应删除其他项目任务");
    }

    /**
     * 验证 task clean 在 scope 非法时会返回 invalid_scope。
     */
    private static void shouldRejectTaskCleanForInvalidScopeSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000417", "clean-invalid-scope-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task clean invalid-scope current completed", source);
        assertEquals(0, result, "非法 scope 的 task clean 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.clean.invalid_scope", "task clean 非法 scope 错误键不正确");
    }

    /**
     * 验证 task clean 在项目选择器非法时会返回 invalid_project_selector。
     */
    private static void shouldRejectTaskCleanForInvalidProjectSelectorSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000418", "clean-invalid-project-selector-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task clean personal invalid-selector completed", source);
        assertEquals(0, result, "非法项目选择器的 task clean 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.clean.invalid_project_selector", "task clean 非法项目选择器错误键不正确");
    }

    /**
     * 验证 task clean 在状态值非法时会返回 invalid_status。
     */
    private static void shouldRejectTaskCleanForInvalidStatusSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000419", "clean-invalid-status-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task clean personal current invalid-status", source);
        assertEquals(0, result, "非法状态的 task clean 应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.clean.invalid_status", "task clean 非法状态错误键不正确");
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
     * 校验 claimp 会拒绝不存在的团队任务。
     */
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

    /**
     * 校验项目经理可以把同一条团队任务从一个成员重新指派给另一个成员。
     */
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

    /**
     * 校验普通成员不能直接完成未分配给自己的团队任务。
     */
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

    /**
     * 校验 lead 角色可以完成已分配的团队任务。
     */
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

    /**
     * 校验已完成的团队任务再次执行 donep 时会返回已完成提示，并保持原状态不变。
     */
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

    /**
     * 校验已分配的团队任务可以被 assignee 正常完成。
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

    /**
     * 校验项目经理仍可删除已完成且已指派的团队任务。
     */
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
        assertEquals(1, result, "项目经理删除已完成且已指派任务时应返回成功");
        assertNull(findTeamTaskByTitleOrNull("Completed Assigned Removable Task"), "removep 后不应再保留已删除的团队任务");
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

        assertEquals(1, dispatcher.execute("todo task clean personal all completed", createSource(0, firstPlayer, server)), "鐜╁ A 鍙戣捣 task clean 搴旇繑鍥炴垚鍔?");
        assertEquals(1, dispatcher.execute("todo task clean personal all completed", createSource(0, secondPlayer, server)), "鐜╁ B 鍙戣捣 task clean 搴旇繑鍥炴垚鍔?");

        assertEquals(1, dispatcher.execute("todo task clean confirm", createSource(0, firstPlayer, server)), "鐜╁ A 纭娓呯悊搴旇繑鍥炴垚鍔?");
        assertNull(findPersonalTaskByTitleOrNull(firstPlayer, "First Completed Task"), "鐜╁ A 纭鍚庡簲娓呯悊鑷繁鐨勫凡瀹屾垚浠诲姟");
        assertNotNull(findPersonalTaskByTitleOrNull(firstPlayer, "First Open Task"), "鐜╁ A 纭鍚庝笉搴旀竻鐞嗚嚜宸辩殑鏈畬鎴愪换鍔?");
        assertNotNull(findPersonalTaskByTitleOrNull(secondPlayer, "Second Completed Task"), "鐜╁ A 纭鍚庝笉搴旀竻鐞嗙帺瀹?B 鐨勫凡瀹屾垚浠诲姟");

        assertEquals(1, dispatcher.execute("todo task clean confirm", createSource(0, secondPlayer, server)), "鐜╁ B 纭娓呯悊搴旇繑鍥炴垚鍔?");
        assertNull(findPersonalTaskByTitleOrNull(secondPlayer, "Second Completed Task"), "鐜╁ B 纭鍚庡簲娓呯悊鑷繁鐨勫凡瀹屾垚浠诲姟");
        assertNotNull(findPersonalTaskByTitleOrNull(secondPlayer, "Second Open Task"), "鐜╁ B 纭鍚庝笉搴旀竻鐞嗚嚜宸辩殑鏈畬鎴愪换鍔?");
    }

    /**
     * 验证重载后仍可列出已持久化的个人任务。
     */
    private static void shouldListPersonalTasksAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000143", "reload-personal-task-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int addResult = dispatcher.execute("todo task add \"Reload Personal Task\" \"Persist me\" persist", createSource(0, player));
        assertEquals(1, addResult, "task add 鎼存棁绻戦崶鐐村灇閸?");
        assertEquals(Boolean.TRUE, Files.exists(getPersonalTaskFilePath(player)), "task add 閸氬骸绨查崘娆忓弳娑擃亙姹夋禒璇插閺傚洣娆?");

        reloadPersistentState();

        assertNotNull(findPersonalTaskByTitle(player, "Reload Personal Task"), "闁插秵鏌婇崚婵嗩潗閸栨牕鎮楁惔鏃囶嚉閼虫垝绮犻幐浣风畽閸栨牗鏋冩禒鏈佃厬鐠囪娲栨稉顏冩眽娴犺濮?");
        CapturingCommandSourceStack source = createSource(0, player);
        int listResult = createDispatcher().execute("todo task list", source);
        assertEquals(1, listResult, "闁插秷娴囬崥?task list 鎼存棁绻戦崶鐐村灇閸?");
        assertContainsText(source.getSuccessMessages(), "Reload Personal Task", "闁插秷娴囬崥?task list 閺堫亣绶崙鍝勫嚒閹镐椒绠欓崠鏍畱娑擃亙姹夋禒璇插");
    }

    /**
     * 验证重载后仍可通过 task listp 列出已持久化的团队任务。
     */
    private static void shouldListTeamTasksAfterReloadSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = createPlayer("00000000-0000-0000-0000-000000000144", "reload-team-task-user", false);
        TestMinecraftServer server = createServer(manager);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int createResult = dispatcher.execute("todo project create team Reload Team Project", createSource(0, manager, server));
        assertEquals(1, createResult, "project create team 鎼存棁绻戦崶鐐村灇閸?");
        Project createdProject = findProjectByName("Reload Team Project");
        assertNotNull(createdProject, "鎼存棁顕氶懗鑺ュ閸掓澘鍨伴崚娑樼紦閻ㄥ嫬娲熼梼鐔笺€嶉惄?");
        flushProjectSaves(server);
        assertEquals(Boolean.TRUE, Files.exists(getTeamProjectsFilePath()), "project create team 閸氬骸绨查崘娆忓弳閸ャ垽妲︽い鍦窗閺傚洣娆?");

        int addResult = dispatcher.execute("todo task addp " + createdProject.getId() + " \"Reload Team Task\" \"Persist team\" persist", createSource(0, manager, server));
        assertEquals(1, addResult, "task addp 鎼存棁绻戦崶鐐村灇閸?");
        assertEquals(Boolean.TRUE, Files.exists(getTeamTaskFilePath()), "task addp 閸氬骸绨查崘娆忓弳閸ァ垽妲︽禒璇插閺傚洣娆?");

        reloadPersistentState();

        CapturingCommandSourceStack source = createSource(0, manager, createServer(manager));
        int listResult = createDispatcher().execute("todo task listp " + createdProject.getId(), source);
        assertEquals(1, listResult, "闁插秷娴囬崥?task listp 鎼存棁绻戦崶鐐村灇閸?");
        assertContainsText(source.getSuccessMessages(), "Reload Team Task", "闁插秷娴囬崥?task listp 閺堫亣绶崙鍝勫嚒閹镐椒绠欓崠鏍畱閸ァ垽妲︽禒璇插");
    }
}
