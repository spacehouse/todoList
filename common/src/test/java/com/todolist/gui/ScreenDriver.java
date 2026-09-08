package com.todolist.gui;

import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 测试驱动器：统一封装 Screen 初始化、按钮点击、输入和拖拽等常用测试操作。
 */
public final class ScreenDriver {
    /**
     * GUI 测试默认宽度。
     */
    public static final int DEFAULT_WIDTH = 320;

    /**
     * GUI 测试默认高度。
     */
    public static final int DEFAULT_HEIGHT = 240;

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ScreenDriver() {
    }

    /**
     * 初始化指定 Screen，使用统一的默认测试尺寸。
     *
     * @param minecraft 假客户端
     * @param screen 待初始化界面
     */
    public static void init(FakeMinecraftClient minecraft, Screen screen) {
        GuiTestSupport.initScreen(minecraft, screen, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * 创建一个最小父界面，供弹窗和子界面测试返回跳转使用。
     *
     * @param title 父界面标题
     * @return 最小父界面
     */
    public static Screen createParentScreen(String title) {
        return new Screen(Component.literal(title)) {
            /**
             * 最小测试父界面无需额外初始化组件。
             */
            @Override
            protected void init() {
            }
        };
    }

    /**
     * 直接触发按钮点击。
     *
     * @param button 目标按钮
     */
    public static void click(Button button) {
        if (button != null) {
            button.onPress(new KeyEvent(0, 0, 0));
        }
    }

    /**
     * 直接向输入框写入文本。
     *
     * @param editBox 目标输入框
     * @param value 文本内容
     */
    public static void setText(EditBox editBox, String value) {
        if (editBox != null) {
            editBox.setValue(value == null ? "" : value);
        }
    }

    /**
     * 向界面发送回车键，供创建类界面测试快捷键流程。
     *
     * @param screen 目标界面
     */
    public static void pressEnter(Screen screen) {
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
    }

    /**
     * 同步预览矩形并模拟一次 HUD 预览拖拽。
     *
     * @param screen 配置界面
     * @param endX 拖拽终点 X
     * @param endY 拖拽终点 Y
     */
    public static void dragPreview(ConfigScreen screen, int endX, int endY) {
        screen.syncPreviewRectForTest();
        int startX = screen.getPreviewHudXForTest() + Math.max(1, screen.getPreviewHudWidthForTest() / 2);
        int startY = screen.getPreviewHudYForTest() + Math.max(1, screen.getPreviewHudHeightForTest() / 2);
        screen.mouseClicked(GuiTestSupport.mouseEvent(startX, startY), false);
        screen.mouseDragged(GuiTestSupport.mouseEvent(endX, endY), endX - startX, endY - startY);
        screen.mouseReleased(GuiTestSupport.mouseEvent(endX, endY));
    }

    /**
     * 获取界面上注册的全部按钮，供弹窗类测试按顺序读取。
     *
     * @param screen 目标界面
     * @return 按钮列表
     */
    public static List<Button> getButtons(Screen screen) {
        List<Button> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button) {
                buttons.add(button);
            }
        }
        return buttons;
    }
}
