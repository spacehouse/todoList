package com.todolist.neoforge.network;

import com.todolist.TodoConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
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
 * NeoForge 缃戠粶妗ユ帴绫汇€?
 * 璐熻矗灏佽鑷畾涔夎浇鑽风殑娉ㄥ唽銆佸彂閫佷笌鍒嗗彂閫昏緫銆?
 */
public final class NeoForgeNetworkBridge {
    /**
     * 鏈嶅姟绔帴鏀跺櫒鍥炶皟銆?
     */
    @FunctionalInterface
    public interface ServerReceiver {
        /**
         * 澶勭悊鏈嶅姟绔敹鍒扮殑鏁版嵁鍖呫€?
         *
         * @param server 褰撳墠鏈嶅姟绔?
         * @param player 鍙戦€佺帺瀹?
         * @param handler 缃戠粶澶勭悊鍣?
         * @param buf 鏁版嵁缂撳啿
         * @param responseSender 鍥炲寘鍙戦€佸櫒
         */
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
                     FriendlyByteBuf buf, NeoForgePacketSender responseSender);
    }

    /**
     * 瀹㈡埛绔帴鏀跺櫒鍥炶皟銆?
     */
    @FunctionalInterface
    public interface ClientReceiver {
        /**
         * 澶勭悊瀹㈡埛绔敹鍒扮殑鏁版嵁鍖呫€?
         *
         * @param client 褰撳墠瀹㈡埛绔?
         * @param handler 缃戠粶澶勭悊鍣?
         * @param buf 鏁版嵁缂撳啿
         * @param responseSender 鍥炲寘鍙戦€佸櫒
         */
        void receive(Minecraft client, Object handler, FriendlyByteBuf buf, NeoForgePacketSender responseSender);
    }

    /**
     * 鐜╁鍔犲叆鐩戝惉鍣ㄣ€?
     */
    @FunctionalInterface
    public interface JoinListener {
        /**
         * 澶勭悊鐜╁鍔犲叆浜嬩欢銆?
         *
         * @param player 鍔犲叆鐜╁
         * @param sender 鍥炲寘鍙戦€佸櫒
         * @param server 褰撳墠鏈嶅姟绔?
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
     * 绉佹湁鏋勯€犲嚱鏁帮紝閬垮厤澶栭儴瀹炰緥鍖栥€?
     */
    private NeoForgeNetworkBridge() {
    }

    /**
     * 鍒濆鍖栫綉缁滄ˉ鎺ュ苟娉ㄥ唽杞借嵎澶勭悊鍣ㄣ€?
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
     * 娉ㄥ唽鏈嶅姟绔帴鏀跺櫒銆?
     *
     * @param channelId 閫氶亾 ID
     * @param receiver 鎺ユ敹鍣?
     */
    public static void registerServerReceiver(ResourceLocation channelId, ServerReceiver receiver) {
        init();
        SERVER_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * 娉ㄥ唽瀹㈡埛绔帴鏀跺櫒銆?
     *
     * @param channelId 閫氶亾 ID
     * @param receiver 鎺ユ敹鍣?
     */
    public static void registerClientReceiver(ResourceLocation channelId, ClientReceiver receiver) {
        init();
        CLIENT_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * 娉ㄥ唽鐜╁鍔犲叆鐩戝惉鍣ㄣ€?
     *
     * @param listener 鐩戝惉鍣?
     */
    public static void registerJoinListener(JoinListener listener) {
        init();
        JOIN_LISTENERS.add(listener);
    }

    /**
     * 妫€鏌ユ槸鍚﹀彲浠ュ彂閫佹秷鎭埌鎸囧畾閫氶亾銆?
     *
     * @param channelId 閫氶亾 ID
     * @return 鏄惁鍙彂閫?
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
     * 鍙戦€佹暟鎹寘鍒版湇鍔＄銆?
     *
     * @param channelId 閫氶亾 ID
     * @param buf 鏁版嵁缂撳啿
     */
    public static void sendToServer(ResourceLocation channelId, FriendlyByteBuf buf) {
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
     * 鍙戦€佹暟鎹寘鍒板鎴风锛堟寚瀹氱帺瀹讹級銆?
     *
     * @param player 鐩爣鐜╁
     * @param channelId 閫氶亾 ID
     * @param buf 鏁版嵁缂撳啿
     */
    public static void sendToPlayer(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        PacketDistributor.sendToPlayer(player, new NeoForgeDispatchPayload(channelId.toString(), toByteArray(buf)));
    }

    /**
     * 娉ㄥ唽杞借嵎澶勭悊鍣ㄤ簨浠剁洃鍚€?
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
    private static Method findFourArgPlayBidirectional() {
        for (Method method : PayloadRegistrar.class.getMethods()) {
            if ("playBidirectional".equals(method.getName()) && method.getParameterCount() == 4) {
                return method;
            }
        }
        return null;
    }

    /**
     * 澶勭悊杞借嵎骞跺垎娲惧埌瀵瑰簲渚с€?
     *
     * @param payload 杞借嵎
     * @param context 杞借嵎涓婁笅鏂?
     */
    private static void handleDispatchPayload(NeoForgeDispatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dispatchPacketBySide(payload, context));
    }

    /**
     * 鎸変晶鍒嗗彂杞借嵎銆?
     *
     * @param payload 杞借嵎
     * @param context 杞借嵎涓婁笅鏂?
     */
    private static void dispatchPacketBySide(NeoForgeDispatchPayload payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            dispatchServerPacket(payload, context);
            return;
        }
        dispatchClientPacket(payload);
    }

    /**
     * 澶勭悊鏈嶅姟绔浇鑽枫€?
     *
     * @param payload 杞借嵎
     * @param context 杞借嵎涓婁笅鏂?
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
     * 澶勭悊瀹㈡埛绔浇鑽枫€?
     *
     * @param payload 杞借嵎
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
     * 娉ㄥ唽鐜╁鐧诲綍鐩戝惉锛岀敤浜庤Е鍙戝姞鍏ュ洖璋冦€?
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
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        for (JoinListener listener : JOIN_LISTENERS) {
            listener.onJoin(player, NO_OP_SENDER, server);
        }
    }

    /**
     * 鑾峰彇鏈嶅姟绔綉缁滃鐞嗗櫒銆?
     *
     * @param player 鐩爣鐜╁
     * @return 鏈嶅姟绔綉缁滃鐞嗗櫒
     */
    private static ServerGamePacketListenerImpl getServerNetworkHandler(ServerPlayer player) {
        return player.connection;
    }

    /**
     * 灏嗙紦鍐插尯鍐呭澶嶅埗涓哄瓧鑺傛暟缁勩€?
     *
     * @param source 鍘熷缂撳啿
     * @return 瀛楄妭鏁扮粍
     */
    private static byte[] toByteArray(FriendlyByteBuf source) {
        FriendlyByteBuf copy = new FriendlyByteBuf(source.copy());
        byte[] bytes = new byte[copy.readableBytes()];
        copy.readBytes(bytes);
        return bytes;
    }

}
