package com.myriad.game;

import com.google.gson.JsonObject;

/** A versioned map choice, frozen at the start of combat and carried by the match checkpoint. */
enum MatchTempo {
    STANDARD(70, 30, 60, 360),
    CHERRY_V1(35, 20, 40, 240);

    private final int wave, small, medium, boss;

    MatchTempo(int wave, int small, int medium, int boss) {
        this.wave = wave;
        this.small = small;
        this.medium = medium;
        this.boss = boss;
    }

    static MatchTempo decode(JsonObject config) {
        if (!config.has("profile")) return STANDARD;
        return switch (config.get("profile").getAsString()) {
            case "standard" -> STANDARD;
            case "cherry_v1" -> CHERRY_V1;
            default -> throw new IllegalArgumentException("Unknown match tempo profile");
        };
    }

    long firstWaveTicks() { return wave * 20L; }

    long firstSpawnTicks(MonsterType type) {
        int seconds = type.isSmallMonster() ? small : type.isMediumMonster() ? medium : boss;
        return seconds * 20L;
    }

    int respawnSeconds(MonsterType type) {
        int original = type.getRespawnSeconds();
        if (this == STANDARD || original == 0) return original;
        if (type.isSmallMonster()) return 75;
        if (type.isMediumMonster()) return 100;
        return type == MonsterType.CHERRY_RIFT_CRAB ? 240 : original;
    }
}
