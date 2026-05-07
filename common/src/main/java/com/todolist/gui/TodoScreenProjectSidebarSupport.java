package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.task.TaskManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 构建侧栏项目任务计数，使用当前界面内存快照避免打开 GUI 时同步查询数据库。
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
