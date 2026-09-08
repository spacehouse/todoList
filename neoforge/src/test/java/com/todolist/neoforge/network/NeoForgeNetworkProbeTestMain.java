package com.todolist.neoforge.network;

import java.lang.reflect.Method;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge 网络反射探测离线自测（适配方案 8.2-1）。
 *
 * <p>21.7 缺陷④教训：playBidirectional 三参重载在 21.7+ 仅注册服务端侧 handler，
 * 客户端启动校验会因 clientbound 载荷缺少客户端 handler 抛异常导致游戏无法启动；
 * 运行时以四参方法存在性选择注册形态。本测试直调
 * NeoForgeNetworkBridge.findFourArgPlayBidirectional()，断言当前依赖类路径
 * （NeoForge 21.9+）下反射探测确实命中四参形态，离线覆盖该启动盲区中
 * 可离线验证的部分；剩余盲区仍由实机冷启动兜底。
 */
public final class NeoForgeNetworkProbeTestMain {

    /**
     * 工具类不需要实例化。
     */
    private NeoForgeNetworkProbeTestMain() {
    }

    /**
     * 入口：断言四参 playBidirectional 可被反射探测命中。
     *
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        Method fourArg = NeoForgeNetworkBridge.findFourArgPlayBidirectional();
        if (fourArg == null) {
            System.out.println("[NET-PROBE][FAIL] four-arg playBidirectional not found on "
                    + PayloadRegistrar.class.getName());
            System.exit(1);
        }
        StringBuilder signature = new StringBuilder();
        for (Class<?> parameterType : fourArg.getParameterTypes()) {
            if (signature.length() > 0) {
                signature.append(", ");
            }
            signature.append(parameterType.getName());
        }
        System.out.println("[NET-PROBE][PASS] playBidirectional(" + signature + ") -> "
                + fourArg.getReturnType().getName());
    }
}
