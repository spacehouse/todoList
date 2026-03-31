package com.todolist.gui;

import com.todolist.client.ClientBridge;
import com.todolist.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置界面：负责编辑 HUD 配置草稿、预览 HUD 位置，并在保存时统一写回配置。
 */
public class ConfigScreen extends Screen {

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
    private HudConfigDraft draft;
    private ConfigLayout layout;
    private List<ConfigRowModel> configRows = new ArrayList<>();
    private int configListScrollOffset;

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
     * 返回 HUD 宽度输入框，供测试写入宽度值。
     *
     * @return HUD 宽度输入框
     */
    EditBox getHudWidthFieldForTest() {
        return hudWidthField;
    }

    /**
     * 返回 HUD 最大高度输入框，供测试写入高度值。
     *
     * @return HUD 最大高度输入框
     */
    EditBox getHudMaxHeightFieldForTest() {
        return hudMaxHeightField;
    }

    /**
     * 返回 HUD 显示切换按钮，供测试触发显隐切换。
     *
     * @return HUD 显示按钮
     */
    Button getHudVisibilityButtonForTest() {
        return hudVisibilityButton;
    }

    /**
     * 返回 HUD 项目来源切换按钮，供测试驱动来源切换。
     *
     * @return HUD 项目来源按钮
     */
    Button getHudProjectSourceButtonForTest() {
        return hudProjectSourceButton;
    }

    /**
     * 返回保存按钮，供测试直接触发保存流程。
     *
     * @return 保存按钮
     */
    Button getSaveButtonForTest() {
        return saveButton;
    }

    /**
     * 返回取消按钮，供测试直接触发取消流程。
     *
     * @return 取消按钮
     */
    Button getCancelButtonForTest() {
        return cancelButton;
    }

    /**
     * 返回当前草稿中的 HUD 可见值。
     *
     * @return true 表示 HUD 可见
     */
    boolean isHudVisibleValueForTest() {
        return hudVisibleValue;
    }

    /**
     * 返回当前草稿中的 HUD 项目来源值。
     *
     * @return HUD 项目来源值
     */
    String getHudProjectSourceValueForTest() {
        return hudProjectSourceValue;
    }

    /**
     * 返回当前预览是否使用自定义位置。
     *
     * @return true 表示使用自定义位置
     */
    boolean isPreviewUseCustomForTest() {
        return previewUseCustom;
    }

    /**
     * 返回预览矩形左上角 X 坐标。
     *
     * @return 预览矩形左上角 X 坐标
     */
    int getPreviewRectXForTest() {
        return previewRectX;
    }

    /**
     * 返回预览矩形左上角 Y 坐标。
     *
     * @return 预览矩形左上角 Y 坐标
     */
    int getPreviewRectYForTest() {
        return previewRectY;
    }

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
     * 返回当前配置页列数，供测试验证单双列切换。
     *
     * @return 当前配置页列数
     */
    int getConfigColumnCountForTest() {
        return layout == null ? 0 : layout.columns();
    }

