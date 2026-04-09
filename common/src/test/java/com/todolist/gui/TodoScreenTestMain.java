package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.gui.testsupport.FakeClientConnection;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.task.Task;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.server.IntegratedServer;

import java.util.List;
import java.util.UUID;

/**
 * TodoScreen GUI 离线自测入口。
 * 负责覆盖项目切换、任务编辑、保存语义以及 LAN/单人切换等关键回归场景。
 */
public final class TodoScreenTestMain {
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ALICE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID BOB_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID CHARLIE_ID = UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final UUID EXTERNAL_OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000005");
    private static final UUID DAVID_ID = UUID.fromString("20000000-0000-0000-0000-000000000006");
    private static final UUID ERIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000007");
    private static final UUID FRANK_ID = UUID.fromString("20000000-0000-0000-0000-000000000008");
    private static final UUID GRACE_ID = UUID.fromString("20000000-0000-0000-0000-000000000009");
    private static final UUID HEIDI_ID = UUID.fromString("20000000-0000-0000-0000-00000000000a");

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenTestMain() {
    }

    /**
     * 串行执行 TodoScreen 的所有 GUI 回归测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldInitializeWithDefaultPersonalProject", TodoScreenTestMain::shouldInitializeWithDefaultPersonalProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowOnlyMyViewInPersonalSpaceUtf8", TodoScreenTestMain::shouldShowOnlyMyViewInPersonalSpaceUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowUnassignedAllAndMineViewsInTeamSpaceUtf8", TodoScreenTestMain::shouldShowUnassignedAllAndMineViewsInTeamSpaceUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowAllProjectTasksInTeamAllViewUtf8", TodoScreenTestMain::shouldShowAllProjectTasksInTeamAllViewUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldAllowMemberAddTaskInTeamAllViewWhenProjectSettingEnabledUtf8", TodoScreenTestMain::shouldAllowMemberAddTaskInTeamAllViewWhenProjectSettingEnabledUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldToggleActiveSectionWithoutChangingCurrentViewUtf8", TodoScreenTestMain::shouldToggleActiveSectionWithoutChangingCurrentViewUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldToggleCompletedSectionWithoutChangingCurrentViewUtf8", TodoScreenTestMain::shouldToggleCompletedSectionWithoutChangingCurrentViewUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepBottomActionButtonsCenteredAboveQuickAddUtf8", TodoScreenTestMain::shouldKeepBottomActionButtonsCenteredAboveQuickAddUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldBlurDetailInputsWhenClickingOutsideFieldsUtf8", TodoScreenTestMain::shouldBlurDetailInputsWhenClickingOutsideFieldsUtf8);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldUseInlineThreeColumnLayoutOnLargeScreen", TodoScreenTestMain::shouldUseInlineThreeColumnLayoutOnLargeScreen);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldUseInlineDetailPanelOnMediumScreen", TodoScreenTestMain::shouldUseInlineDetailPanelOnMediumScreen);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldUseOverlayDetailPanelOnCompactScreen", TodoScreenTestMain::shouldUseOverlayDetailPanelOnCompactScreen);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldUseOverlaySidebarAndDetailPanelOnMinimalScreen", TodoScreenTestMain::shouldUseOverlaySidebarAndDetailPanelOnMinimalScreen);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldHideOverlayDetailPanelAfterDeletingSelectedTaskOnCompactScreen", TodoScreenTestMain::shouldHideOverlayDetailPanelAfterDeletingSelectedTaskOnCompactScreen);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepTaskWhenCancelingDeleteConfirmation", TodoScreenTestMain::shouldKeepTaskWhenCancelingDeleteConfirmation);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldHideTeamActionButtonsInPersonalDetailDrawer", TodoScreenTestMain::shouldHideTeamActionButtonsInPersonalDetailDrawer);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowVerticalTeamActionButtonsInTeamDetailDrawer", TodoScreenTestMain::shouldShowVerticalTeamActionButtonsInTeamDetailDrawer);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldLayoutDetailDrawerCloseRowSeparately", TodoScreenTestMain::shouldLayoutDetailDrawerCloseRowSeparately);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldEnterDetailTitleEditModeAfterClickingTitle", TodoScreenTestMain::shouldEnterDetailTitleEditModeAfterClickingTitle);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldCloseOverlayDetailDrawerFromCloseButton", TodoScreenTestMain::shouldCloseOverlayDetailDrawerFromCloseButton);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepProjectActionButtonsPinnedAtSidebarBottom", TodoScreenTestMain::shouldKeepProjectActionButtonsPinnedAtSidebarBottom);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowProjectSearchPrefixDropdownInTeamSpace", TodoScreenTestMain::shouldShowProjectSearchPrefixDropdownInTeamSpace);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldApplyProjectSearchPrefixSuggestionAndPreserveNameQuery", TodoScreenTestMain::shouldApplyProjectSearchPrefixSuggestionAndPreserveNameQuery);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldReplaceUnknownProjectSearchPrefixWhenApplyingSuggestion", TodoScreenTestMain::shouldReplaceUnknownProjectSearchPrefixWhenApplyingSuggestion);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldFilterTeamProjectsByCreatedPrefix", TodoScreenTestMain::shouldFilterTeamProjectsByCreatedPrefix);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldFilterTeamProjectsByManagedPrefixAndName", TodoScreenTestMain::shouldFilterTeamProjectsByManagedPrefixAndName);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldFilterTeamProjectsByJoinedPrefix", TodoScreenTestMain::shouldFilterTeamProjectsByJoinedPrefix);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldTreatUnknownProjectSearchPrefixAsPlainNameQuery", TodoScreenTestMain::shouldTreatUnknownProjectSearchPrefixAsPlainNameQuery);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldHideProjectSearchPrefixDropdownOutsideTeamSpace", TodoScreenTestMain::shouldHideProjectSearchPrefixDropdownOutsideTeamSpace);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowApplyJoinButtonForNonMemberTeamProject", TodoScreenTestMain::shouldShowApplyJoinButtonForNonMemberTeamProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldUseViewLabelForReadOnlyTeamProjectMember", TodoScreenTestMain::shouldUseViewLabelForReadOnlyTeamProjectMember);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSwitchProjectToTeamScopeAndSyncActiveProject", TodoScreenTestMain::shouldSwitchProjectToTeamScopeAndSyncActiveProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldHandleProjectLifecycleChanges", TodoScreenTestMain::shouldHandleProjectLifecycleChanges);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldEditSelectedTaskAndMarkUnsaved", TodoScreenTestMain::shouldEditSelectedTaskAndMarkUnsaved);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldMarkUnsavedAfterManualReorder", TodoScreenTestMain::shouldMarkUnsavedAfterManualReorder);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepTaskListScrollOffsetWhenSelectingTask", TodoScreenTestMain::shouldKeepTaskListScrollOffsetWhenSelectingTask);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldMovePromotedTaskAheadOfLowerPriorities", TodoScreenTestMain::shouldMovePromotedTaskAheadOfLowerPriorities);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldMoveDemotedTaskBehindHigherPriorities", TodoScreenTestMain::shouldMoveDemotedTaskBehindHigherPriorities);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepManualOrderInsidePriorityBucketAfterPriorityChange", TodoScreenTestMain::shouldKeepManualOrderInsidePriorityBucketAfterPriorityChange);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldFilterTasksBySearchAndStatus", TodoScreenTestMain::shouldFilterTasksBySearchAndStatus);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldAllowUncompleteCompletedTaskInPersonalView", TodoScreenTestMain::shouldAllowUncompleteCompletedTaskInPersonalView);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSavePersonalTasksAndClearUnsavedState", TodoScreenTestMain::shouldSavePersonalTasksAndClearUnsavedState);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepPersonalTasksAfterSavingInPublishedLocalWorld", TodoScreenTestMain::shouldKeepPersonalTasksAfterSavingInPublishedLocalWorld);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepPersonalTasksAfterPublishedLocalWorldReentryFlow", TodoScreenTestMain::shouldKeepPersonalTasksAfterPublishedLocalWorldReentryFlow);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSavePersonalAndTeamTasksWhenSavingFromTeamViewOnRemoteServer", TodoScreenTestMain::shouldSavePersonalAndTeamTasksWhenSavingFromTeamViewOnRemoteServer);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepPersonalAndTeamTasksAfterRemoteReconnectWhenSavingFromPersonalView", TodoScreenTestMain::shouldKeepPersonalAndTeamTasksAfterRemoteReconnectWhenSavingFromPersonalView);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldDiscardUnsavedPersonalChangesOnClose", TodoScreenTestMain::shouldDiscardUnsavedPersonalChangesOnClose);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldRequestTeamSyncWhenClosingUnsavedTeamChanges", TodoScreenTestMain::shouldRequestTeamSyncWhenClosingUnsavedTeamChanges);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldShowNotificationWhenAddingWithoutProject", TodoScreenTestMain::shouldShowNotificationWhenAddingWithoutProject);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldOpenContextMenuAndApplyPriorityAction", TodoScreenTestMain::shouldOpenContextMenuAndApplyPriorityAction);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldSearchAndAssignPlayerFromAssignScreen", TodoScreenTestMain::shouldSearchAndAssignPlayerFromAssignScreen);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldListOfflineProjectMembersInAssignScreen", TodoScreenTestMain::shouldListOfflineProjectMembersInAssignScreen);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldClampAssignDialogScrollOffsetWhenMembersOverflow", TodoScreenTestMain::shouldClampAssignDialogScrollOffsetWhenMembersOverflow);
        GuiTestSupport.runTestCase("TodoScreenTestMain.shouldKeepAssignDialogCancelButtonInsideSmallScreen", TodoScreenTestMain::shouldKeepAssignDialogCancelButtonInsideSmallScreen);
    }

    /**
     * 验证界面初始化后会默认选中个人项目并同步激活项目状态。
     */
    private static void shouldInitializeWithDefaultPersonalProject() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertEquals(personalProject.getId(), screen.getCurrentProjectForTest().getId(), "初始化后应选中默认个人项目");
        GuiTestSupport.assertEquals("PERSONAL", screen.getViewModeNameForTest(), "默认项目初始化后应保持个人视图");
        GuiTestSupport.assertEquals(personalProject.getId(), ops.getActiveProjectId(), "初始化后应记录当前激活项目");
        GuiTestSupport.assertEquals(personalProject.getId(), ops.getActiveProjectSyncCalls().get(0), "初始化后应向桥接层同步当前激活项目");
    }

    /**
     * 验证个人空间只显示“我的”任务视图。
     */
    private static void shouldShowOnlyMyViewInPersonalSpace() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertEquals("PERSONAL", screen.getCurrentSpaceModeNameForTest(), "默认个人项目下应解析为个人空间");
        GuiTestSupport.assertEquals(List.of("MY"), screen.getVisibleTaskViewOptionNamesForTest(), "个人空间应只显示“我的”视图");
        GuiTestSupport.assertEquals("MY", screen.getCurrentTaskViewOptionNameForTest(), "个人空间当前视图应为“我的”");
    }

    /**
     * 验证团队空间会显示待分配、全部和我的三个任务视图。
     */
    private static void shouldShowUnassignedAllAndMineViewsInTeamSpace() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);

        GuiTestSupport.assertEquals("TEAM", screen.getCurrentSpaceModeNameForTest(), "切换团队项目后应解析为团队空间");
        GuiTestSupport.assertEquals(List.of("UNASSIGNED", "ALL", "MY"), screen.getVisibleTaskViewOptionNamesForTest(), "团队空间应显示三个团队视图");
        GuiTestSupport.assertEquals("UNASSIGNED", screen.getCurrentTaskViewOptionNameForTest(), "团队空间默认视图应为“待分配”");
    }

    /**
     * 验证切换已完成分组时不会影响当前空间和任务视图。
     */
    private static void shouldToggleCompletedSectionWithoutChangingCurrentView() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        String originalSpaceMode = screen.getCurrentSpaceModeNameForTest();
        String originalTaskView = screen.getCurrentTaskViewOptionNameForTest();

        GuiTestSupport.assertFalse(screen.isCompletedSectionExpandedForTest(), "默认情况下已完成分组应处于收起状态");

        screen.toggleCompletedSectionForTest();

        GuiTestSupport.assertTrue(screen.isCompletedSectionExpandedForTest(), "切换后已完成分组应展开");
        GuiTestSupport.assertEquals(originalSpaceMode, screen.getCurrentSpaceModeNameForTest(), "切换已完成分组不应改变当前空间");
        GuiTestSupport.assertEquals(originalTaskView, screen.getCurrentTaskViewOptionNameForTest(), "切换已完成分组不应改变当前任务视图");

        screen.toggleCompletedSectionForTest();

        GuiTestSupport.assertFalse(screen.isCompletedSectionExpandedForTest(), "再次切换后已完成分组应恢复收起");
        GuiTestSupport.assertEquals(originalTaskView, screen.getCurrentTaskViewOptionNameForTest(), "反复切换已完成分组也不应改变当前任务视图");
    }

    /**
     * 验证大窗口下主界面会采用三栏常驻布局。
     */
    private static void shouldUseInlineThreeColumnLayoutOnLargeScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-layout-large", "Layout Large Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 480, 300);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Large Layout Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertEquals("LARGE", screen.getResponsiveTierNameForTest(), "大窗口应命中 LARGE 档位");
        GuiTestSupport.assertFalse(screen.isProjectSidebarOverlayForTest(), "大窗口下项目侧栏不应进入覆盖模式");
        GuiTestSupport.assertFalse(screen.isDetailPanelOverlayForTest(), "大窗口下详情区不应进入覆盖模式");
        GuiTestSupport.assertTrue(screen.isProjectSidebarVisibleForTest(), "大窗口下项目侧栏应保持可见");
        GuiTestSupport.assertTrue(screen.isDetailPanelVisibleForTest(), "大窗口下详情区应保持可见");

        assertRectInsideScreen(screen.getProjectSidebarBoundsForTest(), 480, 300, "大窗口下项目侧栏边界应位于屏幕内");
        assertRectInsideScreen(screen.getContentAreaBoundsForTest(), 480, 300, "大窗口下主内容区边界应位于屏幕内");
        assertRectInsideScreen(screen.getDetailPanelBoundsForTest(), 480, 300, "大窗口下详情区边界应位于屏幕内");

        int[] sidebarBounds = screen.getProjectSidebarBoundsForTest();
        int[] contentBounds = screen.getContentAreaBoundsForTest();
        int[] detailBounds = screen.getDetailPanelBoundsForTest();
        GuiTestSupport.assertTrue(sidebarBounds[0] + sidebarBounds[2] <= contentBounds[0], "项目侧栏应位于主内容区左侧");
        GuiTestSupport.assertTrue(contentBounds[0] + contentBounds[2] <= detailBounds[0], "详情区应位于主内容区右侧");
    }

    /**
     * 验证中等窗口下主界面仍保持详情区常驻，但右侧宽度会比大窗口更紧凑。
     */
    private static void shouldUseInlineDetailPanelOnMediumScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-layout-medium", "Layout Medium Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 420, 250);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Medium Layout Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertEquals("MEDIUM", screen.getResponsiveTierNameForTest(), "中等窗口应命中 MEDIUM 档位");
        GuiTestSupport.assertFalse(screen.isProjectSidebarOverlayForTest(), "中等窗口下项目侧栏不应进入覆盖模式");
        GuiTestSupport.assertFalse(screen.isDetailPanelOverlayForTest(), "中等窗口下详情区仍应常驻");
        GuiTestSupport.assertTrue(screen.isDetailPanelVisibleForTest(), "中等窗口下选中任务后详情区应保持可见");

        assertRectInsideScreen(screen.getProjectSidebarBoundsForTest(), 420, 250, "中等窗口下项目侧栏边界应位于屏幕内");
        assertRectInsideScreen(screen.getContentAreaBoundsForTest(), 420, 250, "中等窗口下主内容区边界应位于屏幕内");
        assertRectInsideScreen(screen.getDetailPanelBoundsForTest(), 420, 250, "中等窗口下详情区边界应位于屏幕内");
    }

    /**
     * 验证紧凑窗口下右侧详情区改为覆盖式展示，但主列表仍保持在屏幕内。
     */
    private static void shouldUseOverlayDetailPanelOnCompactScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-layout-compact", "Layout Compact Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 360, 220);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Compact Layout Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertEquals("COMPACT", screen.getResponsiveTierNameForTest(), "紧凑窗口应命中 COMPACT 档位");
        GuiTestSupport.assertFalse(screen.isProjectSidebarOverlayForTest(), "紧凑窗口下项目侧栏仍应常驻");
        GuiTestSupport.assertTrue(screen.isDetailPanelOverlayForTest(), "紧凑窗口下详情区应进入覆盖模式");
        GuiTestSupport.assertTrue(screen.isProjectSidebarVisibleForTest(), "紧凑窗口下项目侧栏应保持可见");
        GuiTestSupport.assertTrue(screen.isDetailPanelVisibleForTest(), "选中任务后详情覆盖层应显示");

        assertRectInsideScreen(screen.getProjectSidebarBoundsForTest(), 360, 220, "紧凑窗口下项目侧栏边界应位于屏幕内");
        assertRectInsideScreen(screen.getContentAreaBoundsForTest(), 360, 220, "紧凑窗口下主内容区边界应位于屏幕内");
        assertRectInsideScreen(screen.getDetailPanelBoundsForTest(), 360, 220, "紧凑窗口下详情区边界应位于屏幕内");

        int[] contentBounds = screen.getContentAreaBoundsForTest();
        int[] detailBounds = screen.getDetailPanelBoundsForTest();
        GuiTestSupport.assertTrue(contentBounds[0] + contentBounds[2] > detailBounds[0], "覆盖式详情区应与主内容区发生水平覆盖，而不是继续压缩主内容区");
    }

    /**
     * 验证极小窗口下项目侧栏与详情区都切换为覆盖式，且仍可通过切换按钮访问项目区。
     */
    private static void shouldUseOverlaySidebarAndDetailPanelOnMinimalScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-layout-minimal", "Layout Minimal Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 300, 190);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Minimal Layout Task");

        GuiTestSupport.assertEquals("MINIMAL", screen.getResponsiveTierNameForTest(), "极小窗口应命中 MINIMAL 档位");
        GuiTestSupport.assertTrue(screen.isProjectSidebarOverlayForTest(), "极小窗口下项目侧栏应进入覆盖模式");
        GuiTestSupport.assertTrue(screen.isDetailPanelOverlayForTest(), "极小窗口下详情区应进入覆盖模式");
        GuiTestSupport.assertFalse(screen.isProjectSidebarVisibleForTest(), "极小窗口初始化时项目侧栏覆盖层应默认收起");
        GuiTestSupport.assertFalse(screen.isDetailPanelVisibleForTest(), "未选中任务时极小窗口详情覆盖层应默认收起");
        GuiTestSupport.assertTrue(screen.isSidebarToggleButtonVisibleForTest(), "极小窗口下应提供项目侧栏切换按钮");

        screen.toggleSidebarOverlayForTest();

        GuiTestSupport.assertTrue(screen.isProjectSidebarVisibleForTest(), "点击切换后极小窗口项目侧栏覆盖层应显示");
        assertRectInsideScreen(screen.getProjectSidebarBoundsForTest(), 300, 190, "极小窗口下项目侧栏覆盖层边界应位于屏幕内");
        assertRectInsideScreen(screen.getContentAreaBoundsForTest(), 300, 190, "极小窗口下主内容区边界应位于屏幕内");

        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertTrue(screen.isDetailPanelVisibleForTest(), "极小窗口选中任务后详情覆盖层应显示");
        assertRectInsideScreen(screen.getDetailPanelBoundsForTest(), 300, 190, "极小窗口下详情覆盖层边界应位于屏幕内");

        int[] contentBounds = screen.getContentAreaBoundsForTest();
        int[] sidebarBounds = screen.getProjectSidebarBoundsForTest();
        int[] detailBounds = screen.getDetailPanelBoundsForTest();
        GuiTestSupport.assertTrue(contentBounds[0] < sidebarBounds[0] + sidebarBounds[2], "极小窗口下项目侧栏应覆盖到主内容区之上");
        GuiTestSupport.assertTrue(contentBounds[0] + contentBounds[2] > detailBounds[0], "极小窗口下详情区应覆盖到主内容区之上");
    }

    /**
     * 验证紧凑窗口下删除当前选中任务后，覆盖式详情区会一起收起，避免留下空抽屉。
     */
    private static void shouldHideOverlayDetailPanelAfterDeletingSelectedTaskOnCompactScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-layout-compact-delete", "Layout Compact Delete Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 360, 220);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Compact Delete Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertTrue(screen.isDetailPanelVisibleForTest(), "删除前紧凑窗口详情覆盖层应已显示");

        Screen confirmScreen = openDeleteTaskConfirmScreen(minecraft, screen, task);
        clickDialogButton(confirmScreen, 0);

        GuiTestSupport.assertFalse(screen.isDetailPanelVisibleForTest(), "删除选中任务后详情覆盖层应自动收起");
        GuiTestSupport.assertNull(screen.getSelectedTaskForTest(), "删除选中任务后不应残留选中项");
    }

    /**
     * 验证切换到团队项目后会更新当前项目与视图，并同步激活项目。
     */
    /**
     * 校验个人空间详情抽屉会隐藏团队操作按钮。
     */
    /**
     * 验证删除确认弹窗点击取消后不会误删任务，并会返回待办主界面。
     */
    private static void shouldKeepTaskWhenCancelingDeleteConfirmation() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Delete Confirm Cancel Task");
        Task task = requireTaskByTitle(screen, "Delete Confirm Cancel Task");
        screen.selectTaskForTest(task);

        Screen confirmScreen = openDeleteTaskConfirmScreen(minecraft, screen, task);
        clickDialogButton(confirmScreen, 1);

        GuiTestSupport.assertEquals(screen, minecraft.getLastScreen(), "取消删除后应返回待办主界面");
        GuiTestSupport.assertEquals(1, screen.getCurrentManagerTasksForTest().size(), "取消删除后任务不应被移除");
        GuiTestSupport.assertEquals(task.getId(), screen.getSelectedTaskForTest().getId(), "取消删除后原任务仍应保持选中");
    }

    private static void shouldHideTeamActionButtonsInPersonalDetailDrawer() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Personal Detail Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertEquals("PERSONAL", screen.getCurrentSpaceModeNameForTest(), "个人项目下应保持个人空间语义");
        GuiTestSupport.assertFalse(screen.isClaimButtonVisibleForTest(), "个人空间详情抽屉不应显示领取按钮");
        GuiTestSupport.assertFalse(screen.isAbandonButtonVisibleForTest(), "个人空间详情抽屉不应显示放弃按钮");
        GuiTestSupport.assertFalse(screen.isAssignOthersButtonVisibleForTest(), "个人空间详情抽屉不应显示指派他人按钮");
    }

    /**
     * 校验团队空间详情抽屉会在标题框下方显示横向排列的团队操作按钮。
     */
    private static void shouldShowVerticalTeamActionButtonsInTeamDetailDrawer() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-detail-buttons", "Detail Buttons Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 480, 300);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Team Detail Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertTrue(screen.isClaimButtonVisibleForTest(), "团队空间详情抽屉应显示领取按钮");
        GuiTestSupport.assertTrue(screen.isAbandonButtonVisibleForTest(), "团队空间详情抽屉应显示放弃按钮");
        GuiTestSupport.assertTrue(screen.isAssignOthersButtonVisibleForTest(), "团队空间详情抽屉应显示指派他人按钮");

        int[] claimBounds = screen.getClaimButtonBoundsForTest();
        int[] abandonBounds = screen.getAbandonButtonBoundsForTest();
        int[] assignBounds = screen.getAssignOthersButtonBoundsForTest();
        int[] titleBounds = screen.getDetailTitleFieldBoundsForTest();
        GuiTestSupport.assertTrue(claimBounds[1] >= titleBounds[1] + titleBounds[3], "团队按钮应位于标题框下方");
        GuiTestSupport.assertEquals(claimBounds[1], abandonBounds[1], "团队按钮应保持同一行纵向对齐");
        GuiTestSupport.assertEquals(abandonBounds[1], assignBounds[1], "团队按钮应保持同一行纵向对齐");
        GuiTestSupport.assertTrue(claimBounds[0] + claimBounds[2] <= abandonBounds[0], "放弃按钮应位于领取按钮右侧");
        GuiTestSupport.assertTrue(abandonBounds[0] + abandonBounds[2] <= assignBounds[0], "指派按钮应位于放弃按钮右侧");
        GuiTestSupport.assertEquals("领取", screen.getClaimButtonTextForTest(), "领取按钮应使用简短文案");
        GuiTestSupport.assertEquals("放弃", screen.getAbandonButtonTextForTest(), "放弃按钮应使用简短文案");
        GuiTestSupport.assertEquals("指派", screen.getAssignOthersButtonTextForTest(), "指派按钮应使用简短文案");
    }

    /**
     * 校验详情抽屉的关闭按钮会缩小并内嵌在标题行右侧。
     */
    private static void shouldLayoutDetailDrawerCloseRowSeparately() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-detail-layout", "Detail Layout Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 480, 300);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Detail Layout Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        int[] closeBounds = screen.getDetailCloseButtonBoundsForTest();
        int[] titleBounds = screen.getDetailTitleFieldBoundsForTest();
        assertRectInsideScreen(closeBounds, 480, 300, "详情抽屉关闭按钮应位于屏幕内");
        assertRectInsideScreen(titleBounds, 480, 300, "详情抽屉标题输入框应位于屏幕内");
        GuiTestSupport.assertTrue(closeBounds[0] >= titleBounds[0] + titleBounds[2], "关闭按钮应位于标题框右侧");
        GuiTestSupport.assertTrue(closeBounds[1] >= titleBounds[1], "关闭按钮应内嵌在标题行高度范围内");
        GuiTestSupport.assertTrue(closeBounds[1] + closeBounds[3] <= titleBounds[1] + titleBounds[3], "关闭按钮底边不应超出标题行");
        GuiTestSupport.assertEquals("×", screen.getDetailCloseButtonTextForTest(), "关闭按钮应使用乘号样式");
    }

    /**
     * 校验详情标题默认只读，点击后才进入编辑态。
     */
    private static void shouldEnterDetailTitleEditModeAfterClickingTitle() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Editable Detail Title");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertFalse(screen.isDetailTitleEditableForTest(), "详情标题默认应处于只读态");

        screen.beginDetailTitleEditingForTest();

        GuiTestSupport.assertTrue(screen.isDetailTitleEditableForTest(), "点击标题后应进入可编辑状态");
        ScreenDriver.setText(screen.getTitleFieldForTest(), "Editable Detail Title Updated");
        GuiTestSupport.assertEquals("Editable Detail Title Updated", task.getTitle(), "编辑详情标题后应同步更新选中任务");
    }

    /**
     * 校验紧凑窗口下点击关闭按钮会收起覆盖式详情抽屉并清空选中项。
     */
    private static void shouldCloseOverlayDetailDrawerFromCloseButton() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-detail-close", "Detail Close Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 360, 220);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Overlay Close Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);

        GuiTestSupport.assertTrue(screen.isDetailPanelVisibleForTest(), "关闭前紧凑窗口详情抽屉应已显示");

        screen.clickDetailCloseButtonForTest();

        GuiTestSupport.assertFalse(screen.isDetailPanelVisibleForTest(), "点击关闭按钮后覆盖式详情抽屉应收起");
        GuiTestSupport.assertNull(screen.getSelectedTaskForTest(), "点击关闭按钮后不应保留选中任务");
    }

    /**
     * 校验项目搜索不会把底部操作按钮从侧栏底部挤走。
     */
    private static void shouldKeepProjectActionButtonsPinnedAtSidebarBottom() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        createPersonalProject("personal-alpha", "Alpha");
        createPersonalProject("personal-beta", "Beta");
        createPersonalProject("personal-gamma", "Gamma");

        int[] listBoundsBefore = screen.getProjectListBoundsForTest();
        int[] addBoundsBefore = screen.getAddProjectButtonBoundsForTest();
        int[] editBoundsBefore = screen.getEditProjectButtonBoundsForTest();
        int[] deleteBoundsBefore = screen.getDeleteProjectButtonBoundsForTest();

        ScreenDriver.setText(screen.getProjectSearchFieldForTest(), "zz-not-found");

        int[] listBoundsAfter = screen.getProjectListBoundsForTest();
        int[] addBoundsAfter = screen.getAddProjectButtonBoundsForTest();
        int[] editBoundsAfter = screen.getEditProjectButtonBoundsForTest();
        int[] deleteBoundsAfter = screen.getDeleteProjectButtonBoundsForTest();

        GuiTestSupport.assertEquals(addBoundsBefore[1], addBoundsAfter[1], "项目搜索后新增按钮应保持固定在侧栏底部");
        GuiTestSupport.assertEquals(editBoundsBefore[1], editBoundsAfter[1], "项目搜索后编辑按钮应保持固定在侧栏底部");
        GuiTestSupport.assertEquals(deleteBoundsBefore[1], deleteBoundsAfter[1], "项目搜索后删除按钮应保持固定在侧栏底部");
        GuiTestSupport.assertEquals(listBoundsBefore[1], listBoundsAfter[1], "项目搜索不应改变列表起始位置");
        GuiTestSupport.assertTrue(listBoundsAfter[1] + listBoundsAfter[3] <= addBoundsAfter[1], "项目列表滚动区不应覆盖底部操作按钮区");
    }

    /**
     * 校验非成员查看团队项目时会显示“申请加入”，并隐藏删除按钮。
     */
    /**
     * 验证团队空间聚焦项目搜索框后会显示前缀下拉提示。
     */
    private static void shouldShowProjectSearchPrefixDropdownInTeamSpace() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        focusProjectSearchField(screen);

        GuiTestSupport.assertTrue(screen.isProjectSearchPrefixDropdownVisibleForTest(), "团队空间聚焦项目搜索框后应显示前缀下拉提示");
        GuiTestSupport.assertEquals(List.of("@me  我创建的项目", "@ma  我管理的项目", "@in  我加入的项目"),
                screen.getProjectSearchPrefixSuggestionTextsForTest(),
                "项目搜索前缀下拉应展示三条固定候选项");
    }

    /**
     * 验证点击项目搜索前缀候选项后会写入前缀并保留已有名称关键字。
     */
    private static void shouldApplyProjectSearchPrefixSuggestionAndPreserveNameQuery() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        ScreenDriver.setText(screen.getProjectSearchFieldForTest(), "核心");
        focusProjectSearchField(screen);

        clickProjectSearchPrefixSuggestion(screen, 1);

        GuiTestSupport.assertEquals("@ma 核心", screen.getProjectSearchFieldForTest().getValue(), "点击 @ma 候选项后应保留已有名称关键字");
        GuiTestSupport.assertFalse(screen.isProjectSearchPrefixDropdownVisibleForTest(), "点击前缀候选项后下拉应自动关闭");
    }

    /**
     * 验证已有未知前缀时点击候选项会替换前缀，并保留原有名称关键字。
     */
    private static void shouldReplaceUnknownProjectSearchPrefixWhenApplyingSuggestion() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        ScreenDriver.setText(screen.getProjectSearchFieldForTest(), "@mx 核心");
        focusProjectSearchField(screen);

        clickProjectSearchPrefixSuggestion(screen, 1);

        GuiTestSupport.assertEquals("@ma 核心", screen.getProjectSearchFieldForTest().getValue(), "未知前缀被建议项替换后应仅保留名称关键字");
        GuiTestSupport.assertFalse(screen.isProjectSearchPrefixDropdownVisibleForTest(), "替换未知前缀后下拉应自动关闭");
    }

    /**
     * 验证 @me 只会筛出我创建的团队项目。
     */
    private static void shouldFilterTeamProjectsByCreatedPrefix() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        Project createdProject = createTeamProject("team-owned-search", "Owned Search Team");
        createExternalTeamProject("team-external-search", "External Search Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        ScreenDriver.setText(screen.getProjectSearchFieldForTest(), "@me");

        GuiTestSupport.assertTrue(screen.getVisibleProjectNamesForTest().contains(ProjectNameFormatter.toDisplayText(defaultTeamProject).getString()),
                "@me 应包含我创建的默认团队项目");
        GuiTestSupport.assertTrue(screen.getVisibleProjectNamesForTest().contains(ProjectNameFormatter.toDisplayText(createdProject).getString()),
                "@me 应包含我创建的普通团队项目");
        GuiTestSupport.assertFalse(screen.getVisibleProjectNamesForTest().contains("External Search Team"),
                "@me 不应包含他人创建的团队项目");
    }

    /**
     * 验证 @ma 支持按管理身份和项目名关键字组合筛选。
     */
    private static void shouldFilterTeamProjectsByManagedPrefixAndName() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        createTeamProject("team-managed-owned", "核心 自建");
        Project leadProject = createExternalTeamProject("team-managed-lead", "核心 管理");
        leadProject.addMember(OWNER_ID.toString(), Project.ProjectRole.LEAD, "owner");
        Project memberProject = createExternalTeamProject("team-managed-member", "核心 参与");
        memberProject.addMember(OWNER_ID.toString(), Project.ProjectRole.MEMBER, "owner");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        ScreenDriver.setText(screen.getProjectSearchFieldForTest(), "@ma 核心");

        GuiTestSupport.assertTrue(screen.getVisibleProjectNamesForTest().contains("核心 自建"), "@ma 核心 应包含我创建且命中关键字的项目");
        GuiTestSupport.assertTrue(screen.getVisibleProjectNamesForTest().contains("核心 管理"), "@ma 核心 应包含我以组长身份管理的项目");
        GuiTestSupport.assertFalse(screen.getVisibleProjectNamesForTest().contains(ProjectNameFormatter.toDisplayText(memberProject).getString()),
                "@ma 核心 不应包含仅以普通成员加入的项目");
    }

    /**
     * 验证 @in 会筛出我已加入的全部团队项目。
     */
    private static void shouldFilterTeamProjectsByJoinedPrefix() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        Project memberProject = createExternalTeamProject("team-joined-member", "Joined Member Team");
        memberProject.addMember(OWNER_ID.toString(), Project.ProjectRole.MEMBER, "owner");
        Project leadProject = createExternalTeamProject("team-joined-lead", "Joined Lead Team");
        leadProject.addMember(OWNER_ID.toString(), Project.ProjectRole.LEAD, "owner");
        createExternalTeamProject("team-joined-outsider", "Joined Outsider Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        ScreenDriver.setText(screen.getProjectSearchFieldForTest(), "@in");

        GuiTestSupport.assertTrue(screen.getVisibleProjectNamesForTest().contains(ProjectNameFormatter.toDisplayText(defaultTeamProject).getString()),
                "@in 应包含我已加入的默认团队项目");
        GuiTestSupport.assertTrue(screen.getVisibleProjectNamesForTest().contains(ProjectNameFormatter.toDisplayText(memberProject).getString()),
                "@in 应包含我以普通成员加入的项目");
        GuiTestSupport.assertTrue(screen.getVisibleProjectNamesForTest().contains(ProjectNameFormatter.toDisplayText(leadProject).getString()),
                "@in 应包含我以组长身份加入的项目");
        GuiTestSupport.assertFalse(screen.getVisibleProjectNamesForTest().contains("Joined Outsider Team"),
                "@in 不应包含我未加入的项目");
    }

    /**
     * 验证未知前缀会退化为普通项目名搜索，不触发角色筛选语义。
     */
    private static void shouldTreatUnknownProjectSearchPrefixAsPlainNameQuery() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        Project namedProject = createExternalTeamProject("team-unknown-prefix", "@mx 特殊项目");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        ScreenDriver.setText(screen.getProjectSearchFieldForTest(), "@mx 特殊");

        GuiTestSupport.assertEquals(List.of(ProjectNameFormatter.toDisplayText(namedProject).getString()),
                screen.getVisibleProjectNamesForTest(),
                "未知前缀应退化为普通名称搜索");
    }

    /**
     * 验证个人空间不会显示团队项目搜索前缀下拉，且点击外部后会关闭下拉。
     */
    private static void shouldHideProjectSearchPrefixDropdownOutsideTeamSpace() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(defaultTeamProject);
        focusProjectSearchField(screen);
        GuiTestSupport.assertTrue(screen.isProjectSearchPrefixDropdownVisibleForTest(), "团队空间下拉应先显示");

        int[] contentBounds = screen.getContentAreaBoundsForTest();
        screen.mouseClicked(contentBounds[0] + 8, contentBounds[1] + 8, 0);
        GuiTestSupport.assertFalse(screen.isProjectSearchPrefixDropdownVisibleForTest(), "点击搜索框外部后应关闭前缀下拉");

        screen.switchProjectForTest(personalProject);
        focusProjectSearchField(screen);
        GuiTestSupport.assertFalse(screen.isProjectSearchPrefixDropdownVisibleForTest(), "个人空间不应显示团队项目搜索前缀下拉");
    }

    private static void shouldShowApplyJoinButtonForNonMemberTeamProject() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project outsiderProject = createExternalTeamProject("team-outsider", "Outsider Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(outsiderProject);

        GuiTestSupport.assertTrue(screen.isApplyJoinProjectButtonVisibleForTest(), "非成员团队项目应显示申请加入按钮");
        GuiTestSupport.assertFalse(screen.isDeleteProjectButtonVisibleForTest(), "非成员团队项目不应显示删除按钮");
    }

    /**
     * 校验普通成员进入团队项目时，编辑按钮文案会降级为“查看”。
     */
    private static void shouldUseViewLabelForReadOnlyTeamProjectMember() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project memberProject = createExternalTeamProject("team-member-readonly", "Readonly Team");
        memberProject.addMember(OWNER_ID.toString(), Project.ProjectRole.MEMBER, "owner");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(memberProject);

        GuiTestSupport.assertEquals("gui.todolist.project.view", screen.getEditProjectButtonTextForTest(), "普通成员进入团队项目时编辑按钮应降级为查看语义");
        GuiTestSupport.assertFalse(screen.isApplyJoinProjectButtonVisibleForTest(), "已加入团队项目后不应继续显示申请加入按钮");
        GuiTestSupport.assertTrue(screen.isDeleteProjectButtonVisibleForTest(), "已加入团队项目后应恢复删除按钮区域");
    }

    private static void shouldSwitchProjectToTeamScopeAndSyncActiveProject() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);

        GuiTestSupport.assertEquals(teamProject.getId(), screen.getCurrentProjectForTest().getId(), "切换后应选中团队项目");
        GuiTestSupport.assertEquals("TEAM_UNASSIGNED", screen.getViewModeNameForTest(), "切换到团队项目后应进入团队未分配视图");
        GuiTestSupport.assertEquals(teamProject.getId(), ops.getActiveProjectId(), "切换后应更新激活项目");
        GuiTestSupport.assertEquals(teamProject.getId(), ops.getActiveProjectSyncCalls().get(ops.getActiveProjectSyncCalls().size() - 1), "切换后应同步新的激活项目");
    }

    /**
     * 验证项目更新、删除与清空后，界面会自动回退到合适的项目与视图。
     */
    private static void shouldHandleProjectLifecycleChanges() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        Project defaultTeamProject = createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);

        Project updatedTeamProject = createTeamProject("team-dev", "Dev Team Updated");
        TodoListCommon.getProjectManager().updateProject(updatedTeamProject);
        GuiTestSupport.assertEquals(updatedTeamProject, screen.getCurrentProjectForTest(), "收到 UPDATED 事件后应替换为最新项目对象");

        TodoListCommon.getProjectManager().deleteProject(updatedTeamProject.getId());
        GuiTestSupport.assertEquals(defaultTeamProject.getId(), screen.getCurrentProjectForTest().getId(), "删除当前团队项目后应回退到默认团队项目");

        TodoListCommon.getProjectManager().clearAll();
        GuiTestSupport.assertNull(screen.getCurrentProjectForTest(), "收到 CLEARED 事件后应清空当前项目");
        GuiTestSupport.assertEquals("PERSONAL", screen.getViewModeNameForTest(), "项目清空后应回退到个人视图");
        GuiTestSupport.assertNull(ops.getActiveProjectId(), "项目清空后桥接层中的激活项目也应为空");

        Project personalProject = createDefaultPersonalProject();
        TodoListCommon.getProjectManager().addProject(personalProject);
        GuiTestSupport.assertEquals(personalProject.getId(), screen.getCurrentProjectForTest().getId(), "重新添加默认个人项目后应自动恢复选中");
    }

    /**
     * 验证编辑已选中的任务后，会即时更新任务内容并标记未保存状态。
     */
    private static void shouldEditSelectedTaskAndMarkUnsaved() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        Task task = screen.getFilteredTasksForTest().get(0);

        screen.selectTaskForTest(task);
        ScreenDriver.setText(screen.getTitleFieldForTest(), "Alpha Updated");
        ScreenDriver.setText(screen.getTagFieldForTest(), "red,urgent");

        GuiTestSupport.assertEquals("Alpha Updated", task.getTitle(), "编辑任务标题后应更新当前任务");
        GuiTestSupport.assertEquals(2, task.getTags().size(), "编辑标签后应拆分为两个标签");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "编辑任务后应标记存在未保存改动");
        GuiTestSupport.assertTrue(TodoScreen.hasPersonalUnsavedChanges(), "编辑个人任务后应同步个人未保存标记");
    }

    /**
     * 验证搜索与状态筛选可以共同作用，并返回预期任务。
     */
    /**
     * 验证手动拖拽排序后会标记未保存状态，并同步当前任务顺序。
     */
    private static void shouldMarkUnsavedAfterManualReorder() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        addTaskViaInput(screen, "Beta");
        addTaskViaInput(screen, "Gamma");
        screen.saveTasksForTest();
        GuiTestSupport.assertFalse(screen.hasUnsavedChangesForTest(), "保存后应先清除未保存状态");

        TaskListWidget widget = screen.getTaskListWidgetForTest();
        Task gamma = screen.getFilteredTasksForTest().stream()
                .filter(task -> "Gamma".equals(task.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应能找到 Gamma 任务"));
        Task alpha = screen.getFilteredTasksForTest().stream()
                .filter(task -> "Alpha".equals(task.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应能找到 Alpha 任务"));
        widget.ensureVisible(gamma);
        int interactX = widget.getInteractXForTest();
        int startY = widget.getTaskRowCenterYForTest(gamma.getId());
        int targetY = widget.getTaskRowCenterYForTest(alpha.getId()) - widget.getTaskItemHeightForTest() / 2;

        screen.mouseClicked(interactX, startY, 0);
        screen.mouseDragged(interactX, targetY, 0, 0, targetY - startY);
        screen.mouseReleased(interactX, targetY, 0);

        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "手动拖拽排序后应重新标记为未保存");
        GuiTestSupport.assertEquals(
                List.of("Gamma", "Alpha", "Beta"),
                screen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "手动拖拽排序后当前任务管理器应保留新的任务顺序"
        );
    }

    /**
     * 验证点击任务选中详情时，不会把任务列表滚动位置重置到顶部或底部。
     */
    private static void shouldKeepTaskListScrollOffsetWhenSelectingTask() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        for (int index = 0; index < 8; index++) {
            addTaskViaInput(screen, "Scroll Task " + index);
        }

        TaskListWidget widget = screen.getTaskListWidgetForTest();
        Task targetTask = requireTaskByTitle(screen, "Scroll Task 7");
        widget.ensureVisible(targetTask);
        int previousOffset = widget.getScrollOffsetForTest();
        GuiTestSupport.assertTrue(previousOffset > 0, "测试前应先滚动到非顶部位置");
        int clickX = widget.getInteractXForTest();
        int clickY = widget.getTaskRowCenterYForTest(targetTask.getId());

        screen.mouseClicked(clickX, clickY, 0);
        screen.mouseReleased(clickX, clickY, 0);

        GuiTestSupport.assertEquals(previousOffset, screen.getTaskListWidgetForTest().getScrollOffsetForTest(), "点击任务后应保持原有滚动偏移");
        GuiTestSupport.assertEquals(targetTask.getId(), screen.getSelectedTaskForTest().getId(), "点击任务后仍应正确选中目标任务");
    }

    /**
     * 验证把任务提升为高优先级后，会自动归位到更靠前的优先级分组。
     */
    private static void shouldMovePromotedTaskAheadOfLowerPriorities() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Medium First");
        addTaskViaInput(screen, "High Middle");
        addTaskViaInput(screen, "Low Last");

        Task mediumTask = requireTaskByTitle(screen, "Medium First");
        Task highTask = requireTaskByTitle(screen, "High Middle");
        Task lowTask = requireTaskByTitle(screen, "Low Last");
        mediumTask.setPriority(Task.Priority.MEDIUM);
        highTask.setPriority(Task.Priority.HIGH);
        lowTask.setPriority(Task.Priority.LOW);
        screen.switchProjectForTest(screen.getCurrentProjectForTest());

        screen.selectTaskForTest(lowTask);
        screen.openTaskContextMenuForTest(lowTask);
        screen.clickContextMenuItemForTest(0);

        GuiTestSupport.assertEquals(
                List.of("High Middle", "Low Last", "Medium First"),
                screen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "提升为高优先级后应移动到更靠前的优先级分组"
        );
    }

    /**
     * 验证把任务降为低优先级后，会自动归位到更靠后的优先级分组。
     */
    private static void shouldMoveDemotedTaskBehindHigherPriorities() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "High First");
        addTaskViaInput(screen, "Medium Middle");
        addTaskViaInput(screen, "Low Last");

        Task highTask = requireTaskByTitle(screen, "High First");
        Task mediumTask = requireTaskByTitle(screen, "Medium Middle");
        Task lowTask = requireTaskByTitle(screen, "Low Last");
        highTask.setPriority(Task.Priority.HIGH);
        mediumTask.setPriority(Task.Priority.MEDIUM);
        lowTask.setPriority(Task.Priority.LOW);
        screen.switchProjectForTest(screen.getCurrentProjectForTest());

        screen.selectTaskForTest(highTask);
        screen.openTaskContextMenuForTest(highTask);
        screen.clickContextMenuItemForTest(2);

        GuiTestSupport.assertEquals(
                List.of("Medium Middle", "Low Last", "High First"),
                screen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "降低为低优先级后应移动到更靠后的优先级分组"
        );
    }

    /**
     * 验证优先级归位时会保留同优先级任务之间的手动拖拽顺序。
     */
    private static void shouldKeepManualOrderInsidePriorityBucketAfterPriorityChange() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "High Alpha");
        addTaskViaInput(screen, "High Beta");
        addTaskViaInput(screen, "Gamma");

        Task highAlpha = requireTaskByTitle(screen, "High Alpha");
        Task highBeta = requireTaskByTitle(screen, "High Beta");
        Task gamma = requireTaskByTitle(screen, "Gamma");
        highAlpha.setPriority(Task.Priority.HIGH);
        highBeta.setPriority(Task.Priority.HIGH);
        gamma.setPriority(Task.Priority.MEDIUM);
        screen.switchProjectForTest(screen.getCurrentProjectForTest());

        dragTaskBefore(screen, highBeta, highAlpha);
        GuiTestSupport.assertEquals(
                List.of("High Beta", "High Alpha", "Gamma"),
                screen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "手动拖拽后同优先级任务顺序应先被任务管理器记录"
        );

        screen.selectTaskForTest(gamma);
        screen.openTaskContextMenuForTest(gamma);
        screen.clickContextMenuItemForTest(0);

        GuiTestSupport.assertEquals(
                List.of("High Beta", "High Alpha", "Gamma"),
                screen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "优先级归位后应保留原有高优先级任务之间的手动拖拽顺序"
        );
    }

    private static void shouldFilterTasksBySearchAndStatus() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        addTaskViaInput(screen, "Beta");

        ScreenDriver.setText(screen.getSearchFieldForTest(), "be");
        GuiTestSupport.assertEquals(1, screen.getFilteredTasksForTest().size(), "搜索后应只保留匹配关键字的任务");
        GuiTestSupport.assertEquals("Beta", screen.getFilteredTasksForTest().get(0).getTitle(), "搜索后应返回匹配标题的任务");

        ScreenDriver.setText(screen.getSearchFieldForTest(), "");
        Task alpha = screen.getFilteredTasksForTest().stream()
                .filter(task -> "Alpha".equals(task.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应能找到 Alpha 任务"));
        alpha.setCompleted(true);

        screen.switchProjectForTest(screen.getCurrentProjectForTest());

        GuiTestSupport.assertTrue(screen.getFilterStatusButtonForTest() == null, "待办界面不应再显示状态筛选按钮");
        GuiTestSupport.assertEquals("active", screen.getCurrentFilterForTest(), "当前筛选状态应固定为未完成列表");
        GuiTestSupport.assertEquals(1, screen.getFilteredTasksForTest().size(), "未完成列表中只应保留未完成任务");
        GuiTestSupport.assertEquals("Beta", screen.getFilteredTasksForTest().get(0).getTitle(), "未完成列表中应只显示 Beta");

        List<String> collapsedRows = screen.getTaskListWidgetForTest().getRowDebugSnapshotForTest();
        GuiTestSupport.assertEquals(3, collapsedRows.size(), "默认收起时应包含两个分组标题和一条未完成任务");
        GuiTestSupport.assertTrue(collapsedRows.get(0).startsWith("HEADER:"), "第一行应为未完成分组标题");
        GuiTestSupport.assertEquals("TASK:" + screen.getFilteredTasksForTest().get(0).getId(), collapsedRows.get(1), "收起时应仅显示 Beta 任务");
        GuiTestSupport.assertTrue(collapsedRows.get(2).startsWith("HEADER:"), "最后一行应为已完成分组标题");

        screen.toggleCompletedSectionForTest();

        List<String> expandedRows = screen.getTaskListWidgetForTest().getRowDebugSnapshotForTest();
        GuiTestSupport.assertEquals(4, expandedRows.size(), "展开已完成分组后应额外显示已完成任务");
        GuiTestSupport.assertEquals("TASK:" + alpha.getId(), expandedRows.get(3), "展开已完成分组后应显示 Alpha 任务");
    }

    /**
     * 验证保存个人任务后，会发送整表替换、清除未保存状态并返回父界面。
     */
    /**
     * 楠岃瘉涓汉瑙嗗浘涓嬪凡瀹屾垚浠诲姟鐐瑰嚮澶嶉€夋鍚庡彲浠ユ仮澶嶄负鏈畬鎴愩€?
     */
    private static void shouldAllowUncompleteCompletedTaskInPersonalView() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Completed Personal Task");
        Task task = screen.getCurrentManagerTasksForTest().get(0);
        task.setCompleted(true);
        screen.switchProjectForTest(screen.getCurrentProjectForTest());
        screen.toggleCompletedSectionForTest();

        TaskListWidget widget = screen.getTaskListWidgetForTest();
        screen.mouseClicked(widget.getCheckboxCenterXForTest(), widget.getCheckboxCenterYForTest(), 0);

        GuiTestSupport.assertFalse(screen.getCurrentManagerTasksForTest().get(0).isCompleted(), "个人视图下点击已完成任务的复选框后应恢复为未完成");
    }

    private static void shouldSavePersonalTasksAndClearUnsavedState() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        screen.saveTasksForTest();

        GuiTestSupport.assertEquals(1, ops.getReplaceAllTaskCalls().size(), "保存个人任务后应向桥接层发送整表替换");
        GuiTestSupport.assertFalse(screen.hasUnsavedChangesForTest(), "保存后应清除未保存状态");
        GuiTestSupport.assertEquals(1, minecraft.getTestPlayerMessages().size(), "保存成功后应给玩家发送一条提示消息");
        GuiTestSupport.assertNotNull(minecraft.getLastScreen(), "保存后应返回父界面");
    }

    /**
     * 验证关闭界面时会丢弃未保存的个人任务改动。
     */
    private static void shouldDiscardUnsavedPersonalChangesOnClose() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        GuiTestSupport.assertEquals(1, screen.getCurrentManagerTasksForTest().size(), "关闭前当前任务管理器中应存在未保存任务");

        screen.onClose();

        GuiTestSupport.assertEquals(0, screen.getCurrentManagerTasksForTest().size(), "关闭后未保存的个人任务应被丢弃");
        GuiTestSupport.assertEquals(0, ops.getRequestTeamSyncCallCount(), "关闭个人任务界面时不应请求团队同步");
        GuiTestSupport.assertFalse(TodoScreen.hasPersonalUnsavedChanges(), "关闭后应清除个人未保存标记");
    }

    /**
     * 验证关闭存在未保存改动的团队视图时，会请求团队数据同步。
     */
    private static void shouldRequestTeamSyncWhenClosingUnsavedTeamChanges() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Team Task");

        screen.onClose();

        GuiTestSupport.assertEquals(1, ops.getRequestTeamSyncCallCount(), "关闭未保存的团队视图时应请求团队同步");
        GuiTestSupport.assertFalse(screen.hasUnsavedChangesForTest(), "关闭后应清除团队视图的未保存状态");
    }

    /**
     * 验证未选中项目时新增任务，会显示提示且不会实际创建任务。
     */
    private static void shouldShowNotificationWhenAddingWithoutProject() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(null);
        ScreenDriver.setText(screen.getTitleFieldForTest(), "Orphan Task");
        addTaskViaEnter(screen);

        GuiTestSupport.assertEquals(1, screen.getNotificationCountForTest(), "未选择项目时新增任务应显示提示");
        GuiTestSupport.assertEquals(0, screen.getCurrentManagerTasksForTest().size(), "未选择项目时不应真正新增任务");
    }

    /**
     * 验证通过上下文菜单修改优先级后，会更新任务、关闭菜单并标记未保存。
     */
    private static void shouldOpenContextMenuAndApplyPriorityAction() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Alpha");
        Task task = screen.getFilteredTasksForTest().get(0);

        screen.selectTaskForTest(task);
        screen.openTaskContextMenuForTest(task);
        GuiTestSupport.assertTrue(screen.hasContextMenuForTest(), "打开上下文菜单后应处于菜单打开状态");
        GuiTestSupport.assertEquals(4, screen.getContextMenuItemTextsForTest().size(), "上下文菜单应包含四个操作项");

        screen.clickContextMenuItemForTest(0);

        GuiTestSupport.assertEquals(Task.Priority.HIGH, task.getPriority(), "通过上下文菜单修改后任务优先级应更新");
        GuiTestSupport.assertEquals(1, ops.getUpdateTaskCalls().size(), "通过上下文菜单修改优先级后应发送更新请求");
        GuiTestSupport.assertFalse(screen.hasContextMenuForTest(), "执行上下文菜单操作后应关闭菜单");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "通过上下文菜单修改任务后应标记未保存状态");
    }

    /**
     * 验证在分配玩家弹窗中搜索并选择成员后，会更新任务分配并返回主界面。
     */
    private static void shouldSearchAndAssignPlayerFromAssignScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        teamProject.addMember(ALICE_ID.toString(), Project.ProjectRole.MEMBER, "alice");
        teamProject.addMember(BOB_ID.toString(), Project.ProjectRole.MEMBER, "bob");
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(ALICE_ID, "alice"),
                createPlayerInfo(BOB_ID, "bob"),
                createPlayerInfo(CHARLIE_ID, "charlie"));
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Team Task");
        Task task = screen.getFilteredTasksForTest().get(0);

        Screen assignScreen = screen.createAssignPlayerScreenForTest(task);
        ScreenDriver.init(minecraft, assignScreen);
        GuiTestSupport.assertEquals(List.of("owner", "alice", "bob"), screen.getAssignablePlayerNamesForTest(assignScreen), "指派列表应只展示团队项目成员且 owner 只出现一次");
        screen.setAssignPlayerSearchForTest(assignScreen, "bo");
        GuiTestSupport.assertEquals(List.of("bob"), screen.getAssignablePlayerNamesForTest(assignScreen), "搜索分配玩家时应只保留匹配结果");

        screen.clickAssignPlayerRowForTest(assignScreen, 0);

        GuiTestSupport.assertEquals("bob", task.getAssigneeName(), "点击玩家后应把任务分配给对应成员");
        GuiTestSupport.assertEquals("20000000-0000-0000-0000-000000000003", task.getAssigneeUuid(), "分配后应写入对应玩家 UUID");
        GuiTestSupport.assertEquals(1, screen.getNotificationCountForTest(), "分配任务后应显示成功提示");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "分配任务后应标记未保存状态");
        GuiTestSupport.assertEquals(screen, minecraft.getLastScreen(), "完成分配后应返回主界面");
    }

    /**
     * 校验“指派他人”弹窗会保留离线项目成员，并允许直接将任务指派给该离线成员。
     */
    private static void shouldListOfflineProjectMembersInAssignScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-offline-member", "Offline Member Team");
        teamProject.addMember(ALICE_ID.toString(), Project.ProjectRole.MEMBER, "alice");
        teamProject.addMember(BOB_ID.toString(), Project.ProjectRole.MEMBER, "bob");
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(ALICE_ID, "alice"),
                createPlayerInfo(CHARLIE_ID, "charlie"));
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Offline Assign Task");
        Task task = screen.getFilteredTasksForTest().get(0);

        Screen assignScreen = screen.createAssignPlayerScreenForTest(task);
        ScreenDriver.init(minecraft, assignScreen);

        GuiTestSupport.assertEquals(List.of("owner", "alice", "bob"), screen.getAssignablePlayerNamesForTest(assignScreen), "离线项目成员也应出现在指派列表中，在线非成员不应出现");
        screen.setAssignPlayerSearchForTest(assignScreen, "bo");
        GuiTestSupport.assertEquals(List.of("bob"), screen.getAssignablePlayerNamesForTest(assignScreen), "搜索离线项目成员时也应命中缓存名称");

        screen.clickAssignPlayerRowForTest(assignScreen, 0);

        GuiTestSupport.assertEquals("bob", task.getAssigneeName(), "离线成员被选中后应写入缓存名称");
        GuiTestSupport.assertEquals(BOB_ID.toString(), task.getAssigneeUuid(), "离线成员被选中后应写入对应 UUID");
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "指派离线成员后仍应标记未保存");
        GuiTestSupport.assertEquals(screen, minecraft.getLastScreen(), "指派离线成员后应返回主界面");
    }

    /**
     * 验证“指派他人”弹窗在候选成员过多时会启用滚动列表，并将滚动偏移限制在最后一屏范围内。
     */
    private static void shouldClampAssignDialogScrollOffsetWhenMembersOverflow() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-assign-overflow", "Assign Overflow Team");
        teamProject.addMember(ALICE_ID.toString(), Project.ProjectRole.MEMBER, "alice");
        teamProject.addMember(BOB_ID.toString(), Project.ProjectRole.MEMBER, "bob");
        teamProject.addMember(CHARLIE_ID.toString(), Project.ProjectRole.MEMBER, "charlie");
        teamProject.addMember(DAVID_ID.toString(), Project.ProjectRole.MEMBER, "david");
        teamProject.addMember(ERIN_ID.toString(), Project.ProjectRole.MEMBER, "erin");
        teamProject.addMember(FRANK_ID.toString(), Project.ProjectRole.MEMBER, "frank");
        teamProject.addMember(GRACE_ID.toString(), Project.ProjectRole.MEMBER, "grace");
        teamProject.addMember(HEIDI_ID.toString(), Project.ProjectRole.MEMBER, "heidi");
        installOnlinePlayers(minecraft, createPlayerInfo(OWNER_ID, "owner"));
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Assign Overflow Task");
        Task task = screen.getFilteredTasksForTest().get(0);

        Screen assignScreen = screen.createAssignPlayerScreenForTest(task);
        ScreenDriver.init(minecraft, assignScreen);

        int candidateCount = screen.getAssignablePlayerNamesForTest(assignScreen).size();
        int visibleRows = screen.getAssignPlayerVisibleRowsForTest(assignScreen);
        GuiTestSupport.assertTrue(candidateCount > visibleRows, "候选成员超出可见行数时才能验证滚动列表");

        screen.scrollAssignPlayerListForTest(assignScreen, candidateCount + 2);

        int expectedMaxOffset = candidateCount - visibleRows;
        GuiTestSupport.assertEquals(expectedMaxOffset, screen.getAssignPlayerScrollOffsetForTest(assignScreen), "指派列表滚动时应停在最后一屏，不应越过可见范围");
    }

    /**
     * 验证小窗口下“指派他人”弹窗会压缩成员列表高度，确保底部取消按钮仍位于界面内部。
     */
    private static void shouldKeepAssignDialogCancelButtonInsideSmallScreen() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-small-screen", "Small Screen Team");
        teamProject.addMember(ALICE_ID.toString(), Project.ProjectRole.MEMBER, "alice");
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(ALICE_ID, "alice"));
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Small Screen Task");
        Task task = screen.getFilteredTasksForTest().get(0);

        Screen assignScreen = screen.createAssignPlayerScreenForTest(task);
        GuiTestSupport.initScreen(minecraft, assignScreen, 320, 170);

        List<Button> buttons = ScreenDriver.getButtons(assignScreen);
        GuiTestSupport.assertTrue(!buttons.isEmpty(), "指派弹窗初始化后应创建按钮");
        Button cancelButton = buttons.get(buttons.size() - 1);
        GuiTestSupport.assertTrue(cancelButton.getY() + cancelButton.getHeight() <= 170, "小窗口下取消按钮不应被挤出界面底部");
    }

    /**
     * 创建默认个人项目并加入项目管理器。
     *
     * @return 默认个人项目
     */
    private static Project createDefaultPersonalProject() {
        Project project = new Project(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_KEY, Project.Scope.PERSONAL, OWNER_ID.toString());
        project.setId(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建默认团队项目并加入项目管理器。
     *
     * @return 默认团队项目
     */
    private static Project createDefaultTeamProject() {
        Project project = new Project(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_KEY, Project.Scope.TEAM, OWNER_ID.toString());
        project.setId(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建一个团队项目并加入项目管理器。
     *
     * @param id 项目 ID
     * @param name 项目名称
     * @return 创建后的团队项目
     */
    private static Project createTeamProject(String id, String name) {
        Project project = new Project(name, Project.Scope.TEAM, OWNER_ID.toString());
        project.setId(id);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建一个额外的个人项目，供项目侧栏测试使用。
     *
     * @param id 项目标识
     * @param name 项目名称
     * @return 创建后的个人项目
     */
    private static Project createPersonalProject(String id, String name) {
        Project project = new Project(name, Project.Scope.PERSONAL, OWNER_ID.toString());
        project.setId(id);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 创建一个由外部玩家拥有的团队项目，默认不包含当前测试玩家成员身份。
     *
     * @param id 项目标识
     * @param name 项目名称
     * @return 创建后的团队项目
     */
    private static Project createExternalTeamProject(String id, String name) {
        Project project = new Project(name, Project.Scope.TEAM, EXTERNAL_OWNER_ID.toString());
        project.setId(id);
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 通过标题输入框新增一条任务。
     *
     * @param screen 待操作界面
     * @param title 任务标题
     */
    private static void addTaskViaInput(TodoScreen screen, String title) {
        ScreenDriver.setText(screen.getQuickAddFieldForTest(), title);
        addTaskViaEnter(screen);
    }

    /**
     * 通过回车键触发新增任务流程。
     *
     * @param screen 待操作界面
     */
    private static void addTaskViaEnter(TodoScreen screen) {
        screen.getQuickAddFieldForTest().setFocused(true);
        ScreenDriver.pressEnter(screen);
    }

    /**
     * 断言矩形区域完全位于给定屏幕范围内。
     *
     * @param bounds 待校验的矩形边界
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param message 断言失败提示
     */
    /**
     * 聚焦项目搜索输入框，触发前缀下拉提示显示。
     *
     * @param screen 目标界面
     */
    private static void focusProjectSearchField(TodoScreen screen) {
        int x = screen.getProjectSearchFieldForTest().getX() + 4;
        int y = screen.getProjectSearchFieldForTest().getY() + Math.max(1, screen.getProjectSearchFieldForTest().getHeight() / 2);
        screen.mouseClicked(x, y, 0);
    }

    /**
     * 点击指定的项目搜索前缀候选项。
     *
     * @param screen 目标界面
     * @param index 候选项索引
     */
    private static void clickProjectSearchPrefixSuggestion(TodoScreen screen, int index) {
        int[] bounds = screen.getProjectSearchPrefixSuggestionBoundsForTest(index);
        int x = bounds[0] + Math.max(1, bounds[2] / 2);
        int y = bounds[1] + Math.max(1, bounds[3] / 2);
        screen.mouseClicked(x, y, 0);
    }

    /**
     * 按标题查找当前界面中的任务，找不到时直接抛出断言。
     *
     * @param screen 目标界面
     * @param title 任务标题
     * @return 匹配到的任务
     */
    private static Task requireTaskByTitle(TodoScreen screen, String title) {
        return screen.getCurrentManagerTasksForTest().stream()
                .filter(task -> title.equals(task.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应能找到任务: " + title));
    }

    /**
     * 将源任务拖拽到目标任务之前，用于校验任务列表的手动排序逻辑。
     *
     * @param screen 目标界面
     * @param sourceTask 需要移动的任务
     * @param targetTask 目标位置前方的任务
     */
    private static void dragTaskBefore(TodoScreen screen, Task sourceTask, Task targetTask) {
        TaskListWidget widget = screen.getTaskListWidgetForTest();
        widget.ensureVisible(sourceTask);
        widget.ensureVisible(targetTask);
        int interactX = widget.getInteractXForTest();
        int startY = widget.getTaskRowCenterYForTest(sourceTask.getId());
        int targetY = widget.getTaskRowCenterYForTest(targetTask.getId()) - widget.getTaskItemHeightForTest() / 2;

        screen.mouseClicked(interactX, startY, 0);
        screen.mouseDragged(interactX, targetY, 0, 0, targetY - startY);
        screen.mouseReleased(interactX, targetY, 0);
    }

    /**
     * 通过右键菜单打开任务删除确认弹窗，并完成该弹窗的初始化。
     *
     * @param minecraft 测试客户端
     * @param screen 待办主界面
     * @param task 待删除任务
     * @return 已初始化的删除确认弹窗
     */
    private static Screen openDeleteTaskConfirmScreen(FakeMinecraftClient minecraft, TodoScreen screen, Task task) {
        screen.openTaskContextMenuForTest(task);
        screen.clickContextMenuItemForTest(3);
        Screen confirmScreen = minecraft.getLastScreen();
        GuiTestSupport.assertNotNull(confirmScreen, "点击删除后应弹出确认窗口");
        GuiTestSupport.assertTrue(confirmScreen instanceof ConfirmActionScreen, "点击删除后应打开任务删除确认弹窗");
        ScreenDriver.init(minecraft, confirmScreen);
        return confirmScreen;
    }

    /**
     * 点击确认类弹窗中的指定按钮，便于复用确认与取消流程测试。
     *
     * @param screen 弹窗界面
     * @param buttonIndex 按钮索引
     */
    private static void clickDialogButton(Screen screen, int buttonIndex) {
        List<Button> buttons = ScreenDriver.getButtons(screen);
        GuiTestSupport.assertTrue(buttonIndex >= 0 && buttonIndex < buttons.size(), "弹窗按钮索引应在可用范围内");
        ScreenDriver.click(buttons.get(buttonIndex));
    }

    private static void assertRectInsideScreen(int[] bounds, int screenWidth, int screenHeight, String message) {
        GuiTestSupport.assertTrue(bounds != null && bounds.length == 4, message + "（边界数据无效）");
        GuiTestSupport.assertTrue(bounds[0] >= 0, message + "（x 不应小于 0）");
        GuiTestSupport.assertTrue(bounds[1] >= 0, message + "（y 不应小于 0）");
        GuiTestSupport.assertTrue(bounds[2] >= 0, message + "（宽度不应小于 0）");
        GuiTestSupport.assertTrue(bounds[3] >= 0, message + "（高度不应小于 0）");
        GuiTestSupport.assertTrue(bounds[0] + bounds[2] <= screenWidth, message + "（右边界超出屏幕）");
        GuiTestSupport.assertTrue(bounds[1] + bounds[3] <= screenHeight, message + "（底边界超出屏幕）");
    }

    /**
     * 为测试客户端安装在线玩家列表。
     *
     * @param minecraft 测试客户端
     * @param players 在线玩家列表
     */
    private static void installOnlinePlayers(FakeMinecraftClient minecraft, PlayerInfo... players) {
        FakeClientConnection connection = (FakeClientConnection) minecraft.getConnection();
        connection.setOnlinePlayers(List.of(players));
    }

    /**
     * 创建一条测试用在线玩家信息。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @return 测试玩家信息对象
     */
    private static PlayerInfo createPlayerInfo(UUID uuid, String name) {
        return GuiTestSupport.createPlayerInfo(uuid, name);
    }

    /**
     * 验证 LAN 主机保存个人任务后，重新进入单人世界仍能看到刚才保存的任务。
     */
    private static void shouldKeepPersonalTasksAfterSavingInPublishedLocalWorld() {
        GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient publishedMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        publishedMinecraft.setLocalServer(true);
        publishedMinecraft.setIntegratedServer(createPublishedIntegratedServer());
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen publishedScreen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(publishedMinecraft, publishedScreen);
        addTaskViaInput(publishedScreen, "Published Personal Task");
        publishedScreen.saveTasksForTest();

        GuiTestSupport.assertEquals(List.of("Published Personal Task"),
                TodoListCommon.getTaskStorage().loadTasksSafe().stream().map(Task::getTitle).toList(),
                "LAN 主机保存个人任务后应同步写入本地文件");

        TodoScreen.resetGuiStateForTest();
        FakeMinecraftClient singleplayerMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        singleplayerMinecraft.setLocalServer(true);
        TodoScreen singleplayerScreen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(singleplayerMinecraft, singleplayerScreen);

        GuiTestSupport.assertEquals(List.of("Published Personal Task"),
                singleplayerScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重新进入单人世界后应仍能看到之前保存的个人任务");
    }

    /**
     * 验证更贴近真实操作的流程。
     * 玩家从单人进入后发布局域网、保存个人任务、关闭界面，再重新进入单人世界时仍能看到原任务。
     */
    private static void shouldKeepPersonalTasksAfterPublishedLocalWorldReentryFlow() {
        GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient lanHostMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        lanHostMinecraft.setLocalServer(true);
        lanHostMinecraft.setIntegratedServer(createPublishedIntegratedServer());
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen lanHostScreen = new TodoScreen(ScreenDriver.createParentScreen("pause"));

        ScreenDriver.init(lanHostMinecraft, lanHostScreen);
        addTaskViaInput(lanHostScreen, "LAN Host Journey Task");
        GuiTestSupport.assertTrue(lanHostScreen.hasUnsavedChangesForTest(), "发布局域网后新增个人任务应先标记为未保存");

        lanHostScreen.saveTasksForTest();

        GuiTestSupport.assertNotNull(lanHostMinecraft.getLastScreen(), "保存个人任务后应关闭当前界面并返回父界面");
        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                TodoListCommon.getTaskStorage().loadTasksSafe().stream().map(Task::getTitle).toList(),
                "LAN 主机保存个人任务后应同步写入本地文件");
        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                loadPlayerTaskTitles(OWNER_ID),
                "LAN 主机保存个人任务后也应写入主机玩家文件");

        TodoScreen.resetGuiStateForTest();
        FakeMinecraftClient singleplayerMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        singleplayerMinecraft.setLocalServer(true);
        singleplayerMinecraft.setIntegratedServer(createUnpublishedIntegratedServer());
        TodoScreen singleplayerScreen = new TodoScreen(ScreenDriver.createParentScreen("pause"));

        ScreenDriver.init(singleplayerMinecraft, singleplayerScreen);

        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                singleplayerScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "退出局域网主机后重新进入单人世界仍应看到刚才保存的个人任务");
        GuiTestSupport.assertEquals(List.of("LAN Host Journey Task"),
                TodoListCommon.getTaskStorage().loadTasksSafe().stream().map(Task::getTitle).toList(),
                "重新进入单人世界后本地个人任务文件应保持最新内容");
    }

    /**
     * 验证远程服务端中先改个人任务、再切团队任务并点击保存时，两侧改动都会一起提交。
     */
    private static void shouldSavePersonalAndTeamTasksWhenSavingFromTeamViewOnRemoteServer() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-remote-save", "Remote Save Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("remote"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Remote Personal Task");
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Remote Team Task");

        screen.saveTasksForTest();

        GuiTestSupport.assertEquals(1, ops.getReplaceAllTaskCalls().size(), "从团队视图保存时也应提交个人任务整表");
        GuiTestSupport.assertEquals(1, ops.getReplaceTeamTaskCalls().size(), "从团队视图保存时应提交团队任务整表");
        GuiTestSupport.assertEquals(List.of("Remote Personal Task"),
                ops.getReplaceAllTaskCalls().get(0).stream().map(Task::getTitle).toList(),
                "个人任务提交内容应包含刚新增的个人任务");
        GuiTestSupport.assertEquals(List.of("Remote Team Task"),
                ops.getReplaceTeamTaskCalls().get(0).stream().map(Task::getTitle).toList(),
                "团队任务提交内容应包含刚新增的团队任务");
        GuiTestSupport.assertNotNull(minecraft.getLastScreen(), "保存成功后应关闭当前界面");

        TodoScreen reopenScreen = new TodoScreen(ScreenDriver.createParentScreen("remote"));
        ScreenDriver.init(minecraft, reopenScreen);
        GuiTestSupport.assertEquals(personalProject.getId(), reopenScreen.getCurrentProjectForTest().getId(), "重新打开 GUI 后应仍能回到个人项目");
        GuiTestSupport.assertEquals(List.of("Remote Personal Task"),
                reopenScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重新打开 GUI 后应能看到保存过的个人任务");
        reopenScreen.switchProjectForTest(teamProject);
        GuiTestSupport.assertEquals(List.of("Remote Team Task"),
                reopenScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重新打开 GUI 后应能看到保存过的团队任务");
    }

    /**
     * 验证远程服务端中先改团队任务、再切个人任务并点击保存后，团队任务会真正持久化并在重连后恢复。
     */
    private static void shouldKeepPersonalAndTeamTasksAfterRemoteReconnectWhenSavingFromPersonalView() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project personalProject = createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-remote-rejoin", "Remote Rejoin Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("remote"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Remote Team Persisted Task");
        screen.switchProjectForTest(personalProject);
        addTaskViaInput(screen, "Remote Personal Persisted Task");

        screen.saveTasksForTest();

        GuiTestSupport.assertEquals(1, ops.getReplaceAllTaskCalls().size(), "从个人视图保存时应提交个人任务整表");
        GuiTestSupport.assertEquals(1, ops.getReplaceTeamTaskCalls().size(), "从个人视图保存时也应提交团队任务整表");

        TodoScreen sameSessionScreen = new TodoScreen(ScreenDriver.createParentScreen("remote"));
        ScreenDriver.init(minecraft, sameSessionScreen);
        GuiTestSupport.assertEquals(List.of("Remote Personal Persisted Task"),
                sameSessionScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "同一联机会话内重新打开 GUI 时应能看到保存过的个人任务");
        sameSessionScreen.switchProjectForTest(teamProject);
        GuiTestSupport.assertEquals(List.of("Remote Team Persisted Task"),
                sameSessionScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "同一联机会话内重新打开 GUI 时应能看到保存过的团队任务");

        RecordingClientOps reconnectOps = new RecordingClientOps();
        restoreTasksToManager(reconnectOps.getTeamTaskManager(), ops.getReplaceTeamTaskCalls().get(0));
        ClientBridge.setOps(reconnectOps);
        TodoScreen.resetGuiStateForTest();

        FakeMinecraftClient reconnectMinecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        TodoScreen reconnectScreen = new TodoScreen(ScreenDriver.createParentScreen("remote"));
        ScreenDriver.init(reconnectMinecraft, reconnectScreen);

        GuiTestSupport.assertEquals(personalProject.getId(), reconnectScreen.getCurrentProjectForTest().getId(), "重连后默认应仍能进入个人项目");
        GuiTestSupport.assertEquals(List.of("Remote Personal Persisted Task"),
                reconnectScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重连后应能从个人任务存储恢复个人任务");
        reconnectScreen.switchProjectForTest(teamProject);
        GuiTestSupport.assertEquals(List.of("Remote Team Persisted Task"),
                reconnectScreen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "重连后应能从服务端同步结果恢复团队任务");
    }

    /**
     * 创建一个已发布 LAN 的测试集成服。
     *
     * @return 已发布状态的测试集成服
     */
    private static PublishedIntegratedServer createPublishedIntegratedServer() {
        PublishedIntegratedServer server = GuiTestSupport.allocate(PublishedIntegratedServer.class);
        server.setTestPublished(true);
        return server;
    }

    /**
     * 创建一个未发布 LAN 的测试集成服。
     *
     * @return 未发布状态的测试集成服
     */
    private static PublishedIntegratedServer createUnpublishedIntegratedServer() {
        PublishedIntegratedServer server = GuiTestSupport.allocate(PublishedIntegratedServer.class);
        server.setTestPublished(false);
        return server;
    }

    /**
     * 读取指定玩家文件中的个人任务标题。
     *
     * @param playerId 玩家 UUID
     * @return 玩家文件中的个人任务标题列表
     */
    private static List<String> loadPlayerTaskTitles(UUID playerId) {
        try {
            return TodoListCommon.getTaskStorage().loadPlayerTasks(playerId).stream().map(Task::getTitle).toList();
        } catch (Exception e) {
            throw new IllegalStateException("读取玩家个人任务失败", e);
        }
    }

    /**
     * 将任务列表复制回指定任务管理器，模拟重连后服务端重新同步团队任务。
     *
     * @param manager 目标任务管理器
     * @param tasks 需要恢复的任务列表
     */
    private static void restoreTasksToManager(com.todolist.task.TaskManager manager, List<Task> tasks) {
        if (manager == null) {
            return;
        }
        manager.clearAll();
        if (tasks == null) {
            return;
        }
        for (Task task : tasks) {
            manager.addTask(copyTask(task));
        }
    }

    /**
     * 复制一条任务，避免不同测试阶段共享同一个任务对象引用。
     *
     * @param task 原始任务
     * @return 复制后的任务
     */
    /**
     * 验证个人空间只显示“我的”视图，并保持当前视图为“我的”。
     */
    private static void shouldShowOnlyMyViewInPersonalSpaceUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertEquals("PERSONAL", screen.getCurrentSpaceModeNameForTest(), "默认个人项目下应解析为个人空间");
        GuiTestSupport.assertEquals(List.of("MY"), screen.getVisibleTaskViewOptionNamesForTest(), "个人空间应只显示“我的”视图");
        GuiTestSupport.assertEquals("MY", screen.getCurrentTaskViewOptionNameForTest(), "个人空间当前视图应为“我的”");
    }

    /**
     * 验证团队空间会暴露“待分配 / 全部 / 我的”三个视图，并默认落在“待分配”。
     */
    private static void shouldShowUnassignedAllAndMineViewsInTeamSpaceUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-dev", "Dev Team");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);

        GuiTestSupport.assertEquals("TEAM", screen.getCurrentSpaceModeNameForTest(), "切换团队项目后应解析为团队空间");
        GuiTestSupport.assertEquals(List.of("UNASSIGNED", "ALL", "MY"), screen.getVisibleTaskViewOptionNamesForTest(), "团队空间应显示三个团队视图");
        GuiTestSupport.assertEquals("UNASSIGNED", screen.getCurrentTaskViewOptionNameForTest(), "团队空间默认视图应为“待分配”");
    }

    /**
     * 验证团队“全部”视图会展示当前项目下的全部任务。
     */
    private static void shouldShowAllProjectTasksInTeamAllViewUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-all-view", "Team All View");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "Unassigned");
        addTaskViaInput(screen, "Mine");
        addTaskViaInput(screen, "Others");
        List<Task> teamTasks = screen.getCurrentManagerTasksForTest();
        Task unassigned = teamTasks.stream().filter(task -> "Unassigned".equals(task.getTitle())).findFirst()
                .orElseThrow(() -> new AssertionError("应能找到 Unassigned 任务"));
        Task mine = teamTasks.stream().filter(task -> "Mine".equals(task.getTitle())).findFirst()
                .orElseThrow(() -> new AssertionError("应能找到 Mine 任务"));
        Task others = teamTasks.stream().filter(task -> "Others".equals(task.getTitle())).findFirst()
                .orElseThrow(() -> new AssertionError("应能找到 Others 任务"));
        mine.setAssigneeUuid(OWNER_ID.toString());
        mine.setAssigneeName("owner");
        others.setAssigneeUuid(ALICE_ID.toString());
        others.setAssigneeName("alice");

        screen.switchToTeamAllViewForTest();

        List<String> titles = screen.getFilteredTasksForTest().stream().map(Task::getTitle).toList();
        GuiTestSupport.assertEquals("ALL", screen.getCurrentTaskViewOptionNameForTest(), "切换后当前任务视图应为“全部”");
        GuiTestSupport.assertTrue(titles.contains("Unassigned"), "团队全部视图应展示未分配任务");
        GuiTestSupport.assertTrue(titles.contains("Mine"), "团队全部视图应展示分配给自己的任务");
        GuiTestSupport.assertTrue(titles.contains("Others"), "团队全部视图应展示分配给其他成员的任务");
    }

    /**
     * 验证团队成员在“全部”视图下会复用“待分配”视图的新增权限逻辑。
     */
    private static void shouldAllowMemberAddTaskInTeamAllViewWhenProjectSettingEnabledUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project memberProject = createExternalTeamProject("team-all-member-create", "Team All Member Create");
        memberProject.addMember(OWNER_ID.toString(), Project.ProjectRole.MEMBER, "owner");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(memberProject);
        screen.switchToTeamAllViewForTest();

        addTaskViaInput(screen, "Blocked In Team All");
        GuiTestSupport.assertEquals(0, screen.getCurrentManagerTasksForTest().size(), "未开启允许成员创建时，团队全部视图不应允许普通成员新增任务");
        GuiTestSupport.assertEquals(1, screen.getNotificationCountForTest(), "被权限拦截时应显示提示");

        memberProject.setAllowMemberCreate(true);
        addTaskViaInput(screen, "Allowed In Team All");

        GuiTestSupport.assertEquals(
                List.of("Allowed In Team All"),
                screen.getCurrentManagerTasksForTest().stream().map(Task::getTitle).toList(),
                "开启允许成员创建后，团队全部视图应允许普通成员新增任务"
        );
        GuiTestSupport.assertTrue(screen.hasUnsavedChangesForTest(), "新增团队任务后应标记存在未保存改动");
    }

    /**
     * 验证折叠未完成分组不会改变当前空间与任务视图。
     */
    private static void shouldToggleActiveSectionWithoutChangingCurrentViewUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-active-toggle", "Team Active Toggle");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        addTaskViaInput(screen, "A1");
        addTaskViaInput(screen, "A2");
        String originalSpaceMode = screen.getCurrentSpaceModeNameForTest();
        String originalTaskView = screen.getCurrentTaskViewOptionNameForTest();
        int[] taskListBounds = screen.getTaskListBoundsForTest();
        int headerClickX = taskListBounds[0] + 10;
        int activeHeaderClickY = screen.getTaskListWidgetForTest().getSectionHeaderCenterYForTest("active");

        GuiTestSupport.assertTrue(screen.isActiveSectionExpandedForTest(), "默认情况下未完成分组应处于展开状态");

        screen.mouseClicked(headerClickX, activeHeaderClickY, 0);

        GuiTestSupport.assertFalse(screen.isActiveSectionExpandedForTest(), "切换后未完成分组应收起");
        GuiTestSupport.assertEquals(originalSpaceMode, screen.getCurrentSpaceModeNameForTest(), "切换未完成分组不应改变当前空间");
        GuiTestSupport.assertEquals(originalTaskView, screen.getCurrentTaskViewOptionNameForTest(), "切换未完成分组不应改变当前任务视图");
    }

    /**
     * 验证折叠已完成分组不会改变当前空间与任务视图。
     */
    private static void shouldToggleCompletedSectionWithoutChangingCurrentViewUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        Project teamProject = createTeamProject("team-completed-toggle", "Team Completed Toggle");
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        screen.switchProjectForTest(teamProject);
        String originalSpaceMode = screen.getCurrentSpaceModeNameForTest();
        String originalTaskView = screen.getCurrentTaskViewOptionNameForTest();

        GuiTestSupport.assertFalse(screen.isCompletedSectionExpandedForTest(), "默认情况下已完成分组应处于收起状态");

        screen.toggleCompletedSectionForTest();

        GuiTestSupport.assertTrue(screen.isCompletedSectionExpandedForTest(), "切换后已完成分组应展开");
        GuiTestSupport.assertEquals(originalSpaceMode, screen.getCurrentSpaceModeNameForTest(), "切换已完成分组不应改变当前空间");
        GuiTestSupport.assertEquals(originalTaskView, screen.getCurrentTaskViewOptionNameForTest(), "切换已完成分组不应改变当前任务视图");

        screen.toggleCompletedSectionForTest();

        GuiTestSupport.assertFalse(screen.isCompletedSectionExpandedForTest(), "再次切换后已完成分组应恢复收起");
        GuiTestSupport.assertEquals(originalTaskView, screen.getCurrentTaskViewOptionNameForTest(), "反复切换已完成分组也不应改变当前任务视图");
    }

    /**
     * 验证保存和取消按钮位于底部独立一行，并在整个 GUI 底部水平居中。
     */
    private static void shouldKeepBottomActionButtonsCenteredAboveQuickAddUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        GuiTestSupport.initScreen(minecraft, screen, 420, 250);

        int[] saveBounds = screen.getSaveButtonBoundsForTest();
        int[] cancelBounds = screen.getCancelButtonBoundsForTest();
        int[] quickAddBounds = screen.getQuickAddFieldBoundsForTest();
        int[] taskListBounds = screen.getTaskListBoundsForTest();
        int actionsCenterX = (saveBounds[0] + cancelBounds[0] + cancelBounds[2]) / 2;
        int screenCenterX = 420 / 2;
        int buttonsBottom = Math.max(saveBounds[1] + saveBounds[3], cancelBounds[1] + cancelBounds[3]);

        GuiTestSupport.assertTrue(taskListBounds[1] + taskListBounds[3] <= quickAddBounds[1], "任务列表应位于底部快速新增输入框上方");
        GuiTestSupport.assertTrue(quickAddBounds[1] + quickAddBounds[3] <= saveBounds[1], "保存按钮应位于底部快速新增输入框下方");
        GuiTestSupport.assertTrue(quickAddBounds[1] + quickAddBounds[3] <= cancelBounds[1], "取消按钮应位于底部快速新增输入框下方");
        GuiTestSupport.assertTrue(Math.abs(actionsCenterX - screenCenterX) <= 2, "保存和取消按钮应在整个GUI底部保持水平居中");
        GuiTestSupport.assertTrue(250 - buttonsBottom <= 12, "保存和取消按钮应贴近整个GUI底部");
    }

    /**
     * 验证标题与描述输入框在点击详情区空白时会失焦，但不会清空当前选中任务。
     */
    private static void shouldBlurDetailInputsWhenClickingOutsideFieldsUtf8() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        createDefaultPersonalProject();
        createDefaultTeamProject();
        TodoScreen screen = new TodoScreen(ScreenDriver.createParentScreen("parent"));

        ScreenDriver.init(minecraft, screen);
        addTaskViaInput(screen, "Focusable Task");
        Task task = screen.getFilteredTasksForTest().get(0);
        screen.selectTaskForTest(task);
        screen.beginDetailTitleEditingForTest();

        int[] detailBounds = screen.getDetailPanelBoundsForTest();
        int[] titleBounds = screen.getDetailTitleFieldBoundsForTest();
        int[] descBounds = screen.getDescFieldBoundsForTest();
        int blankX = detailBounds[0] + 10;
        int blankY = Math.min(detailBounds[1] + detailBounds[3] - 10, titleBounds[1] + titleBounds[3] + 4);
        if (blankY >= descBounds[1]) {
            blankY = descBounds[1] - 4;
        }

        GuiTestSupport.assertTrue(screen.getTitleFieldForTest().isFocused(), "开始编辑后标题输入框应获取焦点");

        screen.mouseClicked(blankX, blankY, 0);

        GuiTestSupport.assertFalse(screen.getTitleFieldForTest().isFocused(), "点击标题框外后标题输入框应失焦");
        GuiTestSupport.assertEquals(task.getId(), screen.getSelectedTaskForTest().getId(), "点击详情区空白时不应清空当前选中任务");

        screen.getDescFieldForTest().setFocused(true);
        screen.mouseClicked(blankX, blankY, 0);

        GuiTestSupport.assertFalse(screen.getDescFieldForTest().isFocused(), "点击描述框外后描述输入框应失焦");
        GuiTestSupport.assertEquals(task.getId(), screen.getSelectedTaskForTest().getId(), "描述框失焦时也不应清空当前选中任务");
    }

    private static Task copyTask(Task task) {
        return task == null ? null : Task.fromNbt(task.toNbt());
    }

    /**
     * 用于 GUI 回归测试的 IntegratedServer 替身。
     * 允许直接控制是否模拟已发布 LAN。
     */
    private static final class PublishedIntegratedServer extends IntegratedServer {
        private boolean testPublished;

        /**
         * 仅为满足父类构造参数而保留。
         * 测试中通过 Unsafe 分配对象，不会真正走到这条路径。
         */
        private PublishedIntegratedServer() {
            super(null, null, null, null, null, null, null);
        }

        /**
         * 返回当前测试集成服是否模拟为已发布 LAN。
         *
         * @return true 表示模拟已发布 LAN
         */
        @Override
        public boolean isPublished() {
            return testPublished;
        }

        /**
         * 设置当前测试集成服的发布状态。
         *
         * @param published 是否模拟为已发布 LAN
         */
        void setTestPublished(boolean published) {
            this.testPublished = published;
        }
    }
}
