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
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 骞冲彴瀹㈡埛绔富绫汇€?
 * 璐熻矗瀹㈡埛绔垵濮嬪寲銆佷簨浠剁洃鍚€佸揩鎹烽敭澶勭悊涓?HUD 娓叉煋銆?
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
     * 绉佹湁鏋勯€犲嚱鏁帮紝閬垮厤澶栭儴瀹炰緥鍖栥€?
     */
    private ForgeTodoClient() {
    }

    /**
     * 鍒濆鍖栧鎴风銆?
     * 娉ㄥ唽閰嶇疆灞忓箷銆丠UD銆佺綉缁滃寘涓庝簨浠剁洃鍚櫒銆?
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
        registerClientListeners();
        TodoListForge.LOGGER.info("Todo List Mod Forge client initialized");
    }

    /**
     * 娉ㄥ唽閰嶇疆鐣岄潰宸ュ巶銆?
     */
    @SuppressWarnings("removal")
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
     * 娉ㄥ唽 HUD 鍥惧眰鍒版柊鐨勫彔鍔犲眰绯荤粺銆?
     */
    @SuppressWarnings("removal")
    private static void registerHudOverlayLayer() {
        try {
            FMLJavaModLoadingContext.get().getModEventBus()
                    .addListener(ForgeTodoClient::onAddGuiOverlayLayersEvent);
        } catch (Exception e) {
            TodoListForge.LOGGER.warn("Failed to register Forge HUD overlay layer", e);
        }
    }

    /**
     * 澶勭悊鍙犲姞灞傛敞鍐屼簨浠讹紝灏?HUD 鎻掑叆鍒扮儹閿爮灞備箣涓娿€?
     *
     * @param event 鍙犲姞灞傛敞鍐屼簨浠?
     */
    private static void onAddGuiOverlayLayersEvent(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().addAbove(
                ForgeLayeredDraw.PRE_SLEEP_STACK,
                HUD_OVERLAY_ID,
                ForgeLayeredDraw.HOTBAR,
                ForgeTodoClient::renderHudLayer
        );
    }

    /**
     * 娓叉煋 HUD 鍥惧眰銆?
     *
     * @param guiGraphics GUI 缁樺埗涓婁笅鏂?
     * @param deltaTracker 甯ч棿闅旇绠楀櫒
     */
    private static void renderHudLayer(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (hudRenderer == null) {
            return;
        }
        float partialTick = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(true) : 0.0f;
        hudRenderer.render(guiGraphics, partialTick);
    }

    /**
     * 閫氳繃鍙嶅皠娉ㄥ唽瀹㈡埛绔簨浠剁洃鍚櫒锛岄檷浣?API 鍙樺姩鐨勫奖鍝嶃€?
     */
    @SuppressWarnings("removal")
    private static void registerClientListeners() {
        MinecraftForge.EVENT_BUS.addListener(ForgeTodoClient::onClientTickEvent);
        MinecraftForge.EVENT_BUS.addListener(ForgeTodoClient::onClientLoggingOutEvent);
        MinecraftForge.EVENT_BUS.addListener(ForgeTodoClient::onClientLoggingInEvent);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeTodoClient::onRegisterKeyMappingsEvent);
    }

    /**
     * 澶勭悊瀹㈡埛绔?Tick 浜嬩欢锛屽搷搴斿揩鎹烽敭骞剁淮鎶ょ姸鎬佸悓姝ャ€?
     */
    private static void onClientTickEvent(TickEvent.ClientTickEvent.Post ignored) {
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

    /**
     * Registers key mappings on the Forge mod event bus.
     *
     * @param event key mapping registration event
     */
    private static void onRegisterKeyMappingsEvent(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TODO_KEY);
        event.register(TOGGLE_HUD_KEY);
        event.register(TOGGLE_HUD_VISIBILITY_KEY);
    }

    /**
     * 澶勭悊瀹㈡埛绔€€鍑轰簨浠讹紝閲嶇疆杩滅鍚屾鏍囪銆?
     */
    private static void onClientLoggingOutEvent(ClientPlayerNetworkEvent.LoggingOut ignored) {
        pendingRemoteResync = false;
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
    }

    /**
     * 澶勭悊瀹㈡埛绔櫥褰曚簨浠讹紝鍑嗗杩滅鍚屾銆?
     */
    private static void onClientLoggingInEvent(ClientPlayerNetworkEvent.LoggingIn ignored) {
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
     * 瑙ｆ瀽褰撳墠鏈嶅姟鍣ㄧ殑瀛樺偍鍛藉悕绌洪棿銆?
     *
     * @param client Minecraft 瀹㈡埛绔疄渚?
     * @return 瀛樺偍鍛藉悕绌洪棿
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
     * 搴旂敤鏂扮殑瀛樺偍鍛藉悕绌洪棿骞跺悓姝?HUD 鐘舵€併€?
     *
     * @param namespace 瀛樺偍鍛藉悕绌洪棿
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
     * 鎵撳紑 Todo 鍒楄〃鐣岄潰銆?
     *
     * @param current 褰撳墠 Minecraft 瀹㈡埛绔?
     */
    private static void openTodoScreen(Minecraft current) {
        if (current.screen == null) {
            current.setScreen(new TodoScreen(current.screen));
        }
    }

    /**
     * 鍒濆鍖?HUD 娓叉煋鍣ㄥ苟娉ㄥ唽鍒板钩鍙伴€傞厤灞傘€?
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
     * 鍒囨崲 HUD 灞曞紑/鏀惰捣鐘舵€併€?
     */
    private static void toggleHud() {
        if (hudRenderer != null) {
            hudRenderer.toggleExpanded();
        }
    }

    /**
     * 鍒囨崲 HUD 鍙鎬с€?
     */
    private static void toggleHudVisibility() {
        boolean nextVisible = !ClientBridge.ops().isHudVisible();
        ClientBridge.ops().setHudVisible(nextVisible);
    }

    /**
     * 鑾峰彇鍥㈤槦浠诲姟绠＄悊鍣ㄣ€?
     *
     * @return 鍥㈤槦浠诲姟绠＄悊鍣?
     */
    public static TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    /**
     * 鑾峰彇褰撳墠婵€娲婚」鐩?ID銆?
     *
     * @return 婵€娲婚」鐩?ID
     */
    public static String getActiveProjectId() {
        return activeProjectId;
    }

    /**
     * 璁剧疆褰撳墠婵€娲婚」鐩?ID銆?
     *
     * @param projectId 婵€娲婚」鐩?ID
     */
    public static void setActiveProjectId(String projectId) {
        activeProjectId = projectId;
    }

    /**
     * 鍒ゆ柇 HUD 鏄惁鍙銆?
     *
     * @return 鏄惁鍙
     */
    public static boolean isHudVisible() {
        return hudVisible;
    }

    /**
     * 璁剧疆 HUD 鍙鐘舵€併€?
     *
     * @param visible 鏄惁鍙
     */
    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    /**
     * 浠庢湇鍔＄鏇存柊鍥㈤槦浠诲姟鍒楄〃銆?
     *
     * @param tasks 鍥㈤槦浠诲姟鍒楄〃
     */
    public static void updateTeamTasksFromServer(java.util.List<Task> tasks) {
        teamTaskManager.clearAll();
        for (Task task : tasks) {
            teamTaskManager.addTask(task);
        }
    }

    /**
     * 鍒ゆ柇鍥㈤槦椤圭洰鍔熻兘鏄惁鍙敤銆?
     *
     * @return 鏄惁鍚敤
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
