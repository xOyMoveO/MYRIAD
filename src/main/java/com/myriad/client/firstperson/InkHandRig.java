package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** The zhenqi model is centered on the wrapped grip. The arm and item share that pivot. */
final class InkHandRig {
    static final float FLAG_SCALE = .81f * 1.07f;

    private InkHandRig() {}

    static Vec3 lowered(Vec3 center, float equip) {
        return center.add(0, -.48 * Math.clamp(equip, 0, 1), 0);
    }

    static Matrix4f flag(InkHandMotion.Pose pose, float equip) {
        return pivot(lowered(pose.grip(), equip), pose.axis(), pose.twist()).scale(FLAG_SCALE);
    }

    static Matrix4f arm(InkHandMotion.Pose pose, boolean left, boolean slim, float equip) {
        Vec3 hand = lowered(left ? pose.palm() : pose.grip(), equip);
        Vec3 shoulder = new Vec3(left ? -.83 : .85, -.82, -.15);
        return pivot(hand, hand.subtract(shoulder), left ? pose.palmRoll() : pose.twist() * .56)
                .scale(.89f, .98f, .89f).mul(NightDaggerRig.arm(slim, left));
    }

    private static Matrix4f pivot(Vec3 at, Vec3 axis, double roll) {
        var dir = axis.normalize();
        var orientation = new Quaternionf().rotationTo(0, 1, 0, (float)dir.x, (float)dir.y, (float)dir.z)
                .rotateY((float)roll);
        return new Matrix4f().translation((float)at.x, (float)at.y, (float)at.z).rotate(orientation);
    }
}
