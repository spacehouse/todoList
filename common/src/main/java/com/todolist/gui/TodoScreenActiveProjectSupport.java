package com.todolist.gui;

import com.todolist.client.ClientBridge;
import com.todolist.project.Project;
import com.todolist.project.ProjectManager;

/**
 * TodoScreen 活动项目支持类，集中处理活动项目 ID 同步。
 */
final class TodoScreenActiveProjectSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenActiveProjectSupport() {
    }

    static Project syncActiveProjectIdWithCurrentProject(ProjectManager projectManager, Project currentProject) {
        if (currentProject == null || projectManager == null) {
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            return null;
        }
        Project fresh = projectManager.getProject(currentProject.getId());
        if (fresh == null) {
            ClientBridge.ops().setActiveProjectId(null);
            ClientBridge.ops().sendSetActiveProjectId(null);
            ClientBridge.saveLastActiveProjectId(null);
            return null;
        }
        ClientBridge.ops().setActiveProjectId(fresh.getId());
        ClientBridge.ops().sendSetActiveProjectId(fresh.getId());
        ClientBridge.saveLastActiveProjectId(fresh.getId());
        return fresh;
    }
}
