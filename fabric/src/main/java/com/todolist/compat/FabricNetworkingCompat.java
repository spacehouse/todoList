package com.todolist.compat;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric 多版本网络兼容工具。
 *
 * <p>适配三代 Fabric 网络形态：</p>
 * <ul>
 *   <li>1.20.1：通道 API（{@code registerGlobalReceiver(ResourceLocation, PlayChannelHandler)}）；</li>
 *   <li>1.20.2 ~ 1.20.4：Fabric API 3.x 仍保留通道 API，继续走旧分支；</li>
 *   <li>1.20.5 / 1.20.6：Fabric API 4.x 移除通道 API，必须走 payload API。</li>
 * </ul>
 *
 * <p>实现上必须遵守两条硬约束：</p>
 * <ol>
 *   <li>发布环境下 Minecraft 类与方法均为 intermediary 混淆名，因此严禁使用
 *       {@code Class.forName("net.minecraft....")} 之类的 Mojang 映射字符串。
 *       所有 Minecraft 运行时 {@link Class} 对象都从 Fabric API（发布环境命名稳定）
 *       的方法签名中动态发现。</li>
 *   <li>Minecraft 接口方法名（如 {@code type()}、{@code encode}、{@code decode}）发布后同样被混淆，
 *       因此代理回调内按“参数形状”分发（参数个数、返回类型、参数实例类型），不依赖方法名。</li>
 * </ol>
 */
public final class FabricNetworkingCompat {

    /** 客户端网络入口类名（Fabric API 自有类，发布环境命名稳定，可用于反射查找）。 */
    private static final String CLIENT_NETWORKING_CLASS = "net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking";
    /** 服务端网络入口类名（Fabric API 自有类，发布环境命名稳定，可用于反射查找）。 */
    private static final String SERVER_NETWORKING_CLASS = "net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking";
    /** payload 类型注册入口类名（Fabric API 自有类，1.20.5 起提供）。 */
    private static final String PAYLOAD_TYPE_REGISTRY_CLASS = "net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry";

    /** 各频道对应的 CustomPacketPayload.Type 实例缓存（key 为频道 id 字符串）。 */
    private static final Map<String, Object> PAYLOAD_TYPES = new ConcurrentHashMap<>();
    /** 已注册到服务端方向的频道缓存（C2S，服务端接收）。 */
    private static final Map<String, Boolean> REGISTERED_C2S = new ConcurrentHashMap<>();
    /** 已注册到客户端方向的频道缓存（S2C，客户端接收）。 */
    private static final Map<String, Boolean> REGISTERED_S2C = new ConcurrentHashMap<>();

    /** 发布环境下动态发现的 payload API 元数据（懒加载、线程安全）。 */
    private static volatile PayloadApi payloadApi;

    /** 禁止实例化工具类。 */
    private FabricNetworkingCompat() {
    }

    /**
     * 客户端缓冲区处理器：收到 S2C 数据包时回调。
     */
    public interface ClientBufferHandler {
        /**
         * 处理接收到的数据。
         *
         * @param client 客户端实例
         * @param buf    数据缓冲区
         */
        void handle(Minecraft client, FriendlyByteBuf buf);
    }

    /**
     * 服务端缓冲区处理器：收到 C2S 数据包时回调。
     */
    public interface ServerBufferHandler {
        /**
         * 处理接收到的数据。
         *
         * @param server 服务端实例
         * @param player 发送数据的玩家
         * @param buf    数据缓冲区
         */
        void handle(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf);
    }

    /**
     * 注册客户端接收器（S2C 方向）。
     *
     * @param channelId 频道标识
     * @param handler   数据处理器
     */
    public static void registerClientReceiver(ResourceLocation channelId, ClientBufferHandler handler) {
        Method legacyMethod = findMethod(ClientPlayNetworking.class, "registerGlobalReceiver", ResourceLocation.class,
                findClass(CLIENT_NETWORKING_CLASS + "$PlayChannelHandler"));
        if (legacyMethod != null) {
            Object proxy = Proxy.newProxyInstance(
                    legacyMethod.getParameterTypes()[1].getClassLoader(),
                    new Class<?>[] {legacyMethod.getParameterTypes()[1]},
                    (proxyInstance, method, args) -> {
                        if ("receive".equals(method.getName())) {
                            Minecraft client = (Minecraft) args[0];
                            FriendlyByteBuf buf = (FriendlyByteBuf) args[2];
                            handler.handle(client, buf);
                            return null;
                        }
                        return defaultObjectMethod(proxyInstance, method, args, channelId.toString());
                    });
            invoke(legacyMethod, null, channelId, proxy);
            return;
        }
        registerPayloadClientReceiver(channelId, handler);
    }

