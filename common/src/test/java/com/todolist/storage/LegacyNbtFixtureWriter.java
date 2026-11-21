package com.todolist.storage;

import com.todolist.project.Project;
import com.todolist.project.ProjectPlayerStateStorage;
import com.todolist.task.Task;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.todolist.compat.NbtIoCompat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 旧 NBT 存档夹具写入器（仅供离线测试使用）。
 * NBT 文件存储死分支从生产代码移除后，由测试侧直接生成迁移读取器可识别的旧格式 .dat 文件；
 * 字段键与 {@link H2LegacyMigrationReader} 的读取约定保持一致。
 */
public final class LegacyNbtFixtureWriter {
    private LegacyNbtFixtureWriter() {
    }

    /**
     * 写入旧任务桶文件（moddata.dat / team_tasks.dat / {uuid}.dat）。
     *
     * @param file 目标文件
     * @param tasks 任务列表
     * @throws IOException 写入失败时抛出
     */
    public static void writeTaskBucket(Path file, List<Task> tasks) throws IOException {
        CompoundTag root = new CompoundTag();
        root.putLong("lastSaved", System.currentTimeMillis());
        root.putInt("version", 1);
        ListTag taskList = new ListTag();
        for (Task task : tasks) {
            taskList.add(task.toNbt());
        }
        root.put("tasks", taskList);
        write(file, root);
    }

    /**
     * 写入旧项目桶文件（projects.dat / team_projects.dat）。
     *
     * @param file 目标文件
     * @param projects 项目列表
     * @throws IOException 写入失败时抛出
     */
    public static void writeProjectBucket(Path file, List<Project> projects) throws IOException {
        CompoundTag root = new CompoundTag();
        ListTag projectList = new ListTag();
        for (Project project : projects) {
            projectList.add(project.toNbt());
        }
        root.put("projects", projectList);
        write(file, root);
    }

    /**
     * 写入旧玩家项目状态文件（{uuid}.dat）。
     *
     * @param file 目标文件
     * @param state 玩家项目状态
     * @throws IOException 写入失败时抛出
     */
    public static void writePlayerState(Path file, ProjectPlayerStateStorage.ProjectPlayerState state) throws IOException {
        CompoundTag root = new CompoundTag();
        if (state.getActiveProjectId() != null) {
            root.putString("activeProjectId", state.getActiveProjectId());
        }
        root.putBoolean("hudVisible", state.isHudVisible());
        ListTag starredList = new ListTag();
        for (String projectId : state.getHudStarredProjectIds()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("projectId", projectId);
            starredList.add(entry);
        }
        root.put("hudStarredProjectIds", starredList);
        root.putLong("lastSaved", System.currentTimeMillis());
        write(file, root);
    }

    /**
     * 确保父目录存在后写入 NBT 文件。
     *
     * @param file 目标文件
     * @param root NBT 根节点
     * @throws IOException 写入失败时抛出
     */
    private static void write(Path file, CompoundTag root) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        NbtIoCompat.write(root, file);
    }
}
