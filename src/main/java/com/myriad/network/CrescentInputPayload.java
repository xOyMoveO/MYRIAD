package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One press and its tap/hold decision share a sequence. Timing, selection and distance belong to the server. */
public record CrescentInputPayload(int gesture, Edge edge) implements CustomPacketPayload {
    public enum Edge { PRESS, TAP, HOLD }
    public static final Type<CrescentInputPayload> TYPE = new Type<>(Identifier.parse("myriad:crescent_input"));
    public static final StreamCodec<FriendlyByteBuf, CrescentInputPayload> CODEC = StreamCodec.of(
            (buf, input) -> { buf.writeVarInt(input.gesture()); buf.writeEnum(input.edge()); },
            buf -> new CrescentInputPayload(buf.readVarInt(), buf.readEnum(Edge.class)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
