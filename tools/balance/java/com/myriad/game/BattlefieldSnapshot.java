package com.myriad.game;

import static com.myriad.game.SnapshotJson.field;
import static com.myriad.game.SnapshotJson.gap;
import static com.myriad.game.SnapshotJson.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.combat.ControlStatusSystem;
import com.myriad.combat.SilenceSystem;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;

final class BattlefieldSnapshot {
    private static final String REAL = "real_tick";
    private static final String ACTION = "action_tick";
    private static final String NONE = "not_timed";
    private static final String CHERRY = "maps/cherry-rift-frontline/3v3-flat/";
    private static final String ATTACK_TIMINGS = "src/main/resources/data/myriad/cherry_rift/attack_timings.json";

    private BattlefieldSnapshot() {}

    static JsonObject export(Path root) {
        var rules = new JsonObject();
        rules.add("maps", maps(root));
        rules.add("minions", minions());
        rules.add("towerTiers", towers());
        rules.add("fountain", fountain());
        rules.add("crystal", crystal());
        rules.add("monsters", monsters());
        rules.add("rotatingBosses", rotatingBosses());
        rules.add("controls", controls());
        rules.add("supplies", supplies());
        rules.add("economy", economy());
        return rules;
    }

    static JsonObject modeProfiles() {
        MatchTempo tempo = MatchArena.riftTempo();
        MatchTempo.Supply supply = tempo.supply();
        var rift = new JsonObject();
        rift.add("profile", modeValue(tempo.name(), "profile_id", MatchArena.class, "riftTempo"));
        rift.add("baseline", modeValue(tempo.laneRewardBaseline().name(), "profile_id", MatchTempo.class, "laneRewardBaseline"));
        rift.add("legacyCheckpointProfile", modeValue(MatchTempo.STANDARD.name(), "profile_id", GameManager.class, "restore"));

        var lane = new JsonObject();
        lane.add("firstWaveTicks", modeValue(tempo.firstWaveTicks(), "ticks", MatchTempo.class, "firstWaveTicks"));
        lane.add("waveIntervalTicks", modeValue(tempo.waveIntervalTicks(), "ticks", MatchTempo.class, "waveIntervalTicks"));
        lane.add("goldMultiplier", supplyValue(supply.laneGoldPercent() / 100.0, "ratio"));
        lane.add("xpMultiplier", supplyValue(supply.laneXpPercent() / 100.0, "ratio"));
        rift.add("lane", lane);

        var camp = new JsonObject();
        camp.add("goldMultiplier", supplyValue(supply.campGoldPercent() / 100.0, "ratio"));
        camp.add("xpMultiplier", supplyValue(supply.campXpPercent() / 100.0, "ratio"));
        camp.add("goldPigTeamGoldCap", supplyValue(supply.goldPigTeamGoldCap(), "actual_team_gold"));
        camp.add("goldPigPayoutSeats", modeValue(MatchArena.Mode.RIFT.teamSize, "hero_slots", MonsterManager.class, "onKill"));
        rift.add("camp", camp);

        var vein = new JsonObject();
        vein.add("opensAfterTicks", modeValue(tempo.veinOpensAfterTicks(), "ticks", MatchTempo.class, "veinOpensAfterTicks"));
        vein.add("pulseIntervalTicks", modeValue(tempo.veinPulseIntervalTicks(), "ticks", MatchTempo.class, "veinPulseIntervalTicks"));
        vein.add("pulseClock", modeValue("world_tick", "clock_id", GameManager.class, "tickSpiritVein"));
        vein.add("attributeCaptureTicks", modeValue(GameManager.SMALL_VEIN_CAPTURE_TICKS, "ticks", GameManager.class, "SMALL_VEIN_CAPTURE_TICKS"));
        vein.add("attributeGoldBase", supplyValue(supply.attributeVeinGold(), "gold_per_seat_before_global"));
        vein.add("attributeXp", supplyValue(supply.attributeVeinXp(), "xp_per_seat"));
        vein.add("attributeBonus", modeValue(tempo.attributeVeinBonus(), "ratio", MatchTempo.class, "attributeVeinBonus"));
        vein.add("largeEnabled", modeValue(false, "boolean", GameManager.class, "rollSpiritVeins"));
        rift.add("vein", vein);

        var tower = new JsonObject();
        tower.add("goldMultiplier", supplyValue(supply.towerGoldPercent() / 100.0, "ratio"));
        rift.add("tower", tower);

        var wage = new JsonObject();
        wage.add("baseMultiplier", supplyValue(supply.wagePercent() / 100.0, "ratio"));
        wage.add("baseGold", supplyValue(supply.wage(GameManager.PASSIVE_GOLD_BASE), "gold_per_seat_before_global"));
        rift.add("wage", wage);

        MatchTempo cherry = MatchTempo.CHERRY_V3;
        var cherryProfile = new JsonObject();
        cherryProfile.add("profile", cherryModeValue(cherry.name(), "profile_id"));
        cherryProfile.add("baseline", cherryModeValue(cherry.laneRewardBaseline().name(), "profile_id"));
        cherryProfile.add("laneExperiencePercent", cherryModeValue(cherry.supply().laneXpPercent(), "percent"));
        cherryProfile.add("campExperiencePercent", cherryModeValue(cherry.supply().campXpPercent(), "percent"));

        var profiles = new JsonObject();
        profiles.add("rift_5v5", rift);
        profiles.add("cherry_3v3", cherryProfile);
        return profiles;
    }

    private static JsonObject cherryModeValue(Object value, String unit) {
        return at(value, unit, MatchTempo.class, "CHERRY_V3", "樱隙前线选定 CHERRY_V3", "match_start", NONE);
    }

    private static JsonObject supplyValue(Object value, String unit) {
        return modeValue(value, unit, MatchTempo.class, "RIFT_V1");
    }

    private static JsonObject modeValue(Object value, String unit, Class<?> owner, String member) {
        return at(value, unit, owner, member, "开战锁定 RIFT_V1", "match_start", NONE);
    }

