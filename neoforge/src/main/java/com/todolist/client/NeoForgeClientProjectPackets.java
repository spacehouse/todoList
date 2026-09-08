package com.todolist.client;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.TodoListNeoForge;
import com.todolist.config.ModConfig;
import com.todolist.neoforge.network.NeoForgeNetworkBridge;
import com.todolist.network.ProjectPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * NeoForge 平台客户端项目数据包处理类。
 * 负责处理项目数据的发送、接收与本地回退逻辑。
 */
public final class NeoForgeClientProjectPackets {
    /**
     * 私有构造函数，禁止实例化。
     */
    private NeoForgeClientProjectPackets() {
    }

    /**
     * 注册客户端接收的项目数据包。
     */
    public static void registerClientPackets() {
        NeoForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_PROJECTS_ID, (client, handler, buf, responseSender) -> {
            if (handler == null) {
                TodoListNeoForge.LOGGER.info("Skip stale project sync packet with null NeoForge connection");
                return;
            }
            if (handler != client.getConnection()) {
                TodoListNeoForge.LOGGER.info("Skip stale project sync packet from old NeoForge connection");
                return;
            }
            List<Project> projects = ProjectPackets.readProjectList(buf);
            String namespaceAtReceive = DataPathProvider.getStorageNamespace();
            client.execute(() -> {
                String currentNamespace = DataPathProvider.getStorageNamespace();
                if (!namespaceAtReceive.equals(currentNamespace)) {
                    TodoListNeoForge.LOGGER.info("Skip stale project sync write due to namespace switch: {} -> {}",
                            namespaceAtReceive, currentNamespace);
                    return;
                }
                handleSyncProjects(projects);
            });
        });
        NeoForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_HUD_VISIBILITY_ID, (client, handler, buf, responseSender) -> {
            boolean visible = buf.readBoolean();
            client.execute(() -> applyHudVisibilitySync(visible));
        });
        NeoForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_HUD_STARRED_PROJECT_IDS_ID, (client, handler, buf, responseSender) -> {
            int count = buf.readInt();
            List<String> projectIds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                projectIds.add(buf.readUtf());
            }
            client.execute(() -> ModConfig.getInstance().setHudStarredProjectIds(projectIds));
        });
        NeoForgeNetworkBridge.registerClientReceiver(ProjectPackets.SYNC_ACTIVE_PROJECT_ID, (client, handler, buf, responseSender) -> {
            boolean present = buf.readBoolean();
            String projectId = present ? buf.readUtf() : null;
            client.execute(() -> {
                ProjectManager manager = TodoListNeoForge.getProjectManager();
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
     * 处理来自服务端的项目同步数据。
     *
     * @param projects 项目列表
     */
    private static void handleSyncProjects(List<Project> projects) {
        TodoListCommon.setProjectSyncInProgress(true);
        ProjectManager manager = TodoListNeoForge.getProjectManager();
        try {
            Map<String, Project> incoming = new HashMap<>();
            for (Project project : projects) {
                project.setName(ProjectNameFormatter.normalizeDefaultName(project.getName(), project.getScope()));
                incoming.put(project.getId(), project);
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
            TodoListNeoForge.LOGGER.info("NeoForge client synced {} projects from server", projects.size());
        } finally {
            TodoListCommon.setProjectSyncInProgress(false);
        }
    }

    /**
     * 解析本地可用的活动项目 ID。
     *
     * @param manager 项目管理器
     * @return 项目 ID
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

    /**
     * 应用服务端同步的 HUD 可见性到本地客户端状态。
     *
     * @param visible HUD 是否可见
     */
    private static void applyHudVisibilitySync(boolean visible) {
        NeoForgeTodoClient.setHudVisible(visible);
    }

    /**
     * 发送添加项目请求。
     *
     * @param project 项目
     */
    public static void sendAddProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.ADD_PROJECT_ID)) {
            addProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.ADD_PROJECT_ID, buf);
    }

    /**
     * 发送更新项目请求。
     *
     * @param project 项目
     */
    public static void sendUpdateProject(Project project) {
        if (shouldUseLocalProjectFallback(ProjectPackets.UPDATE_PROJECT_ID)) {
            updateProjectLocally(project);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ProjectPackets.writeProject(buf, project);
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.UPDATE_PROJECT_ID, buf);
    }

    /**
     * 发送删除项目请求。
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
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.DELETE_PROJECT_ID, buf);
    }

    /**
     * 发送添加成员请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param memberName 成员名称
     */
    public static void sendAddMember(String projectId, String memberUuid, String memberName) {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.ADD_MEMBER_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(memberName);
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.ADD_MEMBER_ID, buf);
    }

    /**
     * 发送移除成员请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     */
    public static void sendRemoveMember(String projectId, String memberUuid) {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.REMOVE_MEMBER_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.REMOVE_MEMBER_ID, buf);
    }

    /**
     * 发送更新成员角色请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param role 角色
     */
    public static void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(role.name());
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.UPDATE_MEMBER_ROLE_ID, buf);
    }

    /**
     * 发送请求加入项目。
     *
     * @param projectId 项目 ID
     */
    public static void sendRequestJoinProject(String projectId) {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.REQUEST_JOIN_PROJECT_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.REQUEST_JOIN_PROJECT_ID, buf);
    }

    /**
     * 请求重新同步项目列表。
     */
    public static void sendRequestSyncProjects() {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeClientProjectStateSeed(buf);
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, buf);
    }

    /**
     * 上报当前活动项目 ID。
     *
     * @param projectId 项目 ID，null 表示清空
     */
    public static void sendSetActiveProjectId(String projectId) {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.SET_ACTIVE_PROJECT_ID)) {
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
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.SET_ACTIVE_PROJECT_ID, buf);
    }

    /**
     * 发送 HUD 星标项目 ID 列表。
     *
     * @param projectIds 项目 ID 列表
     */
    public static void sendSetHudStarredProjectIds(List<String> projectIds) {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID)) {
            applyLocalHudStarredProjectIds(projectIds);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        List<String> ids = projectIds == null ? List.of() : projectIds;
        buf.writeInt(ids.size());
        for (String projectId : ids) {
            buf.writeUtf(projectId == null ? "" : projectId);
        }
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID, buf);
    }

    /**
     * 向服务端同步客户端当前的 HUD 显隐状态。
     *
     * @param visible HUD 是否可见
     */
    public static void sendSetHudVisibility(boolean visible) {
        if (!NeoForgeNetworkBridge.canSend(ProjectPackets.SET_HUD_VISIBILITY_ID)) {
            applyLocalHudVisibility(visible);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBoolean(visible);
        NeoForgeNetworkBridge.sendToServer(ProjectPackets.SET_HUD_VISIBILITY_ID, buf);
    }

    /**
     * 将客户端当前项目状态写入请求同步数据包，供服务端首次初始化玩家状态。
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

    /**
     * 本地回退：添加项目。
     *
     * @param project 项目
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
        TodoListNeoForge.getProjectManager().addProject(project);
        saveProjectsByScope(project.getScope());
    }

    /**
     * 本地回退：更新项目。
     *
     * @param project 项目
     */
    private static void updateProjectLocally(Project project) {
        if (project == null) {
            return;
        }
        ProjectManager manager = TodoListNeoForge.getProjectManager();
        Project existing = manager.getProject(project.getId());
        if (existing == null) {
            return;
        }
        manager.updateProject(project);
        saveProjectsByScope(existing.getScope());
    }

    /**
     * 本地回退：删除项目。
     *
     * @param projectId 项目 ID
     */
    private static void deleteProjectLocally(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return;
        }
        ProjectManager manager = TodoListNeoForge.getProjectManager();
        Project existing = manager.getProject(projectId);
        if (existing == null) {
            return;
        }
        manager.deleteProject(projectId);
        saveProjectsByScope(existing.getScope());
    }

    /**
     * 按范围保存项目数据。
     *
     * @param scope 项目范围
     */
    private static void saveProjectsByScope(Project.Scope scope) {
        try {
            if (scope == Project.Scope.TEAM) {
                TodoListCommon.getProjectStorage().saveTeamProjects(TodoListNeoForge.getProjectManager().getProjectsByScope(Project.Scope.TEAM));
            } else {
                TodoListCommon.getProjectStorage().saveProjects(TodoListNeoForge.getProjectManager().getProjectsByScope(Project.Scope.PERSONAL));
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to save projects in local fallback mode", e);
        }
    }

    /**
     * 在本地单人模式下直接把当前激活项目写入集成服务端，保证命令系统可读取到最新状态。
     *
     * @param projectId 项目 ID
     */
    private static void applyLocalActiveProjectId(String projectId) {
        ServerPlayer serverPlayer = resolveLocalServerPlayer();
        if (serverPlayer == null) {
            return;
        }
        var server = serverPlayer.level().getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> ProjectPackets.setActiveProjectId(serverPlayer, projectId));
    }

    /**
     * 在本地单人模式下直接把 HUD 星标项目写入集成服务端，避免命令侧读取到空状态。
     *
     * @param projectIds 项目 ID 列表
     */
    private static void applyLocalHudStarredProjectIds(List<String> projectIds) {
        ServerPlayer serverPlayer = resolveLocalServerPlayer();
        if (serverPlayer == null) {
            return;
        }
        var server = serverPlayer.level().getServer();
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
        var server = serverPlayer.level().getServer();
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

    /**
     * 判断是否需要走本地回退逻辑。
     *
     * @param channelId 通道 ID
     * @return 是否需要回退
     */
    private static boolean shouldUseLocalProjectFallback(net.minecraft.resources.ResourceLocation channelId) {
        return !NeoForgeNetworkBridge.canSend(channelId);
    }
}
