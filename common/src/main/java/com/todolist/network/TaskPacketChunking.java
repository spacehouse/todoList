package com.todolist.network;

import com.todolist.TodoConstants;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 团队任务网络包分块工具。
 * 当任务列表序列化后超过 Minecraft 自定义负载 32767 字节限制时，将原始字节流拆分为多个小包发送，接收端按会话 ID 重组。
 */
public final class TaskPacketChunking {

    /** 每个分片的最大字节数，留出包头与安全余量。 */
    public static final int MAX_CHUNK_BYTES = 28000;

    /** 单个会话的最大分片数，防止恶意构造的超大 totalChunks。 */
    private static final int MAX_TOTAL_CHUNKS = 256;

    private TaskPacketChunking() {
    }

    /**
     * 将已序列化的完整负载拆分为多个分片。
     *
     * @param data 完整负载字节数组
     * @return 分片列表，每个元素是一段原始字节
     */
    public static List<byte[]> splitPayload(byte[] data) {
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int end = Math.min(offset + MAX_CHUNK_BYTES, data.length);
            chunks.add(Arrays.copyOfRange(data, offset, end));
            offset = end;
        }
        return chunks;
    }

    /**
     * 将一个分片写入网络缓冲区。
     *
     * @param buf 目标缓冲区
     * @param sessionId 会话 ID（标识同一次分包传输）
     * @param totalChunks 总分片数
     * @param chunkIndex 当前分片序号（从 0 开始）
     * @param data 当前分片的原始字节
     */
    public static void writeChunk(FriendlyByteBuf buf, String sessionId, int totalChunks, int chunkIndex, byte[] data) {
        buf.writeUtf(sessionId);
        buf.writeInt(totalChunks);
        buf.writeInt(chunkIndex);
        buf.writeByteArray(data);
    }

    /**
     * 从网络缓冲区读取一个分片。
     *
     * @param buf 源缓冲区
     * @return 分片数据对象
     */
    public static ChunkData readChunk(FriendlyByteBuf buf) {
        String sessionId = buf.readUtf();
        int totalChunks = buf.readInt();
        int chunkIndex = buf.readInt();
        byte[] data = buf.readByteArray();
        return new ChunkData(sessionId, totalChunks, chunkIndex, data);
    }

    /**
     * 判断任务列表是否需要分块发送。
     *
     * @param buf 用于估算的临时缓冲区（调用前为空，函数不改变其内容）
     * @return 如果缓冲区可读字节数超过阈值则返回 true
     */
    public static boolean needsChunking(FriendlyByteBuf buf) {
        return buf.readableBytes() > MAX_CHUNK_BYTES;
    }

    /**
     * 将任务列表序列化到字节数组。
     *
     * @param tasks 任务列表
     * @return 序列化后的完整字节数组
     */
    public static byte[] serializeTasks(List<com.todolist.task.Task> tasks) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    /**
     * 将两份任务列表（tasks + baseTasks）序列化到字节数组。
     *
     * @param tasks 主任务列表
     * @param baseTasks 基线任务列表
     * @return 序列化后的完整字节数组
     */
    public static byte[] serializeMergeTasks(List<com.todolist.task.Task> tasks, List<com.todolist.task.Task> baseTasks) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        TaskPackets.writeTaskList(buf, tasks);
        TaskPackets.writeTaskList(buf, baseTasks);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    /**
     * 从重组后的完整字节数据中读取任务列表。
     *
     * @param data 完整字节数据
     * @return 任务列表
     */
    public static List<com.todolist.task.Task> deserializeTasks(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
        return TaskPackets.readTaskList(buf);
    }

    /**
     * 从重组后的完整字节数据中读取两份任务列表（tasks + baseTasks）。
     *
     * @param data 完整字节数据
     * @return 包含 tasks 和 baseTasks 的数组，[0]=tasks, [1]=baseTasks
     */
    @SuppressWarnings("unchecked")
    public static List<com.todolist.task.Task>[] deserializeMergeTasks(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data));
        List<com.todolist.task.Task> tasks = TaskPackets.readTaskList(buf);
        List<com.todolist.task.Task> baseTasks = buf.readableBytes() > 0 ? TaskPackets.readTaskList(buf) : null;
        return new List[]{tasks, baseTasks};
    }

    /**
     * 生成一个新的会话 ID。
     *
     * @return 唯一会话标识符
     */
    public static String newSessionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 单个分片的数据载体。
     */
    public static final class ChunkData {
        public final String sessionId;
        public final int totalChunks;
        public final int chunkIndex;
        public final byte[] data;

        public ChunkData(String sessionId, int totalChunks, int chunkIndex, byte[] data) {
            this.sessionId = sessionId;
            this.totalChunks = totalChunks;
            this.chunkIndex = chunkIndex;
            this.data = data;
        }
    }

    /**
     * 分片累积器：按会话 ID 收集分片，全部到齐后重组为完整数据。
     * 用于服务端和客户端接收侧。
     */
    public static final class ChunkAccumulator {

        private final Map<String, SessionBuffer> sessions = new ConcurrentHashMap<>();

        /**
         * 存入一个分片，返回完整数据（如果所有分片已到齐），否则返回 null。
         *
         * @param chunk 分片数据
         * @return 所有分片到齐时返回完整字节数组，否则 null
         */
        public byte[] accept(ChunkData chunk) {
            if (chunk.totalChunks <= 0 || chunk.totalChunks > MAX_TOTAL_CHUNKS) {
                TodoConstants.LOGGER.warn("Dropping chunk with invalid totalChunks: {}", chunk.totalChunks);
                return null;
            }
            if (chunk.chunkIndex < 0 || chunk.chunkIndex >= chunk.totalChunks) {
                TodoConstants.LOGGER.warn("Dropping chunk with invalid chunkIndex: {}/{}", chunk.chunkIndex, chunk.totalChunks);
                return null;
            }
            SessionBuffer session = sessions.computeIfAbsent(chunk.sessionId, k -> new SessionBuffer(chunk.totalChunks));
            if (session.totalChunks != chunk.totalChunks) {
                TodoConstants.LOGGER.warn("Dropping chunk with mismatched totalChunks for session {}: expected={}, got={}",
                        chunk.sessionId, session.totalChunks, chunk.totalChunks);
                return null;
            }
            session.setChunk(chunk.chunkIndex, chunk.data);
            if (session.isComplete()) {
                sessions.remove(chunk.sessionId);
                return session.assemble();
            }
            return null;
        }

        /**
         * 清理所有正在累积的会话（用于断开连接或重置）。
         */
        public void clear() {
            sessions.clear();
        }

        private static final class SessionBuffer {
            private final int totalChunks;
            private final byte[][] chunks;
            private int receivedCount;

            SessionBuffer(int totalChunks) {
                this.totalChunks = totalChunks;
                this.chunks = new byte[totalChunks][];
                this.receivedCount = 0;
            }

            void setChunk(int index, byte[] data) {
                if (chunks[index] != null) {
                    return;
                }
                chunks[index] = data;
                receivedCount++;
            }

            boolean isComplete() {
                return receivedCount == totalChunks;
            }

            byte[] assemble() {
                int totalLength = 0;
                for (byte[] chunk : chunks) {
                    totalLength += chunk.length;
                }
                byte[] result = new byte[totalLength];
                int offset = 0;
                for (byte[] chunk : chunks) {
                    System.arraycopy(chunk, 0, result, offset, chunk.length);
                    offset += chunk.length;
                }
                return result;
            }
        }
    }
}
