package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import net.minecraft.client.gui.screens.Screen;

/**
 * 配置界面离线自测入口：覆盖配置布局、滚动、保存取消和预览拖拽。
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
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldBuildTwoColumnConfigRows", ConfigScreenTestMain::shouldBuildTwoColumnConfigRows);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldScrollWhenConfigRowsOverflow", ConfigScreenTestMain::shouldScrollWhenConfigRowsOverflow);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldToggleHudVisibilityAndProjectSource", ConfigScreenTestMain::shouldToggleHudVisibilityAndProjectSource);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldCancelWithoutPersistingConfig", ConfigScreenTestMain::shouldCancelWithoutPersistingConfig);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldSyncHudPreviewPositionWithDraft", ConfigScreenTestMain::shouldSyncHudPreviewPositionWithDraft);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldPersistHudSourceAndVisibilityTogether", ConfigScreenTestMain::shouldPersistHudSourceAndVisibilityTogether);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldPersistSafeValuesOnSave", ConfigScreenTestMain::shouldPersistSafeValuesOnSave);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldEnableCustomPreviewAfterDragAndSave", ConfigScreenTestMain::shouldEnableCustomPreviewAfterDragAndSave);
    }

    /**
     * 验证宽窗口下配置页使用双列布局，并将预览区固定在右侧。
     */
    private static void shouldBuildTwoColumnConfigRows() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);

        GuiTestSupport.initScreen(minecraft, screen, 520, 300);

        GuiTestSupport.assertEquals(2, screen.getConfigColumnCountForTest(), "宽窗口下配置页应使用双列布局");
        int[] configListBounds = screen.getConfigListBoundsForTest();
        int[] previewBounds = screen.getPreviewPanelBoundsForTest();
        GuiTestSupport.assertTrue(configListBounds[0] + configListBounds[2] <= previewBounds[0], "双列配置区应位于固定预览区左侧");
        GuiTestSupport.assertTrue(screen.getSaveButtonForTest().getY() >= configListBounds[1], "底部按钮应落在配置区下方");
    }

    /**
     * 验证配置项超出可见高度时只滚动配置区，并保持滚动偏移不越界。
     */
    private static void shouldScrollWhenConfigRowsOverflow() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);

        GuiTestSupport.initScreen(minecraft, screen, 360, 220);

        GuiTestSupport.assertTrue(screen.isConfigListOverflowingForTest(), "小窗口下配置项应超出可见高度并进入滚动模式");
        int[] previewBoundsBefore = screen.getPreviewPanelBoundsForTest();
        double listCenterX = screen.getConfigListCenterXForTest();
        double listCenterY = screen.getConfigListCenterYForTest();
        for (int i = 0; i < 6; i++) {
            screen.mouseScrolled(listCenterX, listCenterY, -1.0D);
        }

        GuiTestSupport.assertTrue(screen.getConfigListScrollOffsetForTest() > 0, "滚动配置区后应产生正向滚动偏移");
        GuiTestSupport.assertTrue(screen.getConfigListScrollOffsetForTest() <= screen.getConfigListMaxScrollOffsetForTest(), "滚动偏移不应超过配置区允许范围");
        GuiTestSupport.assertEquals(previewBoundsBefore[0], screen.getPreviewPanelBoundsForTest()[0], "滚动配置区时预览区不应跟随滚动");
    }

    /**
     * 验证 HUD 显示和项目来源按钮会更新界面草稿状态。
     */
    private static void shouldToggleHudVisibilityAndProjectSource() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);
        String sourceBefore = screen.getHudProjectSourceValueForTest();

        ScreenDriver.click(screen.getHudVisibilityButtonForTest());
        ScreenDriver.click(screen.getHudProjectSourceButtonForTest());

        GuiTestSupport.assertFalse(screen.isHudVisibleValueForTest(), "点击 HUD 显示按钮后应切换为隐藏");
        GuiTestSupport.assertFalse(sourceBefore.equals(screen.getHudProjectSourceValueForTest()), "点击项目来源按钮后应切换来源值");
    }

    /**
     * 验证拖动 HUD 预览块时会同步更新草稿中的预览位置。
     */
    private static void shouldSyncHudPreviewPositionWithDraft() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);
        int originalX = screen.getPreviewHudXForTest();
        int originalY = screen.getPreviewHudYForTest();

        ScreenDriver.dragPreview(screen, originalX + 36, originalY + 24);

        GuiTestSupport.assertTrue(screen.isPreviewUseCustomForTest(), "拖动 HUD 预览后应切换到自定义预览位置");
        GuiTestSupport.assertTrue(screen.getDraftPreviewHudXForTest() != originalX || screen.getDraftPreviewHudYForTest() != originalY,
                "拖动 HUD 预览后应同步更新草稿中的预览坐标");
    }

    /**
     * 验证保存时会同时持久化 HUD 可见性和项目来源配置。
     */
    private static void shouldPersistHudSourceAndVisibilityTogether() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        ModConfig config = ModConfig.getInstance();
        config.setHudProjectSource("ALL");
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);

        if (screen.isHudVisibleValueForTest()) {
            ScreenDriver.click(screen.getHudVisibilityButtonForTest());
        }
        ScreenDriver.click(screen.getHudProjectSourceButtonForTest());
        String expectedSource = screen.getHudProjectSourceValueForTest();
        ScreenDriver.click(screen.getSaveButtonForTest());

        GuiTestSupport.assertEquals(expectedSource, ModConfig.getInstance().getHudProjectSource(), "保存后应持久化 HUD 项目来源");
        GuiTestSupport.assertFalse(ops.isHudVisible(), "保存后应同步桥接层中的 HUD 可见状态");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "保存配置后应返回父界面");
    }

    /**
     * 验证取消按钮只返回父界面，不会持久化修改后的配置。
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
     * 验证保存时非法数字输入会回退到原有安全值。
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
        GuiTestSupport.assertFalse(ops.isHudVisible(), "保存后应保留当前 HUD 可见状态");
        GuiTestSupport.assertTrue(ops.getHudVisibilityCalls().size() >= 1, "保存流程应向桥接层同步 HUD 可见状态");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "保存后应返回父界面");
    }

    /**
     * 验证拖拽 HUD 预览会启用自定义定位，并在保存后写回配置。
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
