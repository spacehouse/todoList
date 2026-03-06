package com.todolist.project;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * 项目名称格式化工具类。
 * 统一处理默认项目翻译键与普通文本项目名的显示逻辑，避免 UI 与保存流程出现名称回归。
 */
public final class ProjectNameFormatter {
    public static final String DEFAULT_PERSONAL_PROJECT_KEY = "gui.todolist.project.default.personal";
    public static final String DEFAULT_TEAM_PROJECT_KEY = "gui.todolist.project.default.team";
    public static final String DEFAULT_PERSONAL_PROJECT_ID = "default-personal-project";
    public static final String DEFAULT_TEAM_PROJECT_ID = "default-team-project";

    private ProjectNameFormatter() {
    }

    /**
     * 将项目对象转换为可显示文本。
     * @param project 项目对象
     * @return 可用于 UI 的文本对象
     */
    public static MutableText toDisplayText(Project project) {
        if (project == null) {
            return Text.empty();
        }
        String normalizedName = normalizeDefaultName(project.getName(), project.getScope());
        return toDisplayText(normalizedName);
    }

    /**
     * 将项目名字符串转换为可显示文本。
     * @param projectName 项目名或翻译键
     * @return 可用于 UI 的文本对象
     */
    public static MutableText toDisplayText(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return Text.empty();
        }
        if (isTranslationKey(projectName)) {
            return Text.translatable(projectName);
        }
        return Text.literal(projectName);
    }

    /**
     * 判断项目名是否是翻译键。
     * @param name 项目名字符串
     * @return true 表示翻译键，false 表示普通文本
     */
    public static boolean isTranslationKey(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.startsWith("gui.todolist.") || name.startsWith("item.") || name.startsWith("block.");
    }

    public static String normalizeDefaultName(String name, Project.Scope scope) {
        if (name == null || name.isEmpty() || scope == null) {
            return name;
        }
        if (scope == Project.Scope.PERSONAL && matches(name, DEFAULT_PERSONAL_PROJECT_KEY, "默认项目", "Default Project", "Inbox")) {
            return DEFAULT_PERSONAL_PROJECT_KEY;
        }
        if (scope == Project.Scope.TEAM && matches(name, DEFAULT_TEAM_PROJECT_KEY, "团队项目", "Team Project", "General")) {
            return DEFAULT_TEAM_PROJECT_KEY;
        }
        return name;
    }

    private static boolean matches(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
