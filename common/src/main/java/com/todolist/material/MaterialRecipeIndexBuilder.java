package com.todolist.material;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料反推索引的构建入口：遍历配方管理器中的配方并转换为反查索引（1.21.2~1.21.8 覆盖版）。
 *
 * <p>与基线（1.20.x/1.21.1）的唯一差异：1.21.2 起 {@code RecipeManager#getAllRecipesFor(RecipeType)}
 * 被移除，改为经 {@code getRecipes()} 全量读取后按配方类型过滤；
 * {@code RecipeHolder} 形态在 1.21.2~1.21.8 与基线一致，解析器接口无需覆盖。
 */
public final class MaterialRecipeIndexBuilder {

    /**
     * 工具类不需要实例化。
     */
    private MaterialRecipeIndexBuilder() {
    }

    /**
     * 使用默认的原版配方解析器构建索引。
     *
     * @param recipeManager  配方管理器
     * @param registryAccess 注册表访问
     * @return 配方反查索引
     */
    public static MaterialRecipeIndex buildDefault(RecipeManager recipeManager, RegistryAccess registryAccess) {
        return build(recipeManager, registryAccess, List.of(new VanillaMaterialRecipeResolver()));
    }

    /**
     * 按给定解析器列表构建索引。
     *
     * @param recipeManager  配方管理器
     * @param registryAccess 注册表访问
     * @param resolvers      配方解析器列表（原版 + 后续模组解析器）
     * @return 配方反查索引
     */
    public static MaterialRecipeIndex build(RecipeManager recipeManager,
                                            RegistryAccess registryAccess,
                                            List<MaterialRecipeResolver> resolvers) {
        MaterialRecipeIndex index = new MaterialRecipeIndex();
        if (recipeManager == null || resolvers == null) {
            return index;
        }
        int scanned = 0;
        for (MaterialRecipeResolver resolver : resolvers) {
            if (resolver == null) {
                continue;
            }
            for (RecipeType<?> type : resolver.supportedTypes()) {
                for (RecipeHolder<?> holder : recipesOf(recipeManager, type)) {
                    scanned++;
                    MaterialRecipe mapping = resolver.resolve(holder, registryAccess);
                    if (mapping != null) {
                        index.add(mapping);
                    }
                }
            }
        }
        com.todolist.TodoConstants.LOGGER.info(
                "[todolist] material index built: scanned={} holders, resolved={} recipes (manager={})",
                scanned, index.recipeCount(), recipeManager.getClass().getSimpleName());
        return index;
    }

    /**
     * 读取指定配方类型下的全部配方。
     * 1.21.2 起无按类型读取的入口，这里全量读取后按 {@code holder.value().getType()} 过滤。
     *
     * @param recipeManager 配方管理器
     * @param type          配方类型
     * @return 配方列表；类型无配方时返回空列表
     */
    private static List<RecipeHolder<?>> recipesOf(RecipeManager recipeManager, RecipeType<?> type) {
        List<RecipeHolder<?>> holders = new ArrayList<>();
        if (type == null) {
            return holders;
        }
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (holder != null && holder.value() != null && holder.value().getType() == type) {
                holders.add(holder);
            }
        }
        return holders;
    }
}
