package com.myriad.network;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 英雄击杀顶栏播报。multiKill 为十秒窗口内的连续击杀数，最大显示五杀。 */
public record KillAnnouncementPayload(UUID killerId, String killerName, UUID victimId,
                                      String victimName, int multiKill) implements CustomPacketPayload {
    public static final Type<KillAnnouncementPayload> TYPE =
            new Type<>(Identifier.parse("myriad:kill_announcement"));
    public static final StreamCodec<FriendlyByteBuf, KillAnnouncementPayload> CODEC = StreamCodec.ofMember(
            KillAnnouncementPayload::write,
            buffer -> new KillAnnouncementPayload(buffer.readUUID(), buffer.readUtf(64),
                    buffer.readUUID(), buffer.readUtf(64), buffer.readVarInt()));

    private static void write(KillAnnouncementPayload payload, FriendlyByteBuf buffer) {
        buffer.writeUUID(payload.killerId());
        buffer.writeUtf(payload.killerName(), 64);
        buffer.writeUUID(payload.victimId());
        buffer.writeUtf(payload.victimName(), 64);
        buffer.writeVarInt(payload.multiKill());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
