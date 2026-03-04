package com.todolist.gui;

/**
 * 鐙珛鐨勬粴鍔ㄦ潯缁勪欢
 * 鍙傝€?Malilib 鐨?GuiScrollBar 璁捐
 *
 * 鐗规€?
 * - 骞虫粦鎷栨嫿婊氬姩
 * - 榧犳爣鎮仠鏁堟灉
 * - 鍔ㄦ€佹粦鍧楀ぇ灏忚绠?
 * - 鏀寔鑷畾涔夐鑹?
 */
public class ScrollBar {
    // 婊氬姩鐘舵€?
    private int value = 0;
    private int maxValue = 0;
    private boolean isDragging = false;
    private int dragStartValue = 0;
    private double dragStartY = 0;

    // 浣嶇疆鍜屽昂瀵?
    private final int barX;
    private final int barY;
    private final int barWidth;
    private final int barHeight;

    // 瑙嗚鐘舵€?
    private boolean mouseOver = false;

    // 棰滆壊閰嶇疆
    private final int backgroundColor;
    private final int normalColor;
    private final int hoverColor;
    private final int dragColor;

    /**
     * 鍒涘缓婊氬姩鏉?
     *
     * @param barX 婊氬姩鏉鍧愭爣
     * @param barY 婊氬姩鏉鍧愭爣
     * @param barWidth 婊氬姩鏉″搴?
     * @param barHeight 婊氬姩鏉￠珮搴?
     */
    public ScrollBar(int barX, int barY, int barWidth, int barHeight) {
        this(barX, barY, barWidth, barHeight,
             0xFF2A2A2A,  // 鑳屾櫙鑹?
             0xFF555555,  // 鏅€氱姸鎬?
             0xFF666666,  // 鎮仠鐘舵€?
             0xFF777777); // 鎷栨嫿鐘舵€?
    }

    /**
     * 鍒涘缓甯﹁嚜瀹氫箟棰滆壊鐨勬粴鍔ㄦ潯
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

    // ========== 鍊艰幏鍙栧拰璁剧疆 ==========

    /**
     * 鑾峰彇褰撳墠婊氬姩鍊?
     */
    public int getValue() {
        return value;
    }

    /**
     * 璁剧疆婊氬姩鍊硷紙鑷姩闄愬埗鍦ㄦ湁鏁堣寖鍥村唴锛?
     */
    public void setValue(int newValue) {
        this.value = Math.max(0, Math.min(newValue, this.maxValue));
    }

    /**
     * 鍋忕Щ婊氬姩鍊?
     * @param offset 鍋忕Щ閲忥紙鍙负姝ｈ礋锛?
     */
    public void offsetValue(int offset) {
        setValue(this.value + offset);
    }

    /**
     * 璁剧疆鏈€澶ф粴鍔ㄥ€?
     */
    public void setMaxValue(int max) {
        this.maxValue = Math.max(0, max);
        this.value = Math.min(this.value, this.maxValue);
    }

    /**
     * 鑾峰彇鏈€澶ф粴鍔ㄥ€?
     */
    public int getMaxValue() {
        return maxValue;
    }

    // ========== 鐘舵€佹煡璇?==========

    /**
     * 妫€鏌ラ紶鏍囨槸鍚﹀湪婊氬姩鏉′笂
     */
    public boolean wasMouseOver() {
        return mouseOver;
    }

    /**
     * 妫€鏌ユ槸鍚︽鍦ㄦ嫋鎷?
     */
    public boolean isDragging() {
        return isDragging;
    }

    /**
     * 璁剧疆鎷栨嫿鐘舵€?
     */
    public void setIsDragging(boolean dragging) {
        this.isDragging = dragging;
    }

    /**
     * 閲嶇疆婊氬姩浣嶇疆鍒伴《閮?
     */
    public void reset() {
        setValue(0);
    }

    // ========== 娓叉煋 ==========

    /**
     * 娓叉煋婊氬姩鏉?
     *
     * @param mouseX 榧犳爣X鍧愭爣
     * @param mouseY 榧犳爣Y鍧愭爣
     * @param totalContentHeight 鍐呭鎬婚珮搴︼紙鐢ㄤ簬璁＄畻婊戝潡澶у皬锛?
     * @param renderer 娓叉煋鍣ㄦ帴鍙?
     */
    public void render(int mouseX, int mouseY, int totalContentHeight, ScrollBarRenderer renderer) {
        // 璁＄畻婊戝潡灏哄鍜屼綅缃?
        float thumbRatio = Math.min(1.0F, (float) barHeight / (float) totalContentHeight);
        int thumbHeight = Math.max(20, (int) (thumbRatio * barHeight));
        int trackHeight = barHeight - 2;
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = barY + 1 + (maxValue > 0 ? (int) ((value / (float) maxValue) * thumbTravel) : 0);

        // 缁樺埗婊氬姩鏉¤建閬撹儗鏅?
        renderer.fillRect(barX, barY, barX + barWidth, barY + barHeight, backgroundColor);

        // 纭畾婊戝潡棰滆壊
        int thumbColor;
        if (isDragging) {
            thumbColor = dragColor;
        } else if (mouseX >= barX && mouseX < barX + barWidth &&
                   mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
            thumbColor = hoverColor;
        } else {
            thumbColor = normalColor;
        }

        // 鏇存柊榧犳爣鎮仠鐘舵€?
        this.mouseOver = (mouseX >= barX && mouseX < barX + barWidth &&
                         mouseY >= barY && mouseY < barY + barHeight);

        // 缁樺埗婊戝潡
        renderer.fillRect(barX + 1, thumbY, barX + barWidth - 1, thumbY + thumbHeight, thumbColor);

        // 澶勭悊鎷栨嫿
        handleDrag(mouseY, thumbTravel);
    }

    /**
     * 澶勭悊鎷栨嫿閫昏緫
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

    // ========== 娓叉煋鍣ㄦ帴鍙?==========

    /**
     * 婊氬姩鏉℃覆鏌撴帴鍙?
     * 鍏佽浣跨敤涓嶅悓鐨勬覆鏌撴柟寮忥紙濡?DrawContext 鎴栧叾浠栵級
     */
    public interface ScrollBarRenderer {
        /**
         * 濉厖鐭╁舰鍖哄煙
         * @param x1 宸︿笂瑙扻
         * @param y1 宸︿笂瑙扽
         * @param x2 鍙充笅瑙扻
         * @param y2 鍙充笅瑙扽
         * @param color ARGB棰滆壊鍊?
         */
        void fillRect(int x1, int y1, int x2, int y2, int color);
    }

    // ========== Getter鏂规硶 ==========

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


