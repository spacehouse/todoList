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
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldBlockToggleWhenNonOpTeamAllViewEnabled", TaskListWidgetTestMain::shouldBlockToggleWhenNonOpTeamAllViewEnabled);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldReturnTaskByCoordinates", TaskListWidgetTestMain::shouldReturnTaskByCoordinates);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldRenderPriorityColorBlockInsteadOfPriorityText", TaskListWidgetTestMain::shouldRenderPriorityColorBlockInsteadOfPriorityText);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldReturnTaskSectionByCoordinatesWhenCompletedSectionExpanded", TaskListWidgetTestMain::shouldReturnTaskSectionByCoordinatesWhenCompletedSectionExpanded);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldKeepScrollOffsetInsideScrollableTaskArea", TaskListWidgetTestMain::shouldKeepScrollOffsetInsideScrollableTaskArea);
        GuiTestSupport.runTestCase("TaskListWidgetTestMain.shouldKeepCompletedRowsSeparatedFromActiveRows", TaskListWidgetTestMain::shouldKeepCompletedRowsSeparatedFromActiveRows);
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

        boolean handled = widget.mouseClicked(13, 6, 0);

        GuiTestSupport.assertTrue(handled, "点击复选框区域应被组件处理");
        GuiTestSupport.assertEquals(alpha.getId(), toggledId.get(), "点击复选框应触发完成状态切换回调");
    }

    /**
     * 校验非 OP 在 TEAM_ALL 视图下点击复选框不会触发完成切换。
     */
    private static void shouldBlockToggleWhenNonOpTeamAllViewEnabled() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 60);
        Task alpha = createTask("task-alpha", "Alpha");
        widget.setTasks(List.of(alpha));
        widget.setTeamAllViewForNonOp(true);
        AtomicReference<String> toggledId = new AtomicReference<>();
        widget.setOnTaskToggleCompletion(task -> toggledId.set(task.getId()));

        widget.mouseClicked(13, 6, 0);

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
    private static void shouldReturnTaskSectionByCoordinatesWhenCompletedSectionExpanded() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        TaskListWidget widget = new TaskListWidget(minecraft, 0, 0, 220, 120);
        Task alpha = createTask("task-alpha", "Alpha");
        Task done = createTask("task-done", "Done");
        done.setCompleted(true);
        widget.setSections(List.of(
                new TaskListWidget.SectionModel("active", "未完成", List.of(alpha), false, true),
                new TaskListWidget.SectionModel("completed", "已完成 1 项", List.of(done), true, true)
        ));
        int rowHeight = widget.getTaskItemHeightForTest();

        TaskListWidget.TaskSectionHitResult activeHeader = widget.getSectionAt(20, rowHeight / 2.0);
        TaskListWidget.TaskSectionHitResult activeTask = widget.getSectionAt(20, rowHeight + rowHeight / 2.0);
        TaskListWidget.TaskSectionHitResult completedHeader = widget.getSectionAt(20, rowHeight * 2 + rowHeight / 2.0);
        TaskListWidget.TaskSectionHitResult completedTask = widget.getSectionAt(20, rowHeight * 3 + rowHeight / 2.0);

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
                new TaskListWidget.SectionModel("active", "未完成", tasks, false, true)
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
                new TaskListWidget.SectionModel("active", "未完成", List.of(alpha, beta), false, true),
                new TaskListWidget.SectionModel("completed", "已完成 1 项", List.of(done), true, true)
        ));

        GuiTestSupport.assertEquals(
                List.of("HEADER:未完成", "TASK:task-alpha", "TASK:task-beta", "HEADER:已完成 1 项", "TASK:task-done"),
                widget.getRowDebugSnapshotForTest(),
                "已完成分组应始终位于未完成分组之后，且不能与未完成任务混排"
        );
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