    /**
     * 注册服务端接收器（C2S 方向）。
     *
     * @param channelId 频道标识
     * @param handler   数据处理器
     */
    public static void registerServerReceiver(ResourceLocation channelId, ServerBufferHandler handler) {
        Method legacyMethod = findMethod(ServerPlayNetworking.class, "registerGlobalReceiver", ResourceLocation.class,
                findClass(SERVER_NETWORKING_CLASS + "$PlayChannelHandler"));
        if (legacyMethod != null) {
            Object proxy = Proxy.newProxyInstance(
                    legacyMethod.getParameterTypes()[1].getClassLoader(),
                    new Class<?>[] {legacyMethod.getParameterTypes()[1]},
                    (proxyInstance, method, args) -> {
                        if ("receive".equals(method.getName())) {
                            MinecraftServer server = (MinecraftServer) args[0];
                            ServerPlayer player = (ServerPlayer) args[1];
                            FriendlyByteBuf buf = (FriendlyByteBuf) args[3];
                            handler.handle(server, player, buf);
                            return null;
                        }
                        return defaultObjectMethod(proxyInstance, method, args, channelId.toString());
                    });
            invoke(legacyMethod, null, channelId, proxy);
            return;
        }
        registerPayloadServerReceiver(channelId, handler);
    }

    /**
     * 客户端向服务端发送数据（C2S）。
     *
     * @param channelId 频道标识
     * @param buf       数据缓冲区
     */
    public static void sendToServer(ResourceLocation channelId, FriendlyByteBuf buf) {
        Method legacyMethod = findMethod(ClientPlayNetworking.class, "send", ResourceLocation.class, FriendlyByteBuf.class);
        if (legacyMethod != null) {
            invoke(legacyMethod, null, channelId, buf);
            return;
        }
        PayloadApi api = resolvePayloadApi();
        Object payload = createPayloadProxy(api, ensurePayloadTypeRegistered(channelId, false), copyBytes(buf), channelId.toString());
        Method payloadMethod = findMethod(ClientPlayNetworking.class, "send", api.payloadInterface);
        invoke(payloadMethod, null, payload);
    }

    /**
     * 服务端向指定玩家发送数据（S2C）。
     *
     * @param player    目标玩家
     * @param channelId 频道标识
     * @param buf       数据缓冲区
     */
    public static void sendToClient(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf) {
        Method legacyMethod = findMethod(ServerPlayNetworking.class, "send", ServerPlayer.class, ResourceLocation.class, FriendlyByteBuf.class);
        if (legacyMethod != null) {
            invoke(legacyMethod, null, player, channelId, buf);
            return;
        }
        PayloadApi api = resolvePayloadApi();
        Object payload = createPayloadProxy(api, ensurePayloadTypeRegistered(channelId, true), copyBytes(buf), channelId.toString());
        Method payloadMethod = findMethod(ServerPlayNetworking.class, "send", ServerPlayer.class, api.payloadInterface);
        invoke(payloadMethod, null, player, payload);
    }

    /**
     * 判断能否向服务端发送指定频道的数据包。
     * 旧通道时代（1.20.4 及以前）直接查频道协商结果；
     * payload 时代（1.20.5 起）改用 canSend(Type)，优先命中本地 playC2S codec 注册表，
     * 不依赖握手通告的频道集合，避免协商时序导致误判为 false。
     *
     * @param channelId 频道标识
     * @return 是否可发送
     */
    public static boolean canSendToServer(ResourceLocation channelId) {
        boolean legacyChannelApi = findMethod(
                ClientPlayNetworking.class, "send", ResourceLocation.class, FriendlyByteBuf.class) != null;
        if (legacyChannelApi) {
            return ClientPlayNetworking.canSend(channelId);
        }
        try {
            PayloadApi api = resolvePayloadApi();
            Method byType = findMethod(ClientPlayNetworking.class, "canSend", api.typeClass);
            if (byType != null) {
                Object result = invoke(byType, null, ensurePayloadTypeRegistered(channelId, false));
                return result instanceof Boolean value && value;
            }
        } catch (RuntimeException ignored) {
            // payload API 不可用时回退到旧判定，保持 GUI 调用方安全
        }
        return ClientPlayNetworking.canSend(channelId);
    }

