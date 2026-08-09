package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * StorageBackendSelectionTestMain 覆盖 M1-A 的存储后端配置读取和规范化行为。
 */
public final class StorageBackendSelectionTestMain {
    private StorageBackendSelectionTestMain() {
    }

    /**
     * 执行后端选择配置的离线回归用例。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldDefaultToH2", StorageBackendSelectionTestMain::shouldDefaultToH2);
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldLoadH2Backend", StorageBackendSelectionTestMain::shouldLoadH2Backend);
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldNormalizeInvalidBackendToH2", StorageBackendSelectionTestMain::shouldNormalizeInvalidBackendToH2);
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldWriteStorageBackendCommentsToConfig", StorageBackendSelectionTestMain::shouldWriteStorageBackendCommentsToConfig);
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldWriteH2BackupOnStartCommentsToConfig", StorageBackendSelectionTestMain::shouldWriteH2BackupOnStartCommentsToConfig);
    }

    /**
     * 验证缺失配置时默认使用 H2 后端。
     */
    private static void shouldDefaultToH2() {
        GuiTestSupport.resetStateKeepDefaultBackend();
        GuiTestSupport.assertEquals(ModConfig.StorageBackend.H2, ModConfig.getInstance().getStorageBackend(), "缺失 storageBackend 时应默认 H2");
        GuiTestSupport.assertTrue(StorageBackendFactory.isH2Selected(), "默认配置应选择 H2 后端");
    }

    /**
     * 验证配置为 H2 时可以被读取为 H2 后端。
     */
    private static void shouldLoadH2Backend() {
        GuiTestSupport.resetState();
        ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
        ModConfig.load();
        GuiTestSupport.assertEquals(ModConfig.StorageBackend.H2, ModConfig.getInstance().getStorageBackend(), "storageBackend=H2 应被保留");
        GuiTestSupport.assertTrue(StorageBackendFactory.isH2Selected(), "配置为 H2 时工厂应返回 H2");
    }

    /**
     * 验证非法 storageBackend 会规范化回 H2 并写回配置。
     */
    private static void shouldNormalizeInvalidBackendToH2() {
        GuiTestSupport.resetState();
        try {
            Path configFile = DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, "{\"storageBackend\":\"BROKEN\"}", StandardCharsets.UTF_8);
            ModConfig.load();

            GuiTestSupport.assertEquals(ModConfig.StorageBackend.H2, ModConfig.getInstance().getStorageBackend(), "非法 storageBackend 应回退 H2");
            String savedConfig = Files.readString(configFile, StandardCharsets.UTF_8);
            GuiTestSupport.assertTrue(savedConfig.contains("\"storageBackend\": \"h2\""), "非法 storageBackend 规范化后应写回 h2");
        } catch (Exception exception) {
            throw new IllegalStateException("验证非法 storageBackend 规范化时发生异常", exception);
        }
    }

    /**
     * 验证配置文件会写入 storageBackend 多语言说明注释。
     */
    private static void shouldWriteStorageBackendCommentsToConfig() {
        GuiTestSupport.resetState();
        try {
            Path configFile = DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);

            String savedConfig = Files.readString(configFile, StandardCharsets.UTF_8);
            assertContainsEither(savedConfig, "// storageBackend notes:", "// storageBackend 说明：", "配置文件应写入 storageBackend 标题注释");
            assertContainsEither(
                    savedConfig,
                    "// h2: H2 database storage (forced, only supported mode)",
                    "// h2：H2 数据库存储（强制使用，唯一支持的存储模式）",
                    "配置文件应写入 storageBackend 的 h2 说明注释"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("验证 storageBackend 注释写入时发生异常", exception);
        }
    }

    /**
     * 验证配置文件会写入 h2BackupOnStart 多语言说明注释。
     */
    private static void shouldWriteH2BackupOnStartCommentsToConfig() {
        GuiTestSupport.resetState();
        try {
            Path configFile = DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
            ModConfig.getInstance().setH2BackupOnStart(true);

            String savedConfig = Files.readString(configFile, StandardCharsets.UTF_8);
            assertContainsEither(savedConfig, "// h2BackupOnStart notes:", "// h2BackupOnStart 说明：", "配置文件应写入 h2BackupOnStart 标题注释");
            assertContainsEither(
                    savedConfig,
                    "// true: create one H2 backup after H2 initializes on startup (only effective when storageBackend is h2)",
                    "// true：启动初始化 H2 后自动创建一次备份（仅在 storageBackend 为 h2 时生效）",
                    "配置文件应写入 h2BackupOnStart 的启用说明注释"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("验证 h2BackupOnStart 注释写入时发生异常", exception);
        }
    }

    /**
     * 断言文本至少包含两种候选值中的一个，适配不同系统语言的配置注释输出。
     *
     * @param text 待检查文本
     * @param first 第一种候选值
     * @param second 第二种候选值
     * @param message 失败提示
     */
    private static void assertContainsEither(String text, String first, String second, String message) {
        GuiTestSupport.assertTrue(
                text.contains(first) || text.contains(second),
                message + " first=" + first + " second=" + second
        );
    }
}
