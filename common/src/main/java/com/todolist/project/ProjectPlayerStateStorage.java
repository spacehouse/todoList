package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.platform.DataPathProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家项目状态存储组件。
 * 负责持久化玩家当前选中项目、HUD 星标项目以及 HUD 可见性等运行期状态。
 */
public class ProjectPlayerStateStorage {
    private static final int NBT_COMPOUND_TYPE = 10;
    private static final String PLAYER_STATE_VERSION_KEY = "version";
    private static final String LAST_SAVED_KEY = "lastSaved";
    private static final String ACTIVE_PROJECT_ID_KEY = "activeProjectId";
    private static final String HUD_VISIBLE_KEY = "hudVisible";
    private static final String HUD_STARRED_PROJECT_IDS_KEY = "hudStarredProjectIds";
    private static final String PROJECT_ID_KEY = "projectId";

    /**
     * 创建玩家项目状态存储组件，并确保状态目录存在。
     */
    public ProjectPlayerStateStorage() {
        ensureDirectoryExists();
    }

    /**
     * 返回玩家项目状态目录。
     *
     * @return 玩家项目状态目录路径
     */
    private Path getProjectPlayersDirectory() {
        return DataPathProvider.getProjectPlayersDir();
    }

    /**
     * 确保玩家项目状态目录已经创建。
     */
    private void ensureDirectoryExists() {
        try {
            Path playersDir = getProjectPlayersDirectory();
            if (!Files.exists(playersDir)) {
                Files.createDirectories(playersDir);
            }
        } catch (IOException e) {
            TodoConstants.LOGGER.error("Failed to create project player state directory", e);
        }
    }

    /**
     * 返回指定玩家的项目状态文件路径。
     *
     * @param playerUuid 玩家 UUID
     * @return 玩家项目状态文件路径
     */
    public Path getPlayerStateFilePath(UUID playerUuid) {
        ensureDirectoryExists();
        return getProjectPlayersDirectory().resolve(playerUuid.toString() + ".dat");
    }

    /**
     * 判断指定玩家的项目状态文件是否已经存在。
     *
     * @param playerUuid 玩家 UUID
     * @return 状态文件存在时返回 true
     */
    public boolean hasPlayerState(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        return Files.exists(getPlayerStateFilePath(playerUuid));
    }

    /**
     * 保存指定玩家的项目状态。
     *
     * @param playerUuid 玩家 UUID
     * @param state 待保存的项目状态
     * @throws IOException 当写盘失败时抛出
     */
    public void savePlayerState(UUID playerUuid, ProjectPlayerState state) throws IOException {
        ensureDirectoryExists();
        ProjectPlayerState safeState = state == null ? ProjectPlayerState.empty() : state;
        CompoundTag root = new CompoundTag();
        root.putInt(PLAYER_STATE_VERSION_KEY, 1);
        root.putLong(LAST_SAVED_KEY, System.currentTimeMillis());
        if (safeState.getActiveProjectId() != null && !safeState.getActiveProjectId().isBlank()) {
            root.putString(ACTIVE_PROJECT_ID_KEY, safeState.getActiveProjectId());
        }
        root.putBoolean(HUD_VISIBLE_KEY, safeState.isHudVisible());
        ListTag starredProjectList = new ListTag();
        for (String projectId : safeState.getHudStarredProjectIds()) {
            if (projectId == null || projectId.isBlank()) {
                continue;
            }
            CompoundTag projectTag = new CompoundTag();
            projectTag.putString(PROJECT_ID_KEY, projectId);
            starredProjectList.add(projectTag);
        }
        root.put(HUD_STARRED_PROJECT_IDS_KEY, starredProjectList);
        NbtIo.write(root, getPlayerStateFilePath(playerUuid).toFile());
    }

    /**
     * 读取指定玩家的项目状态。
     *
     * @param playerUuid 玩家 UUID
     * @return 读取到的项目状态；文件不存在时返回空状态
     * @throws IOException 当读盘失败时抛出
     */
    public ProjectPlayerState loadPlayerState(UUID playerUuid) throws IOException {
        Path playerStateFile = getPlayerStateFilePath(playerUuid);
        if (!Files.exists(playerStateFile)) {
            return ProjectPlayerState.empty();
        }
        CompoundTag root = NbtIo.read(playerStateFile.toFile());
        if (root == null) {
            return ProjectPlayerState.empty();
        }
        String activeProjectId = readOptionalTrimmedString(root, ACTIVE_PROJECT_ID_KEY);
        boolean hudVisible = !root.contains(HUD_VISIBLE_KEY) || root.getBoolean(HUD_VISIBLE_KEY);
        List<String> hudStarredProjectIds = new ArrayList<>();
        if (root.contains(HUD_STARRED_PROJECT_IDS_KEY, 9)) {
            ListTag starredProjectList = root.getList(HUD_STARRED_PROJECT_IDS_KEY, NBT_COMPOUND_TYPE);
            for (int index = 0; index < starredProjectList.size(); index++) {
                String projectId = readOptionalTrimmedString(starredProjectList.getCompound(index), PROJECT_ID_KEY);
                if (projectId == null || hudStarredProjectIds.contains(projectId)) {
                    continue;
                }
                hudStarredProjectIds.add(projectId);
            }
        }
        return new ProjectPlayerState(activeProjectId, hudStarredProjectIds, hudVisible);
    }

    /**
     * 从 NBT 中读取一个可选字符串，并在为空白时返回 null。
     *
     * @param root NBT 根节点
     * @param key 字段名
     * @return 规范化后的字符串；为空白时返回 null
     */
    private String readOptionalTrimmedString(CompoundTag root, String key) {
        if (root == null || key == null || !root.contains(key)) {
            return null;
        }
        String value = root.getString(key);
        if (value == null) {
            return null;
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
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
