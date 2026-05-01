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
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldDefaultToNbt", StorageBackendSelectionTestMain::shouldDefaultToNbt);
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldLoadH2Backend", StorageBackendSelectionTestMain::shouldLoadH2Backend);
        GuiTestSupport.runTestCase("StorageBackendSelectionTestMain.shouldNormalizeInvalidBackendToNbt", StorageBackendSelectionTestMain::shouldNormalizeInvalidBackendToNbt);
    }

    /**
     * 验证缺失配置时默认使用 NBT 后端。
     */
    private static void shouldDefaultToNbt() {
        GuiTestSupport.resetState();
        GuiTestSupport.assertEquals(ModConfig.StorageBackend.NBT, ModConfig.getInstance().getStorageBackend(), "缺失 storageBackend 时应默认 NBT");
        GuiTestSupport.assertTrue(StorageBackendFactory.isNbtSelected(), "默认配置应选择 NBT 后端");
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
     * 验证非法 storageBackend 会规范化回 NBT 并写回配置。
     */
    private static void shouldNormalizeInvalidBackendToNbt() {
        GuiTestSupport.resetState();
        try {
            Path configFile = DataPathProvider.getGameDir().resolve("config").resolve("todolist.json");
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, "{\"storageBackend\":\"BROKEN\"}", StandardCharsets.UTF_8);
            ModConfig.load();

            GuiTestSupport.assertEquals(ModConfig.StorageBackend.NBT, ModConfig.getInstance().getStorageBackend(), "非法 storageBackend 应回退 NBT");
            String savedConfig = Files.readString(configFile, StandardCharsets.UTF_8);
            GuiTestSupport.assertTrue(savedConfig.contains("\"storageBackend\": \"nbt\""), "非法 storageBackend 规范化后应写回 nbt");
        } catch (Exception exception) {
            throw new IllegalStateException("验证非法 storageBackend 规范化时发生异常", exception);
        }
    }
}
