package com.todolist.gui;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.client.ClientTaskStorageHelper;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.client.TodoHudRenderer;
import com.todolist.config.ModConfig;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * Todo List GUI Screen
 *
 * Features:
 * - Display task list
 * - Add/Edit/Delete tasks
 * - Mark tasks as complete
 * - Filter by priority/status
 * - Project management (Sidebar)
 */
public class TodoScreen extends Screen implements ProjectManager.ProjectChangeListener {
    private static final Component TITLE = Component.translatable("gui.todolist.title");

    private enum ViewMode {
        PERSONAL,
        TEAM_UNASSIGNED,
        TEAM_ALL,
        TEAM_ASSIGNED
    }

    /**
     * 主界面的空间维度，区分个人空间与团队空间。
     */
    private enum SpaceMode {
        PERSONAL,
        TEAM
    }

    /**
     * 主界面的任务视图维度，统一个人与团队空间的可见视图语义。
     */
    private enum TaskViewOption {
        MY,
        UNASSIGNED,
        ALL
    }

    /**
     * 主界面的响应式档位，按窗口宽高综合决定布局降级策略。
     */
    private enum ResponsiveTier {
        LARGE,
        MEDIUM,
        COMPACT,
        MINIMAL
    }

    /**
     * 简单矩形布局对象，记录某个区域的边界信息。
     */
    private static final class LayoutRect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        /**
         * 创建一个矩形边界对象。
         *
         * @param x 左上角横坐标
         * @param y 左上角纵坐标
         * @param width 区域宽度
         * @param height 区域高度
         */
        private LayoutRect(int x, int y, int width, int height) {
            this.x = Math.max(0, x);
            this.y = Math.max(0, y);
            this.width = Math.max(0, width);
            this.height = Math.max(0, height);
        }

