package com.myriad.character.skills.chixiao;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.PlacementUtil;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.network.NetworkSetup.ParabolicMotionPayload;

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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 破军·赤霄：战意叠层、扇形斩、两段追魂踏、招架反击与单体斩杀。 */
public final class ChiXiaoSkills extends Skill {
    public static final int POJUN_PLACEMENT = 6;
    public enum Type { PASSIVE, Q, W, E, R }
    private final Type type;
    private static final Map<UUID, BattleIntent> INTENTS = new HashMap<>();
    private static final Map<UUID, DashState> DASHES = new HashMap<>();
    private static final Map<UUID, Vec3> RETURNS = new HashMap<>();
    private static final Map<UUID, Long> RETURN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> PARRIES = new HashMap<>();
    private static final Map<UUID, LeapState> LEAPS = new HashMap<>();
    private static final Map<UUID, List<Debris>> DEBRIS = new HashMap<>();
    private static final Map<UUID, ImpactRingState> IMPACT_RINGS = new HashMap<>();
    private static final Map<UUID, Long> INTENT_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, MarkState> BLOOD_MARKS = new HashMap<>();
    private static final double DUANYUE_RANGE = 4.0;
    private static final double DUANYUE_CONE_DOT = .15;
    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(0xD04532, 1.25F);
    private static final DustParticleOptions EARTH_DUST = new DustParticleOptions(0x7F4530, .95F);

