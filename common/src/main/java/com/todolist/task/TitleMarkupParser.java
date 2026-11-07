package com.todolist.task;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 任务标题内联标记解析器。
 *
 * 标记语法：[item:命名空间:路径]，例如 [item:minecraft:iron_ingot]。
 * 解析输出分段列表，供各渲染层按段排版（文本 / 图标+名称）。
 *
 * 规则：
 * - 非法标记（未闭合、资源 ID 格式错误、超过长度上限）按普通文本原样保留；
 * - 省略命名空间时自动补默认命名空间 minecraft；
 * - 大写输入宽松归一为小写；
 * - 本类零平台依赖，物品名称与图标的解析由调用方注入。
 */
public final class TitleMarkupParser {
    /** 标记前缀。 */
    private static final String ITEM_PREFIX = "[item:";
    /** 标记结束符。 */
    private static final char MARKUP_END = ']';
    /** 无命名空间时补齐的默认命名空间。 */
    private static final String DEFAULT_NAMESPACE = "minecraft";
    /** 资源 ID 最大长度，与 H2 trigger_target 列宽保持一致。 */
    private static final int MAX_ITEM_ID_LENGTH = 256;
    /** 合法资源 ID 字符集（小写字母、数字、点、下划线、连字符、斜杠、冒号）。 */
    private static final Pattern VALID_ITEM_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9/._-]+$");

    private TitleMarkupParser() {
    }

    /**
     * 解析标题为分段列表。
     *
     * @param title 标题原文
     * @return 分段列表，空标题返回空列表
     */
    public static List<TitleSegment> parse(String title) {
        List<TitleSegment> segments = new ArrayList<>();
        if (title == null || title.isEmpty()) {
            return segments;
        }
        int length = title.length();
        int cursor = 0;
        StringBuilder textBuffer = new StringBuilder();
        while (cursor < length) {
            int start = title.indexOf(ITEM_PREFIX, cursor);
            if (start < 0) {
                textBuffer.append(title, cursor, length);
                break;
            }
            int end = title.indexOf(MARKUP_END, start + ITEM_PREFIX.length());
            if (end < 0) {
                textBuffer.append(title, cursor, length);
                break;
            }
            String normalized = normalizeItemId(title.substring(start + ITEM_PREFIX.length(), end));
            if (normalized == null) {
                textBuffer.append(title, cursor, end + 1);
                cursor = end + 1;
                continue;
            }
            textBuffer.append(title, cursor, start);
            flushText(segments, textBuffer);
            segments.add(TitleSegment.item(normalized));
            cursor = end + 1;
        }
        flushText(segments, textBuffer);
        return segments;
    }

    /**
     * 将标题降级为控制台纯文本：物品标记替换为资源 ID 原文。
     *
     * @param title 标题原文
     * @return 控制台可读文本
     */
    public static String toConsoleText(String title) {
        return rebuildPlainText(title, false);
    }

    /**
     * 计算标题的可见文本长度：物品标记不计入长度预算。
     *
     * @param title 标题原文
     * @return 可见文本长度
     */
    public static int visibleTextLength(String title) {
        return rebuildPlainText(title, true).length();
    }

    /**
     * 按段重建纯文本。
     *
     * @param title       标题原文
     * @param dropMarkers 为 true 时丢弃物品标记，否则替换为资源 ID
     * @return 重建文本
     */
    private static String rebuildPlainText(String title, boolean dropMarkers) {
        List<TitleSegment> segments = parse(title);
        StringBuilder builder = new StringBuilder();
        for (TitleSegment segment : segments) {
            if (segment.isText()) {
                builder.append(segment.getValue());
            } else if (!dropMarkers) {
                builder.append(segment.getValue());
            }
        }
        return builder.toString();
    }

    /**
     * 归一化标记内的资源 ID，非法时返回 null。
     *
     * @param raw 标记内原文
     * @return 归一化后的资源 ID，非法时 null
     */
    private static String normalizeItemId(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.length() > MAX_ITEM_ID_LENGTH) {
            return null;
        }
        if (trimmed.indexOf('[') >= 0) {
            return null;
        }
        String normalized = trimmed.indexOf(':') >= 0
                ? trimmed
                : DEFAULT_NAMESPACE + ':' + trimmed;
        if (!VALID_ITEM_ID.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    /**
     * 将缓冲文本刷入分段列表。
     *
     * @param segments   分段列表
     * @param textBuffer 文本缓冲
     */
    private static void flushText(List<TitleSegment> segments, StringBuilder textBuffer) {
        if (textBuffer.length() > 0) {
            segments.add(TitleSegment.text(textBuffer.toString()));
            textBuffer.setLength(0);
        }
    }
}
