package com.todolist.mixin;

import com.todolist.trigger.TaskTriggerService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合成结果槽注入（1.21.2~1.21.8 覆盖版：{@code Recipe#getResultItem} 已从接口层移除，
 * 产物改经 {@code display()} 的物品堆形态读取，且 1.21.2 的 {@code getRecipeFor} 回归三参形态；
 * {@code craftSlots} 字段与 {@code onTake} 注入点不变）。
 * 1.20.x/1.21.1 形态由主源码树提供。
 * Fabric API 未提供公开的合成事件，这里在玩家取走合成产物（背包 2x2 合成栏与工作台共用
 * {@link ResultSlot}）时，把产物作为 CRAFT_ITEM 事件转发给通用触发引擎。
 *
 * 两种取走方式在 1.20.1 的差异（已按字节码与实机日志核对）：
 * - 鼠标取走：{@code AbstractContainerMenu#doClick} 传入的是还未搬走的产物堆，可直接取物品与数量；
 * - Shift + 左键：走 {@code InventoryMenu#quickMoveStack}，产物在调用 onTake 之前就被
 *   {@code moveItemStackTo} 搬进背包，传入堆已是 {@code minecraft:air} 空堆（类型也丢失），
 *   无法从参数获取任何信息。
 *
 * 因此这里做了回退：{@link ResultSlot#onTake} 的方法体是在**注入点之后**才消耗合成网格材料的，
 * 所以在 HEAD 处 {@code craftSlots} 仍然完整，可用原版同一套配方查询反查出产物（原版紧接着
 * 也是做这一次查询），从而覆盖 Shift 取走；此时数量取配方产物数量（如原木 → 木板 ×4）。
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {
    /** 合成网格，注入点处仍保留着本次合成的材料。 */
    @Shadow
    private CraftingContainer craftSlots;

    /**
     * 在合成产物被取走时转发合成事件。
     *
     * @param player 取走产物的玩家
     * @param stack  产物的直接参数（Shift 取走时为空堆）
     * @param callbackInfo 注入回调信息
     */
    @Inject(method = "onTake", at = @At("HEAD"))
    private void todolist$onCraftTake(Player player, ItemStack stack, CallbackInfo callbackInfo) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack product = stack;
        if (product == null || product.isEmpty()) {
            product = resolveCraftResult(player);
        }
        if (product == null || product.isEmpty()) {
            return;
        }
        Item item = product.getItem();
        if (item == null || item == Items.AIR) {
            return;
        }
        int crafted = Math.max(1, product.getCount());
        TaskTriggerService.handleItemCrafted(serverPlayer, BuiltInRegistries.ITEM.getKey(item).toString(), crafted);
    }

    /**
     * 按当前合成网格反查配方产物，用于 Shift 取走（参数堆已被搬空）时的物品与数量回退。
     *
     * @param player 取走产物的玩家
     * @return 配方产物；无匹配配方时返回空堆
     */
    private ItemStack resolveCraftResult(Player player) {
        if (player == null || craftSlots == null || craftSlots.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return player.getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftSlots.asCraftInput(), player.level())
                .map(holder -> resolveDisplayResult(holder.value()))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * 从配方展示形态读取产物物品堆（1.21.2+ 通路）。
     * 原版标准配方的展示结果即产物堆（{@code SlotDisplay.ItemStackSlotDisplay}）。
     *
     * @param recipe 匹配到的配方
     * @return 产物物品堆；无法解析时返回空堆
     */
    private static ItemStack resolveDisplayResult(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        try {
            for (net.minecraft.world.item.crafting.display.RecipeDisplay display : recipe.display()) {
                net.minecraft.world.item.crafting.display.SlotDisplay resultSlot = null;
                if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped) {
                    resultSlot = shaped.result();
                } else if (display instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) {
                    resultSlot = shapeless.result();
                }
                if (resultSlot instanceof net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay stackDisplay) {
                    ItemStack stack = stackDisplay.stack();
                    if (stack != null && !stack.isEmpty()) {
                        return stack;
                    }
                }
            }
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}
