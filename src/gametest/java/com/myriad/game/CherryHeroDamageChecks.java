package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.character.skills.shuo.ShuoCombat;
import com.myriad.combat.DamageCalc;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.PlayerHealthSystem;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Fires registered attacks at a same-level, unpurchased Tianxing through normal match damage rules. */
final class CherryHeroDamageChecks {
    private static final double MITIGATION = 100.0 / 145.0;
    private static final int END_TICK = 710;
    private static final int HERO_START = 7;

    enum Action {
        YIKE_BASIC("yike", 1, -1), SHELL("yike", 1, 2), NORMAL("luoxia", 1, -1),
        QUICK("luoxia", 1, 0), FULL("luoxia", 1, 1), MARK("luoxia", 1, 2),
        BURST("luoxia", 1, 4), LAST_SUN("luoxia", 1, 5), FAN("luoxia", 4, 6),
        SUNSET("luoxia", 4, 7), SECOND("luoxia", 4, 7), THIRD("luoxia", 4, 7), BASIC_SUN("luoxia", 1, 5), COMBO("luoxia", 2, 2), FOCUS("luoxia", 1, 5),
        YIKE_DENIAL("yike", 1, 3), BOMBARD("yike", 4, 6), BEAM("yike", 4, 7),
        KEHENG_BASIC("keheng", 1, -1), NEEDLE("keheng", 1, 2), BOUNDARY("keheng", 1, 4),
        FOLLOW("keheng", 1, 5), DOMAIN("keheng", 4, 6), PENETRATE("keheng", 4, 7),
        RIVET_BASIC("rivet", 1, -1), HAMMER("rivet", 1, 2), CHARGE("rivet", 4, 7),
        SHUO_BASIC("shuo", 1, -1), RETREAT("shuo", 1, 2), SWEEP("shuo", 1, 3),
        COUNTER("shuo", 1, 3), FLASH("shuo", 1, 4), TAILWIND("shuo", 1, 4),
        DOUBLE("shuo", 1, 5), LINE("shuo", 1, 5), EXECUTE("shuo", 4, 6),
        BREAK("shuo", 4, 6), MOON("shuo", 4, 7), FINISH("shuo", 4, 7),
        EDGE("shuo", 1, 0), GUARD_DRAW("shuo", 2, 1), ARMOR_MARK("yike", 4, 0),
        ECHO("keheng", 4, 0), WALL_HAMMER("rivet", 2, 1), WALL_CHARGE("rivet", 4, 7),
        FIXED_SHOT("yike", 2, 4), MOBILE_SHOT("yike", 2, 5);

        final String role;
        final int level, node;
        Action(String role, int level, int node) {
            this.role = role;
            this.level = level;
            this.node = node;
        }
    }

    record Build(int strength, int wisdom) {}
    private static final List<Build> BUILDS = List.of(new Build(0, 0), new Build(1, 0), new Build(0, 1));

    private static final class Trial {
        Action action;
        Build build;
        ServerPlayer actor, victim, ally;
        Vec3 origin;
        float previous;
        final List<Double> hits = new ArrayList<>();
    }

    private CherryHeroDamageChecks() {}

