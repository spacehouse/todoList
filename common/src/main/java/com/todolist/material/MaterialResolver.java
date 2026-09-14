package com.todolist.material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 材料反推核心：从「目标物品 + 数量」递归展开出最终材料清单。
 *
 * <p>已定语义（见设计文档 §3.2）：
 * <ul>
 *     <li><b>数量传播</b>：{@code craftCount = ceil(需求 / 单次产出)}，
 *         每个输入需要 {@code craftCount × 输入数量}；</li>
 *     <li><b>终止条件（D7）</b>：熔炼/烧制类配方的输入视为最终材料（世界可获取），不再继续展开；</li>
 *     <li><b>中间产物不建任务（D1）</b>：只有最终材料会累加进清单；</li>
 *     <li><b>同材料合并</b>：同一配方内重复出现的输入先按物品合并再展开，
 *         最终材料清单跨分支汇总求和（结果等价于 DAG）；</li>
 *     <li><b>循环与深度保护</b>：递归路径去重（如 铁块 ↔ 铁锭）+ 深度上限 + 节点数上限；</li>
 *     <li><b>多候选输入</b>：配方使用物品标签/多选输入时，默认优先选择玩家当前已拥有的候选，
 *         其次是列表首项；玩家可在预览界面显式指定用哪一种替代材料；</li>
 *     <li><b>多配方（D3）</b>：默认用 {@link MaterialRecipeSelector} 预选，可被选项覆盖。</li>
 * </ul>
 *
 * <p>只依赖中立模型与索引，不依赖 MC 运行时，可完全离线测试。
 */
public final class MaterialResolver {

    /**
     * 工具类不需要实例化。
     */
    private MaterialResolver() {
    }

    /**
     * 使用默认选项展开材料。
     *
     * @param itemId 目标物品资源 ID
     * @param count  目标数量，最小为 1
     * @param index  配方反查索引
     * @return 材料计划；入参非法时返回空计划
     */
    public static MaterialPlan resolve(String itemId, int count, MaterialRecipeIndex index) {
        return resolve(itemId, count, index, MaterialResolveOptions.defaults());
    }

    /**
     * 按指定选项展开材料。
     *
     * @param itemId  目标物品资源 ID
     * @param count   目标数量，最小为 1
     * @param index   配方反查索引
     * @param options 解析选项，为 null 时使用默认值
     * @return 材料计划；入参非法时返回空计划
     */
    public static MaterialPlan resolve(String itemId,
                                      int count,
                                      MaterialRecipeIndex index,
                                      MaterialResolveOptions options) {
        MaterialResolveOptions safeOptions = options == null ? MaterialResolveOptions.defaults() : options;
        if (itemId == null || itemId.isEmpty() || index == null) {
            return new MaterialPlan(null, Map.of(), 0, false);
        }
        Context context = new Context(index, safeOptions);
        MaterialNode root = buildNode(itemId, Math.max(1, count), List.of(itemId), context, new LinkedHashSet<>(), 0);
        return new MaterialPlan(root, context.leafTotals, context.nodeCount, context.truncated);
    }

    /**
     * 递归展开一个物品节点，返回该节点的子树。
     *
     * @param itemId     物品资源 ID
     * @param need       需求数量
     * @param candidates 多候选物品列表
     * @param context     解析上下文
     * @param path       当前递归路径上的物品集合（用于循环检测）
     * @param depth      当前深度
     * @return 节点
     */
    private static MaterialNode buildNode(String itemId,
                                         int need,
                                         List<String> candidates,
                                         Context context,
                                         Set<String> path,
                                         int depth) {
        if (context.nodeCount >= context.options.maxNodes()) {
            context.truncated = true;
            return leaf(itemId, need, candidates, MaterialStopReason.NODE_LIMIT, context);
        }
        if (context.options.isStoppedByUser(itemId)) {
            return leaf(itemId, need, candidates, MaterialStopReason.USER_STOPPED, context);
        }
        if (path.contains(itemId)) {
            return leaf(itemId, need, candidates, MaterialStopReason.CYCLE, context);
        }
        List<MaterialRecipe> recipes = context.index.findByOutput(itemId);
        if (recipes.isEmpty()) {
            return leaf(itemId, need, candidates, MaterialStopReason.NO_RECIPE, context);
        }
        if (depth >= context.options.maxDepth()) {
            return leaf(itemId, need, candidates, MaterialStopReason.MAX_DEPTH, context);
        }
        // 默认只展开根节点的直接材料：更深层需要玩家在预览里逐节点「继续展开」
        if (depth >= context.options.defaultExpandDepth() && !context.options.isForceExpanded(itemId)) {
            return leaf(itemId, need, candidates, MaterialStopReason.FOLDED, context);
        }
        MaterialRecipe recipe = chooseRecipe(itemId, recipes, context.options);
        if (recipe == null) {
            return leaf(itemId, need, candidates, MaterialStopReason.NO_RECIPE, context);
        }
        int craftCount = ceilDiv(need, recipe.outputCount());
        // D7：熔炼/烧制类配方的输入视为最终材料；玩家可要求「继续展开」以覆盖该终止条件
        boolean cooking = recipe.isCooking() && !context.options.isForceExpanded(itemId);
        if (!cooking) {
            path.add(itemId);
        }
        List<MaterialNode> children = new ArrayList<>();
        for (MaterialIngredient input : mergeInputs(recipe)) {
            String childId = resolveCandidateId(input, context.options);
            if (childId.isEmpty()) {
                continue;
            }
            // 展开结果回到当前依赖链上的同一材料（自反配方或环）时不展示，避免重复与死循环
            if (path.contains(childId)) {
                continue;
            }
            int childNeed = craftCount * input.count();
            if (cooking) {
                // D7：熔炼/烧制类配方的输入视为最终材料，不再继续反推
                children.add(leaf(childId, childNeed, input.candidates(), MaterialStopReason.COOKING_INPUT, context));
            } else {
                children.add(buildNode(childId, childNeed, input.candidates(), context, path, depth + 1));
            }
        }
        if (!cooking) {
            path.remove(itemId);
        }
        if (children.isEmpty()) {
            // 全部输入都指回依赖链上的材料：该物品只能作为最终材料终止
            return leaf(itemId, need, candidates, MaterialStopReason.CYCLE, context);
        }
        context.nodeCount++;
        return new MaterialNode(itemId, need, candidates, recipe, null, children);
    }

