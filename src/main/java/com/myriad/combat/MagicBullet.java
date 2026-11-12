package com.myriad.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.myriad.character.CharacterType;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 法师普攻魔法弹：手持初始武器右键发射。
 * 伤害 = 法术强度 × 角色系数（墨尘/青萝 50%，寒泽 60%）；射程 15 格、飞行速度 30 格/秒，
 * 发射间隔 = 1 / 攻速 秒（攻速 1.0 → 每秒 1 发，攻速 0.5 → 每 2 秒 1 发）。
 * 每个法师角色的魔法弹特效不同：墨尘 = 黑白粒子螺旋向前。
 */
public final class MagicBullet {
    /** 飞行速度（格/刻） */
    private static final double SPEED = 1.5; // 30 blocks/second at 20 TPS
    /** 射程（格） */
    private static final double MAX_RANGE = 15.0;
    /** 命中判定半径（格） */
    private static final double HIT_RADIUS = 1.05;
    /** 撞方块采样步长（格） */
    private static final double BLOCK_SAMPLE_STEP = 0.25;
    /** 伤害系数：法术强度 × 0.5 */
    private static final double DMG_RATIO = 0.5;
    /** 青萝魔法弹：明亮的青绿色光球。 */
    private static final DustParticleOptions QINGLUO_ORB = new DustParticleOptions(0x62F578, 1.65F);
    private static final DustParticleOptions QINGLUO_GLOW = new DustParticleOptions(0xC5FF9C, .95F);
    private static final DustParticleOptions YUNSHENG_ORB = new DustParticleOptions(0x75F2E5, 1.45F);
    private static final DustParticleOptions YUNSHENG_GLOW = new DustParticleOptions(0xEDFFF9, .8F);
    /** 观澜魔法弹：青色星核与蓝色外辉。 */
    private static final DustParticleOptions GUANLAN_ORB = new DustParticleOptions(0x42E6FF, 1.55F);
    private static final DustParticleOptions GUANLAN_GLOW = new DustParticleOptions(0x397CFF, .95F);
    private static final DustParticleOptions LUOHENG_BEAD = new DustParticleOptions(0x55EAF2, 1.25F);
    private static final DustParticleOptions LUOHENG_GLOW = new DustParticleOptions(0xF1FDFF, .75F);

    private static final class Bullet {
        Vec3 pos;
        Vec3 dir;
        double traveled;
        float sp;
        float attackMultiplier;
        String characterId;
        int spiralPhase;
    }

    /** 飞行中的魔法弹（玩家 UUID -> 魔法弹列表） */
    private static final Map<UUID, List<Bullet>> BULLETS = new HashMap<>();
    /** 发射冷却（玩家 UUID -> 完全恢复刻与本轮总时长）。 */
    private static final Map<UUID, FireCooldown> FIRE_CD = new HashMap<>();

    private MagicBullet() {
    }

    /** 法师判定：使用初始法器右键发射魔法弹的角色。 */
    public static boolean isMage(ServerPlayer player) {
        PlayerData pd = PlayerDataProvider.get(player);
        if (pd != null && "mingzhu".equals(pd.getCharacterId())) return true;
        return pd != null && pd.getCharacterId() != null
                && (CharacterType.byId(pd.getCharacterId()) == CharacterType.XUANCHEN
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.HANZE
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.QINGLUO
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.YUNSHENG
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.GUANLAN
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.LUOHENG);
    }

