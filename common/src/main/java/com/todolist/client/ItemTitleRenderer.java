package com.todolist.client;

import com.todolist.task.TitleMarkupParser;
import com.todolist.task.TitleSegment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务标题分段渲染工具。
 * 将标题内联标记解析结果转换为"文本段 / 图标+名称段"的可绘制序列，
 * 提供宽度截断（保护图标完整）与解析缓存，供 HUD 与 GUI 列表共用。
 */
public final class ItemTitleRenderer {
    /** 解析缓存最大条目数，超过后整体清空重建。 */
    private static final int PARSE_CACHE_MAX_ENTRIES = 512;
    /** 图标与文本之间的像素间距。 */
    private static final int ICON_TEXT_GAP = 1;
    /** 省略号文本。 */
    private static final String ELLIPSIS = "...";

    /** 标题解析缓存：标题原文 → 分段结果。 */
    private static final Map<String, List<TitleSegment>> PARSE_CACHE = new ConcurrentHashMap<>();

    private ItemTitleRenderer() {
    }

    /**
     * 可绘制标题分段：文本段携带文本，物品段携带资源 ID 并先绘制图标。
     */
    public static final class Part {
        private final Component text;
        private final String itemId;
        private final int iconSize;
        private final int advance;

        private Part(Component text, String itemId, int iconSize, int advance) {
            this.text = text;
            this.itemId = itemId;
            this.iconSize = iconSize;
            this.advance = advance;
        }

        /**
         * 返回段文本；物品段为名称文本。
         *
         * @return 段文本
         */
        public Component getText() {
            return text;
        }

        /**
         * 返回物品资源 ID；文本段返回 null。
         *
         * @return 物品资源 ID
         */
        public String getItemId() {
            return itemId;
        }

        /**
         * 返回图标绘制尺寸（像素）。
         *
         * @return 图标尺寸
         */
        public int getIconSize() {
            return iconSize;
        }

        /**
         * 返回该段占用的水平 advance 宽度。
         *
         * @return advance 宽度
         */
        public int getAdvance() {
            return advance;
        }

        /**
         * 判断是否为物品段。
         *
         * @return 物品段返回 true
         */
        public boolean isItem() {
            return itemId != null;
        }
    }

    /**
     * 将标题构建为可绘制分段序列。
     *
     * @param title      标题原文
     * @param font       字体（用于宽度测量）
     * @param iconSize   图标绘制尺寸（像素）
     * @param strikethrough 是否应用删除线样式（已完成任务）
     * @param gray          是否应用灰色样式（已完成任务）
     * @return 分段序列；空标题返回空列表
     */
    public static List<Part> buildParts(String title, net.minecraft.client.gui.Font font, int iconSize, boolean strikethrough, boolean gray) {
        List<TitleSegment> segments = parseCached(title);
        if (segments.isEmpty()) {
            return List.of();
        }
        List<Part> parts = new ArrayList<>(segments.size());
        for (TitleSegment segment : segments) {
            if (segment.isText()) {
                String raw = segment.getValue();
                if (raw.isEmpty()) {
                    continue;
                }
                parts.add(new Part(styled(Component.literal(raw), strikethrough, gray), null, 0, font.width(raw)));
            } else {
                Component name = resolveItemName(segment.getValue(), strikethrough, gray);
                int textWidth = name == null ? 0 : font.width(name);
                int advance = iconSize + (name == null ? 0 : ICON_TEXT_GAP + textWidth);
                parts.add(new Part(name, segment.getValue(), iconSize, advance));
            }
        }
        return parts;
    }

