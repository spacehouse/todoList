package com.todolist.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class FabricProjectPacketRegistrar {
    private FabricProjectPacketRegistrar() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(FabricProjectPayload.TYPE, FabricProjectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FabricProjectPayload.TYPE, FabricProjectPayload.CODEC);

        ProjectPackets.setServerPacketSender((player, channelId, buf) ->
                ServerPlayNetworking.send(player, FabricProjectPayload.of(channelId, buf)));

        ServerPlayNetworking.registerGlobalReceiver(FabricProjectPayload.TYPE, (payload, context) -> {
            ResourceLocation channelId = payload.channel();
            FriendlyByteBuf buf = payload.toBuf();
            if (channelId.equals(ProjectPackets.ADD_PROJECT_ID)) {
                ProjectPackets.onAddProjectPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.UPDATE_PROJECT_ID)) {
                ProjectPackets.onUpdateProjectPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.DELETE_PROJECT_ID)) {
                ProjectPackets.onDeleteProjectPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.ADD_MEMBER_ID)) {
                ProjectPackets.onAddMemberPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.REMOVE_MEMBER_ID)) {
                ProjectPackets.onRemoveMemberPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.UPDATE_MEMBER_ROLE_ID)) {
                ProjectPackets.onUpdateMemberRolePacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.REQUEST_JOIN_PROJECT_ID)) {
                ProjectPackets.onRequestJoinProjectPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.REQUEST_SYNC_PROJECTS_ID)) {
                ProjectPackets.onRequestSyncProjectsPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.SET_ACTIVE_PROJECT_ID)) {
                ProjectPackets.onSetActiveProjectPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID)) {
                ProjectPackets.onSetHudStarredProjectIdsPacket(context.server(), context.player(), buf);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ProjectPackets.onPlayerJoin(server, handler.getPlayer()));
    }
}
