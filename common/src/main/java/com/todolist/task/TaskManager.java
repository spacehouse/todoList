package com.todolist.task;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 负责维护任务集合，并提供任务的基础增删改查与排序能力。
 */
public class TaskManager {
    private final Map<String, Task> tasks;

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
        return task;
    }

    /**
     * 添加一个已存在的任务对象。
     *
     * @param task 待添加的任务
     */
    public void addTask(Task task) {
        tasks.put(task.getId(), task);
    }

    /**
     * 按任务 ID 获取任务。
     *
     * @param id 任务 ID
     * @return 对应任务，不存在时返回 {@code null}
     */
    public Task getTask(String id) {
        return tasks.get(id);
    }

    /**
     * 按当前稳定顺序返回全部任务。
     *
     * @return 当前顺序下的全部任务列表
     */
    public List<Task> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * 按任务 ID 删除任务。
     *
     * @param taskId 任务 ID
     */
    public void deleteTask(String taskId) {
        tasks.remove(taskId);
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
        return removedCount;
    }

    /**
     * 切换指定任务的完成状态。
     *
     * @param taskId 任务 ID
     */
    public void toggleTaskCompletion(String taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
            task.setCompleted(!task.isCompleted());
        }
    }

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
     * 按当前稳定顺序返回未完成任务。
     *
     * @return 未完成任务列表
     */
    public List<Task> getIncompleteTasks() {
        return tasks.values().stream()
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
        return true;
    }

    /**
     * 清空当前管理器中的全部任务。
     */
    public void clearAll() {
        tasks.clear();
    }
}
