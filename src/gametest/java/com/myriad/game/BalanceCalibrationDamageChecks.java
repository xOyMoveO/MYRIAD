package com.myriad.game;

import com.google.gson.Gson;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.PlayerHealthSystem;
import com.myriad.registry.ModAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Six locked players on the existing synthetic arena. Only the match clock, prior economy and starting positions are staged. */
final class BalanceCalibrationDamageChecks {
    private static final String TOWER = "luoxia.tower_wave";
    private static final String NIGHT = "yingxiao.backstab_21a34_finisher";
    private static final int TOWER_TIME = 3600;
    private static final int NIGHT_TIME = 24000;
    private static final long SEED = 689L;
    private static final Gson JSON = new Gson();
    private static final Vec3 HOLDING = new Vec3(336.5, 64, 115.5);
    private static final List<CharacterType> BLUE = List.of(CharacterType.LUOXIA, CharacterType.SHUO, CharacterType.QINGLUO);
    private static final List<CharacterType> RED = List.of(CharacterType.YINGXIAO, CharacterType.RIVET, CharacterType.KEHENG);
    private static final List<Integer> BLUE_NODES = List.of(1, 2, 3, 4, 6);
    private static final List<Integer> RED_NODES = List.of(0, 2, 3, 4, 6);
    private static final List<Integer> SAME_POINTS = List.of(3, 2, 2, 2, 2);
    private static final List<Integer> ARCHIVED_RANKS = List.of(1, 5, 5, 7, 3);
    private static final List<Integer> ACTIVE_RANKS = List.of(1, 3, 1, 4, 2);
    private static final List<Integer> TOWER_RANKS = List.of(1, 0, 0, 0, 0);
    private static final Build TOWER_LEDGER = new Build(2, 135, 1105, List.of(1, 0, 1, 1, 0), TOWER_RANKS);
    private static final Build TOWER_ARCHIVED = new Build(1, 0, 500, List.of(0, 0, 0, 0, 0), TOWER_RANKS);
    private static final Build NIGHT_LEDGER = new Build(10, 482, 4770, SAME_POINTS, ACTIVE_RANKS);
    private static final Build DEFENDER_LEDGER = new Build(10, 279, 4770, SAME_POINTS, ACTIVE_RANKS);
    private static final Build NIGHT_ARCHIVED = new Build(20, 0, 4885, List.of(5, 0, 0, 3, 2), ARCHIVED_RANKS);
    private static final Build DEFENDER_ARCHIVED = new Build(20, 0, 4885, List.of(2, 2, 2, 2, 2), ARCHIVED_RANKS);
    private static DamageObserver activeObserver;
    private static boolean watching;

    private record Build(int level, int xp, int gold, List<Integer> points, List<Integer> ranks) { }

    private final GameTestHelper helper;
    private final ArenaFixtureChecks.Suite suite;
    private final ServerLevel level;
    private final Map<CharacterType, ServerPlayer> heroes = new EnumMap<>(CharacterType.class);
    private final String caseId = System.getenv().getOrDefault("MYRIAD_CALIBRATION_CASE", "baseline");
    private Path tempo;
    private long start;

    private BalanceCalibrationDamageChecks(GameTestHelper helper) {
        this.helper = helper;
        this.level = helper.getLevel();
        this.suite = new ArenaFixtureChecks.Suite(level.getServer());
    }

    static void run(GameTestHelper helper) {
        var trial = new BalanceCalibrationDamageChecks(helper);
        try {
            trial.setup();
            new TowerRun(trial, false).start();
        } catch (Exception | Error error) {
            trial.cleanup();
            throw new IllegalStateException(error);
        }
    }

