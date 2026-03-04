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
 * 澶勭悊椤圭洰鏁版嵁鐨勬寔涔呭寲瀛樺偍銆? */
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
     * 鍔犺浇涓汉椤圭洰鍒楄〃銆?     */
    public List<Project> loadProjects() throws IOException {
        Path file = projectsDir.resolve(PERSONAL_PROJECTS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(file);
    }

    /**
     * 鍔犺浇鍥㈤槦椤圭洰鍒楄〃銆?     */
    public List<Project> loadTeamProjects() throws IOException {
        Path file = projectsDir.resolve(TEAM_PROJECTS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(file);
    }

    /**
     * 淇濆瓨涓汉椤圭洰鍒楄〃銆?     */
    public void saveProjects(List<Project> projects) throws IOException {
        Path file = projectsDir.resolve(PERSONAL_PROJECTS_FILE);
        saveProjectsToFile(projects, file);
    }

    /**
     * 淇濆瓨鍥㈤槦椤圭洰鍒楄〃銆?     */
    public void saveTeamProjects(List<Project> projects) throws IOException {
        Path file = projectsDir.resolve(TEAM_PROJECTS_FILE);
        saveProjectsToFile(projects, file);
    }

    private List<Project> loadProjectsFromFile(Path file) throws IOException {
        List<Project> projects = new ArrayList<>();
        NbtCompound root = NbtIo.read(file.toFile());
        if (root != null && root.contains("projects", 9)) {
            NbtList list = root.getList("projects", 10);
            for (int i = 0; i < list.size(); i++) {
                projects.add(Project.fromNbt(list.getCompound(i)));
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


