package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.todolist.TodoListCommon;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestMinecraftServer;
import com.todolist.bootstrap.CommandBootstrapIntegrationTestMain.TestServerPlayer;
import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import net.minecraft.commands.CommandSourceStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 命令系统任务导入集成测试入口：覆盖 /todo import 的文件查找、成功落库与失败拒绝链路。
 */
public final class CommandBootstrapImportIntegrationTestMain {

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private CommandBootstrapImportIntegrationTestMain() {
    }

    /**
     * 程序入口，初始化命令测试环境后顺序执行导入相关用例。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 当任一测试失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        CommandBootstrapIntegrationTestMain.bootstrapEnvironment();
        try {
            runCase("shouldImportTasksFromImportsDirectorySuccessfully", CommandBootstrapImportIntegrationTestMain::shouldImportTasksFromImportsDirectorySuccessfully);
            runCase("shouldImportTasksByGameDirRelativePath", CommandBootstrapImportIntegrationTestMain::shouldImportTasksByGameDirRelativePath);
            runCase("shouldRejectImportWhenFileMissing", CommandBootstrapImportIntegrationTestMain::shouldRejectImportWhenFileMissing);
            runCase("shouldRejectImportWhenJsonMalformed", CommandBootstrapImportIntegrationTestMain::shouldRejectImportWhenJsonMalformed);
            runCase("shouldRejectImportWithEmptyTaskList", CommandBootstrapImportIntegrationTestMain::shouldRejectImportWithEmptyTaskList);
            runCase("shouldRejectImportWithoutPersistingWhenValidationFails", CommandBootstrapImportIntegrationTestMain::shouldRejectImportWithoutPersistingWhenValidationFails);
            runCase("shouldRejectImportWithNullTaskEntryWithoutCrash", CommandBootstrapImportIntegrationTestMain::shouldRejectImportWithNullTaskEntryWithoutCrash);
            runCase("shouldImportToSpecifiedProjectByIdSuccessfully", CommandBootstrapImportIntegrationTestMain::shouldImportToSpecifiedProjectByIdSuccessfully);
            runCase("shouldImportToSpecifiedProjectByNameSuccessfully", CommandBootstrapImportIntegrationTestMain::shouldImportToSpecifiedProjectByNameSuccessfully);
            runCase("shouldRejectImportWhenProjectMissing", CommandBootstrapImportIntegrationTestMain::shouldRejectImportWhenProjectMissing);
            runCase("shouldRejectImportWhenProjectIsTeam", CommandBootstrapImportIntegrationTestMain::shouldRejectImportWhenProjectIsTeam);
        } finally {
            com.todolist.storage.H2StorageBootstrap.resetAllForTests();
            com.todolist.network.TaskPackets.setServerPacketSender(null);
        }
    }

    /**
     * 校验 config/todolist/imports/ 目录下的导入文件能被命令找到并落库为带触发器的父子任务。
     */
    private static void shouldImportTasksFromImportsDirectorySuccessfully() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000071", "import-owner", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        Path importFile = importsDir().resolve("smoke-import.json");
        writeImportFile(importFile, """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "id": "base", "title": "下界准备", "trigger": { "type": "ITEM_COLLECT", "target": "minecraft:obsidian", "targetCount": 14 } },
                    { "title": "收集打火石", "parentId": "base" }
                  ]
                }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import smoke-import.json", source);

            GuiTestSupport.assertEquals(1, result, "导入成功应返回成功");
            CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                    source.getSuccessMessages(), "command.todolist.import.success", "导入成功未输出统计反馈");

            List<Task> tasks = CommandBootstrapIntegrationTestMain.loadPersonalTasks(player);
            Task parent = findByTitle(tasks, "下界准备");
            Task child = findByTitle(tasks, "收集打火石");
            GuiTestSupport.assertNotNull(parent, "导入父任务未落库");
            GuiTestSupport.assertNotNull(child, "导入子任务未落库");
            GuiTestSupport.assertTrue(parent.hasTrigger(), "导入任务应带触发器");
            GuiTestSupport.assertEquals(player.getUUID().toString(),
                    parent.getCreatorUuid(), "导入任务创建者应为执行玩家");
            GuiTestSupport.assertEquals(parent.getId(),
                    child.getParentTaskId(), "导入子任务应挂接到父任务");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验放在游戏目录根的导入文件可经相对路径直接导入。
     */
    private static void shouldImportTasksByGameDirRelativePath() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000072", "import-owner2", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        Path importFile = DataPathProvider.getGameDir().resolve("gamedir-import.json");
        writeImportFile(importFile, """
                { "formatVersion": 1, "tasks": [ { "title": "根目录导入任务" } ] }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import gamedir-import.json", source);

            GuiTestSupport.assertEquals(1, result, "根目录导入应成功");
            List<Task> tasks = CommandBootstrapIntegrationTestMain.loadPersonalTasks(player);
            GuiTestSupport.assertNotNull(findByTitle(tasks, "根目录导入任务"), "根目录导入任务未落库");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验文件不存在时返回 file_not_found 反馈且不落库。
     */
    private static void shouldRejectImportWhenFileMissing() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000073", "import-owner3", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();

        CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
        int result = dispatcher.execute("todo import missing-file.json", source);

        GuiTestSupport.assertEquals(0, result, "文件缺失应返回失败");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                source.getFailureMessages(), "command.todolist.import.file_not_found", "文件缺失未输出对应反馈");
        GuiTestSupport.assertEquals(0,
                CommandBootstrapIntegrationTestMain.loadPersonalTasks(player).size(), "文件缺失不应写入任何任务");
    }

    /**
     * 校验非法 JSON 返回 invalid_format 反馈。
     */
    private static void shouldRejectImportWhenJsonMalformed() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000074", "import-owner4", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        Path importFile = importsDir().resolve("malformed-import.json");
        writeImportFile(importFile, "{ formatVersion: 1 ");
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import malformed-import.json", source);

            GuiTestSupport.assertEquals(0, result, "非法 JSON 应返回失败");
            CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                    source.getFailureMessages(), "command.todolist.import.invalid_format", "非法 JSON 未输出对应反馈");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验空任务列表返回 empty 反馈。
     */
    private static void shouldRejectImportWithEmptyTaskList() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000075", "import-owner5", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        Path importFile = importsDir().resolve("empty-import.json");
        writeImportFile(importFile, "{ \"formatVersion\": 1, \"tasks\": [] }");
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import empty-import.json", source);

            GuiTestSupport.assertEquals(0, result, "空任务列表应返回失败");
            CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                    source.getFailureMessages(), "command.todolist.import.empty", "空任务列表未输出对应反馈");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验字段校验失败时整批拒绝：报 validation_failed 且任何任务都不落库（原子性）。
     */
    private static void shouldRejectImportWithoutPersistingWhenValidationFails() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000076", "import-owner6", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        Path importFile = importsDir().resolve("invalid-import.json");
        writeImportFile(importFile, """
                {
                  "formatVersion": 1,
                  "tasks": [
                    { "title": "合法任务" },
                    { "title": "   " },
                    { "title": "引用缺失", "parentId": "ghost" }
                  ]
                }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import invalid-import.json", source);

            GuiTestSupport.assertEquals(0, result, "校验失败应返回失败");
            CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                    source.getFailureMessages(), "command.todolist.import.validation_failed", "校验失败未输出对应反馈");
            List<Task> tasks = CommandBootstrapIntegrationTestMain.loadPersonalTasks(player);
            GuiTestSupport.assertEquals(0, tasks.size(),
                    "校验失败的导入不得部分落库（原子性）");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验含 null 任务条目的导入文件被整批拒绝：命令返回失败、无异常穿透、不落库。
     */
    private static void shouldRejectImportWithNullTaskEntryWithoutCrash() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000077", "import-owner7", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        Path importFile = importsDir().resolve("null-entry-import.json");
        writeImportFile(importFile, """
                { "formatVersion": 1, "tasks": [ null, { "title": "合法任务" } ] }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import null-entry-import.json", source);

            GuiTestSupport.assertEquals(0, result, "null 条目导入应返回失败");
            CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                    source.getFailureMessages(), "command.todolist.import.validation_failed", "null 条目未输出校验失败反馈");
            GuiTestSupport.assertEquals(0,
                    CommandBootstrapIntegrationTestMain.loadPersonalTasks(player).size(),
                    "含 null 条目的导入不得落库");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验 /todo import 带项目 ID 参数时，任务落到指定个人项目而非当前激活项目。
     */
    private static void shouldImportToSpecifiedProjectByIdSuccessfully() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000081", "import-proj-owner1", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        com.todolist.project.Project project = addPersonalProject(player, "import-target-by-id", "导入目标项目甲");
        Path importFile = importsDir().resolve("project-by-id.json");
        writeImportFile(importFile, """
                { "formatVersion": 1, "tasks": [ { "title": "项目ID导入任务" } ] }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import project-by-id.json import-target-by-id", source);

            GuiTestSupport.assertEquals(1, result, "指定项目导入应成功");
            Task task = findByTitle(CommandBootstrapIntegrationTestMain.loadPersonalTasks(player), "项目ID导入任务");
            GuiTestSupport.assertNotNull(task, "指定项目导入任务未落库");
            GuiTestSupport.assertEquals(project.getId(), task.getProjectId(), "任务应落到指定项目");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验 /todo import 带项目名称参数时同样生效（名称精确匹配）。
     */
    private static void shouldImportToSpecifiedProjectByNameSuccessfully() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000082", "import-proj-owner2", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        com.todolist.project.Project project = addPersonalProject(player, "import-target-by-name", "导入目标项目乙");
        Path importFile = importsDir().resolve("project-by-name.json");
        writeImportFile(importFile, """
                { "formatVersion": 1, "tasks": [ { "title": "项目名称导入任务" } ] }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import project-by-name.json 导入目标项目乙", source);

            GuiTestSupport.assertEquals(1, result, "按项目名称导入应成功");
            Task task = findByTitle(CommandBootstrapIntegrationTestMain.loadPersonalTasks(player), "项目名称导入任务");
            GuiTestSupport.assertNotNull(task, "按名称导入任务未落库");
            GuiTestSupport.assertEquals(project.getId(), task.getProjectId(), "任务应落到名称匹配的项目");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验指定的项目不存在时整批拒绝且不落库。
     */
    private static void shouldRejectImportWhenProjectMissing() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000083", "import-proj-owner3", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        Path importFile = importsDir().resolve("project-missing.json");
        writeImportFile(importFile, """
                { "formatVersion": 1, "tasks": [ { "title": "不应落库的项目缺失任务" } ] }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import project-missing.json 不存在的项目", source);

            GuiTestSupport.assertEquals(0, result, "项目缺失应返回失败");
            CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                    source.getFailureMessages(), "command.todolist.import.project_not_found", "项目缺失未输出对应反馈");
            GuiTestSupport.assertNull(
                    findByTitle(CommandBootstrapIntegrationTestMain.loadPersonalTasks(player), "不应落库的项目缺失任务"),
                    "项目缺失的导入不得落库");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 校验指定的团队项目被拒绝（v1 仅支持个人项目）且不落库。
     */
    private static void shouldRejectImportWhenProjectIsTeam() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TestServerPlayer player = CommandBootstrapIntegrationTestMain.createPlayer(
                "00000000-0000-0000-0000-000000000084", "import-proj-owner4", false);
        TestMinecraftServer server = CommandBootstrapIntegrationTestMain.createServer(player);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.addTeamProject(player, "import-team-project", "导入团队项目");
        Path importFile = importsDir().resolve("project-team.json");
        writeImportFile(importFile, """
                { "formatVersion": 1, "tasks": [ { "title": "不应落库的团队项目任务" } ] }
                """);
        try {
            CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(0, player, server);
            int result = dispatcher.execute("todo import project-team.json 导入团队项目", source);

            GuiTestSupport.assertEquals(0, result, "团队项目应返回失败");
            CommandBootstrapIntegrationTestMain.assertContainsMessageKey(
                    source.getFailureMessages(), "command.todolist.import.project_not_personal", "团队项目未输出对应反馈");
            GuiTestSupport.assertNull(
                    findByTitle(CommandBootstrapIntegrationTestMain.loadPersonalTasks(player), "不应落库的团队项目任务"),
                    "团队项目导入不得落库");
        } finally {
            Files.deleteIfExists(importFile);
        }
    }

    /**
     * 创建测试用个人项目并注册进项目管理器。
     */
    private static com.todolist.project.Project addPersonalProject(TestServerPlayer owner, String projectId, String projectName) {
        com.todolist.project.Project project = new com.todolist.project.Project(
                projectName, com.todolist.project.Project.Scope.PERSONAL, owner.getStringUUID());
        project.setId(projectId);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 返回测试游戏目录下的标准导入目录（不存在则创建）。
     */
    private static Path importsDir() throws Exception {
        Path dir = DataPathProvider.getGameDir().resolve("config").resolve("todolist").resolve("imports");
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * 以 UTF-8 写入导入文件内容。
     */
    private static void writeImportFile(Path file, String json) throws Exception {
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    /**
     * 按标题查找任务，找不到返回 null。
     */
    private static Task findByTitle(List<Task> tasks, String title) {
        for (Task task : tasks) {
            if (title.equals(task.getTitle())) {
                return task;
            }
        }
        return null;
    }

    /**
     * 以统一日志格式执行单个导入集成用例。
     */
    private static void runCase(String caseName, CommandTestSupport.ThrowingRunnable action) throws Exception {
        CommandTestSupport.runTestCase(
                CommandBootstrapImportIntegrationTestMain.class.getSimpleName() + "." + caseName,
                action
        );
    }
}
