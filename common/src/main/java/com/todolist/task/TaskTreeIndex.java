package com.todolist.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 任务树的只读快照索引。
 *
 * <p>任务实体本身只保存 {@code parentTaskId}，父子关系由本索引在需要时构建。
 * 该设计允许任意深度而不需要修改实体字段；所有遍历均使用显式栈，避免深层任务树
 * 触发递归栈溢出。</p>
 *
 * <p>索引构建时会安全地忽略无效父任务 ID 与循环父子关系：无效父任务会被视为根任务，
 * 检测到循环时会断开环中的一个父子边。索引只用于展示与查询，不修改任务对象。</p>
 */
public final class TaskTreeIndex {
    private final Map<String, Task> tasksById;
    private final Map<String, List<Task>> childrenByParentId;
    private final List<Task> roots;
    private final Map<String, Integer> depthByTaskId;
    private final Map<String, String> effectiveParentIdByTaskId;

    /**
     * 创建空任务树索引。
     */
    public TaskTreeIndex() {
        this.tasksById = Map.of();
        this.childrenByParentId = Map.of();
        this.roots = List.of();
        this.depthByTaskId = Map.of();
        this.effectiveParentIdByTaskId = Map.of();
    }

    private TaskTreeIndex(Map<String, Task> tasksById,
                          Map<String, List<Task>> childrenByParentId,
                          List<Task> roots,
                          Map<String, Integer> depthByTaskId,
                          Map<String, String> effectiveParentIdByTaskId) {
        this.tasksById = tasksById;
        this.childrenByParentId = childrenByParentId;
        this.roots = roots;
        this.depthByTaskId = depthByTaskId;
        this.effectiveParentIdByTaskId = effectiveParentIdByTaskId;
    }

