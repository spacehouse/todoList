package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.gui.testsupport.FakeClientConnection;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.task.Task;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;
import java.util.UUID;

/**
 * 主界面离线自测入口：优先覆盖初始化、项目切换与项目变更主路径。
 */
public final class TodoScreenTestMain {
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private TodoScreenTestMain() {
    }

    /**
     * 程序入口，串行执行主界面的离线自测。
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
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldDiscardUnsavedPersonalChangesOnClose", TodoScreenTestMain::shouldDiscardUnsavedPersonalChangesOnClose);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldRequestTeamSyncWhenClosingUnsavedTeamChanges", TodoScreenTestMain::shouldRequestTeamSyncWhenClosingUnsavedTeamChanges);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowNotificationWhenAddingWithoutProject", TodoScreenTestMain::shouldShowNotificationWhenAddingWithoutProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldOpenContextMenuAndApplyPriorityAction", TodoScreenTestMain::shouldOpenContextMenuAndApplyPriorityAction);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSearchAndAssignPlayerFromAssignScreen", TodoScreenTestMain::shouldSearchAndAssignPlayerFromAssignScreen);
    }

    /**
     * 校验主界面初始化后会默认选中个人默认项目并同步活动项目 ID。
     */
    private static void shouldInitializeWithDefaultPersonalProject() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertEquals(personalProject.getId(), screen.getCurrentProjectForTest().getId(), "初始化后应优先选中个人默认项目");
        GuiTestSupport.assertEquals("PERSONAL", screen.getViewModeNameForTest(), "个人项目初始化后应保持个人视图");
        GuiTestSupport.assertEquals(personalProject.getId(), ops.getActiveProjectId(), "初始化后应同步当前活动项目 ID");
        GuiTestSupport.assertEquals(personalProject.getId(), ops.getActiveProjectSyncCalls().get(0), "初始化后应向桥接层发送活动项目同步");
    }

    /**
     * 校验切换到团队项目后会更新当前项目、视图模式与活动项目 ID。
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

        GuiTestSupport.assertEquals(teamProject.getId(), screen.getCurrentProjectForTest().getId(), "切换项目后应更新当前项目");
        GuiTestSupport.assertEquals("TEAM_UNASSIGNED", screen.getViewModeNameForTest(), "切换到团队项目后应进入团队默认视图");
        GuiTestSupport.assertEquals(teamProject.getId(), ops.getActiveProjectId(), "切换团队项目后应更新活动项目 ID");
        GuiTestSupport.assertEquals(teamProject.getId(), ops.getActiveProjectSyncCalls().get(ops.getActiveProjectSyncCalls().size() - 1),
                "切换团队项目后应再次向桥接层同步活动项目");
    }

    /**
     * 校验项目新增、更新、删除和清空事件会驱动主界面切换到正确状态。
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
        GuiTestSupport.assertEquals(defaultTeamProject.getId(), screen.getCurrentProjectForTest().getId(), "删除当前团队项目后应回退到团队默认项目");

        TodoListCommon.getProjectManager().clearAll();
        GuiTestSupport.assertNull(screen.getCurrentProjectForTest(), "收到 CLEARED 事件后应清空当前项目");
        GuiTestSupport.assertEquals("PERSONAL", screen.getViewModeNameForTest(), "项目清空后应回退到个人视图");
        GuiTestSupport.assertNull(ops.getActiveProjectId(), "项目清空后应清空活动项目 ID");

        Project personalProject = createDefaultPersonalProject();
        TodoListCommon.getProjectManager().addProject(personalProject);
        GuiTestSupport.assertEquals(personalProject.getId(), screen.getCurrentProjectForTest().getId(), "收到 ADDED 事件且当前无项目时应选择可用回退项目");
    }

    /**
     * 校验编辑选中任务时会同步更新任务内容并标记未保存状态。
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

        GuiTestSupport.assertEquals("Alpha Updated", task.getTitle(), "编辑标题后应同步更新任务标题");
        GuiTestSupport.assertEquals(2, task.getTags().size(), "编辑标签后应同步更新任务标签集合");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "编辑任务后应标记存在未保存改动");
        GuiTestSupport.assertTrue(TodoScreen.hasPersonalUnsavedChanges(), "编辑个人任务后应同步个人未保存标记");
    }

    /**
     * 校验搜索与状态按钮会更新当前筛选结果。
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
        GuiTestSupport.assertEquals(1, screen.getFilteredTasksForTest().size(), "已完成筛选后应只显示完成任务");
        GuiTestSupport.assertEquals("Alpha", screen.getFilteredTasksForTest().get(0).getTitle(), "已完成筛选结果应命中完成任务");
    }

    /**
     * 校验保存个人任务会向桥接层发送整表替换，并清除未保存状态。
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

        GuiTestSupport.assertEquals(1, ops.getReplaceAllTaskCalls().size(), "保存个人任务后应向桥接层发送个人任务整表替换");
        GuiTestSupport.assertFalse(screen.hasUnsavedChangesForTest(), "保存后应清除未保存状态");
        GuiTestSupport.assertEquals(1, minecraft.getTestPlayerMessages().size(), "保存成功后应给玩家发送一次提示消息");
        GuiTestSupport.assertNotNull(minecraft.getLastScreen(), "保存后应返回父界面");
    }

    /**
     * 校验关闭界面时会丢弃未保存的个人任务改动。
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

        GuiTestSupport.assertEquals(0, screen.getCurrentManagerTasksForTest().size(), "关闭后应丢弃未保存的个人任务改动");
        GuiTestSupport.assertEquals(0, ops.getRequestTeamSyncCallCount(), "关闭个人视图时不应请求团队重同步");
        GuiTestSupport.assertFalse(TodoScreen.hasPersonalUnsavedChanges(), "关闭后应清除个人未保存标记");
    }

    /**
     * 校验团队视图存在未保存改动时，关闭界面会请求团队重同步。
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

        GuiTestSupport.assertEquals(1, ops.getRequestTeamSyncCallCount(), "关闭含未保存团队改动的界面时应请求团队重同步");
        GuiTestSupport.assertFalse(screen.hasUnsavedChangesForTest(), "关闭后应清除当前界面的未保存标记");
    }

    /**
     * 校验未选择项目时尝试新增任务会产生通知而不是直接写入任务。
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

        GuiTestSupport.assertEquals(1, screen.getNotificationCountForTest(), "未选择项目时新增任务应产生通知");
        GuiTestSupport.assertEquals(0, screen.getCurrentManagerTasksForTest().size(), "未选择项目时不应实际新增任务");
    }

    /**
     * 校验上下文菜单可打开并触发优先级修改动作。
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
        GuiTestSupport.assertEquals(4, screen.getContextMenuItemTextsForTest().size(), "上下文菜单应包含优先级与删除操作");

        screen.clickContextMenuItemForTest(0);

        GuiTestSupport.assertEquals(Task.Priority.HIGH, task.getPriority(), "点击上下文菜单优先级项后应更新任务优先级");
        GuiTestSupport.assertEquals(1, ops.getUpdateTaskCalls().size(), "通过上下文菜单修改优先级后应向桥接层发送任务更新");
        GuiTestSupport.assertFalse(screen.hasContextMenuForTest(), "执行上下文菜单动作后应关闭菜单");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "通过上下文菜单修改任务后应标记未保存状态");
    }

    /**
     * 校验任务分配弹窗支持搜索在线玩家并把任务分配给目标玩家。
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
        GuiTestSupport.assertEquals(List.of("bob"), screen.getAssignablePlayerNamesForTest(assignScreen), "搜索后应只保留匹配的在线玩家");

        screen.clickAssignPlayerRowForTest(assignScreen, 0);

        GuiTestSupport.assertEquals("bob", task.getAssigneeName(), "点击候选玩家后应更新任务受理人名称");
        GuiTestSupport.assertEquals("20000000-0000-0000-0000-000000000003", task.getAssigneeUuid(), "点击候选玩家后应更新任务受理人 UUID");
        GuiTestSupport.assertEquals(1, screen.getNotificationCountForTest(), "分配任务后应新增一条通知");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "分配任务后应标记未保存状态");
        GuiTestSupport.assertEquals(screen, minecraft.getLastScreen(), "完成分配后应返回主界面");
    }

    /**
     * 创建个人默认项目并注册到全局项目管理器。
     *
     * @return 个人默认项目
     */
    private static Project createDefaultPersonalProject() {
        Project project = new Project(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_KEY, Project.Scope.PERSONAL, OWNER_ID.toString());
        project.setId(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建团队默认项目并注册到全局项目管理器。
     *
     * @return 团队默认项目
     */
    private static Project createDefaultTeamProject() {
        Project project = new Project(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_KEY, Project.Scope.TEAM, OWNER_ID.toString());
        project.setId(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建普通团队项目并注册到全局项目管理器。
     *
     * @param id 项目 ID
     * @param name 项目名称
     * @return 团队项目
     */
    private static Project createTeamProject(String id, String name) {
        Project project = new Project(name, Project.Scope.TEAM, OWNER_ID.toString());
        project.setId(id);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 通过标题输入框和回车快捷键新增任务。
     *
     * @param screen 主界面
     * @param title 任务标题
     */
    private static void addTaskViaInput(TodoScreen screen, String title) {
        ScreenDriver.setText(screen.getTitleFieldForTest(), title);
        addTaskViaEnter(screen);
    }

    /**
     * 向主界面发送一次回车，触发标题输入框的新增任务逻辑。
     *
     * @param screen 主界面
     */
    private static void addTaskViaEnter(TodoScreen screen) {
        screen.getTitleFieldForTest().setFocused(true);
        ScreenDriver.pressEnter(screen);
    }

    /**
     * 为假客户端安装在线玩家列表，供任务分配弹窗读取候选人。
     *
     * @param minecraft 假客户端
     * @param players 在线玩家列表
     */
    private static void installOnlinePlayers(FakeMinecraftClient minecraft, PlayerInfo... players) {
        FakeClientConnection connection = (FakeClientConnection) minecraft.getConnection();
        connection.setOnlinePlayers(List.of(players));
    }

    /**
     * 创建测试玩家信息对象，减少重复样板代码。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @return 测试玩家信息对象
     */
    private static PlayerInfo createPlayerInfo(UUID uuid, String name) {
        return GuiTestSupport.createPlayerInfo(uuid, name);
    }
}
