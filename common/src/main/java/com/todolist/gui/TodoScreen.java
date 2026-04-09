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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * 待办主界面，负责项目侧栏、任务列表、详情面板以及相关弹窗的交互。
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
     * 表示当前界面所在的空间类型。
     */
    private enum SpaceMode {
        PERSONAL,
        TEAM
    }

    /**
     * 表示当前空间下可切换的任务视图选项。
     */
    private enum TaskViewOption {
        MY,
        UNASSIGNED,
        ALL
    }

    /**
     * 表示团队项目搜索前缀对应的角色筛选模式。
     */
    private enum ProjectSearchRoleFilter {
        NONE,
        CREATED_BY_ME,
        MANAGED_BY_ME,
        JOINED_BY_ME
    }

    /**
     * 表示解析后的项目搜索查询结果。
     */
    private static final class ProjectSearchQuery {
        private final ProjectSearchRoleFilter roleFilter;
        private final String nameQuery;

        /**
         * 创建解析后的项目搜索查询对象。
         *
         * @param roleFilter 项目角色筛选模式
         * @param nameQuery 去掉前缀后的名称关键字
         */
        private ProjectSearchQuery(ProjectSearchRoleFilter roleFilter, String nameQuery) {
            this.roleFilter = roleFilter == null ? ProjectSearchRoleFilter.NONE : roleFilter;
            this.nameQuery = nameQuery == null ? "" : nameQuery;
        }
    }

    /**
     * 表示项目搜索前缀下拉面板中的一个候选项。
     */
    private static final class ProjectSearchPrefixOption {
        private final String prefix;
        private final String descKey;
        private final String fallbackZh;
        private final String fallbackEn;

        /**
         * 创建一个项目搜索前缀候选项。
         *
         * @param prefix 搜索前缀
         * @param descKey 描述文本翻译键
         */
        private ProjectSearchPrefixOption(String prefix, String descKey, String fallbackZh, String fallbackEn) {
            this.prefix = prefix;
            this.descKey = descKey;
            this.fallbackZh = fallbackZh == null ? "" : fallbackZh;
            this.fallbackEn = fallbackEn == null ? "" : fallbackEn;
        }
    }

    /**
     * 表示界面在不同屏幕尺寸下采用的响应式档位。
     */
    private enum ResponsiveTier {
        LARGE,
        MEDIUM,
        COMPACT,
        MINIMAL
    }

    /**
     * 表示一个矩形布局区域。
     */
    private static final class LayoutRect {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        /**
         * 创建布局矩形，并对坐标和尺寸做非负约束。
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
         * 判断给定坐标是否位于当前矩形区域内。
         *
         * @param mouseX 鼠标横坐标
         * @param mouseY 鼠标纵坐标
         * @return 命中当前区域时返回 {@code true}
         */
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        /**
         * 将矩形区域转换为测试使用的边界数组。
         *
         * @return 按 x、y、width、height 顺序返回的数组
         */
        private int[] toArray() {
            return new int[] {x, y, width, height};
        }
    }

    /**
     * 汇总主界面布局计算结果，便于各区域统一渲染和命中判断。
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
         * 创建主界面布局参数对象。
         *
         * @param responsiveTier 当前响应式档位
         * @param sidebarBounds 侧栏区域
         * @param contentBounds 内容区区域
         * @param detailBounds 详情区区域
         * @param sidebarOverlay 侧栏是否以覆盖层形式显示
         * @param detailOverlay 详情区是否以覆盖层形式显示
         * @param sidebarVisible 侧栏是否可见
         * @param detailVisible 详情区是否可见
         * @param padding 主布局外边距
         * @param gap 主布局区域间距
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
     * 表示任务详情面板中的临时编辑草稿。
     */
    private static final class TaskDetailDraft {
        private final String taskId;
        private String title;
        private String description;
        private String tags;
        private boolean titleEditing;

        /**
         * 创建任务详情草稿。
         *
         * @param taskId 任务 ID
         * @param title 任务标题
         * @param description 任务描述
         * @param tags 任务标签文本
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
    private boolean activeExpanded = true;
    private boolean completedExpanded;

    // 输入框
    private EditBox searchField;
    private EditBox quickAddField;
    private EditBox titleField;
    private MultiLineEditBox descField;
    private EditBox tagField;

    // 按钮
    private Button detailCloseButton;
    private Button claimButton;
    private Button abandonButton;
    private Button assignOthersButton;
    private Button saveButton;
    private Button cancelButton;
    private Button sidebarToggleButton;

    // 新建或编辑任务时选中的优先级
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // 筛选按钮
    private Button filterStatusButton;
    private Button filterPriorityButton;
    private Button viewToggleButton;
    private Button configButton;
    
    // 项目搜索与展开控制
    private EditBox projectSearchField;
    private boolean projectSearchPrefixDropdownOpen;
    private Button addProjectBtn;
    private Button personalSpaceButton;
    private Button teamSpaceButton;
    private Button myViewButton;
    private Button unassignedViewButton;
    private Button allViewButton;
    private Button editProjectBtn;
    private Button deleteProjectBtn;
    private Button applyJoinProjectBtn;
    private Project.Scope projectScopeFilter = Project.Scope.PERSONAL;
    private String projectSearchQuery = "";
    private String preferredPersonalProjectId;
    private String preferredTeamProjectId;
    private boolean teamProjectsEnabled = true;
    private int savedProjectListScrollOffset;
    private int savedTaskListScrollOffset;
    private static final int PROJECT_SEARCH_PREFIX_DROPDOWN_GAP = 2;
    private static final int PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING = 4;
    private static final int PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT = 16;
    private static final List<ProjectSearchPrefixOption> PROJECT_SEARCH_PREFIX_OPTIONS = List.of(
            new ProjectSearchPrefixOption("@me", "gui.todolist.project.search.prefix.me.desc", "我创建的项目", "Projects I created"),
            new ProjectSearchPrefixOption("@ma", "gui.todolist.project.search.prefix.ma.desc", "我管理的项目", "Projects I manage"),
            new ProjectSearchPrefixOption("@in", "gui.todolist.project.search.prefix.in.desc", "我加入的项目", "Projects I joined")
    );
    
    private int currentPriorityFilter = 0; // 0=All, 1=High, 2=Medium, 3=Low
    
    private Task selectedTask;
    private Task pendingClickSelectionTask;
    private boolean taskRowDragInProgress;
    private List<String> taskRowDragOrderSnapshot = List.of();
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
        boolean activeExpanded = true;
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
     * 创建待办主界面。
     *
     * @param parent 父级界面
     */
    public TodoScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    /**
     * 重置界面级静态状态，供测试初始化使用。
     */
    static void resetGuiStateForTest() {
        personalHasUnsavedChanges = false;
        teamHasUnsavedChanges = false;
        lastGuiState = null;
    }

    /**
     * 返回当前选中的项目，供界面测试断言使用。
     *
     * @return 当前选中的项目
     */
    Project getCurrentProjectForTest() {
        return currentProject;
    }

    /**
     * 返回当前选中的任务，供界面测试断言使用。
     *
     * @return 当前选中的任务；若没有选中则返回 {@code null}
     */
    Task getSelectedTaskForTest() {
        return selectedTask;
    }

    /**
     * 返回当前视图模式名称，供界面测试读取状态。
     *
     * @return 当前视图模式的枚举名称
     */
    String getViewModeNameForTest() {
        return viewMode.name();
    }

    /**
     * 返回当前空间模式名称，供界面测试校验侧栏切换结果。
     *
     * @return 当前空间模式的枚举名称
     */
    String getCurrentSpaceModeNameForTest() {
        return currentSpaceMode.name();
    }

    /**
     * 返回当前界面可见的任务视图选项名称列表。
     *
     * @return 当前可见的任务视图选项名称
     */
    List<String> getVisibleTaskViewOptionNamesForTest() {
        List<String> names = new ArrayList<>();
        for (TaskViewOption option : buildVisibleViewOptions(currentSpaceMode)) {
            names.add(option.name());
        }
        return List.copyOf(names);
    }

    /**
     * 返回当前任务视图选项名称。
     *
     * @return 当前任务视图选项的枚举名称
     */
    String getCurrentTaskViewOptionNameForTest() {
        return currentTaskViewOption.name();
    }

    /**
     * 判断已完成任务分组是否处于展开状态。
     *
     * @return 已展开时返回 {@code true}
     */
    boolean isCompletedSectionExpandedForTest() {
        return completedExpanded;
    }

    /**
     * 判断未完成任务分组是否处于展开状态。
     *
     * @return 已展开时返回 {@code true}
     */
    boolean isActiveSectionExpandedForTest() {
        return activeExpanded;
    }

    /**
     * 返回当前响应式档位名称。
     *
     * @return 当前响应式档位的枚举名称
     */
    String getResponsiveTierNameForTest() {
        return responsiveTier.name();
    }

    /**
     * 判断项目侧栏是否以覆盖层方式显示。
     *
     * @return 侧栏为覆盖层时返回 {@code true}
     */
    boolean isProjectSidebarOverlayForTest() {
        return layoutMetrics != null && layoutMetrics.sidebarOverlay;
    }

    /**
     * 判断任务详情区是否以覆盖层方式显示。
     *
     * @return 详情区为覆盖层时返回 {@code true}
     */
    boolean isDetailPanelOverlayForTest() {
        return layoutMetrics != null && layoutMetrics.detailOverlay;
    }

    /**
     * 判断项目侧栏当前是否可见。
     *
     * @return 侧栏可见时返回 {@code true}
     */
    boolean isProjectSidebarVisibleForTest() {
        return layoutMetrics != null && layoutMetrics.sidebarVisible;
    }

    /**
     * 判断任务详情区当前是否可见。
     *
     * @return 详情区可见时返回 {@code true}
     */
    boolean isDetailPanelVisibleForTest() {
        return layoutMetrics != null && layoutMetrics.detailVisible;
    }

    /**
     * 判断侧栏开关按钮当前是否可见。
     *
     * @return 开关按钮可见时返回 {@code true}
     */
    boolean isSidebarToggleButtonVisibleForTest() {
        return sidebarToggleButton != null && sidebarToggleButton.visible;
    }

    /**
     * 返回项目侧栏区域的边界，供界面测试定位点击区域。
     *
     * @return 侧栏区域的边界数组
     */
    int[] getProjectSidebarBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.sidebarBounds.toArray();
    }

    /**
     * 返回主内容区的边界，供界面测试定位点击区域。
     *
     * @return 内容区的边界数组
     */
    int[] getContentAreaBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.contentBounds.toArray();
    }

    /**
     * 返回详情区的边界，供界面测试定位点击区域。
     *
     * @return 详情区的边界数组
     */
    int[] getDetailPanelBoundsForTest() {
        return layoutMetrics == null ? new int[] {0, 0, 0, 0} : layoutMetrics.detailBounds.toArray();
    }

    /**
     * 返回项目列表区域的边界，供界面测试定位点击区域。
     *
     * @return 项目列表区域的边界数组，格式为 x、y、width、height
     */
    int[] getProjectListBoundsForTest() {
        return projectListWidget == null ? new int[] {0, 0, 0, 0} : projectListWidget.getBoundsForTest();
    }

    /**
     * 返回“添加项目”按钮的边界，供界面测试模拟点击。
     *
     * @return “添加项目”按钮的边界数组
     */
    int[] getAddProjectButtonBoundsForTest() {
        return toWidgetBounds(addProjectBtn);
    }

    /**
     * 返回“编辑项目”按钮的边界，供界面测试模拟点击。
     *
     * @return “编辑项目”按钮的边界数组
     */
    int[] getEditProjectButtonBoundsForTest() {
        return toWidgetBounds(editProjectBtn);
    }

    /**
     * 返回“删除项目”按钮的边界，供界面测试模拟点击。
     *
     * @return “删除项目”按钮的边界数组
     */
    int[] getDeleteProjectButtonBoundsForTest() {
        return toWidgetBounds(deleteProjectBtn);
    }

    /**
     * 判断申请加入项目按钮当前是否可见。
     *
     * @return 按钮可见时返回 {@code true}
     */
    boolean isApplyJoinProjectButtonVisibleForTest() {
        return applyJoinProjectBtn != null && applyJoinProjectBtn.visible;
    }

    /**
     * 判断删除项目按钮当前是否可见。
     *
     * @return 按钮可见时返回 {@code true}
     */
    boolean isDeleteProjectButtonVisibleForTest() {
        return deleteProjectBtn != null && deleteProjectBtn.visible;
    }

    /**
     * 返回编辑项目按钮当前显示的文本。
     *
     * @return 编辑项目按钮文本
     */
    String getEditProjectButtonTextForTest() {
        return editProjectBtn == null ? "" : editProjectBtn.getMessage().getString();
    }

    /**
     * 返回当前筛选器标识，供兼容性测试使用。
     *
     * @return 当前筛选器标识
     */
    String getCurrentFilterForTest() {
        return currentFilter;
    }

    /**
     * 返回当前优先级筛选值。
     *
     * @return 当前优先级筛选值
     */
    int getCurrentPriorityFilterForTest() {
        return currentPriorityFilter;
    }

    /**
     * 返回当前搜索框中的查询文本。
     *
     * @return 当前搜索文本
     */
    String getSearchQueryForTest() {
        return searchQuery;
    }

    /**
     * 返回当前通知数量，供界面测试验证提示状态。
     *
     * @return 当前待显示的通知数量
     */
    int getNotificationCountForTest() {
        return notifications.size();
    }

    /**
     * 返回详情区标题输入框，供界面测试直接操作。
     *
     * @return 标题输入框
     */
    EditBox getTitleFieldForTest() {
        return titleField;
    }

    /**
     * 返回底部快速新增输入框，供界面测试直接操作。
     *
     * @return 快速新增输入框
     */
    EditBox getQuickAddFieldForTest() {
        return quickAddField;
    }

    /**
     * 返回底部快速新增输入框的边界，供界面测试校验布局。
     *
     * @return 快速新增输入框边界数组
     */
    int[] getQuickAddFieldBoundsForTest() {
        return toWidgetBounds(quickAddField);
    }

    /**
     * 返回底部快速新增标记区域的边界，供界面测试校验布局。
     *
     * @return 快速新增标记边界数组
     */
    int[] getQuickAddMarkerBoundsForTest() {
        return getQuickAddMarkerBounds();
    }

    /**
     * 返回详情区描述输入框，供界面测试直接操作。
     *
     * @return 描述输入框
     */
    MultiLineEditBox getDescFieldForTest() {
        return descField;
    }

    /**
     * 返回详情描述输入框的边界，供界面测试定位点击区域。
     *
     * @return 描述输入框边界数组
     */
    int[] getDescFieldBoundsForTest() {
        return toWidgetBounds(descField);
    }

    /**
     * 返回详情区标签输入框，供界面测试直接操作。
     *
     * @return 标签输入框
     */
    EditBox getTagFieldForTest() {
        return tagField;
    }

    /**
     * 返回任务搜索输入框，供界面测试直接操作。
     *
     * @return 任务搜索输入框
     */
    EditBox getSearchFieldForTest() {
        return searchField;
    }

    /**
     * 返回项目搜索输入框，供界面测试直接操作。
     *
     * @return 项目搜索输入框
     */
    EditBox getProjectSearchFieldForTest() {
        return projectSearchField;
    }

    /**
     * 返回当前项目搜索前缀下拉面板是否可见，供界面测试断言。
     *
     * @return 下拉面板可见时返回 {@code true}
     */
    boolean isProjectSearchPrefixDropdownVisibleForTest() {
        return shouldShowProjectSearchPrefixDropdown();
    }

    /**
     * 返回项目搜索前缀候选项文本快照，供界面测试断言。
     *
     * @return 当前候选项文本列表
     */
    List<String> getProjectSearchPrefixSuggestionTextsForTest() {
        List<String> texts = new ArrayList<>();
        for (ProjectSearchPrefixOption option : PROJECT_SEARCH_PREFIX_OPTIONS) {
            texts.add(buildProjectSearchPrefixOptionText(option));
        }
        return List.copyOf(texts);
    }

    /**
     * 返回指定项目搜索前缀候选项的点击区域边界，供界面测试定位。
     *
     * @param index 候选项索引
     * @return 候选项边界数组
     */
    int[] getProjectSearchPrefixSuggestionBoundsForTest(int index) {
        return getProjectSearchPrefixOptionBounds(index);
    }

    /**
     * 返回当前侧栏中可见项目名称快照，供界面测试断言筛选结果。
     *
     * @return 当前可见项目名称列表
     */
    List<String> getVisibleProjectNamesForTest() {
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
     * 返回状态筛选按钮，供兼容性测试使用。
     *
     * @return 状态筛选按钮
     */
    Button getFilterStatusButtonForTest() {
        return filterStatusButton;
    }

    /**
     * 返回优先级筛选按钮，供界面测试操作。
     *
     * @return 优先级筛选按钮
     */
    Button getFilterPriorityButtonForTest() {
        return filterPriorityButton;
    }

    /**
     * 判断当前界面是否存在未保存改动。
     *
     * @return 存在未保存改动时返回 {@code true}
     */
    boolean hasUnsavedChangesForTest() {
        return hasUnsavedChanges;
    }

    /**
     * 返回当前筛选后的任务列表。
     *
     * @return 当前筛选结果任务列表
     */
    List<Task> getFilteredTasksForTest() {
        return List.copyOf(filteredTasks);
    }

    /**
     * 返回当前任务管理器中的全部任务。
     *
     * @return 当前任务管理器中的任务列表
     */
    List<Task> getCurrentManagerTasksForTest() {
        if (taskManager == null) {
            return List.of();
        }
        return taskManager.getAllTasks();
    }

    /**
     * 返回当前右键菜单中的条目文本列表。
     *
     * @return 右键菜单条目文本
     */
    List<String> getContextMenuItemTextsForTest() {
        List<String> texts = new ArrayList<>();
        for (ContextMenuItem item : contextMenuItems) {
            texts.add(item.text.getString());
        }
        return List.copyOf(texts);
    }

    /**
     * 判断当前是否存在任务右键菜单。
     *
     * @return 右键菜单存在时返回 {@code true}
     */
    boolean hasContextMenuForTest() {
        return hasContextMenu();
    }

    /**
     * 切换当前项目，供界面测试构造场景。
     *
     * @param project 目标项目
     */
    void switchProjectForTest(Project project) {
        switchProject(project);
    }

    /**
     * 选中指定任务，供界面测试构造场景。
     *
     * @param task 目标任务
     */
    void selectTaskForTest(Task task) {
        selectTask(task);
    }

    /**
     * 触发保存流程，供界面测试验证保存行为。
     */
    void saveTasksForTest() {
        onSaveTasks();
    }

    /**
     * 切换已完成任务分组的展开状态，供界面测试使用。
     */
    void toggleCompletedSectionForTest() {
        toggleCompletedSection();
        applySearchFilter();
    }

    /**
     * 切换未完成任务分组的展开状态，供界面测试使用。
     */
    void toggleActiveSectionForTest() {
        toggleActiveSection();
        applySearchFilter();
    }

    /**
     * 切换到团队空间“全部”视图，供界面测试构造场景。
     */
    void switchToTeamAllViewForTest() {
        switchView(ViewMode.TEAM_ALL);
    }

    /**
     * 切换侧栏覆盖层状态，供界面测试使用。
     */
    void toggleSidebarOverlayForTest() {
        toggleSidebarOverlay();
    }

    /**
     * 返回任务列表组件实例，供界面测试进一步断言。
     *
     * @return 任务列表组件
     */
    TaskListWidget getTaskListWidgetForTest() {
        return taskListWidget;
    }

    /**
     * 返回任务列表区域的边界，供界面测试校验布局。
     *
     * @return 任务列表区域边界数组
     */
    int[] getTaskListBoundsForTest() {
        return taskListWidget == null ? new int[] {0, 0, 0, 0} : taskListWidget.getBoundsForTest();
    }

    /**
     * 判断详情标题当前是否处于可编辑状态。
     *
     * @return 标题可编辑时返回 {@code true}
     */
    boolean isDetailTitleEditableForTest() {
        return detailDraft != null && detailDraft.titleEditing;
    }

    /**
     * 进入详情标题编辑状态，供界面测试使用。
     */
    void beginDetailTitleEditingForTest() {
        beginDetailTitleEditing();
    }

    /**
     * 触发详情弹层的关闭按钮，供界面测试验证收起逻辑。
     */
    void clickDetailCloseButtonForTest() {
        if (detailCloseButton != null) {
            detailCloseButton.onPress();
        }
    }

    /**
     * 返回详情关闭按钮的边界，供界面测试定位点击。
     *
     * @return 详情关闭按钮边界数组
     */
    int[] getDetailCloseButtonBoundsForTest() {
        return toWidgetBounds(detailCloseButton);
    }

    /**
     * 返回详情关闭按钮当前显示文案，供界面测试校验符号。
     *
     * @return 关闭按钮显示文案
     */
    String getDetailCloseButtonTextForTest() {
        return detailCloseButton == null ? "" : detailCloseButton.getMessage().getString();
    }

    /**
     * 返回详情标题输入框的边界，供界面测试定位点击。
     *
     * @return 详情标题输入框边界数组
     */
    int[] getDetailTitleFieldBoundsForTest() {
        return toWidgetBounds(titleField);
    }

    /**
     * 判断领取任务按钮当前是否可见。
     *
     * @return 领取按钮可见时返回 {@code true}
     */
    boolean isClaimButtonVisibleForTest() {
        return claimButton != null && claimButton.visible;
    }

    /**
     * 判断放弃任务按钮当前是否可见。
     *
     * @return 放弃按钮可见时返回 {@code true}
     */
    boolean isAbandonButtonVisibleForTest() {
        return abandonButton != null && abandonButton.visible;
    }

    /**
     * 判断指派他人按钮当前是否可见。
     *
     * @return 指派按钮可见时返回 {@code true}
     */
    boolean isAssignOthersButtonVisibleForTest() {
        return assignOthersButton != null && assignOthersButton.visible;
    }

    /**
     * 返回领取任务按钮的边界，供界面测试定位点击。
     *
     * @return 领取按钮边界数组
     */
    int[] getClaimButtonBoundsForTest() {
        return toWidgetBounds(claimButton);
    }

    /**
     * 返回领取任务按钮当前显示文案，供界面测试校验按钮压缩文案。
     *
     * @return 领取按钮显示文案
     */
    String getClaimButtonTextForTest() {
        return claimButton == null ? "" : claimButton.getMessage().getString();
    }

    /**
     * 返回放弃任务按钮的边界，供界面测试定位点击。
     *
     * @return 放弃按钮边界数组
     */
    int[] getAbandonButtonBoundsForTest() {
        return toWidgetBounds(abandonButton);
    }

    /**
     * 返回放弃任务按钮当前显示文案，供界面测试校验按钮压缩文案。
     *
     * @return 放弃按钮显示文案
     */
    String getAbandonButtonTextForTest() {
        return abandonButton == null ? "" : abandonButton.getMessage().getString();
    }

    /**
     * 返回指派他人按钮的边界，供界面测试定位点击。
     *
     * @return 指派按钮边界数组
     */
    int[] getAssignOthersButtonBoundsForTest() {
        return toWidgetBounds(assignOthersButton);
    }

    /**
     * 返回指派他人按钮当前显示文案，供界面测试校验按钮压缩文案。
     *
     * @return 指派按钮显示文案
     */
    String getAssignOthersButtonTextForTest() {
        return assignOthersButton == null ? "" : assignOthersButton.getMessage().getString();
    }

    /**
     * 返回保存按钮的边界，供界面测试校验底部布局。
     *
     * @return 保存按钮边界数组
     */
    int[] getSaveButtonBoundsForTest() {
        return toWidgetBounds(saveButton);
    }

    /**
     * 返回取消按钮的边界，供界面测试校验底部布局。
     *
     * @return 取消按钮边界数组
     */
    int[] getCancelButtonBoundsForTest() {
        return toWidgetBounds(cancelButton);
    }

    /**
     * 打开指定任务的右键菜单，供界面测试复用。
     *
     * @param task 目标任务
     */
    void openTaskContextMenuForTest(Task task) {
        openTaskContextMenu(task, 32, 32);
    }

    /**
     * 点击右键菜单中的指定条目，供界面测试复用。
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
     * 创建任务指派弹窗，供界面测试单独验证弹窗内容。
     *
     * @param task 目标任务
     * @return 新创建的指派成员弹窗
     */
    Screen createAssignPlayerScreenForTest(Task task) {
        return new AssignPlayerScreen(this, task);
    }

    /**
     * 读取指派成员弹窗中当前可见的成员名称。
     *
     * @param screen 指派成员弹窗
     * @return 当前可见的成员名称列表
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
     * 读取指派成员弹窗的滚动偏移量。
     *
     * @param screen 指派成员弹窗
     * @return 当前滚动偏移量
     */
    int getAssignPlayerScrollOffsetForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getScrollOffsetForTest();
    }

    /**
     * 读取指派成员弹窗当前可见的成员行数。
     *
     * @param screen 指派成员弹窗
     * @return 当前可见成员行数
     */
    int getAssignPlayerVisibleRowsForTest(Screen screen) {
        if (!(screen instanceof AssignPlayerScreen assignPlayerScreen)) {
            return 0;
        }
        return assignPlayerScreen.getVisibleRowsForTest();
    }

    /**
     * 滚动指派成员列表，供界面测试复用。
     *
     * @param screen 指派成员弹窗
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
     * 设置指派成员弹窗中的搜索词，供界面测试过滤成员列表。
     *
     * @param screen 指派成员弹窗
     * @param value 搜索关键字
     */
    void setAssignPlayerSearchForTest(Screen screen, String value) {
        if (screen instanceof AssignPlayerScreen assignPlayerScreen && assignPlayerScreen.searchField != null) {
            assignPlayerScreen.searchField.setValue(value == null ? "" : value);
        }
    }

    /**
     * 点击指派成员弹窗中的某一行成员，供界面测试模拟选择动作。
     *
     * @param screen 指派成员弹窗
     * @param rowIndex 成员行索引
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

        // 初始化任务管理器并从存储加载任务
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
        
        // 初始化项目管理器
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
        
        // 校验当前项目是否仍然有效
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
        
        // 确保当前任务管理器与当前项目保持一致
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
     * 根据当前项目重新同步空间模式、可见视图和兼容状态。
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
     * 根据项目作用域解析界面应使用的空间模式。
     *
     * @param project 当前项目
     * @return 对应的空间模式
     */
    private SpaceMode resolveSpaceMode(Project project) {
        if (project == null || project.getScope() == Project.Scope.PERSONAL || !teamProjectsEnabled) {
            return SpaceMode.PERSONAL;
        }
        return SpaceMode.TEAM;
    }

    /**
     * 构建当前空间模式下允许显示的任务视图选项。
     *
     * @param spaceMode 当前空间模式
     * @return 可见的任务视图选项列表
     */
    private List<TaskViewOption> buildVisibleViewOptions(SpaceMode spaceMode) {
        if (spaceMode == SpaceMode.TEAM) {
            return List.of(TaskViewOption.UNASSIGNED, TaskViewOption.ALL, TaskViewOption.MY);
        }
        return List.of(TaskViewOption.MY);
    }

    /**
     * 为指定空间模式选择默认任务视图。
     *
     * @param spaceMode 当前空间模式
     * @return 默认任务视图选项
     */
    private TaskViewOption resolveDefaultViewForSpace(SpaceMode spaceMode) {
        return spaceMode == SpaceMode.TEAM ? TaskViewOption.UNASSIGNED : TaskViewOption.MY;
    }

    /**
     * 将旧版视图模式映射为新的任务视图选项。
     *
     * @param legacyViewMode 旧版视图模式
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
     * 根据当前空间模式和任务视图选项回写旧版视图状态。
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
     * 切换已完成任务分组的展开状态，并刷新任务列表。
     */
    private void toggleCompletedSection() {
        completedExpanded = !completedExpanded;
    }

    /**
     * 切换未完成任务分组的展开状态。
     */
    private void toggleActiveSection() {
        activeExpanded = !activeExpanded;
    }

    /**
     * 根据分组 ID 切换对应分组的展开状态。
     *
     * @param sectionId 分组 ID
     * @return 若成功切换则返回 {@code true}
     */
    private boolean toggleTaskSection(String sectionId) {
        if ("active".equals(sectionId)) {
            toggleActiveSection();
            return true;
        }
        if ("completed".equals(sectionId)) {
            toggleCompletedSection();
            return true;
        }
        return false;
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
        activeExpanded = lastGuiState.activeExpanded;
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
        currentFilter = "active";
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
        s.activeExpanded = activeExpanded;
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
     * 在项目增删改后刷新当前项目、侧栏和任务列表。
     */
    private void refreshAfterProjectMutation() {
        syncActiveProjectIdWithCurrentProject();
        updateProjectList();
        refreshTaskList();
        updateButtonStates();
        updateProjectActionButtons();
    }

    /**
     * 将当前项目 ID 同步到界面和配置状态中。
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
     * 在项目被删除后，为当前界面选择一个合适的回退项目。
     *
     * @param removedProject 被删除的项目
     * @return 回退后应选中的项目
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
     * 根据当前空间范围挑选一个优先展示的项目。
     *
     * @return 当前空间下的优先项目
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
        currentFilter = "active";
        if (searchQuery == null) {
            searchQuery = "";
        }
        if (projectSearchQuery == null) {
            projectSearchQuery = "";
        }
        baseFilteredTasks = new ArrayList<>();
        filteredTasks = new ArrayList<>();
        if (taskListWidget != null) {
            savedTaskListScrollOffset = taskListWidget.getScrollOffset();
        }
        this.clearWidgets();

        ModConfig config = ModConfig.getInstance();
        responsiveTier = resolveResponsiveTier(this.width, this.height);
        syncOverlayStateForResponsiveTier();

        layoutMetrics = buildMainLayoutMetrics(config);
        LayoutRect sidebarBounds = layoutMetrics.sidebarBounds;
        LayoutRect contentBounds = layoutMetrics.contentBounds;
        LayoutRect detailBounds = layoutMetrics.detailBounds;
        int padding = layoutMetrics.padding;
        int panelGap = layoutMetrics.gap;
        int contentControlInset = getContentControlInset();
        int contentControlX = contentBounds.x + contentControlInset;
        int contentControlWidth = Math.max(80, contentBounds.width - contentControlInset * 2);
        int contentControlRight = contentControlX + contentControlWidth;
        int topBarGap = clampInt(config.getElementSpacing(), 3, 8);
        int topBarHeight = getContentTopBarHeight();
        int topBarY = contentBounds.y + getContentTopPadding();
        int secondRowHeight = getContentSearchFieldHeight();
        int secondRowY = topBarY + topBarHeight + getContentHeaderGap();
        int inputRowHeight = getContentQuickAddFieldHeight();
        int inputRowY = contentBounds.y + contentBounds.height - inputRowHeight - getContentBottomPadding();
        int bottomActionRowHeight = getContentBottomActionRowHeight();
        int actionRowY = this.height - padding - bottomActionRowHeight;
        int listTop = secondRowY + secondRowHeight + topBarGap;
        int listBottom = inputRowY - Math.max(4, topBarGap);
        int listHeight = Math.max(0, listBottom - listTop);

        int sidebarTopY = sidebarBounds.y;
        int sidebarWidth = sidebarBounds.width;
        int contentX = contentBounds.x;
        int contentWidth = contentBounds.width;
        int rightPanelX = detailBounds.x;
        int rightPanelWidth = detailBounds.width;

        int overlayToggleWidth = layoutMetrics.sidebarOverlay ? 56 : 0;
        sidebarToggleButton = Button.builder(Component.translatable("gui.todolist.project.sidebar"), b -> toggleSidebarOverlay())
                .bounds(contentControlX, topBarY, overlayToggleWidth, topBarHeight).build();
        sidebarToggleButton.visible = layoutMetrics.sidebarOverlay;
        sidebarToggleButton.active = layoutMetrics.sidebarOverlay;
        this.addRenderableWidget(sidebarToggleButton);

        int actionGap = getContentActionGap();
        int configButtonWidth = Math.max(44, Math.min(64, this.font.width(Component.translatable("gui.todolist.config.title")) + 10));
        int cancelButtonWidth = Math.max(44, Math.min(64, this.font.width(Component.translatable("gui.todolist.cancel")) + 10));
        int saveButtonWidth = Math.max(44, Math.min(64, this.font.width(Component.translatable("gui.todolist.save")) + 10));
        int configButtonX = contentControlRight - configButtonWidth;
        int bottomActionWidth = saveButtonWidth + actionGap + cancelButtonWidth;
        int saveButtonX = Math.max(0, (this.width - bottomActionWidth) / 2);
        int cancelButtonX = saveButtonX + saveButtonWidth + actionGap;
        configButton = Button.builder(Component.translatable("gui.todolist.config.title"), b -> this.minecraft.setScreen(new ConfigScreen(this)))
                .bounds(configButtonX, topBarY, configButtonWidth, topBarHeight).build();
        this.addRenderableWidget(configButton);

        int filterGap = getContentActionGap();
        int filtersX = contentControlX + (layoutMetrics.sidebarOverlay ? overlayToggleWidth + filterGap : 0);
        int btnH = secondRowHeight;
        int priorityBtnWidth = Math.min(92, Math.max(52, this.font.width(getPriorityFilterText()) + 12));
        int minSearchWidth = 72;
        int availableSearchWidth = contentControlRight - filtersX - filterGap - priorityBtnWidth;
        if (availableSearchWidth < minSearchWidth) {
            priorityBtnWidth = Math.max(48, contentControlRight - filtersX - filterGap - minSearchWidth);
        }
        int priorityBtnX = contentControlRight - priorityBtnWidth;
        filterPriorityButton = Button.builder(getPriorityFilterText(), button -> {
            currentPriorityFilter = (currentPriorityFilter + 1) % 4;
            button.setMessage(getPriorityFilterText());
            applyPriorityFilter();
        }).bounds(priorityBtnX, secondRowY, priorityBtnWidth, btnH).build();
        this.addRenderableWidget(filterPriorityButton);

        filterStatusButton = null;
        viewToggleButton = null;

        int searchWidth = Math.max(0, priorityBtnX - filterGap - filtersX);
        searchField = new EditBox(this.font, filtersX, secondRowY, searchWidth, secondRowHeight, Component.empty());
        searchField.setHint(Component.translatable("gui.todolist.input.search.placeholder"));
        searchField.setValue(searchQuery);
        this.addRenderableWidget(searchField);

        int sidebarInset = responsiveTier == ResponsiveTier.MINIMAL ? 5 : 7;
        int sidebarInnerX = sidebarBounds.x + sidebarInset;
        int sidebarInnerWidth = Math.max(80, sidebarWidth - sidebarInset * 2);
        int sidebarControlHeight = getSidebarControlHeight();
        int searchFieldHeight = getSidebarSearchFieldHeight();
        int bottomButtonHeight = getSidebarBottomButtonHeight();
        int sidebarSectionGap = getSidebarSectionGap();
        int projectListGap = getSidebarProjectListGap();
        int spaceButtonsY = sidebarTopY + (responsiveTier == ResponsiveTier.MINIMAL ? 8 : 10);
        int spaceButtonGap = 4;
        int spaceButtonWidth = Math.max(48, (sidebarInnerWidth - spaceButtonGap) / 2);
        personalSpaceButton = Button.builder(Component.translatable("gui.todolist.scope.personal"), b -> {
            Project targetProject = getPreferredProjectForScope(Project.Scope.PERSONAL);
            switchProject(targetProject);
        }).bounds(sidebarInnerX, spaceButtonsY, spaceButtonWidth, sidebarControlHeight).build();
        this.addRenderableWidget(personalSpaceButton);

        teamSpaceButton = Button.builder(Component.translatable("gui.todolist.scope.team"), b -> {
            if (!teamProjectsEnabled) {
                return;
            }
            Project targetProject = getPreferredProjectForScope(Project.Scope.TEAM);
            switchProject(targetProject);
        }).bounds(sidebarInnerX + spaceButtonWidth + spaceButtonGap, spaceButtonsY,
                sidebarInnerWidth - spaceButtonWidth - spaceButtonGap, sidebarControlHeight).build();
        teamSpaceButton.active = teamProjectsEnabled;
        this.addRenderableWidget(teamSpaceButton);

        int viewButtonsY = spaceButtonsY + sidebarControlHeight + sidebarSectionGap;
        int teamViewButtonGap = 4;
        int teamViewButtonWidth = Math.max(26, (sidebarInnerWidth - teamViewButtonGap * 2) / 3);
        myViewButton = Button.builder(Component.literal("\u6211\u7684"), b -> {
            if (currentSpaceMode == SpaceMode.PERSONAL) {
                switchView(ViewMode.PERSONAL);
                return;
            }
            switchView(ViewMode.TEAM_ASSIGNED);
        }).bounds(sidebarInnerX, viewButtonsY, teamViewButtonWidth, sidebarControlHeight).build();
        this.addRenderableWidget(myViewButton);

        unassignedViewButton = Button.builder(getCompactTeamViewText(TaskViewOption.UNASSIGNED), b -> switchView(ViewMode.TEAM_UNASSIGNED))
                .bounds(sidebarInnerX + teamViewButtonWidth + teamViewButtonGap, viewButtonsY, teamViewButtonWidth, sidebarControlHeight).build();
        this.addRenderableWidget(unassignedViewButton);

        allViewButton = Button.builder(getCompactTeamViewText(TaskViewOption.ALL), b -> switchView(ViewMode.TEAM_ALL))
                .bounds(sidebarInnerX + (teamViewButtonWidth + teamViewButtonGap) * 2, viewButtonsY,
                        sidebarInnerWidth - (teamViewButtonWidth + teamViewButtonGap) * 2, sidebarControlHeight).build();
        this.addRenderableWidget(allViewButton);

        int projectSearchY = viewButtonsY + sidebarControlHeight + sidebarSectionGap;
        projectSearchField = new EditBox(this.font, sidebarInnerX, projectSearchY, sidebarInnerWidth, searchFieldHeight, Component.translatable("gui.todolist.project.search"));
        projectSearchField.setHint(Component.translatable("gui.todolist.project.search"));
        projectSearchField.setValue(projectSearchQuery);
        projectSearchField.setResponder(text -> {
            projectSearchQuery = text;
            updateProjectList();
        });
        this.addRenderableWidget(projectSearchField);

        int projectButtonGap = getSidebarBottomButtonGap();
        int projectButtonWidth = Math.max(26, (sidebarInnerWidth - projectButtonGap * 2) / 3);
        int projectButtonsY = sidebarBounds.y + sidebarBounds.height - bottomButtonHeight - getSidebarBottomPadding();
        int projectListY = projectSearchY + searchFieldHeight + projectListGap;
        int projectListHeight = Math.max(0, projectButtonsY - projectListGap - projectListY);

        projectListWidget = new ProjectListWidget(this.minecraft, sidebarInnerX, projectListY, sidebarInnerWidth, projectListHeight);
        updateProjectList();
        projectListWidget.setScrollOffset(savedProjectListScrollOffset);
        projectListWidget.setSelectedProject(currentProject);
        projectListWidget.setOnProjectSelected(this::switchProject);

        addProjectBtn = Button.builder(getSidebarAddButtonText(), b -> onAddProject())
                .bounds(sidebarInnerX, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();
        this.addRenderableWidget(addProjectBtn);

        editProjectBtn = Button.builder(getSidebarEditButtonText(true), b -> onProjectSettings())
                .bounds(sidebarInnerX + projectButtonWidth + projectButtonGap, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();
        editProjectBtn.active = currentProject != null;
        this.addRenderableWidget(editProjectBtn);

        int deleteButtonX = sidebarInnerX + (projectButtonWidth + projectButtonGap) * 2;
        deleteProjectBtn = Button.builder(getSidebarDeleteButtonText(), b -> onProjectDelete())
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();
        this.addRenderableWidget(deleteProjectBtn);

        applyJoinProjectBtn = Button.builder(getSidebarJoinProjectButtonText(), b -> onApplyJoinProject())
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();
        this.addRenderableWidget(applyJoinProjectBtn);
        updateProjectActionButtons();

        taskListWidget = new TaskListWidget(this.minecraft, contentX, listTop, contentWidth, listHeight);
        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        taskListWidget.setTeamAllViewForNonOp(getCurrentRole() == Role.MEMBER && teamAllView);
        taskListWidget.setTaskReorderEnabled(isTaskReorderAllowedInCurrentView());
        taskListWidget.setSections(buildTaskPaneSections());
        if (selectedTask != null) {
            taskListWidget.setSelectedTask(selectedTask);
        }
        taskListWidget.setOnTaskToggleCompletion(task -> {
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
        taskListWidget.setOnTaskReorder(this::onManualReorderActiveTasks);

        int quickAddFieldX = contentControlX;
        int quickAddFieldWidth = Math.max(60, contentControlWidth);
        quickAddField = new EditBox(this.font, quickAddFieldX, inputRowY, quickAddFieldWidth, inputRowHeight, Component.empty());
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
        int assignButtonHeight = 20;
        int assignButtonGap = 4;
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        int assignsX = rightPanelX + rightInnerPadding;
        int titleFieldHeight = 20;
        int titleFieldY = rightPanelTop + rightInnerPadding;
        int closeButtonSize = 14;
        int closeButtonGap = 4;
        int closeButtonX = Math.max(assignsX, rightPanelX + rightPanelWidth - rightInnerPadding - closeButtonSize);
        int closeButtonY = titleFieldY + Math.max(0, (titleFieldHeight - closeButtonSize) / 2);
        int titleFieldWidth = Math.max(48, closeButtonX - closeButtonGap - assignsX);

        detailCloseButton = Button.builder(Component.literal("×"), b -> clearSelectedTask())
                .bounds(closeButtonX, closeButtonY, closeButtonSize, closeButtonSize)
                .build();
        this.addRenderableWidget(detailCloseButton);

        titleField = new EditBox(this.font, assignsX, titleFieldY, titleFieldWidth, titleFieldHeight, Component.empty());
        titleField.setHint(Component.translatable("gui.todolist.input.title.placeholder"));
        titleField.setValue("");
        titleField.setMaxLength(100);
        titleField.setEditable(false);
        this.addRenderableWidget(titleField);

        int teamButtonsTop = titleFieldY + titleFieldHeight + rightSectionGap;
        int detailFieldsTop = showAssignButtons
                ? teamButtonsTop + assignButtonHeight + rightSectionGap
                : teamButtonsTop;

        int tagFieldY = rightPanelBottom - rightInnerPadding - 20;
        int descFieldY = detailFieldsTop + textH + 2;
        int minTagFieldY = descFieldY + 28 + rightSectionGap + textH + 2;
        if (tagFieldY < minTagFieldY) {
            tagFieldY = minTagFieldY;
        }
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

        int claimButtonWidth = Math.max(36, (rightFieldWidth - assignButtonGap * 2) / 3);
        int abandonButtonX = assignsX + claimButtonWidth + assignButtonGap;
        int abandonButtonWidth = claimButtonWidth;
        int assignOthersButtonX = abandonButtonX + abandonButtonWidth + assignButtonGap;
        int assignOthersButtonWidth = Math.max(36, rightFieldWidth - claimButtonWidth - abandonButtonWidth - assignButtonGap * 2);

        claimButton = Button.builder(Component.literal("领取"), b -> onClaimTask())
                .bounds(assignsX, teamButtonsTop, claimButtonWidth, assignButtonHeight).build();
        claimButton.active = false;
        this.addRenderableWidget(claimButton);

        abandonButton = Button.builder(Component.literal("放弃"), b -> onAbandonTask())
                .bounds(abandonButtonX, teamButtonsTop, abandonButtonWidth, assignButtonHeight).build();
        abandonButton.active = false;
        this.addRenderableWidget(abandonButton);

        assignOthersButton = Button.builder(Component.literal("指派"), b -> onAssignOthers())
                .bounds(assignOthersButtonX, teamButtonsTop, assignOthersButtonWidth, assignButtonHeight).build();
        assignOthersButton.active = false;
        this.addRenderableWidget(assignOthersButton);

        saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> onSaveTasks())
                .bounds(saveButtonX, actionRowY, saveButtonWidth, bottomActionRowHeight).build();
        this.addRenderableWidget(saveButton);

        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> onCancel())
                .bounds(cancelButtonX, actionRowY, cancelButtonWidth, bottomActionRowHeight).build();
        this.addRenderableWidget(cancelButton);

        applyResponsiveWidgetVisibility();

        // 监听器
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
        if (taskListWidget != null) {
            taskListWidget.setScrollOffset(savedTaskListScrollOffset);
        }
        syncDetailWidgetsFromState();
        this.setFocused(quickAddField);
        updateButtonStates();
    }

    /**
     * 根据屏幕宽高计算当前界面的响应式档位。
     *
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 当前响应式档位
     */
    private ResponsiveTier resolveResponsiveTier(int screenWidth, int screenHeight) {
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
     * 根据屏幕高度计算高度维度的响应式档位。
     *
     * @param screenHeight 屏幕高度
     * @return 高度维度对应的响应式档位
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
     * 根据当前响应式档位同步侧栏和详情区的覆盖层显示状态。
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
     * 根据配置和当前窗口尺寸构建主界面的布局参数。
     *
     * @param config 当前配置
     * @return 主界面的布局参数
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
        boolean detailVisible = selectedTask != null && (!detailOverlay || detailOverlayVisible);

        int availableWidth = Math.max(120, this.width - padding * 2);
        int minContentWidth = switch (responsiveTier) {
            case LARGE -> Math.min(180, Math.max(160, availableWidth));
            case MEDIUM -> Math.min(140, Math.max(132, availableWidth));
            case COMPACT -> Math.min(160, Math.max(136, availableWidth));
            case MINIMAL -> Math.min(150, Math.max(120, availableWidth));
        };
        int sidebarWidth = resolveSidebarWidth(config, availableWidth);
        int detailWidth = resolveDetailWidth(availableWidth);

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
                ? Math.max(padding, this.width - padding - detailWidth)
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
     * @return 主布局外边距
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
     * 根据可用宽度和配置计算侧栏宽度。
     *
     * @param config 当前配置
     * @param availableWidth 可用宽度
     * @return 侧栏宽度
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
     * 根据可用宽度计算详情区宽度。
     *
     * @param availableWidth 可用宽度
     * @return 详情区宽度
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
     * 根据当前响应式布局更新组件显隐状态。
     */
    private void applyResponsiveWidgetVisibility() {
        if (layoutMetrics == null) {
            return;
        }
        layoutSidebarViewButtons();
        boolean sidebarVisible = layoutMetrics.sidebarVisible;
        boolean detailVisible = layoutMetrics.detailVisible;

        boolean teamSpaceVisible = sidebarVisible && currentSpaceMode == SpaceMode.TEAM;
        if (personalSpaceButton != null) {
            personalSpaceButton.visible = sidebarVisible;
            personalSpaceButton.active = sidebarVisible && currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.visible = sidebarVisible;
            teamSpaceButton.active = sidebarVisible && teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (myViewButton != null) {
            myViewButton.visible = sidebarVisible;
            myViewButton.setMessage(Component.literal("\u6211\u7684"));
            myViewButton.active = sidebarVisible && currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.MY;
        }
        if (unassignedViewButton != null) {
            unassignedViewButton.visible = teamSpaceVisible;
            unassignedViewButton.setMessage(getCompactTeamViewText(TaskViewOption.UNASSIGNED));
            unassignedViewButton.active = teamSpaceVisible && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.visible = teamSpaceVisible;
            allViewButton.setMessage(getCompactTeamViewText(TaskViewOption.ALL));
            allViewButton.active = teamSpaceVisible && currentTaskViewOption != TaskViewOption.ALL;
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
        syncProjectSearchPrefixDropdownState();
    }

    /**
     * 根据当前空间、侧栏状态和焦点状态同步项目搜索前缀下拉面板显隐。
     */
    private void syncProjectSearchPrefixDropdownState() {
        if (!canUseProjectSearchPrefixDropdown()
                || projectSearchField == null
                || !projectSearchField.visible
                || !projectSearchField.isFocused()) {
            closeProjectSearchPrefixDropdown();
        }
    }

    /**
     * 判断当前是否允许显示团队项目搜索前缀下拉面板。
     *
     * @return 允许显示时返回 {@code true}
     */
    private boolean canUseProjectSearchPrefixDropdown() {
        return currentSpaceMode == SpaceMode.TEAM && isSidebarPanelVisible();
    }

    /**
     * 判断项目搜索前缀下拉面板当前是否应显示。
     *
     * @return 应显示时返回 {@code true}
     */
    private boolean shouldShowProjectSearchPrefixDropdown() {
        return projectSearchPrefixDropdownOpen
                && canUseProjectSearchPrefixDropdown()
                && projectSearchField != null
                && projectSearchField.visible;
    }

    /**
     * 打开项目搜索前缀下拉面板。
     */
    private void openProjectSearchPrefixDropdown() {
        if (canUseProjectSearchPrefixDropdown()) {
            projectSearchPrefixDropdownOpen = true;
        }
    }

    /**
     * 关闭项目搜索前缀下拉面板。
     */
    private void closeProjectSearchPrefixDropdown() {
        projectSearchPrefixDropdownOpen = false;
    }

    /**
     * 切换侧栏的覆盖层显示状态。
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
     * 判断侧栏面板当前是否应该显示。
     *
     * @return 侧栏应显示时返回 {@code true}
     */
    private boolean isSidebarPanelVisible() {
        return layoutMetrics != null && layoutMetrics.sidebarVisible;
    }

    /**
     * 判断详情面板当前是否应该显示。
     *
     * @return 详情面板应显示时返回 {@code true}
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
        renderContentHeaderSummary(context);
        if (taskListWidget != null) taskListWidget.render(context, mouseX, mouseY, delta);
        if (projectListWidget != null && isSidebarPanelVisible()) {
            projectListWidget.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);

        int color = 0xFFFFFFFF;
        int textH = this.font.lineHeight;

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
        renderProjectSearchPrefixDropdown(context, mouseX, mouseY);
        renderTaskContextMenu(context, mouseX, mouseY);
        renderNotifications(context);
    }

    /**
     * 绘制团队项目搜索前缀下拉面板，帮助玩家发现可用前缀。
     *
     * @param context 当前绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     */
    private void renderProjectSearchPrefixDropdown(GuiGraphics context, int mouseX, int mouseY) {
        if (!shouldShowProjectSearchPrefixDropdown()) {
            return;
        }
        int[] bounds = getProjectSearchPrefixDropdownBounds();
        if (bounds[2] <= 0 || bounds[3] <= 0) {
            return;
        }
        context.fill(bounds[0], bounds[1], bounds[0] + bounds[2], bounds[1] + bounds[3], 0xEE0F141B);
        context.renderOutline(bounds[0], bounds[1], bounds[2], bounds[3], 0xFF506070);

        for (int i = 0; i < PROJECT_SEARCH_PREFIX_OPTIONS.size(); i++) {
            int[] itemBounds = getProjectSearchPrefixOptionBounds(i);
            boolean hovered = mouseX >= itemBounds[0] && mouseX < itemBounds[0] + itemBounds[2]
                    && mouseY >= itemBounds[1] && mouseY < itemBounds[1] + itemBounds[3];
            if (hovered) {
                context.fill(itemBounds[0], itemBounds[1], itemBounds[0] + itemBounds[2], itemBounds[1] + itemBounds[3], 0xFF223040);
            }
            int textY = itemBounds[1] + Math.max(0, (itemBounds[3] - this.font.lineHeight) / 2);
            ProjectSearchPrefixOption option = PROJECT_SEARCH_PREFIX_OPTIONS.get(i);
            context.drawString(this.font, option.prefix, itemBounds[0] + 4, textY, 0xFFE0B240, false);
            context.drawString(this.font, getProjectSearchPrefixOptionDescription(option), itemBounds[0] + 34, textY, 0xFFD8E2EC, false);
        }
    }

    /**
     * 返回项目搜索前缀下拉面板的整体边界。
     *
     * @return 下拉面板边界数组
     */
    private int[] getProjectSearchPrefixDropdownBounds() {
        if (projectSearchField == null) {
            return new int[] {0, 0, 0, 0};
        }
        int x = projectSearchField.getX();
        int y = projectSearchField.getY() + projectSearchField.getHeight() + PROJECT_SEARCH_PREFIX_DROPDOWN_GAP;
        int width = projectSearchField.getWidth();
        int height = PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING * 2
                + PROJECT_SEARCH_PREFIX_OPTIONS.size() * PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT;
        return new int[] {x, y, width, height};
    }

    /**
     * 返回指定前缀候选项的边界。
     *
     * @param index 候选项索引
     * @return 候选项边界数组
     */
    private int[] getProjectSearchPrefixOptionBounds(int index) {
        int[] bounds = getProjectSearchPrefixDropdownBounds();
        if (index < 0 || index >= PROJECT_SEARCH_PREFIX_OPTIONS.size()) {
            return new int[] {0, 0, 0, 0};
        }
        int x = bounds[0] + PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING;
        int y = bounds[1] + PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING + index * PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT;
        int width = Math.max(0, bounds[2] - PROJECT_SEARCH_PREFIX_DROPDOWN_PADDING * 2);
        int height = PROJECT_SEARCH_PREFIX_DROPDOWN_ROW_HEIGHT;
        return new int[] {x, y, width, height};
    }

    /**
     * 构建项目搜索前缀候选项展示文本，供测试断言。
     *
     * @param option 当前前缀候选项
     * @return 展示文本
     */
    private String buildProjectSearchPrefixOptionText(ProjectSearchPrefixOption option) {
        if (option == null) {
            return "";
        }
        return option.prefix + "  " + getProjectSearchPrefixOptionDescription(option);
    }

    /**
     * 返回项目搜索前缀候选项的展示说明，优先使用翻译文本，缺失时回退到内置文案。
     *
     * @param option 当前前缀候选项
     * @return 候选项说明文本
     */
    private String getProjectSearchPrefixOptionDescription(ProjectSearchPrefixOption option) {
        if (option == null) {
            return "";
        }
        String translated = Component.translatable(option.descKey).getString();
        if (translated != null && !translated.isBlank() && !option.descKey.equals(translated)) {
            return translated;
        }
        return option.fallbackZh.isEmpty() ? option.fallbackEn : option.fallbackZh;
    }

    /**
     * 在任务列表顶部绘制当前空间和项目名称，补足按钮左侧的空白区域。
     *
     * @param context 当前绘制上下文
     */
    private void renderContentHeaderSummary(GuiGraphics context) {
        if (layoutMetrics == null || configButton == null) {
            return;
        }
        String summaryText = buildContentHeaderSummaryText();
        if (summaryText.isEmpty()) {
            return;
        }
        float scale = 1.0F;
        int startX = layoutMetrics.contentBounds.x + getContentControlInset();
        if (sidebarToggleButton != null && sidebarToggleButton.visible) {
            startX = sidebarToggleButton.getX() + sidebarToggleButton.getWidth() + getContentActionGap();
        }
        int maxWidth = configButton.getX() - getContentActionGap() - startX;
        if (maxWidth <= 12) {
            return;
        }
        String displayText = trimTextToWidth(summaryText, Math.round(maxWidth / scale));
        if (displayText.isEmpty()) {
            return;
        }
        int textHeight = Math.max(1, Math.round(this.font.lineHeight * scale));
        int drawY = configButton.getY() + Math.max(0, (configButton.getHeight() - textHeight) / 2);
        context.pose().pushPose();
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(this.font, displayText, Math.round(startX / scale), Math.round(drawY / scale), 0xFFFFFFFF, false);
        context.pose().popPose();
    }

    /**
     * 生成任务列表顶部摘要文本，内容为当前空间和项目名称。
     *
     * @return 顶部摘要文本
     */
    private String buildContentHeaderSummaryText() {
        String scopeText = Component.translatable(currentSpaceMode == SpaceMode.TEAM
                ? "gui.todolist.scope.team"
                : "gui.todolist.scope.personal").getString();
        if (currentProject == null) {
            return scopeText;
        }
        String projectName = ProjectNameFormatter.toDisplayText(currentProject).getString().trim();
        if (projectName.isEmpty()) {
            return scopeText;
        }
        return scopeText + " / " + projectName;
    }

    /**
     * 根据可用宽度裁剪文本，超出时追加省略号。
     *
     * @param text 原始文本
     * @param maxWidth 最大宽度
     * @return 裁剪后的文本
     */
    private String trimTextToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = this.font.width("...");
        int coreWidth = Math.max(0, maxWidth - ellipsisWidth);
        String core = this.font.plainSubstrByWidth(text, coreWidth).trim();
        if (core.isEmpty()) {
            return "";
        }
        return core + "...";
    }

    /**
     * 绘制主界面的面板背景，包括内容区、侧栏和详情区。
     *
     * @param context 当前绘制上下文
     */
    /**
     * 绘制底部快速新增输入框左侧的加号标记，强化“新增任务”语义。
     *
     * @param context 当前绘制上下文
     */
    private void renderQuickAddMarker(GuiGraphics context) {
        // 按新的界面方案移除底部新增标识，保留空实现以兼容测试辅助方法。
    }

    /**
     * 计算底部快速新增标记区域的边界。
     *
     * @return 标记区域边界数组，格式为 x、y、width、height
     */
    private int[] getQuickAddMarkerBounds() {
        return new int[] {0, 0, 0, 0};
    }

    /**
     * 判断鼠标是否命中底部快速新增标记区域。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 命中新增标记时返回 {@code true}
     */
    private boolean isMouseOverQuickAddMarker(double mouseX, double mouseY) {
        return false;
    }

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
     * 根据给定边界绘制单个面板背景，并按需叠加遮罩样式。
     *
     * @param context 当前绘制上下文
     * @param bounds 面板边界
     * @param overlay 是否使用覆盖层样式
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
     * 保存个人任务数据。
     *
     * @throws Exception 当本地存储或同步过程失败时抛出
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
     * 保存团队任务数据。
     *
     * @throws Exception 当团队任务存储或同步失败时抛出
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
        if (searchField != null && searchField.isMouseOver(mouseX, mouseY)) return true;
        if (projectSearchField != null && projectSearchField.isMouseOver(mouseX, mouseY)) return true;
        if (quickAddField != null && quickAddField.isMouseOver(mouseX, mouseY)) return true;
        if (isMouseOverQuickAddMarker(mouseX, mouseY)) return true;
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

    /**
     * 判断点击位置是否落在任一文本输入框上。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 若命中文本输入框则返回 {@code true}
     */
    private boolean isMouseOverAnyTextField(double mouseX, double mouseY) {
        if (searchField != null && searchField.isMouseOver(mouseX, mouseY)) return true;
        if (projectSearchField != null && projectSearchField.isMouseOver(mouseX, mouseY)) return true;
        if (quickAddField != null && quickAddField.isMouseOver(mouseX, mouseY)) return true;
        if (isMouseOverQuickAddMarker(mouseX, mouseY)) return true;
        if (titleField != null && titleField.visible && titleField.isMouseOver(mouseX, mouseY)) return true;
        if (descField != null && descField.visible && descField.isMouseOver(mouseX, mouseY)) return true;
        return tagField != null && tagField.visible && tagField.isMouseOver(mouseX, mouseY);
    }

    /**
     * 清理当前界面内所有文本输入框的焦点。
     */
    private void clearTextFieldFocus() {
        if (searchField != null) {
            searchField.setFocused(false);
        }
        if (projectSearchField != null) {
            projectSearchField.setFocused(false);
        }
        closeProjectSearchPrefixDropdown();
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
        this.setFocused(null);
    }

    /**
     * 判断点击是否发生在详情面板的空白区域。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 若命中详情区空白则返回 {@code true}
     */
    private boolean isClickInDetailBlankArea(double mouseX, double mouseY) {
        return layoutMetrics != null
                && layoutMetrics.detailVisible
                && layoutMetrics.detailBounds.contains(mouseX, mouseY)
                && !isClickInEditArea(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncTaskListReorderState();
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
        if (button == 0 && handleProjectSearchPrefixDropdownClick(mouseX, mouseY)) {
            return true;
        }
        if ((button == 0 || button == 1) && shouldShowProjectSearchPrefixDropdown()
                && !isProjectSearchFieldHit(mouseX, mouseY)
                && !isInsideProjectSearchPrefixDropdown(mouseX, mouseY)) {
            closeProjectSearchPrefixDropdown();
        }
        if (button == 0 && canUseProjectSearchPrefixDropdown() && isProjectSearchFieldHit(mouseX, mouseY)) {
            openProjectSearchPrefixDropdown();
        }
        if (taskListWidget != null && taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            pendingClickSelectionTask = null;
            closeTaskContextMenu();
            return true;
        }
        if (projectListWidget != null && isSidebarPanelVisible() && projectListWidget.mouseClicked(mouseX, mouseY, button)) {
            pendingClickSelectionTask = null;
            closeTaskContextMenu();
            return true;
        }

        if (taskListWidget != null) {
            TaskListWidget.TaskSectionHitResult sectionHit = taskListWidget.getSectionAt(mouseX, mouseY);
            if (button == 0
                    && sectionHit != null
                    && sectionHit.getRowType() == TaskListWidget.RowType.SECTION_HEADER
                    && toggleTaskSection(sectionHit.getSectionId())) {
                applySearchFilter();
                pendingClickSelectionTask = null;
                taskRowDragInProgress = false;
                taskRowDragOrderSnapshot = List.of();
                closeTaskContextMenu();
                return true;
            }
            Task clickedTask = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
            if (clickedTask != null) {
                if (button == 0 && isTaskReorderAllowedInCurrentView() && taskListWidget.canStartDrag(clickedTask)) {
                    String dragSectionId = sectionHit == null ? "active" : sectionHit.getSectionId();
                    taskListWidget.armPendingTaskDrag(clickedTask, dragSectionId, mouseX, mouseY);
                    pendingClickSelectionTask = clickedTask;
                    taskRowDragInProgress = false;
                    taskRowDragOrderSnapshot = getCurrentVisibleActiveTaskIds();
                    closeTaskContextMenu();
                    return true;
                }
                pendingClickSelectionTask = null;
                taskRowDragInProgress = false;
                taskRowDragOrderSnapshot = List.of();
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

        if (button == 0 && isMouseOverQuickAddMarker(mouseX, mouseY) && quickAddField != null) {
            quickAddField.setFocused(true);
            this.setFocused(quickAddField);
            closeTaskContextMenu();
            return true;
        }

        if (button == 0 && !isMouseOverAnyTextField(mouseX, mouseY)) {
            clearTextFieldFocus();
        }

        boolean cleared = false;
        boolean detailBlankClicked = button == 0 && selectedTask != null && isClickInDetailBlankArea(mouseX, mouseY);
        pendingClickSelectionTask = null;
        taskRowDragInProgress = false;
        taskRowDragOrderSnapshot = List.of();
        if (button == 0 && selectedTask != null && !detailBlankClicked && !isClickInEditArea(mouseX, mouseY)) {
            clearSelectedTask();
            cleared = true;
        }
        if (button == 0 || button == 1) {
            closeTaskContextMenu();
        }
        if (detailBlankClicked) {
            return true;
        }

        if (this.minecraft == null || Minecraft.getInstance() == null) {
            return cleared;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        return handled || cleared;
    }

    /**
     * 处理项目搜索前缀下拉面板的点击事件。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 命中候选项时返回 {@code true}
     */
    private boolean handleProjectSearchPrefixDropdownClick(double mouseX, double mouseY) {
        if (!shouldShowProjectSearchPrefixDropdown()) {
            return false;
        }
        int index = getProjectSearchPrefixOptionIndexAt(mouseX, mouseY);
        if (index < 0 || index >= PROJECT_SEARCH_PREFIX_OPTIONS.size()) {
            return false;
        }
        applyProjectSearchPrefix(PROJECT_SEARCH_PREFIX_OPTIONS.get(index).prefix);
        if (projectSearchField != null) {
            projectSearchField.setFocused(true);
            this.setFocused(projectSearchField);
        }
        return true;
    }

    /**
     * 判断鼠标是否命中项目搜索输入框。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 命中搜索框时返回 {@code true}
     */
    private boolean isProjectSearchFieldHit(double mouseX, double mouseY) {
        return projectSearchField != null
                && projectSearchField.visible
                && projectSearchField.isMouseOver(mouseX, mouseY);
    }

    /**
     * 判断坐标是否位于项目搜索前缀下拉面板内。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 命中下拉面板时返回 {@code true}
     */
    private boolean isInsideProjectSearchPrefixDropdown(double mouseX, double mouseY) {
        if (!shouldShowProjectSearchPrefixDropdown()) {
            return false;
        }
        int[] bounds = getProjectSearchPrefixDropdownBounds();
        return mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3];
    }

    /**
     * 返回指定坐标命中的项目搜索前缀候选项索引。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 候选项索引；未命中时返回 -1
     */
    private int getProjectSearchPrefixOptionIndexAt(double mouseX, double mouseY) {
        for (int i = 0; i < PROJECT_SEARCH_PREFIX_OPTIONS.size(); i++) {
            int[] bounds = getProjectSearchPrefixOptionBounds(i);
            if (mouseX >= bounds[0] && mouseX < bounds[0] + bounds[2]
                    && mouseY >= bounds[1] && mouseY < bounds[1] + bounds[3]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将指定项目搜索前缀写入搜索框，并尽量保留现有名称关键字。
     *
     * @param prefix 目标搜索前缀
     */
    private void applyProjectSearchPrefix(String prefix) {
        if (projectSearchField == null || prefix == null || prefix.isEmpty()) {
            return;
        }
        String current = projectSearchField.getValue();
        String suffix = extractProjectSearchQuerySuffix(current);
        String nextValue = suffix.isEmpty() ? prefix + " " : prefix + " " + suffix;
        projectSearchField.setValue(nextValue);
        projectSearchQuery = nextValue;
        updateProjectList();
        closeProjectSearchPrefixDropdown();
    }

    /**
     * 提取项目搜索输入中去掉已识别前缀后的剩余关键字。
     *
     * @param current 当前搜索文本
     * @return 去掉前缀后的关键字
     */
    private String extractProjectSearchQuerySuffix(String current) {
        String normalized = current == null ? "" : current.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        ProjectSearchRoleFilter roleFilter = parseProjectSearchRoleFilter(lower);
        if (roleFilter != ProjectSearchRoleFilter.NONE) {
            String prefix = getProjectSearchRolePrefix(roleFilter);
            return normalized.substring(prefix.length()).trim();
        }
        if (!normalized.startsWith("@")) {
            return normalized;
        }
        int separatorIndex = findFirstWhitespaceIndex(normalized);
        if (separatorIndex < 0) {
            return "";
        }
        return normalized.substring(separatorIndex).trim();
    }

    /**
     * 返回字符串中第一个空白字符的位置，未找到时返回 -1。
     *
     * @param text 待检查的文本
     * @return 第一个空白字符的位置
     */
    private int findFirstWhitespaceIndex(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        syncTaskListReorderState();
        if (taskListWidget != null && taskListWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            if (pendingClickSelectionTask != null || taskListWidget.getDropTargetIndexForTest() >= 0) {
                taskRowDragInProgress = true;
                markUnsaved();
            }
            pendingClickSelectionTask = null;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncTaskListReorderState();
        if (taskListWidget != null && taskListWidget.mouseReleased(mouseX, mouseY, button)) {
            if (taskRowDragInProgress || taskListWidget.getDropTargetIndexForTest() >= 0) {
                markUnsaved();
                List<Task> reorderedActiveTasks = taskListWidget.getTasks().stream()
                        .filter(Objects::nonNull)
                        .filter(task -> !task.isCompleted())
                        .toList();
                List<String> reorderedIds = reorderedActiveTasks.stream()
                        .map(Task::getId)
                        .filter(Objects::nonNull)
                        .toList();
                if (!reorderedIds.equals(taskRowDragOrderSnapshot)) {
                    onManualReorderActiveTasks(reorderedActiveTasks);
                }
            }
            pendingClickSelectionTask = null;
            taskRowDragInProgress = false;
            taskRowDragOrderSnapshot = List.of();
            return true;
        }
        if (button == 0 && pendingClickSelectionTask != null) {
            Task taskToSelect = pendingClickSelectionTask;
            pendingClickSelectionTask = null;
            taskRowDragInProgress = false;
            taskRowDragOrderSnapshot = List.of();
            selectTask(taskToSelect);
            return true;
        }
        pendingClickSelectionTask = null;
        taskRowDragInProgress = false;
        taskRowDragOrderSnapshot = List.of();
        return super.mouseReleased(mouseX, mouseY, button);
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
     * 在关闭界面且未保存时，按需丢弃个人任务的临时改动。
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

    // 事件处理

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
        selectedPriority = task.getPriority();
        detailDraft = createDetailDraft(task);
        detailOverlayVisible = true;
        rebuildUI();
    }

    private void clearSelectedTask() {
        selectedTask = null;
        detailDraft = null;
        detailOverlayVisible = false;
        rebuildUI();
    }

    /**
     * 根据任务创建详情面板使用的编辑草稿。
     *
     * @param task 目标任务
     * @return 对应的详情草稿；若任务为空则返回 {@code null}
     */
    private TaskDetailDraft createDetailDraft(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskDetailDraft(task.getId(), task.getTitle(), task.getDescription(), joinTaskTags(task));
    }

    /**
     * 将当前详情草稿状态同步到标题、描述和标签输入控件。
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
     * 根据当前选中任务和权限状态更新详情控件的可编辑性。
     */
    private void applyDetailWidgetEditability() {
        boolean detailVisible = layoutMetrics == null ? selectedTask != null : layoutMetrics.detailVisible;
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
     * 判断当前选中的任务详情是否允许编辑。
     *
     * @return 允许编辑时返回 {@code true}
     */
    private boolean canEditSelectedTaskDetails() {
        return selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted() && canEditTask(selectedTask);
    }

    /**
     * 让详情标题进入编辑状态。
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
     * 响应详情标题输入框内容变化。
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
     * 响应详情描述输入框内容变化。
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
     * 响应详情标签输入框内容变化。
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
     * 将任务标签列表拼接为输入框使用的文本。
     *
     * @param task 目标任务
     * @return 逗号分隔的标签文本
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
        return viewMode == ViewMode.PERSONAL
                || viewMode == ViewMode.TEAM_UNASSIGNED
                || viewMode == ViewMode.TEAM_ALL;
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
        boolean detailVisible = layoutMetrics == null ? hasSelection : layoutMetrics.detailVisible;
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
        Task.Priority previousPriority = task.getPriority();
        if (previousPriority == priority) {
            closeTaskContextMenu();
            return;
        }
        task.setPriority(priority);
        reorderCurrentViewActiveTasksAfterPriorityChange(task);
        setSelectedPriority(priority);
        markUnsaved();
        refreshTaskList();
        if (taskListWidget != null) {
            taskListWidget.ensureVisible(task);
        }
        ClientBridge.ops().sendUpdateTask(task);
        closeTaskContextMenu();
    }

    /**
     * 处理任务右键菜单中的删除操作入口，先弹出二次确认，再在确认后真正执行删除。
     *
     * @param task 目标任务
     */
    private void deleteTaskFromContextMenu(Task task) {
        if (task == null || !canDeleteTask(task)) {
            closeTaskContextMenu();
            return;
        }
        closeTaskContextMenu();
        openDeleteTaskConfirmScreen(task);
    }

    /**
     * 打开任务删除确认弹窗，避免误删任务。
     *
     * @param task 待删除任务
     */
    private void openDeleteTaskConfirmScreen(Task task) {
        if (task == null || task.getId() == null || minecraft == null) {
            return;
        }
        String taskId = task.getId();
        String taskTitle = task.getTitle() == null ? "" : task.getTitle();
        Component message = Component.translatable("gui.todolist.task.delete_confirm.message", taskTitle);
        minecraft.setScreen(new ConfirmActionScreen(
                this,
                Component.translatable("gui.todolist.task.delete_confirm.title"),
                message,
                Component.translatable("gui.todolist.delete"),
                () -> confirmDeleteTask(taskId)
        ));
    }

    /**
     * 在用户确认后真正删除任务，并同步刷新选中态与列表显示。
     *
     * @param taskId 待删除任务 ID
     */
    private void confirmDeleteTask(String taskId) {
        if (taskManager == null || taskId == null || taskId.isBlank()) {
            return;
        }
        taskManager.deleteTask(taskId);
        if (selectedTask != null && selectedTask.getId() != null && selectedTask.getId().equals(taskId)) {
            clearSelectedTask();
        }
        markUnsaved();
        refreshTaskList();
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

    /**
     * 处理未完成任务手动拖拽排序后的顺序同步。
     *
     * @param reorderedActiveTasks 拖拽后的未完成任务顺序
     */
    private void onManualReorderActiveTasks(List<Task> reorderedActiveTasks) {
        if (!isTaskReorderAllowedInCurrentView() || taskManager == null || reorderedActiveTasks == null || reorderedActiveTasks.size() < 2) {
            return;
        }
        List<String> orderedTaskIds = reorderedActiveTasks.stream()
                .filter(Objects::nonNull)
                .map(Task::getId)
                .filter(Objects::nonNull)
                .toList();
        if (orderedTaskIds.size() < 2) {
            return;
        }
        if (!taskManager.reorderTasks(orderedTaskIds)) {
            return;
        }
        if (selectedTask != null && selectedTask.getId() != null) {
            Task refreshedSelectedTask = taskManager.getTask(selectedTask.getId());
            if (refreshedSelectedTask != null) {
                selectedTask = refreshedSelectedTask;
            }
        }
        markUnsaved();
        refreshTaskList();
    }

    /**
     * 在当前视图内按优先级重新归位未完成任务，同时保留同优先级内部的拖拽顺序。
     *
     * @param updatedTask 刚刚修改优先级的任务
     */
    private void reorderCurrentViewActiveTasksAfterPriorityChange(Task updatedTask) {
        if (updatedTask == null || updatedTask.getId() == null || taskManager == null) {
            return;
        }
        List<Task> scopedActiveTasks = getCurrentViewActiveTasksForOrdering();
        if (scopedActiveTasks.size() < 2) {
            return;
        }
        List<Task> highTasks = new ArrayList<>();
        List<Task> mediumTasks = new ArrayList<>();
        List<Task> lowTasks = new ArrayList<>();
        boolean taskIncluded = false;

        for (Task task : scopedActiveTasks) {
            if (task == null || task.getId() == null) {
                continue;
            }
            if (updatedTask.getId().equals(task.getId())) {
                taskIncluded = true;
                continue;
            }
            appendTaskToPriorityBucket(task, highTasks, mediumTasks, lowTasks);
        }
        if (!taskIncluded) {
            return;
        }
        appendTaskToPriorityBucket(updatedTask, highTasks, mediumTasks, lowTasks);

        List<String> orderedTaskIds = new ArrayList<>(highTasks.size() + mediumTasks.size() + lowTasks.size());
        appendTaskIds(highTasks, orderedTaskIds);
        appendTaskIds(mediumTasks, orderedTaskIds);
        appendTaskIds(lowTasks, orderedTaskIds);
        taskManager.reorderTasks(orderedTaskIds);
    }

    /**
     * 返回当前视图内参与排序的未完成任务，并保持任务管理器中的稳定顺序。
     *
     * @return 当前视图范围内的未完成任务
     */
    private List<Task> getCurrentViewActiveTasksForOrdering() {
        if (taskManager == null) {
            return List.of();
        }
        return applyAssignedFilterIfNeeded(taskManager.getIncompleteTasks());
    }

    /**
     * 将任务追加到对应优先级的分组列表中。
     *
     * @param task 目标任务
     * @param highTasks 高优先级任务分组
     * @param mediumTasks 中优先级任务分组
     * @param lowTasks 低优先级任务分组
     */
    private void appendTaskToPriorityBucket(Task task, List<Task> highTasks, List<Task> mediumTasks, List<Task> lowTasks) {
        if (task == null) {
            return;
        }
        if (task.getPriority() == Task.Priority.HIGH) {
            highTasks.add(task);
            return;
        }
        if (task.getPriority() == Task.Priority.LOW) {
            lowTasks.add(task);
            return;
        }
        mediumTasks.add(task);
    }

    /**
     * 按当前顺序将任务 ID 写入目标列表，供任务管理器执行稳定重排。
     *
     * @param tasks 源任务列表
     * @param targetIds 目标 ID 列表
     */
    private void appendTaskIds(List<Task> tasks, List<String> targetIds) {
        if (tasks == null || targetIds == null) {
            return;
        }
        for (Task task : tasks) {
            if (task != null && task.getId() != null) {
                targetIds.add(task.getId());
            }
        }
    }

    /**
     * 根据当前视图和权限状态同步任务列表的拖拽排序能力。
     */
    private void syncTaskListReorderState() {
        if (taskListWidget == null) {
            return;
        }
        taskListWidget.setTaskReorderEnabled(isTaskReorderAllowedInCurrentView());
    }

    /**
     * 判断当前视图是否允许对任务进行拖拽重排。
     *
     * @return 允许拖拽排序时返回 {@code true}
     */
    private boolean isTaskReorderAllowedInCurrentView() {
        if (currentProject == null) {
            return false;
        }
        if (viewMode == ViewMode.PERSONAL) {
            return true;
        }
        Role role = getCurrentRole();
        ViewScope scope = getCurrentViewScope();
        boolean projectMember = isCurrentPlayerProjectMember();
        boolean allowMemberCreate = currentProject.isAllowMemberCreate();
        return PermissionCenter.canPerform(Operation.EDIT_TASK, role,
                new Context(scope, false, false, false, false, false, projectMember, allowMemberCreate));
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

    // 绘制标签使用的辅助方法
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
    
    /**
     * 按当前空间、搜索词和项目作用域刷新左侧项目列表。
     */
    private void updateProjectList() {
        if (projectListWidget == null) return;
        List<Project> visibleProjects = buildVisibleProjectsForSidebar();
        projectListWidget.setProjects(visibleProjects);
        projectListWidget.setProjectTaskCounts(buildSidebarProjectTaskCounts(visibleProjects));
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();
    }

    /**
     * 为侧栏当前可见的项目构建任务数映射，供项目列表右侧数量提示使用。
     *
     * @param visibleProjects 当前侧栏可见的项目列表
     * @return 项目 ID 到任务数的映射
     */
    private Map<String, Integer> buildSidebarProjectTaskCounts(List<Project> visibleProjects) {
        Map<String, Integer> taskCounts = new HashMap<>();
        TaskManager countSource = projectScopeFilter == Project.Scope.TEAM ? teamTaskManager : personalTaskManager;
        if (countSource == null || visibleProjects == null || visibleProjects.isEmpty()) {
            return taskCounts;
        }
        for (Project project : visibleProjects) {
            if (project == null || project.getId() == null || project.getId().isEmpty()) {
                continue;
            }
            taskCounts.put(project.getId(), countSource.getTasksByProject(project.getId()).size());
        }
        return taskCounts;
    }

    /**
     * 构建侧栏中需要展示的项目列表，并按当前作用域过滤结果。
     *
     * @return 当前侧栏应显示的项目集合
     */
    private List<Project> buildVisibleProjectsForSidebar() {
        List<Project> all = new ArrayList<>();
        all.addAll(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
        all.addAll(projectManager.getProjectsByScope(Project.Scope.TEAM));

        Project defaultProject = null;
        for (Project project : all) {
            if (project == null || project.getScope() != projectScopeFilter) {
                continue;
            }
            if (projectScopeFilter == Project.Scope.PERSONAL && project.isDefaultPersonalProject()) {
                defaultProject = project;
                break;
            }
            if (projectScopeFilter == Project.Scope.TEAM && project.isDefaultTeamProject()) {
                defaultProject = project;
                break;
            }
        }

        List<Project> filtered = new ArrayList<>();
        String rawQuery = projectSearchQuery == null ? "" : projectSearchQuery.trim();
        ProjectSearchQuery parsedQuery = parseProjectSearchQuery(projectSearchQuery);
        String playerUuid = getCurrentPlayerUuid();
        for (Project project : all) {
            if (project == null || project.getScope() != projectScopeFilter) {
                continue;
            }
            if (projectScopeFilter == Project.Scope.TEAM
                    && parsedQuery.roleFilter != ProjectSearchRoleFilter.NONE
                    && !matchesTeamProjectSearchRoleFilter(project, parsedQuery.roleFilter, playerUuid)) {
                continue;
            }
            String searchableName = ProjectNameFormatter.toDisplayText(project).getString().toLowerCase(Locale.ROOT);
            if (!parsedQuery.nameQuery.isEmpty() && !searchableName.contains(parsedQuery.nameQuery)) {
                continue;
            }
            filtered.add(project);
        }

        if (defaultProject != null && rawQuery.isEmpty() && !containsProject(filtered, defaultProject.getId())) {
            filtered.add(0, defaultProject);
        }
        return filtered;
    }

    /**
     * 解析项目搜索输入中的角色前缀与名称关键字。
     *
     * @param rawQuery 原始搜索文本
     * @return 解析后的项目搜索查询
     */
    private ProjectSearchQuery parseProjectSearchQuery(String rawQuery) {
        String normalized = rawQuery == null ? "" : rawQuery.trim();
        if (normalized.isEmpty()) {
            return new ProjectSearchQuery(ProjectSearchRoleFilter.NONE, "");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        ProjectSearchRoleFilter roleFilter = parseProjectSearchRoleFilter(lower);
        if (roleFilter == ProjectSearchRoleFilter.NONE) {
            return new ProjectSearchQuery(ProjectSearchRoleFilter.NONE, lower);
        }
        String prefix = getProjectSearchRolePrefix(roleFilter);
        String suffix = normalized.substring(prefix.length()).trim().toLowerCase(Locale.ROOT);
        return new ProjectSearchQuery(roleFilter, suffix);
    }

    /**
     * 解析开头的项目搜索前缀对应的筛选模式。
     *
     * @param lowerQuery 小写后的搜索文本
     * @return 对应的角色筛选模式
     */
    private ProjectSearchRoleFilter parseProjectSearchRoleFilter(String lowerQuery) {
        if (matchesProjectSearchPrefix(lowerQuery, "@me")) {
            return ProjectSearchRoleFilter.CREATED_BY_ME;
        }
        if (matchesProjectSearchPrefix(lowerQuery, "@ma")) {
            return ProjectSearchRoleFilter.MANAGED_BY_ME;
        }
        if (matchesProjectSearchPrefix(lowerQuery, "@in")) {
            return ProjectSearchRoleFilter.JOINED_BY_ME;
        }
        return ProjectSearchRoleFilter.NONE;
    }

    /**
     * 判断搜索文本是否以指定前缀开头，且后续为空或为空白分隔。
     *
     * @param query 当前搜索文本
     * @param prefix 待匹配前缀
     * @return 命中前缀时返回 {@code true}
     */
    private boolean matchesProjectSearchPrefix(String query, String prefix) {
        if (query == null || prefix == null || !query.startsWith(prefix)) {
            return false;
        }
        return query.length() == prefix.length() || Character.isWhitespace(query.charAt(prefix.length()));
    }

    /**
     * 返回指定角色筛选模式对应的项目搜索前缀。
     *
     * @param roleFilter 角色筛选模式
     * @return 对应的搜索前缀
     */
    private String getProjectSearchRolePrefix(ProjectSearchRoleFilter roleFilter) {
        return switch (roleFilter) {
            case CREATED_BY_ME -> "@me";
            case MANAGED_BY_ME -> "@ma";
            case JOINED_BY_ME -> "@in";
            case NONE -> "";
        };
    }

    /**
     * 判断团队项目是否符合当前前缀角色筛选条件。
     *
     * @param project 当前项目
     * @param roleFilter 当前角色筛选模式
     * @param playerUuid 当前玩家 UUID
     * @return 命中筛选条件时返回 {@code true}
     */
    private boolean matchesTeamProjectSearchRoleFilter(Project project, ProjectSearchRoleFilter roleFilter, String playerUuid) {
        if (project == null || playerUuid == null || playerUuid.isEmpty()) {
            return roleFilter == ProjectSearchRoleFilter.NONE;
        }
        if (roleFilter == ProjectSearchRoleFilter.CREATED_BY_ME) {
            return playerUuid.equals(project.getOwnerUuid());
        }
        Project.ProjectRole memberRole = project.getMemberRole(playerUuid);
        if (roleFilter == ProjectSearchRoleFilter.MANAGED_BY_ME) {
            return playerUuid.equals(project.getOwnerUuid())
                    || memberRole == Project.ProjectRole.PROJECT_MANAGER
                    || memberRole == Project.ProjectRole.LEAD;
        }
        if (roleFilter == ProjectSearchRoleFilter.JOINED_BY_ME) {
            return memberRole != null;
        }
        return true;
    }

    /**
     * 返回当前玩家 UUID，供团队项目搜索前缀筛选使用。
     *
     * @return 当前玩家 UUID；不存在时返回空字符串
     */
    private String getCurrentPlayerUuid() {
        return this.minecraft == null || this.minecraft.player == null ? "" : this.minecraft.player.getUUID().toString();
    }

    /**
     * 判断项目列表中是否已经包含指定项目 ID。
     *
     * @param projects 待检查的项目列表
     * @param projectId 目标项目 ID
     * @return 如果列表中包含该项目则返回 {@code true}
     */
    private boolean containsProject(List<Project> projects, String projectId) {
        if (projects == null || projectId == null || projectId.isEmpty()) {
            return false;
        }
        for (Project project : projects) {
            if (project != null && projectId.equals(project.getId())) {
                return true;
            }
        }
        return false;
    }

    private void updateProjectActionButtons() {
        if (editProjectBtn == null || deleteProjectBtn == null || applyJoinProjectBtn == null) {
            return;
        }
        if (currentProject == null) {
            editProjectBtn.active = false;
            editProjectBtn.setMessage(getSidebarEditButtonText(true));
            deleteProjectBtn.visible = true;
            deleteProjectBtn.active = false;
            deleteProjectBtn.setMessage(getSidebarDeleteButtonText());
            applyJoinProjectBtn.visible = false;
            applyJoinProjectBtn.active = false;
            applyJoinProjectBtn.setMessage(getSidebarJoinProjectButtonText());
            syncBottomProjectButtonsState();
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            editProjectBtn.active = true;
            editProjectBtn.setMessage(getSidebarEditButtonText(true));
            deleteProjectBtn.visible = true;
            deleteProjectBtn.active = canDeleteCurrentProject();
            deleteProjectBtn.setMessage(getSidebarDeleteButtonText());
            applyJoinProjectBtn.visible = false;
            applyJoinProjectBtn.active = false;
            applyJoinProjectBtn.setMessage(getSidebarJoinProjectButtonText());
            syncBottomProjectButtonsState();
            return;
        }
        Role role = getCurrentRole();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        boolean canEdit = PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx);
        editProjectBtn.active = true;
        editProjectBtn.setMessage(getSidebarEditButtonText(canEdit));
        boolean member = isCurrentPlayerProjectMember();
        if (!member) {
            deleteProjectBtn.visible = false;
            deleteProjectBtn.active = false;
            deleteProjectBtn.setMessage(getSidebarDeleteButtonText());
            applyJoinProjectBtn.visible = true;
            applyJoinProjectBtn.active = true;
            applyJoinProjectBtn.setMessage(getSidebarJoinProjectButtonText());
            syncBottomProjectButtonsState();
            return;
        }
        applyJoinProjectBtn.visible = false;
        applyJoinProjectBtn.active = false;
        applyJoinProjectBtn.setMessage(getSidebarJoinProjectButtonText());
        deleteProjectBtn.visible = true;
        deleteProjectBtn.active = canDeleteCurrentProject();
        deleteProjectBtn.setMessage(getSidebarDeleteButtonText());
        syncBottomProjectButtonsState();
    }

    /**
     * 同步底部项目操作按钮的显隐、可用状态和文案。
     */
    private void syncBottomProjectButtonsState() {
        boolean sidebarVisible = layoutMetrics == null || layoutMetrics.sidebarVisible;
        if (addProjectBtn != null) {
            addProjectBtn.visible = sidebarVisible;
            addProjectBtn.active = sidebarVisible;
        }
        if (editProjectBtn != null) {
            editProjectBtn.visible = sidebarVisible;
            editProjectBtn.active = sidebarVisible && editProjectBtn.active;
        }
        if (deleteProjectBtn != null) {
            deleteProjectBtn.visible = sidebarVisible && deleteProjectBtn.visible;
            deleteProjectBtn.active = sidebarVisible && deleteProjectBtn.active;
        }
        if (applyJoinProjectBtn != null) {
            applyJoinProjectBtn.visible = sidebarVisible && applyJoinProjectBtn.visible;
            applyJoinProjectBtn.active = sidebarVisible && applyJoinProjectBtn.active;
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
        filterTasks("active");
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
     * 解析项目成员的显示名称，优先使用缓存名称，其次读取在线玩家名称。
     *
     * @param project 当前项目
     * @param memberUuid 成员 UUID
     * @return 成员显示名称；无法解析时回退为 UUID
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
            // 玩家信息读取失败时回退到 UUID，避免界面出现空名称。
        }
        if (displayName == null || displayName.isBlank()) {
            return memberUuid;
        }
        return displayName;
    }

    /**
     * 表示一个可被指派的成员，统一保存 UUID 和显示名称。
     */
    private static final class AssignableMember {
        private final String uuid;
        private final String displayName;

        /**
         * 创建一个可指派成员对象。
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
        currentFilter = "active";
        List<Task> result = taskManager == null ? new ArrayList<>() : taskManager.getIncompleteTasks();

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

        baseFilteredTasks = applyAssignedFilterIfNeeded(result);
        applySearchFilter();
        if (selectedTask != null && !isSelectedTaskValid()) {
            clearSelectedTask();
        }
        updateViewButtonsState();
    }

    private void refreshTaskList() {
        filterTasks("active");
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
            taskListWidget.setTaskReorderEnabled(isTaskReorderAllowedInCurrentView());
            taskListWidget.setSections(buildTaskPaneSections());
        }
    }

    /**
     * 构建任务面板的分组模型，包含未完成任务和可折叠的已完成任务。
     *
     * @return 任务列表需要渲染的分组模型集合
     */
    private List<TaskListWidget.SectionModel> buildTaskPaneSections() {
        List<TaskListWidget.SectionModel> sections = new ArrayList<>();
        List<Task> activeTasks = filteredTasks == null ? List.of() : List.copyOf(filteredTasks);

        sections.add(new TaskListWidget.SectionModel(
                "active",
                formatTaskSectionTitle("gui.todolist.active", activeTasks.size()),
                activeTasks,
                true,
                activeExpanded
        ));

        List<Task> completedTasks = buildCompletedTasksForCurrentView();
        sections.add(new TaskListWidget.SectionModel(
                "completed",
                formatTaskSectionTitle("gui.todolist.completed", completedTasks.size()),
                completedTasks,
                true,
                completedExpanded
        ));
        return sections;
    }

    /**
     * 生成任务分组标题文本，统一带上数量标记。
     *
     * @param translationKey 标题翻译键
     * @param count 当前分组任务数量
     * @return 格式化后的标题文本
     */
    private String formatTaskSectionTitle(String translationKey, int count) {
        return resolveTaskSectionLabel(translationKey) + "\uFF08" + count + "\uFF09";
    }

    /**
     * 解析任务分组标题的显示文本，并为离线测试环境提供兜底标签。
     *
     * @param translationKey 标题翻译键
     * @return 可展示的分组标题文本
     */
    private String resolveTaskSectionLabel(String translationKey) {
        String label = Component.translatable(translationKey).getString();
        if (!Objects.equals(label, translationKey)) {
            return label;
        }
        if ("gui.todolist.active".equals(translationKey)) {
            return "未完成";
        }
        if ("gui.todolist.completed".equals(translationKey)) {
            return "已完成";
        }
        return translationKey;
    }

    /**
     * 构建当前视图下需要展示的已完成任务列表。
     *
     * @return 当前视图下的已完成任务列表
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
     * 获取当前未完成任务分组中可见任务的 ID 顺序。
     *
     * @return 当前可见未完成任务 ID 列表
     */
    private List<String> getCurrentVisibleActiveTaskIds() {
        if (filteredTasks == null || filteredTasks.isEmpty()) {
            return List.of();
        }
        return filteredTasks.stream()
                .filter(Objects::nonNull)
                .filter(task -> !task.isCompleted())
                .map(Task::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 对任务列表应用优先级筛选。
     *
     * @param source 原始任务列表
     * @return 筛选后的任务列表
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
     * 对任务列表应用搜索关键字筛选。
     *
     * @param source 原始任务列表
     * @return 筛选后的任务列表
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
     * 将组件转换为测试使用的边界数组。
     *
     * @param widget 目标组件
     * @return 组件边界数组；组件为空时返回零值数组
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

    /**
     * 返回任务列表内容区控件的统一左右内边距，避免输入框和按钮贴边。
     *
     * @return 内容区控件内边距
     */
    private int getContentControlInset() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 6;
    }

    /**
     * 返回任务列表顶部工具栏距离内容区上边线的留白。
     *
     * @return 顶部留白
     */
    private int getContentTopPadding() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 8 : 10;
    }

    /**
     * 返回顶部“保存/取消/配置”按钮行的高度。
     *
     * @return 顶部按钮行高度
     */
    private int getContentTopBarHeight() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 19;
    }

    /**
     * 返回关键词搜索行和优先级按钮的高度。
     *
     * @return 搜索行高度
     */
    private int getContentSearchFieldHeight() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 19;
    }

    /**
     * 返回顶部工具栏和搜索行之间的纵向间距。
     *
     * @return 顶部纵向间距
     */
    private int getContentHeaderGap() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 5;
    }

    /**
     * 返回顶部按钮、筛选按钮等横向相邻控件之间的间距。
     *
     * @return 横向控件间距
     */
    private int getContentActionGap() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 5;
    }

    /**
     * 返回底部快速新增输入框的高度。
     *
     * @return 底部输入框高度
     */
    private int getContentQuickAddFieldHeight() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 19;
    }

    /**
     * 返回底部“保存 / 取消”操作行的高度。
     *
     * @return 底部操作行高度
     */
    /**
     * 返回底部快速新增标记区域的宽度。
     *
     * @return 快速新增标记宽度
     */
    private int getQuickAddMarkerWidth() {
        return 0;
    }

    /**
     * 返回底部快速新增标记与输入框之间的横向间距。
     *
     * @return 标记与输入框的横向间距
     */
    private int getQuickAddMarkerGap() {
        return 0;
    }

    private int getContentBottomActionRowHeight() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 20;
    }

    /**
     * 返回底部快速新增区域距内容区下边线的留白。
     *
     * @return 底部留白
     */
    private int getContentBottomPadding() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 6 : 8;
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
            return projectFiltered;
        }
        return projectFiltered;
    }
    
    private boolean isTrueSingleplayer() {
        return false;
    }

    /**
     * 返回左侧栏顶部切换按钮和搜索框使用的统一控件高度。
     *
     * @return 侧栏顶部控件高度
     */
    private int getSidebarControlHeight() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 20;
    }

    /**
     * 返回左侧栏项目搜索框的高度，使其视觉上比切换按钮更紧凑。
     *
     * @return 搜索框高度
     */
    private int getSidebarSearchFieldHeight() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 16 : 18;
    }

    /**
     * 返回左侧栏底部项目操作按钮的高度，小窗口下会略微收紧。
     *
     * @return 底部按钮高度
     */
    private int getSidebarBottomButtonHeight() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 18 : 20;
    }

    /**
     * 返回左侧栏分组之间的纵向间距，优先为项目列表让出更多可用高度。
     *
     * @return 分组间距
     */
    private int getSidebarSectionGap() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 6;
    }

    /**
     * 返回搜索框、项目列表和底部按钮之间的紧凑间距。
     *
     * @return 列表区域使用的间距
     */
    private int getSidebarProjectListGap() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 3 : 5;
    }

    /**
     * 返回左侧栏底部项目操作按钮之间的横向间距。
     *
     * @return 底部按钮横向间距
     */
    private int getSidebarBottomButtonGap() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 3 : 4;
    }

    /**
     * 返回左侧栏底部操作按钮距离底边的留白，避免按钮贴边。
     *
     * @return 底部留白
     */
    private int getSidebarBottomPadding() {
        return responsiveTier == ResponsiveTier.MINIMAL ? 4 : 6;
    }

    /**
     * 判断当前侧栏是否需要使用更紧凑的底部按钮文案。
     *
     * @return 需要紧凑按钮文案时返回 {@code true}
     */
    private boolean useCompactSidebarBottomButtons() {
        int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
        return responsiveTier == ResponsiveTier.MINIMAL || sidebarWidth > 0 && sidebarWidth <= 132;
    }

    /**
     * 返回左侧栏底部“添加项目”按钮的文案。
     *
     * @return 添加按钮文案
     */
    private Component getSidebarAddButtonText() {
        return Component.translatable("gui.todolist.add");
    }

    /**
     * 返回左侧栏底部“编辑/查看项目”按钮的文案。
     *
     * @param canEdit 当前是否允许编辑
     * @return 编辑或查看按钮文案
     */
    private Component getSidebarEditButtonText(boolean canEdit) {
        return Component.translatable(canEdit ? "gui.todolist.edit" : "gui.todolist.project.view");
    }

    /**
     * 返回左侧栏底部“删除项目”按钮的文案。
     *
     * @return 删除按钮文案
     */
    private Component getSidebarDeleteButtonText() {
        return Component.translatable("gui.todolist.delete");
    }

    /**
     * 返回左侧栏底部“申请加入项目”按钮的文案，小窗口下会自动缩短。
     *
     * @return 申请加入按钮文案
     */
    private Component getSidebarJoinProjectButtonText() {
        return Component.translatable(useCompactSidebarBottomButtons()
                ? "gui.todolist.project.join.compact"
                : "gui.todolist.project.join.apply");
    }

    /**
     * 根据当前空间模式和侧栏宽度重排左侧视图按钮，优先给项目列表让出高度。
     */
    private void layoutSidebarViewButtons() {
        if (layoutMetrics == null || myViewButton == null || unassignedViewButton == null || allViewButton == null) {
            return;
        }
        int sidebarInset = responsiveTier == ResponsiveTier.MINIMAL ? 5 : 7;
        int sidebarInnerX = layoutMetrics.sidebarBounds.x + sidebarInset;
        int sidebarInnerWidth = Math.max(80, layoutMetrics.sidebarBounds.width - sidebarInset * 2);
        int viewButtonsY = layoutMetrics.sidebarBounds.y
                + (responsiveTier == ResponsiveTier.MINIMAL ? 8 : 10)
                + getSidebarControlHeight()
                + getSidebarSectionGap();
        int teamViewButtonGap = 4;
        int teamViewButtonWidth = Math.max(26, (sidebarInnerWidth - teamViewButtonGap * 2) / 3);

        if (currentSpaceMode == SpaceMode.TEAM) {
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

    /**
     * 根据侧栏可用宽度返回团队视图按钮文案，小窗口下会自动缩短“待领取”。
     *
     * @param option 视图选项
     * @return 当前按钮应显示的文本
     */
    private Component getCompactTeamViewText(TaskViewOption option) {
        if (option == TaskViewOption.UNASSIGNED) {
            int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
            return Component.literal(sidebarWidth <= 128 ? "\u5f85\u9886" : "\u5f85\u9886\u53d6");
        }
        if (option == TaskViewOption.ALL) {
            return Component.translatable("gui.todolist.all");
        }
        return Component.literal("\u6211\u7684");
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
        layoutSidebarViewButtons();
        if (personalSpaceButton != null) {
            personalSpaceButton.active = currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.active = teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (myViewButton != null) {
            myViewButton.setMessage(Component.literal("\u6211\u7684"));
            myViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.MY;
        }
        if (unassignedViewButton != null) {
            unassignedViewButton.setMessage(getCompactTeamViewText(TaskViewOption.UNASSIGNED));
            unassignedViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.setMessage(getCompactTeamViewText(TaskViewOption.ALL));
            allViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.ALL;
        }
    }
    
    private void switchProject(Project project) {
        if (projectListWidget != null) {
            savedProjectListScrollOffset = projectListWidget.getScrollOffset();
        }
        this.selectedTask = null;
        this.detailOverlayVisible = false;
        this.sidebarOverlayVisible = false;
        closeProjectSearchPrefixDropdown();
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
     * 在项目被彻底删除后，清理与该项目关联的任务数据。
     *
     * @param deletedProject 已删除的项目
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
     * 在指定任务管理器中删除属于某个项目的全部任务。
     *
     * @param manager 目标任务管理器
     * @param deletedProjectId 已删除项目的 ID
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
        private Button cancelButton;
        private MemberSelectionDialogLayout dialogLayout;
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
            dialogLayout = buildDialogLayout();
            applyDialogLayout(dialogLayout);

            searchField = new EditBox(this.font, dialogLayout.dialogX(), dialogLayout.searchY(),
                    dialogLayout.dialogWidth(), dialogLayout.searchHeight(), Component.empty());
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
                }).bounds(dialogLayout.dialogX(), btnY, dialogLayout.dialogWidth(), 20).build();
                btn.active = false;
                btn.visible = false;
                this.addRenderableWidget(btn);
                playerButtons[i] = btn;
            }

            cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), b -> {
                minecraft.setScreen(parentScreen);
            }).bounds(dialogLayout.cancelX(), dialogLayout.cancelY(),
                    dialogLayout.cancelWidth(), dialogLayout.cancelHeight()).build();
            this.addRenderableWidget(cancelButton);

            searchField.setResponder(text -> {
                updateFilteredPlayers();
            });
            updateFilteredPlayers();
            this.setFocused(searchField);
        }

        /**
         * 构建成员选择弹窗的布局参数。
         *
         * @return 成员选择弹窗布局
         */
        private MemberSelectionDialogLayout buildDialogLayout() {
            return MemberSelectionDialogLayout.create(this.width, this.height);
        }

        /**
         * 将成员选择弹窗布局应用到输入框、按钮和列表区域。
         *
         * @param layout 计算后的弹窗布局
         */
        private void applyDialogLayout(MemberSelectionDialogLayout layout) {
            if (layout == null) {
                return;
            }
            rowHeight = layout.rowHeight();
            visibleRows = layout.visibleRows();
            listWidth = layout.listWidth();
            listX = layout.listX();
            listY = layout.listY();
            listHeight = layout.listHeight();
        }

        /**
         * 返回成员列表当前滚动偏移，供测试断言使用。
         *
         * @return 当前滚动偏移量
         */
        private int getScrollOffsetForTest() {
            return scrollOffset;
        }

        /**
         * 返回成员列表当前可见行数，供测试断言使用。
         *
         * @return 当前可见行数
         */
        private int getVisibleRowsForTest() {
            return visibleRows;
        }

        /**
         * 返回成员列表可点击区域中心点的横坐标。
         *
         * @return 列表中心横坐标
         */
        private double getListCenterXForTest() {
            return dialogLayout == null ? listX + (listWidth / 2.0D) : dialogLayout.getListCenterX();
        }

        /**
         * 返回成员列表可点击区域中心点的纵坐标。
         *
         * @return 列表中心纵坐标
         */
        private double getListCenterYForTest() {
            return dialogLayout == null ? listY + (listHeight / 2.0D) : dialogLayout.getListCenterY();
        }

        /**
         * 收集当前任务可供指派的成员列表。
         *
         * @return 可指派成员列表
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
         * 根据当前滚动状态获取指定行对应的成员。
         *
         * @param rowIndex 列表行索引
         * @return 对应的成员；不存在时返回 {@code null}
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
         * 根据搜索词刷新弹窗中的成员过滤结果。
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
         * 根据当前过滤结果刷新弹窗中的成员按钮内容。
         */
        private void updatePlayerButtons() {
            if (playerButtons == null) {
                return;
            }
            scrollOffset = clampMemberScrollOffset();
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
         * 约束成员列表滚动偏移，避免滚出可见范围。
         *
         * @return 修正后的滚动偏移量
         */
        private int clampMemberScrollOffset() {
            int totalItems = filteredMembers == null ? 0 : filteredMembers.size();
            return MemberSelectionDialogLayout.clampScrollOffset(scrollOffset, totalItems, visibleRows);
        }

        /**
         * 计算成员列表允许的最大滚动偏移量。
         *
         * @return 最大滚动偏移量
         */
        private int getMaxMemberScrollOffset() {
            int totalItems = filteredMembers == null ? 0 : filteredMembers.size();
            return MemberSelectionDialogLayout.getMaxScrollOffset(totalItems, visibleRows);
        }

        /**
         * 将当前任务指派给指定成员并返回父界面。
         *
         * @param uuid 成员 UUID
         * @param name 成员显示名称
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
            if (dialogLayout != null && dialogLayout.isInsideList(mouseX, mouseY)) {
                if (filteredMembers != null && !filteredMembers.isEmpty()) {
                    int maxOffset = getMaxMemberScrollOffset();
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


