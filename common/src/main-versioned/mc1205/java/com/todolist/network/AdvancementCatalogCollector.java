package com.todolist.network;

import com.todolist.client.AdvancementCatalog;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端进度目录收集器。
 *
 * <p>本类为 1.20.5+（Advancement record 化为 {@code AdvancementHolder}）形态：
 * 遍历 {@code getAllAdvancements()} 得到持有者，展示信息为 Optional。
 * 1.20.1~1.20.4 的实体形态由主源码树提供。
 */
public final class AdvancementCatalogCollector {

    private AdvancementCatalogCollector() {
    }

    /**
     * 汇总服务端全部带展示信息的进度。
     * 无展示信息的进度（配方解锁等）不作为可选目标，直接跳过。
     *
     * @param server 当前服务端
     * @return 进度目录条目
     */
    static List<AdvancementCatalog.Entry> collect(MinecraftServer server) {
        List<AdvancementCatalog.Entry> entries = new ArrayList<>();
        if (server.getAdvancements() == null) {
            return entries;
        }
        for (net.minecraft.advancements.AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            if (holder == null || holder.value() == null || holder.value().display().isEmpty()) {
                continue;
            }
            entries.add(new AdvancementCatalog.Entry(
                    holder.id().toString(),
                    holder.value().display().get().getTitle().getString()));
        }
        return entries;
    }
}
