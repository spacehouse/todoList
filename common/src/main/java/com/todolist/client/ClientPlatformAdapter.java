package com.todolist.client;

import java.util.function.Supplier;

/**
 * 瀹㈡埛绔钩鍙伴€傞厤鍣ㄣ€? * 鐢ㄤ簬瑙ｈ€?common 妯″潡涓庡钩鍙扮壒瀹氱殑瀹㈡埛绔€昏緫锛堝 HUD 娓叉煋鍣ㄨ幏鍙栵級銆? */
public final class ClientPlatformAdapter {
    private static Supplier<TodoHudRenderer> hudRendererSupplier;

    private ClientPlatformAdapter() {
    }

    /**
     * 璁剧疆 HUD 娓叉煋鍣ㄧ殑鎻愪緵鑰呫€?     *
     * @param supplier 鎻愪緵鑰呭嚱鏁?     */
    public static void setHudRendererSupplier(Supplier<TodoHudRenderer> supplier) {
        hudRendererSupplier = supplier;
    }

    /**
     * 鑾峰彇 HUD 娓叉煋鍣ㄥ疄渚嬨€?     *
     * @return TodoHudRenderer 瀹炰緥锛岃嫢鏈敞鍐屽垯杩斿洖 null
     */
    public static TodoHudRenderer getHudRenderer() {
        return hudRendererSupplier != null ? hudRendererSupplier.get() : null;
    }
}