    /**
     * 返回配置列表区域边界，供测试验证滚动区位置。
     *
     * @return 配置列表边界 [x, y, width, height]
     */
    int[] getConfigListBoundsForTest() {
        if (layout == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {layout.configListX(), layout.configListY(), layout.configListWidth(), layout.configListHeight()};
    }

    /**
     * 返回预览面板边界，供测试验证固定预览区位置。
     *
     * @return 预览面板边界 [x, y, width, height]
     */
    int[] getPreviewPanelBoundsForTest() {
        if (layout == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {layout.previewPanelX(), layout.previewPanelY(), layout.previewPanelWidth(), layout.previewPanelHeight()};
    }

    /**
     * 返回配置区当前是否溢出。
     *
     * @return 若配置区超出可见高度则返回 true
     */
    boolean isConfigListOverflowingForTest() {
        return isConfigListOverflowing();
    }

    /**
     * 返回配置区中心点 X 坐标，供测试驱动滚轮事件。
     *
     * @return 配置区中心点 X 坐标
     */
    double getConfigListCenterXForTest() {
        return layout == null ? 0.0D : layout.getConfigListCenterX();
    }

    /**
     * 返回配置区中心点 Y 坐标，供测试驱动滚轮事件。
     *
     * @return 配置区中心点 Y 坐标
     */
    double getConfigListCenterYForTest() {
        return layout == null ? 0.0D : layout.getConfigListCenterY();
    }

    /**
     * 返回当前配置区滚动偏移。
     *
     * @return 当前配置区滚动偏移
     */
    int getConfigListScrollOffsetForTest() {
        return configListScrollOffset;
    }

    /**
     * 返回当前配置区允许的最大滚动偏移。
     *
     * @return 最大滚动偏移
     */
    int getConfigListMaxScrollOffsetForTest() {
        return getConfigListMaxScrollOffset();
    }

    /**
     * 返回草稿中的预览块 X 坐标。
     *
     * @return 草稿中的预览块 X 坐标
     */
    int getDraftPreviewHudXForTest() {
        return draft == null ? previewHudX : draft.previewHudX;
    }

    /**
     * 返回草稿中的预览块 Y 坐标。
     *
     * @return 草稿中的预览块 Y 坐标
     */
    int getDraftPreviewHudYForTest() {
        return draft == null ? previewHudY : draft.previewHudY;
    }

    /**
     * 同步测试读取用的预览矩形边界。
     */
    void syncPreviewRectForTest() {
        previewRectX = previewHudX;
        previewRectY = previewHudY;
    }

    /**
     * 初始化配置页中的草稿、组件和响应式布局。
     */
    @Override
    protected void init() {
        if (draft == null) {
            draft = loadDraftFromConfig();
        } else {
            syncDraftFromWidgets();
        }
        configListScrollOffset = 0;
        configRows = new ArrayList<>();

        hudWidthField = new EditBox(this.font, 0, 0, 120, 20, Component.empty());
        hudWidthField.setValue(Integer.toString(draft.hudWidth));
        this.addRenderableWidget(hudWidthField);

        hudMaxHeightField = new EditBox(this.font, 0, 0, 120, 20, Component.empty());
        hudMaxHeightField.setValue(Integer.toString(draft.hudMaxHeight));
        this.addRenderableWidget(hudMaxHeightField);

        hudTodoLimitSlider = new IntSliderWidget(0, 0, 120, 20, 0, 30, draft.hudTodoLimit);
        this.addRenderableWidget(hudTodoLimitSlider);

        hudDoneLimitSlider = new IntSliderWidget(0, 0, 120, 20, 0, 30, draft.hudDoneLimit);
        this.addRenderableWidget(hudDoneLimitSlider);

        hudOpacitySlider = new DoubleStepSliderWidget(0, 0, 120, 20, 0.0, 1.0, 0.1, draft.hudOpacity);
        this.addRenderableWidget(hudOpacitySlider);

        hudShowWhenEmptyValue = draft.hudShowWhenEmpty;
        hudShowWhenEmptyButton = Button.builder(Component.empty(), button -> {
            draft.hudShowWhenEmpty = !draft.hudShowWhenEmpty;
            hudShowWhenEmptyValue = draft.hudShowWhenEmpty;
            updateHudShowWhenEmptyButtonLabel();
        }).bounds(0, 0, 120, 20).build();
        this.addRenderableWidget(hudShowWhenEmptyButton);

        hudVisibleValue = draft.hudVisible;
        hudVisibilityButton = Button.builder(Component.empty(), button -> {
            draft.hudVisible = !draft.hudVisible;
            hudVisibleValue = draft.hudVisible;
            updateHudVisibilityButtonLabel();
        }).bounds(0, 0, 120, 20).build();
        this.addRenderableWidget(hudVisibilityButton);

        hudProjectSourceValue = normalizeHudProjectSource(draft.hudProjectSource);
        hudProjectSourceIndex = resolveHudProjectSourceIndex(hudProjectSourceValue);
        hudProjectSourceButton = Button.builder(Component.empty(), button -> {
            hudProjectSourceIndex = (hudProjectSourceIndex + 1) % HudProjectSourceOptions.VALUES.length;
            hudProjectSourceValue = HudProjectSourceOptions.VALUES[hudProjectSourceIndex];
            draft.hudProjectSource = hudProjectSourceValue;
            updateHudProjectSourceButtonLabel();
        }).bounds(0, 0, 120, 20).build();
        this.addRenderableWidget(hudProjectSourceButton);

        saveButton = Button.builder(Component.translatable("gui.todolist.config.save_apply"), button -> applyAndReturn())
                .bounds(0, 0, 100, 20).build();
        this.addRenderableWidget(saveButton);

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> this.minecraft.setScreen(parent))
                .bounds(0, 0, 100, 20).build();
        this.addRenderableWidget(cancelButton);

        updateHudShowWhenEmptyButtonLabel();
        updateHudVisibilityButtonLabel();
        updateHudProjectSourceButtonLabel();

        configRows = buildConfigRows();
        layout = computeResponsiveLayout();
        applyLayout();
        this.setFocused(hudWidthField);
    }

    /**
     * 渲染配置界面，并在渲染前刷新布局和预览状态。
     *
     * @param context 当前绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间插值
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        syncDraftFromWidgets();
        layout = computeResponsiveLayout();
        applyLayout();

        this.renderBackground(context);
        drawLayoutShell(context);
        super.render(context, mouseX, mouseY, delta);
        drawConfigRowLabels(context);
        drawPreviewPanel(context);
    }

    /**
     * 处理点击事件，并支持拖拽 HUD 预览块。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @return 若点击命中预览块则返回 true
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= previewRectX && mouseX <= previewRectX + previewHudWidth
                && mouseY >= previewRectY && mouseY <= previewRectY + previewHudHeight) {
            draggingHud = true;
            dragOffsetX = (int) mouseX - previewRectX;
            dragOffsetY = (int) mouseY - previewRectY;
            if (draft != null) {
                draft.previewUseCustom = true;
            }
            previewUseCustom = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 结束预览块拖拽状态。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @return 若本次释放结束了拖拽则返回 true
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
     * 在预览工作区内拖拽 HUD 预览块，并同步写回草稿位置。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @param deltaX 本次拖拽 X 增量
     * @param deltaY 本次拖拽 Y 增量
     * @return 若本次拖拽移动了预览块则返回 true
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingHud && layout != null) {
            int workspaceMaxX = layout.previewWorkspaceX() + Math.max(0, layout.previewWorkspaceWidth() - previewHudWidth);
            int workspaceMaxY = layout.previewWorkspaceY() + Math.max(0, layout.previewWorkspaceHeight() - previewHudHeight);
            previewHudX = clampInt((int) mouseX - dragOffsetX, layout.previewWorkspaceX(), workspaceMaxX);
            previewHudY = clampInt((int) mouseY - dragOffsetY, layout.previewWorkspaceY(), workspaceMaxY);
            syncDraftPreviewPositionFromBlock();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    /**
     * 只在配置区内部处理滚轮滚动，保持预览区固定。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param amount 滚轮偏移量
     * @return 若本次滚动用于配置列表则返回 true
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (layout != null && layout.isInsideConfigList(mouseX, mouseY) && isConfigListOverflowing()) {
            if (amount < 0.0D) {
                configListScrollOffset += 24;
            } else if (amount > 0.0D) {
                configListScrollOffset -= 24;
            }
            configListScrollOffset = clampInt(configListScrollOffset, 0, getConfigListMaxScrollOffset());
            applyLayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    /**
     * 保存当前草稿并返回父界面。
     */
    private void applyAndReturn() {
        syncDraftFromWidgets();
        persistDraft(draft);
        this.minecraft.setScreen(parent);
    }

    /**
     * 从当前配置和客户端桥接层中加载 HUD 配置草稿。
     *
     * @return 新创建的 HUD 配置草稿
     */
    private HudConfigDraft loadDraftFromConfig() {
        ModConfig config = ModConfig.getInstance();
        HudConfigDraft loadedDraft = new HudConfigDraft();
        loadedDraft.hudWidth = config.getHudWidth();
        loadedDraft.hudMaxHeight = config.getHudMaxHeight();
        loadedDraft.hudTodoLimit = config.getHudTodoLimit();
        loadedDraft.hudDoneLimit = config.getHudDoneLimit();
        loadedDraft.hudOpacity = config.getHudOpacity();
        loadedDraft.hudShowWhenEmpty = config.isHudShowWhenEmpty();
        loadedDraft.hudVisible = ClientBridge.ops().isHudVisible();
        loadedDraft.hudProjectSource = normalizeHudProjectSource(config.getHudProjectSource());
        loadedDraft.previewUseCustom = config.isHudUseCustomPosition();
        loadedDraft.previewHorizontalAnchor = config.hasHudCustomAnchors()
                ? config.getHudCustomHorizontalAnchor() : ModConfig.HudHorizontalAnchor.RIGHT;
        loadedDraft.previewVerticalAnchor = config.hasHudCustomAnchors()
                ? config.getHudCustomVerticalAnchor() : ModConfig.HudVerticalAnchor.TOP;
        loadedDraft.previewHorizontalMargin = config.hasHudCustomAnchors() ? config.getHudCustomHorizontalMargin() : 6;
        loadedDraft.previewVerticalMargin = config.hasHudCustomAnchors() ? config.getHudCustomVerticalMargin() : 6;
        loadedDraft.previewHudX = 0;
        loadedDraft.previewHudY = 0;
        return loadedDraft;
    }

    /**
     * 构建配置项模型列表，统一描述标签和对应组件。
     *
     * @return 配置项模型列表
     */
    private List<ConfigRowModel> buildConfigRows() {
        List<ConfigRowModel> rows = new ArrayList<>();
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_width"), hudWidthField));
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_max_height"), hudMaxHeightField));
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_todo_limit"), hudTodoLimitSlider));
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_done_limit"), hudDoneLimitSlider));
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_opacity"), hudOpacitySlider));
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_show_when_empty"), hudShowWhenEmptyButton));
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_visibility"), hudVisibilityButton));
        rows.add(new ConfigRowModel(Component.translatable("gui.todolist.config.hud_project_source"), hudProjectSourceButton));
        return rows;
    }

    /**
     * 计算当前窗口尺寸下的配置页响应式布局。
     *
     * @return 当前窗口下的布局快照
     */
    private ConfigLayout computeResponsiveLayout() {
        int outerPadding = 12;
        int panelGap = 8;
        int titleX = outerPadding;
        int titleY = 14;
        int contentTop = titleY + 20;
        int buttonHeight = 20;
        int buttonGap = 8;
        int buttonY = height - outerPadding - buttonHeight;
        int buttonWidth = Math.max(72, (width - outerPadding * 2 - buttonGap) / 2);
        int columns = width >= 420 ? 2 : 1;

        int previewPanelX;
        int previewPanelY;
        int previewPanelWidth;
        int previewPanelHeight;
        int configListX = outerPadding;
        int configListY = contentTop;
        int configListWidth;
        int configListHeight;

        if (columns == 2) {
            previewPanelWidth = clampInt(width / 3, 150, 200);
            previewPanelX = width - outerPadding - previewPanelWidth;
            previewPanelY = contentTop;
            previewPanelHeight = Math.max(96, buttonY - panelGap - previewPanelY);
            configListWidth = Math.max(140, previewPanelX - panelGap - outerPadding);
            configListHeight = Math.max(72, buttonY - panelGap - configListY);
        } else {
            previewPanelWidth = Math.max(160, width - outerPadding * 2);
            previewPanelHeight = clampInt(height / 3, 72, 96);
            previewPanelX = outerPadding;
            previewPanelY = buttonY - panelGap - previewPanelHeight;
            configListWidth = previewPanelWidth;
            configListHeight = Math.max(44, previewPanelY - panelGap - configListY);
        }

        int workspacePadding = 10;
        int previewWorkspaceX = previewPanelX + workspacePadding;
        int previewWorkspaceY = previewPanelY + 24;
        int previewWorkspaceWidth = Math.max(60, previewPanelWidth - workspacePadding * 2);
        int previewWorkspaceHeight = Math.max(34, previewPanelHeight - 34);
        int saveButtonX = outerPadding;
        int cancelButtonX = saveButtonX + buttonWidth + buttonGap;

        return new ConfigLayout(columns, titleX, titleY, configListX, configListY, configListWidth, configListHeight,
                previewPanelX, previewPanelY, previewPanelWidth, previewPanelHeight,
                previewWorkspaceX, previewWorkspaceY, previewWorkspaceWidth, previewWorkspaceHeight,
                saveButtonX, buttonY, buttonWidth, cancelButtonX, buttonY, buttonWidth, buttonHeight);
    }

    /**
     * 将布局快照同步到组件、滚动区和预览块。
     */
    private void applyLayout() {
        if (layout == null) {
            return;
        }
        configListScrollOffset = clampInt(configListScrollOffset, 0, getConfigListMaxScrollOffset());
        saveButton.setX(layout.saveButtonX());
        saveButton.setY(layout.saveButtonY());
        saveButton.setWidth(layout.saveButtonWidth());
        cancelButton.setX(layout.cancelButtonX());
        cancelButton.setY(layout.cancelButtonY());
        cancelButton.setWidth(layout.cancelButtonWidth());
        layoutConfigRows();
        updatePreviewFromDraft(draft);
    }

    /**
     * 按当前列数和滚动偏移重排配置项组件。
     */
    private void layoutConfigRows() {
        if (layout == null || configRows == null) {
            return;
        }
        int columns = Math.max(1, layout.columns());
        int columnGap = columns == 2 ? 8 : 0;
        int cellWidth = columns == 2
                ? Math.max(80, (layout.configListWidth() - columnGap) / 2)
                : layout.configListWidth();
        int rowHeight = 38;
        int listBottom = layout.configListY() + layout.configListHeight();

        for (int index = 0; index < configRows.size(); index++) {
            ConfigRowModel row = configRows.get(index);
            int rowIndex = index / columns;
            int columnIndex = index % columns;
            int baseX = layout.configListX() + columnIndex * (cellWidth + columnGap);
            int baseY = layout.configListY() + rowIndex * rowHeight - configListScrollOffset;
            int labelY = baseY;
            int slotY = baseY + 14;
            AbstractWidget widget = row.widget();
            boolean visible = slotY + widget.getHeight() >= layout.configListY() && labelY <= listBottom;
            row.updateLayout(baseX, labelY, baseX, slotY, cellWidth, visible);
            widget.setX(baseX);
            widget.setY(slotY);
            widget.setWidth(cellWidth);
            widget.visible = visible;
            widget.active = visible;
        }
    }

    /**
     * 将当前组件状态同步回草稿，避免布局重算丢失编辑值。
     */
    private void syncDraftFromWidgets() {
        if (draft == null) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (hudWidthField != null) {
            draft.hudWidth = parseIntSafe(hudWidthField.getValue(), config.getHudWidth());
        }
        if (hudMaxHeightField != null) {
            draft.hudMaxHeight = parseIntSafe(hudMaxHeightField.getValue(), config.getHudMaxHeight());
        }
        if (hudTodoLimitSlider != null) {
            draft.hudTodoLimit = hudTodoLimitSlider.getIntValue();
        }
        if (hudDoneLimitSlider != null) {
            draft.hudDoneLimit = hudDoneLimitSlider.getIntValue();
        }
        if (hudOpacitySlider != null) {
            draft.hudOpacity = hudOpacitySlider.getDoubleValue();
        }
        draft.hudShowWhenEmpty = hudShowWhenEmptyValue;
        draft.hudVisible = hudVisibleValue;
        draft.hudProjectSource = normalizeHudProjectSource(hudProjectSourceValue);
    }

    /**
     * 根据草稿刷新预览块尺寸和位置。
     *
     * @param currentDraft 当前 HUD 配置草稿
     */
    private void updatePreviewFromDraft(HudConfigDraft currentDraft) {
        if (layout == null || currentDraft == null) {
            return;
        }
        previewHudWidth = clampInt(currentDraft.hudWidth, 80, Math.max(80, layout.previewWorkspaceWidth() - 4));
        previewHudHeight = clampInt(calculatePreviewHeight(currentDraft), 34, Math.max(34, layout.previewWorkspaceHeight() - 4));

        if (currentDraft.previewUseCustom) {
            int resolvedX = resolvePreviewCoordinate(currentDraft.previewHorizontalAnchor,
                    currentDraft.previewHorizontalMargin, layout.previewWorkspaceWidth(), previewHudWidth);
            int resolvedY = resolvePreviewCoordinate(currentDraft.previewVerticalAnchor,
                    currentDraft.previewVerticalMargin, layout.previewWorkspaceHeight(), previewHudHeight);
            previewHudX = layout.previewWorkspaceX() + resolvedX;
            previewHudY = layout.previewWorkspaceY() + resolvedY;
            previewUseCustom = true;
        } else {
            previewHudX = layout.previewWorkspaceX() + Math.max(0, layout.previewWorkspaceWidth() - previewHudWidth - 6);
            previewHudY = layout.previewWorkspaceY() + 6;
            previewUseCustom = false;
        }
        previewRectX = previewHudX;
        previewRectY = previewHudY;
        currentDraft.previewHudX = previewHudX;
        currentDraft.previewHudY = previewHudY;
    }

    /**
     * 将预览块当前绝对位置转换成草稿中的锚点和边距信息。
     */
    private void syncDraftPreviewPositionFromBlock() {
        if (draft == null || layout == null) {
            return;
        }
        int relativeX = previewHudX - layout.previewWorkspaceX();
        int relativeY = previewHudY - layout.previewWorkspaceY();
        int rightMargin = Math.max(0, layout.previewWorkspaceWidth() - previewHudWidth - relativeX);
        int bottomMargin = Math.max(0, layout.previewWorkspaceHeight() - previewHudHeight - relativeY);

        if (relativeX <= rightMargin) {
            draft.previewHorizontalAnchor = ModConfig.HudHorizontalAnchor.LEFT;
            draft.previewHorizontalMargin = Math.max(0, relativeX);
        } else {
            draft.previewHorizontalAnchor = ModConfig.HudHorizontalAnchor.RIGHT;
            draft.previewHorizontalMargin = rightMargin;
        }
        if (relativeY <= bottomMargin) {
            draft.previewVerticalAnchor = ModConfig.HudVerticalAnchor.TOP;
            draft.previewVerticalMargin = Math.max(0, relativeY);
        } else {
            draft.previewVerticalAnchor = ModConfig.HudVerticalAnchor.BOTTOM;
            draft.previewVerticalMargin = bottomMargin;
        }

        previewHorizontalAnchor = draft.previewHorizontalAnchor;
        previewVerticalAnchor = draft.previewVerticalAnchor;
        previewHorizontalMargin = draft.previewHorizontalMargin;
        previewVerticalMargin = draft.previewVerticalMargin;
        draft.previewUseCustom = true;
        previewUseCustom = true;
        draft.previewHudX = previewHudX;
        draft.previewHudY = previewHudY;
        previewRectX = previewHudX;
        previewRectY = previewHudY;
    }

    /**
     * 将草稿统一写回配置对象和客户端桥接层。
     *
     * @param currentDraft 当前 HUD 配置草稿
     */
    private void persistDraft(HudConfigDraft currentDraft) {
        if (currentDraft == null) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        config.setHudWidth(currentDraft.hudWidth);
        config.setHudMaxHeight(currentDraft.hudMaxHeight);
        config.setHudTodoLimit(currentDraft.hudTodoLimit);
        config.setHudDoneLimit(currentDraft.hudDoneLimit);
        config.setHudOpacity(currentDraft.hudOpacity);
        config.setHudShowWhenEmpty(currentDraft.hudShowWhenEmpty);
        config.setHudProjectSource(currentDraft.hudProjectSource);
        if (currentDraft.previewUseCustom) {
            config.updateHudCustomPosition(previewHudX, previewHudY, this.width, this.height, previewHudWidth, previewHudHeight);
        } else {
            config.setHudUseCustomPosition(false);
        }
        ClientBridge.ops().setHudVisible(currentDraft.hudVisible);
    }

    /**
     * 计算当前配置区总内容高度。
     *
     * @return 配置区总内容高度
     */
    private int getConfigListContentHeight() {
        if (layout == null || configRows == null || configRows.isEmpty()) {
            return 0;
        }
        int columns = Math.max(1, layout.columns());
        int rowCount = (configRows.size() + columns - 1) / columns;
        return rowCount * 38;
    }

    /**
     * 计算配置区允许的最大滚动偏移。
     *
     * @return 最大滚动偏移
     */
    private int getConfigListMaxScrollOffset() {
        if (layout == null) {
            return 0;
        }
        return Math.max(0, getConfigListContentHeight() - layout.configListHeight());
    }

    /**
     * 判断配置区内容是否溢出可见高度。
     *
     * @return 若溢出则返回 true
     */
    private boolean isConfigListOverflowing() {
        return getConfigListMaxScrollOffset() > 0;
    }

    /**
     * 绘制配置页壳层，包括标题、配置区、预览区和底部按钮容器。
     *
     * @param context 当前绘制上下文
     */
    private void drawLayoutShell(GuiGraphics context) {
        if (layout == null) {
            return;
        }
        context.drawString(this.font, title, layout.titleX(), layout.titleY(), 0xFFFFFFFF, false);
        context.fill(layout.configListX(), layout.configListY(),
                layout.configListX() + layout.configListWidth(), layout.configListY() + layout.configListHeight(), 0x88101010);
        context.renderOutline(layout.configListX(), layout.configListY(), layout.configListWidth(), layout.configListHeight(), 0xFF5A5A5A);
        context.fill(layout.previewPanelX(), layout.previewPanelY(),
                layout.previewPanelX() + layout.previewPanelWidth(), layout.previewPanelY() + layout.previewPanelHeight(), 0x88202020);
        context.renderOutline(layout.previewPanelX(), layout.previewPanelY(), layout.previewPanelWidth(), layout.previewPanelHeight(), 0xFF7A7A7A);
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_preview.title"),
                layout.previewPanelX() + 10, layout.previewPanelY() + 8, 0xFFFFFFFF, false);
    }

