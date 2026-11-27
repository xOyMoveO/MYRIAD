package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.MagicBullet;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Measures server HP loss through the regular attack and cast entry points. */
final class DamageGrowthChecks {
    private static final float HEALTH = 1000;
    private static final double PHYSICAL_FACTOR = .5; // 100 physical defense
    private static final double MAGIC_FACTOR = .8; // 25 magic resistance
    private static final int FINISH_TICK = 34;
    private static final Identifier BUFF = Identifier.fromNamespaceAndPath("myriad", "damage_growth_test_buff");
    private static final List<CharacterType> MAGES = List.of(CharacterType.XUANCHEN, CharacterType.HANZE,
            CharacterType.QINGLUO, CharacterType.YUNSHENG);
    private static final Build ZERO = new Build(0, 0);
    private static final List<Build> BUILDS = List.of(ZERO, new Build(1, 0), new Build(0, 1), new Build(17, 23));

    private record Build(int strength, int wisdom) {}
    private enum Attack { MAGE, MELEE, YIKE, FULL_DRAW, ARC, CRANE, FEATHER_BURST, RESONANCE, PERCENT, SWORD_HEART, FLAME_SLASH, MOUNTAIN_CLEAVE }
    private record Trial(Attack attack, ServerPlayer actor, Pig target, double expected, boolean respecAfterLaunch) {}

    private DamageGrowthChecks() {}

