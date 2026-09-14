package com.todolist.material;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Set;

/**
 * 配方解析器：把 MC 运行时配方转换为中立的 {@link MaterialRecipe} 模型。
 *
 * <p>这一层是唯一依赖 MC 运行时的部分；转换完成后，索引、选择策略与递归展开
 * 都只依赖中立模型，可离线测试。
 *
 * <p>实现方按配方类型分工：原版配方由 {@code VanillaMaterialRecipeResolver} 处理，
 * 模组自定义配方类型后续按同样接口注册（对应设计文档的点 3）。
 */
public interface MaterialRecipeResolver {

    /**
     * 返回该解析器负责枚举的配方类型集合。
     *
     * @return 配方类型集合
     */
    Set<RecipeType<?>> supportedTypes();

    /**
     * 把一条 MC 配方转换为中立模型。
     *
     * @param recipe         MC 配方（1.20.1 中配方自带 {@link Recipe#getId()}）
     * @param registryAccess 注册表访问，用于解析产物
     * @return 中立配方；无法解析（产物为空 / 无有效输入）时返回 null
     */
    MaterialRecipe resolve(Recipe<?> recipe, RegistryAccess registryAccess);
}
