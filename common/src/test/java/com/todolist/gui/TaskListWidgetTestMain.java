package com.todolist.gui;

import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务列表组件离线自测入口：覆盖选中保持、滚动可见性、复选框点击和坐标命中等核心行为。
 */
public final class TaskListWidgetTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private TaskListWidgetTestMain() {
    }

    /**
     * 程序入口，串行执行任务列表组件的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldKeepSelectionAfterResettingTasks", TaskListWidgetTestMain::shouldKeepSelectionAfterResettingTasks);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldScrollSelectedTaskIntoView", TaskListWidgetTestMain::shouldScrollSelectedTaskIntoView);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldToggleCompletionWhenClickingCheckbox", TaskListWidgetTestMain::shouldToggleCompletionWhenClickingCheckbox);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldToggleCompletedTaskWhenClickingCheckbox", TaskListWidgetTestMain::shouldToggleCompletedTaskWhenClickingCheckbox);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldBlockToggleWhenNonOpTeamAllViewEnabled", TaskListWidgetTestMain::shouldBlockToggleWhenNonOpTeamAllViewEnabled);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldReturnTaskByCoordinates", TaskListWidgetTestMain::shouldReturnTaskByCoordinates);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldRenderPriorityColorBlockInsteadOfPriorityText", TaskListWidgetTestMain::shouldRenderPriorityColorBlockInsteadOfPriorityText);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldPlaceTagsBeforeTaskTitle", TaskListWidgetTestMain::shouldPlaceTagsBeforeTaskTitle);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldReturnTaskSectionByCoordinatesWhenCompletedSectionExpanded", TaskListWidgetTestMain::shouldReturnTaskSectionByCoordinatesWhenCompletedSectionExpanded);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldRenderSectionHeadersWithExpandMarkers", TaskListWidgetTestMain::shouldRenderSectionHeadersWithExpandMarkers);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldKeepScrollOffsetInsideScrollableTaskArea", TaskListWidgetTestMain::shouldKeepScrollOffsetInsideScrollableTaskArea);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldKeepCompletedRowsSeparatedFromActiveRows", TaskListWidgetTestMain::shouldKeepCompletedRowsSeparatedFromActiveRows);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldHideActiveTasksWhenSectionCollapsed", TaskListWidgetTestMain::shouldHideActiveTasksWhenSectionCollapsed);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldStartDraggingOnlyForActiveTasks", TaskListWidgetTestMain::shouldStartDraggingOnlyForActiveTasks);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldUpdateDropTargetWhileDragging", TaskListWidgetTestMain::shouldUpdateDropTargetWhileDragging);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldReorderOnlyCurrentVisibleActiveTasks", TaskListWidgetTestMain::shouldReorderOnlyCurrentVisibleActiveTasks);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldKeepCompletedSectionUnchangedAfterReorder", TaskListWidgetTestMain::shouldKeepCompletedSectionUnchangedAfterReorder);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldAutoScrollWhenDraggingNearListEdge", TaskListWidgetTestMain::shouldAutoScrollWhenDraggingNearListEdge);
    }

    /**
     * 校验重新设置任务列表后仍会按任务 ID 保持选中态。
     */
    private static void shouldKeepSelectionAfterResettingTasks() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task alpha = createTask("task-alpha", "Alpha");
        Task beta = createTask("task-beta", "Beta");
        widget.setTasks(new ArrayList<>(List.of(alpha, beta)));
        widget.setSelectedTask(beta);

        widget.setTasks(new ArrayList<>(List.of(beta, alpha)));

        GuiTestSupport.assertEquals(beta.getId(), widget.getSelectedTaskIdForTest(), "重新设置任务后应按任务 ID 保持选中项");
    }

    /**
     * 校验 ensureVisible 会把目标任务滚动到可视区域。
     */
    private static void shouldScrollSelectedTaskIntoView() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 50);
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            tasks.add(createTask("task-" + index, "Task " + index));
        }
        widget.setTasks(tasks);

        widget.ensureVisible(tasks.get(5));

        GuiTestSupport.assertTrue(widget.getScrollOffsetForTest() > 0, "ensureVisible 应滚动到目标任务所在区域");
    }

    /**
     * 校验点击复选框会触发完成状态切换回调。
     */
    private static void shouldToggleCompletionWhenClickingCheckbox() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task alpha = createTask("task-alpha", "Alpha");
        widget.setTasks(List.of(alpha));
        AtomicReference<String> toggledId = new AtomicReference<>();
        widget.setOnTaskToggleCompletion(task -> toggledId.set(task.getId()));

        boolean handled = widget.mouseClicked(widget.getCheckboxCenterXForTest(), widget.getCheckboxCenterYForTest(), 0);

        GuiTestSupport.assertTrue(handled, "点击复选框区域应被组件处理");
        GuiTestSupport.assertEquals(alpha.getId(), toggledId.get(), "点击复选框应触发完成状态切换回调");
    }

    /**
     * 校验非 OP 在 TEAM_ALL 视图下点击复选框不会触发完成切换。
     */
    /**
     * 鏍￠獙鐐瑰嚮宸插畬鎴愪换鍔＄殑澶嶉€夋鍚庝篃浼氳Е鍙戠姸鎬佸垏鎹㈠洖璋冦€?
     */
    private static void shouldToggleCompletedTaskWhenClickingCheckbox() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setTasks(List.of(done));
        AtomicReference<String> toggledId = new AtomicReference<>();
        widget.setOnTaskToggleCompletion(task -> toggledId.set(task.getId()));

        boolean handled = widget.mouseClicked(widget.getCheckboxCenterXForTest(), widget.getCheckboxCenterYForTest(), 0);

        GuiTestSupport.assertTrue(handled, "点击已完成任务的复选框区域应被组件处理");
        GuiTestSupport.assertEquals(done.getId(), toggledId.get(), "已完成任务点击复选框后也应触发切换回调");
    }

    private static void shouldBlockToggleWhenNonOpTeamAllViewEnabled() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task alpha = createTask("task-alpha", "Alpha");
        widget.setTasks(List.of(alpha));
        widget.setTeamAllViewForNonOp(true);
        AtomicReference<String> toggledId = new AtomicReference<>();
        widget.setOnTaskToggleCompletion(task -> toggledId.set(task.getId()));

        widget.mouseClicked(widget.getCheckboxCenterXForTest(), widget.getCheckboxCenterYForTest(), 0);

        GuiTestSupport.assertNull(toggledId.get(), "非 OP 的 TEAM_ALL 视图不应允许点击复选框完成任务");
    }

    /**
     * 校验通过坐标可定位到正确的任务对象。
     */
    private static void shouldReturnTaskByCoordinates() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task alpha = createTask("task-alpha", "Alpha");
        Task beta = createTask("task-beta", "Beta");
        widget.setTasks(List.of(alpha, beta));

        Task task = widget.getTaskAt(30, 30);

        GuiTestSupport.assertEquals(beta.getId(), task.getId(), "坐标命中应返回对应行的任务对象");
    }

    /**
     * 校验任务行仍通过优先级色块表达优先级，而不是退回文本标签。
     */
    private static void shouldRenderPriorityColorBlockInsteadOfPriorityText() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task alpha = createTask("task-alpha", "Alpha");
        widget.setTasks(List.of(alpha));

        GuiTestSupport.assertTrue(widget.hasPriorityColorBlockForTaskForTest(alpha.getId()), "任务行应保留优先级色块表达");
    }

    /**
     * 校验已完成分组展开后，可以通过坐标准确识别标题行和任务行所属分段。
     */
    /**
     * 校验任务标签会以前置元信息的形式显示在标题之前。
     */
    private static void shouldPlaceTagsBeforeTaskTitle() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task alpha = createTask("task-alpha", "Alpha");
        alpha.setTags(List.of("UI", "Bug"));
        widget.setTasks(List.of(alpha));

        GuiTestSupport.assertEquals("[UI] [Bug]", widget.getTaskLeadingMetaTextForTest(alpha.getId()), "任务标签应显示在任务标题之前");
    }

    /**
     * 校验已完成分组展开后，可以通过坐标准确识别标题行和任务行所属分段。
     */
    private static void shouldReturnTaskSectionByCoordinatesWhenCompletedSectionExpanded() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成（1）", List.of(alpha), true, true),
                new TaskListWidget.SectionModel("completed", "已完成（1）", List.of(done), true, true)
        ));
        int activeHeaderY = widget.getSectionHeaderCenterYForTest("active");
        int activeTaskY = widget.getTaskRowCenterYForTest(alpha.getId());
        int completedHeaderY = widget.getSectionHeaderCenterYForTest("completed");
        int completedTaskY = widget.getTaskRowCenterYForTest(done.getId());

        TaskListWidget.TaskSectionHitResult activeHeader = widget.getSectionAt(20, activeHeaderY);
        TaskListWidget.TaskSectionHitResult activeTask = widget.getSectionAt(20, activeTaskY);
        TaskListWidget.TaskSectionHitResult completedHeader = widget.getSectionAt(20, completedHeaderY);
        TaskListWidget.TaskSectionHitResult completedTask = widget.getSectionAt(20, completedTaskY);

        GuiTestSupport.assertEquals(TaskListWidget.RowType.SECTION_HEADER, activeHeader.getRowType(), "第一行应命中未完成分组标题");
        GuiTestSupport.assertEquals("active", activeHeader.getSectionId(), "第一行应属于未完成分组");
        GuiTestSupport.assertEquals(TaskListWidget.RowType.TASK, activeTask.getRowType(), "第二行应命中未完成任务");
        GuiTestSupport.assertEquals(alpha.getId(), activeTask.getTask().getId(), "第二行应返回未完成任务");
        GuiTestSupport.assertEquals(TaskListWidget.RowType.SECTION_HEADER, completedHeader.getRowType(), "第三行应命中已完成分组标题");
        GuiTestSupport.assertEquals("completed", completedHeader.getSectionId(), "第三行应属于已完成分组");
        GuiTestSupport.assertEquals(TaskListWidget.RowType.TASK, completedTask.getRowType(), "第四行应命中已完成任务");
        GuiTestSupport.assertEquals(done.getId(), completedTask.getTask().getId(), "第四行应返回已完成任务");
    }

    /**
     * 校验分组标题会统一带上展开/收起标记与数量文本。
     */
    private static void shouldRenderSectionHeadersWithExpandMarkers() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成（1）", List.of(alpha), true, true),
                new TaskListWidget.SectionModel("completed", "已完成（1）", List.of(done), true, false)
        ));

        GuiTestSupport.assertEquals(
                List.of("HEADER:v 未完成（1）", "TASK:task-alpha", "HEADER:> 已完成（1）"),
                widget.getRowDebugSnapshotForTest(),
                "分组标题快照应包含展开/收起标记与数量文本"
        );
    }

    /**
     * 校验滚动行为会被限制在任务滚动区内，不会超过可滚动范围。
     */
    private static void shouldKeepScrollOffsetInsideScrollableTaskArea() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            tasks.add(createTask("task-" + index, "Task " + index));
        }
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成（8）", tasks, true, true)
        ));

        GuiTestSupport.assertTrue(widget.isTaskAreaScrollableForTest(), "任务超出可视范围时应进入可滚动状态");
        for (int index = 0; index < 20; index++) {
            widget.mouseScrolled(10, 10, 0, -1);
        }

        GuiTestSupport.assertTrue(widget.getScrollOffsetForTest() >= 0, "滚动偏移不应小于 0");
        GuiTestSupport.assertTrue(widget.getScrollOffsetForTest() <= widget.getMaxScrollOffsetForTest(), "滚动偏移不应超过可滚动上限");
    }

    /**
     * 校验已完成分组与未完成分组在渲染行顺序上保持分离。
     */
    private static void shouldKeepCompletedRowsSeparatedFromActiveRows() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task beta = createTask("task-beta", "Beta");
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成（2）", List.of(alpha, beta), true, true),
                new TaskListWidget.SectionModel("completed", "已完成（1）", List.of(done), true, true)
        ));

        GuiTestSupport.assertEquals(
                List.of("HEADER:v 未完成（2）", "TASK:task-alpha", "TASK:task-beta", "HEADER:v 已完成（1）", "TASK:task-done"),
                widget.getRowDebugSnapshotForTest(),
                "已完成分组应始终位于未完成分组之后，且不能与未完成任务混排"
        );
    }

    /**
     * 校验未完成分组收起后，不会继续渲染未完成任务行。
     */
    private static void shouldHideActiveTasksWhenSectionCollapsed() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task beta = createTask("task-beta", "Beta");
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成（2）", List.of(alpha, beta), true, false),
                new TaskListWidget.SectionModel("completed", "已完成（1）", List.of(done), true, true)
        ));

        GuiTestSupport.assertEquals(
                List.of("HEADER:> 未完成（2）", "HEADER:v 已完成（1）", "TASK:task-done"),
                widget.getRowDebugSnapshotForTest(),
                "未完成分组收起后不应继续渲染未完成任务"
        );
    }

    /**
     * 创建测试任务对象，减少重复样板代码。
     *
     * @param id 任务 ID
     * @param title 任务标题
     * @return 测试任务对象
     */
    /**
     * 校验仅未完成任务允许进入拖拽态，已完成任务不能作为拖拽起点。
     */
    private static void shouldStartDraggingOnlyForActiveTasks() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task active = createTask("task-active", "Active");
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成（1）", List.of(active), true, true),
                new TaskListWidget.SectionModel("completed", "已完成（1）", List.of(done), true, true)
        ));
        widget.setOnTaskReorder(tasks -> {
        });

        int interactX = widget.getInteractXForTest();
        int activeY = widget.getTaskRowCenterYForTest(active.getId());
        widget.mouseClicked(interactX, activeY, 0);
        widget.mouseDragged(interactX, activeY + widget.getTaskItemHeightForTest(), 0, 0, widget.getTaskItemHeightForTest());
        GuiTestSupport.assertTrue(widget.isTaskDraggingForTest(), "未完成任务应允许进入拖拽态");
        widget.mouseReleased(interactX, activeY + widget.getTaskItemHeightForTest(), 0);

        int doneY = widget.getTaskRowCenterYForTest(done.getId());
        widget.mouseClicked(interactX, doneY, 0);
        widget.mouseDragged(interactX, doneY - widget.getTaskItemHeightForTest(), 0, 0, -widget.getTaskItemHeightForTest());
        GuiTestSupport.assertFalse(widget.isTaskDraggingForTest(), "已完成任务不应允许进入拖拽态");
    }

    /**
     * 校验拖拽过程中会持续更新当前落点索引。
     */
    private static void shouldUpdateDropTargetWhileDragging() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task beta = createTask("task-beta", "Beta");
        Task gamma = createTask("task-gamma", "Gamma");
        widget.setSections(List.of(new TaskListWidget.SectionModel("active", "未完成（3）", List.of(alpha, beta, gamma), true, true)));
        widget.setOnTaskReorder(tasks -> {
        });

        int interactX = widget.getInteractXForTest();
        int startY = widget.getTaskRowCenterYForTest(alpha.getId());
        int targetY = widget.getTaskRowCenterYForTest(gamma.getId()) + widget.getTaskItemHeightForTest() / 2;

        widget.mouseClicked(interactX, startY, 0);
        widget.mouseDragged(interactX, targetY, 0, 0, targetY - startY);

        GuiTestSupport.assertTrue(widget.isTaskDraggingForTest(), "拖拽移动超过阈值后应进入拖拽态");
        GuiTestSupport.assertEquals(3, widget.getDropTargetIndexForTest(), "拖拽到列表底部时应更新为末尾落点索引");
    }

    /**
     * 校验拖拽释放后仅对当前可见未完成任务列表输出重排结果。
     */
    private static void shouldReorderOnlyCurrentVisibleActiveTasks() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task gamma = createTask("task-gamma", "Gamma");
        AtomicReference<List<String>> reorderedIds = new AtomicReference<>(List.of());
        widget.setSections(List.of(new TaskListWidget.SectionModel("active", "未完成（2）", List.of(alpha, gamma), true, true)));
        widget.setOnTaskReorder(tasks -> reorderedIds.set(tasks.stream().map(Task::getId).toList()));

        int interactX = widget.getInteractXForTest();
        int startY = widget.getTaskRowCenterYForTest(gamma.getId());
        int targetY = widget.getTaskRowCenterYForTest(alpha.getId()) - widget.getTaskItemHeightForTest() / 2;

        widget.mouseClicked(interactX, startY, 0);
        widget.mouseDragged(interactX, targetY, 0, 0, targetY - startY);
        widget.mouseReleased(interactX, targetY, 0);

        GuiTestSupport.assertEquals(List.of("task-gamma", "task-alpha"), reorderedIds.get(), "释放拖拽后应输出当前可见未完成任务的新顺序");
    }

    /**
     * 校验重排未完成分组时不会破坏已完成分组的结构和顺序。
     */
    private static void shouldKeepCompletedSectionUnchangedAfterReorder() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task beta = createTask("task-beta", "Beta");
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成（2）", List.of(alpha, beta), true, true),
                new TaskListWidget.SectionModel("completed", "已完成（1）", List.of(done), true, true)
        ));
        widget.setOnTaskReorder(tasks -> {
        });

        int interactX = widget.getInteractXForTest();
        int startY = widget.getTaskRowCenterYForTest(beta.getId());
        int targetY = widget.getTaskRowCenterYForTest(alpha.getId()) - widget.getTaskItemHeightForTest() / 2;

        widget.mouseClicked(interactX, startY, 0);
        widget.mouseDragged(interactX, targetY, 0, 0, targetY - startY);
        widget.mouseReleased(interactX, targetY, 0);

        GuiTestSupport.assertEquals(
                List.of("HEADER:v 未完成（2）", "TASK:task-beta", "TASK:task-alpha", "HEADER:v 已完成（1）", "TASK:task-done"),
                widget.getRowDebugSnapshotForTest(),
                "重排未完成分组后应保持已完成分组原样不变"
        );
    }

    /**
     * 校验拖拽靠近列表边缘时会触发自动滚动。
     */
    private static void shouldAutoScrollWhenDraggingNearListEdge() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            tasks.add(createTask("task-" + index, "Task " + index));
        }
        widget.setSections(List.of(new TaskListWidget.SectionModel("active", "未完成（8）", tasks, true, true)));
        widget.setOnTaskReorder(reordered -> {
        });

        int interactX = widget.getInteractXForTest();
        int startY = widget.getTaskRowCenterYForTest(tasks.get(0).getId());
        int targetY = widget.getHeight() - 1;

        widget.mouseClicked(interactX, startY, 0);
        widget.mouseDragged(interactX, targetY, 0, 0, targetY - startY);

        GuiTestSupport.assertTrue(widget.getScrollOffsetForTest() > 0, "拖拽靠近底部边缘时应触发自动向下滚动");
    }

    /**
     * 创建测试任务对象，减少重复样板代码。
     *
     * @param id 任务 ID
     * @param title 任务标题
     * @return 测试任务对象
     */
    private static Task createTask(String id, String title) {
        Task task = new Task(title, "");
        task.setId(id);
        return task;
    }
}
