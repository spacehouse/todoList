package com.todolist.gui;

import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TodoScreen 的测试访问器，集中承载界面自测所需的读取与操作入口。
 */
final class TodoScreenTestAccess {
    private final TodoScreen screen;

    /**
     * 创建测试访问器实例。
     *
     * @param screen 目标界面
     */
    private TodoScreenTestAccess(TodoScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    /**
     * 为指定界面创建测试访问器。
     *
     * @param screen 目标界面
     * @return 测试访问器
     */
    static TodoScreenTestAccess of(TodoScreen screen) {
        return new TodoScreenTestAccess(screen);
    }

    /**
     * 重置 TodoScreen 的静态测试状态。
     */
    static void resetGuiStateForTest() {
        writeStaticScreenField("personalHasUnsavedChanges", false);
        writeStaticScreenField("teamHasUnsavedChanges", false);
        writeStaticScreenField("lastGuiState", null);
    }

    /**
     * 返回当前选中的项目。
     *
     * @return 当前项目
     */
    Project getCurrentProjectForTest() {
        return readScreenField("currentProject", Project.class);
    }

    /**
     * 返回当前选中的任务。
     *
     * @return 当前任务
     */
    Task getSelectedTaskForTest() {
        return readScreenField("selectedTask", Task.class);
    }

    /**
     * 返回当前视图模式名称。
     *
     * @return 视图模式枚举名
     */
    String getViewModeNameForTest() {
        return readScreenEnumName("viewMode");
    }

    /**
     * 返回当前空间模式名称。
     *
     * @return 空间模式枚举名
     */
    String getCurrentSpaceModeNameForTest() {
        return readScreenEnumName("currentSpaceMode");
    }

    /**
     * 返回当前可见的任务视图选项名称。
     *
     * @return 视图选项名称列表
     */
    List<String> getVisibleTaskViewOptionNamesForTest() {
        return TodoScreenViewModeSupport.getVisibleTaskViewOptionNames(getCurrentSpaceModeNameForTest());
    }

    /**
     * 返回当前任务视图选项名称。
     *
     * @return 任务视图选项枚举名
     */
    String getCurrentTaskViewOptionNameForTest() {
        return readScreenEnumName("currentTaskViewOption");
    }

    /**
     * 判断已完成分组是否展开。
     *
     * @return 展开时返回 true
     */
    boolean isCompletedSectionExpandedForTest() {
        return readScreenBoolean("completedExpanded");
    }

    /**
     * 判断未完成分组是否展开。
     *
     * @return 展开时返回 true
     */
    boolean isActiveSectionExpandedForTest() {
        return readScreenBoolean("activeExpanded");
    }

    /**
     * 返回当前响应式档位名称。
     *
     * @return 响应式档位枚举名
     */
    String getResponsiveTierNameForTest() {
        return readScreenEnumName("responsiveTier");
    }

    /**
     * 判断项目侧栏是否为覆盖模式。
     *
     * @return 覆盖模式时返回 true
     */
    boolean isProjectSidebarOverlayForTest() {
        return readLayoutMetricFlag("sidebarOverlay");
    }

    /**
     * 判断详情面板是否为覆盖模式。
     *
     * @return 覆盖模式时返回 true
     */
    boolean isDetailPanelOverlayForTest() {
        return readLayoutMetricFlag("detailOverlay");
    }

    /**
     * 判断项目侧栏当前是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isProjectSidebarVisibleForTest() {
        return readLayoutMetricFlag("sidebarVisible");
    }

    /**
     * 判断详情面板当前是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isDetailPanelVisibleForTest() {
        return readLayoutMetricFlag("detailVisible");
    }

    /**
     * 判断侧栏切换按钮是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isSidebarToggleButtonVisibleForTest() {
        Button button = readScreenField("sidebarToggleButton", Button.class);
        return button != null && button.visible;
    }

    /**
     * 返回项目侧栏边界。
     *
     * @return 侧栏边界数组
     */
    int[] getProjectSidebarBoundsForTest() {
        return readLayoutMetricBounds("sidebarBounds");
    }

    /**
     * 返回内容区边界。
     *
     * @return 内容区边界数组
     */
    int[] getContentAreaBoundsForTest() {
        return readLayoutMetricBounds("contentBounds");
    }

    /**
     * 返回详情面板边界。
     *
     * @return 详情面板边界数组
     */
    int[] getDetailPanelBoundsForTest() {
        return readLayoutMetricBounds("detailBounds");
    }

    /**
     * 返回项目列表边界。
     *
     * @return 项目列表边界数组
     */
    int[] getProjectListBoundsForTest() {
        ProjectListWidget widget = readScreenField("projectListWidget", ProjectListWidget.class);
        return widget == null ? emptyBounds() : widget.getBoundsForTest();
    }

    /**
     * 返回新增项目按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getAddProjectButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("addProjectBtn", Button.class));
    }

    /**
     * 返回编辑项目按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getEditProjectButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("editProjectBtn", Button.class));
    }

    /**
     * 返回删除项目按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getDeleteProjectButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("deleteProjectBtn", Button.class));
    }

    /**
     * 判断申请加入按钮是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isApplyJoinProjectButtonVisibleForTest() {
        Button button = readScreenField("applyJoinProjectBtn", Button.class);
        return button != null && button.visible;
    }

    /**
     * 判断删除项目按钮是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isDeleteProjectButtonVisibleForTest() {
        Button button = readScreenField("deleteProjectBtn", Button.class);
        return button != null && button.visible;
    }

    /**
     * 返回编辑项目按钮文本。
     *
     * @return 按钮文本
     */
    String getEditProjectButtonTextForTest() {
        Button button = readScreenField("editProjectBtn", Button.class);
        return button == null ? "" : button.getMessage().getString();
    }

    /**
     * 返回当前状态筛选标识。
     *
     * @return 固定筛选值
     */
    String getCurrentFilterForTest() {
        return "active";
    }

    /**
     * 返回当前优先级筛选值。
     *
     * @return 优先级筛选值
     */
    int getCurrentPriorityFilterForTest() {
        return readScreenInteger("currentPriorityFilter");
    }

    /**
     * 返回当前搜索关键词。
     *
     * @return 搜索词
     */
    String getSearchQueryForTest() {
        return readScreenField("searchQuery", String.class);
    }

    /**
     * 返回通知数量。
     *
     * @return 当前通知数量
     */
    int getNotificationCountForTest() {
        return readScreenListField("notifications").size();
    }

    /**
     * 返回最近一条通知文本。
     *
     * @return 最新通知文本，不存在时返回空字符串
     */
    String getLastNotificationTextForTest() {
        List<TodoScreenNotificationSupport.NotificationEntry> entries = readScreenListField("notifications");
        if (entries.isEmpty()) {
            return "";
        }
        TodoScreenNotificationSupport.NotificationEntry entry = entries.get(entries.size() - 1);
        return entry == null || entry.text == null ? "" : entry.text;
    }

    /**
     * 返回当前内容区顶部面包屑摘要文本。
     *
     * @return 面包屑摘要文本
     */
    String getContentHeaderSummaryTextForTest() {
        SpaceMode currentSpaceMode = readScreenField("currentSpaceMode", SpaceMode.class);
        TaskViewOption taskViewOption = readScreenField("currentTaskViewOption", TaskViewOption.class);
        Project currentProject = getCurrentProjectForTest();
        return TodoScreenTextSupport.buildContentHeaderSummaryText(
                currentSpaceMode == SpaceMode.TEAM,
                currentProject,
                taskViewOption
        );
    }

    /**
     * 返回标题输入框。
     *
     * @return 标题输入框
     */
    EditBox getTitleFieldForTest() {
        return readScreenField("titleField", EditBox.class);
    }

    /**
     * 返回快速新增输入框。
     *
     * @return 快速新增输入框
     */
    EditBox getQuickAddFieldForTest() {
        return readScreenField("quickAddField", EditBox.class);
    }

    /**
     * 返回快速新增输入框边界。
     *
     * @return 输入框边界数组
     */
    int[] getQuickAddFieldBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(getQuickAddFieldForTest());
    }

