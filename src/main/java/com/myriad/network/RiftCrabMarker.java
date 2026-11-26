package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;

/** The fixed camp and only the information its recipient's team currently knows. */
public record RiftCrabMarker(String campId, float x, float z, Status status, long spawnAt) {
    public enum Status { UNKNOWN, WAITING, ALIVE }

    static void write(FriendlyByteBuf buf, RiftCrabMarker marker) {
        buf.writeUtf(marker.campId, 32);
        buf.writeFloat(marker.x);
        buf.writeFloat(marker.z);
        buf.writeEnum(marker.status);
        if (marker.status == Status.WAITING) buf.writeVarLong(marker.spawnAt);
    }

    static RiftCrabMarker read(FriendlyByteBuf buf) {
        String id = buf.readUtf(32);
        float x = buf.readFloat();
        float z = buf.readFloat();
        Status status = buf.readEnum(Status.class);
        return new RiftCrabMarker(id, x, z, status, status == Status.WAITING ? buf.readVarLong() : 0);
    }

    public long secondsLeft(long gameTime) {
        return Math.ceilDiv(Math.max(0, spawnAt - gameTime), 20L);
    }
}
