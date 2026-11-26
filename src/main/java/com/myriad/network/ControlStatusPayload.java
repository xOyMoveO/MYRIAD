package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Owner-only observation, not an input gate or a client prediction of controls. */
public record ControlStatusPayload(String role, String dimension, int stun, int root, int silence,
        float actionRate, String chargeSkill, int charge, float walkFactor, int motion) implements CustomPacketPayload {
    public static final int MAX_TICKS = 1_000_000, STALE_TICKS = 12;
    public static final Type<ControlStatusPayload> TYPE = new Type<>(Identifier.parse("myriad:control_status"));
    public static ControlStatusPayload empty() { return new ControlStatusPayload("", "", 0, 0, 0, 1, "", 0, 1, 0); }
    public boolean active() { return stun > 0 || root > 0 || silence > 0 || actionRate < 1 || charge > 0 || motion > 0; }
    public static int ticks(long ticks) { return (int)Math.clamp(ticks, 0, MAX_TICKS); }
    public ControlStatusPayload elapsed(int age) {
        if (age < 0 || age > STALE_TICKS) return empty();
        return new ControlStatusPayload(role, dimension, Math.max(0, stun-age), Math.max(0, root-age), Math.max(0, silence-age),
                actionRate, chargeSkill, Math.max(0, charge-age), walkFactor, Math.max(0, motion-age));
    }
    public static final StreamCodec<FriendlyByteBuf, ControlStatusPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUtf(p.role, 64); b.writeUtf(p.dimension, 256); b.writeVarInt(p.stun); b.writeVarInt(p.root); b.writeVarInt(p.silence);
        b.writeFloat(p.actionRate); b.writeUtf(p.chargeSkill, 128); b.writeVarInt(p.charge); b.writeFloat(p.walkFactor); b.writeVarInt(p.motion);
    }, b -> new ControlStatusPayload(b.readUtf(64), b.readUtf(256), readTicks(b), readTicks(b), readTicks(b), rate(b, false),
            b.readUtf(128), readTicks(b), rate(b, true), readTicks(b)));
    static int readTicks(FriendlyByteBuf b) {
        int v = b.readVarInt(); if (v < 0 || v > MAX_TICKS) throw new io.netty.handler.codec.DecoderException("Invalid status duration"); return v;
    }
    private static float rate(FriendlyByteBuf b, boolean zeroAllowed) {
        float v = b.readFloat(); if (!Float.isFinite(v) || v > 1 || v < 0 || !zeroAllowed && v == 0)
            throw new io.netty.handler.codec.DecoderException("Invalid status rate"); return v;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
