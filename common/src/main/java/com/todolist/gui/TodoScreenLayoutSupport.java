package com.todolist.gui;

import com.todolist.config.ModConfig;

/**
 * TodoScreen 布局支持类，负责统一管理响应式档位与主布局参数计算。
 */
final class TodoScreenLayoutSupport {
    private static final int SIDEBAR_WIDTH_BUMP = 12;

    /**
     * 表示界面在不同屏幕尺寸下使用的响应式档位。
     */
    enum ResponsiveTier {
        LARGE,
        MEDIUM,
        COMPACT,
        MINIMAL
    }

    /**
     * 表示一个矩形布局区域。
     */
    static final class LayoutRect {
        final int x;
        final int y;
        final int width;
        final int height;

        /**
         * 创建布局矩形，并对坐标和尺寸做非负约束。
         *
         * @param x 左上角横坐标
         * @param y 左上角纵坐标
         * @param width 区域宽度
         * @param height 区域高度
         */
        LayoutRect(int x, int y, int width, int height) {
            this.x = Math.max(0, x);
            this.y = Math.max(0, y);
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        /**
         * 判断给定坐标是否位于当前矩形区域内。
         *
         * @param mouseX 鼠标横坐标
         * @param mouseY 鼠标纵坐标
         * @return 命中当前区域时返回 {@code true}
         */
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        /**
         * 将矩形区域转换为测试使用的边界数组。
         *
         * @return 按 x、y、width、height 顺序返回的数组
         */
        int[] toArray() {
            return new int[] {x, y, width, height};
        }
    }

    /**
     * 汇总主界面布局计算结果，便于各区域统一渲染和命中判断。
     */
    static final class MainLayoutMetrics {
        final ResponsiveTier responsiveTier;
        final LayoutRect sidebarBounds;
        final LayoutRect contentBounds;
        final LayoutRect detailBounds;
        final boolean sidebarOverlay;
        final boolean detailOverlay;
        final boolean sidebarVisible;
        final boolean detailVisible;
        final int padding;
        final int gap;

        /**
         * 创建主界面布局参数对象。
         *
         * @param responsiveTier 当前响应式档位
         * @param sidebarBounds 侧栏区域
         * @param contentBounds 内容区域
         * @param detailBounds 详情区域
         * @param sidebarOverlay 侧栏是否为覆盖层
         * @param detailOverlay 详情区是否为覆盖层
         * @param sidebarVisible 侧栏是否可见
         * @param detailVisible 详情区是否可见
         * @param padding 主布局外边距
         * @param gap 主布局区域间距
         */
        MainLayoutMetrics(ResponsiveTier responsiveTier,
                          LayoutRect sidebarBounds,
                          LayoutRect contentBounds,
                          LayoutRect detailBounds,
                          boolean sidebarOverlay,
                          boolean detailOverlay,
                          boolean sidebarVisible,
                          boolean detailVisible,
                          int padding,
                          int gap) {
            this.responsiveTier = responsiveTier;
            this.sidebarBounds = sidebarBounds;
            this.contentBounds = contentBounds;
            this.detailBounds = detailBounds;
            this.sidebarOverlay = sidebarOverlay;
            this.detailOverlay = detailOverlay;
            this.sidebarVisible = sidebarVisible;
            this.detailVisible = detailVisible;
            this.padding = padding;
            this.gap = gap;
        }
    }

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenLayoutSupport() {
    }

    /**
     * 根据屏幕宽高计算当前界面的响应式档位。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 当前响应式档位
     */
    static ResponsiveTier resolveResponsiveTier(int screenWidth, int screenHeight) {
        ResponsiveTier widthTier = resolveWidthTier(screenWidth);
        ResponsiveTier heightTier = resolveHeightTier(screenHeight);
        return widthTier.ordinal() >= heightTier.ordinal() ? widthTier : heightTier;
    }

    /**
     * 根据屏幕宽度计算宽度维度的响应式档位。
     *
     * @param screenWidth 屏幕宽度
     * @return 宽度维度对应的响应式档位
     */
    static ResponsiveTier resolveWidthTier(int screenWidth) {
        if (screenWidth >= 460) {
            return ResponsiveTier.LARGE;
        }
        if (screenWidth >= 380) {
            return ResponsiveTier.MEDIUM;
        }
        if (screenWidth >= 320) {
            return ResponsiveTier.COMPACT;
        }
        return ResponsiveTier.MINIMAL;
    }

