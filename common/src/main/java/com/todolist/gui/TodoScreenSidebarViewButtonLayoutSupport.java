package com.todolist.gui;

import com.todolist.gui.TodoScreenLayoutSupport.LayoutRect;
import com.todolist.gui.TodoScreenLayoutSupport.ResponsiveTier;

import net.minecraft.client.gui.components.Button;

/**
 * TodoScreen 侧栏视图按钮布局支持类，集中处理按钮位置与宽度计算。
 */
final class TodoScreenSidebarViewButtonLayoutSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenSidebarViewButtonLayoutSupport() {
    }

    static void apply(Button myViewButton,
                      Button unassignedViewButton,
                      Button allViewButton,
                      LayoutRect sidebarBounds,
                      ResponsiveTier responsiveTier,
                      String spaceModeName) {
        if (sidebarBounds == null || myViewButton == null || unassignedViewButton == null || allViewButton == null) {
            return;
        }
        int sidebarInset = responsiveTier == ResponsiveTier.MINIMAL ? 5 : 7;
        int sidebarInnerX = sidebarBounds.x + sidebarInset;
        int sidebarInnerWidth = Math.max(80, sidebarBounds.width - sidebarInset * 2);
        int viewButtonsY = sidebarBounds.y
                + (responsiveTier == ResponsiveTier.MINIMAL ? 8 : 10)
                + TodoScreenUiMetricsSupport.getSidebarControlHeight(responsiveTier)
                + TodoScreenUiMetricsSupport.getSidebarSectionGap(responsiveTier);
        int teamViewButtonGap = 4;
        int teamViewButtonWidth = Math.max(26, (sidebarInnerWidth - teamViewButtonGap * 2) / 3);

        if ("TEAM".equals(spaceModeName)) {
            myViewButton.setX(sidebarInnerX);
            myViewButton.setY(viewButtonsY);
            myViewButton.setWidth(teamViewButtonWidth);

            unassignedViewButton.setX(sidebarInnerX + teamViewButtonWidth + teamViewButtonGap);
            unassignedViewButton.setY(viewButtonsY);
            unassignedViewButton.setWidth(teamViewButtonWidth);

            allViewButton.setX(sidebarInnerX + (teamViewButtonWidth + teamViewButtonGap) * 2);
            allViewButton.setY(viewButtonsY);
            allViewButton.setWidth(Math.max(24, sidebarInnerWidth - (teamViewButtonWidth + teamViewButtonGap) * 2));
            return;
        }

        myViewButton.setX(sidebarInnerX);
        myViewButton.setY(viewButtonsY);
        myViewButton.setWidth(sidebarInnerWidth);
        unassignedViewButton.setWidth(teamViewButtonWidth);
        allViewButton.setWidth(Math.max(24, sidebarInnerWidth - (teamViewButtonWidth + teamViewButtonGap) * 2));
    }
}
