package com.todolist.task;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.List;

/**
 * TaskManagerSubtaskTestMain 覆盖 Phase 4 的顶层计数与父任务完成状态聚合行为。
 */
public final class TaskManagerSubtaskTestMain {
    /**
     * 工具类不需要实例化。
     */
    private TaskManagerSubtaskTestMain() {
    }

    /**
     * 执行 TaskManager 子任务聚合测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldCountOnlyTopLevelTasksByProject", TaskManagerSubtaskTestMain::shouldCountOnlyTopLevelTasksByProject);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldAggregateParentCompletionFromChildren", TaskManagerSubtaskTestMain::shouldAggregateParentCompletionFromChildren);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldToggleAllDirectChildrenWhenParentToggled", TaskManagerSubtaskTestMain::shouldToggleAllDirectChildrenWhenParentToggled);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldKeepTriggeredParentCompletionFromBeingOverwritten", TaskManagerSubtaskTestMain::shouldKeepTriggeredParentCompletionFromBeingOverwritten);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldKeepTriggeredParentIncompleteWhenAllChildrenComplete", TaskManagerSubtaskTestMain::shouldKeepTriggeredParentIncompleteWhenAllChildrenComplete);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldManuallyToggleTriggeredParentWithChildren", TaskManagerSubtaskTestMain::shouldManuallyToggleTriggeredParentWithChildren);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldAggregateThreeLevelCompletionBottomUp", TaskManagerSubtaskTestMain::shouldAggregateThreeLevelCompletionBottomUp);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldCascadeDeleteDescendants", TaskManagerSubtaskTestMain::shouldCascadeDeleteDescendants);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldToggleAllDescendantsWhenParentToggled", TaskManagerSubtaskTestMain::shouldToggleAllDescendantsWhenParentToggled);
    }

    /**
     * 验证三层依赖任务的完成态自底向上聚合：叶子全部完成时中间层与顶层都完成，
     * 任一叶子回退为未完成时整条链同步回退。
     */
    private static void shouldAggregateThreeLevelCompletionBottomUp() {
        TaskManager manager = new TaskManager();
        Task root = createTask("root", "project-a", false, null);
        Task middle = createTask("middle", "project-a", false, root.getId());
        Task leaf = createTask("leaf", "project-a", false, middle.getId());
        addAll(manager, root, middle, leaf);

        manager.markParentCompletionDirty();
        GuiTestSupport.assertFalse(manager.getTask(root.getId()).isCompleted(),
                "叶子未完成时顶层任务应保持未完成");
        GuiTestSupport.assertFalse(manager.getTask(middle.getId()).isCompleted(),
                "叶子未完成时中间层任务应保持未完成");

        leaf.setCompleted(true);
        manager.markParentCompletionDirty();
        GuiTestSupport.assertTrue(manager.getTask(middle.getId()).isCompleted(),
                "叶子完成后中间层任务应聚合为已完成");
        GuiTestSupport.assertTrue(manager.getTask(root.getId()).isCompleted(),
                "叶子完成后顶层任务应自底向上聚合为已完成");

        leaf.setCompleted(false);
        manager.markParentCompletionDirty();
        GuiTestSupport.assertFalse(manager.getTask(root.getId()).isCompleted(),
                "叶子回退为未完成时顶层任务应同步回退为未完成");
    }

    /**
     * 验证勾选多层级父任务时会级联切换其全部后代，而不是只切换直属子任务。
     */
    private static void shouldToggleAllDescendantsWhenParentToggled() {
        TaskManager manager = new TaskManager();
        Task root = createTask("root", "project-a", false, null);
        Task middle = createTask("middle", "project-a", false, root.getId());
        Task leaf = createTask("leaf", "project-a", false, middle.getId());
        addAll(manager, root, middle, leaf);

        manager.toggleTaskCompletion(root.getId());

        GuiTestSupport.assertTrue(manager.getTask(middle.getId()).isCompleted(),
                "勾选顶层任务应级联完成中间层任务");
        GuiTestSupport.assertTrue(manager.getTask(leaf.getId()).isCompleted(),
                "勾选顶层任务应级联完成孙任务");
    }

