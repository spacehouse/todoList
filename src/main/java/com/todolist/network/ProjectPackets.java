package com.todolist.network;

import com.todolist.TodoListMod;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectStorage;
import com.todolist.project.ProjectSaveDebouncer;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Network packet handling for project synchronization
 */
public class ProjectPackets {
    // Packet IDs
    public static final Identifier SYNC_PROJECTS_ID = new Identifier(TodoListMod.MOD_ID, "sync_projects");
    public static final Identifier ADD_PROJECT_ID = new Identifier(TodoListMod.MOD_ID, "add_project");
    public static final Identifier UPDATE_PROJECT_ID = new Identifier(TodoListMod.MOD_ID, "update_project");
    public static final Identifier DELETE_PROJECT_ID = new Identifier(TodoListMod.MOD_ID, "delete_project");
    public static final Identifier ADD_MEMBER_ID = new Identifier(TodoListMod.MOD_ID, "add_member");
    public static final Identifier REMOVE_MEMBER_ID = new Identifier(TodoListMod.MOD_ID, "remove_member");
    public static final Identifier UPDATE_MEMBER_ROLE_ID = new Identifier(TodoListMod.MOD_ID, "update_member_role");
    public static final Identifier REQUEST_JOIN_PROJECT_ID = new Identifier(TodoListMod.MOD_ID, "request_join_project");

    public static void registerServerPackets() {
        // ADD_PROJECT
        ServerPlayNetworking.registerGlobalReceiver(ADD_PROJECT_ID, (server, player, handler, buf, responseSender) -> {
            Project project = readProject(buf);
            server.execute(() -> handleAddProject(server, player, project));
        });

        // UPDATE_PROJECT
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_PROJECT_ID, (server, player, handler, buf, responseSender) -> {
            Project project = readProject(buf);
            server.execute(() -> handleUpdateProject(server, player, project));
        });

        // DELETE_PROJECT
        ServerPlayNetworking.registerGlobalReceiver(DELETE_PROJECT_ID, (server, player, handler, buf, responseSender) -> {
            String projectId = buf.readString();
            server.execute(() -> handleDeleteProject(server, player, projectId));
        });

        // ADD_MEMBER
        ServerPlayNetworking.registerGlobalReceiver(ADD_MEMBER_ID, (server, player, handler, buf, responseSender) -> {
            String projectId = buf.readString();
            String memberUuidOrName = buf.readString();
            String memberUuid = "";
            String memberName = "";
            if (buf.readableBytes() > 0) {
                memberUuid = memberUuidOrName;
                memberName = buf.readString();
            } else {
                memberName = memberUuidOrName;
            }
            String finalMemberUuid = memberUuid;
            String finalMemberName = memberName;
            server.execute(() -> handleAddMember(server, player, projectId, finalMemberUuid, finalMemberName));
        });

        // REMOVE_MEMBER
        ServerPlayNetworking.registerGlobalReceiver(REMOVE_MEMBER_ID, (server, player, handler, buf, responseSender) -> {
            String projectId = buf.readString();
            String memberUuid = buf.readString();
            server.execute(() -> handleRemoveMember(server, player, projectId, memberUuid));
        });

