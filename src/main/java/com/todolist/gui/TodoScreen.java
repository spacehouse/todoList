package com.todolist.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.todolist.TodoListMod;
import com.todolist.config.ModConfig;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
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
 */
public class TodoScreen extends Screen {
    private static final Text TITLE = Text.of("待办事项列表");
    private static final int BG_COLOR = 0xFF000000;

    private final Screen parent;
    private TaskManager taskManager;
    private TaskListWidget taskListWidget;

    // Input fields
    private TextFieldWidget titleField;
    private TextFieldWidget descField;
    private TextFieldWidget prioritySelector;

    // Buttons
    private ButtonWidget addButton;
    private ButtonWidget editButton;
    private ButtonWidget deleteButton;

    // Selected priority for new/edited tasks
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // Filter buttons
    private ButtonWidget filterAllButton;
    private ButtonWidget filterActiveButton;
    private ButtonWidget filterCompletedButton;
    private ButtonWidget configButton;

    private Task selectedTask;
    private List<Task> filteredTasks = new ArrayList<>();
    private final List<ClickableWidget> drawableButtons = new ArrayList<>();

    public TodoScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Initialize task manager and load tasks from storage
        if (taskManager == null) {
            taskManager = new TaskManager();
            try {
                List<Task> loadedTasks = TodoListMod.getTaskStorage().loadTasks();
                for (Task task : loadedTasks) {
                    taskManager.addTask(task);
                }
                TodoListMod.LOGGER.info("Loaded {} tasks from storage", loadedTasks.size());
            } catch (Exception e) {
                TodoListMod.LOGGER.error("Failed to load tasks from storage", e);
            }
        }

