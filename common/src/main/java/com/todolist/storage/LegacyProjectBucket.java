package com.todolist.storage;

import com.todolist.project.Project;

import java.util.List;

/**
 * LegacyProjectBucket 表示从旧 NBT 文件只读读取到的项目桶。
 */
public record LegacyProjectBucket(String bucketType, List<Project> projects) {
    /**
     * 创建项目桶快照。
     *
     * @param bucketType 项目桶类型
     * @param projects 项目列表
     */
    public LegacyProjectBucket {
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
