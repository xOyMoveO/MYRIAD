package com.myriad.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myriad.Myriad;
import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.MagicBullet;
import com.myriad.combat.VanillaMeleeAttack;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Synthetic single-lane replay. Holds the input builds and positions across tempo profiles. */
final class BattlefieldBuildingChecks {
    private static final long EARLY = 3600;
    private static final long ENDGAME = 21600;
    private static final int BASIC_WINDOW = 120;
    private static final int HELD_GOLD = 500;
    private static final List<CharacterType> PUSHERS = List.of(CharacterType.LUOXIA, CharacterType.SHUO, CharacterType.QINGLUO);
    private static final List<CharacterType> OPPONENTS = List.of(CharacterType.RIVET, CharacterType.YINGXIAO, CharacterType.XUANCHEN);
    private static final Vec3 HOLDING = new Vec3(336.5, 64, 115.5);

    private record Build(int level, int strength, int wisdom, int speed) {
        void apply(ServerPlayer player) {
            AbilityRuntime.clear(player);
            var data = PlayerDataProvider.get(player);
            data.setLevel(level);
            data.setGold(HELD_GOLD);
            data.setFirstGroupAttribute("strength", strength);
            data.setFirstGroupAttribute("wisdom", wisdom);
            data.setFirstGroupAttribute("speed", speed);
            PlayerDataProvider.applyChosenCharacter(player, data);
            player.getInventory().setSelectedSlot(0);
            player.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
            player.setGameMode(GameType.SURVIVAL);
            player.damageCooldownTime = 0;
            player.setOnGround(true);
            if (CharacterType.LUOXIA.getId().equals(data.getCharacterId())) {
                data.setSkillLevel(FirstGroupSkills.node("luoxia", 1).getId(), FirstGroupSkills.passiveRank(level));
            }
        }
    }

    private static final Build OPENING = new Build(1, 0, 0, 0);
    private static final Build MIDGAME = new Build(10, 3, 2, 2);
    private static final Build LATE = new Build(18, 5, 3, 4);

    static void run(GameTestHelper helper) {
        var suite = new ArenaFixtureChecks.Suite(helper.getLevel().getServer());
        try {
            suite.install();
            var layout = MatchArena.configuration(suite.world, MatchArena.Mode.DUEL, "myriad_lane_layout.json");
            try (var resource = BattlefieldBuildingChecks.class.getResourceAsStream("/arena-fixture/buildings_lane_layout.json")) {
                if (resource == null) throw new IllegalStateException("Missing synthetic three-tier building fixture");
                Files.copy(resource, layout, StandardCopyOption.REPLACE_EXISTING);
            }
            suite.command("myriad mod on");
            for (int seat = 0; seat < 6; seat++) suite.player(seat);
            for (var profile : MatchTempo.values()) {
                if (!profile.name().startsWith("CHERRY_")) continue;
                try {
                    var replay = new Replay(suite, profile);
                    replay.towerRules();
                    replay.pureBasics();
                    replay.wholeWaves();
                    replay.fountains();
                    replay.crystal();
                    replay.progression();
                } finally {
                    GameManager.get().resetToLobby(suite.server, false);
                }
            }
            helper.succeed();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        } finally {
            suite.removePlayers();
        }
    }

    private static final class Replay {
        private final ArenaFixtureChecks.Suite suite;
        private final ServerLevel level;
        private final MatchTempo profile;
        private final GameManager match;
        private final long start;
        private final JsonObject freshLane;
        private final Map<CharacterType, ServerPlayer> heroes = new EnumMap<>(CharacterType.class);

