package com.todolist.compat;

import io.netty.buffer.Unpooled;
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
 * Fabric 网络兼容工具，统一处理 1.20.1~1.20.4 的旧通道 API 与 1.20.5+ 的 payload API。
 */
public final class FabricNetworkingCompat {
    private static final String CLIENT_NETWORKING_CLASS = "net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking";
    private static final String SERVER_NETWORKING_CLASS = "net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking";
    private static final String PAYLOAD_INTERFACE_CLASS = "net.minecraft.network.protocol.common.custom.CustomPacketPayload";
    private static final String PAYLOAD_TYPE_CLASS = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type";
    private static final String PAYLOAD_TYPE_REGISTRY_CLASS = "net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry";
    private static final String STREAM_MEMBER_ENCODER_CLASS = "net.minecraft.network.codec.StreamMemberEncoder";
    private static final String STREAM_DECODER_CLASS = "net.minecraft.network.codec.StreamDecoder";
    private static final Map<String, Object> PAYLOAD_TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REGISTERED_C2S = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REGISTERED_S2C = new ConcurrentHashMap<>();

    /**
     * 禁止实例化兼容工具类。
     */
    private FabricNetworkingCompat() {
    }

    /**
     * 注册客户端接收器，兼容旧版通道处理与新版 payload 处理。
     *
     * @param channelId 通道 ID
     * @param handler 客户端处理器
     */
    public static void registerClientReceiver(ResourceLocation channelId, ClientBufferHandler handler) {
        Method legacyMethod = findMethod(
                ClientPlayNetworking.class,
                "registerGlobalReceiver",
                ResourceLocation.class,
                findClass(CLIENT_NETWORKING_CLASS + "$PlayChannelHandler")
        );
        if (legacyMethod != null) {
            invoke(
                    legacyMethod,
                    null,
                    channelId,
                    Proxy.newProxyInstance(
                            legacyMethod.getParameterTypes()[1].getClassLoader(),
                            new Class<?>[]{legacyMethod.getParameterTypes()[1]},
                            (proxy, method, args) -> {
                                if ("receive".equals(method.getName())) {
                                    handler.handle((Minecraft) args[0], (FriendlyByteBuf) args[2]);
                                }
                                return defaultObjectMethod(proxy, method, args, channelId.toString());
                            }
                    )
            );
            return;
        }

        Object payloadType = ensurePayloadTypeRegistered(channelId, true);
        Class<?> payloadHandlerClass = findClass(CLIENT_NETWORKING_CLASS + "$PlayPayloadHandler");
        Method payloadMethod = findMethod(ClientPlayNetworking.class, "registerGlobalReceiver", findClass(PAYLOAD_TYPE_CLASS), payloadHandlerClass);
        invoke(
                payloadMethod,
                null,
                payloadType,
                Proxy.newProxyInstance(
                        payloadHandlerClass.getClassLoader(),
                        new Class<?>[]{payloadHandlerClass},
                        (proxy, method, args) -> {
                            if ("receive".equals(method.getName())) {
                                Minecraft client = (Minecraft) invoke(findMethod(args[1].getClass(), "client"), args[1]);
                                handler.handle(client, toFriendlyByteBuf(args[0]));
                                return null;
                            }
                            return defaultObjectMethod(proxy, method, args, channelId.toString());
                        }
                )
        );
    }

