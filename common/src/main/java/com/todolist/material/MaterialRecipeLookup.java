package com.todolist.material;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * 客户端配方管理器获取器（1.21.2+ 通用形态）。
 *
 * <p>1.21.2 起 {@code ClientLevel#getRecipeManager()} 被移除，改为
 * {@code ClientLevel#recipeAccess()} 返回 {@link RecipeAccess}——客户端的实际实现
 * 即 {@link RecipeManager}，经 {@code instanceof} 还原。
 */
public final class MaterialRecipeLookup {

    private MaterialRecipeLookup() {
    }

    /**
     * 获取当前客户端世界的配方管理器。
     *
     * @param minecraft 客户端实例
     * @return 配方管理器；世界未加载或不可用时返回 null（无注解依赖，见方法注释）
     */
    public static RecipeManager clientRecipeManager(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        RecipeAccess access = minecraft.level.recipeAccess();
        if (access instanceof RecipeManager recipeManager) {
            return recipeManager;
        }
        com.todolist.TodoConstants.LOGGER.warn(
                "[todolist] client recipe access is not a RecipeManager: {}",
                access == null ? "null" : access.getClass().getName());
        return null;
    }
}