        Replay(ArenaFixtureChecks.Suite suite, MatchTempo profile) throws Exception {
            this.suite = suite;
            this.level = suite.server.overworld();
            this.profile = profile;
            var tempo = MatchArena.configuration(suite.world, MatchArena.Mode.DUEL, "myriad_match_tempo.json");
            Files.writeString(tempo, "{\"profile\":\"" + profile.name().toLowerCase(Locale.ROOT) + "\"}");
            suite.check(MatchArena.select(suite.server, "3v3"), "The synthetic three-tier map accepts " + profile);
            match = GameManager.get();
            suite.check(match.startQueuedMatch(suite.server, suite.players), "Six seats enter the fixture draft");
            var next = new EnumMap<Faction, Integer>(Faction.class);
            while (match.queueCurrentPicker() != null) {
                var player = suite.server.getPlayerList().getPlayer(match.queueCurrentPicker());
                Faction side = match.factionOf(player);
                CharacterType role = (side == Faction.GUARDIAN ? PUSHERS : OPPONENTS).get(next.merge(side, 1, Integer::sum) - 1);
                suite.check(match.onPick(player, role), "Synthetic draft chooses " + role);
                heroes.put(role, player);
            }
            suite.clock(match.queuePreparationDeadline());
            match.tick(suite.server, level.getGameTime());
            start = match.getMatchStartTime();
            suite.check(start >= 0 && GameManager.combatTempo() == profile, "Match freezes the selected profile at combat start");
            park();
            LaneSystem.tick(level);
            freshLane = LaneSystem.snapshot();
            suite.check(suite.units("TOWER", null).size() == 6 && suite.units("CRYSTAL", null).size() == 2,
                    "Actual active map spawns all three tower tiers per side and two crystals");
        }

        private void park() {
            for (var player : heroes.values()) player.setPos(HOLDING);
        }

        /** Restores a clean lane state with the selected number of preceding towers already cleared. */
        private void stage(long elapsed, Faction attacker, int clearedTiers, boolean spawnOrdinaryWave) throws Exception {
            park();
            long now = start + elapsed;
            suite.clock(now);
            JsonObject checkpoint = freshLane.deepCopy();
            var units = checkpoint.getAsJsonArray("units");
            var cleared = checkpoint.getAsJsonArray("destroyedTowers");
            Faction defending = enemy(attacker);
            for (int tier = 1; tier <= clearedTiers; tier++) {
                int current = tier;
                units.asList().removeIf(unit -> {
                    var row = unit.getAsJsonObject();
                    return row.get("kind").getAsString().equals("TOWER") && row.get("faction").getAsString().equals(defending.getName())
                            && row.get("towerTier").getAsInt() == current;
                });
                cleared.add(defending.getName() + "/mid/" + tier);
            }
            if (spawnOrdinaryWave) {
                int wave = 1 + (int) Math.max(0, (elapsed - profile.firstWaveTicks()) / LaneSystem.WAVE_INTERVAL);
                if (wave % LaneSystem.MAGIC_WAVE_PERIOD == 0) wave--;
                checkpoint.addProperty("wave", wave - 1);
                checkpoint.addProperty("nextWave", now);
            } else {
                checkpoint.addProperty("nextWave", now + 10000);
            }
            LaneSystem.restore(level, checkpoint);
            LaneSystem.tick(level);
        }

        private Mob tower(Faction side, int tier) throws Exception {
            var position = LaneSystem.towerPoint(side, LaneSystem.Lane.MID, tier);
            return suite.units("TOWER", side).stream().filter(t -> t.blockPosition().equals(position)).findFirst().orElseThrow();
        }

        private List<Mob> advanceWave(Faction attacker, Mob objective) throws Exception {
            var escorted = new ArrayList<Mob>();
            for (String kind : List.of("MELEE", "RANGED", "MAGIC", "SIEGE")) {
                var own = suite.units(kind, attacker).stream()
                        .filter(Mob::isAlive).sorted(java.util.Comparator.comparingDouble(Mob::getX)).toList();
                for (var opposing : suite.units(kind, enemy(attacker))) opposing.discard();
                for (int slot = 0; slot < own.size(); slot++) {
                    Mob minion = own.get(slot);
                    double distance = kind.equals("MELEE") || kind.equals("SIEGE") ? 1.3 : 5.5;
                    double lateral = (slot % 2 == 0 ? -.6 : .6) + 1.6;
                    minion.setPos(objective.position().add(forward(attacker) * -distance, 0, lateral));
                    minion.setOnGround(true);
                    minion.setDeltaMovement(Vec3.ZERO);
                    escorted.add(minion);
                }
            }
            return escorted;
        }

