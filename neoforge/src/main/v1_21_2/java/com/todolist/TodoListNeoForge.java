package com.todolist;

import com.todolist.bootstrap.CommandBootstrap;
import com.todolist.bootstrap.EventBootstrap;
import com.todolist.client.NeoForgeTodoClient;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
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
 * Todo List Mod - NeoForge 鍏ュ彛绫汇€?
 * 璐熻矗 NeoForge 渚у垵濮嬪寲銆佸懡浠ゆ敞鍐屼笌鏈嶅姟绔敓鍛藉懆鏈熷鐞嗐€?
 */
@Mod(TodoListNeoForge.MOD_ID)
public class TodoListNeoForge {
    public static final String MOD_ID = "todolist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TaskStorage taskStorage;
    private static ProjectStorage projectStorage;
    private static ProjectManager projectManager;

    /**
     * NeoForge 妯″潡鏋勯€犲嚱鏁帮紝鎵ц骞冲彴鍒濆鍖栦笌娉ㄥ唽銆?
     */
    // v1_21_2 覆盖：构造器注入 mod bus 仅用于注册类事件；本类 @SubscribeEvent 全为游戏事件，仍注册 game bus
    public TodoListNeoForge(IEventBus modEventBus, ModContainer modContainer) {
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

        NeoForgeNetworkBridge.init(modEventBus);
        NeoForgeTaskPacketRegistrar.register();
        NeoForgeProjectPacketRegistrar.register();
        runClientInitIfNeeded(modEventBus, modContainer);

        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("Todo List Mod (NeoForge) initialized!");
    }

    /**
     * NeoForge 1.21.1 绉婚櫎浜?DisplayTest 鎵╁睍鐐癸紝杩欓噷淇濈暀绌哄疄鐜颁互淇濇寔缁撴瀯涓€鑷淬€?
     */
    private void registerDisplayTest() {
        // NeoForge 鏂扮増涓嶉渶瑕?DisplayTest锛屼繚鐣欏崰浣嶉伩鍏嶆敼鍔ㄨ繃澶с€?
    }

    /**
     * 鍦ㄥ鎴风鐜涓嬪垵濮嬪寲瀹㈡埛绔€昏緫銆?
     */
    private void runClientInitIfNeeded(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        NeoForgeTodoClient.initialize(modEventBus, modContainer);
    }

    /**
     * 鎵ц椤圭洰涓庝换鍔＄殑杩佺Щ閫昏緫锛屼繚璇侀粯璁ら」鐩笌鏃ф暟鎹吋瀹广€?
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
     * 娓呯悊闈炴硶椤圭洰缁戝畾鐨勪换鍔★紝閬垮厤鏃ф暟鎹畫鐣欍€?
     *
     * @param validPersonalProjectIds 鏈夋晥涓汉椤圭洰 ID 闆嗗悎
     * @param validTeamProjectIds 鏈夋晥鍥㈤槦椤圭洰 ID 闆嗗悎
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
     * 鏀堕泦褰撳墠鑼冨洿涓嬪彲鐢ㄧ殑椤圭洰 ID銆?
     *
     * @param scope 椤圭洰鑼冨洿
     * @return 鏈夋晥椤圭洰 ID 闆嗗悎
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
     * 鍒ゆ柇浠诲姟鏄惁鍥犻」鐩粦瀹氶潪娉曡€岄渶鍒犻櫎銆?
     *
     * @param task 浠诲姟瀵硅薄
     * @param validProjectIds 鏈夋晥椤圭洰 ID 闆嗗悎
     * @return true 琛ㄧず闇€鍒犻櫎
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
     * 澶勭悊 NeoForge 鍛戒护娉ㄥ唽浜嬩欢銆?
     *
     * @param event 鍛戒护娉ㄥ唽浜嬩欢
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandBootstrap.registerReflective(
                event.getDispatcher(),
                event.getBuildContext(),
                event.getCommandSelection()
        );
    }

    /**
     * NeoForge 鏈嶅姟绔惎鍔ㄤ簨浠跺鐞嗐€?
     *
     * @param event 鍚姩浜嬩欢
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        EventBootstrap.handleServerStarting(event.getServer(), this::serverStarting);
    }

    /**
     * NeoForge 鏈嶅姟绔仠姝簨浠跺鐞嗐€?
     *
     * @param event 鍋滄浜嬩欢
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        EventBootstrap.handleServerStopped(event.getServer(), this::serverStopped);
    }

    /**
     * 鏈嶅姟绔惎鍔ㄦ椂鐨勮嚜瀹氫箟閫昏緫銆?
     *
     * @param server MinecraftServer 瀹炰緥
     */
    private void serverStarting(MinecraftServer server) {
        LOGGER.info("Todo List Mod (NeoForge): Server starting...");
    }

    /**
     * 鏈嶅姟绔仠姝㈡椂鐨勮嚜瀹氫箟閫昏緫銆?
     *
     * @param server MinecraftServer 瀹炰緥
     */
    private void serverStopped(MinecraftServer server) {
        LOGGER.info("Todo List Mod (NeoForge): Server stopped, saving data...");
        ProjectSaveDebouncer.flushNow(server);
    }

    /**
     * 鑾峰彇浠诲姟瀛樺偍瀹炰緥銆?
     *
     * @return 浠诲姟瀛樺偍
     */
    public static TaskStorage getTaskStorage() {
        return taskStorage;
    }

    /**
     * 鑾峰彇椤圭洰瀛樺偍瀹炰緥銆?
     *
     * @return 椤圭洰瀛樺偍
     */
    public static ProjectStorage getProjectStorage() {
        return projectStorage;
    }

    /**
     * 鑾峰彇椤圭洰绠＄悊鍣ㄥ疄渚嬨€?
     *
     * @return 椤圭洰绠＄悊鍣?
     */
    public static ProjectManager getProjectManager() {
        return projectManager;
    }
}
