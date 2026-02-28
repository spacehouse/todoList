package com.todolist.gui;

import com.todolist.TodoListMod;
import com.todolist.client.TodoClient;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget guiWidthField;
    private TextFieldWidget guiHeightField;
    private TextFieldWidget hudWidthField;
    private TextFieldWidget hudMaxHeightField;
    private TextFieldWidget taskItemHeightField;
    private TextFieldWidget backgroundColorField;
    private TextFieldWidget sidebarWidthField;
    private TextFieldWidget sidebarHeightField;
    private IntSliderWidget hudTodoLimitSlider;
    private IntSliderWidget hudDoneLimitSlider;
    private DoubleStepSliderWidget hudOpacitySlider;
    private ButtonWidget hudExpandedButton;
    private ButtonWidget hudShowWhenEmptyButton;
    private ButtonWidget soundEffectsButton;
    private ButtonWidget hudDefaultViewButton;
    private ButtonWidget hudProjectSourceButton;

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
    private String hudProjectSourceValue;
    private int hudProjectSourceIndex;

    public ConfigScreen(Screen parent) {
        super(Text.translatable("gui.todolist.config.title"));
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

        guiWidthField = new TextFieldWidget(this.textRenderer, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Text.empty());
        guiWidthField.setText(Integer.toString(cfg.getGuiWidth()));
        this.addDrawableChild(guiWidthField);

        guiHeightField = new TextFieldWidget(this.textRenderer, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Text.empty());
        guiHeightField.setText(Integer.toString(cfg.getGuiHeight()));
        this.addDrawableChild(guiHeightField);
        row++;

        hudWidthField = new TextFieldWidget(this.textRenderer, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Text.empty());
        hudWidthField.setText(Integer.toString(cfg.getHudWidth()));
        this.addDrawableChild(hudWidthField);

        hudMaxHeightField = new TextFieldWidget(this.textRenderer, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Text.empty());
        hudMaxHeightField.setText(Integer.toString(cfg.getHudMaxHeight()));
        this.addDrawableChild(hudMaxHeightField);
        row++;

        taskItemHeightField = new TextFieldWidget(this.textRenderer, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Text.empty());
        taskItemHeightField.setText(Integer.toString(cfg.getTaskItemHeight()));
        this.addDrawableChild(taskItemHeightField);

        backgroundColorField = new TextFieldWidget(this.textRenderer, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Text.empty());
        backgroundColorField.setText(String.format("%08X", cfg.getBackgroundColor()));
        this.addDrawableChild(backgroundColorField);
        row++;

        sidebarWidthField = new TextFieldWidget(this.textRenderer, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Text.empty());
        sidebarWidthField.setText(Integer.toString(cfg.getProjectSidebarWidth()));
        this.addDrawableChild(sidebarWidthField);
        
        sidebarHeightField = new TextFieldWidget(this.textRenderer, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Text.empty());
        sidebarHeightField.setText(Integer.toString(cfg.getProjectSidebarHeight()));
        this.addDrawableChild(sidebarHeightField);
        row++;

        int todoInitial = cfg.getHudTodoLimit();
        int doneInitial = cfg.getHudDoneLimit();
        hudTodoLimitSlider = new IntSliderWidget(leftFieldX, y + row * rowH, leftFieldWidth, fieldH, 0, 30, todoInitial);
        hudDoneLimitSlider = new IntSliderWidget(rightFieldX, y + row * rowH, rightFieldWidth, fieldH, 0, 30, doneInitial);
        this.addDrawableChild(hudTodoLimitSlider);
        this.addDrawableChild(hudDoneLimitSlider);
        row++;

        int opacityLabelWidth = this.textRenderer.getWidth(Text.translatable("gui.todolist.config.hud_opacity"));
        int opacitySliderX = x + opacityLabelWidth + 10;
        int opacitySliderW = guiWidth - (opacitySliderX - x);
        hudOpacitySlider = new DoubleStepSliderWidget(opacitySliderX, y + row * rowH, opacitySliderW, fieldH, 0.0, 1.0, 0.1, cfg.getHudOpacity());
        this.addDrawableChild(hudOpacitySlider);
        row++;

        hudExpandedValue = cfg.isHudDefaultExpanded();
        hudShowWhenEmptyValue = cfg.isHudShowWhenEmpty();
        soundEffectsValue = cfg.isEnableSoundEffects();

        hudExpandedButton = ButtonWidget.builder(Text.empty(), b -> {
            hudExpandedValue = !hudExpandedValue;
            updateHudExpandedButtonLabel();
        }).dimensions(leftFieldX, y + row * rowH, leftFieldWidth, fieldH).build();
        this.addDrawableChild(hudExpandedButton);

        hudShowWhenEmptyButton = ButtonWidget.builder(Text.empty(), b -> {
            hudShowWhenEmptyValue = !hudShowWhenEmptyValue;
            updateHudShowWhenEmptyButtonLabel();
        }).dimensions(rightFieldX, y + row * rowH, rightFieldWidth, fieldH).build();
        this.addDrawableChild(hudShowWhenEmptyButton);
        row++;

        soundEffectsButton = ButtonWidget.builder(Text.empty(), b -> {
            soundEffectsValue = !soundEffectsValue;
            updateSoundEffectsButtonLabel();
        }).dimensions(leftFieldX, y + row * rowH, leftFieldWidth, fieldH).build();
        this.addDrawableChild(soundEffectsButton);
        row++;

        String currentView = cfg.getHudDefaultView();
        String[] views = HudViewOptions.VALUES;
        hudDefaultViewIndex = 0;
        for (int i = 0; i < views.length; i++) {
            if (views[i].equalsIgnoreCase(currentView)) {
                hudDefaultViewIndex = i;
                break;
            }
        }
        hudDefaultViewValue = views[hudDefaultViewIndex];

        boolean singlePlayer = this.client != null && this.client.isInSingleplayer();
        if (singlePlayer) {
            hudDefaultViewIndex = 0;
            hudDefaultViewValue = HudViewOptions.VALUES[0];
        }

        int defaultViewLabelWidth = this.textRenderer.getWidth(Text.translatable("gui.todolist.config.hud_default_view"));
        int defaultViewButtonX = x + defaultViewLabelWidth + 10;
        int defaultViewButtonWidth = guiWidth - (defaultViewButtonX - x);

        hudDefaultViewButton = ButtonWidget.builder(Text.empty(), b -> {
            if (this.client != null && this.client.isInSingleplayer()) {
                return;
            }
            hudDefaultViewIndex = (hudDefaultViewIndex + 1) % HudViewOptions.VALUES.length;
            hudDefaultViewValue = HudViewOptions.VALUES[hudDefaultViewIndex];
            updateHudDefaultViewButtonLabel();
        }).dimensions(defaultViewButtonX, y + row * rowH, defaultViewButtonWidth, fieldH).build();
        this.addDrawableChild(hudDefaultViewButton);
        if (singlePlayer) {
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

        int sourceLabelWidth = this.textRenderer.getWidth(Text.translatable("gui.todolist.config.hud_project_source"));
        int sourceButtonX = x + sourceLabelWidth + 10;
        int sourceButtonWidth = guiWidth - (sourceButtonX - x);

        hudProjectSourceButton = ButtonWidget.builder(Text.empty(), b -> {
            hudProjectSourceIndex = (hudProjectSourceIndex + 1) % HudProjectSourceOptions.VALUES.length;
            hudProjectSourceValue = HudProjectSourceOptions.VALUES[hudProjectSourceIndex];
            updateHudProjectSourceButtonLabel();
        }).dimensions(sourceButtonX, y + row * rowH, sourceButtonWidth, fieldH).build();
        this.addDrawableChild(hudProjectSourceButton);
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
        ButtonWidget save = ButtonWidget.builder(Text.translatable("gui.todolist.config.save_apply"), b -> {
            applyAndReturn();
        }).dimensions(x, buttonY, guiWidth / 2 - 5, 20).build();
        ButtonWidget cancel = ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), b -> {
            this.client.setScreen(parent);
        }).dimensions(x + guiWidth / 2 + 5, buttonY, guiWidth / 2 - 5, 20).build();
        this.addDrawableChild(save);
        this.addDrawableChild(cancel);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        int guiWidth = 320;
        int x = (this.width - guiWidth) / 2;
        int yStart = this.height / 6 + 20;
        int rowH = 24;
        int fieldH = 20;
        int textH = this.textRenderer.fontHeight;

        int twoColGap = 20;
        int colWidth = (guiWidth - twoColGap) / 2;
        int leftLabelX = x;
        int rightLabelX = x + colWidth + twoColGap;

        int row = 0;
        int baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.gui_width"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.gui_height"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_width"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_max_height"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.task_item_height"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.background_color"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.sidebar_width"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.sidebar_height"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_todo_limit"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_done_limit"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_opacity"), leftLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_default_expanded"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_show_when_empty"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("config.todolist.enable_sound_effects"), leftLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_default_view"), leftLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_project_source"), leftLabelX, baseY, 0xFFFFFF, false);

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
        context.drawBorder(hudX, hudY, previewHudWidth, previewHudHeight, 0xFFFFFFFF);
        Text line1 = Text.translatable("gui.todolist.config.hud_preview.title");
        Text line2 = Text.translatable("gui.todolist.config.hud_preview.hint");
        int line1Width = this.textRenderer.getWidth(line1);
        int line2Width = this.textRenderer.getWidth(line2);
        int centerX = hudX + previewHudWidth / 2;
        int centerY = hudY + previewHudHeight / 2;
        int lineSpacing = 2;
        int totalTextHeight = textH * 2 + lineSpacing;
        int startY = centerY - totalTextHeight / 2;
        int line1X = centerX - line1Width / 2;
        int line2X = centerX - line2Width / 2;
        context.drawText(this.textRenderer, line1, line1X, startY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, line2, line2X, startY + textH + lineSpacing, 0xFFFFFF, false);
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
        cfg.setGuiWidth(parseIntSafe(guiWidthField.getText(), cfg.getGuiWidth()));
        cfg.setGuiHeight(parseIntSafe(guiHeightField.getText(), cfg.getGuiHeight()));
        cfg.setHudWidth(parseIntSafe(hudWidthField.getText(), cfg.getHudWidth()));
        cfg.setHudMaxHeight(parseIntSafe(hudMaxHeightField.getText(), cfg.getHudMaxHeight()));
        cfg.setTaskItemHeight(parseIntSafe(taskItemHeightField.getText(), cfg.getTaskItemHeight()));
        cfg.setBackgroundColor(parseColorSafe(backgroundColorField.getText(), cfg.getBackgroundColor()));
        cfg.setProjectSidebarWidth(parseIntSafe(sidebarWidthField.getText(), cfg.getProjectSidebarWidth()));
        cfg.setProjectSidebarHeight(parseIntSafe(sidebarHeightField.getText(), cfg.getProjectSidebarHeight()));
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
        if (this.client != null && this.client.isInSingleplayer()) {
            cfg.setHudDefaultView("PERSONAL");
        } else {
            cfg.setHudDefaultView(hudDefaultViewValue);
        }
        cfg.setHudProjectSource(hudProjectSourceValue);
        this.client.setScreen(parent);
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
            hudExpandedButton.setMessage(Text.translatable(key));
        }
    }

    private void updateHudShowWhenEmptyButtonLabel() {
        if (hudShowWhenEmptyButton != null) {
            String key = hudShowWhenEmptyValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
            hudShowWhenEmptyButton.setMessage(Text.translatable(key));
        }
    }

    private void updateSoundEffectsButtonLabel() {
        if (soundEffectsButton != null) {
            String key = soundEffectsValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
            soundEffectsButton.setMessage(Text.translatable(key));
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
            hudDefaultViewButton.setMessage(Text.translatable(key));
        }
    }

    private void updateHudProjectSourceButtonLabel() {
        if (hudProjectSourceButton != null) {
            String value = hudProjectSourceValue == null ? "" : hudProjectSourceValue;
            if ("CURRENT".equalsIgnoreCase(value)) {
                Project project = getActiveProject();
                Text projectName = getProjectDisplayName(project);
                if (projectName != null && !projectName.getString().trim().isEmpty()) {
                    hudProjectSourceButton.setMessage(Text.translatable("gui.todolist.hud.project_source.current_selected", projectName.getString()));
                } else {
                    hudProjectSourceButton.setMessage(Text.translatable("gui.todolist.hud.project_source.current_selected_fallback"));
                }
                return;
            } else if ("STARRED".equalsIgnoreCase(value)) {
                hudProjectSourceButton.setMessage(Text.translatable("gui.todolist.hud.project_source.starred"));
                return;
            } else {
                hudProjectSourceButton.setMessage(Text.translatable("gui.todolist.hud.project_source.all"));
                return;
            }
        }
    }

    private static Project getActiveProject() {
        String activeProjectId = TodoClient.getActiveProjectId();
        if (activeProjectId == null || activeProjectId.isEmpty()) {
            return null;
        }
        return TodoListMod.getProjectManager().getProject(activeProjectId);
    }

    private static Text getProjectDisplayName(Project project) {
        if (project == null) {
            return Text.empty();
        }
        String name = project.getName();
        if (name == null || name.isEmpty()) {
            return Text.empty();
        }
        if (name.startsWith("gui.todolist.") || name.startsWith("item.") || name.startsWith("block.")) {
            return Text.translatable(name);
        }
        return Text.literal(name);
    }

    private static class HudViewOptions {
        static final String[] VALUES = new String[] { "PERSONAL", "TEAM_UNASSIGNED", "TEAM_ALL", "TEAM_ASSIGNED" };
    }

    private static class HudProjectSourceOptions {
        static final String[] VALUES = new String[] { "CURRENT", "STARRED", "ALL" };
    }

    private static class IntSliderWidget extends SliderWidget {
        private final int min;
        private final int max;

        IntSliderWidget(int x, int y, int width, int height, int min, int max, int value) {
            super(x, y, width, height, Text.empty(), 0.0);
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
            this.setMessage(Text.of(Integer.toString(getIntValue())));
        }

        @Override
        protected void applyValue() {
        }
    }

    private static class DoubleStepSliderWidget extends SliderWidget {
        private final double min;
        private final double max;
        private final double step;

        DoubleStepSliderWidget(int x, int y, int width, int height, double min, double max, double step, double value) {
            super(x, y, width, height, Text.empty(), 0.0);
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
            this.setMessage(Text.of(String.format(java.util.Locale.ROOT, "%.1f", getDoubleValue())));
        }

        @Override
        protected void applyValue() {
        }
    }
}