    /**
     * 验证删除任务会级联删除其全部后代，避免留下孤儿子任务。
     */
    private static void shouldCascadeDeleteDescendants() {
        TaskManager manager = new TaskManager();
        Task root = createTask("root", "project-a", false, null);
        Task middle = createTask("middle", "project-a", false, root.getId());
        Task leaf = createTask("leaf", "project-a", false, middle.getId());
        Task other = createTask("other", "project-a", false, null);
        addAll(manager, root, middle, leaf, other);

        manager.deleteTask(root.getId());

        GuiTestSupport.assertNull(manager.getTask(root.getId()), "顶层任务应被删除");
        GuiTestSupport.assertNull(manager.getTask(middle.getId()), "子任务应随父任务级联删除");
        GuiTestSupport.assertNull(manager.getTask(leaf.getId()), "孙任务应随父任务级联删除");
        GuiTestSupport.assertNotNull(manager.getTask(other.getId()), "无关任务不应受影响");
    }

    /**
     * 验证父任务挂有触发器时完成态由触发器决定：子任务聚合不再覆盖它（触发器优先），
     * 未挂触发器的父任务仍维持原有的聚合行为。
     */
    private static void shouldKeepTriggeredParentCompletionFromBeingOverwritten() {
        TaskManager manager = new TaskManager();
        Task triggeredParent = createTask("triggered-parent", "project-a", false, null);
        triggeredParent.setTrigger(new TaskTrigger(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 1));
        Task pendingChild = createTask("pending-child", "project-a", false, triggeredParent.getId());
        addAll(manager, triggeredParent, pendingChild);

        // 触发器达标：父任务被置为已完成，此时仍有未完成子任务
        triggeredParent.setCompleted(true);
        manager.markParentCompletionDirty();
        GuiTestSupport.assertTrue(manager.getTask(triggeredParent.getId()).isCompleted(),
                "父任务挂触发器时，未完成子任务不应把父任务完成态改回未完成");

        Task plainParent = createTask("plain-parent", "project-a", false, null);
        Task plainChild = createTask("plain-child", "project-a", false, plainParent.getId());
        addAll(manager, plainParent, plainChild);
        manager.markParentCompletionDirty();
        GuiTestSupport.assertFalse(manager.getTask(plainParent.getId()).isCompleted(),
                "未挂触发器的父任务仍应由子任务聚合决定完成态");
    }

