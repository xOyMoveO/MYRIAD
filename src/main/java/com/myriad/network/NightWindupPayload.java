package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A zero rate stops only the named actor's cast. */
public record NightWindupPayload(int actor, int cast, float rate) implements CustomPacketPayload {
    public static final Type<NightWindupPayload> TYPE = new Type<>(Identifier.parse("myriad:night_windup"));
    public static final StreamCodec<FriendlyByteBuf, NightWindupPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.actor); buf.writeVarInt(p.cast); buf.writeFloat(p.rate); },
            buf -> new NightWindupPayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
