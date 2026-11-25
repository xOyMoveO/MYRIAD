package com.myriad.network;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/** Preserves the mandatory protocol match formerly provided by the NeoForge payload registrar. */
final class NetworkProtocol {
    static final int VERSION = 24;
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("myriad:protocol");
    private static final Set<ServerConfigurationPacketListenerImpl> PENDING = ConcurrentHashMap.newKeySet();

    private NetworkProtocol() {}

    record VersionPayload(int version) implements CustomPacketPayload {
        static final Type<VersionPayload> TYPE = new Type<>(Identifier.parse("myriad:protocol"));
        static final StreamCodec<FriendlyByteBuf, VersionPayload> CODEC = StreamCodec.ofMember(
                (payload, buf) -> buf.writeVarInt(payload.version()), buf -> new VersionPayload(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static Component mismatch() {
        return Component.literal("万象客户端与服务器版本不一致（网络协议 " + VERSION + "）。请使用相同版本。");
    }

    static void register() {
        PayloadTypeRegistry.clientboundConfiguration().register(VersionPayload.TYPE, VersionPayload.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(VersionPayload.TYPE, VersionPayload.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            if (!ServerConfigurationNetworking.canSend(listener, VersionPayload.TYPE)) {
                listener.disconnect(Component.literal("此服务器要求安装万象（网络协议 " + VERSION + "）。"));
                return;
            }
            ((FabricServerConfigurationPacketListenerImpl) listener).addTask(new ProtocolTask(listener));
        });
        ServerConfigurationNetworking.registerGlobalReceiver(VersionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var listener = context.packetListener();
                    if (!PENDING.remove(listener) || payload.version() != VERSION) {
                        context.responseSender().disconnect(mismatch());
                        return;
                    }
                    ((FabricServerConfigurationPacketListenerImpl) listener).completeTask(TASK);
                }));
        ServerConfigurationConnectionEvents.DISCONNECT.register((listener, server) -> PENDING.remove(listener));
    }

    private record ProtocolTask(ServerConfigurationPacketListenerImpl listener) implements ConfigurationTask {
        @Override public void start(Consumer<Packet<?>> sender) {
            PENDING.add(listener);
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(new VersionPayload(VERSION)));
        }
        @Override public Type type() { return TASK; }
    }
}
