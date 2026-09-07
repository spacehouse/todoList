package com.todolist.gui;

import com.todolist.client.ClientBridge;
import com.todolist.config.ModConfig;
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
    /**
     * 项目设置页布局快照，统一保存弹窗和成员区域的边界信息。
     */
    private static final class SettingsLayout {
        private final int dialogX;
        private final int dialogY;
        private final int dialogWidth;
        private final int dialogHeight;
        private final int scopeBadgeX;
        private final int scopeBadgeY;
        private final int scopeBadgeWidth;
        private final int scopeBadgeHeight;
        private final int memberListX;
        private final int memberListY;
        private final int memberListWidth;
        private final int memberListHeight;

        /**
         * 创建一份项目设置页布局快照。
         */
        private SettingsLayout(int dialogX, int dialogY, int dialogWidth, int dialogHeight,
                               int scopeBadgeX, int scopeBadgeY, int scopeBadgeWidth, int scopeBadgeHeight,
                               int memberListX, int memberListY, int memberListWidth, int memberListHeight) {
            this.dialogX = dialogX;
            this.dialogY = dialogY;
            this.dialogWidth = dialogWidth;
            this.dialogHeight = dialogHeight;
            this.scopeBadgeX = scopeBadgeX;
            this.scopeBadgeY = scopeBadgeY;
            this.scopeBadgeWidth = scopeBadgeWidth;
            this.scopeBadgeHeight = scopeBadgeHeight;
            this.memberListX = memberListX;
            this.memberListY = memberListY;
            this.memberListWidth = memberListWidth;
            this.memberListHeight = memberListHeight;
        }
    }

    private final Screen parent;
    private Project project;
    private EditBox nameField;
    private EditBox memberSearchField;
    private boolean canEdit;
    private MemberListWidget memberList;
    private Button addMemberBtn;
    private Button allowMemberCreateBtn;
    private Button allowAllPlayersClaimCompleteBtn;
    private Button saveButton;
    private boolean allowMemberCreate;
    private boolean allowAllPlayersClaimComplete;
    private SettingsLayout layout;

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
     * 返回允许所有玩家领取/放弃/完成任务开关按钮，供同包测试代码驱动开关切换。
     *
     * @return 允许所有玩家领取/放弃/完成任务开关按钮
     */
    Button getAllowAllPlayersClaimCompleteButtonForTest() {
        return allowAllPlayersClaimCompleteBtn;
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

    /**
     * 返回对话框边界，供测试验证布局。
     *
     * @return 对话框边界数组
     */
    int[] getDialogBoundsForTest() {
        if (layout == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {layout.dialogX, layout.dialogY, layout.dialogWidth, layout.dialogHeight};
    }

    /**
     * 返回范围标识边界，供测试验证右上角布局。
     *
     * @return 范围标识边界数组
     */
    int[] getScopeBadgeBoundsForTest() {
        if (layout == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {layout.scopeBadgeX, layout.scopeBadgeY, layout.scopeBadgeWidth, layout.scopeBadgeHeight};
    }

    /**
     * 返回范围标识当前使用的语义键。
     *
     * @return 范围标识语义键
     */
    String getScopeBadgeTextForTest() {
        return project.getScope() == Project.Scope.TEAM ? "gui.todolist.scope.team" : "gui.todolist.scope.personal";
    }

    /**
     * 返回允许成员创建任务开关当前使用的语义键。
     *
     * @return 开关键值语义键
     */
    String getAllowMemberCreateStateKeyForTest() {
        return allowMemberCreate ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
    }

    /**
     * 返回允许所有玩家领取/放弃/完成任务开关当前使用的语义键。
     *
     * @return 开关键值语义键
     */
    String getAllowAllPlayersClaimCompleteStateKeyForTest() {
        return allowAllPlayersClaimComplete ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off";
    }

    /**
     * 返回成员列表边界，供测试验证按钮右对齐布局。
     *
     * @return 成员列表边界数组
     */
    int[] getMemberListBoundsForTest() {
        if (layout == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {layout.memberListX, layout.memberListY, layout.memberListWidth, layout.memberListHeight};
    }

    /**
     * 返回指定成员行的角色按钮边界。
     *
     * @param rowIndex 行索引
     * @return 角色按钮边界数组
     */
    int[] getMemberRoleButtonBoundsForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry == null || memberList == null) {
            return new int[] {0, 0, 0, 0};
        }
        entry.syncButtonLayoutForTest(rowIndex, memberList);
        return entry.getRoleButtonBoundsForTest();
    }

    /**
     * 返回指定成员行的移出按钮边界。
     *
     * @param rowIndex 行索引
     * @return 移出按钮边界数组
     */
    int[] getMemberRemoveButtonBoundsForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry == null || memberList == null) {
            return new int[] {0, 0, 0, 0};
        }
        entry.syncButtonLayoutForTest(rowIndex, memberList);
        return entry.getRemoveButtonBoundsForTest();
    }

    /**
     * 点击指定成员行的角色按钮，供测试驱动角色切换逻辑。
     *
     * @param rowIndex 行索引
     */
    void clickMemberRoleButtonForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry != null) {
            entry.clickRoleButtonForTest();
        }
    }

    /**
     * 点击指定成员行的移出按钮，供测试驱动移出成员逻辑。
     *
     * @param rowIndex 行索引
     */
    void clickMemberRemoveButtonForTest(int rowIndex) {
        MemberListWidget.MemberEntry entry = getMemberEntryForTest(rowIndex);
        if (entry != null) {
            entry.clickRemoveButtonForTest();
        }
    }

    @Override
    protected void init() {
        TodoListCommon.getProjectManager().addListener(this);
        canEdit = checkPermission();
        boolean isTeam = project.getScope() == Project.Scope.TEAM;
        allowMemberCreate = project.isAllowMemberCreate();
        allowAllPlayersClaimComplete = project.isAllowAllPlayersClaimComplete();
        layout = computeResponsiveLayout(isTeam);

        // Name Field
        int headerRightWidth = isTeam ? 118 : 88;
        int contentLeft = layout.dialogX + 10;
        int contentRight = layout.dialogX + layout.dialogWidth - 10;
        int nameFieldWidth = Math.max(90, layout.dialogWidth - headerRightWidth - 30);

        nameField = new EditBox(font, contentLeft, layout.dialogY + 35, nameFieldWidth, 20, Component.translatable("gui.todolist.project.name"));
        nameField.setValue(ProjectNameFormatter.toDisplayText(project).getString());
        nameField.setMaxLength(32);
        nameField.setEditable(canEdit);
        addRenderableWidget(nameField);

        // Team Member Management
        if (isTeam) {
            allowMemberCreateBtn = Button.builder(getAllowMemberCreateText(), button -> {
                allowMemberCreate = !allowMemberCreate;
                button.setMessage(getAllowMemberCreateText());
            }).bounds(contentRight - 108, layout.dialogY + 34, 108, 18).build();
            allowMemberCreateBtn.active = canEdit;
            addRenderableWidget(allowMemberCreateBtn);

            allowAllPlayersClaimCompleteBtn = Button.builder(getAllowAllPlayersClaimCompleteText(), button -> {
                allowAllPlayersClaimComplete = !allowAllPlayersClaimComplete;
                button.setMessage(getAllowAllPlayersClaimCompleteText());
            }).bounds(contentRight - 108, layout.dialogY + 54, 108, 18).build();
            allowAllPlayersClaimCompleteBtn.active = canEdit;
            addRenderableWidget(allowAllPlayersClaimCompleteBtn);

            memberSearchField = new EditBox(font, contentLeft, layout.dialogY + 95, layout.dialogWidth - 20, 16, Component.empty());
            memberSearchField.setHint(Component.translatable("gui.todolist.member.search"));
            memberSearchField.setResponder(text -> memberList.updateEntries(text));
            addRenderableWidget(memberSearchField);

            int listTop = layout.memberListY;
            memberList = new MemberListWidget(minecraft, layout.memberListWidth, layout.memberListHeight, listTop, 20);
            memberList.setX(layout.memberListX);
            addRenderableWidget(memberList);

            addMemberBtn = Button.builder(Component.translatable("gui.todolist.add_member"), button -> {
                minecraft.setScreen(new AddMemberScreen(this, project.getId()));
            }).bounds(contentRight - 70, layout.dialogY + 73, 60, 16).build();
            addMemberBtn.active = checkAdminPermission();
            addRenderableWidget(addMemberBtn);
        }

        // Save Button
        saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> saveProject())
                .bounds(layout.dialogX + 80, layout.dialogY + layout.dialogHeight - 30, 50, 20).build();
        saveButton.active = canEdit;
        addRenderableWidget(saveButton);

        // Cancel Button
        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(layout.dialogX + layout.dialogWidth - 60, layout.dialogY + layout.dialogHeight - 30, 50, 20).build());
        
        if (canEdit) {
            setFocused(nameField);
        }
    }

    /**
     * 计算项目设置页当前窗口尺寸下的布局快照。
     *
     * @param isTeam 当前是否为团队项目
     * @return 当前窗口下的布局快照
     */
    private SettingsLayout computeResponsiveLayout(boolean isTeam) {
        int dialogWidth = Math.max(220, Math.min(360, width - 20));
        int dialogHeight = isTeam ? Math.max(220, Math.min(320, height - 20)) : Math.max(150, Math.min(220, height - 20));
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;
        int scopeBadgeWidth = 64;
        int scopeBadgeHeight = 14;
        int scopeBadgeX = dialogX + dialogWidth - 10 - scopeBadgeWidth;
        int scopeBadgeY = dialogY + 12;
        int memberListX = dialogX + 10;
        int memberListY = dialogY + 115;
        int memberListWidth = dialogWidth - 20;
        int memberListHeight = Math.max(40, dialogHeight - 155);
        return new SettingsLayout(dialogX, dialogY, dialogWidth, dialogHeight,
                scopeBadgeX, scopeBadgeY, scopeBadgeWidth, scopeBadgeHeight,
                memberListX, memberListY, memberListWidth, memberListHeight);
    }

    /**
     * 渲染右上角的空间归属标识。
     *
     * @param context 当前绘制上下文
     */
    private void renderScopeBadge(GuiGraphics context) {
        if (layout == null) {
            return;
        }
        Component badgeText = project.getScope() == Project.Scope.TEAM
                ? Component.translatable("gui.todolist.scope.team")
                : Component.translatable("gui.todolist.scope.personal");
        context.fill(layout.scopeBadgeX, layout.scopeBadgeY,
                layout.scopeBadgeX + layout.scopeBadgeWidth, layout.scopeBadgeY + layout.scopeBadgeHeight, 0xFF303030);
        context.renderOutline(layout.scopeBadgeX, layout.scopeBadgeY, layout.scopeBadgeWidth, layout.scopeBadgeHeight, 0xFF888888);
        context.drawCenteredString(font, badgeText,
                layout.scopeBadgeX + layout.scopeBadgeWidth / 2,
                layout.scopeBadgeY + 3, 0xFFFFFFFF);
    }

    /**
     * 返回指定索引的成员行，供测试驱动成员行按钮行为。
     *
     * @param rowIndex 行索引
     * @return 对应成员行；不存在时返回 null
     */
    private MemberListWidget.MemberEntry getMemberEntryForTest(int rowIndex) {
        if (memberList == null || rowIndex < 0 || rowIndex >= memberList.children().size()) {
            return null;
        }
        return memberList.children().get(rowIndex);
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
        project.setAllowAllPlayersClaimComplete(allowAllPlayersClaimComplete);
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
     * 返回“允许所有玩家领取/放弃/完成任务”开关按钮文案。
     *
     * @return 开关按钮文案组件
     */
    private Component getAllowAllPlayersClaimCompleteText() {
        return Component.translatable(
                "gui.todolist.project.allow_all_players_claim_complete",
                Component.translatable(allowAllPlayersClaimComplete ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off")
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
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background is drawn manually in render to keep cross-loader consistency.
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());
        boolean isTeam = project.getScope() == Project.Scope.TEAM;
        layout = computeResponsiveLayout(isTeam);
        
        context.fill(layout.dialogX, layout.dialogY, layout.dialogX + layout.dialogWidth, layout.dialogY + layout.dialogHeight, 0xFF202020);
        context.renderOutline(layout.dialogX, layout.dialogY, layout.dialogWidth, layout.dialogHeight, 0xFFFFFFFF);
        
        context.drawString(font, title, layout.dialogX + 10, layout.dialogY + 10, 0xFFFFFFFF, false);
        context.drawString(font, Component.translatable("gui.todolist.label.name"), layout.dialogX + 10, layout.dialogY + 25, 0xFFAAAAAA, false);
        renderScopeBadge(context);
        
        if (isTeam) {
            context.drawString(font, Component.translatable("gui.todolist.label.members"), layout.dialogX + 10, layout.dialogY + 76, 0xFFAAAAAA, false);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }

    private class MemberListWidget extends ContainerObjectSelectionList<MemberListWidget.MemberEntry> {
        public MemberListWidget(Minecraft client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
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
        public int getRowWidth() {
            return this.getWidth() - 10;
        }

        // v1_21_6 覆盖：延续 v1_21_4 处理——AbstractSelectionList#getScrollbarPosition() 扩展点已移除，
        // 滚动条位置由原版 renderScrollbar 内部计算，基线的覆盖方法在此不保留

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

                this.removeBtn = Button.builder(Component.translatable("gui.todolist.remove_member").withStyle(ChatFormatting.RED), b -> {
                    ClientBridge.ops().sendRemoveMember(project.getId(), uuid);
                }).bounds(0, 0, 34, 16).build();
                
                boolean targetSelf = minecraft.player != null && minecraft.player.getUUID().toString().equals(uuid);
                boolean targetProjectManager = project.getOwnerUuid() != null && project.getOwnerUuid().equals(uuid);
                Role actorRole = getCurrentRole();
                Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager);
                this.removeBtn.active = PermissionCenter.canPerform(Operation.REMOVE_MEMBER, actorRole, ctx);
            }

            @Override
            public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                context.drawString(font, name, x + 2, y + 4, 0xFFFFFFFF, false);
                syncButtonLayout(x, y, entryWidth, entryHeight);
                this.removeBtn.render(context, mouseX, mouseY, tickDelta);
                this.roleBtn.render(context, mouseX, mouseY, tickDelta);
            }

            /**
             * 按当前成员行尺寸同步角色按钮与移出按钮位置。
             *
             * @param x 行起始 X
             * @param y 行起始 Y
             * @param entryWidth 行宽度
             * @param entryHeight 行高度
             */
            private void syncButtonLayout(int x, int y, int entryWidth, int entryHeight) {
                int btnY = y + (entryHeight - 16) / 2;
                int removeWidth = Math.max(28, Math.min(34, entryWidth / 6));
                int roleWidth = Math.min(60, Math.max(44, entryWidth / 4));
                int removeX = x + entryWidth - removeWidth - 2;
                int roleBtnX = removeX - 4 - roleWidth;
                this.roleBtn.setWidth(roleWidth);
                this.roleBtn.setX(roleBtnX);
                this.roleBtn.setY(btnY);
                this.removeBtn.setX(removeX);
                this.removeBtn.setY(btnY);
            }

            /**
             * 为测试代码同步按钮布局，避免在未渲染前读取到默认坐标。
             *
             * @param index 当前行索引
             * @param owner 所属成员列表组件
             */
            public void syncButtonLayoutForTest(int index, MemberListWidget owner) {
                int entryTop = owner.getRowTop(index);
                int rowLeft = owner.getX() + (owner.getWidth() - owner.getRowWidth()) / 2;
                syncButtonLayout(rowLeft, entryTop, owner.getRowWidth(), owner.itemHeight);
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

            /**
             * 返回角色按钮边界，供测试验证成员行右侧布局。
             *
             * @return 角色按钮边界数组
             */
            public int[] getRoleButtonBoundsForTest() {
                return new int[] {roleBtn.getX(), roleBtn.getY(), roleBtn.getWidth(), roleBtn.getHeight()};
            }

            /**
             * 返回移出按钮边界，供测试验证成员行右侧布局。
             *
             * @return 移出按钮边界数组
             */
            public int[] getRemoveButtonBoundsForTest() {
                return new int[] {removeBtn.getX(), removeBtn.getY(), removeBtn.getWidth(), removeBtn.getHeight()};
            }

            /**
             * 点击角色按钮，供测试驱动角色切换行为。
             */
            public void clickRoleButtonForTest() {
                roleBtn.onPress();
            }

            /**
             * 点击移出按钮，供测试驱动移出成员行为。
             */
            public void clickRemoveButtonForTest() {
                removeBtn.onPress();
            }
        }
    }
}
