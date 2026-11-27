package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.MagicBullet;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.PlayerHealthSystem;
import com.myriad.registry.ModAttributes;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Samples successive HP observations after one legal rank-one attack on a stationary enemy hero. */
final class CherryFirstTenDamageChecks {
    private static final List<String> ROLES = List.of("yingxiao", "tianxing", "fenjue", "xuanchen", "sulan",
            "lingyuan", "hanze", "qingluo", "chixiao", "yunsheng");
    private static final List<Integer> NODES = List.of(-1, 2, 3, 4, 5, 6, 7, 0, 1);
    private static final int OBSERVE_TICKS = 142;
    private final GameTestHelper helper;
    private final Map<BlockPos, BlockState> savedBlocks = new HashMap<>();
    private final List<Trial> trials = new ArrayList<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
    private final boolean noMana = TrainingMode.isNoMana(), noCooldown = TrainingMode.isNoCooldown();

    private static final class Trial {
        ServerPlayer caster;
        ServerPlayer target;
        AbilitySkill skill;
        String role;
        int build;
        float lastHealth;
        final List<Float> hits = new ArrayList<>();
        Trial control;
        Vec3 start;
        boolean controlOnly;
    }

    private CherryFirstTenDamageChecks(GameTestHelper helper) { this.helper = helper; }

