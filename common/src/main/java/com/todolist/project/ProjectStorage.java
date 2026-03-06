package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.platform.DataPathProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目数据持久化：负责从磁盘加载/保存个人与团队项目列表。
 */
public class ProjectStorage {
    private static final String PERSONAL_PROJECTS_FILE = "projects.dat";
    private static final String TEAM_PROJECTS_FILE = "team_projects.dat";

    private final Path projectsDir;

    public ProjectStorage() {
        this.projectsDir = DataPathProvider.getProjectsDir();
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(projectsDir)) {
                Files.createDirectories(projectsDir);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to create projects directory", e);
        }
    }

    /**
     * 加载个人项目列表。
     */
    public List<Project> loadProjects() throws IOException {
        Path file = projectsDir.resolve(PERSONAL_PROJECTS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(file);
    }

    /**
     * 加载团队项目列表。
     */
    public List<Project> loadTeamProjects() throws IOException {
        Path file = projectsDir.resolve(TEAM_PROJECTS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(file);
    }

    /**
     * 保存个人项目列表。
     */
    public void saveProjects(List<Project> projects) throws IOException {
        Path file = projectsDir.resolve(PERSONAL_PROJECTS_FILE);
        saveProjectsToFile(projects, file);
    }

    /**
     * 保存团队项目列表。
     */
    public void saveTeamProjects(List<Project> projects) throws IOException {
        Path file = projectsDir.resolve(TEAM_PROJECTS_FILE);
        saveProjectsToFile(projects, file);
    }

    /**
     * 判断个人项目数据文件是否存在。
     */
    public boolean hasPersonalProjectsFile() {
        Path file = projectsDir.resolve(PERSONAL_PROJECTS_FILE);
        return Files.exists(file);
    }

    private List<Project> loadProjectsFromFile(Path file) throws IOException {
        List<Project> projects = new ArrayList<>();
        NbtCompound root = NbtIo.read(file.toFile());
        boolean dirty = false;
        if (root != null && root.contains("projects", 9)) {
            NbtList list = root.getList("projects", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound projectNbt = list.getCompound(i);
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

    private void saveProjectsToFile(List<Project> projects, Path file) throws IOException {
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (Project project : projects) {
            list.add(project.toNbt());
        }
        root.put("projects", list);
        NbtIo.write(root, file.toFile());
    }
}

