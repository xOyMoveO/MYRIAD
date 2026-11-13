package com.myriad.combat;

import java.util.HashMap;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;

/** Shared wind-wall state, real-time expiry, swept interception and presentation. */
public final class WindWallSystem {
    private WindWallSystem() {}
    private static final double WALL_LENGTH = 6.0;
    private static final double WALL_HEIGHT = 4.0;
    private static final double WALL_HALF_DEPTH = 0.3;
    private static final double WALL_DIST = 2.0;
    /** 风墙从施法者出发向前滑动的动画时长（刻） */
    private static final int ANIM_TICKS = 10;

    private static final class Wall {
        ServerLevel level;
        Vec3 center;
        Vec3 from;
        Vec3 to;
        Vec3 flat;
        Vec3 right;
        double baseY;
        long until;
        int animTicks;
        boolean slowEnemies;
    }

    private static final Map<UUID, Wall> WALLS = new HashMap<>();

    public static void createWall(ServerPlayer player, PlayerData data, int duration, boolean slowEnemies) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();
        // 风墙从施法者位置出发，向前方滑动 2 格后停下
        Vec3 from = player.position();
        Wall wall = new Wall();
        wall.level = player.level();
        wall.from = from;
        wall.to = from.add(flat.scale(WALL_DIST));
        wall.center = from;
        wall.flat = flat;
        wall.right = new Vec3(-flat.z, 0, flat.x); // 切线方向（垂直于视线）
        wall.baseY = player.getY();
        wall.until = player.level().getGameTime() + duration;
        wall.slowEnemies = slowEnemies;
        wall.animTicks = ANIM_TICKS;
        WALLS.put(player.getUUID(), wall);
        data.setBuff("fengqiang", duration);
        ServerLevel level = (ServerLevel) player.level();
        // 释放音效：一阵风的呼啸
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.9f, 1.4f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.6f, 1.1f);
        if(!com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId()))
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), wall.baseY + 1.0, player.getZ(),
                24, 0.5, 0.8, 0.5, 0.04);
    }

    /** 每 tick：渲染风墙（含滑动动画）+ 阻挡投射物 + 穿过减速 + 音效（PlayerDataProvider 调用） */
    public static void tick(ServerPlayer player, PlayerData data) {
        Wall wall = WALLS.get(player.getUUID());
        if (wall == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        if (now >= wall.until) {
            WALLS.remove(player.getUUID());
            data.setBuff("fengqiang", 0);
            return;
        }
        // 滑动动画：从施法者出发向前移动 2 格
        if (wall.animTicks > 0) {
            wall.animTicks--;
            double progress = 1.0 - wall.animTicks / (double) ANIM_TICKS;
            Vec3 c = wall.from.lerp(wall.to, progress);
            wall.center = new Vec3(c.x, wall.center.y, c.z);
            // 移动音效：风声掠过
            if (wall.animTicks % 4 == 0) {
                level.playSound(null, wall.center.x, wall.baseY + 1.0, wall.center.z,
                        SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.5f, 1.1f + (float) progress * 0.4f);
            }
        } else if (now % 20 == 0) {
            // 常驻音效：风墙持续呜鸣
            level.playSound(null, wall.center.x, wall.baseY + 1.5, wall.center.z,
                    SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS,
                    0.45f, 0.85f + level.getRandom().nextFloat() * 0.3f);
        }
        AABB box = buildBox(wall);
        if(com.myriad.character.rework.FirstGroupSkills.active(data.getCharacterId())){
            if(now%2==0)com.myriad.character.rework.FirstGroupSkills.candidates(data.getCharacterId()).stream()
                    .filter(skill->skill instanceof com.myriad.character.skills.sulan.rework.WindBarrierSkill)
                    .findFirst().ifPresent(skill->com.myriad.character.ability.AbilityVisuals.wall(
                            com.myriad.character.ability.AbilityRuntime.context(player,skill),wall.center,wall.flat,WALL_LENGTH*.5));
        }else renderWall(level, wall);
        // 阻挡飞行投射物（精确判断：仅在风墙 6×4×0.6 内）
        for (Projectile p : level.getEntitiesOfClass(Projectile.class, box.inflate(4.0))) {
            if (p.getOwner() != null && com.myriad.combat.SkillTargets.ally(player, p.getOwner())) continue;
            Vec3 hit = segmentHit(wall, p.position().subtract(p.getDeltaMovement()), p.position());
            if (hit == null) hit = segmentHit(wall, p.position(), p.position().add(p.getDeltaMovement()));
            if (hit == null) continue;
            level.sendParticles(ParticleTypes.CLOUD, hit.x, hit.y, hit.z,
                    10, 0.2, 0.2, 0.2, 0.03);
            level.sendParticles(ParticleTypes.END_ROD, hit.x, hit.y, hit.z,
                    4, 0.15, 0.15, 0.15, 0.02);
            // 被阻挡音效
            level.playSound(null, hit.x, hit.y, hit.z,
                    SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.5f, 1.6f);
            p.discard();
        }
        // 阻挡本模组魔法弹
        MagicBullet.blockIn(level, id -> {
            var caster = level.getServer().getPlayerList().getPlayer(id);
            return !id.equals(player.getUUID()) && (caster == null || !com.myriad.combat.SkillTargets.ally(player, caster));
        }, p -> inWall(wall, p));
        // 穿过减速 50%（2 秒）
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !isAlly(player, e))) {
            if (wall.slowEnemies && inWall(wall, e.position())) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2, false, false, false));
            }
        }
    }

    /** 查询用包围盒（略大于风墙，用于实体搜索，实际判定走 inWall 精确过滤） */
    private static AABB buildBox(Wall wall) {
        double half = WALL_LENGTH / 2;
        Vec3 c = wall.center;
        Vec3 a = c.add(wall.right.scale(-half));
        Vec3 b = c.add(wall.right.scale(half));
        return new AABB(
                Math.min(a.x, b.x) - WALL_HALF_DEPTH, wall.baseY, Math.min(a.z, b.z) - WALL_HALF_DEPTH,
                Math.max(a.x, b.x) + WALL_HALF_DEPTH, wall.baseY + WALL_HEIGHT, Math.max(a.z, b.z) + WALL_HALF_DEPTH);
    }

    /** 精确判定：点是否在风墙内（面向深度 ±0.3、左右 ±3、高度 0~4） */
    private static boolean inWall(Wall wall, Vec3 p) {
        Vec3 rel = p.subtract(wall.center);
        double depth = rel.dot(wall.flat);
        double length = rel.dot(wall.right);
        double height = rel.y;
        return Math.abs(depth) <= WALL_HALF_DEPTH && Math.abs(length) <= WALL_LENGTH / 2.0
                && height >= 0.0 && height <= WALL_HEIGHT;
    }

    /**
     * 判断一个自定义飞行物本 tick 的运动线段是否穿过任意有效风墙。
     * 自定义技能投射物不是原版 Projectile，必须用线段扫掠判定，避免高速穿模。
     */
    /** Read-only clipping for sustained beams: no repeated burst/sound and friendly walls ignored. */
    public static Vec3 firstEnemyHit(ServerPlayer caster, Vec3 from, Vec3 to) {
        Vec3 closest = null;
        Faction own = com.myriad.game.LaneSystem.factionOf(caster);
        for (var entry : WALLS.entrySet()) {
            Wall wall = entry.getValue();
            if (wall.level != caster.level() || wall.until <= caster.level().getGameTime() || entry.getKey().equals(caster.getUUID())) continue;
            ServerPlayer owner = caster.level().getServer().getPlayerList().getPlayer(entry.getKey());
            if (owner != null && own != null && own == com.myriad.game.LaneSystem.factionOf(owner)) continue;
            Vec3 at = segmentHit(wall, from, to);
            if (at != null && (closest == null || from.distanceToSqr(at) < from.distanceToSqr(closest))) closest = at;
        }
        return closest;
    }
    public static boolean blocksProjectile(ServerLevel level, Vec3 from, Vec3 to) {
        long now = level.getGameTime();
        for (Wall wall : WALLS.values()) {
            if (wall.level != level || now >= wall.until) continue;
            Vec3 hit = segmentHit(wall, from, to);
            if (hit == null) continue;
            level.sendParticles(ParticleTypes.CLOUD, hit.x, hit.y, hit.z,
                    10, .2, .2, .2, .03);
            level.sendParticles(ParticleTypes.END_ROD, hit.x, hit.y, hit.z,
                    5, .15, .15, .15, .02);
            level.playSound(null, hit.x, hit.y, hit.z,
                    SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, .5F, 1.6F);
            return true;
        }
        return false;
    }

    /** 将线段转换到风墙本地坐标，对深度、长度、高度三个轴做 slab 相交。 */
    private static Vec3 segmentHit(Wall wall, Vec3 from, Vec3 to) {
        Vec3 a = from.subtract(wall.center);
        Vec3 b = to.subtract(wall.center);
        double[] start = { a.dot(wall.flat), a.dot(wall.right), a.y };
        double[] end = { b.dot(wall.flat), b.dot(wall.right), b.y };
        double[] min = { -WALL_HALF_DEPTH, -WALL_LENGTH / 2.0, 0.0 };
        double[] max = { WALL_HALF_DEPTH, WALL_LENGTH / 2.0, WALL_HEIGHT };
        double enter = 0.0, exit = 1.0;
        for (int axis = 0; axis < 3; axis++) {
            double delta = end[axis] - start[axis];
            if (Math.abs(delta) < 1.0E-8) {
                if (start[axis] < min[axis] || start[axis] > max[axis]) return null;
                continue;
            }
            double first = (min[axis] - start[axis]) / delta;
            double second = (max[axis] - start[axis]) / delta;
            if (first > second) { double swap = first; first = second; second = swap; }
            enter = Math.max(enter, first);
            exit = Math.min(exit, second);
            if (enter > exit) return null;
        }
        return from.lerp(to, enter);
    }

    /** 沿风墙面渲染（右手方向 × 高度 × 面向深度），斜向释放时形状仍为 6×4×0.6 */
    private static void renderWall(ServerLevel level, Wall wall) {
        if (level.getGameTime() % 2 != 0) {
            return;
        }
        for (double len = -WALL_LENGTH / 2; len <= WALL_LENGTH / 2 + 0.01; len += 1.0) {
            for (double h = 0.5; h <= WALL_HEIGHT + 0.01; h += 1.0) {
                for (double d = -WALL_HALF_DEPTH; d <= WALL_HALF_DEPTH + 0.01; d += WALL_HALF_DEPTH) {
                    Vec3 p = wall.center.add(wall.right.scale(len)).add(wall.flat.scale(d));
                    level.sendParticles(ParticleTypes.CLOUD, p.x, wall.baseY + h, p.z,
                            1, 0.04, 0.04, 0.04, 0.01);
                    level.sendParticles(ParticleTypes.END_ROD, p.x, wall.baseY + h, p.z,
                            1, 0.03, 0.03, 0.03, 0.005);
                }
            }
        }
    }

    public static void clearFor(UUID playerId) {
        WALLS.remove(playerId);
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
