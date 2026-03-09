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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 负责在 HUD 上渲染待办任务列表。
 */
public class TodoHudRenderer {
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
    private List<Task> cachedPersonalTasks = new ArrayList<>();
    private List<Task> cachedTeamTasks = new ArrayList<>();

    // 缓存常用组件
    private static final Component PRIORITY_HIGH_ICON = Component.translatable("hud.todolist.priority.high.icon").withStyle(ChatFormatting.RED);
    private static final Component PRIORITY_MEDIUM_ICON = Component.translatable("hud.todolist.priority.medium.icon").withStyle(ChatFormatting.GOLD);
    private static final Component PRIORITY_LOW_ICON = Component.translatable("hud.todolist.priority.low.icon").withStyle(ChatFormatting.GREEN);
    private static final Component CHECKBOX_CHECKED = Component.literal("☑").withStyle(ChatFormatting.DARK_GREEN);
    private static final Component CHECKBOX_UNCHECKED = Component.literal("☐").withStyle(ChatFormatting.WHITE);
    private static final Component SEPARATOR_COMPLETED = Component.translatable("hud.todolist.separator.completed");
    private static final Component ELLIPSIS = Component.literal("...");
    
    // 缓存视图标签
    private static final Component LABEL_TEAM_UNASSIGNED = Component.translatable("hud.todolist.view_label.team_unassigned");
    private static final Component LABEL_TEAM_ALL = Component.translatable("hud.todolist.view_label.team_all");
    private static final Component LABEL_TEAM_ASSIGNED = Component.translatable("hud.todolist.view_label.team_assigned");
    private static final Component LABEL_PERSONAL = Component.translatable("hud.todolist.view_label.personal");

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
        if (!config.isEnableHud()) return;

        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        List<Task> tasks = loadTasksByScope(scope);
        tasks = filterByViewMode(tasks, viewMode);
        tasks = filterByProjectSource(tasks, scope, config);

        if (tasks.isEmpty() && !config.isHudShowWhenEmpty()) return;

        // Calculate layout
        int rowHeight = 12;
        int headerHeight = 14;
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - headerHeight) / rowHeight);
        
        List<Task> pending = new ArrayList<>();
        List<Task> done = new ArrayList<>();
        for (Task task : tasks) {
            if (task.isCompleted()) {
                done.add(task);
            } else {
                pending.add(task);
            }
        }
        Comparator<Task> hudComparator = (a, b) -> {
            int priority = Integer.compare(b.getPriority().ordinal(), a.getPriority().ordinal());
            if (priority != 0) return priority;
            return Long.compare(a.getCreatedAt(), b.getCreatedAt());
        };
        pending.sort(hudComparator);
        done.sort(hudComparator);

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
        int panelHeight = headerHeight + totalRows * rowHeight;

        // Clamp coordinates
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int hudWidth = config.getHudWidth();
        
        int x = config.isHudUseCustomPosition() ? config.getHudCustomX() : 10;
        int y = config.isHudUseCustomPosition() ? config.getHudCustomY() : 10;
        
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + hudWidth > screenWidth) x = Math.max(0, screenWidth - hudWidth);
        if (y + panelHeight > screenHeight) y = Math.max(0, screenHeight - panelHeight);

        renderTaskList(context, x, y, hudWidth, panelHeight, pending, done, shownPending, shownDone, hiddenCount, config, getViewLabel(viewMode));
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
            if (now - lastPersonalReloadMs >= reloadIntervalMs) {
                cachedPersonalTasks = TodoListCommon.getTaskStorage().loadTasksSafe();
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
            return new ArrayList<>(source);
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
        Component priorityText = switch (task.getPriority()) {
            case HIGH -> PRIORITY_HIGH_ICON;
            case MEDIUM -> PRIORITY_MEDIUM_ICON;
            case LOW -> PRIORITY_LOW_ICON;
        };

        Component checkboxText = task.isCompleted() ? CHECKBOX_CHECKED : CHECKBOX_UNCHECKED;

        int paddingX = 4;
        int rowLeft = x + paddingX;
        int rowRight = x + width - paddingX;
        int rowWidth = Math.max(0, rowRight - rowLeft);
        int tagAreaWidth = Math.max(60, Math.min(120, rowWidth / 3));
        int tagGap = 6;
        int titleAreaWidth = Math.max(0, rowWidth - tagAreaWidth - tagGap);

        int textY = y + (rowHeight - client.font.lineHeight) / 2;
        int spaceWidth = client.font.width(" ");
        int textColor = applyOpacityToColor(0xFFFFFF, opacity);

        int cursorX = rowLeft;
        context.drawString(client.font, priorityText, cursorX, textY, textColor);
        cursorX += client.font.width(priorityText) + spaceWidth;
        context.drawString(client.font, checkboxText, cursorX, textY, textColor);
        cursorX += client.font.width(checkboxText) + spaceWidth;

        int titleMaxWidth = Math.max(0, titleAreaWidth - (cursorX - rowLeft));
        String titleCore = trimWithEllipsis(task.getTitle(), titleMaxWidth);
        Component titleText = task.isCompleted()
                ? Component.literal(titleCore).withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH)
                : Component.literal(titleCore).withStyle(ChatFormatting.WHITE);
        if (!titleCore.isEmpty() && titleMaxWidth > 0) {
            context.drawString(client.font, titleText, cursorX, textY, textColor);
        }

        String assigneeLabel = resolveAssigneeLabel(task);
        Set<String> tags = task.getTags();
        if ((!assigneeLabel.isEmpty() || !tags.isEmpty()) && tagAreaWidth > 0) {
            StringBuilder rightBuilder = new StringBuilder();
            if (!assigneeLabel.isEmpty()) {
                rightBuilder.append('[').append(assigneeLabel).append(']');
            }
            List<String> sortedTags = new ArrayList<>(tags);
            sortedTags.sort(String::compareToIgnoreCase);
            for (String tag : sortedTags) {
                if (tag == null) {
                    continue;
                }
                String trimmed = tag.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                rightBuilder.append('[').append(trimmed).append(']');
            }
            String tagText = rightBuilder.toString();
            String trimmedTagText = trimWithEllipsis(tagText, tagAreaWidth);
            Component tagTextObj = Component.literal(trimmedTagText).withStyle(ChatFormatting.DARK_AQUA);
            int tagTextWidth = client.font.width(trimmedTagText);
            int tagRightX = rowRight;
            int tagStartX = Math.max(rowLeft, rowRight - tagAreaWidth);
            int tagX = Math.max(tagStartX, tagRightX - tagTextWidth);
            context.drawString(client.font, tagTextObj, tagX, textY, textColor);
        }
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
        return assigneeUuid.length() > 8 ? assigneeUuid.substring(0, 8) : assigneeUuid;
    }

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
