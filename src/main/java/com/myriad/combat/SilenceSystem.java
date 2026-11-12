package com.myriad.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Target-scoped silence with real game-time expiry, independent of its originating skill. */
public final class SilenceSystem {
    private static final Map<UUID, Long> SILENCED_UNTIL = new HashMap<>();
    private SilenceSystem() {}
    public static void apply(LivingEntity target, int ticks) {
        SILENCED_UNTIL.put(target.getUUID(), target.level().getGameTime() + ticks);
        if (target instanceof ServerPlayer player) {
            PlayerDataProvider.get(player).setBuff("zhongshengguizong", ticks);
            PlayerDataProvider.syncToClient(player);
        }
    }
    public static boolean isSilenced(ServerPlayer player) {
        return SILENCED_UNTIL.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
    }
    /** A targeted cleanse never clears the target's own fields, contracts or allied bonuses. */
    public static void cleanse(ServerPlayer player) {
        clearFor(player.getUUID());
        PlayerDataProvider.get(player).setBuff("zhongshengguizong", 0);
    }
    public static void clearFor(UUID target) { SILENCED_UNTIL.remove(target); }
    public static void tick(long now) { SILENCED_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now); }
}
