package com.todolist.material;

import com.todolist.task.Task;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 材料反推预览界面的状态容器（纯逻辑，可离线测试）。
 *
 * <p>持有界面上的全部可变状态：目标物品与数量、逐节点配方覆盖、逐节点展开/收起、
 * 玩家取消展开的节点、多候选材料的替换选择；并负责把这些状态转成解析选项、
 * 解析计划，以及最终生成任务。
 *
 * <p>生成任务的口径是「只按预览树显示出来的部分」：展开的节点会生成它的下级材料任务，
 * 收起的节点就是该分支的最终材料（仍生成它自己的任务）。因此不再有独立的生成模式，
 * 「只要目标一条任务」等价于把目标节点收起。
 *
 * <p>界面只负责渲染与事件分发，不重复实现这些语义，因此这部分可脱离 MC 运行时测试。
 */
public final class MaterialPreviewState {

    /** 目标物品资源 ID（不可变）。 */
    private final String targetItemId;
    /** 目标数量。 */
    private int targetCount;
    /** 物品资源 ID → 指定配方 ID（逐节点切换配方的结果）。 */
    private final Map<String, String> recipeOverrides = new LinkedHashMap<>();
    /** 被玩家标记为「到此为止」（收起下级配方）的物品资源 ID。 */
    private final Set<String> stopAtItems = new LinkedHashSet<>();
    /** 被玩家要求「继续展开」（展开下级配方）的物品资源 ID。 */
    private final Set<String> forceExpandItems = new LinkedHashSet<>();
    /** 候选组键（默认代表物品 ID）→ 玩家选定的替代物品 ID。 */
    private final Map<String, String> candidateOverrides = new LinkedHashMap<>();
    /** 玩家当前已有的物品（一般是背包内容），用于候选物品的默认优先选择。 */
    private Set<String> preferredItems = Set.of();

    /**
     * 创建预览状态。
     *
     * @param targetItemId 目标物品资源 ID
     * @param targetCount  目标数量，最小为 1
     */
    public MaterialPreviewState(String targetItemId, int targetCount) {
        this.targetItemId = targetItemId == null ? "" : targetItemId;
        this.targetCount = Math.max(1, targetCount);
    }

    /**
     * 返回目标物品资源 ID。
     *
     * @return 物品资源 ID
     */
    public String getTargetItemId() {
        return targetItemId;
    }

    /**
     * 返回目标数量。
     *
     * @return 目标数量
     */
    public int getTargetCount() {
        return targetCount;
    }

    /**
     * 更新目标数量。
     *
     * @param targetCount 目标数量，最小为 1
     */
    public void setTargetCount(int targetCount) {
        this.targetCount = Math.max(1, targetCount);
    }

    /**
     * 返回只读的配方覆盖表。
     *
     * @return 物品资源 ID → 配方 ID
     */
    public Map<String, String> getRecipeOverrides() {
        return Map.copyOf(recipeOverrides);
    }

    /**
     * 返回只读的「到此为止」集合。
     *
     * @return 物品资源 ID 集合
     */
    public Set<String> getStopAtItems() {
        return Set.copyOf(stopAtItems);
    }

    /**
     * 返回只读的「继续展开」集合。
     *
     * @return 物品资源 ID 集合
     */
    public Set<String> getForceExpandItems() {
        return Set.copyOf(forceExpandItems);
    }

