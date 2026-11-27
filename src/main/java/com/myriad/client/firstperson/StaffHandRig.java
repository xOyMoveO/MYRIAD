package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Both item assets have the wrapped grip centered at item origin. */
final class StaffHandRig {
    static final float FROST_SCALE = .69f * 1.06f;
    static final float HERB_SCALE = .69f * 1.05f;

    private StaffHandRig() {}

    static Matrix4f item(StaffHandMotion.Pose pose, boolean mirrored, float equip) {
        return item(pose, true, mirrored, equip);
    }

    static Matrix4f item(StaffHandMotion.Pose pose, boolean frost, boolean mirrored, float equip) {
        return pivot(lowered(pose.grip(), mirrored, equip), mirror(pose.axis(), mirrored),
                mirrored ? -pose.roll() : pose.roll()).scale(frost ? FROST_SCALE : HERB_SCALE);
    }

    static Matrix4f arm(StaffHandMotion.Pose pose, boolean mirrored, boolean offhand, boolean slim, float equip) {
        boolean left = mirrored != offhand;
        Vec3 hand = lowered(offhand ? pose.palm() : pose.grip(), mirrored, equip);
        Vec3 shoulder = offhand
                ? new Vec3(left ? -.85 : .85, -.89, -.16)
                : new Vec3(left ? -.69 : .69, -.99, -.22);
        double roll = offhand ? pose.palmRoll() : pose.roll() * .52;
        return pivot(hand, hand.subtract(shoulder), mirrored ? -roll : roll)
                .scale(.89f, 1.02f, .89f).mul(NightDaggerRig.arm(slim, left));
    }

    static Vec3 lowered(Vec3 point, boolean mirrored, float equip) {
        return mirror(point, mirrored).add(0, -.48 * Math.clamp(equip, 0, 1), 0);
    }

    private static Vec3 mirror(Vec3 point, boolean mirrored) {
        return mirrored ? point.multiply(-1, 1, 1) : point;
    }

    private static Matrix4f pivot(Vec3 grip, Vec3 axis, double roll) {
        Vec3 dir = axis.normalize();
        var rotation = new Quaternionf().rotationTo(0, 1, 0, (float) dir.x, (float) dir.y, (float) dir.z)
                .rotateY((float) roll);
        return new Matrix4f().translation((float) grip.x, (float) grip.y, (float) grip.z).rotate(rotation);
    }
}
