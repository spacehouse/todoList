package com.todolist.task;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.*;

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

    public Task(String title, String description) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.completed = false;
        this.priority = Priority.MEDIUM;
        this.tags = new HashSet<>();
        this.createdAt = System.currentTimeMillis();
        this.dueDate = null;
        this.subtasks = new ArrayList<>();
        this.scope = Scope.PERSONAL;
        this.creatorUuid = null;
        this.assigneeUuid = null;
    }

    // NBT Serialization
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", id);
        nbt.putString("title", title);
        nbt.putString("description", description);
        nbt.putBoolean("completed", completed);
        nbt.putString("priority", priority.name());
        nbt.putLong("createdAt", createdAt);

        // Tags
        NbtList tagsList = new NbtList();
        for (String tag : tags) {
            NbtCompound tagNbt = new NbtCompound();
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

        // Subtasks
        NbtList subtasksList = new NbtList();
        for (Task subtask : subtasks) {
            subtasksList.add(subtask.toNbt());
        }
        nbt.put("subtasks", subtasksList);

        return nbt;
    }

    public static Task fromNbt(NbtCompound nbt) {
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
        if (nbt.contains("tags", NbtElement.LIST_TYPE)) {
            NbtList tagsList = nbt.getList("tags", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < tagsList.size(); i++) {
                NbtCompound tagNbt = tagsList.getCompound(i);
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

        // Subtasks
        if (nbt.contains("subtasks", NbtElement.LIST_TYPE)) {
            NbtList subtasksList = nbt.getList("subtasks", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < subtasksList.size(); i++) {
                task.subtasks.add(Task.fromNbt(subtasksList.getCompound(i)));
            }
        }

        return task;
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
    public Set<String> getTags() { return new HashSet<>(tags); }
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

        public Text getDisplayName() { return Text.translatable(translationKey); }
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
