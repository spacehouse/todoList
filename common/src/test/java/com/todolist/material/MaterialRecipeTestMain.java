package com.todolist.material;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.List;

/**
 * 材料反推纯逻辑核心的离线自测：配方反查索引与默认选择策略。
 *
 * <p>只依赖中立模型（{@link MaterialRecipe} / {@link MaterialIngredient}），
 * 不依赖 MC 运行时，因此可完全离线覆盖。
 */
public final class MaterialRecipeTestMain {

    /**
     * 工具类不需要实例化。
     */
    private MaterialRecipeTestMain() {
    }

    /**
     * 执行材料反推索引与选择策略测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("MaterialRecipeTestMain.shouldIndexRecipesByOutputItem", MaterialRecipeTestMain::shouldIndexRecipesByOutputItem);
        GuiTestSupport.runTestCase("MaterialRecipeTestMain.shouldPreferHigherYieldRecipe", MaterialRecipeTestMain::shouldPreferHigherYieldRecipe);
        GuiTestSupport.runTestCase("MaterialRecipeTestMain.shouldPreferCraftingWhenYieldTies", MaterialRecipeTestMain::shouldPreferCraftingWhenYieldTies);
        GuiTestSupport.runTestCase("MaterialRecipeTestMain.shouldOrderByRecipeIdWhenYieldAndKindTie", MaterialRecipeTestMain::shouldOrderByRecipeIdWhenYieldAndKindTie);
        GuiTestSupport.runTestCase("MaterialRecipeTestMain.shouldTreatZeroInputRecipeAsWorst", MaterialRecipeTestMain::shouldTreatZeroInputRecipeAsWorst);
        GuiTestSupport.runTestCase("MaterialRecipeTestMain.shouldReturnNullWhenNoCandidateRecipe", MaterialRecipeTestMain::shouldReturnNullWhenNoCandidateRecipe);
    }

    /**
     * 验证索引按产物物品反查，并忽略非法条目。
     */
    private static void shouldIndexRecipesByOutputItem() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        MaterialRecipe smelting = singleInput("minecraft:iron_ingot_from_smelting_raw_iron",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.SMELTING, "minecraft:raw_iron", 1);
        MaterialRecipe nuggets = singleInput("minecraft:iron_ingot_from_nuggets",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.CRAFTING, "minecraft:iron_nugget", 9);
        index.add(smelting);
        index.add(nuggets);
        index.add(null);
        index.add(new MaterialRecipe("bad:id", "", 1, List.of(), MaterialRecipeKind.CRAFTING));

        GuiTestSupport.assertEquals(2, index.recipeCount(), "非法配方（null / 空产物 ID）不应计入索引");
        GuiTestSupport.assertEquals(2, index.findByOutput("minecraft:iron_ingot").size(), "铁锭应有两条可反查配方");
        GuiTestSupport.assertTrue(index.hasRecipeFor("minecraft:iron_ingot"), "铁锭应被判定为可合成");
        GuiTestSupport.assertFalse(index.hasRecipeFor("minecraft:raw_iron"), "粗铁无配方，不应被判定为可合成");
        GuiTestSupport.assertTrue(index.findByOutput("minecraft:not_exist").isEmpty(), "未知物品应返回空列表");

