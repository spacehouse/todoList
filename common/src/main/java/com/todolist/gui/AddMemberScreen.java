package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.project.Project;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 新增成员界面，负责从在线玩家列表中搜索候选成员并提交添加请求。
 */
public class AddMemberScreen extends Screen {
    private final Screen parent;
    private final String projectId;
    private EditBox searchField;
    private List<net.minecraft.client.multiplayer.PlayerInfo> allPlayers;
    private List<net.minecraft.client.multiplayer.PlayerInfo> filteredPlayers;
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
     * 创建新增成员界面。
     */
    public AddMemberScreen(Screen parent, String projectId) {
        super(Component.translatable("gui.todolist.add_member.title"));
        this.parent = parent;
        this.projectId = projectId;
    }

    /**
     * 返回当前过滤后的在线玩家列表快照，供同包测试断言筛选结果。
     *
     * @return 过滤后的在线玩家列表快照
     */
    List<net.minecraft.client.multiplayer.PlayerInfo> getFilteredPlayersForTest() {
        return filteredPlayers == null ? List.of() : List.copyOf(filteredPlayers);
    }

    /**
     * 返回当前滚动偏移量，供同包测试断言滚动行为。
     *
     * @return 当前滚动偏移量
     */
    int getScrollOffsetForTest() {
        return scrollOffset;
    }

    /**
     * 返回搜索输入框，供同包测试写入搜索内容。
     *
     * @return 搜索输入框
     */
    EditBox getSearchFieldForTest() {
        return searchField;
    }

    /**
     * 返回当前候选成员按钮数组快照，供同包测试触发点击。
     *
     * @return 候选成员按钮数组快照
     */
    Button[] getPlayerButtonsForTest() {
        return playerButtons == null ? new Button[0] : playerButtons.clone();
    }

    /**
     * 返回候选成员列表区域的中心 X 坐标，供同包测试驱动滚轮事件。
     *
     * @return 列表区域中心 X 坐标
     */
    double getListCenterXForTest() {
        return dialogLayout == null ? listX + (listWidth / 2.0D) : dialogLayout.getListCenterX();
    }

    /**
     * 返回候选成员列表区域的中心 Y 坐标，供同包测试驱动滚轮事件。
     *
     * @return 列表区域中心 Y 坐标
     */
    double getListCenterYForTest() {
        return dialogLayout == null ? listY + (listHeight / 2.0D) : dialogLayout.getListCenterY();
    }

