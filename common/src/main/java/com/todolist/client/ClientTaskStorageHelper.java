package com.todolist.client;

import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户端个人任务存储辅助类。
 * 负责在单人、局域网主机和远程联机之间选择正确的个人任务缓存文件，避免主机本地出现双份数据源。
 */
public final class ClientTaskStorageHelper {
    /**
     * 创建辅助类实例没有意义，禁止实例化。
     */
    private ClientTaskStorageHelper() {
    }

    /**
     * 判断当前客户端是否处于“本地集成服务端且已发布局域网”的模式。
     */
    public static boolean shouldUsePublishedLocalPlayerStorage(Minecraft client) {
        if (client == null || client.player == null || !client.isLocalServer()) {
            return false;
        }
        var server = client.getSingleplayerServer();
        return server != null && server.isPublished();
    }

    /**
     * 读取当前客户端应使用的个人任务列表。
     */
    public static List<Task> loadPersonalTasks(TaskStorage storage, Minecraft client) throws IOException {
        if (storage == null) {
            return new ArrayList<>();
        }
        UUID playerUuid = getClientPlayerUuid(client);
        if (playerUuid != null && shouldUsePublishedLocalPlayerStorage(client)) {
            return storage.loadPlayerTasks(playerUuid);
        }
        return storage.loadTasks();
    }

    /**
     * 安全读取当前客户端应使用的个人任务列表，失败时返回空列表。
     */
    public static List<Task> loadPersonalTasksSafe(TaskStorage storage, Minecraft client) {
        try {
            return loadPersonalTasks(storage, client);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /**
     * 保存当前客户端应使用的个人任务列表。
     */
    public static void savePersonalTasks(TaskStorage storage, Minecraft client, List<Task> tasks) throws IOException {
        if (storage == null) {
            return;
        }
        UUID playerUuid = getClientPlayerUuid(client);
        if (playerUuid != null && shouldUsePublishedLocalPlayerStorage(client)) {
            storage.savePlayerTasks(playerUuid, tasks);
            return;
        }
        storage.saveTasks(tasks);
    }

    /**
     * 读取当前客户端个人任务文件的最后保存时间戳。
     */
    public static long getPersonalTasksLastSaved(TaskStorage storage, Minecraft client) {
        if (storage == null) {
            return 0L;
        }
        UUID playerUuid = getClientPlayerUuid(client);
        if (playerUuid != null && shouldUsePublishedLocalPlayerStorage(client)) {
            return storage.getPlayerTasksLastSaved(playerUuid);
        }
        return storage.getLocalTasksLastSaved();
    }

    /**
     * 提取当前客户端玩家 UUID，不可用时返回 null。
     */
    private static UUID getClientPlayerUuid(Minecraft client) {
        return client == null || client.player == null ? null : client.player.getUUID();
    }
}
