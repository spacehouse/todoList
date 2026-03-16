package com.todolist;

import com.todolist.bootstrap.CommandBootstrap;
import com.todolist.bootstrap.EventBootstrap;
import com.todolist.config.ModConfig;
import com.todolist.neoforge.network.NeoForgeNetworkBridge;
import com.todolist.neoforge.network.NeoForgeProjectPacketRegistrar;
import com.todolist.neoforge.network.NeoForgeTaskPacketRegistrar;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.project.ProjectSaveDebouncer;
import com.todolist.project.ProjectStorage;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Todo List Mod - NeoForge 入口类。
 * 负责 NeoForge 侧初始化、命令注册与服务端生命周期处理。
 */
@Mod(TodoListNeoForge.MOD_ID)
public class TodoListNeoForge {
    public static final String MOD_ID = "todolist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TaskStorage taskStorage;
    private static ProjectStorage projectStorage;
    private static ProjectManager projectManager;

    /**
     * NeoForge 模块构造函数，执行平台初始化与注册。
     */
    public TodoListNeoForge() {
        LOGGER.info("Initializing Todo List Mod (NeoForge)...");
        registerDisplayTest();

        DataPathProvider.setGameDirSupplier(() -> FMLPaths.GAMEDIR.get());

        TodoListCommon.init();
        ModConfig.load();

        taskStorage = TodoListCommon.getTaskStorage();
        projectStorage = TodoListCommon.getProjectStorage();
        projectManager = TodoListCommon.getProjectManager();

        try {
            List<Project> projects = projectStorage.loadProjects();
            for (Project project : projects) {
                projectManager.addProject(project);
            }

            List<Project> teamProjects = projectStorage.loadTeamProjects();
            for (Project project : teamProjects) {
                projectManager.addProject(project);
            }

            LOGGER.info("Loaded {} projects ({} personal, {} team)",
                    projects.size() + teamProjects.size(), projects.size(), teamProjects.size());

            performMigration();
        } catch (Exception e) {
            LOGGER.error("Failed to load projects", e);
        }

        NeoForgeNetworkBridge.init();
        NeoForgeTaskPacketRegistrar.register();
        NeoForgeProjectPacketRegistrar.register();
        runClientInitIfNeeded();

        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("Todo List Mod (NeoForge) initialized!");
    }

    /**
     * NeoForge 1.21.1 移除了 DisplayTest 扩展点，这里保留空实现以保持结构一致。
     */
    private void registerDisplayTest() {
        // NeoForge 新版不需要 DisplayTest，保留占位避免改动过大。
    }

    /**
     * 在客户端环境下初始化客户端逻辑。
     */
    private void runClientInitIfNeeded() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> clientClass = Class.forName("com.todolist.client.NeoForgeTodoClient");
            clientClass.getMethod("initialize").invoke(null);
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize NeoForge client", e);
        }
    }

    /**
     * 执行项目与任务的迁移逻辑，保证默认项目与旧数据兼容。
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
        }

        if (changedPersonal || changedTeam) {
            try {
                if (changedPersonal) {
                    projectStorage.saveProjects(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
                }
                if (changedTeam) {
                    projectStorage.saveTeamProjects(projectManager.getProjectsByScope(Project.Scope.TEAM));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to save updated default project names", e);
            }
        }
        Set<String> validPersonalProjectIds = collectValidProjectIds(Project.Scope.PERSONAL);
        Set<String> validTeamProjectIds = collectValidProjectIds(Project.Scope.TEAM);
        if (!validPersonalProjectIds.isEmpty() || !validTeamProjectIds.isEmpty()) {
            migrateTasks(validPersonalProjectIds, validTeamProjectIds);
        }
    }

    /**
     * 清理非法项目绑定的任务，避免旧数据残留。
     *
     * @param validPersonalProjectIds 有效个人项目 ID 集合
     * @param validTeamProjectIds 有效团队项目 ID 集合
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
     * 收集当前范围下可用的项目 ID。
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
     * 判断任务是否因项目绑定非法而需删除。
     *
     * @param task 任务对象
     * @param validProjectIds 有效项目 ID 集合
     * @return true 表示需删除
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
     * 处理 NeoForge 命令注册事件。
     *
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        Object dispatcher = invokeNoArg(event, "getDispatcher");
        Object buildContext = invokeNoArg(event, "getBuildContext");
        Object commandSelection = invokeNoArg(event, "getCommandSelection");
        CommandBootstrap.registerReflective(dispatcher, buildContext, commandSelection);
    }

    /**
     * 反射调用无参方法以兼容不同版本 API。
     *
     * @param target 目标对象
     * @param methodName 方法名
     * @return 调用结果或 null
     */
    private Object invokeNoArg(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception e) {
            LOGGER.error("Failed to invoke {} reflectively", methodName, e);
            return null;
        }
    }

    /**
     * NeoForge 服务端启动事件处理。
     *
     * @param event 启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        EventBootstrap.handleServerStarting(event.getServer(), this::serverStarting);
    }

    /**
     * NeoForge 服务端停止事件处理。
     *
     * @param event 停止事件
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        EventBootstrap.handleServerStopped(event.getServer(), this::serverStopped);
    }

    /**
     * 服务端启动时的自定义逻辑。
     *
     * @param server MinecraftServer 实例
     */
    private void serverStarting(MinecraftServer server) {
        LOGGER.info("Todo List Mod (NeoForge): Server starting...");
    }

    /**
     * 服务端停止时的自定义逻辑。
     *
     * @param server MinecraftServer 实例
     */
    private void serverStopped(MinecraftServer server) {
        LOGGER.info("Todo List Mod (NeoForge): Server stopped, saving data...");
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