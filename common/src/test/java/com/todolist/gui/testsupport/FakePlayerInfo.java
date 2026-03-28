package com.todolist.gui.testsupport;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * 假玩家信息对象：为成员列表和任务分配测试提供稳定的在线玩家条目。
 */
public class FakePlayerInfo extends PlayerInfo {
    /**
     * 使用测试档案创建玩家信息对象。
     *
     * @param profile 玩家档案
     */
    public FakePlayerInfo(GameProfile profile) {
        super(profile, false);
    }
}
