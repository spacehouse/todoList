package com.todolist.gui;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.util.List;

/**
 * TodoScreenTaskSupportTestMain 覆盖搜索命中子任务时的父任务上下文保留逻辑。
 */
public final class TodoScreenTaskSupportTestMain {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenTaskSupportTestMain() {
    }

    /**
     * 执行 TodoScreenTaskSupport 相关自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TodoScreenTaskSupportTestMain.shouldKeepParentVisibleWhenSearchMatchesSubtask", TodoScreenTaskSupportTestMain::shouldKeepParentVisibleWhenSearchMatchesSubtask);
    }

    /**
     * 验证搜索命中子任务时，即使父任务本身不命中当前筛选，也应返回父任务以保留上下文。
     */
    private static void shouldKeepParentVisibleWhenSearchMatchesSubtask() {
        TaskManager manager = new TaskManager();
        Task parent = createTask("Parent Wrapper", "project-a", false, null);
        parent.setPriority(Task.Priority.LOW);
        Task child = createTask("Child Search Hit", "project-a", false, parent.getId());
        child.setPriority(Task.Priority.HIGH);
        child.setAssigneeUuid("player-a");
        child.addTag("中文子命中");
        Task unrelated = createTask("Other Task", "project-a", false, null);
        unrelated.setPriority(Task.Priority.HIGH);
        unrelated.setAssigneeUuid("player-b");
        addAll(manager, parent, child, unrelated);

        List<Task> withoutSearch = TodoScreenTaskSupport.buildVisibleTasksForCurrentView(
                manager,
                "project-a",
                false,
                1,
                "TEAM_ASSIGNED",
                "player-a",
                ""
        );
        List<Task> withSearch = TodoScreenTaskSupport.buildVisibleTasksForCurrentView(
                manager,
                "project-a",
                false,
                1,
                "TEAM_ASSIGNED",
                "player-a",
                "中文子"
        );

        GuiTestSupport.assertEquals(List.of(), withoutSearch, "无搜索词时不应因为子任务条件把父任务直接混入结果");
        GuiTestSupport.assertEquals(List.of(parent), withSearch, "搜索命中子任务时应返回父任务以保留上下文");
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
