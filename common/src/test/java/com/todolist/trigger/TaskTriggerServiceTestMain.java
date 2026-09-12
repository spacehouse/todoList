package com.todolist.trigger;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TaskTriggerServiceTestMain 覆盖五种事件驱动型任务的引擎判定语义
 * （击杀 / 破坏方块 / 合成 / 收集 / 获得进度），走纯逻辑入口
 * {@code advanceMatchingTasksByUuid} / {@code recalculateItemCollectByUuid}，
 * 不依赖真实 ServerPlayer 与世界。
 */
public final class TaskTriggerServiceTestMain {
    /** 测试用玩家 A 的 UUID 文本。 */
    private static final String PLAYER_A = "50000000-0000-0000-0000-00000000000a";
    /** 测试用玩家 B 的 UUID 文本。 */
    private static final String PLAYER_B = "50000000-0000-0000-0000-00000000000b";

    /**
     * 工具类不需要实例化。
     */
    private TaskTriggerServiceTestMain() {
    }

    /**
     * 执行五种事件类型的引擎判定测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldCompleteKillEntityByAccumulatedKills", TaskTriggerServiceTestMain::shouldCompleteKillEntityByAccumulatedKills);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldAdvanceBreakBlockForTasksSharingTarget", TaskTriggerServiceTestMain::shouldAdvanceBreakBlockForTasksSharingTarget);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldAdvanceCraftItemByCraftedBatchAmount", TaskTriggerServiceTestMain::shouldAdvanceCraftItemByCraftedBatchAmount);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldTrackCollectProgressByAbsoluteHeldCount", TaskTriggerServiceTestMain::shouldTrackCollectProgressByAbsoluteHeldCount);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldCompleteAdvancementOnFirstAward", TaskTriggerServiceTestMain::shouldCompleteAdvancementOnFirstAward);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldIgnoreNonAssigneeEventsForTeamTask", TaskTriggerServiceTestMain::shouldIgnoreNonAssigneeEventsForTeamTask);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldSkipCompletedTasksInIndex", TaskTriggerServiceTestMain::shouldSkipCompletedTasksInIndex);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldTrackDirtyTasksForIncrementalFlush", TaskTriggerServiceTestMain::shouldTrackDirtyTasksForIncrementalFlush);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldPeekDirtyTasksWithoutConsumingForLightPush", TaskTriggerServiceTestMain::shouldPeekDirtyTasksWithoutConsumingForLightPush);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldIgnoreUnassignedTeamTaskForAllPlayers", TaskTriggerServiceTestMain::shouldIgnoreUnassignedTeamTaskForAllPlayers);
        GuiTestSupport.runTestCase("TaskTriggerServiceTestMain.shouldResetTriggerProgressWhenTeamAssigneeChanges", TaskTriggerServiceTestMain::shouldResetTriggerProgressWhenTeamAssigneeChanges);
    }

    /**
     * 击杀实体：累加语义，未达标持续推进，达标任务完成并移出索引。
     */
    private static void shouldCompleteKillEntityByAccumulatedKills() {
        Task task = newTask(TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie", 3);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(task);

        List<Task> completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie", 1);
        GuiTestSupport.assertTrue(completed.isEmpty(), "击杀 1/3 不应达标");
        GuiTestSupport.assertEquals(1, task.getTrigger().getProgress(), "击杀一次进度应为 1");
        GuiTestSupport.assertFalse(task.isCompleted(), "进度未达标任务不应完成");

        completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie", 2);
        GuiTestSupport.assertEquals(1, completed.size(), "累计击杀 3 次应触发一个任务完成");
        GuiTestSupport.assertTrue(task.isCompleted(), "击杀达标后任务应完成");
        GuiTestSupport.assertTrue(bucket.isDirty(), "进度变化应置脏桶");
        GuiTestSupport.assertTrue(bucket.find(TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie").isEmpty(),
                "达标任务应移出倒排索引");
    }

    /**
     * 破坏方块：同一目标被多个任务共享，其中一个达标移出索引时其余任务仍正常推进
     * （固化 Phase P 的 ConcurrentModificationException 防回归场景）。
     */
    private static void shouldAdvanceBreakBlockForTasksSharingTarget() {
        Task quick = newTask(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 1);
        Task slow = newTask(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 2);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(quick, slow);

        List<Task> completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 1);
        GuiTestSupport.assertEquals(1, completed.size(), "一次破坏只应完成数量为 1 的任务");
        GuiTestSupport.assertTrue(quick.isCompleted(), "数量 1 任务应完成");
        GuiTestSupport.assertFalse(slow.isCompleted(), "数量 2 任务不应完成");
        GuiTestSupport.assertEquals(1, slow.getTrigger().getProgress(), "共享目标的另一任务仍应推进到 1");
        GuiTestSupport.assertEquals(1, bucket.find(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone").size(),
                "索引中应只剩未完成任务");
    }

    /**
     * 合成物品：按本次合成产物数量累加（一次合成多个直接计入）。
     */
    private static void shouldAdvanceCraftItemByCraftedBatchAmount() {
        Task task = newTask(TaskTrigger.Type.CRAFT_ITEM, "minecraft:oak_planks", 4);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(task);

        List<Task> completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.CRAFT_ITEM, "minecraft:oak_planks", 2);
        GuiTestSupport.assertTrue(completed.isEmpty(), "合成 2/4 不应达标");
        GuiTestSupport.assertEquals(2, task.getTrigger().getProgress(), "批量合成应按数量累加");

        completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.CRAFT_ITEM, "minecraft:oak_planks", 4);
        GuiTestSupport.assertEquals(1, completed.size(), "再合成 4 个应达标");
        GuiTestSupport.assertEquals(4, task.getTrigger().getProgress(), "进度上限应收敛为目标数量");
    }

