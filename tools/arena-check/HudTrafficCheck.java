package com.myriad.check;

import com.myriad.network.BattleSnapshots;
import com.myriad.network.MatchTabPayload;
import com.myriad.network.NetworkSetup.MinimapMarker;
import com.myriad.network.NetworkSetup.MinimapPathPoint;
import com.myriad.network.NetworkSetup.MinimapSnapshotPayload;
import com.myriad.network.NetworkSetup.PlayerDataPayload;
import com.myriad.network.RiftCrabMarker;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Deterministic uncompressed payload bytes; run with the matching production codecs on the classpath. */
public final class HudTrafficCheck {
    private static final int TICKS = 400;
    private static final List<String> ROLES = List.of("yingxiao", "tianxing", "fenjue", "xuanchen", "sulan",
            "lingyuan", "hanze", "qingluo", "chixiao", "yunsheng", "shuo", "keheng", "yike", "rivet", "luoxia");
    private static final Map<String, Double> ATTRIBUTES = attributes();
    private static final UUID NO_PLAYER = new UUID(0, 0);
    private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");

    public static void main(String[] args) {
        boolean baseline = List.of(args).contains("--baseline");
        run("lobby", 0, 2, baseline);
        run("3v3", 6, 0, baseline);
        run("5v5+4", 10, 4, baseline);
        if (!baseline) checkDelivery();
    }

    private static void run(String name, int participants, int waiting, boolean baseline) {
        int viewers = participants + waiting;
        BattleSnapshots[] recipients = new BattleSnapshots[viewers];
        for (int i = 0; i < viewers; i++) recipients[i] = new BattleSnapshots();
        Traffic hud = new Traffic(), tab = new Traffic(), map = new Traffic();
        for (int tick = 0; tick < TICKS; tick++) {
            var roster = tab(participants, viewers, tick);
            for (int i = 0; i < viewers; i++) {
                var recipient = recipients[i];
                var owner = hud(participants, i, tick, baseline);
                if (baseline || recipient.sendHud(owner, tick, false)) hud.count(PlayerDataPayload.STREAM_CODEC, owner);
                if ((participants == 0 || i < participants) && (baseline || recipient.sendTab(roster)))
                    tab.count(MatchTabPayload.CODEC, roster);
                var minimap = map(participants, i, tick);
                if (baseline || recipient.sendMap(minimap)) map.count(MinimapSnapshotPayload.STREAM_CODEC, minimap);
            }
        }
        System.out.printf("%s %s ticks=%d HUD=%s Tab=%s minimap=%s totalBytes=%d%n",
                baseline ? "baseline" : "current", name, TICKS, hud, tab, map, hud.bytes + tab.bytes + map.bytes);
    }

    private static PlayerDataPayload hud(int seats, int i, int tick, boolean baseline) {
        boolean playing = i < seats;
        var taken = new HashMap<String, Boolean>();
        for (int j = 0; j < ROLES.size(); j++) {
            boolean occupied = j < seats && j != i;
            if (baseline || occupied) taken.put(ROLES.get(j), occupied);
        }
        return new PlayerDataPayload(1, 0, 100, playing ? 500 + tick / 20 : 0, 120, 0,
                playing ? 1 + i % 2 : 0, 0, 0, playing ? 1 : 0, playing ? ROLES.get(i) : "",
                0, 0, 0, 0, 0, false, false, false, false, Map.of(), taken, 1, 0,
                Map.of(), false, 0, true, playing ? 36000 - tick : -1, List.of("", "", "", "", "", ""),
                ATTRIBUTES, 0, 0, 0, 1, 600, 600, 0, 20, i + 100, OVERWORLD, playing, false);
    }

    private static Map<String,Double> attributes() {
        var values = new HashMap<String,Double>();
        values.put("attribute.name.max_health", 600d);
        values.put("attribute.name.movement_speed", .1);
        for (String key : List.of("physical_attack", "physical_defense", "magic_resistance", "attack_speed",
                "spell_power", "crit_chance", "crit_damage", "physical_lifesteal", "tenacity", "flat_armor_pen",
                "percent_armor_pen", "flat_magic_pen", "percent_magic_pen", "max_mana", "mana_regen", "cooldown_reduction",
                "health_regen", "spell_lifesteal")) values.put("attribute.myriad." + key, 1d);
        return Map.copyOf(values);
    }

