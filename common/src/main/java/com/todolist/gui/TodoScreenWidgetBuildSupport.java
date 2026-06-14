package com.todolist.gui;

import com.todolist.gui.TodoScreenLayoutSupport.LayoutRect;
import com.todolist.gui.TodoScreenLayoutSupport.MainLayoutMetrics;
import com.todolist.gui.TodoScreenLayoutSupport.ResponsiveTier;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.task.Task;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/**
 * TodoScreen 控件装配支持类，集中构建顶部、侧栏、任务区、详情区和底部操作控件。
 */
final class TodoScreenWidgetBuildSupport {
    /**
     * 工具类不允许实例化。
     */
    private TodoScreenWidgetBuildSupport() {
    }

    /**
     * 内容区控件装配结果。
     */
    static final class ContentWidgets {
        final Button sidebarToggleButton;
        final Button configButton;
        final Button filterPriorityButton;
        final EditBox searchField;

        /**
         * 创建内容区控件集合。
         */
        ContentWidgets(Button sidebarToggleButton,
                       Button configButton,
                       Button filterPriorityButton,
                       EditBox searchField) {
            this.sidebarToggleButton = sidebarToggleButton;
            this.configButton = configButton;
            this.filterPriorityButton = filterPriorityButton;
            this.searchField = searchField;
        }
    }

    /**
     * 侧栏控件装配结果。
     */
    static final class SidebarWidgets {
        final Button personalSpaceButton;
        final Button teamSpaceButton;
        final Button myViewButton;
        final Button unassignedViewButton;
        final Button allViewButton;
        final EditBox projectSearchField;
        final ProjectListWidget projectListWidget;
        final Button addProjectBtn;
        final Button editProjectBtn;
        final Button deleteProjectBtn;
        final Button applyJoinProjectBtn;

        /**
         * 创建侧栏控件集合。
         */
        SidebarWidgets(Button personalSpaceButton,
                       Button teamSpaceButton,
                       Button myViewButton,
                       Button unassignedViewButton,
                       Button allViewButton,
                       EditBox projectSearchField,
                       ProjectListWidget projectListWidget,
                       Button addProjectBtn,
                       Button editProjectBtn,
                       Button deleteProjectBtn,
                       Button applyJoinProjectBtn) {
            this.personalSpaceButton = personalSpaceButton;
            this.teamSpaceButton = teamSpaceButton;
            this.myViewButton = myViewButton;
            this.unassignedViewButton = unassignedViewButton;
            this.allViewButton = allViewButton;
            this.projectSearchField = projectSearchField;
            this.projectListWidget = projectListWidget;
            this.addProjectBtn = addProjectBtn;
            this.editProjectBtn = editProjectBtn;
            this.deleteProjectBtn = deleteProjectBtn;
            this.applyJoinProjectBtn = applyJoinProjectBtn;
        }
    }

    /**
     * 任务区控件装配结果。
     */
    static final class TaskAreaWidgets {
        final TaskListWidget taskListWidget;
        final EditBox quickAddField;

        /**
         * 创建任务区控件集合。
         */
        TaskAreaWidgets(TaskListWidget taskListWidget, EditBox quickAddField) {
            this.taskListWidget = taskListWidget;
            this.quickAddField = quickAddField;
        }
    }

    /**
     * 详情区控件装配结果。
     */
    static final class DetailWidgets {
        final Button detailCloseButton;
        final EditBox titleField;
        final MultiLineEditBox descField;
        final EditBox tagField;
        final Button claimButton;
        final Button abandonButton;
        final Button assignOthersButton;

        /**
         * 创建详情区控件集合。
         */
        DetailWidgets(Button detailCloseButton,
                      EditBox titleField,
                      MultiLineEditBox descField,
                      EditBox tagField,
                      Button claimButton,
                      Button abandonButton,
                      Button assignOthersButton) {
            this.detailCloseButton = detailCloseButton;
            this.titleField = titleField;
            this.descField = descField;
            this.tagField = tagField;
            this.claimButton = claimButton;
            this.abandonButton = abandonButton;
            this.assignOthersButton = assignOthersButton;
        }
    }

    /**
     * 底部操作控件装配结果。
     */
    static final class BottomActionWidgets {
        final Button saveButton;
        final Button cancelButton;

        /**
         * 创建底部操作控件集合。
         */
        BottomActionWidgets(Button saveButton, Button cancelButton) {
            this.saveButton = saveButton;
            this.cancelButton = cancelButton;
        }
    }

