package com.todolist.gui;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.client.ClientTaskStorageHelper;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.client.TodoHudRenderer;
import com.todolist.compat.ScreenCompat;
import com.todolist.config.ModConfig;
import com.todolist.gui.TodoScreenLayoutSupport.LayoutRect;
import com.todolist.gui.TodoScreenLayoutSupport.MainLayoutMetrics;
import com.todolist.gui.TodoScreenLayoutSupport.ResponsiveTier;
import com.todolist.gui.TodoScreenProjectSearchSupport.ProjectSearchPrefixOption;
import com.todolist.gui.TodoScreenProjectSearchSupport.ProjectSearchQuery;
import com.todolist.gui.TodoScreenProjectSearchSupport.ProjectSearchRoleFilter;
import com.todolist.platform.DataPathProvider;
import com.todolist.storage.H2ConnectionProvider;
import com.todolist.storage.H2TaskQueryService;
import com.todolist.storage.H2TaskStore;
import com.todolist.storage.H2MaintenanceGuard;
import com.todolist.storage.StorageFailureNotifier;
import com.todolist.storage.StorageBackendFactory;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.task.Task;
import com.todolist.task.TaskAssignmentSupport;
import com.todolist.task.TaskManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

/**
 * 待办主界面，负责项目侧栏、任务列表、详情面板和相关弹窗的交互。
 */
