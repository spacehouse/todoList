package com.todolist.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.todolist.TodoConstants;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * H2SchemaInitializer 负责创建和升级 M1 schema v1 的表、索引与元数据。
 */
public final class H2SchemaInitializer {
    public static final String SCHEMA_VERSION = "2";
    private static final String COMMENT_KEY_PREFIX = "h2.schema.comment.";
    private static final String[][] TABLE_COMMENTS = {
            {"tasks"},
            {"task_tags"},
            {"projects"},
            {"project_members"},
            {"player_project_state"},
            {"player_hud_starred_projects"},
            {"storage_bucket_meta"},
            {"storage_meta"}
    };
    private static final String[][] COLUMN_COMMENTS = {
            {"tasks", "bucket_type"},
            {"tasks", "owner_uuid"},
            {"tasks", "id"},
            {"tasks", "scope"},
            {"tasks", "project_id"},
            {"tasks", "parent_task_id"},
            {"tasks", "subtask_sort_order"},
            {"tasks", "title"},
            {"tasks", "description"},
            {"tasks", "completed"},
            {"tasks", "priority"},
            {"tasks", "created_at"},
            {"tasks", "due_date"},
            {"tasks", "creator_uuid"},
            {"tasks", "assignee_uuid"},
            {"tasks", "assignee_name"},
            {"tasks", "sort_order"},
            {"tasks", "updated_at"},
            {"task_tags", "bucket_type"},
            {"task_tags", "owner_uuid"},
            {"task_tags", "task_id"},
            {"task_tags", "tag"},
            {"task_tags", "sort_order"},
            {"projects", "bucket_type"},
            {"projects", "id"},
            {"projects", "name"},
            {"projects", "color"},
            {"projects", "scope"},
            {"projects", "owner_uuid"},
            {"projects", "created_at"},
            {"projects", "allow_member_create"},
            {"projects", "allow_all_players_claim_complete"},
            {"projects", "sort_order"},
            {"project_members", "bucket_type"},
            {"project_members", "project_id"},
            {"project_members", "player_uuid"},
            {"project_members", "role"},
            {"project_members", "member_name"},
            {"player_project_state", "player_uuid"},
            {"player_project_state", "active_project_id"},
            {"player_project_state", "hud_visible"},
            {"player_project_state", "last_saved"},
            {"player_hud_starred_projects", "player_uuid"},
            {"player_hud_starred_projects", "project_id"},
            {"player_hud_starred_projects", "project_bucket_type"},
            {"player_hud_starred_projects", "sort_order"},
            {"storage_bucket_meta", "bucket_type"},
            {"storage_bucket_meta", "owner_uuid"},
            {"storage_bucket_meta", "last_saved"},
            {"storage_meta", "key"},
            {"storage_meta", "value"},
            {"storage_meta", "updated_at"}
    };
    private final H2SchemaUpgrader schemaUpgrader;

    /**
     * 创建 H2 schema 初始化器。
     */
    public H2SchemaInitializer() {
        this(new H2SchemaUpgrader());
    }

    /**
     * 创建可注入升级器的 H2 schema 初始化器。
     *
     * @param schemaUpgrader schema 升级器
     */
    public H2SchemaInitializer(H2SchemaUpgrader schemaUpgrader) {
        this.schemaUpgrader = schemaUpgrader == null ? new H2SchemaUpgrader() : schemaUpgrader;
    }

    /**
     * 幂等初始化 schema v1。
     *
     * @param connection H2 数据库连接
     * @throws SQLException schema 初始化失败时抛出
     * @throws H2SchemaUpgradeException schema 升级失败时抛出
     */
    public void initialize(Connection connection) throws SQLException, H2SchemaUpgradeException {
        try (Statement statement = connection.createStatement()) {
            createTables(statement);
        }
        schemaUpgrader.upgradeIfNeeded(connection);
        try (Statement statement = connection.createStatement()) {
            createIndexes(statement);
            createComments(statement);
            ensureTcpUsers(statement);
        }
    }

