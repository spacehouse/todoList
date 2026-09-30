package com.todolist.material;

import java.util.List;

/**
 * 材料反推的展开树节点。
 *
 * <p>已展开的节点 {@link #stopReason()} 为 null 且 {@link #children()} 非空；
 * 叶子材料节点 {@link #stopReason()} 非空且无子节点。
 *
 * @param itemId        该节点对应的物品资源 ID（多候选取代表）
 * @param requiredCount 该节点需要的数量（已完成向上取整的数量传播）
 * @param candidates    输入多候选（物品标签/多选输入）的全部物品 ID；单候选时只有一个元素
 * @param recipe        该物品的推荐配方；无配方时为 null。因默认展开层级被折叠（{@link MaterialStopReason#FOLDED}）
 *                      的节点也会带上配方，供任务标题提示「用哪个功能方块做」
 * @param stopReason    不再展开的原因；已展开节点为 null
 * @param children      子节点列表；叶子节点为空列表
 */
public record MaterialNode(String itemId,
                           int requiredCount,
                           List<String> candidates,
                           MaterialRecipe recipe,
                           MaterialStopReason stopReason,
                           List<MaterialNode> children) {

    /**
     * 规范化集合字段，避免 null 扩散到界面渲染与任务生成。
     */
    public MaterialNode {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        children = children == null ? List.of() : List.copyOf(children);
    }

    /**
     * 判断该节点是否为最终材料（不再继续展开）。
     *
     * @return 叶子节点返回 true
     */
    public boolean isLeaf() {
        return stopReason != null;
    }
}
