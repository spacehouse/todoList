package com.todolist.gui;

import com.todolist.task.Task;
import com.todolist.task.TaskAssignmentSupport;
import com.todolist.task.TaskManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        return applySearchQueryToTasks(searchQuery, input, input);
    }

    /**
     * 对顶层任务列表应用搜索关键字筛选；当子任务命中时，返回其父任务以保留上下文。
     *
     * @param searchQuery 搜索关键词
     * @param source 顶层任务列表，决定结果顺序
     * @param searchableTasks 参与搜索匹配的任务范围，可包含子任务
     * @return 筛选后的顶层任务列表
     */
    static List<Task> applySearchQueryToTasks(String searchQuery, List<Task> source, List<Task> searchableTasks) {
        List<Task> input = source == null ? List.of() : source;
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new ArrayList<>(input);
        }
        String query = searchQuery.toLowerCase();
        List<Task> scope = searchableTasks == null ? input : searchableTasks;
        List<String> matchedTopLevelIds = new ArrayList<>();
        for (Task task : scope) {
            if (!matchesSearchQuery(task, query)) {
                continue;
            }
            String matchedTopLevelId = task.isSubtask() ? task.getParentTaskId() : task.getId();
            if (matchedTopLevelId != null && !matchedTopLevelId.isEmpty() && !matchedTopLevelIds.contains(matchedTopLevelId)) {
                matchedTopLevelIds.add(matchedTopLevelId);
            }
        }
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task != null && task.getId() != null && matchedTopLevelIds.contains(task.getId())) {
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
        return applyAssignedFilterForView(source, source, currentProjectId, viewModeName, currentPlayerUuid);
    }

    /**
     * 对任务列表应用团队视图的指派筛选，并允许显式传入父任务聚合判定所需的完整作用域。
     *
     * @param source 需要被过滤的任务列表
     * @param assignmentScope 用于判定父任务直属子任务指派状态的完整任务范围
     * @param currentProjectId 当前项目 ID
     * @param viewModeName 当前视图模式名称
     * @param currentPlayerUuid 当前玩家 UUID
     * @return 筛选后的任务列表
     */
    static List<Task> applyAssignedFilterForView(List<Task> source,
                                                 List<Task> assignmentScope,
                                                 String currentProjectId,
                                                 String viewModeName,
                                                 String currentPlayerUuid) {
        if (currentProjectId == null || currentProjectId.isEmpty()) {
            return new ArrayList<>();
        }
        List<Task> input = source == null ? List.of() : source;
        List<Task> assignmentInput = assignmentScope == null ? input : assignmentScope;
        List<Task> projectFiltered = new ArrayList<>();
        for (Task task : input) {
            if (task != null && task.belongsToProject(currentProjectId)) {
                projectFiltered.add(task);
            }
        }
        List<Task> projectAssignmentScope = new ArrayList<>();
        for (Task task : assignmentInput) {
            if (task != null && task.belongsToProject(currentProjectId)) {
                projectAssignmentScope.add(task);
            }
        }

        if ("TEAM_ASSIGNED".equals(viewModeName)) {
            if (currentPlayerUuid == null || currentPlayerUuid.isEmpty()) {
                return new ArrayList<>();
            }
            List<Task> assigned = new ArrayList<>();
            for (Task task : projectFiltered) {
                if (TaskAssignmentSupport.isVisibleInTeamAssigned(task, projectAssignmentScope, currentPlayerUuid)) {
                    assigned.add(task);
                }
            }
            return assigned;
        }

        if ("TEAM_UNASSIGNED".equals(viewModeName)) {
            List<Task> unassigned = new ArrayList<>();
            for (Task task : projectFiltered) {
                if (TaskAssignmentSupport.isVisibleInTeamUnassigned(task, projectAssignmentScope)) {
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
        return resolveTaskSectionLabel(translationKey) + " (" + count + ")";
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
        return buildVisibleTasksForCurrentView(
                taskManager,
                currentProjectId,
                true,
                currentPriorityFilter,
                viewModeName,
                currentPlayerUuid,
                searchQuery
        );
    }

    /**
     * 构建当前项目视图下用于列表层级展示的任务范围。
     * 该范围会保留当前视图可见的顶层任务，并补齐其直属子任务。
     *
     * @param visibleTopLevelTasks 当前视图可见的顶层任务
     * @param scopedProjectTasks 当前项目内的任务范围
     * @return 供列表层级展示使用的任务快照
     */
    static List<Task> buildSectionTasksWithDirectChildren(List<Task> visibleTopLevelTasks, List<Task> scopedProjectTasks) {
        List<Task> visibleParents = visibleTopLevelTasks == null ? List.of() : List.copyOf(visibleTopLevelTasks);
        List<Task> scopedTasks = scopedProjectTasks == null ? List.of() : List.copyOf(scopedProjectTasks);
        List<Task> result = new ArrayList<>(visibleParents);
        Set<String> visibleParentIds = new LinkedHashSet<>();
        Set<String> appendedTaskIds = new LinkedHashSet<>();
        for (Task task : visibleParents) {
            if (task == null || task.getId() == null || task.getId().isEmpty()) {
                continue;
            }
            visibleParentIds.add(task.getId());
            appendedTaskIds.add(task.getId());
        }
        for (Task task : scopedTasks) {
            if (task == null || !task.isSubtask() || task.getId() == null || task.getId().isEmpty()) {
                continue;
            }
            String parentTaskId = task.getParentTaskId();
            if (parentTaskId != null && visibleParentIds.contains(parentTaskId) && appendedTaskIds.add(task.getId())) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 收集搜索命中子任务后需要自动展开的父任务 ID。
     *
     * @param searchQuery 当前搜索词
     * @param visibleTopLevelTasks 当前视图可见的顶层任务
     * @param searchableTasks 当前搜索范围
     * @return 需要自动展开的父任务 ID 集合
     */
    static Set<String> collectAutoExpandedParentTaskIdsForSearch(String searchQuery,
                                                                 List<Task> visibleTopLevelTasks,
                                                                 List<Task> searchableTasks) {
        Set<String> parentTaskIds = new LinkedHashSet<>();
        if (searchQuery == null || searchQuery.isEmpty()) {
            return parentTaskIds;
        }
        String query = searchQuery.toLowerCase();
        Set<String> visibleParentIds = new LinkedHashSet<>();
        List<Task> visibleParents = visibleTopLevelTasks == null ? List.of() : visibleTopLevelTasks;
        List<Task> scope = searchableTasks == null ? List.of() : searchableTasks;
        for (Task task : visibleParents) {
            if (task != null && task.getId() != null && !task.getId().isEmpty()) {
                visibleParentIds.add(task.getId());
            }
        }
        for (Task task : scope) {
            if (task == null || !task.isSubtask() || !matchesSearchQuery(task, query)) {
                continue;
            }
            String parentTaskId = task.getParentTaskId();
            if (parentTaskId != null && visibleParentIds.contains(parentTaskId)) {
                parentTaskIds.add(parentTaskId);
            }
        }
        return parentTaskIds;
    }

    /**
     * 构建当前项目视图下可见的顶层任务；当搜索命中子任务时，返回其父任务以保留上下文。
     *
     * @param taskManager 任务管理器
     * @param currentProjectId 当前项目 ID
     * @param completed 是否构建已完成列表
     * @param currentPriorityFilter 当前优先级筛选
     * @param viewModeName 当前视图模式
     * @param currentPlayerUuid 当前玩家 UUID
     * @param searchQuery 搜索关键词
     * @return 当前视图下的顶层任务列表
     */
    static List<Task> buildVisibleTasksForCurrentView(TaskManager taskManager,
                                                      String currentProjectId,
                                                      boolean completed,
                                                      int currentPriorityFilter,
                                                      String viewModeName,
                                                      String currentPlayerUuid,
                                                      String searchQuery) {
        if (taskManager == null || currentProjectId == null || currentProjectId.isEmpty()) {
            return List.of();
        }
        List<Task> topLevelTasks = filterTasksByProjectAndCompletion(taskManager.getAllTasks(), currentProjectId, completed, true);
        List<Task> scopedTasks = filterTasksByProjectAndCompletion(taskManager.getAllTasks(), currentProjectId, completed, false);
        List<Task> topLevelScopedTasks = applyAssignedFilterForView(
                applyPriorityFilterToTasks(currentPriorityFilter, topLevelTasks),
                applyPriorityFilterToTasks(currentPriorityFilter, scopedTasks),
                currentProjectId,
                viewModeName,
                currentPlayerUuid
        );
        if (searchQuery == null || searchQuery.isEmpty()) {
            return topLevelScopedTasks;
        }
        List<Task> searchableScopedTasks = applyAssignedFilterForView(
                applyPriorityFilterToTasks(
                        currentPriorityFilter,
                        scopedTasks
                ),
                applyPriorityFilterToTasks(currentPriorityFilter, scopedTasks),
                currentProjectId,
                viewModeName,
                currentPlayerUuid
        );
        return applySearchQueryToTasks(searchQuery, topLevelTasks, searchableScopedTasks);
    }

    /**
     * 过滤指定项目和完成状态下的任务，并可选择仅保留顶层任务。
     *
     * @param source 原始任务列表
     * @param currentProjectId 当前项目 ID
     * @param completed 是否已完成
     * @param topLevelOnly 是否仅保留顶层任务
     * @return 过滤后的任务列表
     */
    private static List<Task> filterTasksByProjectAndCompletion(List<Task> source,
                                                                String currentProjectId,
                                                                boolean completed,
                                                                boolean topLevelOnly) {
        List<Task> result = new ArrayList<>();
        if (source == null || currentProjectId == null || currentProjectId.isEmpty()) {
            return result;
        }
        for (Task task : source) {
            if (task == null || task.isCompleted() != completed || !task.belongsToProject(currentProjectId)) {
                continue;
            }
            if (topLevelOnly && !task.isTopLevelTask()) {
                continue;
            }
            result.add(task);
        }
        return result;
    }

    /**
     * 从任务列表中过滤出顶层任务，并保持原顺序。
     *
     * @param source 原始任务列表
     * @return 顶层任务列表
     */
    private static List<Task> filterTopLevelTasks(List<Task> source) {
        List<Task> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Task task : source) {
            if (task != null && task.isTopLevelTask()) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 判断单个任务是否命中搜索关键词。
     *
     * @param task 待判断任务
     * @param query 已标准化为小写的搜索关键词
     * @return 命中时返回 true
     */
    private static boolean matchesSearchQuery(Task task, String query) {
        if (task == null) {
            return false;
        }
        String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase();
        String desc = task.getDescription() == null ? "" : task.getDescription().toLowerCase();
        boolean matchText = title.contains(query) || desc.contains(query);
        if (matchText) {
            return true;
        }
        for (String tag : task.getTags()) {
            if (tag != null && tag.toLowerCase().contains(query)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 收集当前项目下全部已完成任务的 ID，供批量清理操作复用。
     *
     * @param taskManager 任务管理器
     * @param currentProjectId 当前项目 ID
     * @return 已完成任务 ID 列表
     */
    static List<String> collectCompletedTaskIdsForProject(TaskManager taskManager, String currentProjectId) {
        if (taskManager == null || currentProjectId == null || currentProjectId.isEmpty()) {
            return List.of();
        }
        List<String> taskIds = new ArrayList<>();
        for (Task task : taskManager.getCompletedTasks()) {
            if (task != null && task.belongsToProject(currentProjectId) && task.getId() != null && !task.getId().isEmpty()) {
                taskIds.add(task.getId());
            }
        }
        return taskIds;
    }

    static List<TaskListWidget.SectionModel> buildTaskPaneSections(List<Task> activeTasks,
                                                                    List<Task> completedTasks,
                                                                    boolean activeExpanded,
                                                                    boolean completedExpanded) {
        List<Task> safeActiveTasks = activeTasks == null ? List.of() : List.copyOf(activeTasks);
        List<Task> safeCompletedTasks = completedTasks == null ? List.of() : List.copyOf(completedTasks);
        return buildTaskPaneSections(
                safeActiveTasks,
                safeActiveTasks.size(),
                safeCompletedTasks,
                safeCompletedTasks.size(),
                activeExpanded,
                completedExpanded
        );
    }

    /**
     * 构建任务面板分组模型，并允许标题计数使用 SQL 总数而非已加载行数。
     *
     * @param activeTasks 已加载的未完成任务
     * @param activeTotalCount 未完成任务总数
     * @param completedTasks 已加载的已完成任务
     * @param completedTotalCount 已完成任务总数
     * @param activeExpanded 未完成分组是否展开
     * @param completedExpanded 已完成分组是否展开
     * @return 任务列表需要渲染的分组模型集合
     */
    static List<TaskListWidget.SectionModel> buildTaskPaneSections(List<Task> activeTasks,
                                                                    int activeTotalCount,
                                                                    List<Task> completedTasks,
                                                                    int completedTotalCount,
                                                                    boolean activeExpanded,
                                                                    boolean completedExpanded) {
        List<Task> safeActiveTasks = activeTasks == null ? List.of() : List.copyOf(activeTasks);
        List<Task> safeCompletedTasks = completedTasks == null ? List.of() : List.copyOf(completedTasks);
        return List.of(
                new TaskListWidget.SectionModel(
                        "active",
                        formatTaskSectionTitle("gui.todolist.active", Math.max(activeTotalCount, safeActiveTasks.size())),
                        safeActiveTasks,
                        true,
                        activeExpanded
                ),
                new TaskListWidget.SectionModel(
                        "completed",
                        formatTaskSectionTitle("gui.todolist.completed", Math.max(completedTotalCount, safeCompletedTasks.size())),
                        safeCompletedTasks,
                        true,
                        completedExpanded
                )
        );
    }
}
