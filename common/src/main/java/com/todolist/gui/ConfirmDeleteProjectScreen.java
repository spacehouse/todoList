package com.todolist.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 删除项目确认弹窗。
 * 用于展示删除提示信息，并在用户确认后执行对应的删除回调。
 */
public class ConfirmDeleteProjectScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private final Runnable onConfirm;

    /**
     * 创建删除项目确认弹窗。
     *
     * @param parent 父界面
     * @param message 删除提示文案
     * @param onConfirm 确认删除后的回调
     */
    public ConfirmDeleteProjectScreen(Screen parent, Component message, Runnable onConfirm) {
        super(Component.translatable("gui.todolist.project.delete_confirm.title"));
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    /**
     * 初始化删除确认弹窗中的按钮布局。
     */
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

    /**
     * 关闭弹窗并返回父界面。
     */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /**
     * 渲染删除确认弹窗的背景、标题与提示文案。
     *
     * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

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
