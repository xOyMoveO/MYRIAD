package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TowerLockPayload(int tower, boolean locked) implements CustomPacketPayload {
    public static final Type<TowerLockPayload> TYPE = new Type<>(Identifier.parse("myriad:tower_lock"));
    public static final StreamCodec<FriendlyByteBuf, TowerLockPayload> CODEC = StreamCodec.of(
            (buf, state) -> { buf.writeVarInt(state.tower); buf.writeBoolean(state.locked); },
            buf -> new TowerLockPayload(buf.readVarInt(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
