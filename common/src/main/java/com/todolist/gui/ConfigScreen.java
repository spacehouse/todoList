package com.todolist.gui;

import com.todolist.TodoConstants;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 配置界面：提供 HUD 外观与行为的本地配置编辑与预览。
 */
public class ConfigScreen extends Screen {

    private final Screen parent;

    private EditBox hudWidthField;
    private EditBox hudMaxHeightField;
    private IntSliderWidget hudTodoLimitSlider;
    private IntSliderWidget hudDoneLimitSlider;
    private DoubleStepSliderWidget hudOpacitySlider;
    private Button hudShowWhenEmptyButton;
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

    private boolean hudShowWhenEmptyValue;
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
        int guiWidth = clampInt(this.width - 40, 360, 560);
        int x = (this.width - guiWidth) / 2;
        int y = Math.max(36, this.height / 6 + 10);
        int row = 0;
        int rowH = 25; // 20 (height) + 5 (gap)
        int fieldH = 20;

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

        // Row 1: HUD Width | HUD Max Height
        hudWidthField = new EditBox(this.font, leftFieldX, y + row * rowH, leftFieldWidth, fieldH, Component.empty());
        hudWidthField.setValue(Integer.toString(cfg.getHudWidth()));
        this.addRenderableWidget(hudWidthField);

        hudMaxHeightField = new EditBox(this.font, rightFieldX, y + row * rowH, rightFieldWidth, fieldH, Component.empty());
        hudMaxHeightField.setValue(Integer.toString(cfg.getHudMaxHeight()));
        this.addRenderableWidget(hudMaxHeightField);
        row++;

        // Row 2: Todo Limit | Done Limit
        int todoInitial = cfg.getHudTodoLimit();
        int doneInitial = cfg.getHudDoneLimit();
        hudTodoLimitSlider = new IntSliderWidget(leftFieldX, y + row * rowH, leftFieldWidth, fieldH, 0, 30, todoInitial);
        hudDoneLimitSlider = new IntSliderWidget(rightFieldX, y + row * rowH, rightFieldWidth, fieldH, 0, 30, doneInitial);
        this.addRenderableWidget(hudTodoLimitSlider);
        this.addRenderableWidget(hudDoneLimitSlider);
        row++;

        // Row 3: Show When Empty
        hudShowWhenEmptyValue = cfg.isHudShowWhenEmpty();

        hudShowWhenEmptyButton = Button.builder(Component.empty(), b -> {
            hudShowWhenEmptyValue = !hudShowWhenEmptyValue;
            updateHudShowWhenEmptyButtonLabel();
        }).bounds(leftFieldX, y + row * rowH, leftFieldWidth, fieldH).build();
        this.addRenderableWidget(hudShowWhenEmptyButton);
        row++;

        // Row 4: HUD Opacity (Left Only)
        int opacityLabelWidth = this.font.width(Component.translatable("gui.todolist.config.hud_opacity"));
        // Align slider with other left fields
        int opacitySliderX = leftFieldX; 
        int opacitySliderW = leftFieldWidth;
        hudOpacitySlider = new DoubleStepSliderWidget(opacitySliderX, y + row * rowH, opacitySliderW, fieldH, 0.0, 1.0, 0.1, cfg.getHudOpacity());
        this.addRenderableWidget(hudOpacitySlider);
        row++;

        // Row 5: Project Source (Full Width)
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
        // Calculate X to align the button part such that label is to the left?
        // Standard drawLabelForWidget draws label to the left of widget.
        // If we want full width widget, we might need to inset it to make room for label, or put label above.
        // User said: "HUD列表来源可以占整行" (HUD Project Source can take full row).
        // Let's make the button occupy the remaining width after the label, spanning both columns.
        // Layout: Label [Button--------------------]
        int sourceButtonX = x + sourceLabelWidth + 10;
        int sourceButtonWidth = Math.max(80, guiWidth - (sourceButtonX - x));

        hudProjectSourceButton = Button.builder(Component.empty(), b -> {
            hudProjectSourceIndex = (hudProjectSourceIndex + 1) % HudProjectSourceOptions.VALUES.length;
            hudProjectSourceValue = HudProjectSourceOptions.VALUES[hudProjectSourceIndex];
            updateHudProjectSourceButtonLabel();
        }).bounds(sourceButtonX, y + row * rowH, sourceButtonWidth, fieldH).build();
        this.addRenderableWidget(hudProjectSourceButton);
        updateHudProjectSourceButtonLabel();
        row++;

