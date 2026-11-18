package com.myriad.network;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Short-lived faction-scoped snapshots, never entity tracking. */
public record TeamSignalsPayload(String dimension,int faction,List<Entry> entries) implements CustomPacketPayload {
    public record Entry(long serial,UUID sender,String name,int kind,Vec3 position,int remaining) {}
    public TeamSignalsPayload { entries=List.copyOf(entries); }
    public static final int LIMIT=12,LIFE=100;
    public static final Type<TeamSignalsPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("myriad","team_signals"));
    public static final StreamCodec<FriendlyByteBuf,TeamSignalsPayload> CODEC=StreamCodec.ofMember(TeamSignalsPayload::write,TeamSignalsPayload::read);
    private void write(FriendlyByteBuf b){ b.writeUtf(dimension,256); b.writeVarInt(faction); b.writeVarInt(entries.size());
        for(var e:entries){ b.writeLong(e.serial); b.writeUUID(e.sender); b.writeUtf(e.name,64); b.writeVarInt(e.kind);
            b.writeDouble(e.position.x); b.writeDouble(e.position.y); b.writeDouble(e.position.z); b.writeVarInt(e.remaining); } }
    private static TeamSignalsPayload read(FriendlyByteBuf b){String dim=b.readUtf(256); int team=b.readVarInt(),count=b.readVarInt();
        if(team<0||team>2||count<0||count>LIMIT)throw new IllegalArgumentException("Invalid signal snapshot");
        var list=new ArrayList<Entry>(); for(int i=0;i<count;i++){var e=new Entry(b.readLong(),b.readUUID(),b.readUtf(64),b.readVarInt(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt());
            if(e.serial<=0||!com.myriad.game.TeamSignalKind.valid(e.kind)||!TeamSignalRequest.finite(e.position)||e.remaining<1||e.remaining>LIFE)
                throw new IllegalArgumentException("Invalid signal entry"); list.add(e);}
        return new TeamSignalsPayload(dim,team,list); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
