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

    @Override
    protected void init() {
        if (minecraft == null || minecraft.getConnection() == null) {
            return;
        }
        int guiWidth = Math.max(200, Math.min(320, this.width - 20));
        int x = (this.width - guiWidth) / 2;
        int topY = Math.max(20, this.height / 6);
        int searchHeight = 20;
        rowHeight = 22;
        int maxRowsByHeight = Math.max(4, (this.height - topY - 70) / rowHeight);
        visibleRows = Math.min(8, maxRowsByHeight);
        listWidth = guiWidth;
        listX = x;
        listY = topY + searchHeight + 6;
        listHeight = visibleRows * rowHeight;

        searchField = new EditBox(this.font, x, topY, guiWidth, searchHeight, Component.empty());
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
            }).bounds(x, btnY, guiWidth, 20).build();
            btn.active = false;
            btn.visible = false;
            this.addRenderableWidget(btn);
            playerButtons[i] = btn;
        }

        int cancelY = listY + listHeight + 10;
        Button cancel = Button.builder(Component.translatable("gui.todolist.cancel"), b -> {
            minecraft.setScreen(parent);
        }).bounds(x, cancelY, guiWidth, 20).build();
        this.addRenderableWidget(cancel);

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
        int maxOffset = 0;
        if (filteredPlayers != null) {
            maxOffset = Math.max(0, filteredPlayers.size() - visibleRows);
        }
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
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
        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            if (filteredPlayers != null && !filteredPlayers.isEmpty()) {
                int maxOffset = Math.max(0, filteredPlayers.size() - visibleRows);
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


