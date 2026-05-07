package com.todolist.gui;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.client.ClientBridge;
import com.todolist.client.ClientPlatformAdapter;
import com.todolist.client.ClientTaskStorageHelper;
import com.todolist.client.TodoHudRenderer;
import com.todolist.platform.DataPathProvider;
import com.todolist.storage.H2MaintenanceGuard;
import com.todolist.task.Task;
import com.todolist.task.TaskManager;

import java.util.List;

import net.minecraft.client.Minecraft;

/**
 * TodoScreen 持久化支持类，集中处理个人/团队任务保存逻辑。
 */
final class TodoScreenPersistenceSupport {
    /**
     * 保存流程结果。
     */
    static final class SaveOutcome {
        final boolean personalSaved;
        final boolean teamSaved;
        final boolean personalHasUnsavedChanges;
        final boolean teamHasUnsavedChanges;
        final Throwable failure;

        SaveOutcome(boolean personalSaved,
                    boolean teamSaved,
                    boolean personalHasUnsavedChanges,
                    boolean teamHasUnsavedChanges,
                    Throwable failure) {
            this.personalSaved = personalSaved;
            this.teamSaved = teamSaved;
            this.personalHasUnsavedChanges = personalHasUnsavedChanges;
            this.teamHasUnsavedChanges = teamHasUnsavedChanges;
            this.failure = failure;
        }

        boolean allSaved() {
            return personalSaved && teamSaved;
        }

        boolean hasUnsavedChanges() {
            return personalHasUnsavedChanges || teamHasUnsavedChanges;
        }

        /**
         * 返回保存流程中的首个失败异常。
         *
         * @return 首个失败异常；全部成功时返回 null
         */
        Throwable failure() {
            return failure;
        }
    }

    /**
     * 工具类不需要实例化。
     */
    private TodoScreenPersistenceSupport() {
    }

    /**
     * 保存个人与团队任务，并返回完整保存结果。
     *
     * @param personalTaskManager 个人任务管理器
     * @param teamTaskManager 团队任务管理器
     * @param minecraft 当前客户端实例
     * @param personalDirty 个人任务是否有未保存变更
     * @param teamDirty 团队任务是否有未保存变更
     * @return 保存结果
     */
    static SaveOutcome saveAll(TaskManager personalTaskManager,
                               TaskManager teamTaskManager,
                               Minecraft minecraft,
                               boolean personalDirty,
                               boolean teamDirty) {
        boolean personalSaved = !personalDirty;
        boolean teamSaved = !teamDirty;
        boolean personalHasUnsavedChanges = personalDirty;
        boolean teamHasUnsavedChanges = teamDirty;
        Throwable failure = null;

        if (personalDirty) {
            try {
                H2MaintenanceGuard.ensureWritableIfH2();
                savePersonalTasks(personalTaskManager, minecraft);
                personalSaved = true;
                personalHasUnsavedChanges = false;
            } catch (Exception e) {
                personalSaved = false;
                failure = e;
                TodoConstants.LOGGER.error("Failed to save personal tasks", e);
            }
        }

        if (teamDirty) {
            try {
                H2MaintenanceGuard.ensureWritableIfH2();
                saveTeamTasks(teamTaskManager, minecraft);
                teamSaved = true;
                teamHasUnsavedChanges = false;
            } catch (Exception e) {
                teamSaved = false;
                if (failure == null) {
                    failure = e;
                }
                TodoConstants.LOGGER.error("Failed to save team tasks", e);
            }
        }

        return new SaveOutcome(
                personalSaved,
                teamSaved,
                personalHasUnsavedChanges,
                teamHasUnsavedChanges,
                failure
        );
    }

    /**
     * 丢弃个人任务未保存改动，恢复为本地持久化快照。
     *
     * @param personalTaskManager 个人任务管理器
     * @param minecraft 当前客户端实例
     * @return 是否成功恢复
     */
    static boolean discardUnsavedPersonalTasks(TaskManager personalTaskManager, Minecraft minecraft) {
        if (personalTaskManager == null) {
            return false;
        }
        try {
            List<Task> persistedTasks = ClientTaskStorageHelper.loadPersonalTasksSafe(
                    TodoListCommon.getTaskStorage(),
                    minecraft
            );
            personalTaskManager.clearAll();
            for (Task task : persistedTasks) {
                personalTaskManager.addTask(task);
            }
            return true;
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to discard unsaved personal tasks on close", e);
            return false;
        }
    }

    /**
     * 保存个人任务数据，并同步到服务端与 HUD。
     *
     * @param personalTaskManager 个人任务管理器
     * @param minecraft 当前客户端实例
     * @throws Exception 当本地存储或同步过程失败时抛出
     */
    static void savePersonalTasks(TaskManager personalTaskManager, Minecraft minecraft) throws Exception {
        if (personalTaskManager == null) {
            return;
        }
        List<Task> personalTasks = personalTaskManager.getAllTasks();
        ClientTaskStorageHelper.savePersonalTasks(TodoListCommon.getTaskStorage(), minecraft, personalTasks);
        TodoScreen.updateCachedPersonalTasksSnapshot(DataPathProvider.getStorageNamespace(), personalTasks);
        if (ClientBridge.ops() != null) {
            ClientBridge.ops().sendReplaceAllTasks(personalTasks);
        }
        TodoHudRenderer renderer = ClientPlatformAdapter.getHudRenderer();
        if (renderer != null) {
            renderer.syncPersonalTasksFromGui(personalTasks);
        }
        TodoConstants.LOGGER.info("Personal tasks saved");
    }

    /**
     * 保存团队任务数据，并同步到服务端。
     *
     * @param teamTaskManager 团队任务管理器
     * @param minecraft 当前客户端实例
     * @throws Exception 当团队任务存储或同步失败时抛出
     */
    static void saveTeamTasks(TaskManager teamTaskManager, Minecraft minecraft) throws Exception {
        if (teamTaskManager == null) {
            return;
        }
        List<Task> teamTasks = teamTaskManager.getAllTasks();
        if (ClientTaskStorageHelper.shouldUsePublishedLocalPlayerStorage(minecraft)) {
            TodoListCommon.getTaskStorage().saveTeamTasks(teamTasks);
        }
        if (ClientBridge.ops() != null) {
            ClientBridge.ops().sendReplaceTeamTasks(teamTasks);
        }
        TodoConstants.LOGGER.info("Team tasks saved");
    }
}