    static void run(GameTestHelper h) {
        var level = h.getLevel();
        var original = GameManager.snapshot();
        boolean enabled = ModState.isEnabled(), training = TrainingMode.isEnabled();
        var trials = new ArrayList<Trial>();
        var players = new ArrayList<ServerPlayer>();
        var waves = new ArrayList<CherryWaveDamageChecks>();
        var floors = new LinkedHashMap<BlockPos, BlockState>();
        Runnable cleanup = () -> {
            waves.forEach(CherryWaveDamageChecks::close);
            for (ServerPlayer p : players) {
                AbilityRuntime.clear(p);
                level.getServer().getPlayerList().getPlayersByUUID().remove(p.getUUID());
                if (level.getPlayerByUUID(p.getUUID()) == p) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
                else p.discard();
            }
            floors.forEach(level::setBlockAndUpdate);
            GameManager.restore(original, level.getServer());
            ModState.setEnabled(enabled);
            TrainingMode.setEnabled(training);
        };
        try {
            ModState.setEnabled(true);
            TrainingMode.setEnabled(false);
            GameManager.create();
            var manager = GameManager.get();
            manager.setServer(level.getServer());
            var phase = GameManager.class.getDeclaredField("phase");
            phase.setAccessible(true);
            phase.set(manager, GameManager.Phase.PLAYING);
            var field = GameManager.class.getDeclaredField("factions");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var seats = (Map<UUID, Faction>)field.get(manager);
            for (Action action : Action.values()) {
                for (Build build : BUILDS) {
                    var t = new Trial();
                    int i = trials.size();
                    t.action = action;
                    t.build = build;
                    BlockPos at = h.absolutePos(new BlockPos(6 + (i % 6)*24, 72 + (i / 6)*9, 8));
                    CombatChunks.prepare(level, at);
                    t.origin = Vec3.atBottomCenterOf(at);
                    t.actor = participant(h, players, seats, action.role, Faction.GUARDIAN, action.level, t.origin);
                    double distance = switch (action) {
                        case RIVET_BASIC, SHUO_BASIC, KEHENG_BASIC, HAMMER, FOLLOW, RETREAT, SWEEP, COUNTER, MOON, DOUBLE, TAILWIND, EDGE, GUARD_DRAW, WALL_HAMMER -> 2.2;
                        case FINISH -> 3;
                        case BREAK -> 8;
                        default -> 5;
                    };
                    t.victim = participant(h, players, seats, "tianxing", Faction.DESTRUCTION,
                            action.level, t.origin.add(0, 0, distance));
                    if (action.role.equals("shuo") || action.role.equals("rivet")) {
                        for (int z=-4; z<=16; z++) for (int x=-2; x<=2; x++) {
                            var floor = at.offset(x, -1, z);
                            floors.put(floor, level.getBlockState(floor));
                            level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                        }
                    }
                    if (action == Action.WALL_CHARGE || action == Action.WALL_HAMMER) {
                        int wall = action == Action.WALL_HAMMER ? 4 : 8;
                        for (int y=0; y<3; y++) {
                            var block = at.offset(0, y, wall);
                            floors.put(block, level.getBlockState(block));
                            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                        }
                    }
                    if (action == Action.ARMOR_MARK || action == Action.ECHO ||
                            action.node >= 0 && FirstGroupSkills.node(action.role, action.node).needsPoint()) {
                        BlockPos under = t.victim.blockPosition().below();
                        floors.put(under, level.getBlockState(under));
                        level.setBlockAndUpdate(under, Blocks.STONE.defaultBlockState());
                    }
                    if (action == Action.SECOND || action == Action.THIRD) {
                        var front = participant(h, players, seats, "chixiao", Faction.DESTRUCTION,
                                action.level, t.origin.add(0, 0, 2.2));
                        level.addNewPlayer(front);
                        if (action == Action.THIRD) {
                            var middle = participant(h, players, seats, "hanze", Faction.DESTRUCTION,
                                    action.level, t.origin.add(0, 0, 3.6));
                            level.addNewPlayer(middle);
                        }
                    }
                    level.addNewPlayer(t.victim);
                    buyBuild(h, t.actor, build);
                    if (action.node >= 0) learn(h, t.actor, action.node);
                    switch (action) {
                        case COMBO -> learn(h, t.actor, 5);
                        case GUARD_DRAW -> learn(h, t.actor, 3);
                        case WALL_HAMMER, FIXED_SHOT, MOBILE_SHOT -> learn(h, t.actor, 2);
                        case ECHO -> { learn(h, t.actor, 2); learn(h, t.actor, 4); learn(h, t.actor, 7); }
                        case ARMOR_MARK -> { learn(h, t.actor, 2); learn(h, t.actor, 3); learn(h, t.actor, 6); }
                        default -> {}
                    }
                    if (action == Action.FOCUS) {
                        t.ally = participant(h, players, seats, "yike", Faction.GUARDIAN,
                                action.level, t.origin.add(.20,0,0));
                        buyBuild(h, t.ally, build);
                        learn(h, t.ally, 2);
                    }
                    t.previous = t.victim.getHealth();
                    h.assertTrue(t.victim.getAttributeValue(ModAttributes.PHYSICAL_DEFENSE) == 45
                                    && t.victim.getAttributeValue(ModAttributes.MAGIC_RESISTANCE) == 45,
                            "Unpurchased Tianxing starts with 45 physical and magic defense");
                    trials.add(t);
                }
            }
            LaneSystem.tick(level);
            for (int index : new int[]{4, 5, 6}) {
                Vec3 start = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(index*18, 160, -40)));
                var farmer = participant(h, players, seats, "luoxia", Faction.GUARDIAN, index == 6 ? 4 : 1, start);
                learn(h, farmer, index);
                waves.add(new CherryWaveDamageChecks(h, farmer, index));
            }
        } catch (ReflectiveOperationException e) {
            cleanup.run();
            throw new IllegalStateException(e);
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
        boolean[] finished = {false};
        for (int i = 1; i <= END_TICK; i++) {
            int tick = i;
            h.runAfterDelay(i, () -> {
                if (finished[0]) return;
                try {
                    for (var wave : waves) wave.advance(tick);
                    int combatTick = tick-HERO_START+1;
                    if (combatTick >= 1 && combatTick <= 80) KehengTimeFields.tick(level);
                    if (combatTick >= 1 && combatTick <= 80) for (Trial t : trials) {
                        if (combatTick == 1) {
                            h.assertTrue(level.getEntitiesOfClass(ServerPlayer.class, t.victim.getBoundingBox()).contains(t.victim),
                                    "Enemy hero is registered before the first attack: " + t.action);
                            t.previous = t.victim.getHealth();
                        }
                        t.actor.setOnGround(true);
                        if (AbilityRuntime.moving(t.actor))
                            t.actor.move(net.minecraft.world.entity.MoverType.SELF, t.actor.getDeltaMovement());
                        if (com.myriad.character.skills.rivet.RivetPush.autonomousMovementBlocked(t.victim))
                            t.victim.move(net.minecraft.world.entity.MoverType.SELF, t.victim.getDeltaMovement());
                        fire(t, combatTick);
                        AbilityRuntime.tick(t.actor);
                        if (t.ally != null) AbilityRuntime.tick(t.ally);
                        float hp = t.victim.getHealth();
                        if (t.previous - hp > .01) t.hits.add((double)(t.previous - hp));
                        t.previous = hp;
                    }
                    if (combatTick == 81) {
                        for (Trial t : trials) {
                            AbilityRuntime.clear(t.actor);
                            if (t.ally != null) AbilityRuntime.clear(t.ally);
                            level.removePlayerImmediately(t.victim, Entity.RemovalReason.DISCARDED);
                        }
                    }
                    if (tick == END_TICK) {
                        for (Trial t : trials) System.out.printf(Locale.ROOT,
                                "[CHERRY-HERO-HP] %s S%d W%d L%d hits=%s%n",
                                t.action, t.build.strength, t.build.wisdom, t.action.level, t.hits);
                        for (Trial t : trials) verify(h, t);
                        for (var wave : waves) wave.verify(h);
                        finished[0] = true;
                        cleanup.run();
                        h.succeed();
                    }
                } catch (RuntimeException | Error e) {
                    finished[0] = true;
                    cleanup.run();
                    throw e;
                }
            });
        }
    }

    private static ServerPlayer participant(GameTestHelper h, List<ServerPlayer> players, Map<UUID, Faction> seats,
                                             String role, Faction side, int level, Vec3 at) {
        ServerPlayer p = ShuoChecks.player(h, at, new ArrayList<>());
        players.add(p);
        var data = PlayerDataProvider.get(p);
        data.setCharacterId(role);
        data.setLevel(level);
        data.setSkillPoints(level);
        data.setFaction(side);
        seats.put(p.getUUID(), side);
        PlayerDataProvider.applyChosenCharacter(p, data);
        PlayerHealthSystem.fill(p);
        p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
        p.setOnGround(true);
        h.getLevel().getServer().getPlayerList().getPlayersByUUID().put(p.getUUID(), p);
        return p;
    }

    private static void buyBuild(GameTestHelper h, ServerPlayer p, Build build) {
        var data = PlayerDataProvider.get(p);
        data.grantOpeningPurse();
        if (build.strength == 0 && build.wisdom == 0) return;
        var fountains = CombatRegressionTests.laneFixtureLayout().fountains;
        var faction = data.getFaction();
        var previous = fountains.put(faction, p.blockPosition());
        try {
            String attribute = build.strength == 1 ? "strength" : "wisdom";
            h.assertTrue(FirstGroupGrowth.buy(p, data, attribute) && data.getGold() == 140
                            && data.firstGroupAttribute(attribute) == 1,
                    "Purchase the first " + attribute + " at the home shop with 500 opening gold");
        } finally {
            if (previous == null) fountains.remove(faction);
            else fountains.put(faction, previous);
        }
    }

    private static void learn(GameTestHelper h, ServerPlayer p, int index) {
        var d = PlayerDataProvider.get(p);
        var skill = FirstGroupSkills.node(d.getCharacterId(), index);
        PlayerDataProvider.upgradeSkill(p, skill.getId());
        h.assertTrue(d.skillLevel(skill.getId()) == 1, "Legal first rank: " + skill.getId());
    }

    private static void fire(Trial t, int tick) {
        ServerPlayer p = t.actor;
        if (tick == 1) {
            switch (t.action) {
                case SHUO_BASIC, KEHENG_BASIC, RIVET_BASIC -> DamageGrowthChecks.swing(p, t.victim);
                case EDGE -> {}
                case GUARD_DRAW -> SkillSystem.cast(p, 1);
                case WALL_HAMMER, ECHO, ARMOR_MARK -> SkillSystem.cast(p, 0);
                case RETREAT, SWEEP, TAILWIND, DOUBLE, BREAK, FINISH -> {
                    ShuoCombat.basic(p);
                    SkillSystem.cast(p, FirstGroupSkills.node(t.action.role, t.action.node).slot());
                }
                case YIKE_DENIAL, BOUNDARY, DOMAIN, BOMBARD -> groundCast(p, FirstGroupSkills.node(t.action.role, t.action.node).slot());
                case YIKE_BASIC -> BasicAttackSystem.tryAttack(p, 0);
                case BASIC_SUN, NORMAL, QUICK, FULL -> BasicAttackSystem.tryAttack(p, LuoxiaBow.PRESS);
                default -> SkillSystem.cast(p, FirstGroupSkills.node(t.action.role, t.action.node).slot());
            }
        }
        if (t.action == Action.EDGE && tick == 60) DamageGrowthChecks.swing(p, t.victim);
        if (t.action == Action.GUARD_DRAW && tick == 12) DamageGrowthChecks.swing(p, t.victim);
        if (t.action == Action.WALL_HAMMER && tick == 28) {
            p.setPos(t.victim.position().add(0,0,-2));
            DamageGrowthChecks.swing(p,t.victim);
        }
        if (t.action == Action.ARMOR_MARK && tick == 18) groundCast(p,1);
        if (t.action == Action.ARMOR_MARK && tick == 37) groundCast(p,3);
        if (t.action == Action.ECHO && tick == 17) groundCast(p,2);
        if (t.action == Action.ECHO && tick == 35) SkillSystem.cast(p,3);
        if ((t.action == Action.FIXED_SHOT || t.action == Action.MOBILE_SHOT) && tick == 16) SkillSystem.cast(p,0);
        if ((t.action == Action.COUNTER || t.action == Action.GUARD_DRAW) && tick == 3)
            DamageCalc.dealDamage(t.victim, p, 40, DamageCalc.Kind.PHYSICAL);
        if (t.action == Action.COUNTER && tick == 5) SkillSystem.cast(p, 1);
        if (t.action == Action.MOON && (tick == 15 || tick == 30)) SkillSystem.cast(p, 3);
        if ((t.action == Action.NORMAL || t.action == Action.QUICK || t.action == Action.BASIC_SUN) && tick == 14
                || t.action == Action.FULL && tick == 23) BasicAttackSystem.tryAttack(p, LuoxiaBow.RELEASE);
        if (t.action == Action.QUICK && tick == 21) BasicAttackSystem.tryAttack(p, LuoxiaBow.PRESS);
        if (t.action == Action.BASIC_SUN && tick == 28) SkillSystem.cast(p, 2);
        if (t.action == Action.COMBO && tick == 18) SkillSystem.cast(p, 2);
        if (t.action == Action.COMBO && tick == 35) BasicAttackSystem.tryAttack(p, LuoxiaBow.PRESS);
        if (t.action == Action.COMBO && tick == 49) BasicAttackSystem.tryAttack(p, LuoxiaBow.RELEASE);
        if (t.action == Action.FOCUS && tick == 17) SkillSystem.cast(t.ally, 0);
        if (t.action == Action.QUICK && tick == 35) BasicAttackSystem.tryAttack(p, LuoxiaBow.RELEASE);
    }

    private static void groundCast(ServerPlayer p, int slot) {
        p.setXRot((float)Math.toDegrees(Math.atan2(p.getEyeHeight(), 5)));
        SkillSystem.cast(p, slot);
        AbilityRuntime.confirm(p);
        p.setXRot(0);
    }

    private static double[] repeated(double value, int count) {
        double[] hits = new double[count];
        java.util.Arrays.fill(hits, value);
        return hits;
    }

    private static void verify(GameTestHelper h, Trial t) {
        double attack = (t.action.role.equals("luoxia") ? 100 : 65) + 15*t.build.strength;
        double s = t.build.strength, w = t.build.wisdom;
        double[] expected = switch (t.action) {
            case FIXED_SHOT -> new double[]{(111.5+11*w)*.65};
            case MOBILE_SHOT -> new double[]{(111.5+11*w)*.55};
            case EDGE -> new double[]{attack+52+6*s};
            case GUARD_DRAW -> new double[]{attack+70+7.5*s};
            case ARMOR_MARK -> new double[]{111.5+11*w,80.5+7*w,284.5+23*w+46+4*w};
            case ECHO -> new double[]{95+10*w,93.5+9*w,370.5+27*w+57.5+5*w};
            case WALL_HAMMER -> new double[]{90+75.0/14*s,35+15.0/7*s,attack+30+3.75*s};
            case WALL_CHARGE -> new double[]{170+9*s,45+3*s};
            case YIKE_DENIAL -> new double[]{80.5+7*w};
            case BEAM -> repeated(77.5+5*w, 6);
            case BOMBARD -> new double[]{284.5+23*w};
            case NEEDLE -> new double[]{95+10*w};
            case BOUNDARY -> new double[]{93.5+9*w};
            case FOLLOW -> new double[]{105+10*w};
            case DOMAIN -> repeated(27.6+2.4*w, 6);
            case PENETRATE -> new double[]{370.5+27*w};
            case HAMMER -> new double[]{90+75.0/14*s};
            case CHARGE -> new double[]{170+9*s};
            case RETREAT -> new double[]{40.2+4.5*s,26.8+3*s};
            case SWEEP -> new double[]{78.5+7.5*s};
            case COUNTER -> new double[]{62+7.5*s};
            case FLASH -> new double[]{71.4+4.5*s,47.6+3*s};
            case TAILWIND -> new double[]{81.5+7.5*s};
            case LINE -> new double[]{81.3+4.5*s,54.2+3*s};
            case DOUBLE -> new double[]{55.5+3.75*s,55.5+3.75*s};
            case EXECUTE -> new double[]{368+12*s};
            case BREAK -> new double[]{276+12*s};
            case MOON -> new double[]{118+24.0/7*s,118+24.0/7*s,187+36.0/7*s};
            case FINISH -> new double[]{320.5+12*s};
            case SHUO_BASIC, RIVET_BASIC, KEHENG_BASIC, YIKE_BASIC, NORMAL -> new double[]{attack};
            case QUICK -> new double[]{attack, attack * .90};
            case FULL -> new double[]{attack * 1.21};
            case SHELL -> new double[]{111.5 + 11*w};
            case MARK -> new double[]{52 + (25.0/6)*s};
            case BURST -> new double[]{50+3*s, 50+3*s, 25+1.5*s};
            case LAST_SUN -> new double[]{150+7.5*s};
            case FAN -> new double[]{70+4*s, 70+4*s, 70+4*s};
            case SUNSET -> new double[]{175+12*s};
            case SECOND -> new double[]{(175+12*s)*180/260};
            case THIRD -> new double[]{(175+12*s)*125/260};
            case BASIC_SUN -> new double[]{attack,150+7.5*s};
            case COMBO -> new double[]{52+(25.0/6)*s, 150+7.5*s+25+(10.0/3)*s, attack};
            case FOCUS -> new double[]{150+7.5*s, 111.5+11*w};
        };
        h.assertTrue(t.hits.size() == expected.length,
                t.action + " expected " + expected.length + " hits, observed " + t.hits);
        for (int i=0; i<expected.length; i++) {
            h.assertTrue(Math.abs(expected[i]*MITIGATION - t.hits.get(i)) < ((t.action == Action.SHUO_BASIC || t.action == Action.RIVET_BASIC || t.action == Action.EDGE || t.action == Action.GUARD_DRAW || t.action == Action.WALL_HAMMER && i == 2) ? expected[i]*MITIGATION*.05 + .04 : .04),
                    t.action + " hit " + i + " expected " + expected[i]*MITIGATION + " got " + t.hits);
        }
    }
}
