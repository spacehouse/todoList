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
}


