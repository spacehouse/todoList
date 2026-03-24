package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.TodoConstants;
import com.todolist.TodoListForge;
import com.todolist.config.ModConfig;
import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.network.ProjectPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

/**
 * Forge 平台客户端项目相关数据包处理类。
 * 负责处理项目数据的发送与接收，以及本地单人模式下的回退逻辑。
 */
public final class ForgeClientProjectPackets {
    private ForgeClientProjectPackets() {
    }

    /**
     * 注册客户端接收的数据包处理器。
     */
    public static void registerClientPackets() {
        ForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_PROJECTS_ID, (client, handler, buf, responseSender) -> {
            if (handler == null) {
                TodoListForge.LOGGER.info("Skip stale project sync packet with null Forge connection");
                return;
            }
            if (handler != client.getConnection()) {
                TodoListForge.LOGGER.info("Skip stale project sync packet from old Forge connection");
                return;
            }
            List<Project> projects = ProjectPackets.readProjectList(buf);
            String namespaceAtReceive = DataPathProvider.getStorageNamespace();
            client.execute(() -> {
                String currentNamespace = DataPathProvider.getStorageNamespace();
                if (!namespaceAtReceive.equals(currentNamespace)) {
                    TodoListForge.LOGGER.info("Skip stale project sync write due to namespace switch: {} -> {}",
                            namespaceAtReceive, currentNamespace);
                    return;
                }
                handleSyncProjects(projects);
            });
        });
        ForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_HUD_VISIBILITY_ID, (client, handler, buf, responseSender) -> {
            // HUD 可见性改为客户端本地控制，服务端不再作为权威来源
            buf.readBoolean();
        });
        ForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_ACTIVE_PROJECT_ID, (client, handler, buf, responseSender) -> {
            boolean present = buf.readBoolean();
            String projectId = present ? buf.readUtf() : null;
            client.execute(() -> {
                ProjectManager manager = TodoListForge.getProjectManager();
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
     * 处理接收到的项目同步数据。
     *
     * @param projects 从服务端接收到的项目列表
     */
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
            if (ClientBridge.ops().getActiveProjectId() == null || ClientBridge.ops().getActiveProjectId().isEmpty()) {
                String fallback = resolveLocalActiveProjectId(manager);
                if (fallback != null && !fallback.isBlank()) {
                    ClientBridge.ops().setActiveProjectId(fallback);
                    ClientBridge.saveLastActiveProjectId(fallback);
                    ClientBridge.syncHudViewForProject(manager.getProject(fallback));
                    sendSetActiveProjectId(fallback);
                }
            }
            TodoListForge.LOGGER.info("Forge client synced {} projects from server", projects.size());
        } finally {
            TodoListCommon.setProjectSyncInProgress(false);
        }
    }

    /**
     * 发送添加项目请求。
     * 如果服务端不支持该数据包，则尝试本地回退处理。
     */
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

    public static void sendAddProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.ADD_PROJECT_ID)) {
            addProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ForgeNetworkBridge.sendToServer(ProjectPackets.ADD_PROJECT_ID, buf);
    }

    /**
     * 发送更新项目请求。
     */
    public static void sendUpdateProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.UPDATE_PROJECT_ID)) {
            updateProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        ForgeNetworkBridge.sendToServer(ProjectPackets.UPDATE_PROJECT_ID, buf);
    }

    /**
     * 发送删除项目请求。
     */
    public static void sendDeleteProject(String projectId) {
        if (shouldUseLocalProjectFallback(ProjectPackets.DELETE_PROJECT_ID)) {
            deleteProjectLocally(projectId);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        ForgeNetworkBridge.sendToServer(ProjectPackets.DELETE_PROJECT_ID, buf);
    }

    /**
     * 发送添加成员请求。
     */
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

    /**
     * 发送移除成员请求。
     */
    public static void sendRemoveMember(String projectId, String memberUuid) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.REMOVE_MEMBER_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        ForgeNetworkBridge.sendToServer(ProjectPackets.REMOVE_MEMBER_ID, buf);
    }

    /**
     * 发送更新成员角色请求。
     */
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

    /**
     * 发送申请加入项目请求。
     */
    public static void sendRequestJoinProject(String projectId) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.REQUEST_JOIN_PROJECT_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        ForgeNetworkBridge.sendToServer(ProjectPackets.REQUEST_JOIN_PROJECT_ID, buf);
    }

    /**
     * 向服务端请求重新同步项目列表。
     */
    public static void sendRequestSyncProjects() {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ForgeNetworkBridge.sendToServer(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, buf);
    }

    /**
     * 向服务端上报当前激活项目 ID（用于命令默认关联项目等服务端逻辑）。
     *
     * @param projectId 项目 ID，null 表示清空
     */
    public static void sendSetActiveProjectId(String projectId) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.SET_ACTIVE_PROJECT_ID)) {
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
        ForgeNetworkBridge.sendToServer(ProjectPackets.SET_ACTIVE_PROJECT_ID, buf);
    }

    public static void sendSetHudStarredProjectIds(List<String> projectIds) {
        if (!ForgeNetworkBridge.canSend(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID)) {
            applyLocalHudStarredProjectIds(projectIds);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        List<String> ids = projectIds == null ? List.of() : projectIds;
        buf.writeInt(ids.size());
        for (String projectId : ids) {
            buf.writeUtf(projectId == null ? "" : projectId);
        }
        ForgeNetworkBridge.sendToServer(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID, buf);
    }

    /**
     * 本地回退：添加项目。
     */
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

    /**
     * 本地回退：更新项目。
     */
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

    /**
     * 本地回退：删除项目。
     */
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
     * 解析当前本地单人环境对应的服务端玩家对象，用于无网络能力时的本地回退。
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
        return !ForgeNetworkBridge.canSend(channelId);
    }
}
