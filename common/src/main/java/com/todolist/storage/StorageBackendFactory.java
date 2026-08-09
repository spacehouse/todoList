package com.todolist.storage;

import com.todolist.config.ModConfig;

/**
 * StorageBackendFactory 负责集中解析当前配置选择的存储后端。
 */
public final class StorageBackendFactory {
    private StorageBackendFactory() {
    }

    /**
     * 返回当前配置中的存储后端。
     * 生产环境中 ModConfig.normalize() 强制为 H2，但测试可通过 setStorageBackend 临时切换。
     *
     * @return 当前配置的存储后端
     */
    public static ModConfig.StorageBackend getConfiguredBackend() {
        return ModConfig.getInstance().getStorageBackend();
    }

    /**
     * 判断当前是否选择 NBT 存储后端。
     *
     * @return 当前后端为 NBT 时返回 true
     */
    public static boolean isNbtSelected() {
        return getConfiguredBackend() == ModConfig.StorageBackend.NBT;
    }

    /**
     * 判断当前是否选择 H2 存储后端。
     *
     * @return 当前后端为 H2 时返回 true
     */
    public static boolean isH2Selected() {
        return getConfiguredBackend() == ModConfig.StorageBackend.H2;
    }
}
