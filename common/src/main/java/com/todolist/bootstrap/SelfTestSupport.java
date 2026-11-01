package com.todolist.bootstrap;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.storage.H2ConnectionProvider;
import com.todolist.storage.H2StorageAvailability;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import com.todolist.task.TaskStorage;
import com.todolist.task.TaskTrigger;
import com.todolist.trigger.TaskTriggerService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 服务端自检命令族（/todo selftest）的支撑类。
 *
 * <p>把原手测清单中的语义回归场景命令化：自动建任务/挂触发器（预生成）→
 * 直调服务端事件入口或存储链路 → 断言状态（自动验证）。结果以
 * {@code [CMD][CASE ][PASS/FAIL]} 标签行输出（与离线 TestMain 框架同格式，
 * 面板 logparse 可直接解析），末尾输出 {@code SELFTEST SUMMARY} 汇总行供
 * verify-all 判定。
 *
 * <p>两档场景：控制台档（无需玩家，可在专用服务器控制台执行）与玩家档
 * （需要执行玩家，驱动 {@code TaskTriggerService.handle*} 事件入口）。
 * 所有场景使用独立数据（selftest 前缀任务 + 测试 UUID / 快照恢复），不污染真实数据。
 *
 * <p>边界：本类直调事件入口，验证「事件入口 → 触发引擎 → H2 存储」链路；
 * Mixin 真实注入层（真实合成/掉落）不在此覆盖，由 L1 启动冒烟与视觉抽查兜底。
 */
public final class SelfTestSupport {
    private SelfTestSupport() {
    }

