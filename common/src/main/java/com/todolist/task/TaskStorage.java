package com.todolist.task;

import com.todolist.TodoConstants;
import com.todolist.persistence.SafePersistenceHelper;
import com.todolist.platform.DataPathProvider;
import com.todolist.storage.H2TaskStore;
import com.todolist.storage.StorageBackendFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务存储组件。
 * 负责统一处理单人、本地联机与团队任务的数据落盘、读取及元数据查询。
 */
public class TaskStorage {
    private static final int NBT_COMPOUND_TYPE = 10;
    private static final String DATA_FILE = "moddata.dat";
    private static final String TEAM_FILE = "team_tasks.dat";

    private boolean loggedNoTaskData;
    private boolean loggedNoTeamTaskData;
    private final Map<Path, Long> lastLoggedLastSavedByFile = new HashMap<>();
    private final Map<Path, Integer> lastLoggedTaskCountByFile = new HashMap<>();
    private final H2TaskStore h2TaskStore = new H2TaskStore();

    /**
     * 创建任务存储组件，并预热所需的数据目录。
     */
    public TaskStorage() {
        ensureDirectoryExists();
    }

    /**
     * 返回当前存储命名空间对应的数据目录。
     *
     * @return 当前任务数据目录
     */
    private Path getDataDirectory() {
        return DataPathProvider.getTodoDataDir();
    }

