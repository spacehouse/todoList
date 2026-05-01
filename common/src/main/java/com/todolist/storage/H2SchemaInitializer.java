package com.todolist.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * H2SchemaInitializer 负责创建和升级 M1 schema v1 的表、索引与元数据。
 */
public final class H2SchemaInitializer {
    public static final String SCHEMA_VERSION = "1";

    /**
     * 创建 H2 schema 初始化器。
     */
    public H2SchemaInitializer() {
    }

    /**
     * 幂等初始化 schema v1。
     *
     * @param connection H2 数据库连接
     * @throws SQLException schema 初始化失败时抛出
     */
    public void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            createTables(statement);
            createIndexes(statement);
            upsertSchemaVersion(statement);
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
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_project_completed ON tasks(project_id, completed)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_tasks_scope_completed_priority ON tasks(scope, completed, priority)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_task_tags_bucket_order ON task_tags(bucket_type, owner_uuid, task_id, sort_order)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_projects_bucket_order ON projects(bucket_type, sort_order, created_at, id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_project_members_lookup ON project_members(bucket_type, project_id, player_uuid)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_hud_starred_order ON player_hud_starred_projects(player_uuid, sort_order)");
    }

    /**
     * 写入当前 schema 版本元数据。
     *
     * @param statement SQL statement
     * @throws SQLException 写入元数据失败时抛出
     */
    private void upsertSchemaVersion(Statement statement) throws SQLException {
        statement.execute("MERGE INTO storage_meta KEY(\"key\") VALUES ('schema_version', '" + SCHEMA_VERSION + "', " + System.currentTimeMillis() + ")");
    }
}
