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
     * @param itemId  物品资源 ID
     * @param count   数量
     * @param collect true 表示最终材料的「收集」任务；false 表示需要依赖子任务完成的「合成」任务
     * @return 任务标题文本
     */
    String titleFor(String itemId, int count, boolean collect);
}
