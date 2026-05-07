package com.todolist.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.storage.H2TaskQueryService;
import com.todolist.storage.H2TaskStore;
import com.todolist.storage.StorageBackendFactory;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 负责在 HUD 上渲染待办任务列表。
 */
public class TodoHudRenderer {
    private static final long HUD_MODEL_REFRESH_INTERVAL_MS = 1000L;

    /**
     * HUD 视图模式，和配置项中的字符串一一对应。
     */
    private enum HudViewMode {
        PERSONAL,
        TEAM_UNASSIGNED,
        TEAM_ALL,
        TEAM_ASSIGNED
    }

    private final Minecraft client;
    private boolean expanded;
    private long lastPersonalReloadMs;
    private long lastTeamReloadMs;
    private long cachedPersonalLastSavedMs = -1L;
    private long lastHudModelRefreshMs;
    private boolean hudModelCacheInitialized;
    private int cachedLayoutHudWidth = -1;
    private double cachedGuiScale = -1D;
    private boolean cachedPersonalTasksInitialized;
    private boolean cachedPersonalTasksFromGui;
    private List<Task> cachedPersonalTasks = new ArrayList<>();
    private List<Task> cachedTeamTasks = new ArrayList<>();
    private List<Task> cachedPendingTasks = new ArrayList<>();
    private List<Task> cachedDoneTasks = new ArrayList<>();
    private int cachedPendingTotalCount;
    private int cachedDoneTotalCount;
    private final Map<String, RowRenderCache> rowRenderCacheByTaskId = new HashMap<>();
    private HudViewMode cachedHudViewMode = HudViewMode.PERSONAL;
    private Project.Scope cachedHudScope = Project.Scope.PERSONAL;
    private String cachedProjectSourceMode = "";
    private String cachedActiveProjectId = "";
    private String cachedPlayerUuid = "";

    // 缓存常用组件
    private static final Component CHECKBOX_CHECKED = Component.literal("☑").withStyle(ChatFormatting.DARK_GREEN);
    private static final Component CHECKBOX_UNCHECKED = Component.literal("☐").withStyle(ChatFormatting.WHITE);
    private static final Component SEPARATOR_COMPLETED = Component.translatable("hud.todolist.separator.completed");
    private static final Component ELLIPSIS = Component.literal("...");
    private static final float HUD_LABEL_SCALE = 0.85F;
    private static final int HUD_LABEL_MAX_CHARS = 8;
    private static final int HUD_PRIORITY_BLOCK_WIDTH = 4;
    private static final int HUD_PRIORITY_BLOCK_GAP = 4;
    
    // 缓存视图标签
    private static final Component LABEL_SPACE_PERSONAL = Component.translatable("hud.todolist.space_label.personal");
    private static final Component LABEL_SPACE_TEAM = Component.translatable("hud.todolist.space_label.team");
    private static final Component LABEL_TEAM_VIEW_UNASSIGNED = Component.translatable("hud.todolist.team_view_label.unassigned");
    private static final Component LABEL_TEAM_VIEW_ALL = Component.translatable("hud.todolist.team_view_label.all");
    private static final Component LABEL_TEAM_VIEW_ASSIGNED = Component.translatable("hud.todolist.team_view_label.assigned");
    private static final Component LABEL_PROJECT_SOURCE_STARRED = Component.translatable("gui.todolist.hud.project_source.starred");
    private static final Component LABEL_PROJECT_SOURCE_ALL = Component.translatable("gui.todolist.hud.project_source.all");

    /**
     * HUD 行视觉元数据：统一描述优先级色块、前置标签和标题位置。
     */
    private static class HudRowVisual {
        private final int priorityBlockColor;
        private final String priorityText;
        private final String assigneeText;
        private final String tagText;
        private final int assigneeOffset;
        private final int tagOffset;
        private final int titleOffset;
        private final Component titleText;

        /**
         * 创建 HUD 行视觉元数据。
         *
         * @param priorityBlockColor 优先级色块颜色
         * @param priorityText 兼容旧版的优先级文本，新版保持为空字符串
         * @param assigneeText 指派人标签
         * @param tagText 首个标签文本
         * @param assigneeOffset 指派人标签偏移
         * @param tagOffset 标签偏移
         * @param titleOffset 标题起始偏移
         * @param titleText 行标题文本
         */
        private HudRowVisual(int priorityBlockColor, String priorityText, String assigneeText, String tagText,
                             int assigneeOffset, int tagOffset, int titleOffset, Component titleText) {
            this.priorityBlockColor = priorityBlockColor;
            this.priorityText = priorityText;
            this.assigneeText = assigneeText;
            this.tagText = tagText;
            this.assigneeOffset = assigneeOffset;
            this.tagOffset = tagOffset;
            this.titleOffset = titleOffset;
            this.titleText = titleText;
        }
    }

    /**
     * HUD 渲染计划：统一保存展开状态下的可见行数和隐藏数量。
     */
    private static class HudRenderPlan {
        private final int shownPending;
        private final int shownDone;
        private final int hiddenCount;
        private final boolean showDoneSection;
        private final boolean showMore;

        /**
         * 创建 HUD 渲染计划。
         *
         * @param shownPending 当前显示的未完成任务数量
         * @param shownDone 当前显示的已完成任务数量
         * @param hiddenCount 当前隐藏的任务数量
         * @param showDoneSection 是否需要显示已完成分组
         * @param showMore 是否需要显示隐藏计数行
         */
        private HudRenderPlan(int shownPending, int shownDone, int hiddenCount, boolean showDoneSection, boolean showMore) {
            this.shownPending = shownPending;
            this.shownDone = shownDone;
            this.hiddenCount = hiddenCount;
            this.showDoneSection = showDoneSection;
            this.showMore = showMore;
        }
    }

    /**
     * HUD SQL 项目过滤上下文，描述当前项目来源应匹配哪些项目。
     */
    private static class HudSqlProjectFilter {
        private final Set<String> projectIds;
        private final boolean includeAnyAssignedProject;

        /**
         * 创建 HUD SQL 项目过滤上下文。
         *
         * @param projectIds 指定项目 ID 集合
         * @param includeAnyAssignedProject 是否包含任意已分配项目
         */
        private HudSqlProjectFilter(Set<String> projectIds, boolean includeAnyAssignedProject) {
            this.projectIds = projectIds;
            this.includeAnyAssignedProject = includeAnyAssignedProject;
        }
    }

    /**
     * HUD 行缓存：保存任务行布局签名与可复用的视觉元数据。
     */
    private static class RowRenderCache {
        private final String taskId;
        private final String layoutKey;
        private final HudRowVisual rowVisual;
        private final int hudWidth;
        private final double guiScale;

        private RowRenderCache(String taskId, String layoutKey, HudRowVisual rowVisual, int hudWidth, double guiScale) {
            this.taskId = taskId;
            this.layoutKey = layoutKey;
            this.rowVisual = rowVisual;
            this.hudWidth = hudWidth;
            this.guiScale = guiScale;
        }
    }

    /**
     * 创建 HUD 渲染器，并按配置初始化展开状态。
     * @param client Minecraft 客户端实例
     */
    public TodoHudRenderer(Minecraft client) {
        this.client = client;
        this.expanded = ModConfig.getInstance().isHudDefaultExpanded();
    }

