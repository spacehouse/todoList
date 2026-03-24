package com.todolist.task;

import com.todolist.TodoConstants;
import com.todolist.platform.DataPathProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles task data persistence
 *
 * Storage structure:
 * - Single player: saves/worldname/todo/moddata.dat
 * - Multiplayer: world/todo/players/{uuid}.dat
 */
public class TaskStorage {
    private static final int NBT_COMPOUND_TYPE = 10;
    private static final String DATA_FILE = "moddata.dat";
    private static final String TEAM_FILE = "team_tasks.dat";

    private boolean loggedNoTaskData;
    private boolean loggedNoTeamTaskData;
    private final Map<Path, Long> lastLoggedLastSavedByFile = new HashMap<>();
    private final Map<Path, Integer> lastLoggedTaskCountByFile = new HashMap<>();

    public TaskStorage() {
        ensureDirectoryExists();
    }

    /**
     * Get the data directory path
     */
    private Path getDataDirectory() {
        return DataPathProvider.getTodoDataDir();
    }

    /**
     * Ensure data directory exists
     */
    private void ensureDirectoryExists() {
        try {
            Path dataDir = getDataDirectory();
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                TodoConstants.LOGGER.info("Created data directory: {}", dataDir);
            }

            // Create players folder for multiplayer
            Path playersDir = DataPathProvider.getTaskPlayersDir();
            if (!Files.exists(playersDir)) {
                Files.createDirectories(playersDir);
                TodoConstants.LOGGER.info("Created players directory: {}", playersDir);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to create data directory", e);
        }
    }

    /**
     * Save tasks to local storage (single player)
     */
    public void saveTasks(List<Task> tasks) throws IOException {
        ensureDirectoryExists();
        Path dataFile = getDataDirectory().resolve(DATA_FILE);
        saveTasksToFile(tasks, dataFile);
        TodoConstants.LOGGER.info("Saved {} tasks to {}", tasks.size(), dataFile);
    }

    /**
     * Save tasks for a specific player (multiplayer)
     */
    public void savePlayerTasks(UUID playerUuid, List<Task> tasks) throws IOException {
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        saveTasksToFile(tasks, playerFile);
        TodoConstants.LOGGER.info("Saved {} tasks for player {}", tasks.size(), playerUuid);
    }

    /**
     * 按当前服务端运行模式保存个人任务，单人本地模式写入单文件，其余模式写入玩家文件。
     */
    public void savePersonalTasks(MinecraftServer server, UUID playerUuid, List<Task> tasks) throws IOException {
        if (shouldUseLocalPersonalStorage(server)) {
            saveTasks(tasks);
            return;
        }
        savePlayerTasks(playerUuid, tasks);
    }

    public void saveTeamTasks(List<Task> tasks) throws IOException {
        ensureDirectoryExists();
        Path teamFile = getDataDirectory().resolve(TEAM_FILE);
        saveTasksToFile(tasks, teamFile);
        TodoConstants.LOGGER.info("Saved {} team tasks to {}", tasks.size(), teamFile);
    }

    /**
     * Save tasks to a specific file
     */
    private void saveTasksToFile(List<Task> tasks, Path file) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putLong("lastSaved", System.currentTimeMillis());
        root.putInt("version", 1);

        ListTag taskList = new ListTag();
        for (Task task : tasks) {
            taskList.add(task.toNbt());
        }
        root.put("tasks", taskList);

