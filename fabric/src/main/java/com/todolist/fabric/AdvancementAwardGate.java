package com.todolist.fabric;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度上报去重闸门。
 * Fabric 侧没有公开的进度事件，只能通过 Mixin 注入 {@code PlayerAdvancements#award} 上报；
 * 一个多条件进度会在逐个条件校验过程中多次进入 {@code award}，因此这里按
 * 「玩家 UUID + 进度 ID」去重，保证同一条进度只上报一次。
 *
 * 说明：该状态与清理入口刻意放在普通类中，而不是放在 Mixin 类里。
 * Mixin 要求被注入类中不能出现非 private 的静态方法，放在 Mixin 里会在类加载阶段
 * 直接抛 {@code InvalidMixinException} 导致目标类（PlayerAdvancements）无法加载。
 */
public final class AdvancementAwardGate {
    /** 已上报过的「玩家 UUID | 进度 ID」组合。 */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private AdvancementAwardGate() {
    }

    /**
     * 判断本次「玩家 + 进度」是否为首次上报。
     *
     * @param playerUuid    玩家 UUID
     * @param advancementId 进度资源 ID
     * @return 首次上报返回 true；重复上报或参数非法返回 false
     */
    public static boolean shouldReport(UUID playerUuid, String advancementId) {
        if (playerUuid == null || advancementId == null || advancementId.isEmpty()) {
            return false;
        }
        return REPORTED.add(playerUuid + "|" + advancementId);
    }

    /**
     * 清理指定玩家的上报记录，供玩家退出时调用，
     * 使其在重新进入（或进入另一个存档）后仍能再次上报。
     *
     * @param playerUuid 玩家 UUID
     */
    public static void clearFor(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        String prefix = playerUuid + "|";
        REPORTED.removeIf(entry -> entry.startsWith(prefix));
    }
}
