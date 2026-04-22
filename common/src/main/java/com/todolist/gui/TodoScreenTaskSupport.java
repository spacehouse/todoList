package com.todolist.gui;

import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/**
 * TodoScreen 任务支持类，集中处理任务筛选、分组标题和优先级重排等纯集合逻辑。
 */
final class TodoScreenTaskSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenTaskSupport() {
    }

    /**
     * 对任务列表应用优先级筛选。
     *
     * @param currentPriorityFilter 当前优先级筛选值
     * @param source 原始任务列表
     * @return 筛选后的任务列表
     */
    static List<Task> applyPriorityFilterToTasks(int currentPriorityFilter, List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        if (currentPriorityFilter == 0) {
            return new ArrayList<>(input);
        }
        Task.Priority targetPriority = Task.Priority.MEDIUM;
        if (currentPriorityFilter == 1) {
            targetPriority = Task.Priority.HIGH;
        } else if (currentPriorityFilter == 3) {
            targetPriority = Task.Priority.LOW;
        }
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task != null && task.getPriority() == targetPriority) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 对任务列表应用搜索关键字筛选。
     *
     * @param searchQuery 搜索关键词
     * @param source 原始任务列表
     * @return 筛选后的任务列表
     */
    static List<Task> applySearchQueryToTasks(String searchQuery, List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new ArrayList<>(input);
        }
        String query = searchQuery.toLowerCase();
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task == null) {
                continue;
            }
            String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase();
            String desc = task.getDescription() == null ? "" : task.getDescription().toLowerCase();
            boolean matchText = title.contains(query) || desc.contains(query);
            boolean matchTag = false;
            for (String tag : task.getTags()) {
                if (tag != null && tag.toLowerCase().contains(query)) {
                    matchTag = true;
                    break;
                }
            }
            if (matchText || matchTag) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 按当前项目和团队视图筛选任务归属范围。
     *
     * @param source 原始任务列表
     * @param currentProjectId 当前项目 ID
     * @param viewModeName 当前视图模式名称
     * @param currentPlayerUuid 当前玩家 UUID
     * @return 筛选后的任务列表
     */
    static List<Task> applyAssignedFilterForView(List<Task> source,
                                                 String currentProjectId,
                                                 String viewModeName,
                                                 String currentPlayerUuid) {
        if (currentProjectId == null || currentProjectId.isEmpty()) {
            return new ArrayList<>();
        }
        List<Task> input = source == null ? List.of() : source;
        List<Task> projectFiltered = new ArrayList<>();
        for (Task task : input) {
            if (task != null && task.belongsToProject(currentProjectId)) {
                projectFiltered.add(task);
            }
        }

        if ("TEAM_ASSIGNED".equals(viewModeName)) {
            if (currentPlayerUuid == null || currentPlayerUuid.isEmpty()) {
                return new ArrayList<>();
            }
            List<Task> assigned = new ArrayList<>();
            for (Task task : projectFiltered) {
                if (currentPlayerUuid.equals(task.getAssigneeUuid())) {
                    assigned.add(task);
                }
            }
            return assigned;
        }

        if ("TEAM_UNASSIGNED".equals(viewModeName)) {
            List<Task> unassigned = new ArrayList<>();
            for (Task task : projectFiltered) {
                String assignee = task.getAssigneeUuid();
                if (assignee == null || assignee.isEmpty()) {
                    unassigned.add(task);
                }
            }
            return unassigned;
        }

        return projectFiltered;
    }

    /**
     * 提取未完成任务，自动忽略空值项。
     *
     * @param source 原始任务列表
     * @return 未完成任务列表
     */
    static List<Task> extractIncompleteTasks(List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task != null && !task.isCompleted()) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 将任务列表映射为非空任务 ID 列表。
     *
     * @param source 原始任务列表
     * @return 任务 ID 列表
     */
    static List<String> toNonNullTaskIds(List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        List<String> ids = new ArrayList<>();
        for (Task task : input) {
            if (task != null && task.getId() != null) {
                ids.add(task.getId());
            }
        }
        return ids;
    }

    /**
     * 提取当前可见的未完成任务 ID 顺序。
     *
     * @param source 原始任务列表
     * @return 可见未完成任务 ID 列表
     */
    static List<String> getVisibleIncompleteTaskIds(List<Task> source) {
        return toNonNullTaskIds(extractIncompleteTasks(source));
    }

    /**
     * 生成任务分组标题文本并附带数量标记。
     *
     * @param translationKey 标题翻译键
     * @param count 当前分组任务数量
     * @return 格式化后的标题文本
     */
    static String formatTaskSectionTitle(String translationKey, int count) {
        return resolveTaskSectionLabel(translationKey) + "（" + count + "）";
    }

    /**
     * 解析任务分组标题的显示文案，并为离线测试环境提供兜底标签。
     *
     * @param translationKey 标题翻译键
     * @return 可显示的分组标题文本
     */
    static String resolveTaskSectionLabel(String translationKey) {
        String label = Component.translatable(translationKey).getString();
        if (!Objects.equals(label, translationKey)) {
            return label;
        }
        if ("gui.todolist.active".equals(translationKey)) {
            return "未完成";
        }
        if ("gui.todolist.completed".equals(translationKey)) {
            return "已完成";
        }
        return translationKey;
    }

    /**
     * 将任务追加到对应优先级分桶中。
     *
     * @param task 目标任务
     * @param highTasks 高优先级任务分桶
     * @param mediumTasks 中优先级任务分桶
     * @param lowTasks 低优先级任务分桶
     */
    static void appendTaskToPriorityBucket(Task task,
                                           List<Task> highTasks,
                                           List<Task> mediumTasks,
                                           List<Task> lowTasks) {
        if (task == null) {
            return;
        }
        if (task.getPriority() == Task.Priority.HIGH) {
            highTasks.add(task);
            return;
        }
        if (task.getPriority() == Task.Priority.LOW) {
            lowTasks.add(task);
            return;
        }
        mediumTasks.add(task);
    }

    /**
     * 按当前顺序将任务 ID 写入目标列表。
     *
     * @param tasks 源任务列表
     * @param targetIds 目标 ID 列表
     */
    static void appendTaskIds(List<Task> tasks, List<String> targetIds) {
        if (tasks == null || targetIds == null) {
            return;
        }
        for (Task task : tasks) {
            if (task != null && task.getId() != null) {
                targetIds.add(task.getId());
            }
        }
    }

    /**
     * 在当前视图内按照优先级重新归位任务，同时保留同优先级桶内原始顺序。
     *
     * @param scopedActiveTasks 当前视图可排序的未完成任务
     * @param updatedTask 刚更新优先级的任务
     * @return 归位后的任务 ID 顺序；若无法归位则返回空列表
     */
    static List<String> reorderCurrentViewActiveTaskIdsAfterPriorityChange(List<Task> scopedActiveTasks, Task updatedTask) {
        if (updatedTask == null || updatedTask.getId() == null || scopedActiveTasks == null || scopedActiveTasks.size() < 2) {
            return List.of();
        }
        List<Task> highTasks = new ArrayList<>();
        List<Task> mediumTasks = new ArrayList<>();
        List<Task> lowTasks = new ArrayList<>();
        boolean taskIncluded = false;

        for (Task task : scopedActiveTasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            if (updatedTask.getId().equals(task.getId())) {
                taskIncluded = true;
                continue;
            }
            appendTaskToPriorityBucket(task, highTasks, mediumTasks, lowTasks);
        }
        if (!taskIncluded) {
            return List.of();
        }

        appendTaskToPriorityBucket(updatedTask, highTasks, mediumTasks, lowTasks);
        List<String> orderedTaskIds = new ArrayList<>(highTasks.size() + mediumTasks.size() + lowTasks.size());
        appendTaskIds(highTasks, orderedTaskIds);
        appendTaskIds(mediumTasks, orderedTaskIds);
        appendTaskIds(lowTasks, orderedTaskIds);
        return orderedTaskIds;
    }

    static List<Task> buildCompletedTasksForCurrentView(TaskManager taskManager,
                                                        String currentProjectId,
                                                        int currentPriorityFilter,
                                                        String viewModeName,
                                                        String currentPlayerUuid,
                                                        String searchQuery) {
        if (taskManager == null || currentProjectId == null || currentProjectId.isEmpty()) {
            return List.of();
        }
        List<Task> completedTasks = taskManager.getCompletedTasks();
        List<Task> priorityFiltered = applyPriorityFilterToTasks(currentPriorityFilter, completedTasks);
        List<Task> scopedTasks = applyAssignedFilterForView(
                priorityFiltered,
                currentProjectId,
                viewModeName,
                currentPlayerUuid
        );
        return applySearchQueryToTasks(searchQuery, scopedTasks);
    }

    static List<TaskListWidget.SectionModel> buildTaskPaneSections(List<Task> activeTasks,
                                                                   List<Task> completedTasks,
                                                                   boolean activeExpanded,
                                                                   boolean completedExpanded) {
        List<Task> safeActiveTasks = activeTasks == null ? List.of() : List.copyOf(activeTasks);
        List<Task> safeCompletedTasks = completedTasks == null ? List.of() : List.copyOf(completedTasks);
        return List.of(
                new TaskListWidget.SectionModel(
                        "active",
                        formatTaskSectionTitle("gui.todolist.active", safeActiveTasks.size()),
                        safeActiveTasks,
                        true,
                        activeExpanded
                ),
                new TaskListWidget.SectionModel(
                        "completed",
                        formatTaskSectionTitle("gui.todolist.completed", safeCompletedTasks.size()),
                        safeCompletedTasks,
                        true,
                        completedExpanded
                )
        );
    }
}
