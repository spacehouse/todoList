package com.todolist.task;

import java.util.List;
import java.util.Objects;

/**
 * 任务指派聚合支持类，统一处理父任务对直属子任务的聚合指派判定。
 */
public final class TaskAssignmentSupport {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private TaskAssignmentSupport() {
    }

    /**
     * 直属子任务指派统计快照。
     */
    private static final class DirectSubtaskAssignmentState {
        private final int totalCount;
        private final int assignedCount;
        private final int assignedToPlayerCount;

        private DirectSubtaskAssignmentState(int totalCount, int assignedCount, int assignedToPlayerCount) {
            this.totalCount = totalCount;
            this.assignedCount = assignedCount;
            this.assignedToPlayerCount = assignedToPlayerCount;
        }
    }

    /**
     * 判断任务自身是否直接存在领取人。
     *
     * @param task 目标任务
     * @return 存在领取人时返回 true
     */
    public static boolean isDirectlyAssigned(Task task) {
        return task != null && task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
    }

    /**
     * 判断父任务是否拥有直属子任务。
     *
     * @param task 目标任务
     * @param allTasks 同一作用域内的任务集合
     * @return 存在直属子任务时返回 true
     */
    public static boolean hasDirectSubtasks(Task task, List<Task> allTasks) {
        return inspectDirectSubtasks(task, allTasks, null).totalCount > 0;
    }

    /**
     * 判断父任务的直属子任务是否已全部完成指派。
     *
     * @param task 目标任务
     * @param allTasks 同一作用域内的任务集合
     * @return 直属子任务全部已指派时返回 true
     */
    public static boolean areAllDirectSubtasksAssigned(Task task, List<Task> allTasks) {
        DirectSubtaskAssignmentState state = inspectDirectSubtasks(task, allTasks, null);
        return state.totalCount > 0 && state.assignedCount >= state.totalCount;
    }

    /**
     * 判断父任务的直属子任务中是否仍存在未指派项。
     *
     * @param task 目标任务
     * @param allTasks 同一作用域内的任务集合
     * @return 仍存在未指派直属子任务时返回 true
     */
    public static boolean hasAnyUnassignedDirectSubtask(Task task, List<Task> allTasks) {
        DirectSubtaskAssignmentState state = inspectDirectSubtasks(task, allTasks, null);
        return state.totalCount > 0 && state.assignedCount < state.totalCount;
    }

    /**
     * 判断父任务的直属子任务中是否存在分配给指定玩家的项。
     *
     * @param task 目标任务
     * @param allTasks 同一作用域内的任务集合
     * @param playerUuid 目标玩家 UUID
     * @return 存在分配给指定玩家的直属子任务时返回 true
     */
    public static boolean hasAnyDirectSubtaskAssignedToPlayer(Task task, List<Task> allTasks, String playerUuid) {
        DirectSubtaskAssignmentState state = inspectDirectSubtasks(task, allTasks, playerUuid);
        return state.totalCount > 0 && state.assignedToPlayerCount > 0;
    }

    /**
     * 判断任务在团队视图里是否应被视为“已指派”。
     * 对父任务来说，只要直属子任务全部已指派，就不应再作为待分配父任务处理。
     *
     * @param task 目标任务
     * @param allTasks 同一作用域内的任务集合
     * @return 聚合视角下已指派时返回 true
     */
    public static boolean isAggregatedAssigned(Task task, List<Task> allTasks) {
        if (hasDirectSubtasks(task, allTasks)) {
            return areAllDirectSubtasksAssigned(task, allTasks);
        }
        return isDirectlyAssigned(task);
    }

    /**
     * 判断任务在团队待分配视图中是否应继续显示。
     * 对父任务来说，只要仍有未指派直属子任务，就保留父任务作为上下文。
     *
     * @param task 目标任务
     * @param allTasks 同一作用域内的任务集合
     * @return 应显示在团队待分配视图时返回 true
     */
    public static boolean isVisibleInTeamUnassigned(Task task, List<Task> allTasks) {
        if (task == null) {
            return false;
        }
        if (hasDirectSubtasks(task, allTasks)) {
            return hasAnyUnassignedDirectSubtask(task, allTasks);
        }
        return !isDirectlyAssigned(task);
    }

    /**
     * 判断任务在团队“我的”视图中是否应显示。
     * 对父任务来说，只要直属子任务中存在分配给当前玩家的项，就保留父任务作为上下文。
     *
     * @param task 目标任务
     * @param allTasks 同一作用域内的任务集合
     * @param playerUuid 当前玩家 UUID
     * @return 应显示在团队“我的”视图时返回 true
     */
    public static boolean isVisibleInTeamAssigned(Task task, List<Task> allTasks, String playerUuid) {
        if (task == null || playerUuid == null || playerUuid.isEmpty()) {
            return false;
        }
        if (hasDirectSubtasks(task, allTasks)) {
            return hasAnyDirectSubtaskAssignedToPlayer(task, allTasks, playerUuid);
        }
        return Objects.equals(playerUuid, task.getAssigneeUuid());
    }

    /**
     * 统计父任务直属子任务的指派情况。
     *
     * @param task 父任务
     * @param allTasks 同一作用域内的任务集合
     * @param playerUuid 可选的目标玩家 UUID
     * @return 指派统计快照
     */
    private static DirectSubtaskAssignmentState inspectDirectSubtasks(Task task, List<Task> allTasks, String playerUuid) {
        if (task == null || task.isSubtask()) {
            return new DirectSubtaskAssignmentState(0, 0, 0);
        }
        String parentTaskId = task.getId();
        if (parentTaskId == null || parentTaskId.isEmpty() || allTasks == null || allTasks.isEmpty()) {
            return new DirectSubtaskAssignmentState(0, 0, 0);
        }
        int totalCount = 0;
        int assignedCount = 0;
        int assignedToPlayerCount = 0;
        for (Task candidate : allTasks) {
            if (candidate == null || !candidate.isSubtask() || !Objects.equals(parentTaskId, candidate.getParentTaskId())) {
                continue;
            }
            totalCount++;
            if (!isDirectlyAssigned(candidate)) {
                continue;
            }
            assignedCount++;
            if (playerUuid != null && !playerUuid.isEmpty() && Objects.equals(playerUuid, candidate.getAssigneeUuid())) {
                assignedToPlayerCount++;
            }
        }
        return new DirectSubtaskAssignmentState(totalCount, assignedCount, assignedToPlayerCount);
    }
}
