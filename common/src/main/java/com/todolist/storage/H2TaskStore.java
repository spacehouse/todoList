package com.todolist.storage;

import com.todolist.task.Task;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * H2TaskStore 负责在 H2 后端读写任务桶与任务元数据。
 */
public final class H2TaskStore {
    public static final String LOCAL_PERSONAL_BUCKET = "LOCAL_PERSONAL";
    public static final String LOCAL_OWNER = "LOCAL";
    public static final String PLAYER_PERSONAL_BUCKET = "PLAYER_PERSONAL";
    public static final String TEAM_BUCKET = "TEAM";
    public static final String TEAM_OWNER = "TEAM";
    private static final ConcurrentMap<String, Object> BUCKET_SAVE_LOCKS = new ConcurrentHashMap<>();

    private final H2ConnectionProvider connectionProvider;
    private final H2StorageBootstrap bootstrap;

    /**
     * 创建默认 H2 任务存储。
     */
    public H2TaskStore() {
        this(new H2ConnectionProvider(), new H2StorageBootstrap());
    }

    /**
     * 创建可注入依赖的 H2 任务存储。
     *
     * @param connectionProvider H2 连接提供器
     * @param bootstrap H2 启动器
     */
    public H2TaskStore(H2ConnectionProvider connectionProvider, H2StorageBootstrap bootstrap) {
        this.connectionProvider = connectionProvider;
        this.bootstrap = bootstrap;
    }

    /**
     * 读取单人本地任务桶。
     *
     * @return 任务列表
     * @throws IOException 读取失败时抛出
     */
    public List<Task> loadLocalTasks() throws IOException {
        return loadBucket(LOCAL_PERSONAL_BUCKET, LOCAL_OWNER);
    }

    /**
     * 保存单人本地任务桶。
     *
     * @param tasks 待保存任务
     * @throws IOException 保存失败时抛出
     */
    public void saveLocalTasks(List<Task> tasks) throws IOException {
        saveBucket(LOCAL_PERSONAL_BUCKET, LOCAL_OWNER, tasks);
    }

    /**
     * 读取玩家个人任务桶。
     *
     * @param playerUuid 玩家 UUID
     * @return 任务列表
     * @throws IOException 读取失败时抛出
     */
    public List<Task> loadPlayerTasks(UUID playerUuid) throws IOException {
        return loadBucket(PLAYER_PERSONAL_BUCKET, ownerOf(playerUuid));
    }

    /**
     * 保存玩家个人任务桶。
     *
     * @param playerUuid 玩家 UUID
     * @param tasks 待保存任务
     * @throws IOException 保存失败时抛出
     */
    public void savePlayerTasks(UUID playerUuid, List<Task> tasks) throws IOException {
        saveBucket(PLAYER_PERSONAL_BUCKET, ownerOf(playerUuid), tasks);
    }

    /**
     * 在本地集成服务端场景下，同时保存本地个人桶与玩家个人桶。
     * 使用同一事务减少重复连接与提交开销，并保持两份个人任务副本一致。
     *
     * @param playerUuid 玩家 UUID
     * @param tasks 待保存任务
     * @throws IOException 保存失败时抛出
     */
    public void saveLocalAndPlayerTasks(UUID playerUuid, List<Task> tasks) throws IOException {
        String localLockKey = bucketLockKey(LOCAL_PERSONAL_BUCKET, LOCAL_OWNER);
        String playerOwner = ownerOf(playerUuid);
        String playerLockKey = bucketLockKey(PLAYER_PERSONAL_BUCKET, playerOwner);
        if (localLockKey.compareTo(playerLockKey) <= 0) {
            withBucketLock(localLockKey, () -> withBucketLock(playerLockKey, () -> saveBucketsLocked(
                    List.of(
                            new BucketSaveRequest(LOCAL_PERSONAL_BUCKET, LOCAL_OWNER, tasks),
                            new BucketSaveRequest(PLAYER_PERSONAL_BUCKET, playerOwner, tasks)
                    )
            )));
            return;
        }
        withBucketLock(playerLockKey, () -> withBucketLock(localLockKey, () -> saveBucketsLocked(
                List.of(
                        new BucketSaveRequest(LOCAL_PERSONAL_BUCKET, LOCAL_OWNER, tasks),
                        new BucketSaveRequest(PLAYER_PERSONAL_BUCKET, playerOwner, tasks)
                )
        )));
    }

