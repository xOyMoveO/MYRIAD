package com.myriad.game;

import com.google.gson.JsonObject;
import com.myriad.combat.DamageCalc;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The real mode selector, lobby receiver, match clock and settlement paths on two synthetic maps. */
final class ModeSupplyChecks {
    private static final Identifier FIRE_BONUS = Identifier.parse("myriad:fire_vein_bonus");
    private static final BlockPos PIG_CAMP = new BlockPos(72, 64, 42);

    private ModeSupplyChecks() {}

    static void rift(GameTestHelper helper) { run(helper, true); }
    static void cherry(GameTestHelper helper) { run(helper, false); }

    private static void run(GameTestHelper helper, boolean rift) {
        var suite = new ArenaFixtureChecks.Suite(helper.getLevel().getServer());
        String mode = rift ? "5v5" : "3v3";
        var events = new ArrayList<JsonObject>();
        try {
            suite.install();
            var tempoFile = MatchArena.configuration(suite.world, MatchArena.Mode.DUEL, "myriad_match_tempo.json");
            Files.writeString(tempoFile, "{\"profile\":\"cherry_v3\"}");
            if (rift) suite.check(MonsterManager.setSpawnPoint("fixture_gold_pig", PIG_CAMP, MonsterType.GOLD_PIG, false),
                    "pre-match synthetic map editor accepts an isolated gold-pig camp");
            suite.begin(mode);
            MatchTempo profile = rift ? MatchTempo.RIFT_V1 : MatchTempo.CHERRY_V3;
            var match = GameManager.get();
            var actor = suite.players.getFirst();
            var allySide = match.factionOf(actor);
            var enemySide = match.factionOf(suite.opponent(actor));
            long start = match.getMatchStartTime();
            var team = suite.players.stream().filter(p -> match.factionOf(p) == allySide).toList();
            var opponents = suite.players.stream().filter(p -> match.factionOf(p) == enemySide).toList();
            int wage = rift ? 11 : 14;
            suite.check(GameManager.combatTempo() == profile && GameManager.snapshot().getAsJsonObject().get("tempo")
                    .getAsString().equals(profile.name()), "selection locks and serializes " + profile);
            suite.check(suite.players.stream().allMatch(p -> Income.read(p).equals(new Income(500, 0))),
                    "all locked participants receive the same 500 opening purse and no starting XP");
            suite.check(BaseRules.canBuyAttributes(actor) && BaseRules.canBuySupplies(actor), "same shops at own fountain");
            Vec3 fountain = actor.position();
            actor.setPos(fountain.add(0, 0, 25));
            suite.check(BaseRules.canBuyAttributes(actor) && !BaseRules.canBuySupplies(actor), "same field attribute and supply permissions");
            actor.setPos(fountain);

            advance(suite, start + 99);
            award(suite, events, "before_wage", actor, new Income(500, 0), 0, 0);
            var beforeWage = readAll(suite.players);
            advance(suite, start + 100);
            teamAward(suite, events, "wage", suite.players, beforeWage, wage);

            int campAt = rift ? 600 : 400;
            var campPos = new BlockPos(rift ? 80 : 336, 64, 36);
            advance(suite, start + campAt - 1);
            suite.check(findCamp(suite, campPos, MonsterType.FROST_HARE).isEmpty(), "no small camp one tick before first spawn");
            advance(suite, start + campAt);
            var hare = findCamp(suite, campPos, MonsterType.FROST_HARE).getFirst();
            var beforeCamp = Income.read(actor);
            kill(suite, actor, hare);
            award(suite, events, "small_camp", actor, beforeCamp, rift ? 88 : 98, rift ? 23 : 80);

            int firstWave = rift ? 1400 : 700;
            advance(suite, start + firstWave - 1);
            suite.check(suite.units("MELEE", enemySide).isEmpty(), "no minions one tick before the first wave");
            advance(suite, start + firstWave);
            var minions = suite.units("MELEE", enemySide);
            suite.check(minions.size() == (rift ? 6 : 2), "first wave materializes on every configured lane");
            var beforeMinion = Income.read(actor);
            kill(suite, actor, minions.getFirst());
            award(suite, events, "melee_last_hit", actor, beforeMinion, rift ? 11 : 17, rift ? 12 : 65);

            var firstTower = LaneSystem.towerPoint(enemySide, LaneSystem.Lane.MID, 1);
            var tower = suite.units("TOWER", enemySide).stream().filter(t -> t.blockPosition().equals(firstTower)).findFirst().orElseThrow();
            var beforeTower = readAll(team);
            var otherTeam = readAll(opponents);
            kill(suite, actor, tower);
            teamAward(suite, events, "outer_tower", team, beforeTower, rift ? 158 : 210);
            suite.check(readAll(opponents).equals(otherTeam), "tower award only lands on the attacking team");

            if (rift) captureFireVein(suite, events, actor, team, start, wage);
            else suite.check(GameManager.snapshot().getAsJsonObject().getAsJsonObject("spiritVeins").isEmpty(),
                    "active 3v3 profile has no spirit vein income");

            var checkpoint = MatchProgress.checkpoint();
            var savedLanes = checkpoint.getAsJsonObject("lanes");
            var savedMonsters = checkpoint.getAsJsonObject("monsters");
            var checkpointFile = suite.world.resolve("mode-supply-" + mode + "-checkpoint.json");
            Files.writeString(checkpointFile, MatchProgress.JSON.toJson(checkpoint));
            if (rift) {
                var veinLayout = MatchArena.configuration(suite.world, MatchArena.Mode.RIFT, "myriad_vein_spawns.json");
                Files.writeString(veinLayout, "{\"enabled\":false}");
            } else {
                Files.writeString(tempoFile, "{\"profile\":\"cherry_v2\"}");
                MatchArena.initialize(suite.server);
                suite.check(MatchArena.selectedTempo() == MatchTempo.CHERRY_V2, "3v3 edit changes only the next selected profile");
            }
            GameManager.create();
            MatchProgress.restoreCheckpoint(suite.server, MatchProgress.JSON.fromJson(Files.readString(checkpointFile), JsonObject.class));
            suite.check(GameManager.get() != match && GameManager.combatTempo() == profile
                            && GameManager.get().getMatchStartTime() == start,
                    "new match manager restores locked profile and opening clock from the written checkpoint");
            suite.check(savedLanes.get("nextWave").equals(LaneSystem.snapshot().get("nextWave"))
                            && savedMonsters.get("respawns").equals(MonsterManager.snapshot().get("respawns")),
                    "round trip retains absolute wave and defeated-camp deadlines");
            if (rift) {
                var saved = checkpoint.getAsJsonObject("match");
                var restored = GameManager.snapshot().getAsJsonObject();
                suite.check(saved.get("spiritVeins").equals(restored.get("spiritVeins"))
                                && restored.get("spiritVeinAvailableAt").getAsLong() == start + 1200,
                        "checkpoint retains vein ownership and original deadline after a layout edit");
            }
            long nextWage = start + ((suite.server.overworld().getGameTime() - start) / 100 + 1) * 100;
            var afterRestore = readAll(suite.players);
            advance(suite, nextWage);
            int possibleVeinGold = rift && nextWage % 100 == 0 ? 4 : 0;
            award(suite, events, "wage_after_restore", actor, afterRestore.getFirst(), wage + possibleVeinGold,
                    possibleVeinGold == 0 ? 0 : 1);
            if (rift) {
                var pig = findCamp(suite, PIG_CAMP, MonsterType.GOLD_PIG).getFirst();
                advance(suite, start + 9600);
                var beforePig = readAll(team);
                var beforeRival = readAll(opponents);
                kill(suite, actor, pig);
                award(suite, events, "gold_pig_last_hitter", actor, beforePig.getFirst(), 360, 162);
                for (int i = 1; i < team.size(); i++) award(suite, events, "gold_pig_ally", team.get(i), beforePig.get(i), 360, 0);
                suite.check(team.size() == 5 && readAll(opponents).equals(beforeRival), "five locked installments total 1800 for the correct faction");
                legacyCheckpoint(suite, actor);
            } else {
                suite.check(LaneSystem.minionReward(18, true, true, 700, MatchTempo.CHERRY_V2) == 18,
                        "previous CHERRY_V2 keeps the original last-hit XP floor");
            }
            var report = new JsonObject();
            report.addProperty("mode", mode);
            report.addProperty("profile", profile.name());
            report.addProperty("players", suite.players.size());
            report.addProperty("matchStartTick", start);
            report.addProperty("firstWaveTick", start + firstWave);
            report.addProperty("firstSmallCampTick", start + campAt);
            report.addProperty("nextWaveAfterCheckpoint", savedLanes.get("nextWave").getAsLong());
            report.add("campRespawnsAfterCheckpoint", savedMonsters.get("respawns"));
            report.add("events", MatchProgress.JSON.toJsonTree(events));
            Files.writeString(suite.world.resolve("mode-supply-" + mode + ".json"), MatchProgress.JSON.toJson(report));
            suite.report("mode-supply-" + mode, null);
            helper.succeed();
        } catch (Exception | AssertionError failure) {
            suite.report("mode-supply-" + mode, failure);
            throw new IllegalStateException(failure);
        } finally {
            GameManager.get().resetToLobby(suite.server, false);
            suite.removePlayers();
        }
    }

