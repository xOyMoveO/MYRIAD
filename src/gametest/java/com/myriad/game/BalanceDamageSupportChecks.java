package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;

/** Cross-checks selected amounts from cast input to actual HP loss, shield absorption and healing. */
final class BalanceDamageSupportChecks {
    private static final double TOLERANCE = .04;
    private static final float TARGET_HP = 1000;
    private record Build(String name, String purchase, boolean azure) {}
    private record Sweep(Build build, ServerPlayer caster, Pig target, double attack, double expectedTotal) {}

    private static final Build ZERO = new Build("zero", "", false);
    private static final Build STRENGTH = new Build("strength", "strength", false);
    private static final Build AZURE = new Build("azure", "", true);
    private static final Build STRENGTH_AZURE = new Build("strength_azure", "strength", true);
    private static final Build DEFENSE = new Build("defense", "defense", false);
    private static final Build WHIFF = new Build("whiff", "", false);
    private static final Build WISDOM = new Build("wisdom", "wisdom", false);
    private static final Build OVERFLOW = new Build("overflow", "wisdom", false);
    private static final Build WISDOM_BUFF = new Build("wisdom_buff", "wisdom", false);

    private BalanceDamageSupportChecks() {}

    static void run(GameTestHelper h) {
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        var sweeps = new ArrayList<Sweep>();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        Runnable cleanup = () -> {
            for (var sweep : sweeps) {
                remove(h, sweep.caster());
                sweep.target().discard();
            }
            sweeps.clear();
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        };
        try {
            shield(h, 1, ZERO, 62, 57.5);
            shield(h, 1, STRENGTH, 69.5, 62.5);
            shield(h, 1, AZURE, 66.875, 60.75);
            shield(h, 1, DEFENSE, 62, 57.5);
            shield(h, 1, WHIFF, 0, 0);
            shield(h, 20, ZERO, 214, 57.5 + 304.0 / 3);
            shield(h, 20, STRENGTH_AZURE, 250.3, 57.5 + 376.6 / 3);
            heal(h, 1, ZERO, 85, 85);
            heal(h, 1, STRENGTH, 85, 85);
            heal(h, 1, WISDOM, 95, 95);
            heal(h, 1, OVERFLOW, 95, 12);
            heal(h, 20, ZERO, 227.5, 227.5);
            heal(h, 20, WISDOM_BUFF, 245.5, 245.5);
            prepareSweep(h, sweeps, ZERO, 65, 104);
            prepareSweep(h, sweeps, STRENGTH, 80, 111.5);
            prepareSweep(h, sweeps, AZURE, 74.75, 108.875);
            prepareSweep(h, sweeps, STRENGTH_AZURE, 92, 117.5);
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        var firstAmounts = new double[sweeps.size()];
        for (int tick = 1; tick <= 8; tick++) {
            int elapsed = tick;
            h.runAfterDelay(tick, () -> {
                if (sweeps.isEmpty()) return;
                try {
                    for (int index = 0; index < sweeps.size(); index++) {
                        var sweep = sweeps.get(index);
                        AbilityRuntime.tick(sweep.caster());
                        double loss = TARGET_HP - sweep.target().getHealth();
                        if (elapsed == 4) {
                            near(h, loss, sweep.expectedTotal() / 2, "First Flame Slash stroke " + sweep.build().name());
                            firstAmounts[index] = loss;
                            if (sweep.build() == STRENGTH_AZURE) {
                                var caster = sweep.caster();
                                PlayerDataProvider.get(caster).setAzureHornUntil(0);
                                PlayerDataProvider.onPlayerTick(caster);
                                near(h, caster.getAttributeValue(ModAttributes.PHYSICAL_ATTACK), 80, "Expired Azure Horn modifier");
                            }
                        }
                        if (elapsed == 8) {
                            near(h, loss, sweep.expectedTotal(), "Two Flame Slash strokes " + sweep.build().name());
                            System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=bonus_physical_damage build=%s effectiveAttack=%.4f bonusAttack=%.4f first=%.4f total=%.4f%n",
                                    sweep.build().name(), sweep.attack(), sweep.attack() - 65, firstAmounts[index], loss);
                        }
                    }
                    if (elapsed == 8) {
                        cleanup.run();
                        h.succeed();
                    }
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
        }
    }

    private static void prepareSweep(GameTestHelper h, List<Sweep> sweeps, Build build, double attack, double total) {
        var start = position(h, sweeps.size() + 1);
        var caster = actor(h, CharacterType.FENJUE, start);
        var target = target(h, start.add(0, 1, 5));
        sweeps.add(new Sweep(build, caster, target, attack, total));
        apply(h, caster, build);
        double effective = caster.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        near(h, effective, attack, "Effective physical attack after applying " + build.name());
        var skill = FirstGroupSkills.node("fenjue", 2);
        var formula = skill.damageFormulas().getFirst();
        double bonus = FirstGroupGrowth.bonusPhysicalAttack(CharacterType.FENJUE, effective);
        double preview = formula.value(1, effective, 30, bonus, 0);
        near(h, preview * formula.maxHits(), total, "Flame Slash formula card " + build.name());
        cast(caster, skill);
    }

    private static void shield(GameTestHelper h, int level, Build build, double damage, double shield) {
        var start = position(h, 0);
        var caster = actor(h, CharacterType.SHUO, start);
        // Hero attacks reach the player damage path even in the GameTest world's difficulty.
        var target = actor(h, CharacterType.CHIXIAO, start.add(0, 0, 2));
        PlayerDataProvider.get(target).setFaction(Faction.DESTRUCTION);
        target.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);
        h.getLevel().addNewPlayer(target);
        try {
            if (level != 1) TrainingActions.apply(caster, "level:" + level, true);
            apply(h, caster, build);
            var skill = FirstGroupSkills.node("shuo", 3);
            var data = PlayerDataProvider.get(caster);
            float health = caster.getHealth();
            float enemyHealth = target.getHealth();
            cast(caster, skill);
            h.assertTrue(data.getCooldown(skill.getId()) > 0, "Guard cast commits before the incoming hit");
            near(h, ModShieldSystem.value(caster), 0, "Starting the guard grants no shield");
            hit(target, caster, 100, DamageCalc.Kind.PHYSICAL);
            h.assertTrue(data.getBuff("shuo_qiaofan_recast") > 0, "Frontal incoming hit opens the counter");
            near(h, caster.getHealth(), health, "Successful guard cancels the incoming damage");
            near(h, ModShieldSystem.value(caster), 0, "Parrying alone grants no shield");
            if (build == WHIFF) target.setPos(start.add(0, 0, -2));
            SkillSystem.cast(caster, skill.slot());
            double taken = enemyHealth - target.getHealth();
            double granted = ModShieldSystem.value(caster);
            near(h, taken, damage, "Sheath Reversal counter damage " + build.name());
            near(h, granted, shield, "Sheath Reversal actual shield " + build.name());
            double bonus = FirstGroupGrowth.bonusPhysicalAttack(CharacterType.SHUO,
                    caster.getAttributeValue(ModAttributes.PHYSICAL_ATTACK));
            if (build != WHIFF) {
                var formula = skill.supportAmounts().getFirst();
                near(h, formula.value(1, 30, bonus), granted, "Sheath Reversal amount on the formula card");
            }
            System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=shuo_qiaofan_shield build=%s bonusAttack=%.4f shield=%.4f riposte=%.4f%n",
                    buildLabel(build, level), bonus, granted, taken);
            if (level == 1 && (build == ZERO || build == DEFENSE)) shieldSequence(h, caster, target, build);
        } finally {
            remove(h, caster);
            remove(h, target);
        }
    }

    private static void shieldSequence(GameTestHelper h, ServerPlayer caster, ServerPlayer attacker, Build build) {
        double defense = build == DEFENSE ? 50 : 45;
        double expectedEach = 4000 / (100 + defense);
        double expectedTrue = 57.5 - expectedEach * 2;
        double health = caster.getHealth();
        double before = ModShieldSystem.value(caster);
        hit(attacker, caster, 40, DamageCalc.Kind.PHYSICAL);
        double physical = before - ModShieldSystem.value(caster);
        hit(attacker, caster, 40, DamageCalc.Kind.MAGIC);
        double magic = before - physical - ModShieldSystem.value(caster);
        near(h, caster.getHealth(), health, "Shield absorbs both resisted hits");
        double trueAbsorbed = ModShieldSystem.value(caster);
        hit(attacker, caster, 30, DamageCalc.Kind.TRUE);
        double lost = health - caster.getHealth();
        near(h, physical, expectedEach, "Resisted physical damage absorbed");
        near(h, magic, expectedEach, "Resisted magic damage absorbed");
        near(h, trueAbsorbed, expectedTrue, "Remaining shield available to absorb true damage");
        near(h, ModShieldSystem.value(caster), 0, "True damage consumes the remaining shield");
        near(h, lost, 30 - expectedTrue, "Actual HP lost after exhausting the shield");
        System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=shuo_shield_damage_sequence build=%s physicalAbsorbed=%.4f magicAbsorbed=%.4f trueAbsorbed=%.4f healthLost=%.4f%n",
                build.name(), physical, magic, trueAbsorbed, lost);
    }

