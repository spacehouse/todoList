package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.TodoListForge;
import com.todolist.config.ModConfig;
import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.gui.ConfigScreen;
import com.todolist.gui.TodoScreen;
import com.todolist.network.ProjectPackets;
import com.todolist.network.TaskPackets;
import com.todolist.project.Project;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 平台客户端主类。
 * 负责客户端初始化、事件监听、快捷键处理、HUD 渲染集成等。
 */
public final class ForgeTodoClient {
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

    private ForgeTodoClient() {
    }

    /**
     * 初始化客户端。
     * 注册配置屏幕、HUD、网络包、事件监听器等。
     */
    public static void initialize() {
        client = Minecraft.getInstance();
        registerConfigScreenFactory();
        ClientBridge.setOps(new ForgeClientBridgeOps());
        try {
            registerHudRenderer();
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to initialize Forge HUD renderer", e);
        }
        ForgeClientTaskPackets.registerClientPackets();
        ForgeClientProjectPackets.registerClientPackets();
        registerReflectiveListeners();
        TodoListForge.LOGGER.info("Todo List Mod Forge client initialized");
    }

    private static void registerConfigScreenFactory() {
        try {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent))
            );
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to register Forge config screen factory", e);
        }
    }

    private static void registerReflectiveListeners() {
        Object eventBus = MinecraftForge.EVENT_BUS;
        registerListener(eventBus, "net.minecraftforge.event.TickEvent$ClientTickEvent", ForgeTodoClient::onClientTickEvent);
        registerListener(eventBus, "net.minecraftforge.client.event.RenderGuiOverlayEvent$Post", ForgeTodoClient::onGuiOverlayPostEvent);
        registerListener(eventBus, "net.minecraftforge.client.event.ClientPlayerNetworkEvent$LoggingOut", ForgeTodoClient::onClientLoggingOutEvent);
        registerListener(eventBus, "net.minecraftforge.client.event.ClientPlayerNetworkEvent$LoggingIn", ForgeTodoClient::onClientLoggingInEvent);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeTodoClient::onRegisterKeyMappingsEvent);
    }

    private static void registerListener(Object eventBus, String eventClassName, java.util.function.Consumer<Object> consumer) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            eventBus.getClass()
                    .getMethod("addListener", EventPriority.class, boolean.class, Class.class, java.util.function.Consumer.class)
                    .invoke(eventBus, EventPriority.NORMAL, false, eventClass, consumer);
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to register Forge client listener for {}", eventClassName, e);
        }
    }

    private static void onClientTickEvent(Object ignored) {
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
        if (pendingRemoteResync &&
                ForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID) &&
                ForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            ForgeClientProjectPackets.sendRequestSyncProjects();
            ForgeClientTaskPackets.requestTeamSync();
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

    private static void onRegisterKeyMappingsEvent(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TODO_KEY);
        event.register(TOGGLE_HUD_KEY);
        event.register(TOGGLE_HUD_VISIBILITY_KEY);
    }

    private static void onClientLoggingOutEvent(Object ignored) {
        pendingRemoteResync = false;
        pendingLocalWorldInitialization = false;
        lastLocalPublishedState = null;
        TodoListCommon.closeStorageContext();
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
    }

    /**
     * 处理客户端登入事件，分别为远程联机与本地单人世界安排后续初始化逻辑。
     *
     * @param ignored Forge 客户端登入事件对象
     */
    private static void onClientLoggingInEvent(Object ignored) {
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
     * 在 Forge 本地单人世界进入后，统一恢复本地项目、个人任务和团队缓存状态。
     *
     * @param current 当前客户端实例
     */
    private static void initializeLocalWorldState(Minecraft current) {
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
        lastLocalPublishedState = isLocalPublished(current);
        try {
            ClientTaskStorageHelper.restorePublishedPlayerTasksToLocalStorage(TodoListForge.getTaskStorage(), current);
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to restore published personal tasks back to local storage", e);
        }
        TodoListCommon.reloadProjectsFromStorage();
        setActiveProjectId(null);
        hudVisible = true;
        teamTaskManager.clearAll();
        if (isLocalPublished(current)) {
            try {
                updateTeamTasksFromServer(TodoListForge.getTaskStorage().loadTeamTasks());
            } catch (Exception e) {
                TodoListForge.LOGGER.warn("Failed to load local team tasks during local world initialization", e);
            }
        }
        String lastActive = ModConfig.getInstance().getLastActiveProjectId();
        if (lastActive != null && !lastActive.isBlank()) {
            Project p = TodoListForge.getProjectManager().getProject(lastActive);
            if (p != null && p.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                p = null;
            }
            if (p != null) {
                setActiveProjectId(p.getId());
                ForgeClientProjectPackets.sendSetActiveProjectId(p.getId());
            }
        }
    }

    private static String resolveStorageNamespace(Minecraft client) {
        if (client == null || client.isLocalServer()) {
            return DataPathProvider.LOCAL_STORAGE_NAMESPACE;
        }
        ServerData serverData = client.getCurrentServer();
        if (serverData == null || serverData.ip == null || serverData.ip.trim().isEmpty()) {
            return "server_unknown";
        }
        return "server_" + serverData.ip;
    }

    private static void applyStorageNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = DataPathProvider.LOCAL_STORAGE_NAMESPACE;
        }
        if (namespace.equals(lastAppliedStorageNamespace)) {
            return;
        }
        TodoListCommon.closeStorageContext();
        DataPathProvider.setStorageNamespace(namespace);
        lastAppliedStorageNamespace = namespace;
        TodoListCommon.reloadProjectsFromStorage();
        setActiveProjectId(null);
        hudVisible = true;
        teamTaskManager.clearAll();
        String lastActive = ModConfig.getInstance().getLastActiveProjectId();
        if (lastActive != null && !lastActive.isBlank()) {
            Project p = TodoListForge.getProjectManager().getProject(lastActive);
            if (p != null && p.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                p = null;
            }
            if (p != null) {
                setActiveProjectId(p.getId());
                ForgeClientProjectPackets.sendSetActiveProjectId(p.getId());
            }
        }
    }

    private static void onGuiOverlayPostEvent(Object event) {
        if (hudRenderer == null) {
            return;
        }
        if (event instanceof net.minecraftforge.client.event.RenderGuiOverlayEvent.Post postEvent) {
            if (!postEvent.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) {
                return;
            }
            hudRenderer.render(postEvent.getGuiGraphics(), postEvent.getPartialTick());
            TodoToastRenderer.render(postEvent.getGuiGraphics(), Minecraft.getInstance().font,
                    postEvent.getGuiGraphics().guiWidth());
        }
    }

    private static void openTodoScreen(Minecraft current) {
        if (current.screen == null) {
            current.setScreen(new TodoScreen(current.screen));
        }
    }

    private static void registerHudRenderer() {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return;
        }
        hudRenderer = new TodoHudRenderer(current);
        ClientPlatformAdapter.setHudRendererSupplier(() -> hudRenderer);
    }

    private static void toggleHud() {
        if (hudRenderer != null) {
            hudRenderer.toggleExpanded();
        }
    }

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
     * @return 已发布局域网时返回 true
     */
    private static boolean isLocalPublished(Minecraft current) {
        if (!isLocalIntegratedServer(current)) {
            return false;
        }
        var server = current.getSingleplayerServer();
        return server != null && server.isPublished();
    }

    /**
     * 本地世界发布局域网后，重新同步项目状态与团队任务，恢复单机阶段被临时净化的客户端视图。
     */
    private static void syncLocalLanStateAfterPublish() {
        try {
            ClientTaskStorageHelper.migrateLocalTasksToPublishedPlayerStorage(TodoListForge.getTaskStorage(), client);
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to migrate local personal tasks after publishing local world", e);
        }
        try {
            updateTeamTasksFromServer(TodoListForge.getTaskStorage().loadTeamTasks());
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to reload local team tasks after publishing local world", e);
        }
        ForgeClientProjectPackets.sendRequestSyncProjects();
        ForgeClientTaskPackets.requestTeamSync();
    }

    public static TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    public static String getActiveProjectId() {
        return activeProjectId;
    }

    public static void setActiveProjectId(String projectId) {
        activeProjectId = projectId;
    }
    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    public static void updateTeamTasksFromServer(java.util.List<Task> tasks) {
        teamTaskManager.clearAll();
        for (Task task : tasks) {
            teamTaskManager.addTask(task);
        }
    }

    /**
     * 判断当前环境是否允许使用团队项目能力。
     * 本地集成服仅在已发布局域网后启用，远程服按网络能力判定。
     */
    public static boolean isTeamProjectsEnabled() {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) return false;
        if (isLocalIntegratedServer(current)) {
            var server = current.getSingleplayerServer();
            return server != null && server.isPublished();
        }
        return ForgeNetworkBridge.canSend(ProjectPackets.ADD_PROJECT_ID);
    }

    /**
     * 判断当前客户端是否处于本地集成服上下文，兼容 Forge 某些阶段 isLocalServer 尚未稳定的问题。
     *
     * @param current 当前客户端实例
     * @return true 表示当前属于本地单人或本地主机上下文
     */
    private static boolean isLocalIntegratedServer(Minecraft current) {
        return current != null && (current.isLocalServer() || current.getSingleplayerServer() != null);
    }
}
