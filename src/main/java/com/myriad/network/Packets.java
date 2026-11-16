package com.myriad.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Shared send entry points; recipient selection remains at the authoritative gameplay caller. */
public final class Packets {
    private Packets() {}

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload,
            CustomPacketPayload... additionalPayloads) {
        ServerPlayNetworking.send(player, payload);
        for (CustomPacketPayload additional : additionalPayloads) ServerPlayNetworking.send(player, additional);
    }

    @Environment(EnvType.CLIENT)
    public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... additionalPayloads) {
        ClientPackets.sendToServer(payload, additionalPayloads);
    }
}
