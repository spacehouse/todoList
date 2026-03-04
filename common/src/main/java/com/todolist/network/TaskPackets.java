package com.todolist.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 澶勭悊浠诲姟鍚屾鐨勭綉缁滄暟鎹寘銆? */
public class TaskPackets {
    public static final Identifier SYNC_TASKS_ID = new Identifier(TodoConstants.MOD_ID, "sync_tasks");
    public static final Identifier TEAM_SYNC_TASKS_ID = new Identifier(TodoConstants.MOD_ID, "team_sync_tasks");
    public static final Identifier TASK_CONFIRMED_ID = new Identifier(TodoConstants.MOD_ID, "task_confirmed");
    public static final Identifier REPLACE_TASKS_ID = new Identifier(TodoConstants.MOD_ID, "replace_tasks");
    public static final Identifier TEAM_REPLACE_TASKS_ID = new Identifier(TodoConstants.MOD_ID, "team_replace_tasks");
    public static final Identifier TEAM_REQUEST_SYNC_ID = new Identifier(TodoConstants.MOD_ID, "team_request_sync");
    public static final Identifier ADD_TASK_ID = new Identifier(TodoConstants.MOD_ID, "add_task");
    public static final Identifier UPDATE_TASK_ID = new Identifier(TodoConstants.MOD_ID, "update_task");
    public static final Identifier DELETE_TASK_ID = new Identifier(TodoConstants.MOD_ID, "delete_task");
    public static final Identifier TOGGLE_TASK_ID = new Identifier(TodoConstants.MOD_ID, "toggle_task");
    public static final Identifier TEAM_TOGGLE_TASK_ID = new Identifier(TodoConstants.MOD_ID, "team_toggle_task");
    public static final Identifier TEAM_ASSIGN_TASK_ID = new Identifier(TodoConstants.MOD_ID, "team_assign_task");

    public static void registerServerPackets() {
        // REPLACE_TASKS
        ServerPlayNetworking.registerGlobalReceiver(REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) -> {
            List<Task> tasks = readTaskList(buf);
            server.execute(() -> handleReplaceTasks(player, tasks));
        });

        // TEAM_REPLACE_TASKS
        ServerPlayNetworking.registerGlobalReceiver(TEAM_REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) -> {
            List<Task> tasks = readTaskList(buf);
            server.execute(() -> handleTeamReplaceTasks(server, tasks));
        });

        // TEAM_REQUEST_SYNC
        ServerPlayNetworking.registerGlobalReceiver(TEAM_REQUEST_SYNC_ID, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> broadcastTeamTasks(server));
        });

        // ADD_TASK
        ServerPlayNetworking.registerGlobalReceiver(ADD_TASK_ID, (server, player, handler, buf, responseSender) -> {
            Task task = readTask(buf);
            server.execute(() -> handleAddTask(player, task));
        });

        // UPDATE_TASK
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            Task task = readTask(buf);
            server.execute(() -> handleUpdateTask(player, task));
        });

        // DELETE_TASK
        ServerPlayNetworking.registerGlobalReceiver(DELETE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            UUID taskId = buf.readUuid();
            server.execute(() -> handleDeleteTask(player, taskId));
        });

        // TOGGLE_TASK
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            UUID taskId = buf.readUuid();
            server.execute(() -> handleToggleTask(player, taskId));
        });

        // TEAM_TOGGLE_TASK
        ServerPlayNetworking.registerGlobalReceiver(TEAM_TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            UUID taskId = buf.readUuid();
            server.execute(() -> handleTeamToggleTask(server, taskId));
        });
    }

    private static void handleReplaceTasks(ServerPlayerEntity player, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.savePlayerTasks(player.getUuid(), tasks);
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

    private static void handleAddTask(ServerPlayerEntity player, Task task) {
        // Implementation for adding a task
    }

    private static void handleUpdateTask(ServerPlayerEntity player, Task task) {
        // Implementation for updating a task
    }

    private static void handleDeleteTask(ServerPlayerEntity player, UUID taskId) {
        // Implementation for deleting a task
    }

    private static void handleToggleTask(ServerPlayerEntity player, UUID taskId) {
        // Implementation for toggling a task
    }

    private static void handleTeamToggleTask(MinecraftServer server, UUID taskId) {
        // Implementation for toggling a team task
    }

    public static void broadcastTeamTasks(MinecraftServer server) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadTeamTasks();
            PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            writeTaskList(buf, tasks);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, TEAM_SYNC_TASKS_ID, buf);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for broadcast", e);
        }
    }

    public static void writeTask(PacketByteBuf buf, Task task) {
        buf.writeNbt(task.toNbt());
    }

    public static Task readTask(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        return Task.fromNbt(nbt);
    }

    public static void writeTaskList(PacketByteBuf buf, List<Task> tasks) {
        buf.writeInt(tasks.size());
        for (Task task : tasks) {
            writeTask(buf, task);
        }
    }

    public static List<Task> readTaskList(PacketByteBuf buf) {
        int size = buf.readInt();
        List<Task> tasks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tasks.add(readTask(buf));
        }
        return tasks;
    }
}