    /**
     * 构建内容区顶部控件（侧栏切换、配置、搜索与优先级筛选）。
     */
    static ContentWidgets buildContentWidgets(Font font,
                                              MainLayoutMetrics layoutMetrics,
                                              ResponsiveTier responsiveTier,
                                              int contentControlX,
                                              int contentControlRight,
                                              int topBarY,
                                              int topBarHeight,
                                              int secondRowY,
                                              int secondRowHeight,
                                              int currentPriorityFilter,
                                              String searchQuery,
                                              Consumer<Button> onPriorityButtonClick,
                                              Runnable onSidebarToggleClick,
                                              Runnable onConfigClick) {
        int overlayToggleWidth = layoutMetrics.sidebarOverlay ? 56 : 0;
        Button sidebarToggleButton = Button.builder(Component.translatable("gui.todolist.project.sidebar"), b -> onSidebarToggleClick.run())
                .bounds(contentControlX, topBarY, overlayToggleWidth, topBarHeight).build();
        sidebarToggleButton.visible = layoutMetrics.sidebarOverlay;
        sidebarToggleButton.active = layoutMetrics.sidebarOverlay;

        int configButtonWidth = Math.max(44, Math.min(64, font.width(Component.translatable("gui.todolist.config.title")) + 10));
        int configButtonX = contentControlRight - configButtonWidth;
        Button configButton = Button.builder(Component.translatable("gui.todolist.config.title"), b -> onConfigClick.run())
                .bounds(configButtonX, topBarY, configButtonWidth, topBarHeight).build();

        int filterGap = TodoScreenUiMetricsSupport.getContentActionGap(responsiveTier);
        int filtersX = contentControlX + (layoutMetrics.sidebarOverlay ? overlayToggleWidth + filterGap : 0);
        int btnH = secondRowHeight;
        int priorityBtnWidth = Math.min(92, Math.max(52, font.width(TodoScreenFilterTextSupport.getPriorityFilterText(currentPriorityFilter)) + 12));
        int minSearchWidth = 72;
        int availableSearchWidth = contentControlRight - filtersX - filterGap - priorityBtnWidth;
        if (availableSearchWidth < minSearchWidth) {
            priorityBtnWidth = Math.max(48, contentControlRight - filtersX - filterGap - minSearchWidth);
        }
        int priorityBtnX = contentControlRight - priorityBtnWidth;
        Button filterPriorityButton = Button.builder(TodoScreenFilterTextSupport.getPriorityFilterText(currentPriorityFilter), onPriorityButtonClick::accept)
                .bounds(priorityBtnX, secondRowY, priorityBtnWidth, btnH).build();

        int searchWidth = Math.max(0, priorityBtnX - filterGap - filtersX);
        EditBox searchField = new EditBox(font, filtersX, secondRowY, searchWidth, secondRowHeight, Component.empty());
        searchField.setHint(Component.translatable("gui.todolist.input.search.placeholder"));
        searchField.setValue(searchQuery);

        return new ContentWidgets(sidebarToggleButton, configButton, filterPriorityButton, searchField);
    }