    /** 右键尝试发射魔法弹（初始武器 + 法师 + 攻速冷却） */
    public static void tryFire(ServerPlayer player, net.minecraft.world.item.ItemStack held) {
        if (com.myriad.character.ability.AbilityRuntime.channeling(player)
                || PlayerDataProvider.get(player).getBuff("g1_stun") > 0) return;
        if (!com.myriad.game.ModState.isEnabled()) {
            return;
        }
        if (!PlayerDataProvider.isInitialWeapon(held)) {
            return;
        }
        if (!isMage(player)) {
            return;
        }
        if ("mingzhu".equals(PlayerDataProvider.get(player).getCharacterId())) {
            com.myriad.character.skills.mingzhu.MingZhuSkills.fireBasic(player);
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        double as = Math.max(0.05, player.getAttributeValue(
                com.myriad.registry.ModAttributes.ATTACK_SPEED));
        int cd = (int) Math.ceil(20.0 / as);
        FireCooldown previous = FIRE_CD.get(player.getUUID());
        if (previous != null && !BasicAttackSystem.isEarlyReady(now, previous.readyAt, previous.totalTicks)) return;
        float attackMultiplier = previous == null ? 1.0F
                : BasicAttackSystem.recoveryDamageMultiplier(now, previous.readyAt);
        FIRE_CD.put(player.getUUID(), new FireCooldown(now + cd, cd));

        Vec3 dir = player.getLookAngle().normalize();
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 pos = player.getEyePosition().add(dir.scale(0.5));
        Bullet b = new Bullet();
        b.pos = pos;
        b.dir = dir;
        b.traveled = 0;
        b.sp = (float) player.getAttributeValue(com.myriad.registry.ModAttributes.SPELL_POWER);
        if (com.myriad.character.rework.FirstGroupSkills.active(PlayerDataProvider.get(player).getCharacterId()))
            b.sp = (float) (player.getAttributeValue(com.myriad.registry.ModAttributes.PHYSICAL_ATTACK) / DMG_RATIO);
        b.attackMultiplier = attackMultiplier;
        b.characterId = PlayerDataProvider.get(player).getCharacterId();
        b.spiralPhase = 0;
        BULLETS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(b);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.myriad.network.NetworkSetup.MagicBulletCooldownPayload(cd, cd));
        // 起手：枪口迸发 + 音效
        if ("luoheng".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(LUOHENG_BEAD, pos.x, pos.y, pos.z, 14, .12, .12, .12, .015);
            level.sendParticles(LUOHENG_GLOW, pos.x, pos.y, pos.z, 8, .08, .08, .08, .01);
        } else if ("yunsheng".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(YUNSHENG_ORB, pos.x, pos.y, pos.z, 16, .13, .13, .13, .022);
            level.sendParticles(YUNSHENG_GLOW, pos.x, pos.y, pos.z, 9, .08, .08, .08, .015);
        } else if ("guanlan".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(GUANLAN_ORB, pos.x, pos.y, pos.z, 18, .14, .14, .14, .025);
            level.sendParticles(GUANLAN_GLOW, pos.x, pos.y, pos.z, 11, .10, .10, .10, .018);
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 5, .06, .06, .06, .012);
        } else if ("qingluo".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(QINGLUO_ORB, pos.x, pos.y, pos.z, 18, .14, .14, .14, .025);
            level.sendParticles(QINGLUO_GLOW, pos.x, pos.y, pos.z, 10, .09, .09, .09, .018);
        } else if ("hanze".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 14, 0.15, 0.15, 0.15, 0.04);
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 7, 0.1, 0.1, 0.1, 0.02);
        } else {
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 12, 0.15, 0.15, 0.15, 0.04);
            level.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.02);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** 每 tick 推进魔法弹（PlayerDataProvider 调用） */
    public static void tickBullets(ServerPlayer player, PlayerData data) {
        List<Bullet> list = BULLETS.get(player.getUUID());
        if (list == null || list.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        list.removeIf(b -> tickOne(level, player, b));
        if (list.isEmpty()) {
            BULLETS.remove(player.getUUID());
        }
    }

    /** 推进单颗魔法弹；返回 true 表示已消耗（命中、撞墙或射程耗尽） */
    private static boolean tickOne(ServerLevel level, ServerPlayer caster, Bullet b) {
        Vec3 next = b.pos.add(b.dir.scale(SPEED));
        if (com.myriad.combat.WindWallSystem.firstEnemyHit(caster, b.pos, next) != null) {
            return true;
        }
        LivingEntity target = findFirstEnemy(level, caster, b.pos, next);
        if (target != null) {
            float dmg = (float) (b.sp * damageRatio(b.characterId) * b.attackMultiplier);
            DamageCalc.dealBasicDamage(caster, target, dmg, DamageCalc.Kind.MAGIC);
            // 命中爆散特效
            if ("luoheng".equalsIgnoreCase(b.characterId)) {
                level.sendParticles(LUOHENG_BEAD, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(), 20, .28, .28, .28, .03);
                level.sendParticles(LUOHENG_GLOW, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(), 12, .2, .2, .2, .02);
            } else if ("yunsheng".equalsIgnoreCase(b.characterId)) {
                level.sendParticles(YUNSHENG_ORB, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        22, .3, .3, .3, .04);
                level.sendParticles(YUNSHENG_GLOW, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        12, .2, .2, .2, .03);
            } else if ("guanlan".equalsIgnoreCase(b.characterId)) {
                level.sendParticles(GUANLAN_ORB, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        26, .34, .34, .34, .045);
                level.sendParticles(GUANLAN_GLOW, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        18, .27, .27, .27, .035);
            } else if ("qingluo".equalsIgnoreCase(b.characterId)) {
                level.sendParticles(QINGLUO_ORB, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        24, .32, .32, .32, .045);
                level.sendParticles(QINGLUO_GLOW, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        14, .22, .22, .22, .035);
            } else {
                level.sendParticles("hanze".equalsIgnoreCase(b.characterId) ? ParticleTypes.SNOWFLAKE : ParticleTypes.SCULK_SOUL,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        18, 0.35, 0.35, 0.35, 0.05);
            }
            level.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    10, 0.25, 0.25, 0.25, 0.04);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.1f);
            return true;
        }
        Vec3 blockHit = firstBlockHit(level, b.pos, next);
        if (blockHit != null) {
            // 撞到方块直接消失：不播放墨尘专属的幽魂消散特效，也不复用其他角色的命中表现。
            return true;
        }
        b.traveled += SPEED;
        if (b.traveled >= maxRange(b.characterId)) {
            if ("guanlan".equalsIgnoreCase(b.characterId)) {
                level.sendParticles(GUANLAN_ORB, next.x, next.y, next.z, 10, .16, .16, .16, .025);
                level.sendParticles(GUANLAN_GLOW, next.x, next.y, next.z, 7, .12, .12, .12, .018);
            } else {
                level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z,
                        8, 0.15, 0.15, 0.15, 0.02);
            }
            return true;
        }
        // 飞行拖尾：按角色区分特效
        spawnTrail(level, b, next);
        b.pos = next;
        b.spiralPhase++;
        return false;
    }

    /** 飞行拖尾特效（按角色区分；墨尘 = 黑白粒子螺旋向前） */
    private static void spawnTrail(ServerLevel level, Bullet b, Vec3 pos) {
        if ("luoheng".equalsIgnoreCase(b.characterId)) {
            // 青白方块算珠：紧凑方核与四角白色刻度。
            level.sendParticles(LUOHENG_BEAD, pos.x, pos.y, pos.z, 4, .055, .055, .055, .002);
            Vec3 axis=b.dir,up=Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0),side=axis.cross(up).normalize(),vert=side.cross(axis).normalize();
            double phase=(b.spiralPhase&1)==0?.16:-.16;
            for(Vec3 off:List.of(side.scale(phase).add(vert.scale(phase)),side.scale(-phase).add(vert.scale(phase)),side.scale(phase).add(vert.scale(-phase)),side.scale(-phase).add(vert.scale(-phase))))
                level.sendParticles(LUOHENG_GLOW,pos.x+off.x,pos.y+off.y,pos.z+off.z,1,0,0,0,0);
        } else if ("yunsheng".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(YUNSHENG_ORB, pos.x, pos.y, pos.z, 5, .08, .08, .08, .006);
            level.sendParticles(YUNSHENG_GLOW, pos.x, pos.y, pos.z, 2, .04, .04, .04, .003);
        } else if ("guanlan".equalsIgnoreCase(b.characterId)) {
            // 青色星核 + 蓝色双螺旋外辉，保持紧凑以免遮挡准星。
            level.sendParticles(GUANLAN_ORB, pos.x, pos.y, pos.z, 5, .075, .075, .075, .006);
            Vec3 axis = b.dir;
            Vec3 up = Math.abs(axis.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 side = axis.cross(up).normalize();
            Vec3 vert = side.cross(axis).normalize();
            double angle = b.spiralPhase * Math.PI / 5.0;
            Vec3 offset = side.scale(Math.cos(angle) * .23).add(vert.scale(Math.sin(angle) * .23));
            Vec3 first = pos.add(offset), second = pos.subtract(offset);
            level.sendParticles(GUANLAN_GLOW, first.x, first.y, first.z, 1, 0, 0, 0, 0);
            level.sendParticles(GUANLAN_GLOW, second.x, second.y, second.z, 1, 0, 0, 0, 0);
        } else if ("qingluo".equalsIgnoreCase(b.characterId)) {
            // 两层 Dust 聚成紧凑球体，中心亮、外围青绿，拖尾不遮挡视线。
            level.sendParticles(QINGLUO_ORB, pos.x, pos.y, pos.z, 7, .105, .105, .105, .008);
            level.sendParticles(QINGLUO_GLOW, pos.x, pos.y, pos.z, 3, .055, .055, .055, .004);
        } else if ("xuanchen".equalsIgnoreCase(b.characterId)) {
            // 黑白螺旋：以弹道为轴旋转，黑白两色交替
            Vec3 axis = b.dir;
            Vec3 up = Math.abs(axis.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 side = axis.cross(up).normalize();
            Vec3 vert = side.cross(axis).normalize();
            double angle = b.spiralPhase * Math.PI / 6.0;
            double r = 0.35;
            Vec3 offset = side.scale(Math.cos(angle) * r).add(vert.scale(Math.sin(angle) * r));
            Vec3 black = pos.add(offset.scale(1.0));
            Vec3 white = pos.add(offset.scale(-1.0));
            level.sendParticles(ParticleTypes.SCULK_SOUL, black.x, black.y, black.z,
                    1, 0.02, 0.02, 0.02, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, white.x, white.y, white.z,
                    1, 0.02, 0.02, 0.02, 0.01);
            // 弹头一点白光
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z,
                    1, 0.03, 0.03, 0.03, 0.01);
        } else if ("hanze".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z,
                    3, 0.05, 0.05, 0.05, 0.01);
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z,
                    1, 0.025, 0.025, 0.025, 0.005);
        }
    }

    private static double damageRatio(String characterId) {
        return "hanze".equalsIgnoreCase(characterId) ? 0.6 : DMG_RATIO;
    }

    private static double maxRange(String characterId) {
        return MAX_RANGE;
    }

    private static Vec3 firstBlockHit(ServerLevel level, Vec3 from, Vec3 to) {
        double dist = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(dist / BLOCK_SAMPLE_STEP));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, (double) i / steps);
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(p.x, p.y, p.z);
            if (level.getBlockState(bp).isSolid()) {
                return p;
            }
        }
        return null;
    }

    private static LivingEntity findFirstEnemy(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 to) {
        AABB box = new AABB(
                Math.min(from.x, to.x) - HIT_RADIUS, Math.min(from.y, to.y) - HIT_RADIUS, Math.min(from.z, to.z) - HIT_RADIUS,
                Math.max(from.x, to.x) + HIT_RADIUS, Math.max(from.y, to.y) + HIT_RADIUS, Math.max(from.z, to.z) + HIT_RADIUS);
        Vec3 delta = to.subtract(from);
        double lenSqr = delta.lengthSqr();
        LivingEntity best = null;
        double bestT = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && e.isAlive()
                        && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
                        && !(e instanceof net.minecraft.world.entity.decoration.Mannequin)
                        && !isAlly(caster, e))) {
            Vec3 rel = e.getBoundingBox().getCenter().subtract(from);
            double t = rel.dot(delta) / lenSqr;
            if (t < 0 || t > 1) {
                continue;
            }
            Vec3 closest = from.add(delta.scale(t));
            double radius = HIT_RADIUS + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.3;
            if (closest.distanceToSqr(e.getBoundingBox().getCenter()) <= radius * radius && t < bestT) {
                bestT = t;
                best = e;
            }
        }
        return best;
    }

    private static boolean isAlly(ServerPlayer caster, LivingEntity other) {
        return SkillTargets.ally(caster, other);
    }

    /** 玩家退出时清理其全部飞行中的魔法弹 */
    public static void clearFor(UUID playerId) {
        BULLETS.remove(playerId);
        FIRE_CD.remove(playerId);
    }

    private record FireCooldown(long readyAt, int totalTicks) { }

    /** 风墙：清除位置满足条件的飞行中魔法弹（阻挡投射物） */
    public static void blockIn(ServerLevel level, java.util.function.Predicate<Vec3> inside) {
        blockIn(level, owner -> true, inside);
    }

    public static void blockIn(ServerLevel level, java.util.function.Predicate<UUID> owners, java.util.function.Predicate<Vec3> inside) {
        if (BULLETS.isEmpty()) {
            return;
        }
        for (java.util.Iterator<Map.Entry<UUID, List<Bullet>>> it = BULLETS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, List<Bullet>> entry = it.next();
            if (!owners.test(entry.getKey())) continue;
            List<Bullet> list = entry.getValue();
            if (list == null || list.isEmpty()) {
                it.remove();
                continue;
            }
            list.removeIf(b -> inside.test(b.pos));
            if (list.isEmpty()) {
                it.remove();
            }
        }
    }
}
