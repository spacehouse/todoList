package com.todolist.material;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * 客户端配方管理器获取器。
 *
 * <p>版本敏感实现：本类（主源码树）为 1.20.x/1.21.1 形态，直接走
 * {@code ClientLevel#getRecipeManager()}；1.21.2+ 该方法被移除，由
 * 各 api_group 覆盖目录（v1_21_2~v1_21_6）提供 {@code recipeAccess()} 探测形态。
 * 调用方（{@code TodoScreen} 的材料反推入口）保持版本无关。
 */
public final class MaterialRecipeLookup {

    private MaterialRecipeLookup() {
    }

    /**
     * 获取当前客户端世界的配方管理器。
     *
     * @param minecraft 客户端实例
     * @return 配方管理器；世界未加载或不可用时返回 null
     */
    public static RecipeManager clientRecipeManager(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getRecipeManager();
    }
}
