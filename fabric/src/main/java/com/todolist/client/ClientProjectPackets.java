package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side packet handling for projects
 */
public class ClientProjectPackets {

    public static void registerClientPackets() {
        // SYNC_PROJECTS
        ClientPlayNetworking.registerGlobalReceiver(ProjectPackets.SYNC_PROJECTS_ID, (client, handler, buf, responseSender) -> {
            List<Project> projects = ProjectPackets.readProjectList(buf);
            client.execute(() -> handleSyncProjects(projects));
        });
    }

    private static void handleSyncProjects(List<Project> projects) {
        ProjectManager manager = TodoListMod.getProjectManager();
        Map<String, Project> incoming = new HashMap<>();
        for (Project p : projects) {
            incoming.put(p.getId(), p);
        }

        for (Project existing : manager.getAllProjects()) {
            if (!incoming.containsKey(existing.getId())) {
                manager.deleteProject(existing.getId());
            }
        }

        for (Project project : projects) {
            if (manager.getProject(project.getId()) == null) {
                manager.addProject(project);
            } else {
                manager.updateProject(project);
            }
        }
        TodoListMod.LOGGER.info("Client: Synced {} projects from server", projects.size());
    }

    // Sender methods

    public static void sendAddProject(Project project) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.ADD_PROJECT_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ClientPlayNetworking.send(ProjectPackets.ADD_PROJECT_ID, buf);
    }

    public static void sendUpdateProject(Project project) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.UPDATE_PROJECT_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ClientPlayNetworking.send(ProjectPackets.UPDATE_PROJECT_ID, buf);
    }

    public static void sendDeleteProject(String projectId) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.DELETE_PROJECT_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(projectId);
        ClientPlayNetworking.send(ProjectPackets.DELETE_PROJECT_ID, buf);
    }

    public static void sendAddMember(String projectId, String memberUuid, String memberName) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.ADD_MEMBER_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(projectId);
        buf.writeString(memberUuid);
        buf.writeString(memberName);
        ClientPlayNetworking.send(ProjectPackets.ADD_MEMBER_ID, buf);
    }

    public static void sendRemoveMember(String projectId, String memberUuid) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.REMOVE_MEMBER_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(projectId);
        buf.writeString(memberUuid);
        ClientPlayNetworking.send(ProjectPackets.REMOVE_MEMBER_ID, buf);
    }

    public static void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(projectId);
        buf.writeString(memberUuid);
        buf.writeString(role.name());
        ClientPlayNetworking.send(ProjectPackets.UPDATE_MEMBER_ROLE_ID, buf);
    }

    public static void sendRequestJoinProject(String projectId) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.REQUEST_JOIN_PROJECT_ID)) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(projectId);
        ClientPlayNetworking.send(ProjectPackets.REQUEST_JOIN_PROJECT_ID, buf);
    }
}
