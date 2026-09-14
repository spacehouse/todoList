package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * H2MaintenanceBackupTestMain 覆盖 M3-A 的维护锁与 H2 在线备份行为。
 */
public final class H2MaintenanceBackupTestMain {
    private H2MaintenanceBackupTestMain() {
    }

    /**
     * 执行 H2 维护与备份自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldCreateBackupZipWithCurrentData", H2MaintenanceBackupTestMain::shouldCreateBackupZipWithCurrentData);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldCreateBackupOnStartWhenEnabled", H2MaintenanceBackupTestMain::shouldCreateBackupOnStartWhenEnabled);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldReportH2Health", H2MaintenanceBackupTestMain::shouldReportH2Health);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldUpgradeOldSchemaWithBackup", H2MaintenanceBackupTestMain::shouldUpgradeOldSchemaWithBackup);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldMarkUnavailableWhenSchemaUpgradeFails", H2MaintenanceBackupTestMain::shouldMarkUnavailableWhenSchemaUpgradeFails);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldRejectWritesDuringMaintenance", H2MaintenanceBackupTestMain::shouldRejectWritesDuringMaintenance);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldRejectConcurrentMaintenance", H2MaintenanceBackupTestMain::shouldRejectConcurrentMaintenance);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldListAndPruneAutomaticBackups", H2MaintenanceBackupTestMain::shouldListAndPruneAutomaticBackups);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldStageAndApplyPendingRestore", H2MaintenanceBackupTestMain::shouldStageAndApplyPendingRestore);
        GuiTestSupport.runTestCase("H2MaintenanceBackupTestMain.shouldRejectInvalidBackupWhenStaging", H2MaintenanceBackupTestMain::shouldRejectInvalidBackupWhenStaging);
    }

    /**
     * 验证备份列表按时间倒序、序号从 1 开始，并且保留上限只清理自动备份。
     */
    private static void shouldListAndPruneAutomaticBackups() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-backup-retention-");
            ModConfig.getInstance().setH2BackupOnStart(false);
            Path backupDir = H2BackupService.getBackupDirectory();
            Files.createDirectories(backupDir);
            writeDummyBackup(backupDir.resolve("todolist-h2-20260101-000001.zip"), 1_700_000_000_000L);
            writeDummyBackup(backupDir.resolve("todolist-h2-20260101-000002.zip"), 1_700_000_001_000L);
            writeDummyBackup(backupDir.resolve("todolist-h2-20260101-000003.zip"), 1_700_000_002_000L);
            writeDummyBackup(backupDir.resolve("manual-keep.zip"), 1_700_000_003_000L);
            writeDummyBackup(backupDir.resolve("schema-upgrade-v2-to-v3.zip"), 1_700_000_004_000L);

            H2BackupService service = new H2BackupService();
            List<H2BackupService.BackupEntry> entries = service.listBackups();

            GuiTestSupport.assertEquals(5, entries.size(), "应列出全部 .zip 备份");
            GuiTestSupport.assertEquals("schema-upgrade-v2-to-v3.zip",
                    entries.get(0).path().getFileName().toString(), "最新的备份应排在第 1 位");
            GuiTestSupport.assertEquals(1, entries.get(0).index(), "序号应从 1 开始");
            GuiTestSupport.assertEquals("todolist-h2-20260101-000001.zip",
                    entries.get(entries.size() - 1).path().getFileName().toString(), "最旧的备份应排在最后");

            GuiTestSupport.assertEquals("schema-upgrade-v2-to-v3.zip",
                    service.resolveBackup("latest").getFileName().toString(), "latest 应解析到最近的备份");
            GuiTestSupport.assertEquals("todolist-h2-20260101-000003.zip",
                    service.resolveBackup("3").getFileName().toString(), "序号 3 应解析到第 3 条");
            GuiTestSupport.assertEquals("manual-keep.zip",
                    service.resolveBackup("manual-keep").getFileName().toString(), "省略扩展名也应能解析");
            GuiTestSupport.assertTrue(service.resolveBackup("no-such-backup") == null,
                    "不存在的备份应解析为 null");

            List<Path> deleted = service.pruneAutomaticBackups(2);

