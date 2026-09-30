package com.todolist.material;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 材料任务生成的离线自测：两种生成模式、按展开层级生成依赖任务、同种最终材料合并、上下文透传与标题回退。
 *
 * <p>计划数据由 {@link MaterialResolver} + 构造的索引生成，同时覆盖 Phase C 与 Phase D 的衔接。
 */
public final class MaterialTaskGeneratorTestMain {

    /** 目标物：铁块。 */
    private static final String IRON_BLOCK = "minecraft:iron_block";
    /** 中间产物：铁锭。 */
    private static final String IRON_INGOT = "minecraft:iron_ingot";
    /** 最终材料：粗铁。 */
    private static final String RAW_IRON = "minecraft:raw_iron";
    /** 跨层级重复出现的最终材料：橡木原木。 */
    private static final String OAK_LOG = "minecraft:oak_log";
    /** 跨分支重复出现的中间产物：共享件。 */
    private static final String SHARED_PART = "test:shared";

    /**
     * 工具类不需要实例化。
     */
    private MaterialTaskGeneratorTestMain() {
    }

    /**
     * 执行材料任务生成测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldGenerateTargetOnlyTask", MaterialTaskGeneratorTestMain::shouldGenerateTargetOnlyTask);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldGenerateParentWithMaterialSubtasks", MaterialTaskGeneratorTestMain::shouldGenerateParentWithMaterialSubtasks);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldGenerateDependencyChainMatchingExpandedLevels", MaterialTaskGeneratorTestMain::shouldGenerateDependencyChainMatchingExpandedLevels);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldCopyContextIntoEveryTask", MaterialTaskGeneratorTestMain::shouldCopyContextIntoEveryTask);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldSkipTargetItemInSubtasks", MaterialTaskGeneratorTestMain::shouldSkipTargetItemInSubtasks);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldFallbackTitleWhenProviderMissing", MaterialTaskGeneratorTestMain::shouldFallbackTitleWhenProviderMissing);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldReturnEmptyForInvalidInput", MaterialTaskGeneratorTestMain::shouldReturnEmptyForInvalidInput);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldMergeRepeatedFinalMaterialIntoOneTask", MaterialTaskGeneratorTestMain::shouldMergeRepeatedFinalMaterialIntoOneTask);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldMergeRepeatedIntermediateIntoOneTask", MaterialTaskGeneratorTestMain::shouldMergeRepeatedIntermediateIntoOneTask);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldPassRecipeKindToTitleProvider", MaterialTaskGeneratorTestMain::shouldPassRecipeKindToTitleProvider);
        GuiTestSupport.runTestCase("MaterialTaskGeneratorTestMain.shouldHintRecipeKindForFoldedMaterial", MaterialTaskGeneratorTestMain::shouldHintRecipeKindForFoldedMaterial);
    }

    /**
     * 验证标题回调能拿到每个物品的配方类型：展开两层时依次为 工作台合成 / 熔炼 / 无配方。
     *
     * <p>配方类型是任务标题「[功能方块] 动作词 产物 ×数量」提示的来源；
     * 无配方的最终材料传 null，由回调回退为「收集」措辞。
     */
    private static void shouldPassRecipeKindToTitleProvider() {
        MaterialPlan plan = ironBlockPlanExpandedTwice();

        List<String> calls = new ArrayList<>();
        MaterialTaskContext context = new MaterialTaskContext("project-1", Task.Scope.PERSONAL,
                "creator-uuid", null, null, recordingTitleProvider(calls));
        MaterialTaskGenerator.generate(plan, IRON_BLOCK, 3, MaterialTaskMode.TARGET_WITH_MATERIALS, context);

        GuiTestSupport.assertEquals(3, calls.size(), "三层任务各应回调一次标题渲染");
        GuiTestSupport.assertEquals(IRON_BLOCK + "|CRAFTING", calls.get(0), "目标物应提示工作台合成");
        GuiTestSupport.assertEquals(IRON_INGOT + "|SMELTING", calls.get(1), "中间产物应提示熔炉熔炼");
        GuiTestSupport.assertEquals(RAW_IRON + "|none", calls.get(2), "最终材料无配方，应传 null");
    }

