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
 * <p>持有界面上的全部可变状态：目标物品与数量、生成模式、逐节点配方覆盖、
 * 玩家手动终止项、取消勾选的材料；并负责把这些状态转成解析选项、解析计划、
 * 按勾选过滤计划，以及最终生成任务。
 *
 * <p>界面只负责渲染与事件分发，不重复实现这些语义，因此这部分可脱离 MC 运行时测试。
 */
public final class MaterialPreviewState {

    /** 目标物品资源 ID（不可变）。 */
    private final String targetItemId;
    /** 目标数量。 */
    private int targetCount;
    /** 生成模式。 */
    private MaterialTaskMode mode;
    /** 物品资源 ID → 指定配方 ID（逐节点切换配方的结果）。 */
    private final Map<String, String> recipeOverrides = new LinkedHashMap<>();
    /** 被玩家标记为「到此为止」的物品资源 ID。 */
    private final Set<String> stopAtItems = new LinkedHashSet<>();
    /** 被玩家取消勾选的最终材料。 */
    private final Set<String> unselectedLeafItems = new LinkedHashSet<>();
    /** 被玩家要求「继续展开」的物品（覆盖熔炼类终止条件）。 */
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
     * @param mode         生成模式，为 null 时按仅目标任务处理
     */
    public MaterialPreviewState(String targetItemId, int targetCount, MaterialTaskMode mode) {
        this.targetItemId = targetItemId == null ? "" : targetItemId;
        this.targetCount = Math.max(1, targetCount);
        this.mode = mode == null ? MaterialTaskMode.TARGET_ONLY : mode;
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
     * 返回当前生成模式。
     *
     * @return 生成模式
     */
    public MaterialTaskMode getMode() {
        return mode;
    }

    /**
     * 设置生成模式。
     *
     * @param mode 生成模式，为 null 时不改变
     */
    public void setMode(MaterialTaskMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    /**
     * 在两种生成模式之间切换。
     */
    public void toggleMode() {
        this.mode = mode == MaterialTaskMode.TARGET_ONLY
                ? MaterialTaskMode.TARGET_WITH_MATERIALS
                : MaterialTaskMode.TARGET_ONLY;
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
     * 返回只读的手动终止项集合。
     *
     * @return 物品资源 ID 集合
     */
    public Set<String> getStopAtItems() {
        return Set.copyOf(stopAtItems);
    }

    /**
     * 返回只读的取消勾选材料集合。
     *
     * @return 物品资源 ID 集合
     */
    public Set<String> getUnselectedLeafItems() {
        return Set.copyOf(unselectedLeafItems);
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
     * 设置或取消「继续展开」：既用于对熔炼类配方的输入强制继续反推，
     * 也用于展开默认折叠（{@code MaterialStopReason.FOLDED}）的层级。
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
     * 清空全部配方覆盖，回到默认选择策略。
     */
    public void clearRecipeOverrides() {
        recipeOverrides.clear();
    }

    /**
     * 在候选配方之间轮换（预览界面「切换配方」按钮的行为）。
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
     * 设置或取消「到此为止」（不再展开该物品）。
     *
     * @param itemId 物品资源 ID
     * @param stop   为 true 时停止展开，为 false 时恢复展开
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
     * 判断某个最终材料是否被勾选（默认全部勾选）。
     *
     * @param itemId 物品资源 ID
     * @return 未取消勾选时返回 true
     */
    public boolean isLeafSelected(String itemId) {
        return itemId != null && !unselectedLeafItems.contains(itemId);
    }

    /**
     * 设置某个最终材料的勾选状态。
     *
     * @param itemId   物品资源 ID
     * @param selected 是否勾选
     */
    public void setLeafSelected(String itemId, boolean selected) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (selected) {
            unselectedLeafItems.remove(itemId);
        } else {
            unselectedLeafItems.add(itemId);
        }
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
     * 计算允许保留的最终材料集合（供勾选过滤使用）。
     *
     * @param plan 完整计划
     * @return 允许集合；全部勾选时返回 null（表示不过滤）
     */
    public Set<String> allowedLeafItems(MaterialPlan plan) {
        if (plan == null || unselectedLeafItems.isEmpty()) {
            return null;
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (String itemId : plan.leafTotals().keySet()) {
            if (!unselectedLeafItems.contains(itemId)) {
                allowed.add(itemId);
            }
        }
        return allowed;
    }

    /**
     * 按当前状态生成任务列表（已应用勾选过滤）。
     *
     * @param index   配方反查索引
     * @param context 生成上下文
     * @return 任务列表
     */
    public List<Task> generate(MaterialRecipeIndex index, MaterialTaskContext context) {
        MaterialPlan plan = resolve(index);
        MaterialPlan selected = plan.retainLeaves(allowedLeafItems(plan));
        return MaterialTaskGenerator.generate(selected, targetItemId, targetCount, mode, context);
    }
}
