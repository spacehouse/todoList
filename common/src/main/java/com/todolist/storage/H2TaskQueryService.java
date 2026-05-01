package com.todolist.storage;

import com.todolist.task.Task;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H2TaskQueryService 封装面向 GUI/HUD 的只读任务查询，避免界面层直接拼接 SQL。
 */
public final class H2TaskQueryService {
    private final H2ConnectionProvider connectionProvider;
    private final H2StorageBootstrap bootstrap;

    /**
     * HUD 指派过滤模式。
     */
    public enum HudAssigneeFilter {
        ANY,
        UNASSIGNED,
        ASSIGNED_TO_PLAYER
    }

    /**
     * HudTaskQuery 描述一次 HUD 任务查询需要的桶、项目、指派和显示数量限制。
     */
    public static final class HudTaskQuery {
        private final String bucketType;
        private final String ownerUuid;
        private final Collection<String> projectIds;
        private final boolean includeAnyAssignedProject;
        private final HudAssigneeFilter assigneeFilter;
        private final String assigneeUuid;
        private final int pendingLimit;
        private final int doneLimit;

        /**
         * 创建 HUD 任务查询参数。
         *
         * @param bucketType 任务桶类型
         * @param ownerUuid 任务桶拥有者
         * @param projectIds 允许显示的项目 ID
         * @param includeAnyAssignedProject 是否显示任意已分配项目的任务
         * @param assigneeFilter 指派过滤模式
         * @param assigneeUuid 当前玩家 UUID
         * @param pendingLimit 未完成任务读取上限
         * @param doneLimit 已完成任务读取上限
         */
        public HudTaskQuery(String bucketType,
                            String ownerUuid,
                            Collection<String> projectIds,
                            boolean includeAnyAssignedProject,
                            HudAssigneeFilter assigneeFilter,
                            String assigneeUuid,
                            int pendingLimit,
                            int doneLimit) {
            this.bucketType = bucketType;
            this.ownerUuid = ownerUuid;
            this.projectIds = projectIds == null ? List.of() : List.copyOf(projectIds);
            this.includeAnyAssignedProject = includeAnyAssignedProject;
            this.assigneeFilter = assigneeFilter == null ? HudAssigneeFilter.ANY : assigneeFilter;
            this.assigneeUuid = assigneeUuid == null ? "" : assigneeUuid;
            this.pendingLimit = Math.max(0, pendingLimit);
            this.doneLimit = Math.max(0, doneLimit);
        }
    }

    /**
     * HudTaskQueryResult 保存 HUD 查询返回的可绘制任务和匹配总数。
     */
    public static final class HudTaskQueryResult {
        private final List<Task> pendingTasks;
        private final List<Task> doneTasks;
        private final int pendingTotal;
        private final int doneTotal;

        /**
         * 创建 HUD 任务查询结果。
         *
         * @param pendingTasks 未完成任务可绘制行
         * @param doneTasks 已完成任务可绘制行
         * @param pendingTotal 未完成匹配总数
         * @param doneTotal 已完成匹配总数
         */
        private HudTaskQueryResult(List<Task> pendingTasks, List<Task> doneTasks, int pendingTotal, int doneTotal) {
            this.pendingTasks = List.copyOf(pendingTasks);
            this.doneTasks = List.copyOf(doneTasks);
            this.pendingTotal = pendingTotal;
            this.doneTotal = doneTotal;
        }

        /**
         * 返回未完成任务可绘制行。
         *
         * @return 未完成任务列表
         */
        public List<Task> getPendingTasks() {
            return pendingTasks;
        }

        /**
         * 返回已完成任务可绘制行。
         *
         * @return 已完成任务列表
         */
        public List<Task> getDoneTasks() {
            return doneTasks;
        }

        /**
         * 返回未完成匹配总数。
         *
         * @return 未完成总数
         */
        public int getPendingTotal() {
            return pendingTotal;
        }

        /**
         * 返回已完成匹配总数。
         *
         * @return 已完成总数
         */
        public int getDoneTotal() {
            return doneTotal;
        }
    }

    /**
     * 创建默认 H2 任务查询服务。
     */
    public H2TaskQueryService() {
        this(new H2ConnectionProvider(), new H2StorageBootstrap());
    }

    /**
     * 创建可注入依赖的 H2 任务查询服务。
     *
     * @param connectionProvider H2 连接提供器
     * @param bootstrap H2 启动器
     */
    public H2TaskQueryService(H2ConnectionProvider connectionProvider, H2StorageBootstrap bootstrap) {
        this.connectionProvider = connectionProvider;
        this.bootstrap = bootstrap;
    }

