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
        if (current.getConnection() == null) {
            pendingRemoteResync = false;
            applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
        } else if (!current.isLocalServer()) {
            applyStorageNamespace(resolveStorageNamespace(current));
        }
        if (pendingRemoteResync &&
                ForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID) &&
                ForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            ForgeClientProjectPackets.sendRequestSyncProjects();
            ForgeClientProjectPackets.sendSetHudStarredProjectIds(ModConfig.getInstance().getHudStarredProjectIds());
            ForgeClientTaskPackets.requestTeamSync();
            pendingRemoteResync = false;
        }
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
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
    }

    private static void onClientLoggingInEvent(Object ignored) {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null || current.getConnection() == null) {
            return;
        }
        if (!current.isLocalServer()) {
            applyStorageNamespace(resolveStorageNamespace(current));
        }
        pendingRemoteResync = true;
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
        if (current.isLocalServer()) {
            var server = current.getSingleplayerServer();
            return server != null && server.isPublished();
        }
        return ForgeNetworkBridge.canSend(ProjectPackets.ADD_PROJECT_ID);
    }
}
