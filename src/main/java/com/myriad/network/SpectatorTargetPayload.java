package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端确认的死亡观战状态；相机实体仍由原版 setCamera 数据包权威同步。 */
public record SpectatorTargetPayload(boolean active, String targetName) implements CustomPacketPayload {
    public static final Type<SpectatorTargetPayload> TYPE =
            new Type<>(Identifier.parse("myriad:spectator_target"));
    public static final StreamCodec<FriendlyByteBuf, SpectatorTargetPayload> CODEC = StreamCodec.ofMember(
            SpectatorTargetPayload::write,
            buffer -> new SpectatorTargetPayload(buffer.readBoolean(), buffer.readUtf(64)));

    private static void write(SpectatorTargetPayload payload, FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.active());
        buffer.writeUtf(payload.targetName() == null ? "" : payload.targetName(), 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
