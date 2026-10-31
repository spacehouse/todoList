package com.todolist.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.todolist.TodoConstants;
import com.todolist.platform.DataPathProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * H2StorageConfig 负责读写 H2 存储的本地配置（当前仅数据库路径覆盖），在配置缺失或损坏时生成安全默认值。
 * 历史 TCP 相关字段（tcpEnabled、port、accounts 等）随 H2 TCP 服务端移除而废弃，
 * 读取时被静默忽略（Gson 忽略未知键），不做任何迁移提示。
 */
public final class H2StorageConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String databasePathOverride = "";
    private transient boolean recreatedFromCorrupt;

    /**
     * 创建 H2 存储配置对象。
     */
    public H2StorageConfig() {
    }

    /**
     * 从 config/todolist-h2.json 读取配置，缺失或损坏时生成安全默认配置。
     *
     * @return 当前 H2 存储配置
     */
    public static H2StorageConfig load() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            H2StorageConfig config = createDefault();
            config.save();
            return config;
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            H2StorageConfig config = GSON.fromJson(json, H2StorageConfig.class);
            if (config == null) {
                throw new IOException("H2 storage config is empty");
            }
            config.normalize();
            return config;
        } catch (Exception exception) {
            preserveCorruptConfig(configPath);
            H2StorageConfig config = createDefault();
            config.recreatedFromCorrupt = true;
            config.save();
            TodoConstants.LOGGER.warn("Failed to read H2 storage config, preserved corrupted file and recreated safe defaults at {}: {}", configPath, exception.getMessage());
            return config;
        }
    }

    /**
     * 创建安全默认配置。
     *
     * @return 默认配置对象
     */
    public static H2StorageConfig createDefault() {
        H2StorageConfig config = new H2StorageConfig();
        config.normalize();
        return config;
    }

    /**
     * 将当前配置保存到 config/todolist-h2.json。
     */
    public void save() {
        normalize();
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to save H2 storage config to {}", configPath, exception);
        }
    }

    /**
     * 返回配置文件路径。
     *
     * @return config/todolist-h2.json 路径
     */
    public static Path getConfigPath() {
        return DataPathProvider.getGameDir().resolve("config").resolve("todolist-h2.json");
    }

    /**
     * 返回数据库路径覆盖值。
     *
     * @return 覆盖路径，空字符串表示使用默认 namespace 路径
     */
    public String getDatabasePathOverride() {
        return databasePathOverride;
    }

    /**
     * 返回配置是否由损坏文件重建。
     *
     * @return 重建时返回 true
     */
    public boolean isRecreatedFromCorrupt() {
        return recreatedFromCorrupt;
    }

    /**
     * 规范化配置字段，修正缺失值。
     */
    private void normalize() {
        databasePathOverride = databasePathOverride == null ? "" : databasePathOverride.trim();
    }

    /**
     * 保留损坏配置文件，便于服主排查。
     *
     * @param configPath 配置路径
     */
    private static void preserveCorruptConfig(Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                return;
            }
            Path corruptPath = configPath.resolveSibling(configPath.getFileName() + ".corrupted");
            Files.move(configPath, corruptPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to preserve corrupt H2 storage config {}", configPath, exception);
        }
    }
}
