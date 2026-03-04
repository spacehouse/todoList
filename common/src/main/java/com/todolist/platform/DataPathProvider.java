package com.todolist.platform;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 鎻愪緵鏁版嵁瀛樺偍璺緞鐨勫伐鍏风被銆? * 鐢ㄤ簬鑾峰彇娓告垙鐩綍銆佹ā缁勬暟鎹洰褰曘€侀」鐩洰褰曞強鐜╁鏁版嵁鐩綍绛夈€? */
public final class DataPathProvider {
    /** 妯＄粍鏁版嵁鏍圭洰褰曞悕绉?*/
    public static final String TODO_FOLDER = "todo";
    /** 椤圭洰鏁版嵁鐩綍鍚嶇О */
    public static final String PROJECTS_FOLDER = "projects";
    /** 鐜╁鏁版嵁鐩綍鍚嶇О */
    public static final String PLAYERS_FOLDER = "players";

    private static Supplier<Path> gameDirSupplier;

    private DataPathProvider() {
    }

    /**
     * 璁剧疆娓告垙鐩綍鐨勬彁渚涜€呫€?     * 蹇呴』鍦ㄨ皟鐢?getGameDir 涔嬪墠鐢卞钩鍙扮壒瀹氬疄鐜帮紙濡?Fabric 鎴?Forge锛夎繘琛屽垵濮嬪寲銆?     *
     * @param supplier 娓告垙鐩綍鐨?Path 鎻愪緵鑰?     */
    public static void setGameDirSupplier(Supplier<Path> supplier) {
        gameDirSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    /**
     * 鑾峰彇娓告垙鏍圭洰褰曘€?     *
     * @return 娓告垙鏍圭洰褰曠殑 Path
     * @throws IllegalStateException 濡傛灉 supplier 鏈垵濮嬪寲
     */
    public static Path getGameDir() {
        if (gameDirSupplier == null) {
            throw new IllegalStateException("Game directory supplier has not been initialized. " +
                    "Make sure to call setGameDirSupplier during mod initialization.");
        }
        return gameDirSupplier.get().toAbsolutePath();
    }

    /**
     * 鑾峰彇寰呭姙浜嬮」妯＄粍鐨勬暟鎹牴鐩綍銆?     *
     * @return 妯＄粍鏁版嵁鏍圭洰褰曠殑 Path
     */
    public static Path getTodoDataDir() {
        return getGameDir().resolve(TODO_FOLDER);
    }

    /**
     * 鑾峰彇椤圭洰鏁版嵁瀛樺偍鐩綍銆?     *
     * @return 椤圭洰鐩綍鐨?Path
     */
    public static Path getProjectsDir() {
        return getTodoDataDir().resolve(PROJECTS_FOLDER);
    }

    /**
     * 鑾峰彇椤圭洰鍏宠仈鐨勭帺瀹舵暟鎹洰褰曘€?     *
     * @return 鐜╁鏁版嵁鐩綍鐨?Path
     */
    public static Path getProjectPlayersDir() {
        return getProjectsDir().resolve(PLAYERS_FOLDER);
    }

    /**
     * 鑾峰彇鍏ㄥ眬浠诲姟鍏宠仈鐨勭帺瀹舵暟鎹洰褰曘€?     *
     * @return 鐜╁鏁版嵁鐩綍鐨?Path
     */
    public static Path getTaskPlayersDir() {
        return getTodoDataDir().resolve(PLAYERS_FOLDER);
    }
}


