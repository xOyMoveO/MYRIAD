package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record TeamSignalRequest(int kind, Vec3 position) implements CustomPacketPayload {
    public static final Type<TeamSignalRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","team_signal_request"));
    public static final StreamCodec<FriendlyByteBuf,TeamSignalRequest> CODEC=StreamCodec.ofMember(
            (p,b)->{b.writeVarInt(p.kind); b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z);},
            b->new TeamSignalRequest(b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    public static boolean finite(Vec3 p) { return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)
            &&Math.abs(p.x)<=29999984&&Math.abs(p.z)<=29999984&&Math.abs(p.y)<2048; }
    public boolean valid() { return com.myriad.game.TeamSignalKind.valid(kind)&&finite(position); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
