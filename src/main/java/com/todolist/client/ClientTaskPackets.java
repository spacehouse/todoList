package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.network.TaskPackets;
import com.todolist.task.Task;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.util.List;

public class ClientTaskPackets {
    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = TaskPackets.readTaskList(buf);
            client.execute(() -> {
                try {
                    TodoListMod.getTaskStorage().saveTasks(tasks);
                    TodoListMod.LOGGER.info("Received {} tasks from server, saved to local storage", tasks.size());
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to save synced tasks on client", e);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.TEAM_SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = TaskPackets.readTaskList(buf);
            client.execute(() -> {
                TodoClient.updateTeamTasksFromServer(tasks);
                TodoListMod.LOGGER.info("Received {} team tasks from server", tasks.size());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.TASK_CONFIRMED_ID, (client, handler, buf, responseSender) -> {
            String action = buf.readString();
            String taskId = buf.readString();
            boolean success = buf.readBoolean();

            client.execute(() -> {
                TodoListMod.LOGGER.info("Task {} {} (id={})", action, success ? "succeeded" : "failed", taskId);
            });
        });
    }

    public static void sendReplaceAllTasks(List<Task> tasks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.REPLACE_TASKS_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        ClientPlayNetworking.send(TaskPackets.REPLACE_TASKS_ID, buf);
    }

    public static void sendReplaceTeamTasks(List<Task> tasks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        ClientPlayNetworking.send(TaskPackets.TEAM_REPLACE_TASKS_ID, buf);
    }

    public static void requestTeamSync() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        ClientPlayNetworking.send(TaskPackets.TEAM_REQUEST_SYNC_ID, buf);
    }

    public static void sendAddTask(Task task) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.ADD_TASK_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTask(buf, task);
        ClientPlayNetworking.send(TaskPackets.ADD_TASK_ID, buf);
    }

    public static void sendUpdateTask(Task task) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.UPDATE_TASK_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTask(buf, task);
        ClientPlayNetworking.send(TaskPackets.UPDATE_TASK_ID, buf);
    }

    public static void sendDeleteTask(String taskId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.DELETE_TASK_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(taskId);
        ClientPlayNetworking.send(TaskPackets.DELETE_TASK_ID, buf);
    }

    public static void sendToggleTask(String taskId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TOGGLE_TASK_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(taskId);
        ClientPlayNetworking.send(TaskPackets.TOGGLE_TASK_ID, buf);
    }

    public static void sendToggleTeamTask(String taskId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_TOGGLE_TASK_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(taskId);
        ClientPlayNetworking.send(TaskPackets.TEAM_TOGGLE_TASK_ID, buf);
    }

    public static void sendAssignTeamTask(String taskId, String assigneeUuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_ASSIGN_TASK_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(taskId);
        if (assigneeUuid == null || assigneeUuid.isEmpty()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeString(assigneeUuid);
        }
        ClientPlayNetworking.send(TaskPackets.TEAM_ASSIGN_TASK_ID, buf);
    }
}
