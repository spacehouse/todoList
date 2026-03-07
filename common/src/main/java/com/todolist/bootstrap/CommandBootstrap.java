package com.todolist.bootstrap;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.network.TaskPackets;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import com.todolist.project.ProjectSaveDebouncer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 注册服务端命令入口，提供 todo/todolist 命令及其子命令。
 */
public final class CommandBootstrap {
    private static final int COMMAND_SUCCESS = 1;
    private static final int COMMAND_FAILURE = 0;
    private static final int TASK_LIST_MAX_SUMMARY = 10;
    private static final int PROJECT_LIST_MAX_SUMMARY = 10;
    private static final String PERMISSION_DENIED_TRANSLATION_KEY = "command.todolist.permission_denied";
    private static final String COMMAND_RESULT_MESSAGE_KEY_NONE = "command.todolist.result.none";
    private static final String COMMAND_RESULT_LOG_MARKER = "COMMAND_RESULT_TRIPLET";
    private static final CommandSideEffect[] SIDE_EFFECT_NONE = {CommandSideEffect.NONE};
    private static final CommandSideEffect[] SIDE_EFFECT_PERSIST_DATA = {CommandSideEffect.PERSIST_DATA};
    private static final CommandSideEffect[] SIDE_EFFECT_REFRESH_HUD = {CommandSideEffect.REFRESH_HUD};
    private static final CommandSideEffect[] SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD = {
            CommandSideEffect.PERSIST_DATA,
            CommandSideEffect.REFRESH_HUD
    };

    /**
     * 命令入口权限语义，用于将命令能力与统一权限检查策略绑定。
     */
    private enum CommandPermissionSemantic {
        VIEW,
        EDIT,
        PROJECT_ADMIN,
        HUD_CONTROL,
        ADMIN
    }

    /**
     * 命令成功后的副作用语义，用于统一声明写盘、HUD 刷新等后续动作。
     */
    private enum CommandSideEffect {
        NONE,
        PERSIST_DATA,
        REFRESH_HUD
    }

    private static final long CLEAR_CONFIRM_WINDOW_MILLIS = 15_000L;
    private static final ConcurrentHashMap<String, Long> pendingTaskClearConfirmMap = new ConcurrentHashMap<>();

    /**
     * 私有构造函数，避免工具类被实例化。
     */
    private CommandBootstrap() {
    }

    @SuppressWarnings("unchecked")
    public static void registerReflective(Object dispatcher, Object registryAccess, Object environment) {
        if (!(dispatcher instanceof CommandDispatcher<?> rawDispatcher)
                || !(registryAccess instanceof CommandBuildContext rawRegistryAccess)
                || !(environment instanceof Commands.CommandSelection rawEnvironment)) {
            return;
        }
        register((CommandDispatcher<CommandSourceStack>) rawDispatcher, rawRegistryAccess, rawEnvironment);
    }

