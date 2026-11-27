package com.myriad.client.firstperson;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.skills.sulan.rework.WindCastAnimation.*;

public final class WindHandChecks {
    private WindHandChecks() {}

    public static void verify(GameTestHelper test) {
        var motion = new WindHandMotion();
        test.assertTrue(WindHandVfx.geometry(motion, 0, 0).isEmpty(), "Sulan's idle view has no attack trail");
        for (int combo = 0; combo < 3; combo++) {
            double tick = combo * 12;
            var entry = motion.poseAt(tick);
            motion.attack(tick, 12);
            test.assertTrue(motion.combo() == combo, "Recovered clicks advance the three-cut sequence");
            if (combo > 0) test.assertTrue(!entry.equals(WindHandMotion.IDLE) && entry.equals(motion.poseAt(tick)),
                    "An immediate next swing blends from the preceding follow-through");
            var start = tip(motion, tick + WindHandMotion.CUT_FROM);
            var end = tip(motion, tick + WindHandMotion.CUT_TO);
            double direction = combo == 1 ? 1 : -1;
            test.assertTrue((end.x - start.x) * direction > 1, "Blade tip sweeps in the authored direction");
            test.assertTrue(combo == 2 ? Math.abs(end.y - start.y) < .15 : (end.y - start.y) * direction > .50,
                    "The two diagonals and low horizontal cut have distinct angles");
            var hit = tip(motion, tick + WindHandMotion.HIT);
            test.assertTrue(Math.abs(hit.x) < .33 && hit.y < -.27,
                    "Each visual strike crosses in front below the reticle");
            assertAttachment(test, motion, tick + WindHandMotion.HIT);
            double strike = tick + WindHandMotion.HIT + .17;
            var velocity = tipCenter(motion, strike + .04).subtract(tipCenter(motion, strike - .04));
            var weapon = WindHandRig.weapon(motion, strike, 0);
            var edge = weapon.transformDirection(new Vector3f(1, 0, 0)).normalize();
            var shaft = weapon.transformDirection(new Vector3f(0, 1, 0)).normalize();
            var projected = WindHandRig.vector(velocity);
            projected.sub(new Vector3f(shaft).mul(projected.dot(shaft))).normalize();
            test.assertTrue(edge.dot(projected) > .95f, "The actual silver edge leads each cut");
            test.assertTrue(materials(test, motion, tick + 1.5).containsAll(Set.of(0, WIND_HD_SWEEP_EDGE,
                    WIND_HD_FILAMENTS, combo == 1 ? WIND_HD_FLOW : WIND_HD_CUT)), "Every cut has the wind layers");
            test.assertTrue(WindHandVfx.geometry(motion, tick + 8, 0).isEmpty(), "Wind ribbon has dissipated on recovery");
        }
        motion.attack(100, 12);
        test.assertTrue(motion.combo() == 0, "A long pause resets the chain");
        double stun = 100 + WindHandMotion.HIT;
        var interruptedBlade = WindHandRig.weapon(motion, stun, 0);
        motion.interrupt(stun);
        test.assertTrue(interruptedBlade.equals(WindHandRig.weapon(motion, stun, 0), 1e-5f)
                && motion.combo() < 0 && WindHandVfx.geometry(motion, stun, 0).isEmpty(),
                "Stun stops the active cut and trail while preserving its grip, shaft and cutting edge");
        var recoveringBlade = WindHandRig.weapon(motion, stun + 1, 0);
        motion.attack(stun + 1, 12);
        test.assertTrue(recoveringBlade.equals(WindHandRig.weapon(motion, stun + 1, 0), 1e-5f) && motion.combo() == 0,
                "A new click starts at the visible recovery pose without waiting for the cosmetic settle");
        for (int hold : new int[]{WINDUP, DASH_START}) {
            motion.reset();
            motion.cue(6, hold, 0, REFRESH_LIFE, .85, Vec3.ZERO);
            var finalHold = motion.poseAt(REFRESH_LIFE - .001);
            test.assertTrue(finalHold.equals(motion.poseAt(REFRESH_LIFE))
                    && WindHandVfx.geometry(motion, REFRESH_LIFE, 0).isEmpty()
                    && motion.poseAt(REFRESH_LIFE + 4).equals(WindHandMotion.IDLE),
                    "A missing hold refresh withdraws the visible pose and ends local VFX without a dash finish");
            var returning = motion.poseAt(REFRESH_LIFE + 1);
            motion.cue(6, hold == WINDUP ? WINDUP : DASH_HOLD, REFRESH_LIFE + 1, REFRESH_LIFE, .9, Vec3.ZERO);
            test.assertTrue(returning.equals(motion.poseAt(REFRESH_LIFE + 1)), "A delayed refresh resumes from the visible pose");
        }

        motion.reset();
        motion.prepare(2, 0);
        motion.cue(2, DASH_START, 1, 7, 0, Vec3.ZERO);
        var thrust = motion.poseAt(4);
        motion.cue(2, DASH_HOLD, 4, 7, 0, Vec3.ZERO);
        test.assertTrue(thrust.equals(motion.poseAt(5)) && thrust.grip().z < -1.5 && thrust.shaft().z < -.95,
                "Short dash keeps its straight thrust through server refresh");
        test.assertTrue(materials(test, motion, 5).containsAll(Set.of(WIND_HD_NEEDLE, WIND_HD_FLOW)),
                "Short dash has a bright tip and two lanes");
        motion.cue(2, DASH_STOP, 6, 15, 0, Vec3.ZERO);
        test.assertTrue(motion.poseAt(7.2).grip().y < thrust.grip().y, "Actual collision/stop lowers the tip");
        test.assertTrue(motion.poseAt(15).equals(WindHandMotion.IDLE), "Short dash recovers fully");

        motion.cue(3, CAST, 16, 15, 0, new Vec3(.24, .08, 0));
        var mark = motion.poseAt(17);
        test.assertTrue(mark.point() > .99 && mark.free().x > -.10 && mark.shaft().y < 0,
                "Mark aims the two fingers and keeps the blade down");
        test.assertTrue(materials(test, motion, 17.5).contains(WIND_HD_FILAMENTS), "Mark sheds a short palm wisp");
        motion.cue(4, CAST, 26, 15, 0, Vec3.ZERO);
        test.assertTrue(motion.poseAt(27.25).palmUp() > .99 && tip(motion, 27.25).y > tip(motion, 26).y + .55,
                "Wall launches an upward cut and an open raised palm");
        test.assertTrue(materials(test, motion, 27.3).contains(WIND_HD_FLOW), "Wall carries upward wind");
        motion.cue(5, CAST, 36, 15, 0, Vec3.ZERO);
        test.assertTrue(tip(motion, 37.15).x < tip(motion, 36).x - .8, "The throw uses a single leftward sweep");
        test.assertTrue(motion.poseAt(46).equals(WindHandMotion.IDLE), "Projectile return has no second hand motion");

        motion.cue(6, WINDUP, 48, 7, 0, Vec3.ZERO);
        motion.cue(6, WINDUP, 50, 7, .4, Vec3.ZERO);
        motion.cue(6, WINDUP, 52, 7, .8, Vec3.ZERO);
        test.assertTrue(motion.chargeProgress(53) > .55 && motion.poseAt(53).grip().y < -.60,
                "Windup follows the actual confirmed progress");
        motion.cue(6, CANCEL, 54, 7, 0, Vec3.ZERO);
        test.assertTrue(motion.poseAt(58).equals(WindHandMotion.IDLE), "Canceled charge eases back to idle");
        motion.cue(6, WINDUP, 60, 7, .9, Vec3.ZERO);
        motion.cue(6, DASH_START, 62, 7, 0, Vec3.ZERO);
        test.assertTrue(motion.poseAt(64).grip().x > .50 && motion.poseAt(64).shaft().z < -.95,
                "The long dash keeps its weapon at the side instead of thrusting centrally");
        motion.cue(6, DASH_STOP, 65, 15, 0, Vec3.ZERO);
        test.assertTrue(tip(motion, 66.2).x > tip(motion, 65).x + .60, "Only the stop cue triggers the side finish");
        motion.cue(7, CAST, 75, 15, 0, Vec3.ZERO);
        var gathered = motion.poseAt(75);
        var spread = motion.poseAt(76.15);
        test.assertTrue(spread.grip().x - spread.free().x > gathered.grip().x - gathered.free().x + .5,
                "Domain opens both arms from a compact gather pose");
        test.assertTrue(materials(test, motion, 76.2).contains(WIND_HD_RING_FLECKS), "Domain includes dissolving wind grains");
        motion.reset();
        test.assertTrue(motion.poseAt(76.2).equals(WindHandMotion.IDLE)
                && WindHandVfx.geometry(motion, 76.2, 0).isEmpty(), "Clearing the owner removes every view effect");
        System.out.println("[MYRIAD-WIND-HAND] PASS: three leading-edge cuts, palm/equip attachment, six cast gestures, wind layers and cancel/stop");
    }

