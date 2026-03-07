package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.TodoConstants;
import com.todolist.TodoListForge;
import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;

public final class ForgeClientProjectPackets {
    private ForgeClientProjectPackets() {
    }

    public static void registerClientPackets() {
        ForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_PROJECTS_ID, (client, handler, buf, responseSender) -> {
            List<Project> projects = ProjectPackets.readProjectList(buf);
            client.execute(() -> handleSyncProjects(projects));
        });
    }

    private static void handleSyncProjects(List<Project> projects) {
        TodoListCommon.setProjectSyncInProgress(true);
        ProjectManager manager = TodoListForge.getProjectManager();
        try {
            Map<String, Project> incoming = new HashMap<>();
            for (Project p : projects) {
                p.setName(ProjectNameFormatter.normalizeDefaultName(p.getName(), p.getScope()));
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
            ClientBridge.getActiveProject(manager);
            TodoListForge.LOGGER.info("Forge client synced {} projects from server", projects.size());
        } finally {
            TodoListCommon.setProjectSyncInProgress(false);
        }
    }

    public static void sendAddProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.ADD_PROJECT_ID)) {
            addProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ForgeNetworkBridge.sendToServer(ProjectPackets.ADD_PROJECT_ID, buf);
    }

    public static void sendUpdateProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.UPDATE_PROJECT_ID)) {
            updateProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ForgeNetworkBridge.sendToServer(ProjectPackets.UPDATE_PROJECT_ID, buf);
    }

    public static void sendDeleteProject(String projectId) {
        if (shouldUseLocalProjectFallback(ProjectPackets.DELETE_PROJECT_ID)) {
            deleteProjectLocally(projectId);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        ForgeNetworkBridge.sendToServer(ProjectPackets.DELETE_PROJECT_ID, buf);
    }

    public static void sendAddMember(String projectId, String memberUuid, String memberName) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.ADD_MEMBER_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(memberName);
        ForgeNetworkBridge.sendToServer(ProjectPackets.ADD_MEMBER_ID, buf);
    }

    public static void sendRemoveMember(String projectId, String memberUuid) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.REMOVE_MEMBER_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        ForgeNetworkBridge.sendToServer(ProjectPackets.REMOVE_MEMBER_ID, buf);
    }

    public static void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(role.name());
        ForgeNetworkBridge.sendToServer(ProjectPackets.UPDATE_MEMBER_ROLE_ID, buf);
    }

    public static void sendRequestJoinProject(String projectId) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.REQUEST_JOIN_PROJECT_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        ForgeNetworkBridge.sendToServer(ProjectPackets.REQUEST_JOIN_PROJECT_ID, buf);
    }

    private static void addProjectLocally(Project project) {
        if (project == null) {
            return;
        }
        if (project.getScope() == Project.Scope.TEAM) {
            project.setScope(Project.Scope.PERSONAL);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            String playerUuid = minecraft.player.getStringUUID();
            project.setOwnerUuid(playerUuid);
            project.addMember(playerUuid, Project.ProjectRole.PROJECT_MANAGER, minecraft.player.getName().getString());
        }
        TodoListForge.getProjectManager().addProject(project);
        saveProjectsByScope(project.getScope());
    }

    private static void updateProjectLocally(Project project) {
        if (project == null) {
            return;
        }
        ProjectManager manager = TodoListForge.getProjectManager();
        Project existing = manager.getProject(project.getId());
        if (existing == null) {
            return;
        }
        manager.updateProject(project);
        saveProjectsByScope(existing.getScope());
    }

    private static void deleteProjectLocally(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return;
        }
        ProjectManager manager = TodoListForge.getProjectManager();
        Project existing = manager.getProject(projectId);
        if (existing == null) {
            return;
        }
        manager.deleteProject(projectId);
        saveProjectsByScope(existing.getScope());
    }

    private static void saveProjectsByScope(Project.Scope scope) {
        try {
            if (scope == Project.Scope.TEAM) {
                TodoListCommon.getProjectStorage().saveTeamProjects(TodoListForge.getProjectManager().getProjectsByScope(Project.Scope.TEAM));
            } else {
                TodoListCommon.getProjectStorage().saveProjects(TodoListForge.getProjectManager().getProjectsByScope(Project.Scope.PERSONAL));
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to save projects in local fallback mode", e);
        }
    }

    private static boolean shouldUseLocalProjectFallback(net.minecraft.resources.ResourceLocation channelId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.isLocalServer()) {
            return true;
        }
        return !ForgeNetworkBridge.canSend(channelId);
    }
}
