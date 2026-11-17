package com.myriad.player;

import com.myriad.game.ModState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** 仅负责旧存档迁移，不提供符石获取、属性或效果。 */
public final class RetiredRuneCleanup {
    private RetiredRuneCleanup() { }

    public static void clean(LivingEntity entity) {
        boolean changed = false;
        for (var attribute : entity.getAttributes().getSyncableAttributes()) {
            for (var modifier : new java.util.ArrayList<>(attribute.getModifiers())) {
                if (modifier.id().getNamespace().equals("myriad")
                        && modifier.id().getPath().startsWith("rune_")) {
                    attribute.removeModifier(modifier.id());
                    changed = true;
                }
            }
        }
        var tag = com.myriad.player.EntityState.data(entity);
        for (String key : new String[]{"myriad_chill_until", "myriad_corrode_until", "myriad_fear_until",
                "myriad_fear_x", "myriad_fear_z", "myriad_revive_x", "myriad_revive_y", "myriad_revive_z", "myriad_hawk_ready"})
            tag.remove(key);
        ModShieldSystem.removeRetiredRuneSources(entity);
        if (entity instanceof ServerPlayer player) {
            PlayerDataProvider.get(player).discardRetiredRuneBuffs();
            if (changed && ModState.isEnabled()) PlayerHealthSystem.recalculate(player);
        }
    }

    public static void onJoin(net.minecraft.world.entity.Entity subject, net.minecraft.world.level.Level level) {
        if (!level.isClientSide() && subject instanceof LivingEntity living)
            clean(living);
    }
}
