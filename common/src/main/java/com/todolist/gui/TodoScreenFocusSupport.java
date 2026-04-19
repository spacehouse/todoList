package com.todolist.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;

/**
 * TodoScreen 焦点支持类，集中处理输入组件焦点清理逻辑。
 */
final class TodoScreenFocusSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenFocusSupport() {
    }

    static void clearTextFieldFocus(EditBox searchField,
                                    EditBox projectSearchField,
                                    EditBox quickAddField,
                                    EditBox titleField,
                                    MultiLineEditBox descField,
                                    EditBox tagField) {
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (projectSearchField != null) {
            projectSearchField.setFocused(false);
        }
        if (quickAddField != null) {
            quickAddField.setFocused(false);
        }
        if (titleField != null) {
            titleField.setFocused(false);
        }
        if (descField != null) {
            descField.setFocused(false);
        }
        if (tagField != null) {
            tagField.setFocused(false);
        }
    }
}
