package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.project.ProjectSaveDebouncer;
import com.todolist.project.ProjectStorage;
import com.todolist.storage.H2StorageBootstrap;
import com.todolist.storage.H2TcpServerManager;
import com.todolist.task.Task;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.Unpooled;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 命令系统真实集成自检入口，使用 Brigadier 分发器执行最小可行命令链路。
 */
public final class CommandBootstrapIntegrationTestMain {
    private static final Unsafe UNSAFE = loadUnsafe();
    private static final Path TEST_GAME_DIR = createTestGameDir();
    private static int storageNamespaceCounter;

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandBootstrapIntegrationTestMain() {
    }

    /**
     * 程序入口，执行全部离线命令集成测试。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当任一测试失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        bootstrapEnvironment();
        try {
            runCase("CommandBootstrapIntegrationTestMain.shouldRegisterTodoAlias", CommandBootstrapIntegrationTestMain::shouldRegisterTodoAlias);
            runCase("CommandBootstrapIntegrationTestMain.shouldExecuteHelpThroughAlias", CommandBootstrapIntegrationTestMain::shouldExecuteHelpThroughAlias);
            runCase("CommandBootstrapIntegrationTestMain.shouldExecuteDirectHelpCommandSuccessfully", CommandBootstrapIntegrationTestMain::shouldExecuteDirectHelpCommandSuccessfully);
            runCase("CommandBootstrapIntegrationTestMain.shouldAddPersonalTaskSuccessfully", CommandBootstrapIntegrationTestMain::shouldAddPersonalTaskSuccessfully);
            runCase("CommandBootstrapIntegrationTestMain.shouldListPersonalTasksWithFilters", CommandBootstrapIntegrationTestMain::shouldListPersonalTasksWithFilters);
            runCase("CommandBootstrapIntegrationTestMain.shouldCompletePersonalTaskSuccessfully", CommandBootstrapIntegrationTestMain::shouldCompletePersonalTaskSuccessfully);
            runCase("CommandBootstrapIntegrationTestMain.shouldRejectMissingPersonalTaskWhenCompleting", CommandBootstrapIntegrationTestMain::shouldRejectMissingPersonalTaskWhenCompleting);
            runCase("CommandBootstrapIntegrationTestMain.shouldReturnAlreadyCompletedForCompletedPersonalTaskSuccessfully", CommandBootstrapIntegrationTestMain::shouldReturnAlreadyCompletedForCompletedPersonalTaskSuccessfully);
            runCase("CommandBootstrapIntegrationTestMain.shouldRemovePersonalTaskSuccessfully", CommandBootstrapIntegrationTestMain::shouldRemovePersonalTaskSuccessfully);
            runCase("CommandBootstrapIntegrationTestMain.shouldRejectMissingPersonalTaskWhenRemoving", CommandBootstrapIntegrationTestMain::shouldRejectMissingPersonalTaskWhenRemoving);
            runCase("CommandBootstrapIntegrationTestMain.shouldListCompletedPersonalTasksSuccessfully", CommandBootstrapIntegrationTestMain::shouldListCompletedPersonalTasksSuccessfully);
            runCase("CommandBootstrapIntegrationTestMain.shouldPaginatePersonalTaskListWithMoreAndPrevSuccessfully", CommandBootstrapIntegrationTestMain::shouldPaginatePersonalTaskListWithMoreAndPrevSuccessfully);
            runCase("CommandBootstrapIntegrationTestMain.shouldNotEchoTeamReplacePacketToSender", CommandBootstrapIntegrationTestMain::shouldNotEchoTeamReplacePacketToSender);
            runCase("CommandBootstrapIntegrationTestMain.shouldMergeTeamReplacePacketWithServerChanges", CommandBootstrapIntegrationTestMain::shouldMergeTeamReplacePacketWithServerChanges);
            runCase("CommandBootstrapIntegrationTestMain.shouldPreserveServerEditedTaskWhenClientDeletesStaleBase", CommandBootstrapIntegrationTestMain::shouldPreserveServerEditedTaskWhenClientDeletesStaleBase);
            runCase("CommandBootstrapIntegrationTestMain.shouldSendMergedTeamReplaceResultBackToSender", CommandBootstrapIntegrationTestMain::shouldSendMergedTeamReplaceResultBackToSender);
            runCase("CommandBootstrapIntegrationTestMain.shouldFlushProjectSaveAfterBackgroundFailure", CommandBootstrapIntegrationTestMain::shouldFlushProjectSaveAfterBackgroundFailure);
            runCase("CommandBootstrapIntegrationTestMain.shouldPreserveCommandAccessModeAfterExternalConfigEdit", CommandBootstrapIntegrationTestMain::shouldPreserveCommandAccessModeAfterExternalConfigEdit);
        } finally {
            H2TcpServerManager.stop();
            H2StorageBootstrap.resetAllForTests();
            TaskPackets.setServerPacketSender(null);
        }
    }

    /**
     * 初始化测试运行环境，确保配置路径与全局单例可在离线环境下使用。
     */
    static void bootstrapEnvironment() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
        DataPathProvider.setGameDirSupplier(() -> TEST_GAME_DIR);
        DataPathProvider.resetStorageNamespace();
        TodoListCommon.init();
        ModConfig.getInstance().setCommandAccessMode(ModConfig.CommandAccessMode.FULL);
        ProjectPackets.setServerPacketSender((player, channelId, buf) -> {
        });
        TaskPackets.setServerPacketSender((player, channelId, buf) -> {
        });
    }

    /**
     * 校验根命令与别名均已注册到真实 Brigadier 分发器。
     */
    private static void shouldRegisterTodoAlias() {
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        assertNotNull(dispatcher.getRoot().getChild("todo"), "todo 根命令未注册");
        assertNotNull(dispatcher.getRoot().getChild("todolist"), "todolist 别名未注册");
    }

    /**
     * 校验别名命令可以真实执行并输出帮助信息。
     */
    private static void shouldExecuteHelpThroughAlias() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, null);
        int result = dispatcher.execute("todolist help", source);
        assertEquals(1, result, "别名 help 命令执行结果错误");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.help.title", "help 命令未输出标题");
    }

    /**
     * 校验个人任务新增命令会写入标题、描述、标签和当前激活项目。
     */
    /**
     * 验证直接执行 todo help 也会输出帮助信息。
     */
    private static void shouldExecuteDirectHelpCommandSuccessfully() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, null);
        int result = dispatcher.execute("todo help", source);
        assertEquals(1, result, "直接 help 命令执行结果错误");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.help.title", "直接 help 命令未输出标题");
    }

    private static void shouldAddPersonalTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000032", "personal-add-user", false);
        addOwnedPersonalProject(player, "focus-project", "Focus Project");
        ProjectPackets.setActiveProjectId(player, "focus-project");
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task add \"Write docs\" \"Prepare release notes\" docs,release", createSource(0, player));
        assertEquals(1, result, "task add 成功时应返回成功");

        Task savedTask = findPersonalTaskByTitle(player, "Write docs");
        assertEquals("Prepare release notes", savedTask.getDescription(), "task add 未写入描述");
        assertEquals("focus-project", savedTask.getProjectId(), "task add 未绑定当前激活项目");
        assertEquals(Boolean.TRUE, savedTask.getTags().contains("docs"), "task add 未写入 docs 标签");
        assertEquals(Boolean.TRUE, savedTask.getTags().contains("release"), "task add 未写入 release 标签");
    }

    /**
     * 校验个人任务列表命令支持按状态、优先级和文本进行筛选。
     */
    private static void shouldListPersonalTasksWithFilters() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000033", "personal-list-user", false);
        savePersonalTasks(
                player,
                createPersonalTask("Release report", true, Task.Priority.HIGH, null),
                createPersonalTask("Draft changelog", false, Task.Priority.MEDIUM, null),
                createPersonalTask("Fix typo", true, Task.Priority.LOW, null)
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task list completed high report", source);
        assertEquals(1, result, "task list 过滤成功时应返回成功");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.task.list.summary", "task list 未输出汇总消息");
        assertContainsText(source.getSuccessMessages(), "Release report", "task list 未输出匹配任务标题");
        assertNotContainsText(source.getSuccessMessages(), "Draft changelog", "task list 不应输出未匹配的未完成任务");
        assertNotContainsText(source.getSuccessMessages(), "Fix typo", "task list 不应输出未匹配的低优先级任务");
    }

    /**
     * 按既定顺序执行单个集成测试用例，并输出具名 case 日志。
     *
     * @param caseName 测试用例名称
     * @param action 测试用例执行逻辑
     * @throws Exception 当测试用例执行失败时向上抛出异常
     */
    private static void runCase(String caseName, CommandTestSupport.ThrowingRunnable action) throws Exception {
        CommandTestSupport.runTestCase(caseName, action);
    }

    /**
     * 校验 personal task done 会真实把个人任务标记为完成。
     */
    private static void shouldCompletePersonalTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000135", "personal-done-user", false);
        Task task = createPersonalTask("Done by command", false, Task.Priority.MEDIUM, null);
        savePersonalTasks(player, task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task done " + task.getId(), createSource(0, player));
        assertEquals(1, result, "task done 成功时应返回成功");
        assertEquals(Boolean.TRUE, findPersonalTaskByTitle(player, "Done by command").isCompleted(), "task done 未将任务标记为完成");
    }

    /**
     * 校验 personal task remove 会真实删除个人任务。
     */
    /**
     * 验证 personal task done 对不存在任务会返回 not_found。
     */
    private static void shouldRejectMissingPersonalTaskWhenCompleting() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000413", "personal-done-missing-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task done missing-personal-task", source);
        assertEquals(0, result, "完成不存在的个人任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.done.not_found", "个人 task done 不存在任务的错误键不正确");
    }

    /**
     * 验证 personal task done 对已完成任务会返回 already_completed。
     */
    private static void shouldReturnAlreadyCompletedForCompletedPersonalTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000414", "personal-done-completed-user", false);
        Task task = createPersonalTask("Already completed personal task", true, Task.Priority.MEDIUM, null);
        savePersonalTasks(player, task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task done " + task.getId(), source);
        assertEquals(1, result, "重复完成已完成个人任务应返回成功提示");
        assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.task.done.already_completed", "个人 task done 已完成提示键不正确");
        assertEquals(Boolean.TRUE, findPersonalTaskByTitle(player, "Already completed personal task").isCompleted(), "已完成个人任务不应被重复执行后改坏状态");
    }

    /**
     * 验证 personal task remove 会真实删除个人任务。
     */
    private static void shouldRemovePersonalTaskSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000236", "personal-remove-user", false);
        Task task = createPersonalTask("Remove by command", false, Task.Priority.MEDIUM, null);
        savePersonalTasks(player, task);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        int result = dispatcher.execute("todo task remove " + task.getId(), createSource(0, player));
        assertEquals(1, result, "task remove 成功时应返回成功");
        assertNull(findPersonalTaskByTitleOrNull(player, "Remove by command"), "task remove 未删除个人任务");
    }

    /**
     * 校验 personal task remove 对不存在任务会返回 not_found。
     */
    private static void shouldRejectMissingPersonalTaskWhenRemoving() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000237", "personal-remove-missing-user", false);
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task remove missing-personal-task", source);
        assertEquals(0, result, "删除不存在的个人任务应返回失败");
        assertContainsMessageKey(source.getFailureMessages(), "command.todolist.task.remove.not_found", "个人 task remove 不存在任务的错误键不正确");
    }

    /**
     * 校验 personal task list completed 只输出已完成任务。
     */
    private static void shouldListCompletedPersonalTasksSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000134", "completed-list-user", false);
        savePersonalTasks(
                player,
                createPersonalTask("Completed report", true, Task.Priority.HIGH, null),
                createPersonalTask("Open draft", false, Task.Priority.MEDIUM, null)
        );
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();
        CapturingCommandSourceStack source = createSource(0, player);

        int result = dispatcher.execute("todo task list completed", source);
        assertEquals(1, result, "task list completed 成功时应返回成功");
        assertContainsText(source.getSuccessMessages(), "Completed report", "task list completed 未输出已完成任务");
        assertNotContainsText(source.getSuccessMessages(), "Open draft", "task list completed 不应输出未完成任务");
    }

    /**
     * 校验个人任务列表超过 10 条时可以通过 more/prev 翻页查看。
     */
    private static void shouldPaginatePersonalTaskListWithMoreAndPrevSuccessfully() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = createPlayer("00000000-0000-0000-0000-000000000036", "personal-page-user", false);
        savePersonalTasks(player, createPagedPersonalTasks("Personal Page Task ", 12, null));
        CommandDispatcher<CommandSourceStack> dispatcher = createDispatcher();

        CapturingCommandSourceStack firstPageSource = createSource(0, player);
        int firstPageResult = dispatcher.execute("todo task list", firstPageSource);
        assertEquals(1, firstPageResult, "task list 第一页查询应返回成功");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.summary", "task list 第一页未输出汇总消息");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task list 第一页未输出页码状态");
        assertEquals(10, countMessagesContaining(firstPageSource.getSuccessMessages(), "Personal Page Task "), "task list 第一页应显示 10 条任务");
        assertContainsMessageKey(firstPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "task list 第一页未输出下一页按钮");

        CapturingCommandSourceStack secondPageSource = createSource(0, player);
        int secondPageResult = dispatcher.execute("todo task more", secondPageSource);
        assertEquals(1, secondPageResult, "todo task more 应返回成功");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task more 未输出页码状态");
        assertEquals(2, countMessagesContaining(secondPageSource.getSuccessMessages(), "Personal Page Task "), "task more 第二页应只显示剩余 2 条任务");
        assertContainsMessageKey(secondPageSource.getSuccessMessages(), "command.todolist.task.list.page.prev_button", "task more 第二页未输出上一页按钮");

        CapturingCommandSourceStack previousPageSource = createSource(0, player);
        int previousPageResult = dispatcher.execute("todo task prev", previousPageSource);
        assertEquals(1, previousPageResult, "todo task prev 应返回成功");
        assertContainsMessageKey(previousPageSource.getSuccessMessages(), "command.todolist.task.list.page.status", "task prev 未输出页码状态");
        assertEquals(10, countMessagesContaining(previousPageSource.getSuccessMessages(), "Personal Page Task "), "task prev 返回后应再次显示 10 条任务");
        assertContainsMessageKey(previousPageSource.getSuccessMessages(), "command.todolist.task.list.page.next_button", "task prev 返回第一页后未输出下一页按钮");
    }

    /**
     * 验证团队整表替换保存后不会把同一份快照回显给发起者，避免客户端连续操作时旧回包覆盖本地新状态。
     *
     * @throws Exception 读取服务端存储失败时抛出
     */
    private static void shouldNotEchoTeamReplacePacketToSender() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer sender = createPlayer("00000000-0000-0000-0000-000000000501", "team-replace-sender", false);
        TestServerPlayer peer = createPlayer("00000000-0000-0000-0000-000000000502", "team-replace-peer", false);
        TestMinecraftServer server = createServer(sender, peer);
        List<String> targetPlayerNames = new ArrayList<>();
        TaskPackets.setServerPacketSender((target, channelId, buf) -> {
            if (TaskPackets.TEAM_SYNC_TASKS_ID.equals(channelId) && target != null) {
                targetPlayerNames.add(target.getName().getString());
            }
        });

        Task task = new Task("No Echo Team Task", "");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeTaskList(buf, List.of(task));

        TaskPackets.onTeamReplaceTasksPacket(server, sender, buf);

        assertListEquals(List.of("team-replace-peer"), targetPlayerNames, "团队整表替换不应回显给发起者");
        assertEquals("No Echo Team Task", TodoListCommon.getTaskStorage().loadTeamTasks().get(0).getTitle(), "团队整表替换仍应保存到服务端存储");
        TaskPackets.setServerPacketSender(null);
    }

    /**
     * 验证新团队替换包会按客户端基线与服务端当前内容合并，避免旧快照覆盖其他玩家改动。
     *
     * @throws Exception 读写服务端存储失败时抛出
     */
    private static void shouldMergeTeamReplacePacketWithServerChanges() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer sender = createPlayer("00000000-0000-0000-0000-000000000511", "team-merge-sender", false);
        TestServerPlayer peer = createPlayer("00000000-0000-0000-0000-000000000512", "team-merge-peer", false);
        TestMinecraftServer server = createServer(sender, peer);

        Task locallyEditedBase = new Task("Client Edited Base", "");
        Task remotelyEditedBase = new Task("Remote Edited Base", "");
        Task deletedByClient = new Task("Deleted By Client", "");
        List<Task> clientBase = List.of(copyTask(locallyEditedBase), copyTask(remotelyEditedBase), copyTask(deletedByClient));

        Task remoteCurrent = copyTask(remotelyEditedBase);
        remoteCurrent.setCompleted(true);
        Task remoteCreated = new Task("Remote Created", "");
        TodoListCommon.getTaskStorage().saveTeamTasks(List.of(locallyEditedBase, remoteCurrent, deletedByClient, remoteCreated));

        Task localEdited = copyTask(locallyEditedBase);
        localEdited.setCompleted(true);
        Task unchangedRemoteFromBase = copyTask(remotelyEditedBase);
        Task localCreated = new Task("Client Created", "");
        List<Task> submitted = List.of(localEdited, unchangedRemoteFromBase, localCreated);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeTaskList(buf, submitted);
        TaskPackets.writeTaskList(buf, clientBase);

        TaskPackets.onTeamReplaceTasksPacket(server, sender, buf);

        assertEquals(Boolean.TRUE, loadTeamTaskByTitle("Client Edited Base").isCompleted(), "客户端改动任务应保存");
        assertEquals(Boolean.TRUE, loadTeamTaskByTitle("Remote Edited Base").isCompleted(), "服务端期间改动不应被旧客户端快照覆盖");
        assertNotNull(findTeamTaskByTitleOrNull("Remote Created"), "服务端期间新增任务不应丢失");
        assertNotNull(findTeamTaskByTitleOrNull("Client Created"), "客户端新增任务应保存");
        assertNull(findTeamTaskByTitleOrNull("Deleted By Client"), "客户端删除的基线任务应被删除");
    }

    /**
     * 验证客户端删除旧基线任务时，如果服务端已并发编辑同一任务，则应保留服务端版本。
     *
     * @throws Exception 读写服务端存储失败时抛出
     */
    private static void shouldPreserveServerEditedTaskWhenClientDeletesStaleBase() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer sender = createPlayer("00000000-0000-0000-0000-000000000513", "team-delete-conflict-sender", false);
        TestServerPlayer peer = createPlayer("00000000-0000-0000-0000-000000000514", "team-delete-conflict-peer", false);
        TestMinecraftServer server = createServer(sender, peer);

        Task baseTask = new Task("Delete Conflict Base", "");
        Task serverEditedTask = copyTask(baseTask);
        serverEditedTask.setCompleted(true);
        TodoListCommon.getTaskStorage().saveTeamTasks(List.of(serverEditedTask));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeTaskList(buf, List.of());
        TaskPackets.writeTaskList(buf, List.of(copyTask(baseTask)));

        TaskPackets.onTeamReplaceTasksPacket(server, sender, buf);

        Task preservedTask = findTeamTaskByTitleOrNull("Delete Conflict Base");
        assertNotNull(preservedTask, "客户端删除旧基线不应删除已被服务端并发编辑的任务");
        assertEquals(Boolean.TRUE, preservedTask.isCompleted(), "保留下来的任务应保持服务端并发编辑后的状态");
    }

    /**
     * 验证服务端合并团队保存后，会把合并后的权威任务列表同步回发起者。
     *
     * @throws Exception 读写服务端存储失败时抛出
     */
    private static void shouldSendMergedTeamReplaceResultBackToSender() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer sender = createPlayer("00000000-0000-0000-0000-000000000521", "team-merge-authoritative-sender", false);
        TestServerPlayer peer = createPlayer("00000000-0000-0000-0000-000000000522", "team-merge-authoritative-peer", false);
        TestMinecraftServer server = createServer(sender, peer);
        Map<String, List<String>> syncedTitlesByPlayer = new LinkedHashMap<>();
        TaskPackets.setServerPacketSender((target, channelId, buf) -> {
            if (TaskPackets.TEAM_SYNC_TASKS_ID.equals(channelId) && target != null) {
                syncedTitlesByPlayer.put(target.getName().getString(), TaskPackets.readTaskList(buf).stream().map(Task::getTitle).toList());
            }
        });

        Task baseTask = new Task("Authoritative Base", "");
        Task remoteCreated = new Task("Authoritative Remote", "");
        TodoListCommon.getTaskStorage().saveTeamTasks(List.of(baseTask, remoteCreated));
        Task editedByClient = copyTask(baseTask);
        editedByClient.setCompleted(true);
        Task clientCreated = new Task("Authoritative Client", "");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPackets.writeTaskList(buf, List.of(editedByClient, clientCreated));
        TaskPackets.writeTaskList(buf, List.of(copyTask(baseTask)));

        TaskPackets.onTeamReplaceTasksPacket(server, sender, buf);

        List<String> expectedTitles = List.of("Authoritative Base", "Authoritative Remote", "Authoritative Client");
        assertListEquals(expectedTitles, syncedTitlesByPlayer.get("team-merge-authoritative-sender"), "发起者应收到合并后的权威团队任务列表");
        assertListEquals(expectedTitles, syncedTitlesByPlayer.get("team-merge-authoritative-peer"), "其他玩家也应收到合并后的权威团队任务列表");
        TaskPackets.setServerPacketSender(null);
    }

    /**
     * 验证后台项目保存失败并恢复 dirty 后，同一次 flushNow 会继续补救保存。
     *
     * @throws Exception 测试存储替换或等待失败时抛出
     */
    private static void shouldFlushProjectSaveAfterBackgroundFailure() throws Exception {
        resetState(ModConfig.CommandAccessMode.FULL);
        TestMinecraftServer server = createServer();
        Project project = new Project("Debounce Retry Project", Project.Scope.PERSONAL, null);
        project.setId("debounce-retry-project");
        TodoListCommon.getProjectManager().addProject(project);
        FailingOnceProjectStorage storage = new FailingOnceProjectStorage();
        replaceProjectStorageForTest(storage);

        ProjectSaveDebouncer.requestSave(server, Project.Scope.PERSONAL);
        if (!storage.awaitFirstSaveStarted()) {
            throw new AssertionError("后台项目保存应在超时前启动");
        }
        Thread flushThread = new Thread(() -> ProjectSaveDebouncer.flushNow(server), "project-save-flush-test");
        flushThread.start();
        Thread.sleep(100L);

        storage.releaseFirstSave();
        flushThread.join(5_000L);

        if (flushThread.isAlive()) {
            throw new AssertionError("flushNow 应在后台保存失败后完成补救保存");
        }
        assertEquals(2, storage.getPersonalSaveAttempts(), "flushNow 应在后台失败后立即重试一次个人项目保存");
        assertListEquals(List.of("debounce-retry-project"), storage.getLastPersonalProjectIds(), "重试保存应写入当前个人项目快照");
    }

    /**
     * 重置测试用的全局状态，避免不同场景之间相互污染。
     *
     * @param accessMode 当前场景要使用的命令权限模式
     */
    static void resetState(ModConfig.CommandAccessMode accessMode) {
        storageNamespaceCounter++;
        H2StorageBootstrap.resetAllForTests();
        DataPathProvider.setStorageNamespace("command-test-" + storageNamespaceCounter);
        TodoListCommon.init();
        ModConfig.getInstance().setCommandAccessMode(accessMode);
    }

    /**
     * 创建真实 Brigadier 分发器并注册命令树。
     *
     * @return 已注册命令树的分发器
     */
    static CommandDispatcher<CommandSourceStack> createDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandBootstrap.register(dispatcher, null, null);
        return dispatcher;
    }

    /**
     * 创建带消息捕获能力的命令源，便于断言反馈内容。
     *
     * @param permissionLevel 命令源权限等级
     * @param player 绑定的假玩家，可为 null
     * @return 捕获型命令源
     */
    static CapturingCommandSourceStack createSource(int permissionLevel, ServerPlayer player) {
        return new CapturingCommandSourceStack(permissionLevel, player);
    }

    /**
     * 创建带最小服务端上下文的命令源，供团队任务成功分支复用。
     *
     * @param permissionLevel 命令权限等级
     * @param player 当前执行玩家
     * @param server 最小服务端桩对象
     * @return 捕获型命令源
     */
    static CapturingCommandSourceStack createSource(int permissionLevel, ServerPlayer player, MinecraftServer server) {
        return new CapturingCommandSourceStack(permissionLevel, player, server);
    }

    /**
     * 创建最小假玩家对象，供命令集成测试复用。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @param operator 是否视为 OP
     * @return 已初始化好的假玩家
     */
    static TestServerPlayer createPlayer(String uuid, String name, boolean operator) {
        try {
            TestServerPlayer player = (TestServerPlayer) UNSAFE.allocateInstance(TestServerPlayer.class);
            player.testUuid = UUID.fromString(uuid);
            player.testName = name;
            player.testOperator = operator;
            player.testClientMessages = new ArrayList<>();
            return player;
        } catch (InstantiationException e) {
            throw new IllegalStateException("无法创建测试玩家实例", e);
        }
    }

    /**
     * 向项目管理器注入一个当前玩家可见的个人项目。
     *
     * @param owner 玩家
     * @param projectId 项目 ID
     * @param projectName 项目名称
     */
    static void addOwnedPersonalProject(TestServerPlayer owner, String projectId, String projectName) {
        Project project = new Project(projectName, Project.Scope.PERSONAL, owner.getStringUUID());
        project.setId(projectId);
        TodoListCommon.getProjectManager().addProject(project);
    }

    /**
     * 向项目管理器注入一个可用于成员角色测试的团队项目。
     *
     * @param owner 项目经理
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @param memberUuid 待调整角色的成员 UUID
     */
    static void addTeamProjectForMemberRole(TestServerPlayer owner, String projectId, String projectName, String memberUuid) {
        Project project = addTeamProject(owner, projectId, projectName);
        project.addMember(memberUuid, Project.ProjectRole.MEMBER, "member-user");
    }

    /**
     * 向项目管理器注入一个团队项目，并将 owner 设为项目经理。
     *
     * @param owner 项目经理
     * @param projectId 项目 ID
     * @param projectName 项目名称
     * @return 已加入项目管理器的项目对象
     */
    static Project addTeamProject(TestServerPlayer owner, String projectId, String projectName) {
        Project project = new Project(projectName, Project.Scope.TEAM, owner.getStringUUID());
        project.setId(projectId);
        project.addMember(owner.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, owner.getName().getString());
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 将指定团队任务写入测试用团队任务存储。
     *
     * @param tasks 需要保存的团队任务
     * @throws Exception 保存失败时抛出异常
     */
    static void saveTeamTasks(Task... tasks) throws Exception {
        TodoListCommon.getTaskStorage().saveTeamTasks(List.of(tasks));
    }

    /**
     * 将指定个人任务写入测试玩家的个人任务存储。
     *
     * @param player 测试玩家
     * @param tasks 需要保存的个人任务
     * @throws Exception 保存失败时抛出异常
     */
    static void savePersonalTasks(TestServerPlayer player, Task... tasks) throws Exception {
        TodoListCommon.getTaskStorage().savePlayerTasks(player.getUUID(), List.of(tasks));
    }

    /**
     * 读取测试玩家的个人任务列表。
     *
     * @param player 测试玩家
     * @return 个人任务列表
     * @throws Exception 读取失败时抛出异常
     */
    static List<Task> loadPersonalTasks(TestServerPlayer player) throws Exception {
        return TodoListCommon.getTaskStorage().loadPlayerTasks(player.getUUID());
    }

    /**
     * 创建一条可用于个人任务命令测试的任务数据。
     *
     * @param title 任务标题
     * @param completed 是否完成
     * @param priority 任务优先级
     * @param projectId 关联项目 ID
     * @return 初始化后的测试任务
     */
    static Task createPersonalTask(String title, boolean completed, Task.Priority priority, String projectId) {
        Task task = new Task(title, "");
        task.setCompleted(completed);
        task.setPriority(priority);
        task.setScope(Task.Scope.PERSONAL);
        task.setProjectId(projectId);
        return task;
    }

    /**
     * 批量创建一组用于分页测试的个人任务，并通过微小延迟保证排序顺序稳定。
     *
     * @param titlePrefix 任务标题前缀
     * @param count 任务数量
     * @param projectId 关联项目 ID，null 表示不绑定项目
     * @return 生成好的测试任务数组
     * @throws Exception 线程休眠被中断时抛出异常
     */
    static Task[] createPagedPersonalTasks(String titlePrefix, int count, String projectId) throws Exception {
        Task[] tasks = new Task[count];
        for (int index = 0; index < count; index++) {
            tasks[index] = createPersonalTask(titlePrefix + (index + 1), false, Task.Priority.MEDIUM, projectId);
            Thread.sleep(2L);
        }
        return tasks;
    }

    static Task createTeamTask(String projectId, String title) {
        Task task = new Task(title, "");
        task.setScope(Task.Scope.TEAM);
        task.setProjectId(projectId);
        return task;
    }

    /**
     * 从团队任务存储中读取指定任务。
     *
     * @param taskId 任务 ID
     * @return 命中的任务对象
     * @throws Exception 读取失败时抛出异常
     */
    static Task loadTeamTaskById(String taskId) throws Exception {
        for (Task task : TodoListCommon.getTaskStorage().loadTeamTasks()) {
            if (task != null && taskId.equals(task.getId())) {
                return task;
            }
        }
        throw new AssertionError("未找到预期的团队任务，taskId=" + taskId);
    }

    /**
     * 从团队任务存储中按标题读取任务。
     *
     * @param title 任务标题
     * @return 命中的团队任务
     * @throws Exception 读取失败时抛出异常
     */
    static Task loadTeamTaskByTitle(String title) throws Exception {
        for (Task task : TodoListCommon.getTaskStorage().loadTeamTasks()) {
            if (task != null && title.equals(task.getTitle())) {
                return task;
            }
        }
        throw new AssertionError("未找到预期的团队任务，title=" + title);
    }

    /**
     * 深拷贝任务对象，避免测试基线和提交快照共享同一个可变实例。
     *
     * @param task 原始任务
     * @return 拷贝后的任务
     */
    static Task copyTask(Task task) {
        return Task.fromNbt(task.toNbt());
    }

    /**
     * 将 TodoListCommon 的项目存储替换为测试桩。
     *
     * @param storage 测试项目存储
     * @throws Exception 反射替换失败时抛出
     */
    private static void replaceProjectStorageForTest(ProjectStorage storage) throws Exception {
        Field field = TodoListCommon.class.getDeclaredField("projectStorage");
        field.setAccessible(true);
        field.set(null, storage);
    }

    /**
     * 从团队任务存储中按标题读取任务，不存在时返回 null。
     *
     * @param title 任务标题
     * @return 命中的团队任务；不存在时返回 null
     * @throws Exception 读取失败时抛出异常
     */
    static Task findTeamTaskByTitleOrNull(String title) throws Exception {
        for (Task task : TodoListCommon.getTaskStorage().loadTeamTasks()) {
            if (task != null && title.equals(task.getTitle())) {
                return task;
            }
        }
        return null;
    }

    /**
     * 从个人任务存储中按标题读取任务，不存在时返回 null。
     *
     * @param player 测试玩家
     * @param title 任务标题
     * @return 命中的个人任务，未命中时返回 null
     * @throws Exception 读取失败时抛出异常
     */
    static Task findPersonalTaskByTitleOrNull(TestServerPlayer player, String title) throws Exception {
        for (Task task : loadPersonalTasks(player)) {
            if (task != null && title.equals(task.getTitle())) {
                return task;
            }
        }
        return null;
    }

    /**
     * 从个人任务存储中按标题读取任务，不存在时抛出断言异常。
     *
     * @param player 测试玩家
     * @param title 任务标题
     * @return 命中的个人任务
     * @throws Exception 读取失败时抛出异常
     */
    static Task findPersonalTaskByTitle(TestServerPlayer player, String title) throws Exception {
        Task task = findPersonalTaskByTitleOrNull(player, title);
        if (task == null) {
            throw new AssertionError("未找到预期的个人任务，title=" + title);
        }
        return task;
    }

    /**
     * 按项目名称查找刚创建的项目。
     *
     * @param projectName 项目名称
     * @return 命中的项目，未命中时返回 null
     */
    static Project findProjectByName(String projectName) {
        for (Project project : TodoListCommon.getProjectManager().getAllProjects()) {
            if (project != null && projectName.equals(project.getName())) {
                return project;
            }
        }
        return null;
    }

    static TestMinecraftServer createServer(TestServerPlayer... onlinePlayers) {
        return createServer(true, false, onlinePlayers);
    }

    /**
     * 创建未开放局域网的单机服务端桩。
     *
     * @param onlinePlayers 在线玩家
     * @return 单机服务端桩
     */
    static TestMinecraftServer createSingleplayerServer(TestServerPlayer... onlinePlayers) {
        return createServer(false, false, onlinePlayers);
    }

    /**
     * 创建可配置 dedicated/published 状态的测试服务端。
     *
     * @param dedicated 是否为专用服务端
     * @param published 是否已开放局域网
     * @param onlinePlayers 在线玩家
     * @return 测试服务端桩
     */
    static TestMinecraftServer createServer(boolean dedicated, boolean published, TestServerPlayer... onlinePlayers) {
        try {
            TestMinecraftServer server = (TestMinecraftServer) UNSAFE.allocateInstance(TestMinecraftServer.class);
            TestPlayerList playerList = (TestPlayerList) UNSAFE.allocateInstance(TestPlayerList.class);
            playerList.players = new ArrayList<>();
            playerList.playersByUuid = new LinkedHashMap<>();
            playerList.playersByName = new LinkedHashMap<>();
            if (onlinePlayers != null) {
                for (TestServerPlayer player : onlinePlayers) {
                    if (player == null) {
                        continue;
                    }
                    player.testServer = server;
                    playerList.players.add(player);
                    playerList.playersByUuid.put(player.getUUID(), player);
                    playerList.playersByName.put(player.getName().getString(), player);
                }
            }
            server.testPlayerList = playerList;
            server.testDedicatedServer = dedicated;
            server.testPublished = published;
            return server;
        } catch (InstantiationException e) {
            throw new IllegalStateException("无法创建测试服务端实例", e);
        }
    }

    /**
     * 校验 project remove 二次确认成功后会删除项目并清理同项目任务。
     */
    static void flushProjectSaves(MinecraftServer server) {
        ProjectSaveDebouncer.flushNow(server);
    }

    /**
     * 模拟“重进世界”后的公共层重载，重新初始化存储并从磁盘恢复项目数据。
     */
    static void reloadPersistentState() {
        TodoListCommon.init();
        TodoListCommon.reloadProjectsFromStorage();
    }

    /**
     * 获取当前测试命名空间下的个人项目持久化文件路径。
     */
    static Path getPersonalProjectsFilePath() {
        return DataPathProvider.getProjectsDir().resolve("projects.dat");
    }

    /**
     * 获取指定玩家的项目状态持久化文件路径。
     *
     * @param player 测试玩家
     * @return 玩家项目状态文件路径
     */
    static Path getProjectPlayerStateFilePath(TestServerPlayer player) {
        return DataPathProvider.getProjectPlayersDir().resolve(player.getUUID().toString() + ".dat");
    }

    /**
     * 获取当前测试命名空间下的团队项目持久化文件路径。
     */
    static Path getTeamProjectsFilePath() {
        return DataPathProvider.getProjectsDir().resolve("team_projects.dat");
    }

    /**
     * 获取指定玩家的个人任务持久化文件路径。
     *
     * @param player 测试玩家
     * @return 玩家个人任务文件路径
     */
    static Path getPersonalTaskFilePath(TestServerPlayer player) {
        return DataPathProvider.getTaskPlayersDir().resolve(player.getUUID().toString() + ".dat");
    }

    /**
     * 获取当前测试命名空间下的团队任务持久化文件路径。
     */
    static Path getTeamTaskFilePath() {
        return TodoListCommon.getTaskStorage().getDataDirectoryPath().resolve("team_tasks.dat");
    }

    /**
     * 校验启动后手动修改 commandAccessMode 配置，不会被其他配置保存写回默认值。
     */
    private static void shouldPreserveCommandAccessModeAfterExternalConfigEdit() throws Exception {
        writeConfigCommandAccessMode(ModConfig.CommandAccessMode.OP_ONLY);
        ModConfig.load();
        assertEquals(ModConfig.CommandAccessMode.OP_ONLY, ModConfig.getInstance().getCommandAccessMode(), "初始加载的 commandAccessMode 应为 OP_ONLY");

        writeConfigCommandAccessMode(ModConfig.CommandAccessMode.VIEW_ONLY);
        ModConfig.getInstance().setHudShowWhenEmpty(true);

        assertEquals(ModConfig.CommandAccessMode.VIEW_ONLY, ModConfig.getInstance().getCommandAccessMode(), "其他配置保存不应覆盖手动修改的 commandAccessMode");
        assertContainsText(readConfigFileLines(), "\"commandAccessMode\": \"VIEW_ONLY\"", "配置文件中的 commandAccessMode 应保持 VIEW_ONLY");
    }

    /**
     * 将测试配置文件中的 commandAccessMode 更新为指定值，用于模拟玩家在游戏外手动编辑配置。
     *
     * @param accessMode 要写入的命令权限模式
     * @throws Exception 读写配置失败时抛出异常
     */
    static void writeConfigCommandAccessMode(ModConfig.CommandAccessMode accessMode) throws Exception {
        String config = Files.readString(getConfigFilePath(), StandardCharsets.UTF_8);
        String updated = config.replaceAll("\"commandAccessMode\"\\s*:\\s*\"[A-Z_]+\"", "\"commandAccessMode\": \"" + accessMode.name() + "\"");
        Files.writeString(getConfigFilePath(), updated, StandardCharsets.UTF_8);
    }

    /**
     * 读取测试环境的配置文件内容，便于断言写盘结果。
     *
     * @return 配置文件内容，以单元素列表返回，便于复用现有断言工具
     * @throws Exception 读取失败时抛出异常
     */
    static List<Component> readConfigFileLines() throws Exception {
        return List.of(Component.literal(Files.readString(getConfigFilePath(), StandardCharsets.UTF_8)));
    }

    /**
     * 获取命令测试使用的配置文件路径。
     *
     * @return 配置文件路径
     */
    private static Path getConfigFilePath() {
        return TEST_GAME_DIR.resolve("config").resolve("todolist.json");
    }

    /**
     * 创建命令测试专用的临时游戏目录。
     *
     * @return 临时目录路径
     */
    private static Path createTestGameDir() {
        try {
            Path dir = Files.createTempDirectory("todolist-command-tests");
            Files.createDirectories(dir.resolve("config"));
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建命令测试目录", e);
        }
    }

    /**
     * 创建绑定指定服务端的测试层级实例，供 fake 玩家的 level().getServer() 通道使用。
     *
     * @param server 目标服务端
     * @return 已绑定服务端的层级实例
     */
    private static ServerLevel createBoundServerLevel(MinecraftServer server) {
        try {
            ServerLevel level = (ServerLevel) UNSAFE.allocateInstance(ServerLevel.class);
            for (Class<?> c = ServerLevel.class; c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == MinecraftServer.class) {
                        f.setAccessible(true);
                        f.set(level, server);
                    }
                }
            }
            return level;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法创建测试服务端层级", e);
        }
    }

    /**
     * 反射读取 Unsafe，用于无构造创建假玩家实例。
     *
     * @return Unsafe 单例
     */
    private static Unsafe loadUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法访问 Unsafe", e);
        }
    }

    /**
     * 断言消息列表中包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 目标翻译键
     * @param message 失败提示
     */
    static void assertContainsMessageKey(List<Component> messages, String translationKey, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(translationKey)) {
                return;
            }
        }
        throw new AssertionError(message + " actual=" + messages);
    }

    /**
     * 断言消息列表中不包含指定翻译键。
     *
     * @param messages 消息列表
     * @param translationKey 不应出现的翻译键
     * @param message 失败提示
     */
    static void assertNotContainsMessageKey(List<Component> messages, String translationKey, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(translationKey)) {
                throw new AssertionError(message + " actual=" + messages);
            }
        }
    }

    /**
     * 断言消息列表中包含指定文本片段。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本
     * @param message 失败提示
     */
    static void assertContainsText(List<Component> messages, String expectedText, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(expectedText)) {
                return;
            }
        }
        throw new AssertionError(message + " actual=" + messages);
    }

    /**
     * 断言消息列表中不包含指定文本片段。
     *
     * @param messages 消息列表
     * @param unexpectedText 不应出现的文本
     * @param message 失败提示
     */
    static void assertNotContainsText(List<Component> messages, String unexpectedText, String message) {
        for (Component component : messages) {
            if (component != null && component.getString().contains(unexpectedText)) {
                throw new AssertionError(message + " actual=" + messages);
            }
        }
    }

    /**
     * 统计消息列表中包含指定文本片段的消息数量。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本片段
     * @return 命中的消息数量
     */
    static int countMessagesContaining(List<Component> messages, String expectedText) {
        int count = 0;
        for (Component component : messages) {
            if (component != null && component.getString().contains(expectedText)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 断言消息列表中包含两个文本片段，且前者出现在后者之前。
     *
     * @param messages 消息列表
     * @param earlierText 应先出现的文本
     * @param laterText 应后出现的文本
     * @param message 失败提示
     */
    private static void assertTextAppearsBefore(List<Component> messages, String earlierText, String laterText, String message) {
        int earlierIndex = findMessageIndexContaining(messages, earlierText);
        int laterIndex = findMessageIndexContaining(messages, laterText);
        if (earlierIndex < 0 || laterIndex < 0 || earlierIndex >= laterIndex) {
            throw new AssertionError(message + " actual=" + messages);
        }
    }

    /**
     * 查找首个包含指定文本片段的消息索引。
     *
     * @param messages 消息列表
     * @param expectedText 目标文本片段
     * @return 命中的消息索引；未命中时返回 -1
     */
    private static int findMessageIndexContaining(List<Component> messages, String expectedText) {
        for (int index = 0; index < messages.size(); index++) {
            Component component = messages.get(index);
            if (component != null && component.getString().contains(expectedText)) {
                return index;
            }
        }
        return -1;
    }

    static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    /**
     * 断言两个字符串列表相等。
     *
     * @param expected 期望列表
     * @param actual 实际列表
     * @param message 失败提示
     */
    static void assertListEquals(List<String> expected, List<String> actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    /**
     * 断言对象非空。
     *
     * @param value 实际对象
     * @param message 失败提示
     */
    static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言对象为空。
     *
     * @param value 实际对象
     * @param message 失败提示
     */
    static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " actual=" + value);
        }
    }

    /**
     * 捕获命令反馈消息的最小命令源实现。
     */
    static final class CapturingCommandSourceStack extends CommandSourceStack {
        private final List<Component> successMessages = new ArrayList<>();
        private final List<Component> failureMessages = new ArrayList<>();

        /**
         * 创建捕获型命令源。
         *
         * @param permissionLevel 命令权限等级
         * @param player 绑定的玩家实体
         */
        private CapturingCommandSourceStack(int permissionLevel, ServerPlayer player) {
            // v1_21_11 覆盖：CommandSourceStack 构造第 5 参由 int 改为 PermissionSet
            super(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, LevelBasedPermissionSet.forLevel(PermissionLevel.byId(permissionLevel)), "command-test", Component.literal("command-test"), null, player);
        }

        /**
         * 创建带最小服务端上下文的捕获型命令源。
         *
         * @param permissionLevel 命令权限等级
         * @param player 绑定的玩家实体
         * @param server 最小服务端桩对象
         */
        private CapturingCommandSourceStack(int permissionLevel, ServerPlayer player, MinecraftServer server) {
            super(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, LevelBasedPermissionSet.forLevel(PermissionLevel.byId(permissionLevel)), "command-test", Component.literal("command-test"), server, player);
        }

        /**
         * 捕获成功反馈消息。
         *
         * @param textSupplier 成功消息供应器
         * @param broadcastToOps 是否广播给管理员
         */
        @Override
        public void sendSuccess(Supplier<Component> textSupplier, boolean broadcastToOps) {
            successMessages.add(textSupplier.get());
        }

        /**
         * 捕获失败反馈消息。
         *
         * @param component 失败消息
         */
        @Override
        public void sendFailure(Component component) {
            failureMessages.add(component);
        }

        /**
         * 获取成功消息列表。
         *
         * @return 成功消息列表
         */
        List<Component> getSuccessMessages() {
            return successMessages;
        }

        /**
         * 获取失败消息列表。
         *
         * @return 失败消息列表
         */
        List<Component> getFailureMessages() {
            return failureMessages;
        }
    }

    /**
     * 最小假玩家实现，仅覆盖当前集成测试需要访问的玩家信息。
     */
    static class TestServerPlayer extends ServerPlayer {
        private UUID testUuid;
        private String testName;
        private boolean testOperator;
        private List<Component> testClientMessages;
        private MinecraftServer testServer;
    private ServerLevel testLevel;

        /**
         * 构造方法仅用于满足编译要求，运行时通过 Unsafe 绕过。
         */
        private TestServerPlayer() {
            super(null, null, null, null);
            throw new UnsupportedOperationException("请通过 createPlayer 创建测试玩家");
        }

        /**
         * 返回测试玩家 UUID。
         *
         * @return 玩家 UUID
         */
        @Override
        public UUID getUUID() {
            return testUuid;
        }

        /**
         * 返回测试玩家 UUID 字符串。
         *
         * @return 玩家 UUID 字符串
         */
        @Override
        public String getStringUUID() {
            return testUuid == null ? "" : testUuid.toString();
        }

        /**
         * 返回测试玩家绑定的服务端层级；1.21.9 移除 Entity.getServer() 后，
         * 主源集改经 level().getServer() 获取服务端，fake 玩家需提供绑定层级。
         *
         * @return 绑定了测试服务端的层级实例
         */
        @Override
        public ServerLevel level() {
            if (testLevel == null) {
                testLevel = createBoundServerLevel(testServer);
            }
            return testLevel;
        }

        /**
         * 返回测试玩家显示名称。
         *
         * @return 玩家名称组件
         */
        @Override
        public Component getName() {
            return Component.literal(testName == null ? "" : testName);
        }

        /**
         * 返回测试玩家绑定的服务端对象。
         *
         * @return 测试服务端对象
         */
        public MinecraftServer getServer() {
            return testServer;
        }

        /**
         * 返回测试玩家权限集。
         *
         * @return 管理员返回 OWNER 级权限集，非管理员仅保留 ALL 级
         */
        @Override
        public PermissionSet permissions() {
            return testOperator ? LevelBasedPermissionSet.OWNER : LevelBasedPermissionSet.ALL;
        }

        /**
         * 捕获发送到客户端的提示消息，供加入项目审批链路断言使用。
         *
         * @param component 客户端消息
         * @param actionBar 是否显示为 action bar
         */
        @Override
        public void displayClientMessage(Component component, boolean actionBar) {
            if (testClientMessages == null) {
                testClientMessages = new ArrayList<>();
            }
            testClientMessages.add(component);
        }

        /**
         * 返回捕获到的客户端消息列表。
         *
         * @return 客户端消息列表
         */
        List<Component> getClientMessages() {
            if (testClientMessages == null) {
                testClientMessages = new ArrayList<>();
            }
            return testClientMessages;
        }
    }

    /**
     * 最小测试服务端实现，仅覆盖团队任务成功分支需要访问的能力。
     */
    static class TestMinecraftServer extends MinecraftServer {
        private TestPlayerList testPlayerList;
        private boolean testDedicatedServer = true;
        private boolean testPublished;

        /**
         * 构造方法仅用于满足编译要求，运行时通过 Unsafe 绕过。
         */
        private TestMinecraftServer() {
            super(null, null, null, null, Proxy.NO_PROXY, null, null, null);
            throw new UnsupportedOperationException("请通过 createServer 创建测试服务端");
        }

        /**
         * 返回测试用玩家列表。
         *
         * @return 玩家列表桩对象
         */
        @Override
        public PlayerList getPlayerList() {
            return testPlayerList;
        }

        /**
         * 立即执行提交到服务端事件循环的任务，避免测试依赖异步调度。
         *
         * @param runnable 待执行任务
         */
        @Override
        public void execute(Runnable runnable) {
            if (runnable != null) {
                runnable.run();
            }
        }

        /**
         * 与 execute 保持一致，立即执行可选任务。
         *
         * @param runnable 待执行任务
         */
        @Override
        public void executeIfPossible(Runnable runnable) {
            execute(runnable);
        }

        /**
         * 返回是否启用 Tick 耗时日志。
         *
         * @return 测试环境始终关闭 Tick 耗时日志
         */
        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }

        /**
         * 返回 Tick 时间日志器占位实现，用于探测 1.21.1 所需返回类型。
         *
         * @return 占位对象
         */
        public SampleLogger getTickTimeLogger() {
            return null;
        }

        /**
         * 标记测试服务端为专用服务端，避免团队项目被视为单人模式不可用。
         *
         * @return 当前测试服务端的 dedicated 标记
         */
        @Override
        public boolean isDedicatedServer() {
            return testDedicatedServer;
        }

        /**
         * 标记测试服务端未发布局域网。
         *
         * @return 当前测试服务端的 published 标记
         */
        @Override
        public boolean isPublished() {
            return testPublished;
        }

        /**
         * 设置测试服务器的局域网发布状态。
         *
         * @param published 是否已发布局域网
         */
        void setTestPublished(boolean published) {
            this.testPublished = published;
        }

        /**
         * 初始化服务端，测试环境无需执行。
         *
         * @return 始终返回 true
         */
        @Override
        protected boolean initServer() {
            return true;
        }

        /**
         * 返回 OP 权限集。
         *
         * @return 固定返回 OWNER 级（对应旧等级 4）
         */
        @Override
        public LevelBasedPermissionSet operatorUserPermissions() {
            return LevelBasedPermissionSet.OWNER;
        }

        /**
         * 返回函数编译权限集。
         *
         * @return 固定返回 GAMEMASTER 级（对应旧等级 2）
         */
        @Override
        public PermissionSet getFunctionCompilationPermissions() {
            return LevelBasedPermissionSet.GAMEMASTER;
        }

        /**
         * 指示是否广播 RCON 输出。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        /**
         * 填充服务端系统报告，测试环境直接返回入参。
         *
         * @param report 系统报告
         * @return 原始系统报告
         */
        @Override
        public net.minecraft.SystemReport fillServerSystemReport(net.minecraft.SystemReport report) {
            return report;
        }

        /**
         * 返回限速阈值。
         *
         * @return 固定返回 0
         */
        /**
         * 返回最大玩家数，测试环境固定返回 20。
         *
         * @return 最大玩家数
         */
        @Override
        public int getMaxPlayers() {
            return 20;
        }

        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        /**
         * 标记测试服务端不使用 native transport（原 isEpollEnabled 在 1.21.11 更名）。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean useNativeTransport() {
            return false;
        }

        /**
         * 指示管理员广播开关。
         *
         * @return 始终返回 false
         */
        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        /**
         * 判断是否为单人房主，测试环境始终返回 false。
         *
         * @param profile 玩家档案
         * @return 始终返回 false
         */
        @Override
        public boolean isSingleplayerOwner(net.minecraft.server.players.NameAndId profile) {
            return false;
        }
    }

    /**
     * 最小测试玩家列表实现，支持按 UUID、名称和在线列表查询。
     */
    static class TestPlayerList extends PlayerList {
        private List<ServerPlayer> players;
        private Map<UUID, ServerPlayer> playersByUuid;
        private Map<String, ServerPlayer> playersByName;

        /**
         * 构造方法仅用于满足编译要求，运行时通过 Unsafe 绕过。
         */
        private TestPlayerList() {
            super(null, null, null, null);
            throw new UnsupportedOperationException("请通过 createServer 创建测试玩家列表");
        }

        /**
         * 返回在线玩家列表。
         *
         * @return 在线玩家列表
         */
        @Override
        public List<ServerPlayer> getPlayers() {
            return players == null ? List.of() : players;
        }

        /**
         * 按 UUID 查找在线玩家。
         *
         * @param uuid 玩家 UUID
         * @return 匹配的玩家，找不到时返回 null
         */
        @Override
        public ServerPlayer getPlayer(UUID uuid) {
            return playersByUuid == null ? null : playersByUuid.get(uuid);
        }

        /**
         * 按名称查找在线玩家。
         *
         * @param name 玩家名称
         * @return 匹配的玩家，找不到时返回 null
         */
        @Override
        public ServerPlayer getPlayerByName(String name) {
            return playersByName == null ? null : playersByName.get(name);
        }
    }

    /**
     * 第一次个人项目保存阻塞后失败，后续保存成功，用于验证防抖保存补救逻辑。
     */
    static final class FailingOnceProjectStorage extends ProjectStorage {
        private final CountDownLatch firstSaveStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSave = new CountDownLatch(1);
        private int personalSaveAttempts;
        private List<String> lastPersonalProjectIds = List.of();

        /**
         * 保存个人项目，第一次调用模拟后台写盘失败。
         *
         * @param projects 待保存的个人项目列表
         * @throws IOException 第一次保存时模拟写盘失败
         */
        @Override
        public void saveProjects(List<Project> projects) throws IOException {
            personalSaveAttempts++;
            if (personalSaveAttempts == 1) {
                firstSaveStarted.countDown();
                try {
                    if (!releaseFirstSave.await(5, TimeUnit.SECONDS)) {
                        throw new IOException("等待释放第一次项目保存超时");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("等待释放第一次项目保存时被中断", exception);
                }
                throw new IOException("模拟第一次项目保存失败");
            }
            lastPersonalProjectIds = projects.stream().map(Project::getId).toList();
        }

        /**
         * 等待第一次保存开始。
         *
         * @return 超时前开始返回 true
         * @throws InterruptedException 等待被中断时抛出
         */
        boolean awaitFirstSaveStarted() throws InterruptedException {
            return firstSaveStarted.await(5, TimeUnit.SECONDS);
        }

        /**
         * 释放第一次阻塞的保存。
         */
        void releaseFirstSave() {
            releaseFirstSave.countDown();
        }

        /**
         * 返回个人项目保存尝试次数。
         *
         * @return 保存尝试次数
         */
        int getPersonalSaveAttempts() {
            return personalSaveAttempts;
        }

        /**
         * 返回最后一次成功保存的个人项目 ID。
         *
         * @return 项目 ID 列表
         */
        List<String> getLastPersonalProjectIds() {
            return lastPersonalProjectIds;
        }
    }
}
