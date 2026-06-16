package com.todolist.storage;

import com.todolist.config.ModConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * H2StorageBootstrap 负责在 H2 后端首次访问时初始化 schema 并迁移旧 NBT 数据。
 */
public final class H2StorageBootstrap {
    private static final Set<Path> READY_DATABASES = new HashSet<>();

    private final H2ConnectionProvider connectionProvider;
    private final H2SchemaInitializer schemaInitializer;
    private final H2LegacyMigrationReader migrationReader;
    private final H2LegacyMigrator migrator;
    private final H2BackupService backupService;

    /**
     * 创建默认 H2 存储启动器。
     */
    public H2StorageBootstrap() {
        this(new H2ConnectionProvider(), new H2SchemaInitializer(), new H2LegacyMigrationReader(), new H2LegacyMigrator(), null);
    }

    /**
     * 创建可注入依赖的 H2 存储启动器。
     *
     * @param connectionProvider H2 连接提供器
     * @param schemaInitializer schema 初始化器
     * @param migrationReader 旧 NBT 读取器
     * @param migrator 旧 NBT 迁移器
     * @param backupService H2 备份服务
     */
    public H2StorageBootstrap(H2ConnectionProvider connectionProvider,
                              H2SchemaInitializer schemaInitializer,
                              H2LegacyMigrationReader migrationReader,
                              H2LegacyMigrator migrator,
                              H2BackupService backupService) {
        this.connectionProvider = connectionProvider;
        this.schemaInitializer = schemaInitializer;
        this.migrationReader = migrationReader;
        this.migrator = migrator;
        this.backupService = backupService == null ? new H2BackupService(connectionProvider, null) : backupService;
    }

    /**
     * 确保当前命名空间的 H2 数据库已经可用。
     *
     * @throws IOException 初始化或迁移失败时抛出
     */
    public void ensureReady() throws IOException {
        Path databasePath = connectionProvider.getDatabaseBasePath().toAbsolutePath().normalize();
        H2StorageAvailability.ensureAvailable(databasePath);
        synchronized (READY_DATABASES) {
            H2TcpConfig tcpConfig = H2TcpConfig.load();
            if (tcpConfig.isTcpEnabled() && !databaseFileExists(databasePath)) {
                prepareEmbeddedBeforeTcp(databasePath);
            }
            H2TcpServerManager.ensureStarted(tcpConfig);
            if (READY_DATABASES.contains(databasePath)) {
                return;
            }
            try (Connection connection = connectionProvider.openBootstrapConnection()) {
                schemaInitializer.initialize(connection);
                if (!isMigrationCompleted(connection)) {
                    migrator.migrate(connection, migrationReader.readAll());
                }
                backupOnStartIfEnabled(connection, databasePath);
                H2StorageAvailability.markAvailable(databasePath);
                READY_DATABASES.add(databasePath);
            } catch (SQLException exception) {
                H2StorageAvailability.Reason reason = exception.getCause() instanceof ClassNotFoundException
                        ? H2StorageAvailability.Reason.DRIVER_MISSING
                        : H2StorageAvailability.Reason.SCHEMA_INIT_FAILED;
                H2StorageAvailability.markUnavailable(databasePath, reason, exception.getMessage());
                throw new StorageUnavailableException(reason, "Failed to prepare H2 storage", exception);
            } catch (H2SchemaUpgradeException exception) {
                H2StorageAvailability.markUnavailable(databasePath, H2StorageAvailability.Reason.SCHEMA_UPGRADE_FAILED, exception.getMessage());
                throw new StorageUnavailableException(H2StorageAvailability.Reason.SCHEMA_UPGRADE_FAILED, "Failed to upgrade H2 schema", exception);
            } catch (LegacyMigrationException exception) {
                H2StorageAvailability.markUnavailable(databasePath, H2StorageAvailability.Reason.MIGRATION_FAILED, exception.getMessage());
                throw new StorageUnavailableException(H2StorageAvailability.Reason.MIGRATION_FAILED, "Failed to migrate legacy data to H2", exception);
            } catch (IOException exception) {
                H2StorageAvailability.markUnavailable(databasePath, H2StorageAvailability.Reason.BACKUP_FAILED, exception.getMessage());
                throw new StorageUnavailableException(H2StorageAvailability.Reason.BACKUP_FAILED, "Failed to backup H2 storage on start", exception);
            }
        }
    }

