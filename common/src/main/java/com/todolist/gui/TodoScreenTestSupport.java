package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * TodoScreen 测试辅助类，集中提供控件边界、输入值与测试场景辅助操作。
 */
final class TodoScreenTestSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenTestSupport() {
    }

    /**
     * 将控件转换为统一的边界数组。
     *
     * @param widget 目标控件
     * @return 边界数组
     */
    static int[] toWidgetBounds(AbstractWidget widget) {
        if (widget == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()};
    }

    /**
     * 读取输入框的有效文本，并过滤空值与提示文本。
     *
     * @param field 目标输入框
     * @param hint 提示文本
     * @return 清洗后的输入值
     */
    static String getFieldValue(EditBox field, String hint) {
        if (field == null) {
            return "";
        }
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (!hint.isEmpty() && raw.equals(hint)) {
            return "";
        }
        return raw;
    }

    /**
     * 读取多行输入框的有效文本，并过滤空值与提示文本。
     *
     * @param field 目标输入框
     * @param hint 提示文本
     * @return 清洗后的输入值
     */
    static String getFieldValue(MultiLineEditBox field, String hint) {
        if (field == null) {
            return "";
        }
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (!hint.isEmpty() && raw.equals(hint)) {
            return "";
        }
        return raw;
    }

    /**
     * 聚焦项目搜索输入框，并根据能力决定是否打开前缀下拉。
     *
     * @param projectSearchField 项目搜索输入框
     * @param focusAction 追加的聚焦动作
     * @param canUseDropdown 当前是否允许展示前缀下拉
     * @param openDropdownAction 打开下拉动作
     * @param syncDropdownAction 同步下拉状态动作
     */
    static void focusProjectSearchFieldForTest(EditBox projectSearchField,
                                               Runnable focusAction,
                                               boolean canUseDropdown,
                                               Runnable openDropdownAction,
                                               Runnable syncDropdownAction) {
        if (projectSearchField == null) {
            return;
        }
        projectSearchField.setFocused(true);
        focusAction.run();
        if (canUseDropdown) {
            openDropdownAction.run();
            return;
        }
        syncDropdownAction.run();
    }

    /**
     * 读取侧栏中当前可见的项目名称。
     *
     * @param projectListWidget 项目列表控件
     * @return 项目名称快照
     */
    static List<String> getVisibleProjectNamesForTest(ProjectListWidget projectListWidget) {
        if (projectListWidget == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Project project : projectListWidget.getProjectsForTest()) {
            names.add(ProjectNameFormatter.toDisplayText(project).getString());
        }
        return List.copyOf(names);
    }

    /**
     * 读取右键菜单中的条目文本列表。
     *
     * @param contextMenuItems 菜单条目
     * @return 条目文本快照
     */
    static List<String> getContextMenuItemTextsForTest(List<ContextMenuItem> contextMenuItems) {
        if (contextMenuItems == null || contextMenuItems.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        for (ContextMenuItem item : contextMenuItems) {
            if (item != null && item.text != null) {
                texts.add(item.text.getString());
            }
        }
        return List.copyOf(texts);
    }

    /**
     * 构建项目搜索前缀候选文本列表。
     *
     * @return 前缀候选文本
     */
    static List<String> getProjectSearchPrefixSuggestionTextsForTest() {
        List<String> texts = new ArrayList<>();
        for (TodoScreenProjectSearchSupport.ProjectSearchPrefixOption option
                : TodoScreenProjectSearchSupport.getProjectSearchPrefixOptions()) {
            texts.add(TodoScreenProjectSearchSupport.buildProjectSearchPrefixOptionText(option));
        }
        return List.copyOf(texts);
    }

    /**
     * 计算指定项目搜索前缀候选项的边界。
     *
     * @param projectSearchField 项目搜索输入框
     * @param index 候选项下标
     * @return 候选项边界数组
     */
    static int[] getProjectSearchPrefixSuggestionBoundsForTest(EditBox projectSearchField, int index) {
        if (projectSearchField == null) {
            return new int[] {0, 0, 0, 0};
        }
        return TodoScreenProjectSearchSupport.getProjectSearchPrefixOptionBounds(
                projectSearchField.getX(),
                projectSearchField.getY(),
                projectSearchField.getWidth(),
                projectSearchField.getHeight(),
                index
        );
    }

    /**
     * 读取指派弹窗中当前可见的成员名称。
     *
     * @param screen 指派弹窗
     * @return 成员名称快照
     */
    static List<String> getAssignablePlayerNamesForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (AssignPlayerScreen.AssignableMember member : assignPlayerScreen.getFilteredMembersForTest()) {
            if (member != null && member.displayName != null && !member.displayName.isEmpty()) {
                names.add(member.displayName);
            }
        }
        return List.copyOf(names);
    }

    /**
     * 读取指派弹窗当前滚动偏移量。
     *
     * @param screen 指派弹窗
     * @return 当前滚动偏移量
     */
    static int getAssignPlayerScrollOffsetForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getScrollOffsetForTest();
    }

    /**
     * 读取指派弹窗当前可见行数。
     *
     * @param screen 指派弹窗
     * @return 当前可见行数
     */
    static int getAssignPlayerVisibleRowsForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getVisibleRowsForTest();
    }

    /**
     * 模拟滚动指派成员列表。
     *
     * @param screen 指派弹窗
     * @param steps 滚动步数
     */
    static void scrollAssignPlayerListForTest(Screen screen, int steps) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return;
        }
        double listCenterX = assignPlayerScreen.getListCenterXForTest();
        double listCenterY = assignPlayerScreen.getListCenterYForTest();
        int totalSteps = Math.max(0, steps);
        for (int i = 0; i < totalSteps; i++) {
            assignPlayerScreen.mouseScrolled(listCenterX, listCenterY, 0.0D, -1.0D);
        }
    }

    /**
     * 设置指派弹窗中的搜索关键字。
     *
     * @param screen 指派弹窗
     * @param value 搜索关键字
     */
    static void setAssignPlayerSearchForTest(Screen screen, String value) {
        if (screen instanceof AssignPlayerScreen assignPlayerScreen
                && assignPlayerScreen.getSearchFieldForTest() != null) {
            assignPlayerScreen.getSearchFieldForTest().setValue(value == null ? "" : value);
        }
    }

    /**
     * 点击指派弹窗中指定行的成员按钮。
     *
     * @param screen 指派弹窗
     * @param rowIndex 成员行索引
     */
    static void clickAssignPlayerRowForTest(Screen screen, int rowIndex) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)
                || assignPlayerScreen.getPlayerButtonsForTest() == null) {
            return;
        }
        Button[] playerButtons = assignPlayerScreen.getPlayerButtonsForTest();
        if (rowIndex < 0 || rowIndex >= playerButtons.length) {
            return;
        }
        Button button = playerButtons[rowIndex];
        if (button != null && button.active && button.visible) {
            button.onPress();
        }
    }
}
