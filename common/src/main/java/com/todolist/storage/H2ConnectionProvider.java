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
        return DataPathProvider.getTodoDataDir().resolve(DATABASE_BASENAME).toAbsolutePath();
    }

    /**
     * 构造 M1 嵌入式 H2 JDBC URL。
     *
     * @return JDBC URL
     */
    public String getJdbcUrl() {
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
        Files.createDirectories(DataPathProvider.getTodoDataDir());
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("H2 driver is not available on the runtime classpath", exception);
        }
        return DriverManager.getConnection(getJdbcUrl(), "sa", "");
    }
}
