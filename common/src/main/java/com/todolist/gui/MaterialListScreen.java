package com.todolist.gui;

import com.todolist.client.TriggerTargetSupport;
import com.todolist.material.MaterialNode;
import com.todolist.material.MaterialPlan;
import com.todolist.material.MaterialPreviewState;
import com.todolist.material.MaterialRecipe;
import com.todolist.material.MaterialRecipeIndex;
import com.todolist.material.MaterialRecipeKind;
import com.todolist.material.MaterialStopReason;
import com.todolist.material.MaterialTaskContext;
import com.todolist.material.MaterialTaskMode;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 材料反推预览界面。
 *
 * <p>展示目标物品的展开树，并允许在生成任务前调整：目标数量、生成模式（仅目标 / 目标 + 材料）、
 * 逐节点切换配方、「到此为止」与「继续展开」、以及勾选要生成的最终材料。
 *
 * <p>全部语义落在 {@link MaterialPreviewState}，本类只负责渲染与事件分发；
 * 生成结果通过回调交给调用方（任务列表）走现有保存链路。
 */
public class MaterialListScreen extends Screen {

    /** 列表行高（像素）。 */
    private static final int ROW_HEIGHT = 18;
    /** 勾选框占用宽度（像素）。 */
    private static final int CHECKBOX_WIDTH = 12;
    /** 每层缩进（像素）。 */
    private static final int INDENT_WIDTH = 10;
    /** 右侧状态标签预留宽度（像素），超出时不再绘制。 */
    private static final int TRAILING_RESERVE = 64;
    /** 树形连线颜色。 */
    private static final int CONNECTOR_COLOR = 0x66AAAAAA;
    /** 底部行操作按钮宽度（像素）。 */
    private static final int ACTION_BUTTON_WIDTH = 88;
    /** 底部行操作按钮间距（像素）。 */
    private static final int ACTION_BUTTON_GAP = 6;
    /** 材料数量颜色：尚未满足需求。 */
    private static final int MATERIAL_COUNT_COLOR = 0xFF9CDCFE;
    /** 材料数量颜色：背包持有量已达到或超过需求（标绿提示无需再准备）。 */
    private static final int MATERIAL_SUFFICIENT_COLOR = 0xFF55FF55;
    /** 背包已有数量的提示颜色。 */
    private static final int MATERIAL_HELD_COLOR = 0xFF888888;

    private final Screen parent;
    private final MaterialRecipeIndex index;
    private final MaterialPreviewState state;
    private final MaterialTaskContext context;
    private final Consumer<List<Task>> generateCallback;

    private MaterialPlan plan;
    private List<Row> rows = List.of();
    /** 玩家背包内已有物品数量（不展开潜影盒等容器内容），用于标注材料是否已经够用。 */
    private Map<String, Integer> heldItemCounts = Map.of();
    /** 背包持有量来源；默认读取玩家背包，测试可替换为固定数据。 */
    private Supplier<Map<String, Integer>> heldItemCountsSource = this::collectHeldItemCounts;
    /** 按显示顺序自上而下分配给每一行的背包持有量，用于同种材料跨层级累计比较。 */
    private List<Integer> rowHeldCounts = List.of();
    private int selectedRow = -1;
    private int scrollOffset;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;

    private EditBox countField;
    private Button modeButton;
    private Button recipeButton;
    private Button stopButton;
    private Button candidateButton;
    private Button generateButton;

