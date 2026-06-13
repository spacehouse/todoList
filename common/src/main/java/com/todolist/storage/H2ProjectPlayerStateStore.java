package com.todolist.storage;

import com.todolist.project.ProjectPlayerStateStorage.ProjectPlayerState;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * H2ProjectPlayerStateStore 负责在 H2 后端读写玩家项目状态。
 */
public final class H2ProjectPlayerStateStore {
    private final H2ConnectionProvider connectionProvider;
    private final H2StorageBootstrap bootstrap;

    /**
     * 创建默认 H2 玩家项目状态存储。
     */
    public H2ProjectPlayerStateStore() {
        this(new H2ConnectionProvider(), new H2StorageBootstrap());
    }

    /**
     * 创建可注入依赖的 H2 玩家项目状态存储。
     *
     * @param connectionProvider H2 连接提供器
     * @param bootstrap H2 启动器
     */
    public H2ProjectPlayerStateStore(H2ConnectionProvider connectionProvider, H2StorageBootstrap bootstrap) {
        this.connectionProvider = connectionProvider;
        this.bootstrap = bootstrap;
    }

    /**
     * 判断玩家项目状态是否存在。
     *
     * @param playerUuid 玩家 UUID
     * @return 存在时返回 true
     * @throws IOException 查询失败时抛出
     */
    public boolean hasPlayerState(UUID playerUuid) throws IOException {
        if (playerUuid == null) {
            return false;
        }
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM player_project_state WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.QUERY_FAILED, "Failed to query H2 project player state", exception);
        }
    }

    /**
     * 保存玩家项目状态。
     *
     * @param playerUuid 玩家 UUID
     * @param state 玩家项目状态
     * @throws IOException 保存失败时抛出
     */
    public void savePlayerState(UUID playerUuid, ProjectPlayerState state) throws IOException {
        if (playerUuid == null) {
            throw new IOException("playerUuid is required");
        }
        H2MaintenanceLock.ensureWritable();
        ProjectPlayerState safeState = state == null ? ProjectPlayerState.empty() : state;
        long now = System.currentTimeMillis();
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertState(connection, playerUuid, safeState, now);
                replaceStarredProjects(connection, playerUuid, safeState);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.WRITE_FAILED, "Failed to save H2 project player state", exception);
        }
    }

    /**
     * 读取玩家项目状态。
     *
     * @param playerUuid 玩家 UUID
     * @return 玩家项目状态，不存在时返回空状态
     * @throws IOException 读取失败时抛出
     */
    public ProjectPlayerState loadPlayerState(UUID playerUuid) throws IOException {
        if (playerUuid == null) {
            return ProjectPlayerState.empty();
        }
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT active_project_id, hud_visible FROM player_project_state WHERE player_uuid = ?
                     """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return ProjectPlayerState.empty();
                }
                return new ProjectPlayerState(
                        resultSet.getString("active_project_id"),
                        loadStarredProjects(connection, playerUuid),
                        resultSet.getBoolean("hud_visible")
                );
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.READ_FAILED, "Failed to load H2 project player state", exception);
        }
    }

    /**
     * 标记当前 H2 数据库不可用并构造统一异常。
     *
     * @param reason 不可用原因
     * @param message 异常说明
     * @param cause 原始异常
     * @return 存储不可用异常
     */
    private StorageUnavailableException markUnavailable(H2StorageAvailability.Reason reason, String message, Throwable cause) {
        if (!H2StorageAvailability.isTransientLockFailure(cause)) {
            H2StorageAvailability.markUnavailable(connectionProvider.getDatabaseBasePath(), reason, cause == null ? message : cause.getMessage());
        }
        return new StorageUnavailableException(reason, message, cause);
    }

    /**
     * 写入玩家项目状态主体。
     *
     * @param connection H2 连接
     * @param playerUuid 玩家 UUID
     * @param state 玩家项目状态
     * @param lastSaved 最后保存时间
     * @throws SQLException 写入失败时抛出
     */
    private void upsertState(Connection connection, UUID playerUuid, ProjectPlayerState state, long lastSaved) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                MERGE INTO player_project_state KEY(player_uuid)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setString(1, playerUuid.toString());
            setNullableString(statement, 2, state.getActiveProjectId());
            statement.setBoolean(3, state.isHudVisible());
            statement.setLong(4, lastSaved);
            statement.executeUpdate();
        }
    }

    /**
     * 替换玩家 HUD 星标项目。
     *
     * @param connection H2 连接
     * @param playerUuid 玩家 UUID
     * @param state 玩家项目状态
     * @throws SQLException 写入失败时抛出
     */
    private void replaceStarredProjects(Connection connection, UUID playerUuid, ProjectPlayerState state) throws SQLException {
        try (PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM player_hud_starred_projects WHERE player_uuid = ?");
             PreparedStatement insertStatement = connection.prepareStatement("""
                INSERT INTO player_hud_starred_projects(player_uuid, project_id, project_bucket_type, sort_order)
                VALUES (?, ?, ?, ?)
                """)) {
            deleteStatement.setString(1, playerUuid.toString());
            deleteStatement.executeUpdate();
            for (int index = 0; index < state.getHudStarredProjectIds().size(); index++) {
                String projectId = state.getHudStarredProjectIds().get(index);
                if (projectId == null || projectId.isBlank()) {
                    continue;
                }
                insertStatement.setString(1, playerUuid.toString());
                insertStatement.setString(2, projectId);
                insertStatement.setString(3, H2ProjectStore.PERSONAL_PROJECTS_BUCKET);
                insertStatement.setLong(4, index);
                insertStatement.executeUpdate();
            }
        }
    }

    /**
     * 读取玩家 HUD 星标项目。
     *
     * @param connection H2 连接
     * @param playerUuid 玩家 UUID
     * @return 星标项目 ID 列表
     * @throws SQLException 读取失败时抛出
     */
    private List<String> loadStarredProjects(Connection connection, UUID playerUuid) throws SQLException {
        List<String> projectIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT project_id FROM player_hud_starred_projects
                WHERE player_uuid = ?
                ORDER BY sort_order, project_id
                """)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    projectIds.add(resultSet.getString(1));
                }
            }
        }
        return projectIds;
    }

    /**
     * 绑定可空字符串。
     *
     * @param statement SQL statement
     * @param index 参数序号
     * @param value 字符串值
     * @throws SQLException 绑定失败时抛出
     */
    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }
}
