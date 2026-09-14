package com.todolist.storage;

import com.todolist.TodoConstants;
import com.todolist.config.ModConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * H2PendingRestore 负责「恢复到指定备份」的暂存与落地。
 *
 * <p>备份 zip 里装的是完整的 {@code todolist.mv.db}，替换数据库文件必须在数据库未被打开时进行，
 * 因此恢复分两步：
 * <ol>
 *     <li>命令执行时**暂存**：校验备份可读（能打开并读到任务表），把库文件解压到
 *         {@code backups/restore-pending/}，并写入标记文件；</li>
 *     <li>下次启动、数据库被打开之前**落地**：先把当前库打包成 {@code pre-restore-*.zip} 安全备份，
 *         再用暂存的库文件覆盖当前库，最后清理暂存目录。</li>
 * </ol>
 * 这样既不需要玩家手工拷贝文件，也不会出现"数据库正在使用却替换文件"的损坏风险。
 */
public final class H2PendingRestore {

    private static final String PENDING_DIR_NAME = "restore-pending";
    private static final String DATABASE_FILE_NAME = "todolist.mv.db";
    private static final String MARKER_FILE_NAME = "restore-pending.txt";
    private static final String STAGING_DIR_NAME = "staging";
    private static final DateTimeFormatter PRE_RESTORE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicBoolean APPLY_ATTEMPTED = new AtomicBoolean(false);

    /**
     * 工具类不需要实例化。
     */
    private H2PendingRestore() {
    }

    /**
     * 返回暂存目录。
     *
     * @return 暂存目录路径
     */
    public static Path getPendingDirectory() {
        return H2BackupService.getBackupDirectory().resolve(PENDING_DIR_NAME);
    }

    /**
     * 返回暂存的数据库文件路径。
     *
     * @return 暂存数据库文件路径
     */
    public static Path getPendingDatabaseFile() {
        return getPendingDirectory().resolve(DATABASE_FILE_NAME);
    }

    /**
     * 返回暂存标记文件路径。
     *
     * @return 标记文件路径
     */
    private static Path getMarkerFile() {
        return getPendingDirectory().resolve(MARKER_FILE_NAME);
    }

    /**
     * 判断当前是否存在待应用的恢复。
     *
     * @return 存在时返回 true
     */
    public static boolean hasPending() {
        return Files.isRegularFile(getPendingDatabaseFile());
    }

    /**
     * 读取待恢复备份的来源说明。
     *
     * @return 形如 {@code "todolist-h2-20260915-004500.zip"}；无标记时返回空串
     */
    public static String readPendingSource() {
        Path marker = getMarkerFile();
        if (!Files.isRegularFile(marker)) {
            return "";
        }
        try {
            return Files.readString(marker).trim();
        } catch (IOException exception) {
            return "";
        }
    }

