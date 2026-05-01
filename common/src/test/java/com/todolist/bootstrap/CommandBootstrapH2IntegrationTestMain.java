package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.platform.DataPathProvider;
import com.todolist.storage.H2StorageBootstrap;
import com.todolist.storage.H2TcpConfig;
import com.todolist.storage.H2TcpServerManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * CommandBootstrapH2IntegrationTestMain 覆盖 H2 运维命令的离线集成行为。
 */
public final class CommandBootstrapH2IntegrationTestMain {
    /**
     * 禁止实例化 H2 命令测试入口。
     */
    private CommandBootstrapH2IntegrationTestMain() {
    }

    /**
     * 执行 H2 运维命令离线集成测试。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 任一测试失败时抛出
     */
    public static void main(String[] args) throws Exception {
        CommandBootstrapIntegrationTestMain.bootstrapEnvironment();
        try {
            CommandTestSupport.runTestCase("CommandBootstrapH2IntegrationTestMain.shouldExecuteH2StatusCommand", CommandBootstrapH2IntegrationTestMain::shouldExecuteH2StatusCommand);
            CommandTestSupport.runTestCase("CommandBootstrapH2IntegrationTestMain.shouldExecuteH2ResetPasswordCommand", CommandBootstrapH2IntegrationTestMain::shouldExecuteH2ResetPasswordCommand);
        } finally {
            H2TcpServerManager.stop();
            H2StorageBootstrap.resetAllForTests();
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
            DataPathProvider.resetStorageNamespace();
        }
    }

    /**
     * 验证 H2 状态命令可由管理员执行并输出状态消息。
     */
    private static void shouldExecuteH2StatusCommand() throws Exception {
        resetState();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(2, null);

        int result = dispatcher.execute("todo h2 status", source);

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "h2 status 成功时应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.status.backend", "h2 status 未输出后端状态");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.status.mode", "h2 status 未输出连接模式");
    }

    /**
     * 验证 H2 密码重置命令会同步配置和数据库用户。
     */
    private static void shouldExecuteH2ResetPasswordCommand() throws Exception {
        resetState();
        Path databasePath = DataPathProvider.getTodoDataDir().resolve("todolist-command-h2");
        writeH2TcpCommandConfig(databasePath);
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        new H2StorageBootstrap().ensureReady();
        H2TcpConfig beforeReset = H2TcpConfig.load();
        String oldReadonlyPassword = beforeReset.getReadonlyPassword();
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(2, null);

        int result = dispatcher.execute("todo h2 reset-password readonly", source);

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "h2 reset-password 成功时应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.reset_password.success", "h2 reset-password 未输出成功消息");
        H2TcpConfig afterReset = H2TcpConfig.load();
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.FALSE, oldReadonlyPassword.equals(afterReset.getReadonlyPassword()), "配置中的 readonly 密码应更新");
        String jdbcUrl = H2TcpServerManager.buildTcpJdbcUrl(databasePath);
        assertConnectionFails(jdbcUrl, afterReset.getReadonlyUser(), oldReadonlyPassword, "旧 readonly 密码应失效");
        try (Connection ignored = DriverManager.getConnection(jdbcUrl, afterReset.getReadonlyUser(), afterReset.getReadonlyPassword())) {
            // 新密码能成功建立连接即可。
        }
    }

    /**
     * 重置 H2 命令测试状态。
     */
    private static void resetState() {
        H2TcpServerManager.stop();
        H2StorageBootstrap.resetAllForTests();
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TodoListCommon.init();
    }

    /**
     * 写入命令测试使用的 H2 TCP 配置。
     *
     * @param databasePath H2 数据库基础路径
     * @throws Exception 写入失败时抛出
     */
    private static void writeH2TcpCommandConfig(Path databasePath) throws Exception {
        Files.createDirectories(H2TcpConfig.getConfigPath().getParent());
        String h2Path = databasePath.toAbsolutePath().toString().replace("\\", "/");
        String json = """
                {
                  "tcpEnabled": true,
                  "bindAddress": "127.0.0.1",
                  "port": 19122,
                  "autoIncrementPort": true,
                  "maxPortAttempts": 8,
                  "allowRemote": false,
                  "databasePathOverride": "%s",
                  "accounts": {
                    "adminUser": "todo_admin",
                    "adminPassword": "admin-password-command-12345",
                    "readonlyUser": "todo_readonly",
                    "readonlyPassword": "readonly-password-command-12",
                    "readwriteUser": "todo_readwrite",
                    "readwritePassword": "readwrite-password-command-1"
                  }
                }
                """.formatted(h2Path);
        Files.writeString(H2TcpConfig.getConfigPath(), json, StandardCharsets.UTF_8);
    }

    /**
     * 断言指定凭据无法连接 H2。
     *
     * @param jdbcUrl JDBC URL
     * @param user 用户名
     * @param password 密码
     * @param message 失败提示
     */
    private static void assertConnectionFails(String jdbcUrl, String user, String password, String message) {
        try (Connection ignored = DriverManager.getConnection(jdbcUrl, user, password)) {
            throw new AssertionError(message);
        } catch (Exception expected) {
            // 连接失败即符合预期。
        }
    }
}
