package com.todolist.client;

import com.todolist.TodoListNeoForge;
import com.todolist.client.AdvancementCatalog;
import com.todolist.gui.TodoScreen;
import com.todolist.neoforge.network.NeoForgeNetworkBridge;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.client.TodoToastRenderer;
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
    /**
     * 向服务端请求目标物品的配方映射（材料反推，1.21.2+ 服务端权威通路）。
     *
     * @param itemId 目标物品资源 ID
     */
    public static void requestMaterialPlan(String itemId) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null || itemId == null || itemId.isEmpty()) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(itemId);
        NeoForgeNetworkBridge.sendToServer(TaskPackets.MATERIAL_PLAN_REQUEST_ID, buf);
    }

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
                if (hasPersonalUnsavedChanges()) {
                    TodoListNeoForge.LOGGER.info("Skip NeoForge personal task sync write because local personal tasks are unsaved");
                    return;
                }
                try {
                    ClientTaskStorageHelper.savePersonalTasks(TodoListNeoForge.getTaskStorage(), client, tasks);
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

        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.TRIGGER_COMPLETED_ID, (client, handler, buf, responseSender) -> {
            String title = buf.readUtf();
            if (com.todolist.HudProbe.enabled()) {
                com.todolist.HudProbe.log("neoforge client received TRIGGER_COMPLETED title={}", title);
            }
            client.execute(() -> TodoToastRenderer.show(title));
        });

        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.TRIGGER_PROGRESS_ID, (client, handler, buf, responseSender) -> {
            TaskPackets.TriggerProgressBatch batch = TaskPackets.readTriggerProgress(buf);
            if (com.todolist.HudProbe.enabled()) {
                com.todolist.HudProbe.log("neoforge client received TRIGGER_PROGRESS entries={}", batch.entries().size());
            }
            client.execute(() -> TodoScreen.applyTriggerProgress(client, batch));
        });

        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.ADVANCEMENT_CATALOG_ID, (client, handler, buf, responseSender) -> {
            List<AdvancementCatalog.Entry> entries = TaskPackets.readAdvancementCatalog(buf);
            String namespaceAtReceive = DataPathProvider.getStorageNamespace();
            client.execute(() -> {
                AdvancementCatalog.apply(namespaceAtReceive, entries);
                TodoListNeoForge.LOGGER.info("Received {} advancements from server catalog", entries.size());
            });
        });
        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.MATERIAL_ITEMS_ID, (client, handler, buf, responseSender) -> {
            List<String> itemIds = TaskPackets.readMaterialItems(buf);
            client.execute(() -> com.todolist.material.ClientMaterialCatalog.apply(itemIds));
        });
        NeoForgeNetworkBridge.registerClientReceiver(TaskPackets.MATERIAL_PLAN_ID, (client, handler, buf, responseSender) -> {
            java.util.Map.Entry<String, List<com.todolist.material.MaterialRecipe>> plan = TaskPackets.readMaterialPlan(buf);
            client.execute(() -> TodoScreen.applyMaterialPlan(plan.getKey(), plan.getValue()));
        });
    }

    /**
     * 发送替换全部任务请求。
     *
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
     *
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
     *
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

    /**
     * 判断当前个人任务是否存在未保存修改。
     *
     * @return 存在未保存修改时返回 true
     */
    private static boolean hasPersonalUnsavedChanges() {
        return TodoScreen.hasPersonalUnsavedChanges();
    }
}
