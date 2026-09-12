package com.todolist.client;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.TaskTrigger;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * TriggerTargetSupport 离线自测入口，覆盖触发器目标到图标 / 显示名 / 默认标题的解析。
 */
public final class TriggerTargetSupportTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private TriggerTargetSupportTestMain() {
    }

    /**
     * 程序入口，串行执行触发器目标解析的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.bootstrapEnvironment();
        GuiTestSupport.runTestCase("TriggerTargetSupportTestMain.shouldResolveItemIconForItemTargets", TriggerTargetSupportTestMain::shouldResolveItemIconForItemTargets);
        GuiTestSupport.runTestCase("TriggerTargetSupportTestMain.shouldResolveBlockIconThroughItemForm", TriggerTargetSupportTestMain::shouldResolveBlockIconThroughItemForm);
        GuiTestSupport.runTestCase("TriggerTargetSupportTestMain.shouldReturnNullForUnresolvableTargets", TriggerTargetSupportTestMain::shouldReturnNullForUnresolvableTargets);
        GuiTestSupport.runTestCase("TriggerTargetSupportTestMain.shouldBuildNonEmptyDefaultTitle", TriggerTargetSupportTestMain::shouldBuildNonEmptyDefaultTitle);
        GuiTestSupport.runTestCase("TriggerTargetSupportTestMain.shouldEmbedItemMarkupInDefaultTitle", TriggerTargetSupportTestMain::shouldEmbedItemMarkupInDefaultTitle);
        GuiTestSupport.runTestCase("TriggerTargetSupportTestMain.shouldFallbackAdvancementDisplayNameToRawId", TriggerTargetSupportTestMain::shouldFallbackAdvancementDisplayNameToRawId);
    }

    /**
     * 校验物品与合成类目标直接使用目标物品作为图标。
     */
    private static void shouldResolveItemIconForItemTargets() {
        GuiTestSupport.assertTrue(BuiltInRegistries.ITEM.containsKey(new net.minecraft.resources.ResourceLocation("minecraft", "iron_ingot")),
                "测试环境应已注册原版物品");
        GuiTestSupport.assertEquals("minecraft:iron_ingot",
                TriggerTargetSupport.resolveIconId(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot"),
                "收集类目标应直接返回目标物品作为图标");
        GuiTestSupport.assertEquals("minecraft:iron_ingot",
                TriggerTargetSupport.resolveIconId(TaskTrigger.Type.CRAFT_ITEM, "minecraft:iron_ingot"),
                "合成类目标应直接返回目标物品作为图标");
    }

    /**
     * 校验破坏方块类目标通过方块的物品形态解析出图标。
     */
    private static void shouldResolveBlockIconThroughItemForm() {
        GuiTestSupport.assertEquals("minecraft:stone",
                TriggerTargetSupport.resolveIconId(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone"),
                "破坏方块类目标应解析为方块对应的物品图标");
    }

    /**
     * 校验无法解析的目标返回空图标，不抛异常。
     */
    private static void shouldReturnNullForUnresolvableTargets() {
        GuiTestSupport.assertNull(TriggerTargetSupport.resolveIconId(TaskTrigger.Type.ITEM_COLLECT, "todolist:not_an_item"),
                "未注册物品应返回空图标");
        GuiTestSupport.assertNull(TriggerTargetSupport.resolveIconId(TaskTrigger.Type.BREAK_BLOCK, "todolist:not_a_block"),
                "未注册方块应返回空图标");
        GuiTestSupport.assertNull(TriggerTargetSupport.resolveIconId(TaskTrigger.Type.ADVANCEMENT, "minecraft:story/root"),
                "进度类目标没有图标");
        GuiTestSupport.assertNull(TriggerTargetSupport.resolveIconId(null, "minecraft:stone"),
                "空类型应返回空图标");
    }

    /**
     * 校验默认任务标题生成不抛异常且非空。
     */
    private static void shouldBuildNonEmptyDefaultTitle() {
        TaskTrigger trigger = new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 32);
        GuiTestSupport.assertNotNull(TriggerTargetSupport.buildDefaultTaskTitle(trigger), "默认标题不应为 null");
        GuiTestSupport.assertTrue(!TriggerTargetSupport.buildDefaultTaskTitle(trigger).getString().isEmpty(),
                "默认标题文本不应为空");
        GuiTestSupport.assertNotNull(TriggerTargetSupport.buildDefaultTaskTitle(null), "空触发器应返回空文本而非抛异常");
    }

    /**
     * 校验由触发器创建的任务默认标题内联 `[item:...]` 标记（渲染为物品图标），
     * 覆盖收集 / 合成 / 破坏方块三类目标。
     * 离线环境不加载语言文件，因此从 {@code TranslatableContents} 的参数位取值断言。
     */
    private static void shouldEmbedItemMarkupInDefaultTitle() {
        GuiTestSupport.assertEquals("[item:minecraft:iron_ingot]",
                titleTargetArgument(new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 32)),
                "收集类默认标题应内联物品标记");
        GuiTestSupport.assertEquals("[item:minecraft:oak_planks]",
                titleTargetArgument(new TaskTrigger(TaskTrigger.Type.CRAFT_ITEM, "minecraft:oak_planks", 4)),
                "合成类默认标题应内联物品标记");
        GuiTestSupport.assertEquals("[item:minecraft:stone]",
                titleTargetArgument(new TaskTrigger(TaskTrigger.Type.BREAK_BLOCK, "minecraft:stone", 1)),
                "破坏方块类默认标题应内联方块物品标记");
    }

    /**
     * 提取默认标题可翻译组件的第一个参数（目标片段）。
     *
     * @param trigger 触发器
     * @return 目标片段文本
     */
    private static String titleTargetArgument(TaskTrigger trigger) {
        net.minecraft.network.chat.Component title = TriggerTargetSupport.buildDefaultTaskTitle(trigger);
        if (title == null || !(title.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents)) {
            return "<not-translatable>";
        }
        Object[] args = contents.getArgs();
        return args != null && args.length > 0 && args[0] instanceof String value ? value : "<no-arg>";
    }

    /**
     * 校验离线（无客户端连接）环境下进度目标显示名回退为资源 ID 原文，
     * 保证渲染端拿不到进度列表时不显示错误占位。
     */
    private static void shouldFallbackAdvancementDisplayNameToRawId() {
        String advancementId = "minecraft:story/mine_stone";
        GuiTestSupport.assertEquals(advancementId,
                TriggerTargetSupport.resolveTargetDisplayName(TaskTrigger.Type.ADVANCEMENT, advancementId),
                "离线环境进度显示名应回退为资源 ID 原文");
    }
}
