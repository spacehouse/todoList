package com.todolist.gui;

import com.todolist.client.TriggerTargetSupport;
import com.todolist.material.MaterialNode;
import com.todolist.material.MaterialPlan;
import com.todolist.material.MaterialPreviewLayout;
import com.todolist.material.MaterialPreviewState;
import com.todolist.material.MaterialRecipe;
import com.todolist.material.MaterialRecipeIndex;
import com.todolist.material.MaterialRecipeKind;
import com.todolist.material.MaterialStopReason;
import com.todolist.material.MaterialTaskContext;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 材料反推预览界面（横向配方树）。
 *
 * <p>布局为从左（目标物）到右（原料）的分层配方树，物品与其下级配方之间插入一个
 * **功能方块节点**（工作台 / 熔炉 / 切石机 …），一眼能看出「这一步用哪个方块做」。
 *
 * <p>交互（全部在树上直接完成，不需要底部操作按钮）：
 * <ul>
 *     <li>点击**物品图标**：该输入有多种可选材料时弹出「可选材料」列表供选择；</li>
 *     <li>点击**功能方块图标**：该物品有多条配方时切到下一条（方块与下级材料随之变化）；</li>
 *     <li>点击**节点其余区域**（名称 / 数量）：展开或收起该节点的下级配方；</li>
 *     <li>滚轮纵向滚动，Shift + 滚轮横向滚动，空白处拖拽可自由平移。</li>
 * </ul>
 *
 * <p>生成任务时**只按预览树显示出来的部分**：展开的分支生成下级材料任务，
 * 收起的节点作为该分支的最终材料生成自身任务（等价于「只要目标一条任务」＝ 把目标节点收起）。
 *
 * <p>全部语义落在 {@link MaterialPreviewState} 与 {@link MaterialPreviewLayout}，本类只负责
 * 渲染与事件分发；生成结果通过回调交给调用方（任务列表）走现有保存链路。
 */
public class MaterialListScreen extends Screen {

    /** 面板最小宽度（像素）。 */
    private static final int PANEL_MIN_WIDTH = 300;
    /** 面板最大宽度（像素）。 */
    private static final int PANEL_MAX_WIDTH = 760;
    /** 面板最小高度（像素）。 */
    private static final int PANEL_MIN_HEIGHT = 220;
    /** 面板最大高度（像素）。 */
    private static final int PANEL_MAX_HEIGHT = 400;
    /** 滚轮纵向滚动步长（像素）。 */
    private static final int SCROLL_STEP_Y = 24;
    /** 滚轮横向滚动步长（像素）。 */
    private static final int SCROLL_STEP_X = 32;
    /** 树形连线颜色。 */
    private static final int CONNECTOR_COLOR = 0x88AAAAAA;
    /** 节点悬停高亮色。 */
    private static final int HOVER_COLOR = 0x38FFFFFF;
    /** 可点击图标悬停高亮色。 */
    private static final int ICON_HOVER_COLOR = 0x6000A0FF;
    /** 功能方块节点底色（有底 + 有框，与无底色的物品节点区分开）。 */
    private static final int STATION_BACKGROUND_COLOR = 0x70323C4C;
    /** 功能方块节点边框色。 */
    private static final int STATION_BORDER_COLOR = 0xFF6A7F9A;
    /** 可切换配方的功能方块节点边框色（更亮，提示可以点）。 */
    private static final int STATION_BORDER_ACTIVE_COLOR = 0xFF7FB2E5;
    /** 可切换配方的右上角标记色。 */
    private static final int STATION_MARKER_COLOR = 0xFF7FB2E5;
    /** 材料数量颜色：尚未满足需求。 */
    private static final int MATERIAL_COUNT_COLOR = 0xFF9CDCFE;
    /** 材料数量颜色：背包持有量已达到或超过需求（标绿提示无需再准备）。 */
    private static final int MATERIAL_SUFFICIENT_COLOR = 0xFF55FF55;
    /** 背包已有数量的提示颜色。 */
    private static final int MATERIAL_HELD_COLOR = 0xFF888888;
    /** 弹出列表单行高度（像素）。 */
    private static final int POPUP_ROW_HEIGHT = 18;
    /** 弹出列表最多显示的行数。 */
    private static final int POPUP_MAX_ROWS = 8;
    /** 弹出列表最小宽度（像素）。 */
    private static final int POPUP_MIN_WIDTH = 96;
    /** 弹出列表最大宽度（像素）。 */
    private static final int POPUP_MAX_WIDTH = 220;

    private final Screen parent;
    private final MaterialRecipeIndex index;
    private final MaterialPreviewState state;
    private final MaterialTaskContext context;
    private final Consumer<List<Task>> generateCallback;

    private MaterialPlan plan;
    private MaterialPreviewLayout layout = MaterialPreviewLayout.compute(null);
    /** 节点 → 该节点可用的背包持有量（同一物品跨层级按显示顺序自上而下分配）。 */
    private Map<MaterialNode, Integer> allocatedHeldByNode = new IdentityHashMap<>();
    /** 玩家背包内已有物品数量（不展开潜影盒等容器内容），用于标注材料是否已经够用。 */
    private Map<String, Integer> heldItemCounts = Map.of();
    /** 背包持有量来源；默认读取玩家背包，测试可替换为固定数据。 */
    private Supplier<Map<String, Integer>> heldItemCountsSource = this::collectHeldItemCounts;

    private int scrollX;
    private int scrollY;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    /** 面板区域，供测试与弹出列表定位使用。 */
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;

    private EditBox countField;
    private Button generateButton;

    /** 已打开的候选材料弹出列表；为 null 表示未打开。 */
    private CandidatePopup candidatePopup;

    /** 本帧浮层（可选材料列表 / 悬浮提示）的板面；树上的图标与它相交时不绘制。 */
    private OverlayRect overlayRect;
    /** 本帧悬浮提示的文本行；为空表示不显示。 */
    private List<String> hoverTipLines = List.of();

    /** 拖拽平移起点（屏幕坐标）与起始滚动量；不在拖拽时 dragging 为 false。 */
    private boolean dragging;
    private double dragStartX;
    private double dragStartY;
    private int dragStartScrollX;
    private int dragStartScrollY;

    /** Shift 是否按下（按住时滚轮改为横向滚动）。由键盘事件维护，避免依赖窗口句柄。 */
    private boolean shiftHeld;

    /**
     * 浮层板面矩形（屏幕坐标）。
     *
     * <p>浮层（可选材料列表 / 悬浮提示）是浮在树之上的卡片。树上的**物品图标走独立渲染批次**，
     * 只靠绘制顺序压不住，因此本帧会把与浮层相交的图标直接跳过不画；
     * 文字与连线走普通批次，能被浮层正常盖住。
     *
     * @param left   左边界
     * @param top    上边界
     * @param width  宽度
     * @param height 高度
     */
    private record OverlayRect(int left, int top, int width, int height) {

        /**
         * 判断指定矩形是否与本浮层相交。
         *
         * @param x      矩形左边界
         * @param y      矩形上边界
         * @param w      矩形宽度
         * @param h      矩形高度
         * @return 相交时返回 true
         */
        private boolean intersects(int x, int y, int w, int h) {
            return x < left + width && x + w > left
                    && y < top + height && y + h > top;
        }
    }

