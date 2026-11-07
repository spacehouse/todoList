package com.todolist.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.AdvancementCatalog;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.storage.H2MaintenanceGuard;
import com.todolist.storage.StorageFailureNotifier;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import com.todolist.task.TaskTrigger;
import com.todolist.trigger.TaskTriggerService;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** 服务端→客户端：分块下发团队任务。 */
    public static final ResourceLocation TEAM_SYNC_TASKS_CHUNKED_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_sync_tasks_chunked");
    /** 客户端→服务端：分块提交团队任务替换/合并。 */
    public static final ResourceLocation TEAM_REPLACE_TASKS_CHUNKED_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_replace_tasks_chunked");
    /** 服务端→客户端：事件触发器自动完成任务提示（携带任务标题原文）。 */
    public static final ResourceLocation TRIGGER_COMPLETED_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "trigger_completed");
    /** 服务端→客户端：触发器进度轻量推送（仅变化任务 ID/进度/完成态，避免全量快照的网络与落库开销）。 */
    public static final ResourceLocation TRIGGER_PROGRESS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "trigger_progress");
    /** 服务端→客户端：服务端权威进度目录（供「选择进度」列出全部进度，而非仅玩家可见进度）。 */
    public static final ResourceLocation ADVANCEMENT_CATALOG_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "advancement_catalog");
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
        List<Task> baseTasks = buf.readableBytes() > 0 ? readTaskList(buf) : null;
        server.execute(() -> handleTeamReplaceTasks(server, player, tasks, baseTasks));
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
            sendAdvancementCatalog(player);
        });
    }

    /**
     * 向玩家下发服务端权威进度目录。
     * 客户端自带的进度列表只含「已解锁 / 可见」进度，无法用于选择任意进度作为触发目标，
     * 因此这里把服务端全部带展示信息的进度（ID + 展示名）一次性下发，由客户端缓存。
     *
     * @param player 目标玩家
     */
    public static void sendAdvancementCatalog(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeAdvancementCatalog(buf, collectAdvancementCatalog(server));
        serverPacketSender.send(player, ADVANCEMENT_CATALOG_ID, buf);
    }

    /**
     * 汇总服务端全部带展示信息的进度。
     * 无展示信息的进度（配方解锁等）不作为可选目标，直接跳过。
     *
     * @param server 当前服务端
     * @return 进度目录条目
     */
    private static List<AdvancementCatalog.Entry> collectAdvancementCatalog(MinecraftServer server) {
        List<AdvancementCatalog.Entry> entries = new ArrayList<>();
        if (server.getAdvancements() == null) {
            return entries;
        }
        for (net.minecraft.advancements.AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (holder == null || holder.value() == null || holder.value().display().isEmpty()) {
                continue;
            }
            entries.add(new AdvancementCatalog.Entry(
                    holder.id().toString(),
                    holder.value().display().get().getTitle().getString()));
        }
        return entries;
    }

    /**
     * 写出进度目录。
     *
     * @param buf     目标缓冲
     * @param entries 进度目录条目
     */
    public static void writeAdvancementCatalog(FriendlyByteBuf buf, List<AdvancementCatalog.Entry> entries) {
        List<AdvancementCatalog.Entry> valid = entries == null ? List.of() : entries;
        buf.writeVarInt(valid.size());
        for (AdvancementCatalog.Entry entry : valid) {
            buf.writeUtf(entry.id());
            buf.writeUtf(entry.title());
        }
    }

    /**
     * 读取进度目录。
     *
     * @param buf 来源缓冲
     * @return 进度目录条目
     */
    public static List<AdvancementCatalog.Entry> readAdvancementCatalog(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<AdvancementCatalog.Entry> entries = new ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            entries.add(new AdvancementCatalog.Entry(buf.readUtf(), buf.readUtf()));
        }
        return entries;
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
            MinecraftServer server = player.level().getServer();
            java.util.UUID playerUuid = player.getUUID();
            List<Task> tasks = storage.loadPersonalTasks(server, playerUuid);
            if (!storage.shouldUseLocalPersonalStorage(server)) {
                boolean playerFileExists = storage.hasPlayerTasks(playerUuid);
                List<Task> fallback = storage.loadTasks();
                if (!playerFileExists) {
                    if (!fallback.isEmpty()) {
                        tasks = fallback;
                        H2MaintenanceGuard.ensureWritableIfH2();
                        storage.savePlayerTasks(playerUuid, tasks);
                        TodoConstants.LOGGER.info("Migrated {} tasks from local storage to player file {}", tasks.size(), playerUuid);
                    }
                } else if (!fallback.isEmpty()) {
                    long playerLastSaved = storage.getPersonalTasksLastSaved(server, playerUuid);
                    long localLastSaved = storage.getLocalTasksLastSaved();
                    if (localLastSaved > playerLastSaved) {
                        tasks = fallback;
                        H2MaintenanceGuard.ensureWritableIfH2();
                        storage.savePlayerTasks(playerUuid, tasks);
                        TodoConstants.LOGGER.info("Recovered newer local tasks for player file {}, localLastSaved={}, playerLastSaved={}, taskCount={}",
                                playerUuid, localLastSaved, playerLastSaved, tasks.size());
                    }
                }
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            writeTaskList(buf, tasks);
            serverPacketSender.send(player, SYNC_TASKS_ID, buf);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load player tasks for sync", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    /**
     * 用给定的内存任务快照同步个人任务到客户端，避免再次读取存储。
     * 供触发器引擎在进度变化时做短节流推送，让 HUD 即时反映进度。
     *
     * @param player 目标玩家
     * @param tasks  任务快照
     */
    public static void sendPersonalTasksSnapshot(ServerPlayer player, List<Task> tasks) {
        if (player == null || tasks == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeTaskList(buf, tasks);
        serverPacketSender.send(player, SYNC_TASKS_ID, buf);
    }

    /**
     * 用给定的内存任务快照广播团队任务到所有在线玩家，避免再次读取存储。
     *
     * @param server 当前服务端
     * @param tasks  团队任务快照
     */
    public static void broadcastTeamTasksSnapshot(MinecraftServer server, List<Task> tasks) {
        if (server == null || tasks == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTeamTasksToPlayer(player, tasks);
        }
    }

    /**
     * 向指定玩家发送团队任务列表，自动判断是否需要分块。
     *
     * @param player 目标玩家
     * @param tasks 团队任务列表
     */
    private static void sendTeamTasksToPlayer(ServerPlayer player, List<Task> tasks) {
        byte[] data = TaskPacketChunking.serializeTasks(tasks);
        if (data.length <= TaskPacketChunking.MAX_CHUNK_BYTES) {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
            serverPacketSender.send(player, TEAM_SYNC_TASKS_ID, buf);
        } else {
            sendChunkedTeamTasksToPlayer(player, data);
        }
    }

    /**
     * 以分块方式向玩家发送团队任务数据。
     *
     * @param player 目标玩家
     * @param data 完整序列化字节数组
     */
    private static void sendChunkedTeamTasksToPlayer(ServerPlayer player, byte[] data) {
        List<byte[]> chunks = TaskPacketChunking.splitPayload(data);
        String sessionId = TaskPacketChunking.newSessionId();
        TodoConstants.LOGGER.info("Sending chunked team tasks to player {}: {} chunks, {} bytes total",
                player.getName().getString(), chunks.size(), data.length);
        for (int i = 0; i < chunks.size(); i++) {
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            TaskPacketChunking.writeChunk(buf, sessionId, chunks.size(), i, chunks.get(i));
            serverPacketSender.send(player, TEAM_SYNC_TASKS_CHUNKED_ID, buf);
        }
    }

    /**
     * 通知玩家某任务的触发器已自动完成，供客户端绘制带物品图标的浮动提示。
     *
     * @param player 目标玩家
     * @param title  任务标题原文（可能包含物品标记）
     */
    public static void notifyTriggerCompleted(ServerPlayer player, String title) {
        if (player == null || title == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(title);
        serverPacketSender.send(player, TRIGGER_COMPLETED_ID, buf);
    }

    /**
     * 触发器进度推送条目：只承载发生变化的任务 ID、进度与完成态。
     * 相比全量任务快照，网络负载与客户端处理开销都与「变化任务数」成正比，
     * 不随任务总量增长，也不会触发客户端把全量任务重新写回本地存储。
     *
     * @param taskId    任务 ID
     * @param progress  触发器当前进度
     * @param completed 任务是否已完成
     */
    public record TriggerProgress(String taskId, int progress, boolean completed) {
    }

    /**
     * 触发器进度推送批次：区分个人/团队任务桶 + 变化条目。
     *
     * @param team    是否为团队任务桶
     * @param entries 进度变化条目
     */
    public record TriggerProgressBatch(boolean team, List<TriggerProgress> entries) {
    }

    /**
     * 向指定玩家推送触发器进度变化（个人任务桶）。
     *
     * @param player 目标玩家
     * @param tasks  进度发生变化的任务集合
     */
    public static void sendTriggerProgress(ServerPlayer player, java.util.Collection<Task> tasks) {
        if (player == null || tasks == null || tasks.isEmpty()) {
            if (com.todolist.HudProbe.enabled()) {
                com.todolist.HudProbe.log("server push skipped: player={} taskCount={}",
                        player == null ? "null" : player.getName().getString(), tasks == null ? -1 : tasks.size());
            }
            return;
        }
        if (com.todolist.HudProbe.enabled()) {
            com.todolist.HudProbe.log("server push {} entries to {}", tasks.size(), player.getName().getString());
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeTriggerProgress(buf, tasks, false);
        serverPacketSender.send(player, TRIGGER_PROGRESS_ID, buf);
    }

    /**
     * 向所有在线玩家广播触发器进度变化（团队任务桶）。
     *
     * @param server 当前服务端
     * @param tasks  进度发生变化的任务集合
     */
    public static void broadcastTeamTriggerProgress(MinecraftServer server, java.util.Collection<Task> tasks) {
        if (server == null || tasks == null || tasks.isEmpty()) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeTriggerProgress(buf, tasks, true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null) {
                serverPacketSender.send(player, TRIGGER_PROGRESS_ID, new FriendlyByteBuf(buf.copy()));
            }
        }
    }

    /**
     * 写出触发器进度批次。
     *
     * @param buf   目标缓冲
     * @param tasks 进度发生变化的任务集合
     * @param team  是否为团队任务桶
     */
    public static void writeTriggerProgress(FriendlyByteBuf buf, java.util.Collection<Task> tasks, boolean team) {
        buf.writeBoolean(team);
        List<Task> valid = new java.util.ArrayList<>(tasks.size());
        for (Task task : tasks) {
            if (task != null && task.getId() != null) {
                valid.add(task);
            }
        }
        buf.writeVarInt(valid.size());
        for (Task task : valid) {
            buf.writeUtf(task.getId());
            TaskTrigger trigger = task.getTrigger();
            buf.writeVarInt(trigger == null ? 0 : trigger.getProgress());
            buf.writeBoolean(task.isCompleted());
        }
    }

    /**
     * 读入触发器进度批次。
     *
     * @param buf 来源缓冲
     * @return 进度变化批次
     */
    public static TriggerProgressBatch readTriggerProgress(FriendlyByteBuf buf) {
        boolean team = buf.readBoolean();
        int size = buf.readVarInt();
        List<TriggerProgress> entries = new java.util.ArrayList<>(Math.max(0, size));
        for (int i = 0; i < size; i++) {
            entries.add(new TriggerProgress(buf.readUtf(), buf.readVarInt(), buf.readBoolean()));
        }
        return new TriggerProgressBatch(team, entries);
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
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    private static void handleReplaceTasks(ServerPlayer player, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            H2MaintenanceGuard.ensureWritableIfH2();
            TaskTriggerService.mergeTriggerStateInto(player.level().getServer(), player.getUUID(), tasks);
            storage.savePersonalTasks(player.level().getServer(), player.getUUID(), tasks);
            TaskTriggerService.invalidatePersonal(player.level().getServer(), player.getUUID());
            if (containsPendingItemCollectTrigger(tasks)) {
                TaskTriggerService.evaluateItemCollectAfterTriggerChange(player);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save player tasks", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    private static void handleTeamReplaceTasks(MinecraftServer server, ServerPlayer player, List<Task> tasks, List<Task> baseTasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> currentTasks = storage.loadTeamTasks();
            List<Task> submittedTasks = baseTasks == null ? tasks : mergeTeamTasks(currentTasks, baseTasks, tasks);
            List<Task> tasksToSave = mergeTeamTasksWithPermission(player, currentTasks, submittedTasks);
            H2MaintenanceGuard.ensureWritableIfH2();
            TaskTriggerService.mergeTeamTriggerStateInto(tasksToSave);
            // 领取人变更的团队任务清零进度（必须在合并引擎进度之后，否则重置会被覆盖）
            TaskTriggerService.resetTriggerProgressOnAssigneeChange(currentTasks, tasksToSave);
            storage.saveTeamTasks(tasksToSave);
            TaskTriggerService.invalidateTeam();
            if (containsPendingItemCollectTrigger(tasksToSave)) {
                TaskTriggerService.evaluateItemCollectAfterTriggerChange(player);
            }
            if (baseTasks == null) {
                broadcastTeamTasksExcept(server, player);
            } else {
                broadcastTeamTasks(server);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save team tasks", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    /**
     * 按权限将客户端上传的团队任务列表与服务端当前状态合并，避免越权修改。
     */
    private static List<Task> mergeTeamTasksWithPermission(ServerPlayer player, List<Task> currentTasks, List<Task> incomingTasks) {
        Map<String, Task> incomingById = new LinkedHashMap<>();
        for (Task incoming : incomingTasks) {
            if (incoming == null) {
                continue;
            }
            String id = incoming.getId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            incomingById.put(id, incoming);
        }

        List<Task> merged = new ArrayList<>();
        for (Task current : currentTasks) {
            if (current == null) {
                continue;
            }
            String id = current.getId();
            if (id == null || id.isEmpty()) {
                merged.add(current);
                continue;
            }

            Task incoming = incomingById.remove(id);
            if (incoming == null) {
                if (!canDeleteTaskInProject(player, current)) {
                    merged.add(current);
                }
                continue;
            }

            boolean sameProject = isSameProjectBinding(current, incoming);
            if (sameProject) {
                if (canApplyTeamTaskUpdate(player, current, incoming)) {
                    merged.add(incoming);
                } else {
                    merged.add(current);
                }
                continue;
            }

            boolean canDeleteCurrent = canDeleteTaskInProject(player, current);
            boolean canAddIncoming = canAddTaskInProject(player, incoming);
            if (canDeleteCurrent && canAddIncoming) {
                merged.add(incoming);
            } else {
                merged.add(current);
            }
        }

        for (Task incoming : incomingById.values()) {
            if (incoming == null) {
                continue;
            }
            if (canAddTaskInProject(player, incoming)) {
                merged.add(incoming);
            }
        }
        return merged;
    }

    /**
     * 判定同一团队项目下的任务更新是否可应用，按“字段变化 -> 操作类型”逐项校验权限。
     */
    private static boolean canApplyTeamTaskUpdate(ServerPlayer player, Task current, Task incoming) {
        if (current == null || incoming == null || !isSameProjectBinding(current, incoming)) {
            return false;
        }
        Project project = findTaskProject(current);
        if (project == null) {
            return true;
        }

        if (hasEditableFieldChanges(current, incoming)
                && !canOperateInProject(player, current, project, Operation.EDIT_TASK, ViewScope.TEAM_ALL)) {
            return false;
        }

        if (current.isCompleted() != incoming.isCompleted()
                && !canOperateInProject(player, current, project, Operation.TOGGLE_COMPLETE, ViewScope.TEAM_ASSIGNED)) {
            return false;
        }

        Operation assigneeOp = resolveAssigneeOperation(player, current, incoming);
        if (assigneeOp == null) {
            return true;
        }
        return canOperateInProject(player, current, project, assigneeOp, scopeForOperation(assigneeOp));
    }

    /**
     * 判断本次更新是否涉及需要 EDIT_TASK 权限的可编辑字段。
     */
    private static boolean hasEditableFieldChanges(Task current, Task incoming) {
        if (!Objects.equals(current.getTitle(), incoming.getTitle())) {
            return true;
        }
        if (!Objects.equals(current.getDescription(), incoming.getDescription())) {
            return true;
        }
        if (current.getPriority() != incoming.getPriority()) {
            return true;
        }
        if (!Objects.equals(current.getTags(), incoming.getTags())) {
            return true;
        }
        if (!Objects.equals(current.getDueDate(), incoming.getDueDate())) {
            return true;
        }
        if (current.getCreatedAt() != incoming.getCreatedAt()) {
            return true;
        }
        if (current.getScope() != incoming.getScope()) {
            return true;
        }
        return !Objects.equals(current.getCreatorUuid(), incoming.getCreatorUuid());
    }

    /**
     * 根据负责人字段变化推导需要的权限操作。
     */
    private static Operation resolveAssigneeOperation(ServerPlayer player, Task current, Task incoming) {
        String currentAssignee = normalizeText(current.getAssigneeUuid());
        String incomingAssignee = normalizeText(incoming.getAssigneeUuid());
        if (Objects.equals(currentAssignee, incomingAssignee)) {
            return null;
        }

        String playerUuid = player == null ? null : normalizeText(player.getStringUUID());
        if (isBlank(currentAssignee)) {
            if (playerUuid != null && playerUuid.equals(incomingAssignee)) {
                return Operation.CLAIM_TASK;
            }
            return Operation.ASSIGN_OTHERS;
        }
        if (isBlank(incomingAssignee)) {
            return Operation.ABANDON_TASK;
        }
        return Operation.ASSIGN_OTHERS;
    }

    /**
     * 为指定操作选择服务端权限判定使用的视图作用域。
     */
    private static ViewScope scopeForOperation(Operation operation) {
        if (operation == Operation.CLAIM_TASK) {
            return ViewScope.TEAM_UNASSIGNED;
        }
        if (operation == Operation.ABANDON_TASK || operation == Operation.TOGGLE_COMPLETE) {
            return ViewScope.TEAM_ASSIGNED;
        }
        return ViewScope.TEAM_ALL;
    }

    /**
     * 在给定团队项目上下文中执行权限判定。
     */
    private static boolean canOperateInProject(ServerPlayer player, Task task, Project project, Operation operation, ViewScope scope) {
        if (task == null || project == null) {
            return false;
        }
        Role role = getRole(player, project);
        Context context = buildTaskContext(player, task, project, scope);
        return PermissionCenter.canPerform(operation, role, context);
    }

    /**
     * 统一处理字符串标准化：trim 后空串转 null。
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判断文本是否为空（null 或空串）。
     */
    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private static boolean canEditTaskInProject(ServerPlayer player, Task task) {
        Project project = findTaskProject(task);
        if (project == null) {
            return true;
        }
        return canOperateInProject(player, task, project, Operation.EDIT_TASK, ViewScope.TEAM_ALL);
    }

    /**
     * 判断玩家是否允许删除指定团队任务。
     */
    private static boolean canDeleteTaskInProject(ServerPlayer player, Task task) {
        Project project = findTaskProject(task);
        if (project == null) {
            return true;
        }
        return canOperateInProject(player, task, project, Operation.DELETE_TASK, ViewScope.TEAM_ALL);
    }

    /**
     * 判断玩家是否允许在指定团队项目下新增任务。
     */
    private static boolean canAddTaskInProject(ServerPlayer player, Task task) {
        Project project = findTaskProject(task);
        if (project == null) {
            return true;
        }
        return canOperateInProject(player, task, project, Operation.ADD_TASK, ViewScope.TEAM_ALL);
    }

    /**
     * 解析任务所属的团队项目。
     */
    private static Project findTaskProject(Task task) {
        if (task == null || task.getProjectId() == null || task.getProjectId().isBlank()) {
            return null;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(task.getProjectId().trim());
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return null;
        }
        return project;
    }

    /**
     * 构建任务权限判断上下文。
     */
    private static Context buildTaskContext(ServerPlayer player, Task task, Project project) {
        return buildTaskContext(player, task, project, ViewScope.TEAM_ALL);
    }

    /**
     * 构建指定视图作用域下的任务权限上下文。
     */
    private static Context buildTaskContext(ServerPlayer player, Task task, Project project, ViewScope viewScope) {
        boolean assigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean assigneeSelf = assigned
                && player != null
                && player.getStringUUID().equals(task.getAssigneeUuid());
        boolean projectMember = isProjectMember(player, project);
        boolean allowMemberCreate = project != null && project.isAllowMemberCreate();
        boolean allowAllPlayersClaimComplete = project != null && project.isAllowAllPlayersClaimComplete();
        return new Context(
                viewScope,
                task.isCompleted(),
                assigned,
                assigneeSelf,
                false,
                false,
                projectMember,
                allowMemberCreate,
                allowAllPlayersClaimComplete
        );
    }

    /**
     * 判断玩家是否为项目成员，包含负责人。
     */
    private static boolean isProjectMember(ServerPlayer player, Project project) {
        if (player == null || project == null) {
            return false;
        }
        String playerUuid = player.getStringUUID();
        if (playerUuid.equals(project.getOwnerUuid())) {
            return true;
        }
        return project.getMemberRole(playerUuid) != null;
    }

    /**
     * 解析玩家在项目中的角色，用于权限判断。
     */
    private static Role getRole(ServerPlayer player, Project project) {
        if (player == null) {
            return Role.MEMBER;
        }
        if (player.hasPermissions(2)) {
            return Role.OP;
        }
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return Role.MEMBER;
        }
        String playerUuid = player.getStringUUID();
        if (playerUuid.equals(project.getOwnerUuid())) {
            return Role.PROJECT_MANAGER;
        }
        Project.ProjectRole memberRole = project.getMemberRole(playerUuid);
        if (memberRole == Project.ProjectRole.LEAD) {
            return Role.LEAD;
        }
        return Role.MEMBER;
    }

    /**
     * 判断两个任务是否仍绑定到同一个项目。
     */
    private static boolean isSameProjectBinding(Task current, Task incoming) {
        String currentProjectId = current.getProjectId();
        String incomingProjectId = incoming.getProjectId();
        if (currentProjectId == null || currentProjectId.isBlank()) {
            return incomingProjectId == null || incomingProjectId.isBlank();
        }
        return currentProjectId.equals(incomingProjectId);
    }

    /**
     * 基于客户端基线合并团队任务，避免旧整表快照覆盖其他玩家期间提交的改动。
     *
     * @param currentTasks 服务端当前任务列表
     * @param baseTasks 客户端保存发起时的同步基线
     * @param submittedTasks 客户端提交的最新任务列表
     * @return 合并后的任务列表
     */
    private static List<Task> mergeTeamTasks(List<Task> currentTasks, List<Task> baseTasks, List<Task> submittedTasks) {
        Map<String, Task> baseById = mapTasksById(baseTasks);
        Map<String, Task> submittedById = mapTasksById(submittedTasks);
        Map<String, Task> currentById = mapTasksById(currentTasks);
        List<Task> merged = new ArrayList<>();

        for (Task currentTask : currentTasks) {
            Task baseTask = baseById.get(currentTask.getId());
            Task submittedTask = submittedById.get(currentTask.getId());
            if (baseTask != null && submittedTask == null) {
                if (!tasksEquivalent(baseTask, currentTask)) {
                    merged.add(copyTask(currentTask));
                }
                continue;
            }
            if (submittedTask != null && !tasksEquivalent(baseTask, submittedTask)) {
                merged.add(copyTask(submittedTask));
            } else {
                merged.add(copyTask(currentTask));
            }
        }

        for (Task submittedTask : submittedTasks) {
            if (currentById.containsKey(submittedTask.getId())) {
                continue;
            }
            Task baseTask = baseById.get(submittedTask.getId());
            if (baseTask == null || !tasksEquivalent(baseTask, submittedTask)) {
                merged.add(copyTask(submittedTask));
            }
        }

        return merged;
    }

    /**
     * 按任务 ID 构建任务映射，并忽略空任务。
     *
     * @param tasks 原始任务列表
     * @return 任务 ID 到任务对象的映射
     */
    private static Map<String, Task> mapTasksById(List<Task> tasks) {
        Map<String, Task> byId = new LinkedHashMap<>();
        for (Task task : tasks == null ? List.<Task>of() : tasks) {
            if (task != null && task.getId() != null) {
                byId.put(task.getId(), task);
            }
        }
        return byId;
    }

    /**
     * 判断两个任务的可持久化内容是否一致。
     *
     * @param left 左侧任务
     * @param right 右侧任务
     * @return 内容一致时返回 true
     */
    private static boolean tasksEquivalent(Task left, Task right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.toNbt(), right.toNbt());
    }

    /**
     * 深拷贝任务对象，避免合并结果与调用方列表共享可变实例。
     *
     * @param task 原始任务
     * @return 拷贝后的任务
     */
    private static Task copyTask(Task task) {
        return Task.fromNbt(task.toNbt());
    }

    /**
     * 判断两个任务列表的持久化内容是否一致，用于决定是否需要回推纠正结果。
     */
    private static boolean taskListsEquivalent(List<Task> left, List<Task> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        Map<String, Task> leftById = mapTasksById(left);
        Map<String, Task> rightById = mapTasksById(right);
        if (leftById.size() != rightById.size()) {
            return false;
        }
        for (Map.Entry<String, Task> entry : leftById.entrySet()) {
            if (!tasksEquivalent(entry.getValue(), rightById.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
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
        broadcastTeamTasksExcept(server, null);
    }

    /**
     * 将服务端当前团队任务列表广播给除指定玩家以外的在线玩家。
     *
     * @param server 当前服务端
     * @param excludedPlayer 不需要接收本次广播的玩家；为 null 时广播给所有人
     */
    private static void broadcastTeamTasksExcept(MinecraftServer server, ServerPlayer excludedPlayer) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadTeamTasks();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (excludedPlayer != null && player != null && player.getUUID().equals(excludedPlayer.getUUID())) {
                    continue;
                }
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                writeTaskList(buf, tasks);
                serverPacketSender.send(player, TEAM_SYNC_TASKS_ID, buf);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for broadcast", e);
            if (StorageFailureNotifier.isStorageUnavailable(e)) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
                }
            }
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
     * 判断任务列表中是否存在「未完成的物品收集触发器」。
     * 仅此时才需要在保存后立即评估一次持有量，避免无谓的存储读取。
     *
     * @param tasks 任务列表
     * @return 存在时返回 true
     */
    private static boolean containsPendingItemCollectTrigger(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        for (Task task : tasks) {
            if (task == null || task.isCompleted() || !task.hasTrigger()) {
                continue;
            }
            com.todolist.task.TaskTrigger trigger = task.getTrigger();
            if (trigger.isValid() && trigger.getType() == com.todolist.task.TaskTrigger.Type.ITEM_COLLECT) {
                return true;
            }
        }
        return false;
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
