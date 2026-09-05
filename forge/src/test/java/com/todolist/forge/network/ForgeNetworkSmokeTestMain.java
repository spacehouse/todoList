package com.todolist.forge.network;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;

/**
 * ForgeNetworkBridge 网络桥冒烟测试。
 *
 * <p>不依赖外部测试框架，直接通过 main 方法执行注册链路自检，
 * 与 fabric 侧的 FabricNetworkingCompatSmokeTestMain 对称，
 * 用于拦截以下三类历史回归：</p>
 * <ul>
 *   <li>Forge 分代 API 适配失败（47.x newSimpleChannel/registerMessage vs
 *       48.x+ ChannelBuilder/messageBuilder 链，1.20.2 曾因 newSimpleChannel 移除而启动崩溃）；</li>
 *   <li>版本协商策略破坏（ACCEPT_MISSING 只接受 MISSING，双端必装 mod 为 PRESENT 会被拒，
 *       必须保持恒真协商）；</li>
 *   <li>客户端连接解析退化（1.20.2 起 connection 字段上移父类，
 *       字段扫描必须沿类层级向上遍历，否则发送被静默丢弃）。</li>
 * </ul>
 *
 * <p>已知边界：真实握手、事件总线登录钩子与玩家定向发送依赖完整客户端/服务端，
 * 本测试不覆盖，需实机验证。</p>
 */
public final class ForgeNetworkSmokeTestMain {

    /** 失败用例计数，非零时以退出码 1 结束，令 Gradle 任务失败。 */
    private static int failed;

    /** 工具类禁止实例化。 */
    private ForgeNetworkSmokeTestMain() {
    }

    /**
     * 测试入口：引导注册表后依次执行各用例并汇总结果。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        try {
            bootstrapMinecraftRegistries();
            runAllCases();
        } catch (Throwable fatal) {
            System.out.println("[FORGE][FATAL] unexpected top-level failure");
            fatal.printStackTrace(System.out);
            System.exit(1);
        }
    }

    /**
     * 依次执行全部用例并按结果退出。
     */
    private static void runAllCases() {
        runTestCase(
                "ForgeNetworkSmokeTestMain.shouldLocateChannelFactoryOnCurrentForgeApi",
                ForgeNetworkSmokeTestMain::shouldLocateChannelFactoryOnCurrentForgeApi
        );
        runTestCase(
                "ForgeNetworkSmokeTestMain.shouldLocateDispatchRegistrationShapes",
                ForgeNetworkSmokeTestMain::shouldLocateDispatchRegistrationShapes
        );
        runTestCase(
                "ForgeNetworkSmokeTestMain.shouldAcceptAnyRemoteVersion",
                ForgeNetworkSmokeTestMain::shouldAcceptAnyRemoteVersion
        );
        runTestCase(
                "ForgeNetworkSmokeTestMain.shouldResolveConnectionFromSuperclassListener",
                ForgeNetworkSmokeTestMain::shouldResolveConnectionFromSuperclassListener
        );
        runTestCase(
                "ForgeNetworkSmokeTestMain.shouldReportCanSendFalseWithoutClient",
                ForgeNetworkSmokeTestMain::shouldReportCanSendFalseWithoutClient
        );

        if (failed > 0) {
            System.out.println("[FORGE][RESULT][FAIL] " + failed + " case(s) failed");
            System.exit(1);
        }
        System.out.println("[FORGE][RESULT][PASS] all cases passed");
    }

