package com.myriad.game;

import com.google.gson.Gson;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.MagicBullet;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Server settlement on fixed terrain with separately observed cast identity, HP and control. */
final class BalanceSwordFrostChecks {
    private static final Gson JSON = new Gson();
    private static final Map<ServerPlayer, Target> WATCHED = new LinkedHashMap<>();
    private static boolean listening;
    private record Strike(long tick, String skill, int cast, boolean direct, float amount) {}
    private record Target(String name, ServerPlayer player, float hp, List<Strike> hits) {}

    private final GameTestHelper h;
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
    private final boolean freeMana = TrainingMode.isNoMana(), noCooldown = TrainingMode.isNoCooldown();
    private final long started;
    private boolean done;

    private BalanceSwordFrostChecks(GameTestHelper h) {
        this.h = h;
        started = h.getLevel().getGameTime();
    }

    static void run(GameTestHelper h) {
        new BalanceSwordFrostChecks(h).start();
    }

    private void start() {
        if (!listening) {
            ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, raw) -> {
                if (entity instanceof ServerPlayer player && WATCHED.containsKey(player)) {
                    var target = WATCHED.get(player);
                    long now = player.level().getGameTime();
                    target.hits().add(new Strike(now, AbilityRuntime.sourceSkill(), AbilityRuntime.sourceCast(),
                            AbilityRuntime.sourceDirect(), raw));
                }
                return true;
            });
            listening = true;
        }
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        TrainingMode.setNoMana(false);
        TrainingMode.setNoCooldown(false);
        try {
            Vec3 frostStart = lane(0, 2);
            var frost = actor(CharacterType.HANZE, frostStart, Faction.GUARDIAN);
            var first = enemy("frost_primary", frostStart.add(0, 0, 2.6));
            var second = enemy("frost_secondary", frostStart.add(0, 0, 4.3));
            var ally = actor(CharacterType.LINGYUAN, frostStart.add(2, 0, 1), Faction.GUARDIAN);
            learn(frost, 0, 2, 5, 7);

            Vec3 fieldStart = lane(1, 2);
            var swordField = actor(CharacterType.LINGYUAN, fieldStart, Faction.GUARDIAN);
            var inside = enemy("sword_field_stays", fieldStart.add(0, 0, 4));
            var moves = enemy("sword_field_leaves", fieldStart.add(2, 0, 4));
            learn(swordField, 6);

            Vec3 chopStart = lane(2, 2);
            var swordChop = actor(CharacterType.LINGYUAN, chopStart, Faction.GUARDIAN);
            var chop = enemy("sword_chop", chopStart.add(0, 0, 4));
            learn(swordChop, 7);

            Vec3 prepStart = lane(3, 2);
            var swordPrep = actor(CharacterType.LINGYUAN, prepStart, Faction.GUARDIAN);
            var prepared = enemy("sword_heart_preloaded", prepStart.add(0, 0, 2));
            learn(swordPrep, 0);
            swordHeartState(swordPrep, prepared, 2);

            Vec3 zeroStart = lane(4, 2);
            var swordZero = actor(CharacterType.LINGYUAN, zeroStart, Faction.GUARDIAN);
            var zero = enemy("sword_heart_zero", zeroStart.add(0, 0, 2));
            learn(swordZero, 0);
            swordHeartState(swordZero, zero, 0);

            Vec3 frozenStart = lane(5, 2);
            var frostField = actor(CharacterType.HANZE, frozenStart, Faction.GUARDIAN);
            var frozen = enemy("frozen_expanse_indirect", frozenStart.add(0, 0, 4));
            learn(frostField, 0, 6);

            for (int tick = 1; tick <= 165; tick++) {
                int age = tick;
                h.runAfterDelay(age, () -> {
                    if (done) return;
                    try {
                        PlayerDataProvider.get(first.player()).tickBuffs();
                        PlayerDataProvider.get(second.player()).tickBuffs();
                        if (age == 1) {
                            ActionTime.apply(frost.getUUID(), frost.getUUID(), "sword_frost_interleave", .5,
                                    h.getLevel().getGameTime() + 28);
                            cast(frost, 2);
                            swordField.setXRot((float) Math.toDegrees(Math.atan2(swordField.getEyeHeight(), 4)));
                            cast(swordField, 6);
                            AbilityRuntime.confirm(swordField);
                            near(PlayerDataProvider.get(swordField).getMana(), 520, "Ground confirmation spends R-A mana");
                            frostField.setXRot((float) Math.toDegrees(Math.atan2(frostField.getEyeHeight(), 4)));
                            cast(frostField, 6);
                            AbilityRuntime.confirm(frostField);
                            cast(swordChop, 7);
                            swordChop.setYRot(90);
                            near(PlayerDataProvider.get(swordChop).getMana(), 530, "R-B pays at start of charge");
                        }
                        if (age == 7) cast(frost, 5);
                        if (age == 27) moves.player().setPos(fieldStart.add(12, 0, 4));
                        if (age == 30) {
                            cast(frost, 7);
                            near(ModShieldSystem.value(frost), 251, "Winter Bastion shields caster");
                            near(ModShieldSystem.value(ally), 251, "Winter Bastion shields nearby visible ally separately");
                            trace("winter_bastion", "shield", age, Map.of("selfHp", ModShieldSystem.value(frost),
                                    "allyHp", ModShieldSystem.value(ally)));
                        }
                        if (age == 35 || age == 65 || age == 95 || age == 125 || age == 157) basic(frost);
                        if (age == 92) first.player().setPos(frostStart.add(-4, 0, 2.6));
                        if (age == 153) {
                            first.player().setPos(frostStart.add(0, 0, 2.6));
                            second.player().setPos(frostStart.add(4, 0, 4.3));
                        }
                        for (var player : List.of(first.player(), second.player(), inside.player(), moves.player(), chop.player(),
                                prepared.player(), zero.player(), frozen.player())) player.damageCooldownTime = 0;
                        for (var player : List.of(frost, swordField, swordChop, swordPrep, swordZero, frostField))
                            AbilityRuntime.tick(player);
                        MagicBullet.tickBullets(frost, PlayerDataProvider.get(frost));
                        if (age == 29) {
                            long shots = count(first, "g2_hanze_n1");
                            h.assertTrue(shots == 3, "All three ice shards must actually land with the lance interleaved: " + first.hits());
                            h.assertTrue(first.hits().stream().filter(hit -> hit.skill().equals("g2_hanze_n1"))
                                    .map(Strike::cast).distinct().count() == 1, "The three shards share one cast identity");
                        }
                        if (age == 30) {
                            logTarget(first, age);
                            logTarget(second, age);
                            h.assertTrue(count(first, "g2_hanze_pa") == 0, "Interleaved lance cannot make the same Q count twice");
                            h.assertTrue(count(chop, "g2_lingyuan_ub") == 0, "Chop has no hit during the first 29 ticks");
                        }
                        if (age == 39 || age == 68 || age == 130 || age == 162) {
                            logTarget(first, age);
                            logTarget(second, age);
                        }
                        if (age == 39) {
                            h.assertTrue(count(first, "g2_hanze_pa") == 1, "Q, lance, bastion and native basic give four effective stacks");
                            h.assertTrue(PlayerDataProvider.get(first.player()).getBuff("g1_root") > 11,
                                    "The fourth direct hit extends control beyond the earlier Bastion root");
                        }
                        if (age == 68) {
                            h.assertTrue(frostState(frost, first).count == 0, "The effective basic inside the 120-tick window adds zero stacks");
                            trace("frost_primary", "immunity", age, Map.of("stacks", frostState(frost, first).count,
                                    "untilTick", frostState(frost, first).cooldown - started));
                        }
                        if (age == 130) {
                            h.assertTrue(count(first, "g2_hanze_pa") == 1 && count(second, "g2_hanze_pa") == 1,
                                    "The second enemy can trigger while the first is inside its own immunity");
                        }
                        if (age == 162) {
                            h.assertTrue(frostState(frost, first).count == 1, "The first direct hit after the 120-tick window adds a new stack");
                            trace("frost_primary", "immunity_recovered", age, Map.of("stacks", frostState(frost, first).count,
                                    "untilTick", frostState(frost, first).cooldown - started));
                        }
                        if (age == 165) {
                            for (var target : List.of(inside, moves, chop, prepared, zero, frozen)) logTarget(target, age);
                            near(frozen.hp() - frozen.player().getHealth(), 309.6, "Six actual ice field pulses");
                            h.assertTrue(frozen.hits().stream().noneMatch(Strike::direct) && frostState(frostField, frozen).count == 0,
                                    "Ice field pulses cannot enter the direct-hit frost ledger");
                            near(inside.hp() - inside.player().getHealth(), 375, "Six actual sword rain pulses on the staying target");
                            near(moves.hp() - moves.player().getHealth(), 62.5, "Leaving sword rain skips later pulses");
                            near(chop.hp() - chop.player().getHealth(), 336, "Paid chop strikes the starting direction once");
                            h.assertTrue(chop.hits().getFirst().tick() >= started + 30, "Chop lands after charge and follow-through");
                            h.assertTrue(count(prepared, "g2_lingyuan_pa") > 0 && count(zero, "g2_lingyuan_pa") == 0,
                                    "Two preloaded blades produce damage while zero blades cannot");
                            h.assertTrue(count(first, "g2_hanze_pa") == 1, "Immunity and new stacks never create an extra proc");
                            finish();
                            h.succeed();
                        }
                    } catch (RuntimeException | Error failure) {
                        finish();
                        throw failure;
                    }
                });
            }
        } catch (RuntimeException | Error failure) {
            finish();
            throw failure;
        }
    }

    private void learn(ServerPlayer player, int... indices) {
        for (int i : indices) {
            PlayerDataProvider.get(player).setSkillLevel(FirstGroupSkills.node(
                    PlayerDataProvider.get(player).getCharacterId(), i).getId(), 1);
        }
    }

    private static void cast(ServerPlayer player, int index) {
        var skill = FirstGroupSkills.node(PlayerDataProvider.get(player).getCharacterId(), index);
        SkillSystem.cast(player, skill.slot());
    }

    private static void basic(ServerPlayer player) {
        MagicBullet.tryFire(player, player.getMainHandItem());
    }

    private void swordHeartState(ServerPlayer actor, Target target, int blades) {
        var passive = FirstGroupSkills.node("lingyuan", 0);
        var memory = AbilityRuntime.context(actor, passive).memory();
        memory.distance = blades;
        memory.target = target.player().getUUID();
        trace(target.name(), "setup", 0, Map.of("preloadedBlades", blades));
    }

    private Vec3 lane(int index, int border) {
        var base = h.absolutePos(new BlockPos(4 + 29 * index, 120, 4));
        for (int x : new int[]{0, 12}) CombatChunks.prepare(h.getLevel(), base.offset(x, 0, 3));
        for (int x = -border; x <= border; x++) {
            for (int z = -2; z <= 8; z++) {
                var block = base.offset(x, -1, z);
                blocks.putIfAbsent(block, h.getLevel().getBlockState(block));
                h.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            }
        }
        return Vec3.atBottomCenterOf(base);
    }

    private ServerPlayer actor(CharacterType role, Vec3 position, Faction faction) {
        var player = ShuoChecks.player(h, position, new ArrayList<>());
        var data = PlayerDataProvider.get(player);
        data.setCharacterId(role.getId());
        data.setLevel(1);
        data.setFaction(faction);
        PlayerDataProvider.applyChosenCharacter(player, data);
        player.setPos(position);
        player.setOnGround(true);
        player.setYRot(0);
        player.setXRot(0);
        player.setHealth(player.getMaxHealth());
        player.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        data.setMana(700);
        h.getLevel().addNewPlayer(player);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        players.add(player);
        return player;
    }

    private Target enemy(String name, Vec3 position) {
        var player = actor(CharacterType.CHIXIAO, position, Faction.DESTRUCTION);
        player.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);
        player.setHealth(10000);
        var target = new Target(name, player, player.getHealth(), new ArrayList<>());
        WATCHED.put(player, target);
        return target;
    }

    private static AbilityRuntime.Memory frostState(ServerPlayer caster, Target target) {
        return AbilityRuntime.context(caster, FirstGroupSkills.node("hanze", 0)).memory(target.player().getUUID().toString());
    }

    private static long count(Target target, String skill) {
        return target.hits().stream().filter(hit -> hit.skill().equals(skill)).count();
    }

    private void logTarget(Target target, int age) {
        var root = PlayerDataProvider.get(target.player()).getBuff("g1_root");
        trace(target.name(), "target", age, Map.of("hpLoss", target.hp() - target.player().getHealth(),
                "rootTicks", root, "hits", target.hits().stream().map(hit -> new Strike(hit.tick() - started, hit.skill(),
                        hit.cast(), hit.direct(), hit.amount())).toList()));
    }

    private void near(double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) <= .05, message + " " + actual + " vs " + expected);
    }

    private static void trace(String scene, String event, int tick, Map<String, ?> values) {
        var data = new LinkedHashMap<String, Object>();
        data.put("scenario", scene);
        data.put("event", event);
        data.put("tick", tick);
        data.putAll(values);
        System.out.println("MYRIAD_SWORD_FROST " + JSON.toJson(data));
    }

    private void finish() {
        done = true;
        for (var player : players) {
            WATCHED.remove(player);
            AbilityRuntime.clear(player);
            ModShieldSystem.clearFor(player);
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(player.getUUID());
            h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        blocks.forEach(h.getLevel()::setBlockAndUpdate);
        ModState.setEnabled(enabled);
        TrainingMode.setEnabled(training);
        TrainingMode.setNoMana(freeMana);
        TrainingMode.setNoCooldown(noCooldown);
    }
}