    /**
     * 校验备份并暂存一次恢复。
     *
     * @param backupZip 备份文件
     * @return 暂存结果
     * @throws IOException 备份不可读或暂存失败时抛出
     */
    public static PendingRestore stageRestore(Path backupZip) throws IOException {
        if (backupZip == null || !Files.isRegularFile(backupZip)) {
            throw new IOException("Backup file not found: " + backupZip);
        }
        Path pendingDir = getPendingDirectory();
        Path stagingDir = pendingDir.resolve(STAGING_DIR_NAME);
        deleteDirectory(stagingDir);
        Files.createDirectories(stagingDir);

        Path extracted = extractDatabaseFile(backupZip, stagingDir);
        validateDatabaseFile(extracted);

        Path pendingFile = getPendingDatabaseFile();
        Files.createDirectories(pendingDir);
        Files.copy(extracted, pendingFile, StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(getMarkerFile(), backupZip.getFileName().toString() + System.lineSeparator());
        deleteDirectory(stagingDir);

        long sizeBytes = Files.size(pendingFile);
        TodoConstants.LOGGER.info("Staged H2 restore from {} ({} bytes)", backupZip.getFileName(), sizeBytes);
        return new PendingRestore(backupZip, pendingFile, sizeBytes);
    }

    /**
     * 在数据库被打开之前应用待恢复。
     *
     * <p>只在本次进程内尝试一次；没有待恢复或当前库仍在使用时直接返回 null。
     *
     * @param databasePath H2 数据库基础路径（不含 .mv.db）
     * @return 应用结果；未应用时返回 null
     * @throws IOException 覆盖数据库失败时抛出
     */
    public static AppliedRestore applyIfPending(Path databasePath) throws IOException {
        if (!hasPending() || !APPLY_ATTEMPTED.compareAndSet(false, true)) {
            return null;
        }
        Path pendingFile = getPendingDatabaseFile();
        Path target = databasePath.resolveSibling(databasePath.getFileName() + ".mv.db");
        String source = readPendingSource();

        Path safetyBackup = null;
        if (Files.isRegularFile(target)) {
            String baseName = "pre-restore-" + LocalDateTime.now().format(PRE_RESTORE_TIME_FORMAT);
            safetyBackup = H2BackupService.zipDatabaseFile(target, baseName);
        }
        Files.createDirectories(target.getParent());
        Files.copy(pendingFile, target, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(pendingFile);
        Files.deleteIfExists(getMarkerFile());
        deleteDirectory(getPendingDirectory().resolve(STAGING_DIR_NAME));

        try {
            new H2BackupService().pruneAutomaticBackups(ModConfig.getInstance().getH2BackupRetentionCount());
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to prune backups after H2 restore", exception);
        }
        TodoConstants.LOGGER.info("Applied pending H2 restore from {} to {} (safety backup: {})",
                source, target, safetyBackup);
        return new AppliedRestore(source, target, safetyBackup);
    }

    /**
     * 清理测试与运行期残留的暂存状态。
     */
    public static void resetForTests() {
        APPLY_ATTEMPTED.set(false);
    }

    /**
     * 从备份 zip 中解压数据库文件。
     *
     * @param backupZip   备份文件
     * @param stagingDir  解压目录
     * @return 解压出的数据库文件
     * @throws IOException 备份格式不正确时抛出
     */
    private static Path extractDatabaseFile(Path backupZip, Path stagingDir) throws IOException {
        try (ZipFile zip = new ZipFile(backupZip.toFile())) {
            ZipEntry target = null;
            List<ZipEntry> entries = new ArrayList<>();
            zip.stream().forEach(entries::add);
            for (ZipEntry entry : entries) {
                if (!entry.isDirectory()
                        && entry.getName().toLowerCase(Locale.ROOT).endsWith(".mv.db")) {
                    target = entry;
                    break;
                }
            }
            if (target == null) {
                throw new IOException("Backup does not contain an H2 database file: " + backupZip);
            }
            Path output = stagingDir.resolve(DATABASE_FILE_NAME);
            try (InputStream input = zip.getInputStream(target)) {
                Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            }
            return output;
        }
    }

    /**
     * 校验解压出的数据库可读：能打开并读到任务表。
     *
     * @param databaseFile 数据库文件
     * @throws IOException 数据库无法读取时抛出
     */
    private static void validateDatabaseFile(Path databaseFile) throws IOException {
        String base = databaseFile.toAbsolutePath().normalize().toString();
        if (base.toLowerCase(Locale.ROOT).endsWith(".mv.db")) {
            base = base.substring(0, base.length() - ".mv.db".length());
        }
        String url = "jdbc:h2:file:" + base.replace('\\', '/')
                + ";AUTO_SERVER=FALSE;DATABASE_TO_UPPER=FALSE;TRACE_LEVEL_FILE=0";
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException exception) {
            throw new IOException("H2 driver is not available on the runtime classpath", exception);
        }
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM tasks")) {
                resultSet.next();
                TodoConstants.LOGGER.info("Validated H2 backup {}: {} tasks",
                        databaseFile.getFileName(), resultSet.getLong(1));
            }
            // 校验用的连接会把数据库文件锁住，必须显式 SHUTDOWN 才能释放，否则后续复制会失败
            try (Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException ignored) {
                // SHUTDOWN 会主动断开连接，驱动可能抛出「连接已关闭」，属预期行为
            }
        } catch (SQLException exception) {
            throw new IOException("Backup database is not readable: " + databaseFile.getFileName(), exception);
        }
    }

    /**
     * 递归删除目录（不跟随符号链接）。
     *
     * @param directory 目录
     * @throws IOException 删除失败时抛出
     */
    private static void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            List<Path> paths = new ArrayList<>();
            stream.forEach(paths::add);
            for (int index = paths.size() - 1; index >= 0; index--) {
                Files.deleteIfExists(paths.get(index));
            }
        }
    }

    /**
     * 暂存结果。
     *
     * @param sourceBackup 来源备份文件
     * @param pendingFile  暂存的数据库文件
     * @param sizeBytes    暂存文件大小
     */
    public record PendingRestore(Path sourceBackup, Path pendingFile, long sizeBytes) {
    }

    /**
     * 落地结果。
     *
     * @param sourceDescription 来源备份名
     * @param databaseFile      被覆盖的数据库文件
     * @param safetyBackup      恢复前安全备份；不存在时为空
     */
    public record AppliedRestore(String sourceDescription, Path databaseFile, Path safetyBackup) {
    }
}
