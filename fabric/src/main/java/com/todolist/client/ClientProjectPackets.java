package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.TodoConstants;
import com.todolist.TodoListMod;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side packet handling for projects
 */
public class ClientProjectPackets {

    /**
     * 注册客户端接收的项目相关网络包处理器。
     */
    public static void registerClientPackets() {
        // SYNC_PROJECTS
        ClientPlayNetworking.registerGlobalReceiver(ProjectPackets.SYNC_PROJECTS_ID, (client, handler, buf, responseSender) -> {
            List<Project> projects = ProjectPackets.readProjectList(buf);
            client.execute(() -> handleSyncProjects(projects));
        });
    }

    /**
     * 将服务端下发的项目列表同步到客户端项目管理器。
     *
     * @param projects 服务端项目列表
     */
    private static void handleSyncProjects(List<Project> projects) {
        TodoListCommon.setProjectSyncInProgress(true);
        ProjectManager manager = TodoListMod.getProjectManager();
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
            TodoListMod.LOGGER.info("Client: Synced {} projects from server", projects.size());
        } finally {
            TodoListCommon.setProjectSyncInProgress(false);
        }
    }

    // Sender methods

    /**
     * 向服务端发送新增项目请求。
     *
     * @param project 项目对象
     */
    public static void sendAddProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.ADD_PROJECT_ID)) {
            addProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ClientPlayNetworking.send(ProjectPackets.ADD_PROJECT_ID, buf);
    }

    /**
     * 向服务端发送更新项目请求。
     *
     * @param project 项目对象
     */
    public static void sendUpdateProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.UPDATE_PROJECT_ID)) {
            updateProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ClientPlayNetworking.send(ProjectPackets.UPDATE_PROJECT_ID, buf);
    }

    /**
     * 向服务端发送删除项目请求。
     *
     * @param projectId 项目 ID
     */
    public static void sendDeleteProject(String projectId) {
        if (shouldUseLocalProjectFallback(ProjectPackets.DELETE_PROJECT_ID)) {
            deleteProjectLocally(projectId);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        ClientPlayNetworking.send(ProjectPackets.DELETE_PROJECT_ID, buf);
    }

    /**
     * 向服务端发送添加成员请求。
     *
     * @param projectId   项目 ID
     * @param memberUuid  成员 UUID
     * @param memberName  成员名称
     */
    public static void sendAddMember(String projectId, String memberUuid, String memberName) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.ADD_MEMBER_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(memberName);
        ClientPlayNetworking.send(ProjectPackets.ADD_MEMBER_ID, buf);
    }

    /**
     * 向服务端发送移除成员请求。
     *
     * @param projectId  项目 ID
     * @param memberUuid 成员 UUID
     */
    public static void sendRemoveMember(String projectId, String memberUuid) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.REMOVE_MEMBER_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        ClientPlayNetworking.send(ProjectPackets.REMOVE_MEMBER_ID, buf);
    }

    /**
     * 向服务端发送更新成员角色请求。
     *
     * @param projectId  项目 ID
     * @param memberUuid 成员 UUID
     * @param role       新角色
     */
    public static void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(role.name());
        ClientPlayNetworking.send(ProjectPackets.UPDATE_MEMBER_ROLE_ID, buf);
    }

    /**
     * 向服务端发送申请加入项目请求。
     *
     * @param projectId 项目 ID
     */
    public static void sendRequestJoinProject(String projectId) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.REQUEST_JOIN_PROJECT_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        ClientPlayNetworking.send(ProjectPackets.REQUEST_JOIN_PROJECT_ID, buf);
    }

    /**
     * 向服务端请求重新同步项目列表。
     */
    public static void sendRequestSyncProjects() {
        if (!ClientPlayNetworking.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ClientPlayNetworking.send(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, buf);
    }

    /**
     * 向服务端上报当前激活项目 ID（用于命令默认关联项目等服务端逻辑）。
     *
     * @param projectId 项目 ID，null 表示清空
     */
    public static void sendSetActiveProjectId(String projectId) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.SET_ACTIVE_PROJECT_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        if (projectId == null || projectId.isEmpty()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(projectId);
        }
        ClientPlayNetworking.send(ProjectPackets.SET_ACTIVE_PROJECT_ID, buf);
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
        TodoListMod.getProjectManager().addProject(project);
        saveProjectsByScope(project.getScope());
    }

    private static void updateProjectLocally(Project project) {
        if (project == null) {
            return;
        }
        ProjectManager manager = TodoListMod.getProjectManager();
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
        ProjectManager manager = TodoListMod.getProjectManager();
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
                TodoListCommon.getProjectStorage().saveTeamProjects(TodoListMod.getProjectManager().getProjectsByScope(Project.Scope.TEAM));
            } else {
                TodoListCommon.getProjectStorage().saveProjects(TodoListMod.getProjectManager().getProjectsByScope(Project.Scope.PERSONAL));
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to save projects in local fallback mode", e);
        }
    }

    private static boolean shouldUseLocalProjectFallback(net.minecraft.resources.ResourceLocation channelId) {
        return !ClientPlayNetworking.canSend(channelId);
    }
}
