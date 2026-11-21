package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.storage.H2ProjectPlayerStateStore;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 玩家项目状态存储组件。
 * 统一通过 H2 存储持久化玩家当前选中项目、HUD 星标项目以及 HUD 可见性等运行期状态。
 */
public class ProjectPlayerStateStorage {
    private final H2ProjectPlayerStateStore h2PlayerStateStore = new H2ProjectPlayerStateStore();

    /**
     * 创建玩家项目状态存储组件。
     */
    public ProjectPlayerStateStorage() {
    }

    /**
     * 判断指定玩家的项目状态是否已经存在。
     *
     * @param playerUuid 玩家 UUID
     * @return 状态存在时返回 true
     */
    public boolean hasPlayerState(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        try {
            return h2PlayerStateStore.hasPlayerState(playerUuid);
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to query H2 project player state for {}", playerUuid, exception);
            return false;
        }
    }

    /**
     * 保存指定玩家的项目状态。
     *
     * @param playerUuid 玩家 UUID
     * @param state 待保存的项目状态
     * @throws IOException 当保存失败时抛出
     */
    public void savePlayerState(UUID playerUuid, ProjectPlayerState state) throws IOException {
        h2PlayerStateStore.savePlayerState(playerUuid, state);
    }

    /**
     * 读取指定玩家的项目状态。
     *
     * @param playerUuid 玩家 UUID
     * @return 读取到的项目状态；不存在时返回空状态
     * @throws IOException 当读取失败时抛出
     */
    public ProjectPlayerState loadPlayerState(UUID playerUuid) throws IOException {
        return h2PlayerStateStore.loadPlayerState(playerUuid);
    }

    /**
     * 玩家项目状态值对象。
     * 用于封装当前选中项目、HUD 星标项目列表和 HUD 可见性。
     */
    public static final class ProjectPlayerState {
        private final String activeProjectId;
        private final List<String> hudStarredProjectIds;
        private final boolean hudVisible;

        /**
         * 创建一个玩家项目状态对象。
         *
         * @param activeProjectId 当前选中项目 ID
         * @param hudStarredProjectIds HUD 星标项目 ID 列表
         * @param hudVisible HUD 是否可见
         */
        public ProjectPlayerState(String activeProjectId, List<String> hudStarredProjectIds, boolean hudVisible) {
            this.activeProjectId = activeProjectId == null || activeProjectId.trim().isEmpty() ? null : activeProjectId.trim();
            this.hudStarredProjectIds = hudStarredProjectIds == null ? List.of() : List.copyOf(hudStarredProjectIds);
            this.hudVisible = hudVisible;
        }

        /**
         * 返回一个默认空状态。
         *
         * @return 默认空状态
         */
        public static ProjectPlayerState empty() {
            return new ProjectPlayerState(null, List.of(), true);
        }

        /**
         * 返回当前选中项目 ID。
         *
         * @return 当前选中项目 ID
         */
        public String getActiveProjectId() {
            return activeProjectId;
        }

        /**
         * 返回 HUD 星标项目 ID 列表。
         *
         * @return HUD 星标项目 ID 列表
         */
        public List<String> getHudStarredProjectIds() {
            return hudStarredProjectIds;
        }

        /**
         * 返回 HUD 是否可见。
         *
         * @return HUD 是否可见
         */
        public boolean isHudVisible() {
            return hudVisible;
        }
    }
}