    /**
     * payload 分支：注册客户端 S2C 接收器。
     * 通过发现到的 Type 与 PlayPayloadHandler 代理接入 Fabric payload API。
     *
     * @param channelId 频道标识
     * @param handler   数据处理器
     */
    private static void registerPayloadClientReceiver(ResourceLocation channelId, ClientBufferHandler handler) {
        PayloadApi api = resolvePayloadApi();
        Object type = ensurePayloadTypeRegistered(channelId, true);
        Method payloadMethod = findRegisterReceiverMethod(ClientPlayNetworking.class, api.clientHandlerClass);
        Object proxy = Proxy.newProxyInstance(
                api.clientHandlerClass.getClassLoader(),
                new Class<?>[] {api.clientHandlerClass},
                (proxyInstance, method, args) -> {
                    if ("receive".equals(method.getName())) {
                        Minecraft client = (Minecraft) invoke(findMethod(args[1].getClass(), "client"), args[1]);
                        handler.handle(client, toFriendlyByteBuf(args[0]));
                        return null;
                    }
                    return defaultObjectMethod(proxyInstance, method, args, channelId.toString());
                });
        invoke(payloadMethod, null, type, proxy);
    }

    /**
     * payload 分支：注册服务端 C2S 接收器。
     * 服务端实例不再从 Context 反射获取（新版本 Context 已移除 server 访问器），改由玩家引用取得。
     *
     * @param channelId 频道标识
     * @param handler   数据处理器
     */
    private static void registerPayloadServerReceiver(ResourceLocation channelId, ServerBufferHandler handler) {
        PayloadApi api = resolvePayloadApi();
        Object type = ensurePayloadTypeRegistered(channelId, false);
        Method payloadMethod = findRegisterReceiverMethod(ServerPlayNetworking.class, api.serverHandlerClass);
        Object proxy = Proxy.newProxyInstance(
                api.serverHandlerClass.getClassLoader(),
                new Class<?>[] {api.serverHandlerClass},
                (proxyInstance, method, args) -> {
                    if ("receive".equals(method.getName())) {
                        ServerPlayer player = (ServerPlayer) invoke(findMethod(args[1].getClass(), "player"), args[1]);
                        MinecraftServer server = player.getServer();
                        handler.handle(server, player, toFriendlyByteBuf(args[0]));
                        return null;
                    }
                    return defaultObjectMethod(proxyInstance, method, args, channelId.toString());
                });
        invoke(payloadMethod, null, type, proxy);
    }

    /**
     * 确保频道已注册 payload 编解码器并返回其 Type 实例。
     *
     * @param channelId   频道标识
     * @param clientbound true 表示 S2C（客户端接收）方向，false 表示 C2S（服务端接收）方向
     * @return CustomPacketPayload.Type 实例
     */
    private static Object ensurePayloadTypeRegistered(ResourceLocation channelId, boolean clientbound) {
        String channelKey = channelId.toString();
        Object type = PAYLOAD_TYPES.computeIfAbsent(channelKey, key -> createPayloadType(resolvePayloadApi(), key));
        Map<String, Boolean> direction = clientbound ? REGISTERED_S2C : REGISTERED_C2S;
        direction.computeIfAbsent(channelKey, key -> {
            registerPayloadCodec(resolvePayloadApi(), type, clientbound);
            return Boolean.TRUE;
        });
        return type;
    }

    /**
     * 解析并缓存发布环境下的 payload API 元数据。
     * 全部信息来自 Fabric API 自身签名，避免依赖被混淆的 Minecraft 名称。
     *
     * @return payload API 元数据
     */
    private static PayloadApi resolvePayloadApi() {
        PayloadApi resolved = payloadApi;
        if (resolved != null) {
            return resolved;
        }
        synchronized (FabricNetworkingCompat.class) {
            if (payloadApi == null) {
                payloadApi = discoverPayloadApi();
            }
            return payloadApi;
        }
    }

