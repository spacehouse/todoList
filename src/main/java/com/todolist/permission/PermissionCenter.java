package com.todolist.permission;

public final class PermissionCenter {
    public enum Role {
        ADMIN,
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
        ASSIGN_OTHERS
    }

    public static final class Context {
        private final ViewScope viewScope;
        private final boolean completed;
        private final boolean assigned;
        private final boolean assigneeSelf;

        public Context(ViewScope viewScope, boolean completed, boolean assigned, boolean assigneeSelf) {
            this.viewScope = viewScope;
            this.completed = completed;
            this.assigned = assigned;
            this.assigneeSelf = assigneeSelf;
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
    }

    private PermissionCenter() {
    }

    public static boolean canPerform(Operation operation, Role role, Context context) {
        if (context == null) {
            return false;
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
        return role == Role.ADMIN;
    }

    private static boolean canDelete(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            if (context.isCompleted()) {
                return true;
            }
            return canEdit(role, context);
        }
        return role == Role.ADMIN;
    }

    private static boolean canToggleComplete(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return true;
        }
        if (role == Role.ADMIN) {
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
        return role == Role.ADMIN;
    }

    private static boolean canClaim(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return false;
        }
        if (context.isCompleted()) {
            return false;
        }
        if (role == Role.ADMIN) {
            return !context.isAssigned();
        }
        return context.getViewScope() == ViewScope.TEAM_UNASSIGNED;
    }

    private static boolean canAbandon(Role role, Context context) {
        if (context.getViewScope() == ViewScope.PERSONAL) {
            return false;
        }
        if (context.isCompleted()) {
            return false;
        }
        if (role == Role.ADMIN) {
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
        return role == Role.ADMIN;
    }
}

