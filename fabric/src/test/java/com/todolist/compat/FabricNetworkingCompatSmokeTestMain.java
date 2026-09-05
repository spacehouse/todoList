package com.todolist.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;

/**
 * FabricNetworkingCompat 网络兼容层冒烟测试。
 *
 * <p>不依赖外部测试框架，直接通过 main 方法执行注册链路自检，
 * 用于拦截以下三类历史回归：</p>
 * <ul>
 *   <li>签名发现失败（如按错误参数形状查找 receive 方法）；</li>
 *   <li>Mojmap 字符串反射残留（发布环境必然失败的调用路径）；</li>
 *   <li>codec 代理 cast 自引用语义破坏（注册后 codec 为 null，编码时才 NPE）。</li>
 * </ul>
 *
 * <p>已知边界：canSend 握手协商类问题依赖真实网络连接，
 * 本测试无法覆盖，需实机验证。</p>
 */
public final class FabricNetworkingCompatSmokeTestMain {

    /** 失败用例计数，非零时以退出码 1 结束，令 Gradle 任务失败。 */
    private static int failed;

    /** 工具类禁止实例化。 */
    private FabricNetworkingCompatSmokeTestMain() {
    }

    /**
     * 测试入口：依次执行各用例并汇总结果。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        try {
            injectDevelopmentLauncherStub();
            bootstrapMinecraftRegistries();
            runAllCases();
        } catch (Throwable fatal) {
            log("[NET][FATAL] unexpected top-level failure");
            fatal.printStackTrace(System.out);
            StringBuilder trace = new StringBuilder("[NET][FATAL] ").append(fatal);
            for (StackTraceElement element : fatal.getStackTrace()) {
                trace.append("\n    at ").append(element);
            }
            log(trace.toString());
            System.exit(1);
        }
    }

    /**
     * 依次执行全部用例并按结果退出。
     */
    private static void runAllCases() {
        runTestCase(
                "FabricNetworkingCompatSmokeTestMain.shouldRegisterServerReceiverOnCurrentApi",
                FabricNetworkingCompatSmokeTestMain::shouldRegisterServerReceiverOnCurrentApi
        );
        runTestCase(
                "FabricNetworkingCompatSmokeTestMain.shouldRegisterClientReceiverOnCurrentApi",
                FabricNetworkingCompatSmokeTestMain::shouldRegisterClientReceiverOnCurrentApi
        );
        runTestCase(
                "FabricNetworkingCompatSmokeTestMain.shouldRegisterPayloadCodecWithNonNullCast",
                FabricNetworkingCompatSmokeTestMain::shouldRegisterPayloadCodecWithNonNullCast
        );
        runTestCase(
                "FabricNetworkingCompatSmokeTestMain.shouldInvokePublicMethodOnPackagePrivateClass",
                FabricNetworkingCompatSmokeTestMain::shouldInvokePublicMethodOnPackagePrivateClass
        );

        if (failed > 0) {
            System.out.println("[NET][RESULT][FAIL] " + failed + " case(s) failed");
            log("[NET][RESULT][FAIL] " + failed + " case(s) failed");
            System.exit(1);
        }
        System.out.println("[NET][RESULT][PASS] all cases passed");
        log("[NET][RESULT][PASS] all cases passed");
    }

