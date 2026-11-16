package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Actor-only, authoritative UI outcomes and changing shop eligibility. */
public record UiFeedbackPayload(int kind) implements CustomPacketPayload {
    public static final Type<UiFeedbackPayload> TYPE = new Type<>(Identifier.parse("myriad:ui_feedback"));
    public static final StreamCodec<FriendlyByteBuf, UiFeedbackPayload> CODEC = StreamCodec.ofMember(
            (p,b) -> b.writeVarInt(p.kind), b -> new UiFeedbackPayload(b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void levelUp(ServerPlayer p) { Packets.sendToPlayer(p,new UiFeedbackPayload(0)); }
    public static void purchase(ServerPlayer p, boolean success) { Packets.sendToPlayer(p,new UiFeedbackPayload(success ? 1 : 2)); }
    public static void shopAccess(ServerPlayer p, boolean allowed) { Packets.sendToPlayer(p,new UiFeedbackPayload(allowed ? 3 : 4)); }
}
