package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.skills.sulan.rework.WindCastAnimation.*;

final class WindHandVfx {
    private static final Vec3 RIGHT = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final AttackTrailMesh.Style[] CUTS = {
            new AttackTrailMesh.Style(WIND_HD_CUT, WIND_HD_SWEEP_EDGE, 0x7CECC8, 0xF2FFFA, .92f, .016),
            new AttackTrailMesh.Style(WIND_HD_FLOW, WIND_HD_SWEEP_EDGE, 0x82FFD3, 0xE9FFFA, .90f, .013),
            new AttackTrailMesh.Style(WIND_HD_CUT, WIND_HD_SWEEP_EDGE, 0x8CFFDB, 0xF6FFFB, .97f, .022)
    };

    private WindHandVfx() {}

    static List<Quad> geometry(WindHandMotion motion, double now, float equip) {
        if (!motion.hasSkill(now)) {
            if (motion.combo() < 0) return List.of();
            return trail(motion, now, equip, motion.attackStart(), motion.recovery(),
                    WindHandMotion.CUT_FROM, WindHandMotion.CUT_TO, motion.combo());
        }
        var pose = motion.poseAt(now);
        int ability = motion.ability(now), phase = motion.signal(now);
        double start = motion.skillStart(), age = now - start;
        var quads = new ArrayList<Quad>();
        if (phase == CAST && (ability == 4 || ability == 5) || phase == DASH_STOP && ability == 6) {
            quads.addAll(trail(motion, now, equip, start, 6, .15, ability == 4 ? 1.45 : 1.2, ability == 4 ? 1 : 2));
        }
        Vec3 palm = WindHandRig.palm(pose.free(), equip);
        if (phase == CAST && ability == 3) {
            streak(quads, palm.add(.02, .04, -.19), new Vec3(.08, .23, 0).normalize(),
                    .21, .06, WIND_HD_FILAMENTS, age, envelope(age, .3, 3));
        }
        if (phase == CAST && ability == 4) {
            streak(quads, palm.add(0, .055, -.13), UP, .22, .13, WIND_HD_FLOW, age, envelope(age, .36, 3.9));
        }
        if (phase == CAST && ability == 7) {
            double alpha = envelope(age, .1, 3.45);
            streak(quads, palm.add(.12, .06, -.10), new Vec3(-.57, .82, 0), .18, .13, WIND_HD_RING_FLECKS, age, alpha);
            streak(quads, WindHandRig.palm(pose.grip(), equip).add(-.10, .045, -.10),
                    new Vec3(.9, .43, 0), .24, .085, WIND_HD_FLOW, age, alpha);
        }
        if (phase == WINDUP) {
            double progress = motion.chargeProgress(now);
            for (int side : new int[]{-1, 1}) {
                Vec3 at = new Vec3(side * (.94 - progress * .35), -.68, -1.38 - .15 * progress);
                streak(quads, at, new Vec3(side * .91, .4, 0), .31, .048,
                        WIND_HD_FILAMENTS, now + side, .17 + progress * .56);
            }
        }
        if (inMotion(phase)) {
            double power = Math.clamp(age / 1.2, 0, 1);
            for (int side : new int[]{-1, 1}) {
                Vec3 at = new Vec3(side * .94, -.43, -1.32);
                Vec3 lane = new Vec3(-side * .32, .08, -.943);
                streak(quads, at, lane, ability == 2 ? .63 : .78, .075, WIND_HD_FLOW, now + side, power * .73);
                streak(quads, at.add(side * .14, -.075, .03), lane, .71, .05,
                        WIND_HD_FILAMENTS, now + 2 * side, power * .68);
            }
            if (ability == 2) {
                var tip = WindHandRig.weapon(motion, now, equip).transformPosition(WindHandRig.vector(WindHandRig.TIP));
                streak(quads, new Vec3(tip.x, tip.y, tip.z).add(0, -.007, -.17),
                        pose.shaft().normalize(), .39, .055, WIND_HD_NEEDLE, now, power * .9);
            }
        }
        return quads;
    }

    private static List<Quad> trail(WindHandMotion motion, double now, float equip, double start,
                                    double duration, double from, double to, int cut) {
        var samples = AttackTrailMesh.samples(now, start, duration, from / duration, to / duration, tick -> {
            var weapon = WindHandRig.weapon(motion, tick, equip);
            Vec3 root = cut == 2 ? WindHandRig.EDGE_ROOT : WindHandRig.EDGE_ROOT.lerp(WindHandRig.TIP, cut == 0 ? .25 : .35);
            return AttackTrailMesh.slice(weapon, WindHandRig.vector(root), WindHandRig.vector(WindHandRig.TIP), tick);
        });
        var quads = new ArrayList<>(AttackTrailMesh.build(samples, now, CUTS[cut],
                cell(now * .94), cell(now * 1.3 + 2)));
        // Short, narrow scraps outside the core trail, carried by the same tip positions.
        for (int i = 3; i + 1 < samples.size() && quads.size() < AttackTrailMesh.MAX_QUADS; i += 4) {
            var sample = samples.get(i);
            Vec3 travel = samples.get(i + 1).outer().subtract(sample.outer());
            if (travel.lengthSqr() < 1e-6) continue;
            Vec3 out = sample.outer().subtract(sample.inner()).normalize();
            streak(quads, sample.outer().add(out.scale(.026)), travel.normalize(), .07, .019,
                    WIND_HD_FILAMENTS, now + i, .57 * (1 - (now - sample.tick()) / AttackTrailMesh.LIFE));
        }
        return quads;
    }

    private static double envelope(double age, double start, double end) {
        return Math.min(Math.clamp((age - start) / .63, 0, 1), Math.clamp((end - age) / 1.55, 0, 1));
    }

    private static AttackTrailMesh.Cell cell(double clock) {
        float start = (Math.floorMod((int)clock, 4) * 128 + .5f) / 512;
        return new AttackTrailMesh.Cell(start, start + 127f / 512, .5f / 128, 127.5f / 128);
    }

    private static void streak(List<Quad> quads, Vec3 at, Vec3 direction, double length, double width,
                               int material, double clock, double opacity) {
        if (opacity < .01 || quads.size() + 2 > AttackTrailMesh.MAX_QUADS) return;
        Vec3 along = direction.scale(length);
        Vec3 across = direction.cross(new Vec3(0, 0, 1));
        if (across.lengthSqr() < .01) across = Math.abs(direction.y) < .5 ? UP : RIGHT;
        across = across.normalize().scale(width);
        Vec3 a = at.subtract(along).subtract(across), b = at.add(along).subtract(across);
        Vec3 c = at.add(along).add(across), d = at.subtract(along).add(across);
        var uv = cell(clock);
        quads.add(new Quad(a, b, c, d, 0xF0FFFC, (float)opacity, material, false, uv.u0(), uv.u1(), uv.v0(), uv.v1()));
        quads.add(new Quad(d, c, b, a, 0xF0FFFC, (float)opacity, material, true, uv.u0(), uv.u1(), uv.v0(), uv.v1()));
    }
}
