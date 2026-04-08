package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;

/**
 * 项目侧边栏列表组件：展示项目并处理选择/滚动等交互。
 */
public class ProjectListWidget implements Renderable, GuiEventListener, NarratableEntry {
    private static final int ITEM_HORIZONTAL_INSET = 3;
    private static final int ITEM_VERTICAL_GAP = 3;
    private static final int ITEM_COLOR_BAR_WIDTH = 2;
    private static final int ITEM_TEXT_LEFT_GAP = 4;
    private static final int ITEM_COUNT_MAX_WIDTH = 16;
    private static final int ITEM_STAR_WIDTH = 8;

    private final Minecraft client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int itemHeight = 20;

    private List<Project> projects = new ArrayList<>();
    private List<Project> sourceProjects = new ArrayList<>();
    private Map<String, Integer> projectTaskCounts = new HashMap<>();
    private Project selectedProject;
    private Consumer<Project> onProjectSelected;
    
    // Simple scrolling
    private int scrollOffset = 0;

    /**
     * 创建项目列表组件。
     */
    public ProjectListWidget(Minecraft client, int x, int y, int width, int height) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * 设置项目数据源并重建可见列表（含星标排序与筛选逻辑）。
     */
    public void setProjects(List<Project> projects) {
        if (projects == null) {
            this.sourceProjects = new ArrayList<>();
        } else {
            this.sourceProjects = new ArrayList<>(projects);
        }
        rebuildProjects();
        ensureSelectionFallback();
    }

    /**
     * 设置项目选择回调。
     */
    public void setOnProjectSelected(Consumer<Project> callback) {
        this.onProjectSelected = callback;
    }

    /**
     * 设置侧栏项目对应的任务数量，用于在项目名称右侧显示数量提示。
     *
     * @param taskCounts 项目 ID 到任务数的映射
     */
    public void setProjectTaskCounts(Map<String, Integer> taskCounts) {
        if (taskCounts == null || taskCounts.isEmpty()) {
            this.projectTaskCounts = new HashMap<>();
            return;
        }
        this.projectTaskCounts = new HashMap<>(taskCounts);
    }

    /**
     * 设置当前选中的项目，并在选中项失效时回退到默认项目。
     *
     * @param project 当前选中的项目
     */
    public void setSelectedProject(Project project) {
        this.selectedProject = project;
        ensureSelectionFallback();
    }

