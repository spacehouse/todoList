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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
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

    private static final long CONFIRM_WINDOW_MILLIS = 15_000L;
    private static final List<String> TASK_LIST_STATUS_SUGGESTIONS = List.of(
            "incomplete", "completed"
    );
    private static final List<String> TASK_LIST_PRIORITY_SUGGESTIONS = List.of(
            "all", "low", "medium", "high"
    );
    private static final List<String> PROJECT_SCOPE_SUGGESTIONS = List.of(
            "personal", "team"
    );
    private static final List<String> PROJECT_LIST_MODE_SUGGESTIONS = List.of(
            "all", "current", "star"
    );
    private static final List<String> TASK_CLEAN_SCOPE_SUGGESTIONS = List.of(
            "personal", "team"
    );
    private static final List<String> TASK_CLEAN_PROJECT_SUGGESTIONS = List.of(
            "current", "star", "all"
    );
    private static final List<String> TASK_CLEAN_STATUS_SUGGESTIONS = List.of(
            "incomplete", "completed"
    );
    private static final ConcurrentHashMap<String, PendingTaskCleanConfirmation> pendingTaskCleanConfirmMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PendingProjectRemoveConfirmation> pendingProjectRemoveConfirmMap = new ConcurrentHashMap<>();

    private static final class PendingTaskCleanConfirmation {
        private final String scope;
        private final String projectSelector;
        private final String status;
        private final List<String> projectIds;
        private final long issuedAt;

        private PendingTaskCleanConfirmation(String scope, String projectSelector, String status, List<String> projectIds, long issuedAt) {
            this.scope = scope;
            this.projectSelector = projectSelector;
            this.status = status;
            this.projectIds = projectIds;
            this.issuedAt = issuedAt;
        }
    }

    private static final class PendingProjectRemoveConfirmation {
        private final String projectId;
        private final long issuedAt;

        private PendingProjectRemoveConfirmation(String projectId, long issuedAt) {
            this.projectId = projectId;
            this.issuedAt = issuedAt;
        }
    }

    private static final class ResolvedTaskCleanRequest {
        private final String scope;
        private final String projectSelector;
        private final String status;
        private final List<String> projectIds;
        private final int affectedTaskCount;
        private final boolean matchAllPersonalTasks;

        private ResolvedTaskCleanRequest(String scope, String projectSelector, String status, List<String> projectIds, int affectedTaskCount, boolean matchAllPersonalTasks) {
            this.scope = scope;
            this.projectSelector = projectSelector;
            this.status = status;
            this.projectIds = projectIds;
            this.affectedTaskCount = affectedTaskCount;
            this.matchAllPersonalTasks = matchAllPersonalTasks;
        }
    }

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
                        .then(Commands.literal("list")
                                .executes(ctx -> sendTaskList(ctx.getSource(), "all", "all", null))
                                .then(Commands.argument("status", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestTaskListStatuses)
                                        .executes(ctx -> sendTaskList(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "status"),
                                                "all",
                                                null
                                        ))
                                        .then(Commands.argument("priority", StringArgumentType.word())
                                                .suggests(CommandBootstrap::suggestTaskListPriorities)
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
                        .then(Commands.literal("clean")
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> executeTaskCleanConfirm(ctx.getSource())))
                                .then(Commands.argument("scope", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestTaskCleanScopes)
                                        .then(Commands.argument("project", StringArgumentType.word())
                                                .suggests(CommandBootstrap::suggestTaskCleanProjects)
                                                .then(Commands.argument("status", StringArgumentType.word())
                                                        .suggests(CommandBootstrap::suggestTaskCleanStatuses)
                                                        .executes(ctx -> requestTaskCleanConfirm(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "scope"),
                                                                StringArgumentType.getString(ctx, "project"),
                                                                StringArgumentType.getString(ctx, "status")
                                                        ))))))
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
                                        )))))
                .then(Commands.literal("project")
                        .then(Commands.literal("create")
                                .then(Commands.argument("scope", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestProjectScopes)
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeProjectCreate(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "scope"),
                                                        StringArgumentType.getString(ctx, "name")
                                                )))))
                        .then(Commands.literal("remove")
                                .then(Commands.literal("confirm")
                                        .executes(ctx -> executeProjectRemoveConfirm(ctx.getSource())))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestRemovableProjectIds)
                                        .executes(ctx -> requestProjectRemoveConfirm(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "projectId")
                                        ))))
                        .then(Commands.literal("select")
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .executes(ctx -> executeProjectSelect(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "projectId")
                                        ))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestProjectListModes)
                                        .executes(ctx -> sendProjectList(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mode")
                                        )))))
                .then(Commands.literal("hud")
                        .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL, null))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleHudStatus(ctx.getSource()))))
                .then(Commands.literal("join")
                        .then(Commands.literal("project")
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestJoinableProjectIds)
                                        .executes(ctx -> executeJoinProject(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "projectId")
                                        ))))
                        .then(buildJoinDecisionLiteral("accept", true))
                        .then(buildJoinDecisionLiteral("deny", false)));

        LiteralCommandNode<CommandSourceStack> todoRootNode = dispatcher.register(todoRoot);
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

    private static int executeJoinProject(CommandSourceStack source, String projectId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            return sendCommandFailure(source, "message.todolist.project.join.invalid_project");
        }
        ProjectPackets.requestJoinProject(source.getServer(), player, normalizedProjectId);
        return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
    }

    /**
     * 输出 todo 命令帮助信息，展示当前可用命令。
     */
    private static int sendHelp(CommandSourceStack source) {
        sendFeedbackBatchByTranslationKeys(
                source,
                "command.todolist.help.title",
                "command.todolist.help.todo_help",
                "command.todolist.help.todo_task_list",
                "command.todolist.help.todo_task_list_options",
                "command.todolist.help.todo_task_add",
                "command.todolist.help.todo_task_addp",
                "command.todolist.help.todo_task_clean",
                "command.todolist.help.todo_task_done",
                "command.todolist.help.todo_task_remove",
                "command.todolist.help.todo_project_create",
                "command.todolist.help.todo_project_remove",
                "command.todolist.help.todo_project_list",
                "command.todolist.help.todo_project_select",
                "command.todolist.help.todo_hud_toggle",
                "command.todolist.help.todo_join_project",
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
    private static int toggleHudStatus(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        boolean nextEnabled = !ProjectPackets.isHudVisible(player);
        ProjectPackets.setHudVisible(player, nextEnabled);
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_NONE,
                "command.todolist.hud.toggle",
                getHudSwitchText(nextEnabled)
        );
    }

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

            String searchText = text == null || text.isBlank() ? null : text.trim();
            return sendListWithUnifiedTemplate(
                    source,
                    filteredTasks,
                    TASK_LIST_MAX_SUMMARY,
                    () -> Component.translatable("command.todolist.task.list.summary", totalCount, completedCount),
                    "command.todolist.task.list.empty",
                    (displayIndex, task) -> buildTaskListItem(displayIndex, task, searchText),
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
        String normalizedStatus = normalizeTaskListStatus(status);
        if ("all".equals(normalizedStatus)) {
            return true;
        }
        if ("incomplete".equals(normalizedStatus)) {
            return !task.isCompleted();
        }
        if ("completed".equals(normalizedStatus)) {
            return task.isCompleted();
        }
        return false;
    }

    private static boolean matchesTaskPriority(Task task, String priority) {
        String normalizedPriority = normalizeTaskListPriority(priority);
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

    private static String normalizeTaskListStatus(String status) {
        if (status == null) {
            return "all";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "all" -> "all";
            case "incomplete" -> "incomplete";
            case "completed" -> "completed";
            default -> "";
        };
    }

    private static String normalizeTaskListPriority(String priority) {
        if (priority == null) {
            return "all";
        }
        return switch (priority.toLowerCase(Locale.ROOT)) {
            case "all" -> "all";
            case "low" -> "low";
            case "medium" -> "medium";
            case "high" -> "high";
            default -> "";
        };
    }

    private static String normalizeTaskCleanScope(String scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope.toLowerCase(Locale.ROOT)) {
            case "personal" -> "personal";
            case "team" -> "team";
            default -> "";
        };
    }

    private static String normalizeTaskCleanProjectSelector(String selector) {
        if (selector == null) {
            return "";
        }
        return switch (selector.toLowerCase(Locale.ROOT)) {
            case "current" -> "current";
            case "star" -> "star";
            case "all" -> "all";
            default -> "";
        };
    }

    private static String normalizeTaskCleanStatus(String status) {
        if (status == null) {
            return "";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "incomplete" -> "incomplete";
            case "completed" -> "completed";
            default -> "";
        };
    }

    private static String normalizeProjectScope(String scope) {
        return normalizeTaskCleanScope(scope);
    }

    private static String normalizeProjectListMode(String mode) {
        if (mode == null) {
            return "";
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "all" -> "all";
            case "current" -> "current";
            case "star" -> "star";
            default -> "";
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
    private static int requestTaskCleanConfirm(CommandSourceStack source, String scope, String projectSelector, String status) {
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        ResolvedTaskCleanRequest request = resolveTaskCleanRequest(source, player, scope, projectSelector, status, true);
        if (request == null) {
            return COMMAND_FAILURE;
        }
        pendingTaskCleanConfirmMap.put(
                player.getStringUUID(),
                new PendingTaskCleanConfirmation(
                        request.scope,
                        request.projectSelector,
                        request.status,
                        request.projectIds,
                        System.currentTimeMillis()
                )
        );
        return sendTaskCleanConfirmHint(source, request);
    }

    private static int executeTaskCleanConfirm(CommandSourceStack source) {
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        PendingTaskCleanConfirmation pending = pendingTaskCleanConfirmMap.remove(player.getStringUUID());
        if (pending == null || System.currentTimeMillis() - pending.issuedAt > CONFIRM_WINDOW_MILLIS) {
            return sendCommandFailure(source, "command.todolist.task.clean.confirm_expired");
        }
        ResolvedTaskCleanRequest request = resolveTaskCleanRequest(source, player, pending.scope, pending.projectSelector, pending.status, true);
        if (request == null) {
            return COMMAND_FAILURE;
        }
        if (!pending.projectIds.equals(request.projectIds)) {
            return sendCommandFailure(source, "command.todolist.task.clean.confirm_invalidated");
        }
        return executeTaskCleanNow(source, player, request);
    }

    private static ResolvedTaskCleanRequest resolveTaskCleanRequest(
            CommandSourceStack source,
            ServerPlayer player,
            String scope,
            String projectSelector,
            String status,
            boolean sendErrors
    ) {
        String normalizedScope = normalizeTaskCleanScope(scope);
        if (normalizedScope.isEmpty()) {
            if (sendErrors) {
                sendCommandFailure(source, "command.todolist.task.clean.invalid_scope");
            }
            return null;
        }
        String normalizedProjectSelector = normalizeTaskCleanProjectSelector(projectSelector);
        if (normalizedProjectSelector.isEmpty()) {
            if (sendErrors) {
                sendCommandFailure(source, "command.todolist.task.clean.invalid_project_selector");
            }
            return null;
        }
        String normalizedStatus = normalizeTaskCleanStatus(status);
        if (normalizedStatus.isEmpty()) {
            if (sendErrors) {
                sendCommandFailure(source, "command.todolist.task.clean.invalid_status");
            }
            return null;
        }

        boolean op = source.hasPermission(2);
        ProjectManager projectManager = TodoListCommon.getProjectManager();
        String playerUuid = player.getStringUUID();
        List<String> projectIds = new ArrayList<>();
        boolean matchAllPersonalTasks = false;

        if ("personal".equals(normalizedScope)) {
            if ("all".equals(normalizedProjectSelector)) {
                matchAllPersonalTasks = true;
                projectIds.addAll(projectManager.getAllProjects().stream()
                        .filter(project -> project != null && project.getScope() == Project.Scope.PERSONAL)
                        .filter(project -> isProjectVisibleToPlayer(project, playerUuid, source.getServer()))
                        .map(Project::getId)
                        .filter(projectId -> projectId != null && !projectId.isBlank())
                        .sorted()
                        .toList());
            } else if ("current".equals(normalizedProjectSelector)) {
                String activeProjectId = ProjectPackets.getActiveProjectId(player);
                Project project = activeProjectId == null ? null : projectManager.getProject(activeProjectId);
                if (project == null || project.getScope() != Project.Scope.PERSONAL || !isProjectVisibleToPlayer(project, playerUuid, source.getServer())) {
                    if (sendErrors) {
                        sendCommandFailure(source, "command.todolist.task.clean.current_project_invalid");
                    }
                    return null;
                }
                projectIds.add(project.getId());
            } else {
                projectIds.addAll(ProjectPackets.getHudStarredProjectIds(player).stream()
                        .map(projectManager::getProject)
                        .filter(project -> project != null && project.getScope() == Project.Scope.PERSONAL)
                        .filter(project -> isProjectVisibleToPlayer(project, playerUuid, source.getServer()))
                        .map(Project::getId)
                        .distinct()
                        .sorted()
                        .toList());
            }
        } else {
            List<String> starredTeamProjectIds = ProjectPackets.getHudStarredProjectIds(player).stream()
                    .map(projectManager::getProject)
                    .filter(project -> project != null && project.getScope() == Project.Scope.TEAM)
                    .map(Project::getId)
                    .distinct()
                    .sorted()
                    .toList();
            if ("current".equals(normalizedProjectSelector)) {
                String activeProjectId = ProjectPackets.getActiveProjectId(player);
                Project project = activeProjectId == null ? null : projectManager.getProject(activeProjectId);
                if (project == null || project.getScope() != Project.Scope.TEAM) {
                    if (sendErrors) {
                        sendCommandFailure(source, "command.todolist.task.clean.current_project_invalid");
                    }
                    return null;
                }
                if (!op && !playerUuid.equals(project.getOwnerUuid())) {
                    if (sendErrors) {
                        sendCommandFailure(source, "command.todolist.task.clean.team_permission_denied", getProjectDisplayName(project, project.getId()));
                    }
                    return null;
                }
                projectIds.add(project.getId());
            } else if ("star".equals(normalizedProjectSelector)) {
                for (String projectId : starredTeamProjectIds) {
                    Project project = projectManager.getProject(projectId);
                    if (project == null) {
                        continue;
                    }
                    if (!op && !playerUuid.equals(project.getOwnerUuid())) {
                        if (sendErrors) {
                            sendCommandFailure(source, "command.todolist.task.clean.team_permission_denied", getProjectDisplayName(project, projectId));
                        }
                        return null;
                    }
                    projectIds.add(projectId);
                }
            } else {
                projectIds.addAll(projectManager.getAllProjects().stream()
                        .filter(project -> project != null && project.getScope() == Project.Scope.TEAM)
                        .filter(project -> op || playerUuid.equals(project.getOwnerUuid()))
                        .map(Project::getId)
                        .filter(projectId -> projectId != null && !projectId.isBlank())
                        .sorted()
                        .toList());
            }
        }

        List<String> distinctProjectIds = projectIds.stream().distinct().sorted().toList();
        if (!matchAllPersonalTasks && distinctProjectIds.isEmpty()) {
            if (sendErrors) {
                sendCommandFailure(source, "command.todolist.task.clean.no_projects");
            }
            return null;
        }

        int affectedTaskCount;
        try {
            TaskStorage storage = TodoListCommon.getTaskStorage();
            List<Task> tasks = "team".equals(normalizedScope)
                    ? storage.loadTeamTasks()
                    : storage.loadPlayerTasks(player.getUUID());
            ResolvedTaskCleanRequest provisional = new ResolvedTaskCleanRequest(
                    normalizedScope,
                    normalizedProjectSelector,
                    normalizedStatus,
                    distinctProjectIds,
                    0,
                    matchAllPersonalTasks
            );
            affectedTaskCount = (int) tasks.stream().filter(task -> matchesTaskCleanRequest(task, provisional)).count();
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to resolve task clean request", e);
            if (sendErrors) {
                sendCommandFailure(source, "command.todolist.task.clean.failed");
            }
            return null;
        }

        if (affectedTaskCount <= 0) {
            if (sendErrors) {
                sendCommandFailure(source, "command.todolist.task.clean.nothing_to_clean");
            }
            return null;
        }

        return new ResolvedTaskCleanRequest(
                normalizedScope,
                normalizedProjectSelector,
                normalizedStatus,
                distinctProjectIds,
                affectedTaskCount,
                matchAllPersonalTasks
        );
    }

    private static boolean matchesTaskCleanRequest(Task task, ResolvedTaskCleanRequest request) {
        if (task == null || request == null) {
            return false;
        }
        boolean statusMatches = "completed".equals(request.status) ? task.isCompleted() : !task.isCompleted();
        if (!statusMatches) {
            return false;
        }
        if (request.matchAllPersonalTasks) {
            return true;
        }
        String projectId = task.getProjectId();
        return projectId != null && request.projectIds.contains(projectId);
    }

    private static int executeTaskCleanNow(CommandSourceStack source, ServerPlayer player, ResolvedTaskCleanRequest request) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            int removedCount;
            if ("team".equals(request.scope)) {
                List<Task> tasks = new ArrayList<>(storage.loadTeamTasks());
                int before = tasks.size();
                tasks.removeIf(task -> matchesTaskCleanRequest(task, request));
                removedCount = before - tasks.size();
                storage.saveTeamTasks(tasks);
                TaskPackets.broadcastTeamTasks(source.getServer());
            } else {
                List<Task> tasks = new ArrayList<>(storage.loadPlayerTasks(player.getUUID()));
                int before = tasks.size();
                tasks.removeIf(task -> matchesTaskCleanRequest(task, request));
                removedCount = before - tasks.size();
                storage.savePlayerTasks(player.getUUID(), tasks);
                syncTasksToPlayer(source.getServer(), player);
            }
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.clean.success",
                    getTaskCleanScopeText(request.scope),
                    getTaskCleanProjectSelectorText(request.projectSelector),
                    getTaskCleanStatusText(request.status),
                    removedCount
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to clean tasks for command", e);
            return sendCommandFailure(source, "command.todolist.task.clean.failed");
        }
    }

    private static int sendTaskCleanConfirmHint(CommandSourceStack source, ResolvedTaskCleanRequest request) {
        MutableComponent button = Component.translatable("command.todolist.task.clean.confirm_button")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/todo task clean confirm"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("command.todolist.task.clean.confirm_hover")
                        )));
        sendFeedback(source, () -> Component.translatable(
                "command.todolist.task.clean.confirm_hint",
                getTaskCleanScopeText(request.scope),
                getTaskCleanProjectSelectorText(request.projectSelector),
                getTaskCleanStatusText(request.status),
                request.affectedTaskCount,
                button
        ));
        return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
    }

    private static Component getTaskCleanScopeText(String scope) {
        return Component.translatable("command.todolist.scope." + scope);
    }

    private static Component getTaskCleanProjectSelectorText(String selector) {
        return Component.translatable("command.todolist.project_selector." + selector);
    }

    private static Component getTaskCleanStatusText(String status) {
        return Component.translatable("command.todolist.status." + status);
    }

    private static int executeProjectSelect(CommandSourceStack source, String projectId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.select.not_found", projectId);
        }
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null || !isProjectVisibleToPlayer(project, player.getStringUUID(), player.getServer())) {
            return sendCommandFailure(source, "command.todolist.project.select.not_found", normalizedProjectId);
        }
        if (project.getScope() == Project.Scope.TEAM && isSingleplayerServer(source.getServer())) {
            return sendCommandFailure(source, "command.todolist.project.select.singleplayer_team_forbidden");
        }
        ProjectPackets.setActiveProjectId(player, normalizedProjectId);
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_NONE,
                "command.todolist.project.select.success",
                buildProjectNameComponent(project)
        );
    }


    private static int sendProjectList(CommandSourceStack source, String mode) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedMode = normalizeProjectListMode(mode);
        if (normalizedMode.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.list.invalid_mode");
        }

        List<Project> visibleProjects = resolveProjectsForListMode(player, normalizedMode);
        return sendListWithUnifiedTemplate(
                source,
                visibleProjects,
                PROJECT_LIST_MAX_SUMMARY,
                () -> Component.translatable("command.todolist.project.list.summary", visibleProjects.size()),
                getProjectListEmptyKey(normalizedMode),
                CommandBootstrap::buildProjectListItem,
                "command.todolist.project.list.more"
        );
    }

    private static String getProjectListEmptyKey(String mode) {
        return switch (mode) {
            case "current" -> "command.todolist.project.list.current.empty";
            case "star" -> "command.todolist.project.list.star.empty";
            default -> "command.todolist.project.list.empty";
        };
    }

    private static List<Project> resolveProjectsForListMode(ServerPlayer player, String mode) {
        ProjectManager projectManager = TodoListCommon.getProjectManager();
        String playerUuid = player.getStringUUID();
        if ("current".equals(mode)) {
            String activeProjectId = ProjectPackets.getActiveProjectId(player);
            Project project = activeProjectId == null ? null : projectManager.getProject(activeProjectId);
            if (project == null || !isProjectVisibleToPlayer(project, playerUuid, player.getServer())) {
                return List.of();
            }
            return List.of(project);
        }
        if ("star".equals(mode)) {
            return ProjectPackets.getHudStarredProjectIds(player).stream()
                    .map(projectManager::getProject)
                    .filter(project -> project != null && isProjectVisibleToPlayer(project, playerUuid, player.getServer()))
                    .distinct()
                    .sorted(buildProjectSummaryComparator())
                    .toList();
        }
        return projectManager.getAllProjects().stream()
                .filter(project -> isProjectVisibleToPlayer(project, playerUuid, player.getServer()))
                .sorted(buildProjectSummaryComparator())
                .toList();
    }

    private static int executeProjectCreate(CommandSourceStack source, String scope, String name) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedScope = normalizeProjectScope(scope);
        if (normalizedScope.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.create.invalid_scope");
        }
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.create.invalid_name");
        }

        Project.Scope projectScope = "team".equals(normalizedScope) ? Project.Scope.TEAM : Project.Scope.PERSONAL;
        if (projectScope == Project.Scope.TEAM && isSingleplayerServer(source.getServer())) {
            return sendCommandFailure(source, "command.todolist.project.create.singleplayer_forbidden");
        }
        Project project = new Project(normalizedName, projectScope, player.getStringUUID());
        project.addMember(player.getStringUUID(), Project.ProjectRole.PROJECT_MANAGER, player.getName().getString());
        TodoListCommon.getProjectManager().addProject(project);
        saveProjects(source.getServer(), projectScope);
        refreshProjectsAfterMutation(source.getServer(), player, projectScope);
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.project.create.success",
                buildProjectNameComponent(project),
                getProjectScopeText(project)
        );
    }

    private static int requestProjectRemoveConfirm(CommandSourceStack source, String projectId) {
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.remove.not_found", projectId);
        }
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null) {
            return sendCommandFailure(source, "command.todolist.project.remove.not_found", normalizedProjectId);
        }
        if (ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID.equals(normalizedProjectId)
                || ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID.equals(normalizedProjectId)) {
            return sendCommandFailure(source, "command.todolist.project.remove.default_forbidden");
        }
        if (!canRemoveProject(source, player, project)) {
            return sendCommandFailure(source, "command.todolist.project.remove.no_permission");
        }
        pendingProjectRemoveConfirmMap.put(
                player.getStringUUID(),
                new PendingProjectRemoveConfirmation(normalizedProjectId, System.currentTimeMillis())
        );
        MutableComponent button = Component.translatable("command.todolist.project.remove.confirm_button")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/todo project remove confirm"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("command.todolist.project.remove.confirm_hover")
                        )));
        sendFeedback(source, () -> Component.translatable(
                "command.todolist.project.remove.confirm_hint",
                getProjectDisplayName(project, project.getId()),
                button
        ));
        return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
    }

    private static int executeProjectRemoveConfirm(CommandSourceStack source) {
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        PendingProjectRemoveConfirmation pending = pendingProjectRemoveConfirmMap.remove(player.getStringUUID());
        if (pending == null || System.currentTimeMillis() - pending.issuedAt > CONFIRM_WINDOW_MILLIS) {
            return sendCommandFailure(source, "command.todolist.project.remove.confirm_expired");
        }
        Project project = TodoListCommon.getProjectManager().getProject(pending.projectId);
        if (project == null) {
            return sendCommandFailure(source, "command.todolist.project.remove.not_found", pending.projectId);
        }
        if (ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID.equals(pending.projectId)
                || ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID.equals(pending.projectId)) {
            return sendCommandFailure(source, "command.todolist.project.remove.default_forbidden");
        }
        if (!canRemoveProject(source, player, project)) {
            return sendCommandFailure(source, "command.todolist.project.remove.no_permission");
        }
        return executeProjectRemoveNow(source, player, project);
    }

    private static boolean canRemoveProject(CommandSourceStack source, ServerPlayer player, Project project) {
        if (player == null || project == null) {
            return false;
        }
        if (source.hasPermission(2)) {
            return true;
        }
        String playerUuid = player.getStringUUID();
        if (project.getScope() == Project.Scope.PERSONAL) {
            String ownerUuid = project.getOwnerUuid();
            return ownerUuid == null || ownerUuid.isEmpty() || ownerUuid.equals(playerUuid);
        }
        return playerUuid.equals(project.getOwnerUuid());
    }

    private static int executeProjectRemoveNow(CommandSourceStack source, ServerPlayer player, Project project) {
        String projectId = project.getId();
        Project.Scope scope = project.getScope();
        TodoListCommon.getProjectManager().deleteProject(projectId);
        purgeDeletedProjectTasks(scope, projectId, player);
        saveProjects(source.getServer(), scope);
        refreshProjectsAfterMutation(source.getServer(), player, scope);
        if (scope == Project.Scope.TEAM) {
            TaskPackets.broadcastTeamTasks(source.getServer());
        } else {
            syncTasksToPlayer(source.getServer(), player);
        }
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                "command.todolist.project.remove.success",
                getProjectDisplayName(project, projectId)
        );
    }

    private static void purgeDeletedProjectTasks(Project.Scope scope, String projectId, ServerPlayer player) {
        if (scope == null || projectId == null || projectId.isEmpty() || player == null) {
            return;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        if (scope == Project.Scope.PERSONAL) {
            purgeDeletedProjectTasksInPlayerFile(storage, player, projectId);
            purgeDeletedProjectTasksInSingleFile(storage, projectId);
            return;
        }
        if (scope == Project.Scope.TEAM) {
            purgeDeletedProjectTasksInTeamFile(storage, projectId);
        }
    }

    private static void purgeDeletedProjectTasksInPlayerFile(TaskStorage storage, ServerPlayer player, String projectId) {
        try {
            List<Task> tasks = storage.loadPlayerTasks(player.getUUID());
            if (removeTasksByProjectId(tasks, projectId)) {
                storage.savePlayerTasks(player.getUUID(), tasks);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to purge deleted project tasks in player task file, projectId={}", projectId, e);
        }
    }

    private static void purgeDeletedProjectTasksInSingleFile(TaskStorage storage, String projectId) {
        try {
            List<Task> tasks = storage.loadTasks();
            if (removeTasksByProjectId(tasks, projectId)) {
                storage.saveTasks(tasks);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to purge deleted project tasks in local task file, projectId={}", projectId, e);
        }
    }

    private static void purgeDeletedProjectTasksInTeamFile(TaskStorage storage, String projectId) {
        try {
            List<Task> tasks = storage.loadTeamTasks();
            if (removeTasksByProjectId(tasks, projectId)) {
                storage.saveTeamTasks(tasks);
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to purge deleted project tasks in team task file, projectId={}", projectId, e);
        }
    }

    private static boolean removeTasksByProjectId(List<Task> tasks, String projectId) {
        int beforeSize = tasks.size();
        tasks.removeIf(task -> task != null && task.belongsToProject(projectId));
        return beforeSize != tasks.size();
    }

    private static void saveProjects(MinecraftServer server, Project.Scope scope) {
        if (server == null || scope == null) {
            return;
        }
        try {
            ProjectSaveDebouncer.requestSave(server, scope);
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to save projects from command", e);
        }
    }

    private static void refreshProjectsAfterMutation(MinecraftServer server, ServerPlayer actor, Project.Scope scope) {
        if (server == null || actor == null || scope == null) {
            return;
        }
        if (scope == Project.Scope.TEAM) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                ProjectPackets.onPlayerJoin(server, online);
            }
            return;
        }
        ProjectPackets.onPlayerJoin(server, actor);
    }

    /**
     * 判断当前服务端是否处于“团队项目不可用”的单人本地状态。
     * 专用服务器始终可用；本地集成服仅在未发布局域网时不可用。
     */
    private static boolean isSingleplayerServer(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        if (server.isDedicatedServer()) {
            return false;
        }
        return !server.isPublished();
    }

    private static boolean isProjectVisibleToPlayer(Project project, String playerUuid, MinecraftServer server) {
        if (project == null) {
            return false;
        }
        if (project.getScope() == Project.Scope.TEAM) {
            if (server != null && isSingleplayerServer(server)) {
                return false;
            }
            return true;
        }
        String ownerUuid = project.getOwnerUuid();
        if (ownerUuid == null || ownerUuid.isEmpty()) {
            return true;
        }
        return ownerUuid.equals(playerUuid) || project.getMemberRole(playerUuid) != null;
    }

    private static Comparator<Project> buildProjectSummaryComparator() {
        return Comparator.comparing((Project project) -> project.getScope() == Project.Scope.TEAM)
                .thenComparing(Project::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(project -> {
                    String name = project.getName();
                    return name == null ? "" : name;
                });
    }

    private static Component getProjectScopeText(Project project) {
        if (project == null || project.getScope() == Project.Scope.PERSONAL) {
            return Component.translatable("gui.todolist.scope.personal");
        }
        return Component.translatable("gui.todolist.scope.team");
    }

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

    private static Component buildProjectListItem(Integer displayIndex, Project project) {
        MutableComponent line = Component.literal(displayIndex + ". ")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY));
        line.append(Component.literal("[")
                .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)));
        line.append(getProjectScopeText(project).copy().withStyle(style -> style.withColor(ChatFormatting.AQUA)));
        line.append(Component.literal("] ")
                .withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)));
        line.append(buildProjectNameComponent(project));
        return line;
    }

    private static MutableComponent buildProjectNameComponent(Project project) {
        String projectId = project == null ? "" : project.getId();
        MutableComponent name = getProjectDisplayName(project, projectId).copy();
        name.withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
        return applyCopyStyle(
                name,
                projectId,
                Component.translatable("command.todolist.project.list.copy_hint", projectId)
        );
    }

    private static Component buildTaskListItem(Integer displayIndex, Task task, String searchText) {
        MutableComponent line = Component.literal(displayIndex + ". ")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY));
        line.append(Component.literal(task.isCompleted() ? "[x] " : "[ ] ")
                .withStyle(style -> style.withColor(task.isCompleted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        line.append(buildTaskTitleComponent(task, searchText));
        return line;
    }

    private static MutableComponent buildTaskTitleComponent(Task task, String searchText) {
        String fallback = task.getId() == null ? "" : task.getId();
        String title = task.getTitle();
        if (title == null || title.isBlank()) {
            title = fallback;
        }
        MutableComponent titleComponent = buildHighlightedText(title, searchText, ChatFormatting.GOLD, ChatFormatting.AQUA);
        return applyCopyStyle(
                titleComponent,
                fallback,
                Component.translatable("command.todolist.task.list.copy_hint", fallback)
        );
    }

    private static MutableComponent buildHighlightedText(String value, String query, ChatFormatting baseColor, ChatFormatting matchColor) {
        String safeValue = value == null ? "" : value;
        MutableComponent component = Component.empty();
        if (query == null || query.isBlank()) {
            component.append(Component.literal(safeValue)
                    .withStyle(style -> style.withColor(baseColor).withBold(true)));
            return component;
        }
        String normalizedValue = safeValue.toLowerCase(Locale.ROOT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        int cursor = 0;
        while (cursor < safeValue.length()) {
            int matchIndex = normalizedValue.indexOf(normalizedQuery, cursor);
            if (matchIndex < 0) {
                component.append(Component.literal(safeValue.substring(cursor))
                        .withStyle(style -> style.withColor(baseColor).withBold(true)));
                break;
            }
            if (matchIndex > cursor) {
                component.append(Component.literal(safeValue.substring(cursor, matchIndex))
                        .withStyle(style -> style.withColor(baseColor).withBold(true)));
            }
            int matchEnd = Math.min(safeValue.length(), matchIndex + normalizedQuery.length());
            component.append(Component.literal(safeValue.substring(matchIndex, matchEnd))
                    .withStyle(style -> style.withColor(matchColor).withBold(true).withUnderlined(true)));
            cursor = matchEnd;
        }
        return component;
    }

    private static MutableComponent applyCopyStyle(MutableComponent component, String copyValue, Component hoverText) {
        String safeCopyValue = copyValue == null ? "" : copyValue;
        return component.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, safeCopyValue))
                .withInsertion(safeCopyValue)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText)));
    }

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
            return accessMode != ModConfig.CommandAccessMode.OP_ONLY || op;
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
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null) {
            return sendCommandFailure(source, "command.todolist.task.add.invalid_project");
        }
        if (!canAddTaskToProject(player, project)) {
            if (project.getScope() == Project.Scope.TEAM) {
                return sendCommandFailure(source, "command.todolist.task.add.no_permission_team");
            }
            return sendCommandFailure(source, "command.todolist.task.add.invalid_project");
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
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
            if (project.getScope() == Project.Scope.TEAM) {
                newTask.setScope(Task.Scope.TEAM);
                List<Task> tasks = storage.loadTeamTasks();
                tasks.add(newTask);
                storage.saveTeamTasks(tasks);
                TaskPackets.broadcastTeamTasks(source.getServer());
            } else {
                newTask.setScope(Task.Scope.PERSONAL);
                List<Task> tasks = storage.loadPlayerTasks(playerUuid);
                tasks.add(newTask);
                storage.savePlayerTasks(playerUuid, tasks);
                syncTasksToPlayer(source.getServer(), player);
            }
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
    private static boolean canAddTaskToProject(ServerPlayer player, Project project) {
        if (player == null || project == null) {
            return false;
        }
        if (project.getScope() == Project.Scope.PERSONAL) {
            String ownerUuid = project.getOwnerUuid();
            return ownerUuid == null || ownerUuid.isEmpty() || ownerUuid.equals(player.getStringUUID());
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        boolean projectMember = isTeamProjectMember(player, project);
        Role role = resolveProjectRole(player, project);
        boolean allowMemberCreate = project.isAllowMemberCreate();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, false, false, projectMember, allowMemberCreate);
        return PermissionCenter.canPerform(Operation.ADD_TASK, role, ctx);
    }

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
            if (!canAddTaskToProject(player, project)) {
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
    private static CompletableFuture<Suggestions> suggestTaskListStatuses(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(TASK_LIST_STATUS_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestTaskListPriorities(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(TASK_LIST_PRIORITY_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestTaskCleanScopes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(TASK_CLEAN_SCOPE_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestTaskCleanProjects(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(TASK_CLEAN_PROJECT_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestTaskCleanStatuses(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(TASK_CLEAN_STATUS_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestProjectScopes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(PROJECT_SCOPE_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestProjectListModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(PROJECT_LIST_MODE_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestSelectableProjectIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return builder.buildFuture();
        }
        String playerUuid = player.getStringUUID();
        MinecraftServer server = source.getServer();
        for (Project project : TodoListCommon.getProjectManager().getAllProjects()) {
            if (project == null || project.getId() == null || project.getId().isBlank()) {
                continue;
            }
            if (!isProjectVisibleToPlayer(project, playerUuid, server)) {
                continue;
            }
            builder.suggest(project.getId());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRemovableProjectIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return builder.buildFuture();
        }
        String playerUuid = player.getStringUUID();
        for (Project project : TodoListCommon.getProjectManager().getAllProjects()) {
            if (project == null || project.getId() == null || project.getId().isBlank()) {
                continue;
            }
            if (ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID.equals(project.getId())
                    || ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID.equals(project.getId())) {
                continue;
            }
            boolean removable = source.hasPermission(2)
                    || (project.getScope() == Project.Scope.PERSONAL
                        ? project.getOwnerUuid() == null || project.getOwnerUuid().isEmpty() || playerUuid.equals(project.getOwnerUuid())
                        : playerUuid.equals(project.getOwnerUuid()));
            if (removable) {
                builder.suggest(project.getId());
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestJoinableProjectIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return builder.buildFuture();
        }
        String playerUuid = player.getStringUUID();
        for (Project project : TodoListCommon.getProjectManager().getAllProjects()) {
            if (project == null || project.getScope() != Project.Scope.TEAM || project.getId() == null || project.getId().isBlank()) {
                continue;
            }
            if (playerUuid.equals(project.getOwnerUuid()) || project.getMemberRole(playerUuid) != null) {
                continue;
            }
            builder.suggest(project.getId());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestWords(List<String> suggestions, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

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
