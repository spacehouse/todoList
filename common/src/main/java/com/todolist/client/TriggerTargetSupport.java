package com.todolist.client;

import com.todolist.task.TaskTrigger;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.Locale;

/**
 * 触发器目标解析支持类。
 * 为 HUD 进度图标、自动完成提示与触发器编辑器提供统一解析：
 * 把「触发类型 + 目标资源 ID」映射为可绘制的物品图标 ID、本地化显示名与默认任务标题，
 * 覆盖物品 / 方块 / 实体 / 进度四类目标。
 */
public final class TriggerTargetSupport {
    /** 刷怪蛋物品命名后缀，用于实体目标的图标降级解析。 */
    private static final String SPAWN_EGG_SUFFIX = "_spawn_egg";

    private TriggerTargetSupport() {
    }

    /**
     * 解析触发器目标的图标物品 ID。
     *
     * @param trigger 触发器
     * @return 物品资源 ID；无法解析时返回 null
     */
    public static String resolveIconId(TaskTrigger trigger) {
        if (trigger == null) {
            return null;
        }
        return resolveIconId(trigger.getType(), trigger.getTarget());
    }

    /**
     * 解析指定类型与目标的图标物品 ID。
     *
     * @param type   触发类型
     * @param target 目标资源 ID
     * @return 物品资源 ID；无法解析时返回 null
     */
    public static String resolveIconId(TaskTrigger.Type type, String target) {
        if (type == null || target == null || target.isEmpty()) {
            return null;
        }
        switch (type) {
            case ITEM_COLLECT:
            case CRAFT_ITEM:
                return resolveItemId(target);
            case BREAK_BLOCK:
                return resolveBlockItemId(target);
            case KILL_ENTITY:
                return resolveSpawnEggId(target);
            default:
                return null;
        }
    }

    /**
     * 解析目标图标的物品堆，供列表与提示直接绘制。
     *
     * @param type   触发类型
     * @param target 目标资源 ID
     * @return 物品堆；无法解析时返回空堆
     */
    public static ItemStack resolveIconStack(TaskTrigger.Type type, String target) {
        String itemId = resolveIconId(type, target);
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = ItemTitleRenderer.resolveItemStack(itemId);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /**
     * 解析目标的本地化显示名；无法解析时回退为资源 ID 原文。
     *
     * @param type   触发类型
     * @param target 目标资源 ID
     * @return 显示名
     */
    public static String resolveTargetDisplayName(TaskTrigger.Type type, String target) {
        if (type == null || target == null || target.isEmpty()) {
            return "";
        }
        switch (type) {
            case ITEM_COLLECT:
            case CRAFT_ITEM: {
                Item item = resolveItem(target);
                return item == null ? target : item.getName(new ItemStack(item)).getString();
            }
            case BREAK_BLOCK: {
                Block block = resolveBlock(target);
                return block == null ? target : block.getName().getString();
            }
            case KILL_ENTITY: {
                EntityType<?> entityType = resolveEntityType(target);
                return entityType == null ? target : entityType.getDescription().getString();
            }
            case ADVANCEMENT: {
                String title = resolveAdvancementTitle(target);
                return title == null || title.isEmpty() ? target : title;
            }
            default:
                return target;
        }
    }

    /**
     * 依据触发器生成默认任务标题，如「收集 铁锭 ×32」。
     * 物品 / 合成 / 破坏方块类目标会内联为 `[item:...]` 标记，使标题渲染出物品图标；
     * 实体与进度类目标使用本地化名称（进度取客户端已加载的进度标题）。
     *
     * @param trigger 触发器
     * @return 默认标题文本
     */
    public static Component buildDefaultTaskTitle(TaskTrigger trigger) {
        if (trigger == null || trigger.getType() == null) {
            return Component.empty();
        }
        String nameToken = resolveTitleTargetToken(trigger.getType(), trigger.getTarget());
        return Component.translatable(
                "gui.todolist.trigger.default_title." + trigger.getType().name().toLowerCase(Locale.ROOT),
                nameToken,
                trigger.getTargetCount()
        );
    }

    /**
     * 解析标题中的目标片段：物品类目标返回 `[item:...]` 标记（渲染为图标 + 名称）。
     *
     * @param type   触发类型
     * @param target 目标资源 ID
     * @return 标题片段文本
     */
    private static String resolveTitleTargetToken(TaskTrigger.Type type, String target) {
        switch (type) {
            case ITEM_COLLECT:
            case CRAFT_ITEM:
            case BREAK_BLOCK: {
                String itemId = resolveIconId(type, target);
                return itemId == null ? resolveTargetDisplayName(type, target) : "[item:" + itemId + "]";
            }
            default:
                return resolveTargetDisplayName(type, target);
        }
    }

    /**
     * 从客户端已加载的进度中解析进度标题。
     *
     * @param advancementId 进度资源 ID
     * @return 进度标题；无法解析时返回 null
     */
    private static String resolveAdvancementTitle(String advancementId) {
        ResourceLocation location = ResourceLocation.tryParse(advancementId);
        if (location == null) {
            return null;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return null;
        }
        ClientAdvancements advancements = client.getConnection().getAdvancements();
        if (advancements == null || advancements.getAdvancements() == null) {
            return null;
        }
        Advancement advancement = advancements.getAdvancements().get(location);
        if (advancement == null || advancement.getDisplay() == null) {
            return null;
        }
        return advancement.getDisplay().getTitle().getString();
    }

    /**
     * 判断资源 ID 是否为已注册物品。
     *
     * @param itemId 物品资源 ID
     * @return 已注册时返回 true
     */
    private static String resolveItemId(String itemId) {
        return resolveItem(itemId) == null ? null : itemId;
    }

    /**
     * 解析方块对应的物品形态 ID。
     *
     * @param blockId 方块资源 ID
     * @return 物品资源 ID；方块无对应物品时返回 null
     */
    private static String resolveBlockItemId(String blockId) {
        Block block = resolveBlock(blockId);
        if (block == null) {
            return null;
        }
        Item item = block.asItem();
        if (item == null || item == Items.AIR) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /**
     * 按「实体 ID + _spawn_egg」约定解析刷怪蛋物品 ID。
     *
     * @param entityId 实体资源 ID
     * @return 刷怪蛋物品 ID；不存在时返回 null
     */
    private static String resolveSpawnEggId(String entityId) {
        ResourceLocation location = ResourceLocation.tryParse(entityId);
        if (location == null) {
            return null;
        }
        ResourceLocation eggId = new ResourceLocation(location.getNamespace(), location.getPath() + SPAWN_EGG_SUFFIX);
        return BuiltInRegistries.ITEM.containsKey(eggId) ? eggId.toString() : null;
    }

    /**
     * 按资源 ID 解析物品。
     *
     * @param itemId 物品资源 ID
     * @return 物品实例；无法解析时返回 null
     */
    private static Item resolveItem(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
        return item == null || item == Items.AIR ? null : item;
    }

    /**
     * 按资源 ID 解析方块。
     *
     * @param blockId 方块资源 ID
     * @return 方块实例；无法解析时返回 null
     */
    private static Block resolveBlock(String blockId) {
        ResourceLocation location = ResourceLocation.tryParse(blockId);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }

    /**
     * 按资源 ID 解析实体类型。
     *
     * @param entityId 实体资源 ID
     * @return 实体类型；无法解析时返回 null
     */
    private static EntityType<?> resolveEntityType(String entityId) {
        ResourceLocation location = ResourceLocation.tryParse(entityId);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
    }
}
