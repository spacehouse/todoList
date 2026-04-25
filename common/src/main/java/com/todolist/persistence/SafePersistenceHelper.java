package com.todolist.persistence;

import com.todolist.TodoConstants;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 安全持久化辅助类。
 * 负责统一处理临时文件写入、备份文件回滚和损坏现场保留等底层文件操作。
 */
public final class SafePersistenceHelper {
    private static final DateTimeFormatter CORRUPT_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    private static volatile boolean loggedParentDirectoryForceFailure;

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private SafePersistenceHelper() {
    }

    /**
     * 判断主文件或其最近一次备份是否存在。
     *
     * @param target 目标文件
     * @return 只要主文件或备份文件存在就返回 true
     */
    public static boolean existsOrBackup(Path target) {
        if (target == null) {
            return false;
        }
        return Files.exists(target) || Files.exists(resolveBackupPath(target));
    }

    /**
     * 返回目标文件对应的备份文件路径。
     *
     * @param target 目标文件
     * @return 备份文件路径
     */
    public static Path resolveBackupPath(Path target) {
        return resolveSiblingPath(target, ".bak");
    }

    /**
     * 以安全写盘方式写入字节数组。
     *
     * @param target 目标文件
     * @param bytes 待写入字节数组
     * @param label 日志标签
     * @throws IOException 当写盘失败时抛出
     */
    public static void writeBytes(Path target, byte[] bytes, String label) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(bytes, "bytes");
        ensureParentDirectory(target);
        Path temp = resolveTempPath(target);
        Path backup = resolveBackupPath(target);
        boolean movedTargetToBackup = false;
        deleteTempIfExists(temp, label);
        try {
            writeBytesToTemp(temp, bytes);
            if (Files.exists(target)) {
                Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
                forceParentDirectory(target, label);
                movedTargetToBackup = true;
            }
            promoteTempToTarget(temp, target, label);
        } catch (IOException exception) {
            deleteTempQuietly(temp, label);
            if (movedTargetToBackup && !Files.exists(target) && Files.exists(backup)) {
                try {
                    restoreBackupToTarget(target, backup, label);
                } catch (IOException restoreException) {
                    exception.addSuppressed(restoreException);
                }
            }
            throw exception;
        } finally {
            deleteTempQuietly(temp, label);
        }
    }

    /**
     * 以自动恢复方式读取目标文件。
     *
     * @param target 目标文件
     * @param label 日志标签
     * @param reader 读取函数
     * @param validator 结果校验器
     * @param <T> 读取结果类型
     * @return 读取结果；文件不存在且备份也不存在时返回 missing
     * @throws IOException 当主文件和备份都不可用时抛出
     */
    public static <T> ReadResult<T> readWithRecovery(Path target,
                                                     String label,
                                                     ThrowingPathReader<T> reader,
                                                     Predicate<T> validator) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reader, "reader");
        Predicate<T> safeValidator = validator == null ? value -> true : validator;
        Path backup = resolveBackupPath(target);
        if (!Files.exists(target)) {
            if (!Files.exists(backup)) {
                return ReadResult.missing();
            }
            return recoverFromBackup(target, backup, label, reader, safeValidator, false, null);
        }

        try {
            T value = readAndValidate(target, label, reader, safeValidator);
            return ReadResult.present(value, false);
        } catch (IOException primaryException) {
            if (!Files.exists(backup)) {
                throw primaryException;
            }
            return recoverFromBackup(target, backup, label, reader, safeValidator, true, primaryException);
        }
    }

    /**
     * 从备份文件恢复目标文件并返回读取结果。
     *
     * @param target 目标文件
     * @param backup 备份文件
     * @param label 日志标签
     * @param reader 读取函数
     * @param validator 结果校验器
     * @param preserveCorrupt 是否需要保留损坏现场
     * @param primaryException 主文件读取失败异常
     * @param <T> 读取结果类型
     * @return 使用备份读取出的结果
     * @throws IOException 当备份同样不可用时抛出
     */
    private static <T> ReadResult<T> recoverFromBackup(Path target,
                                                       Path backup,
                                                       String label,
                                                       ThrowingPathReader<T> reader,
                                                       Predicate<T> validator,
                                                       boolean preserveCorrupt,
                                                       IOException primaryException) throws IOException {
        T backupValue = readAndValidate(backup, label + " backup", reader, validator);
        try {
            if (preserveCorrupt && Files.exists(target)) {
                preserveCorruptTarget(target, label);
            }
            restoreBackupToTarget(target, backup, label);
            TodoConstants.LOGGER.warn("Recovered {} from backup file {}", safeLabel(label), backup);
        } catch (IOException recoveryException) {
            TodoConstants.LOGGER.warn("Recovered {} from backup {}, but failed to rewrite primary file {}",
                    safeLabel(label), backup, target, recoveryException);
        }
        if (primaryException != null) {
            TodoConstants.LOGGER.warn("Primary {} at {} was unreadable, backup recovery applied: {}",
                    safeLabel(label), target, primaryException.getMessage());
        }
        return ReadResult.present(backupValue, true);
    }

    /**
     * 读取并校验目标文件内容。
     *
     * @param target 目标文件
     * @param label 日志标签
     * @param reader 读取函数
     * @param validator 结果校验器
     * @param <T> 读取结果类型
     * @return 校验通过的读取结果
     * @throws IOException 当读取或校验失败时抛出
     */
    private static <T> T readAndValidate(Path target,
                                         String label,
                                         ThrowingPathReader<T> reader,
                                         Predicate<T> validator) throws IOException {
        try {
            T value = reader.read(target);
            if (!validator.test(value)) {
                throw new IOException("Invalid " + safeLabel(label) + " data in " + target);
            }
            return value;
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Failed to read " + safeLabel(label) + " from " + target, exception);
        }
    }

    /**
     * 将备份文件恢复为主文件。
     *
     * @param target 目标文件
     * @param backup 备份文件
     * @param label 日志标签
     * @throws IOException 当恢复失败时抛出
     */
    private static void restoreBackupToTarget(Path target, Path backup, String label) throws IOException {
        byte[] backupBytes = Files.readAllBytes(backup);
        Path temp = resolveTempPath(target);
        deleteTempIfExists(temp, label);
        try {
            writeBytesToTemp(temp, backupBytes);
            promoteTempToTarget(temp, target, label);
        } finally {
            deleteTempQuietly(temp, label);
        }
    }

    /**
     * 保留当前损坏主文件的现场副本。
     *
     * @param target 损坏的主文件
     * @param label 日志标签
     * @throws IOException 当保留现场失败时抛出
     */
    private static void preserveCorruptTarget(Path target, String label) throws IOException {
        Path corruptPath = resolveCorruptPath(target);
        try {
            Files.move(target, corruptPath, StandardCopyOption.REPLACE_EXISTING);
            forceParentDirectory(target, label);
        } catch (IOException moveException) {
            try {
                Files.copy(target, corruptPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyException) {
                moveException.addSuppressed(copyException);
                throw new IOException("Failed to preserve corrupt " + safeLabel(label) + " file: " + target, moveException);
            }
        }
    }

    /**
     * 将临时文件提升为正式文件。
     *
     * @param temp 临时文件
     * @param target 目标文件
     * @param label 日志标签
     * @throws IOException 当替换失败时抛出
     */
    private static void promoteTempToTarget(Path temp, Path target, String label) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            TodoConstants.LOGGER.warn("Atomic move is not supported for {}, fallback to replace existing move", safeLabel(label));
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        forceParentDirectory(target, label);
    }

    /**
     * 把字节数组写入临时文件并强制刷盘。
     *
     * @param temp 临时文件
     * @param bytes 待写入字节数组
     * @throws IOException 当写入失败时抛出
     */
    private static void writeBytesToTemp(Path temp, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                temp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    /**
     * 确保目标文件父目录存在。
     *
     * @param target 目标文件
     * @return 目标文件父目录
     * @throws IOException 当目录创建失败时抛出
     */
    private static Path ensureParentDirectory(Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("Target file has no parent directory: " + target);
        }
        Files.createDirectories(parent);
        return parent;
    }

    /**
     * 尝试强制刷新目标文件父目录，缩小目录项在断电时丢失的窗口。
     * 某些平台可能不支持目录 force，此时仅记录一次警告并继续走 best-effort。
     *
     * @param target 目标文件
     * @param label 日志标签
     */
    private static void forceParentDirectory(Path target, String label) {
        Path parent = target == null ? null : target.toAbsolutePath().getParent();
        if (parent == null || !Files.exists(parent)) {
            return;
        }
        try (FileChannel directoryChannel = FileChannel.open(parent, StandardOpenOption.READ)) {
            directoryChannel.force(true);
        } catch (IOException | UnsupportedOperationException exception) {
            if (!loggedParentDirectoryForceFailure) {
                loggedParentDirectoryForceFailure = true;
                TodoConstants.LOGGER.warn(
                        "Failed to force parent directory for {}, continuing with best-effort durability: {} ({})",
                        safeLabel(label),
                        parent,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    /**
     * 删除残留的临时文件。
     *
     * @param temp 临时文件
     * @param label 日志标签
     * @throws IOException 当删除失败时抛出
     */
    private static void deleteTempIfExists(Path temp, String label) throws IOException {
        if (Files.exists(temp)) {
            TodoConstants.LOGGER.warn("Deleting stale temp file for {}: {}", safeLabel(label), temp);
            Files.delete(temp);
        }
    }

    /**
     * 尝试静默删除临时文件。
     *
     * @param temp 临时文件
     * @param label 日志标签
     */
    private static void deleteTempQuietly(Path temp, String label) {
        if (temp == null || !Files.exists(temp)) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException exception) {
            TodoConstants.LOGGER.warn("Failed to delete temp file for {}: {}", safeLabel(label), temp, exception);
        }
    }

    /**
     * 生成临时文件路径。
     *
     * @param target 目标文件
     * @return 临时文件路径
     */
    private static Path resolveTempPath(Path target) {
        return resolveSiblingPath(target, ".tmp");
    }

    /**
     * 生成损坏现场文件路径。
     *
     * @param target 目标文件
     * @return 损坏现场文件路径
     */
    private static Path resolveCorruptPath(Path target) {
        String suffix = ".corrupt." + LocalDateTime.now().format(CORRUPT_SUFFIX_FORMATTER);
        return resolveSiblingPath(target, suffix);
    }

    /**
     * 生成同级伴生文件路径。
     *
     * @param target 目标文件
     * @param suffix 文件后缀
     * @return 同级伴生文件路径
     */
    private static Path resolveSiblingPath(Path target, String suffix) {
        Path absoluteTarget = target.toAbsolutePath();
        Path fileName = absoluteTarget.getFileName();
        if (fileName == null) {
            return absoluteTarget;
        }
        return absoluteTarget.resolveSibling(fileName + suffix);
    }

    /**
     * 规范化日志标签。
     *
     * @param label 输入标签
     * @return 非空日志标签
     */
    private static String safeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "data file";
        }
        return label;
    }

    /**
     * 带受检异常的路径读取函数。
     *
     * @param <T> 读取结果类型
     */
    @FunctionalInterface
    public interface ThrowingPathReader<T> {
        /**
         * 从指定路径读取对象。
         *
         * @param path 目标路径
         * @return 读取结果
         * @throws Exception 当读取失败时抛出
         */
        T read(Path path) throws Exception;
    }

    /**
     * 安全读取结果对象。
     * 用于区分文件缺失和恢复成功两种状态。
     *
     * @param <T> 读取结果类型
     */
    public static final class ReadResult<T> {
        private final boolean found;
        private final boolean recoveredFromBackup;
        private final T value;

        /**
         * 创建读取结果对象。
         *
         * @param found 是否找到可用数据
         * @param recoveredFromBackup 是否从备份恢复
         * @param value 读取结果
         */
        private ReadResult(boolean found, boolean recoveredFromBackup, T value) {
            this.found = found;
            this.recoveredFromBackup = recoveredFromBackup;
            this.value = value;
        }

        /**
         * 创建缺失结果。
         *
         * @param <T> 读取结果类型
         * @return 缺失结果
         */
        public static <T> ReadResult<T> missing() {
            return new ReadResult<>(false, false, null);
        }

        /**
         * 创建命中结果。
         *
         * @param value 读取结果
         * @param recoveredFromBackup 是否从备份恢复
         * @param <T> 读取结果类型
         * @return 命中结果
         */
        public static <T> ReadResult<T> present(T value, boolean recoveredFromBackup) {
            return new ReadResult<>(true, recoveredFromBackup, value);
        }

        /**
         * 返回是否找到可用数据。
         *
         * @return true 表示找到可用数据
         */
        public boolean isFound() {
            return found;
        }

        /**
         * 返回是否从备份恢复。
         *
         * @return true 表示读取过程中使用了备份
         */
        public boolean isRecoveredFromBackup() {
            return recoveredFromBackup;
        }

        /**
         * 返回读取结果值。
         *
         * @return 读取结果值
         */
        public T getValue() {
            return value;
        }
    }
}
