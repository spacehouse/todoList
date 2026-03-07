package com.todolist;

import com.todolist.TodoConstants;
import com.todolist.bootstrap.CommandBootstrap;
import com.todolist.bootstrap.EventBootstrap;
import com.todolist.config.ModConfig;
import com.todolist.network.FabricProjectPacketRegistrar;
import com.todolist.network.FabricTaskPacketRegistrar;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * Fabric 服务端/通用入口点。
     * 负责初始化通用逻辑、配置、存储以及服务端网络包与事件注册。
     */
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
        FabricTaskPacketRegistrar.register();
        FabricProjectPacketRegistrar.register();

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(CommandBootstrap::register);

        // Register server lifecycle events
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> EventBootstrap.handleServerStarting(server, this::onServerStarting));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> EventBootstrap.handleServerStopped(server, this::onServerStopped));

        LOGGER.info("Todo List Mod loaded successfully!");
    }
    
    /**
     * 执行数据迁移逻辑，用于兼容旧数据与默认项目名称变更。
     */
    private void performMigration() {
        String defaultPersonalName = ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_KEY;
        String defaultTeamName = ProjectNameFormatter.DEFAULT_TEAM_PROJECT_KEY;
        ModConfig config = ModConfig.getInstance();
        boolean changedPersonal = false;
        boolean changedTeam = false;
        Project defaultPersonal = null;
        Project defaultTeam = null;
        int personalCount = 0;
        int teamCount = 0;

        for (Project p : projectManager.getAllProjects()) {
            if (p.getScope() == Project.Scope.PERSONAL) {
                personalCount++;
                String normalizedName = ProjectNameFormatter.normalizeDefaultName(p.getName(), Project.Scope.PERSONAL);
                if (!normalizedName.equals(p.getName())) {
                    p.setName(normalizedName);
                    changedPersonal = true;
                }
                if (p.isDefaultPersonalProject()) {
                    defaultPersonal = p;
                }
            } else if (p.getScope() == Project.Scope.TEAM) {
                teamCount++;
                String normalizedName = ProjectNameFormatter.normalizeDefaultName(p.getName(), Project.Scope.TEAM);
                if (!normalizedName.equals(p.getName())) {
                    p.setName(normalizedName);
                    changedTeam = true;
                }
                if (p.isDefaultTeamProject()) {
                    defaultTeam = p;
                }
            }
        }

        boolean personalProjectsFileMissing = !projectStorage.hasPersonalProjectsFile();
        if (personalCount == 0 && (!config.isDefaultPersonalProjectInitialized() || personalProjectsFileMissing)) {
            defaultPersonal = new Project(defaultPersonalName, Project.Scope.PERSONAL, null);
            defaultPersonal.setId(ProjectNameFormatter.DEFAULT_PERSONAL_PROJECT_ID);
            projectManager.addProject(defaultPersonal);
            personalCount = 1;
            changedPersonal = true;
            LOGGER.info("Created default personal project on initialization");
        }
        if (!config.isDefaultPersonalProjectInitialized()) {
            config.setDefaultPersonalProjectInitialized(true);
        }

        if (defaultTeam == null && teamCount == 0) {
            defaultTeam = new Project(defaultTeamName, Project.Scope.TEAM, null);
            defaultTeam.setId(ProjectNameFormatter.DEFAULT_TEAM_PROJECT_ID);
            projectManager.addProject(defaultTeam);
            changedTeam = true;
            LOGGER.info("Created missing default team project");
        }

        if (changedPersonal || changedTeam) {
            try {
                if (changedPersonal) projectStorage.saveProjects(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
                if (changedTeam) projectStorage.saveTeamProjects(projectManager.getProjectsByScope(Project.Scope.TEAM));
            } catch (Exception e) {
                LOGGER.error("Failed to save migrated default projects", e);
            }
        }

        Set<String> validPersonalProjectIds = collectValidProjectIds(Project.Scope.PERSONAL);
        Set<String> validTeamProjectIds = collectValidProjectIds(Project.Scope.TEAM);
        if (!validPersonalProjectIds.isEmpty() || !validTeamProjectIds.isEmpty()) {
            migrateTasks(validPersonalProjectIds, validTeamProjectIds);
        }
    }

    /**
     * 收敛旧任务中的非法项目引用，仅保留仍然关联到现有项目的任务。
     *
     * @param validPersonalProjectIds 当前有效的个人项目 ID 集合
     * @param validTeamProjectIds     当前有效的团队项目 ID 集合
     */
    private void migrateTasks(Set<String> validPersonalProjectIds, Set<String> validTeamProjectIds) {
        try {
            List<Task> personalTasks = taskStorage.loadTasks();
            int beforeSize = personalTasks.size();
            personalTasks.removeIf(task -> shouldRemoveTaskByProjectBinding(task, validPersonalProjectIds));
            if (beforeSize != personalTasks.size()) {
                taskStorage.saveTasks(personalTasks);
                LOGGER.info("Removed {} orphan personal tasks during migration", beforeSize - personalTasks.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to migrate personal tasks", e);
        }

        try {
            List<Task> teamTasks = taskStorage.loadTeamTasks();
            int beforeSize = teamTasks.size();
            teamTasks.removeIf(task -> shouldRemoveTaskByProjectBinding(task, validTeamProjectIds));
            if (beforeSize != teamTasks.size()) {
                taskStorage.saveTeamTasks(teamTasks);
                LOGGER.info("Removed {} orphan team tasks during migration", beforeSize - teamTasks.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to migrate team tasks", e);
        }
    }

    /**
     * 收集指定范围下全部有效项目 ID。
     *
     * @param scope 项目范围
     * @return 有效项目 ID 集合
     */
    private Set<String> collectValidProjectIds(Project.Scope scope) {
        Set<String> ids = new HashSet<>();
        for (Project project : projectManager.getProjectsByScope(scope)) {
            if (project == null) {
                continue;
            }
            String projectId = project.getId();
            if (projectId != null && !projectId.isEmpty()) {
                ids.add(projectId);
            }
        }
        return ids;
    }

    /**
     * 判断任务是否应因项目绑定非法而在迁移中删除。
     *
     * @param task 任务对象
     * @param validProjectIds 当前范围的有效项目 ID 集合
     * @return true 表示应删除
     */
    private boolean shouldRemoveTaskByProjectBinding(Task task, Set<String> validProjectIds) {
        if (task == null) {
            return true;
        }
        String projectId = task.getProjectId();
        if (projectId == null || projectId.isEmpty()) {
            return true;
        }
        return !validProjectIds.contains(projectId);
    }

    /**
     * 服务端启动回调。
     *
     * @param server Minecraft 服务端实例
     */
    private void onServerStarting(MinecraftServer server) {
        LOGGER.info("Todo List Mod: Server starting, initializing storage...");
        // Server-specific initialization
    }

    /**
     * 服务端停止回调，用于触发最终保存。
     *
     * @param server Minecraft 服务端实例
     */
    private void onServerStopped(MinecraftServer server) {
        LOGGER.info("Todo List Mod: Server stopped, saving data...");
        ProjectSaveDebouncer.flushNow(server);
    }

    /**
     * 获取任务存储实例。
     *
     * @return 任务存储
     */
    public static TaskStorage getTaskStorage() {
        return taskStorage;
    }

    /**
     * 获取项目存储实例。
     *
     * @return 项目存储
     */
    public static ProjectStorage getProjectStorage() {
        return projectStorage;
    }

    /**
     * 获取项目管理器实例。
     *
     * @return 项目管理器
     */
    public static ProjectManager getProjectManager() {
        return projectManager;
    }
}
