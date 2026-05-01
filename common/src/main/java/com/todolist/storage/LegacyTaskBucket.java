package com.todolist.storage;

import com.todolist.task.Task;

import java.util.List;

/**
 * LegacyTaskBucket 表示从旧 NBT 文件只读读取到的一个任务桶。
 */
public record LegacyTaskBucket(String bucketType, String ownerUuid, long lastSaved, List<Task> tasks) {
    /**
     * 创建任务桶快照。
     *
     * @param bucketType 桶类型
     * @param ownerUuid 桶所有者 UUID 或固定值
     * @param lastSaved 旧 NBT 桶的最后保存时间
     * @param tasks 任务列表
     */
    public LegacyTaskBucket {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
