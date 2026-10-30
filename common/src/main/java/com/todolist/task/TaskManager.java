package com.todolist.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 维护任务集合，并提供无限层级任务树的增删改查、移动、排序与完成态聚合能力。
 *
 * <p>内部始终以“任务 ID → 任务”的平铺映射保存数据，父子关系由稳定的
 * {@code parentTaskId} 表达。树形索引由 {@link TaskTreeIndex} 惰性重建，因此任务
 * 层级没有固定上限，同时所有遍历均为迭代实现，不会因深层嵌套导致栈溢出。</p>
 */
public class TaskManager {
    private final Map<String, Task> tasks;
    /**
     * 父任务完成态聚合脏标记。任何完成态或树结构变化都会置为 {@code true}，
     * 由读取路径做一次自底向上的 O(n) 聚合。
     */
    private boolean parentCompletionDirty = true;
    private boolean treeIndexDirty = true;
    private TaskTreeIndex treeIndex = new TaskTreeIndex();

    /**
     * 初始化任务管理器，创建空的任务存储。
     */
    public TaskManager() {
        this.tasks = new LinkedHashMap<>();
    }

    /**
     * 创建并添加一个根任务。
     *
     * @param title 任务标题
     * @param description 任务描述
     * @return 新创建的任务对象
     */
    public Task addTask(String title, String description) {
        Task task = new Task(title, description);
        addTask(task);
        return task;
    }

    /**
     * 添加一个已存在的任务对象。
     *
     * <p>任务 ID 已存在时拒绝覆盖原任务，避免父子引用被静默替换。调用方可根据返回值判断
     * 是否实际加入；同一对象重复添加视为幂等成功。</p>
     *
     * @param task 待添加的任务
     * @return 实际加入或对象已在管理器中时返回 true；空任务或 ID 冲突时返回 false
     */
    public boolean addTask(Task task) {
        if (task == null) {
            return false;
        }
        if (task.getId() == null || task.getId().isBlank()) {
            task.setId(UUID.randomUUID().toString());
        }
        if (task.getId().equals(task.getParentTaskId())) {
            task.setParentTaskId(null);
        }
        Task existing = tasks.get(task.getId());
        if (existing != null) {
            return existing == task;
        }
        tasks.put(task.getId(), task);
        markTreeDirty();
        return true;
    }

    /**
     * 创建并添加指定父任务下的子任务，自动继承项目、作用域与创建者上下文。
     *
     * @param parentTaskId 父任务 ID
     * @param title 子任务标题
     * @param description 子任务描述
     * @return 新建子任务；父任务不存在时返回 {@code null}
     */
    public Task addSubtask(String parentTaskId, String title, String description) {
        Task parent = getTask(parentTaskId);
        if (parent == null) {
            return null;
        }
        Task child = new Task(title, description);
        child.setProjectId(parent.getProjectId());
        child.setScope(parent.getScope());
        child.setCreatorUuid(parent.getCreatorUuid());
        child.setAssigneeUuid(parent.getAssigneeUuid());
        child.setAssigneeName(parent.getAssigneeName());
        child.setParentTaskId(parent.getId());
        child.setSubtaskSortOrder(resolveNextSubtaskSortOrder(parent.getId()));
        addTask(child);
        return child;
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
     * 删除任务及其全部后代任务，避免留下孤立子任务。
     *
     * @param taskId 任务 ID
     * @return 实际删除的任务数量
     */
    public int deleteTask(String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return 0;
        }
        TaskTreeIndex index = getTreeIndex();
        List<String> idsToRemove = new ArrayList<>();
        idsToRemove.add(taskId);
        idsToRemove.addAll(index.collectDescendantIds(taskId));
        int removed = 0;
        for (String id : idsToRemove) {
            if (tasks.remove(id) != null) {
                removed++;
            }
        }
        if (removed > 0) {
            markTreeDirty();
        }
        return removed;
    }

