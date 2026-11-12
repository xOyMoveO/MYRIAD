package com.myriad.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 统一击退缓冲：一次击退生效后，目标在接下来的 20 tick 内免疫新的击退。 */
public final class KnockbackBuffer {
    public static final int BUFFER_TICKS = 20;
    private static final Map<UUID, Long> NEXT_ALLOWED = new HashMap<>();

    private KnockbackBuffer() { }

    /** 供原版/NeoForge 击退事件使用；返回 false 时应取消本次击退。 */
    public static boolean allow(LivingEntity target) {
        if (target instanceof com.myriad.game.DefenseTowerEntity) return false;
        long now = target.level().getGameTime();
        long next = NEXT_ALLOWED.getOrDefault(target.getUUID(), Long.MIN_VALUE);
        if (now < next) return false;
        NEXT_ALLOWED.put(target.getUUID(), now + BUFFER_TICKS);
        return true;
    }

    /** 供技能直接施加击退速度时使用。 */
    public static boolean apply(LivingEntity target, Vec3 motion) {
        if (!allow(target)) return false;
        target.setDeltaMovement(motion);
        return true;
    }

    public static void clear(UUID targetId) {
        NEXT_ALLOWED.remove(targetId);
    }

    public static void clearAll() {
        NEXT_ALLOWED.clear();
    }

    public static boolean recentlyForced(LivingEntity target) {
        return target.level().getGameTime() < NEXT_ALLOWED.getOrDefault(target.getUUID(), Long.MIN_VALUE);
    }
    /** Identifies a newly accepted explicit impulse, independently of ordinary gravity/landing. */
    public static long impulseToken(LivingEntity target) {
        return NEXT_ALLOWED.getOrDefault(target.getUUID(), Long.MIN_VALUE);
    }
}
