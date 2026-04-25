package com.todolist.gui;

import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;
import com.todolist.task.Task;

import net.minecraft.client.Minecraft;

/**
 * TodoScreen 权限支持类，集中处理角色解析、成员关系与任务操作权限判定。
 */
final class TodoScreenPermissionSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenPermissionSupport() {
    }

    static boolean isCurrentPlayerAssignee(Minecraft minecraft, Task task) {
        if (task == null || minecraft == null || minecraft.player == null) {
            return false;
        }
        String uuid = minecraft.player.getUUID().toString();
        String assignee = task.getAssigneeUuid();
        return assignee != null && assignee.equals(uuid);
    }

    static Role getCurrentRole(Minecraft minecraft, Project currentProject) {
        if (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2)) {
            return Role.OP;
        }
        if (minecraft == null || minecraft.player == null) {
            return Role.MEMBER;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return Role.MEMBER;
        }
        String uuid = minecraft.player.getUUID().toString();
        if (uuid.equals(currentProject.getOwnerUuid())) {
            return Role.PROJECT_MANAGER;
        }
        Project.ProjectRole projectRole = currentProject.getMemberRole(uuid);
        if (projectRole == Project.ProjectRole.LEAD) {
            return Role.LEAD;
        }
        return Role.MEMBER;
    }

    static boolean isCurrentPlayerProjectMember(Minecraft minecraft, Project currentProject) {
        if (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2)) {
            return true;
        }
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        if (currentProject == null || currentProject.getScope() == Project.Scope.PERSONAL) {
            return true;
        }
        String uuid = minecraft.player.getUUID().toString();
        if (uuid.equals(currentProject.getOwnerUuid())) {
            return true;
        }
        return currentProject.getMemberRole(uuid) != null;
    }

    static boolean canTaskOperation(Operation operation,
                                    Task task,
                                    Role role,
                                    ViewScope scope,
                                    boolean projectMember,
                                    boolean assigneeSelf,
                                    boolean allowAllPlayersClaimComplete) {
        if (task == null) {
            return false;
        }
        boolean isCompleted = task.isCompleted();
        boolean isAssigned = task.getAssigneeUuid() != null && !task.getAssigneeUuid().isEmpty();
        Context context = new Context(
                scope,
                isCompleted,
                isAssigned,
                assigneeSelf,
                false,
                false,
                projectMember,
                false,
                allowAllPlayersClaimComplete
        );
        return PermissionCenter.canPerform(operation, role, context);
    }

    static ViewScope resolveViewScope(String viewModeName) {
        if ("PERSONAL".equals(viewModeName)) {
            return ViewScope.PERSONAL;
        }
        if ("TEAM_UNASSIGNED".equals(viewModeName)) {
            return ViewScope.TEAM_UNASSIGNED;
        }
        if ("TEAM_ASSIGNED".equals(viewModeName)) {
            return ViewScope.TEAM_ASSIGNED;
        }
        return ViewScope.TEAM_ALL;
    }

    static String getCurrentPlayerUuid(Minecraft minecraft) {
        return minecraft == null || minecraft.player == null ? "" : minecraft.player.getUUID().toString();
    }

    static boolean canTaskReorder(Project currentProject,
                                  String viewModeName,
                                  Role role,
                                  boolean projectMember,
                                  boolean allowMemberCreate,
                                  boolean allowAllPlayersClaimComplete) {
        if (currentProject == null) {
            return false;
        }
        if ("PERSONAL".equals(viewModeName)) {
            return true;
        }
        ViewScope scope = resolveViewScope(viewModeName);
        Context context = new Context(scope, false, false, false, false, false, projectMember, allowMemberCreate, allowAllPlayersClaimComplete);
        return PermissionCenter.canPerform(Operation.EDIT_TASK, role, context);
    }

    static boolean canTaskReorderInView(Minecraft minecraft, Project currentProject, String viewModeName) {
        return canTaskReorder(
                currentProject,
                viewModeName,
                getCurrentRole(minecraft, currentProject),
                isCurrentPlayerProjectMember(minecraft, currentProject),
                currentProject != null && currentProject.isAllowMemberCreate(),
                currentProject != null && currentProject.isAllowAllPlayersClaimComplete()
        );
    }

    static boolean canAddTaskInView(Minecraft minecraft, Project currentProject, String viewModeName) {
        if (currentProject == null) {
            return false;
        }
        if ("PERSONAL".equals(viewModeName)) {
            return true;
        }
        Role role = getCurrentRole(minecraft, currentProject);
        ViewScope scope = resolveViewScope(viewModeName);
        boolean projectMember = isCurrentPlayerProjectMember(minecraft, currentProject);
        boolean allowMemberCreate = currentProject.isAllowMemberCreate();
        boolean allowAllPlayersClaimComplete = currentProject.isAllowAllPlayersClaimComplete();
        Context context = new Context(scope, false, false, false, false, false, projectMember, allowMemberCreate, allowAllPlayersClaimComplete);
        return PermissionCenter.canPerform(Operation.ADD_TASK, role, context);
    }

    static boolean canTaskOperationInView(Operation operation,
                                          Task task,
                                          Minecraft minecraft,
                                          Project currentProject,
                                          String viewModeName) {
        return canTaskOperation(
                operation,
                task,
                getCurrentRole(minecraft, currentProject),
                resolveViewScope(viewModeName),
                isCurrentPlayerProjectMember(minecraft, currentProject),
                isCurrentPlayerAssignee(minecraft, task),
                currentProject != null && currentProject.isAllowAllPlayersClaimComplete()
        );
    }

    static String validateClaimTask(Task task, Minecraft minecraft, String viewModeName) {
        if (task == null || minecraft == null || minecraft.player == null) {
            return null;
        }
        if ("PERSONAL".equals(viewModeName)) {
            return "message.todolist.assign_only_team";
        }
        String uuid = minecraft.player.getUUID().toString();
        String assignee = task.getAssigneeUuid();
        if (assignee != null && !assignee.isEmpty()) {
            if (assignee.equals(uuid)) {
                return "message.todolist.already_assigned_to_me";
            }
            return "message.todolist.already_assigned";
        }
        return null;
    }

    static String validateAbandonTask(Task task,
                                      Minecraft minecraft,
                                      Project currentProject,
                                      String viewModeName) {
        if (task == null || minecraft == null || minecraft.player == null) {
            return null;
        }
        if ("PERSONAL".equals(viewModeName)) {
            return "message.todolist.assign_only_team";
        }
        if (!canTaskOperationInView(Operation.ABANDON_TASK, task, minecraft, currentProject, viewModeName)) {
            return "message.todolist.no_permission_toggle_team";
        }
        return null;
    }
}
