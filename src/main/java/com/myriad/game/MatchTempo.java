package com.myriad.game;

import com.google.gson.JsonObject;

/** A versioned map choice, frozen at the start of combat and carried by the match checkpoint. */
enum MatchTempo {
    STANDARD(70, 30, 60, 360),
    CHERRY_V1(35, 20, 40, 240),
    CHERRY_V2(35, 20, 40, 240),
    CHERRY_V3(35, 20, 40, 240, Supply.STANDARD.withExperience(360, 320)),
    RIFT_V1(70, 30, 60, 360, new Supply(
            65, 65,       // Lane gold and experience percentages.
            90, 90, 1800, // Camp gold, camp experience and Gold Pig team gold cap after global scaling.
            75, 80,       // Tower gold and wage percentages.
            1, 1));       // Attribute vein base gold and experience per pulse.

    private final int wave, small, medium, boss;
    private final Supply supply;

    /** Frozen map income settings. Changing a published profile requires a new MatchTempo ID. */
    record Supply(int laneGoldPercent, int laneXpPercent, int campGoldPercent, int campXpPercent,
            int goldPigTeamGoldCap, int towerGoldPercent, int wagePercent, int attributeVeinGold, int attributeVeinXp) {
        static final Supply STANDARD = new Supply(100, 100, 100, 100, Integer.MAX_VALUE, 100, 100, 10, 5);

        Supply withExperience(int lanePercent, int campPercent) {
            return new Supply(laneGoldPercent, lanePercent, campGoldPercent, campPercent, goldPigTeamGoldCap,
                    towerGoldPercent, wagePercent, attributeVeinGold, attributeVeinXp);
        }

        int lanePercent(boolean experience) { return experience ? laneXpPercent : laneGoldPercent; }
        int laneXp(int base) { return scaled(base, laneXpPercent); }
        int campGold(int base) { return scaled(base, campGoldPercent); }
        int campXp(int base) { return scaled(base, campXpPercent); }
        int towerGold(int base) { return scaled(base, towerGoldPercent); }
        int wage(int base) { return scaled(base, wagePercent); }

        private static int scaled(int base, int percent) {
            return (int) Math.min(Integer.MAX_VALUE, (base * (long) percent + 50) / 100);
        }
    }

    MatchTempo(int wave, int small, int medium, int boss) {
        this(wave, small, medium, boss, Supply.STANDARD);
    }

    MatchTempo(int wave, int small, int medium, int boss, Supply supply) {
        this.wave = wave;
        this.small = small;
        this.medium = medium;
        this.boss = boss;
        this.supply = supply;
    }

    static MatchTempo decode(JsonObject config) {
        if (!config.has("profile")) return STANDARD;
        return switch (config.get("profile").getAsString()) {
            case "standard" -> STANDARD;
            case "cherry_v1" -> CHERRY_V1;
            case "cherry_v2" -> CHERRY_V2;
            case "cherry_v3" -> CHERRY_V3;
            default -> throw new IllegalArgumentException("Unknown match tempo profile");
        };
    }

    boolean isCherry() { return this == CHERRY_V1 || usesCherryV2CombatStats(); }

    boolean usesCherryV2CombatStats() { return this == CHERRY_V2 || this == CHERRY_V3; }

    MatchTempo laneRewardBaseline() { return this == RIFT_V1 || this == CHERRY_V3 ? CHERRY_V2 : this; }

    Supply supply() { return supply; }

    long firstWaveTicks() { return wave * 20L; }

    long waveIntervalTicks() { return LaneSystem.WAVE_INTERVAL; }

    long veinOpensAfterTicks() { return 60L * 20L; }

    int veinPulseIntervalTicks() { return GameRules.VEIN_INCOME_PULSE_TICKS; }

    double attributeVeinBonus() { return .05; }

    long firstSpawnTicks(MonsterType type) {
        int seconds = type.isSmallMonster() ? small : type.isMediumMonster() ? medium : boss;
        return seconds * 20L;
    }

    int respawnSeconds(MonsterType type) {
        int original = type.getRespawnSeconds();
        if (!isCherry() || original == 0) return original;
        if (type.isSmallMonster()) return 75;
        if (type.isMediumMonster()) return 100;
        return type == MonsterType.CHERRY_RIFT_CRAB ? 240 : original;
    }
}
