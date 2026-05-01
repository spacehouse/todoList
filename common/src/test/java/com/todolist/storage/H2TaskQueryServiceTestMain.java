package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * H2TaskQueryServiceTestMain 覆盖 M4-A 的 H2 任务查询聚合与内存筛选一致性。
 */
public final class H2TaskQueryServiceTestMain {
    private H2TaskQueryServiceTestMain() {
    }

    /**
     * 执行 H2 查询服务自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldCountLocalTasksByProjectLikeTaskManager", H2TaskQueryServiceTestMain::shouldCountLocalTasksByProjectLikeTaskManager);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldCountTeamTasksByProjectLikeTaskManager", H2TaskQueryServiceTestMain::shouldCountTeamTasksByProjectLikeTaskManager);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryHudTasksWithTotalsAndLimits", H2TaskQueryServiceTestMain::shouldQueryHudTasksWithTotalsAndLimits);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryHudAssignedAndUnassignedViews", H2TaskQueryServiceTestMain::shouldQueryHudAssignedAndUnassignedViews);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryHudAnyAssignedProjectFallback", H2TaskQueryServiceTestMain::shouldQueryHudAnyAssignedProjectFallback);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryGuiTaskIdsLikeMemoryFilters", H2TaskQueryServiceTestMain::shouldQueryGuiTaskIdsLikeMemoryFilters);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldTreatLikeWildcardsAsLiteralSearchText", H2TaskQueryServiceTestMain::shouldTreatLikeWildcardsAsLiteralSearchText);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryGuiTaskIdsWithLimitOffsetAndCount", H2TaskQueryServiceTestMain::shouldQueryGuiTaskIdsWithLimitOffsetAndCount);
    }

    /**
     * 验证本地个人任务项目计数与 TaskManager 内存结果一致。
     */
    private static void shouldCountLocalTasksByProjectLikeTaskManager() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-local-");
            List<Task> tasks = List.of(
                    createTask("local-active-a", "project-a", false, Task.Scope.PERSONAL),
                    createTask("local-done-a", "project-a", true, Task.Scope.PERSONAL),
                    createTask("local-active-b", "project-b", false, Task.Scope.PERSONAL),
                    createTask("local-unassigned", null, false, Task.Scope.PERSONAL)
            );
            new H2TaskStore().saveLocalTasks(tasks);

            Map<String, Integer> actual = new H2TaskQueryService().countTasksByProjectIds(
                    H2TaskStore.LOCAL_PERSONAL_BUCKET,
                    H2TaskStore.LOCAL_OWNER,
                    List.of("project-a", "project-b", "project-empty")
            );
            TaskManager manager = buildTaskManager(tasks);

            GuiTestSupport.assertEquals(manager.getTasksByProject("project-a").size(), actual.get("project-a"), "H2 本地项目 A 计数应与内存一致");
            GuiTestSupport.assertEquals(manager.getTasksByProject("project-b").size(), actual.get("project-b"), "H2 本地项目 B 计数应与内存一致");
            GuiTestSupport.assertEquals(0, actual.get("project-empty"), "H2 本地空项目计数应补 0");
            GuiTestSupport.assertFalse(actual.containsKey(null), "H2 本地计数不应包含未分配项目键");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 本地项目任务计数时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证团队任务项目计数与 TaskManager 内存结果一致。
     */
    private static void shouldCountTeamTasksByProjectLikeTaskManager() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-team-");
            List<Task> tasks = List.of(
                    createTask("team-active-a", "team-project-a", false, Task.Scope.TEAM),
                    createTask("team-done-a", "team-project-a", true, Task.Scope.TEAM),
                    createTask("team-active-b", "team-project-b", false, Task.Scope.TEAM),
                    createTask("team-unassigned", "", false, Task.Scope.TEAM)
            );
            new H2TaskStore().saveTeamTasks(tasks);

            Map<String, Integer> actual = new H2TaskQueryService().countTasksByProjectIds(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    List.of("team-project-a", "team-project-b", "team-project-empty")
            );
            TaskManager manager = buildTaskManager(tasks);

