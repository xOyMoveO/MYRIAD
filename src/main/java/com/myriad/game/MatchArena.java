package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.Myriad;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
            "myriad_monster_spawns.json", "myriad_vein_spawns.json", "myriad_minimap.json"};
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
    public static boolean singleLaneReady(Path world) {
        try {
            for (String file : CONFIGS) {
                var path = configuration(world, Mode.DUEL, file);
                if (!Files.isRegularFile(path)) return false;
                JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            }
            var lane = JsonParser.parseString(Files.readString(configuration(world, Mode.DUEL, CONFIGS[0]))).getAsJsonObject();
            var enabled = lane.getAsJsonArray("enabledLanes");
            if (enabled == null || enabled.size() != 1 || !enabled.get(0).getAsString().equals("mid")) return false;
            var tiers = new java.util.HashSet<Integer>();
            if (lane.has("towerTiers")) for (var value : lane.getAsJsonArray("towerTiers")) {
                int tier = value.getAsInt();
                if (tier < 1 || tier > 3) return false;
                tiers.add(tier);
            }
            else tiers.addAll(java.util.List.of(1,2,3));
            if (tiers.size() < 2) return false;
            var keys = new java.util.HashSet<String>();
            for (var row : lane.getAsJsonArray("towers")) {
                var tower = row.getAsJsonObject();
                String faction = tower.get("faction").getAsString();
                int tier = tower.get("tier").getAsInt();
                if ((!faction.equals("youming") && !faction.equals("xuanxiao"))
                        || !tower.get("lane").getAsString().equals("mid") || !tiers.contains(tier)) return false;
                validPosition(tower);
                keys.add(faction + tier);
            }
            if (keys.size() != tiers.size()*2 || lane.getAsJsonArray("towers").size() != tiers.size()*2) return false;
            for (String category : new String[]{"fountains", "crystals"}) {
                var factions = new java.util.HashSet<String>();
                for (var row : lane.getAsJsonArray(category)) {
                    validPosition(row.getAsJsonObject());
                    factions.add(row.getAsJsonObject().get("faction").getAsString());
                }
                if (!factions.equals(java.util.Set.of("youming", "xuanxiao"))) return false;
            }
            var minimap = JsonParser.parseString(Files.readString(configuration(world, Mode.DUEL, CONFIGS[4]))).getAsJsonObject();
            return minimap.getAsJsonArray("corners").size() == 4;
        } catch (Exception error) { return false; }
    }
    private static void validPosition(com.google.gson.JsonObject row) {
        int x = row.get("x").getAsInt(), y = row.get("y").getAsInt(), z = row.get("z").getAsInt();
        if (Math.abs((long)x) > 29_000_000 || Math.abs((long)z) > 29_000_000 || y < -63 || y > 318)
            throw new IllegalArgumentException("Arena position outside world");
    }

    public static void initialize(MinecraftServer server) {
        active = Mode.RIFT;
        var path = server.getWorldPath(LevelResource.ROOT).resolve(SELECTION);
        if (!Files.isRegularFile(path)) return;
        try {
            Mode saved = Mode.byId(JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("mode").getAsString());
            if (saved != null && available(server, saved)) active = saved;
            else Myriad.LOGGER.warn("Saved arena unavailable; using original arena");
        } catch (Exception error) { Myriad.LOGGER.warn("Cannot load arena selection", error); }
    }

    /** Called on the server thread, after command/payload permission validation. */
    public static boolean select(MinecraftServer server, String id) {
        Mode requested = Mode.byId(id);
        GameManager manager = GameManager.get();
        if (requested == null || manager == null || manager.getPhase() != GameManager.Phase.WAITING) return false;
        if (!available(server, requested)) return false;
        if (active == requested) return true;
        Path target = server.getWorldPath(LevelResource.ROOT).resolve(SELECTION);
        try {
            Path temp = Files.createTempFile(target.getParent(), "myriad-arena-", ".tmp");
            try {
                Files.writeString(temp, "{\"mode\":\"" + requested.id + "\"}");
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException error) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temp); }
        } catch (Exception error) {
            Myriad.LOGGER.error("Unable to persist arena selection", error);
            return false;
        }
        active = requested;
        LaneSystem.reloadArena(server);
        MonsterManager.reloadArena(server);
        VisionSystem.initialize(server);
        MapSystem.initialize(server);
        manager.reloadArenaObjectives();
        BaseRules.clear();
        MapSystem.tick(server.overworld());
        MatchProgress.save(server);
        return true;
    }
}
