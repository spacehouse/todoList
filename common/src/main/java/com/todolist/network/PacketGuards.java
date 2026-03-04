package com.todolist.network;

import com.todolist.TodoConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/**
 * 缃戠粶鏁版嵁鍖呭畨鍏ㄦ牎楠屽伐鍏风被銆? */
public final class PacketGuards {
    public static final int MAX_STRING_LENGTH = 32767;
    public static final int MAX_TASK_LIST_SIZE = 10_000;
    public static final int MAX_PROJECT_LIST_SIZE = 2_000;
    public static final int MAX_TAGS_PER_TASK = 512;
    public static final int MAX_SUBTASKS_PER_TASK = 1_024;

    private PacketGuards() {
    }

    /**
     * 浠庣紦鍐插尯璇诲彇鍙楅檺闀垮害鐨勫瓧绗︿覆銆?     * @param buf 缂撳啿鍖?     * @param fieldName 瀛楁鍚?     * @return 瀛楃涓?     */
    public static String readString(PacketByteBuf buf, String fieldName) {
        try {
            return buf.readString(MAX_STRING_LENGTH);
        } catch (RuntimeException ex) {
            throw malformedPacket("Invalid string field: " + fieldName, ex);
        }
    }

    /**
     * 浠庣紦鍐插尯璇诲彇鍙楅檺鑼冨洿鐨勬暣鏁拌鏁般€?     * @param buf 缂撳啿鍖?     * @param max 鏈€澶у€?     * @param fieldName 瀛楁鍚?     * @return 鏁存暟璁℃暟
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
     * 浠庣紦鍐插尯璇诲彇甯冨皵鍊笺€?     * @param buf 缂撳啿鍖?     * @param fieldName 瀛楁鍚?     * @return 甯冨皵鍊?     */
    public static boolean readBoolean(PacketByteBuf buf, String fieldName) {
        try {
            return buf.readBoolean();
        } catch (RuntimeException ex) {
            throw malformedPacket("Invalid boolean field: " + fieldName, ex);
        }
    }

    /**
     * 浠庣紦鍐插尯璇诲彇 NBT 澶嶅悎鏍囩銆?     * @param buf 缂撳啿鍖?     * @param fieldName 瀛楁鍚?     * @return NBT 澶嶅悎鏍囩
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
     * 鍒涘缓鏍煎紡閿欒寮傚父銆?     * @param message 閿欒娑堟伅
     * @param cause 鍘熷洜
     * @return IllegalArgumentException
     */
    public static IllegalArgumentException malformedPacket(String message, Throwable cause) {
        if (cause == null) {
            return new IllegalArgumentException(message);
        }
        return new IllegalArgumentException(message, cause);
    }

    /**
     * 璁板綍涓㈠純鐨勬暟鎹寘淇℃伅銆?     * @param packetName 鏁版嵁鍖呭悕
     * @param error 寮傚父
     */
    public static void logDrop(String packetName, Throwable error) {
        if (error == null) {
            TodoConstants.LOGGER.warn("Dropping malformed packet: {}", packetName);
        } else {
            TodoConstants.LOGGER.warn("Dropping malformed packet: {} ({})", packetName, error.getMessage());
        }
    }
}