        private void towerRules() throws Exception {
            Faction attacker = Faction.GUARDIAN;
            var actor = heroes.get(CharacterType.RIVET);
            for (long elapsed : new long[]{0, 5999, 6000, 6001, 10200, 14400, 24000}) {
                for (int tier = 1; tier <= 3; tier++) {
                    stage(elapsed, attacker, tier - 1, false);
                    Mob target = tower(enemy(attacker), tier);
                    var hitter = heroes.get(CharacterType.SHUO);
                    OPENING.apply(hitter);
                    float hp = LaneSystem.health(target);
                    float unescorted = directHit(hitter, target, 1000);
                    stage(elapsed, attacker, tier - 1, true);
                    target = tower(enemy(attacker), tier);
                    List<Mob> wave = advanceWave(attacker, target);
                    suite.check(wave.size() == 4, "Four ordinary bodies escort the normalized hit");
                    float escorted = directHit(hitter, target, 1000);
                    expectNear(unescorted, 200 * MatchBalance.towerDamageTakenScale(elapsed), .02, "Unescorted tower damage");
                    expectNear(escorted, 1000 * MatchBalance.towerDamageTakenScale(elapsed), .02, "Escorted tower damage");
                    var incomingWaveShot = firstLaneShot(wave, elapsed);

                    stage(elapsed, Faction.DESTRUCTION, tier - 1, false);
                    target = tower(Faction.GUARDIAN, tier);
                    OPENING.apply(actor);
                    actor.setPos(target.position().add(0, 0, 7));
                    float maxHeroHealth = actor.getHealth();
                    var pulses = new JsonArray();
                    float before = maxHeroHealth;
                    for (int tick = 1; tick <= 59; tick++) {
                        suite.clock(start + elapsed + tick);
                        LaneSystem.tick(level);
                        float after = actor.getHealth();
                        if (after < before) {
                            var pulse = new JsonObject();
                            pulse.addProperty("tick", tick);
                            pulse.addProperty("elapsedTicks", elapsed + tick);
                            pulse.addProperty("damage", before - after);
                            pulses.add(pulse);
                        }
                        before = after;
                        actor.damageCooldownTime = Math.max(0, actor.damageCooldownTime - 1);
                    }
                    suite.check(pulses.size() == 2 && pulses.get(0).getAsJsonObject().get("tick").getAsInt() == 29
                            && pulses.get(1).getAsJsonObject().get("tick").getAsInt() == 58,
                            "Each tower fires after a 28-tick lock and can then reacquire the same hero");
                    var row = row("tower", elapsed);
                    row.addProperty("tier", tier);
                    row.addProperty("initialHp", hp);
                    row.addProperty("basePhysicalAttack", LaneSystem.towerAttack(tier));
                    row.addProperty("hitRawTrue", 1000);
                    row.addProperty("emptyHit", unescorted);
                    row.addProperty("escortedHit", escorted);
                    row.addProperty("hero", "rivet");
                    row.addProperty("heroLevel", OPENING.level());
                    row.addProperty("heroMaximumHealth", maxHeroHealth);
                    row.addProperty("heroPhysicalDefense", actor.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE));
                    row.add("outgoingHeroHits", pulses);
                    row.add("outgoingLaneHit", incomingWaveShot);
                    emit(row);
                }
            }
        }

        private JsonObject firstLaneShot(List<Mob> wave, long elapsed) {
            for (int tick = 1; tick <= 29; tick++) {
                suite.clock(start + elapsed + tick);
                var before = wave.stream().map(LaneSystem::health).toList();
                LaneSystem.tick(level);
                for (int index = 0; index < wave.size(); index++) {
                    float drop = before.get(index) - LaneSystem.health(wave.get(index));
                    if (drop > 1) {
                        var result = new JsonObject();
                        result.addProperty("tick", tick);
                        result.addProperty("kind", index < 2 ? "melee" : "ranged");
                        result.addProperty("effectiveDamage", drop);
                        return result;
                    }
                }
            }
            throw new AssertionError(profile + " tower did not deal outgoing damage to the ordinary wave");
        }

