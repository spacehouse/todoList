package com.todolist.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfirmDeleteProjectScreen extends Screen {
    private final Screen parent;
    private final Text message;
    private final Runnable onConfirm;

    public ConfirmDeleteProjectScreen(Screen parent, Text message, Runnable onConfirm) {
        super(Text.translatable("gui.todolist.project.delete_confirm.title"));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int w = 220;
        int h = 120;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.todolist.delete"), b -> {
            onConfirm.run();
            close();
        }).dimensions(x + 10, y + 85, 95, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), b -> close())
                .dimensions(x + w - 105, y + 85, 95, 20).build());
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int w = 220;
        int h = 120;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        context.fill(x, y, x + w, y + h, 0xFF202020);
        context.drawBorder(x, y, w, h, 0xFFFFFFFF);

        context.drawText(textRenderer, title, x + 10, y + 10, 0xFFFFFFFF, false);
        context.drawTextWrapped(textRenderer, message, x + 10, y + 35, w - 20, 0xFFDDDDDD);

        super.render(context, mouseX, mouseY, delta);
    }
}

