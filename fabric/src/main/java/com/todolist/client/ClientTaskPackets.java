package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.network.TaskPackets;
import com.todolist.task.Task;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.util.List;

/**
 * Fabric 客户端侧任务相关网络包处理与发送工具类。
 */
public class ClientTaskPackets {
    /**
     * 注册客户端接收的任务相关网络包处理器。
     */
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

    /**
     * 向服务端发送“用本地列表替换个人任务”的请求。
     *
     * @param tasks 需要替换的任务列表
     */
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

    /**
     * 向服务端发送“用本地列表替换团队任务”的请求。
     *
     * @param tasks 需要替换的团队任务列表
     */
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

    /**
     * 向服务端请求同步团队任务列表。
     */
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

    /**
     * 向服务端发送新增任务请求（个人任务）。
     *
     * @param task 需要新增的任务
     */
    public static void sendAddTask(Task task) {
        if (task == null) {
            return;
        }
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

    /**
     * 向服务端发送更新任务请求（个人任务）。
     *
     * @param task 需要更新的任务
     */
    public static void sendUpdateTask(Task task) {
        if (task == null) {
            return;
        }
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

    /**
     * 向服务端发送删除任务请求（个人任务）。
     *
     * @param taskId 任务 ID
     */
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

    /**
     * 向服务端发送切换任务完成状态请求（个人任务）。
     *
     * @param taskId 任务 ID
     */
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

    /**
     * 向服务端发送切换任务完成状态请求（团队任务）。
     *
     * @param taskId 团队任务 ID
     */
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

    /**
     * 向服务端发送指派团队任务请求。
     *
     * @param taskId        团队任务 ID
     * @param assigneeUuid  被指派玩家 UUID，为空表示取消指派
     */
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
