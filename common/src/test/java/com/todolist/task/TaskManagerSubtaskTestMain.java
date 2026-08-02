package com.todolist.task;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.List;

/**
 * TaskManagerSubtaskTestMain 覆盖 Phase 4 的顶层计数与父任务完成状态聚合行为。
 */
public final class TaskManagerSubtaskTestMain {
    /**
     * 工具类不需要实例化。
     */
    private TaskManagerSubtaskTestMain() {
    }

    /**
     * 执行 TaskManager 子任务聚合测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldCountOnlyTopLevelTasksByProject", TaskManagerSubtaskTestMain::shouldCountOnlyTopLevelTasksByProject);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldAggregateParentCompletionFromChildren", TaskManagerSubtaskTestMain::shouldAggregateParentCompletionFromChildren);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldIgnoreManualParentToggleWhenChildrenExist", TaskManagerSubtaskTestMain::shouldIgnoreManualParentToggleWhenChildrenExist);
    }

    /**
     * 验证项目计数与完成分组默认只统计顶层任务。
     */
    private static void shouldCountOnlyTopLevelTasksByProject() {
        TaskManager manager = new TaskManager();
        Task parentPending = createTask("parent-pending", "project-a", false, null);
        Task childPending = createTask("child-pending", "project-a", false, parentPending.getId());
        Task parentDone = createTask("parent-done", "project-a", true, null);
        Task childDone = createTask("child-done", "project-a", true, parentDone.getId());
        Task otherProject = createTask("other-project", "project-b", false, null);
        addAll(manager, parentPending, childPending, parentDone, childDone, otherProject);

        GuiTestSupport.assertEquals(List.of(parentPending, parentDone), manager.getTasksByProject("project-a"), "项目任务列表应只包含顶层任务");
        GuiTestSupport.assertEquals(List.of(parentDone), manager.getCompletedTasks(), "已完成分组应只包含顶层任务");
        GuiTestSupport.assertEquals(List.of(parentPending, otherProject), manager.getIncompleteTasks(), "未完成分组应只包含顶层任务");
    }

    /**
     * 验证切换子任务完成状态时会自动聚合父任务状态。
     */
    private static void shouldAggregateParentCompletionFromChildren() {
        TaskManager manager = new TaskManager();
        Task parent = createTask("parent", "project-a", false, null);
        Task childA = createTask("child-a", "project-a", false, parent.getId());
        Task childB = createTask("child-b", "project-a", false, parent.getId());
        addAll(manager, parent, childA, childB);

        manager.toggleTaskCompletion(childA.getId());
        GuiTestSupport.assertFalse(manager.getTask(parent.getId()).isCompleted(), "只完成部分子任务时父任务不应被标记完成");

        manager.toggleTaskCompletion(childB.getId());
        GuiTestSupport.assertTrue(manager.getTask(parent.getId()).isCompleted(), "全部子任务完成后父任务应自动完成");

        manager.toggleTaskCompletion(childA.getId());
        GuiTestSupport.assertFalse(manager.getTask(parent.getId()).isCompleted(), "任一子任务恢复未完成后父任务应同步恢复未完成");
    }

    /**
     * 验证存在子任务的父任务不能被手动直接切换完成状态。
     */
    private static void shouldIgnoreManualParentToggleWhenChildrenExist() {
        TaskManager manager = new TaskManager();
        Task parent = createTask("parent", "project-a", false, null);
        Task child = createTask("child", "project-a", false, parent.getId());
        addAll(manager, parent, child);

        manager.toggleTaskCompletion(parent.getId());

        GuiTestSupport.assertFalse(manager.getTask(parent.getId()).isCompleted(), "存在子任务的父任务不应允许手动直接切完成");
        GuiTestSupport.assertFalse(manager.getTask(child.getId()).isCompleted(), "手动切父任务时不应连带改写子任务完成状态");
    }

    /**
     * 创建测试任务。
     *
     * @param title 标题
     * @param projectId 项目 ID
     * @param completed 是否完成
     * @param parentTaskId 父任务 ID
     * @return 测试任务
     */
    private static Task createTask(String title, String projectId, boolean completed, String parentTaskId) {
        Task task = new Task(title, "");
        task.setId(title + "-id");
        task.setProjectId(projectId);
        task.setCompleted(completed);
        task.setParentTaskId(parentTaskId);
        task.setScope(Task.Scope.TEAM);
        return task;
    }

    /**
     * 批量向管理器添加任务。
     *
     * @param manager 任务管理器
     * @param tasks 任务数组
     */
    private static void addAll(TaskManager manager, Task... tasks) {
        for (Task task : tasks) {
            manager.addTask(task);
        }
    }
}
