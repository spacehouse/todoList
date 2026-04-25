package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.persistence.SafePersistenceHelper;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.project.ProjectStorage;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 持久化安全测试入口。
 * 负责覆盖安全写盘、备份恢复和损坏文件兜底等核心回归场景。
 */
public final class PersistenceSafetyTestMain {
    private static final UUID TEST_PLAYER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private PersistenceSafetyTestMain() {
    }

    /**
     * 程序入口，串行执行持久化安全回归用例。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldCreateTaskBackupOnSecondSave", PersistenceSafetyTestMain::shouldCreateTaskBackupOnSecondSave);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldRecoverTasksFromBackupWhenPrimaryCorrupted", PersistenceSafetyTestMain::shouldRecoverTasksFromBackupWhenPrimaryCorrupted);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldRecoverTasksFromBackupWhenPrimaryContainsMalformedEntry", PersistenceSafetyTestMain::shouldRecoverTasksFromBackupWhenPrimaryContainsMalformedEntry);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldRestoreTasksFromBackupWhenPrimaryMissing", PersistenceSafetyTestMain::shouldRestoreTasksFromBackupWhenPrimaryMissing);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldReturnEmptyWhenTaskPrimaryAndBackupBothCorrupted", PersistenceSafetyTestMain::shouldReturnEmptyWhenTaskPrimaryAndBackupBothCorrupted);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldRecoverProjectAndPlayerStateFilesFromBackup", PersistenceSafetyTestMain::shouldRecoverProjectAndPlayerStateFilesFromBackup);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldKeepProjectManagerStateWhenReloadFails", PersistenceSafetyTestMain::shouldKeepProjectManagerStateWhenReloadFails);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldRecoverConfigFromBackupWhenPrimaryCorrupted", PersistenceSafetyTestMain::shouldRecoverConfigFromBackupWhenPrimaryCorrupted);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldFallbackToDefaultsWithoutOverwritingInvalidConfig", PersistenceSafetyTestMain::shouldFallbackToDefaultsWithoutOverwritingInvalidConfig);
    }

    /**
     * 验证任务文件第二次保存后会生成最近一次成功版本的备份。
     */
    private static void shouldCreateTaskBackupOnSecondSave() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.saveTasks(List.of(createTask("Task Backup Old")));
            storage.saveTasks(List.of(createTask("Task Backup New")));
            Path taskFile = storage.getDataDirectoryPath().resolve("moddata.dat");
            Path backupFile = SafePersistenceHelper.resolveBackupPath(taskFile);

