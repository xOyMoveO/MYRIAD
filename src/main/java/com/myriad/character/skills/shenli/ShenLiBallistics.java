package com.myriad.character.skills.shenli;

import net.minecraft.world.phys.Vec3;

/** Cone angle is the total deviation, not independent yaw/pitch offsets. */
public final class ShenLiBallistics {
    public static final double AIM_RANGE = 22.0;
    /** 瞄准射击速度：每秒 50 格，服务端每 tick 推进 2.5 格。 */
    public static final double AIM_SPEED = 50.0 / 20.0;
    private ShenLiBallistics() {}

    public static Vec3 direction(Vec3 look, boolean aiming, double angleSample, double azimuthSample) {
        Vec3 forward = look.normalize();
        if (aiming) return forward;
        // Hip-fire spread is deliberately narrow: 2° minimum, 10° maximum.
        double theta = Math.toRadians(2 + 8 * Math.clamp(angleSample, 0, 1));
        double phi = Math.PI * 2 * Math.clamp(azimuthSample, 0, 1);
        Vec3 reference = Math.abs(forward.y) > .99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();
        return forward.scale(Math.cos(theta))
                .add(right.scale(Math.sin(theta) * Math.cos(phi)))
                .add(up.scale(Math.sin(theta) * Math.sin(phi))).normalize();
    }
}
