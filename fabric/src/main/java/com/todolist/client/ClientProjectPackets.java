package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.TodoConstants;
import com.todolist.TodoListMod;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
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
        ClientPlayNetworking.registerGlobalReceiver(ProjectPackets.SYNC_HUD_VISIBILITY_ID, (client, handler, buf, responseSender) -> {
            // HUD 可见性改为客户端本地控制，服务端不再作为权威来源
            boolean visible = buf.readBoolean();
            client.execute(() -> applyHudVisibilitySync(visible));
        });
        ClientPlayNetworking.registerGlobalReceiver(ProjectPackets.SYNC_HUD_STARRED_PROJECT_IDS_ID, (client, handler, buf, responseSender) -> {
            int count = buf.readInt();
            List<String> projectIds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                projectIds.add(buf.readUtf());
            }
            client.execute(() -> ModConfig.getInstance().setHudStarredProjectIds(projectIds));
        });
        ClientPlayNetworking.registerGlobalReceiver(ProjectPackets.SYNC_ACTIVE_PROJECT_ID, (client, handler, buf, responseSender) -> {
            boolean present = buf.readBoolean();
            String projectId = present ? buf.readUtf() : null;
            client.execute(() -> {
                ProjectManager manager = TodoListMod.getProjectManager();
                String resolvedProjectId = projectId;
                boolean shouldResend = false;
                if (!present) {
                    String fallback = resolveLocalActiveProjectId(manager);
                    if (fallback != null && !fallback.isBlank()) {
                        resolvedProjectId = fallback;
                        shouldResend = true;
                    }
                }
                ClientBridge.ops().setActiveProjectId(resolvedProjectId);
                ClientBridge.saveLastActiveProjectId(resolvedProjectId);
                ClientBridge.syncHudViewForProject(resolvedProjectId == null ? null : manager.getProject(resolvedProjectId));
                if (shouldResend) {
                    sendSetActiveProjectId(resolvedProjectId);
                }
            });
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
            if (ClientBridge.ops().getActiveProjectId() == null || ClientBridge.ops().getActiveProjectId().isEmpty()) {
                String fallback = resolveLocalActiveProjectId(manager);
                if (fallback != null && !fallback.isBlank()) {
                    ClientBridge.ops().setActiveProjectId(fallback);
                    ClientBridge.saveLastActiveProjectId(fallback);
                    ClientBridge.syncHudViewForProject(manager.getProject(fallback));
                    sendSetActiveProjectId(fallback);
                }
            }
            TodoListMod.LOGGER.info("Client: Synced {} projects from server", projects.size());
        } finally {
            TodoListCommon.setProjectSyncInProgress(false);
        }
    }

    private static String resolveLocalActiveProjectId(ProjectManager manager) {
        if (manager == null) {
            return null;
        }
        String lastActive = ModConfig.getInstance().getLastActiveProjectId();
        if (lastActive == null || lastActive.isBlank()) {
            return null;
        }
        Project project = manager.getProject(lastActive);
        if (project == null) {
            return null;
        }
        if (project.getScope() == Project.Scope.TEAM && !ClientBridge.ops().isTeamProjectsEnabled()) {
            return null;
        }
        return project.getId();
    }

    /**
     * Apply the HUD visibility synchronized by server-side commands to the local client state.
     *
     * @param visible whether the HUD should be visible on the client
     */
    private static void applyHudVisibilitySync(boolean visible) {
        TodoClient.setHudVisible(visible);
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
        writeClientProjectStateSeed(buf);
        ClientPlayNetworking.send(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, buf);
    }

    /**
     * 向服务端上报当前激活项目 ID（用于命令默认关联项目等服务端逻辑）。
     *
     * @param projectId 项目 ID，null 表示清空
     */
    public static void sendSetActiveProjectId(String projectId) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.SET_ACTIVE_PROJECT_ID)) {
            applyLocalActiveProjectId(projectId);
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

    public static void sendSetHudStarredProjectIds(List<String> projectIds) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID)) {
            applyLocalHudStarredProjectIds(projectIds);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        List<String> ids = projectIds == null ? List.of() : projectIds;
        buf.writeInt(ids.size());
        for (String projectId : ids) {
            buf.writeUtf(projectId == null ? "" : projectId);
        }
        ClientPlayNetworking.send(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID, buf);
    }

    /**
     * 向服务端同步客户端当前的 HUD 显隐状态。
     *
     * @param visible HUD 是否可见
     */
    public static void sendSetHudVisibility(boolean visible) {
        if (!ClientPlayNetworking.canSend(ProjectPackets.SET_HUD_VISIBILITY_ID)) {
            applyLocalHudVisibility(visible);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBoolean(visible);
        ClientPlayNetworking.send(ProjectPackets.SET_HUD_VISIBILITY_ID, buf);
    }

    /**
     * 将客户端当前项目状态写入 requestSyncProjects 请求，供服务端首次初始化玩家状态。
     *
     * @param buf 待写入的网络缓冲区
     */
    private static void writeClientProjectStateSeed(FriendlyByteBuf buf) {
        String activeProjectId = ClientBridge.ops().getActiveProjectId();
        if (activeProjectId == null || activeProjectId.isBlank()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(activeProjectId.trim());
        }
        List<String> starredProjectIds = ModConfig.getInstance().getHudStarredProjectIds();
        buf.writeInt(starredProjectIds.size());
        for (String projectId : starredProjectIds) {
            buf.writeUtf(projectId == null ? "" : projectId);
        }
        buf.writeBoolean(ClientBridge.ops().isHudVisible());
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

    /**
     * 在本地单人模式下直接把当前激活项目写入集成服务端，保证命令系统可读取到最新状态。
     */
    private static void applyLocalActiveProjectId(String projectId) {
        ServerPlayer serverPlayer = resolveLocalServerPlayer();
        if (serverPlayer == null) {
            return;
        }
        var server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> ProjectPackets.setActiveProjectId(serverPlayer, projectId));
    }

    /**
     * 在本地单人模式下直接把 HUD 星标项目写入集成服务端，避免命令侧读取到空状态。
     */
    private static void applyLocalHudStarredProjectIds(List<String> projectIds) {
        ServerPlayer serverPlayer = resolveLocalServerPlayer();
        if (serverPlayer == null) {
            return;
        }
        var server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        List<String> ids = projectIds == null ? List.of() : List.copyOf(projectIds);
        server.execute(() -> ProjectPackets.setHudStarredProjectIds(serverPlayer, ids));
    }

    /**
     * 在本地单人环境中直接把 HUD 显隐状态写回集成服务端。
     *
     * @param visible HUD 是否可见
     */
    private static void applyLocalHudVisibility(boolean visible) {
        ServerPlayer serverPlayer = resolveLocalServerPlayer();
        if (serverPlayer == null) {
            return;
        }
        var server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> ProjectPackets.setHudVisible(serverPlayer, visible));
    }

    /**
     * 解析当前本地单人环境对应的服务端玩家对象，用于无网络能力时的本地回退。
     *
     * @return 对应的服务端玩家；不存在时返回 null
     */
    private static ServerPlayer resolveLocalServerPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || !minecraft.isLocalServer()) {
            return null;
        }
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return null;
        }
        return server.getPlayerList().getPlayer(minecraft.player.getUUID());
    }

    private static boolean shouldUseLocalProjectFallback(net.minecraft.resources.ResourceLocation channelId) {
        return !ClientPlayNetworking.canSend(channelId);
    }
}
