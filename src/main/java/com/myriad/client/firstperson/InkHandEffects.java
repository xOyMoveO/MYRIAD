package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.skills.xuanchen.rework.InkCastAnimation.*;

final class InkHandEffects {
    private static final Vec3 U = new Vec3(1, 0, 0);
    private static final Vec3 V = new Vec3(0, 1, 0);

    private InkHandEffects() {}

    static List<Quad> geometry(InkHandMotion motion, double now, float equip) {
        var quads = new ArrayList<Quad>();
        var pose = motion.pose(now);
        int phase = motion.phase(now), ability = motion.ability(now);
        double age = motion.age(now);
        Vec3 left = InkHandRig.lowered(pose.palm(), equip).add(.025, .045, -.185);
        if (phase == WINDUP) {
            double rank = motion.progress(now);
            Vec3 between = left.lerp(InkHandRig.lowered(pose.grip(), equip), .34);
            mark(quads, between.add(0, .025, -.07), .12 + .065 * rank, .044, -.24, INK_HD_BRUSH, now, .40 + .55 * rank);
            mark(quads, between.add(.01, .048, -.073), .10 + .05 * rank, .04, -.21, INK_HD_GILT, now, .39 + .52 * rank);
        } else if (phase == RELEASE || phase == SELF || phase == BASIC) {
            double flash = envelope(age, ability == 6 ? 4.7 : 3.6);
            switch (ability) {
                case 2 -> {
                    mark(quads, left.add(0, .018, -.07), .146, .038, .45, INK_HD_CHAIN, age, flash);
                    mark(quads, left.add(.027, .032, -.072), .120, .025, .45, INK_HD_GILT, age + 2, flash * .82);
                }
                case 3 -> folded(quads, left, .105, phase == SELF ? -.46 : .42, age, flash);
                case 4 -> {
                    mark(quads, left.add(.02, .035, 0), .097, .074, .18, INK_HD_STAMP, age, flash * .9);
                    mark(quads, left.add(.026, .046, -.012), .118, .075, .22, INK_HD_GILT, age + 1, flash);
                }
                case 5 -> mark(quads, left.add(0,-.025,0), .128, .047, -.12, INK_HD_STAMP, age, flash);
                case 6 -> {
                    mark(quads, left, .105, .074, -.43, INK_HD_PAPER, age, flash);
                    mark(quads, InkHandRig.lowered(pose.grip(), equip).add(-.07, .09, -.19),
                            .18, .052, .22, INK_HD_FIBERS, age + 1, flash * .90);
                }
                case 7 -> cross(quads, left, age, flash);
                case 8 -> folded(quads, left.add(.025, .018, -.11 * age), .060, .36, age, flash);
                case 9 -> {
                    mark(quads, left.add(.015, -.012, -.05), .195, .040, .05, INK_HD_BRUSH, age, flash);
                    mark(quads, left.add(.026, .015, -.054), .138, .028, .10, INK_HD_FIBERS, age + 1, flash);
                    mark(quads, left.add(.087, .023, -.115), .069, .042, .28, INK_HD_SCRAPS, age + 1, flash * .83);
                }
                case 10 -> {
                    folded(quads, left.add(.018, .022, -.07), .085, -.10, age, flash);
                    mark(quads, left.add(.08, .045, -.084), .12, .095, .28, INK_HD_SCRAPS, age + 1, flash * .85);
                }
                default -> { }
            }
        }
        double activated = motion.flashAge(now);
        if (activated >= 0 && activated < 3.3) {
            Vec3 at = InkHandRig.lowered(pose.grip(), equip).add(-.035,.20,-.19);
            double fade = envelope(activated, 3.3);
            if (motion.flashAbility() == 5) mark(quads, at, .115, .077, .17, INK_HD_GILT, activated, fade);
            if (motion.flashAbility() == 7) cross(quads, at, activated, fade);
        }
        return quads;
    }

    private static double envelope(double age, double end) {
        return Math.clamp(Math.min(.33 + age, (end - age) / 1.7), 0, 1);
    }

    private static void folded(List<Quad> quads, Vec3 origin, double radius, double angle, double age, double brightness) {
        mark(quads, origin, radius * 1.75, radius * .50, angle, INK_HD_BRUSH, age + 1, brightness * .83);
        mark(quads, origin.add(.020, .009, -.009), radius, radius * .79, angle, INK_HD_PAPER, age, brightness);
        mark(quads, origin.add(.035, .018, -.014), radius * 1.04, radius * .83, angle, INK_HD_GILT, age + 2, brightness * .83);
    }

    private static void cross(List<Quad> quads, Vec3 origin, double age, double brightness) {
        mark(quads, origin, .115, .035, .52, INK_HD_BRUSH, age, brightness);
        mark(quads, origin.add(0,.008,-.007), .115, .031, -.66, INK_HD_GILT, age + 1, brightness * .9);
    }

    private static void mark(List<Quad> quads, Vec3 at, double w, double h, double tilt,
                             int texture, double phase, double alpha) {
        if (alpha < .01) return;
        Vec3 x = U.scale(Math.cos(tilt) * w).add(V.scale(Math.sin(tilt) * w));
        Vec3 y = V.scale(Math.cos(tilt) * h).subtract(U.scale(Math.sin(tilt) * h));
        Vec3 a = at.subtract(x).subtract(y), b = at.add(x).subtract(y);
        Vec3 c = at.add(x).add(y), d = at.subtract(x).add(y);
        float u0 = (Math.floorMod((int)Math.floor(phase * 1.15), 4) * 128 + .5f) / 512;
        float u1 = u0 + 127f / 512, v0 = .5f / 128, v1 = 1 - v0;
        quads.add(new Quad(a,b,c,d,0xFFF7E0,(float)alpha,texture,false,u0,u1,v0,v1));
        quads.add(new Quad(d,c,b,a,0xFFF7E0,(float)alpha,texture,true,u0,u1,v0,v1));
    }
}
