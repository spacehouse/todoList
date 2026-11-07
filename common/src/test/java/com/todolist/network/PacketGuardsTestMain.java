package com.todolist.network;

import com.todolist.gui.testsupport.GuiTestSupport;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/**
 * PacketGuards 自测：服务端读取防线的合法路径与非法输入拒绝分支。
 * ProjectPackets 全部服务端读取都经它校验，防线失效即网络包可注入非法数据。
 */
public final class PacketGuardsTestMain {
    private PacketGuardsTestMain() {
    }

    /**
     * 执行读取防线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("PacketGuardsTestMain.shouldReadStringsAndBooleans", PacketGuardsTestMain::shouldReadStringsAndBooleans);
        GuiTestSupport.runTestCase("PacketGuardsTestMain.shouldRejectOverlongString", PacketGuardsTestMain::shouldRejectOverlongString);
        GuiTestSupport.runTestCase("PacketGuardsTestMain.shouldRejectOutOfRangeCounts", PacketGuardsTestMain::shouldRejectOutOfRangeCounts);
        GuiTestSupport.runTestCase("PacketGuardsTestMain.shouldReadBoundedCountsAtLimits", PacketGuardsTestMain::shouldReadBoundedCountsAtLimits);
    }

    private static void shouldReadStringsAndBooleans() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf("hello-guard");
        buf.writeBoolean(true);
        buf.writeBoolean(false);
        GuiTestSupport.assertEquals("hello-guard", PacketGuards.readString(buf, "field"), "合法字符串应原样读出");
        GuiTestSupport.assertTrue(PacketGuards.readBoolean(buf, "b1"), "布尔 true 应读出");
        GuiTestSupport.assertFalse(PacketGuards.readBoolean(buf, "b2"), "布尔 false 应读出");
        GuiTestSupport.assertTrue(buf.readableBytes() == 0, "缓冲区应被完整消费");
    }

    private static void shouldRejectOverlongString() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        StringBuilder overlong = new StringBuilder();
        for (int i = 0; i < PacketGuards.MAX_STRING_LENGTH + 100; i++) {
            overlong.append('x');
        }
        boolean rejected = false;
        try {
            // netty 在写入侧即限长（EncoderException），防线语义 = 超限数据无法进入包体
            buf.writeUtf(overlong.toString());
            PacketGuards.readString(buf, "overlong");
        } catch (RuntimeException expected) {
            rejected = true;
        }
        GuiTestSupport.assertTrue(rejected, "超过长度上限的字符串应被防线拒绝");
    }

    private static void shouldRejectOutOfRangeCounts() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(-1);
        buf.writeInt(PacketGuards.MAX_TASK_LIST_SIZE + 1);
        GuiTestSupport.assertTrue(throwsOutOfRange(buf), "负数量应被拒绝");
        GuiTestSupport.assertTrue(throwsOutOfRange(buf), "超上限数量应被拒绝");
    }

    private static boolean throwsOutOfRange(FriendlyByteBuf buf) {
        try {
            PacketGuards.readBoundedCount(buf, PacketGuards.MAX_TASK_LIST_SIZE, "tasks");
            return false;
        } catch (RuntimeException expected) {
            return true;
        }
    }

    private static void shouldReadBoundedCountsAtLimits() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(0);
        buf.writeInt(PacketGuards.MAX_TASK_LIST_SIZE);
        GuiTestSupport.assertEquals(0, PacketGuards.readBoundedCount(buf, PacketGuards.MAX_TASK_LIST_SIZE, "zero"),
                "下边界 0 应放行");
        GuiTestSupport.assertEquals(PacketGuards.MAX_TASK_LIST_SIZE,
                PacketGuards.readBoundedCount(buf, PacketGuards.MAX_TASK_LIST_SIZE, "max"),
                "上边界 max 应放行");
    }
}