    /**
     * 绘制当前可见配置项的标签文本。
     *
     * @param context 当前绘制上下文
     */
    private void drawConfigRowLabels(GuiGraphics context) {
        if (configRows == null) {
            return;
        }
        for (ConfigRowModel row : configRows) {
            if (!row.visible()) {
                continue;
            }
            context.drawString(this.font, row.label(), row.labelX(), row.labelY(), 0xFFDDDDDD, false);
        }
    }

    /**
     * 绘制预览面板中的 HUD 预览块和提示文本。
     *
     * @param context 当前绘制上下文
     */
    private void drawPreviewPanel(GuiGraphics context) {
        if (layout == null) {
            return;
        }
        context.fill(layout.previewWorkspaceX(), layout.previewWorkspaceY(),
                layout.previewWorkspaceX() + layout.previewWorkspaceWidth(),
                layout.previewWorkspaceY() + layout.previewWorkspaceHeight(), 0x660A0A0A);
        context.renderOutline(layout.previewWorkspaceX(), layout.previewWorkspaceY(),
                layout.previewWorkspaceWidth(), layout.previewWorkspaceHeight(), 0xFF4A4A4A);

        int alpha = (int) Math.round(clampDouble(draft == null ? 0.85D : draft.hudOpacity, 0.0D, 1.0D) * 255.0D);
        int fillColor = (alpha << 24) | 0x003A3A3A;
        context.fill(previewHudX, previewHudY, previewHudX + previewHudWidth, previewHudY + previewHudHeight, fillColor);
        context.renderOutline(previewHudX, previewHudY, previewHudWidth, previewHudHeight, 0xFFFFFFFF);

        Component previewTitle = Component.translatable("gui.todolist.config.hud_preview.title");
        Component previewHint = Component.translatable("gui.todolist.config.hud_preview.hint");
        int textY = previewHudY + Math.max(4, (previewHudHeight - (font.lineHeight * 2 + 2)) / 2);
        context.drawCenteredString(this.font, previewTitle, previewHudX + previewHudWidth / 2, textY, 0xFFFFFFFF);
        context.drawCenteredString(this.font, previewHint, previewHudX + previewHudWidth / 2, textY + font.lineHeight + 2, 0xFFE6E6E6);
    }