            GuiTestSupport.assertEquals(1, deleted.size(), "保留 2 份自动备份时只应删除 1 份");
            GuiTestSupport.assertEquals("todolist-h2-20260101-000001.zip",
                    deleted.get(0).getFileName().toString(), "应删除最早的自动备份");
            GuiTestSupport.assertTrue(Files.exists(backupDir.resolve("manual-keep.zip")),
                    "手动命名的备份不应被自动清理");
            GuiTestSupport.assertTrue(Files.exists(backupDir.resolve("schema-upgrade-v2-to-v3.zip")),
                    "schema 升级备份不应被自动清理");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 备份列表与保留上限时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证恢复流程：暂存备份 → 数据库关闭后落地 → 数据回到备份时的状态，并留下恢复前安全备份。
     */
    private static void shouldStageAndApplyPendingRestore() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-restore-");
            ModConfig.getInstance().setH2BackupOnStart(false);
            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2TaskStore store = new H2TaskStore();
            new H2StorageBootstrap().ensureReady();
            saveLocalTask(store, "backup-task-id", "snapshot-state");
            H2BackupService.BackupResult snapshot = new H2BackupService().backup("snapshot");
            saveLocalTask(store, "backup-task-id", "later-state");
            GuiTestSupport.assertEquals("later-state", store.loadLocalTasks().get(0).getTitle(),
                    "覆盖保存后应读到新状态");

            H2PendingRestore.stageRestore(snapshot.getBackupPath());

            GuiTestSupport.assertTrue(H2PendingRestore.hasPending(), "暂存后应存在待恢复");
            GuiTestSupport.assertEquals("snapshot.zip", H2PendingRestore.readPendingSource(),
                    "标记文件应记录来源备份名");

            shutdownDatabase(provider);
            H2StorageBootstrap.resetAllForTests();
            H2PendingRestore.resetForTests();

            H2PendingRestore.AppliedRestore applied = H2PendingRestore.applyIfPending(provider.getDatabaseBasePath());

            GuiTestSupport.assertTrue(applied != null, "存在待恢复时应完成落地");
            GuiTestSupport.assertTrue(applied != null && applied.safetyBackup() != null
                    && Files.exists(applied.safetyBackup()), "落地前应生成 pre-restore 安全备份");
            GuiTestSupport.assertFalse(H2PendingRestore.hasPending(), "落地后应清理暂存状态");

            new H2StorageBootstrap().ensureReady();

