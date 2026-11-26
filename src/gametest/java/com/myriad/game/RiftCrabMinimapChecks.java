package com.myriad.game;

import com.mojang.authlib.GameProfile;
import com.myriad.combat.DamageCalc;
import com.myriad.network.NetworkSetup.MinimapSnapshotPayload;
import com.myriad.network.RiftCrabMarker;
import com.myriad.network.RiftCrabMarker.Status;
import com.myriad.player.PlayerDataProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class RiftCrabMinimapChecks {
    private record Viewer(ServerPlayer player, List<MinimapSnapshotPayload> snapshots) {
        List<RiftCrabMarker> crabs() { return snapshots.getLast().crabs(); }
        RiftCrabMarker crab() { return crabs().getFirst(); }
    }

    static void run(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        var clock = (ServerLevelData) level.getLevelData();
        long savedTime = level.getGameTime();
        boolean savedEnabled = ModState.isEnabled();
        var savedManager = GameManager.snapshot();
        var savedMonsters = MonsterManager.snapshot();
        Path campFile = MatchArena.configPath(server, "myriad_monster_spawns.json");
        Path mapFile = MatchArena.configPath(server, "myriad_minimap.json");
        var viewers = new ArrayList<Viewer>();
        try {
            byte[] campBackup = read(campFile), mapBackup = read(mapFile);
            try {
                Files.writeString(campFile, "{\"points\":[]}");
                Files.writeString(mapFile, "{\"corners\":[]}");
                MonsterManager.reloadArena(server);
                MapSystem.initialize(server);
                GameManager.create();
                GameManager.get().setServer(server);
                ModState.setEnabled(true);
                BlockPos camp = h.absolutePos(new BlockPos(3, 8, 3));
                for (int i = 0; i < 4; i++) {
                    int dx = i == 1 || i == 2 ? 45 : -45;
                    MapSystem.setCorner(i + 1, camp.offset(dx, 0, i < 2 ? -45 : 45));
                }
                MonsterManager.setSpawnPoint("crab", camp, MonsterType.CHERRY_RIFT_CRAB, false);
                MonsterManager.setSpawnPoint("hare", camp.offset(18, 0, 0), MonsterType.CHERRY_FROST_HARE, false);
                MonsterManager.setSpawnPoint("medium", camp.offset(-12, 0, 0), MonsterType.CHERRY_JADE_BEAST, false);
                MonsterManager.setSpawnPoint("random", camp.offset(26, 0, 0), null, true);
                Vec3 near = Vec3.atBottomCenterOf(camp).add(0, 0, 5), far = near.add(0, 0, 65);
                var blue = viewer(level, new GameProfile(UUID.randomUUID(), "CrabBlue"), near, Faction.GUARDIAN);
                var red = viewer(level, new GameProfile(UUID.randomUUID(), "CrabRed"), far, Faction.DESTRUCTION);
                viewers.add(blue);
                viewers.add(red);
                long start = 20L * (savedTime / 20 + 1);
                var match = GameManager.snapshot().getAsJsonObject();
                match.addProperty("phase", "PLAYING");
                match.addProperty("tempo", "CHERRY_V1");
                match.addProperty("matchStartTime", start);
                var factions = match.getAsJsonObject("factions");
                factions.addProperty(blue.player.getUUID().toString(), "guardian");
                factions.addProperty(red.player.getUUID().toString(), "destruction");
                GameManager.restore(match, server);
                clock.setGameTime(start);
                update(level);
                h.assertTrue(blue.crabs().size() == 1 && blue.crab().campId().equals("crab")
                        && blue.crab().status() == Status.WAITING && blue.crab().equals(red.crab()),
                        "Only the fixed crab has a public first spawn");
                long first = blue.crab().spawnAt();
                h.assertTrue(first == start + 4800, "Cherry profile schedules first crab four minutes from combat");
                checkNormalCamps(h, clock, start, camp, blue.player());
                clock.setGameTime(first - 1);
                update(level);
                h.assertTrue(blue.crab().secondsLeft(first - 1) == 1
                        && level.getEntitiesOfClass(CherryRiftMonsterEntity.class, new AABB(camp).inflate(4)).isEmpty(),
                        "Last tick is rounded up and the camp is still empty");
                clock.setGameTime(first);
                update(level);
                h.assertTrue(blue.crab().status() == Status.ALIVE && red.crab().status() == Status.UNKNOWN,
                        "Actual spawn is alive in sight and unknown out of sight");
                var hidden = red.crab();
                var crab = level.getEntitiesOfClass(CherryRiftMonsterEntity.class, new AABB(camp).inflate(4)).getFirst();
                DamageCalc.dealDamage(blue.player, crab, 100_000, DamageCalc.Kind.TRUE);
                MapSystem.tick(level);
                var pending = MonsterManager.snapshot().getAsJsonArray("respawns").get(0).getAsJsonObject();
                long deadline = pending.get("at").getAsLong();
                h.assertTrue(deadline == first + 4800, "Crab respawn is four minutes after actual death");
                h.assertTrue(!crab.isAlive() && blue.crab().status() == Status.WAITING && blue.crab().spawnAt() == deadline
                        && red.crab().equals(hidden) && hidden.spawnAt() == 0,
                        "Killer receives scheduler time; hidden team's whole marker stays identical");
                var stand = level.getEntitiesOfClass(ArmorStand.class, new AABB(camp).inflate(4)).getFirst();
                h.assertTrue(stand.broadcastToPlayer(blue.player) && !stand.broadcastToPlayer(red.player),
                        "Vanilla tracking also withholds the exact world countdown");
                var saved = MonsterManager.snapshot();
                MonsterManager.restore(level, saved);
                var profile = blue.player.getGameProfile();
                level.removePlayerImmediately(blue.player, Entity.RemovalReason.DISCARDED);
                viewers.remove(blue);
                blue = viewer(level, profile, far, Faction.GUARDIAN);
                viewers.add(blue);
                MapSystem.tick(level);
                h.assertTrue(blue.crab().spawnAt() == deadline && red.crab().equals(hidden),
                        "Reconnected teammate retains knowledge after checkpoint load");
                var legacy = saved.deepCopy();
                legacy.getAsJsonArray("respawns").get(0).getAsJsonObject().remove("seenBy");
                MonsterManager.restore(level, legacy);
                MapSystem.tick(level);
                h.assertTrue(blue.crab().equals(hidden) && red.crab().equals(hidden),
                        "Legacy checkpoint grants no unobserved respawn");
                red.player.setPos(near);
                clock.setGameTime(first + 20);
                update(level);
                h.assertTrue(red.crab().spawnAt() == deadline && blue.crab().equals(hidden),
                        "Scouting the camp's timer reveals it to the scouting team");
                clock.setGameTime(deadline);
                update(level);
                h.assertTrue(red.crab().status() == Status.ALIVE && blue.crab().equals(hidden)
                        && hidden.secondsLeft(deadline + 99) == 0,
                        "Respawn is confirmed by the live entity and time never becomes negative");
                long nextStart = deadline + 100;
                match.addProperty("matchStartTime", nextStart);
                GameManager.restore(match, server);
                clock.setGameTime(nextStart);
                update(level);
                h.assertTrue(blue.crab().status() == Status.WAITING && blue.crab().equals(red.crab())
                        && blue.crab().spawnAt() == nextStart + first - start, "New match starts a fresh public timer");
                match.remove("tempo");
                match.addProperty("matchStartTime", nextStart + 1);
                GameManager.restore(match, server);
                clock.setGameTime(nextStart + 1 + 5400);
                update(level);
                h.assertTrue(blue.crab().status() == Status.WAITING && blue.crab().spawnAt() == nextStart + 1 + 7200,
                        "Legacy 4:30 checkpoint keeps its six-minute unopened camp");
                MonsterManager.removeSpawnPoint("crab");
                MapSystem.tick(level);
                h.assertTrue(blue.crabs().isEmpty() && red.crabs().isEmpty(), "Maps without a fixed crab send no crab state");
            } finally {
                for (Viewer viewer : viewers) level.removePlayerImmediately(viewer.player, Entity.RemovalReason.DISCARDED);
                clock.setGameTime(savedTime);
                restore(campFile, campBackup);
                restore(mapFile, mapBackup);
                MapSystem.initialize(server);
                MonsterManager.reloadArena(server);
                GameManager.restore(savedManager, server);
                MonsterManager.restore(level, savedMonsters);
                ModState.setEnabled(savedEnabled);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        System.out.println("[MYRIAD-RIFT-CRAB-MINIMAP] PASS: fixed camp, real schedule and kill, fog-safe packets and world label, scouting, reconnect, old/new checkpoints and match reset");
        h.succeed();
    }

    private static void checkNormalCamps(GameTestHelper h, ServerLevelData clock, long start, BlockPos camp,
            ServerPlayer killer) {
        var level = h.getLevel();
        var area = new AABB(camp).inflate(30);
        clock.setGameTime(start + 819);
        update(level);
        var camps = level.getEntitiesOfClass(CherryRiftMonsterEntity.class, area, Entity::isAlive);
        h.assertTrue(camps.size() == 2, "Only small and medium camps are present at 0:40.95");
        for (var mob : camps) DamageCalc.dealDamage(killer, mob, 100000, DamageCalc.Kind.TRUE);
        var pending = MonsterManager.snapshot().getAsJsonArray("respawns");
        var deadlines = new java.util.HashSet<Long>();
        pending.forEach(row -> deadlines.add(row.getAsJsonObject().get("at").getAsLong() - start));
        h.assertTrue(deadlines.equals(java.util.Set.of(2319L, 2819L)),
                "Both timers start at the actual off-second death tick");
        for (long[] step : new long[][]{{2318,0},{2319,0},{2320,1},{2818,1},{2819,1},{2820,2},{2840,2}}) {
            clock.setGameTime(start + step[0]);
            update(level);
            var living = level.getEntitiesOfClass(CherryRiftMonsterEntity.class, area, Entity::isAlive);
            h.assertTrue(living.size() == step[1], "Small/medium return on first whole-second check at " + step[0]);
            if (step[1] == 1) h.assertTrue(living.getFirst().variant() == MonsterType.CHERRY_FROST_HARE,
                    "Small camp returns before medium camp");
        }
    }

    private static void update(ServerLevel level) {
        MonsterManager.tick(level);
        MapSystem.tick(level);
    }

    private static Viewer viewer(ServerLevel level, GameProfile profile, Vec3 position, Faction team) {
        var server = level.getServer();
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var snapshots = new ArrayList<MinimapSnapshotPayload>();
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket p && p.payload() instanceof MinimapSnapshotPayload map) {
                    snapshots.add(map);
                }
            }
        };
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setPos(position);
        PlayerDataProvider.get(player).setFaction(team);
        PlayerDataProvider.get(player).setCharacterId("shuo");
        level.addNewPlayer(player);
        return new Viewer(player, snapshots);
    }

    private static byte[] read(Path path) throws java.io.IOException {
        return Files.exists(path) ? Files.readAllBytes(path) : null;
    }

    private static void restore(Path path, byte[] data) throws java.io.IOException {
        if (data == null) Files.deleteIfExists(path);
        else Files.write(path, data);
    }
}
