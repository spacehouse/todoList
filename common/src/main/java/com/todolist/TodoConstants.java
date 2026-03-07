package com.todolist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组常量类。
 * 包含模组 ID、日志记录器等全局常量。
 */
public final class TodoConstants {
    /** 模组 ID */
    public static final String MOD_ID = "todolist";
    /** 全局日志记录器 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private TodoConstants() {
    }
}


