package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 配置界面，提供经典 HUD 配置表单和固定高度的 HUD 预览区域。
 */
public class ConfigScreen extends Screen {

    /**
     * 配置界面中 HUD 预览框使用的固定高度，避免真实 HUD 内容过多时撑大拖拽区域。
     */
    private static final int FIXED_PREVIEW_HUD_HEIGHT = 60;

    private final Screen parent;

    private EditBox hudWidthField;
    private EditBox hudMaxHeightField;
    private IntSliderWidget hudTodoLimitSlider;
    private IntSliderWidget hudDoneLimitSlider;
    private DoubleStepSliderWidget hudOpacitySlider;
    private Button hudShowWhenEmptyButton;
    private Button hudVisibilityButton;
    private Button hudProjectSourceButton;
    private Button saveButton;
    private Button cancelButton;

    private int previewHudX;
    private int previewHudY;
    private ModConfig.HudHorizontalAnchor previewHorizontalAnchor;
    private ModConfig.HudVerticalAnchor previewVerticalAnchor;
    private int previewHorizontalMargin;
    private int previewVerticalMargin;
    private int previewHudWidth;
    private int previewHudHeight;
    private int previewRectX;
    private int previewRectY;
    private boolean draggingHud;
    private boolean previewUseCustom;
    private int dragOffsetX;
    private int dragOffsetY;

    private boolean hudShowWhenEmptyValue;
    private boolean hudVisibleValue;
    private String hudProjectSourceValue;
    private int hudProjectSourceIndex;

    /**
     * 创建配置界面。
     *
     * @param parent 关闭后返回的父界面
     */
    public ConfigScreen(Screen parent) {
        super(Component.translatable("gui.todolist.config.title"));
        this.parent = parent;
    }

    /**
     * 返回 HUD 宽度输入框，供测试写入宽度。
     *
     * @return HUD 宽度输入框
     */
    EditBox getHudWidthFieldForTest() {
        return hudWidthField;
    }

    /**
     * 返回 HUD 最大高度输入框，供测试写入高度。
     *
     * @return HUD 最大高度输入框
     */
    EditBox getHudMaxHeightFieldForTest() {
        return hudMaxHeightField;
    }

    /**
     * 返回 HUD 可见性按钮，供测试点击。
     *
     * @return HUD 可见性按钮
     */
    Button getHudVisibilityButtonForTest() {
        return hudVisibilityButton;
    }

    /**
     * 返回 HUD 项目来源按钮，供测试点击。
     *
     * @return HUD 项目来源按钮
     */
    Button getHudProjectSourceButtonForTest() {
        return hudProjectSourceButton;
    }

    /**
     * 返回保存按钮，供测试触发保存。
     *
     * @return 保存按钮
     */
    Button getSaveButtonForTest() {
        return saveButton;
    }

    /**
     * 返回取消按钮，供测试触发取消。
     *
     * @return 取消按钮
     */
    Button getCancelButtonForTest() {
        return cancelButton;
    }

    /**
     * 返回 HUD 可见状态，供测试断言。
     *
     * @return true 表示 HUD 可见
     */
    boolean isHudVisibleValueForTest() {
        return hudVisibleValue;
    }

    /**
     * 返回 HUD 项目来源值，供测试断言。
     *
     * @return HUD 项目来源值
     */
    String getHudProjectSourceValueForTest() {
        return hudProjectSourceValue;
    }

    /**
     * 返回当前是否启用自定义预览定位。
     *
     * @return true 表示已切换到自定义预览定位
     */
    boolean isPreviewUseCustomForTest() {
        return previewUseCustom;
    }

    /**
     * 返回预览命中矩形 X 坐标。
     *
     * @return 预览矩形 X 坐标
     */

    /**
     * 返回预览命中矩形 Y 坐标。
     *
     * @return 预览矩形 Y 坐标
     */

    /**
     * 返回预览 HUD 宽度。
     *
     * @return 预览 HUD 宽度
     */
    int getPreviewHudWidthForTest() {
        return previewHudWidth;
    }

