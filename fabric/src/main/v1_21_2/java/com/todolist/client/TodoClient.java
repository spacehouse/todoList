package com.todolist.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.todolist.TodoListCommon;
import com.todolist.TodoListMod;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.config.ModConfig;
import com.todolist.gui.TodoScreen;
import com.todolist.project.Project;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side initialization for Todo List Mod
 *
 * Features:
 * - Key binding registration
 * - HUD rendering
 * - Network event handling
 */
public class TodoClient implements ClientModInitializer {
    private static KeyMapping openTodoKeyBinding;
    private static KeyMapping toggleHudKeyBinding;
    private static KeyMapping toggleHudVisibilityKeyBinding;
    private static Minecraft client;
    private static TodoHudRenderer hudRenderer;
    private static final TaskManager teamTaskManager = new TaskManager();
    private static String activeProjectId;
    private static boolean hudVisible = true;
    private static boolean lastConnectionWasRemote;
    private static Boolean lastLocalPublishedState;

    /**
     * Fabric 客户端入口点。
     * 负责初始化按键绑定、HUD（可选）以及客户端网络包接收器。
     */
    @Override
    public void onInitializeClient() {
        TodoListMod.LOGGER.info("Initializing Todo List Mod client...");

        client = Minecraft.getInstance();
        ClientBridge.setOps(new FabricClientBridgeOps());

        // Register key bindings
        registerKeyBindings();

        // Register HUD renderer (Phase 2 feature)
        try {
            registerHudRenderer();
        } catch (Exception e) {
            TodoListMod.LOGGER.warn("Failed to initialize HUD renderer", e);
        }

        ClientTaskPackets.registerClientPackets();
        ClientProjectPackets.registerClientPackets();

        // Register join event
        registerJoinEvent();

        TodoListMod.LOGGER.info("Todo List Mod client initialized!");
    }

