package com.todolist.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 删除项目确认弹窗：展示提示信息并在确认后执行回调。
 */
public class ConfirmDeleteProjectScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private final Runnable onConfirm;

    /**
     * 创建删除项目确认弹窗。
     */
    public ConfirmDeleteProjectScreen(Screen parent, Component message, Runnable onConfirm) {
        super(Component.translatable("gui.todolist.project.delete_confirm.title"));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int w = Math.max(220, Math.min(360, width - 20));
        int h = Math.max(120, Math.min(180, height - 20));
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.delete"), b -> {
            onConfirm.run();
            onClose();
        }).bounds(x + 10, y + 85, 95, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), b -> onClose())
                .bounds(x + w - 105, y + 85, 95, 20).build());
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int w = Math.max(220, Math.min(360, width - 20));
        int h = Math.max(120, Math.min(180, height - 20));
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        context.fill(x, y, x + w, y + h, 0xFF202020);
        context.renderOutline(x, y, w, h, 0xFFFFFFFF);

        context.drawString(font, title, x + 10, y + 10, 0xFFFFFFFF, false);
        context.drawWordWrap(font, message, x + 10, y + 35, w - 20, 0xFFDDDDDD);

        super.render(context, mouseX, mouseY, delta);
    }
}



