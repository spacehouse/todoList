package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.storage.H2ProjectStore;

import java.io.IOException;
import java.util.List;

/**
 * 项目存储组件。
 * 统一通过 H2 存储读取和保存个人项目、团队项目数据。
 */
public class ProjectStorage {
    private final H2ProjectStore h2ProjectStore = new H2ProjectStore();

    /**
     * 创建项目存储组件。
     */
    public ProjectStorage() {
    }

    /**
     * 读取个人项目列表。
     *
     * @return 个人项目列表
     * @throws IOException 当读取失败时抛出
     */
    public List<Project> loadProjects() throws IOException {
        return h2ProjectStore.loadProjects();
    }

    /**
     * 读取团队项目列表。
     *
     * @return 团队项目列表
     * @throws IOException 当读取失败时抛出
     */
    public List<Project> loadTeamProjects() throws IOException {
        return h2ProjectStore.loadTeamProjects();
    }

    /**
     * 保存个人项目列表。
     *
     * @param projects 待保存的个人项目列表
     * @throws IOException 当保存失败时抛出
     */
    public void saveProjects(List<Project> projects) throws IOException {
        h2ProjectStore.saveProjects(projects);
    }

    /**
     * 保存团队项目列表。
     *
     * @param projects 待保存的团队项目列表
     * @throws IOException 当保存失败时抛出
     */
    public void saveTeamProjects(List<Project> projects) throws IOException {
        h2ProjectStore.saveTeamProjects(projects);
    }

    /**
     * 判断个人项目桶是否已有数据。
     * 当前用于初始化阶段区分首次创建与已有存档恢复。
     *
     * @return 若个人项目桶已有数据则返回 true
     */
    public boolean hasPersonalProjectsFile() {
        try {
            return h2ProjectStore.hasPersonalProjects();
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to query H2 personal projects", exception);
            return false;
        }
    }
}
