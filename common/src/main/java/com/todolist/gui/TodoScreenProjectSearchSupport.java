package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * TodoScreen 项目搜索支持类，负责集中处理项目搜索前缀、角色过滤和查询解析逻辑。
 */
final class TodoScreenProjectSearchSupport {
    private static final int PROJECT_SEARCH_PREFIX_DROPDOWN_GAP = 2;
    private static final int PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING = 4;
    private static final int PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT = 16;
    private static final List<ProjectSearchPrefixOption> PROJECT_SEARCH_PREFIX_OPTIONS = List.of(
            new ProjectSearchPrefixOption("@me", "gui.todolist.project.search.prefix.me.desc", "我创建的项目", "Projects I created"),
            new ProjectSearchPrefixOption("@ma", "gui.todolist.project.search.prefix.ma.desc", "我管理的项目", "Projects I manage"),
            new ProjectSearchPrefixOption("@in", "gui.todolist.project.search.prefix.in.desc", "我加入的项目", "Projects I joined")
    );

    /**
     * 表示团队项目搜索前缀对应的角色过滤模式。
     */
    enum ProjectSearchRoleFilter {
        NONE,
        CREATED_BY_ME,
        MANAGED_BY_ME,
        JOINED_BY_ME
    }

    /**
     * 表示解析后的项目搜索查询结果。
     */
    static final class ProjectSearchQuery {
        final ProjectSearchRoleFilter roleFilter;
        final String nameQuery;

        /**
         * 创建解析后的项目搜索查询对象。
         *
         * @param roleFilter 项目角色筛选模式
         * @param nameQuery 去掉前缀后的名称关键字
         */
        ProjectSearchQuery(ProjectSearchRoleFilter roleFilter, String nameQuery) {
            this.roleFilter = roleFilter == null ? ProjectSearchRoleFilter.NONE : roleFilter;
            this.nameQuery = nameQuery == null ? "" : nameQuery;
        }
    }

    /**
     * 表示项目搜索前缀下拉面板中的一个候选项。
     */
    static final class ProjectSearchPrefixOption {
        final String prefix;
        final String descKey;
        final String fallbackZh;
        final String fallbackEn;

        /**
         * 创建一个项目搜索前缀候选项。
         *
         * @param prefix 搜索前缀
         * @param descKey 描述文本翻译键
         * @param fallbackZh 中文兜底文案
         * @param fallbackEn 英文兜底文案
         */
        ProjectSearchPrefixOption(String prefix, String descKey, String fallbackZh, String fallbackEn) {
            this.prefix = prefix;
            this.descKey = descKey;
            this.fallbackZh = fallbackZh == null ? "" : fallbackZh;
            this.fallbackEn = fallbackEn == null ? "" : fallbackEn;
        }
    }

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenProjectSearchSupport() {
    }

    static List<ProjectSearchPrefixOption> getProjectSearchPrefixOptions() {
        return PROJECT_SEARCH_PREFIX_OPTIONS;
    }

    static boolean canUseProjectSearchPrefixDropdown(boolean teamSpaceMode, boolean sidebarVisible) {
        return teamSpaceMode && sidebarVisible;
    }

    static boolean shouldShowProjectSearchPrefixDropdown(boolean dropdownOpen,
                                                         boolean teamSpaceMode,
                                                         boolean sidebarVisible,
                                                         EditBox projectSearchField) {
        return dropdownOpen
                && canUseProjectSearchPrefixDropdown(teamSpaceMode, sidebarVisible)
                && projectSearchField != null
                && projectSearchField.visible;
    }

    static boolean syncProjectSearchPrefixDropdownState(boolean dropdownOpen,
                                                        boolean teamSpaceMode,
                                                        boolean sidebarVisible,
                                                        EditBox projectSearchField) {
        if (!shouldShowProjectSearchPrefixDropdown(dropdownOpen, teamSpaceMode, sidebarVisible, projectSearchField)
                || !projectSearchField.isFocused()) {
            return false;
        }
        return true;
    }

    static int getProjectSearchPrefixDropdownRowHeight() {
        return PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT;
    }

    static int[] getProjectSearchPrefixDropdownBounds(int x, int y, int width, int fieldHeight) {
        int dropX = Math.max(0, x);
        int dropY = Math.max(0, y + Math.max(0, fieldHeight) + PROJECT_SEARCH_PREFIX_DROPDOWN_GAP);
        int dropWidth = Math.max(0, width);
        int dropHeight = PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING * 2
                + PROJECT_SEARCH_PREFIX_OPTIONS.size() * PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT;
        return new int[] {dropX, dropY, dropWidth, dropHeight};
    }

    static int[] getProjectSearchPrefixOptionBounds(int x, int y, int width, int fieldHeight, int index) {
        int[] bounds = getProjectSearchPrefixDropdownBounds(x, y, width, fieldHeight);
        if (index < 0 || index >= PROJECT_SEARCH_PREFIX_OPTIONS.size()) {
            return new int[] {0, 0, 0, 0};
        }
        int itemX = bounds[0] + PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING;
        int itemY = bounds[1] + PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING + index * PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT;
        int itemWidth = Math.max(0, bounds[2] - PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING * 2);
        return new int[] {itemX, itemY, itemWidth, PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT};
    }