    private static void captureFireVein(ArenaFixtureChecks.Suite suite, List<JsonObject> events,
            ServerPlayer actor, List<ServerPlayer> team, long start, int wage) {
        var match = GameManager.get();
        var center = match.spiritVeinSpawnSnapshot(false).get(2);
        actor.setPos(Vec3.atBottomCenterOf(center).add(0, 0, -4));
        for (int tick = 1401; tick <= 1520; tick++) advance(suite, start + tick);
        suite.check(match.ownedVeinStatus(actor, 8) == 1, "normal world ticks capture the uncontested fire vein");
        var bonus = actor.getAttribute(ModAttributes.PHYSICAL_ATTACK).getModifier(FIRE_BONUS);
        suite.check(bonus != null && bonus.amount() == .05, "fire vein grants exactly 5% physical attack");
        long pulse = (suite.server.overworld().getGameTime() / 100 + 1) * 100;
        advance(suite, pulse - 1);
        var before = readAll(team);
        advance(suite, pulse);
        for (int i = 0; i < team.size(); i++) award(suite, events, "vein_pulse", team.get(i), before.get(i),
                4 + ((pulse - start) % 100 == 0 ? wage : 0), 1);
        actor.setPos(Vec3.atBottomCenterOf(LaneSystem.fountainPoint(match.factionOf(actor))));
    }

