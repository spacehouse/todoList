package com.todolist.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端任务完成浮动提示渲染器。
 * 接收服务端触发器自动完成通知，在画面顶部居中绘制卡片式成功提示：
 * 第一行为简短状态文案，第二行为任务标题（含真实物品图标 + 本地化名称），
 * 标题独立成行避免被前缀挤占导致展示不全。
 */
public final class TodoToastRenderer {
    /** 单条提示的展示时长（毫秒）。 */
    private static final long DURATION_MS = 4000L;
    /** 同时展示的最大条数，超出后丢弃最旧的一条。 */
    private static final int MAX_ENTRIES = 3;
    /** 标题中物品图标的绘制尺寸（像素）。 */
    private static final int ICON_SIZE = 16;
    /** 图标与名称之间的间距（像素）。 */
    private static final int ICON_TEXT_GAP = 2;
    /** 卡片左侧成功色条宽度（像素）。 */
    private static final int ACCENT_WIDTH = 3;
    /** 卡片水平内边距（像素）。 */
    private static final int PADDING_X = 10;
    /** 卡片垂直内边距（像素）。 */
    private static final int PADDING_Y = 6;
    /** 两行文本之间的间距（像素）。 */
    private static final int LINE_GAP = 3;
    /** 多条提示之间的间距（像素）。 */
    private static final int ENTRY_GAP = 4;
    /** 提示条距画面顶部距离（像素）。 */
    private static final int TOP_MARGIN = 6;
    /** 卡片最大宽度（像素）。 */
    private static final int MAX_WIDTH = 360;
    /** 卡片最小宽度（像素）。 */
    private static final int MIN_WIDTH = 140;

    private static final int BG_COLOR = 0xF0181818;
    private static final int ACCENT_COLOR = 0xFF67C23A;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    /** 待展示提示队列。 */
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private TodoToastRenderer() {
    }

    /**
     * 加入一条任务自动完成提示。
     *
     * @param title 任务标题原文（可能包含物品标记）
     */
    public static void show(String title) {
        if (title == null || title.isEmpty()) {
            return;
        }
        synchronized (ENTRIES) {
            if (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.remove(0);
            }
            ENTRIES.add(new Entry(title, System.currentTimeMillis() + DURATION_MS));
        }
    }

    /**
     * 绘制全部未过期提示卡片，过期条目自动清理。
     *
     * @param context     绘制上下文
     * @param font        字体
     * @param screenWidth 当前画面宽度
     */
    public static void render(GuiGraphics context, Font font, int screenWidth) {
        if (context == null || font == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.options != null && client.options.hideGui) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (ENTRIES) {
            ENTRIES.removeIf(entry -> entry.expireAt <= now);
            if (ENTRIES.isEmpty()) {
                return;
            }
            Component label = Component.translatable("gui.todolist.toast.trigger.title")
                    .withStyle(ChatFormatting.BOLD);
            int labelWidth = font.width(label);
            int maxCardWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, screenWidth - 20));
            int y = TOP_MARGIN;
            for (Entry entry : ENTRIES) {
                List<ItemTitleRenderer.Part> parts =
                        ItemTitleRenderer.buildParts(entry.title, font, ICON_SIZE, false, false);
                int titleWidth = measure(parts);
                int innerNeeded = Math.max(labelWidth, titleWidth);
                int boxWidth = Math.min(maxCardWidth, Math.max(MIN_WIDTH, innerNeeded + PADDING_X * 2 + ACCENT_WIDTH));
                int innerWidth = Math.max(ICON_SIZE, boxWidth - PADDING_X * 2 - ACCENT_WIDTH);
                int titleLineHeight = Math.max(ICON_SIZE, font.lineHeight);
                int boxHeight = PADDING_Y * 2 + font.lineHeight + LINE_GAP + titleLineHeight;
                int x = Math.max(0, (screenWidth - boxWidth) / 2);

                context.fill(x, y, x + boxWidth, y + boxHeight, BG_COLOR);
                context.fill(x, y, x + ACCENT_WIDTH, y + boxHeight, ACCENT_COLOR);

                int contentX = x + ACCENT_WIDTH + PADDING_X;
                context.drawString(font, label, contentX, y + PADDING_Y, ACCENT_COLOR, false);
                int titleY = y + PADDING_Y + font.lineHeight + LINE_GAP;
                int iconY = titleY + Math.max(0, (titleLineHeight - ICON_SIZE) / 2);
                drawParts(context, font, parts, contentX, iconY, innerWidth, titleY);

                y += boxHeight + ENTRY_GAP;
            }
        }
    }

    /**
     * 绘制标题分段序列（图标 + 名称 / 文本）。
     *
     * @param context    绘制上下文
     * @param font       字体
     * @param parts      分段序列
     * @param startX     起始 x
     * @param iconY      图标左上角 y
     * @param maxWidth   可用最大宽度
     * @param textY      文本基线 y
     */
    private static void drawParts(GuiGraphics context, Font font, List<ItemTitleRenderer.Part> parts,
                                  int startX, int iconY, int maxWidth, int textY) {
        List<ItemTitleRenderer.Part> visible = ItemTitleRenderer.truncate(parts, maxWidth, font);
        int cursorX = startX;
        for (ItemTitleRenderer.Part part : visible) {
            if (part.isItem()) {
                drawItemIcon(context, part.getItemId(), cursorX, iconY, part.getIconSize());
                cursorX += part.getIconSize();
                if (part.getText() != null) {
                    context.drawString(font, part.getText(), cursorX + ICON_TEXT_GAP, textY, TITLE_COLOR, false);
                    cursorX += ICON_TEXT_GAP + font.width(part.getText());
                }
            } else if (part.getText() != null) {
                context.drawString(font, part.getText(), cursorX, textY, TITLE_COLOR, false);
                cursorX += part.getAdvance();
            }
        }
    }

    /**
     * 计算分段序列的总宽度。
     *
     * @param parts 分段序列
     * @return 总宽度（像素）
     */
    private static int measure(List<ItemTitleRenderer.Part> parts) {
        int total = 0;
        for (ItemTitleRenderer.Part part : parts) {
            total += part.getAdvance();
        }
        return total;
    }

    /**
     * 绘制缩放物品图标：按目标尺寸等比缩放 16px 原生物品渲染。
     *
     * @param context 绘制上下文
     * @param itemId  物品资源 ID，无法解析时跳过
     * @param x       图标左上角 x
     * @param y       图标左上角 y
     * @param size    目标尺寸（像素）
     */
    private static void drawItemIcon(GuiGraphics context, String itemId, int x, int y, int size) {
        ItemStack stack = ItemTitleRenderer.resolveItemStack(itemId);
        if (stack == null) {
            return;
        }
        float scale = size / 16.0f;
        context.pose().pushPose();
        context.pose().translate(x, y, 0);
        context.pose().scale(scale, scale, 1.0f);
        context.renderItem(stack, 0, 0);
        context.pose().popPose();
    }

    /**
     * 清空全部提示，供离线测试隔离状态。
     */
    static void clearForTest() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }

    /**
     * 返回当前待展示提示数量，供离线测试断言。
     *
     * @return 提示数量
     */
    static int activeCountForTest() {
        synchronized (ENTRIES) {
            return ENTRIES.size();
        }
    }

    /**
     * 单条浮动提示条目。
     *
     * @param title    任务标题原文
     * @param expireAt 过期时间戳（毫秒）
     */
    private record Entry(String title, long expireAt) {
    }
}
