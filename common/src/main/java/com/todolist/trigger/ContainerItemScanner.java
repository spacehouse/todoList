package com.todolist.trigger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;

/**
 * 容器类物品（潜影盒、模组背包等）内部内容的物品统计。
 *
 * <p>版本敏感实现：本类（主源码树）为 1.20.1~1.20.4 形态，从物品 NBT 的
 * {@code BlockEntityTag.Items} 列表解析，且仅对方块类物品生效；1.20.5+
 * （DataComponents 时代）由 {@code src/main-versioned/mc1205} 覆盖目录提供，
 * 改从 {@code DataComponents.CONTAINER} 读取并对所有容器物品无条件展开。
 * 调用方（{@link TaskTriggerService}）与测试保持版本无关。
 *
 * <p>只展开一层：容器内再嵌套的容器按物品本身计数，其内部内容不再递归展开。
 */
public final class ContainerItemScanner {
    /** 方块实体容器数据在物品 NBT 中的键名。 */
    private static final String BLOCK_ENTITY_TAG_KEY = "BlockEntityTag";
    /** 容器内容列表在方块实体 NBT 中的键名。 */
    private static final String CONTAINER_ITEMS_KEY = "Items";
    /** 物品 NBT 中的资源 ID 键名。 */
    private static final String ITEM_ID_KEY = "id";
    /** 物品 NBT 中的数量键名。 */
    private static final String ITEM_COUNT_KEY = "Count";
    /** NBT 列表类型 ID。 */
    private static final int NBT_LIST_TYPE = 9;
    /** NBT 复合类型 ID。 */
    private static final int NBT_COMPOUND_TYPE = 10;

    private ContainerItemScanner() {
    }

    /**
     * 统计容器物品内部的命中数量并累加到计数表。
     *
     * @param stack   待展开的物品堆（可为容器物品）
     * @param targets 待统计物品资源 ID 集合
     * @param counts  统计结果累加目标
     */
    static void mergeContainerContents(ItemStack stack, Set<String> targets, Map<String, Integer> counts) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return;
        }
        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG_KEY);
        if (blockEntityTag == null || !blockEntityTag.contains(CONTAINER_ITEMS_KEY, NBT_LIST_TYPE)) {
            return;
        }
        ListTag items = blockEntityTag.getList(CONTAINER_ITEMS_KEY, NBT_COMPOUND_TYPE);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            String itemId = entry.getString(ITEM_ID_KEY);
            int count = entry.getByte(ITEM_COUNT_KEY) & 0xFF;
            if (itemId.isEmpty() || count <= 0 || !targets.contains(itemId)) {
                continue;
            }
            counts.merge(itemId, count, Integer::sum);
        }
    }
}
