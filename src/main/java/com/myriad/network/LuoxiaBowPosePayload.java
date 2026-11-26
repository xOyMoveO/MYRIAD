package com.myriad.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Presentation for one archer. Work, required work, aim and every release come from the server. */
public record LuoxiaBowPosePayload(UUID owner, int token, int skill, Phase phase, float work,
        float minimum, float maximum, float yaw, float pitch, long tick) implements CustomPacketPayload {
    public enum Phase { DRAW, WINDUP, LOCK, RELOAD, RELEASE, CANCEL, EARLY, STEP, CHAIN, RESET }
    public static final Type<LuoxiaBowPosePayload> TYPE = new Type<>(Identifier.parse("myriad:luoxia_bow_pose_v1"));
    public static final StreamCodec<FriendlyByteBuf,LuoxiaBowPosePayload> CODEC = StreamCodec.of((b,p) -> {
        b.writeUUID(p.owner); b.writeVarInt(p.token); b.writeByte(p.skill); b.writeEnum(p.phase);
        b.writeFloat(p.work); b.writeFloat(p.minimum); b.writeFloat(p.maximum);
        b.writeFloat(p.yaw); b.writeFloat(p.pitch); b.writeLong(p.tick);
    }, b -> new LuoxiaBowPosePayload(b.readUUID(), b.readVarInt(), b.readByte(), b.readEnum(Phase.class),
            b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
