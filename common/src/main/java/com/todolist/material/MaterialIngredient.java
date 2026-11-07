package com.todolist.material;

import java.util.List;

/**
 * 材料反推的配方输入项（与 MC 运行时无关的中立表示）。
 *
 * <p>一条输入可能对应多个候选物品（配方使用物品标签，或原版 {@code Ingredient} 存在多个可选物品），
 * 此时 {@link #isAlternative()} 为 true，{@link #representative()} 给出用于展示的代表物品。
 *
 * @param candidates 候选物品资源 ID 列表，至少应有一个；空列表表示无法解析该输入
 * @param count      该输入需要的数量，最小为 1
 */
public record MaterialIngredient(List<String> candidates, int count) {

    /**
     * 规范化候选列表与数量，避免 null 与非法数量扩散到反推逻辑。
     */
    public MaterialIngredient {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        count = Math.max(1, count);
    }

    /**
     * 判断该输入是否存在多个可选物品（物品标签或原版 Ingredient 的多候选）。
     *
     * @return 候选数大于 1 时返回 true
     */
    public boolean isAlternative() {
        return candidates.size() > 1;
    }

    /**
     * 返回用于展示/默认展开的代表物品。
     *
     * @return 首个候选资源 ID；无候选时返回空串
     */
    public String representative() {
        return candidates.isEmpty() ? "" : candidates.get(0);
    }
}