    private static void legacyCheckpoint(ArenaFixtureChecks.Suite suite, ServerPlayer actor) {
        JsonObject current = GameManager.snapshot().getAsJsonObject();
        JsonObject legacy = current.deepCopy();
        legacy.remove("tempo");
        GameManager.restore(legacy, suite.server);
        suite.check(GameManager.combatTempo() == MatchTempo.STANDARD && MatchBalance.passiveGoldReward(actor) == 14,
                "an older 5v5 checkpoint without tempo keeps STANDARD and its 14-gold wage");
        legacy.addProperty("tempo", "STANDARD");
        GameManager.restore(legacy, suite.server);
        suite.check(GameManager.combatTempo() == MatchTempo.STANDARD && MatchBalance.passiveGoldReward(actor) == 14,
                "an explicitly locked 5v5 STANDARD version retains its legacy award");
        GameManager.restore(current, suite.server);
    }

    private static void advance(ArenaFixtureChecks.Suite suite, long tick) {
        suite.clock(tick);
        suite.tick();
    }

    private static List<Mob> findCamp(ArenaFixtureChecks.Suite suite, BlockPos center, MonsterType type) {
        return suite.server.overworld().getEntitiesOfClass(Mob.class, new AABB(center).inflate(5),
                mob -> mob.isAlive() && MonsterManager.typeOf(mob) == type);
    }

    private static void kill(ArenaFixtureChecks.Suite suite, ServerPlayer actor, Mob target) {
        DamageCalc.dealBasicDamage(actor, target, 10_000_000, DamageCalc.Kind.TRUE);
        suite.check(!target.isAlive(), "ordinary basic-hit settlement defeats the fixture target");
    }

    private record Income(int gold, int xp) {
        static Income read(ServerPlayer player) {
            var data = PlayerDataProvider.get(player);
            int xp = data.getXp();
            for (int level = 1; level < data.getLevel(); level++) xp += LevelSystem.xpToNext(level);
            return new Income(data.getGold(), xp);
        }
    }

    private static List<Income> readAll(List<ServerPlayer> players) {
        return players.stream().map(Income::read).toList();
    }

    private static void teamAward(ArenaFixtureChecks.Suite suite, List<JsonObject> events, String type,
            List<ServerPlayer> players, List<Income> before, int gold) {
        for (int i = 0; i < players.size(); i++) award(suite, events, type, players.get(i), before.get(i), gold, 0);
    }

    private static void award(ArenaFixtureChecks.Suite suite, List<JsonObject> events, String type,
            ServerPlayer player, Income before, int gold, int xp) {
        var after = Income.read(player);
        int actualGold = after.gold() - before.gold(), actualXp = after.xp() - before.xp();
        suite.check(actualGold == gold && actualXp == xp, type + " paid " + actualGold + " gold / " + actualXp
                + " XP, expected " + gold + " gold / " + xp + " XP");
        events.add(MatchProgress.JSON.toJsonTree(Map.of("type", type, "recipient", player.getScoreboardName(),
                "elapsedTicks", GameManager.get().getMatchElapsedTicks(suite.server.overworld().getGameTime()),
                "gold", actualGold, "experience", actualXp)).getAsJsonObject());
    }
}
