package com.myriad.game;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** Whole action ticks exported from the attack clips and their Blockbench hit markers. */
record CherryRiftAttackTiming(int duration, int hit) {
    private static final String RESOURCE = "/data/myriad/cherry_rift/attack_timings.json";
    private static final Map<MonsterType, CherryRiftAttackTiming> TIMINGS = load();

    CherryRiftAttackTiming {
        if (hit <= 0 || hit >= duration) {
            throw new IllegalArgumentException("The hit marker must lie inside the attack clip");
        }
    }

    static CherryRiftAttackTiming forType(MonsterType type) {
        return TIMINGS.get(type);
    }

    private static Map<MonsterType, CherryRiftAttackTiming> load() {
        try (var input = CherryRiftAttackTiming.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing Cherry Rift attack timings: " + RESOURCE);
            var reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            var entries = new Gson().fromJson(reader, new TypeToken<Map<String, CherryRiftAttackTiming>>() {});
            var timings = new EnumMap<MonsterType, CherryRiftAttackTiming>(MonsterType.class);
            for (MonsterType type : MonsterType.values()) {
                if (type.cherryModel() == null) continue;
                var timing = entries.get(type.getId());
                if (timing == null) throw new IllegalStateException("Missing attack timing for " + type.getId());
                timings.put(type, timing);
            }
            if (timings.size() != entries.size()) throw new IllegalStateException("Unknown Cherry Rift attack timing");
            return Map.copyOf(timings);
        } catch (IOException error) {
            throw new IllegalStateException("Couldn't read Cherry Rift attack timings", error);
        }
    }
}
