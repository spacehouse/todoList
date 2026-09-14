package com.todolist.material;

import com.todolist.task.Task;

/**
 * 材料任务生成的上下文：挂载项目、作用域、归属信息与标题渲染回调。
 *
 * <p>生成的任务全部落到 {@link #projectId()} 指定的当前项目下（已定：在当前项目里新建独立任务）。
 * 团队作用域下若 {@link #assigneeUuid()} 为空，任务属于「待领取」状态，
 * 触发器不会自动推进（见触发器特性 Phase U），需由界面在生成时或生成后完成领取/指派。
 *
 * @param projectId     目标项目 ID
 * @param scope         任务作用域（个人 / 团队）
 * @param creatorUuid   创建者 UUID
 * @param assigneeUuid  负责人 UUID，可为空
 * @param assigneeName  负责人显示名，可为空
 * @param titleProvider 标题渲染回调，可为 null（回退为物品资源 ID）
 */
public record MaterialTaskContext(String projectId,
                                  Task.Scope scope,
                                  String creatorUuid,
                                  String assigneeUuid,
                                  String assigneeName,
                                  MaterialTaskTitleProvider titleProvider) {

    /**
     * 规范作用域默认值，避免 null 扩散到任务模型。
     */
    public MaterialTaskContext {
        scope = scope == null ? Task.Scope.PERSONAL : scope;
    }
}
