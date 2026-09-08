package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.project.Project;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 项目列表组件离线自测入口：覆盖排序、选择、星标点击与滚动裁剪等核心行为。
 */
public final class ProjectListWidgetTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ProjectListWidgetTestMain() {
    }

    /**
     * 程序入口，串行执行项目列表组件的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("ProjectListWidgetTestMain.shouldSortStarredProjectsFirst", ProjectListWidgetTestMain::shouldSortStarredProjectsFirst);
        GuiTestSupport.runTestCase("ProjectListWidgetTestMain.shouldFallbackToDefaultProjectWhenSelectionMissing", ProjectListWidgetTestMain::shouldFallbackToDefaultProjectWhenSelectionMissing);
        GuiTestSupport.runTestCase("ProjectListWidgetTestMain.shouldInvokeCallbackWhenClickingProject", ProjectListWidgetTestMain::shouldInvokeCallbackWhenClickingProject);
        GuiTestSupport.runTestCase("ProjectListWidgetTestMain.shouldToggleStarWithoutSelectingProject", ProjectListWidgetTestMain::shouldToggleStarWithoutSelectingProject);
        GuiTestSupport.runTestCase("ProjectListWidgetTestMain.shouldClampScrollOffset", ProjectListWidgetTestMain::shouldClampScrollOffset);
    }

    /**
     * 校验星标项目会排到列表前部。
     */
    private static void shouldSortStarredProjectsFirst() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        ProjectListWidget widget = new ProjectListWidget(minecraft, 0, 0, 120, 60);
        Project alpha = createProject("alpha", "Alpha");
        Project beta = createProject("beta", "Beta");
        Project gamma = createProject("gamma", "Gamma");
        ModConfig.getInstance().toggleHudStarredProjectId(beta.getId());

        widget.setProjects(List.of(alpha, beta, gamma));

        GuiTestSupport.assertEquals(beta.getId(), widget.getProjectsForTest().get(0).getId(), "星标项目应排在列表前部");
    }

    /**
     * 校验当原选中项目不存在时会回退到默认项目。
     */
    private static void shouldFallbackToDefaultProjectWhenSelectionMissing() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        ProjectListWidget widget = new ProjectListWidget(minecraft, 0, 0, 120, 60);
        Project defaultProject = createProject("default-personal-project", "Default Project");
        Project regularProject = createProject("regular", "Regular");
        Project missing = createProject("missing", "Missing");
        widget.setSelectedProject(missing);

        widget.setProjects(List.of(regularProject, defaultProject));

        GuiTestSupport.assertEquals(defaultProject.getId(), widget.getSelectedProject().getId(), "缺失选中项时应回退到默认项目");
    }

    /**
     * 校验点击普通项目区域会触发选中回调。
     */
    private static void shouldInvokeCallbackWhenClickingProject() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        ProjectListWidget widget = new ProjectListWidget(minecraft, 0, 0, 120, 60);
        Project alpha = createProject("alpha", "Alpha");
        Project beta = createProject("beta", "Beta");
        AtomicReference<String> selectedId = new AtomicReference<>();
        widget.setProjects(List.of(alpha, beta));
        widget.setOnProjectSelected(project -> selectedId.set(project.getId()));

        boolean handled = widget.mouseClicked(GuiTestSupport.mouseEvent(10, 10), false);

        GuiTestSupport.assertTrue(handled, "点击项目区域应被组件处理");
        GuiTestSupport.assertEquals(alpha.getId(), selectedId.get(), "点击项目区域应触发对应项目的选中回调");
    }

    /**
     * 校验点击星标区域只切换星标，不触发项目选择回调。
     */
    private static void shouldToggleStarWithoutSelectingProject() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        ProjectListWidget widget = new ProjectListWidget(minecraft, 0, 0, 120, 60);
        Project alpha = createProject("alpha", "Alpha");
        Project beta = createProject("beta", "Beta");
        AtomicReference<String> selectedId = new AtomicReference<>();
        widget.setProjects(List.of(alpha, beta));
        widget.setOnProjectSelected(project -> selectedId.set(project.getId()));

        boolean handled = widget.mouseClicked(GuiTestSupport.mouseEvent(110, 30), false);

        GuiTestSupport.assertTrue(handled, "点击星标区域应被组件处理");
        GuiTestSupport.assertTrue(ModConfig.getInstance().isHudProjectStarred(beta.getId()), "点击星标区域后应切换项目星标状态");
        GuiTestSupport.assertNull(selectedId.get(), "点击星标区域不应触发项目选择回调");
    }

    /**
     * 校验滚动偏移会被裁剪到合法范围。
     */
    private static void shouldClampScrollOffset() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        ProjectListWidget widget = new ProjectListWidget(minecraft, 0, 0, 120, 40);
        widget.setProjects(List.of(
                createProject("p1", "P1"),
                createProject("p2", "P2"),
                createProject("p3", "P3"),
                createProject("p4", "P4")
        ));

        widget.setScrollOffset(99);

        GuiTestSupport.assertEquals(2, widget.getScrollOffset(), "滚动偏移应裁剪到列表最大范围");
    }

    /**
     * 创建测试项目对象，减少重复样板代码。
     *
     * @param id 项目 ID
     * @param name 项目名称
     * @return 测试项目对象
     */
    private static Project createProject(String id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        return project;
    }
}
