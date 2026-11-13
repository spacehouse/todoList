package com.todolist.neoforge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/**
 * NeoForge 网络发送器接口，用于抽象发送数据包。
 */
@FunctionalInterface
public interface NeoForgePacketSender {
    /**
     * 发送指定通道的数据包。
     * @param channelId 通道 ID
     * @param buf 数据缓冲区
     */
    void sendPacket(Identifier channelId, FriendlyByteBuf buf);
}