    private static Set<Integer> materials(GameTestHelper test, WindHandMotion motion, double now) {
        var quads = WindHandVfx.geometry(motion, now, .17f);
        test.assertTrue(quads.size() <= AttackTrailMesh.MAX_QUADS, "Sulan obeys the first-person mesh budget");
        test.assertTrue(quads.equals(WindHandVfx.geometry(motion, now, .17f)), "Trail samples are independent of render history");
        var out = new HashSet<Integer>();
        for (var quad : quads) {
            out.add(quad.material());
            test.assertTrue(quad.a().z < -.055 && quad.b().z < -.055 && quad.c().z < -.055 && quad.d().z < -.055,
                    "Every wind layer stays in front of the clipping plane");
        }
        return out;
    }

    private static Vector3f tip(WindHandMotion motion, double now) {
        return WindHandRig.weapon(motion, now, 0).transformPosition(WindHandRig.vector(WindHandRig.TIP));
    }

    private static Vec3 tipCenter(WindHandMotion motion, double now) {
        return WindHandRig.tipCenter(motion.poseAt(now));
    }

    private static void assertAttachment(GameTestHelper test, WindHandMotion motion, double now) {
        var pose = motion.poseAt(now);
        for (boolean slim : new boolean[]{false, true}) {
            var weapon = WindHandRig.weapon(motion, now, .67f);
            var armPalm = WindHandRig.arm(pose, false, slim, .67f, weapon)
                    .transformPosition(new Vector3f(slim ? -.5f / 16 : -1f / 16, 8f / 16, 0));
            var bladeGrip = weapon.getTranslation(new Vector3f());
            test.assertTrue(armPalm.distance(bladeGrip) < 1e-5, "The model's grip follows the palm on both skin widths");
        }
    }
}