    /**
     * 从 Fabric API 签名中发现 payload 相关运行时类：
     * <ul>
     *   <li>payload 接口：来自 {@code PlayPayloadHandler#receive} 的首参数；</li>
     *   <li>Type 类：来自 {@code registerGlobalReceiver(Type, PlayPayloadHandler)} 的首参数；</li>
     *   <li>StreamCodec 类：来自 {@code PayloadTypeRegistry#register(Type, StreamCodec)} 的第二参数。</li>
     * </ul>
     *
     * @return payload API 元数据
     */
    private static PayloadApi discoverPayloadApi() {
        Class<?> clientHandlerClass = findClass(CLIENT_NETWORKING_CLASS + "$PlayPayloadHandler");
        Class<?> serverHandlerClass = findClass(SERVER_NETWORKING_CLASS + "$PlayPayloadHandler");
        Class<?> anchorHandlerClass = clientHandlerClass != null ? clientHandlerClass : serverHandlerClass;
        Class<?> ownerClass = clientHandlerClass != null ? ClientPlayNetworking.class : ServerPlayNetworking.class;
        if (anchorHandlerClass == null) {
            throw new IllegalStateException("Fabric payload networking handler interface is unavailable");
        }

        Class<?> typeClass = null;
        for (Method candidate : ownerClass.getMethods()) {
            if ("registerGlobalReceiver".equals(candidate.getName())
                    && candidate.getParameterCount() == 2
                    && candidate.getParameterTypes()[1] == anchorHandlerClass) {
                typeClass = candidate.getParameterTypes()[0];
                break;
            }
        }
        Method receiveMethod = null;
        for (Method candidate : anchorHandlerClass.getMethods()) {
            if ("receive".equals(candidate.getName()) && candidate.getParameterCount() == 2) {
                receiveMethod = candidate;
                break;
            }
        }
        if (typeClass == null) {
            throw new IllegalStateException("Fabric payload receiver registration method is unavailable");
        }
        if (receiveMethod == null) {
            throw new IllegalStateException("Fabric payload handler receive method is unavailable");
        }
        Class<?> payloadInterface = receiveMethod.getParameterTypes()[0];

        Class<?> registryClass = findClass(PAYLOAD_TYPE_REGISTRY_CLASS);
        if (registryClass == null) {
            throw new IllegalStateException("Fabric payload type registry is unavailable");
        }
        Class<?> codecClass = null;
        for (Method candidate : registryClass.getMethods()) {
            if ("register".equals(candidate.getName())
                    && candidate.getParameterCount() == 2
                    && candidate.getParameterTypes()[0] == typeClass) {
                codecClass = candidate.getParameterTypes()[1];
                break;
            }
        }
        if (codecClass == null) {
            throw new IllegalStateException("Fabric payload codec interface is unavailable");
        }
        return new PayloadApi(payloadInterface, typeClass, codecClass, registryClass, clientHandlerClass, serverHandlerClass);
    }

