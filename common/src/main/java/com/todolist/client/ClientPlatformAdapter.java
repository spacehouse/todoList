package com.todolist.client;

import java.util.function.Supplier;

/**
 * 客户端平台适配器。
 * 用于解耦 common 模块与平台特定客户端逻辑（例如 HUD 渲染器获取）。
 */
public final class ClientPlatformAdapter {
    private static Supplier<TodoHudRenderer> hudRendererSupplier;

    private ClientPlatformAdapter() {
    }

    /**
     * 设置 HUD 渲染器提供者。
     *
     * @param supplier 提供者函数
     */
    public static void setHudRendererSupplier(Supplier<TodoHudRenderer> supplier) {
        hudRendererSupplier = supplier;
    }

    /**
     * 获取 HUD 渲染器实例。
     *
     * @return TodoHudRenderer 实例，未注册时返回 null
     */
    public static TodoHudRenderer getHudRenderer() {
        return hudRendererSupplier != null ? hudRendererSupplier.get() : null;
    }
}