    /**
     * 验证默认只展开一层时，「折叠（未展开）」的直接材料也会带上推荐配方类型。
     *
     * <p>不这样做的话，默认视图里除目标物外全是「收集」，功能方块提示就丢了。
     */
    private static void shouldHintRecipeKindForFoldedMaterial() {
        MaterialPlan plan = ironBlockPlan();

        List<String> calls = new ArrayList<>();
        MaterialTaskContext context = new MaterialTaskContext("project-1", Task.Scope.PERSONAL,
                "creator-uuid", null, null, recordingTitleProvider(calls));
        MaterialTaskGenerator.generate(plan, IRON_BLOCK, 3, MaterialTaskMode.TARGET_WITH_MATERIALS, context);

        GuiTestSupport.assertEquals(2, calls.size(), "默认一层应生成两条任务");
        GuiTestSupport.assertEquals(IRON_BLOCK + "|CRAFTING", calls.get(0), "目标物应提示工作台合成");
        GuiTestSupport.assertEquals(IRON_INGOT + "|SMELTING", calls.get(1),
                "折叠的直接材料应提示熔炉熔炼（推荐配方）");
    }

    /**
     * 构造用于记录配方类型的标题回调：把「物品|配方类型」按调用顺序写入结果列表。
     *
     * @param calls 输出：按调用顺序记录的「物品资源 ID|配方类型」
     * @return 标题回调
     */
    private static MaterialTaskTitleProvider recordingTitleProvider(List<String> calls) {
        return (itemId, count, kind) -> {
            calls.add(itemId + "|" + (kind == null ? "none" : kind.name()));
            return itemId + " x" + count;
        };
    }

    /**
     * 验证同种中间产物出现在两个分支时也只生成一条任务，且数量取两个分支的汇总。
     *
     * <p>否则两条同类任务会用同一份进度（收集类看持有量、合成类看每次合成量），
     * 一条满足就会把另一条也推到达标，表现为"任务进度共享"。
     */
    private static void shouldMergeRepeatedIntermediateIntoOneTask() {
        MaterialPlan plan = sharedIntermediatePlan();

        List<Task> tasks = MaterialTaskGenerator.generate(
                plan, "test:target", 2, MaterialTaskMode.TARGET_WITH_MATERIALS, personalContext());

        GuiTestSupport.assertEquals(5, tasks.size(), "应生成 目标 + 部件A + 共享件 + 原料 + 部件B 共 5 条任务");
        Task partA = findByTarget(tasks, "test:part_a");
        Task partB = findByTarget(tasks, "test:part_b");
        Task shared = findByTarget(tasks, SHARED_PART);
        Task raw = findByTarget(tasks, RAW_IRON);
        GuiTestSupport.assertTrue(partA != null && partB != null && shared != null && raw != null,
                "每个材料都应生成一条任务");
        GuiTestSupport.assertEquals(1, countByTarget(tasks, SHARED_PART), "同种中间产物只应生成一条任务");
        GuiTestSupport.assertEquals(4, shared.getTrigger().getTargetCount(),
                "两个分支各需 2 个，共享件数量应取汇总需求 4");
        GuiTestSupport.assertTrue(shared.getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "材料反推任务一律用「收集」语义（拿到即达成，配方只作为标题提示）");
        GuiTestSupport.assertEquals(partA.getId(), shared.getParentTaskId(),
                "合并后的任务应挂在首次出现的位置（部件A 下）");
        GuiTestSupport.assertEquals(shared.getId(), raw.getParentTaskId(),
                "只在重复分支里出现的更深层材料，应挂到最近的已生成祖先任务下");
        GuiTestSupport.assertEquals(1, countByTarget(tasks, RAW_IRON), "原料同样只应生成一条任务");
        GuiTestSupport.assertEquals(4, raw.getTrigger().getTargetCount(), "原料数量应取汇总需求 4");
    }