    /**
     * 注册服务端接收器，兼容旧版通道处理与新版 payload 处理。
     *
     * @param channelId 通道 ID
     * @param handler 服务端处理器
     */
    public static void registerServerReceiver(ResourceLocation channelId, ServerBufferHandler handler) {
        Method legacyMethod = findMethod(
                ServerPlayNetworking.class,
                "registerGlobalReceiver",
                ResourceLocation.class,
                findClass(SERVER_NETWORKING_CLASS + "$PlayChannelHandler")
        );
        if (legacyMethod != null) {
            invoke(
                    legacyMethod,
                    null,
                    channelId,
                    Proxy.newProxyInstance(
                            legacyMethod.getParameterTypes()[1].getClassLoader(),
                            new Class<?>[]{legacyMethod.getParameterTypes()[1]},
                            (proxy, method, args) -> {
                                if ("receive".equals(method.getName())) {
                                    handler.handle((MinecraftServer) args[0], (ServerPlayer) args[1], (FriendlyByteBuf) args[3]);
                                }
                                return defaultObjectMethod(proxy, method, args, channelId.toString());
                            }
                    )
            );
            return;
        }

        Object payloadType = ensurePayloadTypeRegistered(channelId, false);
        Class<?> payloadHandlerClass = findClass(SERVER_NETWORKING_CLASS + "$PlayPayloadHandler");
        Method payloadMethod = findMethod(ServerPlayNetworking.class, "registerGlobalReceiver", findClass(PAYLOAD_TYPE_CLASS), payloadHandlerClass);
        invoke(
                payloadMethod,
                null,
                payloadType,
                Proxy.newProxyInstance(
                        payloadHandlerClass.getClassLoader(),
                        new Class<?>[]{payloadHandlerClass},
                        (proxy, method, args) -> {
                            if ("receive".equals(method.getName())) {
                                MinecraftServer server = (MinecraftServer) invoke(findMethod(args[1].getClass(), "server"), args[1]);
                                ServerPlayer player = (ServerPlayer) invoke(findMethod(args[1].getClass(), "player"), args[1]);
                                handler.handle(server, player, toFriendlyByteBuf(args[0]));
                                return null;
                            }
                            return defaultObjectMethod(proxy, method, args, channelId.toString());
                        }
                )
        );
    }

    /**
     * 向服务端发送数据，兼容旧版通道发送与新版 payload 发送。
     *
     * @param channelId 通道 ID
     * @param buf 网络缓冲区
     */
    public static void sendToServer(ResourceLocation channelId, FriendlyByteBuf buf) {
        Method legacyMethod = findMethod(ClientPlayNetworking.class, "send", ResourceLocation.class, FriendlyByteBuf.class);
        if (legacyMethod != null) {
            invoke(legacyMethod, null, channelId, buf);
            return;
        }

        Object payload = createPayloadProxy(ensurePayloadTypeRegistered(channelId, false), copyBytes(buf), channelId.toString());
        Method payloadMethod = findMethod(ClientPlayNetworking.class, "send", findClass(PAYLOAD_INTERFACE_CLASS));
        invoke(payloadMethod, null, payload);
    }

    /**
     * 向客户端发送数据，兼容旧版通道发送与新版 payload 发送。
     *
     * @param player 目标玩家
     * @param channelId 通道 ID
     * @param buf 网络缓冲区
     */
    public static void sendToClient(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf) {
        Method legacyMethod = findMethod(ServerPlayNetworking.class, "send", ServerPlayer.class, ResourceLocation.class, FriendlyByteBuf.class);
        if (legacyMethod != null) {
            invoke(legacyMethod, null, player, channelId, buf);
            return;
        }

        Object payload = createPayloadProxy(ensurePayloadTypeRegistered(channelId, true), copyBytes(buf), channelId.toString());
        Method payloadMethod = findMethod(ServerPlayNetworking.class, "send", ServerPlayer.class, findClass(PAYLOAD_INTERFACE_CLASS));
        invoke(payloadMethod, null, player, payload);
    }

    /**
     * 判断当前连接是否支持发送指定通道。
     *
     * @param channelId 通道 ID
     * @return 当前连接是否可发送
     */
    public static boolean canSendToServer(ResourceLocation channelId) {
        return ClientPlayNetworking.canSend(channelId);
    }

