package com.todolist.client;

import com.todolist.config.ModConfig;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.util.List;

/**
 * 客户端能力桥接层：在 common 侧通过 {@link ClientOps} 调用各平台的客户端实现。
 */
public final class ClientBridge {
    /**
     * 由平台端（Fabric/Forge）注入的客户端操作集合。
     */
    public interface ClientOps {
        /**
         * 获取团队任务管理器（仅客户端本地缓存，用于渲染与交互）。
         */
        TaskManager getTeamTaskManager();

        /**
         * 当前环境是否启用团队项目能力（例如是否连接到支持服务端）。
         */
        boolean isTeamProjectsEnabled();

        /**
         * 获取当前激活的项目 ID（用于 UI 记忆与默认选择）。
         */
        String getActiveProjectId();

        /**
         * 设置当前激活的项目 ID。
         */
        void setActiveProjectId(String projectId);

        /**
         * 将当前激活的项目 ID 同步到服务端，供命令默认关联项目等逻辑使用。
         */
        void sendSetActiveProjectId(String projectId);

        boolean isHudVisible();

        void setHudVisible(boolean visible);

        /**
         * 发送单条任务更新到服务端。
         */
        void sendUpdateTask(Task task);

        /**
         * 用给定列表替换个人任务列表（客户端到服务端）。
         */
        void sendReplaceAllTasks(List<Task> tasks);

        /**
         * 用给定列表替换团队任务列表（客户端到服务端）。
         */
        void sendReplaceTeamTasks(List<Task> tasks);

        /**
         * 发送带基线快照的团队任务合并请求，旧平台实现默认退回整表替换。
         */
        default void sendMergeTeamTasks(List<Task> baseTasks, List<Task> tasks) {
            sendReplaceTeamTasks(tasks);
        }

        /**
         * 主动请求服务端同步团队任务列表（服务端到客户端）。
         */
        void requestTeamSync();

        /**
         * 发送加入指定项目的申请。
         */
        void sendRequestJoinProject(String projectId);

        void sendHudStarredProjectIds(List<String> projectIds);

        /**
         * 请求删除指定项目。
         */
        void sendDeleteProject(String projectId);

        /**
         * 发送项目更新到服务端。
         */
        void sendUpdateProject(Project project);

        /**
         * 请求将成员从项目中移除。
         */
        void sendRemoveMember(String projectId, String memberUuid);

        /**
         * 请求变更成员在项目中的角色。
         */
        void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role);

        /**
         * 发送新增项目请求。
         */
        void sendAddProject(Project project);

        /**
         * 请求将成员加入项目。
         */
        void sendAddMember(String projectId, String memberUuid, String memberName);

        /**
         * 当前平台是否支持“修改成员角色”相关的网络能力。
         */
        boolean canSendUpdateMemberRole();
    }

    private static final ClientOps NO_OPS = new ClientOps() {
        private final TaskManager teamTaskManager = new TaskManager();
        private String activeProjectId;
        private boolean hudVisible = true;

        @Override
        public TaskManager getTeamTaskManager() {
            return teamTaskManager;
        }

        @Override
        public boolean isTeamProjectsEnabled() {
            return false;
        }

        @Override
        public String getActiveProjectId() {
            return activeProjectId;
        }

        @Override
        public void setActiveProjectId(String projectId) {
            activeProjectId = projectId;
        }

        @Override
        public void sendSetActiveProjectId(String projectId) {
        }

        @Override
        public boolean isHudVisible() {
            return hudVisible;
        }

        @Override
        public void setHudVisible(boolean visible) {
            hudVisible = visible;
        }

        @Override
        public void sendHudStarredProjectIds(List<String> projectIds) {
        }

        @Override
        public void sendUpdateTask(Task task) {
        }

        @Override
        public void sendReplaceAllTasks(List<Task> tasks) {
        }

        @Override
        public void sendReplaceTeamTasks(List<Task> tasks) {
        }

        @Override
        public void requestTeamSync() {
        }

        @Override
        public void sendRequestJoinProject(String projectId) {
        }

        @Override
        public void sendDeleteProject(String projectId) {
        }

        @Override
        public void sendUpdateProject(Project project) {
        }

        @Override
        public void sendRemoveMember(String projectId, String memberUuid) {
        }

        @Override
        public void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role) {
        }

        @Override
        public void sendAddProject(Project project) {
        }

        @Override
        public void sendAddMember(String projectId, String memberUuid, String memberName) {
        }

        @Override
        public boolean canSendUpdateMemberRole() {
            return false;
        }
    };

    private static ClientOps ops = NO_OPS;

    private ClientBridge() {
    }

    /**
     * 注入平台端实现；传入 null 将回退到 NO_OP 实现。
     */
    public static void setOps(ClientOps clientOps) {
        ops = clientOps == null ? NO_OPS : clientOps;
    }

    /**
     * 获取当前注入的客户端操作实现。
     */
    public static ClientOps ops() {
        return ops;
    }

    public static void saveLastActiveProjectId(String projectId) {
        ModConfig.getInstance().setLastActiveProjectId(projectId);
    }

    public static void syncHudViewForProject(Project project) {
        ModConfig config = ModConfig.getInstance();
        if (project == null || project.getScope() == Project.Scope.PERSONAL || !ops().isTeamProjectsEnabled()) {
            config.setHudDefaultView("PERSONAL");
            return;
        }
        String currentView = config.getHudDefaultView();
        if (currentView == null || currentView.isBlank() || "PERSONAL".equalsIgnoreCase(currentView)) {
            config.setHudDefaultView("TEAM_UNASSIGNED");
        }
    }

    /**
     * 获取当前激活项目；若 ID 失效会自动清空激活 ID。
     */
    public static Project getActiveProject(ProjectManager manager) {
        return getActiveProject(manager, null);
    }

    /**
     * 获取指定范围内的激活项目；若 ID 失效或范围不匹配会自动清空激活 ID。
     */
    public static Project getActiveProject(ProjectManager manager, Project.Scope scope) {
        if (manager == null) {
            ops().setActiveProjectId(null);
            return null;
        }
        String activeProjectId = ops().getActiveProjectId();
        if (activeProjectId == null || activeProjectId.isEmpty()) {
            return null;
        }
        Project activeProject = manager.getProject(activeProjectId);
        if (activeProject == null) {
            ops().setActiveProjectId(null);
            return null;
        }
        if (scope != null && activeProject.getScope() != scope) {
            ops().setActiveProjectId(null);
            return null;
        }
        return activeProject;
    }
}
