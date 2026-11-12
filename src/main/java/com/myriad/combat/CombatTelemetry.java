package com.myriad.combat;

import com.myriad.game.ModState;
import com.myriad.network.NetworkSetup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** 服务端权威的个人承伤/治疗统计，只向事件所属玩家发送汇总增量。 */
public final class CombatTelemetry {
    public static final int DAMAGE_PLAYER = 0;
    public static final int DAMAGE_NON_PLAYER = 1;
    public static final int HEAL = 2;
    private static final ThreadLocal<String> HEAL_SOURCE = ThreadLocal.withInitial(() -> "regeneration");

    private CombatTelemetry() { }

    public static void heal(LivingEntity target, float amount, String source) {
        if (amount <= 0) return;
        String previous = HEAL_SOURCE.get();
        HEAL_SOURCE.set(sanitize(source));
        try {
            target.heal(amount);
        } finally {
            HEAL_SOURCE.set(previous);
        }
    }

    /** 在 LivingHealEvent 完成所有治疗倍率修正后调用。 */
    public static void recordHeal(ServerPlayer player, float requestedAmount) {
        if (!ModState.isEnabled() || player.level().isClientSide() || requestedAmount <= 0) return;
        float actual = Math.min(requestedAmount, Math.max(0, player.getMaxHealth() - player.getHealth()));
        if (actual <= 0) return;
        if (com.myriad.game.GameManager.get() != null)
            com.myriad.game.GameManager.get().recordHealingReceived(player, actual);
        PacketDistributor.sendToPlayer(player, new NetworkSetup.CombatTelemetryPayload(
                HEAL, Math.max(1, Math.round(actual)), sanitize(HEAL_SOURCE.get())));
    }

    public static void recordDamage(ServerPlayer victim, float actualDamage, Entity source) {
        if (!ModState.isEnabled() || victim.level().isClientSide() || actualDamage <= 0) return;
        if (com.myriad.game.GameManager.get() != null)
            com.myriad.game.GameManager.get().recordDamageTaken(victim, actualDamage);
        int kind = source instanceof ServerPlayer ? DAMAGE_PLAYER : DAMAGE_NON_PLAYER;
        PacketDistributor.sendToPlayer(victim, new NetworkSetup.CombatTelemetryPayload(
                kind, Math.max(1, Math.round(actualDamage)), ""));
    }

    /** 护盾在 Incoming 阶段消费，不会进入 Post 生命伤害事件，因此单独计入有效承伤。 */
    public static void recordShieldDamage(ServerPlayer victim, float absorbedDamage, Entity source) {
        recordDamage(victim, absorbedDamage, source);
    }

    private static String sanitize(String source) {
        return source != null && source.matches("[a-z0-9_]{1,32}") ? source : "other";
    }
}
