package com.todolist.forge.network;

import com.todolist.network.TaskPackets;

/**
 * Forge 平台任务数据包注册器。
 * 负责注册任务相关的服务端数据包接收器。
 */
public final class ForgeTaskPacketRegistrar {
    private ForgeTaskPacketRegistrar() {
    }

    /**
     * 注册任务相关的数据包处理器。
     */
    public static void register() {
        TaskPackets.setServerPacketSender(ForgeNetworkBridge::sendToPlayer);

        ForgeNetworkBridge.registerServerReceiver(TaskPackets.REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onReplaceTasksPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.TEAM_REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamReplaceTasksPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamReplaceTasksChunkedPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.TEAM_REQUEST_SYNC_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamRequestSyncPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.ADD_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onAddTaskPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.UPDATE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onUpdateTaskPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.DELETE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onDeleteTaskPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onToggleTaskPacket(server, player, buf));
        ForgeNetworkBridge.registerServerReceiver(TaskPackets.TEAM_TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamToggleTaskPacket(server, player, buf));

        ForgeNetworkBridge.registerJoinListener((player, sender, server) ->
                TaskPackets.onPlayerJoin(server, player));
    }
}
