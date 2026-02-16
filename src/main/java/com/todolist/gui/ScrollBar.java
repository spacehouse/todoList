package com.todolist.gui;

/**
 * 独立的滚动条组件
 * 参考 Malilib 的 GuiScrollBar 设计
 *
 * 特性:
 * - 平滑拖拽滚动
 * - 鼠标悬停效果
 * - 动态滑块大小计算
 * - 支持自定义颜色
 */
public class ScrollBar {
    // 滚动状态
    private int value = 0;
    private int maxValue = 0;
    private boolean isDragging = false;
    private int dragStartValue = 0;
    private double dragStartY = 0;

    // 位置和尺寸
    private final int barX;
    private final int barY;
    private final int barWidth;
    private final int barHeight;

    // 视觉状态
    private boolean mouseOver = false;

    // 颜色配置
    private final int backgroundColor;
    private final int normalColor;
    private final int hoverColor;
    private final int dragColor;

    /**
     * 创建滚动条
     *
     * @param barX 滚动条X坐标
     * @param barY 滚动条Y坐标
     * @param barWidth 滚动条宽度
     * @param barHeight 滚动条高度
     */
    public ScrollBar(int barX, int barY, int barWidth, int barHeight) {
        this(barX, barY, barWidth, barHeight,
             0xFF2A2A2A,  // 背景色
             0xFF555555,  // 普通状态
             0xFF666666,  // 悬停状态
             0xFF777777); // 拖拽状态
    }

    /**
     * 创建带自定义颜色的滚动条
     */
    public ScrollBar(int barX, int barY, int barWidth, int barHeight,
                    int backgroundColor, int normalColor, int hoverColor, int dragColor) {
        this.barX = barX;
        this.barY = barY;
        this.barWidth = barWidth;
        this.barHeight = barHeight;
        this.backgroundColor = backgroundColor;
        this.normalColor = normalColor;
        this.hoverColor = hoverColor;
        this.dragColor = dragColor;
    }

    // ========== 值获取和设置 ==========

    /**
     * 获取当前滚动值
     */
    public int getValue() {
        return value;
    }

    /**
     * 设置滚动值（自动限制在有效范围内）
     */
    public void setValue(int newValue) {
        this.value = Math.max(0, Math.min(newValue, this.maxValue));
    }

    /**
     * 偏移滚动值
     * @param offset 偏移量（可为正负）
     */
    public void offsetValue(int offset) {
        setValue(this.value + offset);
    }

    /**
     * 设置最大滚动值
     */
    public void setMaxValue(int max) {
        this.maxValue = Math.max(0, max);
        this.value = Math.min(this.value, this.maxValue);
    }

    /**
     * 获取最大滚动值
     */
    public int getMaxValue() {
        return maxValue;
    }

    // ========== 状态查询 ==========

    /**
     * 检查鼠标是否在滚动条上
     */
    public boolean wasMouseOver() {
        return mouseOver;
    }

    /**
     * 检查是否正在拖拽
     */
    public boolean isDragging() {
        return isDragging;
    }

    /**
     * 设置拖拽状态
     */
    public void setIsDragging(boolean dragging) {
        this.isDragging = dragging;
    }

    /**
     * 重置滚动位置到顶部
     */
    public void reset() {
        setValue(0);
    }

    // ========== 渲染 ==========

    /**
     * 渲染滚动条
     *
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param totalContentHeight 内容总高度（用于计算滑块大小）
     * @param renderer 渲染器接口
     */
    public void render(int mouseX, int mouseY, int totalContentHeight, ScrollBarRenderer renderer) {
        // 计算滑块尺寸和位置
        float thumbRatio = Math.min(1.0F, (float) barHeight / (float) totalContentHeight);
        int thumbHeight = Math.max(20, (int) (thumbRatio * barHeight));
        int trackHeight = barHeight - 2;
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = barY + 1 + (maxValue > 0 ? (int) ((value / (float) maxValue) * thumbTravel) : 0);

        // 绘制滚动条轨道背景
        renderer.fillRect(barX, barY, barX + barWidth, barY + barHeight, backgroundColor);

        // 确定滑块颜色
        int thumbColor;
        if (isDragging) {
            thumbColor = dragColor;
        } else if (mouseX >= barX && mouseX < barX + barWidth &&
                   mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
            thumbColor = hoverColor;
        } else {
            thumbColor = normalColor;
        }

        // 更新鼠标悬停状态
        this.mouseOver = (mouseX >= barX && mouseX < barX + barWidth &&
                         mouseY >= barY && mouseY < barY + barHeight);

        // 绘制滑块
        renderer.fillRect(barX + 1, thumbY, barX + barWidth - 1, thumbY + thumbHeight, thumbColor);

        // 处理拖拽
        handleDrag(mouseY, thumbTravel);
    }

    /**
     * 处理拖拽逻辑
     */
    private void handleDrag(int mouseY, int thumbTravel) {
        if (isDragging) {
            float valuePerPixel = maxValue > 0 ? (float) maxValue / thumbTravel : 0;
            setValue((int) (dragStartValue + ((mouseY - dragStartY) * valuePerPixel)));
        } else {
            dragStartY = mouseY;
            dragStartValue = value;
        }
    }

    // ========== 渲染器接口 ==========

    /**
     * 滚动条渲染接口
     * 允许使用不同的渲染方式（如 DrawContext 或其他）
     */
    public interface ScrollBarRenderer {
        /**
         * 填充矩形区域
         * @param x1 左上角X
         * @param y1 左上角Y
         * @param x2 右下角X
         * @param y2 右下角Y
         * @param color ARGB颜色值
         */
        void fillRect(int x1, int y1, int x2, int y2, int color);
    }

    // ========== Getter方法 ==========

    public int getBarX() {
        return barX;
    }

    public int getBarY() {
        return barY;
    }

    public int getBarWidth() {
        return barWidth;
    }

    public int getBarHeight() {
        return barHeight;
    }
}
