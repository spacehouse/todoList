package com.todolist.task;

import java.util.ArrayList;
import java.util.List;

/**
 * TaskCompatibilityAdapter 负责在 Phase 1 兼容旧 subtasks 嵌套结构。
 * 读取时将旧结构展平为新的一层父子任务模型，写入时统一交给 Task 的新 NBT 格式。
 */
public final class TaskCompatibilityAdapter {
    /**
     * 工具类不需要实例化。
     */
    private TaskCompatibilityAdapter() {
    }

    /**
     * 将读取到的任务列表标准化为平铺结构。
     * 对旧的一层 subtasks 做展平；两层及以上的历史结构会明确阻断，避免迁移语义不清。
     *
     * @param loadedTasks 原始读取结果
     * @return 标准化后的平铺任务列表
     */
    public static List<Task> normalizeLoadedTasks(List<Task> loadedTasks) {
        List<Task> normalizedTasks = new ArrayList<>();
        if (loadedTasks == null || loadedTasks.isEmpty()) {
            return normalizedTasks;
        }

        for (Task loadedTask : loadedTasks) {
            Task normalizedParent = copyWithoutLegacySubtasks(loadedTask);
            normalizedTasks.add(normalizedParent);

            List<Task> legacySubtasks = loadedTask.getSubtasks();
            for (int index = 0; index < legacySubtasks.size(); index++) {
                Task legacyChild = legacySubtasks.get(index);
                if (!legacyChild.getSubtasks().isEmpty()) {
                    throw new IllegalStateException("检测到两层及以上旧 subtasks 结构，当前阶段暂不支持自动展平");
                }
                Task normalizedChild = copyWithoutLegacySubtasks(legacyChild);
                normalizedChild.setParentTaskId(normalizedParent.getId());
                normalizedChild.setSubtaskSortOrder(index);
                inheritMissingParentContext(normalizedParent, normalizedChild);
                normalizedTasks.add(normalizedChild);
            }
        }
        return normalizedTasks;
    }

    /**
     * 为旧子任务补齐父任务上下文字段，避免迁移后丢失项目、作用域和人员信息。
     *
     * @param parent 归一化后的父任务
     * @param child 归一化后的子任务
     */
    private static void inheritMissingParentContext(Task parent, Task child) {
        if (child == null || parent == null) {
            return;
        }
        if (child.isProjectUnassigned() && !parent.isProjectUnassigned()) {
            child.setProjectId(parent.getProjectId());
        }
        if (parent.getScope() == Task.Scope.TEAM && child.getScope() != Task.Scope.TEAM) {
            child.setScope(Task.Scope.TEAM);
        }
        if (isBlank(child.getCreatorUuid()) && !isBlank(parent.getCreatorUuid())) {
            child.setCreatorUuid(parent.getCreatorUuid());
        }
        if (isBlank(child.getAssigneeUuid()) && !isBlank(parent.getAssigneeUuid())) {
            child.setAssigneeUuid(parent.getAssigneeUuid());
        }
        if (isBlank(child.getAssigneeName()) && !isBlank(parent.getAssigneeName())) {
            child.setAssigneeName(parent.getAssigneeName());
        }
    }

    /**
     * 复制任务并移除旧的递归 subtasks 结构，只保留 Phase 1 需要的平铺字段。
     *
     * @param source 原始任务
     * @return 不再包含旧 subtasks 的任务副本
     */
    private static Task copyWithoutLegacySubtasks(Task source) {
        Task copy = Task.fromNbt(source.toNbt());
        copy.setParentTaskId(source.getParentTaskId());
        copy.setSubtaskSortOrder(source.getSubtaskSortOrder());
        return copy;
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断字符串
     * @return 为空白时返回 true
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