    /**
     * 按项目 ID 聚合任务数量，返回值会保留所有请求项目并为缺失项目填充 0。
     *
     * @param bucketType 任务桶类型
     * @param ownerUuid 任务桶拥有者
     * @param projectIds 需要统计的项目 ID 集合
     * @return 项目 ID 到任务数量的映射
     * @throws IOException 查询失败时抛出
     */
    public Map<String, Integer> countTasksByProjectIds(String bucketType, String ownerUuid, Collection<String> projectIds) throws IOException {
        Map<String, Integer> counts = initializeProjectCounts(projectIds);
        if (counts.isEmpty()) {
            return counts;
        }
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT project_id, COUNT(*) AS task_count
                     FROM tasks
                     WHERE bucket_type = ? AND owner_uuid = ? AND project_id IS NOT NULL AND project_id <> ''
                     GROUP BY project_id
                     """)) {
            statement.setString(1, bucketType);
            statement.setString(2, ownerUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String projectId = resultSet.getString("project_id");
                    if (counts.containsKey(projectId)) {
                        counts.put(projectId, resultSet.getInt("task_count"));
                    }
                }
            }
            return counts;
        } catch (SQLException exception) {
            throw markUnavailable("Failed to count H2 tasks by project", exception);
        }
    }

    /**
     * 查询 HUD 当前视图需要的任务行和匹配总数。
     *
     * @param query HUD 查询参数
     * @return HUD 查询结果
     * @throws IOException 查询失败时抛出
     */
    public HudTaskQueryResult queryHudTasks(HudTaskQuery query) throws IOException {
        if (query == null || (!query.includeAnyAssignedProject && sanitizeProjectIds(query.projectIds).isEmpty())) {
            return new HudTaskQueryResult(List.of(), List.of(), 0, 0);
        }
        if (query.assigneeFilter == HudAssigneeFilter.ASSIGNED_TO_PLAYER && query.assigneeUuid.isEmpty()) {
            return new HudTaskQueryResult(List.of(), List.of(), 0, 0);
        }
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection()) {
            int pendingTotal = countHudTasks(connection, query, false);
            int doneTotal = countHudTasks(connection, query, true);
            List<Task> pending = query.pendingLimit <= 0 ? List.of() : loadHudTasks(connection, query, false, query.pendingLimit);
            List<Task> done = query.doneLimit <= 0 ? List.of() : loadHudTasks(connection, query, true, query.doneLimit);
            return new HudTaskQueryResult(pending, done, pendingTotal, doneTotal);
        } catch (SQLException exception) {
            throw markUnavailable("Failed to query H2 HUD tasks", exception);
        }
    }

    /**
     * 查询 GUI 当前任务列表需要的任务 ID，并保持任务管理器稳定顺序。
     *
     * @param bucketType 任务桶类型
     * @param ownerUuid 任务桶拥有者
     * @param projectId 当前项目 ID
     * @param completed 是否查询已完成任务
     * @param priorityName 优先级名称；为空时不过滤优先级
     * @param assigneeFilter 指派过滤模式
     * @param assigneeUuid 当前玩家 UUID
     * @param searchQuery 搜索关键词
     * @return 任务 ID 列表
     * @throws IOException 查询失败时抛出
     */
    public List<String> queryGuiTaskIds(String bucketType,
                                        String ownerUuid,
                                        String projectId,
                                        boolean completed,
                                        String priorityName,
                                         HudAssigneeFilter assigneeFilter,
                                         String assigneeUuid,
                                         String searchQuery) throws IOException {
        return queryGuiTaskIds(
                bucketType,
                ownerUuid,
                projectId,
                completed,
                priorityName,
                assigneeFilter,
                assigneeUuid,
                searchQuery,
                -1,
                0
        );
    }

    /**
     * 分页查询 GUI 当前任务列表需要的任务 ID，并保持任务管理器稳定顺序。
     *
     * @param bucketType 任务桶类型
     * @param ownerUuid 任务桶拥有者
     * @param projectId 当前项目 ID
     * @param completed 是否查询已完成任务
     * @param priorityName 优先级名称；为空时不过滤优先级
     * @param assigneeFilter 指派过滤模式
     * @param assigneeUuid 当前玩家 UUID
     * @param searchQuery 搜索关键词
     * @param limit 读取上限；小于 0 表示不限制
     * @param offset 起始偏移；小于 0 时按 0 处理
     * @return 任务 ID 列表
     * @throws IOException 查询失败时抛出
     */
    public List<String> queryGuiTaskIds(String bucketType,
                                        String ownerUuid,
                                        String projectId,
                                        boolean completed,
                                        String priorityName,
                                        HudAssigneeFilter assigneeFilter,
                                        String assigneeUuid,
                                        String searchQuery,
                                        int limit,
                                        int offset) throws IOException {
        if (projectId == null || projectId.isEmpty()) {
            return List.of();
        }
        if (assigneeFilter == HudAssigneeFilter.ASSIGNED_TO_PLAYER && (assigneeUuid == null || assigneeUuid.isEmpty())) {
            return List.of();
        }
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection()) {
            QueryParts queryParts = buildGuiQueryParts(
                    bucketType,
                    ownerUuid,
                    projectId,
                    completed,
                    priorityName,
                    assigneeFilter,
                    assigneeUuid,
                    searchQuery
            );
            String limitClause = "";
            if (limit >= 0) {
                limitClause = "LIMIT ? OFFSET ?";
                queryParts.parameters.add(Math.max(0, limit));
                queryParts.parameters.add(Math.max(0, offset));
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id
                    FROM tasks
                    """ + queryParts.whereClause + """
                    ORDER BY sort_order, created_at, id
                    """ + limitClause + """
                    """)) {
                bindHudQueryParameters(statement, queryParts.parameters);
                List<String> ids = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(resultSet.getString("id"));
                    }
                }
                return ids;
            }
        } catch (SQLException exception) {
            throw markUnavailable("Failed to query H2 GUI task ids", exception);
        }
    }

    /**
     * 统计 GUI 当前过滤条件下的任务 ID 总数。
     *
     * @param bucketType 任务桶类型
     * @param ownerUuid 任务桶拥有者
     * @param projectId 当前项目 ID
     * @param completed 是否统计已完成任务
     * @param priorityName 优先级名称；为空时不过滤优先级
     * @param assigneeFilter 指派过滤模式
     * @param assigneeUuid 当前玩家 UUID
     * @param searchQuery 搜索关键词
     * @return 匹配总数
     * @throws IOException 查询失败时抛出
     */
    public int countGuiTaskIds(String bucketType,
                               String ownerUuid,
                               String projectId,
                               boolean completed,
                               String priorityName,
                               HudAssigneeFilter assigneeFilter,
                               String assigneeUuid,
                               String searchQuery) throws IOException {
        if (projectId == null || projectId.isEmpty()) {
            return 0;
        }
        if (assigneeFilter == HudAssigneeFilter.ASSIGNED_TO_PLAYER && (assigneeUuid == null || assigneeUuid.isEmpty())) {
            return 0;
        }
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection()) {
            QueryParts queryParts = buildGuiQueryParts(
                    bucketType,
                    ownerUuid,
                    projectId,
                    completed,
                    priorityName,
                    assigneeFilter,
                    assigneeUuid,
                    searchQuery
            );
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM tasks " + queryParts.whereClause)) {
                bindHudQueryParameters(statement, queryParts.parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            }
        } catch (SQLException exception) {
            throw markUnavailable("Failed to count H2 GUI task ids", exception);
        }
    }

    /**
     * 初始化项目计数映射，过滤空项目 ID 并保持输入顺序。
     *
     * @param projectIds 原始项目 ID 集合
     * @return 初始计数映射
     */
    private Map<String, Integer> initializeProjectCounts(Collection<String> projectIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (projectIds == null) {
            return counts;
        }
        for (String projectId : projectIds) {
            if (projectId == null || projectId.isEmpty()) {
                continue;
            }
            counts.putIfAbsent(projectId, 0);
        }
        return counts;
    }

    /**
     * 统计 HUD 查询命中的任务总数。
     *
     * @param connection H2 连接
     * @param query HUD 查询参数
     * @param completed 是否统计已完成任务
     * @return 任务总数
     * @throws SQLException 查询失败时抛出
     */
    private int countHudTasks(Connection connection, HudTaskQuery query, boolean completed) throws SQLException {
        QueryParts queryParts = buildHudQueryParts(query, completed);
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM tasks " + queryParts.whereClause)) {
            bindHudQueryParameters(statement, queryParts.parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /**
     * 读取 HUD 查询命中的可绘制任务行。
     *
     * @param connection H2 连接
     * @param query HUD 查询参数
     * @param completed 是否读取已完成任务
     * @param limit 读取上限
     * @return 任务列表
     * @throws SQLException 查询失败时抛出
     */
    private List<Task> loadHudTasks(Connection connection, HudTaskQuery query, boolean completed, int limit) throws SQLException {
        QueryParts queryParts = buildHudQueryParts(query, completed);
        queryParts.parameters.add(Math.max(0, limit));
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, scope, project_id, title, description, completed, priority, created_at,
                       due_date, creator_uuid, assignee_uuid, assignee_name
                FROM tasks
                """ + queryParts.whereClause + """
                ORDER BY sort_order, created_at, id
                LIMIT ?
                """)) {
            bindHudQueryParameters(statement, queryParts.parameters);
            List<Task> tasks = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tasks.add(readTask(connection, query.bucketType, query.ownerUuid, resultSet));
                }
            }
            return tasks;
        }
    }

    /**
     * 构建 HUD 查询的 WHERE 子句和参数列表。
     *
     * @param query HUD 查询参数
     * @param completed 是否查询已完成任务
     * @return 查询片段
     */
    private QueryParts buildHudQueryParts(HudTaskQuery query, boolean completed) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE bucket_type = ? AND owner_uuid = ? AND completed = ?");
        parameters.add(query.bucketType);
        parameters.add(query.ownerUuid);
        parameters.add(completed);

        List<String> projectIds = sanitizeProjectIds(query.projectIds);
        if (query.includeAnyAssignedProject) {
            where.append(" AND project_id IS NOT NULL AND project_id <> ''");
        } else {
            where.append(" AND project_id IN (");
            for (int index = 0; index < projectIds.size(); index++) {
                if (index > 0) {
                    where.append(", ");
                }
                where.append('?');
                parameters.add(projectIds.get(index));
            }
            where.append(')');
        }

        if (query.assigneeFilter == HudAssigneeFilter.UNASSIGNED) {
            where.append(" AND (assignee_uuid IS NULL OR assignee_uuid = '')");
        } else if (query.assigneeFilter == HudAssigneeFilter.ASSIGNED_TO_PLAYER) {
            where.append(" AND assignee_uuid = ?");
            parameters.add(query.assigneeUuid);
        }
        return new QueryParts(where.toString(), parameters);
    }

    /**
     * 构建 GUI 任务列表查询的 WHERE 子句和参数。
     *
     * @param bucketType 任务桶类型
     * @param ownerUuid 任务桶拥有者
     * @param projectId 当前项目 ID
     * @param completed 是否查询已完成任务
     * @param priorityName 优先级名称
     * @param assigneeFilter 指派过滤模式
     * @param assigneeUuid 当前玩家 UUID
     * @param searchQuery 搜索关键词
     * @return 查询片段
     */
    private QueryParts buildGuiQueryParts(String bucketType,
                                          String ownerUuid,
                                          String projectId,
                                          boolean completed,
                                          String priorityName,
                                          HudAssigneeFilter assigneeFilter,
                                          String assigneeUuid,
                                          String searchQuery) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE bucket_type = ? AND owner_uuid = ? AND project_id = ? AND completed = ?");
        parameters.add(bucketType);
        parameters.add(ownerUuid);
        parameters.add(projectId);
        parameters.add(completed);

        if (priorityName != null && !priorityName.isEmpty()) {
            where.append(" AND priority = ?");
            parameters.add(priorityName);
        }
        if (assigneeFilter == HudAssigneeFilter.UNASSIGNED) {
            where.append(" AND (assignee_uuid IS NULL OR assignee_uuid = '')");
        } else if (assigneeFilter == HudAssigneeFilter.ASSIGNED_TO_PLAYER) {
            where.append(" AND assignee_uuid = ?");
            parameters.add(assigneeUuid == null ? "" : assigneeUuid);
        }

        String normalizedSearch = normalizeSearchQuery(searchQuery);
        if (!normalizedSearch.isEmpty()) {
            String likePattern = "%" + escapeLike(normalizedSearch) + "%";
            where.append("""
                     AND (
                         LOWER(title) LIKE ? ESCAPE '\\'
                         OR LOWER(description) LIKE ? ESCAPE '\\'
                         OR EXISTS (
                             SELECT 1 FROM task_tags
                             WHERE task_tags.bucket_type = tasks.bucket_type
                               AND task_tags.owner_uuid = tasks.owner_uuid
                               AND task_tags.task_id = tasks.id
                               AND LOWER(task_tags.tag) LIKE ? ESCAPE '\\'
                         )
                     )
                    """);
            parameters.add(likePattern);
            parameters.add(likePattern);
            parameters.add(likePattern);
        }
        return new QueryParts(where.toString(), parameters);
    }

    /**
     * 标准化 GUI 搜索关键词。
     *
     * @param searchQuery 原始搜索关键词
     * @return 小写并去除首尾空白后的关键词
     */
    private String normalizeSearchQuery(String searchQuery) {
        return searchQuery == null ? "" : searchQuery.trim().toLowerCase();
    }

    /**
     * 转义 SQL LIKE 模式中的特殊字符，使搜索语义接近 Java contains。
     *
     * @param value 原始关键词
     * @return 可安全放入 LIKE 模式的关键词
     */
    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 过滤 HUD 查询项目 ID，保持顺序并去重。
     *
     * @param projectIds 原始项目 ID
     * @return 可用于 SQL 参数绑定的项目 ID
     */
    private List<String> sanitizeProjectIds(Collection<String> projectIds) {
        Map<String, Boolean> unique = new LinkedHashMap<>();
        if (projectIds == null) {
            return List.of();
        }
        for (String projectId : projectIds) {
            if (projectId == null || projectId.isEmpty()) {
                continue;
            }
            unique.put(projectId, Boolean.TRUE);
        }
        return new ArrayList<>(unique.keySet());
    }

    /**
     * 绑定 HUD 查询参数。
     *
     * @param statement SQL statement
     * @param parameters 参数列表
     * @throws SQLException 绑定失败时抛出
     */
    private void bindHudQueryParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object value = parameters.get(index);
            if (value instanceof Boolean booleanValue) {
                statement.setBoolean(index + 1, booleanValue);
            } else if (value instanceof Integer integerValue) {
                statement.setInt(index + 1, integerValue);
            } else {
                statement.setString(index + 1, value == null ? "" : value.toString());
            }
        }
    }

    /**
     * 从结果集读取任务并补齐标签。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param resultSet 任务结果集
     * @return 任务对象
     * @throws SQLException 读取失败时抛出
     */
    private Task readTask(Connection connection, String bucketType, String ownerUuid, ResultSet resultSet) throws SQLException {
        CompoundTag taskTag = new CompoundTag();
        taskTag.putString("id", resultSet.getString("id"));
        taskTag.putString("scope", resultSet.getString("scope"));
        putOptionalString(taskTag, "projectId", resultSet.getString("project_id"));
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
        taskTag.put("tags", loadTags(connection, bucketType, ownerUuid, resultSet.getString("id")));
        taskTag.put("subtasks", new ListTag());
        return Task.fromNbt(taskTag);
    }

    /**
     * 读取任务标签列表。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param ownerUuid 桶拥有者
     * @param taskId 任务 ID
     * @return NBT 标签列表
     * @throws SQLException 读取失败时抛出
     */
    private ListTag loadTags(Connection connection, String bucketType, String ownerUuid, String taskId) throws SQLException {
        ListTag tags = new ListTag();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT tag FROM task_tags
                WHERE bucket_type = ? AND owner_uuid = ? AND task_id = ?
                ORDER BY sort_order, tag
                """)) {
            statement.setString(1, bucketType);
            statement.setString(2, ownerUuid);
            statement.setString(3, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("tag", resultSet.getString(1));
                    tags.add(tag);
                }
            }
        }
        return tags;
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
     * 标记 H2 查询不可用并包装为统一存储异常。
     *
     * @param message 异常说明
     * @param cause 原始异常
     * @return 存储不可用异常
     */
    private StorageUnavailableException markUnavailable(String message, Throwable cause) {
        H2StorageAvailability.markUnavailable(connectionProvider.getDatabaseBasePath(), H2StorageAvailability.Reason.QUERY_FAILED, cause == null ? message : cause.getMessage());
        return new StorageUnavailableException(H2StorageAvailability.Reason.QUERY_FAILED, message, cause);
    }

    /**
     * QueryParts 保存构造后的 WHERE 子句和参数。
     */
    private static final class QueryParts {
        private final String whereClause;
        private final List<Object> parameters;

        /**
         * 创建查询片段。
         *
         * @param whereClause WHERE 子句
         * @param parameters 参数列表
         */
        private QueryParts(String whereClause, List<Object> parameters) {
            this.whereClause = whereClause;
            this.parameters = parameters;
        }
    }
}
