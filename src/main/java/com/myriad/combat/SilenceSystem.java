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
        if (ticks <= 0) return;
        long now = target.level().getGameTime();
        long until = SILENCED_UNTIL.merge(target.getUUID(), now + ticks, ControlStatusSystem::laterExpiry);
        if (target instanceof ServerPlayer player) {
            PlayerDataProvider.get(player).setBuff("zhongshengguizong", (int) (until - now));
            PlayerDataProvider.syncToClient(player);
        }
    }
    public static long remaining(ServerPlayer player) {
        return Math.max(0, SILENCED_UNTIL.getOrDefault(player.getUUID(), 0L) - player.level().getGameTime());
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
