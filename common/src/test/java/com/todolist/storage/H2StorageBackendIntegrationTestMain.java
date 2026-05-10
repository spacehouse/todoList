package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.project.ProjectStorage;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * H2StorageBackendIntegrationTestMain 覆盖 M1-D 的 H2 后端门面接入行为。
 */
public final class H2StorageBackendIntegrationTestMain {
    private static final UUID TEST_PLAYER = UUID.fromString("71000000-0000-0000-0000-000000000001");

    private H2StorageBackendIntegrationTestMain() {
    }

    /**
     * 执行 H2 存储后端集成自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("H2StorageBackendIntegrationTestMain.shouldRouteTaskStorageToH2", H2StorageBackendIntegrationTestMain::shouldRouteTaskStorageToH2);
        GuiTestSupport.runTestCase("H2StorageBackendIntegrationTestMain.shouldSerializeConcurrentPlayerTaskSavesWithTags", H2StorageBackendIntegrationTestMain::shouldSerializeConcurrentPlayerTaskSavesWithTags);
        GuiTestSupport.runTestCase("H2StorageBackendIntegrationTestMain.shouldRouteProjectStorageToH2", H2StorageBackendIntegrationTestMain::shouldRouteProjectStorageToH2);
        GuiTestSupport.runTestCase("H2StorageBackendIntegrationTestMain.shouldRouteProjectPlayerStateToH2", H2StorageBackendIntegrationTestMain::shouldRouteProjectPlayerStateToH2);
    }

    /**
     * 验证 TaskStorage 在 H2 后端下会先迁移旧 NBT，再读写 H2。
     */
    private static void shouldRouteTaskStorageToH2() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-task-store-");
            writeLegacyLocalTask("legacy-local");
            selectH2Backend();

            TaskStorage storage = new TaskStorage();
            GuiTestSupport.assertEquals("legacy-local", storage.loadTasks().get(0).getTitle(), "首次 H2 读取应迁移旧本地任务");

            Task h2Local = createTask("h2-local");
            h2Local.setId("h2-local-id");
            h2Local.addTag("tag-a");
            storage.saveTasks(List.of(h2Local));
            GuiTestSupport.assertEquals("h2-local", storage.loadTasks().get(0).getTitle(), "H2 本地任务保存后应可读回");
            GuiTestSupport.assertTrue(storage.getLocalTasksLastSaved() > 0, "H2 本地任务应记录 lastSaved");

            Task h2Player = createTask("h2-player");
            h2Player.setId("h2-player-id");
            storage.savePlayerTasks(TEST_PLAYER, List.of(h2Player));
            GuiTestSupport.assertTrue(storage.hasPlayerTasks(TEST_PLAYER), "H2 玩家任务桶保存后应存在");
            GuiTestSupport.assertEquals("h2-player", storage.loadPlayerTasks(TEST_PLAYER).get(0).getTitle(), "H2 玩家任务应可读回");
            GuiTestSupport.assertTrue(storage.getPlayerTasksLastSaved(TEST_PLAYER) > 0, "H2 玩家任务应记录 lastSaved");

            Task h2Team = createTask("h2-team");
            h2Team.setId("h2-team-id");
            h2Team.setScope(Task.Scope.TEAM);
            storage.saveTeamTasks(List.of(h2Team));
            GuiTestSupport.assertEquals("h2-team", storage.loadTeamTasks().get(0).getTitle(), "H2 团队任务应可读回");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 任务门面时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证 ProjectStorage 在 H2 后端下可读写个人项目和团队项目。
     */
    private static void shouldRouteProjectStorageToH2() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-project-store-");
            writeLegacyPersonalProject("legacy-project");
            selectH2Backend();

            ProjectStorage storage = new ProjectStorage();
            GuiTestSupport.assertEquals("legacy-project", storage.loadProjects().get(0).getName(), "首次 H2 读取应迁移旧个人项目");
            GuiTestSupport.assertTrue(storage.hasPersonalProjectsFile(), "H2 个人项目桶有数据时应视为存在");

            Project personal = createProject("h2-personal", Project.Scope.PERSONAL);
            personal.setId("h2-personal-id");
            storage.saveProjects(List.of(personal));
            GuiTestSupport.assertEquals("h2-personal", storage.loadProjects().get(0).getName(), "H2 个人项目应可读回");

            Project team = createProject("h2-team", Project.Scope.TEAM);
            team.setId("h2-team-id");
            team.setAllowMemberCreate(true);
            team.setAllowAllPlayersClaimComplete(true);
            storage.saveTeamProjects(List.of(team));
            Project loadedTeam = storage.loadTeamProjects().get(0);
            GuiTestSupport.assertEquals("h2-team", loadedTeam.getName(), "H2 团队项目应可读回");
            GuiTestSupport.assertTrue(loadedTeam.isAllowMemberCreate(), "H2 团队项目成员创建设置应保留");
            GuiTestSupport.assertTrue(loadedTeam.isAllowAllPlayersClaimComplete(), "H2 团队项目认领完成设置应保留");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 项目门面时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证 ProjectPlayerStateStorage 在 H2 后端下可读写玩家状态。
     */
    private static void shouldRouteProjectPlayerStateToH2() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-player-state-store-");
            writeLegacyPlayerState("legacy-project-id");
            selectH2Backend();

            ProjectPlayerStateStorage storage = new ProjectPlayerStateStorage();
            GuiTestSupport.assertTrue(storage.hasPlayerState(TEST_PLAYER), "首次 H2 查询应迁移旧玩家项目状态");
            ProjectPlayerStateStorage.ProjectPlayerState legacyState = storage.loadPlayerState(TEST_PLAYER);
            GuiTestSupport.assertEquals("legacy-project-id", legacyState.getActiveProjectId(), "H2 应读到迁移后的当前项目");

