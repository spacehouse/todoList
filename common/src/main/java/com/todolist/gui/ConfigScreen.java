package com.todolist.gui;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 配置界面：提供 GUI/HUD 外观与行为的本地配置编辑与预览。
 */
public class ConfigScreen extends Screen {
    private final Screen parent;

    private EditBox guiWidthField;
    private EditBox guiHeightField;
    private EditBox hudWidthField;
    private EditBox hudMaxHeightField;
    private EditBox taskItemHeightField;
    private EditBox backgroundColorField;
    private EditBox sidebarWidthField;
    private EditBox sidebarHeightField;
    private IntSliderWidget hudTodoLimitSlider;
    private IntSliderWidget hudDoneLimitSlider;
    private DoubleStepSliderWidget hudOpacitySlider;
    private Button hudExpandedButton;
    private Button hudShowWhenEmptyButton;
    private Button soundEffectsButton;
    private Button hudDefaultViewButton;
    private Button hudProjectSourceButton;

    private int previewHudX;
    private int previewHudY;
    private int previewHudWidth;
    private int previewHudHeight;
    private int previewRectX;
    private int previewRectY;
    private boolean draggingHud;
    private boolean previewUseCustom;
    private int dragOffsetX;
    private int dragOffsetY;

    private boolean hudExpandedValue;
    private boolean hudShowWhenEmptyValue;
    private boolean soundEffectsValue;
    private String hudDefaultViewValue;
    private int hudDefaultViewIndex;
    private String[] hudDefaultViewOptions = HudViewOptions.VALUES;
    private boolean lockHudDefaultViewOption;
    private String hudProjectSourceValue;
    private int hudProjectSourceIndex;

