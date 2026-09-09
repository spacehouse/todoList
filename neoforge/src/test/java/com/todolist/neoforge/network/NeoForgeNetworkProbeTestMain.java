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
        assertDispatchChannelIdStable();
    }

    /**
     * 断言通用桥接载荷的通道 id 字符串仍为 todolist:bridge（适配方案 8.2-2）。
     *
     * <p>1.21.11 将 ResourceLocation 重命名为 Identifier，只允许类型名变化，
     * 不得改变通道字符串字面量；该 id 是 NeoForgeDispatchPayload 的网络注册
     * 标识，与既有联机对端保持兼容的连续性锚点。经 TYPE.id().toString() 取值，
     * 不点名具体 id 类型，v1_21_1 基线与 v1_21_11 覆盖组下均可编译运行。
     */
    private static void assertDispatchChannelIdStable() {
        String channelId = NeoForgeDispatchPayload.TYPE.id().toString();
        if (!"todolist:bridge".equals(channelId)) {
            System.out.println("[NET-PROBE][FAIL] dispatch channel id drifted: " + channelId);
            System.exit(1);
        }
        System.out.println("[NET-PROBE][PASS] dispatch channel id = " + channelId);
    }
}
