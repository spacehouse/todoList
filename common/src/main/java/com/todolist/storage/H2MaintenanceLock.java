package com.todolist.storage;

/**
 * H2MaintenanceLock 负责串行化 H2 备份、重载和升级等维护操作，并在维护期间拒绝普通写入。
 */
public final class H2MaintenanceLock {
    private static MaintenanceToken activeToken;

    private H2MaintenanceLock() {
    }

    /**
     * 进入指定 H2 维护操作。
     *
     * @param operation 维护操作名称
     * @return 维护令牌，关闭后释放维护锁
     */
    public static synchronized MaintenanceToken enter(String operation) throws StorageUnavailableException {
        if (activeToken != null && !activeToken.closed) {
            throw busyException(activeToken.operation);
        }
        activeToken = new MaintenanceToken(normalizeOperation(operation), System.currentTimeMillis());
        return activeToken;
    }

    /**
     * 确认当前 H2 存储允许普通写入。
     */
    public static synchronized void ensureWritable() throws StorageUnavailableException {
        if (activeToken != null && !activeToken.closed) {
            throw busyException(activeToken.operation);
        }
    }

    /**
     * 获取当前维护锁状态快照。
     *
     * @return 维护锁状态快照
     */
    public static synchronized StatusSnapshot getStatusSnapshot() {
        if (activeToken == null || activeToken.closed) {
            return new StatusSnapshot(false, "", 0L);
        }
        return new StatusSnapshot(true, activeToken.operation, activeToken.startedAt);
    }

    /**
     * 清理测试期间遗留的维护锁。
     */
    public static synchronized void resetForTests() {
        activeToken = null;
    }

    /**
     * 释放指定维护令牌。
     *
     * @param token 待释放令牌
     */
    private static synchronized void release(MaintenanceToken token) {
        if (activeToken == token) {
            activeToken = null;
        }
    }

    /**
     * 构造维护忙碌异常。
     *
     * @param operation 当前维护操作
     * @return 存储不可用异常
     */
    private static StorageUnavailableException busyException(String operation) {
        return new StorageUnavailableException(
                H2StorageAvailability.Reason.MAINTENANCE,
                "H2 maintenance is running: " + normalizeOperation(operation)
        );
    }

    /**
     * 规范化维护操作名称。
     *
     * @param operation 原始操作名称
     * @return 可展示的操作名称
     */
    private static String normalizeOperation(String operation) {
        return operation == null || operation.isBlank() ? "maintenance" : operation.trim();
    }

    /**
     * H2 维护令牌，使用 try-with-resources 管理锁生命周期。
     */
    public static final class MaintenanceToken implements AutoCloseable {
        private final String operation;
        private final long startedAt;
        private boolean closed;

        /**
         * 创建维护令牌。
         *
         * @param operation 维护操作名称
         * @param startedAt 开始时间
         */
        private MaintenanceToken(String operation, long startedAt) {
            this.operation = operation;
            this.startedAt = startedAt;
        }

        /**
         * 返回维护操作名称。
         *
         * @return 维护操作名称
         */
        public String getOperation() {
            return operation;
        }

        /**
         * 返回维护开始时间。
         *
         * @return Unix 毫秒时间戳
         */
        public long getStartedAt() {
            return startedAt;
        }

        /**
         * 释放维护锁。
         */
        @Override
        public void close() {
            synchronized (H2MaintenanceLock.class) {
                if (closed) {
                    return;
                }
                closed = true;
                release(this);
            }
        }
    }

    /**
     * H2 维护锁状态快照。
     */
    public static final class StatusSnapshot {
        private final boolean active;
        private final String operation;
        private final long startedAt;

        /**
         * 创建 H2 维护锁状态快照。
         *
         * @param active 是否有维护操作运行中
         * @param operation 维护操作名称
         * @param startedAt 开始时间
         */
        private StatusSnapshot(boolean active, String operation, long startedAt) {
            this.active = active;
            this.operation = operation == null ? "" : operation;
            this.startedAt = startedAt;
        }

        /**
         * 返回维护锁是否处于活动状态。
         *
         * @return 活动时返回 true
         */
        public boolean isActive() {
            return active;
        }

        /**
         * 返回当前维护操作名称。
         *
         * @return 维护操作名称
         */
        public String getOperation() {
            return operation;
        }

        /**
         * 返回维护开始时间。
         *
         * @return Unix 毫秒时间戳
         */
        public long getStartedAt() {
            return startedAt;
        }
    }
}
