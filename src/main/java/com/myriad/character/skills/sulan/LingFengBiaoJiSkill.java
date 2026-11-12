package com.myriad.character.skills.sulan;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.myriad.character.Skill;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;

/**
 * 风部·素岚 W 技能【灵风标记】：标记一名敌方，使其 10 秒内持续发光
 * 并暴露位置给全幽冥宗队友；目标受到的所有伤害提升 10%，
 * 且被标记期间受到 15% 减速（持续 8 秒）。施法时从施法者释放一个灵球飞向目标。
 */
public class LingFengBiaoJiSkill extends Skill {
    private static final int MARK_TICKS = 200;
    private static final int SLOW_TICKS = 160;
    private static final double MARK_RANGE = 20.0;

    /** 被标记目标：受害者 UUID -> 到期刻 */
    private static final Map<UUID, Long> MARKED = new HashMap<>();

    /** 标记灵球：施法者 UUID -> 灵球 */
    private static final class Orb {
        Vec3 from;
        UUID targetId;
        int remaining;
    }

    private static final Map<UUID, Orb> ORBS = new HashMap<>();

    public LingFengBiaoJiSkill() {
        super("lingfengbiaoji", 300, 90.0F);
    }

    @Override
    public int getCooldownTicks(int level) {
        return Math.max(20, 300 - Math.max(0, level - 1) * 20);
    }

    @Override
    public String levelEffects(int level) {
        return "Lv." + level + "：标记玩家看向的敌方（20格内，减速15% 8秒）并暴露位置；目标受到伤害+10%";
    }

    @Override
    public void cast(ServerPlayer player, PlayerData data) {
        LivingEntity target = findMarkTarget(player);
        if (target == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0, false, false, false));
        MARKED.put(target.getUUID(), now + MARK_TICKS);
        // 从施法者释放一个灵球飞向目标
        Orb orb = new Orb();
        orb.from = player.getEyePosition();
        orb.targetId = target.getUUID();
        orb.remaining = 12;
        ORBS.put(player.getUUID(), orb);
        // 起手：施法者手心迸发一点灵光
        level.sendParticles(ParticleTypes.END_ROD,
                orb.from.x, orb.from.y, orb.from.z,
                8, 0.12, 0.12, 0.12, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** 目标是否被灵风标记（受到伤害 +10%） */
    public static boolean isMarked(LivingEntity entity) {
        return MARKED.containsKey(entity.getUUID());
    }

    /** 每 tick：过期清理 + 维持发光 + 推进标记灵球（PlayerDataProvider 调用） */
    public static void tick(ServerPlayer player, PlayerData data) {
        long now = player.level().getGameTime();
        for (Iterator<Map.Entry<UUID, Long>> it = MARKED.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now >= entry.getValue()) {
                it.remove();
                continue;
            }
            net.minecraft.world.entity.Entity ent = player.level().getEntity(entry.getKey());
            if (ent instanceof LivingEntity le && le.isAlive()) {
                if (!le.hasEffect(MobEffects.GLOWING)) {
                    le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
                }
            } else {
                it.remove();
            }
        }
        advanceOrb(player);
    }

    /** 灵球：从施法者飞向目标（12 刻），到达后命中爆散 */
    private static void advanceOrb(ServerPlayer player) {
        Orb orb = ORBS.get(player.getUUID());
        if (orb == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        net.minecraft.world.entity.Entity ent = level.getEntity(orb.targetId);
        Vec3 to = ent instanceof LivingEntity le && le.isAlive()
                ? le.getBoundingBox().getCenter()
                : orb.from.add(1, 0, 0);
        int total = 12;
        int elapsed = total - orb.remaining;
        double progress = Math.min(1.0, elapsed / (double) total);
        Vec3 p = orb.from.lerp(to, progress);
        // 灵球本体：发光小球
        level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z,
                3, 0.1, 0.1, 0.1, 0.01);
        level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z,
                1, 0.06, 0.06, 0.06, 0.01);
        // 尾迹
        for (int i = 1; i <= 2; i++) {
            double tt = Math.max(0.0, progress - i * 0.1);
            Vec3 tp = orb.from.lerp(to, tt);
            level.sendParticles(ParticleTypes.END_ROD, tp.x, tp.y, tp.z,
                    1, 0.05, 0.05, 0.05, 0.005);
        }
        orb.remaining--;
        if (orb.remaining <= 0) {
            ORBS.remove(player.getUUID());
            // 到达目标：命中爆散
            level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z,
                    18, 0.5, 0.5, 0.5, 0.06);
            level.sendParticles(ParticleTypes.CLOUD, to.x, to.y, to.z,
                    8, 0.3, 0.3, 0.3, 0.04);
            level.playSound(null, to.x, to.y, to.z,
                    com.myriad.registry.ModSounds.foley("wind"), SoundSource.PLAYERS, 0.5f, 1.5f);
        }
    }

    public static void clearFor(UUID playerId) {
        ORBS.remove(playerId);
        // 标记挂在目标上，施法者退出不清除（10 秒自然过期）
    }

    /** 玩家视线 20 格内第一个被看向的敌方目标（准星指向判定） */
    private static LivingEntity findMarkTarget(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 from = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        if (dir.lengthSqr() < 1.0E-4) {
            return null;
        }
        Vec3 to = from.add(dir.scale(MARK_RANGE));
        AABB box = new AABB(
                Math.min(from.x, to.x) - 0.8, Math.min(from.y, to.y) - 0.8, Math.min(from.z, to.z) - 0.8,
                Math.max(from.x, to.x) + 0.8, Math.max(from.y, to.y) + 0.8, Math.max(from.z, to.z) + 0.8);
        Vec3 delta = to.subtract(from);
        double lenSqr = delta.lengthSqr();
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !isAlly(player, e))) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(from);
            double t = rel.dot(delta) / lenSqr;
            if (t < 0 || t > 1) {
                continue;
            }
            Vec3 closest = from.add(delta.scale(t));
            double radius = 0.6 + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.4;
            if (closest.distanceToSqr(e.getBoundingBox().getCenter()) <= radius * radius && t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }

    private static boolean isAlly(ServerPlayer caster, LivingEntity other) {
        if (!(other instanceof ServerPlayer otherPlayer)) {
            return false;
        }
        GameManager manager = GameManager.get();
        if (manager == null) {
            return false;
        }
        Faction a = manager.factionOf(caster);
        Faction b = manager.factionOf(otherPlayer);
        return a != null && a == b;
    }
}
