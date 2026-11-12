package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-confirmed, owner-only Night first-person cue. It cannot cast a skill or move an entity. */
public record NightSkillViewPayload(int action,int ticks,long started,int token) implements CustomPacketPayload {
    public static final int THROW=0,MERGE=1,DASH=2,RETURN=3,SHIELD=4,READY=5,STRIKE=6,CANCEL=7;
    public static final Type<NightSkillViewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","night_skill_view"));
    public static final StreamCodec<FriendlyByteBuf,NightSkillViewPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.action);b.writeVarInt(p.ticks);b.writeLong(p.started);b.writeVarInt(p.token);},
            b->new NightSkillViewPayload(b.readVarInt(),b.readVarInt(),b.readLong(),b.readVarInt()));
    public boolean valid(){return action>=THROW&&action<=CANCEL&&ticks>=0&&ticks<=40&&(action==CANCEL||ticks>0)&&token>=0;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
