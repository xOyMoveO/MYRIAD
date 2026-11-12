package com.myriad.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.math.Transformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared freeze ownership and shell lifecycle; cleansing one target does not clear its skills. */
public final class FrozenControlSystem {
    private FrozenControlSystem() {}
    private static final Map<UUID, FrozenState> FROZEN_TARGETS = new HashMap<>();
    private static final Map<UUID, FrozenVisual> FROZEN_VISUALS = new HashMap<>();
    public static boolean isFrozenBy(ServerPlayer attacker, LivingEntity target, long now) {
        FrozenState frozen = FROZEN_TARGETS.get(target.getUUID());
        if (frozen == null || frozen.until <= now) {
            FROZEN_TARGETS.remove(target.getUUID());
            return false;
        }
        return frozen.owner.equals(attacker.getUUID());
    }

    /** 施加定时冻结并绑定浮冰展示；使用真实游戏时间到期。 */
    public static void apply(ServerLevel level, ServerPlayer owner, LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 255, false, false, false));
        FROZEN_TARGETS.put(target.getUUID(), new FrozenState(owner.getUUID(), level.getGameTime() + duration));
        spawnFrozenVisual(level, owner.getUUID(), target);
    }

    /** 将三枚半透明冰块固定在被冻结目标躯干周围，纯表现、不碰撞。 */
    private static void spawnFrozenVisual(ServerLevel level, UUID owner, LivingEntity target) {
        removeFrozenVisual(target.getUUID());
        List<ItemDisplay> blocks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof ItemDisplay display) {
                display.getSlot(0).set(new ItemStack(Blocks.PACKED_ICE));
                display.setItemTransform(ItemDisplayContext.FIXED);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(.48F, .48F, .48F), new Quaternionf()));
                display.setGlowColorOverride(-16736001);
                display.setPos(target.getX(), target.getY(), target.getZ());
                level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(display));
                blocks.add(display);
            }
        }
        if (!blocks.isEmpty()) FROZEN_VISUALS.put(target.getUUID(), new FrozenVisual(owner, blocks));
    }

    /** 每刻将冰块展示实体贴合目标；冰冻消失时立即回收，避免残留实体。 */
    public static void tick(ServerPlayer player, ServerLevel level) {
        long now = level.getGameTime();
        java.util.Iterator<Map.Entry<UUID, FrozenVisual>> iterator = FROZEN_VISUALS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FrozenVisual> entry = iterator.next();
            // 只推进该来源施加的冰冻展示，不能清理其他来源的效果。
            if (!entry.getValue().owner.equals(player.getUUID())) continue;
            FrozenState frozen = FROZEN_TARGETS.get(entry.getKey());
            Entity entity = level.getEntity(entry.getKey());
            if (frozen == null || frozen.until <= now
                    || !(entity instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target)) {
                discardDisplays(entry.getValue().blocks);
                iterator.remove();
                if (frozen != null && frozen.until <= now) FROZEN_TARGETS.remove(entry.getKey());
                continue;
            }
            for (int i = 0; i < entry.getValue().blocks.size(); i++) {
                ItemDisplay display = entry.getValue().blocks.get(i);
                if (!display.isAlive()) continue;
                double angle = level.getGameTime() * .12 + Math.PI * 2 * i / 3.0;
                display.setPos(target.getX() + Math.cos(angle) * .42,
                        target.getY() + .28 + i * .43, target.getZ() + Math.sin(angle) * .42);
            }
        }
    }

    private static void removeFrozenVisual(UUID targetId) {
        FrozenVisual visual = FROZEN_VISUALS.remove(targetId);
        if (visual != null) discardDisplays(visual.blocks);
    }

    private static void discardDisplays(List<ItemDisplay> displays) {
        for (ItemDisplay display : displays) if (display.isAlive()) display.discard();
    }

    /** Remove this target's frozen state and shell only. The control cleanser removes movement effects separately. */
    public static void cleanse(UUID targetId) {
        FROZEN_TARGETS.remove(targetId);
        removeFrozenVisual(targetId);
    }

    public static void clearFor(UUID playerId) {
        FROZEN_TARGETS.entrySet().removeIf(entry -> entry.getKey().equals(playerId)
                || entry.getValue().owner.equals(playerId));
        java.util.Iterator<Map.Entry<UUID, FrozenVisual>> visuals = FROZEN_VISUALS.entrySet().iterator();
        while (visuals.hasNext()) {
            Map.Entry<UUID, FrozenVisual> entry = visuals.next();
            if (entry.getKey().equals(playerId) || entry.getValue().owner.equals(playerId)) {
                discardDisplays(entry.getValue().blocks);
                visuals.remove();
            }
        }
    }
    private record FrozenState(UUID owner, long until) {}
    private record FrozenVisual(UUID owner, List<ItemDisplay> blocks) {}
}
