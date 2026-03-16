package com.todolist.client;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge 平台客户端主类。
 * 负责客户端初始化、事件监听与 HUD 渲染等处理。
 */
public final class NeoForgeTodoClient {
    private static Minecraft client;
    private static TodoHudRenderer hudRenderer;
    private static final TaskManager teamTaskManager = new TaskManager();
    private static String activeProjectId;
    private static boolean hudVisible = true;
    private static boolean keyKPressed;
    private static boolean keyHPressed;
    private static boolean keyJPressed;
    private static String lastAppliedStorageNamespace = DataPathProvider.LOCAL_STORAGE_NAMESPACE;
    private static boolean pendingRemoteResync;

    /**
     * 私有构造函数，禁止实例化。
     */
    private NeoForgeTodoClient() {
    }

    /**
     * 初始化 NeoForge 客户端逻辑。
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
        registerReflectiveListeners();
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
     * 通过反射注册客户端事件监听器。
     */
    private static void registerReflectiveListeners() {
        Object eventBus = NeoForge.EVENT_BUS;
        registerListener(eventBus, "net.neoforged.neoforge.event.TickEvent$ClientTickEvent", NeoForgeTodoClient::onClientTickEvent);
        registerListener(eventBus, "net.neoforged.neoforge.client.event.RenderGuiEvent$Post", NeoForgeTodoClient::onRenderGuiPostEvent);
        registerListener(eventBus, "net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent$LoggingOut", NeoForgeTodoClient::onClientLoggingOutEvent);
        registerListener(eventBus, "net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent$LoggingIn", NeoForgeTodoClient::onClientLoggingInEvent);
    }

    /**
     * 注册指定事件监听器。
     *
     * @param eventBus 事件总线
     * @param eventClassName 事件类名
     * @param consumer 处理函数
     */
    private static void registerListener(Object eventBus, String eventClassName, java.util.function.Consumer<Object> consumer) {
        try {
            Class<?> eventClass = Class.forName(eventClassName);
            eventBus.getClass()
                    .getMethod("addListener", EventPriority.class, boolean.class, Class.class, java.util.function.Consumer.class)
                    .invoke(eventBus, EventPriority.NORMAL, false, eventClass, consumer);
        } catch (Exception e) {
            TodoListNeoForge.LOGGER.warn("Failed to register NeoForge client listener for {}", eventClassName, e);
        }
    }

    /**
     * 客户端 Tick 事件处理。
     *
     * @param ignored 事件对象
     */
    private static void onClientTickEvent(Object ignored) {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            keyKPressed = false;
            keyHPressed = false;
            keyJPressed = false;
            return;
        }
        if (current.getConnection() == null) {
            pendingRemoteResync = false;
            applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
        } else if (!current.isLocalServer()) {
            applyStorageNamespace(resolveStorageNamespace(current));
        }
        if (pendingRemoteResync &&
                NeoForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID) &&
                NeoForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            NeoForgeClientProjectPackets.sendRequestSyncProjects();
            NeoForgeClientProjectPackets.sendSetHudStarredProjectIds(ModConfig.getInstance().getHudStarredProjectIds());
            NeoForgeClientTaskPackets.requestTeamSync();
            pendingRemoteResync = false;
        }
        if (current.player == null) {
            keyKPressed = false;
            keyHPressed = false;
            keyJPressed = false;
            return;
        }
        long handle = current.getWindow().getWindow();
        boolean nowK = InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_K);
        boolean nowH = InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_H);
        boolean nowJ = InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_J);
        if (nowK && !keyKPressed) {
            openTodoScreen(current);
        }
        if (nowH && !keyHPressed) {
            toggleHud();
        }
        if (nowJ && !keyJPressed) {
            toggleHudVisibility();
        }
        keyKPressed = nowK;
        keyHPressed = nowH;
        keyJPressed = nowJ;
    }

    /**
     * 客户端登出事件处理。
     *
     * @param ignored 事件对象
     */
    private static void onClientLoggingOutEvent(Object ignored) {
        pendingRemoteResync = false;
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
    }

    /**
     * 客户端登录事件处理。
     *
     * @param ignored 事件对象
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
     * 根据服务器信息生成存储命名空间。
     *
     * @param client 客户端实例
     * @return 命名空间
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
     * 应用新的存储命名空间。
     *
     * @param namespace 命名空间
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
            Project p = TodoListNeoForge.getProjectManager().getProject(lastActive);
            if (p != null && p.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                p = null;
            }
            if (p != null) {
                setActiveProjectId(p.getId());
                ClientBridge.syncHudViewForProject(p);
                NeoForgeClientProjectPackets.sendSetActiveProjectId(p.getId());
            }
        }
    }

    /**
     * 渲染 HUD 覆盖层。
     *
     * @param event 事件对象
     */
    private static void onRenderGuiPostEvent(Object event) {
        if (hudRenderer == null) {
            return;
        }
        if (event instanceof RenderGuiEvent.Post postEvent) {
            float partialTick = postEvent.getPartialTick() != null
                    ? postEvent.getPartialTick().getGameTimeDeltaPartialTick(true)
                    : 0.0f;
            hudRenderer.render(postEvent.getGuiGraphics(), partialTick);
        }
    }

    /**
     * 打开任务列表界面。
     *
     * @param current 客户端实例
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
     * 获取团队任务管理器。
     *
     * @return 团队任务管理器
     */
    public static TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    /**
     * 获取当前活动项目 ID。
     *
     * @return 项目 ID
     */
    public static String getActiveProjectId() {
        return activeProjectId;
    }

    /**
     * 设置当前活动项目 ID。
     *
     * @param projectId 项目 ID
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
     * 设置 HUD 可见性。
     *
     * @param visible 是否可见
     */
    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    /**
     * 从服务端更新团队任务列表。
     *
     * @param tasks 任务列表
     */
    public static void updateTeamTasksFromServer(java.util.List<Task> tasks) {
        teamTaskManager.clearAll();
        String lastActive = ModConfig.getInstance().getLastActiveProjectId();
        if (lastActive != null && !lastActive.isBlank()) {
            Project p = TodoListNeoForge.getProjectManager().getProject(lastActive);
            if (p != null && p.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                p = null;
            }
            if (p != null) {
                setActiveProjectId(p.getId());
                ClientBridge.syncHudViewForProject(p);
                NeoForgeClientProjectPackets.sendSetActiveProjectId(p.getId());
            }
        }
        for (Task task : tasks) {
            teamTaskManager.addTask(task);
        }
    }

    /**
     * 判断是否启用团队项目功能。
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
        return NeoForgeNetworkBridge.canSend(ProjectPackets.ADD_PROJECT_ID);
    }
}