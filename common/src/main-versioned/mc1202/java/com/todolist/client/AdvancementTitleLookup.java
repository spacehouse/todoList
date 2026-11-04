package com.todolist.client;

import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端进度标题解析器。
 *
 * <p>本类为 1.20.2+（进度实体 record 化）形态：经
 * {@code ClientAdvancements#get(location)} 直接取 {@code AdvancementHolder}。
 * 1.20.1~1.20.4 的映射形态（{@code getAdvancements().get(...)}）由主源码树提供。
 */
public final class AdvancementTitleLookup {

    private AdvancementTitleLookup() {
    }

    /**
     * 从客户端已加载的进度中解析展示标题。
     * 客户端只持有「已解锁 / 可见」进度，未覆盖时由调用方回退服务端权威目录。
     *
     * @param advancements 客户端进度管理器
     * @param location     进度资源 ID
     * @return 进度标题；进度不存在或无展示信息时返回 null
     */
    static String lookup(ClientAdvancements advancements, ResourceLocation location) {
        if (advancements == null) {
            return null;
        }
        net.minecraft.advancements.AdvancementHolder holder = advancements.get(location);
        if (holder == null || holder.value() == null || holder.value().display().isEmpty()) {
            return null;
        }
        return holder.value().display().get().getTitle().getString();
    }
}
