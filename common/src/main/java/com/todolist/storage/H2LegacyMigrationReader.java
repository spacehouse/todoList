package com.todolist.storage;

import com.todolist.persistence.SafePersistenceHelper;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.task.Task;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * H2LegacyMigrationReader 负责以只读方式读取旧 NBT 数据，供 H2 首次迁移使用。
 */
public final class H2LegacyMigrationReader {
    private static final int NBT_LIST_TYPE = 9;
    private static final int NBT_COMPOUND_TYPE = 10;

    /**
     * 创建旧数据迁移读取器。
     */
    public H2LegacyMigrationReader() {
    }

    /**
     * 读取当前命名空间下所有旧 NBT 数据快照。
     *
     * @return 旧数据迁移快照
     * @throws IOException 读取失败时抛出
     */
    public LegacyMigrationData readAll() throws IOException {
        List<LegacyTaskBucket> taskBuckets = new ArrayList<>();
        readTaskBucket(DataPathProvider.getTodoDataDir().resolve("moddata.dat"), "LOCAL_PERSONAL", "LOCAL", taskBuckets);
        readTaskBucket(DataPathProvider.getTodoDataDir().resolve("team_tasks.dat"), "TEAM", "TEAM", taskBuckets);
        readPlayerTaskBuckets(taskBuckets);

        List<LegacyProjectBucket> projectBuckets = new ArrayList<>();
        readProjectBucket(DataPathProvider.getProjectsDir().resolve("projects.dat"), "PERSONAL_PROJECTS", projectBuckets);
        readProjectBucket(DataPathProvider.getProjectsDir().resolve("team_projects.dat"), "TEAM_PROJECTS", projectBuckets);

        List<LegacyPlayerProjectStateRecord> playerProjectStates = readPlayerProjectStates();
        return new LegacyMigrationData(taskBuckets, projectBuckets, playerProjectStates);
    }

    /**
     * 读取指定任务桶。
     *
     * @param file 旧任务文件
     * @param bucketType 任务桶类型
     * @param ownerUuid 桶所有者
     * @param output 输出列表
     * @throws IOException 读取失败时抛出
     */
    private void readTaskBucket(Path file, String bucketType, String ownerUuid, List<LegacyTaskBucket> output) throws IOException {
        SafePersistenceHelper.ReadResult<CompoundTag> readResult = SafePersistenceHelper.readWithRecoveryReadOnly(
                file,
                "legacy task data",
                path -> NbtIo.read(path.toFile()),
                root -> root != null
        );
        if (!readResult.isFound()) {
            return;
        }
        CompoundTag root = readResult.getValue();
        List<Task> tasks = new ArrayList<>();
        if (root.contains("tasks", NBT_LIST_TYPE)) {
            ListTag taskList = root.getList("tasks", NBT_COMPOUND_TYPE);
            for (int index = 0; index < taskList.size(); index++) {
                tasks.add(Task.fromNbt(taskList.getCompound(index)));
            }
        }
        output.add(new LegacyTaskBucket(bucketType, ownerUuid, root.getLong("lastSaved"), tasks));
    }

    /**
     * 读取所有玩家个人任务桶。
     *
     * @param output 输出列表
     * @throws IOException 读取失败时抛出
     */
    private void readPlayerTaskBuckets(List<LegacyTaskBucket> output) throws IOException {
        Path playersDir = DataPathProvider.getTaskPlayersDir();
        if (!Files.isDirectory(playersDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(playersDir)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                String fileName = file.getFileName().toString();
                String uuidText = fileName.substring(0, fileName.length() - ".dat".length());
                readTaskBucket(file, "PLAYER_PERSONAL", uuidText, output);
            }
        }
    }