    /**
     * 验证挂触发器的多层父任务不会因子任务全部完成而被自动完成：
     * 材料齐了不等于目标物品真的做出来了，必须等触发器达标或手动勾选。
     */
    private static void shouldKeepTriggeredParentIncompleteWhenAllChildrenComplete() {
        TaskManager manager = new TaskManager();
        Task root = createTask("root", "project-a", false, null);
        root.setTrigger(new TaskTrigger(TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_block", 3));
        Task middle = createTask("middle", "project-a", false, root.getId());
        middle.setTrigger(new TaskTrigger(TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_ingot", 27));
        Task leaf = createTask("leaf", "project-a", false, middle.getId());
        addAll(manager, root, middle, leaf);

        leaf.setCompleted(true);
        manager.markParentCompletionDirty();

        GuiTestSupport.assertFalse(manager.getTask(root.getId()).isCompleted(),
                "带触发器的顶层任务不应因子任务全部完成而自动完成");
        GuiTestSupport.assertFalse(manager.getTask(middle.getId()).isCompleted(),
                "带触发器的中间层任务同样不应被自动完成");
        GuiTestSupport.assertTrue(manager.getTask(leaf.getId()).isCompleted(),
                "无触发器的最终材料任务仍按自身完成态");
    }

    /**
     * 验证带触发器的父任务支持手动勾选：直接切换自身完成态（触发器优先），
     * 且不会被后续的子任务聚合覆盖。
     */
    private static void shouldManuallyToggleTriggeredParentWithChildren() {
        TaskManager manager = new TaskManager();
        Task parent = createTask("parent", "project-a", false, null);
        parent.setTrigger(new TaskTrigger(TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_block", 3));
        Task child = createTask("child", "project-a", false, parent.getId());
        addAll(manager, parent, child);

        manager.toggleTaskCompletion(parent.getId());
        GuiTestSupport.assertTrue(manager.getTask(parent.getId()).isCompleted(),
                "手动勾选带触发器的父任务应直接完成自身");
        GuiTestSupport.assertTrue(manager.getTask(child.getId()).isCompleted(),
                "手动勾选父任务时应级联完成无触发器的子任务");

        manager.toggleTaskCompletion(parent.getId());
        GuiTestSupport.assertFalse(manager.getTask(parent.getId()).isCompleted(),
                "再次勾选应取消带触发器父任务的完成态");
    }

    /**
     * 验证项目计数与完成分组默认只统计顶层任务。
     */
    private static void shouldCountOnlyTopLevelTasksByProject() {
        TaskManager manager = new TaskManager();
        Task parentPending = createTask("parent-pending", "project-a", false, null);
        Task childPending = createTask("child-pending", "project-a", false, parentPending.getId());
        Task parentDone = createTask("parent-done", "project-a", true, null);
        Task childDone = createTask("child-done", "project-a", true, parentDone.getId());
        Task otherProject = createTask("other-project", "project-b", false, null);
        addAll(manager, parentPending, childPending, parentDone, childDone, otherProject);

        GuiTestSupport.assertEquals(List.of(parentPending, parentDone), manager.getTasksByProject("project-a"), "项目任务列表应只包含顶层任务");
        GuiTestSupport.assertEquals(List.of(parentDone), manager.getCompletedTasks(), "已完成分组应只包含顶层任务");
        GuiTestSupport.assertEquals(List.of(parentPending, otherProject), manager.getIncompleteTasks(), "未完成分组应只包含顶层任务");
    }

    /**
     * 验证切换子任务完成状态时会自动聚合父任务状态。
     */
    private static void shouldAggregateParentCompletionFromChildren() {
        TaskManager manager = new TaskManager();
        Task parent = createTask("parent", "project-a", false, null);
        Task childA = createTask("child-a", "project-a", false, parent.getId());
        Task childB = createTask("child-b", "project-a", false, parent.getId());
        addAll(manager, parent, childA, childB);

        manager.toggleTaskCompletion(childA.getId());
        GuiTestSupport.assertFalse(manager.getTask(parent.getId()).isCompleted(), "只完成部分子任务时父任务不应被标记完成");

        manager.toggleTaskCompletion(childB.getId());
        GuiTestSupport.assertTrue(manager.getTask(parent.getId()).isCompleted(), "全部子任务完成后父任务应自动完成");

        manager.toggleTaskCompletion(childA.getId());
        GuiTestSupport.assertFalse(manager.getTask(parent.getId()).isCompleted(), "任一子任务恢复未完成后父任务应同步恢复未完成");
    }

    /**
     * 验证切换父任务时会批量切换直属子任务，并同步父任务聚合完成态。
     */
    private static void shouldToggleAllDirectChildrenWhenParentToggled() {
        TaskManager manager = new TaskManager();
        Task parent = createTask("parent", "project-a", false, null);
        Task childA = createTask("child-a", "project-a", false, parent.getId());
        Task childB = createTask("child-b", "project-a", true, parent.getId());
        addAll(manager, parent, childA, childB);

        manager.toggleTaskCompletion(parent.getId());

        GuiTestSupport.assertTrue(manager.getTask(childA.getId()).isCompleted(), "父任务切为完成时应补齐未完成直属子任务");
        GuiTestSupport.assertTrue(manager.getTask(childB.getId()).isCompleted(), "父任务切为完成时应保持已完成直属子任务");
        GuiTestSupport.assertTrue(manager.getTask(parent.getId()).isCompleted(), "直属子任务全部完成后父任务应同步完成");

        manager.toggleTaskCompletion(parent.getId());

        GuiTestSupport.assertFalse(manager.getTask(childA.getId()).isCompleted(), "父任务取消完成时应批量恢复直属子任务为未完成");
        GuiTestSupport.assertFalse(manager.getTask(childB.getId()).isCompleted(), "父任务取消完成时应批量恢复直属子任务为未完成");
        GuiTestSupport.assertFalse(manager.getTask(parent.getId()).isCompleted(), "直属子任务恢复未完成后父任务也应同步恢复");
    }

    /**
     * 创建测试任务。
     *
     * @param title 标题
     * @param projectId 项目 ID
     * @param completed 是否完成
     * @param parentTaskId 父任务 ID
     * @return 测试任务
     */
    private static Task createTask(String title, String projectId, boolean completed, String parentTaskId) {
        Task task = new Task(title, "");
        task.setId(title + "-id");
        task.setProjectId(projectId);
        task.setCompleted(completed);
        task.setParentTaskId(parentTaskId);
        task.setScope(Task.Scope.TEAM);
        return task;
    }

    /**
     * 批量向管理器添加任务。
     *
     * @param manager 任务管理器
     * @param tasks 任务数组
     */
    private static void addAll(TaskManager manager, Task... tasks) {
        for (Task task : tasks) {
            manager.addTask(task);
        }
    }
}
