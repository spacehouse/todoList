package com.todolist.gui;

/**
 * 成员选择弹窗布局快照。
 * 负责为“添加成员”和“指派成员”弹窗提供统一的响应式尺寸、列表区与底部按钮位置。
 */
final class MemberSelectionDialogLayout {
    private static final int HORIZONTAL_PADDING = 10;
    private static final int MIN_DIALOG_WIDTH = 200;
    private static final int MAX_DIALOG_WIDTH = 320;
    private static final int MIN_DIALOG_WIDTH_ON_TINY_SCREEN = 120;
    private static final int MIN_TOP_MARGIN = 12;
    private static final int MAX_TOP_MARGIN = 40;
    private static final int SEARCH_HEIGHT = 20;
    private static final int SEARCH_LIST_GAP = 6;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int BOTTOM_PADDING = 12;

    private final int dialogX;
    private final int dialogWidth;
    private final int searchY;
    private final int searchHeight;
    private final int listX;
    private final int listY;
    private final int listWidth;
    private final int listHeight;
    private final int rowHeight;
    private final int visibleRows;
    private final int cancelX;
    private final int cancelY;
    private final int cancelWidth;
    private final int cancelHeight;

    /**
     * 创建成员选择弹窗布局快照。
     *
     * @param dialogX 弹窗左上角 X 坐标
     * @param dialogWidth 弹窗宽度
     * @param searchY 搜索框 Y 坐标
     * @param searchHeight 搜索框高度
     * @param listX 列表区域 X 坐标
     * @param listY 列表区域 Y 坐标
     * @param listWidth 列表区域宽度
     * @param listHeight 列表区域高度
     * @param rowHeight 列表单行高度
     * @param visibleRows 当前可见行数
     * @param cancelX 取消按钮 X 坐标
     * @param cancelY 取消按钮 Y 坐标
     * @param cancelWidth 取消按钮宽度
     * @param cancelHeight 取消按钮高度
     */
    private MemberSelectionDialogLayout(int dialogX, int dialogWidth, int searchY, int searchHeight,
                                        int listX, int listY, int listWidth, int listHeight,
                                        int rowHeight, int visibleRows, int cancelX, int cancelY,
                                        int cancelWidth, int cancelHeight) {
        this.dialogX = dialogX;
        this.dialogWidth = dialogWidth;
        this.searchY = searchY;
        this.searchHeight = searchHeight;
        this.listX = listX;
        this.listY = listY;
        this.listWidth = listWidth;
        this.listHeight = listHeight;
        this.rowHeight = rowHeight;
        this.visibleRows = visibleRows;
        this.cancelX = cancelX;
        this.cancelY = cancelY;
        this.cancelWidth = cancelWidth;
        this.cancelHeight = cancelHeight;
    }

    /**
     * 根据窗口尺寸计算成员选择弹窗布局。
     *
     * @param screenWidth 当前屏幕宽度
     * @param screenHeight 当前屏幕高度
     * @return 计算后的布局快照
     */
    static MemberSelectionDialogLayout create(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(MIN_DIALOG_WIDTH_ON_TINY_SCREEN, screenWidth - HORIZONTAL_PADDING * 2);
        int dialogWidth = Math.min(MAX_DIALOG_WIDTH, availableWidth);
        if (availableWidth >= MIN_DIALOG_WIDTH) {
            dialogWidth = Math.max(MIN_DIALOG_WIDTH, dialogWidth);
        }

        int dialogX = Math.max(0, (screenWidth - dialogWidth) / 2);
        int topMargin = Math.max(MIN_TOP_MARGIN, Math.min(MAX_TOP_MARGIN, screenHeight / 7));
        int searchY = topMargin;
        int listY = searchY + SEARCH_HEIGHT + SEARCH_LIST_GAP;
        int availableListHeight = screenHeight - listY - BUTTON_GAP - BUTTON_HEIGHT - BOTTOM_PADDING;
        int maxRowsByHeight = Math.max(1, availableListHeight / ROW_HEIGHT);
        int visibleRows = Math.min(MAX_VISIBLE_ROWS, maxRowsByHeight);
        int listHeight = visibleRows * ROW_HEIGHT;
        int cancelY = listY + listHeight + BUTTON_GAP;

        return new MemberSelectionDialogLayout(
                dialogX,
                dialogWidth,
                searchY,
                SEARCH_HEIGHT,
                dialogX,
                listY,
                dialogWidth,
                listHeight,
                ROW_HEIGHT,
                visibleRows,
                dialogX,
                cancelY,
                dialogWidth,
                BUTTON_HEIGHT
        );
    }

