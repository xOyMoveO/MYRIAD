package com.myriad.game;

import com.google.gson.Gson;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.ModShieldSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Native blows, production casts and collision-stepped movement on fixed terrain. */
final class ShuoChixiaoBalanceChecks {
    private static final String GUARD = "shuo_qiaofan";
    private static final String DASH = "g2_chixiao_n2";
    private static final String LEAP = "g2_chixiao_ua";
    private static final String WAR_BLADE = "g2_chixiao_ub";
    private static final Gson JSON = new Gson();
    private static final Map<LivingEntity, List<Strike>> WATCHED = new HashMap<>();
    private static boolean watching;

    private final GameTestHelper h;
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final List<ServerPlayer> players = new ArrayList<>();
    private final boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
    private final boolean freeMana = TrainingMode.isNoMana(), noCooldown = TrainingMode.isNoCooldown();
    private final boolean freeGold = TrainingMode.isNoGoldCost();
    private final long start;
    private boolean done;

    private record Strike(long tick, Entity attacker, String skill, int cast, int slot) {}
    private record Target(ServerPlayer player, Vec3 start, double speed, double hp, List<Strike> strikes) {}
    private record DashRun(String id, ServerPlayer player, Vec3 start, Target near, Target far, boolean wall) {}
    private record LeapRun(String id, ServerPlayer player, Vec3 start, Target target, boolean wall) {}

    private ShuoChixiaoBalanceChecks(GameTestHelper h) {
        this.h = h;
        this.start = h.getLevel().getGameTime();
    }

    static void run(GameTestHelper h) {
        var check = new ShuoChixiaoBalanceChecks(h);
        check.run();
    }

