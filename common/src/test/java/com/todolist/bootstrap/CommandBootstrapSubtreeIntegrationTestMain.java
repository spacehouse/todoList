package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestMinecraftServer;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestServerPlayer;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.task.Task;
import net.minecraft.commands.CommandSourceStack;

/**
 * 命令系统子树语义集成测试入口。
 *
 * <p>验证 {@code task done}、{@code task remove}、{@code task donep} 与
 * {@code task removep} 在多层嵌套任务上复用任务管理器，而不是只修改单个任务对象。</p>
 */
public final class CommandBootstrapSubtreeIntegrationTestMain {

    private CommandBootstrapSubtreeIntegrationTestMain() {
    }

    /**
     * 程序入口，初始化命令测试环境后执行本类全部 {@code should*} 用例。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 任一用例失败时向上抛出
     */
    public static void main(String[] args) throws Exception {
        CommandBootstrapIntegrationTestMain.bootstrapEnvironment();
        CommandTestSupport.runAllShouldCases(CommandBootstrapSubtreeIntegrationTestMain.class);
    }

    /**
     * 验证个人任务 {@code task done} 会级联完成整棵子树，且不影响无关任务。
     */
    private static void shouldCompleteDeepSubtreeThroughPersonalDoneCommand() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000901", "personal-subtree-done", false);
        Task root = CommandBootstrapIntegrationTestMain.createPersonalTask(
                "Subtree Done Root", false, Task.Priority.MEDIUM, null);
        Task child = createChild("Subtree Done Child", root, 0L);
        Task grandchild = createChild("Subtree Done Grandchild", child, 0L);
        Task unrelated = CommandBootstrapIntegrationTestMain.createPersonalTask(
                "Subtree Done Unrelated", false, Task.Priority.MEDIUM, null);
        CommandBootstrapIntegrationTestMain.savePersonalTasks(player, root, child, grandchild, unrelated);

        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        int result = dispatcher.execute(
                "todo task done " + root.getId(),
                CommandBootstrapIntegrationTestMain.createSource(0, player));

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "task done 对多层子树应返回成功");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE,
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, "Subtree Done Root").isCompleted(),
                "task done 应完成根任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE,
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, "Subtree Done Child").isCompleted(),
                "task done 应完成子任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE,
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, "Subtree Done Grandchild").isCompleted(),
                "task done 应完成孙任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.FALSE,
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, "Subtree Done Unrelated").isCompleted(),
                "task done 不应改动无关任务");

        CapturingCommandSourceStack repeatSource = CommandBootstrapIntegrationTestMain.createSource(0, player);
        int repeatResult = dispatcher.execute("todo task done " + root.getId(), repeatSource);
        CommandBootstrapIntegrationTestMain.assertEquals(1, repeatResult, "重复执行 task done 应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                repeatSource.getSuccessMessages(),
                "command.todolist.task.done.already_completed",
                "重复执行 task done 应返回已完成提示");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE,
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, "Subtree Done Root").isCompleted(),
                "重复执行 task done 不应取消根任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE,
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, "Subtree Done Child").isCompleted(),
                "重复执行 task done 不应取消子任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE,
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitle(player, "Subtree Done Grandchild").isCompleted(),
                "重复执行 task done 不应取消孙任务");
    }

    /**
     * 验证个人任务 {@code task remove} 会删除整棵子树，且保留无关任务。
     */
    private static void shouldRemoveDeepSubtreeThroughPersonalRemoveCommand() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000902", "personal-subtree-remove", false);
        Task root = CommandBootstrapIntegrationTestMain.createPersonalTask(
                "Subtree Remove Root", false, Task.Priority.MEDIUM, null);
        Task child = createChild("Subtree Remove Child", root, 0L);
        Task grandchild = createChild("Subtree Remove Grandchild", child, 0L);
        Task unrelated = CommandBootstrapIntegrationTestMain.createPersonalTask(
                "Subtree Remove Unrelated", false, Task.Priority.MEDIUM, null);
        CommandBootstrapIntegrationTestMain.savePersonalTasks(player, root, child, grandchild, unrelated);

        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        int result = dispatcher.execute(
                "todo task remove " + root.getId(),
                CommandBootstrapIntegrationTestMain.createSource(0, player));

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "task remove 对多层子树应返回成功");
        CommandBootstrapIntegrationTestMain.assertNull(
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitleOrNull(player, "Subtree Remove Root"),
                "task remove 应删除根任务");
        CommandBootstrapIntegrationTestMain.assertNull(
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitleOrNull(player, "Subtree Remove Child"),
                "task remove 应删除子任务");
        CommandBootstrapIntegrationTestMain.assertNull(
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitleOrNull(player, "Subtree Remove Grandchild"),
                "task remove 应删除孙任务");
        CommandBootstrapIntegrationTestMain.assertNotNull(
                CommandBootstrapIntegrationTestMain.findPersonalTaskByTitleOrNull(player, "Subtree Remove Unrelated"),
                "task remove 不应误删无关任务");
    }

    /**
     * 验证团队项目 {@code task donep} 与 {@code task removep} 同样遵循子树语义。
     */
    private static void shouldApplyProjectCommandsToWholeSubtree() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer manager = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000903", "team-subtree-manager", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(manager);
        Project project = CommandBootstrapIntegrationTestMain.addTeamProject(
                manager, "team-subtree-commands", "Subtree Commands");

        Task doneRoot = CommandBootstrapIntegrationTestMain.createTeamTask(project.getId(), "Project Done Root");
        Task doneChild = createChild("Project Done Child", doneRoot, 0L);
        Task doneGrandchild = createChild("Project Done Grandchild", doneChild, 0L);
        Task removeRoot = CommandBootstrapIntegrationTestMain.createTeamTask(project.getId(), "Project Remove Root");
        Task removeChild = createChild("Project Remove Child", removeRoot, 0L);
        Task unrelated = CommandBootstrapIntegrationTestMain.createTeamTask(project.getId(), "Project Unrelated");
        CommandBootstrapIntegrationTestMain.saveTeamTasks(
                doneRoot, doneChild, doneGrandchild, removeRoot, removeChild, unrelated);

        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        int doneResult = dispatcher.execute(
                "todo task donep " + project.getId() + " " + doneRoot.getId(),
                CommandBootstrapIntegrationTestMain.createSource(0, manager, server));
        CommandBootstrapIntegrationTestMain.assertEquals(1, doneResult, "donep 对多层子树应返回成功");
        Task completedRoot = CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Done Root");
        Task completedChild = CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Done Child");
        Task completedGrandchild = CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Done Grandchild");
        CommandBootstrapIntegrationTestMain.assertNotNull(completedRoot, "donep 后应保留根任务");
        CommandBootstrapIntegrationTestMain.assertNotNull(completedChild, "donep 后应保留子任务");
        CommandBootstrapIntegrationTestMain.assertNotNull(completedGrandchild, "donep 后应保留孙任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE, completedRoot.isCompleted(), "donep 应完成根任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE, completedChild.isCompleted(), "donep 应完成子任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE, completedGrandchild.isCompleted(), "donep 应完成孙任务");

        CapturingCommandSourceStack repeatTeamSource = CommandBootstrapIntegrationTestMain.createSource(0, manager, server);
        int repeatDoneResult = dispatcher.execute(
                "todo task donep " + project.getId() + " " + doneRoot.getId(), repeatTeamSource);
        CommandBootstrapIntegrationTestMain.assertEquals(1, repeatDoneResult, "重复执行 donep 应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                repeatTeamSource.getSuccessMessages(),
                "command.todolist.task.done.already_completed",
                "重复执行 donep 应返回已完成提示");
        Task repeatedRoot = CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Done Root");
        Task repeatedChild = CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Done Child");
        Task repeatedGrandchild = CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Done Grandchild");
        CommandBootstrapIntegrationTestMain.assertNotNull(repeatedRoot, "重复 donep 后应保留根任务");
        CommandBootstrapIntegrationTestMain.assertNotNull(repeatedChild, "重复 donep 后应保留子任务");
        CommandBootstrapIntegrationTestMain.assertNotNull(repeatedGrandchild, "重复 donep 后应保留孙任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE, repeatedRoot.isCompleted(), "重复 donep 不应取消根任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE, repeatedChild.isCompleted(), "重复 donep 不应取消子任务");
        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE, repeatedGrandchild.isCompleted(), "重复 donep 不应取消孙任务");

        int removeResult = dispatcher.execute(
                "todo task removep " + project.getId() + " " + removeRoot.getId(),
                CommandBootstrapIntegrationTestMain.createSource(0, manager, server));
        CommandBootstrapIntegrationTestMain.assertEquals(1, removeResult, "removep 对多层子树应返回成功");
        CommandBootstrapIntegrationTestMain.assertNull(
                CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Remove Root"),
                "removep 应删除根任务");
        CommandBootstrapIntegrationTestMain.assertNull(
                CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Remove Child"),
                "removep 应删除子任务");
        CommandBootstrapIntegrationTestMain.assertNotNull(
                CommandBootstrapIntegrationTestMain.findTeamTaskByTitleOrNull("Project Unrelated"),
                "removep 不应误删无关任务");
    }

    private static Task createChild(String title, Task parent, long sortOrder) {
        Task child = CommandBootstrapIntegrationTestMain.createPersonalTask(
                title, false, Task.Priority.MEDIUM, parent.getProjectId());
        child.setScope(parent.getScope());
        child.setParentTaskId(parent.getId());
        child.setSubtaskSortOrder(sortOrder);
        return child;
    }
}
