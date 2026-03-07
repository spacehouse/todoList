package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.task.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

/**
 * 任务列表组件：渲染任务条目、处理选中/悬停，并支持滚动与完成状态切换。
 */
public class TaskListWidget implements Renderable {
    private final Minecraft client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private List<Task> tasks = new ArrayList<>();
    private final ScrollBar scrollBar;
    private int hoveredTaskIndex = -1;
    private int selectedTaskIndex = -1;
    private String selectedTaskId;
    private int taskItemHeight;
    private boolean teamAllViewForNonOp;
    private Consumer<Task> onTaskToggleCompletion;

    /**
     * 创建任务列表组件。
     */
    public TaskListWidget(Minecraft client, int x, int y, int width, int height) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.taskItemHeight = ModConfig.getInstance().getTaskItemHeight();
        // 鍒涘缓鐙珛鐨勬粴鍔ㄦ潯缁勪欢锛堝搴?0px锛?
        int barWidth = 10;
        int barX = x + width - barWidth - 1;
        this.scrollBar = new ScrollBar(barX, y, barWidth, height);
    }

    /**
     * 设置非 OP 在 TEAM_ALL 视图下的特殊行为开关（用于客户端展示策略）。
     */
    public void setTeamAllViewForNonOp(boolean enabled) {
        this.teamAllViewForNonOp = enabled;
    }

    /**
     * 设置要展示的任务列表，并重置滚动与选择状态。
     */
    public void setTasks(List<Task> tasks) {
        int previousScroll = this.scrollBar.getValue();
        this.tasks = tasks == null ? new ArrayList<>() : tasks;
        updateMaxScroll();
        syncSelectionIndex();
        if (selectedTaskIndex >= 0) {
            ensureVisibleIndex(selectedTaskIndex);
        } else {
            this.scrollBar.setValue(previousScroll);
        }
    }

    private void updateMaxScroll() {
        int maxScroll = Math.max(0, tasks.size() - height / taskItemHeight);
        scrollBar.setMaxValue(maxScroll);
    }

    /**
     * 设置任务“切换完成状态”的回调。
     */
    public void setOnTaskToggleCompletion(Consumer<Task> callback) {
        this.onTaskToggleCompletion = callback;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();

        // 缁樺埗鑳屾櫙
        context.fill(x, y, x + width, y + height, config.getBackgroundColor());
        context.renderOutline(x, y, width, height, config.getBorderColor());

        // 缁樺埗浠诲姟
        renderTasks(context, mouseX, mouseY);

        // 缁樺埗婊氬姩鏉★紙濡傛灉闇€瑕侊級
        int totalContentHeight = tasks.size() * taskItemHeight;
        if (totalContentHeight > height) {
            // 浣跨敤 ScrollBarRenderer 鎺ュ彛閫傞厤 DrawContext
            scrollBar.render(mouseX, mouseY, totalContentHeight, new ScrollBar.ScrollBarRenderer() {
                @Override
                public void fillRect(int x1, int y1, int x2, int y2, int color) {
                    context.fill(x1, y1, x2, y2, color);
                }
            });
        }
    }

    private void renderTasks(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY) {
        Font textRenderer = client.font;
        ModConfig config = ModConfig.getInstance();
        int scrollOffset = scrollBar.getValue();
        int visibleTasks = Math.min(tasks.size() - scrollOffset, height / taskItemHeight);

        for (int i = 0; i < visibleTasks; i++) {
            int taskIndex = i + scrollOffset;
            if (taskIndex >= tasks.size()) break;

            Task task = tasks.get(taskIndex);
            int taskY = y + i * taskItemHeight;

            int bgColor = getTaskBackgroundColor(taskIndex, taskY, mouseX, mouseY);
            int priorityColor = task.getPriority().getColor();
            int textColor = task.isCompleted() ? 0xFF888888 : 0xFFFFFFFF;

            context.fill(x + 1, taskY, x + width - 1, taskY + taskItemHeight - 1, bgColor);

            context.fill(x + 2, taskY + 2, x + 6, taskY + taskItemHeight - 3, priorityColor);

            int checkboxX = x + 12;
            int checkboxY = taskY + (taskItemHeight - 12) / 2;
            context.fill(checkboxX, checkboxY, checkboxX + 12, checkboxY + 12, 0xFF000000);
            context.renderOutline(checkboxX, checkboxY, 12, 12, 0xFFFFFFFF);

            if (task.isCompleted()) {
                context.fill(checkboxX + 3, checkboxY + 5, checkboxX + 5, checkboxY + 7, 0xFF00FF00);
                context.fill(checkboxX + 5, checkboxY + 7, checkboxX + 9, checkboxY + 3, 0xFF00FF00);
            }

            String title = task.getTitle();
            if (title == null) {
                title = "";
            }
            int titleX = x + 30;
            int reservedForTags = 100;
            int rightLimit = scrollBar.getBarX() - 2;
            int maxTitleWidth = rightLimit - reservedForTags - titleX;
            String truncatedTitle = trimWithEllipsis(textRenderer, title, maxTitleWidth);
            context.drawString(textRenderer, Component.nullToEmpty(truncatedTitle),
                    titleX, taskY + (taskItemHeight - textRenderer.lineHeight) / 2,
                    textColor, false);

            if (width > 150) {
                int rightForTags = scrollBar.getBarX() - 2;
                int tagAreaWidth = 90;
                int tagX = rightForTags - tagAreaWidth;
                List<String> tags = new ArrayList<>();
                for (String tag : task.getTags()) {
                    if (tag == null) continue;
                    String t = tag.trim();
                    if (!t.isEmpty()) {
                        tags.add(t);
                    }
                }
                String assigneeName = null;
                String assigneeUuid = task.getAssigneeUuid();
                if (assigneeUuid != null && !assigneeUuid.isEmpty()) {
                    if (client != null && client.getConnection() != null) {
                        java.util.Collection<net.minecraft.client.multiplayer.PlayerInfo> entries = client.getConnection().getOnlinePlayers();
                        for (net.minecraft.client.multiplayer.PlayerInfo entry : entries) {
                            if (assigneeUuid.equals(entry.getProfile().getId().toString())) {
                                String name = entry.getProfile().getName();
                                if (name != null && !name.isEmpty()) {
                                    assigneeName = name;
                                    task.setAssigneeName(name);
                                }
                                break;
                            }
                        }
                    }
                    if ((assigneeName == null || assigneeName.isEmpty()) && task.getAssigneeName() != null) {
                        assigneeName = task.getAssigneeName();
                    }
                }
                StringBuilder sb = new StringBuilder();
                if (assigneeName != null && !assigneeName.isEmpty()) {
                    sb.append("[").append(assigneeName).append("]");
                }
                for (String tag : tags) {
                    if (sb.length() > 0) {
                        sb.append(" ");
                    }
                    sb.append("[").append(tag).append("]");
                }
                
                String display = sb.toString();
                if (!display.isEmpty()) {
                    int maxTagWidth = rightForTags - tagX;
                    String truncatedTag = trimWithEllipsis(textRenderer, display, maxTagWidth);
                    context.drawString(textRenderer, Component.nullToEmpty(truncatedTag),
                            tagX, taskY + (taskItemHeight - textRenderer.lineHeight) / 2,
                            0xFF55FFFF, false);
                }
            }
        }
    }

    private int getTaskBackgroundColor(int taskIndex, int taskY, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();

        if (teamAllViewForNonOp && client != null && client.player != null &&
                taskIndex >= 0 && taskIndex < tasks.size()) {
            Task task = tasks.get(taskIndex);
            String assignee = task.getAssigneeUuid();
            String uuid = client.player.getUUID().toString();
            if (assignee != null && assignee.equals(uuid)) {
                return 0xFF202020;
            }
        }

        if (taskIndex == selectedTaskIndex) {
            return config.getSelectedBackgroundColor();
        }

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

            // 妫€鏌ユ槸鍚︾偣鍑诲湪婊氬姩鏉′笂
            if (scrollBar.wasMouseOver()) {
                scrollBar.setIsDragging(true);
                return true;
            }

            // 妫€鏌ユ槸鍚︾偣鍑诲湪澶嶉€夋涓?
            int scrollOffset = scrollBar.getValue();
            int index = (int) ((mouseY - y) / taskItemHeight) + scrollOffset;
            if (index >= 0 && index < tasks.size()) {
                int taskY = y + (index - scrollOffset) * taskItemHeight;
                int checkboxX = x + 12;
                int checkboxY = taskY + (taskItemHeight - 12) / 2;

                // 妫€鏌ョ偣鍑绘槸鍚﹀湪澶嶉€夋鑼冨洿鍐?(12x12)
                if (mouseX >= checkboxX && mouseX < checkboxX + 12 &&
                    mouseY >= checkboxY && mouseY < checkboxY + 12) {
                    Task clickedTask = tasks.get(index);
                    if (!clickedTask.isCompleted()) {
                        if (onTaskToggleCompletion != null && !teamAllViewForNonOp) {
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
        // 婊氬姩鏉℃嫋鎷藉湪 render() 鏂规硶涓鐞?
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
        if (task == null) {
            clearSelection();
            return;
        }
        selectedTaskId = task.getId();
        syncSelectionIndex();
    }

    public void clearSelection() {
        selectedTaskIndex = -1;
        selectedTaskId = null;
    }

    private void syncSelectionIndex() {
        if (selectedTaskId == null || selectedTaskId.isEmpty() || tasks == null) {
            selectedTaskIndex = -1;
            return;
        }
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (t != null && selectedTaskId.equals(t.getId())) {
                selectedTaskIndex = i;
                return;
            }
        }
        selectedTaskIndex = -1;
    }

    public void ensureVisible(Task task) {
        if (task == null || task.getId() == null || task.getId().isEmpty()) {
            return;
        }
        selectedTaskId = task.getId();
        syncSelectionIndex();
        if (selectedTaskIndex >= 0) {
            ensureVisibleIndex(selectedTaskIndex);
        }
    }

    private void ensureVisibleIndex(int index) {
        if (index < 0 || tasks == null || tasks.isEmpty()) {
            return;
        }
        int visibleCount = Math.max(1, height / taskItemHeight);
        int currentTop = scrollBar.getValue();
        int currentBottom = currentTop + visibleCount - 1;
        if (index < currentTop) {
            scrollBar.setValue(index);
            return;
        }
        if (index > currentBottom) {
            scrollBar.setValue(index - visibleCount + 1);
        }
    }

    private String trimWithEllipsis(Font textRenderer, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (maxWidth <= 0) {
            return "...";
        }
        if (textRenderer.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = textRenderer.width("...");
        int coreWidth = maxWidth - ellipsisWidth;
        if (coreWidth <= 0) {
            return "...";
        }
        String core = textRenderer.plainSubstrByWidth(text, coreWidth);
        return core + "...";
    }
}


