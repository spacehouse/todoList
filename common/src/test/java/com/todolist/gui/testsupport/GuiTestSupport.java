package com.todolist.gui.testsupport;

import com.mojang.authlib.GameProfile;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.config.ModConfig;
import com.todolist.gui.TodoScreen;
import com.todolist.platform.DataPathProvider;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.InputType;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * GUI 离线测试支撑工具：负责初始化临时环境、创建假客户端并提供基础断言能力。
 */
public final class GuiTestSupport {
    private static final Unsafe UNSAFE = loadUnsafe();
    private static final Path TEST_GAME_DIR = createTestGameDir();
    private static boolean bootstrapped;

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private GuiTestSupport() {
    }

    /**
     * 通过反射写入对象的 double 字段，供 HUD 窗口缩放等测试场景复用。
     *
     * @param owner 字段声明类
     * @param target 目标对象
     * @param fieldName 字段名
     * @param value 字段值
     */
    public static void setDoubleField(Class<?> owner, Object target, String fieldName, double value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            long offset = UNSAFE.objectFieldOffset(field);
            UNSAFE.putDouble(target, offset, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入 double 字段: " + fieldName, e);
        }
    }

    /**
     * 初始化 GUI 测试运行环境，确保数据目录和 Minecraft 静态常量可在离线模式下使用。
     */
    public static synchronized void bootstrapEnvironment() {
        if (bootstrapped) {
            return;
        }
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
        DataPathProvider.setGameDirSupplier(() -> TEST_GAME_DIR);
        bootstrapped = true;
    }

    /**
     * 重置单次 GUI 测试需要的全局状态，并返回新的记录型客户端桥接实现。
     *
     * @return 本轮测试使用的记录型客户端桥接实现
     */
    public static RecordingClientOps resetState() {
        bootstrapEnvironment();
        cleanTestGameDir();
        DataPathProvider.setGameDirSupplier(() -> TEST_GAME_DIR);
        DataPathProvider.resetStorageNamespace();
        setStaticObjectField(ModConfig.class, "instance", null);
        invokeTodoScreenReset();
        TodoListCommon.init();
        TodoListCommon.setProjectSyncInProgress(false);
        ClientPlatformAdapter.setHudRendererSupplier(() -> null);
        RecordingClientOps ops = new RecordingClientOps();
        ClientBridge.setOps(ops);
        return ops;
    }

    /**
     * 创建带默认玩家的假客户端，供 GUI Screen 初始化和事件驱动测试使用。
     *
     * @return 假客户端实例
     */
    public static FakeMinecraftClient createMinecraft() {
        return createMinecraft(UUID.fromString("00000000-0000-0000-0000-000000000001"), "gui-tester", false);
    }

    /**
     * 创建带指定玩家身份的假客户端，供权限和成员相关场景测试使用。
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     * @param operator 是否具备管理员权限
     * @return 假客户端实例
     */
    public static FakeMinecraftClient createMinecraft(UUID playerUuid, String playerName, boolean operator) {
        FakeMinecraftClient minecraft = allocate(FakeMinecraftClient.class);
        FakeFont font = new FakeFont();
        FakeClientPlayer player = FakeClientPlayer.create(playerUuid, playerName, operator);
        FakeClientConnection connection = FakeClientConnection.create();
        FakeGameNarrator narrator = FakeGameNarrator.create();
        FakeSoundManager soundManager = FakeSoundManager.create();
        Window window = allocate(Window.class);
        Options options = allocate(Options.class);
        setIntField(Window.class, window, "width", 320);
        setIntField(Window.class, window, "height", 240);
        setIntField(Window.class, window, "guiScaledWidth", 320);
        setIntField(Window.class, window, "guiScaledHeight", 240);
        setDoubleField(Window.class, window, "guiScale", 1.0D);
        options.hideGui = false;
        setObjectField(Minecraft.class, minecraft, "font", font);
        setObjectField(Minecraft.class, minecraft, "player", player);
        setObjectField(Minecraft.class, minecraft, "window", window);
        setObjectField(Minecraft.class, minecraft, "options", options);
        setObjectField(Minecraft.class, minecraft, "soundManager", soundManager);
        setStaticObjectField(Minecraft.class, "instance", minecraft);
        minecraft.setTestFont(font);
        minecraft.setTestPlayer(player);
        minecraft.setTestConnection(connection);
        minecraft.setTestNarrator(narrator);
        minecraft.setTestSoundManager(soundManager);
        minecraft.setTestWindow(window);
        minecraft.setTestOptions(options);
        minecraft.setTestLastInputType(InputType.KEYBOARD_TAB);
        return minecraft;
    }

