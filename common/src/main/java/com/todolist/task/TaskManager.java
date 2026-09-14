package com.todolist.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 负责维护任务集合，并提供任务的基础增删改查与排序能力。
 */
public class TaskManager {
    private final Map<String, Task> tasks;
    /**
     * 父任务完成态惰性同步标志。任何结构或完成态变更时置为 {@code true}，
     * 由读路径在必要时统一重建，避免每次 getter 都触发全量父子扫描。
     */
    private boolean parentCompletionDirty = true;

    /**
     * 初始化任务管理器，创建空的任务存储。
     */
    public TaskManager() {
        this.tasks = new LinkedHashMap<>();
    }

    /**
     * 创建并添加一个新任务。
     *
     * @param title 任务标题
     * @param description 任务描述
     * @return 新创建的任务对象
     */
    public Task addTask(String title, String description) {
        Task task = new Task(title, description);
        tasks.put(task.getId(), task);
        parentCompletionDirty = true;
        return task;
    }

    /**
     * 添加一个已存在的任务对象。
     *
     * @param task 待添加的任务
     */
    public void addTask(Task task) {
        tasks.put(task.getId(), task);
        parentCompletionDirty = true;
    }

    /**
     * 按任务 ID 获取任务。
     *
     * @param id 任务 ID
     * @return 对应任务，不存在时返回 {@code null}
     */
    public Task getTask(String id) {
        syncParentCompletionStates();
        return tasks.get(id);
    }

    /**
     * 按当前稳定顺序返回全部任务。
     *
     * @return 当前顺序下的全部任务列表
     */
    public List<Task> getAllTasks() {
        syncParentCompletionStates();
        return new ArrayList<>(tasks.values());
    }

