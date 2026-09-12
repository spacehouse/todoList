package com.todolist.fabric;

import com.todolist.trigger.TaskTriggerService;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric 平台游戏事件桥。
 * 订阅 Fabric API 事件，将击杀、破坏方块与库存变化转发给通用触发引擎。
 *
 * 平台局限与补齐（详见 docs/feat-trigger-completion.md）：
 * - 合成事件：Fabric API 无公开回调，由 {@code ResultSlotMixin} 注入 {@code ResultSlot#onTake} 补齐；
 * - 进度事件：Fabric API 无公开回调，由 {@code PlayerAdvancementsMixin} 注入 {@code PlayerAdvancements#award} 补齐。
 */
public final class FabricGameEventBridge {
    private static final int INVENTORY_SCAN_INTERVAL_TICKS = 20;
    private static int tickCounter;

    private FabricGameEventBridge() {
    }

    /**
     * 注册 Fabric 侧全部游戏事件监听。
     */
    public static void register() {
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, directSource, killedEntity) -> {
            if (directSource instanceof ServerPlayer player) {
                String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(killedEntity.getType()).toString();
                TaskTriggerService.handleEntityKilled(player, entityId);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                TaskTriggerService.handleBlockBroken(serverPlayer, blockId);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % INVENTORY_SCAN_INTERVAL_TICKS != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player != null && TaskTriggerService.hasItemCollectWork(player)) {
                    TaskTriggerService.handleInventoryChanged(player);
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.getPlayer() == null) {
                return;
            }
            TaskTriggerService.flushForPlayer(handler.getPlayer());
            AdvancementAwardGate.clearFor(handler.getPlayer().getUUID());
        });
    }
}
