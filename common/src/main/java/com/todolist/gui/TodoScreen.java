package com.todolist.gui;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.client.ClientTaskStorageHelper;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.client.TodoHudRenderer;
import com.todolist.config.ModConfig;
import com.todolist.gui.TodoScreenLayoutSupport.LayoutRect;
import com.todolist.gui.TodoScreenLayoutSupport.MainLayoutMetrics;
import com.todolist.gui.TodoScreenLayoutSupport.ResponsiveTier;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * 待办主界面，负责项目侧栏、任务列表、详情面板以及相关弹窗的交互。
 */
public class TodoScreen extends Screen implements ProjectManager.ProjectChangeListener {
    private static final Component TITLE = Component.translatable("gui.todolist.title");

    private final Screen parent;
    private ProjectManager projectManager;
    private ProjectListWidget projectListWidget;
    private Project currentProject;
    private TaskManager taskManager;
    private TaskManager personalTaskManager;
    private TaskManager teamTaskManager;
    private TaskListWidget taskListWidget;
    private final List<TodoScreenNotificationSupport.NotificationEntry> notifications = new ArrayList<>();

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
     * 创建统一使用顶层实现的任务指派弹窗。
     *
     * @param task 目标任务
     * @return 指派成员弹窗
     */
    private Screen createAssignPlayerScreen(Task task) {
        return new AssignPlayerScreen(
                this,
                task,
                () -> currentProject,
                (project, memberUuid) -> TodoScreenMemberSupport.resolveProjectMemberDisplayName(this.minecraft, project, memberUuid),
                (memberUuid, memberName) -> applyAssignResult(task, memberUuid, memberName)
        );
    }

