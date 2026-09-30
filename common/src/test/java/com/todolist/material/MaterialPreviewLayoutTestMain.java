package com.todolist.material;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.List;

/**
 * 材料预览横向分层布局的离线自测：列 / 行分配、功能方块节点位置、
 * 父节点行居中与内容尺寸（供界面做双向滚动）。
 */
public final class MaterialPreviewLayoutTestMain {

    /** 目标物：铁块。 */
    private static final String IRON_BLOCK = "minecraft:iron_block";
    /** 直接材料：铁锭。 */
    private static final String IRON_INGOT = "minecraft:iron_ingot";

    /**
     * 工具类不需要实例化。
     */
    private MaterialPreviewLayoutTestMain() {
    }

    /**
     * 执行布局测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("MaterialPreviewLayoutTestMain.shouldPlaceColumnsWithStationBetweenProductAndMaterials", MaterialPreviewLayoutTestMain::shouldPlaceColumnsWithStationBetweenProductAndMaterials);
        GuiTestSupport.runTestCase("MaterialPreviewLayoutTestMain.shouldCenterParentRowBetweenChildren", MaterialPreviewLayoutTestMain::shouldCenterParentRowBetweenChildren);
        GuiTestSupport.runTestCase("MaterialPreviewLayoutTestMain.shouldReturnEmptyLayoutForEmptyPlan", MaterialPreviewLayoutTestMain::shouldReturnEmptyLayoutForEmptyPlan);
    }

    /**
     * 验证列分配：产物在第 N 列、直接材料在第 N+1 列，功能方块节点插在两者之间；
     * 折叠但已带推荐配方的节点同样有功能方块节点。
     */
    private static void shouldPlaceColumnsWithStationBetweenProductAndMaterials() {
        MaterialPlan plan = MaterialResolver.resolve(IRON_BLOCK, 3, ironBlockIndex());
        MaterialPreviewLayout layout = MaterialPreviewLayout.compute(plan);

        List<MaterialPreviewLayout.ItemCell> items = layout.itemCells();
        GuiTestSupport.assertEquals(2, items.size(), "默认只展开一层：产物 + 直接材料共两个物品节点");
        GuiTestSupport.assertEquals(IRON_BLOCK, items.get(0).node().itemId(), "第 0 列应为目标物");
        GuiTestSupport.assertEquals(0, items.get(0).depth(), "目标物列号应为 0");
        GuiTestSupport.assertEquals(IRON_INGOT, items.get(1).node().itemId(), "第 1 列应为直接材料");
        GuiTestSupport.assertEquals(1, items.get(1).depth(), "直接材料列号应为 1");
        GuiTestSupport.assertEquals(items.get(0).x() + MaterialPreviewLayout.COLUMN_PITCH, items.get(1).x(),
                "相邻两列的水平间距应为一个列距");
        GuiTestSupport.assertTrue(items.get(0).iconX() - MaterialPreviewLayout.LABEL_MAX_WIDTH / 2 >= 0,
                "首列应让出半个标签宽，使居中绘制的最大宽度名称也不会超出内容左边界");

        List<MaterialPreviewLayout.StationCell> stations = layout.stationCells();
        GuiTestSupport.assertEquals(2, stations.size(),
                "目标物与折叠的直接材料都应带上功能方块节点（折叠节点已带推荐配方）");
        GuiTestSupport.assertEquals(MaterialPreviewLayout.STATION_OFFSET,
                stations.get(0).x() - items.get(0).x(), "功能方块节点应在所属物品右侧固定偏移处");
        GuiTestSupport.assertEquals("minecraft:crafting_table", stations.get(0).stationItemId(),
                "铁块由工作台合成");
        GuiTestSupport.assertEquals("minecraft:furnace", stations.get(1).stationItemId(),
                "铁锭默认走熔炉熔炼");
        GuiTestSupport.assertNotNull(layout.stationOf(plan.root()), "根节点应有功能方块格子");
    }

    /**
     * 验证多材料时父节点行取首末子节点的中点，子树占据连续行区间。
     */
    private static void shouldCenterParentRowBetweenChildren() {
        MaterialPlan plan = MaterialResolver.resolve("test:target", 1, fanIndex());
        MaterialPreviewLayout layout = MaterialPreviewLayout.compute(plan);

        List<MaterialPreviewLayout.ItemCell> items = layout.itemCells();
        GuiTestSupport.assertEquals(6, items.size(), "目标物 + 五个直接材料共六个物品节点");
        MaterialPreviewLayout.ItemCell root = layout.cellOf(plan.root());
        GuiTestSupport.assertNotNull(root, "根节点应有对应的格子");
        GuiTestSupport.assertEquals(2, root.row(), "父节点行应取首末子节点行的中点");
        GuiTestSupport.assertEquals(0, items.get(1).row(), "第一个直接材料应占第 0 行");
        GuiTestSupport.assertEquals(4, items.get(5).row(), "最后一个直接材料应占第 4 行");
        GuiTestSupport.assertEquals(MaterialPreviewLayout.ROW_PITCH * 5 + MaterialPreviewLayout.MARGIN * 2,
                layout.height(), "内容高度应按行数与行距计算，供界面纵向滚动");
    }

    /**
     * 验证空计划返回空布局，且不会因为 null 崩溃。
     */
    private static void shouldReturnEmptyLayoutForEmptyPlan() {
        MaterialPreviewLayout layout = MaterialPreviewLayout.compute(null);

        GuiTestSupport.assertTrue(layout.itemCells().isEmpty(), "空计划不应有任何物品节点");
        GuiTestSupport.assertTrue(layout.stationCells().isEmpty(), "空计划不应有任何功能方块节点");
        GuiTestSupport.assertEquals(0, layout.width(), "空计划内容宽度应为 0");
        GuiTestSupport.assertEquals(0, layout.height(), "空计划内容高度应为 0");
        GuiTestSupport.assertNull(layout.cellOf(null), "传入 null 节点应返回 null");
    }

    /**
     * 构造「铁块 ← 铁锭 ×9，铁锭 ← 熔炼粗铁」的索引。
     *
     * @return 索引
     */
    private static MaterialRecipeIndex ironBlockIndex() {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        index.add(new MaterialRecipe("iron_block", IRON_BLOCK, 1,
                List.of(new MaterialIngredient(List.of(IRON_INGOT), 9)), MaterialRecipeKind.CRAFTING));
        index.add(new MaterialRecipe("iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1,
                List.of(new MaterialIngredient(List.of("minecraft:raw_iron"), 1)), MaterialRecipeKind.SMELTING));
        return index;
    }

    /**
     * 构造「目标 ← 五个直接材料」的索引（行数多，用于验证行分配）。
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
}
