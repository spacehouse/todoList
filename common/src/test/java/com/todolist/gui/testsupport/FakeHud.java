package com.todolist.gui.testsupport;

import net.minecraft.client.gui.Hud;

/**
 * 26.2 测试用 Hud 替身。
 *
 * <p>26.2 起原 {@code options.hideGui} 的 F1 可见性状态移入 {@link Hud#isHidden()}；
 * Unsafe 分配的实例字段全为零值，这里用独立测试标记覆写访问器，
 * 供 {@code FakeMinecraftClient#setHideGui} 驱动 HUD 显示开关方向验证。
 */
public class FakeHud extends Hud {

    /** 测试用隐藏标记。 */
    private boolean testHidden;

    /**
     * 构造方法仅用于满足编译要求，测试运行时通过 Unsafe 绕过。
     */
    public FakeHud() {
        super(null);
    }

    /**
     * 设置测试隐藏状态。
     *
     * @param hidden true 表示隐藏 HUD
     */
    public void setTestHidden(boolean hidden) {
        this.testHidden = hidden;
    }

    @Override
    public boolean isHidden() {
        return testHidden;
    }
}
