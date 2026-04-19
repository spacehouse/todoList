package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.project.ProjectManager;

import java.util.List;

/**
 * TodoScreen 项目偏好支持类，集中处理按空间选择优先项目逻辑。
 */
final class TodoScreenProjectPreferenceSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenProjectPreferenceSupport() {
    }

    static Project getPreferredProjectForScope(ProjectManager projectManager,
                                               Project.Scope scope,
                                               boolean teamProjectsEnabled,
                                               String preferredTeamProjectId,
                                               String preferredPersonalProjectId) {
        if (scope == null || projectManager == null) {
            return null;
        }
        if (scope == Project.Scope.TEAM && !teamProjectsEnabled) {
            return null;
        }
        String preferredId = scope == Project.Scope.TEAM ? preferredTeamProjectId : preferredPersonalProjectId;
        if (preferredId != null && !preferredId.isEmpty()) {
            Project preferred = projectManager.getProject(preferredId);
            if (preferred != null && preferred.getScope() == scope) {
                return preferred;
            }
        }
        List<Project> projects = projectManager.getProjectsByScope(scope);
        if (projects.isEmpty()) {
            return null;
        }
        for (Project project : projects) {
            if (scope == Project.Scope.PERSONAL && project.isDefaultPersonalProject()) {
                return project;
            }
            if (scope == Project.Scope.TEAM && project.isDefaultTeamProject()) {
                return project;
            }
        }
        return projects.get(0);
    }

    static Project resolvePreferredProjectForCurrentScope(ProjectManager projectManager,
                                                          Project.Scope projectScopeFilter,
                                                          boolean teamProjectsEnabled,
                                                          String preferredTeamProjectId,
                                                          String preferredPersonalProjectId) {
        Project preferred = getPreferredProjectForScope(
                projectManager,
                projectScopeFilter,
                teamProjectsEnabled,
                preferredTeamProjectId,
                preferredPersonalProjectId
        );
        if (preferred != null) {
            return preferred;
        }
        Project.Scope fallbackScope = projectScopeFilter == Project.Scope.PERSONAL
                ? Project.Scope.TEAM
                : Project.Scope.PERSONAL;
        return getPreferredProjectForScope(
                projectManager,
                fallbackScope,
                teamProjectsEnabled,
                preferredTeamProjectId,
                preferredPersonalProjectId
        );
    }

    static Project resolveFallbackProjectAfterRemoval(ProjectManager projectManager,
                                                      Project removedProject,
                                                      Project.Scope projectScopeFilter,
                                                      boolean teamProjectsEnabled,
                                                      String preferredTeamProjectId,
                                                      String preferredPersonalProjectId) {
        Project preferred = resolvePreferredProjectForCurrentScope(
                projectManager,
                projectScopeFilter,
                teamProjectsEnabled,
                preferredTeamProjectId,
                preferredPersonalProjectId
        );
        if (preferred != null) {
            return preferred;
        }
        if (removedProject != null) {
            Project.Scope fallbackScope = removedProject.getScope() == Project.Scope.PERSONAL
                    ? Project.Scope.TEAM
                    : Project.Scope.PERSONAL;
            return getPreferredProjectForScope(
                    projectManager,
                    fallbackScope,
                    teamProjectsEnabled,
                    preferredTeamProjectId,
                    preferredPersonalProjectId
            );
        }
        return null;
    }

    /**
     * 更新个人/团队偏好项目 ID。返回数组顺序为：个人、团队。
     */
    static String[] rememberSelectedProject(Project project, String preferredPersonalProjectId, String preferredTeamProjectId) {
        if (project == null || project.getId() == null || project.getId().isEmpty()) {
            return new String[] {preferredPersonalProjectId, preferredTeamProjectId};
        }
        if (project.getScope() == Project.Scope.TEAM) {
            return new String[] {preferredPersonalProjectId, project.getId()};
        }
        return new String[] {project.getId(), preferredTeamProjectId};
    }
}
