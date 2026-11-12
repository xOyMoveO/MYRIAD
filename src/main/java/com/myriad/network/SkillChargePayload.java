package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Start/stop presentation for a real server-owned cast; never a cast request. */
public record SkillChargePayload(String skill,int ticks,long started,float walkFactor) implements CustomPacketPayload {
    public SkillChargePayload(String skill,int ticks,long started){this(skill,ticks,started,0);}
    public static final Type<SkillChargePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","skill_charge"));
    public static final StreamCodec<FriendlyByteBuf,SkillChargePayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeUtf(p.skill,128);b.writeVarInt(p.ticks);b.writeLong(p.started);b.writeFloat(p.walkFactor);},
            b->new SkillChargePayload(b.readUtf(128),b.readVarInt(),b.readLong(),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
