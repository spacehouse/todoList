package com.todolist.client;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.TodoListMod;
import com.todolist.config.ModConfig;
import com.todolist.network.FabricProjectPayload;
import com.todolist.network.ProjectPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理 Fabric 客户端侧项目相关网络包的接收、发送与本地回退逻辑。
 */
public class ClientProjectPackets {

    /**
     * 注册客户端接收的项目相关网络包处理器。
     */
    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(FabricProjectPayload.TYPE, (payload, context) -> {
            ResourceLocation channelId = payload.channel();
            FriendlyByteBuf buf = payload.toBuf();
            Minecraft client = context.client();
            if (channelId.equals(ProjectPackets.SYNC_PROJECTS_ID)) {
                List<Project> projects = ProjectPackets.readProjectList(buf);
                client.execute(() -> handleSyncProjects(projects));
                return;
            }
            if (channelId.equals(ProjectPackets.SYNC_HUD_VISIBILITY_ID)) {
                boolean visible = buf.readBoolean();
                client.execute(() -> applyHudVisibilitySync(visible));
                return;
            }
            if (channelId.equals(ProjectPackets.SYNC_HUD_STARRED_PROJECT_IDS_ID)) {
                int count = buf.readInt();
                List<String> projectIds = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    projectIds.add(buf.readUtf());
                }
                client.execute(() -> ModConfig.getInstance().setHudStarredProjectIds(projectIds));
                return;
            }
            if (channelId.equals(ProjectPackets.SYNC_ACTIVE_PROJECT_ID)) {
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
            }
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
            TodoListMod.LOGGER.info("Client: Synced {} projects from server", projects.size());
        } finally {
            TodoListCommon.setProjectSyncInProgress(false);
        }
    }

    /**
     * 从本地配置与项目状态中解析出可回退的激活项目 ID。
     *
     * @param manager 当前项目管理器
     * @return 可用的项目 ID，不存在则返回 null
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
     * 应用服务端同步过来的 HUD 可见性状态。
     *
     * @param visible HUD 是否可见
     */
    private static void applyHudVisibilitySync(boolean visible) {
        ClientBridge.ops().setHudVisible(visible);
    }

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
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.ADD_PROJECT_ID, buf));
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
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.UPDATE_PROJECT_ID, buf));
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
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.DELETE_PROJECT_ID, buf));
    }

    /**
     * 向服务端发送添加成员请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param memberName 成员名称
     */
    public static void sendAddMember(String projectId, String memberUuid, String memberName) {
        if (!ClientPlayNetworking.canSend(FabricProjectPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(memberName);
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.ADD_MEMBER_ID, buf));
    }

    /**
     * 向服务端发送移除成员请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     */
    public static void sendRemoveMember(String projectId, String memberUuid) {
        if (!ClientPlayNetworking.canSend(FabricProjectPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.REMOVE_MEMBER_ID, buf));
    }

    /**
     * 向服务端发送更新成员角色请求。
     *
     * @param projectId 项目 ID
     * @param memberUuid 成员 UUID
     * @param role 新角色
     */
    public static void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        if (!ClientPlayNetworking.canSend(FabricProjectPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        buf.writeUtf(memberUuid);
        buf.writeUtf(role.name());
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.UPDATE_MEMBER_ROLE_ID, buf));
    }

    /**
     * 向服务端发送申请加入项目请求。
     *
     * @param projectId 项目 ID
     */
    public static void sendRequestJoinProject(String projectId) {
        if (!ClientPlayNetworking.canSend(FabricProjectPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(projectId);
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.REQUEST_JOIN_PROJECT_ID, buf));
    }

    /**
     * 向服务端请求重新同步项目列表。
     */
    public static void sendRequestSyncProjects() {
        if (!ClientPlayNetworking.canSend(FabricProjectPayload.TYPE)) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, buf));
    }

    /**
     * 向服务端上报当前激活项目 ID。
     *
     * @param projectId 项目 ID，null 表示清空
     */
    public static void sendSetActiveProjectId(String projectId) {
        if (!ClientPlayNetworking.canSend(FabricProjectPayload.TYPE)) {
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
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.SET_ACTIVE_PROJECT_ID, buf));
    }

    /**
     * 向服务端上报 HUD 星标项目列表。
     *
     * @param projectIds 星标项目 ID 列表
     */
    public static void sendSetHudStarredProjectIds(List<String> projectIds) {
        if (!ClientPlayNetworking.canSend(FabricProjectPayload.TYPE)) {
            applyLocalHudStarredProjectIds(projectIds);
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        List<String> ids = projectIds == null ? List.of() : projectIds;
        buf.writeInt(ids.size());
        for (String projectId : ids) {
            buf.writeUtf(projectId == null ? "" : projectId);
        }
        ClientPlayNetworking.send(FabricProjectPayload.of(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID, buf));
    }

    /**
     * 在本地回退模式下直接新增项目并写入对应作用域存储。
     *
     * @param project 待新增项目
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
        TodoListMod.getProjectManager().addProject(project);
        saveProjectsByScope(project.getScope());
    }

    /**
     * 在本地回退模式下直接更新项目并保存对应作用域数据。
     *
     * @param project 待更新项目
     */
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

    /**
     * 在本地回退模式下直接删除项目并保存对应作用域数据。
     *
     * @param projectId 待删除项目 ID
     */
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

    /**
     * 按项目作用域保存当前本地项目列表。
     *
     * @param scope 需要写回的项目作用域
     */
    private static void saveProjectsByScope(Project.Scope scope) {
        try {
            if (scope == Project.Scope.TEAM) {
                TodoListCommon.getProjectStorage().saveTeamProjects(
                        TodoListMod.getProjectManager().getProjectsByScope(Project.Scope.TEAM));
            } else {
                TodoListCommon.getProjectStorage().saveProjects(
                        TodoListMod.getProjectManager().getProjectsByScope(Project.Scope.PERSONAL));
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to save projects in local fallback mode", e);
        }
    }

    /**
     * 在本地单人模式下直接把当前激活项目写入集成服务端。
     *
     * @param projectId 当前激活项目 ID
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
     * 在本地单人模式下直接把 HUD 星标项目写入集成服务端。
     *
     * @param projectIds 星标项目 ID 列表
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
     * 解析当前本地单人环境对应的服务端玩家对象。
     *
     * @return 当前集成服务端玩家对象，不存在则返回 null
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
     * 判断当前项目操作是否需要走本地回退逻辑。
     *
     * @param channelId 目标业务频道 ID
     * @return 无法发送网络包时返回 true
     */
    private static boolean shouldUseLocalProjectFallback(ResourceLocation channelId) {
        return !ClientPlayNetworking.canSend(FabricProjectPayload.TYPE);
    }
}
