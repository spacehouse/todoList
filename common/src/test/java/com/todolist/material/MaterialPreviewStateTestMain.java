package com.todolist.material;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;

import java.util.List;
import java.util.Set;

/**
 * 材料反推预览状态的离线自测：状态派生、配方轮换、展开与收起、手动终止与任务生成。
 *
 * <p>界面只负责渲染与事件分发，语义都落在 {@link MaterialPreviewState}，
 * 因此这一层可以完整离线覆盖。
 */
public final class MaterialPreviewStateTestMain {

    /** 目标物品：铁锭。 */
    private static final String IRON_INGOT = "minecraft:iron_ingot";
    /** 合成目标：铁块（直接材料为铁锭）。 */
    private static final String IRON_BLOCK = "minecraft:iron_block";
    /** 最终材料：粗铁。 */
    private static final String RAW_IRON = "minecraft:raw_iron";
    /** 备选配方产物：铁粒。 */
    private static final String IRON_NUGGET = "minecraft:iron_nugget";
    /** 熔炼配方 ID。 */
    private static final String SMELTING_ID = "iron_ingot_from_smelting_raw_iron";
    /** 铁粒配方 ID。 */
    private static final String NUGGET_ID = "iron_ingot_from_nuggets";

    /**
     * 工具类不需要实例化。
     */
    private MaterialPreviewStateTestMain() {
    }

    /**
     * 执行预览状态测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldResolveWithDefaultSelection", MaterialPreviewStateTestMain::shouldResolveWithDefaultSelection);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldCycleRecipeAndReResolve", MaterialPreviewStateTestMain::shouldCycleRecipeAndReResolve);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldToggleExpandAndCollapse", MaterialPreviewStateTestMain::shouldToggleExpandAndCollapse);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldRespectStopAtItem", MaterialPreviewStateTestMain::shouldRespectStopAtItem);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldClampTargetCount", MaterialPreviewStateTestMain::shouldClampTargetCount);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldGenerateTasksByDisplayedTree", MaterialPreviewStateTestMain::shouldGenerateTasksByDisplayedTree);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldApplyCandidateChoiceAndPreferredItems", MaterialPreviewStateTestMain::shouldApplyCandidateChoiceAndPreferredItems);
    }

    /**
     * 验证预览状态把「候选材料选择」与「背包已有材料」透传到反推结果。
     */
    private static void shouldApplyCandidateChoiceAndPreferredItems() {
        String oakPlanks = "minecraft:oak_planks";
        String sprucePlanks = "minecraft:spruce_planks";
        String birchPlanks = "minecraft:birch_planks";
        MaterialPreviewState state = new MaterialPreviewState("test:target", 1);

        state.setPreferredItems(Set.of(birchPlanks));
        MaterialPlan preferred = state.resolve(planksIndex());
        GuiTestSupport.assertEquals(1, preferred.totalFor(birchPlanks), "默认应优先选择背包里已有的候选材料");
        GuiTestSupport.assertEquals(0, preferred.totalFor(oakPlanks), "未选中的候选不应出现在清单中");

        state.setPreferredItems(Set.of());
        state.chooseCandidate(oakPlanks, sprucePlanks);
        GuiTestSupport.assertEquals(sprucePlanks, state.getCandidateOverrides().get(oakPlanks),
                "选择结果应记录为候选覆盖");
        GuiTestSupport.assertEquals(1, state.resolve(planksIndex()).totalFor(sprucePlanks),
                "显式指定的候选材料应优先于列表首项");

        state.chooseCandidate(oakPlanks, "");
        GuiTestSupport.assertTrue(state.getCandidateOverrides().isEmpty(), "清空选择后应回到默认候选");
    }

