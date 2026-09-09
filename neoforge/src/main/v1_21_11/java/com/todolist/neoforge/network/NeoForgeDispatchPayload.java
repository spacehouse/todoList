package com.todolist.neoforge.network;

import com.todolist.TodoConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * NeoForge 通用桥接载荷，用于在单通道内封装多种业务消息。
 */
public record NeoForgeDispatchPayload(String channelId, byte[] payload) implements CustomPacketPayload {
    public static final Type<NeoForgeDispatchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TodoConstants.MOD_ID, "bridge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeDispatchPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, NeoForgeDispatchPayload::channelId,
                    ByteBufCodecs.BYTE_ARRAY, NeoForgeDispatchPayload::payload,
                    NeoForgeDispatchPayload::new
            );

    /**
     * 返回负载类型以供网络注册。
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}