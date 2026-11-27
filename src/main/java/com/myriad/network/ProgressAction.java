package com.myriad.network;

import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.game.TrainingMode;
import com.myriad.player.PlayerDataProvider;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** One confirmation for the rank or price the player actually saw. */
public record ProgressAction(long sequence, String role, String id, int previousRank, long quote)
        implements CustomPacketPayload {
    public static final String ATTRIBUTE = "g1_attribute_";
    public static final Type<ProgressAction> TYPE = new Type<>(Identifier.parse("myriad:progress_action"));
    public static final StreamCodec<FriendlyByteBuf, ProgressAction> CODEC = StreamCodec.ofMember(
            (p, b) -> { b.writeVarLong(p.sequence); b.writeUtf(p.role); b.writeUtf(p.id);
                b.writeVarInt(p.previousRank); b.writeLong(p.quote); },
            b -> new ProgressAction(b.readVarLong(), b.readUtf(), b.readUtf(), b.readVarInt(), b.readLong()));
    @Override public Type<ProgressAction> type() { return TYPE; }

    public record Receipt(long sequence, boolean applied) implements CustomPacketPayload {
        public static final Type<Receipt> TYPE = new Type<>(Identifier.parse("myriad:progress_receipt"));
        public static final StreamCodec<FriendlyByteBuf, Receipt> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarLong(p.sequence); b.writeBoolean(p.applied); },
                b -> new Receipt(b.readVarLong(), b.readBoolean()));
        @Override public Type<Receipt> type() { return TYPE; }
    }

    private static final Map<ServerGamePacketListenerImpl, Long> LAST = new WeakHashMap<>();

    public void handle(ServerPlayer player) {
        boolean fresh = sequence > LAST.getOrDefault(player.connection, 0L);
        boolean applied = false;
        if (fresh) {
            LAST.put(player.connection, sequence);
            applied = apply(player);
        }
        // Deliver the authority's snapshot before acknowledging even a stale quote or a rejection.
        if (!applied) PlayerDataProvider.syncToClient(player);
        Packets.sendToPlayer(player, new Receipt(sequence, applied));
    }

    private boolean apply(ServerPlayer player) {
        var data = PlayerDataProvider.get(player);
        if (data == null || !role.equals(data.getCharacterId()) || !FirstGroupSkills.active(role)) return false;
        if (id.startsWith(ATTRIBUTE)) {
            String attribute = id.substring(ATTRIBUTE.length());
            if (!FirstGroupGrowth.ATTRIBUTES.contains(attribute)) return false;
            int rank = data.firstGroupAttribute(attribute);
            long price = TrainingMode.isNoGoldCost(player) ? 0 : FirstGroupGrowth.price(rank);
            if (rank != previousRank || price != quote) {
                player.sendSystemMessage(Component.translatable("message.myriad.quote_changed", price));
                return false;
            }
            return FirstGroupGrowth.buy(player, data, attribute);
        }
        var node = FirstGroupSkills.byId(id);
        if (node == null || !role.equals(node.role()) || data.skillLevel(id) != previousRank) return false;
        PlayerDataProvider.upgradeSkill(player, id);
        return data.skillLevel(id) > previousRank;
    }
}
