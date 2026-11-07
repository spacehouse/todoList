package com.todolist.bootstrap;

import com.mojang.brigadier.CommandDispatcher;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.storage.H2StorageBootstrap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * CommandBootstrapH2IntegrationTestMain 覆盖 H2 运维命令的离线集成行为。
 */
public final class CommandBootstrapH2IntegrationTestMain {
    /**
     * 禁止实例化 H2 命令测试入口。
     */
    private CommandBootstrapH2IntegrationTestMain() {
    }

    /**
     * 执行 H2 运维命令离线集成测试。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 任一测试失败时抛出
     */
    public static void main(String[] args) throws Exception {
        CommandBootstrapIntegrationTestMain.bootstrapEnvironment();
        try {
            CommandTestSupport.runTestCase("CommandBootstrapH2IntegrationTestMain.shouldExecuteH2StatusCommand", CommandBootstrapH2IntegrationTestMain::shouldExecuteH2StatusCommand);
            CommandTestSupport.runTestCase("CommandBootstrapH2IntegrationTestMain.shouldExecuteH2BackupCommand", CommandBootstrapH2IntegrationTestMain::shouldExecuteH2BackupCommand);
            CommandTestSupport.runTestCase("CommandBootstrapH2IntegrationTestMain.shouldExecuteH2ReloadDbCommand", CommandBootstrapH2IntegrationTestMain::shouldExecuteH2ReloadDbCommand);
            CommandTestSupport.runTestCase("CommandBootstrapH2IntegrationTestMain.shouldExecuteRootReloadDbAlias", CommandBootstrapH2IntegrationTestMain::shouldExecuteRootReloadDbAlias);
            CommandTestSupport.runTestCase("CommandBootstrapH2IntegrationTestMain.shouldExecuteH2HealthCommand", CommandBootstrapH2IntegrationTestMain::shouldExecuteH2HealthCommand);
        } finally {
            H2StorageBootstrap.resetAllForTests();
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
            DataPathProvider.resetStorageNamespace();
        }
    }

    /**
     * 验证 H2 状态命令可由管理员执行并输出状态消息。
     */
    private static void shouldExecuteH2StatusCommand() throws Exception {
        resetState();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(2, null);

        int result = dispatcher.execute("todo h2 status", source);

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "h2 status 成功时应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.status.backend", "h2 status 未输出后端状态");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.status.mode", "h2 status 未输出连接模式");
    }

    /**
     * 验证 H2 备份命令可由管理员执行并输出备份路径。
     */
    private static void shouldExecuteH2BackupCommand() throws Exception {
        resetState();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(2, null);

        int result = dispatcher.execute("todo h2 backup command-backup", source);

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "h2 backup 成功时应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.backup.success", "h2 backup 未输出成功消息");
    }

    /**
     * 验证 H2 reload-db 命令会从数据库刷新内存项目列表。
     */
    private static void shouldExecuteH2ReloadDbCommand() throws Exception {
        resetState();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        Project persisted = new Project("Persisted", Project.Scope.PERSONAL, null);
        persisted.setId("persisted-project");
        TodoListCommon.getProjectStorage().saveProjects(java.util.List.of(persisted));
        Project stale = new Project("Stale", Project.Scope.PERSONAL, null);
        stale.setId("stale-project");
        TodoListCommon.getProjectManager().addProject(stale);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(2, null);

        int result = dispatcher.execute("todo h2 reload-db", source);

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "h2 reload-db 成功时应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.reload_db.success", "h2 reload-db 未输出成功消息");
        CommandBootstrapIntegrationTestMain.assertNotNull(TodoListCommon.getProjectManager().getProject("persisted-project"), "reload-db 后应加载 H2 中的项目");
        CommandBootstrapIntegrationTestMain.assertNull(TodoListCommon.getProjectManager().getProject("stale-project"), "reload-db 后应移除内存中的旧项目");
    }

    /**
     * 验证根级 reload-db 别名会复用 H2 重载流程。
     */
    private static void shouldExecuteRootReloadDbAlias() throws Exception {
        resetState();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(2, null);

        int result = dispatcher.execute("todo reload-db", source);

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "根级 reload-db 成功时应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.reload_db.success", "根级 reload-db 未输出成功消息");
    }

    /**
     * 验证 H2 健康检查命令可输出 schema 和任务统计。
     */
    private static void shouldExecuteH2HealthCommand() throws Exception {
        resetState();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        CommandDispatcher<CommandSourceStack> dispatcher = CommandBootstrapIntegrationTestMain.createDispatcher();
        CommandBootstrapIntegrationTestMain.CapturingCommandSourceStack source = CommandBootstrapIntegrationTestMain.createSource(2, null);

        int result = dispatcher.execute("todo h2 health", source);

        CommandBootstrapIntegrationTestMain.assertEquals(1, result, "h2 health 成功时应返回成功");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.health.schema", "h2 health 未输出 schema");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.health.tasks", "h2 health 未输出任务数量");
        CommandBootstrapIntegrationTestMain.assertContainsMessageKey(source.getSuccessMessages(), "command.todolist.h2.health.success", "h2 health 未输出成功消息");
    }

    /**
     * 重置 H2 命令测试状态。
     */
    private static void resetState() {
        H2StorageBootstrap.resetAllForTests();
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        TodoListCommon.init();
    }
}
