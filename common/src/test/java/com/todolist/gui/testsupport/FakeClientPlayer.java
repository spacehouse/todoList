package com.todolist.gui.testsupport;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.StatsCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 假客户端玩家：提供 UUID、名称、权限与消息捕获能力，供 GUI 自测使用。
 */
public class FakeClientPlayer extends LocalPlayer {
    private UUID testUuid;
    private String testName;
    private boolean operator;
    private List<Component> clientMessages;

    /**
     * 构造方法仅用于满足编译要求，测试运行时通过 Unsafe 绕过。
     */
    protected FakeClientPlayer() {
        super(null, (ClientLevel) null, (ClientPacketListener) null, (StatsCounter) null, (ClientRecipeBook) null, false, false);
        throw new UnsupportedOperationException("请通过 FakeClientPlayer.create 创建测试玩家");
    }

    /**
     * 创建测试玩家实例，并初始化最小身份信息。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @param op 是否具备管理员权限
     * @return 测试玩家实例
     */
    public static FakeClientPlayer create(UUID uuid, String name, boolean op) {
        FakeClientPlayer player = GuiTestSupport.allocate(FakeClientPlayer.class);
        player.testUuid = uuid;
        player.testName = name;
        player.operator = op;
        player.clientMessages = new ArrayList<>();
        return player;
    }

    /**
     * 返回测试玩家 UUID。
     *
     * @return 玩家 UUID
     */
    @Override
    public UUID getUUID() {
        return testUuid;
    }

    /**
     * 返回测试玩家 UUID 字符串。
     *
     * @return 玩家 UUID 字符串
     */
    @Override
    public String getStringUUID() {
        return testUuid == null ? "" : testUuid.toString();
    }

    /**
     * 返回测试玩家显示名。
     *
     * @return 玩家显示名组件
     */
    @Override
    public Component getName() {
        return Component.literal(testName == null ? "" : testName);
    }

    /**
     * 返回测试玩家权限结果。
     *
     * @param level 请求的权限等级
     * @return true 表示具备对应权限
     */
    @Override
    public boolean hasPermissions(int level) {
        return operator || level <= 0;
    }

    /**
     * 捕获客户端提示消息，供后续断言使用。
     *
     * @param component 客户端消息
     * @param actionBar 是否为 action bar
     */
    @Override
    public void displayClientMessage(Component component, boolean actionBar) {
        if (clientMessages == null) {
            clientMessages = new ArrayList<>();
        }
        clientMessages.add(component);
    }

    /**
     * 返回本轮测试捕获到的客户端消息列表。
     *
     * @return 客户端消息列表
     */
    public List<Component> getClientMessages() {
        return clientMessages == null ? List.of() : List.copyOf(clientMessages);
    }
}
