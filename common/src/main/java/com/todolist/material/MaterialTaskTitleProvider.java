package com.todolist.material;

/**
 * 任务标题生成回调。
 *
 * <p>标题渲染依赖游戏内物品名称与图标标记（见 {@code TriggerTargetSupport#buildDefaultTaskTitle}），
 * 属于展示层职责；这里以回调注入，使材料反推与任务生成保持与 MC 运行时的解耦，便于离线测试。
 */
@FunctionalInterface
public interface MaterialTaskTitleProvider {

    /**
     * 生成任务标题。
     *
     * @param itemId 产物物品资源 ID
     * @param count  需求数量
     * @param kind   该物品配方的类型，用于渲染「功能方块 + 动作词」提示（如「[熔炉] 熔炼 [铁锭] ×27」）；
     *               无配方（最终材料）或类型未知时为 null，此时应回退为「收集」措辞
     * @return 任务标题文本
     */
    String titleFor(String itemId, int count, MaterialRecipeKind kind);
}
