package com.todolist.gui;

import com.todolist.TodoListMod;
import com.todolist.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Screen for Todo List Mod
 *
 * Features:
 * - Real-time GUI configuration
 * - Live preview
 * - Reset to defaults
 */
public class ConfigScreen extends Screen {
    private static final Text TITLE = Text.of("待办列表 - 设置");

    private final Screen parent;
    private final TodoScreen todoScreen;

    // Labels to draw
    private static class ConfigLabel {
        final String text;
        final int x;
        final int y;

        ConfigLabel(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
    private final List<ConfigLabel> labels = new ArrayList<>();

    // Configuration input fields
    private TextFieldWidget guiWidthField;
    private TextFieldWidget guiHeightField;
    private TextFieldWidget taskListHeightField;
    private TextFieldWidget taskItemHeightField;
    private TextFieldWidget paddingField;
    private TextFieldWidget elementSpacingField;

    // Action buttons
    private ButtonWidget saveButton;
    private ButtonWidget resetButton;
    private ButtonWidget cancelButton;

    public ConfigScreen(Screen parent, TodoScreen todoScreen) {
        super(TITLE);
        this.parent = parent;
        this.todoScreen = todoScreen;
    }

    @Override
    protected void init() {
        super.init();

        ModConfig config = ModConfig.getInstance();

        int centerX = width / 2;
        int centerY = height / 2;
        int panelWidth = 450;
        int panelHeight = 380;
        int x = centerX - panelWidth / 2;
        int y = centerY - panelHeight / 2;

        // Title
        int titleY = y + 20;

        // GUI Size settings
        int labelY = titleY + 35;
        addConfigOption(x + 20, labelY, "GUI宽度:", String.valueOf(config.getGuiWidth()), 80, this::onGuiWidthChanged);
        addConfigOption(x + 230, labelY, "GUI高度:", String.valueOf(config.getGuiHeight()), 80, this::onGuiHeightChanged);

        labelY += 28;
        addConfigOption(x + 20, labelY, "列表高度:", String.valueOf(config.getTaskListHeight()), 80, this::onTaskListHeightChanged);
        addConfigOption(x + 230, labelY, "任务项高度:", String.valueOf(config.getTaskItemHeight()), 80, this::onTaskItemHeightChanged);

        labelY += 28;
        addConfigOption(x + 20, labelY, "边距:", String.valueOf(config.getPadding()), 80, this::onPaddingChanged);
        addConfigOption(x + 230, labelY, "间距:", String.valueOf(config.getElementSpacing()), 80, this::onSpacingChanged);

        labelY += 28;
        addConfigOption(x + 20, labelY, "背景颜色:", String.format("0x%08X", config.getBackgroundColor()), 100, this::onBackgroundColorChanged);
        addConfigOption(x + 230, labelY, "选中颜色:", String.format("0x%08X", config.getSelectedBackgroundColor()), 100, this::onSelectedColorChanged);

        labelY += 28;
        addConfigOption(x + 20, labelY, "悬停颜色:", String.format("0x%08X", config.getHoveredBackgroundColor()), 100, this::onHoveredColorChanged);
        addConfigOption(x + 230, labelY, "边框颜色:", String.format("0x%08X", config.getBorderColor()), 100, this::onBorderColorChanged);

        // Buttons at bottom
        int buttonY = y + panelHeight - 50;

        saveButton = ButtonWidget.builder(Text.of("保存并应用"), button -> {
            saveAndApply();
        }).dimensions(x + panelWidth / 2 - 155, buttonY, 100, 20).build();
        addDrawableChild(saveButton);

        resetButton = ButtonWidget.builder(Text.of("重置默认"), button -> {
            resetToDefaults();
        }).dimensions(x + panelWidth / 2 - 45, buttonY, 90, 20).build();
        addDrawableChild(resetButton);

        cancelButton = ButtonWidget.builder(Text.of("取消"), button -> {
            close();
        }).dimensions(x + panelWidth / 2 + 55, buttonY, 100, 20).build();
        addDrawableChild(cancelButton);
    }

    private void addConfigOption(int x, int y, String label, String value, int fieldWidth, java.util.function.Consumer<String> callback) {
        // Store label for rendering
        labels.add(new ConfigLabel(label, x, y));
        y += 15;

        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, fieldWidth, 20, Text.of(value));
        field.setText(value);
        field.setMaxLength(10);
        field.setChangedListener((text) -> {
            callback.accept(text);
        });

        addDrawableChild(field);

        // Store references for later access
        if (label.contains("宽度")) guiWidthField = field;
        else if (label.contains("高度") && !label.contains("列表")) guiHeightField = field;
        else if (label.contains("列表高度")) taskListHeightField = field;
        else if (label.contains("项高度")) taskItemHeightField = field;
        else if (label.contains("边距")) paddingField = field;
        else if (label.contains("间距")) elementSpacingField = field;
        else if (label.contains("背景颜色")) {
            // Note: Background color is handled specially in onBackgroundColorChanged
            // We'll keep a reference but the input is handled differently
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // Draw title
        context.drawText(textRenderer, TITLE,
                (width - textRenderer.getWidth(TITLE)) / 2,
                (height - 380) / 2 - 5,
                0xFFFFFFFF, true);

        // Draw panel border
        int panelWidth = 450;
        int panelHeight = 380;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        context.fill(x, y, x + panelWidth, y + panelHeight, 0xFF000000);
        context.drawBorder(x, y, panelWidth, panelHeight, 0xFF3F3F3F);

        // Draw labels
        for (ConfigLabel label : labels) {
            context.drawText(textRenderer, Text.of(label.text), label.x, label.y, 0xFFFFFFFF, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private void saveAndApply() {
        // Save is already done when values change
        // Return to the todo screen (it will auto-reload)
        client.setScreen(todoScreen);
    }

    private void resetToDefaults() {
        ModConfig config = ModConfig.getInstance();

        // Reset all values to defaults
        config.setGuiWidth(400);
        config.setGuiHeight(280);
        config.setTaskListHeight(140);
        config.setTaskItemHeight(25);
        config.setPadding(10);
        config.setElementSpacing(5);
        config.setBackgroundColor(0xFF000000);
        config.setSelectedBackgroundColor(0xFF555555);
        config.setHoveredBackgroundColor(0xFF333333);
        config.setBorderColor(0xFF3F3F3F);

        // Reopen this config screen with new values
        client.setScreen(new ConfigScreen(parent, todoScreen));
    }

    // Configuration change handlers
    private void onGuiWidthChanged(String value) {
        try {
            int width = Integer.parseInt(value);
            if (width >= 300 && width <= 800) {
                ModConfig.getInstance().setGuiWidth(width);
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onGuiHeightChanged(String value) {
        try {
            int height = Integer.parseInt(value);
            if (height >= 200 && height <= 600) {
                ModConfig.getInstance().setGuiHeight(height);
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onTaskListHeightChanged(String value) {
        try {
            int height = Integer.parseInt(value);
            if (height >= 50 && height <= 400) {
                ModConfig.getInstance().setTaskListHeight(height);
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onTaskItemHeightChanged(String value) {
        try {
            int height = Integer.parseInt(value);
            if (height >= 20 && height <= 50) {
                ModConfig.getInstance().setTaskItemHeight(height);
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onPaddingChanged(String value) {
        try {
            int padding = Integer.parseInt(value);
            if (padding >= 0 && padding <= 50) {
                ModConfig.getInstance().setPadding(padding);
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onSpacingChanged(String value) {
        try {
            int spacing = Integer.parseInt(value);
            if (spacing >= 0 && spacing <= 20) {
                ModConfig.getInstance().setElementSpacing(spacing);
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onBackgroundColorChanged(String value) {
        try {
            // Parse hex color (0x prefixed)
            String hex = value.replace("0x", "").trim();
            int color = (int) Long.parseLong(hex, 16);
            ModConfig.getInstance().setBackgroundColor(color);
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onSelectedColorChanged(String value) {
        try {
            String hex = value.replace("0x", "").trim();
            int color = (int) Long.parseLong(hex, 16);
            ModConfig.getInstance().setSelectedBackgroundColor(color);
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onHoveredColorChanged(String value) {
        try {
            String hex = value.replace("0x", "").trim();
            int color = (int) Long.parseLong(hex, 16);
            ModConfig.getInstance().setHoveredBackgroundColor(color);
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    private void onBorderColorChanged(String value) {
        try {
            String hex = value.replace("0x", "").trim();
            int color = (int) Long.parseLong(hex, 16);
            ModConfig.getInstance().setBorderColor(color);
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }
}