    /**
     * 列表行模型：节点 + 缩进层级 + 树形连线信息。
     *
     * @param node            展开树节点
     * @param depth           缩进层级（根为 0）
     * @param lastChild       当前节点是否是同级中的最后一个（决定连线是 └ 还是 ├）
     * @param ancestorHasNext 各祖先层是否还有后续兄弟，索引 i 对应第 i+1 层，决定该层是否画竖向连线
     */
    private record Row(MaterialNode node, int depth, boolean lastChild, List<Boolean> ancestorHasNext) {
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
     * 初始化数量输入、模式按钮、行操作按钮与底部按钮，并完成首次解析。
     */
    @Override
    protected void init() {
        int w = Math.max(300, Math.min(400, width - 40));
        int h = Math.max(200, Math.min(320, height - 40));
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        listLeft = x + 10;
        listRight = x + w - 10;
        listTop = y + 74;
        listBottom = y + h - 58;

        countField = new EditBox(font, x + 10, y + 44, 60, 18,
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

        modeButton = Button.builder(modeText(), button -> {
            state.toggleMode();
            button.setMessage(modeText());
        }).bounds(x + 80, y + 43, w - 90, 20).build();
        addRenderableWidget(modeButton);

        recipeButton = Button.builder(Component.translatable("gui.todolist.material_preview.action.cycle_recipe"),
                        button -> cycleSelectedRecipe())
                .bounds(x + 10, y + h - 50, ACTION_BUTTON_WIDTH, 20).build();
        addRenderableWidget(recipeButton);

        stopButton = Button.builder(Component.translatable("gui.todolist.material_preview.action.stop_at"),
                        button -> toggleSelectedStop())
                .bounds(x + 10 + ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP, y + h - 50, ACTION_BUTTON_WIDTH, 20).build();
        addRenderableWidget(stopButton);

        candidateButton = Button.builder(Component.translatable("gui.todolist.material_preview.action.choose_candidate"),
                        button -> openCandidateSelector())
                .tooltip(Tooltip.create(Component.translatable("gui.todolist.material_preview.action.choose_candidate.tooltip")))
                .bounds(x + 10 + 2 * (ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP), y + h - 50, ACTION_BUTTON_WIDTH, 20).build();
        addRenderableWidget(candidateButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(x + 10, y + h - 26, 80, 20).build());

        generateButton = Button.builder(Component.translatable("gui.todolist.material_preview.generate"),
                        button -> generateTasks())
                .bounds(x + w - 110, y + h - 26, 100, 20).build();
        addRenderableWidget(generateButton);

        refreshPlan();
    }

    /**
     * 重新解析材料计划并刷新行与按钮状态。
     *
     * <p>解析前先把玩家背包内容同步给状态层：配方中的多候选材料（物品标签等）
     * 默认优先选中玩家已经拥有的那种。
     */
    private void refreshPlan() {
        heldItemCounts = heldItemCountsSource.get();
        state.setPreferredItems(heldItemCounts.keySet());
        plan = state.resolve(index);
        List<Row> flattened = new ArrayList<>();
        flatten(plan == null ? null : plan.root(), 0, true, new ArrayList<>(), flattened);
        rows = flattened;
        rowHeldCounts = allocateHeldCounts(rows, heldItemCounts);
        if (selectedRow >= rows.size()) {
            selectedRow = rows.isEmpty() ? -1 : rows.size() - 1;
        }
        int maxScroll = Math.max(0, rows.size() - visibleRows());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        refreshWidgetState();
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
     * 按物品资源 ID 汇总已有数量。
     *
     * @param stacks 物品堆列表
     * @return 物品资源 ID → 总数量；空输入返回空 Map
     */
    static Map<String, Integer> countHeldItems(Iterable<ItemStack> stacks) {
        if (stacks == null) {
            return Map.of();
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts.merge(itemId, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    /**
     * 判断背包持有量是否已达到配方需求（达到或超过即视为"不用再准备"）。
     *
     * @param heldCount     背包持有量
     * @param requiredCount 配方需求量
     * @return 满足时返回 true
     */
    static boolean isRequirementSatisfied(int heldCount, int requiredCount) {
        return requiredCount > 0 && heldCount >= requiredCount;
    }

    /**
     * 按显示顺序自上而下为每一行分配背包持有量。
     *
     * <p>同一种材料可能出现在多个层级（例如第 2 层与第 3 层各需要 8 个木板），
     * 这种情况下的需求必须累计比较：靠上（层级更高）的行先占用背包里的数量，
     * 靠下的行只能拿剩余数量去比较；否则每一行单独看都"已够"，实际总量却不够。
     *
     * @param rows       按显示顺序排列的行
     * @param heldCounts 背包持有量（物品资源 ID → 数量）
     * @return 与 rows 一一对应的可用持有量
     */
    private static List<Integer> allocateHeldCounts(List<Row> rows, Map<String, Integer> heldCounts) {
        Map<String, Integer> remaining = new HashMap<>(heldCounts == null ? Map.of() : heldCounts);
        List<Integer> allocations = new ArrayList<>(rows.size());
        for (Row row : rows) {
            MaterialNode node = row.node();
            allocations.add(consumeHeld(remaining, node.itemId(), node.requiredCount()));
        }
        return allocations;
    }

    /**
     * 从剩余持有量中取走某一行需求所需的部分，并返回本次可用数量。
     *
     * @param remaining     剩余持有量（会被就地扣减）
     * @param itemId        物品资源 ID
     * @param requiredCount 该行需求量
     * @return 本次可用于比较的持有量
     */
    private static int consumeHeld(Map<String, Integer> remaining, String itemId, int requiredCount) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        int available = remaining.getOrDefault(itemId, 0);
        if (available <= 0) {
            return 0;
        }
        int used = Math.min(available, Math.max(0, requiredCount));
        remaining.put(itemId, available - used);
        return used;
    }

    /**
     * 递归展开节点为行列表，同时记录树形连线所需信息。
     *
     * @param node            当前节点
     * @param depth           当前层级
     * @param lastChild       当前节点是否是同级中的最后一个
     * @param ancestorHasNext 祖先层是否还有后续兄弟的栈（索引 i 对应第 i+1 层）
     * @param out             输出列表
     */
    private static void flatten(MaterialNode node,
                                int depth,
                                boolean lastChild,
                                List<Boolean> ancestorHasNext,
                                List<Row> out) {
        if (node == null) {
            return;
        }
        out.add(new Row(node, depth, lastChild, List.copyOf(ancestorHasNext)));
        List<MaterialNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            boolean childLast = i == children.size() - 1;
            if (depth >= 1) {
                ancestorHasNext.add(!lastChild);
            }
            flatten(children.get(i), depth + 1, childLast, ancestorHasNext, out);
            if (depth >= 1) {
                ancestorHasNext.remove(ancestorHasNext.size() - 1);
            }
        }
    }

    /**
     * 按当前选中行刷新按钮可用状态与文案。
     */
    private void refreshWidgetState() {
        Row row = selectedRowOrNull();
        MaterialNode node = row == null ? null : row.node();

        boolean canCycle = node != null && !node.isLeaf()
                && index != null && index.findByOutput(node.itemId()).size() > 1;
        if (recipeButton != null) {
            recipeButton.active = canCycle;
        }

        if (stopButton != null) {
            if (node == null) {
                stopButton.active = false;
                stopButton.setMessage(Component.translatable("gui.todolist.material_preview.action.stop_at"));
            } else if (!node.isLeaf()) {
                stopButton.active = true;
                // 玩家主动展开的深层节点可以折叠回去；默认展开的根节点只能标记「到此为止」
                stopButton.setMessage(Component.translatable(state.isForceExpanded(node.itemId())
                        ? "gui.todolist.material_preview.action.restore_default"
                        : "gui.todolist.material_preview.action.stop_at"));
            } else if (node.stopReason() == MaterialStopReason.FOLDED) {
                stopButton.active = true;
                stopButton.setMessage(Component.translatable("gui.todolist.material_preview.action.force_expand"));
            } else if (node.stopReason() == MaterialStopReason.COOKING_INPUT) {
                boolean forced = state.isForceExpanded(node.itemId());
                stopButton.active = true;
                stopButton.setMessage(Component.translatable(forced
                        ? "gui.todolist.material_preview.action.restore_default"
                        : "gui.todolist.material_preview.action.force_expand"));
            } else if (node.stopReason() == MaterialStopReason.USER_STOPPED) {
                stopButton.active = true;
                stopButton.setMessage(Component.translatable("gui.todolist.material_preview.action.restore_default"));
            } else {
                stopButton.active = false;
                stopButton.setMessage(Component.translatable("gui.todolist.material_preview.action.stop_at"));
            }
        }

        if (generateButton != null) {
            generateButton.active = state.getTargetItemId() != null && !state.getTargetItemId().isEmpty();
        }
        if (candidateButton != null) {
            candidateButton.active = node != null && node.candidates().size() > 1;
        }
        if (modeButton != null) {
            modeButton.setMessage(modeText());
        }
    }

    /**
     * 生成模式按钮文本。
     *
     * @return 本地化文本
     */
    private Component modeText() {
        return Component.translatable(state.getMode() == MaterialTaskMode.TARGET_ONLY
                ? "gui.todolist.material_preview.mode.target_only"
                : "gui.todolist.material_preview.mode.with_materials");
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
     * 切换选中节点的配方（在候选配方之间轮换），随后重新解析。
     */
    private void cycleSelectedRecipe() {
        Row row = selectedRowOrNull();
        if (row == null || row.node().isLeaf() || index == null) {
            return;
        }
        state.cycleRecipe(row.node().itemId(), index.findByOutput(row.node().itemId()));
        refreshPlan();
    }

    /**
     * 为选中节点选择替代材料：列表只列出该材料的全部候选物品，选中后重新解析。
     *
     * <p>例如配方使用「木板」标签时，可在该列表中把默认材料换成任意一种木板。
     */
    private void openCandidateSelector() {
        Row row = selectedRowOrNull();
        if (row == null || minecraft == null) {
            return;
        }
        MaterialNode node = row.node();
        List<String> candidates = node.candidates();
        if (candidates.size() <= 1) {
            return;
        }
        String candidateKey = candidates.get(0);
        minecraft.setScreen(new ItemSelectorScreen(this, ItemSelectorScreen.Kind.ITEM, chosen -> {
            state.chooseCandidate(candidateKey, chosen);
            refreshPlan();
        }, false, new LinkedHashSet<>(candidates)));
    }

    /**
     * 切换选中节点的「到此为止 / 继续展开 / 折叠回去」。
     */
    private void toggleSelectedStop() {
        Row row = selectedRowOrNull();
        if (row == null) {
            return;
        }
        MaterialNode node = row.node();
        String itemId = node.itemId();
        if (!node.isLeaf()) {
            if (state.isForceExpanded(itemId)) {
                // 玩家主动展开的节点：折叠回默认折叠状态
                state.setForceExpand(itemId, false);
            } else {
                state.setStopAt(itemId, true);
            }
        } else if (node.stopReason() == MaterialStopReason.FOLDED) {
            state.setForceExpand(itemId, true);
        } else if (node.stopReason() == MaterialStopReason.COOKING_INPUT) {
            state.setForceExpand(itemId, !state.isForceExpanded(itemId));
        } else if (node.stopReason() == MaterialStopReason.USER_STOPPED) {
            state.setStopAt(itemId, false);
        } else {
            return;
        }
        refreshPlan();
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
     * 计算列表区域可显示的行数。
     *
     * @return 可见行数
     */
    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    /**
     * 返回当前选中的行。
     *
     * @return 行；无选中时返回 null
     */
    private Row selectedRowOrNull() {
        if (selectedRow < 0 || selectedRow >= rows.size()) {
            return null;
        }
        return rows.get(selectedRow);
    }

    /**
     * 滚轮滚动列表。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            if (delta < 0) {
                scrollOffset = Math.min(scrollOffset + 1, Math.max(0, rows.size() - visibleRows()));
            } else if (delta > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * 列表点击：命中勾选框时切换勾选，否则选中该行。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            int index = ((int) mouseY - listTop) / ROW_HEIGHT + scrollOffset;
            if (index >= 0 && index < rows.size()) {
                Row row = rows.get(index);
                int checkboxX = listLeft + 1 + row.depth() * INDENT_WIDTH;
                if (row.node().isLeaf() && mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_WIDTH) {
                    state.setLeafSelected(row.node().itemId(), !state.isLeafSelected(row.node().itemId()));
                } else {
                    selectedRow = index;
                }
                refreshWidgetState();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Esc 关闭，回车在数量框内确认数量。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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
     * 关闭并返回父界面。
     */
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /**
     * 渲染目标行、展开树列表、状态提示与滚动条。
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int w = Math.max(300, Math.min(400, width - 40));
        int h = Math.max(200, Math.min(320, height - 40));
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        context.fill(x, y, x + w, y + h, 0xE6161616);
        context.renderOutline(x, y, w, h, 0xFF606060);

        context.drawString(font, title, x + 10, y + 10, 0xFFE0B240, false);
        renderTargetRow(context, x, y, w);
        context.drawString(font, Component.translatable("gui.todolist.material_preview.count_label"),
                x + 10, y + 33, 0xFFAAAAAA, false);

        super.render(context, mouseX, mouseY, delta);
        renderRows(context, mouseX, mouseY);
        renderFooter(context, x, y, w, h);
    }

    /**
     * 渲染目标物品行（图标 + 名称）。
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
            context.renderItem(icon, x + w - 26, y + 8);
        }
        String name = resolveItemName(itemId);
        int requiredCount = state.getTargetCount();
        int heldCount = targetHeldCount();
        String countText = " ×" + requiredCount;
        String heldSuffix = buildHeldSuffix(heldCount, requiredCount);
        int textWidth = font.width(name) + font.width(countText) + font.width(heldSuffix);
        int maxWidth = w - 46;
        if (textWidth <= maxWidth) {
            int textX = x + w - 30 - textWidth;
            int countX = textX + font.width(name);
            context.drawString(font, name, textX, y + 12, 0xFFFFFFFF, false);
            context.drawString(font, countText, countX, y + 12, resolveCountColor(heldCount, requiredCount), false);
            if (!heldSuffix.isEmpty()) {
                context.drawString(font, heldSuffix, countX + font.width(countText), y + 12,
                        resolveSuffixColor(heldCount, requiredCount), false);
            }
        }
    }

    /**
     * 构建数量后的持有量标注。
     *
     * <p>不止用颜色区分，而且只给"还能做什么"的信息：已够用写「已够」，
     * 不够时直接写还差多少（「缺 N」），不必让玩家自己做减法。
     *
     * @param heldCount     该行可用的背包持有量
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
     * @param heldCount     该行可用的背包持有量
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
     * @param heldCount     该行可用的背包持有量
     * @param requiredCount 需求量
     * @return 文本颜色
     */
    private static int resolveSuffixColor(int heldCount, int requiredCount) {
        return isRequirementSatisfied(heldCount, requiredCount)
                ? MATERIAL_SUFFICIENT_COLOR
                : MATERIAL_HELD_COLOR;
    }

    /**
     * 读取背包中某物品的持有数量（未做跨层级分配）。
     *
     * @param itemId 物品资源 ID
     * @return 持有数量；没有时返回 0
     */
    private int heldCountFor(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        Integer held = heldItemCounts.get(itemId);
        return held == null ? 0 : held;
    }

    /**
     * 读取分配给某一行的背包持有量。
     *
     * @param rowIndex 行下标
     * @param itemId   物品资源 ID（越界时用于回退）
     * @return 该行可用的持有量
     */
    private int allocatedHeldCount(int rowIndex, String itemId) {
        if (rowIndex >= 0 && rowIndex < rowHeldCounts.size()) {
            return rowHeldCounts.get(rowIndex);
        }
        return heldCountFor(itemId);
    }

    /**
     * 读取目标物品可用的背包持有量：列表首行就是目标物品，两者共用同一份分配结果。
     *
     * @return 目标物品可用的持有量
     */
    private int targetHeldCount() {
        String targetId = state.getTargetItemId();
        if (!rows.isEmpty() && !rowHeldCounts.isEmpty()
                && targetId != null && targetId.equals(rows.get(0).node().itemId())) {
            return rowHeldCounts.get(0);
        }
        return heldCountFor(targetId);
    }

    /**
     * 渲染展开树行、选中高亮与滚动条。
     *
     * @param context 绘制上下文
     * @param mouseX  鼠标 x
     * @param mouseY  鼠标 y
     */
    private void renderRows(GuiGraphics context, int mouseX, int mouseY) {
        int rowsVisible = visibleRows();
        int endIndex = Math.min(rows.size(), scrollOffset + rowsVisible);
        context.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int i = scrollOffset; i < endIndex; i++) {
            Row row = rows.get(i);
            int rowY = listTop + (i - scrollOffset) * ROW_HEIGHT;
            boolean hovered = mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseX >= listLeft && mouseX < listRight;
            if (i == selectedRow) {
                context.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT, 0x5000A0FF);
            } else if (hovered) {
                context.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT, 0x30FFFFFF);
            }
            renderRow(context, i, row, rowY);
        }
        context.disableScissor();
        renderScrollBar(context, rowsVisible);
    }

    /**
     * 渲染单行：树形连线 + 勾选框 + 图标 + 名称 + 数量 + 右侧状态标签。
     *
     * @param context  绘制上下文
     * @param rowIndex 行下标（用于取该行可分到的背包持有量）
     * @param row      行模型
     * @param rowY     行顶部 y
     */
    private void renderRow(GuiGraphics context, int rowIndex, Row row, int rowY) {
        MaterialNode node = row.node();
        int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
        renderTreeConnectors(context, row, rowY);

        int checkboxX = listLeft + 1 + row.depth() * INDENT_WIDTH;
        String checkbox = node.isLeaf()
                ? (state.isLeafSelected(node.itemId()) ? "[x]" : "[ ]")
                : " - ";
        context.drawString(font, checkbox, checkboxX, textY, 0xFFAAAAAA, false);

        int cursor = checkboxX + CHECKBOX_WIDTH;
        ItemStack icon = TriggerTargetSupport.resolveIconStack(TaskTrigger.Type.ITEM_COLLECT, node.itemId());
        if (icon != null && !icon.isEmpty()) {
            context.renderItem(icon, cursor, rowY + 1);
        }
        cursor += 19;

        String label = resolveItemName(node.itemId());
        context.drawString(font, label, cursor, textY, 0xFFFFFFFF, false);
        int nameEndX = cursor + font.width(label);
        if (node.candidates().size() > 1) {
            String alternatives = Component.translatable("gui.todolist.material_preview.alternatives",
                    node.candidates().size()).getString();
            context.drawString(font, alternatives, nameEndX + 4, textY, 0xFF888888, false);
            nameEndX += 4 + font.width(alternatives);
        }

        String countText = "×" + node.requiredCount();
        int heldCount = allocatedHeldCount(rowIndex, node.itemId());
        String heldSuffix = buildHeldSuffix(heldCount, node.requiredCount());
        int trailingWidth = font.width(trailingTag(node));
        int limit = listRight - 2 - trailingWidth - 6;
        int countX = nameEndX + 4;
        if (countX + font.width(countText) <= limit) {
            context.drawString(font, countText, countX, textY, resolveCountColor(heldCount, node.requiredCount()), false);
            // 持有量标注优先让位：宽度不够时只隐藏标注，数量本身仍要能看到
            if (!heldSuffix.isEmpty() && countX + font.width(countText) + font.width(heldSuffix) <= limit) {
                context.drawString(font, heldSuffix, countX + font.width(countText), textY,
                        resolveSuffixColor(heldCount, node.requiredCount()), false);
            }
        }

        String tag = trailingTag(node);
        int tagWidth = font.width(tag);
        if (tagWidth <= TRAILING_RESERVE) {
            context.drawString(font, tag, listRight - 2 - tagWidth, textY, 0xFF888888, false);
        }
    }

    /**
     * 绘制当前行的树形连线：祖先层贯穿竖线 + 当前层的「├─ / └─」。
     *
     * @param context 绘制上下文
     * @param row     行模型
     * @param rowY    行顶部 y
     */
    private void renderTreeConnectors(GuiGraphics context, Row row, int rowY) {
        if (row.depth() <= 0) {
            return;
        }
        int midY = rowY + ROW_HEIGHT / 2;
        for (int level = 1; level <= row.depth(); level++) {
            int lineX = listLeft + 1 + (level - 1) * INDENT_WIDTH + INDENT_WIDTH / 2;
            boolean continues;
            if (level < row.depth()) {
                int ancestorIndex = level - 1;
                continues = ancestorIndex < row.ancestorHasNext().size()
                        && Boolean.TRUE.equals(row.ancestorHasNext().get(ancestorIndex));
            } else {
                continues = !row.lastChild();
            }
            int lineBottom = continues ? rowY + ROW_HEIGHT : midY;
            context.fill(lineX, rowY, lineX + 1, lineBottom, CONNECTOR_COLOR);
        }
        int branchX = listLeft + 1 + (row.depth() - 1) * INDENT_WIDTH + INDENT_WIDTH / 2;
        context.fill(branchX, midY, branchX + INDENT_WIDTH / 2 + 1, midY + 1, CONNECTOR_COLOR);
    }

    /**
     * 生成行右侧的状态标签：已展开节点显示配方类型，叶子节点显示终止原因。
     *
     * @param node 节点
     * @return 标签文本
     */
    private String trailingTag(MaterialNode node) {
        if (!node.isLeaf()) {
            MaterialRecipe recipe = node.recipe();
            MaterialRecipeKind kind = recipe == null ? null : recipe.kind();
            String key = kind == null ? "other" : kind.name().toLowerCase(Locale.ROOT);
            return Component.translatable("gui.todolist.material_preview.kind." + key).getString();
        }
        MaterialStopReason reason = node.stopReason();
        String key = reason == null ? "no_recipe" : reason.name().toLowerCase(Locale.ROOT);
        return Component.translatable("gui.todolist.material_preview.stop_reason." + key).getString();
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
     * 渲染滚动条。
     *
     * @param context     绘制上下文
     * @param rowsVisible 可见行数
     */
    private void renderScrollBar(GuiGraphics context, int rowsVisible) {
        if (rows.size() <= rowsVisible) {
            return;
        }
        int trackHeight = listBottom - listTop;
        int barHeight = Math.max(6, trackHeight * rowsVisible / rows.size());
        int barTrack = trackHeight - barHeight;
        int maxScroll = rows.size() - rowsVisible;
        int barY = listTop + (maxScroll <= 0 ? 0 : barTrack * scrollOffset / maxScroll);
        context.fill(listRight - 2, listTop, listRight, listBottom, 0x30FFFFFF);
        context.fill(listRight - 2, barY, listRight, barY + barHeight, 0x80FFFFFF);
    }

    /**
     * 渲染底部提示（材料条数、勾选数、截断提示）。
     *
     * @param context 绘制上下文
     * @param x       面板左上角 x
     * @param y       面板左上角 y
     * @param w       面板宽度
     * @param h       面板高度
     */
    private void renderFooter(GuiGraphics context, int x, int y, int w, int h) {
        String summary;
        if (plan == null || plan.isEmpty()) {
            summary = Component.translatable("gui.todolist.material_preview.empty").getString();
        } else {
            int selected = 0;
            for (String itemId : plan.leafTotals().keySet()) {
                if (state.isLeafSelected(itemId)) {
                    selected++;
                }
            }
            summary = Component.translatable("gui.todolist.material_preview.summary",
                    selected, plan.leafTotals().size()).getString();
            if (plan.truncated()) {
                summary = summary + "  " + Component.translatable("gui.todolist.material_preview.truncated").getString();
            }
        }
        context.drawString(font, summary, x + 10, y + h - 72, 0xFFAAAAAA, false);
    }

    /**
     * 返回行快照，供同包测试断言渲染与勾选状态。
     *
     * @return 形如 {@code "LEAF:minecraft:raw_iron:64:selected"} / {@code "NODE:minecraft:iron_ingot:64"} 的列表
     */
    List<String> getRowSnapshotsForTest() {
        List<String> snapshots = new ArrayList<>();
        for (Row row : rows) {
            MaterialNode node = row.node();
            if (node.isLeaf()) {
                snapshots.add("LEAF:" + node.itemId() + ":" + node.requiredCount()
                        + ":" + (state.isLeafSelected(node.itemId()) ? "selected" : "unselected"));
            } else {
                snapshots.add("NODE:" + node.itemId() + ":" + node.requiredCount());
            }
        }
        return snapshots;
    }

    /**
     * 返回带层级前缀的行快照，供同包测试断言树形结构与缩进层级。
     *
     * @return 形如 {@code "1|LEAF:minecraft:raw_iron:64:selected"} 的列表，前缀为层级
     */
    List<String> getRowSnapshotsWithDepthForTest() {
        List<String> snapshots = new ArrayList<>();
        for (Row row : rows) {
            MaterialNode node = row.node();
            String body = node.isLeaf()
                    ? "LEAF:" + node.itemId() + ":" + node.requiredCount()
                    + ":" + (state.isLeafSelected(node.itemId()) ? "selected" : "unselected")
                    : "NODE:" + node.itemId() + ":" + node.requiredCount();
            snapshots.add(row.depth() + "|" + body);
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
     * 返回模式按钮，供同包测试触发。
     *
     * @return 模式按钮
     */
    Button getModeButtonForTest() {
        return modeButton;
    }

    /**
     * 返回切换配方按钮，供同包测试触发。
     *
     * @return 切换配方按钮
     */
    Button getRecipeButtonForTest() {
        return recipeButton;
    }

    /**
     * 返回终止/继续展开按钮，供同包测试触发。
     *
     * @return 终止按钮
     */
    Button getStopButtonForTest() {
        return stopButton;
    }

    /**
     * 返回选择替代材料按钮，供同包测试断言可用状态。
     *
     * @return 选择材料按钮
     */
    Button getCandidateButtonForTest() {
        return candidateButton;
    }

    /**
     * 应用候选材料选择并重新解析，供同包测试模拟在选择器里点选后返回。
     *
     * @param candidateKey 候选组键（默认代表物品 ID）
     * @param chosenItemId 选定的物品资源 ID
     */
    void applyCandidateSelectionForTest(String candidateKey, String chosenItemId) {
        state.chooseCandidate(candidateKey, chosenItemId);
        refreshPlan();
    }

    /**
     * 用固定背包数据替换真实背包并重新解析，供同包测试断言材料数量标注。
     *
     * @param counts 物品资源 ID → 持有数量
     */
    void setHeldItemCountsForTest(Map<String, Integer> counts) {
        Map<String, Integer> safeCounts = counts == null ? Map.of() : Map.copyOf(counts);
        heldItemCountsSource = () -> safeCounts;
        refreshPlan();
    }

    /**
     * 返回指定行材料数量文本的颜色，供同包测试断言「已够用」标绿。
     *
     * @param rowIndex 行下标
     * @return 文本颜色；下标越界时返回 0
     */
    int getRowCountColorForTest(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return 0;
        }
        MaterialNode node = rows.get(rowIndex).node();
        return resolveCountColor(allocatedHeldCount(rowIndex, node.itemId()), node.requiredCount());
    }

    /**
     * 返回指定行实际分到的背包持有量，供同包测试断言「同种材料跨层级累计分配」。
     *
     * @param rowIndex 行下标
     * @return 该行可用持有量；下标越界时返回 0
     */
    int getRowHeldCountForTest(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return 0;
        }
        return allocatedHeldCount(rowIndex, rows.get(rowIndex).node().itemId());
    }

    /**
     * 返回指定行数量后的持有量标注文本，供同包测试断言「已够」提示。
     *
     * @param rowIndex 行下标
     * @return 标注文本；下标越界或无需标注时返回空串
     */
    String getRowHeldSuffixForTest(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return "";
        }
        MaterialNode node = rows.get(rowIndex).node();
        return buildHeldSuffix(allocatedHeldCount(rowIndex, node.itemId()), node.requiredCount());
    }

    /**
     * 返回目标物品数量文本的颜色，供同包测试断言「已够用」标绿。
     *
     * @return 文本颜色
     */
    int getTargetCountColorForTest() {
        return resolveCountColor(targetHeldCount(), state.getTargetCount());
    }

    /**
     * 返回目标物品数量后的持有量标注文本，供同包测试断言「已够」提示。
     *
     * @return 标注文本；无需标注时返回空串
     */
    String getTargetHeldSuffixForTest() {
        return buildHeldSuffix(targetHeldCount(), state.getTargetCount());
    }

    /**
     * 返回材料数量标绿使用的颜色常量，供同包测试断言。
     *
     * @return 满足需求时的数量文本颜色
     */
    static int sufficientColorForTest() {
        return MATERIAL_SUFFICIENT_COLOR;
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
     * 返回选中行的下标，供同包测试断言。
     *
     * @return 行下标；无选中时为 -1
     */
    int getSelectedRowForTest() {
        return selectedRow;
    }

    /**
     * 返回指定行的中心 y 坐标，供同包测试模拟点击。
     *
     * @param rowIndex 行下标
     * @return 中心 y 坐标
     */
    int getRowCenterYForTest(int rowIndex) {
        return listTop + (rowIndex - scrollOffset) * ROW_HEIGHT + ROW_HEIGHT / 2;
    }

    /**
     * 返回勾选框中心 x 坐标（按首行层级计算），供同包测试模拟点击。
     *
     * @return 勾选框中心 x 坐标
     */
    int getCheckboxCenterXForTest() {
        return getCheckboxCenterXForTest(0);
    }

    /**
     * 返回指定行勾选框中心 x 坐标，供同包测试模拟点击。
     *
     * @param rowIndex 行下标
     * @return 勾选框中心 x 坐标
     */
    int getCheckboxCenterXForTest(int rowIndex) {
        int depth = rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).depth() : 0;
        return listLeft + 1 + depth * INDENT_WIDTH + CHECKBOX_WIDTH / 2;
    }

    /**
     * 返回指定行的缩进层级，供同包测试断言树形结构。
     *
     * @param rowIndex 行下标
     * @return 缩进层级；下标越界时返回 -1
     */
    int getRowDepthForTest(int rowIndex) {
        return rowIndex >= 0 && rowIndex < rows.size() ? rows.get(rowIndex).depth() : -1;
    }

    /**
     * 返回行文本区域的 x 坐标（按首行层级计算），供同包测试模拟点击。
     *
     * @return 文本区域 x 坐标
     */
    int getRowTextXForTest() {
        return getRowTextXForTest(0);
    }

    /**
     * 返回指定行文本区域的 x 坐标，供同包测试模拟点击。
     *
     * @param rowIndex 行下标
     * @return 文本区域 x 坐标
     */
    int getRowTextXForTest(int rowIndex) {
        return getCheckboxCenterXForTest(rowIndex) + CHECKBOX_WIDTH / 2 + 4;
    }
}