    /**
     * 确保存储目录及玩家子目录存在。
     */
    private void ensureDirectoryExists() {
        try {
            Path dataDir = getDataDirectory();
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                TodoConstants.LOGGER.info("Created data directory: {}", dataDir);
            }

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
     * 保存单人本地任务列表。
     *
     * @param tasks 待保存的任务列表
     * @throws IOException 当写入文件失败时抛出
     */
    public void saveTasks(List<Task> tasks) throws IOException {
        if (StorageBackendFactory.isH2Selected()) {
            h2TaskStore.saveLocalTasks(tasks);
            return;
        }
        ensureDirectoryExists();
        Path dataFile = getDataDirectory().resolve(DATA_FILE);
        saveTasksToFile(tasks, dataFile);
        TodoConstants.LOGGER.info("Saved {} tasks to {}", tasks.size(), dataFile);
    }

    /**
     * 保存指定玩家的个人任务列表。
     *
     * @param playerUuid 玩家 UUID
     * @param tasks 待保存的任务列表
     * @throws IOException 当写入文件失败时抛出
     */
    public void savePlayerTasks(UUID playerUuid, List<Task> tasks) throws IOException {
        if (StorageBackendFactory.isH2Selected()) {
            h2TaskStore.savePlayerTasks(playerUuid, tasks);
            return;
        }
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        saveTasksToFile(tasks, playerFile);
        TodoConstants.LOGGER.info("Saved {} tasks for player {}", tasks.size(), playerUuid);
    }

    /**
     * 按当前服务端运行模式保存个人任务。
     * 单人本地模式写入单文件，其余模式写入玩家文件。
     *
     * @param server 当前服务端
     * @param playerUuid 玩家 UUID
     * @param tasks 待保存的任务列表
     * @throws IOException 当写入文件失败时抛出
     */
    public void savePersonalTasks(MinecraftServer server, UUID playerUuid, List<Task> tasks) throws IOException {
        if (shouldUseLocalPersonalStorage(server)) {
            saveTasks(tasks);
            return;
        }
        savePlayerTasks(playerUuid, tasks);
    }

    /**
     * 保存团队任务列表。
     *
     * @param tasks 待保存的团队任务列表
     * @throws IOException 当写入文件失败时抛出
     */
    public void saveTeamTasks(List<Task> tasks) throws IOException {
        if (StorageBackendFactory.isH2Selected()) {
            h2TaskStore.saveTeamTasks(tasks);
            return;
        }
        ensureDirectoryExists();
        Path teamFile = getDataDirectory().resolve(TEAM_FILE);
        saveTasksToFile(tasks, teamFile);
        TodoConstants.LOGGER.info("Saved {} team tasks to {}", tasks.size(), teamFile);
    }

    /**
     * 将任务列表写入指定文件。
     *
     * @param tasks 待保存的任务列表
     * @param file 目标文件
     * @throws IOException 当写入文件失败时抛出
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
        SafePersistenceHelper.writeBytes(file, serializeTaskRoot(root), "task data");
    }

    /**
     * 安全读取单人本地任务列表。
     * 当前保留给离线测试与调试场景使用，读取失败时返回空列表。
     *
     * @return 读取到的任务列表，失败时返回空列表
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
     * 读取单人本地任务列表。
     *
     * @return 读取到的任务列表
     * @throws IOException 当读取文件失败时抛出
     */
    public List<Task> loadTasks() throws IOException {
        if (StorageBackendFactory.isH2Selected()) {
            return h2TaskStore.loadLocalTasks();
        }
        ensureDirectoryExists();
        Path dataFile = getDataDirectory().resolve(DATA_FILE);
        SafePersistenceHelper.ReadResult<List<Task>> readResult = SafePersistenceHelper.readWithRecovery(
                dataFile,
                "task data",
                this::loadTasksFromFile,
                value -> value != null
        );
        if (!readResult.isFound()) {
            if (!loggedNoTaskData) {
                loggedNoTaskData = true;
                TodoConstants.LOGGER.info("No existing task data found, starting fresh");
            }
            return new ArrayList<>();
        }
        return readResult.getValue();
    }

    /**
     * 读取指定玩家的个人任务列表。
     *
     * @param playerUuid 玩家 UUID
     * @return 读取到的任务列表
     * @throws IOException 当读取文件失败时抛出
     */
    public List<Task> loadPlayerTasks(UUID playerUuid) throws IOException {
        if (StorageBackendFactory.isH2Selected()) {
            return h2TaskStore.loadPlayerTasks(playerUuid);
        }
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        SafePersistenceHelper.ReadResult<List<Task>> readResult = SafePersistenceHelper.readWithRecovery(
                playerFile,
                "player task data",
                this::loadTasksFromFile,
                value -> value != null
        );
        if (!readResult.isFound()) {
            TodoConstants.LOGGER.info("No existing task data for player {}", playerUuid);
            return new ArrayList<>();
        }
        return readResult.getValue();
    }

    /**
     * 按当前服务端运行模式读取个人任务。
     * 单人本地模式优先读取单文件，其余模式读取玩家文件。
     *
     * @param server 当前服务端
     * @param playerUuid 玩家 UUID
     * @return 读取到的任务列表
     * @throws IOException 当读取文件失败时抛出
     */
    public List<Task> loadPersonalTasks(MinecraftServer server, UUID playerUuid) throws IOException {
        if (shouldUseLocalPersonalStorage(server)) {
            return loadTasks();
        }
        return loadPlayerTasks(playerUuid);
    }

    /**
     * 读取团队任务列表。
     *
     * @return 读取到的团队任务列表
     * @throws IOException 当读取文件失败时抛出
     */
    public List<Task> loadTeamTasks() throws IOException {
        if (StorageBackendFactory.isH2Selected()) {
            return h2TaskStore.loadTeamTasks();
        }
        ensureDirectoryExists();
        Path teamFile = getDataDirectory().resolve(TEAM_FILE);
        SafePersistenceHelper.ReadResult<List<Task>> readResult = SafePersistenceHelper.readWithRecovery(
                teamFile,
                "team task data",
                this::loadTasksFromFile,
                value -> value != null
        );
        if (!readResult.isFound()) {
            if (!loggedNoTeamTaskData) {
                loggedNoTeamTaskData = true;
                TodoConstants.LOGGER.info("No existing team task data");
            }
            return new ArrayList<>();
        }
        return readResult.getValue();
    }

    /**
     * 从指定文件读取任务列表。
     *
     * @param file 任务数据文件
     * @return 解析后的任务列表
     * @throws IOException 当读取文件失败时抛出
     */
    private List<Task> loadTasksFromFile(Path file) throws IOException {
        CompoundTag root = NbtIo.read(file);
        if (root == null) {
            throw new IOException("Failed to read task data from " + file);
        }

        long lastSaved = root.getLong("lastSaved");
        int version = root.getInt("version");

        ListTag taskList = root.getList("tasks", NBT_COMPOUND_TYPE);
        List<Task> tasks = new ArrayList<>();
        IOException malformedTaskException = null;

        for (int i = 0; i < taskList.size(); i++) {
            CompoundTag taskNbt = taskList.getCompound(i);
            try {
                Task task = Task.fromNbt(taskNbt);
                tasks.add(task);
            } catch (Exception e) {
                TodoConstants.LOGGER.error("Failed to load task at index {}", i, e);
                IOException currentException = new IOException("Failed to parse task data at index " + i + " from " + file, e);
                if (malformedTaskException == null) {
                    malformedTaskException = currentException;
                } else {
                    malformedTaskException.addSuppressed(currentException);
                }
            }
        }

        if (malformedTaskException != null) {
            throw malformedTaskException;
        }

        maybeLogLoadSummary(file, version, lastSaved, tasks.size());
        return tasks;
    }

    /**
     * 返回单人本地任务文件的最后保存时间戳。
     *
     * @return 最后保存时间戳，不存在时返回 0
     */
    public long getLocalTasksLastSaved() {
        if (StorageBackendFactory.isH2Selected()) {
            try {
                return h2TaskStore.getBucketLastSaved(H2TaskStore.LOCAL_PERSONAL_BUCKET, H2TaskStore.LOCAL_OWNER);
            } catch (IOException exception) {
                TodoConstants.LOGGER.warn("Failed to read H2 local task timestamp", exception);
                return 0L;
            }
        }
        ensureDirectoryExists();
        return readLastSavedSafe(getDataDirectory().resolve(DATA_FILE));
    }

    /**
     * 返回指定玩家任务文件的最后保存时间戳。
     *
     * @param playerUuid 玩家 UUID
     * @return 最后保存时间戳，不存在时返回 0
     */
    public long getPlayerTasksLastSaved(UUID playerUuid) {
        if (playerUuid == null) {
            return 0L;
        }
        if (StorageBackendFactory.isH2Selected()) {
            try {
                return h2TaskStore.getBucketLastSaved(H2TaskStore.PLAYER_PERSONAL_BUCKET, playerUuid.toString());
            } catch (IOException exception) {
                TodoConstants.LOGGER.warn("Failed to read H2 player task timestamp for {}", playerUuid, exception);
                return 0L;
            }
        }
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        return readLastSavedSafe(playerFile);
    }

    /**
     * 按当前服务端运行模式读取个人任务文件的最后保存时间戳。
     *
     * @param server 当前服务端
     * @param playerUuid 玩家 UUID
     * @return 最后保存时间戳，不存在时返回 0
     */
    public long getPersonalTasksLastSaved(MinecraftServer server, UUID playerUuid) {
        if (shouldUseLocalPersonalStorage(server)) {
            return getLocalTasksLastSaved();
        }
        return getPlayerTasksLastSaved(playerUuid);
    }

    /**
     * 安全读取指定文件中的最后保存时间戳。
     *
     * @param file 目标文件
     * @return 最后保存时间戳，不存在或读取失败时返回 0
     */
    private long readLastSavedSafe(Path file) {
        if (file == null || !SafePersistenceHelper.existsOrBackup(file)) {
            return 0L;
        }
        try {
            SafePersistenceHelper.ReadResult<CompoundTag> readResult = SafePersistenceHelper.readWithRecovery(
                    file,
                    "task timestamp data",
                    NbtIo::read,
                    root -> root != null
            );
            if (!readResult.isFound()) {
                return 0L;
            }
            CompoundTag root = readResult.getValue();
            return root.getLong("lastSaved");
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 在文件内容发生变化时记录一次读取摘要，避免重复刷日志。
     *
     * @param file 数据文件
     * @param version 数据版本
     * @param lastSaved 最后保存时间戳
     * @param taskCount 任务数量
     */
    private void maybeLogLoadSummary(Path file, int version, long lastSaved, int taskCount) {
        Long lastLoggedLastSaved = lastLoggedLastSavedByFile.get(file);
        Integer lastLoggedCount = lastLoggedTaskCountByFile.get(file);
        if (lastLoggedLastSaved != null && lastLoggedCount != null
                && lastLoggedLastSaved == lastSaved && lastLoggedCount == taskCount) {
            return;
        }
        lastLoggedLastSavedByFile.put(file, lastSaved);
        lastLoggedTaskCountByFile.put(file, taskCount);
        TodoConstants.LOGGER.debug("Loaded task data from {}, version {}, last saved: {}, tasks: {}", file, version, lastSaved, taskCount);
    }

    /**
     * 判断指定玩家任务文件是否存在。
     *
     * @param playerUuid 玩家 UUID
     * @return 若玩家任务文件存在则返回 true
     */
    public boolean hasPlayerTasks(UUID playerUuid) {
        if (StorageBackendFactory.isH2Selected()) {
            try {
                return h2TaskStore.hasPlayerTasks(playerUuid);
            } catch (IOException exception) {
                TodoConstants.LOGGER.warn("Failed to query H2 player task bucket for {}", playerUuid, exception);
                return false;
            }
        }
        ensureDirectoryExists();
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        return SafePersistenceHelper.existsOrBackup(playerFile);
    }

    /**
     * 返回当前任务数据目录。
     * 当前保留给离线测试与调试入口使用。
     *
     * @return 当前任务数据目录
     */
    public Path getDataDirectoryPath() {
        return getDataDirectory();
    }

    /**
     * 将任务 NBT 根节点序列化为字节数组。
     *
     * @param root 任务 NBT 根节点
     * @return 序列化后的字节数组
     * @throws IOException 当序列化失败时抛出
     */
    private byte[] serializeTaskRoot(CompoundTag root) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream)) {
            NbtIo.write(root, dataOutputStream);
            dataOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        }
    }

    /**
     * 判断当前服务端是否应使用单人本地个人任务文件。
     *
     * @param server 当前服务端
     * @return 单人未发布模式返回 true，否则返回 false
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
