package com.todolist.gui;

import com.todolist.client.AdvancementCatalog;
import com.todolist.client.TriggerTargetSupport;
import com.todolist.task.TaskTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 目标选择器界面。
 * 按 {@link Kind} 列出物品 / 方块 / 实体 / 进度，提供资源 ID 与本地化名称双匹配的搜索
 * （中文名可直接搜索），选中后通过回调返回资源 ID，供标题标记输入与触发器目标设置复用。
 */
public class ItemSelectorScreen extends Screen {
    /** 列表行高（像素）：16px 图标 + 2px 间距。 */
    private static final int ROW_HEIGHT = 18;
    /** 搜索结果最大保留条数，控制渲染与匹配成本。 */
    private static final int MAX_RESULTS = 256;

    /**
     * 选择器目标类型。
     */
    public enum Kind {
        /** 物品 */
        ITEM,
        /** 方块 */
        BLOCK,
        /** 实体 */
        ENTITY,
        /** 游戏进度 */
        ADVANCEMENT
    }

    /** 各类型的选择候选缓存，首次打开时构建。 */
    private static final Map<Kind, List<ItemEntry>> CACHE = new EnumMap<>(Kind.class);

    private final Screen parent;
    private final Kind kind;
    private final Consumer<String> selectCallback;
    private final boolean includeTagWrap;
    private List<ItemEntry> filtered = List.of();
    private EditBox searchField;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int scrollOffset;

    /**
     * 创建物品选择器（等价于 {@link Kind#ITEM}）。
     *
     * @param parent         父界面，关闭时返回
     * @param selectCallback 选中回调，参数为资源 ID
     * @param includeTagWrap 为 true 时回调值带 [item:...] 标记包裹（用于标题插入）
     */
    public ItemSelectorScreen(Screen parent, Consumer<String> selectCallback, boolean includeTagWrap) {
        this(parent, Kind.ITEM, selectCallback, includeTagWrap);
    }

    /**
     * 创建目标选择器。
     *
     * @param parent         父界面，关闭时返回
     * @param kind           目标类型
     * @param selectCallback 选中回调，参数为资源 ID
     * @param includeTagWrap 为 true 时回调值带 [item:...] 标记包裹
     */
    public ItemSelectorScreen(Screen parent, Kind kind, Consumer<String> selectCallback, boolean includeTagWrap) {
        super(Component.translatable("gui.todolist.item_selector.title." + kind.name().toLowerCase(Locale.ROOT)));
        this.parent = parent;
        this.kind = kind;
        this.selectCallback = selectCallback;
        this.includeTagWrap = includeTagWrap;
    }

    /**
     * 初始化搜索框与取消按钮，并加载对应类型的候选缓存。
     */
    @Override
    protected void init() {
        int w = Math.max(220, Math.min(360, width - 40));
        int h = Math.max(200, Math.min(280, height - 40));
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        listLeft = x + 8;
        listRight = x + w - 8;
        listTop = y + 46;
        listBottom = y + h - 34;

        searchField = new EditBox(font, x + 8, y + 24, w - 16, 18,
                Component.translatable("gui.todolist.item_selector.search"));
        searchField.setMaxLength(64);
        searchField.setHint(Component.translatable(searchHintKey()));
        searchField.setResponder(query -> refreshFilter());
        addRenderableWidget(searchField);

        addRenderableWidget(Button.builder(Component.translatable("gui.todolist.cancel"), button -> onClose())
                .bounds(x + w - 8 - 70, y + h - 26, 70, 18).build());

        ensureCache();
        refreshFilter();
        setFocused(searchField);
    }

