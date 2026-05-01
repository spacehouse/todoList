package com.todolist.storage;

import com.todolist.platform.DataPathProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * H2BackupService 负责在维护锁保护下生成 H2 在线备份文件。
 */
public final class H2BackupService {
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final H2ConnectionProvider connectionProvider;
    private final H2StorageBootstrap bootstrap;

    /**
     * 创建默认 H2 备份服务。
     */
    public H2BackupService() {
        this(new H2ConnectionProvider(), new H2StorageBootstrap());
    }

    /**
     * 创建可注入依赖的 H2 备份服务。
     *
     * @param connectionProvider H2 连接提供器
     * @param bootstrap H2 启动器
     */
    public H2BackupService(H2ConnectionProvider connectionProvider, H2StorageBootstrap bootstrap) {
        this.connectionProvider = connectionProvider;
        this.bootstrap = bootstrap;
    }

    /**
     * 创建 H2 备份文件。
     *
     * @param requestedName 用户指定备份名，可为空
     * @return 备份结果
     * @throws IOException 备份文件创建失败时抛出
     */
    public BackupResult backup(String requestedName) throws IOException {
        if (bootstrap == null) {
            throw new IOException("H2 bootstrap is required for public backup");
        }
        try (H2MaintenanceLock.MaintenanceToken ignored = H2MaintenanceLock.enter("backup")) {
            bootstrap.ensureReady();
            return backupPreparedDatabase(requestedName);
        }
    }

    /**
     * 为已经完成初始化的 H2 数据库创建备份文件。
     *
     * @param requestedName 用户指定备份名，可为空
     * @return 备份结果
     * @throws IOException 备份文件创建失败时抛出
     */
    BackupResult backupPreparedDatabase(String requestedName) throws IOException {
        try (Connection connection = connectionProvider.openConnection()) {
            return backupPreparedDatabase(connection, requestedName);
        } catch (SQLException exception) {
            throw new IOException("Failed to create H2 backup", exception);
        }
    }

    /**
     * 使用指定连接为已初始化的 H2 数据库创建备份文件。
     *
     * @param connection H2 连接
     * @param requestedName 用户指定备份名，可为空
     * @return 备份结果
     * @throws IOException 备份文件创建失败时抛出
     */
    BackupResult backupPreparedDatabase(Connection connection, String requestedName) throws IOException {
        Path backupPath = nextBackupPath(requestedName);
        Files.createDirectories(backupPath.getParent());
        try (Statement statement = connection.createStatement()) {
            statement.execute("BACKUP TO '" + toSqlPath(backupPath) + "'");
        } catch (SQLException exception) {
            throw new IOException("Failed to create H2 backup", exception);
        }
        return new BackupResult(backupPath, Files.size(backupPath));
    }

    /**
     * 计算不会覆盖现有文件的备份路径。
     *
     * @param requestedName 用户指定备份名
     * @return 备份路径
     * @throws IOException 查询文件存在状态失败时抛出
     */
    private Path nextBackupPath(String requestedName) throws IOException {
        Path backupDir = DataPathProvider.getTodoDataDir().resolve("backups").toAbsolutePath().normalize();
        String baseName = sanitizeBackupName(requestedName);
        Path candidate = backupDir.resolve(baseName + ".zip");
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = backupDir.resolve(baseName + "-" + counter + ".zip");
            counter++;
        }
        return candidate;
    }

    /**
     * 规范化备份文件名。
     *
     * @param requestedName 用户指定备份名
     * @return 安全文件名，不含扩展名
     */
    private String sanitizeBackupName(String requestedName) {
        String fallbackName = "todolist-h2-" + LocalDateTime.now().format(BACKUP_TIME_FORMAT);
        if (requestedName == null || requestedName.isBlank()) {
            return fallbackName;
        }
        String trimmed = requestedName.trim();
        if (trimmed.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        String sanitized = trimmed.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return fallbackName;
        }
        return sanitized;
    }

    /**
     * 转换为 H2 SQL 可接受的路径字面量。
     *
     * @param path 备份文件路径
     * @return SQL 路径字符串
     */
    private String toSqlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/").replace("'", "''");
    }

    /**
     * H2 备份结果。
     */
    public static final class BackupResult {
        private final Path backupPath;
        private final long sizeBytes;

        /**
         * 创建 H2 备份结果。
         *
         * @param backupPath 备份文件路径
         * @param sizeBytes 文件大小
         */
        private BackupResult(Path backupPath, long sizeBytes) {
            this.backupPath = backupPath;
            this.sizeBytes = sizeBytes;
        }

        /**
         * 返回备份文件路径。
         *
         * @return 备份文件路径
         */
        public Path getBackupPath() {
            return backupPath;
        }

        /**
         * 返回备份文件大小。
         *
         * @return 文件大小，单位字节
         */
        public long getSizeBytes() {
            return sizeBytes;
        }
    }
}
