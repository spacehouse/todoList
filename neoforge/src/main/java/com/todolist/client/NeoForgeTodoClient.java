package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.TodoListNeoForge;
import com.todolist.config.ModConfig;
import com.todolist.gui.ConfigScreen;
import com.todolist.gui.TodoScreen;
import com.todolist.neoforge.network.NeoForgeNetworkBridge;
import com.todolist.network.ProjectPackets;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge 平台客户端主类，负责客户端初始化、HUD 集成与状态同步。
 */
public final class NeoForgeTodoClient {
    private static Minecraft client;
    private static TodoHudRenderer hudRenderer;
    private static final TaskManager teamTaskManager = new TaskManager();
    private static final KeyMapping OPEN_TODO_KEY = new KeyMapping("key.todolist.open", GLFW.GLFW_KEY_K, "category.todolist");
    private static final KeyMapping TOGGLE_HUD_KEY = new KeyMapping("key.todolist.togglehud", GLFW.GLFW_KEY_H, "category.todolist");
    private static final KeyMapping TOGGLE_HUD_VISIBILITY_KEY = new KeyMapping("key.todolist.togglehudvisibility", GLFW.GLFW_KEY_J, "category.todolist");
    private static String activeProjectId;
    private static boolean hudVisible = true;
    private static String lastAppliedStorageNamespace = DataPathProvider.LOCAL_STORAGE_NAMESPACE;
    private static boolean pendingRemoteResync;
    private static boolean pendingLocalWorldInitialization;
    private static Boolean lastLocalPublishedState;

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private NeoForgeTodoClient() {
    }

    /**
     * 初始化客户端。
     */
    public static void initialize() {
        client = Minecraft.getInstance();
        registerConfigScreenFactory();
        ClientBridge.setOps(new NeoForgeClientBridgeOps());
        try {
            registerHudRenderer();
        } catch (Exception e) {
            TodoListNeoForge.LOGGER.warn("Failed to initialize NeoForge HUD renderer", e);
        }
        NeoForgeClientTaskPackets.registerClientPackets();
        NeoForgeClientProjectPackets.registerClientPackets();
        registerClientListeners();
        TodoListNeoForge.LOGGER.info("Todo List Mod NeoForge client initialized");
    }

    /**
     * 注册配置界面工厂。
     */
    private static void registerConfigScreenFactory() {
        try {
            ModLoadingContext.get().registerExtensionPoint(
                    IConfigScreenFactory.class,
                    () -> (modContainer, parent) -> new ConfigScreen(parent)
            );
        } catch (Exception e) {
            TodoListNeoForge.LOGGER.warn("Failed to register NeoForge config screen factory", e);
        }
    }

    /**
     * 注册客户端事件监听器。
     */
    private static void registerClientListeners() {
        NeoForge.EVENT_BUS.addListener(NeoForgeTodoClient::onClientTickEvent);
        NeoForge.EVENT_BUS.addListener(NeoForgeTodoClient::onRenderGuiPostEvent);
        NeoForge.EVENT_BUS.addListener(NeoForgeTodoClient::onClientLoggingOutEvent);
        NeoForge.EVENT_BUS.addListener(NeoForgeTodoClient::onClientLoggingInEvent);
        IEventBus modEventBus = ModLoadingContext.get().getActiveContainer().getEventBus();
        modEventBus.addListener(NeoForgeTodoClient::onRegisterKeyMappingsEvent);
    }