        private void pureBasics() throws Exception {
            for (var sample : List.of(Map.entry(EARLY, OPENING), Map.entry(12000L, MIDGAME), Map.entry(ENDGAME, LATE))) {
                Map<CharacterType, Double> rates = new EnumMap<>(CharacterType.class);
                for (var hero : heroes.entrySet()) {
                    var role = hero.getKey();
                    var actor = hero.getValue();
                    Faction side = match.factionOf(actor);
                    long elapsed = sample.getKey();
                    stage(elapsed, side, 0, false);
                    var target = tower(enemy(side), 1);
                    sample.getValue().apply(actor);
                    var auto = new BasicInputs(actor, role);
                    float heroDamage = 0;
                    float firstHit = 0;
                    for (int tick = 1; tick <= BASIC_WINDOW; tick++) {
                        suite.clock(start + elapsed + tick);
                        float before = LaneSystem.health(target);
                        auto.tick(target, tick, 1);
                        float delta = before - LaneSystem.health(target);
                        if (firstHit == 0 && delta > 0) firstHit = delta;
                        heroDamage += delta;
                    }
                    suite.check(firstHit > 0, "Live pure basic attacks hit the tower for " + role);
                    double dps = heroDamage * 20 / BASIC_WINDOW;
                    rates.put(role, dps);
                    var row = row("pure_basic", elapsed);
                    addBuild(row, sample.getValue());
                    row.addProperty("hero", role.getId());
                    row.addProperty("basicWindowTicks", BASIC_WINDOW);
                    row.addProperty("escorted", false);
                    row.addProperty("defenseFeedback", false);
                    row.addProperty("firstHit", firstHit);
                    row.addProperty("attackSpeed", actor.getAttributeValue(ModAttributes.ATTACK_SPEED));
                    row.addProperty("physicalAttack", actor.getAttributeValue(ModAttributes.PHYSICAL_ATTACK));
                    row.addProperty("spellPower", actor.getAttributeValue(ModAttributes.SPELL_POWER));
                    row.addProperty("heroDamage", heroDamage);
                    row.addProperty("effectiveDps", dps);
                    emit(row);
                }
                var ratesSorted = rates.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList();
                var row = row("pure_basic_spread", sample.getKey());
                addBuild(row, sample.getValue());
                row.addProperty("roles", rates.size());
                row.addProperty("maxRole", ratesSorted.getLast().getKey().getId());
                row.addProperty("minRole", ratesSorted.getFirst().getKey().getId());
                row.addProperty("maxToMin", ratesSorted.getLast().getValue() / ratesSorted.getFirst().getValue());
                row.addProperty("luoxiaToQingluo", rates.get(CharacterType.LUOXIA) / rates.get(CharacterType.QINGLUO));
                row.addProperty("referenceMaxToMin", 2);
                emit(row);
            }
        }

        private void wholeWaves() throws Exception {
            for (var sample : List.of(Map.entry(EARLY, OPENING), Map.entry(12000L, MIDGAME), Map.entry(ENDGAME, LATE))) {
                int tier = sample.getKey() == EARLY ? 1 : sample.getKey() == 12000 ? 2 : 3;
                var role = CharacterType.LUOXIA;
                var actor = heroes.get(role);
                stage(sample.getKey(), Faction.GUARDIAN, tier - 1, true);
                var tower = tower(Faction.DESTRUCTION, tier);
                var minions = advanceWave(Faction.GUARDIAN, tower);
                suite.check(minions.size() == 4, "The test feeds a single ordinary wave");
                sample.getValue().apply(actor);
                var totals = push(tower, minions, List.of(new BasicInputs(actor, role)), sample.getKey());
                suite.check(totals.hero > 0 && totals.minions > 0 && totals.ticks > 0, "Whole-wave source accounting has real damage from both sides");
                var row = row("whole_wave", sample.getKey());
                addBuild(row, sample.getValue());
                row.addProperty("hero", role.getId());
                row.addProperty("tier", tier);
                row.addProperty("waveEndTick", totals.ticks);
                row.addProperty("hpAtWaveEntry", totals.initialHealth);
                row.addProperty("heroDamage", totals.hero);
                row.addProperty("laneDamage", totals.minions);
                row.addProperty("totalDamage", totals.initialHealth - LaneSystem.health(tower));
                row.addProperty("percent", (totals.initialHealth - LaneSystem.health(tower)) * 100 / totals.initialHealth);
                row.addProperty("towerDestroyed", !tower.isAlive());
                row.addProperty("allEscortsGone", minions.stream().noneMatch(Mob::isAlive));
                if (tier == 1) row.addProperty("plannerReferencePercent", 15);
                emit(row);
            }
        }

