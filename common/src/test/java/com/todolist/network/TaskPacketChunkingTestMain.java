package com.todolist.network;

import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskTrigger;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * TaskPacketChunking 自测：分块切分边界、writeChunk/readChunk 往返与任务列表分块序列化往返。
 * 分块链路坏一字节即丢任务数据，且离线可全量验证（纯逻辑 + FriendlyByteBuf）。
 */
public final class TaskPacketChunkingTestMain {
    private TaskPacketChunkingTestMain() {
    }

    /**
     * 执行分块链路自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("TaskPacketChunkingTestMain.shouldRoundTripSmallTaskListWithoutChunking", TaskPacketChunkingTestMain::shouldRoundTripSmallTaskListWithoutChunking);
        GuiTestSupport.runTestCase("TaskPacketChunkingTestMain.shouldSplitOversizedPayloadWithTailChunk", TaskPacketChunkingTestMain::shouldSplitOversizedPayloadWithTailChunk);
        GuiTestSupport.runTestCase("TaskPacketChunkingTestMain.shouldRoundTripChunkFrames", TaskPacketChunkingTestMain::shouldRoundTripChunkFrames);
        GuiTestSupport.runTestCase("TaskPacketChunkingTestMain.shouldRoundTripLargeTaskListThroughChunks", TaskPacketChunkingTestMain::shouldRoundTripLargeTaskListThroughChunks);
        GuiTestSupport.runTestCase("TaskPacketChunkingTestMain.shouldRoundTripTriggerFieldsThroughSerialize", TaskPacketChunkingTestMain::shouldRoundTripTriggerFieldsThroughSerialize);
    }

    private static void shouldRoundTripSmallTaskListWithoutChunking() {
        List<Task> tasks = List.of(buildTask("chunk-small-a"), buildTask("chunk-small-b"));
        byte[] data = TaskPacketChunking.serializeTasks(tasks);
        List<Task> reloaded = TaskPacketChunking.deserializeTasks(data);
        GuiTestSupport.assertEquals(tasks.size(), reloaded.size(), "小任务列表往返数量应一致");
        GuiTestSupport.assertEquals(tasks.get(0).getTitle(), reloaded.get(0).getTitle(), "标题应往返一致");
    }

    private static void shouldSplitOversizedPayloadWithTailChunk() {
        // 非整除尾块：MAX*2 + 1000 → 3 块，尾块 1000 字节
        byte[] payload = new byte[TaskPacketChunking.MAX_CHUNK_BYTES * 2 + 1000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        List<byte[]> chunks = TaskPacketChunking.splitPayload(payload);
        GuiTestSupport.assertEquals(3, chunks.size(), "应切成 3 块");
        GuiTestSupport.assertEquals(TaskPacketChunking.MAX_CHUNK_BYTES, chunks.get(0).length, "首块应为满块");
        GuiTestSupport.assertEquals(1000, chunks.get(2).length, "尾块应为剩余字节");
        byte[] joined = join(chunks);
        GuiTestSupport.assertTrue(java.util.Arrays.equals(payload, joined), "分块拼接应还原原始字节");
    }

    private static void shouldRoundTripChunkFrames() {
        byte[] data = new byte[4096];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (255 - i);
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TaskPacketChunking.writeChunk(buf, "session-1", 7, 3, data);
        TaskPacketChunking.ChunkData read = TaskPacketChunking.readChunk(buf);
        GuiTestSupport.assertEquals("session-1", read.sessionId, "会话 ID 应往返一致");
        GuiTestSupport.assertEquals(7, read.totalChunks, "总分片数应往返一致");
        GuiTestSupport.assertEquals(3, read.chunkIndex, "分片序号应往返一致");
        GuiTestSupport.assertTrue(java.util.Arrays.equals(data, read.data), "分片字节应往返一致");
        GuiTestSupport.assertTrue(buf.readableBytes() == 0, "缓冲区应被完整消费");
    }

    private static void shouldRoundTripLargeTaskListThroughChunks() {
        // 构造足够大的任务列表（含长描述）触发分块，经 serialize→split→重组→deserialize
        List<Task> tasks = new ArrayList<>();
        StringBuilder desc = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            desc.append("描述内容用于撑大序列化体积-").append(i).append('-');
        }
        for (int i = 0; i < 40; i++) {
            Task t = buildTask("chunk-large-" + i);
            t.setDescription(desc.toString());
            tasks.add(t);
        }
        byte[] data = TaskPacketChunking.serializeTasks(tasks);
        GuiTestSupport.assertTrue(data.length > TaskPacketChunking.MAX_CHUNK_BYTES,
                "构造的负载应超过分块阈值，实际 " + data.length);
        List<byte[]> chunks = TaskPacketChunking.splitPayload(data);
        byte[] joined = join(chunks);
        List<Task> reloaded = TaskPacketChunking.deserializeTasks(joined);
        GuiTestSupport.assertEquals(tasks.size(), reloaded.size(), "分块往返后任务数量应一致");
        GuiTestSupport.assertEquals(tasks.get(39).getDescription(), reloaded.get(39).getDescription(),
                "长描述应往返一致");
    }

    private static void shouldRoundTripTriggerFieldsThroughSerialize() {
        Task t = buildTask("chunk-trigger");
        t.setTrigger(new TaskTrigger(TaskTrigger.Type.ITEM_COLLECT, "minecraft:iron_ingot", 32));
        t.getTrigger().setProgress(7);
        List<Task> reloaded = TaskPacketChunking.deserializeTasks(
                TaskPacketChunking.serializeTasks(List.of(t)));
        GuiTestSupport.assertEquals(1, reloaded.size(), "数量应一致");
        GuiTestSupport.assertTrue(reloaded.get(0).getTrigger() != null, "触发器应随序列化保留");
        GuiTestSupport.assertEquals(32, reloaded.get(0).getTrigger().getTargetCount(), "目标数量应保留");
        GuiTestSupport.assertEquals(7, reloaded.get(0).getTrigger().getProgress(), "进度应保留");
    }

    private static Task buildTask(String title) {
        Task t = new Task(title, title + "-desc");
        t.setProjectId("chunk-project");
        return t;
    }

    private static byte[] join(List<byte[]> chunks) {
        int total = 0;
        for (byte[] c : chunks) {
            total += c.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, out, offset, c.length);
            offset += c.length;
        }
        return out;
    }
}
