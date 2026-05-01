package com.todolist.storage;

import com.todolist.project.Project;
import com.todolist.task.Task;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.StringJoiner;

/**
 * H2LegacyMigrator 负责把旧 NBT 快照单事务导入 H2 schema v1。
 */
public final class H2LegacyMigrator {
    private static final int META_VALUE_MAX_LENGTH = 4096;

    private final H2SchemaInitializer schemaInitializer;
    private final H2LegacyMigrationPreflight preflight;

    /**
     * 创建旧数据 H2 迁移器。
     */
    public H2LegacyMigrator() {
        this(new H2SchemaInitializer(), new H2LegacyMigrationPreflight());
    }

    /**
     * 创建可注入依赖的旧数据 H2 迁移器。
     *
     * @param schemaInitializer schema 初始化器
     * @param preflight 迁移预检器
     */
    public H2LegacyMigrator(H2SchemaInitializer schemaInitializer, H2LegacyMigrationPreflight preflight) {
        this.schemaInitializer = schemaInitializer;
        this.preflight = preflight;
    }

    /**
     * 将旧数据快照导入 H2，并在同一事务内写入迁移完成标记。
     *
     * @param connection H2 连接
     * @param data 旧数据快照
     * @throws LegacyMigrationException 迁移失败时抛出
     */
    public void migrate(Connection connection, LegacyMigrationData data) throws LegacyMigrationException {
        if (connection == null) {
            throw new LegacyMigrationException("H2 connection is missing");
        }
        LegacyMigrationData safeData = data == null ? new LegacyMigrationData(null, null, null) : data;
        preflight.validate(safeData);
        try {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                schemaInitializer.initialize(connection);
                insertTaskBuckets(connection, safeData);
                insertProjectBuckets(connection, safeData);
                insertPlayerProjectStates(connection, safeData);
                writeMigrationMeta(connection, safeData);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof LegacyMigrationException legacyMigrationException) {
                    throw legacyMigrationException;
                }
                throw new LegacyMigrationException("Failed to migrate legacy NBT data to H2", exception);
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw new LegacyMigrationException("Failed to control H2 migration transaction", exception);
        }
    }

    /**
     * 写入任务桶、任务主体、标签和桶元数据。
     *
     * @param connection H2 连接
     * @param data 旧数据快照
     * @throws SQLException 写入失败时抛出
     */
    private void insertTaskBuckets(Connection connection, LegacyMigrationData data) throws SQLException {
        try (PreparedStatement taskStatement = connection.prepareStatement("""
                MERGE INTO tasks KEY(bucket_type, owner_uuid, id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement tagStatement = connection.prepareStatement("""
                MERGE INTO task_tags KEY(bucket_type, owner_uuid, task_id, tag)
                VALUES (?, ?, ?, ?, ?)
                """);
             PreparedStatement bucketMetaStatement = connection.prepareStatement("""
                MERGE INTO storage_bucket_meta KEY(bucket_type, owner_uuid)
                VALUES (?, ?, ?)
                """)) {
            for (LegacyTaskBucket bucket : data.taskBuckets()) {
                long updatedAt = bucket.lastSaved() > 0 ? bucket.lastSaved() : System.currentTimeMillis();
                for (int index = 0; index < bucket.tasks().size(); index++) {
                    Task task = bucket.tasks().get(index);
                    bindTask(taskStatement, bucket, task, index, updatedAt);
                    taskStatement.executeUpdate();
                    int tagOrder = 0;
                    for (String tag : task.getTags()) {
                        tagStatement.setString(1, bucket.bucketType());
                        tagStatement.setString(2, bucket.ownerUuid());
                        tagStatement.setString(3, task.getId());
                        tagStatement.setString(4, tag);
                        tagStatement.setLong(5, tagOrder++);
                        tagStatement.executeUpdate();
                    }
                }
                bucketMetaStatement.setString(1, bucket.bucketType());
                bucketMetaStatement.setString(2, bucket.ownerUuid());
                bucketMetaStatement.setLong(3, updatedAt);
                bucketMetaStatement.executeUpdate();
            }
        }
    }

    /**
     * 绑定任务写入参数。
     *
     * @param statement SQL statement
     * @param bucket 任务桶
     * @param task 任务
     * @param sortOrder 排序序号
     * @param updatedAt 更新时间
     * @throws SQLException 绑定失败时抛出
     */
    private void bindTask(PreparedStatement statement, LegacyTaskBucket bucket, Task task, int sortOrder, long updatedAt) throws SQLException {
        statement.setString(1, bucket.bucketType());
        statement.setString(2, bucket.ownerUuid());
        statement.setString(3, task.getId());
        statement.setString(4, task.getScope().name());
        setNullableString(statement, 5, task.getProjectId());
        statement.setString(6, task.getTitle());
        setNullableString(statement, 7, task.getDescription());
        statement.setBoolean(8, task.isCompleted());
        statement.setString(9, task.getPriority().name());
        statement.setLong(10, task.getCreatedAt());
        setNullableLong(statement, 11, task.getDueDate());
        setNullableString(statement, 12, task.getCreatorUuid());
        setNullableString(statement, 13, task.getAssigneeUuid());
        setNullableString(statement, 14, task.getAssigneeName());
        statement.setLong(15, sortOrder);
        statement.setLong(16, updatedAt > 0 ? updatedAt : task.getCreatedAt());
    }

    /**
     * 写入项目主体和成员。
     *
     * @param connection H2 连接
     * @param data 旧数据快照
     * @throws SQLException 写入失败时抛出
     */
    private void insertProjectBuckets(Connection connection, LegacyMigrationData data) throws SQLException {
        try (PreparedStatement projectStatement = connection.prepareStatement("""
                MERGE INTO projects KEY(bucket_type, id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement memberStatement = connection.prepareStatement("""
                MERGE INTO project_members KEY(bucket_type, project_id, player_uuid)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (LegacyProjectBucket bucket : data.projectBuckets()) {
                for (int index = 0; index < bucket.projects().size(); index++) {
                    Project project = bucket.projects().get(index);
                    bindProject(projectStatement, bucket, project, index);
                    projectStatement.executeUpdate();
                    for (var entry : project.getMembers().entrySet()) {
                        memberStatement.setString(1, bucket.bucketType());
                        memberStatement.setString(2, project.getId());
                        memberStatement.setString(3, entry.getKey());
                        memberStatement.setString(4, entry.getValue().name());
                        setNullableString(memberStatement, 5, project.getMemberName(entry.getKey()));
                        memberStatement.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * 绑定项目写入参数。
     *
     * @param statement SQL statement
     * @param bucket 项目桶
     * @param project 项目
     * @param sortOrder 排序序号
     * @throws SQLException 绑定失败时抛出
     */
    private void bindProject(PreparedStatement statement, LegacyProjectBucket bucket, Project project, int sortOrder) throws SQLException {
        statement.setString(1, bucket.bucketType());
        statement.setString(2, project.getId());
        statement.setString(3, project.getName());
        statement.setInt(4, project.getColor());
        statement.setString(5, project.getScope().name());
        setNullableString(statement, 6, project.getOwnerUuid());
        statement.setLong(7, project.getCreatedAt());
        statement.setBoolean(8, project.isAllowMemberCreate());
        statement.setBoolean(9, project.isAllowAllPlayersClaimComplete());
        statement.setLong(10, sortOrder);
    }

    /**
     * 写入玩家项目状态和 HUD 星标项目。
     *
     * @param connection H2 连接
     * @param data 旧数据快照
     * @throws SQLException 写入失败时抛出
     */
    private void insertPlayerProjectStates(Connection connection, LegacyMigrationData data) throws SQLException {
        try (PreparedStatement stateStatement = connection.prepareStatement("""
                MERGE INTO player_project_state KEY(player_uuid)
                VALUES (?, ?, ?, ?)
                """);
             PreparedStatement starredStatement = connection.prepareStatement("""
                MERGE INTO player_hud_starred_projects KEY(player_uuid, project_bucket_type, project_id)
                VALUES (?, ?, ?, ?)
                """)) {
            for (LegacyPlayerProjectStateRecord record : data.playerProjectStates()) {
                String playerUuid = record.playerUuid().toString();
                long lastSaved = record.lastSaved() > 0 ? record.lastSaved() : System.currentTimeMillis();
                stateStatement.setString(1, playerUuid);
                setNullableString(stateStatement, 2, record.state().getActiveProjectId());
                stateStatement.setBoolean(3, record.state().isHudVisible());
                stateStatement.setLong(4, lastSaved);
                stateStatement.executeUpdate();

                for (int index = 0; index < record.state().getHudStarredProjectIds().size(); index++) {
                    starredStatement.setString(1, playerUuid);
                    starredStatement.setString(2, record.state().getHudStarredProjectIds().get(index));
                    starredStatement.setString(3, inferProjectBucketType(record.state().getHudStarredProjectIds().get(index), data));
                    starredStatement.setLong(4, index);
                    starredStatement.executeUpdate();
                }
            }
        }
    }

    /**
     * 写入迁移元数据。
     *
     * @param connection H2 连接
     * @param data 旧数据快照
     * @throws SQLException 写入失败时抛出
     */
    private void writeMigrationMeta(Connection connection, LegacyMigrationData data) throws SQLException {
        long now = System.currentTimeMillis();
        String summary = data.isEmpty() ? "no legacy data found" : buildSummary(data);
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO storage_meta KEY("key")
                VALUES (?, ?, ?)
                """)) {
            upsertMeta(statement, "dat_migration_completed", "true", now);
            upsertMeta(statement, "dat_migration_completed_at", String.valueOf(now), now);
            upsertMeta(statement, "dat_migration_summary", truncate(summary), now);
            upsertMeta(statement, "migration_source_format", "nbt-v1", now);
        }
    }

    /**
     * 绑定并写入单个 storage_meta 项。
     *
     * @param statement SQL statement
     * @param key meta key
     * @param value meta value
     * @param updatedAt 更新时间
     * @throws SQLException 写入失败时抛出
     */
    private void upsertMeta(PreparedStatement statement, String key, String value, long updatedAt) throws SQLException {
        statement.setString(1, key);
        statement.setString(2, value);
        statement.setLong(3, updatedAt);
        statement.executeUpdate();
    }

    /**
     * 构造迁移摘要。
     *
     * @param data 旧数据快照
     * @return 摘要文本
     */
    private String buildSummary(LegacyMigrationData data) {
        int taskCount = data.taskBuckets().stream().mapToInt(bucket -> bucket.tasks().size()).sum();
        int projectCount = data.projectBuckets().stream().mapToInt(bucket -> bucket.projects().size()).sum();
        StringJoiner joiner = new StringJoiner(", ");
        joiner.add("taskBuckets=" + data.taskBuckets().size());
        joiner.add("tasks=" + taskCount);
        joiner.add("projectBuckets=" + data.projectBuckets().size());
        joiner.add("projects=" + projectCount);
        joiner.add("playerProjectStates=" + data.playerProjectStates().size());
        return joiner.toString();
    }

    /**
     * 推断 HUD 星标项目所属项目桶。
     *
     * @param projectId 项目 ID
     * @param data 旧数据快照
     * @return 项目桶类型
     */
    private String inferProjectBucketType(String projectId, LegacyMigrationData data) {
        if (projectId != null) {
            for (LegacyProjectBucket bucket : data.projectBuckets()) {
                if (bucket.projects().stream().anyMatch(project -> projectId.equals(project.getId()))) {
                    return bucket.bucketType();
                }
            }
        }
        return "PERSONAL_PROJECTS";
    }

    /**
     * 绑定可空字符串。
     *
     * @param statement SQL statement
     * @param index 参数序号
     * @param value 字符串值
     * @throws SQLException 绑定失败时抛出
     */
    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    /**
     * 绑定可空 Long。
     *
     * @param statement SQL statement
     * @param index 参数序号
     * @param value Long 值
     * @throws SQLException 绑定失败时抛出
     */
    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    /**
     * 截断 meta value 到 schema 允许长度。
     *
     * @param value 原始值
     * @return 截断后的值
     */
    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= META_VALUE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, META_VALUE_MAX_LENGTH);
    }
}