    /**
     * 返回当前类型的搜索框提示键。
     *
     * @return 语言键
     */
    private String searchHintKey() {
        return "gui.todolist.item_selector.search_hint." + kind.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 懒加载当前类型的候选缓存。
     * 进度目录随存档与服务端数据包变化，且首次打开时可能尚未收到目录，
     * 因此进度类型每次打开都重建，避免把「空结果」永久缓存住（曾导致新存档下进度列表恒为空）。
     */
    private void ensureCache() {
        if (kind == Kind.ADVANCEMENT || !CACHE.containsKey(kind)) {
            CACHE.put(kind, buildEntries(kind));
        }
    }

    /**
     * 按类型构建候选条目。
     *
     * @param kind 目标类型
     * @return 候选条目列表
     */
    private static List<ItemEntry> buildEntries(Kind kind) {
        switch (kind) {
            case BLOCK:
                return buildBlockEntries();
            case ENTITY:
                return buildEntityEntries();
            case ADVANCEMENT:
                return buildAdvancementEntries();
            default:
                return buildItemEntries();
        }
    }

    /**
     * 构建物品候选列表。
     *
     * @return 物品条目
     */
    private static List<ItemEntry> buildItemEntries() {
        List<ItemEntry> entries = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            entries.add(newItemEntry(key.toString(), stack, item.getName(stack).getString()));
        }
        return entries;
    }

