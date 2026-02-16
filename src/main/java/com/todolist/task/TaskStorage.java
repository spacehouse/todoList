package com.todolist.task;

import com.todolist.TodoListMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles task data persistence
 *
 * Storage structure:
 * - Single player: saves/worldname/todo/moddata.dat
 * - Multiplayer: world/todo/players/{uuid}.dat
 */
public class TaskStorage {
    private static final String DATA_FOLDER = "todo";
    private static final String DATA_FILE = "moddata.dat";
    private static final String PLAYERS_FOLDER = "players";
    private static final String TEAM_FILE = "team_tasks.dat";

    private final Path dataDir;

    public TaskStorage() {
        this.dataDir = getDataDirectory();
        ensureDirectoryExists();
    }

    /**
     * Get the data directory path
     */
    private Path getDataDirectory() {
        // For development, use game directory
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath();
        TodoListMod.LOGGER.info("Game directory: {}", gameDir);

        // Try to find saves directory (single player)
        Path savesDir = gameDir.resolve("saves");
        if (Files.exists(savesDir)) {
            // In a real scenario, we'd get the current world name
            // For now, use a default location
            return gameDir.resolve(DATA_FOLDER);
        }

        // Default to game directory
        return gameDir.resolve(DATA_FOLDER);
    }

    /**
     * Ensure data directory exists
     */
    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                TodoListMod.LOGGER.info("Created data directory: {}", dataDir);
            }

            // Create players folder for multiplayer
            Path playersDir = dataDir.resolve(PLAYERS_FOLDER);
            if (!Files.exists(playersDir)) {
                Files.createDirectories(playersDir);
                TodoListMod.LOGGER.info("Created players directory: {}", playersDir);
            }
        } catch (IOException e) {
            TodoListMod.LOGGER.error("Failed to create data directory", e);
        }
    }

    /**
     * Save tasks to local storage (single player)
     */
    public void saveTasks(List<Task> tasks) throws IOException {
        Path dataFile = dataDir.resolve(DATA_FILE);
        saveTasksToFile(tasks, dataFile);
        TodoListMod.LOGGER.info("Saved {} tasks to {}", tasks.size(), dataFile);
    }

    /**
     * Save tasks for a specific player (multiplayer)
     */
    public void savePlayerTasks(UUID playerUuid, List<Task> tasks) throws IOException {
        Path playersDir = dataDir.resolve(PLAYERS_FOLDER);
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        saveTasksToFile(tasks, playerFile);
        TodoListMod.LOGGER.info("Saved {} tasks for player {}", tasks.size(), playerUuid);
    }

    public void saveTeamTasks(List<Task> tasks) throws IOException {
        Path teamFile = dataDir.resolve(TEAM_FILE);
        saveTasksToFile(tasks, teamFile);
        TodoListMod.LOGGER.info("Saved {} team tasks to {}", tasks.size(), teamFile);
    }

    /**
     * Save tasks to a specific file
     */
    private void saveTasksToFile(List<Task> tasks, Path file) throws IOException {
        NbtCompound root = new NbtCompound();
        root.putLong("lastSaved", System.currentTimeMillis());
        root.putInt("version", 1);

        NbtList taskList = new NbtList();
        for (Task task : tasks) {
            taskList.add(task.toNbt());
        }
        root.put("tasks", taskList);

        // Write to file
        NbtIo.write(root, file.toFile());
    }

    /**
     * Load tasks from local storage (single player)
     */
    public List<Task> loadTasks() throws IOException {
        Path dataFile = dataDir.resolve(DATA_FILE);
        if (!Files.exists(dataFile)) {
            TodoListMod.LOGGER.info("No existing task data found, starting fresh");
            return new ArrayList<>();
        }
        return loadTasksFromFile(dataFile);
    }

    /**
     * Load tasks for a specific player (multiplayer)
     */
    public List<Task> loadPlayerTasks(UUID playerUuid) throws IOException {
        Path playersDir = dataDir.resolve(PLAYERS_FOLDER);
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        if (!Files.exists(playerFile)) {
            TodoListMod.LOGGER.info("No existing task data for player {}", playerUuid);
            return new ArrayList<>();
        }
        return loadTasksFromFile(playerFile);
    }

    public List<Task> loadTeamTasks() throws IOException {
        Path teamFile = dataDir.resolve(TEAM_FILE);
        if (!Files.exists(teamFile)) {
            TodoListMod.LOGGER.info("No existing team task data");
            return new ArrayList<>();
        }
        return loadTasksFromFile(teamFile);
    }

    /**
     * Load tasks from a specific file
     */
    private List<Task> loadTasksFromFile(Path file) throws IOException {
        NbtCompound root = NbtIo.read(file.toFile());
        if (root == null) {
            TodoListMod.LOGGER.warn("Failed to read task data from {}", file);
            return new ArrayList<>();
        }

        long lastSaved = root.getLong("lastSaved");
        int version = root.getInt("version");
        TodoListMod.LOGGER.info("Loading task data, version {}, last saved: {}", version, lastSaved);

        NbtList taskList = root.getList("tasks", NbtElement.COMPOUND_TYPE);
        List<Task> tasks = new ArrayList<>();

        for (int i = 0; i < taskList.size(); i++) {
            NbtCompound taskNbt = taskList.getCompound(i);
            try {
                Task task = Task.fromNbt(taskNbt);
                tasks.add(task);
            } catch (Exception e) {
                TodoListMod.LOGGER.error("Failed to load task at index {}", i, e);
            }
        }

        TodoListMod.LOGGER.info("Successfully loaded {} tasks", tasks.size());
        return tasks;
    }

    /**
     * Delete player data (for server admin or player leaving)
     */
    public void deletePlayerTasks(UUID playerUuid) throws IOException {
        Path playersDir = dataDir.resolve(PLAYERS_FOLDER);
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        if (Files.exists(playerFile)) {
            Files.delete(playerFile);
            TodoListMod.LOGGER.info("Deleted task data for player {}", playerUuid);
        }
    }

    /**
     * Export tasks to a backup file
     */
    public void exportBackup(UUID playerUuid, Path backupPath) throws IOException {
        List<Task> tasks = loadPlayerTasks(playerUuid);
        saveTasksToFile(tasks, backupPath);
        TodoListMod.LOGGER.info("Exported {} tasks to backup: {}", tasks.size(), backupPath);
    }

    /**
     * Import tasks from a backup file
     */
    public List<Task> importBackup(Path backupPath) throws IOException {
        if (!Files.exists(backupPath)) {
            throw new IOException("Backup file not found: " + backupPath);
        }
        List<Task> tasks = loadTasksFromFile(backupPath);
        TodoListMod.LOGGER.info("Imported {} tasks from backup", tasks.size());
        return tasks;
    }

    /**
     * Check if player data exists
     */
    public boolean hasPlayerTasks(UUID playerUuid) {
        Path playersDir = dataDir.resolve(PLAYERS_FOLDER);
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        return Files.exists(playerFile);
    }

    /**
     * Get data directory path (for debugging)
     */
    public Path getDataDirectoryPath() {
        return dataDir;
    }
}
