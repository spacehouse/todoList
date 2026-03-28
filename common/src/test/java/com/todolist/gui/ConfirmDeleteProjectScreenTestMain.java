package com.todolist.gui;

import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 删除项目确认弹窗离线自测入口：覆盖确认回调与取消返回语义。
 */
public final class ConfirmDeleteProjectScreenTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ConfirmDeleteProjectScreenTestMain() {
    }

    /**
     * 程序入口，串行执行删除项目确认弹窗的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("ConfirmDeleteProjectScreenTestMain.shouldRunConfirmCallbackAndClose", ConfirmDeleteProjectScreenTestMain::shouldRunConfirmCallbackAndClose);
        GuiTestSupport.runTestCase("ConfirmDeleteProjectScreenTestMain.shouldCancelWithoutRunningConfirmCallback", ConfirmDeleteProjectScreenTestMain::shouldCancelWithoutRunningConfirmCallback);
    }

    /**
     * 校验确认按钮会执行回调并返回父界面。
     */
    private static void shouldRunConfirmCallbackAndClose() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        AtomicInteger confirmCount = new AtomicInteger();
        ConfirmDeleteProjectScreen screen = new ConfirmDeleteProjectScreen(
                parent,
                Component.literal("delete project"),
                confirmCount::incrementAndGet
        );
        ScreenDriver.init(minecraft, screen);
        List<Button> buttons = ScreenDriver.getButtons(screen);

        ScreenDriver.click(buttons.get(0));

        GuiTestSupport.assertEquals(1, confirmCount.get(), "确认按钮应执行删除确认回调");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "确认后应返回父界面");
    }

    /**
     * 校验取消按钮不会执行回调，只返回父界面。
     */
    private static void shouldCancelWithoutRunningConfirmCallback() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        AtomicInteger confirmCount = new AtomicInteger();
        ConfirmDeleteProjectScreen screen = new ConfirmDeleteProjectScreen(
                parent,
                Component.literal("delete project"),
                confirmCount::incrementAndGet
        );
        ScreenDriver.init(minecraft, screen);
        List<Button> buttons = ScreenDriver.getButtons(screen);

        ScreenDriver.click(buttons.get(1));

        GuiTestSupport.assertEquals(0, confirmCount.get(), "取消按钮不应执行删除确认回调");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "取消后应返回父界面");
    }
}
