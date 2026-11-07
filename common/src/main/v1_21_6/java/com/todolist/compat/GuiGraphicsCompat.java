package com.todolist.compat;

import net.minecraft.client.gui.GuiGraphics;

/**
 * GuiGraphics 版本差异适配。
 *
 * <p>本类为 1.21.6+ 覆盖版:GuiGraphics 改为命令录制式,flush 概念移除。
 */
public final class GuiGraphicsCompat {

    private GuiGraphicsCompat() {
    }

    /**
     * 刷新当前已提交的绘制命令(1.21.5- 直通,1.21.6+ 空操作)。
     *
     * @param context 绘制上下文
     */
    public static void flush(GuiGraphics context) {
        // 1.21.6+ GuiGraphics 为命令录制式,无 flush 概念;绘制顺序由提交顺序保证
    }
}
