package com.todolist.client;

import com.todolist.TodoListForge;
import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.network.TaskPackets;
import com.todolist.task.Task;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public final class ForgeClientTaskPackets {
    private ForgeClientTaskPackets() {
    }

    public static void registerClientPackets() {
        ForgeNetworkBridge.registerClientReceiver(TaskPackets.SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = TaskPackets.readTaskList(buf);
            client.execute(() -> {
                try {
                    TodoListForge.getTaskStorage().saveTasks(tasks);
                    TodoListForge.LOGGER.info("Received {} tasks from server, saved to local storage", tasks.size());
                } catch (Exception e) {
                    TodoListForge.LOGGER.error("Failed to save synced tasks on Forge client", e);
                }
            });
        });

        ForgeNetworkBridge.registerClientReceiver(TaskPackets.TEAM_SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = TaskPackets.readTaskList(buf);
            client.execute(() -> {
                ForgeTodoClient.updateTeamTasksFromServer(tasks);
                TodoListForge.LOGGER.info("Received {} team tasks from server", tasks.size());
            });
        });

        ForgeNetworkBridge.registerClientReceiver(TaskPackets.TASK_CONFIRMED_ID, (client, handler, buf, responseSender) -> {
            String action = buf.readUtf();
            String taskId = buf.readUtf();
            boolean success = buf.readBoolean();
            client.execute(() -> TodoListForge.LOGGER.info("Task {} {} (id={})", action, success ? "succeeded" : "failed", taskId));
        });
    }

    public static void sendReplaceAllTasks(List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ForgeNetworkBridge.canSend(TaskPackets.REPLACE_TASKS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        ForgeNetworkBridge.sendToServer(TaskPackets.REPLACE_TASKS_ID, buf);
    }

    public static void sendReplaceTeamTasks(List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ForgeNetworkBridge.canSend(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        ForgeNetworkBridge.sendToServer(TaskPackets.TEAM_REPLACE_TASKS_ID, buf);
    }

    public static void requestTeamSync() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ForgeNetworkBridge.sendToServer(TaskPackets.TEAM_REQUEST_SYNC_ID, buf);
    }

    public static void sendUpdateTask(Task task) {
        if (task == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ForgeNetworkBridge.canSend(TaskPackets.UPDATE_TASK_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTask(buf, task);
        ForgeNetworkBridge.sendToServer(TaskPackets.UPDATE_TASK_ID, buf);
    }
}