    /**
     * 创建频道的 payload Type 实例。
     * Type 为 record，其构造参数在 1.20.2 系为 String、1.20.5 系为 ResourceLocation，
     * 此处按构造参数类型自适应，不依赖混淆方法名 createType。
     *
     * @param api        payload API 元数据
     * @param channelKey 频道 id 字符串
     * @return Type 实例
     */
    private static Object createPayloadType(PayloadApi api, String channelKey) {
        for (Constructor<?> constructor : api.typeClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameterType = constructor.getParameterTypes()[0];
            try {
                constructor.setAccessible(true);
                if (parameterType == String.class) {
                    return constructor.newInstance(channelKey);
                }
                if (parameterType == ResourceLocation.class) {
                    return constructor.newInstance(new ResourceLocation(channelKey));
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to create payload type for channel " + channelKey, exception);
            }
        }
        throw new IllegalStateException("No usable payload type constructor for channel " + channelKey);
    }

    /**
     * 将频道的原始字节编解码器注册进对应方向的 payload 类型注册表。
     *
     * @param api         payload API 元数据
     * @param type        频道 Type 实例
     * @param clientbound true 表示 S2C 方向，false 表示 C2S 方向
     */
    private static void registerPayloadCodec(PayloadApi api, Object type, boolean clientbound) {
        Method factoryMethod = findMethod(api.registryClass, clientbound ? "playS2C" : "playC2S");
        Object registry = invoke(factoryMethod, null);
        Method registerMethod = findMethod(api.registryClass, "register", api.typeClass, api.codecClass);
        if (registerMethod == null) {
            for (Method candidate : api.registryClass.getMethods()) {
                if ("register".equals(candidate.getName()) && candidate.getParameterCount() == 2) {
                    registerMethod = candidate;
                    break;
                }
            }
        }
        Object codec = Proxy.newProxyInstance(
                api.codecClass.getClassLoader(),
                new Class<?>[] {api.codecClass},
                new RawCodecProxy(api, type));
        invoke(registerMethod, registry, type, codec);
    }

    /**
     * 在网络入口类上查找 payload 形态的接收器注册方法。
     * 以处理器接口类型作为锚点区分 payload 分支与旧通道分支。
     *
     * @param ownerClass    网络入口类
     * @param handlerClass  PlayPayloadHandler 运行时类
     * @return 注册方法
     */
    private static Method findRegisterReceiverMethod(Class<?> ownerClass, Class<?> handlerClass) {
        if (handlerClass == null) {
            return null;
        }
        for (Method candidate : ownerClass.getMethods()) {
            if ("registerGlobalReceiver".equals(candidate.getName())
                    && candidate.getParameterCount() == 2
                    && candidate.getParameterTypes()[1] == handlerClass) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 创建携带原始字节的 payload 代理实例。
     * 代理按形状分发：无参且非 void 的方法视为 type() 访问器，返回缓存的 Type。
     *
     * @param api       payload API 元数据
     * @param type      频道 Type 实例
     * @param data      原始字节数据
     * @param debugName 调试名称
     * @return payload 代理实例
     */
    private static Object createPayloadProxy(PayloadApi api, Object type, byte[] data, String debugName) {
        return Proxy.newProxyInstance(
                api.payloadInterface.getClassLoader(),
                new Class<?>[] {api.payloadInterface},
                new RawPayloadProxy(type, data, debugName));
    }

    /**
     * 从 payload 代理实例中提取原始字节并包装为缓冲区。
     *
     * @param payload payload 代理实例
     * @return 数据缓冲区
     */
    private static FriendlyByteBuf toFriendlyByteBuf(Object payload) {
        RawPayloadProxy rawPayload = (RawPayloadProxy) Proxy.getInvocationHandler(payload);
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(rawPayload.data));
    }

    /**
     * 复制缓冲区可读字节，避免后续缓冲区释放影响数据。
     *
     * @param buf 原缓冲区
     * @return 字节数组副本
     */
    private static byte[] copyBytes(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    /**
     * 判断实例是否为携带原始字节的 payload 代理。
     *
     * @param candidate 待判断实例
     * @return 是否为 payload 代理
     */
    private static boolean isRawPayload(Object candidate) {
        return candidate instanceof Proxy
                && Proxy.getInvocationHandler(candidate) instanceof RawPayloadProxy;
    }

    /**
     * 调用反射方法，目标方法缺失时抛出明确异常。
     * Fabric 的 ContextImpl 等实现类是包私有内部类，其公开方法跨包反射调用
     * 会被 Java 访问控制拒绝（IllegalAccessException），因此统一放开可访问性。
     *
     * @param method 目标方法
     * @param target 调用目标（静态方法传 null）
     * @param args   调用参数
     * @return 调用结果
     */
    static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            throw new IllegalStateException("Required networking compatibility method is missing");
        }
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke networking compatibility method: " + method, exception);
        }
    }

