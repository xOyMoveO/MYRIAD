package com.myriad.character.skills.sulan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * 风部·素岚 Q 技能【疾风突刺】：快速突进 8 格，对穿过的所有敌人造成
 * 12 + 50% 攻击力 物理伤害；过程中无视碰撞体积与减速。
 */
public class JiFengTuCiSkill extends Skill {
    private static final double DASH_RANGE = 8.0;
    private static final double DASH_SPEED = 1.0;
    private static final double HIT_RADIUS = 1.5;

    private static final class Dash {
        Vec3 dir;
        double moved;
        Vec3 lastPos;
        int remainingTicks;
        final Set<UUID> hit = new HashSet<>();
    }

    private static final Map<UUID, Dash> DASHES = new HashMap<>();

    public JiFengTuCiSkill() {
        super("jifengtuci", 140, 72.0F);
    }

    @Override
    public String levelEffects(int level) {
        return "Lv." + level + "：突进8格，伤害 " + (12 + 8 * (level - 1)) + "+"
                + (50 + 8 * (level - 1)) + "%攻击力；无视碰撞与减速";
    }

    @Override
    public void cast(ServerPlayer player, PlayerData data) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        flat = flat.normalize();
        Dash dash = new Dash();
        dash.dir = flat;
        dash.lastPos = player.position();
        dash.remainingTicks = (int) Math.round(DASH_RANGE / DASH_SPEED);
        DASHES.put(player.getUUID(), dash);
        PacketDistributor.sendToPlayer(player,
                new DashPayload((float) flat.x, 0f, (float) flat.z, dash.remainingTicks),
                new CustomPacketPayload[0]);
        player.setDeltaMovement(flat.x * DASH_SPEED, 0, flat.z * DASH_SPEED);
        player.setNoGravity(true);
        player.noPhysics = true;
        if (player.hasEffect(MobEffects.SLOWNESS)) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
        dash.remainingTicks--;
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.5f, 1.4f);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1.0, player.getZ(),
                14, 0.4, 0.3, 0.4, 0.03);
    }

    /** 每 tick 推进突刺（PlayerDataProvider 调用） */
    public static void tick(ServerPlayer player, PlayerData data) {
        Dash dash = DASHES.get(player.getUUID());
        if (dash == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (player.hasEffect(MobEffects.SLOWNESS)) {
            player.removeEffect(MobEffects.SLOWNESS);
        }
        double movedNow = player.position().distanceTo(dash.lastPos);
        Vec3 previous = dash.lastPos;
        if (movedNow < 0.05 && dash.moved > 0) {
            finish(player, dash, level);
            return;
        }
        dash.moved += movedNow;
        dash.lastPos = player.position();
        Vec3 pos = player.position();
        // 沿途伤害
        AABB box = new AABB(pos.x - HIT_RADIUS, pos.y - 1.0, pos.z - HIT_RADIUS,
                pos.x + HIT_RADIUS, pos.y + 3.0, pos.z + HIT_RADIUS);
        float ad = (float) player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        int lv = Math.max(1, data.skillLevel("jifengtuci"));
        float dmg = 48.0F + 32.0F * (lv - 1) + (2.0F + 0.32F * (lv - 1)) * ad;
        for (LivingEntity e : com.myriad.combat.DashHitbox.targets(
                player, previous, pos, HIT_RADIUS, dash.hit)) {
            if (dash.hit.add(e.getUUID())) {
                DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 0);
                level.sendParticles(ParticleTypes.CLOUD,
                        e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                        14, 0.3, 0.3, 0.3, 0.04);
                level.sendParticles(ParticleTypes.END_ROD,
                        e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                        6, 0.2, 0.2, 0.2, 0.02);
            }
        }
        // 风影拖尾
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.8, pos.z,
                2, 0.1, 0.2, 0.1, 0.01);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.2, pos.z,
                1, 0.05, 0.05, 0.05, 0.01);
        if (dash.remainingTicks > 0) {
            player.setDeltaMovement(dash.dir.x * DASH_SPEED, 0, dash.dir.z * DASH_SPEED);
            player.setNoGravity(true);
            player.noPhysics = true;
            dash.remainingTicks--;
        } else {
            finish(player, dash, level);
        }
    }

    private static void finish(ServerPlayer player, Dash dash, ServerLevel level) {
        DASHES.remove(player.getUUID());
        player.noPhysics = false;
        player.setNoGravity(false);
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
        level.sendParticles(ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.6, player.getZ(),
                10, 0.3, 0.2, 0.3, 0.02);
    }

    public static void clearFor(UUID playerId) {
        DASHES.remove(playerId);
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
