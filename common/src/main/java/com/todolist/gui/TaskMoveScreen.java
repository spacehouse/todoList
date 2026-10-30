package com.todolist.gui;

import com.todolist.task.Task;
import com.todolist.task.TaskTreeIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 任务移动选择弹窗。
 *
 * <p>把任务移动到其他父任务或根层级，并在服务层 {@code TaskManager.moveTask} 中再次
 * 校验自引用/后代环。列表按任务树先序展示，深层目标使用缩进区分。</p>
 */
final class TaskMoveScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 2;

    private final TodoScreen parentScreen;
    private final Task movingTask;
    private final List<Task> allTasks;
    private final Consumer<String> moveCallback;
    private final List<MoveTarget> targets = new ArrayList<>();

    private Button[] targetButtons = new Button[0];
    private Button cancelButton;
    private int scrollOffset;
    private int visibleRows;
    private int listX;
    private int listY;
    private int listWidth;

    /**
     * 创建任务移动弹窗。
     *
     * @param parentScreen 父级待办界面
     * @param movingTask 待移动任务
     * @param allTasks 当前项目全部任务
     * @param moveCallback 目标父任务 ID 回调；{@code null} 表示移动到根层级
     */
    TaskMoveScreen(TodoScreen parentScreen, Task movingTask, List<Task> allTasks, Consumer<String> moveCallback) {
        super(Component.translatable("gui.todolist.task.move.title"));
        this.parentScreen = parentScreen;
        this.movingTask = movingTask;
        this.allTasks = allTasks == null ? List.of() : List.copyOf(allTasks);
        this.moveCallback = moveCallback;
        rebuildTargets();
    }

    private void rebuildTargets() {
        targets.clear();
        if (movingTask == null) {
            return;
        }
        TaskTreeIndex index = TaskTreeIndex.build(allTasks);
        // 允许移动到根层级（当前已是根任务时通常没有意义，但仍保留明确入口）。
        if (!index.isRoot(movingTask.getId())) {
            targets.add(new MoveTarget(null, 0, Component.translatable("gui.todolist.task.move.root").getString()));
        }
        for (Task candidate : index.flattenAll()) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            if (candidate.getId().equals(movingTask.getId()) || index.isDescendantOf(candidate.getId(), movingTask.getId())) {
                continue;
            }
            if (candidate.isCompleted()) {
                continue;
            }
            if (!Objects.equals(candidate.getProjectId(), movingTask.getProjectId())) {
                continue;
            }
            if (Objects.equals(candidate.getId(), movingTask.getParentTaskId())) {
                continue;
            }
            String title = candidate.getTitle() == null || candidate.getTitle().isBlank()
                    ? candidate.getId()
                    : candidate.getTitle();
            targets.add(new MoveTarget(candidate.getId(), index.getDepth(candidate.getId()), title));
        }
    }

    @Override
    protected void init() {
        super.init();
        int dialogWidth = Math.max(240, Math.min(420, width - 24));
        int dialogHeight = Math.max(150, Math.min(300, height - 24));
        listX = (width - dialogWidth) / 2 + 10;
        listWidth = dialogWidth - 20;
        int headerHeight = 38;
        int footerHeight = 30;
        listY = (height - dialogHeight) / 2 + headerHeight;
        int listHeight = dialogHeight - headerHeight - footerHeight;
        visibleRows = Math.max(1, listHeight / (ROW_HEIGHT + ROW_GAP));

        targetButtons = new Button[visibleRows];
        for (int row = 0; row < visibleRows; row++) {
            final int rowIndex = row;
            Button button = Button.builder(Component.empty(), b -> chooseRow(rowIndex))
                    .bounds(listX, listY + row * (ROW_HEIGHT + ROW_GAP), listWidth, ROW_HEIGHT)
                    .build();
            targetButtons[row] = button;
            addRenderableWidget(button);
        }
        cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), b -> onClose())
                .bounds((width - 100) / 2, (height + dialogHeight) / 2 - 26, 100, 20)
                .build();
        addRenderableWidget(cancelButton);
        refreshButtons();
    }

    private void chooseRow(int rowIndex) {
        int targetIndex = scrollOffset + rowIndex;
        if (targetIndex < 0 || targetIndex >= targets.size()) {
            return;
        }
        MoveTarget target = targets.get(targetIndex);
        if (moveCallback != null) {
            moveCallback.accept(target.taskId);
        }
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    private void refreshButtons() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, targets.size() - visibleRows)));
        for (int row = 0; row < targetButtons.length; row++) {
            int targetIndex = scrollOffset + row;
            Button button = targetButtons[row];
            if (targetIndex < 0 || targetIndex >= targets.size()) {
                button.visible = false;
                button.active = false;
                button.setMessage(Component.empty());
                continue;
            }
            MoveTarget target = targets.get(targetIndex);
            int indent = Math.min(target.depth, 10) * 2;
            String prefix = " ".repeat(indent) + (target.taskId == null ? "" : "- ");
            button.visible = true;
            button.active = true;
            button.setMessage(Component.literal(prefix + target.title));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (targets.size() > visibleRows && mouseX >= listX && mouseX <= listX + listWidth) {
            if (amount < 0) {
                scrollOffset++;
            } else if (amount > 0) {
                scrollOffset--;
            }
            refreshButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int dialogWidth = Math.max(240, Math.min(420, width - 24));
        int dialogHeight = Math.max(150, Math.min(300, height - 24));
        int dialogX = (width - dialogWidth) / 2;
        int dialogY = (height - dialogHeight) / 2;
        context.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF202020);
        context.renderOutline(dialogX, dialogY, dialogWidth, dialogHeight, 0xFFFFFFFF);
        context.drawString(font, title, dialogX + 10, dialogY + 10, 0xFFFFFFFF, false);
        context.drawString(font, Component.translatable("gui.todolist.task.move.hint"),
                dialogX + 10, dialogY + 24, 0xFFAAAAAA, false);
        if (targets.isEmpty()) {
            context.drawString(font, Component.translatable("gui.todolist.task.move.none"),
                    listX, listY + 4, 0xFFAAAAAA, false);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private static final class MoveTarget {
        private final String taskId;
        private final int depth;
        private final String title;

        private MoveTarget(String taskId, int depth, String title) {
            this.taskId = taskId;
            this.depth = depth;
            this.title = title == null ? "" : title;
        }
    }
}
