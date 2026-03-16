package com.todolist.neoforge.network;

import com.todolist.TodoConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
 * NeoForge 网络桥接类。
 * 负责封装自定义载荷的注册、发送与分发逻辑。
 */
public final class NeoForgeNetworkBridge {
    /**
     * 服务端接收器回调。
     */
    @FunctionalInterface
    public interface ServerReceiver {
        /**
         * 处理服务端收到的数据包。
         *
         * @param server 当前服务端
         * @param player 发送玩家
         * @param handler 网络处理器
         * @param buf 数据缓冲
         * @param responseSender 回包发送器
         */
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
                     FriendlyByteBuf buf, NeoForgePacketSender responseSender);
    }

    /**
     * 客户端接收器回调。
     */
    @FunctionalInterface
    public interface ClientReceiver {
        /**
         * 处理客户端收到的数据包。
         *
         * @param client 当前客户端
         * @param handler 网络处理器
         * @param buf 数据缓冲
         * @param responseSender 回包发送器
         */
        void receive(Minecraft client, Object handler, FriendlyByteBuf buf, NeoForgePacketSender responseSender);
    }

    /**
     * 玩家加入监听器。
     */
    @FunctionalInterface
    public interface JoinListener {
        /**
         * 处理玩家加入事件。
         *
         * @param player 加入玩家
         * @param sender 回包发送器
         * @param server 当前服务端
         */
        void onJoin(ServerPlayer player, NeoForgePacketSender sender, MinecraftServer server);
    }

    private static final NeoForgePacketSender NO_OP_SENDER = (channelId, buf) -> { };
    private static final String PROTOCOL = "1";
    private static final Map<String, ServerReceiver> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<String, ClientReceiver> CLIENT_RECEIVERS = new ConcurrentHashMap<>();
    private static final List<JoinListener> JOIN_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized;

    /**
     * 私有构造函数，避免外部实例化。
     */
    private NeoForgeNetworkBridge() {
    }

    /**
     * 初始化网络桥接并注册载荷处理器。
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerPayloadHandlers();
        registerPlayerLoginHook();
    }

    /**
     * 注册服务端接收器。
     *
     * @param channelId 通道 ID
     * @param receiver 接收器
     */
    public static void registerServerReceiver(ResourceLocation channelId, ServerReceiver receiver) {
        init();
        SERVER_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * 注册客户端接收器。
     *
     * @param channelId 通道 ID
     * @param receiver 接收器
     */
    public static void registerClientReceiver(ResourceLocation channelId, ClientReceiver receiver) {
        init();
        CLIENT_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * 注册玩家加入监听器。
     *
     * @param listener 监听器
     */
    public static void registerJoinListener(JoinListener listener) {
        init();
        JOIN_LISTENERS.add(listener);
    }

    /**
     * 检查是否可以发送消息到指定通道。
     *
     * @param channelId 通道 ID
     * @return 是否可发送
     */
    public static boolean canSend(ResourceLocation channelId) {
        init();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return false;
        }
        if (client.isLocalServer()) {
            return false;
        }
        Object listenerObj = client.getConnection();
        if (listenerObj instanceof ICommonPacketListener listener) {
            return listener.hasChannel(NeoForgeDispatchPayload.TYPE);
        }
        Object connection = resolveNettyConnection(listenerObj);
        if (connection instanceof net.minecraft.network.Connection) {
            return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(
                    (net.minecraft.network.Connection) connection,
                    ConnectionProtocol.PLAY,
                    NeoForgeDispatchPayload.TYPE.id()
            );
        }
        return false;
    }

    /**
     * 发送数据包到服务端。
     *
     * @param channelId 通道 ID
     * @param buf 数据缓冲
     */
    public static void sendToServer(ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        PacketDistributor.sendToServer(new NeoForgeDispatchPayload(channelId.toString(), toByteArray(buf)));
    }

    /**
     * 发送数据包到客户端（指定玩家）。
     *
     * @param player 目标玩家
     * @param channelId 通道 ID
     * @param buf 数据缓冲
     */
    public static void sendToPlayer(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        PacketDistributor.sendToPlayer(player, new NeoForgeDispatchPayload(channelId.toString(), toByteArray(buf)));
    }

    /**
     * 注册载荷处理器事件监听。
     */
    private static void registerPayloadHandlers() {
        try {
            IEventBus modEventBus = ModLoadingContext.get().getActiveContainer().getEventBus();
            modEventBus.addListener(NeoForgeNetworkBridge::onRegisterPayloadHandlers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register NeoForge payload handlers", e);
        }
    }

    /**
     * 注册载荷编解码与处理逻辑。
     *
     * @param event 载荷注册事件
     */
    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TodoConstants.MOD_ID).versioned(PROTOCOL);
        registrar.playBidirectional(NeoForgeDispatchPayload.TYPE, NeoForgeDispatchPayload.CODEC, NeoForgeNetworkBridge::handleDispatchPayload);
    }

    /**
     * 处理载荷并分派到对应侧。
     *
     * @param payload 载荷
     * @param context 载荷上下文
     */
    private static void handleDispatchPayload(NeoForgeDispatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dispatchPacketBySide(payload, context));
    }

    /**
     * 按侧分发载荷。
     *
     * @param payload 载荷
     * @param context 载荷上下文
     */
    private static void dispatchPacketBySide(NeoForgeDispatchPayload payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            dispatchServerPacket(payload, context);
            return;
        }
        dispatchClientPacket(payload);
    }

    /**
     * 处理服务端载荷。
     *
     * @param payload 载荷
     * @param context 载荷上下文
     */
    private static void dispatchServerPacket(NeoForgeDispatchPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerGamePacketListenerImpl handler = getServerNetworkHandler(player);
        ServerReceiver receiver = SERVER_RECEIVERS.get(payload.channelId());
        if (receiver == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.payload()));
        receiver.receive(server, player, handler, buf, NO_OP_SENDER);
    }

    /**
     * 处理客户端载荷。
     *
     * @param payload 载荷
     */
    private static void dispatchClientPacket(NeoForgeDispatchPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        ClientReceiver receiver = CLIENT_RECEIVERS.get(payload.channelId());
        if (receiver == null) {
            return;
        }
        Object connectionSnapshot = client.getConnection();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.payload()));
        client.execute(() -> receiver.receive(client, connectionSnapshot, buf, NO_OP_SENDER));
    }

    /**
     * 注册玩家登录监听，用于触发加入回调。
     */
    private static void registerPlayerLoginHook() {
        try {
            Object eventBus = NeoForge.EVENT_BUS;
            Class<?> loginEventClass = Class.forName("net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent");
            Method addListener = eventBus.getClass().getMethod("addListener", EventPriority.class, boolean.class,
                    Class.class, java.util.function.Consumer.class);
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
            throw new IllegalStateException("Failed to register NeoForge player login hook", e);
        }
    }

    /**
     * 解析客户端连接对象。
     *
     * @param clientPacketListener 客户端监听器
     * @return 连接对象
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
            for (Field field : clientPacketListener.getClass().getDeclaredFields()) {
                if (connectionType.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field.get(clientPacketListener);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 获取服务端网络处理器。
     *
     * @param player 目标玩家
     * @return 服务端网络处理器
     */
    private static ServerGamePacketListenerImpl getServerNetworkHandler(ServerPlayer player) {
        try {
            Field field = player.getClass().getField("networkHandler");
            Object value = field.get(player);
            if (value instanceof ServerGamePacketListenerImpl handler) {
                return handler;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 将缓冲区内容复制为字节数组。
     *
     * @param source 原始缓冲
     * @return 字节数组
     */
    private static byte[] toByteArray(FriendlyByteBuf source) {
        FriendlyByteBuf copy = new FriendlyByteBuf(source.copy());
        byte[] bytes = new byte[copy.readableBytes()];
        copy.readBytes(bytes);
        return bytes;
    }

    /**
     * 在指定类型上查找指定名称和参数数量的方法。
     *
     * @param type 目标类型
     * @param name 方法名
     * @param paramCount 参数数量
     * @return 匹配的方法
     */
    private static Method findMethod(Class<?> type, String name, int paramCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
                return method;
            }
        }
        throw new IllegalStateException("Method not found: " + type.getName() + "#" + name + "/" + paramCount);
    }

    /**
     * 通过反射调用方法，必要时尝试降级匹配。
     *
     * @param target 调用目标
     * @param name 方法名
     * @param parameterTypes 参数类型
     * @param args 参数
     * @return 调用结果
     */
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
}