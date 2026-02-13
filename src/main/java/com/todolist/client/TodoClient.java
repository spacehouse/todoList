package com.todolist.client;

import com.todolist.TodoListMod;
import com.todolist.config.ModConfig;
import com.todolist.gui.TodoScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
    private static MinecraftClient client;

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

        // Register key press handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openTodoKeyBinding.wasPressed()) {
                openTodoScreen();
            }
        });

        TodoListMod.LOGGER.info("Registered key binding: K key");
    }

    private void registerHudRenderer() {
        // TODO: Implement HUD rendering in Phase 2
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (ModConfig.getInstance().isEnableHud() && client.player != null) {
                // Render task HUD in Phase 2
                // For now, just a placeholder
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

    public static KeyBinding getOpenTodoKeyBinding() {
        return openTodoKeyBinding;
    }
}
