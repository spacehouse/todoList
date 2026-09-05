package com.todolist.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 待办列表全部界面的公共基类。
 *
 * <p>存在的意义：renderBackground / mouseScrolled 等方法在 1.20.1 与 1.20.2+
 * 之间签名不同，无法在同一份源码里直接 override。本类把版本相关成员放到构建期注入
 * （见 common/build.gradle.kts 的 prepareVersionedMainSources 任务），从而保证：</p>
 *
 * <ul>
 *   <li>1.20.2 起 Screen#render 默认实现会先调用 renderBackground 再渲染组件，
 *       本类将其覆盖为空实现以抑制原版默认背景，避免其覆盖界面自绘内容；</li>
 *   <li>需要原版标准背景的界面通过 {@link #renderVanillaBackground} 显式获取；</li>
 *   <li>滚轮事件按目标版本签名透传给父类处理。</li>
 * </ul>
 */
public abstract class BaseTodoScreen extends Screen {

    /**
     * 构造基类界面。
     *
     * @param title 界面标题
     */
    protected BaseTodoScreen(Component title) {
        super(title);
    }

    // __VERSION_INJECTED_MEMBERS__
    public abstract void renderVanillaBackground(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    public abstract boolean superMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount);

    public abstract boolean supportsAutoInitialFocus();
}