    static void run(GameTestHelper helper) {
        var test = new CherryFirstTenDamageChecks(helper);
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(true);
            TrainingMode.setNoMana(false);
            TrainingMode.setNoCooldown(false);
            test.start(0);
        } catch (RuntimeException | Error failure) {
            test.cleanup();
            throw failure;
        }
    }

    private void start(int phase) {
        if (phase == NODES.size()) {
            cleanup();
            helper.succeed();
            return;
        }
        int index = NODES.get(phase);
        for (int row = 0; row < ROLES.size(); row++) {
            String role = ROLES.get(row);
            var skill = index < 0 ? null : FirstGroupSkills.node(role, index);
            boolean passive = skill != null && skill.isPassive();
            if (skill != null && skill.damageFormulas().isEmpty()
                    && !List.of("g1_yingxiao_pa", "g1_fenjue_pb").contains(skill.getId())) continue;
            for (int group = 0; group < (passive ? 6 : 3); group++) {
                int build = group % 3;
                var start = helper.absolutePos(new BlockPos(4 + group * 35, 63 + row * 21, 4));
                CombatChunks.prepare(helper.getLevel(), start);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 16; z++) {
                    BlockPos floor = start.offset(x, -1, z);
                    if (!savedBlocks.containsKey(floor)) {
                        savedBlocks.put(floor, helper.getLevel().getBlockState(floor));
                        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                    }
                }
                Vec3 origin = Vec3.atBottomCenterOf(start);
                int level = skill == null ? 1 : scenarioLevel(skill);
                var trial = new Trial();
                trial.start = origin;
                trial.skill = skill;
                trial.controlOnly = group >= 3;
                if (trial.controlOnly) trials.get(trials.size() - 3).control = trial;
                trial.role = role;
                trial.build = build;
                trial.caster = player(role, level, origin, Faction.GUARDIAN, false);
                trial.target = player("tianxing", level, origin.add(0, 0, 2.5), Faction.DESTRUCTION, true);
                trial.lastHealth = trial.target.getHealth();
                var data = PlayerDataProvider.get(trial.caster);
                data.setFirstGroupAttribute("strength", build == 1 ? 1 : 0);
                data.setFirstGroupAttribute("wisdom", build == 2 ? 1 : 0);
                FirstGroupGrowth.apply(trial.caster, data);
                if (skill != null && !trial.controlOnly) {
                    PlayerDataProvider.upgradeSkill(trial.caster, skill.getId());
                    helper.assertTrue(data.skillLevel(skill.getId()) == (skill.isPassive() ? FirstGroupSkills.passiveRank(level) : 1), "Legal first purchase or passive selection for " + skill.getId());
                }
                if (passive) primePassive(trial);
                trials.add(trial);
            }
        }
        observe(phase, 0);
    }

    private ServerPlayer player(String role, int level, Vec3 at, Faction faction, boolean target) {
        var p = (ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
        players.add(p);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
        };
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        var data = PlayerDataProvider.get(p);
        data.setCharacterId(role);
        data.setLevel(level);
        data.setFaction(faction);
        PlayerDataProvider.applyChosenCharacter(p, data);
        PlayerHealthSystem.fill(p);
        data.setMana(700);
        p.getAttribute(ModAttributes.CRIT_CHANCE).removeModifiers();
        p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        p.setNoGravity(true);
        p.setPos(at);
        p.setOnGround(true);
        p.setYRot(0);
        p.setXRot(0);
        if (target) {
            helper.getLevel().addNewPlayer(p);
            helper.assertTrue(p.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE) == 45
                    && p.getAttributeValue(ModAttributes.MAGIC_RESISTANCE) == 45, "Unmodified Tianxing defenses");
        }
        helper.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(), p);
        return p;
    }

    private void action(Trial trial) {
        if (trial.skill == null) {
            basic(trial);
            return;
        }
        cast(trial, trial.skill);
    }

    private void basic(Trial trial) {
        var actor = trial.caster;
        if (MagicBullet.isMage(actor)) {
            PlayerDataProvider.onRightClickItem(actor, actor.getMainHandItem());
        } else {
            try {
                Field ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
                ticker.setAccessible(true);
                ticker.setInt(actor, 200);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
            actor.getRandom().setSeed(689L);
            actor.attack(trial.target);
        }
    }

    private void cast(Trial trial, AbilitySkill skill) {
        var actor = trial.caster;
        actor.setXRot(skill.needsPoint() || skill.getId().equals("g1_tianxing_n1") ? 45 : 0);
        SkillSystem.cast(actor, skill.slot());
        if (PlayerDataProvider.get(actor).getPlacementType() != 0) AbilityRuntime.confirm(actor);
    }

    private static int scenarioLevel(AbilitySkill skill) {
        if (skill.isUltimate() || skill.getId().equals("g1_xuanchen_pb")) return 4;
        return switch (skill.getId()) {
            case "g1_tianxing_pb", "g1_fenjue_pa", "g1_fenjue_pb", "g1_sulan_pb", "g2_lingyuan_pb",
                 "g2_chixiao_pa" -> 2;
            default -> 1;
        };
    }

    private void primePassive(Trial trial) {
        int[] required = switch (trial.skill.getId()) {
            case "g1_tianxing_pb", "g1_sulan_pb", "g2_lingyuan_pb" -> new int[]{3};
            case "g1_fenjue_pa", "g1_fenjue_pb", "g2_chixiao_pa" -> new int[]{2};
            case "g1_xuanchen_pb" -> new int[]{2, 7};
            default -> new int[0];
        };
        for (int index : required) {
            var prerequisite = FirstGroupSkills.node(trial.role, index);
            PlayerDataProvider.upgradeSkill(trial.caster, prerequisite.getId());
            helper.assertTrue(PlayerDataProvider.get(trial.caster).skillLevel(prerequisite.getId()) == 1,
                    "Legal triggering action " + prerequisite.getId());
        }
        if (trial.skill.getId().equals("g1_fenjue_pb"))
            trial.caster.setHealth(trial.caster.getMaxHealth() * .15f);
        if (trial.controlOnly) return;
        var memory = AbilityRuntime.context(trial.caster, trial.skill).memory();
        if (trial.skill.getId().equals("g2_lingyuan_pa")) memory.distance = 2; // Two previously earned swords.
        if (trial.skill.getId().equals("g2_lingyuan_pb")) memory.count = 2; // Two prior successfully completed casts.
    }

    private void passiveAction(Trial trial, int tick) {
        switch (trial.skill.getId()) {
            case "g1_yingxiao_pa", "g1_yingxiao_pb", "g2_lingyuan_pa" -> {
                if (tick == 3) basic(trial);
                if (tick == 26 && trial.role.equals("lingyuan")) trial.target.setPos(trial.start.add(0, 0, 24));
            }
            case "g1_tianxing_pa" -> { if (tick == 3 || tick == 38 || tick == 73) basic(trial); }
            case "g1_tianxing_pb", "g1_sulan_pb", "g2_lingyuan_pb" -> {
                if (tick == 3) cast(trial, FirstGroupSkills.node(trial.role, 3));
                if (tick == 33) basic(trial);
            }
            case "g1_fenjue_pa", "g1_fenjue_pb" -> {
                if (tick == 3) cast(trial, FirstGroupSkills.node(trial.role, 2));
            }
            case "g1_xuanchen_pb" -> {
                if (tick == 3) cast(trial, FirstGroupSkills.node(trial.role, 2));
                if (tick == 38) cast(trial, FirstGroupSkills.node(trial.role, 7));
            }
            case "g1_sulan_pa" -> {
                if (tick >= 3 && tick <= 64) {
                    trial.caster.setPos(trial.start.add((tick % 2 == 0 ? 0 : .5), 0, 0));
                    trial.caster.setOnGround(true);
                }
                if (tick == 68) basic(trial);
            }
            case "g2_hanze_pa", "g2_chixiao_pa" -> {
                if (tick == 3 || tick == 38 || tick == 73) basic(trial);
                if (tick == 108) {
                    if (trial.role.equals("chixiao")) cast(trial, FirstGroupSkills.node(trial.role, 2));
                    else basic(trial);
                }
            }
            default -> throw new IllegalArgumentException("Unspecified passive scenario " + trial.skill.getId());
        }
    }

    private void observe(int phase, int tick) {
        helper.runAfterDelay(1, () -> {
            try {
                for (Trial trial : trials) {
                    if (trial.skill != null && trial.skill.isPassive()) passiveAction(trial, tick);
                    else if (tick == 3) action(trial);
                    if (trial.skill != null && trial.skill.getId().equals("g2_chixiao_ma") && tick == 10)
                        DamageCalc.dealSkillDamage(trial.target, trial.caster, 20, DamageCalc.Kind.PHYSICAL, 0);
                    if (trial.skill != null && trial.skill.getId().equals("g2_chixiao_ub")
                            && (tick == 12 || tick == 47 || tick == 82)) basic(trial);
                    if (AbilityRuntime.moving(trial.caster))
                        trial.caster.move(MoverType.SELF, trial.caster.getDeltaMovement());
                    MagicBullet.tickBullets(trial.caster, PlayerDataProvider.get(trial.caster));
                    AbilityRuntime.tick(trial.caster);
                    float hp = trial.target.getHealth();
                    trial.hits.add(Math.max(0, trial.lastHealth - hp));
                    trial.lastHealth = hp;
                }
                if (tick < OBSERVE_TICKS) {
                    observe(phase, tick + 1);
                    return;
                }
                for (Trial trial : trials) {
                    if (trial.controlOnly) continue;
                    // Controls use the same role, level, seed, build, timing and triggering attacks with the passive unlearned.
                    var hits = new ArrayList<Float>();
                    for (int i = 0; i < trial.hits.size(); i++) {
                        float delta = trial.hits.get(i) - (trial.control == null ? 0 : trial.control.hits.get(i));
                        if (delta > .001f) hits.add(delta);
                    }
                    String id = trial.skill == null ? "AA" : trial.skill.getId();
                    System.out.printf(Locale.ROOT, "[CHERRY-HERO] %s %s %d %s%n", trial.role, id, trial.build,
                            hits.stream().map(value -> String.format(Locale.ROOT, "%.3f", value)).toList());
                    helper.assertTrue(!hits.isEmpty(), "No hero HP loss in the named hit scenario: " + id);
                    if (trial.skill == null) helper.assertTrue(hits.size() == 1, "Exactly one ordinary attack: " + trial.role);
                    if (id.equals("g1_fenjue_n1") || id.equals("g2_chixiao_n1")) {
                        int expectedHits = id.equals("g1_fenjue_n1") ? 2 : 1;
                        double expected = trial.skill.damageFormulas().getFirst()
                                .value(AbilityRuntime.context(trial.caster, trial.skill)) * 100 / 145;
                        helper.assertTrue(hits.size() == expectedHits && hits.stream().allMatch(v -> Math.abs(v - expected) < .04),
                                id + " public cast reaches enemy Tianxing for each advertised stroke: " + hits + ", expected " + expected);
                    }
                }
                clearPlayers();
                start(phase + 1);
            } catch (RuntimeException | Error failure) {
                cleanup();
                throw failure;
            }
        });
    }

    private void clearPlayers() {
        for (var p : players) {
            AbilityRuntime.clear(p);
            helper.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());
            if (helper.getLevel().getPlayerByUUID(p.getUUID()) != null)
                helper.getLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else p.discard();
        }
        trials.clear();
        players.clear();
    }

    private void cleanup() {
        clearPlayers();
        savedBlocks.forEach(helper.getLevel()::setBlockAndUpdate);
        savedBlocks.clear();
        TrainingMode.setNoMana(noMana);
        TrainingMode.setNoCooldown(noCooldown);
        TrainingMode.setEnabled(training);
        ModState.setEnabled(enabled);
    }
}
