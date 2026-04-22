package com.todolist.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * TodoScreen 通知支持类，集中管理通知模型、存活过滤与布局常量。
 */
final class TodoScreenNotificationSupport {
    static final int BOX_WIDTH = 220;
    static final int BOX_HEIGHT = 20;
    static final int BOX_GAP = 4;
    static final int BOX_MARGIN = 8;
    static final int TEXT_LEFT_PADDING = 6;
    static final int TEXT_COLOR = 0xFFFFFF00;
    static final int BOX_BG_COLOR = 0xCC000000;
    static final int BOX_OUTLINE_COLOR = 0xFFFFFFFF;
    static final int DEFAULT_START_Y = 35;
    static final long DEFAULT_DURATION_MS = 2000L;

    /**
     * 通知数据对象。
     */
    static final class NotificationEntry {
        final String text;
        final long expireAt;

        NotificationEntry(String text, long expireAt) {
            this.text = text;
            this.expireAt = expireAt;
        }
    }

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenNotificationSupport() {
    }

    static NotificationEntry createNotification(String text, long nowMillis, long durationMillis) {
        long ttl = Math.max(0L, durationMillis);
        return new NotificationEntry(text, nowMillis + ttl);
    }

    static List<NotificationEntry> retainActive(List<NotificationEntry> source, long nowMillis) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<NotificationEntry> active = new ArrayList<>();
        for (NotificationEntry notification : source) {
            if (notification != null && notification.expireAt > nowMillis) {
                active.add(notification);
            }
        }
        return active;
    }

    static int resolveStartX(int screenWidth) {
        return Math.max(BOX_MARGIN, screenWidth - BOX_WIDTH - BOX_MARGIN);
    }

    /**
     * 解析通知堆栈的起始 X 坐标，优先锚定在内容区右上角，避免与详情抽屉操作按钮重叠。
     *
     * @param screenWidth 当前屏幕宽度
     * @param contentBounds 内容区边界
     * @return 通知起始 X 坐标
     */
    static int resolveStartX(int screenWidth, TodoScreenLayoutSupport.LayoutRect contentBounds) {
        int fallbackX = resolveStartX(screenWidth);
        if (contentBounds == null || contentBounds.width <= 0) {
            return fallbackX;
        }
        int contentAnchorX = contentBounds.x + contentBounds.width - BOX_WIDTH - BOX_MARGIN;
        int minContentX = contentBounds.x + BOX_MARGIN;
        int anchoredX = Math.max(minContentX, contentAnchorX);
        return Math.min(fallbackX, Math.max(BOX_MARGIN, anchoredX));
    }

    /**
     * 解析通知堆栈的起始 Y 坐标，优先锚定在内容区顶部。
     *
     * @param contentBounds 内容区边界
     * @return 通知起始 Y 坐标
     */
    static int resolveStartY(TodoScreenLayoutSupport.LayoutRect contentBounds) {
        if (contentBounds == null || contentBounds.height <= 0) {
            return DEFAULT_START_Y;
        }
        return Math.max(0, contentBounds.y + BOX_MARGIN);
    }
}
