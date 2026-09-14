package com.todolist.material;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料反推索引的构建入口：遍历配方管理器中的配方并转换为反查索引。
 *
 * <p>索引在配方数据加载后构建一次即可复用；这里不缓存实例，
 * 由调用方（点 1 的解析流程）决定构建时机与失效时机。
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
        for (MaterialRecipeResolver resolver : resolvers) {
            if (resolver == null) {
                continue;
            }
            for (RecipeType<?> type : resolver.supportedTypes()) {
                for (Recipe<?> recipe : recipesOf(recipeManager, type)) {
                    MaterialRecipe mapping = resolver.resolve(recipe, registryAccess);
                    if (mapping != null) {
                        index.add(mapping);
                    }
                }
            }
        }
        return index;
    }

    /**
     * 读取指定配方类型下的全部配方。
     *
     * @param recipeManager 配方管理器
     * @param type          配方类型
     * @return 配方列表；类型无配方时返回空列表
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Recipe<?>> recipesOf(RecipeManager recipeManager, RecipeType<?> type) {
        List<Recipe<?>> recipes = new ArrayList<>();
        if (type == null) {
            return recipes;
        }
        List<?> raw = recipeManager.getAllRecipesFor((RecipeType) type);
        if (raw == null) {
            return recipes;
        }
        for (Object entry : raw) {
            if (entry instanceof Recipe<?> recipe) {
                recipes.add(recipe);
            }
        }
        return recipes;
    }
}
