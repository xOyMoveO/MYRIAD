package com.myriad.character.skills.hanze;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.math.Transformation;
import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.PlacementUtil;
import com.myriad.network.NetworkSetup;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.ModShieldSystem;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;
import com.myriad.registry.ModSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 霜部·寒泽：冰锥、护盾、霜寒领域与持续冰封。 */
public final class HanzeSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    public static final int ABSOLUTE_ZERO_PLACEMENT = 4;
    private final Type type;
    private static final Map<UUID, FrostZone> ZONES = new HashMap<>();
    private static final Map<UUID, IceVolley> ICE_VOLLEYS = new HashMap<>();
    private static final Map<UUID, List<IceSpike>> ICE_SPIKES = new HashMap<>();
    private static final Map<UUID, PendingBlast> PENDING_BLASTS = new HashMap<>();
    private static final Map<UUID, Long> PASSIVE_SOUND_UNTIL = new HashMap<>();
    /** 施法者 UUID ->（目标 UUID -> 寒霜层数与到期时间）。 */
    private static final Map<UUID, Map<UUID, FrostMark>> FROST_MARKS = new HashMap<>();
    /** 施法者 UUID ->（目标 UUID -> 不能再次叠加寒霜的到期时间）。 */
    private static final Map<UUID, Map<UUID, Long>> FROST_MARK_COOLDOWNS = new HashMap<>();
    /** 额外 10% 最大生命真实伤害的重入保护，避免其自身再次叠加被动。 */
    private static final Set<UUID> FROST_EXECUTION_DAMAGE = new HashSet<>();
    private static final int ICE_SPIKE_INTERVAL = 15;
    private static final double ICE_SPIKE_SPEED = .9;
    private static final double ICE_SPIKE_RANGE = 18.0;
    private static final int BLAST_WINDUP = 20;
    private static final int FROST_MARK_DURATION = 200;
    private static final int FROST_FREEZE_DURATION = 60;
    private static final int FROST_MARK_TRIGGER_COOLDOWN = 200;

    public HanzeSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "hanze_passive";
            case Q -> "bingzhuishu";
            case W -> "shuangdun";
            case E -> "jueduilingdu";
            case R -> "qianlibingfeng";
        }, switch (type) {
            case PASSIVE -> 0;
            case Q -> 100;
            case W -> 280;
            case E -> 700;
            case R -> 1200;
        }, switch (type) {
            case PASSIVE -> 0.0F;
            case Q -> 72.0F;
            case W -> 108.0F;
            case E -> 252.0F;
            case R -> 216.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public String levelEffects(int level) {
        return switch (type) {
            case PASSIVE -> "伤害叠加寒霜（10秒）；4层冰冻3秒，触发后10秒内不可再次叠层。对减速目标增伤15%；攻击冰冻目标额外造成其10%最大生命真实伤害并解除冰冻";
            case Q -> "Lv." + level + "：每15刻按当前朝向射出1枚冰锥，共3枚；单枚"
                    + (13 + 5 * (level - 1)) + "+" + (30 + 10 * (level - 1))
                    + "%法强，减速20%（同目标后续减半）";
            case W -> "Lv." + level + "：霜盾" + (30 + 10 * (level - 1)) + "+"
                    + (15 + 5 * (level - 1)) + "%法强，持续4秒";
            case E -> "Lv." + level + "：准星选点后蓄力1秒（不可移动），冰爆10格造成25+50%法强并冰冻1.2秒";
            case R -> "Lv." + level + "：8格冰封，持续" + (3 + (level - 1)) + "秒，每秒25+20%法强";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        int lv = Math.max(1, data.skillLevel(getId()));
        double sp = player.getAttributeValue(ModAttributes.SPELL_POWER);
        if (type == Type.Q) {
            // 三枚冰锥依次于 0 / 15 / 30 tick 射出；每一枚均读取当前面朝方向。
            ICE_VOLLEYS.put(player.getUUID(), new IceVolley(level.getGameTime(),
                    (float) (52 + 20 * (lv - 1) + sp * (1.2 + .4 * (lv - 1)))));
        } else if (type == Type.W) {
            ServerPlayer ally = nearestAlly(level, player, 12);
            if (ally == null) ally = player;
            double shield = 30 + 10 * (lv - 1) + sp * (.15 + .05 * (lv - 1));
            float beforeShield = ModShieldSystem.value(ally);
            // 霜盾占用独立来源层：重复施放应稳定刷新为本次计算值，不能因其他护盾存在而被 ensureAtLeast 错误移除。
            ModShieldSystem.grant(ally, "hanze_shuangdun", (float)shield, 80);
            
            PlayerData allyData = PlayerDataProvider.get(ally);
            allyData.setBuff("shuangdun", 80);
            PlayerDataProvider.syncToClient(ally);
            level.sendParticles(ParticleTypes.SNOWFLAKE, ally.getX(), ally.getY() + 1, ally.getZ(), 34, .55, .8, .55, .03);
            level.playSound(null, ally.getX(), ally.getY(), ally.getZ(), ModSounds.HANZE_FROST_SHIELD_CAST.get(),
                    SoundSource.PLAYERS, .75F, 1.45F);
            playIceCrack(level, ally.position(), .28F, 1.72F);
        } else if (type == Type.E) {
            data.setPlacementType(ABSOLUTE_ZERO_PLACEMENT);
            PlayerDataProvider.syncToClient(player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POWDER_SNOW_PLACE,
                    SoundSource.PLAYERS, .8F, .8F);
            playIceCrack(level, player.position(), .20F, 1.82F);
        } else if (type == Type.R) {
            int duration = (3 + (lv - 1)) * 20;
            ZONES.put(player.getUUID(), new FrostZone(player.position(), level.getGameTime() + duration, lv));
            data.setBuff("qianlibingfeng", duration);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 255, false, false, false));
            NetworkSetup.sendFrostView(player, duration);
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + .2, player.getZ(),
                    160, 8, .25, 8, .03);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HANZE_FROZEN_EXPANSE_CAST.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            playIceCrack(level, player.position(), .58F, .82F);
        }
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        if (data.getPlacementType() == ABSOLUTE_ZERO_PLACEMENT && player.tickCount % 4 == 0) {
            Vec3 point = placementPoint(player);
            if (point != null) drawPlacementPreview(level, player, point);
        }
        tickIceVolley(player, level);
        tickIceSpikes(player, level);
        tickPendingBlast(player, data, level);
        tickFrostMarkDisplay(player, level);
        if (data.getBuff("shuangdun") > 0 && player.tickCount % 2 == 0) {
            spawnShieldAura(level, player);
        }
        FrostZone zone = ZONES.get(player.getUUID());
        if (zone == null) return;
        if (level.getGameTime() >= zone.until) {
            ZONES.remove(player.getUUID());
            data.setBuff("qianlibingfeng", 0);
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            double sp = player.getAttributeValue(ModAttributes.SPELL_POWER);
            hitFrost(level, player, zone.center, 8, (float) (100 + sp * .8), 25, 2, false);
            // 领域每秒结算一次，叠加一次轻量冰风与碎冰节拍，附近玩家均可听见。
            level.playSound(null, zone.center.x, zone.center.y, zone.center.z, ModSounds.HANZE_FROZEN_EXPANSE_CAST.get(),
                    SoundSource.PLAYERS, .34F, .78F);
            playIceCrack(level, zone.center, .26F, .72F);
            for (ServerPlayer friend : level.players()) {
                if (ally(player, friend) && friend.position().distanceTo(zone.center) <= 8) {
                    friend.addEffect(new MobEffectInstance(MobEffects.SPEED, 25, 0, false, false));
                }
            }
        }
        if (level.getGameTime() % 4 == 0) {
            spawnFallingIceSpikes(level, zone.center, 8.0, 4);
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, zone.center.x, zone.center.y + .2, zone.center.z, 14, 7.5, .2, 7.5, .01);
    }

    private static void tickIceVolley(ServerPlayer player, ServerLevel level) {
        IceVolley volley = ICE_VOLLEYS.get(player.getUUID());
        if (volley == null || level.getGameTime() < volley.nextFire) return;
        int shot = volley.shotsFired;
        Vec3 origin = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        // 不做扇形偏移：每一枚都严格沿该刻的当前面朝方向发射，并作为独立弹体向前飞行。
        float damage = volley.damage * (shot == 0 ? 1.0F : .5F);
        Vec3 start = origin.add(direction.scale(.55));
        IceSpike spike = new IceSpike(start, direction, damage);
        spike.visual = spawnIceSpikeModel(level, start, direction);
        ICE_SPIKES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(spike);
        spawnIceSpikeTrail(level, start);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HANZE_ICE_SPIKE_CAST.get(),
                SoundSource.PLAYERS, .62F, 1.08F);
        playIceCrack(level, start, .18F, 1.86F);
        volley.shotsFired++;
        volley.nextFire += ICE_SPIKE_INTERVAL;
        if (volley.shotsFired >= 3) ICE_VOLLEYS.remove(player.getUUID());
    }

    /** 逐刻推进冰锥弹体；首个敌人或方块碰撞后立刻碎裂。 */
    private static void tickIceSpikes(ServerPlayer player, ServerLevel level) {
        List<IceSpike> spikes = ICE_SPIKES.get(player.getUUID());
        if (spikes == null || spikes.isEmpty()) return;
        spikes.removeIf(spike -> tickIceSpike(player, level, spike));
        if (spikes.isEmpty()) ICE_SPIKES.remove(player.getUUID());
    }

    private static boolean tickIceSpike(ServerPlayer player, ServerLevel level, IceSpike spike) {
        Vec3 next = spike.position.add(spike.direction.scale(ICE_SPIKE_SPEED));
        LivingEntity target = firstHitEnemy(level, player, spike.position, next);
        if (target != null) {
            DamageCalc.dealSkillDamage(player, target, spike.damage, Kind.MAGIC, 0);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, false, false));
            discardIceSpikeModel(spike);
            shatterIceSpike(level, target.getX(), target.getY() + target.getBbHeight() * .55, target.getZ(), .92F);
            return true;
        }
        if (firstSolidBlock(level, spike.position, next) != null) {
            discardIceSpikeModel(spike);
            shatterIceSpike(level, next.x, next.y, next.z, .62F);
            return true;
        }
        spike.traveled += ICE_SPIKE_SPEED;
        if (spike.traveled >= ICE_SPIKE_RANGE) {
            discardIceSpikeModel(spike);
            shatterIceSpike(level, next.x, next.y, next.z, .35F);
            return true;
        }
        spike.position = next;
        updateIceSpikeModel(level, spike);
        spawnIceSpikeTrail(level, next);
        return false;
    }

    /** 冰锥模型的尖端为本地 +Y，使用四元数将其精确对齐弹体飞行方向。 */
    private static ItemDisplay spawnIceSpikeModel(ServerLevel level, Vec3 pos, Vec3 direction) {
        if (!(EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof ItemDisplay display)) {
            return null;
        }
        display.getSlot(0).set(new ItemStack(ModItems.HANZE_ICE_SPIKE.get()));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setBillboardConstraints(BillboardConstraints.FIXED);
        display.setGlowColorOverride(0xFF8FE8FF);
        display.setPos(pos.x, pos.y, pos.z);
        orientIceSpike(display, direction);
        level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(display));
        return display;
    }

    private static void updateIceSpikeModel(ServerLevel level, IceSpike spike) {
        if (spike.visual == null || !spike.visual.isAlive()) {
            spike.visual = spawnIceSpikeModel(level, spike.position, spike.direction);
            return;
        }
        spike.visual.setPos(spike.position.x, spike.position.y, spike.position.z);
        orientIceSpike(spike.visual, spike.direction);
    }

    private static void orientIceSpike(ItemDisplay display, Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6) return;
        Vec3 normalized = direction.normalize();
        Quaternionf aim = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                (float) normalized.x, (float) normalized.y, (float) normalized.z);
        display.setYRot(0.0F);
        display.setXRot(0.0F);
        display.setTransformation(new Transformation(new Vector3f(), aim,
                new Vector3f(.82F, .82F, .82F), new Quaternionf()));
    }

    private static void discardIceSpikeModel(IceSpike spike) {
        if (spike.visual != null && spike.visual.isAlive()) spike.visual.discard();
        spike.visual = null;
    }

    private static void spawnIceSpikeTrail(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 4, .065, .065, .065, .012);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, .02, .02, .02, .002);
    }

    private static void shatterIceSpike(ServerLevel level, double x, double y, double z, float volume) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 38, .34, .34, .34, .075);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 12, .24, .24, .24, .045);
        playIceCrack(level, new Vec3(x, y, z), volume, 1.20F);
    }

    private static void tickPendingBlast(ServerPlayer player, PlayerData data, ServerLevel level) {
        PendingBlast blast = PENDING_BLASTS.get(player.getUUID());
        if (blast == null) return;
        if (level.getGameTime() < blast.detonateAt) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, blast.center.x, blast.center.y + .6, blast.center.z,
                    10, 3.5, .5, 3.5, -.025);
            return;
        }
        PENDING_BLASTS.remove(player.getUUID());
        com.myriad.character.ability.SkillChargeSystem.clear(player);
        data.setBuff("jueduilingdu", 0);
        hitFrost(level, player, blast.center, 10, blast.damage, 24, 255, true);
        level.sendParticles(ParticleTypes.SNOWFLAKE, blast.center.x, blast.center.y + 1, blast.center.z,
                220, 5.5, 1.2, 5.5, .11);
        level.sendParticles(ParticleTypes.CLOUD, blast.center.x, blast.center.y + .5, blast.center.z,
                80, 4.5, .4, 4.5, .06);
        level.playSound(null, blast.center.x, blast.center.y, blast.center.z, ModSounds.HANZE_ABSOLUTE_ZERO_BURST.get(),
                SoundSource.PLAYERS, 1.12F, .92F);
        playIceCrack(level, blast.center, .95F, .84F);
    }

    /** 左键确认绝对零度的准星落点，随后进入 1 秒不可移动的聚霜蓄力。 */
    public static void confirm(ServerPlayer player, PlayerData data) {
        if (data.getPlacementType() != ABSOLUTE_ZERO_PLACEMENT) return;
        Vec3 center = placementPoint(player);
        if (center == null) return;
        float damage = (float) (100 + player.getAttributeValue(ModAttributes.SPELL_POWER) * 2.0);
        PENDING_BLASTS.put(player.getUUID(), new PendingBlast(center,
                player.level().getGameTime() + BLAST_WINDUP, damage));
        data.setPlacementType(0);
        data.setBuff("jueduilingdu", BLAST_WINDUP);
        com.myriad.character.ability.SkillChargeSystem.start(player,"jueduilingdu",BLAST_WINDUP);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BLAST_WINDUP, 255, false, false, false));
        PlayerDataProvider.syncToClient(player);
        player.level().sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + .5, center.z,
                80, 4.5, .8, 4.5, -.04);
        player.level().playSound(null, center.x, center.y, center.z, ModSounds.HANZE_ABSOLUTE_ZERO_CHARGE.get(),
                SoundSource.PLAYERS, .95F, .90F);
        playIceCrack(player.level(), center, .32F, 1.45F);
    }

    public static void cancel(ServerPlayer player, PlayerData data) {
        if (data.getPlacementType() == ABSOLUTE_ZERO_PLACEMENT) {
            data.setPlacementType(0);
            data.setCooldown("jueduilingdu", 0);
            PlayerDataProvider.syncToClient(player);
        }
    }

    /**
     * 霜寒领域的服务器伤害钩子。返回本次伤害应乘的倍率；冰冻处决伤害单独走真实伤害结算。
     */
    public static float onFrostDomainDamage(ServerPlayer attacker, LivingEntity target) {
        if (!com.myriad.combat.SkillTargets.alive(target) || FROST_EXECUTION_DAMAGE.contains(target.getUUID())) {
            return 1.0F;
        }
        ServerLevel level = attacker.level();
        long now = level.getGameTime();
        boolean slowed = target.hasEffect(MobEffects.SLOWNESS);
        boolean frozen = com.myriad.combat.FrozenControlSystem.isFrozenBy(attacker, target, now);

        if (frozen) {
            // 冰冻目标受到寒泽下一次伤害时，触发最大生命值 10% 的真实伤害并立即解冻。
            com.myriad.combat.FrozenControlSystem.cleanse(target.getUUID());
            target.removeEffect(MobEffects.SLOWNESS);
            FROST_EXECUTION_DAMAGE.add(target.getUUID());
            try {
                DamageCalc.dealPercentMaxHealthDamage(attacker, target, .10F, Kind.TRUE, 4);
            } finally {
                FROST_EXECUTION_DAMAGE.remove(target.getUUID());
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * .55,
                    target.getZ(), 34, .35, .45, .35, .035);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.HANZE_PASSIVE_PROC.get(),
                    SoundSource.PLAYERS, .72F, .84F);
            playIceCrack(level, target.position(), .48F, 1.05F);
        }

        Map<UUID, Long> cooldowns = FROST_MARK_COOLDOWNS.computeIfAbsent(attacker.getUUID(), ignored -> new HashMap<>());
        long cooldownUntil = cooldowns.getOrDefault(target.getUUID(), 0L);
        if (cooldownUntil > now) {
            return slowed ? 1.15F : 1.0F;
        }
        if (cooldownUntil != 0L) cooldowns.remove(target.getUUID());

        Map<UUID, FrostMark> marks = FROST_MARKS.computeIfAbsent(attacker.getUUID(), ignored -> new HashMap<>());
        FrostMark old = marks.get(target.getUUID());
        int stacks = old != null && old.until > now ? old.stacks + 1 : 1;
        if (stacks >= 4) {
            // 满层标记与 10 秒触发冷却同步存在，期间持续全亮，冷却结束后一并消失。
            marks.put(target.getUUID(), new FrostMark(4, now + FROST_MARK_TRIGGER_COOLDOWN));
            cooldowns.put(target.getUUID(), now + FROST_MARK_TRIGGER_COOLDOWN);
            com.myriad.combat.FrozenControlSystem.apply(level, attacker, target, FROST_FREEZE_DURATION);
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * .55,
                    target.getZ(), 48, .42, .55, .42, .04);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.HANZE_PASSIVE_PROC.get(),
                    SoundSource.PLAYERS, .85F, 1.28F);
            playIceCrack(level, target.position(), .66F, 1.34F);
        } else {
            marks.put(target.getUUID(), new FrostMark(stacks, now + FROST_MARK_DURATION));
        }
        return slowed ? 1.15F : 1.0F;
    }

    /** 仅向寒泽本人发送四格寒霜标记：灰色为空层，青色为已叠加层。 */
    private static void tickFrostMarkDisplay(ServerPlayer player, ServerLevel level) {
        Map<UUID, FrostMark> marks = FROST_MARKS.get(player.getUUID());
        if (marks == null || marks.isEmpty()) return;
        long now = level.getGameTime();
        java.util.Iterator<Map.Entry<UUID, FrostMark>> iterator = marks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FrostMark> entry = iterator.next();
            FrostMark mark = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (mark.until <= now || !(entity instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target)) {
                iterator.remove();
                continue;
            }
            double y = target.getY() + target.getBbHeight() + .52;
            for (int index = 0; index < 4; index++) {
                boolean lit = index < mark.stacks;
                DustParticleOptions particle = new DustParticleOptions(
                        lit ? 0x15EAEA : 0x565E66, .72F);
                level.sendParticles(player, particle, false, false,
                        target.getX() + (index - 1.5) * .22, y, target.getZ(), 1, 0, 0, 0, 0);
            }
        }
        if (marks.isEmpty()) FROST_MARKS.remove(player.getUUID());
    }

    /** Compatibility entry for historical callers; target-only cleanup belongs to the shared service. */
    public static void cleanseFrozen(UUID targetId) {
        com.myriad.combat.FrozenControlSystem.cleanse(targetId);
    }

    /** 清理退出玩家的临时投射物、领域与被动标记。 */
    public static void clearFor(UUID playerId) {
        com.myriad.character.ability.SkillChargeSystem.clear(playerId);
        ZONES.remove(playerId);
        ICE_VOLLEYS.remove(playerId);
        List<IceSpike> spikes = ICE_SPIKES.remove(playerId);
        if (spikes != null) spikes.forEach(HanzeSkills::discardIceSpikeModel);
        PENDING_BLASTS.remove(playerId);
        PASSIVE_SOUND_UNTIL.remove(playerId);
        FROST_MARKS.remove(playerId);
        FROST_MARK_COOLDOWNS.remove(playerId);
        com.myriad.combat.FrozenControlSystem.clearFor(playerId);
        FROST_EXECUTION_DAMAGE.remove(playerId);
    }

    private static Vec3 placementPoint(ServerPlayer player) {
        return PlacementUtil.pointOnGround(player, 20.0);
    }

    private static void drawPlacementPreview(ServerLevel level, ServerPlayer player, Vec3 center) {
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2 * i / 36.0;
            level.sendParticles(player, ParticleTypes.SNOWFLAKE, false, false,
                    center.x + Math.cos(angle) * 10, center.y + .08, center.z + Math.sin(angle) * 10,
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(player, ParticleTypes.END_ROD, false, false, center.x, center.y + .2, center.z,
                8, .5, .15, .5, .01);
    }

    private static void spawnShieldAura(ServerLevel level, ServerPlayer player) {
        double phase = level.getGameTime() * .26;
        for (int i = 0; i < 4; i++) {
            double angle = phase + Math.PI * 2 * i / 4.0;
            double radius = .65;
            double y = player.getY() + .35 + i * .38;
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, .01, .01, .01, .005);
            level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX() - Math.cos(angle) * radius,
                    y + .15, player.getZ() - Math.sin(angle) * radius, 1, .01, .01, .01, .005);
        }
    }

    /** 千里冰封的冰锥雨：从领域上空落下的纵向雪晶轨迹，纯表现不额外造成伤害。 */
    private static void spawnFallingIceSpikes(ServerLevel level, Vec3 center, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            double distance = Math.sqrt(level.getRandom().nextDouble()) * radius;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            for (double y = center.y + 9; y >= center.y + .3; y -= 1.2) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 2, .08, .15, .08, .02);
            }
            level.sendParticles(ParticleTypes.END_ROD, x, center.y + .25, z, 4, .12, .03, .12, .01);
        }
    }

    private static LivingEntity firstHitEnemy(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        AABB box = new AABB(Math.min(from.x, to.x) - .75, Math.min(from.y, to.y) - .75, Math.min(from.z, to.z) - .75,
                Math.max(from.x, to.x) + .75, Math.max(from.y, to.y) + .75, Math.max(from.z, to.z) + .75);
        Vec3 delta = to.subtract(from);
        double lengthSqr = delta.lengthSqr();
        LivingEntity result = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                box, e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !ally(player, e))) {
            Vec3 offset = entity.getBoundingBox().getCenter().subtract(from);
            double progress = offset.dot(delta) / lengthSqr;
            if (progress < 0 || progress > 1 || progress >= best) continue;
            Vec3 closest = from.add(delta.scale(progress));
            double radius = .58 + Math.max(entity.getBbWidth(), entity.getBbHeight()) * .3;
            if (closest.distanceToSqr(entity.getBoundingBox().getCenter()) <= radius * radius) {
                result = entity;
                best = progress;
            }
        }
        return result;
    }

    private static Vec3 firstSolidBlock(ServerLevel level, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(distance / .2));
        for (int i = 1; i <= steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            if (level.getBlockState(BlockPos.containing(point)).isSolid()) return point;
        }
        return null;
    }

    /** 原版冰块使用玻璃材质音组；此处叠加其碎裂声以强化冰晶质感。 */
    private static void playIceCrack(ServerLevel level, Vec3 position, float volume, float pitch) {
        level.playSound(null, position.x, position.y, position.z, SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, volume, pitch);
    }

    private static void hitFrost(ServerLevel level, ServerPlayer player, Vec3 center, double radius, float damage,
            int slowTicks, int slowAmp, boolean freeze) {
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius, 2.5, radius),
                e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !ally(player, e))) {
            DamageCalc.dealSkillDamage(player, enemy, damage, Kind.MAGIC, freeze ? 2 : 3);
            if (freeze) {
                com.myriad.combat.FrozenControlSystem.apply(level, player, enemy, slowTicks);
            } else {
                enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowTicks, slowAmp, false, false));
            }
        }
    }

    private static LivingEntity nearestFacing(ServerLevel level, ServerPlayer player, double range) {
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null; double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                x -> x != player && x.isAlive() && !ally(player, x))) {
            Vec3 delta = e.getEyePosition().subtract(player.getEyePosition());
            double dist = delta.length();
            if (dist <= range && delta.normalize().dot(look) > .85 && dist < bestDist) { best = e; bestDist = dist; }
        }
        return best;
    }

    private static ServerPlayer nearestAlly(ServerLevel level, ServerPlayer player, double range) {
        ServerPlayer best = null; double bestDist = Double.MAX_VALUE;
        for (ServerPlayer other : level.players()) {
            double dist = other.distanceToSqr(player);
            if (other != player && ally(player, other) && dist < range * range && dist < bestDist) { best = other; bestDist = dist; }
        }
        return best;
    }

    private static boolean ally(ServerPlayer player, LivingEntity other) {
        return com.myriad.combat.SkillTargets.ally(player, other);
    }

    private record FrostZone(Vec3 center, long until, int level) { }
    private record FrostMark(int stacks, long until) { }
    private static final class IceVolley {
        private final float damage;
        private long nextFire;
        private int shotsFired;
        private IceVolley(long nextFire, float damage) {
            this.nextFire = nextFire;
            this.damage = damage;
        }
    }
    private static final class IceSpike {
        private Vec3 position;
        private final Vec3 direction;
        private final float damage;
        private double traveled;
        private ItemDisplay visual;

        private IceSpike(Vec3 position, Vec3 direction, float damage) {
            this.position = position;
            this.direction = direction;
            this.damage = damage;
        }
    }
    private record PendingBlast(Vec3 center, long detonateAt, float damage) { }
}
