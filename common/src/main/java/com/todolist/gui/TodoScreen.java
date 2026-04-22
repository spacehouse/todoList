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
import com.todolist.gui.TodoScreenProjectSearchSupport.ProjectSearchPrefixOption;
import com.todolist.gui.TodoScreenProjectSearchSupport.ProjectSearchQuery;
import com.todolist.gui.TodoScreenProjectSearchSupport.ProjectSearchRoleFilter;
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
import java.util.HashMap;
import java.util.List;
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
 * 待办主界面，负责项目侧栏、任务列表、详情面板和相关弹窗的交互。
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

    // Input fields
    private EditBox searchField;
    private EditBox quickAddField;
    private EditBox titleField;
    private MultiLineEditBox descField;
    private EditBox tagField;

    // Action buttons
    private Button detailCloseButton;
    private Button claimButton;
    private Button abandonButton;
    private Button assignOthersButton;
    private Button saveButton;
    private Button cancelButton;
    private Button sidebarToggleButton;

    // Priority selected for task creation or editing
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // Filter and config buttons
    private Button filterStatusButton;
    private Button filterPriorityButton;
    private Button viewToggleButton;
    private Button configButton;
    
    // Project search and sidebar state
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

    @Override
    protected void init() {
        super.init();
        openedStorageNamespace = DataPathProvider.getStorageNamespace();

        // Initialize the personal task manager from local storage.
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
        
        // Initialize project manager state and team project availability.
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
        
        // Restore the selected project if it still exists.
        if (currentProject != null) {
            Project p = projectManager.getProject(currentProject.getId());
            if (p == null) {
                currentProject = null; // Project was deleted
            } else {
                currentProject = p; // Update reference to fresh object
            }
        }

        if (currentProject == null) {
            currentProject = TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                    projectManager,
                    projectScopeFilter,
                    teamProjectsEnabled,
                    preferredTeamProjectId,
                    preferredPersonalProjectId
            );
        }
        if (currentProject != null) {
            String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                    currentProject,
                    preferredPersonalProjectId,
                    preferredTeamProjectId
            );
            preferredPersonalProjectId = preferredIds[0];
            preferredTeamProjectId = preferredIds[1];
            projectScopeFilter = currentProject.getScope();
        }
        syncViewStateForCurrentProject();
        
        // Choose the active task manager for the current space.
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
     * 根据当前项目重新同步空间模式、任务视图选项和兼容状态。
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
     * 根据分组标识切换对应分组的展开状态。
     *
     * @param sectionId 分组标识
     * @return 成功切换时返回 {@code true}
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
        if (lastGuiState.searchQuery != null) {
            searchQuery = lastGuiState.searchQuery;
        }

        if (lastGuiState.currentProjectId != null && !lastGuiState.currentProjectId.isEmpty()) {
            Project p = projectManager.getProject(lastGuiState.currentProjectId);
            if (p != null && (teamProjectsEnabled || p.getScope() == Project.Scope.PERSONAL)) {
                currentProject = p;
                projectScopeFilter = p.getScope();
                String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                        p,
                        preferredPersonalProjectId,
                        preferredTeamProjectId
                );
                preferredPersonalProjectId = preferredIds[0];
                preferredTeamProjectId = preferredIds[1];
            }
        }
    }

    private void saveLastGuiState() {
        String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                currentProject,
                preferredPersonalProjectId,
                preferredTeamProjectId
        );
        preferredPersonalProjectId = preferredIds[0];
        preferredTeamProjectId = preferredIds[1];
        LastGuiState s = new LastGuiState();
        s.projectScopeFilter = projectScopeFilter;
        s.currentProjectId = currentProject == null ? null : currentProject.getId();
        s.viewMode = viewMode;
        s.spaceMode = currentSpaceMode;
        s.taskViewOption = currentTaskViewOption;
        s.activeExpanded = activeExpanded;
        s.completedExpanded = completedExpanded;
        s.currentPriorityFilter = currentPriorityFilter;
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
                    TodoScreenProjectCleanupSupport.hardDeleteTasksForDeletedProject(
                            personalTaskManager,
                            teamTaskManager,
                            project
                    );
                }
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    switchProject(TodoScreenProjectPreferenceSupport.resolveFallbackProjectAfterRemoval(
                            projectManager,
                            project,
                            projectScopeFilter,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId
                    ));
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
                            preferredPersonalProjectId
                    ));
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
     * 在项目增删改之后刷新当前项目、侧栏和任务列表。
     */
    private void refreshAfterProjectMutation() {
        currentProject = TodoScreenActiveProjectSupport.syncActiveProjectIdWithCurrentProject(projectManager, currentProject);
        updateProjectList();
        applySearchFilter();
        updateButtonStates();
        updateProjectActionButtons();
    }

    private void rebuildUI() {
        syncViewStateForCurrentProject();
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
        responsiveTier = TodoScreenLayoutSupport.resolveResponsiveTier(this.width, this.height);
        syncOverlayStateForResponsiveTier();

        layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                config,
                responsiveTier,
                this.width,
                this.height,
                sidebarOverlayVisible,
                detailOverlayVisible,
                selectedTask != null
        );
        LayoutRect sidebarBounds = layoutMetrics.sidebarBounds;
        LayoutRect contentBounds = layoutMetrics.contentBounds;
        LayoutRect detailBounds = layoutMetrics.detailBounds;
        int padding = layoutMetrics.padding;
        int panelGap = layoutMetrics.gap;
        int contentControlInset = TodoScreenUiMetricsSupport.getContentControlInset(responsiveTier);
        int contentControlX = contentBounds.x + contentControlInset;
        int contentControlWidth = Math.max(80, contentBounds.width - contentControlInset * 2);
        int contentControlRight = contentControlX + contentControlWidth;
        int topBarGap = Math.max(3, Math.min(config.getElementSpacing(), 8));
        int topBarHeight = TodoScreenUiMetricsSupport.getContentTopBarHeight(responsiveTier);
        int topBarY = contentBounds.y + TodoScreenUiMetricsSupport.getContentTopPadding(responsiveTier);
        int secondRowHeight = TodoScreenUiMetricsSupport.getContentSearchFieldHeight(responsiveTier);
        int secondRowY = topBarY + topBarHeight + TodoScreenUiMetricsSupport.getContentHeaderGap(responsiveTier);
        int inputRowHeight = TodoScreenUiMetricsSupport.getContentQuickAddFieldHeight(responsiveTier);
        int inputRowY = contentBounds.y + contentBounds.height - inputRowHeight - TodoScreenUiMetricsSupport.getContentBottomPadding(responsiveTier);
        int bottomActionRowHeight = TodoScreenUiMetricsSupport.getContentBottomActionRowHeight(responsiveTier);
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
                () -> this.minecraft.setScreen(new ConfigScreen(this))
        );
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
                () -> {
                    Project targetProject = TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                            projectManager,
                            Project.Scope.PERSONAL,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId
                    );
                    switchProject(targetProject);
                },
                () -> {
                    if (!teamProjectsEnabled) {
                        return;
                    }
                    Project targetProject = TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                            projectManager,
                            Project.Scope.TEAM,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId
                    );
                    switchProject(targetProject);
                },
                () -> {
                    if (currentSpaceMode == SpaceMode.PERSONAL) {
                        switchView(ViewMode.PERSONAL);
                        return;
                    }
                    switchView(ViewMode.TEAM_ASSIGNED);
                },
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
                layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width,
                currentProject != null
        );
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
                        viewMode.name()
                ),
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
                    applySearchFilter();
                },
                this::onManualReorderActiveTasks,
                contentControlX,
                inputRowY,
                Math.max(60, contentControlWidth),
                inputRowHeight
        );
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
                this::onAssignOthers
        );
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
                this::onClose
        );
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
     * Bind detail and search field responders.
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
     * 根据当前响应式布局更新各组件的显隐状态。
     */
    private void applyResponsiveWidgetVisibility() {
        if (layoutMetrics == null) {
            return;
        }
        TodoScreenSidebarViewButtonLayoutSupport.apply(
                myViewButton,
                unassignedViewButton,
                allViewButton,
                layoutMetrics.sidebarBounds,
                responsiveTier,
                currentSpaceMode.name()
        );
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
            int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
            unassignedViewButton.setMessage(TodoScreenUiMetricsSupport.getUnassignedTeamViewText(sidebarWidth));
            unassignedViewButton.active = teamSpaceVisible && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.visible = teamSpaceVisible;
            allViewButton.setMessage(TodoScreenUiMetricsSupport.getAllTeamViewText());
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
        projectSearchPrefixDropdownOpen = TodoScreenProjectSearchSupport.syncProjectSearchPrefixDropdownState(
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible(),
                projectSearchField
        );
    }

    /**
     * 切换侧栏覆盖层的显示状态。
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
                selectedTask != null
        );
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
     * 切换任务完成状态，并立即重建当前视图的未完成/已完成分组数据。
     *
     * @param task 目标任务
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

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());

        Component title = hasUnsavedChanges ? Component.translatable("gui.todolist.title.unsaved") : TITLE;
        context.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 10, 0xFFFFFFFF, false);

        TodoScreenRenderSupport.renderLayoutPanels(
                context,
                layoutMetrics,
                isSidebarPanelVisible(),
                isDetailPanelVisible()
        );
        TodoScreenRenderSupport.renderContentHeaderSummary(
                context,
                this.font,
                layoutMetrics,
                responsiveTier,
                currentSpaceMode,
                currentProject,
                configButton,
                sidebarToggleButton
        );
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
        renderTaskContextMenu(context, mouseX, mouseY);
        TodoScreenRenderSupport.renderNotifications(
                context,
                this.font,
                this.width,
                searchField,
                notifications
        );
    }

    /**
     * Save personal and team task data from the current screen.
     */
    private void onSaveTasks() {
        TodoScreenPersistenceSupport.SaveOutcome outcome = TodoScreenPersistenceSupport.saveAll(
                personalTaskManager,
                teamTaskManager,
                this.minecraft,
                personalHasUnsavedChanges,
                teamHasUnsavedChanges
        );
        personalHasUnsavedChanges = outcome.personalHasUnsavedChanges;
        teamHasUnsavedChanges = outcome.teamHasUnsavedChanges;
        hasUnsavedChanges = outcome.hasUnsavedChanges();
        if (outcome.allSaved()) {
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
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (quickAddField != null && quickAddField.isFocused()) {
                if (!TodoScreenViewModeSupport.isAddTaskAllowedInCurrentView(viewMode.name())) {
                    addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
                    return true;
                }
                onAddTask();
                return true;
            }
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
                tagField
        );
        projectSearchPrefixDropdownOpen = false;
        this.setFocused(null);
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
            layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                    ModConfig.getInstance(),
                    responsiveTier,
                    this.width,
                    this.height,
                    sidebarOverlayVisible,
                    detailOverlayVisible,
                    selectedTask != null
            );
            applyResponsiveWidgetVisibility();
        }
        boolean prefixDropdownVisible = TodoScreenProjectSearchSupport.shouldShowProjectSearchPrefixDropdown(
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible(),
                projectSearchField
        );
        String clickedPrefix = button == 0
                ? TodoScreenProjectSearchSupport.resolveClickedProjectSearchPrefix(
                        prefixDropdownVisible,
                        projectSearchField,
                        mouseX,
                        mouseY
                )
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
        if ((button == 0 || button == 1) && prefixDropdownVisible
                && !TodoScreenProjectSearchSupport.isProjectSearchFieldHit(projectSearchField, mouseX, mouseY)
                && !TodoScreenProjectSearchSupport.isInsideProjectSearchPrefixDropdown(
                        prefixDropdownVisible,
                        projectSearchField,
                        mouseX,
                        mouseY
                )) {
            projectSearchPrefixDropdownOpen = false;
        }
        if (button == 0
                && TodoScreenProjectSearchSupport.canUseProjectSearchPrefixDropdown(
                        currentSpaceMode == SpaceMode.TEAM,
                        isSidebarPanelVisible()
                )
                && TodoScreenProjectSearchSupport.isProjectSearchFieldHit(projectSearchField, mouseX, mouseY)) {
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
            if (button == 0
                    && sectionHit != null
                    && sectionHit.getRowType() == TaskListWidget.RowType.SECTION_HEADER
                    && toggleTaskSection(sectionHit.getSectionId())) {
                applySearchFilter();
                resetTaskRowDragState();
                closeTaskContextMenu();
                return true;
            }
            Task clickedTask = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
            if (clickedTask != null) {
                if (button == 0
                        && TodoScreenPermissionSupport.canTaskReorderInView(
                        this.minecraft,
                        currentProject,
                        viewMode.name()
                )
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

        boolean textFieldHit = TodoScreenHitTestSupport.isMouseOverAnyTextField(
                mouseX,
                mouseY,
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField,
                false
        );
        if (button == 0 && !textFieldHit) {
            clearTextFieldFocus();
        }

        boolean contextMenuHit = TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)
                && TodoScreenContextMenuSupport.isInsideContextMenu(
                mouseX,
                mouseY,
                contextMenuX,
                contextMenuY,
                contextMenuWidth,
                TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight)
        );
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
                contextMenuHit
        );
        boolean cleared = false;
        boolean detailBlankClicked = button == 0
                && selectedTask != null
                && layoutMetrics != null
                && layoutMetrics.detailVisible
                && layoutMetrics.detailBounds.contains(mouseX, mouseY)
                && !clickInEditArea;
        resetTaskRowDragState();
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
            Task taskToSelect = pendingClickSelectionTask;
            resetTaskRowDragState();
            selectTask(taskToSelect);
            return true;
        }
        resetTaskRowDragState();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void resetTaskRowDragState() {
        pendingClickSelectionTask = null;
        taskRowDragInProgress = false;
        taskRowDragOrderSnapshot = List.of();
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
     * 在关闭界面且未保存时按需丢弃个人任务的临时改动。
     */
    private void discardPersonalTasksOnCloseIfNeeded() {
        if (!personalHasUnsavedChanges || personalTaskManager == null) {
            return;
        }
        boolean restored = TodoScreenPersistenceSupport.discardUnsavedPersonalTasks(personalTaskManager, this.minecraft);
        if (restored) {
            personalHasUnsavedChanges = false;
            if (viewMode == ViewMode.PERSONAL) {
                hasUnsavedChanges = false;
            }
        }
    }

    // Add a new task

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
        String title = TodoScreenTestSupport.getFieldValue(quickAddField, "");
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
            applySearchFilter();
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
     * @return 对应的详情草稿；任务为空时返回 {@code null}
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
     * 判断当前选中任务的详情是否允许编辑。
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
        String value = TodoScreenTestSupport.getFieldValue(tagField, "");
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
        this.selectedPriority = priority;
        markUnsaved();
        applySearchFilter();
        if (taskListWidget != null) {
            taskListWidget.ensureVisible(task);
        }
        ClientBridge.ops().sendUpdateTask(task);
        closeTaskContextMenu();
    }

    /**
     * 处理任务右键菜单中的删除入口，先打开二次确认弹窗。
     *
     * @param task 目标任务
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
     * 在用户确认后真正删除任务，并同步刷新选中状态与列表显示。
     * 删除成功后会立即尝试持久化当前视图任务，避免再额外点击保存按钮。
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
        if (!persistCurrentViewTasksImmediatelyAfterDelete()) {
            markUnsaved();
        }
        applySearchFilter();
    }

    /**
     * 删除任务后立即持久化当前视图任务，避免删除操作还需要额外点击保存。
     *
     * @return 持久化成功返回 {@code true}，失败返回 {@code false}
     */
    private boolean persistCurrentViewTasksImmediatelyAfterDelete() {
        try {
            if (viewMode == ViewMode.PERSONAL) {
                TodoScreenPersistenceSupport.savePersonalTasks(personalTaskManager, this.minecraft);
                personalHasUnsavedChanges = false;
            } else {
                TodoScreenPersistenceSupport.saveTeamTasks(teamTaskManager, this.minecraft);
                teamHasUnsavedChanges = false;
            }
            hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
            return true;
        } catch (Exception exception) {
            TodoConstants.LOGGER.error("Failed to persist task deletion immediately", exception);
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.todolist.save_failed"), false);
            } else {
                addNotification(Component.translatable("message.todolist.save_failed").getString());
            }
            return false;
        }
    }

    private void renderTaskContextMenu(GuiGraphics context, int mouseX, int mouseY) {
        if (!TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            return;
        }
        int menuHeight = TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight);
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
        if (!TodoScreenPermissionSupport.canTaskReorderInView(this.minecraft, currentProject, viewMode.name())
                || taskManager == null
                || reorderedActiveTasks == null
                || reorderedActiveTasks.size() < 2) {
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
        applySearchFilter();
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
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft)
        );
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
        String validationKey = TodoScreenPermissionSupport.validateClaimTask(
                selectedTask,
                this.minecraft,
                viewMode.name()
        );
        if (validationKey != null) {
            addNotification(Component.translatable(validationKey).getString());
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
        if (projectListWidget == null) return;
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
                teamTaskManager
        ));
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();
    }

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
        TodoScreenProjectActionSupport.ProjectActionState state = TodoScreenProjectActionSupport.resolve(
                currentProject,
                role,
                projectMember,
                TodoScreenProjectActionSupport.canDeleteCurrentProject(
                        currentProject,
                        TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                        role
                )
        );
        editProjectBtn.active = state.editActive;
        editProjectBtn.setMessage(Component.translatable(state.editMessageKey));
        deleteProjectBtn.visible = state.deleteVisible;
        deleteProjectBtn.active = state.deleteActive;
        applyJoinProjectBtn.visible = state.joinVisible;
        applyJoinProjectBtn.active = state.joinActive;
        boolean sidebarVisible = layoutMetrics == null || layoutMetrics.sidebarVisible;
        TodoScreenProjectActionSupport.applySidebarVisibility(
                sidebarVisible,
                addProjectBtn,
                editProjectBtn,
                deleteProjectBtn,
                applyJoinProjectBtn
        );
    }
    
    private void onAbandonTask() {
        if (selectedTask == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        String validationKey = TodoScreenPermissionSupport.validateAbandonTask(
                selectedTask,
                this.minecraft,
                currentProject,
                viewMode.name()
        );
        if (validationKey != null) {
            addNotification(Component.translatable(validationKey).getString());
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

    /**
     * Create the member assignment dialog for the given task.
     *
     * @param task target task
     * @return assignment dialog screen
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
     * Apply the selected member assignment to the task.
     *
     * @param task target task
     * @param memberUuid selected member UUID
     * @param memberName selected member display name
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

    private void filterTasks() {
        List<Task> result = taskManager == null ? new ArrayList<>() : taskManager.getIncompleteTasks();
        result = TodoScreenTaskSupport.applyPriorityFilterToTasks(currentPriorityFilter, result);

        baseFilteredTasks = TodoScreenTaskSupport.applyAssignedFilterForView(
                result,
                currentProject == null ? null : currentProject.getId(),
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft)
        );
        applySearchFilter();
        if (selectedTask != null && !isSelectedTaskValid()) {
            clearSelectedTask();
        }
        updateViewButtonsState();
    }

    private void applySearchFilter() {
        if (baseFilteredTasks == null) {
            baseFilteredTasks = new ArrayList<>();
        }
        filteredTasks = TodoScreenTaskSupport.applySearchQueryToTasks(searchQuery, baseFilteredTasks);
        if (taskListWidget != null) {
            taskListWidget.setTaskReorderEnabled(TodoScreenPermissionSupport.canTaskReorderInView(
                    this.minecraft,
                    currentProject,
                    viewMode.name()
            ));
            taskListWidget.setSections(buildTaskPaneSections());
        }
    }

    /**
     * 构建任务面板的分组模型，包含未完成任务和可折叠的已完成任务。
     *
     * @return 任务列表需要渲染的分组模型集合
     */
    private List<TaskListWidget.SectionModel> buildTaskPaneSections() {
        List<Task> activeTasks = filteredTasks == null ? List.of() : List.copyOf(filteredTasks);
        List<Task> completedTasks = TodoScreenTaskSupport.buildCompletedTasksForCurrentView(
                taskManager,
                currentProject == null ? null : currentProject.getId(),
                currentPriorityFilter,
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                searchQuery
        );
        return TodoScreenTaskSupport.buildTaskPaneSections(
                activeTasks,
                completedTasks,
                activeExpanded,
                completedExpanded
        );
    }

    private void addNotification(String text) {
        long now = System.currentTimeMillis();
        notifications.add(TodoScreenNotificationSupport.createNotification(
                text,
                now,
                TodoScreenNotificationSupport.DEFAULT_DURATION_MS
        ));
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
        TodoScreenViewModeSupport.HudViewState state = TodoScreenViewModeSupport.resolveHudViewState(
                project,
                teamProjectsEnabled,
                config.getHudDefaultView()
        );
        currentSpaceMode = SpaceMode.valueOf(state.spaceModeName);
        currentTaskViewOption = TaskViewOption.valueOf(state.taskViewOptionName);
        viewMode = ViewMode.valueOf(state.legacyViewModeName);
        config.setHudDefaultView(state.hudDefaultViewToSave);
    }
    
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
            myViewButton.setMessage(Component.literal("\u6211\u7684"));
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
    
    private void switchProject(Project project) {
        if (projectListWidget != null) {
            savedProjectListScrollOffset = projectListWidget.getScrollOffset();
        }
        this.selectedTask = null;
        this.detailOverlayVisible = false;
        this.sidebarOverlayVisible = false;
        projectSearchPrefixDropdownOpen = false;
        this.currentProject = project;
        String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                project,
                preferredPersonalProjectId,
                preferredTeamProjectId
        );
        preferredPersonalProjectId = preferredIds[0];
        preferredTeamProjectId = preferredIds[1];

        if (project == null) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                    currentSpaceMode.name(),
                    currentTaskViewOption.name()
            ));
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
