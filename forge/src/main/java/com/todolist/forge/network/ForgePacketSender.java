package com.todolist.forge.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface ForgePacketSender {
    void sendPacket(ResourceLocation channelId, FriendlyByteBuf buf);
}