    /**
     * 返回当前项目列表的滚动偏移量。
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    /**
     * 设置项目列表滚动偏移量，并自动裁剪到当前可见范围内。
     *
     * @param scrollOffset 目标滚动偏移量
     */
    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
        clampScrollOffset();
    }

    public Project getSelectedProject() {
        return selectedProject;
    }

    /**
     * 返回当前用于渲染的项目列表快照，供同包测试代码断言排序与筛选结果。
     *
     * @return 当前可见项目列表快照
     */
    List<Project> getProjectsForTest() {
        return List.copyOf(projects);
    }

    /**
     * 返回项目列表的布局边界，供同包测试验证侧栏滚动区尺寸。
     *
     * @return 依次包含 x、y、width、height 的边界数组
     */
    int[] getBoundsForTest() {
        return new int[] {x, y, width, height};
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

    private void ensureSelectionFallback() {
        if (projects == null || projects.isEmpty()) {
            selectedProject = null;
            return;
        }
        if (selectedProject != null && selectedProject.getId() != null && !selectedProject.getId().isEmpty()) {
            for (Project p : projects) {
                if (p != null && selectedProject.getId().equals(p.getId())) {
                    return;
                }
            }
        }
        for (Project p : projects) {
            if (p == null) continue;
            if (p.isDefaultPersonalProject() || p.isDefaultTeamProject()) {
                selectedProject = p;
                return;
            }
        }
        selectedProject = projects.get(0);
    }

    private void clampScrollOffset() {
        int visibleItems = Math.max(1, height / itemHeight);
        int maxScroll = Math.max(0, projects.size() - visibleItems);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();
        Font textRenderer = client.font;

        context.fill(x, y, x + width, y + height, 0xFF0D1115);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF111820);
        context.renderOutline(x, y, width, height, config.getBorderColor());

        int visibleItems = height / itemHeight;
        for (int i = 0; i < visibleItems; i++) {
            int index = i + scrollOffset;
            if (index >= projects.size()) {
                break;
            }

            Project project = projects.get(index);
            int itemY = y + i * itemHeight;
            boolean isSelected = selectedProject != null
                    && selectedProject.getId() != null
                    && selectedProject.getId().equals(project.getId());
            boolean isHovered = mouseX >= x && mouseX < x + width && mouseY >= itemY && mouseY < itemY + itemHeight;

            int rowTop = itemY + ITEM_VERTICAL_GAP;
            int rowBottom = itemY + itemHeight - ITEM_VERTICAL_GAP;
            int rowLeft = x + ITEM_HORIZONTAL_INSET;
            int rowRight = x + width - ITEM_HORIZONTAL_INSET;
            if (isSelected) {
                context.fill(rowLeft, rowTop, rowRight, rowBottom, 0xFF283648);
            } else if (isHovered) {
                context.fill(rowLeft, rowTop, rowRight, rowBottom, 0xFF1A2530);
            }

            int nameLeadInset = 2;
            if (isSelected || isHovered) {
                int color = project.getColor() | 0xFF000000;
                context.fill(rowLeft + 1, rowTop + 3, rowLeft + 1 + ITEM_COLOR_BAR_WIDTH, rowBottom - 3, color);
                nameLeadInset = 1 + ITEM_COLOR_BAR_WIDTH + ITEM_TEXT_LEFT_GAP;
            }

            boolean starred = config.isHudProjectStarred(project.getId());
            int starX = rowRight - ITEM_STAR_WIDTH - 1;
            int starColor = starred ? 0xFFFFD700 : 0xFF666666;
            context.drawString(textRenderer, starred ? "★" : "☆", starX, itemY + (itemHeight - 8) / 2, starColor, false);

            String name = ProjectNameFormatter.toDisplayText(project).getString();
            String taskCountText = getProjectTaskCountText(project);
            int nameColor = isSelected ? 0xFFFFFFFF : 0xFFD6E0EC;
            int countColor = isSelected ? 0xFFAEC3DD : 0xFF8FA0B5;
            int countWidth = Math.min(ITEM_COUNT_MAX_WIDTH, textRenderer.width(taskCountText));
            int countX = starX - countWidth - 3;
            int nameX = rowLeft + nameLeadInset;
            int nameWidth = Math.max(16, countX - 4 - nameX);
            String displayName = textRenderer.plainSubstrByWidth(name, nameWidth);
            context.drawString(textRenderer, displayName, nameX, itemY + (itemHeight - 8) / 2, nameColor, false);
            context.drawString(textRenderer, taskCountText, countX, itemY + (itemHeight - 8) / 2, countColor, false);
        }

        if (projects.size() > visibleItems) {
            int barHeight = (int)((float)visibleItems / projects.size() * height);
            int barY = y + (int)((float)scrollOffset / projects.size() * height);
            context.fill(x + width - 3, y + 2, x + width - 1, y + height - 2, 0xFF1C2731);
            context.fill(x + width - 3, barY, x + width - 1, barY + barHeight, 0xFF6E8093);
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
             if (amount == 0) {
                 return false;
             }
             int visibleItems = Math.max(1, height / itemHeight);
             int maxScroll = Math.max(0, projects.size() - visibleItems);
             int before = scrollOffset;
             if (amount > 0) {
                 scrollOffset = Math.max(0, scrollOffset - 1);
             } else {
                 scrollOffset = Math.min(maxScroll, scrollOffset + 1);
             }
             return scrollOffset != before;
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
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
    }

    /**
     * 返回项目在侧栏中的任务数文本，并对极端值做收敛显示。
     *
     * @param project 目标项目
     * @return 任务数文本
     */
    private String getProjectTaskCountText(Project project) {
        if (project == null || project.getId() == null || project.getId().isEmpty()) {
            return "0";
        }
        int count = Math.max(0, projectTaskCounts.getOrDefault(project.getId(), 0));
        if (count > 99) {
            return "99+";
        }
        return Integer.toString(count);
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
        return null;
    }
}


