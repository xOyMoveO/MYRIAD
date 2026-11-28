package com.myriad.game;

import com.google.gson.Gson;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupSkills;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class SulanFenjueBalanceChecks {
    private static final Gson JSON = new Gson();
    private static final Map<LivingEntity, List<Hit>> WATCHED = new LinkedHashMap<>();
    private static boolean listening;
    private static final String WIND_PASSIVE = "g1_sulan_pa";
    private static final String SLASH = "g1_fenjue_n1";
    private static final String BURST = "g1_fenjue_mb";
    private static final String EMBER = "g1_fenjue_pa";

    private record Hit(long worldTick, String skill, boolean direct, int cast, float hp) {}
    private record Case(String name, ServerPlayer actor, LivingEntity target, List<Hit> hits) {}

    private final GameTestHelper h;
    private final long start;
    private final boolean mod = ModState.isEnabled(), training = TrainingMode.isEnabled();
    private final boolean freeMana = TrainingMode.isNoMana(), noCooldown = TrainingMode.isNoCooldown();
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final List<Case> cases = new ArrayList<>();
    private boolean done;

    private SulanFenjueBalanceChecks(GameTestHelper h) {
        this.h = h;
        start = h.getLevel().getGameTime();
    }

    static void run(GameTestHelper h) {
        new SulanFenjueBalanceChecks(h).start();
    }

    private void start() {
        if (!listening) {
            ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, base, taken, blocked) -> {
                var hits = WATCHED.get(target);
                String skill = AbilityRuntime.sourceSkill();
                if (hits != null && taken > 0 && !skill.isEmpty()) {
                    hits.add(new Hit(target.level().getGameTime(), skill,
                            AbilityRuntime.sourceDirect(), AbilityRuntime.sourceCast(), taken));
                }
            });
            listening = true;
        }
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        TrainingMode.setNoMana(false);
        TrainingMode.setNoCooldown(false);
        try {
            Case zero = fixture("sulan.walking_zero", CharacterType.SULAN, 0, 2, 0);
            Case walking = fixture("sulan.walking_prepared", CharacterType.SULAN, 1, 2, 0);
            Case motion = fixture("sulan.skill_motion_only", CharacterType.SULAN, 2, 8, 0);
            Case boundary = fixture("sulan.skill_motion_boundary", CharacterType.SULAN, 3, 8, 0);
            Case fire = fixture("fenjue.ember_refresh", CharacterType.FENJUE, 4, 5, 0);
            AbilityRuntime.tick(zero.actor());
            AbilityRuntime.tick(walking.actor());
            AbilityRuntime.tick(motion.actor());
            AbilityRuntime.tick(boundary.actor());
            cast(fire.actor(), 2);
            cast(motion.actor(), 2);
            for (int tick = 1; tick <= 140; tick++) {
                int elapsed = tick;
                h.runAfterDelay(tick, () -> {
                    if (done) return;
                    try {
                        if (elapsed <= 120) walk(walking.actor(), elapsed);
                        if (elapsed <= 119) walk(boundary.actor(), elapsed);
                        for (var test : cases) {
                            if (AbilityRuntime.moving(test.actor())) {
                                test.actor().move(MoverType.SELF, test.actor().getDeltaMovement());
                                test.actor().setOnGround(true);
                            }
                            AbilityRuntime.tick(test.actor());
                        }
                        if (elapsed == 35) cast(fire.actor(), 5);
                        if (elapsed == 9) {
                            h.assertTrue(!ready(motion.actor()), "A six-block skill displacement adds no passive charge");
                            basic(motion);
                            near(1000 - motion.target().getHealth(), 65, 5, "Dash then uncharged native melee");
                        }
                        if (elapsed == 119) {
                            h.assertTrue(!ready(walking.actor()) && !ready(boundary.actor()),
                                    "29.75 blocks of ordinary ground movement remain below the threshold");
                            long worldTick = h.getLevel().getGameTime();
                            ActionTime.apply(boundary.actor().getUUID(), boundary.actor().getUUID(),
                                    "sulan_balance_motion", .5, worldTick + 30);
                            cast(boundary.actor(), 2);
                        }
                        if (elapsed == 120) {
                            h.assertTrue(ready(walking.actor()) && !ready(zero.actor()),
                                    "Exactly 30 ordinary grounded blocks charge the first hit; zero preparation does not");
                            basic(zero);
                            basic(walking);
                            h.assertTrue(!ready(walking.actor()), "One effective native attack consumes the stored blade");
                            float zeroLoss = 1000 - zero.target().getHealth();
                            float preparedLoss = 1000 - walking.target().getHealth();
                            h.assertTrue(zeroLoss > 0, "The uncharged control has a genuine basic hit");
                            near(preparedLoss - zeroLoss, 45.5, .05, "Charged basic adds the rank-one formula exactly once");
                        }
                        if (elapsed == 136) {
                            h.assertTrue(!AbilityRuntime.moving(boundary.actor()) && !ready(boundary.actor()),
                                    "The final short step of a slowed skill dash must not credit the last .25 blocks");
                            boundary.actor().move(MoverType.PLAYER, new Vec3(.25, 0, 0));
                            boundary.actor().setOnGround(true);
                            AbilityRuntime.tick(boundary.actor());
                            h.assertTrue(ready(boundary.actor()), "A subsequent genuine quarter-block step completes the charge");
                            basic(boundary);
                        }
                        if (elapsed == 140) {
                            verifyBurn(fire);
                            for (var test : cases) trace(test);
                            cleanup();
                            h.succeed();
                        }
                    } catch (RuntimeException | Error failure) {
                        cleanup();
                        throw failure;
                    }
                });
            }
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    private Case fixture(String name, CharacterType role, int index, double targetDistance, int passiveIndex) {
        var pos = h.absolutePos(new BlockPos(7, 195 + 17 * index, 4));
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 9; z++) {
                BlockPos floor = pos.offset(x, -1, z);
                blocks.put(floor, h.getLevel().getBlockState(floor));
                h.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            }
        }
        var startPos = Vec3.atBottomCenterOf(pos);
        var player = ShuoChecks.player(h, startPos, new ArrayList<>());
        var data = PlayerDataProvider.get(player);
        data.setCharacterId(role.getId());
        data.setLevel(1);
        data.setFaction(Faction.GUARDIAN);
        PlayerDataProvider.applyChosenCharacter(player, data);
        data.setMana(700);
        data.setSkillLevel(FirstGroupSkills.node(role.getId(), passiveIndex).getId(), 1);
        player.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        player.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(30);
        player.setPos(startPos);
        player.setOnGround(true);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
        var target = ShuoChecks.target(h, startPos.add(0, role == CharacterType.FENJUE ? 1 : 0, targetDistance));
        target.getAttribute(ModAttributes.PHYSICAL_DEFENSE).setBaseValue(0);
        target.getAttribute(ModAttributes.MAGIC_RESISTANCE).setBaseValue(0);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var hits = new ArrayList<Hit>();
        WATCHED.put(target, hits);
        var test = new Case(name, player, target, hits);
        cases.add(test);
        return test;
    }

    private static void walk(ServerPlayer player, int tick) {
        player.move(MoverType.PLAYER, new Vec3(tick % 2 == 0 ? -.25 : .25, 0, 0));
        player.setOnGround(true);
    }

    private static boolean ready(ServerPlayer player) {
        return AbilityRuntime.context(player, FirstGroupSkills.byId(WIND_PASSIVE)).memory().count == 1;
    }

    private void cast(ServerPlayer player, int skillIndex) {
        var data = PlayerDataProvider.get(player);
        var skill = FirstGroupSkills.node(data.getCharacterId(), skillIndex);
        data.setSkillLevel(skill.getId(), 1);
        SkillSystem.cast(player, skill.slot());
        h.assertTrue(data.getCooldown(skill.getId()) > 0, "Successful production skill input " + skill.getId());
    }

    private void basic(Case test) {
        try {
            var recovery = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            recovery.setAccessible(true);
            recovery.setInt(test.actor(), 200);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        test.actor().setOnGround(true);
        test.actor().fallDistance = 0;
        test.actor().getRandom().setSeed(689);
        int first = test.hits().size();
        float before = test.target().getHealth();
        test.actor().attack(test.target());
        float total = before - test.target().getHealth();
        double attached = test.hits().subList(first, test.hits().size()).stream().mapToDouble(Hit::hp).sum();
        // The native event's raw argument still contains the pre-MYRIAD vanilla damage.
        test.hits().add(first, new Hit(h.getLevel().getGameTime(), "basic", true, 0, (float) (total - attached)));
    }

    private void verifyBurn(Case test) {
        var slashes = test.hits().stream().filter(hit -> hit.skill().equals(SLASH)).toList();
        var burst = test.hits().stream().filter(hit -> hit.skill().equals(BURST)).toList();
        var burn = test.hits().stream().filter(hit -> hit.skill().equals(EMBER)).toList();
        h.assertTrue(slashes.size() == 2 && burst.size() == 1 && burn.size() == 5,
                "Two direct slash cuts and later direct burst extend just one burning cadence: " + test.hits());
        h.assertTrue(slashes.getLast().worldTick() - slashes.getFirst().worldTick() == 4,
                "The original cut spacing is four action ticks at normal rate");
        int slashCast = slashes.getFirst().cast();
        h.assertTrue(slashCast > 0 && slashes.stream().allMatch(hit -> hit.direct() && hit.cast() == slashCast)
                && burst.getFirst().direct() && burst.getFirst().cast() != slashCast,
                "Both slash contacts share one direct cast, and the later refresh uses a separate direct ability");
        long first = slashes.getFirst().worldTick();
        for (int i = 0; i < burn.size(); i++) {
            Hit tick = burn.get(i);
            h.assertTrue(tick.worldTick() - first == 20L * (i + 1) && !tick.direct(),
                    "Refreshing preserves real-tick spacing, and burn never re-enters the direct hit path");
            near(tick.hp(), 1.8, .05, "Rank-one burn uses 6% of 30 S");
        }
    }

    private void trace(Case test) {
        System.out.println("MYRIAD_ROSTER_112 " + JSON.toJson(Map.of(
                "scenario", test.name(), "startWorldTick", start, "hits", test.hits(),
                "hpLoss", 1000 - test.target().getHealth())));
    }

    private void near(double actual, double expected, double tolerance, String label) {
        h.assertTrue(Math.abs(actual - expected) < tolerance, label + " expected=" + expected + " actual=" + actual);
    }

    private void cleanup() {
        done = true;
        for (var test : cases) {
            WATCHED.remove(test.target());
            test.target().discard();
            AbilityRuntime.clear(test.actor());
            h.getLevel().getServer().getPlayerList().getPlayersByUUID().remove(test.actor().getUUID());
            test.actor().discard();
        }
        blocks.forEach((at, state) -> h.getLevel().setBlockAndUpdate(at, state));
        TrainingMode.setNoMana(freeMana);
        TrainingMode.setNoCooldown(noCooldown);
        TrainingMode.setEnabled(training);
        ModState.setEnabled(mod);
    }
}