    /** verify-all 轮询判定的汇总 marker（非标签行）。 */
    public static final String SUMMARY_MARKER = "SELFTEST SUMMARY";
    /** 自检专用 UUID（控制台档的存储隔离空间）。 */
    private static final UUID SELFTEST_UUID =
            UUID.nameUUIDFromBytes("todolist-selftest".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String CASE_PREFIX = "selftest.";
    private static final String TASK_MARK = "selftest-";

    /** 单场景定义。 */
    private record SelfCase(String id, String title, boolean requiresPlayer, CaseBody body) {
    }

    @FunctionalInterface
    private interface CaseBody {
        void run(MinecraftServer server, ServerPlayer player) throws Exception;
    }

    /** 单场景执行结果。 */
    public record CaseResult(String id, String title, boolean pass, long ms, String detail) {
    }

    /** 套件执行汇总。 */
    public record SuiteResult(int pass, int fail, List<CaseResult> results) {
        public int total() {
            return pass + fail;
        }
    }

    private static class SelfTestAssertion extends AssertionError {
        private SelfTestAssertion(String message) {
            super(message);
        }
    }

    private static void check(boolean cond, String detail) {
        if (!cond) {
            throw new SelfTestAssertion(detail);
        }
    }

    /**
     * 是否处于自动模式：系统属性 todolist.selftest.auto 或环境变量
     * TODOLIST_SELFTEST_AUTO 置 1/true（服务端启动完成后自动执行控制台档）。
     */
    public static boolean isAutoMode() {
        return flagValue("todolist.selftest.auto", "TODOLIST_SELFTEST_AUTO");
    }

    /** 自动模式下自检完成后是否自动停服（todolist.selftest.stop / TODOLIST_SELFTEST_STOP）。 */
    static boolean isAutoStop() {
        return flagValue("todolist.selftest.stop", "TODOLIST_SELFTEST_STOP");
    }

    private static boolean flagValue(String propKey, String envKey) {
        String v = System.getProperty(propKey, "");
        if (v.isEmpty()) {
            v = System.getenv(envKey) == null ? "" : System.getenv(envKey);
        }
        return v.equals("1") || v.equalsIgnoreCase("true");
    }

    /** 场景注册表（顺序即执行顺序）。 */
    private static final List<SelfCase> CASES = List.of(
            // ---- 控制台档（无需玩家） ----
            new SelfCase("storage-availability", "H2 存储可用与健康检查", false,
                    SelfTestSupport::caseStorageAvailability),
            new SelfCase("h2-roundtrip", "任务含触发器落库重读一致", false,
                    SelfTestSupport::caseH2Roundtrip),
            new SelfCase("trigger-machine", "真实 H2 往返后的父任务级联状态机", false,
                    SelfTestSupport::caseTriggerMachine),
            new SelfCase("merge-assignee-reset", "领取人变更清零与引擎进度合并", false,
                    SelfTestSupport::caseMergeAssigneeReset),
            // ---- 玩家档（需要执行玩家） ----
            new SelfCase("trigger-collect", "收集触发器：背包塞入即推进并完成", true,
                    SelfTestSupport::caseTriggerCollect),
            new SelfCase("trigger-first-load", "先持有后建触发器应立即完成", true,
                    SelfTestSupport::caseTriggerFirstLoad),
            new SelfCase("trigger-craft", "合成触发器累计语义", true,
                    SelfTestSupport::caseTriggerCraft),
            new SelfCase("trigger-break", "破坏方块触发器累计语义", true,
                    SelfTestSupport::caseTriggerBreak),
            new SelfCase("trigger-kill", "击杀触发器累计语义", true,
                    SelfTestSupport::caseTriggerKill),
            new SelfCase("trigger-advancement", "进度触发器一次性上报", true,
                    SelfTestSupport::caseTriggerAdvancement),
            new SelfCase("persist-roundtrip", "引擎进度落库后重载保持", true,
                    SelfTestSupport::casePersistRoundtrip),
            new SelfCase("trigger-cascade", "父任务级联完成真实数据版", true,
                    SelfTestSupport::caseTriggerCascade)
    );

    /** 列出场景 id（requiresPlayer 过滤；includePlayer 为 false 时只给控制台档）。 */
    public static List<String> caseIds(boolean includePlayer) {
        List<String> ids = new ArrayList<>();
        for (SelfCase c : CASES) {
            if (includePlayer || !c.requiresPlayer()) {
                ids.add(c.id());
            }
        }
        return ids;
    }

    /**
     * 执行自检套件，输出标签行与汇总。
     *
     * @param server   当前服务端
     * @param player   执行玩家（null = 控制台模式，只跑控制台档）
     * @param onlyId   只跑指定场景；null 或 "all" 表示全部
     * @param lineSink 每行输出的出口（命令反馈 / 日志）
     * @return 套件汇总
     */
    public static SuiteResult runSuite(MinecraftServer server, ServerPlayer player,
                                       String onlyId, Consumer<String> lineSink) {
        boolean includePlayer = player != null;
        List<SelfCase> selected = new ArrayList<>();
        for (SelfCase c : CASES) {
            if (c.requiresPlayer() && !includePlayer) {
                continue;
            }
            if (onlyId != null && !"all".equals(onlyId) && !c.id().equals(onlyId)) {
                continue;
            }
            selected.add(c);
        }
        List<CaseResult> results = new ArrayList<>();
        int pass = 0;
        int fail = 0;
        for (SelfCase c : selected) {
            String name = CASE_PREFIX + c.id();
            lineSink.accept("[CMD][CASE ][RUN] " + name);
            long start = System.nanoTime();
            CaseResult r;
            try {
                c.body().run(server, player);
                long ms = (System.nanoTime() - start) / 1_000_000L;
                r = new CaseResult(c.id(), c.title(), true, ms, null);
                lineSink.accept("[CMD][CASE ][PASS] " + name + " (" + ms + " ms)");
            } catch (Throwable t) {
                long ms = (System.nanoTime() - start) / 1_000_000L;
                r = new CaseResult(c.id(), c.title(), false, ms, String.valueOf(t.getMessage()));
                lineSink.accept("[CMD][CASE ][FAIL] " + name + " (" + ms + " ms)");
                lineSink.accept("    失败详情: " + t.getMessage());
            }
            if (r.pass()) {
                pass++;
            } else {
                fail++;
            }
            results.add(r);
        }
        long totalMs = results.stream().mapToLong(CaseResult::ms).sum();
        lineSink.accept("[CMD][GROUP][" + (fail == 0 ? "PASS" : "FAIL") + "] SELFTEST ("
                + totalMs + " ms)");
        lineSink.accept(SUMMARY_MARKER + " pass=" + pass + " fail=" + fail
                + " total=" + results.size()
                + (includePlayer ? " mode=player" : " mode=console"));
        return new SuiteResult(pass, fail, results);
    }

    /** 启动钩子入口：控制台档自动执行（走日志输出），autoStop 模式下跑完自动停服。 */
    public static void runConsoleSuite(MinecraftServer server) {
        server.execute(() -> {
            TodoConstants.LOGGER.info("[selftest] 自动模式：开始执行控制台档自检（{} 个场景）",
                    caseIds(false).size());
            SuiteResult result = runSuite(server, null, "all", line -> TodoConstants.LOGGER.info(line));
            if (isAutoStop()) {
                TodoConstants.LOGGER.info("[selftest] autoStop：自检完成，准备停服");
                server.execute(() -> server.halt(false));
            }
        });
    }

    // ==================== 控制台档场景 ====================

    private static void caseStorageAvailability(MinecraftServer server, ServerPlayer player) {
        H2StorageAvailability.StatusSnapshot snap = H2StorageAvailability.getStatusSnapshot(
                new H2ConnectionProvider().getDatabaseBasePath());
        check(snap.isAvailable(), "H2 存储不可用: " + snap.getMessage());
    }

    private static void caseH2Roundtrip(MinecraftServer server, ServerPlayer player) throws Exception {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        UUID uuid = SELFTEST_UUID;
        List<Task> snapshot = storage.loadPersonalTasks(server, uuid);
        try {
            TaskManager tm = new TaskManager();
            Task parent = tm.addTask(TASK_MARK + "父任务", "");
            Task child = tm.addTask(TASK_MARK + "子任务", "");
            child.setParentTaskId(parent.getId());
            child.setTrigger(new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 10));
            child.getTrigger().setProgress(4);
            tm.addTask(child);
            storage.savePersonalTasks(server, uuid, tm.getAllTasks());

            List<Task> reloaded = storage.loadPersonalTasks(server, uuid);
            check(reloaded.size() == 2, "重读数量应為 2，实际 " + reloaded.size());
            Task reChild = reloaded.stream()
                    .filter(t -> t.getTrigger() != null).findFirst().orElse(null);
            check(reChild != null, "重读后应存在挂触发器的任务");
            check(reChild.getTrigger().getProgress() == 4,
                    "触发器进度应保持 4，实际 " + reChild.getTrigger().getProgress());
            check(reChild.getTrigger().getTargetCount() == 10, "触发器目标数量应保持 10");
            Task reParent = reloaded.stream()
                    .filter(t -> t.getId().equals(reChild.getParentTaskId())).findFirst().orElse(null);
            check(reParent != null, "父子关系应在往返后保持");
        } finally {
            storage.savePersonalTasks(server, uuid, snapshot);
        }
    }