    /**
     * 创建配置界面。
     */
    public ConfigScreen(Screen parent) {
        super(Component.translatable("gui.todolist.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig cfg = ModConfig.getInstance();
        int guiWidth = 320;
        int x = (this.width - guiWidth) / 2;
        int y = this.height / 6 + 20;
        int row = 0;
        int rowH = 24;
        int fieldH = 20;
        int labelWidth = 160;
        int fieldWidth = guiWidth - labelWidth;

        int twoColGap = 20;
        int colWidth = (guiWidth - twoColGap) / 2;
        int leftLabelWidth = 80;
        int rightLabelWidth = 80;
        int leftFieldWidth = colWidth - leftLabelWidth;
        int rightFieldWidth = colWidth - rightLabelWidth;
        int leftLabelX = x;
        int leftFieldX = x + leftLabelWidth;
        int rightLabelX = x + colWidth + twoColGap;
        int rightFieldX = rightLabelX + rightLabelWidth;

        guiWidthField = new EditBox(this.font, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Component.empty());
        guiWidthField.setValue(Integer.toString(cfg.getGuiWidth()));
        this.addRenderableWidget(guiWidthField);

        guiHeightField = new EditBox(this.font, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Component.empty());
        guiHeightField.setValue(Integer.toString(cfg.getGuiHeight()));
        this.addRenderableWidget(guiHeightField);
        row++;

        hudWidthField = new EditBox(this.font, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Component.empty());
        hudWidthField.setValue(Integer.toString(cfg.getHudWidth()));
        this.addRenderableWidget(hudWidthField);

        hudMaxHeightField = new EditBox(this.font, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Component.empty());
        hudMaxHeightField.setValue(Integer.toString(cfg.getHudMaxHeight()));
        this.addRenderableWidget(hudMaxHeightField);
        row++;

        taskItemHeightField = new EditBox(this.font, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Component.empty());
        taskItemHeightField.setValue(Integer.toString(cfg.getTaskItemHeight()));
        this.addRenderableWidget(taskItemHeightField);

        backgroundColorField = new EditBox(this.font, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Component.empty());
        backgroundColorField.setValue(String.format("%08X", cfg.getBackgroundColor()));
        this.addRenderableWidget(backgroundColorField);
        row++;

        sidebarWidthField = new EditBox(this.font, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Component.empty());
        sidebarWidthField.setValue(Integer.toString(cfg.getProjectSidebarWidth()));
        this.addRenderableWidget(sidebarWidthField);
        
        sidebarHeightField = new EditBox(this.font, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Component.empty());
        sidebarHeightField.setValue(Integer.toString(cfg.getProjectSidebarHeight()));
        this.addRenderableWidget(sidebarHeightField);
        row++;

        int todoInitial = cfg.getHudTodoLimit();
        int doneInitial = cfg.getHudDoneLimit();
        hudTodoLimitSlider = new IntSliderWidget(leftFieldX, y + row * rowH, leftFieldWidth, fieldH, 0, 30, todoInitial);
        hudDoneLimitSlider = new IntSliderWidget(rightFieldX, y + row * rowH, rightFieldWidth, fieldH, 0, 30, doneInitial);
        this.addRenderableWidget(hudTodoLimitSlider);
        this.addRenderableWidget(hudDoneLimitSlider);
        row++;

        int opacityLabelWidth = this.font.width(Component.translatable("gui.todolist.config.hud_opacity"));
        int opacitySliderX = x + opacityLabelWidth + 10;
        int opacitySliderW = guiWidth - (opacitySliderX - x);
        hudOpacitySlider = new DoubleStepSliderWidget(opacitySliderX, y + row * rowH, opacitySliderW, fieldH, 0.0, 1.0, 0.1, cfg.getHudOpacity());
        this.addRenderableWidget(hudOpacitySlider);
        row++;

        hudExpandedValue = cfg.isHudDefaultExpanded();
        hudShowWhenEmptyValue = cfg.isHudShowWhenEmpty();
        soundEffectsValue = cfg.isEnableSoundEffects();

        hudExpandedButton = Button.builder(Component.empty(), b -> {
            hudExpandedValue = !hudExpandedValue;
            updateHudExpandedButtonLabel();
        }).bounds(leftFieldX, y + row * rowH, leftFieldWidth, fieldH).build();
        this.addRenderableWidget(hudExpandedButton);

        hudShowWhenEmptyButton = Button.builder(Component.empty(), b -> {
            hudShowWhenEmptyValue = !hudShowWhenEmptyValue;
            updateHudShowWhenEmptyButtonLabel();
        }).bounds(rightFieldX, y + row * rowH, rightFieldWidth, fieldH).build();
        this.addRenderableWidget(hudShowWhenEmptyButton);
        row++;

        soundEffectsButton = Button.builder(Component.empty(), b -> {
            soundEffectsValue = !soundEffectsValue;
            updateSoundEffectsButtonLabel();
        }).bounds(leftFieldX, y + row * rowH, leftFieldWidth, fieldH).build();
        this.addRenderableWidget(soundEffectsButton);
        row++;

        String currentView = cfg.getHudDefaultView();
        Project.Scope activeScope = resolveActiveProjectScope();
        boolean singlePlayer = this.minecraft != null && this.minecraft.isLocalServer();
        if (singlePlayer || activeScope == Project.Scope.PERSONAL) {
            hudDefaultViewOptions = new String[] { "PERSONAL" };
            lockHudDefaultViewOption = true;
        } else if (activeScope == Project.Scope.TEAM) {
            hudDefaultViewOptions = new String[] { "TEAM_UNASSIGNED", "TEAM_ALL", "TEAM_ASSIGNED" };
            lockHudDefaultViewOption = false;
        } else {
            hudDefaultViewOptions = HudViewOptions.VALUES;
            lockHudDefaultViewOption = false;
        }
        hudDefaultViewIndex = 0;
        for (int i = 0; i < hudDefaultViewOptions.length; i++) {
            if (hudDefaultViewOptions[i].equalsIgnoreCase(currentView)) {
                hudDefaultViewIndex = i;
                break;
            }
        }
        if (hudDefaultViewOptions.length == 0) {
            hudDefaultViewOptions = new String[] { "PERSONAL" };
            hudDefaultViewIndex = 0;
        }
        hudDefaultViewValue = hudDefaultViewOptions[Math.max(0, Math.min(hudDefaultViewIndex, hudDefaultViewOptions.length - 1))];

        int defaultViewLabelWidth = this.font.width(Component.translatable("gui.todolist.config.hud_default_view"));
        int defaultViewButtonX = x + defaultViewLabelWidth + 10;
        int defaultViewButtonWidth = guiWidth - (defaultViewButtonX - x);

        hudDefaultViewButton = Button.builder(Component.empty(), b -> {
            if (lockHudDefaultViewOption || hudDefaultViewOptions.length == 0) {
                return;
            }
            hudDefaultViewIndex = (hudDefaultViewIndex + 1) % hudDefaultViewOptions.length;
            hudDefaultViewValue = hudDefaultViewOptions[hudDefaultViewIndex];
            updateHudDefaultViewButtonLabel();
        }).bounds(defaultViewButtonX, y + row * rowH, defaultViewButtonWidth, fieldH).build();
        this.addRenderableWidget(hudDefaultViewButton);
        if (lockHudDefaultViewOption) {
            hudDefaultViewButton.active = false;
        }

        updateHudExpandedButtonLabel();
        updateHudShowWhenEmptyButtonLabel();
        updateSoundEffectsButtonLabel();
        updateHudDefaultViewButtonLabel();

        row++;
        String currentProjectSource = cfg.getHudProjectSource();
        String[] sources = HudProjectSourceOptions.VALUES;
        hudProjectSourceIndex = 0;
        for (int i = 0; i < sources.length; i++) {
            if (sources[i].equalsIgnoreCase(currentProjectSource)) {
                hudProjectSourceIndex = i;
                break;
            }
        }
        hudProjectSourceValue = sources[hudProjectSourceIndex];

        int sourceLabelWidth = this.font.width(Component.translatable("gui.todolist.config.hud_project_source"));
        int sourceButtonX = x + sourceLabelWidth + 10;
        int sourceButtonWidth = guiWidth - (sourceButtonX - x);

        hudProjectSourceButton = Button.builder(Component.empty(), b -> {
            hudProjectSourceIndex = (hudProjectSourceIndex + 1) % HudProjectSourceOptions.VALUES.length;
            hudProjectSourceValue = HudProjectSourceOptions.VALUES[hudProjectSourceIndex];
            updateHudProjectSourceButtonLabel();
        }).bounds(sourceButtonX, y + row * rowH, sourceButtonWidth, fieldH).build();
        this.addRenderableWidget(hudProjectSourceButton);
        updateHudProjectSourceButtonLabel();
        row++;

        previewUseCustom = cfg.isHudUseCustomPosition();
        if (previewUseCustom) {
            previewHudX = cfg.getHudCustomX();
            previewHudY = cfg.getHudCustomY();
        } else {
            previewHudWidth = cfg.getHudWidth();
            previewHudHeight = 40;
            int margin = 10;
            previewHudX = this.width - previewHudWidth - margin;
            if (previewHudX < 0) previewHudX = 0;
            previewHudY = margin;
        }
        previewHudWidth = cfg.getHudWidth();
        previewHudHeight = 40;

        int buttonY = y + row * rowH + 30;
        Button save = Button.builder(Component.translatable("gui.todolist.config.save_apply"), b -> {
            applyAndReturn();
        }).bounds(x, buttonY, guiWidth / 2 - 5, 20).build();
        Button cancel = Button.builder(Component.translatable("gui.todolist.cancel"), b -> {
            this.minecraft.setScreen(parent);
        }).bounds(x + guiWidth / 2 + 5, buttonY, guiWidth / 2 - 5, 20).build();
        this.addRenderableWidget(save);
        this.addRenderableWidget(cancel);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        int guiWidth = 320;
        int x = (this.width - guiWidth) / 2;
        int yStart = this.height / 6 + 20;
        int rowH = 24;
        int fieldH = 20;
        int textH = this.font.lineHeight;

        int twoColGap = 20;
        int colWidth = (guiWidth - twoColGap) / 2;
        int leftLabelX = x;
        int rightLabelX = x + colWidth + twoColGap;

        int row = 0;
        int baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.gui_width"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawString(this.font, Component.translatable("gui.todolist.config.gui_height"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_width"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_max_height"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.task_item_height"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawString(this.font, Component.translatable("gui.todolist.config.background_color"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.sidebar_width"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawString(this.font, Component.translatable("gui.todolist.config.sidebar_height"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_todo_limit"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_done_limit"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_opacity"), leftLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_default_expanded"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_show_when_empty"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("config.todolist.enable_sound_effects"), leftLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_default_view"), leftLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawString(this.font, Component.translatable("gui.todolist.config.hud_project_source"), leftLabelX, baseY, 0xFFFFFF, false);

        int hudX = previewHudX;
        int hudY = previewHudY;
        if (hudX < 0) hudX = 0;
        if (hudY < 0) hudY = 0;
        if (hudX + previewHudWidth > this.width) hudX = this.width - previewHudWidth;
        if (hudY + previewHudHeight > this.height) hudY = this.height - previewHudHeight;
        previewRectX = hudX;
        previewRectY = hudY;
        double opacity = hudOpacitySlider == null ? ModConfig.getInstance().getHudOpacity() : hudOpacitySlider.getDoubleValue();
        int a = (int) Math.round(Math.max(0.0, Math.min(1.0, opacity)) * 255.0);
        context.fill(hudX, hudY, hudX + previewHudWidth, hudY + previewHudHeight, (a << 24));
        context.renderOutline(hudX, hudY, previewHudWidth, previewHudHeight, 0xFFFFFFFF);
        Component line1 = Component.translatable("gui.todolist.config.hud_preview.title");
        Component line2 = Component.translatable("gui.todolist.config.hud_preview.hint");
        int line1Width = this.font.width(line1);
        int line2Width = this.font.width(line2);
        int centerX = hudX + previewHudWidth / 2;
        int centerY = hudY + previewHudHeight / 2;
        int lineSpacing = 2;
        int totalTextHeight = textH * 2 + lineSpacing;
        int startY = centerY - totalTextHeight / 2;
        int line1X = centerX - line1Width / 2;
        int line2X = centerX - line2Width / 2;
        context.drawString(this.font, line1, line1X, startY, 0xFFFFFF, false);
        context.drawString(this.font, line2, line2X, startY + textH + lineSpacing, 0xFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            if (mx >= previewRectX && mx <= previewRectX + previewHudWidth
                    && my >= previewRectY && my <= previewRectY + previewHudHeight) {
                draggingHud = true;
                dragOffsetX = mx - previewRectX;
                dragOffsetY = my - previewRectY;
                previewUseCustom = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingHud) {
            draggingHud = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingHud) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            int newX = mx - dragOffsetX;
            int newY = my - dragOffsetY;
            if (newX < 0) newX = 0;
            if (newY < 0) newY = 0;
            if (newX + previewHudWidth > this.width) newX = this.width - previewHudWidth;
            if (newY + previewHudHeight > this.height) newY = this.height - previewHudHeight;
            previewHudX = newX;
            previewHudY = newY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void applyAndReturn() {
        ModConfig cfg = ModConfig.getInstance();
        cfg.setGuiWidth(parseIntSafe(guiWidthField.getValue(), cfg.getGuiWidth()));
        cfg.setGuiHeight(parseIntSafe(guiHeightField.getValue(), cfg.getGuiHeight()));
        cfg.setHudWidth(parseIntSafe(hudWidthField.getValue(), cfg.getHudWidth()));
        cfg.setHudMaxHeight(parseIntSafe(hudMaxHeightField.getValue(), cfg.getHudMaxHeight()));
        cfg.setTaskItemHeight(parseIntSafe(taskItemHeightField.getValue(), cfg.getTaskItemHeight()));
        cfg.setBackgroundColor(parseColorSafe(backgroundColorField.getValue(), cfg.getBackgroundColor()));
        cfg.setProjectSidebarWidth(parseIntSafe(sidebarWidthField.getValue(), cfg.getProjectSidebarWidth()));
        cfg.setProjectSidebarHeight(parseIntSafe(sidebarHeightField.getValue(), cfg.getProjectSidebarHeight()));
        cfg.setHudTodoLimit(hudTodoLimitSlider.getIntValue());
        cfg.setHudDoneLimit(hudDoneLimitSlider.getIntValue());
        if (hudOpacitySlider != null) {
            cfg.setHudOpacity(hudOpacitySlider.getDoubleValue());
        }
        cfg.setHudDefaultExpanded(hudExpandedValue);
        cfg.setHudUseCustomPosition(previewUseCustom);
        cfg.setHudCustomX(previewHudX);
        cfg.setHudCustomY(previewHudY);
        cfg.setHudShowWhenEmpty(hudShowWhenEmptyValue);
        cfg.setEnableSoundEffects(soundEffectsValue);
        // cfg.setSortByPriority(sortByPriorityValue); // Removed from UI
        if (this.minecraft != null && this.minecraft.isLocalServer()) {
            cfg.setHudDefaultView("PERSONAL");
        } else {
            cfg.setHudDefaultView(hudDefaultViewValue);
        }
        cfg.setHudProjectSource(hudProjectSourceValue);
        this.minecraft.setScreen(parent);
    }

    private int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int parseColorSafe(String s, int fallback) {
        try {
            String hex = s.trim();
            if (hex.startsWith("0x")) hex = hex.substring(2);
            if (hex.startsWith("#")) hex = hex.substring(1);
            // Handle signed int parsing for 32-bit hex (ARGB)
            return (int) Long.parseLong(hex, 16);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void updateHudExpandedButtonLabel() {
        if (hudExpandedButton != null) {
            String key = hudExpandedValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
            hudExpandedButton.setMessage(Component.translatable(key));
        }
    }

    private void updateHudShowWhenEmptyButtonLabel() {
        if (hudShowWhenEmptyButton != null) {
            String key = hudShowWhenEmptyValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
            hudShowWhenEmptyButton.setMessage(Component.translatable(key));
        }
    }

    private void updateSoundEffectsButtonLabel() {
        if (soundEffectsButton != null) {
            String key = soundEffectsValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
            soundEffectsButton.setMessage(Component.translatable(key));
        }
    }

    /*
    private void updateSortByPriorityButtonLabel() {
        if (sortByPriorityButton != null) {
            String key = sortByPriorityValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
            sortByPriorityButton.setMessage(Text.translatable(key));
        }
    }
    */

    private void updateHudDefaultViewButtonLabel() {
        if (hudDefaultViewButton != null) {
            String view = hudDefaultViewValue == null ? "" : hudDefaultViewValue;
            String key;
            if ("TEAM_UNASSIGNED".equalsIgnoreCase(view)) {
                key = "gui.todolist.view.team_unassigned";
            } else if ("TEAM_ALL".equalsIgnoreCase(view)) {
                key = "gui.todolist.view.team_all";
            } else if ("TEAM_ASSIGNED".equalsIgnoreCase(view)) {
                key = "gui.todolist.view.team_assigned";
            } else {
                key = "gui.todolist.view.personal";
            }
            hudDefaultViewButton.setMessage(Component.translatable(key));
        }
    }

    private void updateHudProjectSourceButtonLabel() {
        if (hudProjectSourceButton != null) {
            String value = hudProjectSourceValue == null ? "" : hudProjectSourceValue;
            if ("CURRENT".equalsIgnoreCase(value)) {
                Project.Scope scope = resolveHudScope();
                Project project = getActiveProject(scope);
                Component projectName = getProjectDisplayName(project);
                if (projectName != null && !projectName.getString().trim().isEmpty()) {
                    hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.current_selected", projectName.getString()));
                } else {
                    hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.current_selected_fallback"));
                }
                return;
            } else if ("STARRED".equalsIgnoreCase(value)) {
                hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.starred"));
                return;
            } else {
                hudProjectSourceButton.setMessage(Component.translatable("gui.todolist.hud.project_source.all"));
                return;
            }
        }
    }

    private Project.Scope resolveHudScope() {
        String view = hudDefaultViewValue == null ? "" : hudDefaultViewValue;
        if ("TEAM_UNASSIGNED".equalsIgnoreCase(view) || "TEAM_ALL".equalsIgnoreCase(view) || "TEAM_ASSIGNED".equalsIgnoreCase(view)) {
            return Project.Scope.TEAM;
        }
        return Project.Scope.PERSONAL;
    }

    private static Project getActiveProject(Project.Scope scope) {
        return ClientBridge.getActiveProject(TodoListCommon.getProjectManager(), scope);
    }

    private static Component getProjectDisplayName(Project project) {
        return ProjectNameFormatter.toDisplayText(project);
    }

    private Project.Scope resolveActiveProjectScope() {
        Project activeProject = ClientBridge.getActiveProject(TodoListCommon.getProjectManager());
        if (activeProject == null) {
            return null;
        }
        return activeProject.getScope();
    }

    private static class HudViewOptions {
        static final String[] VALUES = new String[] { "PERSONAL", "TEAM_UNASSIGNED", "TEAM_ALL", "TEAM_ASSIGNED" };
    }

    private static class HudProjectSourceOptions {
        static final String[] VALUES = new String[] { "CURRENT", "STARRED", "ALL" };
    }

    private static class IntSliderWidget extends AbstractSliderButton {
        private final int min;
        private final int max;

        IntSliderWidget(int x, int y, int width, int height, int min, int max, int value) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.min = min;
            this.max = max;
            setValueFromInt(value);
        }

        private void setValueFromInt(int value) {
            int clamped = Math.max(min, Math.min(max, value));
            if (max == min) {
                this.value = 0.0;
            } else {
                this.value = (double)(clamped - min) / (double)(max - min);
            }
            updateMessage();
        }

        int getIntValue() {
            if (max == min) {
                return min;
            }
            int range = max - min;
            int v = (int)Math.round(this.value * range) + min;
            if (v < min) v = min;
            if (v > max) v = max;
            return v;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.nullToEmpty(Integer.toString(getIntValue())));
        }

        @Override
        protected void applyValue() {
        }
    }

    private static class DoubleStepSliderWidget extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;

        DoubleStepSliderWidget(int x, int y, int width, int height, double min, double max, double step, double value) {
            super(x, y, width, height, Component.empty(), 0.0);
            this.min = min;
            this.max = max;
            this.step = step;
            setValueFromDouble(value);
        }

        private void setValueFromDouble(double v) {
            double clamped = Math.max(min, Math.min(max, v));
            double stepped = Math.round(clamped / step) * step;
            double ratio;
            if (max == min) {
                ratio = 0.0;
            } else {
                ratio = (stepped - min) / (max - min);
            }
            if (ratio < 0.0) ratio = 0.0;
            if (ratio > 1.0) ratio = 1.0;
            this.value = ratio;
            updateMessage();
        }

        double getDoubleValue() {
            double clampedRatio = Math.max(0.0, Math.min(1.0, this.value));
            double raw = min + clampedRatio * (max - min);
            double stepped = Math.round(raw / step) * step;
            if (stepped < min) stepped = min;
            if (stepped > max) stepped = max;
            return stepped;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.nullToEmpty(String.format(java.util.Locale.ROOT, "%.1f", getDoubleValue())));
        }

        @Override
        protected void applyValue() {
        }
    }
}


