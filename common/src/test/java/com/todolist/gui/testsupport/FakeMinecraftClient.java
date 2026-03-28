package com.todolist.gui.testsupport;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
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
     * 璁剧疆娴嬭瘯鐢ㄧ獥鍙ｅ疄渚嬶紝渚?HUD 绛夐€昏緫璇诲彇灞忓箷灏哄涓庣缉鏀惧€笺€?
     *
     * @param window 娴嬭瘯绐楀彛瀹炰緥
     */
    public void setTestWindow(Window window) {
        this.testWindow = window;
    }

    /**
     * 璁剧疆娴嬭瘯鐢ㄩ€夐」瀹炰緥锛屼緵鐩存帴璇诲彇 `options.hideGui` 鐨勯€昏緫浣跨敤銆?
     *
     * @param options 娴嬭瘯閫夐」瀹炰緥
     */
    public void setTestOptions(Options options) {
        this.testOptions = options;
    }

    /**
     * 璁剧疆娴嬭瘯绐楀彛鐨?GUI 灏哄涓庣缉鏀撅紝渚夸簬 HUD 绂荤嚎鑷祴鎺у埗甯冨眬鏉′欢銆?
     *
     * @param guiScaledWidth GUI 缂╂斁鍚庡搴?
     * @param guiScaledHeight GUI 缂╂斁鍚庨珮搴?
     * @param guiScale GUI 缂╂斁鍊?
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
     * 璁剧疆 `options.hideGui` 娴嬭瘯鐘舵€侊紝渚涢獙璇?HUD 鏄剧ず寮€鍏虫柟鍚戠殑閫昏緫浣跨敤銆?
     *
     * @param hideGui true 琛ㄧず闅愯棌 HUD
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
     * 杩斿洖娴嬭瘯绐楀彛瀹炰緥锛岄伩鍏嶈繘鍏ョ湡瀹?Minecraft 窗口渚濊禆銆?
     *
     * @return 娴嬭瘯绐楀彛瀹炰緥
     */
    @Override
    public Window getWindow() {
        return testWindow;
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
