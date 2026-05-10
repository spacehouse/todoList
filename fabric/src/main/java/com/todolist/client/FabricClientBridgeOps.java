package com.todolist.client;

import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/**
 * Fabric 端 {@link ClientBridge.ClientOps} 实现。
 * 用于将跨平台的客户端操作桥接到 Fabric 客户端网络与状态实现。
 */
public final class FabricClientBridgeOps implements ClientBridge.ClientOps {
    /**
     * 获取团队任务管理器。
     *
     * @return 团队任务管理器
     */
    @Override
    public TaskManager getTeamTaskManager() {
        return TodoClient.getTeamTaskManager();
    }

    /**
     * 判断是否启用团队项目能力。
     *
     * @return 是否可用
     */
    @Override
    public boolean isTeamProjectsEnabled() {
        return TodoClient.isTeamProjectsEnabled();
    }

    /**
     * 获取当前激活项目 ID。
     *
     * @return 项目 ID
     */
    @Override
    public String getActiveProjectId() {
        return TodoClient.getActiveProjectId();
    }

    /**
     * 设置当前激活项目 ID。
     *
     * @param projectId 项目 ID
     */
    @Override
    public void setActiveProjectId(String projectId) {
        TodoClient.setActiveProjectId(projectId);
    }

    /**
     * 将当前激活项目 ID 同步到服务端。
     *
     * @param projectId 项目 ID
     */
    @Override
    public void sendSetActiveProjectId(String projectId) {
        ClientProjectPackets.sendSetActiveProjectId(projectId);
    }
    @Override
    public boolean isHudVisible() {
        return TodoClient.isHudVisible();
    }

    @Override
    public void setHudVisible(boolean visible) {
        TodoClient.setHudVisible(visible);
        ClientProjectPackets.sendSetHudVisibility(visible);
    }

    /**
     * 发送更新任务请求（个人任务）。
     *
     * @param task 任务对象
     */
    @Override
    public void sendUpdateTask(Task task) {
        ClientTaskPackets.sendUpdateTask(task);
    }

    /**
     * 发送替换个人任务列表请求。
     *
     * @param tasks 任务列表
     */
    @Override
    public void sendReplaceAllTasks(List<Task> tasks) {
        ClientTaskPackets.sendReplaceAllTasks(tasks);
    }

    /**
     * 发送替换团队任务列表请求。
     *
     * @param tasks 团队任务列表
     */
    @Override
    public void sendReplaceTeamTasks(List<Task> tasks) {
        ClientTaskPackets.sendReplaceTeamTasks(tasks);
    }

    /**
     * 发送带基线快照的团队任务合并请求。
     *
     * @param baseTasks 保存发起时的团队任务基线
     * @param tasks 当前提交的团队任务列表
     */
    @Override
    public void sendMergeTeamTasks(List<Task> baseTasks, List<Task> tasks) {
        ClientTaskPackets.sendMergeTeamTasks(baseTasks, tasks);
    }

    /**
     * 请求服务端同步团队任务。
     */
    @Override
    public void requestTeamSync() {
        ClientTaskPackets.requestTeamSync();
    }

    /**
     * 发送申请加入项目请求。
     *
     * @param projectId 项目 ID
     */
    @Override
    public void sendRequestJoinProject(String projectId) {
        ClientProjectPackets.sendRequestJoinProject(projectId);
    }
    @Override
    public void sendHudStarredProjectIds(List<String> projectIds) {
        ClientProjectPackets.sendSetHudStarredProjectIds(projectIds);
    }

    /**
     * 发送删除项目请求。
     *
     * @param projectId 项目 ID
     */
    @Override
    public void sendDeleteProject(String projectId) {
        ClientProjectPackets.sendDeleteProject(projectId);
    }

    /**
     * 发送更新项目信息请求。
     *
     * @param project 项目对象
     */
    @Override
    public void sendUpdateProject(Project project) {
        ClientProjectPackets.sendUpdateProject(project);
    }

    /**
     * 发送移除成员请求。
     *
     * @param projectId  项目 ID
     * @param memberUuid 成员 UUID
     */
    @Override
    public void sendRemoveMember(String projectId, String memberUuid) {
        ClientProjectPackets.sendRemoveMember(projectId, memberUuid);
    }

    /**
     * 发送更新成员角色请求。
     *
     * @param projectId  项目 ID
     * @param memberUuid 成员 UUID
     * @param role       新角色
     */
    @Override
    public void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        ClientProjectPackets.sendUpdateMemberRole(projectId, memberUuid, role);
    }

    /**
     * 发送新增项目请求。
     *
     * @param project 项目对象
     */
    @Override
    public void sendAddProject(Project project) {
        ClientProjectPackets.sendAddProject(project);
    }

    /**
     * 发送添加成员请求。
     *
     * @param projectId   项目 ID
     * @param memberUuid  成员 UUID
     * @param memberName  成员名称
     */
    @Override
    public void sendAddMember(String projectId, String memberUuid, String memberName) {
        ClientProjectPackets.sendAddMember(projectId, memberUuid, memberName);
    }

    /**
     * 判断客户端是否可发送“更新成员角色”相关网络包。
     *
     * @return 是否允许发送
     */
    @Override
    public boolean canSendUpdateMemberRole() {
        return ClientPlayNetworking.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID);
    }
}
