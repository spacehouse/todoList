package com.todolist.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class FabricProjectPacketRegistrar {
    private FabricProjectPacketRegistrar() {
    }

    public static void register() {
        ProjectPackets.setServerPacketSender(ServerPlayNetworking::send);

        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.ADD_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onAddProjectPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.UPDATE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onUpdateProjectPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.DELETE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onDeleteProjectPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.ADD_MEMBER_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onAddMemberPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.REMOVE_MEMBER_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRemoveMemberPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.UPDATE_MEMBER_ROLE_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onUpdateMemberRolePacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.REQUEST_JOIN_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRequestJoinProjectPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(ProjectPackets.SET_ACTIVE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onSetActiveProjectPacket(server, player, buf));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ProjectPackets.onPlayerJoin(server, handler.getPlayer()));
    }
}
