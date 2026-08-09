package com.todolist.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class FabricTaskPacketRegistrar {
    private FabricTaskPacketRegistrar() {
    }

    public static void register() {
        TaskPackets.setServerPacketSender(ServerPlayNetworking::send);

        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onReplaceTasksPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.TEAM_REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamReplaceTasksPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.TEAM_REPLACE_TASKS_CHUNKED_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamReplaceTasksChunkedPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.TEAM_REQUEST_SYNC_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamRequestSyncPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.ADD_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onAddTaskPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.UPDATE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onUpdateTaskPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.DELETE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onDeleteTaskPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onToggleTaskPacket(server, player, buf));
        ServerPlayNetworking.registerGlobalReceiver(TaskPackets.TEAM_TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) ->
                TaskPackets.onTeamToggleTaskPacket(server, player, buf));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                TaskPackets.onPlayerJoin(server, handler.getPlayer()));
    }
}
