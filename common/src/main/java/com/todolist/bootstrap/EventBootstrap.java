package com.todolist.bootstrap;

import net.minecraft.server.MinecraftServer;
import java.util.function.Consumer;

/**
 * 妯＄粍浜嬩欢寮曞绫汇€? * 鍖呭惈鏈嶅姟绔敓鍛藉懆鏈熶簨浠剁殑閫氱敤娉ㄥ唽閫昏緫銆? */
public final class EventBootstrap {
    private EventBootstrap() {
    }

    /**
     * 娉ㄥ唽鏈嶅姟绔敓鍛藉懆鏈熶簨浠躲€?     * 璇ユ柟娉曠敱鍚勫钩鍙版ā鍧楄皟鐢ㄤ互鎵ц骞冲彴鐗瑰畾鐨勬敞鍐屽姩浣溿€?     *
     * @param server     MinecraftServer 瀹炰緥
     * @param onStarting 鏈嶅姟绔惎鍔ㄦ椂鐨勫洖璋?     * @param onStopped  鏈嶅姟绔仠姝㈡椂鐨勫洖璋?     */
    public static void handleServerStarting(MinecraftServer server, Consumer<MinecraftServer> onStarting) {
        if (onStarting != null) {
            onStarting.accept(server);
        }
    }

    public static void handleServerStopped(MinecraftServer server, Consumer<MinecraftServer> onStopped) {
        if (onStopped != null) {
            onStopped.accept(server);
        }
    }
}


