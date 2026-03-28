package com.todolist.gui;

import com.todolist.client.ClientBridge;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * 项目设置界面：编辑项目名称，并在团队项目中进行成员管理与角色调整。
 */
public class ProjectSettingsScreen extends Screen implements ProjectManager.ProjectChangeListener {
    private final Screen parent;
    private Project project;
    private EditBox nameField;
    private EditBox memberSearchField;
    private boolean canEdit;
    private MemberListWidget memberList;
    private Button addMemberBtn;
    private Button allowMemberCreateBtn;
    private Button saveButton;
    private boolean allowMemberCreate;

    /**
     * 创建项目设置界面。
     */
    public ProjectSettingsScreen(Screen parent, Project project) {
        super(Component.translatable("gui.todolist.project_settings.title"));
        this.parent = parent;
        this.project = project;
    }

    /**
     * 返回当前项目设置界面使用的项目对象，供同包测试代码断言刷新结果。
     *
     * @return 当前项目对象
     */
    Project getProjectForTest() {
        return project;
    }

    /**
     * 返回当前是否允许编辑项目，供同包测试代码断言权限分支。
     *
     * @return true 表示当前允许编辑
     */
    boolean canEditForTest() {
        return canEdit;
    }

    /**
     * 返回成员搜索输入框，供同包测试代码写入查询文本。
     *
     * @return 成员搜索输入框
     */
    EditBox getMemberSearchFieldForTest() {
        return memberSearchField;
    }

    /**
     * 返回新增成员按钮，供同包测试代码断言按钮状态。
     *
     * @return 新增成员按钮
     */
    Button getAddMemberButtonForTest() {
        return addMemberBtn;
    }

    /**
     * 返回项目名称输入框，供同包测试代码写入待保存名称。
     *
     * @return 项目名称输入框
     */
    EditBox getNameFieldForTest() {
        return nameField;
    }

    /**
     * 返回允许成员创建任务开关按钮，供同包测试代码驱动开关切换。
     *
     * @return 允许成员创建任务开关按钮
     */
    Button getAllowMemberCreateButtonForTest() {
        return allowMemberCreateBtn;
    }

    /**
     * 返回保存按钮，供同包测试代码触发保存流程。
     *
     * @return 保存按钮
     */
    Button getSaveButtonForTest() {
        return saveButton;
    }

    /**
     * 返回当前成员列表中可见成员名称快照，供同包测试代码断言搜索与刷新结果。
     *
     * @return 当前成员列表中可见成员名称快照
     */
    List<String> getVisibleMemberNamesForTest() {
        if (memberList == null) {
            return List.of();
        }
        return memberList.getVisibleMemberNamesForTest();
    }

    @Override
    protected void init() {
        TodoListCommon.getProjectManager().addListener(this);
        canEdit = checkPermission();
        boolean isTeam = project.getScope() == Project.Scope.TEAM;
        allowMemberCreate = project.isAllowMemberCreate();
        
        int w = Math.max(200, Math.min(360, width - 20));
        int h = isTeam ? Math.max(220, Math.min(320, height - 20)) : Math.max(150, Math.min(220, height - 20));
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        // Name Field
        nameField = new EditBox(font, x + 10, y + 35, w - 20, 20, Component.translatable("gui.todolist.project.name"));
        nameField.setValue(ProjectNameFormatter.toDisplayText(project).getString());
        nameField.setMaxLength(32);
        nameField.setEditable(canEdit);
        addRenderableWidget(nameField);

        // Team Member Management
        if (isTeam) {
            allowMemberCreateBtn = Button.builder(getAllowMemberCreateText(), button -> {
                allowMemberCreate = !allowMemberCreate;
                button.setMessage(getAllowMemberCreateText());
            }).bounds(x + 10, y + 70, w - 20, 16).build();
            allowMemberCreateBtn.active = canEdit;
            addRenderableWidget(allowMemberCreateBtn);

            // Member Search Field
            memberSearchField = new EditBox(font, x + 10, y + 90, w - 100, 16, Component.empty());
            memberSearchField.setHint(Component.translatable("gui.todolist.member.search"));
            memberSearchField.setResponder(text -> memberList.updateEntries(text));
            addRenderableWidget(memberSearchField);

            int listTop = y + 110;
            int listBottom = y + h - 40;
            memberList = new MemberListWidget(minecraft, w - 20, listBottom - listTop, listTop, listBottom, 20);
            memberList.setLeftPos(x + 10);
            addRenderableWidget(memberList);

            addMemberBtn = Button.builder(Component.translatable("gui.todolist.add_member"), button -> {
                minecraft.setScreen(new AddMemberScreen(this, project.getId()));
            }).bounds(x + w - 85, y + 90, 75, 16).build();
            addMemberBtn.active = checkAdminPermission();
            addRenderableWidget(addMemberBtn);
        }

        // Save Button
        saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> saveProject())
                .bounds(x + 80, y + h - 30, 50, 20).build();
        saveButton.active = canEdit;
        addRenderableWidget(saveButton);

