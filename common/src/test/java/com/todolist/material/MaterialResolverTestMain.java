package com.todolist.material;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 材料反推核心（{@link MaterialResolver}）的离线自测。
 *
 * <p>覆盖已定语义：数量向上取整传播、熔炼类输入即终止、中间产物不进清单、
 * 同材料跨分支合并、循环与深度保护、玩家覆盖配方与手动终止。
 */
public final class MaterialResolverTestMain {

    /** 目标铁锭数量。 */
    private static final String IRON_INGOT = "minecraft:iron_ingot";
    /** 粗铁（世界可获取的基础材料）。 */
    private static final String RAW_IRON = "minecraft:raw_iron";
    /** 铁粒。 */
    private static final String IRON_NUGGET = "minecraft:iron_nugget";
    /** 铁块。 */
    private static final String IRON_BLOCK = "minecraft:iron_block";

    /**
     * 工具类不需要实例化。
     */
    private MaterialResolverTestMain() {
    }

    /**
     * 执行材料反推核心测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldStopAtCookingInputs", MaterialResolverTestMain::shouldStopAtCookingInputs);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldPropagateCountsWithCeiling", MaterialResolverTestMain::shouldPropagateCountsWithCeiling);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldMergeLeafMaterialsAcrossBranches", MaterialResolverTestMain::shouldMergeLeafMaterialsAcrossBranches);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldMergeRepeatedInputsWithinSingleRecipe", MaterialResolverTestMain::shouldMergeRepeatedInputsWithinSingleRecipe);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldDetectCycleAndStop", MaterialResolverTestMain::shouldDetectCycleAndStop);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldRespectUserStopAndRecipeOverride", MaterialResolverTestMain::shouldRespectUserStopAndRecipeOverride);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldRespectMaxDepth", MaterialResolverTestMain::shouldRespectMaxDepth);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldForceExpandCookingInput", MaterialResolverTestMain::shouldForceExpandCookingInput);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldReturnEmptyPlanForInvalidInput", MaterialResolverTestMain::shouldReturnEmptyPlanForInvalidInput);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldOnlyExpandDirectMaterialsByDefault", MaterialResolverTestMain::shouldOnlyExpandDirectMaterialsByDefault);
        GuiTestSupport.runTestCase("MaterialResolverTestMain.shouldPreferOwnedCandidateAndRespectOverride", MaterialResolverTestMain::shouldPreferOwnedCandidateAndRespectOverride);
    }

    /**
     * 验证多候选材料（物品标签）的选择规则：
     * 玩家显式指定 &gt; 背包里已有的候选 &gt; 候选列表首项；节点始终保留全部候选供界面选择。
     */
    private static void shouldPreferOwnedCandidateAndRespectOverride() {
        String oakPlanks = "minecraft:oak_planks";
        String sprucePlanks = "minecraft:spruce_planks";
        String birchPlanks = "minecraft:birch_planks";
        MaterialRecipeIndex index = indexWith(
                recipe("torch", "minecraft:torch", 4, MaterialRecipeKind.CRAFTING,
                        in(RAW_IRON, 1), anyOf(1, oakPlanks, sprucePlanks, birchPlanks))
        );

        MaterialPlan fallback = MaterialResolver.resolve("minecraft:torch", 4, index, fullyExpanded());
        GuiTestSupport.assertEquals(1, fallback.totalFor(oakPlanks),
                "没有背包信息时应回退到候选列表首项");
        GuiTestSupport.assertEquals(3, fallback.root().children().get(1).candidates().size(),
                "节点应保留全部候选材料，供预览界面列出选择");

        MaterialPlan preferred = MaterialResolver.resolve("minecraft:torch", 4, index,
                withPreferredItems(birchPlanks));
        GuiTestSupport.assertEquals(1, preferred.totalFor(birchPlanks),
                "玩家已有某候选材料时应优先选择它");
        GuiTestSupport.assertEquals(0, preferred.totalFor(oakPlanks), "未选中的候选不应进入材料清单");

        MaterialPlan overridden = MaterialResolver.resolve("minecraft:torch", 4, index,
                withCandidateOverride(oakPlanks, sprucePlanks));
        GuiTestSupport.assertEquals(1, overridden.totalFor(sprucePlanks),
                "玩家显式指定的候选应优先于列表首项");
        GuiTestSupport.assertEquals(0, overridden.totalFor(oakPlanks), "被替换掉的候选不应进入材料清单");
    }

