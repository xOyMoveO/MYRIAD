package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.Myriad;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Map;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Two arenas in one world. The original root configs remain the legacy 5v5 arena. */
public final class MatchArena {
    private MatchArena() { }
    public enum Mode {
        RIFT("5v5", 5), DUEL("3v3", 3);
        public final String id;
        public final int teamSize;
        Mode(String id, int teamSize) { this.id = id; this.teamSize = teamSize; }
        public Component displayName() { return Component.translatable("arena.myriad." + id); }
        public static Mode byId(String id) {
            for (Mode mode : values()) if (mode.id.equals(id)) return mode;
            return null;
        }
    }
    private static final String SELECTION = "myriad_arena_selection.json";
    private static final String[] CONFIGS = {"myriad_lane_layout.json", "myriad_brush_zones.json",
            "myriad_monster_spawns.json", "myriad_vein_spawns.json", "myriad_minimap.json", "myriad_match_tempo.json"};
    private static MatchTempo selectedTempo = MatchTempo.STANDARD;
    static MatchTempo selectedTempo() { return active == Mode.DUEL ? selectedTempo : MatchTempo.STANDARD; }
    private static Mode active = Mode.RIFT;
    public static Mode active() { return active; }
    public static void shutdown() { active = Mode.RIFT; }

    public static Path configuration(Path world, Mode mode, String file) {
        // Only enum values choose directories, never client-supplied paths. The shared lobby is not routed.
        return (mode == Mode.RIFT ? world : world.resolve("myriad_arenas").resolve(mode.id)).resolve(file);
    }
    public static Path configPath(MinecraftServer server, String file) {
        return configuration(server.getWorldPath(LevelResource.ROOT), active, file);
    }
    public static boolean available(MinecraftServer server, Mode mode) {
        return mode == Mode.RIFT || singleLaneReady(server.getWorldPath(LevelResource.ROOT));
    }
    record ConfigStamp(FileTime modified, long bytes) {}

    /** A queued 3v3 arena can be repaired externally while the server is running. */
    static List<ConfigStamp> configurationStamp(MinecraftServer server) {
        if (active != Mode.DUEL) return List.of();
        var world = server.getWorldPath(LevelResource.ROOT);
        var result = new ArrayList<ConfigStamp>(CONFIGS.length);
        for (String file : CONFIGS) {
            try {
                var stat = Files.readAttributes(configuration(world, active, file), BasicFileAttributes.class);
                result.add(new ConfigStamp(stat.lastModifiedTime(), stat.isRegularFile() ? stat.size() : -1));
            } catch (java.io.IOException error) {
                result.add(new ConfigStamp(null, -1));
            }
        }
        return List.copyOf(result);
    }

    private record Profile(LaneSystem.Layout lanes, Map<String, VisionSystem.BrushDraft> brushes,
            Map<String, MonsterManager.SpawnPoint> monsters, GameManager.VeinLayout veins, Map<Integer, BlockPos> corners, MatchTempo tempo) {
        static Profile read(Path world, Mode mode) throws IOException {
            boolean required = mode == Mode.DUEL;
            return new Profile(
                    ArenaConfigFile.read(configuration(world, mode, CONFIGS[0]), required, root -> LaneSystem.decodeLayout(root, required)),
                    ArenaConfigFile.read(configuration(world, mode, CONFIGS[1]), required, VisionSystem::decodeBrushes),
                    ArenaConfigFile.read(configuration(world, mode, CONFIGS[2]), required, MonsterManager::decodeSpawns),
                    ArenaConfigFile.read(configuration(world, mode, CONFIGS[3]), required, GameManager::decodeVeins),
                    ArenaConfigFile.read(configuration(world, mode, CONFIGS[4]), required, root -> MapSystem.decodeCorners(root, required)),
                    required ? ArenaConfigFile.read(configuration(world, mode, CONFIGS[5]), false, MatchTempo::decode)
                            : MatchTempo.STANDARD);
        }

        void activate(GameManager manager) {
            selectedTempo = tempo;
            LaneSystem.activateLayout(lanes);
            VisionSystem.activateBrushes(brushes);
            MonsterManager.activateSpawns(monsters);
            manager.activateVeins(veins);
            MapSystem.activateCorners(corners);
        }
    }

    public static boolean singleLaneReady(Path world) {
        try {
            Profile.read(world, Mode.DUEL);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Repaired configurations become live as a whole before allocating any player seat. */
    static boolean refreshReadiness(MinecraftServer server) {
        if (active != Mode.DUEL) return true;
        GameManager manager = GameManager.get();
        try {
            Profile profile = Profile.read(server.getWorldPath(LevelResource.ROOT), active);
            if (manager != null && manager.getPhase() == GameManager.Phase.WAITING) profile.activate(manager);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void initialize(MinecraftServer server) {
        active = Mode.RIFT;
        selectedTempo = MatchTempo.STANDARD;
        var path = server.getWorldPath(LevelResource.ROOT).resolve(SELECTION);
        if (!Files.isRegularFile(path)) return;
        try {
            Mode saved = Mode.byId(JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("mode").getAsString());
            if (saved == Mode.DUEL) {
                selectedTempo = Profile.read(server.getWorldPath(LevelResource.ROOT), saved).tempo();
                active = saved;
            } else if (saved == null) {
                Myriad.LOGGER.warn("Saved arena unavailable; using original arena");
            }
        } catch (Exception error) { Myriad.LOGGER.warn("Cannot load arena selection", error); }
    }

    /** A failed administrative selection can report the file and field without changing the active map. */
    public static Component selectError(MinecraftServer server, String id) {
        Mode requested = Mode.byId(id);
        GameManager manager = GameManager.get();
        if (requested == null || manager == null || manager.getPhase() != GameManager.Phase.WAITING)
            return Component.translatable("message.myriad.arena_unavailable");
        Profile profile;
        try {
            profile = Profile.read(server.getWorldPath(LevelResource.ROOT), requested);
            if (active != requested) {
                Path target = server.getWorldPath(LevelResource.ROOT).resolve(SELECTION);
                var selection = new com.google.gson.JsonObject();
                selection.addProperty("mode", requested.id);
                if (!ArenaConfigFile.save(target, selection))
                    return Component.translatable("message.myriad.arena_unavailable").append(" · " + SELECTION);
            }
        } catch (IOException e) {
            Myriad.LOGGER.warn("Arena selection rejected: {}", e.getMessage());
            return Component.translatable("message.myriad.arena_unavailable").append(" · " + e.getMessage());
        }
        boolean switching = active != requested;
        active = requested;
        if (switching) {
            LaneSystem.resetArenaRuntime(server);
            MonsterManager.resetArenaRuntime(server);
            VisionSystem.clearTracking();
            manager.resetMatchObjectives();
            BaseRules.clear();
        }
        profile.activate(manager);
        MapSystem.tick(server.overworld());
        MatchProgress.save(server);
        QueueLobby.arenaSelected(server);
        return null;
    }

    public static boolean select(MinecraftServer server, String id) {
        return selectError(server, id) == null;
    }
}