    private static void heal(GameTestHelper h, int level, Build build, double amount, double effective) {
        var caster = actor(h, CharacterType.QINGLUO, position(h, 0));
        try {
            if (level != 1) TrainingActions.apply(caster, "level:" + level, true);
            apply(h, caster, build);
            if (build == WISDOM_BUFF) {
                caster.getAttribute(ModAttributes.SPELL_POWER).addTransientModifier(new AttributeModifier(
                        Identifier.parse("myriad:test_growth_spell_buff"), 16, AttributeModifier.Operation.ADD_VALUE));
            }
            var skill = FirstGroupSkills.node("qingluo", 2);
            var formula = skill.supportAmounts().getFirst();
            double spellPower = caster.getAttributeValue(ModAttributes.SPELL_POWER);
            double bonus = FirstGroupGrowth.bonusPhysicalAttack(CharacterType.QINGLUO,
                    caster.getAttributeValue(ModAttributes.PHYSICAL_ATTACK));
            double preview = formula.value(1, spellPower, bonus);
            near(h, preview, amount, "Verdant Dew amount on the formula card " + build.name());
            caster.setHealth(build == OVERFLOW ? caster.getMaxHealth() - 12 : 100);
            double health = caster.getHealth();
            cast(caster, skill);
            double restored = caster.getHealth() - health;
            near(h, restored, effective, "Verdant Dew actual HP restoration " + build.name());
            System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=qingluo_verdant_dew build=%s spellPower=%.4f amount=%.4f effective=%.4f%n",
                    buildLabel(build, level), spellPower, preview, restored);
        } finally {
            remove(h, caster);
        }
    }

    private static String buildLabel(Build build, int level) {
        return level == 1 ? build.name() : "lv" + level + "_" + build.name();
    }

    private static void apply(GameTestHelper h, ServerPlayer caster, Build build) {
        var data = PlayerDataProvider.get(caster);
        if (!build.purchase().isEmpty()) {
            data.setGold(360);
            h.assertTrue(FirstGroupGrowth.buy(caster, data, build.purchase()), "Purchase " + build.purchase() + " through the shop");
        }
        if (build.azure()) {
            data.setAzureHornUntil(caster.level().getGameTime() + 1800);
            PlayerDataProvider.onPlayerTick(caster);
        }
    }

    private static void cast(ServerPlayer caster, AbilitySkill skill) {
        var data = PlayerDataProvider.get(caster);
        data.setSkillLevel(skill.getId(), 1);
        data.setMana(700);
        SkillSystem.cast(caster, skill.slot());
    }

    private static void hit(ServerPlayer attacker, ServerPlayer target, float amount, DamageCalc.Kind kind) {
        target.damageCooldownTime = 0;
        DamageCalc.dealDamage(attacker, target, amount, kind);
    }

    private static Vec3 position(GameTestHelper h, int index) {
        return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3 + 12 * index, 120, 3)));
    }

    private static ServerPlayer actor(GameTestHelper h, CharacterType role, Vec3 position) {
        CombatChunks.prepare(h.getLevel(), BlockPos.containing(position));
        var caster = ShuoChecks.player(h, position, new ArrayList<>());
        var data = PlayerDataProvider.get(caster);
        data.setCharacterId(role.getId());
        data.setLevel(1);
        data.setFaction(Faction.GUARDIAN);
        FirstGroupGrowth.initialize(caster, data);
        caster.setHealth(caster.getMaxHealth());
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(caster.getUUID(), caster);
        return caster;
    }

    private static Pig target(GameTestHelper h, Vec3 position) {
        var target = ShuoChecks.target(h, position);
        target.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);
        target.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);
        return target;
    }

    private static void remove(GameTestHelper h, ServerPlayer caster) {
        AbilityRuntime.clear(caster);
        ModShieldSystem.clearFor(caster);
        BaseRules.logout(caster);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(caster.getUUID());
        h.getLevel().removePlayerImmediately(caster, Entity.RemovalReason.DISCARDED);
    }

    private static void near(GameTestHelper h, double actual, double expected, String label) {
        h.assertTrue(Math.abs(actual - expected) < TOLERANCE, label + " expected " + expected + " but recorded " + actual);
    }
}
