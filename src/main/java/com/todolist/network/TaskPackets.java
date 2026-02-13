package com.todolist.network;

import com.todolist.TodoListMod;
import com.todolist.task.Task;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

    /**
     * Register all network packets
     */
    public static void register() {
        TodoListMod.LOGGER.info("Registering network packets...");

        // Client-side packet receivers
        registerClientPackets();

        // Server-side packet receivers
        registerServerPackets();

        TodoListMod.LOGGER.info("Network packets registered successfully");
    }

    private static void registerClientPackets() {
        // Sync tasks packet
        ClientPlayNetworking.registerGlobalReceiver(SYNC_TASKS_ID, (client, handler, buf, responseSender) -> {
            List<Task> tasks = readTaskList(buf);
            client.execute(() -> {
                // Update client-side task manager
                // This will be implemented when we create client-side code
                TodoListMod.LOGGER.info("Received {} tasks from server", tasks.size());
            });
        });

        // Task confirmed packet
        ClientPlayNetworking.registerGlobalReceiver(TASK_CONFIRMED_ID, (client, handler, buf, responseSender) -> {
            String action = buf.readString();
            String taskId = buf.readString();
            boolean success = buf.readBoolean();

            client.execute(() -> {
                TodoListMod.LOGGER.info("Task {} {}", action, success ? "succeeded" : "failed");
            });
        });
    }

    private static void registerServerPackets() {
        // Add task packet
        ServerPlayNetworking.registerGlobalReceiver(ADD_TASK_ID, (server, player, handler, buf, responseSender) -> {
            Task task = readTask(buf);

            server.execute(() -> {
                try {
                    // Add task to server-side storage
                    // This will be implemented when we create server-side handlers
                    TodoListMod.LOGGER.info("Player {} added task: {}", player.getName().getString(), task.getTitle());

                    // Send confirmation
                    sendConfirmation(player, "add", task.getId(), true);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to add task", e);
                    sendConfirmation(player, "add", task.getId(), false);
                }
            });
        });

        // Update task packet
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            Task task = readTask(buf);

            server.execute(() -> {
                try {
                    // Update task in server-side storage
                    TodoListMod.LOGGER.info("Player {} updated task: {}", player.getName().getString(), task.getTitle());
                    sendConfirmation(player, "update", task.getId(), true);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to update task", e);
                    sendConfirmation(player, "update", task.getId(), false);
                }
            });
        });

        // Delete task packet
        ServerPlayNetworking.registerGlobalReceiver(DELETE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            String taskId = buf.readString();

            server.execute(() -> {
                try {
                    // Delete task from server-side storage
                    TodoListMod.LOGGER.info("Player {} deleted task: {}", player.getName().getString(), taskId);
                    sendConfirmation(player, "delete", taskId, true);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to delete task", e);
                    sendConfirmation(player, "delete", taskId, false);
                }
            });
        });

        // Toggle task packet
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_TASK_ID, (server, player, handler, buf, responseSender) -> {
            String taskId = buf.readString();

            server.execute(() -> {
                try {
                    // Toggle task in server-side storage
                    TodoListMod.LOGGER.info("Player {} toggled task: {}", player.getName().getString(), taskId);
                    sendConfirmation(player, "toggle", taskId, true);
                } catch (Exception e) {
                    TodoListMod.LOGGER.error("Failed to toggle task", e);
                    sendConfirmation(player, "toggle", taskId, false);
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

    /**
     * Send confirmation to client
     */
    private static void sendConfirmation(ServerPlayerEntity player, String action, String taskId, boolean success) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString(action);
        buf.writeString(taskId);
        buf.writeBoolean(success);
        ServerPlayNetworking.send(player, TASK_CONFIRMED_ID, buf);
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

    /**
     * Write task to buffer
     */
    public static void writeTask(PacketByteBuf buf, Task task) {
        buf.writeString(task.getId());
        buf.writeString(task.getTitle());
        buf.writeString(task.getDescription());
        buf.writeBoolean(task.isCompleted());
        buf.writeEnumConstant(task.getPriority());
        buf.writeLong(task.getCreatedAt());

        // Write tags
        buf.writeCollection(task.getTags(), (taskBuf, tag) -> taskBuf.writeString(tag));

        // Write due date (optional)
        buf.writeBoolean(task.getDueDate() != null);
        if (task.getDueDate() != null) {
            buf.writeLong(task.getDueDate());
        }

        // Write subtasks
        buf.writeCollection(task.getSubtasks(), (taskBuf, subtask) -> writeTask(taskBuf, subtask));
    }

    /**
     * Read task from buffer
     */
    public static Task readTask(PacketByteBuf buf) {
        Task task = new Task("", "");
        // Note: We can't set the ID directly as it's final
        // In production, we'd need a different approach or make id non-final

        task.setTitle(buf.readString());
        task.setDescription(buf.readString());
        task.setCompleted(buf.readBoolean());
        task.setPriority(buf.readEnumConstant(Task.Priority.class));
        // Skip createdAt for now as we can't set it

        // Read tags
        List<String> tags = buf.readList(taskBuf -> taskBuf.readString());
        for (String tag : tags) {
            task.addTag(tag);
        }

        // Read due date
        boolean hasDueDate = buf.readBoolean();
        if (hasDueDate) {
            task.setDueDate(buf.readLong());
        }

        // Read subtasks
        List<Task> subtasks = buf.readList(taskBuf -> readTask(taskBuf));
        for (Task subtask : subtasks) {
            task.addSubtask(subtask);
        }

        return task;
    }
}