    /**
     * 渲染 HUD 列表主体。
     * @param context 绘制上下文
     * @param delta 帧间隔
     */
    public void render(GuiGraphics context, float delta) {
        if (client.options.hideGui) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnableHud() || !ClientBridge.ops().isHudVisible()) return;

        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        refreshHudModelIfNeeded(config, viewMode, scope);

        if (cachedPendingTasks.isEmpty() && cachedDoneTasks.isEmpty() && !config.isHudShowWhenEmpty()) return;

        // Calculate layout
        List<Task> pending = cachedPendingTasks;
        List<Task> done = cachedDoneTasks;
        HudRenderPlan renderPlan = buildRenderPlan(config, pending, done, cachedPendingTotalCount, cachedDoneTotalCount);
        int panelHeight = calculatePanelHeight(config, pending, done);

        // Clamp coordinates
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int hudWidth = config.getHudWidth();
        
        ModConfig.HudPlacement placement = config.resolveHudPlacement(screenWidth, screenHeight, hudWidth, panelHeight);
        int x = placement.getX();
        int y = placement.getY();

        renderTaskList(context, x, y, hudWidth, panelHeight, pending, done, renderPlan, config, viewMode);
    }

    private void refreshHudModelIfNeeded(ModConfig config, HudViewMode viewMode, Project.Scope scope) {
        long now = System.currentTimeMillis();
        String sourceMode = config.getHudProjectSource();
        String activeProjectId = ClientBridge.ops().getActiveProjectId();
        String playerUuid = client.player == null ? "" : client.player.getStringUUID();
        int hudWidth = config.getHudWidth();
        double guiScale = client.getWindow().getGuiScale();
        boolean contextChanged = hasHudRefreshContextChanged(
                viewMode,
                scope,
                sourceMode,
                activeProjectId,
                playerUuid,
                hudWidth,
                guiScale
        );
        boolean shouldInspectModel = !hudModelCacheInitialized
                || contextChanged
                || now - lastHudModelRefreshMs >= HUD_MODEL_REFRESH_INTERVAL_MS;
        if (!shouldInspectModel) {
            return;
        }

        H2TaskQueryService.HudTaskQueryResult sqlResult = queryHudTasksFromH2(config, viewMode, scope);
        List<Task> pending;
        List<Task> done;
        int pendingTotal;
        int doneTotal;
        if (sqlResult != null) {
            pending = new ArrayList<>(sqlResult.getPendingTasks());
            done = new ArrayList<>(sqlResult.getDoneTasks());
            pendingTotal = sqlResult.getPendingTotal();
            doneTotal = sqlResult.getDoneTotal();
        } else {
            List<Task> tasks = loadTasksByScope(scope);
            tasks = filterByViewMode(tasks, viewMode);
            tasks = filterByProjectSource(tasks, scope, config);

            pending = new ArrayList<>();
            done = new ArrayList<>();
            for (Task task : tasks) {
                if (task.isCompleted()) {
                    done.add(task);
                } else {
                    pending.add(task);
                }
            }
            pendingTotal = pending.size();
            doneTotal = done.size();
        }
        cachedPendingTasks = pending;
        cachedDoneTasks = done;
        cachedPendingTotalCount = pendingTotal;
        cachedDoneTotalCount = doneTotal;
        rebuildRowRenderCache(pending, done, hudWidth);
        cachedHudViewMode = viewMode;
        cachedHudScope = scope;
        cachedProjectSourceMode = sourceMode;
        cachedActiveProjectId = valueOrEmpty(activeProjectId);
        cachedPlayerUuid = playerUuid;
        cachedLayoutHudWidth = hudWidth;
        cachedGuiScale = guiScale;
        hudModelCacheInitialized = true;
        lastHudModelRefreshMs = now;
    }

    /**
     * 判断 HUD 查询上下文是否变化，变化时必须立即重建任务模型。
     *
     * @param viewMode HUD 视图模式
     * @param scope 当前项目空间
     * @param sourceMode 项目来源模式
     * @param activeProjectId 当前激活项目 ID
     * @param playerUuid 当前玩家 UUID
     * @param hudWidth HUD 宽度
     * @param guiScale 当前 GUI 缩放
     * @return 查询上下文变化时返回 true
     */
    private boolean hasHudRefreshContextChanged(HudViewMode viewMode, Project.Scope scope, String sourceMode,
                                                String activeProjectId, String playerUuid, int hudWidth, double guiScale) {
        return viewMode != cachedHudViewMode
                || scope != cachedHudScope
                || !sourceMode.equals(cachedProjectSourceMode)
                || !valueOrEmpty(activeProjectId).equals(cachedActiveProjectId)
                || !playerUuid.equals(cachedPlayerUuid)
                || hudWidth != cachedLayoutHudWidth
                || Double.compare(guiScale, cachedGuiScale) != 0;
    }

    /**
     * 在 H2 后端下通过 SQL 查询 HUD 当前视图所需任务，失败时返回 null 以保留内存路径兜底。
     *
     * @param config 模组配置
     * @param viewMode HUD 视图模式
     * @param scope 当前项目空间
     * @return H2 查询结果；不可用或失败时返回 null
     */
    private H2TaskQueryService.HudTaskQueryResult queryHudTasksFromH2(ModConfig config, HudViewMode viewMode, Project.Scope scope) {
        if (!shouldUseSynchronousH2HudQueries()) {
            return null;
        }
        try {
            H2TaskQueryService.HudTaskQuery query = buildHudTaskQuery(config, viewMode, scope);
            if (query == null) {
                return null;
            }
            return new H2TaskQueryService().queryHudTasks(query);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 判断 HUD 是否允许在渲染路径执行同步 H2 查询。
     *
     * @return 当前固定返回 false，优先使用内存快照避免 HUD 周期性卡顿
     */
    private boolean shouldUseSynchronousH2HudQueries() {
        return false;
    }

    /**
     * 构建 H2 HUD 查询参数。
     *
     * @param config 模组配置
     * @param viewMode HUD 视图模式
     * @param scope 当前项目空间
     * @return HUD 查询参数；无法确定桶时返回 null
     */
    private H2TaskQueryService.HudTaskQuery buildHudTaskQuery(ModConfig config, HudViewMode viewMode, Project.Scope scope) {
        String bucketType = scope == Project.Scope.TEAM ? H2TaskStore.TEAM_BUCKET : H2TaskStore.LOCAL_PERSONAL_BUCKET;
        String ownerUuid = scope == Project.Scope.TEAM ? H2TaskStore.TEAM_OWNER : H2TaskStore.LOCAL_OWNER;
        if (scope == Project.Scope.PERSONAL && client != null && client.player != null && ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(client)) {
            var storagePlayerUuid = ClientTaskStorageHelper.resolveStoragePlayerUuid(client);
            bucketType = H2TaskStore.PLAYER_PERSONAL_BUCKET;
            ownerUuid = storagePlayerUuid == null ? "" : storagePlayerUuid.toString();
        }

        HudSqlProjectFilter projectFilter = resolveHudSqlProjectFilter(scope, config);
        H2TaskQueryService.HudAssigneeFilter assigneeFilter = resolveHudAssigneeFilter(viewMode);
        String playerUuid = client.player == null ? "" : client.player.getStringUUID();
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - 14) / 12);
        int pendingLimit = Math.min(Math.max(0, config.getHudTodoLimit()), maxRowsByHeight);
        int doneLimit = Math.min(Math.max(0, config.getHudDoneLimit()), maxRowsByHeight);
        return new H2TaskQueryService.HudTaskQuery(
                bucketType,
                ownerUuid,
                projectFilter.projectIds,
                projectFilter.includeAnyAssignedProject,
                assigneeFilter,
                playerUuid,
                pendingLimit,
                doneLimit
        );
    }

    /**
     * 将 HUD 项目来源解析为 SQL 项目过滤条件。
     *
     * @param scope 当前项目空间
     * @param config 模组配置
     * @return SQL 项目过滤上下文
     */
    private HudSqlProjectFilter resolveHudSqlProjectFilter(Project.Scope scope, ModConfig config) {
        ProjectManager manager = TodoListCommon.getProjectManager();
        List<Project> scopedProjects = manager.getProjectsByScope(scope);
        String sourceMode = config.getHudProjectSource();
        Set<String> allowedProjectIds = new HashSet<>();
        Project activeProject = resolveScopedActiveProject(manager, scope, scopedProjects);

        if ("CURRENT".equalsIgnoreCase(sourceMode)) {
            if (activeProject != null) {
                allowedProjectIds.add(activeProject.getId());
            } else {
                collectProjectIds(scopedProjects, allowedProjectIds);
            }
        } else if ("STARRED".equalsIgnoreCase(sourceMode)) {
            for (Project project : scopedProjects) {
                if (config.isHudProjectStarred(project.getId())) {
                    allowedProjectIds.add(project.getId());
                }
            }
        } else {
            collectProjectIds(scopedProjects, allowedProjectIds);
        }
        return new HudSqlProjectFilter(allowedProjectIds, scope == Project.Scope.TEAM && allowedProjectIds.isEmpty());
    }

    /**
     * 将 HUD 视图模式解析为 H2 指派过滤模式。
     *
     * @param viewMode HUD 视图模式
     * @return H2 指派过滤模式
     */
    private H2TaskQueryService.HudAssigneeFilter resolveHudAssigneeFilter(HudViewMode viewMode) {
        if (viewMode == HudViewMode.TEAM_UNASSIGNED) {
            return H2TaskQueryService.HudAssigneeFilter.UNASSIGNED;
        }
        if (viewMode == HudViewMode.TEAM_ASSIGNED) {
            return H2TaskQueryService.HudAssigneeFilter.ASSIGNED_TO_PLAYER;
        }
        return H2TaskQueryService.HudAssigneeFilter.ANY;
    }

    /**
     * 根据配置解析 HUD 视图模式，并在团队功能不可用时回退为个人视图。
     * @param config 模组配置
     * @return HUD 视图模式
     */
    private HudViewMode resolveViewMode(ModConfig config) {
        String raw = config.getHudDefaultView();
        if ("TEAM_UNASSIGNED".equalsIgnoreCase(raw)) {
            return ClientBridge.ops().isTeamProjectsEnabled() ? HudViewMode.TEAM_UNASSIGNED : HudViewMode.PERSONAL;
        }
        if ("TEAM_ALL".equalsIgnoreCase(raw)) {
            return ClientBridge.ops().isTeamProjectsEnabled() ? HudViewMode.TEAM_ALL : HudViewMode.PERSONAL;
        }
        if ("TEAM_ASSIGNED".equalsIgnoreCase(raw)) {
            return ClientBridge.ops().isTeamProjectsEnabled() ? HudViewMode.TEAM_ASSIGNED : HudViewMode.PERSONAL;
        }
        return HudViewMode.PERSONAL;
    }

    /**
     * 根据视图模式返回对应任务范围（个人/团队）。
     * @param viewMode HUD 视图模式
     * @return 项目范围
     */
    private Project.Scope getScopeByView(HudViewMode viewMode) {
        return viewMode == HudViewMode.PERSONAL ? Project.Scope.PERSONAL : Project.Scope.TEAM;
    }

    /**
     * 按范围加载任务数据。
     * @param scope 项目范围
     * @return 对应范围的任务列表
     */
    private List<Task> loadTasksByScope(Project.Scope scope) {
        long now = System.currentTimeMillis();
        long reloadIntervalMs = 1000L;
        if (scope == Project.Scope.PERSONAL) {
            if (cachedPersonalTasksFromGui) {
                return new ArrayList<>(cachedPersonalTasks);
            }
            if (StorageBackendFactory.isH2Selected()) {
                if (!cachedPersonalTasksInitialized) {
                    cachedPersonalTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(TodoListCommon.getTaskStorage(), client);
                    cachedPersonalTasksInitialized = true;
                    lastPersonalReloadMs = now;
                }
                return new ArrayList<>(cachedPersonalTasks);
            }
            long localLastSaved = ClientTaskStorageHelper.getPersonalTasksLastSaved(TodoListCommon.getTaskStorage(), client);
            boolean shouldReload = cachedPersonalTasks.isEmpty()
                    || localLastSaved != cachedPersonalLastSavedMs
                    || (now - lastPersonalReloadMs >= reloadIntervalMs && localLastSaved == 0L && cachedPersonalLastSavedMs == 0L);
            if (shouldReload) {
                cachedPersonalTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(TodoListCommon.getTaskStorage(), client);
                cachedPersonalTasksInitialized = true;
                cachedPersonalLastSavedMs = localLastSaved;
                lastPersonalReloadMs = now;
            }
            return new ArrayList<>(cachedPersonalTasks);
        }
        if (now - lastTeamReloadMs >= reloadIntervalMs) {
            TaskManager teamTaskManager = ClientBridge.ops().getTeamTaskManager();
            cachedTeamTasks = teamTaskManager == null ? new ArrayList<>() : teamTaskManager.getAllTasks();
            lastTeamReloadMs = now;
        }
        return new ArrayList<>(cachedTeamTasks);
    }

    /**
     * 按视图模式过滤团队任务的指派状态。
     * @param source 原始任务列表
     * @param viewMode HUD 视图模式
     * @return 过滤后的任务列表
     */
    private List<Task> filterByViewMode(List<Task> source, HudViewMode viewMode) {
        if (viewMode == HudViewMode.PERSONAL) {
            return source;
        }
        List<Task> result = new ArrayList<>();
        String myUuid = client.player == null ? null : client.player.getStringUUID();
        for (Task task : source) {
            String assignee = task.getAssigneeUuid();
            boolean assigned = assignee != null && !assignee.isEmpty();
            if (viewMode == HudViewMode.TEAM_UNASSIGNED && !assigned) {
                result.add(task);
            } else if (viewMode == HudViewMode.TEAM_ALL) {
                result.add(task);
            } else if (viewMode == HudViewMode.TEAM_ASSIGNED && assigned && myUuid != null && myUuid.equals(assignee)) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 按 HUD 项目来源（当前/星标/全部）过滤任务。
     * @param source 原始任务列表
     * @param scope 当前任务范围
     * @param config 模组配置
     * @return 过滤后的任务列表
     */
    private List<Task> filterByProjectSource(List<Task> source, Project.Scope scope, ModConfig config) {
        ProjectManager manager = TodoListCommon.getProjectManager();
        List<Project> scopedProjects = manager.getProjectsByScope(scope);
        String sourceMode = config.getHudProjectSource();
        Set<String> allowedProjectIds = new HashSet<>();
        Project activeProject = resolveScopedActiveProject(manager, scope, scopedProjects);

        if ("CURRENT".equalsIgnoreCase(sourceMode)) {
            if (activeProject != null) {
                allowedProjectIds.add(activeProject.getId());
            } else {
                collectProjectIds(scopedProjects, allowedProjectIds);
            }
        } else if ("STARRED".equalsIgnoreCase(sourceMode)) {
            for (Project project : scopedProjects) {
                if (config.isHudProjectStarred(project.getId())) {
                    allowedProjectIds.add(project.getId());
                }
            }
            if (scope == Project.Scope.TEAM && allowedProjectIds.isEmpty() && scopedProjects.isEmpty()) {
                return filterAssignedProjectTasks(source);
            }
        } else {
            collectProjectIds(scopedProjects, allowedProjectIds);
        }

        if (scope == Project.Scope.TEAM && allowedProjectIds.isEmpty()) {
            return filterAssignedProjectTasks(source);
        }

        List<Task> result = new ArrayList<>();
        for (Task task : source) {
            if (task.isProjectUnassigned()) {
                continue;
            }
            String projectId = task.getProjectId();
            if (allowedProjectIds.contains(projectId)) {
                result.add(task);
            }
        }
        return result;
    }

    private Project resolveScopedActiveProject(ProjectManager manager, Project.Scope scope, List<Project> scopedProjects) {
        Project scopedActive = ClientBridge.getActiveProject(manager, scope);
        if (scopedActive != null) {
            return scopedActive;
        }
        String activeProjectId = ClientBridge.ops().getActiveProjectId();
        if (activeProjectId != null && !activeProjectId.isEmpty()) {
            Project activeProject = manager.getProject(activeProjectId);
            if (activeProject != null && activeProject.getScope() == scope) {
                return activeProject;
            }
        }
        for (Project project : scopedProjects) {
            if (scope == Project.Scope.TEAM && project.isDefaultTeamProject()) {
                return project;
            }
            if (scope == Project.Scope.PERSONAL && project.isDefaultPersonalProject()) {
                return project;
            }
        }
        return scopedProjects.isEmpty() ? null : scopedProjects.get(0);
    }

    private void collectProjectIds(List<Project> projects, Set<String> projectIds) {
        for (Project project : projects) {
            if (project != null && project.getId() != null && !project.getId().isEmpty()) {
                projectIds.add(project.getId());
            }
        }
    }

    private List<Task> filterAssignedProjectTasks(List<Task> source) {
        List<Task> result = new ArrayList<>();
        for (Task task : source) {
            if (!task.isProjectUnassigned()) {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 根据视图模式返回 HUD 标题的翻译文本。
     * @param viewMode HUD 视图模式
     * @return HUD 标题视图标签
     */
    private Component buildHeaderTitle(HudViewMode viewMode) {
        Project.Scope scope = getScopeByView(viewMode);
        ProjectManager manager = TodoListCommon.getProjectManager();
        List<Project> scopedProjects = manager.getProjectsByScope(scope);
        String projectName = buildHeaderProjectSourceText(scope, manager, scopedProjects, ModConfig.getInstance().getHudProjectSource());

        StringBuilder builder = new StringBuilder(getSpaceLabel(scope).getString());
        if (!projectName.isEmpty()) {
            builder.append('-').append(projectName);
        }

        Component teamViewLabel = getTeamViewLabel(viewMode);
        if (teamViewLabel != null) {
            builder.append('-').append(teamViewLabel.getString());
        }
        return Component.literal(builder.toString());
    }

    /**
     * 根据 HUD 项目来源构建标题中的项目段文本。
     *
     * @param scope 当前项目范围
     * @param manager 项目管理器
     * @param scopedProjects 当前范围下的项目列表
     * @param sourceMode HUD 项目来源配置
     * @return 标题中的项目段文本
     */
    private String buildHeaderProjectSourceText(Project.Scope scope, ProjectManager manager, List<Project> scopedProjects, String sourceMode) {
        if ("STARRED".equalsIgnoreCase(sourceMode)) {
            return LABEL_PROJECT_SOURCE_STARRED.getString();
        }
        if ("ALL".equalsIgnoreCase(sourceMode)) {
            return LABEL_PROJECT_SOURCE_ALL.getString();
        }

        Project activeProject = resolveScopedActiveProject(manager, scope, scopedProjects);
        return ProjectNameFormatter.toDisplayText(activeProject).getString().trim();
    }

    /**
     * 根据项目范围返回 HUD 标题中的空间标签。
     *
     * @param scope 当前项目范围
     * @return HUD 空间标签
     */
    private Component getSpaceLabel(Project.Scope scope) {
        return scope == Project.Scope.TEAM ? LABEL_SPACE_TEAM : LABEL_SPACE_PERSONAL;
    }

    /**
     * 根据团队 HUD 视图返回对应的子视图标签。
     *
     * @param viewMode HUD 视图模式
     * @return 团队子视图标签；个人视图时返回 {@code null}
     */
    private Component getTeamViewLabel(HudViewMode viewMode) {
        return switch (viewMode) {
            case TEAM_UNASSIGNED -> LABEL_TEAM_VIEW_UNASSIGNED;
            case TEAM_ALL -> LABEL_TEAM_VIEW_ALL;
            case TEAM_ASSIGNED -> LABEL_TEAM_VIEW_ASSIGNED;
            case PERSONAL -> null;
        };
    }

    /**
     * 绘制任务列表与统计信息。
     * @param context 绘制上下文
     * @param x HUD 起始 x
     * @param y HUD 起始 y
     * @param width HUD 宽度
     * @param panelHeight HUD 总高度
     * @param pending 待办任务列表（已排序）
     * @param done 已完成任务列表（已排序）
     * @param renderPlan 当前 HUD 渲染计划
     * @param config 模组配置
     * @param viewLabel 当前视图标签
     */
    private void renderTaskList(GuiGraphics context, int x, int y, int width, int panelHeight, List<Task> pending, List<Task> done,
            HudRenderPlan renderPlan, ModConfig config, HudViewMode viewMode) {
        int currentY = y;
        float opacity = (float) config.getHudOpacity();
        int panelColor = applyBackgroundOpacity(0xFF232323, opacity);
        int rowHeight = 12;
        int headerHeight = 14;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Component title = buildHeaderTitle(viewMode);

        context.fill(x, y, x + width, y + panelHeight, panelColor);
        int headerTextY = y + (headerHeight - client.font.lineHeight) / 2;
        context.drawString(client.font, title, x + 4, headerTextY, toOpaqueColor(0xFFE0B240));
        currentY += headerHeight;

        if (!expanded) {
            Component summary = buildCollapsedSummaryText(cachedPendingTotalCount, cachedDoneTotalCount);
            int summaryY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, summary, x + 4, summaryY, toOpaqueColor(0xDDDDDD));
            return;
        }

        for (int i = 0; i < renderPlan.shownPending; i++) {
            drawTaskRow(context, x, currentY, width, rowHeight, pending.get(i));
            currentY += rowHeight;
        }

        if (renderPlan.showDoneSection) {
            int separatorY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, SEPARATOR_COMPLETED, x + 4, separatorY, toOpaqueColor(0xAAAAAA));
            currentY += rowHeight;
            for (int i = 0; i < renderPlan.shownDone; i++) {
                drawTaskRow(context, x, currentY, width, rowHeight, done.get(i));
                currentY += rowHeight;
            }
        }

        if (renderPlan.showMore) {
            int moreY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, buildHiddenCountText(renderPlan.hiddenCount), x + 4, moreY, toOpaqueColor(0xAAAAAA));
        }
    }

    /**
     * 绘制单条任务行（含完成状态与优先级图标）。
     * @param context 绘制上下文
     * @param x 行起始 x
     * @param y 行起始 y
     * @param opacity HUD 透明度（用于同步文本与背景的显示效果）
     * @param task 待绘制任务
     */
    private void drawTaskRow(GuiGraphics context, int x, int y, int width, int rowHeight, Task task) {
        RowRenderCache rowCache = rowRenderCacheByTaskId.get(task.getId());
        if (!isRowRenderCacheValid(rowCache, task, width)) {
            rowCache = buildRowRenderCache(task, width);
            rowRenderCacheByTaskId.put(rowCache.taskId, rowCache);
        }

        int rowLeft = x + 4;
        int titleTextY = y + (rowHeight - client.font.lineHeight) / 2;
        int labelLineHeight = Math.max(1, Math.round(client.font.lineHeight * HUD_LABEL_SCALE));
        int labelTextY = y + (rowHeight - labelLineHeight) / 2;
        int textColor = toOpaqueColor(0xFFFFFF);
        int labelColor = toOpaqueColor(0x55FFFF);

        HudRowVisual rowVisual = rowCache.rowVisual;
        context.fill(rowLeft, y + 2, rowLeft + HUD_PRIORITY_BLOCK_WIDTH, y + rowHeight - 2,
                toOpaqueColor(rowVisual.priorityBlockColor));

        if (rowVisual.tagText != null && !rowVisual.tagText.isEmpty()) {
            drawScaledString(context, rowVisual.tagText, rowLeft + rowVisual.tagOffset, labelTextY, labelColor, HUD_LABEL_SCALE);
        }
        if (rowVisual.titleText != null) {
            context.drawString(client.font, rowVisual.titleText, rowLeft + rowVisual.titleOffset, titleTextY, textColor);
        }
    }

    /**
     * 重建 HUD 任务行缓存。
     *
     * @param pending 未完成任务列表
     * @param done 已完成任务列表
     * @param hudWidth HUD 宽度
     */
    private void rebuildRowRenderCache(List<Task> pending, List<Task> done, int hudWidth) {
        rowRenderCacheByTaskId.clear();
        for (Task task : pending) {
            RowRenderCache cache = buildRowRenderCache(task, hudWidth);
            rowRenderCacheByTaskId.put(cache.taskId, cache);
        }
        for (Task task : done) {
            RowRenderCache cache = buildRowRenderCache(task, hudWidth);
            rowRenderCacheByTaskId.put(cache.taskId, cache);
        }
    }

    /**
     * 构建单条 HUD 任务行缓存。
     *
     * @param task 当前任务
     * @param hudWidth HUD 宽度
     * @return 行缓存对象
     */
    private RowRenderCache buildRowRenderCache(Task task, int hudWidth) {
        String taskId = valueOrEmpty(task.getId());
        double guiScale = client.getWindow().getGuiScale();
        String layoutKey = buildRowLayoutKey(task, hudWidth, guiScale);
        HudRowVisual rowVisual = buildRowVisual(task, hudWidth);

        return new RowRenderCache(taskId, layoutKey, rowVisual, hudWidth, guiScale);
    }

    /**
     * 构建单条 HUD 任务行的视觉元数据。
     *
     * @param task 当前任务
     * @param hudWidth HUD 宽度
     * @return 行视觉元数据
     */
    private HudRowVisual buildRowVisual(Task task, int hudWidth) {
        int rowWidth = Math.max(0, hudWidth - 8);
        int currentOffset = HUD_PRIORITY_BLOCK_WIDTH + HUD_PRIORITY_BLOCK_GAP;
        int maxLabelWidth = Math.max(18, rowWidth / 3);

        String tagToken = buildHudLabelToken(resolveFirstTaskTag(task), maxLabelWidth);

        int assigneeOffset = 0;
        int tagOffset = 0;
        if (!tagToken.isEmpty()) {
            tagOffset = currentOffset;
            currentOffset += measureScaledTextWidth(tagToken, HUD_LABEL_SCALE);
        }

        int titleOffset = currentOffset;
        int titleMaxWidth = Math.max(0, rowWidth - titleOffset);
        String titleCore = trimWithEllipsis(valueOrEmpty(task.getTitle()), titleMaxWidth);
        Component titleText = titleCore.isEmpty()
                ? null
                : (task.isCompleted()
                ? Component.literal(titleCore).withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)
                : Component.literal(titleCore).withStyle(ChatFormatting.WHITE));

        return new HudRowVisual(resolvePriorityBlockColor(task.getPriority()), "",
                null,
                tagToken.isEmpty() ? null : tagToken,
                assigneeOffset, tagOffset, titleOffset, titleText);
    }

    /**
     * 构建任务行布局签名，用于判断缓存是否失效。
     *
     * @param task 当前任务
     * @param hudWidth HUD 宽度
     * @param guiScale 当前 GUI 缩放
     * @return 布局签名
     */
    private String buildRowLayoutKey(Task task, int hudWidth, double guiScale) {
        return valueOrEmpty(task.getId()) + '|'
                + valueOrEmpty(task.getTitle()) + '|'
                + task.getPriority().name() + '|'
                + task.isCompleted() + '|'
                + resolveFirstTaskTag(task) + '|'
                + hudWidth + '|'
                + guiScale;
    }

    /**
     * 判断任务行缓存是否仍然可复用。
     *
     * @param cache 当前缓存
     * @param task 当前任务
     * @param hudWidth HUD 宽度
     * @return true 表示缓存有效
     */
    private boolean isRowRenderCacheValid(RowRenderCache cache, Task task, int hudWidth) {
        if (cache == null) {
            return false;
        }
        double guiScale = client.getWindow().getGuiScale();
        if (cache.hudWidth != hudWidth || Double.compare(cache.guiScale, guiScale) != 0) {
            return false;
        }
        return cache.layoutKey.equals(buildRowLayoutKey(task, hudWidth, guiScale));
    }

    /**
     * 解析 HUD 优先级色块使用的颜色。
     *
     * @param priority 当前任务优先级
     * @return 优先级色块颜色
     */
    private int resolvePriorityBlockColor(Task.Priority priority) {
        return priority == null ? Task.Priority.MEDIUM.getColor() : priority.getColor();
    }

    /**
     * 构建 HUD 前置标签。
     *
     * @param rawLabel 原始标签内容
     * @param maxScaledWidth 标签允许的最大缩放宽度
     * @return 可直接绘制的标签文本
     */
    private String buildHudLabelToken(String rawLabel, int maxScaledWidth) {
        String limited = limitLabelContent(rawLabel, HUD_LABEL_MAX_CHARS);
        if (limited.isEmpty()) {
            return "";
        }
        return trimScaledTextWithEllipsis("[" + limited + "]", maxScaledWidth, HUD_LABEL_SCALE);
    }

    /**
     * 获取 HUD 使用的首个任务标签。
     *
     * @param task 当前任务
     * @return 首个非空标签
     */
    private String resolveFirstTaskTag(Task task) {
        if (task == null) {
            return "";
        }
        for (String tag : task.getTags()) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    /**
     * 按字符数限制标签内容长度。
     *
     * @param text 原始文本
     * @param maxChars 最大字符数
     * @return 裁剪后的文本
     */
    private String limitLabelContent(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "...";
    }

    /**
     * 计算缩放文本的屏幕宽度。
     *
     * @param text 文本内容
     * @param scale 绘制缩放
     * @return 屏幕像素宽度
     */
    private int measureScaledTextWidth(String text, float scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.round(client.font.width(text) * scale));
    }

    /**
     * 按缩放后的像素宽度裁剪文本。
     *
     * @param text 原始文本
     * @param maxScaledWidth 最大缩放宽度
     * @param scale 绘制缩放
     * @return 裁剪后的文本
     */
    private String trimScaledTextWithEllipsis(String text, int maxScaledWidth, float scale) {
        if (text == null) {
            return "";
        }
        if (maxScaledWidth <= 0) {
            return "";
        }
        if (measureScaledTextWidth(text, scale) <= maxScaledWidth) {
            return text;
        }
        int unscaledWidth = Math.max(1, (int) Math.floor(maxScaledWidth / Math.max(0.01F, scale)));
        return trimWithEllipsis(text, unscaledWidth);
    }

    /**
     * 以缩放方式绘制 HUD 标签文本。
     *
     * @param context 绘制上下文
     * @param text 文本内容
     * @param x 绘制 X 坐标
     * @param y 绘制 Y 坐标
     * @param color 文本颜色
     * @param scale 绘制缩放
     */
    private void drawScaledString(GuiGraphics context, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) {
            return;
        }
        context.pose().pushPose();
        context.pose().translate(x, y, 0.0F);
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(client.font, text, 0, 0, color, false);
        context.pose().popPose();
    }

    /**
     * 将输入颜色（RGB 或 ARGB）按指定 HUD 透明度缩放其 alpha 值。
     * <p>
     * 之所以需要该方法，是因为游戏内 HUD 渲染阶段可能处于“未开启混合”的状态，
     * 且 drawString 对 RGB 颜色会默认补齐 alpha=255；这里统一转换为 ARGB 并叠加透明度，
     * 以确保预览与实际 HUD 表现一致。
     *
     * @param color 颜色值（0xRRGGBB 或 0xAARRGGBB）
     * @param opacity 透明度倍率（0.0 ~ 1.0）
     * @return 叠加透明度后的 ARGB 颜色
     */
    private static int applyOpacityToColor(int color, float opacity) {
        int argb = (color & 0xFF000000) == 0 ? (0xFF000000 | (color & 0x00FFFFFF)) : color;
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = Math.round(baseAlpha * Math.max(0.0f, Math.min(1.0f, opacity)));
        if (scaledAlpha < 0) scaledAlpha = 0;
        if (scaledAlpha > 255) scaledAlpha = 255;
        return (scaledAlpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 仅对 HUD 背景类颜色应用透明度，不影响文字和前景元素。
     *
     * @param color 原始颜色
     * @param opacity 透明度倍率
     * @return 应用透明度后的背景颜色
     */
    private static int applyBackgroundOpacity(int color, float opacity) {
        return applyOpacityToColor(color, opacity);
    }

    /**
     * 将文字和前景元素颜色统一提升为不透明 ARGB，避免被 HUD 透明度联动影响。
     *
     * @param color 原始 RGB 或 ARGB 颜色
     * @return alpha 固定为 255 的前景颜色
     */
    private static int toOpaqueColor(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    /**
     * 解析 HUD 使用的指派人标签。
     *
     * @param task 当前任务
     * @return 指派人标签
     */
    private String resolveAssigneeLabel(Task task) {
        if (task == null) {
            return "";
        }
        String assigneeName = task.getAssigneeName();
        if (assigneeName != null && !assigneeName.trim().isEmpty()) {
            return assigneeName.trim();
        }
        String assigneeUuid = task.getAssigneeUuid();
        if (assigneeUuid == null || assigneeUuid.isEmpty()) {
            return "";
        }
        return assigneeUuid.length() > HUD_LABEL_MAX_CHARS
                ? assigneeUuid.substring(0, HUD_LABEL_MAX_CHARS)
                : assigneeUuid;
    }

    /**
     * 按像素宽度裁剪文本并追加省略号。
     *
     * @param text 原始文本
     * @param maxWidth 最大像素宽度
     * @return 裁剪后的文本
     */
    private String trimWithEllipsis(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (maxWidth <= 0) {
            return "";
        }
        if (client.font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = client.font.width(ELLIPSIS);
        int coreWidth = maxWidth - ellipsisWidth;
        if (coreWidth <= 0) {
            return "...";
        }
        String core = client.font.plainSubstrByWidth(text, coreWidth);
        return core + "...";
    }

    /**
     * 强制刷新任务缓存入口（当前渲染器按实时读取实现，预留扩展）。
     */
    public void forceRefreshTasks() {
        lastPersonalReloadMs = 0L;
        lastTeamReloadMs = 0L;
        cachedPersonalLastSavedMs = -1L;
        lastHudModelRefreshMs = 0L;
        hudModelCacheInitialized = false;
        cachedPersonalTasksInitialized = false;
        cachedPersonalTasksFromGui = false;
        cachedLayoutHudWidth = -1;
        cachedGuiScale = -1D;
        cachedPersonalTasks = new ArrayList<>();
        cachedTeamTasks = new ArrayList<>();
        cachedPendingTasks = new ArrayList<>();
        cachedDoneTasks = new ArrayList<>();
        cachedPendingTotalCount = 0;
        cachedDoneTotalCount = 0;
        rowRenderCacheByTaskId.clear();
    }

    /**
     * 用 GUI 中最新的个人任务快照更新 HUD 原始任务缓存。
     *
     * @param tasks GUI 当前个人任务列表
     */
    public void syncPersonalTasksFromGui(List<Task> tasks) {
        cachedPersonalTasks = copyTaskList(tasks);
        cachedPersonalTasksInitialized = true;
        cachedPersonalTasksFromGui = true;
        lastPersonalReloadMs = System.currentTimeMillis();
        invalidateHudModelCache();
    }

    /**
     * 清空 HUD 派生模型缓存，使下一帧基于原始任务缓存重建分组和行布局。
     */
    private void invalidateHudModelCache() {
        lastHudModelRefreshMs = 0L;
        hudModelCacheInitialized = false;
        cachedLayoutHudWidth = -1;
        cachedGuiScale = -1D;
        cachedPendingTasks = new ArrayList<>();
        cachedDoneTasks = new ArrayList<>();
        cachedPendingTotalCount = 0;
        cachedDoneTotalCount = 0;
        rowRenderCacheByTaskId.clear();
    }

    /**
     * 深拷贝任务列表，避免 HUD 缓存和 GUI 编辑对象互相污染。
     *
     * @param tasks 原始任务列表
     * @return 拷贝后的任务列表
     */
    private List<Task> copyTaskList(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
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
     * 计算当前配置下 HUD 实际会使用的面板高度。
     *
     * @return HUD 当前面板高度
     */
    public int getCurrentPanelHeight() {
        ModConfig config = ModConfig.getInstance();
        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        refreshHudModelIfNeeded(config, viewMode, scope);
        return calculatePanelHeight(config, cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount);
    }

    /**
     * 供离线测试手动触发一次 HUD 模型刷新，便于断言缓存筛选结果。
     */
    void refreshHudModelForTest() {
        ModConfig config = ModConfig.getInstance();
        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        refreshHudModelIfNeeded(config, viewMode, scope);
    }

    /**
     * 返回当前解析出的 HUD 视图模式名称，供离线测试断言回退逻辑。
     *
     * @return 当前 HUD 视图模式名称
     */
    String getResolvedViewModeNameForTest() {
        return resolveViewMode(ModConfig.getInstance()).name();
    }

    /**
     * 返回当前 HUD 顶部标题文本，供离线测试断言标题结构。
     *
     * @return 当前 HUD 顶部标题
     */
    String getHeaderTitleForTest() {
        return buildHeaderTitle(resolveViewMode(ModConfig.getInstance())).getString();
    }

    /**
     * 返回当前缓存中的未完成任务快照，供离线测试断言筛选结果。
     *
     * @return 当前缓存的未完成任务列表
     */
    List<Task> getCachedPendingTasksForTest() {
        return new ArrayList<>(cachedPendingTasks);
    }

    /**
     * 返回当前缓存中的已完成任务快照，供离线测试断言筛选结果。
     *
     * @return 当前缓存的已完成任务列表
     */
    List<Task> getCachedDoneTasksForTest() {
        return new ArrayList<>(cachedDoneTasks);
    }

    /**
     * 返回当前任务行渲染缓存数量，供离线测试断言缓存重建与清空行为。
     *
     * @return 当前任务行缓存条目数
     */
    int getRowRenderCacheSizeForTest() {
        return rowRenderCacheByTaskId.size();
    }

    /**
     * 返回指定任务行的优先级色块颜色，供离线测试断言 HUD 行视觉同步。
     *
     * @param taskId 任务 ID
     * @return 优先级色块颜色；找不到时返回 0
     */
    int getPriorityBlockColorForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null ? 0 : cache.rowVisual.priorityBlockColor;
    }

    /**
     * 返回指定任务行的优先级文本元数据，新版 HUD 应保持为空字符串。
     *
     * @param taskId 任务 ID
     * @return 优先级文本元数据
     */
    String getPriorityTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null ? "" : cache.rowVisual.priorityText;
    }

    /**
     * 返回指定任务行的标题偏移，供离线测试断言色块与前置标签占位语义。
     *
     * @param taskId 任务 ID
     * @return 标题起始偏移
     */
    /**
     * 返回指定任务行中的责任人标签文本，供离线测试断言 HUD 已隐藏责任人标签。
     *
     * @param taskId 任务 ID
     * @return 责任人标签文本
     */
    String getAssigneeTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null || cache.rowVisual.assigneeText == null ? "" : cache.rowVisual.assigneeText;
    }

    /**
     * 返回指定任务行中的任务标签文本，供离线测试确认标签仍然显示。
     *
     * @param taskId 任务 ID
     * @return 任务标签文本
     */
    String getTagTextForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null || cache.rowVisual.tagText == null ? "" : cache.rowVisual.tagText;
    }

    int getRowTitleOffsetForTest(String taskId) {
        RowRenderCache cache = rowRenderCacheByTaskId.get(taskId);
        return cache == null ? 0 : cache.rowVisual.titleOffset;
    }

    /**
     * 返回当前 HUD 计划中的未完成显示数量，供离线测试断言独立上限逻辑。
     *
     * @return 当前显示的未完成任务数量
     */
    int getShownPendingCountForTest() {
        HudRenderPlan renderPlan = buildRenderPlan(ModConfig.getInstance(), cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount);
        return renderPlan.shownPending;
    }

    /**
     * 返回当前 HUD 计划中的已完成显示数量，供离线测试断言独立上限逻辑。
     *
     * @return 当前显示的已完成任务数量
     */
    int getShownDoneCountForTest() {
        HudRenderPlan renderPlan = buildRenderPlan(ModConfig.getInstance(), cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount);
        return renderPlan.shownDone;
    }

    /**
     * 返回当前 HUD 计划中的隐藏任务数量，供离线测试断言截断语义。
     *
     * @return 隐藏任务数量
     */
    int getHiddenCountForTest() {
        HudRenderPlan renderPlan = buildRenderPlan(ModConfig.getInstance(), cachedPendingTasks, cachedDoneTasks, cachedPendingTotalCount, cachedDoneTotalCount);
        return renderPlan.hiddenCount;
    }

    /**
     * 返回给定透明度下的 HUD 背景颜色，供测试断言透明度只作用于背景。
     *
     * @param opacity HUD 透明度
     * @return HUD 背景颜色
     */
    int getPanelBackgroundColorForTest(float opacity) {
        return applyBackgroundOpacity(0xFF232323, opacity);
    }

    /**
     * 返回 HUD 标题文字颜色，供测试断言标题文字不受透明度影响。
     *
     * @return HUD 标题文字颜色
     */
    int getHeaderTextColorForTest() {
        return toOpaqueColor(0xFFE0B240);
    }

    /**
     * 返回 HUD 任务文字颜色，供测试断言任务文字不受透明度影响。
     *
     * @return HUD 任务文字颜色
     */
    int getTaskTextColorForTest() {
        return toOpaqueColor(0xFFFFFF);
    }

    /**
     * 返回当前 HUD 显示的隐藏计数文本，供离线测试断言展开态截断语义。
     *
     * @return 隐藏计数文本
     */
    String getHiddenCountTextForTest() {
        return buildHiddenCountText(getHiddenCountForTest()).getString();
    }

    /**
     * 返回当前 HUD 折叠态摘要文本，供离线测试断言折叠语义。
     *
     * @return 折叠态摘要文本
     */
    String getCollapsedSummaryTextForTest() {
        return buildCollapsedSummaryText(cachedPendingTotalCount, cachedDoneTotalCount).getString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 根据当前任务列表构建 HUD 渲染计划。
     *
     * @param config 当前模组配置
     * @param pending 未完成任务列表
     * @param done 已完成任务列表
     * @return HUD 渲染计划
     */
    private HudRenderPlan buildRenderPlan(ModConfig config, List<Task> pending, List<Task> done) {
        return buildRenderPlan(config, pending, done, pending == null ? 0 : pending.size(), done == null ? 0 : done.size());
    }

    /**
     * 根据任务总数和当前可绘制行构建 HUD 渲染计划。
     *
     * @param config 当前模组配置
     * @param pending 未完成任务可绘制行
     * @param done 已完成任务可绘制行
     * @param pendingTotal 未完成任务总数
     * @param doneTotal 已完成任务总数
     * @return HUD 渲染计划
     */
    private HudRenderPlan buildRenderPlan(ModConfig config, List<Task> pending, List<Task> done, int pendingTotal, int doneTotal) {
        int rowHeight = 12;
        int headerHeight = 14;
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - headerHeight) / rowHeight);
        int todoLimit = Math.max(0, config.getHudTodoLimit());
        int doneLimit = Math.max(0, config.getHudDoneLimit());
        if (!expanded) {
            int hiddenCount = Math.max(0, pendingTotal) + Math.max(0, doneTotal);
            return new HudRenderPlan(0, 0, hiddenCount, false, false);
        }

        HudRenderPlan visiblePlan = buildVisibleRowsPlan(maxRowsByHeight, todoLimit, doneLimit, Math.max(0, pendingTotal), Math.max(0, doneTotal));
        if (visiblePlan.hiddenCount <= 0 || maxRowsByHeight <= 0) {
            return clampRenderPlanToLoadedRows(visiblePlan, pending, done);
        }

        // 只要存在隐藏任务且内容区至少还能显示一行，就优先预留一行给“还有 N 项”提示，
        // 避免 HUD 在高度刚好不够时直接吞掉隐藏计数语义。
        HudRenderPlan reservedPlan = buildVisibleRowsPlan(Math.max(0, maxRowsByHeight - 1), todoLimit, doneLimit,
                Math.max(0, pendingTotal), Math.max(0, doneTotal));
        return clampRenderPlanToLoadedRows(new HudRenderPlan(reservedPlan.shownPending, reservedPlan.shownDone, reservedPlan.hiddenCount,
                reservedPlan.showDoneSection, true), pending, done);
    }

    /**
     * 将渲染计划中的可绘制行数量限制在已经加载的任务行范围内。
     *
     * @param plan 原始渲染计划
     * @param pending 未完成任务可绘制行
     * @param done 已完成任务可绘制行
     * @return 安全渲染计划
     */
    private HudRenderPlan clampRenderPlanToLoadedRows(HudRenderPlan plan, List<Task> pending, List<Task> done) {
        int pendingRows = pending == null ? 0 : pending.size();
        int doneRows = done == null ? 0 : done.size();
        int shownPending = Math.min(plan.shownPending, pendingRows);
        int shownDone = Math.min(plan.shownDone, doneRows);
        return new HudRenderPlan(shownPending, shownDone, plan.hiddenCount, shownDone > 0 && plan.showDoneSection, plan.showMore);
    }

    /**
     * 根据可用内容行数计算展开态下的基础可见行方案。
     *
     * @param availableRows 内容区可用行数
     * @param todoLimit 未完成任务上限
     * @param doneLimit 已完成任务上限
     * @param pendingCount 未完成任务总数
     * @param doneCount 已完成任务总数
     * @return 不含隐藏计数行的基础渲染计划
     */
    private HudRenderPlan buildVisibleRowsPlan(int availableRows, int todoLimit, int doneLimit, int pendingCount, int doneCount) {
        int safeRows = Math.max(0, availableRows);
        int shownPending = Math.min(pendingCount, Math.min(todoLimit, safeRows));
        int rowsAfterPending = Math.max(0, safeRows - shownPending);
        int shownDone = 0;
        if (doneCount > 0 && doneLimit > 0 && rowsAfterPending > 1) {
            shownDone = Math.min(doneCount, Math.min(doneLimit, rowsAfterPending - 1));
        }
        boolean showDoneSection = shownDone > 0;
        int hiddenCount = Math.max(0, pendingCount - shownPending) + Math.max(0, doneCount - shownDone);
        return new HudRenderPlan(shownPending, shownDone, hiddenCount, showDoneSection, false);
    }

    /**
     * 构建折叠态下使用的 HUD 摘要文本。
     *
     * @param pending 未完成任务列表
     * @param done 已完成任务列表
     * @return 折叠态摘要文本
     */
    private Component buildCollapsedSummaryText(List<Task> pending, List<Task> done) {
        return buildCollapsedSummaryText(pending == null ? 0 : pending.size(), done == null ? 0 : done.size());
    }

    /**
     * 构建折叠态下使用的 HUD 摘要文本。
     *
     * @param pendingCount 未完成任务总数
     * @param doneCount 已完成任务总数
     * @return 折叠态摘要文本
     */
    private Component buildCollapsedSummaryText(int pendingCount, int doneCount) {
        String summaryKey = doneCount <= 0 ? "hud.todolist.summary" : "hud.todolist.summary.with_completed";
        return doneCount <= 0
                ? Component.translatableWithFallback(summaryKey, "Todo: %s", Integer.toString(pendingCount))
                : Component.translatableWithFallback(summaryKey, "Todo: %s | Done: %s",
                Integer.toString(pendingCount), Integer.toString(doneCount));
    }

    /**
     * 构建展开态下的隐藏任务计数文本。
     *
     * @param hiddenCount 隐藏任务数量
     * @return 隐藏任务计数文本
     */
    private Component buildHiddenCountText(int hiddenCount) {
        return Component.translatableWithFallback("hud.todolist.more_tasks", "... %s more tasks",
                Integer.toString(Math.max(0, hiddenCount)));
    }

    /**
     * 根据当前任务列表计算 HUD 面板高度。
     *
     * @param config 当前模组配置
     * @param pending 未完成任务列表
     * @param done 已完成任务列表
     * @return HUD 面板高度
     */
    private int calculatePanelHeight(ModConfig config, List<Task> pending, List<Task> done) {
        return calculatePanelHeight(config, pending, done, pending == null ? 0 : pending.size(), done == null ? 0 : done.size());
    }

    /**
     * 根据当前任务总数计算 HUD 面板高度。
     *
     * @param config 当前模组配置
     * @param pending 未完成任务可绘制行
     * @param done 已完成任务可绘制行
     * @param pendingTotal 未完成任务总数
     * @param doneTotal 已完成任务总数
     * @return HUD 面板高度
     */
    private int calculatePanelHeight(ModConfig config, List<Task> pending, List<Task> done, int pendingTotal, int doneTotal) {
        int rowHeight = 12;
        int headerHeight = 14;
        HudRenderPlan renderPlan = buildRenderPlan(config, pending, done, pendingTotal, doneTotal);
        int rowsForSummary = expanded ? renderPlan.shownPending + (renderPlan.showDoneSection ? 1 + renderPlan.shownDone : 0) : 1;
        int totalRows = rowsForSummary + (renderPlan.showMore ? 1 : 0);
        return headerHeight + totalRows * rowHeight;
    }

    /**
     * 切换 HUD 展开/收起状态。
     */
    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }

    /**
     * 查询 HUD 是否处于展开状态。
     * @return true 表示展开，false 表示收起
     */
    public boolean isExpanded() {
        return expanded;
    }
}
