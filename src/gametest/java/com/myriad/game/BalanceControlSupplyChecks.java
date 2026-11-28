package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.ControlStatusSystem;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.SilenceSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class BalanceControlSupplyChecks {
    private enum Control {
        STUN(false, false, false), ROOT(false, true, true), SILENCE(true, true, false);

        final boolean move;
        final boolean attack;
        final boolean cast;

        Control(boolean move, boolean attack, boolean cast) {
            this.move = move;
            this.attack = attack;
            this.cast = cast;
        }

        void apply(AbilityRuntime.Cast source, ServerPlayer target, int ticks) {
            if (this == SILENCE) SilenceSystem.apply(target, ticks);
            else source.root(target, ticks, this == STUN);
        }

        int remaining(ServerPlayer target) {
            var controls = ControlStatusSystem.controls(target);
            return switch (this) {
                case STUN -> controls.stun();
                case ROOT -> controls.root();
                case SILENCE -> controls.silence();
            };
        }
    }

    private record Controlled(Control type, ServerPlayer longFirst, ServerPlayer shortFirst, ServerPlayer attackTarget) {}

    private static final class Supply {
        final String mode;
        final ServerPlayer player;
        final List<Double> healthPulses = new ArrayList<>();
        final List<Double> manaPulses = new ArrayList<>();

        Supply(String mode, ServerPlayer player) {
            this.mode = mode;
            this.player = player;
        }

        void tick(long now) {
            var data = PlayerDataProvider.get(player);
            float health = player.getHealth();
            float mana = data.getMana();
            TacticalSupplySystem.tickRegeneration(player, now);
            if (player.getHealth() > health) healthPulses.add((double) (player.getHealth() - health));
            if (data.getMana() > mana) manaPulses.add((double) (data.getMana() - mana));
        }

        double healthGained() {
            return healthPulses.stream().mapToDouble(Double::doubleValue).sum();
        }

        double manaGained() {
            return manaPulses.stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    private BalanceControlSupplyChecks() {}

    static void run(GameTestHelper h) {
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        boolean noCooldown = TrainingMode.isNoCooldown(), noMana = TrainingMode.isNoMana(), noGold = TrainingMode.isNoGoldCost();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        TrainingMode.setNoCooldown(false);
        TrainingMode.setNoMana(false);
        TrainingMode.setNoGoldCost(false);
        var players = new ArrayList<ServerPlayer>();
        boolean[] complete = {false};
        Runnable clean = () -> {
            complete[0] = true;
            for (var player : players) {
                TacticalSupplySystem.clearPlayer(player);
                SilenceSystem.cleanse(player);
                AbilityRuntime.clear(player);
                BaseRules.logout(player);
                player.discard();
            }
            TrainingMode.setEnabled(training);
            TrainingMode.setNoCooldown(noCooldown);
            TrainingMode.setNoMana(noMana);
            TrainingMode.setNoGoldCost(noGold);
            ModState.setEnabled(enabled);
        };

        try {
            Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 130, 2)));
            var sourcePlayer = player(h, players, origin);
            var secondSource = player(h, players, origin.add(4, 0, 0));
            var skill = FirstGroupSkills.node("sulan", 4);
            var source = AbilityRuntime.context(sourcePlayer, skill);
            var otherSource = AbilityRuntime.context(secondSource, skill);
            var controls = new ArrayList<Controlled>();
            for (Control type : Control.values()) {
                Vec3 at = origin.add(8 * (type.ordinal() + 1), 0, 0);
                var longFirst = player(h, players, at);
                var shortFirst = player(h, players, at.add(0, 0, 8));
                var attackTarget = player(h, players, at.add(0, 0, 1.5));
                type.apply(source, longFirst, 30);
                type.apply(otherSource, longFirst, 10);
                type.apply(source, shortFirst, 10);
                type.apply(otherSource, shortFirst, 30);
                int longShort = type.remaining(longFirst), shortLong = type.remaining(shortFirst);
                h.assertTrue(longShort == 30 && shortLong == 30,
                        type + " overlap chooses the later expiry in either order");
                controls.add(new Controlled(type, longFirst, shortFirst, attackTarget));
            }

            var timeActor = player(h, players, origin.add(0, 0, 12));
            var timeData = PlayerDataProvider.get(timeActor);
            SkillSystem.cast(timeActor, skill.slot());
            int initialCooldown = timeData.getCooldown(skill.getId());
            h.assertTrue(initialCooldown > 0 && timeData.getMana() < 700, "Actual Wind Barrier starts a positive cooldown");
            long start = h.getLevel().getGameTime();
            ActionTime.apply(timeActor.getUUID(), sourcePlayer.getUUID(), "weak", .75, start + 9);
            ActionTime.apply(timeActor.getUUID(), secondSource.getUUID(), "strong", .5, start + 5);
            long[] actionAt = {-1};
            AbilityRuntime.context(timeActor, skill).actionLater(6, () -> actionAt[0] = h.getLevel().getGameTime() - start);

            var out = new Supply("out_of_combat", player(h, players, origin.add(0, 0, 16)));
            var in = new Supply("in_combat", player(h, players, origin.add(0, 0, 20)));
            var mixed = new Supply("mixed", player(h, players, origin.add(0, 0, 24)));
            var full = new Supply("full", player(h, players, origin.add(0, 0, 28)));
            var dead = new Supply("death", player(h, players, origin.add(0, 0, 32)));
            var supplies = List.of(out, in, mixed, full, dead);
            for (Supply supply : supplies) {
                var p = supply.player;
                var data = PlayerDataProvider.get(p);
                var blood = new ItemStack(ModItems.BLOOD_PILL.get(), 2);
                var mana = new ItemStack(ModItems.MANA_PILL.get(), 2);
                if (supply == full) {
                    TacticalSupplySystem.tryUse(p, blood);
                    TacticalSupplySystem.tryUse(p, mana);
                    h.assertTrue(blood.getCount() == 2 && mana.getCount() == 2, "Already full resources block both pills");
                    p.setHealth(797);
                    data.setMana(695);
                } else {
                    p.setHealth(100);
                    data.setMana(100);
                }
                if (supply == in || supply == mixed) recordCombat(h, sourcePlayer, p, 1);
                TacticalSupplySystem.tryUse(p, blood);
                TacticalSupplySystem.tryUse(p, mana);
                TacticalSupplySystem.tryUse(p, blood);
                TacticalSupplySystem.tryUse(p, mana);
                h.assertTrue(blood.getCount() == 1 && mana.getCount() == 1,
                        "Each pill consumes once while both distinct types can run concurrently");
            }
            verifyCarryLimit(h, player(h, players, origin.add(0, 0, 36)));
            for (int tick = 1; tick <= 160; tick++) {
                int age = tick;
                h.runAfterDelay(tick, () -> {
                    if (complete[0]) return;
                    try {
                        long now = h.getLevel().getGameTime();
                        if (age <= 30) {
                            for (Controlled fixture : controls) {
                                PlayerDataProvider.onPlayerTick(fixture.longFirst());
                                PlayerDataProvider.onPlayerTick(fixture.shortFirst());
                                if (age == 11) verifyActions(h, fixture, skill);
                                if (age == 29) assertRemaining(h, fixture, 1);
                                if (age == 30) {
                                    assertRemaining(h, fixture, 0);
                                    var type = fixture.type();
                                    System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=control_overlap type=%s long_short=30 short_long=30 before_expiry=1 on_expiry=0 move=%b basic_attack=%b stationary_cast=%b%n",
                                            type.name().toLowerCase(Locale.ROOT), type.move, type.attack, type.cast);
                                }
                            }
                        }

                        if (age <= 9) {
                            PlayerDataProvider.onPlayerTick(timeActor);
                            double expected = age < 5 ? .5 : age < 9 ? .75 : 1;
                            h.assertTrue(ActionTime.multiplier(timeActor.getUUID(), now) == expected
                                            && timeData.getCooldown(skill.getId()) == initialCooldown - age,
                                    "Action sources pick the lowest live rate and the real cooldown drops once at tick " + age);
                            h.assertTrue(actionAt[0] == (age == 9 ? 9 : -1),
                                    "Six actual action ticks complete at real tick nine under the source expiry schedule");
                            if (age == 9) {
                                System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=action_time weak=0.75 strong=0.5 overlap=0.5 recovered=0.75 clear=1.0 required_action_ticks=6 elapsed_real_ticks=%d action_lost=%d initial_cooldown=%d remaining_cooldown=%d%n",
                                        actionAt[0], actionAt[0] - 6, initialCooldown, timeData.getCooldown(skill.getId()));
                            }
                        }

                        if (age == 80) recordCombat(h, sourcePlayer, in.player, 1);
                        if (age == 21) {
                            // Let the normal Fabric death event end both active pills and revive the training player.
                            dead.player.damageCooldownTime = 0;
                            DamageCalc.dealUncriticalSkillDamage(sourcePlayer, dead.player, 10000, DamageCalc.Kind.TRUE, 0);
                            var data = PlayerDataProvider.get(dead.player);
                            h.assertTrue(dead.player.getHealth() == dead.player.getMaxHealth() && data.getMana() == 700
                                            && data.getBuff("blood_pill") == 0 && data.getBuff("mana_pill") == 0,
                                    "The lethal hit follows the actual training death and refill path");
                            dead.player.setHealth(100);
                            data.setMana(100);
                        }
                        for (Supply supply : supplies) supply.tick(now);
                        if (age == 19) {
                            h.assertTrue(supplies.stream().allMatch(s -> s.healthPulses.isEmpty() && s.manaPulses.isEmpty()),
                                    "Neither pill pulses before a full second");
                        }
                        if (age == 20) {
                            h.assertTrue(full.player.getHealth() == 800 && PlayerDataProvider.get(full.player).getMana() == 700,
                                    "Pill pulses cap at the real resources missing");
                            full.player.setHealth(100);
                            PlayerDataProvider.get(full.player).setMana(100);
                        }
                        if (age == 160) {
                            for (Supply supply : supplies) {
                                var data = PlayerDataProvider.get(supply.player);
                                h.assertTrue(data.getBuff("blood_pill") == 0 && data.getBuff("mana_pill") == 0,
                                        "Pill effects clear at their final pulse, full resources or death: " + supply.mode);
                            }
                            verifyPulses(h, out, List.of(18., 18., 18., 18., 18., 18., 18., 18.), 144);
                            verifyPulses(h, in, List.of(1.8, 1.8, 1.8, 1.8, 1.8, 1.8, 1.8, 1.8), 14.4);
                            verifyPulses(h, mixed, List.of(1.8, 1.8, 1.8, 1.8, 1.8, 18., 18., 18.), 63);
                            for (Supply supply : List.of(out, in, mixed)) {
                                h.assertTrue(supply.manaPulses.equals(List.of(30., 30., 30., 30., 30.)),
                                        "700 max mana restores 150 in five pulses regardless of combat: " + supply.mode);
                            }
                            System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=mana_pill maximum=700 total=%.3f pulses=%s%n",
                                    in.manaGained(), values(in.manaPulses));
                            h.assertTrue(full.healthPulses.equals(List.of(3.)) && full.manaPulses.equals(List.of(5.))
                                            && dead.healthPulses.size() == 1 && dead.manaPulses.size() == 1,
                                    "Resources lost later cannot restart either pill after filling up or dying");
                            System.out.println("MYRIAD_COMBAT case=pill_lifecycle full_stops=true death_stops=true same_type_rejected=true combined_carry_limit=5");
                            clean.run();
                            h.succeed();
                        }
                    } catch (Throwable failure) {
                        clean.run();
                        throw failure;
                    }
                });
            }
        } catch (Throwable failure) {
            clean.run();
            throw failure;
        }
    }

    private static void assertRemaining(GameTestHelper h, Controlled fixture, int expected) {
        h.assertTrue(fixture.type().remaining(fixture.longFirst()) == expected
                        && fixture.type().remaining(fixture.shortFirst()) == expected,
                fixture.type() + " expected remaining at real expiry boundary: " + expected);
    }

    private static void verifyActions(GameTestHelper h, Controlled fixture,
            com.myriad.character.ability.AbilitySkill skill) {
        var actor = fixture.longFirst();
        var data = PlayerDataProvider.get(actor);
        var type = fixture.type();
        boolean move = actor.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0;
        h.assertTrue(move == type.move, type + " autonomous movement speed availability: " + move);
        readyAttack(actor);
        float targetHealth = fixture.attackTarget().getHealth();
        actor.attack(fixture.attackTarget());
        boolean attacked = fixture.attackTarget().getHealth() < targetHealth;
        h.assertTrue(attacked == type.attack, type + " native attack affects target health: " + attacked);
        float mana = data.getMana();
        SkillSystem.cast(actor, skill.slot());
        boolean cast = data.getMana() < mana && data.getCooldown(skill.getId()) > 0;
        h.assertTrue(cast == type.cast, type + " actual stationary Wind Barrier cast commits: " + cast);
        AbilityRuntime.context(actor, skill).dash(new Vec3(0, 0, 1), 2, 0,
                (ignored, first) -> {}, () -> {}, false, false);
        h.assertTrue(AbilityRuntime.moving(actor) == type.move,
                type + " shared autonomous displacement starts according to the movement restriction");
    }

    private static void verifyCarryLimit(GameTestHelper h, ServerPlayer player) {
        var data = PlayerDataProvider.get(player);
        data.setGold(500);
        player.getInventory().setItem(5, new ItemStack(ModItems.BLOOD_PILL.get(), 2));
        player.getInventory().setItem(6, new ItemStack(ModItems.MANA_PILL.get(), 2));
        h.assertTrue(TacticalSupplySystem.buy(player, "blood_pill") && data.getGold() == 440,
                "The fifth pill is offered at the normal 60 gold price");
        h.assertTrue(!TacticalSupplySystem.buy(player, "mana_pill")
                        && !TacticalSupplySystem.buy(player, "blood_pill") && data.getGold() == 440,
                "The sixth pill of either kind is rejected before deducting gold");
    }

    private static void recordCombat(GameTestHelper h, ServerPlayer source, ServerPlayer target, float amount) {
        target.damageCooldownTime = 0;
        DamageCalc.dealUncriticalSkillDamage(source, target, amount, DamageCalc.Kind.TRUE, 0);
        long now = h.getLevel().getGameTime();
        h.assertTrue(PlayerDataProvider.get(target).getLastCombatGameTime() == now
                        && PlayerDataProvider.get(source).getLastCombatGameTime() == now,
                "Real hostile damage records combat on both heroes");
    }

    private static void verifyPulses(GameTestHelper h, Supply supply, List<Double> expected, double total) {
        h.assertTrue(supply.healthPulses.size() == expected.size(), "Eight blood pulses for " + supply.mode);
        for (int i = 0; i < expected.size(); i++) {
            h.assertTrue(Math.abs(supply.healthPulses.get(i) - expected.get(i)) < .001,
                    "Blood pulse at real tick " + (i + 1) * 20 + " for " + supply.mode + ": " + supply.healthPulses.get(i));
        }
        h.assertTrue(Math.abs(supply.healthGained() - total) < .001, "Blood total for " + supply.mode);
        System.out.printf(Locale.ROOT, "MYRIAD_COMBAT case=blood_pill mode=%s maximum=800 total=%.3f pulses=%s%n",
                supply.mode, supply.healthGained(), values(supply.healthPulses));
    }

    private static String values(List<Double> pulses) {
        return String.join(",", pulses.stream().map(v -> String.format(Locale.ROOT, "%.3f", v)).toList());
    }

    private static ServerPlayer player(GameTestHelper h, List<ServerPlayer> players, Vec3 position) {
        var p = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        players.add(p);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
        };
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        p.setPos(position);
        p.setXRot(0);
        p.setYRot(0);
        p.setNoGravity(true);
        p.tickCount = 1;
        var data = PlayerDataProvider.get(p);
        data.setCharacterId("sulan");
        data.setLevel(4);
        data.setSkillLevel(FirstGroupSkills.node("sulan", 4).getId(), 1);
        data.setModHealthInitialized(true);
        data.setModMaxHealth(800);
        data.setMana(700);
        p.setHealth(800);
        p.getAttribute(ModAttributes.MAX_MANA).setBaseValue(700);
        p.getAttribute(ModAttributes.TENACITY).setBaseValue(0);
        p.getAttribute(ModAttributes.PHYSICAL_ATTACK).setBaseValue(50);
        p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.initialWeaponItem("sulan")));
        return p;
    }

    private static void readyAttack(ServerPlayer player) {
        try {
            Field strength = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            strength.setAccessible(true);
            strength.setInt(player, 200);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
