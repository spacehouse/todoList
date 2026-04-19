package com.todolist.gui;

import com.todolist.task.Task;

import java.util.List;

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