    /**
     * 根据当前草稿估算预览块高度，保证预览在小窗口下仍可完整显示。
     *
     * @param currentDraft 当前 HUD 配置草稿
     * @return 预览块高度
     */
    private int calculatePreviewHeight(HudConfigDraft currentDraft) {
        int lineCount = Math.max(2, Math.min(4, currentDraft.hudTodoLimit + Math.min(1, currentDraft.hudDoneLimit)));
        return 26 + lineCount * 6;
    }

    /**
     * 根据锚点和边距计算预览块在工作区内的相对坐标。
     *
     * @param anchor 当前使用的锚点
     * @param margin 当前使用的边距
     * @param workspaceSize 工作区尺寸
     * @param blockSize 预览块尺寸
     * @return 预览块相对工作区的坐标
     */
    private int resolvePreviewCoordinate(Object anchor, int margin, int workspaceSize, int blockSize) {
        boolean useTrailingAnchor = anchor == ModConfig.HudHorizontalAnchor.RIGHT || anchor == ModConfig.HudVerticalAnchor.BOTTOM;
        int resolved = useTrailingAnchor ? workspaceSize - blockSize - margin : margin;
        return clampInt(resolved, 0, Math.max(0, workspaceSize - blockSize));
    }

    /**
     * 解析整型输入，失败时回退到安全值。
     *
     * @param value 原始输入文本
     * @param fallback 回退值
     * @return 解析后的整型值
     */
    private int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * 刷新“空列表时仍显示”按钮文案。
     */
    private void updateHudShowWhenEmptyButtonLabel() {
        if (hudShowWhenEmptyButton == null) {
            return;
        }
        String key = hudShowWhenEmptyValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
        hudShowWhenEmptyButton.setMessage(Component.translatable(key));
    }

