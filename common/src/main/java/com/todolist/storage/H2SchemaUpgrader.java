package com.todolist.storage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * H2SchemaUpgrader 负责读取 schema 版本、执行升级前备份并按事务升级到当前版本。
 */
public final class H2SchemaUpgrader {
    private static final String SCHEMA_VERSION_KEY = "schema_version";

    private final H2ConnectionProvider connectionProvider;
    private final H2BackupService backupService;
    private final List<SchemaUpgradeStep> upgradeSteps;

    /**
     * 创建默认 H2 schema 升级器。
     */
    public H2SchemaUpgrader() {
        this(new H2ConnectionProvider(), null, defaultUpgradeSteps());
    }

    /**
     * 创建可注入依赖的 H2 schema 升级器。
     *
     * @param connectionProvider H2 连接提供器
     * @param backupService H2 备份服务
     * @param upgradeSteps 升级步骤列表
     */
    public H2SchemaUpgrader(H2ConnectionProvider connectionProvider, H2BackupService backupService, List<SchemaUpgradeStep> upgradeSteps) {
        this.connectionProvider = connectionProvider;
        this.backupService = backupService == null ? new H2BackupService(connectionProvider, null) : backupService;
        this.upgradeSteps = upgradeSteps == null ? defaultUpgradeSteps() : List.copyOf(upgradeSteps);
    }

    /**
     * 将当前连接上的 schema 升级到代码版本。
     *
     * @param connection H2 连接
     * @throws H2SchemaUpgradeException 升级失败时抛出
     */
    public void upgradeIfNeeded(Connection connection) throws H2SchemaUpgradeException {
        try {
            int targetVersion = parseVersion(H2SchemaInitializer.SCHEMA_VERSION);
            Integer currentVersion = readCurrentVersion(connection);
            if (currentVersion == null) {
                writeSchemaVersion(connection, targetVersion);
                return;
            }
            if (currentVersion > targetVersion) {
                throw new H2SchemaUpgradeException("H2 schema version " + currentVersion + " is newer than supported version " + targetVersion);
            }
            if (currentVersion == targetVersion) {
                return;
            }
            backupService.backupPreparedDatabase(connection, "schema-upgrade-v" + currentVersion + "-to-v" + targetVersion);
            runUpgradeTransaction(connection, currentVersion, targetVersion);
        } catch (IOException | SQLException exception) {
            throw new H2SchemaUpgradeException("Failed to upgrade H2 schema", exception);
        }
    }

