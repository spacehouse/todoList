package com.todolist.network;

import com.todolist.client.AdvancementCatalog;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端进度目录收集器。
 *
 * <p>版本敏感实现：本类（主源码树）为 1.20.1~1.20.4 形态，遍历
 * {@code getAllAdvancements()} 得到 {@code Advancement} 实体；1.20.5+
 * （Advancement record 化）由 {@code src/main-versioned/mc1205} 覆盖目录提供，
 * 遍历得到 {@code AdvancementHolder}。调用方（{@link TaskPackets}）版本无关。
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
        for (net.minecraft.advancements.Advancement advancement : server.getAdvancements().getAllAdvancements()) {
            if (advancement == null || advancement.getDisplay() == null) {
                continue;
            }
            entries.add(new AdvancementCatalog.Entry(
                    advancement.getId().toString(),
                    advancement.getDisplay().getTitle().getString()));
        }
        return entries;
    }
}
