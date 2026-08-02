package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;

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
