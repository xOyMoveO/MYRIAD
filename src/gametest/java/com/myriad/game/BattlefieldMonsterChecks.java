package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.MagicBullet;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Grounded, full-strength input against production monsters in an isolated flat fixture. */
final class BattlefieldMonsterChecks {
    private static final int WARMUP = 45;
    private static final int MAX_FIGHT = 900;
    private static final CharacterType[] ROLES = {CharacterType.CHIXIAO, CharacterType.TIANXING, CharacterType.SHUO};
    private static final String CLEAVE = FirstGroupSkills.node("chixiao", 2).getId();

    private BattlefieldMonsterChecks() {}

    static void camps(GameTestHelper helper) {
        runFights(helper, List.of(
                new Encounter("low_hare", MonsterType.CHERRY_FROST_HARE, 1, 1, 1, 400),
                new Encounter("low_fox", MonsterType.CHERRY_DUSK_FOX, 1, 1, 1, 400),
                new Encounter("low_scout", MonsterType.CHERRY_SCOUT_BOT, 1, 1, 1, 400),
                new Encounter("low_jade", MonsterType.CHERRY_JADE_BEAST, 1, 1, 1, 800),
                new Encounter("low_walker", MonsterType.CHERRY_HEAVY_WALKER, 1, 1, 1, 800)));
    }

    static void crab(GameTestHelper helper) {
        runFights(helper, List.of(new Encounter("crab_solo", MonsterType.CHERRY_RIFT_CRAB, 4, 2, 1, 4800)));
    }

    static void crabDuo(GameTestHelper helper) {
        runFights(helper, List.of(new Encounter("crab_duo", MonsterType.CHERRY_RIFT_CRAB, 4, 2, 2, 4800)));
    }

    static void crabTrio(GameTestHelper helper) {
        runFights(helper, List.of(new Encounter("crab_trio", MonsterType.CHERRY_RIFT_CRAB, 4, 2, 3, 4800)));
    }

    private record Encounter(String id, MonsterType type, int level, int strength, int party, long firstSpawn) {}
    private record Hit(long tick, String source, String target, String action, float healthLost) {}
    private record Reward(String role, int gold, int experience, long obsidianBuffTicks) {}

