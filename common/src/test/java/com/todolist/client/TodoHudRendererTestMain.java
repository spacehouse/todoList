package com.todolist.client;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.task.Task;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/**
 * TodoHudRenderer 离线自测入口，覆盖 HUD 视图过滤、顺序同步、透明度与标题展示等关键回归场景。
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
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldHideParentWhenAllSubtasksAssignedInTeamUnassignedView", TodoHudRendererTestMain::shouldHideParentWhenAllSubtasksAssignedInTeamUnassignedView);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldShowParentInTeamAssignedViewOnlyWhenAnyDirectSubtaskBelongsToPlayer", TodoHudRendererTestMain::shouldShowParentInTeamAssignedViewOnlyWhenAnyDirectSubtaskBelongsToPlayer);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldFilterAssignedTasksFromCurrentTeamProject", TodoHudRendererTestMain::shouldFilterAssignedTasksFromCurrentTeamProject);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldFilterStarredProjectsInTeamAllView", TodoHudRendererTestMain::shouldFilterStarredProjectsInTeamAllView);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldKeepTaskManagerOrderInTeamAllView", TodoHudRendererTestMain::shouldKeepTaskManagerOrderInTeamAllView);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldKeepHudTextOpaqueWhenOpacityChanges", TodoHudRendererTestMain::shouldKeepHudTextOpaqueWhenOpacityChanges);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldBuildHeaderWithSpaceProjectAndView", TodoHudRendererTestMain::shouldBuildHeaderWithSpaceProjectAndView);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldBuildHeaderWithStarredProjectSource", TodoHudRendererTestMain::shouldBuildHeaderWithStarredProjectSource);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldBuildHeaderWithAllProjectSource", TodoHudRendererTestMain::shouldBuildHeaderWithAllProjectSource);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldHideAssigneeLabelInHudRows", TodoHudRendererTestMain::shouldHideAssigneeLabelInHudRows);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldHideSubtaskRowsWhenHudSubtasksDisabled", TodoHudRendererTestMain::shouldHideSubtaskRowsWhenHudSubtasksDisabled);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldRenderPriorityAsColorBlockMetadata", TodoHudRendererTestMain::shouldRenderPriorityAsColorBlockMetadata);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldDifferentiateSubtaskRowsInHud", TodoHudRendererTestMain::shouldDifferentiateSubtaskRowsInHud);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldPlaceSubtasksDirectlyAfterParentInHud", TodoHudRendererTestMain::shouldPlaceSubtasksDirectlyAfterParentInHud);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldShowParentSubtaskProgressAndKeepCompletedChildInPendingSection", TodoHudRendererTestMain::shouldShowParentSubtaskProgressAndKeepCompletedChildInPendingSection);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldMoveParentGroupToDoneSectionOnlyAfterAllSubtasksComplete", TodoHudRendererTestMain::shouldMoveParentGroupToDoneSectionOnlyAfterAllSubtasksComplete);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldRespectTodoAndDoneLimitsSeparately", TodoHudRendererTestMain::shouldRespectTodoAndDoneLimitsSeparately);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldShowHiddenCountSummaryWhenCollapsedOrTruncated", TodoHudRendererTestMain::shouldShowHiddenCountSummaryWhenCollapsedOrTruncated);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldExcludeSubtasksFromHudSummaryCounts", TodoHudRendererTestMain::shouldExcludeSubtasksFromHudSummaryCounts);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldReserveRowForHiddenCountWhenHeightIsTight", TodoHudRendererTestMain::shouldReserveRowForHiddenCountWhenHeightIsTight);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldClearCachesWhenForceRefreshing", TodoHudRendererTestMain::shouldClearCachesWhenForceRefreshing);
        GuiTestSupport.runTestCase("TodoHudRendererTestMain.shouldFollowGuiPersonalTaskSnapshot", TodoHudRendererTestMain::shouldFollowGuiPersonalTaskSnapshot);
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
     * 验证当父任务直属子任务全部已指派后，HUD 的 TEAM_UNASSIGNED 视图不再显示该父任务；
     * 若仍存在未指派子任务，则父任务继续保留作为上下文。
     */
    private static void shouldHideParentWhenAllSubtasksAssignedInTeamUnassignedView() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_UNASSIGNED");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-parent-assigned", "HUD Parent Assigned");
        Task fullyAssignedParent = createTeamTask("Fully Assigned Parent", project.getId(), null, null, Task.Priority.HIGH, false);
        Task fullyAssignedChildA = createTeamTask("Fully Assigned Child A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        fullyAssignedChildA.setParentTaskId(fullyAssignedParent.getId());
        fullyAssignedChildA.setSubtaskSortOrder(0L);
        Task fullyAssignedChildB = createTeamTask("Fully Assigned Child B", project.getId(), OTHER_ID.toString(), "other", Task.Priority.LOW, false);
        fullyAssignedChildB.setParentTaskId(fullyAssignedParent.getId());
        fullyAssignedChildB.setSubtaskSortOrder(1L);

        Task mixedParent = createTeamTask("Mixed Parent", project.getId(), null, null, Task.Priority.HIGH, false);
        Task mixedAssignedChild = createTeamTask("Mixed Assigned Child", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        mixedAssignedChild.setParentTaskId(mixedParent.getId());
        mixedAssignedChild.setSubtaskSortOrder(0L);
        Task mixedUnassignedChild = createTeamTask("Mixed Unassigned Child", project.getId(), null, null, Task.Priority.LOW, false);
        mixedUnassignedChild.setParentTaskId(mixedParent.getId());
        mixedUnassignedChild.setSubtaskSortOrder(1L);

        Task standaloneUnassigned = createTeamTask("Standalone Unassigned", project.getId(), null, null, Task.Priority.MEDIUM, false);

        ops.getTeamTaskManager().addTask(fullyAssignedParent);
        ops.getTeamTaskManager().addTask(fullyAssignedChildA);
        ops.getTeamTaskManager().addTask(fullyAssignedChildB);
        ops.getTeamTaskManager().addTask(mixedParent);
        ops.getTeamTaskManager().addTask(mixedAssignedChild);
        ops.getTeamTaskManager().addTask(mixedUnassignedChild);
        ops.getTeamTaskManager().addTask(standaloneUnassigned);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(
                List.of("Mixed Parent", "Mixed Unassigned Child", "Standalone Unassigned"),
                taskTitles(renderer.getCachedPendingTasksForTest()),
                "TEAM_UNASSIGNED 视图应隐藏直属子任务已全部指派的父任务，但保留仍有未指派子任务的父任务上下文与未指派子任务"
        );
    }

    /**
     * 验证 TEAM_ASSIGNED 视图只会保留直属子任务中存在当前玩家的父任务，或直接分配给当前玩家的普通任务。
     */
    private static void shouldShowParentInTeamAssignedViewOnlyWhenAnyDirectSubtaskBelongsToPlayer() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ASSIGNED");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-parent-mine", "HUD Parent Mine");
        Task mixedParent = createTeamTask("Mixed Parent", project.getId(), null, null, Task.Priority.HIGH, false);
        Task myChild = createTeamTask("My Child", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        myChild.setParentTaskId(mixedParent.getId());
        myChild.setSubtaskSortOrder(0L);
        Task otherChild = createTeamTask("Other Child", project.getId(), OTHER_ID.toString(), "other", Task.Priority.LOW, false);
        otherChild.setParentTaskId(mixedParent.getId());
        otherChild.setSubtaskSortOrder(1L);

        Task othersOnlyParent = createTeamTask("Others Only Parent", project.getId(), null, null, Task.Priority.HIGH, false);
        Task othersOnlyChild = createTeamTask("Others Only Child", project.getId(), OTHER_ID.toString(), "other", Task.Priority.MEDIUM, false);
        othersOnlyChild.setParentTaskId(othersOnlyParent.getId());
        othersOnlyChild.setSubtaskSortOrder(0L);

        Task directMine = createTeamTask("Direct Mine", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        Task directOther = createTeamTask("Direct Other", project.getId(), OTHER_ID.toString(), "other", Task.Priority.MEDIUM, false);

        ops.getTeamTaskManager().addTask(mixedParent);
        ops.getTeamTaskManager().addTask(myChild);
        ops.getTeamTaskManager().addTask(otherChild);
        ops.getTeamTaskManager().addTask(othersOnlyParent);
        ops.getTeamTaskManager().addTask(othersOnlyChild);
        ops.getTeamTaskManager().addTask(directMine);
        ops.getTeamTaskManager().addTask(directOther);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(
                List.of("Mixed Parent", "My Child", "Direct Mine"),
                taskTitles(renderer.getCachedPendingTasksForTest()),
                "TEAM_ASSIGNED 视图应只保留直属子任务中存在当前玩家的父任务，以及直接分配给当前玩家的普通任务"
        );
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
     * 验证 TEAM_ALL + STARRED 组合会保留加星团队项目中的全部任务。
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

        GuiTestSupport.assertEquals(List.of("Starred Assigned", "Starred Unassigned"), taskTitles(renderer.getCachedPendingTasksForTest()), "STARRED 来源应保留加星项目中的全部未完成任务");
        GuiTestSupport.assertEquals(List.of("Starred Done"), taskTitles(renderer.getCachedDoneTasksForTest()), "STARRED 来源应保留加星项目中的已完成任务");
        GuiTestSupport.assertEquals(3, renderer.getRowRenderCacheSizeForTest(), "加星项目筛选后应缓存全部命中的任务");
    }

    /**
     * 验证 TEAM_ALL 视图中的 HUD 顺序会跟随任务管理器顺序，不再额外按优先级重排。
     */
    private static void shouldKeepTaskManagerOrderInTeamAllView() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-order", "HUD Order");
        ops.getTeamTaskManager().addTask(createTeamTask("Low First", project.getId(), null, null, Task.Priority.LOW, false));
        ops.getTeamTaskManager().addTask(createTeamTask("High Second", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false));
        ops.getTeamTaskManager().addTask(createTeamTask("Done First", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true));
        ops.getTeamTaskManager().addTask(createTeamTask("Done Second", project.getId(), OTHER_ID.toString(), "other", Task.Priority.HIGH, true));

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(List.of("Low First", "High Second"), taskTitles(renderer.getCachedPendingTasksForTest()), "TEAM_ALL 视图中的未完成任务顺序应与任务管理器一致");
        GuiTestSupport.assertEquals(List.of("Done First", "Done Second"), taskTitles(renderer.getCachedDoneTasksForTest()), "TEAM_ALL 视图中的已完成任务顺序应与任务管理器一致");
    }

    /**
     * 验证 HUD 透明度变化时，只影响背景，不影响标题和任务文字。
     */
    private static void shouldKeepHudTextOpaqueWhenOpacityChanges() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);

        int backgroundColor = renderer.getPanelBackgroundColorForTest(0.25F);
        int headerTextColor = renderer.getHeaderTextColorForTest();
        int taskTextColor = renderer.getTaskTextColorForTest();

        GuiTestSupport.assertTrue(((backgroundColor >>> 24) & 0xFF) < 255, "HUD 背景透明度降低后 alpha 应跟随下降");
        GuiTestSupport.assertEquals(255, (headerTextColor >>> 24) & 0xFF, "HUD 标题文字不应受透明度影响");
        GuiTestSupport.assertEquals(255, (taskTextColor >>> 24) & 0xFF, "HUD 任务文字不应受透明度影响");
    }

    /**
     * 验证 HUD 顶部标题会按“空间-项目名-团队子视图”格式组装。
     */
    private static void shouldBuildHeaderWithSpaceProjectAndView() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("CURRENT");

        Project project = createTeamProject("hud-header", "HUD Header");
        ops.setActiveProjectId(project.getId());

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        String expected = Component.translatable("hud.todolist.space_label.team").getString()
                + "-"
                + ProjectNameFormatter.toDisplayText(project).getString()
                + "-"
                + Component.translatable("hud.todolist.team_view_label.all").getString();
        GuiTestSupport.assertEquals(expected, renderer.getHeaderTitleForTest(), "HUD 顶部标题应展示空间、项目名和当前团队子视图");
    }

    /**
     * 验证 HUD 任务行会暴露色块优先级元数据，而不是依赖优先级文本图标。
     */
    /**
     * 验证 STARRED 来源会在 HUD 标题中显示“星标项目”，而不是某一个活动项目名。
     */
    private static void shouldBuildHeaderWithStarredProjectSource() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("STARRED");

        Project project = createTeamProject("hud-starred-header", "HUD Starred Header");
        ops.setActiveProjectId(project.getId());

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        String expected = Component.translatable("hud.todolist.space_label.team").getString()
                + "-"
                + Component.translatable("gui.todolist.hud.project_source.starred").getString()
                + "-"
                + Component.translatable("hud.todolist.team_view_label.all").getString();
        GuiTestSupport.assertEquals(expected, renderer.getHeaderTitleForTest(), "STARRED 来源下，HUD 标题应显示星标项目来源标签");
    }

    /**
     * 验证 ALL 来源会在 HUD 标题中显示“全部项目”。
     */
    private static void shouldBuildHeaderWithAllProjectSource() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-all-header", "HUD All Header");
        ops.setActiveProjectId(project.getId());

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        String expected = Component.translatable("hud.todolist.space_label.team").getString()
                + "-"
                + Component.translatable("gui.todolist.hud.project_source.all").getString()
                + "-"
                + Component.translatable("hud.todolist.team_view_label.all").getString();
        GuiTestSupport.assertEquals(expected, renderer.getHeaderTitleForTest(), "ALL 来源下，HUD 标题应显示全部项目来源标签");
    }

    /**
     * 验证 HUD 任务行不再显示责任人标签，只保留标签与标题。
     */
    private static void shouldHideAssigneeLabelInHudRows() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ASSIGNED");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-hide-assignee", "HUD Hide Assignee");
        Task task = createTeamTask("Assigned Task", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        task.addTag("Alpha");
        ops.getTeamTaskManager().addTask(task);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals("", renderer.getAssigneeTextForTest(task.getId()), "HUD 任务行不应再显示责任人标签");
        GuiTestSupport.assertTrue(renderer.getTagTextForTest(task.getId()).contains("Alpha"), "HUD 任务行仍应保留任务标签");
    }

    /**
     * 验证关闭 HUD 子任务展示后，只保留父任务行并继续显示父任务进度。
     */
    private static void shouldHideSubtaskRowsWhenHudSubtasksDisabled() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");
        config.setHudShowSubtasks(false);

        Project project = createTeamProject("hud-hide-subtasks", "HUD Hide Subtasks");
        Task parent = createTeamTask("Parent Task", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        Task childTodo = createTeamTask("Child Todo", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        childTodo.setParentTaskId(parent.getId());
        childTodo.setSubtaskSortOrder(0L);
        Task childDone = createTeamTask("Child Done", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true);
        childDone.setParentTaskId(parent.getId());
        childDone.setSubtaskSortOrder(1L);

        ops.getTeamTaskManager().addTask(parent);
        ops.getTeamTaskManager().addTask(childTodo);
        ops.getTeamTaskManager().addTask(childDone);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(List.of("Parent Task"), taskTitles(renderer.getCachedPendingTasksForTest()), "关闭 HUD 子任务展示后，未完成区应只保留父任务");
        GuiTestSupport.assertEquals(List.of(), taskTitles(renderer.getCachedDoneTasksForTest()), "关闭 HUD 子任务展示后，子任务不应单独出现在已完成区");
        GuiTestSupport.assertEquals("1/2", renderer.getRowTrailingTextForTest(parent.getId()), "关闭 HUD 子任务展示后，父任务右侧进度应继续保留");
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
     * 验证 HUD 中的子任务行会带层级前缀和额外缩进，不再与普通任务完全一致。
     */
    private static void shouldDifferentiateSubtaskRowsInHud() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-subtask", "HUD Subtask");
        Task parent = createTeamTask("Parent Task", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        Task subtask = createTeamTask("Child Task", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        subtask.setParentTaskId(parent.getId());
        ops.getTeamTaskManager().addTask(parent);
        ops.getTeamTaskManager().addTask(subtask);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals("Parent Task", renderer.getRowTitleTextForTest(parent.getId()), "父任务 HUD 标题应保持原样");
        GuiTestSupport.assertEquals("- ", renderer.getRowPrefixTextForTest(subtask.getId()), "子任务前缀应独立绘制在优先级色块前");
        GuiTestSupport.assertEquals("Child Task", renderer.getRowTitleTextForTest(subtask.getId()), "子任务标题正文不应再混入层级前缀");
        GuiTestSupport.assertTrue(
                renderer.getPriorityBlockOffsetForTest(subtask.getId()) > renderer.getPriorityBlockOffsetForTest(parent.getId()),
                "子任务优先级色块应整体右移，作为层级缩进起点"
        );
        GuiTestSupport.assertTrue(
                renderer.getRowTitleOffsetForTest(subtask.getId()) > renderer.getRowTitleOffsetForTest(parent.getId()),
                "子任务 HUD 标题应比父任务额外缩进"
        );
    }

    /**
     * 验证 HUD 会把直属子任务排到对应父任务下方，而不是继续沿用原始插入顺序散落显示。
     */
    private static void shouldPlaceSubtasksDirectlyAfterParentInHud() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-subtask-order", "HUD Subtask Order");
        Task parentA = createTeamTask("Parent A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        Task unrelated = createTeamTask("Other Task", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        Task childA2 = createTeamTask("Child A2", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, false);
        childA2.setParentTaskId(parentA.getId());
        childA2.setSubtaskSortOrder(2L);
        Task childA1 = createTeamTask("Child A1", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        childA1.setParentTaskId(parentA.getId());
        childA1.setSubtaskSortOrder(1L);

        ops.getTeamTaskManager().addTask(parentA);
        ops.getTeamTaskManager().addTask(unrelated);
        ops.getTeamTaskManager().addTask(childA2);
        ops.getTeamTaskManager().addTask(childA1);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(
                List.of("Parent A", "Child A1", "Child A2", "Other Task"),
                taskTitles(renderer.getCachedPendingTasksForTest()),
                "HUD 中子任务应紧跟在对应父任务下方，并按父内顺序排列"
        );
    }

    /**
     * 验证父任务会在右侧显示直属子任务完成进度，且部分完成时子任务仍保留在父任务下方。
     */
    private static void shouldShowParentSubtaskProgressAndKeepCompletedChildInPendingSection() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-subtask-progress", "HUD Subtask Progress");
        Task parent = createTeamTask("Parent Progress", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        Task completedChild = createTeamTask("Child Done", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, true);
        completedChild.setParentTaskId(parent.getId());
        completedChild.setSubtaskSortOrder(0L);
        Task pendingChild = createTeamTask("Child Todo", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, false);
        pendingChild.setParentTaskId(parent.getId());
        pendingChild.setSubtaskSortOrder(1L);

        ops.getTeamTaskManager().addTask(parent);
        ops.getTeamTaskManager().addTask(completedChild);
        ops.getTeamTaskManager().addTask(pendingChild);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(
                List.of("Parent Progress", "Child Done", "Child Todo"),
                taskTitles(renderer.getCachedPendingTasksForTest()),
                "只完成部分子任务时，父任务组仍应保留在未完成部分"
        );
        GuiTestSupport.assertEquals(List.of(), taskTitles(renderer.getCachedDoneTasksForTest()), "父任务未全部完成前，不应提前进入已完成部分");
        GuiTestSupport.assertEquals("1/2", renderer.getRowTrailingTextForTest(parent.getId()), "父任务右侧应显示直属子任务已完成数与总数");
        GuiTestSupport.assertTrue(renderer.isRowTitleStrikethroughForTest(completedChild.getId()), "已完成子任务在原位置应显示删除线");
        GuiTestSupport.assertTrue(renderer.isRowTitleGrayForTest(completedChild.getId()), "已完成子任务在原位置应显示灰色标题");
        GuiTestSupport.assertTrue(!renderer.isRowTitleStrikethroughForTest(pendingChild.getId()), "未完成子任务不应显示删除线");
    }

    /**
     * 验证只有当直属子任务全部完成后，父任务组才整体进入已完成部分。
     */
    private static void shouldMoveParentGroupToDoneSectionOnlyAfterAllSubtasksComplete() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");

        Project project = createTeamProject("hud-subtask-all-done", "HUD Subtask All Done");
        Task parent = createTeamTask("Parent All Done", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        Task childA = createTeamTask("Child A", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, true);
        childA.setParentTaskId(parent.getId());
        childA.setSubtaskSortOrder(0L);
        Task childB = createTeamTask("Child B", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true);
        childB.setParentTaskId(parent.getId());
        childB.setSubtaskSortOrder(1L);

        ops.getTeamTaskManager().addTask(parent);
        ops.getTeamTaskManager().addTask(childA);
        ops.getTeamTaskManager().addTask(childB);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        GuiTestSupport.assertEquals(List.of(), taskTitles(renderer.getCachedPendingTasksForTest()), "直属子任务全部完成后，父任务组不应继续停留在未完成部分");
        GuiTestSupport.assertEquals(
                List.of("Parent All Done", "Child A", "Child B"),
                taskTitles(renderer.getCachedDoneTasksForTest()),
                "直属子任务全部完成后，父任务及其子任务应整体进入已完成部分"
        );
        GuiTestSupport.assertEquals("2/2", renderer.getRowTrailingTextForTest(parent.getId()), "全部完成后，父任务右侧统计应显示满额进度");
        GuiTestSupport.assertTrue(renderer.isRowTitleStrikethroughForTest(parent.getId()), "进入已完成部分的父任务应显示删除线");
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
     * 验证展开态底部统计和折叠态摘要文本语义稳定。
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

        String expandedExpected = Component.translatableWithFallback("hud.todolist.summary.fixed", "Todo %s | Done %s", "2", "2").getString();
        GuiTestSupport.assertEquals(expandedExpected, renderer.getHiddenCountTextForTest(), "展开态底部应固定显示待办与已办总数");

        renderer.toggleExpanded();

        String collapsedExpected = Component.translatableWithFallback("hud.todolist.summary.with_completed", "Todo: %s | Done: %s", "2", "2").getString();
        GuiTestSupport.assertEquals(collapsedExpected, renderer.getCollapsedSummaryTextForTest(), "折叠态应保持原有摘要语义");
    }

    /**
     * 验证 HUD 底部和折叠态摘要统计只计算顶层任务，不把子任务计入任务数。
     */
    private static void shouldExcludeSubtasksFromHudSummaryCounts() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("TEAM_ALL");
        config.setHudProjectSource("ALL");
        config.setHudTodoLimit(6);
        config.setHudDoneLimit(6);
        config.setHudMaxHeight(400);
        config.setHudDefaultExpanded(true);

        Project project = createTeamProject("hud-summary-subtask", "HUD Summary Subtask");
        Task pendingParent = createTeamTask("Pending Parent", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.HIGH, false);
        Task pendingChild = createTeamTask("Pending Child", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.MEDIUM, false);
        pendingChild.setParentTaskId(pendingParent.getId());
        pendingChild.setSubtaskSortOrder(0L);
        Task doneParent = createTeamTask("Done Parent", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, false);
        Task doneChild = createTeamTask("Done Child", project.getId(), OWNER_ID.toString(), "owner", Task.Priority.LOW, true);
        doneChild.setParentTaskId(doneParent.getId());
        doneChild.setSubtaskSortOrder(0L);

        ops.getTeamTaskManager().addTask(pendingParent);
        ops.getTeamTaskManager().addTask(pendingChild);
        ops.getTeamTaskManager().addTask(doneParent);
        ops.getTeamTaskManager().addTask(doneChild);

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.refreshHudModelForTest();

        String expandedExpected = Component.translatableWithFallback("hud.todolist.summary.fixed", "Todo %s | Done %s", "1", "1").getString();
        GuiTestSupport.assertEquals(expandedExpected, renderer.getHiddenCountTextForTest(), "展开态底部统计不应把子任务计入任务数");

        renderer.toggleExpanded();

        String collapsedExpected = Component.translatableWithFallback("hud.todolist.summary.with_completed", "Todo: %s | Done: %s", "1", "1").getString();
        GuiTestSupport.assertEquals(collapsedExpected, renderer.getCollapsedSummaryTextForTest(), "折叠态摘要也不应把子任务计入任务数");
    }

    /**
     * 验证内容区高度紧张时，HUD 仍会预留一行显示底部固定统计。
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
        String expected = Component.translatableWithFallback("hud.todolist.summary.fixed", "Todo %s | Done %s", "9", "0").getString();
        GuiTestSupport.assertEquals(expected, renderer.getHiddenCountTextForTest(), "高度不足时仍应显示底部固定统计");
    }

    /**
     * 验证强制刷新会立刻清空当前缓存任务与任务行渲染缓存。
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
     * 验证个人项目 HUD 会跟随 GUI 推送的个人任务快照变化。
     */
    private static void shouldFollowGuiPersonalTaskSnapshot() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = createMinecraft();
        ModConfig config = ModConfig.getInstance();
        config.setHudDefaultView("PERSONAL");
        config.setHudProjectSource("CURRENT");

        Project project = createPersonalProject("hud-personal-gui", "HUD Personal GUI");
        ops.setActiveProjectId(project.getId());

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        renderer.syncPersonalTasksFromGui(List.of(createPersonalTask("GUI Pending A", project.getId(), false)));
        renderer.refreshHudModelForTest();
        GuiTestSupport.assertEquals(List.of("GUI Pending A"), taskTitles(renderer.getCachedPendingTasksForTest()), "HUD 应显示 GUI 推送的个人任务");

        renderer.syncPersonalTasksFromGui(List.of(createPersonalTask("GUI Pending B", project.getId(), false)));
        renderer.refreshHudModelForTest();
        GuiTestSupport.assertEquals(List.of("GUI Pending B"), taskTitles(renderer.getCachedPendingTasksForTest()), "HUD 应替换为 GUI 最新个人任务快照");

        renderer.syncPersonalTasksFromGui(List.of());
        renderer.refreshHudModelForTest();
        GuiTestSupport.assertEquals(List.of(), taskTitles(renderer.getCachedPendingTasksForTest()), "GUI 清空个人任务后 HUD 也应立即清空");
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
     * 创建个人项目并注册到全局项目管理器。
     *
     * @param id 项目 ID
     * @param name 项目名称
     * @return 新建的个人项目
     */
    private static Project createPersonalProject(String id, String name) {
        Project project = new Project(name, Project.Scope.PERSONAL, OWNER_ID.toString());
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
     * 创建一条个人项目任务。
     *
     * @param title 任务标题
     * @param projectId 所属项目 ID
     * @param completed 是否已完成
     * @return 配置完成的个人任务
     */
    private static Task createPersonalTask(String title, String projectId, boolean completed) {
        Task task = new Task(title, "");
        task.setScope(Task.Scope.PERSONAL);
        task.setProjectId(projectId);
        task.setCompleted(completed);
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
