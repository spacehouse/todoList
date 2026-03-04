package com.todolist;

import com.todolist.bootstrap.CommandBootstrap;
import com.todolist.bootstrap.EventBootstrap;
import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
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
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

        // 初始化 Forge 平台的文件路径供应器
        DataPathProvider.setGameDirSupplier(() -> FMLPaths.GAMEDIR.get());

        // 初始化通用业务逻辑
        TodoListCommon.init();

        // 加载模组配置
        ModConfig.load();

        // 缓存通用存储实例
        taskStorage = TodoListCommon.getTaskStorage();
        projectStorage = TodoListCommon.getProjectStorage();
        projectManager = TodoListCommon.getProjectManager();

        // 加载项目数据
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

        // 将当前类实例注册到 Forge 事件总线
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Todo List Mod (Forge) initialized!");
    }

    /**
     * 执行项目与任务的数据迁移，确保旧版本数据能够适配新的项目模型。
     */
    private void performMigration() {
        if (projectManager.getAllProjects().isEmpty()) {
            LOGGER.info("No projects found, creating default projects...");
            Project personalProject = new Project("gui.todolist.project.default.personal", Project.Scope.PERSONAL, null);
            projectManager.addProject(personalProject);
            Project teamProject = new Project("gui.todolist.project.default.team", Project.Scope.TEAM, null);
            projectManager.addProject(teamProject);
            try {
                projectStorage.saveProjects(projectManager.getProjectsByScope(Project.Scope.PERSONAL));
                projectStorage.saveTeamProjects(projectManager.getProjectsByScope(Project.Scope.TEAM));
            } catch (Exception e) {
                LOGGER.error("Failed to save default projects", e);
            }
            migrateTasks(personalProject, teamProject);
        } else {
            Project defaultPersonal = null;
            Project defaultTeam = null;
            for (Project p : projectManager.getAllProjects()) {
                if (p.getScope() == Project.Scope.PERSONAL && "gui.todolist.project.default.personal".equals(p.getName())) {
                    defaultPersonal = p;
                } else if (p.getScope() == Project.Scope.TEAM && "gui.todolist.project.default.team".equals(p.getName())) {
                    defaultTeam = p;
                }
            }
            if (defaultPersonal != null && defaultTeam != null) {
                migrateTasks(defaultPersonal, defaultTeam);
            }
        }
    }

    /**
     * 将无项目关联的任务迁移到指定的默认项目中。
     * @param defaultPersonalProject 默认个人项目
     * @param defaultTeamProject 默认团队项目
     */
    private void migrateTasks(Project defaultPersonalProject, Project defaultTeamProject) {
        try {
            List<Task> personalTasks = taskStorage.loadTasks();
            boolean changed = false;
            for (Task task : personalTasks) {
                if (task.getProjectId() == null) {
                    task.setProjectId(defaultPersonalProject.getId());
                    changed = true;
                }
            }
            if (changed) taskStorage.saveTasks(personalTasks);
        } catch (Exception e) {
            LOGGER.error("Failed to migrate personal tasks", e);
        }

        try {
            List<Task> teamTasks = taskStorage.loadTeamTasks();
            boolean changed = false;
            for (Task task : teamTasks) {
                if (task.getProjectId() == null) {
                    task.setProjectId(defaultTeamProject.getId());
                    changed = true;
                }
            }
            if (changed) taskStorage.saveTeamTasks(teamTasks);
        } catch (Exception e) {
            LOGGER.error("Failed to migrate team tasks", e);
        }
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
}