    /**
     * 确保指定方向的 payload 类型已注册，并返回对应的运行时 Type 对象。
     *
     * @param channelId 通道 ID
     * @param clientbound 是否为服务端下发客户端方向
     * @return 运行时 payload Type 对象
     */
    private static Object ensurePayloadTypeRegistered(ResourceLocation channelId, boolean clientbound) {
        Object type = PAYLOAD_TYPES.computeIfAbsent(channelId.toString(), FabricNetworkingCompat::createPayloadType);
        Map<String, Boolean> directionRegistry = clientbound ? REGISTERED_S2C : REGISTERED_C2S;
        directionRegistry.computeIfAbsent(channelId.toString(), key -> {
            registerPayloadType(type, clientbound);
            return Boolean.TRUE;
        });
        return type;
    }

    /**
     * 创建运行时 payload Type。
     *
     * @param channelKey 通道字符串
     * @return 新建的 payload Type
     */
    private static Object createPayloadType(String channelKey) {
        Class<?> payloadInterface = findClass(PAYLOAD_INTERFACE_CLASS);
        Method createType = findMethod(payloadInterface, "createType", String.class);
        return invoke(createType, null, channelKey);
    }

    /**
     * 在指定方向的 Fabric payload 注册表中登记原始缓冲区 payload。
     *
     * @param type 运行时 payload Type
     * @param clientbound 是否为服务端下发客户端方向
     */
    private static void registerPayloadType(Object type, boolean clientbound) {
        Class<?> payloadRegistryClass = findClass(PAYLOAD_TYPE_REGISTRY_CLASS);
        Object registry = invoke(findMethod(payloadRegistryClass, clientbound ? "playS2C" : "playC2S"), null);
        Object codec = createPayloadCodec(type);
        Method register = findMethod(registry.getClass(), "register", findClass(PAYLOAD_TYPE_CLASS), codec.getClass().getInterfaces()[0]);
        if (register == null) {
            register = registry.getClass().getMethods()[0];
            for (Method candidate : registry.getClass().getMethods()) {
                if ("register".equals(candidate.getName()) && candidate.getParameterCount() == 2) {
                    register = candidate;
                    break;
                }
            }
        }
        invoke(register, registry, type, codec);
    }

    /**
     * 创建原始字节 payload 的 StreamCodec。
     *
     * @param type 运行时 payload Type
     * @return 可供 PayloadTypeRegistry 注册的 codec 对象
     */
    private static Object createPayloadCodec(Object type) {
        Class<?> payloadInterface = findClass(PAYLOAD_INTERFACE_CLASS);
        Class<?> encoderClass = findClass(STREAM_MEMBER_ENCODER_CLASS);
        Class<?> decoderClass = findClass(STREAM_DECODER_CLASS);
        Method codecFactory = findMethod(payloadInterface, "codec", encoderClass, decoderClass);

        Object encoder = Proxy.newProxyInstance(
                encoderClass.getClassLoader(),
                new Class<?>[]{encoderClass},
                (proxy, method, args) -> {
                    if ("encode".equals(method.getName())) {
                        RawPayloadProxy payload = (RawPayloadProxy) Proxy.getInvocationHandler(args[0]);
                        ((FriendlyByteBuf) args[1]).writeBytes(payload.data);
                        return null;
                    }
                    return defaultObjectMethod(proxy, method, args, "raw-payload-encoder");
                }
        );

        Object decoder = Proxy.newProxyInstance(
                decoderClass.getClassLoader(),
                new Class<?>[]{decoderClass},
                (proxy, method, args) -> {
                    if ("decode".equals(method.getName())) {
                        FriendlyByteBuf buf = (FriendlyByteBuf) args[0];
                        byte[] data = new byte[buf.readableBytes()];
                        buf.readBytes(data);
                        return createPayloadProxy(type, data, "raw-payload-decoder");
                    }
                    return defaultObjectMethod(proxy, method, args, "raw-payload-decoder");
                }
        );

        return invoke(codecFactory, null, encoder, decoder);
    }

