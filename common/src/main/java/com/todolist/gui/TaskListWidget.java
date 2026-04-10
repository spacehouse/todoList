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
    private static final int MIN_TASK_ITEM_HEIGHT = 28;
    private static final int SECTION_HEADER_ROW_HEIGHT = 16;
    private static final int SECTION_HEADER_LEFT_PADDING = 12;
    private static final int TASK_ROW_LEFT_PADDING = 2;
    private static final int TASK_ROW_HORIZONTAL_PADDING = 12;
    private static final int TASK_ROW_VERTICAL_PADDING = 5;
    private static final int TASK_PRIORITY_BAR_WIDTH = 4;
    private static final int TASK_CHECKBOX_SIZE = 12;
    private static final int TASK_CONTENT_GAP = 6;
    private static final int TASK_META_GAP = 6;
    private static final int TASK_TITLE_MIN_WIDTH = 40;
    private static final int TASK_LEADING_META_MAX_WIDTH = 96;
    private static final int TASK_TRAILING_META_MIN_WIDTH = 48;
    private static final int TASK_TRAILING_META_MAX_WIDTH = 96;
    private static final int TASK_META_TEXT_COLOR = 0xFF55FFFF;

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
        private final boolean sectionExpandable;
        private final boolean sectionExpanded;
        private final Task task;

        /**
         * 创建一行渲染数据。
         *
         * @param rowType 行类型
         * @param sectionId 分段 ID
         * @param sectionTitle 分段标题
         * @param task 当前行任务
         */
        private DisplayRow(RowType rowType, String sectionId, String sectionTitle,
                           boolean sectionExpandable, boolean sectionExpanded, Task task) {
            this.rowType = rowType;
            this.sectionId = sectionId;
            this.sectionTitle = sectionTitle;
            this.sectionExpandable = sectionExpandable;
            this.sectionExpanded = sectionExpanded;
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
    private List<Integer> rowHeights = new ArrayList<>();
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
        this.taskItemHeight = Math.max(MIN_TASK_ITEM_HEIGHT, ModConfig.getInstance().getTaskItemHeight());
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
        if (displayRows.isEmpty()) {
            scrollBar.setMaxValue(0);
            return;
        }
        int accumulatedHeight = 0;
        int maxScroll = 0;
        for (int index = displayRows.size() - 1; index >= 0; index--) {
            int rowHeight = getRowHeight(index);
            if (accumulatedHeight + rowHeight > height) {
                maxScroll = Math.min(displayRows.size() - 1, index + 1);
                break;
            }
            accumulatedHeight += rowHeight;
            maxScroll = index;
        }
        scrollBar.setMaxValue(Math.max(0, maxScroll));
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

        // 将任务内容限制在列表矩形内部，避免末行覆盖到底部输入区域。
        context.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        renderTasks(context, mouseX, mouseY);
        context.disableScissor();

        // 绘制滚动条（如果需要）
        int totalContentHeight = getTotalContentHeight();
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
        int rowY = y;

        for (int rowIndex = scrollOffset; rowIndex < displayRows.size() && rowY < y + height; rowIndex++) {
            DisplayRow row = displayRows.get(rowIndex);
            int rowHeight = getRowHeight(rowIndex);

            if (row.rowType == RowType.SECTION_HEADER) {
                renderSectionHeader(context, textRenderer, row, rowIndex, rowY, rowHeight, mouseX, mouseY);
            } else if (row.task != null) {
                renderTaskRow(context, textRenderer, config, row.task, rowIndex, rowY, rowHeight, mouseX, mouseY);
            }
            rowY += rowHeight;
        }

        renderDragIndicator(context);
    }

    /**
     * 渲染分组标题行。
     */
    private void renderSectionHeader(net.minecraft.client.gui.GuiGraphics context, Font textRenderer, DisplayRow row,
                                     int rowIndex, int rowY, int rowHeight, int mouseX, int mouseY) {
        int bgColor = rowIndex == selectedTaskIndex ? 0xFF252525 : 0xFF161616;
        if (mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + rowHeight) {
            bgColor = 0xFF202020;
        }
        context.fill(x + 1, rowY, x + width - 1, rowY + rowHeight, bgColor);
        context.drawString(textRenderer, Component.nullToEmpty(buildSectionHeaderText(row)),
                x + SECTION_HEADER_LEFT_PADDING, rowY + (rowHeight - textRenderer.lineHeight) / 2,
                0xFFAAAAAA, false);
    }

    /**
     * 构建分组标题的展示文本，统一包含展开/收起标记。
     *
     * @param row 分组标题行
     * @return 用于渲染和测试断言的标题文本
     */
    private String buildSectionHeaderText(DisplayRow row) {
        if (row == null) {
            return "";
        }
        if (!row.sectionExpandable) {
            return row.sectionTitle == null ? "" : row.sectionTitle;
        }
        String prefix = row.sectionExpanded ? "v " : "> ";
        return prefix + (row.sectionTitle == null ? "" : row.sectionTitle);
    }

    /**
     * 渲染任务行。
     */
    private void renderTaskRow(net.minecraft.client.gui.GuiGraphics context, Font textRenderer, ModConfig config,
                               Task task, int rowIndex, int taskY, int rowHeight, int mouseX, int mouseY) {
        int bgColor = getTaskBackgroundColor(rowIndex, taskY, rowHeight, mouseX, mouseY);
        int priorityColor = task.getPriority().getColor();
        int textColor = task.isCompleted() ? 0xFF888888 : 0xFFFFFFFF;
        int textBaselineY = taskY + (rowHeight - textRenderer.lineHeight) / 2;
        int rowTopInset = taskY + TASK_ROW_VERTICAL_PADDING;
        int rowBottomInset = taskY + rowHeight - TASK_ROW_VERTICAL_PADDING;

        context.fill(x + 1, taskY, x + width - 1, taskY + rowHeight - 1, bgColor);

        int priorityLeft = x + TASK_ROW_LEFT_PADDING;
        context.fill(priorityLeft, rowTopInset, priorityLeft + TASK_PRIORITY_BAR_WIDTH, rowBottomInset, priorityColor);

        int checkboxX = priorityLeft + TASK_PRIORITY_BAR_WIDTH + TASK_CONTENT_GAP;
        int checkboxY = taskY + (rowHeight - TASK_CHECKBOX_SIZE) / 2;
        context.fill(checkboxX, checkboxY, checkboxX + TASK_CHECKBOX_SIZE, checkboxY + TASK_CHECKBOX_SIZE, 0xFF000000);
        context.renderOutline(checkboxX, checkboxY, TASK_CHECKBOX_SIZE, TASK_CHECKBOX_SIZE, 0xFFFFFFFF);

        if (task.isCompleted()) {
            context.fill(checkboxX + 3, checkboxY + 5, checkboxX + 5, checkboxY + 7, 0xFF00FF00);
            context.fill(checkboxX + 5, checkboxY + 7, checkboxX + 9, checkboxY + 3, 0xFF00FF00);
        }

        String title = task.getTitle();
        if (title == null) {
            title = "";
        }
        int textLeft = checkboxX + TASK_CHECKBOX_SIZE + TASK_CONTENT_GAP;
        int rightLimit = scrollBar.getBarX() - TASK_ROW_HORIZONTAL_PADDING;
        String trailingMeta = buildTaskTrailingMetaText(task);
        int trailingMetaWidth = trailingMeta.isEmpty()
                ? 0
                : clampInt(textRenderer.width(trailingMeta), TASK_TRAILING_META_MIN_WIDTH, TASK_TRAILING_META_MAX_WIDTH);
        int contentRight = trailingMetaWidth > 0 ? rightLimit - trailingMetaWidth - TASK_META_GAP : rightLimit;

        String leadingMeta = buildTaskLeadingMetaText(task);
        int availableContentWidth = Math.max(TASK_TITLE_MIN_WIDTH, contentRight - textLeft);
        int leadingMetaWidth = 0;
        String truncatedLeadingMeta = "";
        if (!leadingMeta.isEmpty()) {
            int preferredLeadingWidth = Math.min(TASK_LEADING_META_MAX_WIDTH, Math.max(48, availableContentWidth / 3));
            int safeLeadingWidth = Math.max(0, availableContentWidth - TASK_TITLE_MIN_WIDTH - TASK_META_GAP);
            int maxLeadingWidth = Math.min(preferredLeadingWidth, safeLeadingWidth);
            if (maxLeadingWidth > 0) {
                truncatedLeadingMeta = trimWithEllipsis(textRenderer, leadingMeta, maxLeadingWidth);
                leadingMetaWidth = textRenderer.width(truncatedLeadingMeta);
                context.drawString(textRenderer, Component.nullToEmpty(truncatedLeadingMeta), textLeft, textBaselineY, TASK_META_TEXT_COLOR, false);
            }
        }

        int titleX = textLeft + (leadingMetaWidth > 0 ? leadingMetaWidth + TASK_META_GAP : 0);
        int maxTitleWidth = Math.max(TASK_TITLE_MIN_WIDTH, contentRight - titleX);
        String truncatedTitle = trimWithEllipsis(textRenderer, title, maxTitleWidth);
        context.drawString(textRenderer, Component.nullToEmpty(truncatedTitle), titleX, textBaselineY, textColor, false);

        if (!trailingMeta.isEmpty()) {
            int trailingTextWidth = Math.min(trailingMetaWidth, Math.max(0, rightLimit - titleX));
            String truncatedTrailingMeta = trimWithEllipsis(textRenderer, trailingMeta, trailingTextWidth);
            int trailingX = rightLimit - textRenderer.width(truncatedTrailingMeta);
            context.drawString(textRenderer, Component.nullToEmpty(truncatedTrailingMeta), trailingX, textBaselineY, TASK_META_TEXT_COLOR, false);
        }
    }

    private int getTaskBackgroundColor(int taskIndex, int taskY, int rowHeight, int mouseX, int mouseY) {
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
            mouseY >= taskY && mouseY < taskY + rowHeight) {
            hoveredTaskIndex = taskIndex;
            return config.getHoveredBackgroundColor();
        }
        return 0xFF1A1A1A;
    }

    /**
     * 构建任务标题前方显示的标签文本。
     *
     * @param task 目标任务
     * @return 标签文本；无标签时返回空字符串
     */
    private String buildTaskLeadingMetaText(Task task) {
        if (task == null || task.getTags().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String tag : task.getTags()) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append('[').append(tag.trim()).append(']');
        }
        return builder.toString();
    }

    /**
     * 构建任务标题右侧显示的负责人文本。
     *
     * @param task 目标任务
     * @return 负责人文本；无人负责时返回空字符串
     */
    private String buildTaskTrailingMetaText(Task task) {
        String assigneeName = resolveAssigneeName(task);
        return assigneeName.isEmpty() ? "" : "@" + assigneeName;
    }

    /**
     * 解析任务当前应展示的负责人名称。
     *
     * @param task 目标任务
     * @return 可展示的负责人名称；不存在时返回空字符串
     */
    private String resolveAssigneeName(Task task) {
        if (task == null) {
            return "";
        }
        String assigneeName = task.getAssigneeName();
        if (assigneeName != null && !assigneeName.isBlank()) {
            return assigneeName.trim();
        }
        String assigneeUuid = task.getAssigneeUuid();
        return assigneeUuid == null ? "" : assigneeUuid.trim();
    }

    /**
     * 将整数值限制在指定范围内。
     *
     * @param value 原始值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的结果
     */
    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
            lineY = getRowTopForVisibleIndex(lastRowIndex) + getRowHeight(lastRowIndex);
        } else {
            lineY = getRowTopForVisibleIndex(rowIndexes.get(clampedIndex));
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
            int index = findVisibleRowIndexAt(mouseY);
            if (index >= 0 && index < displayRows.size()) {
                DisplayRow row = displayRows.get(index);
                if (row.rowType != RowType.TASK || row.task == null) {
                    return false;
                }
                int taskY = getRowTopForVisibleIndex(index);
                int rowHeight = getRowHeight(index);
                int checkboxX = x + TASK_ROW_LEFT_PADDING + TASK_PRIORITY_BAR_WIDTH + TASK_CONTENT_GAP;
                int checkboxY = taskY + (rowHeight - TASK_CHECKBOX_SIZE) / 2;

                // 检查点击是否在复选框范围内 (12x12)
                if (mouseX >= checkboxX && mouseX < checkboxX + TASK_CHECKBOX_SIZE &&
                    mouseY >= checkboxY && mouseY < checkboxY + TASK_CHECKBOX_SIZE) {
                    Task clickedTask = row.task;
                    if (onTaskToggleCompletion != null && !teamAllViewForNonOp) {
                        onTaskToggleCompletion.accept(clickedTask);
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

            int index = findVisibleRowIndexAt(y);
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
        int rowIndex = findVisibleRowIndexAt(mouseY);
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
        return getScrollOffset();
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
     * 记录一次待启动的任务拖拽，供外层界面在自定义点击链路下显式同步拖拽起点。
     *
     * @param task 目标任务
     * @param sectionId 所属分段 ID
     * @param mouseX 鼠标按下时的 X 坐标
     * @param mouseY 鼠标按下时的 Y 坐标
     */
    void armPendingTaskDrag(Task task, String sectionId, double mouseX, double mouseY) {
        clearPendingTaskDrag();
        if (!canHandleTaskReorder() || !canStartDrag(task)) {
            return;
        }
        pendingDragTask = task;
        pendingDragSectionId = sectionId == null ? "" : sectionId;
        pendingDragStartX = mouseX;
        pendingDragStartY = mouseY;
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
            double rowMiddleY = getRowTopForVisibleIndex(rowIndex) + getRowHeight(rowIndex) / 2.0D;
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
                snapshot.add("HEADER:" + buildSectionHeaderText(row));
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

    int getSectionHeaderHeightForTest() {
        return Math.min(taskItemHeight, SECTION_HEADER_ROW_HEIGHT);
    }

    /**
     * 返回任务列表控件的边界，供界面测试校验布局。
     *
     * @return 任务列表控件边界数组
     */
    int[] getBoundsForTest() {
        return new int[] {x, y, width, height};
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
     * 返回任务内容区域内的测试用横坐标。
     *
     * @return 任务内容区域内的测试用横坐标
     */
    int getInteractXForTest() {
        return x + TASK_ROW_LEFT_PADDING + TASK_PRIORITY_BAR_WIDTH + TASK_CONTENT_GAP
                + TASK_CHECKBOX_SIZE + TASK_CONTENT_GAP + 4;
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
                return getRowTopForVisibleIndex(index) + getRowHeight(index) / 2;
            }
        }
        return -1;
    }

    int getSectionHeaderCenterYForTest(String sectionId) {
        if (sectionId == null || displayRows == null) {
            return -1;
        }
        for (int index = 0; index < displayRows.size(); index++) {
            DisplayRow row = displayRows.get(index);
            if (row.rowType == RowType.SECTION_HEADER && sectionId.equals(row.sectionId)) {
                return getRowTopForVisibleIndex(index) + getRowHeight(index) / 2;
            }
        }
        return -1;
    }

    /**
     * 返回指定任务当前用于前置展示的标签文本，供测试断言任务项布局语义。
     *
     * @param taskId 任务 ID
     * @return 前置标签文本；任务不存在时返回空字符串
     */
    /**
     * 返回任务复选框中心点的横坐标，供测试稳定命中完成态切换区域。
     *
     * @return 复选框中心点横坐标
     */
    int getCheckboxCenterXForTest() {
        return x + TASK_ROW_LEFT_PADDING + TASK_PRIORITY_BAR_WIDTH + TASK_CONTENT_GAP + TASK_CHECKBOX_SIZE / 2;
    }

    /**
     * 返回首行任务复选框的中心纵坐标，供测试稳定命中复选框点击区域。
     *
     * @return 复选框中心点纵坐标
     */
    int getCheckboxCenterYForTest() {
        for (int index = scrollBar.getValue(); index < displayRows.size(); index++) {
            DisplayRow row = displayRows.get(index);
            if (row.rowType == RowType.TASK) {
                int rowTop = getRowTopForVisibleIndex(index);
                return rowTop + (getRowHeight(index) - TASK_CHECKBOX_SIZE) / 2 + TASK_CHECKBOX_SIZE / 2;
            }
        }
        return y + (taskItemHeight - TASK_CHECKBOX_SIZE) / 2 + TASK_CHECKBOX_SIZE / 2;
    }

    /**
     * 返回指定任务当前用于前置展示的标签文本，供测试断言任务项布局语义。
     *
     * @param taskId 任务 ID
     * @return 前置标签文本；任务不存在时返回空字符串
     */
    String getTaskLeadingMetaTextForTest(String taskId) {
        Task task = findTaskById(taskId);
        return buildTaskLeadingMetaText(task);
    }

    /**
     * 根据任务 ID 查找当前列表中的任务对象。
     *
     * @param taskId 任务 ID
     * @return 匹配的任务对象；不存在时返回 null
     */
    private Task findTaskById(String taskId) {
        if (taskId == null || displayRows == null) {
            return null;
        }
        for (DisplayRow row : displayRows) {
            if (row.rowType == RowType.TASK && row.task != null && taskId.equals(row.task.getId())) {
                return row.task;
            }
        }
        return null;
    }

    /**
     * 直接设置当前滚动偏移，供测试覆盖拖拽自动滚动边界。
     *
     * @param offset 目标滚动偏移
     */
    private int getRowHeight(DisplayRow row) {
        if (row == null) {
            return taskItemHeight;
        }
        return row.rowType == RowType.SECTION_HEADER
                ? Math.min(taskItemHeight, SECTION_HEADER_ROW_HEIGHT)
                : taskItemHeight;
    }

    private int getRowHeight(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= displayRows.size()) {
            return taskItemHeight;
        }
        return rowHeights.get(rowIndex);
    }

    private int getTotalContentHeight() {
        int totalHeight = 0;
        for (int rowHeight : rowHeights) {
            totalHeight += rowHeight;
        }
        return totalHeight;
    }

    private int findVisibleRowIndexAt(double mouseY) {
        if (mouseY < y || mouseY >= y + height) {
            return -1;
        }
        int currentY = y;
        for (int rowIndex = scrollBar.getValue(); rowIndex < displayRows.size() && currentY < y + height; rowIndex++) {
            int rowHeight = getRowHeight(rowIndex);
            if (mouseY >= currentY && mouseY < currentY + rowHeight) {
                return rowIndex;
            }
            currentY += rowHeight;
        }
        return -1;
    }

    private int getRowTopForVisibleIndex(int rowIndex) {
        int currentY = y;
        for (int index = scrollBar.getValue(); index < rowIndex && index < displayRows.size(); index++) {
            currentY += getRowHeight(index);
        }
        return currentY;
    }

    private int getVisibleBottomIndex(int startIndex) {
        int currentY = y;
        int bottomIndex = Math.max(0, Math.min(startIndex, displayRows.size() - 1));
        for (int index = startIndex; index < displayRows.size() && currentY < y + height; index++) {
            int rowHeight = getRowHeight(index);
            if (currentY + rowHeight > y + height) {
                break;
            }
            bottomIndex = index;
            currentY += rowHeight;
        }
        return bottomIndex;
    }

    void setScrollOffsetForTest(int offset) {
        setScrollOffset(offset);
    }

    public int getHeight() {
        return height;
    }

    public void setSelectedTask(Task task) {
        if (task == null) {
            selectedTaskIndex = -1;
            selectedTaskId = null;
            return;
        }
        selectedTaskId = task.getId();
        syncSelectionIndex();
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

    /**
     * 返回当前滚动偏移量，供界面在重建前后保持任务列表可视区域。
     *
     * @return 当前滚动偏移量
     */
    int getScrollOffset() {
        return scrollBar.getValue();
    }

    /**
     * 设置当前滚动偏移量，并自动裁剪到合法范围内。
     *
     * @param offset 目标滚动偏移量
     */
    void setScrollOffset(int offset) {
        scrollBar.setValue(offset);
    }

    private void ensureVisibleIndex(int index) {
        if (index < 0 || displayRows == null || displayRows.isEmpty()) {
            return;
        }
        int currentTop = scrollBar.getValue();
        int currentBottom = getVisibleBottomIndex(currentTop);
        if (index < currentTop) {
            scrollBar.setValue(index);
            return;
        }
        if (index > currentBottom) {
            scrollBar.setValue(index);
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
        setScrollOffset(previousScroll);
    }

    /**
     * 根据分段模型重新构建渲染行。
     */
    private void rebuildDisplayRowsFromSections() {
        List<DisplayRow> rows = new ArrayList<>();
        if (sections == null || sections.isEmpty()) {
            this.displayRows = rows;
            this.rowHeights = new ArrayList<>();
            return;
        }
        for (SectionModel section : sections) {
            if (section == null) {
                continue;
            }
            boolean showHeader = !section.title.isEmpty() || section.expandable;
            if (showHeader) {
                rows.add(new DisplayRow(RowType.SECTION_HEADER,
                        section.id,
                        section.title,
                        section.expandable,
                        section.expanded,
                        null));
            }
            if (!section.expandable || section.expanded) {
                for (Task task : section.tasks) {
                    if (task != null) {
                        rows.add(new DisplayRow(RowType.TASK,
                                section.id,
                                section.title,
                                section.expandable,
                                section.expanded,
                                task));
                    }
                }
            }
        }
        this.displayRows = rows;
        List<Integer> heights = new ArrayList<>(rows.size());
        for (DisplayRow row : rows) {
            heights.add(getRowHeight(row));
        }
        this.rowHeights = heights;
    }
}
