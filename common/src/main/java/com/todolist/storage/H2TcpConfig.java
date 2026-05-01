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
import java.security.SecureRandom;
import java.util.Base64;

/**
 * H2TcpConfig 负责读写 H2 TCP 外部访问配置，并在配置缺失或损坏时生成安全默认值。
 */
public final class H2TcpConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_PORT = 9092;
    private static final int DEFAULT_MAX_PORT_ATTEMPTS = 16;

    private boolean tcpEnabled = false;
    private String bindAddress = "127.0.0.1";
    private int port = DEFAULT_PORT;
    private boolean autoIncrementPort = true;
    private int maxPortAttempts = DEFAULT_MAX_PORT_ATTEMPTS;
    private boolean allowRemote = false;
    private String databasePathOverride = "";
    private AccountCredentials accounts = AccountCredentials.createDefault();
    private transient boolean recreatedFromCorrupt;

    /**
     * 创建 H2 TCP 配置对象。
     */
    public H2TcpConfig() {
    }

    /**
     * 从 config/todolist-h2.json 读取配置，缺失或损坏时生成安全默认配置。
     *
     * @return 当前 H2 TCP 配置
     */
    public static H2TcpConfig load() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            H2TcpConfig config = createDefault();
            config.save();
            return config;
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            H2TcpConfig config = GSON.fromJson(json, H2TcpConfig.class);
            if (config == null) {
                throw new IOException("H2 TCP config is empty");
            }
            boolean remoteWeakPassword = config.hasRemoteWeakPasswordBeforeNormalize();
            config.normalize();
            if (remoteWeakPassword) {
                TodoConstants.LOGGER.warn("H2 TCP remote access uses weak credentials; disabling TCP until passwords are reset");
                config.tcpEnabled = false;
            }
            return config;
        } catch (Exception exception) {
            preserveCorruptConfig(configPath);
            H2TcpConfig config = createDefault();
            config.recreatedFromCorrupt = true;
            config.save();
            TodoConstants.LOGGER.warn("Failed to read H2 TCP config, preserved corrupted file and recreated safe defaults at {}: {}", configPath, exception.getMessage());
            return config;
        }
    }

    /**
     * 创建安全默认配置。
     *
     * @return 默认配置对象
     */
    public static H2TcpConfig createDefault() {
        H2TcpConfig config = new H2TcpConfig();
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
            TodoConstants.LOGGER.warn("Failed to save H2 TCP config to {}", configPath, exception);
        }
    }

    /**
     * 重置指定角色的密码并保存配置。
     *
     * @param role 账号角色
     * @return 新密码
     */
    public String resetPassword(H2TcpAccountRole role) {
        String password = generatePassword();
        setPassword(role, password);
        save();
        return password;
    }

    /**
     * 设置指定角色的密码但不立即保存。
     *
     * @param role 账号角色
     * @param password 新密码
     */
    public void setPassword(H2TcpAccountRole role, String password) {
        switch (role) {
            case ADMIN -> accounts.adminPassword = password;
            case READONLY -> accounts.readonlyPassword = password;
            case READWRITE -> accounts.readwritePassword = password;
        }
    }

    /**
     * 生成新的外部账号随机密码。
     *
     * @return 新密码
     */
    public static String createPassword() {
        return generatePassword();
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
     * 返回 TCP 是否启用。
     *
     * @return 启用时返回 true
     */
    public boolean isTcpEnabled() {
        return tcpEnabled;
    }

    /**
     * 返回绑定地址。
     *
     * @return TCP 绑定地址
     */
    public String getBindAddress() {
        return bindAddress;
    }

    /**
     * 返回起始端口。
     *
     * @return TCP 起始端口
     */
    public int getPort() {
        return port;
    }

    /**
     * 返回端口冲突时是否自动递增。
     *
     * @return 自动递增时返回 true
     */
    public boolean isAutoIncrementPort() {
        return autoIncrementPort;
    }

    /**
     * 返回端口尝试次数上限。
     *
     * @return 尝试次数
     */
    public int getMaxPortAttempts() {
        return maxPortAttempts;
    }

    /**
     * 返回是否允许远程访问。
     *
     * @return 允许远程访问时返回 true
     */
    public boolean isAllowRemote() {
        return allowRemote;
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
     * 返回 admin 用户名。
     *
     * @return admin 用户名
     */
    public String getAdminUser() {
        return accounts.adminUser;
    }

    /**
     * 返回 admin 密码。
     *
     * @return admin 密码
     */
    public String getAdminPassword() {
        return accounts.adminPassword;
    }

    /**
     * 返回 readonly 用户名。
     *
     * @return readonly 用户名
     */
    public String getReadonlyUser() {
        return accounts.readonlyUser;
    }

    /**
     * 返回 readonly 密码。
     *
     * @return readonly 密码
     */
    public String getReadonlyPassword() {
        return accounts.readonlyPassword;
    }

    /**
     * 返回 readwrite 用户名。
     *
     * @return readwrite 用户名
     */
    public String getReadwriteUser() {
        return accounts.readwriteUser;
    }

    /**
     * 返回 readwrite 密码。
     *
     * @return readwrite 密码
     */
    public String getReadwritePassword() {
        return accounts.readwritePassword;
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
     * 规范化配置字段，修正缺失或越界值。
     */
    private void normalize() {
        bindAddress = bindAddress == null || bindAddress.isBlank() ? "127.0.0.1" : bindAddress.trim();
        if (!allowRemote) {
            bindAddress = "127.0.0.1";
        }
        if (port <= 0 || port > 65535) {
            port = DEFAULT_PORT;
        }
        if (maxPortAttempts <= 0 || maxPortAttempts > 128) {
            maxPortAttempts = DEFAULT_MAX_PORT_ATTEMPTS;
        }
        databasePathOverride = databasePathOverride == null ? "" : databasePathOverride.trim();
        if (accounts == null) {
            accounts = AccountCredentials.createDefault();
        }
        accounts.normalize();
    }

    /**
     * 判断远程访问配置是否仍使用弱密码。
     *
     * @return 存在弱密码时返回 true
     */
    private boolean hasRemoteWeakPasswordBeforeNormalize() {
        return allowRemote && (accounts == null
                || isWeak(accounts.adminPassword)
                || isWeak(accounts.readonlyPassword)
                || isWeak(accounts.readwritePassword));
    }

    /**
     * 判断密码是否不满足远程访问最低强度。
     *
     * @param password 密码
     * @return 弱密码时返回 true
     */
    private boolean isWeak(String password) {
        return password == null || password.length() < 24;
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
            TodoConstants.LOGGER.warn("Failed to preserve corrupt H2 TCP config {}", configPath, exception);
        }
    }

    /**
     * 生成外部账号随机密码。
     *
     * @return URL 安全的随机密码
     */
    private static String generatePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * H2 TCP 外部账号凭据。
     */
    private static final class AccountCredentials {
        private String adminUser = "todo_admin";
        private String adminPassword = generatePassword();
        private String readonlyUser = "todo_readonly";
        private String readonlyPassword = generatePassword();
        private String readwriteUser = "todo_readwrite";
        private String readwritePassword = generatePassword();

        /**
         * 创建默认账号凭据。
         *
         * @return 默认账号凭据
         */
        private static AccountCredentials createDefault() {
            return new AccountCredentials();
        }

        /**
         * 修正缺失账号和密码。
         */
        private void normalize() {
            adminUser = normalizeUser(adminUser, "todo_admin");
            readonlyUser = normalizeUser(readonlyUser, "todo_readonly");
            readwriteUser = normalizeUser(readwriteUser, "todo_readwrite");
            adminPassword = normalizePassword(adminPassword);
            readonlyPassword = normalizePassword(readonlyPassword);
            readwritePassword = normalizePassword(readwritePassword);
        }

        /**
         * 规范化用户名。
         *
         * @param user 原用户名
         * @param fallback 默认用户名
         * @return 规范用户名
         */
        private String normalizeUser(String user, String fallback) {
            return user == null || user.isBlank() ? fallback : user.trim();
        }

        /**
         * 规范化密码。
         *
         * @param password 原密码
         * @return 规范密码
         */
        private String normalizePassword(String password) {
            return password == null || password.length() < 24 ? generatePassword() : password;
        }
    }
}