    /**
     * 收集物品：绝对持有量语义 —— 进度等于当前持有数、达标即完成、完成后持有量下降不回退。
     */
    private static void shouldTrackCollectProgressByAbsoluteHeldCount() {
        Task task = newTask(TaskTrigger.Type.ITEM_COLLECT, "minecraft:dirt", 32);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(task);

        List<Task> completed = TaskTriggerService.recalculateItemCollectByUuid(
                PLAYER_A, held("minecraft:dirt", 20), bucket);
        GuiTestSupport.assertTrue(completed.isEmpty(), "持有 20/32 不应达标");
        GuiTestSupport.assertEquals(20, task.getTrigger().getProgress(), "进度应等于当前持有量");

        completed = TaskTriggerService.recalculateItemCollectByUuid(
                PLAYER_A, held("minecraft:dirt", 40), bucket);
        GuiTestSupport.assertEquals(1, completed.size(), "持有超过目标数应达标");
        GuiTestSupport.assertTrue(task.isCompleted(), "持有量达标后任务应完成");
        GuiTestSupport.assertEquals(32, task.getTrigger().getProgress(), "进度上限应收敛为目标数量");

        TaskTriggerService.recalculateItemCollectByUuid(PLAYER_A, held("minecraft:dirt", 5), bucket);
        GuiTestSupport.assertTrue(task.isCompleted(), "完成后持有量下降不应回退完成态");
        GuiTestSupport.assertTrue(bucket.find(TaskTrigger.Type.ITEM_COLLECT, "minecraft:dirt").isEmpty(),
                "完成任务不应再参与收集重算");
    }

    /**
     * 获得进度：单次达标（数量恒为 1），再次上报不再匹配。
     */
    private static void shouldCompleteAdvancementOnFirstAward() {
        Task task = newTask(TaskTrigger.Type.ADVANCEMENT, "minecraft:story/mine_stone", 1);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(task);

        List<Task> completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.ADVANCEMENT, "minecraft:story/mine_stone", 1);
        GuiTestSupport.assertEquals(1, completed.size(), "达成进度应立即完成任务");
        GuiTestSupport.assertTrue(task.isCompleted(), "进度任务应完成");

        completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.ADVANCEMENT, "minecraft:story/mine_stone", 1);
        GuiTestSupport.assertTrue(completed.isEmpty(), "重复上报同一条进度不应再次匹配");
    }

    /**
     * 团队任务：非负责人事件不计入，负责人事件正常推进。
     */
    private static void shouldIgnoreNonAssigneeEventsForTeamTask() {
        Task teamTask = newTask(TaskTrigger.Type.KILL_ENTITY, "minecraft:skeleton", 2);
        teamTask.setScope(Task.Scope.TEAM);
        teamTask.setAssigneeUuid(PLAYER_A);
        TaskTriggerService.CachedBucket bucket = new TaskTriggerService.CachedBucket("T", null, List.of(teamTask));

        List<Task> completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_B, bucket, TaskTrigger.Type.KILL_ENTITY, "minecraft:skeleton", 1);
        GuiTestSupport.assertTrue(completed.isEmpty(), "非负责人击杀不应推进");
        GuiTestSupport.assertEquals(0, teamTask.getTrigger().getProgress(), "非负责人事件进度应保持 0");

        TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.KILL_ENTITY, "minecraft:skeleton", 1);
        GuiTestSupport.assertEquals(1, teamTask.getTrigger().getProgress(), "负责人击杀应推进");
    }

    /**
     * 已完成任务不进入倒排索引，事件到达时不参与匹配。
     */
    private static void shouldSkipCompletedTasksInIndex() {
        Task completedTask = newTask(TaskTrigger.Type.KILL_ENTITY, "minecraft:creeper", 1);
        completedTask.setCompleted(true);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(completedTask);

        GuiTestSupport.assertTrue(bucket.find(TaskTrigger.Type.KILL_ENTITY, "minecraft:creeper").isEmpty(),
                "已完成任务不应进入倒排索引");
        List<Task> completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.KILL_ENTITY, "minecraft:creeper", 1);
        GuiTestSupport.assertTrue(completed.isEmpty(), "已完成任务不应被事件再次匹配");
    }

    /**
     * 引擎推进过进度的任务会进入脏集合，供增量落库只更新这些行；
     * 未引起变化的事件不产生脏任务；取出后集合清空，失败可恢复。
     * （固化「引擎增量落库不覆盖其他保存路径新任务」的数据基础）
     */
    private static void shouldTrackDirtyTasksForIncrementalFlush() {
        Task advanced = newTask(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 2);
        Task untouched = newTask(TaskTrigger.Type.ITEM_COLLECT, "minecraft:dirt", 10);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(advanced, untouched);

        TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 1);
        GuiTestSupport.assertTrue(bucket.isDirty(), "推进后桶应置脏");

        List<Task> dirtyTasks = bucket.takeDirtyTasks();
        GuiTestSupport.assertEquals(1, dirtyTasks.size(), "只有被推进的任务应进入脏集合");
        GuiTestSupport.assertEquals(advanced.getId(), dirtyTasks.get(0).getId(), "脏任务应是破坏方块任务");
        GuiTestSupport.assertTrue(bucket.takeDirtyTasks().isEmpty(), "取出后脏集合应清空");

        bucket.restoreDirtyTasks(dirtyTasks);
        List<Task> restored = bucket.takeDirtyTasks();
        GuiTestSupport.assertEquals(1, restored.size(), "恢复后脏任务应可再次取出");

        // 未引起进度变化的事件（持有量与进度相同）不产生脏任务
        TaskTriggerService.recalculateItemCollectByUuid(PLAYER_A, Map.of("minecraft:dirt", 0), bucket);
        List<Task> noChange = bucket.takeDirtyTasks();
        GuiTestSupport.assertTrue(noChange.isEmpty(), "进度无变化的事件不应产生脏任务");
    }

    /**
     * 轻量推送读取脏任务时不得消费脏集合（Phase T 数据基础）：
     * 推送是旁路，若消费了脏集合，随后的增量落库将拿不到任务、导致进度丢失。
     */
    private static void shouldPeekDirtyTasksWithoutConsumingForLightPush() {
        Task advanced = newTask(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 2);
        TaskTriggerService.CachedBucket bucket = newPersonalBucket(advanced);

        TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 1);

        List<Task> firstPeek = bucket.peekDirtyTasks();
        List<Task> secondPeek = bucket.peekDirtyTasks();
        GuiTestSupport.assertEquals(1, firstPeek.size(), "被推进的任务应可被推送读到");
        GuiTestSupport.assertEquals(1, secondPeek.size(), "重复读取脏任务不应清空脏集合");
        GuiTestSupport.assertEquals(advanced.getId(), secondPeek.get(0).getId(), "推送读到的应是推进过的任务");

        List<Task> taken = bucket.takeDirtyTasks();
        GuiTestSupport.assertEquals(1, taken.size(), "推送后增量落库仍应拿到该任务");
        GuiTestSupport.assertTrue(bucket.peekDirtyTasks().isEmpty(), "落库取出后推送不应再读到脏任务");
    }

    /**
     * 未指派（待领取）的团队任务不参与任何事件触发。
     *
     * 团队任务遵循「创建 → 领取/指派 → 完成」流程：未指派时任何人做对应动作都不应
     * 推进共享进度，否则待领取状态失去意义；累加型与收集型都适用。
     */
    private static void shouldIgnoreUnassignedTeamTaskForAllPlayers() {
        Task unassigned = newTask(TaskTrigger.Type.KILL_ENTITY, "minecraft:pig", 1);
        unassigned.setScope(Task.Scope.TEAM);
        TaskTriggerService.CachedBucket bucket = new TaskTriggerService.CachedBucket("T", null, List.of(unassigned));

        List<Task> completed = TaskTriggerService.advanceMatchingTasksByUuid(
                PLAYER_A, bucket, TaskTrigger.Type.KILL_ENTITY, "minecraft:pig", 1);
        GuiTestSupport.assertTrue(completed.isEmpty(), "未指派团队任务不应被事件完成");
        GuiTestSupport.assertEquals(0, unassigned.getTrigger().getProgress(), "未指派团队任务进度应保持 0");
        GuiTestSupport.assertFalse(unassigned.isCompleted(), "未指派团队任务不应完成");
        GuiTestSupport.assertFalse(bucket.isDirty(), "未指派团队任务被跳过时不应置脏");

        Task unassignedCollect = newTask(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 8);
        unassignedCollect.setScope(Task.Scope.TEAM);
        TaskTriggerService.CachedBucket collectBucket =
                new TaskTriggerService.CachedBucket("T", null, List.of(unassignedCollect));

        List<Task> collectCompleted = TaskTriggerService.recalculateItemCollectByUuid(
                PLAYER_A, held("minecraft:iron_ingot", 8), collectBucket);
        GuiTestSupport.assertTrue(collectCompleted.isEmpty(), "未指派团队收集任务不应被库存重算完成");
        GuiTestSupport.assertEquals(0, unassignedCollect.getTrigger().getProgress(),
                "未指派团队收集任务进度应保持 0");
        GuiTestSupport.assertFalse(collectBucket.isDirty(), "未指派团队收集任务被跳过时不应置脏");
    }

    /**
     * 团队任务领取人变化时清零触发器进度：取消领取清零、改派重新开始；
     * 领取人未变、以及新加入的任务不受影响。
     */
    private static void shouldResetTriggerProgressWhenTeamAssigneeChanges() {
        Task previousAbandon = teamTaskWithTrigger("team-abandon", PLAYER_A, 2, 1);
        Task incomingAbandon = teamTaskWithTrigger("team-abandon", null, 2, 1);
        Task previousReassign = teamTaskWithTrigger("team-reassign", PLAYER_A, 2, 1);
        Task incomingReassign = teamTaskWithTrigger("team-reassign", PLAYER_B, 2, 1);
        Task previousKeep = teamTaskWithTrigger("team-keep", PLAYER_A, 2, 1);
        Task incomingKeep = teamTaskWithTrigger("team-keep", PLAYER_A, 2, 1);
        Task incomingNew = teamTaskWithTrigger("team-new", PLAYER_B, 2, 1);

        TaskTriggerService.resetTriggerProgressOnAssigneeChange(
                List.of(previousAbandon, previousReassign, previousKeep),
                List.of(incomingAbandon, incomingReassign, incomingKeep, incomingNew));

        GuiTestSupport.assertEquals(0, incomingAbandon.getTrigger().getProgress(), "取消领取后进度应清零");
        GuiTestSupport.assertFalse(incomingAbandon.isCompleted(), "取消领取后不应保持完成态");
        GuiTestSupport.assertEquals(0, incomingReassign.getTrigger().getProgress(), "改派给他人后进度应清零");
        GuiTestSupport.assertFalse(incomingReassign.isCompleted(), "改派后不应保持完成态");
        GuiTestSupport.assertEquals(1, incomingKeep.getTrigger().getProgress(), "领取人未变时进度应保留");
        GuiTestSupport.assertEquals(1, incomingNew.getTrigger().getProgress(), "新加入的任务不应被重置");

        // GUI 就地变更领取人（局域网主机直写本地存储路径）走单任务入口
        Task guiTask = teamTaskWithTrigger("team-gui", PLAYER_A, 2, 1);
        String guiPreviousAssignee = guiTask.getAssigneeUuid();
        guiTask.setAssigneeUuid(null);
        TaskTriggerService.resetTriggerProgressIfAssigneeChanged(guiPreviousAssignee, guiTask);
        GuiTestSupport.assertEquals(0, guiTask.getTrigger().getProgress(), "取消领取时 GUI 就地重置应生效");
        GuiTestSupport.assertFalse(guiTask.isCompleted(), "GUI 就地重置应清除完成态");

        // 个人任务不受领取人变更规则影响
        Task personalTask = newTask(TaskTrigger.Type.KILL_ENTITY, "minecraft:pig", 2);
        personalTask.getTrigger().setProgress(1);
        TaskTriggerService.resetTriggerProgressIfAssigneeChanged(PLAYER_A, personalTask);
        GuiTestSupport.assertEquals(1, personalTask.getTrigger().getProgress(), "个人任务不应被重置");
    }

    /**
     * 构造带触发器的团队任务，并指定任务 ID、领取人与当前进度。
     *
     * @param id           任务 ID（用于前后两份列表按 ID 配对）
     * @param assigneeUuid 领取人 UUID，null 表示未领取
     * @param count        目标数量
     * @param progress     当前进度
     * @return 团队任务
     */
    private static Task teamTaskWithTrigger(String id, String assigneeUuid, int count, int progress) {
        Task task = newTask(TaskTrigger.Type.KILL_ENTITY, "minecraft:pig", count);
        task.setId(id);
        task.setScope(Task.Scope.TEAM);
        task.setAssigneeUuid(assigneeUuid);
        task.getTrigger().setProgress(progress);
        return task;
    }

    /**
     * 创建带触发器的个人任务。
     *
     * @param type   触发类型
     * @param target 目标资源 ID
     * @param count  目标数量
     * @return 新任务
     */
    private static Task newTask(TaskTrigger.Type type, String target, int count) {
        Task task = new Task("测试任务", "");
        task.setTrigger(new TaskTrigger(type, target, count));
        return task;
    }

    /**
     * 创建个人桶（玩家 A）。
     *
     * @param tasks 桶内任务
     * @return 桶缓存
     */
    private static TaskTriggerService.CachedBucket newPersonalBucket(Task... tasks) {
        return new TaskTriggerService.CachedBucket("P:TEST", UUID.fromString(PLAYER_A), List.of(tasks));
    }

    /**
     * 构造持有量快照。
     *
     * @param itemId 物品资源 ID
     * @param count  持有数量
     * @return 单条目的持有量映射
     */
    private static Map<String, Integer> held(String itemId, int count) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put(itemId, count);
        return counts;
    }
}
