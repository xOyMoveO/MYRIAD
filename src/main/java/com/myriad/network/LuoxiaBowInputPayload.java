package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A gesture belongs to its sending connection. Aim and charge are server-owned. */
public record LuoxiaBowInputPayload(int action, int gesture) implements CustomPacketPayload {
    public static final Type<LuoxiaBowInputPayload> TYPE = new Type<>(Identifier.parse("myriad:luoxia_bow_input_v1"));
    public static final StreamCodec<FriendlyByteBuf,LuoxiaBowInputPayload> CODEC = StreamCodec.of(
            (b,p) -> { b.writeVarInt(p.action); b.writeVarInt(p.gesture); },
            b -> new LuoxiaBowInputPayload(b.readVarInt(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
