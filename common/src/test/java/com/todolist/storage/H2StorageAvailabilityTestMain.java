package com.todolist.storage;

import com.todolist.config.ModConfig;
import com.todolist.bootstrap.EventBootstrap;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.platform.DataPathProvider;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * H2StorageAvailabilityTestMain 覆盖 M1-E 的 H2 不可用状态与 NBT 回退隔离。
 */
public final class H2StorageAvailabilityTestMain {
    private H2StorageAvailabilityTestMain() {
    }

    /**
     * 执行 H2 存储可用状态自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase("H2StorageAvailabilityTestMain.shouldBlockH2WritesWhenUnavailable", H2StorageAvailabilityTestMain::shouldBlockH2WritesWhenUnavailable);
        GuiTestSupport.runTestCase("H2StorageAvailabilityTestMain.shouldKeepNbtWritableWhenH2Unavailable", H2StorageAvailabilityTestMain::shouldKeepNbtWritableWhenH2Unavailable);
        GuiTestSupport.runTestCase("H2StorageAvailabilityTestMain.shouldFindStorageUnavailableInCauseChain", H2StorageAvailabilityTestMain::shouldFindStorageUnavailableInCauseChain);
        GuiTestSupport.runTestCase("H2StorageAvailabilityTestMain.shouldIsolateAvailabilityByNamespace", H2StorageAvailabilityTestMain::shouldIsolateAvailabilityByNamespace);
        GuiTestSupport.runTestCase("H2StorageAvailabilityTestMain.shouldResetDatabaseStateForNamespace", H2StorageAvailabilityTestMain::shouldResetDatabaseStateForNamespace);
        GuiTestSupport.runTestCase("H2StorageAvailabilityTestMain.shouldMapUnavailableExceptionToUserMessages", H2StorageAvailabilityTestMain::shouldMapUnavailableExceptionToUserMessages);
        GuiTestSupport.runTestCase("H2StorageAvailabilityTestMain.shouldResetCurrentH2StateOnServerStopped", H2StorageAvailabilityTestMain::shouldResetCurrentH2StateOnServerStopped);
    }

    /**
     * 验证 H2 被标记不可用后会阻断后续 H2 写入。
     */
    private static void shouldBlockH2WritesWhenUnavailable() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-unavailable-");
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2StorageAvailability.markUnavailable(provider.getDatabaseBasePath(), H2StorageAvailability.Reason.WRITE_FAILED, "forced unavailable");

