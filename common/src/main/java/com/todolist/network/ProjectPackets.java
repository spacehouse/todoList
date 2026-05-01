package com.todolist.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.project.ProjectStorage;
import com.todolist.project.ProjectSaveDebouncer;
import com.todolist.storage.StorageFailureNotifier;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network packet handling for project synchronization
 */
public class ProjectPackets {
    // Packet IDs
    public static final ResourceLocation SYNC_PROJECTS_ID = new ResourceLocation(TodoConstants.MOD_ID, "sync_projects");
    public static final ResourceLocation ADD_PROJECT_ID = new ResourceLocation(TodoConstants.MOD_ID, "add_project");
    public static final ResourceLocation UPDATE_PROJECT_ID = new ResourceLocation(TodoConstants.MOD_ID, "update_project");
    public static final ResourceLocation DELETE_PROJECT_ID = new ResourceLocation(TodoConstants.MOD_ID, "delete_project");
    public static final ResourceLocation ADD_MEMBER_ID = new ResourceLocation(TodoConstants.MOD_ID, "add_member");
    public static final ResourceLocation REMOVE_MEMBER_ID = new ResourceLocation(TodoConstants.MOD_ID, "remove_member");
    public static final ResourceLocation UPDATE_MEMBER_ROLE_ID = new ResourceLocation(TodoConstants.MOD_ID, "update_member_role");
    public static final ResourceLocation REQUEST_JOIN_PROJECT_ID = new ResourceLocation(TodoConstants.MOD_ID, "request_join_project");
    public static final ResourceLocation REQUEST_SYNC_PROJECTS_ID = new ResourceLocation(TodoConstants.MOD_ID, "request_sync_projects");
    public static final ResourceLocation SET_ACTIVE_PROJECT_ID = new ResourceLocation(TodoConstants.MOD_ID, "set_active_project");
    public static final ResourceLocation SYNC_ACTIVE_PROJECT_ID = new ResourceLocation(TodoConstants.MOD_ID, "sync_active_project");
    public static final ResourceLocation SET_HUD_STARRED_PROJECT_IDS_ID = new ResourceLocation(TodoConstants.MOD_ID, "set_hud_starred_project_ids");
    public static final ResourceLocation SET_HUD_VISIBILITY_ID = new ResourceLocation(TodoConstants.MOD_ID, "set_hud_visibility");
    public static final ResourceLocation SYNC_HUD_STARRED_PROJECT_IDS_ID = new ResourceLocation(TodoConstants.MOD_ID, "sync_hud_starred_project_ids");
    public static final ResourceLocation SYNC_HUD_VISIBILITY_ID = new ResourceLocation(TodoConstants.MOD_ID, "sync_hud_visibility");
    private static volatile TaskPackets.ServerPacketSender serverPacketSender = (player, channelId, buf) -> { };
    private static final ConcurrentHashMap<String, String> playerActiveProjectIdMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<String>> playerHudStarredProjectIdsMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> playerHudVisibilityMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> pendingJoinRequestMap = new ConcurrentHashMap<>();

    public static void setServerPacketSender(TaskPackets.ServerPacketSender sender) {
        serverPacketSender = sender == null ? (player, channelId, buf) -> { } : sender;
    }

