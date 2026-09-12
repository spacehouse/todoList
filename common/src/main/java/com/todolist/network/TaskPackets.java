package com.todolist.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.AdvancementCatalog;
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
    public static final ResourceLocation SYNC_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "sync_tasks");
    public static final ResourceLocation TEAM_SYNC_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_sync_tasks");
    public static final ResourceLocation TASK_CONFIRMED_ID = new ResourceLocation(TodoConstants.MOD_ID, "task_confirmed");
    public static final ResourceLocation REPLACE_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "replace_tasks");
    public static final ResourceLocation TEAM_REPLACE_TASKS_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_replace_tasks");
    public static final ResourceLocation TEAM_REQUEST_SYNC_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_request_sync");
    public static final ResourceLocation ADD_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "add_task");
    public static final ResourceLocation UPDATE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "update_task");
    public static final ResourceLocation DELETE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "delete_task");
    public static final ResourceLocation TOGGLE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "toggle_task");
    public static final ResourceLocation TEAM_TOGGLE_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_toggle_task");
    public static final ResourceLocation TEAM_ASSIGN_TASK_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_assign_task");
    /** 服务端→客户端：分块下发团队任务。 */
    public static final ResourceLocation TEAM_SYNC_TASKS_CHUNKED_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_sync_tasks_chunked");
    /** 客户端→服务端：分块提交团队任务替换/合并。 */
    public static final ResourceLocation TEAM_REPLACE_TASKS_CHUNKED_ID = new ResourceLocation(TodoConstants.MOD_ID, "team_replace_tasks_chunked");
    /** 服务端→客户端：事件触发器自动完成任务提示（携带任务标题原文）。 */
    public static final ResourceLocation TRIGGER_COMPLETED_ID = new ResourceLocation(TodoConstants.MOD_ID, "trigger_completed");
    /** 服务端→客户端：触发器进度轻量推送（仅变化任务 ID/进度/完成态，避免全量快照的网络与落库开销）。 */
    public static final ResourceLocation TRIGGER_PROGRESS_ID = new ResourceLocation(TodoConstants.MOD_ID, "trigger_progress");
    /** 服务端→客户端：服务端权威进度目录（供「选择进度」列出全部进度，而非仅玩家可见进度）。 */
    public static final ResourceLocation ADVANCEMENT_CATALOG_ID = new ResourceLocation(TodoConstants.MOD_ID, "advancement_catalog");
    private static volatile ServerPacketSender serverPacketSender = (player, channelId, buf) -> { };

    /** 服务端分块累积器，用于接收客户端发来的分块团队任务包。 */
    private static final TaskPacketChunking.ChunkAccumulator serverChunkAccumulator = new TaskPacketChunking.ChunkAccumulator();

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

    /**
     * 处理客户端发来的分块团队任务替换/合并包。
     * 累积所有分片后，重组为完整数据并执行原有替换/合并逻辑。
     */
    public static void onTeamReplaceTasksChunkedPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        TaskPacketChunking.ChunkData chunk = TaskPacketChunking.readChunk(buf);
        byte[] assembled = serverChunkAccumulator.accept(chunk);
        if (assembled == null) {
            return;
        }
        List<Task>[] result = TaskPacketChunking.deserializeMergeTasks(assembled);
        List<Task> tasks = result[0];
        List<Task> baseTasks = result[1];
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
        MinecraftServer server = player.getServer();
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
        for (Advancement advancement : server.getAdvancements().getAllAdvancements()) {
            if (advancement == null || advancement.getDisplay() == null) {
                continue;
            }
            entries.add(new AdvancementCatalog.Entry(
                    advancement.getId().toString(),
                    advancement.getDisplay().getTitle().getString()));
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
            MinecraftServer server = player.getServer();
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
            return;
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
            sendTeamTasksToPlayer(player, tasks);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for sync", e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
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

    private static void handleReplaceTasks(ServerPlayer player, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            H2MaintenanceGuard.ensureWritableIfH2();
            TaskTriggerService.mergeTriggerStateInto(player.getServer(), player.getUUID(), tasks);
            storage.savePersonalTasks(player.getServer(), player.getUUID(), tasks);
            TaskTriggerService.invalidatePersonal(player.getServer(), player.getUUID());
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
            H2MaintenanceGuard.ensureWritableIfH2();
            List<Task> storedTasks = storage.loadTeamTasks();
            List<Task> tasksToSave = baseTasks == null ? tasks : mergeTeamTasks(storedTasks, baseTasks, tasks);
            TaskTriggerService.mergeTeamTriggerStateInto(tasksToSave);
            // 领取人变更的团队任务清零进度（必须在合并引擎进度之后，否则重置会被覆盖）
            TaskTriggerService.resetTriggerProgressOnAssigneeChange(storedTasks, tasksToSave);
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
                sendTeamTasksToPlayer(player, tasks);
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