    /**
     * 按名称与参数类型查找公开方法，找不到返回 null。
     *
     * @param owner        目标类
     * @param name         方法名
     * @param parameterTypes 参数类型
     * @return 方法，未找到时为 null
     */
    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    /**
     * 按名称查找类，找不到返回 null（不触发类初始化）。
     *
     * @param className 类全限定名
     * @return 类对象，未找到时为 null
     */
    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className, false, FabricNetworkingCompat.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    /**
     * 处理代理实例上的 Object 基础方法，其余方法返回 null。
     *
     * @param proxy     代理实例
     * @param method    被调用的方法
     * @param args      调用参数
     * @param debugName 调试名称
     * @return 基础方法结果，其余为 null
     */
    private static Object defaultObjectMethod(Object proxy, Method method, Object[] args, String debugName) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> debugName;
            default -> null;
        };
    }

    /**
     * 发布环境发现的 payload API 元数据。
     * 仅保存运行时 Class 对象，不保存任何 Mojang 映射名称。
     */
    private static final class PayloadApi {
        /** CustomPacketPayload 运行时接口。 */
        private final Class<?> payloadInterface;
        /** CustomPacketPayload.Type 运行时类。 */
        private final Class<?> typeClass;
        /** StreamCodec 运行时接口。 */
        private final Class<?> codecClass;
        /** Fabric PayloadTypeRegistry 接口。 */
        private final Class<?> registryClass;
        /** 客户端 PlayPayloadHandler 接口（可能不存在）。 */
        private final Class<?> clientHandlerClass;
        /** 服务端 PlayPayloadHandler 接口（可能不存在）。 */
        private final Class<?> serverHandlerClass;

        /**
         * 构造 payload API 元数据。
         *
         * @param payloadInterface   CustomPacketPayload 运行时接口
         * @param typeClass          Type 运行时类
         * @param codecClass         StreamCodec 运行时接口
         * @param registryClass      Fabric PayloadTypeRegistry 接口
         * @param clientHandlerClass 客户端处理器接口
         * @param serverHandlerClass 服务端处理器接口
         */
        private PayloadApi(Class<?> payloadInterface, Class<?> typeClass, Class<?> codecClass,
                           Class<?> registryClass, Class<?> clientHandlerClass, Class<?> serverHandlerClass) {
            this.payloadInterface = payloadInterface;
            this.typeClass = typeClass;
            this.codecClass = codecClass;
            this.registryClass = registryClass;
            this.clientHandlerClass = clientHandlerClass;
            this.serverHandlerClass = serverHandlerClass;
        }
    }

    /**
     * 原始字节 payload 代理处理器。
     * 发布环境方法名被混淆，因此按形状分发：唯一的无参非 void 方法即 Type 访问器。
     */
    private static final class RawPayloadProxy implements InvocationHandler {
        /** 频道 Type 实例。 */
        private final Object type;
        /** 原始字节数据。 */
        private final byte[] data;
        /** 调试名称。 */
        private final String debugName;

        /**
         * 构造 payload 代理处理器。
         *
         * @param type      频道 Type 实例
         * @param data      原始字节数据
         * @param debugName 调试名称
         */
        private RawPayloadProxy(Object type, byte[] data, String debugName) {
            this.type = type;
            this.data = data;
            this.debugName = debugName;
        }

        /**
         * 代理分发：无参非 void 方法返回 Type，其余走基础方法默认值。
         *
         * @param proxy  代理实例
         * @param method 被调用的方法
         * @param args   调用参数
         * @return 分发结果
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class
                    && method.getDeclaringClass() != Object.class) {
                return this.type;
            }
            return defaultObjectMethod(proxy, method, args, this.debugName);
        }
    }

    /**
     * 原始字节编解码代理处理器。
     * encode / decode 在发布环境均为混淆名，按参数形状分发：
     * 两参 void 方法视为 encode（按实例类型区分缓冲区与 payload），
     * 单参非 void 方法视为 decode。
     */
    private static final class RawCodecProxy implements InvocationHandler {
        /** payload API 元数据。 */
        private final PayloadApi api;
        /** 频道 Type 实例。 */
        private final Object type;

        /**
         * 构造编解码代理处理器。
         *
         * @param api  payload API 元数据
         * @param type 频道 Type 实例
         */
        private RawCodecProxy(PayloadApi api, Object type) {
            this.api = api;
            this.type = type;
        }

        /**
         * 代理分发：encode 写出 payload 原始字节，decode 读出全部字节生成 payload 代理，
         * cast 等自引用方法（0 参且返回 codec 类型，语义为返回自身）返回代理自身。
         *
         * @param proxy  代理实例
         * @param method 被调用的方法
         * @param args   调用参数
         * @return 分发结果
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getParameterCount() == 0 && method.getReturnType() != void.class
                    && method.getReturnType().isInstance(proxy)) {
                return proxy;
            }
            if (method.getParameterCount() == 2 && method.getReturnType() == void.class) {
                FriendlyByteBuf buf = null;
                byte[] data = null;
                for (Object arg : args) {
                    if (arg instanceof FriendlyByteBuf candidateBuf) {
                        buf = candidateBuf;
                    } else if (isRawPayload(arg)) {
                        data = ((RawPayloadProxy) Proxy.getInvocationHandler(arg)).data;
                    }
                }
                if (buf != null && data != null) {
                    buf.writeBytes(data);
                }
                return null;
            }
            if (method.getParameterCount() == 1 && method.getReturnType() != void.class
                    && method.getDeclaringClass() != Object.class) {
                FriendlyByteBuf buf = (FriendlyByteBuf) args[0];
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return createPayloadProxy(this.api, this.type, data, "raw-payload-decode");
            }
            return defaultObjectMethod(proxy, method, args, "raw-codec");
        }
    }
}