    /**
     * 从任务集合构建只读树索引。
     *
     * @param source 任务集合，允许为 {@code null}
     * @return 任务树索引
     */
    public static TaskTreeIndex build(Collection<Task> source) {
        if (source == null || source.isEmpty()) {
            return new TaskTreeIndex();
        }

        Map<String, Task> byId = new LinkedHashMap<>();
        for (Task task : source) {
            if (task == null || isBlank(task.getId())) {
                continue;
            }
            byId.putIfAbsent(task.getId(), task);
        }
        if (byId.isEmpty()) {
            return new TaskTreeIndex();
        }

        Map<String, String> parentByTaskId = new LinkedHashMap<>();
        for (Task task : byId.values()) {
            String parentId = normalizeOptionalText(task.getParentTaskId());
            if (parentId != null && (parentId.equals(task.getId()) || !byId.containsKey(parentId))) {
                parentId = null;
            }
            parentByTaskId.put(task.getId(), parentId);
        }

        // 循环检测：每个节点顺着父链向上走，遇到自身路径中的重复节点时断开起点父边。
        for (String taskId : byId.keySet()) {
            Set<String> path = new HashSet<>();
            String current = taskId;
            while (current != null) {
                if (!path.add(current)) {
                    parentByTaskId.put(taskId, null);
                    break;
                }
                current = parentByTaskId.get(current);
            }
        }

        Map<String, List<Task>> children = new LinkedHashMap<>();
        List<Task> roots = new ArrayList<>();
        for (Task task : byId.values()) {
            String parentId = parentByTaskId.get(task.getId());
            if (parentId == null) {
                roots.add(task);
            } else {
                children.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(task);
            }
        }
        for (List<Task> siblings : children.values()) {
            siblings.sort(TaskTreeIndex::compareSiblingOrder);
        }

        // 使用显式队列计算深度，同时防止任何异常数据造成无限遍历。
        Map<String, Integer> depths = new HashMap<>();
        Deque<TaskDepth> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        for (Task root : roots) {
            queue.addLast(new TaskDepth(root, 0));
        }
        while (!queue.isEmpty()) {
            TaskDepth entry = queue.removeFirst();
            if (entry.task == null || entry.task.getId() == null || !visited.add(entry.task.getId())) {
                continue;
            }
            depths.put(entry.task.getId(), entry.depth);
            List<Task> childTasks = children.get(entry.task.getId());
            if (childTasks == null) {
                continue;
            }
            for (Task child : childTasks) {
                queue.addLast(new TaskDepth(child, entry.depth + 1));
            }
        }

        Map<String, List<Task>> immutableChildren = new LinkedHashMap<>();
        for (Map.Entry<String, List<Task>> entry : children.entrySet()) {
            immutableChildren.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new TaskTreeIndex(
                Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(immutableChildren),
                List.copyOf(roots),
                Collections.unmodifiableMap(depths),
                Collections.unmodifiableMap(parentByTaskId)
        );
    }

    /**
     * 返回索引中的任务总数。
     *
     * @return 任务总数
     */
    public int size() {
        return tasksById.size();
    }

    /**
     * 按 ID 查找任务。
     *
     * @param taskId 任务 ID
     * @return 对应任务；不存在时返回 {@code null}
     */
    public Task getTask(String taskId) {
        return taskId == null ? null : tasksById.get(taskId);
    }

    /**
     * 返回所有根任务。
     *
     * @return 根任务列表
     */
    public List<Task> getRoots() {
        return roots;
    }

    /**
     * 返回指定任务的直属子任务；子任务已按父内排序稳定排列。
     *
     * @param taskId 父任务 ID
     * @return 直属子任务列表，永不为 {@code null}
     */
    public List<Task> getChildren(String taskId) {
        if (taskId == null) {
            return List.of();
        }
        return childrenByParentId.getOrDefault(taskId, List.of());
    }

    /**
     * 返回指定任务的有效父任务 ID。
     *
     * @param taskId 任务 ID
     * @return 有效父任务 ID；根任务或任务不存在时返回 {@code null}
     */
    public String getParentTaskId(String taskId) {
        return taskId == null ? null : effectiveParentIdByTaskId.get(taskId);
    }

    /**
     * 返回指定任务的深度，根任务深度为 0。
     *
     * @param taskId 任务 ID
     * @return 层级深度；任务不存在时返回 0
     */
    public int getDepth(String taskId) {
        if (taskId == null) {
            return 0;
        }
        Integer depth = depthByTaskId.get(taskId);
        return depth == null ? 0 : depth;
    }

    /**
     * 判断任务是否为根任务。
     *
     * @param taskId 任务 ID
     * @return 根任务或未知任务返回 {@code true}
     */
    public boolean isRoot(String taskId) {
        return taskId == null || effectiveParentIdByTaskId.get(taskId) == null;
    }

    /**
     * 返回任务到根节点的完整路径，按根任务到目标任务排列。
     *
     * @param taskId 任务 ID
     * @return 路径列表；任务不存在时返回空列表
     */
    public List<Task> getPath(String taskId) {
        List<Task> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = taskId;
        while (current != null && visited.add(current)) {
            Task task = tasksById.get(current);
            if (task == null) {
                break;
            }
            path.add(task);
            current = effectiveParentIdByTaskId.get(current);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * 返回任务所有祖先的 ID 集合，不含自身。
     *
     * @param taskId 任务 ID
     * @return 祖先 ID 集合，按距离从近到远排列
     */
    public Set<String> getAncestorIds(String taskId) {
        Set<String> ancestors = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        String current = effectiveParentIdByTaskId.get(taskId);
        while (current != null && visited.add(current)) {
            ancestors.add(current);
            current = effectiveParentIdByTaskId.get(current);
        }
        return ancestors;
    }

    /**
     * 返回任务所属根任务 ID。
     *
     * @param taskId 任务 ID
     * @return 根任务 ID；任务不存在时返回 {@code null}
     */
    public String getRootTaskId(String taskId) {
        List<Task> path = getPath(taskId);
        return path.isEmpty() ? null : path.get(0).getId();
    }

    /**
     * 判断 {@code candidateId} 是否为 {@code ancestorId} 的后代。
     *
     * @param candidateId 候选任务 ID
     * @param ancestorId 祖先任务 ID
     * @return 是后代时返回 {@code true}
     */
    public boolean isDescendantOf(String candidateId, String ancestorId) {
        if (candidateId == null || ancestorId == null || candidateId.equals(ancestorId)) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        String current = effectiveParentIdByTaskId.get(candidateId);
        while (current != null && visited.add(current)) {
            if (current.equals(ancestorId)) {
                return true;
            }
            current = effectiveParentIdByTaskId.get(current);
        }
        return false;
    }

    /**
     * 以先序遍历方式收集指定任务的全部后代 ID，不含自身。
     *
     * @param taskId 根任务 ID
     * @return 后代 ID 列表
     */
    public List<String> collectDescendantIds(String taskId) {
        List<String> result = new ArrayList<>();
        if (taskId == null) {
            return result;
        }
        Deque<String> stack = new ArrayDeque<>();
        pushChildrenInReverse(stack, taskId);
        Set<String> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (current == null || !visited.add(current)) {
                continue;
            }
            result.add(current);
            pushChildrenInReverse(stack, current);
        }
        return result;
    }

    /**
     * 以先序遍历方式收集指定任务及其全部后代。
     *
     * @param taskId 根任务 ID
     * @return 任务及其后代列表
     */
    public List<Task> collectSubtree(String taskId) {
        List<Task> result = new ArrayList<>();
        Task root = getTask(taskId);
        if (root == null) {
            return result;
        }
        result.add(root);
        for (String descendantId : collectDescendantIds(taskId)) {
            Task descendant = getTask(descendantId);
            if (descendant != null) {
                result.add(descendant);
            }
        }
        return result;
    }

    /**
     * 按树结构展开全部任务，根任务在前，后代紧随其后。
     *
     * @return 先序任务列表
     */
    public List<Task> flattenAll() {
        List<Task> result = new ArrayList<>(tasksById.size());
        Set<String> visited = new HashSet<>();
        Deque<Task> stack = new ArrayDeque<>();
        for (int index = roots.size() - 1; index >= 0; index--) {
            stack.push(roots.get(index));
        }
        while (!stack.isEmpty()) {
            Task task = stack.pop();
            if (task == null || task.getId() == null || !visited.add(task.getId())) {
                continue;
            }
            result.add(task);
            List<Task> children = childrenByParentId.get(task.getId());
            if (children == null) {
                continue;
            }
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
        return result;
    }

    /**
     * 构建任务所在层级的可读路径标题，不包含任务自身。
     *
     * @param taskId 任务 ID
     * @param separator 分隔符
     * @return 父级路径文本；无父级时返回空字符串
     */
    public String buildParentPathText(String taskId, String separator) {
        List<Task> path = getPath(taskId);
        if (path.size() <= 1) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < path.size() - 1; index++) {
            Task task = path.get(index);
            if (task == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(separator == null ? " / " : separator);
            }
            String title = task.getTitle();
            if (title == null || title.isBlank()) {
                title = task.getId();
            }
            builder.append(title);
        }
        return builder.toString();
    }

    private void pushChildrenInReverse(Deque<String> stack, String taskId) {
        List<Task> children = childrenByParentId.get(taskId);
        if (children == null) {
            return;
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            Task child = children.get(index);
            if (child != null && child.getId() != null) {
                stack.push(child.getId());
            }
        }
    }

    private static int compareSiblingOrder(Task left, Task right) {
        long leftOrder = left == null ? 0L : left.getSubtaskSortOrder();
        long rightOrder = right == null ? 0L : right.getSubtaskSortOrder();
        int compared = Long.compare(leftOrder, rightOrder);
        if (compared != 0) {
            return compared;
        }
        String leftId = left == null || left.getId() == null ? "" : left.getId();
        String rightId = right == null || right.getId() == null ? "" : right.getId();
        return leftId.compareTo(rightId);
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class TaskDepth {
        private final Task task;
        private final int depth;

        private TaskDepth(Task task, int depth) {
            this.task = task;
            this.depth = depth;
        }
    }
}
