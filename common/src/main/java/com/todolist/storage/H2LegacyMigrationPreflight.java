package com.todolist.storage;

import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskCompatibilityAdapter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * H2LegacyMigrationPreflight 负责在写入 H2 前校验旧 NBT 数据是否可迁移。
 */
public final class H2LegacyMigrationPreflight {
    /**
     * 创建迁移预检器。
     */
    public H2LegacyMigrationPreflight() {
    }

    /**
     * 校验完整迁移快照。
     *
     * @param data 旧数据迁移快照
     * @throws LegacyMigrationException 发现不可迁移数据时抛出
     */
    public void validate(LegacyMigrationData data) throws LegacyMigrationException {
        if (data == null) {
            return;
        }
        validateTasks(data);
        validateProjects(data);
        validatePlayerStates(data);
    }

    /**
     * 校验任务桶、任务字段、重复 ID 和旧 subtasks 迁移约束。
     *
     * @param data 旧数据迁移快照
     * @throws LegacyMigrationException 发现不可迁移任务时抛出
     */
    private void validateTasks(LegacyMigrationData data) throws LegacyMigrationException {
        for (LegacyTaskBucket bucket : data.taskBuckets()) {
            requireLength(bucket.bucketType(), 32, "task bucket_type");
            requireLength(bucket.ownerUuid(), 64, "task owner_uuid");
            Set<String> taskIds = new HashSet<>();
            for (Task task : normalizeTasks(bucket)) {
                requirePresent(task.getId(), "task id");
                if (!taskIds.add(task.getId())) {
                    throw new LegacyMigrationException("Duplicate task id in bucket " + bucket.bucketType() + "/" + bucket.ownerUuid() + ": " + task.getId());
                }
                requireLength(task.getId(), 64, "task id");
                requireLength(task.getTitle(), 512, "task title");
                requireLength(task.getDescription(), 16384, "task description");
                requireLength(task.getProjectId(), 64, "task project_id");
                requireLength(task.getCreatorUuid(), 64, "task creator_uuid");
                requireLength(task.getAssigneeUuid(), 64, "task assignee_uuid");
                requireLength(task.getAssigneeName(), 256, "task assignee_name");
                if (task.getPriority() == null) {
                    throw new LegacyMigrationException("Task priority is missing: " + task.getId());
                }
                if (task.getScope() == null) {
                    throw new LegacyMigrationException("Task scope is missing: " + task.getId());
                }
                for (String tag : task.getTags()) {
                    requireLength(tag, 128, "task tag");
                }
            }
        }
    }

    /**
     * 校验项目桶、项目字段、重复 ID 和成员 UUID。
     *
     * @param data 旧数据迁移快照
     * @throws LegacyMigrationException 发现不可迁移项目时抛出
     */
    private void validateProjects(LegacyMigrationData data) throws LegacyMigrationException {
        for (LegacyProjectBucket bucket : data.projectBuckets()) {
            requireLength(bucket.bucketType(), 32, "project bucket_type");
            Set<String> projectIds = new HashSet<>();
            for (Project project : bucket.projects()) {
                requirePresent(project.getId(), "project id");
                if (!projectIds.add(project.getId())) {
                    throw new LegacyMigrationException("Duplicate project id in bucket " + bucket.bucketType() + ": " + project.getId());
                }
                requireLength(project.getId(), 64, "project id");
                requireLength(project.getName(), 512, "project name");
                requireLength(project.getOwnerUuid(), 64, "project owner_uuid");
                if (project.getScope() == null) {
                    throw new LegacyMigrationException("Project scope is missing: " + project.getId());
                }
                for (Map.Entry<String, Project.ProjectRole> entry : project.getMembers().entrySet()) {
                    requireUuid(entry.getKey(), "project member uuid");
                    if (entry.getValue() == null) {
                        throw new LegacyMigrationException("Project member role is missing: " + project.getId() + "/" + entry.getKey());
                    }
                    requireLength(project.getMemberName(entry.getKey()), 256, "project member name");
                }
            }
        }
    }

    /**
     * 校验玩家项目状态字段。
     *
     * @param data 旧数据迁移快照
     * @throws LegacyMigrationException 发现不可迁移状态时抛出
     */
    private void validatePlayerStates(LegacyMigrationData data) throws LegacyMigrationException {
        for (LegacyPlayerProjectStateRecord record : data.playerProjectStates()) {
            if (record.playerUuid() == null) {
                throw new LegacyMigrationException("Player project state uuid is missing");
            }
            if (record.state() == null) {
                throw new LegacyMigrationException("Player project state is missing: " + record.playerUuid());
            }
            requireLength(record.state().getActiveProjectId(), 64, "active project id");
            for (String projectId : record.state().getHudStarredProjectIds()) {
                requireLength(projectId, 64, "hud starred project id");
            }
        }
    }

    /**
     * 校验必填字符串存在。
     *
     * @param value 字段值
     * @param label 字段标签
     * @throws LegacyMigrationException 字段为空时抛出
     */
    private void requirePresent(String value, String label) throws LegacyMigrationException {
        if (value == null || value.isBlank()) {
            throw new LegacyMigrationException("Missing required " + label);
        }
    }

    /**
     * 校验字符串长度。
     *
     * @param value 字段值
     * @param maxLength 最大长度
     * @param label 字段标签
     * @throws LegacyMigrationException 超长时抛出
     */
    private void requireLength(String value, int maxLength, String label) throws LegacyMigrationException {
        if (value != null && value.length() > maxLength) {
            throw new LegacyMigrationException(label + " exceeds max length " + maxLength + ": " + value.length());
        }
    }

    /**
     * 校验 UUID 字符串格式。
     *
     * @param value UUID 字符串
     * @param label 字段标签
     * @throws LegacyMigrationException 格式非法时抛出
     */
    private void requireUuid(String value, String label) throws LegacyMigrationException {
        requirePresent(value, label);
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new LegacyMigrationException(label + " is not a valid UUID: " + value, exception);
        }
    }

    /**
     * 将旧任务桶标准化为可迁移的平铺结构；仅允许单层 subtasks。
     *
     * @param bucket 旧任务桶
     * @return 标准化后的任务列表
     * @throws LegacyMigrationException 检测到不可迁移嵌套时抛出
     */
    private Iterable<Task> normalizeTasks(LegacyTaskBucket bucket) throws LegacyMigrationException {
        try {
            return TaskCompatibilityAdapter.normalizeLoadedTasks(bucket.tasks());
        } catch (IllegalStateException exception) {
            throw new LegacyMigrationException("旧数据包含两层及以上 subtasks，当前迁移仅支持单层结构: "
                    + bucket.bucketType() + "/" + bucket.ownerUuid(), exception);
        }
    }
}
