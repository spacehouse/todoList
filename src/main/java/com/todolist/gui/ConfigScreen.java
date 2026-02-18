package com.todolist.gui;

import com.todolist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget hudWidthField;
    private TextFieldWidget hudMaxHeightField;
    private IntSliderWidget hudTodoLimitSlider;
    private IntSliderWidget hudDoneLimitSlider;
    private ButtonWidget hudExpandedButton;
    private ButtonWidget hudShowWhenEmptyButton;
    private ButtonWidget hudDefaultViewButton;

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
    private String hudDefaultViewValue;
    private int hudDefaultViewIndex;

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

        hudWidthField = new TextFieldWidget(this.textRenderer, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Text.empty());
        hudWidthField.setText(Integer.toString(cfg.getHudWidth()));
        this.addDrawableChild(hudWidthField);

        hudMaxHeightField = new TextFieldWidget(this.textRenderer, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Text.empty());
        hudMaxHeightField.setText(Integer.toString(cfg.getHudMaxHeight()));
        this.addDrawableChild(hudMaxHeightField);
        row++;

        int todoInitial = cfg.getHudTodoLimit();
        int doneInitial = cfg.getHudDoneLimit();
        hudTodoLimitSlider = new IntSliderWidget(leftFieldX, y + row * rowH, leftFieldWidth, fieldH, 0, 30, todoInitial);
        hudDoneLimitSlider = new IntSliderWidget(rightFieldX, y + row * rowH, rightFieldWidth, fieldH, 0, 30, doneInitial);
        this.addDrawableChild(hudTodoLimitSlider);
        this.addDrawableChild(hudDoneLimitSlider);
        row++;

        hudExpandedValue = cfg.isHudDefaultExpanded();
        hudShowWhenEmptyValue = cfg.isHudShowWhenEmpty();

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
        updateHudDefaultViewButtonLabel();

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
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_width"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_max_height"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_todo_limit"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_done_limit"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_default_expanded"), leftLabelX, baseY, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_show_when_empty"), rightLabelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_default_view"), leftLabelX, baseY, 0xFFFFFF, false);

        int hudX = previewHudX;
        int hudY = previewHudY;
        if (hudX < 0) hudX = 0;
        if (hudY < 0) hudY = 0;
        if (hudX + previewHudWidth > this.width) hudX = this.width - previewHudWidth;
        if (hudY + previewHudHeight > this.height) hudY = this.height - previewHudHeight;
        previewRectX = hudX;
        previewRectY = hudY;
        context.fill(hudX, hudY, hudX + previewHudWidth, hudY + previewHudHeight, 0x55000000);
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
        cfg.setHudWidth(parseIntSafe(hudWidthField.getText(), cfg.getHudWidth()));
        cfg.setHudMaxHeight(parseIntSafe(hudMaxHeightField.getText(), cfg.getHudMaxHeight()));
        cfg.setHudTodoLimit(hudTodoLimitSlider.getIntValue());
        cfg.setHudDoneLimit(hudDoneLimitSlider.getIntValue());
        cfg.setHudDefaultExpanded(hudExpandedValue);
        cfg.setHudUseCustomPosition(previewUseCustom);
        cfg.setHudCustomX(previewHudX);
        cfg.setHudCustomY(previewHudY);
        cfg.setHudShowWhenEmpty(hudShowWhenEmptyValue);
        if (this.client != null && this.client.isInSingleplayer()) {
            cfg.setHudDefaultView("PERSONAL");
        } else {
            cfg.setHudDefaultView(hudDefaultViewValue);
        }
        this.client.setScreen(parent);
    }

    private int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
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

    private static class HudViewOptions {
        static final String[] VALUES = new String[] { "PERSONAL", "TEAM_UNASSIGNED", "TEAM_ALL", "TEAM_ASSIGNED" };
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
}
