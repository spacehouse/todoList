package com.todolist.storage;

import com.todolist.platform.DataPathProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * H2ConnectionProvider 负责按当前存储命名空间派生 H2 文件库路径并创建短连接。
 */
public final class H2ConnectionProvider {
    private static final String DATABASE_BASENAME = "todolist";

    /**
     * 创建 H2 连接提供器。
     */
    public H2ConnectionProvider() {
    }

    /**
     * 返回当前命名空间的 H2 数据库基础路径，不包含 .mv.db 后缀。
     *
     * @return H2 数据库基础路径
     */
    public Path getDatabaseBasePath() {
        H2TcpConfig config = H2TcpConfig.load();
        String override = config.getDatabasePathOverride();
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return DataPathProvider.getTodoDataDir().resolve(DATABASE_BASENAME).toAbsolutePath().normalize();
    }

    /**
     * 构造 M1 嵌入式 H2 JDBC URL。
     *
     * @return JDBC URL
     */
    public String getJdbcUrl() {
        H2TcpServerManager.StatusSnapshot tcpStatus = H2TcpServerManager.getStatus();
        if (tcpStatus.isTcpActive()) {
            return H2TcpServerManager.buildTcpJdbcUrl(getDatabaseBasePath());
        }
        String normalizedPath = getDatabaseBasePath().toString().replace("\\", "/");
        return "jdbc:h2:file:" + normalizedPath + ";AUTO_SERVER=FALSE;DATABASE_TO_UPPER=FALSE;TRACE_LEVEL_FILE=0";
    }

    /**
     * 打开一个短生命周期 H2 连接。
     *
     * @return H2 数据库连接
     * @throws IOException 数据目录创建失败时抛出
     * @throws SQLException 连接数据库失败时抛出
     */
    public Connection openConnection() throws IOException, SQLException {
        Path databaseBasePath = getDatabaseBasePath();
        Files.createDirectories(databaseBasePath.getParent());
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("H2 driver is not available on the runtime classpath", exception);
        }
        H2TcpConfig config = H2TcpConfig.load();
        if (H2TcpServerManager.getStatus().isTcpActive()) {
            return DriverManager.getConnection(getJdbcUrl(), config.getAdminUser(), config.getAdminPassword());
        }
        return DriverManager.getConnection(getJdbcUrl(), "sa", "");
    }

    /**
     * 打开 H2 初始化专用连接，用于首次创建 schema 和 TCP 账号。
     *
     * @return H2 初始化连接
     * @throws IOException 数据目录创建失败时抛出
     * @throws SQLException 连接数据库失败时抛出
     */
    public Connection openBootstrapConnection() throws IOException, SQLException {
        Path databaseBasePath = getDatabaseBasePath();
        Files.createDirectories(databaseBasePath.getParent());
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("H2 driver is not available on the runtime classpath", exception);
        }
        return DriverManager.getConnection(getJdbcUrl(), "sa", "");
    }

    /**
     * 打开强制嵌入式初始化连接，用于 TCP 首次启动前预创建数据库。
     *
     * @return H2 嵌入式初始化连接
     * @throws IOException 数据目录创建失败时抛出
     * @throws SQLException 连接数据库失败时抛出
     */
    public Connection openEmbeddedBootstrapConnection() throws IOException, SQLException {
        Path databaseBasePath = getDatabaseBasePath();
        Files.createDirectories(databaseBasePath.getParent());
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("H2 driver is not available on the runtime classpath", exception);
        }
        String normalizedPath = databaseBasePath.toString().replace("\\", "/");
        String jdbcUrl = "jdbc:h2:file:" + normalizedPath + ";AUTO_SERVER=FALSE;DATABASE_TO_UPPER=FALSE;TRACE_LEVEL_FILE=0";
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }
}
