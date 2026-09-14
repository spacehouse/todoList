package com.todolist.material;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;

import java.util.List;
import java.util.Set;

/**
 * 材料反推预览状态的离线自测：状态派生、配方轮换、勾选过滤、手动终止与任务生成。
 *
 * <p>界面只负责渲染与事件分发，语义都落在 {@link MaterialPreviewState}，
 * 因此这一层可以完整离线覆盖。
 */
public final class MaterialPreviewStateTestMain {

    /** 目标物品：铁锭。 */
    private static final String IRON_INGOT = "minecraft:iron_ingot";
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
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldFilterUnselectedLeaves", MaterialPreviewStateTestMain::shouldFilterUnselectedLeaves);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldRespectStopAtItem", MaterialPreviewStateTestMain::shouldRespectStopAtItem);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldToggleModeAndClampCount", MaterialPreviewStateTestMain::shouldToggleModeAndClampCount);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldGenerateTasksWithSelection", MaterialPreviewStateTestMain::shouldGenerateTasksWithSelection);
        GuiTestSupport.runTestCase("MaterialPreviewStateTestMain.shouldApplyCandidateChoiceAndPreferredItems", MaterialPreviewStateTestMain::shouldApplyCandidateChoiceAndPreferredItems);
    }

    /**
     * 验证预览状态把「候选材料选择」与「背包已有材料」透传到反推结果。
     */
    private static void shouldApplyCandidateChoiceAndPreferredItems() {
        String oakPlanks = "minecraft:oak_planks";
        String sprucePlanks = "minecraft:spruce_planks";
        String birchPlanks = "minecraft:birch_planks";
        MaterialPreviewState state = new MaterialPreviewState("test:target", 1, MaterialTaskMode.TARGET_WITH_MATERIALS);

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
     * 默认状态应解析出「铁锭 → 粗铁」，并全部勾选。
     */
    private static void shouldResolveWithDefaultSelection() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 64, MaterialTaskMode.TARGET_WITH_MATERIALS);
        MaterialPlan plan = state.resolve(ironIndex());

        GuiTestSupport.assertEquals(64, plan.totalFor(RAW_IRON), "默认应选熔炼配方，推成粗铁 ×64");
        GuiTestSupport.assertNull(state.allowedLeafItems(plan), "未取消勾选时不应做过滤（返回 null 表示全选）");
        GuiTestSupport.assertTrue(state.isLeafSelected(RAW_IRON), "新增的最终材料默认勾选");
    }

    /**
     * 轮换配方后应重新解析，并保留覆盖值。
     */
    private static void shouldCycleRecipeAndReResolve() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 64, MaterialTaskMode.TARGET_WITH_MATERIALS);
        MaterialRecipeIndex index = ironIndex();

        String next = state.cycleRecipe(IRON_INGOT, index.findByOutput(IRON_INGOT));

        GuiTestSupport.assertEquals(NUGGET_ID, next, "默认预选熔炼，轮换一格应切到铁粒配方");
        GuiTestSupport.assertEquals(NUGGET_ID, state.getRecipeOverrides().get(IRON_INGOT), "轮换结果应记录为配方覆盖");
        MaterialPlan plan = state.resolve(index);
        GuiTestSupport.assertEquals(576, plan.totalFor(IRON_NUGGET), "切换为铁粒配方后应推成铁粒 ×576");
        GuiTestSupport.assertEquals(0, plan.totalFor(RAW_IRON), "切换后不应再走熔炼路线");

        state.clearRecipeOverrides();
        GuiTestSupport.assertEquals(64, state.resolve(index).totalFor(RAW_IRON), "清空覆盖后应回到默认选择");
    }

    /**
     * 取消勾选的最终材料不应进入生成结果。
     */
    private static void shouldFilterUnselectedLeaves() {
        MaterialPreviewState state = new MaterialPreviewState("test:target", 1, MaterialTaskMode.TARGET_WITH_MATERIALS);
        MaterialRecipeIndex index = twoLeafIndex();

        MaterialPlan plan = state.resolve(index);
        GuiTestSupport.assertEquals(2, plan.leafTotals().size(), "初始应有两个最终材料");
        GuiTestSupport.assertNull(state.allowedLeafItems(plan), "全部勾选时不应做过滤（返回 null 表示全选）");

        state.setLeafSelected("minecraft:oak_log", false);
        GuiTestSupport.assertTrue(!state.isLeafSelected("minecraft:oak_log"), "取消勾选后应标记为未选中");
        GuiTestSupport.assertEquals(1, state.allowedLeafItems(plan).size(), "取消勾选后允许集合应只剩一条材料");

        List<Task> tasks = state.generate(index, personalContext());
        GuiTestSupport.assertEquals(2, tasks.size(), "模式 B 应生成父任务 + 1 条已勾选材料子任务");
        GuiTestSupport.assertEquals("minecraft:coal", tasks.get(1).getTrigger().getTarget(), "未勾选的原木不应生成任务");
    }

    /**
     * 玩家手动终止后，该物品本身即最终材料。
     */
    private static void shouldRespectStopAtItem() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 64, MaterialTaskMode.TARGET_WITH_MATERIALS);
        state.setStopAt(IRON_INGOT, true);

        MaterialPlan plan = state.resolve(ironIndex());

        GuiTestSupport.assertEquals(64, plan.totalFor(IRON_INGOT), "手动终止后铁锭本身应成为最终材料");
        GuiTestSupport.assertEquals(0, plan.totalFor(RAW_IRON), "手动终止后不应再推粗铁");

        state.setStopAt(IRON_INGOT, false);
        GuiTestSupport.assertEquals(64, state.resolve(ironIndex()).totalFor(RAW_IRON), "取消终止后应恢复展开");
    }

    /**
     * 模式切换与数量下限收敛。
     */
    private static void shouldToggleModeAndClampCount() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 0, null);

        GuiTestSupport.assertEquals(1, state.getTargetCount(), "数量下限应为 1");
        GuiTestSupport.assertTrue(state.getMode() == MaterialTaskMode.TARGET_ONLY, "模式为 null 时应按仅目标任务处理");

        state.toggleMode();
        GuiTestSupport.assertTrue(state.getMode() == MaterialTaskMode.TARGET_WITH_MATERIALS, "切换后应变为目标 + 材料");
        state.toggleMode();
        GuiTestSupport.assertTrue(state.getMode() == MaterialTaskMode.TARGET_ONLY, "再次切换应回到仅目标任务");

        state.setTargetCount(-5);
        GuiTestSupport.assertEquals(1, state.getTargetCount(), "非法数量应收敛到 1");
    }

    /**
     * 生成任务时应应用数量与勾选结果。
     */
    private static void shouldGenerateTasksWithSelection() {
        MaterialPreviewState state = new MaterialPreviewState(IRON_INGOT, 32, MaterialTaskMode.TARGET_WITH_MATERIALS);

        List<Task> tasks = state.generate(ironIndex(), personalContext());

        GuiTestSupport.assertEquals(2, tasks.size(), "应生成目标父任务 + 粗铁子任务");
        GuiTestSupport.assertTrue(tasks.get(0).getTrigger().getType() == TaskTrigger.Type.CRAFT_ITEM,
                "目标父任务为合成任务，应挂 CRAFT_ITEM 触发器");
        GuiTestSupport.assertEquals(RAW_IRON, tasks.get(1).getTrigger().getTarget(), "子任务应为粗铁");
        GuiTestSupport.assertEquals(32, tasks.get(1).getTrigger().getTargetCount(), "粗铁数量应为 32（跟随最新输入）");

        state.setMode(MaterialTaskMode.TARGET_ONLY);
        GuiTestSupport.assertEquals(1, state.generate(ironIndex(), personalContext()).size(), "模式 A 应只生成目标任务");
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
     * 构造含两个最终材料的索引：目标物 ← 原木 ×1 + 煤炭 ×1（两者均无配方）。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex twoLeafIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("target", "test:target", 1, List.of(
                new MaterialIngredient(List.of("minecraft:oak_log"), 1),
                new MaterialIngredient(List.of("minecraft:coal"), 1)
        ), MaterialRecipeKind.CRAFTING));
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
                (itemId, count, collect) -> itemId + " x" + count);
    }
}
