package com.todolist.gui.testsupport;

import net.minecraft.client.gui.screens.Screen;

/**
 * 26.3 测试用 TextInputManager 替身。
 *
 * <p>26.3 窗口层从 GLFW 迁移到 SDL，{@code TextInputManager} 的
 * startTextInput / stopTextInput / setTextInputArea 全部直达 SDL 原生调用；
 * 离线测试进程没有 natives 且无真实 IME，这里覆写为纯内存语义
 * （保留 owner 与启用标志的行为形状，但不触任何原生层）。
 *
 * <p>仅测试编译可见的方法签名被覆写：{@code applyTextInputArea} 为私有、
 * 由 start/stop 内部调用，覆写 start/stop 后不再触达。
 */
public class FakeTextInputManager extends com.mojang.blaze3d.platform.TextInputManager {

    /** 当前持有文本输入焦点的 owner（测试观测用）。 */
    private Object focusedOwner;
    /** 是否处于文本输入启用态（与原生语义一致的内存标志）。 */
    private boolean textInputEnabled;

    /**
     * 构造方法：父类构造仅记录 Window 引用，传 null 安全（覆写后不再访问）。
     */
    public FakeTextInputManager() {
        super(null);
    }

    @Override
    public void startTextInput(Object owner) {
        this.focusedOwner = owner;
        this.textInputEnabled = true;
    }

    @Override
    public void stopTextInput(Object owner) {
        if (focusedOwner == owner) {
            focusedOwner = null;
            textInputEnabled = false;
        }
    }

    @Override
    public void setTextInputArea(int x, int y, int width, int height) {
        // 原生候选窗定位，测试环境空实现。
    }
}
