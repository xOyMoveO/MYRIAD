package com.myriad.combat;

import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.ModState;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.character.CharacterType;
import com.myriad.combat.WindWallSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/**
 * 专属远程普攻入口与恢复计时。近战由原版 Player.attack 处理，不接受旧扇形攻击包。
 */
public final class BasicAttackSystem {
    private static final Map<UUID, AttackCooldown> ATTACK_COOLDOWNS = new HashMap<>();
    public static final double MELEE_REACH = 3.0;
    public static final double MELEE_HALF_ANGLE = Math.toRadians(40);

    private BasicAttackSystem() { }

    public static void tryAttack(ServerPlayer attacker, int targetId) {
        if (com.myriad.character.ability.AbilityRuntime.channeling(attacker)
                || PlayerDataProvider.get(attacker).getBuff("g1_stun") > 0) return;
        if (!ModState.isEnabled()) return;
        PlayerData data = PlayerDataProvider.get(attacker);
        if (data == null || data.getCharacterId() == null || data.getCharacterId().isBlank()) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;
        CharacterType role = CharacterType.byId(data.getCharacterId());
        if (role == null || !canAttackNow(attacker, data)) return;
        if (VanillaMeleeAttack.usesVanilla(role)) return;
        // 离歌使用原版弓式右键蓄力/松开射击，不接受左键模组普攻包。
        if (role == CharacterType.LIGE) return;
        if (role == CharacterType.SHENLI) {
            if (com.myriad.character.skills.shenli.ShenLiSkills.fireBasic(attacker)) {
                data.setLastCombatGameTime(level.getGameTime());
                com.myriad.game.RecallSystem.cancel(attacker);
                PlayerDataProvider.syncToClient(attacker);
            }
            return;
        }
        if (role == CharacterType.MINGZHU) {
            com.myriad.character.skills.mingzhu.MingZhuSkills.fireBasic(attacker);
            return;
        }
        if (MagicBullet.isMage(attacker)) return;
        boolean wanyue = role == CharacterType.WANYUE;

        // 接轮强化普攻是朝准星方向发射的穿透弹体，不要求客户端先选中实体。
        if (wanyue && com.myriad.character.skills.wanyue.WanyueSkills.hasEmpoweredBasic(attacker)) {
            long now = level.getGameTime();
            AttackCooldown current = ATTACK_COOLDOWNS.get(attacker.getUUID());
            double attackSpeed = Math.max(0.1, attacker.getAttributeValue(ModAttributes.ATTACK_SPEED));
            int cooldownTicks = Math.max(1, (int)Math.ceil(20.0 / attackSpeed));
            AttackWindow window = attackWindow(now, current);
            if (!window.allowed()) return;
            if (com.myriad.character.skills.wanyue.WanyueSkills.fireEmpoweredBasic(attacker,
                    window.damageMultiplier())) {
                ATTACK_COOLDOWNS.put(attacker.getUUID(), new AttackCooldown(now + cooldownTicks, cooldownTicks));
                PlayerDataProvider.syncToClient(attacker);
            }
            return;
        }

        Entity raw = level.getEntity(targetId);
        if (!(raw instanceof LivingEntity target) || target == attacker || !target.isAlive() || isAlly(attacker, target)) {
            return;
        }
        if (!com.myriad.game.VisionSystem.canSee(attacker, target)) return;
        if (wanyue && !com.myriad.character.skills.wanyue.WanyueSkills.canBasicAttack(attacker)) return;
        boolean ranged = wanyue;
        Vec3 origin = wanyue
                ? com.myriad.character.skills.wanyue.WanyueSkills.basicOrigin(attacker)
                : attacker.getEyePosition();
        double range = ranged ? 10.0 : 0.0;
        Vec3 closest = new Vec3(
                net.minecraft.util.Mth.clamp(origin.x, target.getBoundingBox().minX, target.getBoundingBox().maxX),
                net.minecraft.util.Mth.clamp(origin.y, target.getBoundingBox().minY, target.getBoundingBox().maxY),
                net.minecraft.util.Mth.clamp(origin.z, target.getBoundingBox().minZ, target.getBoundingBox().maxZ));
        boolean inRange = ranged ? origin.distanceToSqr(closest) <= range * range
                : attacker.isWithinEntityInteractionRange(target, 0.0);
        boolean clearLine = level.clip(new ClipContext(origin, target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker)).getType() == HitResult.Type.MISS;
        if (!inRange || (ranged ? !clearLine : !attacker.hasLineOfSight(target))) {
            return;
        }
        Vec3 toward = target.getBoundingBox().getCenter().subtract(attacker.getEyePosition());
        if (toward.lengthSqr() < 1.0E-6 || attacker.getLookAngle().normalize().dot(toward.normalize()) < 0.35) {
            return;
        }
        long now = level.getGameTime();
        AttackCooldown current = ATTACK_COOLDOWNS.get(attacker.getUUID());
        double attackSpeed = Math.max(0.1, attacker.getAttributeValue(ModAttributes.ATTACK_SPEED));
        int cooldownTicks = Math.max(1, (int) Math.ceil(20.0 / attackSpeed));
        AttackWindow window = attackWindow(now, current);
        if (!window.allowed()) return;
        ATTACK_COOLDOWNS.put(attacker.getUUID(), new AttackCooldown(now + cooldownTicks, cooldownTicks));
        if (ranged && WindWallSystem.blocksProjectile(level, origin,
                target.getBoundingBox().getCenter())) {
            PlayerDataProvider.syncToClient(attacker);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, .55F, 1.35F);
            return;
        }

