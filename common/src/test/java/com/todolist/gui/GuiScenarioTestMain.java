package com.todolist.gui;

import com.todolist.client.TodoHudRenderer;
import com.todolist.gui.testsupport.ClassFileMethodScanner;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.List;

/**
 * GUI 基础场景离线自测入口：补充真实事件路径的交互场景（坐标级按钮点击、
 * 键盘输入框输入），以及渲染入口守卫（双 blur 调用结构、渲染方法引用存在性、
 * 文字颜色 alpha 完整性），覆盖 1.21.6+ 实机出现过的渲染期缺陷类别
 * （Can only blur once per frame 崩溃、渲染 API 移除导致的
 * NoSuchMethodError、颜色漏写 alpha 导致的文字不渲染）。
 */
public final class GuiScenarioTestMain {

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private GuiScenarioTestMain() {
    }

    /**
     * 程序入口，串行执行基础场景与渲染守卫用例。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("GuiScenarioTestMain.shouldClickConfigButtonsThroughMousePath",
                GuiScenarioTestMain::shouldClickConfigButtonsThroughMousePath);
        GuiTestSupport.runTestCase("GuiScenarioTestMain.shouldTypeIntoConfigEditBoxThroughKeyboardPath",
                GuiScenarioTestMain::shouldTypeIntoConfigEditBoxThroughKeyboardPath);
        GuiTestSupport.runTestCase("GuiScenarioTestMain.shouldGuardScreensAgainstDoubleBlurBackground",
                GuiScenarioTestMain::shouldGuardScreensAgainstDoubleBlurBackground);
        GuiTestSupport.runTestCase("GuiScenarioTestMain.shouldGuardRenderEntryMethodReferences",
                GuiScenarioTestMain::shouldGuardRenderEntryMethodReferences);
        GuiTestSupport.runTestCase("GuiScenarioTestMain.shouldGuardTextColorsCarryAlpha",
                GuiScenarioTestMain::shouldGuardTextColorsCarryAlpha);
    }

    /**
     * 场景：通过坐标级鼠标点击/释放路径操作配置页按钮，
     * 断言按钮状态真实翻转（区别于直接调用 onPress 的单元级断言）。
     */
    private static void shouldClickConfigButtonsThroughMousePath() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        GuiTestSupport.initScreen(minecraft, screen, 520, 300);

        boolean visibleBefore = screen.isHudVisibleValueForTest();
        boolean autoSaveBefore = screen.isAutoSaveValueForTest();

        clickAt(screen, screen.getHudVisibilityButtonForTest());
        clickAt(screen, screen.getAutoSaveButtonForTest());

