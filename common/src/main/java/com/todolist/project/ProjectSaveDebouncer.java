package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import com.todolist.storage.H2MaintenanceGuard;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
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
                    s.execute(() -> flushNow(s));
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

        boolean doPersonal;
        boolean doTeam;
        synchronized (LOCK) {
            doPersonal = dirtyPersonal;
            doTeam = dirtyTeam;
            dirtyPersonal = false;
            dirtyTeam = false;
            pending = null;
        }

        try {
            H2MaintenanceGuard.ensureWritableIfH2();
            ProjectManager manager = TodoListCommon.getProjectManager();
            ProjectStorage storage = TodoListCommon.getProjectStorage();
            if (doPersonal) {
                storage.saveProjects(manager.getProjectsByScope(Project.Scope.PERSONAL));
            }
            if (doTeam) {
                storage.saveTeamProjects(manager.getProjectsByScope(Project.Scope.TEAM));
            }
        } catch (Exception e) {
            restoreDirtyFlags(doPersonal, doTeam);
            TodoConstants.LOGGER.error("Failed to save projects (debounced)", e);
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
