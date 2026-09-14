package com.todolist.material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 多配方时的默认选择策略（对应设计文档 D3）。
 *
 * <p>排序规则（依次比较）：
 * <ol>
 *     <li><b>产出比降序</b>：单次配方的产出量 / 输入总量，越大越优先。
 *         例如铁锭选「粗铁 ×1 → 铁锭 ×1」（1.0）而不是「铁粒 ×9 → 铁锭 ×1」（约 0.11）；</li>
 *     <li><b>配方类型优先级升序</b>：产出比相同时优先不耗燃料的类型（crafting 在前）；</li>
 *     <li><b>配方 ID 字典序升序</b>：保证结果稳定可复现。</li>
 * </ol>
 *
 * <p>该默认值只用于「预选」，最终由玩家在预览界面覆盖（见 D3 结论）。
 */
public final class MaterialRecipeSelector {

    /**
     * 工具类不需要实例化。
     */
    private MaterialRecipeSelector() {
    }

    /**
     * 按默认策略排序配方列表，不修改入参。
     *
     * @param recipes 候选配方
     * @return 排序后的新列表
     */
    public static List<MaterialRecipe> sort(List<MaterialRecipe> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return List.of();
        }
        List<MaterialRecipe> sorted = new ArrayList<>(recipes);
        sorted.sort(defaultComparator());
        return sorted;
    }

    /**
     * 按默认策略选出推荐配方。
     *
     * @param recipes 候选配方
     * @return 推荐配方；候选为空时返回 null
     */
    public static MaterialRecipe selectPreferred(List<MaterialRecipe> recipes) {
        List<MaterialRecipe> sorted = sort(recipes);
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    /**
     * 构造默认排序比较器。
     *
     * @return 比较器
     */
    static Comparator<MaterialRecipe> defaultComparator() {
        return (left, right) -> {
            if (left == null || right == null) {
                return left == right ? 0 : (left == null ? 1 : -1);
            }
            int byYield = compareYieldDescending(left, right);
            if (byYield != 0) {
                return byYield;
            }
            int byKind = Integer.compare(kindPriority(left), kindPriority(right));
            if (byKind != 0) {
                return byKind;
            }
            return safeId(left).compareTo(safeId(right));
        };
    }

    /**
     * 按产出比（单次产出量 / 输入总量）降序比较。
     *
     * <p>使用交叉相乘以避免浮点误差：{@code left.out / left.in > right.out / right.in}
     * 等价于 {@code left.out * right.in > right.out * left.in}。
     * 输入总量为 0 的配方视为产出比最差，避免「无输入」被误判为最优。
     *
     * @param left  左侧配方
     * @param right 右侧配方
     * @return 比较结果，负数表示左侧应排在前面
     */
    private static int compareYieldDescending(MaterialRecipe left, MaterialRecipe right) {
        long leftInput = left.totalInputCount();
        long rightInput = right.totalInputCount();
        if (leftInput <= 0 || rightInput <= 0) {
            if (leftInput <= 0 && rightInput <= 0) {
                return 0;
            }
            return leftInput <= 0 ? 1 : -1;
        }
        long leftCross = (long) left.outputCount() * rightInput;
        long rightCross = (long) right.outputCount() * leftInput;
        return Long.compare(rightCross, leftCross);
    }

    /**
     * 返回配方类型的排序优先级。
     *
     * @param recipe 配方
     * @return 优先级；类型缺失时按 OTHER 处理
     */
    private static int kindPriority(MaterialRecipe recipe) {
        MaterialRecipeKind kind = recipe.kind();
        return kind == null ? MaterialRecipeKind.OTHER.priority() : kind.priority();
    }

    /**
     * 返回用于稳定排序的配方 ID。
     *
     * @param recipe 配方
     * @return 配方 ID；为空时返回空串
     */
    private static String safeId(MaterialRecipe recipe) {
        return recipe.id() == null ? "" : recipe.id();
    }
}
