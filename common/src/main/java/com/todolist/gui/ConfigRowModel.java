package com.todolist.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * 配置页单项配置模型。
 * 负责记录配置标签、关联组件以及当前布局后的位置与可见状态。
 */
final class ConfigRowModel {
    private final Component label;
    private final AbstractWidget widget;
    private int labelX;
    private int labelY;
    private int slotX;
    private int slotY;
    private int slotWidth;
    private boolean visible;

    /**
     * 创建单项配置模型。
     *
     * @param label 配置标签
     * @param widget 关联的输入组件
     */
    ConfigRowModel(Component label, AbstractWidget widget) {
        this.label = label;
        this.widget = widget;
    }

    /**
     * 返回配置标签。
     *
     * @return 配置标签
     */
    Component label() {
        return label;
    }

    /**
     * 返回关联的输入组件。
     *
     * @return 输入组件
     */
    AbstractWidget widget() {
        return widget;
    }

    /**
     * 更新当前配置项的布局结果。
     *
     * @param labelX 标签 X 坐标
     * @param labelY 标签 Y 坐标
     * @param slotX 组件容器 X 坐标
     * @param slotY 组件容器 Y 坐标
     * @param slotWidth 组件容器宽度
     * @param visible 当前是否可见
     */
    void updateLayout(int labelX, int labelY, int slotX, int slotY, int slotWidth, boolean visible) {
        this.labelX = labelX;
        this.labelY = labelY;
        this.slotX = slotX;
        this.slotY = slotY;
        this.slotWidth = slotWidth;
        this.visible = visible;
    }

    /**
     * 返回标签 X 坐标。
     *
     * @return 标签 X 坐标
     */
    int labelX() {
        return labelX;
    }

    /**
     * 返回标签 Y 坐标。
     *
     * @return 标签 Y 坐标
     */
    int labelY() {
        return labelY;
    }

    /**
     * 返回组件容器 X 坐标。
     *
     * @return 组件容器 X 坐标
     */
    int slotX() {
        return slotX;
    }

    /**
     * 返回组件容器 Y 坐标。
     *
     * @return 组件容器 Y 坐标
     */
    int slotY() {
        return slotY;
    }

    /**
     * 返回组件容器宽度。
     *
     * @return 组件容器宽度
     */
    int slotWidth() {
        return slotWidth;
    }

    /**
     * 返回当前配置项是否可见。
     *
     * @return 若当前配置项可见则返回 true
     */
    boolean visible() {
        return visible;
    }
}
