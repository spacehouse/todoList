package com.todolist.gui;

import com.todolist.client.ClientProjectPackets;
import com.todolist.network.ProjectPackets;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.TodoListMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProjectSettingsScreen extends Screen implements ProjectManager.ProjectChangeListener {
    private final Screen parent;
    private Project project;
    private TextFieldWidget nameField;
    private TextFieldWidget memberSearchField;
    private boolean canEdit;
    private MemberListWidget memberList;
    private ButtonWidget addMemberBtn;

    public ProjectSettingsScreen(Screen parent, Project project) {
        super(Text.translatable("gui.todolist.project_settings.title"));
        this.parent = parent;
        this.project = project;
    }

    @Override
    protected void init() {
        TodoListMod.getProjectManager().addListener(this);
        canEdit = checkPermission();
        boolean isTeam = project.getScope() == Project.Scope.TEAM;

        if (isTeam && project.isDefaultTeamProject() && isOpClient()) {
            String owner = project.getOwnerUuid();
            if ((owner == null || owner.isEmpty()) && client != null && client.player != null) {
                String myUuid = client.player.getUuid().toString();
                project.setOwnerUuid(myUuid);
                if (project.getMemberRole(myUuid) == null) {
                    project.addMember(myUuid, Project.ProjectRole.PROJECT_MANAGER);
                }
                canEdit = true;
            }
        }
        
        int w = 200;
        int h = isTeam ? 220 : 150;
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        // Name Field
        nameField = new TextFieldWidget(textRenderer, x + 10, y + 35, w - 20, 20, Text.translatable("gui.todolist.project.name"));
        nameField.setText(Text.translatable(project.getName()).getString());
        nameField.setMaxLength(32);
        nameField.setEditable(canEdit);
        addDrawableChild(nameField);

        // Team Member Management
        if (isTeam) {
            // Member Search Field
            memberSearchField = new TextFieldWidget(textRenderer, x + 10, y + 70, w - 100, 16, Text.empty());
            memberSearchField.setPlaceholder(Text.translatable("gui.todolist.member.search"));
            memberSearchField.setChangedListener(text -> memberList.updateEntries(text));
            addDrawableChild(memberSearchField);

            int listTop = y + 90;
            int listBottom = y + h - 40;
            memberList = new MemberListWidget(client, w - 20, listBottom - listTop, listTop, listBottom, 20);
            memberList.setLeftPos(x + 10);
            addDrawableChild(memberList);

            addMemberBtn = ButtonWidget.builder(Text.translatable("gui.todolist.add_member"), button -> {
                client.setScreen(new AddMemberScreen(this, project.getId()));
            }).dimensions(x + w - 85, y + 70, 75, 16).build();
            addMemberBtn.active = checkAdminPermission();
            addDrawableChild(addMemberBtn);
        }

        // Save Button
        ButtonWidget saveBtn = ButtonWidget.builder(Text.translatable("gui.todolist.save"), button -> saveProject())
                .dimensions(x + 80, y + h - 30, 50, 20).build();
        saveBtn.active = canEdit;
        addDrawableChild(saveBtn);

        // Cancel Button
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), button -> close())
                .dimensions(x + w - 60, y + h - 30, 50, 20).build());
        
        if (canEdit) {
            setFocused(nameField);
        }
    }

    public void optimisticAddMember(String memberUuid, String memberName) {
        if (memberUuid == null || memberUuid.isEmpty()) {
            return;
        }
        if (project.getScope() != Project.Scope.TEAM) {
            return;
        }
        if (project.getMembers().containsKey(memberUuid)) {
            return;
        }
        project.addMember(memberUuid, Project.ProjectRole.MEMBER, memberName);
        if (memberList != null) {
            String search = memberSearchField != null ? memberSearchField.getText() : "";
            memberList.updateEntries(search);
        }
    }

    private boolean checkPermission() {
        if (client.player == null) return false;
        String uuid = client.player.getUuid().toString();
        
        if (project.getScope() == Project.Scope.PERSONAL) {
             String owner = project.getOwnerUuid();
             if (owner == null) return true;
             return uuid.equals(owner);
        }
        
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        return PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx);
    }
    
    private boolean checkAdminPermission() {
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        return PermissionCenter.canPerform(Operation.ADD_MEMBER, role, ctx);
    }

    private boolean isOpClient() {
        return client != null && client.player != null && client.player.hasPermissionLevel(2);
    }

    private Role getCurrentRole() {
        if (isOpClient()) {
            return Role.OP;
        }
        if (client == null || client.player == null) {
            return Role.MEMBER;
        }
        String uuid = client.player.getUuid().toString();
        if (uuid.equals(project.getOwnerUuid())) {
            return Role.PROJECT_MANAGER;
        }
        Project.ProjectRole r = project.getMemberRole(uuid);
        if (r == Project.ProjectRole.LEAD) {
            return Role.LEAD;
        }
        return Role.MEMBER;
    }

    private void saveProject() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        
        project.setName(name);
        ClientProjectPackets.sendUpdateProject(project);
        close();
    }

    @Override
    public void close() {
        TodoListMod.getProjectManager().removeListener(this);
        client.setScreen(parent);
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (project != null && project.getId().equals(this.project.getId())) {
            this.project = project;
            if (client != null) {
                client.execute(() -> {
                    if (memberList != null) {
                        String search = memberSearchField != null ? memberSearchField.getText() : "";
                        memberList.updateEntries(search);
                    }
                });
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        boolean isTeam = project.getScope() == Project.Scope.TEAM;
        int w = 200;
        int h = isTeam ? 220 : 150;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        
        context.fill(x, y, x + w, y + h, 0xFF202020);
        context.drawBorder(x, y, w, h, 0xFFFFFFFF);
        
        context.drawText(textRenderer, title, x + 10, y + 10, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.translatable("gui.todolist.label.name"), x + 10, y + 25, 0xFFAAAAAA, false);
        
        if (isTeam) {
            context.drawText(textRenderer, Text.translatable("gui.todolist.label.members"), x + 10, y + 60, 0xFFAAAAAA, false);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }

    private class MemberListWidget extends ElementListWidget<MemberListWidget.MemberEntry> {
        public MemberListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
            
            this.setRenderBackground(false);
            this.setRenderHeader(false, 0);
            
            updateEntries("");
        }

        public void updateEntries(String search) {
            this.clearEntries();
            String q = search.toLowerCase().trim();

            String ownerUuid = project.getOwnerUuid();
            if (ownerUuid != null && !ownerUuid.isEmpty()) {
                Project.ProjectRole ownerRole = project.getMemberRole(ownerUuid);
                if (ownerRole == null) ownerRole = Project.ProjectRole.PROJECT_MANAGER;
                String name = project.getMemberName(ownerUuid);
                if (name == null || name.isEmpty()) {
                    name = ownerUuid;
                }
                try {
                    UUID id = UUID.fromString(ownerUuid);
                    if (client.getNetworkHandler() != null) {
                        PlayerListEntry ple = client.getNetworkHandler().getPlayerListEntry(id);
                        if (ple != null) {
                            name = ple.getProfile().getName();
                            project.setMemberName(ownerUuid, name);
                        }
                    }
                } catch (Exception e) {}
                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    this.addEntry(new MemberEntry(ownerUuid, ownerRole));
                }
            }

            for (Map.Entry<String, Project.ProjectRole> entry : project.getMembers().entrySet()) {
                String uuid = entry.getKey();
                if (ownerUuid != null && ownerUuid.equals(uuid)) continue;

                Project.ProjectRole role = entry.getValue();

                String name = project.getMemberName(uuid);
                if (name == null || name.isEmpty()) {
                    name = uuid;
                }
                try {
                    UUID id = UUID.fromString(uuid);
                    if (client.getNetworkHandler() != null) {
                        PlayerListEntry ple = client.getNetworkHandler().getPlayerListEntry(id);
                        if (ple != null) {
                            name = ple.getProfile().getName();
                            project.setMemberName(uuid, name);
                        }
                    }
                } catch (Exception e) {}

                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    this.addEntry(new MemberEntry(uuid, role));
                }
            }
        }
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
             // Access fields directly. In Yarn/Fabric 1.20.1, these are protected in EntryListWidget
             // width, height, top, bottom, left, right
             
             // Draw background
             context.fill(this.left, this.top, this.right, this.bottom, 0xFF101010);
             
             // Scissor
             double scale = client.getWindow().getScaleFactor();
             com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                 (int)(this.left * scale), 
                 (int)((client.getWindow().getScaledHeight() - this.bottom) * scale), 
                 (int)(this.width * scale), 
                 (int)(this.height * scale)
             );
             
             // Render list
             int itemHeight = this.itemHeight;
             
             for (int i = 0; i < this.children().size(); i++) {
                 int entryTop = this.getRowTop(i);
                 int entryBottom = entryTop + itemHeight;
                 
                 if (entryBottom >= this.top && entryTop <= this.bottom) {
                     MemberEntry entry = this.children().get(i);
                     int rowLeft = this.left + (this.width - getRowWidth()) / 2;
                     entry.render(context, i, entryTop, rowLeft, getRowWidth(), itemHeight, mouseX, mouseY, isMouseOver(mouseX, mouseY) && mouseY >= entryTop && mouseY < entryBottom, delta);
                 }
             }
             
             com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        }
        
        @Override
        protected void renderBackground(DrawContext context) {
            // Do nothing
        }
        
        @Override
        public int getRowWidth() {
            return this.width - 10;
        }

        @Override
        protected int getScrollbarPositionX() {
            return this.left + this.width + 6;
        }

        public class MemberEntry extends ElementListWidget.Entry<MemberEntry> {
            private final String uuid;
            private final Project.ProjectRole role;
            private final ButtonWidget roleBtn;
            private final ButtonWidget removeBtn;
            private String name;

            public MemberEntry(String uuid, Project.ProjectRole role) {
                this.uuid = uuid;
                this.role = role;
                
                // Resolve name
                this.name = project.getMemberName(uuid);
                if (this.name == null || this.name.isEmpty()) {
                    this.name = uuid;
                }
                try {
                    UUID id = UUID.fromString(uuid);
                    if (client.getNetworkHandler() != null) {
                         PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
                         if (entry != null) {
                             name = entry.getProfile().getName();
                             project.setMemberName(uuid, name);
                         }
                    }
                } catch (Exception e) {
                    // Ignore
                }

                this.roleBtn = ButtonWidget.builder(getRoleText(role), b -> toggleRole())
                        .dimensions(0, 0, 60, 16).build();
                this.roleBtn.active = canEditRole();

                this.removeBtn = ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
                    ClientProjectPackets.sendRemoveMember(project.getId(), uuid);
                }).dimensions(0, 0, 20, 16).build();
                
                boolean targetSelf = client.player != null && client.player.getUuid().toString().equals(uuid);
                boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(uuid);
                Role actorRole = getCurrentRole();
                Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
                this.removeBtn.active = PermissionCenter.canPerform(Operation.REMOVE_MEMBER, actorRole, ctx);
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                context.drawText(textRenderer, name, x + 2, y + 4, 0xFFFFFFFF, false);

                int btnY = y + (entryHeight - 16) / 2;
                int roleBtnX = x + entryWidth - 22 - 4 - 60;
                this.roleBtn.setX(roleBtnX);
                this.roleBtn.setY(btnY);
                this.roleBtn.render(context, mouseX, mouseY, tickDelta);

                this.removeBtn.setX(x + entryWidth - 22);
                this.removeBtn.setY(btnY);
                this.removeBtn.render(context, mouseX, mouseY, tickDelta);
            }

            private boolean canEditRole() {
                boolean targetSelf = client.player != null && client.player.getUuid().toString().equals(uuid);
                boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(uuid);
                Role actorRole = getCurrentRole();
                Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
                if (!PermissionCenter.canPerform(Operation.CHANGE_MEMBER_ROLE, actorRole, ctx)) {
                    return false;
                }
                return role == Project.ProjectRole.MEMBER || role == Project.ProjectRole.LEAD;
            }

            private void toggleRole() {
                if (!canEditRole()) return;

                Project.ProjectRole prevRole = project.getMemberRole(uuid);
                Project.ProjectRole newRole = role == Project.ProjectRole.MEMBER ? Project.ProjectRole.LEAD : Project.ProjectRole.MEMBER;

                project.addMember(uuid, newRole, project.getMemberName(uuid));
                if (memberList != null) {
                    String search = memberSearchField != null ? memberSearchField.getText() : "";
                    memberList.updateEntries(search);
                }

                if (!ClientPlayNetworking.canSend(ProjectPackets.UPDATE_MEMBER_ROLE_ID)) {
                    if (prevRole != null) project.addMember(uuid, prevRole, project.getMemberName(uuid));
                    if (memberList != null) {
                        String search = memberSearchField != null ? memberSearchField.getText() : "";
                        memberList.updateEntries(search);
                    }
                    if (client.player != null) {
                        client.player.sendMessage(Text.translatable("message.todolist.role_update_failed"), false);
                    }
                    return;
                }

                ClientProjectPackets.sendUpdateMemberRole(project.getId(), uuid, newRole);
            }

            @Override
            public List<? extends Element> children() {
                return List.of(roleBtn, removeBtn);
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return List.of(roleBtn, removeBtn);
            }

            private Text getRoleText(Project.ProjectRole role) {
                if (role == Project.ProjectRole.MEMBER) {
                    return Text.translatable("gui.todolist.role.member");
                }
                if (role == Project.ProjectRole.LEAD) {
                    return Text.translatable("gui.todolist.role.lead");
                }
                return Text.translatable("gui.todolist.role.manager");
            }
        }
    }
}