        // Write to file
        NbtIo.write(root, file);
    }

    /**
     * Load tasks from local storage (single player) - Safe version
     */
    public List<Task> loadTasksSafe() {
        try {
            return loadTasks();
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to load tasks safely", e);
            return new ArrayList<>();
        }
    }

    /**
     * Load tasks from local storage (single player)
     */
    public List<Task> loadTasks() throws IOException {
        ensureDirectoryExists();
        Path dataFile = getDataDirectory().resolve(DATA_FILE);
        if (!Files.exists(dataFile)) {
            if (!loggedNoTaskData) {
                loggedNoTaskData = true;
                TodoConstants.LOGGER.info("No existing task data found, starting fresh");
            }
            return new ArrayList<>();
        }
        return loadTasksFromFile(dataFile);
    }

    /**
     * Load tasks for a specific player (multiplayer)
     */
    public List<Task> loadPlayerTasks(UUID playerUuid) throws IOException {
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        if (!Files.exists(playerFile)) {
            TodoConstants.LOGGER.info("No existing task data for player {}", playerUuid);
            return new ArrayList<>();
        }
        return loadTasksFromFile(playerFile);
    }

    /**
     * 按当前服务端运行模式读取个人任务，单人本地模式优先使用单文件，其余模式读取玩家文件。
     */
    public List<Task> loadPersonalTasks(MinecraftServer server, UUID playerUuid) throws IOException {
        if (shouldUseLocalPersonalStorage(server)) {
            return loadTasks();
        }
        return loadPlayerTasks(playerUuid);
    }

    public List<Task> loadTeamTasks() throws IOException {
        ensureDirectoryExists();
        Path teamFile = getDataDirectory().resolve(TEAM_FILE);
        if (!Files.exists(teamFile)) {
            if (!loggedNoTeamTaskData) {
                loggedNoTeamTaskData = true;
                TodoConstants.LOGGER.info("No existing team task data");
            }
            return new ArrayList<>();
        }
        return loadTasksFromFile(teamFile);
    }

    /**
     * Load tasks from a specific file
     */
    private List<Task> loadTasksFromFile(Path file) throws IOException {
        CompoundTag root = NbtIo.read(file);
        if (root == null) {
            TodoConstants.LOGGER.warn("Failed to read task data from {}", file);
            return new ArrayList<>();
        }

        long lastSaved = root.getLong("lastSaved");
        int version = root.getInt("version");

        ListTag taskList = root.getList("tasks", NBT_COMPOUND_TYPE);
        List<Task> tasks = new ArrayList<>();

        for (int i = 0; i < taskList.size(); i++) {
            CompoundTag taskNbt = taskList.getCompound(i);
            try {
                Task task = Task.fromNbt(taskNbt);
                tasks.add(task);
            } catch (Exception e) {
                TodoConstants.LOGGER.error("Failed to load task at index {}", i, e);
            }
        }

        maybeLogLoadSummary(file, version, lastSaved, tasks.size());
        return tasks;
    }

    public long getLocalTasksLastSaved() {
        ensureDirectoryExists();
        return readLastSavedSafe(getDataDirectory().resolve(DATA_FILE));
    }

    public long getPlayerTasksLastSaved(UUID playerUuid) {
        if (playerUuid == null) {
            return 0L;
        }
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        return readLastSavedSafe(playerFile);
    }

    /**
     * 按当前服务端运行模式读取个人任务文件的最后保存时间戳。
     */
    public long getPersonalTasksLastSaved(MinecraftServer server, UUID playerUuid) {
        if (shouldUseLocalPersonalStorage(server)) {
            return getLocalTasksLastSaved();
        }
        return getPlayerTasksLastSaved(playerUuid);
    }

    private long readLastSavedSafe(Path file) {
        if (file == null || !Files.exists(file)) {
            return 0L;
        }
        try {
            CompoundTag root = NbtIo.read(file);
            if (root == null) {
                return 0L;
            }
            return root.getLong("lastSaved");
        } catch (Exception e) {
            return 0L;
        }
    }

    private void maybeLogLoadSummary(Path file, int version, long lastSaved, int taskCount) {
        Long lastLoggedLastSaved = lastLoggedLastSavedByFile.get(file);
        Integer lastLoggedCount = lastLoggedTaskCountByFile.get(file);
        if (lastLoggedLastSaved != null && lastLoggedCount != null &&
                lastLoggedLastSaved == lastSaved && lastLoggedCount == taskCount) {
            return;
        }
        lastLoggedLastSavedByFile.put(file, lastSaved);
        lastLoggedTaskCountByFile.put(file, taskCount);
        TodoConstants.LOGGER.debug("Loaded task data from {}, version {}, last saved: {}, tasks: {}", file, version, lastSaved, taskCount);
    }

    /**
     * Delete player data (for server admin or player leaving)
     */
    public void deletePlayerTasks(UUID playerUuid) throws IOException {
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        if (Files.exists(playerFile)) {
            Files.delete(playerFile);
            TodoConstants.LOGGER.info("Deleted task data for player {}", playerUuid);
        }
    }

    /**
     * Export tasks to a backup file
     */
    public void exportBackup(UUID playerUuid, Path backupPath) throws IOException {
        List<Task> tasks = loadPlayerTasks(playerUuid);
        saveTasksToFile(tasks, backupPath);
        TodoConstants.LOGGER.info("Exported {} tasks to backup: {}", tasks.size(), backupPath);
    }

    /**
     * Import tasks from a backup file
     */
    public List<Task> importBackup(Path backupPath) throws IOException {
        if (!Files.exists(backupPath)) {
            throw new IOException("Backup file not found: " + backupPath);
        }
        List<Task> tasks = loadTasksFromFile(backupPath);
        TodoConstants.LOGGER.info("Imported {} tasks from backup", tasks.size());
        return tasks;
    }

    /**
     * Check if player data exists
     */
    public boolean hasPlayerTasks(UUID playerUuid) {
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        return Files.exists(playerFile);
    }

    /**
     * 按当前服务端运行模式判断个人任务文件是否已存在。
     */
    public boolean hasPersonalTasks(MinecraftServer server, UUID playerUuid) {
        if (shouldUseLocalPersonalStorage(server)) {
            ensureDirectoryExists();
            return Files.exists(getDataDirectory().resolve(DATA_FILE));
        }
        return hasPlayerTasks(playerUuid);
    }

    /**
     * Get data directory path (for debugging)
     */
    public Path getDataDirectoryPath() {
        return getDataDirectory();
    }

    /**
     * 判断当前服务端是否应使用单人本地个人任务文件。
     */
    public boolean shouldUseLocalPersonalStorage(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        if (server.isDedicatedServer()) {
            return false;
        }
        return !server.isPublished();
    }
}