    /**
     * 返回预览 HUD 高度。
     *
     * @return 预览 HUD 高度
     */
    int getPreviewHudHeightForTest() {
        return previewHudHeight;
    }

    /**
     * 返回预览 HUD 当前 X 坐标。
     *
     * @return 预览 HUD 当前 X 坐标
     */
    int getPreviewHudXForTest() {
        return previewHudX;
    }

    /**
     * 返回预览 HUD 当前 Y 坐标。
     *
     * @return 预览 HUD 当前 Y 坐标
     */
    int getPreviewHudYForTest() {
        return previewHudY;
    }

    /**
     * 同步预览矩形到当前 HUD 绝对坐标，供测试拖拽复用。
     */
    void syncPreviewRectForTest() {
        previewRectX = previewHudX;
        previewRectY = previewHudY;
    }

    /**
     * 初始化经典配置表单和 HUD 预览区域。
     */
    @Override
    protected void init() {
        ModConfig config = ModConfig.getInstance();
        int guiWidth = clampInt(this.width - 40, 360, 560);
        int x = (this.width - guiWidth) / 2;
        int y = Math.max(36, this.height / 6 + 10);
        int row = 0;
        int rowHeight = 25;
        int fieldHeight = 20;

        int columnGap = 20;
        int columnWidth = (guiWidth - columnGap) / 2;
        int leftLabelWidth = 80;
        int rightLabelWidth = 80;
        int leftFieldWidth = columnWidth - leftLabelWidth;
        int rightFieldWidth = columnWidth - rightLabelWidth;
        int leftFieldX = x + leftLabelWidth;
        int rightFieldX = x + columnWidth + columnGap + rightLabelWidth;

        hudWidthField = new EditBox(this.font, leftFieldX, y + row * rowHeight, leftFieldWidth, fieldHeight, Component.empty());
        hudWidthField.setValue(Integer.toString(config.getHudWidth()));
        this.addRenderableWidget(hudWidthField);

        hudMaxHeightField = new EditBox(this.font, rightFieldX, y + row * rowHeight, rightFieldWidth, fieldHeight, Component.empty());
        hudMaxHeightField.setValue(Integer.toString(config.getHudMaxHeight()));
        this.addRenderableWidget(hudMaxHeightField);
        row++;

        hudTodoLimitSlider = new IntSliderWidget(leftFieldX, y + row * rowHeight, leftFieldWidth, fieldHeight, 0, 30, config.getHudTodoLimit());
        hudDoneLimitSlider = new IntSliderWidget(rightFieldX, y + row * rowHeight, rightFieldWidth, fieldHeight, 0, 30, config.getHudDoneLimit());
        this.addRenderableWidget(hudTodoLimitSlider);
        this.addRenderableWidget(hudDoneLimitSlider);
        row++;

        hudShowWhenEmptyValue = config.isHudShowWhenEmpty();
        hudShowWhenEmptyButton = Button.builder(Component.empty(), button -> {
            hudShowWhenEmptyValue = !hudShowWhenEmptyValue;
            updateHudShowWhenEmptyButtonLabel();
        }).bounds(leftFieldX, y + row * rowHeight, leftFieldWidth, fieldHeight).build();
        this.addRenderableWidget(hudShowWhenEmptyButton);

        hudVisibleValue = ClientBridge.ops().isHudVisible();
        hudVisibilityButton = Button.builder(Component.empty(), button -> {
            hudVisibleValue = !hudVisibleValue;
            ClientBridge.ops().setHudVisible(hudVisibleValue);
            updateHudVisibilityButtonLabel();
        }).bounds(rightFieldX, y + row * rowHeight, rightFieldWidth, fieldHeight).build();
        this.addRenderableWidget(hudVisibilityButton);
        row++;

        hudOpacitySlider = new DoubleStepSliderWidget(leftFieldX, y + row * rowHeight, leftFieldWidth, fieldHeight, 0.0D, 1.0D, 0.1D, config.getHudOpacity());
        this.addRenderableWidget(hudOpacitySlider);
        row++;

        hudProjectSourceValue = normalizeHudProjectSource(config.getHudProjectSource());
        hudProjectSourceIndex = resolveHudProjectSourceIndex(hudProjectSourceValue);
        int sourceLabelWidth = this.font.width(Component.translatable("gui.todolist.config.hud_project_source"));
        int sourceButtonX = x + sourceLabelWidth + 10;
        int sourceButtonWidth = Math.max(80, guiWidth - (sourceButtonX - x));
        hudProjectSourceButton = Button.builder(Component.empty(), button -> {
            hudProjectSourceIndex = (hudProjectSourceIndex + 1) % HudProjectSourceOptions.VALUES.length;
            hudProjectSourceValue = HudProjectSourceOptions.VALUES[hudProjectSourceIndex];
            updateHudProjectSourceButtonLabel();
        }).bounds(sourceButtonX, y + row * rowHeight, sourceButtonWidth, fieldHeight).build();
        this.addRenderableWidget(hudProjectSourceButton);
        row++;

        updateHudShowWhenEmptyButtonLabel();
        updateHudVisibilityButtonLabel();
        updateHudProjectSourceButtonLabel();

        previewUseCustom = config.isHudUseCustomPosition();
        previewHudWidth = Math.max(1, config.getHudWidth());
        previewHudHeight = Math.max(1, resolvePreviewHudHeight());
        syncPreviewPositionFromConfig(config);

        int buttonY = y + row * rowHeight + 30;
        saveButton = Button.builder(Component.translatable("gui.todolist.config.save_apply"), button -> applyAndReturn())
                .bounds(x, buttonY, guiWidth / 2 - 5, 20)
                .build();
        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> this.minecraft.setScreen(parent))
                .bounds(x + guiWidth / 2 + 5, buttonY, guiWidth / 2 - 5, 20)
                .build();
        this.addRenderableWidget(saveButton);
        this.addRenderableWidget(cancelButton);
    }

    /**
     * 渲染经典配置表单和 HUD 预览区域。
     *
     * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间插值
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int guiWidth = clampInt(this.width - 40, 360, 560);
        int x = (this.width - guiWidth) / 2;
        int y = Math.max(36, this.height / 6 + 10);
        int textHeight = this.font.lineHeight;
        context.drawString(this.font, title, x, y - 36, 0xFFFFFFFF, false);

        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_width"), hudWidthField, textHeight);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_max_height"), hudMaxHeightField, textHeight);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_todo_limit"), hudTodoLimitSlider, textHeight);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_done_limit"), hudDoneLimitSlider, textHeight);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_show_when_empty"), hudShowWhenEmptyButton, textHeight);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_visibility"), hudVisibilityButton, textHeight);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_opacity"), hudOpacitySlider, textHeight);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_project_source"), hudProjectSourceButton, textHeight);

        previewHudWidth = Math.max(1, parseIntSafe(hudWidthField.getValue(), ModConfig.getInstance().getHudWidth()));
        previewHudHeight = Math.max(1, resolvePreviewHudHeight());
        if (previewUseCustom) {
            applyPreviewAnchorsToAbsolutePosition();
        } else {
            syncPreviewPositionFromConfig(ModConfig.getInstance());
        }

        previewRectX = previewHudX;
        previewRectY = previewHudY;

        double opacity = hudOpacitySlider == null ? ModConfig.getInstance().getHudOpacity() : hudOpacitySlider.getDoubleValue();
        int alpha = (int) Math.round(clampRatio(opacity) * 255.0D);
        context.fill(previewHudX, previewHudY, previewHudX + previewHudWidth, previewHudY + previewHudHeight, alpha << 24);
        context.renderOutline(previewHudX, previewHudY, previewHudWidth, previewHudHeight, 0xFFFFFFFF);

        Component previewTitle = Component.translatable("gui.todolist.config.hud_preview.title");
        Component previewHint = Component.translatable("gui.todolist.config.hud_preview.hint");
        int centerX = previewHudX + previewHudWidth / 2;
        int centerY = previewHudY + previewHudHeight / 2;
        int totalTextHeight = textHeight * 2 + 2;
        int startY = centerY - totalTextHeight / 2;
        context.drawString(this.font, previewTitle, centerX - this.font.width(previewTitle) / 2, startY, 0xFFFFFF, false);
        context.drawString(this.font, previewHint, centerX - this.font.width(previewHint) / 2, startY + textHeight + 2, 0xFFFFFF, false);
    }

    /**
     * 处理 HUD 预览框点击事件，命中后进入拖拽模式。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @return 命中预览框时返回 true
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (int) mouseX;
            int y = (int) mouseY;
            if (x >= previewRectX && x <= previewRectX + previewHudWidth
                    && y >= previewRectY && y <= previewRectY + previewHudHeight) {
                draggingHud = true;
                dragOffsetX = x - previewRectX;
                dragOffsetY = y - previewRectY;
                if (!previewUseCustom) {
                    updatePreviewAnchorsFromAbsolutePosition();
                }
                previewUseCustom = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 结束 HUD 预览框的拖拽状态。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @return 本次释放结束了拖拽时返回 true
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingHud) {
            draggingHud = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 拖拽 HUD 预览框，并同步新的预览锚点。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @param deltaX 鼠标 X 偏移
     * @param deltaY 鼠标 Y 偏移
     * @return 处理了拖拽时返回 true
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingHud) {
            int newX = (int) mouseX - dragOffsetX;
            int newY = (int) mouseY - dragOffsetY;
            previewHudX = clampPreviewCoordinate(newX, this.width, previewHudWidth);
            previewHudY = clampPreviewCoordinate(newY, this.height, previewHudHeight);
            updatePreviewAnchorsFromAbsolutePosition();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /**
     * 保存当前配置表单内容，并返回父界面。
     */
    private void applyAndReturn() {
        ModConfig config = ModConfig.getInstance();
        config.setHudWidth(parseIntSafe(hudWidthField.getValue(), config.getHudWidth()));
        config.setHudMaxHeight(parseIntSafe(hudMaxHeightField.getValue(), config.getHudMaxHeight()));
        config.setHudTodoLimit(hudTodoLimitSlider.getIntValue());
        config.setHudDoneLimit(hudDoneLimitSlider.getIntValue());
        config.setHudOpacity(hudOpacitySlider.getDoubleValue());
        config.setHudShowWhenEmpty(hudShowWhenEmptyValue);
        config.setHudProjectSource(hudProjectSourceValue);

        previewHudWidth = Math.max(1, config.getHudWidth());
        previewHudHeight = Math.max(1, resolvePreviewHudHeight());
        if (previewUseCustom) {
            config.updateHudCustomPosition(previewHudX, previewHudY, this.width, this.height, previewHudWidth, previewHudHeight);
        } else {
            config.setHudUseCustomPosition(false);
        }
        ClientBridge.ops().setHudVisible(hudVisibleValue);
        this.minecraft.setScreen(parent);
    }

    /**
     * 按当前配置刷新预览框的绝对坐标。
     *
     * @param config 当前模组配置
     */
    private void syncPreviewPositionFromConfig(ModConfig config) {
        previewHudWidth = Math.max(1, previewHudWidth);
        previewHudHeight = Math.max(1, previewHudHeight);
        if (previewUseCustom) {
            if (config.hasHudCustomAnchors()) {
                previewHorizontalAnchor = config.getHudCustomHorizontalAnchor();
                previewVerticalAnchor = config.getHudCustomVerticalAnchor();
                previewHorizontalMargin = config.getHudCustomHorizontalMargin();
                previewVerticalMargin = config.getHudCustomVerticalMargin();
                applyPreviewAnchorsToAbsolutePosition();
            } else {
                previewHudX = resolveLegacyPreviewCoordinate(
                        config.getHudCustomX(),
                        config.getHudCustomXRatio(),
                        config.hasHudCustomPositionRatios(),
                        this.width,
                        previewHudWidth
                );
                previewHudY = resolveLegacyPreviewCoordinate(
                        config.getHudCustomY(),
                        config.getHudCustomYRatio(),
                        config.hasHudCustomPositionRatios(),
                        this.height,
                        previewHudHeight
                );
                updatePreviewAnchorsFromAbsolutePosition();
            }
        } else {
            ModConfig.HudPlacement placement = config.resolveHudPlacement(this.width, this.height, previewHudWidth, previewHudHeight);
            previewHudX = placement.getX();
            previewHudY = placement.getY();
            updatePreviewAnchorsFromAbsolutePosition();
        }
    }

    /**
     * 返回配置界面中固定使用的 HUD 预览高度，避免任务过多时拖拽框被撑大。
     *
     * @return 固定预览高度
     */
    private int resolvePreviewHudHeight() {
        return FIXED_PREVIEW_HUD_HEIGHT;
    }

    /**
     * 从旧版绝对值或比例值还原预览坐标。
     *
     * @param absoluteCoordinate 旧版绝对坐标
     * @param ratioCoordinate 旧版比例坐标
     * @param preferRatio 是否优先使用比例坐标
     * @param screenSize 屏幕尺寸
     * @param hudSize HUD 尺寸
     * @return 裁剪后的绝对坐标
     */
    private int resolveLegacyPreviewCoordinate(int absoluteCoordinate, double ratioCoordinate, boolean preferRatio,
                                               int screenSize, int hudSize) {
        if (preferRatio) {
            int maxCoordinate = Math.max(0, screenSize - Math.max(0, hudSize));
            return clampPreviewCoordinate((int) Math.round(clampRatio(ratioCoordinate) * maxCoordinate), screenSize, hudSize);
        }
        return clampPreviewCoordinate(absoluteCoordinate, screenSize, hudSize);
    }

    /**
     * 将比例值裁剪到 0 到 1 的范围内。
     *
     * @param ratio 原始比例
     * @return 裁剪后的比例
     */
    private static double clampRatio(double ratio) {
        if (ratio < 0.0D) {
            return 0.0D;
        }
        if (ratio > 1.0D) {
            return 1.0D;
        }
        return ratio;
    }

    /**
     * 根据当前绝对坐标刷新预览框的锚点和边距。
     */
    private void updatePreviewAnchorsFromAbsolutePosition() {
        previewHudX = clampPreviewCoordinate(previewHudX, this.width, previewHudWidth);
        previewHudY = clampPreviewCoordinate(previewHudY, this.height, previewHudHeight);

        int leftMargin = previewHudX;
        int rightMargin = Math.max(0, this.width - Math.max(0, previewHudWidth) - previewHudX);
        if (leftMargin <= rightMargin) {
            previewHorizontalAnchor = ModConfig.HudHorizontalAnchor.LEFT;
            previewHorizontalMargin = leftMargin;
        } else {
            previewHorizontalAnchor = ModConfig.HudHorizontalAnchor.RIGHT;
            previewHorizontalMargin = rightMargin;
        }

        int topMargin = previewHudY;
        int bottomMargin = Math.max(0, this.height - Math.max(0, previewHudHeight) - previewHudY);
        if (topMargin <= bottomMargin) {
            previewVerticalAnchor = ModConfig.HudVerticalAnchor.TOP;
            previewVerticalMargin = topMargin;
        } else {
            previewVerticalAnchor = ModConfig.HudVerticalAnchor.BOTTOM;
            previewVerticalMargin = bottomMargin;
        }
    }

    /**
     * 根据当前锚点和边距恢复预览框的绝对坐标。
     */
    private void applyPreviewAnchorsToAbsolutePosition() {
        int resolvedX = previewHorizontalAnchor == ModConfig.HudHorizontalAnchor.LEFT
                ? previewHorizontalMargin
                : this.width - Math.max(0, previewHudWidth) - previewHorizontalMargin;
        int resolvedY = previewVerticalAnchor == ModConfig.HudVerticalAnchor.TOP
                ? previewVerticalMargin
                : this.height - Math.max(0, previewHudHeight) - previewVerticalMargin;
        previewHudX = clampPreviewCoordinate(resolvedX, this.width, previewHudWidth);
        previewHudY = clampPreviewCoordinate(resolvedY, this.height, previewHudHeight);
    }

    /**
     * 安全解析整型输入。
     *
     * @param value 原始输入
     * @param fallback 回退值
     * @return 解析结果或回退值
     */
    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * 刷新“无任务时仍显示 HUD”按钮文案。
     */
    private void updateHudShowWhenEmptyButtonLabel() {
        if (hudShowWhenEmptyButton == null) {
            return;
        }
        String key = hudShowWhenEmptyValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
        hudShowWhenEmptyButton.setMessage(Component.translatable(key));
    }

    /**
     * 刷新 HUD 可见性按钮文案。
     */
    private void updateHudVisibilityButtonLabel() {
        if (hudVisibilityButton == null) {
            return;
        }
        String key = hudVisibleValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
        hudVisibilityButton.setMessage(Component.translatable(key));
    }

    /**
     * 刷新 HUD 项目来源按钮文案。
     */
    private void updateHudProjectSourceButtonLabel() {
        if (hudProjectSourceButton == null) {
            return;
        }
        if ("CURRENT".equalsIgnoreCase(hudProjectSourceValue)) {
            Project.Scope scope = resolveHudScope();
            Project project = getActiveProject(scope);
            Component projectName = getProjectDisplayName(project);
            if (projectName != null && !projectName.getString().trim().isEmpty()) {
                hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.current_selected", projectName.getString()));
            } else {
                hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.current_selected_fallback"));
            }
            return;
        }
        if ("STARRED".equalsIgnoreCase(hudProjectSourceValue)) {
            hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.starred"));
            return;
        }
        hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.all"));
    }

    /**
     * 规范化 HUD 项目来源值。
     *
     * @param source 原始来源值
     * @return 规范化后的来源值
     */
    private String normalizeHudProjectSource(String source) {
        if (source == null || source.isBlank()) {
            return HudProjectSourceOptions.VALUES[2];
        }
        for (String option : HudProjectSourceOptions.VALUES) {
            if (option.equalsIgnoreCase(source)) {
                return option;
            }
        }
        return HudProjectSourceOptions.VALUES[2];
    }

    /**
     * 解析 HUD 项目来源的索引。
     *
     * @param source 当前来源值
     * @return 对应的索引
     */
    private int resolveHudProjectSourceIndex(String source) {
        for (int index = 0; index < HudProjectSourceOptions.VALUES.length; index++) {
            if (HudProjectSourceOptions.VALUES[index].equalsIgnoreCase(source)) {
                return index;
            }
        }
        return 0;
    }

    /**
     * 根据 HUD 默认视图解析当前项目作用域。
     *
     * @return 对应的项目作用域
     */
    private Project.Scope resolveHudScope() {
        String view = ModConfig.getInstance().getHudDefaultView();
        if ("TEAM_UNASSIGNED".equalsIgnoreCase(view)
                || "TEAM_ALL".equalsIgnoreCase(view)
                || "TEAM_ASSIGNED".equalsIgnoreCase(view)) {
            return Project.Scope.TEAM;
        }
        return Project.Scope.PERSONAL;
    }

    /**
     * 获取当前作用域下的激活项目。
     *
     * @param scope 目标作用域
     * @return 当前激活项目
     */
    private static Project getActiveProject(Project.Scope scope) {
        return ClientBridge.getActiveProject(TodoListCommon.getProjectManager(), scope);
    }

    /**
     * 获取项目显示名称。
     *
     * @param project 目标项目
     * @return 项目显示文本
     */
    private static Component getProjectDisplayName(Project project) {
        return ProjectNameFormatter.toDisplayText(project);
    }

    /**
     * 绘制位于控件左侧的标签文本。
     *
     * @param context 绘制上下文
     * @param label 标签文本
     * @param widget 目标控件
     * @param textHeight 文本高度
     */
    private void drawLabelForWidget(GuiGraphics context, Component label, AbstractWidget widget, int textHeight) {
        if (widget == null || !widget.visible) {
            return;
        }
        int labelY = widget.getY() + (widget.getHeight() - textHeight) / 2;
        int labelX = Math.max(8, widget.getX() - this.font.width(label) - 8);
        context.drawString(this.font, label, labelX, labelY, 0xFFFFFF, false);
    }

    /**
     * 将整型值裁剪到指定范围内。
     *
     * @param value 原始值
     * @param min 最小值
     * @param max 最大值
     * @return 裁剪后的值
     */
    private static int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * 将预览坐标裁剪到当前屏幕范围内。
     *
     * @param coordinate 原始坐标
     * @param screenSize 屏幕尺寸
     * @param hudSize HUD 尺寸
     * @return 合法的预览坐标
     */
    private static int clampPreviewCoordinate(int coordinate, int screenSize, int hudSize) {
        int maxCoordinate = Math.max(0, screenSize - Math.max(0, hudSize));
        if (coordinate < 0) {
            return 0;
        }
        if (coordinate > maxCoordinate) {
            return maxCoordinate;
        }
        return coordinate;
    }

    /**
     * HUD 项目来源常量集合。
     */
    private static final class HudProjectSourceOptions {
        private static final String[] VALUES = new String[] {"CURRENT", "STARRED", "ALL"};

        /**
         * 私有构造方法，避免工具类被实例化。
         */
        private HudProjectSourceOptions() {
        }
    }

    /**
     * 整型滑块控件，用于待办数量和已办数量设置。
     */
    private static class IntSliderWidget extends AbstractSliderButton {
        private final int min;
        private final int max;

        /**
         * 创建整型滑块。
         *
         * @param x 组件 X 坐标
         * @param y 组件 Y 坐标
         * @param width 组件宽度
         * @param height 组件高度
         * @param min 最小值
         * @param max 最大值
         * @param value 初始值
         */
        IntSliderWidget(int x, int y, int width, int height, int min, int max, int value) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.min = min;
            this.max = max;
            setValueFromInt(value);
        }

        /**
         * 根据整型值设置滑块比例。
         *
         * @param value 目标值
         */
        private void setValueFromInt(int value) {
            int clamped = Math.max(min, Math.min(max, value));
            this.value = max == min ? 0.0D : (double) (clamped - min) / (double) (max - min);
            updateMessage();
        }

        /**
         * 返回当前整型值。
         *
         * @return 当前整型值
         */
        int getIntValue() {
            if (max == min) {
                return min;
            }
            int range = max - min;
            int resolved = (int) Math.round(this.value * range) + min;
            return clampInt(resolved, min, max);
        }

        /**
         * 刷新滑块显示文本。
         */
        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(Integer.toString(getIntValue())));
        }

        /**
         * 当前滑块不需要额外的提交逻辑。
         */
        @Override
        protected void applyValue() {
        }
    }

    /**
     * 浮点步进滑块控件，用于透明度设置。
     */
    private static class DoubleStepSliderWidget extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;

        /**
         * 创建浮点步进滑块。
         *
         * @param x 组件 X 坐标
         * @param y 组件 Y 坐标
         * @param width 组件宽度
         * @param height 组件高度
         * @param min 最小值
         * @param max 最大值
         * @param step 步进值
         * @param value 初始值
         */
        DoubleStepSliderWidget(int x, int y, int width, int height, double min, double max, double step, double value) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.min = min;
            this.max = max;
            this.step = step;
            setValueFromDouble(value);
        }

        /**
         * 根据浮点值设置滑块比例。
         *
         * @param value 目标值
         */
        private void setValueFromDouble(double value) {
            double clamped = Math.max(min, Math.min(max, value));
            double stepped = Math.round(clamped / step) * step;
            this.value = max == min ? 0.0D : clampRatio((stepped - min) / (max - min));
            updateMessage();
        }

        /**
         * 返回当前浮点值。
         *
         * @return 当前浮点值
         */
        double getDoubleValue() {
            double raw = min + clampRatio(this.value) * (max - min);
            double stepped = Math.round(raw / step) * step;
            if (stepped < min) {
                return min;
            }
            if (stepped > max) {
                return max;
            }
            return stepped;
        }

        /**
         * 刷新滑块显示文本。
         */
        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format(java.util.Locale.ROOT, "%.1f", getDoubleValue())));
        }

        /**
         * 当前滑块不需要额外的提交逻辑。
         */
        @Override
        protected void applyValue() {
        }
    }
}
