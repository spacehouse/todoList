package com.todolist.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * StorageFailureNotifier 负责把存储异常转换为用户可见的统一提示。
 */
public final class StorageFailureNotifier {
    public static final String STORAGE_UNAVAILABLE_MESSAGE_KEY = "message.todolist.storage_unavailable";
    public static final String COMMAND_STORAGE_UNAVAILABLE_MESSAGE_KEY = "command.todolist.storage_unavailable";
    /** 带失败原因与数据库路径的细化提示，便于玩家直接定位文件并走恢复命令。 */
    public static final String STORAGE_UNAVAILABLE_DETAIL_MESSAGE_KEY = "message.todolist.storage_unavailable.detail";
    public static final String COMMAND_STORAGE_UNAVAILABLE_DETAIL_MESSAGE_KEY = "command.todolist.storage_unavailable.detail";

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
     * <p>存储不可用时给出**具体原因与数据库文件路径**，并提示可用的查看/恢复备份命令，
     * 而不是只回一句「存储不可用」。
     *
     * @param throwable 异常
     * @param fallbackMessageKey 普通失败提示 key
     * @return 本地化提示组件
     */
    public static Component toUserMessage(Throwable throwable, String fallbackMessageKey) {
        StorageUnavailableException unavailable = StorageUnavailableException.find(throwable);
        if (unavailable != null) {
            return Component.translatable(STORAGE_UNAVAILABLE_DETAIL_MESSAGE_KEY,
                    describeReason(unavailable.getReason()), describeDatabaseFile());
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
     * 返回存储不可用原因对应的本地化文本。
     *
     * @param reason 不可用原因
     * @return 本地化文本；原因缺失时返回「未知原因」
     */
    public static String describeReason(H2StorageAvailability.Reason reason) {
        if (reason == null) {
            return Component.translatable("storage.todolist.reason.unknown").getString();
        }
        String key = "storage.todolist.reason." + reason.name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(key).getString();
    }

    /**
     * 返回当前数据库文件路径文本。
     *
     * @return 形如 {@code E:/.../todo/local/todolist.mv.db} 的路径文本；读取失败时返回「未知路径」
     */
    public static String describeDatabaseFile() {
        try {
            return new H2ConnectionProvider().getDatabaseBasePath() + ".mv.db";
        } catch (Exception exception) {
            return Component.translatable("storage.todolist.path.unknown").getString();
        }
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
