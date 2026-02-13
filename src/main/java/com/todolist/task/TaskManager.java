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
     * Get all tasks
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
    public List<Task> getCompletedTasks() {
        return tasks.values().stream()
                .filter(Task::isCompleted)
                .collect(Collectors.toList());
    }

    /**
     * Get incomplete tasks
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
        return tasks.values().stream()
                .filter(t -> t.getPriority() == priority)
                .collect(Collectors.toList());
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

    public void addListener(TaskChangeListener listener) {
        listeners.add(listener);
    }

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

    public interface TaskChangeListener {
        void onTaskChanged(TaskChangeType type, Task task);
    }
}
