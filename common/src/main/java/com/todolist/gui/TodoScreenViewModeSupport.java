package com.todolist.gui;

import com.todolist.project.Project;

import java.util.List;
import java.util.Locale;

/**
 * TodoScreen 视图模式支持类，集中处理新旧视图模式映射与配置解析。
 */
final class TodoScreenViewModeSupport {
    static final class HudViewState {
        final String spaceModeName;
        final String taskViewOptionName;
        final String legacyViewModeName;
        final String hudDefaultViewToSave;

        HudViewState(String spaceModeName, String taskViewOptionName, String legacyViewModeName, String hudDefaultViewToSave) {
            this.spaceModeName = spaceModeName;
            this.taskViewOptionName = taskViewOptionName;
            this.legacyViewModeName = legacyViewModeName;
            this.hudDefaultViewToSave = hudDefaultViewToSave;
        }
    }

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenViewModeSupport() {
    }

    static String resolveDefaultTaskViewOptionName(String spaceModeName) {
        return "TEAM".equals(spaceModeName) ? "UNASSIGNED" : "MY";
    }

    static String resolveSpaceModeName(Project project, boolean teamProjectsEnabled) {
        if (project == null || project.getScope() == Project.Scope.PERSONAL || !teamProjectsEnabled) {
            return "PERSONAL";
        }
        return "TEAM";
    }

    static List<String> getVisibleTaskViewOptionNames(String spaceModeName) {
        if ("TEAM".equals(spaceModeName)) {
            return List.of("UNASSIGNED", "ALL", "MY");
        }
        return List.of("MY");
    }

    static String resolveTaskViewOptionNameFromLegacyViewModeName(String legacyViewModeName) {
        if ("TEAM_UNASSIGNED".equals(legacyViewModeName)) {
            return "UNASSIGNED";
        }
        if ("TEAM_ALL".equals(legacyViewModeName)) {
            return "ALL";
        }
        return "MY";
    }

    static String resolveLegacyViewModeName(String spaceModeName, String taskViewOptionName) {
        if ("PERSONAL".equals(spaceModeName)) {
            return "PERSONAL";
        }
        if ("ALL".equals(taskViewOptionName)) {
            return "TEAM_ALL";
        }
        if ("UNASSIGNED".equals(taskViewOptionName)) {
            return "TEAM_UNASSIGNED";
        }
        return "TEAM_ASSIGNED";
    }

    static String resolveConfigLegacyViewModeName(String rawHudDefaultView) {
        String normalizedView = rawHudDefaultView == null ? "" : rawHudDefaultView.trim().toUpperCase(Locale.ROOT);
        if ("TEAM_UNASSIGNED".equals(normalizedView)) {
            return "TEAM_UNASSIGNED";
        }
        if ("TEAM_ALL".equals(normalizedView)) {
            return "TEAM_ALL";
        }
        if ("TEAM_ASSIGNED".equals(normalizedView)) {
            return "TEAM_ASSIGNED";
        }
        return "PERSONAL";
    }

    static HudViewState resolveHudViewState(Project project, boolean teamProjectsEnabled, String rawHudDefaultView) {
        String spaceModeName = resolveSpaceModeName(project, teamProjectsEnabled);
        if ("PERSONAL".equals(spaceModeName)) {
            return new HudViewState("PERSONAL", "MY", "PERSONAL", "PERSONAL");
        }
        String configLegacyViewModeName = resolveConfigLegacyViewModeName(rawHudDefaultView);
        String taskViewOptionName = "PERSONAL".equals(configLegacyViewModeName)
                ? resolveDefaultTaskViewOptionName(spaceModeName)
                : resolveTaskViewOptionNameFromLegacyViewModeName(configLegacyViewModeName);
        if (!getVisibleTaskViewOptionNames(spaceModeName).contains(taskViewOptionName)) {
            taskViewOptionName = resolveDefaultTaskViewOptionName(spaceModeName);
        }
        String legacyViewModeName = resolveLegacyViewModeName(spaceModeName, taskViewOptionName);
        return new HudViewState(spaceModeName, taskViewOptionName, legacyViewModeName, legacyViewModeName);
    }

    static boolean isAddTaskAllowedInCurrentView(String viewModeName) {
        return "PERSONAL".equals(viewModeName)
                || "TEAM_UNASSIGNED".equals(viewModeName)
                || "TEAM_ALL".equals(viewModeName);
    }

}
