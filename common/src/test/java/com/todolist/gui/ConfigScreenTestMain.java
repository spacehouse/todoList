package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import net.minecraft.client.gui.screens.Screen;

/**
 * 配置界面离线自测入口：覆盖 HUD 开关、项目来源切换、保存取消语义与预览拖拽。
 */
public final class ConfigScreenTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ConfigScreenTestMain() {
    }

    /**
     * 程序入口，串行执行配置界面的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldToggleHudVisibilityAndProjectSource", ConfigScreenTestMain::shouldToggleHudVisibilityAndProjectSource);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldCancelWithoutPersistingConfig", ConfigScreenTestMain::shouldCancelWithoutPersistingConfig);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldPersistSafeValuesOnSave", ConfigScreenTestMain::shouldPersistSafeValuesOnSave);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldEnableCustomPreviewAfterDragAndSave", ConfigScreenTestMain::shouldEnableCustomPreviewAfterDragAndSave);
    }

    /**
     * 校验 HUD 可见性与项目来源按钮会更新界面状态并向桥接层发起调用。
     */
    private static void shouldToggleHudVisibilityAndProjectSource() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);
        String sourceBefore = screen.getHudProjectSourceValueForTest();

        ScreenDriver.click(screen.getHudVisibilityButtonForTest());
        ScreenDriver.click(screen.getHudProjectSourceButtonForTest());

        GuiTestSupport.assertFalse(screen.isHudVisibleValueForTest(), "点击 HUD 可见性按钮后应切换为隐藏");
        GuiTestSupport.assertEquals(1, ops.getHudVisibilityCalls().size(), "点击 HUD 可见性按钮后应调用桥接层同步可见性");
        GuiTestSupport.assertFalse(sourceBefore.equals(screen.getHudProjectSourceValueForTest()), "点击项目来源按钮后应切换来源值");
    }

    /**
     * 校验取消按钮只返回父界面，不会持久化修改后的配置。
     */
    private static void shouldCancelWithoutPersistingConfig() {
        GuiTestSupport.resetState();
        ModConfig config = ModConfig.getInstance();
        config.setHudWidth(240);
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);

        ScreenDriver.setText(screen.getHudWidthFieldForTest(), "333");
        ScreenDriver.click(screen.getCancelButtonForTest());

        GuiTestSupport.assertEquals(240, ModConfig.getInstance().getHudWidth(), "取消按钮不应持久化宽度修改");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "取消后应返回父界面");
    }

    /**
     * 校验保存时非法数字输入会回退到原有安全值。
     */
    private static void shouldPersistSafeValuesOnSave() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        ModConfig config = ModConfig.getInstance();
        config.setHudWidth(220);
        config.setHudMaxHeight(360);
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);

        ScreenDriver.setText(screen.getHudWidthFieldForTest(), "bad-width");
        ScreenDriver.setText(screen.getHudMaxHeightFieldForTest(), "bad-height");
        if (screen.isHudVisibleValueForTest()) {
            ScreenDriver.click(screen.getHudVisibilityButtonForTest());
        }
        ScreenDriver.click(screen.getSaveButtonForTest());

        GuiTestSupport.assertEquals(220, ModConfig.getInstance().getHudWidth(), "非法 HUD 宽度应回退到原有安全值");
        GuiTestSupport.assertEquals(360, ModConfig.getInstance().getHudMaxHeight(), "非法 HUD 高度应回退到原有安全值");
        GuiTestSupport.assertFalse(ops.isHudVisible(), "保存后应保留当前 HUD 可见性状态");
        GuiTestSupport.assertTrue(ops.getHudVisibilityCalls().size() >= 2, "保存流程应再次向桥接层同步 HUD 可见性");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "保存后应返回父界面");
    }

    /**
     * 校验拖拽 HUD 预览会启用自定义定位，并在保存后写回配置。
     */
    private static void shouldEnableCustomPreviewAfterDragAndSave() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);
        int originalX = screen.getPreviewHudXForTest();
        int originalY = screen.getPreviewHudYForTest();

        ScreenDriver.dragPreview(screen, originalX + 40, originalY + 30);
        ScreenDriver.click(screen.getSaveButtonForTest());

        GuiTestSupport.assertTrue(screen.isPreviewUseCustomForTest(), "拖拽 HUD 预览后应切换到自定义定位");
        GuiTestSupport.assertTrue(ModConfig.getInstance().isHudUseCustomPosition(), "保存后应写回自定义 HUD 定位开关");
        GuiTestSupport.assertTrue(ModConfig.getInstance().getHudCustomX() != originalX || ModConfig.getInstance().getHudCustomY() != originalY,
                "保存后应写回新的 HUD 预览坐标");
    }
}
