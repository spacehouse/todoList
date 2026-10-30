package com.todolist.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * 旧任务数据兼容适配器。
 *
 * <p>项目历史版本曾把子任务直接嵌入父任务的 {@code subtasks} NBT 列表。
 * 当前格式改为“平铺列表 + parentTaskId”，两者必须能够无损互相迁移。
 * 本类使用显式栈遍历旧嵌套结构，因此迁移任意深度的任务树都不会触发递归栈溢出。</p>
 *
 * <p>迁移同时负责修复损坏数据：重复/空任务 ID、无效父任务 ID、自引用、循环父子关系、
 * 跨项目父子关系、缺失的项目/作用域继承，以及重复的父内排序号。修复只调整非法关系，
 * 不会删除用户任务。</p>
 */
public final class TaskCompatibilityAdapter {
    private static final int NBT_COMPOUND_TYPE = 10;
    private static final int MAX_TASKS_PER_LOAD = 200_000;
    private static final int TASK_ID_MAX_LENGTH = 64;
    private static final String LEGACY_SUBTASKS_KEY = "subtasks";

    /**
     * 工具类不需要实例化。
     */
    private TaskCompatibilityAdapter() {
    }

    /**
     * 读取旧 NBT 任务列表并按当前平铺模型标准化。
     *
     * @param taskList 旧格式任务列表
     * @return 标准化后的平铺任务列表
     */
    public static List<Task> readTaskNbtList(ListTag taskList) {
        if (taskList == null || taskList.isEmpty()) {
            return new ArrayList<>();
        }
        if (taskList.size() > MAX_TASKS_PER_LOAD) {
            throw new IllegalStateException("任务数量超过安全上限: " + taskList.size());
        }

        List<Task> flattened = new ArrayList<>(taskList.size());
        Deque<NbtTaskFrame> stack = new ArrayDeque<>();
        for (int index = taskList.size() - 1; index >= 0; index--) {
            stack.push(new NbtTaskFrame(taskList.getCompound(index), null, 0L));
        }
        int emittedCount = 0;
        while (!stack.isEmpty()) {
            NbtTaskFrame frame = stack.pop();
            if (frame == null || frame.nbt == null) {
                continue;
            }
            validateCriticalNbtFields(frame.nbt);
            Task task = Task.fromNbt(frame.nbt);
            if (frame.parentTaskId != null) {
                task.setParentTaskId(frame.parentTaskId);
                task.setSubtaskSortOrder(frame.sortOrder);
            }
            flattened.add(task);
            emittedCount++;
            if (emittedCount > MAX_TASKS_PER_LOAD) {
                throw new IllegalStateException("展开旧 subtasks 后任务数量超过安全上限: " + emittedCount);
            }

            ListTag children = frame.nbt.getList(LEGACY_SUBTASKS_KEY, NBT_COMPOUND_TYPE);
            for (int index = children.size() - 1; index >= 0; index--) {
                CompoundTag childNbt = children.getCompound(index);
                if (childNbt != null) {
                    stack.push(new NbtTaskFrame(childNbt, task.getId(), index));
                }
            }
        }
        return normalizeLoadedTasks(flattened);
    }

