package com.todolist.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.todolist.TodoListCommon;
import com.todolist.TodoListForge;
import com.todolist.config.ModConfig;
import com.todolist.forge.network.ForgeNetworkBridge;
import com.todolist.gui.ConfigScreen;
import com.todolist.gui.TodoScreen;
import com.todolist.network.ProjectPackets;
import com.todolist.network.TaskPackets;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;

public final class ForgeTodoClient {
    private static Minecraft client;
    private static TodoHudRenderer hudRenderer;
    private static final TaskManager teamTaskManager = new TaskManager();
    private static String activeProjectId;
    private static boolean keyKPressed;
    private static boolean keyHPressed;
    private static boolean lastConnectionWasRemote;
    private static String lastAppliedStorageNamespace = DataPathProvider.LOCAL_STORAGE_NAMESPACE;
    private static boolean pendingRemoteResync;

    private ForgeTodoClient() {
    }

    public static void initialize() {
        client = Minecraft.getInstance();
        registerConfigScreenFactory();
        ClientBridge.setOps(new ForgeClientBridgeOps());
        try {
            if (ModConfig.getInstance().isEnableHud()) {
                registerHudRenderer();
            }
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
            keyKPressed = false;
            keyHPressed = false;
            return;
        }

        ServerData serverData = current.getCurrentServer();
        boolean remoteServer = serverData != null;
        boolean localServer = !remoteServer;
        if (remoteServer && !lastConnectionWasRemote) {
            pendingRemoteResync = true;
        }
        if (!remoteServer) {
            pendingRemoteResync = false;
        }
        String targetNamespace = resolveStorageNamespace(current, localServer);
        if (!targetNamespace.equals(lastAppliedStorageNamespace)) {
            DataPathProvider.setStorageNamespace(targetNamespace);
            lastAppliedStorageNamespace = targetNamespace;
            TodoListCommon.reloadProjectsFromStorage();
            setActiveProjectId(null);
            teamTaskManager.clearAll();
        }
        if (remoteServer && pendingRemoteResync &&
                ForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID) &&
                ForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            ForgeClientProjectPackets.sendRequestSyncProjects();
            ForgeClientTaskPackets.requestTeamSync();
            pendingRemoteResync = false;
        }
        lastConnectionWasRemote = remoteServer;
        if (current.player == null) {
            keyKPressed = false;
            keyHPressed = false;
            return;
        }
        long handle = current.getWindow().getWindow();
        boolean nowK = InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_K);
        boolean nowH = InputConstants.isKeyDown(handle, GLFW.GLFW_KEY_H);
        if (nowK && !keyKPressed) {
            openTodoScreen(current);
        }
        if (nowH && !keyHPressed) {
            toggleHud();
        }
        keyKPressed = nowK;
        keyHPressed = nowH;
    }

    /**
     * 解析当前联机上下文对应的存储域：单人固定 local，多人为 server_<address>。
     */
    public static String resolveStorageNamespace(Minecraft client, boolean localServer) {
        if (localServer) {
            return DataPathProvider.LOCAL_STORAGE_NAMESPACE;
        }
        ServerData serverData = client.getCurrentServer();
        if (serverData == null || serverData.ip == null || serverData.ip.trim().isEmpty()) {
            return "server_unknown";
        }
        return "server_" + serverData.ip;
    }

    private static void onGuiOverlayPostEvent(Object event) {
        if (hudRenderer == null || !ModConfig.getInstance().isEnableHud()) {
            return;
        }
        try {
            GuiGraphics context;
            Object contextObj = invokeNoArg(event, "getGuiGraphics");
            if (contextObj instanceof GuiGraphics direct) {
                context = direct;
            } else {
                contextObj = invokeNoArg(event, "getDrawContext");
                if (!(contextObj instanceof GuiGraphics fallback)) {
                    return;
                }
                context = fallback;
            }
            float tickDelta = 0.0f;
            Object delta = invokeNoArg(event, "getPartialTick");
            if (delta instanceof Number number) {
                tickDelta = number.floatValue();
            }
            hudRenderer.render(context, tickDelta);
        } catch (Exception e) {
            TodoListForge.LOGGER.debug("Forge HUD render hook ignored: {}", e.getMessage());
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
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

    public static TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    public static String getActiveProjectId() {
        return activeProjectId;
    }

    public static void setActiveProjectId(String projectId) {
        activeProjectId = projectId;
    }

    public static void updateTeamTasksFromServer(java.util.List<Task> tasks) {
        teamTaskManager.clearAll();
        for (Task task : tasks) {
            teamTaskManager.addTask(task);
        }
    }

    public static boolean isTeamProjectsEnabled() {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return false;
        }
        // 如果能发包（服务端安装了模组），则启用团队项目功能，即使是 localServer
        if (ForgeNetworkBridge.canSend(ProjectPackets.ADD_PROJECT_ID)) {
            return true;
        }
        // 服务端没装模组，禁用团队项目
        return false;
    }
}
