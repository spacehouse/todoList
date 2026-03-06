package com.todolist.network;

import com.todolist.TodoConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * 网络包读取防护工具：对来自客户端的数据做长度/范围校验并统一抛错。
 */
public final class PacketGuards {
    public static final int MAX_STRING_LENGTH = 32767;
    public static final int MAX_TASK_LIST_SIZE = 10_000;
    public static final int MAX_PROJECT_LIST_SIZE = 2_000;
    public static final int MAX_TAGS_PER_TASK = 512;
    public static final int MAX_SUBTASKS_PER_TASK = 1_024;

    private PacketGuards() {
    }

    /**
     * 从缓冲区读取受限长度的字符串字段。
     */
    public static String readString(PacketByteBuf buf, String fieldName) {
        try {
            return buf.readString(MAX_STRING_LENGTH);
        } catch (RuntimeException ex) {
            throw malformedPacket("Invalid string field: " + fieldName, ex);
        }
    }

    /**
     * 从缓冲区读取列表数量，并限制在 [0, max] 范围内。
     */
    public static int readBoundedCount(PacketByteBuf buf, int max, String fieldName) {
        final int count;
        try {
            count = buf.readInt();
        } catch (RuntimeException ex) {
            throw malformedPacket("Invalid list count field: " + fieldName, ex);
        }
        if (count < 0 || count > max) {
            throw malformedPacket("Out-of-range list count for " + fieldName + ": " + count + ", max=" + max, null);
        }
        return count;
    }

    /**
     * 从缓冲区读取布尔字段。
     */
    public static boolean readBoolean(PacketByteBuf buf, String fieldName) {
        try {
            return buf.readBoolean();
        } catch (RuntimeException ex) {
            throw malformedPacket("Invalid boolean field: " + fieldName, ex);
        }
    }

    /**
     * 从缓冲区读取 NBT Compound 字段（不允许为 null）。
     */
    public static NbtCompound readNbt(PacketByteBuf buf, String fieldName) {
        try {
            NbtCompound nbt = buf.readNbt();
            if (nbt == null) {
                throw malformedPacket("Missing NBT field: " + fieldName, null);
            }
            return nbt;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw malformedPacket("Invalid NBT field: " + fieldName, ex);
        }
    }

    /**
     * 构造用于标记“格式错误网络包”的异常。
     */
    public static IllegalArgumentException malformedPacket(String message, Throwable cause) {
        if (cause == null) {
            return new IllegalArgumentException(message);
        }
        return new IllegalArgumentException(message, cause);
    }

    /**
     * 记录被丢弃的异常包信息（用于排查客户端或协议不兼容问题）。
     */
    public static void logDrop(String packetName, Throwable error) {
        if (error == null) {
            TodoConstants.LOGGER.warn("Dropping malformed packet: {}", packetName);
        } else {
            TodoConstants.LOGGER.warn("Dropping malformed packet: {} ({})", packetName, error.getMessage());
        }
    }
}


