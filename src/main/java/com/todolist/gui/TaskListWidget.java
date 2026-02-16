package com.todolist.gui;

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
 * 显示任务列表的组件
 *
 * 特性:
 * - 可滚动列表，使用独立 ScrollBar 组件
 * - 根据优先级进行颜色编码
 * - 完成状态指示器
 * - 悬停效果
 * - 可点击复选框切换完成状态
 */
public class TaskListWidget implements Drawable {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private List<Task> tasks = new ArrayList<>();
    private final ScrollBar scrollBar;
    private int hoveredTaskIndex = -1;
    private int selectedTaskIndex = -1;
    private int taskItemHeight;
    private Consumer<Task> onTaskToggleCompletion;

    public TaskListWidget(MinecraftClient client, int x, int y, int width, int height) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.taskItemHeight = ModConfig.getInstance().getTaskItemHeight();
        // 创建独立的滚动条组件（宽度10px）
        int barWidth = 10;
        int barX = x + width - barWidth - 1;
        this.scrollBar = new ScrollBar(barX, y, barWidth, height);
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        this.scrollBar.setValue(0);
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        int maxScroll = Math.max(0, tasks.size() - height / taskItemHeight);
        scrollBar.setMaxValue(maxScroll);
    }

    public void setOnTaskToggleCompletion(Consumer<Task> callback) {
        this.onTaskToggleCompletion = callback;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();

        // 绘制背景
        context.fill(x, y, x + width, y + height, config.getBackgroundColor());
        context.drawBorder(x, y, width, height, config.getBorderColor());

        // 绘制任务
        renderTasks(context, mouseX, mouseY);

        // 绘制滚动条（如果需要）
        int totalContentHeight = tasks.size() * taskItemHeight;
        if (totalContentHeight > height) {
            // 使用 ScrollBarRenderer 接口适配 DrawContext
            scrollBar.render(mouseX, mouseY, totalContentHeight, new ScrollBar.ScrollBarRenderer() {
                @Override
                public void fillRect(int x1, int y1, int x2, int y2, int color) {
                    context.fill(x1, y1, x2, y2, color);
                }
            });
        }
    }

    private void renderTasks(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY) {
        TextRenderer textRenderer = client.textRenderer;
        ModConfig config = ModConfig.getInstance();
        int scrollOffset = scrollBar.getValue();
        int visibleTasks = Math.min(tasks.size() - scrollOffset, height / taskItemHeight);

        for (int i = 0; i < visibleTasks; i++) {
            int taskIndex = i + scrollOffset;
            if (taskIndex >= tasks.size()) break;

            Task task = tasks.get(taskIndex);
            int taskY = y + i * taskItemHeight;

            // 确定颜色
            int bgColor = getTaskBackgroundColor(taskIndex, taskY, mouseX, mouseY);
            int priorityColor = task.getPriority().getColor();
            int textColor = task.isCompleted() ? 0xFF888888 : 0xFFFFFFFF;

            // 绘制任务背景
            context.fill(x + 1, taskY, x + width - 1, taskY + taskItemHeight - 1, bgColor);

            // 绘制优先级指示器
            context.fill(x + 2, taskY + 2, x + 6, taskY + taskItemHeight - 3, priorityColor);

            // 绘制完成状态复选框
            int checkboxX = x + 12;
            int checkboxY = taskY + (taskItemHeight - 12) / 2;
            context.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF000000);
            context.drawBorder(checkboxX, checkboxY, 12, 12, 0xFFFFFFFF);

            if (task.isCompleted()) {
                // 绘制对勾
                context.fill(checkboxX + 3, checkboxY + 5, checkboxX + 5, checkboxY + 7, 0xFF00FF00);
                context.fill(checkboxX + 5, checkboxY + 7, checkboxX + 9, checkboxY + 3, 0xFF00FF00);
            }

            // 绘制任务标题
            String title = task.getTitle();
            int maxWidth = width - 40;
            String truncatedTitle = textRenderer.trimToWidth(title, maxWidth);
            context.drawText(textRenderer, Text.of(truncatedTitle),
                    x + 30, taskY + (taskItemHeight - textRenderer.fontHeight) / 2,
                    textColor, false);

            // 如果空间足够，绘制标签
            if (!task.getTags().isEmpty() && width > 150) {
                int tagX = x + width - 60;
                String tagStr = task.getTags().iterator().next();
                context.drawText(textRenderer, Text.of("[" + tagStr + "]"),
                        tagX, taskY + (taskItemHeight - textRenderer.fontHeight) / 2,
                        0xFF55FFFF, false);
            }
        }
    }

    private int getTaskBackgroundColor(int taskIndex, int taskY, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();

        // 检查是否被选中
        if (taskIndex == selectedTaskIndex) {
            return config.getSelectedBackgroundColor();
        }

        // 检查是否被悬停
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= taskY && mouseY < taskY + taskItemHeight) {
            hoveredTaskIndex = taskIndex;
            return config.getHoveredBackgroundColor();
        }
        return 0xFF1A1A1A;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + height) {

            // 检查是否点击在滚动条上
            if (scrollBar.wasMouseOver()) {
                scrollBar.setIsDragging(true);
                return true;
            }

            // 检查是否点击在复选框上
            int scrollOffset = scrollBar.getValue();
            int index = (int) ((mouseY - y) / taskItemHeight) + scrollOffset;
            if (index >= 0 && index < tasks.size()) {
                int taskY = y + (index - scrollOffset) * taskItemHeight;
                int checkboxX = x + 12;
                int checkboxY = taskY + (taskItemHeight - 12) / 2;

                // 检查点击是否在复选框范围内 (12x12)
                if (mouseX >= checkboxX && mouseX < checkboxX + 12 &&
                    mouseY >= checkboxY && mouseY < checkboxY + 12) {
                    Task clickedTask = tasks.get(index);
                    if (!clickedTask.isCompleted()) {
                        if (onTaskToggleCompletion != null) {
                            onTaskToggleCompletion.accept(clickedTask);
                        }
                    }
                    return true;
                }
            }

            return false;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // 滚动条拖拽在 render() 方法中处理
        return scrollBar.isDragging();
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            scrollBar.setIsDragging(false);
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + height) {

            scrollBar.offsetValue(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    public Task getTaskAt(int x, int y) {
        if (x >= this.x && x < this.x + width &&
            y >= this.y && y < this.y + height) {

            int scrollOffset = scrollBar.getValue();
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
