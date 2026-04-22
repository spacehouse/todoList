package com.todolist.gui;

/**
 * 独立的滚动条组件。
 * 负责管理滚动位置、拖拽状态以及滚动条的基础渲染参数。
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
     * 创建默认配色的滚动条。
     *
     * @param barX 滚动条左上角 X 坐标
     * @param barY 滚动条左上角 Y 坐标
     * @param barWidth 滚动条宽度
     * @param barHeight 滚动条高度
     */
    public ScrollBar(int barX, int barY, int barWidth, int barHeight) {
        this(barX, barY, barWidth, barHeight,
             0xFF2A2A2A,
             0xFF555555,
             0xFF666666,
             0xFF777777);
    }

    /**
     * 创建指定配色的滚动条。
     *
     * @param barX 滚动条左上角 X 坐标
     * @param barY 滚动条左上角 Y 坐标
     * @param barWidth 滚动条宽度
     * @param barHeight 滚动条高度
     * @param backgroundColor 轨道背景颜色
     * @param normalColor 滑块常规颜色
     * @param hoverColor 滑块悬停颜色
     * @param dragColor 滑块拖拽颜色
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

    /**
     * 获取当前滚动值。
     *
     * @return 当前滚动偏移
     */
    public int getValue() {
        return value;
    }

    /**
     * 设置滚动值，并自动裁剪到合法范围内。
     *
     * @param newValue 目标滚动值
     */
    public void setValue(int newValue) {
        this.value = Math.max(0, Math.min(newValue, this.maxValue));
    }

    /**
     * 在当前滚动值基础上增加偏移量。
     *
     * @param offset 滚动偏移量，可为正负值
     */
    public void offsetValue(int offset) {
        setValue(this.value + offset);
    }

    /**
     * 设置最大滚动值，并同步裁剪当前滚动值。
     *
     * @param max 最大滚动值
     */
    public void setMaxValue(int max) {
        this.maxValue = Math.max(0, max);
        this.value = Math.min(this.value, this.maxValue);
    }

    /**
     * 获取最大滚动值。
     *
     * @return 最大滚动偏移
     */
    public int getMaxValue() {
        return maxValue;
    }

    /**
     * 返回上一帧渲染后鼠标是否位于滚动条区域内。
     *
     * @return 鼠标是否悬停在滚动条上
     */
    public boolean wasMouseOver() {
        return mouseOver;
    }

    /**
     * 返回滚动条当前是否处于拖拽中。
     *
     * @return 是否正在拖拽滑块
     */
    public boolean isDragging() {
        return isDragging;
    }

    /**
     * 设置滚动条的拖拽状态。
     *
     * @param dragging 是否正在拖拽
     */
    public void setIsDragging(boolean dragging) {
        this.isDragging = dragging;
    }

    /**
     * 渲染滚动条，并在需要时处理拖拽更新。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param totalContentHeight 内容总高度，用于计算滑块尺寸
     * @param renderer 具体渲染实现
     */
    public void render(int mouseX, int mouseY, int totalContentHeight, ScrollBarRenderer renderer) {
        float thumbRatio = Math.min(1.0F, (float) barHeight / (float) totalContentHeight);
        int thumbHeight = Math.max(20, (int) (thumbRatio * barHeight));
        int trackHeight = barHeight - 2;
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = barY + 1 + (maxValue > 0 ? (int) ((value / (float) maxValue) * thumbTravel) : 0);

        renderer.fillRect(barX, barY, barX + barWidth, barY + barHeight, backgroundColor);

        int thumbColor;
        if (isDragging) {
            thumbColor = dragColor;
        } else if (mouseX >= barX && mouseX < barX + barWidth &&
                   mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
            thumbColor = hoverColor;
        } else {
            thumbColor = normalColor;
        }

        this.mouseOver = (mouseX >= barX && mouseX < barX + barWidth &&
                         mouseY >= barY && mouseY < barY + barHeight);

        renderer.fillRect(barX + 1, thumbY, barX + barWidth - 1, thumbY + thumbHeight, thumbColor);

        handleDrag(mouseY, thumbTravel);
    }

    /**
     * 根据当前拖拽状态计算新的滚动值。
     *
     * @param mouseY 鼠标 Y 坐标
     * @param thumbTravel 滑块可移动距离
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

    /**
     * 滚动条渲染接口。
     * 用于适配不同的绘制上下文实现。
     */
    public interface ScrollBarRenderer {
        /**
         * 填充一个矩形区域。
         *
         * @param x1 左上角 X 坐标
         * @param y1 左上角 Y 坐标
         * @param x2 右下角 X 坐标
         * @param y2 右下角 Y 坐标
         * @param color ARGB 颜色值
         */
        void fillRect(int x1, int y1, int x2, int y2, int color);
    }

    /**
     * 获取滚动条左上角 X 坐标。
     *
     * @return X 坐标
     */
    public int getBarX() {
        return barX;
    }

    /**
     * 获取滚动条左上角 Y 坐标。
     *
     * @return Y 坐标
     */
    public int getBarY() {
        return barY;
    }

    /**
     * 获取滚动条宽度。
     *
     * @return 宽度
     */
    public int getBarWidth() {
        return barWidth;
    }

    /**
     * 获取滚动条高度。
     *
     * @return 高度
     */
    public int getBarHeight() {
        return barHeight;
    }
}