        private void fountains() throws Exception {
            long requested = 5000;
            stage(requested, Faction.GUARDIAN, 0, false);
            var actor = heroes.get(CharacterType.LUOXIA);
            OPENING.apply(actor);
            var data = PlayerDataProvider.get(actor);
            var home = Vec3.atCenterOf(LaneSystem.fountainPoint(Faction.GUARDIAN));
            var hostile = Vec3.atCenterOf(LaneSystem.fountainPoint(Faction.DESTRUCTION));
            long when = (level.getGameTime() / 20 + 1) * 20;
            for (int probe = 0; probe < 5; probe++) {
                boolean own = probe != 4;
                double distance = switch (probe) { case 1 -> 6.01; case 2 -> 6.0; default -> 0.0; };
                boolean nearCap = probe == 3;
                actor.setPos((own ? home : hostile).add(0, 0, distance));
                float maxHp = data.getModMaxHealth();
                float maxMana = (float) actor.getAttributeValue(ModAttributes.MAX_MANA);
                float hpBefore = nearCap ? maxHp - 3 : maxHp / 2;
                float manaBefore = nearCap ? maxMana - 2 : 0;
                actor.setHealth(hpBefore);
                data.setMana(manaBefore);
                actor.damageCooldownTime = 0;
                suite.clock(when++);
                LaneSystem.tick(level);
                float hpDelta = actor.getHealth() - hpBefore;
                float mpDelta = data.getMana() - manaBefore;
                double expectedHp = own ? distance > 6 ? 0 : nearCap ? 3 : maxHp * .1
                        : -LaneSystem.fountainEnemyDamage(maxHp);
                double expectedMp = own && distance <= 6 ? nearCap ? 2 : maxMana * .1 : 0;
                expectNear(hpDelta, expectedHp, .02, "Fountain actual health pulse");
                expectNear(mpDelta, expectedMp, .02, "Fountain actual mana pulse");
                var row = row("fountain", level.getGameTime() - start);
                row.addProperty("owner", own ? "ally" : "enemy");
                row.addProperty("distance", distance);
                row.addProperty("hpBefore", hpBefore);
                row.addProperty("manaBefore", manaBefore);
                row.addProperty("hpDelta", hpDelta);
                row.addProperty("manaDelta", mpDelta);
                emit(row);
                when += 19;
            }
        }

        private void crystal() throws Exception {
            stage(ENDGAME, Faction.GUARDIAN, 3, false);
            var crystal = objective(4);
            var hitter = heroes.get(CharacterType.SHUO);
            float hp = LaneSystem.health(crystal);
            float unescorted = directHit(hitter, crystal, 1000);
            expectNear(unescorted, 200, 0, "An unlocked crystal receives the unescorted rule");
            stage(ENDGAME, Faction.GUARDIAN, 3, true);
            crystal = objective(4);
            advanceWave(Faction.GUARDIAN, crystal);
            float escorted = directHit(hitter, crystal, 1000);
            expectNear(escorted, 1000, 0, "An unlocked crystal has no tower time protection");
            stage(ENDGAME, Faction.GUARDIAN, 3, false);
            var actor = heroes.get(CharacterType.RIVET);
            OPENING.apply(actor);
            var ownCrystal = suite.units("CRYSTAL", Faction.GUARDIAN).getFirst();
            actor.setPos(ownCrystal.position().add(0, 0, -7));
            float before = actor.getHealth();
            for (int tick = 1; tick <= 29; tick++) {
                suite.clock(start + ENDGAME + tick);
                LaneSystem.tick(level);
            }
            suite.check(actor.getHealth() < before, "The crystal fires on the opposing hero");
            var row = row("crystal", ENDGAME);
            row.addProperty("initialHp", hp);
            row.addProperty("basePhysicalAttack", LaneSystem.CRYSTAL_ATTACK);
            row.addProperty("emptyHit", unescorted);
            row.addProperty("escortedHit", escorted);
            row.addProperty("outgoingHeroHit", before - actor.getHealth());
            row.addProperty("hero", "rivet");
            row.addProperty("shotByTick", 29);
            emit(row);
        }

