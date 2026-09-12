package com.todolist.gui;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.TaskTrigger;

/**
 * TriggerEditScreen 离线自测入口，覆盖触发类型到目标选择器类型的映射与目标数量约束。
 */
public final class TriggerEditScreenTestMain {
    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private TriggerEditScreenTestMain() {
    }

    /**
     * 程序入口，串行执行触发器编辑界面的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TriggerEditScreenTestMain.shouldMapTriggerTypeToSelectorKind", TriggerEditScreenTestMain::shouldMapTriggerTypeToSelectorKind);
        GuiTestSupport.runTestCase("TriggerEditScreenTestMain.shouldForceAdvancementTargetCountToOne", TriggerEditScreenTestMain::shouldForceAdvancementTargetCountToOne);
    }

    /**
     * 校验各触发类型会打开与其匹配的目标选择器，而不是一律打开物品选择器。
     */
    private static void shouldMapTriggerTypeToSelectorKind() {
        GuiTestSupport.assertEquals(ItemSelectorScreen.Kind.ITEM,
                TriggerEditScreen.selectorKind(TaskTrigger.Type.ITEM_COLLECT), "收集物品应使用物品选择器");
        GuiTestSupport.assertEquals(ItemSelectorScreen.Kind.ITEM,
                TriggerEditScreen.selectorKind(TaskTrigger.Type.CRAFT_ITEM), "合成物品应使用物品选择器");
        GuiTestSupport.assertEquals(ItemSelectorScreen.Kind.BLOCK,
                TriggerEditScreen.selectorKind(TaskTrigger.Type.BREAK_BLOCK), "破坏方块应使用方块选择器");
        GuiTestSupport.assertEquals(ItemSelectorScreen.Kind.ENTITY,
                TriggerEditScreen.selectorKind(TaskTrigger.Type.KILL_ENTITY), "击杀实体应使用实体选择器");
        GuiTestSupport.assertEquals(ItemSelectorScreen.Kind.ADVANCEMENT,
                TriggerEditScreen.selectorKind(TaskTrigger.Type.ADVANCEMENT), "获得进度应使用进度选择器");
    }

    /**
     * 校验获得进度触发器的目标数量恒为 1（进度只会达成一次），其余类型至少为 1。
     */
    private static void shouldForceAdvancementTargetCountToOne() {
        GuiTestSupport.assertEquals(1,
                TriggerEditScreen.resolveTargetCount(TaskTrigger.Type.ADVANCEMENT, 64), "获得进度必须锁定为 1");
        GuiTestSupport.assertEquals(1,
                TriggerEditScreen.resolveTargetCount(TaskTrigger.Type.ADVANCEMENT, 0), "获得进度非法输入也应为 1");
        GuiTestSupport.assertEquals(32,
                TriggerEditScreen.resolveTargetCount(TaskTrigger.Type.ITEM_COLLECT, 32), "收集类应保留输入数量");
        GuiTestSupport.assertEquals(1,
                TriggerEditScreen.resolveTargetCount(TaskTrigger.Type.CRAFT_ITEM, 0), "合成类非法输入应回退为 1");
    }
}
