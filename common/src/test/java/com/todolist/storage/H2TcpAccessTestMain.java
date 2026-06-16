package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.ProjectPlayerStateStorage.ProjectPlayerState;
import com.todolist.task.Task;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;

/**
 * H2TcpAccessTestMain 覆盖 M2 的 H2 TCP 配置、启动回退和基础权限行为。
 */
public final class H2TcpAccessTestMain {
    private H2TcpAccessTestMain() {
    }

    /**
     * 执行 H2 TCP 外部访问离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldCreateSafeDefaultConfig", H2TcpAccessTestMain::shouldCreateSafeDefaultConfig);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldPreserveCorruptConfig", H2TcpAccessTestMain::shouldPreserveCorruptConfig);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldKeepEmbeddedDatabaseOpenBetweenShortConnections", H2TcpAccessTestMain::shouldKeepEmbeddedDatabaseOpenBetweenShortConnections);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldUseTcpUrlAndAccountPermissions", H2TcpAccessTestMain::shouldUseTcpUrlAndAccountPermissions);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldIncrementPortWhenDefaultBusy", H2TcpAccessTestMain::shouldIncrementPortWhenDefaultBusy);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldFallbackWhenAllPortsBusy", H2TcpAccessTestMain::shouldFallbackWhenAllPortsBusy);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldDisableRemoteWeakPasswordConfig", H2TcpAccessTestMain::shouldDisableRemoteWeakPasswordConfig);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldApplyPasswordResetToDatabaseUser", H2TcpAccessTestMain::shouldApplyPasswordResetToDatabaseUser);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldKeepGameWritesAvailableWithExternalTcpSession", H2TcpAccessTestMain::shouldKeepGameWritesAvailableWithExternalTcpSession);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldRecoverAfterExternalTcpWriteLockIsReleased", H2TcpAccessTestMain::shouldRecoverAfterExternalTcpWriteLockIsReleased);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldKeepPlayerStateWritableAfterUppercaseTcpSession", H2TcpAccessTestMain::shouldKeepPlayerStateWritableAfterUppercaseTcpSession);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldKeepPlayerStateWritableAfterTcpServerRestartWithCachedReusableConnection", H2TcpAccessTestMain::shouldKeepPlayerStateWritableAfterTcpServerRestartWithCachedReusableConnection);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldKeepPlayerStateWritableAfterLeavingTcpWorldToEmbeddedWorld", H2TcpAccessTestMain::shouldKeepPlayerStateWritableAfterLeavingTcpWorldToEmbeddedWorld);
    }

    /**
     * 验证缺失配置会生成安全默认 H2 TCP 配置。
     */
    private static void shouldCreateSafeDefaultConfig() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-default-");
            H2TcpConfig config = H2TcpConfig.load();
            GuiTestSupport.assertFalse(config.isTcpEnabled(), "H2 TCP 默认应关闭");
            GuiTestSupport.assertFalse(config.isAllowRemote(), "H2 TCP 默认不允许远程访问");
            GuiTestSupport.assertTrue(Files.exists(H2TcpConfig.getConfigPath()), "缺失配置时应创建 todolist-h2.json");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 默认配置时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证损坏配置会被保留并重建安全默认配置。
     */
    private static void shouldPreserveCorruptConfig() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-corrupt-");
            Files.createDirectories(H2TcpConfig.getConfigPath().getParent());
            Files.writeString(H2TcpConfig.getConfigPath(), "{not-json", StandardCharsets.UTF_8);
            H2TcpConfig config = H2TcpConfig.load();
            GuiTestSupport.assertTrue(config.isRecreatedFromCorrupt(), "损坏配置应标记为已重建");
            GuiTestSupport.assertTrue(Files.exists(H2TcpConfig.getConfigPath().resolveSibling("todolist-h2.json.corrupted")), "损坏配置应被保留为 .corrupted");
            GuiTestSupport.assertFalse(config.isTcpEnabled(), "损坏配置重建后 TCP 应保持关闭");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 损坏配置恢复时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证嵌入式 H2 URL 会保留数据库实例，避免短连接频繁关闭时重复付出关库成本。
     */
    private static void shouldKeepEmbeddedDatabaseOpenBetweenShortConnections() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-embedded-url-");
            H2ConnectionProvider provider = new H2ConnectionProvider();
            String jdbcUrl = provider.getJdbcUrl();
            GuiTestSupport.assertTrue(jdbcUrl.startsWith("jdbc:h2:file:"), "TCP 关闭时应使用嵌入式 H2 URL");
            GuiTestSupport.assertTrue(jdbcUrl.contains("DB_CLOSE_DELAY=-1"), "嵌入式 H2 URL 应保持数据库实例存活，避免短连接频繁关库");
        } catch (Exception exception) {
            throw new IllegalStateException("验证嵌入式 H2 URL 保活配置时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证 TCP 启用后内部连接使用 TCP URL，且 readonly/readwrite 基础权限生效。
     */
    private static void shouldUseTcpUrlAndAccountPermissions() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-access-");
            Path databasePath = tempGameDir.resolve("todo tcp 中文").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19092, 16);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            new H2StorageBootstrap().ensureReady();

            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2TcpConfig config = H2TcpConfig.load();
            String jdbcUrl = provider.getJdbcUrl();
            GuiTestSupport.assertTrue(jdbcUrl.startsWith("jdbc:h2:tcp://"), "TCP 启用后内部 DAO 应使用 TCP URL");
            GuiTestSupport.assertTrue(H2TcpServerManager.getStatus().isTcpActive(), "TCP Server 应处于活跃状态");

            try (Connection readonly = DriverManager.getConnection(jdbcUrl, config.getReadonlyUser(), config.getReadonlyPassword());
                 Statement statement = readonly.createStatement()) {
                statement.executeQuery("SELECT COUNT(*) FROM tasks");
                GuiTestSupport.assertTrue(failsSql(statement, "INSERT INTO tasks(bucket_type, owner_uuid, id, scope, title, completed, priority, created_at, sort_order, updated_at) VALUES('local_personal','local','blocked','PERSONAL','blocked',false,'MEDIUM',1,1,1)"), "readonly 不应允许 DML");
            }

            try (Connection readwrite = DriverManager.getConnection(jdbcUrl, config.getReadwriteUser(), config.getReadwritePassword());
                 Statement statement = readwrite.createStatement()) {
                statement.execute("INSERT INTO tasks(bucket_type, owner_uuid, id, scope, title, completed, priority, created_at, sort_order, updated_at) VALUES('local_personal','local','allowed','PERSONAL','allowed',false,'MEDIUM',1,1,1)");
                GuiTestSupport.assertTrue(failsSql(statement, "CREATE TABLE tcp_forbidden(id INT)"), "readwrite 不应允许 DDL");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 连接和权限时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证默认端口被占用时会自动递增到下一个可用端口。
     */
    private static void shouldIncrementPortWhenDefaultBusy() {
        Path tempGameDir = null;
        try (ServerSocket ignored = new ServerSocket(19102)) {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-port-");
            Path databasePath = tempGameDir.resolve("todo").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19102, 4);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            new H2StorageBootstrap().ensureReady();

            H2TcpServerManager.StatusSnapshot status = H2TcpServerManager.getStatus();
            GuiTestSupport.assertTrue(status.isTcpActive(), "端口递增后 TCP 应启动成功");
            GuiTestSupport.assertEquals(19103, status.getActualPort(), "默认端口冲突时应递增到下一个端口");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 端口递增时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证所有端口尝试均失败时会保留嵌入式回退状态。
     */
    private static void shouldFallbackWhenAllPortsBusy() {
        Path tempGameDir = null;
        try (ServerSocket ignored = new ServerSocket(19132)) {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-fallback-");
            writeTcpEnabledConfig(tempGameDir.resolve("todo").resolve("todolist"), 19132, 1);

            H2TcpServerManager.StatusSnapshot status = H2TcpServerManager.ensureStarted(H2TcpConfig.load());

            GuiTestSupport.assertFalse(status.isTcpActive(), "端口全部冲突时 TCP 不应处于活跃状态");
            GuiTestSupport.assertEquals("embedded_fallback", status.getMode(), "端口全部冲突时应进入嵌入式回退状态");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 全端口失败回退时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证远程访问弱密码配置会禁用 TCP。
     */
    private static void shouldDisableRemoteWeakPasswordConfig() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-weak-");
            Files.createDirectories(H2TcpConfig.getConfigPath().getParent());
            Files.writeString(H2TcpConfig.getConfigPath(), """
                    {
                      "tcpEnabled": true,
                      "bindAddress": "0.0.0.0",
                      "port": 19142,
                      "autoIncrementPort": true,
                      "maxPortAttempts": 4,
                      "allowRemote": true,
                      "accounts": {
                        "adminUser": "todo_admin",
                        "adminPassword": "weak",
                        "readonlyUser": "todo_readonly",
                        "readonlyPassword": "weak",
                        "readwriteUser": "todo_readwrite",
                        "readwritePassword": "weak"
                      }
                    }
                    """, StandardCharsets.UTF_8);

            H2TcpConfig config = H2TcpConfig.load();

            GuiTestSupport.assertFalse(config.isTcpEnabled(), "远程访问弱密码应禁用 TCP");
            GuiTestSupport.assertEquals("0.0.0.0", config.getBindAddress(), "远程访问配置应保留绑定地址供服主修正");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 远程弱密码保护时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证密码重置会同步到数据库用户，旧密码失效，新密码可用。
     */
    private static void shouldApplyPasswordResetToDatabaseUser() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-password-");
            Path databasePath = tempGameDir.resolve("todo").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19112, 4);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
            new H2StorageBootstrap().ensureReady();

            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2TcpConfig config = H2TcpConfig.load();
            String oldPassword = config.getReadonlyPassword();
            String newPassword = "readonly-password-reset-123456";
            try (Connection admin = DriverManager.getConnection(provider.getJdbcUrl(), config.getAdminUser(), config.getAdminPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("ALTER USER \"" + config.getReadonlyUser() + "\" SET PASSWORD '" + newPassword + "'");
            }
            config.setPassword(H2TcpAccountRole.READONLY, newPassword);
            config.save();

            GuiTestSupport.assertTrue(failsConnection(provider.getJdbcUrl(), config.getReadonlyUser(), oldPassword), "旧 readonly 密码应失效");
            try (Connection ignored = DriverManager.getConnection(provider.getJdbcUrl(), config.getReadonlyUser(), newPassword)) {
                GuiTestSupport.assertTrue(true, "新 readonly 密码应可连接");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 密码重置同步时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证外部 TCP 会话保持连接时，游戏内 H2 重建上下文后仍可继续写入。
     */
    private static void shouldKeepGameWritesAvailableWithExternalTcpSession() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-session-");
            Path databasePath = tempGameDir.resolve("todo").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19152, 4);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            H2StorageBootstrap bootstrap = new H2StorageBootstrap();
            bootstrap.ensureReady();

            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2TcpConfig config = H2TcpConfig.load();
            try (Connection readonly = DriverManager.getConnection(provider.getJdbcUrl(), config.getReadonlyUser(), config.getReadonlyPassword())) {
                H2StorageBootstrap.resetDatabaseState(provider.getDatabaseBasePath());

                Task task = new Task("TCP Session Write Task", "");
                new H2TaskStore().saveLocalTasks(List.of(task));

                GuiTestSupport.assertEquals(1, new H2TaskStore().loadLocalTasks().size(), "外部 TCP 会话存在时游戏内写入仍应可用");
                GuiTestSupport.assertFalse(readonly.isClosed(), "外部 TCP 会话不应被游戏内写入关闭");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 外部会话期间游戏内写入时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证外部 TCP 写事务短暂锁表导致写入失败后，释放锁即可恢复游戏内写入。
     */
    private static void shouldRecoverAfterExternalTcpWriteLockIsReleased() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-lock-");
            Path databasePath = tempGameDir.resolve("todo").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19162, 4);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            H2TaskStore store = new H2TaskStore();
            Task lockedTask = new Task("Locked Task", "");
            store.saveLocalTasks(List.of(lockedTask));

            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2TcpConfig config = H2TcpConfig.load();
            try (Connection admin = DriverManager.getConnection(provider.getJdbcUrl(), config.getAdminUser(), config.getAdminPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("SET DEFAULT_LOCK_TIMEOUT 100");
            }

            boolean failedDuringExternalLock = false;
            try (Connection readwrite = DriverManager.getConnection(provider.getJdbcUrl(), config.getReadwriteUser(), config.getReadwritePassword());
                 Statement statement = readwrite.createStatement()) {
                readwrite.setAutoCommit(false);
                statement.executeUpdate("UPDATE tasks SET title = title WHERE id = '" + lockedTask.getId() + "'");
                try {
                    store.saveLocalTasks(List.of(new Task("Blocked During External Lock", "")));
                } catch (Exception expected) {
                    failedDuringExternalLock = true;
                } finally {
                    readwrite.rollback();
                }
            }
            GuiTestSupport.assertTrue(failedDuringExternalLock, "外部写事务持锁时游戏内写入应先失败");

            store.saveLocalTasks(List.of(new Task("Recovered After External Lock", "")));
            GuiTestSupport.assertEquals("Recovered After External Lock", store.loadLocalTasks().get(0).getTitle(), "外部写锁释放后游戏内写入应自动恢复");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 外部写锁释放后的恢复能力时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证外部工具使用缺少大小写参数的 TCP URL 连接后，游戏内玩家项目状态仍可写入。
     */
    private static void shouldKeepPlayerStateWritableAfterUppercaseTcpSession() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-uppercase-session-");
            Path databasePath = tempGameDir.resolve("todo").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19172, 4);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            H2StorageBootstrap bootstrap = new H2StorageBootstrap();
            bootstrap.ensureReady();

            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2TcpConfig config = H2TcpConfig.load();
            String uppercaseModeUrl = provider.getJdbcUrl().replace(";DATABASE_TO_UPPER=FALSE", "");
            try (Connection external = DriverManager.getConnection(uppercaseModeUrl, config.getReadonlyUser(), config.getReadonlyPassword());
                 Statement statement = external.createStatement()) {
                GuiTestSupport.assertTrue(failsSql(statement, "SELECT COUNT(*) FROM player_project_state"), "缺少大小写参数的外部会话应复现大写表名查找失败");
            }

            H2ProjectPlayerStateStore store = new H2ProjectPlayerStateStore();
            store.savePlayerState(java.util.UUID.fromString("9b17065a-10e8-4e55-a5ce-dcb2a4d9a8ba"),
                    new ProjectPlayerState("active-project", List.of("active-project"), true));

            GuiTestSupport.assertEquals("active-project",
                    store.loadPlayerState(java.util.UUID.fromString("9b17065a-10e8-4e55-a5ce-dcb2a4d9a8ba")).getActiveProjectId(),
                    "外部大写模式会话后游戏内玩家项目状态仍应可写");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 TCP 大写模式外部会话后的玩家状态写入时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证当前线程缓存了旧 TCP 可复用连接后，TCP 服务重启仍不会导致玩家项目状态写入失败。
     */
    private static void shouldKeepPlayerStateWritableAfterTcpServerRestartWithCachedReusableConnection() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-restart-reuse-");
            Path databasePath = tempGameDir.resolve("todo").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19182, 4);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            H2StorageBootstrap bootstrap = new H2StorageBootstrap();
            bootstrap.ensureReady();

            H2ConnectionProvider provider = new H2ConnectionProvider();
            primeReusableConnection(provider);

            H2StorageBootstrap.resetDatabaseState(provider.getDatabaseBasePath());
            H2TcpServerManager.stop();

            bootstrap.ensureReady();
            GuiTestSupport.assertTrue(H2TcpServerManager.getStatus().isTcpActive(), "TCP 重启后应重新回到活跃状态");

            H2ProjectPlayerStateStore store = new H2ProjectPlayerStateStore();
            java.util.UUID playerId = java.util.UUID.fromString("9b17065a-10e8-4e55-a5ce-dcb2a4d9a8ba");
            ProjectPlayerState expected = new ProjectPlayerState("restart-project", List.of("restart-project"), true);
            store.savePlayerState(playerId, expected);

            GuiTestSupport.assertEquals(
                    "restart-project",
                    store.loadPlayerState(playerId).getActiveProjectId(),
                    "TCP 服务重启后旧缓存连接不应让玩家项目状态写入失败"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("验证 TCP 服务重启后的可复用连接失效处理时发生异常", exception);
        } finally {
            H2ConnectionProvider.disableEmbeddedConnectionReuseForCurrentThread();
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证离开启用 TCP 的世界后再进入嵌入式世界时，旧 TCP 可复用连接不会污染未发布单人保存。
     */
    private static void shouldKeepPlayerStateWritableAfterLeavingTcpWorldToEmbeddedWorld() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-tcp-to-embedded-");
            Path databasePath = tempGameDir.resolve("todo").resolve("todolist");
            writeTcpEnabledConfig(databasePath, 19192, 4);
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            H2StorageBootstrap bootstrap = new H2StorageBootstrap();
            bootstrap.ensureReady();

            H2ConnectionProvider provider = new H2ConnectionProvider();
            primeReusableConnection(provider);

            writeTcpDisabledConfig(databasePath);
            H2StorageBootstrap.resetDatabaseState(provider.getDatabaseBasePath());
            H2TcpServerManager.stop();

            bootstrap.ensureReady();
            GuiTestSupport.assertFalse(H2TcpServerManager.getStatus().isTcpActive(), "退回未发布单人后应走嵌入式连接分支");

            H2ProjectPlayerStateStore store = new H2ProjectPlayerStateStore();
            java.util.UUID playerId = java.util.UUID.fromString("8ef0ea48-2f53-4b26-bf8b-4e42cdb6ab6a");
            ProjectPlayerState expected = new ProjectPlayerState("embedded-project", List.of("embedded-project"), true);
            store.savePlayerState(playerId, expected);

            GuiTestSupport.assertEquals(
                    "embedded-project",
                    store.loadPlayerState(playerId).getActiveProjectId(),
                    "从 TCP 世界退回嵌入式世界后玩家项目状态仍应可写"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("验证从 TCP 世界退回嵌入式世界时发生异常", exception);
        } finally {
            H2ConnectionProvider.disableEmbeddedConnectionReuseForCurrentThread();
            cleanup(tempGameDir);
        }
    }

    /**
     * 预热当前线程的可复用 H2 连接缓存，模拟 GUI 后台线程跨生命周期复用连接。
     *
     * @param provider H2 连接提供器
     * @throws Exception 预热失败时抛出
     */
    private static void primeReusableConnection(H2ConnectionProvider provider) throws Exception {
        H2ConnectionProvider.enableEmbeddedConnectionReuseForCurrentThread();
        try (Connection connection = provider.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT 1");
        }
    }

    /**
     * 执行 SQL 并返回是否失败。
     *
     * @param statement SQL statement
     * @param sql SQL 文本
     * @return SQL 失败时返回 true
     */
    private static boolean failsSql(Statement statement, String sql) {
        try {
            statement.execute(sql);
            return false;
        } catch (SQLException expected) {
            return true;
        }
    }

    /**
     * 尝试连接并返回是否失败。
     *
     * @param jdbcUrl JDBC URL
     * @param user 用户名
     * @param password 密码
     * @return 连接失败时返回 true
     */
    private static boolean failsConnection(String jdbcUrl, String user, String password) {
        try (Connection ignored = DriverManager.getConnection(jdbcUrl, user, password)) {
            return false;
        } catch (SQLException expected) {
            return true;
        }
    }

    /**
     * 写入启用 TCP 的测试配置。
     *
     * @param databasePath 数据库基础路径
     * @throws Exception 写入失败时抛出
     */
    private static void writeTcpEnabledConfig(Path databasePath, int port, int maxPortAttempts) throws Exception {
        Files.createDirectories(H2TcpConfig.getConfigPath().getParent());
        String h2Path = databasePath.toAbsolutePath().toString().replace("\\", "/");
        String json = """
                {
                  "tcpEnabled": true,
                  "bindAddress": "127.0.0.1",
                  "port": %s,
                  "autoIncrementPort": true,
                  "maxPortAttempts": %s,
                  "allowRemote": false,
                  "databasePathOverride": "%s",
                  "accounts": {
                    "adminUser": "todo_admin",
                    "adminPassword": "admin-password-123456789012",
                    "readonlyUser": "todo_readonly",
                    "readonlyPassword": "readonly-password-123456789",
                    "readwriteUser": "todo_readwrite",
                    "readwritePassword": "readwrite-password-12345678"
                  }
                }
                """.formatted(port, maxPortAttempts, h2Path);
        Files.writeString(H2TcpConfig.getConfigPath(), json, StandardCharsets.UTF_8);
    }

    /**
     * 写入关闭 TCP 的测试配置，但保持数据库路径与账号配置不变，用于模拟退出局域网后回到嵌入式世界。
     *
     * @param databasePath 数据库基础路径
     * @throws Exception 写入失败时抛出
     */
    private static void writeTcpDisabledConfig(Path databasePath) throws Exception {
        Files.createDirectories(H2TcpConfig.getConfigPath().getParent());
        String h2Path = databasePath.toAbsolutePath().toString().replace("\\", "/");
        String json = """
                {
                  "tcpEnabled": false,
                  "bindAddress": "127.0.0.1",
                  "port": 19192,
                  "autoIncrementPort": true,
                  "maxPortAttempts": 4,
                  "allowRemote": false,
                  "databasePathOverride": "%s",
                  "accounts": {
                    "adminUser": "todo_admin",
                    "adminPassword": "admin-password-123456789012",
                    "readonlyUser": "todo_readonly",
                    "readonlyPassword": "readonly-password-123456789",
                    "readwriteUser": "todo_readwrite",
                    "readwritePassword": "readwrite-password-12345678"
                  }
                }
                """.formatted(h2Path);
        Files.writeString(H2TcpConfig.getConfigPath(), json, StandardCharsets.UTF_8);
    }

    /**
     * 准备测试临时 game dir。
     *
     * @param prefix 临时目录前缀
     * @return 临时目录
     * @throws Exception 准备失败时抛出
     */
    private static Path prepareTempGameDir(String prefix) throws Exception {
        H2StorageBootstrap.resetAllForTests();
        H2TcpServerManager.stop();
        Path tempGameDir = Files.createTempDirectory(prefix);
        DataPathProvider.setGameDirSupplier(() -> tempGameDir);
        DataPathProvider.resetStorageNamespace();
        return tempGameDir;
    }

    /**
     * 清理测试状态和临时目录。
     *
     * @param tempGameDir 临时目录
     */
    private static void cleanup(Path tempGameDir) {
        try {
            H2TcpServerManager.stop();
            H2StorageBootstrap.resetAllForTests();
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
            if (tempGameDir == null || !Files.exists(tempGameDir)) {
                return;
            }
            try (var paths = Files.walk(tempGameDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法删除 H2 TCP 测试临时文件: " + path, exception);
                    }
                });
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理 H2 TCP 测试临时目录", exception);
        }
    }
}
