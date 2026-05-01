package com.todolist.storage;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;

/**
 * H2SchemaInitializerTestMain 覆盖 M1-B 的 H2 路径、schema 初始化和幂等性。
 */
public final class H2SchemaInitializerTestMain {
    private H2SchemaInitializerTestMain() {
    }

    /**
     * 执行 H2 schema 初始化离线自测。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 任一断言或数据库操作失败时抛出
     */
    public static void main(String[] args) throws Exception {
        GuiTestSupport.runTestCase("H2SchemaInitializerTestMain.shouldInitializeSchemaIdempotently", H2SchemaInitializerTestMain::shouldInitializeSchemaIdempotently);
    }

    /**
     * 验证 schema 初始化可重复执行，且数据库文件只出现在临时 game dir 下。
     */
    private static void shouldInitializeSchemaIdempotently() {
        Path tempGameDir = null;
        try {
            tempGameDir = Files.createTempDirectory("todolist-h2-schema-");
            Path finalTempGameDir = tempGameDir;
            DataPathProvider.setGameDirSupplier(() -> finalTempGameDir);
            DataPathProvider.resetStorageNamespace();

            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2SchemaInitializer initializer = new H2SchemaInitializer();
            try (Connection connection = provider.openConnection()) {
                initializer.initialize(connection);
                initializer.initialize(connection);
                assertTableExists(connection, "tasks");
                assertTableExists(connection, "task_tags");
                assertTableExists(connection, "projects");
                assertTableExists(connection, "project_members");
                assertTableExists(connection, "player_project_state");
                assertTableExists(connection, "player_hud_starred_projects");
                assertTableExists(connection, "storage_bucket_meta");
                assertTableExists(connection, "storage_meta");
                assertSchemaVersion(connection);
            }

            GuiTestSupport.assertTrue(Files.exists(provider.getDatabaseBasePath().resolveSibling("todolist.mv.db")), "H2 数据库文件应创建在临时 game dir 的 todo/local 下");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 schema 初始化时发生异常", exception);
        } finally {
            if (tempGameDir != null) {
                deleteRecursively(tempGameDir);
            }
        }
    }

    /**
     * 断言指定表已经存在。
     *
     * @param connection H2 数据库连接
     * @param tableName 表名
     * @throws Exception 查询失败时抛出
     */
    private static void assertTableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, tableName, null)) {
            GuiTestSupport.assertTrue(resultSet.next(), "缺少 H2 表: " + tableName);
        }
    }

    /**
     * 断言 schema_version 元数据已经写入。
     *
     * @param connection H2 数据库连接
     * @throws Exception 查询失败时抛出
     */
    private static void assertSchemaVersion(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT \"value\" FROM storage_meta WHERE \"key\" = 'schema_version'")) {
            GuiTestSupport.assertTrue(resultSet.next(), "storage_meta 应包含 schema_version");
            GuiTestSupport.assertEquals(H2SchemaInitializer.SCHEMA_VERSION, resultSet.getString(1), "schema_version 应为 v1");
        }
    }

    /**
     * 递归删除测试临时目录。
     *
     * @param root 临时目录
     */
    private static void deleteRecursively(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法删除 H2 schema 测试临时文件: " + path, exception);
                    }
                });
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理 H2 schema 测试临时目录", exception);
        }
    }
}