    /**
     * 构建方块候选列表，图标使用方块的物品形态。
     *
     * @return 方块条目
     */
    private static List<ItemEntry> buildBlockEntries() {
        List<ItemEntry> entries = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == null) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) {
                continue;
            }
            Item item = block.asItem();
            ItemStack stack = item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
            entries.add(newItemEntry(key.toString(), stack, block.getName().getString()));
        }
        return entries;
    }

    /**
     * 构建实体候选列表，图标按刷怪蛋约定降级解析。
     *
     * @return 实体条目
     */
    private static List<ItemEntry> buildEntityEntries() {
        List<ItemEntry> entries = new ArrayList<>();
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            if (entityType == null) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (key == null) {
                continue;
            }
            ItemStack egg = TriggerTargetSupport.resolveIconStack(TaskTrigger.Type.KILL_ENTITY, key.toString());
            entries.add(newItemEntry(key.toString(), egg, entityType.getDescription().getString()));
        }
        return entries;
    }

    /**
     * 构建服务端权威进度目录中的候选列表。
     * 客户端自带的进度列表只含「已解锁 / 可见」进度，新存档下几乎为空，
     * 因此这里使用服务端随任务同步下发的完整目录（见 {@link AdvancementCatalog}）。
     *
     * @return 进度条目
     */
    private static List<ItemEntry> buildAdvancementEntries() {
        List<ItemEntry> entries = new ArrayList<>();
        for (AdvancementCatalog.Entry entry : AdvancementCatalog.getEntries()) {
            if (entry == null || entry.id() == null) {
                continue;
            }
            entries.add(newItemEntry(entry.id(), ItemStack.EMPTY, entry.title()));
        }
        return entries;
    }

    /**
     * 创建候选项，统一计算用于匹配的小写字段。
     *
     * @param id      资源 ID
     * @param stack   图标物品堆，可为空
     * @param display 本地化显示名
     * @return 候选项
     */
    private static ItemEntry newItemEntry(String id, ItemStack stack, String display) {
        String lowerId = id.toLowerCase(Locale.ROOT);
        String safeDisplay = display == null ? id : display;
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack;
        return new ItemEntry(id, safeStack, lowerId, safeDisplay.toLowerCase(Locale.ROOT), safeDisplay);
    }

    /**
     * 按搜索词刷新过滤结果并复位滚动。
     */
    private void refreshFilter() {
        String query = searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
        List<ItemEntry> source = CACHE.getOrDefault(kind, List.of());
        List<ItemEntry> result = new ArrayList<>();
        for (ItemEntry entry : source) {
            if (query.isEmpty() || entry.lowerId().contains(query) || entry.lowerName().contains(query)) {
                result.add(entry);
                if (result.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }
        filtered = result;
        scrollOffset = 0;
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
     * 滚轮滚动列表。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta < 0) {
            scrollOffset = Math.min(scrollOffset + 1, Math.max(0, filtered.size() - visibleRows()));
        } else if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * 点击列表行即选中目标并回调返回。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && mouseX >= listLeft && mouseX < listRight
                && mouseY >= listTop && mouseY < listBottom) {
            int index = ((int) mouseY - listTop) / ROW_HEIGHT + scrollOffset;
            if (index >= 0 && index < filtered.size()) {
                accept(filtered.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 选中条目并按需包裹标记后回调关闭。
     *
     * @param entry 选中的条目
     */
    private void accept(ItemEntry entry) {
        String value = includeTagWrap ? "[item:" + entry.id() + "]" : entry.id();
        if (selectCallback != null) {
            selectCallback.accept(value);
        }
        onClose();
    }

    /**
     * 回车选中当前列表首项，Esc 关闭返回父界面。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && searchField != null && searchField.isFocused() && !filtered.isEmpty()) {
            accept(filtered.get(scrollOffset));
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
     * 渲染搜索框、结果列表（图标 + 名称 + ID）与滚动指示。
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int w = Math.max(220, Math.min(360, width - 40));
        int h = Math.max(200, Math.min(280, height - 40));
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        context.fill(x, y, x + w, y + h, 0xE6161616);
        context.renderOutline(x, y, w, h, 0xFF606060);

        context.drawString(font, title, x + 8, y + 10, 0xFFE0B240, false);
        super.render(context, mouseX, mouseY, delta);

        int rows = visibleRows();
        int endIndex = Math.min(filtered.size(), scrollOffset + rows);
        context.enableScissor(listLeft, listTop, listRight, listBottom);
        for (int i = scrollOffset; i < endIndex; i++) {
            ItemEntry entry = filtered.get(i);
            int rowY = listTop + (i - scrollOffset) * ROW_HEIGHT;
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && mouseX >= listLeft && mouseX < listRight) {
                context.fill(listLeft, rowY, listRight, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }
            if (!entry.stack().isEmpty()) {
                context.renderItem(entry.stack(), listLeft + 1, rowY + 1);
            }
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
            context.drawString(font, entry.displayName(), listLeft + 22, textY, 0xFFFFFFFF, false);
            String idText = entry.id();
            int idWidth = font.width(idText);
            if (idWidth <= listRight - listLeft - 130) {
                context.drawString(font, idText, listRight - idWidth - 2, textY, 0xFF888888, false);
            }
        }
        context.disableScissor();

        int total = filtered.size();
        String summary = total > MAX_RESULTS ? MAX_RESULTS + "+" : String.valueOf(total);
        context.drawString(font, Component.translatable("gui.todolist.item_selector.count", summary),
                x + 8, y + h - 22, 0xFFAAAAAA, false);

        if (total > rows) {
            int barHeight = Math.max(6, (listBottom - listTop) * rows / total);
            int barTrack = listBottom - listTop - barHeight;
            int barY = listTop + (total - rows <= 0 ? 0 : barTrack * scrollOffset / (total - rows));
            context.fill(listRight - 2, listTop, listRight, listBottom, 0x30FFFFFF);
            context.fill(listRight - 2, barY, listRight, barY + barHeight, 0x80FFFFFF);
        }
    }

    /**
     * 返回搜索框，供同包测试写入搜索词。
     *
     * @return 搜索输入框
     */
    EditBox getSearchFieldForTest() {
        return searchField;
    }

    /**
     * 返回当前过滤结果数量，供同包测试断言搜索命中。
     *
     * @return 过滤结果数量
     */
    int getFilteredCountForTest() {
        return filtered.size();
    }

    /**
     * 返回当前选择器类型，供同包测试断言。
     *
     * @return 目标类型
     */
    Kind getKindForTest() {
        return kind;
    }

    /**
     * 选择候选项。
     *
     * @param id      资源 ID
     * @param stack   图标物品堆，可为空
     * @param display 本地化显示名
     */
    public record ItemEntry(String id, ItemStack stack, String lowerId, String lowerName, String displayName) {
    }
}
