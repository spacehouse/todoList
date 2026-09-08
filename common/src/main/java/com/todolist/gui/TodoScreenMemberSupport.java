package com.todolist.gui;

import com.todolist.project.Project;

import java.util.UUID;

import net.minecraft.client.Minecraft;

/**
 * TodoScreen 成员支持类，集中处理成员显示名解析逻辑。
 */
final class TodoScreenMemberSupport {
    /**
     * 工具类不需要实例化。
     */
    private TodoScreenMemberSupport() {
    }

    /**
     * 解析项目成员显示名称，优先使用缓存名称，其次读取在线玩家名称。
     *
     * @param minecraft 当前客户端实例
     * @param project 当前项目
     * @param memberUuid 成员 UUID
     * @return 成员显示名称；无法解析时回退为 UUID
     */
    static String resolveProjectMemberDisplayName(Minecraft minecraft, Project project, String memberUuid) {
        if (project == null || memberUuid == null || memberUuid.isBlank()) {
            return "";
        }
        String displayName = project.getMemberName(memberUuid);
        if (displayName != null && !displayName.isBlank()) {
            displayName = displayName.trim();
        }
        try {
            if (minecraft != null && minecraft.getConnection() != null) {
                net.minecraft.client.multiplayer.PlayerInfo playerInfo =
                        minecraft.getConnection().getPlayerInfo(UUID.fromString(memberUuid));
                if (playerInfo != null && playerInfo.getProfile() != null) {
                    String onlineName = playerInfo.getProfile().name();
                    if (onlineName != null && !onlineName.isBlank()) {
                        displayName = onlineName;
                        project.setMemberName(memberUuid, onlineName);
                    }
                }
            }
        } catch (Exception e) {
            // 玩家信息读取失败时回退到 UUID，避免界面出现空名称。
        }
        if (displayName == null || displayName.isBlank()) {
            return memberUuid;
        }
        return displayName;
    }
}
