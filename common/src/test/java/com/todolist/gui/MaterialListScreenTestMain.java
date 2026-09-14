package com.todolist.gui;

import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.material.MaterialIngredient;
import com.todolist.material.MaterialPreviewState;
import com.todolist.material.MaterialRecipe;
import com.todolist.material.MaterialRecipeIndex;
import com.todolist.material.MaterialRecipeKind;
import com.todolist.material.MaterialTaskContext;
import com.todolist.material.MaterialTaskMode;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MaterialListScreen 离线自测：树渲染、勾选、配方轮换、模式切换、数量改动与生成回调。
 *
 * <p>语义层已由 {@code MaterialPreviewStateTestMain} 覆盖，这里只验证界面渲染与事件分发。
 */
public final class MaterialListScreenTestMain {

    /** 测试玩家 UUID。 */
    private static final UUID OWNER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    /** 目标物：铁块。 */
    private static final String IRON_BLOCK = "minecraft:iron_block";
    /** 中间产物：铁锭。 */
    private static final String IRON_INGOT = "minecraft:iron_ingot";
    /** 熔炼输入：粗铁。 */
    private static final String RAW_IRON = "minecraft:raw_iron";
    /** 备选产物：铁粒。 */
    private static final String IRON_NUGGET = "minecraft:iron_nugget";
    /** 跨层级重复出现的材料：橡木木板。 */
    private static final String OAK_PLANKS = "minecraft:oak_planks";

    /**
     * 工具类不需要实例化。
     */
    private MaterialListScreenTestMain() {
    }

    /**
     * 执行材料预览界面测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldRenderTreeRowsWithSelectionState", MaterialListScreenTestMain::shouldRenderTreeRowsWithSelectionState);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldExpandNextLevelOnlyAfterContinueAction", MaterialListScreenTestMain::shouldExpandNextLevelOnlyAfterContinueAction);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldToggleLeafSelectionByCheckboxClick", MaterialListScreenTestMain::shouldToggleLeafSelectionByCheckboxClick);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldCycleRecipeForSelectedIntermediateNode", MaterialListScreenTestMain::shouldCycleRecipeForSelectedIntermediateNode);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldToggleModeByButton", MaterialListScreenTestMain::shouldToggleModeByButton);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldUpdatePlanWhenCountChanges", MaterialListScreenTestMain::shouldUpdatePlanWhenCountChanges);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldGenerateTasksAndCloseScreen", MaterialListScreenTestMain::shouldGenerateTasksAndCloseScreen);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldChooseCandidateForAlternativeMaterial", MaterialListScreenTestMain::shouldChooseCandidateForAlternativeMaterial);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldMarkCountSufficientWhenHeldItemsReachRequirement", MaterialListScreenTestMain::shouldMarkCountSufficientWhenHeldItemsReachRequirement);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldAllocateHeldItemsAcrossRepeatedMaterials", MaterialListScreenTestMain::shouldAllocateHeldItemsAcrossRepeatedMaterials);
    }

    /**
     * 验证背包持有量达到配方需求时数量文本标绿并给出「已够」文字提示，未达到时提示「缺 N」，辅助玩家判断还需要准备多少材料。
     */
    private static void shouldMarkCountSufficientWhenHeldItemsReachRequirement() {
        Harness harness = openScreen();
        MaterialListScreen screen = harness.screen;

        screen.setHeldItemCountsForTest(Map.of(IRON_BLOCK, 2, IRON_INGOT, 30));

        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getRowCountColorForTest(1), "铁锭已有 30 个（需求 27）应标绿");
        GuiTestSupport.assertEquals(heldEnoughText(), screen.getRowHeldSuffixForTest(1),
                "已够用时数量后应给出「已够」文字提示，而不是只靠颜色");
        GuiTestSupport.assertTrue(screen.getRowCountColorForTest(0) != MaterialListScreen.sufficientColorForTest(),
                "铁块已有 2 个（需求 3）不应标绿");
        GuiTestSupport.assertEquals(heldMissingText(1), screen.getRowHeldSuffixForTest(0),
                "数量不足时应提示还缺多少");
        GuiTestSupport.assertTrue(screen.getTargetCountColorForTest() != MaterialListScreen.sufficientColorForTest(),
                "目标物品持有量不足时目标行也不应标绿");
        GuiTestSupport.assertEquals(heldMissingText(1), screen.getTargetHeldSuffixForTest(),
                "目标物品数量不足时同样应提示还缺多少");

