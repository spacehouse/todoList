package com.todolist.storage;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * H2StorageAvailability 记录每个 H2 数据库路径的可用状态和最近失败原因。
 */
public final class H2StorageAvailability {
    private static final Map<Path, Status> STATUS_BY_DATABASE = new HashMap<>();

    /**
     * H2 存储不可用原因。
     */
    public enum Reason {
        DRIVER_MISSING,
        BACKUP_FAILED,
        SCHEMA_INIT_FAILED,
        SCHEMA_UPGRADE_FAILED,
        MIGRATION_FAILED,
        MAINTENANCE,
        READ_FAILED,
        WRITE_FAILED,
        QUERY_FAILED
    }

    private H2StorageAvailability() {
    }

    /**
     * 断言指定数据库当前可用。
     *
     * @param databasePath H2 数据库基础路径
     * @throws StorageUnavailableException 数据库已标记不可用时抛出
     */
    public static void ensureAvailable(Path databasePath) throws StorageUnavailableException {
        Status status;
        synchronized (STATUS_BY_DATABASE) {
            status = STATUS_BY_DATABASE.get(normalize(databasePath));
        }
        if (status != null && !status.available) {
            throw new StorageUnavailableException(status.reason, status.message);
        }
    }

    /**
     * 标记指定数据库可用，并清除既有失败原因。
     *
     * @param databasePath H2 数据库基础路径
     */
    public static void markAvailable(Path databasePath) {
        synchronized (STATUS_BY_DATABASE) {
            STATUS_BY_DATABASE.remove(normalize(databasePath));
        }
    }

    /**
     * 标记指定数据库不可用。
     *
     * @param databasePath H2 数据库基础路径
     * @param reason 不可用原因
     * @param message 说明文本
     */
    public static void markUnavailable(Path databasePath, Reason reason, String message) {
        synchronized (STATUS_BY_DATABASE) {
            STATUS_BY_DATABASE.put(normalize(databasePath), new Status(reason, message));
        }
    }

    /**
     * 返回指定数据库是否已被标记为不可用。
     *
     * @param databasePath H2 数据库基础路径
     * @return 不可用时返回 true
     */
    public static boolean isUnavailable(Path databasePath) {
        synchronized (STATUS_BY_DATABASE) {
            Status status = STATUS_BY_DATABASE.get(normalize(databasePath));
            return status != null && !status.available;
        }
    }

    /**
     * 返回指定数据库的可用状态快照。
     *
     * @param databasePath H2 数据库基础路径
     * @return 状态快照
     */
    public static StatusSnapshot getStatusSnapshot(Path databasePath) {
        synchronized (STATUS_BY_DATABASE) {
            Status status = STATUS_BY_DATABASE.get(normalize(databasePath));
            if (status == null || status.available) {
                return new StatusSnapshot(true, null, "");
            }
            return new StatusSnapshot(false, status.reason, status.message);
        }
    }

    /**
     * 清理测试期间记录的所有可用状态。
     */
    public static void resetForTests() {
        synchronized (STATUS_BY_DATABASE) {
            STATUS_BY_DATABASE.clear();
        }
    }

    /**
     * 清理指定数据库路径的可用状态。
     *
     * @param databasePath H2 数据库基础路径
     */
    public static void reset(Path databasePath) {
        synchronized (STATUS_BY_DATABASE) {
            STATUS_BY_DATABASE.remove(normalize(databasePath));
        }
    }

    /**
     * 规范化数据库路径键。
     *
     * @param databasePath 原始路径
     * @return 规范化路径
     */
    private static Path normalize(Path databasePath) {
        return databasePath == null ? Path.of("") : databasePath.toAbsolutePath().normalize();
    }

    /**
     * 单个数据库的可用状态。
     */
    private static final class Status {
        private final boolean available;
        private final Reason reason;
        private final String message;

        /**
         * 创建不可用状态。
         *
         * @param reason 不可用原因
         * @param message 说明文本
         */
        private Status(Reason reason, String message) {
            this.available = false;
            this.reason = reason;
            this.message = message == null || message.isBlank() ? "H2 storage is unavailable" : message;
        }
    }

    /**
     * H2 可用性状态快照。
     */
    public static final class StatusSnapshot {
        private final boolean available;
        private final Reason reason;
        private final String message;

        /**
         * 创建 H2 可用性状态快照。
         *
         * @param available 是否可用
         * @param reason 不可用原因
         * @param message 最近失败说明
         */
        private StatusSnapshot(boolean available, Reason reason, String message) {
            this.available = available;
            this.reason = reason;
            this.message = message == null ? "" : message;
        }

        /**
         * 返回 H2 是否可用。
         *
         * @return 可用时返回 true
         */
        public boolean isAvailable() {
            return available;
        }

        /**
         * 返回不可用原因。
         *
         * @return 不可用原因，可用时为 null
         */
        public Reason getReason() {
            return reason;
        }

        /**
         * 返回最近失败说明。
         *
         * @return 最近失败说明
         */
        public String getMessage() {
            return message;
        }
    }
}