    private static void caseTriggerMachine(MinecraftServer server, ServerPlayer player) throws Exception {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        UUID uuid = SELFTEST_UUID;
        List<Task> snapshot = storage.loadPersonalTasks(server, uuid);
        try {
            TaskManager tm = new TaskManager();
            Task parent = tm.addTask(TASK_MARK + "级联父", "");
            Task child = tm.addTask(TASK_MARK + "级联子", "");
            child.setParentTaskId(parent.getId());
            child.setTrigger(new TaskTrigger(TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_ingot", 5));
            child.getTrigger().setProgress(2);
            tm.addTask(child);

            // 状态机：级联完成只切完成态、不改写触发器进度
            tm.toggleTaskCompletion(parent.getId());
            check(child.isCompleted(), "勾选父任务应级联完成子任务");
            check(child.getTrigger().getProgress() == 2, "级联不应改写触发器进度");

            // 真实 H2 往返后再验证状态持久化
            storage.savePersonalTasks(server, uuid, tm.getAllTasks());
            TaskManager reloaded = new TaskManager();
            for (Task t : storage.loadPersonalTasks(server, uuid)) {
                reloaded.addTask(t);
            }
            check(reloaded.getTask(parent.getId()).isCompleted(), "完成态应持久化");
            check(reloaded.getTask(child.getId()).isCompleted(), "级联完成态应持久化");

            // 级联还原
            reloaded.toggleTaskCompletion(parent.getId());
            check(!reloaded.getTask(child.getId()).isCompleted(), "取消勾选应级联还原");
            check(reloaded.getTask(child.getId()).getTrigger().getProgress() == 2,
                    "取消后触发器进度仍不被改写");
        } finally {
            storage.savePersonalTasks(server, uuid, snapshot);
        }
    }

    private static void caseMergeAssigneeReset(MinecraftServer server, ServerPlayer player) {
        Task a = new Task(TASK_MARK + "领取变更", "");
        a.setScope(Task.Scope.TEAM);
        a.setAssigneeUuid("uuid-owner-a");
        a.setTrigger(new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 10));
        a.getTrigger().setProgress(5);

        List<Task> previous = new ArrayList<>();
        previous.add(cloneTask(a));
        List<Task> incoming = new ArrayList<>();
        Task changed = cloneTask(a);
        changed.setAssigneeUuid("uuid-owner-b");
        incoming.add(changed);

        TaskTriggerService.resetTriggerProgressOnAssigneeChange(previous, incoming);
        check(incoming.get(0).getTrigger().getProgress() == 0,
                "领取人变更后进度应清零");

        // merge 语义：引擎进度应并入即将保存的快照
        Task stored = cloneTask(a);
        stored.getTrigger().setProgress(0);
        List<Task> incoming2 = new ArrayList<>();
        incoming2.add(stored);
        TaskTriggerService.mergeTeamTriggerStateInto(incoming2);
        check(incoming2.get(0).getTrigger().getProgress() == 0,
                "merge 不应改变一致状态的任务进度");
    }

    // ==================== 玩家档场景 ====================

    private static void caseTriggerCollect(MinecraftServer server, ServerPlayer player) throws Exception {
        Task t = prepareTriggerTask(server, player,
                new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 32));
        try {
            // 清背包后引擎重算：无物品 → 进度 0
            List<ItemStack> backup = snapshotInventory(player);
            try {
                player.getInventory().clearContent();
                TaskTriggerService.handleInventoryChanged(player);
                TaskTriggerService.flushForPlayer(player);
                assertStoredProgress(server, player, t.getId(), 0, false, "空背包阶段");

                // 塞入 32 个铁锭 → 真实背包重读 → 推进并完成
                player.getInventory().add(new ItemStack(Items.IRON_INGOT, 32));
                TaskTriggerService.handleInventoryChanged(player);
                TaskTriggerService.flushForPlayer(player);
                assertStoredProgress(server, player, t.getId(), 32, true, "塞入 32 铁锭后");
            } finally {
                restoreInventory(player, backup);
                TaskTriggerService.handleInventoryChanged(player);
            }
        } finally {
            cleanupTask(server, player, t.getId());
        }
    }

