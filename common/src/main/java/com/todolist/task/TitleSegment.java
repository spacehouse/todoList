package com.todolist.task;

/**
 * 标题分段模型，描述任务标题解析后的最小渲染单元。
 * TEXT 段携带原文文本；ITEM 段携带游戏内物品资源 ID，由渲染层决定图标与名称展示。
 */
public final class TitleSegment {
    /**
     * 分段类型。
     */
    public enum Kind {
        /** 普通文本段 */
        TEXT,
        /** 物品引用段 */
        ITEM
    }

    private final Kind kind;
    private final String value;

    private TitleSegment(Kind kind, String value) {
        this.kind = kind;
        this.value = value == null ? "" : value;
    }

    /**
     * 创建普通文本段。
     *
     * @param text 文本内容
     * @return 文本分段
     */
    public static TitleSegment text(String text) {
        return new TitleSegment(Kind.TEXT, text);
    }

    /**
     * 创建物品引用段。
     *
     * @param itemId 物品资源 ID
     * @return 物品分段
     */
    public static TitleSegment item(String itemId) {
        return new TitleSegment(Kind.ITEM, itemId);
    }

    /**
     * 返回分段类型。
     *
     * @return 分段类型
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * 返回分段值：TEXT 段为原文，ITEM 段为资源 ID。
     *
     * @return 分段值
     */
    public String getValue() {
        return value;
    }

    /**
     * 判断是否为物品引用段。
     *
     * @return 物品段返回 true
     */
    public boolean isItem() {
        return kind == Kind.ITEM;
    }

    /**
     * 判断是否为普通文本段。
     *
     * @return 文本段返回 true
     */
    public boolean isText() {
        return kind == Kind.TEXT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TitleSegment that = (TitleSegment) o;
        return kind == that.kind && java.util.Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, value);
    }

    @Override
    public String toString() {
        return "TitleSegment{" + kind + ", '" + value + "'}";
    }
}
