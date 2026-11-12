package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Owner-only, server-confirmed Tianxing hand gesture; cannot cast or modify combat. */
public record ThunderSkillViewPayload(int action,int ticks,long started,int token) implements CustomPacketPayload {
    public static final int GROUND=0,ARC_READY=1,ARC_FIRE=2,GUARD=3,SLASH_READY=4,SLASH_FIRE=5,
            JUDGMENT_READY=6,JUDGMENT_FIRE=7,NINE_FIRE=8,CANCEL=9;
    public static final Type<ThunderSkillViewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","thunder_skill_view"));
    public static final StreamCodec<FriendlyByteBuf,ThunderSkillViewPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.action);b.writeVarInt(p.ticks);b.writeLong(p.started);b.writeVarInt(p.token);},
            b->new ThunderSkillViewPayload(b.readVarInt(),b.readVarInt(),b.readLong(),b.readVarInt()));
    public boolean valid(){return action>=GROUND&&action<=CANCEL&&ticks>=0&&ticks<=40&&(action==CANCEL||ticks>0)&&token>0;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
