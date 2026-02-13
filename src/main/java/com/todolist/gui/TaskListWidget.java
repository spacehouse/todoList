package com.todolist.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.todolist.config.ModConfig;
import com.todolist.task.Task;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Widget for displaying task list
 *
 * Features:
 * - Scrollable list
 * - Task color coding by priority
 * - Completion status indicator
 * - Hover effects
 * - Clickable checkbox to toggle completion
 */
public class TaskListWidget implements Drawable {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private List<Task> tasks = new ArrayList<>();
    private int scrollOffset = 0;
    private int hoveredTaskIndex = -1;
    private int selectedTaskIndex = -1;
    private int taskItemHeight; // Configurable
    private Consumer<Task> onTaskToggleCompletion; // Callback for checkbox clicks

    // Scrollbar dragging
    private boolean isDraggingScrollbar = false;
    private double dragStartY = 0;
    private int dragStartScrollOffset = 0;

    public TaskListWidget(MinecraftClient client, int x, int y, int width, int height) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.taskItemHeight = ModConfig.getInstance().getTaskItemHeight();
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        this.scrollOffset = 0;
    }

    public void setOnTaskToggleCompletion(Consumer<Task> callback) {
        this.onTaskToggleCompletion = callback;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();

        // Draw background
        context.fill(x, y, x + width, y + height, config.getBackgroundColor());
        context.drawBorder(x, y, width, height, config.getBorderColor());

        // Draw tasks
        renderTasks(context, mouseX, mouseY);

        // Draw scrollbar if needed
        if (tasks.size() * taskItemHeight > height) {
            renderScrollbar(context);
        }
    }

    private void renderTasks(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY) {
        TextRenderer textRenderer = client.textRenderer;
        ModConfig config = ModConfig.getInstance();
        int visibleTasks = Math.min(tasks.size(), height / taskItemHeight);

        for (int i = 0; i < visibleTasks; i++) {
            int taskIndex = i + scrollOffset;
            if (taskIndex >= tasks.size()) break;

            Task task = tasks.get(taskIndex);
            int taskY = y + i * taskItemHeight;

            // Determine colors
            int bgColor = getTaskBackgroundColor(taskIndex, taskY, mouseX, mouseY);
            int priorityColor = task.getPriority().getColor();
            int textColor = task.isCompleted() ? 0xFF888888 : 0xFFFFFFFF;

            // Draw task background
            context.fill(x + 1, taskY, x + width - 1, taskY + taskItemHeight - 1, bgColor);

            // Draw priority indicator
            context.fill(x + 2, taskY + 2, x + 6, taskY + taskItemHeight - 3, priorityColor);

            // Draw completion checkbox
            int checkboxX = x + 12;
            int checkboxY = taskY + (taskItemHeight - 12) / 2;
            context.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF000000);
            context.drawBorder(checkboxX, checkboxY, 12, 12, 0xFFFFFFFF);

            if (task.isCompleted()) {
                // Draw checkmark
                context.fill(checkboxX + 3, checkboxY + 5, checkboxX + 5, checkboxY + 7, 0xFF00FF00);
                context.fill(checkboxX + 5, checkboxY + 7, checkboxX + 9, checkboxY + 3, 0xFF00FF00);
            }

            // Draw task title
            String title = task.getTitle();
            int maxWidth = width - 40;
            String truncatedTitle = textRenderer.trimToWidth(title, maxWidth);
            context.drawText(textRenderer, Text.of(truncatedTitle),
                    x + 30, taskY + (taskItemHeight - textRenderer.fontHeight) / 2,
                    textColor, false);

            // Draw tags if space allows
            if (!task.getTags().isEmpty() && width > 150) {
                int tagX = x + width - 60;
                String tagStr = task.getTags().iterator().next();
                context.drawText(textRenderer, Text.of("[" + tagStr + "]"),
                        tagX, taskY + (taskItemHeight - textRenderer.fontHeight) / 2,
                        0xFFAAAAFF, false);
            }
        }
    }

    private int getTaskBackgroundColor(int taskIndex, int taskY, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();

        // Check if selected
        if (taskIndex == selectedTaskIndex) {
            return config.getSelectedBackgroundColor();
        }

        // Check if hovered
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= taskY && mouseY < taskY + taskItemHeight) {
            hoveredTaskIndex = taskIndex;
            return config.getHoveredBackgroundColor();
        }
        return 0xFF1A1A1A;
    }

    private void renderScrollbar(net.minecraft.client.gui.DrawContext context) {
        ModConfig config = ModConfig.getInstance();
        int scrollbarWidth = 10;
        int scrollbarX = x + width - scrollbarWidth - 1;

        // Calculate scrollbar height and position
        int totalHeight = tasks.size() * taskItemHeight;
        float scrollbarHeight = (float) height / totalHeight * height;
        float scrollbarY = y + (float) scrollOffset / tasks.size() * height;

        // Draw scrollbar track
        context.fill(scrollbarX, y, scrollbarX + scrollbarWidth, y + height, 0xFF2A2A2A);

        // Draw scrollbar thumb
        context.fill(scrollbarX, (int) scrollbarY, scrollbarX + scrollbarWidth,
                (int) (scrollbarY + scrollbarHeight), 0xFF555555);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + height) {

            // Check if clicking on scrollbar
            int scrollbarWidth = 10;
            int scrollbarX = x + width - scrollbarWidth;
            if (mouseX >= scrollbarX) {
                isDraggingScrollbar = true;
                dragStartY = mouseY;
                dragStartScrollOffset = scrollOffset;
                return true;
            }

            // Check if clicking on a checkbox
            int index = (int) ((mouseY - y) / taskItemHeight) + scrollOffset;
            if (index >= 0 && index < tasks.size()) {
                int taskY = y + (index - scrollOffset) * taskItemHeight;
                int checkboxX = x + 12;
                int checkboxY = taskY + (taskItemHeight - 12) / 2;

                // Check if click is within checkbox bounds (12x12)
                if (mouseX >= checkboxX && mouseX < checkboxX + 12 &&
                    mouseY >= checkboxY && mouseY < checkboxY + 12) {
                    // Toggle task completion
                    Task clickedTask = tasks.get(index);
                    if (onTaskToggleCompletion != null) {
                        onTaskToggleCompletion.accept(clickedTask);
                    }
                    return true;
                }
            }

            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar) {
            int maxScroll = Math.max(0, tasks.size() - height / taskItemHeight);
            if (maxScroll > 0) {
                double dragDelta = mouseY - dragStartY;
                int scrollDelta = (int) (dragDelta / height * maxScroll);
                scrollOffset = Math.max(0, Math.min(maxScroll, dragStartScrollOffset + scrollDelta));
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + height) {

            int maxScroll = Math.max(0, tasks.size() - height / taskItemHeight);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
            return true;
        }
        return false;
    }

    public Task getTaskAt(int x, int y) {
        if (x >= this.x && x < this.x + width &&
            y >= this.y && y < this.y + height) {

            int index = (y - this.y) / taskItemHeight + scrollOffset;
            if (index >= 0 && index < tasks.size()) {
                return tasks.get(index);
            }
        }
        return null;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public int getHeight() {
        return height;
    }

    public void setSelectedTask(Task task) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).equals(task)) {
                selectedTaskIndex = i;
                break;
            }
        }
    }

    public void clearSelection() {
        selectedTaskIndex = -1;
    }
}
