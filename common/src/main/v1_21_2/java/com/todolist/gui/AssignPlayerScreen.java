package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.task.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 任务指派弹窗，负责展示当前项目成员、执行搜索过滤并完成成员指派。
 */
final class AssignPlayerScreen extends Screen {
    /**
     * 表示一个可被指派的成员。
     */
    static final class AssignableMember {
        final String uuid;
        final String displayName;

        /**
         * 创建一个可指派成员对象。
         *
         * @param uuid 成员 UUID
         * @param displayName 成员显示名称
         */
        AssignableMember(String uuid, String displayName) {
            this.uuid = uuid;
            this.displayName = displayName;
        }
    }

    private final TodoScreen parentScreen;
    private final Task targetTask;
    private final Supplier<Project> currentProjectSupplier;
    private final BiFunction<Project, String, String> memberDisplayNameResolver;
    private final BiConsumer<String, String> assignCallback;

    private EditBox searchField;
    private List<AssignableMember> allMembers;
    private List<AssignableMember> filteredMembers;
    private Button[] playerButtons;
    private Button cancelButton;
    private MemberSelectionDialogLayout dialogLayout;
    private int scrollOffset;
    private int visibleRows;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int rowHeight;

    /**
     * 创建任务指派弹窗。
     *
     * @param parentScreen 父级待办界面
     * @param targetTask 目标任务
     * @param currentProjectSupplier 当前项目提供者
     * @param memberDisplayNameResolver 成员显示名解析回调
     * @param assignCallback 成员指派回调
     */
    AssignPlayerScreen(TodoScreen parentScreen,
                       Task targetTask,
                       Supplier<Project> currentProjectSupplier,
                       BiFunction<Project, String, String> memberDisplayNameResolver,
                       BiConsumer<String, String> assignCallback) {
        super(Component.translatable("gui.todolist.assign_others"));
        this.parentScreen = parentScreen;
        this.targetTask = targetTask;
        this.currentProjectSupplier = currentProjectSupplier;
        this.memberDisplayNameResolver = memberDisplayNameResolver;
        this.assignCallback = assignCallback;
    }

