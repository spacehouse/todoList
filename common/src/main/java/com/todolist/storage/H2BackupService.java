package com.todolist.storage;

import com.todolist.TodoConstants;
import com.todolist.config.ModConfig;
import com.todolist.platform.DataPathProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * H2BackupService 负责在维护锁保护下生成 H2 在线备份文件。
 */
public final class H2BackupService {
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** 自动启动备份的文件名前缀（参与保留上限清理）。 */
    private static final String AUTOMATIC_BACKUP_PREFIX = "todolist-h2-";
    /** 恢复前安全备份的文件名前缀（参与保留上限清理）。 */
    private static final String PRE_RESTORE_PREFIX = "pre-restore-";

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
        pruneAutomaticBackups(ModConfig.getInstance().getH2BackupRetentionCount());
        return new BackupResult(backupPath, Files.size(backupPath));
    }

    /**
     * 列出全部备份，按时间倒序（序号 1 为最近一次）。
     *
     * @return 备份条目列表
     * @throws IOException 读取备份目录失败时抛出
     */
    public List<BackupEntry> listBackups() throws IOException {
        Path backupDir = getBackupDirectory();
        if (!Files.isDirectory(backupDir)) {
            return new ArrayList<>();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(backupDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                    .forEach(files::add);
        }
        files.sort(Comparator.comparingLong(H2BackupService::lastModifiedMillis).reversed());
        List<BackupEntry> entries = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            Path path = files.get(index);
            entries.add(new BackupEntry(index + 1, path, Files.size(path), lastModifiedMillis(path)));
        }
        return entries;
    }

    /**
     * 按序号或名称解析备份文件。
     *
     * @param target 序号、"latest" 或备份文件名（可省略 .zip）
     * @return 备份文件路径；无法解析时返回 null
     * @throws IOException 读取备份目录失败时抛出
     */
    public Path resolveBackup(String target) throws IOException {
        List<BackupEntry> entries = listBackups();
        if (entries.isEmpty()) {
            return null;
        }
        if (target == null || target.isBlank() || "latest".equalsIgnoreCase(target.trim())) {
            return entries.get(0).path();
        }
        String trimmed = target.trim();
        if (trimmed.matches("\\d+")) {
            int index = Integer.parseInt(trimmed);
            if (index >= 1 && index <= entries.size()) {
                return entries.get(index - 1).path();
            }
            return null;
        }
        String fileName = trimmed.toLowerCase(Locale.ROOT).endsWith(".zip") ? trimmed : trimmed + ".zip";
        for (BackupEntry entry : entries) {
            if (entry.path().getFileName().toString().equalsIgnoreCase(fileName)) {
                return entry.path();
            }
        }
        return null;
    }

    /**
     * 按上限清理自动产生的备份（启动备份与恢复前安全备份），保留最近 keep 份。
     *
     * <p>手动命名备份与 schema 升级备份属于玩家/系统的显式恢复点，不参与自动清理。
     *
     * @param keep 保留份数，最小为 1
     * @return 被删除的备份文件
     * @throws IOException 删除失败时抛出
     */
    public List<Path> pruneAutomaticBackups(int keep) throws IOException {
        int limit = Math.max(1, keep);
        List<Path> automatic = new ArrayList<>();
        for (BackupEntry entry : listBackups()) {
            String name = entry.path().getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.startsWith(AUTOMATIC_BACKUP_PREFIX) || name.startsWith(PRE_RESTORE_PREFIX)) {
                automatic.add(entry.path());
            }
        }
        List<Path> deleted = new ArrayList<>();
        for (int index = limit; index < automatic.size(); index++) {
            Path path = automatic.get(index);
            try {
                Files.deleteIfExists(path);
                deleted.add(path);
            } catch (IOException exception) {
                TodoConstants.LOGGER.warn("Failed to prune H2 backup {}", path, exception);
            }
        }
        return deleted;
    }

    /**
     * 返回备份目录。
     *
     * @return 备份目录路径
     */
    public static Path getBackupDirectory() {
        return DataPathProvider.getTodoDataDir().resolve("backups").toAbsolutePath().normalize();
    }

    /**
     * 读取文件最后修改时间。
     *
     * @param path 文件路径
     * @return 毫秒时间戳；读取失败时返回 0
     */
    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    /**
     * 把数据库文件打包为备份格式（zip 内仅含 todolist.mv.db）。
     *
     * @param databaseFile 数据库文件
     * @param baseName 备份基础名（不含扩展名）
     * @return 生成的备份文件路径
     * @throws IOException 打包失败时抛出
     */
    static Path zipDatabaseFile(Path databaseFile, String baseName) throws IOException {
        Path backupDir = getBackupDirectory();
        Files.createDirectories(backupDir);
        Path target = backupDir.resolve(sanitizeBackupFileName(baseName) + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            output.putNextEntry(new ZipEntry(databaseFile.getFileName().toString()));
            Files.copy(databaseFile, output);
            output.closeEntry();
        }
        return target;
    }

    /**
     * 规范化备份文件基础名。
     *
     * @param baseName 原始基础名
     * @return 安全文件名
     */
    private static String sanitizeBackupFileName(String baseName) {
        String sanitized = String.valueOf(baseName).replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "todolist-backup" : sanitized;
    }

    /**
     * H2 备份条目。
     *
     * @param index 序号（1 为最近一次）
     * @param path 备份文件路径
     * @param sizeBytes 文件大小
     * @param modifiedAtMillis 最后修改时间
     */
    public record BackupEntry(int index, Path path, long sizeBytes, long modifiedAtMillis) {
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
