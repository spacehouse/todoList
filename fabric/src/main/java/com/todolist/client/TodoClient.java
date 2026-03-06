package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.config.ModConfig;
import com.todolist.gui.TodoScreen;
import com.todolist.network.ProjectPackets;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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
    private static KeyBinding openTodoKeyBinding;
    private static KeyBinding toggleHudKeyBinding;
    private static MinecraftClient client;
    private static TodoHudRenderer hudRenderer;
    private static final TaskManager teamTaskManager = new TaskManager();
    private static String activeProjectId;

    /**
     * Fabric 客户端入口点。
     * 负责初始化按键绑定、HUD（可选）以及客户端网络包接收器。
     */
    @Override
    public void onInitializeClient() {
        TodoListMod.LOGGER.info("Initializing Todo List Mod client...");

        client = MinecraftClient.getInstance();
        ClientBridge.setOps(new FabricClientBridgeOps());

        // Register key bindings
        registerKeyBindings();

        // Register HUD renderer (Phase 2 feature)
        try {
            if (ModConfig.getInstance().isEnableHud()) {
                registerHudRenderer();
            }
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
        openTodoKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.todolist.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.todolist"
        ));

        // Key: H key to toggle HUD expanded state
        toggleHudKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.todolist.togglehud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.todolist"
        ));

        // Register key press handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openTodoKeyBinding.wasPressed()) {
                openTodoScreen();
            }
            while (toggleHudKeyBinding.wasPressed()) {
                toggleHud();
            }
        });

        TodoListMod.LOGGER.info("Registered key bindings: K key (open), H key (toggle HUD)");
    }

    /**
     * 注册 HUD 渲染回调并初始化 HUD 渲染器实例。
     */
    private void registerHudRenderer() {
        // Initialize HUD renderer
        hudRenderer = new TodoHudRenderer(client);
        ClientPlatformAdapter.setHudRendererSupplier(() -> hudRenderer);

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (ModConfig.getInstance().isEnableHud() && client.player != null) {
                hudRenderer.render(drawContext, tickDelta);
            }
        });

        TodoListMod.LOGGER.info("Registered HUD renderer");
    }

    /**
     * 注册客户端进入世界事件，用于触发后续同步逻辑。
     */
    private void registerJoinEvent() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TodoListMod.LOGGER.info("Joined server, requesting task sync...");
            // Request task sync from server (to be implemented in Phase 3)
        });
    }

    /**
     * 打开主 Todo 界面。
     */
    private void openTodoScreen() {
        if (client.currentScreen == null) {
            client.setScreen(new TodoScreen(client.currentScreen));
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
    public static KeyBinding getOpenTodoKeyBinding() {
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
        MinecraftClient c = client != null ? client : MinecraftClient.getInstance();
        if (c == null) return false;
        if (c.isInSingleplayer()) return false;
        return ClientPlayNetworking.canSend(ProjectPackets.ADD_PROJECT_ID);
    }
}
