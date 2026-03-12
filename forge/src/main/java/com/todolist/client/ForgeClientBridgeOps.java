package com.todolist.client;

import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.util.List;

/**
 * Forge 平台的客户端桥接操作实现。
 * 负责将通用的客户端操作请求转发到 Forge 特定的实现或网络层。
 */
public final class ForgeClientBridgeOps implements ClientBridge.ClientOps {
    @Override
    public TaskManager getTeamTaskManager() {
        return ForgeTodoClient.getTeamTaskManager();
    }

    @Override
    public boolean isTeamProjectsEnabled() {
        return ForgeTodoClient.isTeamProjectsEnabled();
    }

    @Override
    public String getActiveProjectId() {
        return ForgeTodoClient.getActiveProjectId();
    }

    @Override
    public void setActiveProjectId(String projectId) {
        ForgeTodoClient.setActiveProjectId(projectId);
    }

    @Override
    public void sendSetActiveProjectId(String projectId) {
        ForgeClientProjectPackets.sendSetActiveProjectId(projectId);
    }
    @Override
    public boolean isHudVisible() {
        return ForgeTodoClient.isHudVisible();
    }

    @Override
    public void setHudVisible(boolean visible) {
        ForgeTodoClient.setHudVisible(visible);
    }

    @Override
    public void sendUpdateTask(Task task) {
        ForgeClientTaskPackets.sendUpdateTask(task);
    }

    @Override
    public void sendReplaceAllTasks(List<Task> tasks) {
        ForgeClientTaskPackets.sendReplaceAllTasks(tasks);
    }

    @Override
    public void sendReplaceTeamTasks(List<Task> tasks) {
        ForgeClientTaskPackets.sendReplaceTeamTasks(tasks);
    }

    @Override
    public void requestTeamSync() {
        ForgeClientTaskPackets.requestTeamSync();
    }

    @Override
    public void sendRequestJoinProject(String projectId) {
        ForgeClientProjectPackets.sendRequestJoinProject(projectId);
    }
    @Override
    public void sendHudStarredProjectIds(List<String> projectIds) {
        ForgeClientProjectPackets.sendSetHudStarredProjectIds(projectIds);
    }

    @Override
    public void sendDeleteProject(String projectId) {
        ForgeClientProjectPackets.sendDeleteProject(projectId);
    }

    @Override
    public void sendUpdateProject(Project project) {
        ForgeClientProjectPackets.sendUpdateProject(project);
    }

    @Override
    public void sendRemoveMember(String projectId, String memberUuid) {
        ForgeClientProjectPackets.sendRemoveMember(projectId, memberUuid);
    }

    @Override
    public void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        ForgeClientProjectPackets.sendUpdateMemberRole(projectId, memberUuid, role);
    }

    @Override
    public void sendAddProject(Project project) {
        ForgeClientProjectPackets.sendAddProject(project);
    }

    @Override
    public void sendAddMember(String projectId, String memberUuid, String memberName) {
        ForgeClientProjectPackets.sendAddMember(projectId, memberUuid, memberName);
    }

    @Override
    public boolean canSendUpdateMemberRole() {
        return ForgeNetworkBridge.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID);
    }
}