        // Cancel Button
        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(x + w - 60, y + h - 30, 50, 20).build());
        
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
            String search = memberSearchField != null ? memberSearchField.getValue() : "";
            memberList.updateEntries(search);
        }
    }

    private boolean checkPermission() {
        if (minecraft.player == null) return false;
        String uuid = minecraft.player.getUUID().toString();
        
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
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    private Role getCurrentRole() {
        if (isOpClient()) {
            return Role.OP;
        }
        if (minecraft == null || minecraft.player == null) {
            return Role.MEMBER;
        }
        String uuid = minecraft.player.getUUID().toString();
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
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;

        String normalizedName = normalizeProjectNameForSave(name, project.getName());
        project.setName(normalizedName);
        project.setAllowMemberCreate(allowMemberCreate);
        ClientBridge.ops().sendUpdateProject(project);
        onClose();
    }

    private Component getAllowMemberCreateText() {
        return Component.translatable(
                "gui.todolist.project.allow_member_create",
                Component.translatable(allowMemberCreate ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off")
        );
    }

    /**
     * 规范化项目名称保存值。
     * 当用户未实际修改默认项目显示名时，保留原翻译键，避免把默认项目误保存为本地化文本。
     * @param editedName 输入框中的项目名
     * @param originalName 原始项目名
     * @return 应保存的项目名
     */
    private String normalizeProjectNameForSave(String editedName, String originalName) {
        if (!ProjectNameFormatter.isTranslationKey(originalName)) {
            return editedName;
        }
        String originalDisplay = ProjectNameFormatter.toDisplayText(originalName).getString();
        if (editedName.equals(originalDisplay)) {
            return originalName;
        }
        return editedName;
    }

    @Override
    public void onClose() {
        TodoListCommon.getProjectManager().removeListener(this);
        minecraft.setScreen(parent);
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (project != null && project.getId().equals(this.project.getId())) {
            this.project = project;
            if (minecraft != null) {
                minecraft.execute(() -> {
                    if (memberList != null) {
                        String search = memberSearchField != null ? memberSearchField.getValue() : "";
                        memberList.updateEntries(search);
                    }
                });
            }
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        boolean isTeam = project.getScope() == Project.Scope.TEAM;
        int w = Math.max(200, Math.min(360, width - 20));
        int h = isTeam ? Math.max(220, Math.min(320, height - 20)) : Math.max(150, Math.min(220, height - 20));
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        
        context.fill(x, y, x + w, y + h, 0xFF202020);
        context.renderOutline(x, y, w, h, 0xFFFFFFFF);
        
        context.drawString(font, title, x + 10, y + 10, 0xFFFFFFFF, false);
        context.drawString(font, Component.translatable("gui.todolist.label.name"), x + 10, y + 25, 0xFFAAAAAA, false);
        
        if (isTeam) {
            context.drawString(font, Component.translatable("gui.todolist.label.members"), x + 10, y + 60, 0xFFAAAAAA, false);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }

    private class MemberListWidget extends ContainerObjectSelectionList<MemberListWidget.MemberEntry> {
        public MemberListWidget(Minecraft client, int width, int height, int top, int bottom, int itemHeight) {
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
                    if (minecraft.getConnection() != null) {
                        PlayerInfo ple = minecraft.getConnection().getPlayerInfo(id);
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
                    if (minecraft.getConnection() != null) {
                        PlayerInfo ple = minecraft.getConnection().getPlayerInfo(id);
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

        /**
         * 返回当前成员列表可见成员名称快照，供同包测试代码断言过滤与刷新结果。
         *
         * @return 可见成员名称快照
         */
        public List<String> getVisibleMemberNamesForTest() {
            return this.children().stream()
                    .map(MemberEntry::getNameForTest)
                    .toList();
        }
        
        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
             // Access fields directly. In Yarn/Fabric 1.20.1, these are protected in EntryListWidget
             // width, height, top, bottom, left, right
             
             // Draw background
             context.fill(this.x0, this.y0, this.x1, this.y1, 0xFF101010);
             
             // Scissor
             double scale = minecraft.getWindow().getGuiScale();
             com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                 (int)(this.x0 * scale), 
                 (int)((minecraft.getWindow().getGuiScaledHeight() - this.y1) * scale), 
                 (int)(this.width * scale), 
                 (int)(this.height * scale)
             );
             
             // Render list
             int itemHeight = this.itemHeight;
             
             for (int i = 0; i < this.children().size(); i++) {
                 int entryTop = this.getRowTop(i);
                 int entryBottom = entryTop + itemHeight;
                 
                 if (entryBottom >= this.y0 && entryTop <= this.y1) {
                     MemberEntry entry = this.children().get(i);
                     int rowLeft = this.x0 + (this.width - getRowWidth()) / 2;
                     entry.render(context, i, entryTop, rowLeft, getRowWidth(), itemHeight, mouseX, mouseY, isMouseOver(mouseX, mouseY) && mouseY >= entryTop && mouseY < entryBottom, delta);
                 }
             }
             
             com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        }
        
        @Override
        protected void renderBackground(GuiGraphics context) {
            // Do nothing
        }
        
        @Override
        public int getRowWidth() {
            return this.width - 10;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.x0 + this.width + 6;
        }

        public class MemberEntry extends ContainerObjectSelectionList.Entry<MemberEntry> {
            private final String uuid;
            private final Project.ProjectRole role;
            private final Button roleBtn;
            private final Button removeBtn;
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
                    if (minecraft.getConnection() != null) {
                         PlayerInfo entry = minecraft.getConnection().getPlayerInfo(id);
                         if (entry != null) {
                             name = entry.getProfile().getName();
                             project.setMemberName(uuid, name);
                         }
                    }
                } catch (Exception e) {
                    // Ignore
                }

                this.roleBtn = Button.builder(getRoleText(role), b -> toggleRole())
                        .bounds(0, 0, 60, 16).build();
                this.roleBtn.active = canEditRole();

                this.removeBtn = Button.builder(Component.literal("X").withStyle(ChatFormatting.RED), b -> {
                    ClientBridge.ops().sendRemoveMember(project.getId(), uuid);
                }).bounds(0, 0, 20, 16).build();
                
                boolean targetSelf = minecraft.player != null && minecraft.player.getUUID().toString().equals(uuid);
                boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(uuid);
                Role actorRole = getCurrentRole();
                Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
                this.removeBtn.active = PermissionCenter.canPerform(Operation.REMOVE_MEMBER, actorRole, ctx);
            }

            @Override
            public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                context.drawString(font, name, x + 2, y + 4, 0xFFFFFFFF, false);

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
                boolean targetSelf = minecraft.player != null && minecraft.player.getUUID().toString().equals(uuid);
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
                    String search = memberSearchField != null ? memberSearchField.getValue() : "";
                    memberList.updateEntries(search);
                }

                if (!ClientBridge.ops().canSendUpdateMemberRole()) {
                    if (prevRole != null) project.addMember(uuid, prevRole, project.getMemberName(uuid));
                    if (memberList != null) {
                        String search = memberSearchField != null ? memberSearchField.getValue() : "";
                        memberList.updateEntries(search);
                    }
                    if (minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.translatable("message.todolist.role_update_failed"), false);
                    }
                    return;
                }

                ClientBridge.ops().sendUpdateMemberRole(project.getId(), uuid, newRole);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(roleBtn, removeBtn);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(roleBtn, removeBtn);
            }

            private Component getRoleText(Project.ProjectRole role) {
                if (role == Project.ProjectRole.MEMBER) {
                    return Component.translatable("gui.todolist.role.member");
                }
                if (role == Project.ProjectRole.LEAD) {
                    return Component.translatable("gui.todolist.role.lead");
                }
                return Component.translatable("gui.todolist.role.manager");
            }

            /**
             * 返回当前成员项解析后的显示名称，供同包测试代码断言列表内容。
             *
             * @return 成员显示名称
             */
            public String getNameForTest() {
                return name;
            }
        }
    }
}
