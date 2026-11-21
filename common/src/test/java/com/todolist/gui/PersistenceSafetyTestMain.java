package com.todolist.gui;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.persistence.SafePersistenceHelper;
import com.todolist.platform.DataPathProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 持久化安全测试入口。
 * 负责覆盖配置文件安全写盘、备份恢复和损坏兜底等核心回归场景。
 * 原 NBT 任务/项目/玩家状态文件的备份恢复用例已随 NBT 文件存储死分支清理退役，
 * 数据安全兜底语义由 H2 可用性管理（H2StorageAvailabilityTestMain）与 H2 备份/恢复链路承接。
 */
public final class PersistenceSafetyTestMain {

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private PersistenceSafetyTestMain() {
    }

    /**
     * 程序入口，串行执行持久化安全回归用例。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldRecoverConfigFromBackupWhenPrimaryCorrupted", PersistenceSafetyTestMain::shouldRecoverConfigFromBackupWhenPrimaryCorrupted);
        GuiTestSupport.runTestCase("PersistenceSafetyTestMain.shouldFallbackToDefaultsWithoutOverwritingInvalidConfig", PersistenceSafetyTestMain::shouldFallbackToDefaultsWithoutOverwritingInvalidConfig);
    }

    /**
     * 验证配置主文件损坏时会从备份自动恢复且保留现场文件。
     */
    private static void shouldRecoverConfigFromBackupWhenPrimaryCorrupted() {
        GuiTestSupport.resetState();
        try {
            ModConfig config = ModConfig.getInstance();
            config.setHudWidth(240);
            config.setHudWidth(333);
            Path configFile = getConfigFilePath();
            Path backupFile = SafePersistenceHelper.resolveBackupPath(configFile);

            GuiTestSupport.assertTrue(Files.exists(backupFile), "配置文件第二次保存后应生成 .bak 备份");
            corruptFile(configFile, "{ invalid config primary");
            ModConfig.load();

            GuiTestSupport.assertEquals(240, ModConfig.getInstance().getHudWidth(), "配置主文件损坏后应恢复为最近一次备份版本");
            GuiTestSupport.assertTrue(hasCorruptSibling(configFile), "配置主文件损坏后应保留 .corrupt 现场");
            GuiTestSupport.assertTrue(Files.readString(configFile, StandardCharsets.UTF_8).contains("\"commandAccessMode\""), "恢复后的配置文件应仍保留注释相关配置内容");
        } catch (Exception exception) {
            throw new IllegalStateException("验证配置备份恢复时发生异常", exception);
        }
    }

    /**
     * 验证配置主文件和备份都损坏时，会回退默认值但不覆盖现场坏文件。
     */
    private static void shouldFallbackToDefaultsWithoutOverwritingInvalidConfig() {
        GuiTestSupport.resetState();
        try {
            ModConfig config = ModConfig.getInstance();
            config.setHudWidth(333);
            Path configFile = getConfigFilePath();
            Path backupFile = SafePersistenceHelper.resolveBackupPath(configFile);

            corruptFile(configFile, "{ invalid config primary");
            corruptFile(backupFile, "{ invalid config backup");
            String invalidPrimary = Files.readString(configFile, StandardCharsets.UTF_8);

            ModConfig.load();

            GuiTestSupport.assertEquals(200, ModConfig.getInstance().getHudWidth(), "配置主文件和备份都损坏时应回退默认配置");
            GuiTestSupport.assertEquals(invalidPrimary, Files.readString(configFile, StandardCharsets.UTF_8), "配置双损坏时不应立刻覆盖现场坏文件");
        } catch (Exception exception) {
            throw new IllegalStateException("验证配置双损坏兜底时发生异常", exception);
        }
    }

    /**
     * 将目标文件改写为损坏内容。
     *
     * @param file 目标文件
     * @param content 损坏内容
     * @throws Exception 当写入失败时抛出
     */
    private static void corruptFile(Path file, String content) throws Exception {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /**
     * 判断目标文件是否生成了损坏现场副本。
     *
     * @param file 目标文件
     * @return 找到 .corrupt 副本时返回 true
     * @throws Exception 当目录遍历失败时抛出
     */
    private static boolean hasCorruptSibling(Path file) throws Exception {
        Path absoluteFile = file.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        if (parent == null || !Files.exists(parent)) {
            return false;
        }
        String prefix = absoluteFile.getFileName() + ".corrupt.";
        try (Stream<Path> stream = Files.list(parent)) {
            return stream.anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }

    /**
     * 返回当前测试环境的配置文件路径。
     *
     * @return 配置文件路径
     */
    private static Path getConfigFilePath() {
        return DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
    }
}
