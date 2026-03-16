package com.todolist.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 任务同步相关的网络包定义与服务端处理逻辑。
 */
public class TaskPackets {
    public static final ResourceLocation SYNC_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "sync_tasks");
    public static final ResourceLocation TEAM_SYNC_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_sync_tasks");
    public static final ResourceLocation TASK_CONFIRMED_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "task_confirmed");
    public static final ResourceLocation REPLACE_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "replace_tasks");
    public static final ResourceLocation TEAM_REPLACE_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_replace_tasks");
    public static final ResourceLocation TEAM_REQUEST_SYNC_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_request_sync");
    public static final ResourceLocation ADD_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "add_task");
    public static final ResourceLocation UPDATE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "update_task");
    public static final ResourceLocation DELETE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "delete_task");
    public static final ResourceLocation TOGGLE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "toggle_task");
    public static final ResourceLocation TEAM_TOGGLE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_toggle_task");
    public static final ResourceLocation TEAM_ASSIGN_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_assign_task");
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
        server.execute(() -> handleTeamReplaceTasks(server, tasks));
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
            java.util.UUID playerUuid = player.getUUID();
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            boolean playerFileExists = storage.hasPlayerTasks(playerUuid);
            List<Task> fallback = storage.loadTasks();
            if (!playerFileExists) {
                if (!fallback.isEmpty()) {
                    tasks = fallback;
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoConstants.LOGGER.info("Migrated {} tasks from local storage to player file {}", tasks.size(), playerUuid);
                }
            } else if (!fallback.isEmpty()) {
                long playerLastSaved = storage.getPlayerTasksLastSaved(playerUuid);
                long localLastSaved = storage.getLocalTasksLastSaved();
                if (localLastSaved > playerLastSaved) {
                    tasks = fallback;
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoConstants.LOGGER.info("Recovered newer local tasks for player file {}, localLastSaved={}, playerLastSaved={}, taskCount={}",
                            playerUuid, localLastSaved, playerLastSaved, tasks.size());
                }
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            writeTaskList(buf, tasks);
            serverPacketSender.send(player, SYNC_TASKS_ID, buf);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load player tasks for sync", e);
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
        }
    }

    private static void handleReplaceTasks(ServerPlayer player, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.savePlayerTasks(player.getUUID(), tasks);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save player tasks", e);
        }
    }

    private static void handleTeamReplaceTasks(MinecraftServer server, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.saveTeamTasks(tasks);
            broadcastTeamTasks(server);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save team tasks", e);
        }
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
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadTeamTasks();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                writeTaskList(buf, tasks);
                serverPacketSender.send(player, TEAM_SYNC_TASKS_ID, buf);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for broadcast", e);
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
