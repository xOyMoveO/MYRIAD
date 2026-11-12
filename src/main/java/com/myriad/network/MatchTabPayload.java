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
            MatchTabPayload::write, b -> new MatchTabPayload(b.readLong(), b.readList(v -> new Entry(
                    v.readUUID(), v.readUtf(), v.readUtf(), v.readVarInt(), v.readVarInt(), v.readVarInt(), v.readVarInt(), v.readVarInt()))));
    private static void write(MatchTabPayload p, FriendlyByteBuf b) {
        b.writeLong(p.elapsedTicks);
        b.writeCollection(p.players, (v,e) -> { v.writeUUID(e.id); v.writeUtf(e.name); v.writeUtf(e.character);
            v.writeVarInt(e.faction); v.writeVarInt(e.level); v.writeVarInt(e.kills); v.writeVarInt(e.deaths); v.writeVarInt(e.gold); });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void sync(MinecraftServer server) {
        GameManager gm = GameManager.get();
        var entries = server.getPlayerList().getPlayers().stream().map(p -> {
            var d = PlayerDataProvider.get(p);
            return new Entry(p.getUUID(), p.getName().getString(), d == null || d.getCharacterId() == null ? "" : d.getCharacterId(),
                    NetworkSetup.factionId(gm == null ? null : gm.factionOf(p)), d == null ? 1 : d.getLevel(),
                    gm == null ? 0 : gm.killsOf(p), gm == null ? 0 : gm.deathsOf(p),
                    d == null ? 0 : d.getMatchGoldEarned());
        }).toList();
        var packet = new MatchTabPayload(gm == null ? -1 : gm.getMatchElapsedTicks(server.overworld().getGameTime()), entries);
        for (var player : server.getPlayerList().getPlayers()) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }
}
