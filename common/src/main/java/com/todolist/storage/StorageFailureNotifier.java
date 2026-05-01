package com.todolist.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * StorageFailureNotifier 负责把存储异常转换为用户可见的统一提示。
 */
public final class StorageFailureNotifier {
    public static final String STORAGE_UNAVAILABLE_MESSAGE_KEY = "message.todolist.storage_unavailable";
    public static final String COMMAND_STORAGE_UNAVAILABLE_MESSAGE_KEY = "command.todolist.storage_unavailable";

    private StorageFailureNotifier() {
    }

    /**
     * 判断异常链是否表示存储不可用。
     *
     * @param throwable 异常
     * @return 存储不可用时返回 true
     */
    public static boolean isStorageUnavailable(Throwable throwable) {
        return StorageUnavailableException.find(throwable) != null;
    }

    /**
     * 将保存异常转换为普通 GUI/网络提示。
     *
     * @param throwable 异常
     * @param fallbackMessageKey 普通失败提示 key
     * @return 本地化提示组件
     */
    public static Component toUserMessage(Throwable throwable, String fallbackMessageKey) {
        if (isStorageUnavailable(throwable)) {
            return Component.translatable(STORAGE_UNAVAILABLE_MESSAGE_KEY);
        }
        return Component.translatable(fallbackMessageKey);
    }

    /**
     * 将命令异常转换为命令提示 key。
     *
     * @param throwable 异常
     * @param fallbackMessageKey 普通失败提示 key
     * @return 命令提示 key
     */
    public static String toCommandMessageKey(Throwable throwable, String fallbackMessageKey) {
        if (isStorageUnavailable(throwable)) {
            return COMMAND_STORAGE_UNAVAILABLE_MESSAGE_KEY;
        }
        return fallbackMessageKey;
    }

    /**
     * 向玩家发送存储失败提示。
     *
     * @param player 玩家
     * @param throwable 异常
     * @param fallbackMessageKey 普通失败提示 key
     */
    public static void notifyPlayer(ServerPlayer player, Throwable throwable, String fallbackMessageKey) {
        if (player != null) {
            player.displayClientMessage(toUserMessage(throwable, fallbackMessageKey), false);
        }
    }
}
