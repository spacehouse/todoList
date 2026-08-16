package com.todolist.network;

import com.todolist.compat.FabricNetworkingCompat;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Fabric 平台项目网络包注册器，统一委托到跨版本网络兼容层。
 */
public final class FabricProjectPacketRegistrar {
    private FabricProjectPacketRegistrar() {
    }

    /**
     * 注册 Fabric 平台项目相关收发包处理器。
     */
    public static void register() {
        ProjectPackets.setServerPacketSender(FabricNetworkingCompat::sendToClient);

        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.ADD_PROJECT_ID, (server, player, buf) ->
                ProjectPackets.onAddProjectPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.UPDATE_PROJECT_ID, (server, player, buf) ->
                ProjectPackets.onUpdateProjectPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.DELETE_PROJECT_ID, (server, player, buf) ->
                ProjectPackets.onDeleteProjectPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.ADD_MEMBER_ID, (server, player, buf) ->
                ProjectPackets.onAddMemberPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.REMOVE_MEMBER_ID, (server, player, buf) ->
                ProjectPackets.onRemoveMemberPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.UPDATE_MEMBER_ROLE_ID, (server, player, buf) ->
                ProjectPackets.onUpdateMemberRolePacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.REQUEST_JOIN_PROJECT_ID, (server, player, buf) ->
                ProjectPackets.onRequestJoinProjectPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, (server, player, buf) ->
                ProjectPackets.onRequestSyncProjectsPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.SET_ACTIVE_PROJECT_ID, (server, player, buf) ->
                ProjectPackets.onSetActiveProjectPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID, (server, player, buf) ->
                ProjectPackets.onSetHudStarredProjectIdsPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(ProjectPackets.SET_HUD_VISIBILITY_ID, (server, player, buf) ->
                ProjectPackets.onSetHudVisibilityPacket(server, player, buf));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ProjectPackets.onPlayerJoin(server, handler.getPlayer()));
    }
}
