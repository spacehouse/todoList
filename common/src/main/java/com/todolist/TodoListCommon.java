package com.todolist;

import com.todolist.config.ModConfig;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.project.ProjectStorage;
import com.todolist.project.Project;
import com.todolist.storage.H2ConnectionProvider;
import com.todolist.storage.H2StorageBootstrap;
import com.todolist.storage.H2TcpConfig;
import com.todolist.storage.H2TcpServerManager;
import com.todolist.storage.StorageBackendFactory;
import com.todolist.task.TaskStorage;

import java.io.IOException;
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
        ModConfig.getInstance();
        StorageBackendFactory.getConfiguredBackend();
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
        reloadProjectsFromStorage(true);
    }

    /**
     * 从 H2 数据库重新构建当前存储对象，并重载内存中的项目列表。
     *
     * @throws IOException H2 初始化或重载失败时抛出
     */
    public static void reloadH2StorageContextFromDatabase() throws IOException {
        H2StorageBootstrap.resetDatabaseState(new H2ConnectionProvider().getDatabaseBasePath());
        H2TcpServerManager.ensureStarted(H2TcpConfig.load());
        new H2StorageBootstrap().ensureReady();
        taskStorage = new TaskStorage();
        projectStorage = new ProjectStorage();
        if (projectManager == null) {
            projectManager = new ProjectManager();
        }
        reloadProjectsFromStorage(false);
    }

    /**
     * 从磁盘重新加载项目列表，可选择是否持久化缺省个人项目。
     *
     * @param persistDefaultPersonal 是否在缺少个人项目时回写默认项目
     */
    private static void reloadProjectsFromStorage(boolean persistDefaultPersonal) {
        if (projectStorage == null || projectManager == null) {
            return;
        }
        try {
            List<Project> personal = projectStorage.loadProjects();
            List<Project> team = projectStorage.loadTeamProjects();
            List<Project> reloadedProjects = new java.util.ArrayList<>();
            boolean hasPersonal = false;
            for (Project project : personal) {
                project.setName(ProjectNameFormatter.normalizeDefaultName(project.getName(), project.getScope()));
                reloadedProjects.add(project);
                if (project.getScope() == Project.Scope.PERSONAL) {
                    hasPersonal = true;
                }
            }
            if (!hasPersonal) {
                Project defaultPersonal = new Project(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_KEY, Project.Scope.PERSONAL, null);
                defaultPersonal.setId(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID);
                reloadedProjects.add(defaultPersonal);
                if (persistDefaultPersonal) {
                    projectStorage.saveProjects(filterProjectsByScope(reloadedProjects, Project.Scope.PERSONAL));
                }
            }
            for (Project project : team) {
                project.setName(ProjectNameFormatter.normalizeDefaultName(project.getName(), project.getScope()));
                reloadedProjects.add(project);
            }
            projectManager.clearAll();
            for (Project project : reloadedProjects) {
                projectManager.addProject(project);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to reload projects from storage", e);
        }
    }

    /**
     * 从项目列表中过滤出指定范围的项目。
     *
     * @param projects 原始项目列表
     * @param scope 目标范围
     * @return 过滤后的项目列表
     */
    private static List<Project> filterProjectsByScope(List<Project> projects, Project.Scope scope) {
        List<Project> filtered = new java.util.ArrayList<>();
        if (projects == null || scope == null) {
            return filtered;
        }
        for (Project project : projects) {
            if (project != null && project.getScope() == scope) {
                filtered.add(project);
            }
        }
        return filtered;
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

    /**
     * 清理当前存储上下文相关的运行期状态。
     * 当前用于服务端停止、客户端离开世界等生命周期边界，避免 H2 状态串到下一次启动。
     */
    public static void closeStorageContext() {
        try {
            if (StorageBackendFactory.isH2Selected()) {
                H2StorageBootstrap.resetDatabaseState(new H2ConnectionProvider().getDatabaseBasePath());
                H2TcpServerManager.stop();
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.warn("Failed to close TodoList storage context", e);
        }
    }
}