            GuiTestSupport.assertEquals(manager.getTasksByProject("team-project-a").size(), actual.get("team-project-a"), "H2 团队项目 A 计数应与内存一致");
            GuiTestSupport.assertEquals(manager.getTasksByProject("team-project-b").size(), actual.get("team-project-b"), "H2 团队项目 B 计数应与内存一致");
            GuiTestSupport.assertEquals(0, actual.get("team-project-empty"), "H2 团队空项目计数应补 0");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 团队项目任务计数时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证 HUD 查询会返回总数和受 limit 限制的可绘制任务行。
     */
    private static void shouldQueryHudTasksWithTotalsAndLimits() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-hud-limit-");
            List<Task> tasks = List.of(
                    createTask("hud-pending-first", "hud-project-a", false, Task.Scope.TEAM),
                    createTask("hud-pending-second", "hud-project-a", false, Task.Scope.TEAM),
                    createTask("hud-done-first", "hud-project-a", true, Task.Scope.TEAM),
                    createTask("hud-other-project", "hud-project-b", false, Task.Scope.TEAM)
            );
            new H2TaskStore().saveTeamTasks(tasks);

            H2TaskQueryService.HudTaskQueryResult result = new H2TaskQueryService().queryHudTasks(new H2TaskQueryService.HudTaskQuery(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    List.of("hud-project-a"),
                    false,
                    H2TaskQueryService.HudAssigneeFilter.ANY,
                    "",
                    1,
                    1
            ));

            GuiTestSupport.assertEquals(2, result.getPendingTotal(), "HUD 查询应返回未完成匹配总数");
            GuiTestSupport.assertEquals(1, result.getDoneTotal(), "HUD 查询应返回已完成匹配总数");
            GuiTestSupport.assertEquals(List.of("hud-pending-first"), titles(result.getPendingTasks()), "HUD 未完成可绘制行应受 limit 限制并保持顺序");
            GuiTestSupport.assertEquals(List.of("hud-done-first"), titles(result.getDoneTasks()), "HUD 已完成可绘制行应受 limit 限制并保持顺序");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 HUD limit 查询时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证 HUD 查询能按指派状态过滤团队任务。
     */
    private static void shouldQueryHudAssignedAndUnassignedViews() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-hud-assignee-");
            Task assigned = createTask("hud-assigned", "hud-team-project", false, Task.Scope.TEAM);
            assigned.setAssigneeUuid("player-a");
            Task unassigned = createTask("hud-unassigned", "hud-team-project", false, Task.Scope.TEAM);
            Task otherAssigned = createTask("hud-other-assigned", "hud-team-project", false, Task.Scope.TEAM);
            otherAssigned.setAssigneeUuid("player-b");
            new H2TaskStore().saveTeamTasks(List.of(assigned, unassigned, otherAssigned));

            H2TaskQueryService queryService = new H2TaskQueryService();
            H2TaskQueryService.HudTaskQueryResult assignedResult = queryService.queryHudTasks(new H2TaskQueryService.HudTaskQuery(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    List.of("hud-team-project"),
                    false,
                    H2TaskQueryService.HudAssigneeFilter.ASSIGNED_TO_PLAYER,
                    "player-a",
                    10,
                    10
            ));
            H2TaskQueryService.HudTaskQueryResult unassignedResult = queryService.queryHudTasks(new H2TaskQueryService.HudTaskQuery(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    List.of("hud-team-project"),
                    false,
                    H2TaskQueryService.HudAssigneeFilter.UNASSIGNED,
                    "",
                    10,
                    10
            ));

            GuiTestSupport.assertEquals(List.of("hud-assigned"), titles(assignedResult.getPendingTasks()), "ASSIGNED HUD 查询应只返回当前玩家任务");
            GuiTestSupport.assertEquals(1, assignedResult.getPendingTotal(), "ASSIGNED HUD 查询总数应只统计当前玩家任务");
            GuiTestSupport.assertEquals(List.of("hud-unassigned"), titles(unassignedResult.getPendingTasks()), "UNASSIGNED HUD 查询应只返回未指派任务");
            GuiTestSupport.assertEquals(1, unassignedResult.getPendingTotal(), "UNASSIGNED HUD 查询总数应只统计未指派任务");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 HUD 指派过滤查询时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证团队项目列表为空时，HUD SQL 查询可回退为任意已绑定项目任务。
     */
    private static void shouldQueryHudAnyAssignedProjectFallback() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-hud-any-project-");
            List<Task> tasks = List.of(
                    createTask("hud-bound-a", "bound-project-a", false, Task.Scope.TEAM),
                    createTask("hud-bound-b", "bound-project-b", true, Task.Scope.TEAM),
                    createTask("hud-unbound", null, false, Task.Scope.TEAM)
            );
            new H2TaskStore().saveTeamTasks(tasks);

