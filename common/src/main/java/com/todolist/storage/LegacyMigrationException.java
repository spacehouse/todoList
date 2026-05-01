package com.todolist.storage;

import java.io.IOException;

/**
 * LegacyMigrationException 表示旧 NBT 数据迁移或预检失败。
 */
public class LegacyMigrationException extends IOException {
    /**
     * 创建迁移异常。
     *
     * @param message 异常说明
     */
    public LegacyMigrationException(String message) {
        super(message);
    }

    /**
     * 创建带原因的迁移异常。
     *
     * @param message 异常说明
     * @param cause 原始异常
     */
    public LegacyMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
