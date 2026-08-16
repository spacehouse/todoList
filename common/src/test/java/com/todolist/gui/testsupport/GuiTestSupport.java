package com.todolist.gui.testsupport;

import com.mojang.authlib.GameProfile;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.config.ModConfig;
import com.todolist.platform.DataPathProvider;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * GUI 测试公共工具类，负责环境初始化、断言辅助和测试对象构造。
 */
public final class GuiTestSupport {
    private static final Unsafe UNSAFE = loadUnsafe();
    private static final Path TEST_GAME_DIR = createTestGameDir();
    private static boolean bootstrapped;

    /**
     * 工具类不需要实例化。
     */
    private GuiTestSupport() {
    }

    /**
     * 使用 Unsafe 为目标对象写入 double 字段。
     *
     * @param owner 字段所属类型
     * @param target 目标对象
     * @param fieldName 字段名称
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
     * 初始化 GUI 测试运行环境。
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
     * 重置 GUI 测试状态并返回新的客户端操作记录器。
     * 默认将存储后端强制设为 NBT，因为绝大多数 GUI 测试验证的是界面交互行为而非 H2 持久化；
     * 如需测试默认存储后端选择，请使用 {@link #resetStateKeepDefaultBackend()}。
     *
     * @return 新的操作记录器
     */
    public static RecordingClientOps resetState() {
        RecordingClientOps ops = resetStateKeepDefaultBackend();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
        return ops;
    }

    /**
     * 重置 GUI 测试状态但不覆盖存储后端配置，保留 ModConfig 的默认后端（H2）。
     * 仅供存储后端选择相关测试使用。
     *
     * @return 新的操作记录器
     */
    public static RecordingClientOps resetStateKeepDefaultBackend() {
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
     * 创建默认的假 Minecraft 客户端。
     *
     * @return 假客户端实例
     */
    public static FakeMinecraftClient createMinecraft() {
        return createMinecraft(UUID.fromString("00000000-0000-0000-0000-000000000001"), "gui-tester", false);
    }

    /**
     * 按指定玩家信息创建假 Minecraft 客户端。
     *
     * @param playerUuid 玩家 UUID
     * @param playerName 玩家名称
     * @param operator 是否为管理员
     * @return 假客户端实例
     */
    public static FakeMinecraftClient createMinecraft(UUID playerUuid, String playerName, boolean operator) {
        FakeMinecraftClient minecraft = allocate(FakeMinecraftClient.class);
        FakeFont font = new FakeFont();
        FakeClientPlayer player = FakeClientPlayer.create(playerUuid, playerName, operator);
        FakeClientConnection connection = FakeClientConnection.create();
        FakeGameNarrator narrator = FakeGameNarrator.create();
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
        // 1.20.5+ 按钮键盘激活会播放音效，注入静默声音管理器避免触发真实声音引擎；
        // 必须用 Unsafe 分配，因为 SoundManager 构造器会初始化 SoundEngine 并加载 LWJGL 本地库
        setObjectField(Minecraft.class, minecraft, "soundManager", allocate(FakeSoundManager.class));
        minecraft.setTestFont(font);
        minecraft.setTestPlayer(player);
        minecraft.setTestConnection(connection);
        minecraft.setTestNarrator(narrator);
        minecraft.setTestWindow(window);
        minecraft.setTestOptions(options);
        return minecraft;
    }

    /**
     * 初始化指定测试界面。
     *
     * @param minecraft 假客户端
     * @param screen 目标界面
     * @param width 界面宽度
     * @param height 界面高度
     */
    public static void initScreen(FakeMinecraftClient minecraft, Screen screen, int width, int height) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(screen, "screen");
        // 1.20.5+ 的 Screen.init 会读取 lastInputType 判定初始焦点，测试环境需预设为键盘输入
        minecraft.setLastInputType(InputType.KEYBOARD_ARROW);
        screen.init(minecraft, width, height);
    }

    /**
     * 创建假的在线玩家信息。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @return 玩家信息对象
     */
    public static FakePlayerInfo createPlayerInfo(UUID uuid, String name) {
        return new FakePlayerInfo(new GameProfile(uuid, name));
    }