    /**
     * 创建叶子材料节点并累加到最终材料清单。
     *
     * @param itemId     物品资源 ID
     * @param need       数量
     * @param candidates 多候选物品列表
     * @param reason     停止展开的原因
     * @param context    解析上下文
     * @return 叶子节点
     */
    private static MaterialNode leaf(String itemId,
                                     int need,
                                     List<String> candidates,
                                     MaterialStopReason reason,
                                     Context context) {
        context.nodeCount++;
        context.leafTotals.merge(itemId, need, Integer::sum);
        return new MaterialNode(itemId, need, candidates, null, reason, List.of());
    }

    /**
     * 选择该物品要使用的配方：优先玩家覆盖，否则用默认策略预选。
     *
     * @param itemId  物品资源 ID
     * @param recipes 候选配方
     * @param options 解析选项
     * @return 选中的配方；候选为空或覆盖值无效时回退默认策略
     */
    private static MaterialRecipe chooseRecipe(String itemId,
                                              List<MaterialRecipe> recipes,
                                              MaterialResolveOptions options) {
        String overrideId = options.recipeOverride(itemId);
        if (overrideId != null && !overrideId.isEmpty()) {
            for (MaterialRecipe recipe : recipes) {
                if (overrideId.equals(recipe.id())) {
                    return recipe;
                }
            }
        }
        return MaterialRecipeSelector.selectPreferred(recipes);
    }

    /**
     * 选择一个多候选输入实际使用的物品。
     *
     * <p>优先级：玩家显式指定 &gt; 玩家当前已拥有的候选 &gt; 默认代表物品（首个候选）。
     * 例如配方使用物品标签（木板、锭类标签）时，默认会挑玩家背包里已有的那种，
     * 避免选中手上没有的材料；玩家也可在预览界面里手动指定。
     *
     * @param input   配方输入项
     * @param options 解析选项
     * @return 选定物品的资源 ID；无候选时返回空串
     */
    private static String resolveCandidateId(MaterialIngredient input, MaterialResolveOptions options) {
        if (input.candidates().isEmpty()) {
            return "";
        }
        String representative = input.representative();
        String override = options.candidateOverride(representative);
        if (override != null && !override.isEmpty() && input.candidates().contains(override)) {
            return override;
        }
        for (String candidate : input.candidates()) {
            if (options.isPreferredItem(candidate)) {
                return candidate;
            }
        }
        return representative;
    }

    /**
     * 合并同一配方内重复出现的输入（原版合成配方按格子给出，重复材料会出现多次），
     * 避免同一物品被重复展开、放大向上取整误差。
     *
     * @param recipe 配方
     * @return 合并后的输入列表
     */
    private static List<MaterialIngredient> mergeInputs(MaterialRecipe recipe) {
        Map<String, MaterialIngredient> merged = new LinkedHashMap<>();
        for (MaterialIngredient input : recipe.inputs()) {
            String key = input.representative();
            if (key.isEmpty()) {
                continue;
            }
            MaterialIngredient existing = merged.get(key);
            if (existing == null) {
                merged.put(key, input);
            } else {
                merged.put(key, new MaterialIngredient(existing.candidates(), existing.count() + input.count()));
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 向上取整的除法，得到需要执行配方的次数。
     *
     * @param need 需求数量
     * @param unit 单次产出数量
     * @return 执行次数，最小为 1
     */
    private static int ceilDiv(int need, int unit) {
        if (need <= 0) {
            return 1;
        }
        if (unit <= 0) {
            return need;
        }
        return (need + unit - 1) / unit;
    }

    /**
     * 单次解析的上下文：索引、选项与累加状态。
     */
    private static final class Context {
        private final MaterialRecipeIndex index;
        private final MaterialResolveOptions options;
        private final Map<String, Integer> leafTotals = new LinkedHashMap<>();
        private int nodeCount;
        private boolean truncated;

        /**
         * 创建解析上下文。
         *
         * @param index   配方反查索引
         * @param options 解析选项
         */
        private Context(MaterialRecipeIndex index, MaterialResolveOptions options) {
            this.index = index;
            this.options = options;
        }
    }
}
