package com.myriad.combat;

import com.myriad.game.ModState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/** 治疗统计独立于已删除的符石系统。 */
@EventBusSubscriber(modid = "myriad")
public final class CombatHealingEvents {
    private CombatHealingEvents() { }

    @SubscribeEvent
    public static void heal(LivingHealEvent event) {
        if (ModState.isEnabled() && event.getEntity() instanceof ServerPlayer player)
            CombatTelemetry.recordHeal(player, event.getAmount());
    }
}
