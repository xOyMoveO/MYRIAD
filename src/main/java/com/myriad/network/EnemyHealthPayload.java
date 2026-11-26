package com.myriad.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** A complete health and public control sample for one observer. */
public record EnemyHealthPayload(Identifier dimension, long sequence, List<Entry> entries) implements CustomPacketPayload {
    public static final double DISPLAY_DISTANCE = 20.0;
    public static boolean inRange(double distanceSquared) { return distanceSquared <= DISPLAY_DISTANCE * DISPLAY_DISTANCE; }

    public record Controls(int stun, int silence, int root) {
        public static final Controls NONE = new Controls(0, 0, 0);
        public boolean active() { return stun > 0 || silence > 0 || root > 0; }
    }

    public record Entry(int entityId, UUID playerId, float health, float maximum, boolean ally, float shield,
                        Controls controls) {
        private static Entry read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            UUID player = buf.readUUID();
            float health = buf.readFloat(), maximum = buf.readFloat();
            boolean ally = buf.readBoolean();
            float shield = buf.readFloat();
            Controls controls = buf.readBoolean() ? new Controls(ControlStatusPayload.readTicks(buf),
                    ControlStatusPayload.readTicks(buf), ControlStatusPayload.readTicks(buf)) : Controls.NONE;
            return new Entry(id, player, health, maximum, ally, shield, controls);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeUUID(playerId);
            buf.writeFloat(health);
            buf.writeFloat(maximum);
            buf.writeBoolean(ally);
            buf.writeFloat(shield);
            buf.writeBoolean(controls.active());
            if (controls.active()) {
                buf.writeVarInt(controls.stun);
                buf.writeVarInt(controls.silence);
                buf.writeVarInt(controls.root);
            }
        }
    }

    public static int shieldPixels(float shield, float maximum, int width) {
        return shield > 0 && width > 0 ? Math.clamp(Math.round(width * Math.min(1, shield / Math.max(1, maximum))), 1, width) : 0;
    }

    public static final Type<EnemyHealthPayload> TYPE = new Type<>(Identifier.parse("myriad:enemy_health"));
    public static final StreamCodec<FriendlyByteBuf, EnemyHealthPayload> CODEC = StreamCodec.of((buf, sample) -> {
        buf.writeIdentifier(sample.dimension);
        buf.writeVarLong(sample.sequence);
        PayloadBuffers.writeCollection(buf, sample.entries, (out, entry) -> entry.write(out));
    }, buf -> new EnemyHealthPayload(buf.readIdentifier(), buf.readVarLong(), PayloadBuffers.readList(buf, Entry::read)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
