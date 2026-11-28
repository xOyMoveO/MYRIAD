package com.myriad.game;

import com.google.gson.Gson;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.keheng.BeyondOneSecondSkill;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

final class CalibrationSupportChecks {
    private static final int LEVEL = 12;
    private static final int WARMUP_TICKS = 24;
    private static final int OBSERVE_TICKS = 120;
    private static final int PRESSURE_GAP = 15;
    private static final float PRESSURE_AMOUNT = 165;
    private static final double COUNTERATTACK_HP_FRACTION = .60;
    private static final long ATTACK_SEED_BASE = 110;
    private static final Gson JSON = new Gson().newBuilder().serializeNulls().create();

    private CalibrationSupportChecks() {}

    static void keheng(GameTestHelper h) {
        var fixture = new Fixture(h);
        fixture.start(() -> {
            var withDome = new EscapeTrial(fixture, "baseline", 0, true);
            var withoutDome = new EscapeTrial(fixture, "without_r", 1, false);
            fixture.observe(tick -> {
                withDome.step(tick);
                withoutDome.step(tick);
                if (tick != OBSERVE_TICKS) return;
                h.assertTrue(withDome.entered != null, "Retreating hero must be affected by the live dome");
                h.assertTrue(withDome.exited != null && withoutDome.exited != null,
                        "Both retreating heroes must physically cross the same radius");
                event("keheng.escape", "baseline", "paired_delta", tick,
                        "exitDelayTicks", withDome.exited - withoutDome.exited,
                        "additionalDamageBeforeExit", withDome.damageBeforeExit - withoutDome.damageBeforeExit);
            });
        });
    }

    static void qingluo(GameTestHelper h) {
        var fixture = new Fixture(h);
        fixture.start(() -> {
            var sequence = new RescueTrial(fixture, "baseline", 0, true, true);
            var ultimate = new RescueTrial(fixture, "r_only", 1, true, false);
            var noSupport = new RescueTrial(fixture, "without_support", 2, false, false);
            fixture.observe(tick -> {
                sequence.step(tick);
                ultimate.step(tick);
                noSupport.step(tick);
                if (tick != OBSERVE_TICKS) return;
                h.assertTrue(sequence.effectiveHealing > ultimate.effectiveHealing && sequence.absorbed > 0,
                        "The connected Q and R must add real healing and consumed shield");
                h.assertTrue(sequence.pressureHits == ultimate.pressureHits
                                && sequence.pressureHits == noSupport.pressureHits
                                && sequence.pressureHits == pressureHits(),
                        "All arms must receive every scheduled damage pulse");
                event("qingluo.rescue", "baseline", "paired_delta", tick,
                        "effectiveHealingVsWithoutSupport", sequence.effectiveHealing - noSupport.effectiveHealing,
                        "effectiveHealingVsROnly", sequence.effectiveHealing - ultimate.effectiveHealing,
                        "absorbedVsWithoutSupport", sequence.absorbed - noSupport.absorbed,
                        "absorbedVsROnly", sequence.absorbed - ultimate.absorbed,
                        "endHpVsWithoutSupport", sequence.ally.getHealth() - noSupport.ally.getHealth(),
                        "endHpVsROnly", sequence.ally.getHealth() - ultimate.ally.getHealth(),
                        "outputDamageVsWithoutSupport", sequence.outputDamage - noSupport.outputDamage,
                        "outputDamageVsROnly", sequence.outputDamage - ultimate.outputDamage);
            });
        });
    }

    private static int pressureHits() {
        return (OBSERVE_TICKS - 1) / PRESSURE_GAP + 1;
    }

    private static final class EscapeTrial {
        private final GameTestHelper h;
        private final String id;
        private final boolean castDome;
        private final ServerPlayer caster, follower, enemy;
        private final Vec3 start;
        private Vec3 center;
        private Integer entered, firstHit, exited, firstUnslowed;
        private float damageBeforeExit;

