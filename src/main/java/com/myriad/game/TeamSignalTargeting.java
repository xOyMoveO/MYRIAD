package com.myriad.game;

import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Dedicated signal ray; vanilla interaction MISS endpoints must never shorten it. */
public final class TeamSignalTargeting {
    private TeamSignalTargeting() {}
    public static Vec3 end(Vec3 origin, Vec3 look) {
        return origin.add(look.normalize().scale(TeamSignalRules.RANGE));
    }
    public static Vec3 select(Vec3 origin, Vec3 look, HitResult blockHit, HitResult interaction) {
        Vec3 point = end(origin, look);
        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK
                && TeamSignalRules.inRange(origin, blockHit.getLocation())) point = blockHit.getLocation();
        if (interaction != null && interaction.getType() == HitResult.Type.ENTITY
                && TeamSignalRules.inRange(origin, interaction.getLocation())
                && interaction.getLocation().distanceToSqr(origin) < point.distanceToSqr(origin)) point = interaction.getLocation();
        return point;
    }
}
