package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.gui.testsupport.FakeClientConnection;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;
import java.util.UUID;

/**
 * 项目设置界面离线自测入口：覆盖权限分支、保存语义与成员列表刷新行为。
 */
public final class ProjectSettingsScreenTestMain {
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID BOB_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ProjectSettingsScreenTestMain() {
    }

    /**
     * 程序入口，串行执行项目设置界面的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("ProjectSettingsScreenTestMain.shouldAllowProjectManagerToSaveTeamProject", ProjectSettingsScreenTestMain::shouldAllowProjectManagerToSaveTeamProject);
        GuiTestSupport.runTestCase("ProjectSettingsScreenTestMain.shouldDisableEditingForRegularMember", ProjectSettingsScreenTestMain::shouldDisableEditingForRegularMember);
        GuiTestSupport.runTestCase("ProjectSettingsScreenTestMain.shouldRefreshVisibleMembersAfterProjectChanged", ProjectSettingsScreenTestMain::shouldRefreshVisibleMembersAfterProjectChanged);
    }

    /**
     * 校验项目管理者可以编辑团队项目并向桥接层发送更新请求。
     */
    private static void shouldAllowProjectManagerToSaveTeamProject() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project project = createTeamProject();
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(MEMBER_ID, "alice"));
        ProjectSettingsScreen screen = new ProjectSettingsScreen(ScreenDriver.createParentScreen("parent"), project);

        ScreenDriver.init(minecraft, screen);
        ScreenDriver.setText(screen.getNameFieldForTest(), "Team Rocket");
        ScreenDriver.click(screen.getAllowMemberCreateButtonForTest());
        ScreenDriver.click(screen.getSaveButtonForTest());

        GuiTestSupport.assertTrue(screen.canEditForTest(), "项目管理者应具有项目编辑权限");
        GuiTestSupport.assertTrue(screen.getAddMemberButtonForTest().active, "项目管理者应可打开新增成员入口");
        GuiTestSupport.assertEquals("Team Rocket", project.getName(), "保存后应更新项目名称");
        GuiTestSupport.assertTrue(project.isAllowMemberCreate(), "保存后应写回允许成员创建任务开关");
        GuiTestSupport.assertEquals(1, ops.getUpdateProjectCalls().size(), "保存后应向桥接层发送一次项目更新");
        GuiTestSupport.assertEquals("Team Rocket", ops.getUpdateProjectCalls().get(0).getName(), "桥接层应收到更新后的项目名称");
    }

    /**
     * 校验普通成员进入团队项目设置时不能编辑项目，也不能新增成员。
     */
    private static void shouldDisableEditingForRegularMember() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(MEMBER_ID, "alice", false);
        Project project = createTeamProject();
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(MEMBER_ID, "alice"));
        ProjectSettingsScreen screen = new ProjectSettingsScreen(ScreenDriver.createParentScreen("parent"), project);

        ScreenDriver.init(minecraft, screen);

        GuiTestSupport.assertFalse(screen.canEditForTest(), "普通成员不应具有项目编辑权限");
        GuiTestSupport.assertFalse(screen.getSaveButtonForTest().active, "普通成员不应能触发保存");
        GuiTestSupport.assertFalse(screen.getAllowMemberCreateButtonForTest().active, "普通成员不应能切换成员创建开关");
        GuiTestSupport.assertFalse(screen.getAddMemberButtonForTest().active, "普通成员不应能新增项目成员");
    }

    /**
     * 校验项目更新事件到达后，会按当前搜索条件刷新成员列表。
     */
    private static void shouldRefreshVisibleMembersAfterProjectChanged() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project project = createTeamProject();
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(MEMBER_ID, "alice"),
                createPlayerInfo(BOB_ID, "bob"));
        ProjectSettingsScreen screen = new ProjectSettingsScreen(ScreenDriver.createParentScreen("parent"), project);

        ScreenDriver.init(minecraft, screen);
        ScreenDriver.setText(screen.getMemberSearchFieldForTest(), "bo");
        GuiTestSupport.assertEquals(List.of(), screen.getVisibleMemberNamesForTest(), "更新前不应显示不匹配搜索条件的成员");

        Project updatedProject = createTeamProject();
        updatedProject.addMember(BOB_ID.toString(), Project.ProjectRole.MEMBER, "bob");
        TodoListCommon.getProjectManager().updateProject(updatedProject);

        GuiTestSupport.assertEquals(updatedProject, screen.getProjectForTest(), "收到项目更新后应替换为最新项目对象");
        GuiTestSupport.assertEquals(List.of("bob"), screen.getVisibleMemberNamesForTest(), "项目更新后应按当前搜索条件刷新成员列表");
    }

    /**
     * 创建测试用团队项目，并注册到全局项目管理器中。
     *
     * @return 测试项目
     */
    private static Project createTeamProject() {
        Project project = new Project("Team Alpha", Project.Scope.TEAM, OWNER_ID.toString());
        project.setId("team-alpha");
        project.addMember(MEMBER_ID.toString(), Project.ProjectRole.MEMBER, "alice");
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 为假客户端安装在线玩家列表，供成员名称解析与搜索测试使用。
     *
     * @param minecraft 假客户端
     * @param players 在线玩家列表
     */
    private static void installOnlinePlayers(FakeMinecraftClient minecraft, PlayerInfo... players) {
        FakeClientConnection connection = (FakeClientConnection) minecraft.getConnection();
        connection.setOnlinePlayers(List.of(players));
    }

    /**
     * 创建测试玩家信息对象，减少重复样板代码。
     *
     * @param uuid 玩家 UUID
     * @param name 玩家名称
     * @return 测试玩家信息对象
     */
    private static PlayerInfo createPlayerInfo(UUID uuid, String name) {
        return GuiTestSupport.createPlayerInfo(uuid, name);
    }
}
