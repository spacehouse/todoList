package com.todolist.forge.network;

import com.todolist.network.ProjectPackets;

/**
 * Forge 平台项目数据包注册器。
 * 负责注册项目相关的服务端数据包接收器。
 */
public final class ForgeProjectPacketRegistrar {
    private ForgeProjectPacketRegistrar() {
    }

    /**
     * 注册项目相关的数据包处理器。
     */
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
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRequestSyncProjectsPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.SET_ACTIVE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onSetActiveProjectPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onSetHudStarredProjectIdsPacket(server, player, buf));

        ForgeNetworkBridge.registerJoinListener((player, sender, server) ->
                ProjectPackets.onPlayerJoin(server, player));
    }
}
