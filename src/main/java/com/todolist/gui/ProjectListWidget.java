package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProjectListWidget implements Drawable, Element, Selectable {
    private final MinecraftClient client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int itemHeight = 20;

    private List<Project> projects = new ArrayList<>();
    private List<Project> sourceProjects = new ArrayList<>();
    private Project selectedProject;
    private Consumer<Project> onProjectSelected;
    
    // Simple scrolling
    private int scrollOffset = 0;

    public ProjectListWidget(MinecraftClient client, int x, int y, int width, int height) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setProjects(List<Project> projects) {
        if (projects == null) {
            this.sourceProjects = new ArrayList<>();
        } else {
            this.sourceProjects = new ArrayList<>(projects);
        }
        rebuildProjects();
    }

    public void setOnProjectSelected(Consumer<Project> callback) {
        this.onProjectSelected = callback;
    }

    public void setSelectedProject(Project project) {
        this.selectedProject = project;
    }

    public Project getSelectedProject() {
        return selectedProject;
    }

    private void rebuildProjects() {
        ModConfig config = ModConfig.getInstance();
        List<Project> starred = new ArrayList<>();
        List<Project> unstarred = new ArrayList<>();
        for (Project p : sourceProjects) {
            if (p == null) continue;
            if (config.isHudProjectStarred(p.getId())) {
                starred.add(p);
            } else {
                unstarred.add(p);
            }
        }
        List<Project> merged = new ArrayList<>(starred.size() + unstarred.size());
        merged.addAll(starred);
        merged.addAll(unstarred);
        this.projects = merged;
        clampScrollOffset();
    }

    private void clampScrollOffset() {
        int visibleItems = height / itemHeight;
        int maxScroll = Math.max(0, projects.size() - visibleItems);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        // Background
        context.fill(x, y, x + width, y + height, 0xFF101010); // Darker background for sidebar
        context.drawBorder(x, y, width, height, config.getBorderColor());

        // Header "PROJECTS"
        // context.drawText(textRenderer, "Projects", x + 5, y - 12, 0xFFFFFFFF, false);

        int visibleItems = height / itemHeight;
        
        for (int i = 0; i < visibleItems; i++) {
            int index = i + scrollOffset;
            if (index >= projects.size()) break;

            Project project = projects.get(index);
            int itemY = y + i * itemHeight;

            boolean isSelected = selectedProject != null
                    && selectedProject.getId() != null
                    && selectedProject.getId().equals(project.getId());
            boolean isHovered = mouseX >= x && mouseX < x + width && mouseY >= itemY && mouseY < itemY + itemHeight;

            // Background
            if (isSelected) {
                context.fill(x + 1, itemY, x + width - 1, itemY + itemHeight, 0xFF303030);
            } else if (isHovered) {
                context.fill(x + 1, itemY, x + width - 1, itemY + itemHeight, 0xFF202020);
            }

            // Color indicator
            int color = project.getColor() | 0xFF000000;
            context.fill(x + 4, itemY + 4, x + 8, itemY + 16, color);

            boolean starred = config.isHudProjectStarred(project.getId());
            int starX = x + width - 12;
            int starColor = starred ? 0xFFFFD700 : 0xFF666666;
            context.drawText(textRenderer, starred ? "★" : "☆", starX, itemY + (itemHeight - 8) / 2, starColor, false);

            // Name
            String name = project.getName();
            if (name != null && (name.startsWith("gui.todolist.") || name.startsWith("item.") || name.startsWith("block."))) {
                name = Text.translatable(name).getString();
            }
            int nameColor = isSelected ? 0xFFFFFFFF : 0xFFAAAAAA;
            
            // Truncate name if too long
            String displayName = textRenderer.trimToWidth(name, width - 28);
            context.drawText(textRenderer, displayName, x + 12, itemY + (itemHeight - 8) / 2, nameColor, false);
            
            // Scope indicator (Icon or text?)
            // For now just name
        }
        
        // Scrollbar indicator if needed (simplified)
        if (projects.size() > visibleItems) {
            int barHeight = (int)((float)visibleItems / projects.size() * height);
            int barY = y + (int)((float)scrollOffset / projects.size() * height);
            context.fill(x + width - 2, barY, x + width, barY + barHeight, 0xFF808080);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            int index = (int)((mouseY - y) / itemHeight) + scrollOffset;
            if (index >= 0 && index < projects.size()) {
                Project clicked = projects.get(index);
                int mx = (int) mouseX;
                int my = (int) mouseY;
                int itemY = y + (index - scrollOffset) * itemHeight;
                int starLeft = x + width - 14;
                int starRight = x + width - 4;
                if (mx >= starLeft && mx < starRight && my >= itemY && my < itemY + itemHeight) {
                    ModConfig.getInstance().toggleHudStarredProjectId(clicked.getId());
                    rebuildProjects();
                    return true;
                }
                if (onProjectSelected != null) {
                    onProjectSelected.accept(clicked);
                }
                // play click sound
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
         if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
             int visibleItems = height / itemHeight;
             int maxScroll = Math.max(0, projects.size() - visibleItems);
             if (amount > 0) {
                 scrollOffset = Math.max(0, scrollOffset - 1);
             } else {
                 scrollOffset = Math.min(maxScroll, scrollOffset + 1);
             }
             return true;
         }
         return false;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public SelectionType getType() {
        return SelectionType.NONE;
    }

    @Override
    public void appendNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
    }
    
    @Nullable
    @Override
    public GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return null;
    }
}
