package com.todolist.gui.testsupport;

import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 假旁白器：为 Screen 初始化提供最小可用的 narrator 对象，避免离线 GUI 自测依赖真实旁白系统。
 */
public class FakeGameNarrator extends GameNarrator {
    /**
     * 构造方法仅用于满足编译要求，测试运行时通过 Unsafe 绕过。
     */
    protected FakeGameNarrator() {
        super((Minecraft) null);
        throw new UnsupportedOperationException("请通过 FakeGameNarrator.create 创建测试旁白器");
    }

    /**
     * 创建测试旁白器实例。
     *
     * @return 测试旁白器实例
     */
    public static FakeGameNarrator create() {
        return GuiTestSupport.allocate(FakeGameNarrator.class);
    }

    /**
     * 返回旁白是否激活，测试中固定关闭即可满足 Screen 初始化流程。
     *
     * @return false，表示不启用真实旁白
     */
    @Override
    public boolean isActive() {
        return false;
    }

    /**
     * 忽略普通旁白输出，避免离线测试依赖真实 TTS。
     *
     * @param component 待旁白文本
     */
    @Override
    public void say(Component component) {
    }

    /**
     * 忽略聊天旁白输出，避免离线测试依赖真实 TTS。
     *
     * @param component 待旁白文本
     */
    @Override
    public void sayChat(Component component) {
    }

    /**
     * 忽略立即旁白输出，避免离线测试依赖真实 TTS。
     *
     * @param component 待旁白文本
     */
    @Override
    public void sayNow(Component component) {
    }

    /**
     * 忽略旁白清理，测试中无需处理真实资源。
     */
    @Override
    public void clear() {
    }

    /**
     * 忽略旁白销毁，测试中无需处理真实资源。
     */
    @Override
    public void destroy() {
    }
}
