package com.todolist.platform;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 提供数据存储路径的工具类。
 * 用于获取游戏目录、模组数据目录、项目目录及玩家数据目录等。
 */
public final class DataPathProvider {
    /** 模组数据根目录名称 */
    public static final String TODO_FOLDER = "todo";
    /** 默认本地存储域名称 */
    public static final String LOCAL_STORAGE_NAMESPACE = "local";
    /** 项目数据目录名称 */
    public static final String PROJECTS_FOLDER = "projects";
    /** 玩家数据目录名称 */
    public static final String PLAYERS_FOLDER = "players";

    private static Supplier<Path> gameDirSupplier;
    private static volatile String storageNamespace = LOCAL_STORAGE_NAMESPACE;

    private DataPathProvider() {
    }

    /**
     * 设置游戏目录提供器。
     * 必须在调用 getGameDir 之前，由平台实现（如 Fabric 或 Forge）完成初始化。
     *
     * @param supplier 游戏目录 Path 的提供器
     */
    public static void setGameDirSupplier(Supplier<Path> supplier) {
        gameDirSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    /**
     * 获取游戏根目录。
     *
     * @return 游戏根目录 Path
     * @throws IllegalStateException 当 supplier 未初始化时抛出
     */
    public static Path getGameDir() {
        if (gameDirSupplier == null) {
            throw new IllegalStateException("Game directory supplier has not been initialized. " +
                    "Make sure to call setGameDirSupplier during mod initialization.");
        }
        return gameDirSupplier.get().toAbsolutePath();
    }

    /**
     * 获取待办模组数据根目录。
     *
     * @return 模组数据根目录 Path
     */
    public static Path getTodoDataDir() {
        return getGameDir().resolve(TODO_FOLDER).resolve(storageNamespace);
    }

    /**
     * 设置当前存储域名称。
     *
     * @param namespace 存储域（如 local 或服务器地址派生值）
     */
    public static void setStorageNamespace(String namespace) {
        storageNamespace = sanitizeNamespace(namespace);
    }

    /**
     * 重置为本地存储域。
     */
    public static void resetStorageNamespace() {
        storageNamespace = LOCAL_STORAGE_NAMESPACE;
    }

    /**
     * 获取当前存储域名称。
     */
    public static String getStorageNamespace() {
        return storageNamespace;
    }

    /**
     * 规范化存储域名称，确保可作为目录名。
     */
    private static String sanitizeNamespace(String namespace) {
        if (namespace == null) {
            return LOCAL_STORAGE_NAMESPACE;
        }
        String trimmed = namespace.trim();
        if (trimmed.isEmpty()) {
            return LOCAL_STORAGE_NAMESPACE;
        }
        String sanitized = trimmed.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (sanitized.isEmpty()) {
            return LOCAL_STORAGE_NAMESPACE;
        }
        return sanitized.toLowerCase();
    }

    /**
     * 获取项目数据存储目录。
     *
     * @return 项目目录 Path
     */
    public static Path getProjectsDir() {
        return getTodoDataDir().resolve(PROJECTS_FOLDER);
    }

    /**
     * 获取项目关联的玩家数据目录。
     *
     * @return 玩家数据目录 Path
     */
    public static Path getProjectPlayersDir() {
        return getProjectsDir().resolve(PLAYERS_FOLDER);
    }

    /**
     * 获取全局任务关联的玩家数据目录。
     *
     * @return 玩家数据目录 Path
     */
    public static Path getTaskPlayersDir() {
        return getTodoDataDir().resolve(PLAYERS_FOLDER);
    }
}

