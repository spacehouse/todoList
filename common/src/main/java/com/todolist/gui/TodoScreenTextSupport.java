package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * TodoScreen 文本支持类，集中处理顶部摘要与文本裁剪逻辑。
 */
final class TodoScreenTextSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenTextSupport() {
    }

    /**
     * 生成任务列表顶部摘要文本，内容为当前空间、项目名称与视图维度。
     *
     * @param teamSpace 当前是否为团队空间
     * @param currentProject 当前项目
     * @param taskViewOption 当前任务视图维度
     * @return 顶部摘要文本
     */
    static String buildContentHeaderSummaryText(boolean teamSpace, Project currentProject, TaskViewOption taskViewOption) {
        String scopeText = Component.translatable(teamSpace
                ? "gui.todolist.scope.team"
                : "gui.todolist.scope.personal").getString();
        String viewText = buildContentHeaderViewText(teamSpace, taskViewOption);
        if (currentProject == null) {
            return scopeText + " / " + viewText;
        }
        String projectName = ProjectNameFormatter.toDisplayText(currentProject).getString().trim();
        if (projectName.isEmpty()) {
            return scopeText + " / " + viewText;
        }
        return scopeText + " / " + projectName + " / " + viewText;
    }

    /**
     * 根据空间与任务视图选项生成面包屑中的视图短标签。
     *
     * @param teamSpace 当前是否为团队空间
     * @param taskViewOption 当前任务视图维度
     * @return 视图标签文本
     */
    private static String buildContentHeaderViewText(boolean teamSpace, TaskViewOption taskViewOption) {
        if (!teamSpace || taskViewOption == null || taskViewOption == TaskViewOption.MY) {
            return Component.translatable("gui.todolist.header.view.mine").getString();
        }
        if (taskViewOption == TaskViewOption.UNASSIGNED) {
            return Component.translatable("gui.todolist.header.view.unassigned").getString();
        }
        return Component.translatable("gui.todolist.header.view.all").getString();
    }

    /**
     * 根据可用宽度裁剪文本，超出时追加省略号。
     *
     * @param font 字体对象
     * @param text 原始文本
     * @param maxWidth 最大宽度
     * @return 裁剪后的文本
     */
    static String trimTextToWidth(Font font, String text, int maxWidth) {
        if (font == null || text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int coreWidth = Math.max(0, maxWidth - ellipsisWidth);
        String core = font.plainSubstrByWidth(text, coreWidth).trim();
        if (core.isEmpty()) {
            return "";
        }
        return core + "...";
    }
}
