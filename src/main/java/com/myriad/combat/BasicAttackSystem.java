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
        if(role==CharacterType.YIKE){com.myriad.character.skills.yike.YikeBasicAttack.tryFire(attacker);return;}
        if (VanillaMeleeAttack.usesVanilla(role)) return;
        // Retained ranged heroes fire through MagicBullet; custom target-hit packets cannot deal damage.
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

    /** 立即刷新普攻：供技能使用，并立刻同步攻速条。 */
    public static void refreshCooldown(ServerPlayer player) {
        ATTACK_COOLDOWNS.remove(player.getUUID());
        PlayerDataProvider.syncToClient(player);
    }

    public static int cooldownRemaining(ServerPlayer player) {
        if(VanillaMeleeAttack.usesVanilla(CharacterType.byId(PlayerDataProvider.get(player).getCharacterId())))
            return Math.max(0,(int)Math.ceil((1-player.getAttackStrengthScale(0))*player.getCurrentItemAttackStrengthDelay()));
        if("yike".equals(PlayerDataProvider.get(player).getCharacterId()))return com.myriad.character.skills.yike.YikeBasicAttack.remaining(player);
        if(MagicBullet.isMage(player))return MagicBullet.remaining(player);
        AttackCooldown cooldown = ATTACK_COOLDOWNS.get(player.getUUID());
        return cooldown == null ? 0 : Math.max(0, (int) (cooldown.readyAt - player.level().getGameTime()));
    }

    public static int cooldownTotal(ServerPlayer player) {
        if(VanillaMeleeAttack.usesVanilla(CharacterType.byId(PlayerDataProvider.get(player).getCharacterId())))
            return Math.max(1,(int)Math.ceil(player.getCurrentItemAttackStrengthDelay()));
        if("yike".equals(PlayerDataProvider.get(player).getCharacterId()))return com.myriad.character.skills.yike.YikeBasicAttack.total(player);
        if(MagicBullet.isMage(player))return MagicBullet.total(player);
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