    /**
     * 初始化弹窗中的搜索框、成员列表和取消按钮。
     */
    @Override
    protected void init() {
        super.init();
        if (minecraft == null) {
            return;
        }
        dialogLayout = buildDialogLayout();
        applyDialogLayout(dialogLayout);

        // v1_21_2 覆盖：EditBox 构造移除 x/y 参数，改用 setPosition 定位
        searchField = new EditBox(this.font, dialogLayout.dialogWidth(), dialogLayout.searchHeight(), Component.empty());
        searchField.setPosition(dialogLayout.dialogX(), dialogLayout.searchY());
        searchField.setHint(Component.translatable("gui.todolist.member.name"));
        searchField.setValue("");
        this.addRenderableWidget(searchField);

        allMembers = collectAssignableMembers();
        filteredMembers = new ArrayList<>();

        playerButtons = new Button[visibleRows];
        for (int i = 0; i < visibleRows; i++) {
            int btnY = listY + i * rowHeight;
            final int rowIndex = i;
            Button button = Button.builder(Component.empty(), b -> {
                AssignableMember entry = getMemberForRow(rowIndex);
                if (entry != null) {
                    applyAssignTo(entry.uuid, entry.displayName);
                }
            }).bounds(dialogLayout.dialogX(), btnY, dialogLayout.dialogWidth(), 20).build();
            button.active = false;
            button.visible = false;
            this.addRenderableWidget(button);
            playerButtons[i] = button;
        }

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), b -> minecraft.setScreen(parentScreen))
                .bounds(dialogLayout.cancelX(), dialogLayout.cancelY(),
                        dialogLayout.cancelWidth(), dialogLayout.cancelHeight())
                .build();
        this.addRenderableWidget(cancelButton);

        searchField.setResponder(text -> updateFilteredPlayers());
        updateFilteredPlayers();
        this.setFocused(searchField);
    }

    /**
     * 构建成员选择弹窗的布局参数。
     *
     * @return 成员选择弹窗布局
     */
    private MemberSelectionDialogLayout buildDialogLayout() {
        return MemberSelectionDialogLayout.create(this.width, this.height);
    }

    /**
     * 应用成员选择弹窗布局结果。
     *
     * @param layout 计算后的弹窗布局
     */
    private void applyDialogLayout(MemberSelectionDialogLayout layout) {
        if (layout == null) {
            return;
        }
        rowHeight = layout.rowHeight();
        visibleRows = layout.visibleRows();
        listWidth = layout.listWidth();
        listX = layout.listX();
        listY = layout.listY();
        listHeight = layout.listHeight();
    }

    /**
     * 收集当前项目中可供指派的成员列表。
     *
     * @return 可指派成员列表
     */
    private List<AssignableMember> collectAssignableMembers() {
        List<AssignableMember> members = new ArrayList<>();
        Project currentProject = currentProjectSupplier == null ? null : currentProjectSupplier.get();
        if (currentProject == null || currentProject.getScope() != Project.Scope.TEAM) {
            return members;
        }
        String ownerUuid = currentProject.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            members.add(new AssignableMember(ownerUuid, resolveDisplayName(currentProject, ownerUuid)));
        }
        List<AssignableMember> otherMembers = new ArrayList<>();
        for (String memberUuid : currentProject.getMembers().keySet()) {
            if (memberUuid == null || memberUuid.isBlank() || memberUuid.equals(ownerUuid)) {
                continue;
            }
            otherMembers.add(new AssignableMember(memberUuid, resolveDisplayName(currentProject, memberUuid)));
        }
        otherMembers.sort(Comparator.comparing(member -> member.displayName, String.CASE_INSENSITIVE_ORDER));
        members.addAll(otherMembers);
        return members;
    }

    /**
     * 解析指定项目成员的显示名称。
     *
     * @param project 当前项目
     * @param memberUuid 成员 UUID
     * @return 成员显示名称
     */
    private String resolveDisplayName(Project project, String memberUuid) {
        if (memberDisplayNameResolver == null) {
            return memberUuid == null ? "" : memberUuid;
        }
        return memberDisplayNameResolver.apply(project, memberUuid);
    }

    /**
     * 根据当前滚动状态返回指定行对应的成员。
     *
     * @param rowIndex 列表行索引
     * @return 对应成员，不存在时返回 {@code null}
     */
    private AssignableMember getMemberForRow(int rowIndex) {
        if (filteredMembers == null || filteredMembers.isEmpty()) {
            return null;
        }
        int index = scrollOffset + rowIndex;
        if (index < 0 || index >= filteredMembers.size()) {
            return null;
        }
        return filteredMembers.get(index);
    }

    /**
     * 根据搜索词刷新成员过滤结果。
     */
    private void updateFilteredPlayers() {
        if (allMembers == null || filteredMembers == null) {
            return;
        }
        filteredMembers.clear();
        String query = searchField == null ? "" : searchField.getValue();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        for (AssignableMember entry : allMembers) {
            String name = entry.displayName;
            if (name == null || name.isEmpty()) {
                continue;
            }
            if (normalizedQuery.isEmpty() || name.toLowerCase().contains(normalizedQuery)) {
                filteredMembers.add(entry);
            }
        }
        scrollOffset = 0;
        updatePlayerButtons();
    }

    /**
     * 根据当前过滤结果刷新成员按钮内容。
     */
    private void updatePlayerButtons() {
        if (playerButtons == null) {
            return;
        }
        scrollOffset = clampMemberScrollOffset();
        for (int i = 0; i < playerButtons.length; i++) {
            Button button = playerButtons[i];
            AssignableMember entry = getMemberForRow(i);
            if (entry == null) {
                button.visible = false;
                button.active = false;
                button.setMessage(Component.empty());
            } else {
                button.visible = true;
                button.active = true;
                button.setMessage(Component.nullToEmpty(entry.displayName));
            }
        }
    }

    /**
     * 约束成员列表滚动偏移，避免滚出可见范围。
     *
     * @return 修正后的滚动偏移量
     */
    private int clampMemberScrollOffset() {
        int totalItems = filteredMembers == null ? 0 : filteredMembers.size();
        return MemberSelectionDialogLayout.clampScrollOffset(scrollOffset, totalItems, visibleRows);
    }

    /**
     * 计算成员列表允许的最大滚动偏移量。
     *
     * @return 最大滚动偏移量
     */
    private int getMaxMemberScrollOffset() {
        int totalItems = filteredMembers == null ? 0 : filteredMembers.size();
        return MemberSelectionDialogLayout.getMaxScrollOffset(totalItems, visibleRows);
    }

    /**
     * 将当前任务指派给指定成员并返回父界面。
     *
     * @param uuid 成员 UUID
     * @param name 成员显示名称
     */
    private void applyAssignTo(String uuid, String name) {
        if (targetTask == null) {
            return;
        }
        if (assignCallback != null) {
            assignCallback.accept(uuid, name);
        }
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    /**
     * 处理成员列表区域的滚轮事件。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param horizontalAmount 水平滚动量
     * @param verticalAmount 垂直滚动量
     * @return 继续沿用父类结果
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dialogLayout != null && dialogLayout.isInsideList(mouseX, mouseY)) {
            if (filteredMembers != null && !filteredMembers.isEmpty()) {
                int maxOffset = getMaxMemberScrollOffset();
                if (verticalAmount < 0 && scrollOffset < maxOffset) {
                    scrollOffset++;
                    updatePlayerButtons();
                } else if (verticalAmount > 0 && scrollOffset > 0) {
                    scrollOffset--;
                    updatePlayerButtons();
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * 渲染任务指派弹窗。
     *
     * @param context 当前绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 渲染插值
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());
        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * 返回当前过滤后的成员列表，供测试使用。
     *
     * @return 当前过滤成员列表
     */
    List<AssignableMember> getFilteredMembersForTest() {
        return filteredMembers == null ? List.of() : List.copyOf(filteredMembers);
    }

    /**
     * 返回搜索输入框，供测试使用。
     *
     * @return 搜索输入框
     */
    EditBox getSearchFieldForTest() {
        return searchField;
    }

    /**
     * 返回成员按钮数组，供测试使用。
     *
     * @return 成员按钮数组
     */
    Button[] getPlayerButtonsForTest() {
        return playerButtons;
    }

    /**
     * 返回成员列表当前滚动偏移，供测试断言使用。
     *
     * @return 当前滚动偏移量
     */
    int getScrollOffsetForTest() {
        return scrollOffset;
    }

    /**
     * 返回成员列表当前可见行数，供测试断言使用。
     *
     * @return 当前可见行数
     */
    int getVisibleRowsForTest() {
        return visibleRows;
    }

    /**
     * 返回成员列表可点击区域中心点的横坐标，供测试使用。
     *
     * @return 列表中心横坐标
     */
    double getListCenterXForTest() {
        return dialogLayout == null ? listX + (listWidth / 2.0D) : dialogLayout.getListCenterX();
    }

    /**
     * 返回成员列表可点击区域中心点的纵坐标，供测试使用。
     *
     * @return 列表中心纵坐标
     */
    double getListCenterYForTest() {
        return dialogLayout == null ? listY + (listHeight / 2.0D) : dialogLayout.getListCenterY();
    }
}