public class TodoScreen extends Screen implements ProjectManager.ProjectChangeListener {
    private static final Component TITLE = Component.translatable("gui.todolist.title");
    private static final ExecutorService GUI_STORAGE_LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TodoList GUI Storage Loader");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService TASK_SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "TodoList GUI Task Save");
        thread.setDaemon(true);
        return thread;
    });
    private static String cachedPersonalTasksNamespace = "";
    private static List<Task> cachedPersonalTasksSnapshot = List.of();
    private static List<Task> cachedTeamTasksSnapshot = List.of();
    private static List<Task> deferredTeamTasksSnapshot = null;
    private static String deferredTeamTasksNamespace = "";
    private static List<Task> pendingTeamAuthoritativeBaseSnapshot = null;
    private static List<Task> pendingTeamAuthoritativeSubmittedSnapshot = null;
    private static String pendingTeamAuthoritativeNamespace = "";


    private final Screen parent;
    private ProjectManager projectManager;
    private ProjectListWidget projectListWidget;
    private Project currentProject;
    private TaskManager taskManager;
    private TaskManager personalTaskManager;
    private TaskManager teamTaskManager;
    private TaskListWidget taskListWidget;
    private final List<TodoScreenNotificationSupport.NotificationEntry> notifications = new ArrayList<>();

    private ViewMode viewMode = ViewMode.PERSONAL;
    private SpaceMode currentSpaceMode = SpaceMode.PERSONAL;
    private TaskViewOption currentTaskViewOption = TaskViewOption.MY;
    private boolean activeExpanded = true;
    private boolean completedExpanded;

    // Input fields
    private EditBox searchField;
    private EditBox quickAddField;
    private EditBox titleField;
    private MultiLineEditBox descField;
    private EditBox tagField;

    // Action buttons
    private Button detailCloseButton;
    private Button addSubtaskButton;
    private Button claimButton;
    private Button abandonButton;
    private Button assignOthersButton;
    private Button saveButton;
    private Button cancelButton;
    private Button clearCompletedButton;
    private Button sidebarToggleButton;

    // Priority selected for task creation or editing
    private Task.Priority selectedPriority = Task.Priority.MEDIUM;

    // Filter and config buttons
    private Button filterStatusButton;
    private Button filterPriorityButton;
    private Button viewToggleButton;
    private Button configButton;
    
    // Project search and sidebar state
    private EditBox projectSearchField;
    private boolean projectSearchPrefixDropdownOpen;
    private Button addProjectBtn;
    private Button personalSpaceButton;
    private Button teamSpaceButton;
    private Button myViewButton;
    private Button unassignedViewButton;
    private Button allViewButton;
    private Button editProjectBtn;
    private Button deleteProjectBtn;
    private Button applyJoinProjectBtn;
    private Project.Scope projectScopeFilter = Project.Scope.PERSONAL;
    private String projectSearchQuery = "";
    private String preferredPersonalProjectId;
    private String preferredTeamProjectId;
    private boolean teamProjectsEnabled = true;
    private int savedProjectListScrollOffset;
    private int savedTaskListScrollOffset;
    /**
     * widget 重建过程中保留的“用户已展开的父任务 ID 集合”。
     * 每次进入 {@link #rebuildUI()} 时从旧 widget 读出，重建后再写回新 widget，
     * 避免“点击任务行触发选中”导致子任务展开状态丢失。
     */
    private Set<String> savedExpandedParentTaskIds = new LinkedHashSet<>();

    private int currentPriorityFilter = 0; // 0=All, 1=High, 2=Medium, 3=Low
    
    private Task selectedTask;
    private Task pendingClickSelectionTask;
    private List<Task> filteredTasks = new ArrayList<>();
    private List<Task> baseFilteredTasks = new ArrayList<>();
    private String searchQuery = "";
    private boolean hasUnsavedChanges = false;
    private static boolean personalHasUnsavedChanges = false;
    private static boolean teamHasUnsavedChanges = false;
    private static long personalSaveVersion;
    private static long teamSaveVersion;
    private static LastGuiState lastGuiState;
    private String openedStorageNamespace = DataPathProvider.getStorageNamespace();
    private Task contextMenuTask;
    private int contextMenuX;
    private int contextMenuY;
    private int contextMenuWidth;
    private int contextMenuItemHeight = 18;
    private List<ContextMenuItem> contextMenuItems = new ArrayList<>();
    private ResponsiveTier responsiveTier = ResponsiveTier.LARGE;
    private boolean sidebarOverlayVisible;
    private boolean detailOverlayVisible;
    private MainLayoutMetrics layoutMetrics;
    private TaskDetailDraft detailDraft;
    private boolean syncingDetailWidgets;
    private boolean personalTasksLoading;
    private boolean taskSaveInFlight;
    private boolean personalTaskSaveInFlight;
    private boolean teamTaskSaveInFlight;
    private int pendingTaskSaveCount;
    private int pendingPersonalTaskSaveCount;
    private int pendingTeamTaskSaveCount;


    /**
     * 创建待办主界面。
     *
     * @param parent 父级界面
     */
    public TodoScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        openedStorageNamespace = DataPathProvider.getStorageNamespace();

        // Initialize the personal task manager from local storage.
        if (personalTaskManager == null) {
            personalTaskManager = new TaskManager();
            if (StorageBackendFactory.isH2Selected()) {
                loadCachedPersonalTasksIntoManager();
                schedulePersonalTasksLoadFromStorage();
            } else {
                try {
                    List<Task> loadedTasks = ClientTaskStorageHelper.loadPersonalTasks(TodoListCommon.getTaskStorage(), this.minecraft);
                    replacePersonalTasks(loadedTasks);
                    updateCachedPersonalTasksSnapshot(openedStorageNamespace, loadedTasks);
                    TodoConstants.LOGGER.info("Loaded {} tasks from storage", loadedTasks.size());
                } catch (Exception e) {
                    TodoConstants.LOGGER.error("Failed to load tasks from storage", e);
                }
            }
        }

        teamTaskManager = ClientBridge.ops().getTeamTaskManager();
        if (!teamHasUnsavedChanges && teamTaskManager != null) {
            updateCachedTeamTasksSnapshot(teamTaskManager.getAllTasks());
        }
        
        // Initialize project manager state and team project availability.
        projectManager = TodoListCommon.getProjectManager();
        projectManager.addListener(this);
        teamProjectsEnabled = ClientBridge.ops().isTeamProjectsEnabled();
        if (!teamProjectsEnabled) {
            projectScopeFilter = Project.Scope.PERSONAL;
            if (currentProject != null && currentProject.getScope() == Project.Scope.TEAM) {
                currentProject = null;
            }
            viewMode = ViewMode.PERSONAL;
        }

        applyLastGuiState();
        
        // Restore the selected project if it still exists.
        if (currentProject != null) {
            Project p = projectManager.getProject(currentProject.getId());
            if (p == null) {
                currentProject = null; // Project was deleted
            } else {
                currentProject = p; // Update reference to fresh object
            }
        }

        if (currentProject == null) {
            currentProject = TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                    projectManager,
                    projectScopeFilter,
                    teamProjectsEnabled,
                    preferredTeamProjectId,
                    preferredPersonalProjectId
            );
        }
        if (currentProject != null) {
            String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                    currentProject,
                    preferredPersonalProjectId,
                    preferredTeamProjectId
            );
            preferredPersonalProjectId = preferredIds[0];
            preferredTeamProjectId = preferredIds[1];
            projectScopeFilter = currentProject.getScope();
        }
        syncViewStateForCurrentProject();
        
        // Choose the active task manager for the current space.
        if (currentProject != null) {
            if (currentSpaceMode == SpaceMode.PERSONAL) {
                taskManager = personalTaskManager;
            } else {
                taskManager = teamTaskManager;
            }
        } else {
            taskManager = personalTaskManager;
        }

        syncHudViewForProject(currentProject);
        currentProject = TodoScreenActiveProjectSupport.syncActiveProjectIdWithCurrentProject(projectManager, currentProject);
        hasUnsavedChanges = (viewMode == ViewMode.PERSONAL) ? personalHasUnsavedChanges : teamHasUnsavedChanges;

        rebuildUI();
    }

    /**
     * 将同命名空间的个人任务快照快速装入当前界面，避免 H2 后端打开 GUI 时等待数据库读取。
     */
    private void loadCachedPersonalTasksIntoManager() {
        if (openedStorageNamespace.equals(cachedPersonalTasksNamespace)) {
            replacePersonalTasks(copyTasks(cachedPersonalTasksSnapshot));
        }
    }

    /**
     * 后台加载 H2 个人任务，加载完成后回到主线程刷新当前 GUI。
     */
    private void schedulePersonalTasksLoadFromStorage() {
        if (personalTasksLoading) {
            return;
        }
        personalTasksLoading = true;
        String namespace = openedStorageNamespace;
        Minecraft currentMinecraft = this.minecraft;
        CompletableFuture
                .supplyAsync(() -> loadPersonalTasksForGui(currentMinecraft), GUI_STORAGE_LOADER)
                .whenComplete((loadedTasks, failure) -> {
                    if (currentMinecraft == null) {
                        return;
                    }
                    currentMinecraft.execute(() -> applyAsyncPersonalTasks(namespace, loadedTasks, failure));
                });
    }

    /**
     * 在后台线程读取当前个人任务列表。
     *
     * @param currentMinecraft 当前 Minecraft 客户端实例
     * @return 已加载的个人任务
     */
    private List<Task> loadPersonalTasksForGui(Minecraft currentMinecraft) {
        try {
            H2ConnectionProvider.enableEmbeddedConnectionReuseForCurrentThread();
            try {
                return ClientTaskStorageHelper.loadPersonalTasks(TodoListCommon.getTaskStorage(), currentMinecraft);
            } finally {
                H2ConnectionProvider.disableEmbeddedConnectionReuseForCurrentThread();
            }
        } catch (Exception exception) {
            throw new GuiTaskLoadException(exception);
        }
    }

    /**
     * 应用后台加载出的个人任务，并在当前界面仍打开时刷新任务列表。
     *
     * @param namespace 加载任务时的存储命名空间
     * @param loadedTasks 已加载任务
     * @param failure 加载失败异常
     */
    private void applyAsyncPersonalTasks(String namespace, List<Task> loadedTasks, Throwable failure) {
        personalTasksLoading = false;
        if (failure != null) {
            TodoConstants.LOGGER.error("Failed to load tasks from storage", failure);
            return;
        }
        if (!Objects.equals(namespace, openedStorageNamespace) || loadedTasks == null) {
            return;
        }
        updateCachedPersonalTasksSnapshot(namespace, loadedTasks);
        if (this.minecraft == null || this.minecraft.screen != this || personalHasUnsavedChanges) {
            return;
        }
        replacePersonalTasks(loadedTasks);
        if (currentSpaceMode == SpaceMode.PERSONAL) {
            taskManager = personalTaskManager;
            updateProjectList();
            filterTasks();
            updateButtonStates();
            updateProjectActionButtons();
        }
        TodoConstants.LOGGER.info("Loaded {} tasks from storage", loadedTasks.size());
    }

    /**
     * 替换当前个人任务管理器内容。
     *
     * @param tasks 新的个人任务列表
     */
    private void replacePersonalTasks(List<Task> tasks) {
        if (personalTaskManager == null) {
            personalTaskManager = new TaskManager();
        }
        personalTaskManager.clearAll();
        for (Task task : tasks == null ? List.<Task>of() : tasks) {
            personalTaskManager.addTask(task);
        }
    }

    /**
     * 更新 GUI 个人任务快照缓存，供下一次打开界面时快速显示。
     *
     * @param namespace 存储命名空间
     * @param tasks 最新个人任务列表
     */
    static void updateCachedPersonalTasksSnapshot(String namespace, List<Task> tasks) {
        cachedPersonalTasksNamespace = namespace == null ? "" : namespace;
        cachedPersonalTasksSnapshot = copyTasks(tasks);
    }

    /**
     * 更新 GUI 团队任务快照缓存，作为多人编辑合并时的客户端基线。
     *
     * @param tasks 最新团队任务列表
     */
    static void updateCachedTeamTasksSnapshot(List<Task> tasks) {
        cachedTeamTasksSnapshot = copyTasks(tasks);
    }

    /**
     * 深拷贝任务列表，避免缓存和界面编辑对象互相污染。
     *
     * @param tasks 原始任务列表
     * @return 拷贝后的任务列表
     */
    private static List<Task> copyTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Task> copies = new ArrayList<>();
        for (Task task : tasks) {
            if (task != null) {
                copies.add(Task.fromNbt(task.toNbt()));
            }
        }
        return copies;
    }

    /**
     * 判断两个任务快照的持久化内容是否一致。
     *
     * @param left 左侧任务列表
     * @param right 右侧任务列表
     * @return 内容一致时返回 true
     */
    private static boolean taskSnapshotsEquivalent(List<Task> left, List<Task> right) {
        List<Task> leftTasks = left == null ? List.of() : left;
        List<Task> rightTasks = right == null ? List.of() : right;
        if (leftTasks.size() != rightTasks.size()) {
            return false;
        }
        for (int index = 0; index < leftTasks.size(); index++) {
            Task leftTask = leftTasks.get(index);
            Task rightTask = rightTasks.get(index);
            if (leftTask == rightTask) {
                continue;
            }
            if (leftTask == null || rightTask == null || !Objects.equals(leftTask.toNbt(), rightTask.toNbt())) {
                return false;
            }
        }
        return true;
    }

    /**
     * GUI 后台任务加载异常包装，避免 CompletableFuture 丢失真实原因。
     */
    private static final class GuiTaskLoadException extends RuntimeException {
        /**
         * 创建 GUI 任务加载异常。
         *
         * @param cause 原始失败原因
         */
        private GuiTaskLoadException(Throwable cause) {
            super(cause);
        }
    }

    @Override
    public void removed() {
        super.removed();
        saveLastGuiState();
        if (projectManager != null) {
            projectManager.removeListener(this);
        }
    }

    /**
     * 应用服务端下发的个人任务同步结果，并刷新已打开的 GUI 与 HUD。
     *
     * @param minecraft 当前客户端实例
     * @param tasks 服务端下发的个人任务列表
     */
    public static void applySyncedPersonalTasks(Minecraft minecraft, List<Task> tasks) {
        List<Task> safeTasks = copyTasks(tasks);
        updateCachedPersonalTasksSnapshot(DataPathProvider.getStorageNamespace(), safeTasks);
        TodoHudRenderer renderer = ClientPlatformAdapter.getHudRenderer();
        if (renderer != null) {
            renderer.syncPersonalTasksFromGui(safeTasks);
        }
        if (minecraft != null && minecraft.screen instanceof TodoScreen screen) {
            screen.applySyncedPersonalTasksToOpenScreen(safeTasks);
        }
    }

    /**
     * 应用服务端下发的团队任务同步结果，并刷新已打开的 GUI 与 HUD。
     *
     * @param minecraft 当前客户端实例
     */
    public static void applySyncedTeamTasks(Minecraft minecraft) {
        if (ClientBridge.ops() != null && !teamHasUnsavedChanges) {
            TaskManager manager = ClientBridge.ops().getTeamTaskManager();
            updateCachedTeamTasksSnapshot(manager == null ? List.of() : manager.getAllTasks());
        }
        refreshAppliedTeamTasks(minecraft);
    }

    /**
     * 应用服务端下发的团队任务快照，并避免覆盖当前 GUI 中尚未落盘的团队改动。
     *
     * @param minecraft 当前客户端实例
     * @param tasks 服务端下发的团队任务列表
     */
    public static void applySyncedTeamTasks(Minecraft minecraft, List<Task> tasks) {
        List<Task> safeTasks = copyTasks(tasks);
        if (shouldIgnoreStaleTeamSyncForPendingSave(safeTasks)) {
            return;
        }
        if (minecraft != null
                && minecraft.screen instanceof TodoScreen screen
                && (teamHasUnsavedChanges || screen.teamTaskSaveInFlight)) {
            deferredTeamTasksSnapshot = safeTasks;
            deferredTeamTasksNamespace = DataPathProvider.getStorageNamespace();
            return;
        }
        applyTeamTasksSnapshot(minecraft, safeTasks);
    }

    /**
     * 将团队任务快照写入桥接任务管理器，并刷新 GUI、HUD 与合并基线。
     *
     * @param minecraft 当前客户端实例
     * @param tasks 服务端下发的团队任务快照
     */
    private static void applyTeamTasksSnapshot(Minecraft minecraft, List<Task> tasks) {
        TaskManager manager = ClientBridge.ops() == null ? null : ClientBridge.ops().getTeamTaskManager();
        if (manager != null) {
            manager.clearAll();
            for (Task task : copyTasks(tasks)) {
                manager.addTask(task);
            }
        }
        clearDeferredTeamTasksSnapshot();
        updateCachedTeamTasksSnapshot(tasks);
        refreshAppliedTeamTasks(minecraft);
    }

    /**
     * 清空暂存的团队任务同步快照，避免关闭界面或切换数据目录后误应用旧数据。
     */
    private static void clearDeferredTeamTasksSnapshot() {
        deferredTeamTasksSnapshot = null;
        deferredTeamTasksNamespace = "";
    }

    /**
     * 记录刚提交给服务端的团队任务保存快照，用于过滤乱序晚到的旧同步。
     *
     * @param baseTasks 保存发起时的服务端同步基线
     * @param submittedTasks 本次提交给服务端的团队任务快照
     */
    private static void rememberPendingTeamAuthoritativeSync(List<Task> baseTasks, List<Task> submittedTasks) {
        pendingTeamAuthoritativeBaseSnapshot = copyTasks(baseTasks);
        pendingTeamAuthoritativeSubmittedSnapshot = copyTasks(submittedTasks);
        pendingTeamAuthoritativeNamespace = DataPathProvider.getStorageNamespace();
    }

    /**
     * 清空等待服务端确认的团队任务提交快照。
     */
    private static void clearPendingTeamAuthoritativeSync() {
        pendingTeamAuthoritativeBaseSnapshot = null;
        pendingTeamAuthoritativeSubmittedSnapshot = null;
        pendingTeamAuthoritativeNamespace = "";
    }

    /**
     * 判断团队同步是否是旧提交的晚到回包，避免覆盖更新的本地提交造成闪回。
     *
     * @param syncedTasks 服务端下发的团队任务快照
     * @return true 表示应忽略该同步
     */
    private static boolean shouldIgnoreStaleTeamSyncForPendingSave(List<Task> syncedTasks) {
        if (pendingTeamAuthoritativeSubmittedSnapshot == null) {
            return false;
        }
        if (!Objects.equals(pendingTeamAuthoritativeNamespace, DataPathProvider.getStorageNamespace())) {
            clearPendingTeamAuthoritativeSync();
            return false;
        }
        if (teamSyncCoversPendingSave(syncedTasks)) {
            clearPendingTeamAuthoritativeSync();
            return false;
        }
        return true;
    }

    /**
     * 判断服务端同步是否已经包含最近一次本地团队提交中的增删改结果。
     *
     * @param syncedTasks 服务端下发的团队任务快照
     * @return true 表示同步已覆盖本地提交
     */
    private static boolean teamSyncCoversPendingSave(List<Task> syncedTasks) {
        Map<String, Task> baseById = mapTasksById(pendingTeamAuthoritativeBaseSnapshot);
        Map<String, Task> submittedById = mapTasksById(pendingTeamAuthoritativeSubmittedSnapshot);
        Map<String, Task> syncedById = mapTasksById(syncedTasks);
        for (Task submittedTask : pendingTeamAuthoritativeSubmittedSnapshot) {
            Task baseTask = baseById.get(submittedTask.getId());
            if ((baseTask == null || !tasksEquivalent(baseTask, submittedTask))
                    && !tasksEquivalent(submittedTask, syncedById.get(submittedTask.getId()))) {
                return false;
            }
        }
        for (Task baseTask : pendingTeamAuthoritativeBaseSnapshot) {
            if (!submittedById.containsKey(baseTask.getId())
                    && tasksEquivalent(baseTask, syncedById.get(baseTask.getId()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按任务 ID 构建任务映射，并忽略空任务和空 ID。
     *
     * @param tasks 原始任务列表
     * @return 任务 ID 到任务对象的映射
     */
    private static Map<String, Task> mapTasksById(List<Task> tasks) {
        Map<String, Task> tasksById = new HashMap<>();
        for (Task task : tasks == null ? List.<Task>of() : tasks) {
            if (task != null && task.getId() != null) {
                tasksById.put(task.getId(), task);
            }
        }
        return tasksById;
    }

    /**
     * 判断两个任务的持久化内容是否一致。
     *
     * @param left 左侧任务
     * @param right 右侧任务
     * @return 内容一致时返回 true
     */
    private static boolean tasksEquivalent(Task left, Task right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.toNbt(), right.toNbt());
    }

    /**
     * 刷新已应用团队任务后的 HUD 与当前打开界面。
     *
     * @param minecraft 当前客户端实例
     */
    private static void refreshAppliedTeamTasks(Minecraft minecraft) {
        TodoHudRenderer renderer = ClientPlatformAdapter.getHudRenderer();
        if (renderer != null) {
            renderer.forceRefreshTasks();
        }
        if (minecraft != null && minecraft.screen instanceof TodoScreen screen) {
            screen.refreshAfterExternalTaskSync(Project.Scope.TEAM);
        }
    }

    /**
     * 将个人任务同步结果应用到当前打开的待办界面。
     *
     * @param tasks 服务端下发的个人任务列表
     */
    private void applySyncedPersonalTasksToOpenScreen(List<Task> tasks) {
        if (personalHasUnsavedChanges) {
            return;
        }
        replacePersonalTasks(tasks);
        personalHasUnsavedChanges = false;
        refreshAfterExternalTaskSync(Project.Scope.PERSONAL);
    }

    /**
     * 外部任务同步后刷新当前任务列表、项目计数和按钮状态。
     *
     * @param changedScope 发生变化的任务范围
     */
    private void refreshAfterExternalTaskSync(Project.Scope changedScope) {
        if (changedScope == Project.Scope.TEAM && teamHasUnsavedChanges) {
            return;
        }
        if (changedScope == Project.Scope.PERSONAL && currentSpaceMode == SpaceMode.PERSONAL) {
            taskManager = personalTaskManager;
        } else if (changedScope == Project.Scope.TEAM && currentSpaceMode == SpaceMode.TEAM) {
            taskManager = teamTaskManager;
        }
        if (selectedTask != null && !isSelectedTaskValid()) {
            clearSelectedTask();
        }
        hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
        updateProjectList();
        filterTasks();
        updateButtonStates();
        updateProjectActionButtons();
    }

    /**
     * 根据当前项目重新同步空间模式、任务视图选项和兼容状态。
     */
    private void syncViewStateForCurrentProject() {
        currentSpaceMode = SpaceMode.valueOf(TodoScreenViewModeSupport.resolveSpaceModeName(currentProject, teamProjectsEnabled));
        List<String> visibleOptions = TodoScreenViewModeSupport.getVisibleTaskViewOptionNames(currentSpaceMode.name());
        TaskViewOption resolvedOption = currentSpaceMode == SpaceMode.TEAM && viewMode == ViewMode.PERSONAL
                ? TaskViewOption.valueOf(TodoScreenViewModeSupport.resolveDefaultTaskViewOptionName(currentSpaceMode.name()))
                : TaskViewOption.valueOf(TodoScreenViewModeSupport.resolveTaskViewOptionNameFromLegacyViewModeName(viewMode.name()));
        if (!visibleOptions.contains(resolvedOption.name())) {
            resolvedOption = TaskViewOption.valueOf(TodoScreenViewModeSupport.resolveDefaultTaskViewOptionName(currentSpaceMode.name()));
        }
        currentTaskViewOption = resolvedOption;
        viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                currentSpaceMode.name(),
                currentTaskViewOption.name()
        ));
    }

    /**
     * 根据分组标识切换对应分组的展开状态。
     *
     * @param sectionId 分组标识
     * @return 成功切换时返回 {@code true}
     */
    private boolean toggleTaskSection(String sectionId) {
        if ("active".equals(sectionId)) {
            activeExpanded = !activeExpanded;
            return true;
        }
        if ("completed".equals(sectionId)) {
            completedExpanded = !completedExpanded;
            return true;
        }
        return false;
    }

    private void applyLastGuiState() {
        if (lastGuiState == null || projectManager == null) return;

        projectScopeFilter = lastGuiState.projectScopeFilter == null ? Project.Scope.PERSONAL : lastGuiState.projectScopeFilter;
        if (!teamProjectsEnabled) {
            projectScopeFilter = Project.Scope.PERSONAL;
        }

        if (lastGuiState.projectSearchQuery != null) {
            projectSearchQuery = lastGuiState.projectSearchQuery;
        }
        preferredPersonalProjectId = lastGuiState.lastPersonalProjectId;
        preferredTeamProjectId = lastGuiState.lastTeamProjectId;
        activeExpanded = lastGuiState.activeExpanded;
        completedExpanded = lastGuiState.completedExpanded;

        if (lastGuiState.viewMode != null) {
            viewMode = lastGuiState.viewMode;
        }
        if (lastGuiState.spaceMode != null) {
            currentSpaceMode = lastGuiState.spaceMode;
        }
        if (lastGuiState.taskViewOption != null) {
            currentTaskViewOption = lastGuiState.taskViewOption;
        }
        if (!teamProjectsEnabled && viewMode != ViewMode.PERSONAL) {
            viewMode = ViewMode.PERSONAL;
        }

        currentPriorityFilter = lastGuiState.currentPriorityFilter;
        if (lastGuiState.searchQuery != null) {
            searchQuery = lastGuiState.searchQuery;
        }

        if (lastGuiState.currentProjectId != null && !lastGuiState.currentProjectId.isEmpty()) {
            Project p = projectManager.getProject(lastGuiState.currentProjectId);
            if (p != null && (teamProjectsEnabled || p.getScope() == Project.Scope.PERSONAL)) {
                currentProject = p;
                projectScopeFilter = p.getScope();
                String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                        p,
                        preferredPersonalProjectId,
                        preferredTeamProjectId
                );
                preferredPersonalProjectId = preferredIds[0];
                preferredTeamProjectId = preferredIds[1];
            }
        }
    }

    private void saveLastGuiState() {
        String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                currentProject,
                preferredPersonalProjectId,
                preferredTeamProjectId
        );
        preferredPersonalProjectId = preferredIds[0];
        preferredTeamProjectId = preferredIds[1];
        LastGuiState s = new LastGuiState();
        s.projectScopeFilter = projectScopeFilter;
        s.currentProjectId = currentProject == null ? null : currentProject.getId();
        s.viewMode = viewMode;
        s.spaceMode = currentSpaceMode;
        s.taskViewOption = currentTaskViewOption;
        s.activeExpanded = activeExpanded;
        s.completedExpanded = completedExpanded;
        s.currentPriorityFilter = currentPriorityFilter;
        s.searchQuery = searchQuery;
        s.projectSearchQuery = projectSearchQuery;
        s.lastPersonalProjectId = preferredPersonalProjectId;
        s.lastTeamProjectId = preferredTeamProjectId;
        lastGuiState = s;
    }

    @Override
    public void onProjectChanged(ProjectManager.ProjectChangeType type, Project project) {
        if (this.minecraft == null) return;
        this.minecraft.execute(() -> {
            if (type == ProjectManager.ProjectChangeType.CLEARED) {
                switchProject(null);
                return;
            }

            if (project == null) return;

            if (type == ProjectManager.ProjectChangeType.REMOVED) {
                if (!TodoListCommon.isProjectSyncInProgress()) {
                    TodoScreenProjectCleanupSupport.hardDeleteTasksForDeletedProject(
                            personalTaskManager,
                            teamTaskManager,
                            project
                    );
                }
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    switchProject(TodoScreenProjectPreferenceSupport.resolveFallbackProjectAfterRemoval(
                            projectManager,
                            project,
                            projectScopeFilter,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId
                    ));
                } else {
                    refreshAfterProjectMutation();
                }
            } else if (type == ProjectManager.ProjectChangeType.ADDED) {
                if (currentProject == null) {
                    switchProject(TodoScreenProjectPreferenceSupport.resolvePreferredProjectForCurrentScope(
                            projectManager,
                            projectScopeFilter,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId
                    ));
                } else {
                    refreshAfterProjectMutation();
                }
            } else if (type == ProjectManager.ProjectChangeType.UPDATED) {
                if (currentProject != null && currentProject.getId().equals(project.getId())) {
                    currentProject = project;
                }
                refreshAfterProjectMutation();
            }
        });
    }

    /**
     * 在项目增删改之后刷新当前项目、侧栏和任务列表。
     */
    private void refreshAfterProjectMutation() {
        currentProject = TodoScreenActiveProjectSupport.syncActiveProjectIdWithCurrentProject(projectManager, currentProject);
        updateProjectList();
        applySearchFilter();
        updateButtonStates();
        updateProjectActionButtons();
    }

    private void rebuildUI() {
        syncViewStateForCurrentProject();
        if (searchQuery == null) {
            searchQuery = "";
        }
        if (projectSearchQuery == null) {
            projectSearchQuery = "";
        }
        baseFilteredTasks = new ArrayList<>();
        filteredTasks = new ArrayList<>();
        if (taskListWidget != null) {
            savedTaskListScrollOffset = taskListWidget.getScrollOffset();
            savedExpandedParentTaskIds = taskListWidget.getExpandedParentTaskIds();
        }
        this.clearWidgets();

        ModConfig config = ModConfig.getInstance();
        responsiveTier = TodoScreenLayoutSupport.resolveResponsiveTier(this.width, this.height);
        syncOverlayStateForResponsiveTier();

        layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                config,
                responsiveTier,
                this.width,
                this.height,
                sidebarOverlayVisible,
                detailOverlayVisible,
                selectedTask != null
        );
        LayoutRect sidebarBounds = layoutMetrics.sidebarBounds;
        LayoutRect contentBounds = layoutMetrics.contentBounds;
        LayoutRect detailBounds = layoutMetrics.detailBounds;
        int padding = layoutMetrics.padding;
        int panelGap = layoutMetrics.gap;
        int contentControlInset = TodoScreenUiMetricsSupport.getContentControlInset(responsiveTier);
        int contentControlX = contentBounds.x + contentControlInset;
        int contentControlWidth = Math.max(80, contentBounds.width - contentControlInset * 2);
        int contentControlRight = contentControlX + contentControlWidth;
        int topBarGap = Math.max(3, Math.min(config.getElementSpacing(), 8));
        int topBarHeight = TodoScreenUiMetricsSupport.getContentTopBarHeight(responsiveTier);
        int topBarY = contentBounds.y + TodoScreenUiMetricsSupport.getContentTopPadding(responsiveTier);
        int secondRowHeight = TodoScreenUiMetricsSupport.getContentSearchFieldHeight(responsiveTier);
        int secondRowY = topBarY + topBarHeight + TodoScreenUiMetricsSupport.getContentHeaderGap(responsiveTier);
        int inputRowHeight = TodoScreenUiMetricsSupport.getContentQuickAddFieldHeight(responsiveTier);
        int inputRowY = contentBounds.y + contentBounds.height - inputRowHeight - TodoScreenUiMetricsSupport.getContentBottomPadding(responsiveTier);
        int bottomActionRowHeight = TodoScreenUiMetricsSupport.getContentBottomActionRowHeight(responsiveTier);
        int actionRowY = this.height - padding - bottomActionRowHeight;
        int listTop = secondRowY + secondRowHeight + topBarGap;
        int listBottom = inputRowY - Math.max(4, topBarGap);
        int listHeight = Math.max(0, listBottom - listTop);

        int sidebarTopY = sidebarBounds.y;
        int sidebarWidth = sidebarBounds.width;
        int contentX = contentBounds.x;
        int contentWidth = contentBounds.width;
        int rightPanelX = detailBounds.x;
        int rightPanelWidth = detailBounds.width;

        int actionGap = TodoScreenUiMetricsSupport.getContentActionGap(responsiveTier);
        int cancelButtonWidth = Math.max(44, Math.min(64, this.font.width(Component.translatable("gui.todolist.cancel")) + 10));
        int saveButtonWidth = Math.max(44, Math.min(64, this.font.width(Component.translatable("gui.todolist.save")) + 10));
        int bottomActionWidth = saveButtonWidth + actionGap + cancelButtonWidth;
        int saveButtonX = Math.max(0, (this.width - bottomActionWidth) / 2);
        int cancelButtonX = saveButtonX + saveButtonWidth + actionGap;

        TodoScreenWidgetBuildSupport.ContentWidgets contentWidgets = TodoScreenWidgetBuildSupport.buildContentWidgets(
                this.font,
                layoutMetrics,
                responsiveTier,
                contentControlX,
                contentControlRight,
                topBarY,
                topBarHeight,
                secondRowY,
                secondRowHeight,
                currentPriorityFilter,
                searchQuery,
                button -> {
                    currentPriorityFilter = (currentPriorityFilter + 1) % 4;
                    button.setMessage(TodoScreenFilterTextSupport.getPriorityFilterText(currentPriorityFilter));
                    filterTasks();
                },
                this::toggleSidebarOverlay,
                () -> this.minecraft.setScreen(new ConfigScreen(this))
        );
        sidebarToggleButton = contentWidgets.sidebarToggleButton;
        configButton = contentWidgets.configButton;
        filterPriorityButton = contentWidgets.filterPriorityButton;
        searchField = contentWidgets.searchField;
        filterStatusButton = null;
        viewToggleButton = null;
        this.addRenderableWidget(sidebarToggleButton);
        this.addRenderableWidget(configButton);
        this.addRenderableWidget(filterPriorityButton);
        this.addRenderableWidget(searchField);

        TodoScreenWidgetBuildSupport.SidebarWidgets sidebarWidgets = TodoScreenWidgetBuildSupport.buildSidebarWidgets(
                this.minecraft,
                this.font,
                responsiveTier,
                sidebarBounds,
                sidebarTopY,
                sidebarWidth,
                projectManager,
                teamProjectsEnabled,
                preferredTeamProjectId,
                preferredPersonalProjectId,
                () -> {
                    Project targetProject = TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                            projectManager,
                            Project.Scope.PERSONAL,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId
                    );
                    switchProject(targetProject);
                },
                () -> {
                    if (!teamProjectsEnabled) {
                        return;
                    }
                    Project targetProject = TodoScreenProjectPreferenceSupport.getPreferredProjectForScope(
                            projectManager,
                            Project.Scope.TEAM,
                            teamProjectsEnabled,
                            preferredTeamProjectId,
                            preferredPersonalProjectId
                    );
                    switchProject(targetProject);
                },
                () -> {
                    if (currentSpaceMode == SpaceMode.PERSONAL) {
                        switchView(ViewMode.PERSONAL);
                        return;
                    }
                    switchView(ViewMode.TEAM_ASSIGNED);
                },
                () -> switchView(ViewMode.TEAM_UNASSIGNED),
                () -> switchView(ViewMode.TEAM_ALL),
                projectSearchQuery,
                text -> {
                    projectSearchQuery = text;
                    updateProjectList();
                },
                this::switchProject,
                this::onAddProject,
                this::onProjectSettings,
                this::onProjectDelete,
                this::onApplyJoinProject,
                layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width,
                currentProject != null
        );
        personalSpaceButton = sidebarWidgets.personalSpaceButton;
        teamSpaceButton = sidebarWidgets.teamSpaceButton;
        myViewButton = sidebarWidgets.myViewButton;
        unassignedViewButton = sidebarWidgets.unassignedViewButton;
        allViewButton = sidebarWidgets.allViewButton;
        projectSearchField = sidebarWidgets.projectSearchField;
        projectListWidget = sidebarWidgets.projectListWidget;
        addProjectBtn = sidebarWidgets.addProjectBtn;
        editProjectBtn = sidebarWidgets.editProjectBtn;
        deleteProjectBtn = sidebarWidgets.deleteProjectBtn;
        applyJoinProjectBtn = sidebarWidgets.applyJoinProjectBtn;
        this.addRenderableWidget(personalSpaceButton);
        this.addRenderableWidget(teamSpaceButton);
        this.addRenderableWidget(myViewButton);
        this.addRenderableWidget(unassignedViewButton);
        this.addRenderableWidget(allViewButton);
        this.addRenderableWidget(projectSearchField);
        this.addRenderableWidget(addProjectBtn);
        this.addRenderableWidget(editProjectBtn);
        this.addRenderableWidget(deleteProjectBtn);
        this.addRenderableWidget(applyJoinProjectBtn);
        updateProjectList();
        projectListWidget.setScrollOffset(savedProjectListScrollOffset);
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();

        boolean teamAllView = viewMode == ViewMode.TEAM_ALL;
        TodoScreenWidgetBuildSupport.TaskAreaWidgets taskAreaWidgets = TodoScreenWidgetBuildSupport.buildTaskAndQuickAddWidgets(
                this.minecraft,
                this.font,
                contentX,
                listTop,
                contentWidth,
                listHeight,
                TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject) == Role.MEMBER && teamAllView,
                TodoScreenPermissionSupport.canTaskReorderInView(
                        this.minecraft,
                        currentProject,
                        viewMode.name()
                ),
                buildTaskPaneSections(),
                selectedTask,
                task -> {
                    boolean wasCompleted = task.isCompleted();
                    String toggledTaskId = task.getId();
                    String toggledTaskTitle = task.getTitle();
                    toggleTaskCompletion(task);
                    Task updatedTask = toggledTaskId == null || taskManager == null ? task : taskManager.getTask(toggledTaskId);
                    boolean completedAfterToggle = updatedTask != null && updatedTask.isCompleted();
                    String completedTaskTitle = updatedTask != null && updatedTask.getTitle() != null
                            ? updatedTask.getTitle()
                            : toggledTaskTitle;
                    if (!wasCompleted && completedAfterToggle) {
                        addNotification(Component.translatable("message.todolist.completed", completedTaskTitle).getString());
                        if (config.isEnableSoundEffects() && this.minecraft != null && this.minecraft.player != null) {
                            this.minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 1.0F);
                        }
                    }
                    applySearchFilter();
                },
                this::onManualReorderActiveTasks,
                contentControlX,
                inputRowY,
                Math.max(60, contentControlWidth),
                inputRowHeight
        );
        taskListWidget = taskAreaWidgets.taskListWidget;
        quickAddField = taskAreaWidgets.quickAddField;
        this.addRenderableWidget(quickAddField);
        Component clearCompletedText = Component.translatable("gui.todolist.completed.clear");
        int clearCompletedButtonWidth = Math.max(52, this.font.width(clearCompletedText) + 12);
        clearCompletedButton = Button.builder(clearCompletedText, button -> openClearCompletedConfirmScreen())
                .bounds(contentX, listTop, clearCompletedButtonWidth, 16)
                .build();
        clearCompletedButton.visible = false;
        clearCompletedButton.active = false;
        this.addRenderableWidget(clearCompletedButton);

        TodoScreenWidgetBuildSupport.DetailWidgets detailWidgets = TodoScreenWidgetBuildSupport.buildDetailWidgets(
                this.font,
                detailBounds,
                rightPanelX,
                rightPanelWidth,
                viewMode != ViewMode.PERSONAL,
                shouldShowAddSubtaskButton(),
                hasSelectedTaskParentContext(),
                this::onDetailClose,
                this::onAddSubtask,
                this::onClaimTask,
                this::onAbandonTask,
                this::onAssignOthers
        );
        detailCloseButton = detailWidgets.detailCloseButton;
        titleField = detailWidgets.titleField;
        descField = detailWidgets.descField;
        tagField = detailWidgets.tagField;
        addSubtaskButton = detailWidgets.addSubtaskButton;
        claimButton = detailWidgets.claimButton;
        abandonButton = detailWidgets.abandonButton;
        assignOthersButton = detailWidgets.assignOthersButton;
        this.addRenderableWidget(detailCloseButton);
        this.addRenderableWidget(titleField);
        this.addRenderableWidget(descField);
        this.addRenderableWidget(tagField);
        this.addRenderableWidget(addSubtaskButton);
        this.addRenderableWidget(claimButton);
        this.addRenderableWidget(abandonButton);
        this.addRenderableWidget(assignOthersButton);

        TodoScreenWidgetBuildSupport.BottomActionWidgets bottomActionWidgets = TodoScreenWidgetBuildSupport.buildBottomActionWidgets(
                saveButtonX,
                saveButtonWidth,
                cancelButtonX,
                cancelButtonWidth,
                actionRowY,
                bottomActionRowHeight,
                this::onSaveTasks,
                this::onClose
        );
        saveButton = bottomActionWidgets.saveButton;
        cancelButton = bottomActionWidgets.cancelButton;
        this.addRenderableWidget(saveButton);
        this.addRenderableWidget(cancelButton);

        applyResponsiveWidgetVisibility();
        bindWidgetResponders();
        filterTasks();
        if (taskListWidget != null) {
            taskListWidget.setExpandedParentTaskIds(savedExpandedParentTaskIds);
            taskListWidget.setScrollOffset(savedTaskListScrollOffset);
        }
        syncDetailWidgetsFromState();
        this.setFocused(quickAddField);
        updateButtonStates();
    }

    /**
     * Bind detail and search field responders.
     */
    private void bindWidgetResponders() {
        titleField.setResponder(this::onDetailTitleChanged);
        descField.setValueListener(this::onDetailDescriptionChanged);
        tagField.setResponder(this::onDetailTagsChanged);
        searchField.setResponder(text -> {
            searchQuery = text == null ? "" : text.trim().toLowerCase();
            applySearchFilter();
        });
    }
    private void syncOverlayStateForResponsiveTier() {
        if (responsiveTier != ResponsiveTier.MINIMAL) {
            sidebarOverlayVisible = false;
        }
        if (responsiveTier == ResponsiveTier.LARGE || responsiveTier == ResponsiveTier.MEDIUM) {
            detailOverlayVisible = false;
        } else if (selectedTask == null) {
            detailOverlayVisible = false;
        } else {
            detailOverlayVisible = true;
        }
    }

    /**
     * 根据当前响应式布局更新各组件的显隐状态。
     */
    private void applyResponsiveWidgetVisibility() {
        if (layoutMetrics == null) {
            return;
        }
        TodoScreenSidebarViewButtonLayoutSupport.apply(
                myViewButton,
                unassignedViewButton,
                allViewButton,
                layoutMetrics.sidebarBounds,
                responsiveTier,
                currentSpaceMode.name()
        );
        boolean sidebarVisible = layoutMetrics.sidebarVisible;
        boolean detailVisible = layoutMetrics.detailVisible;

        boolean teamSpaceVisible = sidebarVisible && currentSpaceMode == SpaceMode.TEAM;
        if (personalSpaceButton != null) {
            personalSpaceButton.visible = sidebarVisible;
            personalSpaceButton.active = sidebarVisible && currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.visible = sidebarVisible;
            teamSpaceButton.active = sidebarVisible && teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (myViewButton != null) {
            myViewButton.visible = sidebarVisible;
            myViewButton.setMessage(Component.literal("\u6211\u7684"));
            myViewButton.active = sidebarVisible && currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.MY;
        }
        if (unassignedViewButton != null) {
            unassignedViewButton.visible = teamSpaceVisible;
            int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
            unassignedViewButton.setMessage(TodoScreenUiMetricsSupport.getUnassignedTeamViewText(sidebarWidth));
            unassignedViewButton.active = teamSpaceVisible && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.visible = teamSpaceVisible;
            allViewButton.setMessage(TodoScreenUiMetricsSupport.getAllTeamViewText());
            allViewButton.active = teamSpaceVisible && currentTaskViewOption != TaskViewOption.ALL;
        }
        if (projectSearchField != null) {
            projectSearchField.visible = sidebarVisible;
            projectSearchField.active = sidebarVisible;
        }
        if (addProjectBtn != null) {
            addProjectBtn.visible = sidebarVisible;
            addProjectBtn.active = sidebarVisible;
        }
        if (editProjectBtn != null) {
            editProjectBtn.visible = sidebarVisible;
            if (!sidebarVisible) {
                editProjectBtn.active = false;
            }
        }
        if (deleteProjectBtn != null) {
            if (!sidebarVisible) {
                deleteProjectBtn.visible = false;
                deleteProjectBtn.active = false;
            }
        }
        if (applyJoinProjectBtn != null) {
            if (!sidebarVisible) {
                applyJoinProjectBtn.visible = false;
                applyJoinProjectBtn.active = false;
            }
        }

        if (quickAddField != null) {
            quickAddField.visible = true;
            quickAddField.active = true;
        }
        applyDetailWidgetEditability();
        if (claimButton != null) {
            claimButton.visible = detailVisible && claimButton.visible;
            claimButton.active = detailVisible && claimButton.active;
        }
        if (abandonButton != null) {
            abandonButton.visible = detailVisible && abandonButton.visible;
            abandonButton.active = detailVisible && abandonButton.active;
        }
        if (assignOthersButton != null) {
            assignOthersButton.visible = detailVisible && assignOthersButton.visible;
            assignOthersButton.active = detailVisible && assignOthersButton.active;
        }
        if (addSubtaskButton != null) {
            addSubtaskButton.visible = detailVisible && addSubtaskButton.visible;
            addSubtaskButton.active = detailVisible && addSubtaskButton.active;
        }
        if (sidebarToggleButton != null) {
            sidebarToggleButton.visible = layoutMetrics.sidebarOverlay;
            sidebarToggleButton.active = layoutMetrics.sidebarOverlay;
        }
        projectSearchPrefixDropdownOpen = TodoScreenProjectSearchSupport.syncProjectSearchPrefixDropdownState(
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible(),
                projectSearchField
        );
    }

    /**
     * 切换侧栏覆盖层的显示状态。
     */
    private void toggleSidebarOverlay() {
        if (responsiveTier != ResponsiveTier.MINIMAL) {
            return;
        }
        sidebarOverlayVisible = !sidebarOverlayVisible;
        layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                ModConfig.getInstance(),
                responsiveTier,
                this.width,
                this.height,
                sidebarOverlayVisible,
                detailOverlayVisible,
                selectedTask != null
        );
        updateProjectActionButtons();
        updateButtonStates();
        applyResponsiveWidgetVisibility();
    }

    /**
     * 判断侧栏面板当前是否应该显示。
     *
     * @return 侧栏应显示时返回 {@code true}
     */
    private boolean isSidebarPanelVisible() {
        return layoutMetrics != null && layoutMetrics.sidebarVisible;
    }

    /**
     * 判断详情面板当前是否应该显示。
     *
     * @return 详情面板应显示时返回 {@code true}
     */
    private boolean isDetailPanelVisible() {
        return layoutMetrics != null && layoutMetrics.detailVisible;
    }

    /**
     * 切换任务完成状态，并立即重建当前视图的未完成/已完成分组数据。
     *
     * @param task 目标任务
     */
    private void toggleTaskCompletion(Task task) {
        List<Task> allTasks = taskManager == null ? List.of() : taskManager.getAllTasks();
        if (!TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.TOGGLE_COMPLETE,
                task,
                allTasks,
                this.minecraft,
                currentProject,
                viewMode.name()
        )) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        String currentPlayerUuid = TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft);
        if ("TEAM_ASSIGNED".equals(viewMode.name())
                && TaskAssignmentSupport.hasDirectSubtasks(task, allTasks)
                && currentPlayerUuid != null
                && !currentPlayerUuid.isEmpty()) {
            toggleDirectSubtasksForPlayer(task, allTasks, currentPlayerUuid);
        } else {
            taskManager.toggleTaskCompletion(task.getId());
        }
        markUnsaved();
        String operationName = task.isCompleted() ? "complete" : "uncomplete";
        filterTasks();
        persistCurrentViewTasksInBackground(operationName);
    }

    /**
     * 在"我的"视图下切换父任务中属于当前玩家的直属子任务的完成状态。
     * 仅影响分配给当前玩家的子任务，不影响其他玩家的子任务。
     *
     * @param parentTask 父任务
     * @param allTasks 同作用域的任务列表
     * @param playerUuid 当前玩家 UUID
     */
    private void toggleDirectSubtasksForPlayer(Task parentTask, List<Task> allTasks, String playerUuid) {
        List<Task> playerSubtasks = getDirectSubtasksForBatchAction(parentTask, allTasks);
        boolean anyIncomplete = playerSubtasks.stream()
                .anyMatch(t -> t != null
                        && playerUuid.equals(t.getAssigneeUuid())
                        && !t.isCompleted());
        for (Task subtask : playerSubtasks) {
            if (subtask == null || !playerUuid.equals(subtask.getAssigneeUuid())) {
                continue;
            }
            if (subtask.isCompleted() == anyIncomplete) {
                continue;
            }
            subtask.setCompleted(anyIncomplete);
        }
        taskManager.markParentCompletionDirty();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        refreshClearCompletedButtonState();
        context.fill(0, 0, this.width, this.height, ModConfig.getInstance().getBackgroundColor());

        Component title = hasUnsavedChanges ? Component.translatable("gui.todolist.title.unsaved") : TITLE;
        context.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 10, 0xFFFFFFFF, false);

        TodoScreenRenderSupport.renderLayoutPanels(
                context,
                layoutMetrics,
                isSidebarPanelVisible(),
                isDetailPanelVisible()
        );
        TodoScreenRenderSupport.renderContentHeaderSummary(
                context,
                this.font,
                layoutMetrics,
                responsiveTier,
                currentSpaceMode,
                currentTaskViewOption,
                currentProject,
                configButton,
                sidebarToggleButton
        );
        if (taskListWidget != null) taskListWidget.render(context, mouseX, mouseY, delta);
        if (projectListWidget != null && isSidebarPanelVisible()) {
            projectListWidget.render(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);

        int color = 0xFFFFFFFF;
        int textH = this.font.lineHeight;
        Component parentContext = resolveSelectedTaskParentContextComponent();

        if (descField != null && descField.visible) {
            int dy = descField.getY() - textH - 2;
            if (parentContext != null) {
                int parentY = dy - textH - 4;
                context.drawString(this.font, parentContext, descField.getX(), parentY, color, false);
            }
            context.drawString(this.font, Component.translatable("gui.todolist.label.description"), descField.getX(), dy, color, false);
        }
        if (tagField != null && tagField.visible) {
            int zy = tagField.getY() - textH - 2;
            context.drawString(this.font, Component.translatable("gui.todolist.label.tags"), tagField.getX(), zy, color, false);
        }
        /*
        if (searchField != null) {
            int sy = searchField.getY() + (searchField.getHeight() - textH) / 2;
            int searchLabelX = searchField.getX() - 40;
            context.drawString(this.font, Component.translatable("gui.todolist.label.search"), searchLabelX, sy, color, false);
        }
        */
        TodoScreenRenderSupport.renderProjectSearchPrefixDropdown(
                context,
                mouseX,
                mouseY,
                this.font,
                projectSearchField,
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible()
        );
        renderTaskContextMenu(context, mouseX, mouseY);
        TodoScreenRenderSupport.renderNotifications(
                context,
                this.font,
                this.width,
                layoutMetrics,
                notifications
        );
    }

    /**
     * Save personal and team task data from the current screen.
     */
    private void onSaveTasks() {
        discardSelectedEmptyPlaceholderSubtask();
        if (!personalHasUnsavedChanges && !teamHasUnsavedChanges) {
            onClose();
            return;
        }
        persistDirtyTasksInBackground("manual_save", true, personalHasUnsavedChanges, teamHasUnsavedChanges);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            closeTaskContextMenu();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (quickAddField != null && quickAddField.isFocused()) {
                if (!TodoScreenViewModeSupport.isAddTaskAllowedInCurrentView(viewMode.name())) {
                    addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
                    return true;
                }
                onAddTask();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 清理当前界面内所有文本输入框的焦点。
     */
    private void clearTextFieldFocus() {
        boolean detailFieldFocused = (titleField != null && titleField.isFocused())
                || (descField != null && descField.isFocused())
                || (tagField != null && tagField.isFocused())
                || (detailDraft != null && detailDraft.titleEditing);
        TodoScreenFocusSupport.clearTextFieldFocus(
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField
        );
        projectSearchPrefixDropdownOpen = false;
        this.setFocused(null);
        finishDetailTitleEditing();
        boolean discardedEmptyPlaceholder = discardSelectedEmptyPlaceholderSubtask();
        if (discardedEmptyPlaceholder) {
            if (isGuiAutoSaveEnabled()) {
                persistCurrentViewTasksInBackground("auto_discard_empty_subtask");
            }
            return;
        }
        if (detailFieldFocused) {
            triggerAutoSaveIfNeeded("auto_blur_detail", false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncTaskListReorderState();
        if (handleContextMenuClick(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && layoutMetrics != null && layoutMetrics.sidebarOverlay && layoutMetrics.sidebarVisible
                && !layoutMetrics.sidebarBounds.contains(mouseX, mouseY)
                && (sidebarToggleButton == null || !sidebarToggleButton.isMouseOver(mouseX, mouseY))) {
            sidebarOverlayVisible = false;
            layoutMetrics = TodoScreenLayoutSupport.buildMainLayoutMetrics(
                    ModConfig.getInstance(),
                    responsiveTier,
                    this.width,
                    this.height,
                    sidebarOverlayVisible,
                    detailOverlayVisible,
                    selectedTask != null
            );
            applyResponsiveWidgetVisibility();
        }
        boolean prefixDropdownVisible = TodoScreenProjectSearchSupport.shouldShowProjectSearchPrefixDropdown(
                projectSearchPrefixDropdownOpen,
                currentSpaceMode == SpaceMode.TEAM,
                isSidebarPanelVisible(),
                projectSearchField
        );
        String clickedPrefix = button == 0
                ? TodoScreenProjectSearchSupport.resolveClickedProjectSearchPrefix(
                        prefixDropdownVisible,
                        projectSearchField,
                        mouseX,
                        mouseY
                )
                : null;
        if (clickedPrefix != null) {
            String current = projectSearchField == null ? "" : projectSearchField.getValue();
            String nextValue = TodoScreenProjectSearchSupport.buildProjectSearchValueWithPrefix(current, clickedPrefix);
            if (projectSearchField != null) {
                projectSearchField.setValue(nextValue);
                projectSearchField.setFocused(true);
                this.setFocused(projectSearchField);
            }
            projectSearchQuery = nextValue;
            updateProjectList();
            projectSearchPrefixDropdownOpen = false;
            return true;
        }
        if ((button == 0 || button == 1) && prefixDropdownVisible
                && !TodoScreenProjectSearchSupport.isProjectSearchFieldHit(projectSearchField, mouseX, mouseY)
                && !TodoScreenProjectSearchSupport.isInsideProjectSearchPrefixDropdown(
                        prefixDropdownVisible,
                        projectSearchField,
                        mouseX,
                        mouseY
                )) {
            projectSearchPrefixDropdownOpen = false;
        }
        if (button == 0
                && TodoScreenProjectSearchSupport.canUseProjectSearchPrefixDropdown(
                        currentSpaceMode == SpaceMode.TEAM,
                        isSidebarPanelVisible()
                )
                && TodoScreenProjectSearchSupport.isProjectSearchFieldHit(projectSearchField, mouseX, mouseY)) {
            projectSearchPrefixDropdownOpen = true;
        }
        refreshClearCompletedButtonState();
        if (button == 0
                && clearCompletedButton != null
                && clearCompletedButton.visible
                && clearCompletedButton.active
                && clearCompletedButton.isMouseOver(mouseX, mouseY)) {
            clearCompletedButton.onPress();
            return true;
        }
        if (button == 0
                && saveButton != null
                && saveButton.visible
                && saveButton.active
                && saveButton.isMouseOver(mouseX, mouseY)) {
            saveButton.onPress();
            return true;
        }
        if (button == 0
                && cancelButton != null
                && cancelButton.visible
                && cancelButton.active
                && cancelButton.isMouseOver(mouseX, mouseY)) {
            cancelButton.onPress();
            return true;
        }
        if (button == 0
                && addSubtaskButton != null
                && addSubtaskButton.visible
                && addSubtaskButton.active
                && addSubtaskButton.isMouseOver(mouseX, mouseY)) {
            addSubtaskButton.onPress();
            return true;
        }
        boolean clickInsideVisibleDetailPanel = layoutMetrics != null
                && layoutMetrics.detailVisible
                && selectedTask != null
                && layoutMetrics.detailBounds.contains(mouseX, mouseY);
        if (button == 0 && !clickInsideVisibleDetailPanel) {
            discardSelectedEmptyPlaceholderSubtask();
        }
        if (!clickInsideVisibleDetailPanel
                && taskListWidget != null
                && taskListWidget.mouseClicked(mouseX, mouseY, button)) {
            resetTaskRowDragState();
            closeTaskContextMenu();
            return true;
        }
        if (!clickInsideVisibleDetailPanel
                && projectListWidget != null
                && isSidebarPanelVisible()
                && projectListWidget.mouseClicked(mouseX, mouseY, button)) {
            resetTaskRowDragState();
            closeTaskContextMenu();
            return true;
        }

        if (!clickInsideVisibleDetailPanel && taskListWidget != null) {
            TaskListWidget.TaskSectionHitResult sectionHit = taskListWidget.getSectionAt(mouseX, mouseY);
            if (button == 0
                    && sectionHit != null
                    && sectionHit.getRowType() == TaskListWidget.RowType.SECTION_HEADER
                    && toggleTaskSection(sectionHit.getSectionId())) {
                applySearchFilter();
                resetTaskRowDragState();
                closeTaskContextMenu();
                return true;
            }
            Task clickedTask = taskListWidget.getTaskAt((int)mouseX, (int)mouseY);
            if (clickedTask != null) {
                if (button == 0
                        && selectedTask != null
                        && !Objects.equals(clickedTask.getId(), selectedTask.getId())) {
                    discardSelectedEmptyPlaceholderSubtask();
                }
                if (button == 0
                        && sectionHit != null
                        && (sectionHit.getRowType() == TaskListWidget.RowType.TASK
                        || sectionHit.getRowType() == TaskListWidget.RowType.SUBTASK)
                        && TodoScreenPermissionSupport.canTaskReorderInView(
                        this.minecraft,
                        currentProject,
                        viewMode.name()
                )
                        && taskListWidget.canStartDrag(clickedTask)) {
                    String dragSectionId = sectionHit == null ? "active" : sectionHit.getSectionId();
                    taskListWidget.armPendingTaskDrag(clickedTask, dragSectionId, mouseX, mouseY);
                    pendingClickSelectionTask = clickedTask;
                    closeTaskContextMenu();
                    return true;
                }
                resetTaskRowDragState();
                selectTask(clickedTask);
                if (button == 1) {
                    openTaskContextMenu(clickedTask, (int) mouseX, (int) mouseY);
                } else {
                    closeTaskContextMenu();
                }
                return true;
            }
        }

        if (button == 0
                && titleField != null
                && titleField.visible
                && titleField.isMouseOver(mouseX, mouseY)
                && (detailDraft == null || !detailDraft.titleEditing)) {
                beginDetailTitleEditing();
                return true;
        }

        boolean textFieldHit = TodoScreenHitTestSupport.isMouseOverAnyTextField(
                mouseX,
                mouseY,
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField,
                false
        );
        if (button == 0 && !textFieldHit) {
            clearTextFieldFocus();
        }

        boolean contextMenuHit = TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)
                && TodoScreenContextMenuSupport.isInsideContextMenu(
                mouseX,
                mouseY,
                contextMenuX,
                contextMenuY,
                contextMenuWidth,
                TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight)
        );
        boolean clickInEditArea = TodoScreenHitTestSupport.isClickInEditArea(
                mouseX,
                mouseY,
                searchField,
                projectSearchField,
                quickAddField,
                titleField,
                descField,
                tagField,
                false,
                detailCloseButton,
                addSubtaskButton,
                claimButton,
                abandonButton,
                assignOthersButton,
                contextMenuHit
        );
        boolean cleared = false;
        boolean detailBlankClicked = button == 0
                && selectedTask != null
                && layoutMetrics != null
                && layoutMetrics.detailVisible
                && layoutMetrics.detailBounds.contains(mouseX, mouseY)
                && !clickInEditArea;
        resetTaskRowDragState();
        if (button == 0 && selectedTask != null && !detailBlankClicked && !clickInEditArea) {
            triggerAutoSaveIfNeeded("auto_clear_selection", false);
            clearSelectedTask();
            cleared = true;
        }
        if (button == 0 || button == 1) {
            closeTaskContextMenu();
        }
        if (detailBlankClicked) {
            return true;
        }

        if (this.minecraft == null || Minecraft.getInstance() == null) {
            return cleared;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        return handled || cleared;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return handleMouseScrolled(mouseX, mouseY, 0.0D, amount);
    }

    /**
     * 兼容 1.20.2 及以上版本的四参数滚轮事件。
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return handleMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * 统一处理待办主界面的滚轮事件。
     */
    private boolean handleMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double amount = verticalAmount != 0 ? verticalAmount : horizontalAmount;
        boolean handled = false;
        if (taskListWidget != null) {
            handled = taskListWidget.mouseScrolled(mouseX, mouseY, 0, amount);
        }
        if (!handled && projectListWidget != null && isSidebarPanelVisible()) {
            handled = projectListWidget.mouseScrolled(mouseX, mouseY, amount);
        }
        if (!handled) {
            handled = ScreenCompat.callSuperMouseScrolled(this, mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        syncTaskListReorderState();
        if (taskListWidget != null && taskListWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            pendingClickSelectionTask = null;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncTaskListReorderState();
        if (taskListWidget != null && taskListWidget.mouseReleased(mouseX, mouseY, button)) {
            resetTaskRowDragState();
            return true;
        }
        if (button == 0 && pendingClickSelectionTask != null) {
            Task taskToSelect = pendingClickSelectionTask;
            resetTaskRowDragState();
            selectTask(taskToSelect);
            return true;
        }
        resetTaskRowDragState();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void resetTaskRowDragState() {
        pendingClickSelectionTask = null;
    }

    @Override
    public void onClose() {
        discardSelectedEmptyPlaceholderSubtask();
        if (triggerAutoSaveIfNeeded("auto_close", true)) {
            return;
        }
        if (!personalTaskSaveInFlight) {
            discardPersonalTasksOnCloseIfNeeded();
        }
        if (!teamTaskSaveInFlight && viewMode != ViewMode.PERSONAL && teamHasUnsavedChanges) {
            ClientBridge.ops().requestTeamSync();
            clearDeferredTeamTasksSnapshot();
            restoreTeamTasksFromCachedSnapshot();
            teamHasUnsavedChanges = false;
        }
        hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
        this.minecraft.setScreen(parent);
    }

    /**
     * 在关闭界面且未保存时按需丢弃个人任务的临时改动。
     */
    private void discardPersonalTasksOnCloseIfNeeded() {
        if (!personalHasUnsavedChanges || personalTaskManager == null) {
            return;
        }
        boolean restored = TodoScreenPersistenceSupport.discardUnsavedPersonalTasks(personalTaskManager, this.minecraft);
        if (restored) {
            personalHasUnsavedChanges = false;
            if (viewMode == ViewMode.PERSONAL) {
                hasUnsavedChanges = false;
            }
            publishPersonalTasksToHud();
        }
    }

    /**
     * 关闭未保存的团队视图时恢复到最近一次已同步团队任务基线。
     */
    private void restoreTeamTasksFromCachedSnapshot() {
        if (teamTaskManager == null) {
            return;
        }
        teamTaskManager.clearAll();
        for (Task task : copyTasks(cachedTeamTasksSnapshot)) {
            teamTaskManager.addTask(task);
        }
        if (currentSpaceMode == SpaceMode.TEAM) {
            taskManager = teamTaskManager;
        }
    }

    // Add a new task

    private void onAddTask() {
        if (currentProject == null) {
            addNotification(Component.translatable("message.todolist.select_project_first").getString());
            return;
        }
        if (!TodoScreenViewModeSupport.isAddTaskAllowedInCurrentView(viewMode.name())) {
            addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
            return;
        }
        if (!TodoScreenPermissionSupport.canAddTaskInView(this.minecraft, currentProject, viewMode.name())) {
            addNotification(Component.translatable("message.todolist.no_permission_add_team").getString());
            return;
        }
        String title = TodoScreenTestSupport.getFieldValue(quickAddField, "");
        String desc = "";

        if (!title.isEmpty()) {
            Task task = taskManager.addTask(title, desc);
            task.setPriority(selectedPriority);
            if (currentProject != null) {
                task.setProjectId(currentProject.getId());
            }

            if (viewMode != ViewMode.PERSONAL) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    String uuid = this.minecraft.player.getUUID().toString();
                    String name = this.minecraft.player.getName().getString();
                    task.setScope(Task.Scope.TEAM);
                    task.setCreatorUuid(uuid);
                    if (viewMode == ViewMode.TEAM_ASSIGNED) {
                        task.setAssigneeUuid(uuid);
                        task.setAssigneeName(name);
                    }
                } else {
                    task.setScope(Task.Scope.TEAM);
                }
            }

            clearSelectedTask();
            selectedPriority = Task.Priority.MEDIUM;
            if (quickAddField != null) {
                quickAddField.setValue("");
            }

            markUnsaved();
            applySearchFilter();
            if (isGuiAutoSaveEnabled()) {
                persistCurrentViewTasksInBackground("auto_add");
            }
        }
    }

    /**
     * 响应详情面板关闭按钮，按配置决定是否先自动保存再关闭详情区。
     */
    private void onDetailClose() {
        if (discardSelectedEmptyPlaceholderSubtask()) {
            if (isGuiAutoSaveEnabled()) {
                persistCurrentViewTasksInBackground("auto_discard_empty_subtask");
            }
            return;
        }
        triggerAutoSaveIfNeeded("auto_close_detail", false);
        clearSelectedTask();
    }

    /**
     * 在当前选中的父任务下创建一个空白子任务，并切入标题编辑模式。
     */
    private void onAddSubtask() {
        Task parentTask = selectedTask;
        if (parentTask == null) {
            return;
        }
        createSubtaskFromParent(parentTask);
    }

    /**
     * 从指定父任务创建一个空白子任务，并切入标题编辑模式。
     *
     * @param parentTask 父任务
     */
    private void createSubtaskFromParent(Task parentTask) {
        if (parentTask == null || !parentTask.isTopLevelTask()) {
            return;
        }
        if (currentProject == null) {
            addNotification(Component.translatable("message.todolist.select_project_first").getString());
            return;
        }
        if (!TodoScreenViewModeSupport.isAddTaskAllowedInCurrentView(viewMode.name())) {
            addNotification(Component.translatable("message.todolist.add_not_allowed_in_view").getString());
            return;
        }
        if (!canAddSubtaskToParent(parentTask)) {
            addNotification(Component.translatable("message.todolist.no_permission_add_team").getString());
            return;
        }
        Task subtask = taskManager.addTask("", "");
        subtask.setPriority(parentTask.getPriority());
        subtask.setProjectId(parentTask.getProjectId());
        subtask.setScope(parentTask.getScope());
        subtask.setCreatorUuid(parentTask.getCreatorUuid());
        subtask.setAssigneeUuid(parentTask.getAssigneeUuid());
        subtask.setAssigneeName(parentTask.getAssigneeName());
        subtask.setParentTaskId(parentTask.getId());
        subtask.setSubtaskSortOrder(resolveNextSubtaskSortOrder(parentTask.getId()));

        markUnsaved();
        applySearchFilter();
        selectTask(subtask, false);
        beginDetailTitleEditing();
        closeTaskContextMenu();
    }

    private void selectTask(Task task) {
        selectTask(task, true);
    }

    /**
     * 切换当前选中任务，并按需触发“切换任务”自动保存。
     *
     * @param task 目标任务
     * @param triggerAutoSaveBeforeSelect 是否在切换前执行自动保存
     */
    private void selectTask(Task task, boolean triggerAutoSaveBeforeSelect) {
        if (task != null
                && selectedTask != null
                && !Objects.equals(task.getId(), selectedTask.getId())) {
            discardSelectedEmptyPlaceholderSubtask();
        }
        if (triggerAutoSaveBeforeSelect
                && task != null
                && (selectedTask == null || !task.getId().equals(selectedTask.getId()))) {
            triggerAutoSaveIfNeeded("auto_switch_task", false);
        }
        selectedTask = task;
        selectedPriority = task.getPriority();
        detailDraft = createDetailDraft(task);
        detailOverlayVisible = true;
        rebuildUI();
    }

    private void clearSelectedTask() {
        selectedTask = null;
        detailDraft = null;
        detailOverlayVisible = false;
        rebuildUI();
    }

    /**
     * 判断当前详情区是否需要展示子任务的父任务上下文。
     *
     * @return true 表示应显示父任务上下文提示
     */
    private boolean hasSelectedTaskParentContext() {
        return resolveSelectedTaskParentContextComponent() != null;
    }

    /**
     * 判断当前详情区是否应显示“添加子任务”入口。
     *
     * @return true 表示显示“添加子任务”按钮
     */
    private boolean shouldShowAddSubtaskButton() {
        return selectedTask != null
                && selectedTask.isTopLevelTask()
                && canAddSubtaskToSelectedParent();
    }

    /**
     * 判断当前选中的父任务是否允许继续创建子任务。
     *
     * @return true 表示允许创建子任务
     */
    private boolean canAddSubtaskToSelectedParent() {
        return canAddSubtaskToParent(selectedTask);
    }

    /**
     * 判断给定父任务是否允许继续创建子任务。
     *
     * @param parentTask 父任务
     * @return true 表示允许创建子任务
     */
    private boolean canAddSubtaskToParent(Task parentTask) {
        if (parentTask == null
                || !parentTask.isTopLevelTask()
                || currentProject == null
                || taskManager == null
                || parentTask.isCompleted()
                || !TodoScreenViewModeSupport.isAddTaskAllowedInCurrentView(viewMode.name())
                || !TodoScreenPermissionSupport.canAddTaskInView(this.minecraft, currentProject, viewMode.name())) {
            return false;
        }
        Task latestParent = taskManager.getTask(parentTask.getId());
        return latestParent != null
                && latestParent.isTopLevelTask()
                && !latestParent.isCompleted()
                && currentProject.getId() != null
                && currentProject.getId().equals(latestParent.getProjectId());
    }

    /**
     * 解析当前选中子任务的父任务上下文文案。
     *
     * @return 父任务上下文组件；没有父任务上下文时返回 {@code null}
     */
    private Component resolveSelectedTaskParentContextComponent() {
        String parentTitle = resolveSelectedTaskParentTitle();
        if (parentTitle == null || parentTitle.isEmpty()) {
            return null;
        }
        String fullText = Component.translatable("gui.todolist.label.parent_task", parentTitle).getString();
        int maxWidth = resolveSelectedTaskParentContextMaxWidth();
        if (maxWidth <= 0) {
            return Component.literal(fullText);
        }
        String displayText = TodoScreenTextSupport.trimTextToWidth(this.font, fullText, maxWidth);
        if (displayText.isEmpty() && this.font != null) {
            displayText = this.font.plainSubstrByWidth(fullText, maxWidth).trim();
        }
        return Component.literal(displayText.isEmpty() ? fullText : displayText);
    }

    /**
     * 解析当前选中子任务的父任务标题。
     *
     * @return 父任务标题；没有父任务上下文时返回空字符串
     */
    private String resolveSelectedTaskParentTitle() {
        if (selectedTask == null || !selectedTask.isSubtask() || taskManager == null) {
            return "";
        }
        String parentTaskId = selectedTask.getParentTaskId();
        if (parentTaskId == null || parentTaskId.isEmpty()) {
            return "";
        }
        Task parentTask = taskManager.getTask(parentTaskId);
        if (parentTask == null) {
            return "";
        }
        String parentTitle = parentTask.getTitle();
        if (parentTitle == null || parentTitle.isEmpty()) {
            parentTitle = parentTaskId;
        }
        return parentTitle;
    }

    /**
     * 返回当前选中子任务的父任务上下文文本，供测试断言。
     *
     * @return 父任务上下文文本；不存在时返回空字符串
     */
    private String resolveSelectedTaskParentContextTextForTest() {
        Component component = resolveSelectedTaskParentContextComponent();
        if (component == null) {
            return "";
        }
        return component.getString();
    }

    /**
     * 计算详情区“所属主任务”文案可用的最大显示宽度。
     *
     * @return 父任务上下文文案的最大可用宽度
     */
    private int resolveSelectedTaskParentContextMaxWidth() {
        if (descField != null && descField.visible) {
            return Math.max(0, descField.getWidth());
        }
        if (layoutMetrics == null || !layoutMetrics.detailVisible) {
            return 0;
        }
        return Math.max(0, layoutMetrics.detailBounds.width - 8);
    }

    /**
     * 计算当前父任务下下一个可用的子任务排序号。
     *
     * @param parentTaskId 父任务 ID
     * @return 新子任务应使用的排序号
     */
    private long resolveNextSubtaskSortOrder(String parentTaskId) {
        if (taskManager == null || parentTaskId == null || parentTaskId.isEmpty()) {
            return 0L;
        }
        long nextSortOrder = 0L;
        for (Task task : taskManager.getAllTasks()) {
            if (task == null || !parentTaskId.equals(task.getParentTaskId())) {
                continue;
            }
            nextSortOrder = Math.max(nextSortOrder, task.getSubtaskSortOrder() + 1L);
        }
        return nextSortOrder;
    }

    /**
     * 根据任务创建详情面板使用的编辑草稿。
     *
     * @param task 目标任务
     * @return 对应的详情草稿；任务为空时返回 {@code null}
     */
    private TaskDetailDraft createDetailDraft(Task task) {
        if (task == null) {
            return null;
        }
        return new TaskDetailDraft(task.getId(), task.getTitle(), task.getDescription(), joinTaskTags(task));
    }

    /**
     * 将当前详情草稿状态同步到标题、描述和标签输入控件。
     */
    private void syncDetailWidgetsFromState() {
        syncingDetailWidgets = true;
        try {
            if (titleField != null) {
                titleField.setValue(detailDraft == null ? "" : detailDraft.title);
            }
            if (descField != null) {
                descField.setValue(detailDraft == null ? "" : detailDraft.description);
            }
            if (tagField != null) {
                tagField.setValue(detailDraft == null ? "" : detailDraft.tags);
            }
        } finally {
            syncingDetailWidgets = false;
        }
        applyDetailWidgetEditability();
    }

    /**
     * 根据当前选中任务和权限状态更新详情控件的可编辑性。
     */
    private void applyDetailWidgetEditability() {
        boolean detailVisible = layoutMetrics == null ? selectedTask != null : layoutMetrics.detailVisible;
        boolean editable = detailVisible && canEditSelectedTaskDetails();
        if (titleField != null) {
            boolean titleEditing = editable && detailDraft != null && detailDraft.titleEditing;
            titleField.setEditable(titleEditing);
            titleField.active = detailVisible;
            titleField.visible = detailVisible;
        }
        if (descField != null) {
            descField.active = editable;
            descField.visible = detailVisible;
        }
        if (tagField != null) {
            tagField.setEditable(editable);
            tagField.visible = detailVisible;
        }
        if (detailCloseButton != null) {
            boolean showCloseButton = detailVisible && selectedTask != null;
            detailCloseButton.visible = showCloseButton;
            detailCloseButton.active = showCloseButton;
        }
        if (addSubtaskButton != null) {
            boolean showAddSubtask = detailVisible && shouldShowAddSubtaskButton();
            addSubtaskButton.visible = showAddSubtask;
            addSubtaskButton.active = showAddSubtask;
        }
    }

    /**
     * 判断当前选中任务的详情是否允许编辑。
     *
     * @return 允许编辑时返回 {@code true}
     */
    private boolean canEditSelectedTaskDetails() {
        return selectedTask != null
                && isSelectedTaskValid()
                && !selectedTask.isCompleted()
                && TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.EDIT_TASK,
                selectedTask,
                this.minecraft,
                currentProject,
                viewMode.name()
        );
    }

    /**
     * 让详情标题进入编辑状态。
     */
    private void beginDetailTitleEditing() {
        if (detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.titleEditing = true;
        applyDetailWidgetEditability();
        if (titleField != null) {
            titleField.setFocused(true);
            this.setFocused(titleField);
        }
    }

    /**
     * 结束详情标题编辑模式，并恢复只读展示状态。
     */
    private void finishDetailTitleEditing() {
        if (detailDraft == null || !detailDraft.titleEditing) {
            return;
        }
        detailDraft.titleEditing = false;
        applyDetailWidgetEditability();
    }

    /**
     * 响应详情标题输入框内容变化。
     *
     * @param text 最新标题文本
     */
    private void onDetailTitleChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.title = text == null ? "" : text;
        selectedTask.setTitle(detailDraft.title);
        markUnsaved();
    }

    /**
     * 响应详情描述输入框内容变化。
     *
     * @param text 最新描述文本
     */
    private void onDetailDescriptionChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.description = text == null ? "" : text;
        selectedTask.setDescription(detailDraft.description);
        markUnsaved();
    }

    /**
     * 响应详情标签输入框内容变化。
     *
     * @param text 最新标签文本
     */
    private void onDetailTagsChanged(String text) {
        if (syncingDetailWidgets || detailDraft == null || !canEditSelectedTaskDetails()) {
            return;
        }
        detailDraft.tags = text == null ? "" : text;
        String value = TodoScreenTestSupport.getFieldValue(tagField, "");
        if (value.isEmpty()) {
            selectedTask.clearTags();
        } else {
            List<String> tags = new ArrayList<>();
            for (String part : value.split(",")) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
            selectedTask.setTags(tags);
        }
        markUnsaved();
    }

    /**
     * 将任务标签列表拼接为输入框使用的文本。
     *
     * @param task 目标任务
     * @return 逗号分隔的标签文本
     */
    private String joinTaskTags(Task task) {
        if (task == null || task.getTags() == null || task.getTags().isEmpty()) {
            return "";
        }
        return String.join(",", task.getTags());
    }

    private boolean isSelectedTaskValid() {
        if (selectedTask == null) {
            return false;
        }
        if (currentProject == null) {
            return false;
        }
        String selectedId = selectedTask.getId();
        if (selectedId == null || selectedId.isEmpty()) {
            return false;
        }
        String projectId = currentProject.getId();
        if (!selectedTask.belongsToProject(projectId)) {
            return false;
        }
        for (TaskListWidget.SectionModel section : buildTaskPaneSections()) {
            if (section == null) {
                continue;
            }
            for (Task task : section.getTasks()) {
                if (task != null && selectedId.equals(task.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断当前项目是否允许通过 GUI 清理已完成任务。
     *
     * @return 允许清理时返回 true
     */
    private boolean canClearCompletedTasksInCurrentProject() {
        if (currentProject == null || taskManager == null || this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        if (currentProject.getScope() == Project.Scope.PERSONAL) {
            return true;
        }
        if (this.minecraft.player.hasPermissions(2)) {
            return true;
        }
        String ownerUuid = currentProject.getOwnerUuid();
        String currentPlayerUuid = TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft);
        return ownerUuid != null && !ownerUuid.isEmpty() && ownerUuid.equals(currentPlayerUuid);
    }

    /**
     * 返回当前项目下全部已完成任务的数量。
     *
     * @return 已完成任务数量
     */
    private int getCompletedTaskCountInCurrentProject() {
        return TodoScreenTaskSupport.collectCompletedTaskIdsForProject(
                taskManager,
                currentProject == null ? null : currentProject.getId()
        ).size();
    }

    /**
     * 刷新“清理已完成”按钮的可见性、可点击状态与布局位置。
     */
    private void refreshClearCompletedButtonState() {
        if (clearCompletedButton == null) {
            return;
        }
        boolean canShowButton = canClearCompletedTasksInCurrentProject() && getCompletedTaskCountInCurrentProject() > 0;
        if (!canShowButton || taskListWidget == null) {
            clearCompletedButton.visible = false;
            clearCompletedButton.active = false;
            return;
        }
        int centerY = taskListWidget.getVisibleSectionHeaderCenterY("completed");
        if (centerY < 0) {
            clearCompletedButton.visible = false;
            clearCompletedButton.active = false;
            return;
        }
        int buttonWidth = clearCompletedButton.getWidth();
        int buttonHeight = clearCompletedButton.getHeight();
        clearCompletedButton.setX(Math.max(0, taskListWidget.getContentRightX() - buttonWidth));
        clearCompletedButton.setY(centerY - buttonHeight / 2);
        clearCompletedButton.visible = true;
        clearCompletedButton.active = !taskSaveInFlight;
    }

    private void updateButtonStates() {
        boolean hasSelection = selectedTask != null;
        List<Task> managedTasks = taskManager == null ? List.of() : taskManager.getAllTasks();
        boolean isCompleted = hasSelection && selectedTask.isCompleted();
        boolean isAssigned = hasSelection && TaskAssignmentSupport.isAggregatedAssigned(selectedTask, managedTasks);
        Role role = TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject);
        ViewScope scope = TodoScreenPermissionSupport.resolveViewScope(viewMode.name());
        boolean isAssigneeSelf = hasSelection && TodoScreenPermissionSupport.isCurrentPlayerAssignee(this.minecraft, selectedTask, managedTasks);
        boolean projectMember = TodoScreenPermissionSupport.isCurrentPlayerProjectMember(this.minecraft, currentProject);
        boolean allowMemberCreate = currentProject != null && currentProject.isAllowMemberCreate();
        boolean allowAllPlayersClaimComplete = currentProject != null && currentProject.isAllowAllPlayersClaimComplete();
        Context context = new Context(scope, isCompleted, isAssigned, isAssigneeSelf, false, false, projectMember, allowMemberCreate, allowAllPlayersClaimComplete);
        boolean showAssignButtons = viewMode != ViewMode.PERSONAL;
        boolean detailVisible = layoutMetrics == null ? hasSelection : layoutMetrics.detailVisible;
        if (claimButton != null) {
            claimButton.visible = detailVisible && showAssignButtons;
            boolean canClaim = hasSelection
                    && PermissionCenter.canPerform(Operation.CLAIM_TASK, role, context);
            claimButton.active = detailVisible && showAssignButtons && canClaim;
        }
        if (abandonButton != null) {
            abandonButton.visible = detailVisible && showAssignButtons;
            boolean canAbandon = hasSelection
                    && PermissionCenter.canPerform(Operation.ABANDON_TASK, role, context);
            abandonButton.active = detailVisible && showAssignButtons && canAbandon;
        }
        if (assignOthersButton != null) {
            boolean showAssignOthers = showAssignButtons
                    && PermissionCenter.canPerform(Operation.ASSIGN_OTHERS, role, context);
            assignOthersButton.visible = detailVisible && showAssignOthers;
            boolean canAssignOthers = detailVisible && showAssignOthers && hasSelection;
            assignOthersButton.active = canAssignOthers;
        }
        if (addSubtaskButton != null) {
            boolean showAddSubtask = detailVisible && shouldShowAddSubtaskButton();
            addSubtaskButton.visible = showAddSubtask;
            addSubtaskButton.active = showAddSubtask && canAddSubtaskToSelectedParent();
        }
        if (saveButton != null) {
            saveButton.active = !taskSaveInFlight;
        }
        refreshClearCompletedButtonState();
        rebuildContextMenuIfNeeded();
        applyResponsiveWidgetVisibility();
    }

    private void openTaskContextMenu(Task task, int mouseX, int mouseY) {
        if (task == null) {
            closeTaskContextMenu();
            return;
        }
        contextMenuTask = task;
        contextMenuItems = buildContextMenuItems(task);
        if (contextMenuItems.isEmpty()) {
            closeTaskContextMenu();
            return;
        }
        int maxTextWidth = 0;
        for (ContextMenuItem item : contextMenuItems) {
            maxTextWidth = Math.max(maxTextWidth, this.font.width(item.text));
        }
        contextMenuWidth = Math.max(90, maxTextWidth + 16);
        int menuHeight = TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight);
        int[] origin = TodoScreenContextMenuSupport.resolveMenuOrigin(
                mouseX,
                mouseY,
                this.width,
                this.height,
                contextMenuWidth,
                menuHeight,
                4
        );
        contextMenuX = origin[0];
        contextMenuY = origin[1];
    }

    private List<ContextMenuItem> buildContextMenuItems(Task task) {
        List<ContextMenuItem> items = new ArrayList<>();
        if (task == null) {
            return items;
        }
        boolean canEdit = TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.EDIT_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        ) && !task.isCompleted();
        boolean canDelete = TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.DELETE_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        );
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.high"), canEdit, () -> applyTaskPriority(task, Task.Priority.HIGH)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.medium"), canEdit, () -> applyTaskPriority(task, Task.Priority.MEDIUM)));
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.priority.low"), canEdit, () -> applyTaskPriority(task, Task.Priority.LOW)));
        if (task.isTopLevelTask()) {
            items.add(new ContextMenuItem(
                    Component.translatable("gui.todolist.subtask.add"),
                    canAddSubtaskToParent(task),
                    () -> createSubtaskFromParent(task)
            ));
        }
        items.add(new ContextMenuItem(Component.translatable("gui.todolist.delete"), canDelete, () -> deleteTaskFromContextMenu(task)));
        return items;
    }

    private void applyTaskPriority(Task task, Task.Priority priority) {
        if (task == null
                || priority == null
                || !TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.EDIT_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        )
                || task.isCompleted()) {
            closeTaskContextMenu();
            return;
        }
        Task.Priority previousPriority = task.getPriority();
        if (previousPriority == priority) {
            closeTaskContextMenu();
            return;
        }
        task.setPriority(priority);
        reorderCurrentViewActiveTasksAfterPriorityChange(task);
        this.selectedPriority = priority;
        markUnsaved();
        applySearchFilter();
        if (taskListWidget != null) {
            taskListWidget.ensureVisible(task);
        }
        ClientBridge.ops().sendUpdateTask(task);
        closeTaskContextMenu();
    }

    /**
     * 处理任务右键菜单中的删除入口，先打开二次确认弹窗。
     *
     * @param task 目标任务
     */
    private void deleteTaskFromContextMenu(Task task) {
        if (task == null || !TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.DELETE_TASK,
                task,
                this.minecraft,
                currentProject,
                viewMode.name()
        )) {
            closeTaskContextMenu();
            return;
        }
        closeTaskContextMenu();
        openDeleteTaskConfirmScreen(task);
    }

    /**
     * 打开任务删除确认弹窗，避免误删任务。
     *
     * @param task 待删除任务
     */
    private void openDeleteTaskConfirmScreen(Task task) {
        if (task == null || task.getId() == null || minecraft == null) {
            return;
        }
        String taskId = task.getId();
        String taskTitle = task.getTitle() == null ? "" : task.getTitle();
        Component message = Component.translatable("gui.todolist.task.delete_confirm.message", taskTitle);
        minecraft.setScreen(new ConfirmActionScreen(
                this,
                Component.translatable("gui.todolist.task.delete_confirm.title"),
                message,
                Component.translatable("gui.todolist.delete"),
                () -> confirmDeleteTask(taskId)
        ));
    }

    /**
     * 打开“清理已完成”确认弹窗，避免误删当前项目中的已完成任务。
     */
    private void openClearCompletedConfirmScreen() {
        if (minecraft == null) {
            return;
        }
        if (!canClearCompletedTasksInCurrentProject()) {
            addNotification(Component.translatable("message.todolist.completed.clear.permission_denied").getString());
            refreshClearCompletedButtonState();
            return;
        }
        int completedTaskCount = getCompletedTaskCountInCurrentProject();
        if (completedTaskCount <= 0) {
            addNotification(Component.translatable("message.todolist.completed.clear.none").getString());
            refreshClearCompletedButtonState();
            return;
        }
        String projectName = currentProject == null ? "" : ProjectNameFormatter.toDisplayText(currentProject).getString();
        Component message = Component.translatable(
                "gui.todolist.completed.clear_confirm.message",
                projectName,
                Integer.toString(completedTaskCount)
        );
        minecraft.setScreen(new ConfirmActionScreen(
                this,
                Component.translatable("gui.todolist.completed.clear_confirm.title"),
                message,
                Component.translatable("gui.todolist.completed.clear_confirm.button"),
                this::confirmClearCompletedTasks
        ));
    }

    /**
     * 在用户确认后真正删除任务，并同步刷新选中状态与列表显示。
     * 删除成功后会立即尝试持久化当前视图任务，避免再额外点击保存按钮。
     *
     * @param taskId 待删除任务 ID
     */
    private void confirmDeleteTask(String taskId) {
        if (taskManager == null || taskId == null || taskId.isBlank()) {
            return;
        }
        taskManager.deleteTask(taskId);
        if (selectedTask != null && selectedTask.getId() != null && selectedTask.getId().equals(taskId)) {
            clearSelectedTask();
        }
        applySearchFilter();
        markUnsaved();
        persistCurrentViewTasksInBackground("deletion");
    }

    /**
     * 在用户确认后真正清理当前项目中的已完成任务，并立即后台持久化。
     */
    private void confirmClearCompletedTasks() {
        if (!canClearCompletedTasksInCurrentProject() || currentProject == null || taskManager == null) {
            addNotification(Component.translatable("message.todolist.completed.clear.permission_denied").getString());
            refreshClearCompletedButtonState();
            return;
        }
        List<String> completedTaskIds = TodoScreenTaskSupport.collectCompletedTaskIdsForProject(taskManager, currentProject.getId());
        if (completedTaskIds.isEmpty()) {
            addNotification(Component.translatable("message.todolist.completed.clear.none").getString());
            refreshClearCompletedButtonState();
            return;
        }
        boolean removedSelectedTask = selectedTask != null
                && selectedTask.getId() != null
                && completedTaskIds.contains(selectedTask.getId());
        for (String taskId : completedTaskIds) {
            taskManager.deleteTask(taskId);
        }
        if (removedSelectedTask) {
            clearSelectedTask();
        } else {
            applySearchFilter();
        }
        markUnsaved();
        addNotification(Component.translatable(
                "message.todolist.completed.clear.success",
                Integer.toString(completedTaskIds.size())
        ).getString());
        persistCurrentViewTasksInBackground("clear_completed");
    }

    /**
     * 在关键任务操作后后台持久化当前视图任务，避免渲染线程等待 H2 或文件 IO。
     *
     * @param operationName 操作名称，用于日志定位
     */
    private void persistCurrentViewTasksInBackground(String operationName) {
        persistDirtyTasksInBackground(
                operationName,
                false,
                viewMode == ViewMode.PERSONAL,
                viewMode != ViewMode.PERSONAL
        );
    }

    /**
     * 将脏任务快照提交到后台保存队列，并在完成后回到主线程同步网络、HUD 和保存状态。
     *
     * @param operationName 操作名称，用于日志定位
     * @param closeAfterSave 保存成功后是否关闭当前界面
     * @param savePersonal 是否保存个人任务
     * @param saveTeam 是否保存团队任务
     */
    private void persistDirtyTasksInBackground(String operationName, boolean closeAfterSave, boolean savePersonal, boolean saveTeam) {
        Minecraft currentMinecraft = this.minecraft;
        if (savePersonal) {
            discardEmptyPlaceholderSubtasksBeforeSave(personalTaskManager, operationName, closeAfterSave);
        }
        if (saveTeam) {
            discardEmptyPlaceholderSubtasksBeforeSave(teamTaskManager, operationName, closeAfterSave);
        }
        List<Task> personalSnapshot = savePersonal ? copyTasks(personalTaskManager == null ? List.of() : personalTaskManager.getAllTasks()) : List.of();
        List<Task> teamSnapshot = saveTeam ? copyTasks(teamTaskManager == null ? List.of() : teamTaskManager.getAllTasks()) : List.of();
        List<Task> teamBaseSnapshot = saveTeam ? copyTasks(cachedTeamTasksSnapshot) : List.of();
        long personalVersion = personalSaveVersion;
        long teamVersion = teamSaveVersion;
        pendingTaskSaveCount++;
        if (savePersonal) {
            pendingPersonalTaskSaveCount++;
        }
        if (saveTeam) {
            pendingTeamTaskSaveCount++;
        }
        refreshTaskSaveInFlightFlags();
        updateButtonStates();
        CompletableFuture
                .runAsync(() -> saveTaskSnapshotsToStorage(currentMinecraft, savePersonal, personalSnapshot, saveTeam, teamSnapshot), TASK_SAVE_EXECUTOR)
                .whenComplete((ignored, failure) -> {
                    if (currentMinecraft == null) {
                        finishTaskSaveInFlight(savePersonal, saveTeam);
                        updateButtonStates();
                        return;
                    }
                    currentMinecraft.execute(() -> finishBackgroundTaskPersistence(
                            operationName,
                            closeAfterSave,
                            savePersonal,
                            personalSnapshot,
                            personalVersion,
                            saveTeam,
                            teamBaseSnapshot,
                            teamSnapshot,
                            teamVersion,
                            failure
                    ));
                });
    }

    /**
     * 在保存前移除未填写任何内容的子任务占位项，避免空白子任务被持久化。
     *
     * @param manager 待清理的任务管理器
     * @param operationName 当前保存操作名
     * @param closeAfterSave 保存后是否会关闭当前界面
     */
    private void discardEmptyPlaceholderSubtasksBeforeSave(TaskManager manager, String operationName, boolean closeAfterSave) {
        if (manager == null) {
            return;
        }
        Task selectedBeforeRemoval = selectedTask;
        Task fallbackParentTask = null;
        boolean removedSelectedPlaceholder = false;
        for (Task task : manager.getAllTasks()) {
            if (!isEmptyPlaceholderSubtask(task)) {
                continue;
            }
            if (shouldPreserveEditingPlaceholderSubtask(task, operationName, closeAfterSave)) {
                continue;
            }
            if (selectedBeforeRemoval != null && Objects.equals(selectedBeforeRemoval.getId(), task.getId())) {
                String parentTaskId = task.getParentTaskId();
                fallbackParentTask = parentTaskId == null || parentTaskId.isEmpty() ? null : manager.getTask(parentTaskId);
                removedSelectedPlaceholder = true;
            }
            manager.deleteTask(task.getId());
        }
        if (!removedSelectedPlaceholder) {
            return;
        }
        if (fallbackParentTask != null) {
            selectedTask = fallbackParentTask;
            selectedPriority = fallbackParentTask.getPriority();
            detailDraft = createDetailDraft(fallbackParentTask);
            detailOverlayVisible = true;
        } else {
            selectedTask = null;
            detailDraft = null;
            detailOverlayVisible = false;
        }
        applySearchFilter();
    }

    /**
     * 丢弃当前选中的空白子任务占位项，并优先回退到其父任务详情。
     *
     * @return true 表示本次确实删除了当前选中的空白子任务
     */
    private boolean discardSelectedEmptyPlaceholderSubtask() {
        if (!isEmptyPlaceholderSubtask(selectedTask)) {
            return false;
        }
        TaskManager manager = resolveManagerForTask(selectedTask);
        if (manager == null) {
            return false;
        }
        String parentTaskId = selectedTask.getParentTaskId();
        Task fallbackParentTask = parentTaskId == null || parentTaskId.isEmpty() ? null : manager.getTask(parentTaskId);
        manager.deleteTask(selectedTask.getId());
        if (fallbackParentTask != null) {
            selectedTask = fallbackParentTask;
            selectedPriority = fallbackParentTask.getPriority();
            detailDraft = createDetailDraft(fallbackParentTask);
            detailOverlayVisible = true;
        } else {
            selectedTask = null;
            detailDraft = null;
            detailOverlayVisible = false;
        }
        applySearchFilter();
        refreshUnsavedStateFromSnapshots();
        return true;
    }

    /**
     * 依据当前任务列表与最近一次已保存快照，重新计算 GUI 未保存状态。
     */
    private void refreshUnsavedStateFromSnapshots() {
        List<Task> currentPersonalTasks = personalTaskManager == null ? List.of() : personalTaskManager.getAllTasks();
        List<Task> currentTeamTasks = teamTaskManager == null ? List.of() : teamTaskManager.getAllTasks();
        personalHasUnsavedChanges = !taskSnapshotsEquivalent(currentPersonalTasks, cachedPersonalTasksSnapshot);
        teamHasUnsavedChanges = !taskSnapshotsEquivalent(currentTeamTasks, cachedTeamTasksSnapshot);
        hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
        if (viewMode == ViewMode.PERSONAL) {
            publishPersonalTasksToHud();
        }
        updateButtonStates();
    }

    /**
     * 根据任务作用域解析对应的任务管理器。
     *
     * @param task 目标任务
     * @return 对应任务管理器；无法确定时返回 null
     */
    private TaskManager resolveManagerForTask(Task task) {
        if (task == null) {
            return null;
        }
        if (taskManager != null && task.getId() != null && taskManager.getTask(task.getId()) != null) {
            return taskManager;
        }
        if (task.getScope() == Task.Scope.TEAM) {
            return teamTaskManager;
        }
        return personalTaskManager;
    }

    /**
     * 判断当前空白子任务是否处于“刚创建且仍在编辑”的自动保存窗口内。
     * 这种场景下应先保留占位项，避免用户刚点“添加子任务”就被自动保存链路立即删掉。
     *
     * @param task 待判断子任务
     * @param operationName 当前保存操作名
     * @param closeAfterSave 保存后是否关闭界面
     * @return true 表示本轮保存应暂时保留该占位子任务
     */
    private boolean shouldPreserveEditingPlaceholderSubtask(Task task, String operationName, boolean closeAfterSave) {
        if (task == null || closeAfterSave || operationName == null || !operationName.startsWith("auto_")) {
            return false;
        }
        return selectedTask != null && Objects.equals(selectedTask.getId(), task.getId());
    }

    /**
     * 判断任务是否为尚未填写任何内容的子任务占位项。
     *
     * @param task 待判断任务
     * @return true 表示应在保存前丢弃
     */
    private boolean isEmptyPlaceholderSubtask(Task task) {
        if (task == null || !task.isSubtask()) {
            return false;
        }
        String title = task.getTitle();
        if (title != null && !title.trim().isEmpty()) {
            return false;
        }
        String description = task.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            return false;
        }
        for (String tag : task.getTags()) {
            if (tag != null && !tag.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 在后台线程保存个人和团队任务快照。
     *
     * @param currentMinecraft 当前客户端实例
     * @param savePersonal 是否保存个人任务
     * @param personalSnapshot 个人任务快照
     * @param saveTeam 是否保存团队任务
     * @param teamSnapshot 团队任务快照
     */
    private void saveTaskSnapshotsToStorage(Minecraft currentMinecraft,
                                            boolean savePersonal,
                                            List<Task> personalSnapshot,
                                            boolean saveTeam,
                                            List<Task> teamSnapshot) {
        try {
            H2MaintenanceGuard.ensureWritableIfH2();
            if (savePersonal) {
                ClientTaskStorageHelper.savePersonalTasks(TodoListCommon.getTaskStorage(), currentMinecraft, personalSnapshot);
            }
            if (saveTeam && ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(currentMinecraft)) {
                TodoListCommon.getTaskStorage().saveTeamTasks(teamSnapshot);
            }
        } catch (Exception exception) {
            throw new BackgroundTaskSaveException(exception);
        }
    }

    /**
     * 处理后台任务保存完成状态，并只清理未被后续编辑覆盖的脏标记。
     *
     * @param operationName 操作名称
     * @param closeAfterSave 保存成功后是否关闭
     * @param savedPersonal 是否保存过个人任务
     * @param personalSnapshot 个人任务快照
     * @param personalVersion 保存发起时的个人版本
     * @param savedTeam 是否保存过团队任务
     * @param teamBaseSnapshot 团队任务保存发起时的同步基线
     * @param teamSnapshot 团队任务快照
     * @param teamVersion 保存发起时的团队版本
     * @param failure 保存失败异常
     */
    private void finishBackgroundTaskPersistence(String operationName,
                                                 boolean closeAfterSave,
                                                 boolean savedPersonal,
                                                 List<Task> personalSnapshot,
                                                 long personalVersion,
                                                 boolean savedTeam,
                                                 List<Task> teamBaseSnapshot,
                                                 List<Task> teamSnapshot,
                                                 long teamVersion,
                                                 Throwable failure) {
        if (failure != null) {
            TodoConstants.LOGGER.error("Failed to persist task {} in background", operationName, failure);
            Component failureMessage = resolveSaveFailureMessage(failure);
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(failureMessage, false);
            } else {
                addNotification(failureMessage.getString());
            }
            finishTaskSaveInFlight(savedPersonal, savedTeam);
            updateButtonStates();
            return;
        }
        boolean personalSaveIsCurrent = savedPersonal && personalVersion == personalSaveVersion;
        boolean teamSnapshotStillCurrent = savedTeam && taskSnapshotsEquivalent(
                teamSnapshot,
                teamTaskManager == null ? List.of() : teamTaskManager.getAllTasks()
        );
        boolean teamSaveIsCurrent = savedTeam && (teamVersion == teamSaveVersion || teamSnapshotStillCurrent);
        if (personalSaveIsCurrent) {
            TodoScreen.updateCachedPersonalTasksSnapshot(DataPathProvider.getStorageNamespace(), personalSnapshot);
            if (ClientBridge.ops() != null) {
                ClientBridge.ops().sendReplaceAllTasks(personalSnapshot);
            }
            TodoHudRenderer renderer = ClientPlatformAdapter.getHudRenderer();
            if (renderer != null) {
                renderer.syncPersonalTasksFromGui(personalSnapshot);
            }
            personalHasUnsavedChanges = false;
        }
        if (teamSaveIsCurrent) {
            if (ClientBridge.ops() != null) {
                ClientBridge.ops().sendMergeTeamTasks(teamBaseSnapshot, teamSnapshot);
                rememberPendingTeamAuthoritativeSync(teamBaseSnapshot, teamSnapshot);
            }
            updateCachedTeamTasksSnapshot(teamSnapshot);
            teamHasUnsavedChanges = false;
            clearDeferredTeamTasksSnapshot();
        }
        hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
        applyDeferredTeamSyncIfReady();
        hasUnsavedChanges = personalHasUnsavedChanges || teamHasUnsavedChanges;
        finishTaskSaveInFlight(savedPersonal, savedTeam);
        updateButtonStates();
        if (closeAfterSave && !hasUnsavedChanges && !taskSaveInFlight) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.todolist.saved"), false);
            }
            onClose();
        }
    }

    /**
     * 在本地团队保存完成后应用保存期间暂存的服务端同步，避免权威结果被永久丢弃。
     */
    private void applyDeferredTeamSyncIfReady() {
        if (teamHasUnsavedChanges || pendingTaskSaveCount > 1 || deferredTeamTasksSnapshot == null) {
            return;
        }
        if (!Objects.equals(deferredTeamTasksNamespace, DataPathProvider.getStorageNamespace())) {
            clearDeferredTeamTasksSnapshot();
            return;
        }
        List<Task> deferredTasks = deferredTeamTasksSnapshot;
        clearDeferredTeamTasksSnapshot();
        applyTeamTasksSnapshot(this.minecraft, deferredTasks);
    }

    /**
     * 根据待完成的后台保存数量刷新个人、团队和总体保存中状态。
     */
    private void refreshTaskSaveInFlightFlags() {
        personalTaskSaveInFlight = pendingPersonalTaskSaveCount > 0;
        teamTaskSaveInFlight = pendingTeamTaskSaveCount > 0;
        taskSaveInFlight = pendingTaskSaveCount > 0;
    }

    /**
     * 标记一次后台任务保存完成，并在所有同步副作用结束后刷新保存中状态。
     *
     * @param savedPersonal 本次是否保存个人任务
     * @param savedTeam 本次是否保存团队任务
     */
    private void finishTaskSaveInFlight(boolean savedPersonal, boolean savedTeam) {
        pendingTaskSaveCount = Math.max(0, pendingTaskSaveCount - 1);
        if (savedPersonal) {
            pendingPersonalTaskSaveCount = Math.max(0, pendingPersonalTaskSaveCount - 1);
        }
        if (savedTeam) {
            pendingTeamTaskSaveCount = Math.max(0, pendingTeamTaskSaveCount - 1);
        }
        refreshTaskSaveInFlightFlags();
    }

    /**
     * 后台任务保存异常包装，避免 CompletableFuture 丢失真实原因。
     */
    private static final class BackgroundTaskSaveException extends RuntimeException {
        /**
         * 创建后台任务保存异常。
         *
         * @param cause 原始失败原因
         */
        private BackgroundTaskSaveException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 根据保存异常类型生成用户可见的保存失败提示。
     *
     * @param throwable 保存异常
     * @return 本地化提示组件
     */
    private Component resolveSaveFailureMessage(Throwable throwable) {
        return StorageFailureNotifier.toUserMessage(throwable, "message.todolist.save_failed");
    }

    private void renderTaskContextMenu(GuiGraphics context, int mouseX, int mouseY) {
        if (!TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            return;
        }
        int menuHeight = TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight);
        context.fill(contextMenuX, contextMenuY, contextMenuX + contextMenuWidth, contextMenuY + menuHeight, 0xEE111111);
        context.renderOutline(contextMenuX, contextMenuY, contextMenuWidth, menuHeight, 0xFFFFFFFF);
        for (int i = 0; i < contextMenuItems.size(); i++) {
            ContextMenuItem item = contextMenuItems.get(i);
            int itemTop = contextMenuY + i * contextMenuItemHeight;
            int itemBottom = itemTop + contextMenuItemHeight;
            boolean hovered = mouseX >= contextMenuX && mouseX < contextMenuX + contextMenuWidth
                    && mouseY >= itemTop && mouseY < itemBottom;
            if (hovered) {
                context.fill(contextMenuX + 1, itemTop + 1, contextMenuX + contextMenuWidth - 1, itemBottom - 1, 0xFF2A2A2A);
            }
            int textColor = item.enabled ? 0xFFFFFFFF : 0xFF777777;
            int textY = itemTop + (contextMenuItemHeight - this.font.lineHeight) / 2;
            context.drawString(this.font, item.text, contextMenuX + 6, textY, textColor, false);
        }
    }

    private boolean handleContextMenuClick(double mouseX, double mouseY, int button) {
        if (!TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            return false;
        }
        if (button != 0 && button != 1) {
            return false;
        }
        int menuHeight = TodoScreenContextMenuSupport.resolveMenuHeight(contextMenuItems.size(), contextMenuItemHeight);
        if (!TodoScreenContextMenuSupport.isInsideContextMenu(
                mouseX,
                mouseY,
                contextMenuX,
                contextMenuY,
                contextMenuWidth,
                menuHeight
        )) {
            if (button == 0 || button == 1) {
                closeTaskContextMenu();
            }
            return false;
        }
        if (button != 0) {
            return true;
        }
        int index = TodoScreenContextMenuSupport.resolveClickedItemIndex(
                mouseY,
                contextMenuY,
                contextMenuItemHeight,
                contextMenuItems.size()
        );
        if (index < 0) {
            closeTaskContextMenu();
            return true;
        }
        ContextMenuItem item = contextMenuItems.get(index);
        if (item.enabled && item.action != null) {
            item.action.run();
        } else {
            closeTaskContextMenu();
        }
        return true;
    }

    private void closeTaskContextMenu() {
        contextMenuTask = null;
        contextMenuItems = new ArrayList<>();
    }

    private void rebuildContextMenuIfNeeded() {
        if (!TodoScreenContextMenuSupport.hasContextMenu(contextMenuTask, contextMenuItems)) {
            return;
        }
        Task menuTask = contextMenuTask;
        if (menuTask == null || filteredTasks == null) {
            closeTaskContextMenu();
            return;
        }
        for (Task task : filteredTasks) {
            if (task != null && task.getId() != null && task.getId().equals(menuTask.getId())) {
                contextMenuItems = buildContextMenuItems(task);
                contextMenuTask = task;
                if (contextMenuItems.isEmpty()) {
                    closeTaskContextMenu();
                }
                return;
            }
        }
        closeTaskContextMenu();
    }

    private void markUnsaved() {
        hasUnsavedChanges = true;
        if (viewMode == ViewMode.PERSONAL) {
            personalHasUnsavedChanges = true;
            personalSaveVersion++;
            publishPersonalTasksToHud();
        } else {
            teamHasUnsavedChanges = true;
            teamSaveVersion++;
        }
    }

    /**
     * 将 GUI 当前个人任务快照发布给 HUD，保证 HUD 跟随个人项目列表的即时变更。
     */
    private void publishPersonalTasksToHud() {
        if (personalTaskManager == null) {
            return;
        }
        TodoHudRenderer renderer = ClientPlatformAdapter.getHudRenderer();
        if (renderer != null) {
            renderer.syncPersonalTasksFromGui(personalTaskManager.getAllTasks());
        }
    }

    /**
     * 处理未完成任务手动拖拽排序后的顺序同步。
     *
     * @param reorderedActiveTasks 拖拽后的未完成任务顺序
     */
    private void onManualReorderActiveTasks(List<Task> reorderedActiveTasks) {
        if (!TodoScreenPermissionSupport.canTaskReorderInView(this.minecraft, currentProject, viewMode.name())
                || taskManager == null
                || reorderedActiveTasks == null
                || reorderedActiveTasks.size() < 2) {
            return;
        }
        List<String> orderedTaskIds = reorderedActiveTasks.stream()
                .filter(Objects::nonNull)
                .map(Task::getId)
                .filter(Objects::nonNull)
                .toList();
        if (orderedTaskIds.size() < 2) {
            return;
        }
        Task anchorTask = reorderedActiveTasks.get(0);
        boolean changed;
        if (anchorTask != null && anchorTask.isSubtask()) {
            // 子任务拖拽前，列表控件已经按同父级顺序更新了同一批任务对象；
            // 这里仍调用管理器以统一收口父内顺序，但不再依赖返回值判断是否发生变化。
            taskManager.reorderSubtasks(anchorTask.getParentTaskId(), orderedTaskIds);
            changed = true;
        } else {
            changed = taskManager.reorderTasks(orderedTaskIds);
        }
        if (!changed) {
            return;
        }
        if (selectedTask != null && selectedTask.getId() != null) {
            Task refreshedSelectedTask = taskManager.getTask(selectedTask.getId());
            if (refreshedSelectedTask != null) {
                selectedTask = refreshedSelectedTask;
            }
        }
        markUnsaved();
        filterTasks();
    }

    /**
     * 在当前视图内按优先级重新归位未完成任务，同时保留同优先级内部顺序。
     *
     * @param updatedTask 刚刚修改优先级的任务
     */
    private void reorderCurrentViewActiveTasksAfterPriorityChange(Task updatedTask) {
        if (updatedTask == null || updatedTask.getId() == null || taskManager == null) {
            return;
        }
        List<Task> scopedActiveTasks = getCurrentViewActiveTasksForOrdering();
        List<String> orderedTaskIds =
                TodoScreenTaskSupport.reorderCurrentViewActiveTaskIdsAfterPriorityChange(scopedActiveTasks, updatedTask);
        if (orderedTaskIds.isEmpty()) {
            return;
        }
        taskManager.reorderTasks(orderedTaskIds);
    }

    /**
     * 返回当前视图内参与排序的未完成任务，并保持任务管理器中的稳定顺序。
     *
     * @return 当前视图范围内的未完成任务
     */
    private List<Task> getCurrentViewActiveTasksForOrdering() {
        if (taskManager == null) {
            return List.of();
        }
        return TodoScreenTaskSupport.applyAssignedFilterForView(
                taskManager.getIncompleteTasks(),
                currentProject == null ? null : currentProject.getId(),
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft)
        );
    }

    /**
     * 根据当前视图和权限状态同步任务列表的拖拽排序能力。
     */
    private void syncTaskListReorderState() {
        if (taskListWidget == null) {
            return;
        }
        taskListWidget.setTaskReorderEnabled(TodoScreenPermissionSupport.canTaskReorderInView(
                this.minecraft,
                currentProject,
                viewMode.name()
        ));
    }

    /**
     * 返回个人任务是否存在尚未保存的界面改动。
     *
     * @return 个人任务存在未保存改动时返回 true
     */
    public static boolean hasPersonalUnsavedChanges() {
        return personalHasUnsavedChanges;
    }

    /**
     * 基于当前选中任务 ID 回查任务管理器中的最新对象，避免网络同步后引用过期。
     *
     * @return 可用于后续修改的最新选中任务；不存在时返回 {@code null}
     */
    private Task resolveCurrentSelectedTaskForMutation() {
        if (selectedTask == null) {
            return null;
        }
        Task latest = resolveTaskForMutation(selectedTask);
        if (latest == null) {
            clearSelectedTask();
            return null;
        }
        selectedTask = latest;
        return latest;
    }

    /**
     * 基于任务 ID 回查任务管理器中的最新任务对象。
     *
     * @param task 候选任务对象
     * @return 最新任务对象；无法回查时返回 {@code null}
     */
    private Task resolveTaskForMutation(Task task) {
        if (task == null) {
            return null;
        }
        return resolveTaskByIdForMutation(task.getId());
    }

    /**
     * 基于任务 ID 回查任务管理器中的最新任务对象。
     *
     * @param taskId 任务 ID
     * @return 最新任务对象；无法回查时返回 {@code null}
     */
    private Task resolveTaskByIdForMutation(String taskId) {
        if (taskId == null || taskId.isBlank() || taskManager == null) {
            return null;
        }
        return taskManager.getTask(taskId);
    }

    /**
     * 收集目标父任务当前快照中的直属子任务。
     *
     * @param parentTask 父任务
     * @param managedTasks 当前任务快照
     * @return 按当前顺序返回直属子任务列表
     */
    private List<Task> getDirectSubtasksForBatchAction(Task parentTask, List<Task> managedTasks) {
        if (parentTask == null || managedTasks == null || managedTasks.isEmpty()) {
            return List.of();
        }
        String parentTaskId = parentTask.getId();
        if (parentTaskId == null || parentTaskId.isBlank()) {
            return List.of();
        }
        List<Task> directSubtasks = new ArrayList<>();
        for (Task candidate : managedTasks) {
            if (candidate != null && candidate.isSubtask() && Objects.equals(parentTaskId, candidate.getParentTaskId())) {
                directSubtasks.add(candidate);
            }
        }
        return directSubtasks;
    }

    /**
     * 将领取人应用到任务或其尚未分配的直属子任务。
     *
     * @param task 目标任务
     * @param managedTasks 当前任务快照
     * @param assigneeUuid 领取人 UUID
     * @param assigneeName 领取人名称
     * @return 实际写入领取人的任务数量
     */
    private int assignPendingTaskTargets(Task task, List<Task> managedTasks, String assigneeUuid, String assigneeName) {
        List<Task> directSubtasks = getDirectSubtasksForBatchAction(task, managedTasks);
        if (directSubtasks.isEmpty()) {
            task.setAssigneeUuid(assigneeUuid);
            task.setAssigneeName(assigneeName);
            return 1;
        }
        int changedCount = 0;
        for (Task subtask : directSubtasks) {
            if (TaskAssignmentSupport.isDirectlyAssigned(subtask)) {
                continue;
            }
            subtask.setAssigneeUuid(assigneeUuid);
            subtask.setAssigneeName(assigneeName);
            changedCount++;
        }
        return changedCount;
    }

    /**
     * 清空任务或其直属子任务上的领取信息。
     * 在"我的"视图下仅清空属于当前玩家的子任务，不影响其他玩家领取的子任务。
     *
     * @param task 目标任务
     * @param managedTasks 当前任务快照
     * @param viewModeName 当前视图模式名称
     * @param currentPlayerUuid 当前玩家 UUID
     * @return 实际清空领取人的任务数量
     */
    private int clearAssignedTaskTargets(Task task, List<Task> managedTasks, String viewModeName, String currentPlayerUuid) {
        List<Task> directSubtasks = getDirectSubtasksForBatchAction(task, managedTasks);
        if (directSubtasks.isEmpty()) {
            boolean wasAssigned = TaskAssignmentSupport.isDirectlyAssigned(task);
            task.setAssigneeUuid(null);
            task.setAssigneeName(null);
            return wasAssigned ? 1 : 0;
        }
        boolean scopeToPlayer = "TEAM_ASSIGNED".equals(viewModeName)
                && currentPlayerUuid != null
                && !currentPlayerUuid.isEmpty();
        int changedCount = 0;
        for (Task subtask : directSubtasks) {
            if (!TaskAssignmentSupport.isDirectlyAssigned(subtask)) {
                continue;
            }
            if (scopeToPlayer && !currentPlayerUuid.equals(subtask.getAssigneeUuid())) {
                continue;
            }
            subtask.setAssigneeUuid(null);
            subtask.setAssigneeName(null);
            changedCount++;
        }
        return changedCount;
    }

    private void onClaimTask() {
        Task taskToClaim = resolveCurrentSelectedTaskForMutation();
        if (taskToClaim == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        List<Task> managedTasks = taskManager == null ? List.of() : taskManager.getAllTasks();
        String validationKey = TodoScreenPermissionSupport.validateClaimTask(
                taskToClaim,
                managedTasks,
                this.minecraft,
                viewMode.name()
        );
        if (validationKey != null) {
            addNotification(Component.translatable(validationKey).getString());
            return;
        }
        String uuid = this.minecraft.player.getUUID().toString();
        int changedCount = assignPendingTaskTargets(
                taskToClaim,
                managedTasks,
                uuid,
                this.minecraft.player.getName().getString()
        );
        if (changedCount <= 0) {
            addNotification(Component.translatable("message.todolist.already_assigned").getString());
            filterTasks();
            return;
        }
        addNotification(Component.translatable("message.todolist.assigned_to_me").getString());
        markUnsaved();
        filterTasks();
        persistCurrentViewTasksInBackground("claim");
    }

    /**
     * 按当前空间、搜索词和项目作用域刷新左侧项目列表。
     */
    private void updateProjectList() {
        if (projectListWidget == null) return;
        List<Project> visibleProjects = TodoScreenProjectSidebarSupport.buildVisibleProjectsForSidebar(
                projectManager,
                projectScopeFilter,
                projectSearchQuery,
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft)
        );
        projectListWidget.setProjects(visibleProjects);
        projectListWidget.setProjectTaskCounts(TodoScreenProjectSidebarSupport.buildSidebarProjectTaskCounts(
                visibleProjects,
                projectScopeFilter,
                personalTaskManager,
                teamTaskManager,
                this.minecraft
        ));
        projectListWidget.setSelectedProject(currentProject);
        updateProjectActionButtons();
    }

    private void updateProjectActionButtons() {
        if (editProjectBtn == null || deleteProjectBtn == null || applyJoinProjectBtn == null) {
            return;
        }
        int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
        Component joinMessage = Component.translatable(
                TodoScreenUiMetricsSupport.useCompactSidebarBottomButtons(responsiveTier, sidebarWidth)
                        ? "gui.todolist.project.join.compact"
                        : "gui.todolist.project.join.apply"
        );
        Component deleteMessage = Component.translatable("gui.todolist.delete");
        deleteProjectBtn.setMessage(deleteMessage);
        applyJoinProjectBtn.setMessage(joinMessage);
        Role role = TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject);
        boolean projectMember = TodoScreenPermissionSupport.isCurrentPlayerProjectMember(this.minecraft, currentProject);
        TodoScreenProjectActionSupport.ProjectActionState state = TodoScreenProjectActionSupport.resolve(
                currentProject,
                role,
                projectMember,
                TodoScreenProjectActionSupport.canDeleteCurrentProject(
                        currentProject,
                        TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                        role
                )
        );
        editProjectBtn.active = state.editActive;
        editProjectBtn.setMessage(Component.translatable(state.editMessageKey));
        deleteProjectBtn.visible = state.deleteVisible;
        deleteProjectBtn.active = state.deleteActive;
        applyJoinProjectBtn.visible = state.joinVisible;
        applyJoinProjectBtn.active = state.joinActive;
        boolean sidebarVisible = layoutMetrics == null || layoutMetrics.sidebarVisible;
        TodoScreenProjectActionSupport.applySidebarVisibility(
                sidebarVisible,
                addProjectBtn,
                editProjectBtn,
                deleteProjectBtn,
                applyJoinProjectBtn
        );
    }
    
    private void onAbandonTask() {
        Task taskToAbandon = resolveCurrentSelectedTaskForMutation();
        if (taskToAbandon == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        List<Task> managedTasks = taskManager == null ? List.of() : taskManager.getAllTasks();
        String validationKey = TodoScreenPermissionSupport.validateAbandonTask(
                taskToAbandon,
                managedTasks,
                this.minecraft,
                currentProject,
                viewMode.name()
        );
        if (validationKey != null) {
            addNotification(Component.translatable(validationKey).getString());
            return;
        }
        int changedCount = clearAssignedTaskTargets(taskToAbandon, managedTasks, viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft));
        if (changedCount <= 0) {
            filterTasks();
            return;
        }
        addNotification(Component.translatable("message.todolist.abandoned_task").getString());
        markUnsaved();
        filterTasks();
        persistCurrentViewTasksInBackground("abandon");
    }

    private void onAssignOthers() {
        Task taskToAssign = resolveCurrentSelectedTaskForMutation();
        if (taskToAssign == null || this.minecraft == null) {
            return;
        }
        List<Task> managedTasks = taskManager == null ? List.of() : taskManager.getAllTasks();
        if (viewMode == ViewMode.PERSONAL) {
            addNotification(Component.translatable("message.todolist.assign_only_team").getString());
            return;
        }
        if (TaskAssignmentSupport.areAllDirectSubtasksAssigned(taskToAssign, managedTasks)) {
            addNotification(Component.translatable("message.todolist.already_assigned").getString());
            return;
        }
        if (!TodoScreenPermissionSupport.canTaskOperationInView(
                Operation.ASSIGN_OTHERS,
                taskToAssign,
                managedTasks,
                this.minecraft,
                currentProject,
                viewMode.name()
        )) {
            addNotification(Component.translatable("message.todolist.no_permission_toggle_team").getString());
            return;
        }
        this.minecraft.setScreen(createAssignPlayerScreen(taskToAssign));
    }

    /**
     * Create the member assignment dialog for the given task.
     *
     * @param task target task
     * @return assignment dialog screen
     */
    private Screen createAssignPlayerScreen(Task task) {
        String taskId = task == null ? null : task.getId();
        return new AssignPlayerScreen(
                this,
                task,
                () -> currentProject,
                (project, memberUuid) -> TodoScreenMemberSupport.resolveProjectMemberDisplayName(this.minecraft, project, memberUuid),
                (memberUuid, memberName) -> applyAssignResult(taskId, memberUuid, memberName)
        );
    }

    /**
     * Apply the selected member assignment to the task.
     *
     * @param taskId target task id
     * @param memberUuid selected member UUID
     * @param memberName selected member display name
     */
    private void applyAssignResult(String taskId, String memberUuid, String memberName) {
        Task taskToAssign = resolveTaskByIdForMutation(taskId);
        if (taskToAssign == null) {
            TodoConstants.LOGGER.warn("Skip assign operation because task {} is missing in current manager snapshot", taskId);
            addNotification(Component.translatable("message.todolist.save_failed").getString());
            filterTasks();
            return;
        }
        int changedCount = assignPendingTaskTargets(taskToAssign, taskManager == null ? List.of() : taskManager.getAllTasks(), memberUuid, memberName);
        if (changedCount <= 0) {
            addNotification(Component.translatable("message.todolist.already_assigned").getString());
            filterTasks();
            return;
        }
        if (selectedTask != null && selectedTask.getId() != null && selectedTask.getId().equals(taskToAssign.getId())) {
            selectedTask = taskToAssign;
        }
        addNotification(Component.translatable("message.todolist.assigned_to_player", memberName).getString());
        markUnsaved();
        filterTasks();
        persistDirtyTasksInBackground("assign_others", false, false, true);
    }

    private void filterTasks() {
        if (applyH2GuiTaskFilter()) {
            if (selectedTask != null && !isSelectedTaskValid()) {
                clearSelectedTask();
            }
            updateViewButtonsState();
            return;
        }
        List<Task> result = taskManager == null ? new ArrayList<>() : taskManager.getIncompleteTasks();
        result = TodoScreenTaskSupport.applyPriorityFilterToTasks(currentPriorityFilter, result);

        baseFilteredTasks = TodoScreenTaskSupport.applyAssignedFilterForView(
                result,
                currentProject == null ? null : currentProject.getId(),
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft)
        );
        applySearchFilter();
        if (selectedTask != null && !isSelectedTaskValid()) {
            clearSelectedTask();
        }
        updateViewButtonsState();
    }

    private void applySearchFilter() {
        if (baseFilteredTasks == null) {
            baseFilteredTasks = new ArrayList<>();
        }
        if (applyH2GuiSearchFilter()) {
            return;
        }
        filteredTasks = TodoScreenTaskSupport.buildVisibleTasksForCurrentView(
                taskManager,
                currentProject == null ? null : currentProject.getId(),
                false,
                currentPriorityFilter,
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                searchQuery
        );
        if (taskListWidget != null) {
            taskListWidget.setForcedExpandedParentTaskIds(resolveForcedExpandedParentTaskIds());
            taskListWidget.setTaskReorderEnabled(TodoScreenPermissionSupport.canTaskReorderInView(
                    this.minecraft,
                    currentProject,
                    viewMode.name()
            ));
            taskListWidget.setSections(buildTaskPaneSections());
        }
    }

    /**
     * 构建任务面板的分组模型，包含未完成任务和可折叠的已完成任务。
     *
     * @return 任务列表需要渲染的分组模型集合
     */
    private List<TaskListWidget.SectionModel> buildTaskPaneSections() {
        List<Task> activeTasks = filteredTasks == null ? List.of() : List.copyOf(filteredTasks);
        int activeTotalCount = activeTasks.size();
        int completedTotalCount = -1;
        List<Task> completedTasks;
        if (!completedExpanded && (selectedTask == null || !selectedTask.isCompleted())) {
            Integer h2CompletedTotalCount = queryH2GuiTaskCount(true, searchQuery);
            completedTotalCount = h2CompletedTotalCount == null ? -1 : h2CompletedTotalCount;
            completedTasks = h2CompletedTotalCount == null ? null : List.of();
        } else {
            completedTasks = queryH2GuiTasks(true, searchQuery);
        }
        if (completedTasks == null) {
            completedTasks = TodoScreenTaskSupport.buildCompletedTasksForCurrentView(
                    taskManager,
                    currentProject == null ? null : currentProject.getId(),
                    currentPriorityFilter,
                    viewMode.name(),
                    TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                    searchQuery
            );
        }
        if (completedTotalCount < 0) {
            Integer h2CompletedTotalCount = queryH2GuiTaskCount(true, searchQuery);
            completedTotalCount = h2CompletedTotalCount == null ? completedTasks.size() : h2CompletedTotalCount;
        }
        List<Task> scopedProjectTasks = buildScopedProjectTasksForTaskPane();
        // 收集未完成区任务的 ID 集合，用于已完成区去重
        java.util.Set<String> activeTaskIds = new java.util.HashSet<>();
        for (Task t : activeTasks) {
            if (t != null && t.getId() != null) {
                activeTaskIds.add(t.getId());
            }
        }
        // 已完成区的子任务只展示已完成的，且排除其父任务已在未完成区展示的子任务
        List<Task> completedScopedTasks = new ArrayList<>();
        for (Task t : scopedProjectTasks) {
            if (t == null) {
                continue;
            }
            if (t.isSubtask() && (!t.isCompleted() || activeTaskIds.contains(t.getParentTaskId()))) {
                continue;
            }
            completedScopedTasks.add(t);
        }
        // 去重：如果父任务已出现在未完成区（仍有未完成子任务），则不从已完成区重复展示
        List<Task> dedupedCompletedTasks = new ArrayList<>();
        for (Task t : completedTasks) {
            if (t != null && !activeTaskIds.contains(t.getId())) {
                dedupedCompletedTasks.add(t);
            }
        }
        return TodoScreenTaskSupport.buildTaskPaneSections(
                TodoScreenTaskSupport.buildSectionTasksWithDirectChildren(activeTasks, scopedProjectTasks),
                activeTotalCount,
                TodoScreenTaskSupport.buildSectionTasksWithDirectChildren(dedupedCompletedTasks, completedScopedTasks),
                dedupedCompletedTasks.size() == 0 ? 0 : completedTotalCount,
                activeExpanded,
                completedExpanded
        );
    }

    /**
     * 在 H2 后端下使用 SQL 查询刷新 GUI 任务筛选结果。
     *
     * @return 成功接管筛选时返回 true，失败或非 H2 后端返回 false
     */
    private boolean applyH2GuiTaskFilter() {
        List<Task> baseTasks = queryH2GuiTasks(false, "");
        if (baseTasks == null) {
            return false;
        }
        List<Task> searchedTasks = queryH2GuiTasks(false, searchQuery);
        if (searchedTasks == null) {
            return false;
        }
        baseFilteredTasks = baseTasks;
        filteredTasks = searchedTasks;
        updateTaskListWidgetSections();
        return true;
    }

    /**
     * 在 H2 后端下使用 SQL 查询刷新当前搜索结果。
     *
     * @return 成功接管搜索时返回 true，失败或非 H2 后端返回 false
     */
    private boolean applyH2GuiSearchFilter() {
        List<Task> searchedTasks = queryH2GuiTasks(false, searchQuery);
        if (searchedTasks == null) {
            return false;
        }
        filteredTasks = searchedTasks;
        updateTaskListWidgetSections();
        return true;
    }

    /**
     * 刷新任务列表控件的重排状态和分组模型。
     */
    private void updateTaskListWidgetSections() {
        if (taskListWidget == null) {
            return;
        }
        taskListWidget.setForcedExpandedParentTaskIds(resolveForcedExpandedParentTaskIds());
        taskListWidget.setTaskReorderEnabled(TodoScreenPermissionSupport.canTaskReorderInView(
                this.minecraft,
                currentProject,
                viewMode.name()
        ));
        taskListWidget.setSections(buildTaskPaneSections());
    }

    /**
     * 构建任务面板层级展示所需的项目任务范围。
     * 该范围保留当前项目与视图下的全部任务，用于补齐父任务的直属子任务。
     *
     * @return 任务面板层级展示范围
     */
    private List<Task> buildScopedProjectTasksForTaskPane() {
        if (taskManager == null || currentProject == null || currentProject.getId() == null) {
            return List.of();
        }
        return TodoScreenTaskSupport.applyAssignedFilterForView(
                taskManager.getAllTasks(),
                currentProject.getId(),
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft)
        );
    }

    /**
     * 解析当前需要在列表中被强制展开的父任务集合。
     * 搜索命中子任务或当前选中子任务时，会临时展开对应父任务，保证上下文可见。
     *
     * @return 当前需要强制展开的父任务 ID 集合
     */
    private Set<String> resolveForcedExpandedParentTaskIds() {
        Set<String> parentTaskIds = new LinkedHashSet<>();
        List<Task> scopedProjectTasks = buildScopedProjectTasksForTaskPane();
        List<Task> activeTasks = filteredTasks == null ? List.of() : List.copyOf(filteredTasks);
        List<Task> completedTasks = TodoScreenTaskSupport.buildCompletedTasksForCurrentView(
                taskManager,
                currentProject == null ? null : currentProject.getId(),
                currentPriorityFilter,
                viewMode.name(),
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                searchQuery
        );
        parentTaskIds.addAll(TodoScreenTaskSupport.collectAutoExpandedParentTaskIdsForSearch(
                searchQuery,
                activeTasks,
                scopedProjectTasks
        ));
        parentTaskIds.addAll(TodoScreenTaskSupport.collectAutoExpandedParentTaskIdsForSearch(
                searchQuery,
                completedTasks,
                scopedProjectTasks
        ));
        if (selectedTask != null && selectedTask.isSubtask() && selectedTask.getParentTaskId() != null) {
            parentTaskIds.add(selectedTask.getParentTaskId());
        }
        // 已完成区中补充的未完成父任务（含有已完成子任务）应自动展开，
        // 让玩家能直接看到并操作已完成的子任务。
        String currentPlayerUuid = TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft);
        if (currentPlayerUuid != null && !currentPlayerUuid.isEmpty() && taskManager != null) {
            for (Task task : completedTasks) {
                if (task == null || task.isSubtask() || task.isCompleted()) {
                    continue;
                }
                if (TaskAssignmentSupport.hasAnyCompletedDirectSubtaskAssignedToPlayer(task, taskManager.getAllTasks(), currentPlayerUuid)) {
                    parentTaskIds.add(task.getId());
                }
            }
        }
        return parentTaskIds;
    }

    /**
     * 通过 H2 SQL 查询当前 GUI 任务 ID，并映射回 TaskManager 中的任务对象。
     *
     * @param completed 是否查询已完成任务
     * @param rawSearchQuery 搜索关键词
     * @return 任务列表；非 H2 后端或查询失败时返回 null
     */
    private List<Task> queryH2GuiTasks(boolean completed, String rawSearchQuery) {
        if (!shouldUseSynchronousH2GuiQueries()
                || !StorageBackendFactory.isH2Selected()
                || taskManager == null
                || currentProject == null
                || currentProject.getId() == null) {
            return null;
        }
        try {
            H2GuiBucket bucket = resolveH2GuiBucket();
            List<String> ids = new H2TaskQueryService().queryGuiTaskIds(
                    bucket.bucketType,
                    bucket.ownerUuid,
                    currentProject.getId(),
                    completed,
                    resolveCurrentPriorityName(),
                    resolveH2GuiAssigneeFilter(),
                    TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                    rawSearchQuery
            );
            return mapTaskIdsToCurrentManager(ids);
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to query H2 GUI task list, falling back to memory filtering", exception);
            return null;
        }
    }

    /**
     * 通过 H2 SQL 统计当前 GUI 任务过滤条件下的任务总数。
     *
     * @param completed 是否统计已完成任务
     * @param rawSearchQuery 搜索关键词
     * @return 匹配总数；非 H2 后端或查询失败时返回 null
     */
    private Integer queryH2GuiTaskCount(boolean completed, String rawSearchQuery) {
        if (!shouldUseSynchronousH2GuiQueries()
                || !StorageBackendFactory.isH2Selected()
                || taskManager == null
                || currentProject == null
                || currentProject.getId() == null) {
            return null;
        }
        try {
            H2GuiBucket bucket = resolveH2GuiBucket();
            return new H2TaskQueryService().countGuiTaskIds(
                    bucket.bucketType,
                    bucket.ownerUuid,
                    currentProject.getId(),
                    completed,
                    resolveCurrentPriorityName(),
                    resolveH2GuiAssigneeFilter(),
                    TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                    rawSearchQuery
            );
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to count H2 GUI task list, falling back to memory filtering", exception);
            return null;
        }
    }

    /**
     * 判断 GUI 是否允许在界面交互路径执行同步 H2 查询。
     *
     * @return 当前固定返回 false，优先使用内存任务管理器避免打开 GUI 和筛选时卡顿
     */
    private boolean shouldUseSynchronousH2GuiQueries() {
        return false;
    }

    /**
     * 将 H2 查询返回的任务 ID 映射为当前 TaskManager 中的任务对象。
     *
     * @param taskIds 任务 ID 列表
     * @return 当前任务管理器中的任务对象列表
     */
    private List<Task> mapTaskIdsToCurrentManager(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty() || taskManager == null) {
            return List.of();
        }
        List<Task> tasks = new ArrayList<>();
        for (String taskId : taskIds) {
            Task task = taskManager.getTask(taskId);
            if (task != null) {
                tasks.add(task);
            }
        }
        return tasks;
    }

    /**
     * 解析当前 GUI 视图对应的 H2 任务桶。
     *
     * @return H2 GUI 查询桶
     */
    private H2GuiBucket resolveH2GuiBucket() {
        if (viewMode != ViewMode.PERSONAL) {
            return new H2GuiBucket(H2TaskStore.TEAM_BUCKET, H2TaskStore.TEAM_OWNER);
        }
        if (this.minecraft != null
                && this.minecraft.player != null
                && ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(this.minecraft)) {
            UUID storagePlayerUuid = ClientTaskStorageHelper.resolveStoragePlayerUuid(this.minecraft);
            return new H2GuiBucket(H2TaskStore.PLAYER_PERSONAL_BUCKET, storagePlayerUuid == null ? "" : storagePlayerUuid.toString());
        }
        return new H2GuiBucket(H2TaskStore.LOCAL_PERSONAL_BUCKET, H2TaskStore.LOCAL_OWNER);
    }

    /**
     * 解析当前 GUI 优先级筛选对应的 H2 字段值。
     *
     * @return 优先级名称；全部优先级时返回空字符串
     */
    private String resolveCurrentPriorityName() {
        return switch (currentPriorityFilter) {
            case 1 -> Task.Priority.HIGH.name();
            case 2 -> Task.Priority.MEDIUM.name();
            case 3 -> Task.Priority.LOW.name();
            default -> "";
        };
    }

    /**
     * 解析当前 GUI 团队视图对应的 H2 指派过滤模式。
     *
     * @return H2 指派过滤模式
     */
    private H2TaskQueryService.HudAssigneeFilter resolveH2GuiAssigneeFilter() {
        if (viewMode == ViewMode.TEAM_UNASSIGNED) {
            return H2TaskQueryService.HudAssigneeFilter.UNASSIGNED;
        }
        if (viewMode == ViewMode.TEAM_ASSIGNED) {
            return H2TaskQueryService.HudAssigneeFilter.ASSIGNED_TO_PLAYER;
        }
        return H2TaskQueryService.HudAssigneeFilter.ANY;
    }

    /**
     * H2GuiBucket 描述 GUI 查询当前使用的任务桶。
     */
    private static final class H2GuiBucket {
        private final String bucketType;
        private final String ownerUuid;

        /**
         * 创建 GUI 查询桶。
         *
         * @param bucketType 桶类型
         * @param ownerUuid 桶拥有者
         */
        private H2GuiBucket(String bucketType, String ownerUuid) {
            this.bucketType = bucketType;
            this.ownerUuid = ownerUuid;
        }
    }

    private void addNotification(String text) {
        long now = System.currentTimeMillis();
        notifications.add(TodoScreenNotificationSupport.createNotification(
                text,
                now,
                TodoScreenNotificationSupport.DEFAULT_DURATION_MS
        ));
    }

    private void switchView(ViewMode mode) {
        if (mode != null && mode != this.viewMode) {
            discardSelectedEmptyPlaceholderSubtask();
            triggerAutoSaveIfNeeded("auto_switch_view", false);
        }
        this.viewMode = mode;
        syncViewStateForCurrentProject();
        clearSelectedTask();
        ModConfig config = ModConfig.getInstance();
        if (this.viewMode == ViewMode.PERSONAL) {
            config.setHudDefaultView("PERSONAL");
        } else {
            config.setHudDefaultView(this.viewMode.name());
        }
        updateViewButtonsState();
        rebuildUI();
    }

    private void syncHudViewForProject(Project project) {
        ModConfig config = ModConfig.getInstance();
        TodoScreenViewModeSupport.HudViewState state = TodoScreenViewModeSupport.resolveHudViewState(
                project,
                teamProjectsEnabled,
                config.getHudDefaultView()
        );
        currentSpaceMode = SpaceMode.valueOf(state.spaceModeName);
        currentTaskViewOption = TaskViewOption.valueOf(state.taskViewOptionName);
        viewMode = ViewMode.valueOf(state.legacyViewModeName);
        config.setHudDefaultView(state.hudDefaultViewToSave);
    }
    
    private void updateViewButtonsState() {
        TodoScreenSidebarViewButtonLayoutSupport.apply(
                myViewButton,
                unassignedViewButton,
                allViewButton,
                layoutMetrics == null ? null : layoutMetrics.sidebarBounds,
                responsiveTier,
                currentSpaceMode.name()
        );
        if (personalSpaceButton != null) {
            personalSpaceButton.active = currentSpaceMode != SpaceMode.PERSONAL;
        }
        if (teamSpaceButton != null) {
            teamSpaceButton.active = teamProjectsEnabled && currentSpaceMode != SpaceMode.TEAM;
        }
        if (myViewButton != null) {
            myViewButton.setMessage(Component.literal("\u6211\u7684"));
            myViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.MY;
        }
        if (unassignedViewButton != null) {
            int sidebarWidth = layoutMetrics == null ? 0 : layoutMetrics.sidebarBounds.width;
            unassignedViewButton.setMessage(TodoScreenUiMetricsSupport.getUnassignedTeamViewText(sidebarWidth));
            unassignedViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.UNASSIGNED;
        }
        if (allViewButton != null) {
            allViewButton.setMessage(TodoScreenUiMetricsSupport.getAllTeamViewText());
            allViewButton.active = currentSpaceMode == SpaceMode.TEAM && currentTaskViewOption != TaskViewOption.ALL;
        }
    }
    
    private void switchProject(Project project) {
        if ((project == null && currentProject != null)
                || (project != null && (currentProject == null || !project.getId().equals(currentProject.getId())))) {
            discardSelectedEmptyPlaceholderSubtask();
            triggerAutoSaveIfNeeded("auto_switch_project", false);
        }
        if (projectListWidget != null) {
            savedProjectListScrollOffset = projectListWidget.getScrollOffset();
        }
        this.selectedTask = null;
        this.detailOverlayVisible = false;
        this.sidebarOverlayVisible = false;
        projectSearchPrefixDropdownOpen = false;
        this.currentProject = project;
        String[] preferredIds = TodoScreenProjectPreferenceSupport.rememberSelectedProject(
                project,
                preferredPersonalProjectId,
                preferredTeamProjectId
        );
        preferredPersonalProjectId = preferredIds[0];
        preferredTeamProjectId = preferredIds[1];

        if (project == null) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                    currentSpaceMode.name(),
                    currentTaskViewOption.name()
            ));
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            syncHudViewForProject(null);
            rebuildUI();
            return;
        }

        if (!teamProjectsEnabled && project.getScope() == Project.Scope.TEAM) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        projectScopeFilter = project.getScope();
        ClientBridge.ops().setActiveProjectId(project.getId());
        ClientBridge.ops().sendSetActiveProjectId(project.getId());
        ClientBridge.saveLastActiveProjectId(project.getId());
        
        if (project.getScope() == Project.Scope.PERSONAL) {
            this.taskManager = this.personalTaskManager;
            currentSpaceMode = SpaceMode.PERSONAL;
            currentTaskViewOption = TaskViewOption.MY;
            viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                    currentSpaceMode.name(),
                    currentTaskViewOption.name()
            ));
        } else {
            this.taskManager = this.teamTaskManager;
            currentSpaceMode = SpaceMode.TEAM;
            if (currentTaskViewOption == TaskViewOption.MY && viewMode == ViewMode.PERSONAL) {
                currentTaskViewOption = TaskViewOption.UNASSIGNED;
            }
            viewMode = ViewMode.valueOf(TodoScreenViewModeSupport.resolveLegacyViewModeName(
                    currentSpaceMode.name(),
                    currentTaskViewOption.name()
            ));
        }
        syncHudViewForProject(project);
        
        rebuildUI();
    }

    /**
     * 判断当前是否启用了 GUI 编辑自动保存。
     *
     * @return true 表示已启用自动保存
     */
    private boolean isGuiAutoSaveEnabled() {
        return ModConfig.getInstance().isAutoSave();
    }

    /**
     * 在开启自动保存且存在脏任务时，提交一次后台保存。
     *
     * @param operationName 自动保存操作名
     * @param closeAfterSave 保存完成后是否关闭当前界面
     * @return 本次是否实际触发了自动保存
     */
    private boolean triggerAutoSaveIfNeeded(String operationName, boolean closeAfterSave) {
        if (!isGuiAutoSaveEnabled() || (!personalHasUnsavedChanges && !teamHasUnsavedChanges)) {
            return false;
        }
        finishDetailTitleEditing();
        persistDirtyTasksInBackground(operationName, closeAfterSave, personalHasUnsavedChanges, teamHasUnsavedChanges);
        return true;
    }

    private void onAddProject() {
        Project.Scope defaultScope = Project.Scope.PERSONAL;
        if (currentProject != null) {
            defaultScope = currentProject.getScope();
        } else if (projectScopeFilter != null) {
            defaultScope = projectScopeFilter;
        }
        if (defaultScope == Project.Scope.TEAM && !ClientBridge.ops().isTeamProjectsEnabled()) {
            defaultScope = Project.Scope.PERSONAL;
        }
        minecraft.setScreen(new AddProjectScreen(this, defaultScope));
    }
    
    private void onProjectSettings() {
        if (currentProject == null) return;
        if (TodoScreenProjectActionSupport.isTeamProjectBlocked(currentProject, teamProjectsEnabled)) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        minecraft.setScreen(new ProjectSettingsScreen(this, currentProject));
    }

    private void onApplyJoinProject() {
        if (currentProject == null) return;
        if (TodoScreenProjectActionSupport.isTeamProjectBlocked(currentProject, teamProjectsEnabled)) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            return;
        }
        if (TodoScreenPermissionSupport.isCurrentPlayerProjectMember(this.minecraft, currentProject)) {
            return;
        }
        ClientBridge.ops().sendRequestJoinProject(currentProject.getId());
        addNotification(Component.translatable("message.todolist.project.join.sent").getString());
    }

    private void onProjectDelete() {
        if (currentProject == null) return;
        if (TodoScreenProjectActionSupport.isTeamProjectBlocked(currentProject, teamProjectsEnabled)) {
            addNotification(Component.translatable("message.todolist.team_disabled").getString());
            return;
        }
        if (!TodoScreenProjectActionSupport.canDeleteCurrentProject(
                currentProject,
                TodoScreenPermissionSupport.getCurrentPlayerUuid(this.minecraft),
                TodoScreenPermissionSupport.getCurrentRole(this.minecraft, currentProject)
        )) {
            addNotification(Component.translatable("message.todolist.no_permission_delete_project").getString());
            return;
        }
        String projectName = ProjectNameFormatter.toDisplayText(currentProject).getString();
        Component message = Component.translatable("gui.todolist.project.delete_confirm.message", projectName);
        minecraft.setScreen(new ConfirmDeleteProjectScreen(this, message, () -> {
            ClientBridge.ops().sendDeleteProject(currentProject.getId());
        }));
    }
}
