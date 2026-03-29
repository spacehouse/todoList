package com.todolist.client;

import com.todolist.TodoListCommon;
import com.todolist.gui.testsupport.FakeMinecraftClient;
import com.todolist.gui.testsupport.GuiTestSupport;
import com.todolist.task.Task;
import com.todolist.task.TaskStorage;

import java.util.List;
import java.util.UUID;

/**
 * ClientTaskStorageHelper 离线自测入口，覆盖单人发布局域网时个人任务迁移到玩家文件的回归场景。
 */
public final class ClientTaskStorageHelperTestMain {
    private static final UUID PLAYER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    /**
     * 私有构造方法，避免工具类被实例化。
     */
    private ClientTaskStorageHelperTestMain() {
    }

    /**
     * 程序入口，串行执行 ClientTaskStorageHelper 的离线自测。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        GuiTestSupport.runTestCase(
                "ClientTaskStorageHelperTestMain.shouldMigrateLocalTasksToPlayerStorageWhenPlayerFileMissing",
                ClientTaskStorageHelperTestMain::shouldMigrateLocalTasksToPlayerStorageWhenPlayerFileMissing
        );
        GuiTestSupport.runTestCase(
                "ClientTaskStorageHelperTestMain.shouldSkipMigrationWhenPlayerFileIsNewer",
                ClientTaskStorageHelperTestMain::shouldSkipMigrationWhenPlayerFileIsNewer
        );
        GuiTestSupport.runTestCase(
                "ClientTaskStorageHelperTestMain.shouldReplacePlayerFileWhenLocalTasksAreNewer",
                ClientTaskStorageHelperTestMain::shouldReplacePlayerFileWhenLocalTasksAreNewer
        );
        GuiTestSupport.runTestCase(
                "ClientTaskStorageHelperTestMain.shouldRestorePlayerTasksToLocalStorageWhenPlayerFileIsNewer",
                ClientTaskStorageHelperTestMain::shouldRestorePlayerTasksToLocalStorageWhenPlayerFileIsNewer
        );
        GuiTestSupport.runTestCase(
                "ClientTaskStorageHelperTestMain.shouldSkipRestoringLocalTasksWhenLocalFileIsNewer",
                ClientTaskStorageHelperTestMain::shouldSkipRestoringLocalTasksWhenLocalFileIsNewer
        );
        GuiTestSupport.runTestCase(
                "ClientTaskStorageHelperTestMain.shouldRestorePlayerTasksWhenLoadingLocalSingleplayerTasks",
                ClientTaskStorageHelperTestMain::shouldRestorePlayerTasksWhenLoadingLocalSingleplayerTasks
        );
    }

    /**
     * 验证玩家文件缺失时，会把单人本地个人任务迁移到对应玩家文件。
     */
    private static void shouldMigrateLocalTasksToPlayerStorageWhenPlayerFileMissing() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        saveLocalTasks(storage, "Local Alpha", "Local Beta");

        boolean migrated = migrate(storage);
        List<String> playerTitles = loadPlayerTitles(storage);