    public static void onAddProjectPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        Project project;
        try {
            project = readProject(buf);
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(ADD_PROJECT_ID.toString(), ex);
            return;
        }
        server.execute(() -> handleAddProject(server, player, project));
    }

    public static void onUpdateProjectPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        Project project;
        try {
            project = readProject(buf);
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(UPDATE_PROJECT_ID.toString(), ex);
            return;
        }
        server.execute(() -> handleUpdateProject(server, player, project));
    }

    public static void onDeleteProjectPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        String projectId;
        try {
            projectId = PacketGuards.readString(buf, "projectId");
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(DELETE_PROJECT_ID.toString(), ex);
            return;
        }
        server.execute(() -> handleDeleteProject(server, player, projectId));
    }

    public static void onAddMemberPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        String projectId;
        String memberUuidOrName;
        String memberUuid = "";
        String memberName = "";
        try {
            projectId = PacketGuards.readString(buf, "projectId");
            memberUuidOrName = PacketGuards.readString(buf, "memberUuidOrName");
            if (buf.readableBytes() > 0) {
                memberUuid = memberUuidOrName;
                memberName = PacketGuards.readString(buf, "memberName");
            } else {
                memberName = memberUuidOrName;
            }
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(ADD_MEMBER_ID.toString(), ex);
            return;
        }
        String finalMemberUuid = memberUuid;
        String finalMemberName = memberName;
        server.execute(() -> handleAddMember(server, player, projectId, finalMemberUuid, finalMemberName));
    }

    public static void onRemoveMemberPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        String projectId;
        String memberUuid;
        try {
            projectId = PacketGuards.readString(buf, "projectId");
            memberUuid = PacketGuards.readString(buf, "memberUuid");
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(REMOVE_MEMBER_ID.toString(), ex);
            return;
        }
        server.execute(() -> handleRemoveMember(server, player, projectId, memberUuid));
    }

    public static void onUpdateMemberRolePacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        String projectId;
        String memberUuid;
        String roleStr;
        try {
            projectId = PacketGuards.readString(buf, "projectId");
            memberUuid = PacketGuards.readString(buf, "memberUuid");
            roleStr = PacketGuards.readString(buf, "role");
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(UPDATE_MEMBER_ROLE_ID.toString(), ex);
            return;
        }
        server.execute(() -> handleUpdateMemberRole(server, player, projectId, memberUuid, roleStr));
    }

    public static void onRequestJoinProjectPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        String projectId;
        try {
            projectId = PacketGuards.readString(buf, "projectId");
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(REQUEST_JOIN_PROJECT_ID.toString(), ex);
            return;
        }
        server.execute(() -> requestJoinProject(server, player, projectId));
    }

    /**
     * 客户端主动请求服务端重新同步项目列表。
     */
    public static void onRequestSyncProjectsPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        ClientProjectStateSeed seed;
        try {
            seed = readClientProjectStateSeed(buf);
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(REQUEST_SYNC_PROJECTS_ID.toString(), ex);
            return;
        }
        server.execute(() -> {
            initializePlayerProjectStateIfMissing(player, seed);
            restorePlayerProjectState(server, player);
            syncProjectsToPlayer(player);
            syncHudVisibilityToPlayer(player, isHudVisible(player));
            syncHudStarredProjectIdsToPlayer(player, getHudStarredProjectIds(player));
            syncActiveProjectIdToPlayer(player, getActiveProjectId(player));
        });
    }

    public static void onSetHudStarredProjectIdsPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        List<String> projectIds = new ArrayList<>();
        try {
            int count = PacketGuards.readBoundedCount(buf, PacketGuards.MAX_PROJECT_LIST_SIZE, "projectIds");
            for (int i = 0; i < count; i++) {
                projectIds.add(PacketGuards.readString(buf, "projectId"));
            }
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(SET_HUD_STARRED_PROJECT_IDS_ID.toString(), ex);
            return;
        }
        server.execute(() -> {
            if (player == null) {
                return;
            }
            setHudStarredProjectIds(player, projectIds);
        });
    }

    /**
     * 处理客户端主动上报的 HUD 显隐状态。
     *
     * @param server 当前服务端
     * @param player 当前玩家
     * @param buf 网络缓冲区
     */
    public static void onSetHudVisibilityPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        boolean visible;
        try {
            visible = buf.readBoolean();
        } catch (RuntimeException ex) {
            PacketGuards.logDrop(SET_HUD_VISIBILITY_ID.toString(), ex);
            return;
        }
        server.execute(() -> setHudVisible(player, visible));
    }

    /**
     * 处理客户端上报的当前激活项目 ID，用于服务端侧命令默认关联项目。
     *
     * @param server 当前服务端
     * @param player 当前玩家
     * @param buf 网络缓冲区
     */
    public static void onSetActiveProjectPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        boolean present;
        String projectId = null;
        try {
            present = buf.readBoolean();
            if (present) {
                projectId = PacketGuards.readString(buf, "projectId");
            }
        } catch (IllegalArgumentException ex) {
            PacketGuards.logDrop(SET_ACTIVE_PROJECT_ID.toString(), ex);
            return;
        }
        String finalProjectId = projectId;
        server.execute(() -> {
            if (player == null) {
                return;
            }
            String uuid = player.getStringUUID();
            if (!present || finalProjectId == null || finalProjectId.isBlank()) {
                setActiveProjectId(player, null);
                return;
            }
            setActiveProjectId(player, finalProjectId.trim());
        });
    }

    /**
     * 获取服务端记录的玩家当前激活项目 ID（可能为 null）。
     */
    public static void setActiveProjectId(ServerPlayer player, String projectId) {
        if (player == null) {
            return;
        }
        String uuid = player.getStringUUID();
        if (projectId == null || projectId.isBlank()) {
            playerActiveProjectIdMap.remove(uuid);
            persistPlayerProjectState(player);
            syncActiveProjectIdToPlayer(player, null);
            return;
        }
        Project project = TodoListCommon.getProjectManager().getProject(projectId.trim());
        if (project == null) {
            playerActiveProjectIdMap.remove(uuid);
            persistPlayerProjectState(player);
            syncActiveProjectIdToPlayer(player, null);
            return;
        }
        if (project.getScope() == Project.Scope.TEAM && isSingleplayerServer(player.getServer())) {
            playerActiveProjectIdMap.remove(uuid);
            persistPlayerProjectState(player);
            syncActiveProjectIdToPlayer(player, null);
            return;
        }
        playerActiveProjectIdMap.put(uuid, projectId.trim());
        persistPlayerProjectState(player);
        syncActiveProjectIdToPlayer(player, projectId.trim());
    }

    public static String getActiveProjectId(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        return playerActiveProjectIdMap.get(player.getStringUUID());
    }

    public static List<String> getHudStarredProjectIds(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        List<String> ids = playerHudStarredProjectIdsMap.get(player.getStringUUID());
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    /**
     * 设置服务端记录的玩家 HUD 星标项目列表，并执行与网络包处理一致的净化逻辑。
     */
    public static void setHudStarredProjectIds(ServerPlayer player, List<String> projectIds) {
        if (player == null) {
            return;
        }
        List<String> sanitizedProjectIds = sanitizeProjectIds(projectIds);
        if (sanitizedProjectIds.isEmpty()) {
            playerHudStarredProjectIdsMap.remove(player.getStringUUID());
        } else {
            playerHudStarredProjectIdsMap.put(player.getStringUUID(), sanitizedProjectIds);
        }
        persistPlayerProjectState(player);
        syncHudStarredProjectIdsToPlayer(player, sanitizedProjectIds);
    }

    public static boolean isHudVisible(ServerPlayer player) {
        if (player == null) {
            return true;
        }
        return playerHudVisibilityMap.getOrDefault(player.getStringUUID(), true);
    }

    public static void setHudVisible(ServerPlayer player, boolean visible) {
        if (player == null) {
            return;
        }
        if (visible) {
            playerHudVisibilityMap.remove(player.getStringUUID());
        } else {
            playerHudVisibilityMap.put(player.getStringUUID(), false);
        }
        persistPlayerProjectState(player);
        syncHudVisibilityToPlayer(player, visible);
    }

    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        server.execute(() -> {
            ensureDefaultTeamProjectOwner(server, player);
            cachePlayerNameForTeamProjects(server, player);
            restorePlayerProjectState(server, player);
            syncProjectsToPlayer(player);
            syncHudVisibilityToPlayer(player, isHudVisible(player));
            syncHudStarredProjectIdsToPlayer(player, getHudStarredProjectIds(player));
            syncActiveProjectIdToPlayer(player, playerActiveProjectIdMap.get(player.getStringUUID()));
        });
    }

    /**
     * 判断当前服务端是否处于“团队项目不可用”的本地单人状态。
     * 专用服务器始终可用；本地集成服仅在未发布局域网时不可用。
     */
    private static boolean isSingleplayerServer(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        if (server.isDedicatedServer()) {
            return false;
        }
        return !server.isPublished();
    }

    private static void ensureDefaultTeamProjectOwner(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        if (isSingleplayerServer(server)) {
            return;
        }
        if (!player.hasPermissions(2)) {
            return;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project defaultTeam = manager.getProject(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID);
        if (defaultTeam == null || defaultTeam.getScope() != Project.Scope.TEAM || !defaultTeam.isDefaultTeamProject()) {
            return;
        }
        String ownerUuid = defaultTeam.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isEmpty()) {
            if (defaultTeam.getMemberRole(ownerUuid) == null) {
                defaultTeam.addMember(ownerUuid, Project.ProjectRole.PROJECT_MANAGER);
                manager.updateProject(defaultTeam);
                saveProjects(server, Project.Scope.TEAM);
                broadcastProjects(server);
            }
            return;
        }
        String myUuid = player.getStringUUID();
        defaultTeam.setOwnerUuid(myUuid);
        defaultTeam.addMember(myUuid, Project.ProjectRole.PROJECT_MANAGER, player.getName().getString());
        manager.updateProject(defaultTeam);
        saveProjects(server, Project.Scope.TEAM);
        broadcastProjects(server);
    }

    private static void cachePlayerNameForTeamProjects(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        String uuid = player.getStringUUID();
        String name = player.getName().getString();
        boolean changed = false;
        for (Project project : manager.getProjectsByScope(Project.Scope.TEAM)) {
            if (project == null) {
                continue;
            }
            boolean relevant = uuid.equals(project.getOwnerUuid()) || project.getMemberRole(uuid) != null;
            if (!relevant) {
                continue;
            }
            String existingName = project.getMemberName(uuid);
            if (existingName == null || existingName.isEmpty() || !existingName.equals(name)) {
                project.setMemberName(uuid, name);
                manager.updateProject(project);
                changed = true;
            }
        }
        if (changed) {
            saveProjects(server, Project.Scope.TEAM);
        }
    }

    private static void handleAddProject(MinecraftServer server, ServerPlayer player, Project project) {
        // Validation
        if (project.getName() == null || project.getName().isEmpty()) {
            return;
        }

        String playerUuid = player.getStringUUID();
        if (project.getScope() == Project.Scope.TEAM) {
            project.setOwnerUuid(playerUuid);
            project.getMembers().clear();
            project.getMemberNames().clear();
        } else if (project.getOwnerUuid() == null) {
            project.setOwnerUuid(playerUuid);
        }

        project.addMember(playerUuid, Project.ProjectRole.PROJECT_MANAGER, player.getName().getString());

        ProjectManager manager = TodoListCommon.getProjectManager();
        manager.addProject(project);

        // Save
        saveProjects(server, project.getScope());

        // Sync
        if (project.getScope() == Project.Scope.PERSONAL) {
            syncProjectsToPlayer(player);
        } else {
            broadcastProjects(server);
        }
        
        TodoConstants.LOGGER.info("Player {} added project: {}", player.getName().getString(), project.getName());
    }

    private static void handleUpdateProject(MinecraftServer server, ServerPlayer player, Project incomingProject) {
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project existingProject = manager.getProject(incomingProject.getId());

        if (existingProject == null) {
            return;
        }

        if (existingProject.getScope() == Project.Scope.PERSONAL) {
            String owner = existingProject.getOwnerUuid();
            if (owner != null && !owner.isEmpty() && !owner.equals(player.getStringUUID())) {
                TodoConstants.LOGGER.warn("Player {} tried to update personal project {} without permission", player.getName().getString(), existingProject.getId());
                return;
            }
        } else {
            Role role = getRole(player, existingProject);
            Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
            if (!PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx)) {
                TodoConstants.LOGGER.warn("Player {} tried to update project {} without permission", player.getName().getString(), existingProject.getId());
                return;
            }
        }

        // Update fields
        existingProject.setName(incomingProject.getName());
        existingProject.setColor(incomingProject.getColor());
        existingProject.setAllowMemberCreate(incomingProject.isAllowMemberCreate());
        existingProject.setAllowAllPlayersClaimComplete(incomingProject.isAllowAllPlayersClaimComplete());

        manager.updateProject(existingProject);

        // Save
        saveProjects(server, existingProject.getScope());

        // Sync
        if (existingProject.getScope() == Project.Scope.PERSONAL) {
            syncProjectsToPlayer(player);
        } else {
            broadcastProjects(server);
        }
        
        TodoConstants.LOGGER.info("Player {} updated project: {}", player.getName().getString(), existingProject.getName());
    }

    private static void handleDeleteProject(MinecraftServer server, ServerPlayer player, String projectId) {
        if (ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID.equals(projectId) ||
            ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID.equals(projectId)) {
             TodoConstants.LOGGER.warn("Player {} tried to delete default project {}", player.getName().getString(), projectId);
             return;
        }

        ProjectManager manager = TodoListCommon.getProjectManager();
        Project existingProject = manager.getProject(projectId);

        if (existingProject == null) {
            return;
        }

        if (existingProject.getScope() == Project.Scope.PERSONAL) {
            String owner = existingProject.getOwnerUuid();
            if (owner != null && !owner.isEmpty() && !owner.equals(player.getStringUUID())) {
                TodoConstants.LOGGER.warn("Player {} tried to delete personal project {} without permission", player.getName().getString(), projectId);
                return;
            }
        } else {
            Role role = getRole(player, existingProject);
            Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
            if (!PermissionCenter.canPerform(Operation.DELETE_PROJECT, role, ctx)) {
                TodoConstants.LOGGER.warn("Player {} tried to delete project {} without permission", player.getName().getString(), projectId);
                return;
            }
        }

        clearPendingJoinRequestsForProject(projectId);
        manager.deleteProject(projectId);
        purgeDeletedProjectTasks(existingProject.getScope(), projectId, player);

        if (existingProject.getScope() == Project.Scope.PERSONAL) {
            saveProjects(server, Project.Scope.PERSONAL);
            syncProjectsToPlayer(player);
        } else {
            saveProjects(server, existingProject.getScope());
            broadcastProjects(server);
            TaskPackets.broadcastTeamTasks(server);
        }
        
        TodoConstants.LOGGER.info("Player {} deleted project: {}", player.getName().getString(), projectId);
    }

    /**
     * 按项目范围从存储中硬删除被删项目关联的任务。
     */
    private static void purgeDeletedProjectTasks(Project.Scope scope, String projectId, ServerPlayer player) {
        if (scope == null || projectId == null || projectId.isEmpty() || player == null) {
            return;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        if (scope == Project.Scope.PERSONAL) {
            purgeDeletedProjectTasksInPlayerFile(storage, player, projectId);
            purgeDeletedProjectTasksInSingleFile(storage, projectId);
            return;
        }
        if (scope == Project.Scope.TEAM) {
            purgeDeletedProjectTasksInTeamFile(storage, projectId);
        }
    }

    /**
     * 在玩家任务文件中删除指定项目的任务。
     */
    private static void purgeDeletedProjectTasksInPlayerFile(TaskStorage storage, ServerPlayer player, String projectId) {
        try {
            List<Task> tasks = storage.loadPlayerTasks(player.getUUID());
            if (removeTasksByProjectId(tasks, projectId)) {
                storage.savePlayerTasks(player.getUUID(), tasks);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to purge deleted project tasks in player task file, projectId={}", projectId, e);
        }
    }

    /**
     * 在单文件任务存储中删除指定项目的任务。
     */
    private static void purgeDeletedProjectTasksInSingleFile(TaskStorage storage, String projectId) {
        try {
            List<Task> tasks = storage.loadTasks();
            if (removeTasksByProjectId(tasks, projectId)) {
                storage.saveTasks(tasks);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to purge deleted project tasks in local task file, projectId={}", projectId, e);
        }
    }

    /**
     * 在团队任务文件中删除指定项目的任务。
     */
    private static void purgeDeletedProjectTasksInTeamFile(TaskStorage storage, String projectId) {
        try {
            List<Task> tasks = storage.loadTeamTasks();
            if (removeTasksByProjectId(tasks, projectId)) {
                storage.saveTeamTasks(tasks);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to purge deleted project tasks in team task file, projectId={}", projectId, e);
        }
    }

    /**
     * 从任务集合中删除关联到指定项目的任务。
     */
    private static boolean removeTasksByProjectId(List<Task> tasks, String projectId) {
        int beforeSize = tasks.size();
        tasks.removeIf(task -> task != null && task.belongsToProject(projectId));
        return beforeSize != tasks.size();
    }

    private static void handleAddMember(MinecraftServer server, ServerPlayer player, String projectId, String memberUuid, String memberName) {
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(projectId);
        
        if (project == null) return;
        
        Role role = getRole(player, project);
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        if (!PermissionCenter.canPerform(Operation.ADD_MEMBER, role, ctx)) {
            return;
        }

        if (memberUuid != null && !memberUuid.isEmpty()) {
            if (project.getMembers().containsKey(memberUuid)) {
                return;
            }

            String finalName = memberName;
            try {
                ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(memberUuid));
                if (online != null) {
                    finalName = online.getName().getString();
                }
            } catch (IllegalArgumentException e) {
                TodoConstants.LOGGER.warn("Invalid memberUuid when adding project member, projectId={}", projectId, e);
                return;
            }

            project.addMember(memberUuid, Project.ProjectRole.MEMBER, finalName);
            clearPendingJoinRequest(projectId, memberUuid);
            manager.updateProject(project);
            saveProjects(server, project.getScope());
            broadcastProjects(server);
            TodoConstants.LOGGER.info("Added member {} to project {}", memberUuid, project.getName());
            return;
        }

        server.getProfileCache().getAsync(memberName, optionalProfile -> {
            optionalProfile.ifPresent(profile -> {
                server.execute(() -> {
                    String uuid = profile.getId().toString();
                    if (project.getMembers().containsKey(uuid)) return;

                    project.addMember(uuid, Project.ProjectRole.MEMBER, profile.getName());
                    clearPendingJoinRequest(projectId, uuid);
                    manager.updateProject(project);
                    saveProjects(server, project.getScope());
                    broadcastProjects(server);

                    TodoConstants.LOGGER.info("Added member {} to project {}", memberName, project.getName());
                });
            });
        });
    }

    private static void handleRemoveMember(MinecraftServer server, ServerPlayer player, String projectId, String memberUuid) {
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(projectId);
        
        if (project == null) return;
        
        Role role = getRole(player, project);
        boolean targetSelf = player.getStringUUID().equals(memberUuid);
        boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(memberUuid);
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
        if (!PermissionCenter.canPerform(Operation.REMOVE_MEMBER, role, ctx)) {
            return;
        }
        
        project.removeMember(memberUuid);
        manager.updateProject(project);
        saveProjects(server, project.getScope());
        broadcastProjects(server);
        
        TodoConstants.LOGGER.info("Removed member {} from project {}", memberUuid, project.getName());
    }

    private static void handleUpdateMemberRole(MinecraftServer server, ServerPlayer player, String projectId, String memberUuid, String roleStr) {
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(projectId);
        if (project == null) return;

        Role role = getRole(player, project);
        boolean targetSelf = player.getStringUUID().equals(memberUuid);
        boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(memberUuid);
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
        if (!PermissionCenter.canPerform(Operation.CHANGE_MEMBER_ROLE, role, ctx)) {
            return;
        }

        Project.ProjectRole newRole;
        if ("LEAD".equals(roleStr)) {
            newRole = Project.ProjectRole.LEAD;
        } else {
            newRole = Project.ProjectRole.MEMBER;
        }

        String name = project.getMemberName(memberUuid);
        project.addMember(memberUuid, newRole, name);
        manager.updateProject(project);
        saveProjects(server, project.getScope());
        broadcastProjects(server);
    }

    public static void requestJoinProject(MinecraftServer server, ServerPlayer player, String projectId) {
        if (server == null || player == null || projectId == null || projectId.isEmpty()) {
            return;
        }
        if (isSingleplayerServer(server)) {
            player.displayClientMessage(Component.translatable("message.todolist.project.join.singleplayer_forbidden"), false);
            return;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            player.displayClientMessage(Component.translatable("message.todolist.project.join.invalid_project"), false);
            return;
        }
        String applicantUuid = player.getStringUUID();
        if (isExistingProjectMember(project, applicantUuid)) {
            player.displayClientMessage(Component.translatable("message.todolist.project.join.already_member"), false);
            return;
        }
        MutableComponent projectName = getProjectDisplayName(project);
        String joinRequestKey = buildJoinRequestKey(projectId, applicantUuid);
        if (pendingJoinRequestMap.containsKey(joinRequestKey)) {
            player.displayClientMessage(Component.translatable("message.todolist.project.join.already_requested", projectName), false);
            return;
        }
        pendingJoinRequestMap.put(joinRequestKey, Boolean.TRUE);
        String cmdAccept = "/todolist join accept " + projectId + " " + applicantUuid;
        String cmdDeny = "/todolist join deny " + projectId + " " + applicantUuid;

        MutableComponent acceptBtn = Component.translatable("message.todolist.project.join.accept_button")
                .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdAccept))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmdAccept))));
        MutableComponent denyBtn = Component.translatable("message.todolist.project.join.deny_button")
                .withStyle(s -> s.withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdDeny))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmdDeny))));

        MutableComponent msg = Component.translatable("message.todolist.project.join.request_received", player.getName().getString(), projectName)
                .append(" ")
                .append(acceptBtn)
                .append(" ")
                .append(denyBtn);

        boolean notified = false;
        java.util.Set<String> notifiedUuids = new java.util.HashSet<>();
        String ownerUuid = project.getOwnerUuid();
        if (ownerUuid != null
                && !ownerUuid.isEmpty()
                && project.getMemberRole(ownerUuid) == Project.ProjectRole.PROJECT_MANAGER) {
            try {
                ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(ownerUuid));
                if (owner != null) {
                    owner.displayClientMessage(msg, false);
                    notified = true;
                    notifiedUuids.add(ownerUuid);
                }
            } catch (IllegalArgumentException e) {
                TodoConstants.LOGGER.debug("Invalid ownerUuid when notifying join request, projectId={}", projectId, e);
            }
        }

        if (!notified) {
            for (var entry : project.getMembers().entrySet()) {
                if (!entry.getValue().atLeast(Project.ProjectRole.LEAD)) {
                    continue;
                }
                if (notifiedUuids.contains(entry.getKey())) {
                    continue;
                }
                try {
                    ServerPlayer lead = server.getPlayerList().getPlayer(UUID.fromString(entry.getKey()));
                    if (lead != null) {
                        lead.displayClientMessage(msg, false);
                        notified = true;
                        notifiedUuids.add(entry.getKey());
                    }
                } catch (IllegalArgumentException e) {
                    TodoConstants.LOGGER.debug("Invalid leadUuid when notifying join request, projectId={}", projectId, e);
                }
            }
        }

        if (!notified) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online == null || !online.hasPermissions(2)) {
                    continue;
                }
                String uuid = online.getStringUUID();
                if (notifiedUuids.contains(uuid)) {
                    continue;
                }
                online.displayClientMessage(msg, false);
                notified = true;
            }
        }

        if (notified) {
            MutableComponent copyName = applyCopyStyle(projectName.copy().withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true)), projectId, Component.translatable("message.todolist.project.join.copy_hint", projectId));
            player.displayClientMessage(Component.translatable("message.todolist.project.join.sent_named", copyName), false);
        } else {
            player.displayClientMessage(Component.translatable("message.todolist.project.join.no_reviewer_online"), false);
        }
    }

    /**
     * 处理“加入项目申请”的审批结果，并向相关玩家发送提示与同步。
     */
    public static boolean handleJoinDecision(MinecraftServer server, ServerPlayer approver, String projectId, String applicantUuid, boolean accepted) {
        if (server == null || approver == null || projectId == null || projectId.isEmpty() || applicantUuid == null || applicantUuid.isEmpty()) {
            return false;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.invalid_project"), false);
            return false;
        }
        if (approver.getStringUUID().equals(applicantUuid)) {
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.cannot_approve_self"), false);
            return false;
        }

        Role role = getRole(approver, project);
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        if (!PermissionCenter.canPerform(Operation.ADD_MEMBER, role, ctx)) {
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.no_permission"), false);
            return false;
        }

        boolean alreadyMember = isExistingProjectMember(project, applicantUuid);
        if (alreadyMember) {
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.already_member"), false);
            return false;
        }
        String joinRequestKey = buildJoinRequestKey(projectId, applicantUuid);
        if (!pendingJoinRequestMap.containsKey(joinRequestKey)) {
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.no_pending_request"), false);
            return false;
        }

        ServerPlayer applicant = null;
        try {
            applicant = server.getPlayerList().getPlayer(UUID.fromString(applicantUuid));
        } catch (IllegalArgumentException e) {
            TodoConstants.LOGGER.warn("Invalid applicantUuid in join decision, projectId={}", projectId, e);
            return false;
        }
        if (applicant == null) {
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.applicant_offline"), false);
            return false;
        }

        MutableComponent projectName = getProjectDisplayName(project);
        pendingJoinRequestMap.remove(joinRequestKey);
        if (accepted) {
            project.addMember(applicantUuid, Project.ProjectRole.MEMBER, applicant.getName().getString());
            manager.updateProject(project);
            saveProjects(server, project.getScope());
            broadcastProjects(server);
            applicant.displayClientMessage(Component.translatable("message.todolist.project.join.accepted", projectName), false);
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.approved", applicant.getName().getString()), false);
        } else {
            applicant.displayClientMessage(Component.translatable("message.todolist.project.join.denied", projectName), false);
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.rejected", applicant.getName().getString()), false);
        }
        return true;
    }

    /**
     * 清理指定项目对应的所有待审批加入请求，避免项目删除后保留悬挂请求。
     *
     * @param projectId 项目 ID
     */
    public static void clearPendingJoinRequestsForProject(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }
        String normalizedProjectId = projectId.trim();
        pendingJoinRequestMap.keySet().removeIf(key -> key.startsWith(normalizedProjectId + "|"));
    }

    /**
     * 清理指定项目与申请人的单条待审批加入请求，避免成员已被手动加入后保留陈旧申请。
     *
     * @param projectId 项目 ID
     * @param applicantUuid 申请人 UUID
     */
    public static void clearPendingJoinRequest(String projectId, String applicantUuid) {
        if (projectId == null || projectId.isBlank() || applicantUuid == null || applicantUuid.isBlank()) {
            return;
        }
        pendingJoinRequestMap.remove(buildJoinRequestKey(projectId, applicantUuid));
    }

    /**
     * 构建加入申请在运行期缓存中的唯一键。
     *
     * @param projectId 项目 ID
     * @param applicantUuid 申请人 UUID
     * @return 由项目与申请人组成的唯一键
     */
    /**
     * 判断指定玩家当前是否已在项目成员表中拥有有效成员身份。
     *
     * @param project 目标项目
     * @param playerUuid 待检查的玩家 UUID
     * @return 已存在成员记录时返回 true
     */
    private static boolean isExistingProjectMember(Project project, String playerUuid) {
        if (project == null || playerUuid == null || playerUuid.isBlank()) {
            return false;
        }
        return project.getMemberRole(playerUuid) != null;
    }

    /**
     * 构建加入申请在运行期缓存中的唯一键。
     *
     * @param projectId 项目 ID
     * @param applicantUuid 申请人 UUID
     * @return 由项目与申请人组成的唯一键
     */
    private static String buildJoinRequestKey(String projectId, String applicantUuid) {
        String safeProjectId = projectId == null ? "" : projectId.trim();
        String safeApplicantUuid = applicantUuid == null ? "" : applicantUuid.trim();
        return safeProjectId + "|" + safeApplicantUuid;
    }

    private static MutableComponent getProjectDisplayName(Project project) {
        return ProjectNameFormatter.toDisplayText(project);
    }

    private static MutableComponent applyCopyStyle(MutableComponent component, String copyValue, Component hoverText) {
        String safeCopyValue = copyValue == null ? "" : copyValue;
        return component.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, safeCopyValue))
                .withInsertion(safeCopyValue)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
    }

    private static Role getRole(ServerPlayer player, Project project) {
        if (player == null) {
            return Role.MEMBER;
        }
        if (player.hasPermissions(2)) {
            return Role.OP;
        }
        if (project == null || project.getScope() == Project.Scope.PERSONAL) {
            return Role.MEMBER;
        }
        String uuid = player.getStringUUID();
        Project.ProjectRole memberRole = project.getMemberRole(uuid);
        if (memberRole == Project.ProjectRole.PROJECT_MANAGER) {
            return Role.PROJECT_MANAGER;
        }
        if (memberRole == Project.ProjectRole.LEAD) {
            return Role.LEAD;
        }
        return Role.MEMBER;
    }

    private static void saveProjects(MinecraftServer server, Project.Scope scope) {
        try {
            ProjectSaveDebouncer.requestSave(server, scope);
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to save projects", e);
        }
    }

    private static void syncProjectsToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        String playerUuid = player.getStringUUID();
        List<Project> projectsToSend = new ArrayList<>();

        projectsToSend.addAll(manager.getProjectsByScope(Project.Scope.TEAM));
        for (Project p : manager.getProjectsByScope(Project.Scope.PERSONAL)) {
            if (p == null) {
                continue;
            }
            String ownerUuid = p.getOwnerUuid();
            boolean isOwnedByPlayer = playerUuid.equals(ownerUuid);
            boolean isUnownedDefaultPersonal = (ownerUuid == null || ownerUuid.isEmpty()) && p.isDefaultPersonalProject();
            if (isOwnedByPlayer || isUnownedDefaultPersonal) {
                projectsToSend.add(p);
            }
        }
        
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeProjectList(buf, projectsToSend);
        serverPacketSender.send(player, SYNC_PROJECTS_ID, buf);
    }

    private static void broadcastProjects(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncProjectsToPlayer(player);
        }
    }

    private static List<String> sanitizeProjectIds(List<String> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String projectId : projectIds) {
            if (projectId == null) {
                continue;
            }
            String trimmed = projectId.trim();
            if (trimmed.isEmpty() || sanitized.contains(trimmed)) {
                continue;
            }
            sanitized.add(trimmed);
        }
        return sanitized;
    }

    /**
     * 承载客户端发起 requestSyncProjects 时附带的本地项目状态种子。
     */
    private static final class ClientProjectStateSeed {
        private final String activeProjectId;
        private final List<String> hudStarredProjectIds;
        private final boolean hudVisible;

        /**
         * 创建一份客户端项目状态种子。
         *
         * @param activeProjectId 客户端当前激活项目
         * @param hudStarredProjectIds 客户端当前 HUD 星标项目
         * @param hudVisible 客户端当前 HUD 可见性
         */
        private ClientProjectStateSeed(String activeProjectId, List<String> hudStarredProjectIds, boolean hudVisible) {
            this.activeProjectId = activeProjectId;
            this.hudStarredProjectIds = hudStarredProjectIds == null ? List.of() : List.copyOf(hudStarredProjectIds);
            this.hudVisible = hudVisible;
        }

        /**
         * 返回一份空的客户端项目状态种子，用于兼容旧包格式。
         *
         * @return 空种子
         */
        private static ClientProjectStateSeed empty() {
            return new ClientProjectStateSeed(null, List.of(), true);
        }
    }

    /**
     * 读取客户端发来的项目状态种子，用于首次建立服务端玩家状态。
     *
     * @param buf 网络缓冲区
     * @return 客户端项目状态种子
     */
    private static ClientProjectStateSeed readClientProjectStateSeed(FriendlyByteBuf buf) {
        if (buf == null || buf.readableBytes() <= 0) {
            return ClientProjectStateSeed.empty();
        }
        boolean activePresent = buf.readBoolean();
        String activeProjectId = activePresent ? PacketGuards.readString(buf, "activeProjectId") : null;
        int count = PacketGuards.readBoundedCount(buf, PacketGuards.MAX_PROJECT_LIST_SIZE, "hudStarredProjectIds");
        List<String> starredProjectIds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            starredProjectIds.add(PacketGuards.readString(buf, "hudStarredProjectId"));
        }
        boolean hudVisible = buf.readableBytes() <= 0 || buf.readBoolean();
        return new ClientProjectStateSeed(activeProjectId, starredProjectIds, hudVisible);
    }

    /**
     * 持久化指定玩家当前的项目相关运行期状态。
     *
     * @param player 目标玩家
     */
    private static void persistPlayerProjectState(ServerPlayer player) {
        if (player == null) {
            return;
        }
        persistPlayerProjectState(player, buildPlayerProjectState(player));
    }

    /**
     * 将指定的玩家项目状态直接写入磁盘。
     *
     * @param player 目标玩家
     * @param state 待持久化的项目状态
     */
    private static void persistPlayerProjectState(
            ServerPlayer player,
            ProjectPlayerStateStorage.ProjectPlayerState state
    ) {
        if (player == null) {
            return;
        }
        try {
            ProjectPlayerStateStorage.ProjectPlayerState mergedState = mergeTemporarilyUnavailableTeamState(player, state);
            getProjectPlayerStateStorage().savePlayerState(player.getUUID(), mergedState);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to persist project player state for {}", player.getStringUUID(), e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
    }

    /**
     * 在服务端尚未建立玩家项目状态文件时，用客户端种子初始化一份初始状态。
     *
     * @param player 当前玩家
     * @param seed 客户端上报的本地项目状态
     */
    private static void initializePlayerProjectStateIfMissing(ServerPlayer player, ClientProjectStateSeed seed) {
        if (player == null) {
            return;
        }
        ProjectPlayerStateStorage storage = getProjectPlayerStateStorage();
        if (storage.hasPlayerState(player.getUUID())) {
            return;
        }
        ClientProjectStateSeed safeSeed = seed == null ? ClientProjectStateSeed.empty() : seed;
        persistPlayerProjectState(
                player,
                new ProjectPlayerStateStorage.ProjectPlayerState(
                        safeSeed.activeProjectId,
                        sanitizeProjectIds(safeSeed.hudStarredProjectIds),
                        safeSeed.hudVisible
                )
        );
    }

    /**
     * 在未发布局域网的本地单机环境中，保留磁盘上已有的团队项目 current/star 状态，
     * 避免运行期临时净化结果因为其他单机交互再次写盘而永久覆盖原始团队状态。
     *
     * @param player 当前玩家
     * @param state 本次准备写盘的运行期状态
     * @return 合并后的最终持久化状态
     * @throws IOException 读取历史玩家状态失败时抛出
     */
    private static ProjectPlayerStateStorage.ProjectPlayerState mergeTemporarilyUnavailableTeamState(
            ServerPlayer player,
            ProjectPlayerStateStorage.ProjectPlayerState state
    ) throws IOException {
        ProjectPlayerStateStorage.ProjectPlayerState safeState = state == null
                ? ProjectPlayerStateStorage.ProjectPlayerState.empty()
                : state;
        MinecraftServer server = player.getServer();
        if (!isSingleplayerServer(server)) {
            return safeState;
        }

        ProjectPlayerStateStorage.ProjectPlayerState storedState = getProjectPlayerStateStorage().loadPlayerState(player.getUUID());
        String mergedActiveProjectId = safeState.getActiveProjectId();
        if (!isTeamProjectId(mergedActiveProjectId) && isTemporarilyUnavailableTeamProjectId(server, storedState.getActiveProjectId())) {
            mergedActiveProjectId = storedState.getActiveProjectId();
        }

        List<String> mergedStarredProjectIds = new ArrayList<>(sanitizeProjectIds(safeState.getHudStarredProjectIds()));
        for (String projectId : sanitizeProjectIds(storedState.getHudStarredProjectIds())) {
            if (!isTemporarilyUnavailableTeamProjectId(server, projectId) || mergedStarredProjectIds.contains(projectId)) {
                continue;
            }
            mergedStarredProjectIds.add(projectId);
        }

        return new ProjectPlayerStateStorage.ProjectPlayerState(
                mergedActiveProjectId,
                mergedStarredProjectIds,
                safeState.isHudVisible()
        );
    }

    /**
     * 在玩家进服时从磁盘恢复项目相关运行期状态，并覆盖当前内存缓存。
     *
     * @param server 当前服务端
     * @param player 目标玩家
     */
    private static void restorePlayerProjectState(MinecraftServer server, ServerPlayer player) {
        if (player == null) {
            return;
        }
        ProjectPlayerStateStorage.ProjectPlayerState state = ProjectPlayerStateStorage.ProjectPlayerState.empty();
        try {
            state = getProjectPlayerStateStorage().loadPlayerState(player.getUUID());
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load project player state for {}", player.getStringUUID(), e);
            StorageFailureNotifier.notifyPlayer(player, e, "message.todolist.save_failed");
        }
        ProjectPlayerStateStorage.ProjectPlayerState runtimeState = sanitizePlayerProjectState(server, state, true);
        applyPlayerProjectState(player, runtimeState);
        ProjectPlayerStateStorage.ProjectPlayerState persistedState = sanitizePlayerProjectState(server, state, false);
        if (!samePlayerProjectState(state, persistedState)) {
            persistPlayerProjectState(player, persistedState);
        }
    }

    /**
     * 将当前内存中的项目运行期状态组装为可写盘对象。
     *
     * @param player 目标玩家
     * @return 当前玩家的项目运行期状态
     */
    private static ProjectPlayerStateStorage.ProjectPlayerState buildPlayerProjectState(ServerPlayer player) {
        return new ProjectPlayerStateStorage.ProjectPlayerState(
                getActiveProjectId(player),
                getHudStarredProjectIds(player),
                isHudVisible(player)
        );
    }

    /**
     * 将恢复后的玩家项目状态应用到运行期缓存。
     *
     * @param player 目标玩家
     * @param state 已规范化的玩家项目状态
     */
    private static void applyPlayerProjectState(ServerPlayer player, ProjectPlayerStateStorage.ProjectPlayerState state) {
        if (player == null) {
            return;
        }
        ProjectPlayerStateStorage.ProjectPlayerState safeState = state == null
                ? ProjectPlayerStateStorage.ProjectPlayerState.empty()
                : state;
        String uuid = player.getStringUUID();
        String activeProjectId = safeState.getActiveProjectId();
        if (activeProjectId == null || activeProjectId.isBlank()) {
            playerActiveProjectIdMap.remove(uuid);
        } else {
            playerActiveProjectIdMap.put(uuid, activeProjectId);
        }
        List<String> starredProjectIds = sanitizeProjectIds(safeState.getHudStarredProjectIds());
        if (starredProjectIds.isEmpty()) {
            playerHudStarredProjectIdsMap.remove(uuid);
        } else {
            playerHudStarredProjectIdsMap.put(uuid, starredProjectIds);
        }
        if (safeState.isHudVisible()) {
            playerHudVisibilityMap.remove(uuid);
        } else {
            playerHudVisibilityMap.put(uuid, false);
        }
    }

    /**
     * 规范化从磁盘恢复的玩家项目状态，移除不存在或当前环境不可用的项目引用。
     *
     * @param server 当前服务端
     * @param state 原始玩家项目状态
     * @return 规范化后的玩家项目状态
     */
    private static ProjectPlayerStateStorage.ProjectPlayerState sanitizePlayerProjectState(
            MinecraftServer server,
            ProjectPlayerStateStorage.ProjectPlayerState state,
            boolean excludeTemporarilyUnavailableTeamProjects
    ) {
        if (state == null) {
            return ProjectPlayerStateStorage.ProjectPlayerState.empty();
        }
        String sanitizedActiveProjectId = sanitizeStoredProjectId(server, state.getActiveProjectId(), excludeTemporarilyUnavailableTeamProjects);
        List<String> sanitizedStarredProjectIds = new ArrayList<>();
        for (String projectId : sanitizeProjectIds(state.getHudStarredProjectIds())) {
            String sanitizedProjectId = sanitizeStoredProjectId(server, projectId, excludeTemporarilyUnavailableTeamProjects);
            if (sanitizedProjectId == null || sanitizedStarredProjectIds.contains(sanitizedProjectId)) {
                continue;
            }
            sanitizedStarredProjectIds.add(sanitizedProjectId);
        }
        return new ProjectPlayerStateStorage.ProjectPlayerState(
                sanitizedActiveProjectId,
                sanitizedStarredProjectIds,
                state.isHudVisible()
        );
    }

    /**
     * 规范化单个已持久化项目 ID，移除不存在或在当前环境不可用的项目。
     *
     * @param server 当前服务端
     * @param projectId 待规范化的项目 ID
     * @return 规范化后的项目 ID；不可用时返回 null
     */
    private static String sanitizeStoredProjectId(
            MinecraftServer server,
            String projectId,
            boolean excludeTemporarilyUnavailableTeamProjects
    ) {
        if (projectId == null || projectId.isBlank()) {
            return null;
        }
        String normalizedProjectId = projectId.trim();
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null) {
            return null;
        }
        if (excludeTemporarilyUnavailableTeamProjects
                && project.getScope() == Project.Scope.TEAM
                && isSingleplayerServer(server)) {
            return null;
        }
        return normalizedProjectId;
    }

    /**
     * 判断指定项目 ID 当前是否指向团队项目。
     *
     * @param projectId 待判断的项目 ID
     * @return 是团队项目时返回 true
     */
    private static boolean isTeamProjectId(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return false;
        }
        Project project = TodoListCommon.getProjectManager().getProject(projectId.trim());
        return project != null && project.getScope() == Project.Scope.TEAM;
    }

    /**
     * 判断指定项目是否属于“因当前单机环境暂时不可用，但磁盘上仍应保留”的团队项目。
     *
     * @param server 当前服务端
     * @param projectId 待判断的项目 ID
     * @return 属于临时不可用团队项目时返回 true
     */
    private static boolean isTemporarilyUnavailableTeamProjectId(MinecraftServer server, String projectId) {
        return isSingleplayerServer(server) && isTeamProjectId(projectId);
    }

    /**
     * 比较两份玩家项目状态是否等价。
     *
     * @param left 左侧状态
     * @param right 右侧状态
     * @return 两份状态等价时返回 true
     */
    private static boolean samePlayerProjectState(
            ProjectPlayerStateStorage.ProjectPlayerState left,
            ProjectPlayerStateStorage.ProjectPlayerState right
    ) {
        ProjectPlayerStateStorage.ProjectPlayerState safeLeft = left == null
                ? ProjectPlayerStateStorage.ProjectPlayerState.empty()
                : left;
        ProjectPlayerStateStorage.ProjectPlayerState safeRight = right == null
                ? ProjectPlayerStateStorage.ProjectPlayerState.empty()
                : right;
        return sameNullableString(safeLeft.getActiveProjectId(), safeRight.getActiveProjectId())
                && safeLeft.isHudVisible() == safeRight.isHudVisible()
                && safeLeft.getHudStarredProjectIds().equals(safeRight.getHudStarredProjectIds());
    }

    /**
     * 比较两个可空字符串是否相同。
     *
     * @param left 左侧字符串
     * @param right 右侧字符串
     * @return 相同时返回 true
     */
    private static boolean sameNullableString(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    /**
     * 返回玩家项目状态存储实例。
     *
     * @return 玩家项目状态存储
     */
    private static ProjectPlayerStateStorage getProjectPlayerStateStorage() {
        return new ProjectPlayerStateStorage();
    }

    private static void syncActiveProjectIdToPlayer(ServerPlayer player, String projectId) {
        if (player == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        if (projectId == null || projectId.isBlank()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(projectId);
        }
        serverPacketSender.send(player, SYNC_ACTIVE_PROJECT_ID, buf);
    }

    private static void syncHudVisibilityToPlayer(ServerPlayer player, boolean visible) {
        if (player == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBoolean(visible);
        serverPacketSender.send(player, SYNC_HUD_VISIBILITY_ID, buf);
    }

    /**
     * 将服务端记录的 HUD 星标项目列表同步到客户端。
     *
     * @param player 目标玩家
     * @param projectIds 星标项目 ID 列表
     */
    private static void syncHudStarredProjectIdsToPlayer(ServerPlayer player, List<String> projectIds) {
        if (player == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        List<String> ids = projectIds == null ? List.of() : sanitizeProjectIds(projectIds);
        buf.writeInt(ids.size());
        for (String projectId : ids) {
            buf.writeUtf(projectId == null ? "" : projectId);
        }
        serverPacketSender.send(player, SYNC_HUD_STARRED_PROJECT_IDS_ID, buf);
    }

    // Helper methods
    /**
     * 将单个项目写入网络缓冲区。
     */
    public static void writeProject(FriendlyByteBuf buf, Project project) {
        buf.writeNbt(project.toNbt());
    }

    /**
     * 从网络缓冲区读取单个项目。
     */
    public static Project readProject(FriendlyByteBuf buf) {
        try {
            CompoundTag nbt = PacketGuards.readNbt(buf, "project");
            return Project.fromNbt(nbt);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw PacketGuards.malformedPacket("Failed to read project", ex);
        }
    }

    /**
     * 将项目列表写入网络缓冲区。
     */
    public static void writeProjectList(FriendlyByteBuf buf, List<Project> projects) {
        buf.writeInt(projects.size());
        for (Project project : projects) {
            writeProject(buf, project);
        }
    }

    /**
     * 从网络缓冲区读取项目列表。
     */
    public static List<Project> readProjectList(FriendlyByteBuf buf) {
        try {
            int count = PacketGuards.readBoundedCount(buf, PacketGuards.MAX_PROJECT_LIST_SIZE, "projects");
            List<Project> projects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                projects.add(readProject(buf));
            }
            return projects;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw PacketGuards.malformedPacket("Failed to read project list", ex);
        }
    }
}


