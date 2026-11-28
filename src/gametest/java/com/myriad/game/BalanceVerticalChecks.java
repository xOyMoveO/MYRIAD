package com.myriad.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LastSun;
import com.myriad.character.skills.yike.YikeStance;
import com.myriad.character.skills.yike.YikeVfx;
import com.myriad.character.skills.yike.YikeVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Uses real skill input, player ticks, muzzle/release events and target health in the test server. */
final class BalanceVerticalChecks {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final String LUOXIA = "luoxia_last_sun_range";
    private static final String YIKE = "yike_rainbow_shell_stances";
    private static final String LUOXIA_CASE = "range_probes";
    private static final String Q = "yike_rainbow_shell";
    private static final String E = "luoxia_last_sun";
    private static final double[] ARROW_PROBES = {27.5, 28.5, 31.5, 32.5};
    private static final int RANGE_OBSERVATION_END = 30;
    private static final int YIKE_Q_WINDUP = 5;
    private static final int TEST_END = 139;
    private static final float STARTING_MANA = 700;
    private static final float TARGET_HEALTH = 1000;

    static void run(GameTestHelper helper) {
        boolean enabled = ModState.isEnabled();
        boolean training = TrainingMode.isEnabled();
        boolean noCooldown = TrainingMode.isNoCooldown();
        boolean noMana = TrainingMode.isNoMana();
        boolean noGoldCost = TrainingMode.isNoGoldCost();
        ModState.setEnabled(true);
        TrainingMode.setEnabled(true);
        TrainingMode.setNoCooldown(false);
        TrainingMode.setNoMana(false);

        var fixtures = new ArrayList<Fixture>();
        Runnable cleanup = () -> {
            fixtures.forEach(Fixture::close);
            ModState.setEnabled(enabled);
            TrainingMode.setEnabled(training);
            TrainingMode.setNoCooldown(noCooldown);
            TrainingMode.setNoMana(noMana);
            TrainingMode.setNoGoldCost(noGoldCost);
        };
        try {
            double expectedRange = Double.parseDouble(System.getProperty("myriad.balance.luoxia.expected_range", "28"));
            helper.assertTrue(expectedRange == 28 || expectedRange == 32, "Expected range must match a fixed probe boundary");
            long start = helper.getLevel().getGameTime();
            var arrows = new ArrayList<ArrowProbe>();
            for (double distance : ARROW_PROBES) {
                var fixture = new Fixture(helper, CharacterType.LUOXIA, fixtures.size(), distance);
                fixtures.add(fixture);
                arrows.add(new ArrowProbe(helper, fixture, start, distance, expectedRange));
            }
            var shells = new ArrayList<ShellProbe>();
            for (var stance : Stance.values()) {
                var fixture = new Fixture(helper, CharacterType.YIKE, fixtures.size(), 5);
                fixtures.add(fixture);
                shells.add(new ShellProbe(helper, fixture, start, stance));
            }
            boolean[] done = {false};
            for (int step = 1; step <= TEST_END; step++) {
                final int tick = step;
                helper.runAfterDelay(tick, () -> {
                    if (done[0]) return;
                    try {
                        helper.assertTrue(helper.getLevel().getGameTime() - start == tick, "One step per real game tick");
                        if (tick <= RANGE_OBSERVATION_END) for (var arrow : arrows) arrow.tick(tick);
                        for (var shell : shells) shell.tick(tick);
                        if (tick == TEST_END) {
                            done[0] = true;
                            cleanup.run();
                            helper.succeed();
                        }
                    } catch (RuntimeException | Error failure) {
                        done[0] = true;
                        cleanup.run();
                        throw failure;
                    }
                });
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static final class Fixture {
        final ServerPlayer player;
        final PlayerData data;
        final Villager target;
        final Vec3 origin;
        final Vec3 targetPosition;
        final List<CustomPacketPayload> signals = new ArrayList<>();

        Fixture(GameTestHelper helper, CharacterType role, int lane, double distance) {
            var level = helper.getLevel();
            // Separate lanes vertically, above the fixture structure and outside each other's flight bounds.
            var at = helper.absolutePos(new BlockPos(4, 26 + lane * 5, 4));
            origin = Vec3.atBottomCenterOf(at);
            targetPosition = origin.add(0, .75, distance);
            CombatChunks.prepare(level, at);
            CombatChunks.prepare(level, BlockPos.containing(targetPosition));

            player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
            var link = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(link);
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), link, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket custom) {
                        if (custom.payload() instanceof LuoxiaBowPosePayload pose
                                && pose.owner().equals(player.getUUID())
                                && pose.phase() == LuoxiaBowPosePayload.Phase.RELEASE) signals.add(pose);
                        if (custom.payload() instanceof AbilityVfxPayload visual && visual.owner() == player.getId()
                                && visual.theme() == YikeVfx.THEME && YikeVfxGeometry.signal(visual) == YikeVfx.MUZZLE
                                && visual.kind() == AbilityVfxGeometry.BURST) signals.add(visual);
                    }
                }
            };
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            level.getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
            level.players().add(player);
            player.setPos(origin);
            player.setNoGravity(true);
            player.setYRot(0);
            player.setXRot(0);
            data = PlayerDataProvider.get(player);
            data.setCharacterId(role.getId());
            data.setLevel(1);
            PlayerDataProvider.applyChosenCharacter(player, data);
            player.getAttribute(ModAttributes.MANA_REGEN).setBaseValue(0);
            data.setMana(STARTING_MANA);

            target = helper.spawn(EntityTypes.VILLAGER, helper.relativePos(BlockPos.containing(targetPosition)));
            target.setPos(targetPosition);
            target.setNoAi(true);
            target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HEALTH);
            target.setHealth(TARGET_HEALTH);
        }

        void tick() {
            if (player.level().getGameTime() % 20 == 0) {
                CombatChunks.hold(player.level(), BlockPos.containing(origin));
                CombatChunks.hold(player.level(), BlockPos.containing(targetPosition));
            }
            target.setPos(targetPosition);
            target.setDeltaMovement(Vec3.ZERO);
            player.doTick();
            // A mock player has no native connection in the network tick list. Run its gameplay hook once.
            PlayerDataProvider.onPlayerTick(player);
        }

        void close() {
            AbilityRuntime.clear(player);
            player.level().players().remove(player);
            player.level().getServer().getPlayerList().getPlayersByUUID().remove(player.getUUID());
            target.discard();
            player.discard();
        }
    }

    private static final class ArrowProbe {
        final GameTestHelper helper;
        final Fixture fixture;
        final long start;
        final double distance;
        final boolean expectedHit;
        boolean hit;

        ArrowProbe(GameTestHelper helper, Fixture fixture, long start, double distance, double expectedRange) {
            this.helper = helper;
            this.fixture = fixture;
            this.start = start;
            this.distance = distance;
            double width = fixture.target.getBbWidth();
            expectedHit = fixture.target.getBoundingBox().minZ - fixture.origin.z < expectedRange;
            helper.assertTrue(Math.abs(width - .6) < .00001, "Last Sun probe target width " + width);
            trace(LUOXIA, LUOXIA_CASE, "initial", 0, "centerDistanceBlocks", distance, "targetWidthBlocks", width,
                    "level", fixture.data.getLevel(), "rank", 1, "mana", fixture.data.getMana(),
                    "rangeBlocks", LastSun.RANGE, "observationEndTick", RANGE_OBSERVATION_END);
            fixture.data.setSkillLevel(E, 1);
            SkillSystem.cast(fixture.player, CharacterType.SLOT_E);
            boolean accepted = E.equals(AbilityRuntime.preparingSkill(fixture.player));
            helper.assertTrue(accepted, "Last Sun input accepted at " + distance);
            trace(LUOXIA, LUOXIA_CASE, "cast_requested", 0, "centerDistanceBlocks", distance, "accepted", accepted);
        }

        void tick(int tick) {
            fixture.tick();
            if (tick == 10) {
                helper.assertTrue(fixture.signals.size() == 1
                        && fixture.signals.getFirst() instanceof LuoxiaBowPosePayload pose
                        && pose.skill() == 5 && pose.tick() - start == tick, "Last Sun releases at real tick 10");
                trace(LUOXIA, LUOXIA_CASE, "arrow_released", tick, "centerDistanceBlocks", distance);
            }
            if (!hit && fixture.target.getHealth() < TARGET_HEALTH) {
                hit = true;
                trace(LUOXIA, LUOXIA_CASE, "range_probe", tick, "centerDistanceBlocks", distance,
                        "hit", true, "damage", TARGET_HEALTH - fixture.target.getHealth());
            }
            if (tick == RANGE_OBSERVATION_END) {
                if (!hit) trace(LUOXIA, LUOXIA_CASE, "range_probe", tick, "centerDistanceBlocks", distance,
                        "hit", false, "damage", 0);
                helper.assertTrue(hit == expectedHit, "Last Sun geometry at " + distance + " blocks expected hit="
                        + expectedHit + ", actual=" + hit);
            }
        }
    }

    private enum Stance {
        NORMAL("normal", -1, 0, 0, 0, 0),
        FIXED("fixed", 4, 9, 5, 70, 22),
        MOBILE("mobile", 5, 7, 4, 65, 27);

        final String id;
        final int skillIndex;
        final int deployTicks;
        final int ammo;
        final int deploymentMana;
        final int muzzleInterval;

        Stance(String id, int skillIndex, int deployTicks, int ammo, int deploymentMana, int muzzleInterval) {
            this.id = id;
            this.skillIndex = skillIndex;
            this.deployTicks = deployTicks;
            this.ammo = ammo;
            this.deploymentMana = deploymentMana;
            this.muzzleInterval = muzzleInterval;
        }

        boolean artillery() { return this != NORMAL; }
    }

    private static final class ShellProbe {
        final GameTestHelper helper;
        final Fixture fixture;
        final long start;
        final Stance stance;
        final int firstMuzzle;
        final int secondRequest;
        final int secondMuzzle;
        int hits;
        float previousHealth = TARGET_HEALTH;

        ShellProbe(GameTestHelper helper, Fixture fixture, long start, Stance stance) {
            this.helper = helper;
            this.fixture = fixture;
            this.start = start;
            this.stance = stance;
            firstMuzzle = (stance.artillery() ? stance.deployTicks + 1 : 0) + YIKE_Q_WINDUP;
            secondRequest = firstMuzzle + (stance.artillery() ? stance.muzzleInterval - YIKE_Q_WINDUP : 120);
            secondMuzzle = secondRequest + YIKE_Q_WINDUP;
            fixture.data.setSkillLevel(Q, 1);
            double cdr = fixture.player.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION);
            double actionMultiplier = ActionTime.multiplier(fixture.player.getUUID(), start);
            int boughtPoints = FirstGroupGrowth.ATTRIBUTES.stream().mapToInt(fixture.data::firstGroupAttribute).sum();
            helper.assertTrue(cdr == 0 && actionMultiplier == 1 && boughtPoints == 0 && cooldown() == 0
                    && fixture.data.getMana() == STARTING_MANA, "Fixed Q initial state");
            trace(YIKE, stance.id, "initial", 0, "level", fixture.data.getLevel(), "rank", fixture.data.skillLevel(Q),
                    "mana", fixture.data.getMana(), "cooldownTicks", cooldown(), "ammo", null,
                    "stanceRank", stance.artillery() ? 1 : null, "boughtPoints", boughtPoints,
                    "cooldownReductionPct", cdr, "noManaCost", TrainingMode.isNoMana(fixture.player),
                    "noCooldown", TrainingMode.isNoCooldown(fixture.player), "actionMultiplier", actionMultiplier);
            if (stance.artillery()) {
                String skill = FirstGroupSkills.node("yike", stance.skillIndex).getId();
                fixture.data.setSkillLevel(skill, 1);
                SkillSystem.cast(fixture.player, CharacterType.SLOT_E);
                boolean accepted = skill.equals(AbilityRuntime.preparingSkill(fixture.player));
                helper.assertTrue(accepted, stance.id + " E deploy request");
                trace(YIKE, stance.id, "stance_requested", 0, "accepted", accepted);
            } else {
                request(0, 1, true);
            }
        }

        void tick(int tick) {
            fixture.tick();
            var player = fixture.player;
            if (stance.artillery() && tick <= stance.deployTicks) {
                helper.assertTrue(YikeStance.active(player) == (tick == stance.deployTicks), stance.id + " exact deployment tick");
                if (tick == stance.deployTicks) {
                    helper.assertTrue(YikeStance.mobile(player) == (stance == Stance.MOBILE)
                            && ammo() == stance.ammo && fixture.data.getMana() == STARTING_MANA - stance.deploymentMana,
                            stance.id + " deployment ammo and mana");
                    trace(YIKE, stance.id, "stance_entered", tick, "ammo", ammo(), "mana", fixture.data.getMana());
                }
            }
            if (stance.artillery() && tick == stance.deployTicks + 1) request(tick, 1, true);
            if (tick == secondRequest - 1) {
                helper.assertTrue(stance.artillery() ? YikeStance.nextShotTicks(player) == YIKE_Q_WINDUP + 1 : cooldown() == 1,
                        stance.id + " one real tick before the next legal request");
                request(tick, 2, false);
            }
            if (tick == secondRequest) {
                helper.assertTrue(stance.artillery() ? YikeStance.nextShotTicks(player) == YIKE_Q_WINDUP : cooldown() == 0,
                        stance.id + " exact next legal request tick");
                request(tick, 2, true);
            }

            int expectedShots = tick < firstMuzzle ? 0 : tick < secondMuzzle ? 1 : 2;
            helper.assertTrue(fixture.signals.size() == expectedShots, stance.id + " muzzle count at tick " + tick
                    + " expected=" + expectedShots + " actual=" + fixture.signals.size());
            if (tick == firstMuzzle || tick == secondMuzzle) muzzle(tick, expectedShots);
            if (stance.artillery() && tick == secondMuzzle + 1) {
                SkillSystem.cast(player, CharacterType.SLOT_E);
                helper.assertTrue(!YikeStance.active(player) && "manual".equals(YikeStance.exitReason(player))
                        && ammo() == 0 && cooldown() == 120, stance.id + " manual exit restores the normal Q cooldown");
                trace(YIKE, stance.id, "stance_exited", tick, "reason", YikeStance.exitReason(player),
                        "cooldownTicks", cooldown());
            }

            float health = fixture.target.getHealth();
            if (health < previousHealth) {
                hits++;
                helper.assertTrue(hits <= expectedShots, stance.id + " only an actual shot damages its target");
                trace(YIKE, stance.id, "hit", tick, "shot", hits, "damage", previousHealth - health);
                previousHealth = health;
            }
            if (tick == TEST_END) helper.assertTrue(hits == 2, stance.id + " both actual shots damaged the target");
        }

        private void request(int tick, int shot, boolean expectedAcceptance) {
            float beforeMana = fixture.data.getMana();
            Integer beforeAmmo = ammo();
            int beforeCooldown = cooldown();
            SkillSystem.cast(fixture.player, CharacterType.SLOT_Q);
            boolean accepted = Q.equals(AbilityRuntime.preparingSkill(fixture.player));
            trace(YIKE, stance.id, "cast_requested", tick, "accepted", accepted, "shot", shot);
            helper.assertTrue(accepted == expectedAcceptance && fixture.data.getMana() == beforeMana && cooldown() == beforeCooldown
                    && Objects.equals(beforeAmmo, ammo()), stance.id + " request is unresolved/unpaid at " + tick);
        }

        private void muzzle(int tick, int shot) {
            var signal = (AbilityVfxPayload) fixture.signals.get(shot - 1);
            int nextShotTicks = YikeStance.nextShotTicks(fixture.player);
            trace(YIKE, stance.id, "muzzle", tick, "shot", shot, "ammo", ammo(), "mana", fixture.data.getMana(),
                    "cooldownTicks", cooldown(), "nextShotTicks", nextShotTicks);
            helper.assertTrue(signal.started() - start == tick && signal.variant() == 2
                    && fixture.data.getMana() == STARTING_MANA - stance.deploymentMana - 35 * shot
                    && (stance.artillery() ? cooldown() == 0 && ammo() == stance.ammo - shot
                            && nextShotTicks == stance.muzzleInterval : cooldown() == 120),
                    stance.id + " real muzzle consumes one shell and 35 mana at " + tick);
        }

        private int cooldown() { return fixture.data.getCooldown(Q); }
        private Integer ammo() { return stance.artillery() ? YikeStance.shots(fixture.player) : null; }
    }

    private static void trace(String scenario, String caseId, String event, int tick, Object... fields) {
        var row = new LinkedHashMap<String, Object>();
        row.put("schemaVersion", 1);
        row.put("scenarioId", scenario);
        row.put("caseId", caseId);
        row.put("event", event);
        row.put("tick", tick);
        row.put("timeUnit", "real_tick");
        for (int i = 0; i < fields.length; i += 2) row.put((String) fields[i], fields[i + 1]);
        System.out.println("BALANCE_VERTICAL " + JSON.toJson(row));
    }
}