        EscapeTrial(Fixture fixture, String id, int lane, boolean castDome) {
            h = fixture.h;
            this.id = id;
            this.castDome = castDome;
            start = fixture.floor(lane);
            caster = fixture.player("keheng", Faction.GUARDIAN, start.add(0, 0, -4));
            follower = fixture.player("tianxing", Faction.GUARDIAN, start.add(.8, 0, -4.8));
            enemy = fixture.player("yingxiao", Faction.DESTRUCTION, start);
            learn(h, caster, 6, 3);
        }

        void step(int tick) {
            if (tick == 0) {
                aim(caster, start);
                var dome = FirstGroupSkills.node("keheng", 6);
                center = AbilityRuntime.context(caster, dome).point(BeyondOneSecondSkill.RANGE);
                h.assertTrue(center != null && center.distanceTo(start) < .1, "Aim R at the retreating enemy's feet");
                event("keheng.escape", id, "start", tick,
                        "heroLevel", LEVEL, "purchasedAttributePoints", 0, "ultimateRank", 3, "radiusBlocks",
                        BeyondOneSecondSkill.RADIUS, "warningTicks", BeyondOneSecondSkill.WARNING_TICKS,
                        "activeTicks", BeyondOneSecondSkill.DURATION_TICKS, "domeApplied", castDome,
                        "enemyHp", enemy.getHealth(), "allyStartDx", .8, "allyStartDz", -4.8,
                        "enemyBaseMoveSpeed", enemy.getAttributeValue(Attributes.MOVEMENT_SPEED),
                        "allyBaseMoveSpeed", follower.getAttributeValue(Attributes.MOVEMENT_SPEED));
                if (castDome) {
                    event("keheng.escape", id, "cast", tick,
                            "manaSpent", cast(h, caster, 6, true),
                            "cooldownTicks", PlayerDataProvider.get(caster).getCooldown(dome.getId()));
                }
                return;
            }

            double beforeRate = ActionTime.multiplier(enemy.getUUID(), enemy.level().getGameTime());
            if (beforeRate < 1 && entered == null) {
                entered = tick;
                event("keheng.escape", id, "field_entry", tick, "enemyRate", beforeRate,
                        "distanceBlocks", distance(), "enemyHp", enemy.getHealth());
            }
            if (exited == null) {
                walk(enemy);
                if (distance() > BeyondOneSecondSkill.RADIUS + .000001) {
                    exited = tick;
                    event("keheng.escape", id, "radius_exit", tick, "distanceBlocks", distance(),
                            "enemyHp", enemy.getHealth());
                }
                if (exited == null && firstHit == null) walk(follower);
                if (exited == null && firstHit == null && follower.isWithinEntityInteractionRange(enemy, 0)
                        && follower.getAttackStrengthScale(.5F) >= 1) {
                    float hp = enemy.getHealth();
                    basic(follower, enemy, tick);
                    float damage = hp - enemy.getHealth();
                    event("keheng.escape", id, "ally_attack", tick, "effectiveDamage", damage,
                            "distanceBlocks", distance(), "enemyHp", enemy.getHealth());
                    if (damage > 0) {
                        firstHit = tick;
                        damageBeforeExit = damage;
                    }
                }
            }

            KehengTimeFields.refreshActor(enemy);
            double afterRate = ActionTime.multiplier(enemy.getUUID(), enemy.level().getGameTime());
            if (entered != null && afterRate == 1 && firstUnslowed == null) {
                firstUnslowed = tick;
                event("keheng.escape", id, "slow_end", tick, "distanceBlocks", distance());
            }
            if (tick <= (exited == null ? OBSERVE_TICKS : exited)) {
                event("keheng.escape", id, "position", tick,
                        "distanceBlocks", distance(), "enemyDz", enemy.getZ() - start.z,
                        "allyDz", follower.getZ() - start.z, "enemyRate", afterRate,
                        "enemyHp", enemy.getHealth());
            }
            if (tick == OBSERVE_TICKS) {
                event("keheng.escape", id, "summary", tick, "fieldEntryTick", entered, "exitTick", exited,
                        "firstUnslowedTick", firstUnslowed, "firstEffectiveAllyHitTick", firstHit,
                        "allyHitBeforeExit", firstHit != null && exited != null && firstHit < exited,
                        "allyDamageBeforeExit", damageBeforeExit,
                        "allyLeadTicks", firstHit != null && exited != null ? exited - firstHit : null);
            }
        }

