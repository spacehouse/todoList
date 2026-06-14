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
    }

    /**
     * 基于现有任务创建一个完整副本，避免界面缓存与后台保存共享同一对象。
     *
     * @param source 原始任务
     */
    private Task(Task source) {
        this.id = source.id;
        this.title = source.title;
        this.description = source.description;
        this.completed = source.completed;
        this.priority = source.priority;
        this.tags = new LinkedHashSet<>(source.tags);
        this.createdAt = source.createdAt;
        this.dueDate = source.dueDate;
        this.subtasks = new ArrayList<>();
        for (Task subtask : source.subtasks) {
            this.subtasks.add(subtask == null ? null : new Task(subtask));
        }
        this.scope = source.scope;
        this.creatorUuid = source.creatorUuid;
        this.assigneeUuid = source.assigneeUuid;
        this.assigneeName = source.assigneeName;
        this.projectId = source.projectId;
    }

    // NBT Serialization
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

        // Subtasks
        ListTag subtasksList = new ListTag();
        for (Task subtask : subtasks) {
            subtasksList.add(subtask.toNbt());
        }
        nbt.put("subtasks", subtasksList);

        return nbt;
    }

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
     * 创建当前任务的完整副本，供缓存或后台保存使用。
     *
     * @return 当前任务的深拷贝
     */
    public Task copy() {
        return new Task(this);
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