    /**
     * 创建运行时 payload 代理对象。
     *
     * @param type 运行时 payload Type
     * @param data 原始负载字节
     * @param debugName 调试名称
     * @return 实现 CustomPacketPayload 的代理对象
     */
    private static Object createPayloadProxy(Object type, byte[] data, String debugName) {
        Class<?> payloadInterface = findClass(PAYLOAD_INTERFACE_CLASS);
        return Proxy.newProxyInstance(
                payloadInterface.getClassLoader(),
                new Class<?>[]{payloadInterface},
                new RawPayloadProxy(type, data, debugName)
        );
    }

    /**
     * 将运行时 payload 代理还原为 FriendlyByteBuf。
     *
     * @param payload 运行时 payload 对象
     * @return 包含原始字节的 FriendlyByteBuf
     */
    private static FriendlyByteBuf toFriendlyByteBuf(Object payload) {
        RawPayloadProxy proxy = (RawPayloadProxy) Proxy.getInvocationHandler(payload);
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(proxy.data));
    }

    /**
     * 复制缓冲区当前可读区域的字节，而不改变读指针。
     *
     * @param buf 源缓冲区
     * @return 当前可读字节副本
     */
    private static byte[] copyBytes(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return data;
    }

    /**
     * 查找指定方法；不存在时返回 null。
     *
     * @param owner 方法所属类型
     * @param name 方法名
     * @param parameterTypes 参数类型
     * @return 匹配方法；不存在时返回 null
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
     * 按类名查找运行时类型；不存在时返回 null。
     *
     * @param className 目标类名
     * @return 目标类型；不存在时返回 null
     */
    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    /**
     * 统一执行反射调用，并在失败时抛出可读异常。
     *
     * @param method 目标方法
     * @param target 调用对象；静态方法时为 null
     * @param args 方法参数
     * @return 反射调用结果
     */
    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            throw new IllegalStateException("Required networking compatibility method is missing");
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke networking compatibility bridge", exception);
        }
    }

    /**
     * 为代理对象提供 Object 基础方法实现。
     *
     * @param proxy 当前代理
     * @param method 当前方法
     * @param args 方法参数
     * @param debugName 调试名称
     * @return Object 方法返回值
     */
    private static Object defaultObjectMethod(Object proxy, Method method, Object[] args, String debugName) {
        return switch (method.getName()) {
            case "toString" -> debugName;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
    }

    /**
     * 原始 payload 代理实现，保存运行时 Type 与负载字节。
     */
    private static final class RawPayloadProxy implements InvocationHandler {
        private final Object type;
        private final byte[] data;
        private final String debugName;

        /**
         * 创建原始 payload 代理。
         *
         * @param type 运行时 payload Type
         * @param data 原始负载字节
         * @param debugName 调试名称
         */
        private RawPayloadProxy(Object type, byte[] data, String debugName) {
            this.type = type;
            this.data = data;
            this.debugName = debugName;
        }

        /**
         * 处理 payload 代理方法调用。
         *
         * @param proxy 当前代理
         * @param method 当前方法
         * @param args 方法参数
         * @return 方法返回值
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("type".equals(method.getName())) {
                return this.type;
            }
            return defaultObjectMethod(proxy, method, args, this.debugName);
        }
    }

    /**
     * 客户端原始缓冲区处理器。
     */
    @FunctionalInterface
    public interface ClientBufferHandler {
        /**
         * 处理客户端收到的原始缓冲区。
         *
         * @param client 当前客户端
         * @param buf 网络缓冲区
         */
        void handle(Minecraft client, FriendlyByteBuf buf);
    }

    /**
     * 服务端原始缓冲区处理器。
     */
    @FunctionalInterface
    public interface ServerBufferHandler {
        /**
         * 处理服务端收到的原始缓冲区。
         *
         * @param server 当前服务端
         * @param player 当前玩家
         * @param buf 网络缓冲区
         */
        void handle(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf);
    }
}
