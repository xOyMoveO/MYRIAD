package com.myriad.combat;

import com.myriad.registry.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class LifestealSystem {
    public static void onActualDamage(ServerPlayer p, LivingEntity target, float actual, int type, boolean skill) {
        if (p.isDeadOrDying() || p.isSpectator() || target instanceof com.myriad.game.DefenseTowerEntity
                || actual <= 0 || type == com.myriad.client.DamageFloater.TYPE_TRUE) return;
        boolean magic = type == com.myriad.client.DamageFloater.TYPE_MAGIC;
        if (skill && !magic) return;
        double rate = Math.clamp(p.getAttributeValue(magic ? ModAttributes.SPELL_LIFESTEAL : ModAttributes.PHYSICAL_LIFESTEAL), 0, 20) / 100;
        double efficiency = (skill ? .33 : 1) * (target instanceof ServerPlayer ? 1 : .5);
        var nbt = p.getPersistentData();
        long second = p.level().getGameTime() / 20;
        double used = nbt.getLongOr("myriad_lifesteal_second", -1) == second ? nbt.getDoubleOr("myriad_lifesteal_used", 0) : 0;
        float heal = (float)Math.max(0, Math.min(actual * rate * efficiency, p.getMaxHealth() * .04 - used));
        heal = Math.min(heal, p.getMaxHealth() - p.getHealth());
        if (heal <= 0) return;
        float before = p.getHealth(); CombatTelemetry.heal(p, heal, "lifesteal");
        float gained = Math.max(0, p.getHealth() - before);
        nbt.putLong("myriad_lifesteal_second", second); nbt.putDouble("myriad_lifesteal_used", used + gained);
        if (gained > 0) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new com.myriad.network.NetworkSetup.HealFloatPayload(Math.max(1, Math.round(gained))));
    }
}
