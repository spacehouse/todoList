package com.todolist.network;

import com.todolist.compat.FabricNetworkingCompat;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Fabric 平台任务网络包注册器，统一委托到跨版本网络兼容层。
 */
public final class FabricTaskPacketRegistrar {
    private FabricTaskPacketRegistrar() {
    }

    /**
     * 注册 Fabric 平台任务相关收发包处理器。
     */
    public static void register() {
        TaskPackets.setServerPacketSender(FabricNetworkingCompat::sendToClient);

        FabricNetworkingCompat.registerServerReceiver(TaskPackets.REPLACE_TASKS_ID, (server, player, buf) ->
                TaskPackets.onReplaceTasksPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.TEAM_REPLACE_TASKS_ID, (server, player, buf) ->
                TaskPackets.onTeamReplaceTasksPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID, (server, player, buf) ->
                TaskPackets.onTeamReplaceTasksChunkedPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.TEAM_REQUEST_SYNC_ID, (server, player, buf) ->
                TaskPackets.onTeamRequestSyncPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.ADD_TASK_ID, (server, player, buf) ->
                TaskPackets.onAddTaskPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.UPDATE_TASK_ID, (server, player, buf) ->
                TaskPackets.onUpdateTaskPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.DELETE_TASK_ID, (server, player, buf) ->
                TaskPackets.onDeleteTaskPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.TOGGLE_TASK_ID, (server, player, buf) ->
                TaskPackets.onToggleTaskPacket(server, player, buf));
        FabricNetworkingCompat.registerServerReceiver(TaskPackets.TEAM_TOGGLE_TASK_ID, (server, player, buf) ->
                TaskPackets.onTeamToggleTaskPacket(server, player, buf));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                TaskPackets.onPlayerJoin(server, handler.getPlayer()));
    }
}