            ProjectPlayerStateStorage.ProjectPlayerState nextState = new ProjectPlayerStateStorage.ProjectPlayerState(
                    "h2-active-project",
                    List.of("h2-active-project", "h2-starred-project"),
                    false
            );
            storage.savePlayerState(TEST_PLAYER, nextState);
            ProjectPlayerStateStorage.ProjectPlayerState loadedState = storage.loadPlayerState(TEST_PLAYER);
            GuiTestSupport.assertEquals("h2-active-project", loadedState.getActiveProjectId(), "H2 玩家状态当前项目应可读回");
            GuiTestSupport.assertEquals(2, loadedState.getHudStarredProjectIds().size(), "H2 HUD 星标项目数量应可读回");
            GuiTestSupport.assertFalse(loadedState.isHudVisible(), "H2 HUD 可见性应可读回");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 玩家项目状态门面时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证同一玩家任务桶并发保存带标签任务时不会触发 task_tags 唯一索引冲突。
     */
    private static void shouldSerializeConcurrentPlayerTaskSavesWithTags() {
        Path tempGameDir = null;
        ExecutorService executor = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-concurrent-task-tags-");
            selectH2Backend();
            TaskStorage storage = new TaskStorage();
            CountDownLatch startSignal = new CountDownLatch(1);
            executor = Executors.newFixedThreadPool(2);

            Future<?> firstSave = executor.submit(() -> saveTaggedPlayerTaskAfterLatch(storage, startSignal, "tagged-task-a", "tagged-a"));
            Future<?> secondSave = executor.submit(() -> saveTaggedPlayerTaskAfterLatch(storage, startSignal, "tagged-task-b", "tagged-b"));
            startSignal.countDown();

            firstSave.get(10, TimeUnit.SECONDS);
            secondSave.get(10, TimeUnit.SECONDS);

            List<Task> loadedTasks = storage.loadPlayerTasks(TEST_PLAYER);
            GuiTestSupport.assertEquals(1, loadedTasks.size(), "并发替换保存后玩家任务桶应保持最后一次写入的一致快照");
            GuiTestSupport.assertTrue(loadedTasks.get(0).getTags().contains("tag1"), "并发保存后的任务标签应可读回");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 并发标签保存时发生异常", exception);
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            selectNbtBackendQuietly();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 等待开始信号后保存一条带标签的玩家任务。
     *
     * @param storage 任务存储
     * @param startSignal 开始信号
     * @param taskId 任务 ID
     * @param title 任务标题
     */
    private static void saveTaggedPlayerTaskAfterLatch(TaskStorage storage, CountDownLatch startSignal, String taskId, String title) {
        try {
            startSignal.await(10, TimeUnit.SECONDS);
            Task task = createTask(title);
            task.setId(taskId);
            task.addTag("tag1");
            storage.savePlayerTasks(TEST_PLAYER, List.of(task));
        } catch (Exception exception) {
            throw new IllegalStateException("并发保存带标签玩家任务失败", exception);
        }
    }

    /**
     * 准备测试临时游戏目录。
     *
     * @param prefix 临时目录前缀
     * @return 临时游戏目录
     * @throws Exception 准备失败时抛出
     */
    private static Path prepareTempGameDir(String prefix) throws Exception {
        Path tempGameDir = Files.createTempDirectory(prefix);
        DataPathProvider.setGameDirSupplier(() -> tempGameDir);
        DataPathProvider.resetStorageNamespace();
        selectNbtBackendQuietly();
        return tempGameDir;
    }

    /**
     * 写入旧 NBT 本地任务。
     *
     * @param title 任务标题
     * @throws Exception 写入失败时抛出
     */
    private static void writeLegacyLocalTask(String title) throws Exception {
        Task task = createTask(title);
        task.setId("legacy-local-id");
        new TaskStorage().saveTasks(List.of(task));
    }

    /**
     * 写入旧 NBT 个人项目。
     *
     * @param name 项目名称
     * @throws Exception 写入失败时抛出
     */
    private static void writeLegacyPersonalProject(String name) throws Exception {
        Project project = createProject(name, Project.Scope.PERSONAL);
        project.setId("legacy-project-id");
        new ProjectStorage().saveProjects(List.of(project));
    }

    /**
     * 写入旧 NBT 玩家项目状态。
     *
     * @param activeProjectId 当前项目 ID
     * @throws Exception 写入失败时抛出
     */
    private static void writeLegacyPlayerState(String activeProjectId) throws Exception {
        ProjectPlayerStateStorage.ProjectPlayerState state = new ProjectPlayerStateStorage.ProjectPlayerState(
                activeProjectId,
                List.of(activeProjectId),
                true
        );
        new ProjectPlayerStateStorage().savePlayerState(TEST_PLAYER, state);
    }

    /**
     * 切换为 H2 后端。
     */
    private static void selectH2Backend() {
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
    }

    /**
     * 安静切换回 NBT 后端，避免影响后续测试。
     */
    private static void selectNbtBackendQuietly() {
        try {
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
        } catch (Exception ignored) {
            // 测试清理阶段仅做最佳努力恢复配置。
        }
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
     * 创建测试项目。
     *
     * @param name 项目名称
     * @param scope 项目范围
     * @return 测试项目
     */
    private static Project createProject(String name, Project.Scope scope) {
        Project project = new Project(name, scope, TEST_PLAYER.toString());
        project.addMember(TEST_PLAYER.toString(), Project.ProjectRole.PROJECT_MANAGER, "Owner");
        return project;
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
                        throw new IllegalStateException("无法删除 H2 存储测试临时文件: " + path, exception);
                    }
                });
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理 H2 存储测试临时目录", exception);
        }
    }
}
