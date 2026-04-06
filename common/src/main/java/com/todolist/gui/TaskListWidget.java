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
    /**
     * 任务列表中的行类型，用于区分分组标题与任务行。
     */
    public enum RowType {
        SECTION_HEADER,
        TASK
    }

    /**
     * 任务分段模型，供主界面按“未完成 / 已完成”等结构组织任务区。
     */
    public static final class SectionModel {
        private final String id;
        private final String title;
        private final List<Task> tasks;
        private final boolean expandable;
        private final boolean expanded;

        /**
         * 创建一个任务分段模型。
         *
         * @param id 分段 ID
         * @param title 分段标题
         * @param tasks 分段中的任务列表
         * @param expandable 是否可折叠
         * @param expanded 当前是否展开
         */
        public SectionModel(String id, String title, List<Task> tasks, boolean expandable, boolean expanded) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.tasks = tasks == null ? List.of() : List.copyOf(tasks);
            this.expandable = expandable;
            this.expanded = expanded;
        }

        /**
         * 返回分段 ID。
         *
         * @return 分段 ID
         */
        public String getId() {
            return id;
        }

        /**
         * 返回分段标题。
         *
         * @return 分段标题
         */
        public String getTitle() {
            return title;
        }

        /**
         * 返回分段中的任务列表。
         *
         * @return 分段任务列表
         */
        public List<Task> getTasks() {
            return tasks;
        }

        /**
         * 返回当前分段是否支持折叠。
         *
         * @return true 表示支持折叠
         */
        public boolean isExpandable() {
            return expandable;
        }

        /**
         * 返回当前分段是否处于展开状态。
         *
         * @return true 表示当前分段已展开
         */
        public boolean isExpanded() {
            return expanded;
        }
    }

    /**
     * 坐标命中后的分段结果，供界面决定后续交互。
     */
    public static final class TaskSectionHitResult {
        private final RowType rowType;
        private final String sectionId;
        private final String sectionTitle;
        private final Task task;

        /**
         * 创建一次任务区命中结果。
         *
         * @param rowType 行类型
         * @param sectionId 分段 ID
         * @param sectionTitle 分段标题
         * @param task 命中的任务；若命中标题行则为空
         */
        public TaskSectionHitResult(RowType rowType, String sectionId, String sectionTitle, Task task) {
            this.rowType = rowType;
            this.sectionId = sectionId == null ? "" : sectionId;
            this.sectionTitle = sectionTitle == null ? "" : sectionTitle;
            this.task = task;
        }

        /**
         * 返回当前命中的行类型。
         *
         * @return 当前命中的行类型
         */
        public RowType getRowType() {
            return rowType;
        }

        /**
         * 返回当前命中的分段 ID。
         *
         * @return 当前命中的分段 ID
         */
        public String getSectionId() {
            return sectionId;
        }

        /**
         * 返回当前命中的分段标题。
         *
         * @return 当前命中的分段标题
         */
        public String getSectionTitle() {
            return sectionTitle;
        }

        /**
         * 返回当前命中的任务对象。
         *
         * @return 当前命中的任务；命中标题行时返回 null
         */
        public Task getTask() {
            return task;
        }
    }

    /**
     * 内部渲染行模型，统一承载标题行与任务行。
     */
    private static final class DisplayRow {
        private final RowType rowType;
        private final String sectionId;
        private final String sectionTitle;
        private final Task task;

        /**
         * 创建一行渲染数据。
         *
         * @param rowType 行类型
         * @param sectionId 分段 ID
         * @param sectionTitle 分段标题
         * @param task 当前行任务
         */
        private DisplayRow(RowType rowType, String sectionId, String sectionTitle, Task task) {
            this.rowType = rowType;
            this.sectionId = sectionId;
            this.sectionTitle = sectionTitle;
            this.task = task;
        }
    }

    private final Minecraft client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private List<Task> tasks = new ArrayList<>();
    private List<SectionModel> sections = new ArrayList<>();
    private List<DisplayRow> displayRows = new ArrayList<>();
    private final ScrollBar scrollBar;
    private int hoveredTaskIndex = -1;
    private int selectedTaskIndex = -1;
    private String selectedTaskId;
    private int taskItemHeight;
    private boolean teamAllViewForNonOp;
    private boolean taskReorderEnabled = true;
    private Consumer<Task> onTaskToggleCompletion;
    private Consumer<List<Task>> onTaskReorder;
    private Task pendingDragTask;
    private String pendingDragSectionId;
    private double pendingDragStartX;
    private double pendingDragStartY;
    private Task draggedTask;
    private String draggedTaskSectionId;
    private int dragTargetIndex = -1;

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
        // 创建独立的滚动条组件（宽度 10px）
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
        clearPendingTaskDrag();
        clearTaskDragState();
        this.tasks = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        this.sections = List.of(new SectionModel("default", "", this.tasks, false, true));
        rebuildDisplayRows(previousScroll);
    }

    /**
     * 设置任务分段列表，供界面按分组结构渲染任务区。
     *
     * @param sections 任务分段列表
     */
    public void setSections(List<SectionModel> sections) {
        int previousScroll = this.scrollBar.getValue();
        clearPendingTaskDrag();
        clearTaskDragState();
        this.sections = sections == null ? new ArrayList<>() : new ArrayList<>(sections);
        List<Task> mergedTasks = new ArrayList<>();
        for (SectionModel section : this.sections) {
            if (section == null) {
                continue;
            }
            mergedTasks.addAll(section.tasks);
        }
        this.tasks = mergedTasks;
        rebuildDisplayRows(previousScroll);
    }

    private void updateMaxScroll() {
        int visibleCount = Math.max(1, height / Math.max(1, taskItemHeight));
        int maxScroll = Math.max(0, displayRows.size() - visibleCount);
        scrollBar.setMaxValue(maxScroll);
    }

    /**
     * 设置任务“切换完成状态”的回调。
     */
    public void setOnTaskToggleCompletion(Consumer<Task> callback) {
        this.onTaskToggleCompletion = callback;
    }

    /**
     * 设置任务拖拽重排回调。
     *
     * @param callback 拖拽完成后的重排结果回调
     */
    public void setOnTaskReorder(Consumer<List<Task>> callback) {
        this.onTaskReorder = callback;
    }

    /**
     * 设置当前列表是否允许发起任务拖拽重排。
     *
     * @param enabled true 表示允许拖拽排序
     */
    public void setTaskReorderEnabled(boolean enabled) {
        this.taskReorderEnabled = enabled;
        if (!enabled) {
            clearPendingTaskDrag();
            clearTaskDragState();
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();

        // 绘制背景
        context.fill(x, y, x + width, y + height, config.getBackgroundColor());
        context.renderOutline(x, y, width, height, config.getBorderColor());

        // 绘制任务
        renderTasks(context, mouseX, mouseY);

        // 绘制滚动条（如果需要）
        int totalContentHeight = displayRows.size() * taskItemHeight;
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

    private void renderTasks(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY) {
        Font textRenderer = client.font;
        ModConfig config = ModConfig.getInstance();
        int scrollOffset = scrollBar.getValue();
        int visibleCount = Math.max(1, height / Math.max(1, taskItemHeight));
        int visibleTasks = Math.min(displayRows.size() - scrollOffset, visibleCount);

        for (int i = 0; i < visibleTasks; i++) {
            int rowIndex = i + scrollOffset;
            if (rowIndex >= displayRows.size()) break;

            DisplayRow row = displayRows.get(rowIndex);
            int rowY = y + i * taskItemHeight;

            if (row.rowType == RowType.SECTION_HEADER) {
                renderSectionHeader(context, textRenderer, row, rowIndex, rowY, mouseX, mouseY);
            } else if (row.task != null) {
                renderTaskRow(context, textRenderer, config, row.task, rowIndex, rowY, mouseX, mouseY);
            }
        }

        renderDragIndicator(context);
    }

    /**
     * 渲染分组标题行。
     */
    private void renderSectionHeader(net.minecraft.client.gui.GuiGraphics context, Font textRenderer, DisplayRow row,
                                     int rowIndex, int rowY, int mouseX, int mouseY) {
        int bgColor = rowIndex == selectedTaskIndex ? 0xFF252525 : 0xFF161616;
        if (mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + taskItemHeight) {
            bgColor = 0xFF202020;
        }
        context.fill(x + 1, rowY, x + width - 1, rowY + taskItemHeight - 1, bgColor);
        context.drawString(textRenderer, Component.nullToEmpty(row.sectionTitle),
                x + 8, rowY + (taskItemHeight - textRenderer.lineHeight) / 2,
                0xFFAAAAAA, false);
    }

    /**
     * 渲染任务行。
     */
    private void renderTaskRow(net.minecraft.client.gui.GuiGraphics context, Font textRenderer, ModConfig config,
                               Task task, int rowIndex, int taskY, int mouseX, int mouseY) {
        int bgColor = getTaskBackgroundColor(rowIndex, taskY, mouseX, mouseY);
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
        int maxTitleWidth = Math.max(16, rightLimit - reservedForTags - titleX);
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

    private int getTaskBackgroundColor(int taskIndex, int taskY, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();

        if (teamAllViewForNonOp && client != null && client.player != null &&
                taskIndex >= 0 && taskIndex < displayRows.size()) {
            DisplayRow row = displayRows.get(taskIndex);
            Task task = row.task;
            if (task == null) {
                return 0xFF1A1A1A;
            }
            String assignee = task.getAssigneeUuid();
            String uuid = client.player.getUUID().toString();
            if (assignee != null && assignee.equals(uuid)) {
                return 0xFF202020;
            }
        }

        if (draggedTask != null && taskIndex >= 0 && taskIndex < displayRows.size()) {
            DisplayRow row = displayRows.get(taskIndex);
            if (row.task != null && draggedTask.getId().equals(row.task.getId())) {
                return 0xFF303030;
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

    /**
     * 渲染拖拽中的插入指示线，帮助用户预览落点。
     *
     * @param context 当前绘制上下文
     */
    private void renderDragIndicator(net.minecraft.client.gui.GuiGraphics context) {
        if (draggedTask == null || dragTargetIndex < 0) {
            return;
        }
        List<Integer> rowIndexes = collectDraggableRowIndexes(draggedTaskSectionId);
        if (rowIndexes.isEmpty()) {
            return;
        }
        int clampedIndex = Math.max(0, Math.min(dragTargetIndex, rowIndexes.size()));
        int lineY;
        if (clampedIndex >= rowIndexes.size()) {
            int lastRowIndex = rowIndexes.get(rowIndexes.size() - 1);
            lineY = y + (lastRowIndex - scrollBar.getValue() + 1) * taskItemHeight;
        } else {
            lineY = y + (rowIndexes.get(clampedIndex) - scrollBar.getValue()) * taskItemHeight;
        }
        if (lineY < y || lineY > y + height) {
            return;
        }
        int indicatorLeft = x + 8;
        int indicatorRight = scrollBar.getBarX() - 4;
        context.fill(indicatorLeft, lineY - 1, indicatorRight, lineY + 1, 0xFF55FFFF);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        clearPendingTaskDrag();
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + height) {
            boolean clickOnScrollBar = button == 0
                    && scrollBar.getMaxValue() > 0
                    && mouseX >= scrollBar.getBarX() && mouseX < scrollBar.getBarX() + scrollBar.getBarWidth()
                    && mouseY >= scrollBar.getBarY() && mouseY < scrollBar.getBarY() + scrollBar.getBarHeight();
            if (clickOnScrollBar) {
                scrollBar.setIsDragging(true);
                return true;
            }

            // 检查是否点击在复选框上
            int scrollOffset = scrollBar.getValue();
            int index = (int) ((mouseY - y) / taskItemHeight) + scrollOffset;
            if (index >= 0 && index < displayRows.size()) {
                DisplayRow row = displayRows.get(index);
                if (row.rowType != RowType.TASK || row.task == null) {
                    return false;
                }
                int taskY = y + (index - scrollOffset) * taskItemHeight;
                int checkboxX = x + 12;
                int checkboxY = taskY + (taskItemHeight - 12) / 2;

                // 检查点击是否在复选框范围内 (12x12)
                if (mouseX >= checkboxX && mouseX < checkboxX + 12 &&
                    mouseY >= checkboxY && mouseY < checkboxY + 12) {
                    Task clickedTask = row.task;
                    if (!clickedTask.isCompleted()) {
                        if (onTaskToggleCompletion != null && !teamAllViewForNonOp) {
                            onTaskToggleCompletion.accept(clickedTask);
                        }
                    }
                    return true;
                }
                if (button == 0 && canHandleTaskReorder() && canStartDrag(row.task)) {
                    pendingDragTask = row.task;
                    pendingDragSectionId = row.sectionId;
                    pendingDragStartX = mouseX;
                    pendingDragStartY = mouseY;
                }
            }

            return false;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // 滚动条拖拽在 render() 方法中处理
        if (scrollBar.isDragging()) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (draggedTask != null) {
            updateTaskDrag(mouseX, mouseY);
            return true;
        }
        if (pendingDragTask == null || pendingDragSectionId == null) {
            return false;
        }
        double deltaXValue = mouseX - pendingDragStartX;
        double deltaYValue = mouseY - pendingDragStartY;
        double distanceSquared = deltaXValue * deltaXValue + deltaYValue * deltaYValue;
        if (distanceSquared < 9.0D) {
            return false;
        }
        beginTaskDrag(pendingDragTask, mouseX, mouseY);
        updateTaskDrag(mouseX, mouseY);
        clearPendingTaskDrag();
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        if (button == 0) {
            if (scrollBar.isDragging()) {
                scrollBar.setIsDragging(false);
                handled = true;
            }
            if (draggedTask != null) {
                finishTaskDrag();
                handled = true;
            }
            clearPendingTaskDrag();
        }
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + height) {
            if (verticalAmount == 0 || scrollBar.getMaxValue() <= 0) {
                return false;
            }
            int before = scrollBar.getValue();
            scrollBar.offsetValue(verticalAmount > 0 ? -1 : 1);
            return scrollBar.getValue() != before;
        }
        return false;
    }

    public Task getTaskAt(int x, int y) {
        if (x >= this.x && x < this.x + width &&
            y >= this.y && y < this.y + height) {

            int scrollOffset = scrollBar.getValue();
            int index = (y - this.y) / taskItemHeight + scrollOffset;
            if (index >= 0 && index < displayRows.size()) {
                DisplayRow row = displayRows.get(index);
                if (row.rowType == RowType.TASK) {
                    return row.task;
                }
            }
        }
        return null;
    }

    /**
     * 返回指定坐标命中的任务分段信息。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @return 命中的分段信息；未命中时返回 null
     */
    public TaskSectionHitResult getSectionAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return null;
        }
        int rowIndex = (int) ((mouseY - y) / taskItemHeight) + scrollBar.getValue();
        if (rowIndex < 0 || rowIndex >= displayRows.size()) {
            return null;
        }
        DisplayRow row = displayRows.get(rowIndex);
        return new TaskSectionHitResult(row.rowType, row.sectionId, row.sectionTitle, row.task);
    }

    public List<Task> getTasks() {
        return tasks;
    }

    /**
     * 返回当前滚动偏移量，供同包测试代码断言滚动行为。
     *
     * @return 当前滚动偏移量
     */
    int getScrollOffsetForTest() {
        return scrollBar.getValue();
    }

    /**
     * 返回当前选中任务 ID，供同包测试代码断言选择保持逻辑。
     *
     * @return 当前选中任务 ID；未选中时返回 null
     */
    String getSelectedTaskIdForTest() {
        return selectedTaskId;
    }

    /**
     * 返回当前是否使用优先级色块，供同包测试代码断言视觉语义。
     *
     * @param taskId 任务 ID
     * @return true 表示该任务行会绘制优先级色块
     */
    boolean hasPriorityColorBlockForTaskForTest(String taskId) {
        for (DisplayRow row : displayRows) {
            if (row.rowType == RowType.TASK && row.task != null && taskId.equals(row.task.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断给定任务是否允许作为拖拽起点。
     *
     * @param task 目标任务
     * @return true 表示该任务允许开始拖拽
     */
    boolean canStartDrag(Task task) {
        return task != null && !task.isCompleted();
    }

    /**
     * 初始化一次任务拖拽会话。
     *
     * @param task 被拖拽的任务
     * @param mouseX 当前鼠标 X 坐标
     * @param mouseY 当前鼠标 Y 坐标
     */
    void beginTaskDrag(Task task, double mouseX, double mouseY) {
        if (!canHandleTaskReorder() || !canStartDrag(task)) {
            return;
        }
        draggedTask = task;
        draggedTaskSectionId = pendingDragSectionId;
        dragTargetIndex = resolveDropIndex(mouseY);
    }

    /**
     * 更新拖拽过程中的落点和自动滚动状态。
     *
     * @param mouseX 当前鼠标 X 坐标
     * @param mouseY 当前鼠标 Y 坐标
     */
    void updateTaskDrag(double mouseX, double mouseY) {
        if (draggedTask == null) {
            return;
        }
        autoScrollWhileDragging(mouseY);
        dragTargetIndex = resolveDropIndex(mouseY);
    }

    /**
     * 结束当前拖拽会话，并在顺序变化时触发回调。
     */
    void finishTaskDrag() {
        if (draggedTask == null) {
            return;
        }
        List<Task> sectionTasks = getDraggableTasksForSection(draggedTaskSectionId);
        List<Task> reorderedTasks = reorderActiveTasks(sectionTasks, draggedTask.getId(), dragTargetIndex);
        boolean changed = !sameTaskOrder(sectionTasks, reorderedTasks);
        if (changed) {
            applySectionTaskOrder(draggedTaskSectionId, reorderedTasks);
            if (onTaskReorder != null) {
                onTaskReorder.accept(List.copyOf(reorderedTasks));
            }
        }
        clearTaskDragState();
    }

    /**
     * 根据鼠标纵坐标解析当前拖拽应插入到的目标索引。
     *
     * @param mouseY 当前鼠标 Y 坐标
     * @return 当前拖拽目标索引
     */
    int resolveDropIndex(double mouseY) {
        List<Integer> rowIndexes = collectDraggableRowIndexes(draggedTaskSectionId);
        if (rowIndexes.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < rowIndexes.size(); index++) {
            int rowIndex = rowIndexes.get(index);
            double rowMiddleY = y + (rowIndex - scrollBar.getValue()) * taskItemHeight + taskItemHeight / 2.0D;
            if (mouseY < rowMiddleY) {
                return index;
            }
        }
        return rowIndexes.size();
    }

    /**
     * 计算拖拽后当前可见未完成任务的新顺序。
     *
     * @param tasks 当前可见未完成任务列表
     * @param draggedTaskId 被拖拽任务 ID
     * @param targetIndex 目标插入索引
     * @return 重排后的任务列表
     */
    List<Task> reorderActiveTasks(List<Task> tasks, String draggedTaskId, int targetIndex) {
        List<Task> input = tasks == null ? new ArrayList<>() : new ArrayList<>(tasks);
        if (draggedTaskId == null || input.size() < 2) {
            return input;
        }
        int sourceIndex = -1;
        Task dragged = null;
        for (int index = 0; index < input.size(); index++) {
            Task candidate = input.get(index);
            if (candidate != null && draggedTaskId.equals(candidate.getId())) {
                sourceIndex = index;
                dragged = candidate;
                break;
            }
        }
        if (sourceIndex < 0 || dragged == null) {
            return input;
        }
        int insertIndex = Math.max(0, Math.min(targetIndex, input.size()));
        input.remove(sourceIndex);
        if (insertIndex > sourceIndex) {
            insertIndex--;
        }
        insertIndex = Math.max(0, Math.min(insertIndex, input.size()));
        input.add(insertIndex, dragged);
        return input;
    }

    /**
     * 判断当前是否满足处理拖拽重排的基础条件。
     *
     * @return true 表示当前允许处理拖拽重排
     */
    private boolean canHandleTaskReorder() {
        return taskReorderEnabled && onTaskReorder != null;
    }

    /**
     * 清理尚未进入正式拖拽态的候选拖拽信息。
     */
    private void clearPendingTaskDrag() {
        pendingDragTask = null;
        pendingDragSectionId = null;
        pendingDragStartX = 0.0D;
        pendingDragStartY = 0.0D;
    }

    /**
     * 清理当前拖拽态及其落点缓存。
     */
    private void clearTaskDragState() {
        draggedTask = null;
        draggedTaskSectionId = null;
        dragTargetIndex = -1;
    }

    /**
     * 在拖拽靠近列表边缘时自动调整滚动偏移。
     *
     * @param mouseY 当前鼠标 Y 坐标
     */
    private void autoScrollWhileDragging(double mouseY) {
        if (scrollBar.getMaxValue() <= 0) {
            return;
        }
        int edgePadding = Math.max(8, taskItemHeight / 2);
        if (mouseY <= y + edgePadding) {
            scrollBar.offsetValue(-1);
        } else if (mouseY >= y + height - edgePadding) {
            scrollBar.offsetValue(1);
        }
    }

    /**
     * 收集当前拖拽分段内所有可拖拽任务行的显示索引。
     *
     * @param sectionId 目标分段 ID
     * @return 可拖拽任务行索引列表
     */
    private List<Integer> collectDraggableRowIndexes(String sectionId) {
        List<Integer> rowIndexes = new ArrayList<>();
        if (sectionId == null || displayRows == null) {
            return rowIndexes;
        }
        for (int index = 0; index < displayRows.size(); index++) {
            DisplayRow row = displayRows.get(index);
            if (row.rowType == RowType.TASK
                    && row.task != null
                    && sectionId.equals(row.sectionId)
                    && canStartDrag(row.task)) {
                rowIndexes.add(index);
            }
        }
        return rowIndexes;
    }

    /**
     * 返回指定分段内当前可拖拽的任务列表。
     *
     * @param sectionId 分段 ID
     * @return 当前可拖拽的任务列表
     */
    private List<Task> getDraggableTasksForSection(String sectionId) {
        List<Task> sectionTasks = new ArrayList<>();
        if (sectionId == null || sections == null) {
            return sectionTasks;
        }
        for (SectionModel section : sections) {
            if (section == null || !sectionId.equals(section.id)) {
                continue;
            }
            for (Task task : section.tasks) {
                if (canStartDrag(task)) {
                    sectionTasks.add(task);
                }
            }
            break;
        }
        return sectionTasks;
    }

    /**
     * 将重排结果写回当前分段模型，保证控件内部快照立即同步。
     *
     * @param sectionId 分段 ID
     * @param reorderedTasks 重排后的任务列表
     */
    private void applySectionTaskOrder(String sectionId, List<Task> reorderedTasks) {
        if (sectionId == null || reorderedTasks == null) {
            return;
        }
        List<SectionModel> updatedSections = new ArrayList<>();
        for (SectionModel section : sections) {
            if (section == null) {
                continue;
            }
            if (!sectionId.equals(section.id)) {
                updatedSections.add(section);
                continue;
            }
            List<Task> mergedSectionTasks = new ArrayList<>();
            int reorderedIndex = 0;
            for (Task task : section.tasks) {
                if (canStartDrag(task) && reorderedIndex < reorderedTasks.size()) {
                    mergedSectionTasks.add(reorderedTasks.get(reorderedIndex++));
                } else {
                    mergedSectionTasks.add(task);
                }
            }
            updatedSections.add(new SectionModel(section.id, section.title, mergedSectionTasks, section.expandable, section.expanded));
        }
        int previousScroll = scrollBar.getValue();
        this.sections = updatedSections;
        List<Task> mergedTasks = new ArrayList<>();
        for (SectionModel section : this.sections) {
            if (section != null) {
                mergedTasks.addAll(section.tasks);
            }
        }
        this.tasks = mergedTasks;
        rebuildDisplayRows(previousScroll);
    }

    /**
     * 判断两组任务列表的顺序是否完全一致。
     *
     * @param before 调整前列表
     * @param after 调整后列表
     * @return true 表示顺序完全一致
     */
    private boolean sameTaskOrder(List<Task> before, List<Task> after) {
        if (before == null || after == null || before.size() != after.size()) {
            return false;
        }
        for (int index = 0; index < before.size(); index++) {
            Task beforeTask = before.get(index);
            Task afterTask = after.get(index);
            String beforeId = beforeTask == null ? null : beforeTask.getId();
            String afterId = afterTask == null ? null : afterTask.getId();
            if (!java.util.Objects.equals(beforeId, afterId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回当前渲染行快照，供同包测试代码断言分组顺序。
     *
     * @return 当前渲染行调试快照
     */
    List<String> getRowDebugSnapshotForTest() {
        List<String> snapshot = new ArrayList<>();
        for (DisplayRow row : displayRows) {
            if (row.rowType == RowType.SECTION_HEADER) {
                snapshot.add("HEADER:" + row.sectionTitle);
            } else if (row.task != null) {
                snapshot.add("TASK:" + row.task.getId());
            }
        }
        return List.copyOf(snapshot);
    }

    /**
     * 返回当前任务区是否可滚动，供同包测试代码断言滚动边界。
     *
     * @return true 表示当前任务区可滚动
     */
    boolean isTaskAreaScrollableForTest() {
        return scrollBar.getMaxValue() > 0;
    }

    /**
     * 返回当前任务行高度，供同包测试代码构造稳定的坐标命中输入。
     *
     * @return 当前任务行高度
     */
    int getTaskItemHeightForTest() {
        return taskItemHeight;
    }

    /**
     * 返回当前滚动区的最大偏移量，供同包测试代码断言滚动上限。
     *
     * @return 当前滚动区最大偏移量
     */
    int getMaxScrollOffsetForTest() {
        return scrollBar.getMaxValue();
    }

    /**
     * 返回当前是否处于任务拖拽中，供测试断言拖拽状态切换。
     *
     * @return true 表示当前存在活动中的任务拖拽
     */
    boolean isTaskDraggingForTest() {
        return draggedTask != null;
    }

    /**
     * 返回当前拖拽落点索引，供测试断言拖拽目标更新。
     *
     * @return 当前拖拽落点索引；未拖拽时返回 -1
     */
    int getDropTargetIndexForTest() {
        return dragTargetIndex;
    }

    /**
     * 返回任务列表内部可用于交互的横坐标，供测试稳定构造点击和拖拽输入。
     *
     * @return 任务内容区域内的测试用横坐标
     */
    int getInteractXForTest() {
        return x + 30;
    }

    /**
     * 返回指定任务当前可见行的中心纵坐标，供测试稳定命中任务行。
     *
     * @param taskId 任务 ID
     * @return 当前任务行中心纵坐标；未命中时返回 -1
     */
    int getTaskRowCenterYForTest(String taskId) {
        if (taskId == null || displayRows == null) {
            return -1;
        }
        for (int index = 0; index < displayRows.size(); index++) {
            DisplayRow row = displayRows.get(index);
            if (row.rowType == RowType.TASK && row.task != null && taskId.equals(row.task.getId())) {
                int visibleRowIndex = index - scrollBar.getValue();
                return y + visibleRowIndex * taskItemHeight + taskItemHeight / 2;
            }
        }
        return -1;
    }

    /**
     * 直接设置当前滚动偏移，供测试覆盖拖拽自动滚动边界。
     *
     * @param offset 目标滚动偏移
     */
    void setScrollOffsetForTest(int offset) {
        scrollBar.setValue(offset);
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
        if (selectedTaskId == null || selectedTaskId.isEmpty() || displayRows == null) {
            selectedTaskIndex = -1;
            return;
        }
        for (int i = 0; i < displayRows.size(); i++) {
            DisplayRow row = displayRows.get(i);
            Task t = row.task;
            if (row.rowType == RowType.TASK && t != null && selectedTaskId.equals(t.getId())) {
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
        if (index < 0 || displayRows == null || displayRows.isEmpty()) {
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

    /**
     * 重新构建显示行，并尽量保留当前滚动与选中态。
     *
     * @param previousScroll 旧的滚动偏移量
     */
    private void rebuildDisplayRows(int previousScroll) {
        rebuildDisplayRowsFromSections();
        updateMaxScroll();
        syncSelectionIndex();
        if (selectedTaskIndex >= 0) {
            ensureVisibleIndex(selectedTaskIndex);
        } else {
            this.scrollBar.setValue(previousScroll);
        }
    }

    /**
     * 根据分段模型重新构建渲染行。
     */
    private void rebuildDisplayRowsFromSections() {
        List<DisplayRow> rows = new ArrayList<>();
        if (sections == null || sections.isEmpty()) {
            this.displayRows = rows;
            return;
        }
        for (SectionModel section : sections) {
            if (section == null) {
                continue;
            }
            boolean showHeader = !section.title.isEmpty() || section.expandable;
            if (showHeader) {
                rows.add(new DisplayRow(RowType.SECTION_HEADER, section.id, section.title, null));
            }
            if (!section.expandable || section.expanded) {
                for (Task task : section.tasks) {
                    if (task != null) {
                        rows.add(new DisplayRow(RowType.TASK, section.id, section.title, task));
                    }
                }
            }
        }
        this.displayRows = rows;
    }
}
