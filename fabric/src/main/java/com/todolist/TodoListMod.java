package com.todolist;

import com.todolist.TodoConstants;
import com.todolist.bootstrap.CommandBootstrap;
import com.todolist.bootstrap.EventBootstrap;
import com.todolist.config.ModConfig;
import com.todolist.network.ProjectPackets;
import com.todolist.network.TaskPackets;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectStorage;
import com.todolist.project.ProjectSaveDebouncer;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import com.todolist.platform.DataPathProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

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
    public static final String MOD_ID = TodoConstants.MOD_ID;
    public static final Logger LOGGER = TodoConstants.LOGGER;

    private static TaskStorage taskStorage;
    private static ProjectStorage projectStorage;
    private static ProjectManager projectManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Todo List Mod...");

        // Initialize platform paths
        DataPathProvider.setGameDirSupplier(() -> FabricLoader.getInstance().getGameDir().toAbsolutePath());

        // Initialize common logic
        TodoListCommon.init();

        // Initialize configuration
        ModConfig.load();

        // Use common storage instances
        taskStorage = TodoListCommon.getTaskStorage();
        projectStorage = TodoListCommon.getProjectStorage();
        projectManager = TodoListCommon.getProjectManager();

        // Load projects
        try {
            // Load personal projects (singleplayer/server-global)
            List<Project> projects = projectStorage.loadProjects();
            for (Project project : projects) {
                projectManager.addProject(project);
            }
            
            // Load team projects
            List<Project> teamProjects = projectStorage.loadTeamProjects();
            for (Project project : teamProjects) {
                projectManager.addProject(project);
            }
            
            LOGGER.info("Loaded {} projects ({} personal, {} team)", 
                projects.size() + teamProjects.size(), projects.size(), teamProjects.size());
                
            // Perform migration if needed
            performMigration();
            
        } catch (Exception e) {
            LOGGER.error("Failed to load projects", e);
        }

        // Register server-side network packets
        TaskPackets.registerServerPackets();
        ProjectPackets.registerServerPackets();

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(CommandBootstrap::register);

        // Register server lifecycle events
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> EventBootstrap.handleServerStarting(server, this::onServerStarting));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> EventBootstrap.handleServerStopped(server, this::onServerStopped));

        LOGGER.info("Todo List Mod loaded successfully!");
    }
    
    private void performMigration() {
        // 1. Check if we need to create default projects
        if (projectManager.getAllProjects().isEmpty()) {
            LOGGER.info("No projects found, creating default projects...");
            
            // Create default Personal project
            Project personalProject = new Project("gui.todolist.project.default.personal", Project.Scope.PERSONAL, null);
            projectManager.addProject(personalProject);
            
            // Create default Team project
            Project teamProject = new Project("gui.todolist.project.default.team", Project.Scope.TEAM, null);
            projectManager.addProject(teamProject);
            
            try {
                // Save separately
                projectStorage.saveProjects(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
                projectStorage.saveTeamProjects(projectManager.getProjectsByScope(Project.Scope.TEAM));
            } catch (Exception e) {
                LOGGER.error("Failed to save default projects", e);
            }
            
            // 2. Migrate existing tasks
            migrateTasks(personalProject, teamProject);
        } else {
            // Check for legacy default names and update them
            boolean changedPersonal = false;
            boolean changedTeam = false;
            Project defaultPersonal = null;
            Project defaultTeam = null;
            
            for (Project p : projectManager.getAllProjects()) {
                if (p.getScope() == Project.Scope.PERSONAL) {
                    if ("Inbox".equals(p.getName())) {
                        p.setName("gui.todolist.project.default.personal");
                        changedPersonal = true;
                    }
                    if ("gui.todolist.project.default.personal".equals(p.getName())) {
                        defaultPersonal = p;
                    }
                } else if (p.getScope() == Project.Scope.TEAM) {
                    if ("General".equals(p.getName())) {
                        p.setName("gui.todolist.project.default.team");
                        changedTeam = true;
                    }
                    if ("gui.todolist.project.default.team".equals(p.getName())) {
                        defaultTeam = p;
                    }
                }
            }
            
            if (changedPersonal || changedTeam) {
                try {
                    if (changedPersonal) projectStorage.saveProjects(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
                    if (changedTeam) projectStorage.saveTeamProjects(projectManager.getProjectsByScope(Project.Scope.TEAM));
                    LOGGER.info("Updated legacy default project names to translation keys");
                } catch (Exception e) {
                    LOGGER.error("Failed to save updated project names", e);
                }
            }
            
            // Migrate tasks for existing projects if they have null projectId
            if (defaultPersonal != null && defaultTeam != null) {
                migrateTasks(defaultPersonal, defaultTeam);
            }
        }
    }

    private void migrateTasks(Project defaultPersonalProject, Project defaultTeamProject) {
        // Migrate Personal Tasks (moddata.dat)
        try {
            List<Task> personalTasks = taskStorage.loadTasks();
            boolean changed = false;
            for (Task task : personalTasks) {
                if (task.getProjectId() == null) {
                    task.setProjectId(defaultPersonalProject.getId());
                    changed = true;
                }
            }
            if (changed) {
                taskStorage.saveTasks(personalTasks);
                LOGGER.info("Migrated {} personal tasks to project {}", personalTasks.size(), defaultPersonalProject.getName());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to migrate personal tasks", e);
        }

        // Migrate Team Tasks (team_tasks.dat)
        try {
            List<Task> teamTasks = taskStorage.loadTeamTasks();
            boolean changed = false;
            for (Task task : teamTasks) {
                if (task.getProjectId() == null) {
                    task.setProjectId(defaultTeamProject.getId());
                    changed = true;
                }
            }
            if (changed) {
                taskStorage.saveTeamTasks(teamTasks);
                LOGGER.info("Migrated {} team tasks to project {}", teamTasks.size(), defaultTeamProject.getName());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to migrate team tasks", e);
        }
    }

    private void onServerStarting(MinecraftServer server) {
        LOGGER.info("Todo List Mod: Server starting, initializing storage...");
        // Server-specific initialization
    }

    private void onServerStopped(MinecraftServer server) {
        LOGGER.info("Todo List Mod: Server stopped, saving data...");
        ProjectSaveDebouncer.flushNow(server);
    }

    public static TaskStorage getTaskStorage() {
        return taskStorage;
    }

    public static ProjectStorage getProjectStorage() {
        return projectStorage;
    }

    public static ProjectManager getProjectManager() {
        return projectManager;
    }
}