    /**
     * TCP 首次启用前用嵌入式连接预创建数据库，避免 H2 TCP 拒绝创建远程库。
     *
     * @param databasePath H2 数据库基础路径
     * @throws IOException 初始化或迁移失败时抛出
     */
    private void prepareEmbeddedBeforeTcp(Path databasePath) throws IOException {
        H2TcpServerManager.stop();
        try (Connection connection = connectionProvider.openEmbeddedBootstrapConnection()) {
            schemaInitializer.initialize(connection);
            if (!isMigrationCompleted(connection)) {
                migrator.migrate(connection, migrationReader.readAll());
            }
            backupOnStartIfEnabled(connection, databasePath);
            H2StorageAvailability.markAvailable(databasePath);
            READY_DATABASES.add(databasePath);
        } catch (SQLException exception) {
            H2StorageAvailability.markUnavailable(databasePath, H2StorageAvailability.Reason.SCHEMA_INIT_FAILED, exception.getMessage());
            throw new StorageUnavailableException(H2StorageAvailability.Reason.SCHEMA_INIT_FAILED, "Failed to prepare H2 storage before TCP startup", exception);
        } catch (H2SchemaUpgradeException exception) {
            H2StorageAvailability.markUnavailable(databasePath, H2StorageAvailability.Reason.SCHEMA_UPGRADE_FAILED, exception.getMessage());
            throw new StorageUnavailableException(H2StorageAvailability.Reason.SCHEMA_UPGRADE_FAILED, "Failed to upgrade H2 schema before TCP startup", exception);
        } catch (LegacyMigrationException exception) {
            H2StorageAvailability.markUnavailable(databasePath, H2StorageAvailability.Reason.MIGRATION_FAILED, exception.getMessage());
            throw new StorageUnavailableException(H2StorageAvailability.Reason.MIGRATION_FAILED, "Failed to migrate legacy data before TCP startup", exception);
        } catch (IOException exception) {
            H2StorageAvailability.markUnavailable(databasePath, H2StorageAvailability.Reason.BACKUP_FAILED, exception.getMessage());
            throw new StorageUnavailableException(H2StorageAvailability.Reason.BACKUP_FAILED, "Failed to backup H2 storage on start before TCP startup", exception);
        }
    }

    /**
     * 在配置启用时为启动后的 H2 数据库创建备份。
     *
     * @param connection H2 连接
     * @param databasePath H2 数据库基础路径
     * @throws IOException 备份失败时抛出
     */
    private void backupOnStartIfEnabled(Connection connection, Path databasePath) throws IOException {
        if (!ModConfig.getInstance().isH2BackupOnStart()) {
            return;
        }
        backupService.backupPreparedDatabase(connection, "startup-" + databasePath.getFileName());
    }

    /**
     * 判断 H2 数据库文件是否已经存在。
     *
     * @param databasePath H2 数据库基础路径
     * @return 存在时返回 true
     */
    private boolean databaseFileExists(Path databasePath) {
        Path fileName = databasePath.getFileName();
        if (fileName == null) {
            return false;
        }
        return Files.exists(databasePath.resolveSibling(fileName + ".mv.db"));
    }

    /**
     * 查询旧 NBT 迁移是否已经完成。
     *
     * @param connection H2 连接
     * @return 已完成时返回 true
     * @throws SQLException 查询失败时抛出
     */
    private boolean isMigrationCompleted(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT "value" FROM storage_meta WHERE "key" = 'dat_migration_completed'
                """);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && "true".equalsIgnoreCase(resultSet.getString(1));
        }
    }

    /**
     * 清理指定数据库路径的启动缓存和不可用状态。
     *
     * @param databasePath H2 数据库基础路径
     */
    public static void resetDatabaseState(Path databasePath) {
        Path normalizedPath = databasePath == null ? Path.of("") : databasePath.toAbsolutePath().normalize();
        synchronized (READY_DATABASES) {
            READY_DATABASES.remove(normalizedPath);
        }
        H2ConnectionProvider.invalidateReusableConnections();
        H2StorageAvailability.reset(normalizedPath);
    }

    /**
     * 清理全部 H2 启动缓存，供离线测试使用。
     */
    public static void resetAllForTests() {
        synchronized (READY_DATABASES) {
            READY_DATABASES.clear();
        }
        H2StorageAvailability.resetForTests();
        H2MaintenanceLock.resetForTests();
    }

}
