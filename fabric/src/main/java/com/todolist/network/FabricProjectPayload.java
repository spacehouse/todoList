package com.todolist.network;

import com.todolist.TodoConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric 侧项目数据通道的自定义负载，负责打包与解包网络数据。
 */
public record FabricProjectPayload(String channelId, byte[] data) implements CustomPacketPayload {
    public static final Type<FabricProjectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "project_bridge"));

    public static final StreamCodec<FriendlyByteBuf, FabricProjectPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FabricProjectPayload::channelId,
                    ByteBufCodecs.BYTE_ARRAY, FabricProjectPayload::data,
                    FabricProjectPayload::new
            );

    /**
     * 从缓冲区读取数据并构建负载实例。
     */
    public static FabricProjectPayload of(ResourceLocation channelId, FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return new FabricProjectPayload(channelId.toString(), bytes);
    }

    /**
     * 将字符串通道还原为资源定位符。
     */
    public ResourceLocation channel() {
        return ResourceLocation.parse(channelId);
    }

    /**
     * 将负载数据写回缓冲区。
     */
    public FriendlyByteBuf toBuf() {
        return new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
    }

    /**
     * 返回负载类型以供网络注册。
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}