    /**
     * 将读取到的任务列表标准化为无限层级的平铺结构。
     *
     * <p>内存中旧格式任务可能通过 {@link Task#getSubtasks()} 携带嵌套结构；
     * 平铺格式任务则只带 {@code parentTaskId}。两种输入可以混用。</p>
     *
     * @param loadedTasks 原始读取结果
     * @return 标准化后的平铺任务列表
     */
    public static List<Task> normalizeLoadedTasks(List<Task> loadedTasks) {
        if (loadedTasks == null || loadedTasks.isEmpty()) {
            return new ArrayList<>();
        }

        List<Task> flattened = flattenLegacyInput(loadedTasks);
        if (flattened.isEmpty()) {
            return flattened;
        }

        Map<String, Task> byId = new LinkedHashMap<>();
        for (Task task : flattened) {
            if (task != null && task.getId() != null) {
                byId.putIfAbsent(task.getId(), task);
            }
        }

        // 第一遍：修复无效父 ID、自身引用、项目/作用域继承与缺失上下文。
        for (Task task : flattened) {
            if (task == null) {
                continue;
            }
            String parentId = normalizeOptionalText(task.getParentTaskId());
            if (parentId == null || parentId.equals(task.getId()) || !byId.containsKey(parentId)) {
                task.setParentTaskId(null);
                continue;
            }
            Task parent = byId.get(parentId);
            if (parent == null) {
                task.setParentTaskId(null);
                continue;
            }
            if (!compatibleProject(parent, task)) {
                // 跨项目父子关系会破坏项目隔离；保留任务本身并提升为根任务。
                task.setParentTaskId(null);
                continue;
            }
            inheritMissingParentContext(parent, task);
        }

        // 第二遍：断开循环引用。逐节点沿父链上溯，发现环时把当前节点提升为根。
        for (Task task : flattened) {
            Set<String> path = new HashSet<>();
            String currentId = task == null ? null : task.getId();
            while (currentId != null) {
                if (!path.add(currentId)) {
                    task.setParentTaskId(null);
                    break;
                }
                Task current = byId.get(currentId);
                currentId = current == null ? null : normalizeOptionalText(current.getParentTaskId());
            }
        }

        normalizeSiblingSortOrders(flattened, byId);
        return flattened;
    }

    /**
     * 校验会影响整份存档可信度的关键 NBT 字段。
     *
     * <p>单条任务的未知优先级属于结构性损坏，必须让上层备份恢复机制感知，
     * 不能静默降级为 MEDIUM，否则会覆盖用户上一份有效备份。</p>
     *
     * @param nbt 任务 NBT
     */
    private static void validateCriticalNbtFields(CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        String priority = nbt.getString("priority");
        if (priority != null && !priority.isBlank()) {
            try {
                Task.Priority.valueOf(priority);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid task priority: " + priority, exception);
            }
        }
        String scope = nbt.getString("scope");
        if (scope != null && !scope.isBlank()) {
            try {
                Task.Scope.valueOf(scope);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid task scope: " + scope, exception);
            }
        }
    }

    /**
     * 将输入中的旧嵌套结构展开为平铺任务，并为重复/缺失 ID 生成唯一 ID。
     *
     * @param loadedTasks 原始任务列表
     * @return 平铺任务副本列表
     */
    private static List<Task> flattenLegacyInput(List<Task> loadedTasks) {
        List<Task> flattened = new ArrayList<>(loadedTasks.size());
        Set<String> usedIds = new HashSet<>();
        Set<Task> visitedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<SourceTaskFrame> stack = new ArrayDeque<>();

        for (int index = loadedTasks.size() - 1; index >= 0; index--) {
            stack.push(new SourceTaskFrame(loadedTasks.get(index), null, 0L));
        }

        while (!stack.isEmpty()) {
            SourceTaskFrame frame = stack.pop();
            Task source = frame.task;
            if (source == null || !visitedObjects.add(source)) {
                continue;
            }

            Task copy = copyWithoutLegacySubtasks(source);
            String candidateId = normalizeOptionalText(copy.getId());
            if (candidateId == null || candidateId.length() > TASK_ID_MAX_LENGTH || usedIds.contains(candidateId)) {
                candidateId = UUID.randomUUID().toString();
                while (usedIds.contains(candidateId)) {
                    candidateId = UUID.randomUUID().toString();
                }
            }
            usedIds.add(candidateId);
            copy.setId(candidateId);

            if (frame.parentTaskId != null) {
                copy.setParentTaskId(frame.parentTaskId);
                copy.setSubtaskSortOrder(frame.sortOrder);
            }
            flattened.add(copy);
            if (flattened.size() > MAX_TASKS_PER_LOAD) {
                throw new IllegalStateException("任务数量超过安全上限: " + flattened.size());
            }

            List<Task> legacyChildren = source.getSubtasks();
            for (int index = legacyChildren.size() - 1; index >= 0; index--) {
                Task child = legacyChildren.get(index);
                if (child != null) {
                    stack.push(new SourceTaskFrame(child, candidateId, index));
                }
            }
        }
        return flattened;
    }