        screen.setHeldItemCountsForTest(Map.of(IRON_BLOCK, 3, IRON_INGOT, 27));

        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getRowCountColorForTest(0), "持有量刚好达到需求也应标绿");
        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getTargetCountColorForTest(), "目标物品持有量达标时目标行应标绿");
        GuiTestSupport.assertEquals(heldEnoughText(), screen.getTargetHeldSuffixForTest(),
                "目标物品已够用时也应给出文字提示");
    }

    /**
     * 验证同种材料出现在多个层级时按「自上而下、高层先占用」累计比较：
     * 第 1 层的橡木木板先占掉背包里的数量，第 2 层只能用剩余数量比较。
     */
    private static void shouldAllocateHeldItemsAcrossRepeatedMaterials() {
        Harness harness = openScreenForSharedPlanks();
        MaterialListScreen screen = harness.screen;

        screen.mouseClicked(screen.getRowTextXForTest(2), screen.getRowCenterYForTest(2), 0);
        ScreenDriver.click(screen.getStopButtonForTest());

        GuiTestSupport.assertEquals(
                List.of("NODE:test:target:1",
                        "LEAF:" + OAK_PLANKS + ":8:selected",
                        "NODE:test:part:1",
                        "LEAF:" + OAK_PLANKS + ":8:selected"),
                screen.getRowSnapshotsForTest(),
                "继续展开后橡木木板应同时出现在两个层级上");

        screen.setHeldItemCountsForTest(Map.of(OAK_PLANKS, 16));

        GuiTestSupport.assertEquals(8, screen.getRowHeldCountForTest(1), "第 1 层木板应分到 8 个");
        GuiTestSupport.assertEquals(8, screen.getRowHeldCountForTest(3), "第 2 层木板应分到剩余的 8 个");
        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getRowCountColorForTest(1), "共需 16 个木板且背包有 16 个时第 1 层应标绿");
        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getRowCountColorForTest(3), "共需 16 个木板且背包有 16 个时第 2 层也应标绿");

        screen.setHeldItemCountsForTest(Map.of(OAK_PLANKS, 12));

        GuiTestSupport.assertEquals(8, screen.getRowHeldCountForTest(1), "第 1 层木板应优先占掉 8 个");
        GuiTestSupport.assertEquals(4, screen.getRowHeldCountForTest(3), "第 2 层只能拿到剩余 4 个");
        GuiTestSupport.assertEquals(heldMissingText(4), screen.getRowHeldSuffixForTest(3),
                "第 2 层应提示还缺 4 个木板");
        GuiTestSupport.assertTrue(screen.getRowCountColorForTest(3) != MaterialListScreen.sufficientColorForTest(),
                "总量只有 12 个木板时第 2 层不应标绿");

        screen.setHeldItemCountsForTest(Map.of(OAK_PLANKS, 8));

        GuiTestSupport.assertEquals(0, screen.getRowHeldCountForTest(3), "第 1 层占满后第 2 层不应重复使用同一批数量");
        GuiTestSupport.assertEquals(heldMissingText(8), screen.getRowHeldSuffixForTest(3),
                "第 2 层没有分到持有量时应提示缺少全部 8 个");
    }

    /**
     * 构造「已够」标注的期望文本。
     *
     * @return 本地化后的标注文本
     */
    private static String heldEnoughText() {
        return Component.translatable("gui.todolist.material_preview.held_enough").getString();
    }

    /**
     * 构造「缺 N」标注的期望文本。
     *
     * @param missingCount 缺少数量
     * @return 本地化后的标注文本
     */
    private static String heldMissingText(int missingCount) {
        return Component.translatable("gui.todolist.material_preview.held_missing", missingCount).getString();
    }

    /**
     * 验证多候选材料节点会启用「选择材料」按钮，选定后重新解析为该材料。
     */
    private static void shouldChooseCandidateForAlternativeMaterial() {
        String oakPlanks = "minecraft:oak_planks";
        String birchPlanks = "minecraft:birch_planks";
        Harness harness = openScreenForPlanks();
        MaterialListScreen screen = harness.screen;

        screen.mouseClicked(screen.getRowTextXForTest(1), screen.getRowCenterYForTest(1), 0);
        GuiTestSupport.assertEquals("LEAF:" + oakPlanks + ":1:selected",
                screen.getRowSnapshotsForTest().get(1), "默认应选中候选列表首项木板");
        GuiTestSupport.assertTrue(screen.getCandidateButtonForTest().active,
                "该材料有多个候选时「选择材料」按钮应可用");

        screen.applyCandidateSelectionForTest(oakPlanks, birchPlanks);

        GuiTestSupport.assertEquals(birchPlanks,
                screen.getPreviewStateForTest().getCandidateOverrides().get(oakPlanks),
                "选择结果应记录为候选覆盖");
        GuiTestSupport.assertEquals(1, screen.getPlanForTest().totalFor(birchPlanks),
                "重新解析后应使用选定的木板");
        GuiTestSupport.assertEquals("LEAF:" + birchPlanks + ":1:selected",
                screen.getRowSnapshotsForTest().get(1), "行快照应显示选定后的材料");
    }

    /**
     * 验证默认只展开目标物品的直接材料：首屏应渲染铁块 → 铁锭两行，
     * 并给出正确的层级信息，供树形连线渲染使用。
     */
    private static void shouldRenderTreeRowsWithSelectionState() {
        Harness harness = openScreen();

        GuiTestSupport.assertEquals(
                List.of("NODE:" + IRON_BLOCK + ":3", "LEAF:" + IRON_INGOT + ":27:selected"),
                harness.screen.getRowSnapshotsForTest(),
                "默认应只渲染目标物品与它的直接材料两行，直接材料默认勾选");
        GuiTestSupport.assertEquals(
                List.of("0|NODE:" + IRON_BLOCK + ":3", "1|LEAF:" + IRON_INGOT + ":27:selected"),
                harness.screen.getRowSnapshotsWithDepthForTest(),
                "直接材料应位于第 1 层，用于树形缩进与连线");
        GuiTestSupport.assertEquals(0, harness.screen.getRowDepthForTest(0), "目标物品层级应为 0");
        GuiTestSupport.assertEquals(1, harness.screen.getRowDepthForTest(1), "直接材料层级应为 1");
    }

    /**
     * 验证点击「继续展开」后才会展示下一层材料。
     */
    private static void shouldExpandNextLevelOnlyAfterContinueAction() {
        Harness harness = openScreen();
        MaterialListScreen screen = harness.screen;

        screen.mouseClicked(screen.getRowTextXForTest(1), screen.getRowCenterYForTest(1), 0);
        ScreenDriver.click(screen.getStopButtonForTest());

        GuiTestSupport.assertEquals(
                List.of("NODE:" + IRON_BLOCK + ":3", "NODE:" + IRON_INGOT + ":27", "LEAF:" + RAW_IRON + ":27:selected"),
                screen.getRowSnapshotsForTest(),
                "对直接材料选择「继续展开」后应展示它的下一层材料");
        GuiTestSupport.assertEquals(2, screen.getRowDepthForTest(2), "继续展开产生的材料应位于第 2 层");

        ScreenDriver.click(screen.getStopButtonForTest());

        GuiTestSupport.assertEquals(
                List.of("NODE:" + IRON_BLOCK + ":3", "LEAF:" + IRON_INGOT + ":27:selected"),
                screen.getRowSnapshotsForTest(),
                "再次点击应把该节点折叠回默认状态");
    }

    /**
     * 验证点击勾选框区域可切换最终材料的勾选状态。
     */
    private static void shouldToggleLeafSelectionByCheckboxClick() {
        Harness harness = openScreen();
        int leafRow = 1;
        int y = harness.screen.getRowCenterYForTest(leafRow);
        int checkboxX = harness.screen.getCheckboxCenterXForTest(leafRow);

        harness.screen.mouseClicked(checkboxX, y, 0);
        GuiTestSupport.assertTrue(!harness.screen.getPreviewStateForTest().isLeafSelected(IRON_INGOT),
                "点击勾选框应取消勾选");
        GuiTestSupport.assertEquals("LEAF:" + IRON_INGOT + ":27:unselected",
                harness.screen.getRowSnapshotsForTest().get(leafRow), "行快照应反映未勾选状态");

        harness.screen.mouseClicked(checkboxX, y, 0);
        GuiTestSupport.assertTrue(harness.screen.getPreviewStateForTest().isLeafSelected(IRON_INGOT),
                "再次点击应恢复勾选");
    }

    /**
     * 验证选中中间产物后可用「切换配方」按钮轮换配方并重新解析。
     */
    private static void shouldCycleRecipeForSelectedIntermediateNode() {
        Harness harness = openScreen();
        MaterialListScreen screen = harness.screen;

        // 先对直接材料「继续展开」，使其成为可切换配方的中间节点
        screen.mouseClicked(screen.getRowTextXForTest(1), screen.getRowCenterYForTest(1), 0);
        ScreenDriver.click(screen.getStopButtonForTest());
        GuiTestSupport.assertEquals(1, screen.getSelectedRowForTest(), "点击行文本区域应选中该行");
        GuiTestSupport.assertTrue(screen.getRecipeButtonForTest().active, "铁锭有两条配方，切换配方按钮应可用");

        ScreenDriver.click(screen.getRecipeButtonForTest());

        GuiTestSupport.assertEquals("iron_ingot_from_nuggets",
                screen.getPreviewStateForTest().getRecipeOverrides().get(IRON_INGOT),
                "轮换后应记录为铁粒配方覆盖");
        GuiTestSupport.assertEquals(243, screen.getPlanForTest().totalFor(IRON_NUGGET),
                "铁锭 ×27 走铁粒配方后应推成铁粒 ×243");
        GuiTestSupport.assertEquals("LEAF:" + IRON_NUGGET + ":243:selected",
                screen.getRowSnapshotsForTest().get(2), "叶子行应变为铁粒");
    }

    /**
     * 验证模式按钮在两种生成模式之间切换。
     */
    private static void shouldToggleModeByButton() {
        Harness harness = openScreen();
        MaterialPreviewState state = harness.screen.getPreviewStateForTest();
        GuiTestSupport.assertTrue(state.getMode() == MaterialTaskMode.TARGET_WITH_MATERIALS,
                "默认模式应为「目标 + 材料」");

        ScreenDriver.click(harness.screen.getModeButtonForTest());
        GuiTestSupport.assertTrue(state.getMode() == MaterialTaskMode.TARGET_ONLY, "点击后应切换为仅目标任务");

        ScreenDriver.click(harness.screen.getModeButtonForTest());
        GuiTestSupport.assertTrue(state.getMode() == MaterialTaskMode.TARGET_WITH_MATERIALS, "再次点击应切回目标 + 材料");
    }

    /**
     * 验证修改数量输入会重新解析计划。
     */
    private static void shouldUpdatePlanWhenCountChanges() {
        Harness harness = openScreen();

        ScreenDriver.setText(harness.screen.getCountFieldForTest(), "6");

        GuiTestSupport.assertEquals(6, harness.screen.getPreviewStateForTest().getTargetCount(),
                "数量输入应更新到预览状态");
        GuiTestSupport.assertEquals(54, harness.screen.getPlanForTest().totalFor(IRON_INGOT),
                "铁块 ×6 应推成直接材料铁锭 ×54");
    }

    /**
     * 验证生成按钮把任务交给回调并关闭界面。
     */
    private static void shouldGenerateTasksAndCloseScreen() {
        Harness harness = openScreen();

        ScreenDriver.click(harness.screen.getGenerateButtonForTest());

        GuiTestSupport.assertEquals(2, harness.generated.size(), "应生成目标父任务 + 直接材料子任务");
        GuiTestSupport.assertTrue(harness.generated.get(0).getTrigger().getType() == TaskTrigger.Type.CRAFT_ITEM,
                "第一条为目标合成任务，应挂 CRAFT_ITEM 触发器");
        GuiTestSupport.assertEquals(IRON_INGOT, harness.generated.get(1).getTrigger().getTarget(),
                "第二条应为直接材料子任务");
        GuiTestSupport.assertEquals(harness.parent, harness.minecraft.getLastScreen(), "生成后应关闭界面返回父界面");
    }

    /**
     * 打开一个已完成初始化的预览界面。
     *
     * @return 测试夹具
     */
    private static Harness openScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Screen parent = ScreenDriver.createParentScreen("parent");
        List<Task> generated = new ArrayList<>();
        MaterialPreviewState state = new MaterialPreviewState(IRON_BLOCK, 3, MaterialTaskMode.TARGET_WITH_MATERIALS);
        MaterialListScreen screen = new MaterialListScreen(parent, ironIndex(), state, personalContext(), generated::addAll);
        ScreenDriver.init(minecraft, screen);
        return new Harness(minecraft, parent, screen, generated);
    }

    /**
     * 打开一个目标物使用「任意一种木板」的预览界面。
     *
     * @return 测试夹具
     */
    private static Harness openScreenForPlanks() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Screen parent = ScreenDriver.createParentScreen("parent");
        List<Task> generated = new ArrayList<>();
        MaterialPreviewState state = new MaterialPreviewState("test:target", 1, MaterialTaskMode.TARGET_WITH_MATERIALS);
        MaterialListScreen screen = new MaterialListScreen(parent, planksIndex(), state, personalContext(), generated::addAll);
        ScreenDriver.init(minecraft, screen);
        return new Harness(minecraft, parent, screen, generated);
    }

    /**
     * 构造目标物 ← 任意一种木板 的测试索引。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex planksIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("target", "test:target", 1, List.of(
                new MaterialIngredient(List.of(
                        "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks"), 1)
        ), MaterialRecipeKind.CRAFTING));
        return index;
    }

    /**
     * 构造 目标物 ← 橡木木板 ×8 + 部件 ×1，且 部件 ← 橡木木板 ×8 的测试索引。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex sharedPlanksIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("target", "test:target", 1, List.of(
                new MaterialIngredient(List.of(OAK_PLANKS), 8),
                new MaterialIngredient(List.of("test:part"), 1)
        ), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("part", "test:part", 1, List.of(
                new MaterialIngredient(List.of(OAK_PLANKS), 8)
        ), MaterialRecipeKind.CRAFTING));
        return index;
    }

    /**
     * 打开一个「目标物配方与其次级配方都需要橡木木板」的预览界面。
     *
     * @return 测试夹具
     */
    private static Harness openScreenForSharedPlanks() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Screen parent = ScreenDriver.createParentScreen("parent");
        List<Task> generated = new ArrayList<>();
        MaterialPreviewState state = new MaterialPreviewState("test:target", 1, MaterialTaskMode.TARGET_WITH_MATERIALS);
        MaterialListScreen screen = new MaterialListScreen(parent, sharedPlanksIndex(), state, personalContext(), generated::addAll);
        ScreenDriver.init(minecraft, screen);
        return new Harness(minecraft, parent, screen, generated);
    }

    /**
     * 构造铁块 → 铁锭 → 粗铁/铁粒 的测试索引。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex ironIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("iron_block", IRON_BLOCK, 1,
                List.of(new MaterialIngredient(List.of(IRON_INGOT), 9)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(RAW_IRON), 1)), MaterialRecipeKind.SMELTING));
        index.add(new MaterialRecipe("iron_ingot_from_nuggets", IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(IRON_NUGGET), 9)), MaterialRecipeKind.CRAFTING));
        return index;
    }

    /**
     * 构造个人作用域上下文。
     *
     * @return 上下文
     */
    private static MaterialTaskContext personalContext() {
        return new MaterialTaskContext("project-1", Task.Scope.PERSONAL, OWNER_ID.toString(), null, null,
                (itemId, count, collect) -> itemId + " x" + count);
    }

    /**
     * 测试夹具：假客户端、父界面、被测界面与生成结果收集器。
     *
     * @param minecraft 假客户端
     * @param parent    父界面
     * @param screen    被测界面
     * @param generated 生成结果收集器
     */
    private record Harness(FakeMinecraftClient minecraft, Screen parent, MaterialListScreen screen, List<Task> generated) {
    }
}
