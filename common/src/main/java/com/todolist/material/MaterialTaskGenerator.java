package com.todolist.material;

import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 材料任务生成器：把 {@link MaterialPlan} 转换为可直接入库的任务列表。
 *
 * <p>两种模式（见设计文档 §3.4）：
 * <ul>
 *     <li>{@link MaterialTaskMode#TARGET_ONLY}：只生成目标物品任务，触发器为
 *         {@code ITEM_COLLECT 目标物品 ×目标数量}，不使用计划内容；</li>
 *     <li>{@link MaterialTaskMode#TARGET_WITH_MATERIALS}：按材料树的展开层级生成依赖任务链——
 *         目标物与中间产物为「合成」任务（挂 {@code CRAFT_ITEM}，必须真的合成出来），
 *         最终材料为「收集」任务（挂 {@code ITEM_COLLECT}，持有量语义）。
 *         每条任务都由自身触发器决定完成态，触发器判定优先于子任务聚合，
 *         不会因为子任务完成而被自动完成；同种最终材料跨层级/分支重复出现时只生成一条收集任务，
 *         数量取汇总需求（避免多条收集任务共享同一份持有量）。</li>
 * </ul>
 *
 * <p>生成结果是**纯数据**：不写存储、不发网络包，由调用方（预览界面）确认后走现有保存链路。
 * 所有任务共用 {@link MaterialTaskContext} 的项目、作用域与归属信息；子任务通过
 * {@code parentTaskId} / {@code subtaskSortOrder} 挂到父任务下。
 */
public final class MaterialTaskGenerator {

    /**
     * 工具类不需要实例化。
     */
    private MaterialTaskGenerator() {
    }

    /**
     * 生成任务列表：按材料树的层级生成依赖任务（模式 B），或只生成目标任务（模式 A）。
     *
     * <p>模式 B 下任务层级与预览中已展开的材料层级一一对应：
     * 目标物为根任务，被展开的中间产物为「合成」任务（挂 {@code CRAFT_ITEM}），
     * 无需再展开的最终材料为「收集」任务（挂 {@code ITEM_COLLECT}）；
     * 触发器判定优先于子任务聚合。
     *
     * @param plan         材料计划（模式 A 不使用）
     * @param targetItemId 目标物品资源 ID
     * @param targetCount  目标数量，最小为 1
     * @param mode         生成模式，为 null 时按仅目标任务处理
     * @param context      生成上下文
     * @return 任务列表；入参非法时返回空列表
     */
    public static List<Task> generate(MaterialPlan plan,
                                     String targetItemId,
                                     int targetCount,
                                     MaterialTaskMode mode,
                                     MaterialTaskContext context) {
        List<Task> tasks = new ArrayList<>();
        if (targetItemId == null || targetItemId.isEmpty() || context == null) {
            return tasks;
        }
        MaterialTaskMode safeMode = mode == null ? MaterialTaskMode.TARGET_ONLY : mode;
        if (safeMode != MaterialTaskMode.TARGET_WITH_MATERIALS || plan == null || plan.root() == null) {
            tasks.add(createTriggeredTask(targetItemId, Math.max(1, targetCount), true, context));
            return tasks;
        }
        Map<String, Integer> finalMaterialTotals = new LinkedHashMap<>();
        collectFinalMaterialTotals(plan.root(), plan, finalMaterialTotals);
        appendSubtreeTasks(plan.root(), null, plan, context, tasks,
                new LinkedHashMap<>(), finalMaterialTotals, new LinkedHashSet<>());
        return tasks;
    }

    /**
     * 汇总每个最终材料的总需求数量，供「同种材料只生成一条收集任务」使用。
     *
     * <p>遍历规则与建任务保持一致：叶子材料、以及展开结果全被取消勾选而退化为最终材料的中间产物
     * 都算最终材料，且不再向下遍历；被取消勾选的叶子材料不计入。
     *
     * @param node   当前材料节点
     * @param plan   材料计划
     * @param totals 输出：物品资源 ID → 汇总数量
     */
    private static void collectFinalMaterialTotals(MaterialNode node,
                                                   MaterialPlan plan,
                                                   Map<String, Integer> totals) {
        if (node == null) {
            return;
        }
        String itemId = node.itemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (node.isLeaf() && !plan.leafTotals().containsKey(itemId)) {
            return;
        }
        if (node.isLeaf() || !hasSelectedLeaf(node, plan)) {
            totals.merge(itemId, Math.max(1, node.requiredCount()), Integer::sum);
            return;
        }
        for (MaterialNode child : node.children()) {
            collectFinalMaterialTotals(child, plan, totals);
        }
    }

    /**
     * 递归把材料树节点转换为任务：前序输出（父任务在前、子任务紧随其后）。
     *
     * <p>被玩家在预览中取消勾选的最终材料不会生成任务；若某个节点展开后的材料全部被取消勾选，
     * 该节点退化为最终材料，改为生成「收集」任务。
     *
     * <p>同种材料可能出现在多个层级或分支上：只在**首次出现**的位置生成一条「收集」任务，
     * 数量取该材料的汇总需求。否则多条任务各自用「持有量」语义比较，会出现"背包里只有一半
     * 数量却把几条任务全部判为完成"的误判。
     *
     * @param node                当前材料节点
     * @param parentTaskId        父任务 ID；根节点传 null
     * @param plan                材料计划（其中 {@code leafTotals} 已按勾选结果过滤）
     * @param context             生成上下文
     * @param tasks               输出任务列表
     * @param nextSortOrderByParent 每个父任务下的下一个子任务排序号
     * @param finalMaterialTotals 每个最终材料的汇总需求
     * @param emittedFinalItems   已生成过收集任务的最终材料（避免同种材料重复建任务）
     */
    private static void appendSubtreeTasks(MaterialNode node,
                                           String parentTaskId,
                                           MaterialPlan plan,
                                           MaterialTaskContext context,
                                           List<Task> tasks,
                                           Map<String, Long> nextSortOrderByParent,
                                           Map<String, Integer> finalMaterialTotals,
                                           Set<String> emittedFinalItems) {
        if (node == null) {
            return;
        }
        String itemId = node.itemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (node.isLeaf() && !plan.leafTotals().containsKey(itemId)) {
            // 玩家取消勾选的材料不生成任务
            return;
        }
        int count = Math.max(1, node.requiredCount());
        // 叶子节点、以及展开结果全被取消勾选的节点都作为最终材料，用「收集」语义（ITEM_COLLECT），
        // 其余用「合成」语义（CRAFT_ITEM）；完成态由触发器决定且优先于子任务聚合。
        boolean collect = node.isLeaf() || !hasSelectedLeaf(node, plan);
        if (collect) {
            Integer mergedCount = finalMaterialTotals.get(itemId);
            if (mergedCount != null) {
                count = Math.max(1, mergedCount);
            }
            if (!emittedFinalItems.add(itemId)) {
                // 同种材料的收集任务已在靠上的位置生成过，这里不再重复
                return;
            }
        }
        Task task = createTriggeredTask(itemId, count, collect, context);
        if (parentTaskId != null && !parentTaskId.isEmpty()) {
            long sortOrder = nextSortOrderByParent.getOrDefault(parentTaskId, 0L);
            nextSortOrderByParent.put(parentTaskId, sortOrder + 1L);
            task.setParentTaskId(parentTaskId);
            task.setSubtaskSortOrder(sortOrder);
        }
        tasks.add(task);
        for (MaterialNode child : node.children()) {
            appendSubtreeTasks(child, task.getId(), plan, context, tasks,
                    nextSortOrderByParent, finalMaterialTotals, emittedFinalItems);
        }
    }

    /**
     * 判断该节点子树中是否还存在被勾选的最终材料。
     *
     * @param node 材料节点
     * @param plan 材料计划
     * @return 存在被勾选的最终材料时返回 true
     */
    private static boolean hasSelectedLeaf(MaterialNode node, MaterialPlan plan) {
        if (node == null) {
            return false;
        }
        if (node.isLeaf()) {
            return plan.leafTotals().containsKey(node.itemId());
        }
        for (MaterialNode child : node.children()) {
            if (hasSelectedLeaf(child, plan)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建带触发器的任务：按语义选择触发器类型。
     *
     * <ul>
     *     <li><b>收集</b>（最终材料，配方不再展开）→ {@code ITEM_COLLECT}：持有量语义，
     *         玩家已经拥有该材料时立即达标；</li>
     *     <li><b>合成</b>（目标物与仍被展开的中间产物）→ {@code CRAFT_ITEM}：累计合成量语义。
     *         玩家在预览里展开了这一步的配方材料，说明这一步确实需要自己合成出来，
     *         因此不能因为背包里恰好已有该物品就算完成。</li>
     * </ul>
     *
     * @param itemId  物品资源 ID
     * @param count   数量
     * @param collect true 表示最终材料的「收集」任务；false 表示目标物/中间产物的「合成」任务
     * @param context 生成上下文
     * @return 任务
     */
    private static Task createTriggeredTask(String itemId, int count, boolean collect, MaterialTaskContext context) {
        TaskTrigger.Type type = collect ? TaskTrigger.Type.ITEM_COLLECT : TaskTrigger.Type.CRAFT_ITEM;
        Task task = baseTask(itemId, count, collect, context);
        task.setTrigger(new TaskTrigger(type, itemId, count));
        return task;
    }

    /**
     * 创建任务骨架并写入项目/作用域/归属信息。
     *
     * @param itemId  物品资源 ID
     * @param count   数量
     * @param collect 是否为收集任务（仅影响标题文案）
     * @param context 生成上下文
     * @return 任务
     */
    private static Task baseTask(String itemId, int count, boolean collect, MaterialTaskContext context) {
        Task task = new Task(resolveTitle(itemId, count, collect, context), "");
        task.setProjectId(context.projectId());
        task.setScope(context.scope());
        task.setCreatorUuid(context.creatorUuid());
        task.setAssigneeUuid(context.assigneeUuid());
        task.setAssigneeName(context.assigneeName());
        return task;
    }

    /**
     * 生成任务标题：优先使用注入的渲染回调，缺失或返回空时回退为物品资源 ID。
     *
     * @param itemId  物品资源 ID
     * @param count   数量
     * @param collect 是否为收集任务
     * @param context 生成上下文
     * @return 标题文本
     */
    private static String resolveTitle(String itemId, int count, boolean collect, MaterialTaskContext context) {
        MaterialTaskTitleProvider provider = context.titleProvider();
        if (provider == null) {
            return itemId;
        }
        String title = provider.titleFor(itemId, count, collect);
        return title == null || title.isEmpty() ? itemId : title;
    }
}
