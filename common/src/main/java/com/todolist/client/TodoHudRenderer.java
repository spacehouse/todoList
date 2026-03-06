package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private final MinecraftClient client;
    private boolean expanded;
    private long lastPersonalReloadMs;
    private long lastTeamReloadMs;
    private List<Task> cachedPersonalTasks = new ArrayList<>();
    private List<Task> cachedTeamTasks = new ArrayList<>();

    /**
     * 创建 HUD 渲染器，并按配置初始化展开状态。
     * @param client Minecraft 客户端实例
     */
    public TodoHudRenderer(MinecraftClient client) {
        this.client = client;
        this.expanded = ModConfig.getInstance().isHudDefaultExpanded();
    }

    /**
     * 渲染 HUD 列表主体。
     * @param context 绘制上下文
     * @param delta 帧间隔
     */
    public void render(DrawContext context, float delta) {
        if (client.options.hudHidden) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.isEnableHud()) return;

        HudViewMode viewMode = resolveViewMode(config);
        Project.Scope scope = getScopeByView(viewMode);
        List<Task> tasks = loadTasksByScope(scope);
        tasks = filterByViewMode(tasks, viewMode);
        tasks = filterByProjectSource(tasks, scope, config);

        if (tasks.isEmpty() && !config.isHudShowWhenEmpty()) return;

        int x = config.isHudUseCustomPosition() ? config.getHudCustomX() : 10;
        int y = config.isHudUseCustomPosition() ? config.getHudCustomY() : 10;

        renderTaskList(context, x, y, tasks, config, getViewLabel(viewMode));
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
        String myUuid = client.player == null ? null : client.player.getUuidAsString();
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
    private Text getViewLabel(HudViewMode viewMode) {
        return switch (viewMode) {
            case TEAM_UNASSIGNED -> Text.translatable("hud.todolist.view_label.team_unassigned");
            case TEAM_ALL -> Text.translatable("hud.todolist.view_label.team_all");
            case TEAM_ASSIGNED -> Text.translatable("hud.todolist.view_label.team_assigned");
            case PERSONAL -> Text.translatable("hud.todolist.view_label.personal");
        };
    }

    /**
     * 绘制任务列表与统计信息，支持待办/已完成分组和数量限制。
     * @param context 绘制上下文
     * @param x HUD 起始 x
     * @param y HUD 起始 y
     * @param tasks 任务列表
     * @param config 模组配置
     * @param viewLabel 当前视图标签
     */
    private void renderTaskList(DrawContext context, int x, int y, List<Task> tasks, ModConfig config, Text viewLabel) {
        int currentY = y;
        int width = config.getHudWidth();
        float opacity = (float) config.getHudOpacity();
        int alpha = ((int) (opacity * 255)) << 24;
        int panelColor = alpha | 0x00232323;
        int rowHeight = 12;
        int headerHeight = 14;
        int maxRowsByHeight = Math.max(0, (config.getHudMaxHeight() - headerHeight) / rowHeight);

        Text title = Text.translatable("hud.todolist.header", viewLabel);
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

        context.fill(x, y, x + width, y + panelHeight, panelColor);
        int headerTextY = y + (headerHeight - client.textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(client.textRenderer, title, x + 4, headerTextY, 0xFFE0B240);
        currentY += headerHeight;

        if (!expanded) {
            String summaryKey = done.isEmpty() ? "hud.todolist.summary" : "hud.todolist.summary.with_completed";
            Text summary = done.isEmpty()
                    ? Text.translatable(summaryKey, Integer.toString(pending.size()))
                    : Text.translatable(summaryKey, Integer.toString(pending.size()), Integer.toString(done.size()));
            int summaryY = currentY + (rowHeight - client.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(client.textRenderer, summary, x + 4, summaryY, 0xDDDDDD);
            return;
        }

        int remainingRows = maxRowsByHeight;

        shownPending = 0;
        for (Task task : pending) {
            if (shownPending >= todoLimit || remainingRows <= 0) break;
            drawTaskRow(context, x, currentY, width, rowHeight, task);
            currentY += rowHeight;
            shownPending++;
            remainingRows--;
        }

        shownDone = 0;
        if (!done.isEmpty() && doneLimit > 0 && remainingRows > 0) {
            int separatorY = currentY + (rowHeight - client.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.todolist.separator.completed"), x + 4, separatorY, 0xAAAAAA);
            currentY += rowHeight;
            remainingRows--;
            for (Task task : done) {
                if (shownDone >= doneLimit || remainingRows <= 0) break;
                drawTaskRow(context, x, currentY, width, rowHeight, task);
                currentY += rowHeight;
                shownDone++;
                remainingRows--;
            }
        }

        hiddenCount = Math.max(0, pending.size() - shownPending) + Math.max(0, done.size() - shownDone);
        if (hiddenCount > 0 && remainingRows > 0) {
            int moreY = currentY + (rowHeight - client.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.todolist.more_tasks", Integer.toString(hiddenCount)), x + 4, moreY, 0xAAAAAA);
        }
    }

    /**
     * 绘制单条任务行（含完成状态与优先级图标）。
     * @param context 绘制上下文
     * @param x 行起始 x
     * @param y 行起始 y
     * @param task 待绘制任务
     */
    private void drawTaskRow(DrawContext context, int x, int y, int width, int rowHeight, Task task) {
        String priorityKey = switch (task.getPriority()) {
            case HIGH -> "hud.todolist.priority.high.icon";
            case MEDIUM -> "hud.todolist.priority.medium.icon";
            case LOW -> "hud.todolist.priority.low.icon";
        };
        Formatting priorityColor = switch (task.getPriority()) {
            case HIGH -> Formatting.RED;
            case MEDIUM -> Formatting.GOLD;
            case LOW -> Formatting.GREEN;
        };

        int paddingX = 4;
        int rowLeft = x + paddingX;
        int rowRight = x + width - paddingX;
        int rowWidth = Math.max(0, rowRight - rowLeft);
        int tagAreaWidth = Math.max(60, Math.min(120, rowWidth / 3));
        int tagGap = 6;
        int titleAreaWidth = Math.max(0, rowWidth - tagAreaWidth - tagGap);

        int textY = y + (rowHeight - client.textRenderer.fontHeight) / 2;
        int spaceWidth = client.textRenderer.getWidth(" ");

        Text priorityText = Text.translatable(priorityKey).formatted(priorityColor);
        Text checkboxText = Text.literal(task.isCompleted() ? "☑" : "☐").formatted(task.isCompleted() ? Formatting.DARK_GREEN : Formatting.WHITE);

        int cursorX = rowLeft;
        context.drawTextWithShadow(client.textRenderer, priorityText, cursorX, textY, 0xFFFFFF);
        cursorX += client.textRenderer.getWidth(priorityText) + spaceWidth;
        context.drawTextWithShadow(client.textRenderer, checkboxText, cursorX, textY, 0xFFFFFF);
        cursorX += client.textRenderer.getWidth(checkboxText) + spaceWidth;

        int titleMaxWidth = Math.max(0, titleAreaWidth - (cursorX - rowLeft));
        String titleCore = trimWithEllipsis(task.getTitle(), titleMaxWidth);
        Text titleText = task.isCompleted()
                ? Text.literal(titleCore).formatted(Formatting.GRAY, Formatting.STRIKETHROUGH)
                : Text.literal(titleCore).formatted(Formatting.WHITE);
        if (!titleCore.isEmpty() && titleMaxWidth > 0) {
            context.drawTextWithShadow(client.textRenderer, titleText, cursorX, textY, 0xFFFFFF);
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
            Text tagTextObj = Text.literal(trimmedTagText).formatted(Formatting.DARK_AQUA);
            int tagTextWidth = client.textRenderer.getWidth(trimmedTagText);
            int tagRightX = rowRight;
            int tagStartX = Math.max(rowLeft, rowRight - tagAreaWidth);
            int tagX = Math.max(tagStartX, tagRightX - tagTextWidth);
            context.drawTextWithShadow(client.textRenderer, tagTextObj, tagX, textY, 0xFFFFFF);
        }
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
        if (client.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = client.textRenderer.getWidth("...");
        int coreWidth = maxWidth - ellipsisWidth;
        if (coreWidth <= 0) {
            return "...";
        }
        String core = client.textRenderer.trimToWidth(text, coreWidth);
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
