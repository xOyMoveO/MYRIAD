package com.myriad.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.character.skills.yike.YikeStance;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.MagicBullet;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.PlayerHealthSystem;
import com.myriad.registry.ModAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Two matched synthetic 3v3 rounds with actual wave entities, skills and reward settlement. */
final class BattlefieldLaneChecks {
    private BattlefieldLaneChecks() {}

    static void roster(GameTestHelper helper) {
        roster(helper, List.of(CharacterType.YIKE, CharacterType.SHUO, CharacterType.RIVET, CharacterType.CHIXIAO));
    }

    static void fullRoster(GameTestHelper helper) {
        roster(helper, List.of(CharacterType.playableValues()), true);
    }

    static void roster(GameTestHelper helper, List<CharacterType> roles) {
        roster(helper, roles, false);
    }

    private static void roster(GameTestHelper helper, List<CharacterType> roles, boolean fullRoster) {
        var suite = new ArenaFixtureChecks.Suite(helper.getLevel().getServer());
        var config = MatchArena.configuration(suite.world, MatchArena.Mode.DUEL, "myriad_match_tempo.json");
        var profile = fullRoster ? MatchTempo.CHERRY_V3 : MatchTempo.CHERRY_V2;
        try {
            suite.install();
            Files.writeString(config, "{\"profile\":\"" + profile.name().toLowerCase(java.util.Locale.ROOT) + "\"}");
            suite.begin("3v3");
            var match = GameManager.get();
            suite.check(GameManager.combatTempo() == profile, "Roster replay uses the selected 3v3 profile");
            var actor = suite.players.stream().filter(player -> "yike".equals(PlayerDataProvider.get(player).getCharacterId()))
                    .findFirst().orElseThrow();
            var initialLane = LaneSystem.snapshot();
            long start = match.getMatchStartTime();
            for (var role : roles) {
                suite.clock(start);
                LaneSystem.restore(suite.server.overworld(), initialLane.deepCopy());
                if (!fullRoster && role == CharacterType.YIKE) {
                    clearWave(suite, actor, profile, start, 3600, 4, 1, 500);
                    clearWave(suite, actor, profile, start, 9600, 10, 4, 2400);
                } else {
                    rosterWave(suite, actor, role, 3600, 4, 1, 500, null, false);
                    rosterWave(suite, actor, role, 9600, 10, 4, 2400, null, false);
                }
            }
            if (fullRoster) {
                suite.clock(start);
                LaneSystem.restore(suite.server.overworld(), initialLane.deepCopy());
                actor.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(match.factionOf(actor))));
                PlayerDataProvider.get(actor).setCharacterId(CharacterType.YIKE.getId());
                PlayerDataProvider.get(actor).setFirstGroupAttribute("strength", 0);
                PlayerDataProvider.get(actor).refundAllSkillPointsForTraining();
                clearWave(suite, actor, profile, start, 3600, 4, 1, 500);
                clearWave(suite, actor, profile, start, 9600, 10, 4, 2400);
                for (var probe : MAIN_LANE_PROBES) {
                    for (boolean escorted : new boolean[]{false, true}) {
                        suite.clock(start);
                        LaneSystem.restore(suite.server.overworld(), initialLane.deepCopy());
                        rosterWave(suite, actor, probe.role(), 3600, 4, 1, 500, probe, escorted);
                        rosterWave(suite, actor, probe.role(), 9600, 10, 4, 2400, probe, escorted);
                    }
                }
            }
            helper.succeed();
        } catch (Exception | AssertionError error) {
            throw new IllegalStateException(error);
        } finally {
            GameManager.get().resetToLobby(suite.server, false);
            suite.removePlayers();
            try {
                Files.deleteIfExists(config);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Failed to remove synthetic tempo fixture", error);
            }
        }
    }

    private record LaneProbe(CharacterType role, String attribute, int passive, int e, int r, List<Integer> midRanks) {
        List<Integer> skillIndices() { return List.of(2, 3, e, r); }
    }

    private static final List<LaneProbe> MAIN_LANE_PROBES = List.of(
            new LaneProbe(CharacterType.YIKE, "wisdom", 0, 4, 6, List.of(4, 3, 1, 2)),
            new LaneProbe(CharacterType.FENJUE, "strength", 0, 4, 6, List.of(3, 1, 4, 2)),
            new LaneProbe(CharacterType.HANZE, "wisdom", 0, 4, 6, List.of(3, 1, 4, 2)),
            new LaneProbe(CharacterType.LINGYUAN, "wisdom", 1, 5, 6, List.of(3, 1, 4, 2)),
            new LaneProbe(CharacterType.KEHENG, "wisdom", 0, 4, 7, List.of(3, 1, 4, 2)),
            new LaneProbe(CharacterType.LUOXIA, "strength", 1, 4, 7, List.of(4, 1, 3, 2)),
            new LaneProbe(CharacterType.CHIXIAO, "strength", 0, 5, 6, List.of(3, 1, 4, 2)),
            new LaneProbe(CharacterType.XUANCHEN, "wisdom", 0, 5, 7, List.of(4, 1, 3, 2)));

    private static void rosterWave(ArenaFixtureChecks.Suite suite, ServerPlayer hero, CharacterType role, long elapsed,
            int heroLevel, int points, int budget, LaneProbe probe, boolean escorted) throws Exception {
        var level = suite.server.overworld();
        HeroRespawnSystem.reset(suite.server, List.of(hero.getUUID()));
        var match = GameManager.get();
        long start = match.getMatchStartTime();
        stageWave(suite, start, elapsed);
        var side = match.factionOf(hero);
        var defender = match.factionOf(suite.opponent(hero));
        var data = PlayerDataProvider.get(hero);
        AbilityRuntime.clear(hero);
        BasicAttackSystem.clearFor(hero.getUUID());
        data.clearSkillCooldowns();
        data.setCharacterId(role.getId());
        data.refundAllSkillPointsForTraining();
        data.setLevel(heroLevel);
        data.setXp(0);
        data.setSkillPoints(heroLevel);
        data.setGold(budget);
        data.setFirstGroupAttribute("strength", 0);
        data.setFirstGroupAttribute("wisdom", 0);
        hero.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(side)));
        String attribute = probe == null ? "strength" : probe.attribute();
        for (int i = 0; i < points; i++) {
            suite.check(FirstGroupGrowth.buy(hero, data, attribute), "Fixed attribute purchase uses the paid path");
        }
        var opener = FirstGroupSkills.node(role.getId(), 2);
        if (probe == null) {
            learn(suite, hero, opener, heroLevel >= 10 ? 5 : 1);
        } else {
            learn(suite, hero, FirstGroupSkills.node(role.getId(), probe.passive()), 1);
            var indices = probe.skillIndices();
            for (int i = 0; i < indices.size(); i++) {
                learn(suite, hero, FirstGroupSkills.node(role.getId(), indices.get(i)),
                        heroLevel >= 10 ? probe.midRanks().get(i) : 1);
            }
        }
        int qRank = data.skillLevel(opener.getId());
        PlayerDataProvider.applyChosenCharacter(hero, data);
        PlayerHealthSystem.fill(hero);
        data.setMana((float) hero.getAttributeValue(ModAttributes.MAX_MANA));
        hero.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        hero.getInventory().setSelectedSlot(0);
        boolean melee = VanillaMeleeAttack.usesVanilla(role);
        double range = probe == null ? 1.8 : melee ? 2.1 : 5.75;
        double direction = side == Faction.GUARDIAN ? 1 : -1;
        var approach = probe == null ? new Vec3(0, 0, range) : new Vec3(direction * range, 0, 0);
        var center = probe == null ? new Vec3(320.5, 64, 80.5 + range) : new Vec3(336.5, 64, 64.5);
        var origin = center.subtract(approach);
        hero.setPos(origin);
        hero.setOnGround(true);
        hero.setDeltaMovement(Vec3.ZERO);
        var enemies = wave(suite, defender, center);
        var allies = escorted ? wave(suite, side, center.add(-direction * 1.7, 0, 0)) : List.<Mob>of();
        for (var player : suite.players) {
            if (player != hero) player.setPos(new Vec3(290.5, 64, 101.5));
        }
        int startGold = data.getGold();
        int startXp = totalExperience(data);
        float startHp = enemies.stream().map(LaneSystem::health).reduce(0F, Float::sum);
        var strikes = new JsonArray();
        var casts = new JsonArray();
        var initialRanks = new JsonObject();
        data.getSkillLevels().forEach(initialRanks::addProperty);
        var basic = new LaneBasics(hero, role);
        var actionNodes = probe == null ? List.of(opener) : List.of(
                FirstGroupSkills.node(role.getId(), probe.r()), FirstGroupSkills.node(role.getId(), probe.e()),
                opener, FirstGroupSkills.node(role.getId(), 3));
        int finish = 0;
        int observedTicks = 0;
        int horizon = probe == null ? 1500 : (int) Math.min(1500,
                LaneSystem.snapshot().get("nextWave").getAsLong() - start - elapsed - 1);
        for (int tick = 1; tick <= horizon; tick++) {
            observedTicks = tick;
            if (HeroRespawnSystem.isWaiting(hero)) break;
            suite.clock(start + elapsed + tick);
            var target = enemies.stream().filter(Mob::isAlive).findFirst().orElse(null);
            if (target == null) {
                finish = tick - 1;
                break;
            }
            float before = waveHealth(enemies);
            if (!AbilityRuntime.moving(hero) && !AbilityRuntime.channeling(hero) && !YikeStance.active(hero)
                    && hero.distanceToSqr(target) > (range + .7) * (range + .7)) {
                hero.setPos(target.position().subtract(approach));
                hero.setDeltaMovement(Vec3.ZERO);
                hero.setOnGround(true);
            }
            aim(hero, target.getBoundingBox().getCenter());
            var aimPoint = escorted ? target.position().add(0, -.02, 0) : center.add(0, -.02, 0);
            boolean cast = probe != null && tick % 2 == 0
                    && tryLaneCast(hero, data, actionNodes, aimPoint, target, tick, casts);
            if (!cast) {
                aim(hero, target.getBoundingBox().getCenter());
                if (!basic.tick(tick, target) && probe == null && tick % 2 == 0) {
                    tryLaneCast(hero, data, actionNodes, aimPoint, target, tick, casts);
                }
            }
            AbilityRuntime.tick(hero);
            if (MagicBullet.isMage(hero)) MagicBullet.tickBullets(hero, data);
            data.tickCooldowns();
            if (probe != null && tick % 20 == 0) {
                data.setMana((float) Math.min(hero.getAttributeValue(ModAttributes.MAX_MANA),
                        data.getMana() + hero.getAttributeValue(ModAttributes.MANA_REGEN)));
            }
            float afterHero = waveHealth(enemies);
            waveDamage(strikes, tick, "hero_inputs", before, afterHero);
            if (escorted) {
                LaneSystem.tick(level);
                waveDamage(strikes, tick, "lane_tick", afterHero, waveHealth(enemies));
                for (var ally : allies) advanceMinion(ally);
            }
            for (var enemy : enemies) {
                if (escorted) advanceMinion(enemy);
                else enemy.damageCooldownTime = Math.max(0, enemy.damageCooldownTime - 1);
            }
        }
        var row = sample("clear_wave", GameManager.combatTempo(), elapsed);
        row.addProperty("variant", probe == null ? "clustered_q_basic_fixed_reengage" : "legal_rotation");
        row.addProperty("escorted", escorted);
        row.addProperty("hero", role.getId());
        row.addProperty("heroLevel", heroLevel);
        row.addProperty("heroLevelAtEnd", data.getLevel());
        row.addProperty("strength", data.firstGroupAttribute("strength"));
        row.addProperty("wisdom", data.firstGroupAttribute("wisdom"));
        row.addProperty("qRank", qRank);
        row.addProperty("budget", budget);
        row.addProperty("wave", LaneSystem.snapshot().get("wave").getAsInt());
        row.addProperty("startWaveHealth", startHp);
        row.addProperty("finishTicks", finish);
        row.addProperty("observedTicks", observedTicks);
        row.addProperty("heroDefeated", HeroRespawnSystem.isWaiting(hero));
        row.addProperty("endWaveHealth", waveHealth(enemies));
        row.addProperty("earnedGold", data.getGold() - startGold);
        row.addProperty("earnedExperience", totalExperience(data) - startXp);
        row.addProperty("startingBasicType", role == CharacterType.LUOXIA ? "bow" : role == CharacterType.YIKE ? "cannon"
                : melee ? "melee" : "magic");
        row.addProperty("endMana", data.getMana());
        row.add("allocatedSkillRanks", initialRanks);
        row.add("castEvents", casts);
        row.add("healthLossEvents", strikes);
        log(row);
        suite.check(strikes.asList().stream().anyMatch(event ->
                        event.getAsJsonObject().get("source").getAsString().equals("hero_inputs")),
                "Actual role inputs damage the wave for " + role);
    }

    private static void learn(ArenaFixtureChecks.Suite suite, ServerPlayer hero, AbilitySkill skill, int rank) {
        for (int i = 0; i < rank; i++) PlayerDataProvider.upgradeSkill(hero, skill.getId());
        int expected = skill.isPassive() ? FirstGroupSkills.passiveRank(PlayerDataProvider.get(hero).getLevel()) : rank;
        suite.check(PlayerDataProvider.get(hero).skillLevel(skill.getId()) == expected, "Legal ranks for " + skill.getId());
    }

    private static List<Mob> wave(ArenaFixtureChecks.Suite suite, Faction faction, Vec3 center) throws Exception {
        var units = new ArrayList<Mob>();
        for (String kind : List.of("MELEE", "RANGED", "MAGIC")) units.addAll(suite.units(kind, faction));
        suite.check(units.size() == 4, "Roster replay starts with a real four-unit wave");
        for (int i = 0; i < units.size(); i++) {
            var unit = units.get(i);
            unit.setPos(center.add(i % 2 == 0 ? -.4 : .4, 0, i < 2 ? -.30 : .30));
            unit.setOnGround(true);
            unit.setDeltaMovement(Vec3.ZERO);
        }
        return units;
    }

    private static boolean tryLaneCast(ServerPlayer hero, PlayerData data, List<AbilitySkill> candidates,
            Vec3 point, Mob target, int tick, JsonArray casts) {
        if (AbilityRuntime.moving(hero) || AbilityRuntime.channeling(hero) || AbilityRuntime.winding(hero) != null) return false;
        for (var skill : candidates) {
            int cooldown = data.getCooldown(skill.getId());
            float mana = data.getMana();
            var cast = AbilityRuntime.context(hero, skill);
            // Keep committed channels and stance effects running. Rainbow Shell's readiness honors its stance.
            if (skill.recastAvailable(cast) || !skill.cooldownReady(cast) || mana < skill.manaCost(cast.rank)) continue;
            aim(hero, skill.needsPoint() ? point : target.getBoundingBox().getCenter());
            SkillSystem.cast(hero, skill.slot());
            if (data.getPlacementType() != 0) {
                AbilityRuntime.confirm(hero);
                if (data.getPlacementType() != 0) AbilityRuntime.cancel(hero);
            }
            boolean preparing = !AbilityRuntime.preparingSkill(hero).isEmpty() || AbilityRuntime.winding(hero) != null;
            if (mana != data.getMana() || cooldown != data.getCooldown(skill.getId()) || preparing) {
                var event = new JsonObject();
                event.addProperty("tick", tick);
                event.addProperty("skill", skill.getId());
                event.addProperty("manaSpentAtInput", mana - data.getMana());
                event.addProperty("cooldownAtInput", data.getCooldown(skill.getId()));
                event.addProperty("preparingAfterInput", preparing);
                if (YikeStance.active(hero)) event.addProperty("artilleryShotsRemainingAtInput", YikeStance.shots(hero));
                casts.add(event);
                return true;
            }
        }
        return false;
    }

    private static float waveHealth(List<Mob> units) {
        return units.stream().filter(Mob::isAlive).map(LaneSystem::health).reduce(0F, Float::sum);
    }

    private static void waveDamage(JsonArray strikes, int tick, String source, float before, float after) {
        if (before <= after) return;
        var strike = new JsonObject();
        strike.addProperty("tick", tick);
        strike.addProperty("source", source);
        strike.addProperty("effectiveDamage", before - after);
        strike.addProperty("waveHealthLeft", after);
        strikes.add(strike);
    }

    private static void advanceMinion(Mob minion) {
        minion.damageCooldownTime = Math.max(0, minion.damageCooldownTime - 1);
        if (minion.isAlive()) minion.move(MoverType.SELF, minion.getDeltaMovement());
    }

    private static final class LaneBasics {
        private final ServerPlayer hero;
        private final CharacterType role;
        private int next = 1;

        LaneBasics(ServerPlayer hero, CharacterType role) {
            this.hero = hero;
            this.role = role;
        }

        boolean tick(int tick, Mob target) {
            if (tick < next || !BasicAttackSystem.canAttackNow(hero, PlayerDataProvider.get(hero))
                    || AbilityRuntime.channeling(hero)) return false;
            if (role == CharacterType.LUOXIA) {
                if (LuoxiaBow.drawing(hero)) {
                    BasicAttackSystem.tryAttack(hero, LuoxiaBow.RELEASE);
                    next = tick + 1;
                } else {
                    BasicAttackSystem.tryAttack(hero, LuoxiaBow.PRESS);
                    next = tick + (LuoxiaBow.drawing(hero) ? (int) Math.ceil(LuoxiaBow.NORMAL_WORK /
                            LuoxiaBow.drawRate(hero.getAttributeValue(ModAttributes.ATTACK_SPEED), 1)) : 1);
                }
            } else {
                if (role == CharacterType.YIKE) BasicAttackSystem.tryAttack(hero, 0);
                else if (MagicBullet.isMage(hero)) MagicBullet.tryFire(hero, hero.getMainHandItem());
                else DamageGrowthChecks.swing(hero, target);
                next = tick + BasicAttackSystem.cooldownTotal(hero);
            }
            return true;
        }
    }

    static void run(GameTestHelper helper) {
        var level = helper.getLevel();
        var suite = new ArenaFixtureChecks.Suite(level.getServer());
        var config = MatchArena.configuration(suite.world, MatchArena.Mode.DUEL, "myriad_match_tempo.json");
        try {
            suite.install();
            for (var tempo : List.of(MatchTempo.CHERRY_V1, MatchTempo.CHERRY_V2)) {
                Files.writeString(config, "{\"profile\":\"cherry_" + (tempo == MatchTempo.CHERRY_V1 ? "v1" : "v2") + "\"}");
                suite.begin("3v3");
                suite.check(GameManager.combatTempo() == tempo, "the real draft locks its map combat profile");
                long start = GameManager.get().getMatchStartTime();
                var hero = suite.players.stream().filter(p -> "yike".equals(PlayerDataProvider.get(p).getCharacterId()))
                        .findFirst().orElseThrow();
                checkLock(suite, config, tempo, start);
                clearWave(suite, hero, tempo, start, 3600, 4, 1, 500);
                clearWave(suite, hero, tempo, start, 9600, 10, 4, 2400);
                unlockSiege(suite, hero);
                sampleBoundary(suite, hero, tempo, start, 19180, 19200, 19220);
                sampleBoundary(suite, hero, tempo, start, 23980, 24000, 24020);
                GameManager.get().resetToLobby(suite.server, false);
                suite.clock(level.getGameTime() + 1);
                suite.tick();
            }
            suite.report("battlefield-lane", null);
            helper.succeed();
        } catch (Exception | AssertionError error) {
            suite.report("battlefield-lane", error);
            throw new IllegalStateException(error);
        } finally {
            GameManager.get().resetToLobby(suite.server, false);
            suite.removePlayers();
            try {
                Files.deleteIfExists(config);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Failed to remove synthetic tempo fixture", error);
            }
        }
    }

    private static void checkLock(ArenaFixtureChecks.Suite suite, Path config, MatchTempo tempo, long start) throws Exception {
        var level = suite.server.overworld();
        suite.clock(start + 699);
        LaneSystem.tick(level);
        suite.check(LaneSystem.snapshot().get("wave").getAsInt() == 0, "no wave before tick 700");
        suite.clock(start + 700);
        LaneSystem.tick(level);
        var lanes = LaneSystem.snapshot();
        var match = GameManager.snapshot().getAsJsonObject();
        suite.check(lanes.get("wave").getAsInt() == 1 && lanes.get("nextWave").getAsLong() == start + 1500,
                "first wave starts at 700 with an absolute next deadline");
        Files.writeString(config, "{\"profile\":\"cherry_" + (tempo == MatchTempo.CHERRY_V1 ? "v2" : "v1") + "\"}");
        MatchArena.initialize(suite.server);
        suite.check(MatchArena.selectedTempo() != tempo && GameManager.combatTempo() == tempo,
                "configuration changes leave an open match on its original profile");
        var old = match.deepCopy();
        old.remove("tempo");
        GameManager.restore(old, suite.server);
        suite.check(GameManager.combatTempo() == MatchTempo.STANDARD, "a pre-profile checkpoint restores standard rules");
        var unsupported = match.deepCopy();
        unsupported.addProperty("tempo", "unknown_tempo");
        try {
            GameManager.restore(unsupported, suite.server);
            throw new AssertionError("An unknown persisted version cannot silently resume under standard rules");
        } catch (IllegalArgumentException expected) {
            suite.check(GameManager.combatTempo() == MatchTempo.STANDARD,
                    "an unsupported checkpoint leaves the running manager unchanged");
        }
        GameManager.restore(match, suite.server);
        LaneSystem.restore(level, lanes);
        suite.check(GameManager.combatTempo() == tempo && LaneSystem.snapshot().get("nextWave").equals(lanes.get("nextWave")),
                "restoring the match and lane state keeps the locked profile and wave deadline");
        var row = sample("checkpoint", tempo, 700);
        row.addProperty("configuredProfileAfterChange", MatchArena.selectedTempo().name());
        row.addProperty("restoredProfile", GameManager.combatTempo().name());
        row.addProperty("wave", 1);
        row.addProperty("nextWaveElapsedTicks", lanes.get("nextWave").getAsLong() - start);
        log(row);
    }

    private static void clearWave(ArenaFixtureChecks.Suite suite, ServerPlayer hero, MatchTempo tempo, long start,
            long elapsed, int heroLevel, int wisdom, int budget) throws Exception {
        stageWave(suite, start, elapsed);
        var level = suite.server.overworld();
        var match = GameManager.get();
        var side = match.factionOf(hero);
        var defender = match.factionOf(suite.opponent(hero));
        var data = PlayerDataProvider.get(hero);
        AbilityRuntime.clear(hero);
        data.clearSkillCooldowns();
        data.setLevel(heroLevel);
        data.setXp(0);
        data.setSkillPoints(heroLevel);
        data.setGold(budget);
        data.setFirstGroupAttribute("wisdom", 0);
        for (int node : new int[]{2, 3, 6}) data.setSkillLevel(FirstGroupSkills.node("yike", node).getId(), 0);
        hero.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(side)));
        for (int i = 0; i < wisdom; i++) suite.check(FirstGroupGrowth.buy(hero, data, "wisdom"), "payment for fixed Wisdom build");
        for (int i = 0; i < heroLevel - 2; i++) {
            PlayerDataProvider.upgradeSkill(hero, FirstGroupSkills.node("yike", i < Math.min(5, heroLevel - 3) ? 2 : 3).getId());
        }
        var ultimate = FirstGroupSkills.node("yike", 6);
        for (int i = 0; i < (heroLevel >= 8 ? 2 : 1); i++) PlayerDataProvider.upgradeSkill(hero, ultimate.getId());
        suite.check(data.skillLevel(ultimate.getId()) == (heroLevel >= 8 ? 2 : 1), "fixed legal ultimate ranks learned");
        PlayerDataProvider.applyChosenCharacter(hero, data);
        PlayerHealthSystem.fill(hero);
        data.setMana((float) hero.getAttributeValue(ModAttributes.MAX_MANA));
        hero.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        hero.getInventory().setSelectedSlot(0);

        var enemies = new ArrayList<Mob>();
        for (String kind : List.of("MELEE", "RANGED", "MAGIC")) enemies.addAll(suite.units(kind, defender));
        suite.check(enemies.size() == 4, "clear-line trial starts with exactly one real four-unit wave");
        var origin = new Vec3(320.5,64,80.5);
        var center = origin.add(0, 0, 5.75);
        hero.setPos(origin);
        hero.setOnGround(true);
        hero.setDeltaMovement(Vec3.ZERO);
        for (int i = 0; i < enemies.size(); i++) {
            double x = i % 2 == 0 ? -.55 : .55;
            enemies.get(i).setPos(center.add(x, 0, i < 2 ? -.55 : .55));
        }
        for (var player : suite.players) {
            if (player != hero) player.setPos(new Vec3(290.5,64,101.5));
        }
        int startGold = data.getGold();
        int startXp = totalExperience(data);
        float startHp = enemies.stream().map(LaneSystem::health).reduce(0F, Float::sum);
        aim(hero, center);
        SkillSystem.cast(hero, 3);
        AbilityRuntime.confirm(hero);
        var strikes = new com.google.gson.JsonArray();
        int finish = 0;
        for (int tick = 1; tick <= 1500; tick++) {
            suite.clock(start + elapsed + tick);
            var target = enemies.stream().filter(Mob::isAlive).findFirst().orElse(null);
            if (target == null) {
                finish = tick - 1;
                break;
            }
            float before = enemies.stream().map(LaneSystem::health).reduce(0F, Float::sum);
            if (tick == 28) {
                aim(hero, center);
                SkillSystem.cast(hero, 1);
                AbilityRuntime.confirm(hero);
            } else {
                aim(hero, target.getBoundingBox().getCenter());
                if (tick % 2 == 0) SkillSystem.cast(hero, 0);
                else BasicAttackSystem.tryAttack(hero, 0);
            }
            AbilityRuntime.tick(hero);
            data.tickCooldowns();
            float after = enemies.stream().map(LaneSystem::health).reduce(0F, Float::sum);
            if (before > after) {
                var strike = new JsonObject();
                strike.addProperty("tick", tick);
                strike.addProperty("effectiveDamage", before - after);
                strike.addProperty("waveHealthLeft", after);
                strikes.add(strike);
            }
        }
        var row = sample("clear_wave", tempo, elapsed);
        row.addProperty("variant", "clustered_hero_only");
        row.addProperty("hero", data.getCharacterId());
        row.addProperty("heroLevel", heroLevel);
        row.addProperty("wisdom", wisdom);
        row.addProperty("qRank", data.skillLevel(FirstGroupSkills.node("yike", 2).getId()));
        row.addProperty("wRank", data.skillLevel(FirstGroupSkills.node("yike", 3).getId()));
        row.addProperty("rRank", data.skillLevel(ultimate.getId()));
        row.addProperty("spellPower", hero.getAttributeValue(ModAttributes.SPELL_POWER));
        row.addProperty("budget", budget);
        row.addProperty("wave", LaneSystem.snapshot().get("wave").getAsInt());
        row.addProperty("startWaveHealth", startHp);
        row.addProperty("finishTicks", finish);
        row.addProperty("endWaveHealth", enemies.stream().filter(Mob::isAlive).map(LaneSystem::health).reduce(0F, Float::sum));
        row.addProperty("earnedGold", data.getGold() - startGold);
        row.addProperty("earnedExperience", totalExperience(data) - startXp);
        row.add("healthLossEvents", strikes);
        log(row);
        suite.check(finish > 0, "real Yike attacks and skills clear the full four-unit wave");
    }

    private static void aim(ServerPlayer player, Vec3 point) {
        Vec3 delta = point.subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }

    private static void unlockSiege(ArenaFixtureChecks.Suite suite, ServerPlayer hero) throws Exception {
        var opponent = suite.opponent(hero);
        var defending = GameManager.get().factionOf(hero);
        for (int tier : LaneSystem.enabledTowerTiers()) {
            var point = LaneSystem.towerPoint(defending, LaneSystem.Lane.MID, tier);
            var tower = suite.units("TOWER", defending).stream().filter(mob -> mob.blockPosition().equals(point))
                    .findFirst().orElseThrow();
            DamageCalc.dealBasicDamage(opponent, tower, 1_000_000, DamageCalc.Kind.TRUE);
            suite.check(!tower.isAlive(), "real tower death unlocks siege substitution after tier " + tier);
        }
    }

    private static void sampleBoundary(ArenaFixtureChecks.Suite suite, ServerPlayer hero, MatchTempo tempo,
            long start, long... samples) throws Exception {
        stageWave(suite, start, samples[0]);
        var level = suite.server.overworld();
        var enemy = GameManager.get().factionOf(suite.opponent(hero));
        var data = PlayerDataProvider.get(hero);
        var previousHealth = new EnumMap<LaneSystem.Kind, Float>(LaneSystem.Kind.class);
        var previousAttack = new EnumMap<LaneSystem.Kind, Float>(LaneSystem.Kind.class);
        for (long elapsed : samples) {
            suite.clock(start + elapsed);
            LaneSystem.tick(level);
            var lanes = LaneSystem.snapshot();
            var goldBeforeWave = data.getGold();
            var xpBeforeWave = totalExperience(data);
            for (var kind : List.of(LaneSystem.Kind.MELEE, LaneSystem.Kind.RANGED, LaneSystem.Kind.MAGIC, LaneSystem.Kind.SIEGE)) {
                JsonObject unit = null;
                for (var value : lanes.getAsJsonArray("units")) {
                    var entity = value.getAsJsonObject();
                    if (entity.get("kind").getAsString().equals(kind.name()) && entity.get("faction").getAsString().equals(enemy.getName())) {
                        unit = entity;
                        break;
                    }
                }
                if (unit == null) throw new AssertionError("Missing " + kind + " at wave " + lanes.get("wave"));
                float health = unit.get("maxHealth").getAsFloat();
                float attack = unit.get("attackDamage").getAsFloat();
                Float oldHealth = previousHealth.put(kind, health);
                Float oldAttack = previousAttack.put(kind, attack);
                if (oldHealth != null && tempo == MatchTempo.CHERRY_V2) {
                    suite.check(health > oldHealth && health < oldHealth * 1.01F
                            && attack > oldAttack && attack < oldAttack * 1.01F,
                            "candidate managed health and attack grow continuously around the inspected boundary");
                }
                var target = suite.units(kind.name(), enemy).getFirst();
                int goldBefore = data.getGold();
                int xpBefore = totalExperience(data);
                int baseGold = LaneSystem.minionReward(LaneSystem.minionBaseGold(kind, tempo), true, false, elapsed, tempo);
                int baseXp = LaneSystem.minionReward(LaneSystem.minionBaseXp(kind), true, true, elapsed, tempo);
                int expectedGold = GlobalBalance.goldIncome(MatchBalance.farmReward(hero, baseGold, false));
                int expectedXp = MatchBalance.farmReward(hero, baseXp, true);
                DamageCalc.dealBasicDamage(hero, target, 10_000_000, DamageCalc.Kind.TRUE);
                var row = sample("minion_death", tempo, elapsed);
                row.addProperty("kind", kind.name());
                row.addProperty("wave", lanes.get("wave").getAsInt());
                row.add("maxHealth", unit.get("maxHealth"));
                row.add("attack", unit.get("attackDamage"));
                row.addProperty("goldBeforeCatchUpAndGlobalScale", baseGold);
                row.addProperty("experienceBeforeCatchUp", baseXp);
                row.addProperty("gold", data.getGold() - goldBefore);
                row.addProperty("experience", totalExperience(data) - xpBefore);
                log(row);
                suite.check(!target.isAlive() && data.getGold() - goldBefore == expectedGold
                        && totalExperience(data) - xpBefore == expectedXp, "real minion death matches the profile's reward rules");
            }
            suite.check(data.getGold() > goldBeforeWave && totalExperience(data) > xpBeforeWave, "the mixed wave pays active gold and experience");
            LaneSystem.restore(level, lanes);
        }
    }

    private static void stageWave(ArenaFixtureChecks.Suite suite, long start, long elapsed) throws Exception {
        var level = suite.server.overworld();
        var deadline = LaneSystem.snapshot().get("nextWave").getAsLong();
        while (deadline <= start + elapsed) {
            discardMinions(suite);
            suite.clock(Math.max(level.getGameTime(), deadline));
            LaneSystem.tick(level);
            deadline = LaneSystem.snapshot().get("nextWave").getAsLong();
        }
        suite.clock(start + elapsed);
        LaneSystem.tick(level);
    }

    private static void discardMinions(ArenaFixtureChecks.Suite suite) throws Exception {
        for (String kind : List.of("MELEE", "RANGED", "MAGIC", "SIEGE")) {
            for (var mob : suite.units(kind, null)) {
                LaneSystem.onDeath(mob, null, true);
                mob.discard();
            }
        }
    }

    private static int totalExperience(PlayerData data) {
        int xp = data.getXp();
        for (int level = 1; level < data.getLevel(); level++) xp += LevelSystem.xpToNext(level);
        return xp;
    }

    private static JsonObject sample(String scenario, MatchTempo tempo, long elapsed) {
        var row = new JsonObject();
        row.addProperty("scenario", scenario);
        row.addProperty("profile", tempo.name());
        row.addProperty("elapsedTicks", elapsed);
        return row;
    }

    private static void log(JsonObject row) {
        System.out.println("MYRIAD_BATTLEFIELD_LANE " + row);
    }
}
