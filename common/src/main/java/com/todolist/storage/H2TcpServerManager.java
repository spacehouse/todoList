package com.todolist.storage;

import com.todolist.TodoConstants;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * H2TcpServerManager 管理可选 H2 TCP Server 的启动、停止、重启和状态快照。
 * TCP 服务器固定仅接受本机回环连接，用作同进程跨 classloader 的内部通道，
 * 从机制上不提供远程访问能力。
 */
public final class H2TcpServerManager {
    /** H2 TCP 服务器唯一允许的连接地址，始终为本机回环。 */
    public static final String LOOPBACK_BIND_ADDRESS = "127.0.0.1";
    private static Object server;
    private static StatusSnapshot status = StatusSnapshot.disabled();

    private H2TcpServerManager() {
    }

    /**
     * 确保 TCP 配置启用时已启动 H2 TCP Server。
     *
     * @param config TCP 配置
     * @return 启动状态快照
     */
    public static synchronized StatusSnapshot ensureStarted(H2TcpConfig config) {
        H2TcpConfig safeConfig = config == null ? H2TcpConfig.load() : config;
        if (!safeConfig.isTcpEnabled()) {
            stop();
            status = StatusSnapshot.disabled();
            return status;
        }
        if (server != null && status.tcpActive) {
            return status;
        }
        stop();
        int attempts = safeConfig.isAutoIncrementPort() ? safeConfig.getMaxPortAttempts() : 1;
        for (int i = 0; i < attempts; i++) {
            int port = safeConfig.getPort() + i;
            try {
                server = createAndStartServer(safeConfig, port);
                status = StatusSnapshot.tcpActive(port);
                TodoConstants.LOGGER.info("H2 TCP server started on {}:{}", LOOPBACK_BIND_ADDRESS, port);
                return status;
            } catch (Exception exception) {
                server = null;
                String failure = summarizeFailure(exception);
                status = StatusSnapshot.fallback("TCP startup failed on port " + port + ": " + failure);
                TodoConstants.LOGGER.warn("Failed to start H2 TCP server on configured port {}; falling back if possible: {}", port, failure);
                if (!safeConfig.isAutoIncrementPort()) {
                    break;
                }
            }
        }
        return status;
    }

    /**
     * 重启 H2 TCP Server。
     *
     * @param config TCP 配置
     * @return 重启后的状态快照
     */
    public static synchronized StatusSnapshot restart(H2TcpConfig config) {
        stop();
        return ensureStarted(config);
    }

    /**
     * 停止当前 H2 TCP Server。
     */
    public static synchronized void stop() {
        H2ConnectionProvider.invalidateReusableConnections();
        if (server == null) {
            status = StatusSnapshot.disabled();
            return;
        }
        try {
            Method stop = server.getClass().getMethod("stop");
            stop.invoke(server);
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to stop H2 TCP server cleanly", exception);
        } finally {
            server = null;
            status = StatusSnapshot.disabled();
        }
    }

    /**
     * 返回当前 TCP 状态快照。
     *
     * @return 状态快照
     */
    public static synchronized StatusSnapshot getStatus() {
        return status;
    }

    /**
     * 构造内部 DAO 使用的 TCP JDBC URL。
     *
     * @param databaseBasePath H2 数据库基础路径
     * @return TCP JDBC URL
     */
    public static synchronized String buildTcpJdbcUrl(Path databaseBasePath) {
        Path normalizedPath = databaseBasePath.toAbsolutePath().normalize();
        String h2Path = normalizedPath.toString().replace("\\", "/");
        return "jdbc:h2:tcp://" + LOOPBACK_BIND_ADDRESS + ":" + status.actualPort + "/" + h2Path
                + ";DATABASE_TO_UPPER=FALSE";
    }

    /**
     * 通过反射创建并启动 H2 TCP Server，避免 NBT 模式静态依赖 H2 类。
     * 不携带 -tcpAllowOthers 参数，H2 会在协议层拒绝所有非本机连接。
     *
     * @param config TCP 配置
     * @param port 目标端口
     * @return H2 Server 实例
     * @throws Exception 启动失败时抛出
     */
    private static Object createAndStartServer(H2TcpConfig config, int port) throws Exception {
        Class<?> serverClass = Class.forName("org.h2.tools.Server");
        Method createTcpServer = serverClass.getMethod("createTcpServer", String[].class);
        String[] args = new String[] {"-tcp", "-tcpPort", String.valueOf(port)};
        Object created = createTcpServer.invoke(null, (Object) args);
        Method start = created.getClass().getMethod("start");
        start.invoke(created);
        return created;
    }

    /**
     * 提取启动异常的简短说明，避免预期端口冲突刷出完整堆栈。
     *
     * @param exception 原始异常
     * @return 简短失败说明
     */
    private static String summarizeFailure(Exception exception) {
        Throwable current = exception instanceof InvocationTargetException invocationTargetException
                ? invocationTargetException.getCause()
                : exception;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) {
            return "unknown";
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    /**
     * H2 TCP Server 状态快照。
     */
    public static final class StatusSnapshot {
        private final boolean tcpEnabled;
        private final boolean tcpActive;
        private final int actualPort;
        private final String mode;
        private final String lastFailure;

        /**
         * 创建 H2 TCP 状态快照。
         */
        private StatusSnapshot(boolean tcpEnabled, boolean tcpActive, int actualPort, String mode, String lastFailure) {
            this.tcpEnabled = tcpEnabled;
            this.tcpActive = tcpActive;
            this.actualPort = actualPort;
            this.mode = mode;
            this.lastFailure = lastFailure;
        }

        /**
         * 创建禁用状态。
         *
         * @return 状态快照
         */
        private static StatusSnapshot disabled() {
            return new StatusSnapshot(false, false, -1, "embedded", "");
        }

        /**
         * 创建 TCP 活跃状态，绑定地址固定为本机回环。
         *
         * @param actualPort 实际端口
         * @return 状态快照
         */
        private static StatusSnapshot tcpActive(int actualPort) {
            return new StatusSnapshot(true, true, actualPort, "tcp", "");
        }

        /**
         * 创建回退嵌入式状态。
         *
         * @param lastFailure 最近失败原因
         * @return 状态快照
         */
        private static StatusSnapshot fallback(String lastFailure) {
            return new StatusSnapshot(true, false, -1, "embedded_fallback", lastFailure);
        }

        /**
         * 返回 TCP 是否启用。
         *
         * @return 启用时返回 true
         */
        public boolean isTcpEnabled() {
            return tcpEnabled;
        }

        /**
         * 返回 TCP 是否正在服务。
         *
         * @return 活跃时返回 true
         */
        public boolean isTcpActive() {
            return tcpActive;
        }

        /**
         * 返回绑定地址，固定为本机回环地址。
         *
         * @return 绑定地址
         */
        public String getBindAddress() {
            return LOOPBACK_BIND_ADDRESS;
        }

        /**
         * 返回实际端口。
         *
         * @return 实际端口，未启动时为 -1
         */
        public int getActualPort() {
            return actualPort;
        }

        /**
         * 返回连接模式。
         *
         * @return tcp、embedded 或 embedded_fallback
         */
        public String getMode() {
            return mode;
        }

        /**
         * 返回最近失败说明。
         *
         * @return 最近失败说明
         */
        public String getLastFailure() {
            return lastFailure;
        }
    }
}
