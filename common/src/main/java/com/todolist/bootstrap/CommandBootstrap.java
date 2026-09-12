package com.todolist.bootstrap;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import com.todolist.task.TaskTrigger;
import com.todolist.trigger.TaskTriggerService;
import com.todolist.project.ProjectSaveDebouncer;
import com.todolist.storage.H2ConnectionProvider;
import com.todolist.storage.H2BackupService;
import com.todolist.storage.H2HealthCheckService;
import com.todolist.storage.H2MaintenanceLock;
import com.todolist.storage.H2StorageAvailability;
import com.todolist.storage.H2TcpAccountRole;
import com.todolist.storage.H2TcpConfig;
import com.todolist.storage.H2TcpServerManager;
import com.todolist.storage.StorageFailureNotifier;
import com.todolist.storage.StorageUnavailableException;
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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
            "all", "incomplete", "completed"
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
    private static final List<String> PROJECT_MEMBER_ROLE_SUGGESTIONS = List.of(
            "lead", "member"
    );
    private static final List<String> COMMAND_ACCESS_MODE_SUGGESTIONS = List.of(
            "op_only", "view_only", "full"
    );
    private static final List<String> H2_ACCOUNT_ROLE_SUGGESTIONS = List.of(
            "admin", "readonly", "readwrite"
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
    private static final List<String> TASK_TRIGGER_TYPE_SUGGESTIONS = List.of(
            "kill_entity", "break_block", "craft_item", "item_collect", "advancement"
    );
    private static final ConcurrentHashMap<String, PendingTaskCleanConfirmation> pendingTaskCleanConfirmMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, PendingProjectRemoveConfirmation> pendingProjectRemoveConfirmMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TaskListPageState> taskListPageStateMap = new ConcurrentHashMap<>();

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
                        .then(Commands.literal("listp")
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .executes(ctx -> sendTaskListByProject(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "projectId"),
                                                "all",
                                                "all",
                                                null
                                        ))
                                        .then(Commands.argument("status", StringArgumentType.word())
                                                .suggests(CommandBootstrap::suggestTaskListStatuses)
                                                .executes(ctx -> sendTaskListByProject(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "status"),
                                                        "all",
                                                        null
                                                ))
                                                .then(Commands.argument("priority", StringArgumentType.word())
                                                        .suggests(CommandBootstrap::suggestTaskListPriorities)
                                                        .executes(ctx -> sendTaskListByProject(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "projectId"),
                                                                StringArgumentType.getString(ctx, "status"),
                                                                StringArgumentType.getString(ctx, "priority"),
                                                                null
                                                        ))
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(ctx -> sendTaskListByProject(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "projectId"),
                                                                        StringArgumentType.getString(ctx, "status"),
                                                                        StringArgumentType.getString(ctx, "priority"),
                                                                StringArgumentType.getString(ctx, "text")
                                                                )))))))
                        .then(Commands.literal("more")
                                .executes(ctx -> executeTaskListPageTurn(ctx.getSource(), 1)))
                        .then(Commands.literal("prev")
                                .executes(ctx -> executeTaskListPageTurn(ctx.getSource(), -1)))
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
                        .then(Commands.literal("trigger")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests(CommandBootstrap::suggestTaskTriggerTypes)
                                                        .then(Commands.argument("target", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                                                .executes(ctx -> executeTaskTriggerSet(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "taskId"),
                                                                        StringArgumentType.getString(ctx, "type"),
                                                                        net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "target").toString(),
                                                                        1
                                                                ))
                                                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                                                        .executes(ctx -> executeTaskTriggerSet(
                                                                                ctx.getSource(),
                                                                                StringArgumentType.getString(ctx, "taskId"),
                                                                                StringArgumentType.getString(ctx, "type"),
                                                                                net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "target").toString(),
                                                                                IntegerArgumentType.getInteger(ctx, "count")
                                                                        )))))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .executes(ctx -> executeTaskTriggerClear(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "taskId")
                                                ))))
                                .then(Commands.literal("info")
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .executes(ctx -> executeTaskTriggerInfo(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "taskId")
                                                )))))
                        .then(Commands.literal("donep")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .executes(ctx -> executeTaskDoneByProject(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "taskId")
                                                )))))
                        .then(Commands.literal("claimp")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .executes(ctx -> executeTaskClaimByProject(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "taskId")
                                                )))))
                        .then(Commands.literal("abandonp")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .executes(ctx -> executeTaskAbandonByProject(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "taskId")
                                                )))))
                        .then(Commands.literal("remove")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("taskId", StringArgumentType.word())
                                        .executes(ctx -> executeTaskRemove(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "taskId")
                                        ))))
                        .then(Commands.literal("removep")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .executes(ctx -> executeTaskRemoveByProject(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "taskId")
                                                )))))
                        .then(Commands.literal("assignp")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.EDIT, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("taskId", StringArgumentType.word())
                                                .then(Commands.argument("member", StringArgumentType.word())
                                                        .suggests(CommandBootstrap::suggestAssignableProjectMembers)
                                                        .executes(ctx -> executeTaskAssignByProject(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "projectId"),
                                                                StringArgumentType.getString(ctx, "taskId"),
                                                                StringArgumentType.getString(ctx, "member")
                                                        )))))))
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
                        .then(Commands.literal("rename")
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> executeProjectRename(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "name")
                                                )))))
                        .then(Commands.literal("member-create")
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("enabled", StringArgumentType.word())
                                                .executes(ctx -> executeProjectAllowMemberCreate(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "enabled")
                                                )))))
                        .then(Commands.literal("all-player-claim-complete")
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .then(Commands.argument("enabled", StringArgumentType.word())
                                                .executes(ctx -> executeProjectAllowAllPlayersClaimComplete(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "projectId"),
                                                        StringArgumentType.getString(ctx, "enabled")
                                                )))))
                        .then(Commands.literal("star")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .executes(ctx -> executeProjectStarState(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "projectId"),
                                                true
                                        ))))
                        .then(Commands.literal("unstar")
                                .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL, null))
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                        .executes(ctx -> executeProjectStarState(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "projectId"),
                                                false
                                        ))))
                        .then(Commands.literal("member")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                                .then(Commands.argument("member", StringArgumentType.word())
                                                        .suggests(CommandBootstrap::suggestAddableProjectMembers)
                                                        .executes(ctx -> executeProjectMemberAdd(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "projectId"),
                                                                StringArgumentType.getString(ctx, "member")
                                                        )))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                                .then(Commands.argument("memberUuid", StringArgumentType.word())
                                                        .suggests(CommandBootstrap::suggestRemovableProjectMemberIds)
                                                        .executes(ctx -> executeProjectMemberRemove(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "projectId"),
                                                                StringArgumentType.getString(ctx, "memberUuid")
                                                        )))))
                                .then(Commands.literal("role")
                                        .then(Commands.argument("projectId", StringArgumentType.word())
                                                .suggests(CommandBootstrap::suggestSelectableProjectIds)
                                                .then(Commands.argument("memberUuid", StringArgumentType.word())
                                                        .suggests(CommandBootstrap::suggestRoleEditableProjectMemberIds)
                                                        .then(Commands.argument("role", StringArgumentType.word())
                                                                .suggests(CommandBootstrap::suggestProjectMemberRoles)
                                                                .executes(ctx -> executeProjectMemberRole(
                                                                        ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "projectId"),
                                                                        StringArgumentType.getString(ctx, "memberUuid"),
                                                                        StringArgumentType.getString(ctx, "role")
                                                                )))))))
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
                                .executes(ctx -> toggleHudStatus(ctx.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .executes(ctx -> setHudStatus(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "enabled")
                                        )))))
                .then(Commands.literal("join")
                        .then(Commands.literal("project")
                                .then(Commands.argument("projectId", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestJoinableProjectIds)
                                        .executes(ctx -> executeJoinProject(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "projectId")
                                        ))))
                        .then(buildJoinDecisionLiteral("accept", true))
                        .then(buildJoinDecisionLiteral("deny", false)))
                .then(Commands.literal("reload-db")
                        .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.ADMIN, null))
                        .executes(ctx -> reloadH2Database(ctx.getSource())))
                .then(Commands.literal("h2")
                        .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.ADMIN, null))
                        .then(Commands.literal("status")
                                .executes(ctx -> sendH2Status(ctx.getSource())))
                        .then(Commands.literal("restart-tcp")
                                .executes(ctx -> restartH2Tcp(ctx.getSource())))
                        .then(Commands.literal("backup")
                                .executes(ctx -> backupH2(ctx.getSource(), null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> backupH2(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")
                                        ))))
                        .then(Commands.literal("reload-db")
                                .executes(ctx -> reloadH2Database(ctx.getSource())))
                        .then(Commands.literal("health")
                                .executes(ctx -> checkH2Health(ctx.getSource())))
                        .then(Commands.literal("reset-password")
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestH2AccountRoles)
                                        .executes(ctx -> resetH2Password(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "role")
                                        )))))
                .then(Commands.literal("admin")
                        .requires(source -> hasCommandPermission(source, CommandPermissionSemantic.ADMIN, null))
                        .then(Commands.literal("command-access")
                                .executes(ctx -> sendCommandAccessMode(ctx.getSource()))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests(CommandBootstrap::suggestCommandAccessModes)
                                        .executes(ctx -> setCommandAccessMode(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "mode")
                                        )))));

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
        if (!isValidUuidToken(applicantUuid)) {
            return sendCommandFailure(source, "command.todolist.join.invalid_applicant_uuid");
        }
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
     * 校验命令行传入的 UUID 文本是否为合法格式，避免非法输入直接落入业务层静默失败。
     */
    private static boolean isValidUuidToken(String uuidToken) {
        if (uuidToken == null || uuidToken.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(uuidToken);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
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
        if (isSingleplayerServer(source.getServer())) {
            return sendCommandFailure(source, "message.todolist.project.join.singleplayer_forbidden");
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
                "command.todolist.help.todo_task_listp",
                "command.todolist.help.todo_task_list_options",
                "command.todolist.help.todo_task_more",
                "command.todolist.help.todo_task_prev",
                "command.todolist.help.todo_task_add",
                "command.todolist.help.todo_task_addp",
                "command.todolist.help.todo_task_clean",
                "command.todolist.help.todo_task_done",
                "command.todolist.help.todo_task_donep",
                "command.todolist.help.todo_task_claimp",
                "command.todolist.help.todo_task_abandonp",
                "command.todolist.help.todo_task_remove",
                "command.todolist.help.todo_task_removep",
                "command.todolist.help.todo_task_assignp",
                "command.todolist.help.todo_project_create",
                "command.todolist.help.todo_project_rename",
                "command.todolist.help.todo_project_member_create",
                "command.todolist.help.todo_project_all_player_claim_complete",
                "command.todolist.help.todo_project_member_add",
                "command.todolist.help.todo_project_member_remove",
                "command.todolist.help.todo_project_member_role",
                "command.todolist.help.todo_project_remove",
                "command.todolist.help.todo_project_list",
                "command.todolist.help.todo_project_select",
                "command.todolist.help.todo_project_star",
                "command.todolist.help.todo_project_unstar",
                "command.todolist.help.todo_hud_toggle",
                "command.todolist.help.todo_hud_set",
                "command.todolist.help.todo_h2_status",
                "command.todolist.help.todo_h2_restart_tcp",
                "command.todolist.help.todo_h2_backup",
                "command.todolist.help.todo_h2_reload_db",
                "command.todolist.help.todo_reload_db",
                "command.todolist.help.todo_h2_health",
                "command.todolist.help.todo_h2_reset_password",
                "command.todolist.help.todo_admin_command_access",
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
                SIDE_EFFECT_REFRESH_HUD,
                "command.todolist.hud.toggle",
                getHudSwitchText(nextEnabled)
        );
    }

    /**
     * 按显式开关值设置 HUD 状态，避免用户只能依赖 toggle。
     */
    private static int setHudStatus(CommandSourceStack source, String enabled) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Boolean normalizedEnabled = CommandInputNormalizer.normalizeToggleState(enabled);
        if (normalizedEnabled == null) {
            return sendCommandFailure(source, "command.todolist.hud.invalid_value");
        }
        ProjectPackets.setHudVisible(player, normalizedEnabled);
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_REFRESH_HUD,
                "command.todolist.hud.toggle",
                getHudSwitchText(normalizedEnabled)
        );
    }

    /**
     * 输出当前命令权限模式，便于管理员确认服务端当前开放级别。
     *
     * @param source 命令源
     * @return 命令执行结果
     */
    private static int sendCommandAccessMode(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_NONE,
                "command.todolist.command_access_mode.current",
                getCommandAccessModeText(ModConfig.getInstance().getCommandAccessMode())
        );
    }

    /**
     * 通过命令修改 commandAccessMode 配置，并立即刷新在线玩家的命令树。
     *
     * @param source 命令源
     * @param mode 原始权限模式参数
     * @return 命令执行结果
     */
    private static int setCommandAccessMode(CommandSourceStack source, String mode) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ModConfig.CommandAccessMode resolvedMode = resolveCommandAccessMode(mode);
        if (resolvedMode == null) {
            return sendCommandFailure(source, "command.todolist.command_access_mode.invalid_value");
        }
        ModConfig.getInstance().setCommandAccessMode(resolvedMode);
        refreshAvailableCommands(source.getServer());
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.command_access_mode.success",
                getCommandAccessModeText(resolvedMode)
        );
    }

    /**
     * 输出 H2 当前连接模式、TCP 状态和最近失败信息。
     *
     * @param source 命令源
     * @return 命令执行结果
     */
    private static int sendH2Status(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        H2ConnectionProvider provider = new H2ConnectionProvider();
        H2TcpConfig tcpConfig = H2TcpConfig.load();
        H2TcpServerManager.StatusSnapshot tcpStatus = H2TcpServerManager.getStatus();
        H2StorageAvailability.StatusSnapshot storageStatus = H2StorageAvailability.getStatusSnapshot(provider.getDatabaseBasePath());
        sendFeedbackByTranslationKey(source, "command.todolist.h2.status.backend", ModConfig.getInstance().getStorageBackend().name().toLowerCase(Locale.ROOT));
        sendFeedbackByTranslationKey(source, "command.todolist.h2.status.storage", storageStatus.isAvailable() ? "available" : "unavailable");
        sendFeedbackByTranslationKey(source, "command.todolist.h2.status.tcp_enabled", tcpConfig.isTcpEnabled() ? "true" : "false");
        sendFeedbackByTranslationKey(source, "command.todolist.h2.status.mode", tcpStatus.getMode());
        if (tcpStatus.isTcpActive()) {
            sendFeedbackByTranslationKey(
                    source,
                    "command.todolist.h2.status.tcp_address",
                    tcpStatus.getBindAddress(),
                    tcpStatus.getActualPort(),
                    "local"
            );
        }
        if (!storageStatus.isAvailable()) {
            sendFeedbackByTranslationKey(source, "command.todolist.h2.status.failure", storageStatus.getReason(), sanitizeStatusMessage(storageStatus.getMessage()));
        } else if (tcpStatus.getLastFailure() != null && !tcpStatus.getLastFailure().isBlank()) {
            sendFeedbackByTranslationKey(source, "command.todolist.h2.status.failure", "TCP", sanitizeStatusMessage(tcpStatus.getLastFailure()));
        }
        return sendCommandSuccess(source, COMMAND_SUCCESS, "command.todolist.h2.status", SIDE_EFFECT_NONE);
    }

    /**
     * 重启 H2 TCP Server，并输出新的连接模式。
     *
     * @param source 命令源
     * @return 命令执行结果
     */
    private static int restartH2Tcp(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        H2TcpServerManager.StatusSnapshot status = H2TcpServerManager.restart(H2TcpConfig.load());
        if (status.isTcpActive()) {
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_NONE,
                    "command.todolist.h2.restart_tcp.success",
                    status.getBindAddress(),
                    status.getActualPort()
            );
        }
        return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE, "command.todolist.h2.restart_tcp.fallback", status.getMode());
    }

    /**
     * 创建 H2 在线备份文件。
     *
     * @param source 命令源
     * @param requestedName 用户指定备份名，可为空
     * @return 命令执行结果
     */
    private static int backupH2(CommandSourceStack source, String requestedName) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        if (ModConfig.getInstance().getStorageBackend() != ModConfig.StorageBackend.H2) {
            return sendCommandFailure(source, "command.todolist.h2.backup.requires_h2");
        }
        try {
            H2BackupService.BackupResult result = new H2BackupService().backup(requestedName);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_NONE,
                    "command.todolist.h2.backup.success",
                    result.getBackupPath().toString(),
                    result.getSizeBytes()
            );
        } catch (StorageUnavailableException exception) {
            if (exception.getReason() != H2StorageAvailability.Reason.MAINTENANCE) {
                return sendStorageAwareCommandFailure(source, "command.todolist.h2.backup.failed", exception);
            }
            return sendCommandFailure(source, "command.todolist.h2.maintenance_busy", sanitizeStatusMessage(exception.getMessage()));
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to create H2 backup", exception);
            return sendCommandFailure(source, "command.todolist.h2.backup.failed");
        }
    }

    /**
     * 重新加载 H2 数据库并刷新当前内存存储上下文。
     *
     * @param source 命令源
     * @return 命令执行结果
     */
    private static int reloadH2Database(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        if (ModConfig.getInstance().getStorageBackend() != ModConfig.StorageBackend.H2) {
            return sendCommandFailure(source, "command.todolist.h2.reload_db.requires_h2");
        }
        try (H2MaintenanceLock.MaintenanceToken ignored = H2MaintenanceLock.enter("reload-db")) {
            TodoListCommon.reloadH2StorageContextFromDatabase();
            refreshAvailableCommands(source.getServer());
            return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE, "command.todolist.h2.reload_db.success");
        } catch (StorageUnavailableException exception) {
            if (exception.getReason() != H2StorageAvailability.Reason.MAINTENANCE) {
                return sendStorageAwareCommandFailure(source, "command.todolist.h2.reload_db.failed", exception);
            }
            return sendCommandFailure(source, "command.todolist.h2.maintenance_busy", sanitizeStatusMessage(exception.getMessage()));
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to reload H2 database", exception);
            return sendCommandFailure(source, "command.todolist.h2.reload_db.failed");
        }
    }

    /**
     * 执行 H2 健康检查并输出关键状态。
     *
     * @param source 命令源
     * @return 命令执行结果
     */
    private static int checkH2Health(CommandSourceStack source) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        if (ModConfig.getInstance().getStorageBackend() != ModConfig.StorageBackend.H2) {
            return sendCommandFailure(source, "command.todolist.h2.health.requires_h2");
        }
        try {
            H2HealthCheckService.HealthSnapshot snapshot = new H2HealthCheckService().check();
            sendFeedbackByTranslationKey(source, "command.todolist.h2.health.schema", snapshot.getSchemaVersion());
            sendFeedbackByTranslationKey(source, "command.todolist.h2.health.tasks", snapshot.getTaskCount());
            if (!snapshot.isHealthy()) {
                return sendCommandFailure(source, "command.todolist.h2.health.failed");
            }
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_NONE,
                    "command.todolist.h2.health.success"
            );
        } catch (StorageUnavailableException exception) {
            return sendStorageAwareCommandFailure(source, "command.todolist.h2.health.failed", exception);
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to check H2 health", exception);
            return sendCommandFailure(source, "command.todolist.h2.health.failed");
        }
    }

    /**
     * 重置 H2 TCP 指定角色密码。
     *
     * @param source 命令源
     * @param role 原始角色文本
     * @return 命令执行结果
     */
    private static int resetH2Password(CommandSourceStack source, String role) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.ADMIN) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        H2TcpAccountRole accountRole = H2TcpAccountRole.fromCommandValue(role);
        if (accountRole == null) {
            return sendCommandFailure(source, "command.todolist.h2.reset_password.invalid_role");
        }
        H2TcpConfig config = H2TcpConfig.load();
        String newPassword = H2TcpConfig.createPassword();
        try {
            updateH2AccountPassword(config, accountRole, newPassword);
            config.setPassword(accountRole, newPassword);
            config.save();
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to reset H2 TCP password for role {}", accountRole.commandValue(), exception);
            return sendCommandFailure(source, "command.todolist.h2.reset_password.failed");
        }
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.h2.reset_password.success",
                accountRole.commandValue()
        );
    }

    /**
     * 更新 H2 数据库内指定 TCP 账号密码。
     *
     * @param config 当前 H2 TCP 配置
     * @param role 账号角色
     * @param newPassword 新密码
     * @throws IOException 打开管理连接失败时抛出
     * @throws SQLException 更新数据库用户失败时抛出
     */
    private static void updateH2AccountPassword(H2TcpConfig config, H2TcpAccountRole role, String newPassword) throws IOException, SQLException {
        try (Connection connection = openH2PasswordResetConnection(config);
             Statement statement = connection.createStatement()) {
            String user = switch (role) {
                case ADMIN -> config.getAdminUser();
                case READONLY -> config.getReadonlyUser();
                case READWRITE -> config.getReadwriteUser();
            };
            statement.execute("ALTER USER " + quoteH2Identifier(user) + " SET PASSWORD '" + escapeH2Sql(newPassword) + "'");
        }
    }

    /**
     * 打开密码重置所需的管理连接。
     *
     * @param config 当前 H2 TCP 配置
     * @return 管理连接
     * @throws IOException 打开连接失败时抛出
     * @throws SQLException 打开连接失败时抛出
     */
    private static Connection openH2PasswordResetConnection(H2TcpConfig config) throws IOException, SQLException {
        H2ConnectionProvider provider = new H2ConnectionProvider();
        if (H2TcpServerManager.getStatus().isTcpActive()) {
            return java.sql.DriverManager.getConnection(provider.getJdbcUrl(), config.getAdminUser(), config.getAdminPassword());
        }
        return provider.openEmbeddedBootstrapConnection();
    }

    /**
     * 转义 H2 SQL 字符串。
     *
     * @param value 原始值
     * @return 已转义值
     */
    private static String escapeH2Sql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * 转义 H2 标识符。
     *
     * @param value 原始标识符
     * @return 引号包裹的标识符
     */
    private static String quoteH2Identifier(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }

    /**
     * 清理状态命令中的敏感片段，避免输出完整 JDBC URL 或密码。
     *
     * @param message 原始状态说明
     * @return 脱敏后的状态说明
     */
    private static String sanitizeStatusMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = message.replaceAll("jdbc:h2:[^\\s]+", "jdbc:h2:<hidden>");
        return sanitized.length() > 160 ? sanitized.substring(0, 160) + "..." : sanitized;
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
        TaskListPageState pageState = rememberTaskListPageState(
                player,
                new TaskListPageState(
                        null,
                        normalizeTaskListStatus(status),
                        normalizeTaskListPriority(priority),
                        normalizeTaskListText(text),
                        1
                )
        );
        return renderTaskListPage(source, player, pageState);
    }

    /**
     * 记录玩家最近一次任务列表查询的筛选条件与当前页码，供 more/prev 翻页复用。
     */
    private static final class TaskListPageState {
        private final String projectId;
        private final String status;
        private final String priority;
        private final String text;
        private final int page;

        /**
         * 创建一份任务列表翻页状态快照。
         *
         * @param projectId 项目 ID，null 表示个人任务列表
         * @param status 状态筛选
         * @param priority 优先级筛选
         * @param text 文本搜索条件
         * @param page 当前页码，从 1 开始
         */
        private TaskListPageState(String projectId, String status, String priority, String text, int page) {
            this.projectId = projectId;
            this.status = status;
            this.priority = priority;
            this.text = text;
            this.page = Math.max(1, page);
        }

        /**
         * 基于当前筛选条件创建一份新的页码状态。
         *
         * @param nextPage 目标页码
         * @return 带有新页码的翻页状态
         */
        private TaskListPageState withPage(int nextPage) {
            return new TaskListPageState(projectId, status, priority, text, nextPage);
        }
    }

    /**
     * 承载一次任务列表渲染所需的数据，避免 personal/listp 两条链路重复组织摘要信息。
     */
    private static final class ResolvedTaskListPage {
        private final List<Task> filteredTasks;
        private final Supplier<Component> summarySupplier;
        private final String emptyTranslationKey;
        private final String searchText;

        /**
         * 创建一份可直接用于分页渲染的任务列表结果。
         *
         * @param filteredTasks 经过筛选并排序后的任务列表
         * @param summarySupplier 顶部摘要文案
         * @param emptyTranslationKey 空列表文案翻译键
         * @param searchText 当前文本搜索条件
         */
        private ResolvedTaskListPage(
                List<Task> filteredTasks,
                Supplier<Component> summarySupplier,
                String emptyTranslationKey,
                String searchText
        ) {
            this.filteredTasks = filteredTasks;
            this.summarySupplier = summarySupplier;
            this.emptyTranslationKey = emptyTranslationKey;
            this.searchText = searchText;
        }
    }

    /**
     * 成员输入解析结果：统一承载成员 UUID 与展示名称，便于命令侧复用。
     */
    private static final class ResolvedMemberTarget {
        private final String uuid;
        private final String displayName;

        /**
         * 创建成员解析结果对象。
         *
         * @param uuid 成员 UUID
         * @param displayName 成员展示名称
         */
        private ResolvedMemberTarget(String uuid, String displayName) {
            this.uuid = uuid;
            this.displayName = displayName;
        }
    }

    /**
     * 按指定项目输出任务列表，支持个人项目与团队项目共用同一套筛选参数。
     */
    private static int sendTaskListByProject(CommandSourceStack source, String projectId, String status, String priority, String text) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        TaskListPageState pageState = rememberTaskListPageState(
                player,
                new TaskListPageState(
                        projectId == null ? null : projectId.trim(),
                        normalizeTaskListStatus(status),
                        normalizeTaskListPriority(priority),
                        normalizeTaskListText(text),
                        1
                )
        );
        return renderTaskListPage(source, player, pageState);
    }

    /**
     * 基于玩家最近一次任务列表查询继续向前或向后翻页。
     *
     * @param source 命令源
     * @param delta 页码变化量，1 表示下一页，-1 表示上一页
     * @return 命令执行结果
     */
    private static int executeTaskListPageTurn(CommandSourceStack source, int delta) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.VIEW) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        TaskListPageState currentState = taskListPageStateMap.get(player.getStringUUID());
        if (currentState == null) {
            return sendCommandFailure(source, "command.todolist.task.list.page.no_session");
        }
        if (delta < 0 && currentState.page <= 1) {
            return sendCommandFailure(source, "command.todolist.task.list.page.already_first");
        }
        return renderTaskListPage(source, player, currentState.withPage(currentState.page + delta));
    }

    /**
     * 将一次任务列表查询保存为玩家的当前翻页上下文。
     *
     * @param player 当前玩家
     * @param pageState 最新的列表翻页状态
     * @return 已保存的列表翻页状态
     */
    private static TaskListPageState rememberTaskListPageState(ServerPlayer player, TaskListPageState pageState) {
        taskListPageStateMap.put(player.getStringUUID(), pageState);
        return pageState;
    }

    /**
     * 根据保存的筛选条件重新加载任务列表，并按目标页码输出对应片段。
     *
     * @param source 命令源
     * @param player 当前玩家
     * @param pageState 翻页状态
     * @return 命令执行结果
     */
    private static int renderTaskListPage(CommandSourceStack source, ServerPlayer player, TaskListPageState pageState) {
        try {
            ResolvedTaskListPage resolvedPage = resolveTaskListPage(source, player, pageState);
            if (resolvedPage == null) {
                taskListPageStateMap.remove(player.getStringUUID());
                return COMMAND_FAILURE;
            }

            List<Task> filteredTasks = resolvedPage.filteredTasks;
            sendFeedback(source, resolvedPage.summarySupplier);
            if (filteredTasks.isEmpty()) {
                taskListPageStateMap.remove(player.getStringUUID());
                sendFeedbackByTranslationKey(source, resolvedPage.emptyTranslationKey);
                return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
            }

            int totalCount = filteredTasks.size();
            int totalPages = Math.max(1, (totalCount + TASK_LIST_MAX_SUMMARY - 1) / TASK_LIST_MAX_SUMMARY);
            if (pageState.page > totalPages) {
                return sendCommandFailure(source, "command.todolist.task.list.page.already_last");
            }

            int startIndex = (pageState.page - 1) * TASK_LIST_MAX_SUMMARY;
            int endExclusive = Math.min(totalCount, startIndex + TASK_LIST_MAX_SUMMARY);
            sendFeedbackByTranslationKey(
                    source,
                    "command.todolist.task.list.page.status",
                    pageState.page,
                    totalPages,
                    startIndex + 1,
                    endExclusive,
                    totalCount
            );
            for (int index = startIndex; index < endExclusive; index++) {
                int displayIndex = index + 1;
                Task task = filteredTasks.get(index);
                sendFeedback(source, () -> buildTaskListItem(displayIndex, task, resolvedPage.searchText));
            }

            boolean hasPrev = pageState.page > 1;
            boolean hasNext = pageState.page < totalPages;
            if (hasPrev || hasNext) {
                sendFeedback(source, () -> buildTaskListPageButtons(hasPrev, hasNext));
            }
            rememberTaskListPageState(player, pageState);
            return sendCommandSuccess(source, COMMAND_SUCCESS, SIDE_EFFECT_NONE);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to render task list page for command", e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.list.failed", e);
        }
    }

    /**
     * 解析当前翻页状态对应的任务列表数据，并生成摘要与空状态文案。
     *
     * @param source 命令源
     * @param player 当前玩家
     * @param pageState 翻页状态
     * @return 一份可直接用于渲染的任务列表结果
     * @throws IOException 读取任务存储失败时抛出
     */
    private static ResolvedTaskListPage resolveTaskListPage(
            CommandSourceStack source,
            ServerPlayer player,
            TaskListPageState pageState
    ) throws IOException {
        if (pageState.projectId == null || pageState.projectId.isBlank()) {
            List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), TodoListCommon.getTaskStorage(), player.getUUID());
            int totalCount = tasks.size();
            int completedCount = (int) tasks.stream().filter(Task::isCompleted).count();
            List<Task> filteredTasks = tasks.stream()
                    .filter(task -> matchesTaskStatus(task, pageState.status))
                    .filter(task -> matchesTaskPriority(task, pageState.priority))
                    .filter(task -> matchesTaskText(task, pageState.text))
                    .sorted(buildTaskSummaryComparator())
                    .toList();
            return new ResolvedTaskListPage(
                    filteredTasks,
                    () -> Component.translatable("command.todolist.task.list.summary", totalCount, completedCount),
                    "command.todolist.task.list.empty",
                    pageState.text
            );
        }

        Project project = resolveProjectForCommand(source, player, pageState.projectId, "command.todolist.task.project.not_found");
        if (project == null) {
            return null;
        }
        if (!canViewProjectTasks(player, project)) {
            sendCommandFailure(source, "command.todolist.task.project.permission_denied");
            return null;
        }
        List<Task> scopedTasks = loadProjectTasksForCommand(source, player, project).stream()
                .filter(task -> task != null && task.belongsToProject(project.getId()))
                .sorted(buildTaskSummaryComparator())
                .toList();
        int totalCount = scopedTasks.size();
        int completedCount = (int) scopedTasks.stream().filter(Task::isCompleted).count();
        List<Task> filteredTasks = scopedTasks.stream()
                .filter(task -> matchesTaskStatus(task, pageState.status))
                .filter(task -> matchesTaskPriority(task, pageState.priority))
                .filter(task -> matchesTaskText(task, pageState.text))
                .toList();
        return new ResolvedTaskListPage(
                filteredTasks,
                () -> Component.translatable(
                        "command.todolist.task.project.list.summary",
                        buildProjectNameComponent(project),
                        totalCount,
                        completedCount
                ),
                "command.todolist.task.project.list.empty",
                pageState.text
        );
    }

    /**
     * 规范化任务列表的文本搜索条件，空白输入统一视为 null。
     *
     * @param text 原始文本搜索条件
     * @return 规范化后的文本搜索条件
     */
    private static String normalizeTaskListText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }

    /**
     * 构建任务列表翻页按钮行，便于玩家继续查看更多或返回上一页。
     *
     * @param hasPrev 是否展示上一页按钮
     * @param hasNext 是否展示下一页按钮
     * @return 翻页按钮组件
     */
    private static Component buildTaskListPageButtons(boolean hasPrev, boolean hasNext) {
        MutableComponent line = Component.empty();
        if (hasPrev) {
            line.append(buildTaskListPageButton(
                    "command.todolist.task.list.page.prev_button",
                    "/todo task prev",
                    "command.todolist.task.list.page.prev_hover"
            ));
        }
        if (hasPrev && hasNext) {
            line.append(Component.literal(" "));
        }
        if (hasNext) {
            line.append(buildTaskListPageButton(
                    "command.todolist.task.list.page.next_button",
                    "/todo task more",
                    "command.todolist.task.list.page.next_hover"
            ));
        }
        return line;
    }

    /**
     * 构建单个任务列表翻页按钮，并附带点击与悬停提示。
     *
     * @param labelKey 按钮文案翻译键
     * @param command 点击后执行的命令
     * @param hoverKey 悬停提示翻译键
     * @return 可点击的按钮组件
     */
    private static Component buildTaskListPageButton(String labelKey, String command, String hoverKey) {
        return Component.translatable(labelKey)
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(hoverKey)
                        )));
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
            List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), storage, playerUuid);
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
            savePersonalTasksForCommand(source.getServer(), storage, playerUuid, tasks);
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
            return sendStorageAwareCommandFailure(source, "command.todolist.task.add.failed", e);
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
     * 按命令执行所在服务端的运行模式读取个人任务，保证与单人 GUI 使用同一份数据。
     */
    private static List<Task> loadPersonalTasksForCommand(MinecraftServer server, TaskStorage storage, UUID playerUuid) throws IOException {
        return storage.loadPersonalTasks(server, playerUuid);
    }

    /**
     * 判断给定任务在列表中是否存在直属子任务（即父任务）。
     * 父任务完成态由子任务聚合决定，禁止为其设置事件触发器。
     *
     * @param tasks 任务列表
     * @param task  目标任务
     * @return 存在直属子任务时返回 true
     */
    private static boolean hasDirectSubtasksInList(List<Task> tasks, Task task) {
        if (tasks == null || task == null || task.getId() == null) {
            return false;
        }
        for (Task candidate : tasks) {
            if (candidate != null && task.getId().equals(candidate.getParentTaskId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按命令执行所在服务端的运行模式保存个人任务，避免单人模式写入到错误的玩家文件。
     */
    private static void savePersonalTasksForCommand(MinecraftServer server, TaskStorage storage, UUID playerUuid, List<Task> tasks) throws IOException {
        storage.savePersonalTasks(server, playerUuid, tasks);
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

    /**
     * 解析命令中引用的项目对象，并统一处理“不可见/不存在”错误提示。
     */
    private static Project resolveProjectForCommand(CommandSourceStack source, ServerPlayer player, String projectId, String notFoundTranslationKey) {
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            sendCommandFailure(source, notFoundTranslationKey, projectId);
            return null;
        }
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null || !isProjectVisibleToPlayer(project, player.getStringUUID(), source.getServer())) {
            sendCommandFailure(source, notFoundTranslationKey, normalizedProjectId);
            return null;
        }
        return project;
    }

    /**
     * 按项目范围读取命令所需的任务列表。
     */
    private static List<Task> loadProjectTasksForCommand(CommandSourceStack source, ServerPlayer player, Project project) throws IOException {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        if (project.getScope() == Project.Scope.TEAM) {
            return new ArrayList<>(storage.loadTeamTasks());
        }
        return new ArrayList<>(loadPersonalTasksForCommand(source.getServer(), storage, player.getUUID()));
    }

    /**
     * 判断当前玩家是否可以查看指定项目下的任务列表。
     */
    private static boolean canViewProjectTasks(ServerPlayer player, Project project) {
        if (player == null || project == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        if (project.getScope() == Project.Scope.PERSONAL) {
            String ownerUuid = project.getOwnerUuid();
            return ownerUuid == null || ownerUuid.isEmpty() || ownerUuid.equals(player.getStringUUID());
        }
        return isTeamProjectMember(player, project);
    }

    /**
     * 按项目范围保存命令修改后的任务列表，并触发对应同步。
     */
    private static void saveProjectTasksForCommand(
            CommandSourceStack source,
            ServerPlayer player,
            Project project,
            TaskStorage storage,
            List<Task> tasks
    ) throws IOException {
        if (project.getScope() == Project.Scope.TEAM) {
            // 先合并引擎内存中的触发器进度，避免命令保存旧快照覆盖尚未落库的进度
            TaskTriggerService.mergeTeamTriggerStateInto(tasks);
            // 领取/放弃/改派命令后，领取人发生变化的团队任务需要清零触发器进度
            // （必须在合并之后，否则重置会被引擎进度覆盖）
            TaskTriggerService.resetTriggerProgressOnAssigneeChange(storage.loadTeamTasks(), tasks);
            storage.saveTeamTasks(tasks);
            TaskPackets.broadcastTeamTasks(source.getServer());
            return;
        }
        savePersonalTasksForCommand(source.getServer(), storage, player.getUUID(), tasks);
        syncTasksToPlayer(source.getServer(), player);
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
        return CommandInputNormalizer.normalizeTaskListStatus(status);
    }

    private static String normalizeTaskListPriority(String priority) {
        return CommandInputNormalizer.normalizeTaskListPriority(priority);
    }

    private static String normalizeTaskCleanScope(String scope) {
        return CommandInputNormalizer.normalizeTaskCleanScope(scope);
    }

    private static String normalizeTaskCleanProjectSelector(String selector) {
        return CommandInputNormalizer.normalizeTaskCleanProjectSelector(selector);
    }

    private static String normalizeTaskCleanStatus(String status) {
        return CommandInputNormalizer.normalizeTaskCleanStatus(status);
    }

    private static String normalizeProjectScope(String scope) {
        return CommandInputNormalizer.normalizeProjectScope(scope);
    }

    private static String normalizeProjectListMode(String mode) {
        return CommandInputNormalizer.normalizeProjectListMode(mode);
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
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
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
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
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
                    : loadPersonalTasksForCommand(source.getServer(), storage, player.getUUID());
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
                sendStorageAwareCommandFailure(source, "command.todolist.task.clean.failed", e);
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
                List<Task> tasks = new ArrayList<>(loadPersonalTasksForCommand(source.getServer(), storage, player.getUUID()));
                int before = tasks.size();
                tasks.removeIf(task -> matchesTaskCleanRequest(task, request));
                removedCount = before - tasks.size();
                savePersonalTasksForCommand(source.getServer(), storage, player.getUUID(), tasks);
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
            return sendStorageAwareCommandFailure(source, "command.todolist.task.clean.failed", e);
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

    /**
     * 将命令输入解析为配置中的命令权限模式。
     *
     * @param mode 原始权限模式参数
     * @return 命中的命令权限模式；非法值返回 null
     */
    private static ModConfig.CommandAccessMode resolveCommandAccessMode(String mode) {
        String normalizedMode = CommandInputNormalizer.normalizeCommandAccessMode(mode);
        return switch (normalizedMode) {
            case "op_only" -> ModConfig.CommandAccessMode.OP_ONLY;
            case "view_only" -> ModConfig.CommandAccessMode.VIEW_ONLY;
            case "full" -> ModConfig.CommandAccessMode.FULL;
            default -> null;
        };
    }

    /**
     * 构建命令权限模式的本地化文本组件。
     *
     * @param mode 命令权限模式
     * @return 模式文本组件
     */
    private static Component getCommandAccessModeText(ModConfig.CommandAccessMode mode) {
        String normalizedMode = mode == null ? "op_only" : mode.name().toLowerCase(Locale.ROOT);
        return Component.translatable("command.todolist.command_access_mode.mode." + normalizedMode);
    }

    /**
     * 在命令权限模式变化后刷新所有在线玩家的命令树。
     *
     * @param server 当前服务端
     */
    private static void refreshAvailableCommands(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        try {
            if (server.getCommands() == null) {
                return;
            }
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                if (online != null) {
                    server.getCommands().sendCommands(online);
                }
            }
        } catch (RuntimeException ignored) {
            // 测试桩服务端可能不具备完整命令分发能力，这里保持静默即可。
        }
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
        if (project == null || !isProjectVisibleToPlayer(project, player.getStringUUID(), source.getServer())) {
            return sendCommandFailure(source, "command.todolist.project.select.not_found", normalizedProjectId);
        }
        if (project.getScope() == Project.Scope.TEAM && isSingleplayerServer(source.getServer())) {
            return sendCommandFailure(source, "command.todolist.project.select.singleplayer_team_forbidden");
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
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

        List<Project> visibleProjects = resolveProjectsForListMode(player, source.getServer(), normalizedMode);
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

    private static List<Project> resolveProjectsForListMode(ServerPlayer player, MinecraftServer server, String mode) {
        ProjectManager projectManager = TodoListCommon.getProjectManager();
        String playerUuid = player.getStringUUID();
        if ("current".equals(mode)) {
            String activeProjectId = ProjectPackets.getActiveProjectId(player);
            Project project = activeProjectId == null ? null : projectManager.getProject(activeProjectId);
            if (project == null || !isProjectVisibleToPlayer(project, playerUuid, server)) {
                return List.of();
            }
            return List.of(project);
        }
        if ("star".equals(mode)) {
            return ProjectPackets.getHudStarredProjectIds(player).stream()
                    .map(projectManager::getProject)
                    .filter(project -> project != null && isProjectVisibleToPlayer(project, playerUuid, server))
                    .distinct()
                    .sorted(buildProjectSummaryComparator())
                    .toList();
        }
        return projectManager.getAllProjects().stream()
                .filter(project -> isProjectVisibleToPlayer(project, playerUuid, server))
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
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
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

    /**
     * 重命名指定项目，并在成功后同步项目列表。
     */
    private static int executeProjectRename(CommandSourceStack source, String projectId, String name) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.rename.not_found", projectId);
        }
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null) {
            return sendCommandFailure(source, "command.todolist.project.rename.not_found", normalizedProjectId);
        }
        if (!canEditProject(source, player, project)) {
            return sendCommandFailure(source, "command.todolist.project.rename.no_permission");
        }
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.create.invalid_name");
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        project.setName(normalizedName);
        TodoListCommon.getProjectManager().updateProject(project);
        saveProjects(source.getServer(), project.getScope());
        refreshProjectsAfterMutation(source.getServer(), player, project.getScope());
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.project.rename.success",
                buildProjectNameComponent(project)
        );
    }

    /**
     * 设置团队项目的“成员可创建任务”开关，并在成功后同步项目列表。
     */
    private static int executeProjectAllowMemberCreate(CommandSourceStack source, String projectId, String enabled) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.member_create.not_found", projectId);
        }
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null) {
            return sendCommandFailure(source, "command.todolist.project.member_create.not_found", normalizedProjectId);
        }
        if (project.getScope() != Project.Scope.TEAM) {
            return sendCommandFailure(source, "command.todolist.project.member_create.team_only");
        }
        if (!canEditProject(source, player, project)) {
            return sendCommandFailure(source, "command.todolist.project.member_create.no_permission");
        }
        Boolean normalizedEnabled = CommandInputNormalizer.normalizeToggleState(enabled);
        if (normalizedEnabled == null) {
            return sendCommandFailure(source, "command.todolist.project.member_create.invalid_value");
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        project.setAllowMemberCreate(normalizedEnabled);
        TodoListCommon.getProjectManager().updateProject(project);
        saveProjects(source.getServer(), project.getScope());
        refreshProjectsAfterMutation(source.getServer(), player, project.getScope());
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.project.member_create.success",
                buildProjectNameComponent(project),
                getCommandToggleText(normalizedEnabled)
        );
    }

    /**
     * 设置团队项目“允许所有玩家领取/放弃/完成任务”开关，并在成功后同步项目列表。
     */
    private static int executeProjectAllowAllPlayersClaimComplete(CommandSourceStack source, String projectId, String enabled) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        String normalizedProjectId = projectId == null ? "" : projectId.trim();
        if (normalizedProjectId.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.all_player_claim_complete.not_found", projectId);
        }
        Project project = TodoListCommon.getProjectManager().getProject(normalizedProjectId);
        if (project == null) {
            return sendCommandFailure(source, "command.todolist.project.all_player_claim_complete.not_found", normalizedProjectId);
        }
        if (project.getScope() != Project.Scope.TEAM) {
            return sendCommandFailure(source, "command.todolist.project.all_player_claim_complete.team_only");
        }
        if (!canEditProject(source, player, project)) {
            return sendCommandFailure(source, "command.todolist.project.all_player_claim_complete.no_permission");
        }
        Boolean normalizedEnabled = CommandInputNormalizer.normalizeToggleState(enabled);
        if (normalizedEnabled == null) {
            return sendCommandFailure(source, "command.todolist.project.all_player_claim_complete.invalid_value");
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        project.setAllowAllPlayersClaimComplete(normalizedEnabled);
        TodoListCommon.getProjectManager().updateProject(project);
        saveProjects(source.getServer(), project.getScope());
        refreshProjectsAfterMutation(source.getServer(), player, project.getScope());
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.project.all_player_claim_complete.success",
                buildProjectNameComponent(project),
                getCommandToggleText(normalizedEnabled)
        );
    }

    /**
     * 向团队项目添加成员，支持在线玩家名或 UUID 输入。
     */
    /**
     * 调整项目的 HUD 星标状态，并同步到客户端本地配置。
     */
    private static int executeProjectStarState(CommandSourceStack source, String projectId, boolean starred) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.HUD_CONTROL) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveProjectForCommand(source, player, projectId, "command.todolist.project.star.not_found");
        if (project == null) {
            return COMMAND_FAILURE;
        }
        List<String> currentProjectIds = ProjectPackets.getHudStarredProjectIds(player);
        boolean alreadyStarred = currentProjectIds.contains(project.getId());
        if (starred == alreadyStarred) {
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_NONE,
                    starred ? "command.todolist.project.star.already_starred" : "command.todolist.project.unstar.not_starred",
                    buildProjectNameComponent(project)
            );
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        List<String> updatedProjectIds = CommandInputNormalizer.applyHudStarredProjectState(currentProjectIds, project.getId(), starred);
        ProjectPackets.setHudStarredProjectIds(player, updatedProjectIds);
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_REFRESH_HUD,
                starred ? "command.todolist.project.star.success" : "command.todolist.project.unstar.success",
                buildProjectNameComponent(project)
        );
    }

    /**
     * 向团队项目添加成员，支持在线玩家名或 UUID 输入。
     */
    private static int executeProjectMemberAdd(CommandSourceStack source, String projectId, String memberToken) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveTeamProjectForMemberCommand(source, player, projectId);
        if (project == null) {
            return COMMAND_FAILURE;
        }
        if (!canManageProjectMember(player, project, null, Operation.ADD_MEMBER)) {
            return sendCommandFailure(source, "command.todolist.project.member.add.no_permission");
        }
        ResolvedMemberTarget target = resolveMemberTarget(source.getServer(), memberToken);
        if (target == null) {
            return sendCommandFailure(source, "command.todolist.project.member.add.invalid_target", memberToken);
        }
        if (target.uuid.equals(project.getOwnerUuid()) || project.getMemberRole(target.uuid) != null) {
            return sendCommandFailure(source, "command.todolist.project.member.add.already_exists", target.displayName);
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        project.addMember(target.uuid, Project.ProjectRole.MEMBER, target.displayName);
        ProjectPackets.clearPendingJoinRequest(project.getId(), target.uuid);
        TodoListCommon.getProjectManager().updateProject(project);
        saveProjects(source.getServer(), project.getScope());
        refreshProjectsAfterMutation(source.getServer(), player, project.getScope());
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.project.member.add.success",
                buildProjectNameComponent(project),
                Component.literal(target.displayName)
        );
    }

    /**
     * 从团队项目移除成员，并同步项目列表。
     */
    private static int executeProjectMemberRemove(CommandSourceStack source, String projectId, String memberUuid) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveTeamProjectForMemberCommand(source, player, projectId);
        if (project == null) {
            return COMMAND_FAILURE;
        }
        String normalizedMemberUuid = memberUuid == null ? "" : memberUuid.trim();
        if (normalizedMemberUuid.isEmpty() || project.getMemberRole(normalizedMemberUuid) == null) {
            return sendCommandFailure(source, "command.todolist.project.member.target.not_found", memberUuid);
        }
        if (!canManageProjectMember(player, project, normalizedMemberUuid, Operation.REMOVE_MEMBER)) {
            return sendCommandFailure(source, "command.todolist.project.member.remove.no_permission");
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        String memberName = getProjectMemberDisplayName(project, normalizedMemberUuid);
        project.removeMember(normalizedMemberUuid);
        TodoListCommon.getProjectManager().updateProject(project);
        saveProjects(source.getServer(), project.getScope());
        refreshProjectsAfterMutation(source.getServer(), player, project.getScope());
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.project.member.remove.success",
                buildProjectNameComponent(project),
                Component.literal(memberName)
        );
    }

    /**
     * 修改团队项目成员角色，目前支持 MEMBER 与 LEAD 之间切换。
     */
    private static int executeProjectMemberRole(CommandSourceStack source, String projectId, String memberUuid, String role) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveTeamProjectForMemberCommand(source, player, projectId);
        if (project == null) {
            return COMMAND_FAILURE;
        }
        String normalizedMemberUuid = memberUuid == null ? "" : memberUuid.trim();
        if (normalizedMemberUuid.isEmpty() || project.getMemberRole(normalizedMemberUuid) == null) {
            return sendCommandFailure(source, "command.todolist.project.member.target.not_found", memberUuid);
        }
        if (!canManageProjectMember(player, project, normalizedMemberUuid, Operation.CHANGE_MEMBER_ROLE)) {
            return sendCommandFailure(source, "command.todolist.project.member.role.no_permission");
        }
        String normalizedRole = CommandInputNormalizer.normalizeProjectMemberRole(role);
        if (normalizedRole.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.project.member.role.invalid_value");
        }
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        Project.ProjectRole newRole = "lead".equals(normalizedRole) ? Project.ProjectRole.LEAD : Project.ProjectRole.MEMBER;
        String memberName = getProjectMemberDisplayName(project, normalizedMemberUuid);
        project.addMember(normalizedMemberUuid, newRole, memberName);
        TodoListCommon.getProjectManager().updateProject(project);
        saveProjects(source.getServer(), project.getScope());
        refreshProjectsAfterMutation(source.getServer(), player, project.getScope());
        return sendCommandSuccess(
                source,
                COMMAND_SUCCESS,
                SIDE_EFFECT_PERSIST_DATA,
                "command.todolist.project.member.role.success",
                buildProjectNameComponent(project),
                Component.literal(memberName),
                getProjectMemberRoleText(newRole)
        );
    }

    private static int requestProjectRemoveConfirm(CommandSourceStack source, String projectId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
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
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
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

    /**
     * 判断玩家是否具备编辑指定项目的能力。
     */
    private static boolean canEditProject(CommandSourceStack source, ServerPlayer player, Project project) {
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
        Role role = resolveProjectRole(player, project);
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false);
        return PermissionCenter.canPerform(Operation.EDIT_PROJECT, role, ctx);
    }

    /**
     * 解析成员管理命令使用的团队项目，并统一处理范围与可见性校验。
     */
    /**
     * 解析团队任务命令使用的团队项目，并统一处理可见性与项目类型校验。
     */
    private static Project resolveTeamProjectForTaskCommand(CommandSourceStack source, ServerPlayer player, String projectId) {
        Project project = resolveProjectForCommand(source, player, projectId, "command.todolist.task.project.not_found");
        if (project == null) {
            return null;
        }
        if (project.getScope() != Project.Scope.TEAM) {
            sendCommandFailure(source, "command.todolist.task.project.team_only");
            return null;
        }
        return project;
    }

    /**
     * 解析成员管理命令使用的团队项目，并统一处理可见性与项目类型校验。
     */
    private static Project resolveTeamProjectForMemberCommand(CommandSourceStack source, ServerPlayer player, String projectId) {
        Project project = resolveProjectForCommand(source, player, projectId, "command.todolist.project.member.project_not_found");
        if (project == null) {
            return null;
        }
        if (project.getScope() != Project.Scope.TEAM) {
            sendCommandFailure(source, "command.todolist.project.member.team_only");
            return null;
        }
        return project;
    }

    /**
     * 判断当前玩家是否可以对目标成员执行指定成员管理操作。
     */
    private static boolean canManageProjectMember(ServerPlayer actor, Project project, String targetMemberUuid, Operation operation) {
        if (actor == null || project == null) {
            return false;
        }
        String actorUuid = actor.getStringUUID();
        boolean targetSelf = targetMemberUuid != null && targetMemberUuid.equals(actorUuid);
        boolean targetProjectManager = targetMemberUuid != null && targetMemberUuid.equals(project.getOwnerUuid());
        Role role = resolveProjectRole(actor, project);
        Context context = new Context(ViewScope.TEAM_ALL, false, false, false, targetSelf, targetProjectManager, isTeamProjectMember(actor, project));
        return PermissionCenter.canPerform(operation, role, context);
    }

    /**
     * 将成员输入解析为 UUID 与展示名。
     */
    private static ResolvedMemberTarget resolveMemberTarget(MinecraftServer server, String memberToken) {
        String normalizedToken = memberToken == null ? "" : memberToken.trim();
        if (normalizedToken.isEmpty()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(normalizedToken);
            ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(uuid);
            String displayName = online == null ? normalizedToken : online.getName().getString();
            return new ResolvedMemberTarget(uuid.toString(), displayName);
        } catch (IllegalArgumentException ignored) {
            if (server == null) {
                return null;
            }
            ServerPlayer online = server.getPlayerList().getPlayerByName(normalizedToken);
            if (online == null) {
                return null;
            }
            return new ResolvedMemberTarget(online.getStringUUID(), online.getName().getString());
        }
    }

    /**
     * 解析团队任务指派目标，只允许项目经理、负责人或成员本人已在项目中的成员被选中。
     */
    private static ResolvedMemberTarget resolveProjectAssignmentTarget(Project project, MinecraftServer server, String memberToken) {
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return null;
        }
        String normalizedToken = CommandInputNormalizer.normalizeProjectId(memberToken);
        if (normalizedToken.isEmpty()) {
            return null;
        }

        ResolvedMemberTarget directTarget = resolveMemberTarget(server, normalizedToken);
        if (directTarget != null && isProjectAssignableMember(project, directTarget.uuid)) {
            String displayName = getProjectMemberDisplayName(project, directTarget.uuid);
            if (displayName == null || displayName.isBlank()) {
                displayName = directTarget.displayName;
            }
            return new ResolvedMemberTarget(directTarget.uuid, displayName);
        }

        for (Map.Entry<String, Project.ProjectRole> entry : project.getMembers().entrySet()) {
            String memberUuid = entry.getKey();
            String displayName = getProjectMemberDisplayName(project, memberUuid);
            if (displayName != null && !displayName.isBlank() && displayName.equalsIgnoreCase(normalizedToken)) {
                return new ResolvedMemberTarget(memberUuid, displayName);
            }
        }
        return null;
    }

    /**
     * 判断目标成员是否属于当前团队项目，可作为任务指派目标。
     */
    private static boolean isProjectAssignableMember(Project project, String memberUuid) {
        if (project == null || memberUuid == null || memberUuid.isBlank()) {
            return false;
        }
        if (memberUuid.equals(project.getOwnerUuid())) {
            return true;
        }
        return project.getMemberRole(memberUuid) != null;
    }

    /**
     * 获取项目内成员的展示名称，不存在缓存时回退到 UUID。
     */
    private static String getProjectMemberDisplayName(Project project, String memberUuid) {
        if (project == null || memberUuid == null || memberUuid.isBlank()) {
            return "";
        }
        String memberName = project.getMemberName(memberUuid);
        return memberName == null || memberName.isBlank() ? memberUuid : memberName;
    }

    /**
     * 将项目成员角色转换为本地化文本。
     */
    private static Component getProjectMemberRoleText(Project.ProjectRole role) {
        if (role == Project.ProjectRole.LEAD) {
            return Component.translatable("gui.todolist.role.lead");
        }
        return Component.translatable("gui.todolist.role.member");
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
        if (ensureStorageAvailableForCommand(source) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        String projectId = project.getId();
        Project.Scope scope = project.getScope();
        ProjectPackets.clearPendingJoinRequestsForProject(projectId);
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

    /**
     * 将命令开关结果转换为统一的本地化文本。
     */
    private static Component getCommandToggleText(boolean enabled) {
        return Component.translatable(enabled ? "gui.todolist.config.toggle.on" : "gui.todolist.config.toggle.off");
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
     * 判断当前服务端是否处于“团队项目不可用”的本地单人状态。
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

    /**
     * 判断命令来源是否可以对指定项目任务执行目标操作。
     */
    private static boolean canOperateProjectTask(
            CommandSourceStack source,
            ServerPlayer player,
            Project project,
            Task task,
            Operation operation
    ) {
        if (source.hasPermission(2)) {
            return true;
        }
        if (project == null || task == null) {
            return false;
        }
        if (project.getScope() == Project.Scope.PERSONAL) {
            String ownerUuid = project.getOwnerUuid();
            return ownerUuid == null || ownerUuid.isEmpty() || ownerUuid.equals(player.getStringUUID());
        }
        Role role = resolveProjectRole(player, project);
        boolean assigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean assigneeSelf = assigned && player.getStringUUID().equals(task.getAssigneeUuid());
        boolean projectMember = isTeamProjectMember(player, project);
        ViewScope scope;
        if (!assigned) {
            scope = ViewScope.TEAM_UNASSIGNED;
        } else if (assigneeSelf) {
            scope = ViewScope.TEAM_ASSIGNED;
        } else {
            scope = ViewScope.TEAM_ALL;
        }
        Context context = new Context(
                scope,
                task.isCompleted(),
                assigned,
                assigneeSelf,
                false,
                false,
                projectMember,
                project.isAllowMemberCreate(),
                project.isAllowAllPlayersClaimComplete()
        );
        return PermissionCenter.canPerform(operation, role, context);
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
            List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), storage, playerUuid);
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
            savePersonalTasksForCommand(source.getServer(), storage, playerUuid, tasks);
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
            return sendStorageAwareCommandFailure(source, "command.todolist.task.done.failed", e);
        }
    }

    /**
     * 为当前玩家的个人任务设置事件触发器。
     */
    private static int executeTaskTriggerSet(CommandSourceStack source, String taskId, String typeName, String target, int count) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        TaskTrigger.Type type = parseTaskTriggerType(typeName);
        if (type == null) {
            return sendCommandFailure(source, "command.todolist.task.trigger.set.invalid_type", typeName);
        }
        String normalizedTarget = target == null ? "" : target.trim();
        if (normalizedTarget.isEmpty()) {
            return sendCommandFailure(source, "command.todolist.task.trigger.set.invalid_target");
        }
        UUID playerUuid = player.getUUID();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), storage, playerUuid);
            TaskTriggerService.mergeTriggerStateInto(source.getServer(), playerUuid, tasks);
            Task task = findTaskById(tasks, taskId);
            if (task == null) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }
            if (hasDirectSubtasksInList(tasks, task)) {
                return sendCommandFailure(source, "command.todolist.task.trigger.set.parent_not_allowed", task.getTitle());
            }
            task.setTrigger(new TaskTrigger(type, normalizedTarget, count));
            savePersonalTasksForCommand(source.getServer(), storage, playerUuid, tasks);
            TaskTriggerService.invalidatePersonal(source.getServer(), playerUuid);
            if (type == TaskTrigger.Type.ITEM_COLLECT) {
                // 玩家可能已持有足量物品，设置后立即评估一次
                TaskTriggerService.evaluateItemCollectAfterTriggerChange(player);
            }
            syncTasksToPlayer(source.getServer(), player);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.trigger.set.success",
                    task.getTitle(), type.name().toLowerCase(java.util.Locale.ROOT), normalizedTarget, count
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to set task trigger for command", e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.trigger.set.failed", e);
        }
    }

    /**
     * 清除当前玩家个人任务的事件触发器。
     */
    private static int executeTaskTriggerClear(CommandSourceStack source, String taskId) {
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
            List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), storage, playerUuid);
            TaskTriggerService.mergeTriggerStateInto(source.getServer(), playerUuid, tasks);
            Task task = findTaskById(tasks, taskId);
            if (task == null) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }
            if (task.getTrigger() == null) {
                return sendCommandSuccess(
                        source,
                        COMMAND_SUCCESS,
                        SIDE_EFFECT_NONE,
                        "command.todolist.task.trigger.clear.not_set",
                        task.getTitle()
                );
            }
            task.setTrigger(null);
            savePersonalTasksForCommand(source.getServer(), storage, playerUuid, tasks);
            TaskTriggerService.invalidatePersonal(source.getServer(), playerUuid);
            syncTasksToPlayer(source.getServer(), player);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.trigger.clear.success",
                    task.getTitle()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to clear task trigger for command", e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.trigger.clear.failed", e);
        }
    }

    /**
     * 查看当前玩家个人任务的事件触发器配置与进度。
     */
    private static int executeTaskTriggerInfo(CommandSourceStack source, String taskId) {
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
            List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), storage, playerUuid);
            TaskTriggerService.mergeTriggerStateInto(source.getServer(), playerUuid, tasks);
            Task task = findTaskById(tasks, taskId);
            if (task == null) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }
            TaskTrigger trigger = task.getTrigger();
            if (trigger == null) {
                return sendCommandSuccess(
                        source,
                        COMMAND_SUCCESS,
                        SIDE_EFFECT_NONE,
                        "command.todolist.task.trigger.info.none",
                        task.getTitle()
                );
            }
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_NONE,
                    "command.todolist.task.trigger.info.format",
                    task.getTitle(),
                    trigger.getType().name().toLowerCase(java.util.Locale.ROOT),
                    trigger.getTarget(),
                    trigger.getProgress(),
                    trigger.getTargetCount()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to read task trigger for command", e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.trigger.info.failed", e);
        }
    }

    /**
     * 解析触发器类型参数，非法输入返回 null。
     */
    private static TaskTrigger.Type parseTaskTriggerType(String typeName) {
        if (typeName == null) {
            return null;
        }
        try {
            return TaskTrigger.Type.valueOf(typeName.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 将指定项目中的目标任务标记为完成，并依据项目范围执行对应同步。
     */
    private static int executeTaskDoneByProject(CommandSourceStack source, String projectId, String taskId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveProjectForCommand(source, player, projectId, "command.todolist.task.project.not_found");
        if (project == null) {
            return COMMAND_FAILURE;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = loadProjectTasksForCommand(source, player, project);
            Task task = findTaskById(tasks, taskId);
            if (task == null || !task.belongsToProject(project.getId())) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }
            if (!canOperateProjectTask(source, player, project, task, Operation.TOGGLE_COMPLETE)) {
                return sendCommandFailure(source, "command.todolist.task.project.permission_denied");
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
            saveProjectTasksForCommand(source, player, project, storage, tasks);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.done.success",
                    task.getTitle()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to mark project task as done for command, projectId={}", project.getId(), e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.done.failed", e);
        }
    }

    /**
     * 从当前玩家任务列表删除指定任务并写回存储。
     */
    /**
     * 在团队项目中领取指定任务，并将任务指派给当前玩家。
     */
    private static int executeTaskClaimByProject(CommandSourceStack source, String projectId, String taskId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveTeamProjectForTaskCommand(source, player, projectId);
        if (project == null) {
            return COMMAND_FAILURE;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = loadProjectTasksForCommand(source, player, project);
            Task task = findTaskById(tasks, taskId);
            if (task == null || !task.belongsToProject(project.getId())) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }
            if (!canOperateProjectTask(source, player, project, task, Operation.CLAIM_TASK)) {
                return sendCommandFailure(source, "command.todolist.task.project.permission_denied");
            }
            task.setAssigneeUuid(player.getStringUUID());
            task.setAssigneeName(player.getName().getString());
            saveProjectTasksForCommand(source, player, project, storage, tasks);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.claim.success",
                    task.getTitle()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to claim project task for command, projectId={}", project.getId(), e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.claim.failed", e);
        }
    }

    /**
     * 在团队项目中放弃指定任务，清空当前指派人。
     */
    private static int executeTaskAbandonByProject(CommandSourceStack source, String projectId, String taskId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveTeamProjectForTaskCommand(source, player, projectId);
        if (project == null) {
            return COMMAND_FAILURE;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = loadProjectTasksForCommand(source, player, project);
            Task task = findTaskById(tasks, taskId);
            if (task == null || !task.belongsToProject(project.getId())) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }
            if (!canOperateProjectTask(source, player, project, task, Operation.ABANDON_TASK)) {
                return sendCommandFailure(source, "command.todolist.task.project.permission_denied");
            }
            task.setAssigneeUuid(null);
            task.setAssigneeName(null);
            saveProjectTasksForCommand(source, player, project, storage, tasks);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.abandon.success",
                    task.getTitle()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to abandon project task for command, projectId={}", project.getId(), e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.abandon.failed", e);
        }
    }

    /**
     * 在团队项目中将指定任务指派给目标成员。
     */
    private static int executeTaskAssignByProject(CommandSourceStack source, String projectId, String taskId, String memberToken) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveTeamProjectForTaskCommand(source, player, projectId);
        if (project == null) {
            return COMMAND_FAILURE;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = loadProjectTasksForCommand(source, player, project);
            Task task = findTaskById(tasks, taskId);
            if (task == null || !task.belongsToProject(project.getId())) {
                return sendCommandFailure(source, "command.todolist.task.done.not_found", taskId);
            }
            if (!canOperateProjectTask(source, player, project, task, Operation.ASSIGN_OTHERS)) {
                return sendCommandFailure(source, "command.todolist.task.project.permission_denied");
            }
            ResolvedMemberTarget target = resolveProjectAssignmentTarget(project, source.getServer(), memberToken);
            if (target == null) {
                return sendCommandFailure(source, "command.todolist.task.assign.invalid_target", memberToken);
            }
            task.setAssigneeUuid(target.uuid);
            task.setAssigneeName(target.displayName);
            saveProjectTasksForCommand(source, player, project, storage, tasks);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.assign.success",
                    task.getTitle(),
                    target.displayName
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to assign project task for command, projectId={}", project.getId(), e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.assign.failed", e);
        }
    }

    /**
     * 从当前玩家任务列表删除指定任务，并写回存储。
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
            List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), storage, playerUuid);
            Task task = findTaskById(tasks, taskId);
            if (task == null) {
                return sendCommandFailure(source, "command.todolist.task.remove.not_found", taskId);
            }

            tasks.remove(task);
            savePersonalTasksForCommand(source.getServer(), storage, playerUuid, tasks);
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
            return sendStorageAwareCommandFailure(source, "command.todolist.task.remove.failed", e);
        }
    }

    /**
     * 从指定项目中删除目标任务，并依据项目范围执行对应同步。
     */
    private static int executeTaskRemoveByProject(CommandSourceStack source, String projectId, String taskId) {
        if (ensureCommandPermission(source, CommandPermissionSemantic.EDIT) == COMMAND_FAILURE) {
            return COMMAND_FAILURE;
        }
        ServerPlayer player = getPlayerIfPresent(source);
        if (player == null) {
            return COMMAND_FAILURE;
        }
        Project project = resolveProjectForCommand(source, player, projectId, "command.todolist.task.project.not_found");
        if (project == null) {
            return COMMAND_FAILURE;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = loadProjectTasksForCommand(source, player, project);
            Task task = findTaskById(tasks, taskId);
            if (task == null || !task.belongsToProject(project.getId())) {
                return sendCommandFailure(source, "command.todolist.task.remove.not_found", taskId);
            }
            if (!canOperateProjectTask(source, player, project, task, Operation.DELETE_TASK)) {
                return sendCommandFailure(source, "command.todolist.task.project.permission_denied");
            }
            tasks.remove(task);
            saveProjectTasksForCommand(source, player, project, storage, tasks);
            return sendCommandSuccess(
                    source,
                    COMMAND_SUCCESS,
                    SIDE_EFFECT_PERSIST_DATA_AND_REFRESH_HUD,
                    "command.todolist.task.remove.success",
                    task.getTitle()
            );
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to remove project task for command, projectId={}", project.getId(), e);
            return sendStorageAwareCommandFailure(source, "command.todolist.task.remove.failed", e);
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
                List<Task> tasks = loadPersonalTasksForCommand(source.getServer(), storage, playerUuid);
                tasks.add(newTask);
                savePersonalTasksForCommand(source.getServer(), storage, playerUuid, tasks);
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
            return sendStorageAwareCommandFailure(source, "command.todolist.task.add.failed", e);
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
        boolean allowAllPlayersClaimComplete = project.isAllowAllPlayersClaimComplete();
        Context ctx = new Context(ViewScope.TEAM_ALL, false, false, false, false, false, projectMember, allowMemberCreate, allowAllPlayersClaimComplete);
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

    /**
     * 为任务触发器类型参数提供候选项。
     */
    private static CompletableFuture<Suggestions> suggestTaskTriggerTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(TASK_TRIGGER_TYPE_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestProjectScopes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(PROJECT_SCOPE_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestProjectListModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(PROJECT_LIST_MODE_SUGGESTIONS, builder);
    }

    private static CompletableFuture<Suggestions> suggestProjectMemberRoles(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(PROJECT_MEMBER_ROLE_SUGGESTIONS, builder);
    }

    /**
     * 为管理员命令的 commandAccessMode 参数提供自动补全。
     *
     * @param ctx 命令上下文
     * @param builder 补全构建器
     * @return 补全结果
     */
    private static CompletableFuture<Suggestions> suggestCommandAccessModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(COMMAND_ACCESS_MODE_SUGGESTIONS, builder);
    }

    /**
     * 为 H2 TCP 账号角色参数提供自动补全。
     *
     * @param ctx 命令上下文
     * @param builder 补全构建器
     * @return 补全结果
     */
    private static CompletableFuture<Suggestions> suggestH2AccountRoles(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestWords(H2_ACCOUNT_ROLE_SUGGESTIONS, builder);
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

    private static CompletableFuture<Suggestions> suggestAddableProjectMembers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return builder.buildFuture();
        }
        String projectId = StringArgumentType.getString(ctx, "projectId");
        Project project = TodoListCommon.getProjectManager().getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM || source.getServer() == null) {
            return builder.buildFuture();
        }
        for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
            if (online == null) {
                continue;
            }
            String onlineUuid = online.getStringUUID();
            if (onlineUuid.equals(project.getOwnerUuid()) || project.getMemberRole(onlineUuid) != null) {
                continue;
            }
            builder.suggest(online.getName().getString());
            builder.suggest(onlineUuid);
        }
        return builder.buildFuture();
    }

    /**
     * 为团队任务指派命令提供可选成员建议，包含成员 UUID 与缓存名称。
     */
    private static CompletableFuture<Suggestions> suggestAssignableProjectMembers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return builder.buildFuture();
        }
        String projectId = StringArgumentType.getString(ctx, "projectId");
        Project project = TodoListCommon.getProjectManager().getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return builder.buildFuture();
        }
        Set<String> suggestions = new LinkedHashSet<>();
        String ownerUuid = project.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            suggestions.add(ownerUuid);
            String ownerName = getProjectMemberDisplayName(project, ownerUuid);
            if (ownerName != null && !ownerName.isBlank()) {
                suggestions.add(ownerName);
            }
        }
        for (Map.Entry<String, Project.ProjectRole> entry : project.getMembers().entrySet()) {
            String memberUuid = entry.getKey();
            if (memberUuid == null || memberUuid.isBlank()) {
                continue;
            }
            suggestions.add(memberUuid);
            String memberName = getProjectMemberDisplayName(project, memberUuid);
            if (memberName != null && !memberName.isBlank()) {
                suggestions.add(memberName);
            }
        }
        for (String suggestion : suggestions) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRemovableProjectMemberIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestProjectMemberIds(ctx, builder, false);
    }

    private static CompletableFuture<Suggestions> suggestRoleEditableProjectMemberIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestProjectMemberIds(ctx, builder, true);
    }

    private static CompletableFuture<Suggestions> suggestProjectMemberIds(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder,
            boolean excludeProjectManager
    ) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = getPlayerOrNull(source);
        if (player == null) {
            return builder.buildFuture();
        }
        String projectId = StringArgumentType.getString(ctx, "projectId");
        Project project = TodoListCommon.getProjectManager().getProject(projectId);
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return builder.buildFuture();
        }
        for (Map.Entry<String, Project.ProjectRole> entry : project.getMembers().entrySet()) {
            String memberUuid = entry.getKey();
            if (memberUuid == null || memberUuid.isBlank()) {
                continue;
            }
            if (excludeProjectManager && memberUuid.equals(project.getOwnerUuid())) {
                continue;
            }
            builder.suggest(memberUuid);
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
        Project.ProjectRole memberRole = project.getMemberRole(playerUuid);
        if (memberRole == Project.ProjectRole.PROJECT_MANAGER) {
            return Role.PROJECT_MANAGER;
        }
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
        return project.getMemberRole(player.getStringUUID()) != null;
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
     * 根据存储异常类型发送命令失败反馈。
     */
    private static int sendStorageAwareCommandFailure(CommandSourceStack source, String fallbackTranslationKey, Throwable throwable) {
        return sendCommandFailure(source, StorageFailureNotifier.toCommandMessageKey(throwable, fallbackTranslationKey));
    }

    /**
     * 在命令修改内存状态前检查当前存储后端是否可写。
     */
    private static int ensureStorageAvailableForCommand(CommandSourceStack source) {
        if (ModConfig.getInstance().getStorageBackend() != ModConfig.StorageBackend.H2) {
            return COMMAND_SUCCESS;
        }
        try {
            H2StorageAvailability.ensureAvailable(new H2ConnectionProvider().getDatabaseBasePath());
            H2MaintenanceLock.ensureWritable();
            return COMMAND_SUCCESS;
        } catch (StorageUnavailableException exception) {
            return sendStorageAwareCommandFailure(source, "command.todolist.storage_unavailable", exception);
        } catch (Exception exception) {
            TodoConstants.LOGGER.warn("Failed to check H2 storage availability before command mutation", exception);
            return COMMAND_SUCCESS;
        }
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