    /**
     * 执行单个用例并打印结果，异常计为失败。
     *
     * @param name 用例名称
     * @param caseBody 用例主体
     */
    private static void runTestCase(String name, Runnable caseBody) {
        long start = System.currentTimeMillis();
        try {
            caseBody.run();
            System.out.println("[FORGE][CASE ][PASS] " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
        } catch (Throwable t) {
            failed++;
            System.out.println("[FORGE][CASE ][FAIL] " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
            t.printStackTrace(System.out);
        }
    }

    /**
     * 用例：当前 Forge API 代际的通道工厂形状完整可用。
     * SimpleChannel 的真实构造依赖 eventbus 对 NetworkEvent 的字节码增强
     * （运行时由 modlauncher 完成，裸 JVM 不具备），因此这里验证分代工厂方法
     * 的存在性与签名：47.x 的 NetworkRegistry.newSimpleChannel(4参)，
     * 或 48.x+ 的 ChannelBuilder 链式五方法。1.20.2 曾因 newSimpleChannel
     * 被移除而启动崩溃，本用例可在构建期拦截同类破坏。
     *
     * @return null（占位，供聚合器使用）
     */
    private static Object shouldLocateChannelFactoryOnCurrentForgeApi() {
        boolean legacyAvailable = false;
        boolean builderAvailable = false;
        try {
            Class<?> registryClass = Class.forName("net.minecraftforge.network.NetworkRegistry");
            legacyAvailable = findPublicMethod(registryClass, "newSimpleChannel", 4) != null;
        } catch (ClassNotFoundException ignored) {
            // 48.x+ 移除了 NetworkRegistry 工厂，属预期
        }
        try {
            Class<?> builderClass = Class.forName("net.minecraftforge.network.ChannelBuilder");
            Class<?> versionTestClass = Class.forName("net.minecraftforge.network.Channel$VersionTest");
            builderAvailable = builderClass.getMethod("named", ResourceLocation.class) != null
                    && builderClass.getMethod("networkProtocolVersion", int.class) != null
                    && builderClass.getMethod("clientAcceptedVersions", versionTestClass) != null
                    && builderClass.getMethod("serverAcceptedVersions", versionTestClass) != null
                    && builderClass.getMethod("simpleChannel") != null;

            // 恒真 VersionTest 代理必须可创建（版本协商策略的前提形状）
            Object acceptAll = java.lang.reflect.Proxy.newProxyInstance(
                    versionTestClass.getClassLoader(),
                    new Class<?>[]{versionTestClass},
                    (proxy, method, methodArgs) -> {
                        if ("accepts".equals(method.getName()) && method.getReturnType() == boolean.class) {
                            return Boolean.TRUE;
                        }
                        throw new UnsupportedOperationException("Unexpected VersionTest method: " + method.getName());
                    });
            if (acceptAll == null) {
                throw new AssertionError("VersionTest accept-all proxy was not created");
            }
        } catch (ClassNotFoundException e) {
            // 47.x 没有 ChannelBuilder，属预期
        } catch (ReflectiveOperationException e) {
            // builder 类存在但链式方法缺失：按形状不可用处理，由下方联合断言统一拦截
        }
        System.out.println("[FORGE][INFO ] channel factory shapes: legacy(47.x)=" + legacyAvailable
                + ", builder(48.x+)=" + builderAvailable);
        if (!legacyAvailable && !builderAvailable) {
            throw new AssertionError("no channel factory shape available on this Forge generation");
        }
        return null;
    }

    /**
     * 用例：分发消息注册的当前代际形状完整可用。
     * 47.x 为 SimpleChannel.registerMessage 五参；48.x+ 为 messageBuilder 链式
     * （encoder/decoder/consumerNetworkThread/add）。
     * SimpleChannel 的包路径在 48.x 从 network.simple 上移到 network，
     * 与 ForgeNetworkBridge 在运行时通道实例上反射定位的约定保持一致，
     * 两个已知包布局任一命中即可。
     *
     * @return null（占位，供聚合器使用）
     */
    private static Object shouldLocateDispatchRegistrationShapes() {
        Class<?> channelClass = findSimpleChannelClass();
        boolean legacyShape = findPublicMethod(channelClass, "registerMessage", 5) != null;

        boolean builderShape = false;
        try {
            Method messageBuilder = channelClass.getMethod("messageBuilder", Class.class, int.class);
            Class<?> builderType = messageBuilder.getReturnType();
            builderShape = findPublicMethod(builderType, "encoder", 1) != null
                    && findPublicMethod(builderType, "decoder", 1) != null
                    && findPublicMethod(builderType, "consumerNetworkThread", 1) != null
                    && findPublicMethod(builderType, "add", 0) != null;
        } catch (NoSuchMethodException e) {
            // messageBuilder 链不存在：由 legacyShape 兜底判定
        }

        System.out.println("[FORGE][INFO ] dispatch registration shapes: registerMessage(47.x)=" + legacyShape
                + ", messageBuilder(48.x+)=" + builderShape);
        if (!legacyShape && !builderShape) {
            throw new AssertionError("no dispatch registration shape available on this Forge generation");
        }
        return null;
    }

    /**
     * 定位当前 Forge 代际的 SimpleChannel 类。
     * 47.x 位于 network.simple 子包，48.x+ 上移到 network 包。
     *
     * @return SimpleChannel 类对象
     */
    private static Class<?> findSimpleChannelClass() {
        for (String candidate : new String[]{
                "net.minecraftforge.network.simple.SimpleChannel",
                "net.minecraftforge.network.SimpleChannel"}) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException ignored) {
                // 尝试下一个已知包布局
            }
        }
        throw new AssertionError("SimpleChannel class missing on classpath (checked 47.x and 48.x+ package layouts)");
    }