        // Note: Default View is not in the user's requested list, but it's part of HUD config.
        // User list: Width, MaxHeight, TodoLimit, DoneLimit, Opacity, DefaultExpanded, ShowWhenEmpty, ProjectSource.
        // Default View is missing from user request. I should probably remove it to strictly follow "Only retain...".
        // Or maybe "HUD列表项目来源" implies view control? No, Project Source is distinct.
        // I will follow the user's explicit list.

        updateHudShowWhenEmptyButtonLabel();

        // Preview initialization
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

        // Clamp preview position to screen bounds
        if (previewHudX < 0) previewHudX = 0;
        if (previewHudY < 0) previewHudY = 0;
        if (previewHudX + previewHudWidth > this.width) previewHudX = Math.max(0, this.width - previewHudWidth);
        if (previewHudY + previewHudHeight > this.height) previewHudY = Math.max(0, this.height - previewHudHeight);

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
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        int guiWidth = clampInt(this.width - 40, 360, 560);
        int x = (this.width - guiWidth) / 2;
        int yStart = Math.max(36, this.height / 6 + 10);
        int textH = this.font.lineHeight;
        context.drawString(this.font, title, x, yStart - 36, 0xFFFFFFFF, false);

        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_width"), hudWidthField, textH);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_max_height"), hudMaxHeightField, textH);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_todo_limit"), hudTodoLimitSlider, textH);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_done_limit"), hudDoneLimitSlider, textH);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_show_when_empty"), hudShowWhenEmptyButton, textH);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_opacity"), hudOpacitySlider, textH);
        drawLabelForWidget(context, Component.translatable("gui.todolist.config.hud_project_source"), hudProjectSourceButton, textH);

        int hudX = previewHudX;
        int hudY = previewHudY;
        // Clamp for rendering safety
        if (hudX < 0) hudX = 0;
        if (hudY < 0) hudY = 0;
        if (hudX + previewHudWidth > this.width) hudX = Math.max(0, this.width - previewHudWidth);
        if (hudY + previewHudHeight > this.height) hudY = Math.max(0, this.height - previewHudHeight);
        
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
            if (newX + previewHudWidth > this.width) newX = Math.max(0, this.width - previewHudWidth);
            if (newY + previewHudHeight > this.height) newY = Math.max(0, this.height - previewHudHeight);
            previewHudX = newX;
            previewHudY = newY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void applyAndReturn() {
        ModConfig cfg = ModConfig.getInstance();
        cfg.setHudWidth(parseIntSafe(hudWidthField.getValue(), cfg.getHudWidth()));
        cfg.setHudMaxHeight(parseIntSafe(hudMaxHeightField.getValue(), cfg.getHudMaxHeight()));
        cfg.setHudTodoLimit(hudTodoLimitSlider.getIntValue());
        cfg.setHudDoneLimit(hudDoneLimitSlider.getIntValue());
        if (hudOpacitySlider != null) {
            cfg.setHudOpacity(hudOpacitySlider.getDoubleValue());
        }
        cfg.setHudUseCustomPosition(previewUseCustom);
        cfg.setHudCustomX(previewHudX);
        cfg.setHudCustomY(previewHudY);
        cfg.setHudShowWhenEmpty(hudShowWhenEmptyValue);
        
        // Note: Default View is not exposed in UI anymore, so we keep current value or default.
        // cfg.setHudDefaultView(...); 
        
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

    private void updateHudShowWhenEmptyButtonLabel() {
        if (hudShowWhenEmptyButton != null) {
            String key = hudShowWhenEmptyValue ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
            hudShowWhenEmptyButton.setMessage(Component.translatable(key));
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
        // Since we removed the view selector, we rely on the saved config or default
        String view = ModConfig.getInstance().getHudDefaultView();
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

    private void drawLabelForWidget(GuiGraphics context, Component label, AbstractWidget widget, int textH) {
        if (widget == null || !widget.visible) {
            return;
        }
        int y = widget.getY() + (widget.getHeight() - textH) / 2;
        int labelWidth = this.font.width(label);
        int x = Math.max(8, widget.getX() - labelWidth - 8);
        context.drawString(this.font, label, x, y, 0xFFFFFF, false);
    }

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
