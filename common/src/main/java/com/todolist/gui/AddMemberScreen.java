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
 * 新增成员界面：从在线玩家列表中搜索并向服务端发送添加成员请求。
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
     * 返回当前过滤后的在线玩家列表快照，供同包测试代码断言过滤结果。
     *
     * @return 过滤后的在线玩家列表快照
     */
    List<net.minecraft.client.multiplayer.PlayerInfo> getFilteredPlayersForTest() {
        return filteredPlayers == null ? List.of() : List.copyOf(filteredPlayers);
    }

    /**
     * 返回当前滚动偏移量，供同包测试代码断言滚动行为。
     *
     * @return 当前滚动偏移量
     */
    int getScrollOffsetForTest() {
        return scrollOffset;
    }

    /**
     * 返回搜索输入框，供同包测试代码写入查询文本。
     *
     * @return 搜索输入框
     */
    EditBox getSearchFieldForTest() {
        return searchField;
    }

    /**
     * 返回当前成员列表按钮数组快照，供同包测试代码触发成员添加操作。
     *
     * @return 当前成员列表按钮数组快照
     */
    Button[] getPlayerButtonsForTest() {
        return playerButtons == null ? new Button[0] : playerButtons.clone();
    }

    /**
     * 返回成员列表区域中心点 X 坐标，供同包测试代码驱动滚轮事件。
     *
     * @return 成员列表区域中心点 X 坐标
     */
    double getListCenterXForTest() {
        return dialogLayout == null ? listX + (listWidth / 2.0D) : dialogLayout.getListCenterX();
    }

    /**
     * 返回成员列表区域中心点 Y 坐标，供同包测试代码驱动滚轮事件。
     *
     * @return 成员列表区域中心点 Y 坐标
     */
    double getListCenterYForTest() {
        return dialogLayout == null ? listY + (listHeight / 2.0D) : dialogLayout.getListCenterY();
    }

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

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), b -> {
            minecraft.setScreen(parent);
        }).bounds(dialogLayout.cancelX(), dialogLayout.cancelY(),
                dialogLayout.cancelWidth(), dialogLayout.cancelHeight()).build();
        this.addRenderableWidget(cancelButton);

        searchField.setResponder(text -> {
            updateFilteredPlayers();
        });
        updateFilteredPlayers();
        this.setFocused(searchField);
    }

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
     * 将布局快照中的坐标同步到当前界面字段，供渲染与测试复用。
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
     * 返回新增成员弹窗候选列表允许的最大滚动偏移。
     *
     * @return 最大滚动偏移
     */
    private int getMaxPlayerScrollOffset() {
        int totalItems = filteredPlayers == null ? 0 : filteredPlayers.size();
        return MemberSelectionDialogLayout.getMaxScrollOffset(totalItems, visibleRows);
    }

    private void addMember(net.minecraft.client.multiplayer.PlayerInfo entry) {
        String name = entry.getProfile().getName();
        ClientBridge.ops().sendAddMember(projectId, entry.getProfile().getId().toString(), name);
        if (parent instanceof ProjectSettingsScreen) {
            ((ProjectSettingsScreen) parent).optimisticAddMember(entry.getProfile().getId().toString(), name);
        }
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (dialogLayout != null && dialogLayout.isInsideList(mouseX, mouseY)) {
            if (filteredPlayers != null && !filteredPlayers.isEmpty()) {
                int maxOffset = getMaxPlayerScrollOffset();
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

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        
        context.drawString(font, title, listX, 10, 0xFFFFFFFF, false);
        context.drawString(font, Component.translatable("gui.todolist.label.member_name"), listX, searchField.getY() - 10, 0xFFAAAAAA, false);
        
        super.render(context, mouseX, mouseY, delta);
    }
}


