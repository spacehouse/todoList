package com.todolist.task;

import com.todolist.gui.testsupport.GuiTestSupport;
import net.minecraft.nbt.CompoundTag;

/**
 * TaskTriggerTestMain 覆盖触发器实体的进度收敛、类型解析与 NBT 往返序列化。
 */
public final class TaskTriggerTestMain {
    /**
     * 工具类不需要实例化。
     */
    private TaskTriggerTestMain() {
    }

    /**
     * 执行触发器实体测试。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TaskTriggerTestMain.shouldClampProgressAndCount", TaskTriggerTestMain::shouldClampProgressAndCount);
        GuiTestSupport.runTestCase("TaskTriggerTestMain.shouldRoundTripTriggerThroughNbt", TaskTriggerTestMain::shouldRoundTripTriggerThroughNbt);
        GuiTestSupport.runTestCase("TaskTriggerTestMain.shouldRoundTripTriggerInsideTaskNbt", TaskTriggerTestMain::shouldRoundTripTriggerInsideTaskNbt);
        GuiTestSupport.runTestCase("TaskTriggerTestMain.shouldParseTypeWithFallback", TaskTriggerTestMain::shouldParseTypeWithFallback);
        GuiTestSupport.runTestCase("TaskTriggerTestMain.shouldTreatInvalidTriggerAsAbsent", TaskTriggerTestMain::shouldTreatInvalidTriggerAsAbsent);
    }

    /**
     * 验证目标数量下限与进度收敛区间。
     */
    private static void shouldClampProgressAndCount() {
        TaskTrigger zeroCount = new TaskTrigger(TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie", 0);
        GuiTestSupport.assertEquals(1, zeroCount.getTargetCount(), "目标数量最小应为 1");

        TaskTrigger trigger = new TaskTrigger(TaskTrigger.Type.KILL_ENTITY, "minecraft:zombie", 3);
        GuiTestSupport.assertFalse(trigger.addProgress(2), "推进 2/3 不应达标");
        GuiTestSupport.assertEquals(2, trigger.getProgress(), "进度应为 2");
        GuiTestSupport.assertFalse(trigger.addProgress(-5), "负增量不应触发达标");
        GuiTestSupport.assertEquals(0, trigger.getProgress(), "进度下限应收敛为 0");
        trigger.addProgress(10);
        GuiTestSupport.assertEquals(3, trigger.getProgress(), "进度上限应收敛为目标数量");
        GuiTestSupport.assertTrue(trigger.isSatisfied(), "进度达到目标数量后应达标");
    }

    /**
     * 验证触发器 NBT 往返保留全部字段。
     */
    private static void shouldRoundTripTriggerThroughNbt() {
        TaskTrigger trigger = new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 64);
        trigger.setProgress(12);
        CompoundTag nbt = trigger.toNbt();

        TaskTrigger restored = TaskTrigger.fromNbt(nbt);
        GuiTestSupport.assertEquals(trigger, restored, "触发器 NBT 往返应保留字段");
        GuiTestSupport.assertEquals(12, restored.getProgress(), "往返后进度应保留");
        GuiTestSupport.assertEquals(64, restored.getTargetCount(), "往返后目标数量应保留");
        GuiTestSupport.assertEquals("minecraft:iron_ingot", restored.getTarget(), "往返后目标资源 ID 应保留");
    }

    /**
     * 验证任务 NBT 往返保留触发器字段且无触发器任务不受影响。
     */
    private static void shouldRoundTripTriggerInsideTaskNbt() {
        Task withTrigger = new Task("收集铁锭", "");
        withTrigger.setTrigger(new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 32));
        Task restored = Task.fromNbt(withTrigger.toNbt());
        GuiTestSupport.assertTrue(restored.hasTrigger(), "任务 NBT 往返应保留触发器");
        GuiTestSupport.assertEquals(32, restored.getTrigger().getTargetCount(), "往返后触发器目标数量应保留");

        Task plain = Task.fromNbt(new Task("普通任务", "").toNbt());
        GuiTestSupport.assertFalse(plain.hasTrigger(), "无触发器任务往返后不应凭空出现触发器");
    }

    /**
     * 验证类型解析的大小写与非法值回退。
     */
    private static void shouldParseTypeWithFallback() {
        GuiTestSupport.assertEquals(
                TaskTrigger.Type.KILL_ENTITY,
                TaskTrigger.Type.parse("kill_entity"),
                "小写类型名应正确解析"
        );
        GuiTestSupport.assertEquals(
                TaskTrigger.Type.BREAK_BLOCK,
                TaskTrigger.Type.parse("Break_Block"),
                "混合大小写类型名应正确解析"
        );
        GuiTestSupport.assertEquals(
                TaskTrigger.Type.ITEM_COLLECT,
                TaskTrigger.Type.parse("not_a_type"),
                "非法类型名应回退为 ITEM_COLLECT"
        );
        GuiTestSupport.assertEquals(
                TaskTrigger.Type.ITEM_COLLECT,
                TaskTrigger.Type.parse(null),
                "空类型名应回退为 ITEM_COLLECT"
        );
    }

    /**
     * 验证空目标触发器按无效处理，任务读取时会被剔除。
     */
    private static void shouldTreatInvalidTriggerAsAbsent() {
        TaskTrigger invalid = new TaskTrigger(TaskTrigger.Type.KILL_ENTITY, "  ", 1);
        GuiTestSupport.assertFalse(invalid.isValid(), "空目标触发器应判定为无效");

        CompoundTag nbt = new CompoundTag();
        nbt.putString("title", "无效触发任务");
        nbt.putString("description", "");
        nbt.putString("priority", "MEDIUM");
        nbt.putLong("createdAt", 1L);
        CompoundTag triggerTag = new CompoundTag();
        triggerTag.putString("type", "KILL_ENTITY");
        triggerTag.putString("target", "");
        triggerTag.putInt("targetCount", 1);
        nbt.put("trigger", triggerTag);
        Task restored = Task.fromNbt(nbt);
        GuiTestSupport.assertFalse(restored.hasTrigger(), "空目标触发器在任务反序列化时应被剔除");
    }
}
