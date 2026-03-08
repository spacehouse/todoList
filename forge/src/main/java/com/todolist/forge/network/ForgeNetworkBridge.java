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
            return false;
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
     */
    public static void sendToServer(ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        invoke(simpleChannel, "sendToServer", new Class<?>[]{Object.class}, new DispatchPacket(channelId.toString(), toByteArray(buf)));
    }

    /**
     * 发送数据包到客户端（指定玩家）。
     */
    public static void sendToPlayer(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        Object packetTarget = createPlayerTarget(player);
        invoke(simpleChannel, "send", new Class<?>[]{packetTarget.getClass(), Object.class}, packetTarget, new DispatchPacket(channelId.toString(), toByteArray(buf)));
    }

    private static Object createChannel() {
        try {
            Class<?> networkRegistryClass = Class.forName("net.minecraftforge.network.NetworkRegistry");
            Method factory = findMethod(networkRegistryClass, "newSimpleChannel", 4);
            return factory.invoke(
                    null,
                    new ResourceLocation(TodoConstants.MOD_ID, "bridge"),
                    (Supplier<String>) () -> PROTOCOL,
                    (java.util.function.Predicate<String>) ForgeNetworkBridge::acceptRemoteVersion,
                    (java.util.function.Predicate<String>) ForgeNetworkBridge::acceptRemoteVersion
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Forge channel", e);
        }
    }

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
        BiConsumer<DispatchPacket, Supplier<Object>> dispatcher = (packet, contextSupplier) -> {
            Object context = contextSupplier.get();
            Runnable work = () -> dispatchPacketBySide(packet, context);
            invoke(context, "enqueueWork", new Class<?>[]{Runnable.class}, work);
            invoke(context, "setPacketHandled", new Class<?>[]{boolean.class}, true);
        };
        invoke(channel, "registerMessage", new Class<?>[]{int.class, Class.class, BiConsumer.class, Function.class, BiConsumer.class}, DISPATCH_ID, DispatchPacket.class, encoder, decoder, dispatcher);
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

    private static Object createPlayerTarget(ServerPlayer player) {
        try {
            Class<?> distributorClass = Class.forName("net.minecraftforge.network.PacketDistributor");
            Field playerField = distributorClass.getField("PLAYER");
            Object distributor = playerField.get(null);
            Method withMethod = distributor.getClass().getMethod("with", Supplier.class);
            return withMethod.invoke(distributor, (Supplier<Object>) () -> player);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create packet target", e);
        }
    }

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

    private static byte[] toByteArray(FriendlyByteBuf source) {
        FriendlyByteBuf copy = new FriendlyByteBuf(source.copy());
        byte[] bytes = new byte[copy.readableBytes()];
        copy.readBytes(bytes);
        return bytes;
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