        GuiTestSupport.assertFalse(visibleBefore == screen.isHudVisibleValueForTest(),
                "坐标点击 HUD 可见性按钮后应切换可见状态");
        GuiTestSupport.assertFalse(autoSaveBefore == screen.isAutoSaveValueForTest(),
                "坐标点击 GUI 自动保存按钮后应切换开关状态");
    }

    /**
     * 场景：点击聚焦输入框后走键盘输入路径（charTyped 追加、BACKSPACE 删除），
     * 断言输入框内容随键盘事件变化（区别于直接 setValue 的单元级断言）。
     */
    private static void shouldTypeIntoConfigEditBoxThroughKeyboardPath() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        ConfigScreen screen = new ConfigScreen(parent);
        GuiTestSupport.initScreen(minecraft, screen, 520, 300);

        EditBox field = screen.getHudWidthFieldForTest();
        int fieldX = field.getX() + 4;
        int fieldY = field.getY() + field.getHeight() / 2;
        screen.mouseClicked(GuiTestSupport.mouseEvent(fieldX, fieldY), false);
        GuiTestSupport.assertTrue(field.isFocused(), "点击输入框后应获得焦点");

        field.setValue("");
        typeText(screen, "18");
        GuiTestSupport.assertEquals("18", field.getValue(), "键盘输入路径应把字符追加到输入框");

        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        GuiTestSupport.assertEquals("1", field.getValue(), "退格键应删除末尾字符");

        typeText(screen, "80");
        GuiTestSupport.assertEquals("180", field.getValue(), "追加输入应保留已有内容");

        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        GuiTestSupport.assertEquals("180", field.getValue(), "回车不应破坏输入框内容");
    }

    /**
     * 渲染守卫：检测运行时框架层 Screen.renderWithTooltip 是否已内置背景渲染
     * （1.21.6+ 行为），并据此断言各 GUI 屏幕的 render 方法不得再次调用
     * renderBackground，防止同一帧双 blur 触发
     * "Can only blur once per frame" 崩溃；框架不渲染背景的版本则要求
     * ConfigScreen.render 保留自行调用，避免背景缺失回归。
     */
    private static void shouldGuardScreensAgainstDoubleBlurBackground() {
        boolean frameworkRendersBackground = ClassFileMethodScanner
                .findInvokes(Screen.class, "renderWithTooltipAndSubtitles").stream()
                .anyMatch(ref -> "renderBackground".equals(ref.name()));

        boolean configCallsRenderBackground = ClassFileMethodScanner
                .findInvokes(ConfigScreen.class, "render").stream()
                .anyMatch(ref -> "renderBackground".equals(ref.name()));
        if (frameworkRendersBackground) {
            GuiTestSupport.assertFalse(configCallsRenderBackground,
                    "框架层已渲染背景时 ConfigScreen.render 不得再调用 renderBackground（双 blur 崩溃）");
        } else {
            GuiTestSupport.assertTrue(configCallsRenderBackground,
                    "框架层不渲染背景时 ConfigScreen.render 必须自行调用 renderBackground（保持背景）");
        }

        List<Class<?>> overlayBackgroundScreens = List.of(
                AddProjectScreen.class,
                ConfirmDeleteProjectScreen.class,
                ProjectSettingsScreen.class,
                AddMemberScreen.class,
                ConfirmActionScreen.class,
                TodoScreen.class);
        for (Class<?> screenClass : overlayBackgroundScreens) {
            boolean callsRenderBackground = ClassFileMethodScanner
                    .findInvokes(screenClass, "render").stream()
                    .anyMatch(ref -> "renderBackground".equals(ref.name()));
            GuiTestSupport.assertFalse(callsRenderBackground,
                    screenClass.getSimpleName() + ".render 不得调用 renderBackground（其背景由 override 自绘）");
        }
    }

    /**
     * 渲染守卫：扫描 HUD 渲染器与全部 GUI 屏幕的字节码方法引用，
     * 断言被引用的非 JDK 类与方法在运行时 classpath 中真实存在，
     * 捕获渲染 API 被移除（如 1.21.6 移除 RenderSystem.enableBlend）导致的
     * 运行期 NoSuchMethodError/崩溃类别。
     */
    private static void shouldGuardRenderEntryMethodReferences() {
        List<Class<?>> renderEntryClasses = List.of(
                TodoHudRenderer.class,
                ConfigScreen.class,
                AddProjectScreen.class,
                ConfirmDeleteProjectScreen.class,
                ProjectSettingsScreen.class,
                AddMemberScreen.class,
                ConfirmActionScreen.class,
                TodoScreen.class);
        for (Class<?> entryClass : renderEntryClasses) {
            assertMethodReferencesExist(entryClass);
        }
    }

    /**
     * 渲染守卫：检测运行时框架层 GuiGraphics.drawString 是否带 alpha==0 早退语义
     * （1.21.6+ 行为），并据此断言本 mod 渲染类字节码中不存在无 alpha 的灰白
     * 颜色字面量，防止 1.21.6 起 "颜色字面量漏写 alpha 导致文字不渲染" 的
     * 回归（实机表现为配置项标签/预览文字空白）；框架无早退语义的旧版本跳过
     * 检查（旧管线对 RGB 颜色默认补齐 alpha=255）。
     */
    private static void shouldGuardTextColorsCarryAlpha() {
        boolean frameworkSkipsAlphaZero = ClassFileMethodScanner
                .findInvokes(GuiGraphics.class, "drawString").stream()
                .anyMatch(ref -> "net/minecraft/util/ARGB".equals(ref.owner())
                        && "alpha".equals(ref.name()));
        if (!frameworkSkipsAlphaZero) {
            System.out.println("[GUI][SCENARIO] 框架 drawString 无 alpha==0 早退语义，无 alpha 颜色字面量检查跳过");
            return;
        }
        List<Class<?>> renderClasses = List.of(
                TodoHudRenderer.class,
                ConfigScreen.class,
                TodoScreen.class,
                ProjectSettingsScreen.class,
                AddProjectScreen.class,
                AddMemberScreen.class,
                ConfirmActionScreen.class,
                ConfirmDeleteProjectScreen.class,
                TaskListWidget.class,
                ProjectListWidget.class);
        for (Class<?> renderClass : renderClasses) {
            List<Integer> offenders = ClassFileMethodScanner.findIntConstants(renderClass, null).stream()
                    .filter(GuiScenarioTestMain::isLikelyOpaquelessGrayColor)
                    .distinct()
                    .toList();
            if (!offenders.isEmpty()) {
                throw new AssertionError(renderClass.getSimpleName()
                        + " 存在无 alpha 的灰白颜色字面量（1.21.6 起 drawString 会直接跳过渲染）: "
                        + offenders.stream().map(v -> String.format("0x%06X", v)).toList());
            }
        }
    }

    /**
     * 判断整数值是否形如无 alpha 的灰白颜色字面量（R==G==B 且值域在
     * 0x101010~0xFFFFFF 之间），用于防回归扫描；小值域与坐标尺寸常量
     * 均不满足该形态，误报风险低。
     *
     * @param value 整数常量值
     * @return 是否疑似无 alpha 颜色
     */
    private static boolean isLikelyOpaquelessGrayColor(int value) {
        if (value < 0x101010 || value >= 0x1000000) {
            return false;
        }
        int r = (value >> 16) & 0xFF;
        int g = (value >> 8) & 0xFF;
        int b = value & 0xFF;
        return r == g && g == b;
    }

    /**
     * 在屏幕坐标上模拟一次完整的鼠标点击（按下 + 释放）。
     *
     * @param screen 目标界面
     * @param button 目标按钮
     */
    private static void clickAt(Screen screen, Button button) {
        int x = button.getX() + Math.max(1, button.getWidth() / 2);
        int y = button.getY() + Math.max(1, button.getHeight() / 2);
        screen.mouseClicked(GuiTestSupport.mouseEvent(x, y), false);
        screen.mouseReleased(GuiTestSupport.mouseEvent(x, y));
    }

    /**
     * 通过 charTyped 事件路径逐字符输入文本。
     *
     * @param screen 目标界面
     * @param text 文本内容
     */
    private static void typeText(Screen screen, String text) {
        for (int i = 0; i < text.length(); i++) {
            screen.charTyped(new CharacterEvent(text.charAt(i), 0));
        }
    }

    /**
     * 断言目标类字节码中的全部方法引用（非 JDK 部分）在运行时可解析。
     *
     * @param entryClass 渲染入口类
     */
    private static void assertMethodReferencesExist(Class<?> entryClass) {
        List<ClassFileMethodScanner.MethodRef> refs = ClassFileMethodScanner.findAllInvokes(entryClass);
        GuiTestSupport.assertTrue(refs.size() > 10,
                entryClass.getSimpleName() + " 的字节码应能扫描到足够数量的方法引用");
        for (ClassFileMethodScanner.MethodRef ref : refs) {
            String fqn = ref.owner().replace('/', '.');
            if (fqn.startsWith("java.") || fqn.startsWith("javax.")
                    || fqn.startsWith("jdk.") || fqn.startsWith("sun.")) {
                continue;
            }
            Class<?> ownerClass;
            try {
                ownerClass = Class.forName(fqn, false, entryClass.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new AssertionError(entryClass.getSimpleName() + " 渲染路径引用的类不存在: "
                        + fqn + "#" + ref.name(), e);
            }
            if (!hasMethodOrConstructorNamed(ownerClass, ref.name())) {
                throw new AssertionError(entryClass.getSimpleName() + " 渲染路径引用的方法不存在: "
                        + fqn + "#" + ref.name() + "（疑似 API 已移除或变更）");
            }
        }
    }

    /**
     * 判断类及其超类/接口层级中是否存在指定名称的方法或构造器。
     *
     * @param clazz 目标类
     * @param name 方法名（含 &lt;init&gt; 构造器名）
     * @return 存在时返回 true
     */
    private static boolean hasMethodOrConstructorNamed(Class<?> clazz, String name) {
        // Constructor.getName() 返回的是声明类简单名而非 "<init>"，
        // 因此构造器引用只需验证目标类层级上存在至少一个构造器即可。
        boolean constructorRef = "<init>".equals(name);
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return true;
                }
            }
            if (constructorRef && c.getDeclaredConstructors().length > 0) {
                return true;
            }
            for (Class<?> iface : c.getInterfaces()) {
                if (hasMethodOrConstructorNamed(iface, name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