    /**
     * 按名称与参数个数在目标类的 public 方法中查找。
     *
     * @param type 目标类
     * @param name 方法名
     * @param paramCount 参数个数
     * @return 匹配的方法，未找到返回 null
     */
    private static Method findPublicMethod(Class<?> type, String name, int paramCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        return null;
    }

    /**
     * 用例：版本协商保持恒真策略。
     * 任意远端版本（含空、非数字）都应被接受，防止回归成 ACCEPT_MISSING 语义。
     */
    private static void shouldAcceptAnyRemoteVersion() {
        Method acceptor = declaredMethod("acceptRemoteVersion");
        Object first = invokeStatic(acceptor, "1");
        Object second = invokeStatic(acceptor, "0.19.3");
        if (!Boolean.TRUE.equals(first) || !Boolean.TRUE.equals(second)) {
            throw new AssertionError("acceptRemoteVersion must accept any version, got: " + first + ", " + second);
        }
    }

    /**
     * 用例：从客户端监听器解析 Netty 连接。
     * 1.20.2 起 connection 字段上移父类，字段扫描必须含继承层级；
     * 同时验证 getConnection 方法优先路径。
     */
    private static void shouldResolveConnectionFromSuperclassListener() {
        net.minecraft.network.Connection connection = newConnectionInstance();
        ChildListener child = new ChildListener(connection);
        Object resolved = invokePrivateStatic("resolveNettyConnection", child);
        if (resolved != connection) {
            throw new AssertionError("connection field on superclass was not resolved, got: " + resolved);
        }

        MethodListener methodListener = new MethodListener();
        Object byMethod = invokePrivateStatic("resolveNettyConnection", methodListener);
        if (byMethod != MethodListener.SENTINEL) {
            throw new AssertionError("getConnection() method was not preferred, got: " + byMethod);
        }
    }

    /**
     * 用例：无客户端环境时 canSend 安全返回 false 而不抛异常。
     * 通过直接注入桩通道绕过 init() 的事件总线注册（裸 JVM 无 Forge 运行时）。
     */
    private static void shouldReportCanSendFalseWithoutClient() {
        try {
            Field initialized = ForgeNetworkBridge.class.getDeclaredField("initialized");
            initialized.setAccessible(true);
            initialized.setBoolean(null, true);
            Field channelField = ForgeNetworkBridge.class.getDeclaredField("simpleChannel");
            channelField.setAccessible(true);
            channelField.set(null, new Object());

            boolean canSend = ForgeNetworkBridge.canSend(new ResourceLocation("todolist", "smoke_probe"));
            if (canSend) {
                throw new AssertionError("canSend must be false without a running client");
            }
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("failed to prepare canSend probe", e);
        }
    }