        private double distance() {
            return center.distanceTo(enemy.position());
        }
    }

    private static final class RescueTrial {
        private final GameTestHelper h;
        private final String id;
        private final boolean castR, castQ;
        private final ServerPlayer healer, ally, enemy;
        private final float maxHp;
        private Integer firstOutput;
        private float effectiveHealing, absorbed, pressureHpLost, outputDamage, lowHp;
        private int pressureHits;

        RescueTrial(Fixture fixture, String id, int lane, boolean castR, boolean castQ) {
            h = fixture.h;
            this.id = id;
            this.castR = castR;
            this.castQ = castQ;
            Vec3 start = fixture.floor(lane);
            healer = fixture.player("qingluo", Faction.GUARDIAN, start.add(-4, 0, 0));
            ally = fixture.player("chixiao", Faction.GUARDIAN, start);
            enemy = fixture.player("yingxiao", Faction.DESTRUCTION, start.add(0, 0, 2));
            maxHp = ally.getMaxHealth();
            learn(h, healer, 7, 3);
            learn(h, healer, 2, 3);
        }

        void step(int tick) {
            if (tick == 0) {
                ally.setHealth(maxHp * .5F);
                lowHp = ally.getHealth();
                event("qingluo.rescue", id, "start", tick,
                        "heroLevel", LEVEL, "purchasedAttributePoints", 0, "allyHp", lowHp,
                        "allyMaxHp", maxHp, "minimumOutputHpFraction", COUNTERATTACK_HP_FRACTION,
                        "pressureRawAmount", PRESSURE_AMOUNT, "pressureGapTicks", PRESSURE_GAP,
                        "allyPhysicalDefense", ally.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE),
                        "ultimateRank", 3, "qRank", 3);
            }
            if (tick > 0 && (tick - 1) % PRESSURE_GAP == 0) {
                float beforeShield = ModShieldSystem.value(ally);
                float beforeHp = ally.getHealth();
                DamageCalc.dealDamage(enemy, ally, PRESSURE_AMOUNT, DamageCalc.Kind.PHYSICAL);
                float consumed = beforeShield - ModShieldSystem.value(ally);
                float lostHp = beforeHp - ally.getHealth();
                h.assertTrue(consumed + lostHp > 0, "Incoming physical pulse must connect at " + tick + " for " + id);
                pressureHits++;
                absorbed += consumed;
                pressureHpLost += lostHp;
                event("qingluo.rescue", id, "pressure_hit", tick, "effectiveDamageToHp", lostHp,
                        "absorbed", consumed, "shieldRemaining", ModShieldSystem.value(ally), "allyHp", ally.getHealth());
            }
            if (castR && tick == 2) support(tick, 7, "cast_r");
            if (castQ && tick == 10) support(tick, 2, "cast_q");
            if (tick >= 12 && (tick - 12) % 20 == 0) {
                float before = enemy.getHealth();
                boolean fights = ally.getHealth() >= maxHp * COUNTERATTACK_HP_FRACTION;
                if (fights) basic(ally, enemy, tick);
                float damage = before - enemy.getHealth();
                if (firstOutput == null && damage > 0) firstOutput = tick;
                outputDamage += damage;
                event("qingluo.rescue", id, "ally_output", tick, "inputSent", fights,
                        "effectiveDamage", damage, "allyHp", ally.getHealth(), "enemyHp", enemy.getHealth());
            }
            lowHp = Math.min(lowHp, ally.getHealth());
            event("qingluo.rescue", id, "health_sample", tick, "allyHp", ally.getHealth(),
                    "shieldRemaining", ModShieldSystem.value(ally));
            if (tick == OBSERVE_TICKS) {
                h.assertTrue(Math.abs(maxHp * .5F + effectiveHealing - pressureHpLost - ally.getHealth()) < .02,
                        "Every HP change must be accounted for by scheduled damage and skill healing");
                event("qingluo.rescue", id, "summary", tick, "effectiveHealing", effectiveHealing,
                        "consumedShield", absorbed, "endHp", ally.getHealth(), "minimumHp", lowHp,
                        "pressureHpLost", pressureHpLost,
                        "firstEffectiveOutputTick", firstOutput, "outputDamage", outputDamage,
                        "connectedPressureHits", pressureHits);
            }
        }

