package com.myriad.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Physical-client implementation, never resolved by common initialization or server sends. */
@Environment(EnvType.CLIENT)
final class ClientPackets {
    private ClientPackets() {}

    static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... additionalPayloads) {
        ClientPlayNetworking.send(payload);
        for (CustomPacketPayload additional : additionalPayloads) ClientPlayNetworking.send(additional);
    }
}