    /**
     * 默认状态应解析出「铁锭 → 粗铁」。
     */
    private static void shouldResolveWithDefaultSelection() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 64);
        MaterialPlan plan = state.resolve(ironIndex());

        GuiTestSupport.assertEquals(64, plan.totalFor(RAW_IRON), "默认应选熔炼配方，推成粗铁 ×64");
    }

    /**
     * 轮换配方后应重新解析，并保留覆盖值。
     */
    private static void shouldCycleRecipeAndReResolve() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 64);
        MaterialRecipeIndex index = ironIndex();

        String next = state.cycleRecipe(IRON_INGOT, index.findByOutput(IRON_INGOT));

        GuiTestSupport.assertEquals(NUGGET_ID, next, "默认预选熔炼，轮换一格应切到铁粒配方");
        GuiTestSupport.assertEquals(NUGGET_ID, state.getRecipeOverrides().get(IRON_INGOT), "轮换结果应记录为配方覆盖");
        MaterialPlan plan = state.resolve(index);
        GuiTestSupport.assertEquals(576, plan.totalFor(IRON_NUGGET), "切换为铁粒配方后应推成铁粒 ×576");
        GuiTestSupport.assertEquals(0, plan.totalFor(RAW_IRON), "切换后不应再走熔炼路线");

        state.overrideRecipe(IRON_INGOT, "");
        GuiTestSupport.assertEquals(64, state.resolve(index).totalFor(RAW_IRON), "清空覆盖后应回到默认选择");
    }

    /**
     * 展开 / 收起：默认折叠的直接材料可以展开出下级配方，反过来又能收回去；
     * 没有配方的最终材料不可展开。
     */
    private static void shouldToggleExpandAndCollapse() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_BLOCK, 3);

        MaterialPlan folded = state.resolve(ironBlockIndex());
        MaterialNode middle = folded.root().children().get(0);
        GuiTestSupport.assertEquals(IRON_INGOT, middle.itemId(), "铁块的直接材料应为铁锭");
        GuiTestSupport.assertTrue(middle.isLeaf() && middle.stopReason() == MaterialStopReason.FOLDED,
                "默认只展开一层，铁锭应处于折叠状态");
        GuiTestSupport.assertTrue(state.isExpandToggleAvailable(middle), "折叠节点应可展开");

        GuiTestSupport.assertTrue(state.toggleExpand(middle), "展开应产生状态变化");
        MaterialNode expandedMiddle = state.resolve(ironBlockIndex()).root().children().get(0);
        GuiTestSupport.assertFalse(expandedMiddle.isLeaf(), "展开后铁锭应带下级配方");
        GuiTestSupport.assertEquals(27, state.resolve(ironBlockIndex()).totalFor(RAW_IRON),
                "铁块 ×3 需要铁锭 ×27，展开两层后应推出粗铁 ×27");

        GuiTestSupport.assertTrue(state.toggleExpand(expandedMiddle), "收起应产生状态变化");
        MaterialNode collapsedMiddle = state.resolve(ironBlockIndex()).root().children().get(0);
        GuiTestSupport.assertTrue(collapsedMiddle.isLeaf(), "收起后铁锭应回到最终材料");
        GuiTestSupport.assertEquals(0, state.resolve(ironBlockIndex()).totalFor(RAW_IRON),
                "收起后不应再推粗铁");

        MaterialPreviewState probe = new MaterialPreviewState(RAW_IRON, 5);
        GuiTestSupport.assertFalse(probe.isExpandToggleAvailable(probe.resolve(ironBlockIndex()).root()),
                "无配方的最终材料不可展开");
    }

    /**
     * 玩家手动终止后，该物品本身即最终材料。
     */
    private static void shouldRespectStopAtItem() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 64);
        state.setStopAt(IRON_INGOT, true);

        MaterialPlan plan = state.resolve(ironIndex());

        GuiTestSupport.assertEquals(64, plan.totalFor(IRON_INGOT), "手动终止后铁锭本身应成为最终材料");
        GuiTestSupport.assertEquals(0, plan.totalFor(RAW_IRON), "手动终止后不应再推粗铁");

        state.setStopAt(IRON_INGOT, false);
        GuiTestSupport.assertEquals(64, state.resolve(ironIndex()).totalFor(RAW_IRON), "取消终止后应恢复展开");
    }

    /**
     * 数量下限收敛。
     */
    private static void shouldClampTargetCount() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 0);

        GuiTestSupport.assertEquals(1, state.getTargetCount(), "数量下限应为 1");
        state.setTargetCount(-5);
        GuiTestSupport.assertEquals(1, state.getTargetCount(), "非法数量应收敛到 1");
        state.setTargetCount(12);
        GuiTestSupport.assertEquals(12, state.getTargetCount(), "合法数量应生效");
    }

    /**
     * 生成任务只按当前预览树：默认展开一层生成「目标 + 直接材料」，
     * 把目标节点收起后只生成目标自身这一条任务。
     */
    private static void shouldGenerateTasksByDisplayedTree() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 32);

        List<Task> tasks = state.generate(ironIndex(), personalContext());

        GuiTestSupport.assertEquals(2, tasks.size(), "应生成目标任务 + 粗铁子任务");
        GuiTestSupport.assertTrue(tasks.get(0).getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "材料反推生成的任务一律用「收集」语义（配方只作为标题提示）");
        GuiTestSupport.assertEquals(RAW_IRON, tasks.get(1).getTrigger().getTarget(), "子任务应为粗铁");
        GuiTestSupport.assertEquals(32, tasks.get(1).getTrigger().getTargetCount(), "粗铁数量应为 32（跟随最新输入）");

        state.setStopAt(IRON_INGOT, true);
        List<Task> onlyTarget = state.generate(ironIndex(), personalContext());
        GuiTestSupport.assertEquals(1, onlyTarget.size(), "收起目标节点后只应生成目标自身这一条任务");
        GuiTestSupport.assertEquals(IRON_INGOT, onlyTarget.get(0).getTrigger().getTarget(), "唯一任务应追踪目标物品");
        GuiTestSupport.assertEquals(32, onlyTarget.get(0).getTrigger().getTargetCount(), "唯一任务数量应为目标数量");
    }

    /**
     * 构造铁锭的双配方索引。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex ironIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe(SMELTING_ID, IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(RAW_IRON), 1)), MaterialRecipeKind.SMELTING));
        index.add(new MaterialRecipe(NUGGET_ID, IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(IRON_NUGGET), 9)), MaterialRecipeKind.CRAFTING));
        return index;
    }

    /**
     * 构造「铁块 ← 铁锭 ×9，铁锭 ← 熔炼粗铁」的索引：
     * 默认只展开一层，铁锭处于折叠状态，便于测试展开 / 收起。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex ironBlockIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("iron_block", IRON_BLOCK, 1,
                List.of(new MaterialIngredient(List.of(IRON_INGOT), 9)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe(SMELTING_ID, IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(RAW_IRON), 1)), MaterialRecipeKind.SMELTING));
        return index;
    }

    /**
     * 构造含多候选材料的索引：目标物 ← 任意一种木板 ×1。
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
     * 构造个人作用域上下文。
     *
     * @return 上下文
     */
    private static MaterialTaskContext personalContext() {
        return new MaterialTaskContext("project-1", Task.Scope.PERSONAL, "creator-uuid", null, null,
                (itemId, count, kind) -> itemId + " x" + count);
    }
}
