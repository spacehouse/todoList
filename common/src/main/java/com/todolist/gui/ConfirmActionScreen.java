package com.todolist.gui;

import com.todolist.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 通用确认操作弹窗。
 * 用于展示标题、说明文案以及确认和取消按钮，承载高风险操作的二次确认。
 */
public class ConfirmActionScreen extends Screen {
    private final Screen parent;
    private final Component message;
    private final Component confirmButtonText;
    private final Runnable onConfirm;

    /**
     * 创建通用确认操作弹窗。
     *
     * @param parent 父界面
     * @param title 弹窗标题
     * @param message 弹窗说明文案
     * @param confirmButtonText 确认按钮文本
     * @param onConfirm 确认后的回调
     */
    public ConfirmActionScreen(Screen parent,
                               Component title,
                               Component message,
                               Component confirmButtonText,
                               Runnable onConfirm) {
        super(title);
        this.parent = parent;
        this.message = message == null ? Component.empty() : message;
        this.confirmButtonText = confirmButtonText == null ? Component.empty() : confirmButtonText;
        this.onConfirm = onConfirm;
    }

    /**
     * 初始化确认弹窗中的按钮布局。
     */
    @Override
    protected void init() {
        int dialogWidth = Math.max(220, Math.min(360, width - 20));
        int dialogHeight = Math.max(120, Math.min(180, height - 20));
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        addRenderableWidget(Button.builder(confirmButtonText, button -> {
            if (onConfirm != null) {
                onConfirm.run();
            }
            onClose();
        }).bounds(dialogX + 10, dialogY + 85, 95, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(dialogX + dialogWidth - 105, dialogY + 85, 95, 20).build());
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
     * 覆盖默认背景渲染，保持由 render 方法统一手工绘制背景。
     *
     * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔
     */
    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background is drawn manually in render to keep cross-loader consistency.
    }

    /**
     * 渲染确认弹窗的背景、标题和说明文案。
     *
     * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());

        int dialogWidth = Math.max(220, Math.min(360, width - 20));
        int dialogHeight = Math.max(120, Math.min(180, height - 20));
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;

        context.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF202020);
        context.renderOutline(dialogX, dialogY, dialogWidth, dialogHeight, 0xFFFFFFFF);

        context.drawString(font, title, dialogX + 10, dialogY + 10, 0xFFFFFFFF, false);
        context.drawWordWrap(font, message, dialogX + 10, dialogY + 35, dialogWidth - 20, 0xFFDDDDDD);

        super.render(context, mouseX, mouseY, delta);
    }
}
