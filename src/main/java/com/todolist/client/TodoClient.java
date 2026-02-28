package com.todolist.client;

import com.todolist.TodoListMod;
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

    @Override
    public void onInitializeClient() {
        TodoListMod.LOGGER.info("Initializing Todo List Mod client...");

        client = MinecraftClient.getInstance();

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

    private void registerHudRenderer() {
        // Initialize HUD renderer
        hudRenderer = new TodoHudRenderer(client);

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (ModConfig.getInstance().isEnableHud() && client.player != null) {
                hudRenderer.render(drawContext, tickDelta);
            }
        });

        TodoListMod.LOGGER.info("Registered HUD renderer");
    }

    private void registerJoinEvent() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TodoListMod.LOGGER.info("Joined server, requesting task sync...");
            // Request task sync from server (to be implemented in Phase 3)
        });
    }

    private void openTodoScreen() {
        if (client.currentScreen == null) {
            client.setScreen(new TodoScreen(client.currentScreen));
        }
    }

    private void toggleHud() {
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

    public static KeyBinding getOpenTodoKeyBinding() {
        return openTodoKeyBinding;
    }

    public static TodoHudRenderer getHudRenderer() {
        return hudRenderer;
    }

    public static boolean isTeamProjectsEnabled() {
        MinecraftClient c = client != null ? client : MinecraftClient.getInstance();
        if (c == null) return false;
        if (c.isInSingleplayer()) return false;
        return ClientPlayNetworking.canSend(ProjectPackets.ADD_PROJECT_ID);
    }
}
