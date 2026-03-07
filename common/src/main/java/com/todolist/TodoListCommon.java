package com.todolist;

import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.project.ProjectStorage;
import com.todolist.project.Project;
import com.todolist.task.TaskStorage;

import java.util.List;

/**
 * 模组通用逻辑类。
 * 包含全局存储实例的管理。
 */
public final class TodoListCommon {
    private static TaskStorage taskStorage;
    private static ProjectStorage projectStorage;
    private static ProjectManager projectManager;
    private static volatile boolean projectSyncInProgress;

    private TodoListCommon() {
    }

    /**
     * 初始化通用组件。
     */
    public static void init() {
        taskStorage = new TaskStorage();
        projectStorage = new ProjectStorage();
        projectManager = new ProjectManager();
    }

    /**
     * 获取任务存储实例。
     * @return TaskStorage 实例
     */
    public static TaskStorage getTaskStorage() {
        return taskStorage;
    }

    /**
     * 获取项目存储实例。
     * @return ProjectStorage 实例
     */
    public static ProjectStorage getProjectStorage() {
        return projectStorage;
    }

    /**
     * 获取项目管理器实例。
     * @return ProjectManager 实例
     */
    public static ProjectManager getProjectManager() {
        return projectManager;
    }

    /**
     * 从磁盘重新加载项目列表，用于在多人/单人切换时恢复本地项目数据。
     */
    public static void reloadProjectsFromStorage() {
        if (projectStorage == null || projectManager == null) {
            return;
        }
        projectManager.clearAll();
        try {
            List<Project> personal = projectStorage.loadProjects();
            boolean hasPersonal = false;
            for (Project project : personal) {
                project.setName(ProjectNameFormatter.normalizeDefaultName(project.getName(), project.getScope()));
                projectManager.addProject(project);
                if (project.getScope() == Project.Scope.PERSONAL) {
                    hasPersonal = true;
                }
            }
            if (!hasPersonal) {
                Project defaultPersonal = new Project(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_KEY, Project.Scope.PERSONAL, null);
                defaultPersonal.setId(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID);
                projectManager.addProject(defaultPersonal);
                projectStorage.saveProjects(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
            }
            List<Project> team = projectStorage.loadTeamProjects();
            for (Project project : team) {
                project.setName(ProjectNameFormatter.normalizeDefaultName(project.getName(), project.getScope()));
                projectManager.addProject(project);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to reload projects from storage", e);
        }
    }

    /**
     * 标记客户端项目同步是否处于进行中，用于避免 GUI 在同步过程中触发兜底创建逻辑。
     */
    public static void setProjectSyncInProgress(boolean syncing) {
        projectSyncInProgress = syncing;
    }

    /**
     * 获取客户端项目同步进行中标记。
     */
    public static boolean isProjectSyncInProgress() {
        return projectSyncInProgress;
    }
}
