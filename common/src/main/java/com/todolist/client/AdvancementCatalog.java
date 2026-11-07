package com.todolist.client;

import com.todolist.platform.DataPathProvider;

import java.util.List;

/**
 * 客户端进度目录缓存。
 *
 * 客户端自带的 {@code ClientAdvancements} 只包含「玩家已解锁 / 可见」的进度：
 * 服务端按可见性（完成态、祖先完成度、隐藏标记）增量下发，新存档下几乎为空，
 * 因此不能作为「选择任意进度」的候选来源。这里缓存服务端权威目录
 * （由 {@code MinecraftServer#getAdvancements()} 全量导出，随任务同步一起下发）。
 *
 * 缓存与存储域（{@link DataPathProvider#getStorageNamespace()}）绑定：
 * 切换存档 / 服务器后，旧目录立即失效，避免把上一个存档的进度列表带进新存档。
 */
public final class AdvancementCatalog {
    /** 一条进度候选：资源 ID 与服务端解析出的展示名。 */
    public record Entry(String id, String title) {
    }

    /** 当前缓存的服务端权威进度目录。 */
    private static volatile List<Entry> entries = List.of();
    /** 目录所属的存储域，与 DataPathProvider 的当前值不一致时视为失效。 */
    private static volatile String namespace;

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private AdvancementCatalog() {
    }

    /**
     * 接收并缓存服务端下发的完整进度目录。
     *
     * @param namespaceAtReceive 接收时刻的存储域
     * @param incoming           服务端目录条目
     */
    public static void apply(String namespaceAtReceive, List<Entry> incoming) {
        if (incoming == null) {
            return;
        }
        namespace = namespaceAtReceive;
        entries = List.copyOf(incoming);
    }

    /**
     * 返回当前存档有效的进度目录；存储域已切换或从未收到时返回空列表。
     *
     * @return 进度目录（可能为空）
     */
    public static List<Entry> getEntries() {
        if (namespace == null || !namespace.equals(DataPathProvider.getStorageNamespace())) {
            clear();
            return List.of();
        }
        List<Entry> current = entries;
        return current == null ? List.of() : current;
    }

    /**
     * 清空缓存，供切换存档或断开连接时调用。
     */
    public static void clear() {
        entries = List.of();
        namespace = null;
    }
}
