package com.myriad.player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 模组护盾的唯一存储与结算入口。
 * 每个技能占用独立来源层，避免一层到期时误删其他技能提供的护盾。
 */
public final class ModShieldSystem {
    private static final Map<UUID, Map<String, ShieldLayer>> LAYERS = new HashMap<>();

    private ModShieldSystem() { }

    /** 覆盖同来源护盾，并与其他来源护盾相加。 */
    public static void grant(LivingEntity player, String source, float amount, int durationTicks) {
        if (amount <= 0.0f || durationTicks <= 0) return;
        Map<String, ShieldLayer> layers = LAYERS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        pruneExpired(player, layers);
        layers.put(source, new ShieldLayer(amount, player.level().getGameTime() + durationTicks));
        updateValue(player, layers, true);
    }

    /**
     * 保证总护盾至少达到 amount，保持旧版“取现有护盾与新护盾较大值”的语义。
     */
    public static void ensureAtLeast(LivingEntity player, String source, float amount, int durationTicks) {
        if (amount <= 0.0f || durationTicks <= 0) return;
        Map<String, ShieldLayer> layers = LAYERS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        pruneExpired(player, layers);
        float other = 0.0f;
        for (Map.Entry<String, ShieldLayer> entry : layers.entrySet()) {
            if (!entry.getKey().equals(source)) other += entry.getValue().remaining;
        }
        float contribution = Math.max(0.0f, amount - other);
        if (contribution > 0.0f) {
            layers.put(source, new ShieldLayer(contribution, player.level().getGameTime() + durationTicks));
        } else {
            layers.remove(source);
        }
        updateValue(player, layers, true);
    }

    /** 用最终伤害消耗护盾，返回被护盾吸收的实际数值。 */
    public static float consume(LivingEntity player, float damage) {
        if (damage <= 0.0f) return 0.0f;
        Map<String, ShieldLayer> layers = LAYERS.get(player.getUUID());
        if (layers == null || layers.isEmpty()) return 0.0f;
        pruneExpired(player, layers);
        if (layers.isEmpty()) {
            updateValue(player, layers, player instanceof ServerPlayer);
            return 0.0f;
        }
        float left = damage;
        while (left > 0.0f && !layers.isEmpty()) {
            String earliestKey = null;
            ShieldLayer earliest = null;
            for (Map.Entry<String, ShieldLayer> entry : layers.entrySet()) {
                if (earliest == null || entry.getValue().expiresAt < earliest.expiresAt) {
                    earliestKey = entry.getKey();
                    earliest = entry.getValue();
                }
            }
            float used = Math.min(left, earliest.remaining);
            earliest.remaining -= used;
            left -= used;
            if (earliest.remaining <= 0.001f) layers.remove(earliestKey);
        }
        float absorbed = damage - left;
        updateValue(player, layers, absorbed > 0.0f);
        return absorbed;
    }

    public static void removeSource(LivingEntity player, String source) {
        Map<String, ShieldLayer> layers = LAYERS.get(player.getUUID());
        if (layers == null || layers.remove(source) == null) return;
        updateValue(player, layers, true);
    }

    /** Refresh one source without letting a weaker recast reduce that source's shield. */
    public static void grantMaxSource(LivingEntity player, String source, float amount, int durationTicks) {
        if (amount <= 0 || durationTicks <= 0) return;
        var layers = LAYERS.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        pruneExpired(player, layers);
        var existing = layers.get(source);
        layers.put(source, new ShieldLayer(existing == null ? amount : Math.max(amount, existing.remaining),
                player.level().getGameTime() + durationTicks));
        updateValue(player, layers, true);
    }

    /** 每 tick 清除到期层，同时阻止原版吸收生命与模组护盾并存。 */
    public static void tick(ServerPlayer player) {
        if (player.getAbsorptionAmount() > 0.0f) player.setAbsorptionAmount(0.0f);
        Map<String, ShieldLayer> layers = LAYERS.get(player.getUUID());
        if (layers == null) {
            PlayerData data = PlayerDataProvider.get(player);
            if (data != null && data.getModShield() > 0.0f) {
                data.setModShield(0.0f);
                PlayerDataProvider.syncToClient(player);
            }
            return;
        }
        long now = player.level().getGameTime();
        boolean changed = false;
        Iterator<ShieldLayer> it = layers.values().iterator();
        while (it.hasNext()) {
            if (now >= it.next().expiresAt) {
                it.remove();
                changed = true;
            }
        }
        if (changed) updateValue(player, layers, true);
    }

    public static void clearFor(ServerPlayer player) {
        LAYERS.remove(player.getUUID());
        PlayerData data = PlayerDataProvider.get(player);
        if (data != null) data.setModShield(0.0f);
        if (player.getAbsorptionAmount() > 0.0f) player.setAbsorptionAmount(0.0f);
        PlayerDataProvider.syncToClient(player);
    }

    /** 迁移旧符石护盾，保留所有角色护盾层。 */
    public static void removeRetiredRuneSources(LivingEntity entity) {
        var layers = LAYERS.get(entity.getUUID());
        if (layers != null && layers.keySet().removeIf(source -> source.startsWith("rune_")))
            updateValue(entity, layers, entity instanceof ServerPlayer);
    }

    public static void clearFor(UUID playerId) {
        LAYERS.remove(playerId);
    }

    public static void clearAll() {
        LAYERS.clear();
    }

    public static float value(LivingEntity entity) {
        Map<String, ShieldLayer> layers = LAYERS.get(entity.getUUID());
        if (layers == null) return 0.0f;
        long now = entity.level().getGameTime();
        layers.values().removeIf(layer -> now >= layer.expiresAt);
        float total = 0.0f;
        for (ShieldLayer layer : layers.values()) total += Math.max(0.0f, layer.remaining);
        if (layers.isEmpty()) LAYERS.remove(entity.getUUID());
        if (entity instanceof ServerPlayer player) {
            PlayerData data = PlayerDataProvider.get(player);
            if (data != null) data.setModShield(total);
        }
        return total;
    }

    public static boolean hasSource(LivingEntity entity, String source) {
        Map<String, ShieldLayer> layers = LAYERS.get(entity.getUUID());
        if (layers == null) return false;
        pruneExpired(entity, layers);
        ShieldLayer layer = layers.get(source);
        if (layers.isEmpty()) LAYERS.remove(entity.getUUID());
        return layer != null && layer.remaining > 0.001F;
    }

    private static void updateValue(LivingEntity player, Map<String, ShieldLayer> layers, boolean sync) {
        float total = 0.0f;
        for (ShieldLayer layer : layers.values()) total += Math.max(0.0f, layer.remaining);
        if (layers.isEmpty()) LAYERS.remove(player.getUUID());
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerData data = PlayerDataProvider.get(serverPlayer);
            if (data != null) data.setModShield(total);
            if (sync) PlayerDataProvider.syncToClient(serverPlayer);
        }
    }

    private static void pruneExpired(LivingEntity entity, Map<String, ShieldLayer> layers) {
        long now = entity.level().getGameTime();
        layers.values().removeIf(layer -> now >= layer.expiresAt);
    }

    private static final class ShieldLayer {
        private float remaining;
        private final long expiresAt;

        private ShieldLayer(float remaining, long expiresAt) {
            this.remaining = remaining;
            this.expiresAt = expiresAt;
        }
    }
}
