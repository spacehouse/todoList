package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.task.TaskManager;

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
