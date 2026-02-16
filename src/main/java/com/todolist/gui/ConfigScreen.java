package com.todolist.gui;

import com.todolist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget hudWidthField;
    private TextFieldWidget hudMaxHeightField;
    private TextFieldWidget hudTodoLimitField;
    private TextFieldWidget hudDoneLimitField;
    private TextFieldWidget hudExpandedField;
    private TextFieldWidget hudShowWhenEmptyField;
    private TextFieldWidget hudCustomXField;
    private TextFieldWidget hudCustomYField;

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

        hudWidthField = new TextFieldWidget(this.textRenderer, x + labelWidth, y + row * rowH, fieldWidth, fieldH, Text.empty());
        hudWidthField.setText(Integer.toString(cfg.getHudWidth())); row++;

        hudMaxHeightField = new TextFieldWidget(this.textRenderer, x + labelWidth, y + row * rowH, fieldWidth, fieldH, Text.empty());
        hudMaxHeightField.setText(Integer.toString(cfg.getHudMaxHeight())); row++;

        hudTodoLimitField = new TextFieldWidget(this.textRenderer, x + labelWidth, y + row * rowH, fieldWidth, fieldH, Text.empty());
        hudTodoLimitField.setText(Integer.toString(cfg.getHudTodoLimit())); row++;

        hudDoneLimitField = new TextFieldWidget(this.textRenderer, x + labelWidth, y + row * rowH, fieldWidth, fieldH, Text.empty());
        hudDoneLimitField.setText(Integer.toString(cfg.getHudDoneLimit())); row++;

        hudExpandedField = new TextFieldWidget(this.textRenderer, x + labelWidth, y + row * rowH, fieldWidth, fieldH, Text.empty());
        hudExpandedField.setText(Boolean.toString(cfg.isHudDefaultExpanded())); row++;

        hudShowWhenEmptyField = new TextFieldWidget(this.textRenderer, x + labelWidth, y + row * rowH, fieldWidth, fieldH, Text.empty());
        hudShowWhenEmptyField.setText(Boolean.toString(cfg.isHudShowWhenEmpty())); row++;

        this.addDrawableChild(hudWidthField);
        this.addDrawableChild(hudMaxHeightField);
        this.addDrawableChild(hudTodoLimitField);
        this.addDrawableChild(hudDoneLimitField);
        this.addDrawableChild(hudExpandedField);
        this.addDrawableChild(hudShowWhenEmptyField);

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

        int buttonY = y + row * rowH + 12;
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
        int labelX = (this.width - guiWidth) / 2;
        int yStart = this.height / 6 + 20;
        int rowH = 24;
        int fieldH = 20;
        int textH = this.textRenderer.fontHeight;

        int row = 0;
        int baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_width"), labelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_max_height"), labelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_todo_limit"), labelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_done_limit"), labelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_default_expanded"), labelX, baseY, 0xFFFFFF, false);
        row++;
        baseY = yStart + row * rowH + (fieldH - textH) / 2;
        context.drawText(this.textRenderer, Text.translatable("gui.todolist.config.hud_show_when_empty"), labelX, baseY, 0xFFFFFF, false);

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
        cfg.setHudTodoLimit(parseIntSafe(hudTodoLimitField.getText(), cfg.getHudTodoLimit()));
        cfg.setHudDoneLimit(parseIntSafe(hudDoneLimitField.getText(), cfg.getHudDoneLimit()));
        cfg.setHudDefaultExpanded(parseBooleanSafe(hudExpandedField.getText(), cfg.isHudDefaultExpanded()));
        cfg.setHudUseCustomPosition(previewUseCustom);
        cfg.setHudCustomX(previewHudX);
        cfg.setHudCustomY(previewHudY);
        cfg.setHudShowWhenEmpty(parseBooleanSafe(hudShowWhenEmptyField.getText(), cfg.isHudShowWhenEmpty()));
        this.client.setScreen(parent);
    }

    private int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean parseBooleanSafe(String s, boolean fallback) {
        try {
            return Boolean.parseBoolean(s.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