        /**
         * 判断坐标是否位于当前矩形内部。
         *
         * @param mouseX 鼠标横坐标
         * @param mouseY 鼠标纵坐标
         * @return true 表示命中当前区域
         */
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        /**
         * 以测试友好的数组形式返回矩形边界。
         *
         * @return 依次包含 x、y、width、height 的数组
         */
        private int[] toArray() {
            return new int[] {x, y, width, height};
        }
    }

    /**
     * 主界面布局快照，集中保存响应式断点和三栏区域边界。
     */
    private static final class MainLayoutMetrics {
        private final ResponsiveTier responsiveTier;
        private final LayoutRect sidebarBounds;
        private final LayoutRect contentBounds;
        private final LayoutRect detailBounds;
        private final boolean sidebarOverlay;
        private final boolean detailOverlay;
        private final boolean sidebarVisible;
        private final boolean detailVisible;
        private final int padding;
        private final int gap;

        /**
         * 创建一次主界面布局计算结果。
         *
         * @param responsiveTier 当前响应式档位
         * @param sidebarBounds 项目侧栏边界
         * @param contentBounds 主内容区边界
         * @param detailBounds 详情区边界
         * @param sidebarOverlay 项目侧栏是否覆盖显示
         * @param detailOverlay 详情区是否覆盖显示
         * @param sidebarVisible 项目侧栏当前是否可见
         * @param detailVisible 详情区当前是否可见
         * @param padding 外层边距
         * @param gap 面板间距
         */
        private MainLayoutMetrics(ResponsiveTier responsiveTier,
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
     * 详情抽屉草稿对象，集中维护当前选中任务的编辑态和显示态。
     */
    private static final class TaskDetailDraft {
        private final String taskId;
        private String title;
        private String description;
        private String tags;
        private boolean titleEditing;

        /**
         * 创建一个与选中任务绑定的详情草稿。
         *
         * @param taskId 任务标识
         * @param title 标题文本
         * @param description 描述文本
         * @param tags 标签文本
         */
        private TaskDetailDraft(String taskId, String title, String description, String tags) {
            this.taskId = taskId;
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            this.tags = tags == null ? "" : tags;
            this.titleEditing = false;
        }
    }

    private final Screen parent;
    private ProjectManager projectManager;
    private ProjectListWidget projectListWidget;
    private Project currentProject;
    private TaskManager taskManager;
    private TaskManager personalTaskManager;
    private TaskManager teamTaskManager;
    private TaskListWidget taskListWidget;
    private final List<Notification> notifications = new ArrayList<>();

    private ViewMode viewMode = ViewMode.PERSONAL;
    private SpaceMode currentSpaceMode = SpaceMode.PERSONAL;
    private TaskViewOption currentTaskViewOption = TaskViewOption.MY;
    private boolean completedExpanded;

    // Input fields
    private EditBox searchField;
    private EditBox quickAddField;
    private EditBox titleField;
    private MultiLineEditBox descField;
    private EditBox tagField;

    // Buttons
    private Button detailCloseButton;
    private Button claimButton;
    private Button abandonButton;
    private Button assignOthersButton;
    private Button saveButton;
    private Button cancelButton;
    private Button sidebarToggleButton;

    // Selected priority for new/edited tasks
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // Filter buttons
    private Button filterStatusButton;
    private Button filterPriorityButton; // Unified priority button
    private Button viewToggleButton;
    private Button configButton;
    
    // Project Search & Toggle
    private EditBox projectSearchField;
    private Button addProjectBtn;
    private Button projectScopeButton;
    private Button editProjectBtn;
    private Button deleteProjectBtn;
    private Button applyJoinProjectBtn;
    private Project.Scope projectScopeFilter = Project.Scope.PERSONAL;
    private String projectSearchQuery = "";
    private String preferredPersonalProjectId;
    private String preferredTeamProjectId;
    private boolean teamProjectsEnabled = true;
    private int savedProjectListScrollOffset;
    
    private int currentPriorityFilter = 0; // 0=All, 1=High, 2=Medium, 3=Low
    
    private Task selectedTask;
    private List<Task> filteredTasks = new ArrayList<>();
    private List<Task> baseFilteredTasks = new ArrayList<>();
    private String currentFilter = "active";
    private String searchQuery = "";
    private boolean hasUnsavedChanges = false;
    private static boolean personalHasUnsavedChanges = false;
    private static boolean teamHasUnsavedChanges = false;
    private static LastGuiState lastGuiState;
    private String openedStorageNamespace = DataPathProvider.getStorageNamespace();
    private Task contextMenuTask;
    private int contextMenuX;
    private int contextMenuY;
    private int contextMenuWidth;
    private int contextMenuItemHeight = 18;
    private List<ContextMenuItem> contextMenuItems = new ArrayList<>();
    private ResponsiveTier responsiveTier = ResponsiveTier.LARGE;
    private boolean sidebarOverlayVisible;
    private boolean detailOverlayVisible;
    private MainLayoutMetrics layoutMetrics;
    private TaskDetailDraft detailDraft;
    private boolean syncingDetailWidgets;

    private static class LastGuiState {
        Project.Scope projectScopeFilter;
        String currentProjectId;
        ViewMode viewMode;
        SpaceMode spaceMode;
        TaskViewOption taskViewOption;
        boolean completedExpanded;
        int currentPriorityFilter;
        String currentFilter;
        String searchQuery;
        String projectSearchQuery;
        String lastPersonalProjectId;
        String lastTeamProjectId;
    }

    private static class ContextMenuItem {
        final Component text;
        final boolean enabled;
        final Runnable action;

        ContextMenuItem(Component text, boolean enabled, Runnable action) {
            this.text = text;
            this.enabled = enabled;
            this.action = action;
        }
    }


    /**
     * 创建主界面，并在关闭时返回到父界面。
     */
    public TodoScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    /**
     * 重置主界面的静态运行态，供同包测试代码隔离用例。
     */
    static void resetGuiStateForTest() {
        personalHasUnsavedChanges = false;
        teamHasUnsavedChanges = false;
        lastGuiState = null;
    }

    /**
     * 返回当前项目，供同包测试代码断言项目切换与恢复逻辑。
     *
     * @return 当前项目；不存在时返回 null
     */
    Project getCurrentProjectForTest() {
        return currentProject;
    }

    /**
     * 返回当前选中任务，供同包测试代码断言选择逻辑。
     *
     * @return 当前选中任务；不存在时返回 null
     */
    Task getSelectedTaskForTest() {
        return selectedTask;
    }

    /**
     * 返回当前视图模式名称，供同包测试代码断言视图恢复逻辑。
     *
     * @return 当前视图模式名称
     */
    String getViewModeNameForTest() {
        return viewMode.name();
    }

    /**
     * 返回当前空间模式名称，供同包测试代码断言个人/团队空间切换语义。
     *
     * @return 当前空间模式名称
     */
    String getCurrentSpaceModeNameForTest() {
        return currentSpaceMode.name();
    }

    /**
     * 返回当前可见任务视图选项快照，供同包测试代码断言空间与视图映射关系。
     *
     * @return 当前可见任务视图选项名称列表
     */
    List<String> getVisibleTaskViewOptionNamesForTest() {
        List<String> names = new ArrayList<>();
        for (TaskViewOption option : buildVisibleViewOptions(currentSpaceMode)) {
            names.add(option.name());
        }
        return List.copyOf(names);
    }

    /**
     * 返回当前任务视图选项名称，供同包测试代码断言视图切换语义。
     *
     * @return 当前任务视图选项名称
     */
    String getCurrentTaskViewOptionNameForTest() {
        return currentTaskViewOption.name();
    }

    /**
     * 返回已完成分组是否展开，供同包测试代码断言折叠状态切换。
     *
     * @return true 表示已完成分组已展开
     */
    boolean isCompletedSectionExpandedForTest() {
        return completedExpanded;
    }

    /**
     * 返回当前主界面的响应式档位名称，供测试断言断点命中结果。
     *
     * @return 当前响应式档位名称
     */
    String getResponsiveTierNameForTest() {
        return responsiveTier.name();
    }

    /**
     * 返回项目侧栏当前是否以覆盖式布局呈现。
     *
     * @return true 表示项目侧栏处于覆盖模式
     */
    boolean isProjectSidebarOverlayForTest() {
        return layoutMetrics != null && layoutMetrics.sidebarOverlay;
    }

    /**
     * 返回详情区当前是否以覆盖式布局呈现。
     *
     * @return true 表示详情区处于覆盖模式
     */
    boolean isDetailPanelOverlayForTest() {
        return layoutMetrics != null && layoutMetrics.detailOverlay;
    }

    /**
     * 返回项目侧栏当前是否可见。
     *
     * @return true 表示项目侧栏可见
     */
    boolean isProjectSidebarVisibleForTest() {
        return layoutMetrics != null && layoutMetrics.sidebarVisible;
    }

    /**
     * 返回详情区当前是否可见。
     *
     * @return true 表示详情区可见
     */
    boolean isDetailPanelVisibleForTest() {
        return layoutMetrics != null && layoutMetrics.detailVisible;
    }

    /**
     * 返回项目侧栏切换按钮当前是否可见。
     *
     * @return true 表示项目侧栏切换按钮可见
     */
    boolean isSidebarToggleButtonVisibleForTest() {
        return sidebarToggleButton != null && sidebarToggleButton.visible;
    }

    /**
     * 返回项目侧栏区域边界，供测试验证响应式布局。
     *
     * @return 侧栏边界数组
     */
    int[] getProjectSidebarBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.sidebarBounds.toArray();
    }

    /**
     * 返回主内容区边界，供测试验证响应式布局。
     *
     * @return 主内容区边界数组
     */
    int[] getContentAreaBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.contentBounds.toArray();
    }

    /**
     * 返回详情区边界，供测试验证响应式布局。
     *
     * @return 详情区边界数组
     */
    int[] getDetailPanelBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.detailBounds.toArray();
    }

    /**
     * 返回当前状态筛选值，供同包测试代码断言过滤逻辑。
     *
     * @return 当前状态筛选值
     */
    String getCurrentFilterForTest() {
        return currentFilter;
    }

    /**
     * 返回当前优先级筛选值，供同包测试代码断言过滤逻辑。
     *
     * @return 当前优先级筛选值
     */
    int getCurrentPriorityFilterForTest() {
        return currentPriorityFilter;
    }

    /**
     * 返回当前搜索关键字，供同包测试代码断言搜索恢复逻辑。
     *
     * @return 当前搜索关键字
     */
    String getSearchQueryForTest() {
        return searchQuery;
    }

    /**
     * 返回当前通知数量，供同包测试代码断言提示行为。
     *
     * @return 当前通知数量
     */
    int getNotificationCountForTest() {
        return notifications.size();
    }

    /**
     * 返回任务标题输入框，供同包测试代码写入任务标题。
     *
     * @return 任务标题输入框
     */
    EditBox getTitleFieldForTest() {
        return titleField;
    }

    /**
     * 返回底部快速新增输入框，供同包测试代码驱动新增任务流程。
     *
     * @return 底部快速新增输入框
     */
    EditBox getQuickAddFieldForTest() {
        return quickAddField;
    }

    /**
     * 返回任务描述输入框，供同包测试代码写入任务描述。
     *
     * @return 任务描述输入框
     */
    MultiLineEditBox getDescFieldForTest() {
        return descField;
    }

    /**
     * 返回任务标签输入框，供同包测试代码写入标签内容。
     *
     * @return 任务标签输入框
     */
    EditBox getTagFieldForTest() {
        return tagField;
    }

    /**
     * 返回搜索输入框，供同包测试代码驱动搜索筛选。
     *
     * @return 搜索输入框
     */
    EditBox getSearchFieldForTest() {
        return searchField;
    }

    /**
     * 返回状态筛选按钮，供同包测试代码切换完成/未完成筛选。
     *
     * @return 状态筛选按钮
     */
    Button getFilterStatusButtonForTest() {
        return filterStatusButton;
    }

    /**
     * 返回优先级筛选按钮，供同包测试代码切换优先级筛选。
     *
     * @return 优先级筛选按钮
     */
    Button getFilterPriorityButtonForTest() {
        return filterPriorityButton;
    }

    /**
     * 返回当前是否存在未保存改动，供同包测试代码断言保存与关闭语义。
     *
     * @return true 表示当前存在未保存改动
     */
    boolean hasUnsavedChangesForTest() {
        return hasUnsavedChanges;
    }

    /**
     * 返回当前筛选结果任务快照，供同包测试代码断言过滤与上下文菜单行为。
     *
     * @return 当前筛选结果任务快照
     */
    List<Task> getFilteredTasksForTest() {
        return List.copyOf(filteredTasks);
    }

    /**
     * 返回当前任务管理器中的全部任务快照，供同包测试代码断言保存与关闭后的数据状态。
     *
     * @return 当前任务管理器中的全部任务快照
     */
    List<Task> getCurrentManagerTasksForTest() {
        if (taskManager == null) {
            return List.of();
        }
        return taskManager.getAllTasks();
    }

    /**
     * 返回当前上下文菜单项文本快照，供同包测试代码断言菜单内容。
     *
     * @return 当前上下文菜单项文本快照
     */
    List<String> getContextMenuItemTextsForTest() {
        List<String> texts = new ArrayList<>();
        for (ContextMenuItem item : contextMenuItems) {
            texts.add(item.text.getString());
        }
        return List.copyOf(texts);
    }

    /**
     * 返回当前是否显示任务上下文菜单，供同包测试代码断言菜单行为。
     *
     * @return true 表示当前显示任务上下文菜单
     */
    boolean hasContextMenuForTest() {
        return hasContextMenu();
    }

    /**
     * 切换当前项目，供同包测试代码直接覆盖项目切换主路径。
     *
     * @param project 目标项目；传入 null 表示清空当前项目
     */
    void switchProjectForTest(Project project) {
        switchProject(project);
    }

    /**
     * 选中指定任务，供同包测试代码直接覆盖编辑相关分支。
     *
     * @param task 目标任务
     */
    void selectTaskForTest(Task task) {
        selectTask(task);
    }

    /**
     * 触发保存流程，供同包测试代码断言保存后的状态与桥接调用。
     */
    void saveTasksForTest() {
        onSaveTasks();
    }

    /**
     * 切换已完成分组展开状态，供同包测试代码断言状态切换不会影响当前视图。
     */
    void toggleCompletedSectionForTest() {
        toggleCompletedSection();
    }

    /**
     * 切换项目侧栏覆盖层显示状态，供测试驱动极小窗口交互。
     */
    void toggleSidebarOverlayForTest() {
        toggleSidebarOverlay();
    }

    /**
     * 返回详情标题当前是否允许直接编辑，供测试验证“点击后进入编辑态”语义。
     *
     * @return true 表示详情标题已进入编辑态
     */
    boolean isDetailTitleEditableForTest() {
        return detailDraft != null && detailDraft.titleEditing;
    }

    /**
     * 触发详情标题进入编辑态，供测试模拟点击标题的行为。
     */
    void beginDetailTitleEditingForTest() {
        beginDetailTitleEditing();
    }

    /**
     * 触发详情抽屉关闭按钮，供测试验证抽屉收起逻辑。
     */
    void clickDetailCloseButtonForTest() {
        if (detailCloseButton != null) {
            detailCloseButton.onPress();
        }
    }

    /**
     * 返回详情抽屉关闭按钮的边界，供测试验证布局。
     *
     * @return 关闭按钮边界数组
     */
    int[] getDetailCloseButtonBoundsForTest() {
        return toWidgetBounds(detailCloseButton);
    }

    /**
     * 返回详情标题输入框的边界，供测试验证布局。
     *
     * @return 标题输入框边界数组
     */
    int[] getDetailTitleFieldBoundsForTest() {
        return toWidgetBounds(titleField);
    }

    /**
     * 返回领取按钮当前是否可见。
     *
     * @return true 表示领取按钮可见
     */
    boolean isClaimButtonVisibleForTest() {
        return claimButton != null && claimButton.visible;
    }

    /**
     * 返回放弃按钮当前是否可见。
     *
     * @return true 表示放弃按钮可见
     */
    boolean isAbandonButtonVisibleForTest() {
        return abandonButton != null && abandonButton.visible;
    }

    /**
     * 返回“指派他人”按钮当前是否可见。
     *
     * @return true 表示指派他人按钮可见
     */
    boolean isAssignOthersButtonVisibleForTest() {
        return assignOthersButton != null && assignOthersButton.visible;
    }

    /**
     * 返回领取按钮的边界，供测试验证纵向布局。
     *
     * @return 领取按钮边界数组
     */
    int[] getClaimButtonBoundsForTest() {
        return toWidgetBounds(claimButton);
    }

    /**
     * 返回放弃按钮的边界，供测试验证纵向布局。
     *
     * @return 放弃按钮边界数组
     */
    int[] getAbandonButtonBoundsForTest() {
        return toWidgetBounds(abandonButton);
    }

    /**
     * 返回“指派他人”按钮的边界，供测试验证纵向布局。
     *
     * @return 指派他人按钮边界数组
     */
    int[] getAssignOthersButtonBoundsForTest() {
        return toWidgetBounds(assignOthersButton);
    }

    /**
     * 打开指定任务的上下文菜单，供同包测试代码断言菜单行为。
     *
     * @param task 目标任务
     */
    void openTaskContextMenuForTest(Task task) {
        openTaskContextMenu(task, 32, 32);
    }

    /**
     * 点击指定索引的上下文菜单项，供同包测试代码驱动菜单动作。
     *
     * @param index 菜单项索引
     */
    void clickContextMenuItemForTest(int index) {
        if (!hasContextMenu() || index < 0 || index >= contextMenuItems.size()) {
            closeTaskContextMenu();
            return;
        }
        ContextMenuItem item = contextMenuItems.get(index);
        if (item.enabled && item.action != null) {
            item.action.run();
        } else {
            closeTaskContextMenu();
        }
    }

    /**
     * 创建任务分配弹窗，供同包测试代码覆盖玩家分配流程。
     *
     * @param task 目标任务
     * @return 任务分配弹窗
     */
    Screen createAssignPlayerScreenForTest(Task task) {
        return new AssignPlayerScreen(this, task);
    }

    /**
     * 返回任务分配弹窗中的候选玩家名称快照，供同包测试代码断言过滤结果。
     *
     * @param screen 任务分配弹窗
     * @return 候选玩家名称快照
     */
    List<String> getAssignablePlayerNamesForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen) || assignPlayerScreen.filteredMembers == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (AssignableMember member : assignPlayerScreen.filteredMembers) {
            if (member != null && member.displayName != null && !member.displayName.isEmpty()) {
                names.add(member.displayName);
            }
        }
        return List.copyOf(names);
    }

    /**
     * 向任务分配弹窗的搜索框写入内容，供同包测试代码驱动候选人过滤。
     *
     * @param screen 任务分配弹窗
     * @param value 搜索关键字
     */
    void setAssignPlayerSearchForTest(Screen screen, String value) {
        if (screen instanceof AssignPlayerScreen assignPlayerScreen && assignPlayerScreen.searchField != null) {
            assignPlayerScreen.searchField.setValue(value == null ? "" : value);
        }
    }

    /**
     * 点击任务分配弹窗中的指定候选人行，供同包测试代码驱动分配动作。
     *
     * @param screen 任务分配弹窗
     * @param rowIndex 候选人行索引
     */
    void clickAssignPlayerRowForTest(Screen screen, int rowIndex) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen) || assignPlayerScreen.playerButtons == null) {
            return;
        }
        if (rowIndex < 0 || rowIndex >= assignPlayerScreen.playerButtons.length) {
            return;
        }
        Button button = assignPlayerScreen.playerButtons[rowIndex];
        if (button != null && button.active && button.visible) {
            button.onPress();
        }
    }

    @Override
    protected void init() {
        super.init();
        openedStorageNamespace = DataPathProvider.getStorageNamespace();

        // Initialize task manager and load tasks from storage
        if (personalTaskManager == null) {
            personalTaskManager = new TaskManager();
            try {
                List<Task> loadedTasks = ClientTaskStorageHelper.loadPersonalTasks(TodoListCommon.getTaskStorage(), this.minecraft);
                for (Task task : loadedTasks) {
                    personalTaskManager.addTask(task);
                }
                TodoConstants.LOGGER.info("Loaded {} tasks from storage", loadedTasks.size());
            } catch (Exception e) {
                TodoConstants.LOGGER.error("Failed to load tasks from storage", e);
            }
        }

        teamTaskManager = ClientBridge.ops().getTeamTaskManager();
        
        // Initialize ProjectManager
        projectManager = TodoListCommon.getProjectManager();
        projectManager.addListener(this);
        teamProjectsEnabled = ClientBridge.ops().isTeamProjectsEnabled();
        if (!teamProjectsEnabled) {
            projectScopeFilter = Project.Scope.PERSONAL;
            if (currentProject != null && currentProject.getScope() == Project.Scope.TEAM) {
                currentProject = null;
            }
            viewMode = ViewMode.PERSONAL;
        }

        applyLastGuiState();
        
        // Verify currentProject is still valid
        if (currentProject != null) {
            Project p = projectManager.getProject(currentProject.getId());
            if (p == null) {
                currentProject = null; // Project was deleted
            } else {
                currentProject = p; // Update reference to fresh object
            }
        }

        if (currentProject == null) {
            currentProject = getPreferredProjectForScope(projectScopeFilter);
        }
        if (currentProject != null) {
            rememberSelectedProject(currentProject);
            projectScopeFilter = currentProject.getScope();
        }
        syncViewStateForCurrentProject();
        
        // Ensure taskManager matches currentProject
        if (currentProject != null) {
            if (currentSpaceMode == SpaceMode.PERSONAL) {
                taskManager = personalTaskManager;
            } else {
                taskManager = teamTaskManager;
            }
        } else {
            taskManager = personalTaskManager;
        }

        syncHudViewForProject(currentProject);
        syncActiveProjectIdWithCurrentProject();
        hasUnsavedChanges = (viewMode == ViewMode.PERSONAL) ? personalHasUnsavedChanges : teamHasUnsavedChanges;

        rebuildUI();
    }

    @Override
    public void removed() {
        super.removed();
        saveLastGuiState();
        if (projectManager != null) {
            projectManager.removeListener(this);
        }
    }

    /**
     * 根据当前项目与旧视图模式同步新的空间与任务视图状态。
     */
    private void syncViewStateForCurrentProject() {
        currentSpaceMode = resolveSpaceMode(currentProject);
        List<TaskViewOption> visibleOptions = buildVisibleViewOptions(currentSpaceMode);
        TaskViewOption resolvedOption = currentSpaceMode == SpaceMode.TEAM && viewMode == ViewMode.PERSONAL
                ? resolveDefaultViewForSpace(currentSpaceMode)
                : resolveTaskViewOptionFromLegacy(viewMode);
        if (!visibleOptions.contains(resolvedOption)) {
            resolvedOption = resolveDefaultViewForSpace(currentSpaceMode);
        }
        currentTaskViewOption = resolvedOption;
        syncLegacyViewModeFromState();
    }

    /**
     * 根据项目解析当前空间模式。
     *
     * @param project 当前项目
     * @return 解析后的空间模式
     */
    private SpaceMode resolveSpaceMode(Project project) {
        if (project == null || project.getScope() == Project.Scope.PERSONAL || !teamProjectsEnabled) {
            return SpaceMode.PERSONAL;
        }
        return SpaceMode.TEAM;
    }

    /**
     * 构建当前空间下可见的任务视图选项。
     *
     * @param spaceMode 当前空间模式
     * @return 当前空间下可见的任务视图选项
     */
    private List<TaskViewOption> buildVisibleViewOptions(SpaceMode spaceMode) {
        if (spaceMode == SpaceMode.TEAM) {
            return List.of(TaskViewOption.UNASSIGNED, TaskViewOption.ALL, TaskViewOption.MY);
        }
        return List.of(TaskViewOption.MY);
    }

    /**
     * 返回当前空间下的默认任务视图选项。
     *
     * @param spaceMode 当前空间模式
     * @return 默认任务视图选项
     */
    private TaskViewOption resolveDefaultViewForSpace(SpaceMode spaceMode) {
        return spaceMode == SpaceMode.TEAM ? TaskViewOption.UNASSIGNED : TaskViewOption.MY;
    }

    /**
     * 将旧的视图模式映射到新的任务视图选项。
     *
     * @param legacyViewMode 旧的视图模式
     * @return 对应的新任务视图选项
     */
    private TaskViewOption resolveTaskViewOptionFromLegacy(ViewMode legacyViewMode) {
        if (legacyViewMode == ViewMode.TEAM_UNASSIGNED) {
            return TaskViewOption.UNASSIGNED;
        }
        if (legacyViewMode == ViewMode.TEAM_ALL) {
            return TaskViewOption.ALL;
        }
        return TaskViewOption.MY;
    }

    /**
     * 将新的空间与任务视图状态反向同步到旧的视图模式。
     */
    private void syncLegacyViewModeFromState() {
        if (currentSpaceMode == SpaceMode.PERSONAL) {
            viewMode = ViewMode.PERSONAL;
            return;
        }
        if (currentTaskViewOption == TaskViewOption.ALL) {
            viewMode = ViewMode.TEAM_ALL;
        } else if (currentTaskViewOption == TaskViewOption.UNASSIGNED) {
            viewMode = ViewMode.TEAM_UNASSIGNED;
        } else {
            viewMode = ViewMode.TEAM_ASSIGNED;
        }
    }

    /**
     * 切换已完成分组展开状态。
     */
    private void toggleCompletedSection() {
        completedExpanded = !completedExpanded;
    }

    private void applyLastGuiState() {
        if (lastGuiState == null || projectManager == null) return;

        projectScopeFilter = lastGuiState.projectScopeFilter == null ? Project.Scope.PERSONAL : lastGuiState.projectScopeFilter;
        if (!teamProjectsEnabled) {
            projectScopeFilter = Project.Scope.PERSONAL;
        }

        if (lastGuiState.projectSearchQuery != null) {
            projectSearchQuery = lastGuiState.projectSearchQuery;
        }
        preferredPersonalProjectId = lastGuiState.lastPersonalProjectId;
        preferredTeamProjectId = lastGuiState.lastTeamProjectId;
        completedExpanded = lastGuiState.completedExpanded;

        if (lastGuiState.viewMode != null) {
            viewMode = lastGuiState.viewMode;
        }
        if (lastGuiState.spaceMode != null) {
            currentSpaceMode = lastGuiState.spaceMode;
        }
        if (lastGuiState.taskViewOption != null) {
            currentTaskViewOption = lastGuiState.taskViewOption;
        }
        if (!teamProjectsEnabled && viewMode != ViewMode.PERSONAL) {
            viewMode = ViewMode.PERSONAL;
        }

        currentPriorityFilter = lastGuiState.currentPriorityFilter;
        if (lastGuiState.currentFilter != null && !lastGuiState.currentFilter.isEmpty()) {
            currentFilter = lastGuiState.currentFilter;
        }
        if (lastGuiState.searchQuery != null) {
            searchQuery = lastGuiState.searchQuery;
        }

        if (lastGuiState.currentProjectId != null && !lastGuiState.currentProjectId.isEmpty()) {
            Project p = projectManager.getProject(lastGuiState.currentProjectId);
            if (p != null && (teamProjectsEnabled || p.getScope() == Project.Scope.PERSONAL)) {
                currentProject = p;
                projectScopeFilter = p.getScope();
                rememberSelectedProject(p);
            }
        }
    }

    private void saveLastGuiState() {
        rememberSelectedProject(currentProject);
        LastGuiState s = new LastGuiState();
        s.projectScopeFilter = projectScopeFilter;
        s.currentProjectId = currentProject == null ? null : currentProject.getId();
        s.viewMode = viewMode;
        s.spaceMode = currentSpaceMode;
        s.taskViewOption = currentTaskViewOption;
        s.completedExpanded = completedExpanded;
        s.currentPriorityFilter = currentPriorityFilter;
        s.currentFilter = currentFilter;
        s.searchQuery = searchQuery;
        s.projectSearchQuery = projectSearchQuery;
        s.lastPersonalProjectId = preferredPersonalProjectId;
        s.lastTeamProjectId = preferredTeamProjectId;
        lastGuiState = s;
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (this.minecraft == null) return;
        this.minecraft.execute(() -> {
            if (type == ProjectManager.ProjectChangeType.CLEARED) {
                switchProject(null);
                return;
            }

            if (project == null) return;

            if (type == ProjectManager.ProjectChangeType.REMOVED) {
                if (!TodoListCommon.isProjectSyncInProgress()) {
                    hardDeleteTasksForDeletedProject(project);
                }
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    switchProject(resolveFallbackProjectAfterRemoval(project));
                } else {
                    refreshAfterProjectMutation();
                }
            } else if (type == ProjectManager.ProjectChangeType.ADDED) {
                if (currentProject == null) {
                    switchProject(resolvePreferredProjectForCurrentScope());
                } else {
                    refreshAfterProjectMutation();
                }
            } else if (type == ProjectManager.ProjectChangeType.UPDATED) {
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    currentProject = project;
                }
                refreshAfterProjectMutation();
            }
        });
    }

    /**
     * 项目增删改后执行轻量刷新，避免整页重新初始化。
     */
    private void refreshAfterProjectMutation() {
        syncActiveProjectIdWithCurrentProject();
        updateProjectList();
        refreshTaskList();
        updateButtonStates();
        updateProjectActionButtons();
    }

    /**
     * 将 activeProjectId 与当前项目状态对齐，避免残留失效项目 ID。
     */
    private void syncActiveProjectIdWithCurrentProject() {
        if (currentProject == null || projectManager == null) {
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            return;
        }
        Project fresh = projectManager.getProject(currentProject.getId());
        if (fresh == null) {
            currentProject = null;
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            return;
        }
        currentProject = fresh;
        ClientBridge.ops().setActiveProjectId(currentProject.getId());
        ClientBridge.ops().sendSetActiveProjectId(currentProject.getId());
        ClientBridge.saveLastActiveProjectId(currentProject.getId());
    }

    /**
     * 当前项目被删除时，按当前 scope 优先选择一个可用项目，允许为空。
     */
    private Project resolveFallbackProjectAfterRemoval(Project removedProject) {
        Project preferred = resolvePreferredProjectForCurrentScope();
        if (preferred != null) {
            return preferred;
        }
        if (removedProject != null) {
            Project.Scope fallbackScope = removedProject.getScope() == Project.Scope.PERSONAL ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            return getPreferredProjectForScope(fallbackScope);
        }
        return null;
    }

    /**
     * 按当前 scope 选择首选项目，不可用时回退到另一 scope。
     */
    private Project resolvePreferredProjectForCurrentScope() {
        Project preferred = getPreferredProjectForScope(projectScopeFilter);
        if (preferred != null) {
            return preferred;
        }
        Project.Scope fallbackScope = projectScopeFilter == Project.Scope.PERSONAL ? Project.Scope.TEAM : Project.Scope.PERSONAL;
        return getPreferredProjectForScope(fallbackScope);
    }

    private void rebuildUI() {
        syncViewStateForCurrentProject();
        selectedTask = null;
        if (currentFilter == null || currentFilter.isEmpty()) {
            currentFilter = "active";
        }
        if (searchQuery == null) {
            searchQuery = "";
        }
        if (projectSearchQuery == null) {
            projectSearchQuery = "";
        }
        baseFilteredTasks = new ArrayList<>();
        filteredTasks = new ArrayList<>();
        this.clearWidgets();

        ModConfig config = ModConfig.getInstance();
        responsiveTier = resolveResponsiveTier(this.width, this.height);
        syncOverlayStateForResponsiveTier();

        layoutMetrics = buildMainLayoutMetrics(config);
        int padding = layoutMetrics.padding;
        int panelGap = layoutMetrics.gap;
        int topBarGap = clampInt(config.getElementSpacing(), 4, 12);
        int topBarY = Math.max(24, padding + 16);
        int topBarHeight = 20;
        int secondRowY = topBarY + topBarHeight + topBarGap;
        int secondRowHeight = 20;
        int inputRowHeight = 20;
        int bottomBarHeight = 20;
        int bottomBarY = this.height - padding - bottomBarHeight;
        int inputRowY = bottomBarY - topBarGap - inputRowHeight;
        int listTop = secondRowY + secondRowHeight + topBarGap;
        int listBottom = inputRowY - Math.max(8, topBarGap + 2);
        int listHeight = Math.max(0, listBottom - listTop);

        LayoutRect sidebarBounds = layoutMetrics.sidebarBounds;
        LayoutRect contentBounds = layoutMetrics.contentBounds;
        LayoutRect detailBounds = layoutMetrics.detailBounds;

        int sidebarTopY = sidebarBounds.y;
        int sidebarWidth = sidebarBounds.width;
        int contentX = contentBounds.x;
        int contentWidth = contentBounds.width;
        int rightPanelX = detailBounds.x;
        int rightPanelWidth = detailBounds.width;

        int overlayToggleWidth = layoutMetrics.sidebarOverlay ? 56 : 0;
        sidebarToggleButton = Button.builder(Component.translatable("gui.todolist.project.sidebar"), b -> toggleSidebarOverlay())
                .bounds(contentX, topBarY, overlayToggleWidth, topBarHeight).build();
        sidebarToggleButton.visible = layoutMetrics.sidebarOverlay;
        sidebarToggleButton.active = layoutMetrics.sidebarOverlay;
        this.addRenderableWidget(sidebarToggleButton);

        int configButtonWidth = Math.max(56, Math.min(84, this.font.width(Component.translatable("gui.todolist.config.title")) + 16));
        configButton = Button.builder(Component.translatable("gui.todolist.config.title"), b -> this.minecraft.setScreen(new ConfigScreen(this)))
                .bounds(contentX + contentWidth - configButtonWidth, topBarY, configButtonWidth, topBarHeight).build();
        this.addRenderableWidget(configButton);

        int filterGap = 4;
        int filtersX = contentX + (layoutMetrics.sidebarOverlay ? overlayToggleWidth + filterGap : 0);
        int filtersY = topBarY;
        int btnH = 20;
        int availableBeforeConfig = Math.max(80, configButton.getX() - filterGap - filtersX);
        int minBtnW = 56;
        int maxBtnW = 120;
        int viewBtnWidth = Math.min(maxBtnW, Math.max(minBtnW, this.font.width(getViewToggleText()) + 16));
        int priorityBtnWidth = Math.min(108, Math.max(minBtnW, this.font.width(getPriorityFilterText()) + 16));
        int statusBtnWidth = Math.min(108, Math.max(minBtnW, this.font.width(getStatusFilterText()) + 16));
        int totalW = viewBtnWidth + priorityBtnWidth + statusBtnWidth + filterGap * 2;
        int guard = 0;
        while (totalW > availableBeforeConfig && guard++ < 200) {
            if (viewBtnWidth >= priorityBtnWidth && viewBtnWidth >= statusBtnWidth && viewBtnWidth > minBtnW) {
                viewBtnWidth -= 4;
            } else if (priorityBtnWidth >= statusBtnWidth && priorityBtnWidth > minBtnW) {
                priorityBtnWidth -= 4;
            } else if (statusBtnWidth > minBtnW) {
                statusBtnWidth -= 4;
            } else {
                break;
            }
            totalW = viewBtnWidth + priorityBtnWidth + statusBtnWidth + filterGap * 2;
        }

        viewToggleButton = Button.builder(getViewToggleText(), b -> {
            if (viewMode == ViewMode.PERSONAL) {
                return;
            }
            if (viewMode == ViewMode.TEAM_UNASSIGNED) {
                switchView(ViewMode.TEAM_ALL);
            } else if (viewMode == ViewMode.TEAM_ALL) {
                switchView(ViewMode.TEAM_ASSIGNED);
            } else {
                switchView(ViewMode.TEAM_UNASSIGNED);
            }
        }).bounds(filtersX, filtersY, viewBtnWidth, btnH).build();
        viewToggleButton.active = viewMode != ViewMode.PERSONAL;
        this.addRenderableWidget(viewToggleButton);

        int priorityBtnX = filtersX + viewBtnWidth + filterGap;
        filterPriorityButton = Button.builder(getPriorityFilterText(), button -> {
            currentPriorityFilter = (currentPriorityFilter + 1) % 4;
            button.setMessage(getPriorityFilterText());
            applyPriorityFilter();
        }).bounds(priorityBtnX, filtersY, priorityBtnWidth, btnH).build();
        this.addRenderableWidget(filterPriorityButton);

        int statusBtnX = priorityBtnX + priorityBtnWidth + filterGap;
        filterStatusButton = Button.builder(getStatusFilterText(), button -> {
            filterTasks("completed".equals(currentFilter) ? "active" : "completed");
        }).bounds(statusBtnX, filtersY, statusBtnWidth, btnH).build();
        this.addRenderableWidget(filterStatusButton);

        searchField = new EditBox(this.font, contentX, secondRowY, contentWidth, secondRowHeight, Component.empty());
        searchField.setHint(Component.translatable("gui.todolist.input.search.placeholder"));
        searchField.setValue(searchQuery);
        this.addRenderableWidget(searchField);

        projectScopeButton = Button.builder(getProjectScopeText(), b -> {
            if (!teamProjectsEnabled) {
                return;
            }
            projectScopeFilter = (projectScopeFilter == Project.Scope.PERSONAL) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            Project targetProject = getPreferredProjectForScope(projectScopeFilter);
            switchProject(targetProject);
        }).bounds(sidebarBounds.x, sidebarTopY, sidebarWidth, 20).build();
        projectScopeButton.active = teamProjectsEnabled;
        this.addRenderableWidget(projectScopeButton);

        projectSearchField = new EditBox(this.font, sidebarBounds.x, sidebarTopY + 28, sidebarWidth, 20, Component.translatable("gui.todolist.project.search"));
        projectSearchField.setHint(Component.translatable("gui.todolist.project.search"));
        projectSearchField.setValue(projectSearchQuery);
        projectSearchField.setResponder(text -> {
            projectSearchQuery = text;
            updateProjectList();
        });
        this.addRenderableWidget(projectSearchField);

        int projectButtonGap = 4;
        int projectButtonWidth = Math.max(28, (sidebarWidth - projectButtonGap * 2) / 3);
        int projectButtonsY = sidebarBounds.y + sidebarBounds.height - 20;
        int projectListY = sidebarTopY + 56;
        int projectListHeight = Math.max(0, projectButtonsY - 8 - projectListY);

        projectListWidget = new ProjectListWidget(this.minecraft, sidebarBounds.x, projectListY, sidebarWidth, projectListHeight);
        updateProjectList();
        projectListWidget.setScrollOffset(savedProjectListScrollOffset);
        projectListWidget.setSelectedProject(currentProject);
        projectListWidget.setOnProjectSelected(this::switchProject);

        addProjectBtn = Button.builder(Component.translatable("gui.todolist.add"), b -> onAddProject())
                .bounds(sidebarBounds.x, projectButtonsY, projectButtonWidth, 20).build();
        this.addRenderableWidget(addProjectBtn);

        editProjectBtn = Button.builder(Component.translatable("gui.todolist.edit"), b -> onProjectSettings())
                .bounds(sidebarBounds.x + projectButtonWidth + projectButtonGap, projectButtonsY, projectButtonWidth, 20).build();
        editProjectBtn.active = currentProject != null;
        this.addRenderableWidget(editProjectBtn);

        int deleteButtonX = sidebarBounds.x + (projectButtonWidth + projectButtonGap) * 2;
        deleteProjectBtn = Button.builder(Component.translatable("gui.todolist.delete"), b -> onProjectDelete())
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, 20).build();
        this.addRenderableWidget(deleteProjectBtn);

        applyJoinProjectBtn = Button.builder(Component.translatable("gui.todolist.project.join.apply"), b -> onApplyJoinProject())
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, 20).build();
        this.addRenderableWidget(applyJoinProjectBtn);
        updateProjectActionButtons();

        taskListWidget = new TaskListWidget(this.minecraft, contentX, listTop, contentWidth, listHeight);
        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        taskListWidget.setTeamAllViewForNonOp(getCurrentRole() == Role.MEMBER && teamAllView);
        taskListWidget.setSections(buildTaskPaneSections());
        taskListWidget.setOnTaskToggleCompletion(task -> {
            if (task.isCompleted()) {
                return;
            }
            boolean wasCompleted = task.isCompleted();
            toggleTaskCompletion(task);
            if (!wasCompleted && task.isCompleted()) {
                addNotification(Component.translatable("message.todolist.completed", task.getTitle()).getString());
                if (config.isEnableSoundEffects() && this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 1.0F);
                }
            }
            refreshTaskList();
        });

        quickAddField = new EditBox(this.font, contentX, inputRowY, contentWidth, inputRowHeight, Component.empty());
        quickAddField.setHint(Component.translatable("gui.todolist.input.title.placeholder"));
        quickAddField.setValue("");
        quickAddField.setMaxLength(100);
        this.addRenderableWidget(quickAddField);

        int rightInnerPadding = 4;
        int rightFieldWidth = Math.max(60, rightPanelWidth - rightInnerPadding * 2);
        int rightPanelTop = detailBounds.y;
        int rightPanelBottom = detailBounds.y + detailBounds.height;
        int rightSectionGap = 6;
        int textH = this.font.lineHeight;
        int assignButtonWidth = rightFieldWidth;
        int assignButtonHeight = 20;
        int assignButtonGap = 4;
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        int assignsX = rightPanelX + rightInnerPadding;
        int closeRowY = rightPanelTop + rightInnerPadding;
        int closeButtonSize = 20;
        int closeButtonX = Math.max(assignsX, rightPanelX + rightPanelWidth - rightInnerPadding - closeButtonSize);

        detailCloseButton = Button.builder(Component.literal("X"), b -> clearSelectedTask())
                .bounds(closeButtonX, closeRowY, closeButtonSize, closeButtonSize)
                .build();
        this.addRenderableWidget(detailCloseButton);

        int titleFieldY = closeRowY + closeButtonSize + rightSectionGap;
        titleField = new EditBox(this.font, assignsX, titleFieldY, rightFieldWidth, 20, Component.empty());
        titleField.setHint(Component.translatable("gui.todolist.input.title.placeholder"));
        titleField.setValue("");
        titleField.setMaxLength(100);
        titleField.setEditable(false);
        this.addRenderableWidget(titleField);

        int teamButtonsTop = rightPanelBottom;
        if (showAssignButtons) {
            int teamButtonsTotalHeight = assignButtonHeight * 3 + assignButtonGap * 2;
            teamButtonsTop = rightPanelBottom - teamButtonsTotalHeight;
        }

        int tagFieldY = teamButtonsTop - rightSectionGap - 20;
        int minTagFieldY = titleFieldY + 20 + textH + rightSectionGap + 32;
        if (tagFieldY < minTagFieldY) {
            tagFieldY = minTagFieldY;
        }
        int descFieldY = titleFieldY + 20 + textH + 2 + rightSectionGap;
        int descFieldBottom = tagFieldY - rightSectionGap - textH - 2;
        int descFieldHeight = Math.max(28, descFieldBottom - descFieldY);
        descField = new MultiLineEditBox(
                this.font,
                assignsX,
                descFieldY,
                rightFieldWidth,
                descFieldHeight,
                Component.translatable("gui.todolist.input.description"),
                Component.translatable("gui.todolist.input.description.placeholder")
        );
        descField.setValue("");
        descField.setCharacterLimit(2000);
        this.addRenderableWidget(descField);

        tagField = new EditBox(this.font, assignsX, tagFieldY, rightFieldWidth, 20, Component.empty());
        tagField.setValue("");
        tagField.setMaxLength(100);
        this.addRenderableWidget(tagField);

        claimButton = Button.builder(Component.translatable("gui.todolist.claim_task"), b -> onClaimTask())
                .bounds(assignsX, teamButtonsTop, assignButtonWidth, assignButtonHeight).build();
        claimButton.active = false;
        this.addRenderableWidget(claimButton);

        abandonButton = Button.builder(Component.translatable("gui.todolist.abandon_task"), b -> onAbandonTask())
                .bounds(assignsX, teamButtonsTop + (assignButtonHeight + assignButtonGap), assignButtonWidth, assignButtonHeight).build();
        abandonButton.active = false;
        this.addRenderableWidget(abandonButton);

        assignOthersButton = Button.builder(Component.translatable("gui.todolist.assign_others"), b -> onAssignOthers())
                .bounds(assignsX, teamButtonsTop + (assignButtonHeight + assignButtonGap) * 2, assignButtonWidth, assignButtonHeight).build();
        assignOthersButton.active = false;
        this.addRenderableWidget(assignOthersButton);

        int saveCancelWidth = Math.max(64, Math.min(90, (contentWidth - 5) / 2));
        int saveCancelGap = 5;
        int totalSaveCancelWidth = saveCancelWidth * 2 + saveCancelGap;
        int saveCancelX = contentX + Math.max(0, (contentWidth - totalSaveCancelWidth) / 2);

        saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> onSaveTasks())
                .bounds(saveCancelX, bottomBarY, saveCancelWidth, 20).build();
        this.addRenderableWidget(saveButton);

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> onCancel())
                .bounds(saveCancelX + saveCancelWidth + saveCancelGap, bottomBarY, saveCancelWidth, 20).build();
        this.addRenderableWidget(cancelButton);

        applyResponsiveWidgetVisibility();

        // Listeners
        titleField.setResponder(this::onDetailTitleChanged);
        descField.setValueListener(text -> {
            onDetailDescriptionChanged(text);
        });
        tagField.setResponder(this::onDetailTagsChanged);
        searchField.setResponder(text -> {
            searchQuery = text == null ? "" : text.trim().toLowerCase();
            applySearchFilter();
        });

        filterTasks(currentFilter);
        syncDetailWidgetsFromState();
        this.setFocused(quickAddField);
        updateButtonStates();
    }

    /**
     * 根据窗口宽高解析当前主界面的响应式档位。
     *
     * @param screenWidth 当前屏幕宽度
     * @param screenHeight 当前屏幕高度
     * @return 解析后的响应式档位
     */
    private ResponsiveTier resolveResponsiveTier(int screenWidth, int screenHeight) {
        ResponsiveTier widthTier = resolveWidthTier(screenWidth);
        ResponsiveTier heightTier = resolveHeightTier(screenHeight);
        return widthTier.ordinal() >= heightTier.ordinal() ? widthTier : heightTier;
    }

    /**
     * 按宽度解析响应式档位。
     *
     * @param screenWidth 当前屏幕宽度
     * @return 宽度对应的档位
     */
    private ResponsiveTier resolveWidthTier(int screenWidth) {
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
     * 按高度解析响应式档位。
     *
     * @param screenHeight 当前屏幕高度
     * @return 高度对应的档位
     */
    private ResponsiveTier resolveHeightTier(int screenHeight) {
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
     * 根据当前档位同步覆盖层展开状态，保证小窗口布局不会挤出屏幕。
     */
    private void syncOverlayStateForResponsiveTier() {
        if (responsiveTier != ResponsiveTier.MINIMAL) {
            sidebarOverlayVisible = false;
        }
        if (responsiveTier == ResponsiveTier.LARGE || responsiveTier == ResponsiveTier.MEDIUM) {
            detailOverlayVisible = false;
        } else if (selectedTask == null) {
            detailOverlayVisible = false;
        } else {
            detailOverlayVisible = true;
        }
    }

    /**
     * 计算当前主界面的三栏布局边界。
     *
     * @param config 当前配置对象
     * @return 主界面布局快照
     */
    private MainLayoutMetrics buildMainLayoutMetrics(ModConfig config) {
        int padding = resolveLayoutPadding(config);
        int gap = responsiveTier == ResponsiveTier.LARGE ? 10 : 8;
        int panelTop = Math.max(24, padding + 16);
        int bottomBarHeight = 20;
        int inputRowHeight = 20;
        int topBarGap = clampInt(config.getElementSpacing(), 4, 12);
        int bottomBarY = this.height - padding - bottomBarHeight;
        int inputRowY = bottomBarY - topBarGap - inputRowHeight;
        int panelBottom = inputRowY + inputRowHeight;
        int panelHeight = Math.max(0, panelBottom - panelTop);

        boolean sidebarOverlay = responsiveTier == ResponsiveTier.MINIMAL;
        boolean detailOverlay = responsiveTier == ResponsiveTier.COMPACT || responsiveTier == ResponsiveTier.MINIMAL;
        boolean sidebarVisible = !sidebarOverlay || sidebarOverlayVisible;
        boolean detailVisible = !detailOverlay || (selectedTask != null && detailOverlayVisible);

        int availableWidth = Math.max(120, this.width - padding * 2);
        int minContentWidth = switch (responsiveTier) {
            case LARGE -> Math.min(180, Math.max(160, availableWidth));
            case MEDIUM -> Math.min(140, Math.max(132, availableWidth));
            case COMPACT -> Math.min(160, Math.max(136, availableWidth));
            case MINIMAL -> Math.min(150, Math.max(120, availableWidth));
        };
        int sidebarWidth = resolveSidebarWidth(config, availableWidth);
        int detailWidth = resolveDetailWidth(availableWidth);

        if (!detailOverlay && !sidebarOverlay) {
            int inlineAvailable = availableWidth - gap * 2;
            int desiredTotal = sidebarWidth + detailWidth + minContentWidth;
            if (desiredTotal > inlineAvailable) {
                int overflow = desiredTotal - inlineAvailable;
                int detailShrink = Math.min(overflow, Math.max(0, detailWidth - 120));
                detailWidth -= detailShrink;
                overflow -= detailShrink;
                if (overflow > 0) {
                    int sidebarShrink = Math.min(overflow, Math.max(0, sidebarWidth - 96));
                    sidebarWidth -= sidebarShrink;
                }
            }
        }

        int inlineSidebarWidth = sidebarOverlay ? 0 : sidebarWidth;
        int inlineDetailWidth = detailOverlay ? 0 : detailWidth;
        int contentWidth = availableWidth - inlineSidebarWidth - inlineDetailWidth;
        if (!sidebarOverlay) {
            contentWidth -= gap;
        }
        if (!detailOverlay) {
            contentWidth -= gap;
        }
        contentWidth = Math.max(Math.min(minContentWidth, availableWidth), contentWidth);

        int contentX = padding + (sidebarOverlay ? 0 : sidebarWidth + gap);
        LayoutRect sidebarBounds = new LayoutRect(padding, panelTop, Math.min(sidebarWidth, availableWidth), panelHeight);
        LayoutRect contentBounds = new LayoutRect(contentX, panelTop, Math.min(contentWidth, availableWidth), panelHeight);

        int detailX = detailOverlay
                ? Math.max(padding, this.width - padding - detailWidth)
                : contentBounds.x + contentBounds.width + gap;
        LayoutRect detailBounds = new LayoutRect(detailX, panelTop, Math.min(detailWidth, availableWidth), panelHeight);

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
     * 计算当前档位下的外层边距。
     *
     * @param config 当前配置对象
     * @return 经过断点修正的边距
     */
    private int resolveLayoutPadding(ModConfig config) {
        int basePadding = clampInt(config.getPadding(), 6, 20);
        return switch (responsiveTier) {
            case LARGE -> Math.max(10, basePadding);
            case MEDIUM -> Math.max(8, Math.min(basePadding, 12));
            case COMPACT -> Math.max(6, Math.min(basePadding, 10));
            case MINIMAL -> Math.max(4, Math.min(basePadding, 8));
        };
    }

    /**
     * 计算当前档位下的项目侧栏宽度。
     *
     * @param config 当前配置对象
     * @param availableWidth 可用宽度
     * @return 适配后的侧栏宽度
     */
    private int resolveSidebarWidth(ModConfig config, int availableWidth) {
        return switch (responsiveTier) {
            case LARGE -> clampInt(config.getProjectSidebarWidth(), 112, Math.min(156, Math.max(112, availableWidth / 3)));
            case MEDIUM -> clampInt(config.getProjectSidebarWidth(), 104, Math.min(136, Math.max(104, availableWidth / 3)));
            case COMPACT -> clampInt(config.getProjectSidebarWidth(), 96, Math.min(124, Math.max(96, availableWidth / 3)));
            case MINIMAL -> clampInt(Math.max(132, availableWidth - 84), 132, Math.max(132, Math.min(200, availableWidth - 8)));
        };
    }

    /**
     * 计算当前档位下的详情区宽度。
     *
     * @param availableWidth 可用宽度
     * @return 适配后的详情区宽度
     */
    private int resolveDetailWidth(int availableWidth) {
        return switch (responsiveTier) {
            case LARGE -> clampInt(150, 132, Math.max(132, Math.min(180, availableWidth / 2)));
            case MEDIUM -> clampInt(132, 120, Math.max(120, Math.min(156, availableWidth / 2)));
            case COMPACT -> clampInt(Math.max(148, availableWidth / 2), 148, Math.max(148, Math.min(180, availableWidth - 24)));
            case MINIMAL -> clampInt(Math.max(156, availableWidth - 72), 156, Math.max(156, availableWidth - 8));
        };
    }

    /**
     * 根据当前布局快照更新覆盖层可见性和相关控件状态。
     */
    private void applyResponsiveWidgetVisibility() {
        if (layoutMetrics == null) {
            return;
        }
        boolean sidebarVisible = layoutMetrics.sidebarVisible;
        boolean detailVisible = layoutMetrics.detailVisible;

        if (projectScopeButton != null) {
            projectScopeButton.visible = sidebarVisible;
            projectScopeButton.active = sidebarVisible && teamProjectsEnabled;
        }
        if (projectSearchField != null) {
            projectSearchField.visible = sidebarVisible;
            projectSearchField.active = sidebarVisible;
        }
        if (addProjectBtn != null) {
            addProjectBtn.visible = sidebarVisible;
            addProjectBtn.active = sidebarVisible;
        }
        if (editProjectBtn != null) {
            editProjectBtn.visible = sidebarVisible;
            if (!sidebarVisible) {
                editProjectBtn.active = false;
            }
        }
        if (deleteProjectBtn != null) {
            if (!sidebarVisible) {
                deleteProjectBtn.visible = false;
                deleteProjectBtn.active = false;
            }
        }
        if (applyJoinProjectBtn != null) {
            if (!sidebarVisible) {
                applyJoinProjectBtn.visible = false;
                applyJoinProjectBtn.active = false;
            }
        }

        if (quickAddField != null) {
            quickAddField.visible = true;
            quickAddField.active = true;
        }
        applyDetailWidgetEditability();
        if (claimButton != null) {
            claimButton.visible = detailVisible && claimButton.visible;
            claimButton.active = detailVisible && claimButton.active;
        }
        if (abandonButton != null) {
            abandonButton.visible = detailVisible && abandonButton.visible;
            abandonButton.active = detailVisible && abandonButton.active;
        }
        if (assignOthersButton != null) {
            assignOthersButton.visible = detailVisible && assignOthersButton.visible;
            assignOthersButton.active = detailVisible && assignOthersButton.active;
        }
        if (sidebarToggleButton != null) {
            sidebarToggleButton.visible = layoutMetrics.sidebarOverlay;
            sidebarToggleButton.active = layoutMetrics.sidebarOverlay;
        }
    }

    /**
     * 切换极小窗口下的项目侧栏覆盖层。
     */
    private void toggleSidebarOverlay() {
        if (responsiveTier != ResponsiveTier.MINIMAL) {
            return;
        }
        sidebarOverlayVisible = !sidebarOverlayVisible;
        layoutMetrics = buildMainLayoutMetrics(ModConfig.getInstance());
        updateProjectActionButtons();
        updateButtonStates();
        applyResponsiveWidgetVisibility();
    }

    /**
     * 返回项目侧栏当前是否可见。
     *
     * @return true 表示项目侧栏可见
     */
    private boolean isSidebarPanelVisible() {
        return layoutMetrics != null && layoutMetrics.sidebarVisible;
    }

    /**
     * 返回详情区当前是否可见。
     *
     * @return true 表示详情区可见
     */
    private boolean isDetailPanelVisible() {
        return layoutMetrics != null && layoutMetrics.detailVisible;
    }

    private void toggleTaskCompletion(Task task) {
        if (!canToggleCompletion(task)) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        taskManager.toggleTaskCompletion(task.getId());
        markUnsaved();
        refreshTaskList();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());

        Component title = hasUnsavedChanges ? Component.translatable("gui.todolist.title.unsaved") : TITLE;
        context.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 10, 0xFFFFFFFF, false);

        renderLayoutPanels(context);
        if (taskListWidget != null) taskListWidget.render(context, mouseX, mouseY, delta);
        if (projectListWidget != null && isSidebarPanelVisible()) {
            projectListWidget.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);

        int color = 0xFFFFFFFF;
        int textH = this.font.lineHeight;

        if (quickAddField != null && quickAddField.visible) {
            int plusY = quickAddField.getY() + (quickAddField.getHeight() - textH) / 2;
            context.drawString(this.font, "+", Math.max(4, quickAddField.getX() - 10), plusY, color, false);
        }
        if (descField != null && descField.visible) {
            int dy = descField.getY() - textH - 2;
            context.drawString(this.font, Component.translatable("gui.todolist.label.description"), descField.getX(), dy, color, false);
        }
        if (tagField != null && tagField.visible) {
            int zy = tagField.getY() - textH - 2;
            context.drawString(this.font, Component.translatable("gui.todolist.label.tags"), tagField.getX(), zy, color, false);
        }
        /*
        if (searchField != null) {
            int sy = searchField.getY() + (searchField.getHeight() - textH) / 2;
            int searchLabelX = searchField.getX() - 40;
            context.drawString(this.font, Component.translatable("gui.todolist.label.search"), searchLabelX, sy, color, false);
        }
        */
        renderTaskContextMenu(context, mouseX, mouseY);
        renderNotifications(context);
    }

    /**
     * 绘制主界面的面板背景，帮助覆盖式侧栏和详情区与主内容区分层显示。
     *
     * @param context 当前绘制上下文
     */
    private void renderLayoutPanels(GuiGraphics context) {
        if (layoutMetrics == null) {
            return;
        }
        renderPanelBackground(context, layoutMetrics.contentBounds, false);
        if (isSidebarPanelVisible()) {
            renderPanelBackground(context, layoutMetrics.sidebarBounds, layoutMetrics.sidebarOverlay);
        }
        if (isDetailPanelVisible()) {
            renderPanelBackground(context, layoutMetrics.detailBounds, layoutMetrics.detailOverlay);
        }
    }

    /**
     * 绘制单个面板背景和描边。
     *
     * @param context 当前绘制上下文
     * @param bounds 面板边界
     * @param overlay 当前面板是否为覆盖式
     */
    private void renderPanelBackground(GuiGraphics context, LayoutRect bounds, boolean overlay) {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        int fillColor = overlay ? 0xD91A1A1A : 0x8C111111;
        int outlineColor = overlay ? 0xCCB8B8B8 : 0x66888888;
        context.fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, fillColor);
        context.renderOutline(bounds.x, bounds.y, bounds.width, bounds.height, outlineColor);
    }

    private void renderNotifications(GuiGraphics context) {
        if (notifications.isEmpty()) return;
        long now = System.currentTimeMillis();

        int boxWidth = 220;
        int boxHeight = 20;
        int startX = Math.max(8, this.width - boxWidth - 8);
        int startY = (searchField != null) ? searchField.getY() : 35;
        int gap = 4;

        List<Notification> active = new ArrayList<>();
        for (Notification n : notifications) {
            if (n.expireAt > now) active.add(n);
        }
        notifications.clear();
        notifications.addAll(active);

        int dy = 0;
        for (Notification n : notifications) {
            int bx1 = startX;
            int by1 = startY + dy;
            int bx2 = bx1 + boxWidth;
            int by2 = by1 + boxHeight;
            context.fill(bx1, by1, bx2, by2, 0xCC000000);
            context.renderOutline(bx1, by1, boxWidth, boxHeight, 0xFFFFFFFF);
            int tx = bx1 + 6;
            int ty = by1 + (boxHeight - this.font.lineHeight) / 2;
            context.drawString(this.font, Component.nullToEmpty(n.text), tx, ty, 0xFFFFFF00, false);
            dy += boxHeight + gap;
        }
    }

    private void onSaveTasks() {
        boolean personalSaved = !personalHasUnsavedChanges;
        boolean teamSaved = !teamHasUnsavedChanges;

        if (personalHasUnsavedChanges) {
            try {
                savePersonalTasks();
                personalSaved = true;
                personalHasUnsavedChanges = false;
            } catch (Exception e) {
                personalSaved = false;
                TodoConstants.LOGGER.error("Failed to save personal tasks", e);
            }
        }

        if (teamHasUnsavedChanges) {
            try {
                saveTeamTasks();
                teamSaved = true;
                teamHasUnsavedChanges = false;
            } catch (Exception e) {
                teamSaved = false;
                TodoConstants.LOGGER.error("Failed to save team tasks", e);
            }
        }

        hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
        if (personalSaved && teamSaved) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.todolist.saved"), false);
            }
            onClose();
            return;
        }

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("message.todolist.save_failed"), false);
        }
    }

    /**
     * 保存当前客户端缓存中的个人任务，并同步到服务端与 HUD。
     *
     * @throws Exception 当个人任务保存失败时抛出异常
     */
    private void savePersonalTasks() throws Exception {
        if (personalTaskManager == null) {
            return;
        }
        List<Task> personalTasks = personalTaskManager.getAllTasks();
        ClientTaskStorageHelper.savePersonalTasks(TodoListCommon.getTaskStorage(), this.minecraft, personalTasks);
        if (ClientBridge.ops() != null) {
            ClientBridge.ops().sendReplaceAllTasks(personalTasks);
        }
        TodoHudRenderer renderer = ClientPlatformAdapter.getHudRenderer();
        if (renderer != null) {
            renderer.forceRefreshTasks();
        }
        TodoConstants.LOGGER.info("Personal tasks saved");
    }

    /**
     * 保存当前客户端缓存中的团队任务，并同步到服务端。
     *
     * @throws Exception 当团队任务保存失败时抛出异常
     */
    private void saveTeamTasks() throws Exception {
        if (teamTaskManager == null) {
            return;
        }
        List<Task> teamTasks = teamTaskManager.getAllTasks();
        if (ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(this.minecraft)) {
            TodoListCommon.getTaskStorage().saveTeamTasks(teamTasks);
        }
        if (ClientBridge.ops() != null) {
            ClientBridge.ops().sendReplaceTeamTasks(teamTasks);
        }
        TodoConstants.LOGGER.info("Team tasks saved");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && hasContextMenu()) {
            closeTaskContextMenu();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (quickAddField != null && quickAddField.isFocused()) {
                if (!isAddTaskAllowedInCurrentView()) {
                    addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
                    return true;
                }
                if (selectedTask != null) {
                    return true;
                }
                onAddTask();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updatePrioritySelection() {
    }

    private boolean isClickInEditArea(double mouseX, double mouseY) {
        if (quickAddField != null && quickAddField.isMouseOver(mouseX, mouseY)) return true;
        if (titleField != null && titleField.visible && titleField.isMouseOver(mouseX, mouseY)) return true;
        if (descField != null && descField.visible && descField.isMouseOver(mouseX, mouseY)) return true;
        if (tagField != null && tagField.visible && tagField.isMouseOver(mouseX, mouseY)) return true;
        if (detailCloseButton != null && detailCloseButton.visible && detailCloseButton.isMouseOver(mouseX, mouseY)) return true;
        if (claimButton != null && claimButton.visible && claimButton.isMouseOver(mouseX, mouseY)) return true;
        if (abandonButton != null && abandonButton.visible && abandonButton.isMouseOver(mouseX, mouseY)) return true;
        if (assignOthersButton != null && assignOthersButton.visible && assignOthersButton.isMouseOver(mouseX, mouseY)) return true;
        if (isInsideContextMenu(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && layoutMetrics != null && layoutMetrics.sidebarOverlay && layoutMetrics.sidebarVisible
                && !layoutMetrics.sidebarBounds.contains(mouseX, mouseY)
                && (sidebarToggleButton == null || !sidebarToggleButton.isMouseOver(mouseX, mouseY))) {
            sidebarOverlayVisible = false;
            layoutMetrics = buildMainLayoutMetrics(ModConfig.getInstance());
            applyResponsiveWidgetVisibility();
        }
        if (taskListWidget != null && taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            closeTaskContextMenu();
            return true;
        }
        if (projectListWidget != null && isSidebarPanelVisible() && projectListWidget.mouseClicked(mouseX, mouseY, button)) {
            closeTaskContextMenu();
            return true;
        }

        if (taskListWidget != null) {
            Task clickedTask = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
            if (clickedTask != null) {
                selectTask(clickedTask);
                if (button == 1) {
                    openTaskContextMenu(clickedTask, (int) mouseX, (int) mouseY);
                } else {
                    closeTaskContextMenu();
                }
                return true;
            }
        }

        if (button == 0
                && titleField != null
                && titleField.visible
                && titleField.isMouseOver(mouseX, mouseY)
                && (detailDraft == null || !detailDraft.titleEditing)) {
            beginDetailTitleEditing();
            return true;
        }

        boolean cleared = false;
        if (button == 0 && selectedTask != null && !isClickInEditArea(mouseX, mouseY)) {
            clearSelectedTask();
            cleared = true;
        }
        if (button == 0 || button == 1) {
            closeTaskContextMenu();
        }

        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        return handled || cleared;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        boolean handled = false;
        if (taskListWidget != null) {
            handled = taskListWidget.mouseScrolled(mouseX, mouseY, 0, amount);
        }
        if (!handled && projectListWidget != null && isSidebarPanelVisible()) {
            handled = projectListWidget.mouseScrolled(mouseX, mouseY, amount);
        }
        if (!handled) {
            handled = super.mouseScrolled(mouseX, mouseY, amount);
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (taskListWidget != null) taskListWidget.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    @Override
    public void onClose() {
        discardPersonalTasksOnCloseIfNeeded();
        if (viewMode != ViewMode.PERSONAL && teamHasUnsavedChanges) {
            ClientBridge.ops().requestTeamSync();
            hasUnsavedChanges = false;
            teamHasUnsavedChanges = false;
        }
        this.minecraft.setScreen(parent);
    }

    /**
     * 在关闭界面时丢弃未保存的个人任务改动，保持“仅保存按钮落盘”语义。
     */
    private void discardPersonalTasksOnCloseIfNeeded() {
        if (!personalHasUnsavedChanges || personalTaskManager == null) {
            return;
        }
        try {
            List<Task> persistedTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(TodoListCommon.getTaskStorage(), this.minecraft);
            personalTaskManager.clearAll();
            for (Task task : persistedTasks) {
                personalTaskManager.addTask(task);
            }
            personalHasUnsavedChanges = false;
            if (viewMode == ViewMode.PERSONAL) {
                hasUnsavedChanges = false;
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to discard unsaved personal tasks on close", e);
        }
    }

    private void onCancel() {
        onClose();
    }

    // Event handlers

    private void onAddTask() {
        if (currentProject == null) {
            addNotification(Component.translatable("message.todolist.select_project_first").getString());
            return;
        }
        if (!isAddTaskAllowedInCurrentView()) {
            addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
            return;
        }
        if (viewMode != ViewMode.PERSONAL) {
            Role role = getCurrentRole();
            ViewScope scope = getCurrentViewScope();
            boolean projectMember = isCurrentPlayerProjectMember();
            boolean allowMemberCreate = currentProject.isAllowMemberCreate();
            if (!PermissionCenter.canPerform(Operation.ADD_TASK, role, new Context(scope, false, false, false, false, false, projectMember, allowMemberCreate))) {
                addNotification(Component.translatable("message.todolist.no_permission_add_team").getString());
                return;
            }
        }
        String title = getFieldValue(quickAddField, "");
        String desc = "";

        if (!title.isEmpty()) {
            Task task = taskManager.addTask(title, desc);
            task.setPriority(selectedPriority);
            if (currentProject != null) {
                task.setProjectId(currentProject.getId());
            }

            if (viewMode != ViewMode.PERSONAL) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    String uuid = this.minecraft.player.getUUID().toString();
                    String name = this.minecraft.player.getName().getString();
                    task.setScope(Task.Scope.TEAM);
                    task.setCreatorUuid(uuid);
                    if (viewMode == ViewMode.TEAM_ASSIGNED) {
                        task.setAssigneeUuid(uuid);
                        task.setAssigneeName(name);
                    }
                } else {
                    task.setScope(Task.Scope.TEAM);
                }
            }

            clearSelectedTask();
            selectedPriority = Task.Priority.MEDIUM;
            if (quickAddField != null) {
                quickAddField.setValue("");
            }

            markUnsaved();
            refreshTaskList();
        }
    }

    private void onDeleteTask() {
        if (selectedTask != null) {
            String id = selectedTask.getId();
            taskManager.deleteTask(id);
            clearSelectedTask();
            closeTaskContextMenu();
            markUnsaved();
            refreshTaskList();
        }
    }

    private void selectTask(Task task) {
        selectedTask = task;
        if (layoutMetrics != null && layoutMetrics.detailOverlay) {
            detailOverlayVisible = true;
            layoutMetrics = buildMainLayoutMetrics(ModConfig.getInstance());
        }
        selectedPriority = task.getPriority();
        detailDraft = createDetailDraft(task);
        taskListWidget.setSelectedTask(task);
        syncDetailWidgetsFromState();
        updateButtonStates();
        applyResponsiveWidgetVisibility();
    }

    private void clearSelectedTask() {
        selectedTask = null;
        detailDraft = null;
        if (layoutMetrics != null && layoutMetrics.detailOverlay) {
            detailOverlayVisible = false;
            layoutMetrics = buildMainLayoutMetrics(ModConfig.getInstance());
        }
        if (taskListWidget != null) {
            taskListWidget.clearSelection();
        }
        syncDetailWidgetsFromState();
        updateButtonStates();
        applyResponsiveWidgetVisibility();
    }

    /**
     * 基于当前选中任务创建一份详情抽屉草稿。
     *
     * @param task 当前选中任务
     * @return 对应的详情草稿；当任务为空时返回 null
     */
    private TaskDetailDraft createDetailDraft(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskDetailDraft(task.getId(), task.getTitle(), task.getDescription(), joinTaskTags(task));
    }

    /**
     * 将当前详情草稿同步到界面控件，避免选择切换时残留旧内容。
     */
    private void syncDetailWidgetsFromState() {
        syncingDetailWidgets = true;
        try {
            if (titleField != null) {
                titleField.setValue(detailDraft == null ? "" : detailDraft.title);
            }
            if (descField != null) {
                descField.setValue(detailDraft == null ? "" : detailDraft.description);
            }
            if (tagField != null) {
                tagField.setValue(detailDraft == null ? "" : detailDraft.tags);
            }
        } finally {
            syncingDetailWidgets = false;
        }
        applyDetailWidgetEditability();
    }

    /**
     * 按当前抽屉状态更新标题、描述和标签的可编辑性。
     */
    private void applyDetailWidgetEditability() {
        boolean detailVisible = isDetailPanelVisible() || (layoutMetrics == null || !layoutMetrics.detailOverlay);
        boolean editable = detailVisible && canEditSelectedTaskDetails();
        if (titleField != null) {
            boolean titleEditing = editable && detailDraft != null && detailDraft.titleEditing;
            titleField.setEditable(titleEditing);
            titleField.active = detailVisible;
            titleField.visible = detailVisible;
        }
        if (descField != null) {
            descField.active = editable;
            descField.visible = detailVisible;
        }
        if (tagField != null) {
            tagField.setEditable(editable);
            tagField.visible = detailVisible;
        }
        if (detailCloseButton != null) {
            boolean showCloseButton = detailVisible && selectedTask != null;
            detailCloseButton.visible = showCloseButton;
            detailCloseButton.active = showCloseButton;
        }
    }

    /**
     * 判断当前选中任务是否允许在详情抽屉中编辑基础字段。
     *
     * @return true 表示当前任务可编辑
     */
    private boolean canEditSelectedTaskDetails() {
        return selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted() && canEditTask(selectedTask);
    }

    /**
     * 让详情标题进入编辑态，供点击标题或测试驱动复用。
     */
    private void beginDetailTitleEditing() {
        if (detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.titleEditing = true;
        applyDetailWidgetEditability();
        if (titleField != null) {
            titleField.setFocused(true);
            this.setFocused(titleField);
        }
    }

    /**
     * 处理详情标题变更，并同步回当前选中任务。
     *
     * @param text 最新标题文本
     */
    private void onDetailTitleChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.title = text == null ? "" : text;
        selectedTask.setTitle(detailDraft.title);
        markUnsaved();
    }

    /**
     * 处理详情描述变更，并同步回当前选中任务。
     *
     * @param text 最新描述文本
     */
    private void onDetailDescriptionChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.description = text == null ? "" : text;
        selectedTask.setDescription(detailDraft.description);
        markUnsaved();
    }

    /**
     * 处理详情标签变更，并同步回当前选中任务。
     *
     * @param text 最新标签文本
     */
    private void onDetailTagsChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.tags = text == null ? "" : text;
        String value = getFieldValue(tagField, "");
        if (value.isEmpty()) {
            selectedTask.clearTags();
        } else {
            List<String> tags = new ArrayList<>();
            for (String part : value.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
            selectedTask.setTags(tags);
        }
        markUnsaved();
    }

    /**
     * 将任务标签拼接成详情抽屉使用的逗号分隔文本。
     *
     * @param task 目标任务
     * @return 逗号分隔后的标签文本
     */
    private String joinTaskTags(Task task) {
        if (task == null || task.getTags() == null || task.getTags().isEmpty()) {
            return "";
        }
        return String.join(",", task.getTags());
    }

    private boolean isSelectedTaskValid() {
        if (selectedTask == null) {
            return false;
        }
        if (currentProject == null) {
            return false;
        }
        String selectedId = selectedTask.getId();
        if (selectedId == null || selectedId.isEmpty()) {
            return false;
        }
        String projectId = currentProject.getId();
        if (!selectedTask.belongsToProject(projectId)) {
            return false;
        }
        for (TaskListWidget.SectionModel section : buildTaskPaneSections()) {
            if (section == null) {
                continue;
            }
            for (Task task : section.getTasks()) {
                if (task != null && selectedId.equals(task.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAddTaskAllowedInCurrentView() {
        return viewMode == ViewMode.PERSONAL || viewMode == ViewMode.TEAM_UNASSIGNED;
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedTask != null;
        boolean isCompleted = hasSelection && selectedTask.isCompleted();
        boolean isAssigned = hasSelection
                && selectedTask.getAssigneeUuid() != null
                && !selectedTask.getAssigneeUuid().isEmpty();
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isAssigneeSelf = hasSelection && isCurrentPlayerAssignee(selectedTask);
        boolean projectMember = isCurrentPlayerProjectMember();
        boolean allowMemberCreate = currentProject != null && currentProject.isAllowMemberCreate();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember, allowMemberCreate);
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        boolean detailVisible = isDetailPanelVisible() || (layoutMetrics == null || !layoutMetrics.detailOverlay);
        if (claimButton != null) {
            claimButton.visible = detailVisible && showAssignButtons;
            boolean canClaim = hasSelection
                    && PermissionCenter.canPerform(Operation.CLAIM_TASK, role, context);
            claimButton.active = detailVisible && showAssignButtons && canClaim;
        }
        if (abandonButton != null) {
            abandonButton.visible = detailVisible && showAssignButtons;
            boolean canAbandon = hasSelection
                    && PermissionCenter.canPerform(Operation.ABANDON_TASK, role, context);
            abandonButton.active = detailVisible && showAssignButtons && canAbandon;
        }
        if (assignOthersButton != null) {
            boolean showAssignOthers = showAssignButtons
                    && PermissionCenter.canPerform(Operation.ASSIGN_OTHERS, role, context);
            assignOthersButton.visible = detailVisible && showAssignOthers;
            boolean canAssignOthers = detailVisible && showAssignOthers && hasSelection;
            assignOthersButton.active = canAssignOthers;
        }
        rebuildContextMenuIfNeeded();
        applyResponsiveWidgetVisibility();
    }

    private void setSelectedPriority(Task.Priority priority) {
        this.selectedPriority = priority;
    }

    private boolean hasContextMenu() {
        return contextMenuTask != null && !contextMenuItems.isEmpty();
    }

    private void openTaskContextMenu(Task task, int mouseX, int mouseY) {
        if (task == null) {
            closeTaskContextMenu();
            return;
        }
        contextMenuTask = task;
        contextMenuItems = buildContextMenuItems(task);
        if (contextMenuItems.isEmpty()) {
            closeTaskContextMenu();
            return;
        }
        int maxTextWidth = 0;
        for (ContextMenuItem item : contextMenuItems) {
            maxTextWidth = Math.max(maxTextWidth, this.font.width(item.text));
        }
        contextMenuWidth = Math.max(90, maxTextWidth + 16);
        int menuHeight = contextMenuItems.size() * contextMenuItemHeight;
        contextMenuX = Math.max(4, Math.min(mouseX, this.width - contextMenuWidth - 4));
        contextMenuY = Math.max(4, Math.min(mouseY, this.height - menuHeight - 4));
    }

    private List<ContextMenuItem> buildContextMenuItems(Task task) {
        List<ContextMenuItem> items = new ArrayList<>();
        if (task == null) {
            return items;
        }
        boolean canEdit = canEditTask(task) && !task.isCompleted();
        boolean canDelete = canDeleteTask(task);
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.high"), canEdit, () -> applyTaskPriority(task, Task.Priority.HIGH)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.medium"), canEdit, () -> applyTaskPriority(task, Task.Priority.MEDIUM)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.low"), canEdit, () -> applyTaskPriority(task, Task.Priority.LOW)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.delete"), canDelete, () -> deleteTaskFromContextMenu(task)));
        return items;
    }

    private void applyTaskPriority(Task task, Task.Priority priority) {
        if (task == null || priority == null || !canEditTask(task) || task.isCompleted()) {
            closeTaskContextMenu();
            return;
        }
        task.setPriority(priority);
        setSelectedPriority(priority);
        markUnsaved();
        refreshTaskList();
        if (taskListWidget != null) {
            taskListWidget.ensureVisible(task);
        }
        ClientBridge.ops().sendUpdateTask(task);
        closeTaskContextMenu();
    }

    private void deleteTaskFromContextMenu(Task task) {
        if (task == null || !canDeleteTask(task)) {
            closeTaskContextMenu();
            return;
        }
        taskManager.deleteTask(task.getId());
        if (selectedTask != null && selectedTask.getId() != null && selectedTask.getId().equals(task.getId())) {
            clearSelectedTask();
        }
        markUnsaved();
        refreshTaskList();
        closeTaskContextMenu();
    }

    private void renderTaskContextMenu(GuiGraphics context, int mouseX, int mouseY) {
        if (!hasContextMenu()) {
            return;
        }
        int menuHeight = contextMenuItems.size() * contextMenuItemHeight;
        context.fill(contextMenuX, contextMenuY, contextMenuX + contextMenuWidth, contextMenuY + menuHeight, 0xEE111111);
        context.renderOutline(contextMenuX, contextMenuY, contextMenuWidth, menuHeight, 0xFFFFFFFF);
        for (int i = 0; i < contextMenuItems.size(); i++) {
            ContextMenuItem item = contextMenuItems.get(i);
            int itemTop = contextMenuY + i * contextMenuItemHeight;
            int itemBottom = itemTop + contextMenuItemHeight;
            boolean hovered = mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                    && mouseY >= itemTop && mouseY < itemBottom;
            if (hovered) {
                context.fill(contextMenuX + 1, itemTop + 1, contextMenuX + contextMenuWidth - 1, itemBottom - 1, 0xFF2A2A2A);
            }
            int textColor = item.enabled ? 0xFFFFFFFF : 0xFF777777;
            int textY = itemTop + (contextMenuItemHeight - this.font.lineHeight) / 2;
            context.drawString(this.font, item.text, contextMenuX + 6, textY, textColor, false);
        }
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (!hasContextMenu()) {
            return false;
        }
        if (button != 0 && button != 1) {
            return false;
        }
        if (!isInsideContextMenu(mouseX, mouseY)) {
            if (button == 0 || button == 1) {
                closeTaskContextMenu();
            }
            return false;
        }
        if (button != 0) {
            return true;
        }
        int index = ((int) mouseY - contextMenuY) / contextMenuItemHeight;
        if (index < 0 || index >= contextMenuItems.size()) {
            closeTaskContextMenu();
            return true;
        }
        ContextMenuItem item = contextMenuItems.get(index);
        if (item.enabled && item.action != null) {
            item.action.run();
        } else {
            closeTaskContextMenu();
        }
        return true;
    }

    private boolean isInsideContextMenu(double mouseX, double mouseY) {
        if (!hasContextMenu()) {
            return false;
        }
        int menuHeight = contextMenuItems.size() * contextMenuItemHeight;
        return mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                && mouseY >= contextMenuY && mouseY < contextMenuY + menuHeight;
    }

    private void closeTaskContextMenu() {
        contextMenuTask = null;
        contextMenuItems = new ArrayList<>();
    }

    private void rebuildContextMenuIfNeeded() {
        if (!hasContextMenu()) {
            return;
        }
        Task menuTask = contextMenuTask;
        if (menuTask == null || filteredTasks == null) {
            closeTaskContextMenu();
            return;
        }
        for (Task task : filteredTasks) {
            if (task != null && task.getId() != null && task.getId().equals(menuTask.getId())) {
                contextMenuItems = buildContextMenuItems(task);
                contextMenuTask = task;
                if (contextMenuItems.isEmpty()) {
                    closeTaskContextMenu();
                }
                return;
            }
        }
        closeTaskContextMenu();
    }

    private boolean isAdminClient() {
        return this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
    }

    private void markUnsaved() {
        hasUnsavedChanges = true;
        if (viewMode == ViewMode.PERSONAL) {
            personalHasUnsavedChanges = true;
        } else {
            teamHasUnsavedChanges = true;
        }
    }

    public static boolean hasPersonalUnsavedChanges() {
        return personalHasUnsavedChanges;
    }

    private boolean canEditTask(Task task) {
        if (task == null) {
            return false;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isCompleted = task.isCompleted();
        boolean isAssigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean isAssigneeSelf = isCurrentPlayerAssignee(task);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember);
        return PermissionCenter.canPerform(Operation.EDIT_TASK, role, context);
    }

    private boolean canDeleteTask(Task task) {
        if (task == null) {
            return false;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isCompleted = task.isCompleted();
        boolean isAssigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean isAssigneeSelf = isCurrentPlayerAssignee(task);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember);
        return PermissionCenter.canPerform(Operation.DELETE_TASK, role, context);
    }

    private boolean canToggleCompletion(Task task) {
        if (task == null) {
            return false;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean isCompleted = task.isCompleted();
        boolean isAssigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean isAssigneeSelf = isCurrentPlayerAssignee(task);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember);
        return PermissionCenter.canPerform(Operation.TOGGLE_COMPLETE, role, context);
    }

    private boolean isCurrentPlayerAssignee(Task task) {
        if (task == null || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        String assignee = task.getAssigneeUuid();
        return assignee != null && assignee.equals(uuid);
    }

    private Role getCurrentRole() {
        if (isAdminClient()) {
            return Role.OP;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            return Role.MEMBER;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return Role.MEMBER;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        if (uuid.equals(currentProject.getOwnerUuid())) {
            return Role.PROJECT_MANAGER;
        }
        Project.ProjectRole projectRole = currentProject.getMemberRole(uuid);
        if (projectRole == Project.ProjectRole.LEAD) {
            return Role.LEAD;
        }
        return Role.MEMBER;
    }

    private boolean isCurrentPlayerProjectMember() {
        if (isAdminClient()) {
            return true;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return true;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        if (uuid.equals(currentProject.getOwnerUuid())) {
            return true;
        }
        return currentProject.getMemberRole(uuid) != null;
    }

    private ViewScope getCurrentViewScope() {
        if (viewMode == ViewMode.PERSONAL) {
            return ViewScope.PERSONAL;
        }
        if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            return ViewScope.TEAM_UNASSIGNED;
        }
        if (viewMode == ViewMode.TEAM_ASSIGNED) {
            return ViewScope.TEAM_ASSIGNED;
        }
        return ViewScope.TEAM_ALL;
    }

    private void onClaimTask() {
        if (selectedTask == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        String assignee = selectedTask.getAssigneeUuid();
        if (assignee != null && !assignee.isEmpty() && !assignee.equals(uuid)) {
            addNotification(Component.translatable("message.todolist.already_assigned").getString());
            return;
        }
        selectedTask.setAssigneeUuid(uuid);
        selectedTask.setAssigneeName(this.minecraft.player.getName().getString());
        addNotification(Component.translatable("message.todolist.assigned_to_me").getString());
        markUnsaved();
        refreshTaskList();
    }

    // Helper for rendering labels
    private class TextLabelWidget extends net.minecraft.client.gui.components.AbstractWidget {
        private final Component text;
        private final int color;
        
        public TextLabelWidget(int x, int y, Component text, int color) {
            super(x, y, minecraft.font.width(text), minecraft.font.lineHeight, text);
            this.text = text;
            this.color = color;
            this.active = false; // Not clickable
        }

        @Override
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            context.drawString(minecraft.font, text, getX(), getY(), color, false);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
        }
    }
    
    private void updateProjectList() {
        if (projectListWidget == null) return;
        List<Project> all = new ArrayList<>();
        all.addAll(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
        all.addAll(projectManager.getProjectsByScope(Project.Scope.TEAM));
        
        Project defaultProject = null;
        for (Project p : all) {
            if (p == null) continue;
            if (p.getScope() != projectScopeFilter) continue;
            if (projectScopeFilter == Project.Scope.PERSONAL && p.isDefaultPersonalProject()) {
                defaultProject = p;
                break;
            }
            if (projectScopeFilter == Project.Scope.TEAM && p.isDefaultTeamProject()) {
                defaultProject = p;
                break;
            }
        }

        List<Project> filtered = new ArrayList<>();
        String q = projectSearchQuery.toLowerCase().trim();
        
        for (Project p : all) {
            // Scope filter
            if (p.getScope() != projectScopeFilter) continue;
            
            // Name filter
            String searchableName = ProjectNameFormatter.toDisplayText(p).getString().toLowerCase();
            if (!q.isEmpty() && !searchableName.contains(q)) continue;
            
            filtered.add(p);
        }

        if (defaultProject != null) {
            boolean exists = false;
            String id = defaultProject.getId();
            for (Project p : filtered) {
                if (p != null && id != null && id.equals(p.getId())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                filtered.add(0, defaultProject);
            }
        }
        
        projectListWidget.setProjects(filtered);
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();
    }

    private void updateProjectActionButtons() {
        if (editProjectBtn == null || deleteProjectBtn == null || applyJoinProjectBtn == null) {
            return;
        }
        if (currentProject == null) {
            editProjectBtn.active = false;
            editProjectBtn.setMessage(Component.translatable("gui.todolist.edit"));
            deleteProjectBtn.visible = true;
            deleteProjectBtn.active = false;
            applyJoinProjectBtn.visible = false;
            applyJoinProjectBtn.active = false;
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            editProjectBtn.active = true;
            editProjectBtn.setMessage(Component.translatable("gui.todolist.edit"));
            deleteProjectBtn.visible = true;
            deleteProjectBtn.active = canDeleteCurrentProject();
            applyJoinProjectBtn.visible = false;
            applyJoinProjectBtn.active = false;
            return;
        }
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        boolean canEdit = PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx);
        editProjectBtn.active = true;
        editProjectBtn.setMessage(Component.translatable(canEdit ? "gui.todolist.edit" : "gui.todolist.project.view"));
        boolean member = isCurrentPlayerProjectMember();
        if (!member) {
            deleteProjectBtn.visible = false;
            deleteProjectBtn.active = false;
            applyJoinProjectBtn.visible = true;
            applyJoinProjectBtn.active = true;
            return;
        }
        applyJoinProjectBtn.visible = false;
        applyJoinProjectBtn.active = false;
        deleteProjectBtn.visible = true;
        deleteProjectBtn.active = canDeleteCurrentProject();
    }
    
    private Component getProjectScopeText() {
        if (projectScopeFilter == Project.Scope.PERSONAL) {
            return Component.translatable("gui.todolist.project.toggle.personal");
        } else {
            return Component.translatable("gui.todolist.project.toggle.team");
        }
    }
    private void onAbandonTask() {
        if (selectedTask == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean completed = selectedTask.isCompleted();
        String assignee = selectedTask.getAssigneeUuid();
        boolean assigned = assignee != null && !assignee.isEmpty();
        boolean assigneeSelf = isCurrentPlayerAssignee(selectedTask);
        boolean projectMember = isCurrentPlayerProjectMember();
        Context ctx = new Context(scope, completed, assigned, assigneeSelf, false, false, projectMember);
        if (!PermissionCenter.canPerform(Operation.ABANDON_TASK, role, ctx)) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        selectedTask.setAssigneeUuid(null);
        selectedTask.setAssigneeName(null);
        addNotification(Component.translatable("message.todolist.abandoned_task").getString());
        markUnsaved();
        refreshTaskList();
    }

    private Component getPriorityFilterText() {
        // String labelKey = "gui.todolist.label.priority";
        String valueKey;
        switch (currentPriorityFilter) {
            case 1: 
                valueKey = "gui.todolist.filter.priority_high"; 
                break;
            case 2: 
                valueKey = "gui.todolist.filter.priority_medium"; 
                break;
            case 3: 
                valueKey = "gui.todolist.filter.priority_low"; 
                break;
            default: 
                valueKey = "gui.todolist.all"; 
                break;
        }
        return Component.translatable(valueKey);
    }

    private Component getStatusFilterText() {
        // MutableComponent label = Component.translatable("gui.todolist.label.status");
        Component value = "completed".equals(currentFilter) ? Component.translatable("gui.todolist.completed") : Component.translatable("gui.todolist.active");
        return value;
    }

    private Component getViewToggleText() {
        // MutableComponent label = Component.translatable("gui.todolist.label.view");
        String key;
        if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            key = "gui.todolist.view.team_unassigned";
        } else if (viewMode == ViewMode.TEAM_ALL) {
            key = "gui.todolist.view.team_all";
        } else if (viewMode == ViewMode.TEAM_ASSIGNED) {
            key = "gui.todolist.view.team_assigned";
        } else {
            key = "gui.todolist.view.personal";
        }
        return Component.translatable(key);
    }

    private void applyPriorityFilter() {
        filterTasks(currentFilter);
    }

    private void onAssignOthers() {
        if (selectedTask == null || this.minecraft == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        if (!canEditTask(selectedTask)) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        this.minecraft.setScreen(new AssignPlayerScreen(this, selectedTask));
    }

    /**
     * 解析团队项目成员在当前客户端上的显示名称，优先使用缓存名称，并在成员在线时刷新为最新玩家名。
     *
     * @param project 当前团队项目
     * @param memberUuid 成员 UUID
     * @return 可用于界面展示的成员名称；若没有缓存名称则回退为 UUID
     */
    private String resolveProjectMemberDisplayName(Project project, String memberUuid) {
        if (project == null || memberUuid == null || memberUuid.isBlank()) {
            return "";
        }
        String displayName = project.getMemberName(memberUuid);
        if (displayName != null && !displayName.isBlank()) {
            displayName = displayName.trim();
        }
        try {
            if (minecraft != null && minecraft.getConnection() != null) {
                net.minecraft.client.multiplayer.PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(UUID.fromString(memberUuid));
                if (playerInfo != null && playerInfo.getProfile() != null) {
                    String onlineName = playerInfo.getProfile().getName();
                    if (onlineName != null && !onlineName.isBlank()) {
                        displayName = onlineName;
                        project.setMemberName(memberUuid, onlineName);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略非法 UUID 或临时连接状态异常，继续使用缓存名称或 UUID 兜底。
        }
        if (displayName == null || displayName.isBlank()) {
            return memberUuid;
        }
        return displayName;
    }

    /**
     * 任务指派弹窗中的成员候选项，保存成员 UUID 与当前显示名称。
     */
    private static final class AssignableMember {
        private final String uuid;
        private final String displayName;

        /**
         * 创建一个可指派成员候选项。
         *
         * @param uuid 成员 UUID
         * @param displayName 成员显示名称
         */
        private AssignableMember(String uuid, String displayName) {
            this.uuid = uuid;
            this.displayName = displayName;
        }
    }

    private void filterTasks(String filter) {
        // If filter is "active" or "completed" or "all", update currentFilter (Tab)
        if (filter.equals("active") || filter.equals("completed") || filter.equals("all")) {
            currentFilter = filter;
        }
        
        List<Task> result = new ArrayList<>();
        // 1. First apply Tab filter
        switch (currentFilter) {
            case "all":
                result = taskManager.getAllTasks();
                break;
            case "active":
                result = taskManager.getIncompleteTasks();
                break;
            case "completed":
                result = taskManager.getCompletedTasks();
                break;
            default:
                // Fallback
                result = taskManager.getIncompleteTasks();
                break;
        }
        
        // 2. Apply Priority Filter
        if (currentPriorityFilter != 0) {
            Task.Priority targetPriority = Task.Priority.MEDIUM;
            if (currentPriorityFilter == 1) targetPriority = Task.Priority.HIGH;
            else if (currentPriorityFilter == 2) targetPriority = Task.Priority.MEDIUM;
            else if (currentPriorityFilter == 3) targetPriority = Task.Priority.LOW;
            
            List<Task> priorityFiltered = new ArrayList<>();
            for (Task t : result) {
                if (t.getPriority() == targetPriority) {
                    priorityFiltered.add(t);
                }
            }
            result = priorityFiltered;
        }
        
        // 3. Apply View Scope (Assigned/Unassigned)
        baseFilteredTasks = applyAssignedFilterIfNeeded(result);
        
        // 4. Apply Search
        applySearchFilter();
        if (selectedTask != null && !isSelectedTaskValid()) {
            clearSelectedTask();
        }
        if (filterStatusButton != null) {
            filterStatusButton.setMessage(getStatusFilterText());
        }
        if (viewToggleButton != null) {
            viewToggleButton.setMessage(getViewToggleText());
        }
    }

    private void refreshTaskList() {
        filterTasks(currentFilter);
    }

    private void applySearchFilter() {
        if (baseFilteredTasks == null) {
            baseFilteredTasks = new ArrayList<>();
        }
        if (searchQuery == null || searchQuery.isEmpty()) {
            filteredTasks = new ArrayList<>(baseFilteredTasks);
        } else {
            String q = searchQuery;
            List<Task> result = new ArrayList<>();
            for (Task task : baseFilteredTasks) {
                String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase();
                String desc = task.getDescription() == null ? "" : task.getDescription().toLowerCase();
                boolean matchText = title.contains(q) || desc.contains(q);
                boolean matchTag = false;
                for (String tag : task.getTags()) {
                    if (tag != null && tag.toLowerCase().contains(q)) {
                        matchTag = true;
                        break;
                    }
                }
                if (matchText || matchTag) {
                    result.add(task);
                }
            }
            filteredTasks = result;
        }
        if (taskListWidget != null) {
            taskListWidget.setSections(buildTaskPaneSections());
        }
    }

    /**
     * 构建当前任务区分段数据，为“未完成 + 已完成折叠分组”提供基础模型。
     *
     * @return 当前任务区分段列表
     */
    private List<TaskListWidget.SectionModel> buildTaskPaneSections() {
        List<TaskListWidget.SectionModel> sections = new ArrayList<>();
        List<Task> activeTasks = filteredTasks == null ? List.of() : List.copyOf(filteredTasks);

        if ("completed".equals(currentFilter)) {
            sections.add(new TaskListWidget.SectionModel(
                    "completed",
                    Component.translatable("gui.todolist.completed").getString(),
                    activeTasks,
                    false,
                    true
            ));
            return sections;
        }

        sections.add(new TaskListWidget.SectionModel(
                "active",
                Component.translatable("gui.todolist.active").getString(),
                activeTasks,
                false,
                true
        ));

        List<Task> completedTasks = buildCompletedTasksForCurrentView();
        sections.add(new TaskListWidget.SectionModel(
                "completed",
                Component.translatable("gui.todolist.completed").getString() + " " + completedTasks.size() + " 项",
                completedTasks,
                true,
                completedExpanded
        ));
        return sections;
    }

    /**
     * 构建当前项目和当前视图下的已完成任务列表，用于主任务区底部折叠分组。
     *
     * @return 当前可见的已完成任务列表
     */
    private List<Task> buildCompletedTasksForCurrentView() {
        if (taskManager == null || currentProject == null) {
            return List.of();
        }
        List<Task> completedTasks = taskManager.getCompletedTasks();
        List<Task> priorityFiltered = applyPriorityFilterToTasks(completedTasks);
        List<Task> scopedTasks = applyAssignedFilterIfNeeded(priorityFiltered);
        return applySearchQueryToTasks(scopedTasks);
    }

    /**
     * 对指定任务列表复用当前优先级筛选条件。
     *
     * @param source 待筛选的任务列表
     * @return 优先级筛选后的任务列表
     */
    private List<Task> applyPriorityFilterToTasks(List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        if (currentPriorityFilter == 0) {
            return new ArrayList<>(input);
        }
        Task.Priority targetPriority = Task.Priority.MEDIUM;
        if (currentPriorityFilter == 1) {
            targetPriority = Task.Priority.HIGH;
        } else if (currentPriorityFilter == 2) {
            targetPriority = Task.Priority.MEDIUM;
        } else if (currentPriorityFilter == 3) {
            targetPriority = Task.Priority.LOW;
        }
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task != null && task.getPriority() == targetPriority) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 对指定任务列表复用当前搜索关键字。
     *
     * @param source 待筛选的任务列表
     * @return 搜索筛选后的任务列表
     */
    private List<Task> applySearchQueryToTasks(List<Task> source) {
        List<Task> input = source == null ? List.of() : source;
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new ArrayList<>(input);
        }
        String q = searchQuery;
        List<Task> result = new ArrayList<>();
        for (Task task : input) {
            if (task == null) {
                continue;
            }
            String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase();
            String desc = task.getDescription() == null ? "" : task.getDescription().toLowerCase();
            boolean matchText = title.contains(q) || desc.contains(q);
            boolean matchTag = false;
            for (String tag : task.getTags()) {
                if (tag != null && tag.toLowerCase().contains(q)) {
                    matchTag = true;
                    break;
                }
            }
            if (matchText || matchTag) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 将控件转换为统一的边界数组，供测试代码读取布局信息。
     *
     * @param widget 目标控件
     * @return 依次包含 x、y、width、height 的边界数组
     */
    private int[] toWidgetBounds(net.minecraft.client.gui.components.AbstractWidget widget) {
        if (widget == null) {
            return new int[] {0, 0, 0, 0};
        }
        return new int[] {widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()};
    }

    private String getFieldValue(EditBox field, String hint) {
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        if (raw.isEmpty()) return "";
        if (!hint.isEmpty() && raw.equals(hint)) return "";
        return raw;
    }

    private String getFieldValue(MultiLineEditBox field, String hint) {
        String raw = field.getValue() == null ? "" : field.getValue().trim();
        if (raw.isEmpty()) return "";
        if (!hint.isEmpty() && raw.equals(hint)) return "";
        return raw;
    }

    private ViewMode parseHudViewMode(String raw) {
        if (raw == null) {
            return ViewMode.PERSONAL;
        }
        String v = raw.trim().toUpperCase();
        if ("TEAM_UNASSIGNED".equals(v)) return ViewMode.TEAM_UNASSIGNED;
        if ("TEAM_ALL".equals(v)) return ViewMode.TEAM_ALL;
        if ("TEAM_ASSIGNED".equals(v)) return ViewMode.TEAM_ASSIGNED;
        return ViewMode.PERSONAL;
    }

    private void addNotification(String text) {
        long now = System.currentTimeMillis();
        notifications.add(new Notification(text, now + 2000));
    }

    private static class Notification {
        final String text;
        final long expireAt;

        Notification(String text, long expireAt) {
            this.text = text;
            this.expireAt = expireAt;
        }
    }

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
    
    private List<Task> applyAssignedFilterIfNeeded(List<Task> tasks) {
        if (currentProject == null) {
            return new ArrayList<>();
        }
        List<Task> projectFiltered = new ArrayList<>();
        for (Task t : tasks) {
            if (t.belongsToProject(currentProject.getId())) {
                projectFiltered.add(t);
            }
        }
        
        List<Task> result = new ArrayList<>();
        if (viewMode == ViewMode.TEAM_ASSIGNED) {
            if (this.minecraft != null && this.minecraft.player != null) {
                String myUuid = this.minecraft.player.getUUID().toString();
                for (Task t : projectFiltered) {
                    if (myUuid.equals(t.getAssigneeUuid())) {
                        result.add(t);
                    }
                }
            }
            return result;
        } else if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            for (Task t : projectFiltered) {
                String assignee = t.getAssigneeUuid();
                if (assignee == null || assignee.isEmpty()) {
                    result.add(t);
                }
            }
            return result;
        } else if (viewMode == ViewMode.TEAM_ALL) {
            for (Task t : projectFiltered) {
                String assignee = t.getAssigneeUuid();
                if (assignee != null && !assignee.isEmpty()) {
                    result.add(t);
                }
            }
            return result;
        }
        return projectFiltered;
    }
    
    private boolean isTrueSingleplayer() {
        return false;
    }
    
    private void switchView(ViewMode mode) {
        this.viewMode = mode;
        syncViewStateForCurrentProject();
        clearSelectedTask();
        ModConfig config = ModConfig.getInstance();
        if (this.viewMode == ViewMode.PERSONAL) {
            config.setHudDefaultView("PERSONAL");
        } else {
            config.setHudDefaultView(this.viewMode.name());
        }
        updateViewButtonsState();
        rebuildUI();
    }

    private void syncHudViewForProject(Project project) {
        ModConfig config = ModConfig.getInstance();
        currentSpaceMode = resolveSpaceMode(project);
        if (currentSpaceMode == SpaceMode.PERSONAL) {
            currentTaskViewOption = TaskViewOption.MY;
            syncLegacyViewModeFromState();
            config.setHudDefaultView("PERSONAL");
            return;
        }
        ViewMode configView = parseHudViewMode(config.getHudDefaultView());
        TaskViewOption resolvedOption = configView == ViewMode.PERSONAL
                ? resolveDefaultViewForSpace(currentSpaceMode)
                : resolveTaskViewOptionFromLegacy(configView);
        if (!buildVisibleViewOptions(currentSpaceMode).contains(resolvedOption)) {
            resolvedOption = resolveDefaultViewForSpace(currentSpaceMode);
        }
        currentTaskViewOption = resolvedOption;
        syncLegacyViewModeFromState();
        config.setHudDefaultView(this.viewMode.name());
    }
    
    private void updateViewButtonsState() {
        if (viewToggleButton != null) {
            viewToggleButton.active = viewMode != ViewMode.PERSONAL;
            viewToggleButton.setMessage(getViewToggleText());
        }
    }
    
    private void switchProject(Project project) {
        if (projectListWidget != null) {
            savedProjectListScrollOffset = projectListWidget.getScrollOffset();
        }
        this.selectedTask = null;
        this.detailOverlayVisible = false;
        this.sidebarOverlayVisible = false;
        this.currentProject = project;
        rememberSelectedProject(project);

        if (project == null) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            syncLegacyViewModeFromState();
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            syncHudViewForProject(null);
            rebuildUI();
            return;
        }

        if (!teamProjectsEnabled && project.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        projectScopeFilter = project.getScope();
        ClientBridge.ops().setActiveProjectId(project.getId());
        ClientBridge.ops().sendSetActiveProjectId(project.getId());
        ClientBridge.saveLastActiveProjectId(project.getId());
        
        if (project.getScope() == Project.Scope.PERSONAL) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            syncLegacyViewModeFromState();
        } else {
            this.taskManager = this.teamTaskManager;
            currentSpaceMode = SpaceMode.TEAM;
            if (currentTaskViewOption == TaskViewOption.MY && viewMode == ViewMode.PERSONAL) {
                currentTaskViewOption = TaskViewOption.UNASSIGNED;
            }
            syncLegacyViewModeFromState();
        }
        syncHudViewForProject(project);
        
        rebuildUI();
    }

    /**
     * 当项目删除时，立即从本地任务管理器硬删除关联任务。
     */
    private void hardDeleteTasksForDeletedProject(Project deletedProject) {
        if (deletedProject == null || deletedProject.getId() == null || deletedProject.getId().isEmpty()) {
            return;
        }
        String deletedProjectId = deletedProject.getId();
        hardDeleteProjectTasksInManager(personalTaskManager, deletedProjectId);
        hardDeleteProjectTasksInManager(teamTaskManager, deletedProjectId);
    }

    /**
     * 在指定任务管理器中删除目标项目下全部任务。
     */
    private void hardDeleteProjectTasksInManager(TaskManager manager, String deletedProjectId) {
        if (manager == null || deletedProjectId == null || deletedProjectId.isEmpty()) {
            return;
        }
        manager.deleteTasksByProjectId(deletedProjectId);
    }

    private void rememberSelectedProject(Project project) {
        if (project == null || project.getId() == null || project.getId().isEmpty()) {
            return;
        }
        if (project.getScope() == Project.Scope.TEAM) {
            preferredTeamProjectId = project.getId();
        } else {
            preferredPersonalProjectId = project.getId();
        }
    }

    private Project getPreferredProjectForScope(Project.Scope scope) {
        if (scope == null) {
            return null;
        }
        if (scope == Project.Scope.TEAM && !teamProjectsEnabled) {
            return null;
        }
        String preferredId = scope == Project.Scope.TEAM ? preferredTeamProjectId : preferredPersonalProjectId;
        if (preferredId != null && !preferredId.isEmpty()) {
            Project preferred = projectManager.getProject(preferredId);
            if (preferred != null && preferred.getScope() == scope) {
                return preferred;
            }
        }
        List<Project> projects = projectManager.getProjectsByScope(scope);
        if (projects.isEmpty()) {
            return null;
        }
        for (Project p : projects) {
            if (scope == Project.Scope.PERSONAL && p.isDefaultPersonalProject()) {
                return p;
            }
            if (scope == Project.Scope.TEAM && p.isDefaultTeamProject()) {
                return p;
            }
        }
        return projects.get(0);
    }
    
    private void onAddProject() {
        Project.Scope defaultScope = Project.Scope.PERSONAL;
        if (currentProject != null) {
            defaultScope = currentProject.getScope();
        } else if (projectScopeFilter != null) {
            defaultScope = projectScopeFilter;
        }
        if (defaultScope == Project.Scope.TEAM && !ClientBridge.ops().isTeamProjectsEnabled()) {
            defaultScope = Project.Scope.PERSONAL;
        }
        minecraft.setScreen(new AddProjectScreen(this, defaultScope));
    }
    
    private void onProjectSettings() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        minecraft.setScreen(new ProjectSettingsScreen(this, currentProject));
    }

    private boolean canDeleteCurrentProject() {
        if (currentProject == null || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (currentProject.isDefaultPersonalProject() || currentProject.isDefaultTeamProject()) {
            return false;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        if (currentProject.getScope() == Project.Scope.PERSONAL) {
            String owner = currentProject.getOwnerUuid();
            return owner == null || owner.isEmpty() || owner.equals(uuid);
        }
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        return PermissionCenter.canPerform(Operation.DELETE_PROJECT, role, ctx);
    }

    private void onApplyJoinProject() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            return;
        }
        if (isCurrentPlayerProjectMember()) {
            return;
        }
        ClientBridge.ops().sendRequestJoinProject(currentProject.getId());
        addNotification(Component.translatable("message.todolist.project.join.sent").getString());
    }

    private void onProjectDelete() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (!canDeleteCurrentProject()) {
            addNotification(Component.translatable("message.todolist.no_permission_delete_project").getString());
            return;
        }
        String projectName = ProjectNameFormatter.toDisplayText(currentProject).getString();
        Component message = Component.translatable("gui.todolist.project.delete_confirm.message", projectName);
        minecraft.setScreen(new ConfirmDeleteProjectScreen(this, message, () -> {
            ClientBridge.ops().sendDeleteProject(currentProject.getId());
        }));
    }

    private class AssignPlayerScreen extends Screen {
        private final TodoScreen parentScreen;
        private final Task targetTask;
        private EditBox searchField;
        private List<AssignableMember> allMembers;
        private List<AssignableMember> filteredMembers;
        private Button[] playerButtons;
        private int scrollOffset;
        private int visibleRows;
        private int listX;
        private int listY;
        private int listWidth;
        private int listHeight;
        private int rowHeight;

        protected AssignPlayerScreen(TodoScreen parentScreen, Task targetTask) {
            super(Component.translatable("gui.todolist.assign_others"));
            this.parentScreen = parentScreen;
            this.targetTask = targetTask;
        }

        @Override
        protected void init() {
            super.init();
            if (minecraft == null) {
                return;
            }
            int guiWidth = Math.max(200, Math.min(320, this.width - 20));
            int x = (this.width - guiWidth) / 2;
            int topY = Math.max(20, this.height / 6);
            int searchHeight = 20;
            int cancelButtonHeight = 20;
            int buttonGap = 10;
            rowHeight = 22;
            int availableListHeight = this.height - topY - searchHeight - 6 - buttonGap - cancelButtonHeight;
            int maxRowsByHeight = Math.max(1, availableListHeight / rowHeight);
            visibleRows = Math.min(8, maxRowsByHeight);
            listWidth = guiWidth;
            listX = x;
            listY = topY + searchHeight + 6;
            listHeight = visibleRows * rowHeight;

            searchField = new EditBox(this.font, x, topY, guiWidth, searchHeight, Component.empty());
            searchField.setHint(Component.translatable("gui.todolist.member.name"));
            searchField.setValue("");
            this.addRenderableWidget(searchField);

            allMembers = collectAssignableMembers();
            filteredMembers = new ArrayList<>();

            playerButtons = new Button[visibleRows];
            for (int i = 0; i < visibleRows; i++) {
                int btnY = listY + i * rowHeight;
                final int rowIndex = i;
                Button btn = Button.builder(Component.empty(), b -> {
                    AssignableMember entry = getMemberForRow(rowIndex);
                    if (entry != null) {
                        applyAssignTo(entry.uuid, entry.displayName);
                    }
                }).bounds(x, btnY, guiWidth, 20).build();
                btn.active = false;
                btn.visible = false;
                this.addRenderableWidget(btn);
                playerButtons[i] = btn;
            }

            int cancelY = listY + listHeight + buttonGap;
            Button cancel = Button.builder(Component.translatable("gui.todolist.cancel"), b -> {
                minecraft.setScreen(parentScreen);
            }).bounds(x, cancelY, guiWidth, cancelButtonHeight).build();
            this.addRenderableWidget(cancel);

            searchField.setResponder(text -> {
                updateFilteredPlayers();
            });
            updateFilteredPlayers();
            this.setFocused(searchField);
        }

        /**
         * 构建当前团队项目可供指派的成员列表，包含 owner 且去重，并按约定顺序稳定输出。
         *
         * @return 可指派成员候选列表
         */
        private List<AssignableMember> collectAssignableMembers() {
            List<AssignableMember> members = new ArrayList<>();
            if (currentProject == null || currentProject.getScope() != Project.Scope.TEAM) {
                return members;
            }
            String ownerUuid = currentProject.getOwnerUuid();
            if (ownerUuid != null && !ownerUuid.isBlank()) {
                members.add(new AssignableMember(ownerUuid, resolveProjectMemberDisplayName(currentProject, ownerUuid)));
            }
            List<AssignableMember> otherMembers = new ArrayList<>();
            for (String memberUuid : currentProject.getMembers().keySet()) {
                if (memberUuid == null || memberUuid.isBlank()) {
                    continue;
                }
                if (memberUuid.equals(ownerUuid)) {
                    continue;
                }
                otherMembers.add(new AssignableMember(memberUuid, resolveProjectMemberDisplayName(currentProject, memberUuid)));
            }
            otherMembers.sort(Comparator.comparing(member -> member.displayName, String.CASE_INSENSITIVE_ORDER));
            members.addAll(otherMembers);
            return members;
        }

        /**
         * 返回指定行当前对应的成员候选项。
         *
         * @param rowIndex 行索引
         * @return 当前行成员；若越界则返回 null
         */
        private AssignableMember getMemberForRow(int rowIndex) {
            if (filteredMembers == null || filteredMembers.isEmpty()) {
                return null;
            }
            int index = scrollOffset + rowIndex;
            if (index < 0 || index >= filteredMembers.size()) {
                return null;
            }
            return filteredMembers.get(index);
        }

        /**
         * 按搜索关键字过滤可指派成员列表。
         */
        private void updateFilteredPlayers() {
            if (allMembers == null || filteredMembers == null) {
                return;
            }
            filteredMembers.clear();
            String query = searchField == null ? "" : searchField.getValue();
            if (query == null) {
                query = "";
            }
            String q = query.trim().toLowerCase();
            for (AssignableMember entry : allMembers) {
                String name = entry.displayName;
                if (name == null || name.isEmpty()) {
                    continue;
                }
                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    filteredMembers.add(entry);
                }
            }
            scrollOffset = 0;
            updatePlayerButtons();
        }

        /**
         * 根据当前滚动位置刷新成员按钮文案与可见性。
         */
        private void updatePlayerButtons() {
            if (playerButtons == null) {
                return;
            }
            int maxOffset = 0;
            if (filteredMembers != null) {
                maxOffset = Math.max(0, filteredMembers.size() - visibleRows);
            }
            if (scrollOffset > maxOffset) {
                scrollOffset = maxOffset;
            }
            if (scrollOffset < 0) {
                scrollOffset = 0;
            }
            for (int i = 0; i < playerButtons.length; i++) {
                Button btn = playerButtons[i];
                AssignableMember entry = getMemberForRow(i);
                if (entry == null) {
                    btn.visible = false;
                    btn.active = false;
                    btn.setMessage(Component.empty());
                } else {
                    btn.visible = true;
                    btn.active = true;
                    btn.setMessage(Component.nullToEmpty(entry.displayName));
                }
            }
        }

        /**
         * 将目标任务指派给选中的团队成员，并回到父界面。
         *
         * @param uuid 目标成员 UUID
         * @param name 目标成员显示名称
         */
        private void applyAssignTo(String uuid, String name) {
            targetTask.setAssigneeUuid(uuid);
            targetTask.setAssigneeName(name);
            parentScreen.addNotification(Component.translatable("message.todolist.assigned_to_player", name).getString());
            parentScreen.markUnsaved();
            parentScreen.refreshTaskList();
            minecraft.setScreen(parentScreen);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                if (filteredMembers != null && !filteredMembers.isEmpty()) {
                    int maxOffset = Math.max(0, filteredMembers.size() - visibleRows);
                    if (amount < 0 && scrollOffset < maxOffset) {
                        scrollOffset++;
                        updatePlayerButtons();
                    } else if (amount > 0 && scrollOffset > 0) {
                        scrollOffset--;
                        updatePlayerButtons();
                    }
                }
            }
            return super.mouseScrolled(mouseX, mouseY, amount);
        }

        @Override
        public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}