    /**
     * 弹出式候选材料列表（点击物品图标时出现）。
     *
     * <p>板面几何在打开时按字体宽度算好，之后只做命中与滚动，不再依赖渲染上下文。
     */
    private static final class CandidatePopup {
        private final String candidateKey;
        private final List<String> candidates;
        private final String selectedItemId;
        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private int scroll;

        private CandidatePopup(String candidateKey,
                               List<String> candidates,
                               String selectedItemId,
                               int left,
                               int top,
                               int width,
                               int height) {
            this.candidateKey = candidateKey;
            this.candidates = candidates;
            this.selectedItemId = selectedItemId;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        /**
         * 返回列表可滚动的最大偏移行数。
         *
         * @return 最大偏移行数
         */
        private int maxScroll() {
            return Math.max(0, candidates.size() - POPUP_MAX_ROWS);
        }

        /**
         * 判断点是否落在弹出列表内。
         *
         * @param pointX 点 X
         * @param pointY 点 Y
         * @return 命中时返回 true
         */
        private boolean contains(double pointX, double pointY) {
            return pointX >= left && pointX < left + width && pointY >= top && pointY < top + height;
        }

        /**
         * 返回点命中的候选条目下标。
         *
         * @param pointY 点 Y
         * @return 条目下标；落在空白处时返回 -1
         */
        private int rowAt(double pointY) {
            int row = (int) ((pointY - top - 2) / POPUP_ROW_HEIGHT) + scroll;
            return row >= 0 && row < candidates.size() ? row : -1;
        }
    }

    /**
     * 创建材料预览界面。
     *
     * @param parent           父界面
     * @param index            配方反查索引
     * @param state            预览状态（含目标物品与数量）
     * @param context          生成上下文（项目 / 作用域 / 归属）
     * @param generateCallback 生成回调，参数为待入库的任务列表
     */
    public MaterialListScreen(Screen parent,
                              MaterialRecipeIndex index,
                              MaterialPreviewState state,
                              MaterialTaskContext context,
                              Consumer<List<Task>> generateCallback) {
        super(Component.translatable("gui.todolist.material_preview.title"));
        this.parent = parent;
        this.index = index;
        this.state = state;
        this.context = context;
        this.generateCallback = generateCallback;
    }

    /**
     * 初始化数量输入、底部按钮与树区域，并完成首次解析。
     */
    @Override
    protected void init() {
        panelWidth = Math.max(PANEL_MIN_WIDTH, Math.min(PANEL_MAX_WIDTH, width - 40));
        panelHeight = Math.max(PANEL_MIN_HEIGHT, Math.min(PANEL_MAX_HEIGHT, height - 60));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int x = panelLeft;
        int y = panelTop;
        int w = panelWidth;
        int h = panelHeight;

        listLeft = x + 8;
        listRight = x + w - 8;
        listTop = y + 64;
        listBottom = y + h - 50;

        countField = new EditBox(font, x + 40, y + 42, 56, 18,
                Component.translatable("gui.todolist.material_preview.count_label"));
        countField.setMaxLength(6);
        countField.setValue(String.valueOf(state.getTargetCount()));
        countField.setResponder(text -> {
            int parsed = parseCount(text);
            if (parsed > 0 && parsed != state.getTargetCount()) {
                state.setTargetCount(parsed);
                refreshPlan();
            }
        });
        addRenderableWidget(countField);

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(x + 10, y + h - 26, 80, 20).build());

        generateButton = Button.builder(Component.translatable("gui.todolist.material_preview.generate"),
                        button -> generateTasks())
                .bounds(x + w - 110, y + h - 26, 100, 20).build();
        addRenderableWidget(generateButton);

        refreshPlan();
    }

    /**
     * 重新解析材料计划、重算布局与持有量分配，并收敛滚动范围。
     *
     * <p>解析前先把玩家背包内容同步给状态层：配方中的多候选材料（物品标签等）
     * 默认优先选中玩家已经拥有的那种。
     */
    private void refreshPlan() {
        heldItemCounts = heldItemCountsSource.get();
        state.setPreferredItems(heldItemCounts.keySet());
        plan = state.resolve(index);
        layout = MaterialPreviewLayout.compute(plan);
        allocatedHeldByNode = allocateHeldCounts(layout.itemCells(), heldItemCounts);
        scrollX = clamp(scrollX, 0, maxScrollX());
        scrollY = clamp(scrollY, 0, maxScrollY());
        closeCandidatePopup();
        if (generateButton != null) {
            generateButton.active = state.getTargetItemId() != null && !state.getTargetItemId().isEmpty();
        }
    }

