package com.todolist.gui;

/**
 * 配置页布局快照。
 * 负责记录配置列表区、预览区和底部按钮区的响应式边界与列数。
 */
final class ConfigLayout {
    private final int columns;
    private final int titleX;
    private final int titleY;
    private final int configListX;
    private final int configListY;
    private final int configListWidth;
    private final int configListHeight;
    private final int previewPanelX;
    private final int previewPanelY;
    private final int previewPanelWidth;
    private final int previewPanelHeight;
    private final int previewWorkspaceX;
    private final int previewWorkspaceY;
    private final int previewWorkspaceWidth;
    private final int previewWorkspaceHeight;
    private final int saveButtonX;
    private final int saveButtonY;
    private final int saveButtonWidth;
    private final int cancelButtonX;
    private final int cancelButtonY;
    private final int cancelButtonWidth;
    private final int buttonHeight;

    /**
     * 创建配置页布局快照。
     *
     * @param columns 配置区列数
     * @param titleX 标题 X 坐标
     * @param titleY 标题 Y 坐标
     * @param configListX 配置区 X 坐标
     * @param configListY 配置区 Y 坐标
     * @param configListWidth 配置区宽度
     * @param configListHeight 配置区高度
     * @param previewPanelX 预览区 X 坐标
     * @param previewPanelY 预览区 Y 坐标
     * @param previewPanelWidth 预览区宽度
     * @param previewPanelHeight 预览区高度
     * @param previewWorkspaceX 预览工作区 X 坐标
     * @param previewWorkspaceY 预览工作区 Y 坐标
     * @param previewWorkspaceWidth 预览工作区宽度
     * @param previewWorkspaceHeight 预览工作区高度
     * @param saveButtonX 保存按钮 X 坐标
     * @param saveButtonY 保存按钮 Y 坐标
     * @param saveButtonWidth 保存按钮宽度
     * @param cancelButtonX 取消按钮 X 坐标
     * @param cancelButtonY 取消按钮 Y 坐标
     * @param cancelButtonWidth 取消按钮宽度
     * @param buttonHeight 底部按钮高度
     */
    ConfigLayout(int columns, int titleX, int titleY, int configListX, int configListY, int configListWidth,
                 int configListHeight, int previewPanelX, int previewPanelY, int previewPanelWidth,
                 int previewPanelHeight, int previewWorkspaceX, int previewWorkspaceY, int previewWorkspaceWidth,
                 int previewWorkspaceHeight, int saveButtonX, int saveButtonY, int saveButtonWidth,
                 int cancelButtonX, int cancelButtonY, int cancelButtonWidth, int buttonHeight) {
        this.columns = columns;
        this.titleX = titleX;
        this.titleY = titleY;
        this.configListX = configListX;
        this.configListY = configListY;
        this.configListWidth = configListWidth;
        this.configListHeight = configListHeight;
        this.previewPanelX = previewPanelX;
        this.previewPanelY = previewPanelY;
        this.previewPanelWidth = previewPanelWidth;
        this.previewPanelHeight = previewPanelHeight;
        this.previewWorkspaceX = previewWorkspaceX;
        this.previewWorkspaceY = previewWorkspaceY;
        this.previewWorkspaceWidth = previewWorkspaceWidth;
        this.previewWorkspaceHeight = previewWorkspaceHeight;
        this.saveButtonX = saveButtonX;
        this.saveButtonY = saveButtonY;
        this.saveButtonWidth = saveButtonWidth;
        this.cancelButtonX = cancelButtonX;
        this.cancelButtonY = cancelButtonY;
        this.cancelButtonWidth = cancelButtonWidth;
        this.buttonHeight = buttonHeight;
    }

    /**
     * 返回配置区列数。
     *
     * @return 配置区列数
     */
    int columns() {
        return columns;
    }

    /**
     * 返回标题 X 坐标。
     *
     * @return 标题 X 坐标
     */
    int titleX() {
        return titleX;
    }

    /**
     * 返回标题 Y 坐标。
     *
     * @return 标题 Y 坐标
     */
    int titleY() {
        return titleY;
    }

