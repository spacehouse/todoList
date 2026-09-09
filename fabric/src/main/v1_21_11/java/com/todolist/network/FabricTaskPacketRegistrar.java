package com.todolist.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

public final class FabricTaskPacketRegistrar {
    private FabricTaskPacketRegistrar() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(FabricTaskPayload.TYPE, FabricTaskPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FabricTaskPayload.TYPE, FabricTaskPayload.CODEC);

        TaskPackets.setServerPacketSender((player, channelId, buf) ->
                ServerPlayNetworking.send(player, FabricTaskPayload.of(channelId, buf)));

        ServerPlayNetworking.registerGlobalReceiver(FabricTaskPayload.TYPE, (payload, context) -> {
            Identifier channelId = payload.channel();
            FriendlyByteBuf buf = payload.toBuf();
            if (channelId.equals(TaskPackets.REPLACE_TASKS_ID)) {
                TaskPackets.onReplaceTasksPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(TaskPackets.TEAM_REPLACE_TASKS_ID)) {
                TaskPackets.onTeamReplaceTasksPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
                TaskPackets.onTeamRequestSyncPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(TaskPackets.ADD_TASK_ID)) {
                TaskPackets.onAddTaskPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(TaskPackets.UPDATE_TASK_ID)) {
                TaskPackets.onUpdateTaskPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(TaskPackets.DELETE_TASK_ID)) {
                TaskPackets.onDeleteTaskPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(TaskPackets.TOGGLE_TASK_ID)) {
                TaskPackets.onToggleTaskPacket(context.server(), context.player(), buf);
                return;
            }
            if (channelId.equals(TaskPackets.TEAM_TOGGLE_TASK_ID)) {
                TaskPackets.onTeamToggleTaskPacket(context.server(), context.player(), buf);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                TaskPackets.onPlayerJoin(server, handler.getPlayer()));
    }
}
