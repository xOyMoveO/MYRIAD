package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.myriad.character.skills.sulan.rework.WindCastAnimation.*;

/** Palm and blade both use the grip center baked into fengren.json. */
final class WindHandRig {
    static final Vec3 TIP = new Vec3(.17, 1.499, 0);
    static final Vec3 EDGE_ROOT = new Vec3(.057, .196, 0);
    private static final Vec3 RIGHT = new Vec3(1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double BLADE_SCALE = .82;
    private static final double IDLE_ROLL = -.18;

    private WindHandRig() {}

    static Matrix4f weapon(WindHandMotion motion, double now, float equip) {
        var pose = motion.poseAt(now);
        Vec3 shaft = pose.shaft().normalize();
        Vec3 reference = across(RIGHT, shaft);
        Vec3 face = reference.cross(shaft);
        double roll = IDLE_ROLL + rollOffset(motion, now, shaft, reference, face);
        Vec3 edge = reference.scale(Math.cos(roll)).subtract(face.scale(Math.sin(roll)));
        return basis(palm(pose.grip(), equip), edge, shaft).scale((float)BLADE_SCALE);
    }

    static double rollOffset(WindHandMotion motion, double now) {
        Vec3 shaft = motion.poseAt(now).shaft().normalize();
        Vec3 reference = across(RIGHT, shaft);
        return rollOffset(motion, now, shaft, reference, reference.cross(shaft));
    }

    private static double rollOffset(WindHandMotion motion, double now, Vec3 shaft, Vec3 reference, Vec3 face) {
        double weight = 0;
        double travelTick = now;
        double anchorTick = now;
        if (motion.hasSkill(now)) {
            int ability = motion.ability(now);
            int phase = motion.signal(now);
            if (phase == CAST && (ability == 4 || ability == 5) || phase == DASH_STOP && ability == 6) {
                double age = now - motion.skillStart();
                double cutEnd = phase == DASH_STOP ? 1.2 : ability == 4 ? 1.25 : 1.15;
                travelTick = motion.skillStart() + Math.clamp(age, .14, cutEnd - .14);
                anchorTick = motion.skillStart() + cutEnd * .5;
                weight = Math.clamp(1 - (age - cutEnd) / 3.4, 0, 1) * Math.clamp(age / .38, 0, 1);
            }
        } else if (motion.combo() >= 0) {
            double age = now - motion.attackStart();
            travelTick = motion.attackStart() + Math.clamp(age, WindHandMotion.CUT_FROM + .14, WindHandMotion.CUT_TO - .14);
            anchorTick = motion.attackStart() + WindHandMotion.HIT;
            weight = Math.clamp(age / WindHandMotion.CUT_FROM, 0, 1)
                    * Math.clamp((motion.recovery() - age) / (motion.recovery() - WindHandMotion.CUT_TO), 0, 1);
        }
        if (weight > 0) {
            double angle = cutAngle(motion, shaft, reference, face, travelTick);
            if (Double.isFinite(angle)) {
                var anchorPose = motion.poseAt(anchorTick);
                Vec3 anchorShaft = anchorPose.shaft().normalize();
                Vec3 anchorRef = across(RIGHT, anchorShaft);
                double anchor = cutAngle(motion, anchorShaft, anchorRef, anchorRef.cross(anchorShaft), anchorTick);
                double turn = Double.isFinite(anchor)
                        ? Math.IEEEremainder(anchor - IDLE_ROLL, Math.PI * 2) + Math.IEEEremainder(angle - anchor, Math.PI * 2)
                        : Math.IEEEremainder(angle - IDLE_ROLL, Math.PI * 2);
                return motion.returningRoll(now) * (1 - weight) + turn * weight;
            }
        }
        return motion.returningRoll(now);
    }

    private static double cutAngle(WindHandMotion motion, Vec3 shaft, Vec3 reference, Vec3 face, double travelTick) {
        Vec3 travel = tipCenter(motion.poseAt(travelTick + .055)).subtract(tipCenter(motion.poseAt(travelTick - .055)));
        Vec3 lead = travel.subtract(shaft.scale(travel.dot(shaft)));
        if (lead.lengthSqr() <= 1e-7) return Double.NaN;
        lead = lead.normalize();
        return Math.atan2(-lead.dot(face), lead.dot(reference));
    }

    static Matrix4f arm(WindHandMotion.Pose pose, boolean off, boolean slim, float equip, Matrix4f weapon) {
        Vec3 grip = palm(off ? pose.free() : pose.grip(), equip);
        Vec3 shoulder = new Vec3(off ? -.83 : .84, -.78, -.16);
        Vec3 axis = grip.subtract(shoulder).normalize();
        Vec3 across = across(RIGHT, axis);
        Vec3 transverse = axis.cross(across);
        double wristRoll = off ? Math.toRadians(134) * pose.palmUp() : .53 *
                weapon.transformDirection(new Vector3f(1, 0, 0)).normalize().dot(vector(transverse));
        across = across.scale(Math.cos(wristRoll)).add(transverse.scale(Math.sin(wristRoll)));
        return basis(grip, across, axis).scale(.71f, .99f, .71f).mul(NightDaggerRig.arm(slim, off));
    }

    static Vec3 palm(Vec3 grip, float equip) {
        return grip.add(0, -.48 * Math.clamp(equip, 0, 1), 0);
    }

    static Vec3 tipCenter(WindHandMotion.Pose pose) {
        return pose.grip().add(pose.shaft().normalize().scale(BLADE_SCALE * TIP.y));
    }

    static Vector3f vector(Vec3 v) { return new Vector3f((float)v.x, (float)v.y, (float)v.z); }

    private static Vec3 across(Vec3 hint, Vec3 axis) {
        Vec3 side = hint.subtract(axis.scale(hint.dot(axis)));
        if (side.lengthSqr() < 1e-5) side = UP.subtract(axis.scale(axis.y));
        return side.normalize();
    }

    private static Matrix4f basis(Vec3 origin, Vec3 side, Vec3 axis) {
        Vec3 face = side.cross(axis);
        return new Matrix4f()
                .setColumn(0, column(side, 0))
                .setColumn(1, column(axis, 0))
                .setColumn(2, column(face, 0))
                .setColumn(3, column(origin, 1));
    }

    private static Vector4f column(Vec3 axis, float w) {
        return new Vector4f((float)axis.x, (float)axis.y, (float)axis.z, w);
    }
}