    /**
     * 删除指定项目下的全部任务及其后代。
     *
     * @param projectId 项目 ID
     * @return 实际删除的任务数量
     */
    public int deleteTasksByProjectId(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return 0;
        }
        TaskTreeIndex index = getTreeIndex();
        Set<String> idsToRemove = new LinkedHashSet<>();
        for (Task task : tasks.values()) {
            if (task != null && task.belongsToProject(projectId)) {
                idsToRemove.add(task.getId());
                idsToRemove.addAll(index.collectDescendantIds(task.getId()));
            }
        }
        int removed = 0;
        for (String id : idsToRemove) {
            if (tasks.remove(id) != null) {
                removed++;
            }
        }
        if (removed > 0) {
            markTreeDirty();
        }
        return removed;
    }

    /**
     * 将指定任务标记为已完成，并级联完成后代。
     *
     * <p>该方法是幂等的：任务已经完成时不会再次切换，也不会取消任何后代任务。
     * GUI 的复选框仍使用 {@link #toggleTaskCompletion(String)}，命令的“done”语义
     * 使用本方法，避免依赖一次条件切换。</p>
     *
     * @param taskId 任务 ID
     * @return 任务存在并处于完成状态时返回 true；任务不存在时返回 false
     */
    public boolean completeTask(String taskId) {
        syncParentCompletionStates();
        Task task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        if (task.isCompleted()) {
            return true;
        }
        toggleTaskCompletion(taskId);
        return true;
    }

    /**
     * 切换指定任务的完成状态。
     *
     * <p>有子任务的任务会级联切换其全部后代；叶子节点只切换自身，随后自底向上
     * 重新聚合所有祖先。该语义与项目原有“父任务随全部子任务完成而完成”一致。</p>
     *
     * @param taskId 任务 ID
     */
    public void toggleTaskCompletion(String taskId) {
        syncParentCompletionStates();
        Task task = tasks.get(taskId);
        if (task == null) {
            return;
        }
        boolean targetCompleted = !task.isCompleted();
        List<String> descendants = getTreeIndex().collectDescendantIds(taskId);
        if (descendants.isEmpty()) {
            task.setCompleted(targetCompleted);
        } else {
            task.setCompleted(targetCompleted);
            for (String descendantId : descendants) {
                Task descendant = tasks.get(descendantId);
                if (descendant != null) {
                    descendant.setCompleted(targetCompleted);
                }
            }
        }
        parentCompletionDirty = true;
        syncParentCompletionStates();
    }

    /**
     * 标记父任务完成状态为脏，下次读取时重新聚合。
     */
    public void markParentCompletionDirty() {
        parentCompletionDirty = true;
    }

    /**
     * 按当前稳定顺序返回已完成根任务。
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
     * 按当前稳定顺序返回未完成根任务。
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
     * 返回指定项目下的顶层任务。
     *
     * @param projectId 项目 ID
     * @return 指定项目下的顶层任务列表
     */
    public List<Task> getTasksByProject(String projectId) {
        syncParentCompletionStates();
        return tasks.values().stream()
                .filter(Task::isTopLevelTask)
                .filter(task -> Objects.equals(task.getProjectId(), projectId))
                .collect(Collectors.toList());
    }

    /**
     * 返回指定项目下的全部任务（含任意深度子任务），供筛选/搜索与调试使用。
     *
     * @param projectId 项目 ID
     * @return 项目内全部任务列表
     */
    public List<Task> getAllTasksByProject(String projectId) {
        syncParentCompletionStates();
        return tasks.values().stream()
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
        markTreeDirty();
        return true;
    }

    /**
     * 按指定顺序重排同一父任务下的直属子任务。
     *
     * @param parentTaskId 父任务 ID
     * @param orderedSubtaskIds 目标顺序下的子任务 ID 列表
     * @return 当子任务顺序发生变化时返回 {@code true}
     */
    public boolean reorderSubtasks(String parentTaskId, List<String> orderedSubtaskIds) {
        if (parentTaskId == null || parentTaskId.isEmpty() || orderedSubtaskIds == null) {
            return false;
        }
        Set<String> targetIds = new LinkedHashSet<>();
        for (String taskId : orderedSubtaskIds) {
            Task task = taskId == null ? null : tasks.get(taskId);
            if (task != null && parentTaskId.equals(task.getParentTaskId())) {
                targetIds.add(taskId);
            }
        }
        List<Task> currentSiblings = getDirectChildren(parentTaskId);
        if (targetIds.size() != currentSiblings.size() || targetIds.size() < 2) {
            return false;
        }
        List<String> currentOrderIds = currentSiblings.stream()
                .map(Task::getId)
                .toList();
        List<String> targetOrderIds = new ArrayList<>(targetIds);
        if (currentOrderIds.equals(targetOrderIds)) {
            return false;
        }
        for (int index = 0; index < targetOrderIds.size(); index++) {
            Task task = tasks.get(targetOrderIds.get(index));
            if (task != null) {
                task.setSubtaskSortOrder(index);
            }
        }
        markTreeDirty();
        return true;
    }

    /**
     * 判断是否可以将任务移动到指定父任务下。
     *
     * @param taskId 待移动任务 ID
     * @param newParentTaskId 新父任务 ID；{@code null} 表示移动到根层级
     * @return 允许移动时返回 {@code true}
     */
    public boolean canMoveTask(String taskId, String newParentTaskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        if (newParentTaskId == null || newParentTaskId.isEmpty()) {
            return task.isSubtask();
        }
        if (newParentTaskId.equals(taskId)) {
            return false;
        }
        Task newParent = tasks.get(newParentTaskId);
        if (newParent == null) {
            return false;
        }
        String taskProject = normalize(task.getProjectId());
        String parentProject = normalize(newParent.getProjectId());
        if (taskProject != null && parentProject != null && !taskProject.equals(parentProject)) {
            return false;
        }
        return !getTreeIndex().isDescendantOf(newParentTaskId, taskId);
    }

    /**
     * 将任务移动到另一个父任务下，并放到同级末尾。
     *
     * @param taskId 待移动任务 ID
     * @param newParentTaskId 新父任务 ID；{@code null} 表示移动到根层级
     * @return 移动成功时返回 {@code true}
     */
    public boolean moveTask(String taskId, String newParentTaskId) {
        return moveTask(taskId, newParentTaskId, Integer.MAX_VALUE);
    }

    /**
     * 将任务移动到另一个父任务下，并插入指定同级位置。
     *
     * @param taskId 待移动任务 ID
     * @param newParentTaskId 新父任务 ID；{@code null} 表示移动到根层级
     * @param targetIndex 同级目标索引，超出范围时追加到末尾
     * @return 移动成功时返回 {@code true}
     */
    public boolean moveTask(String taskId, String newParentTaskId, int targetIndex) {
        if (!canMoveTask(taskId, newParentTaskId)) {
            return false;
        }
        Task task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        TaskTreeIndex index = getTreeIndex();
        String oldParentId = normalize(task.getParentTaskId());
        List<Task> newSiblings = new ArrayList<>();
        if (newParentTaskId != null && !newParentTaskId.isEmpty()) {
            for (Task sibling : index.getChildren(newParentTaskId)) {
                if (sibling != null && !taskId.equals(sibling.getId())) {
                    newSiblings.add(sibling);
                }
            }
        } else {
            for (Task root : index.getRoots()) {
                if (root != null && !taskId.equals(root.getId())) {
                    newSiblings.add(root);
                }
            }
        }
        int insertIndex = Math.max(0, Math.min(targetIndex, newSiblings.size()));
        newSiblings.add(insertIndex, task);

        if (oldParentId != null && !oldParentId.equals(newParentTaskId)) {
            List<Task> oldSiblings = new ArrayList<>();
            for (Task sibling : index.getChildren(oldParentId)) {
                if (sibling != null && !taskId.equals(sibling.getId())) {
                    oldSiblings.add(sibling);
                }
            }
            for (int i = 0; i < oldSiblings.size(); i++) {
                oldSiblings.get(i).setSubtaskSortOrder(i);
            }
        }

        task.setParentTaskId(newParentTaskId);
        Task newParent = newParentTaskId == null || newParentTaskId.isEmpty() ? null : tasks.get(newParentTaskId);
        if (newParent != null) {
            if (isBlank(task.getProjectId()) && !isBlank(newParent.getProjectId())) {
                task.setProjectId(newParent.getProjectId());
            }
            if (newParent.getScope() != null) {
                task.setScope(newParent.getScope());
            }
        }
        for (int i = 0; i < newSiblings.size(); i++) {
            newSiblings.get(i).setSubtaskSortOrder(i);
        }
        markTreeDirty();
        parentCompletionDirty = true;
        return true;
    }

    /**
     * 返回指定任务的直属子任务，按父内顺序稳定排序。
     *
     * @param parentTaskId 父任务 ID
     * @return 直属子任务列表
     */
    public List<Task> getDirectChildren(String parentTaskId) {
        return new ArrayList<>(getTreeIndex().getChildren(parentTaskId));
    }

    /**
     * 返回指定任务的全部后代任务，按先序排列。
     *
     * @param taskId 任务 ID
     * @return 后代任务列表
     */
    public List<Task> getDescendants(String taskId) {
        List<Task> descendants = new ArrayList<>();
        for (String descendantId : getTreeIndex().collectDescendantIds(taskId)) {
            Task task = tasks.get(descendantId);
            if (task != null) {
                descendants.add(task);
            }
        }
        return descendants;
    }

    /**
     * 返回任务深度，根任务为 0。
     *
     * @param taskId 任务 ID
     * @return 层级深度
     */
    public int getTaskDepth(String taskId) {
        return getTreeIndex().getDepth(taskId);
    }

    /**
     * 返回任务从根到自身的完整路径。
     *
     * @param taskId 任务 ID
     * @return 路径列表
     */
    public List<Task> getTaskPath(String taskId) {
        return getTreeIndex().getPath(taskId);
    }

    /**
     * 返回任务树只读快照。
     *
     * @return 任务树索引
     */
    public TaskTreeIndex getTreeIndex() {
        if (treeIndexDirty) {
            treeIndex = TaskTreeIndex.build(tasks.values());
            treeIndexDirty = false;
        }
        return treeIndex;
    }

    /**
     * 计算指定父任务下下一个可用的父内排序号。
     *
     * @param parentTaskId 父任务 ID
     * @return 下一个排序号
     */
    public long resolveNextSubtaskSortOrder(String parentTaskId) {
        long next = 0L;
        for (Task sibling : getTreeIndex().getChildren(parentTaskId)) {
            if (sibling != null) {
                next = Math.max(next, sibling.getSubtaskSortOrder() + 1L);
            }
        }
        return next;
    }

    /**
     * 清空当前管理器中的全部任务。
     */
    public void clearAll() {
        tasks.clear();
        markTreeDirty();
    }

    /**
     * 将任务集合标记为已变化，并清空树索引缓存。
     */
    private void markTreeDirty() {
        treeIndexDirty = true;
        parentCompletionDirty = true;
    }

    /**
     * 根据全部子任务完成状态自底向上同步父任务完成状态。
     *
     * <p>按深度降序处理，保证子父任务先于祖先计算；每层只扫描一次直属子任务，
     * 总复杂度 O(n)。只有存在子任务的任务会被聚合，叶子任务保留自身完成状态。</p>
     */
    private void syncParentCompletionStates() {
        if (!parentCompletionDirty) {
            return;
        }
        parentCompletionDirty = false;
        TaskTreeIndex index = getTreeIndex();
        List<Task> orderedByDepth = new ArrayList<>(tasks.values());
        orderedByDepth.sort(Comparator.comparingInt(task -> -index.getDepth(task == null ? null : task.getId())));
        for (Task task : orderedByDepth) {
            if (task == null || task.getId() == null) {
                continue;
            }
            List<Task> children = index.getChildren(task.getId());
            if (children.isEmpty()) {
                continue;
            }
            boolean allCompleted = true;
            for (Task child : children) {
                if (child != null && !child.isCompleted()) {
                    allCompleted = false;
                    break;
                }
            }
            task.setCompleted(allCompleted);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
