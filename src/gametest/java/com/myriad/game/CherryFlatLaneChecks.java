package com.myriad.game;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/** Synthetic route and JSON round trip; never launches a real map. */
final class CherryFlatLaneChecks {
    static void run(GameTestHelper h) {
        var server=h.getLevel().getServer();
        Path root=server.getWorldPath(LevelResource.ROOT);
        Path profile=root.resolve("myriad_arenas/3v3");
        Path lane=profile.resolve("myriad_lane_layout.json");
        Path selection=root.resolve("myriad_arena_selection.json");
        var before=new LinkedHashMap<Path,byte[]>();
        try {
            var layout="""
                    {"enabledLanes":["mid"],"towerTiers":[1,3],"manualPaths":["mid"],
                     "paths":{"mid":[{"slot":1,"x":-20,"y":81,"z":6},{"slot":2,"x":0,"y":81,"z":0},{"slot":3,"x":20,"y":81,"z":-6}]},
                     "towers":[
                       {"faction":"guardian","lane":"mid","tier":1,"x":-8,"y":83,"z":0,"minionPlatformRadius":5.5},
                       {"faction":"guardian","lane":"mid","tier":3,"x":-16,"y":84,"z":0},
                       {"faction":"destruction","lane":"mid","tier":1,"x":8,"y":83,"z":0},
                       {"faction":"destruction","lane":"mid","tier":3,"x":16,"y":84,"z":0}],
                     "fountains":[{"faction":"guardian","x":-35,"y":81,"z":0},{"faction":"destruction","x":35,"y":81,"z":0}],
                     "crystals":[{"faction":"guardian","x":-29,"y":85,"z":0},{"faction":"destruction","x":29,"y":85,"z":0}]}
                    """;
            var json=JsonParser.parseString(layout).getAsJsonObject();
            set(before,selection,"{\"mode\":\"3v3\"}");
            set(before,lane,layout);
            set(before,profile.resolve("myriad_brush_zones.json"),"{\"brushes\":[]}");
            set(before,profile.resolve("myriad_vein_spawns.json"),"{\"enabled\":false}");
            set(before,profile.resolve("myriad_monster_spawns.json"),"{\"points\":[]}");
            set(before,profile.resolve("myriad_minimap.json"),"""
                    {"corners":[{"slot":1,"x":-40,"y":81,"z":-30},{"slot":2,"x":40,"y":81,"z":-30},
                                {"slot":3,"x":40,"y":81,"z":30},{"slot":4,"x":-40,"y":81,"z":30}]}
                    """);
            MatchArena.initialize(server);
            LaneSystem.initialize(server);
            h.assertTrue(MatchArena.refreshReadiness(server) && MatchArena.selectedTempo() == MatchTempo.STANDARD,
                    "An unmarked single-lane map keeps standard timing");
            set(before, profile.resolve("myriad_match_tempo.json"), "{\"profile\":\"cherry_v1\"}");
            h.assertTrue(MatchArena.refreshReadiness(server) && MatchArena.selectedTempo() == MatchTempo.CHERRY_V1,
                    "Map opts in through its own optional configuration");
            checkWaves(h);
            var mid=LaneSystem.Lane.MID;
            h.assertTrue(MatchArena.active()==MatchArena.Mode.DUEL,"Valid saved 3v3 activates");
            var expected=List.of(new BlockPos(-20,81,6),new BlockPos(0,81,0),new BlockPos(20,81,-6));
            h.assertTrue(LaneSystem.orderedPath(mid,Faction.GUARDIAN).equals(expected),"Manual trail bypasses four towers");
            h.assertTrue(LaneSystem.orderedPath(mid,Faction.DESTRUCTION).equals(expected.reversed()),"Opposite wave starts at the opposite end");
            h.assertTrue(LaneSystem.minimapPaths().size()==3,"Minimap uses the actual 3v3 route");
            h.assertTrue(LaneSystem.setPathPoint(mid,2,new BlockPos(0,81,1)),"Admin can edit a manual point");
            var saved=JsonParser.parseString(Files.readString(lane)).getAsJsonObject();
            h.assertTrue(saved.getAsJsonArray("manualPaths").size()==1
                    &&saved.getAsJsonArray("towers").get(0).getAsJsonObject().get("minionPlatformRadius").getAsDouble()==5.5,
                    "Path editing persists the manual selection and pedestal reach");
            json.remove("manualPaths");
            Files.writeString(lane,json.toString());
            LaneSystem.initialize(server);
            h.assertTrue(LaneSystem.orderedPath(mid,Faction.GUARDIAN).contains(new BlockPos(-8,83,0)),
                    "Existing 3v3 layouts without the new option still use tower routes");
            h.assertTrue(LaneSystem.platformInReach(new Vec3(0,81,6),new Vec3(0,84,0),5.5,1.8)
                    &&!LaneSystem.platformInReach(new Vec3(0,81,8),new Vec3(0,84,0),5.5,1.8)
                    &&!LaneSystem.platformInReach(new Vec3(0,81,6),new Vec3(0,87,0),5.5,1.8),
                    "Pedestal reach is horizontally and vertically bounded");
            Files.writeString(selection, "{\"mode\":\"5v5\"}");
            MatchArena.initialize(server);
            h.assertTrue(MatchArena.active() == MatchArena.Mode.RIFT && MatchArena.selectedTempo() == MatchTempo.STANDARD
                    && MatchArena.selectedTempo().firstWaveTicks() == 1400,
                    "Selecting 5v5 ignores the other map's cherry profile");
        } catch(Exception e) {throw new IllegalStateException("Synthetic Cherry Rift lane fixture failed",e);}
        finally {
            try {
                for(var entry:before.entrySet()){
                    if(entry.getValue()==null)Files.deleteIfExists(entry.getKey());
                    else Files.write(entry.getKey(),entry.getValue());
                }
                MatchArena.initialize(server);
                LaneSystem.initialize(server);
            } catch(Exception e){throw new IllegalStateException("Synthetic fixture cleanup failed",e);}
        }
        System.out.println("[MYRIAD-CHERRY-FLAT] PASS: manual/reverse routes, minimap, bounded raised objectives, old layout fallback and save round trip");
        h.succeed();
    }

