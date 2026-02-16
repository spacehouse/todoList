package com.todolist.network;

import com.todolist.TodoListMod;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Network packet handling for task synchronization
 *
 * Packets:
 * - SYNC_TASKS: Server -> Client (sync all tasks on login)
 * - ADD_TASK: Client -> Server (add new task)
 * - UPDATE_TASK: Client -> Server (update existing task)
 * - DELETE_TASK: Client -> Server (delete task)
 * - TOGGLE_TASK: Client -> Server (toggle completion)
 * - TASK_CONFIRMED: Server -> Client (confirm operation)
 */
public class TaskPackets {
    // Packet IDs
    public static final Identifier SYNC_TASKS_ID = new Identifier(TodoListMod.MOD_ID, "sync_tasks");
    public static final Identifier ADD_TASK_ID = new Identifier(TodoListMod.MOD_ID, "add_task");
    public static final Identifier UPDATE_TASK_ID = new Identifier(TodoListMod.MOD_ID, "update_task");
    public static final Identifier DELETE_TASK_ID = new Identifier(TodoListMod.MOD_ID, "delete_task");
    public static final Identifier TOGGLE_TASK_ID = new Identifier(TodoListMod.MOD_ID, "toggle_task");
    public static final Identifier TASK_CONFIRMED_ID = new Identifier(TodoListMod.MOD_ID, "task_confirmed");
    public static final Identifier REPLACE_TASKS_ID = new Identifier(TodoListMod.MOD_ID, "replace_tasks");
    public static final Identifier TEAM_SYNC_TASKS_ID = new Identifier(TodoListMod.MOD_ID, "team_sync_tasks");
    public static final Identifier TEAM_REPLACE_TASKS_ID = new Identifier(TodoListMod.MOD_ID, "team_replace_tasks");
    public static final Identifier TEAM_TOGGLE_TASK_ID = new Identifier(TodoListMod.MOD_ID, "team_toggle_task");

