package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;

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
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldUseTcpUrlAndAccountPermissions", H2TcpAccessTestMain::shouldUseTcpUrlAndAccountPermissions);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldIncrementPortWhenDefaultBusy", H2TcpAccessTestMain::shouldIncrementPortWhenDefaultBusy);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldFallbackWhenAllPortsBusy", H2TcpAccessTestMain::shouldFallbackWhenAllPortsBusy);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldDisableRemoteWeakPasswordConfig", H2TcpAccessTestMain::shouldDisableRemoteWeakPasswordConfig);
        GuiTestSupport.runTestCase("H2TcpAccessTestMain.shouldApplyPasswordResetToDatabaseUser", H2TcpAccessTestMain::shouldApplyPasswordResetToDatabaseUser);
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
