package com.todolist.storage;

import java.util.Locale;

/**
 * H2TcpAccountRole 表示 H2 TCP 外部访问账号角色。
 */
public enum H2TcpAccountRole {
    ADMIN,
    READONLY,
    READWRITE;

    /**
     * 解析命令行角色文本。
     *
     * @param raw 原始角色
     * @return 匹配角色；无法识别时返回 null
     */
    public static H2TcpAccountRole fromCommandValue(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "admin" -> ADMIN;
            case "readonly", "read_only" -> READONLY;
            case "readwrite", "read_write" -> READWRITE;
            default -> null;
        };
    }

    /**
     * 返回命令展示用角色名。
     *
     * @return 小写角色名
     */
    public String commandValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
