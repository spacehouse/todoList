package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.config.ModConfig;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import com.todolist.task.TaskStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HUD Renderer for Todo List
 *
 * Displays task overview on screen:
 * - Task count
 * - Next incomplete task
 * - Priority indicators
 */
public class TodoHudRenderer {
    private static final int HUD_PADDING = 5;
    private static final int LINE_HEIGHT = 12;
    private static final int TASK_DISPLAY_LIMIT = 5;

    private final MinecraftClient client;
    private TaskManager taskManager;
    private TaskStorage taskStorage;

    private boolean expanded;

    public TodoHudRenderer(MinecraftClient client) {
        this.client = client;
        this.expanded = ModConfig.getInstance().isHudDefaultExpanded();
        initializeTaskManager();
    }

    private void initializeTaskManager() {
        try {
            this.taskStorage = TodoListMod.getTaskStorage();
            this.taskManager = new TaskManager();
            List<Task> loadedTasks = taskStorage.loadTasks();
            for (Task task : loadedTasks) {
                taskManager.addTask(task);
            }
        } catch (Exception e) {
            TodoListMod.LOGGER.error("Failed to load tasks for HUD", e);
            this.taskManager = new TaskManager();
        }
    }

    /**
     * Render HUD on screen
     */
    public void render(DrawContext context, float tickDelta) {
        if (!ModConfig.getInstance().isEnableHud()) {
            return;
        }

        if (client.player == null) {
            return;
        }

        refreshTasksIfNeeded();

        List<Task> baseTasks = getBaseTasksForView();
        List<Task> displayTasks = getDisplayTasks(baseTasks);
        if (displayTasks.isEmpty() && !ModConfig.getInstance().isHudShowWhenEmpty()) {
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        int x;
        int y;
        if (cfg.isHudUseCustomPosition()) {
            x = cfg.getHudCustomX();
            y = cfg.getHudCustomY();
        } else {
            String position = cfg.getHudPosition();
            x = getX(position);
            y = getY(position);
        }

        TextRenderer textRenderer = client.textRenderer;

        int incompleteCount = 0;
        int completedCount = 0;
        for (Task task : baseTasks) {
            if (task.isCompleted()) {
                completedCount++;
            } else {
                incompleteCount++;
            }
        }

        if (expanded) {
            int todoLimit = ModConfig.getInstance().getHudTodoLimit();
            int doneLimit = ModConfig.getInstance().getHudDoneLimit();
            int totalLines = 2;
            totalLines += Math.min(incompleteCount, todoLimit);
            if (completedCount > 0 && ModConfig.getInstance().isShowCompletedTasks()) {
                totalLines += 1 + Math.min(completedCount, doneLimit);
            }
            int height = HUD_PADDING * 2 + LINE_HEIGHT * totalLines;
            int maxHeight = ModConfig.getInstance().getHudMaxHeight();
            if (height > maxHeight) {
                height = maxHeight;
            }
            int hudWidth = ModConfig.getInstance().getHudWidth();
            drawBackgroundPanel(context, x, y, hudWidth, height);

            // Draw header
            int currentY = y + HUD_PADDING;
            drawHeader(context, textRenderer, x + HUD_PADDING, currentY, incompleteCount, completedCount);
            currentY += LINE_HEIGHT + HUD_PADDING;

            int displayTodoLimit = todoLimit;
            int shownTasks = 0;
            for (Task task : displayTasks) {
                if (shownTasks >= displayTodoLimit) break;
                if (task.isCompleted()) continue; // Skip completed tasks

                String priorityIcon = getPriorityIcon(task.getPriority());
                String taskTitle = task.getTitle();
                if (taskTitle.length() > 18) {
                    taskTitle = taskTitle.substring(0, 15) + "...";
                }

                String statusIcon = "§7☐";

                String tagsDisplay = "";
                String baseTag = null;
                if (!task.getTags().isEmpty()) {
                    String allTags = String.join(",", task.getTags());
                    if (allTags.length() > 10) {
                        allTags = allTags.substring(0, 7) + "..";
                    }
                    baseTag = allTags;
                }
                String assigneeName = null;
                String assigneeUuid = task.getAssigneeUuid();
                if (assigneeUuid != null && client.getNetworkHandler() != null) {
                    java.util.Collection<net.minecraft.client.network.PlayerListEntry> entries = client.getNetworkHandler().getPlayerList();
                    for (net.minecraft.client.network.PlayerListEntry entry : entries) {
                        if (assigneeUuid.equals(entry.getProfile().getId().toString())) {
                            String name = entry.getProfile().getName();
                            if (name != null && !name.isEmpty()) {
                                assigneeName = name;
                            }
                            break;
                        }
                    }
                }
                StringBuilder sbTag = new StringBuilder();
                if (assigneeName != null && !assigneeName.isEmpty()) {
                    sbTag.append(assigneeName);
                }
                if (baseTag != null && !baseTag.isEmpty()) {
                    if (sbTag.length() > 0) {
                        sbTag.append(", ");
                    }
                    sbTag.append(baseTag);
                }
                if (sbTag.length() > 0) {
                    tagsDisplay = " §b[" + sbTag + "]§f";
                }

                String taskLine = priorityIcon + " " + statusIcon + " " + taskTitle + tagsDisplay;
                context.drawText(textRenderer, Text.of(taskLine),
                        x + HUD_PADDING, currentY, 0xFFFFFFFF, true);
                currentY += LINE_HEIGHT;
                shownTasks++;
            }

            if (completedCount > 0 && ModConfig.getInstance().isShowCompletedTasks()) {
                String separatorText = I18n.translate("hud.todolist.separator.completed");
                String separator = "§7§l" + separatorText;
                context.drawText(textRenderer, Text.of(separator),
                        x + HUD_PADDING, currentY, 0xFFFFFFFF, true);
                currentY += LINE_HEIGHT;
            }

            int displayDoneLimit = doneLimit;
            shownTasks = 0;
            for (Task task : displayTasks) {
                if (shownTasks >= displayDoneLimit) break;
                if (!task.isCompleted()) continue; // Skip incomplete tasks

                String priorityIcon = getPriorityIcon(task.getPriority());
                String taskTitle = task.getTitle();
                if (taskTitle.length() > 18) {
                    taskTitle = taskTitle.substring(0, 15) + "...";
                }

                String statusIcon = "§a☑";

                String tagsDisplay = "";
                String baseTag = null;
                if (!task.getTags().isEmpty()) {
                    String allTags = String.join(",", task.getTags());
                    if (allTags.length() > 10) {
                        allTags = allTags.substring(0, 7) + "..";
                    }
                    baseTag = allTags;
                }
                String assigneeName = null;
                String assigneeUuid = task.getAssigneeUuid();
                if (assigneeUuid != null && client.getNetworkHandler() != null) {
                    java.util.Collection<net.minecraft.client.network.PlayerListEntry> entries = client.getNetworkHandler().getPlayerList();
                    for (net.minecraft.client.network.PlayerListEntry entry : entries) {
                        if (assigneeUuid.equals(entry.getProfile().getId().toString())) {
                            String name = entry.getProfile().getName();
                            if (name != null && !name.isEmpty()) {
                                assigneeName = name;
                            }
                            break;
                        }
                    }
                }
                StringBuilder sbTagDone = new StringBuilder();
                if (assigneeName != null && !assigneeName.isEmpty()) {
                    sbTagDone.append(assigneeName);
                }
                if (baseTag != null && !baseTag.isEmpty()) {
                    if (sbTagDone.length() > 0) {
                        sbTagDone.append(", ");
                    }
                    sbTagDone.append(baseTag);
                }
                if (sbTagDone.length() > 0) {
                    tagsDisplay = " §b[" + sbTagDone + "]§f";
                }

                String taskLine = priorityIcon + " " + statusIcon + " " + taskTitle + tagsDisplay;
                context.drawText(textRenderer, Text.of(taskLine),
                        x + HUD_PADDING, currentY, 0xFFFFFFFF, true);
                currentY += LINE_HEIGHT;
                shownTasks++;
            }

            int totalTasks = incompleteCount + (ModConfig.getInstance().isShowCompletedTasks() ? completedCount : 0);
            int maxShown = Math.min(incompleteCount, displayTodoLimit)
                    + (ModConfig.getInstance().isShowCompletedTasks() ? Math.min(completedCount, displayDoneLimit) : 0);
            if (totalTasks > maxShown) {
                int more = totalTasks - maxShown;
                String moreText = I18n.translate("hud.todolist.more_tasks", Integer.toString(more));
                context.drawText(textRenderer, Text.of(moreText),
                        x + HUD_PADDING, currentY, 0xFFAAAAAA, true);
            }
        } else {
            int height = HUD_PADDING * 2 + LINE_HEIGHT;
            int hudWidth = ModConfig.getInstance().getHudWidth();
            drawBackgroundPanel(context, x, y, hudWidth, height);

            String summaryCore;
            if (completedCount > 0) {
                summaryCore = I18n.translate("hud.todolist.summary.with_completed",
                        Integer.toString(incompleteCount), Integer.toString(completedCount));
            } else {
                summaryCore = I18n.translate("hud.todolist.summary", Integer.toString(incompleteCount));
            }
            String summary = getPriorityIcon(getHighestPriority(baseTasks)) + " " + summaryCore;

            context.drawText(textRenderer, Text.of(summary),
                    x + HUD_PADDING, y + HUD_PADDING, 0xFFFFFFFF, true);
        }
    }

    private void drawBackgroundPanel(DrawContext context, int x, int y, int width, int height) {
        // Semi-transparent black background
        context.fill(x, y, x + width, y + height, 0xDD000000);
        // Border
        context.drawBorder(x, y, width, height, 0xFF3F3F3F);
    }

    private void drawHeader(DrawContext context, TextRenderer textRenderer, int x, int y, int incomplete, int completed) {
        String view = getEffectiveView();
        String viewLabelKey;
        if ("TEAM_UNASSIGNED".equalsIgnoreCase(view)) {
            viewLabelKey = "hud.todolist.view_label.team_unassigned";
        } else if ("TEAM_ALL".equalsIgnoreCase(view)) {
            viewLabelKey = "hud.todolist.view_label.team_all";
        } else if ("TEAM_ASSIGNED".equalsIgnoreCase(view)) {
            viewLabelKey = "hud.todolist.view_label.team_assigned";
        } else {
            viewLabelKey = "hud.todolist.view_label.personal";
        }
        String viewLabel = I18n.translate(viewLabelKey);
        String headerCore = I18n.translate("hud.todolist.header", viewLabel);
        String header = "§6§l" + headerCore;
        context.drawText(textRenderer, Text.of(header), x, y, 0xFFFFFFFF, true);
    }

    private String getEffectiveView() {
        ModConfig cfg = ModConfig.getInstance();
        String view = cfg.getHudDefaultView();
        if (client != null && client.isInSingleplayer()) {
            return "PERSONAL";
        }
        return view;
    }

    private List<Task> getBaseTasksForView() {
        String view = getEffectiveView();
        TaskManager manager;
        if (view != null && view.toUpperCase().startsWith("TEAM_")) {
            manager = TodoClient.getTeamTaskManager();
        } else {
            manager = taskManager;
        }
        List<Task> tasks = manager.getAllTasks();
        if ("TEAM_ALL".equalsIgnoreCase(view)) {
            tasks = tasks.stream()
                    .filter(t -> t.getAssigneeUuid() != null && !t.getAssigneeUuid().isEmpty())
                    .collect(Collectors.toList());
        } else if ("TEAM_ASSIGNED".equalsIgnoreCase(view) && client.player != null) {
            String uuid = client.player.getUuid().toString();
            tasks = tasks.stream()
                    .filter(t -> {
                        String assignee = t.getAssigneeUuid();
                        return assignee != null && assignee.equals(uuid);
                    })
                    .collect(Collectors.toList());
        } else if ("TEAM_UNASSIGNED".equalsIgnoreCase(view)) {
            tasks = tasks.stream()
                    .filter(t -> t.getAssigneeUuid() == null || t.getAssigneeUuid().isEmpty())
                    .collect(Collectors.toList());
        }
        return tasks;
    }

    private List<Task> getDisplayTasks(List<Task> sourceTasks) {
        boolean sortByPriority = true;
        boolean showCompleted = ModConfig.getInstance().isShowCompletedTasks();

        List<Task> tasks;
        if (showCompleted) {
            tasks = sourceTasks;
        } else {
            tasks = sourceTasks.stream()
                    .filter(t -> !t.isCompleted())
                    .collect(Collectors.toList());
        }

        if (sortByPriority) {
            // Sort by priority (HIGH > MEDIUM > LOW)
            tasks = tasks.stream()
                    .sorted((a, b) -> b.getPriority().ordinal() - a.getPriority().ordinal())
                    .collect(Collectors.toList());
        }

        return tasks;
    }

    private Task.Priority getHighestPriority(List<Task> baseTasks) {
        List<Task> incompleteTasks = baseTasks.stream()
                .filter(t -> !t.isCompleted())
                .collect(Collectors.toList());
        if (incompleteTasks.isEmpty()) {
            return Task.Priority.LOW;
        }

        Task.Priority highest = Task.Priority.LOW;
        for (Task task : incompleteTasks) {
            if (task.getPriority().ordinal() > highest.ordinal()) {
                highest = task.getPriority();
            }
        }
        return highest;
    }

    private String getPriorityIcon(Task.Priority priority) {
        switch (priority) {
            case HIGH:
                return "§c§l" + I18n.translate("hud.todolist.priority.high.icon");
            case MEDIUM:
                return "§e§l" + I18n.translate("hud.todolist.priority.medium.icon");
            case LOW:
                return "§a§l" + I18n.translate("hud.todolist.priority.low.icon");
            default:
                return "§7[?]";
        }
    }

    private int getX(String position) {
        int screenWidth = client.getWindow().getScaledWidth();
        int hudWidth = ModConfig.getInstance().getHudWidth();
        switch (position) {
            case "TOP_LEFT":
                return 5;
            case "TOP_RIGHT":
                return screenWidth - hudWidth - 5;
            case "BOTTOM_LEFT":
                return 5;
            case "BOTTOM_RIGHT":
                return screenWidth - hudWidth - 5;
            default:
                return screenWidth - hudWidth - 5;
        }
    }

    private int getY(String position) {
        int screenHeight = client.getWindow().getScaledHeight();
        switch (position) {
            case "TOP_LEFT":
            case "TOP_RIGHT":
                return 5;
            case "BOTTOM_LEFT":
            case "BOTTOM_RIGHT":
                return screenHeight - 50;
            default:
                return 5;
        }
    }

    /**
     * Toggle HUD expanded state
     */
    public void toggleExpanded() {
        expanded = !expanded;
        TodoListMod.LOGGER.debug("HUD expanded state: {}", expanded);
    }

    /**
     * Force refresh tasks from storage
     * Called after manual save in GUI
     */
    public void forceRefreshTasks() {
        try {
            List<Task> loadedTasks = taskStorage.loadTasks();
            taskManager = new TaskManager();
            for (Task task : loadedTasks) {
                taskManager.addTask(task);
            }
            lastRefreshTime = System.currentTimeMillis();
            TodoListMod.LOGGER.debug("HUD tasks force refreshed");
        } catch (Exception e) {
            TodoListMod.LOGGER.error("Failed to force refresh tasks for HUD", e);
        }
    }

    /**
     * Refresh tasks from storage periodically
     */
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL_MS = 5000; // Refresh every 5 seconds

    private void refreshTasksIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshTime > REFRESH_INTERVAL_MS) {
            try {
                List<Task> loadedTasks = taskStorage.loadTasks();
                taskManager = new TaskManager();
                for (Task task : loadedTasks) {
                    taskManager.addTask(task);
                }
                lastRefreshTime = currentTime;
            } catch (Exception e) {
                TodoListMod.LOGGER.error("Failed to refresh tasks for HUD", e);
            }
        }
    }
}
