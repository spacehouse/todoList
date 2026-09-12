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
    private TaskTrigger trigger; // 游戏内事件触发完成条件，可为空

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
        this.trigger = null;
    }

    /**
     * 将任务写入 NBT。
     * Phase 1 开始统一按平铺结构写出，只保留兼容读取旧 subtasks 的能力。
     *
     * @return 当前任务的 NBT 表示
     */
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", id);
        nbt.putString("title", title);
        nbt.putString("description", description);
        nbt.putBoolean("completed", completed);
        nbt.putString("priority", priority.name());
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
        if (trigger != null) {
            nbt.put("trigger", trigger.toNbt());
        }

        // 新格式不再持久化旧的递归 subtasks 结构，仅保留空列表兼容旧读取方。
        ListTag subtasksList = new ListTag();
        nbt.put("subtasks", subtasksList);

        return nbt;
    }

    /**
     * 从 NBT 读取任务。
     * 同时兼容旧的递归 subtasks 与新的平铺子任务字段。
     *
     * @param nbt 任务 NBT
     * @return 还原后的任务对象
     */
    public static Task fromNbt(CompoundTag nbt) {
        String title = nbt.getString("title");
        String description = nbt.getString("description");
        Task task = new Task(title, description);

        // Load ID
        if (nbt.contains("id")) {
            task.id = nbt.getString("id"); // Note: would need to make id non-final or use reflection
        }

        task.completed = nbt.getBoolean("completed");

        // Priority
        String priorityStr = nbt.getString("priority");
        task.priority = Priority.valueOf(priorityStr);

        task.createdAt = nbt.getLong("createdAt");

        // Tags
        if (nbt.contains("tags", NBT_LIST_TYPE)) {
            ListTag tagsList = nbt.getList("tags", NBT_COMPOUND_TYPE);
            for (int i = 0; i < tagsList.size(); i++) {
                CompoundTag tagNbt = tagsList.getCompound(i);
                task.tags.add(tagNbt.getString("tag"));
            }
        }

        // Due date
        if (nbt.contains("dueDate")) {
            task.dueDate = nbt.getLong("dueDate");
        }

        if (nbt.contains("scope")) {
            try {
                task.scope = Scope.valueOf(nbt.getString("scope"));
            } catch (IllegalArgumentException e) {
                task.scope = Scope.PERSONAL;
            }
        } else {
            task.scope = Scope.PERSONAL;
        }
        if (nbt.contains("creatorUuid")) {
            task.creatorUuid = nbt.getString("creatorUuid");
        }
        if (nbt.contains("assigneeUuid")) {
            task.assigneeUuid = nbt.getString("assigneeUuid");
        }
        if (nbt.contains("assigneeName")) {
            task.assigneeName = nbt.getString("assigneeName");
        }
        if (nbt.contains("projectId")) {
            task.projectId = nbt.getString("projectId");
        }
        if (nbt.contains(PARENT_TASK_ID_KEY)) {
            task.parentTaskId = normalizeOptionalText(nbt.getString(PARENT_TASK_ID_KEY));
        }
        if (nbt.contains(SUBTASK_SORT_ORDER_KEY)) {
            task.subtaskSortOrder = nbt.getLong(SUBTASK_SORT_ORDER_KEY);
        }
        if (nbt.contains("trigger", NBT_COMPOUND_TYPE)) {
            task.trigger = TaskTrigger.fromNbt(nbt.getCompound("trigger"));
            if (!task.trigger.isValid()) {
                task.trigger = null;
            }
        }

        // Subtasks
        if (nbt.contains("subtasks", NBT_LIST_TYPE)) {
            ListTag subtasksList = nbt.getList("subtasks", NBT_COMPOUND_TYPE);
            for (int i = 0; i < subtasksList.size(); i++) {
                task.subtasks.add(Task.fromNbt(subtasksList.getCompound(i)));
            }
        }

        return task;
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
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Set<String> getTags() { return new LinkedHashSet<>(tags); }
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
     * 返回任务的事件触发条件。
     *
     * @return 触发器，未设置时返回 null
     */
    public TaskTrigger getTrigger() { return trigger; }

    /**
     * 设置任务的事件触发条件，null 表示清除触发器。
     *
     * @param trigger 触发器
     */
    public void setTrigger(TaskTrigger trigger) { this.trigger = trigger; }

    /**
     * 判断任务是否配置了有效的事件触发条件。
     *
     * @return 存在有效触发器时返回 true
     */
    public boolean hasTrigger() { return trigger != null && trigger.isValid(); }

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

