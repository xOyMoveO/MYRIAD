package com.myriad.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
public record RewardFloatPayload(double x,double y,double z,int gold,int xp) implements CustomPacketPayload {
    public static final Type<RewardFloatPayload> TYPE=new Type<>(Identifier.parse("myriad:reward_float"));
    public static final StreamCodec<FriendlyByteBuf,RewardFloatPayload> CODEC=StreamCodec.ofMember(
        RewardFloatPayload::write,b->new RewardFloatPayload(b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readVarInt()));
    private static void write(RewardFloatPayload p,FriendlyByteBuf b) {b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeVarInt(p.gold);b.writeVarInt(p.xp);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void send(ServerPlayer player,Entity killed,int gold,int xp) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new RewardFloatPayload(killed.getX(),killed.getY()+.8,killed.getZ(),gold,xp));
    }
}
