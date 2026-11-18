package com.myriad.game;

/** Shared client/server admission policy; training does not require a running match. */
public final class TeamSignalRules {
    public static final double RANGE = 18.0;
    private TeamSignalRules() {}
    public static boolean inRange(net.minecraft.world.phys.Vec3 origin, net.minecraft.world.phys.Vec3 point) {
        return com.myriad.network.TeamSignalRequest.finite(origin) && com.myriad.network.TeamSignalRequest.finite(point)
                && origin.distanceToSqr(point) <= RANGE * RANGE + 1.0e-6;
    }
    public static boolean allowed(boolean enabled,boolean training,GameManager.Phase phase) {
        return enabled&&(training||phase==GameManager.Phase.PLAYING);
    }
}
