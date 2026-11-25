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
import com.myriad.character.ability.ActionTime;
import com.myriad.character.ability.AbilityRuntime;
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
    private static final DustParticleOptions YUNSHENG_ORB = new DustParticleOptions(0x75F2E5, 1.45F);
    private static final DustParticleOptions YUNSHENG_GLOW = new DustParticleOptions(0xEDFFF9, .8F);

    private static final class Bullet {
        final ServerLevel level;
        Bullet(ServerLevel level) { this.level = level; }

        Vec3 pos;
        Vec3 dir;
        double traveled;
        float sp;
        float attackMultiplier;
        String characterId;
        int visualTheme, visualId;
        final UUID flight=UUID.randomUUID();
        long lastTick=Long.MIN_VALUE;int age;
    }

    /** 飞行中的魔法弹（玩家 UUID -> 魔法弹列表） */
    private static final Map<UUID, List<Bullet>> BULLETS = new HashMap<>();
    /** 发射恢复（玩家 UUID -> 已完成的行动量与本轮所需总量）。 */
    private static final Map<UUID, FireCooldown> FIRE_CD = new HashMap<>();

    private MagicBullet() {
    }

    /** 法师判定：使用初始法器右键发射魔法弹的角色。 */
    public static boolean isMage(ServerPlayer player) {
        PlayerData pd = PlayerDataProvider.get(player);

        return pd != null && pd.getCharacterId() != null
                && (CharacterType.byId(pd.getCharacterId()) == CharacterType.XUANCHEN
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.HANZE
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.QINGLUO
                        || CharacterType.byId(pd.getCharacterId()) == CharacterType.YUNSHENG);

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
        if (!isMage(player) || !BasicAttackSystem.canAttackNow(player, PlayerDataProvider.get(player))) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        double as = Math.max(0.05, player.getAttributeValue(
                com.myriad.registry.ModAttributes.ATTACK_SPEED));
        int cd = (int) Math.ceil(20.0 / as);
        FireCooldown previous = FIRE_CD.get(player.getUUID());
        if (previous != null && !previous.progress.advance(now,ActionTime.multiplier(player.getUUID(),now),previous.totalTicks)) return;
        float attackMultiplier = 1.0F;
        FIRE_CD.put(player.getUUID(), new FireCooldown(now,cd));

        Vec3 dir = player.getLookAngle().normalize();
        if (dir.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 pos = player.getEyePosition().add(dir.scale(0.5));
        Bullet b = new Bullet(level);
        b.pos = pos;
        b.dir = dir;
        b.traveled = 0;
        b.sp = (float) player.getAttributeValue(com.myriad.registry.ModAttributes.SPELL_POWER);
        if (com.myriad.character.rework.FirstGroupSkills.active(PlayerDataProvider.get(player).getCharacterId()))
            b.sp = (float) (player.getAttributeValue(com.myriad.registry.ModAttributes.PHYSICAL_ATTACK) / DMG_RATIO);
        b.attackMultiplier = attackMultiplier;
        b.characterId = PlayerDataProvider.get(player).getCharacterId();
        b.visualTheme = MagicBulletVisuals.theme(b.characterId);
        if(b.visualTheme>0)b.visualId=MagicBulletVisuals.start();
        BULLETS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(b);
        if ("xuanchen".equals(b.characterId)) {
            com.myriad.character.skills.xuanchen.rework.InkCastCues.basicBullet(player, held, pos, dir);
        }
        com.myriad.network.Packets.sendToPlayer(player,
                new com.myriad.network.NetworkSetup.MagicBulletCooldownPayload(remaining(player), total(player)));
        com.myriad.character.ability.StaffHandCues.basic(player, held, b.characterId, pos, dir);
        // 起手：枪口迸发 + 音效
        if ("yunsheng".equalsIgnoreCase(b.characterId)) {
            com.myriad.character.ability.WarSpiritHandCues.basic(player,b.characterId,total(player));
            level.sendParticles(YUNSHENG_ORB, pos.x, pos.y, pos.z, 16, .13, .13, .13, .022);
            level.sendParticles(YUNSHENG_GLOW, pos.x, pos.y, pos.z, 9, .08, .08, .08, .015);
        } else if (b.visualId == 0) {
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 12, 0.15, 0.15, 0.15, 0.04);
            level.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y, pos.z, 8, 0.1, 0.1, 0.1, 0.02);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** 每 tick 推进魔法弹（PlayerDataProvider 调用） */
    public static void tickBullets(ServerPlayer player, PlayerData data) {
        var recovery=FIRE_CD.get(player.getUUID());
        if(recovery!=null){boolean ready=recovery.progress.advance(player.level().getGameTime(),ActionTime.multiplier(player.getUUID(),player.level().getGameTime()),recovery.totalTicks);
            com.myriad.network.Packets.sendToPlayer(player,new com.myriad.network.NetworkSetup.MagicBulletCooldownPayload(remaining(player),total(player)));
            if(ready)FIRE_CD.remove(player.getUUID());}
        List<Bullet> list = BULLETS.get(player.getUUID());
        if (list == null || list.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        list.removeIf(b -> {boolean ended=b.level!=level || tickOne(level,player,b);if(ended){
            ActionTime.clearTarget(b.flight);
            if(b.visualId>0)MagicBulletVisuals.end(player,b.visualTheme,b.visualId);
        }return ended;});
        if (list.isEmpty()) {
            BULLETS.remove(player.getUUID());
        }
    }

    /** 推进单颗魔法弹；返回 true 表示已消耗（命中、撞墙或射程耗尽） */
    private static boolean tickOne(ServerLevel level, ServerPlayer caster, Bullet b) {
        long now=level.getGameTime();if(b.lastTick==now)return false;b.lastTick=now;
        if(++b.age>2*Math.ceil(maxRange(b.characterId)/SPEED))return true;
        double step=Math.min(SPEED*AbilityRuntime.projectileRate(caster,b.flight,b.pos,now),maxRange(b.characterId)-b.traveled);
        Vec3 next = b.pos.add(b.dir.scale(step));
        if (com.myriad.combat.WindWallSystem.interceptProjectile(caster, b.pos, next) != null) {
            return true;
        }
        LivingEntity target = findFirstEnemy(level, caster, b.pos, next);
        if (target != null) {
            float dmg = (float) (b.sp * damageRatio(b.characterId) * b.attackMultiplier);
            DamageCalc.dealBasicDamage(caster, target, dmg, DamageCalc.Kind.MAGIC);
            if (b.visualId>0) {
                Vec3 at=target.getBoundingBox().inflate(HIT_RADIUS).clip(b.pos,next).orElse(next);
                MagicBulletVisuals.sample(caster,b.visualTheme,b.visualId,b.pos,at);
                MagicBulletVisuals.impact(caster,b.visualTheme,at,b.dir);
                level.playSound(null,target.getX(),target.getY(),target.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.6f,1.1f);
                return true;
            }
            // 命中爆散特效
            if ("yunsheng".equalsIgnoreCase(b.characterId)) {
                level.sendParticles(YUNSHENG_ORB, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        22, .3, .3, .3, .04);
                level.sendParticles(YUNSHENG_GLOW, target.getX(), target.getY() + target.getBbHeight() * .6, target.getZ(),
                        12, .2, .2, .2, .03);
            } else {
                level.sendParticles(ParticleTypes.SCULK_SOUL,
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
            if(b.visualId>0)MagicBulletVisuals.sample(caster,b.visualTheme,b.visualId,b.pos,blockHit);
            return true;
        }
        if(b.visualId>0)MagicBulletVisuals.sample(caster,b.visualTheme,b.visualId,b.pos,next);
        b.traveled += step;
        if (b.traveled >= maxRange(b.characterId)) {
            if(b.visualId==0)
                level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z,
                        8, 0.15, 0.15, 0.15, 0.02);

            return true;
        }
        // 飞行拖尾：按角色区分特效
        spawnTrail(level, b, next);
        b.pos = next;
        return false;
    }

    /** Vanilla particle trails for roles without a modeled projectile. */
    private static void spawnTrail(ServerLevel level, Bullet b, Vec3 pos) {
        if ("yunsheng".equalsIgnoreCase(b.characterId)) {
            level.sendParticles(YUNSHENG_ORB, pos.x, pos.y, pos.z, 5, .08, .08, .08, .006);
            level.sendParticles(YUNSHENG_GLOW, pos.x, pos.y, pos.z, 2, .04, .04, .04, .003);
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
                        && SkillTargets.hostile(caster, e))) {
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

    /** 玩家退出时清理其全部飞行中的魔法弹 */
    public static void clearFor(UUID playerId) {
        var bullets=BULLETS.remove(playerId);if(bullets!=null)bullets.forEach(b->ActionTime.clearTarget(b.flight));
        FIRE_CD.remove(playerId);
    }

    private static final class FireCooldown {final ActionTime.Progress progress;final int totalTicks;
        FireCooldown(long now,int total){progress=new ActionTime.Progress(now);totalTicks=total;}}
    public static int remaining(ServerPlayer p){var s=FIRE_CD.get(p.getUUID());if(s==null)return 0;double rate=ActionTime.multiplier(p.getUUID(),p.level().getGameTime());return Math.max(0,(int)Math.ceil((s.totalTicks-s.progress.completed())/rate));}
    public static int total(ServerPlayer p){var s=FIRE_CD.get(p.getUUID());double base=s==null?Math.ceil(20/Math.max(.05,p.getAttributeValue(com.myriad.registry.ModAttributes.ATTACK_SPEED))):s.totalTicks;return Math.max(1,(int)Math.ceil(base/ActionTime.multiplier(p.getUUID(),p.level().getGameTime())));}

    /** 风墙：清除位置满足条件的飞行中魔法弹（阻挡投射物） */
    public static void blockIn(ServerLevel level, java.util.function.Predicate<Vec3> inside) {
        blockIn(level, owner -> true, inside);
    }

    public static void blockIn(ServerLevel level, java.util.function.Predicate<UUID> owners, java.util.function.Predicate<Vec3> inside) {
        blockIn(level,owners,inside,at->{});
    }

    public static void blockIn(ServerLevel level, java.util.function.Predicate<UUID> owners,
            java.util.function.Predicate<Vec3> inside,java.util.function.Consumer<Vec3> intercepted) {
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
            var caster=level.getServer().getPlayerList().getPlayer(entry.getKey());
            list.removeIf(b -> {if(b.level!=level||!inside.test(b.pos))return false;ActionTime.clearTarget(b.flight);
                if(b.visualId>0&&caster!=null&&caster.level()==level)
                    MagicBulletVisuals.end(caster,b.visualTheme,b.visualId);
                intercepted.accept(b.pos);return true;});
            if (list.isEmpty()) {
                it.remove();
            }
        }
    }
}
