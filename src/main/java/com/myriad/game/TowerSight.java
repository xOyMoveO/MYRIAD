package com.myriad.game;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Use the beam socket and actual block collision shapes for every defensive tower shot. */
final class TowerSight {
    private TowerSight() {}

    static Vec3 muzzle(LivingEntity tower) {
        return tower.position().add(0, 7, 0);
    }

    static Vec3 visibleBodyPoint(LivingEntity tower, LivingEntity target) {
        var from = muzzle(tower);
        var box = target.getBoundingBox();
        var center = box.getCenter();
        if (clear(tower, from, center)) return center;
        var eye = target.getEyePosition();
        if (clear(tower, from, eye)) return eye;
        // Sample both sides of the silhouette near the shoulders and feet.
        var side = new Vec3(center.z - from.z, 0, from.x - center.x).normalize().scale(box.getXsize() * .43);
        for (double h : new double[]{.83, .15}) {
            Vec3 point = new Vec3(center.x, box.minY + box.getYsize() * h, center.z);
            if (clear(tower, from, point)) return point;
            if (clear(tower, from, point.add(side))) return point.add(side);
            if (clear(tower, from, point.subtract(side))) return point.subtract(side);
        }
        return null;
    }

    private static boolean clear(LivingEntity tower, Vec3 from, Vec3 to) {
        return tower.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, tower)).getType() == HitResult.Type.MISS;
    }
}
