package com.myriad.game;

import com.myriad.combat.DamageCalc;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class TowerCoverChecks {
    static void run(GameTestHelper helper) {
        var suite = new ArenaFixtureChecks.Suite(helper.getLevel().getServer());
        try {
            suite.install();
            suite.begin("3v3");
            var level = suite.server.overworld();
            var match = GameManager.get();
            var invader = suite.players.getFirst();
            var defender = suite.opponent(invader);
            var towers = suite.units("TOWER", match.factionOf(defender));
            var tower = towers.stream().filter(t -> t.blockPosition().equals(
                    LaneSystem.towerPoint(match.factionOf(defender), LaneSystem.Lane.MID, 1))).findFirst().orElseThrow();
            // Move one generated tower within range of the fixture's configured brush.
            tower.setPos(336.5, 64, 43.5);
            var victim = new Vec3(336.5, 64, 52.5);
            invader.setPos(victim);
            for (var member : suite.players) if (member != invader) member.setPos(390, 64, 43);
            helper.assertTrue(!VisionSystem.visibleToTower(tower, match.factionOf(defender), invader), "grass conceals target from an isolated tower");
            defender.setPos(victim.add(1, 0, 0));
            helper.assertTrue(VisionSystem.visibleToTower(tower, match.factionOf(defender), invader), "tower sees a grass target through the allied hero");
            var wall = new BlockPos(336, 64, 48);
            long start = level.getGameTime();
            float original = invader.getHealth();
            for (var material : List.of(Blocks.STONE, Blocks.GLASS)) {
                fillColumn(level, wall, material);
                helper.assertTrue(TowerSight.visibleBodyPoint(tower, invader) == null, "complete cover uses collision shape of " + material);
                LaneSystem.onHeroAttacked(defender, invader);
                for (int i=0; i<31; i++) { suite.clock(++start); LaneSystem.tick(level); }
                helper.assertTrue(invader.getHealth() == original, "initial cover and aggro override both block tower damage");
            }
            fillColumn(level, wall, Blocks.AIR);
            for (int i=0; i<20; i++) { suite.clock(++start); LaneSystem.tick(level); }
            fillColumn(level, wall, Blocks.STONE);
            for (int i=0; i<20; i++) { suite.clock(++start); LaneSystem.tick(level); }
            helper.assertTrue(invader.getHealth() == original, "walking behind cover breaks the original windup");
            fillColumn(level, wall, Blocks.AIR);
            for (int i=0; i<28; i++) { suite.clock(++start); LaneSystem.tick(level); }
            helper.assertTrue(invader.getHealth() == original, "reacquisition gets a fresh windup");
            suite.clock(++start);
            LaneSystem.tick(level);
            helper.assertTrue(invader.getHealth() < original, "first unobstructed shot fires after 28 ticks");

            // Upper-body line of sight clears low geometry with the same sampler.
            for (Block partial : List.of(Blocks.OAK_FENCE, Blocks.STONE_STAIRS, Blocks.STONE_SLAB)) {
                level.setBlockAndUpdate(wall, partial.defaultBlockState());
                helper.assertTrue(TowerSight.visibleBodyPoint(tower, invader) != null, "target is visible over " + partial);
            }
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            verifyFeedback(helper, suite, tower, invader, defender, start);
            verifyMinionDefense(helper, suite, invader, defender);
            helper.succeed();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        } finally {
            GameManager.get().resetToLobby(suite.server, false);
            suite.removePlayers();
        }
    }

    private static void verifyFeedback(GameTestHelper h, ArenaFixtureChecks.Suite suite,
            net.minecraft.world.entity.Mob tower, net.minecraft.server.level.ServerPlayer invader,
            net.minecraft.server.level.ServerPlayer defender, long now) throws ReflectiveOperationException {
        var state = unitState(tower);
        var field = state.getClass().getDeclaredField("lastTowerHitFeedback");
        field.setAccessible(true);
        var visual = state.getClass().getDeclaredField("visualEntity");
        visual.setAccessible(true);
        var display = (net.minecraft.world.entity.Display.ItemDisplay) visual.get(state);
        var normal = display.getBrightnessOverride();
        float before = LaneSystem.health(tower);
        DamageCalc.dealBasicDamage(defender, tower, 100, DamageCalc.Kind.TRUE);
        h.assertTrue(LaneSystem.health(tower) == before && field.getLong(state) == Long.MIN_VALUE,
                "A friendly hit triggers neither tower damage nor feedback");
        DamageCalc.dealBasicDamage(invader, tower, 100, DamageCalc.Kind.TRUE);
        float after = LaneSystem.health(tower);
        h.assertTrue(after < before && field.getLong(state) == now
                && new net.minecraft.util.Brightness(15,15).equals(display.getBrightnessOverride()),
                "A legal hit both reduces actual tower HP and starts the flash");
        suite.clock(now + 1);
        DamageCalc.dealBasicDamage(invader, tower, 100, DamageCalc.Kind.TRUE);
        h.assertTrue(LaneSystem.health(tower) < after && field.getLong(state) == now,
                "Feedback limit does not suppress damage");
        suite.clock(now + 4);
        LaneSystem.tick(suite.server.overworld());
        h.assertTrue(java.util.Objects.equals(normal, display.getBrightnessOverride()),
                "The tower returns to normal light after four ticks");
    }

    private static void verifyMinionDefense(GameTestHelper h, ArenaFixtureChecks.Suite suite,
            net.minecraft.server.level.ServerPlayer attacker, net.minecraft.server.level.ServerPlayer victim) throws Exception {
        long wave = GameManager.get().getMatchStartTime() + GameManager.combatTempo().firstWaveTicks();
        suite.clock(wave);
        LaneSystem.tick(suite.server.overworld());
        var faction = GameManager.get().factionOf(victim);
        var ranged = suite.units("RANGED", faction).getFirst();
        var melee = suite.units("MELEE", faction).getFirst();
        var start = new Vec3(336.5, 64, 64.5);
        melee.setPos(start);
        ranged.setPos(start.add(.1, 0, 0));
        attacker.setPos(start.add(0, 0, 8.4));
        victim.setPos(attacker.position().add(1, 0, 0));
        var rangedState = unitState(ranged);
        var meleeState = unitState(melee);
        var defended = rangedState.getClass().getDeclaredField("defendedAgainst");
        defended.setAccessible(true);
        float victimHealth = victim.getHealth();
        DamageCalc.dealBasicDamage(attacker, victim, 30, DamageCalc.Kind.TRUE);
        h.assertTrue(victim.getHealth() < victimHealth && attacker.getUUID().equals(defended.get(rangedState))
                && defended.get(meleeState) == null, "One real hit at 8.4 blocks draws ranged minions only");
        attacker.setPos(start.add(0, 0, 9));
        suite.clock(wave + 1);
        LaneSystem.tick(suite.server.overworld());
        var chase = rangedState.getClass().getDeclaredField("chaseTarget");
        chase.setAccessible(true);
        h.assertTrue(defended.get(rangedState) == null && !attacker.getUUID().equals(chase.get(rangedState)),
                "Crossing 8.8 blocks drops the defense lock without reacquiring on ordinary scan");
    }

    private static Object unitState(net.minecraft.world.entity.Mob unit) throws ReflectiveOperationException {
        var units = LaneSystem.class.getDeclaredField("UNITS");
        units.setAccessible(true);
        return ((java.util.Map<?, ?>) units.get(null)).get(unit.getUUID());
    }

    private static void fillColumn(ServerLevel level, BlockPos base, Block block) {
        for (int y=0; y<10; y++) level.setBlockAndUpdate(base.above(y), block.defaultBlockState());
    }
}
