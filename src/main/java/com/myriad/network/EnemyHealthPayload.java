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
    public record Entry(int entityId, float health, float maximum, boolean ally, float shield) {}
    public static int shieldPixels(float shield, float maximum, int width) {
        return shield > 0 && width > 0 ? Math.clamp(Math.round(width * Math.min(1, shield / Math.max(1, maximum))), 1, width) : 0;
    }
    public static final Type<EnemyHealthPayload> TYPE = new Type<>(Identifier.parse("myriad:enemy_health"));
    public static final StreamCodec<FriendlyByteBuf, EnemyHealthPayload> CODEC = StreamCodec.ofMember(
            EnemyHealthPayload::write, b -> new EnemyHealthPayload(PayloadBuffers.readList(b,
                    buf -> new Entry(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat()))));
    private static void write(EnemyHealthPayload p, FriendlyByteBuf b) {
        PayloadBuffers.writeCollection(b, p.entries, (buf, e) -> {
            buf.writeVarInt(e.entityId); buf.writeFloat(e.health); buf.writeFloat(e.maximum);
            buf.writeBoolean(e.ally); buf.writeFloat(e.shield);
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
