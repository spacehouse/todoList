package com.todolist.gui;

import com.todolist.TodoListMod;
import com.todolist.client.TodoClient;
import com.todolist.client.TodoHudRenderer;
import com.todolist.config.ModConfig;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import com.todolist.client.ClientTaskPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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
 */
public class TodoScreen extends Screen {
    private static final Text TITLE = Text.translatable("gui.todolist.title");
    private static final int BG_COLOR = 0xFF000000;

    private final Screen parent;
    private TaskManager taskManager;
    private TaskManager personalTaskManager;
    private TaskManager teamTaskManager;
    private TaskListWidget taskListWidget;
    private final List<Notification> notifications = new ArrayList<>();

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
    private ButtonWidget filterActiveButton;
    private ButtonWidget filterCompletedButton;
    private ButtonWidget filterHighButton;
    private ButtonWidget filterMediumButton;
    private ButtonWidget filterLowButton;
    private ButtonWidget configButton;
    private ButtonWidget personalViewButton;
    private ButtonWidget teamAllViewButton;
    private ButtonWidget teamUnassignedViewButton;
    private ButtonWidget teamAssignedViewButton;

    private Task selectedTask;
    private List<Task> filteredTasks = new ArrayList<>();
    private List<Task> baseFilteredTasks = new ArrayList<>();
    private String currentFilter = "all";
    private String searchQuery = "";
    private boolean hasUnsavedChanges = false;
    private static boolean personalHasUnsavedChanges = false;
    private static boolean teamHasUnsavedChanges = false;

    private ViewMode viewMode = ViewMode.PERSONAL;

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
        taskManager = viewMode == ViewMode.PERSONAL ? personalTaskManager : teamTaskManager;
        hasUnsavedChanges = viewMode == ViewMode.PERSONAL ? personalHasUnsavedChanges : teamHasUnsavedChanges;

