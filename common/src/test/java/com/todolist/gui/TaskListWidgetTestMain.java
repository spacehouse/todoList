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
