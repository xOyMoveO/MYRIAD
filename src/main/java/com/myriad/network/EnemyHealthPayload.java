package com.myriad.network;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Observer-specific hero health snapshot: never send hidden opponents' health. */
public record EnemyHealthPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final double DISPLAY_DISTANCE = 20.0;
    public static boolean inRange(double distanceSquared) { return distanceSquared <= DISPLAY_DISTANCE * DISPLAY_DISTANCE; }
    public record Entry(int entityId, float health, float maximum, boolean ally) {}
    public static final Type<EnemyHealthPayload> TYPE = new Type<>(Identifier.parse("myriad:enemy_health"));
    public static final StreamCodec<FriendlyByteBuf, EnemyHealthPayload> CODEC = StreamCodec.ofMember(
            EnemyHealthPayload::write, b -> new EnemyHealthPayload(b.readList(
                    buf -> new Entry(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean()))));
    private static void write(EnemyHealthPayload p, FriendlyByteBuf b) {
        b.writeCollection(p.entries, (buf, e) -> {
            buf.writeVarInt(e.entityId); buf.writeFloat(e.health); buf.writeFloat(e.maximum);
            buf.writeBoolean(e.ally);
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
