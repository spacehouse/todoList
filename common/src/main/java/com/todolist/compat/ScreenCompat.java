package com.todolist.compat;

import com.todolist.TodoConstants;
import com.todolist.gui.BaseTodoScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * 屏幕兼容工具，统一处理 1.20.1 与 1.20.2 之间的 GUI 签名差异。
 *
 * <p>所有版本相关调用均委托 {@link BaseTodoScreen} 的构建期注入成员，
 * 不再使用运行时按名字反射：Fabric/Forge 生产环境中 Minecraft 方法名
 * 分别为 intermediary/SRG 命名，按名字反射必然失败。</p>
 */
public final class ScreenCompat {
    private ScreenCompat() {
    }

    /**
     * 调用当前版本可用的屏幕背景渲染方法（原版标准背景）。
     *
     * @param screen 当前界面实例
     * @param context 当前绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 渲染插值
     */
    public static void renderBackground(Screen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (screen instanceof BaseTodoScreen base) {
            base.renderVanillaBackground(context, mouseX, mouseY, delta);
            return;
        }
        TodoConstants.LOGGER.warn("renderBackground compat requires BaseTodoScreen, got {}", screen.getClass().getName());
    }

    /**
     * 设置屏幕初始焦点，兼容 1.20.1~1.20.4 与 1.20.5+ 的差异。
     * 1.20.5 起 Screen#init 会自动执行一次 Tab 导航设置初始焦点；
     * 若在此之前手动聚焦目标，导航会把焦点移动到下一个组件，因此新版跳过手动设置。
     *
     * @param screen 当前界面实例
     * @param target 期望获得初始焦点的组件
     */
    public static void setInitialFocusCompat(Screen screen, GuiEventListener target) {
        if (screen instanceof BaseTodoScreen base && base.supportsAutoInitialFocus()) {
            // 1.20.5+：交给自动 Tab 导航从头聚焦第一个组件
            return;
        }
        // 1.20.1~1.20.4：无自动初始焦点，需要手动聚焦
        screen.setFocused(target);
    }

    /**
     * 调用当前版本对应的父类滚轮处理实现。
     *
     * @param screen 当前界面实例
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param horizontalAmount 横向滚动量
     * @param verticalAmount 纵向滚动量
     * @return 父类是否已处理
     */
    public static boolean callSuperMouseScrolled(Screen screen,
                                                 double mouseX,
                                                 double mouseY,
                                                 double horizontalAmount,
                                                 double verticalAmount) {
        if (screen instanceof BaseTodoScreen base) {
            return base.superMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return false;
    }
}
