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
 *         目标物与中间产物全部生成「收集」任务（挂 {@code ITEM_COLLECT}，持有量语义）。
 *         最终目标是「拿到这个材料」，怎么拿到都算达成：配方（含功能方块）只是提示，不强制照做
 *         （Phase E12）。因此每条任务都由自身触发器决定完成态，触发器判定优先于子任务聚合，
 *         不会因为子任务完成而被自动完成；同种材料在整棵材料树里只生成一条任务，
 *         挂在深度优先顺序里首次出现的位置、数量取汇总需求（避免多条任务共享同一份进度）。</li>
 * </ul>
 *
 * <p>任务的**标题**由 {@link MaterialTaskTitleProvider} 渲染，并按节点首次出现位置的配方类型
 * 提示「用哪个功能方块做」（如「[熔炉] 熔炼 [铁锭] ×27」）；没有配方时标题为「收集 …」。
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
     * 目标物为根任务，被展开的中间产物与其下的材料为子任务，**全部挂 `ITEM_COLLECT`**
     * （持有量语义：拿到就算达成，配方只是提示）；触发器判定优先于子任务聚合。
     *
     * <p>整棵树里同种材料只生成一条任务，数量取全树汇总，
     * 避免同种物品的多条任务互相争抢同一份进度。
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
            tasks.add(createTriggeredTask(targetItemId, Math.max(1, targetCount), null, context));
            return tasks;
        }
        Map<String, Integer> totalCountsByItem = new LinkedHashMap<>();
        Map<String, MaterialRecipeKind> recipeKindByItem = new LinkedHashMap<>();
        collectNodeTotals(plan.root(), plan, totalCountsByItem, recipeKindByItem);
        appendSubtreeTasks(plan.root(), null, plan, context, tasks,
                new LinkedHashMap<>(), totalCountsByItem, recipeKindByItem, new LinkedHashSet<>());
        return tasks;
    }

    /**
     * 汇总整棵材料树里每个物品的总需求数量，并记录它首次出现位置的配方类型（供标题提示）。
     *
     * <p>遍历规则与建任务完全一致（见 {@link #appendSubtreeTasks}）：被玩家取消勾选的叶子材料不参与，
     * 其余节点无论是不是「最终材料」都计入。这样每条任务的数量都等于该物品在整棵树里的汇总需求，
     * 而不是各分支各自的数量。
     *
     * @param node            当前材料节点
     * @param plan            材料计划（其中 {@code leafTotals} 已按勾选结果过滤）
     * @param totals          输出：物品资源 ID → 汇总需求数量
     * @param recipeKindByItem 输出：物品资源 ID → 首次出现位置的配方类型（无配方时为 null）；
     *                         用 {@code containsKey} 区分「没有配方」与「从未出现」
     */
    private static void collectNodeTotals(MaterialNode node,
                                          MaterialPlan plan,
                                          Map<String, Integer> totals,
                                          Map<String, MaterialRecipeKind> recipeKindByItem) {
        if (node == null) {
            return;
        }
        String itemId = node.itemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (node.isLeaf() && !plan.leafTotals().containsKey(itemId)) {
            // 玩家取消勾选的材料不生成任务，也不计入汇总
            return;
        }
        totals.merge(itemId, Math.max(1, node.requiredCount()), Integer::sum);
        if (!recipeKindByItem.containsKey(itemId)) {
            // 标题里的配方提示取「首次出现」位置的配方，与任务落位（首次出现处）保持一致
            MaterialRecipe recipe = node.recipe();
            recipeKindByItem.put(itemId, recipe == null ? null : recipe.kind());
        }
        for (MaterialNode child : node.children()) {
            collectNodeTotals(child, plan, totals, recipeKindByItem);
        }
    }

    /**
     * 递归把材料树节点转换为任务：前序输出（父任务在前、子任务紧随其后）。
     *
     * <p>被玩家在预览中取消勾选的最终材料不会生成任务；若某个节点展开后的材料全部被取消勾选，
     * 该节点退化为最终材料，仍生成一条「收集」任务。
     *
     * <p>同种材料可能出现在多个层级或分支上：只在**首次出现**的位置生成一条任务，
     * 数量取该材料在全树的汇总需求，标题里的配方提示取该物品首次出现位置的配方类型。
     * 否则多条任务各自动作，会出现"背包里只有一半数量却把几条任务全部判为完成"的误判。
     *
     * <p>重复出现的节点不再建任务，但仍会继续下钻，把更深的材料挂到最近的已生成祖先任务下，
     * 避免漏掉只在重复分支里出现的材料。
     *
     * @param node                当前材料节点
     * @param parentTaskId        最近的已生成祖先任务 ID；根节点传 null
     * @param plan                材料计划（其中 {@code leafTotals} 已按勾选结果过滤）
     * @param context             生成上下文
     * @param tasks               输出任务列表
     * @param nextSortOrderByParent 每个父任务下的下一个子任务排序号
     * @param totalCountsByItem   每个物品在全树的汇总需求数量
     * @param recipeKindByItem    每个物品首次出现位置的配方类型
     * @param emittedItemIds      已生成过任务的物品（避免同种材料重复建任务）
     */
    private static void appendSubtreeTasks(MaterialNode node,
                                           String parentTaskId,
                                           MaterialPlan plan,
                                           MaterialTaskContext context,
                                           List<Task> tasks,
                                           Map<String, Long> nextSortOrderByParent,
                                           Map<String, Integer> totalCountsByItem,
                                           Map<String, MaterialRecipeKind> recipeKindByItem,
                                           Set<String> emittedItemIds) {
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
        if (!emittedItemIds.add(itemId)) {
            // 同种材料的任务已在靠上的位置生成过：这里不再重复建任务，
            // 但继续下钻，让更深层的材料挂到最近的已生成祖先任务下，避免漏掉任务。
            for (MaterialNode child : node.children()) {
                appendSubtreeTasks(child, parentTaskId, plan, context, tasks,
                        nextSortOrderByParent, totalCountsByItem, recipeKindByItem, emittedItemIds);
            }
            return;
        }
        int count = Math.max(1, totalCountsByItem.getOrDefault(itemId, node.requiredCount()));
        Task task = createTriggeredTask(itemId, count, recipeKindByItem.get(itemId), context);
        if (parentTaskId != null && !parentTaskId.isEmpty()) {
            long sortOrder = nextSortOrderByParent.getOrDefault(parentTaskId, 0L);
            nextSortOrderByParent.put(parentTaskId, sortOrder + 1L);
            task.setParentTaskId(parentTaskId);
            task.setSubtaskSortOrder(sortOrder);
        }
        tasks.add(task);
        for (MaterialNode child : node.children()) {
            appendSubtreeTasks(child, task.getId(), plan, context, tasks,
                    nextSortOrderByParent, totalCountsByItem, recipeKindByItem, emittedItemIds);
        }
    }

    /**
     * 创建带触发器的任务。
     *
     * <p>材料反推生成的任务**一律用「收集」**（{@code ITEM_COLLECT}，持有量语义）：
     * 最终目标是「拿到这个材料」，通过配方制作只是其中一种获取方式，
     * 买、捡、交换同样达成目标，因此不强制「必须自己合成出来」。
     * 配方（含功能方块）以标题提示的形式呈现，见 {@link #resolveTitle}。
     *
     * @param itemId  物品资源 ID
     * @param count   数量
     * @param kind    该物品首次出现位置的配方类型，仅用于标题提示；无配方时为 null
     * @param context 生成上下文
     * @return 任务
     */
    private static Task createTriggeredTask(String itemId,
                                            int count,
                                            MaterialRecipeKind kind,
                                            MaterialTaskContext context) {
        Task task = baseTask(itemId, count, kind, context);
        task.setTrigger(new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, itemId, count));
        return task;
    }

    /**
     * 创建任务骨架并写入项目/作用域/归属信息。
     *
     * @param itemId  物品资源 ID
     * @param count   数量
     * @param kind    该物品的配方类型，仅用于标题提示；无配方时为 null
     * @param context 生成上下文
     * @return 任务
     */
    private static Task baseTask(String itemId,
                                 int count,
                                 MaterialRecipeKind kind,
                                 MaterialTaskContext context) {
        Task task = new Task(resolveTitle(itemId, count, kind, context), "");
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
     * @param kind    该物品的配方类型（用于渲染「功能方块 + 动作词」提示）；无配方时为 null
     * @param context 生成上下文
     * @return 标题文本
     */
    private static String resolveTitle(String itemId,
                                       int count,
                                       MaterialRecipeKind kind,
                                       MaterialTaskContext context) {
        MaterialTaskTitleProvider provider = context.titleProvider();
        if (provider == null) {
            return itemId;
        }
        String title = provider.titleFor(itemId, count, kind);
        return title == null || title.isEmpty() ? itemId : title;
    }
}
