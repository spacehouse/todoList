package com.todolist.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * 简单文本标签控件，仅负责展示文本，不参与交互。
 */
final class TextLabelWidget extends AbstractWidget {
    private final Font font;
    private final Component text;
    private final int color;

    /**
     * 创建只读文本标签控件。
     */
    TextLabelWidget(Font font, int x, int y, Component text, int color) {
        super(x, y, font.width(text), font.lineHeight, text);
        this.font = font;
        this.text = text;
        this.color = color;
        this.active = false;
    }

    /**
     * 绘制文本标签。
     */
    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.drawString(font, text, getX(), getY(), color, false);
    }

    /**
     * 更新旁白信息；该控件仅展示文本，无需额外旁白。
     */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
