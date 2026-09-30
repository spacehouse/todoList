package com.todolist.material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 材料预览树（横向分层布局）的纯几何计算，不依赖 MC 运行时，可离线测试。
 *
 * <p>布局规则：
 * <ul>
 *     <li><b>列</b>：目标物在第 0 列，每深一层向右一列（间距 {@link #COLUMN_PITCH}）；</li>
 *     <li><b>功能方块节点</b>：插在物品与其下级配方之间（相对所属物品列右移 {@link #STATION_OFFSET}）。
 *         只要该节点解析出了配方就画——默认折叠但已带上推荐配方的节点同样画，
 *         用于提示「这一步该用哪个方块做」；</li>
 *     <li><b>行</b>：后序分配，叶子依次占一行，父节点取首末子节点的行中点，
 *         保证同一棵子树占据连续的行区间、连线不交叉；</li>
 *     <li><b>尺寸</b>：{@link #width()} / {@link #height()} 给出内容总尺寸，供界面做双向滚动。</li>
 * </ul>
 */
public final class MaterialPreviewLayout {

    /** 物品/功能方块图标边长（像素）。 */
    public static final int ICON_SIZE = 16;
    /** 节点格子边长（像素），图标居中绘制。 */
    public static final int CELL_SIZE = 22;
    /** 节点下方文本占用高度（像素）：名称 + 数量两行。 */
    public static final int LABEL_HEIGHT = 20;
    /** 节点命中区的额外外扩（像素），让数量文本也可点。 */
    public static final int HIT_PADDING = 3;
    /** 同一列内的行距（像素）。 */
    public static final int ROW_PITCH = 54;
    /** 相邻两列（物品列）的水平间距（像素）。 */
    public static final int COLUMN_PITCH = 96;
    /** 功能方块节点相对所属物品列的横向偏移（像素）。 */
    public static final int STATION_OFFSET = 40;
    /** 内容四周留白（像素）。 */
    public static final int MARGIN = 8;
    /** 图标相对格子左上角的内缩（像素）。 */
    public static final int ICON_INSET = (CELL_SIZE - ICON_SIZE) / 2;
    /** 节点名称允许占用的最大宽度（像素），超出部分由界面截断。 */
    public static final int LABEL_MAX_WIDTH = COLUMN_PITCH - 6;
    /** 为「居中标签」在首尾列两侧预留的边距（像素）：半个标签宽 − 半格宽。 */
    public static final int LABEL_SIDE_MARGIN = Math.max(0, (LABEL_MAX_WIDTH - CELL_SIZE) / 2);

    /**
     * 物品节点格子：图标 + 下方数量文本。
     *
     * @param node  展开树节点
     * @param depth 缩进层级（目标物为 0）
     * @param row   所在行号
     * @param x     格子左上角 X
     * @param y     格子左上角 Y
     */
    public record ItemCell(MaterialNode node, int depth, int row, int x, int y) {

        /**
         * 图标绘制与命中区的左上角 X。
         *
         * @return X 坐标
         */
        public int iconX() {
            return x + ICON_INSET;
        }

        /**
         * 图标绘制与命中区的左上角 Y。
         *
         * @return Y 坐标
         */
        public int iconY() {
            return y + ICON_INSET;
        }

        /**
         * 判断点是否落在整格命中区（含下方数量文本），用于「点击节点其余处展开/收起」。
         *
         * @param pointX 点 X
         * @param pointY 点 Y
         * @return 命中时返回 true
         */
        public boolean contains(int pointX, int pointY) {
            return pointX >= x - HIT_PADDING && pointX < x + CELL_SIZE + HIT_PADDING
                    && pointY >= y - HIT_PADDING && pointY < y + CELL_SIZE + LABEL_HEIGHT + HIT_PADDING;
        }

        /**
         * 判断点是否落在图标上，用于「点击图标选择替代材料」。
         *
         * @param pointX 点 X
         * @param pointY 点 Y
         * @return 命中图标时返回 true
         */
        public boolean containsIcon(int pointX, int pointY) {
            return pointX >= iconX() && pointX < iconX() + ICON_SIZE
                    && pointY >= iconY() && pointY < iconY() + ICON_SIZE;
        }
    }

    /**
     * 功能方块节点格子：只画图标，点图标切换配方。
     *
     * @param owner         所属物品节点
     * @param kind          该配方的类型
     * @param stationItemId 功能方块的物品资源 ID（用于取图标）
     * @param x             格子左上角 X
     * @param y             格子左上角 Y
     */
    public record StationCell(MaterialNode owner, MaterialRecipeKind kind, String stationItemId, int x, int y) {

        /**
         * 图标绘制与命中区的左上角 X。
         *
         * @return X 坐标
         */
        public int iconX() {
            return x + ICON_INSET;
        }

        /**
         * 图标绘制与命中区的左上角 Y。
         *
         * @return Y 坐标
         */
        public int iconY() {
            return y + ICON_INSET;
        }

        /**
         * 判断点是否落在图标上。
         *
         * @param pointX 点 X
         * @param pointY 点 Y
         * @return 命中图标时返回 true
         */
        public boolean containsIcon(int pointX, int pointY) {
            return pointX >= iconX() && pointX < iconX() + ICON_SIZE
                    && pointY >= iconY() && pointY < iconY() + ICON_SIZE;
        }
    }

    private final List<ItemCell> itemCells;
    private final List<StationCell> stationCells;
    private final Map<MaterialNode, ItemCell> cellByNode;
    private final Map<MaterialNode, StationCell> stationByNode;
    private final int width;
    private final int height;

    private MaterialPreviewLayout(List<ItemCell> itemCells,
                                  List<StationCell> stationCells,
                                  Map<MaterialNode, ItemCell> cellByNode,
                                  Map<MaterialNode, StationCell> stationByNode,
                                  int width,
                                  int height) {
        this.itemCells = itemCells;
        this.stationCells = stationCells;
        this.cellByNode = cellByNode;
        this.stationByNode = stationByNode;
        this.width = width;
        this.height = height;
    }

    /**
     * 计算材料计划的横向分层布局。
     *
     * @param plan 材料计划；为 null 或根节点为空时返回空布局
     * @return 布局结果
     */
    public static MaterialPreviewLayout compute(MaterialPlan plan) {
        if (plan == null || plan.root() == null) {
            return new MaterialPreviewLayout(List.of(), List.of(), Map.of(), Map.of(), 0, 0);
        }
        List<ItemCell> items = new ArrayList<>();
        List<StationCell> stations = new ArrayList<>();
        int[] nextRow = {0};
        int[] maxDepth = {0};
        place(plan.root(), 0, items, stations, nextRow, maxDepth);
        // 按（列, 行）排序，保证顺序稳定、与视觉上的从左到右一致
        items.sort(Comparator.comparingInt(ItemCell::depth).thenComparingInt(ItemCell::row));
        stations.sort(Comparator.comparingInt(StationCell::x).thenComparingInt(StationCell::y));
        Map<MaterialNode, ItemCell> cellByNode = new IdentityHashMap<>();
        for (ItemCell cell : items) {
            cellByNode.put(cell.node(), cell);
        }
        Map<MaterialNode, StationCell> stationByNode = new IdentityHashMap<>();
        for (StationCell cell : stations) {
            stationByNode.put(cell.owner(), cell);
        }
        int usedRows = Math.max(1, nextRow[0]);
        int width = MARGIN * 2 + LABEL_SIDE_MARGIN * 2 + maxDepth[0] * COLUMN_PITCH + CELL_SIZE;
        int height = MARGIN * 2 + usedRows * ROW_PITCH;
        return new MaterialPreviewLayout(List.copyOf(items), List.copyOf(stations),
                cellByNode, stationByNode, width, height);
    }

    /**
     * 递归放置一个节点及其子树，返回该节点所占行号。
     *
     * @param node      当前节点
     * @param depth     当前列号
     * @param items     输出：物品格子
     * @param stations  输出：功能方块格子
     * @param nextRow   下一个可用的叶子行号（单元素数组作为可变计数器）
     * @param maxDepth  当前最大列号（单元素数组）
     * @return 当前节点占用的行号
     */
    private static int place(MaterialNode node,
                             int depth,
                             List<ItemCell> items,
                             List<StationCell> stations,
                             int[] nextRow,
                             int[] maxDepth) {
        int row;
        List<MaterialNode> children = node.children();
        if (children.isEmpty()) {
            row = nextRow[0]++;
        } else {
            int firstRow = -1;
            int lastRow = -1;
            for (MaterialNode child : children) {
                int childRow = place(child, depth + 1, items, stations, nextRow, maxDepth);
                if (firstRow < 0) {
                    firstRow = childRow;
                }
                lastRow = childRow;
            }
            row = (firstRow + lastRow) / 2;
        }
        // 节点名称是居中画在格子下方的，首列必须让出半个标签宽，
        // 否则较长的名称会被左边界裁掉（末尾列同理，所以内容宽度也按两侧各留一份计算）
        int x = MARGIN + LABEL_SIDE_MARGIN + depth * COLUMN_PITCH;
        int y = MARGIN + row * ROW_PITCH;
        items.add(new ItemCell(node, depth, row, x, y));
        MaterialRecipe recipe = node.recipe();
        MaterialRecipeKind kind = recipe == null ? null : recipe.kind();
        if (kind != null && kind.stationItemId() != null) {
            stations.add(new StationCell(node, kind, kind.stationItemId(), x + STATION_OFFSET, y));
        }
        maxDepth[0] = Math.max(maxDepth[0], depth);
        return row;
    }

    /**
     * 返回全部物品格子（按列、行排序）。
     *
     * @return 物品格子列表
     */
    public List<ItemCell> itemCells() {
        return itemCells;
    }

    /**
     * 返回全部功能方块格子。
     *
     * @return 功能方块格子列表
     */
    public List<StationCell> stationCells() {
        return stationCells;
    }

    /**
     * 按节点取物品格子。
     *
     * @param node 展开树节点（按实例比较）
     * @return 格子；未放置时返回 null
     */
    public ItemCell cellOf(MaterialNode node) {
        return node == null ? null : cellByNode.get(node);
    }

    /**
     * 按节点取功能方块格子。
     *
     * @param node 展开树节点（按实例比较）
     * @return 格子；该节点没有配方时返回 null
     */
    public StationCell stationOf(MaterialNode node) {
        return node == null ? null : stationByNode.get(node);
    }

    /**
     * 返回内容总宽度（供横向滚动）。
     *
     * @return 宽度（像素）
     */
    public int width() {
        return width;
    }

    /**
     * 返回内容总高度（供纵向滚动）。
     *
     * @return 高度（像素）
     */
    public int height() {
        return height;
    }
}
