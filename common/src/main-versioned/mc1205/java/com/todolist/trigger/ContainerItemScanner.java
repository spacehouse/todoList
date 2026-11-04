package com.todolist.trigger;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.Map;
import java.util.Set;

/**
 * 容器类物品（潜影盒、模组背包等）内部内容的物品统计。
 *
 * <p>本类为 1.20.5+（DataComponents 时代）形态：容器内容存放在
 * {@code DataComponents.CONTAINER}，统一覆盖原版与模组容器物品，
 * 调用点不再需要 BlockItem 类型过滤。1.20.1~1.20.4 的 NBT 形态由主源码树提供。
 *
 * <p>只展开一层：容器内再嵌套的容器按物品本身计数，其内部内容不再递归展开。
 */
public final class ContainerItemScanner {

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
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container == null) {
            return;
        }
        for (ItemStack inner : container.nonEmptyItems()) {
            if (inner == null || inner.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(inner.getItem()).toString();
            int count = inner.getCount();
            if (itemId.isEmpty() || count <= 0 || !targets.contains(itemId)) {
                continue;
            }
            counts.merge(itemId, count, Integer::sum);
        }
    }
}