        private Mob objective(int tier) throws Exception {
            return tier <= 3 ? tower(Faction.DESTRUCTION, tier) : suite.units("CRYSTAL", Faction.DESTRUCTION).getFirst();
        }

        private void progression() throws Exception {
            middleOuterTower();
            stage(ENDGAME, Faction.GUARDIAN, 0, true);
            var crystal = suite.units("CRYSTAL", Faction.DESTRUCTION).getFirst();
            var finisher = heroes.get(CharacterType.SHUO);
            for (var role : PUSHERS) LATE.apply(heroes.get(role));
            expectNear(directHit(finisher, tower(Faction.DESTRUCTION, 2), 1000), 0, 0, "Inner tower waits for preceding tower");
            expectNear(directHit(finisher, tower(Faction.DESTRUCTION, 3), 1000), 0, 0, "Base tower waits for preceding towers");
            expectNear(directHit(finisher, crystal, 1000), 0, 0, "Crystal waits for the last tower");

            long elapsed = ENDGAME;
            int tier = 1;
            for (int waveIndex = 1; waveIndex <= 10 && match.getPhase() == GameManager.Phase.PLAYING; waveIndex++) {
                if (waveIndex > 1) {
                    park();
                    var saved = LaneSystem.snapshot();
                    long nextWave = saved.get("nextWave").getAsLong();
                    saved.getAsJsonArray("units").asList().removeIf(unit -> {
                        String kind = unit.getAsJsonObject().get("kind").getAsString();
                        return !kind.equals("TOWER") && !kind.equals("CRYSTAL");
                    });
                    suite.clock(nextWave);
                    LaneSystem.restore(level, saved);
                    LaneSystem.tick(level);
                    elapsed = nextWave - start;
                }
                Mob target = objective(tier);
                List<Mob> minions = advanceWave(Faction.GUARDIAN, target);
                var inputs = PUSHERS.stream().filter(role -> heroes.get(role).isAlive())
                        .map(role -> new BasicInputs(heroes.get(role), role)).toList();
                int[] before = golds();
                var totals = push(target, minions, inputs, elapsed);
                int[] payouts = payouts(before, golds());
                var row = row("late_push_wave", elapsed);
                addBuild(row, LATE);
                row.addProperty("wave", waveIndex);
                row.addProperty("tier", tier);
                row.addProperty("heroesAtStart", inputs.size());
                row.addProperty("escortsAtStart", minions.size());
                row.addProperty("waveEndTick", totals.ticks);
                row.addProperty("endElapsedTicks", elapsed + totals.ticks);
                row.addProperty("hpAtWaveEntry", totals.initialHealth);
                row.addProperty("heroDamage", totals.hero);
                row.addProperty("laneDamage", totals.minions);
                row.addProperty("remainingHp", LaneSystem.health(target));
                row.addProperty("broken", !target.isAlive());
                row.add("goldByRecipient", rewards(payouts));
                emit(row);
                if (!target.isAlive()) {
                    verifyReward(tier, payouts);
                    tier++;
                    if (tier == 5) break;
                    if (tier < 4) expectNear(directHit(finisher, objective(4), 1000), 0, 0,
                            "Crystal remains closed while a tower stands");
                }
            }
            var terminal = row("late_push_result", level.getGameTime() - start);
            terminal.addProperty("elapsedSinceWaveEntry", level.getGameTime() - start - ENDGAME);
            terminal.addProperty("plannerReferenceSecondsMin", 25);
            terminal.addProperty("plannerReferenceSecondsMax", 35);
            terminal.addProperty("stagedAtWaveArrival", true);
            terminal.addProperty("remainingTier", tier < 5 ? tier : 0);
            terminal.addProperty("finished", match.getPhase() == GameManager.Phase.ENDED);
            if (match.getWinner() != null) terminal.addProperty("winner", match.getWinner().getName());
            emit(terminal);

            // Exercise the rest of the normal damage/death pipeline even when the timed wave script stalls.
            // The separate event reports forced damage explicitly and cannot supply a push duration.
            for (; tier <= 4 && match.getPhase() == GameManager.Phase.PLAYING; tier++) {
                Mob objective = objective(tier);
                int[] before = golds();
                float damage = directHit(finisher, objective, 1_000_000);
                suite.check(!objective.isAlive(), "Normal building death path advances after probe for tier " + tier);
                int[] payouts = payouts(before, golds());
                verifyReward(tier, payouts);
                if (tier < 3) expectNear(directHit(finisher, objective(4), 1000), 0, 0, "Crystal remains closed before the third tower");
                var row = row("forced_objective_path", level.getGameTime() - start);
                row.addProperty("tier", tier);
                row.addProperty("fixtureRawTrueDamage", 1_000_000);
                row.addProperty("effectiveDamage", damage);
                row.add("goldByRecipient", rewards(payouts));
                row.addProperty("phase", match.getPhase().name());
                if (match.getWinner() != null) row.addProperty("winner", match.getWinner().getName());
                emit(row);
            }
            suite.check(match.getPhase() == GameManager.Phase.ENDED && match.getWinner() == Faction.GUARDIAN,
                    "Only the destruction of the unlocked enemy crystal decides the winner");
        }

