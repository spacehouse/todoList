package com.todolist.storage;

/**
 * H2SchemaUpgradeException 表示 H2 schema 升级流程失败。
 */
public class H2SchemaUpgradeException extends Exception {
    /**
     * 创建 H2 schema 升级异常。
     *
     * @param message 异常说明
     */
    public H2SchemaUpgradeException(String message) {
        super(message);
    }

    /**
     * 创建带原因链的 H2 schema 升级异常。
     *
     * @param message 异常说明
     * @param cause 原始异常
     */
    public H2SchemaUpgradeException(String message, Throwable cause) {
        super(message, cause);
    }
}