    /**
     * 注册快捷键。
     *
     * @param event 快捷键注册事件
     */
    private static void onRegisterKeyMappingsEvent(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TODO_KEY);
        event.register(TOGGLE_HUD_KEY);
        event.register(TOGGLE_HUD_VISIBILITY_KEY);
    }

    /**
     * 处理客户端 Tick 事件并维护同步状态。
     *
     * @param ignored Tick 事件
     */
    private static void onClientTickEvent(ClientTickEvent.Post ignored) {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return;
        }
        boolean localIntegrated = isLocalIntegratedServer(current);
        if (current.getConnection() == null) {
            pendingRemoteResync = false;
            applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
        } else if (!localIntegrated) {
            applyStorageNamespace(resolveStorageNamespace(current));
        }
        if (pendingLocalWorldInitialization && current.player != null) {
            if (localIntegrated) {
                initializeLocalWorldState(current);
                pendingLocalWorldInitialization = false;
            } else if (current.getConnection() != null) {
                pendingLocalWorldInitialization = false;
            }
        }
        if (pendingRemoteResync
                && NeoForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID)
                && NeoForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            NeoForgeClientProjectPackets.sendRequestSyncProjects();
            NeoForgeClientProjectPackets.sendSetHudStarredProjectIds(ModConfig.getInstance().getHudStarredProjectIds());
            NeoForgeClientTaskPackets.requestTeamSync();
            pendingRemoteResync = false;
        }
        refreshLocalPublishedState(current);
        if (current.player == null) {
            return;
        }
        while (OPEN_TODO_KEY.consumeClick()) {
            openTodoScreen(current);
        }
        while (TOGGLE_HUD_KEY.consumeClick()) {
            toggleHud();
        }
        while (TOGGLE_HUD_VISIBILITY_KEY.consumeClick()) {
            toggleHudVisibility();
        }
    }

    /**
     * 处理客户端退出事件。
     *
     * @param ignored 退出事件
     */
    private static void onClientLoggingOutEvent(ClientPlayerNetworkEvent.LoggingOut ignored) {
        pendingRemoteResync = false;
        pendingLocalWorldInitialization = false;
        lastLocalPublishedState = null;
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
    }

    /**
     * 处理客户端进入世界事件，并为远程服与本地单人环境安排后续初始化。
     *
     * @param ignored 进入事件
     */
    private static void onClientLoggingInEvent(ClientPlayerNetworkEvent.LoggingIn ignored) {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return;
        }
        pendingLocalWorldInitialization = true;
        if (!isLocalIntegratedServer(current)) {
            if (current.getConnection() != null) {
                applyStorageNamespace(resolveStorageNamespace(current));
            }
            lastLocalPublishedState = null;
        } else {
            lastLocalPublishedState = isLocalPublished(current);
        }
        pendingRemoteResync = true;
    }

    /**
     * 在本地单人世界进入后，统一恢复本地项目、个人任务和团队缓存状态。
     *
     * @param current 当前客户端实例
     */
    private static void initializeLocalWorldState(Minecraft current) {
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
        lastLocalPublishedState = isLocalPublished(current);
        try {
            ClientTaskStorageHelper.restorePublishedPlayerTasksToLocalStorage(TodoListNeoForge.getTaskStorage(), current);
        } catch (Exception e) {
            TodoListNeoForge.LOGGER.warn("Failed to restore published personal tasks back to local storage", e);
        }
        TodoListCommon.reloadProjectsFromStorage();
        setActiveProjectId(null);
        hudVisible = true;
        teamTaskManager.clearAll();
        if (isLocalPublished(current)) {
            try {
                updateTeamTasksFromServer(TodoListNeoForge.getTaskStorage().loadTeamTasks());
            } catch (Exception e) {
                TodoListNeoForge.LOGGER.warn("Failed to load local team tasks during local world initialization", e);
            }
        }
        String lastActive = ModConfig.getInstance().getLastActiveProjectId();
        if (lastActive != null && !lastActive.isBlank()) {
            Project project = TodoListNeoForge.getProjectManager().getProject(lastActive);
            if (project != null && project.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                project = null;
            }
            if (project != null) {
                setActiveProjectId(project.getId());
                ClientBridge.syncHudViewForProject(project);
                NeoForgeClientProjectPackets.sendSetActiveProjectId(project.getId());
            }
        }
    }

    /**
     * 解析当前服务端对应的存储命名空间。
     *
     * @param current 当前客户端实例
     * @return 存储命名空间
     */
    private static String resolveStorageNamespace(Minecraft current) {
        if (current == null || current.isLocalServer()) {
            return DataPathProvider.LOCAL_STORAGE_NAMESPACE;
        }
        ServerData serverData = current.getCurrentServer();
        if (serverData == null || serverData.ip == null || serverData.ip.trim().isEmpty()) {
            return "server_unknown";
        }
        return "server_" + serverData.ip;
    }

    /**
     * 应用新的存储命名空间并重载客户端状态。
     *
     * @param namespace 目标命名空间
     */
    private static void applyStorageNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = DataPathProvider.LOCAL_STORAGE_NAMESPACE;
        }
        if (namespace.equals(lastAppliedStorageNamespace)) {
            return;
        }
        DataPathProvider.setStorageNamespace(namespace);
        lastAppliedStorageNamespace = namespace;
        TodoListCommon.reloadProjectsFromStorage();
        setActiveProjectId(null);
        hudVisible = true;
        teamTaskManager.clearAll();
        String lastActive = ModConfig.getInstance().getLastActiveProjectId();
        if (lastActive != null && !lastActive.isBlank()) {
            Project project = TodoListNeoForge.getProjectManager().getProject(lastActive);
            if (project != null && project.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                project = null;
            }
            if (project != null) {
                setActiveProjectId(project.getId());
                ClientBridge.syncHudViewForProject(project);
                NeoForgeClientProjectPackets.sendSetActiveProjectId(project.getId());
            }
        }
    }

    /**
     * 渲染 HUD 图层。
     *
     * @param event GUI 渲染事件
     */
    private static void onRenderGuiPostEvent(RenderGuiEvent.Post event) {
        if (hudRenderer == null) {
            return;
        }
        float partialTick = event.getPartialTick() != null
                ? event.getPartialTick().getGameTimeDeltaPartialTick(true)
                : 0.0f;
        hudRenderer.render(event.getGuiGraphics(), partialTick);
    }

    /**
     * 打开待办界面。
     *
     * @param current 当前客户端实例
     */
    private static void openTodoScreen(Minecraft current) {
        if (current.screen == null) {
            current.setScreen(new TodoScreen(current.screen));
        }
    }

    /**
     * 注册 HUD 渲染器。
     */
    private static void registerHudRenderer() {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return;
        }
        hudRenderer = new TodoHudRenderer(current);
        ClientPlatformAdapter.setHudRendererSupplier(() -> hudRenderer);
    }

    /**
     * 切换 HUD 展开状态。
     */
    private static void toggleHud() {
        if (hudRenderer != null) {
            hudRenderer.toggleExpanded();
        }
    }

    /**
     * 切换 HUD 可见性。
     */
    private static void toggleHudVisibility() {
        boolean nextVisible = !ClientBridge.ops().isHudVisible();
        ClientBridge.ops().setHudVisible(nextVisible);
    }

    /**
     * 检查本地世界的局域网发布状态变化，并在刚发布时主动拉取一次团队相关同步数据。
     *
     * @param current 当前客户端实例
     */
    private static void refreshLocalPublishedState(Minecraft current) {
        if (!isLocalIntegratedServer(current)) {
            lastLocalPublishedState = null;
            return;
        }
        boolean published = isLocalPublished(current);
        if (lastLocalPublishedState != null && !lastLocalPublishedState && published) {
            syncLocalLanStateAfterPublish();
        } else if (lastLocalPublishedState != null && lastLocalPublishedState && !published) {
            teamTaskManager.clearAll();
        }
        lastLocalPublishedState = published;
    }

    /**
     * 判断当前本地集成服是否已发布局域网。
     *
     * @param current 当前客户端实例
     * @return 已发布时返回 true
     */
    private static boolean isLocalPublished(Minecraft current) {
        if (!isLocalIntegratedServer(current)) {
            return false;
        }
        var server = current.getSingleplayerServer();
        return server != null && server.isPublished();
    }

    /**
     * 本地世界发布局域网后，重新同步项目状态与团队任务。
     */
    private static void syncLocalLanStateAfterPublish() {
        try {
            ClientTaskStorageHelper.migrateLocalTasksToPublishedPlayerStorage(TodoListNeoForge.getTaskStorage(), client);
        } catch (Exception e) {
            TodoListNeoForge.LOGGER.warn("Failed to migrate local personal tasks after publishing local world", e);
        }
        try {
            updateTeamTasksFromServer(TodoListNeoForge.getTaskStorage().loadTeamTasks());
        } catch (Exception e) {
            TodoListNeoForge.LOGGER.warn("Failed to reload local team tasks after publishing local world", e);
        }
        NeoForgeClientProjectPackets.sendRequestSyncProjects();
        NeoForgeClientTaskPackets.requestTeamSync();
    }

    /**
     * 获取团队任务管理器。
     *
     * @return 团队任务管理器
     */
    public static TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    /**
     * 获取当前激活项目 ID。
     *
     * @return 激活项目 ID
     */
    public static String getActiveProjectId() {
        return activeProjectId;
    }

    /**
     * 设置当前激活项目 ID。
     *
     * @param projectId 激活项目 ID
     */
    public static void setActiveProjectId(String projectId) {
        activeProjectId = projectId;
    }

    /**
     * 判断 HUD 是否可见。
     *
     * @return HUD 是否可见
     */
    public static boolean isHudVisible() {
        return hudVisible;
    }

    /**
     * 设置 HUD 可见性。
     *
     * @param visible HUD 是否可见
     */
    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    /**
     * 用服务端同步结果刷新团队任务。
     *
     * @param tasks 团队任务列表
     */
    public static void updateTeamTasksFromServer(java.util.List<Task> tasks) {
        teamTaskManager.clearAll();
        String lastActive = ModConfig.getInstance().getLastActiveProjectId();
        if (lastActive != null && !lastActive.isBlank()) {
            Project project = TodoListNeoForge.getProjectManager().getProject(lastActive);
            if (project != null && project.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                project = null;
            }
            if (project != null) {
                setActiveProjectId(project.getId());
                ClientBridge.syncHudViewForProject(project);
                NeoForgeClientProjectPackets.sendSetActiveProjectId(project.getId());
            }
        }
        for (Task task : tasks) {
            teamTaskManager.addTask(task);
        }
    }

    /**
     * 判断当前环境是否允许使用团队项目。
     *
     * @return 可用时返回 true
     */
    public static boolean isTeamProjectsEnabled() {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return false;
        }
        if (isLocalIntegratedServer(current)) {
            var server = current.getSingleplayerServer();
            return server != null && server.isPublished();
        }
        return NeoForgeNetworkBridge.canSend(ProjectPackets.ADD_PROJECT_ID);
    }

    /**
     * 判断当前客户端是否处于本地集成服上下文。
     *
     * @param current 当前客户端实例
     * @return 属于本地单人或本地主机上下文时返回 true
     */
    private static boolean isLocalIntegratedServer(Minecraft current) {
        return current != null && (current.isLocalServer() || current.getSingleplayerServer() != null);
    }
}
