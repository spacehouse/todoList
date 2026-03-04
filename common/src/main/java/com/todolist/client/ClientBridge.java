package com.todolist.client;

import com.todolist.project.Project;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.util.List;

public final class ClientBridge {
    public interface ClientOps {
        TaskManager getTeamTaskManager();

        boolean isTeamProjectsEnabled();

        String getActiveProjectId();

        void setActiveProjectId(String projectId);

        void sendUpdateTask(Task task);

        void sendReplaceAllTasks(List<Task> tasks);

        void sendReplaceTeamTasks(List<Task> tasks);

        void requestTeamSync();

        void sendRequestJoinProject(String projectId);

        void sendDeleteProject(String projectId);

        void sendUpdateProject(Project project);

        void sendRemoveMember(String projectId, String memberUuid);

        void sendUpdateMemberRole(String projectId, String memberUuid, Project.ProjectRole role);

        void sendAddProject(Project project);

        void sendAddMember(String projectId, String memberUuid, String memberName);

        boolean canSendUpdateMemberRole();
    }

    private static final ClientOps NO_OPS = new ClientOps() {
        private final TaskManager teamTaskManager = new TaskManager();
        private String activeProjectId;

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

    public static void setOps(ClientOps clientOps) {
        ops = clientOps == null ? NO_OPS : clientOps;
    }

    public static ClientOps ops() {
        return ops;
    }
}
