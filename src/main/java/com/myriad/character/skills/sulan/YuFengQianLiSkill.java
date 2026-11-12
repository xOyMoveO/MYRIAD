package com.myriad.character.skills.sulan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModParticles;

/**
 * 风部·素岚 R 大招【御风千里】：蓄力 1 秒后化作疾风向前快速冲刺
 * （12 格 + 每级 1 格）；遇到敌人或位移结束时触发旋转击：
 * 对周围 2 格敌人造成 120+60% 攻击力 物理伤害并减速 2 秒（每级 +20 基础伤害）；
 * 结束后自身移速 +30%、无视减速，持续 3 秒。
 */
public class YuFengQianLiSkill extends Skill {
    private static final double BASE_RANGE = 20.0;
    private static final double DASH_SPEED = 1.0;
    private static final double HIT_RADIUS = 1.5;
    private static final double SPIN_RADIUS = 2.0;
    private static final int CHANNEL_TICKS = 20;
    private static final int SPIN_SLOW_TICKS = 40;
    private static final int SPIN_EFFECT_TICKS = 25;
    private static final int BUFF_TICKS = 60;
    private static final double BUFF_MS = 0.30;
    private static final Identifier MS_MOD = Identifier.parse("myriad:yufengqianli_ms");

    /** 施法状态：0=蓄力，1=冲刺 */
    private static final class Cast {
        long start;
        int phase;
        Vec3 dir;
        double moved;
        Vec3 lastPos;
        int remainingTicks;
        int level;
        float ad;
    }

    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private static final Map<UUID, Long> BUFF_UNTIL = new HashMap<>();
    private static final Map<UUID, SpinEffect> SPIN_EFFECTS = new HashMap<>();
    private static final DustParticleOptions BLADE_WHITE = new DustParticleOptions(0xF8FFFF, 1.15F);
    private static final DustParticleOptions BLADE_EDGE = new DustParticleOptions(0xBCEFFF, 0.72F);

    private static final class SpinEffect {
        final Vec3 center;
        final long start;

        SpinEffect(Vec3 center, long start) {
            this.center = center;
            this.start = start;
        }
    }

    public YuFengQianLiSkill() {
        super("yufengqianli", 1000, 180.0F);
    }

