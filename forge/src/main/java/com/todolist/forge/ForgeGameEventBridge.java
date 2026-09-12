package com.todolist.forge;

import com.todolist.trigger.TaskTriggerService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge 平台游戏事件桥。
 * 订阅 Forge 事件总线，将击杀、破坏方块、合成、进度与库存变化转发给通用触发引擎。
 */
public class ForgeGameEventBridge {
    private static final int INVENTORY_SCAN_INTERVAL_TICKS = 20;
    private int tickCounter;

    /**
     * 转发实体死亡事件，判定击杀者为服务端玩家时推进 KILL_ENTITY 触发器。
     *
     * @param event 实体死亡事件
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
            TaskTriggerService.handleEntityKilled(player, entityId);
        }
    }

    /**
     * 转发方块破坏事件，推进 BREAK_BLOCK 触发器。
     *
     * @param event 方块破坏事件
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
            TaskTriggerService.handleBlockBroken(player, blockId);
        }
    }

    /**
     * 转发物品合成事件，按产物数量推进 CRAFT_ITEM 触发器。
     *
     * @param event 物品合成事件
     */
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack crafting = event.getCrafting();
            if (crafting != null && !crafting.isEmpty()) {
                String itemId = BuiltInRegistries.ITEM.getKey(crafting.getItem()).toString();
                TaskTriggerService.handleItemCrafted(player, itemId, crafting.getCount());
            }
        }
    }

    /**
     * 转发进度获得事件，推进 ADVANCEMENT 触发器。
     *
     * @param event 进度获得事件
     */
    @SubscribeEvent
    public void onAdvancement(AdvancementEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String advancementId = event.getAdvancement().getId().toString();
            TaskTriggerService.handleAdvancementAwarded(player, advancementId);
        }
    }

    /**
     * 服务端 tick 事件，按固定周期对存在收集触发任务的玩家执行库存重算。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tickCounter++;
        if (tickCounter % INVENTORY_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != null && TaskTriggerService.hasItemCollectWork(player)) {
                TaskTriggerService.handleInventoryChanged(player);
            }
        }
    }

    /**
     * 玩家退出事件，刷新并清理该玩家的触发器桶缓存。
     *
     * @param event 玩家退出事件
     */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TaskTriggerService.flushForPlayer(player);
        }
    }
}
