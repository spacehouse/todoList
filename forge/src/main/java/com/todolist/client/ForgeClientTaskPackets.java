package com.todolist.client;

import com.todolist.TodoListForge;
import com.todolist.gui.TodoScreen;
import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.network.TaskPacketChunking;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Forge 平台客户端任务相关数据包处理类。
 * 负责处理任务数据的发送与接收。
 */
public final class ForgeClientTaskPackets {
    private ForgeClientTaskPackets() {
    }

    /** Forge 客户端分块累积器，用于接收服务端发来的分块团队任务包。 */
    private static final TaskPacketChunking.ChunkAccumulator clientChunkAccumulator = new TaskPacketChunking.ChunkAccumulator();

    /**
     * 注册客户端接收的数据包处理器。
     */
    public static void registerClientPackets() {
        ForgeNetworkBridge.registerClientReceiver(TaskPackets.SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            if (handler == null) {
                TodoListForge.LOGGER.info("Skip stale task sync packet with null Forge connection");
                return;
            }
            if (handler != client.getConnection()) {
                TodoListForge.LOGGER.info("Skip stale task sync packet from old Forge connection");
                return;
            }
            List<Task> tasks = TaskPackets.readTaskList(buf);
            String namespaceAtReceive = DataPathProvider.getStorageNamespace();
            client.execute(() -> {
                String currentNamespace = DataPathProvider.getStorageNamespace();
                if (!namespaceAtReceive.equals(currentNamespace)) {
                    TodoListForge.LOGGER.info("Skip stale task sync write due to namespace switch: {} -> {}",
                            namespaceAtReceive, currentNamespace);
                    return;
                }
                if (hasPersonalUnsavedChanges()) {
                    TodoListForge.LOGGER.info("Skip Forge personal task sync write because local personal tasks are unsaved");
                    return;
                }
                try {
                    ClientTaskStorageHelper.savePersonalTasks(TodoListForge.getTaskStorage(), client, tasks);
                    TodoScreen.applySyncedPersonalTasks(client, tasks);
                    TodoListForge.LOGGER.info("Received {} tasks from server, saved to local storage", tasks.size());
                } catch (Exception e) {
                    TodoListForge.LOGGER.error("Failed to save synced tasks on Forge client", e);
                }
            });
        });

        ForgeNetworkBridge.registerClientReceiver(TaskPackets.TEAM_SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = TaskPackets.readTaskList(buf);
            client.execute(() -> {
                TodoScreen.applySyncedTeamTasks(client, tasks);
                TodoListForge.LOGGER.info("Received {} team tasks from server", tasks.size());
            });
        });

        ForgeNetworkBridge.registerClientReceiver(TaskPackets.TEAM_SYNC_TASKS_CHUNKED_ID, (client, handler, buf, responseSender) -> {
            TaskPacketChunking.ChunkData chunk = TaskPacketChunking.readChunk(buf);
            byte[] assembled = clientChunkAccumulator.accept(chunk);
            if (assembled == null) {
                return;
            }
            List<Task> tasks = TaskPacketChunking.deserializeTasks(assembled);
            client.execute(() -> {
                TodoScreen.applySyncedTeamTasks(client, tasks);
                TodoListForge.LOGGER.info("Received {} team tasks from server (chunked, {} bytes)", tasks.size(), assembled.length);
            });
        });

        ForgeNetworkBridge.registerClientReceiver(TaskPackets.TASK_CONFIRMED_ID, (client, handler, buf, responseSender) -> {
            String action = buf.readUtf();
            String taskId = buf.readUtf();
            boolean success = buf.readBoolean();
            client.execute(() -> TodoListForge.LOGGER.info("Task {} {} (id={})", action, success ? "succeeded" : "failed", taskId));
        });
    }

    /**
     * 发送替换所有个人任务请求。
     */
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

    /**
     * 发送替换团队任务请求。
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
            if (!ForgeNetworkBridge.canSend(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
            ForgeNetworkBridge.sendToServer(TaskPackets.TEAM_REPLACE_TASKS_ID, buf);
        } else {
            if (!ForgeNetworkBridge.canSend(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID)) {
                return;
            }
            sendChunkedReplaceTasks(data, null);
        }
    }

    /**
     * 发送带基线快照的团队任务合并请求。
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
            if (!ForgeNetworkBridge.canSend(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
            ForgeNetworkBridge.sendToServer(TaskPackets.TEAM_REPLACE_TASKS_ID, buf);
        } else {
            if (!ForgeNetworkBridge.canSend(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID)) {
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
        TodoListForge.LOGGER.info("Sending chunked team tasks to server: {} chunks, {} bytes, merge={}",
                chunks.size(), data.length, baseTasksMarker != null);
        for (int i = 0; i < chunks.size(); i++) {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            TaskPacketChunking.writeChunk(buf, sessionId, chunks.size(), i, chunks.get(i));
            ForgeNetworkBridge.sendToServer(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID, buf);
        }
    }

    /**
     * 请求团队任务同步。
     */
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

    /**
     * 发送更新任务请求。
     */
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

    private static boolean hasPersonalUnsavedChanges() {
        return TodoScreen.hasPersonalUnsavedChanges();
    }
}