    /**
     * 断言两个值相等。
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
     * 断言条件为 true。
     *
     * @param condition 条件值
     * @param message 失败提示
     */
    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言条件为 false。
     *
     * @param condition 条件值
     * @param message 失败提示
     */
    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言对象不为 null。
     *
     * @param value 目标对象
     * @param message 失败提示
     */
    public static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言对象为 null。
     *
     * @param value 目标对象
     * @param message 失败提示
     */
    public static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " actual=" + value);
        }
    }

    /**
     * 运行一组 GUI 测试并输出分组日志。
     *
     * @param groupName 分组名称
     * @param action 测试动作
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
            throw new IllegalStateException("GUI 测试分组执行失败: " + groupName, throwable);
        }
    }

    /**
     * 运行单个 GUI 测试用例并输出日志。
     *
     * @param caseName 用例名称
     * @param action 测试动作
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
            throw new AssertionError("GUI 测试断言失败: " + caseName + " -> " + assertionError.getMessage(), assertionError);
        } catch (Throwable throwable) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            System.out.println("[GUI][CASE ][FAIL] " + caseName + " (" + durationMs + " ms)");
            throw new IllegalStateException("GUI 测试用例执行失败: " + caseName, throwable);
        }
    }

    /**
     * 通过反射读取字段值。
     *
     * @param owner 字段所属类型
     * @param target 目标对象
     * @param fieldName 字段名称
     * @return 字段值
     */
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
     * 使用 Unsafe 为目标对象写入 int 字段。
     *
     * @param owner 字段所属类型
     * @param target 目标对象
     * @param fieldName 字段名称
     * @param value 字段值
     */
    public static void setIntField(Class<?> owner, Object target, String fieldName, int value) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            long offset = UNSAFE.objectFieldOffset(field);
            UNSAFE.putInt(target, offset, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法写入 int 字段: " + fieldName, e);
        }
    }

    /**
     * 使用 Unsafe 创建未执行构造方法的实例。
     *
     * @param type 目标类型
     * @param <T> 类型参数
     * @return 新建实例
     */
    public static <T> T allocate(Class<T> type) {
        try {
            return type.cast(UNSAFE.allocateInstance(type));
        } catch (InstantiationException e) {
            throw new IllegalStateException("无法分配实例: " + type.getName(), e);
        }
    }

    /**
     * 使用 Unsafe 为目标对象写入引用字段。
     *
     * @param owner 字段所属类型
     * @param target 目标对象
     * @param fieldName 字段名称
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
     * 使用 Unsafe 写入静态对象字段。
     *
     * @param owner 字段所属类型
     * @param fieldName 字段名称
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
            throw new IllegalStateException("无法写入静态对象字段: " + fieldName, e);
        }
    }

    /**
     * 清空并重建 GUI 测试目录。
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
                                throw new IllegalStateException("无法清理 GUI 测试目录中的文件: " + path, e);
                            }
                        });
            }
            Files.createDirectories(TEST_GAME_DIR.resolve("config"));
        } catch (Exception e) {
            throw new IllegalStateException("无法重置 GUI 测试目录", e);
        }
    }

    /**
     * 创建 GUI 测试专用目录。
     *
     * @return 测试目录路径
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
     * 读取 Unsafe 实例。
     *
     * @return Unsafe 实例
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
     * 反射调用 TodoScreenTestAccess 的静态重置方法。
     */
    private static void invokeTodoScreenReset() {
        try {
            Class<?> accessClass = Class.forName("com.todolist.gui.TodoScreenTestAccess");
            java.lang.reflect.Method method = accessClass.getDeclaredMethod("resetGuiStateForTest");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法重置 TodoScreen 静态状态", e);
        }
    }

    /**
     * 静默按钮音效的声音管理器，避免测试环境触发真实声音引擎。
     */
    private static final class FakeSoundManager extends SoundManager {
        /**
         * 创建测试用声音管理器。
         */
        private FakeSoundManager() {
            super(null);
        }

        /**
         * 静默处理声音播放请求。
         *
         * @param sound 声音实例
         */
        @Override
        public void play(SoundInstance sound) {
            // 测试环境不播放任何声音
        }
    }

    /**
     * 简化 GUI 测试所需文本测量行为的字体实现。
     */
    private static final class FakeFont extends Font {
        /**
         * 创建测试字体实例。
         */
        private FakeFont() {
            super(id -> null, false);
        }

        /**
         * 计算普通字符串宽度。
         *
         * @param text 文本内容
         * @return 文本宽度
         */
        @Override
        public int width(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        /**
         * 计算 FormattedText 的宽度。
         *
         * @param text 文本内容
         * @return 文本宽度
         */
        @Override
        public int width(FormattedText text) {
            return text == null ? 0 : width(text.getString());
        }

        /**
         * 计算 FormattedCharSequence 的宽度。
         *
         * @param text 文本内容
         * @return 文本宽度
         */
        @Override
        public int width(FormattedCharSequence text) {
            if (text == null) {
                return 0;
            }
            final int[] count = new int[] {0};
            text.accept((index, style, codePoint) -> {
                count[0]++;
                return true;
            });
            return count[0] * 6;
        }

        /**
         * 按宽度截取普通字符串。
         *
         * @param text 原始文本
         * @param maxWidth 最大宽度
         * @return 截取结果
         */
        @Override
        public String plainSubstrByWidth(String text, int maxWidth) {
            return plainSubstrByWidth(text, maxWidth, false);
        }

        /**
         * 按宽度从前向或反向截取普通字符串。
         *
         * @param text 原始文本
         * @param maxWidth 最大宽度
         * @param reverse 是否从末尾反向截取
         * @return 截取结果
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