    public ChiXiaoSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "chixiao_passive";
            case Q -> "duanyuezhan";
            case W -> "zhuihunta";
            case E -> "chixiaofanfeng";
            case R -> "pojunwuhui";
        }, switch (type) {
            case PASSIVE -> 0;
            case Q -> 140;
            case W -> 240;
            case E -> 360;
            case R -> 1600;
        }, switch (type) {
            case PASSIVE -> 0.0F;
            case Q -> 72.0F;
            case W -> 90.0F;
            case E -> 126.0F;
            case R -> 306.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getCooldownTicks(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case W -> Math.max(180, 240 - (lv - 1) * 8);
            case R -> Math.max(1, 1600 - (lv - 1) * 260);
            default -> super.getCooldownTicks(level);
        };
    }
    @Override public String levelEffects(int level) {
        return switch (type) {
            case PASSIVE -> "连续伤害同一英雄叠3层战意；下个技能附加48+50%物攻真实伤害并获得25%全能吸血";
            case Q -> "前方4格扇形斩 " + (12 + (level - 1) * 12) + "+75%物攻；半血目标额外30%伤害";
            case W -> "突进约8格造成25+60%物攻并减速45%，2秒内可返回原位";
            case E -> "1秒招架，正面减伤70%；成功后反击 " + (80 + (level - 1) * 60) + "+250%物攻并眩晕";
            case R -> "14格选点跃斩 " + (35 + (level - 1) * 25) + "+110%物攻；冷却 " + Math.max(0, 80 - (level - 1) * 13) + " 秒";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        int lv = Math.max(1, data.skillLevel(getId()));
        float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        if (type == Type.Q) {
            Vec3 look = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize();
            boolean hit = false;
            for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(DUANYUE_RANGE),
                    e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !ally(player, e))) {
                if (!insideDuanYueArc(player, enemy, look)) continue;
                float damage = (float)(48 + (lv - 1) * 48 + ad * 3.0);
                if (enemy.getHealth() / enemy.getMaxHealth() < .5F) damage *= 1.30F;
                dealEmpowered(player, enemy, damage, Kind.PHYSICAL, 0);
                hit = true;
                if (enemy.getHealth() / enemy.getMaxHealth() < .5F) healMissing(player, .05F + ((lv - 1) / 2) * .01F);
            }
            spawnQArc(level, player, look);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .9F, .78F);
            level.playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, .55F, .68F);
            if (hit) level.playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, .8F, .72F);
        } else if (type == Type.W) {
            Long returnUntil = RETURN_UNTIL.get(player.getUUID());
            Vec3 origin = RETURNS.get(player.getUUID());
            if (origin != null && returnUntil != null && level.getGameTime() < returnUntil) {
                player.teleportTo(origin.x, origin.y, origin.z); player.resetFallDistance();
                RETURNS.remove(player.getUUID()); RETURN_UNTIL.remove(player.getUUID()); DASHES.remove(player.getUUID());
                data.setBuff("zhuihunta_recast", 0);
                MarkState returnedMark = BLOOD_MARKS.remove(player.getUUID());
                if (returnedMark != null && returnedMark.stand != null && returnedMark.stand.isAlive()) returnedMark.stand.discard();
                level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + .5, origin.z, 18, .3, .3, .3, .03);
                level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .55F, 1.4F);
            } else {
                Vec3 flat = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
                if (flat.lengthSqr() < .001) return;
                flat = flat.normalize();
                RETURNS.put(player.getUUID(), player.position()); RETURN_UNTIL.put(player.getUUID(), level.getGameTime() + 40);
                data.setBuff("zhuihunta_recast", 40);
                MarkState oldMark = BLOOD_MARKS.remove(player.getUUID());
                if (oldMark != null && oldMark.stand != null && oldMark.stand.isAlive()) oldMark.stand.discard();
                BLOOD_MARKS.put(player.getUUID(), new MarkState(player.position(), level.getGameTime() + 70,
                        spawnBloodClone(level, player)));
                DASHES.put(player.getUUID(), new DashState(flat, 9, lv, player.position()));
                PacketDistributor.sendToPlayer(player, new DashPayload((float)flat.x, 0, (float)flat.z, 9), new CustomPacketPayload[0]);
                player.setDeltaMovement(flat.x * .9, player.getDeltaMovement().y, flat.z * .9);
                for (int i = 0; i < 18; i++) { double a = Math.PI * 2 * i / 18.0;
                    level.sendParticles(BLOOD_DUST, player.getX() + Math.cos(a) * .7, player.getY() + .1,
                            player.getZ() + Math.sin(a) * .7, 1, 0, .35, 0, .03); }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, .65F, 1.45F);
            }
        } else if (type == Type.E) {
            PARRIES.put(player.getUUID(), level.getGameTime() + 20);
            data.setBuff("chixiaofanfeng", 20);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .85F, .8F);
        } else if (type == Type.R) {
            data.setPlacementType(POJUN_PLACEMENT); PlayerDataProvider.syncToClient(player);
        }
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level(); long now = level.getGameTime();
        BattleIntent intent = INTENTS.get(player.getUUID());
        if (intent != null && now - intent.lastHit > 100) { INTENTS.remove(player.getUUID()); data.setBuff("chixiao_passive", 0); }
        else if (intent != null) data.setBuff("chixiao_passive", intent.stacks);
        long intentCd = INTENT_COOLDOWNS.getOrDefault(player.getUUID(), 0L);
        if (intentCd > now) data.setBuff("chixiao_passive_cd", (int)(intentCd - now)); else INTENT_COOLDOWNS.remove(player.getUUID());
        tickBloodMark(player, level, now);
        if (RETURN_UNTIL.getOrDefault(player.getUUID(), 0L) <= now) {
            RETURN_UNTIL.remove(player.getUUID()); RETURNS.remove(player.getUUID()); data.setBuff("zhuihunta_recast", 0);
        }
        if (PARRIES.getOrDefault(player.getUUID(), 0L) <= now) { PARRIES.remove(player.getUUID()); data.setBuff("chixiaofanfeng", 0); }
        if (PARRIES.getOrDefault(player.getUUID(), 0L) > now) spawnParryShield(level, player);
        if (data.getPlacementType() == POJUN_PLACEMENT && now % 3 == 0) {
            Vec3 preview = placementPoint(player, 14); if (preview != null) for (int i = 0; i < 24; i++) { double a = Math.PI * 2 * i / 24.0;
                level.sendParticles(player, BLOOD_DUST, false, false, preview.x + Math.cos(a) * 3, preview.y + .08, preview.z + Math.sin(a) * 3, 1, 0, 0, 0, 0); }
        }
        tickLeap(player, data, level, now);
        tickImpactRing(player, level, now);
        tickDebris(player, level);
        DashState dash = DASHES.get(player.getUUID());
        if (dash != null) {
            if (dash.remaining-- <= 0) { DASHES.remove(player.getUUID()); player.setDeltaMovement(0, player.getDeltaMovement().y, 0); return; }
            Vec3 previous = dash.lastPos;
            double moved = player.position().distanceTo(dash.lastPos); dash.lastPos = player.position();
            if (moved < .04 && dash.moved > .1) { DASHES.remove(player.getUUID()); return; }
            dash.moved += moved;
            player.setDeltaMovement(dash.direction.x * .9, player.getDeltaMovement().y, dash.direction.z * .9);
            if (now % 2 == 0) level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + .5, player.getZ(), 3, .1, .12, .1, .01);
            for (LivingEntity enemy : com.myriad.combat.DashHitbox.targets(player, previous, player.position(), 1.6, dash.hit)) {
                if (dash.hit.add(enemy.getUUID())) {
                    float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
                    dealEmpowered(player, enemy, (float)(100 + ad * 2.40), Kind.PHYSICAL, 1);
                    level.playSound(null, enemy.getX(), enemy.getY() + .7, enemy.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, .75F, .76F);
                    enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, Math.min(1, (dash.level - 1) / 2), false, false, false));
                }
            }
        }
    }

    public static boolean canReturn(ServerPlayer player) {
        return RETURNS.containsKey(player.getUUID())
                && RETURN_UNTIL.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
    }

    /** 伤害事件调用：赤霄连续伤害同一敌方英雄叠战意，离战5秒自动清空。 */
    public static void onDamageDealt(ServerPlayer attacker, LivingEntity target) {
        if (!(target instanceof ServerPlayer) || !com.myriad.combat.SkillTargets.alive(target)) return;
        long now = attacker.level().getGameTime(); BattleIntent intent = INTENTS.computeIfAbsent(attacker.getUUID(), id -> new BattleIntent());
        if (INTENT_COOLDOWNS.getOrDefault(attacker.getUUID(), 0L) > now) return;
        if (!target.getUUID().equals(intent.target) || now - intent.lastHit > 100) { intent.target = target.getUUID(); intent.stacks = 0; }
        intent.stacks = Math.min(3, intent.stacks + 1); intent.lastHit = now;
        PlayerData data = PlayerDataProvider.get(attacker); data.setBuff("chixiao_passive", intent.stacks);
        attacker.level().playSound(null, attacker.getX(), attacker.getY() + .7, attacker.getZ(),
                intent.stacks >= 3 ? SoundEvents.ANVIL_LAND : SoundEvents.IRON_STEP,
                SoundSource.PLAYERS, intent.stacks >= 3 ? .55F : .32F, .82F + intent.stacks * .12F);
    }

    /** 招架仅减伤正面攻击；成功挡下敌方英雄伤害时立即反击。 */
    public static void onIncomingDamage(ServerPlayer victim, net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        long until = PARRIES.getOrDefault(victim.getUUID(), 0L); long now = victim.level().getGameTime();
        if (until > now && event.getSource().getEntity() instanceof LivingEntity attacker && isFront(victim, attacker)) {
            event.getContainer().setNewDamage(event.getAmount() * .30F);
            if (attacker instanceof ServerPlayer && !ally(victim, attacker)) {
                PARRIES.remove(victim.getUUID()); PlayerDataProvider.get(victim).setBuff("chixiaofanfeng", 0);
                float ad = (float)victim.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
                int level = Math.max(1, PlayerDataProvider.get(victim).skillLevel("chixiaofanfeng"));
                dealEmpowered(victim, attacker, (float)(80 + (level - 1) * 60 + ad * 2.50), Kind.PHYSICAL, 2);
                // 高等级缓慢在原版中仍可移动；用 255 级将其稳定地定身，作为 0.8~1.1 秒眩晕表现。
                attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 16 + Math.min(6, (level - 1) * 2), 255, false, false, false));
                victim.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1F, .62F);
                victim.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .55F, 1.28F);
            }
        }
        PlayerData data = PlayerDataProvider.get(victim);
        if (data.getBuff("pojunwuhui") > 0 && event.getAmount() > 0) event.getContainer().setNewDamage(event.getAmount() * .70F);
    }

    public static boolean hasUltimateTarget(ServerPlayer player) { return nearestEnemyHero(player.level(), player, 14) != null; }
    public static void clearFor(UUID id) {
        INTENTS.remove(id); DASHES.remove(id); RETURNS.remove(id); RETURN_UNTIL.remove(id); PARRIES.remove(id);
        LEAPS.remove(id); IMPACT_RINGS.remove(id); INTENT_COOLDOWNS.remove(id);
        clearDebris(id);
        MarkState mark = BLOOD_MARKS.remove(id);
        if (mark != null && mark.stand != null && mark.stand.isAlive()) mark.stand.discard();
    }

    public static void confirmR(ServerPlayer player, PlayerData data) {
        Vec3 point = placementPoint(player, 14);
        // placementPoint 已将视线限制在 14 格；回退落点可在远端地面，不能再以玩家脚部的三维距离拒绝。
        if (point == null) return;
        data.setPlacementType(0); PlayerDataProvider.syncToClient(player);
        clearDebris(player.getUUID());
        Vec3 delta = point.subtract(player.position());
        LEAPS.put(player.getUUID(), new LeapState(player.position(), point, player.level().getGameTime(),
                Math.max(1, data.skillLevel("pojunwuhui"))));
        PacketDistributor.sendToPlayer(player,
                new ParabolicMotionPayload((float)delta.x, (float)delta.y, (float)delta.z, 4.0F, 16),
                new CustomPacketPayload[0]);
        player.resetFallDistance();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, .8F, .68F);
    }

    public static void cancelR(ServerPlayer player, PlayerData data) {
        if (data.getPlacementType() == POJUN_PLACEMENT) {
            data.setPlacementType(0); data.setCooldown("pojunwuhui", 0);
            PlayerDataProvider.syncToClient(player);
        }
    }

    private static void tickLeap(ServerPlayer player, PlayerData data, ServerLevel level, long now) {
        LeapState leap = LEAPS.get(player.getUUID()); if (leap == null) return;
        long age = now - leap.startTick();
        if (age < 16) {
            player.setDeltaMovement(leapMotion(leap, (int)age)); player.resetFallDistance();
            level.sendParticles(BLOOD_DUST, player.getX(), player.getY() + .1, player.getZ(), 4, .25, .1, .25, .01);
            return;
        }
        Vec3 remaining = leap.end().subtract(player.position());
        boolean nearLanding = remaining.horizontalDistanceSqr() <= 1.0 && Math.abs(remaining.y) <= 1.5;
        if ((!player.onGround() || !nearLanding) && age < 28) {
            Vec3 horizontal = new Vec3(remaining.x, 0, remaining.z);
            if (horizontal.lengthSqr() > .01) horizontal = horizontal.normalize().scale(Math.min(.8, Math.sqrt(horizontal.lengthSqr()) * .35));
            double vy = remaining.y > .35 ? .38 : -.48;
            player.setDeltaMovement(horizontal.x, vy, horizontal.z); player.resetFallDistance();
            return;
        }
        Vec3 c = player.position(); float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        LEAPS.remove(player.getUUID()); player.setDeltaMovement(0, 0, 0); player.resetFallDistance();
        spawnDebris(level, player, c);
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(18),
                e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !ally(player, e) && e.position().distanceTo(c) <= 3.0)) {
            float base = (float)(140 + (leap.level - 1) * 100 + ad * 4.40);
            float missing = 1F - enemy.getHealth() / enemy.getMaxHealth();
            dealEmpowered(player, enemy, base * (1F + missing * .8F), Kind.PHYSICAL, 3);
            enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false, false));
            if (!com.myriad.combat.SkillTargets.alive(enemy) || enemy.getHealth() <= 0) data.setCooldown("pojunwuhui", 0);
        }
        IMPACT_RINGS.put(player.getUUID(), new ImpactRingState(c, now));
        level.playSound(null, c.x, c.y, c.z, SoundEvents.HEAVY_CORE_PLACE, SoundSource.PLAYERS, .75F, .72F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .65F, .58F);
    }

    private static Vec3 leapMotion(LeapState leap, int age) {
        int ticks = 16;
        Vec3 delta = leap.end().subtract(leap.origin());
        double t0 = age / (double)ticks, t1 = (age + 1) / (double)ticks;
        double arcStep = 16.0 * (t1 * (1.0 - t1) - t0 * (1.0 - t0));
        return new Vec3(delta.x / ticks, delta.y / ticks + arcStep, delta.z / ticks);
    }

    /** 破军无回落地冲击波：连续扩张的双层高密度圆环，不使用爆炸粒子。 */
    private static void tickImpactRing(ServerPlayer player, ServerLevel level, long now) {
        ImpactRingState ring = IMPACT_RINGS.get(player.getUUID());
        if (ring == null) return;
        long age = now - ring.startTick;
        if (age > 14) { IMPACT_RINGS.remove(player.getUUID()); return; }
        double progress = age / 14.0;
        double radius = .35 + progress * 3.35;
        DustParticleOptions brightBlood = new DustParticleOptions(0xFF3B25, 2.45F);
        DustParticleOptions darkEarth = new DustParticleOptions(0x8C3028, 2.05F);
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * 2 * i / 64.0;
            double waveY = Math.sin(angle * 4 + age * .45) * .09;
            level.sendParticles(brightBlood, ring.center.x + Math.cos(angle) * radius,
                    ring.center.y + .16 + waveY, ring.center.z + Math.sin(angle) * radius,
                    1, .025, .025, .025, .01);
            if ((i & 1) == 0) level.sendParticles(darkEarth,
                    ring.center.x + Math.cos(angle) * (radius - .16), ring.center.y + .08,
                    ring.center.z + Math.sin(angle) * (radius - .16), 1, .02, .02, .02, .008);
        }
    }

    /** 将落点周围真实方块以无碰撞 ItemDisplay 抛散，纯视觉，不修改地图。 */
    private static void spawnDebris(ServerLevel level, ServerPlayer player, Vec3 center) {
        List<Debris> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) { double a = level.getRandom().nextDouble() * Math.PI * 2; double r = .5 + level.getRandom().nextDouble() * 2.5;
            BlockPos pos = BlockPos.containing(center.x + Math.cos(a) * r, center.y - .5, center.z + Math.sin(a) * r); BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock().asItem() == net.minecraft.world.item.Items.AIR) continue;
            ItemDisplay display = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED); if (display == null) continue;
            display.getSlot(0).set(new ItemStack(state.getBlock())); display.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(display));
            Vec3 v = new Vec3(Math.cos(a), .7 + level.getRandom().nextDouble() * .6, Math.sin(a)).scale(.35 + level.getRandom().nextDouble() * .35);
            list.add(new Debris(display, display.position(), v, 24));
        }
        if (!list.isEmpty()) DEBRIS.put(player.getUUID(), list);
    }

    /** 新一轮破军无回开始前，立即回收上一轮仍在空中的纯视觉碎块。 */
    private static void clearDebris(UUID playerId) {
        List<Debris> list = DEBRIS.remove(playerId);
        if (list == null) return;
        for (Debris debris : list) {
            if (debris.display != null && debris.display.isAlive()) debris.display.discard();
        }
    }

    private static void tickDebris(ServerPlayer player, ServerLevel level) {
        List<Debris> list = DEBRIS.get(player.getUUID()); if (list == null) return;
        list.removeIf(d -> { if (!d.display.isAlive() || d.ticks-- <= 0) { if (d.display.isAlive()) d.display.discard(); return true; }
            d.velocity = d.velocity.add(0, -.055, 0); d.position = d.position.add(d.velocity); d.display.setPos(d.position.x, d.position.y, d.position.z); return false; });
        if (list.isEmpty()) DEBRIS.remove(player.getUUID());
    }

    private static void spawnQArc(ServerLevel level, ServerPlayer player, Vec3 look) {
        Vec3 right = new Vec3(-look.z, 0, look.x);
        DustParticleOptions largeBlood = new DustParticleOptions(0xD04532, 2.6F);
        DustParticleOptions largeEarth = new DustParticleOptions(0x7F4530, 2.15F);
        for (int layer = 0; layer < 4; layer++) for (int i = 0; i <= 54; i++) {
            double t = i / 54.0; double crescent = Math.sin(Math.PI * t) * (1.15 + layer * .13);
            Vec3 p = player.position().add(look.scale(.8 + 4.1 * t + crescent))
                    .add(right.scale((t - .5) * 3.8)).add(0, 3.3 - 5.4 * t + crescent * .28, 0);
            level.sendParticles((i + layer) % 2 == 0 ? largeBlood : largeEarth,
                    p.x, p.y, p.z, 2, .10, .10, .10, .012);
        }
    }

    private static void spawnParryShield(ServerLevel level, ServerPlayer player) {
        Vec3 look = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize(); Vec3 right = new Vec3(-look.z, 0, look.x);
        for (int side = 0; side < 4; side++) { Vec3 dir = side == 0 ? look : side == 1 ? look.scale(-1) : side == 2 ? right : right.scale(-1);
            for (int i = 0; i < 14; i++) { double a = Math.PI * 2 * i / 14.0; Vec3 p = player.position().add(dir.scale(.82)).add(right.scale(Math.cos(a) * .38)).add(0, 1 + Math.sin(a) * .38, 0);
                level.sendParticles(BLOOD_DUST, p.x, p.y, p.z, 1, 0, 0, 0, 0); }}
    }

    private static void tickBloodMark(ServerPlayer player, ServerLevel level, long now) {
        MarkState mark = BLOOD_MARKS.get(player.getUUID()); if (mark == null || now >= mark.until) {
            if (mark != null && mark.stand != null && mark.stand.isAlive()) mark.stand.discard();
            BLOOD_MARKS.remove(player.getUUID()); return; }
        if (mark.stand != null && mark.stand.isAlive()) {
            mark.stand.setInvulnerable(true);
            mark.stand.setHealth(mark.stand.getMaxHealth());
            mark.stand.clearFire();
        }
        for (int i = 0; i < 10; i++) { double a = Math.PI * 2 * i / 10.0 + now * .08;
            level.sendParticles(BLOOD_DUST, mark.pos.x + Math.cos(a) * .72, mark.pos.y + .12 + (now % 14) * .07,
                    mark.pos.z + Math.sin(a) * .72, 1, .04, .14, .04, .02); }
    }

    private static Mannequin spawnBloodClone(ServerLevel level, ServerPlayer player) {
        Mannequin stand = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (stand == null) return null;
        stand.setPos(player.getX(), player.getY(), player.getZ());
        stand.setYRot(player.getYRot()); stand.setYHeadRot(player.getYHeadRot());
        stand.setNoGravity(true); stand.setInvulnerable(true);
        stand.setHealth(stand.getMaxHealth());
        stand.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        stand.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copy());
        stand.setItemSlot(EquipmentSlot.OFFHAND, player.getOffhandItem().copy());
        stand.setItemSlot(EquipmentSlot.HEAD, player.getItemBySlot(EquipmentSlot.HEAD).copy());
        stand.setItemSlot(EquipmentSlot.CHEST, player.getItemBySlot(EquipmentSlot.CHEST).copy());
        stand.setItemSlot(EquipmentSlot.LEGS, player.getItemBySlot(EquipmentSlot.LEGS).copy());
        stand.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET).copy());
        level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(stand));
        return stand;
    }

    private static Vec3 placementPoint(ServerPlayer player, int range) {
        return PlacementUtil.pointOnGround(player, range);
    }

    /**
     * 断岳斩的4格射程以目标碰撞箱前缘计算，而不是以实体中心计算。
     * 这样玩家和大型野怪在月牙已经触及身体时都能得到一致命中判定。
     */
    private static boolean insideDuanYueArc(ServerPlayer player, LivingEntity target, Vec3 look) {
        net.minecraft.world.phys.AABB box = target.getBoundingBox();
        double closestX = net.minecraft.util.Mth.clamp(player.getX(), box.minX, box.maxX);
        double closestZ = net.minecraft.util.Mth.clamp(player.getZ(), box.minZ, box.maxZ);
        double dx = closestX - player.getX();
        double dz = closestZ - player.getZ();
        if (dx * dx + dz * dz > DUANYUE_RANGE * DUANYUE_RANGE) return false;
        Vec3 toCenter = target.getBoundingBox().getCenter().subtract(player.position()).multiply(1, 0, 1);
        return toCenter.lengthSqr() < 1.0E-6 || look.dot(toCenter.normalize()) >= DUANYUE_CONE_DOT;
    }

    private static DamageCalc.Roll dealEmpowered(ServerPlayer attacker, LivingEntity target, float damage, Kind kind, int sourceSlot) {
        BattleIntent intent = INTENTS.get(attacker.getUUID()); boolean empower = intent != null && intent.stacks >= 3;
        DamageCalc.Roll roll = DamageCalc.dealSkillDamage(attacker, target, damage, kind, sourceSlot);
        if (empower) {
            intent.stacks = 0;
            INTENT_COOLDOWNS.put(attacker.getUUID(), attacker.level().getGameTime() + 80);
            float ad = (float)attacker.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
            DamageCalc.dealSkillDamage(attacker, target, 48 + ad * .50F, Kind.TRUE, 4);
            float heal = roll.damage() * .25F;
            if (heal > 0) com.myriad.combat.CombatTelemetry.heal(attacker,
                    Math.min(heal, attacker.getMaxHealth() - attacker.getHealth()), "passive");
            attacker.level().sendParticles(ParticleTypes.ENCHANT, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 12, .35, .55, .35, .03);
        }
        return roll;
    }

    private static void healMissing(ServerPlayer player, float percent) {
        com.myriad.combat.CombatTelemetry.heal(player,
                Math.max(0, player.getMaxHealth() - player.getHealth()) * percent, "passive");
    }
    private static ServerPlayer nearestEnemyHero(ServerLevel level, ServerPlayer player, double range) {
        ServerPlayer best = null; double dBest = range * range;
        for (ServerPlayer other : level.players()) if (other != player && com.myriad.combat.SkillTargets.alive(other) && !ally(player, other)) { double d = other.distanceToSqr(player); if (d < dBest) { best = other; dBest = d; } }
        return best;
    }
    private static boolean isFront(ServerPlayer victim, LivingEntity attacker) { Vec3 to = attacker.position().subtract(victim.position()); return to.lengthSqr() > .001 && victim.getLookAngle().normalize().dot(to.normalize()) > .20; }
    private static boolean ally(ServerPlayer player, LivingEntity other) {
        MarkState mark = BLOOD_MARKS.get(player.getUUID());
        if (mark != null && mark.stand == other) return true;
        if (!(other instanceof ServerPlayer otherPlayer)) return false;
        GameManager manager = GameManager.get(); Faction a = manager == null ? null : manager.factionOf(player); Faction b = manager == null ? null : manager.factionOf(otherPlayer); return a != null && a == b;
    }
    private static final class BattleIntent { UUID target; int stacks; long lastHit; }
    private record MarkState(Vec3 pos, long until, Mannequin stand) { }
    private static final class DashState { final Vec3 direction; int remaining; final int level; Vec3 lastPos; double moved; final java.util.Set<UUID> hit = new java.util.HashSet<>(); DashState(Vec3 d,int r,int l,Vec3 p){direction=d;remaining=r;level=l;lastPos=p;} }
    private record LeapState(Vec3 origin, Vec3 end, long startTick, int level) { }
    private record ImpactRingState(Vec3 center, long startTick) { }
    private static final class Debris { final ItemDisplay display; Vec3 position, velocity; int ticks; Debris(ItemDisplay d,Vec3 p,Vec3 v,int t){display=d;position=p;velocity=v;ticks=t;} }
}
