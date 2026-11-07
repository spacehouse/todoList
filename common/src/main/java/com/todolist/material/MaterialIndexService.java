package com.todolist.material;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端权威材料反推索引。
 *
 * <p>1.21.2 起客户端不再持有配方数据（客户端配方容器只剩属性集与切石集），
 * 材料反推的索引构建与反查迁移到服务端：索引按服务端配方管理器构建后
 * 全服共享缓存，玩家请求时按目标物品反查下发。
 *
 * <p>索引在首个请求到来时惰性构建；配方重载（{@code /reload}）后由
 * 重载监听调用 {@link #invalidate()} 失效，下一次请求自动重建。
 */
public final class MaterialIndexService {
    /** 产物物品资源 ID → 该物品的配方映射列表。 */
    private static volatile Map<String, List<MaterialRecipe>> byOutput;
    /** 去重后的可反推产物物品 ID 清单（与 byOutput 同源）。 */
    private static volatile List<String> itemIds;

    private MaterialIndexService() {
    }

    /**
     * 确保索引已按当前服务端配方构建。
     *
     * @param server 当前服务端
     */
    public static synchronized void ensureBuilt(MinecraftServer server) {
        if (byOutput != null) {
            return;
        }
        MaterialRecipeIndex index = MaterialRecipeIndexBuilder.buildDefault(
                server.getRecipeManager(),
                server.registryAccess()
        );
        Map<String, List<MaterialRecipe>> output = new ConcurrentHashMap<>();
        for (String outputItemId : index.outputItemIds()) {
            List<MaterialRecipe> recipes = index.findByOutput(outputItemId);
            if (recipes == null || recipes.isEmpty()) {
                continue;
            }
            output.put(outputItemId, recipes);
        }
        byOutput = output;
        itemIds = List.copyOf(output.keySet());
        com.todolist.TodoConstants.LOGGER.info(
                "[todolist] server material index built: {} distinct outputs",
                itemIds.size());
    }

    /**
     * 返回可反推物品清单（去重产物 ID）。
     *
     * @param server 当前服务端
     * @return 物品资源 ID 列表；索引未构建时返回空列表
     */
    public static List<String> itemIds(MinecraftServer server) {
        ensureBuilt(server);
        return itemIds;
    }

    /**
     * 反查目标物品的配方映射。
     *
     * @param server 当前服务端
     * @param itemId 目标物品资源 ID
     * @return 配方映射列表；无匹配时返回空列表
     */
    public static List<MaterialRecipe> recipesFor(MinecraftServer server, String itemId) {
        ensureBuilt(server);
        if (itemId == null || itemId.isEmpty()) {
            return List.of();
        }
        return byOutput.getOrDefault(itemId, List.of());
    }

    /**
     * 使缓存索引失效（配方重载后调用），下一次请求自动重建。
     */
    public static void invalidate() {
        byOutput = null;
        itemIds = null;
    }
}