    /**
     * 将分段序列截断到最大宽度，超限时对末段文本追加省略号，物品图标不会被截半。
     *
     * @param parts    原分段序列
     * @param maxWidth 最大宽度
     * @param font     字体
     * @return 截断后的新分段序列
     */
    public static List<Part> truncate(List<Part> parts, int maxWidth, net.minecraft.client.gui.Font font) {
        if (parts == null || parts.isEmpty() || maxWidth <= 0) {
            return List.of();
        }
        int total = 0;
        for (Part part : parts) {
            total += part.getAdvance();
        }
        if (total <= maxWidth) {
            return parts;
        }
        int ellipsisWidth = font.width(ELLIPSIS);
        int budget = maxWidth - ellipsisWidth;
        List<Part> result = new ArrayList<>();
        int used = 0;
        for (Part part : parts) {
            if (used >= budget) {
                break;
            }
            int remaining = budget - used;
            if (part.getAdvance() <= remaining) {
                result.add(part);
                used += part.getAdvance();
                continue;
            }
            if (part.isItem()) {
                int iconAdvance = part.getIconSize();
                if (iconAdvance <= remaining) {
                    result.add(new Part(null, part.getItemId(), part.getIconSize(), iconAdvance));
                    used += iconAdvance;
                }
                break;
            }
            Component text = part.getText();
            String raw = text == null ? "" : text.getString();
            String core = font.plainSubstrByWidth(raw, remaining);
            if (!core.isEmpty()) {
                MutableComponent truncated = Component.literal(core + ELLIPSIS);
                if (text != null && text.getStyle().isStrikethrough()) {
                    truncated = truncated.withStyle(ChatFormatting.STRIKETHROUGH);
                }
                result.add(new Part(truncated, null, 0, font.width(truncated.getString())));
            }
            break;
        }
        if (result.isEmpty()) {
            result.add(new Part(Component.literal(ELLIPSIS), null, 0, ellipsisWidth));
        }
        return result;
    }

    /**
     * 解析物品资源 ID 对应的名称文本；物品缺失时降级为资源 ID 原文。
     *
     * @param itemId 物品资源 ID
     * @param strikethrough 是否删除线
     * @param gray          是否灰色
     * @return 名称文本
     */
    public static Component resolveItemName(String itemId, boolean strikethrough, boolean gray) {
        Item item = resolveItem(itemId);
        if (item == null || item == Items.AIR) {
            return styled(Component.literal(itemId), strikethrough, gray);
        }
        return styled(item.getName(new ItemStack(item)), strikethrough, gray);
    }

    /**
     * 解析物品资源 ID 对应的 ItemStack；物品缺失时返回 null。
     *
     * @param itemId 物品资源 ID
     * @return 物品堆
     */
    public static ItemStack resolveItemStack(String itemId) {
        Item item = resolveItem(itemId);
        return item == null ? null : new ItemStack(item);
    }

    /**
     * 解析物品资源 ID 对应的物品实例。
     *
     * @param itemId 物品资源 ID
     * @return 物品实例，无法解析时返回 null
     */
    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null);
    }

    /**
     * 为文本应用可选的删除线与灰色样式。
     *
     * @param component    原文本
     * @param strikethrough 是否删除线
     * @param gray          是否灰色
     * @return 样式化文本
     */
    private static Component styled(Component component, boolean strikethrough, boolean gray) {
        MutableComponent mutable = component.copy();
        if (strikethrough) {
            mutable = mutable.withStyle(ChatFormatting.STRIKETHROUGH);
        }
        if (gray) {
            mutable = mutable.withStyle(ChatFormatting.GRAY);
        }
        return mutable;
    }

    /**
     * 读取标题解析缓存，未命中时解析并写入。
     *
     * @param title 标题原文
     * @return 分段结果
     */
    private static List<TitleSegment> parseCached(String title) {
        if (title == null || title.isEmpty()) {
            return List.of();
        }
        List<TitleSegment> cached = PARSE_CACHE.get(title);
        if (cached != null) {
            return cached;
        }
        List<TitleSegment> parsed = TitleMarkupParser.parse(title);
        if (PARSE_CACHE.size() >= PARSE_CACHE_MAX_ENTRIES) {
            PARSE_CACHE.clear();
        }
        PARSE_CACHE.put(title, parsed);
        return parsed;
    }
}
