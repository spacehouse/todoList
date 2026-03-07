package com.todolist.forge.network;

import com.todolist.network.ProjectPackets;

public final class ForgeProjectPacketRegistrar {
    private ForgeProjectPacketRegistrar() {
    }

    public static void register() {
        ProjectPackets.setServerPacketSender(ForgeNetworkBridge::sendToPlayer);

        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.ADD_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onAddProjectPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.UPDATE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onUpdateProjectPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.DELETE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onDeleteProjectPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.ADD_MEMBER_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onAddMemberPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.REMOVE_MEMBER_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRemoveMemberPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.UPDATE_MEMBER_ROLE_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onUpdateMemberRolePacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.REQUEST_JOIN_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRequestJoinProjectPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.SET_ACTIVE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onSetActiveProjectPacket(server, player, buf));

        ForgeNetworkBridge.registerJoinListener((player, sender, server) ->
                ProjectPackets.onPlayerJoin(server, player));
    }
}
