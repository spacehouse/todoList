package com.todolist;

import com.todolist.platform.DataPathProvider;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HUD 实时链路诊断探针开关。
 *
 * <p>在游戏目录的 {@code config/todolist-hud-probe.flag} 放置标记文件后启用，
 * 服务端推送与客户端应用的各环节会以 {@code [hud-probe]} 前缀输出诊断日志，
 * 用于定位"HUD 进度不实时、打开 GUI 才同步"的断点。诊断完成后删除标记文件即可关闭。
 */
public final class HudProbe {
    private HudProbe() {
    }

    /**
     * 探针是否启用（标记文件存在即启用；文件缺失或游戏目录未初始化时返回 false）。
     */
    public static boolean enabled() {
        try {
            Path flag = DataPathProvider.getGameDir().resolve("config").resolve("todolist-hud-probe.flag");
            return Files.exists(flag);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 探针启用时输出诊断日志，未启用时不产生任何开销以外的字符串拼接。
     *
     * @param format 日志格式
     * @param args   参数
     */
    public static void log(String format, Object... args) {
        TodoConstants.LOGGER.info("[hud-probe] " + format, args);
    }
}