    /**
     * 应用任务指派结果，并刷新当前搜索后的任务显示。
     *
     * @param task 目标任务
     * @param memberUuid 被指派成员 UUID
     * @param memberName 被指派成员显示名称
     */
    private void applyAssignResult(Task task, String memberUuid, String memberName) {
        if (task == null) {
            return;
        }
        task.setAssigneeUuid(memberUuid);
        task.setAssigneeName(memberName);
        addNotification(Component.translatable("message.todolist.assigned_to_player", memberName).getString());
        markUnsaved();
        applySearchFilter();
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

        // 恢复当前选中项目，并在项目已不存在时按空间偏好回退。
        if (currentProject != null) {
            Project project = projectManager.getProject(currentProject.getId());
            if (project == null) {
                currentProject = null;
            } else {
                currentProject = project;
            }
        }
        if (currentProject == null) {
            currentProject = TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                    projectManager,
                    projectScopeFilter,
                    teamProjectsEnabled,
                    preferredTeamProjectId,
                    preferredPersonalProjectId);
        }
        if (currentProject != null) {
            String[] preferredProjectIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                    currentProject,
                    preferredPersonalProjectId,
                    preferredTeamProjectId);
            preferredPersonalProjectId = preferredProjectIds[0];
            preferredTeamProjectId = preferredProjectIds[1];
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
        currentProject = TodoScreenActiveProjectSupport.syncActiveProjectIdWithCurrentProject(projectManager, currentProject);
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
        currentSpaceMode = SpaceMode.valueOf(TodoScreenViewModeSupport.resolveSpaceModeName(currentProject, teamProjectsEnabled));
        List<String> visibleOptions = TodoScreenViewModeSupport.getVisibleTaskViewOptionNames(currentSpaceMode.name());
        TaskViewOption resolvedOption = currentSpaceMode == SpaceMode.TEAM && viewMode == ViewMode.PERSONAL
                ? TaskViewOption.valueOf(TodoScreenViewModeSupport.resolveDefaultTaskViewOptionName(currentSpaceMode.name()))
                : TaskViewOption.valueOf(TodoScreenViewModeSupport.resolveTaskViewOptionNameFromLegacyViewModeName(viewMode.name()));
        if (!visibleOptions.contains(resolvedOption.name())) {
            resolvedOption = TaskViewOption.valueOf(TodoScreenViewModeSupport.resolveDefaultTaskViewOptionName(currentSpaceMode.name()));
        }
        currentTaskViewOption = resolvedOption;
        viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                currentSpaceMode.name(),
                currentTaskViewOption.name()
        ));
    }

    /**
     * 根据分组 ID 切换对应分组的展开状态。
     *
     * @param sectionId 分组 ID
     * @return 若成功切换则返回 {@code true}
     */
    private boolean toggleTaskSection(String sectionId) {
        if ("active".equals(sectionId)) {
            activeExpanded = !activeExpanded;
            return true;
        }
        if ("completed".equals(sectionId)) {
            completedExpanded = !completedExpanded;
            return true;
        }
        return false;
    }

    /**
     * 应用上一次关闭界面时保存的视图状态。
     */
    private void applyLastGuiState() {
        if (lastGuiState == null || projectManager == null) {
            return;
        }
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
        currentFilter = lastGuiState.currentFilter == null ? currentFilter : lastGuiState.currentFilter;
        if (lastGuiState.searchQuery != null) {
            searchQuery = lastGuiState.searchQuery;
        }
        if (lastGuiState.currentProjectId == null || lastGuiState.currentProjectId.isEmpty()) {
            return;
        }
        Project project = projectManager.getProject(lastGuiState.currentProjectId);
        if (project == null || (!teamProjectsEnabled && project.getScope() == Project.Scope.TEAM)) {
            return;
        }
        currentProject = project;
        projectScopeFilter = project.getScope();
        String[] preferredProjectIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                project,
                preferredPersonalProjectId,
                preferredTeamProjectId);
        preferredPersonalProjectId = preferredProjectIds[0];
        preferredTeamProjectId = preferredProjectIds[1];
    }

    /**
     * 保存当前界面状态，供下次打开时恢复。
     */
    private void saveLastGuiState() {
        String[] preferredProjectIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                currentProject,
                preferredPersonalProjectId,
                preferredTeamProjectId);
        preferredPersonalProjectId = preferredProjectIds[0];
        preferredTeamProjectId = preferredProjectIds[1];
        LastGuiState state = new LastGuiState();
        state.projectScopeFilter = projectScopeFilter;
        state.currentProjectId = currentProject == null ? null : currentProject.getId();
        state.viewMode = viewMode;
        state.spaceMode = currentSpaceMode;
        state.taskViewOption = currentTaskViewOption;
        state.activeExpanded = activeExpanded;
        state.completedExpanded = completedExpanded;
        state.currentPriorityFilter = currentPriorityFilter;
        state.currentFilter = currentFilter;
        state.searchQuery = searchQuery;
        state.projectSearchQuery = projectSearchQuery;
        state.lastPersonalProjectId = preferredPersonalProjectId;
        state.lastTeamProjectId = preferredTeamProjectId;
        lastGuiState = state;
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.execute(() -> {
            if (type == ProjectManager.ProjectChangeType.CLEARED) {
                switchProject(null);
                return;
            }

            if (project == null) {
                return;
            }

            if (type == ProjectManager.ProjectChangeType.REMOVED) {
                if (!TodoListCommon.isProjectSyncInProgress()) {
                    TodoScreenProjectCleanupSupport.hardDeleteTasksForDeletedProject(
                            personalTaskManager,
                            teamTaskManager,
                            project);
                }
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    switchProject(TodoScreenProjectPreferenceSupport.resolveFallbackProjectAfterRemoval(
                            projectManager,
                            project,
                            projectScopeFilter,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId));
                } else {
                    refreshAfterProjectMutation();
                }
            } else if (type == ProjectManager.ProjectChangeType.ADDED) {
                if (currentProject == null) {
                    switchProject(TodoScreenProjectPreferenceSupport.resolvePreferredProjectForCurrentScope(
                            projectManager,
                            projectScopeFilter,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId));
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
        currentProject = TodoScreenActiveProjectSupport.syncActiveProjectIdWithCurrentProject(projectManager, currentProject);
        updateProjectList();
        filterTasks();
        updateButtonStates();
        updateProjectActionButtons();
    }

    /**
     * 根据当前项目同步 HUD 默认视图以及界面空间/视图状态。
     *
     * @param project 当前项目
     */
    private void syncHudViewForProject(Project project) {
        ModConfig config = ModConfig.getInstance();
        TodoScreenViewModeSupport.HudViewState hudViewState = TodoScreenViewModeSupport.resolveHudViewState(
                project,
                teamProjectsEnabled,
                config.getHudDefaultView());
        currentSpaceMode = SpaceMode.valueOf(hudViewState.spaceModeName);
        currentTaskViewOption = TaskViewOption.valueOf(hudViewState.taskViewOptionName);
        viewMode = ViewMode.valueOf(hudViewState.legacyViewModeName);
        config.setHudDefaultView(hudViewState.hudDefaultViewToSave);
    }

    /**
     * 根据当前空间与任务视图状态刷新左侧按钮文案和激活状态。
     */
    private void updateViewButtonsState() {
        TodoScreenSidebarViewButtonLayoutSupport.apply(
                myViewButton,
                unassignedViewButton,
                allViewButton,
                layoutMetrics == null ? null : layoutMetrics.sidebarBounds,
                responsiveTier,
                currentSpaceMode.name()
        );
        if (personalSpaceButton != null) {
            personalSpaceButton.active = currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.active = teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (myViewButton != null) {
            myViewButton.setMessage(Component.literal("我的"));
            myViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.MY;
        }
        if (unassignedViewButton != null) {
            int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
            unassignedViewButton.setMessage(TodoScreenUiMetricsSupport.getUnassignedTeamViewText(sidebarWidth));
            unassignedViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.setMessage(TodoScreenUiMetricsSupport.getAllTeamViewText());
            allViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.ALL;
        }
    }

    /**
     * 切换当前项目，并同步任务管理器、视图状态和列表滚动位置。
     *
     * @param project 目标项目
     */
    private void switchProject(Project project) {
        if (projectListWidget != null) {
            savedProjectListScrollOffset = projectListWidget.getScrollOffset();
        }
        this.selectedTask = null;
        this.detailOverlayVisible = false;
        this.sidebarOverlayVisible = false;
        projectSearchPrefixDropdownOpen = false;
        this.currentProject = project;
        String[] preferredProjectIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                project,
                preferredPersonalProjectId,
                preferredTeamProjectId);
        preferredPersonalProjectId = preferredProjectIds[0];
        preferredTeamProjectId = preferredProjectIds[1];

        if (project == null) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                    currentSpaceMode.name(),
                    currentTaskViewOption.name()
            ));
            currentProject = TodoScreenActiveProjectSupport.syncActiveProjectIdWithCurrentProject(projectManager, currentProject);
            syncHudViewForProject(null);
            rebuildUI();
            if (projectListWidget != null) {
                projectListWidget.setScrollOffset(savedProjectListScrollOffset);
            }
            return;
        }

        if (!teamProjectsEnabled && project.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        projectScopeFilter = project.getScope();
        currentProject = TodoScreenActiveProjectSupport.syncActiveProjectIdWithCurrentProject(projectManager, currentProject);

        if (project.getScope() == Project.Scope.PERSONAL) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                    currentSpaceMode.name(),
                    currentTaskViewOption.name()
            ));
        } else {
            this.taskManager = this.teamTaskManager;
            currentSpaceMode = SpaceMode.TEAM;
            if (currentTaskViewOption == TaskViewOption.MY && viewMode == ViewMode.PERSONAL) {
                currentTaskViewOption = TaskViewOption.UNASSIGNED;
            }
            viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                    currentSpaceMode.name(),
                    currentTaskViewOption.name()
            ));
        }
        syncHudViewForProject(project);
        rebuildUI();
        if (projectListWidget != null) {
            projectListWidget.setScrollOffset(savedProjectListScrollOffset);
        }
    }

    private void rebuildUI() {
        syncViewStateForCurrentProject();
        currentFilter = "active";
        searchQuery = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        projectSearchQuery = projectSearchQuery == null ? "" : projectSearchQuery.trim();
        savedTaskListScrollOffset = taskListWidget == null ? savedTaskListScrollOffset : taskListWidget.getScrollOffset();
        baseFilteredTasks = new ArrayList<>();
        filteredTasks = new ArrayList<>();
        this.clearWidgets();

        ModConfig config = ModConfig.getInstance();
        responsiveTier = TodoScreenLayoutSupport.resolveResponsiveTier(this.width, this.height);
        syncOverlayStateForResponsiveTier();
        layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                config,
                responsiveTier,
                this.width,
                this.height,
                sidebarOverlayVisible,
                detailOverlayVisible,
                selectedTask != null);
        LayoutRect sidebarBounds = layoutMetrics.sidebarBounds;
        LayoutRect contentBounds = layoutMetrics.contentBounds;
        LayoutRect detailBounds = layoutMetrics.detailBounds;
        int contentControlInset = TodoScreenUiMetricsSupport.getContentControlInset(responsiveTier);
        int contentControlX = contentBounds.x + contentControlInset;
        int contentControlWidth = Math.max(80, contentBounds.width - contentControlInset * 2);
        int contentControlRight = contentControlX + contentControlWidth;
        int topBarGap = Math.max(3, Math.min(8, config.getElementSpacing()));
        int topBarHeight = TodoScreenUiMetricsSupport.getContentTopBarHeight(responsiveTier);
        int topBarY = contentBounds.y + TodoScreenUiMetricsSupport.getContentTopPadding(responsiveTier);
        int secondRowHeight = TodoScreenUiMetricsSupport.getContentSearchFieldHeight(responsiveTier);
        int secondRowY = topBarY + topBarHeight + TodoScreenUiMetricsSupport.getContentHeaderGap(responsiveTier);
        int inputRowHeight = TodoScreenUiMetricsSupport.getContentQuickAddFieldHeight(responsiveTier);
        int inputRowY = contentBounds.y + contentBounds.height - inputRowHeight - TodoScreenUiMetricsSupport.getContentBottomPadding(responsiveTier);
        int bottomActionRowHeight = TodoScreenUiMetricsSupport.getContentBottomActionRowHeight(responsiveTier);
        int actionRowY = this.height - layoutMetrics.padding - bottomActionRowHeight;
        int listTop = secondRowY + secondRowHeight + topBarGap;
        int listBottom = inputRowY - Math.max(4, topBarGap);
        int listHeight = Math.max(0, listBottom - listTop);
        int sidebarTopY = sidebarBounds.y;
        int sidebarWidth = sidebarBounds.width;
        int contentX = contentBounds.x;
        int contentWidth = contentBounds.width;
        int rightPanelX = detailBounds.x;
        int rightPanelWidth = detailBounds.width;

        int actionGap = TodoScreenUiMetricsSupport.getContentActionGap(responsiveTier);
        int cancelButtonWidth = Math.max(44, Math.min(64, this.font.width(Component.translatable("gui.todolist.cancel")) + 10));
        int saveButtonWidth = Math.max(44, Math.min(64, this.font.width(Component.translatable("gui.todolist.save")) + 10));
        int bottomActionWidth = saveButtonWidth + actionGap + cancelButtonWidth;
        int saveButtonX = Math.max(0, (this.width - bottomActionWidth) / 2);
        int cancelButtonX = saveButtonX + saveButtonWidth + actionGap;

        TodoScreenWidgetBuildSupport.ContentWidgets contentWidgets = TodoScreenWidgetBuildSupport.buildContentWidgets(
                this.font,
                layoutMetrics,
                responsiveTier,
                contentControlX,
                contentControlRight,
                topBarY,
                topBarHeight,
                secondRowY,
                secondRowHeight,
                currentPriorityFilter,
                searchQuery,
                button -> {
                    currentPriorityFilter = (currentPriorityFilter + 1) % 4;
                    button.setMessage(TodoScreenFilterTextSupport.getPriorityFilterText(currentPriorityFilter));
                    filterTasks();
                },
                this::toggleSidebarOverlay,
                () -> this.minecraft.setScreen(new ConfigScreen(this)));
        sidebarToggleButton = contentWidgets.sidebarToggleButton;
        configButton = contentWidgets.configButton;
        filterPriorityButton = contentWidgets.filterPriorityButton;
        searchField = contentWidgets.searchField;
        filterStatusButton = null;
        viewToggleButton = null;
        this.addRenderableWidget(sidebarToggleButton);
        this.addRenderableWidget(configButton);
        this.addRenderableWidget(filterPriorityButton);
        this.addRenderableWidget(searchField);

        TodoScreenWidgetBuildSupport.SidebarWidgets sidebarWidgets = TodoScreenWidgetBuildSupport.buildSidebarWidgets(
                this.minecraft,
                this.font,
                responsiveTier,
                sidebarBounds,
                sidebarTopY,
                sidebarWidth,
                projectManager,
                teamProjectsEnabled,
                preferredTeamProjectId,
                preferredPersonalProjectId,
                () -> switchProject(TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                        projectManager,
                        Project.Scope.PERSONAL,
                        teamProjectsEnabled,
                        preferredTeamProjectId,
                        preferredPersonalProjectId)),
                () -> {
                    if (teamProjectsEnabled) {
                        switchProject(TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                                projectManager,
                                Project.Scope.TEAM,
                                teamProjectsEnabled,
                                preferredTeamProjectId,
                                preferredPersonalProjectId));
                    }
                },
                () -> switchView(currentSpaceMode == SpaceMode.PERSONAL ? ViewMode.PERSONAL : ViewMode.TEAM_ASSIGNED),
                () -> switchView(ViewMode.TEAM_UNASSIGNED),
                () -> switchView(ViewMode.TEAM_ALL),
                projectSearchQuery,
                text -> {
                    projectSearchQuery = text;
                    updateProjectList();
                },
                this::switchProject,
                this::onAddProject,
                this::onProjectSettings,
                this::onProjectDelete,
                this::onApplyJoinProject,
                sidebarWidth,
                currentProject != null);
        personalSpaceButton = sidebarWidgets.personalSpaceButton;
        teamSpaceButton = sidebarWidgets.teamSpaceButton;
        myViewButton = sidebarWidgets.myViewButton;
        unassignedViewButton = sidebarWidgets.unassignedViewButton;
        allViewButton = sidebarWidgets.allViewButton;
        projectSearchField = sidebarWidgets.projectSearchField;
        projectListWidget = sidebarWidgets.projectListWidget;
        addProjectBtn = sidebarWidgets.addProjectBtn;
        editProjectBtn = sidebarWidgets.editProjectBtn;
        deleteProjectBtn = sidebarWidgets.deleteProjectBtn;
        applyJoinProjectBtn = sidebarWidgets.applyJoinProjectBtn;
        this.addRenderableWidget(personalSpaceButton);
        this.addRenderableWidget(teamSpaceButton);
        this.addRenderableWidget(myViewButton);
        this.addRenderableWidget(unassignedViewButton);
        this.addRenderableWidget(allViewButton);
        this.addRenderableWidget(projectSearchField);
        this.addRenderableWidget(addProjectBtn);
        this.addRenderableWidget(editProjectBtn);
        this.addRenderableWidget(deleteProjectBtn);
        this.addRenderableWidget(applyJoinProjectBtn);
        updateProjectList();
        projectListWidget.setScrollOffset(savedProjectListScrollOffset);
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();

        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        TodoScreenWidgetBuildSupport.TaskAreaWidgets taskAreaWidgets = TodoScreenWidgetBuildSupport.buildTaskAndQuickAddWidgets(
                this.minecraft,
                this.font,
                contentX,
                listTop,
                contentWidth,
                listHeight,
                TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject) == Role.MEMBER && teamAllView,
                TodoScreenPermissionSupport.canTaskReorderInView(
                        this.minecraft,
                        currentProject,
                        viewMode.name()),
                buildTaskPaneSections(),
                selectedTask,
                task -> {
                    boolean wasCompleted = task.isCompleted();
                    toggleTaskCompletion(task);
                    if (!wasCompleted && task.isCompleted()) {
                        addNotification(Component.translatable("message.todolist.completed", task.getTitle()).getString());
                        if (config.isEnableSoundEffects() && this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 1.0F);
                        }
                    }
                },
                this::onManualReorderActiveTasks,
                contentControlX,
                inputRowY,
                Math.max(60, contentControlWidth),
                inputRowHeight);
        taskListWidget = taskAreaWidgets.taskListWidget;
        quickAddField = taskAreaWidgets.quickAddField;
        this.addRenderableWidget(quickAddField);

        TodoScreenWidgetBuildSupport.DetailWidgets detailWidgets = TodoScreenWidgetBuildSupport.buildDetailWidgets(
                this.font,
                detailBounds,
                rightPanelX,
                rightPanelWidth,
                viewMode != ViewMode.PERSONAL,
                this::clearSelectedTask,
                this::onClaimTask,
                this::onAbandonTask,
                this::onAssignOthers);
        detailCloseButton = detailWidgets.detailCloseButton;
        titleField = detailWidgets.titleField;
        descField = detailWidgets.descField;
        tagField = detailWidgets.tagField;
        claimButton = detailWidgets.claimButton;
        abandonButton = detailWidgets.abandonButton;
        assignOthersButton = detailWidgets.assignOthersButton;
        this.addRenderableWidget(detailCloseButton);
        this.addRenderableWidget(titleField);
        this.addRenderableWidget(descField);
        this.addRenderableWidget(tagField);
        this.addRenderableWidget(claimButton);
        this.addRenderableWidget(abandonButton);
        this.addRenderableWidget(assignOthersButton);

        TodoScreenWidgetBuildSupport.BottomActionWidgets bottomActionWidgets = TodoScreenWidgetBuildSupport.buildBottomActionWidgets(
                saveButtonX,
                saveButtonWidth,
                cancelButtonX,
                cancelButtonWidth,
                actionRowY,
                bottomActionRowHeight,
                this::onSaveTasks,
                this::onClose);
        saveButton = bottomActionWidgets.saveButton;
        cancelButton = bottomActionWidgets.cancelButton;
        this.addRenderableWidget(saveButton);
        this.addRenderableWidget(cancelButton);

        applyResponsiveWidgetVisibility();
        bindWidgetResponders();
        filterTasks();
        if (taskListWidget != null) {
            taskListWidget.setScrollOffset(savedTaskListScrollOffset);
        }
        syncDetailWidgetsFromState();
        this.setFocused(quickAddField);
        updateButtonStates();
    }

    /**
     * 统一绑定重建后需要恢复的输入监听器。
     */
    private void bindWidgetResponders() {
        titleField.setResponder(this::onDetailTitleChanged);
        descField.setValueListener(this::onDetailDescriptionChanged);
        tagField.setResponder(this::onDetailTagsChanged);
        searchField.setResponder(text -> {
            searchQuery = text == null ? "" : text.trim().toLowerCase();
            applySearchFilter();
        });
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
     * 根据当前响应式布局更新组件显隐状态。
     */
    private void applyResponsiveWidgetVisibility() {
        if (layoutMetrics == null) {
            return;
        }
        TodoScreenSidebarViewButtonLayoutSupport.apply(
                myViewButton,
                unassignedViewButton,
                allViewButton,
                layoutMetrics == null ? null : layoutMetrics.sidebarBounds,
                responsiveTier,
                currentSpaceMode.name());
        boolean sidebarVisible = layoutMetrics.sidebarVisible;
        boolean detailVisible = layoutMetrics.detailVisible;
        if (myViewButton != null) {
            myViewButton.visible = sidebarVisible;
        }
        boolean teamViewButtonsVisible = sidebarVisible && currentSpaceMode == SpaceMode.TEAM;
        if (unassignedViewButton != null) {
            unassignedViewButton.visible = teamViewButtonsVisible;
        }
        if (allViewButton != null) {
            allViewButton.visible = teamViewButtonsVisible;
        }
        if (personalSpaceButton != null) {
            personalSpaceButton.visible = sidebarVisible;
            personalSpaceButton.active = sidebarVisible && currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.visible = sidebarVisible;
            teamSpaceButton.active = sidebarVisible && teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (projectSearchField != null) {
            projectSearchField.visible = sidebarVisible;
            projectSearchField.active = sidebarVisible;
        }
        TodoScreenProjectActionSupport.applySidebarVisibility(
                sidebarVisible,
                addProjectBtn,
                editProjectBtn,
                deleteProjectBtn,
                applyJoinProjectBtn);
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
        projectSearchPrefixDropdownOpen = TodoScreenProjectSearchSupport.syncProjectSearchPrefixDropdownState(
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible(),
                projectSearchField);
    }

    /**
     * 供测试入口复用，判断当前是否允许显示项目搜索前缀下拉面板。
     *
     * @return 允许显示时返回 {@code true}
     */
    private boolean canUseProjectSearchPrefixDropdown() {
        return TodoScreenProjectSearchSupport.canUseProjectSearchPrefixDropdown(
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible()
        );
    }

    /**
     * 供测试入口复用，尝试打开项目搜索前缀下拉面板。
     */
    private void openProjectSearchPrefixDropdown() {
        if (canUseProjectSearchPrefixDropdown()) {
            projectSearchPrefixDropdownOpen = true;
        }
    }

    /**
     * 供测试入口复用，同步项目搜索前缀下拉面板显隐状态。
     */
    private void syncProjectSearchPrefixDropdownState() {
        projectSearchPrefixDropdownOpen = TodoScreenProjectSearchSupport.syncProjectSearchPrefixDropdownState(
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible(),
                projectSearchField
        );
    }

    /**
     * 切换侧栏的覆盖层显示状态。
     */
    private void toggleSidebarOverlay() {
        if (responsiveTier != ResponsiveTier.MINIMAL) {
            return;
        }
        sidebarOverlayVisible = !sidebarOverlayVisible;
        layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                ModConfig.getInstance(),
                responsiveTier,
                this.width,
                this.height,
                sidebarOverlayVisible,
                detailOverlayVisible,
                selectedTask != null);
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

    /**
     * 切换指定任务的完成状态，并在当前视图中立即刷新分组结果。
     *
     * @param task 待切换完成状态的任务
     */
    private void toggleTaskCompletion(Task task) {
        if (!TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.TOGGLE_COMPLETE,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        )) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        taskManager.toggleTaskCompletion(task.getId());
        markUnsaved();
        filterTasks();
    }

    /**
     * 覆盖默认背景渲染，避免父类在 render 阶段重复绘制背景遮罩。
     *
     * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔
     */
    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Background is drawn manually in render to keep cross-loader consistency.
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());

        Component title = hasUnsavedChanges ? Component.translatable("gui.todolist.title.unsaved") : TITLE;
        context.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 10, 0xFFFFFFFF, false);

        TodoScreenRenderSupport.renderLayoutPanels(
                context,
                layoutMetrics,
                isSidebarPanelVisible(),
                isDetailPanelVisible());
        TodoScreenRenderSupport.renderContentHeaderSummary(
                context,
                this.font,
                layoutMetrics,
                responsiveTier,
                currentSpaceMode,
                currentProject,
                configButton,
                sidebarToggleButton);
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
        TodoScreenRenderSupport.renderProjectSearchPrefixDropdown(
                context,
                mouseX,
                mouseY,
                this.font,
                projectSearchField,
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible()
        );
        if (TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            TodoScreenContextMenuSupport.renderMenu(
                    context,
                    this.font,
                    contextMenuItems,
                    contextMenuX,
                    contextMenuY,
                    contextMenuWidth,
                    contextMenuItemHeight,
                    mouseX,
                    mouseY);
        }
        TodoScreenRenderSupport.renderNotifications(context, this.font, this.width, searchField, notifications);
    }

    private void onSaveTasks() {
        TodoScreenPersistenceSupport.SaveOutcome saveOutcome = TodoScreenPersistenceSupport.saveAll(
                personalTaskManager,
                teamTaskManager,
                this.minecraft,
                personalHasUnsavedChanges,
                teamHasUnsavedChanges);
        personalHasUnsavedChanges = saveOutcome.personalHasUnsavedChanges;
        teamHasUnsavedChanges = saveOutcome.teamHasUnsavedChanges;
        hasUnsavedChanges = saveOutcome.hasUnsavedChanges();
        if (saveOutcome.allSaved()) {
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            closeTaskContextMenu();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && quickAddField != null
                && quickAddField.isFocused()) {
            if (!TodoScreenViewModeSupport.isAddTaskAllowedInCurrentView(viewMode.name())) {
                addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
                return true;
            }
            onAddTask();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 清理当前界面内所有文本输入框的焦点。
     */
    private void clearTextFieldFocus() {
        TodoScreenFocusSupport.clearTextFieldFocus(
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField);
        projectSearchPrefixDropdownOpen = false;
        this.setFocused(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncTaskListReorderState();
        if (handleContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        boolean prefixDropdownVisible = TodoScreenProjectSearchSupport.shouldShowProjectSearchPrefixDropdown(
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible(),
                projectSearchField
        );
        boolean projectSearchFieldHit = TodoScreenProjectSearchSupport.isProjectSearchFieldHit(projectSearchField, mouseX, mouseY);
        boolean insideProjectSearchPrefixDropdown = TodoScreenProjectSearchSupport.isInsideProjectSearchPrefixDropdown(
                prefixDropdownVisible,
                projectSearchField,
                mouseX,
                mouseY);
        boolean mouseOverQuickAddMarker = false;
        boolean mouseOverAnyTextField = TodoScreenHitTestSupport.isMouseOverAnyTextField(
                mouseX,
                mouseY,
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField,
                false);
        boolean sidebarToggleHovered = sidebarToggleButton != null && sidebarToggleButton.isMouseOver(mouseX, mouseY);
        boolean insideSidebar = layoutMetrics != null && layoutMetrics.sidebarBounds.contains(mouseX, mouseY);
        if (button == 0
                && layoutMetrics != null
                && layoutMetrics.sidebarOverlay
                && layoutMetrics.sidebarVisible
                && !insideSidebar
                && !sidebarToggleHovered) {
            sidebarOverlayVisible = false;
            layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                    ModConfig.getInstance(),
                    responsiveTier,
                    this.width,
                    this.height,
                    sidebarOverlayVisible,
                    detailOverlayVisible,
                    selectedTask != null);
            applyResponsiveWidgetVisibility();
        }
        String clickedPrefix = button == 0
                ? TodoScreenProjectSearchSupport.resolveClickedProjectSearchPrefix(
                        prefixDropdownVisible,
                        projectSearchField,
                        mouseX,
                        mouseY)
                : null;
        if (clickedPrefix != null) {
            String current = projectSearchField == null ? "" : projectSearchField.getValue();
            String nextValue = TodoScreenProjectSearchSupport.buildProjectSearchValueWithPrefix(current, clickedPrefix);
            if (projectSearchField != null) {
                projectSearchField.setValue(nextValue);
                projectSearchField.setFocused(true);
                this.setFocused(projectSearchField);
            }
            projectSearchQuery = nextValue;
            updateProjectList();
            projectSearchPrefixDropdownOpen = false;
            return true;
        }
        if ((button == 0 || button == 1)
                && prefixDropdownVisible
                && !projectSearchFieldHit
                && !insideProjectSearchPrefixDropdown) {
            projectSearchPrefixDropdownOpen = false;
        }
        if (button == 0
                && TodoScreenProjectSearchSupport.canUseProjectSearchPrefixDropdown(
                        currentSpaceMode == SpaceMode.TEAM,
                        isSidebarPanelVisible()
                )
                && projectSearchFieldHit) {
            projectSearchPrefixDropdownOpen = true;
        }
        if (taskListWidget != null && taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            resetTaskRowDragState();
            closeTaskContextMenu();
            return true;
        }
        if (projectListWidget != null && isSidebarPanelVisible() && projectListWidget.mouseClicked(mouseX, mouseY, button)) {
            resetTaskRowDragState();
            closeTaskContextMenu();
            return true;
        }

        if (taskListWidget != null) {
            TaskListWidget.TaskSectionHitResult sectionHit = taskListWidget.getSectionAt(mouseX, mouseY);
            Task clickedTask = taskListWidget.getTaskAt((int) mouseX, (int) mouseY);
            if (button == 0
                    && sectionHit != null
                    && sectionHit.getRowType() == TaskListWidget.RowType.SECTION_HEADER
                    && toggleTaskSection(sectionHit.getSectionId())) {
                applySearchFilter();
                resetTaskRowDragState();
                closeTaskContextMenu();
                return true;
            }
            if (clickedTask != null) {
                if (button == 0
                        && TodoScreenPermissionSupport.canTaskReorderInView(
                        this.minecraft,
                        currentProject,
                        viewMode.name())
                        && taskListWidget.canStartDrag(clickedTask)) {
                    String dragSectionId = sectionHit == null ? "active" : sectionHit.getSectionId();
                    taskListWidget.armPendingTaskDrag(clickedTask, dragSectionId, mouseX, mouseY);
                    pendingClickSelectionTask = clickedTask;
                    taskRowDragInProgress = false;
                    taskRowDragOrderSnapshot = TodoScreenTaskSupport.getVisibleIncompleteTaskIds(filteredTasks);
                    closeTaskContextMenu();
                    return true;
                }
                resetTaskRowDragState();
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

        if (button == 0 && mouseOverQuickAddMarker && quickAddField != null) {
            quickAddField.setFocused(true);
            this.setFocused(quickAddField);
            closeTaskContextMenu();
            return true;
        }

        boolean contextMenuHit = TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)
                && TodoScreenContextMenuSupport.isInsideContextMenu(
                mouseX,
                mouseY,
                contextMenuX,
                contextMenuY,
                contextMenuWidth,
                TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight));
        boolean clickInEditArea = TodoScreenHitTestSupport.isClickInEditArea(
                mouseX,
                mouseY,
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField,
                false,
                detailCloseButton,
                claimButton,
                abandonButton,
                assignOthersButton,
                contextMenuHit);
        boolean detailBlankClicked = button == 0
                && selectedTask != null
                && layoutMetrics != null
                && layoutMetrics.detailVisible
                && layoutMetrics.detailBounds.contains(mouseX, mouseY)
                && !clickInEditArea;
        if ((button == 0 || button == 1) && !mouseOverAnyTextField) {
            clearTextFieldFocus();
        }
        resetTaskRowDragState();
        boolean cleared = false;
        if (button == 0 && selectedTask != null && !detailBlankClicked && !clickInEditArea) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean handled = false;
        if (taskListWidget != null) {
            handled = taskListWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (!handled && projectListWidget != null && isSidebarPanelVisible()) {
            handled = projectListWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (!handled) {
            handled = super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
            resetTaskRowDragState();
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
                List<Task> reorderedActiveTasks = TodoScreenTaskSupport.extractIncompleteTasks(taskListWidget.getTasks());
                List<String> reorderedIds = TodoScreenTaskSupport.toNonNullTaskIds(reorderedActiveTasks);
                if (!reorderedIds.equals(taskRowDragOrderSnapshot)) {
                    onManualReorderActiveTasks(reorderedActiveTasks);
                }
            }
            resetTaskRowDragState();
            return true;
        }
        if (button == 0 && pendingClickSelectionTask != null) {
            selectTask(pendingClickSelectionTask);
            resetTaskRowDragState();
            return true;
        }
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
        boolean discarded = TodoScreenPersistenceSupport.discardUnsavedPersonalTasks(personalTaskManager, this.minecraft);
        if (discarded) {
            personalHasUnsavedChanges = false;
            if (viewMode == ViewMode.PERSONAL) {
                hasUnsavedChanges = false;
            }
        }
    }

    // 事件处理

    /**
     * 在当前视图下快速新增一条任务，并同步写入项目、作用域和默认指派信息。
     */
    private void onAddTask() {
        if (currentProject == null) {
            addNotification(Component.translatable("message.todolist.select_project_first").getString());
            return;
        }
        if (!TodoScreenViewModeSupport.isAddTaskAllowedInCurrentView(viewMode.name())) {
            addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
            return;
        }
        if (!TodoScreenPermissionSupport.canAddTaskInView(this.minecraft, currentProject, viewMode.name())) {
            addNotification(Component.translatable("message.todolist.no_permission_add_team").getString());
            return;
        }
        String title = getFieldValue(quickAddField, "");
        if (title.isEmpty()) {
            return;
        }
        Task task = taskManager.addTask(title, "");
        task.setPriority(selectedPriority);
        if (currentProject != null) {
            task.setProjectId(currentProject.getId());
        }
        if (viewMode != ViewMode.PERSONAL) {
            task.setScope(Task.Scope.TEAM);
            if (this.minecraft != null && this.minecraft.player != null) {
                String uuid = this.minecraft.player.getUUID().toString();
                String name = this.minecraft.player.getName().getString();
                task.setCreatorUuid(uuid);
                if (viewMode == ViewMode.TEAM_ASSIGNED) {
                    task.setAssigneeUuid(uuid);
                    task.setAssigneeName(name);
                }
            }
        }
        clearSelectedTask();
        selectedPriority = Task.Priority.MEDIUM;
        if (quickAddField != null) {
            quickAddField.setValue("");
        }
        markUnsaved();
        filterTasks();
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
     * 清空当前挂起的任务点击与拖拽状态。
     */
    private void resetTaskRowDragState() {
        pendingClickSelectionTask = null;
        taskRowDragInProgress = false;
        taskRowDragOrderSnapshot = List.of();
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
        return selectedTask != null
                && isSelectedTaskValid()
                && !selectedTask.isCompleted()
                && TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.EDIT_TASK,
                selectedTask,
                this.minecraft,
                currentProject,
                viewMode.name()
        );
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
        if (selectedTask == null || currentProject == null) {
            return false;
        }
        String selectedId = selectedTask.getId();
        if (selectedId == null || selectedId.isEmpty()) {
            return false;
        }
        if (!selectedTask.belongsToProject(currentProject.getId())) {
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

    private void updateButtonStates() {
        boolean hasSelection = selectedTask != null;
        boolean isCompleted = hasSelection && selectedTask.isCompleted();
        boolean isAssigned = hasSelection
                && selectedTask.getAssigneeUuid() != null
                && !selectedTask.getAssigneeUuid().isEmpty();
        Role role = TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject);
        ViewScope scope = TodoScreenPermissionSupport.resolveViewScope(viewMode.name());
        boolean isAssigneeSelf = hasSelection && TodoScreenPermissionSupport.isCurrentPlayerAssignee(this.minecraft, selectedTask);
        boolean projectMember = TodoScreenPermissionSupport.isCurrentPlayerProjectMember(this.minecraft, currentProject);
        boolean allowMemberCreate = currentProject != null && currentProject.isAllowMemberCreate();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember, allowMemberCreate);
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        boolean detailVisible = layoutMetrics == null ? selectedTask != null : layoutMetrics.detailVisible;
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
            assignOthersButton.active = detailVisible && showAssignOthers && hasSelection;
        }
        rebuildContextMenuIfNeeded();
        applyResponsiveWidgetVisibility();
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
        int menuHeight = TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight);
        int[] origin = TodoScreenContextMenuSupport.resolveMenuOrigin(
                mouseX,
                mouseY,
                this.width,
                this.height,
                contextMenuWidth,
                menuHeight,
                4
        );
        contextMenuX = origin[0];
        contextMenuY = origin[1];
    }

    private List<ContextMenuItem> buildContextMenuItems(Task task) {
        List<ContextMenuItem> items = new ArrayList<>();
        if (task == null) {
            return items;
        }
        boolean canEdit = TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.EDIT_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        ) && !task.isCompleted();
        boolean canDelete = TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.DELETE_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        );
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.high"), canEdit, () -> applyTaskPriority(task, Task.Priority.HIGH)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.medium"), canEdit, () -> applyTaskPriority(task, Task.Priority.MEDIUM)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.low"), canEdit, () -> applyTaskPriority(task, Task.Priority.LOW)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.delete"), canDelete, () -> deleteTaskFromContextMenu(task)));
        return items;
    }

    private void applyTaskPriority(Task task, Task.Priority priority) {
        if (task == null
                || priority == null
                || !TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.EDIT_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        )
                || task.isCompleted()) {
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
        selectedPriority = priority;
        markUnsaved();
        filterTasks();
        if (taskListWidget != null) {
            taskListWidget.ensureVisible(task);
        }
        ClientBridge.ops().sendUpdateTask(task);
        closeTaskContextMenu();
    }

    /**
     * 处理右键菜单中的删除任务动作，并在允许删除时打开确认弹窗。
     *
     * @param task 当前菜单对应的任务
     */
    private void deleteTaskFromContextMenu(Task task) {
        if (task == null || !TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.DELETE_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        )) {
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
        filterTasks();
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (!TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            return false;
        }
        if (button != 0 && button != 1) {
            return false;
        }
        int menuHeight = TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight);
        if (!TodoScreenContextMenuSupport.isInsideContextMenu(
                mouseX,
                mouseY,
                contextMenuX,
                contextMenuY,
                contextMenuWidth,
                menuHeight
        )) {
            if (button == 0 || button == 1) {
                closeTaskContextMenu();
            }
            return false;
        }
        if (button != 0) {
            return true;
        }
        int index = TodoScreenContextMenuSupport.resolveClickedItemIndex(
                mouseY,
                contextMenuY,
                contextMenuItemHeight,
                contextMenuItems.size()
        );
        if (index < 0) {
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

    private void closeTaskContextMenu() {
        contextMenuTask = null;
        contextMenuItems = new ArrayList<>();
    }

    private void rebuildContextMenuIfNeeded() {
        if (!TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
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
        if (!TodoScreenPermissionSupport.canTaskReorderInView(
                this.minecraft,
                currentProject,
                viewMode.name()) || taskManager == null || reorderedActiveTasks == null || reorderedActiveTasks.size() < 2) {
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
        filterTasks();
    }

    /**
     * 在当前视图内按优先级重新归位未完成任务，同时保留同优先级内部顺序。
     *
     * @param updatedTask 刚刚修改优先级的任务
     */
    private void reorderCurrentViewActiveTasksAfterPriorityChange(Task updatedTask) {
        if (updatedTask == null || updatedTask.getId() == null || taskManager == null) {
            return;
        }
        List<Task> scopedActiveTasks = getCurrentViewActiveTasksForOrdering();
        List<String> orderedTaskIds =
                TodoScreenTaskSupport.reorderCurrentViewActiveTaskIdsAfterPriorityChange(scopedActiveTasks, updatedTask);
        if (orderedTaskIds.isEmpty()) {
            return;
        }
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
        return TodoScreenTaskSupport.applyAssignedFilterForView(
                taskManager.getIncompleteTasks(),
                currentProject == null ? null : currentProject.getId(),
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft));
    }

    /**
     * 根据当前视图和权限状态同步任务列表的拖拽排序能力。
     */
    private void syncTaskListReorderState() {
        if (taskListWidget == null) {
            return;
        }
        taskListWidget.setTaskReorderEnabled(TodoScreenPermissionSupport.canTaskReorderInView(
                this.minecraft,
                currentProject,
                viewMode.name()
        ));
    }

    public static boolean hasPersonalUnsavedChanges() {
        return personalHasUnsavedChanges;
    }

    private void onClaimTask() {
        if (selectedTask == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        String validationMessageKey = TodoScreenPermissionSupport.validateClaimTask(
                selectedTask,
                this.minecraft,
                viewMode.name());
        if (validationMessageKey != null) {
            addNotification(Component.translatable(validationMessageKey).getString());
            return;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        selectedTask.setAssigneeUuid(uuid);
        selectedTask.setAssigneeName(this.minecraft.player.getName().getString());
        addNotification(Component.translatable("message.todolist.assigned_to_me").getString());
        markUnsaved();
        applySearchFilter();
    }

    /**
     * 按当前空间、搜索词和项目作用域刷新左侧项目列表。
     */
    private void updateProjectList() {
        if (projectListWidget == null) {
            return;
        }
        List<Project> visibleProjects = TodoScreenProjectSidebarSupport.buildVisibleProjectsForSidebar(
                projectManager,
                projectScopeFilter,
                projectSearchQuery,
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft)
        );
        projectListWidget.setProjects(visibleProjects);
        projectListWidget.setProjectTaskCounts(TodoScreenProjectSidebarSupport.buildSidebarProjectTaskCounts(
                visibleProjects,
                projectScopeFilter,
                personalTaskManager,
                teamTaskManager));
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();
    }

    /**
     * 根据当前项目和权限刷新左侧底部项目操作按钮状态。
     */
    private void updateProjectActionButtons() {
        if (editProjectBtn == null || deleteProjectBtn == null || applyJoinProjectBtn == null) {
            return;
        }
        int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
        Component joinMessage = Component.translatable(
                TodoScreenUiMetricsSupport.useCompactSidebarBottomButtons(responsiveTier, sidebarWidth)
                        ? "gui.todolist.project.join.compact"
                        : "gui.todolist.project.join.apply"
        );
        Component deleteMessage = Component.translatable("gui.todolist.delete");
        deleteProjectBtn.setMessage(deleteMessage);
        applyJoinProjectBtn.setMessage(joinMessage);
        Role role = TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject);
        boolean projectMember = TodoScreenPermissionSupport.isCurrentPlayerProjectMember(this.minecraft, currentProject);
        TodoScreenProjectActionSupport.ProjectActionState actionState = TodoScreenProjectActionSupport.resolve(
                currentProject,
                role,
                projectMember,
                TodoScreenProjectActionSupport.canDeleteCurrentProject(
                        currentProject,
                        TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                        role
                )
        );
        editProjectBtn.active = actionState.editActive;
        editProjectBtn.setMessage(Component.translatable(actionState.editMessageKey));
        deleteProjectBtn.visible = actionState.deleteVisible;
        deleteProjectBtn.active = actionState.deleteActive;
        applyJoinProjectBtn.visible = actionState.joinVisible;
        applyJoinProjectBtn.active = actionState.joinActive;
        boolean sidebarVisible = layoutMetrics == null || layoutMetrics.sidebarVisible;
        TodoScreenProjectActionSupport.applySidebarVisibility(
                sidebarVisible,
                addProjectBtn,
                editProjectBtn,
                deleteProjectBtn,
                applyJoinProjectBtn);
    }
    
    private void onAbandonTask() {
        if (selectedTask == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        String validationMessageKey = TodoScreenPermissionSupport.validateAbandonTask(
                selectedTask,
                this.minecraft,
                currentProject,
                viewMode.name());
        if (validationMessageKey != null) {
            addNotification(Component.translatable(validationMessageKey).getString());
            return;
        }
        selectedTask.setAssigneeUuid(null);
        selectedTask.setAssigneeName(null);
        addNotification(Component.translatable("message.todolist.abandoned_task").getString());
        markUnsaved();
        applySearchFilter();
    }

    private void onAssignOthers() {
        if (selectedTask == null || this.minecraft == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        if (!TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.EDIT_TASK,
                selectedTask,
                this.minecraft,
                currentProject,
                viewMode.name()
        )) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        this.minecraft.setScreen(createAssignPlayerScreen(selectedTask));
    }

    private void filterTasks() {
        currentFilter = "active";
        List<Task> result = taskManager == null ? List.of() : taskManager.getIncompleteTasks();
        result = TodoScreenTaskSupport.applyPriorityFilterToTasks(currentPriorityFilter, result);
        baseFilteredTasks = TodoScreenTaskSupport.applyAssignedFilterForView(
                result,
                currentProject == null ? null : currentProject.getId(),
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft));
        applySearchFilter();
        if (selectedTask != null && !isSelectedTaskValid()) {
            clearSelectedTask();
        }
        updateViewButtonsState();
    }

    private void applySearchFilter() {
        baseFilteredTasks = baseFilteredTasks == null ? new ArrayList<>() : baseFilteredTasks;
        filteredTasks = TodoScreenTaskSupport.applySearchQueryToTasks(searchQuery, baseFilteredTasks);
        if (taskListWidget != null) {
            taskListWidget.setTaskReorderEnabled(TodoScreenPermissionSupport.canTaskReorderInView(
                    this.minecraft,
                    currentProject,
                    viewMode.name()));
            taskListWidget.setSections(buildTaskPaneSections());
        }
    }

    /**
     * 构建任务面板的分组模型，包含未完成任务和可折叠的已完成任务。
     *
     * @return 任务列表需要渲染的分组模型集合
     */
    private List<TaskListWidget.SectionModel> buildTaskPaneSections() {
        return TodoScreenTaskSupport.buildTaskPaneSections(
                filteredTasks,
                TodoScreenTaskSupport.buildCompletedTasksForCurrentView(
                        taskManager,
                        currentProject == null ? null : currentProject.getId(),
                        currentPriorityFilter,
                        viewMode.name(),
                        TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                        searchQuery),
                activeExpanded,
                completedExpanded);
    }

    /**
     * 将组件转换为测试使用的边界数组。
     *
     * @param widget 目标组件
     * @return 组件边界数组；组件为空时返回零值数组
     */
    private int[] toWidgetBounds(net.minecraft.client.gui.components.AbstractWidget widget) {
        return TodoScreenTestSupport.toWidgetBounds(widget);
    }

    private String getFieldValue(EditBox field, String hint) {
        return TodoScreenTestSupport.getFieldValue(field, hint);
    }

    private String getFieldValue(MultiLineEditBox field, String hint) {
        return TodoScreenTestSupport.getFieldValue(field, hint);
    }

    private void addNotification(String text) {
        long now = System.currentTimeMillis();
        notifications.add(TodoScreenNotificationSupport.createNotification(
                text,
                now,
                TodoScreenNotificationSupport.DEFAULT_DURATION_MS
        ));
    }

    /**
     * 切换当前视图模式，并同步 HUD 默认视图与界面按钮状态。
     *
     * @param mode 目标视图模式
     */
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
        if (TodoScreenProjectActionSupport.isTeamProjectBlocked(currentProject, teamProjectsEnabled)) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        minecraft.setScreen(new ProjectSettingsScreen(this, currentProject));
    }

    private void onApplyJoinProject() {
        if (currentProject == null) return;
        if (TodoScreenProjectActionSupport.isTeamProjectBlocked(currentProject, teamProjectsEnabled)) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            return;
        }
        if (TodoScreenPermissionSupport.isCurrentPlayerProjectMember(this.minecraft, currentProject)) {
            return;
        }
        ClientBridge.ops().sendRequestJoinProject(currentProject.getId());
        addNotification(Component.translatable("message.todolist.project.join.sent").getString());
    }

    private void onProjectDelete() {
        if (currentProject == null) return;
        if (TodoScreenProjectActionSupport.isTeamProjectBlocked(currentProject, teamProjectsEnabled)) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (!TodoScreenProjectActionSupport.canDeleteCurrentProject(
                currentProject,
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject)
        )) {
            addNotification(Component.translatable("message.todolist.no_permission_delete_project").getString());
            return;
        }
        String projectName = ProjectNameFormatter.toDisplayText(currentProject).getString();
        Component message = Component.translatable("gui.todolist.project.delete_confirm.message", projectName);
        minecraft.setScreen(new ConfirmDeleteProjectScreen(this, message, () -> {
            ClientBridge.ops().sendDeleteProject(currentProject.getId());
        }));
    }

}


