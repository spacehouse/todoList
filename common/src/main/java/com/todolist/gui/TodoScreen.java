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

    // Input fields
    private EditBox searchField;
    private EditBox titleField;
    private MultiLineEditBox descField;
    private EditBox tagField;

    // Buttons
    private Button claimButton;
    private Button abandonButton;
    private Button assignOthersButton;

    // Selected priority for new/edited tasks
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // Filter buttons
    private Button filterStatusButton;
    private Button filterPriorityButton; // Unified priority button
    private Button viewToggleButton;
    private Button configButton;
    
    // Project Search & Toggle
    private EditBox projectSearchField;
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

    private static class LastGuiState {
        Project.Scope projectScopeFilter;
        String currentProjectId;
        ViewMode viewMode;
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
        
        // Ensure taskManager matches currentProject
        if (currentProject != null) {
            if (!teamProjectsEnabled || currentProject.getScope() == Project.Scope.PERSONAL) {
                taskManager = personalTaskManager;
                viewMode = ViewMode.PERSONAL;
            } else {
                taskManager = teamTaskManager;
                if (viewMode == ViewMode.PERSONAL) {
                    viewMode = ViewMode.TEAM_UNASSIGNED;
                }
            }
        } else {
            taskManager = personalTaskManager;
            viewMode = ViewMode.PERSONAL;
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

        if (lastGuiState.viewMode != null) {
            viewMode = lastGuiState.viewMode;
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
        if (!teamProjectsEnabled && viewMode != ViewMode.PERSONAL) {
            viewMode = ViewMode.PERSONAL;
        }
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

        int viewportMargin = 4;
        int maxGuiWidth = Math.max(300, this.width - viewportMargin * 2);
        int maxGuiHeight = Math.max(200, this.height - viewportMargin * 2);
        int guiWidth = clampInt(config.getGuiWidth(), 300, maxGuiWidth);
        int guiHeight = clampInt(config.getGuiHeight(), 200, maxGuiHeight);
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        int padding = clampInt(config.getPadding(), 6, 20);
        int e = clampInt(config.getElementSpacing(), 4, 12);
        int sidebarGap = 8;
        int minSidebarWidth = 90;
        int maxSidebarWidth = Math.max(minSidebarWidth, Math.min(220, guiWidth / 3));
        int sidebarWidth = clampInt(config.getProjectSidebarWidth(), minSidebarWidth, maxSidebarWidth);

        int minContentWidth = 160;
        int minRightPanelWidth = 72;
        int maxRightPanelWidth = 120;
        int rightPanelWidth = clampInt(96, minRightPanelWidth, maxRightPanelWidth);
        int contentWidth = guiWidth - padding * 2 - sidebarWidth - sidebarGap * 2 - rightPanelWidth;
        if (contentWidth < minContentWidth) {
            int need = minContentWidth - contentWidth;
            sidebarWidth = Math.max(minSidebarWidth, sidebarWidth - need);
            contentWidth = guiWidth - padding * 2 - sidebarWidth - sidebarGap * 2 - rightPanelWidth;
        }
        if (contentWidth < minContentWidth) {
            int need = minContentWidth - contentWidth;
            rightPanelWidth = Math.max(minRightPanelWidth, rightPanelWidth - need);
            contentWidth = guiWidth - padding * 2 - sidebarWidth - sidebarGap * 2 - rightPanelWidth;
        }
        contentWidth = Math.max(minContentWidth, contentWidth);

        int topBarY = y + padding + 10; // Extra 10px margin for Title
        int topBarHeight = 20;
        int topBarGap = e;
        int secondRowY = topBarY + topBarHeight + topBarGap;
        int secondRowHeight = 20;
        int listTop = secondRowY + secondRowHeight + topBarGap;
        int bottomBarHeight = 20;
        int bottomBarY = y + guiHeight - padding - bottomBarHeight;
        int inputRowHeight = 20;
        int inputRowY = bottomBarY - topBarGap - inputRowHeight;
        int sidebarTopY = topBarY;

        int rowHeight = 20;
        // int minTaskListHeight = Math.max(rowHeight * 2, Math.max(40, config.getTaskItemHeight() * 2));
        int listInputGap = Math.max(8, topBarGap + 2);
        int listBottom = inputRowY - listInputGap;
        int availableListHeight = Math.max(0, listBottom - listTop);
        int listHeight = availableListHeight; // Strictly use available height to avoid overlap

        int sidebarScopeBtnHeight = 20;
        int sidebarSearchHeight = 16;
        int gap10 = 8;
        int projListY = sidebarTopY + sidebarScopeBtnHeight + gap10 + sidebarSearchHeight + gap10;
        int projBtnGap = 5;
        int projectButtonsHeight = 20 * 3 + projBtnGap * 2;
        int sidebarBottomButtonsY = y + guiHeight - padding - projectButtonsHeight;
        int sidebarListBottom = sidebarBottomButtonsY - gap10;
        // int minSidebarListHeight = rowHeight * 2;
        int sidebarListHeight = Math.max(0, sidebarListBottom - projListY);

        int contentX = x + padding + sidebarWidth + sidebarGap;
        int rightPanelX = contentX + contentWidth + sidebarGap;

        // Scope Toggle
        projectScopeButton = Button.builder(getProjectScopeText(), b -> {
            if (!teamProjectsEnabled) return;
            projectScopeFilter = (projectScopeFilter == Project.Scope.PERSONAL) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            Project targetProject = getPreferredProjectForScope(projectScopeFilter);
            switchProject(targetProject);
        }).bounds(x + padding, sidebarTopY, sidebarWidth, sidebarScopeBtnHeight).build();
        projectScopeButton.active = teamProjectsEnabled;
        this.addRenderableWidget(projectScopeButton);
        
        projectSearchField = new EditBox(this.font, x + padding, sidebarTopY + sidebarScopeBtnHeight + gap10, sidebarWidth, sidebarSearchHeight, Component.translatable("gui.todolist.project.search"));
        projectSearchField.setHint(Component.translatable("gui.todolist.project.search"));
        projectSearchField.setValue(projectSearchQuery);
        projectSearchField.setResponder(text -> {
            projectSearchQuery = text;
            updateProjectList();
        });
        this.addRenderableWidget(projectSearchField);

        projectListWidget = new ProjectListWidget(this.minecraft, x + padding, projListY, sidebarWidth, sidebarListHeight);
        updateProjectList(); 
        projectListWidget.setScrollOffset(savedProjectListScrollOffset);
        projectListWidget.setSelectedProject(currentProject);
        projectListWidget.setOnProjectSelected(this::switchProject);
        this.addRenderableWidget(projectListWidget);
        
        int projBtnY = sidebarBottomButtonsY;

        Button addProjectBtn = Button.builder(Component.translatable("gui.todolist.add"), b -> onAddProject())
                .bounds(x + padding, projBtnY, sidebarWidth, 20).build();
        this.addRenderableWidget(addProjectBtn);

        editProjectBtn = Button.builder(Component.translatable("gui.todolist.edit"), b -> onProjectSettings())
                .bounds(x + padding, projBtnY + 20 + projBtnGap, sidebarWidth, 20).build();
        editProjectBtn.active = currentProject != null;
        this.addRenderableWidget(editProjectBtn);

        deleteProjectBtn = Button.builder(Component.translatable("gui.todolist.delete"), b -> onProjectDelete())
                .bounds(x + padding, projBtnY + (20 + projBtnGap) * 2, sidebarWidth, 20).build();
        this.addRenderableWidget(deleteProjectBtn);

        applyJoinProjectBtn = Button.builder(Component.translatable("gui.todolist.project.join.apply"), b -> onApplyJoinProject())
                .bounds(x + padding, projBtnY + (20 + projBtnGap) * 2, sidebarWidth, 20).build();
        this.addRenderableWidget(applyJoinProjectBtn);

        updateProjectActionButtons();

        // 2. Task List
        taskListWidget = new TaskListWidget(this.minecraft, contentX, listTop, contentWidth, listHeight);
        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        taskListWidget.setTeamAllViewForNonOp(getCurrentRole() == Role.MEMBER && teamAllView);
        taskListWidget.setTasks(filteredTasks);
        taskListWidget.setOnTaskToggleCompletion(task -> {
            if (task.isCompleted()) return;
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

        // 3. Input Fields
        int labelWidth = 0; // Remove "Title:" label space
        int fieldX = contentX + labelWidth;
        int fieldWidth = Math.max(120, contentWidth - labelWidth);

        titleField = new EditBox(this.font, fieldX, inputRowY, fieldWidth, 20, Component.empty());
        titleField.setHint(Component.translatable("gui.todolist.input.title.placeholder"));
        titleField.setValue("");
        titleField.setMaxLength(100);
        this.addRenderableWidget(titleField);

        int rightInnerPadding = 4;
        int rightFieldWidth = Math.max(60, rightPanelWidth - rightInnerPadding * 2);
        int rightPanelTop = topBarY; // Align with top bar
        int rightPanelBottom = inputRowY + inputRowHeight;
        int rightCurrentY = rightPanelTop;
        int rightSectionGap = 6;
        int textH = this.font.lineHeight;
        int assignButtonWidth = rightFieldWidth;
        int assignButtonHeight = 20;
        int assignButtonGap = 4;
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        int assignsX = rightPanelX + rightInnerPadding;

        configButton = Button.builder(Component.translatable("gui.todolist.config.title"), b -> this.minecraft.setScreen(new ConfigScreen(this)))
                .bounds(assignsX, rightCurrentY, rightFieldWidth, 20).build();
        this.addRenderableWidget(configButton);
        rightCurrentY += 20 + rightSectionGap;

        int teamButtonsTop = rightPanelBottom;
        if (showAssignButtons) {
            int teamButtonsTotalHeight = assignButtonHeight * 3 + assignButtonGap * 2;
            teamButtonsTop = rightPanelBottom - teamButtonsTotalHeight;
        }

        int tagFieldY = teamButtonsTop - rightSectionGap - 20;
        int minTagFieldY = rightCurrentY + textH + 2 + 30;
        if (tagFieldY < minTagFieldY) {
            tagFieldY = minTagFieldY;
        }
        int descFieldY = rightCurrentY + textH + 2;
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

        // 6. Save/Cancel
        int saveCancelWidth = 90;
        int saveCancelGap = 5;
        int totalSaveCancelWidth = saveCancelWidth * 2 + saveCancelGap;
        int saveCancelX = x + (guiWidth - totalSaveCancelWidth) / 2;

        Button saveButton = Button.builder(Component.translatable("gui.todolist.save"), button -> onSaveTasks())
                .bounds(saveCancelX, bottomBarY, saveCancelWidth, 20).build();
        this.addRenderableWidget(saveButton);

        Button cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), button -> onCancel())
                .bounds(saveCancelX + saveCancelWidth + saveCancelGap, bottomBarY, saveCancelWidth, 20).build();
        this.addRenderableWidget(cancelButton);

        // 7. Filter Row (View/Priority/Status/Config)
        int filterGap = 4;
        int filterLabelW = 0; // Remove "Filter:" label
        int filtersX = contentX + filterLabelW;
        int filtersY = topBarY;
        int btnH = 20;

        // this.addRenderableWidget(new TextLabelWidget(contentX, filtersY + (btnH - 8) / 2, Component.translatable("gui.todolist.label.filter"), 0xFFFFFF));

        int availableBeforeConfig = contentWidth - filterLabelW;
        int minBtnW = 70;
        int maxBtnW = 140;
        int viewBtnWidth = Math.min(maxBtnW, Math.max(minBtnW, this.font.width(getViewToggleText()) + 16));
        int priorityBtnWidth = Math.min(120, Math.max(minBtnW, this.font.width(getPriorityFilterText()) + 16));
        int statusBtnWidth = Math.min(120, Math.max(minBtnW, this.font.width(getStatusFilterText()) + 16));
        int totalW = viewBtnWidth + priorityBtnWidth + statusBtnWidth + filterGap * 2;
        int maxW = Math.max(0, availableBeforeConfig - filterGap);
        int guard = 0;
        while (totalW > maxW && guard++ < 200) {
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
        
        // 9. Search Field
        int searchY = secondRowY;
        
        // Search Label & Field
        int searchLabelWidth = 0; // Remove "Search:" label
        int searchFieldX = contentX + searchLabelWidth;
        int searchFieldWidth = Math.max(120, contentWidth - searchLabelWidth);
        
        searchField = new EditBox(this.font, searchFieldX, searchY, searchFieldWidth, 20, Component.empty());
        searchField.setHint(Component.translatable("gui.todolist.input.search.placeholder"));
        searchField.setValue(searchQuery);
        this.addRenderableWidget(searchField);

        // Listeners
        titleField.setResponder(text -> {
            if (selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted()) {
                selectedTask.setTitle(text);
                markUnsaved();
            }
        });
        descField.setValueListener(text -> {
            if (selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted()) {
                selectedTask.setDescription(text);
                markUnsaved();
            }
        });
        tagField.setResponder(text -> {
            if (selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted()) {
                String value = getFieldValue(tagField, "");
                if (value.isEmpty()) selectedTask.clearTags();
                else {
                    List<String> tags = new ArrayList<>();
                    for (String part : value.split(",")) {
                        String t = part.trim();
                        if (!t.isEmpty()) tags.add(t);
                    }
                    selectedTask.setTags(tags);
                }
                markUnsaved();
            }
        });
        searchField.setResponder(text -> {
            searchQuery = text == null ? "" : text.trim().toLowerCase();
            applySearchFilter();
        });

        filterTasks(currentFilter);
        this.setFocused(titleField);
        updateButtonStates();
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

        if (taskListWidget != null) taskListWidget.render(context, mouseX, mouseY, delta);
        if (projectListWidget != null) projectListWidget.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        int labelX = titleField != null ? titleField.getX() - 40 : 0;
        int color = 0xFFFFFFFF;
        int textH = this.font.lineHeight;

        /*
        if (titleField != null) {
            int ty = titleField.getY() + (titleField.getHeight() - textH) / 2;
            context.drawString(this.font, Component.translatable("gui.todolist.label.title"), labelX, ty, color, false);
        }
        */
        if (descField != null) {
            int dy = descField.getY() - textH - 2;
            context.drawString(this.font, Component.translatable("gui.todolist.label.description"), descField.getX(), dy, color, false);
        }
        if (tagField != null) {
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
            if (titleField != null && titleField.isFocused()) {
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
        if (titleField != null && titleField.isMouseOver(mouseX, mouseY)) return true;
        if (descField != null && descField.isMouseOver(mouseX, mouseY)) return true;
        if (tagField != null && tagField.isMouseOver(mouseX, mouseY)) return true;
        if (claimButton != null && claimButton.isMouseOver(mouseX, mouseY)) return true;
        if (abandonButton != null && abandonButton.isMouseOver(mouseX, mouseY)) return true;
        if (assignOthersButton != null && assignOthersButton.isMouseOver(mouseX, mouseY)) return true;
        if (isInsideContextMenu(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (taskListWidget != null && taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            closeTaskContextMenu();
            return true;
        }
        if (projectListWidget != null && projectListWidget.mouseClicked(mouseX, mouseY, button)) {
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
        if (!handled && projectListWidget != null) {
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
        String title = getFieldValue(titleField, "");
        String desc = getFieldValue(descField, "");
        String tagsStr = getFieldValue(tagField, "");

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

            // Parse and add tags (comma-separated)
            if (!tagsStr.isEmpty()) {
                String[] tags = tagsStr.split(",");
                for (String tag : tags) {
                    String trimmedTag = tag.trim();
                    if (!trimmedTag.isEmpty()) {
                        task.addTag(trimmedTag);
                    }
                }
            }

            clearSelectedTask();
            selectedPriority = Task.Priority.MEDIUM;

            markUnsaved();
            refreshTaskList();
        }
    }

    private void onDeleteTask() {
        if (selectedTask != null) {
            String id = selectedTask.getId();
            taskManager.deleteTask(id);
            selectedTask = null;
            closeTaskContextMenu();
            updateButtonStates();
            markUnsaved();
            refreshTaskList();
        }
    }

    private void selectTask(Task task) {
        selectedTask = task;
        selectedPriority = task.getPriority();
        titleField.setValue(task.getTitle());
        descField.setValue(task.getDescription());

        // Display tags as comma-separated string
        if (task.getTags() != null && !task.getTags().isEmpty()) {
            String tagsStr = String.join(",", task.getTags());
            tagField.setValue(tagsStr);
        } else {
            tagField.setValue("");
        }

        taskListWidget.setSelectedTask(task);
        updateButtonStates();
        boolean editable = canEditTask(task);
        titleField.setEditable(editable);
        descField.active = editable;
        tagField.setEditable(editable);
    }

    private void clearSelectedTask() {
        selectedTask = null;
        if (taskListWidget != null) {
            taskListWidget.clearSelection();
        }
        if (titleField != null) {
            titleField.setValue("");
            titleField.setEditable(true);
        }
        if (descField != null) {
            descField.setValue("");
            descField.active = true;
        }
        if (tagField != null) {
            tagField.setValue("");
            tagField.setEditable(true);
        }
        updateButtonStates();
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
        if (filteredTasks == null) {
            return true;
        }
        for (Task t : filteredTasks) {
            if (t != null && selectedId.equals(t.getId())) {
                return true;
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
        if (claimButton != null) {
            claimButton.visible = showAssignButtons;
            boolean canClaim = hasSelection
                    && PermissionCenter.canPerform(Operation.CLAIM_TASK, role, context);
            claimButton.active = showAssignButtons && canClaim;
        }
        if (abandonButton != null) {
            abandonButton.visible = showAssignButtons;
            boolean canAbandon = hasSelection
                    && PermissionCenter.canPerform(Operation.ABANDON_TASK, role, context);
            abandonButton.active = showAssignButtons && canAbandon;
        }
        if (assignOthersButton != null) {
            boolean showAssignOthers = showAssignButtons
                    && PermissionCenter.canPerform(Operation.ASSIGN_OTHERS, role, context);
            assignOthersButton.visible = showAssignOthers;
            boolean canAssignOthers = showAssignOthers && hasSelection;
            assignOthersButton.active = canAssignOthers;
        }
        rebuildContextMenuIfNeeded();
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
            selectedTask = null;
        }
        markUnsaved();
        refreshTaskList();
        updateButtonStates();
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
        if (taskListWidget != null) taskListWidget.setTasks(filteredTasks);
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
        this.selectedTask = null;
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
        if (project == null || project.getScope() == Project.Scope.PERSONAL) {
            config.setHudDefaultView("PERSONAL");
            return;
        }
        ViewMode configView = parseHudViewMode(config.getHudDefaultView());
        if (this.viewMode == ViewMode.PERSONAL) {
            this.viewMode = configView;
        }
        if (this.viewMode == ViewMode.PERSONAL) {
            this.viewMode = ViewMode.TEAM_UNASSIGNED;
        }
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
        this.currentProject = project;
        rememberSelectedProject(project);

        if (project == null) {
            this.taskManager = this.personalTaskManager;
            this.viewMode = ViewMode.PERSONAL;
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
            this.viewMode = ViewMode.PERSONAL;
        } else {
            this.taskManager = this.teamTaskManager;
            if (this.viewMode == ViewMode.PERSONAL) {
                this.viewMode = ViewMode.TEAM_UNASSIGNED;
            }
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


