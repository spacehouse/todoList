package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.platform.DataPathProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目存储组件。
 * 负责读取和保存个人项目、团队项目的数据文件。
 */
public class ProjectStorage {
    private static final String PERSONAL_PROJECTS_FILE = "projects.dat";
    private static final String TEAM_PROJECTS_FILE = "team_projects.dat";

    /**
     * 创建项目存储组件，并预热项目目录。
     */
    public ProjectStorage() {
        ensureDirectoryExists();
    }

    /**
     * 返回项目数据目录。
     *
     * @return 项目数据目录
     */
    private Path getProjectsDirectory() {
        return DataPathProvider.getProjectsDir();
    }

    /**
     * 确保项目数据目录存在。
     */
    private void ensureDirectoryExists() {
        try {
            Path projectsDir = getProjectsDirectory();
            if (!Files.exists(projectsDir)) {
                Files.createDirectories(projectsDir);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to create projects directory", e);
        }
    }

    /**
     * 读取个人项目列表。
     *
     * @return 个人项目列表
     * @throws IOException 当读取文件失败时抛出
     */
    public List<Project> loadProjects() throws IOException {
        ensureDirectoryExists();
        Path file = getProjectsDirectory().resolve(PERSONAL_PROJECTS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(file);
    }

    /**
     * 读取团队项目列表。
     *
     * @return 团队项目列表
     * @throws IOException 当读取文件失败时抛出
     */
    public List<Project> loadTeamProjects() throws IOException {
        ensureDirectoryExists();
        Path file = getProjectsDirectory().resolve(TEAM_PROJECTS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(file);
    }

    /**
     * 保存个人项目列表。
     *
     * @param projects 待保存的个人项目列表
     * @throws IOException 当写入文件失败时抛出
     */
    public void saveProjects(List<Project> projects) throws IOException {
        ensureDirectoryExists();
        Path file = getProjectsDirectory().resolve(PERSONAL_PROJECTS_FILE);
        saveProjectsToFile(projects, file);
    }

    /**
     * 保存团队项目列表。
     *
     * @param projects 待保存的团队项目列表
     * @throws IOException 当写入文件失败时抛出
     */
    public void saveTeamProjects(List<Project> projects) throws IOException {
        ensureDirectoryExists();
        Path file = getProjectsDirectory().resolve(TEAM_PROJECTS_FILE);
        saveProjectsToFile(projects, file);
    }

    /**
     * 判断个人项目数据文件是否存在。
     * 当前用于初始化阶段区分首次创建与已有存档恢复。
     *
     * @return 若个人项目文件存在则返回 true
     */
    public boolean hasPersonalProjectsFile() {
        ensureDirectoryExists();
        Path file = getProjectsDirectory().resolve(PERSONAL_PROJECTS_FILE);
        return Files.exists(file);
    }

    /**
     * 从指定文件读取项目列表，并在必要时回写规范化后的数据。
     *
     * @param file 项目数据文件
     * @return 读取到的项目列表
     * @throws IOException 当读取文件失败时抛出
     */
    private List<Project> loadProjectsFromFile(Path file) throws IOException {
        List<Project> projects = new ArrayList<>();
        CompoundTag root = NbtIo.read(file);
        boolean dirty = false;
        if (root != null && root.contains("projects", 9)) {
            ListTag list = root.getList("projects", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag projectNbt = list.getCompound(i);
                boolean hadValidId = projectNbt.contains("id") && !projectNbt.getString("id").trim().isEmpty();
                boolean scopeDirty = false;
                if (!projectNbt.contains("scope")) {
                    scopeDirty = true;
                } else {
                    try {
                        Project.Scope.valueOf(projectNbt.getString("scope").trim());
                    } catch (IllegalArgumentException e) {
                        scopeDirty = true;
                    }
                }
                Project project = Project.fromNbt(projectNbt);
                projects.add(project);
                if (!hadValidId || scopeDirty) {
                    dirty = true;
                }
            }
        }
        if (dirty) {
            try {
                saveProjectsToFile(projects, file);
                TodoConstants.LOGGER.info("Rewrote projects file with normalized ids/scopes: {}", file);
            } catch (Exception e) {
                TodoConstants.LOGGER.warn("Failed to rewrite projects file with normalized ids/scopes: {}", file, e);
            }
        }
        return projects;
    }

    /**
     * 将项目列表写入指定文件。
     *
     * @param projects 待写入的项目列表
     * @param file 目标文件
     * @throws IOException 当写入文件失败时抛出
     */
    private void saveProjectsToFile(List<Project> projects, Path file) throws IOException {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (Project project : projects) {
            list.add(project.toNbt());
        }
        root.put("projects", list);
        NbtIo.write(root, file);
    }
}