        // Rebuild UI with current configuration
        rebuildUI();
    }

    private void rebuildUI() {
        filteredTasks = taskManager.getAllTasks();
        drawableButtons.clear();

        // Get configuration
        ModConfig config = ModConfig.getInstance();

        // Calculate layout
        int x = (width - config.getGuiWidth()) / 2;
        int y = (height - config.getGuiHeight()) / 2;
        int guiWidth = config.getGuiWidth();
        int guiHeight = config.getGuiHeight();
        int padding = config.getPadding();

        // Calculate available height for task list
        int listHeight = config.getTaskListHeight();
        int availableHeight = guiHeight - 165;
        if (listHeight > availableHeight) {
            listHeight = availableHeight;
        }

        // Task list widget
        taskListWidget = new TaskListWidget(this.client, x + padding, y + config.getTaskListY(), guiWidth - padding * 2, listHeight);
        taskListWidget.setTasks(filteredTasks);
        taskListWidget.setOnTaskToggleCompletion(task -> {
            // Toggle task completion when checkbox is clicked
            taskManager.toggleTaskCompletion(task.getId());
            refreshTaskList();
        });

        // Calculate Y positions for input fields and buttons
        int currentY = y + config.getTaskListY() + listHeight + config.getElementSpacing() + 4;

        // Row 1: Title input field
        int titleWidth = guiWidth - padding * 2;
        titleField = new TextFieldWidget(textRenderer, x + padding, currentY, titleWidth, config.getButtonHeight(), Text.of("任务标题"));
        titleField.setPlaceholder(Text.of("输入任务标题..."));
        titleField.setMaxLength(100);
        addDrawableChild(titleField);
        currentY += config.getButtonHeight() + config.getElementSpacing();

        // Row 2: Description input field
        int descWidth = guiWidth - padding * 2;
        descField = new TextFieldWidget(textRenderer, x + padding, currentY, descWidth, config.getButtonHeight(), Text.of("描述"));
        descField.setPlaceholder(Text.of("描述（可选）..."));
        descField.setMaxLength(255);
        addDrawableChild(descField);
        currentY += config.getButtonHeight() + config.getElementSpacing();

        // Row 3: Priority buttons (LOW, MEDIUM, HIGH)
        int priorityButtonWidth = 70;
        int totalPriorityWidth = priorityButtonWidth * 3 + 8; // 3 buttons + 2 gaps of 4px each
        int priorityStartX = x + (guiWidth - totalPriorityWidth) / 2; // Center the buttons

        ButtonWidget lowPriorityButton = ButtonWidget.builder(Text.of("低"), button -> {
            setSelectedPriority(Task.Priority.LOW);
        }).dimensions(priorityStartX, currentY, priorityButtonWidth, config.getButtonHeight()).build();
        addDrawableChild(lowPriorityButton);
        drawableButtons.add(lowPriorityButton);

        ButtonWidget mediumPriorityButton = ButtonWidget.builder(Text.of("中"), button -> {
            setSelectedPriority(Task.Priority.MEDIUM);
        }).dimensions(priorityStartX + priorityButtonWidth + 4, currentY, priorityButtonWidth, config.getButtonHeight()).build();
        addDrawableChild(mediumPriorityButton);
        drawableButtons.add(mediumPriorityButton);

        ButtonWidget highPriorityButton = ButtonWidget.builder(Text.of("高"), button -> {
            setSelectedPriority(Task.Priority.HIGH);
        }).dimensions(priorityStartX + priorityButtonWidth * 2 + 8, currentY, priorityButtonWidth, config.getButtonHeight()).build();
        addDrawableChild(highPriorityButton);
        drawableButtons.add(highPriorityButton);
        currentY += config.getButtonHeight() + config.getElementSpacing();

        // Row 4: Action buttons (Add, Edit, Delete)
        int actionButtonWidth = 70;
        int actionButtonGap = 5;
        int totalActionWidth = actionButtonWidth * 3 + actionButtonGap * 2;
        int actionButtonX = x + (guiWidth - totalActionWidth) / 2; // Center the buttons

        addButton = ButtonWidget.builder(Text.of("添加"), button -> {
            onAddTask();
        }).dimensions(actionButtonX, currentY, actionButtonWidth, config.getButtonHeight()).build();
        addDrawableChild(addButton);
        drawableButtons.add(addButton);

        editButton = ButtonWidget.builder(Text.of("编辑"), button -> {
            onEditTask();
        }).dimensions(actionButtonX + actionButtonWidth + actionButtonGap, currentY, actionButtonWidth, config.getButtonHeight()).build();
        editButton.active = false;
        addDrawableChild(editButton);
        drawableButtons.add(editButton);

        deleteButton = ButtonWidget.builder(Text.of("删除"), button -> {
            onDeleteTask();
        }).dimensions(actionButtonX + actionButtonWidth * 2 + actionButtonGap * 2, currentY, actionButtonWidth, config.getButtonHeight()).build();
        deleteButton.active = false;
        addDrawableChild(deleteButton);
        drawableButtons.add(deleteButton);
        currentY += config.getButtonHeight() + config.getElementSpacing() * 2;

        // Save and Cancel buttons at bottom
        int saveCancelWidth = 90;
        int saveCancelGap = 5;
        int totalSaveCancelWidth = saveCancelWidth * 2 + saveCancelGap;
        int saveCancelX = x + (guiWidth - totalSaveCancelWidth) / 2; // Center the buttons

        ButtonWidget saveButton = ButtonWidget.builder(Text.of("保存"), button -> {
            onSaveTasks();
        }).dimensions(saveCancelX, currentY, saveCancelWidth, config.getButtonHeight()).build();
        addDrawableChild(saveButton);
        drawableButtons.add(saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.of("取消"), button -> {
            onCancel();
        }).dimensions(saveCancelX + saveCancelWidth + saveCancelGap, currentY, saveCancelWidth, config.getButtonHeight()).build();
        addDrawableChild(cancelButton);
        drawableButtons.add(cancelButton);

        // Filter buttons
        filterAllButton = ButtonWidget.builder(Text.of("全部"), button -> {
            filterTasks("all");
        }).dimensions(x + 100, y + 10, 60, 20).build();
        addDrawableChild(filterAllButton);
        drawableButtons.add(filterAllButton);

        filterActiveButton = ButtonWidget.builder(Text.of("进行中"), button -> {
            filterTasks("active");
        }).dimensions(x + 165, y + 10, 60, 20).build();
        addDrawableChild(filterActiveButton);
        drawableButtons.add(filterActiveButton);

        filterCompletedButton = ButtonWidget.builder(Text.of("已完成"), button -> {
            filterTasks("completed");
        }).dimensions(x + 230, y + 10, 60, 20).build();
        addDrawableChild(filterCompletedButton);
        drawableButtons.add(filterCompletedButton);

        // Config button - opens settings screen
        configButton = ButtonWidget.builder(Text.of("⚙"), button -> {
            onOpenConfig();
        }).dimensions(x + 295, y + 10, 30, 20).build();
        addDrawableChild(configButton);
        drawableButtons.add(configButton);

        // Set initial focus
        setInitialFocus(titleField);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background
        renderBackground(context);

        // Draw title - moved up to avoid overlap with filter buttons
        int titleY = (height - ModConfig.getInstance().getGuiHeight()) / 2 - 5;
        context.drawText(textRenderer, TITLE,
                (width - textRenderer.getWidth(TITLE)) / 2,
                titleY,
                0xFFFFFFFF, true);

        // Draw task list widget manually
        taskListWidget.render(context, mouseX, mouseY, delta);

        // Draw input fields
        titleField.render(context, mouseX, mouseY, delta);
        descField.render(context, mouseX, mouseY, delta);

        // Draw buttons
        for (ClickableWidget button : drawableButtons) {
            button.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key to add task
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (titleField.isFocused()) {
                onAddTask();
                return true;
            }
        }

        // Escape key to close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle task list selection
        taskListWidget.mouseClicked(mouseX, mouseY, button);
        Task clicked = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
        if (clicked != null) {
            selectTask(clicked);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Handle task list scrolling
        return taskListWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Handle scrollbar dragging
        if (taskListWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Handle scrollbar release
        taskListWidget.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        titleField.tick();
        descField.tick();
    }

    @Override
    public void close() {
        // Auto-save tasks before closing
        try {
            TodoListMod.getTaskStorage().saveTasks(taskManager.getAllTasks());
            TodoListMod.LOGGER.info("Tasks auto-saved on close");
        } catch (Exception e) {
            TodoListMod.LOGGER.error("Failed to auto-save tasks on close", e);
        }
        this.client.setScreen(parent);
    }

    // Event handlers

    private void onAddTask() {
        String title = titleField.getText().trim();
        String desc = descField.getText().trim();

        if (!title.isEmpty()) {
            Task task = taskManager.addTask(title, desc);
            task.setPriority(selectedPriority);

            taskManager.addListener((type, t) -> {
                // Handle task changes (network sync later)
            });

            // Clear input fields
            titleField.setText("");
            descField.setText("");
            selectedPriority = Task.Priority.MEDIUM; // Reset to default

            // Refresh task list
            refreshTaskList();
        }
    }

    private void onEditTask() {
        if (selectedTask != null) {
            String newTitle = titleField.getText().trim();
            String newDesc = descField.getText().trim();

            if (!newTitle.isEmpty()) {
                selectedTask.setTitle(newTitle);
                selectedTask.setDescription(newDesc);
                selectedTask.setPriority(selectedPriority);

                taskManager.updateTask(selectedTask);

                // Refresh task list
                refreshTaskList();
            }
        }
    }

    private void onDeleteTask() {
        if (selectedTask != null) {
            taskManager.deleteTask(selectedTask.getId());
            selectedTask = null;
            updateButtonStates();
            refreshTaskList();
        }
    }

    private void selectTask(Task task) {
        selectedTask = task;
        selectedPriority = task.getPriority();
        titleField.setText(task.getTitle());
        descField.setText(task.getDescription());
        taskListWidget.setSelectedTask(task);
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedTask != null;
        editButton.active = hasSelection;
        deleteButton.active = hasSelection;
    }

    private void setSelectedPriority(Task.Priority priority) {
        this.selectedPriority = priority;
    }

    private void filterTasks(String filter) {
        switch (filter) {
            case "all":
                filteredTasks = taskManager.getAllTasks();
                break;
            case "active":
                filteredTasks = taskManager.getIncompleteTasks();
                break;
            case "completed":
                filteredTasks = taskManager.getCompletedTasks();
                break;
        }
        taskListWidget.clearSelection();
        taskListWidget.setTasks(filteredTasks);
    }

    private void refreshTaskList() {
        filterTasks("all"); // Refresh with current filter
    }

    private void onSaveTasks() {
        try {
            TodoListMod.getTaskStorage().saveTasks(taskManager.getAllTasks());
            // Show save confirmation message
            if (client.player != null) {
                client.player.sendMessage(Text.of("§a任务列表已保存！"), true);
            }
            // Close UI after saving
            close();
        } catch (Exception e) {
            if (client.player != null) {
                client.player.sendMessage(Text.of("§c保存失败: " + e.getMessage()), true);
            }
            TodoListMod.LOGGER.error("Failed to save tasks", e);
        }
    }

    private void onCancel() {
        close();
    }

    private void onOpenConfig() {
        client.setScreen(new ConfigScreen(this, this));
    }
}
