package com.todolist.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;

/**
 * H2DiagnosticTestMain 是 H2 迁移 M1-0 的离线诊断入口，只验证驱动、临时数据库和后续阶段所需 API 可用性。
 */
public final class H2DiagnosticTestMain {
    /**
     * 禁止实例化诊断入口类。
     */
    private H2DiagnosticTestMain() {
    }

    /**
     * 执行 H2 驱动加载、临时数据库 smoke test、幂等 DDL、脚本、备份和 TCP API 诊断。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 任一诊断失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Class.forName("org.h2.Driver");
        Path tempDir = Files.createTempDirectory("todolist-h2-m1-0-");
        try {
            runDatabaseSmokeTest(tempDir);
            verifyTcpServerApi();
            System.out.println("H2 M1-0 diagnostic passed in temp dir: " + tempDir);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * 在临时目录创建 H2 文件库，并验证 SELECT、幂等索引、SCRIPT、RUNSCRIPT 和 BACKUP 语法。
     *
     * @param tempDir 临时测试目录
     * @throws SQLException SQL 执行失败时抛出
     * @throws IOException 临时脚本或备份路径处理失败时抛出
     */
    private static void runDatabaseSmokeTest(Path tempDir) throws SQLException, IOException {
        Path databasePath = tempDir.resolve("todolist-h2-smoke");
        String jdbcUrl = "jdbc:h2:file:" + databasePath.toAbsolutePath()
                + ";AUTO_SERVER=FALSE;DATABASE_TO_UPPER=FALSE;TRACE_LEVEL_FILE=0";
        Path scriptPath = tempDir.resolve("smoke-script.sql");
        Path backupPath = tempDir.resolve("smoke-backup.zip");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("H2 SELECT 1 smoke test returned unexpected value");
                }
            }
            statement.execute("CREATE TABLE IF NOT EXISTS smoke_tasks (id VARCHAR(36) PRIMARY KEY, title VARCHAR(255) NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_smoke_tasks_title ON smoke_tasks(title)");
            statement.execute("MERGE INTO smoke_tasks KEY(id) VALUES ('00000000-0000-0000-0000-000000000001', '中文标题 smoke')");
            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM smoke_tasks")) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new SQLException("H2 SELECT smoke test returned unexpected count");
                }
            }
            statement.execute("SCRIPT TO '" + normalizeSqlPath(scriptPath) + "'");
            statement.execute("BACKUP TO '" + normalizeSqlPath(backupPath) + "'");
        }

        if (!Files.exists(scriptPath) || !Files.exists(backupPath)) {
            throw new IOException("H2 SCRIPT or BACKUP output was not created in the temporary directory");
        }

        Path restoredDatabasePath = tempDir.resolve("todolist-h2-restored");
        String restoredJdbcUrl = "jdbc:h2:file:" + restoredDatabasePath.toAbsolutePath()
                + ";AUTO_SERVER=FALSE;DATABASE_TO_UPPER=FALSE;TRACE_LEVEL_FILE=0";
        try (Connection connection = DriverManager.getConnection(restoredJdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM '" + normalizeSqlPath(scriptPath) + "'");
            try (ResultSet resultSet = statement.executeQuery("SELECT title FROM smoke_tasks")) {
                if (!resultSet.next() || !"中文标题 smoke".equals(resultSet.getString(1))) {
                    throw new SQLException("H2 RUNSCRIPT smoke test did not restore expected data");
                }
            }
        }
    }

    /**
     * 验证 H2 TCP Server 工具类和 createTcpServer API 可被测试 classpath 访问。
     *
     * @throws ReflectiveOperationException H2 TCP API 不存在或不可访问时抛出
     */
    private static void verifyTcpServerApi() throws ReflectiveOperationException {
        Class<?> serverClass = Class.forName("org.h2.tools.Server");
        serverClass.getMethod("createTcpServer", String[].class);
    }

    /**
     * 将 Windows 路径转换为 H2 SQL 字符串中稳定可用的路径形式。
     *
     * @param path 需要写入 SQL 的路径
     * @return 已转义单引号并统一分隔符的绝对路径
     */
    private static String normalizeSqlPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/").replace("'", "''");
    }

    /**
     * 递归清理本诊断创建的临时目录。
     *
     * @param root 临时目录根路径
     * @throws IOException 删除失败时抛出
     */
    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to delete temporary H2 diagnostic path: " + path, e);
                }
            });
        }
    }
}
