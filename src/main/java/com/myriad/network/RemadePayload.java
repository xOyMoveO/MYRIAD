package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent only to the returned participants, after their post-round lobby snapshot. */
public record RemadePayload() implements CustomPacketPayload {
    public static final Type<RemadePayload> TYPE = new Type<>(Identifier.parse("myriad:round_remade_v1"));
    public static final StreamCodec<FriendlyByteBuf, RemadePayload> CODEC = StreamCodec.unit(new RemadePayload());
    @Override public Type<RemadePayload> type() { return TYPE; }
}
