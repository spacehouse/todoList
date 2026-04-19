package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.task.TaskManager;

/**
 * TodoScreen 项目清理支持类，集中处理项目删除后的任务清理。
 */
final class TodoScreenProjectCleanupSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenProjectCleanupSupport() {
    }

    static void hardDeleteTasksForDeletedProject(TaskManager personalTaskManager,
                                                 TaskManager teamTaskManager,
                                                 Project deletedProject) {
        if (deletedProject == null || deletedProject.getId() == null || deletedProject.getId().isEmpty()) {
            return;
        }
        String deletedProjectId = deletedProject.getId();
        hardDeleteProjectTasksInManager(personalTaskManager, deletedProjectId);
        hardDeleteProjectTasksInManager(teamTaskManager, deletedProjectId);
    }

    private static void hardDeleteProjectTasksInManager(TaskManager manager, String deletedProjectId) {
        if (manager == null || deletedProjectId == null || deletedProjectId.isEmpty()) {
            return;
        }
        manager.deleteTasksByProjectId(deletedProjectId);
    }
}
