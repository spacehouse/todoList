package com.todolist.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/**
 * TodoScreen 测试辅助类，集中提供常用的控件边界和输入值工具。
 */
final class TodoScreenTestSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenTestSupport() {
    }

    /**
     * 将控件转换为统一的边界数组。
     *
     * @param widget 目标控件
     * @return 边界数组
     */
    static int[] toWidgetBounds(AbstractWidget widget) {
        if (widget == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()};
    }

    /**
     * 读取输入框的有效文本，并过滤空值与提示文本。
     *
     * @param field 目标输入框
     * @param hint 提示文本
     * @return 清洗后的输入值
     */
    static String getFieldValue(EditBox field, String hint) {
        if (field == null) {
            return "";
        }
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (!hint.isEmpty() && raw.equals(hint)) {
            return "";
        }
        return raw;
    }
}
