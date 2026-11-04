package com.todolist.trigger;

import com.todolist.gui.testsupport.GuiTestSupport;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 容器物品内部内容统计的离线自检（本文件为 1.20.5+ DataComponents 形态）。
 *
 * <p>1.20.1~1.20.4（物品 NBT 形态）的对应用例由主测试源码树中的同名类提供，
 * 构建期按目标版本择一生效。
 */
public final class ContainerItemScannerTestMain {

    private ContainerItemScannerTestMain() {
    }

    /**
     * 测试入口：逐用例执行并输出 [CMD][CASE] 标签，供 verify 日志解析。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        GuiTestSupport.bootstrapEnvironment();
        GuiTestSupport.runTestCase("ContainerItemScannerTestMain.shouldCountHeldItemsInsideContainerItem", ContainerItemScannerTestMain::shouldCountHeldItemsInsideContainerItem);
    }

    /**
     * 收集类触发器统计持有量时，应展开容器物品（潜影盒）内部内容，且只展开一层。
     */
    private static void shouldCountHeldItemsInsideContainerItem() {
        Set<String> targets = new HashSet<>(List.of(
                "minecraft:iron_ingot",
                "minecraft:diamond",
                "minecraft:shulker_box"
        ));
        Map<String, Integer> counts = new HashMap<>();

        // 外层潜影盒持有真实物品堆，走与生产一致的 ItemStack → DataComponents.CONTAINER 路径
        ItemStack shulkerStack = new ItemStack(Items.SHULKER_BOX);
        List<ItemStack> items = List.of(
                new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(Items.GOLD_INGOT, 5),
                new ItemStack(Items.SHULKER_BOX, 1)
        );
        shulkerStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));

        ContainerItemScanner.mergeContainerContents(shulkerStack, targets, counts);

        GuiTestSupport.assertEquals(64, counts.get("minecraft:iron_ingot"), "潜影盒内的铁锭应计入持有量");
        GuiTestSupport.assertEquals(1, counts.get("minecraft:shulker_box"), "潜影盒内嵌套的潜影盒应按物品本身计入一次");
        GuiTestSupport.assertNull(counts.get("minecraft:gold_ingot"), "未配置为目标的物品不应计入");
        GuiTestSupport.assertNull(counts.get("minecraft:diamond"), "嵌套容器内部内容不应被递归展开");
    }
}
