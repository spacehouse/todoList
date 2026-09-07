package com.todolist.gui;

import com.todolist.gui.TodoScreenLayoutSupport.LayoutRect;
import com.todolist.gui.TodoScreenLayoutSupport.MainLayoutMetrics;
import com.todolist.gui.TodoScreenLayoutSupport.ResponsiveTier;
import com.todolist.project.Project;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * TodoScreen 渲染支持类，集中处理与主界面绘制相关的纯渲染逻辑。
 */
final class TodoScreenRenderSupport {
    /**
     * 工具类不允许实例化。
     */
    private TodoScreenRenderSupport() {
    }

    /**
     * 绘制项目搜索前缀下拉菜单。
     */
    static void renderProjectSearchPrefixDropdown(GuiGraphics context,
                                                  int mouseX,
                                                  int mouseY,
                                                  Font font,
                                                  EditBox projectSearchField,
                                                  boolean projectSearchPrefixDropdownOpen,
                                                  boolean teamSpace,
                                                  boolean sidebarVisible) {
        boolean prefixDropdownVisible = TodoScreenProjectSearchSupport.shouldShowProjectSearchPrefixDropdown(
                projectSearchPrefixDropdownOpen,
                teamSpace,
                sidebarVisible,
                projectSearchField
        );
        if (!prefixDropdownVisible || font == null || projectSearchField == null) {
            return;
        }
        int[] bounds = TodoScreenProjectSearchSupport.getProjectSearchPrefixDropdownBounds(
                projectSearchField.getX(),
                projectSearchField.getY(),
                projectSearchField.getWidth(),
                projectSearchField.getHeight()
        );
        if (bounds[2] <= 0 || bounds[3] <= 0) {
            return;
        }
        context.fill(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3], 0xEE0F141B);
        context.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3], 0xFF506070);

        List<TodoScreenProjectSearchSupport.ProjectSearchPrefixOption> options =
                TodoScreenProjectSearchSupport.getProjectSearchPrefixOptions();
        for (int i = 0; i < options.size(); i++) {
            int[] itemBounds = TodoScreenProjectSearchSupport.getProjectSearchPrefixOptionBounds(
                    projectSearchField.getX(),
                    projectSearchField.getY(),
                    projectSearchField.getWidth(),
                    projectSearchField.getHeight(),
                    i
            );
            boolean hovered = mouseX >= itemBounds[0] && mouseX < itemBounds[0] + itemBounds[2]
                    && mouseY >= itemBounds[1] && mouseY < itemBounds[1] + itemBounds[3];
            if (hovered) {
                context.fill(itemBounds[0], itemBounds[1], itemBounds[0] + itemBounds[2], itemBounds[1] + itemBounds[3], 0xFF223040);
            }
            int textY = itemBounds[1] + Math.max(0, (itemBounds[3] - font.lineHeight) / 2);
            TodoScreenProjectSearchSupport.ProjectSearchPrefixOption option = options.get(i);
            context.drawString(font, option.prefix, itemBounds[0] + 4, textY, 0xFFE0B240, false);
            context.drawString(
                    font,
                    TodoScreenProjectSearchSupport.getProjectSearchPrefixOptionDescription(option),
                    itemBounds[0] + 34,
                    textY,
                    0xFFD8E2EC,
                    false
            );
        }
    }

    /**
     * 绘制内容区顶部摘要文本。
     */
    static void renderContentHeaderSummary(GuiGraphics context,
                                           Font font,
                                           MainLayoutMetrics layoutMetrics,
                                           ResponsiveTier responsiveTier,
                                           SpaceMode currentSpaceMode,
                                           TaskViewOption currentTaskViewOption,
                                           Project currentProject,
                                           Button configButton,
                                           Button sidebarToggleButton) {
        if (layoutMetrics == null || configButton == null || font == null || responsiveTier == null || currentSpaceMode == null) {
            return;
        }
        String summaryText = TodoScreenTextSupport.buildContentHeaderSummaryText(
                currentSpaceMode == SpaceMode.TEAM,
                currentProject,
                currentTaskViewOption
        );
        if (summaryText.isEmpty()) {
            return;
        }
        float scale = 1.0F;
        int startX = layoutMetrics.contentBounds.x + TodoScreenUiMetricsSupport.getContentControlInset(responsiveTier);
        if (sidebarToggleButton != null && sidebarToggleButton.visible) {
            startX = sidebarToggleButton.getX() + sidebarToggleButton.getWidth()
                    + TodoScreenUiMetricsSupport.getContentActionGap(responsiveTier);
        }
        int maxWidth = configButton.getX() - TodoScreenUiMetricsSupport.getContentActionGap(responsiveTier) - startX;
        if (maxWidth <= 12) {
            return;
        }
        String displayText = TodoScreenTextSupport.trimTextToWidth(font, summaryText, Math.round(maxWidth / scale));
        if (displayText.isEmpty()) {
            return;
        }
        int textHeight = Math.max(1, Math.round(font.lineHeight * scale));
        int drawY = configButton.getY() + Math.max(0, (configButton.getHeight() - textHeight) / 2);
        // v1_21_6 覆盖：GuiGraphics.pose() 返回 joml Matrix3x2fStack，
        // pushPose/popPose 改为 pushMatrix/popMatrix，scale 收敛为二维参数
        context.pose().pushMatrix();
        context.pose().scale(scale, scale);
        context.drawString(font, displayText, Math.round(startX / scale), Math.round(drawY / scale), 0xFFFFFFFF, false);
        context.pose().popMatrix();
    }

    /**
     * 绘制主界面三个面板（侧栏、内容、详情）的背景。
     */
    static void renderLayoutPanels(GuiGraphics context,
                                   MainLayoutMetrics layoutMetrics,
                                   boolean sidebarPanelVisible,
                                   boolean detailPanelVisible) {
        if (layoutMetrics == null) {
            return;
        }
        renderPanelBackground(context, layoutMetrics.contentBounds, false);
        if (sidebarPanelVisible) {
            renderPanelBackground(context, layoutMetrics.sidebarBounds, layoutMetrics.sidebarOverlay);
        }
        if (detailPanelVisible) {
            renderPanelBackground(context, layoutMetrics.detailBounds, layoutMetrics.detailOverlay);
        }
    }

    /**
     * 绘制单个面板背景和描边。
     */
    private static void renderPanelBackground(GuiGraphics context, LayoutRect bounds, boolean overlay) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        int fillColor = overlay ? 0xD91A1A1A : 0x8C111111;
        int outlineColor = overlay ? 0xCCB8B8B8 : 0x66888888;
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, fillColor);
        context.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, outlineColor);
    }

    /**
     * 绘制通知并同步清理已过期通知项。
     */
    static void renderNotifications(GuiGraphics context,
                                    Font font,
                                    int screenWidth,
                                    MainLayoutMetrics layoutMetrics,
                                    List<TodoScreenNotificationSupport.NotificationEntry> notifications) {
        if (font == null || notifications == null || notifications.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int boxWidth = TodoScreenNotificationSupport.BOX_WIDTH;
        int boxHeight = TodoScreenNotificationSupport.BOX_HEIGHT;
        LayoutRect contentBounds = layoutMetrics == null ? null : layoutMetrics.contentBounds;
        int startX = TodoScreenNotificationSupport.resolveStartX(screenWidth, contentBounds);
        int startY = TodoScreenNotificationSupport.resolveStartY(contentBounds);

        List<TodoScreenNotificationSupport.NotificationEntry> active =
                TodoScreenNotificationSupport.retainActive(notifications, now);
        notifications.clear();
        notifications.addAll(active);

        int dy = 0;
        for (TodoScreenNotificationSupport.NotificationEntry notification : notifications) {
            int bx1 = startX;
            int by1 = startY + dy;
            int bx2 = bx1 + boxWidth;
            int by2 = by1 + boxHeight;
            context.fill(bx1, by1, bx2, by2, TodoScreenNotificationSupport.BOX_BG_COLOR);
            context.renderOutline(bx1, by1, boxWidth, boxHeight, TodoScreenNotificationSupport.BOX_OUTLINE_COLOR);
            int tx = bx1 + TodoScreenNotificationSupport.TEXT_LEFT_PADDING;
            int ty = by1 + (boxHeight - font.lineHeight) / 2;
            context.drawString(font, Component.nullToEmpty(notification.text), tx, ty, TodoScreenNotificationSupport.TEXT_COLOR, false);
            dy += boxHeight + TodoScreenNotificationSupport.BOX_GAP;
        }
    }
}
