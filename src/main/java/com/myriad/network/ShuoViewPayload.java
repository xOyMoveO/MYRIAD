package com.myriad.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
/** Owner-only confirmed view state; not a cast or damage request. */
public record ShuoViewPayload(int action,int ticks,long started,int token) implements CustomPacketPayload {
    public static final int DRAW=0,SHEATHE=1,CHARGE=2,GUARD=3,COUNTER=4,FLASH=5,CUT_LEFT=6,CUT_RIGHT=7,CUT_DOWN=8,RETREAT=9,CANCEL=10;
    public static final int BASIC_RISE=11,BASIC_SWEEP=12,MAX_ACTION=BASIC_SWEEP;
    public static boolean basicAction(int action){return action==DRAW||action==BASIC_RISE||action==BASIC_SWEEP;}
    public static final Type<ShuoViewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","shuo_view"));
    public static final StreamCodec<FriendlyByteBuf,ShuoViewPayload> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.action);b.writeVarInt(p.ticks);b.writeLong(p.started);b.writeVarInt(p.token);},b->new ShuoViewPayload(b.readVarInt(),b.readVarInt(),b.readLong(),b.readVarInt()));
    public boolean valid(){return action>=0&&action<=MAX_ACTION&&token>0&&ticks>=0&&ticks<=60&&(action==CANCEL||ticks>0);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