    private void run() {
        if (!watching) {
            ServerLivingEntityEvents.ALLOW_DAMAGE.register((target, source, amount) -> {
                var list = WATCHED.get(target);
                if (list != null) {
                    var skill = DamageCalc.peekSkillHit(target);
                    list.add(new Strike(target.level().getGameTime(), source.getEntity(),
                            AbilityRuntime.sourceSkill(), AbilityRuntime.sourceCast(), skill == null ? -1 : skill.slot()));
                }
                return true;
            });
            watching = true;
        }
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        TrainingMode.setNoMana(false);
        TrainingMode.setNoCooldown(false);
        TrainingMode.setNoGoldCost(false);
        try {
            parry(0, "parry_hit_zero", false, false, false);
            parry(1, "parry_hit_strength", true, false, false);
            parry(2, "parry_whiff", false, true, false);
            parry(3, "rear_hit_no_parry", false, false, true);
            var open = dash(4, false);
            var wall = dash(5, true);
            var openLeap = leap(6, false);
            var wallLeap = leap(7, true);

            var bladeStart = lane(8, 0, 0);
            var blade = actor(CharacterType.CHIXIAO, bladeStart, Faction.GUARDIAN);
            var bladeTarget = target(bladeStart.add(0, 0, 2));
            var bladeSegments = new ArrayList<Double>();

            for (int i = 1; i <= 126; i++) {
                int at = i;
                h.runAfterDelay(i, () -> {
                    if (done) return;
                    try {
                        if (at <= 24) {
                            step(open.player());
                            step(wall.player());
                            step(openLeap.player());
                            step(wallLeap.player());
                        }
                        if (at == 10) {
                            verifyDash(open, false);
                            verifyDash(wall, false);
                            SkillSystem.cast(open.player(), CharacterType.SLOT_W);
                        }
                        if (at == 21) {
                            verifyDash(open, true);
                            verifyLeap(openLeap);
                            verifyLeap(wallLeap);
                        }
                        blade.doTick();
                        if (at == 31) {
                            learnAndCast(blade, WAR_BLADE);
                            near(ModShieldSystem.value(blade), 251, "War Blade actual starting shield");
                        }
                        if (at == 32 || at == 63 || at == 94 || at == 125) {
                            bladeTarget.player().setPos(bladeTarget.start());
                            double before = bladeTarget.player().getHealth();
                            int hitCount = bladeTarget.strikes().size();
                            basic(blade, bladeTarget.player(), false);
                            var events = bladeTarget.strikes().subList(hitCount, bladeTarget.strikes().size());
                            h.assertTrue(events.stream().anyMatch(e -> e.attacker() == blade && e.skill().isEmpty()),
                                    "War Blade observation includes the native basic attack");
                            int expected = at == 125 ? 0 : 1;
                            var bonus = events.stream().filter(e -> e.skill().equals(WAR_BLADE)).toList();
                            h.assertTrue(bonus.size() == expected, "Only the first three native hits trigger War Blade");
                            for (var strike : bonus) owner(strike, blade, WAR_BLADE, CharacterType.SLOT_R);
                            var total = before - bladeTarget.player().getHealth();
                            h.assertTrue(total > 0, "Native War Blade attack must actually damage the target");
                            bladeSegments.add(total);
                            trace("chixiao.war_blade", "native_hit", Map.of("totalHpLoss", total, "bonusHits", expected,
                                    "skillSource", bonus.isEmpty() ? "" : bonus.getFirst().skill(),
                                    "sourceSlot", bonus.isEmpty() ? -1 : bonus.getFirst().slot(),
                                    "castId", bonus.isEmpty() ? 0 : bonus.getFirst().cast()));
                        }
                        if (at == 126) {
                            for (int n = 0; n < 3; n++) near(bladeSegments.get(n) - bladeSegments.get(3), 54,
                                    "Each native War Blade attack adds one 54-HP segment");
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

    private void parry(int index, String id, boolean strength, boolean miss, boolean rear) {
        var origin = lane(index, 0, 0);
        var shuo = actor(CharacterType.SHUO, origin, Faction.GUARDIAN);
        var enemy = target(origin.add(0, 0, rear ? -2 : 2));
        if (strength) {
            var data = PlayerDataProvider.get(shuo);
            data.setGold(360);
            h.assertTrue(FirstGroupGrowth.buy(shuo, data, "strength") && data.getGold() == 0,
                    "One strength point goes through normal purchase at 360 gold");
        }
        var shuoData = PlayerDataProvider.get(shuo);
        double initialHp = shuo.getHealth();
        learnAndCast(shuo, GUARD);
        near(ModShieldSystem.value(shuo), 0, "Opening guard alone gives no shield");
        basic(enemy.player(), shuo, true);
        boolean ready = shuoData.getBuff(GUARD + "_recast") > 0;
        double incomingLoss = initialHp - shuo.getHealth();
        h.assertTrue(ready != rear, "Frontal native hit opens the riposte; a rear hit cannot");
        if (rear) h.assertTrue(incomingLoss > 0, "An unguarded native hit really damages Shuo");
        else near(incomingLoss, 0, "A real frontal strike is blocked");
        near(ModShieldSystem.value(shuo), 0, "The guard result itself gives no shield");
        if (miss) enemy.player().setPos(origin.add(0, 0, -2));
        SkillSystem.cast(shuo, CharacterType.SLOT_W);
        double dealt = enemy.hp() - enemy.player().getHealth();
        double shield = ModShieldSystem.value(shuo);
        int stun = PlayerDataProvider.get(enemy.player()).getBuff("g1_stun");
        boolean hit = !miss && !rear;
        near(dealt, hit ? strength ? 69.5 : 62 : 0, "Riposte health damage " + id);
        near(shield, hit ? strength ? 62.5 : 57.5 : 0, "Riposte shield " + id);
        h.assertTrue(stun == (hit ? 6 : 0), "Only a landed counter applies six real ticks of stun");
        h.assertTrue(enemy.strikes().size() == (hit ? 1 : 0), "At most a single counter strike");
        if (hit) owner(enemy.strikes().getFirst(), shuo, GUARD, CharacterType.SLOT_W);
        double shieldAbsorbed = 0;
        if (hit) {
            // The counter stunned the first attacker. An independent ally of that attacker delivers the follow-up.
            var followUp = target(origin.add(1.4, 0, 0));
            shuo.damageCooldownTime = 0;
            basic(followUp.player(), shuo, true);
            shieldAbsorbed = shield - ModShieldSystem.value(shuo);
            h.assertTrue(shieldAbsorbed > 0 && shieldAbsorbed < shield, "An actual next hit consumes the counter shield");
            near(shuo.getHealth(), initialHp, "Shield protects actual health on follow-up");
        }
        trace("shuo." + id, "counter", Map.of("parryReady", ready, "incomingHpLoss", incomingLoss, "riposteHpLoss", dealt,
                "shieldHp", shield, "actualAbsorbedHp", shieldAbsorbed, "stunTicks", stun, "source", hit ? GUARD : ""));
    }

    private DashRun dash(int index, boolean wall) {
        var origin = lane(index, wall ? 4 : 0, 3);
        var player = actor(CharacterType.CHIXIAO, origin, Faction.GUARDIAN);
        var near = target(origin.add(0, 0, 2));
        var far = target(origin.add(0, 0, 6));
        learnAndCast(player, DASH);
        h.assertTrue(AbilityRuntime.moving(player), "Dash commits even when an obstacle is later in its path");
        return new DashRun(wall ? "w_wall" : "w_open", player, origin, near, far, wall);
    }

    private void verifyDash(DashRun run, boolean returning) {
        var actor = run.player();
        double advance = actor.getZ() - run.start().z;
        if (returning) near(advance, 0, "Free recast returns along the saved line");
        else near(advance, run.wall() ? 2.625 : 7, "Dash uses authoritative collision clearance");
        for (var target : List.of(run.near(), run.far())) {
            boolean hit = target == run.near() || !run.wall();
            double hpLoss = target.hp() - target.player().getHealth();
            near(hpLoss, hit ? 62 : 0, "Dash effective health damage at " + target.start());
            near(target.player().position().distanceTo(target.start()), 0, "Dash leaves target position unchanged");
            double speedRatio = target.player().getAttributeValue(Attributes.MOVEMENT_SPEED) / target.speed();
            near(speedRatio, hit ? .7 : 1, "Slow applies only to targets reached by the dash");
            h.assertTrue(target.strikes().size() == (hit ? 1 : 0), "At most one W impact per target including free return");
            if (hit) owner(target.strikes().getFirst(), actor, DASH, CharacterType.SLOT_W);
            trace("chixiao." + run.id(), returning ? "returned" : "outbound", Map.of("actorZ", actor.getZ(),
                    "actorStartZ", run.start().z, "targetStartZ", target.start().z, "targetZ", target.player().getZ(),
                    "targetHpLoss", hpLoss, "targetMovementRatio", speedRatio, "damageSource", hit ? DASH : "",
                    "hitTick", hit ? target.strikes().getFirst().tick() - start : -1));
        }
        near(PlayerDataProvider.get(actor).getMana(), 620, "Dash and free return together spend 80 mana");
    }

    private LeapRun leap(int index, boolean wall) {
        var origin = lane(index, 0, 0);
        if (wall) {
            // The sight ray stays below the overhang, while the rising body intersects it.
            var base = BlockPos.containing(origin);
            for (int x = -1; x <= 1; x++) {
                for (int y = 2; y <= 5; y++) setBlock(base.offset(x, y, 4), Blocks.STONE.defaultBlockState());
            }
        }
        var player = actor(CharacterType.CHIXIAO, origin, Faction.GUARDIAN);
        var target = target(origin.add(0, 0, 8));
        player.setXRot((float) Math.toDegrees(Math.atan2(player.getEyeHeight(), 7)));
        var data = PlayerDataProvider.get(player);
        data.setSkillLevel(LEAP, 1);
        SkillSystem.cast(player, CharacterType.SLOT_R);
        AbilityRuntime.confirm(player);
        if (wall) {
            h.assertTrue(!AbilityRuntime.moving(player), "Blocked leap cannot start");
            near(data.getMana(), 700, "Failed leap confirmation does not charge mana");
        } else {
            h.assertTrue(AbilityRuntime.moving(player), "Clear ground allows a real leap");
            near(data.getMana(), 520, "Legal leap charges 180 mana");
        }
        return new LeapRun(wall ? "r_wall" : "r_open", player, origin, target, wall);
    }

    private void verifyLeap(LeapRun run) {
        double travel = run.player().getZ() - run.start().z;
        if (run.wall()) near(travel, 0, "Blocked leap does not advance");
        else h.assertTrue(travel > 6.5 && travel < 7.5, "Actual leap arrives at the aimed landing cell: " + travel);
        var target = run.target();
        double loss = target.hp() - target.player().getHealth();
        near(loss, run.wall() ? 0 : 195.5, "Only actual landing causes damage");
        near(target.player().position().distanceTo(target.start()), 0, "Leap leaves enemy position unchanged");
        double speedRatio = target.player().getAttributeValue(Attributes.MOVEMENT_SPEED) / target.speed();
        near(speedRatio, run.wall() ? 1 : .6, "Only actual landing applies the movement slow");
        h.assertTrue(target.strikes().size() == (run.wall() ? 0 : 1), "At most one landing hit");
        if (!run.wall()) owner(target.strikes().getFirst(), run.player(), LEAP, CharacterType.SLOT_R);
        trace("chixiao." + run.id(), "landing", Map.of("actorStartZ", run.start().z, "actorEndZ", run.player().getZ(),
                "targetStartZ", target.start().z, "targetEndZ", target.player().getZ(), "targetHpLoss", loss,
                "targetMovementRatio", speedRatio, "source", run.wall() ? "" : LEAP));
    }

    private void step(ServerPlayer player) {
        if (AbilityRuntime.moving(player)) player.move(MoverType.SELF, player.getDeltaMovement());
        AbilityRuntime.tick(player);
    }

    private Vec3 lane(int index, int wallDepth, int wallHeight) {
        var start = h.absolutePos(new BlockPos(4 + 9 * index, 120, 4));
        CombatChunks.prepare(h.getLevel(), start);
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= 10; z++) {
                setBlock(start.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            }
            if (wallDepth != 0) {
                for (int y = 0; y < wallHeight; y++) setBlock(start.offset(x, y, wallDepth), Blocks.STONE.defaultBlockState());
            }
        }
        return Vec3.atBottomCenterOf(start);
    }

    private void setBlock(BlockPos position, BlockState block) {
        blocks.putIfAbsent(position, h.getLevel().getBlockState(position));
        h.getLevel().setBlockAndUpdate(position, block);
    }

    private ServerPlayer actor(CharacterType role, Vec3 position, Faction faction) {
        var player = ShuoChecks.player(h, position, new ArrayList<>());
        players.add(player);
        var data = PlayerDataProvider.get(player);
        data.setCharacterId(role.getId());
        data.setLevel(1);
        data.setFaction(faction);
        PlayerDataProvider.applyChosenCharacter(player, data);
        player.setPos(position);
        player.setOnGround(true);
        player.setHealth(player.getMaxHealth());
        data.setMana(700);
        var crit = player.getAttribute(ModAttributes.CRIT_CHANCE);
        crit.removeModifiers();
        crit.setBaseValue(0);
        h.getLevel().addNewPlayer(player);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        return player;
    }

    private Target target(Vec3 position) {
        var player = actor(CharacterType.CHIXIAO, position, Faction.DESTRUCTION);
        player.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);
        var events = new ArrayList<Strike>();
        WATCHED.put(player, events);
        return new Target(player, position, player.getAttributeValue(Attributes.MOVEMENT_SPEED), player.getHealth(), events);
    }

    private static void learnAndCast(ServerPlayer actor, String skill) {
        PlayerDataProvider.get(actor).setSkillLevel(skill, 1);
        SkillSystem.cast(actor, FirstGroupSkills.byId(skill).slot());
    }

    private void basic(ServerPlayer attacker, ServerPlayer target, boolean fresh) {
        if (fresh) {
            try {
                var recovery = LivingEntity.class.getDeclaredField("attackStrengthTicker");
                recovery.setAccessible(true);
                recovery.setInt(attacker, 200);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
        Vec3 toward = target.position().subtract(attacker.position());
        attacker.setYRot((float) -Math.toDegrees(Math.atan2(toward.x, toward.z)));
        attacker.setOnGround(true);
        attacker.fallDistance = 0;
        attacker.getRandom().setSeed(689);
        h.assertTrue(attacker.getAttackStrengthScale(.5f) >= 1, "Script uses only fully recovered native swings");
        attacker.attack(target);
    }

    private void owner(Strike strike, ServerPlayer caster, String id, int slot) {
        h.assertTrue(strike.attacker() == caster && strike.skill().equals(id) && strike.slot() == slot && strike.cast() > 0,
                "Damage event attributes " + id + " to the actual caster, slot and cast");
    }

    private void trace(String scenario, String event, Map<String, Object> fields) {
        var row = new LinkedHashMap<String, Object>();
        row.put("scenario", scenario);
        row.put("event", event);
        row.put("tick", h.getLevel().getGameTime() - start);
        row.putAll(fields);
        System.out.println("MYRIAD_ROSTER_111 " + JSON.toJson(row));
    }

    private void near(double value, double expected, String label) {
        h.assertTrue(Math.abs(value - expected) < .04, label + " expected " + expected + " but read " + value);
    }

    private void finish() {
        if (done) return;
        done = true;
        for (var player : players) {
            WATCHED.remove(player);
            AbilityRuntime.clear(player);
            ModShieldSystem.clearFor(player);
            BaseRules.logout(player);
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(player.getUUID());
            h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        blocks.forEach(h.getLevel()::setBlockAndUpdate);
        TrainingMode.setEnabled(training);
        TrainingMode.setNoMana(freeMana);
        TrainingMode.setNoCooldown(noCooldown);
        TrainingMode.setNoGoldCost(freeGold);
        ModState.setEnabled(enabled);
    }
}
