package com.todolist.storage;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.persistence.SafePersistenceHelper;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.project.ProjectStorage;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * H2LegacyMigrationTestMain 覆盖 M1-C 的只读迁移读取、预检和单事务导入。
 */
public final class H2LegacyMigrationTestMain {
    private static final UUID TEST_PLAYER = UUID.fromString("70000000-0000-0000-0000-000000000001");

    private H2LegacyMigrationTestMain() {
    }

    /**
     * 执行旧 NBT 迁移离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("H2LegacyMigrationTestMain.shouldReadBackupWithoutRestoringPrimary", H2LegacyMigrationTestMain::shouldReadBackupWithoutRestoringPrimary);
        GuiTestSupport.runTestCase("H2LegacyMigrationTestMain.shouldRejectNonEmptySubtasks", H2LegacyMigrationTestMain::shouldRejectNonEmptySubtasks);
        GuiTestSupport.runTestCase("H2LegacyMigrationTestMain.shouldMigrateLegacyDataInSingleTransaction", H2LegacyMigrationTestMain::shouldMigrateLegacyDataInSingleTransaction);
    }

    /**
     * 验证迁移读取备份时不会把备份恢复成主文件。
     */
    private static void shouldReadBackupWithoutRestoringPrimary() {
        Path tempGameDir = null;
        try {
            tempGameDir = Files.createTempDirectory("todolist-h2-readonly-");
            Path finalTempGameDir = tempGameDir;
            DataPathProvider.setGameDirSupplier(() -> finalTempGameDir);
            DataPathProvider.resetStorageNamespace();
            TaskStorage taskStorage = new TaskStorage();
            taskStorage.saveTasks(List.of(createTask("backup-old")));
            taskStorage.saveTasks(List.of(createTask("backup-new")));
            Path taskFile = taskStorage.getDataDirectoryPath().resolve("moddata.dat");
            Path backupFile = SafePersistenceHelper.resolveBackupPath(taskFile);
            Files.delete(taskFile);

            LegacyMigrationData data = new H2LegacyMigrationReader().readAll();

            GuiTestSupport.assertTrue(Files.exists(backupFile), "迁移只读读取前应存在备份文件");
            GuiTestSupport.assertFalse(Files.exists(taskFile), "迁移只读读取不应把备份恢复成主文件");
            GuiTestSupport.assertEquals(1, data.taskBuckets().size(), "应读取到一个任务桶");
            GuiTestSupport.assertEquals("backup-old", data.taskBuckets().get(0).tasks().get(0).getTitle(), "缺失主文件时应只读使用备份内容");
        } catch (Exception exception) {
            throw new IllegalStateException("验证只读备份读取时发生异常", exception);
        } finally {
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证旧数据存在非空子任务时预检会阻断迁移。
     */
    private static void shouldRejectNonEmptySubtasks() {
        Task parent = createTask("parent");
        parent.addSubtask(createTask("child"));
        LegacyMigrationData data = new LegacyMigrationData(
                List.of(new LegacyTaskBucket("LOCAL_PERSONAL", "LOCAL", 1L, List.of(parent))),
                List.of(),
                List.of()
        );
        boolean failed = false;
        try {
            new H2LegacyMigrationPreflight().validate(data);
        } catch (LegacyMigrationException exception) {
            failed = exception.getMessage().contains("子任务");
        }
        GuiTestSupport.assertTrue(failed, "非空子任务应阻断 H2 M1 迁移");
    }

    /**
     * 验证旧 NBT 数据可导入 H2，并写入迁移完成元数据。
     */
    private static void shouldMigrateLegacyDataInSingleTransaction() {
        Path tempGameDir = null;
        try {
            tempGameDir = Files.createTempDirectory("todolist-h2-migrate-");
            Path finalTempGameDir = tempGameDir;
            DataPathProvider.setGameDirSupplier(() -> finalTempGameDir);
            DataPathProvider.resetStorageNamespace();
            writeLegacyData();

            LegacyMigrationData data = new H2LegacyMigrationReader().readAll();
            H2ConnectionProvider provider = new H2ConnectionProvider();
            try (Connection connection = provider.openConnection()) {
                new H2LegacyMigrator().migrate(connection, data);
                assertCount(connection, "tasks", 1);
                assertCount(connection, "task_tags", 1);
                assertCount(connection, "projects", 1);
                assertCount(connection, "project_members", 1);
                assertCount(connection, "player_project_state", 1);
                assertCount(connection, "player_hud_starred_projects", 1);
                assertMeta(connection, "dat_migration_completed", "true");
                assertMeta(connection, "migration_source_format", "nbt-v1");
            }
        } catch (Exception exception) {
            throw new IllegalStateException("验证旧数据 H2 导入时发生异常", exception);
        } finally {
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 写入用于迁移测试的旧 NBT 数据。
     *
     * @throws Exception 写入失败时抛出
     */
    private static void writeLegacyData() throws Exception {
        Task task = createTask("legacy task");
        task.setId("legacy-task-1");
        task.addTag("中文标签");
        new TaskStorage().saveTasks(List.of(task));

        Project project = new Project("legacy project", Project.Scope.PERSONAL, TEST_PLAYER.toString());
        project.setId("legacy-project-1");
        project.setMemberName(TEST_PLAYER.toString(), "Owner");
        new ProjectStorage().saveProjects(List.of(project));

        ProjectPlayerStateStorage.ProjectPlayerState state = new ProjectPlayerStateStorage.ProjectPlayerState(
                project.getId(),
                List.of(project.getId()),
                true
        );
        new ProjectPlayerStateStorage().savePlayerState(TEST_PLAYER, state);
    }

    /**
     * 创建测试任务。
     *
     * @param title 任务标题
     * @return 测试任务
     */
    private static Task createTask(String title) {
        return new Task(title, "");
    }

    /**
     * 断言指定表的记录数。
     *
     * @param connection H2 连接
     * @param table 表名
     * @param expected 期望记录数
     * @throws Exception 查询失败时抛出
     */
    private static void assertCount(Connection connection, String table, int expected) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            GuiTestSupport.assertTrue(resultSet.next(), "应能查询表记录数: " + table);
            GuiTestSupport.assertEquals(expected, resultSet.getInt(1), "表记录数不符合预期: " + table);
        }
    }

    /**
     * 断言 storage_meta 中指定键的值。
     *
     * @param connection H2 连接
     * @param key meta key
     * @param expected 期望值
     * @throws Exception 查询失败时抛出
     */
    private static void assertMeta(Connection connection, String key, String expected) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT \"value\" FROM storage_meta WHERE \"key\" = '" + key + "'")) {
            GuiTestSupport.assertTrue(resultSet.next(), "storage_meta 缺少键: " + key);
            GuiTestSupport.assertEquals(expected, resultSet.getString(1), "storage_meta 值不符合预期: " + key);
        }
    }

    /**
     * 递归删除测试临时目录。
     *
     * @param root 临时目录
     */
    private static void deleteRecursively(Path root) {
        try {
            if (root == null || !Files.exists(root)) {
                return;
            }
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法删除迁移测试临时文件: " + path, exception);
                    }
                });
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理迁移测试临时目录", exception);
        }
    }
}
