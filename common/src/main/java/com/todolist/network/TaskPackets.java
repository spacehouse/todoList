package com.todolist.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.storage.H2MaintenanceGuard;
import com.todolist.storage.StorageFailureNotifier;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 任务同步相关的网络包定义与服务端处理逻辑。
 */
public class TaskPackets {
    public static final ResourceLocation SYNC_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "sync_tasks");
    public static final ResourceLocation TEAM_SYNC_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_sync_tasks");
    public static final ResourceLocation TASK_CONFIRMED_ID = new ResourceLocation(TodoConstants.MOD_ID, "task_confirmed");
    public static final ResourceLocation REPLACE_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "replace_tasks");
    public static final ResourceLocation TEAM_REPLACE_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_replace_tasks");
    public static final ResourceLocation TEAM_REQUEST_SYNC_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_request_sync");
    public static final ResourceLocation ADD_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "add_task");
    public static final ResourceLocation UPDATE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "update_task");
    public static final ResourceLocation DELETE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "delete_task");
    public static final ResourceLocation TOGGLE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "toggle_task");
    public static final ResourceLocation TEAM_TOGGLE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_toggle_task");
    public static final ResourceLocation TEAM_ASSIGN_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_assign_task");
    private static volatile ServerPacketSender serverPacketSender = (player, channelId, buf) -> { };

    public static void setServerPacketSender(ServerPacketSender sender) {
        serverPacketSender = sender == null ? (player, channelId, buf) -> { } : sender;
    }

    public static void onReplaceTasksPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        List<Task> tasks = readTaskList(buf);
        server.execute(() -> handleReplaceTasks(player, tasks));
    }

    public static void onTeamReplaceTasksPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        List<Task> tasks = readTaskList(buf);
        List<Task> baseTasks = buf.readableBytes() > 0 ? readTaskList(buf) : null;
        server.execute(() -> handleTeamReplaceTasks(server, player, tasks, baseTasks));
    }

    public static void onTeamRequestSyncPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        server.execute(() -> broadcastTeamTasks(server));
    }

    public static void onAddTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        Task task = readTask(buf);
        server.execute(() -> handleAddTask(player, task));
    }

    public static void onUpdateTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        Task task = readTask(buf);
        server.execute(() -> handleUpdateTask(player, task));
    }

    public static void onDeleteTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        UUID taskId = buf.readUUID();
        server.execute(() -> handleDeleteTask(player, taskId));
    }

    public static void onToggleTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        UUID taskId = buf.readUUID();
        server.execute(() -> handleToggleTask(player, taskId));
    }

    public static void onTeamToggleTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        UUID taskId = buf.readUUID();
        server.execute(() -> handleTeamToggleTask(server, taskId));
    }

    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        server.execute(() -> {
            syncTasksToPlayer(player);
            syncTeamTasksToPlayer(player);
        });
    }

    /**
     * 将服务端当前玩家个人任务列表同步到该玩家客户端。
     */
    public static void syncTasksToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            MinecraftServer server = player.getServer();
            java.util.UUID playerUuid = player.getUUID();
            List<Task> tasks = storage.loadPersonalTasks(server, playerUuid);
            if (!storage.shouldUseLocalPersonalStorage(server)) {
                boolean playerFileExists = storage.hasPlayerTasks(playerUuid);
                List<Task> fallback = storage.loadTasks();
                if (!playerFileExists) {
                    if (!fallback.isEmpty()) {
                        tasks = fallback;
                        H2MaintenanceGuard.ensureWritableIfH2();
                        storage.savePlayerTasks(playerUuid, tasks);
                        TodoConstants.LOGGER.info("Migrated {} tasks from local storage to player file {}", tasks.size(), playerUuid);
                    }
                } else if (!fallback.isEmpty()) {
                    long playerLastSaved = storage.getPersonalTasksLastSaved(server, playerUuid);
                    long localLastSaved = storage.getLocalTasksLastSaved();
                    if (localLastSaved > playerLastSaved) {
                        tasks = fallback;
                        H2MaintenanceGuard.ensureWritableIfH2();
                        storage.savePlayerTasks(playerUuid, tasks);
                        TodoConstants.LOGGER.info("Recovered newer local tasks for player file {}, localLastSaved={}, playerLastSaved={}, taskCount={}",
                                playerUuid, localLastSaved, playerLastSaved, tasks.size());
                    }
                }
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            writeTaskList(buf, tasks);
            serverPacketSender.send(player, SYNC_TASKS_ID, buf);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load player tasks for sync", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    private static void syncTeamTasksToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadTeamTasks();
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            writeTaskList(buf, tasks);
            serverPacketSender.send(player, TEAM_SYNC_TASKS_ID, buf);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for sync", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    private static void handleReplaceTasks(ServerPlayer player, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            H2MaintenanceGuard.ensureWritableIfH2();
            storage.savePersonalTasks(player.getServer(), player.getUUID(), tasks);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save player tasks", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    private static void handleTeamReplaceTasks(MinecraftServer server, ServerPlayer player, List<Task> tasks, List<Task> baseTasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            H2MaintenanceGuard.ensureWritableIfH2();
            List<Task> tasksToSave = baseTasks == null ? tasks : mergeTeamTasks(storage.loadTeamTasks(), baseTasks, tasks);
            storage.saveTeamTasks(tasksToSave);
            if (baseTasks == null) {
                broadcastTeamTasksExcept(server, player);
            } else {
                broadcastTeamTasks(server);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save team tasks", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    /**
     * 基于客户端基线合并团队任务，避免旧整表快照覆盖其他玩家期间提交的改动。
     *
     * @param currentTasks 服务端当前任务列表
     * @param baseTasks 客户端保存发起时的同步基线
     * @param submittedTasks 客户端提交的最新任务列表
     * @return 合并后的任务列表
     */
    private static List<Task> mergeTeamTasks(List<Task> currentTasks, List<Task> baseTasks, List<Task> submittedTasks) {
        Map<String, Task> baseById = mapTasksById(baseTasks);
        Map<String, Task> submittedById = mapTasksById(submittedTasks);
        Map<String, Task> currentById = mapTasksById(currentTasks);
        List<Task> merged = new ArrayList<>();

        for (Task currentTask : currentTasks) {
            Task baseTask = baseById.get(currentTask.getId());
            Task submittedTask = submittedById.get(currentTask.getId());
            if (baseTask != null && submittedTask == null) {
                if (!tasksEquivalent(baseTask, currentTask)) {
                    merged.add(copyTask(currentTask));
                }
                continue;
            }
            if (submittedTask != null && !tasksEquivalent(baseTask, submittedTask)) {
                merged.add(copyTask(submittedTask));
            } else {
                merged.add(copyTask(currentTask));
            }
        }

        for (Task submittedTask : submittedTasks) {
            if (currentById.containsKey(submittedTask.getId())) {
                continue;
            }
            Task baseTask = baseById.get(submittedTask.getId());
            if (baseTask == null || !tasksEquivalent(baseTask, submittedTask)) {
                merged.add(copyTask(submittedTask));
            }
        }

        return merged;
    }

    /**
     * 按任务 ID 构建任务映射，并忽略空任务。
     *
     * @param tasks 原始任务列表
     * @return 任务 ID 到任务对象的映射
     */
    private static Map<String, Task> mapTasksById(List<Task> tasks) {
        Map<String, Task> byId = new LinkedHashMap<>();
        for (Task task : tasks == null ? List.<Task>of() : tasks) {
            if (task != null && task.getId() != null) {
                byId.put(task.getId(), task);
            }
        }
        return byId;
    }

    /**
     * 判断两个任务的可持久化内容是否一致。
     *
     * @param left 左侧任务
     * @param right 右侧任务
     * @return 内容一致时返回 true
     */
    private static boolean tasksEquivalent(Task left, Task right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.toNbt(), right.toNbt());
    }

    /**
     * 深拷贝任务对象，避免合并结果与调用方列表共享可变实例。
     *
     * @param task 原始任务
     * @return 拷贝后的任务
     */
    private static Task copyTask(Task task) {
        return Task.fromNbt(task.toNbt());
    }

    private static void handleAddTask(ServerPlayer player, Task task) {
        // Implementation for adding a task
    }

    private static void handleUpdateTask(ServerPlayer player, Task task) {
        // Implementation for updating a task
    }

    private static void handleDeleteTask(ServerPlayer player, UUID taskId) {
        // Implementation for deleting a task
    }

    private static void handleToggleTask(ServerPlayer player, UUID taskId) {
        // Implementation for toggling a task
    }

    private static void handleTeamToggleTask(MinecraftServer server, UUID taskId) {
        // Implementation for toggling a team task
    }

    /**
     * 将服务端当前团队任务列表广播给所有在线玩家。
     */
    public static void broadcastTeamTasks(MinecraftServer server) {
        broadcastTeamTasksExcept(server, null);
    }

    /**
     * 将服务端当前团队任务列表广播给除指定玩家以外的在线玩家。
     *
     * @param server 当前服务端
     * @param excludedPlayer 不需要接收本次广播的玩家；为 null 时广播给所有人
     */
    private static void broadcastTeamTasksExcept(MinecraftServer server, ServerPlayer excludedPlayer) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadTeamTasks();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (excludedPlayer != null && player != null && player.getUUID().equals(excludedPlayer.getUUID())) {
                    continue;
                }
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                writeTaskList(buf, tasks);
                serverPacketSender.send(player, TEAM_SYNC_TASKS_ID, buf);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for broadcast", e);
            if (StorageFailureNotifier.isStorageUnavailable(e)) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
                }
            }
        }
    }

    /**
     * 将单个任务写入网络缓冲区。
     */
    public static void writeTask(FriendlyByteBuf buf, Task task) {
        buf.writeNbt(task.toNbt());
    }

    /**
     * 从网络缓冲区读取单个任务。
     */
    public static Task readTask(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        return Task.fromNbt(nbt);
    }

    /**
     * 将任务列表写入网络缓冲区。
     */
    public static void writeTaskList(FriendlyByteBuf buf, List<Task> tasks) {
        buf.writeInt(tasks.size());
        for (Task task : tasks) {
            writeTask(buf, task);
        }
    }

    /**
     * 从网络缓冲区读取任务列表。
     */
    public static List<Task> readTaskList(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Task> tasks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tasks.add(readTask(buf));
        }
        return tasks;
    }

    @FunctionalInterface
    public interface ServerPacketSender {
        void send(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf);
    }
}