    @Override
    public int getCooldownTicks(int level) {
        return Math.max(20, 1000 - Math.max(0, level - 1) * 100);
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public String levelEffects(int level) {
        int dmg = 31 + 16 * (level - 1);
        return "Lv." + level + "：蓄力1秒后快速冲刺 " + (BASE_RANGE + level - 1)
                + " 格（撞人立即停下），遇敌或冲刺结束触发旋转击（半径2格，伤害 " + dmg
                  + "+" + (60 + 20 * (level - 1)) + "%攻击力 并减速2秒）；结束后移速+30%持续3秒";
    }

    @Override
    public void cast(ServerPlayer player, PlayerData data) {
        int lv = data.skillLevel("yufengqianli");
        Cast cast = new Cast();
        cast.start = player.level().getGameTime();
        cast.phase = 0;
        cast.dir = currentDirection(player);
        cast.level = lv;
        cast.ad = (float) player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        CASTS.put(player.getUUID(), cast);
        data.setBuff("yufengqianli", CHANNEL_TICKS);
        ServerLevel level = (ServerLevel) player.level();
        // 蓄力起手音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    /** 每 tick：推进蓄力/冲刺/旋转击 + 移速buff（PlayerDataProvider 调用） */
    public static void tick(ServerPlayer player, PlayerData data) {
        advanceCast(player, data);
        advanceSpinEffect(player);
        advanceBuff(player, data);
    }

    /** 终点旋转刀光：两道白色月牙沿水平环快速旋转，淡青粒子只作为外缘。 */
    private static void advanceSpinEffect(ServerPlayer player) {
        SpinEffect effect = SPIN_EFFECTS.get(player.getUUID());
        if (effect == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long elapsed = level.getGameTime() - effect.start;
        if (elapsed >= SPIN_EFFECT_TICKS) {
            SPIN_EFFECTS.remove(player.getUUID());
            return;
        }

        double rotation = elapsed * 0.78;
        double fade = 1.0 - elapsed / (double) SPIN_EFFECT_TICKS;
        for (int blade = 0; blade < 2; blade++) {
            double bladeAngle = rotation + blade * Math.PI;
            for (int segment = 0; segment <= 12; segment++) {
                double arc = (segment / 12.0 - 0.5) * 1.75;
                double angle = bladeAngle + arc;
                // 中段更长、两端收束，形成月牙状刀光而非普通粒子圆环。
                double radius = 1.05 + Math.cos(arc) * 0.95;
                double y = effect.center.y + 0.72 + blade * 0.42
                        + Math.sin(arc * 1.6) * 0.18;
                double x = effect.center.x + Math.cos(angle) * radius;
                double z = effect.center.z + Math.sin(angle) * radius;
                level.sendParticles(BLADE_WHITE, x, y, z,
                        1, 0.015 * fade, 0.01, 0.015 * fade, 0.0);
                if ((segment & 1) == 0) {
                    level.sendParticles(BLADE_EDGE,
                            effect.center.x + Math.cos(angle) * (radius + 0.12), y + 0.035,
                            effect.center.z + Math.sin(angle) * (radius + 0.12),
                            1, 0.01, 0.005, 0.01, 0.0);
                }
            }
        }
    }

    private static void advanceCast(ServerPlayer player, PlayerData data) {
        Cast cast = CASTS.get(player.getUUID());
        if (cast == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long elapsed = level.getGameTime() - cast.start;
        if (cast.phase == 0) {
            // 蓄力期间持续读取朝向，冲刺不再锁定按下技能时的方向。
            cast.dir = currentDirection(player);
            // 蓄力 1 秒：可缓慢移动，狂风向自身汇聚
            if (elapsed >= CHANNEL_TICKS) {
                cast.phase = 1;
                startDash(player, cast, level);
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 1, false, false, false));
            Vec3 pos = player.position();
            double progress = elapsed / (double) CHANNEL_TICKS;
            if (player.tickCount % 2 == 0) {
                // 环绕周身旋转的风（越蓄越密集）
                double ang = level.getGameTime() * 0.15;
                int n = 8 + (int) (progress * 8);
                for (int i = 0; i < n; i++) {
                    double a = ang + i * Math.PI * 2.0 / n;
                    double r = 1.4 + Math.sin(level.getGameTime() * 0.3 + i) * 0.4;
                    level.sendParticles(ParticleTypes.CLOUD,
                            pos.x + Math.cos(a) * r, pos.y + 1.0 + Math.sin(level.getGameTime() * 0.4 + i * 2.0) * 0.5,
                            pos.z + Math.sin(a) * r,
                            1, 0, 0, 0, 0);
                    if (i % 3 == 0) {
                        level.sendParticles(ParticleTypes.END_ROD,
                                pos.x + Math.cos(a) * r * 0.8, pos.y + 1.0, pos.z + Math.sin(a) * r * 0.8,
                                1, 0, 0, 0, 0);
                    }
                }
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 1.2, pos.z,
                        4, 0.3, 0.5, 0.3, 0.03);
            }
            // 蓄力音效：风声渐强
            if (player.tickCount % 5 == 0) {
                level.playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS,
                        0.5f, 0.8f + (float) progress * 0.6f);
            }
            return;
        }
        // 冲刺阶段
        if (player.hasEffect(MobEffects.SLOWNESS)) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
        double movedNow = player.position().distanceTo(cast.lastPos);
        if (movedNow < 0.05 && cast.moved > 0) {
            doSpin(player, cast, level, data);
            finish(player, cast, level, data);
            return;
        }
        cast.moved += movedNow;
        cast.lastPos = player.position();
        Vec3 pos = player.position();
        // 遇到敌人：立即触发旋转击
        LivingEntity hit = findEnemyAt(level, player, pos);
        if (hit != null) {
            doSpin(player, cast, level, data);
            finish(player, cast, level, data);
            return;
        }
        // 风影拖尾
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.8, pos.z,
                3, 0.12, 0.25, 0.12, 0.02);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.3, pos.z,
                1, 0.06, 0.06, 0.06, 0.01);
        if (cast.remainingTicks > 0) {
            cast.dir = currentDirection(player);
            player.setDeltaMovement(cast.dir.x * DASH_SPEED, 0, cast.dir.z * DASH_SPEED);
            PacketDistributor.sendToPlayer(player,
                    new DashPayload((float) cast.dir.x, 0f, (float) cast.dir.z, 2));
            player.setNoGravity(true);
            player.noPhysics = true;
            cast.remainingTicks--;
        } else {
            // 位移结束：触发旋转击
            doSpin(player, cast, level, data);
            finish(player, cast, level, data);
        }
    }

    /** 蓄力完成：开始快速冲刺 */
    private static void startDash(ServerPlayer player, Cast cast, ServerLevel level) {
        double range = BASE_RANGE + (cast.level - 1);
        cast.lastPos = player.position();
        cast.remainingTicks = (int) Math.round(range / DASH_SPEED);
        PacketDistributor.sendToPlayer(player,
                new DashPayload((float) cast.dir.x, 0f, (float) cast.dir.z, cast.remainingTicks),
                new CustomPacketPayload[0]);
        player.setDeltaMovement(cast.dir.x * DASH_SPEED, 0, cast.dir.z * DASH_SPEED);
        player.setNoGravity(true);
        player.noPhysics = true;
        if (player.hasEffect(MobEffects.SLOWNESS)) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
        cast.remainingTicks--;
        // 冲刺起手：风啸爆发
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0f, 1.3f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.7f, 1.0f);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                30, 0.5, 0.4, 0.5, 0.04);
    }

    /** 旋转击：对周围 2 格敌人造成伤害并减速 2 秒 */
    private static void doSpin(ServerPlayer player, Cast cast, ServerLevel level, PlayerData data) {
        Vec3 pos = player.position();
        SPIN_EFFECTS.put(player.getUUID(), new SpinEffect(pos, level.getGameTime()));
        // count=0 时三个偏移量会原样传到客户端 Provider：初始滚转、旋转速度、渲染尺寸。
        level.sendParticles(ModParticles.SULAN_BLADE_ARC.get(),
                pos.x, pos.y + 1.05, pos.z,
                0, 0.0, 0.46, 1.75, 1.0);
        level.sendParticles(ModParticles.SULAN_BLADE_ARC.get(),
                pos.x, pos.y + 1.18, pos.z,
                0, Math.PI, -0.52, 1.48, 1.0);
        float dmg = 124.0F + 64.0F * (cast.level - 1)
                + (2.4F + 0.8F * (cast.level - 1)) * cast.ad;
        AABB box = new AABB(pos.x - SPIN_RADIUS, pos.y - 1.0, pos.z - SPIN_RADIUS,
                pos.x + SPIN_RADIUS, pos.y + 4.0, pos.z + SPIN_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !isAlly(player, e))) {
            DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 3);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPIN_SLOW_TICKS, 2, false, false, false));
            level.sendParticles(ParticleTypes.CLOUD,
                    e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                    16, 0.4, 0.4, 0.4, 0.05);
            level.sendParticles(ParticleTypes.END_ROD,
                    e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                    8, 0.3, 0.3, 0.3, 0.03);
        }
        // 旋转击视觉：周身风环
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2.0 * i / 24.0;
            level.sendParticles(ParticleTypes.CLOUD,
                    pos.x + Math.cos(a) * SPIN_RADIUS, pos.y + 0.6 + Math.sin(a * 2) * 0.6, pos.z + Math.sin(a) * SPIN_RADIUS,
                    1, 0.05, 0.05, 0.05, 0.01);
            level.sendParticles(ParticleTypes.END_ROD,
                    pos.x + Math.cos(a) * SPIN_RADIUS * 0.7, pos.y + 1.0, pos.z + Math.sin(a) * SPIN_RADIUS * 0.7,
                    1, 0.03, 0.03, 0.03, 0.01);
        }
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z,
            20, SPIN_RADIUS, 0.5, SPIN_RADIUS, 0.05);
        // 旋转击音效
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.9f, 1.0f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    private static void finish(ServerPlayer player, Cast cast, ServerLevel level, PlayerData data) {
        CASTS.remove(player.getUUID());
        player.noPhysics = false;
        player.setNoGravity(false);
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
        // 通知客户端停止冲刺（撞人/位移结束立即停下，不再滑行）
        PacketDistributor.sendToPlayer(player,
                new DashPayload(0f, 0f, 0f, 0), new CustomPacketPayload[0]);
        // 结束后移速 +30%、无视减速 3 秒
        AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null) {
            ms.removeModifier(MS_MOD);
            ms.addTransientModifier(new AttributeModifier(MS_MOD, BUFF_MS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        BUFF_UNTIL.put(player.getUUID(), player.level().getGameTime() + BUFF_TICKS);
        data.setBuff("yufengqianli_buff", BUFF_TICKS);
    }

    private static void advanceBuff(ServerPlayer player, PlayerData data) {
        Long until = BUFF_UNTIL.get(player.getUUID());
        if (until == null) {
            return;
        }
        // buff 期间无视减速
        if (player.hasEffect(MobEffects.SLOWNESS)) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
        if (player.level().getGameTime() >= until) {
            BUFF_UNTIL.remove(player.getUUID());
            AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (ms != null) {
                ms.removeModifier(MS_MOD);
            }
            data.setBuff("yufengqianli_buff", 0);
        }
    }

    /** 冲刺路径上遇到的第一个敌人 */
    private static LivingEntity findEnemyAt(ServerLevel level, ServerPlayer caster, Vec3 pos) {
        AABB box = new AABB(pos.x - HIT_RADIUS, pos.y - 1.0, pos.z - HIT_RADIUS,
                pos.x + HIT_RADIUS, pos.y + 3.5, pos.z + HIT_RADIUS);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && com.myriad.combat.SkillTargets.alive(e) && !isAlly(caster, e))) {
            return e;
        }
        return null;
    }

    public static void clearFor(UUID playerId) {
        CASTS.remove(playerId);
        BUFF_UNTIL.remove(playerId);
        SPIN_EFFECTS.remove(playerId);
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

    private static Vec3 currentDirection(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }
}