        GuiTestSupport.assertTrue(migrated, "玩家文件不存在时应执行本地个人任务迁移");
        GuiTestSupport.assertEquals(List.of("Local Alpha", "Local Beta"), playerTitles, "迁移后玩家文件应保留原本地个人任务");
    }

    /**
     * 验证当玩家文件更新于本地文件时，不会被旧的本地个人任务回写覆盖。
     */
    private static void shouldSkipMigrationWhenPlayerFileIsNewer() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        saveLocalTasks(storage, "Old Local");
        sleepForTimestampTick();
        savePlayerTasks(storage, "New Player");

        boolean migrated = migrate(storage);
        List<String> playerTitles = loadPlayerTitles(storage);

        GuiTestSupport.assertFalse(migrated, "玩家文件更新时不应被旧本地任务覆盖");
        GuiTestSupport.assertEquals(List.of("New Player"), playerTitles, "玩家文件较新时应保留现有玩家任务");
    }

    /**
     * 验证当本地文件更新于玩家文件时，会重新覆盖玩家文件，恢复较新的单人个人任务。
     */
    private static void shouldReplacePlayerFileWhenLocalTasksAreNewer() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        savePlayerTasks(storage, "Old Player");
        sleepForTimestampTick();
        saveLocalTasks(storage, "New Local");

        boolean migrated = migrate(storage);
        List<String> playerTitles = loadPlayerTitles(storage);

        GuiTestSupport.assertTrue(migrated, "本地文件更新时应覆盖玩家文件以恢复最新个人任务");
        GuiTestSupport.assertEquals(List.of("New Local"), playerTitles, "本地文件较新时玩家文件应更新为本地任务");
    }

    /**
     * 验证玩家文件比本地文件新时，会在单人重进后回灌到本地个人任务文件。
     */
    private static void shouldRestorePlayerTasksToLocalStorageWhenPlayerFileIsNewer() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        saveLocalTasks(storage, "Old Local");
        sleepForTimestampTick();
        savePlayerTasks(storage, "New Player");

        boolean restored = restore(storage);
        List<String> localTitles = loadLocalTitles(storage);

        GuiTestSupport.assertTrue(restored, "玩家文件更新时应回灌到本地个人任务文件");
        GuiTestSupport.assertEquals(List.of("New Player"), localTitles, "回灌后本地个人任务应与玩家文件保持一致");
    }

    /**
     * 验证本地文件更新时，不会被较旧的玩家文件覆盖。
     */
    private static void shouldSkipRestoringLocalTasksWhenLocalFileIsNewer() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        savePlayerTasks(storage, "Old Player");
        sleepForTimestampTick();
        saveLocalTasks(storage, "New Local");

        boolean restored = restore(storage);
        List<String> localTitles = loadLocalTitles(storage);

        GuiTestSupport.assertFalse(restored, "本地文件较新时不应被旧玩家文件覆盖");
        GuiTestSupport.assertEquals(List.of("New Local"), localTitles, "本地文件较新时应保留当前本地个人任务");
    }

    /**
     * 调用待测迁移逻辑，减少重复样板。
     *
     * @param storage 当前测试使用的任务存储
     * @return 是否发生实际迁移
     */
    /**
     * 楠岃瘉鏈彂甯冨崟浜烘ā寮忎笅閫氳繃瀹㈡埛绔釜浜轰换鍔″叆鍙ｈ鍙栨椂锛屼細鍏堣嚜鍔ㄦ仮澶嶈緝鏂扮殑鐜╁鏂囦欢銆?     */
    private static void shouldRestorePlayerTasksWhenLoadingLocalSingleplayerTasks() {
        GuiTestSupport.resetState();
        TaskStorage storage = TodoListCommon.getTaskStorage();
        saveLocalTasks(storage, "Old Local");
        sleepForTimestampTick();
        savePlayerTasks(storage, "Recovered Player");

        FakeMinecraftClient minecraft = GuiTestSupport.createMinecraft(PLAYER_ID, "gui-tester", false);
        minecraft.setLocalServer(true);

        List<String> loadedTitles = loadPersonalTitles(storage, minecraft);
        List<String> localTitles = loadLocalTitles(storage);

        GuiTestSupport.assertEquals(List.of("Recovered Player"), loadedTitles, "鏈彂甯冨崟浜烘ā寮忚鍙栦釜浜轰换鍔℃椂搴旇嚜鍔ㄦ仮澶嶈緝鏂扮殑鐜╁鏂囦欢");
        GuiTestSupport.assertEquals(List.of("Recovered Player"), localTitles, "鑷姩鎭㈠鍚庢湰鍦颁釜浜轰换鍔℃枃浠跺簲琚慨姝ｅ埌鏈€鏂扮姸鎬?");
    }

    private static boolean migrate(TaskStorage storage) {
        try {
            return ClientTaskStorageHelper.migrateLocalTasksToPlayerStorage(storage, PLAYER_ID);
        } catch (Exception e) {
            throw new IllegalStateException("执行个人任务迁移时发生异常", e);
        }
    }

    /**
     * 调用本地回灌逻辑，减少重复样板。
     *
     * @param storage 当前测试使用的任务存储
     * @return 是否发生实际回灌
     */
    private static boolean restore(TaskStorage storage) {
        try {
            return ClientTaskStorageHelper.restorePlayerTasksToLocalStorage(storage, PLAYER_ID);
        } catch (Exception e) {
            throw new IllegalStateException("执行个人任务回灌时发生异常", e);
        }
    }

    /**
     * 向本地单机任务文件写入指定标题的任务列表。
     *
     * @param storage 当前测试使用的任务存储
     * @param titles 任务标题列表
     */
    private static void saveLocalTasks(TaskStorage storage, String... titles) {
        try {
            storage.saveTasks(createTasks(titles));
        } catch (Exception e) {
            throw new IllegalStateException("写入本地个人任务失败", e);
        }
    }

    /**
     * 向玩家任务文件写入指定标题的任务列表。
     *
     * @param storage 当前测试使用的任务存储
     * @param titles 任务标题列表
     */
    private static void savePlayerTasks(TaskStorage storage, String... titles) {
        try {
            storage.savePlayerTasks(PLAYER_ID, createTasks(titles));
        } catch (Exception e) {
            throw new IllegalStateException("写入玩家个人任务失败", e);
        }
    }

    /**
     * 读取玩家文件中的任务标题，便于断言迁移结果。
     *
     * @param storage 当前测试使用的任务存储
     * @return 玩家任务标题列表
     */
    private static List<String> loadPlayerTitles(TaskStorage storage) {
        try {
            return storage.loadPlayerTasks(PLAYER_ID).stream().map(Task::getTitle).toList();
        } catch (Exception e) {
            throw new IllegalStateException("读取玩家个人任务失败", e);
        }
    }

    /**
     * 读取本地文件中的任务标题，便于断言单人模式回灌结果。
     *
     * @param storage 当前测试使用的任务存储
     * @return 本地任务标题列表
     */
    private static List<String> loadLocalTitles(TaskStorage storage) {
        try {
            return storage.loadTasks().stream().map(Task::getTitle).toList();
        } catch (Exception e) {
            throw new IllegalStateException("读取本地个人任务失败", e);
        }
    }

    /**
     * 根据标题列表创建测试任务集合。
     *
     * @param titles 任务标题列表
     * @return 对应的任务对象列表
     */
    /**
     * 閫氳繃瀹㈡埛绔釜浜轰换鍔″叆鍙ｈ鍙栨爣棰樺垪琛紝鐢ㄤ簬楠岃瘉鏈彂甯冨崟浜烘ā寮忕殑鑷剤鍥炵亴閫昏緫銆?     */
    private static List<String> loadPersonalTitles(TaskStorage storage, FakeMinecraftClient minecraft) {
        try {
            return ClientTaskStorageHelper.loadPersonalTasks(storage, minecraft).stream().map(Task::getTitle).toList();
        } catch (Exception e) {
            throw new IllegalStateException("璇诲彇瀹㈡埛绔釜浜轰换鍔″垪琛ㄥけ璐?", e);
        }
    }

    private static List<Task> createTasks(String... titles) {
        return java.util.Arrays.stream(titles)
                .map(title -> new Task(title, ""))
                .toList();
    }

    /**
     * 等待最小时间粒度推进，确保文件时间戳比较稳定区分先后。
     */
    private static void sleepForTimestampTick() {
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待任务文件时间戳推进时被中断", e);
        }
    }
}
