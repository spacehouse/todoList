package com.todolist.neoforge;

import com.todolist.trigger.TaskTriggerService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEarnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge 平台游戏事件桥。
 * 订阅 NeoForge 事件总线，将击杀、破坏方块、合成、进度与库存变化转发给通用触发引擎，
 * 与 Forge 线的 {@code ForgeGameEventBridge} 保持一致的转发语义（事件类名与包按 NeoForge 21.1 调整）。
 */
public class NeoForgeGameEventBridge {
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
     * 转发进度达成事件，推进 ADVANCEMENT 触发器。
     *
     * NeoForge 21.1 把进度事件拆为 {@link AdvancementEarnEvent}（进度真正完成）与
     * {@code AdvancementProgressEvent}（criteria 递增）两类，这里只监听前者：
     * 监听后者会导致同一条进度按条件数重复推进（见 docs/feat-trigger-completion.md 回归 R20）。
     *
     * @param event 进度达成事件
     */
    @SubscribeEvent
    public void onAdvancement(AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAdvancement() != null) {
            String advancementId = event.getAdvancement().id().toString();
            TaskTriggerService.handleAdvancementAwarded(player, advancementId);
        }
    }

    /**
     * 服务端 tick 事件，按固定周期对存在收集触发任务的玩家执行库存重算。
     * NeoForge 21.1 将 tick 事件拆分为 Pre / Post 两类，监听 Post 等价于 Forge 版的 END 阶段。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % INVENTORY_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
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