    /**
     * 读取指定项目桶，不触发项目文件规范化回写。
     *
     * @param file 旧项目文件
     * @param bucketType 项目桶类型
     * @param output 输出列表
     * @throws IOException 读取失败时抛出
     */
    private void readProjectBucket(Path file, String bucketType, List<LegacyProjectBucket> output) throws IOException {
        SafePersistenceHelper.ReadResult<CompoundTag> readResult = SafePersistenceHelper.readWithRecoveryReadOnly(
                file,
                "legacy project data",
                path -> NbtIo.read(path.toFile()),
                root -> root != null
        );
        if (!readResult.isFound()) {
            return;
        }
        CompoundTag root = readResult.getValue();
        List<Project> projects = new ArrayList<>();
        if (root.contains("projects", NBT_LIST_TYPE)) {
            ListTag list = root.getList("projects", NBT_COMPOUND_TYPE);
            for (int index = 0; index < list.size(); index++) {
                projects.add(Project.fromNbt(list.getCompound(index)));
            }
        }
        output.add(new LegacyProjectBucket(bucketType, projects));
    }

    /**
     * 读取所有玩家项目状态文件。
     *
     * @return 玩家项目状态快照列表
     * @throws IOException 读取失败时抛出
     */
    private List<LegacyPlayerProjectStateRecord> readPlayerProjectStates() throws IOException {
        List<LegacyPlayerProjectStateRecord> states = new ArrayList<>();
        Path playersDir = DataPathProvider.getProjectPlayersDir();
        if (!Files.isDirectory(playersDir)) {
            return states;
        }
        try (Stream<Path> stream = Files.list(playersDir)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".dat")).toList()) {
                String fileName = file.getFileName().toString();
                UUID playerUuid = UUID.fromString(fileName.substring(0, fileName.length() - ".dat".length()));
                readPlayerProjectState(file, playerUuid, states);
            }
        }
        return states;
    }

    /**
     * 读取单个玩家项目状态文件。
     *
     * @param file 状态文件
     * @param playerUuid 玩家 UUID
     * @param output 输出列表
     * @throws IOException 读取失败时抛出
     */
    private void readPlayerProjectState(Path file, UUID playerUuid, List<LegacyPlayerProjectStateRecord> output) throws IOException {
        SafePersistenceHelper.ReadResult<CompoundTag> readResult = SafePersistenceHelper.readWithRecoveryReadOnly(
                file,
                "legacy project player state",
                path -> NbtIo.read(path.toFile()),
                root -> root != null
        );
        if (!readResult.isFound()) {
            return;
        }
        CompoundTag root = readResult.getValue();
        String activeProjectId = readOptionalTrimmedString(root, "activeProjectId");
        boolean hudVisible = !root.contains("hudVisible") || root.getBoolean("hudVisible");
        List<String> starredProjectIds = new ArrayList<>();
        if (root.contains("hudStarredProjectIds", NBT_LIST_TYPE)) {
            ListTag starredList = root.getList("hudStarredProjectIds", NBT_COMPOUND_TYPE);
            for (int index = 0; index < starredList.size(); index++) {
                String projectId = readOptionalTrimmedString(starredList.getCompound(index), "projectId");
                if (projectId != null && !starredProjectIds.contains(projectId)) {
                    starredProjectIds.add(projectId);
                }
            }
        }
        ProjectPlayerStateStorage.ProjectPlayerState state = new ProjectPlayerStateStorage.ProjectPlayerState(activeProjectId, starredProjectIds, hudVisible);
        output.add(new LegacyPlayerProjectStateRecord(playerUuid, root.contains("lastSaved") ? root.getLong("lastSaved") : 0L, state));
    }

    /**
     * 从 NBT 中读取可选字符串并去除首尾空白。
     *
     * @param root NBT 根节点
     * @param key 字段名
     * @return 规范化字符串；缺失或空白时返回 null
     */
    private String readOptionalTrimmedString(CompoundTag root, String key) {
        if (root == null || key == null || !root.contains(key)) {
            return null;
        }
        String value = root.getString(key);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
