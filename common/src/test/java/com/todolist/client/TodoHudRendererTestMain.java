package com.todolist.client;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import com.todolist.task.Task;

import java.util.List;
import java.util.UUID;

/**
 * TodoHudRenderer 离线自测入口：覆盖 HUD 视图回退、任务筛选、视觉元数据与高度语义。
 */
public final class TodoHudRendererTestMain {
    private static final UUID OWNER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private TodoHudRendererTestMain() {
    }

    /**
     * 程序入口，串行执行 TodoHudRenderer 的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldFallbackToPersonalViewWhenTeamProjectsDisabled", TodoHudRendererTestMain::shouldFallbackToPersonalViewWhenTeamProjectsDisabled);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldFilterUnassignedTasksInTeamUnassignedView", TodoHudRendererTestMain::shouldFilterUnassignedTasksInTeamUnassignedView);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldFilterAssignedTasksFromCurrentTeamProject", TodoHudRendererTestMain::shouldFilterAssignedTasksFromCurrentTeamProject);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldFilterStarredProjectsInTeamAllView", TodoHudRendererTestMain::shouldFilterStarredProjectsInTeamAllView);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldRenderPriorityAsColorBlockMetadata", TodoHudRendererTestMain::shouldRenderPriorityAsColorBlockMetadata);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldRespectTodoAndDoneLimitsSeparately", TodoHudRendererTestMain::shouldRespectTodoAndDoneLimitsSeparately);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldShowHiddenCountSummaryWhenCollapsedOrTruncated", TodoHudRendererTestMain::shouldShowHiddenCountSummaryWhenCollapsedOrTruncated);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldReserveRowForHiddenCountWhenHeightIsTight", TodoHudRendererTestMain::shouldReserveRowForHiddenCountWhenHeightIsTight);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldClearCachesWhenForceRefreshing", TodoHudRendererTestMain::shouldClearCachesWhenForceRefreshing);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldChangePanelHeightWhenTogglingExpanded", TodoHudRendererTestMain::shouldChangePanelHeightWhenTogglingExpanded);
    }

    /**
     * 验证团队项目能力关闭时，HUD 团队视图会回退为个人视图。
     */
    private static void shouldFallbackToPersonalViewWhenTeamProjectsDisabled() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        ops.setTeamProjectsEnabled(false);
        config.setHudDefaultView("TEAM_ASSIGNED");

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);

        GuiTestSupport.assertEquals("PERSONAL", renderer.getResolvedViewModeNameForTest(), "团队 HUD 视图在能力关闭时应回退为个人视图");
    }

    /**
     * 验证 TEAM_UNASSIGNED 视图只保留未指派的团队任务。
     */
    private static void shouldFilterUnassignedTasksInTeamUnassignedView() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_UNASSIGNED");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-unassigned", "HUD Unassigned");
        ops.getTeamTaskManager().addTask(createTeamTask("Unassigned Pending", project.getId(), null, null, Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Assigned Pending", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Unassigned Done", project.getId(), null, null, Task.Priority.LOW, true));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(List.of("Unassigned Pending"), taskTitles(renderer.getCachedPendingTasksForTest()), "TEAM_UNASSIGNED 视图应只保留未指派的未完成任务");
        GuiTestSupport.assertEquals(List.of("Unassigned Done"), taskTitles(renderer.getCachedDoneTasksForTest()), "TEAM_UNASSIGNED 视图应只保留未指派的已完成任务");
        GuiTestSupport.assertEquals(2, renderer.getRowRenderCacheSizeForTest(), "未指派视图应只缓存命中的未指派任务");
    }

    /**
     * 验证 TEAM_ASSIGNED + CURRENT 组合只保留当前团队项目中分配给自己的任务。
     */
    private static void shouldFilterAssignedTasksFromCurrentTeamProject() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ASSIGNED");
        config.setHudProjectSource("CURRENT");

        Project activeProject = createTeamProject("hud-team-a", "HUD Team A");
        Project otherProject = createTeamProject("hud-team-b", "HUD Team B");
        ops.setActiveProjectId(activeProject.getId());
        ops.getTeamTaskManager().addTask(createTeamTask("Mine Active", activeProject.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Other Active", activeProject.getId(), OTHER_ID.toString(), "other", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Unassigned Active", activeProject.getId(), null, null, Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Mine Other Project", otherProject.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Mine Done", activeProject.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(List.of("Mine Active"), taskTitles(renderer.getCachedPendingTasksForTest()), "当前团队项目视图应只保留分配给自己的未完成任务");
        GuiTestSupport.assertEquals(List.of("Mine Done"), taskTitles(renderer.getCachedDoneTasksForTest()), "当前团队项目视图应只保留分配给自己的已完成任务");
        GuiTestSupport.assertEquals(2, renderer.getRowRenderCacheSizeForTest(), "刷新 HUD 模型后应只缓存命中的任务行");
    }

    /**
     * 验证 TEAM_ALL + STARRED 组合只保留加星团队项目中的已分派任务。
     */
    private static void shouldFilterStarredProjectsInTeamAllView() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("STARRED");

        Project starredProject = createTeamProject("hud-starred", "HUD Starred");
        Project normalProject = createTeamProject("hud-normal", "HUD Normal");
        config.setHudStarredProjectIds(List.of(starredProject.getId()));
        ops.getTeamTaskManager().addTask(createTeamTask("Starred Assigned", starredProject.getId(), OTHER_ID.toString(), "other", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Normal Assigned", normalProject.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Starred Done", starredProject.getId(), OTHER_ID.toString(), "other", Task.Priority.MEDIUM, true));
        ops.getTeamTaskManager().addTask(createTeamTask("Starred Unassigned", starredProject.getId(), null, null, Task.Priority.LOW, false));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(List.of("Starred Assigned"), taskTitles(renderer.getCachedPendingTasksForTest()), "STARRED 来源应只保留加星项目中的已分派未完成任务");
        GuiTestSupport.assertEquals(List.of("Starred Done"), taskTitles(renderer.getCachedDoneTasksForTest()), "STARRED 来源应只保留加星项目中的已分派已完成任务");
        GuiTestSupport.assertEquals(2, renderer.getRowRenderCacheSizeForTest(), "加星项目筛选后应只保留命中的两条缓存");
    }

    /**
     * 验证 HUD 任务行会暴露色块优先级元数据，而不是依赖优先级文本图标。
     */
    private static void shouldRenderPriorityAsColorBlockMetadata() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-priority", "HUD Priority");
        Task task = createTeamTask("Priority Task", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        task.addTag("Alpha");
        ops.getTeamTaskManager().addTask(task);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(Task.Priority.HIGH.getColor(), renderer.getPriorityBlockColorForTest(task.getId()), "HUD 优先级应改为与主界面一致的色块颜色");
        GuiTestSupport.assertEquals("", renderer.getPriorityTextForTest(task.getId()), "HUD 任务行不应再依赖优先级文本图标");
        GuiTestSupport.assertTrue(renderer.getRowTitleOffsetForTest(task.getId()) > 8, "HUD 标题应位于优先级色块和前置标签之后");
    }

    /**
     * 验证待办数与已办数上限会分别生效，不互相挤占配额。
     */
    private static void shouldRespectTodoAndDoneLimitsSeparately() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");
        config.setHudTodoLimit(1);
        config.setHudDoneLimit(2);
        config.setHudMaxHeight(400);

        Project project = createTeamProject("hud-limits", "HUD Limits");
        ops.getTeamTaskManager().addTask(createTeamTask("Pending A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending B", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Done A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, true));
        ops.getTeamTaskManager().addTask(createTeamTask("Done B", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, true));
        ops.getTeamTaskManager().addTask(createTeamTask("Done C", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(1, renderer.getShownPendingCountForTest(), "待办上限应独立控制未完成任务显示数量");
        GuiTestSupport.assertEquals(2, renderer.getShownDoneCountForTest(), "已办上限应独立控制已完成任务显示数量");
        GuiTestSupport.assertEquals(2, renderer.getHiddenCountForTest(), "超出待办和已办上限的任务应统一计入隐藏数量");
    }

    /**
     * 验证折叠态摘要和截断态隐藏计数文本语义稳定。
     */
    private static void shouldShowHiddenCountSummaryWhenCollapsedOrTruncated() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");
        config.setHudTodoLimit(1);
        config.setHudDoneLimit(1);
        config.setHudMaxHeight(400);
        config.setHudDefaultExpanded(true);

        Project project = createTeamProject("hud-summary", "HUD Summary");
        ops.getTeamTaskManager().addTask(createTeamTask("Pending A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending B", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Done A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true));
        ops.getTeamTaskManager().addTask(createTeamTask("Done B", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertTrue(renderer.getHiddenCountTextForTest().contains("2"), "展开态被截断时应显示隐藏任务计数");

        renderer.toggleExpanded();

        GuiTestSupport.assertTrue(renderer.getCollapsedSummaryTextForTest().contains("2"), "折叠态应显示待办与已办摘要");
    }

    /**
     * 验证内容区高度紧张时，HUD 仍会预留一行显示隐藏任务计数。
     */
    private static void shouldReserveRowForHiddenCountWhenHeightIsTight() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");
        config.setHudTodoLimit(9);
        config.setHudDoneLimit(0);
        config.setHudMaxHeight(120);
        config.setHudDefaultExpanded(true);

        Project project = createTeamProject("hud-tight-height", "HUD Tight Height");
        ops.getTeamTaskManager().addTask(createTeamTask("Pending A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending B", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending C", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending D", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending E", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending F", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending G", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending H", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Pending I", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, false));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(7, renderer.getShownPendingCountForTest(), "最小 HUD 高度下应预留一行给隐藏计数，而不是把全部可用行都挤给任务");
        GuiTestSupport.assertEquals(2, renderer.getHiddenCountForTest(), "被预留行挤出的任务也应计入隐藏数量");
        GuiTestSupport.assertTrue(renderer.getHiddenCountTextForTest().contains("2"), "高度不足时仍应显示隐藏任务计数");
    }

    /**
     * 验证强制刷新会立即清空当前缓存任务与任务行渲染缓存。
     */
    private static void shouldClearCachesWhenForceRefreshing() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-cache", "HUD Cache");
        ops.getTeamTaskManager().addTask(createTeamTask("Cache Pending", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Cache Done", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, true));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();
        GuiTestSupport.assertEquals(1, renderer.getCachedPendingTasksForTest().size(), "刷新前应缓存未完成任务");
        GuiTestSupport.assertEquals(1, renderer.getCachedDoneTasksForTest().size(), "刷新前应缓存已完成任务");
        GuiTestSupport.assertEquals(2, renderer.getRowRenderCacheSizeForTest(), "刷新前应构建任务行缓存");

        renderer.forceRefreshTasks();

        GuiTestSupport.assertEquals(0, renderer.getCachedPendingTasksForTest().size(), "强制刷新后应清空未完成任务缓存");
        GuiTestSupport.assertEquals(0, renderer.getCachedDoneTasksForTest().size(), "强制刷新后应清空已完成任务缓存");
        GuiTestSupport.assertEquals(0, renderer.getRowRenderCacheSizeForTest(), "强制刷新后应清空任务行缓存");
    }

    /**
     * 验证 HUD 折叠与展开状态会影响当前面板高度计算结果。
     */
    private static void shouldChangePanelHeightWhenTogglingExpanded() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");
        config.setHudDefaultExpanded(true);
        config.setHudTodoLimit(5);
        config.setHudDoneLimit(5);
        config.setHudMaxHeight(400);

        Project project = createTeamProject("hud-height", "HUD Height");
        ops.getTeamTaskManager().addTask(createTeamTask("Height Pending A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Height Pending B", project.getId(), OTHER_ID.toString(), "other", Task.Priority.MEDIUM, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Height Done", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        int expandedHeight = renderer.getCurrentPanelHeight();

        renderer.toggleExpanded();
        int collapsedHeight = renderer.getCurrentPanelHeight();

        GuiTestSupport.assertEquals(62, expandedHeight, "展开态高度应包含两条待办、一条已完成和分隔行");
        GuiTestSupport.assertEquals(26, collapsedHeight, "折叠态高度应只保留标题和摘要行");
        GuiTestSupport.assertTrue(collapsedHeight < expandedHeight, "折叠后高度应小于展开态");
        GuiTestSupport.assertFalse(renderer.isExpanded(), "切换一次后 HUD 应处于折叠状态");
    }

    /**
     * 创建带默认窗口参数的假客户端，供 HUD 自测复用。
     *
     * @return 具备 HUD 测试支持的假客户端
     */
    private static FakeMinecraftClient createMinecraft() {
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        minecraft.setWindowMetrics(320, 240, 1.0D);
        minecraft.setHideGui(false);
        return minecraft;
    }

    /**
     * 创建团队项目并注册到全局项目管理器。
     *
     * @param id 项目 ID
     * @param name 项目名称
     * @return 新建的团队项目
     */
    private static Project createTeamProject(String id, String name) {
        Project project = new Project(name, Project.Scope.TEAM, OWNER_ID.toString());
        project.setId(id);
        com.todolist.TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建一条带项目与指派信息的团队任务。
     *
     * @param title 任务标题
     * @param projectId 所属项目 ID
     * @param assigneeUuid 受理人 UUID
     * @param assigneeName 受理人名称
     * @param priority 优先级
     * @param completed 是否已完成
     * @return 配置完成的团队任务
     */
    private static Task createTeamTask(String title, String projectId, String assigneeUuid, String assigneeName, Task.Priority priority, boolean completed) {
        Task task = new Task(title, "");
        task.setScope(Task.Scope.TEAM);
        task.setProjectId(projectId);
        task.setPriority(priority);
        task.setCompleted(completed);
        if (assigneeUuid != null) {
            task.setAssigneeUuid(assigneeUuid);
        }
        if (assigneeName != null) {
            task.setAssigneeName(assigneeName);
        }
        return task;
    }

    /**
     * 提取任务标题列表，便于断言 HUD 过滤结果。
     *
     * @param tasks 任务列表
     * @return 对应的标题列表
     */
    private static List<String> taskTitles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }
}
