package com.todolist.gui.testsupport;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.InputType;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 假 Minecraft 客户端：为离线 GUI 测试提供最小的 screen、player 与 connection 能力。
 */
public class FakeMinecraftClient extends Minecraft {
    private Font testFont;
    private LocalPlayer testPlayer;
    private ClientPacketListener testConnection;
    private GameNarrator testNarrator;
    private Window testWindow;
    private Options testOptions;
    private SoundManager testSoundManager;
    private InputType testLastInputType;
    private Screen lastScreen;
    private boolean localServer;
    private IntegratedServer integratedServer;

    /**
     * 构造方法仅用于满足编译要求，测试运行时通过 Unsafe 绕过。
     */
    protected FakeMinecraftClient() {
        super(null);
        throw new UnsupportedOperationException("请通过 GuiTestSupport.createMinecraft 创建假客户端");
    }

    /**
     * 设置测试字体实例。
     *
     * @param font 字体实例
     */
    void setTestFont(Font font) {
        this.testFont = font;
    }

    /**
     * 设置测试玩家实例。
     *
     * @param player 玩家实例
     */
    public void setTestPlayer(LocalPlayer player) {
        this.testPlayer = player;
        GuiTestSupport.setObjectField(Minecraft.class, this, "player", player);
    }

    /**
     * 设置测试连接实例。
     *
     * @param connection 连接实例
     */
    public void setTestConnection(ClientPacketListener connection) {
        this.testConnection = connection;
    }

    /**
     * 设置测试旁白器实例。
     *
     * @param narrator 测试旁白器
     */
    public void setTestNarrator(GameNarrator narrator) {
        this.testNarrator = narrator;
    }

    /**
     * 设置测试窗口实例，供 HUD 等逻辑读取屏幕尺寸与缩放值。
     *
     * @param window 测试窗口实例
     */
    public void setTestWindow(Window window) {
        this.testWindow = window;
    }

    /**
     * 设置测试选项实例，供直接读取 `options.hideGui` 的逻辑使用。
     *
     * @param options 测试选项实例
     */
    public void setTestOptions(Options options) {
        this.testOptions = options;
    }

    /**
     * 设置测试音效管理器，避免按钮快捷键测试访问空的音频环境。
     *
     * @param soundManager 测试音效管理器
     */
    public void setTestSoundManager(SoundManager soundManager) {
        this.testSoundManager = soundManager;
        GuiTestSupport.setObjectField(Minecraft.class, this, "soundManager", soundManager);
    }

    /**
     * 设置最近一次输入设备类型，避免 Screen 初始化阶段读取到空值。
     *
     * @param inputType 最近一次输入类型
     */
    public void setTestLastInputType(InputType inputType) {
        this.testLastInputType = inputType;
        GuiTestSupport.setObjectField(Minecraft.class, this, "lastInputType", inputType);
    }

    /**
     * 设置测试窗口的 GUI 尺寸与缩放，便于 HUD 离线自测控制布局条件。
     *
     * @param guiScaledWidth GUI 缩放后宽度
     * @param guiScaledHeight GUI 缩放后高度
     * @param guiScale GUI 缩放值
     */
    public void setWindowMetrics(int guiScaledWidth, int guiScaledHeight, double guiScale) {
        if (testWindow == null) {
            return;
        }
        GuiTestSupport.setIntField(Window.class, testWindow, "width", guiScaledWidth);
        GuiTestSupport.setIntField(Window.class, testWindow, "height", guiScaledHeight);
        GuiTestSupport.setIntField(Window.class, testWindow, "guiScaledWidth", guiScaledWidth);
        GuiTestSupport.setIntField(Window.class, testWindow, "guiScaledHeight", guiScaledHeight);
        GuiTestSupport.setDoubleField(Window.class, testWindow, "guiScale", guiScale);
    }

    /**
     * 设置 `options.hideGui` 测试状态，供验证 HUD 显示开关方向的逻辑使用。
     *
     * @param hideGui true 表示隐藏 HUD
     */
    public void setHideGui(boolean hideGui) {
        if (testOptions != null) {
            testOptions.hideGui = hideGui;
        }
    }

    /**
     * 设置是否视为本地集成服务端环境。
     *
     * @param local true 表示本地集成服务端
     */
    public void setLocalServer(boolean local) {
        this.localServer = local;
    }

    /**
     * 设置测试用集成服务端。
     *
     * @param server 集成服务端实例
     */
    public void setIntegratedServer(IntegratedServer server) {
        this.integratedServer = server;
    }

    /**
     * 获取最近一次切换到的界面。
     *
     * @return 最近一次切换到的界面
     */
    public Screen getLastScreen() {
        return lastScreen;
    }

    /**
     * 返回测试玩家收到的客户端消息快照。
     *
     * @return 客户端消息快照
     */
    public List<Component> getTestPlayerMessages() {
        if (testPlayer instanceof FakeClientPlayer fakeClientPlayer) {
            return fakeClientPlayer.getClientMessages();
        }
        return List.of();
    }

    /**
     * 返回测试玩家累计播放的提示音次数。
     *
     * @return 提示音播放次数
     */
    public int getPlayedSoundCount() {
        if (testPlayer instanceof FakeClientPlayer fakeClientPlayer) {
            return fakeClientPlayer.getPlayedSoundCount();
        }
        return 0;
    }

    /**
     * 记录界面切换，并同步到父类 screen 字段。
     *
     * @param guiScreen 新界面
     */
    @Override
    public void setScreen(Screen guiScreen) {
        this.lastScreen = guiScreen;
        GuiTestSupport.setObjectField(Minecraft.class, this, "screen", guiScreen);
    }

    /**
     * 返回测试连接实例。
     *
     * @return 测试连接实例
     */
    @Override
    public ClientPacketListener getConnection() {
        return testConnection;
    }

    /**
     * 返回测试窗口实例，避免进入真实 Minecraft 窗口依赖。
     *
     * @return 测试窗口实例
     */
    @Override
    public Window getWindow() {
        return testWindow;
    }

    /**
     * 返回测试音效管理器，供按钮交互播放点击音效时使用。
     *
     * @return 测试音效管理器
     */
    @Override
    public SoundManager getSoundManager() {
        return testSoundManager;
    }

    /**
     * 返回最近一次输入设备类型，供 GUI 焦点逻辑判断键鼠模式。
     *
     * @return 最近一次输入类型
     */
    @Override
    public InputType getLastInputType() {
        return testLastInputType;
    }

    /**
     * 同步更新最近一次输入设备类型，保持测试桩与父类字段一致。
     *
     * @param inputType 最近一次输入类型
     */
    @Override
    public void setLastInputType(InputType inputType) {
        setTestLastInputType(inputType);
    }

    /**
     * 返回测试旁白器，避免 Screen 初始化时访问空旁白对象。
     *
     * @return 测试旁白器
     */
    @Override
    public GameNarrator getNarrator() {
        return testNarrator;
    }

    /**
     * 立即执行提交到客户端线程的任务，避免离线测试依赖真实调度器。
     *
     * @param runnable 待执行任务
     */
    @Override
    public void execute(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    /**
     * 返回当前是否处于本地集成服务端环境。
     *
     * @return true 表示本地集成服务端
     */
    @Override
    public boolean isLocalServer() {
        return localServer;
    }

    /**
     * 返回测试用集成服务端。
     *
     * @return 测试用集成服务端
     */
    @Override
    public IntegratedServer getSingleplayerServer() {
        return integratedServer;
    }
}