    private static void runFights(GameTestHelper helper, List<Encounter> encounters) {
        var fixture = new Fixture(helper);
        try {
            var combats = new ArrayList<Combat>();
            var points = new LinkedHashMap<String, MonsterManager.SpawnPoint>();
            for (var encounter : encounters) {
                var center = fixture.floor(combats.size());
                points.put(encounter.id(), new MonsterManager.SpawnPoint(encounter.id(), center, encounter.type()));
                combats.add(new Combat(fixture, encounter, center));
            }
            MonsterManager.activateSpawns(points);
            fixture.begin(encounters.getFirst().firstSpawn() - WARMUP);
            for (var combat : combats) combat.learnOpeningSkill();
            fixture.listen(() -> {
                long elapsed = fixture.elapsed();
                if (elapsed == encounters.getFirst().firstSpawn() - 20) fixture.roundTrip();
                for (var combat : combats) combat.tick(elapsed);
                if (combats.stream().anyMatch(combat -> combat.finished == null)) return false;
                fixture.verifyRespawns(combats);
                return true;
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static final class Combat {
        private final Fixture fixture;
        private final Encounter encounter;
        private final BlockPos center;
        private final List<ServerPlayer> fighters = new ArrayList<>();
        private final List<Vec3> stands = new ArrayList<>();
        private final List<Hit> hits = new ArrayList<>();
        private final float[] priorHealth;
        private final float[] startingHealth;
        private final float[] grossHealthLoss;
        private final long[] lastInput;
        private Mob target;
        private long spawnedAt;
        private long respawnAt = -1;
        private String finished;

        Combat(Fixture fixture, Encounter encounter, BlockPos center) {
            this.fixture = fixture;
            this.encounter = encounter;
            this.center = center;
            priorHealth = new float[encounter.party()];
            startingHealth = new float[encounter.party()];
            grossHealthLoss = new float[encounter.party()];
            lastInput = new long[encounter.party()];
            var base = Vec3.atBottomCenterOf(center);
            double radius = encounter.type().cherryModel().width() / 2.0 + .60;
            for (int i = 0; i < encounter.party(); i++) {
                Vec3 stand = base.add(switch (i) {
                    case 0 -> new Vec3(0, 0, -radius);
                    case 1 -> new Vec3(radius, 0, 0);
                    default -> new Vec3(-radius, 0, 0);
                });
                var player = fixture.player(ROLES[i], encounter.level(), encounter.strength(), Faction.GUARDIAN, stand);
                fighters.add(player);
                stands.add(stand);
                startingHealth[i] = player.getHealth();
                priorHealth[i] = startingHealth[i];
                lastInput[i] = -20;
            }
        }

        void learnOpeningSkill() {
            var hero = fighters.getFirst();
            var data = PlayerDataProvider.get(hero);
            data.setSkillPoints(encounter.level());
            PlayerDataProvider.upgradeSkill(hero, CLEAVE);
            fixture.helper.assertTrue(data.skillLevel(CLEAVE) == 1, "Rank-one Q is learned through the live skill path");
        }

        void tick(long elapsed) {
            if (finished != null) return;
            if (target == null) {
                var spawned = fixture.mob(encounter.type(), center);
                if (elapsed < encounter.firstSpawn()) {
                    fixture.helper.assertTrue(spawned == null, encounter.id() + " never spawns before its configured time");
                } else if (spawned != null) {
                    fixture.helper.assertTrue(elapsed <= encounter.firstSpawn() + 1, "First spawn is observed by the next tick");
                    target = spawned;
                    spawnedAt = elapsed;
                    fixture.helper.assertTrue(MonsterManager.modMaxHealth(target) > 0, "Spawn has managed health");
                    event("spawn", Map.of("scenario", encounter.id(), "type", encounter.type().getId(),
                            "elapsedTicks", elapsed, "generation", 0, "health", MonsterManager.modMaxHealth(target),
                            "rawAttack", target.getAttributeValue(Attributes.ATTACK_DAMAGE),
                            "physicalDefense", target.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE),
                            "magicDefense", target.getAttributeValue(ModAttributes.MAGIC_RESISTANCE)));
                } else if (elapsed > encounter.firstSpawn() + 1) {
                    fixture.helper.assertTrue(false, encounter.id() + " not spawned, snapshot=" + MonsterManager.snapshot()
                            + " phase=" + GameManager.get().getPhase() + " tempo=" + GameManager.combatTempo()
                            + " points=" + MonsterManager.spawnPointInfos());
                }
            }
            long fight = elapsed - spawnedAt;
            for (int i = 0; i < fighters.size(); i++) {
                var player = fighters.get(i);
                float drop = priorHealth[i] - player.getHealth();
                if (drop > .01F) {
                    grossHealthLoss[i] += drop;
                    hits.add(new Hit(fight, encounter.type().getId(), ROLES[i].getId(), "npc_attack", drop));
                }
                priorHealth[i] = player.getHealth();
                fixture.tickPlayer(player, stands.get(i));
                priorHealth[i] = player.getHealth();
            }
            if (target == null) return;
            if (fighters.stream().allMatch(BattlefieldMonsterChecks::defeated)) {
                finish(fight, "party_defeated", List.of());
                return;
            }
            if (fight >= MAX_FIGHT) {
                finish(fight, "time_limit", List.of());
                return;
            }
            for (int i = 0; i < fighters.size(); i++) {
                var player = fighters.get(i);
                if (defeated(player) || fight < i * 5L || fight - lastInput[i] < 11) continue;
                var data = PlayerDataProvider.get(player);
                String action;
                var before = rewards();
                float health = MonsterManager.modHealth(target);
                var facing = target.position().subtract(player.position());
                player.setYRot((float) Math.toDegrees(Math.atan2(-facing.x, facing.z)));
                player.setXRot(0);
                if (i == 0 && data.getCooldown(CLEAVE) == 0 && data.getMana() >= 70) {
                    SkillSystem.cast(player, CharacterType.SLOT_Q);
                    action = "rank_one_q";
                } else if (player.getAttackStrengthScale(.5F) >= 1) {
                    player.getRandom().setSeed(689L + i * 37L + fight);
                    player.attack(target);
                    action = "basic";
                } else continue;
                float lost = target.isAlive() ? health - MonsterManager.modHealth(target) : health;
                if (lost > 0.01F) hits.add(new Hit(fight, ROLES[i].getId(), encounter.type().getId(), action, lost));
                lastInput[i] = fight;
                if (!target.isAlive()) {
                    respawnAt = fixture.respawnAt(encounter.id());
                    var after = rewards();
                    var delta = new ArrayList<Reward>();
                    for (int j = 0; j < fighters.size(); j++) {
                        var old = before.get(j);
                        var earned = after.get(j);
                        delta.add(new Reward(earned.role(), earned.gold() - old.gold(),
                                earned.experience() - old.experience(), earned.obsidianBuffTicks()));
                        fixture.helper.assertTrue(j == i ? delta.getLast().gold() > 0 && delta.getLast().experience() > 0
                                : delta.getLast().gold() == 0 && delta.getLast().experience() == 0,
                                "Gold and XP settle exclusively on the final hit");
                    }
                    fixture.helper.assertTrue(respawnAt - fixture.level.getGameTime()
                            == GameManager.combatTempo().respawnSeconds(encounter.type()) * 20L,
                            "Death schedules the configured respawn delay");
                    finish(fight, "killed", delta);
                    return;
                }
            }
        }

        private List<Reward> rewards() {
            return fighters.stream().map(player -> {
                var data = PlayerDataProvider.get(player);
                int xp = data.getXp();
                for (int stage = 1; stage < data.getLevel(); stage++) xp += LevelSystem.xpToNext(stage);
                return new Reward(data.getCharacterId(), data.getGold(), xp,
                        Math.max(0, data.getObsidianTortoiseUntil() - fixture.level.getGameTime()));
            }).toList();
        }

        private void finish(long fight, String outcome, List<Reward> rewards) {
            finished = outcome;
            var row = new LinkedHashMap<String, Object>();
            row.put("scenario", encounter.id());
            row.put("profile", GameManager.combatTempo().name());
            row.put("elapsedStartTick", spawnedAt);
            row.put("fightTicks", fight);
            row.put("outcome", outcome);
            row.put("party", fighters.stream().map(p -> PlayerDataProvider.get(p).getCharacterId()).toList());
            row.put("levelAtStart", encounter.level());
            row.put("strengthPerHero", encounter.strength());
            row.put("startingBudgetGoldPerHero", startingBudget(encounter.strength()));
            row.put("healthAtStart", startingHealth);
            row.put("healthAtEnd", fighters.stream().map(ServerPlayer::getHealth).toList());
            row.put("grossHeroHealthLost", grossHealthLoss);
            row.put("monsterHealthAtEnd", target.isAlive() ? MonsterManager.modHealth(target) : 0);
            row.put("respawnDelayTicks", respawnAt < 0 ? null : respawnAt - fixture.level.getGameTime());
            row.put("rewards", rewards);
            row.put("hits", hits);
            event("fight", row);
            fixture.helper.assertTrue(hits.stream().anyMatch(hit -> hit.target().equals(encounter.type().getId())),
                    "At least one legitimate hero input must damage each encounter");
            fixture.helper.assertTrue(hits.stream().anyMatch(hit -> hit.action().equals("npc_attack")),
                    "The active monster returns real damage");
        }


    }

    private static boolean defeated(ServerPlayer player) {
        return !player.isAlive() || HeroRespawnSystem.isWaiting(player);
    }

    private static int startingBudget(int strength) { return strength == 1 ? 500 : 1000; }

    static void pengKing(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var center = fixture.floor(0);
            var stand = Vec3.atBottomCenterOf(center.north(2));
            var hunter = fixture.player(CharacterType.CHIXIAO, 8, 0, Faction.GUARDIAN, stand);
            var allyStand = Vec3.atBottomCenterOf(center.east(4));
            var ally = fixture.player(CharacterType.XUANCHEN, 8, 0, Faction.GUARDIAN, allyStand);
            var enemy = fixture.player(CharacterType.SHUO, 8, 0, Faction.DESTRUCTION, Vec3.atBottomCenterOf(center.west(4)));
            MonsterManager.activateSpawns(Map.of());
            fixture.begin(9600 - WARMUP);
            var target = helper.spawn(net.minecraft.world.entity.EntityTypes.PIG, helper.relativePos(center));
            fixture.probe = target;
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            target.setHealth(1000);
            fixture.listen(new TickCheck() {
                final List<Long> normal = new ArrayList<>(), buffed = new ArrayList<>(), expired = new ArrayList<>();
                final List<Long> normalShots = new ArrayList<>(), buffedShots = new ArrayList<>(), expiredShots = new ArrayList<>();
                double normalMod, normalVanilla, buffedMod, buffedVanilla, expiredMod, expiredVanilla;
                double normalMageMod, buffedMageMod, expiredMageMod;
                double normalMovement, buffedMovement, expiredMovement;
                long until = -1;

                @Override public boolean tick() {
                    fixture.tickPlayer(hunter, stand);
                    fixture.tickPlayer(ally, allyStand);
                    ally.setXRot(-85);
                    target.setPos(Vec3.atBottomCenterOf(center));
                    target.setDeltaMovement(Vec3.ZERO);
                    hunter.setYRot(0);
                    long elapsed = fixture.elapsed();
                    if (elapsed < 9600) return false;
                    if (until < 0) {
                        strike(normal);
                        shoot(normalShots);
                        if (normal.size() == 3 && normalShots.size() == 3 && hunter.tickCount % 20 == 0) {
                            normalMod = hunter.getAttributeValue(ModAttributes.ATTACK_SPEED);
                            normalVanilla = hunter.getAttributeValue(Attributes.ATTACK_SPEED);
                            normalMageMod = ally.getAttributeValue(ModAttributes.ATTACK_SPEED);
                            normalMovement = hunter.getAttributeValue(Attributes.MOVEMENT_SPEED);
                            var at = center.offset(4, 0, 4);
                            MonsterManager.spawn(fixture.level, at, MonsterType.GALE_ROC_MATRIARCH);
                            var boss = fixture.mob(MonsterType.GALE_ROC_MATRIARCH, at);
                            helper.assertTrue(boss != null, "Real Gale Roc spawned for the reward probe");
                            // Isolate the reward and recovery path from the unconfigured rotating-boss encounter.
                            DamageCalc.dealDamage(hunter, boss, MonsterManager.modMaxHealth(boss) + 100, DamageCalc.Kind.TRUE);
                            until = PlayerDataProvider.get(hunter).getGaleRocUntil();
                            helper.assertTrue(!boss.isAlive() && until - fixture.level.getGameTime() == MonsterManager.BOSS_BUFF_TICKS
                                    && PlayerDataProvider.get(ally).getGaleRocUntil() == until
                                    && PlayerDataProvider.get(enemy).getGaleRocUntil() == 0,
                                    "Actual kill credits the killer and friendly participant only");
                        }
                        return false;
                    }
                    if (fixture.level.getGameTime() < until) {
                        if (fixture.level.getGameTime() < until - MonsterManager.BOSS_BUFF_TICKS + 40) return false;
                        strike(buffed);
                        shoot(buffedShots);
                        if (hunter.tickCount % 20 == 0) {
                            buffedMod = hunter.getAttributeValue(ModAttributes.ATTACK_SPEED);
                            buffedVanilla = hunter.getAttributeValue(Attributes.ATTACK_SPEED);
                            buffedMageMod = ally.getAttributeValue(ModAttributes.ATTACK_SPEED);
                            buffedMovement = hunter.getAttributeValue(Attributes.MOVEMENT_SPEED);
                        }
                        return false;
                    }
                    if (fixture.level.getGameTime() < until + 20) return false;
                    strike(expired);
                    shoot(expiredShots);
                    if (expired.size() < 3 || expiredShots.size() < 3 || hunter.tickCount % 20 != 0) return false;
                    expiredMod = hunter.getAttributeValue(ModAttributes.ATTACK_SPEED);
                    expiredVanilla = hunter.getAttributeValue(Attributes.ATTACK_SPEED);
                    expiredMageMod = ally.getAttributeValue(ModAttributes.ATTACK_SPEED);
                    expiredMovement = hunter.getAttributeValue(Attributes.MOVEMENT_SPEED);
                    var evidence = new LinkedHashMap<String, Object>();
                    evidence.put("roles", List.of("chixiao", "xuanchen"));
                    evidence.put("level", 8);
                    evidence.put("strength", 0);
                    evidence.put("buffDurationTicks", MonsterManager.BOSS_BUFF_TICKS);
                    evidence.put("meleeNormal", attackInterval(normal, normalMod, normalVanilla));
                    evidence.put("meleeAfterSync", attackInterval(buffed, buffedMod, buffedVanilla));
                    evidence.put("meleeAfterExpirySync", attackInterval(expired, expiredMod, expiredVanilla));
                    evidence.put("mageNormal", attackInterval(normalShots, normalMageMod, normalMageMod));
                    evidence.put("mageAfterSync", attackInterval(buffedShots, buffedMageMod, buffedMageMod));
                    evidence.put("mageAfterExpirySync", attackInterval(expiredShots, expiredMageMod, expiredMageMod));
                    evidence.put("movement", List.of(normalMovement, buffedMovement, expiredMovement));
                    event("peng_king", evidence);
                    helper.assertTrue(expiredMod == normalMod && expiredVanilla == normalVanilla
                            && expiredMageMod == normalMageMod && Math.abs(buffedMovement / normalMovement - 1.15) < 1E-6
                            && normalMovement == expiredMovement,
                            "The production attributes revert after expiry and vanilla synchronization");
                    if ("CHERRY_V2".equals(GameManager.combatTempo().name())) {
                        helper.assertTrue(Math.abs(buffedMod / normalMod - 1.20) < 1E-6
                                && Math.abs(buffedVanilla - buffedMod) < 1E-6
                                && Math.abs(buffedMageMod / normalMageMod - 1.20) < 1E-6
                                && buffed.get(1) - buffed.getFirst() < normal.get(1) - normal.getFirst()
                                && buffedShots.get(1) - buffedShots.getFirst() < normalShots.get(1) - normalShots.getFirst(),
                                "Buffed native melee and sustained mage firing both recover faster after a real attribute sync");
                    }
                    return true;
                }

                private void shoot(List<Long> ticks) {
                    if (ticks.size() == 3) return;
                    MagicBullet.tryFire(ally, ally.getMainHandItem());
                    if (MagicBullet.remaining(ally) == MagicBullet.total(ally) && MagicBullet.remaining(ally) > 0) {
                        ticks.add(fixture.elapsed());
                    }
                }

                private void strike(List<Long> ticks) {
                    if (ticks.size() == 3) return;
                    float health = target.getHealth();
                    hunter.getRandom().setSeed(877L);
                    hunter.attack(target);
                    if (target.getHealth() < health) {
                        ticks.add(fixture.elapsed());
                        target.setHealth(1000);
                    }
                }
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static Map<String, Object> attackInterval(List<Long> hits, double mod, double vanilla) {
        return Map.of("hitTicks", hits, "firstInterval", hits.get(1) - hits.get(0),
                "secondInterval", hits.get(2) - hits.get(1), "modAttackSpeed", mod, "vanillaAttackSpeed", vanilla);
    }

    private static void event(String name, Map<String, ?> data) {
        var row = new LinkedHashMap<String, Object>();
        row.put("event", name);
        row.putAll(data);
        System.out.println("MYRIAD_BATTLEFIELD_MONSTER " + MatchProgress.JSON.toJson(row));
    }

    @FunctionalInterface
    private interface TickCheck { boolean tick(); }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final MinecraftServer server;
        private final ServerLevelData clock;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final Map<String, MonsterManager.SpawnPoint> originalPoints = new LinkedHashMap<>();
        private final com.google.gson.JsonElement originalMatch = GameManager.snapshot();
        private final com.google.gson.JsonObject originalMonsters = MonsterManager.snapshot();
        private final com.google.gson.JsonElement originalRespawns = HeroRespawnSystem.snapshot();
        private final boolean originalEnabled = ModState.isEnabled();
        private final boolean originalTraining = TrainingMode.isEnabled();
        private long startGameTime, startServerTick, matchStart;
        private Mob probe;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            server = level.getServer();
            clock = (ServerLevelData) level.getLevelData();
            for (var point : MonsterManager.spawnPointInfos()) {
                originalPoints.put(point.id(), new MonsterManager.SpawnPoint(point.id(), point.pos(),
                        MonsterType.byId(point.typeId())));
            }
            startGameTime = level.getGameTime();
            startServerTick = server.getTickCount();
            ModState.setEnabled(true);
            TrainingMode.setEnabled(false);
        }

        BlockPos floor(int index) {
            var center = helper.absolutePos(new BlockPos(7, 220 + index * 10, 7));
            CombatChunks.prepare(level, center);
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                var at = center.offset(x, -1, z);
                blocks.put(at, level.getBlockState(at));
                level.setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
            }
            return center;
        }

        ServerPlayer player(CharacterType role, int heroLevel, int strength, Faction faction, Vec3 position) {
            var player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {}
            };
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            server.getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
            players.add(player);
            player.setPos(position);
            player.setOnGround(true);
            level.players().remove(player);
            level.addNewPlayer(player);
            helper.assertTrue(level.getEntity(player.getUUID()) == player, "Mock hero is tracked for actual NPC retaliation");
            var data = PlayerDataProvider.get(player);
            data.setFaction(faction);
            data.setCharacterId(role.getId());
            data.setLevel(heroLevel);
            data.setGold(strength == 0 ? 500 : startingBudget(strength) - (strength == 1 ? 360 : 792));
            data.setFirstGroupAttribute("strength", strength);
            PlayerDataProvider.applyChosenCharacter(player, data);
            FirstGroupGrowth.apply(player, data);
            var crit = player.getAttribute(ModAttributes.CRIT_CHANCE);
            crit.removeModifiers();
            crit.setBaseValue(0);
            return player;
        }

        void begin(long elapsed) {
            clock.setGameTime(startGameTime + 12000);
            matchStart = level.getGameTime() - elapsed;
            var match = MatchProgress.JSON.toJsonTree(new GameManager()).getAsJsonObject();
            match.addProperty("phase", "PLAYING");
            match.addProperty("matchStartTime", matchStart);
            match.addProperty("tempo", System.getenv().getOrDefault("MYRIAD_MONSTER_FIXTURE_TEMPO", "CHERRY_V1"));
            for (var player : players) {
                match.getAsJsonObject("factions").addProperty(player.getUUID().toString(),
                        PlayerDataProvider.get(player).getFaction().getName());
            }
            GameManager.restore(match, server);
        }

        long elapsed() { return level.getGameTime() - matchStart; }

        Mob mob(MonsterType type, BlockPos center) {
            return level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(4),
                    mob -> mob.isAlive() && MonsterManager.typeOf(mob) == type).stream().findFirst().orElse(null);
        }

        long respawnAt(String id) {
            for (var row : MonsterManager.snapshot().getAsJsonArray("respawns")) {
                var pending = row.getAsJsonObject();
                if (pending.has("pointId") && id.equals(pending.get("pointId").getAsString())) return pending.get("at").getAsLong();
            }
            return -1;
        }

        void roundTrip() {
            GameManager.restore(GameManager.snapshot(), server);
            MonsterManager.restore(level, MonsterManager.snapshot());
        }

        void verifyRespawns(List<Combat> combats) {
            long current = level.getGameTime();
            roundTrip();
            var deadlines = new java.util.TreeMap<Long, List<Combat>>();
            for (var combat : combats) {
                if (combat.respawnAt < 0) continue;
                helper.assertTrue(respawnAt(combat.encounter.id()) == combat.respawnAt,
                        "Checkpoint restores the absolute death timer");
                long pollingTick = ((combat.respawnAt + 19) / 20) * 20;
                deadlines.computeIfAbsent(pollingTick, ignored -> new ArrayList<>()).add(combat);
            }
            for (var entry : deadlines.entrySet()) {
                long pollingTick = entry.getKey();
                clock.setGameTime(pollingTick - 1);
                MonsterManager.tick(level);
                for (var combat : entry.getValue()) {
                    helper.assertTrue(mob(combat.encounter.type(), combat.center) == null, "No premature respawn");
                }
                clock.setGameTime(pollingTick);
                MonsterManager.tick(level);
                for (var combat : entry.getValue()) {
                    var respawned = mob(combat.encounter.type(), combat.center);
                    helper.assertTrue(respawned != null && !respawned.getUUID().equals(combat.target.getUUID()), "Real second spawn");
                    helper.assertTrue(respawnAt(combat.encounter.id()) < 0, "The pending timer is consumed exactly once");
                    event("respawn", Map.of("scenario", combat.encounter.id(), "scheduledElapsedTick", combat.respawnAt - matchStart,
                            "pollElapsedTick", pollingTick - matchStart, "generation", 1,
                            "health", MonsterManager.modMaxHealth(respawned), "checkpointRestored", true));
                }
            }
            clock.setGameTime(current);
        }

        void tickPlayer(ServerPlayer player, Vec3 position) {
            if (defeated(player)) return;
            player.setPos(position);
            player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
            player.doTick();
            PlayerDataProvider.onPlayerTick(player);
        }

        void listen(TickCheck check) {
            GameTestTicks.addListener(new Consumer<>() {
                @Override public void accept(MinecraftServer ignored) {
                    try {
                        if (!check.tick()) return;
                    } catch (RuntimeException | Error failure) {
                        GameTestTicks.removeListener(this);
                        close();
                        helper.runAfterDelay(1, () -> { throw failure; });
                        return;
                    }
                    GameTestTicks.removeListener(this);
                    close();
                    helper.runAfterDelay(1, helper::succeed);
                }
            });
        }

        void close() {
            for (var player : players) {
                AbilityRuntime.clear(player);
                BasicAttackSystem.clearFor(player.getUUID());
                MagicBullet.clearFor(player.getUUID());
                server.getPlayerList().getPlayersByUUID().remove(player.getUUID(), player);
                level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            if (probe != null) probe.discard();
            clock.setGameTime(startGameTime + server.getTickCount() - startServerTick);
            GameManager.restore(originalMatch, server);
            MonsterManager.activateSpawns(originalPoints);
            MonsterManager.restore(level, originalMonsters);
            HeroRespawnSystem.restore(originalRespawns);
            blocks.forEach(level::setBlockAndUpdate);
            TrainingMode.setEnabled(originalTraining);
            ModState.setEnabled(originalEnabled);
        }
    }
}
