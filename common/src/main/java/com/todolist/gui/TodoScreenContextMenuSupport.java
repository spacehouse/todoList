package com.todolist.gui;

import com.todolist.task.Task;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * TodoScreen 右键菜单支持类，集中管理菜单命中与尺寸计算。
 */
final class TodoScreenContextMenuSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenContextMenuSupport() {
    }

    static boolean hasContextMenu(Task contextMenuTask, List<?> contextMenuItems) {
        return contextMenuTask != null && contextMenuItems != null && !contextMenuItems.isEmpty();
    }

    static int resolveMenuHeight(int itemCount, int itemHeight) {
        return Math.max(0, itemCount) * Math.max(0, itemHeight);
    }

    static boolean isInsideContextMenu(double mouseX,
                                       double mouseY,
                                       int contextMenuX,
                                       int contextMenuY,
                                       int contextMenuWidth,
                                       int menuHeight) {
        return mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                && mouseY >= contextMenuY && mouseY < contextMenuY + menuHeight;
    }

    static int resolveClickedItemIndex(double mouseY, int contextMenuY, int itemHeight, int itemCount) {
        if (itemHeight <= 0 || itemCount <= 0) {
            return -1;
        }
        int index = ((int) mouseY - contextMenuY) / itemHeight;
        return index < 0 || index >= itemCount ? -1 : index;
    }

    /**
     * 绘制任务右键菜单面板及菜单项。
     *
     * @param context 图形上下文
     * @param font 字体实例
     * @param items 菜单项集合
     * @param contextMenuX 菜单起始 X 坐标
     * @param contextMenuY 菜单起始 Y 坐标
     * @param contextMenuWidth 菜单宽度
     * @param itemHeight 菜单项高度
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     */
    static void renderMenu(GuiGraphics context,
                           Font font,
                           List<ContextMenuItem> items,
                           int contextMenuX,
                           int contextMenuY,
                           int contextMenuWidth,
                           int itemHeight,
                           int mouseX,
                           int mouseY) {
        if (context == null || font == null || items == null || items.isEmpty()) {
            return;
        }
        int menuHeight = resolveMenuHeight(items.size(), itemHeight);
        context.fill(contextMenuX, contextMenuY, contextMenuX + contextMenuWidth, contextMenuY + menuHeight, 0xEE111111);
        context.submitOutline(contextMenuX, contextMenuY, contextMenuWidth, menuHeight, 0xFFFFFFFF);
        for (int i = 0; i < items.size(); i++) {
            ContextMenuItem item = items.get(i);
            if (item == null) {
                continue;
            }
            int itemTop = contextMenuY + i * itemHeight;
            int itemBottom = itemTop + itemHeight;
            boolean hovered = mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                    && mouseY >= itemTop && mouseY < itemBottom;
            if (hovered) {
                context.fill(contextMenuX + 1, itemTop + 1, contextMenuX + contextMenuWidth - 1, itemBottom - 1, 0xFF2A2A2A);
            }
            int textColor = item.enabled ? 0xFFFFFFFF : 0xFF777777;
            int textY = itemTop + (itemHeight - font.lineHeight) / 2;
            context.drawString(font, item.text, contextMenuX + 6, textY, textColor, false);
        }
    }

    static int[] resolveMenuOrigin(int mouseX,
                                   int mouseY,
                                   int screenWidth,
                                   int screenHeight,
                                   int contextMenuWidth,
                                   int menuHeight,
                                   int margin) {
        int x = Math.max(margin, Math.min(mouseX, screenWidth - contextMenuWidth - margin));
        int y = Math.max(margin, Math.min(mouseY, screenHeight - menuHeight - margin));
        return new int[] {x, y};
    }
}
