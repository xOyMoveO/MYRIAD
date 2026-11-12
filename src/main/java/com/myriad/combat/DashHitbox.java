package com.myriad.combat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.myriad.game.GameManager;
import com.myriad.game.LaneSystem;

/** Swept body collision: no skipped ticks, duplicate victims, or building skill hits. */
public final class DashHitbox {
    private DashHitbox() { }
    public static List<LivingEntity> targets(ServerPlayer p, Vec3 from, Vec3 to,
            double radius, Set<UUID> alreadyHit) {
        final double effectiveRadius = radius + .25;
        Vec3 a = from.add(0, .9, 0), b = to.add(0, .9, 0);
        var faction = GameManager.get() == null ? null : GameManager.get().factionOf(p);
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(effectiveRadius, 1, effectiveRadius), e -> {
            if (e == p || !e.isAlive() || e instanceof com.myriad.game.DefenseTowerEntity
                    || alreadyHit.contains(e.getUUID())) return false;
            if (faction != null && faction == LaneSystem.factionOf(e)) return false;
            AABB bounds = e.getBoundingBox().inflate(effectiveRadius, .5, effectiveRadius);
            return (bounds.contains(a) || bounds.clip(a, b).isPresent()) && p.hasLineOfSight(e);
        });
    }
}