    /**
     * 刷新 HUD 显示按钮文案。
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
        String value = normalizeHudProjectSource(hudProjectSourceValue);
        if ("CURRENT".equalsIgnoreCase(value)) {
            hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.current_selected_fallback"));
            return;
        }
        if ("STARRED".equalsIgnoreCase(value)) {
            hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.starred"));
            return;
        }
        hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.all"));
    }

    /**
     * 规范化 HUD 项目来源值，避免空值导致按钮状态异常。
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
     * 解析 HUD 项目来源在选项数组中的索引。
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
     * 将整型值限制在给定范围内。
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
     * 将浮点值限制在给定范围内。
     *
     * @param value 原始值
     * @param min 最小值
     * @param max 最大值
     * @return 裁剪后的值
     */
    private static double clampDouble(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * HUD 项目来源选项集合。
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
     * 整型步进滑块：用于待办数和已办数配置。
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
         * 按整型值设置滑块内部比例值。
         *
         * @param value 目标整型值
         */
        private void setValueFromInt(int value) {
            int clamped = Math.max(min, Math.min(max, value));
            this.value = max == min ? 0.0D : (double) (clamped - min) / (double) (max - min);
            updateMessage();
        }

        /**
         * 返回当前滑块代表的整型值。
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
         * 根据当前整型值刷新滑块文本。
         */
        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(Integer.toString(getIntValue())));
        }

        /**
         * 当前滑块不需要额外的应用逻辑。
         */
        @Override
        protected void applyValue() {
        }
    }

    /**
     * 双精度步进滑块：用于透明度配置。
     */
    private static class DoubleStepSliderWidget extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;

        /**
         * 创建双精度步进滑块。
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
         * 按双精度值设置滑块内部比例值。
         *
         * @param value 目标双精度值
         */
        private void setValueFromDouble(double value) {
            double clamped = clampDouble(value, min, max);
            double stepped = Math.round(clamped / step) * step;
            this.value = max == min ? 0.0D : clampDouble((stepped - min) / (max - min), 0.0D, 1.0D);
            updateMessage();
        }

        /**
         * 返回当前滑块代表的双精度值。
         *
         * @return 当前双精度值
         */
        double getDoubleValue() {
            double raw = min + clampDouble(this.value, 0.0D, 1.0D) * (max - min);
            double stepped = Math.round(raw / step) * step;
            return clampDouble(stepped, min, max);
        }

        /**
         * 根据当前双精度值刷新滑块文本。
         */
        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(String.format(java.util.Locale.ROOT, "%.1f", getDoubleValue())));
        }

        /**
         * 当前滑块不需要额外的应用逻辑。
         */
        @Override
        protected void applyValue() {
        }
    }
}