        private void middleOuterTower() throws Exception {
            long elapsed = 12000;
            stage(elapsed, Faction.GUARDIAN, 0, true);
            var target = objective(1);
            var minions = advanceWave(Faction.GUARDIAN, target);
            var inputs = PUSHERS.stream().map(role -> {
                var actor = heroes.get(role);
                MIDGAME.apply(actor);
                return new BasicInputs(actor, role);
            }).toList();
            var totals = push(target, minions, inputs, elapsed);
            var row = row("mid_team_outer", elapsed);
            addBuild(row, MIDGAME);
            row.addProperty("waveEndTick", totals.ticks);
            row.addProperty("hpAtWaveEntry", totals.initialHealth);
            row.addProperty("heroDamage", totals.hero);
            row.addProperty("laneDamage", totals.minions);
            row.addProperty("broken", !target.isAlive());
            row.addProperty("plannerReferenceSecondsMin", 12);
            row.addProperty("plannerReferenceSecondsMax", 18);
            emit(row);
        }

        private record Totals(float initialHealth, float hero, float minions, int ticks) {}

        private Totals push(Mob target, List<Mob> wave, List<BasicInputs> autos, long elapsed) {
            float initial = LaneSystem.health(target);
            float hero = 0;
            float minion = 0;
            int tick = 0;
            for (; tick < LaneSystem.WAVE_INTERVAL - 1 && target.isAlive() && wave.stream().anyMatch(Mob::isAlive);) {
                suite.clock(start + elapsed + ++tick);
                float before = LaneSystem.health(target);
                for (int index = 0; index < autos.size() && target.isAlive(); index++) {
                    autos.get(index).tick(target, tick, index + 1);
                }
                float afterHero = target.isAlive() ? LaneSystem.health(target) : 0;
                hero += before - afterHero;
                if (!target.isAlive()) break;
                LaneSystem.tick(level);
                minion += afterHero - (target.isAlive() ? LaneSystem.health(target) : 0);
                for (var p : heroes.values()) p.damageCooldownTime = Math.max(0, p.damageCooldownTime - 1);
                for (var unit : wave) {
                    unit.damageCooldownTime = Math.max(0, unit.damageCooldownTime - 1);
                    if (unit.isAlive()) unit.move(MoverType.SELF, unit.getDeltaMovement());
                }
            }
            expectNear(hero + minion, initial - (target.isAlive() ? LaneSystem.health(target) : 0), .03,
                    "Per-tick hero and lane contributions sum to effective HP loss");
            return new Totals(initial, hero, minion, tick);
        }

        private float directHit(ServerPlayer actor, Mob target, int amount) {
            float before = LaneSystem.health(target);
            target.damageCooldownTime = 0;
            DamageCalc.dealBasicDamage(actor, target, amount, DamageCalc.Kind.TRUE);
            return before - (target.isAlive() ? LaneSystem.health(target) : 0);
        }

        private int[] golds() {
            return Arrays.stream(CharacterType.values()).filter(heroes::containsKey).map(heroes::get)
                    .map(PlayerDataProvider::get).mapToInt(PlayerData::getGold).toArray();
        }

        private int[] payouts(int[] before, int[] after) {
            for (int index = 0; index < after.length; index++) after[index] -= before[index];
            return after;
        }