    static int getProjectSearchPrefixOptionIndexAt(double mouseX,
                                                   double mouseY,
                                                   int x,
                                                   int y,
                                                   int width,
                                                   int fieldHeight) {
        for (int i = 0; i < PROJECT_SEARCH_PREFIX_OPTIONS.size(); i++) {
            int[] bounds = getProjectSearchPrefixOptionBounds(x, y, width, fieldHeight, i);
            if (mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                    && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3]) {
                return i;
            }
        }
        return -1;
    }

    static boolean isProjectSearchFieldHit(EditBox projectSearchField, double mouseX, double mouseY) {
        return projectSearchField != null
                && projectSearchField.visible
                && projectSearchField.isMouseOver(mouseX, mouseY);
    }

    static boolean isInsideProjectSearchPrefixDropdown(boolean dropdownVisible,
                                                       EditBox projectSearchField,
                                                       double mouseX,
                                                       double mouseY) {
        if (!dropdownVisible || projectSearchField == null) {
            return false;
        }
        int[] bounds = getProjectSearchPrefixDropdownBounds(
                projectSearchField.getX(),
                projectSearchField.getY(),
                projectSearchField.getWidth(),
                projectSearchField.getHeight()
        );
        return mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3];
    }

    static String resolveClickedProjectSearchPrefix(boolean dropdownVisible,
                                                    EditBox projectSearchField,
                                                    double mouseX,
                                                    double mouseY) {
        if (!dropdownVisible || projectSearchField == null) {
            return null;
        }
        int index = getProjectSearchPrefixOptionIndexAt(
                mouseX,
                mouseY,
                projectSearchField.getX(),
                projectSearchField.getY(),
                projectSearchField.getWidth(),
                projectSearchField.getHeight()
        );
        if (index < 0 || index >= PROJECT_SEARCH_PREFIX_OPTIONS.size()) {
            return null;
        }
        return PROJECT_SEARCH_PREFIX_OPTIONS.get(index).prefix;
    }

    static String getProjectSearchPrefixOptionDescription(ProjectSearchPrefixOption option) {
        if (option == null) {
            return "";
        }
        String translated = Component.translatable(option.descKey).getString();
        if (translated != null && !translated.isBlank() && !option.descKey.equals(translated)) {
            return translated;
        }
        return option.fallbackZh.isEmpty() ? option.fallbackEn : option.fallbackZh;
    }

    static String buildProjectSearchPrefixOptionText(ProjectSearchPrefixOption option) {
        if (option == null) {
            return "";
        }
        return option.prefix + "  " + getProjectSearchPrefixOptionDescription(option);
    }

    /**
     * 解析项目搜索输入中的角色前缀与名称关键字。
     *
     * @param rawQuery 原始搜索文本
     * @return 解析后的项目搜索查询
     */
    static ProjectSearchQuery parseProjectSearchQuery(String rawQuery) {
        String normalized = rawQuery == null ? "" : rawQuery.trim();
        if (normalized.isEmpty()) {
            return new ProjectSearchQuery(ProjectSearchRoleFilter.NONE, "");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        ProjectSearchRoleFilter roleFilter = parseProjectSearchRoleFilter(lower);
        if (roleFilter == ProjectSearchRoleFilter.NONE) {
            return new ProjectSearchQuery(ProjectSearchRoleFilter.NONE, lower);
        }
        String prefix = getProjectSearchRolePrefix(roleFilter);
        String suffix = normalized.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
        return new ProjectSearchQuery(roleFilter, suffix);
    }

    /**
     * 解析搜索文本开头的角色过滤前缀。
     *
     * @param lowerQuery 转成小写后的搜索文本
     * @return 命中的角色过滤模式
     */
    static ProjectSearchRoleFilter parseProjectSearchRoleFilter(String lowerQuery) {
        if (matchesProjectSearchPrefix(lowerQuery, "@me")) {
            return ProjectSearchRoleFilter.CREATED_BY_ME;
        }
        if (matchesProjectSearchPrefix(lowerQuery, "@ma")) {
            return ProjectSearchRoleFilter.MANAGED_BY_ME;
        }
        if (matchesProjectSearchPrefix(lowerQuery, "@in")) {
            return ProjectSearchRoleFilter.JOINED_BY_ME;
        }
        return ProjectSearchRoleFilter.NONE;
    }

    /**
     * 判断查询是否以指定项目前缀开头且后续为空或空白分隔。
     *
     * @param query 当前搜索文本
     * @param prefix 目标前缀
     * @return 命中指定前缀时返回 {@code true}
     */
    static boolean matchesProjectSearchPrefix(String query, String prefix) {
        if (query == null || prefix == null || !query.startsWith(prefix)) {
            return false;
        }
        return query.length() == prefix.length() || Character.isWhitespace(query.charAt(prefix.length()));
    }

    /**
     * 返回角色过滤模式对应的项目搜索前缀。
     *
     * @param roleFilter 角色过滤模式
     * @return 对应的搜索前缀
     */
    static String getProjectSearchRolePrefix(ProjectSearchRoleFilter roleFilter) {
        return switch (roleFilter) {
            case CREATED_BY_ME -> "@me";
            case MANAGED_BY_ME -> "@ma";
            case JOINED_BY_ME -> "@in";
            case NONE -> "";
        };
    }

    /**
     * 判断团队项目是否满足当前角色过滤条件。
     *
     * @param project 当前项目
     * @param roleFilter 角色过滤模式
     * @param playerUuid 当前玩家 UUID
     * @return 命中过滤条件时返回 {@code true}
     */
    static boolean matchesTeamProjectSearchRoleFilter(Project project,
                                                      ProjectSearchRoleFilter roleFilter,
                                                      String playerUuid) {
        if (project == null || playerUuid == null || playerUuid.isEmpty()) {
            return roleFilter == ProjectSearchRoleFilter.NONE;
        }
        if (roleFilter == ProjectSearchRoleFilter.CREATED_BY_ME) {
            return playerUuid.equals(project.getOwnerUuid());
        }
        Project.ProjectRole memberRole = project.getMemberRole(playerUuid);
        if (roleFilter == ProjectSearchRoleFilter.MANAGED_BY_ME) {
            return playerUuid.equals(project.getOwnerUuid())
                    || memberRole == Project.ProjectRole.PROJECT_MANAGER
                    || memberRole == Project.ProjectRole.LEAD;
        }
        if (roleFilter == ProjectSearchRoleFilter.JOINED_BY_ME) {
            return memberRole != null;
        }
        return true;
    }

    /**
     * 提取项目搜索文本中去掉已识别前缀后的剩余关键字。
     *
     * @param current 当前搜索文本
     * @return 去掉前缀后的名称关键字
     */
    static String extractProjectSearchQuerySuffix(String current) {
        String normalized = current == null ? "" : current.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        ProjectSearchRoleFilter roleFilter = parseProjectSearchRoleFilter(lower);
        if (roleFilter != ProjectSearchRoleFilter.NONE) {
            String prefix = getProjectSearchRolePrefix(roleFilter);
            return normalized.substring(prefix.length()).trim();
        }
        if (!normalized.startsWith("@")) {
            return normalized;
        }
        int separatorIndex = findFirstWhitespaceIndex(normalized);
        if (separatorIndex < 0) {
            return "";
        }
        return normalized.substring(separatorIndex).trim();
    }

    static String buildProjectSearchValueWithPrefix(String current, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return current == null ? "" : current;
        }
        String suffix = extractProjectSearchQuerySuffix(current);
        return suffix.isEmpty() ? prefix + " " : prefix + " " + suffix;
    }

    /**
     * 返回字符串中第一个空白字符的位置。
     *
     * @param text 待检查文本
     * @return 第一个空白字符的位置，未找到时返回 -1
     */
    static int findFirstWhitespaceIndex(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 按作用域、前缀角色和名称关键字筛选项目列表。
     *
     * @param source 原始项目列表
     * @param scopeFilter 当前侧栏作用域筛选
     * @param queryRaw 原始搜索文本
     * @param playerUuid 当前玩家 UUID
     * @return 筛选后的项目列表
     */
    static List<Project> filterProjectsForSidebar(List<Project> source,
                                                  Project.Scope scopeFilter,
                                                  String queryRaw,
                                                  String playerUuid) {
        List<Project> input = source == null ? List.of() : source;
        ProjectSearchQuery parsedQuery = parseProjectSearchQuery(queryRaw);
        List<Project> filtered = new ArrayList<>();
        for (Project project : input) {
            if (project == null || project.getScope() != scopeFilter) {
                continue;
            }
            if (scopeFilter == Project.Scope.TEAM
                    && parsedQuery.roleFilter != ProjectSearchRoleFilter.NONE
                    && !matchesTeamProjectSearchRoleFilter(project, parsedQuery.roleFilter, playerUuid)) {
                continue;
            }
            String searchableName = ProjectNameFormatter.toDisplayText(project).getString().toLowerCase(Locale.ROOT);
            if (!parsedQuery.nameQuery.isEmpty() && !searchableName.contains(parsedQuery.nameQuery)) {
                continue;
            }
            filtered.add(project);
        }
        return filtered;
    }

    /**
     * 判断项目列表中是否已包含指定项目 ID。
     *
     * @param projects 待检查项目列表
     * @param projectId 目标项目 ID
     * @return 包含时返回 {@code true}
     */
    static boolean containsProjectId(List<Project> projects, String projectId) {
        if (projects == null || projectId == null || projectId.isEmpty()) {
            return false;
        }
        for (Project project : projects) {
            if (project != null && projectId.equals(project.getId())) {
                return true;
            }
        }
        return false;
    }
}
