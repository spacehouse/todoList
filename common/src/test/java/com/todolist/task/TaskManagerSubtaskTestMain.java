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
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldCompleteTaskIdempotentlyAcrossNestedLevels", TaskManagerSubtaskTestMain::shouldCompleteTaskIdempotentlyAcrossNestedLevels);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldAggregateCompletionAcrossTwelveLevels", TaskManagerSubtaskTestMain::shouldAggregateCompletionAcrossTwelveLevels);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldDeleteWholeSubtree", TaskManagerSubtaskTestMain::shouldDeleteWholeSubtree);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldRejectMoveIntoOwnDescendant", TaskManagerSubtaskTestMain::shouldRejectMoveIntoOwnDescendant);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldMoveSubtreeBetweenParents", TaskManagerSubtaskTestMain::shouldMoveSubtreeBetweenParents);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldRejectDuplicateTaskIdWithoutOverwritingExistingTask", TaskManagerSubtaskTestMain::shouldRejectDuplicateTaskIdWithoutOverwritingExistingTask);
        GuiTestSupport.runTestCase("TaskManagerSubtaskTestMain.shouldIgnoreOrphanParentIdWhenBuildingIndex", TaskManagerSubtaskTestMain::shouldIgnoreOrphanParentIdWhenBuildingIndex);
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
     * 验证明确完成方法在多层子树上具有幂等性，重复调用不会取消完成状态。
     */
    private static void shouldCompleteTaskIdempotentlyAcrossNestedLevels() {
        TaskManager manager = new TaskManager();
        Task root = manager.addTask("root", "");
        root.setProjectId("project-a");
        Task child = manager.addSubtask(root.getId(), "child", "");
        Task grandchild = manager.addSubtask(child.getId(), "grandchild", "");

        GuiTestSupport.assertTrue(manager.completeTask(root.getId()), "首次完成根任务应成功");
        GuiTestSupport.assertTrue(manager.getTask(root.getId()).isCompleted(), "首次完成后根任务应完成");
        GuiTestSupport.assertTrue(manager.getTask(child.getId()).isCompleted(), "首次完成后子任务应完成");
        GuiTestSupport.assertTrue(manager.getTask(grandchild.getId()).isCompleted(), "首次完成后孙任务应完成");

        GuiTestSupport.assertTrue(manager.completeTask(root.getId()), "重复完成已完成任务应返回成功");
        GuiTestSupport.assertTrue(manager.getTask(root.getId()).isCompleted(), "重复完成不应取消根任务");
        GuiTestSupport.assertTrue(manager.getTask(child.getId()).isCompleted(), "重复完成不应取消子任务");
        GuiTestSupport.assertTrue(manager.getTask(grandchild.getId()).isCompleted(), "重复完成不应取消孙任务");
        GuiTestSupport.assertFalse(manager.completeTask("missing-task"), "完成不存在的任务应返回失败");
    }

    /**
     * 验证十二层任务链的完成状态会自底向上聚合到根任务。
     */
    private static void shouldAggregateCompletionAcrossTwelveLevels() {
        TaskManager manager = new TaskManager();
        Task root = manager.addTask("root", "");
        root.setProjectId("project-a");
        Task current = root;
        for (int level = 1; level <= 12; level++) {
            current = manager.addSubtask(current.getId(), "level-" + level, "");
        }

        manager.toggleTaskCompletion(current.getId());
        GuiTestSupport.assertTrue(manager.getTask(root.getId()).isCompleted(), "最深层完成后所有祖先应完成");
        GuiTestSupport.assertEquals(12, manager.getTaskDepth(current.getId()), "最深层深度应为 12");

        manager.toggleTaskCompletion(current.getId());
        GuiTestSupport.assertFalse(manager.getTask(root.getId()).isCompleted(), "最深层取消完成后所有祖先应恢复未完成");
    }

    /**
     * 验证删除父任务会清理全部后代，不留下孤立数据。
     */
    private static void shouldDeleteWholeSubtree() {
        TaskManager manager = new TaskManager();
        Task root = manager.addTask("root", "");
        root.setProjectId("project-a");
        Task child = manager.addSubtask(root.getId(), "child", "");
        Task grandchild = manager.addSubtask(child.getId(), "grandchild", "");
        Task sibling = manager.addTask("sibling", "");
        sibling.setProjectId("project-a");

        int removed = manager.deleteTask(root.getId());

        GuiTestSupport.assertEquals(3, removed, "应删除父任务与两个后代");
        GuiTestSupport.assertEquals(1, manager.getAllTasks().size(), "无关任务应保留");
        GuiTestSupport.assertNull(manager.getTask(grandchild.getId()), "最深后代也必须删除");
    }

    /**
     * 验证无法把任务移动到自身后代之下。
     */
    private static void shouldRejectMoveIntoOwnDescendant() {
        TaskManager manager = new TaskManager();
        Task root = manager.addTask("root", "");
        root.setProjectId("project-a");
        Task child = manager.addSubtask(root.getId(), "child", "");
        Task grandchild = manager.addSubtask(child.getId(), "grandchild", "");

        GuiTestSupport.assertFalse(manager.canMoveTask(root.getId(), grandchild.getId()), "不能移动到自身后代之下");
        GuiTestSupport.assertFalse(manager.moveTask(root.getId(), grandchild.getId()), "非法移动必须被拒绝");
        GuiTestSupport.assertEquals(child.getId(), manager.getTask(grandchild.getId()).getParentTaskId(), "最深任务父关系应保持");
    }

    /**
     * 验证将子树移动到另一个父任务后，后代关系与深度保持一致。
     */
    private static void shouldMoveSubtreeBetweenParents() {
        TaskManager manager = new TaskManager();
        Task root = manager.addTask("root", "");
        root.setProjectId("project-a");
        Task source = manager.addSubtask(root.getId(), "source", "");
        Task leaf = manager.addSubtask(source.getId(), "leaf", "");
        Task target = manager.addSubtask(root.getId(), "target", "");

        GuiTestSupport.assertTrue(manager.moveTask(source.getId(), target.getId()), "同一项目内移动应成功");
        GuiTestSupport.assertEquals(target.getId(), manager.getTask(source.getId()).getParentTaskId(), "移动后父 ID 应更新");
        GuiTestSupport.assertEquals(source.getId(), manager.getTask(leaf.getId()).getParentTaskId(), "后代父关系应保持不变");
        GuiTestSupport.assertEquals(3, manager.getTaskDepth(leaf.getId()), "移动后后代深度应重新计算");
    }

    /**
     * 验证重复 ID 不会覆盖原任务，也不会破坏原任务的子任务关系。
     */
    private static void shouldRejectDuplicateTaskIdWithoutOverwritingExistingTask() {
        TaskManager manager = new TaskManager();
        Task parent = createTask("parent", "project-a", false, null);
        Task child = createTask("child", "project-a", false, parent.getId());
        GuiTestSupport.assertTrue(manager.addTask(parent), "首次添加父任务应成功");
        GuiTestSupport.assertTrue(manager.addTask(child), "首次添加子任务应成功");

        Task duplicate = new Task("duplicate", "");
        duplicate.setId(parent.getId());
        GuiTestSupport.assertFalse(manager.addTask(duplicate), "重复 ID 的新任务应被拒绝");
        GuiTestSupport.assertEquals(2, manager.getAllTasks().size(), "重复 ID 不应增加任务数量");
        GuiTestSupport.assertEquals("parent", manager.getTask(parent.getId()).getTitle(), "重复 ID 不应覆盖原任务");
        GuiTestSupport.assertEquals(parent.getId(), manager.getTask(child.getId()).getParentTaskId(), "原任务子任务关系应保持不变");
        GuiTestSupport.assertEquals(1, manager.getDirectChildren(parent.getId()).size(), "原任务仍应只有一个子任务");
        GuiTestSupport.assertTrue(manager.addTask(parent), "同一对象重复添加应保持幂等成功");
    }

    /**
     * 验证无效父 ID 不会让任务从树中消失。
     */
    private static void shouldIgnoreOrphanParentIdWhenBuildingIndex() {
        TaskManager manager = new TaskManager();
        Task orphan = new Task("orphan", "");
        orphan.setId("orphan");
        orphan.setProjectId("project-a");
        orphan.setParentTaskId("missing-parent");
        manager.addTask(orphan);

        TaskTreeIndex index = manager.getTreeIndex();
        GuiTestSupport.assertEquals(1, index.size(), "无效父 ID 不应导致任务丢失");
        GuiTestSupport.assertTrue(index.isRoot("orphan"), "无效父 ID 应按根任务处理");
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
