package com.myriad.network;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerDataProvider;

public record MatchTabPayload(long elapsedTicks, List<Entry> players) implements CustomPacketPayload {
    public record Entry(UUID id, String name, String character, int faction, int level, int kills, int deaths, int gold) {}
    public static final Type<MatchTabPayload> TYPE = new Type<>(Identifier.parse("myriad:match_tab"));
    public static final StreamCodec<FriendlyByteBuf, MatchTabPayload> CODEC = StreamCodec.ofMember(
            MatchTabPayload::write, b -> new MatchTabPayload(b.readLong(), PayloadBuffers.readList(b, v -> new Entry(
                    v.readUUID(), v.readUtf(), v.readUtf(), v.readVarInt(), v.readVarInt(), v.readVarInt(), v.readVarInt(), v.readVarInt()))));
    private static void write(MatchTabPayload p, FriendlyByteBuf b) {
        b.writeLong(p.elapsedTicks);
        PayloadBuffers.writeCollection(b, p.players, (v,e) -> { v.writeUUID(e.id); v.writeUtf(e.name); v.writeUtf(e.character);
            v.writeVarInt(e.faction); v.writeVarInt(e.level); v.writeVarInt(e.kills); v.writeVarInt(e.deaths); v.writeVarInt(e.gold); });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void sync(MinecraftServer server) {
        GameManager gm = GameManager.get();
        List<Entry> entries;
        if (gm != null && gm.isQueuedMatch()) {
            entries = gm.queuePickOrder().stream().map(id -> {
                var online = server.getPlayerList().getPlayer(id);
                if (online != null) return entry(online, gm);
                return new Entry(id, gm.queueName(id), gm.queuePickedRole(id),
                        NetworkSetup.factionId(gm.queueFaction(id)), 0, 0, 0, 0);
            }).toList();
        } else {
            entries = server.getPlayerList().getPlayers().stream().map(p -> entry(p, gm)).toList();
        }
        var packet = new MatchTabPayload(gm == null ? -1 : gm.getMatchElapsedTicks(server.overworld().getGameTime()), entries);
        for (var player : server.getPlayerList().getPlayers()) {
            if (gm == null || !gm.isQueuedMatch() || gm.isParticipant(player.getUUID())) Packets.sendToPlayer(player, packet);
        }
    }
    private static Entry entry(net.minecraft.server.level.ServerPlayer player, GameManager gm) {
        var d = PlayerDataProvider.get(player);
        return new Entry(player.getUUID(), player.getName().getString(), d == null || d.getCharacterId() == null ? "" : d.getCharacterId(),
                NetworkSetup.factionId(gm == null ? null : gm.factionOf(player)), d == null ? 1 : d.getLevel(),
                gm == null ? 0 : gm.killsOf(player), gm == null ? 0 : gm.deathsOf(player),
                d == null ? 0 : d.getMatchGoldEarned());
    }

}