    /**
     * 初始化搜索框、候选按钮和取消按钮。
     */
    @Override
    protected void init() {
        if (minecraft == null || minecraft.getConnection() == null) {
            return;
        }
        dialogLayout = buildDialogLayout();
        applyDialogLayout(dialogLayout);

        searchField = new EditBox(this.font, dialogLayout.dialogX(), dialogLayout.searchY(),
                dialogLayout.dialogWidth(), dialogLayout.searchHeight(), Component.empty());
        searchField.setHint(Component.translatable("gui.todolist.member.name"));
        searchField.setValue("");
        this.addRenderableWidget(searchField);

        allPlayers = new ArrayList<>();
        filteredPlayers = new ArrayList<>();
        Collection<net.minecraft.client.multiplayer.PlayerInfo> entries = minecraft.getConnection().getOnlinePlayers();
        allPlayers.addAll(entries);

        playerButtons = new Button[visibleRows];
        for (int i = 0; i < visibleRows; i++) {
            int btnY = listY + i * rowHeight;
            final int rowIndex = i;
            Button btn = Button.builder(Component.empty(), b -> {
                net.minecraft.client.multiplayer.PlayerInfo entry = getPlayerForRow(rowIndex);
                if (entry != null) {
                    addMember(entry);
                }
            }).bounds(dialogLayout.dialogX(), btnY, dialogLayout.dialogWidth(), 20).build();
            btn.active = false;
            btn.visible = false;
            this.addRenderableWidget(btn);
            playerButtons[i] = btn;
        }

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), b -> minecraft.setScreen(parent))
                .bounds(dialogLayout.cancelX(), dialogLayout.cancelY(),
                        dialogLayout.cancelWidth(), dialogLayout.cancelHeight())
                .build();
        this.addRenderableWidget(cancelButton);

        searchField.setResponder(text -> updateFilteredPlayers());
        updateFilteredPlayers();
        this.setFocused(searchField);
    }

    /**
     * 根据行号返回当前可见的候选玩家。
     */
    private net.minecraft.client.multiplayer.PlayerInfo getPlayerForRow(int rowIndex) {
        if (filteredPlayers == null || filteredPlayers.isEmpty()) {
            return null;
        }
        int index = scrollOffset + rowIndex;
        if (index < 0 || index >= filteredPlayers.size()) {
            return null;
        }
        return filteredPlayers.get(index);
    }

    /**
     * 根据搜索词和项目成员状态刷新候选成员列表。
     */
    private void updateFilteredPlayers() {
        if (allPlayers == null) {
            return;
        }
        filteredPlayers.clear();
        Project project = TodoListCommon.getProjectManager().getProject(projectId);
        String query = searchField == null ? "" : searchField.getValue();
        if (query == null) {
            query = "";
        }
        String q = query.trim().toLowerCase();
        for (net.minecraft.client.multiplayer.PlayerInfo entry : allPlayers) {
            if (isAlreadyMember(project, entry.getProfile().getId())) {
                continue;
            }
            String name = entry.getProfile().getName();
            if (name == null) {
                continue;
            }
            if (q.isEmpty() || name.toLowerCase().contains(q)) {
                filteredPlayers.add(entry);
            }
        }
        scrollOffset = 0;
        updatePlayerButtons();
    }

    /**
     * 构建当前窗口尺寸下的成员选择弹窗布局。
     *
     * @return 响应式布局快照
     */
    private MemberSelectionDialogLayout buildDialogLayout() {
        return MemberSelectionDialogLayout.create(this.width, this.height);
    }

    /**
     * 将布局快照同步到当前界面字段，供渲染和测试复用。
     *
     * @param layout 当前窗口下的成员选择弹窗布局
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
     * 判断目标玩家是否已经是项目拥有者或现有成员。
     */
    private boolean isAlreadyMember(Project project, UUID playerId) {
        if (project == null || playerId == null) {
            return false;
        }
        String uuid = playerId.toString();
        String ownerUuid = project.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isEmpty() && ownerUuid.equals(uuid)) {
            return true;
        }
        return project.getMembers().containsKey(uuid);
    }

    /**
     * 根据当前滚动偏移刷新候选成员按钮的显示内容。
     */
    private void updatePlayerButtons() {
        if (playerButtons == null) {
            return;
        }
        scrollOffset = clampPlayerScrollOffset();
        for (int i = 0; i < playerButtons.length; i++) {
            Button btn = playerButtons[i];
            net.minecraft.client.multiplayer.PlayerInfo entry = getPlayerForRow(i);
            if (entry == null) {
                btn.visible = false;
                btn.active = false;
                btn.setMessage(Component.empty());
            } else {
                String name = entry.getProfile().getName();
                btn.visible = true;
                btn.active = true;
                btn.setMessage(Component.nullToEmpty(name));
            }
        }
    }

    /**
     * 将新增成员弹窗的滚动偏移限制在候选列表的有效范围内。
     *
     * @return 修正后的滚动偏移
     */
    private int clampPlayerScrollOffset() {
        int totalItems = filteredPlayers == null ? 0 : filteredPlayers.size();
        return MemberSelectionDialogLayout.clampScrollOffset(scrollOffset, totalItems, visibleRows);
    }

    /**
     * 发送新增成员请求，并在项目设置界面中做乐观更新。
     */
    private void addMember(net.minecraft.client.multiplayer.PlayerInfo entry) {
        String name = entry.getProfile().getName();
        ClientBridge.ops().sendAddMember(projectId, entry.getProfile().getId().toString(), name);
        if (parent instanceof ProjectSettingsScreen) {
            ((ProjectSettingsScreen) parent).optimisticAddMember(entry.getProfile().getId().toString(), name);
        }
        onClose();
    }

    /**
     * 关闭界面并返回父界面。
     */
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /**
     * 处理候选成员列表区域内的滚轮滚动。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (dialogLayout != null && dialogLayout.isInsideList(mouseX, mouseY)) {
            if (filteredPlayers != null && !filteredPlayers.isEmpty()) {
                int totalItems = filteredPlayers.size();
                int maxOffset = MemberSelectionDialogLayout.getMaxScrollOffset(totalItems, visibleRows);
                if (amount < 0 && scrollOffset < maxOffset) {
                    scrollOffset++;
                    updatePlayerButtons();
                } else if (amount > 0 && scrollOffset > 0) {
                    scrollOffset--;
                    updatePlayerButtons();
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    /**
     * 渲染新增成员弹窗标题和搜索标签。
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.drawString(font, title, listX, 10, 0xFFFFFFFF, false);
        context.drawString(font, Component.translatable("gui.todolist.label.member_name"),
                listX, searchField.getY() - 10, 0xFFAAAAAA, false);

        super.render(context, mouseX, mouseY, delta);
    }
}