    /**
     * 构造 Netty Connection 实例：优先选参数最少的构造器传 null，
     * 失败时退回 Unsafe 无构造实例化（测试环境无需可用连接，仅验证字段解析）。
     *
     * @return Connection 实例
     */
    private static net.minecraft.network.Connection newConnectionInstance() {
        Class<?> connectionType;
        try {
            connectionType = Class.forName("net.minecraft.network.Connection");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Connection class missing on classpath", e);
        }
        Constructor<?> best = null;
        for (Constructor<?> candidate : connectionType.getDeclaredConstructors()) {
            if (best == null || candidate.getParameterCount() < best.getParameterCount()) {
                best = candidate;
            }
        }
        try {
            best.setAccessible(true);
            Object[] nullArgs = new Object[best.getParameterCount()];
            return (net.minecraft.network.Connection) best.newInstance(nullArgs);
        } catch (Throwable ignored) {
            // 构造器内部对 null 参数敏感时退回 Unsafe
        }
        try {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            return (net.minecraft.network.Connection) unsafe.allocateInstance(connectionType);
        } catch (Throwable e) {
            throw new AssertionError("failed to instantiate Connection", e);
        }
    }

    /**
     * 模拟 1.20.2+ 客户端监听器层次：Connection 字段声明在父类。
     */
    private abstract static class BaseListener {
        private final net.minecraft.network.Connection connection;

        BaseListener(net.minecraft.network.Connection connection) {
            this.connection = connection;
        }
    }

    /**
     * 子类自身不声明 Connection 字段，也不提供 getConnection 方法。
     */
    private static final class ChildListener extends BaseListener {
        ChildListener(net.minecraft.network.Connection connection) {
            super(connection);
        }
    }

    /**
     * 提供 getConnection 公开方法的监听器：验证方法优先路径。
     */
    private static final class MethodListener {
        static final Object SENTINEL = new Object();

        public Object getConnection() {
            return SENTINEL;
        }
    }

    /**
     * 引导 Minecraft 注册表，防止通道构建链路意外触碰未引导的注册表。
     * 失败不视为错误（多数 Forge 网络类不依赖注册表）。
     */
    private static void bootstrapMinecraftRegistries() {
        try {
            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
            System.out.println("[FORGE][SETUP ] minecraft bootstrap completed");
        } catch (Throwable t) {
            System.out.println("[FORGE][SETUP ] minecraft bootstrap skipped: " + t);
        }
    }

    /**
     * 按名称与参数调用 ForgeNetworkBridge 的私有静态方法。
     *
     * @param name 方法名
     * @param args 实参
     * @return 方法返回值
     */
    private static Object invokePrivateStatic(String name, Object... args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        try {
            Method method = ForgeNetworkBridge.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (NoSuchMethodException e) {
            // 兜底：按名称匹配首个参数个数一致的方法（避免基本类型装箱差异）
            for (Method candidate : ForgeNetworkBridge.class.getDeclaredMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == args.length) {
                    candidate.setAccessible(true);
                    try {
                        return candidate.invoke(null, args);
                    } catch (Exception invokeFailure) {
                        throw new AssertionError("invoke " + name + " failed", invokeFailure);
                    }
                }
            }
            throw new AssertionError("method not found: " + name + "/" + args.length, e);
        } catch (Exception e) {
            throw new AssertionError("invoke " + name + " failed", e);
        }
    }

    /**
     * 调用已定位的私有静态方法。
     *
     * @param method 目标方法
     * @param args 实参
     * @return 方法返回值
     */
    private static Object invokeStatic(Method method, Object... args) {
        try {
            return method.invoke(null, args);
        } catch (Exception e) {
            throw new AssertionError("invoke " + method.getName() + " failed", e);
        }
    }

    /**
     * 定位 ForgeNetworkBridge 的私有静态方法。
     *
     * @param name 方法名
     * @return 方法对象
     */
    private static Method declaredMethod(String name) {
        try {
            Method method = ForgeNetworkBridge.class.getDeclaredMethod(name, String.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            throw new AssertionError("method not found: " + name, e);
        }
    }
}
