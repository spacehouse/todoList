package com.todolist.client;

import com.todolist.TodoConstants;
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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 平台客户端主类。
 * 负责客户端初始化、事件监听、快捷键处理与 HUD 渲染。
 */
public final class ForgeTodoClient {
    private static final ResourceLocation HUD_OVERLAY_ID =
            ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "todo_hud");
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

    /**
     * 私有构造函数，避免外部实例化。
     */
    private ForgeTodoClient() {
    }

    /**
     * 初始化客户端。
     * 注册配置屏幕、HUD、网络包与事件监听器。
     */
    public static void initialize() {
        client = Minecraft.getInstance();
        registerConfigScreenFactory();
        ClientBridge.setOps(new ForgeClientBridgeOps());
        try {
            registerHudRenderer();
            registerHudOverlayLayer();
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to initialize Forge HUD renderer", e);
        }
        ForgeClientTaskPackets.registerClientPackets();
        ForgeClientProjectPackets.registerClientPackets();
        registerReflectiveListeners();
        TodoListForge.LOGGER.info("Todo List Mod Forge client initialized");
    }

    /**
     * 注册配置界面工厂。
     */
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

    /**
     * 注册 HUD 图层到新的叠加层系统。
     */
    private static void registerHudOverlayLayer() {
        try {
            FMLJavaModLoadingContext.get().getModEventBus()
                    .addListener(ForgeTodoClient::onAddGuiOverlayLayersEvent);
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to register Forge HUD overlay layer", e);
        }
    }

    /**
     * 处理叠加层注册事件，将 HUD 插入到热键栏层之上。
     *
     * @param event 叠加层注册事件
     */
    private static void onAddGuiOverlayLayersEvent(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(ForgeLayeredDraw.HOTBAR, HUD_OVERLAY_ID, ForgeTodoClient::renderHudLayer);
    }

    /**
     * 渲染 HUD 图层。
     *
     * @param guiGraphics GUI 绘制上下文
     * @param deltaTracker 帧间隔计算器
     */
    private static void renderHudLayer(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (hudRenderer == null) {
            return;
        }
        float partialTick = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(true) : 0.0f;
        hudRenderer.render(guiGraphics, partialTick);
    }

    /**
     * 通过反射注册客户端事件监听器，降低 API 变动的影响。
     */
    private static void registerReflectiveListeners() {
        Object eventBus = MinecraftForge.EVENT_BUS;
        registerListener(eventBus, "net.minecraftforge.event.TickEvent$ClientTickEvent", ForgeTodoClient::onClientTickEvent);
        registerListener(eventBus, "net.minecraftforge.client.event.ClientPlayerNetworkEvent$LoggingOut", ForgeTodoClient::onClientLoggingOutEvent);
        registerListener(eventBus, "net.minecraftforge.client.event.ClientPlayerNetworkEvent$LoggingIn", ForgeTodoClient::onClientLoggingInEvent);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeTodoClient::onRegisterKeyMappingsEvent);
    }

    /**
     * 使用反射向事件总线注册监听器。
     *
     * @param eventBus 事件总线实例
     * @param eventClassName 事件类名
     * @param consumer 事件处理器
     */
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

    /**
     * 处理客户端 Tick 事件，响应快捷键并维护状态同步。
     */
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

    /**
     * 处理客户端退出事件，重置远端同步标记。
     */
    private static void onClientLoggingOutEvent(Object ignored) {
        pendingRemoteResync = false;
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
    }

    /**
     * 处理客户端登录事件，准备远端同步。
     */
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

    /**
     * 解析当前服务器的存储命名空间。
     *
     * @param client Minecraft 客户端实例
     * @return 存储命名空间
     */
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

    /**
     * 应用新的存储命名空间并同步 HUD 状态。
     *
     * @param namespace 存储命名空间
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

    /**
     * 打开 Todo 列表界面。
     *
     * @param current 当前 Minecraft 客户端
     */
    private static void openTodoScreen(Minecraft current) {
        if (current.screen == null) {
            current.setScreen(new TodoScreen(current.screen));
        }
    }

    /**
     * 初始化 HUD 渲染器并注册到平台适配层。
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
     * 切换 HUD 展开/收起状态。
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
     * @return 是否可见
     */
    public static boolean isHudVisible() {
        return hudVisible;
    }

    /**
     * 设置 HUD 可见状态。
     *
     * @param visible 是否可见
     */
    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    /**
     * 从服务端更新团队任务列表。
     *
     * @param tasks 团队任务列表
     */
    public static void updateTeamTasksFromServer(java.util.List<Task> tasks) {
        teamTaskManager.clearAll();
        for (Task task : tasks) {
            teamTaskManager.addTask(task);
        }
    }

    /**
     * 判断团队项目功能是否可用。
     *
     * @return 是否启用
     */
    public static boolean isTeamProjectsEnabled() {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return false;
        }
        if (current.isLocalServer()) {
            var server = current.getSingleplayerServer();
            if (server != null && server.getPlayerList() != null && server.getPlayerList().getPlayerCount() == 1) {
                return false;
            }
        }
        return ForgeNetworkBridge.canSend(ProjectPackets.ADD_PROJECT_ID);
    }
}