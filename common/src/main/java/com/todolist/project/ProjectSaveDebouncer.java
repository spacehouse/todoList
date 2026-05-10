package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.storage.H2MaintenanceGuard;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 项目保存防抖器。
 * 用于合并短时间内的多次项目保存请求，降低重复写盘带来的 IO 开销。
 */
public final class ProjectSaveDebouncer {
    private static final long DEBOUNCE_MS = 750;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "todolist-project-save");
        t.setDaemon(true);
        return t;
    });

    private static final Object LOCK = new Object();
    private static boolean dirtyPersonal = false;
    private static boolean dirtyTeam = false;
    private static ScheduledFuture<?> pending = null;
    private static Future<?> inFlightSave = null;
    private static MinecraftServer lastServer = null;

    /**
     * 禁止外部实例化工具类。
     */
    private ProjectSaveDebouncer() {
    }

    /**
     * 请求在短延迟后保存指定范围的项目数据。
     * 多次调用会被合并为一次实际落盘操作。
     *
     * @param server 当前服务端实例
     * @param scope 需要保存的项目范围
     */
    public static void requestSave(MinecraftServer server, Project.Scope scope) {
        if (server == null || scope == null) {
            return;
        }
        synchronized (LOCK) {
            lastServer = server;
            if (scope == Project.Scope.PERSONAL) {
                dirtyPersonal = true;
            } else {
                dirtyTeam = true;
            }
            if (pending != null) {
                pending.cancel(false);
            }
            pending = SCHEDULER.schedule(() -> {
                MinecraftServer s;
                synchronized (LOCK) {
                    s = lastServer;
                }
                if (s != null) {
                    s.execute(() -> flushAsync(s));
                }
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 立即执行一次保存，并清除待执行的防抖状态。
     * 通常由服务端线程或停服收尾流程调用。
     *
     * @param server 当前服务端实例
     */
    public static void flushNow(MinecraftServer server) {
        if (server == null) {
            return;
        }

        while (true) {
            boolean doPersonal;
            boolean doTeam;
            Future<?> saveToWait;
            synchronized (LOCK) {
                if (pending != null) {
                    pending.cancel(false);
                    pending = null;
                }
                saveToWait = inFlightSave;
                if (saveToWait == null) {
                    doPersonal = dirtyPersonal;
                    doTeam = dirtyTeam;
                    dirtyPersonal = false;
                    dirtyTeam = false;
                } else {
                    doPersonal = false;
                    doTeam = false;
                }
            }

            if (saveToWait != null) {
                try {
                    waitForInFlightSave(saveToWait);
                } catch (Exception e) {
                    TodoConstants.LOGGER.error("Failed to wait for in-flight project save", e);
                }
                continue;
            }
            if (!doPersonal && !doTeam) {
                return;
            }

            try {
                saveCurrentProjects(doPersonal, doTeam);
                return;
            } catch (Exception e) {
                restoreDirtyFlags(doPersonal, doTeam);
                TodoConstants.LOGGER.error("Failed to save projects (debounced)", e);
                return;
            }
        }
    }

    /**
     * 将防抖触发的项目保存从服务端线程转移到后台线程。
     *
     * @param server 当前服务端实例
     */
    private static void flushAsync(MinecraftServer server) {
        if (server == null) {
            return;
        }
        boolean doPersonal;
        boolean doTeam;
        CompletableFuture<Void> saveFuture;
        synchronized (LOCK) {
            doPersonal = dirtyPersonal;
            doTeam = dirtyTeam;
            dirtyPersonal = false;
            dirtyTeam = false;
            pending = null;
            if (!doPersonal && !doTeam) {
                return;
            }
            saveFuture = new CompletableFuture<>();
            inFlightSave = saveFuture;
        }
        ProjectManager manager = TodoListCommon.getProjectManager();
        var personalProjects = doPersonal ? manager.getProjectsByScope(Project.Scope.PERSONAL) : java.util.List.<Project>of();
        var teamProjects = doTeam ? manager.getProjectsByScope(Project.Scope.TEAM) : java.util.List.<Project>of();
        SCHEDULER.execute(() -> {
            try {
                saveProjectSnapshots(doPersonal, personalProjects, doTeam, teamProjects);
                saveFuture.complete(null);
            } catch (Throwable throwable) {
                saveFuture.completeExceptionally(throwable);
            } finally {
                synchronized (LOCK) {
                    if (inFlightSave == saveFuture) {
                        inFlightSave = null;
                    }
                }
            }
        });
    }

    /**
     * 等待已经提交到后台线程的项目保存完成，保证强制刷新不会早于实际落盘返回。
     *
     * @param saveToWait 需要等待的后台保存任务
     * @throws ExecutionException 后台保存失败时抛出
     * @throws InterruptedException 当前线程等待时被中断
     */
    private static void waitForInFlightSave(Future<?> saveToWait) throws ExecutionException, InterruptedException {
        if (saveToWait != null) {
            saveToWait.get();
        }
    }

    /**
     * 在当前线程保存项目管理器中的最新项目数据。
     *
     * @param doPersonal 是否保存个人项目
     * @param doTeam 是否保存团队项目
     * @throws Exception 保存失败或 H2 写入保护检查失败时抛出
     */
    private static void saveCurrentProjects(boolean doPersonal, boolean doTeam) throws Exception {
        H2MaintenanceGuard.ensureWritableIfH2();
        ProjectManager manager = TodoListCommon.getProjectManager();
        ProjectStorage storage = TodoListCommon.getProjectStorage();
        if (doPersonal) {
            storage.saveProjects(manager.getProjectsByScope(Project.Scope.PERSONAL));
        }
        if (doTeam) {
            storage.saveTeamProjects(manager.getProjectsByScope(Project.Scope.TEAM));
        }
    }

    /**
     * 在后台线程保存项目快照，避免 H2 或文件写入阻塞服务端线程。
     *
     * @param doPersonal 是否保存个人项目
     * @param personalProjects 个人项目快照
     * @param doTeam 是否保存团队项目
     * @param teamProjects 团队项目快照
     * @throws Exception 保存失败或 H2 写入保护检查失败时抛出
     */
    private static void saveProjectSnapshots(boolean doPersonal,
                                             java.util.List<Project> personalProjects,
                                             boolean doTeam,
                                             java.util.List<Project> teamProjects) throws Exception {
        try {
            H2MaintenanceGuard.ensureWritableIfH2();
            ProjectStorage storage = TodoListCommon.getProjectStorage();
            if (doPersonal) {
                storage.saveProjects(personalProjects);
            }
            if (doTeam) {
                storage.saveTeamProjects(teamProjects);
            }
        } catch (Exception e) {
            restoreDirtyFlags(doPersonal, doTeam);
            TodoConstants.LOGGER.error("Failed to save projects (debounced background)", e);
            throw e;
        }
    }

    /**
     * 保存失败时恢复 dirty 标记，避免内存改动被误认为已经落盘。
     *
     * @param personalFailed 本次是否尝试保存个人项目
     * @param teamFailed 本次是否尝试保存团队项目
     */
    private static void restoreDirtyFlags(boolean personalFailed, boolean teamFailed) {
        synchronized (LOCK) {
            dirtyPersonal = dirtyPersonal || personalFailed;
            dirtyTeam = dirtyTeam || teamFailed;
        }
    }
}
