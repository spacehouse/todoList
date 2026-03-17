package com.todolist.forge.network;

import com.todolist.TodoConstants;
import com.todolist.TodoListForge;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Forge networking bridge based on SimpleChannel.
 * This class keeps the same external API used by current packet registrars.
 */
public final class ForgeNetworkBridge {
    /**
     * Server-side packet receiver callback.
     */
    @FunctionalInterface
    public interface ServerReceiver {
        /**
         * Handles one server-side packet.
         *
         * @param server current server instance
         * @param player sender player
         * @param handler server network handler
         * @param buf packet payload
         * @param responseSender response sender
         */
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf, ForgePacketSender responseSender);
    }

    /**
     * Client-side packet receiver callback.
     */
    @FunctionalInterface
    public interface ClientReceiver {
        /**
         * Handles one client-side packet.
         *
         * @param client current client instance
         * @param handler connection snapshot
         * @param buf packet payload
         * @param responseSender response sender
         */
        void receive(Minecraft client, Object handler, FriendlyByteBuf buf, ForgePacketSender responseSender);
    }

    /**
     * Player join callback.
     */
    @FunctionalInterface
    public interface JoinListener {
        /**
         * Handles one player join event.
         *
         * @param player joined player
         * @param sender response sender
         * @param server current server
         */
        void onJoin(ServerPlayer player, ForgePacketSender sender, MinecraftServer server);
    }

    private static final ForgePacketSender NO_OP_SENDER = (channelId, buf) -> { };
    private static final int PROTOCOL = 1;
    private static final int DISPATCH_ID = 0;
    private static final Map<String, ServerReceiver> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    private static final Map<String, ClientReceiver> CLIENT_RECEIVERS = new ConcurrentHashMap<>();
    private static final List<JoinListener> JOIN_LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized;
    private static SimpleChannel simpleChannel;

    /**
     * Prevents external instantiation.
     */
    private ForgeNetworkBridge() {
    }

    /**
     * Initializes channel and listeners once.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            simpleChannel = createChannel();
            registerDispatchMessages(simpleChannel);
            registerPlayerLoginHook();
            initialized = true;
            TodoListForge.LOGGER.info("Forge network bridge initialized successfully");
        } catch (Exception e) {
            initialized = false;
            simpleChannel = null;
            TodoListForge.LOGGER.error("Forge network bridge initialization failed", e);
            throw e;
        }
    }

    /**
     * Registers one server packet receiver.
     *
     * @param channelId packet id
     * @param receiver receiver callback
     */
    public static void registerServerReceiver(ResourceLocation channelId, ServerReceiver receiver) {
        init();
        SERVER_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * Registers one client packet receiver.
     *
     * @param channelId packet id
     * @param receiver receiver callback
     */
    public static void registerClientReceiver(ResourceLocation channelId, ClientReceiver receiver) {
        init();
        CLIENT_RECEIVERS.put(channelId.toString(), receiver);
    }

    /**
     * Registers one player join listener.
     *
     * @param listener join callback
     */
    public static void registerJoinListener(JoinListener listener) {
        init();
        JOIN_LISTENERS.add(listener);
    }

    /**
     * Checks whether remote side advertises this channel.
     *
     * @param channelId packet id
     * @return true when remote channel exists
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
        Connection connection = client.getConnection().getConnection();
        if (connection == null) {
            return false;
        }
        try {
            return simpleChannel.isRemotePresent(connection);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Sends one packet to server.
     *
     * @param channelId packet id
     * @param buf packet payload
     */
    public static void sendToServer(ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        simpleChannel.send(new DispatchPacket(channelId.toString(), toByteArray(buf)), PacketDistributor.SERVER.noArg());
    }

    /**
     * Sends one packet to a specific player.
     *
     * @param player target player
     * @param channelId packet id
     * @param buf packet payload
     */
    public static void sendToPlayer(ServerPlayer player, ResourceLocation channelId, FriendlyByteBuf buf) {
        init();
        simpleChannel.send(new DispatchPacket(channelId.toString(), toByteArray(buf)), PacketDistributor.PLAYER.with(player));
    }

    /**
     * Creates the Forge simple channel.
     *
     * @return configured simple channel
     */
    private static SimpleChannel createChannel() {
        ResourceLocation channelId = ResourceLocation.fromNamespaceAndPath(TodoConstants.MOD_ID, "bridge");
        try {
            return ChannelBuilder.named(channelId)
                    .networkProtocolVersion(PROTOCOL)
                    .clientAcceptedVersions((status, version) -> true)
                    .serverAcceptedVersions((status, version) -> true)
                    .simpleChannel();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Forge channel", e);
        }
    }

    /**
     * Registers the dispatch wrapper packet for bidirectional routing.
     *
     * @param channel channel instance
     */
    private static void registerDispatchMessages(SimpleChannel channel) {
        channel.messageBuilder(DispatchPacket.class, DISPATCH_ID)
                .encoder((packet, buffer) -> {
                    buffer.writeUtf(packet.channelId());
                    buffer.writeByteArray(packet.payload());
                })
                .decoder(buffer -> new DispatchPacket(buffer.readUtf(), buffer.readByteArray()))
                .consumerMainThread(ForgeNetworkBridge::dispatchPacketBySide)
                .add();
        channel.build();
    }

    /**
     * Dispatches packet to server or client receiver by runtime side.
     *
     * @param packet wrapped packet
     * @param context forge packet context
     */
    private static void dispatchPacketBySide(DispatchPacket packet, CustomPayloadEvent.Context context) {
        if (context.getSender() instanceof ServerPlayer) {
            dispatchServerPacket(packet, context);
            return;
        }
        dispatchClientPacket(packet);
    }

    /**
     * Dispatches one packet on server side.
     *
     * @param packet wrapped packet
     * @param context forge packet context
     */
    private static void dispatchServerPacket(DispatchPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player == null) {
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

    /**
     * Dispatches one packet on client side.
     *
     * @param packet wrapped packet
     */
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

    /**
     * Registers player login callback on Forge event bus.
     */
    private static void registerPlayerLoginHook() {
        try {
            MinecraftForge.EVENT_BUS.addListener(ForgeNetworkBridge::onPlayerLoggedIn);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register Forge player login hook", e);
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
     * Resolves server network handler from player.
     *
     * @param player target player
     * @return handler or null
     */
    private static ServerGamePacketListenerImpl getServerNetworkHandler(ServerPlayer player) {
        return player.connection;
    }

    /**
     * Copies payload bytes from source buffer.
     *
     * @param source source buffer
     * @return copied bytes
     */
    private static byte[] toByteArray(FriendlyByteBuf source) {
        FriendlyByteBuf copy = new FriendlyByteBuf(source.copy());
        byte[] bytes = new byte[copy.readableBytes()];
        copy.readBytes(bytes);
        return bytes;
    }

    /**
     * Wrapped dispatch packet for one logical channel payload.
     *
     * @param channelId logical channel id
     * @param payload payload bytes
     */
    private record DispatchPacket(String channelId, byte[] payload) {
    }
}
