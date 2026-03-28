package com.todolist.gui;

import com.todolist.TodoListCommon;
import com.todolist.gui.testsupport.FakeClientConnection;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.gui.testsupport.RecordingClientOps;
import com.todolist.project.Project;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;
import java.util.UUID;

/**
 * 新增成员界面离线自测入口：覆盖成员过滤、搜索滚动与添加成员提交流程。
 */
public final class AddMemberScreenTestMain {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EXISTING_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ALICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID BOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID CAROL_ID = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final UUID DAVE_ID = UUID.fromString("00000000-0000-0000-0000-000000000014");
    private static final UUID ERIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final UUID FRANK_ID = UUID.fromString("00000000-0000-0000-0000-000000000016");
    private static final UUID GRACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000017");
    private static final UUID HEIDI_ID = UUID.fromString("00000000-0000-0000-0000-000000000018");
    private static final UUID IVAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000019");

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private AddMemberScreenTestMain() {
    }

    /**
     * 程序入口，串行执行新增成员界面的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("AddMemberScreenTestMain.shouldExcludeOwnerAndExistingMembersFromCandidateList", AddMemberScreenTestMain::shouldExcludeOwnerAndExistingMembersFromCandidateList);
        GuiTestSupport.runTestCase("AddMemberScreenTestMain.shouldFilterBySearchAndResetScrollOffset", AddMemberScreenTestMain::shouldFilterBySearchAndResetScrollOffset);
        GuiTestSupport.runTestCase("AddMemberScreenTestMain.shouldSendAddMemberAndOptimisticallyUpdateParent", AddMemberScreenTestMain::shouldSendAddMemberAndOptimisticallyUpdateParent);
    }

    /**
     * 校验候选列表会排除项目拥有者与已存在成员。
     */
    private static void shouldExcludeOwnerAndExistingMembersFromCandidateList() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project project = createTeamProject();
        AddMemberScreen screen = new AddMemberScreen(ScreenDriver.createParentScreen("parent"), project.getId());
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(EXISTING_MEMBER_ID, "member-one"),
                createPlayerInfo(ALICE_ID, "alice"),
                createPlayerInfo(BOB_ID, "bob"));

        ScreenDriver.init(minecraft, screen);

        List<String> names = screen.getFilteredPlayersForTest().stream()
                .map(info -> info.getProfile().getName())
                .toList();
        GuiTestSupport.assertEquals(List.of("alice", "bob"), names, "候选列表应排除拥有者和已存在成员");
    }

    /**
     * 校验搜索会刷新候选列表，并把滚动偏移重置到顶部。
     */
    private static void shouldFilterBySearchAndResetScrollOffset() {
        GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project project = createTeamProject();
        AddMemberScreen screen = new AddMemberScreen(ScreenDriver.createParentScreen("parent"), project.getId());
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(EXISTING_MEMBER_ID, "member-one"),
                createPlayerInfo(ALICE_ID, "alice"),
                createPlayerInfo(BOB_ID, "bob"),
                createPlayerInfo(CAROL_ID, "carol"),
                createPlayerInfo(DAVE_ID, "dave"),
                createPlayerInfo(ERIN_ID, "erin"),
                createPlayerInfo(FRANK_ID, "frank"),
                createPlayerInfo(GRACE_ID, "grace"),
                createPlayerInfo(HEIDI_ID, "heidi"),
                createPlayerInfo(IVAN_ID, "ivan"));

        ScreenDriver.init(minecraft, screen);
        for (int i = 0; i < 3; i++) {
            screen.mouseScrolled(screen.getListCenterXForTest(), screen.getListCenterYForTest(), -1.0D);
        }
        GuiTestSupport.assertTrue(screen.getScrollOffsetForTest() > 0, "滚轮向下滚动后应产生正向偏移");

        ScreenDriver.setText(screen.getSearchFieldForTest(), "gr");

        List<String> names = screen.getFilteredPlayersForTest().stream()
                .map(info -> info.getProfile().getName())
                .toList();
        GuiTestSupport.assertEquals(List.of("grace"), names, "搜索后应只保留匹配的候选成员");
        GuiTestSupport.assertEquals(0, screen.getScrollOffsetForTest(), "搜索刷新后应把滚动偏移重置到顶部");
    }

    /**
     * 校验点击成员按钮会发送添加成员请求，并同步更新父界面的项目成员列表。
     */
    private static void shouldSendAddMemberAndOptimisticallyUpdateParent() {
        RecordingClientOps ops = GuiTestSupport.resetState();
        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(OWNER_ID, "owner", false);
        Project project = createTeamProject();
        installOnlinePlayers(minecraft,
                createPlayerInfo(OWNER_ID, "owner"),
                createPlayerInfo(EXISTING_MEMBER_ID, "member-one"),
                createPlayerInfo(ALICE_ID, "alice"));
        ProjectSettingsScreen parent = new ProjectSettingsScreen(ScreenDriver.createParentScreen("root"), project);
        ScreenDriver.init(minecraft, parent);
        AddMemberScreen screen = new AddMemberScreen(parent, project.getId());
        ScreenDriver.init(minecraft, screen);

        Button firstPlayerButton = screen.getPlayerButtonsForTest()[0];
        ScreenDriver.click(firstPlayerButton);

        GuiTestSupport.assertEquals(List.of(project.getId() + ":" + ALICE_ID + ":alice"), ops.getAddMemberCalls(),
                "点击成员按钮后应向桥接层发送新增成员请求");
        GuiTestSupport.assertEquals(Project.ProjectRole.MEMBER, project.getMemberRole(ALICE_ID.toString()),
                "点击成员按钮后父界面应乐观更新项目成员");
        GuiTestSupport.assertEquals(parent, minecraft.getLastScreen(), "添加成员后应返回父界面");
    }

    /**
     * 创建测试用团队项目，并注册到全局项目管理器中。
     *
     * @return 测试项目
     */
    private static Project createTeamProject() {
        Project project = new Project("Team Alpha", Project.Scope.TEAM, OWNER_ID.toString());
        project.setId("team-alpha");
        project.addMember(EXISTING_MEMBER_ID.toString(), Project.ProjectRole.MEMBER, "member-one");
        TodoListCommon.getProjectManager().addProject(project);
        return project;
    }

    /**
     * 为假客户端安装在线玩家列表，供新增成员界面读取候选人。
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
