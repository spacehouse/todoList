package com.todolist.neoforge.network;

import com.todolist.network.TaskPackets;

/**
 * NeoForge 平台任务数据包注册器。
 * 负责注册任务相关服务端处理器。
 */
public final class NeoForgeTaskPacketRegistrar {
    /**
     * 私有构造函数，禁止实例化。
     */
    private NeoForgeTaskPacketRegistrar() {
    }

    /**
     * 注册任务相关的数据包处理逻辑。
     */
    public static void register() {
        TaskPackets.setServerPacketSender(NeoForgeNetworkBridge::sendToPlayer);

        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onReplaceTasksPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.TEAM_REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamReplaceTasksPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.TEAM_REQUEST_SYNC_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamRequestSyncPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.ADD_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onAddTaskPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.UPDATE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onUpdateTaskPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.DELETE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onDeleteTaskPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onToggleTaskPacket(server, player, buf));
        NeoForgeNetworkBridge.registerServerReceiver(TaskPackets.TEAM_TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamToggleTaskPacket(server, player, buf));

        NeoForgeNetworkBridge.registerJoinListener((player, sender, server) ->
                TaskPackets.onPlayerJoin(server, player));
    }
}