    /**
     * 根据屏幕高度计算高度维度的响应式档位。
     *
     * @param screenHeight 屏幕高度
     * @return 高度维度对应的响应式档位
     */
    static ResponsiveTier resolveHeightTier(int screenHeight) {
        if (screenHeight >= 280) {
            return ResponsiveTier.LARGE;
        }
        if (screenHeight >= 240) {
            return ResponsiveTier.MEDIUM;
        }
        if (screenHeight >= 200) {
            return ResponsiveTier.COMPACT;
        }
        return ResponsiveTier.MINIMAL;
    }

    /**
     * 根据配置和当前窗口尺寸计算主界面布局参数。
     *
     * @param config 当前配置
     * @param responsiveTier 当前响应式档位
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param sidebarOverlayVisible 侧栏覆盖层显示状态
     * @param detailOverlayVisible 详情覆盖层显示状态
     * @param hasSelectedTask 当前是否存在选中任务
     * @return 主界面布局参数
     */
    static MainLayoutMetrics buildMainLayoutMetrics(ModConfig config,
                                                    ResponsiveTier responsiveTier,
                                                    int screenWidth,
                                                    int screenHeight,
                                                    boolean sidebarOverlayVisible,
                                                    boolean detailOverlayVisible,
                                                    boolean hasSelectedTask) {
        int padding = resolveLayoutPadding(config, responsiveTier);
        int gap = responsiveTier == ResponsiveTier.LARGE ? 5 : 4;
        int panelTop = Math.max(24, padding + 16);
        int bottomBarHeight = 20;
        int inputRowHeight = 20;
        int topBarGap = clampInt(config.getElementSpacing(), 4, 12);
        int bottomBarY = screenHeight - padding - bottomBarHeight;
        int inputRowY = bottomBarY - topBarGap - inputRowHeight;
        int panelBottom = inputRowY + inputRowHeight;
        int panelHeight = Math.max(0, panelBottom - panelTop);

        boolean sidebarOverlay = responsiveTier == ResponsiveTier.MINIMAL;
        boolean detailOverlay = responsiveTier == ResponsiveTier.COMPACT || responsiveTier == ResponsiveTier.MINIMAL;
        boolean sidebarVisible = !sidebarOverlay || sidebarOverlayVisible;
        boolean detailVisible = hasSelectedTask && (!detailOverlay || detailOverlayVisible);

        int availableWidth = Math.max(120, screenWidth - padding * 2);
        int minContentWidth = switch (responsiveTier) {
            case LARGE -> Math.min(180, Math.max(160, availableWidth));
            case MEDIUM -> Math.min(140, Math.max(132, availableWidth));
            case COMPACT -> Math.min(160, Math.max(136, availableWidth));
            case MINIMAL -> Math.min(150, Math.max(120, availableWidth));
        };
        int sidebarWidth = resolveSidebarWidth(config, responsiveTier, availableWidth);
        int detailWidth = resolveDetailWidth(responsiveTier, availableWidth);

        if (!sidebarOverlay) {
            int inlineAvailable = availableWidth - gap - (detailVisible && !detailOverlay ? gap : 0);
            int desiredTotal = sidebarWidth + minContentWidth + (detailVisible && !detailOverlay ? detailWidth : 0);
            if (desiredTotal > inlineAvailable) {
                int overflow = desiredTotal - inlineAvailable;
                if (detailVisible && !detailOverlay) {
                    int detailShrink = Math.min(overflow, Math.max(0, detailWidth - 120));
                    detailWidth -= detailShrink;
                    overflow -= detailShrink;
                }
                if (overflow > 0) {
                    int sidebarShrink = Math.min(overflow, Math.max(0, sidebarWidth - 96));
                    sidebarWidth -= sidebarShrink;
                }
            }
        }

        int inlineSidebarWidth = sidebarOverlay ? 0 : sidebarWidth;
        int inlineDetailWidth = detailVisible && !detailOverlay ? detailWidth : 0;
        int contentWidth = availableWidth - inlineSidebarWidth - inlineDetailWidth;
        if (!sidebarOverlay) {
            contentWidth -= gap;
        }
        if (detailVisible && !detailOverlay) {
            contentWidth -= gap;
        }
        contentWidth = Math.max(Math.min(minContentWidth, availableWidth), contentWidth);

        int contentX = padding + (sidebarOverlay ? 0 : sidebarWidth + gap);
        LayoutRect sidebarBounds = new LayoutRect(padding, panelTop, Math.min(sidebarWidth, availableWidth), panelHeight);
        LayoutRect contentBounds = new LayoutRect(contentX, panelTop, Math.min(contentWidth, availableWidth), panelHeight);

        int detailX = detailOverlay
                ? Math.max(padding, screenWidth - padding - detailWidth)
                : contentBounds.x + contentBounds.width + (detailVisible ? gap : 0);
        LayoutRect detailBounds = detailVisible
                ? new LayoutRect(detailX, panelTop, Math.min(detailWidth, availableWidth), panelHeight)
                : new LayoutRect(detailX, panelTop, 0, panelHeight);

        return new MainLayoutMetrics(
                responsiveTier,
                sidebarBounds,
                contentBounds,
                detailBounds,
                sidebarOverlay,
                detailOverlay,
                sidebarVisible,
                detailVisible,
                padding,
                gap
        );
    }

