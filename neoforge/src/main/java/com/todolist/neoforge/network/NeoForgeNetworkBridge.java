package com.todolist.neoforge.network;

import com.todolist.TodoConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
    public static void registerServerReceiver(Identifier channelId, ServerReceiver receiver) {
        init();
        SERVER_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * 注册客户端接收器。
     *
     * @param channelId 通道 ID
     * @param receiver 接收器
     */
    public static void registerClientReceiver(Identifier channelId, ClientReceiver receiver) {
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
    public static boolean canSend(Identifier channelId) {
        init();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return false;
        }
        if (client.isLocalServer()) {
            var server = client.getSingleplayerServer();
            return server != null && server.isPublished();
        }
        if (client.getConnection() instanceof ICommonPacketListener listener) {
            return listener.hasChannel(NeoForgeDispatchPayload.TYPE);
        }
        var connection = client.getConnection().getConnection();
        return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(
                connection,
                ConnectionProtocol.PLAY,
                NeoForgeDispatchPayload.TYPE.id()
        );
    }

    /**
     * 发送数据包到服务端。
     *
     * @param channelId 通道 ID
     * @param buf 数据缓冲
     */
    public static void sendToServer(Identifier channelId, FriendlyByteBuf buf) {
        init();
        // v1_21_6 覆盖：NeoForge 21.7 起 PacketDistributor 移除 sendToServer，
        // 改为经客户端连接监听器直接发送 ServerboundCustomPayloadPacket（21.6 与 21.7+ 通用）
        var clientConnection = Minecraft.getInstance().getConnection();
        if (clientConnection != null) {
            clientConnection.send(new ServerboundCustomPayloadPacket(
                    new NeoForgeDispatchPayload(channelId.toString(), toByteArray(buf))));
        }
    }

    /**
     * 发送数据包到客户端（指定玩家）。
     *
     * @param player 目标玩家
     * @param channelId 通道 ID
     * @param buf 数据缓冲
     */
    public static void sendToPlayer(ServerPlayer player, Identifier channelId, FriendlyByteBuf buf) {
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
     * 注册双向载荷 todolist:bridge 的编解码与处理逻辑。
     *
     * <p>v1_21_6 覆盖（21.6/21.7/21.8 共用源码）：NeoForge 21.7 起三参 playBidirectional
     * 仅注册服务端侧 handler，且客户端启动期会校验 clientbound 载荷必须持有客户端
     * handler；21.6 仅有三参形态且 handler 双侧生效。因此运行时反射探测四参重载，
     * 21.7/21.8 显式传双 handler，21.6 回退三参旧行为。
     *
     * @param event 载荷注册事件
     */
    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(TodoConstants.MOD_ID).versioned(PROTOCOL);
        // v1_21_6 覆盖（21.6/21.7/21.8 共用源码）：NeoForge 21.7 起 playBidirectional
        // 三参重载的 handler 仅注册为服务端侧（客户端 handler 置 null），且客户端启动时
        // ClientNetworkRegistry.setup 校验 clientbound 载荷必须有客户端 handler，缺失即抛
        // IllegalStateException 导致游戏无法启动；21.6 仅有三参形态（handler 双侧生效、
        // 无启动校验）。运行时按四参方法存在性选择调用形态：21.7/21.8 走四参显式传双
        // handler，21.6 走三参保持旧行为。
        Method fourArgBidirectional = findFourArgPlayBidirectional();
        IPayloadHandler<NeoForgeDispatchPayload> dispatchHandler = NeoForgeNetworkBridge::handleDispatchPayload;
        try {
            if (fourArgBidirectional != null) {
                fourArgBidirectional.invoke(registrar, NeoForgeDispatchPayload.TYPE, NeoForgeDispatchPayload.CODEC,
                        dispatchHandler, dispatchHandler);
            } else {
                registrar.playBidirectional(NeoForgeDispatchPayload.TYPE, NeoForgeDispatchPayload.CODEC,
                        dispatchHandler);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注册双向载荷 todolist:bridge 失败", e);
        }
    }

    /**
     * 查找四参形态的 playBidirectional（type, codec, serverHandler, clientHandler）。
     *
     * @return 四参方法；不存在（NeoForge 21.6 及以下仅有三参形态）时返回 null
     */
    // 1.21.9 分支起降为包级可见，供 NeoForgeNetworkProbeTestMain 离线直调（方案 8.2-1）
    static Method findFourArgPlayBidirectional() {
        for (Method method : PayloadRegistrar.class.getMethods()) {
            if ("playBidirectional".equals(method.getName()) && method.getParameterCount() == 4) {
                return method;
            }
        }
        return null;
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
        MinecraftServer server = player.level().getServer();
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
            NeoForge.EVENT_BUS.addListener(NeoForgeNetworkBridge::onPlayerLoggedIn);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register NeoForge player login hook", e);
        }
    }

    /**
     * Handles player login event and dispatches join listeners.
     *
     * @param event player login event
     */
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        for (JoinListener listener : JOIN_LISTENERS) {
            listener.onJoin(player, NO_OP_SENDER, server);
        }
    }

    /**
     * 获取服务端网络处理器。
     *
     * @param player 目标玩家
     * @return 服务端网络处理器
     */
    private static ServerGamePacketListenerImpl getServerNetworkHandler(ServerPlayer player) {
        return player.connection;
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

}
