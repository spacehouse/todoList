package com.todolist.material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 材料反推结果：展开树 + 合并去重后的最终材料清单。
 *
 * <p>任务生成只消费 {@link #leafTotals()}（每个最终材料的汇总数量）；
 * {@link #root()} 供预览界面展示展开过程与切换配方。
 *
 * @param root       展开树根节点（即目标物品）
 * @param leafTotals 最终材料清单：物品资源 ID → 汇总数量（保持首次出现顺序）
 * @param nodeCount  展开产生的节点总数
 * @param truncated  是否因节点数上限被提前截断
 */
public record MaterialPlan(MaterialNode root,
                           Map<String, Integer> leafTotals,
                           int nodeCount,
                           boolean truncated) {

    /**
     * 复制为不可变的有序映射，避免调用方修改内部状态。
     */
    public MaterialPlan {
        leafTotals = leafTotals == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(leafTotals));
    }

    /**
     * 读取指定最终材料的汇总数量。
     *
     * @param itemId 物品资源 ID
     * @return 汇总数量；不在清单中时返回 0
     */
    public int totalFor(String itemId) {
        Integer total = itemId == null ? null : leafTotals.get(itemId);
        return total == null ? 0 : total;
    }

    /**
     * 判断最终材料清单是否为空。
     *
     * @return 无最终材料时返回 true
     */
    public boolean isEmpty() {
        return leafTotals.isEmpty();
    }

    /**
     * 按允许集合过滤最终材料清单（供预览界面的勾选使用）。
     *
     * @param allowedItemIds 允许保留的物品资源 ID 集合；为 null 时返回原计划（表示全选）
     * @return 过滤后的新计划；allowedItemIds 为 null 时返回 this
     */
    public MaterialPlan retainLeaves(Set<String> allowedItemIds) {
        if (allowedItemIds == null) {
            return this;
        }
        Map<String, Integer> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : leafTotals.entrySet()) {
            if (allowedItemIds.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return new MaterialPlan(root, filtered, nodeCount, truncated);
    }
}
