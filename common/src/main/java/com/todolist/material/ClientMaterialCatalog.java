package com.todolist.material;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 客户端可反推物品清单（服务端权威）。
 *
 * <p>1.21.2 起客户端不再持有配方数据，可反推物品清单由服务端在玩家登录时
 * 经 {@code MATERIAL_ITEMS} 通道下发，材料按钮的物品选择以此为数据源。
 * 1.20.x/1.21.1 走客户端本地配方索引，不消费该清单。
 */
public final class ClientMaterialCatalog {
    private static volatile boolean received;
    private static final Set<String> ITEM_IDS = new CopyOnWriteArraySet<>();

    private ClientMaterialCatalog() {
    }

    /**
     * 应用服务端下发的物品清单（整体替换）。
     *
     * @param itemIds 物品资源 ID 列表
     */
    public static void apply(List<String> itemIds) {
        ITEM_IDS.clear();
        if (itemIds != null) {
            ITEM_IDS.addAll(itemIds);
        }
        received = true;
    }

    /**
     * 服务端清单是否已就绪。
     *
     * @return 已收到清单时返回 true
     */
    public static boolean available() {
        return received;
    }

    /**
     * 目标物品是否在服务端可反推清单内。
     *
     * @param itemId 物品资源 ID
     * @return 清单内返回 true；清单未就绪时也返回 true（避免误拦截，交由请求结果提示）
     */
    public static boolean contains(String itemId) {
        return ITEM_IDS.contains(itemId);
    }

    /**
     * 已就绪清单的快照。
     *
     * @return 物品资源 ID 列表快照
     */
    public static List<String> snapshot() {
        return List.copyOf(ITEM_IDS);
    }

    /**
     * 清空状态，供离线测试隔离。
     */
    public static void clearForTest() {
        ITEM_IDS.clear();
        received = false;
    }
}