    /**
     * 注册命令树，并保留 /todolist 到 /todo 的别名重定向。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> todoRoot = Commands.literal("todo")
                .then(Commands.literal("help")
                        .executes(ctx -> sendHelp(ctx.getSource())))
                .then(Commands.literal("task")
                        .executes(ctx -> sendUnimplemented(ctx.getSource(), "task"))
                        .then(Commands.literal("list")
                                .executes(ctx -> sendTaskList(ctx.getSource(), "all", "all", null))
                                .then(Commands.argument("status", StringArgumentType.word())
                                        .executes(ctx -> sendTaskList(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "status"),
                                                "all",
                                                null
                                        ))
                                        .then(Commands.argument("priority", StringArgumentType.word())
                                                .executes(ctx -> sendTaskList(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "status"),
                                                        StringArgumentType.getString(ctx, "priority"),
                                                        null
                                                ))
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(ctx -> sendTaskList(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "status"),
                                                                StringArgumentType.getString(ctx, "priority"),
                                                                StringArgumentType.getString(ctx, "text")
                                                        ))))))
                        .then(Commands.literal("add")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("title", StringArgumentType.string())
                                        .executes(ctx -> executeTaskAdd(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "title"),
                                                null,
                                                null
                                        ))
                                        .then(Commands.argument("description", StringArgumentType.string())
                                                .executes(ctx -> executeTaskAdd(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "title"),
                                                        StringArgumentType.getString(ctx, "description"),
                                                        null
                                                ))
                                                .then(Commands.argument("tags", StringArgumentType.greedyString())
                                                        .executes(ctx -> executeTaskAdd(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "title"),
                                                                StringArgumentType.getString(ctx, "description"),
                                                                StringArgumentType.getString(ctx, "tags")
                                                        ))))))
                        .then(Commands.literal("addp")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestBindableProjectIds)
                                        .then(Commands.argument("title", StringArgumentType.string())
                                                .executes(ctx -> executeTaskAddWithProject(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "title"),
                                                        null,
                                                        null
                                                ))
                                                .then(Commands.argument("description", StringArgumentType.string())
                                                        .executes(ctx -> executeTaskAddWithProject(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "projectId"),
                                                                StringArgumentType.getString(ctx, "title"),
                                                                StringArgumentType.getString(ctx, "description"),
                                                                null
                                                        ))
                                                        .then(Commands.argument("tags", StringArgumentType.greedyString())
                                                                .executes(ctx -> executeTaskAddWithProject(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "projectId"),
                                                                        StringArgumentType.getString(ctx, "title"),
                                                                        StringArgumentType.getString(ctx, "description"),
                                                                        StringArgumentType.getString(ctx, "tags")
                                                                )))))))
                        .then(Commands.literal("clear")
                                .executes(ctx -> requestTaskClearConfirm(ctx.getSource(), "/todo task clear confirm"))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> executeTaskClearConfirm(ctx.getSource()))))
                        .then(Commands.literal("clean")
                                .executes(ctx -> requestTaskClearConfirm(ctx.getSource(), "/todo task clean confirm"))
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> executeTaskClearConfirm(ctx.getSource()))))
                        .then(Commands.literal("done")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("taskId", StringArgumentType.word())
                                        .executes(ctx -> executeTaskDone(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "taskId")
                                        ))))
                        .then(Commands.literal("remove")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("taskId", StringArgumentType.word())
                                        .executes(ctx -> executeTaskRemove(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "taskId")
                                        ))))
                )
                        
                .then(Commands.literal("project")
                        .executes(ctx -> sendUnimplemented(ctx.getSource(), "project"))
                        .then(Commands.literal("list")
                                .executes(ctx -> sendProjectList(ctx.getSource())))
                        .then(Commands.literal("current")
                                .executes(ctx -> sendCurrentProjectByTaskStats(ctx.getSource()))))
                .then(Commands.literal("hud")
                        .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL, null))
                        .executes(ctx -> sendHudStatus(ctx.getSource()))
                        .then(Commands.literal("status")
                                .executes(ctx -> sendHudStatus(ctx.getSource())))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleHudStatus(ctx.getSource()))))
                .then(Commands.literal("join")
                        .then(buildJoinDecisionLiteral("accept", true))
                        .then(buildJoinDecisionLiteral("deny", false)));

        var todoRootNode = dispatcher.register(todoRoot);
        dispatcher.register(Commands.literal("todolist").redirect(todoRootNode));
    }

    /**
     * 构建 join 审批子命令（accept/deny），复用同一套处理逻辑。
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildJoinDecisionLiteral(String literal, boolean approved) {
        return Commands.literal(literal)
                .then(Commands.argument("projectId", StringArgumentType.word())
                        .then(Commands.argument("applicantUuid", StringArgumentType.word())
                                .executes(ctx -> executeJoinDecision(ctx.getSource(), ctx, approved))));
    }

    /**
     * 执行加入申请审批逻辑，并根据 approved 决定通过或拒绝。
     */
    private static int executeJoinDecision(CommandSourceStack source, CommandContext<CommandSourceStack> ctx, boolean approved) {
        ServerPlayer approver = getPlayerIfPresent(source);
        if (approver == null) {
            return COMMAND_FAILURE;
        }
        String projectId = StringArgumentType.getString(ctx, "projectId");
        if (ensureCommandPermission(source, CommandPermissionSemantic.PROJECT_ADMIN, projectId) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        String applicantUuid = StringArgumentType.getString(ctx, "applicantUuid");
        if (!handleJoinDecision(source.getServer(), approver, projectId, applicantUuid, approved)) {
            return COMMAND_FAILURE;
        }
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                approved ? "command.todolist.join.accept.success" : "command.todolist.join.deny.success"
        );
    }

    private static boolean handleJoinDecision(MinecraftServer server, ServerPlayer approver, String projectId, String applicantUuid, boolean accepted) {
        return ProjectPackets.handleJoinDecision(server, approver, projectId, applicantUuid, accepted);
    }

    /**
     * 输出 todo 命令帮助信息，展示当前可用命令。
     */
    private static int sendHelp(CommandSourceStack source) {
        sendFeedbackBatchByTranslationKeys(
                source,
                "command.todolist.help.title",
                "command.todolist.help.todo_help",
                "command.todolist.help.todo_task",
                "command.todolist.help.todo_task_list",
                "command.todolist.help.todo_task_add",
                "command.todolist.help.todo_task_addp",
                "command.todolist.help.todo_task_clear",
                "command.todolist.help.todo_task_clean",
                "command.todolist.help.todo_task_done",
                "command.todolist.help.todo_task_remove",
                "command.todolist.help.todo_project",
                "command.todolist.help.todo_project_list",
                "command.todolist.help.todo_project_current",
                "command.todolist.help.todo_hud",
                "command.todolist.help.todo_hud_status",
                "command.todolist.help.todo_hud_toggle",
                "command.todolist.help.todo_join_accept",
                "command.todolist.help.todo_join_deny",
                "command.todolist.help.alias"
        );
        ServerPlayer player = getPlayerOrNull(source);
        if (player != null && source.getServer() != null) {
            source.getServer().getCommands().sendCommands(player);
        }
        return sendCommandSuccess(source, Command.SINGLE_SUCCESS, SIDE_EFFECT_NONE);
    }

