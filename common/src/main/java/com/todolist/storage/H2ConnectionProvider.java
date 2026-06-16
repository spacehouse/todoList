package com.todolist.storage;

import com.todolist.platform.DataPathProvider;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * H2ConnectionProvider 负责按当前存储命名空间派生 H2 文件库路径并创建短连接。
 */
public final class H2ConnectionProvider {
    private static final String DATABASE_BASENAME = "todolist";
    private static final String EMBEDDED_JDBC_OPTIONS = ";AUTO_SERVER=FALSE;DATABASE_TO_UPPER=FALSE;TRACE_LEVEL_FILE=0;DB_CLOSE_DELAY=-1";
    private static final ThreadLocal<Boolean> REUSE_BACKGROUND_CONNECTION = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<CachedConnection> CACHED_BACKGROUND_CONNECTION = new ThreadLocal<>();
    private static final AtomicLong REUSABLE_CONNECTION_CONTEXT_VERSION = new AtomicLong(0L);

    /**
     * 创建 H2 连接提供器。
     */
    public H2ConnectionProvider() {
    }

    /**
     * 为当前线程开启嵌入式 H2 连接复用。
     * 仅供 GUI 后台读取线程使用，避免频繁短连接关库带来的额外开销。
     */
    public static void enableEmbeddedConnectionReuseForCurrentThread() {
        REUSE_BACKGROUND_CONNECTION.set(Boolean.TRUE);
    }

    /**
     * 为当前线程关闭嵌入式 H2 连接复用标记。
     * 已建立的线程缓存连接会保留，供同一线程下次读取复用。
     */
    public static void disableEmbeddedConnectionReuseForCurrentThread() {
        REUSE_BACKGROUND_CONNECTION.remove();
    }

    /**
     * 使当前进程内全部线程缓存的可复用 H2 连接失效。
     * 用于服务端停止、TCP 重启、命名空间切换等生命周期边界，避免旧 session 被复用到新上下文。
     */
    public static void invalidateReusableConnections() {
        REUSABLE_CONNECTION_CONTEXT_VERSION.incrementAndGet();
        CachedConnection cached = CACHED_BACKGROUND_CONNECTION.get();
        closeCachedConnection(cached);
        CACHED_BACKGROUND_CONNECTION.remove();
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
        return "jdbc:h2:file:" + normalizedPath + EMBEDDED_JDBC_OPTIONS;
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
        boolean tcpActive = H2TcpServerManager.getStatus().isTcpActive();
        String jdbcUrl = getJdbcUrl();
        if (Boolean.TRUE.equals(REUSE_BACKGROUND_CONNECTION.get())) {
            String user = tcpActive ? config.getAdminUser() : "sa";
            String password = tcpActive ? config.getAdminPassword() : "";
            return openReusableConnection(jdbcUrl, user, password);
        }
        if (tcpActive) {
            return DriverManager.getConnection(jdbcUrl, config.getAdminUser(), config.getAdminPassword());
        }
        return DriverManager.getConnection(jdbcUrl, "sa", "");
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

    /**
     * 为当前线程返回一个可复用的 H2 连接代理。
     * 代理的 close() 为无操作，物理连接会保留在线程缓存中。
     *
     * @param jdbcUrl JDBC URL
     * @return 复用连接代理
     * @throws SQLException 连接创建或重置失败时抛出
     */
    private Connection openReusableConnection(String jdbcUrl, String user, String password) throws SQLException {
        CachedConnection cached = CACHED_BACKGROUND_CONNECTION.get();
        boolean cacheClosed = cached != null && cached.connection().isClosed();
        boolean cacheUrlMatches = cached != null && cached.jdbcUrl().equals(jdbcUrl);
        boolean cacheUserMatches = cached != null && cached.user().equals(user);
        long contextVersion = REUSABLE_CONNECTION_CONTEXT_VERSION.get();
        boolean cacheContextMatches = cached != null && cached.contextVersion() == contextVersion;
        if (cached == null || cacheClosed || !cacheUrlMatches || !cacheUserMatches || !cacheContextMatches) {
            closeCachedConnection(cached);
            cached = new CachedConnection(jdbcUrl, user, contextVersion, DriverManager.getConnection(jdbcUrl, user, password));
            CACHED_BACKGROUND_CONNECTION.set(cached);
        }
        try {
            resetReusableConnectionState(cached.connection());
        } catch (SQLException firstFailure) {
            closeCachedConnection(cached);
            cached = new CachedConnection(jdbcUrl, user, contextVersion, DriverManager.getConnection(jdbcUrl, user, password));
            CACHED_BACKGROUND_CONNECTION.set(cached);
            resetReusableConnectionState(cached.connection());
        }
        Connection physicalConnection = cached.connection();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("close".equals(methodName)) {
                        return null;
                    }
                    if ("isClosed".equals(methodName)) {
                        return physicalConnection.isClosed();
                    }
                    if ("unwrap".equals(methodName) && args != null && args.length == 1 && args[0] instanceof Class<?> targetType) {
                        if (targetType.isInstance(physicalConnection)) {
                            return physicalConnection;
                        }
                    }
                    if ("isWrapperFor".equals(methodName) && args != null && args.length == 1 && args[0] instanceof Class<?> targetType) {
                        return targetType.isInstance(physicalConnection);
                    }
                    return method.invoke(physicalConnection, args);
                }
        );
    }

    /**
     * 在复用连接重新借出前重置其状态，避免上一次事务残留影响当前读取。
     *
     * @param connection 物理连接
     * @throws SQLException 状态重置失败时抛出
     */
    private void resetReusableConnectionState(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            return;
        }
        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        if (connection.isReadOnly()) {
            connection.setReadOnly(false);
        }
        connection.clearWarnings();
    }

    /**
     * 关闭旧的线程缓存嵌入式连接。
     *
     * @param cached 旧缓存连接
     */
    private static void closeCachedConnection(CachedConnection cached) {
        if (cached == null || cached.connection() == null) {
            return;
        }
        try {
            cached.connection().close();
        } catch (SQLException ignored) {
        }
    }

    /**
     * 当前线程缓存的嵌入式连接信息。
     *
     * @param jdbcUrl JDBC URL
     * @param user 用户名
     * @param contextVersion 连接上下文版本
     * @param connection 物理连接
     */
    private record CachedConnection(String jdbcUrl, String user, long contextVersion, Connection connection) {
    }

}
