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
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQuerySubtasksByParentTaskId", H2TaskQueryServiceTestMain::shouldQuerySubtasksByParentTaskId);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryHudTasksWithTotalsAndLimits", H2TaskQueryServiceTestMain::shouldQueryHudTasksWithTotalsAndLimits);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryHudAssignedAndUnassignedViews", H2TaskQueryServiceTestMain::shouldQueryHudAssignedAndUnassignedViews);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryHudAnyAssignedProjectFallback", H2TaskQueryServiceTestMain::shouldQueryHudAnyAssignedProjectFallback);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryGuiTaskIdsLikeMemoryFilters", H2TaskQueryServiceTestMain::shouldQueryGuiTaskIdsLikeMemoryFilters);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldKeepParentContextWhenGuiSearchMatchesSubtask", H2TaskQueryServiceTestMain::shouldKeepParentContextWhenGuiSearchMatchesSubtask);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldTreatLikeWildcardsAsLiteralSearchText", H2TaskQueryServiceTestMain::shouldTreatLikeWildcardsAsLiteralSearchText);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldQueryGuiTaskIdsWithLimitOffsetAndCount", H2TaskQueryServiceTestMain::shouldQueryGuiTaskIdsWithLimitOffsetAndCount);
        GuiTestSupport.runTestCase("H2TaskQueryServiceTestMain.shouldPreserveTriggerAndAllColumnsThroughQueryRoundtrip", H2TaskQueryServiceTestMain::shouldPreserveTriggerAndAllColumnsThroughQueryRoundtrip);
    }

    /**
     * 验证本地个人任务项目计数与 TaskManager 内存结果一致。
     */
    private static void shouldCountLocalTasksByProjectLikeTaskManager() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-local-");
            Task projectAParentActive = createTask("local-active-a", "project-a", false, Task.Scope.PERSONAL);
            Task projectAChild = createSubtask("local-child-a", "project-a", false, Task.Scope.PERSONAL, projectAParentActive.getId(), 0L);
            Task projectADone = createTask("local-done-a", "project-a", true, Task.Scope.PERSONAL);
            Task projectBParent = createTask("local-active-b", "project-b", false, Task.Scope.PERSONAL);
            Task projectBChild = createSubtask("local-child-b", "project-b", true, Task.Scope.PERSONAL, projectBParent.getId(), 0L);
            Task unassigned = createTask("local-unassigned", null, false, Task.Scope.PERSONAL);
            List<Task> tasks = List.of(projectAParentActive, projectAChild, projectADone, projectBParent, projectBChild, unassigned);
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
            Task teamAParentActive = createTask("team-active-a", "team-project-a", false, Task.Scope.TEAM);
            Task teamAChild = createSubtask("team-child-a", "team-project-a", false, Task.Scope.TEAM, teamAParentActive.getId(), 0L);
            Task teamADone = createTask("team-done-a", "team-project-a", true, Task.Scope.TEAM);
            Task teamBParent = createTask("team-active-b", "team-project-b", false, Task.Scope.TEAM);
            Task teamBChild = createSubtask("team-child-b", "team-project-b", true, Task.Scope.TEAM, teamBParent.getId(), 0L);
            Task teamUnassigned = createTask("team-unassigned", "", false, Task.Scope.TEAM);
            List<Task> tasks = List.of(teamAParentActive, teamAChild, teamADone, teamBParent, teamBChild, teamUnassigned);
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
     * 验证 H2 可按父任务查询子任务，并按父内排序返回。
     */
    private static void shouldQuerySubtasksByParentTaskId() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-subtasks-");
            Task parent = createTask("subtask-parent", "sub-project", false, Task.Scope.TEAM);
            Task childB = createSubtask("subtask-child-b", "sub-project", false, Task.Scope.TEAM, parent.getId(), 1L);
            Task childA = createSubtask("subtask-child-a", "sub-project", true, Task.Scope.TEAM, parent.getId(), 0L);
            Task otherParent = createTask("other-parent", "sub-project", false, Task.Scope.TEAM);
            Task otherChild = createSubtask("other-child", "sub-project", false, Task.Scope.TEAM, otherParent.getId(), 0L);
            new H2TaskStore().saveTeamTasks(List.of(parent, childB, childA, otherParent, otherChild));

            List<Task> actual = new H2TaskQueryService().querySubtasksByParentTaskId(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    parent.getId()
            );

            GuiTestSupport.assertEquals(List.of("subtask-child-a", "subtask-child-b"), titles(actual), "子任务查询应仅返回目标父任务下的子任务并按父内顺序排序");
            GuiTestSupport.assertEquals(parent.getId(), actual.get(0).getParentTaskId(), "返回的子任务应保留父任务 ID");
            GuiTestSupport.assertEquals(0L, actual.get(0).getSubtaskSortOrder(), "首个子任务应保持父内顺序");
            GuiTestSupport.assertEquals(1L, actual.get(1).getSubtaskSortOrder(), "第二个子任务应保持父内顺序");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 子任务展开查询时发生异常", exception);
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
            Task pendingFirst = createTask("hud-pending-first", "hud-project-a", false, Task.Scope.TEAM);
            Task pendingSecond = createTask("hud-pending-second", "hud-project-a", false, Task.Scope.TEAM);
            Task pendingChild = createSubtask("hud-pending-child", "hud-project-a", false, Task.Scope.TEAM, pendingFirst.getId(), 0L);
            Task doneFirst = createTask("hud-done-first", "hud-project-a", true, Task.Scope.TEAM);
            Task doneChild = createSubtask("hud-done-child", "hud-project-a", true, Task.Scope.TEAM, doneFirst.getId(), 0L);
            Task otherProject = createTask("hud-other-project", "hud-project-b", false, Task.Scope.TEAM);
            List<Task> tasks = List.of(pendingFirst, pendingSecond, pendingChild, doneFirst, doneChild, otherProject);
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

            GuiTestSupport.assertEquals(2, result.getPendingTotal(), "HUD 查询应只统计顶层未完成任务总数");
            GuiTestSupport.assertEquals(1, result.getDoneTotal(), "HUD 查询应只统计顶层已完成任务总数");
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
            Task matchedChild = createSubtask("Alpha Child", "gui-project", false, Task.Scope.TEAM, matched.getId(), 0L);
            matchedChild.addTag("中文子标签");
            Task wrongPriority = createTask("Alpha Low", "gui-project", false, Task.Scope.TEAM);
            wrongPriority.setPriority(Task.Priority.LOW);
            wrongPriority.setAssigneeUuid("player-a");
            Task wrongAssignee = createTask("Alpha Other", "gui-project", false, Task.Scope.TEAM);
            wrongAssignee.setPriority(Task.Priority.HIGH);
            wrongAssignee.setAssigneeUuid("player-b");
            Task completed = createTask("Alpha Done", "gui-project", true, Task.Scope.TEAM);
            completed.setPriority(Task.Priority.HIGH);
            completed.setAssigneeUuid("player-a");
            new H2TaskStore().saveTeamTasks(List.of(matched, matchedChild, wrongPriority, wrongAssignee, completed));

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
     * 验证 GUI SQL 搜索命中子任务时会返回其父任务，保留顶层上下文。
     */
    private static void shouldKeepParentContextWhenGuiSearchMatchesSubtask() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-gui-subtask-context-");
            Task parent = createTask("Parent Wrapper", "gui-project", false, Task.Scope.TEAM);
            parent.setPriority(Task.Priority.LOW);
            Task child = createSubtask("Child Search Hit", "gui-project", false, Task.Scope.TEAM, parent.getId(), 0L);
            child.setPriority(Task.Priority.HIGH);
            child.setAssigneeUuid("player-a");
            child.addTag("中文子命中");
            Task otherParent = createTask("Other Parent", "gui-project", false, Task.Scope.TEAM);
            otherParent.setPriority(Task.Priority.HIGH);
            otherParent.setAssigneeUuid("player-b");
            new H2TaskStore().saveTeamTasks(List.of(parent, child, otherParent));

            H2TaskQueryService queryService = new H2TaskQueryService();
            List<String> actual = queryService.queryGuiTaskIds(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    "gui-project",
                    false,
                    Task.Priority.HIGH.name(),
                    H2TaskQueryService.HudAssigneeFilter.ASSIGNED_TO_PLAYER,
                    "player-a",
                    "中文子"
            );
            int total = queryService.countGuiTaskIds(
                    H2TaskStore.TEAM_BUCKET,
                    H2TaskStore.TEAM_OWNER,
                    "gui-project",
                    false,
                    Task.Priority.HIGH.name(),
                    H2TaskQueryService.HudAssigneeFilter.ASSIGNED_TO_PLAYER,
                    "player-a",
                    "中文子"
            );

            GuiTestSupport.assertEquals(List.of(parent.getId()), actual, "GUI SQL 搜索命中子任务时应返回父任务 ID");
            GuiTestSupport.assertEquals(1, total, "GUI SQL 搜索命中子任务时总数应按父任务计数");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 GUI 搜索保留父任务上下文时发生异常", exception);
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
            Task secondChild = createSubtask("Page 2 child", "page-project", true, Task.Scope.PERSONAL, second.getId(), 0L);
            Task third = createTask("Page 3", "page-project", true, Task.Scope.PERSONAL);
            Task fourth = createTask("Page 4", "page-project", true, Task.Scope.PERSONAL);
            Task otherProject = createTask("Page other", "other-project", true, Task.Scope.PERSONAL);
            new H2TaskStore().saveLocalTasks(List.of(first, second, secondChild, third, fourth, otherProject));

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
            GuiTestSupport.assertEquals(4, total, "GUI SQL 总数应忽略分页限制并只统计顶层任务");
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
     * 创建测试子任务。
     *
     * @param title 任务标题
     * @param projectId 项目 ID
     * @param completed 是否已完成
     * @param scope 任务空间
     * @param parentTaskId 父任务 ID
     * @param subtaskSortOrder 父内顺序
     * @return 测试子任务
     */
    private static Task createSubtask(String title,
                                      String projectId,
                                      boolean completed,
                                      Task.Scope scope,
                                      String parentTaskId,
                                      long subtaskSortOrder) {
        Task task = createTask(title, projectId, completed, scope);
        task.setParentTaskId(parentTaskId);
        task.setSubtaskSortOrder(subtaskSortOrder);
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

    /**
     * 回归（2026-11-01 触发器列缺失缺陷）：GUI 的 H2 查询链路（主查询/子任务查询）曾缺
     * trigger 四列且 readTask 不构建触发器，替换式保存会把触发器清空，表现为所有子任务
     * 触发器不生效。本用例对两条修复后的查询链路做全字段往返断言，防止未来再加列时漏同步。
     */
    private static void shouldPreserveTriggerAndAllColumnsThroughQueryRoundtrip() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-query-roundtrip-");

            Task parent = createTask("roundtrip-parent", "roundtrip-project", false, Task.Scope.PERSONAL);
            parent.setDescription("全字段往返父任务");
            parent.setPriority(Task.Priority.HIGH);
            parent.setDueDate(1735689600000L);
            parent.setCreatorUuid("roundtrip-creator");
            parent.setAssigneeUuid("roundtrip-owner");
            parent.setAssigneeName("往返玩家");
            parent.setTags(java.util.List.of("alpha", "beta"));
            parent.setTrigger(new com.todolist.task.TaskTrigger(
                    com.todolist.task.TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_ingot", 3));
            parent.getTrigger().setProgress(1);

            Task child = createSubtask("roundtrip-child", "roundtrip-project", false, Task.Scope.PERSONAL,
                    parent.getId(), 0L);
            child.setAssigneeUuid("roundtrip-owner");
            child.setTrigger(new com.todolist.task.TaskTrigger(
                    com.todolist.task.TaskTrigger.Type.ITEM_COLLECT, "minecraft:raw_iron", 27));
            child.getTrigger().setProgress(9);

            new H2TaskStore().saveLocalTasks(List.of(parent, child));

            H2TaskQueryService queryService = new H2TaskQueryService();

            // 链路一：子任务查询（parent_task_id = ?）
            List<Task> subtasks = queryService.querySubtasksByParentTaskId(
                    H2TaskStore.LOCAL_PERSONAL_BUCKET, H2TaskStore.LOCAL_OWNER, parent.getId());
            GuiTestSupport.assertEquals(1, subtasks.size(), "子任务查询应返回 1 条");
            assertTriggerRoundtrip(subtasks.get(0), "子任务查询链路");
            assertRoundtripColumns(child, subtasks.get(0), "子任务查询链路");

            // 链路二：HUD 主查询（parent_task_id 不限定的全列查询）
            H2TaskQueryService.HudTaskQueryResult hud = queryService.queryHudTasks(
                    new H2TaskQueryService.HudTaskQuery(
                            H2TaskStore.LOCAL_PERSONAL_BUCKET, H2TaskStore.LOCAL_OWNER,
                            List.of("roundtrip-project"), false,
                            H2TaskQueryService.HudAssigneeFilter.ANY, "", 50, 50));
            List<Task> hudTasks = new java.util.ArrayList<>(hud.getPendingTasks());
            Task hudParent = hudTasks.stream()
                    .filter(t -> t.getId().equals(parent.getId())).findFirst().orElse(null);
            GuiTestSupport.assertTrue(hudParent != null, "HUD 查询应返回父任务");
            assertTriggerRoundtrip(hudParent, "HUD 主查询链路");
            assertRoundtripColumns(parent, hudParent, "HUD 主查询链路");

            // 链路三（线上 bug 的完整链路）：GUI 内存的任务对象来自查询链路（触发器可能缺失），
            // 替换式保存后再经查询加载，触发器必须完整保留。
            // 注意：HUD 查询只返回顶层任务（parent_task_id IS NULL），替换式保存必须使用
            // 完整任务列表（顶层 + 子任务），否则会误删子任务——这是替换式保存的既有契约。
            Task guiChildCopy = subtasks.get(0);
            List<Task> guiSnapshot = new java.util.ArrayList<>(hudTasks);
            guiSnapshot.add(guiChildCopy);
            new H2TaskStore().saveLocalTasks(guiSnapshot);
            List<Task> reloaded = queryService.querySubtasksByParentTaskId(
                    H2TaskStore.LOCAL_PERSONAL_BUCKET, H2TaskStore.LOCAL_OWNER, parent.getId());
            GuiTestSupport.assertEquals(1, reloaded.size(), "替换式保存后子任务查询应仍返回 1 条");
            assertTriggerRoundtrip(reloaded.get(0), "替换式保存往返");
            assertRoundtripColumns(child, reloaded.get(0), "替换式保存往返");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 查询链路全字段往返时发生异常", exception);
        } finally {
            cleanup(tempGameDir);
        }
    }

    /**
     * 断言任务经 H2 查询链路往返后触发器与关键列完整保留。
     */
    private static void assertRoundtripColumns(Task saved, Task loaded, String stage) {
        GuiTestSupport.assertEquals(saved.getDescription(), loaded.getDescription(),
                stage + ": description 应保持（列清单同步回归）");
        GuiTestSupport.assertEquals(saved.getPriority(), loaded.getPriority(),
                stage + ": priority 应保持（列清单同步回归）");
        GuiTestSupport.assertEquals(saved.getDueDate(), loaded.getDueDate(),
                stage + ": dueDate 应保持（列清单同步回归）");
        GuiTestSupport.assertEquals(saved.getCreatorUuid(), loaded.getCreatorUuid(),
                stage + ": creatorUuid 应保持（列清单同步回归）");
        GuiTestSupport.assertEquals(saved.getAssigneeUuid(), loaded.getAssigneeUuid(),
                stage + ": assigneeUuid 应保持（列清单同步回归）");
        GuiTestSupport.assertEquals(saved.getAssigneeName(), loaded.getAssigneeName(),
                stage + ": assigneeName 应保持（列清单同步回归）");
        GuiTestSupport.assertEquals(saved.getProjectId(), loaded.getProjectId(),
                stage + ": projectId 应保持（列清单同步回归）");
        GuiTestSupport.assertEquals(saved.getParentTaskId(), loaded.getParentTaskId(),
                stage + ": parentTaskId 应保持（列清单同步回归）");
        GuiTestSupport.assertTrue(
                java.util.Objects.equals(saved.getTags(), loaded.getTags())
                        || (loaded.getTags() != null && loaded.getTags().containsAll(saved.getTags())),
                stage + ": tags 应保持（列清单同步回归）");
    }

    private static void assertTriggerRoundtrip(Task task, String stage) {
        GuiTestSupport.assertTrue(task.getTrigger() != null,
                stage + ": " + task.getTitle() + " 的触发器应在往返后保留（缺列回归）");
        if (task.getTitle().contains("child")) {
            GuiTestSupport.assertEquals(com.todolist.task.TaskTrigger.Type.ITEM_COLLECT,
                    task.getTrigger().getType(), stage + ": 子任务触发器类型应保持");
            GuiTestSupport.assertEquals("minecraft:raw_iron", task.getTrigger().getTarget(),
                    stage + ": 子任务触发器目标应保持");
            GuiTestSupport.assertEquals(27, task.getTrigger().getTargetCount(),
                    stage + ": 子任务触发器目标数量应保持");
            GuiTestSupport.assertEquals(9, task.getTrigger().getProgress(),
                    stage + ": 子任务触发器进度应保持");
        } else {
            GuiTestSupport.assertEquals(com.todolist.task.TaskTrigger.Type.CRAFT_ITEM,
                    task.getTrigger().getType(), stage + ": 父任务触发器类型应保持");
            GuiTestSupport.assertEquals("minecraft:iron_ingot", task.getTrigger().getTarget(),
                    stage + ": 父任务触发器目标应保持");
            GuiTestSupport.assertEquals(3, task.getTrigger().getTargetCount(),
                    stage + ": 父任务触发器目标数量应保持");
            GuiTestSupport.assertEquals(1, task.getTrigger().getProgress(),
                    stage + ": 父任务触发器进度应保持");
        }
    }
}