    /**
     * 构建左侧项目栏控件（空间切换、项目搜索、项目列表和底部动作）。
     */
    static SidebarWidgets buildSidebarWidgets(Minecraft minecraft,
                                              Font font,
                                              ResponsiveTier responsiveTier,
                                              LayoutRect sidebarBounds,
                                              int sidebarTopY,
                                              int sidebarWidth,
                                              ProjectManager projectManager,
                                              boolean teamProjectsEnabled,
                                              String preferredTeamProjectId,
                                              String preferredPersonalProjectId,
                                              Runnable onPersonalSpaceClick,
                                              Runnable onTeamSpaceClick,
                                              Runnable onMyViewClick,
                                              Runnable onUnassignedViewClick,
                                              Runnable onAllViewClick,
                                              String projectSearchQuery,
                                              Consumer<String> onProjectSearchChanged,
                                              Consumer<Project> onProjectSelected,
                                              Runnable onAddProjectClick,
                                              Runnable onProjectSettingsClick,
                                              Runnable onProjectDeleteClick,
                                              Runnable onApplyJoinProjectClick,
                                              int sidebarPanelWidth,
                                              boolean hasCurrentProject) {
        int sidebarInset = responsiveTier == ResponsiveTier.MINIMAL ? 5 : 7;
        int sidebarInnerX = sidebarBounds.x + sidebarInset;
        int sidebarInnerWidth = Math.max(80, sidebarWidth - sidebarInset * 2);
        int sidebarControlHeight = TodoScreenUiMetricsSupport.getSidebarControlHeight(responsiveTier);
        int searchFieldHeight = TodoScreenUiMetricsSupport.getSidebarSearchFieldHeight(responsiveTier);
        int bottomButtonHeight = TodoScreenUiMetricsSupport.getSidebarBottomButtonHeight(responsiveTier);
        int sidebarSectionGap = TodoScreenUiMetricsSupport.getSidebarSectionGap(responsiveTier);
        int projectListGap = TodoScreenUiMetricsSupport.getSidebarProjectListGap(responsiveTier);
        int spaceButtonsY = sidebarTopY + (responsiveTier == ResponsiveTier.MINIMAL ? 8 : 10);
        int spaceButtonGap = 4;
        int spaceButtonWidth = Math.max(48, (sidebarInnerWidth - spaceButtonGap) / 2);

        Button personalSpaceButton = Button.builder(Component.translatable("gui.todolist.scope.personal"), b -> onPersonalSpaceClick.run())
                .bounds(sidebarInnerX, spaceButtonsY, spaceButtonWidth, sidebarControlHeight).build();

        Button teamSpaceButton = Button.builder(Component.translatable("gui.todolist.scope.team"), b -> onTeamSpaceClick.run())
                .bounds(sidebarInnerX + spaceButtonWidth + spaceButtonGap, spaceButtonsY,
                        sidebarInnerWidth - spaceButtonWidth - spaceButtonGap, sidebarControlHeight).build();
        teamSpaceButton.active = teamProjectsEnabled;

        int viewButtonsY = spaceButtonsY + sidebarControlHeight + sidebarSectionGap;
        int teamViewButtonGap = 4;
        int teamViewButtonWidth = Math.max(26, (sidebarInnerWidth - teamViewButtonGap * 2) / 3);
        Button myViewButton = Button.builder(Component.literal("\u6211\u7684"), b -> onMyViewClick.run())
                .bounds(sidebarInnerX, viewButtonsY, teamViewButtonWidth, sidebarControlHeight).build();

        Button unassignedViewButton = Button.builder(TodoScreenUiMetricsSupport.getUnassignedTeamViewText(sidebarInnerWidth), b -> onUnassignedViewClick.run())
                .bounds(sidebarInnerX + teamViewButtonWidth + teamViewButtonGap, viewButtonsY, teamViewButtonWidth, sidebarControlHeight).build();

        Button allViewButton = Button.builder(TodoScreenUiMetricsSupport.getAllTeamViewText(), b -> onAllViewClick.run())
                .bounds(sidebarInnerX + (teamViewButtonWidth + teamViewButtonGap) * 2, viewButtonsY,
                        sidebarInnerWidth - (teamViewButtonWidth + teamViewButtonGap) * 2, sidebarControlHeight).build();

        int projectSearchY = viewButtonsY + sidebarControlHeight + sidebarSectionGap;
        EditBox projectSearchField = new EditBox(font, sidebarInnerX, projectSearchY, sidebarInnerWidth, searchFieldHeight, Component.translatable("gui.todolist.project.search"));
        projectSearchField.setHint(Component.translatable("gui.todolist.project.search"));
        projectSearchField.setValue(projectSearchQuery);
        projectSearchField.setResponder(onProjectSearchChanged);

        int projectButtonGap = TodoScreenUiMetricsSupport.getSidebarBottomButtonGap(responsiveTier);
        int projectButtonWidth = Math.max(26, (sidebarInnerWidth - projectButtonGap * 2) / 3);
        int projectButtonsY = sidebarBounds.y + sidebarBounds.height - bottomButtonHeight
                - TodoScreenUiMetricsSupport.getSidebarBottomPadding(responsiveTier);
        int projectListY = projectSearchY + searchFieldHeight + projectListGap;
        int projectListHeight = Math.max(0, projectButtonsY - projectListGap - projectListY);

        ProjectListWidget projectListWidget = new ProjectListWidget(minecraft, sidebarInnerX, projectListY, sidebarInnerWidth, projectListHeight);
        projectListWidget.setOnProjectSelected(onProjectSelected);

        Button addProjectBtn = Button.builder(Component.translatable("gui.todolist.add"), b -> onAddProjectClick.run())
                .bounds(sidebarInnerX, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();

        Button editProjectBtn = Button.builder(Component.translatable("gui.todolist.edit"), b -> onProjectSettingsClick.run())
                .bounds(sidebarInnerX + projectButtonWidth + projectButtonGap, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();
        editProjectBtn.active = hasCurrentProject;

        int deleteButtonX = sidebarInnerX + (projectButtonWidth + projectButtonGap) * 2;
        Button deleteProjectBtn = Button.builder(Component.translatable("gui.todolist.delete"), b -> onProjectDeleteClick.run())
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();

        Button applyJoinProjectBtn = Button.builder(
                        Component.translatable(TodoScreenUiMetricsSupport.useCompactSidebarBottomButtons(responsiveTier, sidebarPanelWidth)
                                ? "gui.todolist.project.join.compact"
                                : "gui.todolist.project.join.apply"),
                        b -> onApplyJoinProjectClick.run()
                )
                .bounds(deleteButtonX, projectButtonsY, projectButtonWidth, bottomButtonHeight).build();

        return new SidebarWidgets(
                personalSpaceButton,
                teamSpaceButton,
                myViewButton,
                unassignedViewButton,
                allViewButton,
                projectSearchField,
                projectListWidget,
                addProjectBtn,
                editProjectBtn,
                deleteProjectBtn,
                applyJoinProjectBtn
        );
    }

    /**
     * 构建任务列表与快速添加输入框。
     */
    static TaskAreaWidgets buildTaskAndQuickAddWidgets(Minecraft minecraft,
                                                        Font font,
                                                        int contentX,
                                                        int listTop,
                                                        int contentWidth,
                                                        int listHeight,
                                                        boolean teamAllViewForNonOp,
                                                        boolean taskReorderEnabled,
                                                        List<TaskListWidget.SectionModel> sections,
                                                        Task selectedTask,
                                                        Consumer<Task> onTaskToggleCompletion,
                                                        Consumer<List<Task>> onTaskReorder,
                                                        int quickAddFieldX,
                                                        int inputRowY,
                                                        int quickAddFieldWidth,
                                                        int inputRowHeight) {
        TaskListWidget taskListWidget = new TaskListWidget(minecraft, contentX, listTop, contentWidth, listHeight);
        taskListWidget.setTeamAllViewForNonOp(teamAllViewForNonOp);
        taskListWidget.setTaskReorderEnabled(taskReorderEnabled);
        taskListWidget.setSections(sections);
        if (selectedTask != null) {
            taskListWidget.setSelectedTask(selectedTask);
        }
        taskListWidget.setOnTaskToggleCompletion(onTaskToggleCompletion);
        taskListWidget.setOnTaskReorder(onTaskReorder);

        EditBox quickAddField = new EditBox(font, quickAddFieldX, inputRowY, quickAddFieldWidth, inputRowHeight, Component.empty());
        quickAddField.setHint(Component.translatable("gui.todolist.input.quick_add.placeholder"));
        quickAddField.setValue("");
        quickAddField.setMaxLength(100);

        return new TaskAreaWidgets(taskListWidget, quickAddField);
    }

    /**
     * 构建详情面板控件。
     */
    static DetailWidgets buildDetailWidgets(Font font,
                                            LayoutRect detailBounds,
                                            int rightPanelX,
                                            int rightPanelWidth,
                                            boolean showAssignButtons,
                                            Runnable onDetailCloseClick,
                                            Runnable onClaimClick,
                                            Runnable onAbandonClick,
                                            Runnable onAssignOthersClick) {
        int rightInnerPadding = 4;
        int rightFieldWidth = Math.max(60, rightPanelWidth - rightInnerPadding * 2);
        int rightPanelTop = detailBounds.y;
        int rightPanelBottom = detailBounds.y + detailBounds.height;
        int rightSectionGap = 6;
        int textH = font.lineHeight;
        int assignButtonHeight = 20;
        int assignButtonGap = 4;
        int assignsX = rightPanelX + rightInnerPadding;
        int titleFieldHeight = 20;
        int titleFieldY = rightPanelTop + rightInnerPadding;
        int closeButtonSize = 14;
        int closeButtonGap = 4;
        int closeButtonX = Math.max(assignsX, rightPanelX + rightPanelWidth - rightInnerPadding - closeButtonSize);
        int closeButtonY = titleFieldY + Math.max(0, (titleFieldHeight - closeButtonSize) / 2);
        int titleFieldWidth = Math.max(48, closeButtonX - closeButtonGap - assignsX);

        Button detailCloseButton = Button.builder(Component.literal("\u00d7"), b -> onDetailCloseClick.run())
                .bounds(closeButtonX, closeButtonY, closeButtonSize, closeButtonSize)
                .build();

        EditBox titleField = new EditBox(font, assignsX, titleFieldY, titleFieldWidth, titleFieldHeight, Component.empty());
        titleField.setHint(Component.translatable("gui.todolist.input.title.edit.placeholder"));
        titleField.setValue("");
        titleField.setMaxLength(100);
        titleField.setEditable(false);

        int teamButtonsTop = titleFieldY + titleFieldHeight + rightSectionGap;
        int detailFieldsTop = showAssignButtons
                ? teamButtonsTop + assignButtonHeight + rightSectionGap
                : teamButtonsTop;

        int tagFieldY = rightPanelBottom - rightInnerPadding - 20;
        int descFieldY = detailFieldsTop + textH + 2;
        int minTagFieldY = descFieldY + 28 + rightSectionGap + textH + 2;
        if (tagFieldY < minTagFieldY) {
            tagFieldY = minTagFieldY;
        }
        int descFieldBottom = tagFieldY - rightSectionGap - textH - 2;
        int descFieldHeight = Math.max(28, descFieldBottom - descFieldY);

        MultiLineEditBox descField = new MultiLineEditBox(
                font,
                assignsX,
                descFieldY,
                rightFieldWidth,
                descFieldHeight,
                Component.translatable("gui.todolist.input.description"),
                Component.translatable("gui.todolist.input.description.placeholder")
        );
        descField.setValue("");
        descField.setCharacterLimit(2000);

        EditBox tagField = new EditBox(font, assignsX, tagFieldY, rightFieldWidth, 20, Component.empty());
        tagField.setHint(Component.translatable("gui.todolist.input.tags.placeholder"));
        tagField.setValue("");
        tagField.setMaxLength(100);

        int claimButtonWidth = Math.max(36, (rightFieldWidth - assignButtonGap * 2) / 3);
        int abandonButtonX = assignsX + claimButtonWidth + assignButtonGap;
        int abandonButtonWidth = claimButtonWidth;
        int assignOthersButtonX = abandonButtonX + abandonButtonWidth + assignButtonGap;
        int assignOthersButtonWidth = Math.max(36, rightFieldWidth - claimButtonWidth - abandonButtonWidth - assignButtonGap * 2);

        Button claimButton = Button.builder(Component.literal("\u9886\u53d6"), b -> onClaimClick.run())
                .bounds(assignsX, teamButtonsTop, claimButtonWidth, assignButtonHeight).build();
        claimButton.active = false;

        Button abandonButton = Button.builder(Component.literal("\u653e\u5f03"), b -> onAbandonClick.run())
                .bounds(abandonButtonX, teamButtonsTop, abandonButtonWidth, assignButtonHeight).build();
        abandonButton.active = false;

        Button assignOthersButton = Button.builder(Component.literal("\u6307\u6d3e"), b -> onAssignOthersClick.run())
                .bounds(assignOthersButtonX, teamButtonsTop, assignOthersButtonWidth, assignButtonHeight).build();
        assignOthersButton.active = false;

        return new DetailWidgets(
                detailCloseButton,
                titleField,
                descField,
                tagField,
                claimButton,
                abandonButton,
                assignOthersButton
        );
    }

    /**
     * 构建底部保存与取消按钮。
     */
    static BottomActionWidgets buildBottomActionWidgets(int saveButtonX,
                                                        int saveButtonWidth,
                                                        int cancelButtonX,
                                                        int cancelButtonWidth,
                                                        int actionRowY,
                                                        int bottomActionRowHeight,
                                                        Runnable onSaveClick,
                                                        Runnable onCancelClick) {
        Button saveButton = Button.builder(Component.translatable("gui.todolist.save"), b -> onSaveClick.run())
                .bounds(saveButtonX, actionRowY, saveButtonWidth, bottomActionRowHeight).build();
        Button cancelButton = Button.builder(Component.translatable("gui.todolist.cancel"), b -> onCancelClick.run())
                .bounds(cancelButtonX, actionRowY, cancelButtonWidth, bottomActionRowHeight).build();
        return new BottomActionWidgets(saveButton, cancelButton);
    }
}
