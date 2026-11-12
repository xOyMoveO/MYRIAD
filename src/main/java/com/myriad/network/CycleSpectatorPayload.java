package com.myriad.network;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** 死亡等待期间由客户端请求切换到下一名可观战友军。 */
public record CycleSpectatorPayload() implements CustomPacketPayload {
    public static final Type<CycleSpectatorPayload> TYPE =
            new Type<>(Identifier.parse("myriad:cycle_spectator"));
    public static final StreamCodec<FriendlyByteBuf, CycleSpectatorPayload> CODEC =
            StreamCodec.unit(new CycleSpectatorPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
