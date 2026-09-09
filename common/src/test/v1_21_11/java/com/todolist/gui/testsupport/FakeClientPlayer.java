package com.todolist.gui.testsupport;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Input;

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
    private int playedSoundCount;

    /**
     * 构造方法仅用于满足编译要求，测试运行时通过 Unsafe 绕过。
     */
    protected FakeClientPlayer() {
        // v1_21_6 覆盖：LocalPlayer 构造第 6 参由 boolean 改为 Input
        super(null, (ClientLevel) null, (ClientPacketListener) null, (StatsCounter) null, (ClientRecipeBook) null, (Input) null, false);
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
        player.playedSoundCount = 0;
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
     * 返回测试玩家权限集。
     *
     * @return 管理员返回 OWNER 级权限集，非管理员仅保留 ALL 级
     */
    @Override
    public PermissionSet permissions() {
        return operator ? LevelBasedPermissionSet.OWNER : LevelBasedPermissionSet.ALL;
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
     * 记录播放提示音次数，供后续断言交互反馈是否触发。
     *
     * @param sound 播放的声音事件
     * @param volume 音量
     * @param pitch 音调
     */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        playedSoundCount++;
    }

    /**
     * 返回本轮测试捕获到的客户端消息列表。
     *
     * @return 客户端消息列表
     */
    public List<Component> getClientMessages() {
        return clientMessages == null ? List.of() : List.copyOf(clientMessages);
    }

    /**
     * 返回本轮测试累计播放的提示音次数。
     *
     * @return 提示音播放次数
     */
    public int getPlayedSoundCount() {
        return playedSoundCount;
    }
}
