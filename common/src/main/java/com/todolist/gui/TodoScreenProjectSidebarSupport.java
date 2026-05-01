package com.todolist.gui;

import com.todolist.TodoConstants;
import com.todolist.client.ClientTaskStorageHelper;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.storage.H2TaskQueryService;
import com.todolist.storage.H2TaskStore;
import com.todolist.storage.StorageBackendFactory;
import com.todolist.task.TaskManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * TodoScreen 项目侧栏支持类，集中处理项目列表与任务计数构建。
 */
final class TodoScreenProjectSidebarSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenProjectSidebarSupport() {
    }

    static Map<String, Integer> buildSidebarProjectTaskCounts(List<Project> visibleProjects,
                                                              Project.Scope projectScopeFilter,
                                                              TaskManager personalTaskManager,
                                                              TaskManager teamTaskManager) {
        return buildSidebarProjectTaskCounts(visibleProjects, projectScopeFilter, personalTaskManager, teamTaskManager, null);
    }

    /**
     * 构建侧栏项目任务计数；H2 后端优先使用 SQL 聚合，NBT 后端保留原内存路径。
     *
     * @param visibleProjects 当前侧栏可见项目
     * @param projectScopeFilter 当前项目空间
     * @param personalTaskManager 个人任务管理器
     * @param teamTaskManager 团队任务管理器
     * @param minecraft 当前客户端实例
     * @return 项目 ID 到任务数量的映射
     */
    static Map<String, Integer> buildSidebarProjectTaskCounts(List<Project> visibleProjects,
                                                              Project.Scope projectScopeFilter,
                                                              TaskManager personalTaskManager,
                                                              TaskManager teamTaskManager,
                                                              Minecraft minecraft) {
        if (StorageBackendFactory.isH2Selected()) {
            try {
                return buildH2SidebarProjectTaskCounts(visibleProjects, projectScopeFilter, minecraft);
            } catch (Exception exception) {
                TodoConstants.LOGGER.warn("Failed to build H2 sidebar project task counts, falling back to memory snapshot", exception);
            }
        }
        Map<String, Integer> taskCounts = new HashMap<>();
        TaskManager countSource = projectScopeFilter == Project.Scope.TEAM ? teamTaskManager : personalTaskManager;
        if (countSource == null || visibleProjects == null || visibleProjects.isEmpty()) {
            return taskCounts;
        }
        for (Project project : visibleProjects) {
            if (project == null || project.getId() == null || project.getId().isEmpty()) {
                continue;
            }
            taskCounts.put(project.getId(), countSource.getTasksByProject(project.getId()).size());
        }
        return taskCounts;
    }

    /**
     * 通过 H2 SQL 聚合构建侧栏项目任务计数。
     *
     * @param visibleProjects 当前侧栏可见项目
     * @param projectScopeFilter 当前项目空间
     * @param minecraft 当前客户端实例
     * @return 项目 ID 到任务数量的映射
     * @throws Exception 查询失败时抛出
     */
    private static Map<String, Integer> buildH2SidebarProjectTaskCounts(List<Project> visibleProjects,
                                                                        Project.Scope projectScopeFilter,
                                                                        Minecraft minecraft) throws Exception {
        Set<String> projectIds = collectVisibleProjectIds(visibleProjects);
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        String bucketType = projectScopeFilter == Project.Scope.TEAM ? H2TaskStore.TEAM_BUCKET : H2TaskStore.LOCAL_PERSONAL_BUCKET;
        String ownerUuid = projectScopeFilter == Project.Scope.TEAM ? H2TaskStore.TEAM_OWNER : H2TaskStore.LOCAL_OWNER;
        if (projectScopeFilter == Project.Scope.PERSONAL
                && minecraft != null
                && minecraft.player != null
                && ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(minecraft)) {
            UUID storagePlayerUuid = ClientTaskStorageHelper.resolveStoragePlayerUuid(minecraft);
            bucketType = H2TaskStore.PLAYER_PERSONAL_BUCKET;
            ownerUuid = storagePlayerUuid == null ? "" : storagePlayerUuid.toString();
        }
        return new H2TaskQueryService().countTasksByProjectIds(bucketType, ownerUuid, projectIds);
    }

    /**
     * 提取侧栏可见项目 ID，并保持侧栏顺序。
     *
     * @param visibleProjects 当前侧栏可见项目
     * @return 非空项目 ID 集合
     */
    private static Set<String> collectVisibleProjectIds(List<Project> visibleProjects) {
        Set<String> projectIds = new LinkedHashSet<>();
        if (visibleProjects == null) {
            return projectIds;
        }
        for (Project project : visibleProjects) {
            if (project == null || project.getId() == null || project.getId().isEmpty()) {
                continue;
            }
            projectIds.add(project.getId());
        }
        return projectIds;
    }

    static List<Project> buildVisibleProjectsForSidebar(ProjectManager projectManager,
                                                        Project.Scope projectScopeFilter,
                                                        String projectSearchQuery,
                                                        String playerUuid) {
        if (projectManager == null || projectScopeFilter == null) {
            return List.of();
        }
        List<Project> all = new ArrayList<>();
        all.addAll(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
        all.addAll(projectManager.getProjectsByScope(Project.Scope.TEAM));

        Project defaultProject = null;
        for (Project project : all) {
            if (project == null || project.getScope() != projectScopeFilter) {
                continue;
            }
            if (projectScopeFilter == Project.Scope.PERSONAL && project.isDefaultPersonalProject()) {
                defaultProject = project;
                break;
            }
            if (projectScopeFilter == Project.Scope.TEAM && project.isDefaultTeamProject()) {
                defaultProject = project;
                break;
            }
        }

        String rawQuery = projectSearchQuery == null ? "" : projectSearchQuery.trim();
        List<Project> filtered = TodoScreenProjectSearchSupport.filterProjectsForSidebar(
                all,
                projectScopeFilter,
                projectSearchQuery,
                playerUuid
        );
        if (defaultProject != null
                && rawQuery.isEmpty()
                && !TodoScreenProjectSearchSupport.containsProjectId(filtered, defaultProject.getId())) {
            filtered.add(0, defaultProject);
        }
        return filtered;
    }
}
