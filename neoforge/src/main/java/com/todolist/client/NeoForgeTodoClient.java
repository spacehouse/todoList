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
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
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
 * NeoForge 楠炲啿褰寸€广垺鍩涚粩顖欏瘜缁眹鈧?
 * 鐠愮喕鐭楃€广垺鍩涚粩顖氬灥婵瀵查妴浣风皑娴犲墎娲冮崥顑跨瑢 HUD 濞撳弶鐓嬬粵澶婎槱閻炲棎鈧?
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

    /**
     * 缁変焦婀侀弸鍕偓鐘插毐閺佸府绱濈粋浣诡剾鐎圭偘绶ラ崠鏍モ偓?
     */
    private NeoForgeTodoClient() {
    }

    /**
     * 閸掓繂顫愰崠?NeoForge 鐎广垺鍩涚粩顖炩偓鏄忕帆閵?
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
     * 濞夈劌鍞介柊宥囩枂閻ｅ矂娼板銉ュ范閵?
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
     * 闁俺绻冮崣宥呯殸濞夈劌鍞界€广垺鍩涚粩顖欑皑娴犲墎娲冮崥顒€娅掗妴?
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
     * Registers NeoForge key mappings on the mod event bus.
     *
     * @param event key mapping registration event
     */
    private static void onRegisterKeyMappingsEvent(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TODO_KEY);
        event.register(TOGGLE_HUD_KEY);
        event.register(TOGGLE_HUD_VISIBILITY_KEY);
    }

    /**
     * 鐎广垺鍩涚粩?Tick 娴滃娆㈡径鍕倞閵?
     *
     * @param ignored 娴滃娆㈢€电钖?
     */
    private static void onClientTickEvent(ClientTickEvent.Post ignored) {
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
                NeoForgeNetworkBridge.canSend(ProjectPackets.REQUEST_SYNC_PROJECTS_ID) &&
                NeoForgeNetworkBridge.canSend(TaskPackets.TEAM_REQUEST_SYNC_ID)) {
            NeoForgeClientProjectPackets.sendRequestSyncProjects();
            NeoForgeClientProjectPackets.sendSetHudStarredProjectIds(ModConfig.getInstance().getHudStarredProjectIds());
            NeoForgeClientTaskPackets.requestTeamSync();
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
     * 鐎广垺鍩涚粩顖滄閸戣桨绨ㄦ禒璺侯槱閻炲棎鈧?
     *
     * @param ignored 娴滃娆㈢€电钖?
     */
    private static void onClientLoggingOutEvent(ClientPlayerNetworkEvent.LoggingOut ignored) {
        pendingRemoteResync = false;
        applyStorageNamespace(DataPathProvider.LOCAL_STORAGE_NAMESPACE);
    }

    /**
     * 鐎广垺鍩涚粩顖滄瑜版洑绨ㄦ禒璺侯槱閻炲棎鈧?
     *
     * @param ignored 娴滃娆㈢€电钖?
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
     * 閺嶈宓侀張宥呭閸ｃ劋淇婇幁顖滄晸閹存劕鐡ㄩ崒銊ユ嚒閸氬秶鈹栭梻娣偓?
     *
     * @param client 鐎广垺鍩涚粩顖氱杽娓?
     * @return 閸涜棄鎮曠粚娲？
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
     * 鎼存梻鏁ら弬鎵畱鐎涙ê鍋嶉崨钘夋倳缁屾椽妫块妴?
     *
     * @param namespace 閸涜棄鎮曠粚娲？
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
     * 濞撳弶鐓?HUD 鐟曞棛娲婄仦鍌樷偓?
     *
     * @param event 娴滃娆㈢€电钖?
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
     * 閹垫挸绱戞禒璇插閸掓銆冮悾宀勬桨閵?
     *
     * @param current 鐎广垺鍩涚粩顖氱杽娓?
     */
    private static void openTodoScreen(Minecraft current) {
        if (current.screen == null) {
            current.setScreen(new TodoScreen(current.screen));
        }
    }

    /**
     * 濞夈劌鍞?HUD 濞撳弶鐓嬮崳銊ｂ偓?
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
     * 閸掑洦宕?HUD 鐏炴洖绱戦悩鑸碘偓浣碘偓?
     */
    private static void toggleHud() {
        if (hudRenderer != null) {
            hudRenderer.toggleExpanded();
        }
    }

    /**
     * 閸掑洦宕?HUD 閸欘垵顫嗛幀褋鈧?
     */
    private static void toggleHudVisibility() {
        boolean nextVisible = !ClientBridge.ops().isHudVisible();
        ClientBridge.ops().setHudVisible(nextVisible);
    }

    /**
     * 閼惧嘲褰囬崶銏ゆЕ娴犺濮熺粻锛勬倞閸ｃ劊鈧?
     *
     * @return 閸ャ垽妲︽禒璇插缁狅紕鎮婇崳?
     */
    public static TaskManager getTeamTaskManager() {
        return teamTaskManager;
    }

    /**
     * 閼惧嘲褰囪ぐ鎾冲濞茶濮╂い鍦窗 ID閵?
     *
     * @return 妞ゅ湱娲?ID
     */
    public static String getActiveProjectId() {
        return activeProjectId;
    }

    /**
     * 鐠佸墽鐤嗚ぐ鎾冲濞茶濮╂い鍦窗 ID閵?
     *
     * @param projectId 妞ゅ湱娲?ID
     */
    public static void setActiveProjectId(String projectId) {
        activeProjectId = projectId;
    }

    /**
     * 閸掋倖鏌?HUD 閺勵垰鎯侀崣顖濐潌閵?
     *
     * @return 閺勵垰鎯侀崣顖濐潌
     */
    public static boolean isHudVisible() {
        return hudVisible;
    }

    /**
     * 鐠佸墽鐤?HUD 閸欘垵顫嗛幀褋鈧?
     *
     * @param visible 閺勵垰鎯侀崣顖濐潌
     */
    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    /**
     * 娴犲孩婀囬崝锛勵伂閺囧瓨鏌婇崶銏ゆЕ娴犺濮熼崚妤勩€冮妴?
     *
     * @param tasks 娴犺濮熼崚妤勩€?
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
     * 閸掋倖鏌囬弰顖氭儊閸氼垳鏁ら崶銏ゆЕ妞ゅ湱娲伴崝鐔诲厴閵?
     *
     * @return 閺勵垰鎯侀崥顖滄暏
     */
    public static boolean isTeamProjectsEnabled() {
        Minecraft current = client != null ? client : Minecraft.getInstance();
        if (current == null) {
            return false;
        }
        if (current.isLocalServer()) {
            var server = current.getSingleplayerServer();
            return server != null && server.isPublished();
        }
        return NeoForgeNetworkBridge.canSend(ProjectPackets.ADD_PROJECT_ID);
    }
}