    /**
     * 按任务 ID 删除任务，并级联删除其全部后代任务（子任务、孙任务…）。
     *
     * <p>层级不限，依赖任务被删除时必须连同其下游一起移除，
     * 否则会留下 parentTaskId 悬空的孤儿任务。
     *
     * @param taskId 任务 ID
     */
    public void deleteTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return;
        }
        Set<String> removedIds = collectDescendantIds(taskId);
        removedIds.add(taskId);
        for (String id : removedIds) {
            tasks.remove(id);
        }
        parentCompletionDirty = true;
    }

    /**
     * 收集指定任务的全部后代任务 ID（子任务、孙任务…，不含自身）。
     *
     * <p>使用广度优先遍历并记录已访问节点，父子关系成环时也不会死循环。
     *
     * @param taskId 任务 ID
     * @return 后代任务 ID 集合；无后代时返回空集合
     */
    public Set<String> collectDescendantIds(String taskId) {
        Set<String> descendants = new LinkedHashSet<>();
        if (taskId == null || taskId.isEmpty()) {
            return descendants;
        }
        Map<String, List<Task>> childrenByParent = buildChildrenIndex();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(taskId);
        Set<String> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            String currentId = pending.poll();
            if (currentId == null || !visited.add(currentId)) {
                continue;
            }
            List<Task> children = childrenByParent.get(currentId);
            if (children == null) {
                continue;
            }
            for (Task child : children) {
                if (child == null || child.getId() == null || !descendants.add(child.getId())) {
                    continue;
                }
                pending.add(child.getId());
            }
        }
        return descendants;
    }

    /**
     * 判断把 {@code taskId} 挂到 {@code parentTaskId} 下是否会造成父子关系成环。
     *
     * @param taskId       待移动的任务 ID
     * @param parentTaskId 目标父任务 ID
     * @return 会成环时返回 true
     */
    public boolean wouldCreateParentCycle(String taskId, String parentTaskId) {
        if (taskId == null || taskId.isEmpty() || parentTaskId == null || parentTaskId.isEmpty()) {
            return false;
        }
        if (taskId.equals(parentTaskId)) {
            return true;
        }
        return collectDescendantIds(taskId).contains(parentTaskId);
    }

    /**
     * 返回指定父任务下的直属子任务，按父内顺序稳定排序。
     *
     * @param parentTaskId 父任务 ID
     * @return 直属子任务列表；无子任务时返回空列表
     */
    public List<Task> getChildTasks(String parentTaskId) {
        return getSiblingSubtasksInOrder(parentTaskId);
    }

    /**
     * 按父任务 ID 归组全部子任务，用于层级遍历。
     *
     * @return 父任务 ID → 直属子任务列表
     */
    private Map<String, List<Task>> buildChildrenIndex() {
        Map<String, List<Task>> index = new LinkedHashMap<>();
        for (Task task : tasks.values()) {
            if (task == null || !task.isSubtask()) {
                continue;
            }
            String parentId = task.getParentTaskId();
            if (parentId == null || parentId.isEmpty()) {
                continue;
            }
            index.computeIfAbsent(parentId, key -> new ArrayList<>()).add(task);
        }
        return index;
    }

    /**
     * 删除指定项目下的全部任务。
     *
     * @param projectId 项目 ID
     * @return 实际删除的任务数量
     */
    public int deleteTasksByProjectId(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return 0;
        }
        int removedCount = 0;
        Iterator<Map.Entry<String, Task>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Task> entry = iterator.next();
            Task task = entry.getValue();
            if (task != null && task.belongsToProject(projectId)) {
                iterator.remove();
                removedCount++;
            }
        }
        parentCompletionDirty = true;
        return removedCount;
    }

    /**
     * 切换指定任务的完成状态。
     *
     * <p>无子任务时切换自身；有子任务时级联切换其全部后代（层级不限）。
     * 挂有触发器的任务完成态由触发器决定（触发器优先），因此手动勾选时直接切换自身，
     * 不受子任务聚合覆盖；带触发器的后代则跳过，避免覆盖触发器进度。
     *
     * @param taskId 任务 ID
     */
    public void toggleTaskCompletion(String taskId) {
        syncParentCompletionStates();
        Task task = tasks.get(taskId);
        if (task == null) {
            return;
        }
        if (!hasChildren(task.getId())) {
            task.setCompleted(!task.isCompleted());
            parentCompletionDirty = true;
            return;
        }
        boolean targetCompleted = !task.isCompleted();
        if (task.hasTrigger()) {
            task.setCompleted(targetCompleted);
        }
        boolean changed = false;
        for (String descendantId : collectDescendantIds(task.getId())) {
            Task descendant = tasks.get(descendantId);
            if (descendant == null || descendant.hasTrigger() || descendant.isCompleted() == targetCompleted) {
                continue;
            }
            descendant.setCompleted(targetCompleted);
            changed = true;
        }
        if (changed || task.hasTrigger()) {
            parentCompletionDirty = true;
        }
    }

    /**
     * 标记父任务完成状态为脏，下次读取时会重新聚合。
     * 供 GUI 层在直接修改子任务完成状态后调用。
     */
    public void markParentCompletionDirty() {
        parentCompletionDirty = true;
    }

    /**
     * 按当前稳定顺序返回已完成任务。
     *
     * @return 已完成任务列表
     */
    public List<Task> getCompletedTasks() {
        syncParentCompletionStates();
        return tasks.values().stream()
                .filter(Task::isTopLevelTask)
                .filter(Task::isCompleted)
                .collect(Collectors.toList());
    }

    /**
     * 按当前稳定顺序返回未完成任务。
     *
     * @return 未完成任务列表
     */
    public List<Task> getIncompleteTasks() {
        syncParentCompletionStates();
        return tasks.values().stream()
                .filter(Task::isTopLevelTask)
                .filter(task -> !task.isCompleted())
                .collect(Collectors.toList());
    }

    /**
     * 按当前稳定顺序返回指定项目下的任务。
     *
     * @param projectId 项目 ID
     * @return 指定项目下的任务列表
     */
    public List<Task> getTasksByProject(String projectId) {
        syncParentCompletionStates();
        return tasks.values().stream()
                .filter(Task::isTopLevelTask)
                .filter(task -> Objects.equals(task.getProjectId(), projectId))
                .collect(Collectors.toList());
    }

    /**
     * 仅重排目标任务子集在整体列表中的相对顺序，并保持其余任务位置不变。
     *
     * @param orderedTaskIds 目标顺序下的任务 ID 列表
     * @return 当任务顺序发生变化时返回 {@code true}
     */
    public boolean reorderTasks(List<String> orderedTaskIds) {
        if (orderedTaskIds == null || orderedTaskIds.size() < 2) {
            return false;
        }
        LinkedHashSet<String> targetIds = new LinkedHashSet<>();
        for (String taskId : orderedTaskIds) {
            if (taskId != null && tasks.containsKey(taskId)) {
                targetIds.add(taskId);
            }
        }
        if (targetIds.size() < 2) {
            return false;
        }

        List<Task> reorderedSubset = new ArrayList<>();
        for (String taskId : targetIds) {
            reorderedSubset.add(tasks.get(taskId));
        }

        LinkedHashMap<String, Task> rebuiltTasks = new LinkedHashMap<>();
        Iterator<Task> reorderedIterator = reorderedSubset.iterator();
        boolean changed = false;

        for (Map.Entry<String, Task> entry : tasks.entrySet()) {
            if (targetIds.contains(entry.getKey())) {
                Task nextTask = reorderedIterator.next();
                rebuiltTasks.put(nextTask.getId(), nextTask);
                if (!changed && !Objects.equals(nextTask.getId(), entry.getKey())) {
                    changed = true;
                }
                continue;
            }
            rebuiltTasks.put(entry.getKey(), entry.getValue());
        }

        if (!changed) {
            return false;
        }

        tasks.clear();
        tasks.putAll(rebuiltTasks);
        return true;
    }

    /**
     * 按指定顺序重排同一父任务下的直属子任务，并同步更新父内排序号。
     *
     * @param parentTaskId 父任务 ID
     * @param orderedSubtaskIds 目标顺序下的子任务 ID 列表
     * @return 当子任务顺序发生变化时返回 {@code true}
     */
    public boolean reorderSubtasks(String parentTaskId, List<String> orderedSubtaskIds) {
        if (parentTaskId == null || parentTaskId.isEmpty() || orderedSubtaskIds == null || orderedSubtaskIds.size() < 2) {
            return false;
        }
        LinkedHashSet<String> targetIds = new LinkedHashSet<>();
        for (String taskId : orderedSubtaskIds) {
            Task task = taskId == null ? null : tasks.get(taskId);
            if (task != null && task.isSubtask() && Objects.equals(parentTaskId, task.getParentTaskId())) {
                targetIds.add(taskId);
            }
        }
        if (targetIds.size() < 2) {
            return false;
        }

        List<Task> currentSiblings = getSiblingSubtasksInOrder(parentTaskId);
        List<String> currentOrderIds = currentSiblings.stream()
                .map(Task::getId)
                .filter(Objects::nonNull)
                .toList();
        List<String> targetOrderIds = new ArrayList<>(targetIds);
        if (currentOrderIds.size() != targetOrderIds.size()) {
            return false;
        }
        if (currentOrderIds.equals(targetOrderIds)) {
            return false;
        }

        for (int index = 0; index < targetOrderIds.size(); index++) {
            Task task = tasks.get(targetOrderIds.get(index));
            if (task != null) {
                task.setSubtaskSortOrder(index);
            }
        }
        parentCompletionDirty = true;
        return true;
    }

    /**
     * 清空当前管理器中的全部任务。
     */
    public void clearAll() {
        tasks.clear();
        parentCompletionDirty = true;
    }

    /**
     * 判断指定任务是否存在直属子任务。
     * 供 GUI 与命令层判断"父任务"（完成态由子任务聚合），例如禁止为父任务设置触发器。
     *
     * @param taskId 任务 ID
     * @return 存在直属子任务时返回 true
     */
    public boolean hasChildren(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return false;
        }
        return tasks.values().stream().anyMatch(task -> taskId.equals(task.getParentTaskId()));
    }

    /**
     * 返回指定父任务下的直属子任务，并按父内顺序稳定排序。
     *
     * @param parentTaskId 父任务 ID
     * @return 当前父任务下的直属子任务
     */
    private List<Task> getSiblingSubtasksInOrder(String parentTaskId) {
        if (parentTaskId == null || parentTaskId.isEmpty()) {
            return List.of();
        }
        return tasks.values().stream()
                .filter(task -> task != null && task.isSubtask() && Objects.equals(parentTaskId, task.getParentTaskId()))
                .sorted((left, right) -> {
                    int sortCompare = Long.compare(left.getSubtaskSortOrder(), right.getSubtaskSortOrder());
                    if (sortCompare != 0) {
                        return sortCompare;
                    }
                    String leftId = left.getId() == null ? "" : left.getId();
                    String rightId = right.getId() == null ? "" : right.getId();
                    return leftId.compareTo(rightId);
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据直属子任务完成状态同步父任务完成状态，层级不限（子任务的子任务同样参与聚合）。
     *
     * <p>父任务挂有触发器时跳过：其完成态由触发器决定（触发器优先），
     * 避免聚合结果把触发器刚设置的完成态覆盖回未完成。
     *
     * <p>采用惰性重建：仅当 {@link #parentCompletionDirty} 为 {@code true} 时执行；
     * 内部按"反复全量重算直到不再变化"的方式自底向上收敛，保证深层节点的完成态
     * 能正确反映到祖先节点。迭代轮数上限为任务总数，父子关系成环时也不会死循环。
     */
    private void syncParentCompletionStates() {
        if (!parentCompletionDirty) {
            return;
        }
        parentCompletionDirty = false;
        Map<String, List<Task>> childrenByParent = buildChildrenIndex();
        if (childrenByParent.isEmpty()) {
            return;
        }
        int maxRounds = Math.max(1, Math.min(tasks.size(), 32));
        for (int round = 0; round < maxRounds; round++) {
            boolean changed = false;
            for (Map.Entry<String, List<Task>> entry : childrenByParent.entrySet()) {
                Task parent = tasks.get(entry.getKey());
                if (parent == null || parent.hasTrigger()) {
                    continue;
                }
                List<Task> children = entry.getValue();
                if (children == null || children.isEmpty()) {
                    continue;
                }
                boolean allCompleted = true;
                for (Task child : children) {
                    if (child == null || !child.isCompleted()) {
                        allCompleted = false;
                        break;
                    }
                }
                if (parent.isCompleted() != allCompleted) {
                    parent.setCompleted(allCompleted);
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
    }
}