    private void setup() throws Exception {
        suite.install();
        tempo = MatchArena.configuration(suite.world, MatchArena.Mode.DUEL, "myriad_match_tempo.json");
        Files.writeString(tempo, "{\"profile\":\"cherry_v2\"}");
        suite.command("myriad mod on");
        helper.assertTrue(MatchArena.select(suite.server, "3v3"), "Select the synthetic 3v3 arena");
        for (int seat = 0; seat < 6; seat++) suite.player(seat);
        draft();
        if (!watching) {
            ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
                if (activeObserver != null && activeObserver.watchedEntity == target) activeObserver.before(source.getEntity());
                return true;
            });
            watching = true;
        }
    }

    private void draft() {
        heroes.clear();
        var match = GameManager.get();
        helper.assertTrue(match.startQueuedMatch(suite.server, suite.players), "Six locked seats enter draft");
        var positions = new EnumMap<Faction, Integer>(Faction.class);
        while (match.queueCurrentPicker() != null) {
            var player = suite.server.getPlayerList().getPlayer(match.queueCurrentPicker());
            Faction side = match.factionOf(player);
            CharacterType role = (side == Faction.GUARDIAN ? BLUE : RED)
                    .get(positions.merge(side, 1, Integer::sum) - 1);
            helper.assertTrue(match.onPick(player, role), "Draft selects " + role.getId());
            heroes.put(role, player);
        }
        suite.clock(match.queuePreparationDeadline());
        match.tick(suite.server, level.getGameTime());
        start = match.getMatchStartTime();
        helper.assertTrue(match.getPhase() == GameManager.Phase.PLAYING && GameManager.combatTempo() == MatchTempo.CHERRY_V2
                && heroes.size() == 6 && !TrainingMode.isEnabled(), "CHERRY_V2 3v3 is playing with training disabled");
        park();
        LaneSystem.tick(level);
    }

    private void park() {
        int index = 0;
        for (var player : heroes.values()) place(player, HOLDING.add(index++ * 2, 0, 0));
    }

    private void cleanup() {
        activeObserver = null;
        for (var player : heroes.values()) {
            AbilityRuntime.clear(player);
            LuoxiaBow.clear(player);
        }
        GameManager.get().resetToLobby(suite.server, false);
        suite.removePlayers();
        if (tempo != null) {
            try {
                Files.deleteIfExists(tempo);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Remove the isolated arena tempo fixture", error);
            }
        }
    }

    private void next(Runnable step) {
        helper.runAfterDelay(1, () -> {
            try {
                step.run();
            } catch (RuntimeException | Error error) {
                cleanup();
                throw error;
            }
        });
    }

    private void build(ServerPlayer actor, Build build, List<Integer> nodes) {
        var data = PlayerDataProvider.get(actor);
        var id = data.getCharacterId();
        int xp = build.xp;
        for (int from = data.getLevel(); from < build.level; from++) xp += LevelSystem.xpToNext(from);
        LevelSystem.addXp(actor, data, Math.max(0, xp - data.getXp()));
        helper.assertTrue(data.getLevel() == build.level && data.getXp() == build.xp, "Real level progression to fixture level for " + id);
        int alreadySpent = 0;
        for (String attribute : FirstGroupGrowth.ATTRIBUTES) {
            for (int rank = 0; rank < data.firstGroupAttribute(attribute); rank++) alreadySpent += FirstGroupGrowth.price(rank);
        }
        data.setGold(build.gold - alreadySpent);
        data.resetMatchGoldEarned(build.gold);
        for (int index = 0; index < FirstGroupGrowth.ATTRIBUTES.size(); index++) {
            String attribute = FirstGroupGrowth.ATTRIBUTES.get(index);
            for (int rank = data.firstGroupAttribute(attribute); rank < build.points.get(index); rank++)
                helper.assertTrue(FirstGroupGrowth.buy(actor, data, attribute), "Fixture buys " + id + " " + attribute);
        }
        for (int index = 0; index < nodes.size(); index++) {
            var skill = FirstGroupSkills.node(id, nodes.get(index));
            int ranks = build.ranks.get(index);
            int desiredRank = skill.isPassive() ? FirstGroupSkills.passiveRank(build.level) : ranks;
            int purchases = skill.isPassive() ? (data.skillLevel(skill.getId()) == 0 ? 1 : 0)
                    : Math.max(0, ranks - data.skillLevel(skill.getId()));
            for (int n = 0; n < purchases; n++) PlayerDataProvider.upgradeSkill(actor, skill.getId());
            helper.assertTrue(data.skillLevel(skill.getId()) == desiredRank,
                    "Fixture selects the legal rank of " + skill.getId());
        }
        actor.getInventory().setSelectedSlot(0);
        actor.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(actor.getAttributeValue(ModAttributes.ATTACK_SPEED));
        PlayerHealthSystem.fill(actor);
        data.setMana((float) actor.getAttributeValue(ModAttributes.MAX_MANA));
    }

    private Map<String, Object> stats(ServerPlayer actor) {
        var data = PlayerDataProvider.get(actor);
        var stats = new LinkedHashMap<String, Object>();
        stats.put("role", data.getCharacterId());
        stats.put("faction", GameManager.get().factionOf(actor).getName());
        stats.put("level", data.getLevel());
        stats.put("xp", data.getXp());
        stats.put("goldEarned", data.getMatchGoldEarned());
        stats.put("wallet", data.getGold());
        stats.put("points", FirstGroupGrowth.ATTRIBUTES.stream().map(data::firstGroupAttribute).toList());
        stats.put("skills", data.getSkillLevels());
        stats.put("health", actor.getHealth());
        stats.put("mana", data.getMana());
        stats.put("physicalAttack", actor.getAttributeValue(ModAttributes.PHYSICAL_ATTACK));
        stats.put("physicalDefense", actor.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE));
        stats.put("magicResistance", actor.getAttributeValue(ModAttributes.MAGIC_RESISTANCE));
        stats.put("attackSpeed", actor.getAttributeValue(ModAttributes.ATTACK_SPEED));
        stats.put("critChance", actor.getAttributeValue(ModAttributes.CRIT_CHANCE));
        return stats;
    }

    private static void place(LivingEntity entity, Vec3 at) {
        entity.setPos(at);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(true);
    }

    private static void aim(ServerPlayer actor, LivingEntity target) {
        actor.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
    }

    private static void tickMock(ServerPlayer player) {
        // Fixture connections are intentionally absent from Minecraft's native network tick list.
        player.doTick();
        PlayerDataProvider.onPlayerTick(player);
        if (AbilityRuntime.moving(player)) player.move(MoverType.SELF, player.getDeltaMovement());
    }

    private void trace(String scenario, boolean archived, String event, long tick, Object... fields) {
        var row = new LinkedHashMap<String, Object>();
        row.put("scenarioId", scenario);
        row.put("scenarioVersion", 1);
        row.put("caseId", archived ? caseId + "_archived_inputs" : caseId);
        row.put("event", event);
        row.put("tick", tick);
        row.put("timeUnit", "real_tick");
        for (int i = 0; i < fields.length; i += 2) row.put((String) fields[i], fields[i + 1]);
        System.out.println("BALANCE_CALIBRATION " + JSON.toJson(row));
    }

    /** Snapshots the mod HP pool at the Fabric damage boundary and at the next hit or end of step. */
    private abstract static class DamageObserver {
        final LivingEntity watchedEntity;
        private Entity source;
        private long tick;
        private float health;
        private boolean pending;

        DamageObserver(LivingEntity target) {
            this.watchedEntity = target;
        }

        abstract float health();
        abstract void lost(long tick, Entity source, float before, float after);

        void before(Entity attacker) {
            flush();
            pending = true;
            source = attacker;
            tick = watchedEntity.level().getGameTime();
            health = health();
        }

        void flush() {
            if (!pending) return;
            pending = false;
            float after = health();
            if (after < health) lost(tick, source, health, after);
        }
    }

    private static final class TowerRun {
        private final BalanceCalibrationDamageChecks test;
        private final ServerPlayer luoxia;
        private final boolean archived;
        private final List<Mob> wave = new ArrayList<>();
        private Mob tower;
        private DamageObserver observer;
        private long began, nextRelease;
        private float heroDamage, laneDamage, initial;
        private int arrows, laneHits;

        TowerRun(BalanceCalibrationDamageChecks test, boolean archived) {
            this.test = test;
            this.archived = archived;
            luoxia = test.heroes.get(CharacterType.LUOXIA);
        }

        void start() throws Exception {
            var suite = test.suite;
            began = test.start + TOWER_TIME;
            suite.clock(began);
            test.build(luoxia, archived ? TOWER_ARCHIVED : TOWER_LEDGER, BLUE_NODES);
            var checkpoint = LaneSystem.snapshot();
            checkpoint.addProperty("nextWave", began);
            checkpoint.addProperty("wave", 3); // Ordinary wave 4 at its staged arrival.
            LaneSystem.restore(test.level, checkpoint);
            LaneSystem.tick(test.level);
            var towerPoint = LaneSystem.towerPoint(Faction.DESTRUCTION, LaneSystem.Lane.MID, 1);
            tower = suite.units("TOWER", Faction.DESTRUCTION).stream()
                    .filter(t -> t.blockPosition().equals(towerPoint)).findFirst().orElseThrow();
            for (var kind : List.of("MELEE", "RANGED")) {
                for (var unit : suite.units(kind, Faction.DESTRUCTION)) unit.discard();
                var own = suite.units(kind, Faction.GUARDIAN).stream().sorted(Comparator.comparingDouble(Mob::getX)).toList();
                test.helper.assertTrue(own.size() == 2, "Two ordinary soldiers of kind " + kind);
                for (int i = 0; i < own.size(); i++) {
                    double gap = kind.equals("MELEE") ? 1.3 : 5.5;
                    place(own.get(i), tower.position().add(-gap, 0, i == 0 ? 1 : 2.2));
                    wave.add(own.get(i));
                }
            }
            place(luoxia, tower.position().add(-5.5, 0, -.55));
            aim(luoxia, tower);
            initial = LaneSystem.health(tower);
            long defenders = test.heroes.values().stream().filter(p -> GameManager.get().factionOf(p) == Faction.DESTRUCTION
                    && p.distanceTo(tower) <= LaneSystem.TOWER_ATTACK_RANGE).count();
            test.helper.assertTrue(initial == LaneSystem.towerHealth(1)
                    && suite.units("MAGIC", null).isEmpty() && suite.units("SIEGE", null).isEmpty()
                    && defenders == 0, "Unattended full tier-one tower and exactly one ordinary wave");
            var escorts = wave.stream().map(mob -> Map.of("position", mob.position(), "hp", LaneSystem.health(mob),
                    "kind", wave.indexOf(mob) < 2 ? "melee" : "ranged")).toList();
            test.trace(TOWER, archived, "case_start", 0, "profile", GameManager.combatTempo().name(), "matchTick", TOWER_TIME,
                    "economy", archived ? "109_opening_build" : "v2_s0_3m_core",
                    "towerPosition", tower.position(), "heroPosition", luoxia.position(), "towerHp", initial,
                    "waveNumber", 4, "wave", escorts, "defendersInRange", defenders,
                    "towerDamageTakenScale", MatchBalance.towerDamageTakenScale(TOWER_TIME), "hero", test.stats(luoxia));
            observer = new DamageObserver(tower) {
                @Override float health() { return tower.isAlive() ? LaneSystem.health(tower) : 0; }
                @Override void lost(long tick, Entity source, float before, float after) {
                    String origin;
                    if (source == luoxia) {
                        heroDamage += before - after;
                        origin = "hero";
                    } else {
                        int slot = wave.indexOf(source);
                        test.helper.assertTrue(slot >= 0, "Every tower HP loss comes from the hero or this ordinary wave");
                        laneDamage += before - after;
                        laneHits++;
                        origin = slot < 2 ? "melee_minion" : "ranged_minion";
                    }
                    test.trace(TOWER, archived, "tower_hit", tick - began, "origin", origin, "damage", before - after,
                            "hpBefore", before, "hpAfter", after);
                }
            };
            activeObserver = observer;
            test.next(this::step);
        }

        private void step() {
            observer.flush();
            long time = test.level.getGameTime();
            long tick = time - began;
            int alive = (int) wave.stream().filter(Mob::isAlive).count();
            if (alive == 0 || !tower.isAlive()) {
                float remaining = observer.health();
                float total = initial - remaining;
                float percent = total * 100 / initial;
                test.helper.assertTrue(Math.abs(heroDamage + laneDamage - total) < .05,
                        "The sum of each source's tower damage reconciles to the tower's HP pool");
                test.trace(TOWER, archived, "case_result", tick, "heroDamage", heroDamage, "laneDamage", laneDamage,
                        "totalDamage", total, "initialHp", initial, "remainingHp", remaining, "percent", percent,
                        "targetPercent", 15, "differencePercentagePoints", percent - 15, "arrowsReleased", arrows,
                        "laneHits", laneHits, "escortsRemaining", alive, "towerDestroyed", !tower.isAlive());
                activeObserver = null;
                LuoxiaBow.clear(luoxia);
                AbilityRuntime.clear(luoxia);
                new NightRun(test, archived).stage();
                return;
            }
            test.helper.assertTrue(tick < LaneSystem.WAVE_INTERVAL - 1 && luoxia.isAlive() && !luoxia.isSpectator(),
                    "One ordinary wave resolves before another wave reaches the target, with the shooter still present");
            aim(luoxia, tower);
            tickMock(luoxia);
            if (!LuoxiaBow.drawing(luoxia)) {
                LuoxiaBow.input(luoxia, LuoxiaBow.PRESS);
                if (LuoxiaBow.drawing(luoxia)) {
                    nextRelease = time + (int) Math.ceil(LuoxiaBow.NORMAL_WORK
                            / LuoxiaBow.drawRate(luoxia.getAttributeValue(ModAttributes.ATTACK_SPEED), 1));
                }
            } else if (time >= nextRelease) {
                LuoxiaBow.input(luoxia, LuoxiaBow.RELEASE);
                arrows++;
            }
            observer.flush();
            test.next(this::step);
        }
    }

    private static final class NightRun {
        private final BalanceCalibrationDamageChecks test;
        private final ServerPlayer attacker, target;
        private final boolean archived;
        private final List<Float> after = new ArrayList<>();
        private DamageObserver observer;
        private long began, lastActionAt, lastSkillAt, lastHitAt;
        private int action;
        private String pendingAction;

        NightRun(BalanceCalibrationDamageChecks test, boolean archived) {
            this.test = test;
            this.archived = archived;
            attacker = test.heroes.get(CharacterType.YINGXIAO);
            target = test.heroes.get(CharacterType.LUOXIA);
        }

        void stage() {
            test.park();
            began = test.start + NIGHT_TIME;
            test.suite.clock(began - 40);
            test.build(attacker, archived ? NIGHT_ARCHIVED : NIGHT_LEDGER, RED_NODES);
            test.build(target, archived ? DEFENDER_ARCHIVED : DEFENDER_LEDGER, BLUE_NODES);
            var point = new BlockPos(332, 64, 105);
            CombatChunks.prepare(test.level, point);
            place(target, Vec3.atBottomCenterOf(point));
            place(attacker, target.position().add(0, 0, -2));
            target.setYRot(0);
            target.setXRot(0);
            aim(attacker, target);
            test.next(this::prepare);
        }

        private void prepare() {
            tickMock(target);
            tickMock(attacker);
            if (test.level.getGameTime() < began) {
                test.next(this::prepare);
                return;
            }
            test.helper.assertTrue(rearDot() < -.9 && attacker.hasLineOfSight(target)
                    && target.getHealth() == target.getMaxHealth() && attacker.getAttackStrengthScale(.5f) >= 1
                    && PlayerDataProvider.get(attacker).getMatchGoldEarned() == PlayerDataProvider.get(target).getMatchGoldEarned(),
                    "Recovered attacker faces a full-HP victim's rear at an equal budget");
            test.trace(NIGHT, archived, "case_start", 0, "profile", GameManager.combatTempo().name(), "matchTick", NIGHT_TIME,
                    "input", "21A34AA", "economy", archived ? "historical_4885_fixture_current_purchase_rules" : "v2_s0_20m_same_affordable_purchase",
                    "stagedBudget", archived ? 4885 : 4770, "attacker", test.stats(attacker),
                    "target", test.stats(target), "rearDot", rearDot(), "distance", attacker.distanceTo(target),
                    "basicSeed", SEED, "attackerLedgerAvailableGold", archived ? 4885 : 5512);
            observer = new DamageObserver(target) {
                @Override float health() { return HeroRespawnSystem.isWaiting(target) ? 0 : target.getHealth(); }
                @Override void lost(long tick, Entity source, float before, float afterHit) {
                    test.helper.assertTrue(source == attacker && pendingAction != null, "Damage is attributed to the outstanding combo action");
                    after.add(afterHit);
                    test.trace(NIGHT, archived, "health_lost", tick - began, "action", pendingAction, "hpBefore", before, "hpAfter", afterHit,
                            "damage", before - afterHit, "rearDotObserved", rearDot(), "targetRespawning", HeroRespawnSystem.isWaiting(target));
                    pendingAction = null;
                    lastHitAt = tick;
                }
            };
            activeObserver = observer;
            cast("2", CharacterType.SLOT_W);
            test.helper.assertTrue(attacker.hasEffect(MobEffects.INVISIBILITY), "The actual W applies invisibility before the rear Q");
            pendingAction = null;
            action = 1;
            test.next(this::step);
        }

        private void cast(String input, int slot) {
            var data = PlayerDataProvider.get(attacker);
            var skill = FirstGroupSkills.resolve(CharacterType.YINGXIAO, slot, data.getSkillLevels());
            float before = data.getMana();
            double rearAtInput = rearDot();
            aim(attacker, target);
            pendingAction = input;
            lastActionAt = test.level.getGameTime();
            lastSkillAt = lastActionAt;
            SkillSystem.cast(attacker, slot);
            boolean accepted = data.getCooldown(skill.getId()) > 0 || skill.getId().equals(AbilityRuntime.preparingSkill(attacker));
            test.trace(NIGHT, archived, "cast_requested", lastActionAt - began, "action", input, "skill", skill.getId(),
                    "accepted", accepted, "rearDot", rearAtInput, "manaBefore", before, "manaAfter", data.getMana(),
                    "distanceAfter", attacker.distanceTo(target));
            test.helper.assertTrue(accepted, "Production skill request accepts combo action " + input);
        }

        private void basic(String input) {
            aim(attacker, target);
            test.helper.assertTrue(attacker.isWithinEntityInteractionRange(target, 0) && attacker.hasLineOfSight(target),
                    "Natural melee position and vision for " + input);
            pendingAction = input;
            lastActionAt = test.level.getGameTime();
            attacker.getRandom().setSeed(SEED + after.size());
            test.trace(NIGHT, archived, "basic_requested", lastActionAt - began, "action", input, "rearDot", rearDot(),
                    "distance", attacker.distanceTo(target), "recovery", attacker.getAttackStrengthScale(.5f));
            attacker.attack(target);
            observer.flush();
        }

        private double rearDot() {
            return attacker.position().subtract(target.position()).multiply(1, 0, 1).normalize()
                    .dot(target.getLookAngle().multiply(1, 0, 1).normalize());
        }

        private void step() {
            tickMock(target);
            tickMock(attacker);
            observer.flush();
            long now = test.level.getGameTime();
            long tick = now - began;
            boolean waiting = HeroRespawnSystem.isWaiting(target);
            if (action == 7 && pendingAction == null || waiting) {
                Float opener = after.size() > 3 ? after.get(3) : null;
                Float first = after.size() > 4 ? after.get(4) : null;
                Float second = after.size() > 5 ? after.get(5) : null;
                test.trace(NIGHT, archived, "case_result", tick, "afterOpener", opener, "afterFirstBasic", first,
                        "afterSecondBasic", second, "secondBasicKills", first != null && first > 0 && second != null && second == 0,
                        "openerCompleted", opener != null, "victimRespawning", waiting, "realHealthAfter", target.getHealth());
                activeObserver = null;
                if (archived) {
                    test.cleanup();
                    test.helper.succeed();
                } else {
                    GameManager.get().resetToLobby(test.suite.server, false);
                    test.draft();
                    try {
                        new TowerRun(test, true).start();
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                }
                return;
            }
            test.helper.assertTrue(tick < 180, "The real combat sequence completes within nine seconds; action=" + action
                    + ", outstanding=" + pendingAction + ", position=" + attacker.position() + ", target=" + target.position());
            if (pendingAction == null && now > lastActionAt && !AbilityRuntime.moving(attacker)) {
                switch (action) {
                    case 1 -> { if (tick >= 3) { cast("1", CharacterType.SLOT_Q); action++; } }
                    case 2, 5, 6 -> {
                        if (now > lastHitAt && attacker.getAttackStrengthScale(.5f) >= 1 && !AbilityRuntime.channeling(attacker)) {
                            basic(action == 2 ? "A" : action == 5 ? "A1" : "A2");
                            action++;
                        }
                    }
                    case 3, 4 -> {
                        int slot = action == 3 ? CharacterType.SLOT_E : CharacterType.SLOT_R;
                        var skill = (com.myriad.character.ability.AbilitySkill) FirstGroupSkills.resolve(
                                CharacterType.YINGXIAO, slot, PlayerDataProvider.get(attacker).getSkillLevels());
                        if (now - lastSkillAt >= skill.inputRecoveryTicks()) {
                            cast(Integer.toString(action), slot);
                            action++;
                        }
                    }
                    default -> { }
                }
            }
            observer.flush();
            test.next(this::step);
        }
    }
}
