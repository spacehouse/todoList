package com.todolist.gui;

import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.material.MaterialIngredient;
import com.todolist.material.MaterialPreviewState;
import com.todolist.material.MaterialRecipe;
import com.todolist.material.MaterialRecipeIndex;
import com.todolist.material.MaterialRecipeKind;
import com.todolist.material.MaterialTaskContext;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MaterialListScreen 离线自测：横向配方树布局与功能方块节点、节点展开 / 收起、
 * 点图标选材料 / 切配方、双向滚动、数量改动、持有量标注与生成回调。
 *
 * <p>语义层已由 {@code MaterialPreviewStateTestMain} 与
 * {@code MaterialPreviewLayoutTestMain} 覆盖，这里只验证界面组装与事件分发。
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
    /** 测试屏幕宽度与界面尺寸保持一致（见 {@link ScreenDriver#DEFAULT_WIDTH}）。 */
    private static final int TREE_CENTER_X = 100;
    /** 测试屏幕上位于树区域内、且不落在任何节点上的空白点 X。 */
    private static final int EMPTY_POINT_X = 218;
    /** 测试屏幕上位于树区域内、且不落在任何节点上的空白点 Y。 */
    private static final int EMPTY_POINT_Y = 154;

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
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldRenderHorizontalTreeWithStationNodes", MaterialListScreenTestMain::shouldRenderHorizontalTreeWithStationNodes);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldExpandAndCollapseByClickingNodeBody", MaterialListScreenTestMain::shouldExpandAndCollapseByClickingNodeBody);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldCycleRecipeByClickingStationIcon", MaterialListScreenTestMain::shouldCycleRecipeByClickingStationIcon);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldOpenCandidatePopupOnItemIconClick", MaterialListScreenTestMain::shouldOpenCandidatePopupOnItemIconClick);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldUpdatePlanWhenCountChanges", MaterialListScreenTestMain::shouldUpdatePlanWhenCountChanges);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldGenerateTasksAndCloseScreen", MaterialListScreenTestMain::shouldGenerateTasksAndCloseScreen);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldMarkCountSufficientWhenHeldItemsReachRequirement", MaterialListScreenTestMain::shouldMarkCountSufficientWhenHeldItemsReachRequirement);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldAllocateHeldItemsAcrossRepeatedMaterials", MaterialListScreenTestMain::shouldAllocateHeldItemsAcrossRepeatedMaterials);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldScrollTreeByWheelAndDrag", MaterialListScreenTestMain::shouldScrollTreeByWheelAndDrag);
        GuiTestSupport.runTestCase("MaterialListScreenTestMain.shouldKeepStationNodeWhenRecipeHitsCycle", MaterialListScreenTestMain::shouldKeepStationNodeWhenRecipeHitsCycle);
    }

    /**
     * 验证配方指回上层（如「铁锭 ← 铁块」而铁块又需要铁锭）时：
     * 该节点仍保留功能方块节点，玩家可以点它切换成别的配方绕开循环，而不会卡死。
     */
    private static void shouldKeepStationNodeWhenRecipeHitsCycle() {
        Harness harness = openScreenFor(IRON_BLOCK, cycleIndex(), 3);
        MaterialListScreen screen = harness.screen;

        // 默认选中的配方把材料指回上层，展开后该节点只能退化为「循环终止」
        screen.clickItemBodyForTest(IRON_INGOT);
        GuiTestSupport.assertEquals(
                List.of("0|" + IRON_BLOCK + ":3:0", "1|" + IRON_INGOT + ":27:0"),
                screen.getItemSnapshotsForTest(),
                "指回上层的配方不能再展开，节点仍作为该分支的最终材料");
        GuiTestSupport.assertEquals(
                List.of(IRON_BLOCK + "|minecraft:crafting_table", IRON_INGOT + "|minecraft:crafting_table"),
                screen.getStationSnapshotsForTest(),
                "循环终止的节点也应保留功能方块节点，否则无法再切换配方");

        GuiTestSupport.assertTrue(screen.clickStationIconForTest(IRON_INGOT), "点击功能方块节点应被界面处理");
        GuiTestSupport.assertEquals("iron_ingot_from_smelting_raw_iron",
                screen.getPreviewStateForTest().getRecipeOverrides().get(IRON_INGOT),
                "应能切换到不产生循环的熔炼配方");
        GuiTestSupport.assertEquals(27, screen.getPlanForTest().totalFor(RAW_IRON),
                "切换后应能正常推出粗铁 ×27");
        GuiTestSupport.assertEquals(
                List.of(IRON_BLOCK + "|minecraft:crafting_table", IRON_INGOT + "|minecraft:furnace"),
                screen.getStationSnapshotsForTest(),
                "功能方块节点应随配方切换为熔炉");
    }

    /**
     * 验证默认渲染为横向配方树：目标物与它的直接材料各占一列，
     * 每个有配方的节点后面都跟着一个功能方块节点（工作台 / 熔炉）。
     */
    private static void shouldRenderHorizontalTreeWithStationNodes() {
        Harness harness = openScreen();

        GuiTestSupport.assertEquals(
                List.of("0|" + IRON_BLOCK + ":3:0", "1|" + IRON_INGOT + ":27:0"),
                harness.screen.getItemSnapshotsForTest(),
                "默认应渲染目标物与它的直接材料，层级依次为 0 / 1");
        GuiTestSupport.assertEquals(
                List.of(IRON_BLOCK + "|minecraft:crafting_table", IRON_INGOT + "|minecraft:furnace"),
                harness.screen.getStationSnapshotsForTest(),
                "铁块由工作台合成、铁锭默认走熔炉熔炼，两处都应插入对应的功能方块节点");
    }

    /**
     * 验证点击节点其余区域可展开 / 收起下级配方，且生成结果随之变化。
     */
    private static void shouldExpandAndCollapseByClickingNodeBody() {
        Harness harness = openScreen();
        MaterialListScreen screen = harness.screen;

        GuiTestSupport.assertTrue(screen.clickItemBodyForTest(IRON_INGOT), "点击节点应被界面处理");
        GuiTestSupport.assertEquals(
                List.of("0|" + IRON_BLOCK + ":3:0", "1|" + IRON_INGOT + ":27:0", "2|" + RAW_IRON + ":27:0"),
                screen.getItemSnapshotsForTest(),
                "点击折叠节点应展开出它的下级配方");

        GuiTestSupport.assertTrue(screen.clickItemBodyForTest(IRON_INGOT), "再次点击节点应被界面处理");
        GuiTestSupport.assertEquals(
                List.of("0|" + IRON_BLOCK + ":3:0", "1|" + IRON_INGOT + ":27:0"),
                screen.getItemSnapshotsForTest(),
                "再次点击应收起下级配方");
    }

    /**
     * 验证点击功能方块节点可切换配方：功能方块图标与下级材料随之变化。
     */
    private static void shouldCycleRecipeByClickingStationIcon() {
        Harness harness = openScreen();
        MaterialListScreen screen = harness.screen;

        // 先展开直接材料，使它成为「用哪条配方做」的中间节点
        screen.clickItemBodyForTest(IRON_INGOT);
        GuiTestSupport.assertTrue(screen.clickStationIconForTest(IRON_INGOT), "点击功能方块节点应被界面处理");

        GuiTestSupport.assertEquals("iron_ingot_from_nuggets",
                screen.getPreviewStateForTest().getRecipeOverrides().get(IRON_INGOT),
                "铁锭有两条配方，点击功能方块节点应切到下一条（铁粒）");
        GuiTestSupport.assertEquals(243, screen.getPlanForTest().totalFor(IRON_NUGGET),
                "铁锭 ×27 走铁粒配方后应推成铁粒 ×243");
        GuiTestSupport.assertEquals(
                List.of("0|" + IRON_BLOCK + ":3:0", "1|" + IRON_INGOT + ":27:0", "2|" + IRON_NUGGET + ":243:0"),
                screen.getItemSnapshotsForTest(),
                "下级材料应随配方切换为铁粒");
        GuiTestSupport.assertEquals(
                List.of(IRON_BLOCK + "|minecraft:crafting_table", IRON_INGOT + "|minecraft:crafting_table"),
                screen.getStationSnapshotsForTest(),
                "功能方块节点应随配方切换为工作台");
    }

    /**
     * 验证点击物品图标会弹出候选材料列表，选中后重新解析为所选材料。
     */
    private static void shouldOpenCandidatePopupOnItemIconClick() {
        String oakPlanks = "minecraft:oak_planks";
        String birchPlanks = "minecraft:birch_planks";
        Harness harness = openScreenFor("test:target", planksIndex(), 1);
        MaterialListScreen screen = harness.screen;

        GuiTestSupport.assertFalse(screen.isCandidatePopupOpenForTest(), "初始不应有候选材料弹出列表");
        GuiTestSupport.assertTrue(screen.clickItemIconForTest(oakPlanks), "点击物品图标应被界面处理");
        GuiTestSupport.assertTrue(screen.isCandidatePopupOpenForTest(), "该材料有多个候选时应弹出可选列表");
        GuiTestSupport.assertEquals(3, screen.getCandidatePopupEntriesForTest().size(), "弹出列表应列出全部候选材料");

        GuiTestSupport.assertTrue(screen.clickCandidateEntryForTest(birchPlanks), "点击条目应被界面处理");
        GuiTestSupport.assertFalse(screen.isCandidatePopupOpenForTest(), "选定后应关闭弹出列表");
        GuiTestSupport.assertEquals(birchPlanks,
                screen.getPreviewStateForTest().getCandidateOverrides().get(oakPlanks),
                "选择结果应记录为候选覆盖");
        GuiTestSupport.assertEquals(1, screen.getPlanForTest().totalFor(birchPlanks),
                "重新解析后应使用选定的木板");
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

        GuiTestSupport.assertEquals(2, harness.generated.size(), "应生成目标任务 + 直接材料子任务");
        GuiTestSupport.assertTrue(harness.generated.get(0).getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "第一条为目标物任务，材料反推一律用「收集」语义");
        GuiTestSupport.assertEquals(IRON_INGOT, harness.generated.get(1).getTrigger().getTarget(),
                "第二条应为直接材料子任务");
        GuiTestSupport.assertEquals(harness.parent, harness.minecraft.getLastScreen(), "生成后应关闭界面返回父界面");
    }

    /**
     * 验证背包持有量达到配方需求时数量文本标绿并给出「已够」文字提示，未达到时提示「缺 N」。
     */
    private static void shouldMarkCountSufficientWhenHeldItemsReachRequirement() {
        Harness harness = openScreen();
        MaterialListScreen screen = harness.screen;

        screen.setHeldItemCountsForTest(Map.of(IRON_BLOCK, 2, IRON_INGOT, 30));

        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getItemCountColorForTest(IRON_INGOT), "铁锭已有 30 个（需求 27）应标绿");
        GuiTestSupport.assertEquals(heldEnoughText(), screen.getItemHeldSuffixForTest(IRON_INGOT),
                "已够用时数量后应给出「已够」文字提示，而不是只靠颜色");
        GuiTestSupport.assertTrue(screen.getItemCountColorForTest(IRON_BLOCK) != MaterialListScreen.sufficientColorForTest(),
                "铁块已有 2 个（需求 3）不应标绿");
        GuiTestSupport.assertEquals(heldMissingText(1), screen.getItemHeldSuffixForTest(IRON_BLOCK),
                "数量不足时应提示还缺多少");
        GuiTestSupport.assertTrue(screen.getTargetCountColorForTest() != MaterialListScreen.sufficientColorForTest(),
                "目标物品持有量不足时目标行也不应标绿");
        GuiTestSupport.assertEquals(heldMissingText(1), screen.getTargetHeldSuffixForTest(),
                "目标物品数量不足时同样应提示还缺多少");

        screen.setHeldItemCountsForTest(Map.of(IRON_BLOCK, 3, IRON_INGOT, 27));

        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getItemCountColorForTest(IRON_BLOCK), "持有量刚好达到需求也应标绿");
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
        Harness harness = openScreenFor("test:target", sharedPlanksIndex(), 1);
        MaterialListScreen screen = harness.screen;

        screen.clickItemBodyForTest("test:part");

        screen.setHeldItemCountsForTest(Map.of(OAK_PLANKS, 16));

        GuiTestSupport.assertEquals(
                List.of("0|test:target:1:0",
                        "1|" + OAK_PLANKS + ":8:8",
                        "1|test:part:1:0",
                        "2|" + OAK_PLANKS + ":8:8"),
                screen.getItemSnapshotsForTest(),
                "共需 16 个木板且背包有 16 个时两个层级应各分到 8 个");
        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getItemCountColorAtForTest(1), "第 1 层木板应标绿");
        GuiTestSupport.assertEquals(MaterialListScreen.sufficientColorForTest(),
                screen.getItemCountColorAtForTest(3), "第 2 层木板也应标绿");

        screen.setHeldItemCountsForTest(Map.of(OAK_PLANKS, 12));

        GuiTestSupport.assertEquals("2|" + OAK_PLANKS + ":8:4", screen.getItemSnapshotsForTest().get(3),
                "第 1 层优先占掉 8 个，第 2 层只能拿到剩余 4 个");
        GuiTestSupport.assertTrue(screen.getItemCountColorAtForTest(3) != MaterialListScreen.sufficientColorForTest(),
                "总量只有 12 个木板时第 2 层不应标绿");

        screen.setHeldItemCountsForTest(Map.of(OAK_PLANKS, 8));

        GuiTestSupport.assertEquals("2|" + OAK_PLANKS + ":8:0", screen.getItemSnapshotsForTest().get(3),
                "第 1 层占满后第 2 层不应重复使用同一批数量");
    }

    /**
     * 验证双向滚动：滚轮纵向滚动并收敛到底，按住 Shift 的滚轮与空白处拖拽横向滚动。
     */
    private static void shouldScrollTreeByWheelAndDrag() {
        Harness fanHarness = openScreenFor("test:target", fanIndex(), 1);
        MaterialListScreen fanScreen = fanHarness.screen;

        GuiTestSupport.assertTrue(fanScreen.mouseScrolled(TREE_CENTER_X, 120, -1),
                "树区域内的滚轮应被界面消费");
        GuiTestSupport.assertTrue(fanScreen.getScrollYForTest() > 0, "滚轮向下应产生纵向偏移");
        for (int i = 0; i < 40; i++) {
            fanScreen.mouseScrolled(TREE_CENTER_X, 120, -1);
        }
        int stabilized = fanScreen.getScrollYForTest();
        fanScreen.mouseScrolled(TREE_CENTER_X, 120, -1);
        GuiTestSupport.assertEquals(stabilized, fanScreen.getScrollYForTest(),
                "滚动到底后纵向偏移不应继续增加");

        Harness deepHarness = openScreenFor("test:target", deepChainIndex(), 1);
        MaterialListScreen deepScreen = deepHarness.screen;
        deepScreen.clickItemBodyForTest("test:a");
        deepScreen.clickItemBodyForTest("test:b");
        deepScreen.clickItemBodyForTest("test:c");

        deepScreen.keyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);
        GuiTestSupport.assertTrue(deepScreen.mouseScrolled(EMPTY_POINT_X, EMPTY_POINT_Y, -1),
                "按住 Shift 时滚轮应被界面消费");
        int afterShiftWheel = deepScreen.getScrollXForTest();
        GuiTestSupport.assertTrue(afterShiftWheel > 0, "按住 Shift 时滚轮应改为横向滚动");
        deepScreen.keyReleased(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0);

        deepScreen.mouseClicked(EMPTY_POINT_X, EMPTY_POINT_Y, 0);
        deepScreen.mouseDragged(EMPTY_POINT_X - 40, EMPTY_POINT_Y, 0, -40, 0);
        deepScreen.mouseReleased(EMPTY_POINT_X - 40, EMPTY_POINT_Y, 0);
        GuiTestSupport.assertTrue(deepScreen.getScrollXForTest() > afterShiftWheel,
                "在空白处向左拖拽应继续增加横向偏移");
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
     * 打开铁块 ×3 的默认预览界面。
     *
     * @return 测试夹具
     */
    private static Harness openScreen() {
        return openScreenFor(IRON_BLOCK, ironIndex(), 3);
    }

    /**
     * 打开一个已完成初始化的预览界面。
     *
     * @param targetItemId 目标物品资源 ID
     * @param index        配方反查索引
     * @param targetCount  目标数量
     * @return 测试夹具
     */
    private static Harness openScreenFor(String targetItemId, MaterialRecipeIndex index, int targetCount) {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Screen parent = ScreenDriver.createParentScreen("parent");
        List<Task> generated = new ArrayList<>();
        MaterialPreviewState state = new MaterialPreviewState(targetItemId, targetCount);
        MaterialListScreen screen = new MaterialListScreen(parent, index, state, personalContext(), generated::addAll);
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
     * 构造 目标物 ← 五个直接材料 的测试索引（行数多，用于测试纵向滚动）。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex fanIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("target", "test:target", 1, List.of(
                new MaterialIngredient(List.of("test:m1"), 1),
                new MaterialIngredient(List.of("test:m2"), 1),
                new MaterialIngredient(List.of("test:m3"), 1),
                new MaterialIngredient(List.of("test:m4"), 1),
                new MaterialIngredient(List.of("test:m5"), 1)
        ), MaterialRecipeKind.CRAFTING));
        return index;
    }

    /**
     * 构造一条足够深的合成链：目标 ← 甲 ← 乙 ← 丙 ← 丁（丁无配方），用于测试横向滚动。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex deepChainIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(chainRecipe("target", "test:target", "test:a"));
        index.add(chainRecipe("a", "test:a", "test:b"));
        index.add(chainRecipe("b", "test:b", "test:c"));
        index.add(chainRecipe("c", "test:c", "test:d"));
        return index;
    }

    /**
     * 构造一条单输入单产出的合成配方。
     *
     * @param id     配方 ID
     * @param output 产物物品资源 ID
     * @param input  输入物品资源 ID
     * @return 配方
     */
    private static MaterialRecipe chainRecipe(String id, String output, String input) {
        return new MaterialRecipe(id, output, 1,
                List.of(new MaterialIngredient(List.of(input), 1)), MaterialRecipeKind.CRAFTING);
    }

    /**
     * 构造一条含循环的测试索引：铁块 ← 铁锭 ×9，铁锭 ← 铁块 ×1（指回上层）+ 熔炼粗铁。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex cycleIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("iron_block", IRON_BLOCK, 1,
                List.of(new MaterialIngredient(List.of(IRON_INGOT), 9)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("iron_ingot_from_block", IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(IRON_BLOCK), 1)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(RAW_IRON), 1)), MaterialRecipeKind.SMELTING));
        return index;
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
                (itemId, count, kind) -> itemId + " x" + count);
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
