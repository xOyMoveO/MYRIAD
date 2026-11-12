package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Short motion lease with an endpoint, preventing prediction from overshooting a landing. */
public record AbilityMotionPayload(float x,float y,float z,double tx,double ty,double tz,int ticks) implements CustomPacketPayload {
    public static final Type<AbilityMotionPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","ability_motion"));
    public static final StreamCodec<FriendlyByteBuf,AbilityMotionPayload> STREAM_CODEC=StreamCodec.ofMember(AbilityMotionPayload::write,AbilityMotionPayload::read);
    private void write(FriendlyByteBuf b){b.writeFloat(x);b.writeFloat(y);b.writeFloat(z);b.writeDouble(tx);b.writeDouble(ty);b.writeDouble(tz);b.writeInt(ticks);}
    private static AbilityMotionPayload read(FriendlyByteBuf b){return new AbilityMotionPayload(b.readFloat(),b.readFloat(),b.readFloat(),b.readDouble(),b.readDouble(),b.readDouble(),b.readInt());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
