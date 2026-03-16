package com.todolist.client;

import com.todolist.neoforge.network.NeoForgeNetworkBridge;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import java.util.List;

/**
 * NeoForge 平台客户端桥接实现。
 * 负责将通用客户端请求转发到 NeoForge 网络与实现层。
 */
public final class NeoForgeClientBridgeOps implements ClientBridge.ClientOps {
    /**
     * 获取团队任务管理器。
     * @return 团队任务管理器
     */
    @Override
    public TaskManager getTeamTaskManager() {
        return NeoForgeTodoClient.getTeamTaskManager();
    }

    /**
     * 判断是否启用团队项目。
     * @return 是否启用
     */
    @Override
    public boolean isTeamProjectsEnabled() {
        return NeoForgeTodoClient.isTeamProjectsEnabled();
    }

    /**
     * 获取当前活动项目 ID。
     * @return 项目 ID
     */
    @Override
    public String getActiveProjectId() {
        return NeoForgeTodoClient.getActiveProjectId();
    }

    /**
     * 设置当前活动项目 ID。
     * @param projectId 项目 ID
     */
    @Override
    public void setActiveProjectId(String projectId) {
        NeoForgeTodoClient.setActiveProjectId(projectId);
    }

    /**
     * 发送设置活动项目 ID 的请求。
     * @param projectId 项目 ID
     */
    @Override
    public void sendSetActiveProjectId(String projectId) {
        NeoForgeClientProjectPackets.sendSetActiveProjectId(projectId);
    }

    /**
     * 判断 HUD 是否可见。
     * @return 是否可见
     */
    @Override
    public boolean isHudVisible() {
        return NeoForgeTodoClient.isHudVisible();
    }

    /**
     * 设置 HUD 可见性。
     * @param visible 是否可见
     */
    @Override
    public void setHudVisible(boolean visible) {
        NeoForgeTodoClient.setHudVisible(visible);
    }

    /**
     * 发送更新任务请求。
     * @param task 任务
     */
    @Override
    public void sendUpdateTask(Task task) {
        NeoForgeClientTaskPackets.sendUpdateTask(task);
    }

    /**
     * 发送替换全部任务请求。
     * @param tasks 任务列表
     */
    @Override
    public void sendReplaceAllTasks(List<Task> tasks) {
        NeoForgeClientTaskPackets.sendReplaceAllTasks(tasks);
    }

    /**
     * 发送替换团队任务请求。
     * @param tasks 团队任务列表
     */
    @Override
    public void sendReplaceTeamTasks(List<Task> tasks) {
        NeoForgeClientTaskPackets.sendReplaceTeamTasks(tasks);
    }

    /**
     * 请求团队任务同步。
     */
    @Override
    public void requestTeamSync() {
        NeoForgeClientTaskPackets.requestTeamSync();
    }

    /**
     * 发送请求加入项目。
     * @param projectId 项目 ID
     */
    @Override
    public void sendRequestJoinProject(String projectId) {
        NeoForgeClientProjectPackets.sendRequestJoinProject(projectId);
    }

    /**
     * 发送 HUD 星标项目 ID 列表。
     * @param projectIds 项目 ID 列表
     */
    @Override
    public void sendHudStarredProjectIds(List<String> projectIds) {
        NeoForgeClientProjectPackets.sendSetHudStarredProjectIds(projectIds);
    }

    /**
     * 发送删除项目请求。
     * @param projectId 项目 ID
     */
    @Override
    public void sendDeleteProject(String projectId) {
        NeoForgeClientProjectPackets.sendDeleteProject(projectId);
    }

    /**
     * 发送更新项目信息请求。
     * @param project 项目
     */
    @Override
    public void sendUpdateProject(Project project) {
        NeoForgeClientProjectPackets.sendUpdateProject(project);
    }

    /**
     * 发送移除成员请求。
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     */
    @Override
    public void sendRemoveMember(String projectId, String memberUuid) {
        NeoForgeClientProjectPackets.sendRemoveMember(projectId, memberUuid);
    }

    /**
     * 发送更新成员角色请求。
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param role 角色
     */
    @Override
    public void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        NeoForgeClientProjectPackets.sendUpdateMemberRole(projectId, memberUuid, role);
    }

    /**
     * 发送添加项目请求。
     * @param project 项目
     */
    @Override
    public void sendAddProject(Project project) {
        NeoForgeClientProjectPackets.sendAddProject(project);
    }

    /**
     * 发送添加成员请求。
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param memberName 成员名称
     */
    @Override
    public void sendAddMember(String projectId, String memberUuid, String memberName) {
        NeoForgeClientProjectPackets.sendAddMember(projectId, memberUuid, memberName);
    }

    /**
     * 判断是否允许发送角色更新请求。
     * @return 是否允许
     */
    @Override
    public boolean canSendUpdateMemberRole() {
        return NeoForgeNetworkBridge.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID);
    }
}
