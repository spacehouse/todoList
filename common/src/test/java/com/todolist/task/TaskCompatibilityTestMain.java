package com.todolist.task;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Path;
import java.util.List;

/**
 * TaskCompatibilityTestMain 覆盖子任务 Phase 1 的 Task/NBT 兼容与展平回归。
 */
public final class TaskCompatibilityTestMain {
    private static final int NBT_COMPOUND_TYPE = 10;

    /**
     * 工具类不需要实例化。
     */
    private TaskCompatibilityTestMain() {
    }

    /**
     * 执行 Task/NBT 子任务兼容测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase(
                "TaskCompatibilityTestMain.shouldRoundTripSubtaskFieldsWithNbt",
                TaskCompatibilityTestMain::shouldRoundTripSubtaskFieldsWithNbt
        );
        GuiTestSupport.runTestCase(
                "TaskCompatibilityTestMain.shouldNormalizeLegacySingleLevelSubtasks",
                TaskCompatibilityTestMain::shouldNormalizeLegacySingleLevelSubtasks
        );
        GuiTestSupport.runTestCase(
                "TaskCompatibilityTestMain.shouldRejectNestedLegacySubtasksBeyondOneLevel",
                TaskCompatibilityTestMain::shouldRejectNestedLegacySubtasksBeyondOneLevel
        );
        GuiTestSupport.runTestCase(
                "TaskCompatibilityTestMain.shouldFlattenLegacySubtasksWhenLoadingTaskStorage",
                TaskCompatibilityTestMain::shouldFlattenLegacySubtasksWhenLoadingTaskStorage
        );
    }

    /**
     * 验证新字段可通过 NBT 往返保留。
     */
    private static void shouldRoundTripSubtaskFieldsWithNbt() {
        GuiTestSupport.resetState();
        Task subtask = new Task("child", "phase1");
        subtask.setId("child-1");
        subtask.setParentTaskId("parent-1");
        subtask.setSubtaskSortOrder(3L);
        subtask.setProjectId("project-a");

        Task restored = Task.fromNbt(subtask.toNbt());

        GuiTestSupport.assertTrue(restored.isSubtask(), "带 parentTaskId 的任务应识别为子任务");
        GuiTestSupport.assertEquals("parent-1", restored.getParentTaskId(), "NBT 往返后应保留父任务 ID");
        GuiTestSupport.assertEquals(3L, restored.getSubtaskSortOrder(), "NBT 往返后应保留子任务排序号");
        GuiTestSupport.assertTrue(restored.getSubtasks().isEmpty(), "新格式子任务不应附带旧 subtasks 结构");
    }

    /**
     * 验证一层旧 subtasks 可被展平成平铺任务列表。
     */
    private static void shouldNormalizeLegacySingleLevelSubtasks() {
        GuiTestSupport.resetState();
        Task parent = new Task("parent", "legacy");
        parent.setId("parent-1");
        parent.setProjectId("project-a");
        Task childA = new Task("child-a", "");
        childA.setId("child-a");
        Task childB = new Task("child-b", "");
        childB.setId("child-b");
        parent.addSubtask(childA);
        parent.addSubtask(childB);

        List<Task> normalized = TaskCompatibilityAdapter.normalizeLoadedTasks(List.of(parent));

        GuiTestSupport.assertEquals(3, normalized.size(), "一层旧 subtasks 应展平成父任务加两个子任务");
        Task normalizedParent = normalized.get(0);
        Task normalizedChildA = normalized.get(1);
        Task normalizedChildB = normalized.get(2);
        GuiTestSupport.assertTrue(normalizedParent.isTopLevelTask(), "父任务仍应保持顶层任务");
        GuiTestSupport.assertTrue(normalizedParent.getSubtasks().isEmpty(), "展平后的父任务不应再保留旧 subtasks");
        GuiTestSupport.assertEquals("parent-1", normalizedChildA.getParentTaskId(), "子任务 A 应指向父任务");
        GuiTestSupport.assertEquals("parent-1", normalizedChildB.getParentTaskId(), "子任务 B 应指向父任务");
        GuiTestSupport.assertEquals(0L, normalizedChildA.getSubtaskSortOrder(), "子任务 A 顺序应从 0 开始");
        GuiTestSupport.assertEquals(1L, normalizedChildB.getSubtaskSortOrder(), "子任务 B 顺序应递增");
        GuiTestSupport.assertEquals("project-a", normalizedChildA.getProjectId(), "子任务 A 应继承父任务项目");
        GuiTestSupport.assertEquals("project-a", normalizedChildB.getProjectId(), "子任务 B 应继承父任务项目");
    }

