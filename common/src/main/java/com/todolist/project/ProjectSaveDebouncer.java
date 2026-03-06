package com.todolist.project;

import com.todolist.TodoConstants;
import com.todolist.TodoListCommon;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 项目保存防抖：将短时间内的多次保存请求合并为一次写盘，降低 IO 频率。
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

    private ProjectSaveDebouncer() {}

    /**
     * 请求在短延迟后保存指定范围的项目数据；多次调用会被合并。
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
     * 立即执行一次保存（会清空 pending 标记），通常由服务端线程调度调用。
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
            ProjectManager manager = TodoListCommon.getProjectManager();
            ProjectStorage storage = TodoListCommon.getProjectStorage();
            if (doPersonal) {
                storage.saveProjects(manager.getProjectsByScope(Project.Scope.PERSONAL));
            }
            if (doTeam) {
                storage.saveTeamProjects(manager.getProjectsByScope(Project.Scope.TEAM));
            }
        } catch (Exception e) {
            TodoConstants.LOGGER.error("Failed to save projects (debounced)", e);
        }
    }
}



