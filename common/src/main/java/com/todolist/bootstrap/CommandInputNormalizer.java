package com.todolist.bootstrap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 命令输入归一化工具：集中处理命令参数的大小写、别名与非法值回退规则。
 */
public final class CommandInputNormalizer {

    /**
     * 创建命令输入归一化工具实例的私有构造器，阻止外部实例化。
     */
    private CommandInputNormalizer() {
    }

    /**
     * 归一化任务列表状态参数。
     *
     * @param status 原始状态参数
     * @return all/incomplete/completed 之一；非法值返回空字符串
     */
    public static String normalizeTaskListStatus(String status) {
        if (status == null) {
            return "all";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "all" -> "all";
            case "incomplete" -> "incomplete";
            case "completed" -> "completed";
            default -> "";
        };
    }

    /**
     * 归一化任务列表优先级参数。
     *
     * @param priority 原始优先级参数
     * @return all/low/medium/high 之一；非法值返回空字符串
     */
    public static String normalizeTaskListPriority(String priority) {
        if (priority == null) {
            return "all";
        }
        return switch (priority.toLowerCase(Locale.ROOT)) {
            case "all" -> "all";
            case "low" -> "low";
            case "medium" -> "medium";
            case "high" -> "high";
            default -> "";
        };
    }

    /**
     * 归一化任务清理范围参数。
     *
     * @param scope 原始范围参数
     * @return personal/team 之一；非法值返回空字符串
     */
    public static String normalizeTaskCleanScope(String scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope.toLowerCase(Locale.ROOT)) {
            case "personal" -> "personal";
            case "team" -> "team";
            default -> "";
        };
    }

    /**
     * 归一化任务清理项目选择器参数。
     *
     * @param selector 原始选择器参数
     * @return current/star/all 之一；非法值返回空字符串
     */
    public static String normalizeTaskCleanProjectSelector(String selector) {
        if (selector == null) {
            return "";
        }
        return switch (selector.toLowerCase(Locale.ROOT)) {
            case "current" -> "current";
            case "star" -> "star";
            case "all" -> "all";
            default -> "";
        };
    }

    /**
     * 归一化任务清理状态参数。
     *
     * @param status 原始状态参数
     * @return incomplete/completed 之一；非法值返回空字符串
     */
    public static String normalizeTaskCleanStatus(String status) {
        if (status == null) {
            return "";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "incomplete" -> "incomplete";
            case "completed" -> "completed";
            default -> "";
        };
    }

    /**
     * 归一化项目范围参数。
     *
     * @param scope 原始范围参数
     * @return personal/team 之一；非法值返回空字符串
     */
    public static String normalizeProjectScope(String scope) {
        return normalizeTaskCleanScope(scope);
    }

    /**
     * 归一化项目列表模式参数。
     *
     * @param mode 原始模式参数
     * @return all/current/star 之一；非法值返回空字符串
     */
    public static String normalizeProjectListMode(String mode) {
        if (mode == null) {
            return "";
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "all" -> "all";
            case "current" -> "current";
            case "star" -> "star";
            default -> "";
        };
    }

    /**
     * 归一化布尔型开关参数。
     *
     * @param rawValue 原始开关参数
     * @return true/false；非法值返回 null
     */
    public static Boolean normalizeToggleState(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (rawValue.toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled", "1", "yes" -> Boolean.TRUE;
            case "off", "false", "disable", "disabled", "0", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    /**
     * 归一化项目成员角色参数。
     *
     * @param rawRole 原始角色参数
     * @return lead/member 之一；非法值返回空字符串
     */
    public static String normalizeProjectMemberRole(String rawRole) {
        if (rawRole == null) {
            return "";
        }
        return switch (rawRole.toLowerCase(Locale.ROOT)) {
            case "lead" -> "lead";
            case "member" -> "member";
            default -> "";
        };
    }

    /**
     * 归一化命令权限模式参数。
     *
     * @param rawMode 原始权限模式参数
     * @return op_only/view_only/full 之一；非法值返回空字符串
     */
    public static String normalizeCommandAccessMode(String rawMode) {
        if (rawMode == null) {
            return "";
        }
        String normalizedMode = rawMode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalizedMode) {
            case "op_only" -> "op_only";
            case "view_only" -> "view_only";
            case "full" -> "full";
            default -> "";
        };
    }

    /**
     * 根据目标状态生成新的 HUD 星标项目列表，自动去重并过滤空值。
     *
     * @param currentProjectIds 当前星标项目 ID 列表
     * @param projectId 目标项目 ID
     * @param starred 是否应保留为星标
     * @return 处理后的项目 ID 列表
     */
    public static List<String> applyHudStarredProjectState(List<String> currentProjectIds, String projectId, boolean starred) {
        LinkedHashSet<String> projectIds = new LinkedHashSet<>();
        if (currentProjectIds != null) {
            for (String currentProjectId : currentProjectIds) {
                String normalizedCurrentProjectId = normalizeProjectId(currentProjectId);
                if (!normalizedCurrentProjectId.isEmpty()) {
                    projectIds.add(normalizedCurrentProjectId);
                }
            }
        }
        String normalizedProjectId = normalizeProjectId(projectId);
        if (!normalizedProjectId.isEmpty()) {
            if (starred) {
                projectIds.add(normalizedProjectId);
            } else {
                projectIds.remove(normalizedProjectId);
            }
        }
        return new ArrayList<>(projectIds);
    }

    /**
     * 归一化项目 ID，去除首尾空白并将空输入折叠为空字符串。
     *
     * @param projectId 原始项目 ID
     * @return 归一化后的项目 ID；非法或空输入返回空字符串
     */
    public static String normalizeProjectId(String projectId) {
        if (projectId == null) {
            return "";
        }
        String normalizedProjectId = projectId.trim();
        return normalizedProjectId.isEmpty() ? "" : normalizedProjectId;
    }
}