    /**
     * 输出 HUD 开关状态，便于玩家确认当前配置值。
     */
    private static int sendHudStatus(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        boolean enabled = ModConfig.getInstance().isEnableHud();
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_NONE,
                "command.todolist.hud.status",
                getHudSwitchText(enabled)
        );
    }

    /**
     * 切换 HUD 开关状态并立即写回配置文件。
     */
    private static int toggleHudStatus(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ModConfig config = ModConfig.getInstance();
        boolean nextEnabled = !config.isEnableHud();
        config.setEnableHud(nextEnabled);
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_REFRESH_HUD,
                "command.todolist.hud.toggle",
                getHudSwitchText(nextEnabled)
        );
    }

    /**
     * 返回 HUD 开关状态文案（开/关），由语言文件统一翻译。
     */
    private static Component getHudSwitchText(boolean enabled) {
        return Component.translatable(enabled ? "command.todolist.hud.enabled" : "command.todolist.hud.disabled");
    }

    /**
     * 读取当前玩家任务并输出统计信息与最多 10 条摘要。
     * 支持 all/todo/done 过滤参数。
     */
    private static int sendTaskList(CommandSourceStack source, String status, String priority, String text) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();

        try {
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            int totalCount = tasks.size();
            int completedCount = (int) tasks.stream().filter(Task::isCompleted).count();

            List<Task> filteredTasks = tasks.stream()
                    .filter(task -> matchesTaskStatus(task, status))
                    .filter(task -> matchesTaskPriority(task, priority))
                    .filter(task -> matchesTaskText(task, text))
                    .sorted(buildTaskSummaryComparator())
                    .toList();

            return sendListWithUnifiedTemplate(
                    source,
                    filteredTasks,
                    TASK_LIST_MAX_SUMMARY,
                    () -> Component.translatable("command.todolist.task.list.summary", totalCount, completedCount),
                    "command.todolist.task.list.empty",
                    (displayIndex, task) -> Component.translatable(
                            "command.todolist.task.list.item",
                            displayIndex,
                            task.isCompleted() ? "✓" : "✗",
                            task.getTitle()
                    ),
                    "command.todolist.task.list.more"
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load player task list for command", e);
            return sendCommandFailure(source, "command.todolist.task.list.failed");
        }
    }

    /**
     * 为当前玩家添加一条新任务并写回存储。
     */
    private static int executeTaskAdd(CommandSourceStack source, String title, String description, String tags) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            String normalizedTitle = title == null ? "" : title.trim();
            if (normalizedTitle.isEmpty()) {
                return sendCommandFailure(source, "command.todolist.task.add.invalid_title");
            }
            String normalizedDescription = description == null ? "" : description.trim();
            Task newTask = new Task(normalizedTitle, normalizedDescription);
            Set<String> parsedTags = parseTagSet(tags);
            if (!parsedTags.isEmpty()) {
                newTask.setTags(parsedTags);
            }
            newTask.setCreatorUuid(playerUuid.toString());
            String resolvedProjectId = resolveProjectIdForCommandTaskAdd(source, player);
            if (resolvedProjectId != null && !resolvedProjectId.isBlank()) {
                newTask.setProjectId(resolvedProjectId);
            }
            tasks.add(newTask);
            storage.savePlayerTasks(playerUuid, tasks);
            syncTasksToPlayer(source.getServer(), player);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.add.success",
                    normalizedTitle
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to add task for command", e);
            return sendCommandFailure(source, "command.todolist.task.add.failed");
        }
    }

    /**
     * 为 /todo task add 解析默认 projectId：优先使用客户端上报的激活项目，其次回退到默认个人项目。
     */
    private static String resolveProjectIdForCommandTaskAdd(CommandSourceStack source, ServerPlayer player) {
        if (player == null) {
            return null;
        }
        String activeProjectId = ProjectPackets.getActiveProjectId(player);
        if (activeProjectId != null && !activeProjectId.isBlank()) {
            Project active = TodoListCommon.getProjectManager().getProject(activeProjectId);
            if (active != null && active.getScope() == Project.Scope.PERSONAL && isProjectBindableForPlayer(activeProjectId, player)) {
                return activeProjectId;
            }
        }
        String defaultId = ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID;
        Project def = TodoListCommon.getProjectManager().getProject(defaultId);
        if (def != null && def.getScope() == Project.Scope.PERSONAL && isProjectBindableForPlayer(defaultId, player)) {
            return defaultId;
        }
        return null;
    }

    private static Set<String> parseTagSet(String tags) {
        if (tags == null || tags.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 将当前玩家个人任务列表同步到客户端，保证命令写盘后界面能立即刷新。
     */
    private static void syncTasksToPlayer(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        TaskPackets.syncTasksToPlayer(player);
    }

    private static boolean matchesTaskStatus(Task task, String status) {
        String normalizedStatus = status == null ? "all" : status.toLowerCase(Locale.ROOT);
        if ("all".equals(normalizedStatus)) {
            return true;
        }
        if ("todo".equals(normalizedStatus) || "incomplete".equals(normalizedStatus)) {
            return !task.isCompleted();
        }
        if ("done".equals(normalizedStatus) || "completed".equals(normalizedStatus)) {
            return task.isCompleted();
        }
        return false;
    }

    private static boolean matchesTaskPriority(Task task, String priority) {
        String normalizedPriority = priority == null ? "all" : priority.toLowerCase(Locale.ROOT);
        if ("all".equals(normalizedPriority)) {
            return true;
        }
        return switch (normalizedPriority) {
            case "low" -> task.getPriority() == Task.Priority.LOW;
            case "medium" -> task.getPriority() == Task.Priority.MEDIUM;
            case "high" -> task.getPriority() == Task.Priority.HIGH;
            default -> false;
        };
    }

    private static boolean matchesTaskText(Task task, String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        if (task.getTitle() != null && task.getTitle().toLowerCase(Locale.ROOT).contains(normalizedText)) {
            return true;
        }
        if (task.getDescription() != null && task.getDescription().toLowerCase(Locale.ROOT).contains(normalizedText)) {
            return true;
        }
        return task.getTags().stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(normalizedText));
    }

    /**
     * 请求任务清空确认：首次执行仅提示确认入口，不做实际清空。
     */
    private static int requestTaskClearConfirm(CommandSourceStack source, String confirmCommand) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        pendingTaskClearConfirmMap.put(player.getStringUUID(), System.currentTimeMillis());
        return sendTaskClearConfirmHint(source, confirmCommand);
    }

    /**
     * 执行任务清空确认：仅在确认窗口内才会实际清空任务。
     */
    private static int executeTaskClearConfirm(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String uuid = player.getStringUUID();
        Long issuedAt = pendingTaskClearConfirmMap.get(uuid);
        if (issuedAt == null || System.currentTimeMillis() - issuedAt > CLEAR_CONFIRM_WINDOW_MILLIS) {
            pendingTaskClearConfirmMap.remove(uuid);
            return sendCommandFailure(source, "command.todolist.task.clear.confirm_expired");
        }
        pendingTaskClearConfirmMap.remove(uuid);
        return executeTaskClearNow(source, player);
    }

    /**
     * 清空当前玩家所有任务并写回存储。
     */
    private static int executeTaskClearNow(CommandSourceStack source, ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.savePlayerTasks(playerUuid, List.of());
            syncTasksToPlayer(source.getServer(), player);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.clear.success"
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to clear tasks for command", e);
            return sendCommandFailure(source, "command.todolist.task.clear.failed");
        }
    }

    /**
     * 输出二次确认提示，并提供可点击的 confirm 命令。
     */
    private static int sendTaskClearConfirmHint(CommandSourceStack source, String confirmCommand) {
        MutableComponent button = Component.translatable("command.todolist.task.clear.confirm_button")
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, confirmCommand)));
        sendFeedback(source, () -> Component.translatable("command.todolist.task.clear.confirm_hint", button));
        return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
    }

    /**
     * 输出当前玩家可见项目摘要，包含总数与最多 10 条项目信息。
     */
    private static int sendProjectList(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String playerUuid = player.getStringUUID();
        ProjectManager projectManager = TodoListCommon.getProjectManager();

        List<Project> visibleProjects = projectManager.getAllProjects().stream()
                .filter(project -> isProjectVisibleToPlayer(project, playerUuid))
                .sorted(buildProjectSummaryComparator())
                .toList();

        int totalCount = visibleProjects.size();
        return sendListWithUnifiedTemplate(
                source,
                visibleProjects,
                PROJECT_LIST_MAX_SUMMARY,
                () -> Component.translatable("command.todolist.project.list.summary", totalCount),
                "command.todolist.project.list.empty",
                (displayIndex, project) -> Component.translatable(
                        "command.todolist.project.list.item",
                        displayIndex,
                        getProjectScopeText(project),
                        getProjectDisplayName(project, project.getId()),
                        project.getId()
                ),
                "command.todolist.project.list.more"
        );
    }

    /**
     * 基于当前玩家任务的 projectId 频次，输出最常用项目作为当前项目。
     */
    private static int sendCurrentProjectByTaskStats(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            Map<String, Long> projectTaskCountMap = tasks.stream()
                    .map(Task::getProjectId)
                    .filter(projectId -> projectId != null && !projectId.isBlank())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            if (projectTaskCountMap.isEmpty()) {
                return sendCommandSuccess(
                        source,
                        COMMAND_SUCCESS,
                        SIDE_EFFECT_NONE,
                        "command.todolist.project.current.empty"
                );
            }

            Optional<Map.Entry<String, Long>> mostUsedProject = projectTaskCountMap.entrySet().stream()
                    .max(Map.Entry.<String, Long>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())));
            if (mostUsedProject.isEmpty()) {
                return sendCommandSuccess(
                        source,
                        COMMAND_SUCCESS,
                        SIDE_EFFECT_NONE,
                        "command.todolist.project.current.empty"
                );
            }

            String projectId = mostUsedProject.get().getKey();
            long taskCount = mostUsedProject.get().getValue();
            Project project = TodoListCommon.getProjectManager().getProject(projectId);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_NONE,
                    "command.todolist.project.current.result",
                    getProjectDisplayName(project, projectId),
                    projectId,
                    taskCount
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to resolve current project by task stats for command", e);
            return sendCommandFailure(source, "command.todolist.project.current.failed");
        }
    }

    /**
     * 判断项目是否应出现在玩家项目列表中。
     */
    private static boolean isProjectVisibleToPlayer(Project project, String playerUuid) {
        if (project == null) {
            return false;
        }
        if (project.getScope() == Project.Scope.TEAM) {
            return true;
        }
        String ownerUuid = project.getOwnerUuid();
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return true;
        }
        return ownerUuid.equals(playerUuid) || project.getMemberRole(playerUuid) != null;
    }

    /**
     * 构建项目摘要排序规则：个人项目优先，其次按创建时间倒序和名称升序。
     */
    private static Comparator<Project> buildProjectSummaryComparator() {
        return Comparator.comparing((Project project) -> project.getScope() == Project.Scope.TEAM)
                .thenComparing(Project::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(project -> {
                    String name = project.getName();
                    return name == null ? "" : name;
                });
    }

    /**
     * 获取项目范围的本地化文本（个人/团队）。
     */
    private static Component getProjectScopeText(Project project) {
        if (project == null || project.getScope() == Project.Scope.PERSONAL) {
            return Component.translatable("gui.todolist.scope.personal");
        }
        return Component.translatable("gui.todolist.scope.team");
    }

    /**
     * 获取项目展示名称：优先使用项目名，其次回退到 projectId。
     */
    private static Component getProjectDisplayName(Project project, String fallbackProjectId) {
        if (project == null) {
            return fallbackProjectId == null ? Component.empty() : Component.literal(fallbackProjectId);
        }
        String name = project.getName();
        if (name == null || name.isBlank()) {
            return fallbackProjectId == null ? Component.empty() : Component.literal(fallbackProjectId);
        }
        if (name.startsWith("gui.todolist.") || name.startsWith("item.") || name.startsWith("block.")) {
            return Component.translatable(name);
        }
        return Component.literal(name);
    }

    /**
     * 将当前玩家指定任务标记为已完成并写回存储。
     */
    private static int executeTaskDone(CommandSourceStack source, String taskId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            Task task = findTaskById(tasks, taskId);
            if (task == null) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }

            if (task.isCompleted()) {
                return sendCommandSuccess(
                        source,
                        COMMAND_SUCCESS,
                        SIDE_EFFECT_NONE,
                        "command.todolist.task.done.already_completed",
                        task.getTitle()
                );
            }

            task.setCompleted(true);
            storage.savePlayerTasks(playerUuid, tasks);
            syncTasksToPlayer(source.getServer(), player);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.done.success",
                    task.getTitle()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to mark task as done for command", e);
            return sendCommandFailure(source, "command.todolist.task.done.failed");
        }
    }

    /**
     * 从当前玩家任务列表删除指定任务并写回存储。
     */
    private static int executeTaskRemove(CommandSourceStack source, String taskId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            Task task = findTaskById(tasks, taskId);
            if (task == null) {
                return sendCommandFailure(source, "command.todolist.task.remove.not_found", taskId);
            }

            tasks.remove(task);
            storage.savePlayerTasks(playerUuid, tasks);
            syncTasksToPlayer(source.getServer(), player);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.remove.success",
                    task.getTitle()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to remove task for command", e);
            return sendCommandFailure(source, "command.todolist.task.remove.failed");
        }
    }

    /**
     * 构建任务摘要排序规则：未完成优先，其次按优先级和创建时间排序。
     */
    private static Comparator<Task> buildTaskSummaryComparator() {
        return Comparator.comparing(Task::isCompleted)
                .thenComparing((Task task) -> task.getPriority().ordinal(), Comparator.reverseOrder())
                .thenComparing(Task::getCreatedAt, Comparator.reverseOrder());
    }

    /**
     * 按任务 ID 在任务列表中查找任务对象。
     */
    private static Task findTaskById(List<Task> tasks, String taskId) {
        for (Task task : tasks) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    /**
     * 检测命令来源是否为玩家；若不是玩家则返回统一的本地化错误提示。
     */
    private static ServerPlayer getPlayerIfPresent(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        sendCommandFailure(source, "command.todolist.not_player_context");
        return null;
    }

    /**
     * 输出一级子命令占位提示，用于标记该能力尚未实现。
     */
    private static int sendUnimplemented(CommandSourceStack source, String subcommand) {
        if ("task".equals(subcommand)) {
            if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
                return COMMAND_FAILURE;
            }
        } else if ("project".equals(subcommand)) {
            if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
                return COMMAND_FAILURE;
            }
        } else if ("join".equals(subcommand)) {
            if (ensureCommandPermission(source, CommandPermissionSemantic.PROJECT_ADMIN) == COMMAND_FAILURE) {
                return COMMAND_FAILURE;
            }
        }
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_NONE,
                "command.todolist.unimplemented",
                subcommand
        );
    }

    /**
     * 统一校验命令语义权限并在失败时返回标准拒绝消息。
     */
    private static int ensureCommandPermission(CommandSourceStack source, CommandPermissionSemantic semantic) {
        return ensureCommandPermission(source, semantic, null);
    }

    /**
     * 统一校验命令语义权限（支持 projectId 上下文）并在失败时返回标准拒绝消息。
     */
    private static int ensureCommandPermission(CommandSourceStack source, CommandPermissionSemantic semantic, String projectId) {
        if (hasCommandPermission(source, semantic, projectId)) {
            return COMMAND_SUCCESS;
        }
        return sendCommandFailure(
                source,
                PERMISSION_DENIED_TRANSLATION_KEY,
                Component.translatable(getCommandPermissionTranslationKey(semantic))
        );
    }

    /**
     * 根据语义和上下文判断命令来源是否具备执行权限。
     */
    private static boolean hasCommandPermission(CommandSourceStack source, CommandPermissionSemantic semantic, String projectId) {
        if (semantic == CommandPermissionSemantic.ADMIN) {
            return source.hasPermission(2);
        }

        ModConfig.CommandAccessMode accessMode = ModConfig.getInstance().getCommandAccessMode();
        boolean op = source.hasPermission(2);
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return false;
        }

        if (semantic == CommandPermissionSemantic.PROJECT_ADMIN) {
            return hasProjectAdminPermission(source, player, projectId);
        }
        if (semantic == CommandPermissionSemantic.HUD_CONTROL) {
            return op;
        }
        if (semantic == CommandPermissionSemantic.VIEW) {
            return accessMode != ModConfig.CommandAccessMode.OP_ONLY || op;
        }
        if (semantic == CommandPermissionSemantic.EDIT) {
            if (accessMode == ModConfig.CommandAccessMode.FULL) {
                return true;
            }
            return op;
        }
        return false;
    }

    /**
     * 为当前玩家添加一条新任务（关联指定项目）并写回存储。
     */
    private static int executeTaskAddWithProject(CommandSourceStack source, String projectId, String title, String description, String tags) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.task.add.invalid_project");
        }
        if (!isProjectBindableForPlayer(normalizedProjectId, player)) {
            return sendCommandFailure(source, "command.todolist.task.add.invalid_project");
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            String normalizedTitle = title == null ? "" : title.trim();
            if (normalizedTitle.isEmpty()) {
                return sendCommandFailure(source, "command.todolist.task.add.invalid_title");
            }
            String normalizedDescription = description == null ? "" : description.trim();
            Task newTask = new Task(normalizedTitle, normalizedDescription);
            Set<String> parsedTags = parseTagSet(tags);
            if (!parsedTags.isEmpty()) {
                newTask.setTags(parsedTags);
            }
            newTask.setCreatorUuid(playerUuid.toString());
            newTask.setProjectId(normalizedProjectId);
            tasks.add(newTask);
            storage.savePlayerTasks(playerUuid, tasks);
            syncTasksToPlayer(source.getServer(), player);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.add.success",
                    normalizedTitle
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to add task with project for command", e);
            return sendCommandFailure(source, "command.todolist.task.add.failed");
        }
    }

    /**
     * 判断指定项目是否允许被当前玩家用于任务关联。
     */
    private static boolean isProjectBindableForPlayer(String projectId, ServerPlayer player) {
        if (player == null || projectId == null || projectId.isBlank()) {
            return false;
        }
        Project project = TodoListCommon.getProjectManager().getProject(projectId);
        if (project == null) {
            return false;
        }
        String playerUuid = player.getStringUUID();
        if (project.getScope() == Project.Scope.PERSONAL) {
            String ownerUuid = project.getOwnerUuid();
            return ownerUuid == null || ownerUuid.isEmpty() || ownerUuid.equals(playerUuid);
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        if (playerUuid.equals(project.getOwnerUuid())) {
            return true;
        }
        return project.getMemberRole(playerUuid) != null;
    }

    /**
     * 为 addp 命令的 projectId 参数提供自动补全，只返回玩家权限范围内可关联的项目 ID。
     */
    private static CompletableFuture<Suggestions> suggestBindableProjectIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return builder.buildFuture();
        }
        ProjectManager projectManager = TodoListCommon.getProjectManager();
        for (Project project : projectManager.getAllProjects()) {
            if (project == null) {
                continue;
            }
            String projectId = project.getId();
            if (projectId == null || projectId.isBlank()) {
                continue;
            }
            if (!isProjectBindableForPlayer(projectId, player)) {
                continue;
            }
            builder.suggest(projectId);
        }
        return builder.buildFuture();
    }

    /**
     * 尝试提取命令来源玩家，不触发任何提示输出。
     */
    private static ServerPlayer getPlayerOrNull(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    /**
     * 判断玩家是否具备团队项目管理员语义权限（项目经理/负责人/OP）。
     */
    private static boolean hasProjectAdminPermission(CommandSourceStack source, ServerPlayer player, String projectId) {
        if (source.hasPermission(2)) {
            return true;
        }
        if (projectId == null || projectId.isBlank()) {
            return false;
        }
        Project project = TodoListCommon.getProjectManager().getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return true;
        }

        Role role = resolveProjectRole(player, project);
        boolean projectMember = isTeamProjectMember(player, project);
        Context context = new Context(ViewScope.TEAM_ALL, false, false, false, false, false, projectMember);
        return PermissionCenter.canPerform(Operation.ADD_MEMBER, role, context);
    }

    /**
     * 解析玩家在指定项目中的权限角色（与项目权限中心角色模型对齐）。
     */
    private static Role resolveProjectRole(ServerPlayer player, Project project) {
        if (player == null) {
            return Role.MEMBER;
        }
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return Role.MEMBER;
        }
        String playerUuid = player.getStringUUID();
        if (playerUuid.equals(project.getOwnerUuid())) {
            return Role.PROJECT_MANAGER;
        }
        Project.ProjectRole memberRole = project.getMemberRole(playerUuid);
        if (memberRole == Project.ProjectRole.LEAD) {
            return Role.LEAD;
        }
        return Role.MEMBER;
    }

    /**
     * 判断玩家是否属于团队项目成员（含项目经理与负责人）。
     */
    private static boolean isTeamProjectMember(ServerPlayer player, Project project) {
        if (player == null || project == null || project.getScope() != Project.Scope.TEAM) {
            return false;
        }
        String playerUuid = player.getStringUUID();
        if (playerUuid.equals(project.getOwnerUuid())) {
            return true;
        }
        return project.getMemberRole(playerUuid) != null;
    }

    /**
     * 将权限语义映射为本地化翻译键，用于统一拒绝提示。
     */
    private static String getCommandPermissionTranslationKey(CommandPermissionSemantic semantic) {
        return "command.todolist.permission." + semantic.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 使用统一模板输出列表：先 summary，再 empty/items，最后 more。
     */
    private static <T> int sendListWithUnifiedTemplate(
            CommandSourceStack source,
            List<T> items,
            int maxSummaryCount,
            Supplier<Component> summaryTextSupplier,
            String emptyTranslationKey,
            BiFunction<Integer, T, Component> itemTextFactory,
            String moreTranslationKey
    ) {
        sendFeedback(source, summaryTextSupplier);
        if (items.isEmpty()) {
            sendFeedbackByTranslationKey(source, emptyTranslationKey);
            return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
        }

        int totalCount = items.size();
        int summaryCount = Math.min(totalCount, maxSummaryCount);
        for (int index = 0; index < summaryCount; index++) {
            int displayIndex = index + 1;
            T item = items.get(index);
            sendFeedback(source, () -> itemTextFactory.apply(displayIndex, item));
        }

        if (totalCount > maxSummaryCount) {
            sendFeedbackByTranslationKey(source, moreTranslationKey, totalCount - maxSummaryCount);
        }
        return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
    }

    /**
     * 使用统一入口发送普通反馈消息（不广播给管理员）。
     */
    private static void sendFeedback(CommandSourceStack source, Supplier<Component> textSupplier) {
        source.sendSuccess(textSupplier, false);
    }

    /**
     * 通过翻译键构造消息并发送到统一反馈入口。
     */
    private static void sendFeedbackByTranslationKey(CommandSourceStack source, String translationKey, Object... args) {
        sendFeedback(source, () -> Component.translatable(translationKey, args));
    }

    /**
     * 批量按顺序发送翻译键消息，保证传入顺序与输出顺序一致。
     */
    private static void sendFeedbackBatchByTranslationKeys(CommandSourceStack source, String... translationKeys) {
        for (String translationKey : translationKeys) {
            sendFeedbackByTranslationKey(source, translationKey);
        }
    }

    /**
     * 使用统一入口发送错误消息。
     */
    private static void sendError(CommandSourceStack source, Supplier<Component> textSupplier) {
        source.sendFailure(textSupplier.get());
    }

    /**
     * 通过翻译键构造错误消息并发送到统一错误入口。
     */
    private static void sendErrorByTranslationKey(CommandSourceStack source, String translationKey, Object... args) {
        sendError(source, () -> Component.translatable(translationKey, args));
    }

    /**
     * 批量按顺序发送翻译键错误消息，保证传入顺序与输出顺序一致。
     */
    private static void sendErrorBatchByTranslationKeys(CommandSourceStack source, String... translationKeys) {
        for (String translationKey : translationKeys) {
            sendErrorByTranslationKey(source, translationKey);
        }
    }

    /**
     * 发送命令成功反馈并返回统一成功状态码，同时显式声明副作用。
     */
    private static int sendCommandSuccess(
            CommandSourceStack source,
            int code,
            CommandSideEffect[] sideEffects,
            String translationKey,
            Object... args
    ) {
        sendFeedbackByTranslationKey(source, translationKey, args);
        return sendCommandSuccess(source, code, translationKey, sideEffects);
    }

    /**
     * 发送无额外消息体的命令成功结果，并显式声明副作用。
     */
    private static int sendCommandSuccess(CommandSourceStack source, int code, CommandSideEffect... sideEffects) {
        return sendCommandSuccess(source, code, COMMAND_RESULT_MESSAGE_KEY_NONE, sideEffects);
    }

    /**
     * 发送无额外消息体的命令成功结果，并沉淀可比对三元组日志。
     */
    private static int sendCommandSuccess(
            CommandSourceStack source,
            int code,
            String messageKey,
            CommandSideEffect... sideEffects
    ) {
        CommandSideEffect[] normalizedEffects = normalizeCommandSideEffects(sideEffects);
        logCommandResultTriplet(code, messageKey, normalizedEffects);
        return code;
    }

    /**
     * 将副作用语义映射为本地化翻译键，便于双端统一展示与调试。
     */
    private static String getCommandSideEffectTranslationKey(CommandSideEffect sideEffect) {
        return "command.todolist.side_effect." + sideEffect.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化副作用列表：空集合回退 NONE，且在存在真实副作用时移除 NONE。
     */
    private static CommandSideEffect[] normalizeCommandSideEffects(CommandSideEffect... sideEffects) {
        if (sideEffects == null || sideEffects.length == 0) {
            return SIDE_EFFECT_NONE;
        }
        EnumSet<CommandSideEffect> normalized = EnumSet.noneOf(CommandSideEffect.class);
        for (CommandSideEffect sideEffect : sideEffects) {
            if (sideEffect != null) {
                normalized.add(sideEffect);
            }
        }
        if (normalized.isEmpty()) {
            return SIDE_EFFECT_NONE;
        }
        if (normalized.size() > 1) {
            normalized.remove(CommandSideEffect.NONE);
        }
        if (normalized.isEmpty()) {
            normalized.add(CommandSideEffect.NONE);
        }
        return normalized.toArray(new CommandSideEffect[0]);
    }

    /**
     * 发送命令失败反馈并返回统一失败状态码。
     */
    private static int sendCommandFailure(CommandSourceStack source, String translationKey, Object... args) {
        sendErrorByTranslationKey(source, translationKey, args);
        logCommandResultTriplet(COMMAND_FAILURE, translationKey, SIDE_EFFECT_NONE);
        return COMMAND_FAILURE;
    }

    /**
     * 统一输出命令结果三元组日志，便于双端自动一致性比对。
     */
    private static void logCommandResultTriplet(int code, String messageKey, CommandSideEffect... sideEffects) {
        CommandSideEffect[] normalizedEffects = normalizeCommandSideEffects(sideEffects);
        String normalizedMessageKey = messageKey == null || messageKey.isBlank()
                ? COMMAND_RESULT_MESSAGE_KEY_NONE
                : messageKey;
        String sideEffectsLogValue = Arrays.stream(normalizedEffects)
                .map(CommandSideEffect::name)
                .collect(Collectors.joining(",", "[", "]"));
        TodoConstants.LOGGER.info(
                "{} code={} messageKey={} sideEffects={}",
                COMMAND_RESULT_LOG_MARKER,
                code,
                normalizedMessageKey,
                sideEffectsLogValue
        );
    }
}