    /**
     * 返回快速新增标记边界。
     *
     * @return 标记边界数组
     */
    int[] getQuickAddMarkerBoundsForTest() {
        return emptyBounds();
    }

    /**
     * 返回描述输入框。
     *
     * @return 描述输入框
     */
    MultiLineEditBox getDescFieldForTest() {
        return readScreenField("descField", MultiLineEditBox.class);
    }

    /**
     * 返回描述输入框边界。
     *
     * @return 输入框边界数组
     */
    int[] getDescFieldBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(getDescFieldForTest());
    }

    /**
     * 返回标签输入框。
     *
     * @return 标签输入框
     */
    EditBox getTagFieldForTest() {
        return readScreenField("tagField", EditBox.class);
    }

    /**
     * 返回任务搜索输入框。
     *
     * @return 搜索输入框
     */
    EditBox getSearchFieldForTest() {
        return readScreenField("searchField", EditBox.class);
    }

    /**
     * 返回项目搜索输入框。
     *
     * @return 搜索输入框
     */
    EditBox getProjectSearchFieldForTest() {
        return readScreenField("projectSearchField", EditBox.class);
    }

    /**
     * 判断项目搜索前缀下拉是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isProjectSearchPrefixDropdownVisibleForTest() {
        return TodoScreenProjectSearchSupport.shouldShowProjectSearchPrefixDropdown(
                readScreenBoolean("projectSearchPrefixDropdownOpen"),
                Objects.equals(getCurrentSpaceModeNameForTest(), "TEAM"),
                invokeScreen("isSidebarPanelVisible", Boolean.class),
                getProjectSearchFieldForTest()
        );
    }

    /**
     * 返回项目搜索前缀候选文本。
     *
     * @return 候选文本列表
     */
    List<String> getProjectSearchPrefixSuggestionTextsForTest() {
        List<String> texts = new ArrayList<>();
        for (TodoScreenProjectSearchSupport.ProjectSearchPrefixOption option
                : TodoScreenProjectSearchSupport.getProjectSearchPrefixOptions()) {
            texts.add(TodoScreenProjectSearchSupport.buildProjectSearchPrefixOptionText(option));
        }
        return List.copyOf(texts);
    }

    /**
     * 返回指定前缀候选项边界。
     *
     * @param index 候选项下标
     * @return 候选项边界数组
     */
    int[] getProjectSearchPrefixSuggestionBoundsForTest(int index) {
        EditBox field = getProjectSearchFieldForTest();
        if (field == null) {
            return emptyBounds();
        }
        return TodoScreenProjectSearchSupport.getProjectSearchPrefixOptionBounds(
                field.getX(),
                field.getY(),
                field.getWidth(),
                field.getHeight(),
                index
        );
    }

    /**
     * 返回当前可见的项目名称。
     *
     * @return 项目名称列表
     */
    List<String> getVisibleProjectNamesForTest() {
        ProjectListWidget widget = readScreenField("projectListWidget", ProjectListWidget.class);
        if (widget == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Project project : widget.getProjectsForTest()) {
            names.add(ProjectNameFormatter.toDisplayText(project).getString());
        }
        return List.copyOf(names);
    }

    /**
     * 返回状态筛选按钮。
     *
     * @return 状态筛选按钮
     */
    Button getFilterStatusButtonForTest() {
        return readScreenField("filterStatusButton", Button.class);
    }

    /**
     * 返回优先级筛选按钮。
     *
     * @return 优先级筛选按钮
     */
    Button getFilterPriorityButtonForTest() {
        return readScreenField("filterPriorityButton", Button.class);
    }

    /**
     * 判断当前界面是否存在未保存改动。
     *
     * @return 存在未保存改动时返回 true
     */
    boolean hasUnsavedChangesForTest() {
        return readScreenBoolean("hasUnsavedChanges");
    }

    /**
     * 返回当前筛选后的任务列表。
     *
     * @return 任务列表快照
     */
    List<Task> getFilteredTasksForTest() {
        return List.copyOf(readScreenListField("filteredTasks"));
    }

    /**
     * 返回当前任务管理器中的全部任务。
     *
     * @return 当前任务列表
     */
    List<Task> getCurrentManagerTasksForTest() {
        TaskManager manager = readScreenField("taskManager", TaskManager.class);
        return manager == null ? List.of() : manager.getAllTasks();
    }

    /**
     * 返回上下文菜单条目文本。
     *
     * @return 菜单文本列表
     */
    List<String> getContextMenuItemTextsForTest() {
        List<String> texts = new ArrayList<>();
        for (Object item : readScreenListField("contextMenuItems")) {
            Component text = readField(item, "text", Component.class);
            texts.add(text == null ? "" : text.getString());
        }
        return List.copyOf(texts);
    }

    /**
     * 判断上下文菜单是否存在。
     *
     * @return 存在时返回 true
     */
    boolean hasContextMenuForTest() {
        return TodoScreenContextMenuSupport.hasContextMenu(
                readScreenField("contextMenuTask", Task.class),
                readScreenListField("contextMenuItems")
        );
    }

    /**
     * 切换当前项目。
     *
     * @param project 目标项目
     */
    void switchProjectForTest(Project project) {
        invokeScreenVoid("switchProject", new Class<?>[] {Project.class}, project);
    }

    /**
     * 选中指定任务。
     *
     * @param task 目标任务
     */
    void selectTaskForTest(Task task) {
        invokeScreenVoid("selectTask", new Class<?>[] {Task.class}, task);
    }

    /**
     * 触发领取任务动作。
     */
    void triggerClaimTaskForTest() {
        invokeScreenVoid("onClaimTask");
    }

    /**
     * 触发放弃任务动作。
     */
    void triggerAbandonTaskForTest() {
        invokeScreenVoid("onAbandonTask");
    }

    /**
     * 触发保存流程。
     */
    void saveTasksForTest() {
        invokeScreenVoid("onSaveTasks");
    }

    /**
     * 切换已完成分组展开状态。
     */
    void toggleCompletedSectionForTest() {
        writeScreenField("completedExpanded", !isCompletedSectionExpandedForTest());
        invokeScreenVoid("applySearchFilter");
    }

    /**
     * 切换未完成分组展开状态。
     */
    void toggleActiveSectionForTest() {
        writeScreenField("activeExpanded", !isActiveSectionExpandedForTest());
        invokeScreenVoid("applySearchFilter");
    }

    /**
     * 切换到团队全部视图。
     */
    void switchToTeamAllViewForTest() {
        Method method = resolveMethodByArity(TodoScreen.class, "switchView", 1);
        Object viewMode = enumConstantByName(method.getParameterTypes()[0], "TEAM_ALL");
        invokeRaw(screen, method, viewMode);
    }

    /**
     * 切换侧栏覆盖层显示状态。
     */
    void toggleSidebarOverlayForTest() {
        invokeScreenVoid("toggleSidebarOverlay");
    }

    /**
     * 返回任务列表组件。
     *
     * @return 任务列表组件
     */
    TaskListWidget getTaskListWidgetForTest() {
        return readScreenField("taskListWidget", TaskListWidget.class);
    }

    /**
     * 返回任务列表边界。
     *
     * @return 列表边界数组
     */
    int[] getTaskListBoundsForTest() {
        TaskListWidget widget = getTaskListWidgetForTest();
        return widget == null ? emptyBounds() : widget.getBoundsForTest();
    }

    /**
     * 判断详情标题是否处于可编辑状态。
     *
     * @return 可编辑时返回 true
     */
    boolean isDetailTitleEditableForTest() {
        Object detailDraft = readScreenField("detailDraft");
        return detailDraft != null && Boolean.TRUE.equals(readField(detailDraft, "titleEditing", Boolean.class));
    }

    /**
     * 进入详情标题编辑状态。
     */
    void beginDetailTitleEditingForTest() {
        invokeScreenVoid("beginDetailTitleEditing");
    }

    /**
     * 点击详情关闭按钮。
     */
    void clickDetailCloseButtonForTest() {
        Button button = readScreenField("detailCloseButton", Button.class);
        if (button != null) {
            button.onPress();
        }
    }

    /**
     * 返回详情关闭按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getDetailCloseButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("detailCloseButton", Button.class));
    }

    /**
     * 返回详情关闭按钮文本。
     *
     * @return 按钮文本
     */
    String getDetailCloseButtonTextForTest() {
        Button button = readScreenField("detailCloseButton", Button.class);
        return button == null ? "" : button.getMessage().getString();
    }

    /**
     * 返回详情标题输入框边界。
     *
     * @return 输入框边界数组
     */
    int[] getDetailTitleFieldBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(getTitleFieldForTest());
    }

    /**
     * 判断领取按钮是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isClaimButtonVisibleForTest() {
        Button button = readScreenField("claimButton", Button.class);
        return button != null && button.visible;
    }

    /**
     * 判断放弃按钮是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isAbandonButtonVisibleForTest() {
        Button button = readScreenField("abandonButton", Button.class);
        return button != null && button.visible;
    }

    /**
     * 判断指派他人按钮是否可见。
     *
     * @return 可见时返回 true
     */
    boolean isAssignOthersButtonVisibleForTest() {
        Button button = readScreenField("assignOthersButton", Button.class);
        return button != null && button.visible;
    }

    /**
     * 返回领取按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getClaimButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("claimButton", Button.class));
    }

    /**
     * 返回领取按钮文本。
     *
     * @return 按钮文本
     */
    String getClaimButtonTextForTest() {
        Button button = readScreenField("claimButton", Button.class);
        return button == null ? "" : button.getMessage().getString();
    }

    /**
     * 返回放弃按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getAbandonButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("abandonButton", Button.class));
    }

    /**
     * 返回放弃按钮文本。
     *
     * @return 按钮文本
     */
    String getAbandonButtonTextForTest() {
        Button button = readScreenField("abandonButton", Button.class);
        return button == null ? "" : button.getMessage().getString();
    }

    /**
     * 返回指派他人按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getAssignOthersButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("assignOthersButton", Button.class));
    }

    /**
     * 返回指派他人按钮文本。
     *
     * @return 按钮文本
     */
    String getAssignOthersButtonTextForTest() {
        Button button = readScreenField("assignOthersButton", Button.class);
        return button == null ? "" : button.getMessage().getString();
    }

    /**
     * 返回保存按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getSaveButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("saveButton", Button.class));
    }

    /**
     * 返回取消按钮边界。
     *
     * @return 按钮边界数组
     */
    int[] getCancelButtonBoundsForTest() {
        return TodoScreenTestSupport.toWidgetBounds(readScreenField("cancelButton", Button.class));
    }

    /**
     * 打开任务上下文菜单。
     *
     * @param task 目标任务
     */
    void openTaskContextMenuForTest(Task task) {
        invokeScreenVoid("openTaskContextMenu", new Class<?>[] {Task.class, int.class, int.class}, task, 32, 32);
    }

    /**
     * 点击上下文菜单中的指定条目。
     *
     * @param index 条目下标
     */
    void clickContextMenuItemForTest(int index) {
        List<?> items = readScreenListField("contextMenuItems");
        if (!TodoScreenContextMenuSupport.hasContextMenu(readScreenField("contextMenuTask", Task.class), items)
                || index < 0
                || index >= items.size()) {
            invokeScreenVoid("closeTaskContextMenu");
            return;
        }
        Object item = items.get(index);
        Runnable action = readField(item, "action", Runnable.class);
        boolean enabled = Boolean.TRUE.equals(readField(item, "enabled", Boolean.class));
        if (enabled && action != null) {
            action.run();
        } else {
            invokeScreenVoid("closeTaskContextMenu");
        }
    }

    /**
     * 创建指派成员弹窗。
     *
     * @param task 目标任务
     * @return 指派弹窗
     */
    Screen createAssignPlayerScreenForTest(Task task) {
        return invokeScreen("createAssignPlayerScreen", Screen.class, new Class<?>[] {Task.class}, task);
    }

    /**
     * 返回指派弹窗中当前可见的成员名称。
     *
     * @param screen 指派弹窗
     * @return 成员名称列表
     */
    List<String> getAssignablePlayerNamesForTest(Screen screen) {
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
     * 返回指派弹窗滚动偏移量。
     *
     * @param screen 指派弹窗
     * @return 当前滚动偏移量
     */
    int getAssignPlayerScrollOffsetForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getScrollOffsetForTest();
    }

    /**
     * 返回指派弹窗当前可见行数。
     *
     * @param screen 指派弹窗
     * @return 当前可见行数
     */
    int getAssignPlayerVisibleRowsForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getVisibleRowsForTest();
    }

    /**
     * 滚动指派成员列表。
     *
     * @param screen 指派弹窗
     * @param steps 滚动步数
     */
    void scrollAssignPlayerListForTest(Screen screen, int steps) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return;
        }
        double listCenterX = assignPlayerScreen.getListCenterXForTest();
        double listCenterY = assignPlayerScreen.getListCenterYForTest();
        int totalSteps = Math.max(0, steps);
        for (int i = 0; i < totalSteps; i++) {
            assignPlayerScreen.mouseScrolled(listCenterX, listCenterY, -1.0D);
        }
    }

    /**
     * 设置指派弹窗搜索词。
     *
     * @param screen 指派弹窗
     * @param value 搜索词
     */
    void setAssignPlayerSearchForTest(Screen screen, String value) {
        if (screen instanceof AssignPlayerScreen assignPlayerScreen && assignPlayerScreen.getSearchFieldForTest() != null) {
            assignPlayerScreen.getSearchFieldForTest().setValue(value == null ? "" : value);
        }
    }

    /**
     * 点击指派弹窗中的指定成员行。
     *
     * @param screen 指派弹窗
     * @param rowIndex 成员行下标
     */
    void clickAssignPlayerRowForTest(Screen screen, int rowIndex) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen) || assignPlayerScreen.getPlayerButtonsForTest() == null) {
            return;
        }
        if (rowIndex < 0 || rowIndex >= assignPlayerScreen.getPlayerButtonsForTest().length) {
            return;
        }
        Button button = assignPlayerScreen.getPlayerButtonsForTest()[rowIndex];
        if (button != null && button.active && button.visible) {
            button.onPress();
        }
    }

    /**
     * 读取界面布局指标中的布尔标志。
     *
     * @param fieldName 字段名
     * @return 标志值
     */
    private boolean readLayoutMetricFlag(String fieldName) {
        Object metrics = readScreenField("layoutMetrics");
        return metrics != null && Boolean.TRUE.equals(readField(metrics, fieldName, Boolean.class));
    }

    /**
     * 读取界面布局指标中的边界数组。
     *
     * @param fieldName 字段名
     * @return 边界数组
     */
    private int[] readLayoutMetricBounds(String fieldName) {
        Object metrics = readScreenField("layoutMetrics");
        if (metrics == null) {
            return emptyBounds();
        }
        Object rect = readField(metrics, fieldName);
        return rect == null ? emptyBounds() : invoke(rect, "toArray", int[].class);
    }

    /**
     * 读取界面枚举字段名称。
     *
     * @param fieldName 字段名
     * @return 枚举名称
     */
    private String readScreenEnumName(String fieldName) {
        return toEnumName(readScreenField(fieldName));
    }

    /**
     * 读取界面对象字段。
     *
     * @param fieldName 字段名
     * @return 字段值
     */
    private Object readScreenField(String fieldName) {
        return readField(screen, fieldName);
    }

    /**
     * 读取界面字段并按指定类型转换。
     *
     * @param fieldName 字段名
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 字段值
     */
    private <T> T readScreenField(String fieldName, Class<T> type) {
        return readField(screen, fieldName, type);
    }

    /**
     * 读取界面布尔字段。
     *
     * @param fieldName 字段名
     * @return 字段值
     */
    private boolean readScreenBoolean(String fieldName) {
        return Boolean.TRUE.equals(readScreenField(fieldName, Boolean.class));
    }

    /**
     * 读取界面整数字段。
     *
     * @param fieldName 字段名
     * @return 字段值
     */
    private int readScreenInteger(String fieldName) {
        Integer value = readScreenField(fieldName, Integer.class);
        return value == null ? 0 : value;
    }

    /**
     * 读取界面列表字段。
     *
     * @param fieldName 字段名
     * @param <T> 列表元素类型
     * @return 列表值
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> readScreenListField(String fieldName) {
        Object value = readScreenField(fieldName);
        return value instanceof List<?> list ? (List<T>) list : List.of();
    }

    /**
     * 写入界面字段。
     *
     * @param fieldName 字段名
     * @param value 目标值
     */
    private void writeScreenField(String fieldName, Object value) {
        writeField(screen, fieldName, value);
    }

    /**
     * 调用界面的无参方法。
     *
     * @param methodName 方法名
     */
    private void invokeScreenVoid(String methodName) {
        invokeVoid(screen, methodName, new Class<?>[0]);
    }

    /**
     * 调用界面的指定方法且不关心返回值。
     *
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @param args 参数值
     */
    private void invokeScreenVoid(String methodName, Class<?>[] parameterTypes, Object... args) {
        invokeVoid(screen, methodName, parameterTypes, args);
    }

    /**
     * 调用界面的无参方法并转换返回值。
     *
     * @param methodName 方法名
     * @param returnType 返回类型
     * @param <T> 返回类型参数
     * @return 方法返回值
     */
    private <T> T invokeScreen(String methodName, Class<T> returnType) {
        return invoke(screen, methodName, returnType, new Class<?>[0]);
    }

    /**
     * 调用界面的指定方法并转换返回值。
     *
     * @param methodName 方法名
     * @param returnType 返回类型
     * @param parameterTypes 参数类型
     * @param args 参数值
     * @param <T> 返回类型参数
     * @return 方法返回值
     */
    private <T> T invokeScreen(String methodName, Class<T> returnType, Class<?>[] parameterTypes, Object... args) {
        return invoke(screen, methodName, returnType, parameterTypes, args);
    }

    /**
     * 调用目标对象的无参方法并转换返回值。
     *
     * @param target 目标对象
     * @param methodName 方法名
     * @param returnType 返回类型
     * @param <T> 返回类型参数
     * @return 方法返回值
     */
    private static <T> T invoke(Object target, String methodName, Class<T> returnType) {
        return invoke(target, methodName, returnType, new Class<?>[0]);
    }

    /**
     * 返回空边界数组。
     *
     * @return 空边界
     */
    private static int[] emptyBounds() {
        return new int[] {0, 0, 0, 0};
    }

    /**
     * 将枚举对象转换为名称。
     *
     * @param value 枚举对象
     * @return 枚举名称
     */
    private static String toEnumName(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.name() : "";
    }

    /**
     * 读取对象字段。
     *
     * @param target 目标对象
     * @param fieldName 字段名
     * @return 字段值
     */
    private static Object readField(Object target, String fieldName) {
        try {
            Field field = resolveField(target.getClass(), fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure(fieldName, exception);
        }
    }

    /**
     * 读取对象字段并按指定类型转换。
     *
     * @param target 目标对象
     * @param fieldName 字段名
     * @param type 目标类型
     * @param <T> 目标类型参数
     * @return 字段值
     */
    private static <T> T readField(Object target, String fieldName, Class<T> type) {
        Object value = readField(target, fieldName);
        return value == null ? null : type.cast(value);
    }

    /**
     * 写入对象字段。
     *
     * @param target 目标对象
     * @param fieldName 字段名
     * @param value 字段值
     */
    private static void writeField(Object target, String fieldName, Object value) {
        try {
            Field field = resolveField(target.getClass(), fieldName);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure(fieldName, exception);
        }
    }

    /**
     * 写入 TodoScreen 的静态字段。
     *
     * @param fieldName 字段名
     * @param value 字段值
     */
    private static void writeStaticScreenField(String fieldName, Object value) {
        try {
            Field field = resolveField(TodoScreen.class, fieldName);
            field.set(null, value);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure(fieldName, exception);
        }
    }

    /**
     * 调用无返回值方法。
     *
     * @param target 目标对象
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @param args 参数值
     */
    private static void invokeVoid(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        invoke(target, methodName, Void.class, parameterTypes, args);
    }

    /**
     * 调用指定方法并转换返回值。
     *
     * @param target 目标对象
     * @param methodName 方法名
     * @param returnType 返回类型
     * @param parameterTypes 参数类型
     * @param args 参数值
     * @param <T> 返回类型参数
     * @return 方法返回值
     */
    private static <T> T invoke(Object target,
                                String methodName,
                                Class<T> returnType,
                                Class<?>[] parameterTypes,
                                Object... args) {
        Method method = resolveMethod(target.getClass(), methodName, parameterTypes);
        Object value = invokeRaw(target, method, args);
        if (returnType == Void.class) {
            return null;
        }
        return returnType.cast(value);
    }

    /**
     * 调用已解析的方法对象。
     *
     * @param target 目标对象
     * @param method 已解析方法
     * @param args 参数值
     * @return 原始返回值
     */
    private static Object invokeRaw(Object target, Method method, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure(method.getName(), exception);
        }
    }

    /**
     * 解析指定字段。
     *
     * @param type 目标类型
     * @param fieldName 字段名
     * @return 已解析字段
     */
    private static Field resolveField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("Cannot resolve field: " + fieldName);
    }

    /**
     * 解析指定方法。
     *
     * @param type 目标类型
     * @param methodName 方法名
     * @param parameterTypes 参数类型
     * @return 已解析方法
     */
    private static Method resolveMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("Cannot resolve method: " + methodName);
    }

    /**
     * 按参数个数解析方法。
     *
     * @param type 目标类型
     * @param methodName 方法名
     * @param parameterCount 参数个数
     * @return 已解析方法
     */
    private static Method resolveMethodByArity(Class<?> type, String methodName, int parameterCount) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException("Cannot resolve method by arity: " + methodName);
    }

    /**
     * 根据名称解析枚举常量。
     *
     * @param enumType 枚举类型
     * @param constantName 枚举常量名
     * @return 枚举常量
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstantByName(Class<?> enumType, String constantName) {
        return Enum.valueOf((Class<? extends Enum>) enumType.asSubclass(Enum.class), constantName);
    }

    /**
     * 构造统一的反射异常。
     *
     * @param memberName 成员名称
     * @param exception 原始异常
     * @return 包装后的异常
     */
    private static IllegalStateException reflectionFailure(String memberName, ReflectiveOperationException exception) {
        return new IllegalStateException("Failed to access TodoScreen member: " + memberName, exception);
    }
}
