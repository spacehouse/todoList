package com.todolist.permission;

import com.todolist.gui.testsupport.GuiTestSupport;

import java.util.List;

/**
 * PermissionCenter 自测：角色×操作×上下文判定矩阵的直接断言。
 * 权限中枢是全部写操作的判定源头，此前仅有 2 条间接用例。
 */
public final class PermissionCenterTestMain {
    private PermissionCenterTestMain() {
    }

    private static final List<PermissionCenter.Operation> ALL_OPS = List.of(
            PermissionCenter.Operation.values());

    /**
     * 执行权限矩阵自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("PermissionCenterTestMain.shouldGrantOpEverythingExceptSelfRemoval", PermissionCenterTestMain::shouldGrantOpEverythingExceptSelfRemoval);
        GuiTestSupport.runTestCase("PermissionCenterTestMain.shouldGateTeamOpsForNonMembers", PermissionCenterTestMain::shouldGateTeamOpsForNonMembers);
        GuiTestSupport.runTestCase("PermissionCenterTestMain.shouldEnforcePersonalScopeSemantics", PermissionCenterTestMain::shouldEnforcePersonalScopeSemantics);
        GuiTestSupport.runTestCase("PermissionCenterTestMain.shouldEnforceTeamEditAndToggleMatrix", PermissionCenterTestMain::shouldEnforceTeamEditAndToggleMatrix);
        GuiTestSupport.runTestCase("PermissionCenterTestMain.shouldEnforceClaimAbandonSemantics", PermissionCenterTestMain::shouldEnforceClaimAbandonSemantics);
        GuiTestSupport.runTestCase("PermissionCenterTestMain.shouldEnforceMemberManagementMatrix", PermissionCenterTestMain::shouldEnforceMemberManagementMatrix);
    }

    private static PermissionCenter.Context teamCtx(boolean member) {
        return new PermissionCenter.Context(PermissionCenter.ViewScope.TEAM_ALL, false, false, false,
                false, false, member, false, false);
    }

    private static void shouldGrantOpEverythingExceptSelfRemoval() {
        PermissionCenter.Context ctx = teamCtx(true);
        for (PermissionCenter.Operation op : ALL_OPS) {
            // ctx 的 targetSelf=false，OP 对全部操作放行（含 REMOVE_MEMBER 他人）
            GuiTestSupport.assertTrue(PermissionCenter.canPerform(op, PermissionCenter.Role.OP, ctx),
                    "OP 应放行 " + op);
        }
        PermissionCenter.Context selfRemove = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ALL, false, false, false,
                true, false, true, false, false);
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.REMOVE_MEMBER,
                        PermissionCenter.Role.OP, selfRemove),
                "OP 移除自己也应被拒绝（目标为自己）");
    }

    private static void shouldGateTeamOpsForNonMembers() {
        PermissionCenter.Context nonMember = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ALL, false, false, false,
                false, false, false, false, false);
        List<PermissionCenter.Operation> blocked = List.of(
                PermissionCenter.Operation.ADD_TASK, PermissionCenter.Operation.DELETE_TASK,
                PermissionCenter.Operation.EDIT_TASK, PermissionCenter.Operation.ASSIGN_OTHERS);
        for (PermissionCenter.Role role : List.of(PermissionCenter.Role.MEMBER, PermissionCenter.Role.LEAD)) {
            for (PermissionCenter.Operation op : blocked) {
                GuiTestSupport.assertFalse(PermissionCenter.canPerform(op, role, nonMember),
                        "非项目成员的 " + role + " 不应执行 " + op);
            }
        }
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.TOGGLE_COMPLETE,
                        PermissionCenter.Role.MEMBER, nonMember),
                "非成员默认不可勾选完成");
        PermissionCenter.Context allowAll = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ALL, false, false, false,
                false, false, false, false, true);
        // allowAll 只放行"非项目成员"的第一道闸，勾选仍受视图规则约束（TEAM_ALL 的 MEMBER 不可勾选）
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.TOGGLE_COMPLETE,
                        PermissionCenter.Role.MEMBER, allowAll),
                "allowAll 放行闸门后仍受视图规则约束");
        // 放行的真实效果：CLAIM 在 TEAM_UNASSIGNED 视图下对非成员可用
        PermissionCenter.Context allowAllUnassigned = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_UNASSIGNED, false, false, false,
                false, false, false, false, true);
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.CLAIM_TASK,
                        PermissionCenter.Role.MEMBER, allowAllUnassigned),
                "allowAll 开启后非成员可在待领取视图领取");
    }

    private static void shouldEnforcePersonalScopeSemantics() {
        PermissionCenter.Context personal = new PermissionCenter.Context(
                PermissionCenter.ViewScope.PERSONAL, false, false, false,
                false, false, false, false, false);
        // OP 顶层短路全放行（第一个用例已覆盖），此处验证 PM/LEAD/MEMBER 的视图语义
        for (PermissionCenter.Role role : List.of(PermissionCenter.Role.PROJECT_MANAGER,
                PermissionCenter.Role.LEAD, PermissionCenter.Role.MEMBER)) {
            GuiTestSupport.assertTrue(
                    PermissionCenter.canPerform(PermissionCenter.Operation.ADD_TASK, role, personal),
                    role + " 个人视图应可新增");
            GuiTestSupport.assertTrue(
                    PermissionCenter.canPerform(PermissionCenter.Operation.EDIT_TASK, role, personal),
                    role + " 个人视图应可编辑");
            GuiTestSupport.assertTrue(
                    PermissionCenter.canPerform(PermissionCenter.Operation.TOGGLE_COMPLETE, role, personal),
                    role + " 个人视图应可勾选");
            GuiTestSupport.assertFalse(
                    PermissionCenter.canPerform(PermissionCenter.Operation.CLAIM_TASK, role, personal),
                    role + " 个人视图不应出现领取");
            GuiTestSupport.assertFalse(
                    PermissionCenter.canPerform(PermissionCenter.Operation.ABANDON_TASK, role, personal),
                    role + " 个人视图不应出现取消领取");
        }
        PermissionCenter.Context personalDone = new PermissionCenter.Context(
                PermissionCenter.ViewScope.PERSONAL, true, false, false,
                false, false, false, false, false);
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.DELETE_TASK,
                        PermissionCenter.Role.MEMBER, personalDone),
                "个人已完成任务应可删除");
    }

    private static void shouldEnforceTeamEditAndToggleMatrix() {
        PermissionCenter.Context member = teamCtx(true);
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.EDIT_TASK,
                        PermissionCenter.Role.MEMBER, member),
                "团队视图 MEMBER 不应可编辑");
        for (PermissionCenter.Role role : List.of(PermissionCenter.Role.PROJECT_MANAGER,
                PermissionCenter.Role.LEAD)) {
            GuiTestSupport.assertTrue(
                    PermissionCenter.canPerform(PermissionCenter.Operation.EDIT_TASK, role, member),
                    role + " 团队视图应可编辑");
            GuiTestSupport.assertTrue(
                    PermissionCenter.canPerform(PermissionCenter.Operation.TOGGLE_COMPLETE, role, member),
                    role + " 团队视图应可勾选");
        }
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.TOGGLE_COMPLETE,
                        PermissionCenter.Role.MEMBER, member),
                "TEAM_ALL 的 MEMBER 不应可勾选");
        PermissionCenter.Context assignedSelf = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ASSIGNED, false, true, true,
                false, false, true, false, false);
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.TOGGLE_COMPLETE,
                        PermissionCenter.Role.MEMBER, assignedSelf),
                "TEAM_ASSIGNED 的本人领取任务应可勾选");
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.ABANDON_TASK,
                        PermissionCenter.Role.MEMBER, assignedSelf),
                "TEAM_ASSIGNED 的本人领取任务应可取消领取");
        PermissionCenter.Context assignedOther = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ASSIGNED, false, true, false,
                false, false, true, false, false);
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.TOGGLE_COMPLETE,
                        PermissionCenter.Role.MEMBER, assignedOther),
                "TEAM_ASSIGNED 的他人任务不应可勾选");
    }

    private static void shouldEnforceClaimAbandonSemantics() {
        PermissionCenter.Context unassigned = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_UNASSIGNED, false, false, false,
                false, false, true, false, false);
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.CLAIM_TASK,
                        PermissionCenter.Role.MEMBER, unassigned),
                "TEAM_UNASSIGNED 未领取应可被 MEMBER 领取");
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.ABANDON_TASK,
                        PermissionCenter.Role.MEMBER, unassigned),
                "TEAM_UNASSIGNED 不应可取消领取");
        PermissionCenter.Context claimed = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_UNASSIGNED, false, true, false,
                false, false, true, false, false);
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.CLAIM_TASK,
                        PermissionCenter.Role.PROJECT_MANAGER, claimed),
                "已领取任务不应再次领取");
        PermissionCenter.Context done = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ALL, true, false, false,
                false, false, true, false, false);
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.CLAIM_TASK,
                        PermissionCenter.Role.PROJECT_MANAGER, done),
                "已完成任务不应可领取");
    }

    private static void shouldEnforceMemberManagementMatrix() {
        PermissionCenter.Context ctx = teamCtx(true);
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.EDIT_PROJECT,
                        PermissionCenter.Role.PROJECT_MANAGER, ctx),
                "PM 应可编辑项目");
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.EDIT_PROJECT,
                        PermissionCenter.Role.LEAD, ctx),
                "LEAD 不应可编辑项目设置");
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.ADD_MEMBER,
                        PermissionCenter.Role.LEAD, ctx),
                "LEAD 应可添加成员");
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.ADD_MEMBER,
                        PermissionCenter.Role.MEMBER, ctx),
                "MEMBER 不应可添加成员");
        PermissionCenter.Context targetPm = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ALL, false, false, false,
                false, true, true, false, false);
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.REMOVE_MEMBER,
                        PermissionCenter.Role.PROJECT_MANAGER, targetPm),
                "PM 应可移除其他 PM");
        GuiTestSupport.assertFalse(
                PermissionCenter.canPerform(PermissionCenter.Operation.REMOVE_MEMBER,
                        PermissionCenter.Role.LEAD, targetPm),
                "LEAD 不应移除 PM");
        PermissionCenter.Context targetMember = new PermissionCenter.Context(
                PermissionCenter.ViewScope.TEAM_ALL, false, false, false,
                false, false, true, false, false);
        GuiTestSupport.assertTrue(
                PermissionCenter.canPerform(PermissionCenter.Operation.CHANGE_MEMBER_ROLE,
                        PermissionCenter.Role.LEAD, targetMember),
                "LEAD 应可变更成员角色");
    }
}