    private static void caseTriggerFirstLoad(MinecraftServer server, ServerPlayer player) throws Exception {
        List<ItemStack> backup = snapshotInventory(player);
        Task t = null;
        try {
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.IRON_INGOT, 32));
            // 先持有物品，再创建触发器任务并保存
            t = prepareTriggerTask(server, player,
                    new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 32));
            TaskTriggerService.invalidatePersonal(server, player.getUUID());
            TaskTriggerService.handleInventoryChanged(player);
            TaskTriggerService.flushForPlayer(player);
            assertStoredProgress(server, player, t.getId(), 32, true, "已有存量时建触发器应立即完成");
        } finally {
            restoreInventory(player, backup);
            TaskTriggerService.handleInventoryChanged(player);
            if (t != null) {
                cleanupTask(server, player, t.getId());
            }
        }
    }

    private static void caseTriggerCraft(MinecraftServer server, ServerPlayer player) throws Exception {
        Task t = prepareTriggerTask(server, player,
                new TaskTrigger(TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_ingot", 5));
        try {
            TaskTriggerService.handleItemCrafted(player, "minecraft:iron_ingot", 3);
            TaskTriggerService.handleItemCrafted(player, "minecraft:iron_ingot", 2);
            TaskTriggerService.flushForPlayer(player);
            assertStoredProgress(server, player, t.getId(), 5, true, "合成 3+2 后");
        } finally {
            cleanupTask(server, player, t.getId());
        }
    }

    private static void caseTriggerBreak(MinecraftServer server, ServerPlayer player) throws Exception {
        Task t = prepareTriggerTask(server, player,
                new TaskTrigger(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 3));
        try {
            for (int i = 0; i < 3; i++) {
                TaskTriggerService.handleBlockBroken(player, "minecraft:stone");
            }
            TaskTriggerService.flushForPlayer(player);
            assertStoredProgress(server, player, t.getId(), 3, true, "破坏 3 次后");
        } finally {
            cleanupTask(server, player, t.getId());
        }
    }

    private static void caseTriggerKill(MinecraftServer server, ServerPlayer player) throws Exception {
        Task t = prepareTriggerTask(server, player,
                new TaskTrigger(TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie", 2));
        try {
            TaskTriggerService.handleEntityKilled(player, "minecraft:zombie");
            TaskTriggerService.handleEntityKilled(player, "minecraft:zombie");
            TaskTriggerService.flushForPlayer(player);
            assertStoredProgress(server, player, t.getId(), 2, true, "击杀 2 次后");
        } finally {
            cleanupTask(server, player, t.getId());
        }
    }

    private static void caseTriggerAdvancement(MinecraftServer server, ServerPlayer player) throws Exception {
        Task t = prepareTriggerTask(server, player,
                new TaskTrigger(TaskTrigger.Type.ADVANCEMENT, "minecraft:story/mine_stone", 1));
        try {
            TaskTriggerService.handleAdvancementAwarded(player, "minecraft:story/mine_stone");
            TaskTriggerService.flushForPlayer(player);
            assertStoredProgress(server, player, t.getId(), 1, true, "达成进度后");
        } finally {
            cleanupTask(server, player, t.getId());
        }
    }

    private static void casePersistRoundtrip(MinecraftServer server, ServerPlayer player) throws Exception {
        Task t = prepareTriggerTask(server, player,
                new TaskTrigger(TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_ingot", 10));
        try {
            TaskTriggerService.handleItemCrafted(player, "minecraft:iron_ingot", 6);
            TaskTriggerService.flushForPlayer(player);
            // 清引擎桶模拟重进世界，重载后进度应保持且可继续推进
            TaskTriggerService.invalidatePersonal(server, player.getUUID());
            TaskTriggerService.handleItemCrafted(player, "minecraft:iron_ingot", 4);
            TaskTriggerService.flushForPlayer(player);
            assertStoredProgress(server, player, t.getId(), 10, true, "桶清空重载后继续推进");
        } finally {
            cleanupTask(server, player, t.getId());
        }
    }

    private static void caseTriggerCascade(MinecraftServer server, ServerPlayer player) throws Exception {
        UUID uuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        List<Task> snapshot = storage.loadPersonalTasks(server, uuid);
        try {
            Task parent = new Task(TASK_MARK + "级联父", "");
            Task child = new Task(TASK_MARK + "级联子", "");
            child.setParentTaskId(parent.getId());
            child.setTrigger(new TaskTrigger(TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie", 5));
            child.getTrigger().setProgress(1);
            TaskManager tm = new TaskManager();
            tm.addTask(parent);
            tm.addTask(child);
            TaskTriggerService.mergeTriggerStateInto(server, uuid, tm.getAllTasks());
            storage.savePersonalTasks(server, uuid, tm.getAllTasks());
            TaskTriggerService.invalidatePersonal(server, uuid);

            // 手动勾选父任务 → 级联完成真实数据
            TaskManager working = new TaskManager();
            for (Task t : storage.loadPersonalTasks(server, uuid)) {
                working.addTask(t);
            }
            working.toggleTaskCompletion(parent.getId());
            TaskTriggerService.mergeTriggerStateInto(server, uuid, working.getAllTasks());
            storage.savePersonalTasks(server, uuid, working.getAllTasks());

            TaskManager verify = new TaskManager();
            for (Task t : storage.loadPersonalTasks(server, uuid)) {
                verify.addTask(t);
            }
            check(verify.getTask(parent.getId()).isCompleted(), "父任务应已完成");
            check(verify.getTask(child.getId()).isCompleted(), "子任务应被级联完成");
            check(verify.getTask(child.getId()).getTrigger().getProgress() == 1,
                    "级联不应改写触发器进度");
        } finally {
            TaskTriggerService.mergeTriggerStateInto(server, uuid, snapshot);
            storage.savePersonalTasks(server, uuid, snapshot);
            TaskTriggerService.invalidatePersonal(server, uuid);
        }
    }

    // ==================== 场景辅助 ====================

    /**
     * 在玩家个人空间创建一个挂触发器的 selftest 任务并落库（场景 finally 里 cleanupTask 清理）。
     */
    private static Task prepareTriggerTask(MinecraftServer server, ServerPlayer player,
                                           TaskTrigger trigger) throws Exception {
        UUID uuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        List<Task> tasks = storage.loadPersonalTasks(server, uuid);
        TaskManager tm = new TaskManager();
        for (Task t : tasks) {
            tm.addTask(t);
        }
        Task t = tm.addTask(TASK_MARK + trigger.getType().name().toLowerCase(Locale.ROOT), "");
        t.setTrigger(trigger);
        t.setAssigneeUuid(uuid.toString());
        TaskTriggerService.mergeTriggerStateInto(server, uuid, tm.getAllTasks());
        storage.savePersonalTasks(server, uuid, tm.getAllTasks());
        TaskTriggerService.invalidatePersonal(server, uuid);
        return t;
    }

    /** 删除 selftest 任务并落库、清桶（场景收尾）。 */
    private static void cleanupTask(MinecraftServer server, ServerPlayer player, String taskId) {
        try {
            UUID uuid = player.getUUID();
            TaskStorage storage = TodoListCommon.getTaskStorage();
            List<Task> tasks = storage.loadPersonalTasks(server, uuid);
            tasks.removeIf(t -> t.getId().equals(taskId) || taskId.equals(t.getParentTaskId()));
            storage.savePersonalTasks(server, uuid, tasks);
            TaskTriggerService.invalidatePersonal(server, uuid);
        } catch (Exception e) {
            TodoConstants.LOGGER.warn("[selftest] 清理任务失败: {}", e.getMessage());
        }
    }

    /** 从存储重读指定任务，断言触发器进度与完成态。 */
    private static void assertStoredProgress(MinecraftServer server, ServerPlayer player,
                                             String taskId, int progress, boolean completed,
                                             String stage) throws Exception {
        List<Task> tasks = TodoListCommon.getTaskStorage().loadPersonalTasks(server, player.getUUID());
        Task t = tasks.stream().filter(x -> x.getId().equals(taskId)).findFirst().orElse(null);
        check(t != null, stage + ": 任务应存在于存储");
        check(t.getTrigger() != null && t.getTrigger().getProgress() == progress,
                stage + ": 进度应为 " + progress + "，实际 "
                        + (t.getTrigger() == null ? "无触发器" : t.getTrigger().getProgress()));
        check(t.isCompleted() == completed,
                stage + ": 完成态应为 " + completed + "，实际 " + t.isCompleted());
    }

    /** 克隆任务用于数据语义场景（隔离内存引用，保留 id 以便按 id 匹配的方法生效）。 */
    private static Task cloneTask(Task source) {
        Task copy = new Task(source.getTitle(), source.getDescription());
        copy.setId(source.getId());
        copy.setScope(source.getScope());
        copy.setAssigneeUuid(source.getAssigneeUuid());
        if (source.getTrigger() != null) {
            TaskTrigger trigger = new TaskTrigger(source.getTrigger().getType(),
                    source.getTrigger().getTarget(), source.getTrigger().getTargetCount());
            trigger.setProgress(source.getTrigger().getProgress());
            copy.setTrigger(trigger);
        }
        return copy;
    }

    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        List<ItemStack> backup = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            backup.add(player.getInventory().getItem(i).copy());
        }
        return backup;
    }

    private static void restoreInventory(ServerPlayer player, List<ItemStack> backup) {
        player.getInventory().clearContent();
        for (int i = 0; i < backup.size() && i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = backup.get(i);
            if (!stack.isEmpty()) {
                player.getInventory().setItem(i, stack.copy());
            }
        }
        player.getInventory().setChanged();
    }
}