    /**
     * 返回配置区 X 坐标。
     *
     * @return 配置区 X 坐标
     */
    int configListX() {
        return configListX;
    }

    /**
     * 返回配置区 Y 坐标。
     *
     * @return 配置区 Y 坐标
     */
    int configListY() {
        return configListY;
    }

    /**
     * 返回配置区宽度。
     *
     * @return 配置区宽度
     */
    int configListWidth() {
        return configListWidth;
    }

    /**
     * 返回配置区高度。
     *
     * @return 配置区高度
     */
    int configListHeight() {
        return configListHeight;
    }

    /**
     * 返回预览区 X 坐标。
     *
     * @return 预览区 X 坐标
     */
    int previewPanelX() {
        return previewPanelX;
    }

    /**
     * 返回预览区 Y 坐标。
     *
     * @return 预览区 Y 坐标
     */
    int previewPanelY() {
        return previewPanelY;
    }

    /**
     * 返回预览区宽度。
     *
     * @return 预览区宽度
     */
    int previewPanelWidth() {
        return previewPanelWidth;
    }

    /**
     * 返回预览区高度。
     *
     * @return 预览区高度
     */
    int previewPanelHeight() {
        return previewPanelHeight;
    }

    /**
     * 返回预览工作区 X 坐标。
     *
     * @return 预览工作区 X 坐标
     */
    int previewWorkspaceX() {
        return previewWorkspaceX;
    }

    /**
     * 返回预览工作区 Y 坐标。
     *
     * @return 预览工作区 Y 坐标
     */
    int previewWorkspaceY() {
        return previewWorkspaceY;
    }

    /**
     * 返回预览工作区宽度。
     *
     * @return 预览工作区宽度
     */
    int previewWorkspaceWidth() {
        return previewWorkspaceWidth;
    }

    /**
     * 返回预览工作区高度。
     *
     * @return 预览工作区高度
     */
    int previewWorkspaceHeight() {
        return previewWorkspaceHeight;
    }

    /**
     * 返回保存按钮 X 坐标。
     *
     * @return 保存按钮 X 坐标
     */
    int saveButtonX() {
        return saveButtonX;
    }

    /**
     * 返回保存按钮 Y 坐标。
     *
     * @return 保存按钮 Y 坐标
     */
    int saveButtonY() {
        return saveButtonY;
    }

    /**
     * 返回保存按钮宽度。
     *
     * @return 保存按钮宽度
     */
    int saveButtonWidth() {
        return saveButtonWidth;
    }

    /**
     * 返回取消按钮 X 坐标。
     *
     * @return 取消按钮 X 坐标
     */
    int cancelButtonX() {
        return cancelButtonX;
    }

    /**
     * 返回取消按钮 Y 坐标。
     *
     * @return 取消按钮 Y 坐标
     */
    int cancelButtonY() {
        return cancelButtonY;
    }

    /**
     * 返回取消按钮宽度。
     *
     * @return 取消按钮宽度
     */
    int cancelButtonWidth() {
        return cancelButtonWidth;
    }

    /**
     * 返回底部按钮高度。
     *
     * @return 底部按钮高度
     */
    int buttonHeight() {
        return buttonHeight;
    }

    /**
     * 判断鼠标是否位于配置区内。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 若鼠标位于配置区内则返回 true
     */
    boolean isInsideConfigList(double mouseX, double mouseY) {
        return mouseX >= configListX && mouseX <= configListX + configListWidth
                && mouseY >= configListY && mouseY <= configListY + configListHeight;
    }

    /**
     * 返回配置区中心点 X 坐标。
     *
     * @return 配置区中心点 X 坐标
     */
    double getConfigListCenterX() {
        return configListX + (configListWidth / 2.0D);
    }

    /**
     * 返回配置区中心点 Y 坐标。
     *
     * @return 配置区中心点 Y 坐标
     */
    double getConfigListCenterY() {
        return configListY + (configListHeight / 2.0D);
    }
}
