package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** A compact visual recipe, never a damage or motion instruction. */
public record AbilityVfxPayload(int owner, int anchor, int theme, int variant, int kind, Vec3 from, Vec3 to,
        float radius, float angle, int life, long started, boolean personal,int instance) implements CustomPacketPayload {
    public AbilityVfxPayload(int owner,int anchor,int theme,int variant,int kind,Vec3 from,Vec3 to,
            float radius,float angle,int life,long started,boolean personal){
        this(owner,anchor,theme,variant,kind,from,to,radius,angle,life,started,personal,0);
    }
    public static final Type<AbilityVfxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("myriad","ability_vfx"));
    public static final StreamCodec<FriendlyByteBuf,AbilityVfxPayload> CODEC =
            StreamCodec.ofMember(AbilityVfxPayload::write,AbilityVfxPayload::read);
    private void write(FriendlyByteBuf b) {
        b.writeVarInt(owner);b.writeVarInt(anchor);b.writeVarInt(theme);b.writeVarInt(variant);b.writeVarInt(kind);
        b.writeDouble(from.x);b.writeDouble(from.y);b.writeDouble(from.z);
        b.writeDouble(to.x);b.writeDouble(to.y);b.writeDouble(to.z);
        b.writeFloat(radius);b.writeFloat(angle);b.writeVarInt(life);b.writeLong(started);b.writeBoolean(personal);b.writeVarInt(instance);
    }
    private static AbilityVfxPayload read(FriendlyByteBuf b) {
        return new AbilityVfxPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),
                b.readFloat(),b.readFloat(),b.readVarInt(),b.readLong(),b.readBoolean(),b.readVarInt());
    }
    public boolean valid() {
        return owner>=0&&anchor>=-1&&theme>=0&&theme<12&&variant>=0&&variant<8&&kind>=0&&kind<13
                &&finite(from)&&finite(to)&&from.distanceToSqr(to)<=128*128
                &&Float.isFinite(radius)&&radius>=0&&radius<=24&&Float.isFinite(angle)&&angle>=0&&angle<=360
                &&life>=1&&life<=120&&instance>=0&&(!(kind==12&&angle==2)||instance>0);
    }
    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)
                &&Math.abs(v.x)<=3.0E7&&Math.abs(v.y)<=3.0E7&&Math.abs(v.z)<=3.0E7;
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
