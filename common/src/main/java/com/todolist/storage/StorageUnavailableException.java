package com.todolist.storage;

import java.io.IOException;

/**
 * StorageUnavailableException 表示当前配置的持久化后端不可用，调用方应停止继续写入。
 */
public class StorageUnavailableException extends IOException {
    private final H2StorageAvailability.Reason reason;

    /**
     * 创建存储不可用异常。
     *
     * @param reason 不可用原因
     * @param message 异常说明
     */
    public StorageUnavailableException(H2StorageAvailability.Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * 创建带原因链的存储不可用异常。
     *
     * @param reason 不可用原因
     * @param message 异常说明
     * @param cause 原始异常
     */
    public StorageUnavailableException(H2StorageAvailability.Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * 返回存储不可用原因。
     *
     * @return 不可用原因
     */
    public H2StorageAvailability.Reason getReason() {
        return reason;
    }

    /**
     * 在异常链中查找存储不可用异常。
     *
     * @param throwable 待检查异常
     * @return 找到的存储不可用异常；不存在时返回 null
     */
    public static StorageUnavailableException find(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StorageUnavailableException storageUnavailableException) {
                return storageUnavailableException;
            }
            current = current.getCause();
        }
        return null;
    }
}
