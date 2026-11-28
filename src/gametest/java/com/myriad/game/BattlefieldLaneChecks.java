package com.myriad.game;

import com.google.gson.JsonObject;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Two matched synthetic 3v3 rounds with actual wave entities, skills and reward settlement. */
final class BattlefieldLaneChecks {
    private BattlefieldLaneChecks() {}

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

        var enemies = new ArrayList<Mob>();
        for (String kind : List.of("MELEE", "RANGED", "MAGIC")) enemies.addAll(suite.units(kind, defender));
        suite.check(enemies.size() == 4, "clear-line trial starts with exactly one real four-unit wave");
        var origin = new Vec3(320.5,64,80.5);
        var center = origin.add(0, 0, 5.75);
        hero.setPos(origin);
        hero.setOnGround(true);
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
        int finish = 0;
        for (int tick = 1; tick <= 1500; tick++) {
            suite.clock(start + elapsed + tick);
            var target = enemies.stream().filter(Mob::isAlive).findFirst().orElse(null);
            if (target == null) {
                finish = tick - 1;
                break;
            }
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