        // 保留 PLAYER_ATTACK 伤害源，以复用既有物攻、暴击、吸血与角色被动结算链。
        DamageSource source = target.damageSources().playerAttack(attacker);
        DamageCalc.markBasicAttack(attacker, target, window.damageMultiplier());
        target.hurt(source, 1.0F);
        DamageCalc.clearBasicAttack(attacker, target);

        // 立即下发冷却起点；后续逐 tick 动画由客户端按本地游戏刻推演，避免每 tick 发送整包状态。
        PlayerDataProvider.syncToClient(attacker);
        data.setLastCombatGameTime(now);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.75F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.55,
                target.getZ(), 7, 0.22, 0.22, 0.22, 0.03);
        if (ranged) {
            Vec3 from = origin;
            Vec3 to = target.getBoundingBox().getCenter();
            int points = Math.max(4, (int)Math.ceil(from.distanceTo(to) * 3));
            net.minecraft.core.particles.DustParticleOptions arrow = new net.minecraft.core.particles.DustParticleOptions(
                    wanyue ? 0xB8E9FF : 0x62E6E8, .9F);
            for (int i = 0; i <= points; i++) {
                Vec3 p = from.lerp(to, i / (double)points);
                level.sendParticles(arrow, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, .55F, 1.35F);
        }
    }

    public static void clearFor(UUID playerId) {
        ATTACK_COOLDOWNS.remove(playerId);
        VanillaMeleeAttack.clear(playerId);
    }

    public static boolean canAttackNow(ServerPlayer player, PlayerData data) {
        GameManager manager = GameManager.get();
        return player.isAlive() && !player.isSpectator() 
                && !com.myriad.character.skills.shuo.ShuoCombat.blocked(player)
                && data.getPlacementType() == 0
                && (com.myriad.game.TrainingMode.isEnabled()
                    || manager != null && manager.getPhase() == GameManager.Phase.PLAYING);
    }

    /** Historical geometry utility for archived tests only; no live melee attack calls it. */
    public static boolean inMeleeArc(Vec3 origin, Vec3 facing, net.minecraft.world.phys.AABB box,
            double reach, double halfAngle) {
        if (box.maxY < origin.y - .5 || box.minY > origin.y + 2.4) return false;
        double dx = net.minecraft.util.Mth.clamp(origin.x, box.minX, box.maxX) - origin.x;
        double dz = net.minecraft.util.Mth.clamp(origin.z, box.minZ, box.maxZ) - origin.z;
        if (dx * dx + dz * dz > reach * reach) return false;
        Vec3 delta = box.getCenter().subtract(origin).multiply(1, 0, 1);
        double distance = delta.length();
        if (distance < .01) return true;
        double radius = Math.min(.8, Math.max(box.getXsize(), box.getZsize()) * .5);
        double tolerance = Math.asin(Math.min(1, radius / distance));
        return facing.dot(delta.scale(1 / distance)) >= Math.cos(Math.min(Math.PI / 2, halfAngle + tolerance));
    }

    /** 立即刷新普攻：供裂羽箭等技能使用，并立刻同步攻速条。 */
    public static void refreshCooldown(ServerPlayer player) {
        ATTACK_COOLDOWNS.remove(player.getUUID());
        PlayerDataProvider.syncToClient(player);
    }

    public static int cooldownRemaining(ServerPlayer player) {
        if(VanillaMeleeAttack.usesVanilla(CharacterType.byId(PlayerDataProvider.get(player).getCharacterId())))
            return Math.max(0,(int)Math.ceil((1-player.getAttackStrengthScale(0))*player.getCurrentItemAttackStrengthDelay()));
        AttackCooldown cooldown = ATTACK_COOLDOWNS.get(player.getUUID());
        return cooldown == null ? 0 : Math.max(0, (int) (cooldown.readyAt - player.level().getGameTime()));
    }

    public static int cooldownTotal(ServerPlayer player) {
        if(VanillaMeleeAttack.usesVanilla(CharacterType.byId(PlayerDataProvider.get(player).getCharacterId())))
            return Math.max(1,(int)Math.ceil(player.getCurrentItemAttackStrengthDelay()));
        AttackCooldown cooldown = ATTACK_COOLDOWNS.get(player.getUUID());
        return cooldown == null ? Math.max(1, (int) Math.ceil(20.0 /
                Math.max(0.1, player.getAttributeValue(ModAttributes.ATTACK_SPEED)))) : cooldown.totalTicks;
    }

    /** No early/weak attacks: only a fully recovered attack can be committed. */
    private static AttackWindow attackWindow(long now, AttackCooldown current) {
        if (current == null || now >= current.readyAt) return new AttackWindow(true, 1.0F);
        return new AttackWindow(false, 0.0F);
    }

    /** Historical readiness API; early release is retired, so no remaining tick is allowed. */
    public static int earlyReadyRemaining(int totalTicks) {
        return 0;
    }

    /** 供各自维护冷却的法师普攻和角色专属普攻复用同一判定。 */
    public static boolean isEarlyReady(long now, long readyAt, int totalTicks) {
        return now >= readyAt;
    }

    public static float recoveryDamageMultiplier(long now, long readyAt) {
        return 1.0F;
    }

    private static boolean isAlly(ServerPlayer attacker, LivingEntity target) {
        return SkillTargets.ally(attacker, target);
    }

    private record AttackCooldown(long readyAt, int totalTicks) { }
    private record AttackWindow(boolean allowed, float damageMultiplier) { }
}
