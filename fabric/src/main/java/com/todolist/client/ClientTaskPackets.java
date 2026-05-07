package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.gui.TodoScreen;
import com.todolist.network.FabricTaskPayload;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 处理 Fabric 客户端侧任务相关网络包的接收与发送。
 */
public class ClientTaskPackets {
    /**
     * 注册客户端接收的任务相关网络包处理器。
     */
    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(FabricTaskPayload.TYPE, (payload, context) -> {
            ResourceLocation channelId = payload.channel();
            FriendlyByteBuf buf = payload.toBuf();
            Minecraft client = context.client();
            if (channelId.equals(TaskPackets.SYNC_TASKS_ID)) {
                List<Task> tasks = TaskPackets.readTaskList(buf);
                String namespaceAtReceive = DataPathProvider.getStorageNamespace();
                client.execute(() -> {
                    if (!namespaceAtReceive.equals(DataPathProvider.getStorageNamespace())) {
                        TodoListMod.LOGGER.info("Skip stale task sync write due to namespace switch: {} -> {}",
                                namespaceAtReceive, DataPathProvider.getStorageNamespace());
                        return;
                    }
                    if (TodoScreen.hasPersonalUnsavedChanges()) {
                        TodoListMod.LOGGER.info("Skip Fabric personal task sync write because local personal tasks are unsaved");
                        return;
                    }
                    try {
                        ClientTaskStorageHelper.savePersonalTasks(TodoListMod.getTaskStorage(), client, tasks);
                        TodoScreen.applySyncedPersonalTasks(client, tasks);
                        TodoListMod.LOGGER.info("Received {} tasks from server, saved to local storage", tasks.size());
                    } catch (Exception e) {
                        TodoListMod.LOGGER.error("Failed to save synced tasks on client", e);
                    }
                });
                return;
            }
            if (channelId.equals(TaskPackets.TEAM_SYNC_TASKS_ID)) {
                List<Task> tasks = TaskPackets.readTaskList(buf);
                client.execute(() -> {
                    TodoClient.updateTeamTasksFromServer(tasks);
                    TodoScreen.applySyncedTeamTasks(client);
                    TodoListMod.LOGGER.info("Received {} team tasks from server", tasks.size());
                });
                return;
            }
            if (channelId.equals(TaskPackets.TASK_CONFIRMED_ID)) {
                String action = buf.readUtf();
                String taskId = buf.readUtf();
                boolean success = buf.readBoolean();
                client.execute(() ->
                        TodoListMod.LOGGER.info("Task {} {} (id={})", action, success ? "succeeded" : "failed", taskId));
            }
        });
    }

    /**
     * 向服务端发送“用本地列表替换个人任务”的请求。
     *
     * @param tasks 需要替换的任务列表
     */
    public static void sendReplaceAllTasks(List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.REPLACE_TASKS_ID, buf));
    }

    /**
     * 向服务端发送“用本地列表替换团队任务”的请求。
     *
     * @param tasks 需要替换的团队任务列表
     */
    public static void sendReplaceTeamTasks(List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.TEAM_REPLACE_TASKS_ID, buf));
    }

    /**
     * 向服务端请求同步团队任务列表。
     */
    public static void requestTeamSync() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.TEAM_REQUEST_SYNC_ID, buf));
    }

    /**
     * 向服务端发送新增任务请求。
     *
     * @param task 需要新增的任务
     */
    public static void sendAddTask(Task task) {
        if (task == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTask(buf, task);
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.ADD_TASK_ID, buf));
    }

    /**
     * 向服务端发送更新任务请求。
     *
     * @param task 需要更新的任务
     */
    public static void sendUpdateTask(Task task) {
        if (task == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTask(buf, task);
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.UPDATE_TASK_ID, buf));
    }

    /**
     * 向服务端发送删除任务请求。
     *
     * @param taskId 任务 ID
     */
    public static void sendDeleteTask(String taskId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(taskId);
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.DELETE_TASK_ID, buf));
    }

    /**
     * 向服务端发送切换任务完成状态请求。
     *
     * @param taskId 任务 ID
     */
    public static void sendToggleTask(String taskId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(taskId);
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.TOGGLE_TASK_ID, buf));
    }

    /**
     * 向服务端发送切换团队任务完成状态请求。
     *
     * @param taskId 团队任务 ID
     */
    public static void sendToggleTeamTask(String taskId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(taskId);
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.TEAM_TOGGLE_TASK_ID, buf));
    }

    /**
     * 向服务端发送指派团队任务请求。
     *
     * @param taskId 团队任务 ID
     * @param assigneeUuid 被指派玩家 UUID，为空表示取消指派
     */
    public static void sendAssignTeamTask(String taskId, String assigneeUuid) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(FabricTaskPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(taskId);
        if (assigneeUuid == null || assigneeUuid.isEmpty()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(assigneeUuid);
        }
        ClientPlayNetworking.send(FabricTaskPayload.of(TaskPackets.TEAM_ASSIGN_TASK_ID, buf));
    }
}
