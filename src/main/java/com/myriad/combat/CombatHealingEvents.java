package com.myriad.combat;

import com.myriad.game.ModState;
import net.minecraft.server.level.ServerPlayer;

/** 治疗统计独立于已删除的符石系统。 */
public final class CombatHealingEvents {
    private CombatHealingEvents() { }

    public static void heal(net.minecraft.world.entity.LivingEntity subject, float amount) {
        if (ModState.isEnabled() && subject instanceof ServerPlayer player)
            CombatTelemetry.recordHeal(player, amount);
    }
}
