package com.todolist.gui;

import net.minecraft.network.chat.Component;

/**
 * TodoScreen 筛选文案支持类，集中管理筛选与视图按钮显示文本。
 */
final class TodoScreenFilterTextSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenFilterTextSupport() {
    }

    static Component getPriorityFilterText(int currentPriorityFilter) {
        String valueKey;
        switch (currentPriorityFilter) {
            case 1:
                valueKey = "gui.todolist.filter.priority_high";
                break;
            case 2:
                valueKey = "gui.todolist.filter.priority_medium";
                break;
            case 3:
                valueKey = "gui.todolist.filter.priority_low";
                break;
            default:
                valueKey = "gui.todolist.all";
                break;
        }
        return Component.translatable(valueKey);
    }

    static Component getStatusFilterText(String currentFilter) {
        return "completed".equals(currentFilter)
                ? Component.translatable("gui.todolist.completed")
                : Component.translatable("gui.todolist.active");
    }

    static Component getViewToggleText(String viewModeName) {
        String key;
        if ("TEAM_UNASSIGNED".equals(viewModeName)) {
            key = "gui.todolist.view.team_unassigned";
        } else if ("TEAM_ALL".equals(viewModeName)) {
            key = "gui.todolist.view.team_all";
        } else if ("TEAM_ASSIGNED".equals(viewModeName)) {
            key = "gui.todolist.view.team_assigned";
        } else {
            key = "gui.todolist.view.personal";
        }
        return Component.translatable(key);
    }
}