    /**
     * 解析主布局外边距。
     *
     * @param config 当前配置
     * @param responsiveTier 当前响应式档位
     * @return 主布局外边距
     */
    static int resolveLayoutPadding(ModConfig config, ResponsiveTier responsiveTier) {
        int basePadding = clampInt(config.getPadding(), 6, 20);
        return switch (responsiveTier) {
            case LARGE -> Math.max(10, basePadding);
            case MEDIUM -> Math.max(8, Math.min(basePadding, 12));
            case COMPACT -> Math.max(6, Math.min(basePadding, 10));
            case MINIMAL -> Math.max(4, Math.min(basePadding, 8));
        };
    }

    /**
     * 根据可用宽度和配置计算侧栏宽度。
     *
     * @param config 当前配置
     * @param responsiveTier 当前响应式档位
     * @param availableWidth 可用宽度
     * @return 侧栏宽度
     */
    static int resolveSidebarWidth(ModConfig config, ResponsiveTier responsiveTier, int availableWidth) {
        int bumpedSidebarWidth = config.getProjectSidebarWidth() + SIDEBAR_WIDTH_BUMP;
        return switch (responsiveTier) {
            case LARGE -> clampInt(bumpedSidebarWidth, 124, Math.min(168, Math.max(124, availableWidth / 3 + SIDEBAR_WIDTH_BUMP)));
            case MEDIUM -> clampInt(bumpedSidebarWidth, 116, Math.min(148, Math.max(116, availableWidth / 3 + SIDEBAR_WIDTH_BUMP)));
            case COMPACT -> clampInt(bumpedSidebarWidth, 108, Math.min(136, Math.max(108, availableWidth / 3 + SIDEBAR_WIDTH_BUMP)));
            case MINIMAL -> clampInt(Math.max(144, availableWidth - 72), 144, Math.max(144, Math.min(212, availableWidth - 8)));
        };
    }

    /**
     * 根据可用宽度计算详情区宽度。
     *
     * @param responsiveTier 当前响应式档位
     * @param availableWidth 可用宽度
     * @return 详情区宽度
     */
    static int resolveDetailWidth(ResponsiveTier responsiveTier, int availableWidth) {
        return switch (responsiveTier) {
            case LARGE -> clampInt(150, 132, Math.max(132, Math.min(180, availableWidth / 2)));
            case MEDIUM -> clampInt(132, 120, Math.max(120, Math.min(156, availableWidth / 2)));
            case COMPACT -> clampInt(Math.max(148, availableWidth / 2), 148, Math.max(148, Math.min(180, availableWidth - 24)));
            case MINIMAL -> clampInt(Math.max(156, availableWidth - 72), 156, Math.max(156, availableWidth - 8));
        };
    }

    /**
     * 约束整数值到指定范围。
     *
     * @param value 原始值
     * @param min 最小值
     * @param max 最大值
     * @return 约束后的值
     */
    private static int clampInt(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}

