package com.myriad.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Development-only real-server mode transition checks. Never runs on a live map. */
public final class ArenaSwitchRegression {
    private ArenaSwitchRegression() { }
    private static Object field(Class<?> type, Object instance, String name) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(instance);
    }
    public static void run(GameTestHelper helper) {
        try {
            var server = helper.getLevel().getServer();
            Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            // Gradle test run only: the test world is separate from the user's Minecraft instance.
            if (!Boolean.getBoolean("myriad.arenaTests")) throw new IllegalStateException("Explicit test flag required");
            Path sample = Path.of(System.getProperty("myriad.arenaFixture"));
            var profile = root.resolve("myriad_arenas/3v3"); Files.createDirectories(profile);
            String[] files = {"myriad_lane_layout.json", "myriad_monster_spawns.json",
                    "myriad_brush_zones.json", "myriad_vein_spawns.json", "myriad_minimap.json"};
            for (String name : files) {
                Files.copy(sample.resolve(name), root.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.copy(sample.resolve("myriad_arenas/3v3").resolve(name), profile.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            GameManager.create(); var manager = GameManager.get(); manager.setServer(server);
            ModState.setEnabled(true);
            MatchArena.select(server, "5v5");
            LaneSystem.initialize(server); MonsterManager.initialize(server);
            VisionSystem.initialize(server); MapSystem.initialize(server);
            byte[] originalLayout = Files.readAllBytes(root.resolve(files[0]));
            var originalBase = LaneSystem.respawnPoint(Faction.YOUMING);
            var originalVeins = manager.spiritVeinSpawnSnapshot(false);
            var rollVeins = GameManager.class.getDeclaredMethod("rollSpiritVeins"); rollVeins.setAccessible(true);
            helper.assertTrue((boolean)rollVeins.invoke(manager), "Original 5v5 veins are configured");
            @SuppressWarnings("unchecked")
            var previousObjectives = new java.util.HashMap<Integer,Object>((Map<Integer,Object>)field(GameManager.class, manager, "spiritVeins"));
            MonsterManager.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), MonsterType.FROST_HARE);
            helper.assertTrue(!((Map<?,?>)field(MonsterManager.class, null, "STATES")).isEmpty(), "Previous arena NPC created");
            helper.assertTrue(MatchArena.select(server, "3v3"), "Server accepts 3v3 while waiting");
            helper.assertTrue(((Map<?,?>)field(MonsterManager.class, null, "STATES")).isEmpty(), "Switch clears NPC runtime");
            helper.assertTrue(LaneSystem.respawnPoint(Faction.YOUMING).equals(new BlockPos(4128,65,96)), "3v3 base replaces 5v5 base");
            helper.assertTrue(MonsterManager.spawnPointInfos().size() == 13, "Only 3v3 monster camps and boss pit active");
            helper.assertTrue(VisionSystem.brushInfos().size() == 12 && MapSystem.corner(1).getX() == 4096, "Grass and minimap switched together");
            var editor = MapFacilityEditor.snapshotEntries(server);
            helper.assertTrue(editor.stream().anyMatch(e -> e.category().equals("arena") && e.id().equals("3v3")),
                    "Facility editor explicitly identifies active 3v3 profile");
            for (var expected : Map.of("monster",13L,"tower",4L,"crystal",2L,"fountain",2L,"brush",12L,"map_bound",4L).entrySet()) {
                helper.assertTrue(editor.stream().filter(e -> e.category().equals(expected.getKey()) && e.firstSet()).count()==expected.getValue(),
                        "Editor exposes configured 3v3 " + expected.getKey());
            }
            helper.assertTrue(editor.stream().noneMatch(e -> e.category().contains("vein") || e.category().equals("lane_corner")),
                    "No disabled veins or inactive top/bottom corners in 3v3 editor");
            helper.assertTrue(editor.stream().filter(e -> e.category().equals("lane_path") && e.firstSet()).count()==6,
                    "Six configured single-lane route points");
            byte[] originalMonsterConfig = Files.readAllBytes(root.resolve(files[1]));
            byte[] duelMonsterConfig = Files.readAllBytes(profile.resolve(files[1]));
            MonsterManager.initialize(server);
            helper.assertTrue(MonsterManager.spawnPointInfos().size()==13, "All monster points survive disk reload");
            var crystal = LaneSystem.crystalPoint(Faction.YOUMING);
            helper.assertTrue(LaneSystem.setCrystalPoint(Faction.YOUMING, crystal.offset(1,0,0)), "3v3 crystal editable");
            LaneSystem.initialize(server);
            helper.assertTrue(LaneSystem.crystalPoint(Faction.YOUMING).equals(crystal.offset(1,0,0)), "Crystal position persisted and reloaded");
            LaneSystem.setCrystalPoint(Faction.YOUMING, crystal);
            helper.assertTrue(java.util.Arrays.equals(originalLayout, Files.readAllBytes(root.resolve(files[0]))),
                    "Editing 3v3 crystal preserves 5v5 layout");
            helper.assertTrue(java.util.Arrays.equals(originalMonsterConfig, Files.readAllBytes(root.resolve(files[1])))
                    && java.util.Arrays.equals(duelMonsterConfig, Files.readAllBytes(profile.resolve(files[1]))),
                    "Editor snapshots and reload do not rewrite existing monster configs");
            var camps = MapSystem.neutralCampMarkers();
            helper.assertTrue(camps.size() == 13 && camps.stream().filter(p -> p.kind() == 8).count() == 12
                    && camps.stream().filter(p -> p.kind() == 9).count() == 1,
                    "3v3 minimap marks twelve camps and one boss before spawning");
            helper.assertTrue(camps.stream().allMatch(p -> p.factionId() == 0 && p.x() >= 4096
                    && p.playerId().equals(new java.util.UUID(0,0))), "Neutral fixed markers expose no player identities");
            helper.assertTrue(MonsterManager.setSpawnPoint("outside_map_check", new BlockPos(0,65,0), MonsterType.FROST_HARE, false)
                    && MapSystem.neutralCampMarkers().size() == 13, "Out-of-map camp excluded");
            MonsterManager.removeSpawnPoint("outside_map_check");
            var facilities = LaneSystem.minimapUnits(helper.getLevel());
            helper.assertTrue(facilities.size() == 8 && facilities.stream().filter(p -> p.kind() == 4).count() == 4
                    && facilities.stream().filter(p -> p.kind() == 5).count() == 2
                    && facilities.stream().filter(p -> p.kind() == 10).count() == 2,
                    "Tower/crystal/fountain markers work without loaded building entities");
            @SuppressWarnings("unchecked")
            var destroyedTowers = (java.util.Set<String>)field(LaneSystem.class, null, "DESTROYED_TOWERS");
            destroyedTowers.add("youming/mid/1");
            helper.assertTrue(LaneSystem.minimapFacilities().stream().filter(p -> p.kind() == 4).count() == 3,
                    "Destroyed tower icon removed, not restored from layout");
            destroyedTowers.clear();
            helper.assertTrue(manager.spiritVeinSpawnSnapshot(false).isEmpty()
                    && manager.spiritVeinSpawnSnapshot(true).isEmpty(), "3v3 has no vein locations");
            helper.assertTrue((boolean)rollVeins.invoke(manager)
                    && ((Map<?,?>)field(GameManager.class, manager, "spiritVeins")).isEmpty(),
                    "Deliberately disabled veins do not spawn or count as missing configuration");
            helper.assertTrue(!(boolean)field(GameManager.class, manager, "spiritVeinsEnabled"), "3v3 disables vein system");
            @SuppressWarnings("unchecked")
            var activeObjectives = (Map<Integer,Object>)field(GameManager.class, manager, "spiritVeins");
            activeObjectives.putAll(previousObjectives);
            manager.setServer(server);
            helper.assertTrue(activeObjectives.isEmpty(), "Disabled map clears stale checkpoint objectives on server bind");
            helper.assertTrue(manager.setSpiritVeinSpawn(false, 1, null)
                    && !com.google.gson.JsonParser.parseString(Files.readString(profile.resolve(files[3])))
                    .getAsJsonObject().get("enabled").getAsBoolean(), "Editor saves preserve disabled state");
            helper.assertTrue(LaneSystem.minimapPaths().stream().allMatch(p -> p.lane() == LaneSystem.Lane.MID.ordinal()), "No inactive minimap lanes");
            helper.assertTrue(LaneSystem.setPathPoint(LaneSystem.Lane.MID,12,new BlockPos(4271,65,124)), "Active arena editable");
            helper.assertTrue(java.util.Arrays.equals(originalLayout, Files.readAllBytes(root.resolve(files[0]))), "Editing 3v3 never writes 5v5 layout");
            LaneSystem.clearPathPoint(LaneSystem.Lane.MID,12);
            var phase = GameManager.class.getDeclaredField("phase"); phase.setAccessible(true);
            for (var state : List.of(GameManager.Phase.PICKING, GameManager.Phase.PLAYING)) {
                phase.set(manager,state);
                helper.assertTrue(!MatchArena.select(server,"5v5") && MatchArena.active()==MatchArena.Mode.DUEL, "Cannot switch during draft or play");
            }
            phase.set(manager,GameManager.Phase.WAITING);
            var capacity = GameManager.class.getDeclaredMethod("arenaCapacityAllows",List.class); capacity.setAccessible(true);
            helper.assertTrue((boolean)capacity.invoke(manager,java.util.Collections.nCopies(6,null))
                    && !(boolean)capacity.invoke(manager,java.util.Collections.nCopies(7,null)), "3v3 capacity validated server-side");
            MatchArena.shutdown(); MatchArena.initialize(server);
            helper.assertTrue(MatchArena.active()==MatchArena.Mode.DUEL, "World selection persists across lifecycle reload");
            helper.assertTrue(MatchArena.select(server,"5v5"), "Switch back without changing world");
            helper.assertTrue(LaneSystem.respawnPoint(Faction.YOUMING).equals(originalBase), "Original 5v5 base restored");
            helper.assertTrue((boolean)field(GameManager.class, manager, "spiritVeinsEnabled")
                    && manager.spiritVeinSpawnSnapshot(false).equals(originalVeins), "5v5 retains its original veins");
            var legacyEditor = MapFacilityEditor.snapshotEntries(server);
            helper.assertTrue(legacyEditor.stream().filter(e -> e.category().equals("tower") && e.firstSet()).count()==18
                    && legacyEditor.stream().filter(e -> e.category().equals("attribute_vein")).count()==3,
                    "5v5 editor restores original eighteen towers and vein entries");
            helper.assertTrue(LaneSystem.minimapPaths().stream().map(LaneSystem.MinimapPathPoint::lane).distinct().count()==3,
                    "All original 5v5 lanes restored");
            helper.assertTrue(MapSystem.neutralCampMarkers().stream().allMatch(p -> p.x() < 4096),
                    "Switch back removes all 3v3 camp coordinates");
            helper.assertTrue(!MatchArena.select(server,"../3v3"), "Invalid mode cannot become file path");
            com.myriad.Myriad.LOGGER.info("ARENA_SWITCH_SERVER_PASS: wait/draft/play guards, NPC cleanup, facilities, editing, capacity, reload, 3v3 -> 5v5");
            helper.succeed();
        } catch (Exception error) { throw new RuntimeException(error); }
    }
}
