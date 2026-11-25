package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Repeatable grounded attack and farming timings on the production monster and damage paths. */
final class ChixiaoOpeningPaceChecks {
    private static final int WARMUP = 45;
    private static final int ACTION_GAP = 11;
    private static final int TEST_LIMIT = 1850;
    private static final List<Build> BUILDS = List.of(new Build(0, 0), new Build(1, 0), new Build(3, 0),
            new Build(5, 0), new Build(0, 1), new Build(4, 1));
    private static final List<MonsterType> CAMPS = List.of(MonsterType.CHERRY_FROST_HARE,
            MonsterType.CHERRY_DUSK_FOX, MonsterType.CHERRY_SCOUT_BOT);

    private record Build(int strength, int wisdom) {}

    private static final class Trial {
        final ServerPlayer actor;
        final List<CherryRiftMonsterEntity> targets = new ArrayList<>();
        final List<Float> combo = new ArrayList<>();
        final List<Integer> kills = new ArrayList<>();
        final Vec3 origin;
        final boolean farm;
        int basics, casts;
        int previousAction = -ACTION_GAP;
        int finishedAt = -1;

        Trial(ServerPlayer actor, Vec3 origin, boolean farm) {
            this.actor = actor;
            this.origin = origin;
            this.farm = farm;
        }
    }

    private ChixiaoOpeningPaceChecks() {}