        private void support(int tick, int node, String name) {
            aim(healer, ally.getBoundingBox().getCenter());
            float beforeHp = ally.getHealth(), beforeShield = ModShieldSystem.value(ally);
            float spent = cast(h, healer, node, false);
            float restored = ally.getHealth() - beforeHp;
            float granted = ModShieldSystem.value(ally) - beforeShield;
            h.assertTrue(restored > 0, "The ally must receive " + name);
            effectiveHealing += restored;
            event("qingluo.rescue", id, name, tick, "manaSpent", spent, "effectiveHealing", restored,
                    "shieldGranted", granted, "allyHp", ally.getHealth());
        }
    }

    private static final class Fixture {
        private final GameTestHelper h;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        private final boolean modEnabled, trainingEnabled, noCooldown, noGold, noMana, naturalRegen;
        private final com.google.gson.JsonElement savedGame;
        private Map<UUID, Faction> seats;

        Fixture(GameTestHelper h) {
            this.h = h;
            savedGame = GameManager.snapshot();
            modEnabled = ModState.isEnabled();
            trainingEnabled = TrainingMode.isEnabled();
            noCooldown = TrainingMode.isNoCooldown();
            noGold = TrainingMode.isNoGoldCost();
            noMana = TrainingMode.isNoMana();
            naturalRegen = h.getLevel().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
        }

        void start(Runnable build) {
            try {
                ModState.setEnabled(true);
                TrainingMode.setEnabled(false);
                h.getLevel().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, h.getLevel().getServer());
                GameManager.create();
                var manager = GameManager.get();
                manager.setServer(h.getLevel().getServer());
                var phase = GameManager.class.getDeclaredField("phase");
                phase.setAccessible(true);
                phase.set(manager, GameManager.Phase.PLAYING);
                var factions = GameManager.class.getDeclaredField("factions");
                factions.setAccessible(true);
                @SuppressWarnings("unchecked")
                var playingSeats = (Map<UUID, Faction>) factions.get(manager);
                seats = playingSeats;
                build.run();
            } catch (ReflectiveOperationException failure) {
                cleanup();
                throw new IllegalStateException(failure);
            } catch (RuntimeException | Error failure) {
                cleanup();
                throw failure;
            }
        }

        Vec3 floor(int lane) {
            BlockPos at = h.absolutePos(new BlockPos(4, 208 + lane * 10, 4));
            for (int x = -5; x <= 2; x++) for (int z = -6; z <= 8; z++) {
                BlockPos support = at.offset(x, -1, z);
                CombatChunks.prepare(h.getLevel(), support);
                floor.put(support, h.getLevel().getBlockState(support));
                h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            }
            return Vec3.atBottomCenterOf(at);
        }

        ServerPlayer player(String role, Faction side, Vec3 at) {
            ServerPlayer p = ShuoChecks.player(h, at, new ArrayList<>());
            players.add(p);
            var data = PlayerDataProvider.get(p);
            data.setCharacterId(role);
            data.setLevel(LEVEL);
            data.setSkillPoints(LEVEL);
            data.setFaction(side);
            seats.put(p.getUUID(), side);
            PlayerDataProvider.applyChosenCharacter(p, data);
            // Suppress incidental regeneration and random crits so HP changes can be assigned to events.
            p.getAttribute(ModAttributes.HEALTH_REGEN).setBaseValue(0);
            p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
            p.setNoGravity(false);
            p.setOnGround(true);
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(), p);
            h.getLevel().addNewPlayer(p);
            return p;
        }

        void observe(IntConsumer step) {
            long start = h.getLevel().getGameTime() + WARMUP_TICKS;
            boolean[] finished = {false};
            for (int delay = 1; delay <= WARMUP_TICKS + OBSERVE_TICKS; delay++) {
                h.runAfterDelay(delay, () -> {
                    if (finished[0]) return;
                    try {
                        int tick = Math.toIntExact(h.getLevel().getGameTime() - start);
                        KehengTimeFields.tick(h.getLevel());
                        for (var p : players) {
                            p.doTick();
                            PlayerDataProvider.onPlayerTick(p);
                        }
                        if (tick >= 0) step.accept(tick);
                        if (tick < OBSERVE_TICKS) return;
                    } catch (RuntimeException | Error failure) {
                        finished[0] = true;
                        cleanup();
                        throw failure;
                    }
                    finished[0] = true;
                    cleanup();
                    h.succeed();
                });
            }
        }

        private void cleanup() {
            for (var p : players) {
                AbilityRuntime.clear(p);
                BasicAttackSystem.clearFor(p.getUUID());
                ModShieldSystem.clearFor(p);
                BaseRules.logout(p);
                h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());
                h.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            }
            floor.forEach(h.getLevel()::setBlockAndUpdate);
            h.getLevel().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, naturalRegen, h.getLevel().getServer());
            GameManager.restore(savedGame, h.getLevel().getServer());
            TrainingMode.setEnabled(trainingEnabled);
            TrainingMode.setNoCooldown(noCooldown);
            TrainingMode.setNoGoldCost(noGold);
            TrainingMode.setNoMana(noMana);
            ModState.setEnabled(modEnabled);
        }
    }

    private static void learn(GameTestHelper h, ServerPlayer p, int node, int rank) {
        var data = PlayerDataProvider.get(p);
        String id = FirstGroupSkills.node(data.getCharacterId(), node).getId();
        for (int point = 0; point < rank; point++) PlayerDataProvider.upgradeSkill(p, id);
        h.assertTrue(data.skillLevel(id) == rank, "Legal rank " + rank + " for " + id);
    }

    private static float cast(GameTestHelper h, ServerPlayer p, int node, boolean confirmPoint) {
        var data = PlayerDataProvider.get(p);
        var skill = FirstGroupSkills.node(data.getCharacterId(), node);
        float mana = data.getMana();
        SkillSystem.cast(p, skill.slot());
        if (confirmPoint) AbilityRuntime.confirm(p);
        float spent = mana - data.getMana();
        h.assertTrue(spent > 0 && data.getCooldown(skill.getId()) > 0, "Normal cast payment for " + skill.getId());
        return spent;
    }

    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 delta = point.subtract(p.getEyePosition());
        p.setYRot((float) -Math.toDegrees(Math.atan2(delta.x, delta.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }

    private static void basic(ServerPlayer actor, ServerPlayer target, int tick) {
        aim(actor, target.getBoundingBox().getCenter());
        actor.getRandom().setSeed(ATTACK_SEED_BASE + tick);
        actor.attack(target);
    }

    /** Script the forward input through native movement on the grounded synthetic lane. */
    private static void walk(ServerPlayer p) {
        p.setYRot(0);
        p.setXRot(0);
        p.setSpeed((float) p.getAttributeValue(Attributes.MOVEMENT_SPEED));
        p.travel(new Vec3(0, 0, .98));
    }

    private static void event(String scenario, String id, String name, int tick, Object... fields) {
        var row = new LinkedHashMap<String, Object>();
        row.put("scenarioId", scenario);
        row.put("scenarioVersion", 1);
        row.put("caseId", id);
        row.put("event", name);
        row.put("tick", tick);
        for (int i = 0; i < fields.length; i += 2) row.put((String) fields[i], fields[i + 1]);
        System.out.println("BALANCE_CALIBRATION " + JSON.toJson(row));
    }
}