    /**
     * 统计玩家背包（含快捷栏与副手）中已有物品的数量。
     *
     * <p>只统计槽位里的物品本身，**不展开潜影盒等容器内容**：预览阶段的口径是"手上能直接用的材料"，
     * 与「收集」触发器（会展开原版标准容器）不同，避免把装起来的材料也算成可直接使用。
     *
     * @return 物品资源 ID → 数量；读不到玩家或背包时返回空 Map
     */
    private Map<String, Integer> collectHeldItemCounts() {
        if (minecraft == null || minecraft.player == null || minecraft.player.getInventory() == null) {
            return Map.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return countHeldItems(stacks);
    }

    /**
     * 统计给定物品堆集合中每个物品的数量。
     *
     * @param stacks 物品堆集合
     * @return 物品资源 ID → 数量
     */
    static Map<String, Integer> countHeldItems(Iterable<ItemStack> stacks) {
        Map<String, Integer> counts = new HashMap<>();
        if (stacks == null) {
            return counts;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (itemId == null || itemId.isEmpty()) {
                continue;
            }
            counts.merge(itemId, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    /**
     * 判断背包持有量是否已满足需求。
     *
     * @param heldCount     持有量
     * @param requiredCount 需求量
     * @return 达标时返回 true
     */
    static boolean isRequirementSatisfied(int heldCount, int requiredCount) {
        return requiredCount > 0 && heldCount >= requiredCount;
    }

    /**
     * 按显示顺序把背包持有量分配给每个物品节点：同种物品在靠上的层级先占用，
     * 靠下的层级只能用剩下的数量，避免同一批材料被多层重复计入。
     *
     * @param itemCells 按（列, 行）排序的物品格子
     * @param heldCounts 背包持有量
     * @return 节点 → 该节点可用的持有量
     */
    private static Map<MaterialNode, Integer> allocateHeldCounts(List<MaterialPreviewLayout.ItemCell> itemCells,
                                                                 Map<String, Integer> heldCounts) {
        Map<MaterialNode, Integer> allocated = new IdentityHashMap<>();
        if (itemCells == null || itemCells.isEmpty()) {
            return allocated;
        }
        Map<String, Integer> remaining = new HashMap<>(heldCounts == null ? Map.of() : heldCounts);
        for (MaterialPreviewLayout.ItemCell cell : itemCells) {
            MaterialNode node = cell.node();
            allocated.put(node, consumeHeld(remaining, node.itemId(), node.requiredCount()));
        }
        return allocated;
    }

    /**
     * 从剩余持有量中取走某材料的可用数量。
     *
     * @param remaining     剩余持有量（就地修改）
     * @param itemId        物品资源 ID
     * @param requiredCount 需求量
     * @return 本次可用的数量
     */
    private static int consumeHeld(Map<String, Integer> remaining, String itemId, int requiredCount) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        Integer held = remaining.get(itemId);
        if (held == null || held <= 0) {
            return 0;
        }
        int usable = Math.min(held, Math.max(0, requiredCount));
        int rest = held - usable;
        if (rest <= 0) {
            remaining.remove(itemId);
        } else {
            remaining.put(itemId, rest);
        }
        return usable;
    }

    /**
     * 把数值收敛到 [min, max] 区间。
     *
     * @param value 数值
     * @param min   下限
     * @param max   上限
     * @return 收敛后的数值
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 解析数量输入。
     *
     * @param text 输入文本
     * @return 合法数量；非法时返回 -1
     */
    private static int parseCount(String text) {
        if (text == null || text.trim().isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /**
     * 生成任务并回调给调用方，随后关闭界面。
     */
    private void generateTasks() {
        if (index == null) {
            return;
        }
        List<Task> tasks = state.generate(index, context);
        if (tasks.isEmpty()) {
            return;
        }
        if (generateCallback != null) {
            generateCallback.accept(tasks);
        }
        onClose();
    }

    /**
     * 返回树区域可横向滚动的最大值。
     *
     * @return 最大横向偏移（像素）
     */
    private int maxScrollX() {
        return Math.max(0, layout.width() - (listRight - listLeft));
    }

    /**
     * 返回树区域可纵向滚动的最大值。
     *
     * @return 最大纵向偏移（像素）
     */
    private int maxScrollY() {
        return Math.max(0, layout.height() - (listBottom - listTop));
    }

    /**
     * 返回物品格子在屏幕上的图标中心 X。
     *
     * @param cell 物品格子
     * @return 屏幕 X
     */
    private int itemIconCenterX(MaterialPreviewLayout.ItemCell cell) {
        return listLeft - scrollX + cell.iconX() + MaterialPreviewLayout.ICON_SIZE / 2;
    }

    /**
     * 返回物品格子在屏幕上的图标中心 Y。
     *
     * @param cell 物品格子
     * @return 屏幕 Y
     */
    private int itemIconCenterY(MaterialPreviewLayout.ItemCell cell) {
        return listTop - scrollY + cell.iconY() + MaterialPreviewLayout.ICON_SIZE / 2;
    }

    /**
     * 返回功能方块格子在屏幕上的图标中心 X。
     *
     * @param cell 功能方块格子
     * @return 屏幕 X
     */
    private int stationIconCenterX(MaterialPreviewLayout.StationCell cell) {
        return listLeft - scrollX + cell.iconX() + MaterialPreviewLayout.ICON_SIZE / 2;
    }

    /**
     * 返回功能方块格子在屏幕上的图标中心 Y。
     *
     * @param cell 功能方块格子
     * @return 屏幕 Y
     */
    private int stationIconCenterY(MaterialPreviewLayout.StationCell cell) {
        return listTop - scrollY + cell.iconY() + MaterialPreviewLayout.ICON_SIZE / 2;
    }

    /**
     * 命中测试：返回鼠标所在的物品格子。
     *
     * @param mouseX    鼠标 X
     * @param mouseY    鼠标 Y
     * @param iconOnly  为 true 时只命中图标区域
     * @return 命中的物品格子；未命中时返回 null
     */
    private MaterialPreviewLayout.ItemCell findItemCell(double mouseX, double mouseY, boolean iconOnly) {
        int localX = (int) mouseX - (listLeft - scrollX);
        int localY = (int) mouseY - (listTop - scrollY);
        for (MaterialPreviewLayout.ItemCell cell : layout.itemCells()) {
            boolean hit = iconOnly ? cell.containsIcon(localX, localY) : cell.contains(localX, localY);
            if (hit) {
                return cell;
            }
        }
        return null;
    }

    /**
     * 命中测试：返回鼠标所在的功能方块格子。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 命中的功能方块格子；未命中时返回 null
     */
    private MaterialPreviewLayout.StationCell findStationCell(double mouseX, double mouseY) {
        int localX = (int) mouseX - (listLeft - scrollX);
        int localY = (int) mouseY - (listTop - scrollY);
        for (MaterialPreviewLayout.StationCell cell : layout.stationCells()) {
            if (cell.containsIcon(localX, localY)) {
                return cell;
            }
        }
        return null;
    }

    /**
     * 判断点是否落在树区域内。
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 落在树区域时返回 true
     */
    private boolean insideTree(double mouseX, double mouseY) {
        return mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom;
    }

    /**
     * 打开候选材料弹出列表（点物品图标时调用）。
     *
     * @param cell 物品格子
     */
    private void openCandidatePopup(MaterialPreviewLayout.ItemCell cell) {
        List<String> candidates = cell.node().candidates();
        if (candidates == null || candidates.size() <= 1 || minecraft == null) {
            return;
        }
        String candidateKey = candidates.get(0);
        String selected = state.getCandidateOverrides().get(candidateKey);
        int rowCount = Math.min(candidates.size(), POPUP_MAX_ROWS);
        int popupWidth = POPUP_MIN_WIDTH;
        for (String candidate : candidates) {
            popupWidth = Math.max(popupWidth, font.width(resolveItemName(candidate)) + 34);
        }
        popupWidth = Math.min(popupWidth, POPUP_MAX_WIDTH);
        int popupHeight = rowCount * POPUP_ROW_HEIGHT + 4;
        int popupLeft = clamp(itemIconCenterX(cell) + 12, panelLeft + 4,
                Math.max(panelLeft + 4, panelLeft + panelWidth - popupWidth - 4));
        int popupTop = clamp(itemIconCenterY(cell) - popupHeight / 2, panelTop + 4,
                Math.max(panelTop + 4, panelTop + panelHeight - popupHeight - 4));
        candidatePopup = new CandidatePopup(candidateKey, List.copyOf(candidates), selected,
                popupLeft, popupTop, popupWidth, popupHeight);
    }

    /**
     * 关闭候选材料弹出列表。
     */
    private void closeCandidatePopup() {
        candidatePopup = null;
    }

    /**
     * 点击功能方块节点：该物品有多条配方时切到下一条并重新解析。
     *
     * @param cell 功能方块格子
     */
    private void cycleRecipeFor(MaterialPreviewLayout.StationCell cell) {
        MaterialNode owner = cell.owner();
        if (index == null || owner == null) {
            return;
        }
        List<MaterialRecipe> candidates = index.findByOutput(owner.itemId());
        if (candidates == null || candidates.size() <= 1) {
            return;
        }
        state.cycleRecipe(owner.itemId(), candidates);
        refreshPlan();
    }

    /**
     * 切换某个物品节点的展开 / 收起（点节点其余区域时调用）。
     *
     * @param node 物品节点
     */
    private void toggleExpandFor(MaterialNode node) {
        if (state.toggleExpand(node)) {
            refreshPlan();
        }
    }

    /**
     * 关闭并返回父界面。
     */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /**
     * Esc 关闭（弹出列表打开时先关列表），回车在数量框内确认数量；同时维护 Shift 状态。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftHeld = true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (candidatePopup != null) {
                closeCandidatePopup();
                return true;
            }
            onClose();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && countField != null && countField.isFocused()) {
            int parsed = parseCount(countField.getValue());
            if (parsed > 0) {
                state.setTargetCount(parsed);
                refreshPlan();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 松开 Shift 时恢复「滚轮纵向滚动」。
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            shiftHeld = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * 滚轮：默认纵向滚动，按住 Shift 时横向滚动；弹出列表优先消费。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (candidatePopup != null && candidatePopup.contains(mouseX, mouseY)) {
            candidatePopup.scroll = clamp(candidatePopup.scroll + (delta < 0 ? 1 : -1),
                    0, candidatePopup.maxScroll());
            return true;
        }
        if (insideTree(mouseX, mouseY)) {
            int step = delta < 0 ? 1 : -1;
            if (shiftHeld) {
                scrollX = clamp(scrollX + step * SCROLL_STEP_X, 0, maxScrollX());
            } else {
                scrollY = clamp(scrollY + step * SCROLL_STEP_Y, 0, maxScrollY());
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * 左键点击：先处理弹出列表，再按「物品图标 → 功能方块图标 → 节点其余区域」分派，
     * 空白处按下则进入拖拽平移。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (candidatePopup != null) {
            if (candidatePopup.contains(mouseX, mouseY)) {
                int row = candidatePopup.rowAt(mouseY);
                if (row >= 0) {
                    String chosen = candidatePopup.candidates.get(row);
                    state.chooseCandidate(candidatePopup.candidateKey, chosen);
                    refreshPlan();
                }
                return true;
            }
            closeCandidatePopup();
            return true;
        }
        if (!insideTree(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        MaterialPreviewLayout.ItemCell iconCell = findItemCell(mouseX, mouseY, true);
        if (iconCell != null) {
            if (iconCell.node().candidates().size() > 1) {
                openCandidatePopup(iconCell);
            } else {
                // 没有可选材料时，点图标与点节点其余区域同义：展开 / 收起下级配方
                toggleExpandFor(iconCell.node());
            }
            return true;
        }
        MaterialPreviewLayout.StationCell stationCell = findStationCell(mouseX, mouseY);
        if (stationCell != null) {
            cycleRecipeFor(stationCell);
            return true;
        }
        MaterialPreviewLayout.ItemCell cell = findItemCell(mouseX, mouseY, false);
        if (cell != null) {
            toggleExpandFor(cell.node());
            return true;
        }
        dragging = true;
        dragStartX = mouseX;
        dragStartY = mouseY;
        dragStartScrollX = scrollX;
        dragStartScrollY = scrollY;
        return true;
    }

    /**
     * 拖拽平移树区域。
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            scrollX = clamp(dragStartScrollX - (int) (mouseX - dragStartX), 0, maxScrollX());
            scrollY = clamp(dragStartScrollY - (int) (mouseY - dragStartY), 0, maxScrollY());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * 结束拖拽平移。
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 渲染面板、横向配方树、悬浮提示与候选材料弹出列表。
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int x = panelLeft;
        int y = panelTop;
        int w = panelWidth;
        int h = panelHeight;
        context.fill(x, y, x + w, y + h, 0xE6161616);
        context.renderOutline(x, y, w, h, 0xFF606060);

        context.drawString(font, title, x + 10, y + 8, 0xFFE0B240, false);
        renderTargetRow(context, x, y, w);
        context.drawString(font, Component.translatable("gui.todolist.material_preview.count_label"),
                x + 12, y + 46, 0xFFAAAAAA, false);

        // 先定下本帧浮层（可选材料列表优先，其次悬浮提示）：
        // 树渲染时要跳过被它压住的图标（物品图标走独立渲染批次，压不住）
        if (candidatePopup != null) {
            hoverTipLines = List.of();
            overlayRect = new OverlayRect(candidatePopup.left, candidatePopup.top,
                    candidatePopup.width, candidatePopup.height);
        } else {
            overlayRect = computeHoverTip(mouseX, mouseY);
        }

        renderTree(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        renderSummary(context, x, y, w, h);
        // 先把树上已提交的绘制刷出去，再画浮层，尽量让浮层落在最上层
        context.flush();
        renderHoverTip(context);
        renderCandidatePopup(context, mouseX, mouseY);
    }

    /**
     * 渲染目标物品行（图标 + 名称 + 需求数量 + 持有量标注）。
     *
     * @param context 绘制上下文
     * @param x       面板左上角 x
     * @param y       面板左上角 y
     * @param w       面板宽度
     */
    private void renderTargetRow(GuiGraphics context, int x, int y, int w) {
        String itemId = state.getTargetItemId();
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        ItemStack icon = TriggerTargetSupport.resolveIconStack(TaskTrigger.Type.ITEM_COLLECT, itemId);
        if (icon != null && !icon.isEmpty()) {
            context.renderItem(icon, x + w - 26, y + 5);
        }
        String name = resolveItemName(itemId);
        int requiredCount = state.getTargetCount();
        int heldCount = targetHeldCount();
        String countText = " ×" + requiredCount;
        String heldSuffix = buildHeldSuffix(heldCount, requiredCount);
        int textWidth = font.width(name) + font.width(countText) + font.width(heldSuffix);
        int maxWidth = w - 46;
        if (textWidth > maxWidth) {
            return;
        }
        int textX = x + w - 30 - textWidth;
        int countX = textX + font.width(name);
        context.drawString(font, name, textX, y + 9, 0xFFFFFFFF, false);
        context.drawString(font, countText, countX, y + 9, resolveCountColor(heldCount, requiredCount), false);
        if (!heldSuffix.isEmpty()) {
            context.drawString(font, heldSuffix, countX + font.width(countText), y + 9,
                    resolveSuffixColor(heldCount, requiredCount), false);
        }
    }

    /**
     * 渲染横向配方树：树形连线 → 功能方块节点 → 物品节点，并裁剪在树区域内。
     *
     * @param context 绘制上下文
     * @param mouseX  鼠标 x
     * @param mouseY  鼠标 y
     */
    private void renderTree(GuiGraphics context, int mouseX, int mouseY) {
        context.fill(listLeft, listTop, listRight, listBottom, 0x30000000);
        context.enableScissor(listLeft, listTop, listRight, listBottom);
        int originX = listLeft - scrollX;
        int originY = listTop - scrollY;

        renderConnectors(context, originX, originY);
        for (MaterialPreviewLayout.StationCell cell : layout.stationCells()) {
            renderStationCell(context, cell, originX, originY, mouseX, mouseY);
        }
        for (MaterialPreviewLayout.ItemCell cell : layout.itemCells()) {
            renderItemCell(context, cell, originX, originY, mouseX, mouseY);
        }
        context.disableScissor();
    }

    /**
     * 判断某个图标是否被本帧浮层压住（被压住时不绘制，避免它浮在浮层之上）。
     *
     * @param iconX 图标左上角 X（屏幕坐标）
     * @param iconY 图标左上角 Y（屏幕坐标）
     * @return 被浮层覆盖时返回 true
     */
    private boolean isIconCovered(int iconX, int iconY) {
        return overlayRect != null
                && overlayRect.intersects(iconX, iconY, MaterialPreviewLayout.ICON_SIZE, MaterialPreviewLayout.ICON_SIZE);
    }

    /**
     * 渲染树形连线：物品 → 功能方块，功能方块 → 各下级材料（含竖向汇流线）。
     *
     * @param context 绘制上下文
     * @param originX 内容原点屏幕 X
     * @param originY 内容原点屏幕 Y
     */
    private void renderConnectors(GuiGraphics context, int originX, int originY) {
        for (MaterialPreviewLayout.ItemCell cell : layout.itemCells()) {
            MaterialNode node = cell.node();
            MaterialPreviewLayout.StationCell station = layout.stationOf(node);
            if (station == null) {
                continue;
            }
            int itemCenterY = originY + cell.y() + MaterialPreviewLayout.CELL_SIZE / 2;
            int itemRight = originX + cell.x() + MaterialPreviewLayout.CELL_SIZE;
            int stationLeft = originX + station.x();
            int stationRight = stationLeft + MaterialPreviewLayout.CELL_SIZE;
            int stationCenterY = originY + station.y() + MaterialPreviewLayout.CELL_SIZE / 2;
            context.fill(itemRight, itemCenterY, stationLeft, itemCenterY + 1, CONNECTOR_COLOR);

            List<MaterialNode> children = node.children();
            if (children.isEmpty()) {
                // 收起状态：功能方块后画一小段虚线，提示"这里还能继续展开"
                for (int offset = 0; offset < 12; offset += 4) {
                    context.fill(stationRight + offset, stationCenterY,
                            stationRight + Math.min(offset + 2, 12), stationCenterY + 1, CONNECTOR_COLOR);
                }
                continue;
            }
            int firstChildX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (MaterialNode child : children) {
                MaterialPreviewLayout.ItemCell childCell = layout.cellOf(child);
                if (childCell == null) {
                    continue;
                }
                firstChildX = Math.min(firstChildX, originX + childCell.x());
                int childCenterY = originY + childCell.y() + MaterialPreviewLayout.CELL_SIZE / 2;
                minY = Math.min(minY, childCenterY);
                maxY = Math.max(maxY, childCenterY);
            }
            if (firstChildX == Integer.MAX_VALUE) {
                continue;
            }
            int trunkX = (stationRight + firstChildX) / 2;
            context.fill(stationRight, stationCenterY, trunkX, stationCenterY + 1, CONNECTOR_COLOR);
            if (minY != maxY) {
                context.fill(trunkX, minY, trunkX + 1, maxY + 1, CONNECTOR_COLOR);
            }
            for (MaterialNode child : children) {
                MaterialPreviewLayout.ItemCell childCell = layout.cellOf(child);
                if (childCell == null) {
                    continue;
                }
                int childCenterY = originY + childCell.y() + MaterialPreviewLayout.CELL_SIZE / 2;
                context.fill(trunkX, childCenterY, originX + childCell.x(), childCenterY + 1, CONNECTOR_COLOR);
            }
        }
    }

    /**
     * 渲染功能方块节点：图标 + 悬停高亮。
     *
     * @param context 绘制上下文
     * @param cell    功能方块格子
     * @param originX 内容原点屏幕 X
     * @param originY 内容原点屏幕 Y
     * @param mouseX  鼠标 x
     * @param mouseY  鼠标 y
     */
    private void renderStationCell(GuiGraphics context,
                                   MaterialPreviewLayout.StationCell cell,
                                   int originX,
                                   int originY,
                                   int mouseX,
                                   int mouseY) {
        boolean hovered = cell.containsIcon((int) mouseX - originX, (int) mouseY - originY);
        boolean switchable = hasRecipeAlternatives(cell.owner());
        int cellX = originX + cell.x();
        int cellY = originY + cell.y();
        int right = cellX + MaterialPreviewLayout.CELL_SIZE;
        int bottom = cellY + MaterialPreviewLayout.CELL_SIZE;
        // 功能方块节点统一画成「有底 + 有框」的方块格，与只画图标的物品节点一眼可分
        context.fill(cellX, cellY, right, bottom,
                hovered && switchable ? ICON_HOVER_COLOR : STATION_BACKGROUND_COLOR);
        context.renderOutline(cellX, cellY, MaterialPreviewLayout.CELL_SIZE, MaterialPreviewLayout.CELL_SIZE,
                switchable ? STATION_BORDER_ACTIVE_COLOR : STATION_BORDER_COLOR);
        if (switchable) {
            context.fill(right - 5, cellY + 1, right - 1, cellY + 5, STATION_MARKER_COLOR);
        }
        int iconX = originX + cell.iconX();
        int iconY = originY + cell.iconY();
        if (isIconCovered(iconX, iconY)) {
            // 图标落在浮层下：不画图标（方块格与边框走普通批次，仍会被浮层正常盖住）
            return;
        }
        ItemStack icon = TriggerTargetSupport.resolveIconStack(TaskTrigger.Type.ITEM_COLLECT, cell.stationItemId());
        if (icon != null && !icon.isEmpty()) {
            context.renderItem(icon, iconX, iconY);
        }
    }

    /**
     * 渲染物品节点：图标 + 名称 + 数量（含持有量标注），并按状态加悬停高亮。
     *
     * @param context 绘制上下文
     * @param cell    物品格子
     * @param originX 内容原点屏幕 X
     * @param originY 内容原点屏幕 Y
     * @param mouseX  鼠标 x
     * @param mouseY  鼠标 y
     */
    private void renderItemCell(GuiGraphics context,
                                MaterialPreviewLayout.ItemCell cell,
                                int originX,
                                int originY,
                                int mouseX,
                                int mouseY) {
        MaterialNode node = cell.node();
        int localX = (int) mouseX - originX;
        int localY = (int) mouseY - originY;
        boolean iconHovered = cell.containsIcon(localX, localY);
        boolean bodyHovered = cell.contains(localX, localY);
        int cellX = originX + cell.x();
        int cellY = originY + cell.y();
        if (iconHovered && node.candidates().size() > 1) {
            context.fill(cellX, cellY, cellX + MaterialPreviewLayout.CELL_SIZE,
                    cellY + MaterialPreviewLayout.CELL_SIZE, ICON_HOVER_COLOR);
        } else if (bodyHovered) {
            context.fill(cellX - 2, cellY - 2, cellX + MaterialPreviewLayout.CELL_SIZE + 2,
                    cellY + MaterialPreviewLayout.CELL_SIZE + MaterialPreviewLayout.LABEL_HEIGHT,
                    HOVER_COLOR);
        }
        int iconX = originX + cell.iconX();
        int iconY = originY + cell.iconY();
        if (!isIconCovered(iconX, iconY)) {
            // 图标落在浮层下：不画图标；名称与数量走普通批次，会被浮层正常盖住
            ItemStack icon = TriggerTargetSupport.resolveIconStack(TaskTrigger.Type.ITEM_COLLECT, node.itemId());
            if (icon != null && !icon.isEmpty()) {
                context.renderItem(icon, iconX, iconY);
            }
        }

        String name = font.plainSubstrByWidth(resolveItemName(node.itemId()), MaterialPreviewLayout.LABEL_MAX_WIDTH);
        int nameX = cellX + (MaterialPreviewLayout.CELL_SIZE - font.width(name)) / 2;
        context.drawString(font, name, nameX, cellY + MaterialPreviewLayout.CELL_SIZE, 0xFFFFFFFF, false);

        int heldCount = allocatedHeldCount(node);
        String countText = "×" + node.requiredCount();
        String heldSuffix = buildHeldSuffix(heldCount, node.requiredCount());
        String countLine = countText + heldSuffix;
        int countX = cellX + (MaterialPreviewLayout.CELL_SIZE - font.width(countLine)) / 2;
        context.drawString(font, countText, countX,
                cellY + MaterialPreviewLayout.CELL_SIZE + font.lineHeight,
                resolveCountColor(heldCount, node.requiredCount()), false);
        if (!heldSuffix.isEmpty()) {
            context.drawString(font, heldSuffix, countX + font.width(countText),
                    cellY + MaterialPreviewLayout.CELL_SIZE + font.lineHeight,
                    resolveSuffixColor(heldCount, node.requiredCount()), false);
        }
    }

    /**
     * 渲染底部统计摘要（材料条数与截断提示）。
     *
     * @param context 绘制上下文
     * @param x       面板左上角 x
     * @param y       面板左上角 y
     * @param w       面板宽度
     * @param h       面板高度
     */
    private void renderSummary(GuiGraphics context, int x, int y, int w, int h) {
        String summary;
        if (plan == null || plan.isEmpty()) {
            summary = Component.translatable("gui.todolist.material_preview.empty").getString();
        } else {
            summary = Component.translatable("gui.todolist.material_preview.summary",
                    plan.leafTotals().size(), plan.leafTotals().size()).getString();
            if (plan.truncated()) {
                summary = summary + "  " + Component.translatable("gui.todolist.material_preview.truncated").getString();
            }
            summary = summary + "  " + Component.translatable("gui.todolist.material_preview.hint").getString();
        }
        context.drawString(font, summary, x + 10, y + h - 42, 0xFFAAAAAA, false);
    }

    /**
     * 计算本帧悬浮提示的文本行与板面（只算不画）。
     *
     * <p>必须在绘制树之前算出板面：树上的物品图标要避开被浮层压住的位置。
     *
     * @param mouseX 鼠标 x
     * @param mouseY 鼠标 y
     * @return 提示板面；不显示提示时返回 null
     */
    private OverlayRect computeHoverTip(int mouseX, int mouseY) {
        hoverTipLines = List.of();
        if (candidatePopup != null || !insideTree(mouseX, mouseY)) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        MaterialPreviewLayout.StationCell stationCell = findStationCell(mouseX, mouseY);
        if (stationCell != null) {
            lines.add(resolveItemName(stationCell.stationItemId()));
            MaterialRecipeKind kind = stationCell.kind();
            lines.add(Component.translatable("gui.todolist.material_preview.kind."
                    + kind.name().toLowerCase(Locale.ROOT)).getString());
            if (hasRecipeAlternatives(stationCell.owner())) {
                lines.add(Component.translatable("gui.todolist.material_preview.tip.cycle_recipe").getString());
            }
        } else {
            MaterialPreviewLayout.ItemCell itemCell = findItemCell(mouseX, mouseY, false);
            if (itemCell == null) {
                return null;
            }
            MaterialNode node = itemCell.node();
            lines.add(resolveItemName(node.itemId()));
            lines.add(Component.translatable("gui.todolist.material_preview.tip.required",
                    node.requiredCount()).getString());
            int heldCount = allocatedHeldCount(node);
            lines.add(Component.translatable("gui.todolist.material_preview.tip.held",
                    heldCount, buildHeldSuffix(heldCount, node.requiredCount())).getString());
            if (node.candidates().size() > 1) {
                lines.add(Component.translatable("gui.todolist.material_preview.tip.choose_candidate",
                        node.candidates().size()).getString());
            }
            if (state.isExpandToggleAvailable(node)) {
                lines.add(Component.translatable(node.children().isEmpty()
                        ? "gui.todolist.material_preview.tip.expand"
                        : "gui.todolist.material_preview.tip.collapse").getString());
            } else if (node.isLeaf()) {
                // 不能再展开的节点说明原因：没有配方 / 循环依赖 / 达到上限
                String reasonKey = switch (node.stopReason()) {
                    case NO_RECIPE -> "tip.no_recipe";
                    case CYCLE -> "tip.cycle";
                    case MAX_DEPTH, NODE_LIMIT -> "tip.limit";
                    default -> null;
                };
                if (reasonKey != null) {
                    lines.add(Component.translatable("gui.todolist.material_preview." + reasonKey).getString());
                }
            }
        }
        hoverTipLines = List.copyOf(lines);
        return tooltipRect(hoverTipLines, mouseX, mouseY);
    }

    /**
     * 按文本行计算悬浮提示的板面：贴着鼠标，并收敛在屏幕内。
     *
     * @param lines  文本行
     * @param mouseX 鼠标 x
     * @param mouseY 鼠标 y
     * @return 板面
     */
    private OverlayRect tooltipRect(List<String> lines, int mouseX, int mouseY) {
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }
        int boxWidth = width + 8;
        int boxHeight = lines.size() * (font.lineHeight + 2) + 4;
        int boxLeft = clamp(mouseX + 10, 2, Math.max(2, this.width - boxWidth - 2));
        int boxTop = clamp(mouseY + 10, 2, Math.max(2, this.height - boxHeight - 2));
        return new OverlayRect(boxLeft, boxTop, boxWidth, boxHeight);
    }

    /**
     * 绘制本帧悬浮提示（板面已在 {@link #computeHoverTip} 算好）。
     *
     * @param context 绘制上下文
     */
    private void renderHoverTip(GuiGraphics context) {
        OverlayRect rect = overlayRect;
        if (rect == null || hoverTipLines.isEmpty() || candidatePopup != null) {
            return;
        }
        context.fill(rect.left(), rect.top(), rect.left() + rect.width(), rect.top() + rect.height(), 0xF0100010);
        context.renderOutline(rect.left(), rect.top(), rect.width(), rect.height(), 0xFF5050A0);
        int textY = rect.top() + 3;
        for (String line : hoverTipLines) {
            context.drawString(font, line, rect.left() + 4, textY, 0xFFFFFFFF, false);
            textY += font.lineHeight + 2;
        }
    }

    /**
     * 渲染候选材料弹出列表。
     *
     * @param context 绘制上下文
     * @param mouseX  鼠标 x
     * @param mouseY  鼠标 y
     */
    private void renderCandidatePopup(GuiGraphics context, int mouseX, int mouseY) {
        CandidatePopup popup = candidatePopup;
        if (popup == null) {
            return;
        }
        context.fill(popup.left, popup.top, popup.left + popup.width, popup.top + popup.height, 0xF0202020);
        context.renderOutline(popup.left, popup.top, popup.width, popup.height, 0xFF808080);
        int hoveredRow = popup.contains(mouseX, mouseY) ? popup.rowAt(mouseY) : -1;
        int end = Math.min(popup.candidates.size(), popup.scroll + POPUP_MAX_ROWS);
        int rowY = popup.top + 2;
        for (int row = popup.scroll; row < end; row++) {
            String itemId = popup.candidates.get(row);
            if (row == hoveredRow) {
                context.fill(popup.left + 1, rowY, popup.left + popup.width - 1, rowY + POPUP_ROW_HEIGHT, 0x4000A0FF);
            }
            ItemStack icon = TriggerTargetSupport.resolveIconStack(TaskTrigger.Type.ITEM_COLLECT, itemId);
            if (icon != null && !icon.isEmpty()) {
                context.renderItem(icon, popup.left + 3, rowY + 1);
            }
            String name = font.plainSubstrByWidth(resolveItemName(itemId), popup.width - 24);
            boolean selected = itemId != null && itemId.equals(popup.selectedItemId);
            context.drawString(font, name, popup.left + 22, rowY + 5,
                    selected ? MATERIAL_SUFFICIENT_COLOR : 0xFFFFFFFF, false);
            rowY += POPUP_ROW_HEIGHT;
        }
        if (popup.maxScroll() > 0) {
            int barHeight = Math.max(8, popup.height * POPUP_MAX_ROWS / Math.max(1, popup.candidates.size()));
            int barTop = popup.top + (popup.height - barHeight) * popup.scroll / Math.max(1, popup.maxScroll());
            context.fill(popup.left + popup.width - 3, barTop, popup.left + popup.width - 1, barTop + barHeight,
                    0xFF909090);
        }
    }

    /**
     * 判断某物品是否有可选的其他配方（决定功能方块节点是否可点击切换）。
     *
     * @param owner 物品节点
     * @return 有多条配方时返回 true
     */
    private boolean hasRecipeAlternatives(MaterialNode owner) {
        if (index == null || owner == null) {
            return false;
        }
        List<MaterialRecipe> recipes = index.findByOutput(owner.itemId());
        return recipes != null && recipes.size() > 1;
    }

    /**
     * 构建数量后的持有量标注。
     *
     * <p>不止用颜色区分，而且只给"还能做什么"的信息：已够用写「已够」，
     * 不够时直接写还差多少（「缺 N」），不必让玩家自己做减法。
     *
     * @param heldCount     该节点可用的背包持有量
     * @param requiredCount 需求量
     * @return 已够用时返回「已够」；不够时返回「缺 N」；需求量非法时返回空串
     */
    private String buildHeldSuffix(int heldCount, int requiredCount) {
        if (isRequirementSatisfied(heldCount, requiredCount)) {
            return Component.translatable("gui.todolist.material_preview.held_enough").getString();
        }
        if (requiredCount <= 0) {
            return "";
        }
        int missing = requiredCount - Math.max(0, heldCount);
        return Component.translatable("gui.todolist.material_preview.held_missing", missing).getString();
    }

    /**
     * 按「背包持有量是否达到需求」选择数量文本颜色：达到或超过时标绿。
     *
     * @param heldCount     该节点可用的背包持有量
     * @param requiredCount 需求量
     * @return 文本颜色
     */
    private static int resolveCountColor(int heldCount, int requiredCount) {
        return isRequirementSatisfied(heldCount, requiredCount)
                ? MATERIAL_SUFFICIENT_COLOR
                : MATERIAL_COUNT_COLOR;
    }

    /**
     * 选择持有量标注文本的颜色：已够用时与数量同色（绿），未够用时用灰色弱化。
     *
     * @param heldCount     该节点可用的背包持有量
     * @param requiredCount 需求量
     * @return 文本颜色
     */
    private static int resolveSuffixColor(int heldCount, int requiredCount) {
        return isRequirementSatisfied(heldCount, requiredCount)
                ? MATERIAL_SUFFICIENT_COLOR
                : MATERIAL_HELD_COLOR;
    }

    /**
     * 读取分配给某节点的背包持有量。
     *
     * @param node 物品节点
     * @return 该节点可用的持有量
     */
    private int allocatedHeldCount(MaterialNode node) {
        Integer allocated = allocatedHeldByNode.get(node);
        return allocated == null ? 0 : allocated;
    }

    /**
     * 读取目标物品可用的背包持有量。
     *
     * @return 目标物品可用的持有量
     */
    private int targetHeldCount() {
        MaterialPreviewLayout.ItemCell rootCell = plan == null ? null : layout.cellOf(plan.root());
        return rootCell == null ? 0 : allocatedHeldCount(rootCell.node());
    }

    /**
     * 解析物品本地化名称，失败时回退为资源 ID。
     *
     * @param itemId 物品资源 ID
     * @return 显示名
     */
    private String resolveItemName(String itemId) {
        String name = TriggerTargetSupport.resolveTargetDisplayName(TaskTrigger.Type.ITEM_COLLECT, itemId);
        return name == null || name.isEmpty() ? itemId : name;
    }

    /**
     * 返回横向配方树的布局，供同包测试断言。
     *
     * @return 布局
     */
    MaterialPreviewLayout getLayoutForTest() {
        return layout;
    }

    /**
     * 返回物品节点快照（按布局顺序），供同包测试断言树形、数量与持有量分配。
     *
     * @return 形如 {@code "0|minecraft:iron_block:3:2"} 的列表（层级|物品:数量:可用持有量）
     */
    List<String> getItemSnapshotsForTest() {
        List<String> snapshots = new ArrayList<>();
        for (MaterialPreviewLayout.ItemCell cell : layout.itemCells()) {
            MaterialNode node = cell.node();
            snapshots.add(cell.depth() + "|" + node.itemId() + ":" + node.requiredCount()
                    + ":" + allocatedHeldCount(node));
        }
        return snapshots;
    }

    /**
     * 返回功能方块节点快照（按布局顺序），供同包测试断言配方来源。
     *
     * @return 形如 {@code "minecraft:iron_block|minecraft:crafting_table"} 的列表（产物|功能方块）
     */
    List<String> getStationSnapshotsForTest() {
        List<String> snapshots = new ArrayList<>();
        for (MaterialPreviewLayout.StationCell cell : layout.stationCells()) {
            snapshots.add(cell.owner().itemId() + "|" + cell.stationItemId());
        }
        return snapshots;
    }

    /**
     * 返回预览状态，供同包测试断言。
     *
     * @return 预览状态
     */
    MaterialPreviewState getPreviewStateForTest() {
        return state;
    }

    /**
     * 返回当前材料计划，供同包测试断言。
     *
     * @return 材料计划
     */
    MaterialPlan getPlanForTest() {
        return plan;
    }

    /**
     * 返回数量输入框，供同包测试写入。
     *
     * @return 数量输入框
     */
    EditBox getCountFieldForTest() {
        return countField;
    }

    /**
     * 返回生成按钮，供同包测试触发。
     *
     * @return 生成按钮
     */
    Button getGenerateButtonForTest() {
        return generateButton;
    }

    /**
     * 在测试中点击某物品节点的图标（触发候选材料弹出列表）。
     *
     * @param itemId 物品资源 ID
     * @return 是否命中并处理
     */
    boolean clickItemIconForTest(String itemId) {
        MaterialPreviewLayout.ItemCell cell = itemCellForTest(itemId);
        if (cell == null) {
            return false;
        }
        return mouseClicked(itemIconCenterX(cell), itemIconCenterY(cell), 0);
    }

    /**
     * 在测试中点击某物品节点的其余区域（触发展开 / 收起）。
     *
     * @param itemId 物品资源 ID
     * @return 是否命中并处理
     */
    boolean clickItemBodyForTest(String itemId) {
        MaterialPreviewLayout.ItemCell cell = itemCellForTest(itemId);
        if (cell == null) {
            return false;
        }
        return mouseClicked(itemIconCenterX(cell),
                listTop - scrollY + cell.y() + MaterialPreviewLayout.CELL_SIZE + 5, 0);
    }

    /**
     * 在测试中点击某物品对应的功能方块节点图标（触发切换配方）。
     *
     * @param itemId 物品资源 ID
     * @return 是否命中并处理
     */
    boolean clickStationIconForTest(String itemId) {
        MaterialPreviewLayout.ItemCell itemCell = itemCellForTest(itemId);
        if (itemCell == null) {
            return false;
        }
        MaterialPreviewLayout.StationCell station = layout.stationOf(itemCell.node());
        if (station == null) {
            return false;
        }
        return mouseClicked(stationIconCenterX(station), stationIconCenterY(station), 0);
    }

    /**
     * 返回候选材料弹出列表是否已打开。
     *
     * @return 已打开时返回 true
     */
    boolean isCandidatePopupOpenForTest() {
        return candidatePopup != null;
    }

    /**
     * 返回候选材料弹出列表中的条目（物品资源 ID）。
     *
     * @return 条目列表；未打开时返回空列表
     */
    List<String> getCandidatePopupEntriesForTest() {
        return candidatePopup == null ? List.of() : candidatePopup.candidates;
    }

    /**
     * 在测试中点击候选材料弹出列表里的某条目。
     *
     * @param itemId 物品资源 ID
     * @return 是否命中并处理
     */
    boolean clickCandidateEntryForTest(String itemId) {
        CandidatePopup popup = candidatePopup;
        if (popup == null || itemId == null) {
            return false;
        }
        int row = popup.candidates.indexOf(itemId);
        if (row < 0 || row < popup.scroll || row >= popup.scroll + POPUP_MAX_ROWS) {
            return false;
        }
        double pointY = popup.top + 2 + (row - popup.scroll) * POPUP_ROW_HEIGHT + 1;
        return mouseClicked(popup.left + 4, pointY, 0);
    }

    /**
     * 覆盖背包持有量来源，供同包测试注入固定数据。
     *
     * @param counts 物品资源 ID → 持有量；为 null 时清空
     */
    void setHeldItemCountsForTest(Map<String, Integer> counts) {
        Map<String, Integer> snapshot = counts == null ? Map.of() : Map.copyOf(counts);
        this.heldItemCountsSource = () -> snapshot;
        if (countField != null) {
            refreshPlan();
        }
    }

    /**
     * 返回某物品节点分到的背包持有量，供同包测试断言跨层级分配。
     *
     * @param itemId 物品资源 ID
     * @return 分到的持有量；节点不存在时返回 0
     */
    int getItemHeldCountForTest(String itemId) {
        MaterialPreviewLayout.ItemCell cell = itemCellForTest(itemId);
        return cell == null ? 0 : allocatedHeldCount(cell.node());
    }

    /**
     * 返回某物品节点数量文本的颜色，供同包测试断言"已够"标绿。
     *
     * @param itemId 物品资源 ID
     * @return 文本颜色；节点不存在时返回未达标颜色
     */
    int getItemCountColorForTest(String itemId) {
        MaterialPreviewLayout.ItemCell cell = itemCellForTest(itemId);
        if (cell == null) {
            return MATERIAL_COUNT_COLOR;
        }
        int held = allocatedHeldCount(cell.node());
        return resolveCountColor(held, cell.node().requiredCount());
    }

    /**
     * 返回某物品节点数量后的持有量标注，供同包测试断言「已够 / 缺 N」。
     *
     * @param itemId 物品资源 ID
     * @return 标注文本；节点不存在时返回空串
     */
    String getItemHeldSuffixForTest(String itemId) {
        MaterialPreviewLayout.ItemCell cell = itemCellForTest(itemId);
        if (cell == null) {
            return "";
        }
        return buildHeldSuffix(allocatedHeldCount(cell.node()), cell.node().requiredCount());
    }

    /**
     * 按布局顺序返回第 index 个物品节点数量文本的颜色，供同包测试断言逐节点标绿。
     *
     * @param index 布局顺序下标
     * @return 文本颜色；越界时返回未达标颜色
     */
    int getItemCountColorAtForTest(int index) {
        List<MaterialPreviewLayout.ItemCell> cells = layout.itemCells();
        if (index < 0 || index >= cells.size()) {
            return MATERIAL_COUNT_COLOR;
        }
        MaterialNode node = cells.get(index).node();
        return resolveCountColor(allocatedHeldCount(node), node.requiredCount());
    }

    /**
     * 返回目标物品数量文本的颜色，供同包测试断言。
     *
     * @return 文本颜色
     */
    int getTargetCountColorForTest() {
        return resolveCountColor(targetHeldCount(), state.getTargetCount());
    }

    /**
     * 返回目标物品数量后的持有量标注，供同包测试断言。
     *
     * @return 标注文本
     */
    String getTargetHeldSuffixForTest() {
        return buildHeldSuffix(targetHeldCount(), state.getTargetCount());
    }

    /**
     * 返回树区域的横向滚动偏移，供同包测试断言。
     *
     * @return 横向偏移（像素）
     */
    int getScrollXForTest() {
        return scrollX;
    }

    /**
     * 返回树区域的纵向滚动偏移，供同包测试断言。
     *
     * @return 纵向偏移（像素）
     */
    int getScrollYForTest() {
        return scrollY;
    }

    /**
     * 返回"已够"标绿用的颜色，供同包测试比较。
     *
     * @return 颜色值
     */
    static int sufficientColorForTest() {
        return MATERIAL_SUFFICIENT_COLOR;
    }

    /**
     * 按物品资源 ID 查找布局里的物品格子。
     *
     * @param itemId 物品资源 ID
     * @return 首个匹配的格子；不存在时返回 null
     */
    private MaterialPreviewLayout.ItemCell itemCellForTest(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (MaterialPreviewLayout.ItemCell cell : layout.itemCells()) {
            if (itemId.equals(cell.node().itemId())) {
                return cell;
            }
        }
        return null;
    }
}
