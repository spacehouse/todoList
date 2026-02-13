package com.todolist;

import com.todolist.config.ModConfig;
import com.todolist.network.TaskPackets;
import com.todolist.task.TaskStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Todo List Mod - Main Entry Point
 *
 * Main features:
 * - Task management with GUI
 * - Client-side and server-side storage
 * - Multiplayer synchronization
 * - HUD display (planned)
 *
 * @author TodoList Team
 * @version 1.0.0
 */
public class TodoListMod implements ModInitializer {
    public static final String MOD_ID = "todolist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TaskStorage taskStorage;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Todo List Mod...");

        // Initialize configuration
        ModConfig.load();

        // Initialize task storage
        taskStorage = new TaskStorage();

        // Register network packets
        TaskPackets.register();

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(this::onServerStopped);

        LOGGER.info("Todo List Mod loaded successfully!");
    }

    private void onServerStarting(MinecraftServer server) {
        LOGGER.info("Todo List Mod: Server starting, initializing storage...");
        // Server-specific initialization
    }

    private void onServerStopped(MinecraftServer server) {
        LOGGER.info("Todo List Mod: Server stopped, saving data...");
        // Cleanup and save data
    }

    public static TaskStorage getTaskStorage() {
        return taskStorage;
    }
}
