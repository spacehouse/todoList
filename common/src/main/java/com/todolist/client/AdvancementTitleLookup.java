package com.todolist.client;

import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端进度标题解析器。
 *
 * <p>版本敏感实现：本类（主源码树）为 1.20.1~1.20.4 形态，经
 * {@code ClientAdvancements#getAdvancements()} 拿到进度映射并取
 * {@code Advancement} 实体；1.20.5+（进度实体 record 化）由
 * {@code src/main-versioned/mc1205} 覆盖目录提供，改经
 * {@code ClientAdvancements#get(location)} 取 {@code AdvancementHolder}。
 * 调用方（{@link TriggerTargetSupport}）版本无关。
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
        if (advancements == null || advancements.getAdvancements() == null) {
            return null;
        }
        net.minecraft.advancements.Advancement advancement = advancements.getAdvancements().get(location);
        if (advancement == null || advancement.getDisplay() == null) {
            return null;
        }
        return advancement.getDisplay().getTitle().getString();
    }
}
