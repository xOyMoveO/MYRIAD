package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record YikeStatePayload(int owner,int mode,int shells,int remaining,int nextShot,int windup,int jumps,Vec3 aim,int barrierCooldown,boolean steady,float progress,long tick) implements CustomPacketPayload {
    public static final Type<YikeStatePayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","yike_state"));
    public static final StreamCodec<FriendlyByteBuf,YikeStatePayload> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.owner);b.writeVarInt(p.mode);b.writeVarInt(p.shells);b.writeVarInt(p.remaining);b.writeVarInt(p.nextShot);b.writeVarInt(p.windup);b.writeVarInt(p.jumps);
        b.writeDouble(p.aim.x);b.writeDouble(p.aim.y);b.writeDouble(p.aim.z);b.writeVarInt(p.barrierCooldown);b.writeBoolean(p.steady);b.writeFloat(p.progress);b.writeLong(p.tick);
    },b->new YikeStatePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt(),b.readBoolean(),b.readFloat(),b.readLong()));
    public boolean valid(){return owner>=0&&mode>=0&&mode<=3&&shells>=0&&shells<=5&&remaining>=0&&remaining<=120&&nextShot>=0&&nextShot<=24000&&windup>=-1&&windup<=7&&jumps>=0&&jumps<=6&&barrierCooldown>=0&&barrierCooldown<=240
        &&Float.isFinite(progress)&&progress>=0&&progress<=1&&Double.isFinite(aim.x)&&Double.isFinite(aim.y)&&Double.isFinite(aim.z)&&Math.abs(aim.lengthSqr()-1)<.02;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