    /**
     * 在任务列表中按触发器目标查找任务。
     *
     * @param tasks  任务列表
     * @param target 目标物品资源 ID
     * @return 首个匹配任务；没有时返回 null
     */
    private static Task findByTarget(List<Task> tasks, String target) {
        for (Task task : tasks) {
            if (target.equals(task.getTrigger().getTarget())) {
                return task;
            }
        }
        return null;
    }

    /**
     * 统计追踪指定物品的任务条数。
     *
     * @param tasks  任务列表
     * @param target 目标物品资源 ID
     * @return 任务条数
     */
    private static int countByTarget(List<Task> tasks, String target) {
        int count = 0;
        for (Task task : tasks) {
            if (target.equals(task.getTrigger().getTarget())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 验证同种最终材料出现在多个层级时只生成一条「收集」任务，且数量取汇总需求。
     *
     * <p>否则多条任务各自用「持有量」语义比较，背包里只有一半数量就会把几条任务全部判为完成。
     */
    private static void shouldMergeRepeatedFinalMaterialIntoOneTask() {
        MaterialPlan plan = sharedLogPlan();

        GuiTestSupport.assertEquals(3, plan.totalFor(OAK_LOG), "三个层级各需 1 个原木，汇总应为 3");

        List<Task> tasks = MaterialTaskGenerator.generate(
                plan, "test:target", 1, MaterialTaskMode.TARGET_WITH_MATERIALS, personalContext());

        List<Task> logTasks = new ArrayList<>();
        for (Task task : tasks) {
            if (OAK_LOG.equals(task.getTrigger().getTarget())) {
                logTasks.add(task);
            }
        }
        GuiTestSupport.assertEquals(4, tasks.size(), "应生成 目标 + 原木 + 部件A + 部件B 共 4 条任务");
        GuiTestSupport.assertEquals(1, logTasks.size(), "同种最终材料只应生成一条收集任务");
        Task logTask = logTasks.get(0);
        GuiTestSupport.assertTrue(logTask.getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "最终材料应挂 ITEM_COLLECT 触发器");
        GuiTestSupport.assertEquals(3, logTask.getTrigger().getTargetCount(),
                "数量应取汇总需求，而不是各层级各自的需求");
        GuiTestSupport.assertEquals(tasks.get(0).getId(), logTask.getParentTaskId(),
                "合并后的任务应挂在首次出现的位置（目标物任务下）");
        GuiTestSupport.assertTrue(logTask.isSubtask(), "合并后的收集任务应是子任务");
    }

    /**
     * 模式 A：只生成一条目标任务，挂 ITEM_COLLECT 且不使用计划内容。
     */
    private static void shouldGenerateTargetOnlyTask() {
        List<Task> tasks = MaterialTaskGenerator.generate(
                ironBlockPlan(), IRON_BLOCK, 3, MaterialTaskMode.TARGET_ONLY, personalContext());

        GuiTestSupport.assertEquals(1, tasks.size(), "模式 A 只应生成目标任务");
        Task task = tasks.get(0);
        GuiTestSupport.assertEquals(IRON_BLOCK, task.getTrigger().getTarget(), "目标任务应追踪目标物品");
        GuiTestSupport.assertEquals(3, task.getTrigger().getTargetCount(), "目标任务数量应为目标数量");
        GuiTestSupport.assertTrue(task.getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "目标任务应使用 ITEM_COLLECT 触发器");
        GuiTestSupport.assertTrue(task.isTopLevelTask(), "目标任务应为顶层任务");
    }

    /**
     * 模式 B：默认只展开一层，生成「目标物收集任务 + 直接材料收集任务」。
     *
     * <p>两条任务都挂 {@code ITEM_COLLECT}（持有量语义）：最终目标是拿到材料，
     * 配方（含功能方块）只作为标题提示，不强制必须自己制作。
     */
    private static void shouldGenerateParentWithMaterialSubtasks() {
        List<Task> tasks = MaterialTaskGenerator.generate(
                ironBlockPlan(), IRON_BLOCK, 3, MaterialTaskMode.TARGET_WITH_MATERIALS, personalContext());

        GuiTestSupport.assertEquals(2, tasks.size(), "默认展开一层：目标物 + 一层直接材料");
        Task parent = tasks.get(0);
        Task child = tasks.get(1);
        GuiTestSupport.assertEquals(IRON_BLOCK + " x3", parent.getTitle(), "父任务标题应由标题回调生成");
        GuiTestSupport.assertTrue(parent.isTopLevelTask(), "父任务应为顶层任务");
        GuiTestSupport.assertTrue(parent.getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "材料反推任务一律用「收集」语义，目标物也不例外");
        GuiTestSupport.assertEquals(IRON_BLOCK, parent.getTrigger().getTarget(), "目标任务应追踪目标物品");
        GuiTestSupport.assertEquals(3, parent.getTrigger().getTargetCount(), "目标任务数量应为目标数量");
        GuiTestSupport.assertEquals(IRON_INGOT, child.getTrigger().getTarget(), "子任务应追踪默认展开出的直接材料");
        GuiTestSupport.assertEquals(27, child.getTrigger().getTargetCount(), "铁块 ×3 需要铁锭 ×27");
        GuiTestSupport.assertTrue(child.getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "直接材料任务应使用 ITEM_COLLECT 触发器");
        GuiTestSupport.assertTrue(child.isSubtask(), "材料任务应挂为子任务");
        GuiTestSupport.assertEquals(parent.getId(), child.getParentTaskId(), "子任务应挂到父任务下");
        GuiTestSupport.assertEquals(0L, child.getSubtaskSortOrder(), "首个子任务排序号应为 0");
    }

    /**
     * 验证任务层级与预览展开层级一一对应：展开两层时生成三层依赖任务链，
     * 每一层的任务都挂自身的 {@code ITEM_COLLECT} 触发器（材料反推一律「收集」语义）。
     */
    private static void shouldGenerateDependencyChainMatchingExpandedLevels() {
        List<Task> tasks = MaterialTaskGenerator.generate(
                ironBlockPlanExpandedTwice(), IRON_BLOCK, 3, MaterialTaskMode.TARGET_WITH_MATERIALS, personalContext());

        GuiTestSupport.assertEquals(3, tasks.size(), "展开两层应生成三层任务链");
        Task root = tasks.get(0);
        Task middle = tasks.get(1);
        Task leaf = tasks.get(2);
        GuiTestSupport.assertTrue(root.isTopLevelTask(), "根任务应为顶层任务");
        GuiTestSupport.assertTrue(root.getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "根任务同样用「收集」语义（目标物由工作台合成，该信息只体现在标题提示里）");
        GuiTestSupport.assertEquals(root.getId(), middle.getParentTaskId(), "中间产物应挂到根任务下");
        GuiTestSupport.assertTrue(middle.getTrigger().getType() == TaskTrigger.Type.ITEM_COLLECT,
                "中间产物同样用「收集」语义（拿到铁锭即达成，是否熔炼出来不强制）");
        GuiTestSupport.assertEquals(IRON_INGOT, middle.getTrigger().getTarget(), "中间产物触发器应追踪铁锭");
        GuiTestSupport.assertEquals(middle.getId(), leaf.getParentTaskId(), "最终材料应挂到中间产物下");
        GuiTestSupport.assertEquals(RAW_IRON, leaf.getTrigger().getTarget(), "最深层应为最终材料粗铁");
        GuiTestSupport.assertEquals(27, leaf.getTrigger().getTargetCount(), "铁块 ×3 需要粗铁 ×27");
    }

    /**
     * 验证项目、作用域与归属信息透传到每一条任务，且每条任务都带自身触发器。
     */
    private static void shouldCopyContextIntoEveryTask() {
        MaterialTaskContext context = new MaterialTaskContext(
                "project-1", Task.Scope.TEAM, "creator-uuid", "assignee-uuid", "Alice", defaultTitleProvider());
        List<Task> tasks = MaterialTaskGenerator.generate(
                ironBlockPlanExpandedTwice(), IRON_BLOCK, 3, MaterialTaskMode.TARGET_WITH_MATERIALS, context);

        GuiTestSupport.assertEquals(3, tasks.size(), "应生成三层依赖任务");
        for (Task task : tasks) {
            GuiTestSupport.assertEquals("project-1", task.getProjectId(), "任务应挂在当前项目下");
            GuiTestSupport.assertTrue(task.getScope() == Task.Scope.TEAM, "任务作用域应透传");
            GuiTestSupport.assertEquals("creator-uuid", task.getCreatorUuid(), "创建者应透传");
            GuiTestSupport.assertEquals("assignee-uuid", task.getAssigneeUuid(), "负责人应透传");
            GuiTestSupport.assertEquals("Alice", task.getAssigneeName(), "负责人显示名应透传");
            GuiTestSupport.assertTrue(task.hasTrigger(), "每条任务都应挂自身触发器");
        }
    }

    /**
     * 验证目标物本身就是最终材料（无配方）时，不会重复生成一条同名子任务。
     */
    private static void shouldSkipTargetItemInSubtasks() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        MaterialPlan plan = MaterialResolver.resolve(RAW_IRON, 5, index);

        List<Task> tasks = MaterialTaskGenerator.generate(
                plan, RAW_IRON, 5, MaterialTaskMode.TARGET_WITH_MATERIALS, personalContext());

        GuiTestSupport.assertEquals(1, tasks.size(), "目标物即最终材料时，模式 B 也只应生成一条任务");
        GuiTestSupport.assertTrue(tasks.get(0).isTopLevelTask(), "唯一任务应为顶层任务");
    }

    /**
     * 验证未提供标题渲染回调时回退为物品资源 ID，不抛异常。
     */
    private static void shouldFallbackTitleWhenProviderMissing() {
        MaterialTaskContext context = new MaterialTaskContext("project-1", Task.Scope.PERSONAL, null, null, null, null);
        List<Task> tasks = MaterialTaskGenerator.generate(
                null, IRON_BLOCK, 8, MaterialTaskMode.TARGET_ONLY, context);

        GuiTestSupport.assertEquals(1, tasks.size(), "空计划在模式 A 下仍应生成目标任务");
        GuiTestSupport.assertEquals(IRON_BLOCK, tasks.get(0).getTitle(), "缺少渲染回调时应回退为物品资源 ID");
    }

    /**
     * 验证非法入参返回空列表而不是抛异常。
     */
    private static void shouldReturnEmptyForInvalidInput() {
        GuiTestSupport.assertTrue(
                MaterialTaskGenerator.generate(ironBlockPlan(), null, 1, MaterialTaskMode.TARGET_ONLY, personalContext()).isEmpty(),
                "目标物品为空应返回空列表");
        GuiTestSupport.assertTrue(
                MaterialTaskGenerator.generate(ironBlockPlan(), "", 1, MaterialTaskMode.TARGET_ONLY, personalContext()).isEmpty(),
                "目标物品为空串应返回空列表");
        GuiTestSupport.assertTrue(
                MaterialTaskGenerator.generate(ironBlockPlan(), IRON_BLOCK, 1, MaterialTaskMode.TARGET_ONLY, null).isEmpty(),
                "上下文为 null 应返回空列表");

        List<Task> noPlan = MaterialTaskGenerator.generate(null, IRON_BLOCK, 1, MaterialTaskMode.TARGET_WITH_MATERIALS, personalContext());
        GuiTestSupport.assertEquals(1, noPlan.size(), "计划为 null 时模式 B 应退化为只生成目标任务");
    }

    /**
     * 构造铁块 ×3 的材料计划：铁块 ← 铁锭 ×9，铁锭 ← 熔炼粗铁（终止条件命中，粗铁为最终材料）。
     *
     * @return 材料计划
     */
    private static MaterialPlan ironBlockPlan() {
        return MaterialResolver.resolve(IRON_BLOCK, 3, blockIngotRecipeIndex());
    }

    /**
     * 构造铁块 ×3 且强制展开两层的材料计划：铁块 ← 铁锭 ← 粗铁。
     *
     * @return 材料计划
     */
    private static MaterialPlan ironBlockPlanExpandedTwice() {
        MaterialResolveOptions options = new MaterialResolveOptions(
                MaterialResolveOptions.DEFAULT_MAX_DEPTH,
                MaterialResolveOptions.DEFAULT_MAX_NODES,
                Map.of(),
                Set.of(),
                Set.of(IRON_INGOT),
                MaterialResolveOptions.DEFAULT_EXPAND_DEPTH);
        return MaterialResolver.resolve(IRON_BLOCK, 3, blockIngotRecipeIndex(), options);
    }

    /**
     * 构造「原木在三个层级各出现一次」的材料计划：
     * 目标 ← 原木 ×1 + 部件A；部件A ← 原木 ×1 + 部件B；部件B ← 原木 ×1。
     *
     * @return 材料计划
     */
    private static MaterialPlan sharedLogPlan() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("target", "test:target", 1, List.of(
                new MaterialIngredient(List.of(OAK_LOG), 1),
                new MaterialIngredient(List.of("test:part_a"), 1)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("part_a", "test:part_a", 1, List.of(
                new MaterialIngredient(List.of(OAK_LOG), 1),
                new MaterialIngredient(List.of("test:part_b"), 1)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("part_b", "test:part_b", 1, List.of(
                new MaterialIngredient(List.of(OAK_LOG), 1)), MaterialRecipeKind.CRAFTING));
        MaterialResolveOptions options = new MaterialResolveOptions(
                MaterialResolveOptions.DEFAULT_MAX_DEPTH,
                MaterialResolveOptions.DEFAULT_MAX_NODES,
                Map.of(),
                Set.of(),
                Set.of("test:part_a", "test:part_b"),
                MaterialResolveOptions.DEFAULT_EXPAND_DEPTH);
        return MaterialResolver.resolve("test:target", 1, index, options);
    }

    /**
     * 构造「中间产物跨分支重复」的材料计划：
     * 目标 ← 部件A + 部件B；部件A ← 共享件；部件B ← 共享件；共享件 ← 粗铁。
     *
     * @return 材料计划
     */
    private static MaterialPlan sharedIntermediatePlan() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("target", "test:target", 1, List.of(
                new MaterialIngredient(List.of("test:part_a"), 1),
                new MaterialIngredient(List.of("test:part_b"), 1)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("part_a", "test:part_a", 1, List.of(
                new MaterialIngredient(List.of(SHARED_PART), 1)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("part_b", "test:part_b", 1, List.of(
                new MaterialIngredient(List.of(SHARED_PART), 1)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("shared", SHARED_PART, 1, List.of(
                new MaterialIngredient(List.of(RAW_IRON), 1)), MaterialRecipeKind.CRAFTING));
        MaterialResolveOptions options = new MaterialResolveOptions(
                MaterialResolveOptions.DEFAULT_MAX_DEPTH,
                MaterialResolveOptions.DEFAULT_MAX_NODES,
                Map.of(),
                Set.of(),
                Set.of("test:part_a", "test:part_b", SHARED_PART),
                MaterialResolveOptions.DEFAULT_EXPAND_DEPTH);
        return MaterialResolver.resolve("test:target", 2, index, options);
    }

    /**
     * 构造「铁块 ← 铁锭 ← 熔炼粗铁」的配方索引。
     *
     * @return 配方索引
     */
    private static MaterialRecipeIndex blockIngotRecipeIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("iron_block", IRON_BLOCK, 1,
                List.of(new MaterialIngredient(List.of(IRON_INGOT), 9)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of(RAW_IRON), 1)), MaterialRecipeKind.SMELTING));
        return index;
    }

    /**
     * 构造个人作用域上下文。
     *
     * @return 上下文
     */
    private static MaterialTaskContext personalContext() {
        return new MaterialTaskContext("project-1", Task.Scope.PERSONAL, "creator-uuid", null, null, defaultTitleProvider());
    }

    /**
     * 构造测试用标题渲染回调。
     *
     * @return 标题回调
     */
    private static MaterialTaskTitleProvider defaultTitleProvider() {
        return (itemId, count, kind) -> itemId + " x" + count;
    }
}
