package com.todolist;

import com.todolist.bootstrap.CommandBootstrap;
import com.todolist.bootstrap.EventBootstrap;
import com.todolist.client.ForgeTodoClient;
import com.todolist.config.ModConfig;
import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.forge.network.ForgeProjectPacketRegistrar;
import com.todolist.forge.network.ForgeTaskPacketRegistrar;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectNameFormatter;
import com.todolist.project.ProjectStorage;
import com.todolist.project.ProjectSaveDebouncer;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;
import com.todolist.platform.DataPathProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Todo List Mod - Forge 入口类
 * 负责 Forge 端的模组初始化、命令注册及服务端生命周期事件监听。
 */
@Mod(TodoListForge.MOD_ID)
public class TodoListForge {
    public static final String MOD_ID = "todolist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TaskStorage taskStorage;
    private static ProjectStorage projectStorage;
    private static ProjectManager projectManager;

    /**
     * 模组构造函数，初始化通用逻辑与平台特定配置。
     */
    public TodoListForge() {
        LOGGER.info("Initializing Todo List Mod (Forge)...");
        registerDisplayTest();

        DataPathProvider.setGameDirSupplier(() -> FMLPaths.GAMEDIR.get());

        ModConfig.load();

        TodoListCommon.init();

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

        ForgeNetworkBridge.init();
        ForgeTaskPacketRegistrar.register();
        ForgeProjectPacketRegistrar.register();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ForgeTodoClient::initialize);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Todo List Mod (Forge) initialized!");
    }

    private void registerDisplayTest() {
        try {
            ModLoadingContext.get().registerExtensionPoint(
                    IExtensionPoint.DisplayTest.class,
                    () -> new IExtensionPoint.DisplayTest(
                            () -> "IGNORE_SERVER_VERSION",
                            (remoteVersion, isFromServer) -> true
                    )
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to register display test extension point", e);
        }
    }

    /**
     * 执行项目与任务的数据迁移，确保旧版本数据能够适配新的项目模型。
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
     * 收敛旧任务中的非法项目引用，仅保留仍然关联到现有项目的任务。
     *
     * @param validPersonalProjectIds 当前有效的个人项目 ID 集合
     * @param validTeamProjectIds 当前有效的团队项目 ID 集合
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
     * 监听 Forge 命令注册事件，对接通用的 CommandBootstrap 进行命令注册。
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
     * 通过反射调用目标对象的无参方法，用于兼容不同 Forge/Minecraft 版本的 API 差异。
     * @param target 目标对象
     * @param methodName 方法名
     * @return 反射调用返回值；失败时返回 null
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
     * 监听服务端启动事件，对接通用的 EventBootstrap。
     * @param event 服务端启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        EventBootstrap.handleServerStarting(event.getServer(), this::serverStarting);
    }

    /**
     * 监听服务端停止事件，对接通用的 EventBootstrap，并确保未保存的项目数据被刷新到磁盘。
     * @param event 服务端停止事件
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        EventBootstrap.handleServerStopped(event.getServer(), this::serverStopped);
    }

    /**
     * 服务端启动时的内部处理逻辑。
     * @param server MinecraftServer 实例
     */
    private void serverStarting(MinecraftServer server) {
        LOGGER.info("Todo List Mod (Forge): Server starting...");
    }

    /**
     * 服务端停止时的内部处理逻辑，负责持久化数据。
     * @param server MinecraftServer 实例
     */
    private void serverStopped(MinecraftServer server) {
        LOGGER.info("Todo List Mod (Forge): Server stopped, saving data...");
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
