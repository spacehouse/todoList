package com.todolist.trigger;

import com.todolist.TodoListCommon;
import com.todolist.network.TaskPackets;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import com.todolist.task.TaskTrigger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端事件触发引擎，负责接收平台事件、推进触发器进度并在达标时自动完成任务。
 *
 * 设计要点：
 * - 所有事件入口调度到服务端主线程，与 GUI 替换式保存串行执行，消除并发竞态；
 * - 桶级内存缓存 + type+target 倒排索引，事件路径零 SQL；
 * - 进度只改内存，3 秒防抖统一落库，完成瞬间立即落库；
 * - 客户端推送与落库解耦：进度变化按 250ms 短节流用内存快照推送，HUD 无需等待落库；
 * - 其他路径保存任务前必须调用 {@link #mergeTriggerStateInto} 合并引擎侧进度，
 *   保存后调用 {@link #invalidatePersonal}/{@link #invalidateTeam} 失效缓存。
 */
public final class TaskTriggerService {
    /** 进度防抖窗口（毫秒）：窗口内进度变化只累计在内存，超窗后下一次事件触发落库。 */
    private static final long FLUSH_DEBOUNCE_MS = 3000L;
    /** 客户端推送节流窗口（毫秒）：与落库解耦，保证 HUD 进度接近实时。 */
    private static final long PUSH_THROTTLE_MS = 250L;

    private static final Map<String, CachedBucket> BUCKETS = new HashMap<>();

    private TaskTriggerService() {
    }

    /**
     * 处理玩家击杀实体事件，推进 KILL_ENTITY 触发器。
     *
     * @param player   击杀者
     * @param entityId 被击杀实体资源 ID
     */
    public static void handleEntityKilled(ServerPlayer player, String entityId) {
        dispatch(player, bucket -> advanceMatchingTasks(player, bucket, TaskTrigger.Type.KILL_ENTITY, entityId, 1));
    }

    /**
     * 处理玩家破坏方块事件，推进 BREAK_BLOCK 触发器。
     *
     * @param player   破坏者
     * @param blockId  方块资源 ID
     */
    public static void handleBlockBroken(ServerPlayer player, String blockId) {
        dispatch(player, bucket -> advanceMatchingTasks(player, bucket, TaskTrigger.Type.BREAK_BLOCK, blockId, 1));
    }

    /**
     * 处理玩家合成物品事件，推进 CRAFT_ITEM 触发器。
     *
     * @param player  合成者
     * @param itemId  产物资源 ID
     * @param count   本次合成产物数量
     */
    public static void handleItemCrafted(ServerPlayer player, String itemId, int count) {
        dispatch(player, bucket -> advanceMatchingTasks(player, bucket, TaskTrigger.Type.CRAFT_ITEM, itemId, count));
    }

    /**
     * 处理玩家获得进度事件，推进 ADVANCEMENT 触发器。
     *
     * @param player         获得者
     * @param advancementId  进度资源 ID
     */
    public static void handleAdvancementAwarded(ServerPlayer player, String advancementId) {
        dispatch(player, bucket -> advanceMatchingTasks(player, bucket, TaskTrigger.Type.ADVANCEMENT, advancementId, 1));
    }

    /**
     * 处理玩家库存变化事件，按"绝对持有量"语义重算 ITEM_COLLECT 触发器进度。
     * 进度如实反映当前持有量；已完成的任务不回退。
     *
     * @param player 库存变化的玩家
     */
    public static void handleInventoryChanged(ServerPlayer player) {
        dispatch(player, bucket -> recalculateItemCollect(player, bucket));
    }

    /**
     * 将引擎内存中的触发器进度合并进即将保存的任务列表。
     * 必须在 GUI/命令等替换式保存前调用，避免旧快照覆盖引擎已推进的进度。
     *
     * @param server 当前服务端
     * @param playerUuid 个人任务桶对应的玩家 UUID
     * @param incoming 即将保存的任务列表（就地合并）
     */
    public static void mergeTriggerStateInto(MinecraftServer server, UUID playerUuid, List<Task> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        CachedBucket bucket = peekBucket(personalBucketKey(server, playerUuid));
        if (bucket == null || !bucket.dirty) {
            return;
        }
        mergeTriggerState(bucket, incoming);
    }

    /**
     * 将引擎内存中的团队任务触发器进度合并进即将保存的任务列表。
     *
     * @param incoming 即将保存的团队任务列表（就地合并）
     */
    public static void mergeTeamTriggerStateInto(List<Task> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        CachedBucket bucket = peekBucket(TEAM_BUCKET_KEY);
        if (bucket == null || !bucket.dirty) {
            return;
        }
        mergeTriggerState(bucket, incoming);
    }

    /**
     * 按任务 ID 把缓存中的触发器进度合并进目标列表。
     *
     * @param bucket   引擎缓存桶
     * @param incoming 目标任务列表
     */
    private static void mergeTriggerState(CachedBucket bucket, List<Task> incoming) {
        Map<String, Task> cachedById = new HashMap<>();
        for (Task task : bucket.tasks) {
            if (task.hasTrigger()) {
                cachedById.put(task.getId(), task);
            }
        }
        for (Task target : incoming) {
            Task cached = cachedById.get(target.getId());
            if (cached != null && cached.hasTrigger()) {
                target.setTrigger(TaskTrigger.fromNbt(cached.getTrigger().toNbt()));
            }
        }
    }

    /**
     * 失效个人任务桶缓存，供 GUI/命令保存个人任务后调用。
     *
     * @param server     当前服务端
     * @param playerUuid 玩家 UUID
     */
    public static void invalidatePersonal(MinecraftServer server, UUID playerUuid) {
        BUCKETS.remove(personalBucketKey(server, playerUuid));
    }

    /**
     * 失效全部个人任务桶缓存（本地桶与所有玩家桶）。
     * 由任务存储的保存出口统一调用：任何全量替换式保存都可能改变桶内容，
     * 失效后引擎会在下一次事件时重新加载，保证缓存与存储一致。
     * 这是比"逐调用点接失效钩子"更可靠的收口，杜绝遗漏导致旧缓存覆盖新任务。
     */
    public static void invalidateAllPersonal() {
        BUCKETS.keySet().removeIf(key -> key.startsWith("P:"));
    }

    /**
     * 失效团队任务桶缓存，供 GUI/命令保存团队任务后调用。
     */
    public static void invalidateTeam() {
        BUCKETS.remove(TEAM_BUCKET_KEY);
    }

    /**
     * 判定指定玩家的个人桶或团队桶是否存在未完成的 ITEM_COLLECT 触发任务，
     * 供平台层决定是否执行库存快照扫描。
     *
     * 注意：这里必须走 {@link #getOrLoadBucket} 而不是只读缓存。
     * 桶只在事件发生时懒加载，若用 {@code peekBucket}，纯捡取（无任何游戏事件）
     * 时桶永远不存在，扫描会被永久关闭，导致收集类触发器完全不生效。
     *
     * @param player 目标玩家
     * @return 存在需要扫描的收集触发器时返回 true
     */
    public static boolean hasItemCollectWork(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        CachedBucket personal = getOrLoadBucket(server, personalBucketKey(server, player.getUUID()), player.getUUID());
        if (personal.hasType(TaskTrigger.Type.ITEM_COLLECT)) {
            return true;
        }
        CachedBucket team = getOrLoadBucket(server, TEAM_BUCKET_KEY, null);
        return team.hasTypeForPlayer(TaskTrigger.Type.ITEM_COLLECT, player.getUUID().toString());
    }

    /**
     * 触发器被创建/修改/清除后立即评估一次当前持有量。
     * 用于「玩家已拥有足够物品」时新加的收集任务能马上判定完成，不必等待下一次捡取事件。
     *
     * @param player 目标玩家
     */
    public static void evaluateItemCollectAfterTriggerChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        handleInventoryChanged(player);
    }

    /**
     * 刷新指定玩家相关的脏桶并清空缓存（玩家退出时调用）。
     *
     * @param player 退出玩家
     */
    public static void flushForPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        flushBucket(server, personalBucketKey(server, player.getUUID()), false);
        BUCKETS.remove(personalBucketKey(server, player.getUUID()));
    }

    /**
     * 刷新全部脏桶并清空缓存（服务端停止时调用，必须在主线程同步执行）。
     *
     * @param server 当前服务端
     */
    public static void flushAll(MinecraftServer server) {
        for (Map.Entry<String, CachedBucket> entry : new HashMap<>(BUCKETS).entrySet()) {
            flushBucket(server, entry.getKey(), false);
        }
        BUCKETS.clear();
    }

    /**
     * 统一事件分发入口：调度到服务端主线程后执行桶级处理。
     *
     * @param player  事件玩家
     * @param action  桶级处理动作
     */
    private static void dispatch(ServerPlayer player, BucketAction action) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            String personalKey = personalBucketKey(server, player.getUUID());
            action.run(getOrLoadBucket(server, personalKey, player.getUUID()));
            maybeFlushDebounced(server, personalKey, player);
            maybePushThrottled(server, personalKey);
            action.run(getOrLoadBucket(server, TEAM_BUCKET_KEY, null));
            maybeFlushDebounced(server, TEAM_BUCKET_KEY, player);
            maybePushThrottled(server, TEAM_BUCKET_KEY);
        });
    }

    /**
     * 按短节流窗口向客户端推送**进度变化的任务**（轻量增量包），使 HUD 进度接近实时。
     * 与落库防抖完全解耦，推送路径不触发任何 SQL。
     *
     * 只发送引擎推进过的任务（ID + 进度 + 完成态），不发送全量任务快照：
     * 全量快照会让客户端把上千条任务重新写回本地存储，造成每次事件触发都卡顿；
     * 增量包的处理开销只与变化任务数成正比。
     *
     * @param server    当前服务端
     * @param bucketKey 桶键
     */
    private static void maybePushThrottled(MinecraftServer server, String bucketKey) {
        CachedBucket bucket = peekBucket(bucketKey);
        if (bucket == null || !bucket.dirty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - bucket.lastPushTime < PUSH_THROTTLE_MS) {
            return;
        }
        List<Task> changed = bucket.peekDirtyTasks();
        if (changed.isEmpty()) {
            return;
        }
        bucket.lastPushTime = now;
        if (bucket.playerUuid != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(bucket.playerUuid);
            if (online != null) {
                TaskPackets.sendTriggerProgress(online, changed);
            }
        } else {
            TaskPackets.broadcastTeamTriggerProgress(server, changed);
        }
    }

    /**
     * 按防抖窗口决定是否落库当前脏桶。
     *
     * @param server    当前服务端
     * @param bucketKey 桶键
     * @param player    事件玩家，用于同步推送
     */
    private static void maybeFlushDebounced(MinecraftServer server, String bucketKey, ServerPlayer player) {
        CachedBucket bucket = peekBucket(bucketKey);
        if (bucket == null || !bucket.dirty) {
            return;
        }
        if (System.currentTimeMillis() - bucket.lastFlushTime >= FLUSH_DEBOUNCE_MS) {
            flushBucket(server, bucketKey, true);
        }
    }

    /**
     * 将脏桶的触发器进度增量写回存储并按需推送刷新。
     *
     * 落库采用增量 UPDATE（只写引擎推进过的任务行，见
     * {@link TaskStorage#updateTriggerStates}），不做全量替换：
     * - 不会覆盖其他保存路径刚写入的新任务（否则新任务会被旧缓存列表抹掉）；
     * - 避免上千行任务的替换式写放大导致主线程卡顿。
     *
     * 落库后的推送同样只发进度变化任务（轻量增量包），不再发送全量任务快照：
     * 全量快照会让服务端多读一次库、客户端把全量任务重新写回本地存储。
     *
     * @param server   当前服务端
     * @param bucketKey 桶键
     * @param pushSync 落库后是否向在线玩家推送进度变化
     */
    private static void flushBucket(MinecraftServer server, String bucketKey, boolean pushSync) {
        CachedBucket bucket = peekBucket(bucketKey);
        if (bucket == null || !bucket.dirty) {
            return;
        }
        List<Task> dirtyTasks = bucket.takeDirtyTasks();
        try {
            TodoListCommon.getTaskStorage().updateTriggerStates(dirtyTasks);
            bucket.dirty = false;
            bucket.lastFlushTime = System.currentTimeMillis();
        } catch (Exception exception) {
            bucket.restoreDirtyTasks(dirtyTasks);
            com.todolist.TodoConstants.LOGGER.error("Failed to flush trigger bucket {}", bucketKey, exception);
            return;
        }
        if (pushSync) {
            if (bucket.playerUuid != null) {
                ServerPlayer online = server.getPlayerList().getPlayer(bucket.playerUuid);
                if (online != null) {
                    TaskPackets.sendTriggerProgress(online, dirtyTasks);
                }
            } else {
                TaskPackets.broadcastTeamTriggerProgress(server, dirtyTasks);
            }
        }
    }

    /**
     * 推进桶内匹配的累加型触发器（KILL/BREAK/CRAFT/ADVANCEMENT）。
     *
     * @param player 事件玩家
     * @param bucket 目标桶
     * @param type   触发类型
     * @param target 目标资源 ID
     * @param amount 本次增量
     */
    private static void advanceMatchingTasks(ServerPlayer player, CachedBucket bucket, TaskTrigger.Type type, String target, int amount) {
        for (Task task : advanceMatchingTasksByUuid(player.getUUID().toString(), bucket, type, target, amount)) {
            completeTask(player, bucket, task);
        }
    }

    /**
     * 按玩家 UUID 推进匹配的累加型触发器（纯判定逻辑，不依赖 MC 运行时对象）。
     * 返回本轮达标的任务（已置完成并移出索引），由调用方负责落库与推送。
     *
     * @param playerUuid 事件玩家 UUID
     * @param bucket     目标桶
     * @param type       触发类型
     * @param target     目标资源 ID
     * @param amount     本次增量
     * @return 本轮达标的任务列表
     */
    static List<Task> advanceMatchingTasksByUuid(String playerUuid, CachedBucket bucket, TaskTrigger.Type type, String target, int amount) {
        if (target == null || target.isEmpty()) {
            return List.of();
        }
        // 必须遍历副本：完成处理会把达标任务从倒排索引中移除，
        // 直接遍历索引内的实时列表会在下一次迭代抛出 ConcurrentModificationException，
        // 导致整个事件回调中断（不落库、不推送，表现就是"任务不完成、进度也不变"）。
        List<Task> candidates = new ArrayList<>(bucket.find(type, target));
        List<Task> completed = new ArrayList<>();
        for (Task task : candidates) {
            if (!canPlayerAdvanceByUuid(playerUuid, task)) {
                continue;
            }
            TaskTrigger trigger = task.getTrigger();
            if (trigger.addProgress(amount)) {
                task.setCompleted(true);
                bucket.markDirty(task);
                bucket.removeIndex(task);
                completed.add(task);
            } else {
                bucket.markDirty(task);
            }
        }
        return completed;
    }

    /**
     * 重算桶内全部 ITEM_COLLECT 触发器为当前绝对持有量。
     *
     * @param player 事件玩家
     * @param bucket 目标桶
     */
    private static void recalculateItemCollect(ServerPlayer player, CachedBucket bucket) {
        List<Task> probe = new ArrayList<>();
        collectItemTargets(bucket, TaskTrigger.Type.ITEM_COLLECT, player.getUUID().toString(), probe);
        if (probe.isEmpty()) {
            return;
        }
        Set<String> targets = new HashSet<>();
        for (Task task : probe) {
            targets.add(task.getTrigger().getTarget());
        }
        Map<String, Integer> heldCounts = countHeldItems(player, targets);
        for (Task task : recalculateItemCollectByUuid(player.getUUID().toString(), heldCounts, bucket)) {
            completeTask(player, bucket, task);
        }
    }

    /**
     * 按玩家 UUID 与持有量快照重算 ITEM_COLLECT 触发器（纯判定逻辑，不依赖 MC 运行时对象）。
     * 进度如实反映持有量；已完成的任务不回退。
     *
     * @param playerUuid 事件玩家 UUID
     * @param heldCounts 资源 ID 到当前持有数量的映射
     * @param bucket     目标桶
     * @return 本轮达标的任务列表
     */
    static List<Task> recalculateItemCollectByUuid(String playerUuid, Map<String, Integer> heldCounts, CachedBucket bucket) {
        List<Task> candidates = new ArrayList<>();
        collectItemTargets(bucket, TaskTrigger.Type.ITEM_COLLECT, playerUuid, candidates);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Task> completed = new ArrayList<>();
        for (Task task : candidates) {
            TaskTrigger trigger = task.getTrigger();
            int held = heldCounts.getOrDefault(trigger.getTarget(), 0);
            if (held == trigger.getProgress()) {
                continue;
            }
            trigger.setProgress(held);
            if (trigger.isSatisfied()) {
                task.setCompleted(true);
                bucket.markDirty(task);
                bucket.removeIndex(task);
                completed.add(task);
            } else {
                bucket.markDirty(task);
            }
        }
        return completed;
    }

    /**
     * 收集玩家可推进的指定类型触发任务及其目标集合。
     *
     * @param bucket     目标桶
     * @param type       触发类型
     * @param playerUuid 事件玩家 UUID
     * @param candidates 输出收集容器
     */
    private static void collectItemTargets(CachedBucket bucket, TaskTrigger.Type type, String playerUuid, List<Task> candidates) {
        for (List<Task> group : bucket.index().values()) {
            for (Task task : group) {
                if (task.getTrigger().getType() == type && canPlayerAdvanceByUuid(playerUuid, task)) {
                    candidates.add(task);
                }
            }
        }
    }

    /**
     * 一次遍历玩家库存统计目标物品持有量。
     *
     * @param player  目标玩家
     * @param targets 待统计物品资源 ID 集合
     * @return 资源 ID 到持有数量的映射
     */
    private static Map<String, Integer> countHeldItems(ServerPlayer player, Set<String> targets) {
        Map<String, Integer> counts = new HashMap<>();
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString();
            if (targets.contains(itemId)) {
                counts.merge(itemId, stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /**
     * 任务达标后的统一完成处理：立即增量落库、推送进度变化、发送完成提示。
     *
     * 落库与推送都只针对本批进度变化的任务（{@link #flushBucket} 的增量语义），
     * 不再发送全量任务快照：全量快照会让服务端多读一次库、客户端把上千条任务
     * 重新写回本地存储，表现为「完成任务的那一刻卡顿一下」。
     *
     * @param player 达标玩家
     * @param bucket 任务所在桶
     * @param task   达标任务
     */
    private static void completeTask(ServerPlayer player, CachedBucket bucket, Task task) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        flushBucket(server, bucket.key, true);
        TaskPackets.notifyTriggerCompleted(player, task.getTitle());
    }

    /**
     * 按 UUID 判断玩家是否可推进任务（个人桶恒真；团队桶要求为负责人或任务未指派）。
     * 包级可见供同包离线测试直接构造团队/个人场景。
     *
     * @param playerUuid 玩家 UUID
     * @param task       目标任务
     * @return 可推进时返回 true
     */
    static boolean canPlayerAdvanceByUuid(String playerUuid, Task task) {
        if (task.getScope() != Task.Scope.TEAM) {
            return true;
        }
        String assignee = task.getAssigneeUuid();
        return assignee == null || assignee.isEmpty() || assignee.equals(playerUuid);
    }

    /**
     * 获取或加载个人/团队桶缓存。
     *
     * @param server     当前服务端
     * @param bucketKey  桶键
     * @param playerUuid 个人桶玩家 UUID，团队桶为 null
     * @return 桶缓存
     */
    private static CachedBucket getOrLoadBucket(MinecraftServer server, String bucketKey, UUID playerUuid) {
        CachedBucket cached = BUCKETS.get(bucketKey);
        if (cached != null) {
            return cached;
        }
        try {
            TaskStorage storage = TodoListCommon.getTaskStorage();
            List<Task> tasks = playerUuid != null
                    ? storage.loadPersonalTasks(server, playerUuid)
                    : storage.loadTeamTasks();
            cached = new CachedBucket(bucketKey, playerUuid, tasks);
        } catch (Exception exception) {
            com.todolist.TodoConstants.LOGGER.error("Failed to load trigger bucket {}", bucketKey, exception);
            cached = new CachedBucket(bucketKey, playerUuid, List.of());
        }
        BUCKETS.put(bucketKey, cached);
        return cached;
    }

    /**
     * 只读获取桶缓存，不触发加载。
     *
     * @param bucketKey 桶键
     * @return 缓存桶，不存在时返回 null
     */
    private static CachedBucket peekBucket(String bucketKey) {
        return BUCKETS.get(bucketKey);
    }

    /**
     * 生成个人任务桶缓存键。
     *
     * @param server     当前服务端
     * @param playerUuid 玩家 UUID
     * @return 桶键
     */
    private static String personalBucketKey(MinecraftServer server, UUID playerUuid) {
        boolean local = TodoListCommon.getTaskStorage().shouldUseLocalPersonalStorage(server);
        return "P:" + (local ? "LOCAL" : playerUuid.toString());
    }

    /** 团队任务桶缓存键。 */
    private static final String TEAM_BUCKET_KEY = "T";

    /**
     * 桶级处理动作。
     */
    @FunctionalInterface
    private interface BucketAction {
        /**
         * 执行桶级处理。
         *
         * @param bucket 目标桶
         */
        void run(CachedBucket bucket);
    }

    /**
     * 任务桶缓存：任务列表 + 触发器倒排索引 + 脏标记。
     * 包级可见供同包离线测试构造五种事件类型的推进场景。
     */
    static final class CachedBucket {
        private final String key;
        private final UUID playerUuid;
        private final List<Task> tasks;
        private final Map<String, List<Task>> triggerIndex = new HashMap<>();
        /** 引擎推进过进度、等待增量落库的任务 ID 集合。 */
        private final Set<String> dirtyTaskIds = new HashSet<>();
        private boolean dirty;
        private long lastFlushTime = System.currentTimeMillis();
        private long lastPushTime = System.currentTimeMillis();

        /**
         * 创建桶缓存并按触发器建立索引。
         *
         * @param key        桶键
         * @param playerUuid 个人桶玩家 UUID，团队桶为 null
         * @param tasks      桶内任务列表
         */
        CachedBucket(String key, UUID playerUuid, List<Task> tasks) {
            this.key = key;
            this.playerUuid = playerUuid;
            this.tasks = new ArrayList<>(tasks);
            rebuildIndex();
        }

        /**
         * 标记任务进度已变化，等待增量落库。
         *
         * @param task 进度变化的任务
         */
        private void markDirty(Task task) {
            dirty = true;
            if (task != null) {
                dirtyTaskIds.add(task.getId());
            }
        }

        /**
         * 只读获取当前待落库的任务（不清空脏集合），供节流推送读取变化任务。
         * 与 {@link #takeDirtyTasks()} 的区别：推送是旁路，不能消费脏集合，
         * 否则随后的落库会拿不到任务、导致进度丢失。
         *
         * @return 当前进度变化的任务列表
         */
        List<Task> peekDirtyTasks() {
            if (dirtyTaskIds.isEmpty()) {
                return List.of();
            }
            List<Task> dirtyTasks = new ArrayList<>();
            for (Task task : tasks) {
                if (dirtyTaskIds.contains(task.getId())) {
                    dirtyTasks.add(task);
                }
            }
            return dirtyTasks;
        }

        /**
         * 取出待增量落库的任务快照并清空脏集合。
         * 落库失败时应通过 {@link #restoreDirtyTasks} 恢复，避免进度丢失。
         * 包级可见供同包离线测试断言增量落库的脏任务集合行为。
         *
         * @return 待落库任务列表
         */
        List<Task> takeDirtyTasks() {
            if (dirtyTaskIds.isEmpty()) {
                return List.of();
            }
            List<Task> dirtyTasks = new ArrayList<>();
            for (Task task : tasks) {
                if (dirtyTaskIds.contains(task.getId())) {
                    dirtyTasks.add(task);
                }
            }
            dirtyTaskIds.clear();
            return dirtyTasks;
        }

        /**
         * 落库失败时恢复脏集合，等待下次重试。
         *
         * @param dirtyTasks 取出的待落库任务
         */
        void restoreDirtyTasks(List<Task> dirtyTasks) {
            for (Task task : dirtyTasks) {
                dirtyTaskIds.add(task.getId());
            }
        }

        /**
         * 重建触发器倒排索引（仅收录未完成的有效触发任务）。
         */
        private void rebuildIndex() {
            triggerIndex.clear();
            for (Task task : tasks) {
                if (task.hasTrigger() && !task.isCompleted()) {
                    triggerIndex.computeIfAbsent(indexKey(task.getTrigger()), ignored -> new ArrayList<>()).add(task);
                }
            }
        }

        /**
         * 查询匹配的触发任务列表。
         *
         * @param type   触发类型
         * @param target 目标资源 ID
         * @return 匹配任务列表（可能为空）
         */
        List<Task> find(TaskTrigger.Type type, String target) {
            return triggerIndex.getOrDefault(type.name() + '\u0000' + target, List.of());
        }

        /**
         * 判断桶内是否存在指定类型的未完成触发任务。
         *
         * @param type 触发类型
         * @return 存在时返回 true
         */
        private boolean hasType(TaskTrigger.Type type) {
            return hasTypeForPlayer(type, null);
        }

        /**
         * 判断桶内是否存在指定玩家可推进的指定类型触发任务。
         *
         * @param type       触发类型
         * @param playerUuid 玩家 UUID，null 表示不限玩家
         * @return 存在时返回 true
         */
        private boolean hasTypeForPlayer(TaskTrigger.Type type, String playerUuid) {
            for (List<Task> group : triggerIndex.values()) {
                for (Task task : group) {
                    if (task.getTrigger().getType() != type) {
                        continue;
                    }
                    if (playerUuid == null || canPlayerAdvanceByUuid(playerUuid, task)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * 将已完成任务从索引移除（保留在任务列表中随桶保存）。
         *
         * @param task 已完成任务
         */
        private void removeIndex(Task task) {
            if (!task.hasTrigger()) {
                return;
            }
            List<Task> group = triggerIndex.get(indexKey(task.getTrigger()));
            if (group != null) {
                group.remove(task);
            }
        }

        /**
         * 生成触发器索引键。
         *
         * @param trigger 触发器
         * @return 索引键
         */
        private static String indexKey(TaskTrigger trigger) {
            return trigger.getType().name() + '\u0000' + trigger.getTarget();
        }

        /**
         * 返回索引视图（仅内部重算遍历使用）。
         *
         * @return 索引映射
         */
        private Map<String, List<Task>> index() {
            return triggerIndex;
        }

        /**
         * 返回脏标记（离线测试断言进度是否变化时使用）。
         *
         * @return 有未落库变化时返回 true
         */
        boolean isDirty() {
            return dirty;
        }
    }
}