    /**
     * 注册客户端按键绑定以及按键触发逻辑。
     */
    private void registerKeyBindings() {
        // Key: K key to open todo list
        openTodoKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.todolist.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.todolist"
        ));

        // Key: H key to toggle HUD expanded state
        toggleHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.todolist.togglehud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.todolist"
        ));

        toggleHudVisibilityKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.todolist.togglehudvisibility",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.todolist"
        ));

        // Register key press handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            refreshLocalPublishedState(client);
            while (openTodoKeyBinding.consumeClick()) {
                openTodoScreen();
            }
            while (toggleHudKeyBinding.consumeClick()) {
                toggleHud();
            }
            while (toggleHudVisibilityKeyBinding.consumeClick()) {
                toggleHudVisibility();
            }
        });

        TodoListMod.LOGGER.info("Registered key bindings: K key (open), H key (toggle HUD expanded), J key (toggle HUD visibility)");
    }

    /**
     * 注册 HUD 渲染回调并初始化 HUD 渲染器实例。
     */
    private void registerHudRenderer() {
        // Initialize HUD renderer
        hudRenderer = new TodoHudRenderer(client);
        ClientPlatformAdapter.setHudRendererSupplier(() -> hudRenderer);

        // v1_21_2 覆盖：HudRenderCallback 仍存在，但回调第二参数由 float 改为 DeltaTracker
        HudRenderCallback.EVENT.register((drawContext, tickTracker) -> {
            if (client.player != null) {
                hudRenderer.render(drawContext, 0.0F);
            }
        });

        TodoListMod.LOGGER.info("Registered HUD renderer");
    }

    /**
     * 注册客户端进入世界事件，用于触发后续同步逻辑。
     */
    private void registerJoinEvent() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            boolean localServer = client.isLocalServer();
            applyStorageNamespace(localServer, client);
            lastLocalPublishedState = localServer ? isLocalPublished(client) : null;
            client.execute(() -> {
                if (localServer) {
                    try {
                        ClientTaskStorageHelper.restorePublishedPlayerTasksToLocalStorage(TodoListMod.getTaskStorage(), client);
                    } catch (Exception e) {
                        TodoListMod.LOGGER.warn("Failed to restore published personal tasks back to local storage", e);
                    }
                }
                TodoListCommon.reloadProjectsFromStorage();
                setActiveProjectId(null);
                teamTaskManager.clearAll();
                hudVisible = true;
                String lastActive = ModConfig.getInstance().getLastActiveProjectId();
                if (lastActive != null && !lastActive.isBlank()) {
                    Project p = TodoListMod.getProjectManager().getProject(lastActive);
                    if (p != null && p.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                        p = null;
                    }
                    if (p != null) {
                        setActiveProjectId(p.getId());
                        ClientBridge.syncHudViewForProject(p);
                        ClientProjectPackets.sendSetActiveProjectId(p.getId());
                    }
                }
                if (!localServer) {
                    ClientProjectPackets.sendRequestSyncProjects();
                    ClientTaskPackets.requestTeamSync();
                }
            });
            lastConnectionWasRemote = !localServer;
            TodoListMod.LOGGER.info("Joined server, requesting task sync...");
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            boolean wasRemote = lastConnectionWasRemote;
            lastConnectionWasRemote = false;
            lastLocalPublishedState = null;
            if (!wasRemote) {
                return;
            }
            client.execute(() -> {
                TodoListCommon.closeStorageContext();
                DataPathProvider.resetStorageNamespace();
                TodoListCommon.reloadProjectsFromStorage();
                setActiveProjectId(null);
                hudVisible = true;
                String lastActive = ModConfig.getInstance().getLastActiveProjectId();
                if (lastActive != null && !lastActive.isBlank()) {
                    Project p = TodoListMod.getProjectManager().getProject(lastActive);
                    if (p != null && p.getScope() == Project.Scope.TEAM && !isTeamProjectsEnabled()) {
                        p = null;
                    }
                    if (p != null) {
                        setActiveProjectId(p.getId());
                        ClientBridge.syncHudViewForProject(p);
                        ClientProjectPackets.sendSetActiveProjectId(p.getId());
                    }
                }
                teamTaskManager.clearAll();
            });
        });
    }

    /**
     * 根据联机上下文切换存储域：单人使用 local，多人使用 server_<address>。
     */
    private void applyStorageNamespace(boolean localServer, Minecraft client) {
        if (localServer) {
            DataPathProvider.resetStorageNamespace();
            return;
        }
        ServerData serverData = client.getCurrentServer();
        String serverKey = serverData == null ? "server_unknown" : ("server_" + serverData.ip);
        DataPathProvider.setStorageNamespace(serverKey);
    }

    /**
     * 打开主 Todo 界面。
     */
    private void openTodoScreen() {
        if (client.screen == null) {
            client.setScreen(new TodoScreen(client.screen));
        }
    }

    /**
     * 切换 HUD 展开状态。
     */
    private void toggleHud() {
        if (hudRenderer != null) {
            hudRenderer.toggleExpanded();
        }
    }

    private void toggleHudVisibility() {
        boolean nextVisible = !ClientBridge.ops().isHudVisible();
        ClientBridge.ops().setHudVisible(nextVisible);
    }

    /**
     * 检查本地世界的局域网发布状态变化，并在刚发布时主动拉取最新团队状态。
     *
     * @param current 当前客户端实例
     */
    private static void refreshLocalPublishedState(Minecraft current) {
        if (current == null || !current.isLocalServer()) {
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
        if (current == null || !current.isLocalServer()) {
            return false;
        }
        var server = current.getSingleplayerServer();
        return server != null && server.isPublished();
    }

    /**
     * 本地世界发布局域网后，重新拉取项目状态和团队任务，恢复单机阶段被临时净化的团队视图。
     */
    private static void syncLocalLanStateAfterPublish() {
        try {
            ClientTaskStorageHelper.migrateLocalTasksToPublishedPlayerStorage(TodoListMod.getTaskStorage(), client);
        } catch (Exception e) {
            TodoListMod.LOGGER.warn("Failed to migrate local personal tasks after publishing local world", e);
        }
        try {
            updateTeamTasksFromServer(TodoListMod.getTaskStorage().loadTeamTasks());
        } catch (Exception e) {
            TodoListMod.LOGGER.warn("Failed to reload local team tasks after publishing local world", e);
        }
        ClientProjectPackets.sendRequestSyncProjects();
        ClientTaskPackets.requestTeamSync();
    }

    /**
     * 获取客户端侧团队任务管理器实例。
     *
     * @return 团队任务管理器
     */
    public static TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    /**
     * 获取当前激活的项目 ID。
     *
     * @return 项目 ID，可能为 null
     */
    public static String getActiveProjectId() {
        return activeProjectId;
    }

    /**
     * 设置当前激活的项目 ID。
     *
     * @param projectId 项目 ID
     */
    public static void setActiveProjectId(String projectId) {
        activeProjectId = projectId;
    }
    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    /**
     * 用来自服务器的任务列表刷新本地团队任务缓存。
     *
     * @param tasks 服务器下发的团队任务列表
     */
    public static void updateTeamTasksFromServer(java.util.List<Task> tasks) {
        teamTaskManager.clearAll();
        for (Task task : tasks) {
            teamTaskManager.addTask(task);
        }
    }

    /**
     * 获取打开 Todo 界面的按键绑定。
     *
     * @return 按键绑定实例
     */
    public static KeyMapping getOpenTodoKeyBinding() {
        return openTodoKeyBinding;
    }

    /**
     * 获取 HUD 渲染器实例。
     *
     * @return HUD 渲染器，可能为 null
     */
    public static TodoHudRenderer getHudRenderer() {
        return hudRenderer;
    }

    /**
     * 判断是否处于可用的团队项目环境（通常为联机并且服务端支持对应数据包）。
     *
     * @return 是否启用团队项目能力
     */
    public static boolean isTeamProjectsEnabled() {
        Minecraft c = client != null ? client : Minecraft.getInstance();
        if (c == null) return false;
        if (c.isLocalServer()) {
            var server = c.getSingleplayerServer();
            return server != null && server.isPublished();
        }
        return ClientPlayNetworking.canSend(com.todolist.network.FabricProjectPayload.TYPE);
    }
}
