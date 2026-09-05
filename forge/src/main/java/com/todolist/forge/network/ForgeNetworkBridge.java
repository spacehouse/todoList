package com.todolist.forge.network;

import com.todolist.TodoConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Forge 网络桥接类。
 * 负责处理基于 SimpleChannel 的网络通信，封装了数据包的发送与接收逻辑。
 * 通过反射与 Forge 网络系统交互，以减少直接依赖。
 */
public final class ForgeNetworkBridge {
    @FunctionalInterface
    public interface ServerReceiver {
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, ForgePacketSender responseSender);
    }

    @FunctionalInterface
    public interface ClientReceiver {
        void receive(Minecraft client, Object handler, FriendlyByteBuf buf, ForgePacketSender responseSender);
    }

    @FunctionalInterface
    public interface JoinListener {
        void onJoin(ServerPlayer player, ForgePacketSender sender, MinecraftServer server);
    }

    private static final ForgePacketSender NO_OP_SENDER = (channelId, buf) -> { };
    private static final String PROTOCOL = "1";
    private static final int DISPATCH_ID = 0;
    private static final Map<String, ServerReceiver> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<String, ClientReceiver> CLIENT_RECEIVERS = new ConcurrentHashMap<>();
    private static final List<JoinListener> JOIN_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized;
    private static Object simpleChannel;

    private ForgeNetworkBridge() {
    }

    /**
     * 初始化网络桥接。
     * 创建 SimpleChannel 并注册分发消息。
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        simpleChannel = createChannel();
        registerDispatchMessages(simpleChannel);
        registerPlayerLoginHook();
    }

    /**
     * 注册服务端接收器。
     */
    public static void registerServerReceiver(ResourceLocation channelId, ServerReceiver receiver) {
        init();
        SERVER_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * 注册客户端接收器。
     */
    public static void registerClientReceiver(ResourceLocation channelId, ClientReceiver receiver) {
        init();
        CLIENT_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * 注册玩家加入监听器。
     */
    public static void registerJoinListener(JoinListener listener) {
        init();
        JOIN_LISTENERS.add(listener);
    }

    /**
     * 检查是否可以发送消息到指定通道（即对方是否注册了该通道）。
     */
    public static boolean canSend(ResourceLocation channelId) {
        init();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return false;
        }
        if (client.isLocalServer()) {
            var server = client.getSingleplayerServer();
            return server != null && server.isPublished();
        }
        Object connection = resolveNettyConnection(client.getConnection());
        if (connection == null) {
            return false;
        }
        try {
            Object remotePresent = invoke(simpleChannel, "isRemotePresent", new Class<?>[]{connection.getClass()}, connection);
            if (remotePresent instanceof Boolean available) {
                return available;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 发送数据包到服务端。
     * 47.x 使用 sendToServer(MSG)；48.x 移除该方法，改为 send(MSG, Connection)。
     */
    public static void sendToServer(ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        Object payload = new DispatchPacket(channelId.toString(), toByteArray(buf));
        try {
            Method legacySend = findMethodOrNull(simpleChannel.getClass(), "sendToServer", 1);
            if (legacySend != null) {
                legacySend.setAccessible(true);
                legacySend.invoke(simpleChannel, payload);
                return;
            }
            Minecraft client = Minecraft.getInstance();
            Object connection = client == null ? null : resolveNettyConnection(client.getConnection());
            if (connection == null) {
                return;
            }
            Method sendMethod = simpleChannel.getClass().getMethod("send", Object.class, net.minecraft.network.Connection.class);
            sendMethod.setAccessible(true);
            sendMethod.invoke(simpleChannel, payload, connection);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send packet to server", e);
        }
    }

    /**
     * 发送数据包到客户端（指定玩家）。
     * 47.x 为 send(PacketTarget, MSG)；48.x 参数顺序反转为 send(MSG, PacketTarget)。
     */
    public static void sendToPlayer(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        try {
            Object packetTarget = createPlayerTarget(player);
            Object payload = new DispatchPacket(channelId.toString(), toByteArray(buf));
            Class<?> targetClass = packetTarget.getClass();
            Method legacySend = null;
            try {
                legacySend = simpleChannel.getClass().getMethod("send", targetClass, Object.class);
            } catch (NoSuchMethodException ignored) {
                // 48.x 无 (PacketTarget, MSG) 形态，走新参数顺序
            }
            if (legacySend != null) {
                legacySend.setAccessible(true);
                legacySend.invoke(simpleChannel, packetTarget, payload);
                return;
            }
            Method modernSend = simpleChannel.getClass().getMethod("send", Object.class, targetClass);
            modernSend.setAccessible(true);
            modernSend.invoke(simpleChannel, payload, packetTarget);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send packet to player", e);
        }
    }

    /**
     * 创建 Forge SimpleChannel。
     * Forge 47.x（1.20.1 及以前）使用 NetworkRegistry.newSimpleChannel 工厂方法；
     * Forge 48.x（1.20.2 起）网络系统重构，改为 ChannelBuilder 链式构建，
     * 协议版本从字符串谓词协商改为 int + VersionTest。
     */
    private static Object createChannel() {
        try {
            Class<?> registryClass = Class.forName("net.minecraftforge.network.NetworkRegistry");
            Method legacyFactory = findMethodOrNull(registryClass, "newSimpleChannel", 4);
            if (legacyFactory != null) {
                return legacyFactory.invoke(
                        null,
                        new ResourceLocation(TodoConstants.MOD_ID, "bridge"),
                        (Supplier<String>) () -> PROTOCOL,
                        (java.util.function.Predicate<String>) ForgeNetworkBridge::acceptRemoteVersion,
                        (java.util.function.Predicate<String>) ForgeNetworkBridge::acceptRemoteVersion
                );
            }
            return createChannelViaBuilder();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Forge channel", e);
        }
    }

    /**
     * 通过 Forge 48.x 的 ChannelBuilder 创建 SimpleChannel。
     * 版本协商策略与旧版一致：接受任意远端版本（恒真 VersionTest）。
     * 注意不能用 ACCEPT_MISSING——它仅接受对端未声明通道（MISSING）的情况，
     * 而本 mod 双端必装、通道状态为 PRESENT，会导致握手时版本被拒而断连。
     *
     * @return 构建完成的 SimpleChannel 实例
     * @throws Exception 反射构建链路失败
     */
    private static Object createChannelViaBuilder() throws Exception {
        Class<?> builderClass = Class.forName("net.minecraftforge.network.ChannelBuilder");
        Class<?> versionTestClass = Class.forName("net.minecraftforge.network.Channel$VersionTest");
        Object acceptAll = java.lang.reflect.Proxy.newProxyInstance(
                versionTestClass.getClassLoader(),
                new Class<?>[]{versionTestClass},
                (proxy, method, methodArgs) -> {
                    if ("accepts".equals(method.getName()) && method.getReturnType() == boolean.class) {
                        return Boolean.TRUE;
                    }
                    throw new UnsupportedOperationException(
                            "Unexpected VersionTest method: " + method.getName());
                });
        Object builder = builderClass.getMethod("named", ResourceLocation.class)
                .invoke(null, new ResourceLocation(TodoConstants.MOD_ID, "bridge"));
        builder = builderClass.getMethod("networkProtocolVersion", int.class).invoke(builder, 1);
        builder = builderClass.getMethod("clientAcceptedVersions", versionTestClass).invoke(builder, acceptAll);
        builder = builderClass.getMethod("serverAcceptedVersions", versionTestClass).invoke(builder, acceptAll);
        return builderClass.getMethod("simpleChannel").invoke(builder);
    }

    /**
     * 注册分发消息。
     * 47.x 使用 registerMessage(int, Class, encoder, decoder, handler)；
     * 48.x 改为 messageBuilder(Class, int) 链式注册，处理回调由 Supplier&lt;Context&gt;
     * 变为直接传 Context，统一在 dispatcher 内按实际类型归一化。
     */
    private static void registerDispatchMessages(Object channel) {
        BiConsumer<DispatchPacket, Object> encoder = (packet, bufferObj) -> {
            FriendlyByteBuf buffer = (FriendlyByteBuf) bufferObj;
            buffer.writeUtf(packet.channelId());
            buffer.writeByteArray(packet.payload());
        };
        Function<Object, DispatchPacket> decoder = bufferObj -> {
            FriendlyByteBuf buffer = (FriendlyByteBuf) bufferObj;
            String channelId = buffer.readUtf();
            byte[] payload = buffer.readByteArray();
            return new DispatchPacket(channelId, payload);
        };
        BiConsumer<DispatchPacket, Object> dispatcher = (packet, contextArg) -> {
            Object context = contextArg instanceof Supplier<?> supplier ? supplier.get() : contextArg;
            Runnable work = () -> dispatchPacketBySide(packet, context);
            invoke(context, "enqueueWork", new Class<?>[]{Runnable.class}, work);
            invoke(context, "setPacketHandled", new Class<?>[]{boolean.class}, true);
        };
        try {
            Method legacyRegister = findMethodOrNull(channel.getClass(), "registerMessage", 5);
            if (legacyRegister != null) {
                legacyRegister.setAccessible(true);
                legacyRegister.invoke(channel, DISPATCH_ID, DispatchPacket.class, encoder, decoder, dispatcher);
                return;
            }
            Method messageBuilder = channel.getClass().getMethod("messageBuilder", Class.class, int.class);
            messageBuilder.setAccessible(true);
            Object builder = messageBuilder.invoke(channel, DispatchPacket.class, DISPATCH_ID);
            Method encoderMethod = builder.getClass().getMethod("encoder", BiConsumer.class);
            Method decoderMethod = builder.getClass().getMethod("decoder", Function.class);
            Method consumerMethod = builder.getClass().getMethod("consumerNetworkThread", BiConsumer.class);
            Method addMethod = builder.getClass().getMethod("add");
            encoderMethod.setAccessible(true);
            decoderMethod.setAccessible(true);
            consumerMethod.setAccessible(true);
            addMethod.setAccessible(true);
            encoderMethod.invoke(builder, encoder);
            decoderMethod.invoke(builder, decoder);
            consumerMethod.invoke(builder, dispatcher);
            addMethod.invoke(builder);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register dispatch messages", e);
        }
    }

    private static void dispatchPacketBySide(DispatchPacket packet, Object context) {
        Object senderObj = null;
        try {
            senderObj = invoke(context, "getSender", new Class<?>[]{});
        } catch (Exception ignored) {
        }
        if (senderObj instanceof ServerPlayer) {
            dispatchServerPacket(packet, context);
            return;
        }
        dispatchClientPacket(packet);
    }

    private static void dispatchServerPacket(DispatchPacket packet, Object context) {
        Object senderObj = invoke(context, "getSender", new Class<?>[]{});
        if (!(senderObj instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerGamePacketListenerImpl handler = getServerNetworkHandler(player);
        ServerReceiver receiver = SERVER_RECEIVERS.get(packet.channelId());
        if (receiver == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.payload()));
        receiver.receive(server, player, handler, buf, NO_OP_SENDER);
    }

    private static void dispatchClientPacket(DispatchPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        ClientReceiver receiver = CLIENT_RECEIVERS.get(packet.channelId());
        if (receiver == null) {
            return;
        }
        Object connectionSnapshot = client.getConnection();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.payload()));
        client.execute(() -> receiver.receive(client, connectionSnapshot, buf, NO_OP_SENDER));
    }

    private static void registerPlayerLoginHook() {
        try {
            Object eventBus = MinecraftForge.EVENT_BUS;
            Class<?> loginEventClass = Class.forName("net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent");
            Method addListener = eventBus.getClass().getMethod("addListener", EventPriority.class, boolean.class, Class.class, java.util.function.Consumer.class);
            java.util.function.Consumer<Object> consumer = event -> {
                Object playerObj = invoke(event, "getEntity", new Class<?>[]{});
                if (!(playerObj instanceof ServerPlayer player)) {
                    return;
                }
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return;
                }
                for (JoinListener listener : JOIN_LISTENERS) {
                    listener.onJoin(player, NO_OP_SENDER, server);
                }
            };
            addListener.invoke(eventBus, EventPriority.NORMAL, false, loginEventClass, consumer);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register Forge player login hook", e);
        }
    }

    private static boolean acceptRemoteVersion(String version) {
        return true;
    }

    /**
     * 创建发往指定玩家的 PacketTarget。
     * 47.x 的 PacketDistributor.with 接收 Supplier；48.x 改为直接接收目标对象。
     */
    private static Object createPlayerTarget(ServerPlayer player) {
        try {
            Class<?> distributorClass = Class.forName("net.minecraftforge.network.PacketDistributor");
            Field playerField = distributorClass.getField("PLAYER");
            Object distributor = playerField.get(null);
            Method withMethod;
            Object targetArgument;
            try {
                withMethod = distributor.getClass().getMethod("with", Supplier.class);
                targetArgument = (Supplier<Object>) () -> player;
            } catch (NoSuchMethodException modernShape) {
                withMethod = distributor.getClass().getMethod("with", Object.class);
                targetArgument = player;
            }
            withMethod.setAccessible(true);
            return withMethod.invoke(distributor, targetArgument);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create packet target", e);
        }
    }

    /**
     * 从客户端监听器解析 Netty 连接。
     * 优先调用 getConnection 方法；1.20.2 起该方法不存在且 connection 字段
     * 上移到了父类 ClientCommonPacketListenerImpl，getDeclaredFields 不含继承字段，
     * 因此字段扫描必须沿类层级向上遍历，否则解析为 null 导致发送被静默丢弃。
     *
     * @param clientPacketListener 客户端监听器实例
     * @return Netty Connection 或 null
     */
    private static Object resolveNettyConnection(Object clientPacketListener) {
        if (clientPacketListener == null) {
            return null;
        }
        try {
            return invoke(clientPacketListener, "getConnection", new Class<?>[]{});
        } catch (Exception ignored) {
        }
        try {
            Class<?> connectionType = Class.forName("net.minecraft.network.Connection");
            for (Class<?> current = clientPacketListener.getClass(); current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (connectionType.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return field.get(clientPacketListener);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 获取服务端玩家的网络处理器。
     * Mojmap 映射下 ServerPlayer 的处理器字段名为 connection（public），
     * 兼容保留 networkHandler 名称探测以覆盖其他映射形态。
     *
     * @param player 服务端玩家
     * @return 网络处理器，无法解析时返回 null
     */
    private static ServerGamePacketListenerImpl getServerNetworkHandler(ServerPlayer player) {
        for (String fieldName : new String[]{"connection", "networkHandler"}) {
            try {
                Field field = player.getClass().getField(fieldName);
                Object value = field.get(player);
                if (value instanceof ServerGamePacketListenerImpl handler) {
                    return handler;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static byte[] toByteArray(FriendlyByteBuf source) {
        FriendlyByteBuf copy = new FriendlyByteBuf(source.copy());
        byte[] bytes = new byte[copy.readableBytes()];
        copy.readBytes(bytes);
        return bytes;
    }

    /**
     * 按名称与参数个数查找方法，找不到时返回 null（与 findMethod 的抛异常版本相对）。
     *
     * @param type       目标类
     * @param name       方法名
     * @param paramCount 参数个数
     * @return 匹配的方法，未找到返回 null
     */
    private static Method findMethodOrNull(Class<?> type, String name, int paramCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, int paramCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        throw new IllegalStateException("Method not found: " + type.getName() + "#" + name + "/" + paramCount);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            return method.invoke(target, args);
        } catch (NoSuchMethodException ex) {
            Method fallback = findMethod(target.getClass(), name, parameterTypes.length);
            try {
                return fallback.invoke(target, args);
            } catch (Exception e) {
                throw new IllegalStateException("Invocation failed: " + target.getClass().getName() + "#" + name, e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Invocation failed: " + target.getClass().getName() + "#" + name, e);
        }
    }

    private record DispatchPacket(String channelId, byte[] payload) {
    }
}
