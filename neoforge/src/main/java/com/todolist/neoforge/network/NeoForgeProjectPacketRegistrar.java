package com.todolist.neoforge.network;

import com.todolist.network.ProjectPackets;

/**
 * NeoForge 平台项目数据包注册器。
 * 负责注册项目相关服务端处理器。
 */
public final class NeoForgeProjectPacketRegistrar {
    /**
     * 私有构造函数，禁止实例化。
     */
    private NeoForgeProjectPacketRegistrar() {
    }

    /**
     * 注册项目相关的数据包处理逻辑。
     */
    public static void register() {
        ProjectPackets.setServerPacketSender(NeoForgeNetworkBridge::sendToPlayer);

        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.ADD_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onAddProjectPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.UPDATE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onUpdateProjectPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.DELETE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onDeleteProjectPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.ADD_MEMBER_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onAddMemberPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.REMOVE_MEMBER_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRemoveMemberPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.UPDATE_MEMBER_ROLE_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onUpdateMemberRolePacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.REQUEST_JOIN_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRequestJoinProjectPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.REQUEST_SYNC_PROJECTS_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onRequestSyncProjectsPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.SET_ACTIVE_PROJECT_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onSetActiveProjectPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.SET_HUD_STARRED_PROJECT_IDS_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onSetHudStarredProjectIdsPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(ProjectPackets.SET_HUD_VISIBILITY_ID, (server, player, handler, buf, responseSender) ->
                ProjectPackets.onSetHudVisibilityPacket(server, player, buf));

        NeoForgeNetworkBridge.registerJoinListener((player, sender, server) ->
                ProjectPackets.onPlayerJoin(server, player));
    }
}
