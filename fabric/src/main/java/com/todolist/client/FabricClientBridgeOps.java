package com.todolist.client;

import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

public final class FabricClientBridgeOps implements ClientBridge.ClientOps {
    @Override
    public TaskManager getTeamTaskManager() {
        return TodoClient.getTeamTaskManager();
    }

    @Override
    public boolean isTeamProjectsEnabled() {
        return TodoClient.isTeamProjectsEnabled();
    }

    @Override
    public String getActiveProjectId() {
        return TodoClient.getActiveProjectId();
    }

    @Override
    public void setActiveProjectId(String projectId) {
        TodoClient.setActiveProjectId(projectId);
    }

    @Override
    public void sendUpdateTask(Task task) {
        ClientTaskPackets.sendUpdateTask(task);
    }

    @Override
    public void sendReplaceAllTasks(List<Task> tasks) {
        ClientTaskPackets.sendReplaceAllTasks(tasks);
    }

    @Override
    public void sendReplaceTeamTasks(List<Task> tasks) {
        ClientTaskPackets.sendReplaceTeamTasks(tasks);
    }

    @Override
    public void requestTeamSync() {
        ClientTaskPackets.requestTeamSync();
    }

    @Override
    public void sendRequestJoinProject(String projectId) {
        ClientProjectPackets.sendRequestJoinProject(projectId);
    }

    @Override
    public void sendDeleteProject(String projectId) {
        ClientProjectPackets.sendDeleteProject(projectId);
    }

    @Override
    public void sendUpdateProject(Project project) {
        ClientProjectPackets.sendUpdateProject(project);
    }

    @Override
    public void sendRemoveMember(String projectId, String memberUuid) {
        ClientProjectPackets.sendRemoveMember(projectId, memberUuid);
    }

    @Override
    public void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        ClientProjectPackets.sendUpdateMemberRole(projectId, memberUuid, role);
    }

    @Override
    public void sendAddProject(Project project) {
        ClientProjectPackets.sendAddProject(project);
    }

    @Override
    public void sendAddMember(String projectId, String memberUuid, String memberName) {
        ClientProjectPackets.sendAddMember(projectId, memberUuid, memberName);
    }

    @Override
    public boolean canSendUpdateMemberRole() {
        return ClientPlayNetworking.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID);
    }
}
