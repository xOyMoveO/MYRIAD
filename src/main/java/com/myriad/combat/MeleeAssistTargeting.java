package com.myriad.combat;

import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Side-independent geometry for one assisted melee attempt. */
public final class MeleeAssistTargeting {
    private MeleeAssistTargeting() {}

    public static LivingEntity find(Player player, Predicate<LivingEntity> allowed) {
        double reach = player.entityInteractionRange();
        if (reach <= 0) return null;
        var itemRange = player.getMainHandItem().get(DataComponents.ATTACK_RANGE);
        if (itemRange != null && itemRange.getClosesetHit(player, 1, EntitySelector.CAN_BE_PICKED).getType()
                == HitResult.Type.ENTITY) return null;

        Vec3 start = player.getEyePosition();
        Vec3 limit = start.add(player.getLookAngle().scale(reach));
        var block = clip(player, start, limit, ClipContext.Block.OUTLINE);
        boolean stopped = block.getType() == HitResult.Type.BLOCK;
        Vec3 end = stopped ? block.getLocation() : limit;
        double distanceSquared = start.distanceToSqr(end);
        if (distanceSquared == 0) return null;

        // Use vanilla picking (including its pick radii), even for entities the player cannot damage.
        AABB search = new AABB(start, end).inflate(1 + BasicAttackAssist.MAX_PADDING);
        var exact = ProjectileUtil.getEntityHitResult(player, start, end, search,
                EntitySelector.CAN_BE_PICKED, distanceSquared);
        if (exact != null && start.distanceToSqr(exact.getLocation()) < distanceSquared) return null;

        var collider = clip(player, start, end, ClipContext.Block.COLLIDER);
        if (collider.getType() == HitResult.Type.BLOCK) {
            stopped = true;
            end = collider.getLocation();
        }
        BasicAttackAssist.Contact best = null;
        LivingEntity target = null;
        for (var entity : player.level().getEntities(player, search, EntitySelector.CAN_BE_PICKED)) {
            if (!(entity instanceof LivingEntity living) || !BasicAttackAssist.eligibleTarget(living)
                    || !player.isWithinEntityInteractionRange(living, 0)) continue;
            var contact = BasicAttackAssist.contact(start, end, stopped, reach, living.getBoundingBox(), 0,
                    BasicAttackAssist.DEFAULT_FRACTION);
            if (contact == null || !BasicAttackAssist.precedes(contact, living.getId(), best,
                    target == null ? 0 : target.getId()) || !allowed.test(living)
                    || !player.level().getWorldBorder().isWithinBounds(living.blockPosition())
                    || !player.hasLineOfSight(living)
                    || obstructed(player, start, contact.originalPoint())) continue;
            best = contact;
            target = living;
        }
        return target;
    }

    private static boolean obstructed(Player player, Vec3 start, Vec3 point) {
        return clip(player, start, point, ClipContext.Block.COLLIDER).getType() == HitResult.Type.BLOCK
                || clip(player, start, point, ClipContext.Block.OUTLINE).getType() == HitResult.Type.BLOCK;
    }

    private static HitResult clip(Player player, Vec3 start, Vec3 end, ClipContext.Block mode) {
        return player.level().clip(new ClipContext(start, end, mode, ClipContext.Fluid.NONE, player));
    }
}
