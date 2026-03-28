package com.todolist.gui.testsupport;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.network.Connection;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 假客户端连接：提供在线玩家集合查询能力，供成员列表和任务分配场景测试使用。
 */
public class FakeClientConnection extends ClientPacketListener {
    private List<PlayerInfo> onlinePlayers;

    /**
     * 构造方法仅用于满足编译要求，测试运行时通过 Unsafe 绕过。
     */
    protected FakeClientConnection() {
        super((Minecraft) null, (Screen) null, (Connection) null, (ServerData) null, (GameProfile) null, (WorldSessionTelemetryManager) null);
        throw new UnsupportedOperationException("请通过 FakeClientConnection.create 创建测试连接");
    }

    /**
     * 创建空的测试连接实例。
     *
     * @return 测试连接实例
     */
    public static FakeClientConnection create() {
        FakeClientConnection connection = GuiTestSupport.allocate(FakeClientConnection.class);
        connection.onlinePlayers = new ArrayList<>();
        return connection;
    }

    /**
     * 批量设置在线玩家列表。
     *
     * @param players 在线玩家列表
     */
    public void setOnlinePlayers(Collection<? extends PlayerInfo> players) {
        this.onlinePlayers = players == null ? new ArrayList<>() : new ArrayList<>(players);
    }

    /**
     * 返回当前在线玩家列表。
     *
     * @return 在线玩家列表
     */
    @Override
    public Collection<PlayerInfo> getOnlinePlayers() {
        return onlinePlayers == null ? List.of() : List.copyOf(onlinePlayers);
    }

    /**
     * 按 UUID 返回在线玩家信息。
     *
     * @param uuid 玩家 UUID
     * @return 匹配的在线玩家信息
     */
    @Override
    public PlayerInfo getPlayerInfo(UUID uuid) {
        if (onlinePlayers == null || uuid == null) {
            return null;
        }
        for (PlayerInfo info : onlinePlayers) {
            if (info != null && info.getProfile() != null && uuid.equals(info.getProfile().getId())) {
                return info;
            }
        }
        return null;
    }
}