            GuiTestSupport.assertEquals("snapshot-state", new H2TaskStore().loadLocalTasks().get(0).getTitle(),
                    "恢复后应回到备份时的数据");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 恢复流程时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证暂存恢复时会拒绝不含数据库文件（或无法打开）的备份。
     */
    private static void shouldRejectInvalidBackupWhenStaging() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-restore-invalid-");
            Path backupDir = H2BackupService.getBackupDirectory();
            Files.createDirectories(backupDir);
            Path broken = backupDir.resolve("broken.zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(broken))) {
                output.putNextEntry(new ZipEntry("readme.txt"));
                output.write("not a database".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }

            try {
                H2PendingRestore.stageRestore(broken);
                throw new AssertionError("不含数据库文件的备份应被拒绝");
            } catch (IOException expected) {
                GuiTestSupport.assertFalse(H2PendingRestore.hasPending(), "校验失败不应留下待恢复状态");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 恢复校验时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 写入指定标题的本地任务。
     *
     * @param store H2 任务存储
     * @param taskId 任务 ID
     * @param title 任务标题
     * @throws Exception 保存失败时抛出
     */
    private static void saveLocalTask(H2TaskStore store, String taskId, String title) throws Exception {
        Task task = new Task(title, "");
        task.setId(taskId);
        store.saveLocalTasks(List.of(task));
    }

    /**
     * 关闭当前 H2 数据库连接，释放数据库文件。
     *
     * @param provider H2 连接提供器
     * @throws Exception 执行失败时抛出
     */
    private static void shutdownDatabase(H2ConnectionProvider provider) throws Exception {
        try (Connection connection = provider.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException exception) {
            // SHUTDOWN 会主动断开连接，驱动可能抛出「连接已关闭」，属预期行为
        }
    }

    /**
     * 写入一个用于列表/清理验证的占位备份 zip，并指定修改时间。
     *
     * @param path 备份文件路径
     * @param modifiedAtMillis 期望的最后修改时间
     * @throws Exception 写入失败时抛出
     */
    private static void writeDummyBackup(Path path, long modifiedAtMillis) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("todolist.mv.db"));
            output.write(new byte[] {1, 2, 3});
            output.closeEntry();
        }
        Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.fromMillis(modifiedAtMillis));
    }

    /**
     * 验证备份服务会生成非空 H2 ZIP 备份文件。
     */
    private static void shouldCreateBackupZipWithCurrentData() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-backup-");
            H2TaskStore store = new H2TaskStore();
            Task task = new Task("backup-task", "");
            task.setId("backup-task-id");
            store.saveLocalTasks(List.of(task));

            H2BackupService.BackupResult result = new H2BackupService().backup("named-backup");

            GuiTestSupport.assertTrue(Files.exists(result.getBackupPath()), "H2 备份文件应存在");
            GuiTestSupport.assertTrue(result.getSizeBytes() > 0, "H2 备份文件不应为空");
            try (ZipFile zipFile = new ZipFile(result.getBackupPath().toFile())) {
                GuiTestSupport.assertTrue(zipFile.size() > 0, "H2 备份 ZIP 应包含数据库文件");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 备份文件时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证开启启动备份配置后，H2 初始化完成会创建一次备份。
     */
    private static void shouldCreateBackupOnStartWhenEnabled() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-startup-backup-");
            ModConfig.getInstance().setH2BackupOnStart(true);

            new H2StorageBootstrap().ensureReady();

            GuiTestSupport.assertTrue(hasBackupWithPrefix("startup-"), "启用 h2BackupOnStart 后应生成启动备份");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 启动备份时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证健康检查能读取 schema 版本和任务数量。
     */
    private static void shouldReportH2Health() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-health-");
            H2TaskStore store = new H2TaskStore();
            Task task = new Task("health-task", "");
            task.setId("health-task-id");
            store.saveLocalTasks(List.of(task));

            H2HealthCheckService.HealthSnapshot snapshot = new H2HealthCheckService().check();

            GuiTestSupport.assertTrue(snapshot.isHealthy(), "H2 健康检查应通过");
            GuiTestSupport.assertEquals(H2SchemaInitializer.SCHEMA_VERSION, snapshot.getSchemaVersion(), "H2 schema 版本应可读");
            GuiTestSupport.assertEquals(1L, snapshot.getTaskCount(), "H2 健康检查应统计任务数量");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 健康检查时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证旧 schema 版本会先备份再升级到当前版本。
     */
    private static void shouldUpgradeOldSchemaWithBackup() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-schema-upgrade-");
            H2ConnectionProvider provider = new H2ConnectionProvider();
            new H2StorageBootstrap().ensureReady();
            writeSchemaVersion(provider, "0");
            H2StorageBootstrap.resetDatabaseState(provider.getDatabaseBasePath());

            new H2StorageBootstrap().ensureReady();

            GuiTestSupport.assertEquals(H2SchemaInitializer.SCHEMA_VERSION, readSchemaVersion(provider), "旧 schema 应升级到当前版本");
            GuiTestSupport.assertTrue(
                    hasBackupWithPrefix("schema-upgrade-v0-to-v" + H2SchemaInitializer.SCHEMA_VERSION),
                    "schema 升级前应创建备份"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 schema 升级备份时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证 schema 升级失败时会标记 H2 不可用且不推进版本。
     */
    private static void shouldMarkUnavailableWhenSchemaUpgradeFails() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-schema-upgrade-fail-");
            H2ConnectionProvider provider = new H2ConnectionProvider();
            new H2StorageBootstrap().ensureReady();
            writeSchemaVersion(provider, "0");
            H2StorageBootstrap.resetDatabaseState(provider.getDatabaseBasePath());
            H2SchemaUpgrader failingUpgrader = new H2SchemaUpgrader(
                    provider,
                    new H2BackupService(provider, null),
                    List.of(new FailingUpgradeStep())
            );
            H2StorageBootstrap failingBootstrap = new H2StorageBootstrap(
                    provider,
                    new H2SchemaInitializer(failingUpgrader),
                    new H2LegacyMigrationReader(),
                    new H2LegacyMigrator(),
                    null
            );

            try {
                failingBootstrap.ensureReady();
                throw new AssertionError("schema 升级失败时应拒绝 H2 启动");
            } catch (StorageUnavailableException exception) {
                GuiTestSupport.assertEquals(H2StorageAvailability.Reason.SCHEMA_UPGRADE_FAILED, exception.getReason(), "升级失败原因应为 SCHEMA_UPGRADE_FAILED");
            }
            GuiTestSupport.assertEquals("0", readSchemaVersion(provider), "升级失败不应推进 schema 版本");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 schema 升级失败时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证维护锁活动期间普通 H2 写入会被拒绝。
     */
    private static void shouldRejectWritesDuringMaintenance() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-maintenance-write-");
            H2TaskStore store = new H2TaskStore();
            try (H2MaintenanceLock.MaintenanceToken ignored = H2MaintenanceLock.enter("test-lock")) {
                try {
                    store.saveLocalTasks(List.of(new Task("blocked", "")));
                    throw new AssertionError("维护期间 H2 写入应被拒绝");
                } catch (StorageUnavailableException exception) {
                    GuiTestSupport.assertEquals(H2StorageAvailability.Reason.MAINTENANCE, exception.getReason(), "维护拒绝原因应为 MAINTENANCE");
                }
            }
            store.saveLocalTasks(List.of(new Task("allowed", "")));
            GuiTestSupport.assertEquals("allowed", store.loadLocalTasks().get(0).getTitle(), "维护锁释放后应恢复写入");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 维护写入拒绝时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证维护锁串行化备份操作。
     */
    private static void shouldRejectConcurrentMaintenance() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-maintenance-concurrent-");
            new H2StorageBootstrap().ensureReady();
            try (H2MaintenanceLock.MaintenanceToken ignored = H2MaintenanceLock.enter("outer")) {
                try {
                    new H2BackupService().backup("blocked-backup");
                    throw new AssertionError("已有维护锁时备份应被拒绝");
                } catch (StorageUnavailableException exception) {
                    GuiTestSupport.assertEquals(H2StorageAvailability.Reason.MAINTENANCE, exception.getReason(), "并发维护拒绝原因应为 MAINTENANCE");
                }
            }
            GuiTestSupport.assertFalse(H2MaintenanceLock.getStatusSnapshot().isActive(), "维护锁释放后状态应为空闲");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 并发维护拒绝时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 准备独立测试游戏目录。
     *
     * @param prefix 临时目录前缀
     * @return 临时游戏目录
     * @throws Exception 准备失败时抛出
     */
    private static Path prepareTempGameDir(String prefix) throws Exception {
        Path tempGameDir = Files.createTempDirectory(prefix);
        DataPathProvider.setGameDirSupplier(() -> tempGameDir);
        DataPathProvider.resetStorageNamespace();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        H2StorageBootstrap.resetAllForTests();
        return tempGameDir;
    }

    /**
     * 清理测试状态和临时目录。
     *
     * @param tempGameDir 临时游戏目录
     */
    private static void cleanup(Path tempGameDir) {
        try {
            ModConfig.getInstance().setH2BackupOnStart(false);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
            H2StorageBootstrap.resetAllForTests();
            if (tempGameDir == null || !Files.exists(tempGameDir)) {
                return;
            }
            try (var paths = Files.walk(tempGameDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法删除 H2 维护测试临时文件: " + path, exception);
                    }
                });
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理 H2 维护测试临时目录", exception);
        }
    }

    /**
     * 写入测试用 schema 版本。
     *
     * @param provider H2 连接提供器
     * @param version schema 版本
     * @throws Exception 写入失败时抛出
     */
    private static void writeSchemaVersion(H2ConnectionProvider provider, String version) throws Exception {
        try (Connection connection = provider.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     MERGE INTO storage_meta KEY("key") VALUES ('schema_version', ?, ?)
                     """)) {
            statement.setString(1, version);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * 读取测试库中的 schema 版本。
     *
     * @param provider H2 连接提供器
     * @return schema 版本
     * @throws Exception 读取失败时抛出
     */
    private static String readSchemaVersion(H2ConnectionProvider provider) throws Exception {
        try (Connection connection = provider.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT "value" FROM storage_meta WHERE "key" = 'schema_version'
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getString(1) : "";
        }
    }

    /**
     * 判断备份目录中是否存在指定前缀的备份文件。
     *
     * @param prefix 文件名前缀
     * @return 存在时返回 true
     * @throws Exception 查询失败时抛出
     */
    private static boolean hasBackupWithPrefix(String prefix) throws Exception {
        Path backupDir = DataPathProvider.getTodoDataDir().resolve("backups");
        if (!Files.exists(backupDir)) {
            return false;
        }
        try (var paths = Files.list(backupDir)) {
            return paths.anyMatch(path -> {
                String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
                return fileName.startsWith(prefix) && fileName.endsWith(".zip");
            });
        }
    }

    /**
     * FailingUpgradeStep 是测试专用升级步骤，用于模拟 DDL 失败。
     */
    private static final class FailingUpgradeStep implements H2SchemaUpgrader.SchemaUpgradeStep {
        /**
         * 返回测试起始版本。
         *
         * @return 起始版本
         */
        @Override
        public int fromVersion() {
            return 0;
        }

        /**
         * 返回测试目标版本。
         *
         * @return 目标版本
         */
        @Override
        public int toVersion() {
            return 1;
        }

        /**
         * 抛出测试用 SQL 异常。
         *
         * @param connection H2 连接
         * @throws SQLException 始终抛出测试异常
         */
        @Override
        public void upgrade(Connection connection) throws SQLException {
            throw new SQLException("forced schema upgrade failure");
        }
    }
}
