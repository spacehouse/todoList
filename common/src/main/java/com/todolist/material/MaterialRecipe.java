package com.todolist.material;

import java.util.List;

/**
 * 材料反推的配方映射（与 MC 运行时无关的中立表示）。
 *
 * <p>由平台侧 resolver 从 MC 配方转换而来，反推算法只依赖本模型，
 * 因此索引、选择策略与递归展开都能离线构造数据并测试。
 *
 * @param id           配方资源 ID，用于结果复现与去重
 * @param outputItemId 产物物品资源 ID
 * @param outputCount  单次产出的数量，最小为 1
 * @param inputs       输入项列表
 * @param kind         配方类型分类
 */
public record MaterialRecipe(String id,
                             String outputItemId,
                             int outputCount,
                             List<MaterialIngredient> inputs,
                             MaterialRecipeKind kind) {

    /**
     * 规范化输入列表与产出数量。
     */
    public MaterialRecipe {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputCount = Math.max(1, outputCount);
    }

    /**
     * 汇总本次配方消耗的输入物品总数量。
     *
     * @return 输入总数量
     */
    public int totalInputCount() {
        int total = 0;
        for (MaterialIngredient input : inputs) {
            total += input.count();
        }
        return total;
    }

    /**
     * 判断是否为熔炼/烧制类配方（终止条件：其输入被视为基础材料，不再继续反推）。
     *
     * @return 熔炼类返回 true
     */
    public boolean isCooking() {
        return kind != null && kind.isCooking();
    }
}