    /**
     * 设置或取消「继续展开」：既用于展开默认折叠（{@code MaterialStopReason.FOLDED}）的层级，
     * 也用于对熔炼类配方的输入强制继续反推。
     *
     * @param itemId 物品资源 ID
     * @param expand 为 true 时要求继续展开，为 false 时恢复默认折叠行为
     */
    public void setForceExpand(String itemId, boolean expand) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (expand) {
            forceExpandItems.add(itemId);
        } else {
            forceExpandItems.remove(itemId);
        }
    }

    /**
     * 判断某物品是否被要求「继续展开」。
     *
     * @param itemId 物品资源 ID
     * @return 命中时返回 true
     */
    public boolean isForceExpanded(String itemId) {
        return itemId != null && forceExpandItems.contains(itemId);
    }

    /**
     * 指定某物品使用的配方。
     *
     * @param itemId   物品资源 ID
     * @param recipeId 配方 ID
     */
    public void overrideRecipe(String itemId, String recipeId) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (recipeId == null || recipeId.isEmpty()) {
            recipeOverrides.remove(itemId);
        } else {
            recipeOverrides.put(itemId, recipeId);
        }
    }

    /**
     * 在候选配方之间轮换（点击预览树里的功能方块节点时的行为）。
     * 首次调用基于默认策略的预选结果前进一格。
     *
     * @param itemId     物品资源 ID
     * @param candidates 该物品的候选配方
     * @return 轮换后的配方 ID；无候选时返回 null
     */
    public String cycleRecipe(String itemId, List<MaterialRecipe> candidates) {
        if (itemId == null || itemId.isEmpty() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<MaterialRecipe> sorted = MaterialRecipeSelector.sort(candidates);
        if (sorted.isEmpty() || sorted.get(0).id() == null) {
            return null;
        }
        String currentId = recipeOverrides.get(itemId);
        if (currentId == null) {
            MaterialRecipe preferred = MaterialRecipeSelector.selectPreferred(sorted);
            currentId = preferred == null ? null : preferred.id();
        }
        int currentIndex = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).id() != null && sorted.get(i).id().equals(currentId)) {
                currentIndex = i;
                break;
            }
        }
        MaterialRecipe next = sorted.get((currentIndex + 1) % sorted.size());
        if (next.id() == null) {
            return null;
        }
        recipeOverrides.put(itemId, next.id());
        return next.id();
    }

    /**
     * 设置或取消「到此为止」（收起该物品的下级配方）。
     *
     * @param itemId 物品资源 ID
     * @param stop   为 true 时收起，为 false 时恢复展开
     */
    public void setStopAt(String itemId, boolean stop) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (stop) {
            stopAtItems.add(itemId);
        } else {
            stopAtItems.remove(itemId);
        }
    }

    /**
     * 判断该节点是否还能展开下级配方（与 {@link #toggleExpand} 的可点击范围一致）。
     *
     * <p>无配方 / 循环依赖 / 深度或节点上限这类天然终止的节点不可展开；
     * 其余节点都可以在「展开」与「收起」之间来回切换。
     *
     * @param node 材料节点
     * @return 可以切换展开状态时返回 true
     */
    public boolean isExpandToggleAvailable(MaterialNode node) {
        if (node == null || node.itemId() == null || node.itemId().isEmpty()) {
            return false;
        }
        if (!node.isLeaf()) {
            return true;
        }
        MaterialStopReason reason = node.stopReason();
        return reason == MaterialStopReason.FOLDED
                || reason == MaterialStopReason.COOKING_INPUT
                || reason == MaterialStopReason.USER_STOPPED;
    }

    /**
     * 切换某个节点的「展开 / 收起下级配方」。
     *
     * <p>语义映射（对应解析器的开关）：
     * <ul>
     *     <li>已展开的节点 → 收起：玩家主动展开过的恢复默认折叠，否则标记「到此为止」；</li>
     *     <li>默认折叠（{@code FOLDED}）→ 继续展开；</li>
     *     <li>熔炼类输入（{@code COOKING_INPUT}）→ 在「继续展开 / 恢复默认」之间切换；</li>
     *     <li>「到此为止」（{@code USER_STOPPED}）→ 恢复展开；</li>
     *     <li>其余自然终止的节点 → 不变。</li>
     * </ul>
     *
     * @param node 材料节点
     * @return 状态发生变化时返回 true（调用方据此重新解析）
     */
    public boolean toggleExpand(MaterialNode node) {
        if (!isExpandToggleAvailable(node)) {
            return false;
        }
        String itemId = node.itemId();
        if (!node.isLeaf()) {
            if (isForceExpanded(itemId)) {
                // 玩家主动展开过的节点：折叠回默认折叠状态
                setForceExpand(itemId, false);
            } else {
                setStopAt(itemId, true);
            }
            return true;
        }
        if (node.stopReason() == MaterialStopReason.COOKING_INPUT) {
            setForceExpand(itemId, !isForceExpanded(itemId));
            return true;
        }
        if (node.stopReason() == MaterialStopReason.USER_STOPPED) {
            setStopAt(itemId, false);
            return true;
        }
        setForceExpand(itemId, true);
        return true;
    }

    /**
     * 设置或清除某个候选组（同一输入可用的多种替代材料）的选定物品。
     *
     * @param candidateKey  候选组键，取该组默认代表物品的资源 ID
     * @param chosenItemId  选定的替代物品资源 ID；为空时清除选择，回到默认候选
     */
    public void chooseCandidate(String candidateKey, String chosenItemId) {
        if (candidateKey == null || candidateKey.isEmpty()) {
            return;
        }
        if (chosenItemId == null || chosenItemId.isEmpty()) {
            candidateOverrides.remove(candidateKey);
        } else {
            candidateOverrides.put(candidateKey, chosenItemId);
        }
    }

    /**
     * 返回只读的候选组选择表。
     *
     * @return 候选组键 → 选定物品资源 ID
     */
    public Map<String, String> getCandidateOverrides() {
        return Map.copyOf(candidateOverrides);
    }

    /**
     * 设置玩家当前已有的物品集合，用于多候选输入默认优先选择已有材料。
     *
     * @param preferredItems 物品资源 ID 集合；为 null 时视为空
     */
    public void setPreferredItems(Set<String> preferredItems) {
        this.preferredItems = preferredItems == null ? Set.of() : Set.copyOf(preferredItems);
    }

    /**
     * 构造解析选项。
     *
     * @return 解析选项
     */
    public MaterialResolveOptions buildOptions() {
        return new MaterialResolveOptions(
                MaterialResolveOptions.DEFAULT_MAX_DEPTH,
                MaterialResolveOptions.DEFAULT_MAX_NODES,
                recipeOverrides,
                stopAtItems,
                forceExpandItems,
                MaterialResolveOptions.DEFAULT_EXPAND_DEPTH,
                candidateOverrides,
                preferredItems
        );
    }

    /**
     * 按当前状态解析材料计划。
     *
     * @param index 配方反查索引
     * @return 材料计划
     */
    public MaterialPlan resolve(MaterialRecipeIndex index) {
        return MaterialResolver.resolve(targetItemId, targetCount, index, buildOptions());
    }

    /**
     * 按当前预览树生成任务列表。
     *
     * <p>树形（哪些节点展开、各自用哪条配方、用哪种替代材料）由 {@link #resolve} 决定，
     * 生成时完全按这棵树走：展开的分支会生成下级材料任务，收起的节点作为最终材料生成自身任务。
     *
     * @param index   配方反查索引
     * @param context 生成上下文
     * @return 任务列表
     */
    public List<Task> generate(MaterialRecipeIndex index, MaterialTaskContext context) {
        return MaterialTaskGenerator.generate(
                resolve(index),
                targetItemId,
                targetCount,
                MaterialTaskMode.TARGET_WITH_MATERIALS,
                context
        );
    }
}