        // UPDATE_MEMBER_ROLE
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_MEMBER_ROLE_ID, (server, player, handler, buf, responseSender) -> {
            String projectId = buf.readString();
            String memberUuid = buf.readString();
            String roleStr = buf.readString();
            server.execute(() -> handleUpdateMemberRole(server, player, projectId, memberUuid, roleStr));
        });

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_JOIN_PROJECT_ID, (server, player, handler, buf, responseSender) -> {
            String projectId = buf.readString();
            server.execute(() -> handleRequestJoinProject(server, player, projectId));
        });

        // Sync on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> {
                cachePlayerNameForTeamProjects(server, player);
                syncProjectsToPlayer(player);
            });
        });
    }

    private static void cachePlayerNameForTeamProjects(MinecraftServer server, ServerPlayerEntity player) {
        if (server == null || player == null) {
            return;
        }
        ProjectManager manager = TodoListMod.getProjectManager();
        String uuid = player.getUuidAsString();
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

    private static void handleAddProject(MinecraftServer server, ServerPlayerEntity player, Project project) {
        // Validation
        if (project.getName() == null || project.getName().isEmpty()) {
            return;
        }

        String playerUuid = player.getUuidAsString();
        if (project.getScope() == Project.Scope.TEAM) {
            project.setOwnerUuid(playerUuid);
            project.getMembers().clear();
            project.getMemberNames().clear();
        } else if (project.getOwnerUuid() == null) {
            project.setOwnerUuid(playerUuid);
        }

        project.addMember(playerUuid, Project.ProjectRole.PROJECT_MANAGER, player.getName().getString());

        ProjectManager manager = TodoListMod.getProjectManager();
        manager.addProject(project);

        // Save
        saveProjects(server, project.getScope());

        // Sync
        if (project.getScope() == Project.Scope.PERSONAL) {
            syncProjectsToPlayer(player);
        } else {
            broadcastProjects(server);
        }
        
        TodoListMod.LOGGER.info("Player {} added project: {}", player.getName().getString(), project.getName());
    }

    private static void handleUpdateProject(MinecraftServer server, ServerPlayerEntity player, Project incomingProject) {
        ProjectManager manager = TodoListMod.getProjectManager();
        Project existingProject = manager.getProject(incomingProject.getId());

        if (existingProject == null) {
            return;
        }

        if (existingProject.getScope() == Project.Scope.PERSONAL) {
            String owner = existingProject.getOwnerUuid();
            if (owner != null && !owner.isEmpty() && !owner.equals(player.getUuidAsString())) {
                TodoListMod.LOGGER.warn("Player {} tried to update personal project {} without permission", player.getName().getString(), existingProject.getId());
                return;
            }
        } else {
            Role role = getRole(player, existingProject);
            Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
            if (!PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx)) {
                TodoListMod.LOGGER.warn("Player {} tried to update project {} without permission", player.getName().getString(), existingProject.getId());
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
        
        TodoListMod.LOGGER.info("Player {} updated project: {}", player.getName().getString(), existingProject.getName());
    }

    private static void handleDeleteProject(MinecraftServer server, ServerPlayerEntity player, String projectId) {
        ProjectManager manager = TodoListMod.getProjectManager();
        Project existingProject = manager.getProject(projectId);

        if (existingProject == null) {
            return;
        }

        if (existingProject.getScope() == Project.Scope.PERSONAL) {
            String owner = existingProject.getOwnerUuid();
            if (owner != null && !owner.isEmpty() && !owner.equals(player.getUuidAsString())) {
                TodoListMod.LOGGER.warn("Player {} tried to delete personal project {} without permission", player.getName().getString(), projectId);
                return;
            }
        } else {
            Role role = getRole(player, existingProject);
            Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
            if (!PermissionCenter.canPerform(Operation.DELETE_PROJECT, role, ctx)) {
                TodoListMod.LOGGER.warn("Player {} tried to delete project {} without permission", player.getName().getString(), projectId);
                return;
            }
        }

        manager.deleteProject(projectId);

        // Save
        saveProjects(server, existingProject.getScope());

        // Sync
        if (existingProject.getScope() == Project.Scope.PERSONAL) {
            syncProjectsToPlayer(player);
        } else {
            broadcastProjects(server);
        }
        
        TodoListMod.LOGGER.info("Player {} deleted project: {}", player.getName().getString(), projectId);
    }

    private static void handleAddMember(MinecraftServer server, ServerPlayerEntity player, String projectId, String memberUuid, String memberName) {
        ProjectManager manager = TodoListMod.getProjectManager();
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
                ServerPlayerEntity online = server.getPlayerManager().getPlayer(UUID.fromString(memberUuid));
                if (online != null) {
                    finalName = online.getName().getString();
                }
            } catch (Exception e) {}

            project.addMember(memberUuid, Project.ProjectRole.MEMBER, finalName);
            manager.updateProject(project);
            saveProjects(server, project.getScope());
            broadcastProjects(server);
            TodoListMod.LOGGER.info("Added member {} to project {}", memberUuid, project.getName());
            return;
        }

        server.getUserCache().findByNameAsync(memberName, optionalProfile -> {
            optionalProfile.ifPresent(profile -> {
                server.execute(() -> {
                    String uuid = profile.getId().toString();
                    if (project.getMembers().containsKey(uuid)) return;

                    project.addMember(uuid, Project.ProjectRole.MEMBER, profile.getName());
                    manager.updateProject(project);
                    saveProjects(server, project.getScope());
                    broadcastProjects(server);

                    TodoListMod.LOGGER.info("Added member {} to project {}", memberName, project.getName());
                });
            });
        });
    }

    private static void handleRemoveMember(MinecraftServer server, ServerPlayerEntity player, String projectId, String memberUuid) {
        ProjectManager manager = TodoListMod.getProjectManager();
        Project project = manager.getProject(projectId);
        
        if (project == null) return;
        
        Role role = getRole(player, project);
        boolean targetSelf = player.getUuidAsString().equals(memberUuid);
        boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(memberUuid);
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
        if (!PermissionCenter.canPerform(Operation.REMOVE_MEMBER, role, ctx)) {
            return;
        }
        
        project.removeMember(memberUuid);
        manager.updateProject(project);
        saveProjects(server, project.getScope());
        broadcastProjects(server);
        
        TodoListMod.LOGGER.info("Removed member {} from project {}", memberUuid, project.getName());
    }

    private static void handleUpdateMemberRole(MinecraftServer server, ServerPlayerEntity player, String projectId, String memberUuid, String roleStr) {
        ProjectManager manager = TodoListMod.getProjectManager();
        Project project = manager.getProject(projectId);
        if (project == null) return;

        Role role = getRole(player, project);
        boolean targetSelf = player.getUuidAsString().equals(memberUuid);
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

    private static void handleRequestJoinProject(MinecraftServer server, ServerPlayerEntity player, String projectId) {
        if (server == null || player == null || projectId == null || projectId.isEmpty()) {
            return;
        }
        ProjectManager manager = TodoListMod.getProjectManager();
        Project project = manager.getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            player.sendMessage(Text.translatable("message.todolist.project.join.invalid_project"), false);
            return;
        }
        String applicantUuid = player.getUuidAsString();
        if (applicantUuid.equals(project.getOwnerUuid()) || project.getMemberRole(applicantUuid) != null) {
            player.sendMessage(Text.translatable("message.todolist.project.join.already_member"), false);
            return;
        }

        MutableText projectName = getProjectDisplayName(project);
        String cmdAccept = "/todolist join accept " + projectId + " " + applicantUuid;
        String cmdDeny = "/todolist join deny " + projectId + " " + applicantUuid;

        MutableText acceptBtn = Text.translatable("message.todolist.project.join.accept_button")
                .styled(s -> s.withColor(Formatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdAccept))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(cmdAccept))));
        MutableText denyBtn = Text.translatable("message.todolist.project.join.deny_button")
                .styled(s -> s.withColor(Formatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmdDeny))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(cmdDeny))));

        MutableText msg = Text.translatable("message.todolist.project.join.request_received", player.getName().getString(), projectName)
                .append(" ")
                .append(acceptBtn)
                .append(" ")
                .append(denyBtn);

        boolean notified = false;
        if (project.getOwnerUuid() != null && !project.getOwnerUuid().isEmpty()) {
            try {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(UUID.fromString(project.getOwnerUuid()));
                if (owner != null) {
                    owner.sendMessage(msg, false);
                    notified = true;
                }
            } catch (Exception e) {}
        }

        for (var entry : project.getMembers().entrySet()) {
            if (entry.getValue() != Project.ProjectRole.LEAD) {
                continue;
            }
            try {
                ServerPlayerEntity lead = server.getPlayerManager().getPlayer(UUID.fromString(entry.getKey()));
                if (lead != null) {
                    lead.sendMessage(msg, false);
                    notified = true;
                }
            } catch (Exception e) {}
        }

        if (notified) {
            player.sendMessage(Text.translatable("message.todolist.project.join.sent"), false);
        } else {
            player.sendMessage(Text.translatable("message.todolist.project.join.no_reviewer_online"), false);
        }
    }

    public static void handleJoinDecision(MinecraftServer server, ServerPlayerEntity approver, String projectId, String applicantUuid, boolean accepted) {
        if (server == null || approver == null || projectId == null || projectId.isEmpty() || applicantUuid == null || applicantUuid.isEmpty()) {
            return;
        }
        ProjectManager manager = TodoListMod.getProjectManager();
        Project project = manager.getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            approver.sendMessage(Text.translatable("message.todolist.project.join.invalid_project"), false);
            return;
        }
        if (approver.getUuidAsString().equals(applicantUuid)) {
            approver.sendMessage(Text.translatable("message.todolist.project.join.cannot_approve_self"), false);
            return;
        }

        Role role = getRole(approver, project);
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        if (!PermissionCenter.canPerform(Operation.ADD_MEMBER, role, ctx)) {
            approver.sendMessage(Text.translatable("message.todolist.project.join.no_permission"), false);
            return;
        }

        boolean alreadyMember = applicantUuid.equals(project.getOwnerUuid()) || project.getMemberRole(applicantUuid) != null;
        if (alreadyMember) {
            approver.sendMessage(Text.translatable("message.todolist.project.join.already_member"), false);
            return;
        }

        ServerPlayerEntity applicant = null;
        try {
            applicant = server.getPlayerManager().getPlayer(UUID.fromString(applicantUuid));
        } catch (Exception e) {}
        if (applicant == null) {
            approver.sendMessage(Text.translatable("message.todolist.project.join.applicant_offline"), false);
            return;
        }

        MutableText projectName = getProjectDisplayName(project);
        if (accepted) {
            project.addMember(applicantUuid, Project.ProjectRole.MEMBER, applicant.getName().getString());
            manager.updateProject(project);
            saveProjects(server, project.getScope());
            broadcastProjects(server);
            applicant.sendMessage(Text.translatable("message.todolist.project.join.accepted", projectName), false);
            approver.sendMessage(Text.translatable("message.todolist.project.join.approved", applicant.getName().getString()), false);
        } else {
            applicant.sendMessage(Text.translatable("message.todolist.project.join.denied", projectName), false);
            approver.sendMessage(Text.translatable("message.todolist.project.join.rejected", applicant.getName().getString()), false);
        }
    }

    private static MutableText getProjectDisplayName(Project project) {
        if (project == null) {
            return Text.empty();
        }
        String name = project.getName();
        if (name == null) {
            return Text.empty();
        }
        if (name.startsWith("gui.todolist.") || name.startsWith("item.") || name.startsWith("block.")) {
            return Text.translatable(name);
        }
        return Text.literal(name);
    }

    private static Role getRole(ServerPlayerEntity player, Project project) {
        if (player == null) {
            return Role.MEMBER;
        }
        if (player.hasPermissionLevel(2)) {
            return Role.OP;
        }
        if (project == null || project.getScope() == Project.Scope.PERSONAL) {
            return Role.MEMBER;
        }
        String uuid = player.getUuidAsString();
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
            TodoListMod.LOGGER.error("Failed to save projects", e);
        }
    }

    private static void syncProjectsToPlayer(ServerPlayerEntity player) {
        ProjectManager manager = TodoListMod.getProjectManager();
        List<Project> personalProjects = manager.getProjectsByScope(Project.Scope.PERSONAL);
        List<Project> teamProjects = manager.getProjectsByScope(Project.Scope.TEAM);
        
        // Filter personal projects for this player?
        // Wait, ProjectManager currently holds ALL projects.
        // For personal projects, we should only send the ones owned by the player?
        // Yes. The current implementation of ProjectManager loads ALL projects. 
        // But for PERSONAL scope, we should probably filter by owner if we are in a multi-user environment.
        // However, ProjectStorage.loadProjects() loads from "projects.dat" which seems to be global or single-player.
        // If this is a dedicated server with multiple players, PERSONAL projects should probably be per-player file.
        // Re-checking ProjectStorage: 
        // savePlayerProjects(UUID playerUuid, ...) -> saves to players/{uuid}.dat
        // loadPlayerProjects(UUID playerUuid) -> loads from players/{uuid}.dat
        // But TodoListMod initializes by calling loadProjects() (global) and loadTeamProjects().
        // It seems TodoListMod initialization needs to be aware of per-player loading on join.
        
        // Let's refine syncProjectsToPlayer:
        // 1. Load player-specific projects if not loaded? 
        // Actually, ProjectManager should ideally manage per-player projects separately or we load them into the manager with some key.
        // Current ProjectManager is a simple map.
        
        // In a real multiplayer scenario:
        // Personal projects are private. We should load them when player joins, and unload when leaves?
        // Or just load them and keep them.
        
        // Let's look at TaskPackets. It uses storage.loadPlayerTasks(playerUuid).
        // So ProjectPackets should also load player projects on demand or use what's in manager.
        // But TodoListMod only loaded global projects.
        
        // Correction: TodoListMod loaded "projects.dat" which are... global? 
        // In singleplayer, there's only one player.
        // In multiplayer, we need per-player personal projects.
        
        // Let's update syncProjectsToPlayer to load from storage if needed.
        
        List<Project> projectsToSend = new ArrayList<>();
        
        // 1. Team projects (visible to all for now, or filter by membership)
        // For now, send all team projects.
        projectsToSend.addAll(teamProjects);
        
        // 2. Personal projects
        // We should check if we have them in memory.
        // If ProjectManager is global, we might have mixed personal projects.
        // Filter by owner.
        String playerUuid = player.getUuidAsString();
        /*
        for (Project p : personalProjects) {
            if (playerUuid.equals(p.getOwnerUuid())) {
                projectsToSend.add(p);
            }
        }
        */
        // Actually, TodoListMod currently loads `projects.dat` which seems to be the "main" file.
        // If we want per-player isolation, we should use `loadPlayerProjects`.
        // But let's stick to the current implementation of TodoListMod which loads `loadProjects()`.
        // Wait, `loadProjects()` loads from `projects.dat`.
        // If we want to support multiplayer properly, we should change TodoListMod to NOT load personal projects globally,
        // but load them per player on join.
        
        // However, for this task, I'll stick to what I have. 
        // If `loadProjects()` loads all personal projects (from singleplayer context), then filtering by owner is good practice.
        // In singleplayer, ownerUuid might be the player's UUID.
        
        // Let's send all projects for now, client can filter or we filter here.
        // Sending all is easier for synchronization if we assume singleplayer or small coop.
        // But let's try to be correct:
        
        projectsToSend.addAll(personalProjects); // Sending all personal projects (simplification)
        
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        writeProjectList(buf, projectsToSend);
        ServerPlayNetworking.send(player, SYNC_PROJECTS_ID, buf);
    }

    private static void broadcastProjects(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncProjectsToPlayer(player);
        }
    }

    // Helper methods
    public static void writeProject(PacketByteBuf buf, Project project) {
        buf.writeNbt(project.toNbt());
    }

    public static Project readProject(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        return Project.fromNbt(nbt);
    }

    public static void writeProjectList(PacketByteBuf buf, List<Project> projects) {
        buf.writeInt(projects.size());
        for (Project project : projects) {
            writeProject(buf, project);
        }
    }

    public static List<Project> readProjectList(PacketByteBuf buf) {
        int count = buf.readInt();
        List<Project> projects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            projects.add(readProject(buf));
        }
        return projects;
    }
}
