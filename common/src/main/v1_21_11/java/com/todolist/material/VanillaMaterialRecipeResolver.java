package com.todolist.material;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 原版配方解析器（对应设计文档 L1）——1.21.2~1.21.8 覆盖版。
 *
 * <p>覆盖 7 种原版配方类型：crafting / smelting / blasting / smoking / campfire /
 * stonecutting / smithing。1.21.2 起 {@code Recipe#getIngredients()} 与
 * {@code Recipe#getResultItem(RegistryAccess)} 从接口层移除，本版改走：
 * <ul>
 *   <li>输入：{@link Recipe#placementInfo()} 的 {@code ingredients()}（全类型通用的原料列表，
 *       物品标签经 {@link Ingredient#items()} 展开为候选）；</li>
 *   <li>产物：{@link Recipe#display()} 的首个 {@code SlotDisplay.ItemStackSlotDisplay}（原版标准
 *       配方的展示结果即产物堆）；</li>
 *   <li>配方 ID：{@code holder.id()} 在 1.21.2+ 返回 {@code ResourceKey}，经 {@code location()} 取
 *       {@link Identifier}。</li>
 * </ul>
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
     * @param holder         MC 配方持有者
     * @param registryAccess 注册表访问
     * @return 中立配方；无法解析时返回 null
     */
    @Override
    public MaterialRecipe resolve(RecipeHolder<?> holder, RegistryAccess registryAccess) {
        if (holder == null || holder.value() == null) {
            return null;
        }
        Recipe<?> recipe = holder.value();
        MaterialRecipeKind kind = KIND_BY_TYPE.get(recipe.getType());
        if (kind == null) {
            return null;
        }
        ItemStack output = safeResult(recipe);
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
        Identifier recipeId = holder.id().identifier();
        return new MaterialRecipe(
                recipeId == null ? "" : recipeId.toString(),
                outputItemId,
                output.getCount(),
                inputs,
                kind
        );
    }

    /**
     * 安全读取配方产物：遍历配方展示形态，取首个物品堆形态的结果。
     * 1.21.2 起原版标准配方的展示结果即产物堆（{@code SlotDisplay.ItemStackSlotDisplay}）。
     *
     * @param recipe 配方
     * @return 产物物品堆；不可用时返回空堆
     */
    private static ItemStack safeResult(Recipe<?> recipe) {
        try {
            List<RecipeDisplay> displays = recipe.display();
            if (displays != null) {
                for (RecipeDisplay display : displays) {
                    SlotDisplay resultSlot = resultSlotOf(display);
                    ItemStack stack = stackOf(resultSlot);
                    if (stack != null && !stack.isEmpty()) {
                        return stack;
                    }
                }
            }
            // 锻造配方（SmithingRecipeDisplay 无产物槽位）：以空输入试探组装
            if (recipe instanceof net.minecraft.world.item.crafting.SmithingRecipe smithing) {
                ItemStack assembled = smithing.assemble(
                        new net.minecraft.world.item.crafting.SmithingRecipeInput(
                                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY),
                        null);
                if (assembled != null && !assembled.isEmpty()) {
                    return assembled;
                }
            }
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 从展示形态中取产物槽位描述符。
     * 各展示 record 的产物字段名不同，此处逐一分派。
     *
     * @param display 配方展示形态
     * @return 产物槽位描述符；该形态不含产物时返回 null
     */
    private static SlotDisplay resultSlotOf(RecipeDisplay display) {
        if (display instanceof net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped) {
            return shaped.result();
        }
        if (display instanceof net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.result();
        }
        if (display instanceof net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay furnace) {
            return furnace.result();
        }
        if (display instanceof net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay stonecutter) {
            return stonecutter.result();
        }
        return null;
    }

    /**
     * 从产物槽位描述符解析物品堆。
     * 数据包配方的槽位可能是物品堆形态（直接带数量）或物品形态（数量 1），两种都要识别。
     *
     * @param slot 产物槽位描述符
     * @return 物品堆；无法解析时返回 null
     */
    private static ItemStack stackOf(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            return stackDisplay.stack();
        }
        if (slot instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            return new ItemStack(itemDisplay.item());
        }
        return null;
    }

    /**
     * 读取配方的输入项（1.21.2+ 通路：{@code placementInfo().ingredients()}）。
     * 每个 {@link Ingredient} 作为一个输入项，其候选物品由 {@link Ingredient#items()} 展开
     * （物品标签会展开成全部候选）。
     *
     * @param recipe 配方
     * @return 输入项列表
     */
    private static List<MaterialIngredient> collectInputs(Recipe<?> recipe) {
        List<MaterialIngredient> inputs = new ArrayList<>();
        PlacementInfo placementInfo;
        try {
            placementInfo = recipe.placementInfo();
        } catch (RuntimeException exception) {
            return inputs;
        }
        if (placementInfo == null || placementInfo.ingredients() == null) {
            return inputs;
        }
        for (Ingredient ingredient : placementInfo.ingredients()) {
            if (ingredient == null) {
                continue;
            }
            List<String> candidates = new ArrayList<>();
            java.util.stream.Stream<net.minecraft.core.Holder<net.minecraft.world.item.Item>> items;
            try {
                items = ingredient.items();
            } catch (RuntimeException exception) {
                continue;
            }
            if (items == null) {
                continue;
            }
            for (net.minecraft.core.Holder<net.minecraft.world.item.Item> holder : items.toList()) {
                if (holder == null || holder.value() == null) {
                    continue;
                }
                String candidateId = BuiltInRegistries.ITEM.getKey(holder.value()).toString();
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
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