    static void run(GameTestHelper h) {
        var level = h.getLevel();
        var savedMonsters = MonsterManager.snapshot();
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        boolean freeGold = TrainingMode.isNoGoldCost(), freeMana = TrainingMode.isNoMana();
        boolean noCooldown = TrainingMode.isNoCooldown();
        var trials = new ArrayList<Trial>();
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Runnable cleanup = () -> {
            for (Trial trial : trials) {
                AbilityRuntime.clear(trial.actor);
                trial.actor.discard();
            }
            MonsterManager.restore(level, savedMonsters);
            blocks.forEach(level::setBlockAndUpdate);
            TrainingMode.setEnabled(training);
            TrainingMode.setNoGoldCost(freeGold);
            TrainingMode.setNoMana(freeMana);
            TrainingMode.setNoCooldown(noCooldown);
            ModState.setEnabled(enabled);
        };
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(true);
            TrainingMode.setNoGoldCost(false);
            TrainingMode.setNoMana(false);
            TrainingMode.setNoCooldown(false);
            for (int i = 0; i < BUILDS.size(); i++) prepare(h, trials, blocks, i, BUILDS.get(i), false);
            prepare(h, trials, blocks, BUILDS.size(), BUILDS.get(1), true);
            prepare(h, trials, blocks, BUILDS.size() + 1, BUILDS.get(4), true);
        } catch (RuntimeException | Error failure) {
            com.myriad.Myriad.LOGGER.error("Preparing Chixiao opening-pace fixtures", failure);
            cleanup.run();
            throw failure;
        }
        long start = level.getGameTime();
        GameTestTicks.addListener(new java.util.function.Consumer<net.minecraft.server.MinecraftServer>() {
            @Override public void accept(net.minecraft.server.MinecraftServer server) {
                int tick = (int) (level.getGameTime() - start) - WARMUP;
                try {
                    for (Trial trial : trials) {
                        if (trial.finishedAt >= 0) continue;
                        trial.actor.doTick();
                        PlayerDataProvider.onPlayerTick(trial.actor);
                        if (tick >= 0 && tick - trial.previousAction >= ACTION_GAP) act(h, trial, tick);
                    }
                    h.assertTrue(tick < TEST_LIMIT, "Timed out before clearing the first-spawn camp fixture");
                    if (trials.stream().anyMatch(trial -> trial.finishedAt < 0)) return;
                    verify(h, trials);
                } catch (RuntimeException | Error failure) {
                    com.myriad.Myriad.LOGGER.error("Measuring Chixiao opening pace at tick {}", tick, failure);
                    GameTestTicks.removeListener(this);
                    cleanup.run();
                    throw failure;
                }
                GameTestTicks.removeListener(this);
                cleanup.run();
                h.runAfterDelay(1, h::succeed);
            }
        });
    }

    private static void prepare(GameTestHelper h, List<Trial> trials, Map<BlockPos, BlockState> blocks,
                                int lane, Build build, boolean farm) {
        var level = h.getLevel();
        // Keep all scenarios in the GameTest's ticking chunk and separate their combat volumes vertically.
        BlockPos start = h.absolutePos(new BlockPos(4, 220 + lane * 8, 4));
        CombatChunks.prepare(level, start);
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 3; z++) for (int y = -1; y <= 3; y++) {
            BlockPos at = start.offset(x, y, z);
            blocks.put(at, level.getBlockState(at));
            level.setBlockAndUpdate(at, (y < 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        var actor = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        actor.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                actor, CommonListenerCookie.createInitial(actor.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {}
        };
        actor.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        Vec3 origin = Vec3.atBottomCenterOf(start);
        actor.setPos(origin);
        actor.setYRot(0);
        actor.setXRot(0);
        actor.setOnGround(true);
        var trial = new Trial(actor, origin, farm);
        trials.add(trial);

        var data = PlayerDataProvider.get(actor);
        data.setCharacterId("chixiao");
        data.setLevel(1);
        data.setGold(500);
        PlayerDataProvider.applyChosenCharacter(actor, data);
        PlayerDataProvider.upgradeSkill(actor, cleaveId());
        h.assertTrue(data.skillLevel(cleaveId()) == 1, "Rank-one Q is the only learned skill");
        if (farm) {
            String key = build.strength == 1 ? "strength" : "wisdom";
            h.assertTrue(FirstGroupGrowth.buy(actor, data, key) && data.getGold() == 140
                            && !FirstGroupGrowth.buy(actor, data, key), "A 500-gold opening pays 360 for just one point");
        } else {
            data.setFirstGroupAttribute("strength", build.strength);
            data.setFirstGroupAttribute("wisdom", build.wisdom);
            FirstGroupGrowth.apply(actor, data);
        }
        var crit = actor.getAttribute(ModAttributes.CRIT_CHANCE);
        crit.removeModifiers();
        crit.setBaseValue(0);

        List<MonsterType> types = farm ? CAMPS : List.of(CAMPS.getFirst());
        for (int i = 0; i < types.size(); i++) {
            var type = types.get(i);
            BlockPos at = start.offset(farm ? i - 1 : 0, 0, 2);
            MonsterManager.spawn(level, at, type);
            var mobs = level.getEntitiesOfClass(CherryRiftMonsterEntity.class, new AABB(at).inflate(.5),
                    candidate -> candidate.variant() == type);
            h.assertTrue(!mobs.isEmpty(), "The production spawn must add " + type + " at " + at);
            var mob = mobs.getFirst();
            mob.noPhysics = true;
            mob.setNoGravity(true);
            h.assertTrue(MonsterManager.modHealth(mob) == type.getHealth()
                    && mob.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE) == type.getPhysicalDefense(),
                    "Uses the first-spawn monster's actual health and defense: " + type);
            trial.targets.add(mob);
        }
    }

    private static void act(GameTestHelper h, Trial trial, int tick) {
        var actor = trial.actor;
        var data = PlayerDataProvider.get(actor);
        var target = trial.targets.stream().filter(CherryRiftMonsterEntity::isAlive).findFirst()
                .orElseThrow(() -> new IllegalStateException("No fixture target remains for the next input"));
        float before = MonsterManager.modHealth(target);
        boolean cleave = (trial.farm || trial.basics == 3) && data.getCooldown(cleaveId()) == 0 && data.getMana() >= 70;
        if (cleave) {
            actor.setYRot(0);
            actor.setXRot(0);
            float mana = data.getMana();
            SkillSystem.cast(actor, CharacterType.SLOT_Q);
            h.assertTrue(data.getMana() == mana - 70 && data.getCooldown(cleaveId()) > 0, "Q spends real mana and starts cooldown");
            trial.casts++;
        } else if (actor.getAttackStrengthScale(.5F) >= 1 && (trial.farm || trial.basics < 3)) {
            h.assertTrue(actor.onGround() && actor.fallDistance == 0, "Only grounded, full-recovery swings are measured");
            Vec3 aim = target.position().subtract(trial.origin);
            actor.setYRot((float) -Math.toDegrees(Math.atan2(aim.x, aim.z)));
            actor.getRandom().setSeed(689L + trial.basics);
            actor.attack(target);
            trial.basics++;
        } else return;

        float damage = before - MonsterManager.modHealth(target);
        if (damage <= 0) h.assertTrue(false, (cleave ? "Q" : "Basic") + " failed at tick " + tick
                + " farm=" + trial.farm + " strength=" + data.firstGroupAttribute("strength")
                + " actor=" + actor.position() + " target=" + target.position()
                + " lineOfSight=" + actor.hasLineOfSight(target)
                + " range=" + actor.isWithinEntityInteractionRange(target, 0)
                + " initialWeapon=" + PlayerDataProvider.isInitialWeapon(actor.getMainHandItem())
                + " recovered=" + actor.getAttackStrengthScale(.5F) + " attackable=" + target.isAttackable()
                + " managed=" + MonsterManager.isMonster(target));
        trial.previousAction = tick;
        if (trial.farm) {
            int defeated = (int) trial.targets.stream().filter(mob -> !mob.isAlive()).count();
            while (trial.kills.size() < defeated) trial.kills.add(tick);
            if (defeated == trial.targets.size()) trial.finishedAt = tick;
        } else {
            trial.combo.add(damage);
            h.assertTrue(data.getLevel() == 1, "Damage comparison remains at hero level one");
            if (trial.combo.size() == 4) trial.finishedAt = tick;
        }
    }

    private static void verify(GameTestHelper h, List<Trial> trials) {
        var baseline = trials.getFirst().combo;
        for (int i = 0; i < BUILDS.size(); i++) {
            var build = BUILDS.get(i);
            var trial = trials.get(i);
            for (int swing = 0; swing < 3; swing++) {
                double expected = baseline.get(swing) * (65 + 15.0 * build.strength) / 65;
                h.assertTrue(Math.abs(trial.combo.get(swing) - expected) < .02,
                        "Matched-seed full attack scales only with Strength: " + build);
            }
            double expectedQ = (93 + 7.5 * build.strength) / 1.24;
            h.assertTrue(Math.abs(trial.combo.get(3) - expectedQ) < .02, "Actual Q at 24 defense: " + build);
            System.out.printf(Locale.ROOT, "[MYRIAD-CHIXIAO-V1] combo str=%d wis=%d A=[%.3f,%.3f,%.3f] Q=%.3f sum=%.3f endTick=%d%n",
                    build.strength, build.wisdom, trial.combo.get(0), trial.combo.get(1), trial.combo.get(2), trial.combo.get(3),
                    trial.combo.stream().mapToDouble(Float::doubleValue).sum(), trial.finishedAt);
        }
        var strength = trials.get(BUILDS.size());
        var wisdom = trials.getLast();
        for (Trial trial : List.of(strength, wisdom)) {
            System.out.printf(Locale.ROOT, "[MYRIAD-CHIXIAO-V1] camp %s paid=360 killTicks=%s clearTicks=%d A=%d Q=%d%n",
                    trial == strength ? "strength" : "wisdom", trial.kills, trial.finishedAt, trial.basics, trial.casts);
        }
        h.assertTrue(strength.finishedAt < wisdom.finishedAt, "Strength clears this first-spawn fixture faster at the same budget and cadence");
    }

    private static String cleaveId() {
        return FirstGroupSkills.node("chixiao", 2).getId();
    }
}
