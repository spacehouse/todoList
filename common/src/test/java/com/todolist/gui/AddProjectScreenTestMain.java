package com.todolist.gui;

import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import net.minecraft.client.gui.screens.Screen;

/**
 * 新建项目界面离线自测入口：覆盖团队能力开关、范围切换、空输入防呆与创建提交。
 */
public final class AddProjectScreenTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private AddProjectScreenTestMain() {
    }

    /**
     * 程序入口，串行执行新建项目界面的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("AddProjectScreenTestMain.shouldForcePersonalScopeWhenTeamProjectsDisabled", AddProjectScreenTestMain::shouldForcePersonalScopeWhenTeamProjectsDisabled);
        GuiTestSupport.runTestCase("AddProjectScreenTestMain.shouldToggleScopeWhenTeamProjectsEnabled", AddProjectScreenTestMain::shouldToggleScopeWhenTeamProjectsEnabled);
        GuiTestSupport.runTestCase("AddProjectScreenTestMain.shouldRejectEmptyProjectName", AddProjectScreenTestMain::shouldRejectEmptyProjectName);
        GuiTestSupport.runTestCase("AddProjectScreenTestMain.shouldCreateProjectAndCloseOnEnter", AddProjectScreenTestMain::shouldCreateProjectAndCloseOnEnter);
    }

    /**
     * 校验团队能力关闭时界面会强制回退到个人项目。
     */
    private static void shouldForcePersonalScopeWhenTeamProjectsDisabled() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        ops.setTeamProjectsEnabled(false);
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        AddProjectScreen screen = new AddProjectScreen(parent, Project.Scope.TEAM);
        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertEquals(Project.Scope.PERSONAL, screen.getScopeForTest(), "团队能力关闭时应强制使用个人项目范围");
        GuiTestSupport.assertFalse(screen.getScopeButtonForTest().active, "团队能力关闭时范围切换按钮应禁用");
    }

    /**
     * 校验团队能力开启时可以通过按钮切换项目范围。
     */
    private static void shouldToggleScopeWhenTeamProjectsEnabled() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        ops.setTeamProjectsEnabled(true);
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        AddProjectScreen screen = new AddProjectScreen(parent, Project.Scope.PERSONAL);
        ScreenDriver.init(minecraft, screen);

        ScreenDriver.click(screen.getScopeButtonForTest());

        GuiTestSupport.assertEquals(Project.Scope.TEAM, screen.getScopeForTest(), "点击范围切换按钮后应切换到团队项目");
    }

    /**
     * 校验空项目名不会发送创建请求，也不会关闭界面。
     */
    private static void shouldRejectEmptyProjectName() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        AddProjectScreen screen = new AddProjectScreen(parent, Project.Scope.PERSONAL);
        ScreenDriver.init(minecraft, screen);

        ScreenDriver.setText(screen.getNameFieldForTest(), "   ");
        ScreenDriver.click(screen.getCreateButtonForTest());

        GuiTestSupport.assertEquals(0, ops.getAddProjectCalls().size(), "空项目名不应发送创建请求");
        GuiTestSupport.assertNull(minecraft.getLastScreen(), "空项目名不应触发关闭返回");
    }

    /**
     * 校验回车创建会发送项目并返回父界面。
     */
    private static void shouldCreateProjectAndCloseOnEnter() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        ops.setTeamProjectsEnabled(true);
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft();
        Screen parent = ScreenDriver.createParentScreen("parent");
        AddProjectScreen screen = new AddProjectScreen(parent, Project.Scope.TEAM);
        ScreenDriver.init(minecraft, screen);

        ScreenDriver.setText(screen.getNameFieldForTest(), "Roadmap");
        ScreenDriver.pressEnter(screen);

        GuiTestSupport.assertEquals(1, ops.getAddProjectCalls().size(), "按回车创建时应发送新增项目请求");
        GuiTestSupport.assertEquals("Roadmap", ops.getAddProjectCalls().get(0).getName(), "创建请求应包含输入的项目名");
        GuiTestSupport.assertEquals(Project.Scope.TEAM, ops.getAddProjectCalls().get(0).getScope(), "创建请求应保留当前范围");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "创建完成后应返回父界面");
    }
}
