package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Actor-only, authoritative UI outcomes and changing shop eligibility. */
public record UiFeedbackPayload(int kind) implements CustomPacketPayload {
    public static final Type<UiFeedbackPayload> TYPE = new Type<>(Identifier.parse("myriad:ui_feedback"));
    public static final StreamCodec<FriendlyByteBuf, UiFeedbackPayload> CODEC = StreamCodec.ofMember(
            (p,b) -> b.writeVarInt(p.kind), b -> new UiFeedbackPayload(b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void levelUp(ServerPlayer p) { Packets.sendToPlayer(p,new UiFeedbackPayload(0)); }
    public static void purchase(ServerPlayer p, boolean success) { Packets.sendToPlayer(p,new UiFeedbackPayload(success ? 1 : 2)); }
    public static void shopAccess(ServerPlayer p, ShopAccess access) {
        Packets.sendToPlayer(p, new UiFeedbackPayload(access.kind()));
    }

    public record ShopAccess(boolean attributes, boolean supplies, boolean equipment) {
        private static final int FIRST_KIND = 7;
        public static final ShopAccess NONE = new ShopAccess(false, false, false);

        public int kind() {
            return FIRST_KIND + (attributes ? 1 : 0) + (supplies ? 2 : 0) + (equipment ? 4 : 0);
        }

        public static ShopAccess fromKind(int kind) {
            int flags = kind - FIRST_KIND;
            return flags < 0 || flags > 7 ? null
                    : new ShopAccess((flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
        }
    }
}
