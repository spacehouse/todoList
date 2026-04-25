package com.todolist.bootstrap;

import com.todolist.config.ModConfig;
import com.todolist.network.TaskPackets;
import com.todolist.project.Project;
import com.todolist.task.Task;

import java.lang.reflect.Method;

/**
 * TaskPackets 服务端权限回归测试入口。
 * 负责覆盖团队任务整表同步时对“全服任务模式”的权限透传，避免 GUI 允许但服务端回滚的情况再次出现。
 */
public final class TaskPacketsPermissionTestMain {

    /**
     * 私有构造方法，避免测试入口被实例化。
     */
    private TaskPacketsPermissionTestMain() {
    }

    /**
     * 程序入口，串行执行 TaskPackets 相关权限回归测试。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 任一测试失败时向上抛出异常
     */
    public static void main(String[] args) throws Exception {
        CommandTestSupport.runTestCase(
                "TaskPacketsPermissionTestMain.shouldAllowNonMemberClaimUpdateWhenAllPlayerClaimCompleteEnabled",
                TaskPacketsPermissionTestMain::shouldAllowNonMemberClaimUpdateWhenAllPlayerClaimCompleteEnabled
        );
        CommandTestSupport.runTestCase(
                "TaskPacketsPermissionTestMain.shouldRejectNonMemberClaimUpdateWhenAllPlayerClaimCompleteDisabled",
                TaskPacketsPermissionTestMain::shouldRejectNonMemberClaimUpdateWhenAllPlayerClaimCompleteDisabled
        );
    }

    /**
     * 验证团队项目开启全服任务模式后，非成员通过整表同步提交的领取更新会被服务端接受。
     *
     * @throws Exception 反射调用失败时抛出异常
     */
    private static void shouldAllowNonMemberClaimUpdateWhenAllPlayerClaimCompleteEnabled() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        CommandBootstrapIntegrationTestMain.TestServerPlayer manager =
                CommandBootstrapIntegrationTestMain.createPlayer("00000000-0000-0000-0000-000000001201", "manager", false);
        CommandBootstrapIntegrationTestMain.TestServerPlayer outsider =
                CommandBootstrapIntegrationTestMain.createPlayer("00000000-0000-0000-0000-000000001202", "outsider", false);
        Project project = CommandBootstrapIntegrationTestMain.addTeamProject(manager, "task-packets-allow-all", "Task Packets Allow All");
        project.setAllowAllPlayersClaimComplete(true);

        Task currentTask = CommandBootstrapIntegrationTestMain.createTeamTask(project.getId(), "Outsider Claim Task");
        Task incomingTask = copyTaskForClaim(currentTask, outsider);

        boolean allowed = invokeCanApplyTeamTaskUpdate(outsider, currentTask, incomingTask);

        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.TRUE, allowed, "开启全服任务模式后，非成员领取更新应被服务端接受");
    }

    /**
     * 验证团队项目关闭全服任务模式时，非成员通过整表同步提交的领取更新仍会被服务端拒绝。
     *
     * @throws Exception 反射调用失败时抛出异常
     */
    private static void shouldRejectNonMemberClaimUpdateWhenAllPlayerClaimCompleteDisabled() throws Exception {
        CommandBootstrapIntegrationTestMain.resetState(ModConfig.CommandAccessMode.FULL);
        CommandBootstrapIntegrationTestMain.TestServerPlayer manager =
                CommandBootstrapIntegrationTestMain.createPlayer("00000000-0000-0000-0000-000000001203", "manager", false);
        CommandBootstrapIntegrationTestMain.TestServerPlayer outsider =
                CommandBootstrapIntegrationTestMain.createPlayer("00000000-0000-0000-0000-000000001204", "outsider", false);
        Project project = CommandBootstrapIntegrationTestMain.addTeamProject(manager, "task-packets-allow-none", "Task Packets Allow None");
        project.setAllowAllPlayersClaimComplete(false);

        Task currentTask = CommandBootstrapIntegrationTestMain.createTeamTask(project.getId(), "Outsider Claim Rejected Task");
        Task incomingTask = copyTaskForClaim(currentTask, outsider);

        boolean allowed = invokeCanApplyTeamTaskUpdate(outsider, currentTask, incomingTask);

        CommandBootstrapIntegrationTestMain.assertEquals(Boolean.FALSE, allowed, "关闭全服任务模式后，非成员领取更新仍应被服务端拒绝");
    }

    /**
     * 基于当前任务复制出一份“由指定玩家领取”的目标状态，供服务端权限判断使用。
     *
     * @param currentTask 当前服务端任务状态
     * @param claimant 领取任务的玩家
     * @return 模拟客户端提交后的任务状态
     */
    private static Task copyTaskForClaim(Task currentTask, CommandBootstrapIntegrationTestMain.TestServerPlayer claimant) {
        Task incomingTask = new Task(currentTask.getTitle(), currentTask.getDescription());
        incomingTask.setId(currentTask.getId());
        incomingTask.setScope(currentTask.getScope());
        incomingTask.setProjectId(currentTask.getProjectId());
        incomingTask.setCompleted(currentTask.isCompleted());
        incomingTask.setPriority(currentTask.getPriority());
        incomingTask.setTags(currentTask.getTags());
        incomingTask.setDueDate(currentTask.getDueDate());
        incomingTask.setCreatorUuid(currentTask.getCreatorUuid());
        incomingTask.setAssigneeUuid(claimant.getStringUUID());
        incomingTask.setAssigneeName(claimant.getName().getString());
        return incomingTask;
    }

    /**
     * 反射调用 TaskPackets 的团队任务更新权限判定，验证服务端会如何处理整表同步中的领取改动。
     *
     * @param player 发起更新的玩家
     * @param currentTask 服务端当前任务状态
     * @param incomingTask 客户端提交的任务状态
     * @return 服务端是否接受此次更新
     * @throws Exception 反射调用失败时抛出异常
     */
    private static boolean invokeCanApplyTeamTaskUpdate(
            CommandBootstrapIntegrationTestMain.TestServerPlayer player,
            Task currentTask,
            Task incomingTask
    ) throws Exception {
        Method method = TaskPackets.class.getDeclaredMethod(
                "canApplyTeamTaskUpdate",
                net.minecraft.server.level.ServerPlayer.class,
                Task.class,
                Task.class
        );
        method.setAccessible(true);
        return (Boolean) method.invoke(null, player, currentTask, incomingTask);
    }
}