    /**
     * 初始化指定 Screen，模拟 Minecraft 对界面的尺寸注入。
     *
     * @param minecraft 假客户端
     * @param screen 待初始化的界面
     * @param width 界面宽度
     * @param height 界面高度
     */
    public static void initScreen(FakeMinecraftClient minecraft, Screen screen, int width, int height) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(screen, "screen");
        if (minecraft.getLastInputType() == null) {
            minecraft.setTestLastInputType(InputType.KEYBOARD_TAB);
        }
        screen.init(minecraft, width, height);
    }

    /**
     * 创建测试专用玩家信息对象，供在线成员列表相关界面使用。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @return 测试玩家信息对象
     */
    public static FakePlayerInfo createPlayerInfo(UUID uuid, String name) {
        return new FakePlayerInfo(new GameProfile(uuid, name));
    }

    /**
     * 断言两个对象相等，不相等时抛出带说明的异常。
     *
     * @param expected 期望值
     * @param actual 实际值
     * @param message 失败提示
     */
    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    /**
     * 断言条件为真，不满足时抛出带说明的异常。
     *
     * @param condition 待断言条件
     * @param message 失败提示
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言条件为假，不满足时抛出带说明的异常。
     *
     * @param condition 待断言条件
     * @param message 失败提示
     */
    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言对象非空，不满足时抛出带说明的异常。
     *
     * @param value 待断言对象
     * @param message 失败提示
     */
    public static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言对象为空，不满足时抛出带说明的异常。
     *
     * @param value 待断言对象
     * @param message 失败提示
     */
    public static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " actual=" + value);
        }
    }

    /**
     * 通过反射读取对象字段，供测试访问私有内部状态。
     *
     * @param owner 字段声明类
     * @param target 目标对象
     * @param fieldName 字段名
     * @return 字段当前值
     */
    /**
     * 运行具名 GUI 测试组，并输出清晰的开始、通过和失败日志。
     *
     * @param groupName 测试组名称
     * @param action 测试组执行逻辑
     */
    public static void runTestGroup(String groupName, Runnable action) {
        long startNs = System.nanoTime();
        System.out.println("[GUI][GROUP][RUN ] " + groupName);
        try {
            action.run();
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[GUI][GROUP][PASS] " + groupName + " (" + durationMs + " ms)");
        } catch (Throwable throwable) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[GUI][GROUP][FAIL] " + groupName + " (" + durationMs + " ms)");
            throw new IllegalStateException("GUI 测试组失败: " + groupName, throwable);
        }
    }

    /**
     * 运行具名 GUI 测试用例，并在失败时补充明确的用例名称。
     *
     * @param caseName 测试用例名称
     * @param action 测试用例执行逻辑
     */
    public static void runTestCase(String caseName, Runnable action) {
        long startNs = System.nanoTime();
        System.out.println("[GUI][CASE ][RUN ] " + caseName);
        try {
            action.run();
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[GUI][CASE ][PASS] " + caseName + " (" + durationMs + " ms)");
        } catch (AssertionError assertionError) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[GUI][CASE ][FAIL] " + caseName + " (" + durationMs + " ms)");
            throw new AssertionError("GUI 测试用例失败: " + caseName + " -> " + assertionError.getMessage(), assertionError);
        } catch (Throwable throwable) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[GUI][CASE ][FAIL] " + caseName + " (" + durationMs + " ms)");
            throw new IllegalStateException("GUI 测试用例异常: " + caseName, throwable);
        }
    }

    public static Object readField(Class<?> owner, Object target, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法读取字段: " + fieldName, e);
        }
    }

    /**
     * 通过反射写入对象整数字段，供测试构造内部状态。
     *
     * @param owner 字段声明类
     * @param target 目标对象
     * @param fieldName 字段名
     * @param value 字段值
     */
    public static void setIntField(Class<?> owner, Object target, String fieldName, int value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            long offset = UNSAFE.objectFieldOffset(field);
            UNSAFE.putInt(target, offset, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入整数字段: " + fieldName, e);
        }
    }

    /**
     * 使用 Unsafe 无构造创建测试对象，避免真实客户端依赖。
     *
     * @param type 目标类型
     * @return 无构造创建的实例
     * @param <T> 泛型类型
     */
    public static <T> T allocate(Class<T> type) {
        try {
            return type.cast(UNSAFE.allocateInstance(type));
        } catch (InstantiationException e) {
            throw new IllegalStateException("无法创建测试实例: " + type.getName(), e);
        }
    }

    /**
     * 设置对象字段值，必要时使用 Unsafe 绕过 final 限制。
     *
     * @param owner 字段声明类
     * @param target 目标对象
     * @param fieldName 字段名
     * @param value 字段值
     */
    static void setObjectField(Class<?> owner, Object target, String fieldName, Object value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            long offset = UNSAFE.objectFieldOffset(field);
            UNSAFE.putObject(target, offset, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入对象字段: " + fieldName, e);
        }
    }

    /**
     * 设置静态字段值，必要时使用 Unsafe 绕过 final 限制。
     *
     * @param owner 字段声明类
     * @param fieldName 字段名
     * @param value 字段值
     */
    private static void setStaticObjectField(Class<?> owner, String fieldName, Object value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object base = UNSAFE.staticFieldBase(field);
            long offset = UNSAFE.staticFieldOffset(field);
            UNSAFE.putObject(base, offset, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入静态字段: " + fieldName, e);
        }
    }

    /**
     * 清理测试目录，保证每个 GUI 自测从干净的数据状态启动。
     */
    private static void cleanTestGameDir() {
        try {
            if (Files.exists(TEST_GAME_DIR)) {
                Files.walk(TEST_GAME_DIR)
                        .sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(TEST_GAME_DIR))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception e) {
                                throw new IllegalStateException("无法清理测试目录: " + path, e);
                            }
                        });
            }
            Files.createDirectories(TEST_GAME_DIR.resolve("config"));
        } catch (Exception e) {
            throw new IllegalStateException("无法重置 GUI 测试目录", e);
        }
    }

    /**
     * 创建 GUI 测试使用的临时游戏目录。
     *
     * @return 临时目录路径
     */
    private static Path createTestGameDir() {
        try {
            Path dir = Files.createTempDirectory("todolist-gui-tests");
            Files.createDirectories(dir.resolve("config"));
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建 GUI 测试目录", e);
        }
    }

    /**
     * 反射读取 Unsafe 单例，供假对象无构造创建与 final 字段写入使用。
     *
     * @return Unsafe 单例
     */
    private static Unsafe loadUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法访问 Unsafe", e);
        }
    }

    /**
     * 通过反射调用 TodoScreen 的测试重置入口，避免跨包访问限制。
     */
    private static void invokeTodoScreenReset() {
        try {
            java.lang.reflect.Method method = TodoScreen.class.getDeclaredMethod("resetGuiStateForTest");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法重置 TodoScreen 静态状态", e);
        }
    }

    /**
     * 极简字体实现：为离线 GUI 测试提供稳定的宽度和裁剪行为。
     */
    private static final class FakeFont extends Font {
        /**
         * 创建极简字体实现，测试中只依赖宽度计算和字符串裁剪能力。
         */
        private FakeFont() {
            super(id -> null, false);
        }

        /**
         * 返回字符串估算宽度，避免真实字体集依赖。
         *
         * @param text 待测文本
         * @return 文本估算宽度
         */
        @Override
        public int width(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        /**
         * 返回组件估算宽度，供按钮和标签布局使用。
         *
         * @param text 待测组件
         * @return 组件估算宽度
         */
        @Override
        public int width(FormattedText text) {
            return text == null ? 0 : width(text.getString());
        }

        /**
         * 返回格式化字符序列的估算宽度，供组件布局计算使用。
         *
         * @param text 格式化字符序列
         * @return 估算宽度
         */
        @Override
        public int width(FormattedCharSequence text) {
            if (text == null) {
                return 0;
            }
            final int[] count = new int[] { 0 };
            text.accept((index, style, codePoint) -> {
                count[0]++;
                return true;
            });
            return count[0] * 6;
        }

        /**
         * 按近似宽度截断字符串，供列表组件和输入框测试使用。
         *
         * @param text 原始文本
         * @param maxWidth 最大宽度
         * @return 裁剪后的文本
         */
        @Override
        public String plainSubstrByWidth(String text, int maxWidth) {
            return plainSubstrByWidth(text, maxWidth, false);
        }

        /**
         * 按近似宽度截断字符串，并兼容带方向参数的重载调用。
         *
         * @param text 原始文本
         * @param maxWidth 最大宽度
         * @param reverse 是否反向截断
         * @return 裁剪后的文本
         */
        @Override
        public String plainSubstrByWidth(String text, int maxWidth, boolean reverse) {
            if (text == null || maxWidth <= 0) {
                return "";
            }
            int maxChars = Math.max(0, maxWidth / 6);
            if (text.length() <= maxChars) {
                return text;
            }
            if (reverse) {
                return text.substring(text.length() - maxChars);
            }
            return text.substring(0, maxChars);
        }
    }
}
