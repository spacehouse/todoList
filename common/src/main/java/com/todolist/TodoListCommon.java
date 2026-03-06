package com.todolist;

import com.todolist.project.ProjectManager;
import com.todolist.project.ProjectStorage;
import com.todolist.task.TaskStorage;

/**
 * 妯＄粍閫氱敤閫昏緫绫汇€? * 鍖呭惈鍏ㄥ眬瀛樺偍瀹炰緥鐨勭鐞嗐€? */
public final class TodoListCommon {
    private static TaskStorage taskStorage;
    private static ProjectStorage projectStorage;
    private static ProjectManager projectManager;
    private static volatile boolean projectSyncInProgress;

    private TodoListCommon() {
    }

    /**
     * 鍒濆鍖栭€氱敤缁勪欢銆?     */
    public static void init() {
        taskStorage = new TaskStorage();
        projectStorage = new ProjectStorage();
        projectManager = new ProjectManager();
    }

    /**
     * 鑾峰彇浠诲姟瀛樺偍瀹炰緥銆?     * @return TaskStorage 瀹炰緥
     */
    public static TaskStorage getTaskStorage() {
        return taskStorage;
    }

    /**
     * 鑾峰彇椤圭洰瀛樺偍瀹炰緥銆?     * @return ProjectStorage 瀹炰緥
     */
    public static ProjectStorage getProjectStorage() {
        return projectStorage;
    }

    /**
     * 鑾峰彇椤圭洰绠＄悊鍣ㄥ疄渚嬨€?     * @return ProjectManager 瀹炰緥
     */
    public static ProjectManager getProjectManager() {
        return projectManager;
    }

    /**
     * 标记客户端项目同步是否处于进行中，用于避免 GUI 在同步过程中触发兜底创建逻辑。
     */
    public static void setProjectSyncInProgress(boolean syncing) {
        projectSyncInProgress = syncing;
    }

    /**
     * 获取客户端项目同步进行中标记。
     */
    public static boolean isProjectSyncInProgress() {
        return projectSyncInProgress;
    }
}


