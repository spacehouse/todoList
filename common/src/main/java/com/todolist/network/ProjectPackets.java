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
import com.todolist.project.ProjectStorage;
import com.todolist.project.ProjectSaveDebouncer;
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
    private static volatile TaskPackets.ServerPacketSender serverPacketSender = (player, channelId, buf) -> { };
    private static final ConcurrentHashMap<String, String> playerActiveProjectIdMap = new ConcurrentHashMap<>();

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
        server.execute(() -> handleRequestJoinProject(server, player, projectId));
    }

    /**
     * 客户端主动请求服务端重新同步项目列表。
     */
    public static void onRequestSyncProjectsPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        server.execute(() -> syncProjectsToPlayer(player));
    }

    /**
     * 客户端上报当前激活（选中）的项目 ID，用于命令默认关联项目等服务端逻辑。
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
                playerActiveProjectIdMap.remove(uuid);
                return;
            }
            playerActiveProjectIdMap.put(uuid, finalProjectId.trim());
        });
    }

    /**
     * 获取服务端记录的玩家当前激活项目 ID（可能为 null）。
     */
    public static String getActiveProjectId(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        return playerActiveProjectIdMap.get(player.getStringUUID());
    }

    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        server.execute(() -> {
            ensureDefaultTeamProjectOwner(server, player);
            cachePlayerNameForTeamProjects(server, player);
            syncProjectsToPlayer(player);
        });
    }

    private static void ensureDefaultTeamProjectOwner(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
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

    private static void handleRequestJoinProject(MinecraftServer server, ServerPlayer player, String projectId) {
        if (server == null || player == null || projectId == null || projectId.isEmpty()) {
            return;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            player.displayClientMessage(Component.translatable("message.todolist.project.join.invalid_project"), false);
            return;
        }
        String applicantUuid = player.getStringUUID();
        if (applicantUuid.equals(project.getOwnerUuid()) || project.getMemberRole(applicantUuid) != null) {
            player.displayClientMessage(Component.translatable("message.todolist.project.join.already_member"), false);
            return;
        }

        MutableComponent projectName = getProjectDisplayName(project);
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
        if (project.getOwnerUuid() != null && !project.getOwnerUuid().isEmpty()) {
            try {
                ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(project.getOwnerUuid()));
                if (owner != null) {
                    owner.displayClientMessage(msg, false);
                    notified = true;
                    notifiedUuids.add(project.getOwnerUuid());
                }
            } catch (IllegalArgumentException e) {
                TodoConstants.LOGGER.debug("Invalid ownerUuid when notifying join request, projectId={}", projectId, e);
            }
        }

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
            player.displayClientMessage(Component.translatable("message.todolist.project.join.sent"), false);
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

        boolean alreadyMember = applicantUuid.equals(project.getOwnerUuid()) || project.getMemberRole(applicantUuid) != null;
        if (alreadyMember) {
            approver.displayClientMessage(Component.translatable("message.todolist.project.join.already_member"), false);
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

    private static MutableComponent getProjectDisplayName(Project project) {
        return ProjectNameFormatter.toDisplayText(project);
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
        if (uuid.equals(project.getOwnerUuid())) {
            return Role.PROJECT_MANAGER;
        }
        Project.ProjectRole r = project.getMemberRole(uuid);
        if (r == Project.ProjectRole.LEAD) {
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