    /**
     * 创建 M1 schema v1 需要的全部业务表。
     *
     * @param statement SQL statement
     * @throws SQLException DDL 执行失败时抛出
     */
    private void createTables(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS tasks (
                    bucket_type VARCHAR(32) NOT NULL,
                    owner_uuid VARCHAR(64) NOT NULL,
                    id VARCHAR(64) NOT NULL,
                    scope VARCHAR(16) NOT NULL,
                    project_id VARCHAR(64),
                    parent_task_id VARCHAR(64),
                    subtask_sort_order BIGINT NOT NULL DEFAULT 0,
                    title VARCHAR(512) NOT NULL,
                    description VARCHAR(16384),
                    completed BOOLEAN NOT NULL,
                    priority VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL,
                    due_date BIGINT,
                    creator_uuid VARCHAR(64),
                    assignee_uuid VARCHAR(64),
                    assignee_name VARCHAR(256),
                    sort_order BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (bucket_type, owner_uuid, id)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS task_tags (
                    bucket_type VARCHAR(32) NOT NULL,
                    owner_uuid VARCHAR(64) NOT NULL,
                    task_id VARCHAR(64) NOT NULL,
                    tag VARCHAR(128) NOT NULL,
                    sort_order BIGINT NOT NULL,
                    CONSTRAINT uq_task_tags UNIQUE (bucket_type, owner_uuid, task_id, tag)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS projects (
                    bucket_type VARCHAR(32) NOT NULL,
                    id VARCHAR(64) NOT NULL,
                    name VARCHAR(512) NOT NULL,
                    color INT NOT NULL,
                    scope VARCHAR(16) NOT NULL,
                    owner_uuid VARCHAR(64),
                    created_at BIGINT NOT NULL,
                    allow_member_create BOOLEAN NOT NULL,
                    allow_all_players_claim_complete BOOLEAN NOT NULL,
                    sort_order BIGINT NOT NULL,
                    PRIMARY KEY (bucket_type, id)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS project_members (
                    bucket_type VARCHAR(32) NOT NULL,
                    project_id VARCHAR(64) NOT NULL,
                    player_uuid VARCHAR(64) NOT NULL,
                    role VARCHAR(32) NOT NULL,
                    member_name VARCHAR(256),
                    CONSTRAINT uq_project_members UNIQUE (bucket_type, project_id, player_uuid)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS player_project_state (
                    player_uuid VARCHAR(64) NOT NULL,
                    active_project_id VARCHAR(64),
                    hud_visible BOOLEAN NOT NULL,
                    last_saved BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS player_hud_starred_projects (
                    player_uuid VARCHAR(64) NOT NULL,
                    project_id VARCHAR(64) NOT NULL,
                    project_bucket_type VARCHAR(32) NOT NULL,
                    sort_order BIGINT NOT NULL,
                    CONSTRAINT uq_player_hud_starred_projects UNIQUE (player_uuid, project_bucket_type, project_id)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS storage_bucket_meta (
                    bucket_type VARCHAR(32) NOT NULL,
                    owner_uuid VARCHAR(64) NOT NULL,
                    last_saved BIGINT NOT NULL,
                    PRIMARY KEY (bucket_type, owner_uuid)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS storage_meta (
                    "key" VARCHAR(128) NOT NULL,
                    "value" VARCHAR(4096) NOT NULL,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY ("key")
                )
                """);
    }

    /**
     * 创建 M1 schema v1 所需索引。
     *
     * @param statement SQL statement
     * @throws SQLException DDL 执行失败时抛出
     */
    private void createIndexes(Statement statement) throws SQLException {
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_bucket_order ON tasks(bucket_type, owner_uuid, sort_order, created_at, id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_bucket_top_level_order ON tasks(bucket_type, owner_uuid, parent_task_id, sort_order, created_at, id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_parent_sort ON tasks(bucket_type, owner_uuid, parent_task_id, subtask_sort_order, created_at, id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_project_completed ON tasks(project_id, completed)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_project_parent_completed ON tasks(bucket_type, owner_uuid, project_id, parent_task_id, completed)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_scope_completed_priority ON tasks(scope, completed, priority)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_task_tags_bucket_order ON task_tags(bucket_type, owner_uuid, task_id, sort_order)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_projects_bucket_order ON projects(bucket_type, sort_order, created_at, id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_project_members_lookup ON project_members(bucket_type, project_id, player_uuid)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_hud_starred_order ON player_hud_starred_projects(player_uuid, sort_order)");
    }

    /**
     * 写入表和字段注释，便于 DBeaver 等外部工具查看 schema 说明。
     *
     * @param statement SQL statement
     * @throws SQLException 注释 DDL 执行失败时抛出
     */
    private void createComments(Statement statement) throws SQLException {
        Map<String, String> comments = loadSchemaComments();
        for (String[] table : TABLE_COMMENTS) {
            String tableName = table[0];
            commentOn(statement, "TABLE " + quoteIdentifier(tableName), comments.get(tableKey(tableName)));
        }
        for (String[] column : COLUMN_COMMENTS) {
            String tableName = column[0];
            String columnName = column[1];
            commentOn(statement, "COLUMN " + quoteIdentifier(tableName) + "." + quoteIdentifier(columnName), comments.get(columnKey(tableName, columnName)));
        }
    }

    /**
     * 执行单条 H2 COMMENT 语句。
     *
     * @param statement SQL statement
     * @param target 注释目标
     * @param comment 注释内容
     * @throws SQLException 注释 DDL 执行失败时抛出
     */
    private void commentOn(Statement statement, String target, String comment) throws SQLException {
        if (comment == null || comment.isBlank()) {
            return;
        }
        statement.execute("COMMENT ON " + target + " IS '" + escapeSql(comment) + "'");
    }

    /**
     * 从当前语言资源读取 H2 schema 注释，缺失项回退到 en_us。
     *
     * @return 注释键值表
     */
    private Map<String, String> loadSchemaComments() {
        Map<String, String> fallback = loadLangComments("en_us");
        String localeName = normalizeLocale(Locale.getDefault());
        if ("en_us".equals(localeName)) {
            return fallback;
        }
        Map<String, String> preferred = loadLangComments(localeName);
        if (preferred.isEmpty()) {
            return fallback;
        }
        fallback.putAll(preferred);
        return fallback;
    }

    /**
     * 读取指定语言文件中的 H2 schema 注释键。
     *
     * @param localeName 语言文件名，不含扩展名
     * @return 注释键值表
     */
    private Map<String, String> loadLangComments(String localeName) {
        Map<String, String> comments = new HashMap<>();
        String resourcePath = "/assets/todolist/lang/" + localeName + ".json";
        try (InputStream stream = H2SchemaInitializer.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return comments;
            }
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (entry.getKey().startsWith(COMMENT_KEY_PREFIX) && entry.getValue().isJsonPrimitive()) {
                        comments.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to load H2 schema comments from {}", resourcePath, exception);
        }
        return comments;
    }

    /**
     * 将 JVM Locale 转为 Minecraft 语言文件命名。
     *
     * @param locale JVM Locale
     * @return 语言文件名
     */
    private String normalizeLocale(Locale locale) {
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().isBlank()) {
            return "en_us";
        }
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String country = locale.getCountry() == null ? "" : locale.getCountry().toLowerCase(Locale.ROOT);
        return country.isBlank() ? language : language + "_" + country;
    }

    /**
     * 构造表注释多语言键。
     *
     * @param tableName 表名
     * @return 多语言键
     */
    private String tableKey(String tableName) {
        return COMMENT_KEY_PREFIX + "table." + tableName;
    }

    /**
     * 构造字段注释多语言键。
     *
     * @param tableName 表名
     * @param columnName 字段名
     * @return 多语言键
     */
    private String columnKey(String tableName, String columnName) {
        return COMMENT_KEY_PREFIX + "column." + tableName + "." + columnName;
    }

    /**
     * 创建或修复 H2 TCP 外部访问账号。
     *
     * @param statement SQL statement
     * @throws SQLException 用户或授权 DDL 执行失败时抛出
     */
    private void ensureTcpUsers(Statement statement) throws SQLException {
        H2TcpConfig config = H2TcpConfig.load();
        statement.execute("CREATE USER IF NOT EXISTS " + quoteIdentifier(config.getAdminUser()) + " PASSWORD '" + escapeSql(config.getAdminPassword()) + "' ADMIN");
        statement.execute("ALTER USER " + quoteIdentifier(config.getAdminUser()) + " SET PASSWORD '" + escapeSql(config.getAdminPassword()) + "'");
        statement.execute("ALTER USER " + quoteIdentifier(config.getAdminUser()) + " ADMIN TRUE");
        statement.execute("CREATE USER IF NOT EXISTS " + quoteIdentifier(config.getReadonlyUser()) + " PASSWORD '" + escapeSql(config.getReadonlyPassword()) + "'");
        statement.execute("ALTER USER " + quoteIdentifier(config.getReadonlyUser()) + " SET PASSWORD '" + escapeSql(config.getReadonlyPassword()) + "'");
        statement.execute("CREATE USER IF NOT EXISTS " + quoteIdentifier(config.getReadwriteUser()) + " PASSWORD '" + escapeSql(config.getReadwritePassword()) + "'");
        statement.execute("ALTER USER " + quoteIdentifier(config.getReadwriteUser()) + " SET PASSWORD '" + escapeSql(config.getReadwritePassword()) + "'");
        statement.execute("GRANT SELECT ON SCHEMA PUBLIC TO " + quoteIdentifier(config.getReadonlyUser()));
        statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA PUBLIC TO " + quoteIdentifier(config.getReadwriteUser()));
    }

    /**
     * 转义 SQL 字符串字面量。
     *
     * @param value 原始值
     * @return 已转义值
     */
    private String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * 构造安全的 H2 标识符。
     *
     * @param value 原始标识符
     * @return 引号包裹的标识符
     */
    private String quoteIdentifier(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
}
