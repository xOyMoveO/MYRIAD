package com.myriad.game;

import com.google.gson.JsonObject;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.PlayerHealthSystem;
import com.myriad.registry.ModAttributes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

final class TianxingBalanceChecks {
    private static final int TARGET_HEALTH = 1000;

    private TianxingBalanceChecks() {}

    static void run(GameTestHelper h) {
        boolean mod = ModState.isEnabled();
        boolean training = TrainingMode.isEnabled();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        ThunderThirdStrikeChecks.verify(h);
        var p = player(h);
        var players = h.getLevel().getServer().getPlayerList().getPlayersByUUID();
        var spot = new BlockPos(8, 12, 5);
        var origin = Vec3.atBottomCenterOf(h.absolutePos(spot));
        p.setPos(origin);
        p.setNoGravity(true);
        p.setOnGround(true);
        var data = PlayerDataProvider.get(p);
        data.setCharacterId("tianxing");
        data.setLevel(20);
        int rank = FirstGroupSkills.passiveRank(20);
        var skill = FirstGroupSkills.node("tianxing", 0);
        data.setSkillLevel(skill.getId(), rank);
        PlayerDataProvider.applyChosenCharacter(p, data);
        p.getInventory().setSelectedSlot(0);
        p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(125);
        var targets = List.of(target(h, spot.offset(0, 0, 2)), target(h, spot.offset(2, 0, 2)),
                target(h, spot.offset(-2, 0, 2)), target(h, spot.offset(0, 0, 4)));
        var main = targets.getFirst();
        var memory = AbilityRuntime.context(p, skill).memory();
        double[] firstHit = {0};

        h.runAtTickTime(5, () -> {
            firstHit[0] = attack(h, p, targets, "first", 1);
            h.assertTrue(firstHit[0] > 0 && main.getUUID().equals(memory.target), "Effective native basic increments the mark");
        });
        h.runAtTickTime(20, () -> {
            var before = health(targets);
            long last = memory.last;
            p.setYRot(180);
            p.swing(InteractionHand.MAIN_HAND, p.getMainHandItem().getAttackAnimation(), false);
            sample(h, "miss", targets, before, memory.count);
            h.assertTrue(memory.count == 1 && memory.last == last && health(targets).equals(before),
                    "A real swing without contact preserves count and does not refresh the mark");
            p.setYRot(0);
        });
        h.runAtTickTime(35, () -> {
            double second = attack(h, p, targets, "second_after_miss", 2);
            near(h, second, firstHit[0], "Miss does not add an extra damaging segment");
        });
        h.runAtTickTime(65, () -> {
            var before = health(targets);
            double third = attack(h, p, targets, "third_and_splash", 0);
            near(h, third - firstHit[0], 70, "Third same-target attack independently adds the true segment");
            var after = health(targets);
            var splashLoss = java.util.stream.IntStream.range(1, targets.size())
                    .mapToDouble(i -> before.get(i) - after.get(i)).filter(loss -> loss > 0).toArray();
            h.assertTrue(splashLoss.length == 2, "The actual third hit splashes a maximum of two additional targets");
            for (double loss : splashLoss) near(h, loss, 83.4, "Each splash is independently applied");
        });
        h.runAtTickTime(95, () -> {
            attack(h, p, targets, "new_cycle", 1);
            p.setPos(origin.add(2, 0, 0));
        });
        h.runAtTickTime(125, () -> {
            var before = health(targets);
            targets.get(1).damageCooldownTime = 0;
            DamageGrowthChecks.swing(p, targets.get(1));
            sample(h, "switch_target", targets, before, memory.count);
            h.assertTrue(memory.count == 1 && targets.get(1).getUUID().equals(memory.target)
                    && targets.get(1).getHealth() < before.get(1), "An effective hit after a target switch starts a new count");
        });
        h.runAtTickTime(210, () -> {
            p.setPos(origin);
            var before = health(targets);
            main.damageCooldownTime = 0;
            DamageGrowthChecks.swing(p, main);
            sample(h, "return_to_main", targets, before, memory.count);
            h.assertTrue(memory.count == 1 && main.getUUID().equals(memory.target), "Returning to the old target starts at one");
        });
        h.runAtTickTime(291, () -> {
            attack(h, p, targets, "expired_same_target", 1);
            compareIncoming(h, p);
            AbilityRuntime.clear(p);
            targets.forEach(Entity::discard);
            players.remove(p.getUUID());
            p.discard();
            TrainingMode.setEnabled(training);
            ModState.setEnabled(mod);
            h.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper h) {
        var p = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND),
                p, CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
        };
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(), p);
        return p;
    }

    private static void compareIncoming(GameTestHelper h, ServerPlayer source) {
        var victim = player(h);
        victim.setPos(source.position().add(0, 0, -2));
        victim.setNoGravity(true);
        var data = PlayerDataProvider.get(victim);
        List<CharacterType> roles = List.of(CharacterType.TIANXING, CharacterType.SULAN, CharacterType.FENJUE,
                CharacterType.SHUO, CharacterType.RIVET);
        try {
            for (int level : new int[]{4, 12, 20}) {
                int defense = level == 4 ? 0 : 2;
                for (var role : roles) {
                    AbilityRuntime.clear(victim);
                    data.clearSkillCooldowns();
                    data.refundAllSkillPointsForTraining();
                    data.setCharacterId(role.getId());
                    data.setLevel(level);
                    data.setFirstGroupAttribute("defense", defense);
                    PlayerDataProvider.applyChosenCharacter(victim, data);
                    var base = receiveDamage(h, source, victim, role, level, defense, "no_active_defense");
                    if (role != CharacterType.TIANXING) continue;
                    var guard = FirstGroupSkills.node(role.getId(), 4);
                    data.setSkillLevel(guard.getId(), 1);
                    SkillSystem.cast(victim, CharacterType.SLOT_E);
                    h.assertTrue(data.getBuff(guard.getId()) > 0, "Live Thunder Guard is active before the same incoming hits");
                    var guarded = receiveDamage(h, source, victim, role, level, defense, "thunder_guard");
                    near(h, guarded.get(0), base.get(0) * .85, "Thunder Guard changes actual physical HP loss");
                    near(h, guarded.get(1), base.get(1) * .85, "Thunder Guard changes actual magic HP loss");
                    near(h, guarded.get(2), base.get(2), "Thunder Guard preserves actual true HP loss");
                    data.setBuff(guard.getId(), 0);
                    data.setBuff("g1_guard15", 0);
                }
            }
        } finally {
            AbilityRuntime.clear(victim);
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(victim.getUUID());
            victim.discard();
        }
    }

    private static List<Double> receiveDamage(GameTestHelper h, ServerPlayer source, ServerPlayer victim,
            CharacterType role, int level, int defense, String scenario) {
        PlayerHealthSystem.fill(victim);
        var amounts = List.of(160F, 160F, 90F, 180F, 180F);
        var kinds = List.of(DamageCalc.Kind.PHYSICAL, DamageCalc.Kind.MAGIC, DamageCalc.Kind.TRUE,
                DamageCalc.Kind.PHYSICAL, DamageCalc.Kind.MAGIC);
        var losses = new java.util.ArrayList<Double>();
        var row = new JsonObject();
        row.addProperty("event", "incoming_pressure");
        row.addProperty("scenario", scenario);
        row.addProperty("hero", role.getId());
        row.addProperty("level", level);
        row.addProperty("defense", defense);
        row.addProperty("startingHealthHp", victim.getHealth());
        var hits = new com.google.gson.JsonArray();
        for (int i = 0; i < amounts.size(); i++) {
            victim.damageCooldownTime = 0;
            float before = victim.getHealth();
            DamageCalc.dealUncriticalSkillDamage(source, victim, amounts.get(i), kinds.get(i), 0);
            double loss = before - victim.getHealth();
            h.assertTrue(loss > 0, "Incoming pressure uses the actual HP path");
            losses.add(loss);
            var hit = new JsonObject();
            hit.addProperty("kind", kinds.get(i).name());
            hit.addProperty("rawHp", amounts.get(i));
            hit.addProperty("healthLossHp", loss);
            hits.add(hit);
        }
        row.add("hits", hits);
        row.addProperty("remainingHealthHp", victim.getHealth());
        System.out.println("MYRIAD_ROSTER_112_THUNDER " + row);
        return losses;
    }

    private static LivingEntity target(GameTestHelper h, BlockPos pos) {
        var target = h.spawn(EntityTypes.PIG, pos);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
        target.setHealth(TARGET_HEALTH);
        return target;
    }

    private static double attack(GameTestHelper h, ServerPlayer actor, List<LivingEntity> targets, String event, int count) {
        var before = health(targets);
        var target = targets.getFirst();
        target.damageCooldownTime = 0;
        DamageGrowthChecks.swing(actor, target);
        int actual = AbilityRuntime.context(actor, FirstGroupSkills.node("tianxing", 0)).memory().count;
        sample(h, event, targets, before, actual);
        h.assertTrue(actual == count, "Mark count after " + event + ": " + actual);
        return before.getFirst() - target.getHealth();
    }

    private static List<Float> health(List<LivingEntity> targets) {
        return targets.stream().map(LivingEntity::getHealth).toList();
    }

    private static void sample(GameTestHelper h, String event, List<LivingEntity> targets, List<Float> before, int count) {
        var row = new JsonObject();
        row.addProperty("event", event);
        row.addProperty("tick", h.getTick());
        row.addProperty("markCount", count);
        row.addProperty("mainHealthLossHp", before.getFirst() - targets.getFirst().getHealth());
        var splash = new com.google.gson.JsonArray();
        for (int i = 1; i < targets.size(); i++) splash.add(before.get(i) - targets.get(i).getHealth());
        row.add("otherTargetsHealthLossHp", splash);
        System.out.println("MYRIAD_ROSTER_112_THUNDER " + row);
    }

    private static void near(GameTestHelper h, double actual, double expected, String detail) {
        h.assertTrue(Math.abs(actual - expected) < .01, detail + " actual=" + actual + " expected=" + expected);
    }
}
