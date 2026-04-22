package com.todolist.gui;

import com.todolist.permission.PermissionCenter;
import com.todolist.permission.PermissionCenter.Context;
import com.todolist.permission.PermissionCenter.Operation;
import com.todolist.permission.PermissionCenter.Role;
import com.todolist.permission.PermissionCenter.ViewScope;
import com.todolist.project.Project;

import net.minecraft.client.gui.components.Button;

/**
 * TodoScreen 项目操作按钮支持类，集中处理底部按钮状态判定。
 */
final class TodoScreenProjectActionSupport {
    static final class ProjectActionState {
        final String editMessageKey;
        final boolean editActive;
        final boolean deleteVisible;
        final boolean deleteActive;
        final boolean joinVisible;
        final boolean joinActive;

        ProjectActionState(String editMessageKey,
                           boolean editActive,
                           boolean deleteVisible,
                           boolean deleteActive,
                           boolean joinVisible,
                           boolean joinActive) {
            this.editMessageKey = editMessageKey;
            this.editActive = editActive;
            this.deleteVisible = deleteVisible;
            this.deleteActive = deleteActive;
            this.joinVisible = joinVisible;
            this.joinActive = joinActive;
        }
    }

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenProjectActionSupport() {
    }

    static ProjectActionState resolve(Project currentProject,
                                      Role role,
                                      boolean projectMember,
                                      boolean canDeleteCurrentProject) {
        if (currentProject == null) {
            return new ProjectActionState("gui.todolist.edit", false, true, false, false, false);
        }
        if (currentProject.getScope() != Project.Scope.TEAM) {
            return new ProjectActionState("gui.todolist.edit", true, true, canDeleteCurrentProject, false, false);
        }
        boolean canEdit = PermissionCenter.canPerform(
                Operation.EDIT_PROJECT,
                role,
                new Context(ViewScope.TEAM_ALL, false, false, false)
        );
        if (!projectMember) {
            return new ProjectActionState(
                    canEdit ? "gui.todolist.edit" : "gui.todolist.project.view",
                    true,
                    false,
                    false,
                    true,
                    true
            );
        }
        return new ProjectActionState(
                canEdit ? "gui.todolist.edit" : "gui.todolist.project.view",
                true,
                true,
                canDeleteCurrentProject,
                false,
                false
        );
    }

    static boolean canDeleteCurrentProject(Project currentProject, String currentPlayerUuid, Role role) {
        if (currentProject == null) {
            return false;
        }
        if (currentProject.isDefaultPersonalProject() || currentProject.isDefaultTeamProject()) {
            return false;
        }
        if (currentProject.getScope() == Project.Scope.PERSONAL) {
            String owner = currentProject.getOwnerUuid();
            return owner == null || owner.isEmpty() || owner.equals(currentPlayerUuid);
        }
        return PermissionCenter.canPerform(
                Operation.DELETE_PROJECT,
                role,
                new Context(ViewScope.TEAM_ALL, false, false, false)
        );
    }

    static boolean isTeamProjectBlocked(Project currentProject, boolean teamProjectsEnabled) {
        return currentProject != null
                && !teamProjectsEnabled
                && currentProject.getScope() == Project.Scope.TEAM;
    }

    static void applySidebarVisibility(boolean sidebarVisible,
                                       Button addProjectBtn,
                                       Button editProjectBtn,
                                       Button deleteProjectBtn,
                                       Button applyJoinProjectBtn) {
        if (addProjectBtn != null) {
            addProjectBtn.visible = sidebarVisible;
            addProjectBtn.active = sidebarVisible;
        }
        if (editProjectBtn != null) {
            editProjectBtn.visible = sidebarVisible;
            editProjectBtn.active = sidebarVisible && editProjectBtn.active;
        }
        if (deleteProjectBtn != null) {
            deleteProjectBtn.visible = sidebarVisible && deleteProjectBtn.visible;
            deleteProjectBtn.active = sidebarVisible && deleteProjectBtn.active;
        }
        if (applyJoinProjectBtn != null) {
            applyJoinProjectBtn.visible = sidebarVisible && applyJoinProjectBtn.visible;
            applyJoinProjectBtn.active = sidebarVisible && applyJoinProjectBtn.active;
        }
    }
}
