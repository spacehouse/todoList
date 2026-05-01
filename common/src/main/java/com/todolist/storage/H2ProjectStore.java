package com.todolist.storage;

import com.todolist.project.Project;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * H2ProjectStore 负责在 H2 后端读写个人项目和团队项目。
 */
public final class H2ProjectStore {
    public static final String PERSONAL_PROJECTS_BUCKET = "PERSONAL_PROJECTS";
    public static final String TEAM_PROJECTS_BUCKET = "TEAM_PROJECTS";

    private final H2ConnectionProvider connectionProvider;
    private final H2StorageBootstrap bootstrap;

    /**
     * 创建默认 H2 项目存储。
     */
    public H2ProjectStore() {
        this(new H2ConnectionProvider(), new H2StorageBootstrap());
    }

    /**
     * 创建可注入依赖的 H2 项目存储。
     *
     * @param connectionProvider H2 连接提供器
     * @param bootstrap H2 启动器
     */
    public H2ProjectStore(H2ConnectionProvider connectionProvider, H2StorageBootstrap bootstrap) {
        this.connectionProvider = connectionProvider;
        this.bootstrap = bootstrap;
    }

    /**
     * 读取个人项目列表。
     *
     * @return 个人项目列表
     * @throws IOException 读取失败时抛出
     */
    public List<Project> loadProjects() throws IOException {
        return loadBucket(PERSONAL_PROJECTS_BUCKET);
    }

    /**
     * 读取团队项目列表。
     *
     * @return 团队项目列表
     * @throws IOException 读取失败时抛出
     */
    public List<Project> loadTeamProjects() throws IOException {
        return loadBucket(TEAM_PROJECTS_BUCKET);
    }

    /**
     * 保存个人项目列表。
     *
     * @param projects 待保存项目
     * @throws IOException 保存失败时抛出
     */
    public void saveProjects(List<Project> projects) throws IOException {
        saveBucket(PERSONAL_PROJECTS_BUCKET, projects);
    }

    /**
     * 保存团队项目列表。
     *
     * @param projects 待保存团队项目
     * @throws IOException 保存失败时抛出
     */
    public void saveTeamProjects(List<Project> projects) throws IOException {
        saveBucket(TEAM_PROJECTS_BUCKET, projects);
    }

