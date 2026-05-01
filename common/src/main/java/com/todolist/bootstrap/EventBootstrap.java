package com.todolist.bootstrap;

import com.todolist.TodoListCommon;
import net.minecraft.server.MinecraftServer;
import java.util.function.Consumer;

/**
 * 模组事件引导类。
 * 包含服务端生命周期事件的通用分发逻辑，供不同平台（Fabric/Forge）在各自事件回调中调用。
 */
public final class EventBootstrap {
    private EventBootstrap() {
    }

    /**
     * 处理服务端启动阶段事件。
     *
     * @param server     MinecraftServer 实例
     * @param onStarting 服务端启动时的回调
     */
    public static void handleServerStarting(MinecraftServer server, Consumer<MinecraftServer> onStarting) {
        if (onStarting != null) {
            onStarting.accept(server);
        }
    }

    /**
     * 处理服务端停止阶段事件。
     *
     * @param server    MinecraftServer 实例
     * @param onStopped 服务端停止时的回调
     */
    public static void handleServerStopped(MinecraftServer server, Consumer<MinecraftServer> onStopped) {
        if (onStopped != null) {
            onStopped.accept(server);
        }
        TodoListCommon.closeStorageContext();
    }
}


