package com.todolist.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;

/**
 * TodoScreen 命中检测支持类，集中处理输入框与详情编辑控件的点击命中逻辑。
 */
final class TodoScreenHitTestSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenHitTestSupport() {
    }

    static boolean isMouseOverAnyTextField(double mouseX,
                                           double mouseY,
                                           EditBox searchField,
                                           EditBox projectSearchField,
                                           EditBox quickAddField,
                                           EditBox titleField,
                                           MultiLineEditBox descField,
                                           EditBox tagField,
                                           boolean quickAddMarkerHit) {
        if (searchField != null && searchField.isMouseOver(mouseX, mouseY)) return true;
        if (projectSearchField != null && projectSearchField.isMouseOver(mouseX, mouseY)) return true;
        if (quickAddField != null && quickAddField.isMouseOver(mouseX, mouseY)) return true;
        if (quickAddMarkerHit) return true;
        if (titleField != null && titleField.visible && titleField.isMouseOver(mouseX, mouseY)) return true;
        if (descField != null && descField.visible && descField.isMouseOver(mouseX, mouseY)) return true;
        return tagField != null && tagField.visible && tagField.isMouseOver(mouseX, mouseY);
    }

    static boolean isClickInEditArea(double mouseX,
                                     double mouseY,
                                     EditBox searchField,
                                     EditBox projectSearchField,
                                     EditBox quickAddField,
                                     EditBox titleField,
                                     MultiLineEditBox descField,
                                     EditBox tagField,
                                     boolean quickAddMarkerHit,
                                     Button detailCloseButton,
                                     Button addSubtaskButton,
                                     Button claimButton,
                                     Button abandonButton,
                                     Button assignOthersButton,
                                     boolean contextMenuHit) {
        if (isMouseOverAnyTextField(
                mouseX,
                mouseY,
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField,
                quickAddMarkerHit
        )) {
            return true;
        }
        if (detailCloseButton != null && detailCloseButton.visible && detailCloseButton.isMouseOver(mouseX, mouseY)) return true;
        if (addSubtaskButton != null && addSubtaskButton.visible && addSubtaskButton.isMouseOver(mouseX, mouseY)) return true;
        if (claimButton != null && claimButton.visible && claimButton.isMouseOver(mouseX, mouseY)) return true;
        if (abandonButton != null && abandonButton.visible && abandonButton.isMouseOver(mouseX, mouseY)) return true;
        if (assignOthersButton != null && assignOthersButton.visible && assignOthersButton.isMouseOver(mouseX, mouseY)) return true;
        return contextMenuHit;
    }
}
