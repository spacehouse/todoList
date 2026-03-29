package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectNameFormatter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;

/**
 * 项目侧边栏列表组件：展示项目并处理选择、滚动与测试辅助读取。
 */
public class ProjectListWidget implements Renderable, GuiEventListener, NarratableEntry {
    private final Minecraft client;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int itemHeight = 20;

    private List<Project> projects = new ArrayList<>();
    private List<Project> sourceProjects = new ArrayList<>();
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
     * 设置项目数据源并重建可见列表。
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
     * 设置项目选择回调。
     *
     * @param callback 选择回调
     */
    public void setOnProjectSelected(Consumer<Project> callback) {
        this.onProjectSelected = callback;
    }

    /**
     * 设置当前选中项目。
     *
     * @param project 当前选中项目
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
     * 设置项目列表滚动偏移量，并自动裁剪到可见范围。
     *
     * @param scrollOffset 目标滚动偏移量
     */
    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = scrollOffset;
        clampScrollOffset();
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
     * 重建用于渲染的项目列表。
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
     * 确保当前选中项在项目列表变化后仍然有效。
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
     * 将滚动偏移限制在当前列表的可用范围内。
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
     * 渲染项目列表组件。
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

        context.fill(x, y, x + width, y + height, 0xFF101010);
        context.renderOutline(x, y, width, height, config.getBorderColor());

        int visibleItems = height / itemHeight;
        for (int indexInView = 0; indexInView < visibleItems; indexInView++) {
            int index = indexInView + scrollOffset;
            if (index >= projects.size()) {
                break;
            }

            Project project = projects.get(index);
            int itemY = y + indexInView * itemHeight;
            boolean isSelected = selectedProject != null
                    && selectedProject.getId() != null
                    && selectedProject.getId().equals(project.getId());
            boolean isHovered = mouseX >= x && mouseX < x + width && mouseY >= itemY && mouseY < itemY + itemHeight;

            if (isSelected) {
                context.fill(x + 1, itemY, x + width - 1, itemY + itemHeight, 0xFF303030);
            } else if (isHovered) {
                context.fill(x + 1, itemY, x + width - 1, itemY + itemHeight, 0xFF202020);
            }

            int color = project.getColor() | 0xFF000000;
            context.fill(x + 4, itemY + 4, x + 8, itemY + 16, color);

            boolean starred = config.isHudProjectStarred(project.getId());
            int starX = x + width - 12;
            int starColor = starred ? 0xFFFFD700 : 0xFF666666;
            context.drawString(textRenderer, starred ? "★" : "☆", starX, itemY + (itemHeight - 8) / 2, starColor, false);

            String name = ProjectNameFormatter.toDisplayText(project).getString();
            int nameColor = isSelected ? 0xFFFFFFFF : 0xFFAAAAAA;
            int nameWidth = Math.max(16, width - 28);
            String displayName = textRenderer.plainSubstrByWidth(name, nameWidth);
            context.drawString(textRenderer, displayName, x + 12, itemY + (itemHeight - 8) / 2, nameColor, false);
        }

        if (projects.size() > visibleItems) {
            int barHeight = (int) ((float) visibleItems / projects.size() * height);
            int barY = y + (int) ((float) scrollOffset / projects.size() * height);
            context.fill(x + width - 2, barY, x + width, barY + barHeight, 0xFF808080);
        }
    }

    /**
     * 处理鼠标点击。
     *
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按键
     * @return 是否已消费事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
     * 处理鼠标滚轮滚动。
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
     * 设置焦点状态。
     *
     * @param focused 是否聚焦
     */
    @Override
    public void setFocused(boolean focused) {
    }

    /**
     * 判断当前组件是否聚焦。
     *
     * @return 始终返回 false
     */
    @Override
    public boolean isFocused() {
        return false;
    }

    /**
     * 返回旁白优先级。
     *
     * @return 旁白优先级
     */
    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    /**
     * 更新旁白内容。
     *
     * @param builder 旁白输出构建器
     */
    @Override
    public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
    }

    /**
     * 计算下一个焦点路径。
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