    public static void registerServerPackets() {
        ServerPlayNetworking.registerGlobalReceiver(ADD_TASK_ID, (server, player, handler, buf, responseSender) -> {
            Task task = readTask(buf);

            server.execute(() -> {
                UUID playerUuid = player.getUuid();
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    List<Task> tasks = storage.loadPlayerTasks(playerUuid);
                    tasks.add(task);
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoListMod.LOGGER.info("Player {} added task: {}", player.getName().getString(), task.getTitle());
                    sendConfirmation(player, "add", task.getId(), true);
                    sendSyncTasks(player, tasks);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to add task", e);
                    sendConfirmation(player, "add", task.getId(), false);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UPDATE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            Task updatedTask = readTask(buf);

            server.execute(() -> {
                UUID playerUuid = player.getUuid();
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    List<Task> tasks = storage.loadPlayerTasks(playerUuid);
                    boolean found = false;
                    for (Task task : tasks) {
                        if (task.getId().equals(updatedTask.getId())) {
                            task.setTitle(updatedTask.getTitle());
                            task.setDescription(updatedTask.getDescription());
                            task.setCompleted(updatedTask.isCompleted());
                            task.setPriority(updatedTask.getPriority());
                            task.setTags(updatedTask.getTags());
                            task.setDueDate(updatedTask.getDueDate());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        tasks.add(updatedTask);
                    }
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoListMod.LOGGER.info("Player {} updated task: {}", player.getName().getString(), updatedTask.getTitle());
                    sendConfirmation(player, "update", updatedTask.getId(), true);
                    sendSyncTasks(player, tasks);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to update task", e);
                    sendConfirmation(player, "update", updatedTask.getId(), false);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DELETE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            String taskId = buf.readString();

            server.execute(() -> {
                UUID playerUuid = player.getUuid();
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    List<Task> tasks = storage.loadPlayerTasks(playerUuid);
                    tasks.removeIf(t -> t.getId().equals(taskId));
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoListMod.LOGGER.info("Player {} deleted task: {}", player.getName().getString(), taskId);
                    sendConfirmation(player, "delete", taskId, true);
                    sendSyncTasks(player, tasks);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to delete task", e);
                    sendConfirmation(player, "delete", taskId, false);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            String taskId = buf.readString();

            server.execute(() -> {
                UUID playerUuid = player.getUuid();
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    List<Task> tasks = storage.loadPlayerTasks(playerUuid);
                    for (Task task : tasks) {
                        if (task.getId().equals(taskId)) {
                            task.setCompleted(!task.isCompleted());
                            break;
                        }
                    }
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoListMod.LOGGER.info("Player {} toggled task: {}", player.getName().getString(), taskId);
                    sendConfirmation(player, "toggle", taskId, true);
                    sendSyncTasks(player, tasks);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to toggle task", e);
                    sendConfirmation(player, "toggle", taskId, false);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) -> {
            List<Task> tasks = readTaskList(buf);

            server.execute(() -> {
                UUID playerUuid = player.getUuid();
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    storage.savePlayerTasks(playerUuid, tasks);
                    TodoListMod.LOGGER.info("Player {} replaced all tasks, count={}", player.getName().getString(), tasks.size());
                    sendSyncTasks(player, tasks);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to replace tasks", e);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TEAM_REPLACE_TASKS_ID, (server, player, handler, buf, responseSender) -> {
            List<Task> tasks = readTaskList(buf);

            server.execute(() -> {
                if (!isAdmin(player)) {
                    TodoListMod.LOGGER.warn("Player {} attempted to replace team tasks without permission", player.getName().getString());
                    return;
                }
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    storage.saveTeamTasks(tasks);
                    TodoListMod.LOGGER.info("Player {} replaced team tasks, count={}", player.getName().getString(), tasks.size());
                    broadcastTeamTasks(server, tasks);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to replace team tasks", e);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TEAM_TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            String taskId = buf.readString();

            server.execute(() -> {
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    List<Task> tasks = storage.loadTeamTasks();
                    boolean changed = false;
                    UUID playerUuid = player.getUuid();
                    for (Task task : tasks) {
                        if (task.getId().equals(taskId)) {
                            boolean canToggle = isAdmin(player);
                            if (!canToggle) {
                                String assignee = task.getAssigneeUuid();
                                if (assignee != null && assignee.equals(playerUuid.toString())) {
                                    canToggle = true;
                                }
                            }
                            if (!canToggle) {
                                TodoListMod.LOGGER.warn("Player {} attempted to toggle team task {} without permission", player.getName().getString(), taskId);
                                break;
                            }
                            task.setCompleted(!task.isCompleted());
                            changed = true;
                            break;
                        }
                    }
                    if (changed) {
                        storage.saveTeamTasks(tasks);
                        TodoListMod.LOGGER.info("Player {} toggled team task {}", player.getName().getString(), taskId);
                        broadcastTeamTasks(server, tasks);
                    }
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to toggle team task", e);
                }
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerUuid = player.getUuid();
            server.execute(() -> {
                try {
                    TaskStorage storage = TodoListMod.getTaskStorage();
                    List<Task> tasks = storage.loadPlayerTasks(playerUuid);
                    sendSyncTasks(player, tasks);
                    TodoListMod.LOGGER.info("Synced {} tasks to player {}", tasks.size(), player.getName().getString());
                    List<Task> teamTasks = storage.loadTeamTasks();
                    sendTeamSyncTasks(player, teamTasks);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to sync tasks to player on join", e);
                }
            });
        });
    }

    // Packet writing methods

    /**
     * Create packet to sync tasks to client
     */
    public static Packet<ClientPlayPacketListener> createSyncTasksPacket(List<Task> tasks) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        writeTaskList(buf, tasks);
        return new CustomPayloadS2CPacket(SYNC_TASKS_ID, buf);
    }

    private static void sendConfirmation(ServerPlayerEntity player, String action, String taskId, boolean success) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(action);
        buf.writeString(taskId);
        buf.writeBoolean(success);
        ServerPlayNetworking.send(player, TASK_CONFIRMED_ID, buf);
    }

    private static void sendSyncTasks(ServerPlayerEntity player, List<Task> tasks) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        writeTaskList(buf, tasks);
        ServerPlayNetworking.send(player, SYNC_TASKS_ID, buf);
    }

    private static void sendTeamSyncTasks(ServerPlayerEntity player, List<Task> tasks) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        writeTaskList(buf, tasks);
        ServerPlayNetworking.send(player, TEAM_SYNC_TASKS_ID, buf);
    }

    private static void broadcastTeamTasks(net.minecraft.server.MinecraftServer server, List<Task> tasks) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendTeamSyncTasks(player, tasks);
        }
    }

    private static boolean isAdmin(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2);
    }

    // Serialization helpers

    /**
     * Write task list to buffer
     */
    public static void writeTaskList(PacketByteBuf buf, List<Task> tasks) {
        buf.writeInt(tasks.size());
        for (Task task : tasks) {
            writeTask(buf, task);
        }
    }

    /**
     * Read task list from buffer
     */
    public static List<Task> readTaskList(PacketByteBuf buf) {
        int count = buf.readInt();
        List<Task> tasks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tasks.add(readTask(buf));
        }
        return tasks;
    }

    public static void writeTask(PacketByteBuf buf, Task task) {
        buf.writeString(task.getId());
        buf.writeString(task.getTitle());
        buf.writeString(task.getDescription());
        buf.writeBoolean(task.isCompleted());
        buf.writeEnumConstant(task.getPriority());
        buf.writeLong(task.getCreatedAt());
        buf.writeEnumConstant(task.getScope());
        boolean hasCreator = task.getCreatorUuid() != null;
        boolean hasAssignee = task.getAssigneeUuid() != null;
        buf.writeBoolean(hasCreator);
        if (hasCreator) {
            buf.writeString(task.getCreatorUuid());
        }
        buf.writeBoolean(hasAssignee);
        if (hasAssignee) {
            buf.writeString(task.getAssigneeUuid());
        }

        buf.writeCollection(task.getTags(), (taskBuf, tag) -> taskBuf.writeString(tag));

        buf.writeBoolean(task.getDueDate() != null);
        if (task.getDueDate() != null) {
            buf.writeLong(task.getDueDate());
        }

        buf.writeCollection(task.getSubtasks(), (taskBuf, subtask) -> writeTask(taskBuf, subtask));
    }

    public static Task readTask(PacketByteBuf buf) {
        String id = buf.readString();
        String title = buf.readString();
        String description = buf.readString();
        boolean completed = buf.readBoolean();
        Task.Priority priority = buf.readEnumConstant(Task.Priority.class);
        long createdAt = buf.readLong();
        Task.Scope scope = buf.readEnumConstant(Task.Scope.class);
        boolean hasCreator = buf.readBoolean();
        String creatorUuid = hasCreator ? buf.readString() : null;
        boolean hasAssignee = buf.readBoolean();
        String assigneeUuid = hasAssignee ? buf.readString() : null;

        Task task = new Task(title, description);
        task.setId(id);
        task.setCompleted(completed);
        task.setPriority(priority);
        task.setScope(scope);
        task.setCreatorUuid(creatorUuid);
        task.setAssigneeUuid(assigneeUuid);

        List<String> tags = buf.readList(taskBuf -> taskBuf.readString());
        for (String tag : tags) {
            task.addTag(tag);
        }

        boolean hasDueDate = buf.readBoolean();
        if (hasDueDate) {
            task.setDueDate(buf.readLong());
        }

        List<Task> subtasks = buf.readList(taskBuf -> readTask(taskBuf));
        for (Task subtask : subtasks) {
            task.addSubtask(subtask);
        }

        return task;
    }

    // Client-side send methods moved to com.todolist.client.ClientTaskPackets
}