            boolean failedWithUnavailable = false;
            try {
                new TaskStorage().saveTasks(List.of(new Task("blocked", "")));
            } catch (StorageUnavailableException exception) {
                failedWithUnavailable = exception.getReason() == H2StorageAvailability.Reason.WRITE_FAILED;
            }
            GuiTestSupport.assertTrue(failedWithUnavailable, "H2 不可用时应抛出 StorageUnavailableException");
            GuiTestSupport.assertTrue(H2StorageAvailability.isUnavailable(provider.getDatabaseBasePath()), "H2 不可用状态应被保留");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 不可用阻断写入时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            H2StorageAvailability.resetForTests();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证 H2 不可用状态不会影响 NBT 后端写入。
     */
    private static void shouldKeepNbtWritableWhenH2Unavailable() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-nbt-while-h2-unavailable-");
            H2ConnectionProvider provider = new H2ConnectionProvider();
            H2StorageAvailability.markUnavailable(provider.getDatabaseBasePath(), H2StorageAvailability.Reason.WRITE_FAILED, "forced unavailable");
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);

            TaskStorage storage = new TaskStorage();
            storage.saveTasks(List.of(new Task("nbt-ok", "")));

            GuiTestSupport.assertEquals("nbt-ok", storage.loadTasks().get(0).getTitle(), "NBT 后端应不受 H2 不可用状态影响");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 NBT 不受 H2 不可用影响时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            H2StorageAvailability.resetForTests();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证异常链中可定位存储不可用异常。
     */
    private static void shouldFindStorageUnavailableInCauseChain() {
        StorageUnavailableException unavailable = new StorageUnavailableException(H2StorageAvailability.Reason.READ_FAILED, "read failed");
        RuntimeException wrapper = new RuntimeException("wrapper", unavailable);
        GuiTestSupport.assertEquals(unavailable, StorageUnavailableException.find(wrapper), "应能从异常链中找到 StorageUnavailableException");
        GuiTestSupport.assertEquals(null, StorageUnavailableException.find(new RuntimeException("other")), "普通异常链不应误判为存储不可用");
    }

    /**
     * 验证不同 namespace 的 H2 可用状态互相隔离。
     */
    private static void shouldIsolateAvailabilityByNamespace() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-namespace-isolation-");
            DataPathProvider.setStorageNamespace("alpha");
            Path alphaDatabase = new H2ConnectionProvider().getDatabaseBasePath();
            H2StorageAvailability.markUnavailable(alphaDatabase, H2StorageAvailability.Reason.READ_FAILED, "alpha failed");

            DataPathProvider.setStorageNamespace("beta");
            Path betaDatabase = new H2ConnectionProvider().getDatabaseBasePath();

            GuiTestSupport.assertTrue(H2StorageAvailability.isUnavailable(alphaDatabase), "alpha namespace 应保持不可用状态");
            GuiTestSupport.assertFalse(H2StorageAvailability.isUnavailable(betaDatabase), "beta namespace 不应继承 alpha 的不可用状态");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 namespace 可用状态隔离时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            H2StorageBootstrap.resetAllForTests();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证指定 namespace 的 H2 状态可重建。
     */
    private static void shouldResetDatabaseStateForNamespace() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-namespace-reset-");
            DataPathProvider.setStorageNamespace("reset-target");
            Path database = new H2ConnectionProvider().getDatabaseBasePath();
            H2StorageAvailability.markUnavailable(database, H2StorageAvailability.Reason.WRITE_FAILED, "reset me");

            H2StorageBootstrap.resetDatabaseState(database);

            GuiTestSupport.assertFalse(H2StorageAvailability.isUnavailable(database), "resetDatabaseState 应清理指定库的不可用状态");
        } catch (Exception exception) {
            throw new IllegalStateException("验证 H2 namespace 状态重建时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            H2StorageBootstrap.resetAllForTests();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 验证存储不可用异常会映射为专用用户提示。
     */
    private static void shouldMapUnavailableExceptionToUserMessages() {
        StorageUnavailableException unavailable = new StorageUnavailableException(H2StorageAvailability.Reason.QUERY_FAILED, "query failed");
        GuiTestSupport.assertEquals(
                StorageFailureNotifier.COMMAND_STORAGE_UNAVAILABLE_MESSAGE_KEY,
                StorageFailureNotifier.toCommandMessageKey(unavailable, "command.todolist.task.add.failed"),
                "命令失败应映射为存储不可用提示 key"
        );
        GuiTestSupport.assertEquals(
                "command.todolist.task.add.failed",
                StorageFailureNotifier.toCommandMessageKey(new RuntimeException("other"), "command.todolist.task.add.failed"),
                "普通失败应保留 fallback 提示 key"
        );
    }

    /**
     * 验证服务端停止生命周期会清理当前 H2 存储状态。
     */
    private static void shouldResetCurrentH2StateOnServerStopped() {
        Path tempGameDir = null;
        try {
            tempGameDir = prepareTempGameDir("todolist-h2-server-stop-");
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.H2);
            Path database = new H2ConnectionProvider().getDatabaseBasePath();
            H2StorageAvailability.markUnavailable(database, H2StorageAvailability.Reason.WRITE_FAILED, "server stop reset");

            EventBootstrap.handleServerStopped(null, ignored -> {
            });

            GuiTestSupport.assertFalse(H2StorageAvailability.isUnavailable(database), "服务端停止后应清理当前 H2 不可用状态");
        } catch (Exception exception) {
            throw new IllegalStateException("验证服务端停止清理 H2 状态时发生异常", exception);
        } finally {
            selectNbtBackendQuietly();
            H2StorageBootstrap.resetAllForTests();
            deleteRecursively(tempGameDir);
        }
    }

    /**
     * 准备测试临时 game dir。
     *
     * @param prefix 临时目录前缀
     * @return 临时 game dir
     * @throws Exception 准备失败时抛出
     */
    private static Path prepareTempGameDir(String prefix) throws Exception {
        H2StorageAvailability.resetForTests();
        Path tempGameDir = Files.createTempDirectory(prefix);
        DataPathProvider.setGameDirSupplier(() -> tempGameDir);
        DataPathProvider.resetStorageNamespace();
        selectNbtBackendQuietly();
        return tempGameDir;
    }

    /**
     * 安静切换回 NBT 后端。
     */
    private static void selectNbtBackendQuietly() {
        try {
            ModConfig.getInstance().setStorageBackend(ModConfig.StorageBackend.NBT);
        } catch (Exception ignored) {
            // 测试清理阶段只做最佳努力恢复。
        }
    }

    /**
     * 递归删除测试临时目录。
     *
     * @param root 临时目录
     */
    private static void deleteRecursively(Path root) {
        try {
            if (root == null || !Files.exists(root)) {
                return;
            }
            try (var paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException("无法删除 H2 可用状态测试临时文件: " + path, exception);
                    }
                });
            }
        } catch (Exception exception) {
            throw new IllegalStateException("无法清理 H2 可用状态测试临时目录", exception);
        }
    }
}
