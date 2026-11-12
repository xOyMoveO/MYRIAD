package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Input intent only; the server owns aim state and validates the held weapon. */
public record ToggleGunAimPayload() implements CustomPacketPayload {
    public static final Type<ToggleGunAimPayload> TYPE = new Type<>(Identifier.parse("myriad:toggle_gun_aim"));
    public static final StreamCodec<FriendlyByteBuf, ToggleGunAimPayload> CODEC = StreamCodec.unit(new ToggleGunAimPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
