package com.todolist.storage;

/**
 * H2MaintenanceGuard 为非 DAO 层入口提供统一的 H2 维护写入前检查。
 */
public final class H2MaintenanceGuard {
    private H2MaintenanceGuard() {
    }

    /**
     * 当前选择 H2 后端时确认维护锁允许写入。
     *
     * @throws StorageUnavailableException H2 维护中时抛出
     */
    public static void ensureWritableIfH2() throws StorageUnavailableException {
        if (!StorageBackendFactory.isH2Selected()) {
            return;
        }
        H2MaintenanceLock.ensureWritable();
    }

    /**
     * 判断当前 H2 后端是否允许写入。
     *
     * @return 允许写入时返回 true
     */
    public static boolean isWritableIfH2() {
        try {
            ensureWritableIfH2();
            return true;
        } catch (StorageUnavailableException exception) {
            return false;
        }
    }
}
