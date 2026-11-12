package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 己方防御塔被摧毁时发送给防守方的三秒 HUD 警报。 */
public record TowerAlertPayload(String laneId, int tier) implements CustomPacketPayload {
    public static final Type<TowerAlertPayload> TYPE =
            new Type<>(Identifier.parse("myriad:tower_alert"));
    public static final StreamCodec<FriendlyByteBuf, TowerAlertPayload> CODEC = StreamCodec.ofMember(
            TowerAlertPayload::write,
            buffer -> new TowerAlertPayload(buffer.readUtf(16), buffer.readVarInt()));

    private static void write(TowerAlertPayload payload, FriendlyByteBuf buffer) {
        buffer.writeUtf(payload.laneId(), 16);
        buffer.writeVarInt(payload.tier());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
