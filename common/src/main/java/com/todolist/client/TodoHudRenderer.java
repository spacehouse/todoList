package com.todolist.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final long HUD_MODEL_REFRESH_INTERVAL_MS = 250L;
    private static final Comparator<Task> HUD_TASK_COMPARATOR = (a, b) -> {
        int priority = Integer.compare(b.getPriority().ordinal(), a.getPriority().ordinal());
        if (priority != 0) return priority;
        return Long.compare(a.getCreatedAt(), b.getCreatedAt());
    };

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
    private int cachedLayoutHudWidth = -1;
    private double cachedGuiScale = -1D;
    private List<Task> cachedPersonalTasks = new ArrayList<>();
    private List<Task> cachedTeamTasks = new ArrayList<>();
    private List<Task> cachedPendingTasks = new ArrayList<>();
    private List<Task> cachedDoneTasks = new ArrayList<>();
    private final Map<String, RowRenderCache> rowRenderCacheByTaskId = new HashMap<>();
    private HudViewMode cachedHudViewMode = HudViewMode.PERSONAL;
    private Project.Scope cachedHudScope = Project.Scope.PERSONAL;
    private String cachedProjectSourceMode = "";
    private String cachedActiveProjectId = "";
    private String cachedPlayerUuid = "";

    // 缓存常用组件
    private static final Component PRIORITY_HIGH_ICON = Component.translatable("hud.todolist.priority.high.icon").withStyle(ChatFormatting.RED);
    private static final Component PRIORITY_MEDIUM_ICON = Component.translatable("hud.todolist.priority.medium.icon").withStyle(ChatFormatting.GOLD);
    private static final Component PRIORITY_LOW_ICON = Component.translatable("hud.todolist.priority.low.icon").withStyle(ChatFormatting.GREEN);
    private static final Component CHECKBOX_CHECKED = Component.literal("☑").withStyle(ChatFormatting.DARK_GREEN);
    private static final Component CHECKBOX_UNCHECKED = Component.literal("☐").withStyle(ChatFormatting.WHITE);
    private static final Component SEPARATOR_COMPLETED = Component.translatable("hud.todolist.separator.completed");
    private static final Component ELLIPSIS = Component.literal("...");
    private static final float HUD_LABEL_SCALE = 0.85F;
    private static final int HUD_LABEL_MAX_CHARS = 8;
    
    // 缓存视图标签
    private static final Component LABEL_TEAM_UNASSIGNED = Component.translatable("hud.todolist.view_label.team_unassigned");
    private static final Component LABEL_TEAM_ALL = Component.translatable("hud.todolist.view_label.team_all");
    private static final Component LABEL_TEAM_ASSIGNED = Component.translatable("hud.todolist.view_label.team_assigned");
    private static final Component LABEL_PERSONAL = Component.translatable("hud.todolist.view_label.personal");

    private static class RowRenderCache {
        private final String taskId;
        private final String layoutKey;
        private final Component priorityText;
        private final String assigneeText;
        private final String tagText;
        private final int assigneeOffset;
        private final int tagOffset;
        private final int titleOffset;
        private final Component titleText;
        private final int hudWidth;
        private final double guiScale;

        private RowRenderCache(String taskId, String layoutKey, Component priorityText, String assigneeText, String tagText,
                               int assigneeOffset, int tagOffset, int titleOffset, Component titleText,
                               int hudWidth, double guiScale) {
            this.taskId = taskId;
            this.layoutKey = layoutKey;
            this.priorityText = priorityText;
            this.assigneeText = assigneeText;
            this.tagText = tagText;
            this.assigneeOffset = assigneeOffset;
            this.tagOffset = tagOffset;
            this.titleOffset = titleOffset;
            this.titleText = titleText;
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
        int rowHeight = 12;
        int headerHeight = 14;
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - headerHeight) / rowHeight);
        
        List<Task> pending = cachedPendingTasks;
        List<Task> done = cachedDoneTasks;

        int todoLimit = Math.max(0, config.getHudTodoLimit());
        int doneLimit = Math.max(0, config.getHudDoneLimit());
        int shownPending = Math.min(pending.size(), Math.min(todoLimit, maxRowsByHeight));
        int rowsAfterPending = maxRowsByHeight - shownPending;
        boolean showDoneSection = !done.isEmpty() && doneLimit > 0 && rowsAfterPending > 0;
        int shownDone = showDoneSection ? Math.min(done.size(), Math.min(doneLimit, rowsAfterPending - 1)) : 0;
        int rowsForSummary = expanded ? shownPending + (showDoneSection ? 1 + shownDone : 0) : 1;
        int hiddenCount = Math.max(0, pending.size() - shownPending) + Math.max(0, done.size() - shownDone);
        boolean showMore = expanded && hiddenCount > 0 && (rowsForSummary < maxRowsByHeight);
        int panelHeight = calculatePanelHeight(config, pending, done);

        // Clamp coordinates
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int hudWidth = config.getHudWidth();
        
        ModConfig.HudPlacement placement = config.resolveHudPlacement(screenWidth, screenHeight, hudWidth, panelHeight);
        int x = placement.getX();
        int y = placement.getY();

        renderTaskList(context, x, y, hudWidth, panelHeight, pending, done, shownPending, shownDone, hiddenCount, config, getViewLabel(viewMode));
    }

    private void refreshHudModelIfNeeded(ModConfig config, HudViewMode viewMode, Project.Scope scope) {
        long now = System.currentTimeMillis();
        String sourceMode = config.getHudProjectSource();
        String activeProjectId = ClientBridge.ops().getActiveProjectId();
        String playerUuid = client.player == null ? "" : client.player.getStringUUID();
        int hudWidth = config.getHudWidth();
        double guiScale = client.getWindow().getGuiScale();
        boolean shouldRefresh = now - lastHudModelRefreshMs >= HUD_MODEL_REFRESH_INTERVAL_MS
                || viewMode != cachedHudViewMode
                || scope != cachedHudScope
                || !sourceMode.equals(cachedProjectSourceMode)
                || !valueOrEmpty(activeProjectId).equals(cachedActiveProjectId)
                || !playerUuid.equals(cachedPlayerUuid)
                || hudWidth != cachedLayoutHudWidth
                || Double.compare(guiScale, cachedGuiScale) != 0;
        if (!shouldRefresh) {
            return;
        }

        List<Task> tasks = loadTasksByScope(scope);
        tasks = filterByViewMode(tasks, viewMode);
        tasks = filterByProjectSource(tasks, scope, config);

        List<Task> pending = new ArrayList<>();
        List<Task> done = new ArrayList<>();
        for (Task task : tasks) {
            if (task.isCompleted()) {
                done.add(task);
            } else {
                pending.add(task);
            }
        }
        pending.sort(HUD_TASK_COMPARATOR);
        done.sort(HUD_TASK_COMPARATOR);

        cachedPendingTasks = pending;
        cachedDoneTasks = done;
        rebuildRowRenderCache(pending, done, hudWidth);
        cachedHudViewMode = viewMode;
        cachedHudScope = scope;
        cachedProjectSourceMode = sourceMode;
        cachedActiveProjectId = valueOrEmpty(activeProjectId);
        cachedPlayerUuid = playerUuid;
        cachedLayoutHudWidth = hudWidth;
        cachedGuiScale = guiScale;
        lastHudModelRefreshMs = now;
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
            long localLastSaved = ClientTaskStorageHelper.getPersonalTasksLastSaved(TodoListCommon.getTaskStorage(), client);
            boolean shouldReload = cachedPersonalTasks.isEmpty()
                    || localLastSaved != cachedPersonalLastSavedMs
                    || (now - lastPersonalReloadMs >= reloadIntervalMs && localLastSaved == 0L && cachedPersonalLastSavedMs == 0L);
            if (shouldReload) {
                cachedPersonalTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(TodoListCommon.getTaskStorage(), client);
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
            } else if (viewMode == HudViewMode.TEAM_ALL && assigned) {
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
    private Component getViewLabel(HudViewMode viewMode) {
        return switch (viewMode) {
            case TEAM_UNASSIGNED -> LABEL_TEAM_UNASSIGNED;
            case TEAM_ALL -> LABEL_TEAM_ALL;
            case TEAM_ASSIGNED -> LABEL_TEAM_ASSIGNED;
            case PERSONAL -> LABEL_PERSONAL;
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
     * @param shownPending 显示的待办数量
     * @param shownDone 显示的已完成数量
     * @param hiddenCount 隐藏任务数
     * @param config 模组配置
     * @param viewLabel 当前视图标签
     */
    private void renderTaskList(GuiGraphics context, int x, int y, int width, int panelHeight, List<Task> pending, List<Task> done, 
            int shownPending, int shownDone, int hiddenCount, ModConfig config, Component viewLabel) {
        int currentY = y;
        float opacity = (float) config.getHudOpacity();
        int panelColor = applyOpacityToColor(0xFF232323, opacity);
        int rowHeight = 12;
        int headerHeight = 14;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Component title = Component.translatable("hud.todolist.header", viewLabel);

        context.fill(x, y, x + width, y + panelHeight, panelColor);
        int headerTextY = y + (headerHeight - client.font.lineHeight) / 2;
        context.drawString(client.font, title, x + 4, headerTextY, applyOpacityToColor(0xFFE0B240, opacity));
        currentY += headerHeight;

        if (!expanded) {
            String summaryKey = done.isEmpty() ? "hud.todolist.summary" : "hud.todolist.summary.with_completed";
            Component summary = done.isEmpty()
                    ? Component.translatable(summaryKey, Integer.toString(pending.size()))
                    : Component.translatable(summaryKey, Integer.toString(pending.size()), Integer.toString(done.size()));
            int summaryY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, summary, x + 4, summaryY, applyOpacityToColor(0xDDDDDD, opacity));
            return;
        }

        int renderedCount = 0;
        for (int i = 0; i < shownPending; i++) {
            drawTaskRow(context, x, currentY, width, rowHeight, opacity, pending.get(i));
            currentY += rowHeight;
            renderedCount++;
        }

        if (shownDone > 0) {
            int separatorY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, SEPARATOR_COMPLETED, x + 4, separatorY, applyOpacityToColor(0xAAAAAA, opacity));
            currentY += rowHeight;
            for (int i = 0; i < shownDone; i++) {
                drawTaskRow(context, x, currentY, width, rowHeight, opacity, done.get(i));
                currentY += rowHeight;
            }
        }

        if (hiddenCount > 0) {
            int moreY = currentY + (rowHeight - client.font.lineHeight) / 2;
            context.drawString(client.font, Component.translatable("hud.todolist.more_tasks", Integer.toString(hiddenCount)), x + 4, moreY, applyOpacityToColor(0xAAAAAA, opacity));
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
    private void drawTaskRow(GuiGraphics context, int x, int y, int width, int rowHeight, float opacity, Task task) {
        RowRenderCache rowCache = rowRenderCacheByTaskId.get(task.getId());
        if (!isRowRenderCacheValid(rowCache, task, width)) {
            rowCache = buildRowRenderCache(task, width);
            rowRenderCacheByTaskId.put(rowCache.taskId, rowCache);
        }

        int rowLeft = x + 4;
        int titleTextY = y + (rowHeight - client.font.lineHeight) / 2;
        int labelLineHeight = Math.max(1, Math.round(client.font.lineHeight * HUD_LABEL_SCALE));
        int labelTextY = y + (rowHeight - labelLineHeight) / 2;
        int textColor = applyOpacityToColor(0xFFFFFF, opacity);
        int labelColor = applyOpacityToColor(0x55FFFF, opacity);

        context.drawString(client.font, rowCache.priorityText, rowLeft, titleTextY, textColor);

        if (rowCache.assigneeText != null && !rowCache.assigneeText.isEmpty()) {
            drawScaledString(context, rowCache.assigneeText, rowLeft + rowCache.assigneeOffset, labelTextY, labelColor, HUD_LABEL_SCALE);
        }
        if (rowCache.tagText != null && !rowCache.tagText.isEmpty()) {
            drawScaledString(context, rowCache.tagText, rowLeft + rowCache.tagOffset, labelTextY, labelColor, HUD_LABEL_SCALE);
        }
        if (rowCache.titleText != null) {
            context.drawString(client.font, rowCache.titleText, rowLeft + rowCache.titleOffset, titleTextY, textColor);
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
        Component priorityText = getPriorityText(task);
        int rowWidth = Math.max(0, hudWidth - 8);
        int currentOffset = client.font.width(priorityText);
        int maxLabelWidth = Math.max(18, rowWidth / 3);

        String assigneeToken = buildHudLabelToken(resolveAssigneeLabel(task), maxLabelWidth);
        String tagToken = buildHudLabelToken(resolveFirstTaskTag(task), maxLabelWidth);

        int assigneeOffset = 0;
        if (!assigneeToken.isEmpty()) {
            assigneeOffset = currentOffset;
            currentOffset += measureScaledTextWidth(assigneeToken, HUD_LABEL_SCALE);
        }

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

        return new RowRenderCache(taskId, layoutKey, priorityText,
                assigneeToken.isEmpty() ? null : assigneeToken,
                tagToken.isEmpty() ? null : tagToken,
                assigneeOffset, tagOffset, titleOffset, titleText, hudWidth, guiScale);
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
                + resolveAssigneeLabel(task) + '|'
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
     * 获取 HUD 行使用的优先级文本。
     *
     * @param task 当前任务
     * @return 优先级文本
     */
    private Component getPriorityText(Task task) {
        return switch (task.getPriority()) {
            case HIGH -> PRIORITY_HIGH_ICON;
            case MEDIUM -> PRIORITY_MEDIUM_ICON;
            case LOW -> PRIORITY_LOW_ICON;
        };
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
        cachedLayoutHudWidth = -1;
        cachedGuiScale = -1D;
        cachedPendingTasks = new ArrayList<>();
        cachedDoneTasks = new ArrayList<>();
        rowRenderCacheByTaskId.clear();
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
        return calculatePanelHeight(config, cachedPendingTasks, cachedDoneTasks);
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

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
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
        int rowHeight = 12;
        int headerHeight = 14;
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - headerHeight) / rowHeight);
        int todoLimit = Math.max(0, config.getHudTodoLimit());
        int doneLimit = Math.max(0, config.getHudDoneLimit());
        int shownPending = Math.min(pending.size(), Math.min(todoLimit, maxRowsByHeight));
        int rowsAfterPending = maxRowsByHeight - shownPending;
        boolean showDoneSection = !done.isEmpty() && doneLimit > 0 && rowsAfterPending > 0;
        int shownDone = showDoneSection ? Math.min(done.size(), Math.min(doneLimit, rowsAfterPending - 1)) : 0;
        int rowsForSummary = expanded ? shownPending + (showDoneSection ? 1 + shownDone : 0) : 1;
        int hiddenCount = Math.max(0, pending.size() - shownPending) + Math.max(0, done.size() - shownDone);
        boolean showMore = expanded && hiddenCount > 0 && (rowsForSummary < maxRowsByHeight);
        int totalRows = rowsForSummary + (showMore ? 1 : 0);
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
