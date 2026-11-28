package com.myriad.game;

import com.google.gson.Gson;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

final class MageSupportBalanceChecks {
    private static final int WARMUP = 24;
    private static final int END = 106;
    private static final Gson JSON = new Gson();
    private static final String GUARDIAN = "g1_xuanchen_pa";
    private static final String ECHO = "g2_yunsheng_pa";
    private static final String BEACON = "g2_yunsheng_ma";
    private static final String SHELTER = "g2_yunsheng_ub";
    private static final List<String> SUPPORT_SHIELDS = List.of(ECHO, BEACON, SHELTER);

    private final GameTestHelper h;
    private final List<ServerPlayer> players = new ArrayList<>();
    private final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
    private final com.google.gson.JsonElement savedGame = GameManager.snapshot();
    private final boolean mod = ModState.isEnabled(), training = TrainingMode.isEnabled();
    private final boolean naturalRegen;
    private Map<UUID, Faction> seats;
    private boolean done;

    private MageSupportBalanceChecks(GameTestHelper h) {
        this.h = h;
        naturalRegen = h.getLevel().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
    }

    static void run(GameTestHelper h) {
        new MageSupportBalanceChecks(h).start();
    }

    private void start() {
        ModState.setEnabled(true);
        TrainingMode.setEnabled(false);
        h.getLevel().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, false, h.getLevel().getServer());
        try {
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
            var open = new InkTrial(0, false);
            var interrupted = new InkTrial(1, true);
            var heal = new SpiritTrial(2, true);
            var shield = new SpiritTrial(3, false);
            for (int delay = 1; delay <= WARMUP + END; delay++) {
                int tick = delay - WARMUP;
                h.runAfterDelay(delay, () -> {
                    if (done) return;
                    try {
                        for (var player : players) {
                            player.doTick();
                            PlayerDataProvider.onPlayerTick(player);
                        }
                        if (tick < 0) return;
                        open.step(tick);
                        interrupted.step(tick);
                        heal.step(tick);
                        shield.step(tick);
                        if (tick == END) {
                            done = true;
                            cleanup();
                            h.succeed();
                        }
                    } catch (RuntimeException | Error failure) {
                        if (!done) cleanup();
                        done = true;
                        throw failure;
                    }
                });
            }
        } catch (ReflectiveOperationException failure) {
            cleanup();
            throw new IllegalStateException(failure);
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    private final class InkTrial {
        final Vec3 origin;
        final ServerPlayer caster, primary, outside, enemy;
        final boolean cancel;
        final String id;
        float initialEnemyHealth;
        float magicInside;
        float magicOutside;

        InkTrial(int lane, boolean cancel) {
            this.cancel = cancel;
            id = cancel ? "interrupted" : "completed";
            origin = ground(lane);
            caster = player("xuanchen", Faction.GUARDIAN, origin);
            primary = player("luoxia", Faction.GUARDIAN, origin.add(-1.5, 0, 0));
            outside = player("luoxia", Faction.GUARDIAN, origin.add(7, 0, 0));
            enemy = player("tianxing", Faction.DESTRUCTION, origin.add(0, 0, 3));
            learn(caster, 0);
            learn(caster, 5);
            learn(caster, 6);
        }

        void step(int tick) {
            switch (tick) {
                case 0 -> {
                    primary.setHealth(primary.getMaxHealth() * .45F);
                    outside.setHealth(outside.getMaxHealth() * .75F);
                    initialEnemyHealth = enemy.getHealth();
                    cast(caster, 6, null, id, tick);
                    h.assertTrue(AbilityRuntime.channeling(caster), "R-A starts a real paid channel");
                    near(ModShieldSystem.value(primary), 65.5, "R-A commit awards exact lowest-HP-ratio ally shield");
                    near(ModShieldSystem.value(outside), 0, "R-A passive skips the healthier ally");
                    near(ModShieldSystem.value(caster), 0, "R-A passive takes an ally before self");
                    log("xuanchen", id, "lowest_ally_shield", tick, "target", "primary",
                            "primaryRatio", .45, "outsideRatio", .75, "newShield", ModShieldSystem.value(primary));
                }
                case 7 -> {
                    if (cancel) AbilityRuntime.context(enemy, FirstGroupSkills.node("tianxing", 2)).root(caster, 8, true);
                    h.assertTrue(AbilityRuntime.channeling(caster), "Interruption happens during the R-A windup");
                    if (cancel) log("xuanchen", id, "enemy_stun_applied", tick,
                            "stunTicks", PlayerDataProvider.get(caster).getBuff("g1_stun"));
                }
                case 8 -> {
                    h.assertTrue(AbilityRuntime.channeling(caster) != cancel, "Enemy stun interrupts channel on the next tick");
                    log("xuanchen", id, "channel_sample", tick, "active", AbilityRuntime.channeling(caster));
                }
                case 9 -> {
                    var receipt = damage(enemy, primary, 100, DamageCalc.Kind.TRUE);
                    near(receipt.absorbed(), 65.5, "Legal incoming damage consumes the passive shield");
                    logHit("xuanchen", id, "primary", tick, receipt);
                }
                case 16 -> {
                    h.assertTrue(!AbilityRuntime.channeling(caster), "Both windups have terminated");
                    log("xuanchen", id, "channel_sample", tick, "active", false);
                }
                case 25 -> {
                    var in = damage(enemy, primary, 100, DamageCalc.Kind.MAGIC);
                    var out = damage(enemy, outside, 100, DamageCalc.Kind.MAGIC);
                    magicInside = in.health();
                    magicOutside = out.health();
                    h.assertTrue(magicOutside > 0, "The outside ally takes real HP damage");
                    near(magicInside / magicOutside, cancel ? 1 : .85, "Actual R-A magic reduction in and out");
                    logHit("xuanchen", id, "primary_inside", tick, in);
                    logHit("xuanchen", id, "outside", tick, out);
                }
                case 31 -> {
                    var in = damage(enemy, primary, 60, DamageCalc.Kind.TRUE);
                    var out = damage(enemy, outside, 60, DamageCalc.Kind.TRUE);
                    near(in.health(), out.health(), "R-A guard does not reduce true damage");
                    logHit("xuanchen", id, "primary_inside", tick, in);
                    logHit("xuanchen", id, "outside", tick, out);
                }
                case 45 -> {
                    float net = initialEnemyHealth - enemy.getHealth();
                    h.assertTrue(cancel ? net == 0 : net > 0, "Only the completed windup produces an enemy pulse");
                    log("xuanchen", id, "ultimate_sample", tick, "enemyHpLost", net,
                            "magicDamageInside", magicInside, "magicDamageOutside", magicOutside);
                }
                case 65 -> cast(caster, 5, origin.add(0, 0, 2), id, tick);
                case 86 -> {
                    var in = damage(enemy, primary, 100, DamageCalc.Kind.TRUE);
                    near(in.absorbed(), 70.5, "E-B activation grants one shield in its zone");
                    logHit("xuanchen", id, "primary_inside", tick, in);
                }
                case 92 -> {
                    var in = damage(enemy, primary, 100, DamageCalc.Kind.MAGIC);
                    var out = damage(enemy, outside, 100, DamageCalc.Kind.MAGIC);
                    near(in.absorbed(), 0, "The E-B shield is already spent in the overlap window");
                    near(in.health() / out.health(), cancel ? .88 : .85, "Overlapping guards choose the stronger reduction");
                    logHit("xuanchen", id, "primary_overlap", tick, in);
                    logHit("xuanchen", id, "outside", tick, out);
                }
                case 103 -> {
                    var in = damage(enemy, primary, 100, DamageCalc.Kind.MAGIC);
                    var out = damage(enemy, outside, 100, DamageCalc.Kind.MAGIC);
                    near(in.absorbed(), 0, "E-B does not continually refill the spent shield");
                    near(in.health() / out.health(), .88, "Actual E-B magic reduction in and out");
                    logHit("xuanchen", id, "primary_inside", tick, in);
                    logHit("xuanchen", id, "outside", tick, out);
                }
                default -> { }
            }
        }
    }

    private final class SpiritTrial {
        final Vec3 origin;
        final ServerPlayer caster, enemy;
        final List<Benefit> team;
        final boolean heal;
        final String id;

        SpiritTrial(int lane, boolean heal) {
            this.heal = heal;
            id = heal ? "chorus_beacon" : "shelter_beacon";
            origin = ground(lane);
            caster = player("yunsheng", Faction.GUARDIAN, origin);
            ServerPlayer primary = player("luoxia", Faction.GUARDIAN, origin.add(0, 0, 3));
            ServerPlayer secondary = player("luoxia", Faction.GUARDIAN, origin.add(-2, 0, 2));
            enemy = player("tianxing", Faction.DESTRUCTION, origin.add(3, 0, 1));
            team = List.of(new Benefit("primary", primary, caster), new Benefit("secondary", secondary, caster),
                    new Benefit("low_pressure_caster", caster, caster));
            learn(caster, 0);
            learn(caster, 2);
            learn(caster, 4);
            learn(caster, heal ? 6 : 7);
        }

        void step(int tick) {
            if (tick == 0) {
                team.get(0).player.setHealth(team.get(0).player.getMaxHealth() * .50F);
                team.get(1).player.setHealth(team.get(1).player.getMaxHealth() * .75F);
                caster.setHealth(caster.getMaxHealth());
                team.forEach(Benefit::reset);
                cast(caster, heal ? 6 : 7, heal ? origin.add(0, 0, 2) : null, id, tick);
            }
            if (tick == (heal ? 24 : 5)) cast(caster, 4, origin.add(0, 0, 2), id, tick);
            if (tick == (heal ? 33 : 12)) cast(caster, 2, team.getFirst().player.getBoundingBox().getCenter(), id, tick);
            for (var target : team) target.sample(id, tick, heal);
            if (tick == 28 || tick == 52 || tick == 76) {
                team.get(0).pressure(enemy, id, tick, 130);
                team.get(1).pressure(enemy, id, tick, 55);
            }
            if (tick == 61) team.get(2).pressure(enemy, id, tick, 22);
            if (tick == END) {
                var primary = team.getFirst();
                var low = team.getLast();
                h.assertTrue(primary.absorbed > low.absorbed && primary.ward > 0,
                        "The repeat-support shield is granted in the real primary pressure sequence");
                h.assertTrue(primary.healing > 0, "The primary receives actual healing");
                if (heal) {
                    near(team.get(1).healing, 210, "Four actual Chorus pulses each heal the secondary seat");
                    near(low.healing, 0, "The low-pressure caster remains at full HP on each pulse");
                    near(low.overflow, 210, "All four low-pressure Chorus pulses overflow");
                }
                h.assertTrue(low.repeated > 0, "The low-pressure member receives refreshes without new shield");
                for (var target : team) target.summary(id, tick);
            }
        }
    }

    private record DamageReceipt(float raw, String kind, float health, float absorbed) {}

    private static final class Benefit {
        final String label;
        final ServerPlayer player, caster;
        Map<String, Float> previous;
        float lastHp, healing, overflow, absorbed, granted, ward;
        int repeated;

        Benefit(String label, ServerPlayer player, ServerPlayer caster) {
            this.label = label;
            this.player = player;
            this.caster = caster;
        }

        void reset() {
            previous = shields();
            lastHp = player.getHealth();
        }

        void sample(String scenario, int tick, boolean chorus) {
            var next = shields();
            for (String source : SUPPORT_SHIELDS) {
                float before = previous.get(source);
                float after = next.get(source);
                if (after <= before) continue;
                float delta = after - before;
                granted += delta;
                if (source.equals(ECHO)) ward += delta;
                log("yunsheng", scenario, "shield_gain", tick, "target", label, "source", source,
                        "newShield", delta, "activeSourceShield", after);
            }
            int beaconCast = chorus ? 24 : 5;
            if (tick > beaconCast && (tick - beaconCast) % 20 == 0 && next.get(BEACON) > 0
                    && Float.compare(next.get(BEACON), previous.get(BEACON)) == 0) {
                repeated++;
                log("yunsheng", scenario, "same_source_refresh", tick, "target", label,
                        "source", BEACON, "newShield", 0, "activeSourceShield", next.get(BEACON));
            }
            previous = next;
            float gained = Math.max(0, player.getHealth() - lastHp);
            boolean chorusPulse = chorus && tick >= 40 && (tick - 40) % 20 == 0;
            if (gained > 0 || chorusPulse) {
                float nominal = chorusPulse ? 52.5F : 49;
                float waste = Math.max(0, nominal - gained);
                healing += gained;
                overflow += waste;
                log("yunsheng", scenario, "heal", tick, "target", label,
                        "source", chorusPulse ? "g2_yunsheng_ua" : "g2_yunsheng_n1",
                        "nominal", nominal, "effectiveHealing", gained, "overflow", waste);
            }
            lastHp = player.getHealth();
        }

        void pressure(ServerPlayer enemy, String scenario, int tick, float raw) {
            var receipt = damage(enemy, player, raw, DamageCalc.Kind.MAGIC);
            logHit("yunsheng", scenario, label, tick, receipt);
            absorbed += receipt.absorbed();
            var remaining = shields();
            for (String source : SUPPORT_SHIELDS) {
                float consumed = previous.get(source) - remaining.get(source);
                if (consumed > 0) log("yunsheng", scenario, "shield_absorb", tick,
                        "target", label, "source", source, "absorbedShield", consumed);
            }
            previous = remaining;
            lastHp = player.getHealth();
        }

        Map<String, Float> shields() {
            Map<String, Float> values = new LinkedHashMap<>();
            for (String source : SUPPORT_SHIELDS) values.put(source,
                    ModShieldSystem.sourceValue(player, source + "/" + caster.getUUID()));
            return values;
        }

        void summary(String scenario, int tick) {
            log("yunsheng", scenario, "support_summary", tick, "target", label,
                    "newShield", granted, "absorbedShield", absorbed, "effectiveHealing", healing,
                    "overflow", overflow, "echoShield", ward, "sameSourceRefreshWithoutGain", repeated);
        }
    }

    private static DamageReceipt damage(ServerPlayer enemy, ServerPlayer target, float raw, DamageCalc.Kind kind) {
        float before = target.getHealth();
        float shield = ModShieldSystem.value(target);
        DamageCalc.dealDamage(enemy, target, raw, kind);
        return new DamageReceipt(raw, kind.name(), before - target.getHealth(), shield - ModShieldSystem.value(target));
    }

    private void cast(ServerPlayer actor, int node, Vec3 aim, String scenario, int tick) {
        var data = PlayerDataProvider.get(actor);
        var skill = FirstGroupSkills.node(data.getCharacterId(), node);
        if (aim != null) {
            Vec3 delta = aim.subtract(actor.getEyePosition());
            actor.setYRot((float) -Math.toDegrees(Math.atan2(delta.x, delta.z)));
            actor.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
        }
        float before = data.getMana();
        SkillSystem.cast(actor, skill.slot());
        if (skill.needsPoint()) AbilityRuntime.confirm(actor);
        float spent = before - data.getMana();
        h.assertTrue(spent > 0 && data.getCooldown(skill.getId()) > 0,
                "Normal successful cast for " + skill.getId() + " in " + scenario + " tick " + tick);
        log(data.getCharacterId(), scenario, "cast_committed", tick,
                "skill", skill.getId(), "manaSpent", spent, "cooldown", data.getCooldown(skill.getId()));
    }

    private ServerPlayer player(String role, Faction side, Vec3 position) {
        var player = ShuoChecks.player(h, position, new ArrayList<>());
        players.add(player);
        var data = PlayerDataProvider.get(player);
        data.setCharacterId(role);
        data.setLevel(4);
        data.setSkillPoints(4);
        data.setFaction(side);
        seats.put(player.getUUID(), side);
        PlayerDataProvider.applyChosenCharacter(player, data);
        player.getAttribute(ModAttributes.HEALTH_REGEN).setBaseValue(0);
        player.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        player.setNoGravity(false);
        player.setOnGround(true);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private void learn(ServerPlayer player, int node) {
        var data = PlayerDataProvider.get(player);
        var id = FirstGroupSkills.node(data.getCharacterId(), node).getId();
        PlayerDataProvider.upgradeSkill(player, id);
        h.assertTrue(data.skillLevel(id) >= 1, "Legal learned rank for " + id);
    }

    private Vec3 ground(int lane) {
        var root = h.absolutePos(new BlockPos(4, 208 + lane * 10, 4));
        for (int x = -4; x <= 10; x++) for (int z = -3; z <= 6; z++) {
            var p = root.offset(x, -1, z);
            CombatChunks.prepare(h.getLevel(), p);
            floor.put(p, h.getLevel().getBlockState(p));
            h.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        return Vec3.atBottomCenterOf(root);
    }

    private void near(double actual, double expected, String label) {
        h.assertTrue(Math.abs(actual - expected) < .025, label + ": expected " + expected + ", actual " + actual);
    }

    private void cleanup() {
        for (var player : players) {
            AbilityRuntime.clear(player);
            ModShieldSystem.clearFor(player);
            BaseRules.logout(player);
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(player.getUUID());
            h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        floor.forEach(h.getLevel()::setBlockAndUpdate);
        h.getLevel().getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, naturalRegen, h.getLevel().getServer());
        GameManager.restore(savedGame, h.getLevel().getServer());
        TrainingMode.setEnabled(training);
        ModState.setEnabled(mod);
    }

    private static void logHit(String role, String scenario, String target, int tick, DamageReceipt receipt) {
        log(role, scenario, "incoming", tick, "target", target, "kind", receipt.kind(),
                "raw", receipt.raw(), "hpLost", receipt.health(), "absorbedShield", receipt.absorbed());
    }

    private static void log(String role, String scenario, String event, int tick, Object... fields) {
        var row = new LinkedHashMap<String, Object>();
        row.put("role", role);
        row.put("scenario", scenario);
        row.put("version", 1);
        row.put("event", event);
        row.put("tick", tick);
        for (int index = 0; index < fields.length; index += 2)
            row.put((String) fields[index], fields[index + 1]);
        System.out.println("MYRIAD_ROSTER_113 " + JSON.toJson(row));
    }
}