    /**
     * 验证两层及以上旧 subtasks 会被明确阻断。
     */
    private static void shouldRejectNestedLegacySubtasksBeyondOneLevel() {
        GuiTestSupport.resetState();
        Task parent = new Task("parent", "");
        parent.setId("parent-1");
        Task child = new Task("child", "");
        child.setId("child-1");
        child.addSubtask(new Task("grandchild", ""));
        parent.addSubtask(child);

        boolean failed = false;
        try {
            TaskCompatibilityAdapter.normalizeLoadedTasks(List.of(parent));
        } catch (IllegalStateException exception) {
            failed = exception.getMessage() != null && exception.getMessage().contains("两层");
        }
        GuiTestSupport.assertTrue(failed, "两层旧 subtasks 应被明确阻断");
    }

    /**
     * 验证 TaskStorage 读取旧 NBT 文件时会先展平为平铺结构。
     */
    private static void shouldFlattenLegacySubtasksWhenLoadingTaskStorage() {
        GuiTestSupport.resetState();
        try {
            TaskStorage storage = new TaskStorage();
            Path taskFile = storage.getDataDirectoryPath().resolve("moddata.dat");
            writeLegacyTaskFile(taskFile);

            List<Task> loaded = storage.loadTasks();

            GuiTestSupport.assertEquals(2, loaded.size(), "读取旧 NBT 后应展平成父任务与一个子任务");
            GuiTestSupport.assertTrue(loaded.get(0).isTopLevelTask(), "首条任务应为顶层父任务");
            GuiTestSupport.assertEquals("legacy-parent", loaded.get(1).getParentTaskId(), "子任务应在读取后绑定父任务");
            GuiTestSupport.assertEquals(0L, loaded.get(1).getSubtaskSortOrder(), "单个子任务顺序应为 0");
        } catch (Exception exception) {
            throw new IllegalStateException("验证旧 NBT 展平读取时发生异常", exception);
        }
    }

    /**
     * 写入一份旧 subtasks 嵌套格式的任务文件。
     *
     * @param taskFile 目标任务文件
     * @throws Exception 写入失败时抛出
     */
    private static void writeLegacyTaskFile(Path taskFile) throws Exception {
        CompoundTag root = new CompoundTag();
        root.putLong("lastSaved", System.currentTimeMillis());
        root.putInt("version", 1);

        CompoundTag child = createLegacyTaskNbt("legacy-child", "child", "project-a");
        CompoundTag parent = createLegacyTaskNbt("legacy-parent", "parent", "project-a");
        ListTag subtasks = new ListTag();
        subtasks.add(child);
        parent.put("subtasks", subtasks);

        ListTag tasks = new ListTag();
        tasks.add(parent);
        root.put("tasks", tasks);
        NbtIo.write(root, taskFile.toFile());
    }

    /**
     * 创建旧格式任务 NBT。
     *
     * @param id 任务 ID
     * @param title 标题
     * @param projectId 项目 ID
     * @return 旧格式任务 NBT
     */
    private static CompoundTag createLegacyTaskNbt(String id, String title, String projectId) {
        CompoundTag task = new CompoundTag();
        task.putString("id", id);
        task.putString("title", title);
        task.putString("description", "");
        task.putBoolean("completed", false);
        task.putString("priority", Task.Priority.MEDIUM.name());
        task.putLong("createdAt", 1L);
        task.putString("scope", Task.Scope.PERSONAL.name());
        task.putString("projectId", projectId);
        task.putLong("subtaskSortOrder", 0L);
        task.put("tags", new ListTag());
        task.put("subtasks", new ListTag());
        return task;
    }
}
