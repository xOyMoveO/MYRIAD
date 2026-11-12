package com.myriad.game;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 假人系统（训练目标）：
 * 以村民为载体（站桩无 AI），标记 UUID 集合识别；
 * 指令 /myriad jiaren create 生成假人，remove 清除周围 5 格；
 * 右键假人打开属性设置界面（生命/物攻/物防/法防）。
 * 假人血量由 mod 独立跟踪（MAX_HP=100000），因原版血量上限 1024 无法表达大血量；
 * 假人受击时只扣 mod 血量，原版血量保持满值，不会死亡。
 */
public final class JiarenManager {
    private JiarenManager() {}

    /** 假人最大生命（mod 独立跟踪） */
    public static final float MAX_HP = 100000.0f;

    private static final Set<UUID> JIAREN = new HashSet<>();
    private static final Map<UUID, Float> HP = new HashMap<>();
    private static final Map<UUID, UUID> FRIENDLY_OWNER = new HashMap<>();

    public static boolean isJiaren(UUID uuid) {
        return JIAREN.contains(uuid);
    }

    public static boolean isFriendlyDummy(ServerPlayer player, LivingEntity entity) {
        return player.getUUID().equals(FRIENDLY_OWNER.get(entity.getUUID()));
    }

    /** 当前 mod 血量 */
    public static float hp(UUID uuid) {
        return HP.getOrDefault(uuid, MAX_HP);
    }

    /** 创建假人：原地生成 100000 血站桩村民（血量走 mod 跟踪） */
    public static void create(ServerPlayer player) {
        createInternal(player, false);
    }

    /** 生成仅对创建者视为友军的训练灵偶，可用于治疗、护盾与牵丝测试。 */
    public static void createFriendly(ServerPlayer player) {
        createInternal(player, true);
    }

    private static void createInternal(ServerPlayer player, boolean friendly) {
        ServerLevel level = (ServerLevel) player.level();
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (villager == null) {
            return;
        }
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1);
        villager.setPos(player.getX() + forward.x * 3, player.getY(), player.getZ() + forward.z * 3);
        villager.setNoAi(true);
        villager.setCustomName(friendly ? Component.literal("友方灵偶").withStyle(net.minecraft.ChatFormatting.GREEN)
                : Component.translatable("entity.myriad.jiaren"));
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(false);
        // 原版血量有上限（1024），假人血量走 mod 独立跟踪；原版血量保持满值避免误伤死亡
        villager.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
        var physicalDefense = villager.getAttribute(com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE);
        var magicDefense = villager.getAttribute(com.myriad.registry.ModAttributes.MAGIC_RESISTANCE);
        if (physicalDefense != null) physicalDefense.setBaseValue(0.0);
        if (magicDefense != null) magicDefense.setBaseValue(0.0);
        villager.setHealth(20.0f);
        level.addFreshEntity(ModEntityTracker.markGenerated(villager));
        JIAREN.add(villager.getUUID());
        if (friendly) FRIENDLY_OWNER.put(villager.getUUID(), player.getUUID());
        HP.put(villager.getUUID(), MAX_HP);
        player.sendSystemMessage(Component.translatable("message.myriad.jiaren_created"));
    }

    public static double physicalDefense(LivingEntity jiaren) {
        var attribute = jiaren.getAttribute(com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE);
        return attribute == null ? 0.0 : attribute.getValue();
    }

    public static double magicDefense(LivingEntity jiaren) {
        var attribute = jiaren.getAttribute(com.myriad.registry.ModAttributes.MAGIC_RESISTANCE);
        return attribute == null ? 0.0 : attribute.getValue();
    }

    /** 服务端校验后的假人双防设置，范围与属性注册上限保持一致。 */
    public static void setDefenses(LivingEntity jiaren, double physical, double magic) {
        if (!isJiaren(jiaren.getUUID())) return;
        var pd = jiaren.getAttribute(com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE);
        var mr = jiaren.getAttribute(com.myriad.registry.ModAttributes.MAGIC_RESISTANCE);
        if (pd != null) pd.setBaseValue(net.minecraft.util.Mth.clamp(physical, 0.0, 1000.0));
        if (mr != null) mr.setBaseValue(net.minecraft.util.Mth.clamp(magic, 0.0, 1000.0));
    }

    /** 假人受击：只减少 mod 血量（打空后重置满血，训练目标常驻） */
    public static void damage(LivingEntity jiaren, float amount) {
        UUID id = jiaren.getUUID();
        float hp = Math.max(0.0f, HP.getOrDefault(id, MAX_HP) - amount);
        HP.put(id, hp);
        if (hp <= 0.0f) {
            HP.put(id, MAX_HP);
        }
    }

    /** 清除周围 5 格内假人 */
    public static void removeNearby(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        int removed = 0;
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(5.0), v -> isJiaren(v.getUUID()))) {
            villager.discard();
            JIAREN.remove(villager.getUUID());
            HP.remove(villager.getUUID());
            FRIENDLY_OWNER.remove(villager.getUUID());
            removed++;
        }
        player.sendSystemMessage(Component.translatable("message.myriad.jiaren_removed", removed));
    }

    /** 新一轮模组玩法开启时清除所有训练假人与其血量缓存。 */
    public static void clearGenerated(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof Villager villager && JIAREN.contains(villager.getUUID())) {
                    villager.discard();
                }
            }
        }
        JIAREN.clear();
        HP.clear();
        FRIENDLY_OWNER.clear();
    }
}