    /**
     * 判断个人项目桶中是否已有项目。
     *
     * @return 有项目时返回 true
     * @throws IOException 查询失败时抛出
     */
    public boolean hasPersonalProjects() throws IOException {
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM projects WHERE bucket_type = ? LIMIT 1")) {
            statement.setString(1, PERSONAL_PROJECTS_BUCKET);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.QUERY_FAILED, "Failed to query H2 personal projects", exception);
        }
    }

    /**
     * 读取指定项目桶。
     *
     * @param bucketType 桶类型
     * @return 项目列表
     * @throws IOException 读取失败时抛出
     */
    private List<Project> loadBucket(String bucketType) throws IOException {
        bootstrap.ensureReady();
        try (Connection connection = connectionProvider.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, name, color, scope, owner_uuid, created_at,
                            allow_member_create, allow_all_players_claim_complete
                     FROM projects
                     WHERE bucket_type = ?
                     ORDER BY sort_order, created_at, id
                     """)) {
            statement.setString(1, bucketType);
            List<Project> projects = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    projects.add(readProject(connection, bucketType, resultSet));
                }
            }
            return projects;
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.READ_FAILED, "Failed to load H2 projects", exception);
        }
    }

    /**
     * 保存指定项目桶，使用替换式写入保持列表顺序。
     *
     * @param bucketType 桶类型
     * @param projects 待保存项目
     * @throws IOException 保存失败时抛出
     */
    private void saveBucket(String bucketType, List<Project> projects) throws IOException {
        bootstrap.ensureReady();
        List<Project> safeProjects = projects == null ? List.of() : projects;
        try (Connection connection = connectionProvider.openConnection()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteBucket(connection, bucketType);
                insertProjects(connection, bucketType, safeProjects);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        } catch (SQLException exception) {
            throw markUnavailable(H2StorageAvailability.Reason.WRITE_FAILED, "Failed to save H2 projects", exception);
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
        H2StorageAvailability.markUnavailable(connectionProvider.getDatabaseBasePath(), reason, cause == null ? message : cause.getMessage());
        return new StorageUnavailableException(reason, message, cause);
    }

    /**
     * 删除指定项目桶的项目和成员。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @throws SQLException 删除失败时抛出
     */
    private void deleteBucket(Connection connection, String bucketType) throws SQLException {
        try (PreparedStatement members = connection.prepareStatement("DELETE FROM project_members WHERE bucket_type = ?");
             PreparedStatement projects = connection.prepareStatement("DELETE FROM projects WHERE bucket_type = ?")) {
            members.setString(1, bucketType);
            members.executeUpdate();
            projects.setString(1, bucketType);
            projects.executeUpdate();
        }
    }

    /**
     * 批量插入项目和成员。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param projects 待插入项目
     * @throws SQLException 插入失败时抛出
     */
    private void insertProjects(Connection connection, String bucketType, List<Project> projects) throws SQLException {
        try (PreparedStatement projectStatement = connection.prepareStatement("""
                INSERT INTO projects(bucket_type, id, name, color, scope, owner_uuid, created_at,
                                     allow_member_create, allow_all_players_claim_complete, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """);
             PreparedStatement memberStatement = connection.prepareStatement("""
                INSERT INTO project_members(bucket_type, project_id, player_uuid, role, member_name)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (int index = 0; index < projects.size(); index++) {
                Project project = projects.get(index);
                bindProject(projectStatement, bucketType, project, index);
                projectStatement.executeUpdate();
                for (var entry : project.getMembers().entrySet()) {
                    memberStatement.setString(1, bucketType);
                    memberStatement.setString(2, project.getId());
                    memberStatement.setString(3, entry.getKey());
                    memberStatement.setString(4, entry.getValue().name());
                    setNullableString(memberStatement, 5, project.getMemberName(entry.getKey()));
                    memberStatement.executeUpdate();
                }
            }
        }
    }

    /**
     * 绑定项目写入参数。
     *
     * @param statement SQL statement
     * @param bucketType 桶类型
     * @param project 项目
     * @param sortOrder 排序序号
     * @throws SQLException 绑定失败时抛出
     */
    private void bindProject(PreparedStatement statement, String bucketType, Project project, int sortOrder) throws SQLException {
        statement.setString(1, bucketType);
        statement.setString(2, project.getId());
        statement.setString(3, project.getName());
        statement.setInt(4, project.getColor());
        statement.setString(5, project.getScope().name());
        setNullableString(statement, 6, project.getOwnerUuid());
        statement.setLong(7, project.getCreatedAt());
        statement.setBoolean(8, project.isAllowMemberCreate());
        statement.setBoolean(9, project.isAllowAllPlayersClaimComplete());
        statement.setLong(10, sortOrder);
    }

    /**
     * 从当前结果行读取项目并补齐成员。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param resultSet 项目结果集
     * @return 项目对象
     * @throws SQLException 读取失败时抛出
     */
    private Project readProject(Connection connection, String bucketType, ResultSet resultSet) throws SQLException {
        Project project = new Project();
        project.setId(resultSet.getString("id"));
        project.setName(resultSet.getString("name"));
        project.setColor(resultSet.getInt("color"));
        project.setScope(Project.Scope.valueOf(resultSet.getString("scope")));
        project.setOwnerUuid(resultSet.getString("owner_uuid"));
        project.setCreatedAt(resultSet.getLong("created_at"));
        project.setAllowMemberCreate(resultSet.getBoolean("allow_member_create"));
        project.setAllowAllPlayersClaimComplete(resultSet.getBoolean("allow_all_players_claim_complete"));
        loadMembers(connection, bucketType, project);
        return project;
    }

    /**
     * 读取项目成员。
     *
     * @param connection H2 连接
     * @param bucketType 桶类型
     * @param project 项目对象
     * @throws SQLException 读取失败时抛出
     */
    private void loadMembers(Connection connection, String bucketType, Project project) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, role, member_name FROM project_members
                WHERE bucket_type = ? AND project_id = ?
                ORDER BY role, player_uuid
                """)) {
            statement.setString(1, bucketType);
            statement.setString(2, project.getId());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String playerUuid = resultSet.getString("player_uuid");
                    Project.ProjectRole role = Project.ProjectRole.valueOf(resultSet.getString("role"));
                    String memberName = resultSet.getString("member_name");
                    if (memberName == null || memberName.isBlank()) {
                        project.addMember(playerUuid, role);
                    } else {
                        project.addMember(playerUuid, role, memberName);
                    }
                }
            }
        }
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
