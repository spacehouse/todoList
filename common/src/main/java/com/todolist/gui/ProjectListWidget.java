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
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 项目侧栏列表组件，负责展示项目、处理选择、星标切换与滚动交互。
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
    private int scrollOffset = 0;

    /**
     * 创建项目列表组件。
     *
     * @param client 客户端实例
     * @param x 左上角 X 坐标
     * @param y 左上角 Y 坐标
     * @param width 组件宽度
     * @param height 组件高度
     */
    public ProjectListWidget(Minecraft client, int x, int y, int width, int height) {
        this.client = client;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * 设置项目数据源，并重建当前可见的项目列表。
     *
     * @param projects 项目列表
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
     * 设置项目点击后的选择回调。
     *
     * @param callback 选择回调
     */
    public void setOnProjectSelected(Consumer<Project> callback) {
        this.onProjectSelected = callback;
    }

    /**
     * 设置项目对应的任务数量，用于在侧栏右侧显示计数。
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
     * 设置当前选中的项目，并在选中项失效时自动回退。
     *
     * @param project 当前选中的项目
     */
    public void setSelectedProject(Project project) {
        this.selectedProject = project;
        ensureSelectionFallback();
    }

    /**
     * 获取当前选中项目。
     *
     * @return 当前选中项目
     */
    public Project getSelectedProject() {
        return selectedProject;
    }

    /**
     * 获取当前项目列表滚动偏移量。
     *
     * @return 当前滚动偏移量
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    /**
     * 设置项目列表的滚动偏移量，并裁剪到合法范围。
     *
     * @param scrollOffset 目标滚动偏移量
     */
    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
        clampScrollOffset();
    }

    /**
     * 返回当前可见项目列表快照，供同包测试校验排序结果。
     *
     * @return 当前可见项目列表快照
     */
    List<Project> getProjectsForTest() {
        return List.copyOf(projects);
    }

    /**
     * 返回组件边界，供同包测试验证侧栏布局。
     *
     * @return 依次包含 x、y、width、height 的数组
     */
    int[] getBoundsForTest() {
        return new int[] {x, y, width, height};
    }

    /**
     * 按星标状态重建项目列表顺序。
     */
    private void rebuildProjects() {
        ModConfig config = ModConfig.getInstance();
        List<Project> starred = new ArrayList<>();
        List<Project> unstarred = new ArrayList<>();
        for (Project project : sourceProjects) {
            if (project == null) {
                continue;
            }
            if (config.isHudProjectStarred(project.getId())) {
                starred.add(project);
            } else {
                unstarred.add(project);
            }
        }
        List<Project> merged = new ArrayList<>(starred.size() + unstarred.size());
        merged.addAll(starred);
        merged.addAll(unstarred);
        this.projects = merged;
        clampScrollOffset();
    }

    /**
     * 确保当前选中项仍然有效，否则回退到默认项目或首项。
     */
    private void ensureSelectionFallback() {
        if (projects == null || projects.isEmpty()) {
            selectedProject = null;
            return;
        }
        if (selectedProject != null && selectedProject.getId() != null && !selectedProject.getId().isEmpty()) {
            for (Project project : projects) {
                if (project != null && selectedProject.getId().equals(project.getId())) {
                    return;
                }
            }
        }
        for (Project project : projects) {
            if (project == null) {
                continue;
            }
            if (project.isDefaultPersonalProject() || project.isDefaultTeamProject()) {
                selectedProject = project;
                return;
            }
        }
        selectedProject = projects.get(0);
    }

    /**
     * 将滚动偏移量限制在当前列表可滚动范围内。
     */
    private void clampScrollOffset() {
        int visibleItems = Math.max(1, height / itemHeight);
        int maxScroll = Math.max(0, projects.size() - visibleItems);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
    }

    /**
     * 渲染项目侧栏的背景、项目行、星标与滚动条。
     *
     * @param context GUI 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔
     */
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();
        Font textRenderer = client.font;

        context.fill(x, y, x + width, y + height, 0xFF0D1115);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF111820);
        context.submitOutline(x, y, width, height, config.getBorderColor());

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
            int barHeight = (int) ((float) visibleItems / projects.size() * height);
            int barY = y + (int) ((float) scrollOffset / projects.size() * height);
            context.fill(x + width - 3, y + 2, x + width - 1, y + height - 2, 0xFF1C2731);
            context.fill(x + width - 3, barY, x + width - 1, barY + barHeight, 0xFF6E8093);
        }
    }

    /**
     * 处理项目项点击与星标切换点击。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @return 是否已消费事件
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 1.21.9：鼠标事件改为 MouseButtonEvent 记录，解构坐标与按键保持原逻辑不变
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            int index = (int) ((mouseY - y) / itemHeight) + scrollOffset;
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
                return true;
            }
        }
        return false;
    }

    /**
     * 处理项目列表区域内的滚轮滚动。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param horizontalAmount 水平滚动量
     * @param verticalAmount 垂直滚动量
     * @return 是否已消费事件
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            if (verticalAmount == 0) {
                return false;
            }
            int visibleItems = Math.max(1, height / itemHeight);
            int maxScroll = Math.max(0, projects.size() - visibleItems);
            int before = scrollOffset;
            if (verticalAmount > 0) {
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else {
                scrollOffset = Math.min(maxScroll, scrollOffset + 1);
            }
            return scrollOffset != before;
        }
        return false;
    }

    /**
     * 接收焦点变化通知；当前组件不维护独立焦点状态。
     *
     * @param focused 是否聚焦
     */
    @Override
    public void setFocused(boolean focused) {
    }

    /**
     * 返回当前组件是否持有焦点；当前始终不参与焦点管理。
     *
     * @return 始终返回 false
     */
    @Override
    public boolean isFocused() {
        return false;
    }

    /**
     * 返回旁白优先级；当前组件不提供旁白内容。
     *
     * @return 旁白优先级
     */
    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    /**
     * 更新旁白内容；当前组件不输出旁白文本。
     *
     * @param builder 旁白输出构建器
     */
    @Override
    public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
    }

    /**
     * 返回项目在侧栏中的任务数量文本，并对极端值做收敛显示。
     *
     * @param project 目标项目
     * @return 任务数量文本
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

    /**
     * 返回下一个可聚焦路径；当前组件不支持键盘焦点导航。
     *
     * @param navigation 焦点导航事件
     * @return 当前组件不参与焦点导航时返回 null
     */
    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
        return null;
    }
}
