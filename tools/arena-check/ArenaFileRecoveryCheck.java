package com.myriad.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

/** Exercise the 5v5 file loaders and edit bases without opening a world or requiring a game server. */
public final class ArenaFileRecoveryCheck {
    private ArenaFileRecoveryCheck() {}

    public static void run() throws Exception {
        Path dir = Files.createTempDirectory("myriad-config-recovery-");
        try {
            check(dir, "myriad_lane_layout.json", "towerTiers", "[]", "[1,2]", json -> LaneSystem.decodeLayout(json, false),
                    layout -> layout.tiers.equals(java.util.Set.of(1, 2)));
            check(dir, "myriad_brush_zones.json", "brushes", "[]", "[{\"id\":\"repaired\",\"x1\":1,\"y1\":65,\"z1\":2}]",
                    VisionSystem::decodeBrushes, brushes -> brushes.containsKey("repaired"));
            check(dir, "myriad_monster_spawns.json", "points", "[]", "[{\"id\":\"repaired\",\"type\":\"frost_hare\",\"x\":1,\"y\":65,\"z\":2}]",
                    MonsterManager::decodeSpawns, points -> points.containsKey("repaired"));
            check(dir, "myriad_vein_spawns.json", "attribute", "{}", "{\"1\":{\"x\":1,\"y\":65,\"z\":2}}",
                    GameManager::decodeVeins, veins -> veins.attributes().containsKey(1));
            check(dir, "myriad_minimap.json", "corners", "[]", "[{\"slot\":2,\"x\":1,\"y\":65,\"z\":2}]",
                    json -> MapSystem.decodeCorners(json, false), corners -> corners.containsKey(2));
        } finally {
            Files.delete(dir);
        }
    }

    private static <T> void check(Path dir, String name, String field, String initial, String repaired,
            Function<JsonObject, T> decode, Predicate<T> hasRepair) throws Exception {
        Path file = dir.resolve(name);
        try {
            T empty = ArenaConfigFile.readOptional(file, decode).orElseThrow();
            JsonObject original = new JsonObject();
            original.add(field, JsonParser.parseString(initial));
            if (ArenaConfigFile.readForEdit(file, decode, () -> empty).isEmpty() || !ArenaConfigFile.save(file, original))
                throw new AssertionError("First edit could not create " + name);
            T previous = ArenaConfigFile.readOptional(file, decode).orElseThrow();
            for (String broken : new String[]{"{", "{\"" + field + "\": 27}"}) {
                Files.writeString(file, broken);
                byte[] bytes = Files.readAllBytes(file);
                if (ArenaConfigFile.readOptional(file, decode).orElse(previous) != previous
                        || ArenaConfigFile.readForEdit(file, decode, () -> empty).isPresent()
                        || !Arrays.equals(bytes, Files.readAllBytes(file)))
                    throw new AssertionError("Invalid file was accepted or modified: " + name);
            }
            original.add(field, JsonParser.parseString(repaired));
            Files.writeString(file, original.toString());
            T candidate = ArenaConfigFile.readForEdit(file, decode, () -> previous).orElseThrow();
            if (!hasRepair.test(candidate)) throw new AssertionError("Lost an external repair in " + name);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