        // Rebuild UI
        rebuildUI();
    }

    private void rebuildUI() {
        currentFilter = "active";
        searchQuery = "";
        baseFilteredTasks = taskManager.getIncompleteTasks();
        baseFilteredTasks = applyAssignedFilterIfNeeded(baseFilteredTasks);
        filteredTasks = new ArrayList<>(baseFilteredTasks);
        this.clearChildren();

        // Get configuration
        ModConfig config = ModConfig.getInstance();

        // Calculate layout
        int guiWidth = config.getGuiWidth();
        int guiHeight = config.getGuiHeight();
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        int padding = config.getPadding();
        int configuredListHeight = config.getTaskListHeight();
        int headerOffset = 60;
        int e = config.getElementSpacing();
        int paddingV = 10;
        int inputRows = 3; // title, desc, tag
        int rowHeight = 20;
        int fieldsBlock = inputRows * rowHeight + (inputRows - 1) * e;
        int priorityRow = rowHeight + e;
        int actionRow = rowHeight + e;
        int saveRow = rowHeight;
        int bottomReserved = paddingV + fieldsBlock + priorityRow + actionRow + saveRow + paddingV;
        int maxListHeight = Math.max(rowHeight * 2, guiHeight - headerOffset - bottomReserved);
        int defaultVisible = 5;
        int preferredByItems = config.getTaskItemHeight() * defaultVisible;
        int desiredHeight = configuredListHeight > 0 ? configuredListHeight : preferredByItems;
        int listHeight = Math.min(desiredHeight, maxListHeight);

        int labelWidth = 40;
        int contentX = x + padding;
        int contentWidth = guiWidth - padding * 2;
        int listTop = y + headerOffset;
        taskListWidget = new TaskListWidget(this.client, contentX, listTop, contentWidth, listHeight);
        boolean nonOpClient = !isAdminClient();
        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        taskListWidget.setTeamAllViewForNonOp(nonOpClient && teamAllView);
        taskListWidget.setTasks(filteredTasks);
        taskListWidget.setOnTaskToggleCompletion(task -> {
            if (task.isCompleted()) {
                return;
            }
            boolean wasCompleted = task.isCompleted();
            toggleTaskCompletion(task);
            if (!wasCompleted && task.isCompleted()) {
                addNotification(Text.translatable("message.todolist.completed", task.getTitle()).getString());
            }
            refreshTaskList();
        });

        int currentY = y + headerOffset + listHeight + e;
        int fieldX = x + padding + labelWidth;
        int fieldWidth = guiWidth - padding * 2 - labelWidth;

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

        int priorityButtonWidth = 50;
        int priorityStartX = fieldX;

        priorityButtons = new ButtonWidget[3];
        for (int i = 0; i < 3; i++) {
            Task.Priority priority = Task.Priority.values()[2 - i];
            String base;
            switch (priority) {
                case HIGH:
                    base = Text.translatable("gui.todolist.priority.high").getString();
                    break;
                case MEDIUM:
                    base = Text.translatable("gui.todolist.priority.medium").getString();
                    break;
                case LOW:
                default:
                    base = Text.translatable("gui.todolist.priority.low").getString();
                    break;
            }
            String buttonText = (priority == Task.Priority.HIGH ? "§c[" :
                                priority == Task.Priority.MEDIUM ? "§e[" : "§a[") + base + "]";
            Text buttonLabel = Text.of(buttonText);
            int index = i;
            priorityButtons[i] = ButtonWidget.builder(buttonLabel, button -> {
                setSelectedPriority(priority);
                if (selectedTask != null) {
                    selectedTask.setPriority(priority);
                    refreshTaskList();
                    ClientTaskPackets.sendUpdateTask(selectedTask);
                }
                updatePrioritySelection();
                markUnsaved();
            }).dimensions(priorityStartX + index * (priorityButtonWidth + 4), currentY, priorityButtonWidth, 20).build();
            this.addDrawableChild(priorityButtons[i]);
        }

        int actionButtonWidth = 60;
        int actionButtonGap = 5;
        int actionButtonsCount = 2;
        int actionButtonsWidth = actionButtonWidth * actionButtonsCount + actionButtonGap * (actionButtonsCount - 1);
        int actionButtonX = x + guiWidth - padding - actionButtonsWidth;

        addButton = ButtonWidget.builder(Text.translatable("gui.todolist.add"), button -> {
            onAddTask();
        }).dimensions(actionButtonX, currentY, actionButtonWidth, 20).build();
        this.addDrawableChild(addButton);

        deleteButton = ButtonWidget.builder(Text.translatable("gui.todolist.delete"), button -> {
            onDeleteTask();
        }).dimensions(actionButtonX + (actionButtonWidth + actionButtonGap), currentY, actionButtonWidth, 20).build();
        deleteButton.active = false;
        this.addDrawableChild(deleteButton);

        currentY += 24 + 25;

        // Save and Cancel buttons at bottom
        int saveCancelWidth = 90;
        int saveCancelGap = 5;
        int totalSaveCancelWidth = saveCancelWidth * 2 + saveCancelGap;
        int saveCancelX = x + (guiWidth - totalSaveCancelWidth) / 2;

        ButtonWidget saveButton = ButtonWidget.builder(Text.translatable("gui.todolist.save"), button -> {
            onSaveTasks();
        }).dimensions(saveCancelX, currentY, saveCancelWidth, 20).build();
        this.addDrawableChild(saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), button -> {
            close();
        }).dimensions(saveCancelX + saveCancelWidth + saveCancelGap, currentY, saveCancelWidth, 20).build();
        this.addDrawableChild(cancelButton);

        int modeButtonWidth = 80;
        int modeButtonHeight = 20;
        int modeButtonGap = 4;
        int modesX = x + padding - modeButtonWidth - 8;
        int modesY = listTop;

        personalViewButton = ButtonWidget.builder(Text.translatable("gui.todolist.view.personal"), b -> {
            switchView(ViewMode.PERSONAL);
        }).dimensions(modesX, modesY, modeButtonWidth, modeButtonHeight).build();
        this.addDrawableChild(personalViewButton);

        boolean isAdmin = isAdminClient();

        int rowIndex = 1;

        teamUnassignedViewButton = ButtonWidget.builder(Text.translatable("gui.todolist.view.team_unassigned"), b -> {
            switchView(ViewMode.TEAM_UNASSIGNED);
        }).dimensions(modesX, modesY + (modeButtonHeight + modeButtonGap) * rowIndex, modeButtonWidth, modeButtonHeight).build();
        this.addDrawableChild(teamUnassignedViewButton);
        rowIndex++;

        teamAllViewButton = ButtonWidget.builder(Text.translatable("gui.todolist.view.team_all"), b -> {
            switchView(ViewMode.TEAM_ALL);
        }).dimensions(modesX, modesY + (modeButtonHeight + modeButtonGap) * rowIndex, modeButtonWidth, modeButtonHeight).build();
        this.addDrawableChild(teamAllViewButton);
        rowIndex++;

        teamAssignedViewButton = ButtonWidget.builder(Text.translatable("gui.todolist.view.team_assigned"), b -> {
            switchView(ViewMode.TEAM_ASSIGNED);
        }).dimensions(modesX, modesY + (modeButtonHeight + modeButtonGap) * rowIndex, modeButtonWidth, modeButtonHeight).build();
        this.addDrawableChild(teamAssignedViewButton);

        int assignButtonWidth = 80;
        int assignButtonHeight = 20;
        int assignButtonGap = 4;
        int assignsX = contentX + contentWidth + 8;
        int assignsY = listTop;

        claimButton = ButtonWidget.builder(Text.translatable("gui.todolist.claim_task"), b -> {
            onClaimTask();
        }).dimensions(assignsX, assignsY, assignButtonWidth, assignButtonHeight).build();
        claimButton.active = false;
        this.addDrawableChild(claimButton);

        abandonButton = ButtonWidget.builder(Text.translatable("gui.todolist.abandon_task"), b -> {
            onAbandonTask();
        }).dimensions(assignsX, assignsY + (assignButtonHeight + assignButtonGap), assignButtonWidth, assignButtonHeight).build();
        abandonButton.active = false;
        this.addDrawableChild(abandonButton);

        assignOthersButton = ButtonWidget.builder(Text.translatable("gui.todolist.assign_others"), b -> {
            onAssignOthers();
        }).dimensions(assignsX, assignsY + (assignButtonHeight + assignButtonGap) * 2, assignButtonWidth, assignButtonHeight).build();
        assignOthersButton.active = false;
        this.addDrawableChild(assignOthersButton);

        updateViewButtonsState();

        int filterButtonWidth = 60;
        int filterGap = 4;
        int filterCount = 5;
        int totalFilterWidth = filterButtonWidth * filterCount + filterGap * (filterCount - 1);
        int filtersX = x + padding;
        int filtersY = y + 10;

        filterActiveButton = ButtonWidget.builder(Text.translatable("gui.todolist.active"), button -> {
            filterTasks("active");
        }).dimensions(filtersX, filtersY, filterButtonWidth, 20).build();
        this.addDrawableChild(filterActiveButton);

        filterCompletedButton = ButtonWidget.builder(Text.translatable("gui.todolist.completed"), button -> {
            filterTasks("completed");
        }).dimensions(filtersX + (filterButtonWidth + filterGap), filtersY, filterButtonWidth, 20).build();
        this.addDrawableChild(filterCompletedButton);

        filterHighButton = ButtonWidget.builder(Text.of("§c" + Text.translatable("gui.todolist.filter.priority_high").getString()), button -> {
            filterTasks("priority_high");
        }).dimensions(filtersX + (filterButtonWidth + filterGap) * 2, filtersY, filterButtonWidth, 20).build();
        this.addDrawableChild(filterHighButton);

        filterMediumButton = ButtonWidget.builder(Text.of("§e" + Text.translatable("gui.todolist.filter.priority_medium").getString()), button -> {
            filterTasks("priority_medium");
        }).dimensions(filtersX + (filterButtonWidth + filterGap) * 3, filtersY, filterButtonWidth, 20).build();
        this.addDrawableChild(filterMediumButton);

        filterLowButton = ButtonWidget.builder(Text.of("§a" + Text.translatable("gui.todolist.filter.priority_low").getString()), button -> {
            filterTasks("priority_low");
        }).dimensions(filtersX + (filterButtonWidth + filterGap) * 4, filtersY, filterButtonWidth, 20).build();
        this.addDrawableChild(filterLowButton);

        int searchWidth = fieldWidth;
        int searchX = x + padding + labelWidth;
        int searchY = filtersY + 24;
        searchField = new TextFieldWidget(this.textRenderer, searchX, searchY, searchWidth, 20, Text.empty());
        searchField.setText("");
        this.addDrawableChild(searchField);

        int configBtnW = 50;
        int configBtnX = x + guiWidth - padding - configBtnW;
        configButton = ButtonWidget.builder(Text.translatable("gui.todolist.config.title"), b -> {
            this.client.setScreen(new ConfigScreen(this));
        }).dimensions(configBtnX, y + 10, configBtnW, 20).build();
        this.addDrawableChild(configButton);

        titleField.setChangedListener(text -> {
            if (selectedTask != null && !selectedTask.isCompleted()) {
                selectedTask.setTitle(text);
                markUnsaved();
            }
        });

        descField.setChangedListener(text -> {
            if (selectedTask != null && !selectedTask.isCompleted()) {
                selectedTask.setDescription(text);
                markUnsaved();
            }
        });

        tagField.setChangedListener(text -> {
            if (selectedTask != null && !selectedTask.isCompleted()) {
                String value = getFieldValue(tagField, "");
                if (value.isEmpty()) {
                    selectedTask.clearTags();
                } else {
                    List<String> tags = new ArrayList<>();
                    String[] parts = value.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            tags.add(trimmed);
                        }
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

        this.setFocused(titleField);
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
        context.fill(0, 0, this.width, this.height, BG_COLOR);

        Text title = hasUnsavedChanges ? Text.translatable("gui.todolist.title.unsaved") : TITLE;
        context.drawText(this.textRenderer, title, (this.width - this.textRenderer.getWidth(title)) / 2, 10, 0xFFFFFFFF, false);

        taskListWidget.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        ModConfig config = ModConfig.getInstance();
        int guiWidth = config.getGuiWidth();
        int guiHeight = config.getGuiHeight();
        int x = (this.width - guiWidth) / 2;
        int y = (this.height - guiHeight) / 2;
        int padding = config.getPadding();
        int labelX = x + padding;
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
            int searchLabelX = labelX;
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
        int startY = y + 35;
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

    private void updatePrioritySelection() {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Handle task list clicks
        Task clickedTask = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
        if (clickedTask != null) {
            selectTask(clickedTask);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return taskListWidget.mouseScrolled(mouseX, mouseY, 0, amount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Handle task list mouse release
        taskListWidget.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    // Event handlers

    private void onAddTask() {
        if (viewMode != ViewMode.PERSONAL && !isAdminClient()) {
            addNotification(Text.translatable("message.todolist.no_permission_add_team").getString());
            return;
        }
        String title = getFieldValue(titleField, "");
        String desc = getFieldValue(descField, "");
        String tagsStr = getFieldValue(tagField, "");

        if (!title.isEmpty()) {
            Task task = taskManager.addTask(title, desc);
            task.setPriority(selectedPriority);

            if (viewMode != ViewMode.PERSONAL) {
                if (this.client != null && this.client.player != null) {
                    String uuid = this.client.player.getUuid().toString();
                    task.setScope(Task.Scope.TEAM);
                    task.setCreatorUuid(uuid);
                    if (viewMode == ViewMode.TEAM_ASSIGNED) {
                        task.setAssigneeUuid(uuid);
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

    private void updateButtonStates() {
        boolean hasSelection = selectedTask != null;
        boolean canEdit = hasSelection && canEditTask(selectedTask);
        boolean isCompleted = hasSelection && selectedTask.isCompleted();
        boolean isAssigned = hasSelection
                && selectedTask.getAssigneeUuid() != null
                && !selectedTask.getAssigneeUuid().isEmpty();
        boolean priorityEnabled = canEdit && !isCompleted;
        deleteButton.active = hasSelection && canDeleteTask(selectedTask);
        if (priorityButtons != null) {
            for (ButtonWidget button : priorityButtons) {
                if (button != null) {
                    button.active = priorityEnabled;
                }
            }
        }
        boolean isAdmin = isAdminClient();
        if (addButton != null) {
            if (viewMode == ViewMode.PERSONAL) {
                addButton.active = !isCompleted;
            } else {
                addButton.active = isAdmin && !isCompleted;
            }
        }
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        if (claimButton != null) {
            claimButton.visible = showAssignButtons;
            if (isAdmin) {
                claimButton.active = hasSelection && showAssignButtons && !isCompleted && !isAssigned;
            } else {
                claimButton.active = hasSelection && showAssignButtons && !isCompleted
                        && viewMode == ViewMode.TEAM_UNASSIGNED;
            }
        }
        if (abandonButton != null) {
            abandonButton.visible = showAssignButtons;
            if (isAdmin) {
                abandonButton.active = hasSelection && showAssignButtons && !isCompleted;
            } else {
                abandonButton.active = hasSelection && showAssignButtons && !isCompleted
                        && viewMode == ViewMode.TEAM_ASSIGNED
                        && isCurrentPlayerAssignee(selectedTask);
            }
        }
        if (assignOthersButton != null) {
            assignOthersButton.visible = showAssignButtons;
            assignOthersButton.active = hasSelection && showAssignButtons && isAdmin && !isCompleted;
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
        if (task == null || task.isCompleted()) {
            return false;
        }
        if (viewMode == ViewMode.PERSONAL) {
            return true;
        }
        return isAdminClient();
    }

    private boolean canDeleteTask(Task task) {
        if (task == null) {
            return false;
        }
        if (viewMode == ViewMode.PERSONAL) {
            if (task.isCompleted()) {
                return true;
            }
            return canEditTask(task);
        }
        if (!isAdminClient()) {
            return false;
        }
        return true;
    }

    private boolean canToggleCompletion(Task task) {
        if (task == null) {
            return false;
        }
        if (viewMode == ViewMode.PERSONAL) {
            return true;
        }
        if (isAdminClient()) {
            return true;
        }
        if (viewMode != ViewMode.TEAM_ASSIGNED) {
            return false;
        }
        if (this.client == null || this.client.player == null) {
            return false;
        }
        String uuid = this.client.player.getUuid().toString();
        String assignee = task.getAssigneeUuid();
        return assignee != null && assignee.equals(uuid);
    }

    private boolean isCurrentPlayerAssignee(Task task) {
        if (task == null || this.client == null || this.client.player == null) {
            return false;
        }
        String uuid = this.client.player.getUuid().toString();
        String assignee = task.getAssigneeUuid();
        return assignee != null && assignee.equals(uuid);
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
        if (!isAdminClient() && assignee != null && !assignee.isEmpty() && !assignee.equals(uuid)) {
            addNotification(Text.translatable("message.todolist.already_assigned").getString());
            return;
        }
        selectedTask.setAssigneeUuid(uuid);
        addNotification(Text.translatable("message.todolist.assigned_to_me").getString());
        markUnsaved();
        refreshTaskList();
    }

    private void onAbandonTask() {
        if (selectedTask == null || this.client == null || this.client.player == null) {
            return;
        }
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Text.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        if (!isCurrentPlayerAssignee(selectedTask) && !isAdminClient()) {
            addNotification(Text.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        selectedTask.setAssigneeUuid(null);
        addNotification(Text.translatable("message.todolist.abandoned_task").getString());
        markUnsaved();
        refreshTaskList();
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
        currentFilter = filter;
        List<Task> result = new ArrayList<>();
        switch (filter) {
            case "all":
                result = taskManager.getAllTasks();
                break;
            case "active":
                result = taskManager.getIncompleteTasks();
                break;
            case "completed":
                result = taskManager.getCompletedTasks();
                break;
            case "priority_high":
                result = taskManager.getTasksByPriority(Task.Priority.HIGH);
                break;
            case "priority_medium":
                result = taskManager.getTasksByPriority(Task.Priority.MEDIUM);
                break;
            case "priority_low":
                result = taskManager.getTasksByPriority(Task.Priority.LOW);
                break;
        }
        baseFilteredTasks = applyAssignedFilterIfNeeded(result);
        applySearchFilter();
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
        taskListWidget.setTasks(filteredTasks);
    }

    private List<Task> applyAssignedFilterIfNeeded(List<Task> source) {
        if (viewMode == ViewMode.TEAM_ALL) {
            List<Task> result = new ArrayList<>();
            for (Task task : source) {
                String assignee = task.getAssigneeUuid();
                if (assignee != null && !assignee.isEmpty()) {
                    result.add(task);
                }
            }
            return result;
        }
        if (viewMode == ViewMode.TEAM_ASSIGNED) {
            if (this.client == null || this.client.player == null) {
                return source;
            }
            String uuid = this.client.player.getUuid().toString();
            List<Task> result = new ArrayList<>();
            for (Task task : source) {
                String assignee = task.getAssigneeUuid();
                if (assignee != null && assignee.equals(uuid)) {
                    result.add(task);
                }
            }
            return result;
        }
        if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            List<Task> result = new ArrayList<>();
            for (Task task : source) {
                if (task.getAssigneeUuid() == null || task.getAssigneeUuid().isEmpty()) {
                    result.add(task);
                }
            }
            return result;
        }
        return source;
    }

    private void switchView(ViewMode mode) {
        if (this.viewMode == mode) {
            return;
        }
        this.viewMode = mode;
        taskManager = viewMode == ViewMode.PERSONAL ? personalTaskManager : teamTaskManager;
        rebuildUI();
    }

    private void updateViewButtonsState() {
        if (personalViewButton != null) {
            personalViewButton.active = viewMode != ViewMode.PERSONAL;
        }
        if (teamAllViewButton != null) {
            teamAllViewButton.active = viewMode != ViewMode.TEAM_ALL;
        }
        if (teamUnassignedViewButton != null) {
            teamUnassignedViewButton.active = viewMode != ViewMode.TEAM_UNASSIGNED;
        }
        if (teamAssignedViewButton != null) {
            teamAssignedViewButton.active = viewMode != ViewMode.TEAM_ASSIGNED;
        }
    }

    private enum ViewMode {
        PERSONAL,
        TEAM_ALL,
        TEAM_UNASSIGNED,
        TEAM_ASSIGNED
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

    private class AssignPlayerScreen extends Screen {
        private final TodoScreen parentScreen;
        private final Task targetTask;

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
            int y = this.height / 6 + 20;
            int rowH = 22;
            int index = 0;

            java.util.Collection<net.minecraft.client.network.PlayerListEntry> entries = client.getNetworkHandler().getPlayerList();
            for (net.minecraft.client.network.PlayerListEntry entry : entries) {
                String name = entry.getProfile().getName();
                java.util.UUID uuid = entry.getProfile().getId();
                Text label = Text.of(name);
                int btnY = y + index * rowH;
                ButtonWidget btn = ButtonWidget.builder(label, b -> {
                    applyAssignTo(uuid.toString(), name);
                }).dimensions(x, btnY, guiWidth, 20).build();
                this.addDrawableChild(btn);
                index++;
            }

            int cancelY = y + index * rowH + 10;
            ButtonWidget cancel = ButtonWidget.builder(Text.translatable("gui.todolist.cancel"), b -> {
                client.setScreen(parentScreen);
            }).dimensions(x, cancelY, guiWidth, 20).build();
            this.addDrawableChild(cancel);
        }

        private void applyAssignTo(String uuid, String name) {
            targetTask.setAssigneeUuid(uuid);
            parentScreen.addNotification(Text.translatable("message.todolist.assigned_to_player", name).getString());
            parentScreen.markUnsaved();
            parentScreen.refreshTaskList();
            client.setScreen(parentScreen);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}
