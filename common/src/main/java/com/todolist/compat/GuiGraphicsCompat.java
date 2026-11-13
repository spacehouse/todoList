package com.todolist.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * GuiGraphicsExtractor 版本差异适配（1.21.9+ 形态）。
 *
 * <p>1.21.6 起 GuiGraphicsExtractor 改为命令录制式，{@code flush()} 概念移除；
 * 绘制顺序由提交顺序保证，本类为空实现占位，保持调用点版本无关。
 */
public final class GuiGraphicsCompat {

    private GuiGraphicsCompat() {
    }

    /**
     * 刷新当前已提交的绘制命令（1.21.9+ 为空操作）。
     *
     * @param context 绘制上下文
     */
    public static void flush(GuiGraphicsExtractor context) {
        // 1.21.6+ GuiGraphicsExtractor 为命令录制式，无 flush 概念
    }
}
