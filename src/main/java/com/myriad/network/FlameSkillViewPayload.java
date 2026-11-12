package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Owner-only confirmed hand presentation; never a request to cast or deal damage. */
public record FlameSkillViewPayload(int action,int ticks,long started,int token) implements CustomPacketPayload {
    public static final int CAGE_FIRE=0,BURST_READY=1,BURST_FIRE=2,SEA_FLIGHT=3,SEA_LAND=4,ARMOR=5,CANCEL=6;
    public static final Type<FlameSkillViewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","flame_skill_view"));
    public static final StreamCodec<FriendlyByteBuf,FlameSkillViewPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.action);b.writeVarInt(p.ticks);b.writeLong(p.started);b.writeVarInt(p.token);},
            b->new FlameSkillViewPayload(b.readVarInt(),b.readVarInt(),b.readLong(),b.readVarInt()));
    public boolean valid(){return action>=0&&action<=CANCEL&&ticks>=0&&ticks<=40&&(action==CANCEL||ticks>0)&&token>0;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