    private static JsonObject maps(Path root) {
        var maps = new JsonObject();
        for (MatchArena.Mode mode : MatchArena.Mode.values()) {
            var arena = new JsonObject();
            arena.add("teamSize", constant(mode.teamSize, "hero_slots", MatchArena.class, "Mode"));
            if (mode == MatchArena.Mode.RIFT) {
                arena.add("tempoProfile", constant(MatchArena.riftTempo().name(), "profile_id", MatchArena.class, "selectedTempo"));
                arena.add("firstWave", at(MatchArena.riftTempo().firstWaveTicks(), "ticks", MatchTempo.class,
                        "firstWaveTicks", "配置了可用兵线的 5v5 地图", "match_start", REAL));
                for (String key : List.of("enabledLanes", "towerTiers", "towers", "fountains", "crystals", "camps", "veins")) {
                    arena.add(key, gap("missing_input", "map_definition", source(MatchArena.class, "configuration"),
                            "5v5", "arena_selection", NONE, "需要部署世界根目录的设施、营地及灵脉配置。"));
                }
            } else {
                Path world = root.resolve(CHERRY);
                Path tempoPath = MatchArena.configuration(world, mode, "myriad_match_tempo.json");
                Path lanePath = MatchArena.configuration(world, mode, "myriad_lane_layout.json");
                Path campPath = MatchArena.configuration(world, mode, "myriad_monster_spawns.json");
                Path veinPath = MatchArena.configuration(world, mode, "myriad_vein_spawns.json");
                readMap(arena, "tempoProfile", root, tempoPath, false, document -> {
                    MatchTempo tempo = MatchTempo.decode(document);
                    String provenance = Files.isRegularFile(tempoPath) ? relative(root, tempoPath)
                            : source(MatchTempo.class, "decode");
                    arena.add("tempoProfile", field(tempo.name(), "profile_id", provenance,
                            "樱隙前线 3v3", "match_start", NONE));
                    arena.add("firstWave", at(tempo.firstWaveTicks(), "ticks", MatchTempo.class,
                            "firstWaveTicks", "樱隙前线 3v3 已配置中路，使用 " + tempo.name(), "match_start", REAL));
                });
                readMap(arena, "facilities", root, lanePath, true,
                        document -> addLayout(arena, LaneSystem.decodeLayout(document, true), relative(root, lanePath)));
                readMap(arena, "camps", root, campPath, true,
                        document -> addCamps(arena, MonsterManager.decodeSpawns(document), relative(root, campPath)));
                readMap(arena, "veins", root, veinPath, true,
                        document -> mapValue(arena, "veins", GameManager.decodeVeins(document).json(), "map_definition",
                                relative(root, veinPath)));
            }
            maps.add(mode.id, arena);
        }
        return maps;
    }