            H2TaskQueryService.HudTaskQueryResult result = new H2TaskQueryService().queryHudTasks(new H2TaskQueryService.HudTaskQuery(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    List.of(),
                    true,
                    H2TaskQueryService.HudAssigneeFilter.ANY,
                    "",
                    10,
                    10
            ));

            GuiTestSupport.assertEquals(List.of("hud-bound-a"), titles(result.getPendingTasks()), "任意项目兜底不应包含未绑定项目任务");
            GuiTestSupport.assertEquals(List.of("hud-bound-b"), titles(result.getDoneTasks()), "任意项目兜底应包含已绑定项目的已完成任务");
            GuiTestSupport.assertEquals(1, result.getPendingTotal(), "任意项目兜底未完成总数应只统计已绑定项目");
            GuiTestSupport.assertEquals(1, result.getDoneTotal(), "任意项目兜底已完成总数应只统计已绑定项目");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 HUD 任意项目兜底查询时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证 GUI SQL 查询在项目、优先级、搜索和指派过滤上与内存筛选一致。
     */
    private static void shouldQueryGuiTaskIdsLikeMemoryFilters() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-gui-");
            Task matched = createTask("Alpha Match", "gui-project", false, Task.Scope.TEAM);
            matched.setPriority(Task.Priority.HIGH);
            matched.setAssigneeUuid("player-a");
            matched.addTag("中文标签");
            Task wrongPriority = createTask("Alpha Low", "gui-project", false, Task.Scope.TEAM);
            wrongPriority.setPriority(Task.Priority.LOW);
            wrongPriority.setAssigneeUuid("player-a");
            Task wrongAssignee = createTask("Alpha Other", "gui-project", false, Task.Scope.TEAM);
            wrongAssignee.setPriority(Task.Priority.HIGH);
            wrongAssignee.setAssigneeUuid("player-b");
            Task completed = createTask("Alpha Done", "gui-project", true, Task.Scope.TEAM);
            completed.setPriority(Task.Priority.HIGH);
            completed.setAssigneeUuid("player-a");
            new H2TaskStore().saveTeamTasks(List.of(matched, wrongPriority, wrongAssignee, completed));

            List<String> actual = new H2TaskQueryService().queryGuiTaskIds(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    "gui-project",
                    false,
                    Task.Priority.HIGH.name(),
                    H2TaskQueryService.HudAssigneeFilter.ASSIGNED_TO_PLAYER,
                    "player-a",
                    "中文"
            );

            GuiTestSupport.assertEquals(List.of(matched.getId()), actual, "GUI SQL 查询应匹配项目、优先级、指派和中文标签搜索");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 GUI 任务查询时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证 GUI SQL 搜索会把 LIKE 通配符当普通字符处理。
     */
    private static void shouldTreatLikeWildcardsAsLiteralSearchText() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-gui-wildcard-");
            Task percent = createTask("100% Ready", "wild-project", false, Task.Scope.PERSONAL);
            Task underscore = createTask("abc_def", "wild-project", false, Task.Scope.PERSONAL);
            Task other = createTask("abcXdef", "wild-project", false, Task.Scope.PERSONAL);
            new H2TaskStore().saveLocalTasks(List.of(percent, underscore, other));

            H2TaskQueryService queryService = new H2TaskQueryService();
            List<String> percentResult = queryService.queryGuiTaskIds(
                    H2TaskStore.LOCAL_PERSONAL_BUCKET,
                    H2TaskStore.LOCAL_OWNER,
                    "wild-project",
                    false,
                    "",
                    H2TaskQueryService.HudAssigneeFilter.ANY,
                    "",
                    "%"
            );
            List<String> underscoreResult = queryService.queryGuiTaskIds(
                    H2TaskStore.LOCAL_PERSONAL_BUCKET,
                    H2TaskStore.LOCAL_OWNER,
                    "wild-project",
                    false,
                    "",
                    H2TaskQueryService.HudAssigneeFilter.ANY,
                    "",
                    "_"
            );

