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
 * Todo List Mod - Forge 鍏ュ彛绫?
 * 璐熻矗 Forge 绔殑妯＄粍鍒濆鍖栥€佸懡浠ゆ敞鍐屽強鏈嶅姟绔敓鍛藉懆鏈熶簨浠剁洃鍚€?
 */
@Mod(TodoListForge.MOD_ID)
public class TodoListForge {
    public static final String MOD_ID = "todolist";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static TaskStorage taskStorage;
    private static ProjectStorage projectStorage;
    private static ProjectManager projectManager;

    /**
     * 妯＄粍鏋勯€犲嚱鏁帮紝鍒濆鍖栭€氱敤閫昏緫涓庡钩鍙扮壒瀹氶厤缃€?
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

    @SuppressWarnings("removal")
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
     * 鎵ц椤圭洰涓庝换鍔＄殑鏁版嵁杩佺Щ锛岀‘淇濇棫鐗堟湰鏁版嵁鑳藉閫傞厤鏂扮殑椤圭洰妯″瀷銆?
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
     * 鏀舵暃鏃т换鍔′腑鐨勯潪娉曢」鐩紩鐢紝浠呬繚鐣欎粛鐒跺叧鑱斿埌鐜版湁椤圭洰鐨勪换鍔°€?
     *
     * @param validPersonalProjectIds 褰撳墠鏈夋晥鐨勪釜浜洪」鐩?ID 闆嗗悎
     * @param validTeamProjectIds 褰撳墠鏈夋晥鐨勫洟闃熼」鐩?ID 闆嗗悎
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
     * 鏀堕泦鎸囧畾鑼冨洿涓嬪叏閮ㄦ湁鏁堥」鐩?ID銆?
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
     * 鍒ゆ柇浠诲姟鏄惁搴斿洜椤圭洰缁戝畾闈炴硶鑰屽湪杩佺Щ涓垹闄ゃ€?
     *
     * @param task 浠诲姟瀵硅薄
     * @param validProjectIds 褰撳墠鑼冨洿鐨勬湁鏁堥」鐩?ID 闆嗗悎
     * @return true 琛ㄧず搴斿垹闄?
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
     * 鐩戝惉 Forge 鍛戒护娉ㄥ唽浜嬩欢锛屽鎺ラ€氱敤鐨?CommandBootstrap 杩涜鍛戒护娉ㄥ唽銆?
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
     * 鐩戝惉鏈嶅姟绔惎鍔ㄤ簨浠讹紝瀵规帴閫氱敤鐨?EventBootstrap銆?
     * @param event 鏈嶅姟绔惎鍔ㄤ簨浠?
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        EventBootstrap.handleServerStarting(event.getServer(), this::serverStarting);
    }

    /**
     * 鐩戝惉鏈嶅姟绔仠姝簨浠讹紝瀵规帴閫氱敤鐨?EventBootstrap锛屽苟纭繚鏈繚瀛樼殑椤圭洰鏁版嵁琚埛鏂板埌纾佺洏銆?
     * @param event 鏈嶅姟绔仠姝簨浠?
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        EventBootstrap.handleServerStopped(event.getServer(), this::serverStopped);
    }

    /**
     * 鏈嶅姟绔惎鍔ㄦ椂鐨勫唴閮ㄥ鐞嗛€昏緫銆?
     * @param server MinecraftServer 瀹炰緥
     */
    private void serverStarting(MinecraftServer server) {
        LOGGER.info("Todo List Mod (Forge): Server starting...");
    }

    /**
     * 鏈嶅姟绔仠姝㈡椂鐨勫唴閮ㄥ鐞嗛€昏緫锛岃礋璐ｆ寔涔呭寲鏁版嵁銆?
     * @param server MinecraftServer 瀹炰緥
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
