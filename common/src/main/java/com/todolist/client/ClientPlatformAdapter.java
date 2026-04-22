package com.todolist.client;

import java.util.function.Supplier;

/**
 * 客户端平台适配器。
 * 用于解耦 common 模块与平台侧客户端实现之间的 HUD 渲染器访问。
 */
public final class ClientPlatformAdapter {
    private static Supplier<TodoHudRenderer> hudRendererSupplier;

    /**
     * 禁止外部实例化工具类。
     */
    private ClientPlatformAdapter() {
    }

    /**
     * 设置 HUD 渲染器提供者。
     *
     * @param supplier 提供 HUD 渲染器实例的函数
     */
    public static void setHudRendererSupplier(Supplier<TodoHudRenderer> supplier) {
        hudRendererSupplier = supplier;
    }

    /**
     * 获取当前注册的 HUD 渲染器。
     *
     * @return HUD 渲染器实例；未注册时返回 null
     */
    public static TodoHudRenderer getHudRenderer() {
        return hudRendererSupplier != null ? hudRendererSupplier.get() : null;
    }
}
