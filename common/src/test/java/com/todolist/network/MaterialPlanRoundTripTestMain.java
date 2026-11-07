package com.todolist.network;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.material.MaterialIngredient;
import com.todolist.material.MaterialRecipe;
import com.todolist.material.MaterialRecipeKind;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料反推网络协议的序列化往返自检。
 *
 * <p>覆盖 1.21.2+ 服务端权威通路的三段载荷：物品清单、单物品配方映射
 * （含多候选输入与多配方），验证写出→读回字段一致。
 */
public final class MaterialPlanRoundTripTestMain {

    private MaterialPlanRoundTripTestMain() {
    }

    /**
     * 测试入口：逐用例执行并输出 [CMD][CASE] 标签，供 verify 日志解析。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("MaterialPlanRoundTripTestMain.shouldRoundTripMaterialItems", MaterialPlanRoundTripTestMain::shouldRoundTripMaterialItems);
        GuiTestSupport.runTestCase("MaterialPlanRoundTripTestMain.shouldRoundTripMaterialPlan", MaterialPlanRoundTripTestMain::shouldRoundTripMaterialPlan);
    }

    /**
     * 物品清单往返：写出多条 ID（含空串）读回应一致。
     */
    private static void shouldRoundTripMaterialItems() {
        List<String> itemIds = List.of("minecraft:iron_ingot", "minecraft:diamond", "");
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeMaterialItems(buf, itemIds);
        List<String> readBack = TaskPackets.readMaterialItems(buf);
        GuiTestSupport.assertEquals(itemIds, readBack, "物品清单往返应字段一致");
        GuiTestSupport.assertTrue(buf.readableBytes() == 0, "清单读回后缓冲应耗尽");
    }

    /**
     * 配方映射往返：多配方（多候选输入、多数量、产出数量）读回应字段一致。
     */
    private static void shouldRoundTripMaterialPlan() {
        List<MaterialRecipe> recipes = new ArrayList<>();
        recipes.add(new MaterialRecipe("minecraft:crafting/iron_pickaxe",
                "minecraft:iron_pickaxe", 1,
                List.of(new MaterialIngredient(List.of("minecraft:iron_ingot"), 1),
                        new MaterialIngredient(List.of("minecraft:stick", "minecraft:bamboo"), 1),
                        new MaterialIngredient(List.of("minecraft:iron_ingot"), 1)),
                MaterialRecipeKind.CRAFTING));
        recipes.add(new MaterialRecipe("minecraft:smelting/iron_ingot_from_iron_ore",
                "minecraft:iron_ingot", 1,
                List.of(new MaterialIngredient(List.of("minecraft:raw_iron"), 1)),
                MaterialRecipeKind.SMELTING));

        String itemId = "minecraft:iron_pickaxe";
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeMaterialPlan(buf, itemId, recipes);
        var readBack = TaskPackets.readMaterialPlan(buf);

        GuiTestSupport.assertEquals(itemId, readBack.getKey(), "目标物品 ID 往返应一致");
        List<MaterialRecipe> readRecipes = readBack.getValue();
        GuiTestSupport.assertEquals(recipes.size(), readRecipes.size(), "配方条数往返应一致");
        for (int i = 0; i < recipes.size(); i++) {
            MaterialRecipe expected = recipes.get(i);
            MaterialRecipe actual = readRecipes.get(i);
            GuiTestSupport.assertEquals(expected.kind(), actual.kind(), "配方 " + i + " 类型应一致");
            GuiTestSupport.assertEquals(expected.id(), actual.id(), "配方 " + i + " ID 应一致");
            GuiTestSupport.assertEquals(expected.outputItemId(), actual.outputItemId(), "配方 " + i + " 产物应一致");
            GuiTestSupport.assertEquals(expected.outputCount(), actual.outputCount(), "配方 " + i + " 产物数量应一致");
            GuiTestSupport.assertEquals(expected.inputs().size(), actual.inputs().size(), "配方 " + i + " 输入项数应一致");
            for (int j = 0; j < expected.inputs().size(); j++) {
                GuiTestSupport.assertEquals(expected.inputs().get(j), actual.inputs().get(j),
                        "配方 " + i + " 输入 " + j + " 应一致");
            }
        }
        GuiTestSupport.assertTrue(buf.readableBytes() == 0, "配方映射读回后缓冲应耗尽");
    }
}
