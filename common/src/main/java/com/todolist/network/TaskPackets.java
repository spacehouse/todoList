package com.todolist.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 任务同步相关的网络包定义与服务端处理逻辑。
 */
public class TaskPackets {
    public static final ResourceLocation SYNC_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "sync_tasks");
    public static final ResourceLocation TEAM_SYNC_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_sync_tasks");
    public static final ResourceLocation TASK_CONFIRMED_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "task_confirmed");
    public static final ResourceLocation REPLACE_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "replace_tasks");
    public static final ResourceLocation TEAM_REPLACE_TASKS_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_replace_tasks");
    public static final ResourceLocation TEAM_REQUEST_SYNC_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_request_sync");
    public static final ResourceLocation ADD_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "add_task");
    public static final ResourceLocation UPDATE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "update_task");
    public static final ResourceLocation DELETE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "delete_task");
    public static final ResourceLocation TOGGLE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "toggle_task");
    public static final ResourceLocation TEAM_TOGGLE_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_toggle_task");
    public static final ResourceLocation TEAM_ASSIGN_TASK_ID = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "team_assign_task");
    private static volatile ServerPacketSender serverPacketSender = (player, channelId, buf) -> { };

    public static void setServerPacketSender(ServerPacketSender sender) {
        serverPacketSender = sender == null ? (player, channelId, buf) -> { } : sender;
    }

    public static void onReplaceTasksPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        List<Task> tasks = readTaskList(buf);
        server.execute(() -> handleReplaceTasks(player, tasks));
    }

    public static void onTeamReplaceTasksPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        List<Task> tasks = readTaskList(buf);
        server.execute(() -> handleTeamReplaceTasks(server, player, tasks));
    }

    public static void onTeamRequestSyncPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        server.execute(() -> broadcastTeamTasks(server));
    }

    public static void onAddTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        Task task = readTask(buf);
        server.execute(() -> handleAddTask(player, task));
    }

    public static void onUpdateTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        Task task = readTask(buf);
        server.execute(() -> handleUpdateTask(player, task));
    }

    public static void onDeleteTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        UUID taskId = buf.readUUID();
        server.execute(() -> handleDeleteTask(player, taskId));
    }

    public static void onToggleTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        UUID taskId = buf.readUUID();
        server.execute(() -> handleToggleTask(player, taskId));
    }

    public static void onTeamToggleTaskPacket(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf) {
        UUID taskId = buf.readUUID();
        server.execute(() -> handleTeamToggleTask(server, taskId));
    }

    public static void onPlayerJoin(MinecraftServer server, ServerPlayer player) {
        server.execute(() -> {
            syncTasksToPlayer(player);
            syncTeamTasksToPlayer(player);
        });
    }

    /**
     * 将服务端当前玩家个人任务列表同步到该玩家客户端。
     */
    public static void syncTasksToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            java.util.UUID playerUuid = player.getUUID();
            List<Task> tasks = storage.loadPlayerTasks(playerUuid);
            boolean playerFileExists = storage.hasPlayerTasks(playerUuid);
            List<Task> fallback = storage.loadTasks();
            if (!playerFileExists) {
                if (!fallback.isEmpty()) {
                    tasks = fallback;
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoConstants.LOGGER.info("Migrated {} tasks from local storage to player file {}", tasks.size(), playerUuid);
                }
            } else if (!fallback.isEmpty()) {
                long playerLastSaved = storage.getPlayerTasksLastSaved(playerUuid);
                long localLastSaved = storage.getLocalTasksLastSaved();
                if (localLastSaved > playerLastSaved) {
                    tasks = fallback;
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoConstants.LOGGER.info("Recovered newer local tasks for player file {}, localLastSaved={}, playerLastSaved={}, taskCount={}",
                            playerUuid, localLastSaved, playerLastSaved, tasks.size());
                }
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            writeTaskList(buf, tasks);
            serverPacketSender.send(player, SYNC_TASKS_ID, buf);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load player tasks for sync", e);
        }
    }

    private static void syncTeamTasksToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadTeamTasks();
            FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            writeTaskList(buf, tasks);
            serverPacketSender.send(player, TEAM_SYNC_TASKS_ID, buf);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for sync", e);
        }
    }

    private static void handleReplaceTasks(ServerPlayer player, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            storage.savePlayerTasks(player.getUUID(), tasks);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save player tasks", e);
        }
    }

    /**
     * 澶勭悊鍥㈤槦浠诲姟鏁撮噺鏇挎崲锛屼粎鍏佽鐜╁淇敼鑷繁鏈夋潈闄愮殑椤圭洰浠诲姟銆?
     */
    private static void handleTeamReplaceTasks(MinecraftServer server, ServerPlayer player, List<Task> tasks) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> currentTasks = storage.loadTeamTasks();
            List<Task> mergedTasks = mergeTeamTasksWithPermission(player, currentTasks, tasks);
            storage.saveTeamTasks(mergedTasks);
            broadcastTeamTasks(server);
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to save team tasks", e);
        }
    }

    /**
     * 鎸夋潈闄愬皢瀹㈡埛绔笂浼犱换鍔″垪琛ㄤ笌鏈嶅姟绔幇鐘舵枟鍚堬紝闃叉瓒婃潈淇敼銆?
     */
    private static List<Task> mergeTeamTasksWithPermission(ServerPlayer player, List<Task> currentTasks, List<Task> incomingTasks) {
        Map<String, Task> incomingById = new LinkedHashMap<>();
        for (Task incoming : incomingTasks) {
            if (incoming == null) {
                continue;
            }
            String id = incoming.getId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            incomingById.put(id, incoming);
        }

        List<Task> merged = new ArrayList<>();
        for (Task current : currentTasks) {
            if (current == null) {
                continue;
            }
            String id = current.getId();
            if (id == null || id.isEmpty()) {
                merged.add(current);
                continue;
            }

            Task incoming = incomingById.remove(id);
            if (incoming == null) {
                if (!canDeleteTaskInProject(player, current)) {
                    merged.add(current);
                }
                continue;
            }

            boolean sameProject = isSameProjectBinding(current, incoming);
            if (sameProject) {
                if (canApplyTeamTaskUpdate(player, current, incoming)) {
                    merged.add(incoming);
                } else {
                    merged.add(current);
                }
                continue;
            }

            boolean canDeleteCurrent = canDeleteTaskInProject(player, current);
            boolean canAddIncoming = canAddTaskInProject(player, incoming);
            if (canDeleteCurrent && canAddIncoming) {
                merged.add(incoming);
            } else {
                merged.add(current);
            }
        }

        for (Task incoming : incomingById.values()) {
            if (incoming == null) {
                continue;
            }
            if (canAddTaskInProject(player, incoming)) {
                merged.add(incoming);
            }
        }
        return merged;
    }

    /**
     * 鍒ゆ柇鐜╁鏄惁鍏佽缂栬緫鎸囧畾鍥㈤槦浠诲姟銆?
     */
    /**
     * 判定同一团队项目下的任务更新是否可应用，按“字段变化 -> 操作类型”逐项校验权限。
     */
    private static boolean canApplyTeamTaskUpdate(ServerPlayer player, Task current, Task incoming) {
        if (current == null || incoming == null || !isSameProjectBinding(current, incoming)) {
            return false;
        }
        Project project = findTaskProject(current);
        if (project == null) {
            return false;
        }

        if (hasEditableFieldChanges(current, incoming)
                && !canOperateInProject(player, current, project, Operation.EDIT_TASK, ViewScope.TEAM_ALL)) {
            return false;
        }

        if (current.isCompleted() != incoming.isCompleted()
                && !canOperateInProject(player, current, project, Operation.TOGGLE_COMPLETE, ViewScope.TEAM_ASSIGNED)) {
            return false;
        }

        Operation assigneeOp = resolveAssigneeOperation(player, current, incoming);
        if (assigneeOp == null) {
            return true;
        }
        return canOperateInProject(player, current, project, assigneeOp, scopeForOperation(assigneeOp));
    }

    /**
     * 判断本次更新是否涉及需要 EDIT_TASK 权限的可编辑字段。
     */
    private static boolean hasEditableFieldChanges(Task current, Task incoming) {
        if (!Objects.equals(current.getTitle(), incoming.getTitle())) {
            return true;
        }
        if (!Objects.equals(current.getDescription(), incoming.getDescription())) {
            return true;
        }
        if (current.getPriority() != incoming.getPriority()) {
            return true;
        }
        if (!Objects.equals(current.getTags(), incoming.getTags())) {
            return true;
        }
        if (!Objects.equals(current.getDueDate(), incoming.getDueDate())) {
            return true;
        }
        if (current.getCreatedAt() != incoming.getCreatedAt()) {
            return true;
        }
        if (current.getScope() != incoming.getScope()) {
            return true;
        }
        return !Objects.equals(current.getCreatorUuid(), incoming.getCreatorUuid());
    }

    /**
     * 根据负责人字段变化推导需要的权限操作。
     */
    private static Operation resolveAssigneeOperation(ServerPlayer player, Task current, Task incoming) {
        String currentAssignee = normalizeText(current.getAssigneeUuid());
        String incomingAssignee = normalizeText(incoming.getAssigneeUuid());
        if (Objects.equals(currentAssignee, incomingAssignee)) {
            return null;
        }

        String playerUuid = player == null ? null : normalizeText(player.getStringUUID());
        if (isBlank(currentAssignee)) {
            if (playerUuid != null && playerUuid.equals(incomingAssignee)) {
                return Operation.CLAIM_TASK;
            }
            return Operation.ASSIGN_OTHERS;
        }
        if (isBlank(incomingAssignee)) {
            return Operation.ABANDON_TASK;
        }
        return Operation.ASSIGN_OTHERS;
    }

    /**
     * 为指定操作选择服务端权限判定使用的视图作用域。
     */
    private static ViewScope scopeForOperation(Operation operation) {
        if (operation == Operation.CLAIM_TASK) {
            return ViewScope.TEAM_UNASSIGNED;
        }
        if (operation == Operation.ABANDON_TASK || operation == Operation.TOGGLE_COMPLETE) {
            return ViewScope.TEAM_ASSIGNED;
        }
        return ViewScope.TEAM_ALL;
    }

    /**
     * 在给定团队项目上下文中执行权限判定。
     */
    private static boolean canOperateInProject(ServerPlayer player, Task task, Project project, Operation operation, ViewScope scope) {
        if (task == null || project == null) {
            return false;
        }
        Role role = getRole(player, project);
        Context context = buildTaskContext(player, task, project, scope);
        return PermissionCenter.canPerform(operation, role, context);
    }

    /**
     * 统一处理字符串标准化：trim 后空串转 null。
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判断文本是否为空（null 或空串）。
     */
    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private static boolean canEditTaskInProject(ServerPlayer player, Task task) {
        Project project = findTaskProject(task);
        if (project == null) {
            return false;
        }
        return canOperateInProject(player, task, project, Operation.EDIT_TASK, ViewScope.TEAM_ALL);
    }

    /**
     * 鍒ゆ柇鐜╁鏄惁鍏佽鍒犻櫎鎸囧畾鍥㈤槦浠诲姟銆?
     */
    private static boolean canDeleteTaskInProject(ServerPlayer player, Task task) {
        Project project = findTaskProject(task);
        if (project == null) {
            return false;
        }
        return canOperateInProject(player, task, project, Operation.DELETE_TASK, ViewScope.TEAM_ALL);
    }

    /**
     * 鍒ゆ柇鐜╁鏄惁鍏佽鍦ㄦ寚瀹氬洟闃熼」鐩笅鏂板浠诲姟銆?
     */
    private static boolean canAddTaskInProject(ServerPlayer player, Task task) {
        Project project = findTaskProject(task);
        if (project == null) {
            return false;
        }
        return canOperateInProject(player, task, project, Operation.ADD_TASK, ViewScope.TEAM_ALL);
    }

    /**
     * 瑙ｆ瀽浠诲姟瀵瑰簲鐨勫洟闃熼」鐩€?
     */
    private static Project findTaskProject(Task task) {
        if (task == null || task.getProjectId() == null || task.getProjectId().isBlank()) {
            return null;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        Project project = manager.getProject(task.getProjectId().trim());
        if (project == null || project.getScope() != Project.Scope.TEAM) {
            return null;
        }
        return project;
    }

    /**
     * 鏋勫缓浠诲姟鏉冮檺鍒ゆ柇涓婁笅鏂囥€?
     */
    private static Context buildTaskContext(ServerPlayer player, Task task, Project project) {
        return buildTaskContext(player, task, project, ViewScope.TEAM_ALL);
    }

    /**
     * 构建指定视图作用域下的任务权限上下文。
     */
    private static Context buildTaskContext(ServerPlayer player, Task task, Project project, ViewScope viewScope) {
        boolean assigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        boolean assigneeSelf = assigned
                && player != null
                && player.getStringUUID().equals(task.getAssigneeUuid());
        boolean projectMember = isProjectMember(player, project);
        boolean allowMemberCreate = project != null && project.isAllowMemberCreate();
        return new Context(viewScope, task.isCompleted(), assigned, assigneeSelf, false, false, projectMember, allowMemberCreate);
    }

    /**
     * 鍒ゆ柇鐜╁鏄惁涓洪」鐩垚鍛橈紙鍚礋璐ｄ汉锛夈€?
     */
    private static boolean isProjectMember(ServerPlayer player, Project project) {
        if (player == null || project == null) {
            return false;
        }
        String playerUuid = player.getStringUUID();
        if (playerUuid.equals(project.getOwnerUuid())) {
            return true;
        }
        return project.getMemberRole(playerUuid) != null;
    }

    /**
     * 瑙ｆ瀽鐜╁鍦ㄩ」鐩腑鐨勮鑹诧紝鐢ㄤ簬鏉冮檺鍒ゆ柇銆?
     */
    private static Role getRole(ServerPlayer player, Project project) {
        if (player == null) {
            return Role.MEMBER;
        }
        if (player.hasPermissions(2)) {
            return Role.OP;
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
     * 鍒ゆ柇涓や釜浠诲姟鐨勯」鐩粦瀹氭槸鍚︾浉鍚屻€?
     */
    private static boolean isSameProjectBinding(Task current, Task incoming) {
        String currentProjectId = current.getProjectId();
        String incomingProjectId = incoming.getProjectId();
        if (currentProjectId == null || currentProjectId.isBlank()) {
            return incomingProjectId == null || incomingProjectId.isBlank();
        }
        return currentProjectId.equals(incomingProjectId);
    }

    private static void handleAddTask(ServerPlayer player, Task task) {
        // Implementation for adding a task
    }

    private static void handleUpdateTask(ServerPlayer player, Task task) {
        // Implementation for updating a task
    }

    private static void handleDeleteTask(ServerPlayer player, UUID taskId) {
        // Implementation for deleting a task
    }

    private static void handleToggleTask(ServerPlayer player, UUID taskId) {
        // Implementation for toggling a task
    }

    private static void handleTeamToggleTask(MinecraftServer server, UUID taskId) {
        // Implementation for toggling a team task
    }

    /**
     * 将服务端当前团队任务列表广播给所有在线玩家。
     */
    public static void broadcastTeamTasks(MinecraftServer server) {
        TaskStorage storage = TodoListCommon.getTaskStorage();
        try {
            List<Task> tasks = storage.loadTeamTasks();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                writeTaskList(buf, tasks);
                serverPacketSender.send(player, TEAM_SYNC_TASKS_ID, buf);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load team tasks for broadcast", e);
        }
    }

    /**
     * 将单个任务写入网络缓冲区。
     */
    public static void writeTask(FriendlyByteBuf buf, Task task) {
        buf.writeNbt(task.toNbt());
    }

    /**
     * 从网络缓冲区读取单个任务。
     */
    public static Task readTask(FriendlyByteBuf buf) {
        CompoundTag nbt = buf.readNbt();
        return Task.fromNbt(nbt);
    }

    /**
     * 将任务列表写入网络缓冲区。
     */
    public static void writeTaskList(FriendlyByteBuf buf, List<Task> tasks) {
        buf.writeInt(tasks.size());
        for (Task task : tasks) {
            writeTask(buf, task);
        }
    }

    /**
     * 从网络缓冲区读取任务列表。
     */
    public static List<Task> readTaskList(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Task> tasks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tasks.add(readTask(buf));
        }
        return tasks;
    }

    @FunctionalInterface
    public interface ServerPacketSender {
        void send(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf);
    }
}
