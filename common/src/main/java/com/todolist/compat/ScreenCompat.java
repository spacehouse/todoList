package com.todolist.compat;

import com.todolist.TodoConstants;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * 屏幕兼容工具，统一处理 1.20.1 与 1.20.2 之间的 GUI 签名差异。
 */
public final class ScreenCompat {
    private ScreenCompat() {
    }

    /**
     * 调用当前版本可用的屏幕背景渲染方法。
     *
     * @param screen 当前界面实例
     * @param context 当前绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 渲染插值
     */
    public static void renderBackground(Screen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        try {
            Method modernMethod = Screen.class.getMethod("renderBackground", GuiGraphics.class, int.class, int.class, float.class);
            modernMethod.invoke(screen, context, mouseX, mouseY, delta);
            return;
        } catch (NoSuchMethodException ignored) {
            // Fall through to the 1.20.1 signature.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke modern Screen#renderBackground", e);
        }

        try {
            Method legacyMethod = Screen.class.getMethod("renderBackground", GuiGraphics.class);
            legacyMethod.invoke(screen, context);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke legacy Screen#renderBackground", e);
        }
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
        try {
            Screen.class.getDeclaredMethod("setInitialFocus");
            // 1.20.5+：交给自动 Tab 导航从头聚焦第一个组件
            return;
        } catch (NoSuchMethodException ignored) {
            // 1.20.1~1.20.4：无自动初始焦点，需要手动聚焦
            screen.setFocused(target);
        }
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
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(screen.getClass(), MethodHandles.lookup());
            try {
                MethodHandle modernHandle = lookup.findSpecial(
                        Screen.class,
                        "mouseScrolled",
                        MethodType.methodType(boolean.class, double.class, double.class, double.class, double.class),
                        screen.getClass()
                );
                return (boolean) modernHandle.bindTo(screen).invokeWithArguments(mouseX, mouseY, horizontalAmount, verticalAmount);
            } catch (NoSuchMethodException ignored) {
                double amount = verticalAmount != 0 ? verticalAmount : horizontalAmount;
                MethodHandle legacyHandle = lookup.findSpecial(
                        Screen.class,
                        "mouseScrolled",
                        MethodType.methodType(boolean.class, double.class, double.class, double.class),
                        screen.getClass()
                );
                return (boolean) legacyHandle.bindTo(screen).invokeWithArguments(mouseX, mouseY, amount);
            }
        } catch (Throwable throwable) {
            TodoConstants.LOGGER.warn("Failed to call Screen#mouseScrolled compatibly", throwable);
            return false;
        }
    }
}
