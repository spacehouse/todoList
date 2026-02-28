package com.todolist.gui;

import com.todolist.client.ClientProjectPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AddMemberScreen extends Screen {
    private final Screen parent;
    private final String projectId;
    private TextFieldWidget searchField;
    private List<net.minecraft.client.network.PlayerListEntry> allPlayers;
    private List<net.minecraft.client.network.PlayerListEntry> filteredPlayers;
    private ButtonWidget[] playerButtons;
    private int scrollOffset;
    private int visibleRows;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int rowHeight;

    public AddMemberScreen(Screen parent, String projectId) {
        super(Text.translatable("gui.todolist.add_member.title"));
        this.parent = parent;
        this.projectId = projectId;
    }

    @Override
    protected void init() {
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }
        int guiWidth = 200;
        int x = (this.width - guiWidth) / 2;
        int topY = this.height / 6;
        int searchHeight = 20;
        rowHeight = 22;
        visibleRows = 8;
        listWidth = guiWidth;
        listX = x;
        listY = topY + searchHeight + 6;
        listHeight = visibleRows * rowHeight;

        searchField = new TextFieldWidget(this.textRenderer, x, topY, guiWidth, searchHeight, Text.empty());
        searchField.setPlaceholder(Text.translatable("gui.todolist.member.name"));
        searchField.setText("");
        this.addDrawableChild(searchField);

        allPlayers = new ArrayList<>();
        filteredPlayers = new ArrayList<>();
        Collection<net.minecraft.client.network.PlayerListEntry> entries = client.getNetworkHandler().getPlayerList();
        allPlayers.addAll(entries);

        playerButtons = new ButtonWidget[visibleRows];
        for (int i = 0; i < visibleRows; i++) {
            int btnY = listY + i * rowHeight;
            final int rowIndex = i;
            ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
                net.minecraft.client.network.PlayerListEntry entry = getPlayerForRow(rowIndex);
                if (entry != null) {
                    addMember(entry);
                }
            }).dimensions(x, btnY, guiWidth, 20).build();
            btn.active = false;
            btn.visible = false;
            this.addDrawableChild(btn);
            playerButtons[i] = btn;
        }

        int cancelY = listY + listHeight + 10;
        ButtonWidget cancel = ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), b -> {
            client.setScreen(parent);
        }).dimensions(x, cancelY, guiWidth, 20).build();
        this.addDrawableChild(cancel);

        searchField.setChangedListener(text -> {
            updateFilteredPlayers();
        });
        updateFilteredPlayers();
        this.setFocused(searchField);
    }

    private net.minecraft.client.network.PlayerListEntry getPlayerForRow(int rowIndex) {
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
        String query = searchField == null ? "" : searchField.getText();
        if (query == null) {
            query = "";
        }
        String q = query.trim().toLowerCase();
        for (net.minecraft.client.network.PlayerListEntry entry : allPlayers) {
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
            ButtonWidget btn = playerButtons[i];
            net.minecraft.client.network.PlayerListEntry entry = getPlayerForRow(i);
            if (entry == null) {
                btn.visible = false;
                btn.active = false;
                btn.setMessage(Text.empty());
            } else {
                String name = entry.getProfile().getName();
                btn.visible = true;
                btn.active = true;
                btn.setMessage(Text.of(name));
            }
        }
    }

    private void addMember(net.minecraft.client.network.PlayerListEntry entry) {
        String name = entry.getProfile().getName();
        ClientProjectPackets.sendAddMember(projectId, entry.getProfile().getId().toString(), name);
        if (parent instanceof ProjectSettingsScreen) {
            ((ProjectSettingsScreen) parent).optimisticAddMember(entry.getProfile().getId().toString(), name);
        }
        close();
    }

    @Override
    public void close() {
        client.setScreen(parent);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        
        context.drawText(textRenderer, title, listX, 10, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.translatable("gui.todolist.label.member_name"), listX, searchField.getY() - 10, 0xFFAAAAAA, false);
        
        super.render(context, mouseX, mouseY, delta);
    }
}
