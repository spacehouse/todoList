package com.todolist.compat;

import net.minecraft.client.gui.GuiGraphics;

/**
 * GuiGraphics 版本差异适配。
 *
 * <p>版本敏感实现:本类(主源码树)为 1.20.x~1.21.5 形态,提供 {@link GuiGraphics#flush()}
 * 的直通调用;1.21.6 起 GuiGraphics 改为命令录制式,flush 移除,由 v1_21_6 覆盖目录提供空实现
 * (录制顺序天然保证浮层后画)。
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
        context.flush();
    }
}
