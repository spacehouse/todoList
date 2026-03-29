package com.todolist.gui.testsupport;

import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;

/**
 * 轻量级测试音效管理器：为离线 GUI 测试提供无副作用的按钮音效桩实现。
 */
public final class FakeSoundManager extends SoundManager {
    /**
     * 构造方法仅用于满足继承要求，测试中通过 Unsafe 分配实例，不会真正执行。
     */
    private FakeSoundManager() {
        super((Options) null);
        throw new UnsupportedOperationException("请通过 FakeSoundManager.create 创建测试音效管理器");
    }

    /**
     * 创建测试音效管理器实例，避免真实声音系统初始化。
     *
     * @return 测试音效管理器
     */
    public static FakeSoundManager create() {
        return GuiTestSupport.allocate(FakeSoundManager.class);
    }

    /**
     * 吞掉按钮点击等即时音效播放请求，避免 GUI 测试依赖底层音频设备。
     *
     * @param sound 待播放的声音实例
     */
    @Override
    public void play(SoundInstance sound) {
    }

    /**
     * 吞掉延迟音效播放请求，保持测试环境纯内存化。
     *
     * @param sound 待播放的声音实例
     * @param delay 延迟 tick 数
     */
    @Override
    public void playDelayed(SoundInstance sound, int delay) {
    }
}