    /**
     * 按输入顺序重建存在的父链，并重新编号父内排序号，避免重复排序导致显示不稳定。
     *
     * @param flattened 平铺任务列表
     * @param byId 任务 ID 索引
     */
    private static void normalizeSiblingSortOrders(List<Task> flattened, Map<String, Task> byId) {
        Map<String, List<Task>> childrenByParentId = new HashMap<>();
        for (Task task : flattened) {
            if (task == null) {
                continue;
            }
            String parentId = normalizeOptionalText(task.getParentTaskId());
            if (parentId == null || !byId.containsKey(parentId) || parentId.equals(task.getId())) {
                task.setParentTaskId(null);
                continue;
            }
            childrenByParentId.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(task);
        }

        Map<String, Integer> inputOrder = new HashMap<>();
        for (int index = 0; index < flattened.size(); index++) {
            Task task = flattened.get(index);
            if (task != null && task.getId() != null) {
                inputOrder.put(task.getId(), index);
            }
        }
        for (List<Task> siblings : childrenByParentId.values()) {
            Set<Long> seenOrders = new HashSet<>();
            boolean needsRenumber = false;
            for (Task sibling : siblings) {
                long order = sibling.getSubtaskSortOrder();
                if (order < 0L || !seenOrders.add(order)) {
                    needsRenumber = true;
                    break;
                }
            }
            if (!needsRenumber) {
                continue;
            }
            siblings.sort((left, right) -> {
                int compared = Long.compare(left.getSubtaskSortOrder(), right.getSubtaskSortOrder());
                if (compared != 0) {
                    return compared;
                }
                int leftIndex = inputOrder.getOrDefault(left.getId(), Integer.MAX_VALUE);
                int rightIndex = inputOrder.getOrDefault(right.getId(), Integer.MAX_VALUE);
                return Integer.compare(leftIndex, rightIndex);
            });
            for (int index = 0; index < siblings.size(); index++) {
                siblings.get(index).setSubtaskSortOrder(index);
            }
        }
    }

    /**
     * 复制任务主体并移除旧递归 subtasks 结构。
     *
     * @param source 原始任务
     * @return 不包含旧 subtasks 的任务副本
     */
    private static Task copyWithoutLegacySubtasks(Task source) {
        Task copy = Task.fromNbt(source.toNbt());
        copy.setId(source.getId());
        copy.setParentTaskId(source.getParentTaskId());
        copy.setSubtaskSortOrder(source.getSubtaskSortOrder());
        return copy;
    }

    /**
     * 为旧子任务补齐父任务上下文，避免迁移后丢失项目、作用域和人员信息。
     *
     * @param parent 父任务
     * @param child 子任务
     */
    private static void inheritMissingParentContext(Task parent, Task child) {
        if (child == null || parent == null) {
            return;
        }
        if (child.isProjectUnassigned() && !parent.isProjectUnassigned()) {
            child.setProjectId(parent.getProjectId());
        }
        if (parent.getScope() != null && child.getScope() != parent.getScope()) {
            child.setScope(parent.getScope());
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
     * 判断父子任务是否属于同一项目。双方都有项目 ID 且不一致时视为跨项目损坏关系。
     *
     * @param parent 父任务
     * @param child 子任务
     * @return 兼容时返回 {@code true}
     */
    private static boolean compatibleProject(Task parent, Task child) {
        String parentProject = normalizeOptionalText(parent.getProjectId());
        String childProject = normalizeOptionalText(child.getProjectId());
        return parentProject == null || childProject == null || parentProject.equals(childProject);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class NbtTaskFrame {
        private final CompoundTag nbt;
        private final String parentTaskId;
        private final long sortOrder;

        private NbtTaskFrame(CompoundTag nbt, String parentTaskId, long sortOrder) {
            this.nbt = nbt;
            this.parentTaskId = parentTaskId;
            this.sortOrder = sortOrder;
        }
    }

    private static final class SourceTaskFrame {
        private final Task task;
        private final String parentTaskId;
        private final long sortOrder;

        private SourceTaskFrame(Task task, String parentTaskId, long sortOrder) {
            this.task = task;
            this.parentTaskId = parentTaskId;
            this.sortOrder = sortOrder;
        }
    }
}