    /**
     * 读取团队任务桶。
     *
     * @return 团队任务列表
     * @throws IOException 读取失败时抛出
     */
    public List<Task> loadTeamTasks() throws IOException {
        return loadBucket(TEAM_BUCKET, TEAM_OWNER);
    }

    /**
     * 保存团队任务桶。
     *
     * @param tasks 待保存团队任务
     * @throws IOException 保存失败时抛出
     */
    public void saveTeamTasks(List<Task> tasks) throws IOException {
        saveBucket(TEAM_BUCKET, TEAM_OWNER, tasks);
    }

    /**
     * 读取指定桶的最后保存时间。
     *
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @return 最后保存时间，不存在时返回 0
     * @throws IOException 读取失败时抛出
     */
    public long getBucketLastSaved(String bucketType, String ownerUuid) throws IOException {
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT last_saved FROM storage_bucket_meta WHERE bucket_type = ? AND owner_uuid = ?
                     """)) {
            statement.setString(1, bucketType);
            statement.setString(2, ownerUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.QUERY_FAILED, "Failed to read H2 task bucket timestamp", exception);
        }
    }

    /**
     * 判断玩家个人任务桶是否存在。
     *
     * @param playerUuid 玩家 UUID
     * @return 存在时返回 true
     * @throws IOException 查询失败时抛出
     */
    public boolean hasPlayerTasks(UUID playerUuid) throws IOException {
        return hasBucket(PLAYER_PERSONAL_BUCKET, ownerOf(playerUuid));
    }

    /**
     * 读取指定任务桶。
     *
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @return 任务列表
     * @throws IOException 读取失败时抛出
     */
    private List<Task> loadBucket(String bucketType, String ownerUuid) throws IOException {
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection()) {
            java.util.Map<String, ListTag> tagsByTaskId = loadTagsByTaskId(connection, bucketType, ownerUuid);
            try (PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, scope, project_id, parent_task_id, subtask_sort_order, title, description, completed, priority, created_at,
                            due_date, creator_uuid, assignee_uuid, assignee_name
                     FROM tasks
                     WHERE bucket_type = ? AND owner_uuid = ?
                     ORDER BY sort_order, created_at, id
                     """)) {
                statement.setString(1, bucketType);
                statement.setString(2, ownerUuid);
                List<Task> tasks = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        tasks.add(readTask(resultSet, tagsByTaskId));
                    }
                }
                return tasks;
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.READ_FAILED, "Failed to load H2 tasks", exception);
        }
    }

    /**
     * 保存指定任务桶，使用替换式写入保持列表顺序。
     *
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param tasks 待保存任务
     * @throws IOException 保存失败时抛出
     */
    private void saveBucket(String bucketType, String ownerUuid, List<Task> tasks) throws IOException {
        withBucketLock(bucketLockKey(bucketType, ownerUuid), () -> saveBucketLocked(bucketType, ownerUuid, tasks));
    }

    /**
     * 在同桶保存锁内执行 H2 替换式任务保存。
     *
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param tasks 待保存任务
     * @throws IOException 保存失败时抛出
     */
    private void saveBucketLocked(String bucketType, String ownerUuid, List<Task> tasks) throws IOException {
        saveBucketsLocked(List.of(new BucketSaveRequest(bucketType, ownerUuid, tasks)));
    }

    /**
     * 在已获取保存锁后，用单连接事务批量保存多个任务桶。
     *
     * @param requests 待保存的桶请求列表
     * @throws IOException 保存失败时抛出
     */
    private void saveBucketsLocked(List<BucketSaveRequest> requests) throws IOException {
        H2MaintenanceLock.ensureWritable();
        bootstrap.ensureReady();
        long now = System.currentTimeMillis();
        try (Connection connection = connectionProvider.openConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (BucketSaveRequest request : requests) {
                    List<Task> safeTasks = request.tasks == null ? List.of() : request.tasks;
                    deleteBucket(connection, request.bucketType, request.ownerUuid);
                    insertTasks(connection, request.bucketType, request.ownerUuid, safeTasks, now);
                    upsertBucketMeta(connection, request.bucketType, request.ownerUuid, now);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.WRITE_FAILED, "Failed to save H2 tasks", exception);
        }
    }

    /**
     * 判断指定任务桶是否存在。
     *
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @return 存在时返回 true
     * @throws IOException 查询失败时抛出
     */
    private boolean hasBucket(String bucketType, String ownerUuid) throws IOException {
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM storage_bucket_meta WHERE bucket_type = ? AND owner_uuid = ?
                     """)) {
            statement.setString(1, bucketType);
            statement.setString(2, ownerUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.QUERY_FAILED, "Failed to query H2 task bucket", exception);
        }
    }

    /**
     * 标记当前 H2 数据库不可用并构造统一异常。
     *
     * @param reason 不可用原因
     * @param message 异常说明
     * @param cause 原始异常
     * @return 存储不可用异常
     */
    private StorageUnavailableException markUnavailable(H2StorageAvailability.Reason reason, String message, Throwable cause) {
        if (!H2StorageAvailability.isTransientLockFailure(cause)) {
            H2StorageAvailability.markUnavailable(connectionProvider.getDatabaseBasePath(), reason, cause == null ? message : cause.getMessage());
        }
        return new StorageUnavailableException(reason, message, cause);
    }

    /**
     * 删除指定任务桶现有任务和标签。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @throws SQLException 删除失败时抛出
     */
    private void deleteBucket(Connection connection, String bucketType, String ownerUuid) throws SQLException {
        try (PreparedStatement tags = connection.prepareStatement("DELETE FROM task_tags WHERE bucket_type = ? AND owner_uuid = ?");
             PreparedStatement tasks = connection.prepareStatement("DELETE FROM tasks WHERE bucket_type = ? AND owner_uuid = ?")) {
            tags.setString(1, bucketType);
            tags.setString(2, ownerUuid);
            tags.executeUpdate();
            tasks.setString(1, bucketType);
            tasks.setString(2, ownerUuid);
            tasks.executeUpdate();
        }
    }

    /**
     * 批量插入任务和标签。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param tasks 待插入任务
     * @param updatedAt 更新时间
     * @throws SQLException 插入失败时抛出
     */
    private void insertTasks(Connection connection, String bucketType, String ownerUuid, List<Task> tasks, long updatedAt) throws SQLException {
        try (PreparedStatement taskStatement = connection.prepareStatement("""
                INSERT INTO tasks(bucket_type, owner_uuid, id, scope, project_id, title, description, completed, priority,
                                  created_at, due_date, creator_uuid, assignee_uuid, assignee_name, parent_task_id,
                                  subtask_sort_order, sort_order, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement tagStatement = connection.prepareStatement("""
                MERGE INTO task_tags KEY(bucket_type, owner_uuid, task_id, tag)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (int index = 0; index < tasks.size(); index++) {
                Task task = tasks.get(index);
                bindTask(taskStatement, bucketType, ownerUuid, task, index, updatedAt);
                taskStatement.addBatch();
                int tagOrder = 0;
                for (String tag : task.getTags()) {
                    tagStatement.setString(1, bucketType);
                    tagStatement.setString(2, ownerUuid);
                    tagStatement.setString(3, task.getId());
                    tagStatement.setString(4, tag);
                    tagStatement.setLong(5, tagOrder++);
                    tagStatement.addBatch();
                }
            }
            if (!tasks.isEmpty()) {
                taskStatement.executeBatch();
            }
            taskStatement.clearBatch();
            tagStatement.executeBatch();
            tagStatement.clearBatch();
        }
    }

    /**
     * 生成同桶保存锁键，确保多桶复合保存时使用稳定顺序。
     *
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @return 保存锁键
     */
    private String bucketLockKey(String bucketType, String ownerUuid) {
        return bucketType + '\u0000' + ownerUuid;
    }

    /**
     * 在指定桶锁内执行保存逻辑。
     *
     * @param lockKey 锁键
     * @param action 保存动作
     * @throws IOException 保存失败时抛出
     */
    private void withBucketLock(String lockKey, BucketSaveAction action) throws IOException {
        Object saveLock = BUCKET_SAVE_LOCKS.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (saveLock) {
            action.run();
        }
    }

    /**
     * 写入或更新任务桶元数据。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param lastSaved 最后保存时间
     * @throws SQLException 写入失败时抛出
     */
    private void upsertBucketMeta(Connection connection, String bucketType, String ownerUuid, long lastSaved) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO storage_bucket_meta KEY(bucket_type, owner_uuid)
                VALUES (?, ?, ?)
                """)) {
            statement.setString(1, bucketType);
            statement.setString(2, ownerUuid);
            statement.setLong(3, lastSaved);
            statement.executeUpdate();
        }
    }

    /**
     * 绑定任务写入参数。
     *
     * @param statement SQL statement
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param task 任务
     * @param sortOrder 排序序号
     * @param updatedAt 更新时间
     * @throws SQLException 绑定失败时抛出
     */
    private void bindTask(PreparedStatement statement, String bucketType, String ownerUuid, Task task, int sortOrder, long updatedAt) throws SQLException {
        statement.setString(1, bucketType);
        statement.setString(2, ownerUuid);
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
        setNullableString(statement, 15, task.getParentTaskId());
        statement.setLong(16, task.getSubtaskSortOrder());
        statement.setLong(17, sortOrder);
        statement.setLong(18, updatedAt);
    }

    /**
     * 从当前结果行读取任务并补齐标签。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param resultSet 任务结果集
     * @return 任务对象
     * @throws SQLException 读取失败时抛出
     */
    private Task readTask(ResultSet resultSet, java.util.Map<String, ListTag> tagsByTaskId) throws SQLException {
        CompoundTag taskTag = new CompoundTag();
        taskTag.putString("id", resultSet.getString("id"));
        taskTag.putString("scope", resultSet.getString("scope"));
        putOptionalString(taskTag, "projectId", resultSet.getString("project_id"));
        putOptionalString(taskTag, "parentTaskId", resultSet.getString("parent_task_id"));
        taskTag.putLong("subtaskSortOrder", resultSet.getLong("subtask_sort_order"));
        taskTag.putString("title", resultSet.getString("title"));
        putOptionalString(taskTag, "description", resultSet.getString("description"));
        taskTag.putBoolean("completed", resultSet.getBoolean("completed"));
        taskTag.putString("priority", resultSet.getString("priority"));
        taskTag.putLong("createdAt", resultSet.getLong("created_at"));
        Long dueDate = readNullableLong(resultSet, "due_date");
        if (dueDate != null) {
            taskTag.putLong("dueDate", dueDate);
        }
        putOptionalString(taskTag, "creatorUuid", resultSet.getString("creator_uuid"));
        putOptionalString(taskTag, "assigneeUuid", resultSet.getString("assignee_uuid"));
        putOptionalString(taskTag, "assigneeName", resultSet.getString("assignee_name"));
        taskTag.put("tags", tagsByTaskId.getOrDefault(resultSet.getString("id"), new ListTag()));
        taskTag.put("subtasks", new ListTag());
        return Task.fromNbt(taskTag);
    }

    /**
     * 批量读取任务桶内所有标签，避免按任务逐条查询。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @return 按任务 ID 分组后的标签映射
     * @throws SQLException 读取失败时抛出
     */
    private java.util.Map<String, ListTag> loadTagsByTaskId(Connection connection, String bucketType, String ownerUuid) throws SQLException {
        java.util.Map<String, ListTag> tagsByTaskId = new java.util.HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT task_id, tag FROM task_tags
                WHERE bucket_type = ? AND owner_uuid = ?
                ORDER BY task_id, sort_order, tag
                """)) {
            statement.setString(1, bucketType);
            statement.setString(2, ownerUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String taskId = resultSet.getString("task_id");
                    ListTag tags = tagsByTaskId.computeIfAbsent(taskId, ignored -> new ListTag());
                    CompoundTag tag = new CompoundTag();
                    tag.putString("tag", resultSet.getString("tag"));
                    tags.add(tag);
                }
            }
        }
        return tagsByTaskId;
    }

    /**
     * 将玩家 UUID 转为桶拥有者字符串。
     *
     * @param playerUuid 玩家 UUID
     * @return 桶拥有者字符串
     */
    private String ownerOf(UUID playerUuid) {
        return playerUuid == null ? "" : playerUuid.toString();
    }

    /**
     * 多桶保存时使用的桶请求。
     */
    private record BucketSaveRequest(String bucketType, String ownerUuid, List<Task> tasks) {
    }

    /**
     * 支持抛出 IOException 的保存动作。
     */
    @FunctionalInterface
    private interface BucketSaveAction {
        void run() throws IOException;
    }

    /**
     * 写入可选字符串 NBT 字段。
     *
     * @param tag NBT 标签
     * @param key 字段名
     * @param value 字段值
     */
    private void putOptionalString(CompoundTag tag, String key, String value) {
        if (value != null && !value.isBlank()) {
            tag.putString(key, value);
        }
    }

    /**
     * 读取可空 long 值。
     *
     * @param resultSet 结果集
     * @param column 列名
     * @return 可空 long
     * @throws SQLException 读取失败时抛出
     */
    private Long readNullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
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
}