    /**
     * 将测试输出同步写入构建目录日志文件，规避控制台管道丢失输出的问题。
     *
     * @param line 日志行
     */
    private static void log(String line) {
        try {
            java.nio.file.Files.write(
                    java.nio.file.Path.of("build", "smoke-result.log"),
                    (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
            // 日志落盘失败不影响测试结果本身
        }
    }

    /**
     * 为裸 JVM 注入 isDevelopment=true 的 FabricLauncher 桩。
     *
     * <p>部分 Fabric API 版本（如 1.20.3 的 networking 3.x）在 NetworkingImpl
     * 静态初始化时调用 FabricLauncherBase.getLauncher().isDevelopment()，
     * 无 Knot 启动器的测试 JVM 中该值为 null 会直接 NPE。
     * 注入失败不视为错误，由用例前置守卫决定是否跳过。</p>
     */
    private static void injectDevelopmentLauncherStub() {
        try {
            Class<?> baseClass = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Class<?> launcherInterface = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncher");
            Object stub = java.lang.reflect.Proxy.newProxyInstance(
                    launcherInterface.getClassLoader(),
                    new Class<?>[]{launcherInterface},
                    (proxy, method, methodArgs) -> {
                        Class<?> returnType = method.getReturnType();
                        if (method.getName().equals("isDevelopment") && returnType == boolean.class) {
                            return Boolean.TRUE;
                        }
                        if (returnType == boolean.class) {
                            return Boolean.FALSE;
                        }
                        if (returnType == int.class) {
                            return 0;
                        }
                        if (returnType == long.class) {
                            return 0L;
                        }
                        return null;
                    });
            for (Field field : baseClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && launcherInterface.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    field.set(null, stub);
                }
            }
        } catch (Throwable setupFailure) {
            System.out.println("[NET][SETUP ] launcher stub injection skipped: " + setupFailure);
        }
    }

    /**
     * 引导 Minecraft 注册表，避免代理类初始化触发 Bootstrap 检查失败。
     *
     * <p>JDK 动态代理类首次实例化时，其静态初始化块会以初始化模式加载接口
     * 签名引用的全部类型。服务端通道接口（PlayChannelHandler）签名引用了
     * ServerPlayer，其类初始化链（Entity → EntityDataSerializers →
     * FeatureElement → Registries → BuiltInRegistries）在构造注册表时调用
     * Bootstrap.checkBootstrapCalled()。裸 JVM 测试环境中无人调用过
     * Bootstrap.bootStrap()，该检查会抛 "Not bootstrapped" 使用例误报失败；
     * 真实游戏环境由游戏本体完成引导，不受影响。测试运行在 named classpath 上，
     * 可直接以 Mojang 映射名调用引导方法（发布环境命名约束不适用于测试代码）。
     * 引导失败不视为错误，由具体用例自行暴露问题。</p>
     */
    private static void bootstrapMinecraftRegistries() {
        try {
            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Throwable setupFailure) {
            System.out.println("[NET][SETUP ] minecraft bootstrap skipped: " + setupFailure);
            for (Throwable cause = setupFailure.getCause(); cause != null; cause = cause.getCause()) {
                System.out.println("[NET][SETUP ]   cause: " + cause);
            }
        }
    }

    /**
     * 判断当前 JVM 是否具备可用的 Fabric 启动器（原生或桩）。
     *
     * @return 启动器可用返回 true；无法确认时返回 true 以便用例自行暴露问题
     */
    private static boolean isLauncherAvailable() {
        try {
            Class<?> baseClass = Class.forName("net.fabricmc.loader.impl.launch.FabricLauncherBase");
            Method getLauncher = baseClass.getMethod("getLauncher");
            getLauncher.setAccessible(true);
            return getLauncher.invoke(null) != null;
        } catch (ReflectiveOperationException exception) {
            return true;
        }
    }

    /**
     * 验证服务端接收器（C2S）注册链路在当前 Fabric API 形态下可用。
     * 覆盖签名发现、Type 构造、codec 注册、receiver 注册整条链路，
     * 任一环节缺失会抛出 IllegalStateException 令用例失败。
     */
    private static void shouldRegisterServerReceiverOnCurrentApi() {
        if (!isLauncherAvailable()) {
            System.out.println("[NET][CASE ][SKIP] no Fabric launcher in bare JVM on this API generation");
            return;
        }
        FabricNetworkingCompat.registerServerReceiver(
                new ResourceLocation("todolist", "smoke_c2s"),
                (server, player, buf) -> {
                });
    }

    /**
     * 验证客户端接收器（S2C）注册链路在当前 Fabric API 形态下可用。
     */
    private static void shouldRegisterClientReceiverOnCurrentApi() {
        if (!isLauncherAvailable()) {
            System.out.println("[NET][CASE ][SKIP] no Fabric launcher in bare JVM on this API generation");
            return;
        }
        FabricNetworkingCompat.registerClientReceiver(
                new ResourceLocation("todolist", "smoke_s2c"),
                (client, buf) -> {
                });
    }

    /**
     * 验证 payload 形态下注册的 codec 可查询且非 null。
     * 历史回归：codec 代理对 cast 自引用方法返回 null，注册后
     * TypeAndCodec 持有 null codec，发送编码时才抛 NPE——本用例
     * 在注册期即可拦截该问题。旧通道形态（无 PayloadTypeRegistryImpl）跳过。
     */
    private static void shouldRegisterPayloadCodecWithNonNullCast() throws Exception {
        Class<?> registryImpl = findClass("net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl");
        if (registryImpl == null) {
            System.out.println("[NET][CASE ][SKIP] payload registry unavailable on this API generation");
            return;
        }

        Field playC2sField = registryImpl.getDeclaredField("PLAY_C2S");
        playC2sField.setAccessible(true);
        Object registry = playC2sField.get(null);

        Method getMethod = registry.getClass().getMethod("get", ResourceLocation.class);
        Object typeAndCodec = getMethod.invoke(registry, new ResourceLocation("todolist", "smoke_c2s"));
        if (typeAndCodec == null) {
            throw new IllegalStateException("payload codec was not registered for todolist:smoke_c2s");
        }

        Method codecAccessor = typeAndCodec.getClass().getMethod("codec");
        Object codec = codecAccessor.invoke(typeAndCodec);
        if (codec == null) {
            throw new IllegalStateException("registered codec is null: proxy cast self-reference is broken");
        }
    }

    /**
     * 验证兼容层反射调用可穿透包私有类的公开方法。
     * 历史回归：Fabric 的 ContextImpl 是包私有内部类，其 player()/client()
     * 方法公开但跨包反射调用抛 IllegalAccessException，导致 C2S 接收回调
     * 崩溃、项目创建等上行操作全部失效——本用例在注册期即可拦截该问题。
     */
    private static void shouldInvokePublicMethodOnPackagePrivateClass() throws Exception {
        Class<?> fixtureClass = Class.forName("com.todolist.compat.fixture.PackagePrivateContextFixture");
        java.lang.reflect.Constructor<?> constructor = fixtureClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();
        java.lang.reflect.Method playerMethod = fixtureClass.getMethod("player");
        Object result = FabricNetworkingCompat.invoke(playerMethod, instance);
        if (!"fixture-player".equals(result)) {
            throw new IllegalStateException("unexpected player accessor result: " + result);
        }
    }

    /**
     * 可抛受检异常的测试用例逻辑。
     */
    @FunctionalInterface
    private interface ThrowingRunnable {
        /**
         * 执行用例逻辑。
         *
         * @throws Exception 用例执行中的异常
         */
        void run() throws Exception;
    }

    /**
     * 执行单个测试用例，输出结果并统计失败数。
     *
     * @param name 用例名称
     * @param test 用例逻辑
     */
    private static void runTestCase(String name, ThrowingRunnable test) {
        System.out.println("[NET][CASE ][RUN ] " + name);
        log("[NET][CASE ][RUN ] " + name);
        long start = System.currentTimeMillis();
        try {
            test.run();
            System.out.println("[NET][CASE ][PASS] " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
            log("[NET][CASE ][PASS] " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
        } catch (Throwable throwable) {
            failed++;
            System.out.println("[NET][CASE ][FAIL] " + name);
            log("[NET][CASE ][FAIL] " + name);
            throwable.printStackTrace(System.out);
            StringBuilder chain = new StringBuilder("[NET][THROW] ").append(throwable);
            for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
                chain.append("\n[NET][CAUSE] ").append(cause);
            }
            log(chain.toString());
        }
    }

    /**
     * 按全限定名查找类，找不到时返回 null 而非抛异常。
     *
     * @param name 类全限定名
     * @return 类对象或 null
     */
    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