    static void run(GameTestHelper helper) {
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        var players = helper.getLevel().getServer().getPlayerList().getPlayersByUUID();
        var trials = new ArrayList<Trial>();
        Runnable cleanup = () -> {
            for (Trial trial : trials) {
                AbilityRuntime.clear(trial.actor());
                players.remove(trial.actor().getUUID());
                trial.actor().discard();
                trial.target().discard();
            }
            TrainingMode.setEnabled(training);
            ModState.setEnabled(enabled);
        };
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        try {
            FirstTenDamageFormulaChecks.verify(helper);
            LastFiveDamageFormulaChecks.run(helper);
            var healer = player(helper);
            try { SupportGrowthChecks.verify(helper, healer); }
            finally { AbilityRuntime.clear(healer); healer.discard(); }
            for (CharacterType mage : MAGES) {
                double baseline = mage == CharacterType.HANZE ? 78 : 65;
                double ratio = mage == CharacterType.HANZE ? .60 : .50;
                for (Build build : BUILDS) {
                    add(helper, trials, mage, Attack.MAGE, build, (baseline + 20 * ratio * build.wisdom()) * MAGIC_FACTOR, false);
                }
                var physicalBuff = add(helper, trials, mage, Attack.MAGE, ZERO, baseline * MAGIC_FACTOR, false);
                buff(physicalBuff.actor(), ModAttributes.PHYSICAL_ATTACK, 50);
                var spellBuff = add(helper, trials, mage, Attack.MAGE, ZERO, (baseline + 16 * ratio) * MAGIC_FACTOR, true);
                buff(spellBuff.actor(), ModAttributes.SPELL_POWER, 16);
            }
            for (Build build : BUILDS) {
                double attackPower = 65 + 15.0 * build.strength();
                // Keheng's fully charged vanilla melee uses the roster's exact basic attack roll.
                add(helper, trials, CharacterType.KEHENG, Attack.MELEE, build, attackPower * PHYSICAL_FACTOR, false);
                add(helper, trials, CharacterType.YIKE, Attack.YIKE, build, attackPower * PHYSICAL_FACTOR, true);
                add(helper, trials, CharacterType.LUOXIA, Attack.FULL_DRAW, build,
                        (100 + 15.0 * build.strength()) * 1.21 * PHYSICAL_FACTOR, true);
                add(helper, trials, CharacterType.TIANXING, Attack.ARC, build, (79.5 + 7.5 * build.strength()) * PHYSICAL_FACTOR, false);
                add(helper, trials, CharacterType.YUNSHENG, Attack.CRANE, build, (68.5 + 9 * build.wisdom()) * MAGIC_FACTOR, true);
                add(helper, trials, CharacterType.LUOXIA, Attack.FEATHER_BURST, build, (125 + 7.5 * build.strength()) * PHYSICAL_FACTOR, true);
                add(helper, trials, CharacterType.TIANXING, Attack.RESONANCE, build, (44 + 6 * build.wisdom()) * MAGIC_FACTOR, false);
                add(helper, trials, CharacterType.TIANXING, Attack.PERCENT, build, 40 * MAGIC_FACTOR, false);
            }
            for (Build build : BUILDS) {
                add(helper, trials, CharacterType.FENJUE, Attack.FLAME_SLASH, build,
                        (104 + 7.5 * build.strength()) * PHYSICAL_FACTOR, true);
                add(helper, trials, CharacterType.CHIXIAO, Attack.MOUNTAIN_CLEAVE, build,
                        (98 + 7.5 * build.strength()) * PHYSICAL_FACTOR, false);
            }
            add(helper, trials, CharacterType.LINGYUAN, Attack.SWORD_HEART, ZERO, 21.76, false);
            add(helper, trials, CharacterType.LINGYUAN, Attack.SWORD_HEART, new Build(0, 1), 25.60, false);
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        boolean[] completed = {false};
        for (int tick = 1; tick <= FINISH_TICK; tick++) {
            int elapsed = tick;
            helper.runAfterDelay(tick, () -> {
                if (completed[0]) return;
                try {
                    for (Trial trial : trials) advance(helper, trial, elapsed);
                    if (elapsed == FINISH_TICK) {
                        completed[0] = true;
                        cleanup.run();
                        System.out.println("[MYRIAD-DAMAGE-GROWTH] PASS: " + trials.size() + " server HP loss cases");
                        helper.succeed();
                    }
                } catch (RuntimeException | Error failure) {
                    completed[0] = true;
                    cleanup.run();
                    throw failure;
                }
            });
        }
    }

    private static Trial add(GameTestHelper h, List<Trial> trials, CharacterType role, Attack attack,
                             Build build, double expected, boolean respecAfterLaunch) {
        int index = trials.size();
        // Separate all concurrent casts without relying on temporary terrain or real world files.
        var local = new BlockPos(3 + 9 * (index % 3), 96 + 8 * (index / 3), 3);
        var start = h.absolutePos(local);
        CombatChunks.prepare(h.getLevel(), start);
        var actor = player(h);
        PlayerDataProvider.chooseCharacter(actor, role);
        var data = PlayerDataProvider.get(actor);
        applyBuild(actor, build);
        actor.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        actor.setPos(Vec3.atBottomCenterOf(start));
        actor.setOnGround(true);
        boolean melee = attack == Attack.MELEE || attack == Attack.RESONANCE || attack == Attack.SWORD_HEART;
        var pig = h.spawn(EntityTypes.PIG, local.offset(0, melee ? 0 : 1, melee ? 2 : 5));
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.noPhysics = true;
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        pig.setHealth(HEALTH);
        pig.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(100);
        pig.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(25);
        var trial = new Trial(attack, actor, pig, expected, respecAfterLaunch);
        trials.add(trial);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(actor.getUUID(), actor);
        h.assertTrue(role.getId().equals(data.getCharacterId()) && actor.hasLineOfSight(pig),
                "Fixture has the chosen role and a visible target: " + attack);
        return trial;
    }

    private static void advance(GameTestHelper h, Trial trial, int tick) {
        var actor = trial.actor();
        if (tick == 1) {
            switch (trial.attack()) {
                case MAGE -> PlayerDataProvider.onRightClickItem(actor, actor.getMainHandItem());
                case MELEE -> swing(actor, trial.target());
                case YIKE -> BasicAttackSystem.tryAttack(actor, 0);
                case FULL_DRAW -> {
                    learn(actor, 1);
                    BasicAttackSystem.tryAttack(actor, LuoxiaBow.PRESS);
                }
                case ARC -> cast(actor, 3);
                case FLAME_SLASH, MOUNTAIN_CLEAVE -> cast(actor, 2);
                case CRANE -> cast(actor, 2);
                case FEATHER_BURST -> cast(actor, 4);
                case RESONANCE -> verifyResonance(h, trial);
                case PERCENT -> DamageCalc.dealPercentMaxHealthDamage(actor, trial.target(), .04f, DamageCalc.Kind.MAGIC, 0);
                case SWORD_HEART -> primeSwordHeart(h, trial);
            }
        }
        if (tick == 21 && trial.attack() == Attack.FULL_DRAW) BasicAttackSystem.tryAttack(actor, LuoxiaBow.RELEASE);
        if (trial.respecAfterLaunch() && tick == (trial.attack() == Attack.FULL_DRAW ? 21 : 1)) {
            h.assertTrue(trial.target().getHealth() == HEALTH, "Damage is still in flight: " + trial.attack());
            applyBuild(actor, new Build(25, 29));
            buff(actor, ModAttributes.SPELL_POWER, 60);
        }
        if (trial.attack() == Attack.MAGE) {
            var data = PlayerDataProvider.get(actor);
            MagicBullet.tickBullets(actor, data);
            MagicBullet.tickBullets(actor, data);
            if (tick == 2) h.assertTrue(amount(trial.target()) == 0, "Repeated callbacks in one tick cannot advance the same flight twice");
        }
        AbilityRuntime.tick(actor);
        if (trial.attack() == Attack.FLAME_SLASH && tick >= 3 && tick <= 9) {
            // Slash work begins on the cast tick. The half-damage plateau proves the separate strokes.
            int age = tick - 1;
            double expected = age < 3 ? 0 : age < 7 ? trial.expected() / 2 : trial.expected();
            near(h, amount(trial.target()), expected, .04, "Flame Slash elapsed " + age);
        }
        if (trial.attack() == Attack.SWORD_HEART) {
            if (tick == 1) {
                near(h, amount(trial.target()), 0, .02, "Passive schedules swords before either strike lands");
                applyBuild(actor, new Build(25, 29));
            }
            if (tick == 2 || tick == 3) {
                near(h, amount(trial.target()), trial.expected() / 2, .02, "First sword uses the volley spell-power snapshot");
            }
            if (tick == 4) {
                near(h, amount(trial.target()), trial.expected(), .02, "Second sword uses the same snapshot three action ticks after scheduling");
            }
        }
        if (tick == FINISH_TICK && trial.attack() != Attack.RESONANCE) {
            near(h, amount(trial.target()), trial.expected(), .035,
                    PlayerDataProvider.get(actor).getCharacterId() + " " + trial.attack());
        }
    }

    private static void primeSwordHeart(GameTestHelper h, Trial trial) {
        var actor = trial.actor();
        var target = trial.target();
        var cast = AbilityRuntime.context(actor, learn(actor, 0));
        cast.memory().distance = 2;
        swing(actor, target);
        h.assertTrue(target.getUUID().equals(cast.memory().target), "An effective normal attack selects the flying swords' target");
        target.setHealth(HEALTH);
        target.damageCooldownTime = 0;
    }

    private static void verifyResonance(GameTestHelper h, Trial trial) {
        var actor = trial.actor();
        var victim = trial.target();
        swing(actor, victim);
        double ordinary = amount(victim);
        double expectedPhysical = actor.getAttributeValue(ModAttributes.PHYSICAL_ATTACK) * PHYSICAL_FACTOR;
        near(h, ordinary, expectedPhysical, expectedPhysical * .05 + .02, "Ordinary Tianxing physical hit");
        victim.setHealth(HEALTH);
        victim.damageCooldownTime = 0;
        var passive = learn(actor, 1);
        var primingDummy = player(h);
        try {
            primingDummy.setPos(actor.position().add(0, 0, -2));
            var cast = AbilityRuntime.context(actor, FirstGroupSkills.node("tianxing", 2));
            h.assertTrue(cast.damage(primingDummy, 10, DamageCalc.Kind.MAGIC)
                    && PlayerDataProvider.get(actor).getBuff(passive.getId()) > 0, "Actual hero hit readies Thunder Resonance");
        } finally {
            primingDummy.discard();
        }
        swing(actor, victim);
        near(h, amount(victim) - ordinary, trial.expected(), .04, "Resonance adds one independently mitigated magic segment");
        h.assertTrue(PlayerDataProvider.get(actor).getBuff(passive.getId()) == 0, "The empowered basic consumes its proc");
    }

    static void swing(ServerPlayer player, LivingEntity target) {
        try {
            Field ticker = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            ticker.setAccessible(true);
            ticker.setInt(player, 200);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        player.fallDistance = 0;
        player.getRandom().setSeed(689L);
        player.attack(target);
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        player.setYRot(0);
        player.setXRot(0);
        player.setNoGravity(true);
        return player;
    }

    private static void applyBuild(ServerPlayer player, Build build) {
        var data = PlayerDataProvider.get(player);
        data.setFirstGroupAttribute("strength", build.strength());
        data.setFirstGroupAttribute("wisdom", build.wisdom());
        FirstGroupGrowth.apply(player, data);
    }

    private static void buff(ServerPlayer player, Holder<Attribute> attribute, double amount) {
        player.getAttribute(attribute).addOrUpdateTransientModifier(new AttributeModifier(BUFF, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static AbilitySkill learn(ServerPlayer player, int index) {
        var data = PlayerDataProvider.get(player);
        var skill = FirstGroupSkills.node(data.getCharacterId(), index);
        data.setSkillLevel(skill.getId(), 1);
        return skill;
    }

    private static void cast(ServerPlayer player, int index) {
        var skill = learn(player, index);
        PlayerDataProvider.get(player).setMana(700);
        SkillSystem.cast(player, skill.slot());
    }

    private static double amount(Pig target) {
        return HEALTH - target.getHealth();
    }

    private static void near(GameTestHelper h, double actual, double expected, double tolerance, String label) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, label + " expected " + expected + " HP damage, got " + actual);
    }
}