        index.clear();
        GuiTestSupport.assertEquals(0, index.recipeCount(), "清空后索引应为空");
    }

    /**
     * 验证默认策略选产出比更高的配方：铁锭应选熔炼（1:1）而不是铁粒（9:1）。
     */
    private static void shouldPreferHigherYieldRecipe() {
        MaterialRecipe smelting = singleInput("minecraft:iron_ingot_from_smelting_raw_iron",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.SMELTING, "minecraft:raw_iron", 1);
        MaterialRecipe nuggets = singleInput("minecraft:iron_ingot_from_nuggets",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.CRAFTING, "minecraft:iron_nugget", 9);
        MaterialRecipe blasting = singleInput("minecraft:iron_ingot_from_blasting_raw_iron",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.BLASTING, "minecraft:raw_iron", 1);

        List<MaterialRecipe> sorted = MaterialRecipeSelector.sort(List.of(nuggets, blasting, smelting));

        GuiTestSupport.assertEquals(smelting, MaterialRecipeSelector.selectPreferred(List.of(nuggets, blasting, smelting)),
                "产出比相同且非 crafting 时，应优先熔炉熔炼而不是高炉熔炼");
        GuiTestSupport.assertEquals(0, sorted.indexOf(smelting), "熔炼（1:1）应排在铁粒（9:1）之前");
        GuiTestSupport.assertEquals(2, sorted.indexOf(nuggets), "铁粒（9:1）产出比最差，应排在最后");
    }

    /**
     * 验证产出比相同时优先 crafting（不耗燃料）。
     */
    private static void shouldPreferCraftingWhenYieldTies() {
        MaterialRecipe smelting = singleInput("minecraft:iron_ingot_from_smelting_raw_iron",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.SMELTING, "minecraft:raw_iron", 1);
        MaterialRecipe crafting = singleInput("minecraft:iron_ingot_from_block",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.CRAFTING, "minecraft:iron_block", 1);

        MaterialRecipe preferred = MaterialRecipeSelector.selectPreferred(List.of(smelting, crafting));

        GuiTestSupport.assertEquals(crafting, preferred, "产出比相同时应优先 crafting");
    }

    /**
     * 验证产出比与配方类型都相同时，按配方 ID 字典序稳定排序。
     */
    private static void shouldOrderByRecipeIdWhenYieldAndKindTie() {
        MaterialRecipe later = singleInput("b_recipe", "minecraft:iron_ingot", 1,
                MaterialRecipeKind.SMELTING, "minecraft:raw_iron", 1);
        MaterialRecipe earlier = singleInput("a_recipe", "minecraft:iron_ingot", 1,
                MaterialRecipeKind.SMELTING, "minecraft:raw_iron", 1);

        List<MaterialRecipe> sorted = MaterialRecipeSelector.sort(List.of(later, earlier));

        GuiTestSupport.assertEquals(earlier, sorted.get(0), "完全并列时应按配方 ID 字典序升序，保证结果可复现");
        // 与入参顺序无关，重复排序结果一致
        GuiTestSupport.assertEquals(earlier, MaterialRecipeSelector.sort(List.of(earlier, later)).get(0),
                "排序结果不应依赖入参顺序");
    }

    /**
     * 验证无输入的配方被视为产出比最差，不会被误选为最优。
     */
    private static void shouldTreatZeroInputRecipeAsWorst() {
        MaterialRecipe noInput = new MaterialRecipe("minecraft:special_free", "minecraft:iron_ingot", 1,
                List.of(), MaterialRecipeKind.OTHER);
        MaterialRecipe normal = singleInput("minecraft:iron_ingot_from_smelting_raw_iron",
                "minecraft:iron_ingot", 1, MaterialRecipeKind.SMELTING, "minecraft:raw_iron", 1);

        GuiTestSupport.assertEquals(normal, MaterialRecipeSelector.selectPreferred(List.of(noInput, normal)),
                "无输入配方应排在最后，而不是被当成产出比最高");
    }

    /**
     * 验证空候选返回 null 而不是抛异常。
     */
    private static void shouldReturnNullWhenNoCandidateRecipe() {
        GuiTestSupport.assertNull(MaterialRecipeSelector.selectPreferred(List.of()), "空候选应返回 null");
        GuiTestSupport.assertNull(MaterialRecipeSelector.selectPreferred(null), "null 候选应返回 null");
        GuiTestSupport.assertTrue(MaterialRecipeSelector.sort(null).isEmpty(), "null 候选排序应返回空列表");
    }

    /**
     * 构造单输入配方。
     *
     * @param id        配方 ID
     * @param output    产物物品资源 ID
     * @param outCount  产出数量
     * @param kind      配方类型
     * @param input     输入物品资源 ID
     * @param inCount   输入数量
     * @return 配方映射
     */
    private static MaterialRecipe singleInput(String id,
                                             String output,
                                             int outCount,
                                             MaterialRecipeKind kind,
                                             String input,
                                             int inCount) {
        return new MaterialRecipe(id, output, outCount,
                List.of(new MaterialIngredient(List.of(input), inCount)), kind);
    }
}
