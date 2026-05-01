package com.todolist.storage;

import com.todolist.project.ProjectPlayerStateStorage;

import java.util.UUID;

/**
 * LegacyPlayerProjectStateRecord 表示旧 NBT 玩家项目状态文件的只读快照。
 */
public record LegacyPlayerProjectStateRecord(UUID playerUuid,
                                             long lastSaved,
                                             ProjectPlayerStateStorage.ProjectPlayerState state) {
    /**
     * 创建玩家项目状态快照。
     *
     * @param playerUuid 玩家 UUID
     * @param lastSaved 旧状态文件最后保存时间
     * @param state 玩家项目状态
     */
    public LegacyPlayerProjectStateRecord {
    }
}
