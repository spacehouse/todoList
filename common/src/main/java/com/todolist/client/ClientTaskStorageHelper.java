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
 * 负责在单人、本地局域网主机和远程联机之间选择正确的个人任务存储文件。
 */
public final class ClientTaskStorageHelper {
    /**
     * 禁止外部实例化工具类。
     */
    private ClientTaskStorageHelper() {
    }

    /**
     * 判断当前客户端是否处于“本地集成服务端且已发布局域网”的模式。
     *
     * @param client 当前客户端实例
     * @return 是否应使用玩家文件存储个人任务
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
     *
     * @param storage 任务存储服务
     * @param client 当前客户端实例
     * @return 当前应使用的个人任务列表
     * @throws IOException 读取失败时抛出
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
     * 安全读取当前客户端应使用的个人任务列表。
     * 发生异常时返回空列表。
     *
     * @param storage 任务存储服务
     * @param client 当前客户端实例
     * @return 当前应使用的个人任务列表，失败时返回空列表
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
     * 本地集成服务端环境下会同时更新本地单文件与玩家文件，避免两份个人任务副本长期漂移。
     *
     * @param storage 任务存储服务
     * @param client 当前客户端实例
     * @param tasks 待保存的任务列表
     * @throws IOException 保存失败时抛出
     */
    public static void savePersonalTasks(TaskStorage storage, Minecraft client, List<Task> tasks) throws IOException {
        if (storage == null) {
            return;
        }
        UUID playerUuid = getClientPlayerUuid(client);
        if (playerUuid != null && isLocalIntegratedServer(client)) {
            if (storage.isH2StorageSelected()) {
                storage.saveLocalAndPlayerTasks(playerUuid, tasks);
            } else {
                storage.savePlayerTasks(playerUuid, tasks);
                storage.saveTasks(tasks);
            }
            return;
        }
        storage.saveTasks(tasks);
    }

    /**
     * 在单人世界发布局域网后，将本地个人任务迁移到主机玩家文件。
     * 仅当本地文件更新，或玩家文件尚不存在时才执行覆盖。
     *
     * @param storage 任务存储服务
     * @param client 当前客户端实例
     * @return 是否实际执行了迁移
     * @throws IOException 迁移失败时抛出
     */
    public static boolean migrateLocalTasksToPublishedPlayerStorage(TaskStorage storage, Minecraft client) throws IOException {
        if (storage == null || !shouldUsePublishedLocalPlayerStorage(client)) {
            return false;
        }
        UUID playerUuid = getClientPlayerUuid(client);
        return migrateLocalTasksToPlayerStorage(storage, playerUuid);
    }

    /**
     * 在重新进入未发布的单人世界时，将更新的玩家文件任务恢复到本地文件。
     *
     * @param storage 任务存储服务
     * @param client 当前客户端实例
     * @return 是否实际恢复了本地文件
     * @throws IOException 恢复失败时抛出
     */
    public static boolean restorePublishedPlayerTasksToLocalStorage(TaskStorage storage, Minecraft client) throws IOException {
        if (storage == null || !isLocalIntegratedServer(client)) {
            return false;
        }
        UUID playerUuid = getClientPlayerUuid(client);
        return restorePlayerTasksToLocalStorage(storage, playerUuid);
    }

    /**
     * 读取当前客户端正在使用的个人任务文件最后保存时间。
     *
     * @param storage 任务存储服务
     * @param client 当前客户端实例
     * @return 最后保存时间戳
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
     * 返回当前客户端用于个人任务存储的玩家 UUID，供只读查询路径复用同一桶选择逻辑。
     *
     * @param client 当前客户端实例
     * @return 存储使用的玩家 UUID；不可用时返回 null
     */
    public static UUID resolveStoragePlayerUuid(Minecraft client) {
        return getClientPlayerUuid(client);
    }

    /**
     * 提取当前客户端玩家 UUID。
     * 在本地集成服务端场景下，会尽量对齐服务端玩家 UUID。
     *
     * @param client 当前客户端实例
     * @return 当前玩家 UUID；不可用时返回 null
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
     * 解析本地单人或 LAN 主机对应的服务端玩家对象。
     *
     * @param client 当前客户端实例
     * @param clientUuid 客户端玩家 UUID
     * @return 对应的服务端玩家对象；无法解析时返回 null
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
     * 判断客户端是否处于本地集成服务端上下文。
     *
     * @param client 当前客户端实例
     * @return 是否处于本地集成服务端
     */
    private static boolean isLocalIntegratedServer(Minecraft client) {
        return client != null && (client.isLocalServer() || client.getSingleplayerServer() != null);
    }

    /**
     * 将本地个人任务迁移到指定玩家文件。
     * 供已发布局域网切换和离线回归测试复用。
     *
     * @param storage 任务存储服务
     * @param playerUuid 目标玩家 UUID
     * @return 是否实际执行了迁移
     * @throws IOException 迁移失败时抛出
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
     * 将较新的玩家任务文件恢复到本地文件。
     * 供重新进入单人模式后的离线继续使用。
     *
     * @param storage 任务存储服务
     * @param playerUuid 目标玩家 UUID
     * @return 是否实际执行了恢复
     * @throws IOException 恢复失败时抛出
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
