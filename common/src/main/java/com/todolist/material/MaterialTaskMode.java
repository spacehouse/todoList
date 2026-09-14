package com.todolist.material;

/**
 * 材料任务生成模式（见设计文档 §3.4）。
 */
public enum MaterialTaskMode {
    /** 模式 A：只生成目标物品任务（挂 ITEM_COLLECT），不建材料子任务。 */
    TARGET_ONLY,
    /** 模式 B：生成父任务（目标物）+ 材料子任务（最终材料各一条）。 */
    TARGET_WITH_MATERIALS
}
