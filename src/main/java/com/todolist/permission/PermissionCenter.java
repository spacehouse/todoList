package com.todolist.permission;

public final class PermissionCenter {
    public enum Role {
        OP,
        PROJECT_MANAGER,
        LEAD,
        MEMBER
    }

    public enum ViewScope {
        PERSONAL,
        TEAM_UNASSIGNED,
        TEAM_ALL,
        TEAM_ASSIGNED
    }

    public enum Operation {
        ADD_TASK,
        DELETE_TASK,
        EDIT_TASK,
        TOGGLE_COMPLETE,
        CLAIM_TASK,
        ABANDON_TASK,
        ASSIGN_OTHERS,
        EDIT_PROJECT,
        DELETE_PROJECT,
        ADD_MEMBER,
        REMOVE_MEMBER,
        CHANGE_MEMBER_ROLE
    }

    public static final class Context {
        private final ViewScope viewScope;
        private final boolean completed;
        private final boolean assigned;
        private final boolean assigneeSelf;
        private final boolean targetSelf;
        private final boolean targetProjectManager;
        private final boolean projectMember;

        public Context(ViewScope viewScope, boolean completed, boolean assigned, boolean assigneeSelf) {
            this(viewScope, completed, assigned, assigneeSelf, false, false, true);
        }

        public Context(ViewScope viewScope, boolean completed, boolean assigned, boolean assigneeSelf, boolean targetSelf, boolean targetProjectManager) {
            this(viewScope, completed, assigned, assigneeSelf, targetSelf, targetProjectManager, true);
        }

        public Context(ViewScope viewScope, boolean completed, boolean assigned, boolean assigneeSelf, boolean targetSelf, boolean targetProjectManager, boolean projectMember) {
            this.viewScope = viewScope;
            this.completed = completed;
            this.assigned = assigned;
            this.assigneeSelf = assigneeSelf;
            this.targetSelf = targetSelf;
            this.targetProjectManager = targetProjectManager;
            this.projectMember = projectMember;
        }

        public ViewScope getViewScope() {
            return viewScope;
        }

        public boolean isCompleted() {
            return completed;
        }

        public boolean isAssigned() {
            return assigned;
        }

        public boolean isAssigneeSelf() {
            return assigneeSelf;
        }

        public boolean isTargetSelf() {
            return targetSelf;
        }

        public boolean isTargetProjectManager() {
            return targetProjectManager;
        }

        public boolean isProjectMember() {
            return projectMember;
        }
    }

    private PermissionCenter() {
    }

    public static boolean canPerform(Operation operation, Role role, Context context) {
        if (context == null) {
            return false;
        }
        if (operation == Operation.REMOVE_MEMBER && context.isTargetSelf()) {
            return false;
        }
        if (role == Role.OP) {
            return true;
        }
        if (context.getViewScope() != ViewScope.PERSONAL && !context.isProjectMember()) {
            switch (operation) {
                case ADD_TASK:
                case DELETE_TASK:
                case EDIT_TASK:
                case TOGGLE_COMPLETE:
                case CLAIM_TASK:
                case ABANDON_TASK:
                case ASSIGN_OTHERS:
                    return false;
                default:
                    break;
            }
        }
        switch (operation) {
            case EDIT_TASK:
                return canEdit(role, context);
            case DELETE_TASK:
                return canDelete(role, context);
            case TOGGLE_COMPLETE:
                return canToggleComplete(role, context);
            case ADD_TASK:
                return canAdd(role, context);
            case CLAIM_TASK:
                return canClaim(role, context);
            case ABANDON_TASK:
                return canAbandon(role, context);
            case ASSIGN_OTHERS:
                return canAssignOthers(role, context);
            case EDIT_PROJECT:
                return canEditProject(role);
            case DELETE_PROJECT:
                return canDeleteProject(role);
            case ADD_MEMBER:
                return canAddMember(role);
            case REMOVE_MEMBER:
                return canRemoveMember(role, context);
            case CHANGE_MEMBER_ROLE:
                return canChangeMemberRole(role, context);
            default:
                return false;
        }
    }

    private static boolean canEdit(Role role, Context context) {
        if (context.isCompleted()) {
            return false;
        }
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return true;
        }
        return role == Role.PROJECT_MANAGER || role == Role.LEAD;
    }

    private static boolean canDelete(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            if (context.isCompleted()) {
                return true;
            }
            return canEdit(role, context);
        }
        return role == Role.PROJECT_MANAGER || role == Role.LEAD;
    }

    private static boolean canToggleComplete(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return true;
        }
        if (role == Role.PROJECT_MANAGER || role == Role.LEAD) {
            return true;
        }
        if (context.getViewScope() != ViewScope.TEAM_ASSIGNED) {
            return false;
        }
        return context.isAssigneeSelf();
    }

    private static boolean canAdd(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return true;
        }
        return role == Role.PROJECT_MANAGER || role == Role.LEAD;
    }

    private static boolean canClaim(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return false;
        }
        if (context.isCompleted()) {
            return false;
        }
        if (role == Role.PROJECT_MANAGER || role == Role.LEAD) {
            return !context.isAssigned();
        }
        return context.getViewScope() == ViewScope.TEAM_UNASSIGNED && !context.isAssigned();
    }

    private static boolean canAbandon(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return false;
        }
        if (context.isCompleted()) {
            return false;
        }
        if (role == Role.PROJECT_MANAGER || role == Role.LEAD) {
            return true;
        }
        if (context.getViewScope() != ViewScope.TEAM_ASSIGNED) {
            return false;
        }
        return context.isAssigneeSelf();
    }

    private static boolean canAssignOthers(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return false;
        }
        if (context.isCompleted()) {
            return false;
        }
        return role == Role.PROJECT_MANAGER || role == Role.LEAD;
    }

    private static boolean canEditProject(Role role) {
        return role == Role.PROJECT_MANAGER;
    }

    private static boolean canDeleteProject(Role role) {
        return role == Role.PROJECT_MANAGER;
    }

    private static boolean canAddMember(Role role) {
        return role == Role.PROJECT_MANAGER || role == Role.LEAD;
    }

    private static boolean canRemoveMember(Role role, Context context) {
        if (role == Role.PROJECT_MANAGER) {
            return !context.isTargetSelf();
        }
        if (role == Role.LEAD) {
            return !context.isTargetSelf() && !context.isTargetProjectManager();
        }
        return false;
    }

    private static boolean canChangeMemberRole(Role role, Context context) {
        if (role == Role.PROJECT_MANAGER) {
            return !context.isTargetSelf();
        }
        if (role == Role.LEAD) {
            return !context.isTargetSelf() && !context.isTargetProjectManager();
        }
        return false;
    }
}