    /**
     * 验证终止条件：熔炼类配方的输入视为最终材料，不再继续反推。
     * 铁锭应推成粗铁，而不是被推成铁粒。
     */
    private static void shouldStopAtCookingInputs() {
        MaterialRecipeIndex index = indexWith(
                recipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, MaterialRecipeKind.SMELTING, in(RAW_IRON, 1)),
                recipe("iron_ingot_from_nuggets", IRON_INGOT, 1, MaterialRecipeKind.CRAFTING, in(IRON_NUGGET, 9))
        );

        MaterialPlan plan = MaterialResolver.resolve(IRON_INGOT, 64, index);

        GuiTestSupport.assertEquals(64, plan.totalFor(RAW_IRON), "铁锭 ×64 应推成粗铁 ×64（熔炼 1:1）");
        GuiTestSupport.assertEquals(0, plan.totalFor(IRON_NUGGET), "不应选出铁粒配方（9:1 产出比更差）");
        GuiTestSupport.assertTrue(plan.root().children().get(0).stopReason() == MaterialStopReason.COOKING_INPUT,
                "熔炼类配方的输入应标记为 COOKING_INPUT 终止原因");
    }

    /**
     * 验证数量传播向上取整，且中间产物不进入最终材料清单。
     */
    private static void shouldPropagateCountsWithCeiling() {
        MaterialRecipeIndex index = indexWith(
                recipe("iron_block", IRON_BLOCK, 1, MaterialRecipeKind.CRAFTING, in(IRON_INGOT, 9)),
                recipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, MaterialRecipeKind.SMELTING, in(RAW_IRON, 1))
        );

        MaterialPlan plan = MaterialResolver.resolve(IRON_BLOCK, 3, index, fullyExpanded());

        GuiTestSupport.assertEquals(27, plan.totalFor(RAW_IRON), "铁块 ×3 需要铁锭 ×27 → 粗铁 ×27");
        GuiTestSupport.assertEquals(0, plan.totalFor(IRON_INGOT), "中间产物（铁锭）不应进入最终材料清单");
        GuiTestSupport.assertEquals(0, plan.totalFor(IRON_BLOCK), "目标物本身不应进入最终材料清单");
    }

    /**
     * 验证跨分支的同材料会合并求和。
     */
    private static void shouldMergeLeafMaterialsAcrossBranches() {
        MaterialRecipeIndex index = indexWith(
                recipe("target", "test:target", 1, MaterialRecipeKind.CRAFTING, in("test:part_a", 1), in("test:part_b", 1)),
                recipe("part_a", "test:part_a", 1, MaterialRecipeKind.CRAFTING, in("minecraft:oak_log", 1)),
                recipe("part_b", "test:part_b", 1, MaterialRecipeKind.CRAFTING, in("minecraft:oak_log", 1))
        );

        MaterialPlan plan = MaterialResolver.resolve("test:target", 1, index, fullyExpanded());

        GuiTestSupport.assertEquals(2, plan.totalFor("minecraft:oak_log"), "两条分支各需 1 原木，应合并为 2");
        GuiTestSupport.assertEquals(1, plan.leafTotals().size(), "最终材料清单应只有原木一条");
    }

    /**
     * 验证同一配方内重复出现的输入会先合并，避免重复展开。
     */
    private static void shouldMergeRepeatedInputsWithinSingleRecipe() {
        MaterialRecipeIndex index = indexWith(
                recipe("target", "test:target", 1, MaterialRecipeKind.CRAFTING,
                        in("minecraft:oak_log", 1), in("minecraft:oak_log", 1), in("minecraft:iron_ingot", 1)),
                recipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, MaterialRecipeKind.SMELTING, in(RAW_IRON, 1))
        );

        MaterialPlan plan = MaterialResolver.resolve("test:target", 1, index, fullyExpanded());

        GuiTestSupport.assertEquals(2, plan.root().children().size(),
                "同一配方内重复的原木输入应合并成一个子节点（原木 + 铁锭）");
        GuiTestSupport.assertEquals(2, plan.totalFor("minecraft:oak_log"), "合并后的原木数量应为 2");
        GuiTestSupport.assertEquals(1, plan.totalFor(RAW_IRON), "铁锭 ×1 应推成粗铁 ×1");
    }

    /**
     * 验证循环依赖能被检测并停止，不会死循环：
     * 展开结果回到依赖链上的同一材料时不再展示，该层物品作为最终材料终止。
     */
    private static void shouldDetectCycleAndStop() {
        MaterialRecipeIndex index = indexWith(
                recipe("a", "test:a", 1, MaterialRecipeKind.CRAFTING, in("test:b", 1)),
                recipe("b", "test:b", 1, MaterialRecipeKind.CRAFTING, in("test:a", 1))
        );

        MaterialPlan plan = MaterialResolver.resolve("test:a", 1, index, fullyExpanded());

        GuiTestSupport.assertEquals(0, plan.totalFor("test:a"),
                "回到依赖链上已出现的材料时不应再计入最终材料清单，避免结果还是原材料本身");
        GuiTestSupport.assertEquals(1, plan.totalFor("test:b"),
                "环上被展开的中间产物应作为最终材料终止");
        GuiTestSupport.assertTrue(
                plan.root().children().get(0).stopReason() == MaterialStopReason.CYCLE,
                "环上节点应标记为 CYCLE 终止原因");
    }

    /**
     * 验证玩家手动终止与配方覆盖两种干预方式。
     */
    private static void shouldRespectUserStopAndRecipeOverride() {
        MaterialRecipeIndex index = indexWith(
                recipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, MaterialRecipeKind.SMELTING, in(RAW_IRON, 1)),
                recipe("iron_ingot_from_nuggets", IRON_INGOT, 1, MaterialRecipeKind.CRAFTING, in(IRON_NUGGET, 9))
        );

        MaterialPlan stopped = MaterialResolver.resolve(IRON_INGOT, 64, index,
                new MaterialResolveOptions(32, 2000, Map.of(), Set.of(IRON_INGOT), Set.of()));
        GuiTestSupport.assertEquals(64, stopped.totalFor(IRON_INGOT), "玩家标记「到此为止」后，铁锭本身即最终材料");
        GuiTestSupport.assertEquals(0, stopped.totalFor(RAW_IRON), "手动终止后不应再推前置材料");
        GuiTestSupport.assertTrue(stopped.root().stopReason() == MaterialStopReason.USER_STOPPED,
                "手动终止的节点应标记为 USER_STOPPED");

        MaterialPlan overridden = MaterialResolver.resolve(IRON_INGOT, 64, index,
                new MaterialResolveOptions(32, 2000, Map.of(IRON_INGOT, "iron_ingot_from_nuggets"), Set.of(), Set.of()));
        GuiTestSupport.assertEquals(576, overridden.totalFor(IRON_NUGGET), "覆盖为铁粒配方后应推成铁粒 ×576");
        GuiTestSupport.assertEquals(0, overridden.totalFor(RAW_IRON), "覆盖配方后不应再走熔炼路线");
    }

    /**
     * 验证深度上限生效。
     */
    private static void shouldRespectMaxDepth() {
        MaterialRecipeIndex index = indexWith(
                recipe("a", "test:a", 1, MaterialRecipeKind.CRAFTING, in("test:b", 1)),
                recipe("b", "test:b", 1, MaterialRecipeKind.CRAFTING, in("test:c", 1)),
                recipe("c", "test:c", 1, MaterialRecipeKind.CRAFTING, in("test:d", 1))
        );

        MaterialPlan plan = MaterialResolver.resolve("test:a", 1, index,
                new MaterialResolveOptions(2, 2000, Map.of(), Set.of(), Set.of(), 32));

        GuiTestSupport.assertEquals(1, plan.totalFor("test:c"), "深度上限 2 时应在第三层停止，并把该层物品作为最终材料");
        GuiTestSupport.assertEquals(0, plan.totalFor("test:d"), "超过深度上限的物品不应被展开");
    }

    /**
     * 验证「继续展开」可以覆盖熔炼类的终止条件。
     */
    private static void shouldForceExpandCookingInput() {
        MaterialRecipeIndex index = indexWith(
                recipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, MaterialRecipeKind.SMELTING, in(RAW_IRON, 1)),
                recipe("raw_iron", RAW_IRON, 1, MaterialRecipeKind.CRAFTING, in("minecraft:iron_ore", 1))
        );

        MaterialPlan defaultPlan = MaterialResolver.resolve(IRON_INGOT, 64, index);
        GuiTestSupport.assertEquals(64, defaultPlan.totalFor(RAW_IRON), "默认应在熔炼输入处停止，粗铁即最终材料");

        MaterialPlan forced = MaterialResolver.resolve(IRON_INGOT, 64, index,
                new MaterialResolveOptions(32, 2000, Map.of(), Set.of(), Set.of(IRON_INGOT), 32));
        GuiTestSupport.assertEquals(0, forced.totalFor(RAW_IRON), "要求继续展开后，粗铁应变为中间产物");
        GuiTestSupport.assertEquals(64, forced.totalFor("minecraft:iron_ore"), "继续展开后应推成铁矿石 ×64");
    }

    /**
     * 验证非法入参返回空计划而不是抛异常。
     */
    private static void shouldReturnEmptyPlanForInvalidInput() {
        MaterialRecipeIndex index = indexWith(
                recipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, MaterialRecipeKind.SMELTING, in(RAW_IRON, 1))
        );

        GuiTestSupport.assertTrue(MaterialResolver.resolve(null, 1, index).isEmpty(), "物品 ID 为空应返回空计划");
        GuiTestSupport.assertTrue(MaterialResolver.resolve("", 1, index).isEmpty(), "物品 ID 为空串应返回空计划");
        GuiTestSupport.assertTrue(MaterialResolver.resolve(IRON_INGOT, 1, null).isEmpty(), "索引为空应返回空计划");

        MaterialPlan unknown = MaterialResolver.resolve("minecraft:raw_iron", 5, index);
        GuiTestSupport.assertEquals(5, unknown.totalFor(RAW_IRON), "无配方的物品应整体作为最终材料（NO_RECIPE）");
        GuiTestSupport.assertTrue(unknown.root().stopReason() == MaterialStopReason.NO_RECIPE,
                "无配方节点应标记为 NO_RECIPE");
    }

    /**
     * 验证默认只展开根物品的直接材料：更深的依赖标记为 FOLDED（未展开），
     * 玩家对该节点「继续展开」后才继续反推。
     */
    private static void shouldOnlyExpandDirectMaterialsByDefault() {
        MaterialRecipeIndex index = indexWith(
                recipe("iron_block", IRON_BLOCK, 1, MaterialRecipeKind.CRAFTING, in(IRON_INGOT, 9)),
                recipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, MaterialRecipeKind.SMELTING, in(RAW_IRON, 1)),
                recipe("raw_iron", RAW_IRON, 1, MaterialRecipeKind.CRAFTING, in("minecraft:iron_ore", 1))
        );

        MaterialPlan plan = MaterialResolver.resolve(IRON_BLOCK, 1, index);

        GuiTestSupport.assertEquals(1, plan.root().children().size(), "默认应只展开目标物品的直接材料");
        GuiTestSupport.assertEquals(9, plan.totalFor(IRON_INGOT), "默认最终材料应是直接材料铁锭 ×9");
        GuiTestSupport.assertEquals(0, plan.totalFor("minecraft:iron_ore"), "默认不应继续反推到更深的材料");
        GuiTestSupport.assertTrue(plan.root().children().get(0).stopReason() == MaterialStopReason.FOLDED,
                "超过默认展开层数的节点应标记为 FOLDED");

        MaterialPlan expanded = MaterialResolver.resolve(IRON_BLOCK, 1, index,
                new MaterialResolveOptions(32, 2000, Map.of(), Set.of(), Set.of(IRON_INGOT)));
        GuiTestSupport.assertEquals(1, expanded.root().children().get(0).children().size(),
                "对铁锭要求继续展开后，应展示它的下一层材料");
        GuiTestSupport.assertTrue(expanded.root().children().get(0).children().get(0).isLeaf(),
                "继续展开一层后，更深的材料仍保持折叠");
    }

    /**
     * 构造全展开选项：忽略默认展开层数限制，用于验证反推算法本身的正确性。
     *
     * @return 全展开选项
     */
    private static MaterialResolveOptions fullyExpanded() {
        return new MaterialResolveOptions(32, 2000, Map.of(), Set.of(), Set.of(), 32);
    }

    /**
     * 构造配方反查索引。
     *
     * @param recipes 配方列表
     * @return 索引
     */
    private static MaterialRecipeIndex indexWith(MaterialRecipe... recipes) {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        for (MaterialRecipe recipe : recipes) {
            index.add(recipe);
        }
        return index;
    }

    /**
     * 构造配方映射。
     *
     * @param id       配方 ID
     * @param output   产物物品资源 ID
     * @param outCount 产出数量
     * @param kind     配方类型
     * @param inputs   输入项
     * @return 配方映射
     */
    private static MaterialRecipe recipe(String id,
                                        String output,
                                        int outCount,
                                        MaterialRecipeKind kind,
                                        MaterialIngredient... inputs) {
        return new MaterialRecipe(id, output, outCount, List.of(inputs), kind);
    }

    /**
     * 构造带候选覆盖的解析选项。
     *
     * @param candidateKey 候选组键（默认代表物品 ID）
     * @param chosenItemId 选定的物品资源 ID
     * @return 解析选项
     */
    private static MaterialResolveOptions withCandidateOverride(String candidateKey, String chosenItemId) {
        return new MaterialResolveOptions(32, 2000, Map.of(), Set.of(), Set.of(), 32,
                Map.of(candidateKey, chosenItemId), Set.of());
    }

    /**
     * 构造带「玩家已有物品」的解析选项。
     *
     * @param itemIds 已拥有的物品资源 ID
     * @return 解析选项
     */
    private static MaterialResolveOptions withPreferredItems(String... itemIds) {
        return new MaterialResolveOptions(32, 2000, Map.of(), Set.of(), Set.of(), 32, Map.of(), Set.of(itemIds));
    }

    /**
     * 构造多候选输入项。
     *
     * @param count   数量
     * @param itemIds 候选物品资源 ID
     * @return 输入项
     */
    private static MaterialIngredient anyOf(int count, String... itemIds) {
        return new MaterialIngredient(List.of(itemIds), count);
    }

    /**
     * 构造单候选输入项。
     *
     * @param itemId 物品资源 ID
     * @param count  数量
     * @return 输入项
     */
    private static MaterialIngredient in(String itemId, int count) {
        return new MaterialIngredient(List.of(itemId), count);
    }
}
