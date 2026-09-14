package com.todolist.material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 材料反推的配方反查索引：物品资源 ID → 能够产出它的配方列表。
 *
 * <p>反推过程会对同一物品反复查询配方，因此构建一次后复用；索引与 MC 运行时解耦，
 * 可离线用构造的 {@link MaterialRecipe} 数据测试。
 */
public final class MaterialRecipeIndex {

    /** 产物物品资源 ID → 配方列表（保持加入顺序）。 */
    private final Map<String, List<MaterialRecipe>> byOutputItem = new HashMap<>();

    /**
     * 加入一条配方映射；产物 ID 为空或配方为 null 时忽略。
     *
     * @param recipe 配方映射
     */
    public void add(MaterialRecipe recipe) {
        if (recipe == null || recipe.outputItemId() == null || recipe.outputItemId().isEmpty()) {
            return;
        }
        byOutputItem.computeIfAbsent(recipe.outputItemId(), ignored -> new ArrayList<>()).add(recipe);
    }

    /**
     * 查询能够产出指定物品的全部配方。
     *
     * @param itemId 物品资源 ID
     * @return 配方列表；无匹配时返回空列表
     */
    public List<MaterialRecipe> findByOutput(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return List.of();
        }
        List<MaterialRecipe> recipes = byOutputItem.get(itemId);
        return recipes == null ? List.of() : List.copyOf(recipes);
    }

    /**
     * 判断指定物品是否存在可解析的配方。
     *
     * @param itemId 物品资源 ID
     * @return 存在配方时返回 true
     */
    public boolean hasRecipeFor(String itemId) {
        return !findByOutput(itemId).isEmpty();
    }

    /**
     * 返回所有存在配方的产物物品资源 ID 集合。
     *
     * @return 产物 ID 集合
     */
    public Set<String> outputItemIds() {
        return Set.copyOf(byOutputItem.keySet());
    }

    /**
     * 返回索引中的配方总条数。
     *
     * @return 配方条数
     */
    public int recipeCount() {
        int total = 0;
        for (List<MaterialRecipe> recipes : byOutputItem.values()) {
            total += recipes.size();
        }
        return total;
    }

    /**
     * 清空索引内容。
     */
    public void clear() {
        byOutputItem.clear();
    }
}
