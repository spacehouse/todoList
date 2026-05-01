package com.todolist.storage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * H2HealthCheckService 负责执行 H2 运维健康检查。
 */
public final class H2HealthCheckService {
    private final H2ConnectionProvider connectionProvider;
    private final H2StorageBootstrap bootstrap;

    /**
     * 创建默认 H2 健康检查服务。
     */
    public H2HealthCheckService() {
        this(new H2ConnectionProvider(), new H2StorageBootstrap());
    }

    /**
     * 创建可注入依赖的 H2 健康检查服务。
     *
     * @param connectionProvider H2 连接提供器
     * @param bootstrap H2 启动器
     */
    public H2HealthCheckService(H2ConnectionProvider connectionProvider, H2StorageBootstrap bootstrap) {
        this.connectionProvider = connectionProvider;
        this.bootstrap = bootstrap;
    }

    /**
     * 执行 H2 健康检查。
     *
     * @return 健康检查结果
     * @throws IOException 初始化或查询失败时抛出
     */
    public HealthSnapshot check() throws IOException {
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             Statement statement = connection.createStatement()) {
            int pingValue = queryInt(statement, "SELECT 1");
            String schemaVersion = queryString(statement, "SELECT \"value\" FROM storage_meta WHERE \"key\" = 'schema_version'");
            long taskCount = queryLong(statement, "SELECT COUNT(*) FROM tasks");
            boolean healthy = pingValue == 1 && schemaVersion != null && !schemaVersion.isBlank();
            return new HealthSnapshot(healthy, schemaVersion, taskCount);
        } catch (SQLException exception) {
            throw new IOException("Failed to check H2 health", exception);
        }
    }

    /**
     * 查询单个 int 值。
     *
     * @param statement SQL statement
     * @param sql 查询语句
     * @return 查询结果
     * @throws SQLException 查询失败时抛出
     */
    private int queryInt(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    /**
     * 查询单个 long 值。
     *
     * @param statement SQL statement
     * @param sql 查询语句
     * @return 查询结果
     * @throws SQLException 查询失败时抛出
     */
    private long queryLong(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    /**
     * 查询单个字符串值。
     *
     * @param statement SQL statement
     * @param sql 查询语句
     * @return 查询结果
     * @throws SQLException 查询失败时抛出
     */
    private String queryString(Statement statement, String sql) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : "";
        }
    }

    /**
     * H2 健康检查结果。
     */
    public static final class HealthSnapshot {
        private final boolean healthy;
        private final String schemaVersion;
        private final long taskCount;

        /**
         * 创建 H2 健康检查结果。
         *
         * @param healthy 是否健康
         * @param schemaVersion schema 版本
         * @param taskCount 任务数量
         */
        private HealthSnapshot(boolean healthy, String schemaVersion, long taskCount) {
            this.healthy = healthy;
            this.schemaVersion = schemaVersion == null ? "" : schemaVersion;
            this.taskCount = taskCount;
        }

        /**
         * 返回 H2 是否健康。
         *
         * @return 健康时返回 true
         */
        public boolean isHealthy() {
            return healthy;
        }

        /**
         * 返回 schema 版本。
         *
         * @return schema 版本
         */
        public String getSchemaVersion() {
            return schemaVersion;
        }

        /**
         * 返回任务数量。
         *
         * @return 任务数量
         */
        public long getTaskCount() {
            return taskCount;
        }
    }
}
