package com.todolist.task;

import com.todolist.TodoConstants;
import com.todolist.storage.H2TaskStore;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 任务存储组件。
 * 统一通过 H2 存储处理单人本地、玩家个人与团队任务的数据落盘、读取及元数据查询。
 */
public class TaskStorage {
    private final H2TaskStore h2TaskStore = new H2TaskStore();

    /**
     * 创建任务存储组件。
     */
    public TaskStorage() {
    }

    /**
     * 保存单人本地任务列表。
     *
     * @param tasks 待保存的任务列表
     * @throws IOException 当保存失败时抛出
     */
    public void saveTasks(List<Task> tasks) throws IOException {
        h2TaskStore.saveLocalTasks(tasks);
    }

    /**
     * 保存指定玩家的个人任务列表。
     *
     * @param playerUuid 玩家 UUID
     * @param tasks 待保存的任务列表
     * @throws IOException 当保存失败时抛出
     */
    public void savePlayerTasks(UUID playerUuid, List<Task> tasks) throws IOException {
        h2TaskStore.savePlayerTasks(playerUuid, tasks);
    }

    /**
     * 同时保存本地个人任务与玩家个人任务。
     *
     * @param playerUuid 玩家 UUID
     * @param tasks 待保存的任务列表
     * @throws IOException 保存失败时抛出
     */
    public void saveLocalAndPlayerTasks(UUID playerUuid, List<Task> tasks) throws IOException {
        h2TaskStore.saveLocalAndPlayerTasks(playerUuid, tasks);
    }

    /**
     * 按当前服务端运行模式保存个人任务。
     * 单人本地模式写入本地任务桶，其余模式写入玩家任务桶。
     *
     * @param server 当前服务端
     * @param playerUuid 玩家 UUID
     * @param tasks 待保存的任务列表
     * @throws IOException 当保存失败时抛出
     */
    public void savePersonalTasks(MinecraftServer server, UUID playerUuid, List<Task> tasks) throws IOException {
        if (shouldUseLocalPersonalStorage(server)) {
            saveTasks(tasks);
            return;
        }
        savePlayerTasks(playerUuid, tasks);
    }

    /**
     * 保存团队任务列表。
     *
     * @param tasks 待保存的团队任务列表
     * @throws IOException 当保存失败时抛出
     */
    public void saveTeamTasks(List<Task> tasks) throws IOException {
        h2TaskStore.saveTeamTasks(tasks);
    }

    /**
     * 增量更新任务的触发器进度与完成态（只 UPDATE 触发器相关列，不做全量替换）。
     * 供触发器引擎落库使用，避免覆盖其他保存路径刚写入的任务。
     *
     * @param tasks 引擎推进过进度的任务集合
     * @throws IOException 更新失败时抛出
     */
    public void updateTriggerStates(java.util.Collection<Task> tasks) throws IOException {
        h2TaskStore.updateTriggerStates(tasks);
    }

    /**
     * 读取单人本地任务列表。
     *
     * @return 读取到的任务列表
     * @throws IOException 当读取失败时抛出
     */
    public List<Task> loadTasks() throws IOException {
        return h2TaskStore.loadLocalTasks();
    }

    /**
     * 读取指定玩家的个人任务列表。
     *
     * @param playerUuid 玩家 UUID
     * @return 读取到的任务列表
     * @throws IOException 当读取失败时抛出
     */
    public List<Task> loadPlayerTasks(UUID playerUuid) throws IOException {
        return h2TaskStore.loadPlayerTasks(playerUuid);
    }

    /**
     * 按当前服务端运行模式读取个人任务。
     * 单人本地模式优先读取本地任务桶，其余模式读取玩家任务桶。
     *
     * @param server 当前服务端
     * @param playerUuid 玩家 UUID
     * @return 读取到的任务列表
     * @throws IOException 当读取失败时抛出
     */
    public List<Task> loadPersonalTasks(MinecraftServer server, UUID playerUuid) throws IOException {
        if (shouldUseLocalPersonalStorage(server)) {
            return loadTasks();
        }
        return loadPlayerTasks(playerUuid);
    }

    /**
     * 读取团队任务列表。
     *
     * @return 读取到的团队任务列表
     * @throws IOException 当读取失败时抛出
     */
    public List<Task> loadTeamTasks() throws IOException {
        return h2TaskStore.loadTeamTasks();
    }

    /**
     * 返回单人本地任务桶的最后保存时间戳。
     *
     * @return 最后保存时间戳，不存在时返回 0
     */
    public long getLocalTasksLastSaved() {
        try {
            return h2TaskStore.getBucketLastSaved(H2TaskStore.LOCAL_PERSONAL_BUCKET, H2TaskStore.LOCAL_OWNER);
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to read H2 local task timestamp", exception);
            return 0L;
        }
    }

    /**
     * 返回指定玩家任务桶的最后保存时间戳。
     *
     * @param playerUuid 玩家 UUID
     * @return 最后保存时间戳，不存在时返回 0
     */
    public long getPlayerTasksLastSaved(UUID playerUuid) {
        if (playerUuid == null) {
            return 0L;
        }
        try {
            return h2TaskStore.getBucketLastSaved(H2TaskStore.PLAYER_PERSONAL_BUCKET, playerUuid.toString());
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to read H2 player task timestamp for {}", playerUuid, exception);
            return 0L;
        }
    }

    /**
     * 按当前服务端运行模式读取个人任务桶的最后保存时间戳。
     *
     * @param server 当前服务端
     * @param playerUuid 玩家 UUID
     * @return 最后保存时间戳，不存在时返回 0
     */
    public long getPersonalTasksLastSaved(MinecraftServer server, UUID playerUuid) {
        if (shouldUseLocalPersonalStorage(server)) {
            return getLocalTasksLastSaved();
        }
        return getPlayerTasksLastSaved(playerUuid);
    }

    /**
     * 判断指定玩家任务桶是否已有数据。
     *
     * @param playerUuid 玩家 UUID
     * @return 若玩家任务桶已有数据则返回 true
     */
    public boolean hasPlayerTasks(UUID playerUuid) {
        try {
            return h2TaskStore.hasPlayerTasks(playerUuid);
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to query H2 player task bucket for {}", playerUuid, exception);
            return false;
        }
    }

    /**
     * 判断当前服务端是否应使用单人本地个人任务桶。
     *
     * @param server 当前服务端
     * @return 单人未发布模式返回 true，否则返回 false
     */
    public boolean shouldUseLocalPersonalStorage(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        if (server.isDedicatedServer()) {
            return false;
        }
        return !server.isPublished();
    }
}