            GuiTestSupport.assertEquals(List.of(percent.getId()), percentResult, "百分号搜索应只匹配字面百分号");
            GuiTestSupport.assertEquals(List.of(underscore.getId()), underscoreResult, "下划线搜索应只匹配字面下划线");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 GUI 通配符搜索时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 验证 GUI SQL 查询支持分页读取，并可单独统计完整匹配总数。
     */
    private static void shouldQueryGuiTaskIdsWithLimitOffsetAndCount() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-gui-page-");
            Task first = createTask("Page 1", "page-project", true, Task.Scope.PERSONAL);
            Task second = createTask("Page 2", "page-project", true, Task.Scope.PERSONAL);
            Task third = createTask("Page 3", "page-project", true, Task.Scope.PERSONAL);
            Task fourth = createTask("Page 4", "page-project", true, Task.Scope.PERSONAL);
            Task otherProject = createTask("Page other", "other-project", true, Task.Scope.PERSONAL);
            new H2TaskStore().saveLocalTasks(List.of(first, second, third, fourth, otherProject));

            H2TaskQueryService queryService = new H2TaskQueryService();
            List<String> page = queryService.queryGuiTaskIds(
                    H2TaskStore.LOCAL_PERSONAL_BUCKET,
                    H2TaskStore.LOCAL_OWNER,
                    "page-project",
                    true,
                    "",
                    H2TaskQueryService.HudAssigneeFilter.ANY,
                    "",
                    "",
                    2,
                    1
            );
            int total = queryService.countGuiTaskIds(
                    H2TaskStore.LOCAL_PERSONAL_BUCKET,
                    H2TaskStore.LOCAL_OWNER,
                    "page-project",
                    true,
                    "",
                    H2TaskQueryService.HudAssigneeFilter.ANY,
                    "",
                    ""
            );

            GuiTestSupport.assertEquals(List.of(second.getId(), third.getId()), page, "GUI SQL 分页应保持 sort_order 顺序并应用 offset");
            GuiTestSupport.assertEquals(4, total, "GUI SQL 总数应忽略分页限制并按完整过滤条件统计");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 GUI 分页查询时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 准备独立测试游戏目录。
     *
     * @param prefix 临时目录前缀
     * @return 临时游戏目录
     * @throws Exception 准备失败时抛出
     */
    private static Path prepareTempGameDir(String prefix) throws Exception {
        Path tempGameDir = Files.createTempDirectory(prefix);
        DataPathProvider.setGameDirSupplier(() -> tempGameDir);
        DataPathProvider.resetStorageNamespace();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        H2StorageBootstrap.resetAllForTests();
        return tempGameDir;
    }

    /**
     * 创建测试任务。
     *
     * @param title 任务标题
     * @param projectId 项目 ID
     * @param completed 是否已完成
     * @param scope 任务空间
     * @return 测试任务
     */
    private static Task createTask(String title, String projectId, boolean completed, Task.Scope scope) {
        Task task = new Task(title, "");
        task.setId(title + "-id");
        task.setProjectId(projectId);
        task.setCompleted(completed);
        task.setScope(scope);
        return task;
    }

    /**
     * 构造用于对照 SQL 结果的内存任务管理器。
     *
     * @param tasks 任务列表
     * @return 任务管理器
     */
    private static TaskManager buildTaskManager(List<Task> tasks) {
        TaskManager manager = new TaskManager();
        for (Task task : tasks) {
            manager.addTask(task);
        }
        return manager;
    }

    /**
     * 提取任务标题列表。
     *
     * @param tasks 任务列表
     * @return 标题列表
     */
    private static List<String> titles(List<Task> tasks) {
        return tasks.stream().map(Task::getTitle).toList();
    }

    /**
     * 清理测试状态和临时目录。
     *
     * @param tempGameDir 临时游戏目录
     */
    private static void cleanup(Path tempGameDir) {
        try {
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
            H2StorageBootstrap.resetAllForTests();
            if (tempGameDir == null || !Files.exists(tempGameDir)) {
                return;
            }
            try (var paths = Files.walk(tempGameDir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法删除 H2 查询测试临时文件: " + path, exception);
                    }
                });
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理 H2 查询测试临时目录", exception);
        }
    }
}
