package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.config.ModConfig;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 璐熻矗鍦?HUD 涓婃覆鏌撳緟鍔炰簨椤瑰垪琛ㄣ€? */
public class TodoHudRenderer {
    private final MinecraftClient client;
    private boolean expanded;

    public TodoHudRenderer(MinecraftClient client) {
        this.client = client;
        this.expanded = ModConfig.getInstance().isHudDefaultExpanded();
    }

    public void render(DrawContext context, float delta) {
        if (client.options.hudHidden) return;
        
        ModConfig config = ModConfig.getInstance();
        List<Task> tasks = TodoListCommon.getTaskStorage().loadTasksSafe(); // Simplified for now
        
        if (tasks.isEmpty() && !config.isHudShowWhenEmpty()) return;

        int x = config.isHudUseCustomPosition() ? config.getHudCustomX() : 10;
        int y = config.isHudUseCustomPosition() ? config.getHudCustomY() : 10;
        
        renderTaskList(context, x, y, tasks, config);
    }

    private void renderTaskList(DrawContext context, int x, int y, List<Task> tasks, ModConfig config) {
        int currentY = y;
        int width = config.getHudWidth();
        float opacity = (float) config.getHudOpacity();
        int backgroundColor = (int) (opacity * 255) << 24;

        // Render header
        context.fill(x, currentY, x + width, currentY + 12, backgroundColor | 0x333333);
        context.drawTextWithShadow(client.textRenderer, Text.translatable("gui.todolist.hud.title"), x + 4, currentY + 2, 0xFFFFFF);
        currentY += 14;

        if (expanded) {
            for (Task task : tasks) {
                int color = task.isCompleted() ? 0xAAAAAA : 0xFFFFFF;
                String text = (task.isCompleted() ? "[X] " : "[ ] ") + task.getTitle();
                context.drawTextWithShadow(client.textRenderer, text, x + 4, currentY, color);
                currentY += 10;
            }
        }
    }

    public void forceRefreshTasks() {
        // Implementation for refreshing tasks
    }

    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }

    public boolean isExpanded() {
        return expanded;
    }
}


