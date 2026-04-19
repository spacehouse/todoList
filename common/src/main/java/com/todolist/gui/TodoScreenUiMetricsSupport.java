package com.todolist.gui;

import com.todolist.gui.TodoScreenLayoutSupport.ResponsiveTier;

import net.minecraft.network.chat.Component;

/**
 * TodoScreen UI 尺寸支持类，集中管理不同响应式档位下的间距和控件高度。
 */
final class TodoScreenUiMetricsSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenUiMetricsSupport() {
    }

    static int getContentControlInset(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 6;
    }

    static int getContentTopPadding(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 8 : 10;
    }

    static int getContentTopBarHeight(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 19;
    }

    static int getContentSearchFieldHeight(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 19;
    }

    static int getContentHeaderGap(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 5;
    }

    static int getContentActionGap(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 5;
    }

    static int getContentQuickAddFieldHeight(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 19;
    }

    static int getContentBottomActionRowHeight(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 20;
    }

    static int getContentBottomPadding(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 6 : 8;
    }

    static int getSidebarControlHeight(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 20;
    }

    static int getSidebarSearchFieldHeight(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 16 : 18;
    }

    static int getSidebarBottomButtonHeight(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 20;
    }

    static int getSidebarSectionGap(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 6;
    }

    static int getSidebarProjectListGap(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 3 : 5;
    }

    static int getSidebarBottomButtonGap(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 3 : 4;
    }

    static int getSidebarBottomPadding(ResponsiveTier responsiveTier) {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 6;
    }

    static boolean useCompactSidebarBottomButtons(ResponsiveTier responsiveTier, int sidebarWidth) {
        return responsiveTier == ResponsiveTier.MINIMAL || (sidebarWidth > 0 && sidebarWidth <= 132);
    }

    static Component getUnassignedTeamViewText(int sidebarWidth) {
        return Component.literal(sidebarWidth <= 128 ? "待领" : "待领取");
    }

    static Component getAllTeamViewText() {
        return Component.translatable("gui.todolist.all");
    }
}
