package com.todolist.storage;

import java.util.List;

/**
 * LegacyMigrationData 汇总旧 NBT 数据迁移所需的只读快照。
 */
public record LegacyMigrationData(List<LegacyTaskBucket> taskBuckets,
                                  List<LegacyProjectBucket> projectBuckets,
                                  List<LegacyPlayerProjectStateRecord> playerProjectStates) {
    /**
     * 创建迁移数据快照。
     *
     * @param taskBuckets 任务桶列表
     * @param projectBuckets 项目桶列表
     * @param playerProjectStates 玩家项目状态列表
     */
    public LegacyMigrationData {
        taskBuckets = taskBuckets == null ? List.of() : List.copyOf(taskBuckets);
        projectBuckets = projectBuckets == null ? List.of() : List.copyOf(projectBuckets);
        playerProjectStates = playerProjectStates == null ? List.of() : List.copyOf(playerProjectStates);
    }

    /**
     * 判断迁移快照是否不包含任何旧数据。
     *
     * @return 没有任务、项目和玩家状态时返回 true
     */
    public boolean isEmpty() {
        return taskBuckets.isEmpty() && projectBuckets.isEmpty() && playerProjectStates.isEmpty();
    }
}