    private static void readMap(JsonObject arena, String key, Path root, Path path, boolean required,
            java.util.function.Consumer<JsonObject> read) {
        try {
            ArenaConfigFile.read(path, required, document -> { read.accept(document); return document; });
        } catch (IOException exception) {
            arena.add(key, gap("missing_input", "map_definition", relative(root, path),
                    "樱隙前线 3v3", "arena_selection", NONE, "未能读取有效地图配置。"));
        }
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void addLayout(JsonObject arena, LaneSystem.Layout layout, String provenance) {
        mapValue(arena, "enabledLanes", layout.enabled.stream().map(LaneSystem.Lane::id).toList(), "lane_ids", provenance);
        mapValue(arena, "towerTiers", layout.tiers, "tier_ids", provenance);
        var towers = new JsonObject();
        layout.towers.forEach((key, pos) -> {
            String id = key.faction().getName() + "/" + key.lane().id() + "/" + key.tier();
            var tower = new JsonObject();
            mapValue(tower, "position", position(pos), "block_position", provenance);
            if (layout.radii.containsKey(pos))
                mapValue(tower, "minionPlatformRadius", layout.radii.get(pos), "blocks", provenance);
            towers.add(id, tower);
        });
        arena.add("towers", towers);
        for (var named : Map.of("fountains", layout.fountains, "crystals", layout.crystals).entrySet()) {
            var positions = new JsonObject();
            named.getValue().forEach((faction, pos) -> mapValue(positions, faction.getName(), position(pos),
                    "block_position", provenance));
            arena.add(named.getKey(), positions);
        }
    }

    private static void addCamps(JsonObject arena, Map<String, MonsterManager.SpawnPoint> points, String provenance) {
        var camps = new JsonObject();
        points.forEach((id, point) -> {
            var camp = new JsonObject();
            mapValue(camp, "type", point.largeRandom() ? "large_random" : point.type().getId(), "monster_id", provenance);
            mapValue(camp, "position", position(point.pos()), "block_position", provenance);
            camps.add(id, camp);
        });
        arena.add("camps", camps);
    }

    private static void mapValue(JsonObject object, String key, Object value, String unit, String provenance) {
        object.add(key, field(value, unit, provenance, "樱隙前线 3v3", "arena_selection", NONE));
    }

    private static Map<String, Integer> position(BlockPos pos) {
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
    }

    private static JsonObject minions() {
        var result = new JsonObject();
        result.add("waveInterval", at(LaneSystem.WAVE_INTERVAL, "ticks", LaneSystem.class, "WAVE_INTERVAL",
                "首波生成以后", "wave_spawn", REAL));
        result.add("meleeSlots", constant(LaneSystem.MELEE_PER_WAVE, "minions_per_side_lane_wave", LaneSystem.class, "MELEE_PER_WAVE"));
        result.add("rangedSlots", constant(LaneSystem.RANGED_PER_WAVE, "minions_per_side_lane_wave", LaneSystem.class, "RANGED_PER_WAVE"));
        result.add("siegeSlots", at(LaneSystem.MAX_SIEGE_PER_LANE_WAVE, "minions_per_side_lane_wave", LaneSystem.class,
                "MAX_SIEGE_PER_LANE_WAVE", "该路全部敌方配置塔已毁，替换近战兵", "wave_spawn", NONE));
        result.add("magicInterval", at(LaneSystem.MAGIC_WAVE_PERIOD, "waves", LaneSystem.class, "MAGIC_WAVE_PERIOD",
                "周期波次用法兵替换一个远程兵", "wave_spawn", REAL));
        result.add("wavesPerGrowthStage", constant(LaneSystem.WAVES_PER_GROWTH_STAGE, "waves", LaneSystem.class, "WAVES_PER_GROWTH_STAGE"));
        result.add("lateGrowthBegins", at(LaneSystem.LATE_MINION_GROWTH_TICKS, "ticks", LaneSystem.class,
                "LATE_MINION_GROWTH_TICKS", "STANDARD 与 CHERRY_V1 从正式开战计时", "minion_tick", REAL));
        var pressure = new JsonObject();
        long stageTicks = LaneSystem.WAVES_PER_GROWTH_STAGE * LaneSystem.WAVE_INTERVAL;
        pressure.add("growthBegins", at(MatchTempo.CHERRY_V2.firstWaveTicks() + stageTicks,
                "ticks", LaneSystem.class, "elapsedGrowthStages", "CHERRY_V2", "match_start", REAL));
        pressure.add("growthStageDuration", at(stageTicks,
                "ticks", LaneSystem.class, "elapsedGrowthStages", "CHERRY_V2", "match_start", REAL));
        pressure.add("begins", constant(LaneSystem.MINION_PRESSURE_START_TICKS, "ticks", LaneSystem.class, "MINION_PRESSURE_START_TICKS"));
        pressure.add("healthPerGrowthStage", constant(LaneSystem.MINION_HEALTH_PER_GROWTH_STAGE, "base_health_ratio_per_stage",
                LaneSystem.class, "MINION_HEALTH_PER_GROWTH_STAGE"));
        pressure.add("attackPerGrowthStage", constant(LaneSystem.MINION_ATTACK_PER_GROWTH_STAGE, "base_attack_ratio_per_stage",
                LaneSystem.class, "MINION_ATTACK_PER_GROWTH_STAGE"));
        pressure.add("healthPerPressureMinute", constant(LaneSystem.MINION_HEALTH_PER_PRESSURE_MINUTE, "base_health_ratio_per_minute",
                LaneSystem.class, "MINION_HEALTH_PER_PRESSURE_MINUTE"));
        pressure.add("attackPerPressureMinute", constant(LaneSystem.MINION_ATTACK_PER_PRESSURE_MINUTE, "base_attack_ratio_per_minute",
                LaneSystem.class, "MINION_ATTACK_PER_PRESSURE_MINUTE"));
        result.add("cherryV2Pressure", pressure);

        var types = new JsonObject();
        for (LaneSystem.Kind kind : List.of(LaneSystem.Kind.MELEE, LaneSystem.Kind.RANGED, LaneSystem.Kind.MAGIC, LaneSystem.Kind.SIEGE)) {
            var unit = new JsonObject();
            float baseHp = LaneSystem.baseHealth(kind);
            int baseGold = LaneSystem.minionBaseGold(kind, MatchTempo.CHERRY_V1);
            int baseXp = LaneSystem.minionBaseXp(kind);
            unit.add("baseHealth", constant(baseHp, "hp", LaneSystem.class, "baseHealth"));
            unit.add("baseAttack", constant(LaneSystem.baseAttack(kind), "hp", LaneSystem.class, "baseAttack"));
            unit.add("attackRecovery", at(LaneSystem.minionAttackRecovery(kind), "ticks", LaneSystem.class,
                    "minionAttackRecovery", "受局部行动速度影响", "attack", ACTION));
            unit.add("attackRange", constant(LaneSystem.minionAttackRange(kind), "blocks", LaneSystem.class, "minionAttackRange"));
            unit.add("baseGold", at(baseGold, "gold", LaneSystem.class, "minionBaseGold",
                    "STANDARD 与 CHERRY_V1", "definition", NONE));
            unit.add("baseExperience", constant(baseXp, "xp", LaneSystem.class, "minionBaseXp"));
            var samples = new JsonArray();
            for (int stage : List.of(0, 1, 7)) {
                for (boolean late : List.of(false, true)) {
                    var sample = new JsonObject();
                    sample.addProperty("stage", stage);
                    sample.addProperty("late", late);
                    sample.add("health", constant(LaneSystem.scaledMinionHealth(baseHp, stage, late), "hp", LaneSystem.class, "scaledMinionHealth"));
                    sample.add("attack", constant(LaneSystem.scaledMinionAttack(kind, stage, late), "hp", LaneSystem.class, "scaledMinionAttack"));
                    samples.add(sample);
                }
            }
            unit.add("growthSamples", samples);
            var rewards = new JsonArray();
            for (long elapsed : new long[]{0, 2400, 24000}) {
                var sample = new JsonObject();
                sample.addProperty("elapsedTicks", elapsed);
                sample.add("lastHitGold", minionReward(baseGold, true, false, elapsed, MatchTempo.CHERRY_V1));
                sample.add("lastHitExperience", minionReward(baseXp, true, true, elapsed, MatchTempo.CHERRY_V1));
                sample.add("nonPlayerKillNearbyGold", minionReward(baseGold, false, false, elapsed, MatchTempo.CHERRY_V1));
                sample.add("nearbyExperience", minionReward(baseXp, false, true, elapsed, MatchTempo.CHERRY_V1));
                rewards.add(sample);
            }
            unit.add("rewardSamplesBeforeCatchUpAndGlobalGold", rewards);
            var byTempo = new JsonObject();
            for (MatchTempo tempo : MatchTempo.values()) byTempo.add(tempo.name(), minionTimeline(kind, tempo));
            unit.add("byTempo", byTempo);
            types.add(kind.name().toLowerCase(Locale.ROOT), unit);
        }
        result.add("types", types);
        return result;
    }

    private static JsonObject minionTimeline(LaneSystem.Kind kind, MatchTempo tempo) {
        var rule = new JsonObject();
        int baseGold = LaneSystem.minionBaseGold(kind, tempo);
        int baseXp = LaneSystem.minionBaseXp(kind);
        rule.add("baseGold", constant(baseGold, "gold", LaneSystem.class, "minionBaseGold"));
        rule.add("baseExperience", constant(baseXp, "xp", LaneSystem.class, "minionBaseXp"));
        var samples = new JsonArray();
        for (long elapsed : new long[]{700, 3600, 9600, 19180, 19200, 19220, 23980, 24000, 24020, 26400}) {
            int wave = elapsed < tempo.firstWaveTicks() ? 0 : (int) ((elapsed - tempo.firstWaveTicks()) / tempo.waveIntervalTicks()) + 1;
            var sample = new JsonObject();
            sample.addProperty("elapsedTicks", elapsed);
            sample.addProperty("wave", wave);
            sample.add("health", at(LaneSystem.scaledMinionHealth(kind, wave, elapsed, tempo), "hp",
                    LaneSystem.class, "scaledMinionHealth", tempo.name(), "minion_tick", REAL));
            sample.add("attack", at(LaneSystem.scaledMinionAttack(kind, wave, elapsed, tempo), "hp",
                    LaneSystem.class, "scaledMinionAttack", tempo.name(), "minion_tick", REAL));
            sample.add("lastHitGold", minionReward(baseGold, true, false, elapsed, tempo));
            sample.add("lastHitExperience", minionReward(baseXp, true, true, elapsed, tempo));
            sample.add("nonPlayerKillNearbyGold", minionReward(baseGold, false, false, elapsed, tempo));
            sample.add("nearbyExperience", minionReward(baseXp, false, true, elapsed, tempo));
            samples.add(sample);
        }
        rule.add("timelineBeforeCatchUpAndGlobalGold", samples);
        return rule;
    }

    private static JsonObject minionReward(int base, boolean playerKill, boolean xp, long elapsed, MatchTempo tempo) {
        return at(LaneSystem.minionReward(base, playerKill, xp, elapsed, tempo), xp ? "xp" : "gold", LaneSystem.class,
                "minionReward", tempo.name() + (playerKill ? " 英雄补刀" : " 符合范围的非补刀受益者"), "minion_death", REAL);
    }

    private static JsonObject towers() {
        var result = new JsonObject();
        for (int tier = 1; tier <= LaneSystem.TOWERS_PER_LANE; tier++) {
            var tower = new JsonObject();
            tower.add("health", constant(LaneSystem.towerHealth(tier), "hp", LaneSystem.class, "towerHealth"));
            tower.add("attack", constant(LaneSystem.towerAttack(tier), "physical_hp", LaneSystem.class, "towerAttack"));
            tower.add("baseTeamGoldPerRecipient", at(LaneSystem.towerBaseGold(tier), "gold", LaneSystem.class, "towerBaseGold",
                    "敌方阵营击破，按全局金币倍率结算给该阵营在主世界的在线参赛者", "tower_death", NONE));
            tower.add("attackRange", constant(LaneSystem.TOWER_ATTACK_RANGE, "blocks", DefenseTowerEntity.class, "ATTACK_RANGE"));
            tower.add("lockTime", at(LaneSystem.TOWER_LOCK_TICKS, "ticks", LaneSystem.class, "TOWER_LOCK_TICKS",
                    "持续合法锁定", "target_lock", REAL));
            result.add(Integer.toString(tier), tower);
        }
        result.add("vulnerability", at("preceding_tiers_cleared", "rule_id", LaneSystem.class, "isTowerVulnerable",
                "同路同阵营的更外层防御塔已经摧毁", "incoming_hit", NONE));
        result.add("escortedRadius", constant(LaneSystem.TOWER_ESCORTED_RADIUS, "blocks", LaneSystem.class, "TOWER_ESCORTED_RADIUS"));
        result.add("unescortedHeroDamageMultiplier", at(LaneSystem.TOWER_UNESCORTED_MULTIPLIER, "ratio", LaneSystem.class,
                "TOWER_UNESCORTED_MULTIPLIER", "攻击者方兵线未进护送范围，含水晶", "incoming_hit", NONE));
        var protection = new JsonArray();
        for (int elapsed : new int[]{0, 3600, 5999, 6000, 6001, 10200, 14400, 21600, 24000}) {
            var sample = new JsonObject();
            sample.addProperty("elapsedTicks", elapsed);
            sample.add("incomingMultiplier", at(MatchBalance.towerDamageTakenScale(elapsed), "ratio",
                    MatchBalance.class, "towerDamageTakenScale", "防御塔入伤", "incoming_hit", REAL));
            sample.add("heroAttackMultiplierFirstHit", at(MatchBalance.towerScale(elapsed, 0), "ratio",
                    MatchBalance.class, "towerScale", "首次锁定英雄", "attack", REAL));
            sample.add("heroAttackMultiplierFourStacks", at(MatchBalance.towerScale(elapsed, 4), "ratio",
                    MatchBalance.class, "towerScale", "对同一英雄连续命中，累计四层", "attack", REAL));
            protection.add(sample);
        }
        result.add("scalingSamples", protection);
        return result;
    }

    private static JsonObject fountain() {
        var result = new JsonObject();
        result.add("range", constant(LaneSystem.FOUNTAIN_RADIUS, "blocks", LaneSystem.class, "FOUNTAIN_RADIUS"));
        result.add("allyHealthAndManaRestored", at(LaneSystem.FOUNTAIN_RESTORE_RATIO, "ratio_of_maximum",
                LaneSystem.class, "FOUNTAIN_RESTORE_RATIO", "己方存活英雄位于泉水范围内", "fountain_pulse", REAL));
        result.add("pulseInterval", at(LaneSystem.FOUNTAIN_PULSE_TICKS, "ticks", LaneSystem.class, "FOUNTAIN_PULSE_TICKS",
                "正式开战后", "fountain_pulse", REAL));
        result.add("enemyDamageSamples", field(List.of(
                        Map.of("maxHp", 100, "damage", LaneSystem.fountainEnemyDamage(100)),
                        Map.of("maxHp", 800, "damage", LaneSystem.fountainEnemyDamage(800))),
                "true_hp", source(LaneSystem.class, "fountainEnemyDamage"), "敌方可被攻击的英雄在泉水范围内",
                "fountain_pulse", REAL));
        return result;
    }

    private static JsonObject crystal() {
        var result = new JsonObject();
        result.add("health", constant(LaneSystem.CRYSTAL_HEALTH, "hp", LaneSystem.class, "CRYSTAL_HEALTH"));
        result.add("attack", constant(LaneSystem.CRYSTAL_ATTACK, "physical_hp", LaneSystem.class, "CRYSTAL_ATTACK"));
        result.add("attackRange", constant(LaneSystem.TOWER_ATTACK_RANGE, "blocks", DefenseTowerEntity.class, "ATTACK_RANGE"));
        result.add("lockTime", at(LaneSystem.TOWER_LOCK_TICKS, "ticks", LaneSystem.class, "TOWER_LOCK_TICKS",
                "持续合法锁定", "target_lock", REAL));
        result.add("vulnerability", at("active_match_and_a_lane_cleared", "rule_id", LaneSystem.class,
                "crystalVulnerable", "至少一路的敌方配置塔已全毁，正式开战中可攻击", "incoming_hit", NONE));
        result.add("unescortedHeroDamageMultiplier", at(LaneSystem.TOWER_UNESCORTED_MULTIPLIER, "ratio", LaneSystem.class,
                "TOWER_UNESCORTED_MULTIPLIER", "攻击者方兵线未进护送范围", "incoming_hit", NONE));
        result.add("matchResult", at("opposing_faction_wins", "rule_id", GameManager.class,
                "crystalDestroyed", "敌方水晶实际摧毁，结束正式对局", "crystal_death", NONE));
        return result;
    }

    private static JsonObject monsters() {
        var result = new JsonObject();
        var growth = new JsonObject();
        for (int generation : new int[]{0, 1, 2, 3, 8}) {
            growth.add(Integer.toString(generation), at(MonsterManager.growthMultiplier(generation), "ratio",
                    MonsterManager.class, "growthMultiplier", "营地自开局起的成功生成次数，从零编号",
                    "monster_spawn", NONE));
        }
        result.add("spawnGenerationGrowthSamples", growth);

        var types = new JsonObject();
        Arrays.stream(MonsterType.values()).sorted(Comparator.comparing(MonsterType::getId)).forEach(type -> {
            var row = new JsonObject();
            row.add("sizeClass", constant(type.isSmallMonster() ? "small" : type.isMediumMonster() ? "medium" : "large_boss",
                    "category", MonsterType.class, "isSmallMonster/isMediumMonster/isLargeBoss"));
            row.add("baseHealth", constant(type.getHealth(), "hp", MonsterType.class, "getHealth"));
            row.add("baseAttack", constant(type.getAttack(), "hp", MonsterType.class, "getAttack"));
            row.add("basePhysicalDefense", constant(type.getPhysicalDefense(), "defense_points", MonsterType.class, "getPhysicalDefense"));
            row.add("baseMagicDefense", constant(type.getMagicDefense(), "defense_points", MonsterType.class, "getMagicDefense"));
            var combatStats = new JsonObject();
            for (MatchTempo tempo : MatchTempo.values()) {
                String condition = "开战锁定 " + tempo.name() + "，首刷（第 0 代）";
                var stats = new JsonObject();
                double hp = type.healthFor(tempo);
                stats.add("health", at(hp, "hp", MonsterType.class, "healthFor", condition, "monster_spawn", NONE));
                stats.add("attack", at(type.attackFor(tempo), "hp", MonsterType.class, "attackFor",
                        condition, "monster_spawn", NONE));
                stats.add("physicalEffectiveHealth", at(hp * (1 + type.getPhysicalDefense() / 100), "hp",
                        com.myriad.combat.DamageCalc.class, "mitigation", condition + "；零穿透、非暴击",
                        "monster_spawn", NONE));
                stats.add("magicEffectiveHealth", at(hp * (1 + type.getMagicDefense() / 100), "hp",
                        com.myriad.combat.DamageCalc.class, "mitigation", condition + "；零穿透、非暴击",
                        "monster_spawn", NONE));
                combatStats.add(tempo.name(), stats);
            }
            row.add("combatStatsByTempo", combatStats);
            row.add("moveSpeed", constant(type.getMoveSpeed(), "blocks_per_second", MonsterType.class, "getMoveSpeed"));
            row.add("attackRange", constant(type.getAttackRange(), "blocks", MonsterType.class, "getAttackRange"));
            row.add("baseExperience", constant(type.getXp(), "xp", MonsterType.class, "getXp"));
            if (type == MonsterType.GOLD_PIG) {
                var samples = new JsonObject();
                for (long elapsed : new long[]{0, 4800, 9600}) {
                    samples.add(Long.toString(elapsed), at(MatchBalance.pigGold(elapsed), "gold", MatchBalance.class, "pigGold",
                            "按对局经过刻，额外进行全局金币换算", "camp_death", REAL));
                }
                row.add("preGlobalGoldSamplesByElapsedTicks", samples);
            } else {
                row.add("baseGold", constant(type.getGold(), "gold", MonsterType.class, "getGold"));
            }
            row.add("teamGold", constant(type.isTeamGold(), "boolean", MonsterType.class, "isTeamGold"));
            row.add("goldOwner", at(type.isTeamGold() ? "killer_team" : "last_hitter", "reward_owner",
                    MonsterManager.class, "onKill", "符合结算资格的玩家", "camp_death", NONE));
            row.add("experienceOwner", at("last_hitter", "reward_owner", MonsterManager.class, "onKill",
                    "符合结算资格的玩家", "camp_death", NONE));
            row.add("fixedCampEligible", constant(type.canUseFixedSpawn(), "boolean", MonsterType.class, "canUseFixedSpawn"));
            if (type.canUseFixedSpawn()) {
                var tempoRows = new JsonObject();
                for (MatchTempo tempo : MatchTempo.values()) {
                    var times = new JsonObject();
                    times.add("firstSpawn", at(tempo.firstSpawnTicks(type), "ticks", MatchTempo.class, "firstSpawnTicks",
                            "已配置对应营地", "match_start", REAL));
                    int respawn = tempo.respawnSeconds(type);
                    times.add("respawnDelay", at(respawn * 20L, "ticks", MatchTempo.class, "respawnSeconds",
                            respawn == 0 ? "死亡后禁止重生" : "配置营地野怪实际死亡", "camp_death", REAL));
                    tempoRows.add(tempo.name(), times);
                }
                row.add("fixedCampTiming", tempoRows);
            } else {
                row.add("fixedCampTiming", gap("not_applicable", "ticks", source(MonsterType.class, "canUseFixedSpawn"),
                        "轮换大型首领", "camp_death", REAL, "由全图轮换规则决定下一次生成。"));
            }
            if (type.getBuff() != null) {
                row.add("rewardBuff", at(type.getBuff(), "buff_id", MonsterType.class, "getBuff",
                        type.isLargeBoss() ? "击杀者阵营的符合结算资格成员" : "符合结算资格的最后一击",
                        "camp_death", NONE));
                if (type.isLargeBoss())
                    row.add("teamBuffDuration", at(MonsterManager.BOSS_BUFF_TICKS, "ticks", MonsterManager.class,
                            "BOSS_BUFF_TICKS", "击杀者阵营的符合资格成员", "camp_death", REAL));
            }
            var animation = CherryRiftAttackTiming.forType(type);
            if (animation != null) {
                row.add("attackWindup", field(animation.hit(), "ticks", ATTACK_TIMINGS,
                        "攻击开始后到命中判定", "npc_attack", ACTION));
                row.add("attackClipDuration", field(animation.duration(), "ticks", ATTACK_TIMINGS,
                        "樱隙野怪专属攻击动作", "npc_attack", ACTION));
            }
            types.add(type.getId(), row);
        });
        result.add("types", types);
        return result;
    }

    private static JsonObject rotatingBosses() {
        var result = new JsonObject();
        result.add("candidates", constant(MonsterManager.LARGE_BOSSES.stream().map(MonsterType::getId).toList(),
                "monster_ids", MonsterManager.class, "LARGE_BOSSES"));
        result.add("firstAttempt", at(MonsterManager.FIRST_BOSS_SPAWN_TICKS, "ticks", MonsterManager.class,
                "FIRST_BOSS_SPAWN_TICKS", "正式开战后，至少一个随机大型点空置且有未在场首领时生成",
                "match_start", REAL));
        result.add("interval", at(MonsterManager.BOSS_SPAWN_INTERVAL_TICKS, "ticks", MonsterManager.class,
                "BOSS_SPAWN_INTERVAL_TICKS", "一次生成成功后开始下轮计时", "boss_spawn", REAL));
        return result;
    }

    private static JsonObject controls() {
        var result = new JsonObject();
        result.add("sameTypeExpiry", at("max", "expiry_operator", ControlStatusSystem.class, "laterExpiry",
                "叠加同类眩晕、禁锢或沉默；每次施加先结算其自身生效时长", "control_applied", REAL));
        var overlaps = new JsonObject();
        overlaps.add("30_then_10", at(ControlStatusSystem.laterExpiry(30, 10), "ticks",
                ControlStatusSystem.class, "laterExpiry", "现余 30 刻再施加 10 刻同类控制", "control_applied", REAL));
        overlaps.add("10_then_30", at(ControlStatusSystem.laterExpiry(10, 30), "ticks",
                ControlStatusSystem.class, "laterExpiry", "现余 10 刻再施加 30 刻同类控制", "control_applied", REAL));
        result.add("sameTypeExpirySamples", overlaps);
        var actions = new JsonObject();
        actions.add("stun", at(Map.of("move", false, "basicAttack", false, "stationaryCast", false),
                "action_availability", AbilityRuntime.class, "root", "常规玩家行动；技能可声明解控例外",
                "input", REAL));
        actions.add("root", at(Map.of("move", false, "basicAttack", true, "stationaryCast", true),
                "action_availability", AbilityRuntime.class, "root", "满足各自释放要求的动作；禁止自主位移",
                "input", REAL));
        actions.add("silence", at(Map.of("move", true, "basicAttack", true, "stationaryCast", false),
                "action_availability", SilenceSystem.class, "isSilenced", "常规玩家行动；技能可声明解控例外",
                "input", REAL));
        result.add("basicActionAvailability", actions);
        var time = new JsonObject();
        time.add("overlap", at("minimum", "rate_operator", ActionTime.class, "multiplier",
                "同一受支持行动对象同时具有多个生效的来源", "action_progress", ACTION));
        time.add("cooldownClock", at(REAL, "timebase", PlayerData.class, "tickCooldowns",
                "缓时期间技能冷却继续推进", "cooldown_tick", REAL));
        result.add("actionTime", time);
        return result;
    }

    private static JsonObject supplies() {
        var result = new JsonObject();
        result.add("pillCarryLimit", at(TacticalSupplySystem.PILL_LIMIT, "items", TacticalSupplySystem.class,
                "PILL_LIMIT", "回春丹与回灵丹合计", "purchase_and_inventory_check", NONE));
        result.add("regenerationLifecycle", field(Map.of("fullStops", true, "deathStops", true, "sameTypeRejected", true),
                "regeneration_lifecycle", source(TacticalSupplySystem.class, "tryUse,tickRegeneration")
                        + " | " + source(PlayerDataProvider.class, "onLivingDeath"),
                "同种丹药生效中不可续服；回满该资源或收到死亡事件即终止余下回复", "pill_use_and_tick", REAL));
        var offers = new JsonObject();
        for (String id : TacticalSupplySystem.supplyIds()) {
            var rules = new JsonObject();
            var offer = TacticalSupplySystem.offer(id);
            rules.add("price", at(offer.price(), "gold", TacticalSupplySystem.class, "offer",
                    "满足购物权限；免金训练的实际支出为零", "purchase", NONE));
            rules.add("count", constant(offer.count(), "items", TacticalSupplySystem.class, "offer"));
            offers.add(id, rules);
        }
        var blood = offers.getAsJsonObject("blood_pill");
        regeneration(blood, TacticalSupplySystem.BLOOD_DURATION, "BLOOD_DURATION", "bloodPerSecond", "hp",
                List.of(0, 800), TacticalSupplySystem::bloodPerSecond, "脱战整额");
        blood.add("pulseBase", constant(TacticalSupplySystem.BLOOD_PULSE_BASE, "hp",
                TacticalSupplySystem.class, "BLOOD_PULSE_BASE"));
        blood.add("pulseMaximumRatio", constant(TacticalSupplySystem.BLOOD_PULSE_MAX_HEALTH_PERCENT / 100.0,
                "ratio", TacticalSupplySystem.class, "BLOOD_PULSE_MAX_HEALTH_PERCENT"));
        blood.add("combatWindowInclusive", at(PlayerDataProvider.COMBAT_WINDOW_TICKS, "ticks",
                PlayerDataProvider.class, "COMBAT_WINDOW_TICKS", "距最近一次已记录战斗的真实刻数，含边界",
                "regeneration_pulse", REAL));
        blood.add("combatMultiplier", at(PlayerDataProvider.IN_COMBAT_HEALTH_REGEN_MULTIPLIER, "ratio",
                PlayerDataProvider.class, "IN_COMBAT_HEALTH_REGEN_MULTIPLIER",
                "每跳重新检查战斗状态；战斗中将基础量与最大值成长之和乘一次", "regeneration_pulse", REAL));
        var mana = offers.getAsJsonObject("mana_pill");
        regeneration(mana, TacticalSupplySystem.MANA_DURATION, "MANA_DURATION", "manaPerSecond", "mana",
                List.of(0, 700), TacticalSupplySystem::manaPerSecond, "战斗与脱战同额");
        mana.add("pulseBase", constant(TacticalSupplySystem.MANA_PULSE_BASE, "mana",
                TacticalSupplySystem.class, "MANA_PULSE_BASE"));
        mana.add("pulseMaximumRatio", constant(TacticalSupplySystem.MANA_PULSE_MAX_MANA_PERCENT / 100.0,
                "ratio", TacticalSupplySystem.class, "MANA_PULSE_MAX_MANA_PERCENT"));
        var ward = offers.getAsJsonObject("scout_totem");
        ward.add("duration", supplyTime(TacticalSupplySystem.WARD_DURATION, "WARD_DURATION", "totem_placed"));
        ward.add("detectionRadius", constant(TacticalSupplySystem.WARD_RADIUS, "blocks", TacticalSupplySystem.class, "WARD_RADIUS"));
        ward.add("castRange", constant(TacticalSupplySystem.WARD_CAST_RADIUS, "blocks", TacticalSupplySystem.class, "WARD_CAST_RADIUS"));
        var trueSight = offers.getAsJsonObject("true_sight_charm");
        trueSight.add("duration", supplyTime(TacticalSupplySystem.TRUE_SIGHT_DURATION, "TRUE_SIGHT_DURATION", "charm_used"));
        trueSight.add("detectionRadius", constant(TacticalSupplySystem.TRUE_SIGHT_RADIUS, "blocks", TacticalSupplySystem.class, "TRUE_SIGHT_RADIUS"));
        var trap = offers.getAsJsonObject("spirit_snare");
        trap.add("duration", supplyTime(TacticalSupplySystem.TRAP_DURATION, "TRAP_DURATION", "trap_placed"));
        trap.add("triggerBoxInflation", constant(TacticalSupplySystem.TRAP_RADIUS, "blocks", TacticalSupplySystem.class, "TRAP_RADIUS"));
        trap.add("slowDuration", supplyTime(TacticalSupplySystem.TRAP_SLOW_TICKS, "TRAP_SLOW_TICKS", "trap_triggered"));
        trap.add("slowAmplifier", constant(TacticalSupplySystem.TRAP_SLOW_AMPLIFIER, "effect_amplifier", TacticalSupplySystem.class, "TRAP_SLOW_AMPLIFIER"));
        trap.add("revealDuration", supplyTime(TacticalSupplySystem.TRAP_REVEAL_TICKS, "TRAP_REVEAL_TICKS", "trap_triggered"));
        result.add("offers", offers);
        return result;
    }

    @FunctionalInterface
    private interface RegenerationAmount {
        float evaluate(float maximum);
    }

    private static void regeneration(JsonObject result, int duration, String durationSource, String amountSource,
            String unit, List<Integer> maxima, RegenerationAmount amount, String condition) {
        result.add("duration", supplyTime(duration, durationSource, "pill_used"));
        result.add("pulseInterval", supplyTime(TacticalSupplySystem.PILL_PULSE_TICKS, "PILL_PULSE_TICKS", "pill_used"));
        var samples = new JsonObject();
        for (int maximum : maxima) {
            samples.add(Integer.toString(maximum), at(amount.evaluate(maximum), unit, TacticalSupplySystem.class, amountSource,
                    "按当前上限计算该跳；存活且还有对应资源缺口；" + condition, "regeneration_pulse", REAL));
        }
        result.add("uncappedPulseSamplesByMaximum", samples);
    }

    private static JsonObject supplyTime(int value, String member, String sampledAt) {
        return at(value, "ticks", TacticalSupplySystem.class, member, "成功使用或触发后计时", sampledAt, REAL);
    }

    private static JsonObject economy() {
        var result = new JsonObject();
        result.add("openingPurse", at(PlayerData.OPENING_PURSE_GOLD, "gold", PlayerData.class, "OPENING_PURSE_GOLD",
                "本轮首次领用", "match_opening", NONE));
        result.add("globalIncomeMultiplier", constant(GlobalBalance.GOLD_INCOME_SCALE, "ratio", GlobalBalance.class, "GOLD_INCOME_SCALE"));
        result.add("passiveGoldBase", at(GameManager.PASSIVE_GOLD_BASE, "gold", GameManager.class, "PASSIVE_GOLD_BASE",
                "在线参赛者含阵亡等待，基础工资应用全局金币倍率", "wage_pulse", REAL));
        result.add("passiveGoldInterval", at(GameManager.PASSIVE_GOLD_INTERVAL_TICKS, "ticks",
                GameManager.class, "PASSIVE_GOLD_INTERVAL_TICKS", "开战后经过刻数的倍数", "wage_pulse", REAL));
        result.add("shopRadius", at(BaseRules.SHOP_RADIUS, "blocks", BaseRules.class, "SHOP_RADIUS",
                "补给需处于己方主世界泉水范围；按三维距离计，含 10 格边界",
                "purchase", NONE));
        result.add("attributeShopAccess", at("participant_alive_or_waiting", "rule_id", BaseRules.class, "canBuyAttributes",
                "正式对局开战后参赛者可在线上或泉水购点，阵亡等待仍可购点；训练沿用个人设置", "purchase", NONE));
        result.add("supplyShopAccess", at("participant_alive_at_own_fountain", "rule_id", BaseRules.class, "canBuySupplies",
                "正式对局开战后仅存活且在己方泉水范围的参赛者可购补给；训练沿用个人设置", "purchase", NONE));
        result.add("heroKillRewards", heroKills());
        result.add("assistContactWindow", at(MatchAssists.CONTACT_TICKS, "ticks", MatchAssists.class, "CONTACT_TICKS",
                "队列局战绩助攻：伤害或有效控制", "hero_death", REAL));
        result.add("assistProtectionWindow", at(MatchAssists.RESCUE_TICKS, "ticks", MatchAssists.class, "RESCUE_TICKS",
                "队列局战绩助攻：保护最终击杀者", "hero_death", REAL));
        result.add("assistPool", at(Map.of("gold", MatchBalance.ASSIST_GOLD_POOL, "xp", MatchBalance.ASSIST_XP_POOL),
                "gold_and_xp", MatchBalance.class, "ASSIST_GOLD_POOL,ASSIST_XP_POOL",
                "首轮 3v3 选定的奖池；金币为实际入账额。两个池分别按锁定名单均分并按序补余数。"
                        + "仅在线时到账；同局重连领取离线期间所得份额", "hero_death", NONE));
        result.add("farmReward", field("locked_seat_averages", "rule_id", source(GameManager.class, "farmBonus"),
                "开战后使用双方锁定席位；掉线冻结累计收入和等级；训练及任一队零席位使用基础值", "farm_reward", NONE));
        result.add("farmGoldGapWeight", constant(MatchBalance.FARM_GOLD_GAP_WEIGHT, "ratio", MatchBalance.class, "FARM_GOLD_GAP_WEIGHT"));
        result.add("farmLevelGapWeight", constant(MatchBalance.FARM_LEVEL_GAP_WEIGHT, "ratio_per_level", MatchBalance.class, "FARM_LEVEL_GAP_WEIGHT"));
        result.add("farmCatchUpCap", constant(MatchBalance.FARM_CATCH_UP_CAP, "ratio", MatchBalance.class, "FARM_CATCH_UP_CAP"));
        result.add("farmGoldIncomeFloor", constant(MatchBalance.FARM_GOLD_INCOME_FLOOR, "gold", MatchBalance.class, "FARM_GOLD_INCOME_FLOOR"));
        result.add("passiveGoldCatchUpWeight", at(MatchBalance.PASSIVE_GOLD_CATCH_UP_WEIGHT, "ratio", MatchBalance.class,
                "PASSIVE_GOLD_CATCH_UP_WEIGHT", "首轮 3v3 选定为零。主动发育沿用收入及等级追赶", "wage_pulse", NONE));
        result.add("farmRewardDynamicInputs", gap("missing_input", "team_income_and_hero_levels",
                source(GameManager.class, "farmBonus"), "追赶结算", "farm_reward", NONE,
                "需要开战锁定席位，以及事件前各席位的累计收入和等级。"));
        result.add("spiritVeinPulse", at(GameRules.VEIN_INCOME_PULSE_TICKS, "ticks", GameRules.class, "VEIN_INCOME_PULSE_TICKS",
                "已开启且完全控制灵脉", "vein_income", REAL));
        result.add("xpToNextByLevel", constant(IntStream.range(1, 20).map(LevelSystem::xpToNext).toArray(),
                "xp", LevelSystem.class, "xpToNext"));
        var respawns = new JsonObject();
        for (int level = 1; level <= 20; level++) {
            var row = new JsonObject();
            row.add("normal", at(HeroRespawnSystem.waitTicks(level, false), "ticks", HeroRespawnSystem.class, "waitTicks",
                    "无复活符文", "hero_death", REAL));
            row.add("withRespawnRune", at(HeroRespawnSystem.waitTicks(level, true), "ticks", HeroRespawnSystem.class, "waitTicks",
                    "复活符文生效并消耗", "hero_death", REAL));
            respawns.add(Integer.toString(level), row);
        }
        result.add("respawnByHeroLevel", respawns);
        return result;
    }

    private static JsonArray heroKills() {
        var result = new JsonArray();
        for (int[] input : new int[][]{{0, 0, 0, 0}, {4800, 0, 0, 0}, {9600, 0, 0, 0}, {9600, 5, 0, 3}, {9600, 0, 6, 0}}) {
            var sample = new JsonObject();
            sample.addProperty("elapsedTicks", input[0]);
            sample.addProperty("victimStreak", input[1]);
            sample.addProperty("victimDeathsWithoutKill", input[2]);
            sample.addProperty("victimLevelMinusKiller", input[3]);
            sample.add("preGlobalGold", at(MatchBalance.killReward(false, input[0], input[1], input[2], input[3]),
                    "gold", MatchBalance.class, "killReward", "不同阵营的合格击杀者", "hero_kill", REAL));
            sample.add("experience", at(MatchBalance.killReward(true, input[0], input[1], input[2], input[3]),
                    "xp", MatchBalance.class, "killReward", "不同阵营的合格击杀者", "hero_kill", REAL));
            result.add(sample);
        }
        return result;
    }

    private static JsonObject constant(Object value, String unit, Class<?> owner, String member) {
        return at(value, unit, owner, member, "当前服务端规则", "definition", NONE);
    }

    private static JsonObject at(Object value, String unit, Class<?> owner, String member,
            String condition, String sampledAt, String clock) {
        return field(value, unit, source(owner, member), condition, sampledAt, clock);
    }
}
