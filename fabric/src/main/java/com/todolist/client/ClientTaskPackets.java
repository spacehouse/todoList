package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.gui.TodoScreen;
import com.todolist.network.TaskPacketChunking;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import java.util.List;

/**
 * Fabric 客户端侧任务相关网络包处理与发送工具类。
 */
public class ClientTaskPackets {

    /** 客户端分块累积器，用于接收服务端发来的分块团队任务包。 */
    private static final TaskPacketChunking.ChunkAccumulator clientChunkAccumulator = new TaskPacketChunking.ChunkAccumulator();

    /**
     * 注册客户端接收的任务相关网络包处理器。
     */
    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
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
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.TEAM_SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = TaskPackets.readTaskList(buf);
            client.execute(() -> {
                TodoScreen.applySyncedTeamTasks(client, tasks);
                TodoListMod.LOGGER.info("Received {} team tasks from server", tasks.size());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.TEAM_SYNC_TASKS_CHUNKED_ID, (client, handler, buf, responseSender) -> {
            TaskPacketChunking.ChunkData chunk = TaskPacketChunking.readChunk(buf);
            byte[] assembled = clientChunkAccumulator.accept(chunk);
            if (assembled == null) {
                return;
            }
            List<Task> tasks = TaskPacketChunking.deserializeTasks(assembled);
            client.execute(() -> {
                TodoScreen.applySyncedTeamTasks(client, tasks);
                TodoListMod.LOGGER.info("Received {} team tasks from server (chunked, {} bytes)", tasks.size(), assembled.length);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.TASK_CONFIRMED_ID, (client, handler, buf, responseSender) -> {
            String action = buf.readUtf();
            String taskId = buf.readUtf();
            boolean success = buf.readBoolean();

            client.execute(() -> {
                TodoListMod.LOGGER.info("Task {} {} (id={})", action, success ? "succeeded" : "failed", taskId);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.TRIGGER_COMPLETED_ID, (client, handler, buf, responseSender) -> {
            String title = buf.readUtf();
            client.execute(() -> TodoToastRenderer.show(title));
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskPackets.TRIGGER_PROGRESS_ID, (client, handler, buf, responseSender) -> {
            TaskPackets.TriggerProgressBatch batch = TaskPackets.readTriggerProgress(buf);
            client.execute(() -> TodoScreen.applyTriggerProgress(client, batch));
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
        if (!ClientPlayNetworking.canSend(TaskPackets.REPLACE_TASKS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        ClientPlayNetworking.send(TaskPackets.REPLACE_TASKS_ID, buf);
    }

    /**
     * 向服务端发送“用本地列表替换团队任务”的请求。
     * 自动检测负载大小，超过限制时使用分块通道。
     *
     * @param tasks 需要替换的团队任务列表
     */
    public static void sendReplaceTeamTasks(List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        byte[] data = TaskPacketChunking.serializeTasks(tasks);
        if (data.length <= TaskPacketChunking.MAX_CHUNK_BYTES) {
            if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
            ClientPlayNetworking.send(TaskPackets.TEAM_REPLACE_TASKS_ID, buf);
        } else {
            if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID)) {
                return;
            }
            sendChunkedReplaceTasks(data, null);
        }
    }

    /**
     * 向服务端发送“按基线合并团队任务”的请求。
     * 自动检测负载大小，超过限制时使用分块通道。
     *
     * @param baseTasks 保存发起时客户端已同步的团队任务基线
     * @param tasks 当前提交的团队任务列表
     */
    public static void sendMergeTeamTasks(List<Task> baseTasks, List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        byte[] data = TaskPacketChunking.serializeMergeTasks(tasks, baseTasks);
        if (data.length <= TaskPacketChunking.MAX_CHUNK_BYTES) {
            if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
            ClientPlayNetworking.send(TaskPackets.TEAM_REPLACE_TASKS_ID, buf);
        } else {
            if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID)) {
                return;
            }
            sendChunkedReplaceTasks(data, baseTasks);
        }
    }

    /**
     * 以分块方式向服务端发送团队任务替换/合并数据。
     *
     * @param data 完整序列化字节数组
     * @param baseTasksMarker 基线标记（非 null 表示这是 merge 请求，仅用于日志）
     */
    private static void sendChunkedReplaceTasks(byte[] data, List<Task> baseTasksMarker) {
        List<byte[]> chunks = TaskPacketChunking.splitPayload(data);
        String sessionId = TaskPacketChunking.newSessionId();
        TodoListMod.LOGGER.info("Sending chunked team tasks to server: {} chunks, {} bytes, merge={}",
                chunks.size(), data.length, baseTasksMarker != null);
        for (int i = 0; i < chunks.size(); i++) {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            TaskPacketChunking.writeChunk(buf, sessionId, chunks.size(), i, chunks.get(i));
            ClientPlayNetworking.send(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID, buf);
        }
    }

    /**
     * 向服务端请求同步团队任务列表。
     */
    public static void requestTeamSync() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.ADD_TASK_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
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
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.UPDATE_TASK_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTask(buf, task);
        ClientPlayNetworking.send(TaskPackets.UPDATE_TASK_ID, buf);
    }

    /**
     * 向服务端发送删除任务请求（个人任务）。
     *
     * @param taskId 任务 ID
     */
    public static void sendDeleteTask(String taskId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.DELETE_TASK_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(taskId);
        ClientPlayNetworking.send(TaskPackets.DELETE_TASK_ID, buf);
    }

    /**
     * 向服务端发送切换任务完成状态请求（个人任务）。
     *
     * @param taskId 任务 ID
     */
    public static void sendToggleTask(String taskId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TOGGLE_TASK_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(taskId);
        ClientPlayNetworking.send(TaskPackets.TOGGLE_TASK_ID, buf);
    }

    /**
     * 向服务端发送切换任务完成状态请求（团队任务）。
     *
     * @param taskId 团队任务 ID
     */
    public static void sendToggleTeamTask(String taskId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_TOGGLE_TASK_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(taskId);
        ClientPlayNetworking.send(TaskPackets.TEAM_TOGGLE_TASK_ID, buf);
    }

    /**
     * 向服务端发送指派团队任务请求。
     *
     * @param taskId        团队任务 ID
     * @param assigneeUuid  被指派玩家 UUID，为空表示取消指派
     */
    public static void sendAssignTeamTask(String taskId, String assigneeUuid) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!ClientPlayNetworking.canSend(TaskPackets.TEAM_ASSIGN_TASK_ID)) {
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
        ClientPlayNetworking.send(TaskPackets.TEAM_ASSIGN_TASK_ID, buf);
    }
}
