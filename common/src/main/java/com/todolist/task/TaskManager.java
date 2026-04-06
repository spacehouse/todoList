package com.todolist.task;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Manages task lists and provides CRUD operations
 *
 * Thread-safe task management for both client and server side
 */
public class TaskManager {
    private final Map<String, Task> tasks;
    private final List<TaskChangeListener> listeners;

    public TaskManager() {
        this.tasks = new LinkedHashMap<>();
        this.listeners = new ArrayList<>();
    }

    // CRUD Operations

    /**
     * Add a new task
     */
    public Task addTask(String title, String description) {
        Task task = new Task(title, description);
        tasks.put(task.getId(), task);
        notifyListeners(TaskChangeType.ADDED, task);
        return task;
    }

    /**
     * Add an existing task
     */
    public void addTask(Task task) {
        tasks.put(task.getId(), task);
        notifyListeners(TaskChangeType.ADDED, task);
    }

    /**
     * Get task by ID
     */
    public Task getTask(String id) {
        return tasks.get(id);
    }

    /**
     * 获取所有任务并按优先级从高到低排序返回。
     */
    /**
     * 按当前稳定顺序返回全部任务。
     *
     * @return 当前顺序下的全部任务列表
     */
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Update task
     */
    public void updateTask(Task task) {
        if (tasks.containsKey(task.getId())) {
            tasks.put(task.getId(), task);
            notifyListeners(TaskChangeType.UPDATED, task);
        }
    }

    /**
     * Delete task
     */
    public void deleteTask(String taskId) {
        Task removed = tasks.remove(taskId);
        if (removed != null) {
            notifyListeners(TaskChangeType.REMOVED, removed);
        }
    }

    /**
     * Delete all tasks in the specified project
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
        if (removedCount > 0) {
            notifyListeners(TaskChangeType.BATCH_UPDATED, null);
        }
        return removedCount;
    }

    /**
     * Toggle task completion status
     */
    public void toggleTaskCompletion(String taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
            task.setCompleted(!task.isCompleted());
            notifyListeners(TaskChangeType.UPDATED, task);
        }
    }

    // Filtering and Search

    /**
     * Get completed tasks
     */
    /**
     * 按当前稳定顺序返回已完成任务。
     *
     * @return 已完成任务列表
     */
    public List<Task> getCompletedTasks() {
        return tasks.values().stream()
                .filter(Task::isCompleted)
                .collect(Collectors.toList());
    }

    /**
     * Get incomplete tasks
     */
    /**
     * 按当前稳定顺序返回未完成任务。
     *
     * @return 未完成任务列表
     */
    public List<Task> getIncompleteTasks() {
        return tasks.values().stream()
                .filter(t -> !t.isCompleted())
                .collect(Collectors.toList());
    }

    /**
     * Get tasks by priority
     */
    public List<Task> getTasksByPriority(Task.Priority priority) {
        List<Task> list = tasks.values().stream()
                .filter(t -> t.getPriority() == priority)
                .collect(Collectors.toList());
        list.sort((a, b) -> {
            if (a.isCompleted() && !b.isCompleted()) return 1;
            if (!a.isCompleted() && b.isCompleted()) return -1;
            return 0;
        });
        return list;
    }

    /**
     * Get tasks by project ID
     */
    /**
     * 按当前稳定顺序返回指定项目下的任务。
     *
     * @param projectId 项目 ID
     * @return 指定项目下的任务列表
     */
    public List<Task> getTasksByProject(String projectId) {
        return tasks.values().stream()
                .filter(t -> Objects.equals(t.getProjectId(), projectId))
                .collect(Collectors.toList());
    }

    /**
     * 仅重排给定任务子集在整体列表中的相对顺序，同时保持其他任务原位不变。
     *
     * @param orderedTaskIds 目标顺序下的任务 ID 列表
     * @return true 表示任务顺序发生变化
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
        notifyListeners(TaskChangeType.BATCH_UPDATED, null);
        return true;
    }

    /**
     * Get tasks with specific tag
     */
    public List<Task> getTasksByTag(String tag) {
        return tasks.values().stream()
                .filter(t -> t.getTags().contains(tag))
                .collect(Collectors.toList());
    }

    /**
     * Search tasks by title or description
     */
    public List<Task> searchTasks(String query) {
        String lowerQuery = query.toLowerCase();
        return tasks.values().stream()
                .filter(t -> t.getTitle().toLowerCase().contains(lowerQuery) ||
                        t.getDescription().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    /**
     * Filter tasks by predicate
     */
    public List<Task> filterTasks(Predicate<Task> predicate) {
        return tasks.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    // Statistics

    /**
     * Get total task count
     */
    public int getTaskCount() {
        return tasks.size();
    }

    /**
     * Get completed task count
     */
    public int getCompletedCount() {
        return (int) tasks.values().stream()
                .filter(Task::isCompleted)
                .count();
    }

    /**
     * Get incomplete task count
     */
    public int getIncompleteCount() {
        return tasks.size() - getCompletedCount();
    }

    // Batch Operations

    /**
     * Add multiple tasks
     */
    public void addTasks(List<Task> newTasks) {
        for (Task task : newTasks) {
            tasks.put(task.getId(), task);
        }
        notifyListeners(TaskChangeType.BATCH_UPDATED, null);
    }

    /**
     * Clear all tasks
     */
    public void clearAll() {
        tasks.clear();
        notifyListeners(TaskChangeType.CLEARED, null);
    }

    /**
     * Mark all tasks as completed
     */
    public void markAllCompleted() {
        tasks.values().forEach(t -> t.setCompleted(true));
        notifyListeners(TaskChangeType.BATCH_UPDATED, null);
    }

    /**
     * Mark all tasks as incomplete
     */
    public void markAllIncomplete() {
        tasks.values().forEach(t -> t.setCompleted(false));
        notifyListeners(TaskChangeType.BATCH_UPDATED, null);
    }

    // Listener Management

    /**
     * 添加任务变更监听器。
     */
    public void addListener(TaskChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除任务变更监听器。
     */
    public void removeListener(TaskChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(TaskChangeType type, Task task) {
        for (TaskChangeListener listener : listeners) {
            listener.onTaskChanged(type, task);
        }
    }

    // Inner classes and interfaces

    public enum TaskChangeType {
        ADDED,
        UPDATED,
        REMOVED,
        BATCH_UPDATED,
        CLEARED
    }

    /**
     * 监听任务列表的增删改清事件。
     */
    public interface TaskChangeListener {
        /**
         * 在任务发生变更时回调。
         */
        void onTaskChanged(TaskChangeType type, Task task);
    }
}