            GuiTestSupport.assertTrue(Files.exists(taskFile), "第二次保存后任务主文件应存在");
            GuiTestSupport.assertTrue(Files.exists(backupFile), "第二次保存后任务备份文件应存在");
            GuiTestSupport.assertEquals(List.of("Task Backup New"), loadTaskTitles(storage.loadTasks()), "任务主文件应保留最新保存结果");
        } catch (Exception exception) {
            throw new IllegalStateException("验证任务备份创建时发生异常", exception);
        }
    }

    /**
     * 验证任务主文件损坏时会自动回退到最近一次备份。
     */
    private static void shouldRecoverTasksFromBackupWhenPrimaryCorrupted() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.saveTasks(List.of(createTask("Task Recover Old")));
            storage.saveTasks(List.of(createTask("Task Recover New")));
            Path taskFile = storage.getDataDirectoryPath().resolve("moddata.dat");

            corruptFile(taskFile, "broken-task-primary");
            List<String> restoredTitles = loadTaskTitles(storage.loadTasks());

            GuiTestSupport.assertEquals(List.of("Task Recover Old"), restoredTitles, "任务主文件损坏后应从备份恢复上一版数据");
            GuiTestSupport.assertTrue(hasCorruptSibling(taskFile), "任务主文件损坏后应保留 .corrupt 现场文件");
            GuiTestSupport.assertEquals(List.of("Task Recover Old"), loadTaskTitles(storage.loadTasks()), "任务主文件恢复后再次读取应保持恢复结果");
        } catch (Exception exception) {
            throw new IllegalStateException("验证任务备份恢复时发生异常", exception);
        }
    }

    /**
     * 验证任务主文件中只要出现无法解析的坏任务，也会回退到最近一次备份。
     */
    private static void shouldRecoverTasksFromBackupWhenPrimaryContainsMalformedEntry() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.saveTasks(List.of(createTask("Task Partial Old")));
            storage.saveTasks(List.of(createTask("Task Partial New")));
            Path taskFile = storage.getDataDirectoryPath().resolve("moddata.dat");

            corruptTaskPriorityInsideNbt(taskFile, "BROKEN_PRIORITY");
            List<String> restoredTitles = loadTaskTitles(storage.loadTasks());

            GuiTestSupport.assertEquals(List.of("Task Partial Old"), restoredTitles, "任务文件存在部分坏任务时应回退到最近一次备份");
            GuiTestSupport.assertTrue(hasCorruptSibling(taskFile), "任务文件存在部分坏任务时应保留 .corrupt 现场文件");
        } catch (Exception exception) {
            throw new IllegalStateException("验证任务部分损坏触发恢复时发生异常", exception);
        }
    }

    /**
     * 验证任务主文件丢失但备份存在时会自动从备份补回主文件。
     */
    private static void shouldRestoreTasksFromBackupWhenPrimaryMissing() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.saveTasks(List.of(createTask("Task Missing Old")));
            storage.saveTasks(List.of(createTask("Task Missing New")));
            Path taskFile = storage.getDataDirectoryPath().resolve("moddata.dat");
            Files.delete(taskFile);

            List<String> restoredTitles = loadTaskTitles(storage.loadTasks());

            GuiTestSupport.assertEquals(List.of("Task Missing Old"), restoredTitles, "任务主文件缺失时应从备份恢复上一版数据");
            GuiTestSupport.assertTrue(Files.exists(taskFile), "任务主文件缺失后应被自动补回");
        } catch (Exception exception) {
            throw new IllegalStateException("验证任务缺失恢复时发生异常", exception);
        }
    }

    /**
     * 验证任务主文件和备份都损坏时，严格读取失败而安全读取返回空列表。
     */
    private static void shouldReturnEmptyWhenTaskPrimaryAndBackupBothCorrupted() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.saveTasks(List.of(createTask("Task Broken Old")));
            storage.saveTasks(List.of(createTask("Task Broken New")));
            Path taskFile = storage.getDataDirectoryPath().resolve("moddata.dat");
            Path backupFile = SafePersistenceHelper.resolveBackupPath(taskFile);

            corruptFile(taskFile, "broken-task-primary");
            corruptFile(backupFile, "broken-task-backup");

            boolean strictFailed = false;
            try {
                storage.loadTasks();
            } catch (Exception exception) {
                strictFailed = true;
            }

            GuiTestSupport.assertTrue(strictFailed, "任务主文件和备份都损坏时严格读取应失败");
            GuiTestSupport.assertEquals(List.of(), loadTaskTitles(storage.loadTasksSafe()), "任务主文件和备份都损坏时安全读取应返回空列表");
        } catch (Exception exception) {
            throw new IllegalStateException("验证任务双损坏兜底时发生异常", exception);
        }
    }

    /**
     * 验证项目文件、团队项目文件和玩家项目状态文件都能从备份自动恢复。
     */
    private static void shouldRecoverProjectAndPlayerStateFilesFromBackup() {
        GuiTestSupport.resetState();
        ProjectStorage projectStorage = TodoListCommon.getProjectStorage();
        ProjectPlayerStateStorage playerStateStorage = new ProjectPlayerStateStorage();
        try {
            projectStorage.saveProjects(List.of(createProject("personal-old", "Personal Old", Project.Scope.PERSONAL)));
            projectStorage.saveProjects(List.of(createProject("personal-new", "Personal New", Project.Scope.PERSONAL)));
            projectStorage.saveTeamProjects(List.of(createProject("team-old", "Team Old", Project.Scope.TEAM)));
            projectStorage.saveTeamProjects(List.of(createProject("team-new", "Team New", Project.Scope.TEAM)));
            playerStateStorage.savePlayerState(TEST_PLAYER_ID, new ProjectPlayerStateStorage.ProjectPlayerState("state-old", List.of("state-old"), true));
            playerStateStorage.savePlayerState(TEST_PLAYER_ID, new ProjectPlayerStateStorage.ProjectPlayerState("state-new", List.of("state-new"), false));

            Path personalFile = DataPathProvider.getProjectsDir().resolve("projects.dat");
            Path teamFile = DataPathProvider.getProjectsDir().resolve("team_projects.dat");
            Path playerStateFile = playerStateStorage.getPlayerStateFilePath(TEST_PLAYER_ID);
            corruptFile(personalFile, "broken-personal-projects");
            corruptFile(teamFile, "broken-team-projects");
            corruptFile(playerStateFile, "broken-player-state");

            List<Project> personalProjects = projectStorage.loadProjects();
            List<Project> teamProjects = projectStorage.loadTeamProjects();
            ProjectPlayerStateStorage.ProjectPlayerState playerState = playerStateStorage.loadPlayerState(TEST_PLAYER_ID);

            GuiTestSupport.assertEquals(List.of("Personal Old"), personalProjects.stream().map(Project::getName).toList(), "个人项目主文件损坏后应恢复上一版备份");
            GuiTestSupport.assertEquals(List.of("Team Old"), teamProjects.stream().map(Project::getName).toList(), "团队项目主文件损坏后应恢复上一版备份");
            GuiTestSupport.assertEquals("state-old", playerState.getActiveProjectId(), "玩家项目状态主文件损坏后应恢复上一版备份");
            GuiTestSupport.assertEquals(List.of("state-old"), playerState.getHudStarredProjectIds(), "玩家项目状态星标列表应恢复到备份版本");
            GuiTestSupport.assertTrue(hasCorruptSibling(personalFile), "个人项目文件损坏后应保留 .corrupt 现场");
            GuiTestSupport.assertTrue(hasCorruptSibling(teamFile), "团队项目文件损坏后应保留 .corrupt 现场");
            GuiTestSupport.assertTrue(hasCorruptSibling(playerStateFile), "玩家项目状态文件损坏后应保留 .corrupt 现场");
        } catch (Exception exception) {
            throw new IllegalStateException("验证项目与玩家状态备份恢复时发生异常", exception);
        }
    }

    /**
     * 验证项目重载失败时不会先清空当前内存项目列表。
     */
    private static void shouldKeepProjectManagerStateWhenReloadFails() {
        GuiTestSupport.resetState();
        ProjectStorage projectStorage = TodoListCommon.getProjectStorage();
        ProjectManager projectManager = TodoListCommon.getProjectManager();
        try {
            Project memoryProject = createProject("memory-keep", "Memory Keep", Project.Scope.PERSONAL);
            projectManager.addProject(memoryProject);
            projectStorage.saveProjects(List.of(createProject("disk-old", "Disk Old", Project.Scope.PERSONAL)));
            projectStorage.saveProjects(List.of(createProject("disk-new", "Disk New", Project.Scope.PERSONAL)));

            Path personalFile = DataPathProvider.getProjectsDir().resolve("projects.dat");
            Path personalBackupFile = SafePersistenceHelper.resolveBackupPath(personalFile);
            corruptFile(personalFile, "broken-project-primary");
            corruptFile(personalBackupFile, "broken-project-backup");

            TodoListCommon.reloadProjectsFromStorage();

            GuiTestSupport.assertNotNull(projectManager.getProject("memory-keep"), "项目重载失败时不应清空已有内存项目");
            GuiTestSupport.assertEquals("Memory Keep", projectManager.getProject("memory-keep").getName(), "项目重载失败后应保留原有内存项目内容");
        } catch (Exception exception) {
            throw new IllegalStateException("验证项目重载失败保护时发生异常", exception);
        }
    }

    /**
     * 验证配置主文件损坏时会从备份自动恢复且保留现场文件。
     */
    private static void shouldRecoverConfigFromBackupWhenPrimaryCorrupted() {
        GuiTestSupport.resetState();
        try {
            ModConfig config = ModConfig.getInstance();
            config.setHudWidth(240);
            config.setHudWidth(333);
            Path configFile = getConfigFilePath();
            Path backupFile = SafePersistenceHelper.resolveBackupPath(configFile);

            GuiTestSupport.assertTrue(Files.exists(backupFile), "配置文件第二次保存后应生成 .bak 备份");
            corruptFile(configFile, "{ invalid config primary");
            ModConfig.load();

            GuiTestSupport.assertEquals(240, ModConfig.getInstance().getHudWidth(), "配置主文件损坏后应恢复为最近一次备份版本");
            GuiTestSupport.assertTrue(hasCorruptSibling(configFile), "配置主文件损坏后应保留 .corrupt 现场");
            GuiTestSupport.assertTrue(Files.readString(configFile, StandardCharsets.UTF_8).contains("\"commandAccessMode\""), "恢复后的配置文件应仍保留注释相关配置内容");
        } catch (Exception exception) {
            throw new IllegalStateException("验证配置备份恢复时发生异常", exception);
        }
    }

    /**
     * 验证配置主文件和备份都损坏时，会回退默认值但不覆盖现场坏文件。
     */
    private static void shouldFallbackToDefaultsWithoutOverwritingInvalidConfig() {
        GuiTestSupport.resetState();
        try {
            ModConfig config = ModConfig.getInstance();
            config.setHudWidth(333);
            Path configFile = getConfigFilePath();
            Path backupFile = SafePersistenceHelper.resolveBackupPath(configFile);

            corruptFile(configFile, "{ invalid config primary");
            corruptFile(backupFile, "{ invalid config backup");
            String invalidPrimary = Files.readString(configFile, StandardCharsets.UTF_8);

            ModConfig.load();

            GuiTestSupport.assertEquals(200, ModConfig.getInstance().getHudWidth(), "配置主文件和备份都损坏时应回退默认配置");
            GuiTestSupport.assertEquals(invalidPrimary, Files.readString(configFile, StandardCharsets.UTF_8), "配置双损坏时不应立刻覆盖现场坏文件");
        } catch (Exception exception) {
            throw new IllegalStateException("验证配置双损坏兜底时发生异常", exception);
        }
    }

    /**
     * 创建一个测试任务。
     *
     * @param title 任务标题
     * @return 构造好的任务对象
     */
    private static Task createTask(String title) {
        return new Task(title, "");
    }

    /**
     * 创建一个测试项目。
     *
     * @param id 项目 ID
     * @param name 项目名称
     * @param scope 项目范围
     * @return 构造好的项目对象
     */
    private static Project createProject(String id, String name, Project.Scope scope) {
        Project project = new Project(name, scope, null);
        project.setId(id);
        return project;
    }

    /**
     * 提取任务标题列表，便于断言结果。
     *
     * @param tasks 任务列表
     * @return 任务标题列表
     */
    private static List<String> loadTaskTitles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }

    /**
     * 将目标文件改写为损坏内容。
     *
     * @param file 目标文件
     * @param content 损坏内容
     * @throws Exception 当写入失败时抛出
     */
    private static void corruptFile(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /**
     * 将任务文件中首条任务的优先级改写为非法值，模拟“整文件可读但单条任务损坏”的场景。
     *
     * @param file 目标任务文件
     * @param invalidPriority 非法优先级值
     * @throws Exception 当改写失败时抛出
     */
    private static void corruptTaskPriorityInsideNbt(Path file, String invalidPriority) throws Exception {
        CompoundTag root = NbtIo.read(file.toFile());
        if (root == null || !root.contains("tasks", 9)) {
            throw new IllegalStateException("任务文件缺少 tasks 列表，无法构造部分损坏场景");
        }
        ListTag taskList = root.getList("tasks", 10);
        if (taskList.size() <= 0) {
            throw new IllegalStateException("任务文件没有可损坏的任务条目");
        }
        taskList.getCompound(0).putString("priority", invalidPriority);
        NbtIo.write(root, file.toFile());
    }

    /**
     * 判断目标文件是否生成了损坏现场副本。
     *
     * @param file 目标文件
     * @return 找到 .corrupt 副本时返回 true
     * @throws Exception 当目录遍历失败时抛出
     */
    private static boolean hasCorruptSibling(Path file) throws Exception {
        Path absoluteFile = file.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        if (parent == null || !Files.exists(parent)) {
            return false;
        }
        String prefix = absoluteFile.getFileName() + ".corrupt.";
        try (Stream<Path> stream = Files.list(parent)) {
            return stream.anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }

    /**
     * 返回当前测试环境的配置文件路径。
     *
     * @return 配置文件路径
     */
    private static Path getConfigFilePath() {
        return DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
    }
}
