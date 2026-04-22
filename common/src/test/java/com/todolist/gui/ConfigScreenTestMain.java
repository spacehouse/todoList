package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.client.ClientTaskStorageHelper;
import com.todolist.client.TodoHudRenderer;
import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.task.Task;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置界面离线自测入口，覆盖旧版配置页的表单、保存和预览拖拽流程。
 */
public final class ConfigScreenTestMain {

    /**
     * 工具类不需要实例化。
     */
    private ConfigScreenTestMain() {
    }

    /**
     * 串行执行配置界面相关 GUI 回归用例。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldRenderClassicFormWithinScreen", ConfigScreenTestMain::shouldRenderClassicFormWithinScreen);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldToggleHudVisibilityAndProjectSource", ConfigScreenTestMain::shouldToggleHudVisibilityAndProjectSource);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldCancelWithoutPersistingConfig", ConfigScreenTestMain::shouldCancelWithoutPersistingConfig);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldUpdatePreviewPositionAfterDrag", ConfigScreenTestMain::shouldUpdatePreviewPositionAfterDrag);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldPersistHudSourceAndVisibilityTogether", ConfigScreenTestMain::shouldPersistHudSourceAndVisibilityTogether);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldPersistSafeValuesOnSave", ConfigScreenTestMain::shouldPersistSafeValuesOnSave);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldEnableCustomPreviewAfterDragAndSave", ConfigScreenTestMain::shouldEnableCustomPreviewAfterDragAndSave);
        GuiTestSupport.runTestCase("ConfigScreenTestMain.shouldKeepFixedPreviewHeightWhenActualHudIsTall", ConfigScreenTestMain::shouldKeepFixedPreviewHeightWhenActualHudIsTall);
    }

    /**
     * 验证旧版表单布局和底部按钮都位于屏幕范围内。
     */
    private static void shouldRenderClassicFormWithinScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);

        GuiTestSupport.initScreen(minecraft, screen, 520, 300);

        GuiTestSupport.assertTrue(screen.getSaveButtonForTest().getX() >= 0, "保存按钮应位于屏幕内");
        GuiTestSupport.assertTrue(screen.getCancelButtonForTest().getX() >= 0, "取消按钮应位于屏幕内");
        GuiTestSupport.assertTrue(screen.getSaveButtonForTest().getY() >= 0, "保存按钮纵坐标应位于屏幕内");
        GuiTestSupport.assertTrue(screen.getCancelButtonForTest().getY() >= 0, "取消按钮纵坐标应位于屏幕内");
        GuiTestSupport.assertTrue(screen.getSaveButtonForTest().getX() < screen.getCancelButtonForTest().getX(), "旧版配置页中保存按钮应位于取消按钮左侧");
        GuiTestSupport.assertTrue(screen.getPreviewHudXForTest() >= 0, "预览 HUD 应位于屏幕内");
        GuiTestSupport.assertTrue(screen.getPreviewHudYForTest() >= 0, "预览 HUD 应位于屏幕内");
        GuiTestSupport.assertTrue(screen.getPreviewHudXForTest() + screen.getPreviewHudWidthForTest() <= 520, "预览 HUD 不应超出右边界");
        GuiTestSupport.assertTrue(screen.getPreviewHudYForTest() + screen.getPreviewHudHeightForTest() <= 300, "预览 HUD 不应超出下边界");
    }

    /**
     * 验证 HUD 可见性和项目来源按钮会更新旧版表单状态。
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

        GuiTestSupport.assertFalse(screen.isHudVisibleValueForTest(), "点击 HUD 可见性按钮后应切换为隐藏");
        GuiTestSupport.assertFalse(sourceBefore.equals(screen.getHudProjectSourceValueForTest()), "点击项目来源按钮后应切换来源值");
    }

    /**
     * 验证取消只返回父界面，不持久化修改内容。
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

        GuiTestSupport.assertEquals(240, ModConfig.getInstance().getHudWidth(), "点击取消后不应持久化 HUD 宽度");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "点击取消后应返回父界面");
    }

    /**
     * 验证拖拽旧版 HUD 预览时会立即更新预览位置。
     */
    private static void shouldUpdatePreviewPositionAfterDrag() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);
        int originalX = screen.getPreviewHudXForTest();
        int originalY = screen.getPreviewHudYForTest();

        ScreenDriver.dragPreview(screen, originalX + 36, originalY + 24);

        GuiTestSupport.assertTrue(screen.isPreviewUseCustomForTest(), "拖拽预览后应切换到自定义定位");
        GuiTestSupport.assertTrue(screen.getPreviewHudXForTest() != originalX || screen.getPreviewHudYForTest() != originalY,
                "拖拽预览后应更新当前预览坐标");
    }

    /**
     * 验证保存会同步 HUD 可见性和项目来源配置。
     */
    private static void shouldPersistHudSourceAndVisibilityTogether() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        ModConfig.getInstance().setHudProjectSource("ALL");
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

        GuiTestSupport.assertEquals(expectedSource, ModConfig.getInstance().getHudProjectSource(), "保存后应写回 HUD 项目来源");
        GuiTestSupport.assertFalse(ops.isHudVisible(), "保存后应同步桥接层中的 HUD 可见状态");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "保存后应返回父界面");
    }

    /**
     * 验证非法输入保存时会回退到原有安全值。
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
        GuiTestSupport.assertFalse(ops.isHudVisible(), "保存后应保持当前 HUD 可见状态");
        GuiTestSupport.assertTrue(ops.getHudVisibilityCalls().size() >= 1, "保存流程应同步 HUD 可见状态");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "保存后应返回父界面");
    }

    /**
     * 验证拖拽预览后保存，会把自定义定位写回配置。
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
                "保存后应写回新的 HUD 坐标");
    }

    /**
     * 验证即使实际 HUD 面板高度较大，配置页预览框仍保持固定高度，便于拖拽定位。
     */
    private static void shouldKeepFixedPreviewHeightWhenActualHudIsTall() {
        GuiTestSupport.resetState();
        ModConfig config = ModConfig.getInstance();
        config.setHudTodoLimit(10);
        config.setHudDoneLimit(7);
        config.setHudMaxHeight(400);
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < 18; index++) {
            tasks.add(new Task("Tall Preview " + index, ""));
        }
        try {
            ClientTaskStorageHelper.savePersonalTasks(TodoListCommon.getTaskStorage(), minecraft, tasks);
        } catch (Exception exception) {
            throw new AssertionError("写入个人任务测试数据失败", exception);
        }

        TodoHudRenderer renderer = new TodoHudRenderer(minecraft);
        ClientPlatformAdapter.setHudRendererSupplier(() -> renderer);

        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertEquals(60, screen.getPreviewHudHeightForTest(), "配置页预览框高度应保持固定值，不跟随实际 HUD 高度变化");
    }
}
