package com.todolist.gui;

import com.todolist.TodoListMod;
import com.todolist.client.TodoClient;
import com.todolist.client.TodoHudRenderer;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import com.todolist.client.ClientProjectPackets;
import com.todolist.client.ClientTaskPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

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
    private static final Text TITLE = Text.translatable("gui.todolist.title");

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
    private TextFieldWidget searchField;
    private TextFieldWidget titleField;
    private TextFieldWidget descField;
    private TextFieldWidget tagField;

    // Buttons
    private ButtonWidget addButton;
    private ButtonWidget deleteButton;
    private ButtonWidget claimButton;
    private ButtonWidget abandonButton;
    private ButtonWidget assignOthersButton;
    private ButtonWidget[] priorityButtons;

    // Selected priority for new/edited tasks
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // Filter buttons
    private ButtonWidget filterStatusButton;
    private ButtonWidget filterPriorityButton; // Unified priority button
    private ButtonWidget viewToggleButton;
    private ButtonWidget configButton;
    
    // Project Search & Toggle
    private TextFieldWidget projectSearchField;
    private ButtonWidget projectScopeButton;
    private ButtonWidget editProjectBtn;
    private ButtonWidget deleteProjectBtn;
    private ButtonWidget applyJoinProjectBtn;
    private Project.Scope projectScopeFilter = Project.Scope.PERSONAL;
    private String projectSearchQuery = "";
    private boolean teamProjectsEnabled = true;
    
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

    private static class LastGuiState {
        Project.Scope projectScopeFilter;
        String currentProjectId;
        ViewMode viewMode;
        int currentPriorityFilter;
        String currentFilter;
        String searchQuery;
        String projectSearchQuery;
    }


    public TodoScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Initialize task manager and load tasks from storage
        if (personalTaskManager == null) {
            personalTaskManager = new TaskManager();
            try {
                List<Task> loadedTasks = TodoListMod.getTaskStorage().loadTasks();
                for (Task task : loadedTasks) {
                    personalTaskManager.addTask(task);
                }
                TodoListMod.LOGGER.info("Loaded {} tasks from storage", loadedTasks.size());
            } catch (Exception e) {
                TodoListMod.LOGGER.error("Failed to load tasks from storage", e);
            }
        }

        teamTaskManager = TodoClient.getTeamTaskManager();
        
        // Initialize ProjectManager
        projectManager = TodoListMod.getProjectManager();
        projectManager.addListener(this);

        teamProjectsEnabled = TodoClient.isTeamProjectsEnabled();
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
            // Default to first personal project
            List<Project> projects = projectManager.getProjectsByScope(Project.Scope.PERSONAL);
            if (!projects.isEmpty()) {
                currentProject = projects.get(0);
            } else if (teamProjectsEnabled) {
                List<Project> teamProjects = projectManager.getProjectsByScope(Project.Scope.TEAM);
                if (!teamProjects.isEmpty()) {
                    currentProject = teamProjects.get(0);
                }
            }
        }
        
        // Ensure taskManager matches currentProject
        if (currentProject != null) {
            if (!teamProjectsEnabled || currentProject.getScope() == Project.Scope.PERSONAL) {
                // For Personal Project, we should use a project-specific task manager or filter the main one?
                // Currently personalTaskManager loads ALL personal tasks.
                // Requirement 4: "Different personal projects still show the same task list, personal projects should be local and independent"
                // So we need to filter personalTaskManager by project ID.
                taskManager = new TaskManager(); // Temporary manager for view? Or just use personalTaskManager and filter?
                // Better: Use personalTaskManager but filter in `rebuildUI` by project ID.
                // But `taskManager` is used for add/delete etc.
                // If we add to `personalTaskManager`, we need to set Project ID.
                taskManager = personalTaskManager;
                viewMode = ViewMode.PERSONAL;
            } else {
                taskManager = teamTaskManager;
                // Default to ALL if switching to team
                if (viewMode == ViewMode.PERSONAL) {
                    viewMode = ViewMode.TEAM_UNASSIGNED; // Default to UNASSIGNED for team
                }
            }
        } else {
            taskManager = personalTaskManager;
            viewMode = ViewMode.PERSONAL;
        }

        TodoClient.setActiveProjectId(currentProject != null ? currentProject.getId() : null);
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
            }
        }
    }

    private void saveLastGuiState() {
        LastGuiState s = new LastGuiState();
        s.projectScopeFilter = projectScopeFilter;
        s.currentProjectId = currentProject == null ? null : currentProject.getId();
        s.viewMode = viewMode;
        s.currentPriorityFilter = currentPriorityFilter;
        s.currentFilter = currentFilter;
        s.searchQuery = searchQuery;
        s.projectSearchQuery = projectSearchQuery;
        lastGuiState = s;
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (this.client == null) return;
        this.client.execute(() -> {
            if (type == ProjectManager.ProjectChangeType.CLEARED) {
                currentProject = null;
                // Reset view mode if needed
                viewMode = ViewMode.PERSONAL; 
                init(); // Re-initialize to pick a default project
                return;
            }

            if (project == null) return;

            if (type == ProjectManager.ProjectChangeType.REMOVED) {
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    currentProject = null;
                    init(); // Re-initialize to pick another project
                } else {
                    updateProjectList();
                }
            } else if (type == ProjectManager.ProjectChangeType.ADDED) {
                updateProjectList();
                // Optional: Auto-select newly created project?
                // Only if user just created it? Hard to tell here.
            } else if (type == ProjectManager.ProjectChangeType.UPDATED) {
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    currentProject = project; // Update reference
                    // Rebuild UI to update title/permissions
                    // But rebuildUI is heavy. Maybe just updateProjectList() and specific fields?
                    // Permissions might change, so init() or rebuildUI() is safer.
                    // But init() resets everything.
                    // Let's just update project list and maybe settings button state.
                    updateProjectList();
                    // If name changed, we might need to update displayed name in UI if any.
                    // If scope changed (unlikely), we might need to switch view.
                } else {
                    updateProjectList();
                }
            }
        });
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
        this.clearChildren();

        // Get configuration
        ModConfig config = ModConfig.getInstance();

        // Calculate layout
        int guiWidth = config.getGuiWidth();
        int guiHeight = config.getGuiHeight();
        int x = (this.width - guiWidth) / 2;
        
        int standardHeight = 400;
        int y = (this.height - standardHeight) / 2;
        if (y < 10) y = 10;
        
        int padding = config.getPadding();
        int headerOffset = 60;
        int e = config.getElementSpacing();
        
        // Sidebar Layout
        int sidebarWidth = config.getProjectSidebarWidth();
        int sidebarGap = 8;
        
        // Determine layout based on Scope (Task List)
        boolean isTeam = currentProject != null && currentProject.getScope() == Project.Scope.TEAM;
        int topRowY = y + 10;
        int secondRowY = topRowY + 24;

        int listTop = y + headerOffset;
        
        // 1. Sidebar Controls & List
        
        // Align Sidebar Top with Right Side "View" buttons
        // Right side "View" buttons start at `topRowY` (y+10)
        // So sidebar should also start at `topRowY`?
        // But listTop is y + headerOffset.
        // User said: "1. Sidebar project part can be moved up overall, aligned with the top of the 'View' part on the right"
        // View part starts at `topRowY` (y+10).
        // So we should move sidebar up.
        
        int sidebarTopY = topRowY; 
        
        // Project Sidebar Layout
        // Scope Button: Top
        // Search Field: Below Button
        // List: Below Search
        int sidebarScopeBtnHeight = 20;
        int sidebarSearchHeight = 16;
        int sidebarHeaderHeight = sidebarScopeBtnHeight + e + sidebarSearchHeight + e;
        
        int projListY = sidebarTopY + sidebarHeaderHeight;
        
        // Main content area adjusted for sidebar
        int contentX = x + padding + sidebarWidth + sidebarGap;
        int contentWidth = guiWidth - padding * 2 - sidebarWidth - sidebarGap;
        
        // Height calculations
        int paddingV = 10;
        int inputRows = 3; 
        int rowHeight = 20;
        int fieldsBlock = inputRows * rowHeight + (inputRows - 1) * e;
        int priorityRow = rowHeight + e;
        int actionRow = rowHeight + e;
        int saveRow = rowHeight;
        int bottomReserved = paddingV + fieldsBlock + priorityRow + actionRow + saveRow + paddingV;
        int maxListHeight = Math.max(rowHeight * 2, guiHeight - headerOffset - bottomReserved);
        
        int configuredListHeight = config.getTaskListHeight();
        // If sidebar height is configured, use it for sidebar calculation base?
        // Actually, sidebar controls take space.
        
        int defaultVisible = 5;
        int preferredByItems = config.getTaskItemHeight() * defaultVisible;
        int desiredHeight = (configuredListHeight > 0 ? configuredListHeight : preferredByItems) + 24;
        int listHeight = Math.min(desiredHeight, maxListHeight);
        
        // Sidebar List Height
        // If we move sidebar up, we have more vertical space.
        // Let's calculate max height for sidebar independently?
        // Or keep it tied to main list but extended?
        // User didn't specify height change, just "move up".
        // But if we move up, and keep height, it will end earlier.
        // Let's assume we want to extend it down to match bottom alignment or just fixed height?
        // Let's just use `listHeight` + difference in top position?
        // difference = listTop - sidebarTopY = (y + headerOffset) - (y + 10) = headerOffset - 10.
        // So new height = listHeight + (headerOffset - 10).
        
        int heightDiff = listTop - sidebarTopY;
        int sidebarListHeight = listHeight + heightDiff - sidebarHeaderHeight;
        
        if (config.getProjectSidebarHeight() > 0) {
             sidebarListHeight = config.getProjectSidebarHeight() - sidebarHeaderHeight;
        }
        if (sidebarListHeight < 20) sidebarListHeight = 20;

        // Scope Toggle
        projectScopeButton = ButtonWidget.builder(getProjectScopeText(), b -> {
            if (!teamProjectsEnabled) return;
            projectScopeFilter = (projectScopeFilter == Project.Scope.PERSONAL) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
            List<Project> projects = projectManager.getProjectsByScope(projectScopeFilter);
            if (!projects.isEmpty()) {
                switchProject(projects.get(0));
            } else {
                switchProject(null);
            }
        }).dimensions(x + padding, sidebarTopY, sidebarWidth, sidebarScopeBtnHeight).build();
        projectScopeButton.active = teamProjectsEnabled;
        this.addDrawableChild(projectScopeButton);
        
        // Project Search
        // User said: "Project List and Add/Edit buttons spacing adjusted to 10px"
        // Wait: "2. Sidebar project part, [Team Project] button and search box, search box and project list, project list and add edit button spacing adjusted to 10px;"
        // So gaps should be 10.
        int gap10 = 10;
        
        projectSearchField = new TextFieldWidget(this.textRenderer, x + padding, sidebarTopY + sidebarScopeBtnHeight + gap10, sidebarWidth, sidebarSearchHeight, Text.translatable("gui.todolist.project.search"));
        projectSearchField.setPlaceholder(Text.translatable("gui.todolist.project.search"));
        projectSearchField.setText(projectSearchQuery);
        projectSearchField.setChangedListener(text -> {
            projectSearchQuery = text;
            updateProjectList();
        });
        this.addDrawableChild(projectSearchField);

        // Project List
        projListY = sidebarTopY + sidebarScopeBtnHeight + gap10 + sidebarSearchHeight + gap10;
        // Recalculate height based on new Y
        // Bottom of list should be same as before? Or extended?
        // Let's keep bottom aligned with task list bottom?
        // Task List Bottom = listTop + listHeight
        // Sidebar Bottom = projListY + sidebarListHeight
        // We want Sidebar Bottom = Task List Bottom
        // sidebarListHeight = (listTop + listHeight) - projListY
        
        int listBottom = listTop + listHeight;
        sidebarListHeight = listBottom - projListY;
        
        if (config.getProjectSidebarHeight() > 0) {
             sidebarListHeight = config.getProjectSidebarHeight() - (sidebarScopeBtnHeight + gap10 + sidebarSearchHeight + gap10);
        }
        if (sidebarListHeight < 20) sidebarListHeight = 20;
        
        projectListWidget = new ProjectListWidget(this.client, x + padding, projListY, sidebarWidth, sidebarListHeight);
        updateProjectList(); 
        projectListWidget.setSelectedProject(currentProject);
        projectListWidget.setOnProjectSelected(this::switchProject);
        this.addDrawableChild(projectListWidget);
        
        int projBtnY = projListY + sidebarListHeight + gap10;
        int projBtnGap = 5;

        ButtonWidget addProjectBtn = ButtonWidget.builder(Text.translatable("gui.todolist.add"), b -> onAddProject())
                .dimensions(x + padding, projBtnY, sidebarWidth, 20).build();
        this.addDrawableChild(addProjectBtn);

        editProjectBtn = ButtonWidget.builder(Text.translatable("gui.todolist.edit"), b -> onProjectSettings())
                .dimensions(x + padding, projBtnY + 20 + projBtnGap, sidebarWidth, 20).build();
        editProjectBtn.active = currentProject != null;
        this.addDrawableChild(editProjectBtn);

        deleteProjectBtn = ButtonWidget.builder(Text.translatable("gui.todolist.delete"), b -> onProjectDelete())
                .dimensions(x + padding, projBtnY + (20 + projBtnGap) * 2, sidebarWidth, 20).build();
        this.addDrawableChild(deleteProjectBtn);

        applyJoinProjectBtn = ButtonWidget.builder(Text.translatable("gui.todolist.project.join.apply"), b -> onApplyJoinProject())
                .dimensions(x + padding, projBtnY + (20 + projBtnGap) * 2, sidebarWidth, 20).build();
        this.addDrawableChild(applyJoinProjectBtn);

        updateProjectActionButtons();

        // 2. Task List
        taskListWidget = new TaskListWidget(this.client, contentX, listTop, contentWidth, listHeight);
        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        taskListWidget.setTeamAllViewForNonOp(getCurrentRole() == Role.MEMBER && teamAllView);
        taskListWidget.setTasks(filteredTasks);
        taskListWidget.setOnTaskToggleCompletion(task -> {
            if (task.isCompleted()) return;
            boolean wasCompleted = task.isCompleted();
            toggleTaskCompletion(task);
            if (!wasCompleted && task.isCompleted()) {
                addNotification(Text.translatable("message.todolist.completed", task.getTitle()).getString());
                if (config.isEnableSoundEffects() && this.client != null && this.client.player != null) {
                    this.client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.7F, 1.0F);
                }
            }
            refreshTaskList();
        });

        // 3. Input Fields
        int currentY = y + headerOffset + listHeight + e;
        int labelWidth = 40;
        int fieldX = contentX + labelWidth;
        int fieldWidth = contentWidth - labelWidth;

        titleField = new TextFieldWidget(this.textRenderer, fieldX, currentY, fieldWidth, 20, Text.empty());
        titleField.setText("");
        titleField.setMaxLength(100);
        this.addDrawableChild(titleField);
        currentY += 24;

        descField = new TextFieldWidget(this.textRenderer, fieldX, currentY, fieldWidth, 20, Text.empty());
        descField.setText("");
        descField.setMaxLength(255);
        this.addDrawableChild(descField);
        currentY += 24;

        tagField = new TextFieldWidget(this.textRenderer, fieldX, currentY, fieldWidth, 20, Text.empty());
        tagField.setText("");
        tagField.setMaxLength(100);
        this.addDrawableChild(tagField);
        currentY += 24;

        // 4. Priority Buttons
        int priorityButtonWidth = 50;
        int priorityStartX = fieldX;
        priorityButtons = new ButtonWidget[3];
        for (int i = 0; i < 3; i++) {
            Task.Priority priority = Task.Priority.values()[2 - i];
            String base;
            switch (priority) {
                case HIGH: base = Text.translatable("gui.todolist.priority.high").getString(); break;
                case MEDIUM: base = Text.translatable("gui.todolist.priority.medium").getString(); break;
                case LOW: default: base = Text.translatable("gui.todolist.priority.low").getString(); break;
            }
            String buttonText = (priority == Task.Priority.HIGH ? "§c[" : priority == Task.Priority.MEDIUM ? "§e[" : "§a[") + base + "]";
            int index = i;
            priorityButtons[i] = ButtonWidget.builder(Text.of(buttonText), button -> {
                setSelectedPriority(priority);
                Task taskToUpdate = selectedTask;
                if (taskToUpdate != null) {
                    taskToUpdate.setPriority(priority);
                }
                refreshTaskList();
                if (taskToUpdate != null) {
                    ClientTaskPackets.sendUpdateTask(taskToUpdate);
                }
                updatePrioritySelection();
                markUnsaved();
            }).dimensions(priorityStartX + index * (priorityButtonWidth + 4), currentY, priorityButtonWidth, 20).build();
            this.addDrawableChild(priorityButtons[i]);
        }

        // 5. Action Buttons (Add/Delete)
        int actionButtonWidth = 60;
        int actionButtonGap = 5;
        int actionButtonsCount = 2;
        int actionButtonsWidth = actionButtonWidth * actionButtonsCount + actionButtonGap * (actionButtonsCount - 1);
        int actionButtonX = x + guiWidth - padding - actionButtonsWidth;

        addButton = ButtonWidget.builder(Text.translatable("gui.todolist.add"), button -> onAddTask())
                .dimensions(actionButtonX, currentY, actionButtonWidth, 20).build();
        this.addDrawableChild(addButton);

        deleteButton = ButtonWidget.builder(Text.translatable("gui.todolist.delete"), button -> onDeleteTask())
                .dimensions(actionButtonX + (actionButtonWidth + actionButtonGap), currentY, actionButtonWidth, 20).build();
        deleteButton.active = false;
        this.addDrawableChild(deleteButton);

        currentY += 24 + 25;

        // 6. Save/Cancel
        int saveCancelWidth = 90;
        int saveCancelGap = 5;
        int totalSaveCancelWidth = saveCancelWidth * 2 + saveCancelGap;
        int saveCancelX = x + (guiWidth - totalSaveCancelWidth) / 2;

        ButtonWidget saveButton = ButtonWidget.builder(Text.translatable("gui.todolist.save"), button -> onSaveTasks())
                .dimensions(saveCancelX, currentY, saveCancelWidth, 20).build();
        this.addDrawableChild(saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), button -> onCancel())
                .dimensions(saveCancelX + saveCancelWidth + saveCancelGap, currentY, saveCancelWidth, 20).build();
        this.addDrawableChild(cancelButton);

        // 7. Filter Row (View/Priority/Status/Config)
        int filterGap = 4;
        int filterLabelW = 40;
        int filtersX = contentX + filterLabelW;
        int filtersY = topRowY;
        int btnH = 20;

        this.addDrawableChild(new TextLabelWidget(contentX, filtersY + (btnH - 8) / 2, Text.translatable("gui.todolist.label.filter"), 0xFFFFFF));

        int configBtnW = 50;
        int configBtnX = x + guiWidth - padding - configBtnW;
        configButton = ButtonWidget.builder(Text.translatable("gui.todolist.config.title"), b -> this.client.setScreen(new ConfigScreen(this)))
                .dimensions(configBtnX, filtersY, configBtnW, btnH).build();
        this.addDrawableChild(configButton);

        int availableBeforeConfig = configBtnX - filtersX;
        int minBtnW = 70;
        int maxBtnW = 140;
        int viewBtnWidth = Math.min(maxBtnW, Math.max(minBtnW, this.textRenderer.getWidth(getViewToggleText()) + 16));
        int priorityBtnWidth = Math.min(120, Math.max(minBtnW, this.textRenderer.getWidth(getPriorityFilterText()) + 16));
        int statusBtnWidth = Math.min(120, Math.max(minBtnW, this.textRenderer.getWidth(getStatusFilterText()) + 16));
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

        viewToggleButton = ButtonWidget.builder(getViewToggleText(), b -> {
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
        }).dimensions(filtersX, filtersY, viewBtnWidth, btnH).build();
        viewToggleButton.active = viewMode != ViewMode.PERSONAL;
        this.addDrawableChild(viewToggleButton);

        int priorityBtnX = filtersX + viewBtnWidth + filterGap;
        filterPriorityButton = ButtonWidget.builder(getPriorityFilterText(), button -> {
            currentPriorityFilter = (currentPriorityFilter + 1) % 4;
            button.setMessage(getPriorityFilterText());
            applyPriorityFilter();
        }).dimensions(priorityBtnX, filtersY, priorityBtnWidth, btnH).build();
        this.addDrawableChild(filterPriorityButton);

        int statusBtnX = priorityBtnX + priorityBtnWidth + filterGap;
        filterStatusButton = ButtonWidget.builder(getStatusFilterText(), button -> {
            filterTasks("completed".equals(currentFilter) ? "active" : "completed");
        }).dimensions(statusBtnX, filtersY, statusBtnWidth, btnH).build();
        this.addDrawableChild(filterStatusButton);
        
        // 8. Assign Buttons (Claim/Abandon/Assign Others) - Right side of task list, aligned with top
        int assignButtonWidth = 80;
        int assignButtonHeight = 20;
        int assignButtonGap = 4;
        int assignsX = contentX + contentWidth + 8;
        int assignsY = listTop;
        
        claimButton = ButtonWidget.builder(Text.translatable("gui.todolist.claim_task"), b -> onClaimTask())
                .dimensions(assignsX, assignsY, assignButtonWidth, assignButtonHeight).build();
        claimButton.active = false;
        this.addDrawableChild(claimButton);

        abandonButton = ButtonWidget.builder(Text.translatable("gui.todolist.abandon_task"), b -> onAbandonTask())
                .dimensions(assignsX, assignsY + (assignButtonHeight + assignButtonGap), assignButtonWidth, assignButtonHeight).build();
        abandonButton.active = false;
        this.addDrawableChild(abandonButton);

        assignOthersButton = ButtonWidget.builder(Text.translatable("gui.todolist.assign_others"), b -> onAssignOthers())
                .dimensions(assignsX, assignsY + (assignButtonHeight + assignButtonGap) * 2, assignButtonWidth, assignButtonHeight).build();
        assignOthersButton.active = false;
        this.addDrawableChild(assignOthersButton);

        // 9. Search Field
        int searchY = secondRowY;
        
        // Search Label & Field
        int searchLabelWidth = 40; // Same as "Title:", "Desc:" etc.
        int searchFieldX = contentX + searchLabelWidth;
        int searchFieldWidth = contentWidth - searchLabelWidth;
        
        // We need to render the label in render(), so just add field here.
        // Wait, "Title:", "Desc:" are rendered in render(). "Search:" was too.
        // But Search field was added with hardcoded X.
        // We need to match the layout of input fields below.
        
        searchField = new TextFieldWidget(this.textRenderer, searchFieldX, searchY, searchFieldWidth, 20, Text.empty());
        searchField.setText(searchQuery);
        this.addDrawableChild(searchField);

        // Listeners
        titleField.setChangedListener(text -> {
            if (selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted()) {
                selectedTask.setTitle(text);
                markUnsaved();
            }
        });
        descField.setChangedListener(text -> {
            if (selectedTask != null && isSelectedTaskValid() && !selectedTask.isCompleted()) {
                selectedTask.setDescription(text);
                markUnsaved();
            }
        });
        tagField.setChangedListener(text -> {
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
        searchField.setChangedListener(text -> {
            searchQuery = text == null ? "" : text.trim().toLowerCase();
            applySearchFilter();
        });

        filterTasks(currentFilter);
        this.setFocused(titleField);
        updateButtonStates();
    }

    private void toggleTaskCompletion(Task task) {
        if (!canToggleCompletion(task)) {
            addNotification(Text.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        taskManager.toggleTaskCompletion(task.getId());
        markUnsaved();
        refreshTaskList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());

        Text title = hasUnsavedChanges ? Text.translatable("gui.todolist.title.unsaved") : TITLE;
        context.drawText(this.textRenderer, title, (this.width - this.textRenderer.getWidth(title)) / 2, 10, 0xFFFFFFFF, false);

        if (taskListWidget != null) taskListWidget.render(context, mouseX, mouseY, delta);
        if (projectListWidget != null) projectListWidget.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        ModConfig config = ModConfig.getInstance();
        int guiWidth = config.getGuiWidth();
        int guiHeight = config.getGuiHeight();
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        int padding = config.getPadding();
        int labelX = x + padding + 100 + 8; // Adjust for sidebar
        int color = 0xFFFFFFFF;
        int textH = this.textRenderer.fontHeight;

        if (titleField != null) {
            int ty = titleField.getY() + (titleField.getHeight() - textH) / 2;
            context.drawText(this.textRenderer, Text.translatable("gui.todolist.label.title"), labelX, ty, color, false);
        }
        if (descField != null) {
            int dy = descField.getY() + (descField.getHeight() - textH) / 2;
            context.drawText(this.textRenderer, Text.translatable("gui.todolist.label.description"), labelX, dy, color, false);
        }
        if (tagField != null) {
            int zy = tagField.getY() + (tagField.getHeight() - textH) / 2;
            context.drawText(this.textRenderer, Text.translatable("gui.todolist.label.tags"), labelX, zy, color, false);
        }
        if (searchField != null) {
            int sy = searchField.getY() + (searchField.getHeight() - textH) / 2;
            int searchLabelX = searchField.getX() - 40; // Relative to field
            context.drawText(this.textRenderer, Text.translatable("gui.todolist.label.search"), searchLabelX, sy, color, false);
        }
        if (priorityButtons != null && priorityButtons.length > 0 && priorityButtons[0] != null) {
            int py = priorityButtons[0].getY() + (priorityButtons[0].getHeight() - textH) / 2;
            context.drawText(this.textRenderer, Text.translatable("gui.todolist.label.priority"), labelX, py, color, false);
        }

        renderNotifications(context);
    }

    private void renderNotifications(DrawContext context) {
        if (notifications.isEmpty()) return;
        long now = System.currentTimeMillis();

        ModConfig config = ModConfig.getInstance();
        int guiWidth = config.getGuiWidth();
        int guiHeight = config.getGuiHeight();
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        int padding = config.getPadding();

        int boxWidth = 220;
        int boxHeight = 20;
        int startX = x + guiWidth - padding - boxWidth;
        int startY = (searchField != null) ? searchField.getY() : (y + 35);
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
            context.drawBorder(bx1, by1, boxWidth, boxHeight, 0xFFFFFFFF);
            int tx = bx1 + 6;
            int ty = by1 + (boxHeight - this.textRenderer.fontHeight) / 2;
            context.drawText(this.textRenderer, Text.of(n.text), tx, ty, 0xFFFFFF00, false);
            dy += boxHeight + gap;
        }
    }

    private void onSaveTasks() {
        try {
            if (viewMode == ViewMode.PERSONAL) {
                TodoListMod.getTaskStorage().saveTasks(taskManager.getAllTasks());
                TodoListMod.LOGGER.info("Tasks saved");
                ClientTaskPackets.sendReplaceAllTasks(taskManager.getAllTasks());
                TodoHudRenderer renderer = TodoClient.getHudRenderer();
                if (renderer != null) {
                    renderer.forceRefreshTasks();
                }
            } else {
                ClientTaskPackets.sendReplaceTeamTasks(taskManager.getAllTasks());
                TodoListMod.LOGGER.info("Team tasks saved");
            }
            hasUnsavedChanges = false;
            if (viewMode == ViewMode.PERSONAL) {
                personalHasUnsavedChanges = false;
            } else {
                teamHasUnsavedChanges = false;
            }
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("message.todolist.saved"), false);
            }
        } catch (Exception e) {
            TodoListMod.LOGGER.error("Failed to save tasks", e);
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("message.todolist.save_failed"), false);
            }
        }
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (titleField != null && titleField.isFocused()) {
                if (!isAddTaskAllowedInCurrentView()) {
                    addNotification(Text.translatable("message.todolist.add_not_allowed_in_view").getString());
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
        if (priorityButtons != null) {
            for (ButtonWidget b : priorityButtons) {
                if (b != null && b.isMouseOver(mouseX, mouseY)) return true;
            }
        }
        if (addButton != null && addButton.isMouseOver(mouseX, mouseY)) return true;
        if (deleteButton != null && deleteButton.isMouseOver(mouseX, mouseY)) return true;
        if (claimButton != null && claimButton.isMouseOver(mouseX, mouseY)) return true;
        if (abandonButton != null && abandonButton.isMouseOver(mouseX, mouseY)) return true;
        if (assignOthersButton != null && assignOthersButton.isMouseOver(mouseX, mouseY)) return true;
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (taskListWidget != null && taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (projectListWidget != null && projectListWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Handle task list clicks
        if (taskListWidget != null) {
            Task clickedTask = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
            if (clickedTask != null) {
                selectTask(clickedTask);
                return true;
            }
        }

        boolean cleared = false;
        if (button == 0 && selectedTask != null && !isClickInEditArea(mouseX, mouseY)) {
            clearSelectedTask();
            cleared = true;
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
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (taskListWidget != null) taskListWidget.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    @Override
    public void close() {
        if (viewMode != ViewMode.PERSONAL && teamHasUnsavedChanges) {
            ClientTaskPackets.requestTeamSync();
            hasUnsavedChanges = false;
            teamHasUnsavedChanges = false;
        }
        this.client.setScreen(parent);
    }

    private void onCancel() {
        close();
    }

    // Event handlers

    private void onAddTask() {
        if (!isAddTaskAllowedInCurrentView()) {
            addNotification(Text.translatable("message.todolist.add_not_allowed_in_view").getString());
            return;
        }
        if (viewMode != ViewMode.PERSONAL) {
            Role role = getCurrentRole();
            ViewScope scope = getCurrentViewScope();
            boolean projectMember = isCurrentPlayerProjectMember();
            if (!PermissionCenter.canPerform(Operation.ADD_TASK, role, new Context(scope, false, false, false, false, false, projectMember))) {
                addNotification(Text.translatable("message.todolist.no_permission_add_team").getString());
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
                if (this.client != null && this.client.player != null) {
                    String uuid = this.client.player.getUuid().toString();
                    String name = this.client.player.getName().getString();
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

            titleField.setText("");
            descField.setText("");
            tagField.setText("");
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
            updateButtonStates();
            markUnsaved();
            refreshTaskList();
        }
    }

    private void selectTask(Task task) {
        selectedTask = task;
        selectedPriority = task.getPriority();
        titleField.setText(task.getTitle());
        descField.setText(task.getDescription());

        // Display tags as comma-separated string
        if (task.getTags() != null && !task.getTags().isEmpty()) {
            String tagsStr = String.join(",", task.getTags());
            tagField.setText(tagsStr);
        } else {
            tagField.setText("");
        }

        taskListWidget.setSelectedTask(task);
        updateButtonStates();
        boolean editable = canEditTask(task);
        titleField.setEditable(editable);
        descField.setEditable(editable);
        tagField.setEditable(editable);
    }

    private void clearSelectedTask() {
        selectedTask = null;
        if (taskListWidget != null) {
            taskListWidget.clearSelection();
        }
        if (titleField != null) {
            titleField.setText("");
            titleField.setEditable(true);
        }
        if (descField != null) {
            descField.setText("");
            descField.setEditable(true);
        }
        if (tagField != null) {
            tagField.setText("");
            tagField.setEditable(true);
        }
        updateButtonStates();
    }

    private boolean isSelectedTaskValid() {
        if (selectedTask == null) {
            return false;
        }
        if (currentProject == null) {
            return true;
        }
        String selectedId = selectedTask.getId();
        if (selectedId == null || selectedId.isEmpty()) {
            return false;
        }
        boolean inProject = false;
        String projectId = currentProject.getId();
        String taskProjectId = selectedTask.getProjectId();
        if (projectId != null && projectId.equals(taskProjectId)) {
            inProject = true;
        } else if (taskProjectId == null && currentProject.getScope() == Project.Scope.PERSONAL && isDefaultProject(currentProject)) {
            inProject = true;
        }
        if (!inProject) {
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
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember);
        boolean canEdit = hasSelection && PermissionCenter.canPerform(Operation.EDIT_TASK, role, context);
        boolean priorityEnabled;
        if (!hasSelection) {
            priorityEnabled = addButton != null && addButton.active;
        } else {
            priorityEnabled = canEdit && !isCompleted;
        }
        deleteButton.active = hasSelection && PermissionCenter.canPerform(Operation.DELETE_TASK, role, context);
        if (priorityButtons != null) {
            for (ButtonWidget button : priorityButtons) {
                if (button != null) {
                    button.active = priorityEnabled;
                }
            }
        }
        if (addButton != null) {
            if (hasSelection) {
                addButton.active = false;
            } else if (viewMode == ViewMode.PERSONAL) {
                addButton.active = true;
            } else {
                if (!isAddTaskAllowedInCurrentView()) {
                    addButton.active = false;
                } else {
                    boolean canAdd = PermissionCenter.canPerform(Operation.ADD_TASK, role, new Context(scope, false, false, false, false, false, projectMember));
                    addButton.active = canAdd;
                }
            }
        }
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
    }

    private void setSelectedPriority(Task.Priority priority) {
        this.selectedPriority = priority;
    }

    private boolean isAdminClient() {
        return this.client != null && this.client.player != null && this.client.player.hasPermissionLevel(2);
    }

    private void markUnsaved() {
        hasUnsavedChanges = true;
        if (viewMode == ViewMode.PERSONAL) {
            personalHasUnsavedChanges = true;
        } else {
            teamHasUnsavedChanges = true;
        }
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
        if (task == null || this.client == null || this.client.player == null) {
            return false;
        }
        String uuid = this.client.player.getUuid().toString();
        String assignee = task.getAssigneeUuid();
        return assignee != null && assignee.equals(uuid);
    }

    private Role getCurrentRole() {
        if (isAdminClient()) {
            return Role.OP;
        }
        if (this.client == null || this.client.player == null) {
            return Role.MEMBER;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return Role.MEMBER;
        }
        String uuid = this.client.player.getUuid().toString();
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
        if (this.client == null || this.client.player == null) {
            return false;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return true;
        }
        String uuid = this.client.player.getUuid().toString();
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
        if (selectedTask == null || this.client == null || this.client.player == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Text.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        String uuid = this.client.player.getUuid().toString();
        String assignee = selectedTask.getAssigneeUuid();
        if (assignee != null && !assignee.isEmpty() && !assignee.equals(uuid)) {
            addNotification(Text.translatable("message.todolist.already_assigned").getString());
            return;
        }
        selectedTask.setAssigneeUuid(uuid);
        selectedTask.setAssigneeName(this.client.player.getName().getString());
        addNotification(Text.translatable("message.todolist.assigned_to_me").getString());
        markUnsaved();
        refreshTaskList();
    }

    // Helper for rendering labels
    private class TextLabelWidget extends net.minecraft.client.gui.widget.ClickableWidget {
        private final Text text;
        private final int color;
        
        public TextLabelWidget(int x, int y, Text text, int color) {
            super(x, y, client.textRenderer.getWidth(text), client.textRenderer.fontHeight, text);
            this.text = text;
            this.color = color;
            this.active = false; // Not clickable
        }

        @Override
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            context.drawText(client.textRenderer, text, getX(), getY(), color, false);
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        }
    }
    
    private void updateProjectList() {
        if (projectListWidget == null) return;
        List<Project> all = new ArrayList<>();
        all.addAll(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
        all.addAll(projectManager.getProjectsByScope(Project.Scope.TEAM));
        
        List<Project> filtered = new ArrayList<>();
        String q = projectSearchQuery.toLowerCase().trim();
        
        for (Project p : all) {
            // Scope filter
            if (p.getScope() != projectScopeFilter) continue;
            
            // Name filter
            if (!q.isEmpty() && !p.getName().toLowerCase().contains(q)) continue;
            
            filtered.add(p);
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
            editProjectBtn.setMessage(Text.translatable("gui.todolist.edit"));
            deleteProjectBtn.visible = true;
            deleteProjectBtn.active = false;
            applyJoinProjectBtn.visible = false;
            applyJoinProjectBtn.active = false;
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            editProjectBtn.active = true;
            editProjectBtn.setMessage(Text.translatable("gui.todolist.edit"));
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
        editProjectBtn.setMessage(Text.translatable(canEdit ? "gui.todolist.edit" : "gui.todolist.project.view"));
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
    
    private Text getProjectScopeText() {
        if (projectScopeFilter == Project.Scope.PERSONAL) {
            return Text.translatable("gui.todolist.project.toggle.personal");
        } else {
            return Text.translatable("gui.todolist.project.toggle.team");
        }
    }
    private void onAbandonTask() {
        if (selectedTask == null || this.client == null || this.client.player == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Text.translatable("message.todolist.assign_only_team").getString());
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
            addNotification(Text.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        selectedTask.setAssigneeUuid(null);
        selectedTask.setAssigneeName(null);
        addNotification(Text.translatable("message.todolist.abandoned_task").getString());
        markUnsaved();
        refreshTaskList();
    }

    private Text getPriorityFilterText() {
        String labelKey = "gui.todolist.label.priority";
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
        return Text.translatable(labelKey).append(Text.translatable(valueKey));
    }

    private Text getStatusFilterText() {
        MutableText label = Text.translatable("gui.todolist.label.status");
        Text value = "completed".equals(currentFilter) ? Text.translatable("gui.todolist.completed") : Text.translatable("gui.todolist.active");
        return label.append(value);
    }

    private Text getViewToggleText() {
        MutableText label = Text.translatable("gui.todolist.label.view");
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
        return label.append(Text.translatable(key));
    }

    private void applyPriorityFilter() {
        filterTasks(currentFilter);
    }

    private void onAssignOthers() {
        if (selectedTask == null || this.client == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Text.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        if (!canEditTask(selectedTask)) {
            addNotification(Text.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        this.client.setScreen(new AssignPlayerScreen(this, selectedTask));
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

    private String getFieldValue(TextFieldWidget field, String hint) {
        String raw = field.getText() == null ? "" : field.getText().trim();
        if (raw.isEmpty()) return "";
        if (!hint.isEmpty() && raw.equals(hint)) return "";
        return raw;
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
    
    // NEW METHODS
    
    private List<Task> applyAssignedFilterIfNeeded(List<Task> tasks) {
        // First, filter by Project ID
        List<Task> projectFiltered = new ArrayList<>();
        if (currentProject != null) {
            for (Task t : tasks) {
                // For Personal Scope, check if task belongs to this project
                // For Team Scope, check if task belongs to this project
                // Assuming Task has getProjectId().
                // If legacy task has no project ID, what to do?
                // If Task doesn't have projectId field yet, we need to add it.
                // Assuming it was added in previous steps.
                if (currentProject.getId().equals(t.getProjectId())) {
                    projectFiltered.add(t);
                } else if (t.getProjectId() == null && currentProject.getScope() == Project.Scope.PERSONAL && isDefaultProject(currentProject)) {
                     // Legacy tasks -> Default Project
                     projectFiltered.add(t);
                }
            }
        } else {
            projectFiltered = tasks;
        }
        
        List<Task> result = new ArrayList<>();
        if (viewMode == ViewMode.TEAM_ASSIGNED) {
            if (this.client != null && this.client.player != null) {
                String myUuid = this.client.player.getUuid().toString();
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
            // "Team · Assigned" (All assigned tasks)
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
    
    private boolean isDefaultProject(Project p) {
        // Only the project with the specific translation key is default
        return "gui.todolist.project.default.personal".equals(p.getName());
    }
    
    private boolean isTrueSingleplayer() {
        return false;
    }
    
    private void switchView(ViewMode mode) {
        this.viewMode = mode;
        this.selectedTask = null;
        updateViewButtonsState();
        rebuildUI();
    }
    
    private void updateViewButtonsState() {
        if (viewToggleButton != null) {
            viewToggleButton.active = viewMode != ViewMode.PERSONAL;
            viewToggleButton.setMessage(getViewToggleText());
        }
    }
    
    private void switchProject(Project project) {
        this.selectedTask = null;
        this.currentProject = project;

        if (project == null) {
            this.taskManager = this.personalTaskManager;
            this.viewMode = ViewMode.PERSONAL;
            TodoClient.setActiveProjectId(null);
            rebuildUI();
            return;
        }

        if (!teamProjectsEnabled && project.getScope() == Project.Scope.TEAM) {
            addNotification(Text.translatable("message.todolist.team_disabled").getString());
            return;
        }
        TodoClient.setActiveProjectId(project.getId());
        
        if (project.getScope() == Project.Scope.PERSONAL) {
            this.taskManager = this.personalTaskManager;
            this.viewMode = ViewMode.PERSONAL;
        } else {
            this.taskManager = this.teamTaskManager;
            if (this.viewMode == ViewMode.PERSONAL) {
                this.viewMode = ViewMode.TEAM_UNASSIGNED;
            }
        }
        
        rebuildUI();
    }
    
    private void onAddProject() {
        Project.Scope defaultScope = Project.Scope.PERSONAL;
        if (currentProject != null) {
            defaultScope = currentProject.getScope();
        } else if (projectScopeFilter != null) {
            defaultScope = projectScopeFilter;
        }
        if (defaultScope == Project.Scope.TEAM && !TodoClient.isTeamProjectsEnabled()) {
            defaultScope = Project.Scope.PERSONAL;
        }
        client.setScreen(new AddProjectScreen(this, defaultScope));
    }
    
    private void onProjectSettings() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Text.translatable("message.todolist.team_disabled").getString());
            return;
        }
        client.setScreen(new ProjectSettingsScreen(this, currentProject));
    }

    private boolean canDeleteCurrentProject() {
        if (currentProject == null || this.client == null || this.client.player == null) {
            return false;
        }
        String uuid = this.client.player.getUuid().toString();
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
            addNotification(Text.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            return;
        }
        if (isCurrentPlayerProjectMember()) {
            return;
        }
        ClientProjectPackets.sendRequestJoinProject(currentProject.getId());
        addNotification(Text.translatable("message.todolist.project.join.sent").getString());
    }

    private void onProjectDelete() {
        if (currentProject == null) return;
        if (!teamProjectsEnabled && currentProject.getScope() == Project.Scope.TEAM) {
            addNotification(Text.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (!canDeleteCurrentProject()) {
            addNotification(Text.translatable("message.todolist.no_permission_delete_project").getString());
            return;
        }
        String projectName = Text.translatable(currentProject.getName()).getString();
        Text message = Text.translatable("gui.todolist.project.delete_confirm.message", projectName);
        client.setScreen(new ConfirmDeleteProjectScreen(this, message, () -> {
            ClientProjectPackets.sendDeleteProject(currentProject.getId());
        }));
    }

    private class AssignPlayerScreen extends Screen {
        private final TodoScreen parentScreen;
        private final Task targetTask;
        private TextFieldWidget searchField;
        private java.util.List<net.minecraft.client.network.PlayerListEntry> allPlayers;
        private java.util.List<net.minecraft.client.network.PlayerListEntry> filteredPlayers;
        private ButtonWidget[] playerButtons;
        private int scrollOffset;
        private int visibleRows;
        private int listX;
        private int listY;
        private int listWidth;
        private int listHeight;
        private int rowHeight;

        protected AssignPlayerScreen(TodoScreen parentScreen, Task targetTask) {
            super(Text.translatable("gui.todolist.assign_others"));
            this.parentScreen = parentScreen;
            this.targetTask = targetTask;
        }

        @Override
        protected void init() {
            super.init();
            if (client == null || client.getNetworkHandler() == null) {
                return;
            }
            int guiWidth = 200;
            int x = (this.width - guiWidth) / 2;
            int topY = this.height / 6;
            int searchHeight = 20;
            rowHeight = 22;
            visibleRows = 8;
            listWidth = guiWidth;
            listX = x;
            listY = topY + searchHeight + 6;
            listHeight = visibleRows * rowHeight;

            searchField = new TextFieldWidget(this.textRenderer, x, topY, guiWidth, searchHeight, Text.empty());
            searchField.setText("");
            this.addDrawableChild(searchField);

            allPlayers = new java.util.ArrayList<>();
            filteredPlayers = new java.util.ArrayList<>();
            java.util.Collection<net.minecraft.client.network.PlayerListEntry> entries = client.getNetworkHandler().getPlayerList();
            allPlayers.addAll(entries);

            playerButtons = new ButtonWidget[visibleRows];
            for (int i = 0; i < visibleRows; i++) {
                int btnY = listY + i * rowHeight;
                final int rowIndex = i;
                ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
                    net.minecraft.client.network.PlayerListEntry entry = getPlayerForRow(rowIndex);
                    if (entry != null) {
                        String name = entry.getProfile().getName();
                        java.util.UUID uuid = entry.getProfile().getId();
                        applyAssignTo(uuid.toString(), name);
                    }
                }).dimensions(x, btnY, guiWidth, 20).build();
                btn.active = false;
                btn.visible = false;
                this.addDrawableChild(btn);
                playerButtons[i] = btn;
            }

            int cancelY = listY + listHeight + 10;
            ButtonWidget cancel = ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), b -> {
                client.setScreen(parentScreen);
            }).dimensions(x, cancelY, guiWidth, 20).build();
            this.addDrawableChild(cancel);

            searchField.setChangedListener(text -> {
                updateFilteredPlayers();
            });
            updateFilteredPlayers();
            this.setFocused(searchField);
        }

        private net.minecraft.client.network.PlayerListEntry getPlayerForRow(int rowIndex) {
            if (filteredPlayers == null || filteredPlayers.isEmpty()) {
                return null;
            }
            int index = scrollOffset + rowIndex;
            if (index < 0 || index >= filteredPlayers.size()) {
                return null;
            }
            return filteredPlayers.get(index);
        }

        private void updateFilteredPlayers() {
            if (allPlayers == null) {
                return;
            }
            filteredPlayers.clear();
            String query = searchField == null ? "" : searchField.getText();
            if (query == null) {
                query = "";
            }
            String q = query.trim().toLowerCase();
            for (net.minecraft.client.network.PlayerListEntry entry : allPlayers) {
                String name = entry.getProfile().getName();
                if (name == null) {
                    continue;
                }
                if (q.isEmpty() || name.toLowerCase().contains(q)) {
                    filteredPlayers.add(entry);
                }
            }
            scrollOffset = 0;
            updatePlayerButtons();
        }

        private void updatePlayerButtons() {
            if (playerButtons == null) {
                return;
            }
            int maxOffset = 0;
            if (filteredPlayers != null) {
                maxOffset = Math.max(0, filteredPlayers.size() - visibleRows);
            }
            if (scrollOffset > maxOffset) {
                scrollOffset = maxOffset;
            }
            if (scrollOffset < 0) {
                scrollOffset = 0;
            }
            for (int i = 0; i < playerButtons.length; i++) {
                ButtonWidget btn = playerButtons[i];
                net.minecraft.client.network.PlayerListEntry entry = getPlayerForRow(i);
                if (entry == null) {
                    btn.visible = false;
                    btn.active = false;
                    btn.setMessage(Text.empty());
                } else {
                    String name = entry.getProfile().getName();
                    btn.visible = true;
                    btn.active = true;
                    btn.setMessage(Text.of(name));
                }
            }
        }

        private void applyAssignTo(String uuid, String name) {
            targetTask.setAssigneeUuid(uuid);
            targetTask.setAssigneeName(name);
            parentScreen.addNotification(Text.translatable("message.todolist.assigned_to_player", name).getString());
            parentScreen.markUnsaved();
            parentScreen.refreshTaskList();
            client.setScreen(parentScreen);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                if (filteredPlayers != null && !filteredPlayers.isEmpty()) {
                    int maxOffset = Math.max(0, filteredPlayers.size() - visibleRows);
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
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
