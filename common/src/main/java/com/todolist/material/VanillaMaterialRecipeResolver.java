package com.todolist.material;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 原版配方解析器（对应设计文档 L1）。
 *
 * <p>覆盖 7 种原版配方类型：crafting / smelting / blasting / smoking / campfire /
 * stonecutting / smithing。输入统一从 {@link Recipe#getIngredients()} 读取，
 * 产物统一从 {@link Recipe#getResultItem(RegistryAccess)} 读取，
 * 因此物品标签也会被原版 {@link Ingredient} 展开为候选物品列表。
 */
public final class VanillaMaterialRecipeResolver implements MaterialRecipeResolver {

    /** 配方类型 → 材料反推分类。 */
    private static final Map<RecipeType<?>, MaterialRecipeKind> KIND_BY_TYPE = buildKindByType();

    /**
     * 构造配方类型到分类的映射。
     *
     * @return 不可变映射
     */
    private static Map<RecipeType<?>, MaterialRecipeKind> buildKindByType() {
        Map<RecipeType<?>, MaterialRecipeKind> kinds = new LinkedHashMap<>();
        kinds.put(RecipeType.CRAFTING, MaterialRecipeKind.CRAFTING);
        kinds.put(RecipeType.STONECUTTING, MaterialRecipeKind.STONECUTTING);
        kinds.put(RecipeType.SMITHING, MaterialRecipeKind.SMITHING);
        kinds.put(RecipeType.SMELTING, MaterialRecipeKind.SMELTING);
        kinds.put(RecipeType.BLASTING, MaterialRecipeKind.BLASTING);
        kinds.put(RecipeType.SMOKING, MaterialRecipeKind.SMOKING);
        kinds.put(RecipeType.CAMPFIRE_COOKING, MaterialRecipeKind.CAMPFIRE);
        return Map.copyOf(kinds);
    }

    /**
     * 返回本解析器负责的全部原版配方类型。
     *
     * @return 配方类型集合
     */
    @Override
    public Set<RecipeType<?>> supportedTypes() {
        return new LinkedHashSet<>(KIND_BY_TYPE.keySet());
    }

    /**
     * 把一条原版配方转换为中立模型。
     * 产物为空、配方类型未识别、或无有效输入时返回 null（不参与反推）。
     *
     * @param recipe         MC 配方
     * @param registryAccess 注册表访问
     * @return 中立配方；无法解析时返回 null
     */
    @Override
    public MaterialRecipe resolve(Recipe<?> recipe, RegistryAccess registryAccess) {
        if (recipe == null) {
            return null;
        }
        MaterialRecipeKind kind = KIND_BY_TYPE.get(recipe.getType());
        if (kind == null) {
            return null;
        }
        ItemStack output = safeResult(recipe, registryAccess);
        if (output.isEmpty()) {
            return null;
        }
        String outputItemId = itemId(output);
        if (outputItemId.isEmpty()) {
            return null;
        }
        List<MaterialIngredient> inputs = collectInputs(recipe);
        if (inputs.isEmpty()) {
            return null;
        }
        ResourceLocation recipeId = recipe.getId();
        return new MaterialRecipe(
                recipeId == null ? "" : recipeId.toString(),
                outputItemId,
                output.getCount(),
                inputs,
                kind
        );
    }

    /**
     * 安全读取配方产物，避免个别配方实现抛异常或返回 null。
     *
     * @param recipe         配方
     * @param registryAccess 注册表访问
     * @return 产物物品堆；不可用时返回空堆
     */
    private static ItemStack safeResult(Recipe<?> recipe, RegistryAccess registryAccess) {
        try {
            ItemStack result = recipe.getResultItem(registryAccess);
            return result == null ? ItemStack.EMPTY : result;
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 读取配方的输入项。
     * 每个 {@link Ingredient} 作为一个输入项（原版合成配方按格子给出，重复材料会出现多次），
     * 其候选物品由 {@link Ingredient#getItems()} 展开（物品标签会展开成全部候选）。
     *
     * @param recipe 配方
     * @return 输入项列表
     */
    private static List<MaterialIngredient> collectInputs(Recipe<?> recipe) {
        List<MaterialIngredient> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null) {
                continue;
            }
            List<String> candidates = new ArrayList<>();
            ItemStack[] items;
            try {
                items = ingredient.getItems();
            } catch (RuntimeException exception) {
                continue;
            }
            if (items == null) {
                continue;
            }
            for (ItemStack item : items) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                String candidateId = itemId(item);
                if (!candidateId.isEmpty() && !candidates.contains(candidateId)) {
                    candidates.add(candidateId);
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }
            inputs.add(new MaterialIngredient(candidates, 1));
        }
        return inputs;
    }

    /**
     * 读取物品堆的物品资源 ID。
     *
     * @param stack 物品堆
     * @return 资源 ID；无法解析时返回空串
     */
    private static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