    /**
     * 将滚动偏移限制在当前列表的有效范围内。
     *
     * @param scrollOffset 当前滚动偏移
     * @param totalItems 列表总项数
     * @param visibleRows 当前可见行数
     * @return 修正后的滚动偏移
     */
    static int clampScrollOffset(int scrollOffset, int totalItems, int visibleRows) {
        int maxOffset = getMaxScrollOffset(totalItems, visibleRows);
        if (scrollOffset < 0) {
            return 0;
        }
        if (scrollOffset > maxOffset) {
            return maxOffset;
        }
        return scrollOffset;
    }

    /**
     * 计算列表在当前可见行数下允许的最大滚动偏移。
     *
     * @param totalItems 列表总项数
     * @param visibleRows 当前可见行数
     * @return 最大滚动偏移
     */
    static int getMaxScrollOffset(int totalItems, int visibleRows) {
        return Math.max(0, totalItems - Math.max(0, visibleRows));
    }

    /**
     * 判断鼠标是否位于列表滚动区域内。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 若鼠标位于列表区域内则返回 true
     */
    boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listHeight;
    }

    /**
     * 返回列表区域中心点 X 坐标，供测试驱动滚轮事件。
     *
     * @return 列表区域中心点 X 坐标
     */
    double getListCenterX() {
        return listX + (listWidth / 2.0D);
    }

    /**
     * 返回列表区域中心点 Y 坐标，供测试驱动滚轮事件。
     *
     * @return 列表区域中心点 Y 坐标
     */
    double getListCenterY() {
        return listY + (listHeight / 2.0D);
    }

    /**
     * 返回弹窗左侧 X 坐标。
     *
     * @return 弹窗左侧 X 坐标
     */
    int dialogX() {
        return dialogX;
    }

    /**
     * 返回弹窗宽度。
     *
     * @return 弹窗宽度
     */
    int dialogWidth() {
        return dialogWidth;
    }

    /**
     * 返回搜索框 Y 坐标。
     *
     * @return 搜索框 Y 坐标
     */
    int searchY() {
        return searchY;
    }

    /**
     * 返回搜索框高度。
     *
     * @return 搜索框高度
     */
    int searchHeight() {
        return searchHeight;
    }

    /**
     * 返回列表区域 X 坐标。
     *
     * @return 列表区域 X 坐标
     */
    int listX() {
        return listX;
    }

    /**
     * 返回列表区域 Y 坐标。
     *
     * @return 列表区域 Y 坐标
     */
    int listY() {
        return listY;
    }

    /**
     * 返回列表区域宽度。
     *
     * @return 列表区域宽度
     */
    int listWidth() {
        return listWidth;
    }

    /**
     * 返回列表区域高度。
     *
     * @return 列表区域高度
     */
    int listHeight() {
        return listHeight;
    }

    /**
     * 返回单行高度。
     *
     * @return 单行高度
     */
    int rowHeight() {
        return rowHeight;
    }

    /**
     * 返回当前可见行数。
     *
     * @return 当前可见行数
     */
    int visibleRows() {
        return visibleRows;
    }

    /**
     * 返回取消按钮 X 坐标。
     *
     * @return 取消按钮 X 坐标
     */
    int cancelX() {
        return cancelX;
    }

    /**
     * 返回取消按钮 Y 坐标。
     *
     * @return 取消按钮 Y 坐标
     */
    int cancelY() {
        return cancelY;
    }

    /**
     * 返回取消按钮宽度。
     *
     * @return 取消按钮宽度
     */
    int cancelWidth() {
        return cancelWidth;
    }

    /**
     * 返回取消按钮高度。
     *
     * @return 取消按钮高度
     */
    int cancelHeight() {
        return cancelHeight;
    }
}
