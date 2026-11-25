package com.myriad.combat;

import com.myriad.game.VisionSystem;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-side collision checks for one basic projectile step. */
public final class ProjectileBasicAttack {
    private ProjectileBasicAttack() {}

    public record Hit(LivingEntity target, Vec3 point) {}

    public record Segment(ServerPlayer caster, Vec3 from, Vec3 end, Vec3 blockEnd,
                          boolean blocked, boolean windBlocked) {
        public List<LivingEntity> candidates(double radius, Predicate<LivingEntity> eligible) {
            double padding = BasicAttackAssist.enabled() ? Math.max(radius, BasicAttackAssist.MAX_PADDING) : radius;
            return caster.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(padding),
                    target -> canHit(caster, target) && eligible.test(target));
        }

        public boolean reaches(Vec3 impact, Vec3 originalPoint, double remaining) {
            double contactDistance = from.distanceToSqr(impact);
            double stepDistance = from.distanceToSqr(end);
            return (blocked ? contactDistance < stepDistance : contactDistance <= Math.nextUp(stepDistance))
                    && from.distanceToSqr(originalPoint) <= remaining * remaining
                    && blockHit(caster, from, originalPoint).getType() == HitResult.Type.MISS
                    && WindWallSystem.firstEnemyHit(caster, from, originalPoint) == null;
        }

        /** Preserve the projectile's existing baseline contact rule, then check the actual target box. */
        public boolean reaches(Vec3 impact, AABB original, double remaining) {
            Vec3 originalPoint = new Vec3(Math.clamp(impact.x, original.minX, original.maxX),
                    Math.clamp(impact.y, original.minY, original.maxY),
                    Math.clamp(impact.z, original.minZ, original.maxZ));
            return reaches(impact, originalPoint, remaining);
        }

        public void showWindIntercept() {
            if (windBlocked) WindWallSystem.interceptProjectile(caster, from, blockEnd);
        }
    }

    public static Segment clip(ServerPlayer caster, Vec3 from, Vec3 desired) {
        var block = blockHit(caster, from, desired);
        boolean blocked = block.getType() != HitResult.Type.MISS;
        Vec3 blockEnd = blocked ? block.getLocation() : desired;
        Vec3 wind = WindWallSystem.firstEnemyHit(caster, from, blockEnd);
        if (wind != null && (!blocked || from.distanceToSqr(wind) < from.distanceToSqr(blockEnd))) {
            return new Segment(caster, from, wind, blockEnd, true, true);
        }
        return new Segment(caster, from, blockEnd, blockEnd, blocked, false);
    }

    /** A legal baseline hit takes priority before this search is called. */
    public static Hit firstAssistedHit(Segment segment, List<LivingEntity> candidates, double radius, double remaining) {
        if (!BasicAttackAssist.enabled()) return null;
        BasicAttackAssist.Contact best = null;
        LivingEntity target = null;
        for (LivingEntity candidate : candidates) {
            if (!BasicAttackAssist.canAssist(segment.caster(), candidate)) continue;
            var contact = BasicAttackAssist.contact(segment.from(), segment.end(), segment.blocked(), remaining,
                    candidate.getBoundingBox(), radius);
            if (contact == null || !segment.reaches(contact.point(), contact.originalPoint(), remaining)) continue;
            if (BasicAttackAssist.precedes(contact, candidate.getId(), best, target == null ? 0 : target.getId())) {
                best = contact;
                target = candidate;
            }
        }
        return best == null ? null : new Hit(target, best.point());
    }

    private static boolean canHit(ServerPlayer caster, LivingEntity target) {
        return target.isAlive() && !target.isSpectator() && !(target instanceof Player player && player.isCreative())
                && SkillTargets.hostile(caster, target) && VisionSystem.canSee(caster, target)
                && !target.isInvisibleTo(caster);
    }

    private static HitResult blockHit(ServerPlayer caster, Vec3 from, Vec3 to) {
        return caster.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
    }
}