    /**
     * 读取当前 schema 版本。
     *
     * @param connection H2 连接
     * @return 当前版本，缺失时返回 null
     * @throws SQLException 查询失败时抛出
     * @throws H2SchemaUpgradeException 版本格式非法时抛出
     */
    private Integer readCurrentVersion(Connection connection) throws SQLException, H2SchemaUpgradeException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT "value" FROM storage_meta WHERE "key" = ?
                """)) {
            statement.setString(1, SCHEMA_VERSION_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return parseVersion(resultSet.getString(1));
            }
        }
    }

    /**
     * 在事务中执行升级步骤并写入目标版本。
     *
     * @param connection H2 连接
     * @param currentVersion 当前版本
     * @param targetVersion 目标版本
     * @throws SQLException SQL 执行失败时抛出
     * @throws H2SchemaUpgradeException 升级步骤失败时抛出
     */
    private void runUpgradeTransaction(Connection connection, int currentVersion, int targetVersion) throws SQLException, H2SchemaUpgradeException {
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (int version = currentVersion; version < targetVersion; version++) {
                runStep(connection, version, version + 1);
            }
            writeSchemaVersion(connection, targetVersion);
            connection.commit();
        } catch (SQLException | H2SchemaUpgradeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    /**
     * 执行指定版本跨度的升级步骤。
     *
     * @param connection H2 连接
     * @param fromVersion 起始版本
     * @param toVersion 目标版本
     * @throws SQLException SQL 执行失败时抛出
     * @throws H2SchemaUpgradeException 缺少升级步骤时抛出
     */
    private void runStep(Connection connection, int fromVersion, int toVersion) throws SQLException, H2SchemaUpgradeException {
        for (SchemaUpgradeStep step : upgradeSteps) {
            if (step.fromVersion() == fromVersion && step.toVersion() == toVersion) {
                step.upgrade(connection);
                return;
            }
        }
        throw new H2SchemaUpgradeException("No H2 schema upgrade step from " + fromVersion + " to " + toVersion);
    }

    /**
     * 写入 schema 版本。
     *
     * @param connection H2 连接
     * @param version schema 版本
     * @throws SQLException 写入失败时抛出
     */
    private void writeSchemaVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO storage_meta KEY("key") VALUES (?, ?, ?)
                """)) {
            statement.setString(1, SCHEMA_VERSION_KEY);
            statement.setString(2, String.valueOf(version));
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * 解析 schema 版本。
     *
     * @param value 原始版本文本
     * @return 整数版本
     * @throws H2SchemaUpgradeException 版本格式非法时抛出
     */
    private int parseVersion(String value) throws H2SchemaUpgradeException {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException exception) {
            throw new H2SchemaUpgradeException("Invalid H2 schema version: " + value, exception);
        }
    }

    /**
     * 创建当前默认升级步骤。
     *
     * @return 默认升级步骤列表
     */
    private static List<SchemaUpgradeStep> defaultUpgradeSteps() {
        return List.of(
                new NoopUpgradeStep(0, 1),
                new TaskSubtaskColumnsUpgradeStep(1, 2),
                new TaskTriggerColumnsUpgradeStep(2, 3)
        );
    }

    /**
     * H2 schema 单步升级定义。
     */
    public interface SchemaUpgradeStep {
        /**
         * 返回起始版本。
         *
         * @return 起始版本
         */
        int fromVersion();

        /**
         * 返回目标版本。
         *
         * @return 目标版本
         */
        int toVersion();

        /**
         * 执行升级步骤。
         *
         * @param connection H2 连接
         * @throws SQLException SQL 执行失败时抛出
         */
        void upgrade(Connection connection) throws SQLException;
    }

    /**
     * 无业务 DDL 的升级步骤，用于补齐 v0 到 v1 的框架迁移。
     */
    private record NoopUpgradeStep(int fromVersion, int toVersion) implements SchemaUpgradeStep {
        /**
         * 执行无操作升级步骤。
         *
         * @param connection H2 连接
         * @throws SQLException SQL 执行失败时抛出
         */
        @Override
        public void upgrade(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }
        }
    }

    /**
     * 为 tasks 表补充子任务列，支持从 v1 升级到 v2。
     */
    private record TaskSubtaskColumnsUpgradeStep(int fromVersion, int toVersion) implements SchemaUpgradeStep {
        /**
         * 执行 tasks 子任务列升级。
         *
         * @param connection H2 连接
         * @throws SQLException SQL 执行失败时抛出
         */
        @Override
        public void upgrade(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS parent_task_id VARCHAR(64)");
                statement.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS subtask_sort_order BIGINT NOT NULL DEFAULT 0");
                statement.execute("UPDATE tasks SET subtask_sort_order = 0 WHERE subtask_sort_order IS NULL");
            }
        }
    }

    /**
     * 为 tasks 表补充事件触发器列，支持从 v2 升级到 v3。
     */
    private record TaskTriggerColumnsUpgradeStep(int fromVersion, int toVersion) implements SchemaUpgradeStep {
        /**
         * 执行 tasks 触发器列升级。
         *
         * @param connection H2 连接
         * @throws SQLException SQL 执行失败时抛出
         */
        @Override
        public void upgrade(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(32)");
                statement.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trigger_target VARCHAR(256)");
                statement.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trigger_count INT NOT NULL DEFAULT 1");
                statement.execute("ALTER TABLE tasks ADD COLUMN IF NOT EXISTS trigger_progress INT NOT NULL DEFAULT 0");
            }
        }
    }
}