    private static MatchTabPayload tab(int seats, int viewers, int tick) {
        List<MatchTabPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < (seats == 0 ? viewers : seats); i++) {
            entries.add(new MatchTabPayload.Entry(new UUID(0, i + 1), "Player" + i, seats == 0 ? "" : ROLES.get(i),
                    seats == 0 ? 0 : 1 + i % 2, 1, 0, 0, 0, seats == 0 ? 0 : 500 + tick / 20));
        }
        return new MatchTabPayload(seats == 0 ? -1 : tick, entries);
    }

    private static MinimapSnapshotPayload map(int seats, int viewer, int tick) {
        if (viewer >= seats) return new MinimapSnapshotPayload(false, 0,0,0,0,0,0,0,0, List.of(), List.of(), List.of());
        var routes = new ArrayList<MinimapPathPoint>();
        int lanes = seats == 6 ? 1 : 3;
        for (int lane = 0; lane < lanes; lane++) {
            for (int j = 1; j <= 6; j++) routes.add(new MinimapPathPoint(lane, j, j * 5f, lane * 3f));
        }
        var markers = new ArrayList<MinimapMarker>();
        for (int j = 0; j < 13; j++) markers.add(new MinimapMarker(8, 0, j, 1f, NO_PLAYER));
        for (int j = viewer % 2; j < seats; j += 2)
            markers.add(new MinimapMarker(j == viewer ? 1 : 0, 1 + viewer % 2, j, 2f, new UUID(0, j + 1)));
        for (int j = 0; j < 12 * lanes; j++)
            markers.add(new MinimapMarker(2, 1 + j % 2, j, tick / 20f, NO_PLAYER));
        return new MinimapSnapshotPayload(true,0,0,100,0,100,100,0,100,
                routes, markers, List.of(new RiftCrabMarker("river", 40, 40, RiftCrabMarker.Status.ALIVE, 0)));
    }

    private static void checkDelivery() {
        BattleSnapshots cache = new BattleSnapshots();
        var empty = map(0, 0, 0);
        var populated = map(6, 0, 0);
        require(cache.sendMap(empty) && !cache.sendMap(empty) && cache.sendMap(populated) && cache.sendMap(empty),
                "map first, unchanged, configured, then cleared");
        var owner = hud(0, 0, 0, false);
        require(cache.sendHud(owner, 0, false) && !cache.sendHud(owner, 1, false)
                && cache.sendHud(owner, 5, false) && cache.sendHud(owner, 5, true), "HUD heartbeat and explicit refresh");
        require(cache.sendTab(tab(6, 6, 0)) && !cache.sendTab(tab(6, 6, 1)) && cache.sendTab(tab(6, 6, 20)),
                "Tab clock changes at displayed second");
        var scores = new ArrayList<>(tab(6, 6, 20).players());
        var original = scores.getFirst();
        scores.set(0, new MatchTabPayload.Entry(original.id(), original.name(), original.character(),
                original.faction(), original.level(), 1, 0, 0, original.gold()));
        require(cache.sendTab(new MatchTabPayload(21, scores)), "Tab score changes within a displayed second");
        require(new BattleSnapshots().sendMap(empty), "fresh delivery cache sends even an empty first map");
        System.out.println("delivery and all three codec round trips passed");
    }

    private static void require(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
    }

    private static final class Traffic {
        int count;
        long bytes;

        <T> void count(StreamCodec<FriendlyByteBuf,T> codec, T payload) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                codec.encode(buffer, payload);
                bytes += buffer.readableBytes();
                if (count++ == 0) require(codec.decode(buffer).equals(payload) && !buffer.isReadable(), "wire round trip");
            } finally {
                buffer.release();
            }
        }
        @Override public String toString() { return count + " packets/" + bytes + " bytes"; }
    }
}
