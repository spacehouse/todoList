package com.todolist.gui.testsupport;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;

/**
 * 26.2 测试用 Gui 替身。
 *
 * <p>26.2 起屏幕状态从 {@code Minecraft.screen} 移入中央 {@link Gui} 组件；
 * 真实 {@link Gui#setScreen} 包含 TitleScreen 兜底、死亡屏分支与完整 init 流程，
 * 测试桩没有真实世界/玩家状态，这里覆写为「记录 + 直写字段」，与 1.21.x 时代
 * {@code FakeMinecraftClient.setScreen} 的拦截语义一致。
 */
public class FakeGui extends Gui {

    /** 属主 fake 客户端，用于同步 lastScreen 记录。 */
    private FakeMinecraftClient owner;

    /**
     * 构造方法仅用于满足编译要求，测试运行时通过 Unsafe 绕过。
     */
    public FakeGui() {
        super(null, null, null);
    }

    /**
     * 绑定属主 fake 客户端（createMinecraft 注入后调用）。
     *
     * @param client 属主实例
     */
    public void bindOwner(FakeMinecraftClient client) {
        this.owner = client;
    }

    /**
     * 记录界面切换并直写 screen 字段，绕开真实切换流程。
     *
     * @param guiScreen 新界面
     */
    @Override
    public void setScreen(Screen guiScreen) {
        GuiTestSupport.setObjectField(Gui.class, this, "screen", guiScreen);
        if (owner != null) {
            owner.recordLastScreen(guiScreen);
        }
    }
}
