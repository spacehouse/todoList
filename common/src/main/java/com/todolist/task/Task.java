package com.todolist.task;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

/**
 * Task entity for Todo List
 *
 * Features:
 * - Basic task information (title, description)
 * - Completion status
 * - Priority levels
 * - Tags/categories
 * - Creation and due dates
 * - Subtasks support
 */
public class Task {
    private static final int NBT_LIST_TYPE = 9;
    private static final int NBT_COMPOUND_TYPE = 10;
    private static final String PARENT_TASK_ID_KEY = "parentTaskId";
    private static final String SUBTASK_SORT_ORDER_KEY = "subtaskSortOrder";
    private String id;
    private String title;
    private String description;
    private boolean completed;
    private Priority priority;
    private Set<String> tags;
    private long createdAt;
    private Long dueDate;
    private List<Task> subtasks;
    private Scope scope;
    private String creatorUuid;
    private String assigneeUuid;
    private String assigneeName;
    private String projectId; // New field for project association
    private String parentTaskId;
    private long subtaskSortOrder;

    public Task(String title, String description) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.completed = false;
        this.priority = Priority.MEDIUM;
        this.tags = new LinkedHashSet<>();
        this.createdAt = System.currentTimeMillis();
        this.dueDate = null;
        this.subtasks = new ArrayList<>();
        this.scope = Scope.PERSONAL;
        this.creatorUuid = null;
        this.assigneeUuid = null;
        this.assigneeName = null;
        this.projectId = null;
        this.parentTaskId = null;
        this.subtaskSortOrder = 0L;
    }

    /**
     * 将任务写入 NBT。
     *
     * <p>当前格式统一按平铺结构写出：每个任务只保存自身的 {@code parentTaskId}
     * 和父内排序号，因此任意深度都不会增加 NBT 嵌套层级。空的 {@code subtasks}
     * 列表仅用于兼容仍在读取旧递归格式的第三方工具。</p>
     *
     * @return 当前任务的 NBT 表示
     */
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", id == null ? "" : id);
        nbt.putString("title", title == null ? "" : title);
        nbt.putString("description", description == null ? "" : description);
        nbt.putBoolean("completed", completed);
        nbt.putString("priority", priority == null ? Priority.MEDIUM.name() : priority.name());
        nbt.putLong("createdAt", createdAt);

        // Tags
        ListTag tagsList = new ListTag();
        for (String tag : tags) {
            CompoundTag tagNbt = new CompoundTag();
            tagNbt.putString("tag", tag);
            tagsList.add(tagNbt);
        }
        nbt.put("tags", tagsList);

        // Due date (optional)
        if (dueDate != null) {
            nbt.putLong("dueDate", dueDate);
        }

        nbt.putString("scope", scope.name());
        if (creatorUuid != null) {
            nbt.putString("creatorUuid", creatorUuid);
        }
        if (assigneeUuid != null) {
            nbt.putString("assigneeUuid", assigneeUuid);
        }
        if (assigneeName != null) {
            nbt.putString("assigneeName", assigneeName);
        }
        if (projectId != null) {
            nbt.putString("projectId", projectId);
        }
        if (parentTaskId != null) {
            nbt.putString(PARENT_TASK_ID_KEY, parentTaskId);
        }
        nbt.putLong(SUBTASK_SORT_ORDER_KEY, subtaskSortOrder);

        // 新格式不再持久化旧的递归 subtasks 结构，仅保留空列表兼容旧读取方。
        ListTag subtasksList = new ListTag();
        nbt.put("subtasks", subtasksList);

        return nbt;
    }

    /**
     * 从 NBT 读取单个平铺任务。
     *
     * <p>该方法刻意不读取旧格式的 {@code subtasks} 递归列表：旧数据必须由
     * {@link TaskCompatibilityAdapter#readTaskNbtList(ListTag)} 使用显式栈展开，
     * 以免深层嵌套任务导致递归栈溢出。读取过程对损坏字段做安全降级，
     * 保证单个坏字段不会让整份存档无法加载。</p>
     *
     * @param nbt 任务 NBT
     * @return 还原后的任务对象
     */
    public static Task fromNbt(CompoundTag nbt) {
        CompoundTag safeNbt = nbt == null ? new CompoundTag() : nbt;
        String title = safeNbt.getString("title");
        String description = safeNbt.getString("description");
        Task task = new Task(title, description);

        String storedId = normalizeOptionalText(safeNbt.getString("id"));
        if (storedId != null) {
            task.id = storedId;
        }

        task.completed = safeNbt.getBoolean("completed");

        String priorityStr = safeNbt.getString("priority");
        task.priority = parsePriority(priorityStr);

        long createdAt = safeNbt.getLong("createdAt");
        task.createdAt = createdAt > 0L ? createdAt : System.currentTimeMillis();

        // Tags
        if (safeNbt.contains("tags", NBT_LIST_TYPE)) {
            ListTag tagsList = safeNbt.getList("tags", NBT_COMPOUND_TYPE);
            for (int i = 0; i < tagsList.size(); i++) {
                CompoundTag tagNbt = tagsList.getCompound(i);
                String tag = normalizeOptionalText(tagNbt.getString("tag"));
                if (tag != null) {
                    task.tags.add(tag);
                }
            }
        }

        // Due date
        if (safeNbt.contains("dueDate")) {
            task.dueDate = safeNbt.getLong("dueDate");
        }

        String scopeName = safeNbt.getString("scope");
        try {
            task.scope = scopeName == null || scopeName.isBlank()
                    ? Scope.PERSONAL
                    : Scope.valueOf(scopeName);
        } catch (IllegalArgumentException e) {
            task.scope = Scope.PERSONAL;
        }
        task.creatorUuid = normalizeOptionalText(safeNbt.getString("creatorUuid"));
        task.assigneeUuid = normalizeOptionalText(safeNbt.getString("assigneeUuid"));
        task.assigneeName = normalizeOptionalText(safeNbt.getString("assigneeName"));
        task.projectId = normalizeOptionalText(safeNbt.getString("projectId"));
        task.parentTaskId = normalizeOptionalText(safeNbt.getString(PARENT_TASK_ID_KEY));
        if (safeNbt.contains(SUBTASK_SORT_ORDER_KEY)) {
            task.subtaskSortOrder = Math.max(0L, safeNbt.getLong(SUBTASK_SORT_ORDER_KEY));
        }

        // 旧版递归 subtasks 由 TaskCompatibilityAdapter 负责展开；这里保持空列表。
        return task;
    }

    private static Priority parsePriority(String priorityStr) {
        if (priorityStr != null && !priorityStr.isBlank()) {
            try {
                return Priority.valueOf(priorityStr);
            } catch (IllegalArgumentException ignored) {
                // 损坏的优先级按中优先级处理，避免整份任务数据加载失败。
            }
        }
        return Priority.MEDIUM;
    }

    /**
     * 将可选字符串标准化为 null 或有效值，避免空串被当作有效关联。
     *
     * @param value 原始字符串
     * @return 标准化后的可选值
     */
    private static String normalizeOptionalText(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title == null ? "" : title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? "" : description; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority == null ? Priority.MEDIUM : priority; }
    public Set<String> getTags() { return new LinkedHashSet<>(tags == null ? Set.of() : tags); }
    public void setTags(Iterable<String> tags) {
        this.tags.clear();
        for (String tag : tags) {
            if (tag != null && !tag.isEmpty()) {
                this.tags.add(tag);
            }
        }
    }
    public void clearTags() { this.tags.clear(); }
    public void addTag(String tag) { this.tags.add(tag); }
    public void removeTag(String tag) { this.tags.remove(tag); }
    public long getCreatedAt() { return createdAt; }
    public Long getDueDate() { return dueDate; }
    public void setDueDate(Long dueDate) { this.dueDate = dueDate; }
    public List<Task> getSubtasks() { return new ArrayList<>(subtasks); }
    public void addSubtask(Task subtask) { this.subtasks.add(subtask); }
    public void removeSubtask(Task subtask) { this.subtasks.remove(subtask); }
    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }
    public String getCreatorUuid() { return creatorUuid; }
    public void setCreatorUuid(String creatorUuid) { this.creatorUuid = creatorUuid; }
    public String getAssigneeUuid() { return assigneeUuid; }
    public void setAssigneeUuid(String assigneeUuid) { this.assigneeUuid = assigneeUuid; }
    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    /**
     * 返回当前子任务的父任务 ID。
     *
     * @return 父任务 ID，顶层任务时返回 null
     */
    public String getParentTaskId() { return parentTaskId; }

    /**
     * 设置父任务 ID，并将空串标准化为 null。
     *
     * @param parentTaskId 父任务 ID
     */
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = normalizeOptionalText(parentTaskId); }

    /**
     * 返回子任务在父任务内的排序号。
     *
     * @return 子任务排序号
     */
    public long getSubtaskSortOrder() { return subtaskSortOrder; }

    /**
     * 设置子任务排序号。
     *
     * @param subtaskSortOrder 子任务排序号
     */
    public void setSubtaskSortOrder(long subtaskSortOrder) { this.subtaskSortOrder = subtaskSortOrder; }

    /**
     * 判断当前任务是否为子任务。
     *
     * @return 存在父任务 ID 时返回 true
     */
    public boolean isSubtask() { return parentTaskId != null && !parentTaskId.isEmpty(); }

    /**
     * 判断当前任务是否为顶层任务。
     *
     * @return 不存在父任务 ID 时返回 true
     */
    public boolean isTopLevelTask() { return !isSubtask(); }

    public boolean isProjectUnassigned() { return projectId == null || projectId.isEmpty(); }
    public boolean belongsToProject(String targetProjectId) {
        return targetProjectId != null && !targetProjectId.isEmpty() && targetProjectId.equals(projectId);
    }
    public boolean clearProjectBindingIfMatches(String targetProjectId) {
        if (!belongsToProject(targetProjectId)) {
            return false;
        }
        this.projectId = null;
        return true;
    }

    /**
     * Priority levels for tasks
     */
    public enum Priority {
        LOW("gui.todolist.priority.low", 0xFF55FF55),
        MEDIUM("gui.todolist.priority.medium", 0xFFFFFF00),
        HIGH("gui.todolist.priority.high", 0xFFFF5555);

        private final String translationKey;
        private final int color;

        Priority(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }

        public Component getDisplayName() { return Component.translatable(translationKey); }
        public int getColor() { return color; }
    }

    public enum Scope {
        PERSONAL,
        TEAM
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(id, task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", completed=" + completed +
                ", priority=" + priority +
                '}';
    }
}

