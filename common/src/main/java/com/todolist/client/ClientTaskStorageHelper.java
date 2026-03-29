package com.todolist.client;

import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

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
        if (client == null || client.player == null || !isLocalIntegratedServer(client)) {
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
        if (playerUuid != null && isLocalIntegratedServer(client)) {
            restorePlayerTasksToLocalStorage(storage, playerUuid);
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
            if (isLocalIntegratedServer(client)) {
                storage.saveTasks(tasks);
            }
            return;
        }
        storage.saveTasks(tasks);
    }

    /**
     * 读取当前客户端个人任务文件的最后保存时间戳。
     */
    /**
     * 在单人世界发布局域网后，将旧的本地个人任务迁移到主机玩家文件。
     * 仅当本地文件更新，或玩家文件尚不存在时才执行写入，避免覆盖更新的数据。
     */
    public static boolean migrateLocalTasksToPublishedPlayerStorage(TaskStorage storage, Minecraft client) throws IOException {
        if (storage == null || !shouldUsePublishedLocalPlayerStorage(client)) {
            return false;
        }
        UUID playerUuid = getClientPlayerUuid(client);
        return migrateLocalTasksToPlayerStorage(storage, playerUuid);
    }

    /**
     * 在重新进入未发布的单人世界时，将较新的玩家文件个人任务回灌到本地文件。
     * 这样上一轮局域网主机阶段新增的个人任务，在下次单人模式下也能继续看到。
     */
    public static boolean restorePublishedPlayerTasksToLocalStorage(TaskStorage storage, Minecraft client) throws IOException {
        if (storage == null || !isLocalIntegratedServer(client)) {
            return false;
        }
        UUID playerUuid = getClientPlayerUuid(client);
        return restorePlayerTasksToLocalStorage(storage, playerUuid);
    }

    /**
     * 读取当前客户端正在使用的个人任务文件最后保存时间戳。
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
        if (client == null || client.player == null) {
            return null;
        }
        UUID clientUuid = client.player.getUUID();
        if (!isLocalIntegratedServer(client)) {
            return clientUuid;
        }
        ServerPlayer serverPlayer = resolveLocalServerPlayer(client, clientUuid);
        return serverPlayer == null ? clientUuid : serverPlayer.getUUID();
    }

    /**
     * 解析本地单人/LAN 主机对应的服务端玩家对象，尽量对齐客户端与服务端可能存在的 UUID 差异。
     */
    private static ServerPlayer resolveLocalServerPlayer(Minecraft client, UUID clientUuid) {
        if (client == null || client.player == null || !isLocalIntegratedServer(client)) {
            return null;
        }
        var server = client.getSingleplayerServer();
        if (server == null) {
            return null;
        }
        var playerList = server.getPlayerList();
        if (playerList == null) {
            return null;
        }
        ServerPlayer serverPlayer = clientUuid == null ? null : playerList.getPlayer(clientUuid);
        if (serverPlayer != null) {
            return serverPlayer;
        }
        String playerName = client.player.getGameProfile() == null ? null : client.player.getGameProfile().getName();
        if (playerName != null && !playerName.isBlank()) {
            serverPlayer = playerList.getPlayerByName(playerName);
            if (serverPlayer != null) {
                return serverPlayer;
            }
        }
        List<ServerPlayer> players = playerList.getPlayers();
        if (players.size() == 1) {
            return players.get(0);
        }
        return null;
    }

    /**
     * 判断客户端是否处于本地集成服上下文，兼容 Forge 某些阶段 isLocalServer 仍未稳定的情况。
     */
    private static boolean isLocalIntegratedServer(Minecraft client) {
        return client != null && (client.isLocalServer() || client.getSingleplayerServer() != null);
    }

    /**
     * 将本地个人任务迁移到指定玩家文件，供发布局域网切换和离线回归测试复用。
     */
    static boolean migrateLocalTasksToPlayerStorage(TaskStorage storage, UUID playerUuid) throws IOException {
        if (storage == null || playerUuid == null) {
            return false;
        }

        long localLastSaved = storage.getLocalTasksLastSaved();
        if (localLastSaved <= 0L) {
            return false;
        }

        boolean playerFileExists = storage.hasPlayerTasks(playerUuid);
        long playerLastSaved = storage.getPlayerTasksLastSaved(playerUuid);
        if (playerFileExists && playerLastSaved >= localLastSaved) {
            return false;
        }

        List<Task> localTasks = storage.loadTasks();
        storage.savePlayerTasks(playerUuid, localTasks);
        return true;
    }

    /**
     * 将较新的玩家任务文件恢复到本地文件，供单人重进后的离线模式继续使用。
     */
    static boolean restorePlayerTasksToLocalStorage(TaskStorage storage, UUID playerUuid) throws IOException {
        if (storage == null || playerUuid == null || !storage.hasPlayerTasks(playerUuid)) {
            return false;
        }

        long playerLastSaved = storage.getPlayerTasksLastSaved(playerUuid);
        long localLastSaved = storage.getLocalTasksLastSaved();
        if (playerLastSaved <= localLastSaved) {
            return false;
        }

        List<Task> playerTasks = storage.loadPlayerTasks(playerUuid);
        storage.saveTasks(playerTasks);
        return true;
    }
}
