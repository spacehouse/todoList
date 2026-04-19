package com.todolist.gui;

import net.minecraft.network.chat.Component;

/**
 * TodoScreen 右键菜单项模型。
 */
final class ContextMenuItem {
    final Component text;
    final boolean enabled;
    final Runnable action;

    /**
     * 创建菜单项模型。
     *
     * @param text 显示文本
     * @param enabled 是否可用
     * @param action 点击回调
     */
    ContextMenuItem(Component text, boolean enabled, Runnable action) {
        this.text = text;
        this.enabled = enabled;
        this.action = action;
    }
}

