package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.gui.testsupport.FakeClientConnection;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.task.Task;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.server.IntegratedServer;

import java.util.List;
import java.util.UUID;

/**
 * TodoScreen GUI 离线自测入口。
 * 负责覆盖项目切换、任务编辑、保存语义以及 LAN/单人切换等关键回归场景。
 */
public final class TodoScreenTestMain {
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenTestMain() {
    }

    /**
     * 串行执行 TodoScreen 的所有 GUI 回归测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldInitializeWithDefaultPersonalProject", TodoScreenTestMain::shouldInitializeWithDefaultPersonalProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSwitchProjectToTeamScopeAndSyncActiveProject", TodoScreenTestMain::shouldSwitchProjectToTeamScopeAndSyncActiveProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldHandleProjectLifecycleChanges", TodoScreenTestMain::shouldHandleProjectLifecycleChanges);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldEditSelectedTaskAndMarkUnsaved", TodoScreenTestMain::shouldEditSelectedTaskAndMarkUnsaved);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldFilterTasksBySearchAndStatus", TodoScreenTestMain::shouldFilterTasksBySearchAndStatus);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSavePersonalTasksAndClearUnsavedState", TodoScreenTestMain::shouldSavePersonalTasksAndClearUnsavedState);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepPersonalTasksAfterSavingInPublishedLocalWorld", TodoScreenTestMain::shouldKeepPersonalTasksAfterSavingInPublishedLocalWorld);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepPersonalTasksAfterPublishedLocalWorldReentryFlow", TodoScreenTestMain::shouldKeepPersonalTasksAfterPublishedLocalWorldReentryFlow);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSavePersonalAndTeamTasksWhenSavingFromTeamViewOnRemoteServer", TodoScreenTestMain::shouldSavePersonalAndTeamTasksWhenSavingFromTeamViewOnRemoteServer);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepPersonalAndTeamTasksAfterRemoteReconnectWhenSavingFromPersonalView", TodoScreenTestMain::shouldKeepPersonalAndTeamTasksAfterRemoteReconnectWhenSavingFromPersonalView);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldDiscardUnsavedPersonalChangesOnClose", TodoScreenTestMain::shouldDiscardUnsavedPersonalChangesOnClose);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldRequestTeamSyncWhenClosingUnsavedTeamChanges", TodoScreenTestMain::shouldRequestTeamSyncWhenClosingUnsavedTeamChanges);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowNotificationWhenAddingWithoutProject", TodoScreenTestMain::shouldShowNotificationWhenAddingWithoutProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldOpenContextMenuAndApplyPriorityAction", TodoScreenTestMain::shouldOpenContextMenuAndApplyPriorityAction);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSearchAndAssignPlayerFromAssignScreen", TodoScreenTestMain::shouldSearchAndAssignPlayerFromAssignScreen);
    }

    /**
     * 验证界面初始化后会默认落在个人项目，并同步当前激活项目。
     */
    private static void shouldInitializeWithDefaultPersonalProject() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertEquals(personalProject.getId(), screen.getCurrentProjectForTest().getId(), "初始化后应选中默认个人项目");
        GuiTestSupport.assertEquals("PERSONAL", screen.getViewModeNameForTest(), "默认项目初始化后应保持个人视图");
        GuiTestSupport.assertEquals(personalProject.getId(), ops.getActiveProjectId(), "初始化后应记录当前激活项目");
        GuiTestSupport.assertEquals(personalProject.getId(), ops.getActiveProjectSyncCalls().get(0), "初始化后应向桥接层同步当前激活项目");
    }

    /**
     * 验证切换到团队项目后会更新当前项目与视图，并同步激活项目。
     */
    private static void shouldSwitchProjectToTeamScopeAndSyncActiveProject() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);

        GuiTestSupport.assertEquals(teamProject.getId(), screen.getCurrentProjectForTest().getId(), "切换后应选中团队项目");
        GuiTestSupport.assertEquals("TEAM_UNASSIGNED", screen.getViewModeNameForTest(), "切换到团队项目后应进入团队未分配视图");
        GuiTestSupport.assertEquals(teamProject.getId(), ops.getActiveProjectId(), "切换后应更新激活项目");
        GuiTestSupport.assertEquals(teamProject.getId(), ops.getActiveProjectSyncCalls().get(ops.getActiveProjectSyncCalls().size() - 1), "切换后应同步新的激活项目");
    }

    /**
     * 验证项目更新、删除与清空后，界面会自动回退到合适的项目与视图。
     */
    private static void shouldHandleProjectLifecycleChanges() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);

        Project updatedTeamProject = createTeamProject("team-dev", "Dev Team Updated");
        TodoListCommon.getProjectManager().updateProject(updatedTeamProject);
        GuiTestSupport.assertEquals(updatedTeamProject, screen.getCurrentProjectForTest(), "收到 UPDATED 事件后应替换为最新项目对象");

        TodoListCommon.getProjectManager().deleteProject(updatedTeamProject.getId());
        GuiTestSupport.assertEquals(defaultTeamProject.getId(), screen.getCurrentProjectForTest().getId(), "删除当前团队项目后应回退到默认团队项目");

        TodoListCommon.getProjectManager().clearAll();
        GuiTestSupport.assertNull(screen.getCurrentProjectForTest(), "收到 CLEARED 事件后应清空当前项目");
        GuiTestSupport.assertEquals("PERSONAL", screen.getViewModeNameForTest(), "项目清空后应回退到个人视图");
        GuiTestSupport.assertNull(ops.getActiveProjectId(), "项目清空后桥接层中的激活项目也应为空");

        Project personalProject = createDefaultPersonalProject();
        TodoListCommon.getProjectManager().addProject(personalProject);
        GuiTestSupport.assertEquals(personalProject.getId(), screen.getCurrentProjectForTest().getId(), "重新添加默认个人项目后应自动恢复选中");
    }

    /**
     * 验证编辑已选中的任务后，会即时更新任务内容并标记未保存状态。
     */
    private static void shouldEditSelectedTaskAndMarkUnsaved() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        Task task = screen.getFilteredTasksForTest().get(0);

        screen.selectTaskForTest(task);
        ScreenDriver.setText(screen.getTitleFieldForTest(), "Alpha Updated");
        ScreenDriver.setText(screen.getTagFieldForTest(), "red,urgent");

        GuiTestSupport.assertEquals("Alpha Updated", task.getTitle(), "编辑任务标题后应更新当前任务");
        GuiTestSupport.assertEquals(2, task.getTags().size(), "编辑标签后应拆分为两个标签");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "编辑任务后应标记存在未保存改动");
        GuiTestSupport.assertTrue(TodoScreen.hasPersonalUnsavedChanges(), "编辑个人任务后应同步个人未保存标记");
    }

    /**
     * 验证搜索与状态筛选可以共同作用，并返回预期任务。
     */
    private static void shouldFilterTasksBySearchAndStatus() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        addTaskViaInput(screen, "Beta");

        ScreenDriver.setText(screen.getSearchFieldForTest(), "be");
        GuiTestSupport.assertEquals(1, screen.getFilteredTasksForTest().size(), "搜索后应只保留匹配关键字的任务");
        GuiTestSupport.assertEquals("Beta", screen.getFilteredTasksForTest().get(0).getTitle(), "搜索后应返回匹配标题的任务");

        ScreenDriver.setText(screen.getSearchFieldForTest(), "");
        Task alpha = screen.getFilteredTasksForTest().stream()
                .filter(task -> "Alpha".equals(task.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应能找到 Alpha 任务"));
        alpha.setCompleted(true);

        ScreenDriver.click(screen.getFilterStatusButtonForTest());

        GuiTestSupport.assertEquals("completed", screen.getCurrentFilterForTest(), "点击状态筛选按钮后应切换到已完成筛选");
        GuiTestSupport.assertEquals(1, screen.getFilteredTasksForTest().size(), "已完成筛选后只应保留完成任务");
        GuiTestSupport.assertEquals("Alpha", screen.getFilteredTasksForTest().get(0).getTitle(), "已完成筛选后应返回 Alpha 任务");
    }

    /**
     * 验证保存个人任务后，会发送整表替换、清除未保存状态并返回父界面。
     */
    private static void shouldSavePersonalTasksAndClearUnsavedState() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        screen.saveTasksForTest();

        GuiTestSupport.assertEquals(1, ops.getReplaceAllTaskCalls().size(), "保存个人任务后应向桥接层发送整表替换");
        GuiTestSupport.assertFalse(screen.hasUnsavedChangesForTest(), "保存后应清除未保存状态");
        GuiTestSupport.assertEquals(1, minecraft.getTestPlayerMessages().size(), "保存成功后应给玩家发送一条提示消息");
        GuiTestSupport.assertNotNull(minecraft.getLastScreen(), "保存后应返回父界面");
    }

    /**
     * 验证关闭界面时会丢弃未保存的个人任务改动。
     */
    private static void shouldDiscardUnsavedPersonalChangesOnClose() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        GuiTestSupport.assertEquals(1, screen.getCurrentManagerTasksForTest().size(), "关闭前当前任务管理器中应存在未保存任务");

        screen.onClose();

        GuiTestSupport.assertEquals(0, screen.getCurrentManagerTasksForTest().size(), "关闭后未保存的个人任务应被丢弃");
        GuiTestSupport.assertEquals(0, ops.getRequestTeamSyncCallCount(), "关闭个人任务界面时不应请求团队同步");
        GuiTestSupport.assertFalse(TodoScreen.hasPersonalUnsavedChanges(), "关闭后应清除个人未保存标记");
    }

    /**
     * 验证关闭存在未保存改动的团队视图时，会请求团队数据同步。
     */
    private static void shouldRequestTeamSyncWhenClosingUnsavedTeamChanges() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Team Task");

        screen.onClose();

        GuiTestSupport.assertEquals(1, ops.getRequestTeamSyncCallCount(), "关闭未保存的团队视图时应请求团队同步");
        GuiTestSupport.assertFalse(screen.hasUnsavedChangesForTest(), "关闭后应清除团队视图的未保存状态");
    }

    /**
     * 验证未选中项目时新增任务，会显示提示且不会实际创建任务。
     */
    private static void shouldShowNotificationWhenAddingWithoutProject() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(null);
        ScreenDriver.setText(screen.getTitleFieldForTest(), "Orphan Task");
        addTaskViaEnter(screen);

        GuiTestSupport.assertEquals(1, screen.getNotificationCountForTest(), "未选择项目时新增任务应显示提示");
        GuiTestSupport.assertEquals(0, screen.getCurrentManagerTasksForTest().size(), "未选择项目时不应真正新增任务");
    }

    /**
     * 验证通过上下文菜单修改优先级后，会更新任务、关闭菜单并标记未保存。
     */
    private static void shouldOpenContextMenuAndApplyPriorityAction() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        Task task = screen.getFilteredTasksForTest().get(0);

        screen.selectTaskForTest(task);
        screen.openTaskContextMenuForTest(task);
        GuiTestSupport.assertTrue(screen.hasContextMenuForTest(), "打开上下文菜单后应处于菜单打开状态");
        GuiTestSupport.assertEquals(4, screen.getContextMenuItemTextsForTest().size(), "上下文菜单应包含四个操作项");

        screen.clickContextMenuItemForTest(0);

        GuiTestSupport.assertEquals(Task.Priority.HIGH, task.getPriority(), "通过上下文菜单修改后任务优先级应更新");
        GuiTestSupport.assertEquals(1, ops.getUpdateTaskCalls().size(), "通过上下文菜单修改优先级后应发送更新请求");
        GuiTestSupport.assertFalse(screen.hasContextMenuForTest(), "执行上下文菜单操作后应关闭菜单");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "通过上下文菜单修改任务后应标记未保存状态");
    }

    /**
     * 验证在分配玩家弹窗中搜索并选择成员后，会更新任务分配并返回主界面。
     */
    private static void shouldSearchAndAssignPlayerFromAssignScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(UUID.fromString("20000000-0000-0000-0000-000000000002"), "alice"),
                createPlayerInfo(UUID.fromString("20000000-0000-0000-0000-000000000003"), "bob"));
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Team Task");
        Task task = screen.getFilteredTasksForTest().get(0);

        Screen assignScreen = screen.createAssignPlayerScreenForTest(task);
        ScreenDriver.init(minecraft, assignScreen);
        screen.setAssignPlayerSearchForTest(assignScreen, "bo");
        GuiTestSupport.assertEquals(List.of("bob"), screen.getAssignablePlayerNamesForTest(assignScreen), "搜索分配玩家时应只保留匹配结果");

        screen.clickAssignPlayerRowForTest(assignScreen, 0);

        GuiTestSupport.assertEquals("bob", task.getAssigneeName(), "点击玩家后应把任务分配给对应成员");
        GuiTestSupport.assertEquals("20000000-0000-0000-0000-000000000003", task.getAssigneeUuid(), "分配后应写入对应玩家 UUID");
        GuiTestSupport.assertEquals(1, screen.getNotificationCountForTest(), "分配任务后应显示成功提示");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "分配任务后应标记未保存状态");
        GuiTestSupport.assertEquals(screen, minecraft.getLastScreen(), "完成分配后应返回主界面");
    }

    /**
     * 创建默认个人项目并加入项目管理器。
     *
     * @return 默认个人项目
     */
    private static Project createDefaultPersonalProject() {
        Project project = new Project(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_KEY, Project.Scope.PERSONAL, OWNER_ID.toString());
        project.setId(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建默认团队项目并加入项目管理器。
     *
     * @return 默认团队项目
     */
    private static Project createDefaultTeamProject() {
        Project project = new Project(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_KEY, Project.Scope.TEAM, OWNER_ID.toString());
        project.setId(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建一个团队项目并加入项目管理器。
     *
     * @param id 项目 ID
     * @param name 项目名称
     * @return 创建后的团队项目
     */
    private static Project createTeamProject(String id, String name) {
        Project project = new Project(name, Project.Scope.TEAM, OWNER_ID.toString());
        project.setId(id);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 通过标题输入框新增一条任务。
     *
     * @param screen 待操作界面
     * @param title 任务标题
     */
    private static void addTaskViaInput(TodoScreen screen, String title) {
        ScreenDriver.setText(screen.getTitleFieldForTest(), title);
        addTaskViaEnter(screen);
    }

    /**
     * 通过回车键触发新增任务流程。
     *
     * @param screen 待操作界面
     */
    private static void addTaskViaEnter(TodoScreen screen) {
        screen.getTitleFieldForTest().setFocused(true);
        ScreenDriver.pressEnter(screen);
    }

    /**
     * 为测试客户端安装在线玩家列表。
     *
     * @param minecraft 测试客户端
     * @param players 在线玩家列表
     */
    private static void installOnlinePlayers(FakeMinecraftClient minecraft, PlayerInfo... players) {
        FakeClientConnection connection = (FakeClientConnection) minecraft.getConnection();
        connection.setOnlinePlayers(List.of(players));
    }

    /**
     * 创建一条测试用在线玩家信息。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @return 测试玩家信息对象
     */
    private static PlayerInfo createPlayerInfo(UUID uuid, String name) {
        return GuiTestSupport.createPlayerInfo(uuid, name);
    }

    /**
     * 验证 LAN 主机保存个人任务后，重新进入单人世界仍能看到刚才保存的任务。
     */
    private static void shouldKeepPersonalTasksAfterSavingInPublishedLocalWorld() {
        GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient publishedMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        publishedMinecraft.setLocalServer(true);
        publishedMinecraft.setIntegratedServer(createPublishedIntegratedServer());
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen publishedScreen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(publishedMinecraft, publishedScreen);
        addTaskViaInput(publishedScreen, "Published Personal Task");
        publishedScreen.saveTasksForTest();

        GuiTestSupport.assertEquals(List.of("Published Personal Task"),
                TodoListCommon.getTaskStorage().loadTasksSafe().stream().map(Task::getTitle).toList(),
                "LAN 主机保存个人任务后应同步写入本地文件");

        TodoScreen.resetGuiStateForTest();
        FakeMinecraftClient singleplayerMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        singleplayerMinecraft.setLocalServer(true);
        TodoScreen singleplayerScreen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(singleplayerMinecraft, singleplayerScreen);

        GuiTestSupport.assertEquals(List.of("Published Personal Task"),
                singleplayerScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重新进入单人世界后应仍能看到之前保存的个人任务");
    }

    /**
     * 验证更贴近真实操作的流程。
     * 玩家从单人进入后发布局域网、保存个人任务、关闭界面，再重新进入单人世界时仍能看到原任务。
     */
    private static void shouldKeepPersonalTasksAfterPublishedLocalWorldReentryFlow() {
        GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient lanHostMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        lanHostMinecraft.setLocalServer(true);
        lanHostMinecraft.setIntegratedServer(createPublishedIntegratedServer());
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen lanHostScreen = new TodoScreen(ScreenDriver.createParentScreen("pause"));

        ScreenDriver.init(lanHostMinecraft, lanHostScreen);
        addTaskViaInput(lanHostScreen, "LAN Host Journey Task");
        GuiTestSupport.assertTrue(lanHostScreen.hasUnsavedChangesForTest(), "发布局域网后新增个人任务应先标记为未保存");

        lanHostScreen.saveTasksForTest();

        GuiTestSupport.assertNotNull(lanHostMinecraft.getLastScreen(), "保存个人任务后应关闭当前界面并返回父界面");
        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                TodoListCommon.getTaskStorage().loadTasksSafe().stream().map(Task::getTitle).toList(),
                "LAN 主机保存个人任务后应同步写入本地文件");
        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                loadPlayerTaskTitles(OWNER_ID),
                "LAN 主机保存个人任务后也应写入主机玩家文件");

        TodoScreen.resetGuiStateForTest();
        FakeMinecraftClient singleplayerMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        singleplayerMinecraft.setLocalServer(true);
        singleplayerMinecraft.setIntegratedServer(createUnpublishedIntegratedServer());
        TodoScreen singleplayerScreen = new TodoScreen(ScreenDriver.createParentScreen("pause"));

        ScreenDriver.init(singleplayerMinecraft, singleplayerScreen);

        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                singleplayerScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "退出局域网主机后重新进入单人世界仍应看到刚才保存的个人任务");
        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                TodoListCommon.getTaskStorage().loadTasksSafe().stream().map(Task::getTitle).toList(),
                "重新进入单人世界后本地个人任务文件应保持最新内容");
    }

    /**
     * 验证远程服务端中先改个人任务、再切团队任务并点击保存时，两侧改动都会一起提交。
     */
    private static void shouldSavePersonalAndTeamTasksWhenSavingFromTeamViewOnRemoteServer() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-remote-save", "Remote Save Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("remote"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Remote Personal Task");
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Remote Team Task");

        screen.saveTasksForTest();

        GuiTestSupport.assertEquals(1, ops.getReplaceAllTaskCalls().size(), "从团队视图保存时也应提交个人任务整表");
        GuiTestSupport.assertEquals(1, ops.getReplaceTeamTaskCalls().size(), "从团队视图保存时应提交团队任务整表");
        GuiTestSupport.assertEquals(List.of("Remote Personal Task"),
                ops.getReplaceAllTaskCalls().get(0).stream().map(Task::getTitle).toList(),
                "个人任务提交内容应包含刚新增的个人任务");
        GuiTestSupport.assertEquals(List.of("Remote Team Task"),
                ops.getReplaceTeamTaskCalls().get(0).stream().map(Task::getTitle).toList(),
                "团队任务提交内容应包含刚新增的团队任务");
        GuiTestSupport.assertNotNull(minecraft.getLastScreen(), "保存成功后应关闭当前界面");

        TodoScreen reopenScreen = new TodoScreen(ScreenDriver.createParentScreen("remote"));
        ScreenDriver.init(minecraft, reopenScreen);
        GuiTestSupport.assertEquals(personalProject.getId(), reopenScreen.getCurrentProjectForTest().getId(), "重新打开 GUI 后应仍能回到个人项目");
        GuiTestSupport.assertEquals(List.of("Remote Personal Task"),
                reopenScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重新打开 GUI 后应能看到保存过的个人任务");
        reopenScreen.switchProjectForTest(teamProject);
        GuiTestSupport.assertEquals(List.of("Remote Team Task"),
                reopenScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重新打开 GUI 后应能看到保存过的团队任务");
    }

    /**
     * 验证远程服务端中先改团队任务、再切个人任务并点击保存后，团队任务会真正持久化并在重连后恢复。
     */
    private static void shouldKeepPersonalAndTeamTasksAfterRemoteReconnectWhenSavingFromPersonalView() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-remote-rejoin", "Remote Rejoin Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("remote"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Remote Team Persisted Task");
        screen.switchProjectForTest(personalProject);
        addTaskViaInput(screen, "Remote Personal Persisted Task");

        screen.saveTasksForTest();

        GuiTestSupport.assertEquals(1, ops.getReplaceAllTaskCalls().size(), "从个人视图保存时应提交个人任务整表");
        GuiTestSupport.assertEquals(1, ops.getReplaceTeamTaskCalls().size(), "从个人视图保存时也应提交团队任务整表");

        TodoScreen sameSessionScreen = new TodoScreen(ScreenDriver.createParentScreen("remote"));
        ScreenDriver.init(minecraft, sameSessionScreen);
        GuiTestSupport.assertEquals(List.of("Remote Personal Persisted Task"),
                sameSessionScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "同一联机会话内重新打开 GUI 时应能看到保存过的个人任务");
        sameSessionScreen.switchProjectForTest(teamProject);
        GuiTestSupport.assertEquals(List.of("Remote Team Persisted Task"),
                sameSessionScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "同一联机会话内重新打开 GUI 时应能看到保存过的团队任务");

        RecordingClientOps reconnectOps = new RecordingClientOps();
        restoreTasksToManager(reconnectOps.getTeamTaskManager(), ops.getReplaceTeamTaskCalls().get(0));
        ClientBridge.setOps(reconnectOps);
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient reconnectMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        TodoScreen reconnectScreen = new TodoScreen(ScreenDriver.createParentScreen("remote"));
        ScreenDriver.init(reconnectMinecraft, reconnectScreen);

        GuiTestSupport.assertEquals(personalProject.getId(), reconnectScreen.getCurrentProjectForTest().getId(), "重连后默认应仍能进入个人项目");
        GuiTestSupport.assertEquals(List.of("Remote Personal Persisted Task"),
                reconnectScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重连后应能从个人任务存储恢复个人任务");
        reconnectScreen.switchProjectForTest(teamProject);
        GuiTestSupport.assertEquals(List.of("Remote Team Persisted Task"),
                reconnectScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重连后应能从服务端同步结果恢复团队任务");
    }

    /**
     * 创建一个已发布 LAN 的测试集成服。
     *
     * @return 已发布状态的测试集成服
     */
    private static PublishedIntegratedServer createPublishedIntegratedServer() {
        PublishedIntegratedServer server = GuiTestSupport.allocate(PublishedIntegratedServer.class);
        server.setTestPublished(true);
        return server;
    }

    /**
     * 创建一个未发布 LAN 的测试集成服。
     *
     * @return 未发布状态的测试集成服
     */
    private static PublishedIntegratedServer createUnpublishedIntegratedServer() {
        PublishedIntegratedServer server = GuiTestSupport.allocate(PublishedIntegratedServer.class);
        server.setTestPublished(false);
        return server;
    }

    /**
     * 读取指定玩家文件中的个人任务标题。
     *
     * @param playerId 玩家 UUID
     * @return 玩家文件中的个人任务标题列表
     */
    private static List<String> loadPlayerTaskTitles(UUID playerId) {
        try {
            return TodoListCommon.getTaskStorage().loadPlayerTasks(playerId).stream().map(Task::getTitle).toList();
        } catch (Exception e) {
            throw new IllegalStateException("读取玩家个人任务失败", e);
        }
    }

    /**
     * 将任务列表复制回指定任务管理器，模拟重连后服务端重新同步团队任务。
     *
     * @param manager 目标任务管理器
     * @param tasks 需要恢复的任务列表
     */
    private static void restoreTasksToManager(com.todolist.task.TaskManager manager, List<Task> tasks) {
        if (manager == null) {
            return;
        }
        manager.clearAll();
        if (tasks == null) {
            return;
        }
        for (Task task : tasks) {
            manager.addTask(copyTask(task));
        }
    }

    /**
     * 复制一条任务，避免不同测试阶段共享同一个任务对象引用。
     *
     * @param task 原始任务
     * @return 复制后的任务
     */
    private static Task copyTask(Task task) {
        return task == null ? null : Task.fromNbt(task.toNbt());
    }

    /**
     * 用于 GUI 回归测试的 IntegratedServer 替身。
     * 允许直接控制是否模拟已发布 LAN。
     */
    private static final class PublishedIntegratedServer extends IntegratedServer {
        private boolean testPublished;

        /**
         * 仅为满足父类构造参数而保留。
         * 测试中通过 Unsafe 分配对象，不会真正走到这条路径。
         */
        private PublishedIntegratedServer() {
            super(null, null, null, null, null, null, null);
        }

        /**
         * 返回当前测试集成服是否模拟为已发布 LAN。
         *
         * @return true 表示模拟已发布 LAN
         */
        @Override
        public boolean isPublished() {
            return testPublished;
        }

        /**
         * 设置当前测试集成服的发布状态。
         *
         * @param published 是否模拟为已发布 LAN
         */
        void setTestPublished(boolean published) {
            this.testPublished = published;
        }
    }
}