        private JsonObject rewards(int[] payouts) {
            var amounts = new JsonObject();
            var roles = Arrays.stream(CharacterType.values()).filter(heroes::containsKey).toList();
            for (int index = 0; index < payouts.length; index++) amounts.addProperty(roles.get(index).getId(), payouts[index]);
            return amounts;
        }

        private void verifyReward(int tier, int[] payouts) {
            var amounts = rewards(payouts);
            for (var role : heroes.keySet()) {
                int expected = tier < 4 && PUSHERS.contains(role) ? GlobalBalance.goldIncome(LaneSystem.towerBaseGold(tier)) : 0;
                suite.check(amounts.get(role.getId()).getAsInt() == expected, "Eligible team tower gold for tier " + tier + ": " + role);
            }
        }

        private void expectNear(double actual, double expected, double tolerance, String detail) {
            suite.check(Math.abs(actual - expected) <= tolerance,
                    profile + " " + detail + " expected " + expected + " got " + actual);
        }

        private JsonObject row(String test, long elapsed) {
            var entry = new JsonObject();
            entry.addProperty("profile", profile.name());
            entry.addProperty("scenario", test);
            entry.addProperty("elapsedTicks", elapsed);
            return entry;
        }
    }

    private static Faction enemy(Faction faction) {
        return faction == Faction.GUARDIAN ? Faction.DESTRUCTION : Faction.GUARDIAN;
    }

    private static double forward(Faction faction) {
        return faction == Faction.GUARDIAN ? 1 : -1;
    }

    private static void addBuild(JsonObject row, Build build) {
        row.addProperty("level", build.level());
        row.addProperty("strengthPoints", build.strength());
        row.addProperty("wisdomPoints", build.wisdom());
        row.addProperty("speedPoints", build.speed());
        long quotedCost = 0;
        for (int points : new int[]{build.strength(), build.wisdom(), build.speed()}) {
            for (int rank = 0; rank < points; rank++) quotedCost += FirstGroupGrowth.price(rank);
        }
        row.addProperty("quotedBuildCost", quotedCost);
        row.addProperty("heldGoldBeforeAwards", HELD_GOLD);
        row.addProperty("crit", 0);
    }

    private static void emit(JsonObject row) {
        Myriad.LOGGER.info("BATTLEFIELD_BUILDING {}", row);
    }

    private static final class BasicInputs {
        private final ServerPlayer actor;
        private final CharacterType role;
        private int next;
        private boolean drawing;

        BasicInputs(ServerPlayer actor, CharacterType role) {
            this.actor = actor;
            this.role = role;
            AbilityRuntime.clear(actor);
        }

        void tick(Mob target, int tick, int slot) {
            if (!actor.isAlive()) return;
            boolean melee = VanillaMeleeAttack.usesVanilla(role);
            double facing = forward(GameManager.get().factionOf(actor));
            double distance = melee ? 1.8 : 5.5;
            actor.setPos(target.position().add(-facing * distance, 0, -slot * .55));
            actor.setOnGround(true);
            actor.lookAt(EntityAnchorArgument.Anchor.EYES, target.position().add(0, 1.45, 0));
            AbilityRuntime.tick(actor);
            MagicBullet.tickBullets(actor, PlayerDataProvider.get(actor));
            if (tick < next || !target.isAlive()) return;
            if (role == CharacterType.LUOXIA) {
                BasicAttackSystem.tryAttack(actor, drawing ? LuoxiaBow.RELEASE : LuoxiaBow.PRESS);
                if (drawing) {
                    drawing = false;
                    next = tick + 1;
                } else if (LuoxiaBow.drawing(actor)) {
                    drawing = true;
                    next = tick + (int) Math.ceil(LuoxiaBow.NORMAL_WORK /
                            LuoxiaBow.drawRate(actor.getAttributeValue(ModAttributes.ATTACK_SPEED), 1));
                }
            } else if (MagicBullet.isMage(actor)) {
                MagicBullet.tryFire(actor, actor.getMainHandItem());
                next = tick + BasicAttackSystem.cooldownTotal(actor);
            } else if (melee) {
                DamageGrowthChecks.swing(actor, target);
                next = tick + BasicAttackSystem.cooldownTotal(actor);
            }
        }
    }
}
