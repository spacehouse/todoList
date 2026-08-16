package com.todolist.compat;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * NBT 读写兼容工具，屏蔽 1.20.1 与 1.20.3+ 之间 NbtIo.read 签名差异。
 */
public final class NbtIoCompat {
    private static final Method READ_PATH_METHOD = resolveReadMethod(Path.class);
    private static final Method READ_FILE_METHOD = resolveReadMethod(File.class);
    private static final Method WRITE_PATH_METHOD = resolveWriteMethod(Path.class);
    private static final Method WRITE_FILE_METHOD = resolveWriteMethod(File.class);

    /**
     * 禁止实例化工具类。
     */
    private NbtIoCompat() {
    }

    /**
     * 按当前运行版本可用的签名读取 NBT 根标签。
     *
     * @param path NBT 文件路径
     * @return 读取到的根标签
     * @throws IOException 读取失败时抛出
     */
    public static CompoundTag read(Path path) throws IOException {
        if (READ_PATH_METHOD != null) {
            return invokeRead(READ_PATH_METHOD, path);
        }
        if (READ_FILE_METHOD != null) {
            return invokeRead(READ_FILE_METHOD, path.toFile());
        }
        throw new IOException("No compatible NbtIo.read overload was found");
    }

    /**
     * 按当前运行版本可用的签名写入 NBT 根标签。
     *
     * @param root NBT 根标签
     * @param path NBT 文件路径
     * @throws IOException 写入失败时抛出
     */
    public static void write(CompoundTag root, Path path) throws IOException {
        if (WRITE_PATH_METHOD != null) {
            invokeWrite(WRITE_PATH_METHOD, root, path);
            return;
        }
        if (WRITE_FILE_METHOD != null) {
            invokeWrite(WRITE_FILE_METHOD, root, path.toFile());
            return;
        }
        throw new IOException("No compatible NbtIo.write overload was found");
    }

    /**
     * 按参数类型查找当前版本可用的 NbtIo.read 重载。
     *
     * @param parameterType 参数类型
     * @return 匹配的方法；不存在时返回 null
     */
    private static Method resolveReadMethod(Class<?> parameterType) {
        try {
            return NbtIo.class.getMethod("read", parameterType);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    /**
     * 按参数类型查找当前版本可用的 NbtIo.write 重载。
     *
     * @param parameterType 参数类型
     * @return 匹配的方法；不存在时返回 null
     */
    private static Method resolveWriteMethod(Class<?> parameterType) {
        try {
            return NbtIo.class.getMethod("write", CompoundTag.class, parameterType);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    /**
     * 调用底层 NbtIo.read 并保留 IOException 语义。
     *
     * @param method 当前版本可用的方法
     * @param argument 方法参数
     * @return 读取到的根标签
     * @throws IOException 读取失败时抛出
     */
    private static CompoundTag invokeRead(Method method, Object argument) throws IOException {
        try {
            return (CompoundTag) method.invoke(null, argument);
        } catch (IllegalAccessException exception) {
            throw new IOException("Unable to access NbtIo.read", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Failed to invoke NbtIo.read", cause);
        }
    }

    /**
     * 调用底层 NbtIo.write 并保留 IOException 语义。
     *
     * @param method 当前版本可用的方法
     * @param root NBT 根标签
     * @param argument 方法参数
     * @throws IOException 写入失败时抛出
     */
    private static void invokeWrite(Method method, CompoundTag root, Object argument) throws IOException {
        try {
            method.invoke(null, root, argument);
        } catch (IllegalAccessException exception) {
            throw new IOException("Unable to access NbtIo.write", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Failed to invoke NbtIo.write", cause);
        }
    }
}