    private static void checkWaves(GameTestHelper h) {
        var level = h.getLevel();
        var server = level.getServer();
        var clock = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
        long originalTime = level.getGameTime(), start = originalTime + 100;
        var previous = GameManager.snapshot();
        var originalLanes = LaneSystem.snapshot();
        boolean enabled = ModState.isEnabled();
        try {
            var match = previous.deepCopy().getAsJsonObject();
            match.addProperty("phase", "PLAYING");
            match.addProperty("tempo", "CHERRY_V1");
            match.addProperty("matchStartTime", start);
            GameManager.restore(match, server);
            ModState.setEnabled(true);
            for (long[] step : new long[][]{{0,0},{699,0},{700,1},{1499,1},{1500,2},{2300,3}}) {
                clock.setGameTime(start + step[0]);
                LaneSystem.tick(level);
                var snapshot = LaneSystem.snapshot();
                h.assertTrue(snapshot.get("wave").getAsInt() == step[1], "Wave number at " + step[0]);
                if (step[0] == 700) {
                    var counts = new java.util.HashMap<String, Integer>();
                    for (var value : snapshot.getAsJsonArray("units")) {
                        var unit = value.getAsJsonObject();
                        if (unit.get("kind").getAsString().equals("MELEE") || unit.get("kind").getAsString().equals("RANGED"))
                            counts.merge(unit.get("faction").getAsString(), 1, Integer::sum);
                    }
                    h.assertTrue(counts.size() == 2 && counts.values().stream().allMatch(n -> n == 4),
                            "Both teams generate four first-wave minions on tick 700: " + counts);
                    LaneSystem.restore(level, snapshot);
                    h.assertTrue(LaneSystem.snapshot().get("nextWave").getAsLong() == start + 1500,
                            "Restore preserves next wave deadline");
                }
            }
        } finally {
            clock.setGameTime(originalTime);
            GameManager.restore(previous, server);
            LaneSystem.restore(level, originalLanes);
            ModState.setEnabled(enabled);
        }
    }

    private static void set(LinkedHashMap<Path,byte[]> before,Path target,String json)throws java.io.IOException {
        before.put(target,Files.isRegularFile(target)?Files.readAllBytes(target):null);
        Files.createDirectories(target.getParent());
        Files.writeString(target,json);
    }
}
