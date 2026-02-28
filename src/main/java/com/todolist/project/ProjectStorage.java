package com.todolist.project;

import com.todolist.TodoListMod;
import com.todolist.task.TaskStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles project data persistence
 *
 * Storage structure:
 * - Single player: saves/worldname/todo/projects.dat
 * - Multiplayer: world/todo/projects/players/{uuid}.dat (personal)
 *                world/todo/projects/team_projects.dat (team)
 */
public class ProjectStorage {
    private static final String DATA_FOLDER = "todo";
    private static final String PROJECTS_FOLDER = "projects";
    private static final String PROJECTS_FILE = "projects.dat";
    private static final String PLAYERS_FOLDER = "players";
    private static final String TEAM_PROJECTS_FILE = "team_projects.dat";

    private final Path dataDir;

    public ProjectStorage() {
        this.dataDir = getDataDirectory();
        ensureDirectoryExists();
    }

    private Path getDataDirectory() {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath();
        return gameDir.resolve(DATA_FOLDER);
    }

    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            
            Path projectsDir = dataDir.resolve(PROJECTS_FOLDER);
            if (!Files.exists(projectsDir)) {
                Files.createDirectories(projectsDir);
            }

            Path playersDir = projectsDir.resolve(PLAYERS_FOLDER);
            if (!Files.exists(playersDir)) {
                Files.createDirectories(playersDir);
            }
        } catch (IOException e) {
            TodoListMod.LOGGER.error("Failed to create project data directories", e);
        }
    }

    // --- Personal Projects ---

    /**
     * Save personal projects for single player
     */
    public void saveProjects(List<Project> projects) throws IOException {
        Path projectsDir = dataDir.resolve(PROJECTS_FOLDER);
        Path dataFile = projectsDir.resolve(PROJECTS_FILE);
        saveProjectsToFile(projects, dataFile);
        TodoListMod.LOGGER.info("Saved {} projects to {}", projects.size(), dataFile);
    }

    /**
     * Load personal projects for single player
     */
    public List<Project> loadProjects() throws IOException {
        Path projectsDir = dataDir.resolve(PROJECTS_FOLDER);
        Path dataFile = projectsDir.resolve(PROJECTS_FILE);
        if (!Files.exists(dataFile)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(dataFile);
    }
    
    /**
     * Save personal projects for a specific player (multiplayer)
     */
    public void savePlayerProjects(UUID playerUuid, List<Project> projects) throws IOException {
        Path projectsDir = dataDir.resolve(PROJECTS_FOLDER);
        Path playersDir = projectsDir.resolve(PLAYERS_FOLDER);
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        saveProjectsToFile(projects, playerFile);
        TodoListMod.LOGGER.info("Saved {} projects for player {}", projects.size(), playerUuid);
    }

    /**
     * Load personal projects for a specific player (multiplayer)
     */
    public List<Project> loadPlayerProjects(UUID playerUuid) throws IOException {
        Path projectsDir = dataDir.resolve(PROJECTS_FOLDER);
        Path playersDir = projectsDir.resolve(PLAYERS_FOLDER);
        Path playerFile = playersDir.resolve(playerUuid.toString() + ".dat");
        if (!Files.exists(playerFile)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(playerFile);
    }

    // --- Team Projects ---

    public void saveTeamProjects(List<Project> projects) throws IOException {
        Path projectsDir = dataDir.resolve(PROJECTS_FOLDER);
        Path teamFile = projectsDir.resolve(TEAM_PROJECTS_FILE);
        saveProjectsToFile(projects, teamFile);
        TodoListMod.LOGGER.info("Saved team projects snapshot ({} items) to {}", projects.size(), teamFile);
    }

    public List<Project> loadTeamProjects() throws IOException {
        Path projectsDir = dataDir.resolve(PROJECTS_FOLDER);
        Path teamFile = projectsDir.resolve(TEAM_PROJECTS_FILE);
        if (!Files.exists(teamFile)) {
            return new ArrayList<>();
        }
        return loadProjectsFromFile(teamFile);
    }

    // --- Internal Helpers ---

    private void saveProjectsToFile(List<Project> projects, Path file) throws IOException {
        NbtCompound root = new NbtCompound();
        root.putLong("lastSaved", System.currentTimeMillis());
        root.putInt("version", 1);

        NbtList projectList = new NbtList();
        for (Project project : projects) {
            projectList.add(project.toNbt());
        }
        root.put("projects", projectList);

        NbtIo.write(root, file.toFile());
    }

    private List<Project> loadProjectsFromFile(Path file) throws IOException {
        NbtCompound root = NbtIo.read(file.toFile());
        if (root == null) {
            TodoListMod.LOGGER.warn("Failed to read project data from {}", file);
            return new ArrayList<>();
        }

        NbtList projectList = root.getList("projects", NbtElement.COMPOUND_TYPE);
        List<Project> projects = new ArrayList<>();

        for (int i = 0; i < projectList.size(); i++) {
            NbtCompound projectNbt = projectList.getCompound(i);
            try {
                Project project = Project.fromNbt(projectNbt);
                projects.add(project);
            } catch (Exception e) {
                TodoListMod.LOGGER.error("Failed to load project at index {}", i, e);
            }
        }

        return projects;
    }
}
