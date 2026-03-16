package com.todolist.client;

import com.todolist.TodoListNeoForge;
import com.todolist.neoforge.network.NeoForgeNetworkBridge;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

/**
 * NeoForge 平台客户端任务数据包处理类。
 * 负责处理任务数据的发送与接收。
 */
public final class NeoForgeClientTaskPackets {
    /**
     * 私有构造函数，禁止实例化。
     */
    private NeoForgeClientTaskPackets() {
    }

    /**
     * 注册客户端接收的任务数据包。
     */
    public static void registerClientPackets() {
        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            if (handler == null) {
                TodoListNeoForge.LOGGER.info("Skip stale task sync packet with null NeoForge connection");
                return;
            }
            if (handler != client.getConnection()) {
                TodoListNeoForge.LOGGER.info("Skip stale task sync packet from old NeoForge connection");
                return;
            }
            List<Task> tasks = TaskPackets.readTaskList(buf);
            String namespaceAtReceive = DataPathProvider.getStorageNamespace();
            client.execute(() -> {
                String currentNamespace = DataPathProvider.getStorageNamespace();
                if (!namespaceAtReceive.equals(currentNamespace)) {
                    TodoListNeoForge.LOGGER.info("Skip stale task sync write due to namespace switch: {} -> {}",
                            namespaceAtReceive, currentNamespace);
                    return;
                }
                try {
                    TodoListNeoForge.getTaskStorage().saveTasks(tasks);
                    TodoListNeoForge.LOGGER.info("Received {} tasks from server, saved to local storage", tasks.size());
                } catch (Exception e) {
                    TodoListNeoForge.LOGGER.error("Failed to save synced tasks on NeoForge client", e);
                }
            });
        });

        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.TEAM_SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = TaskPackets.readTaskList(buf);
            client.execute(() -> {
                NeoForgeTodoClient.updateTeamTasksFromServer(tasks);
                TodoListNeoForge.LOGGER.info("Received {} team tasks from server", tasks.size());
            });
        });

        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.TASK_CONFIRMED_ID, (client, handler, buf, responseSender) -> {
            String action = buf.readUtf();
            String taskId = buf.readUtf();
            boolean success = buf.readBoolean();
            client.execute(() -> TodoListNeoForge.LOGGER.info("Task {} {} (id={})", action, success ? "succeeded" : "failed", taskId));
        });
    }

    /**
     * 发送替换全部任务请求。
     * @param tasks 任务列表
     */
    public static void sendReplaceAllTasks(List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!NeoForgeNetworkBridge.canSend(TaskPackets.REPLACE_TASKS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        NeoForgeNetworkBridge.sendToServer(TaskPackets.REPLACE_TASKS_ID, buf);
    }

    /**
     * 发送替换团队任务请求。
     * @param tasks 团队任务列表
     */
    public static void sendReplaceTeamTasks(List<Task> tasks) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!NeoForgeNetworkBridge.canSend(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        NeoForgeNetworkBridge.sendToServer(TaskPackets.TEAM_REPLACE_TASKS_ID, buf);
    }

    /**
     * 请求团队任务同步。
     */
    public static void requestTeamSync() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!NeoForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        NeoForgeNetworkBridge.sendToServer(TaskPackets.TEAM_REQUEST_SYNC_ID, buf);
    }

    /**
     * 发送更新任务请求。
     * @param task 任务
     */
    public static void sendUpdateTask(Task task) {
        if (task == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        if (!NeoForgeNetworkBridge.canSend(TaskPackets.UPDATE_TASK_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTask(buf, task);
        NeoForgeNetworkBridge.sendToServer(TaskPackets.UPDATE_TASK_ID, buf);
    }
}
