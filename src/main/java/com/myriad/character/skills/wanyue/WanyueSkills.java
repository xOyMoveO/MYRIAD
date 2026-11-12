package com.myriad.character.skills.wanyue;

import com.mojang.math.Transformation;
import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.character.skills.sulan.FengQiangSkill;
import com.myriad.combat.DamageCalc;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.LaneSystem;
import com.myriad.game.MapSystem;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.player.ModShieldSystem;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 月轮·绾月：服务器权威月轮轨迹、折轨充能、追月 motion 与满月巡天。 */
public final class WanyueSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    private enum WheelMode { OUT, RETURNING, ORBIT }

    public static final String W_CHARGE = "skill_charge_zheguangliuguang";
    public static final String W_REMAINING = "skill_charge_zheguangliuguang_remaining";
    public static final String W_TOTAL = "skill_charge_zheguangliuguang_total";
    public static final String WHEEL_STATE = "wanyue_wheel_state";
    public static final String WHEEL_DISTANCE = "wanyue_wheel_distance";
    private static final DustParticleOptions MOON = new DustParticleOptions(0xA6E8FF, 1.0F);
    private static final DustParticleOptions SILVER = new DustParticleOptions(0xE9F7FF, .85F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD66B, 1.0F);
    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Query or shorten only the charge currently recovering; never grant a charge directly. */
    public static int runeRecharge(ServerPlayer p, double fraction) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.wReady.isEmpty()) return 0;
        long now = p.level().getGameTime();
        int remaining = (int)Math.max(0, s.wReady.peekFirst() - now);
        if (fraction > 0) {
            com.myriad.character.SequentialChargeTimers.moveFirstTo(s.wReady,
                    now + Math.max(1, (int)Math.ceil(remaining * (1 - fraction))));
        }
        return remaining;
    }
    private static final Map<UUID, Vec3> REQUESTED = new HashMap<>();
    private static final Map<UUID, Float> EMPOWERED_HIT_MULTIPLIERS = new HashMap<>();
    private final Type type;

    public WanyueSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "wanyue_passive";
            case Q -> "zhiyuehuixian";
            case W -> "zheguangliuguang";
            case E -> "taxianzuiyue";
            case R -> "manyuexuntian";
        }, switch (type) {
            case PASSIVE -> 0; case Q -> 200; case W -> 180; case E -> 320; case R -> 1900;
        }, switch (type) {
            case PASSIVE -> 0.0F; case Q -> 79.2F; case W -> 72.0F; case E -> 115.2F; case R -> 288.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getMaxCharges() { return type == Type.W ? 2 : 1; }
    @Override public int getCooldownTicks(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case Q -> Math.max(140, 200 - (lv - 1) * 20);
            case W -> Math.max(150, 180 - (lv - 1) * 10);
            case E -> Math.max(260, 320 - (lv - 1) * 20);
            case R -> Math.max(1300, 1900 - (lv - 1) * 300);
            default -> 0;
        };
    }

    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "射手普攻按80%物攻结算；月轮离体且未公转时普攻从月轮位置射出并造成90%本次普攻伤害；接轮后发射20格穿透月华，每多命中1个目标伤害降低33%，最低33%；踏弦接轮额外提高20%伤害与弹速";
            case Q -> "掷出 " + (18 + (lv - 1) * 8) + "+45%物攻；再施放回弦 "
                    + (12 + (lv - 1) * 6) + "+30%物攻";
            case W -> "2层充能；非大招期间朝准星所指方向最多飞12格，并造成 "
                    + (14 + (lv - 1) * 6) + "+28%物攻";
            case E -> "追月5格；接住月轮获得 " + (72 + (lv - 1) * 32) + "+100%物攻护盾并返还1层折轨";
            case R -> "月轮巡天 " + (4 + lv) + "秒，每次触碰造成 "
                    + (22 + (lv - 1) * 10) + "+25%物攻";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) { }

    public static void handleCast(ServerPlayer p, PlayerData data, int slot, Skill raw,
            boolean noCd, boolean noMana) {
        if (!(raw instanceof WanyueSkills skill)) return;
        State s = state(p); long now = p.level().getGameTime();
        boolean qRecast = skill.type == Type.Q && s.wheel != null && s.wheel.mode != WheelMode.RETURNING;
        if (skill.type == Type.W) {
            castW(p, data, skill, s, noCd, noMana, now);
            return;
        }
        if (!qRecast && !noCd && data.getCooldown(skill.getId()) > 0) {
            p.sendSystemMessage(Component.translatable("message.myriad.skill_cd",
                    (data.getCooldown(skill.getId()) + 19) / 20)); return;
        }
        if (!qRecast && !noMana && data.getMana() < skill.getManaCost()) {
            p.sendSystemMessage(Component.translatable("message.myriad.not_enough_mana")); return;
        }
        int lv = Math.max(1, data.skillLevel(skill.getId()));
        if (!qRecast && !noMana) data.spendMana(skill.getManaCost());
        switch (skill.type) {
            case Q -> castQ(p, data, s, lv, now);
            case E -> castE(p, data, s, lv, now);
            case R -> castR(p, data, s, lv, now);
            default -> { }
        }
        if (!qRecast && !noCd) data.setCooldown(skill.getId(), reducedCooldown(p, skill.getCooldownTicks(lv)));
        
        com.myriad.character.SkillPresentation.cast(p, skill);
        PlayerDataProvider.syncToClient(p);
    }

    private static void castQ(ServerPlayer p, PlayerData data, State s, int lv, long now) {
        if (s.wheel != null) {
            s.wheel.mode = WheelMode.RETURNING;
            s.wheel.returnHits.clear();
            data.setBuff("zhiyuehuixian_recast", 0);
            p.level().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_RETURN,
                    SoundSource.PLAYERS, .8F, 1.45F);
            return;
        }
        Vec3 direction = horizontal(p.getLookAngle());
        Vec3 start = p.getEyePosition().add(direction.scale(.8));
        s.wheel = new Wheel(start, direction.scale(1.05), WheelMode.OUT, lv, now);
        s.wheel.visual = spawnWheelVisual(p.level(), start, direction);
        s.wheel.travelLeft = 12.0;
        data.setBuff("zhiyuehuixian_recast", 80);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, .9F, 1.55F);
    }

    private static void castW(ServerPlayer p, PlayerData data, WanyueSkills skill, State s,
            boolean noCd, boolean noMana, long now) {
        recoverCharges(p, data, s, skill, now);
        if (s.wheel == null) {
            p.sendSystemMessage(Component.translatable("message.myriad.wanyue_wheel_required")); return;
        }
        if (!noCd && s.wCharges <= 0) {
            p.sendSystemMessage(Component.translatable("message.myriad.skill_cd",
                    Math.max(1, (int)Math.ceil((s.wReady.isEmpty() ? 20 : s.wReady.peekFirst() - now) / 20.0)))); return;
        }
        if (!noMana && data.getMana() < skill.getManaCost()) {
            p.sendSystemMessage(Component.translatable("message.myriad.not_enough_mana")); return;
        }
        com.myriad.character.SkillPresentation.cast(p, skill);
        if (!noMana) data.spendMana(skill.getManaCost());
        int lv = Math.max(1, data.skillLevel(skill.getId()));
        if (!noCd) {
            s.wCharges--;
            s.wRechargeTotal = reducedCooldown(p, skill.getCooldownTicks(lv));
            com.myriad.character.SequentialChargeTimers.enqueue(s.wReady, now, s.wRechargeTotal);
        }
        Vec3 look = horizontal(p.getLookAngle()), input = REQUESTED.get(p.getUUID());
        Wheel wheel = s.wheel;
        if (wheel.mode == WheelMode.ORBIT) {
            if (input == null) { wheel.detachedTicks = 8; wheel.velocity = look; }
            else {
                double forward = input.dot(look), side = input.dot(new Vec3(-look.z, 0, look.x));
                if (forward > .55) wheel.radiusTarget = 3.5;
                else if (forward < -.55) wheel.radiusTarget = 6.5;
                else wheel.clockwise = side >= 0 ? 1 : -1;
            }
        } else {
            // 非大招折轨：从当前月轮位置朝玩家准星所指方向飞行，单次最多推进 12 格。
            Vec3 eye = p.getEyePosition();
            Vec3 aimEnd = eye.add(p.getLookAngle().normalize().scale(64.0));
            BlockHitResult hit = p.level().clip(new ClipContext(eye, aimEnd,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
            Vec3 aim = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : aimEnd;
            aim = new Vec3(aim.x, wheel.pos.y, aim.z);
            Vec3 towardAim = aim.subtract(wheel.pos);
            if (towardAim.lengthSqr() < 1.0E-6) towardAim = horizontal(p.getLookAngle());
            wheel.velocity = towardAim.normalize();
            wheel.hoverTicks = 0;
            wheel.boostTicks = 0;
            wheel.travelLeft = Math.min(12.0, towardAim.length());
        }
        wheel.expiresAt = Math.max(wheel.expiresAt, now + 40);
        wheel.wLevel = lv; wheel.wDamageUntil = now + 12;
        syncCharges(data, s, skill, now);
        PlayerDataProvider.syncToClient(p);
    }

    private static void castE(ServerPlayer p, PlayerData data, State s, int lv, long now) {
        Vec3 dir = requestedOrLook(p);
        double distance = s.wheel == null ? 3.5 : 5.0;
        Vec3 step = dir.scale(distance / 6.0);
        s.dash = new Dash(step, 6, lv);
        PacketDistributor.sendToPlayer(p, new DashPayload((float)step.x, 0, (float)step.z, 6));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, .8F, 1.65F);
    }

    private static void castR(ServerPlayer p, PlayerData data, State s, int lv, long now) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(12)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 center = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().add(0, .9, 0)
                : p.position().add(horizontal(p.getLookAngle()).scale(8)).add(0, .9, 0);
        if (!MapSystem.contains(center.x, center.z)) center = p.position().add(0, .9, 0);
        if (s.wheel == null) {
            Vec3 launch = p.getEyePosition().add(horizontal(p.getLookAngle()).scale(.55)).add(0, -.35, 0);
            s.wheel = new Wheel(launch, horizontal(p.getLookAngle()), WheelMode.ORBIT, lv, now);
            s.wheel.visual = spawnWheelVisual(p.level(), launch, horizontal(p.getLookAngle()));
        }
        Wheel wheel = s.wheel;
        wheel.mode = WheelMode.ORBIT; wheel.orbitCenter = center; wheel.radius = 5;
        wheel.angle = Math.atan2(wheel.pos.z - center.z, wheel.pos.x - center.x);
        int entryTicks = wheel.pos.distanceToSqr(center.add(5, 0, 0)) > 1 ? 6 : 0;
        wheel.orbitUntil = now + entryTicks + 80 + lv * 20L; wheel.rLevel = lv; wheel.rHits.clear();
        wheel.radiusTarget = 5; wheel.expiresAt = wheel.orbitUntil + 80;
        wheel.orbitEntryTicks = entryTicks;
        data.setBuff("manyuexuntian", (int)(wheel.orbitUntil - now));
        data.setBuff("zhiyuehuixian_recast", (int)(wheel.orbitUntil - now));
        p.level().playSound(null, p.blockPosition(), com.myriad.registry.ModSounds.foley("blade"),
                SoundSource.PLAYERS, 1F, 1.3F);
    }

    public static void tick(ServerPlayer p, PlayerData data) {
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.WANYUE) return;
        State s = state(p); long now = p.level().getGameTime();
        if (s.empowered && data.getBuff("wanyue_empowered") <= 0) {
            s.empowered = false; s.empoweredChase = false;
        }
        recoverCharges(p, data, s, new WanyueSkills(Type.W), now);
        tickDash(p, data, s);
        tickWheel(p, data, s, now);
        tickMoonwaves(p, s);
        data.setBuff(WHEEL_STATE, s.wheel == null ? 0 : switch (s.wheel.mode) {
            case OUT -> 1; case RETURNING -> 2; case ORBIT -> 3;
        });
        data.setBuff(WHEEL_DISTANCE, s.wheel == null ? 0 : (int)Math.round(p.position().distanceTo(s.wheel.pos)));
    }

    private static void tickDash(ServerPlayer p, PlayerData data, State s) {
        if (s.dash == null) return;
        if (p.hasEffect(MobEffects.SLOWNESS) && p.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 200) {
            stopDash(p, s); return;
        }
        Vec3 step = s.dash.step;
        if (!MapSystem.contains(p.getX() + step.x, p.getZ() + step.z)
                || !p.level().noCollision(p, p.getBoundingBox().move(step))) {
            stopDash(p, s); return;
        }
        Vec3 dashFrom = p.getBoundingBox().getCenter();
        Vec3 dashTo = dashFrom.add(step);
        p.setDeltaMovement(step.x, 0, step.z);
        s.dash.ticks--;
        if (s.wheel != null && segmentDistanceSqr(s.wheel.pos, dashFrom, dashTo) <= 1.2 * 1.2) {
            int lv = s.dash.level;
            float shield = 72 + (lv - 1) * 32 + ad(p);
            ModShieldSystem.grant(p, "wanyue_taxianzuiyue", shield, 50);
            data.setBuff("taxianzuiyue_shield", 50);
            if (s.wCharges < 2) { s.wCharges++; if (!s.wReady.isEmpty()) s.wReady.removeLast(); }
            if (s.wheel.mode != WheelMode.ORBIT) catchWheel(p, data, s, true);
            syncCharges(data, s, new WanyueSkills(Type.W), p.level().getGameTime());
            PlayerDataProvider.syncToClient(p);
            stopDash(p, s); return;
        }
        if (s.dash.ticks <= 0) stopDash(p, s);
    }

    private static void stopDash(ServerPlayer p, State s) {
        s.dash = null;
        PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0));
    }

    private static void tickWheel(ServerPlayer p, PlayerData data, State s, long now) {
        Wheel w = s.wheel; if (w == null) return;
        if (w.mode != WheelMode.ORBIT && now >= w.expiresAt) { safeRecover(data, s); return; }
        Vec3 from = w.pos;
        if (w.windWallWait > 0) {
            w.windWallWait--;
            renderWheel(p.level(), w.pos, now);
            return;
        }
        if (w.mode == WheelMode.ORBIT) {
            if (now >= w.orbitUntil) {
                w.mode = WheelMode.RETURNING; data.setBuff("manyuexuntian", 0);
            } else if (w.orbitEntryTicks > 0) {
                Vec3 desired = w.orbitCenter.add(5, 0, 0);
                Vec3 step = desired.subtract(w.pos).scale(1.0 / w.orbitEntryTicks);
                Vec3 next = w.pos.add(step);
                if (FengQiangSkill.blocksProjectile(p.level(), w.pos, next)) {
                    w.velocity = Vec3.ZERO; w.windWallWait = 4;
                } else {
                    if (!blocked(p.level(), p, w.pos, next)) w.pos = next;
                    w.orbitEntryTicks--;
                }
                if (w.orbitEntryTicks == 0) w.angle = 0;
            } else if (w.detachedTicks > 0) {
                Vec3 next = w.pos.add(horizontal(w.velocity));
                if (FengQiangSkill.blocksProjectile(p.level(), w.pos, next)) {
                    w.velocity = Vec3.ZERO; w.windWallWait = 4;
                } else {
                    if (!blocked(p.level(), p, w.pos, next)) w.pos = next;
                    w.detachedTicks--;
                }
                if (w.detachedTicks <= 0) w.angle = Math.atan2(w.pos.z - w.orbitCenter.z, w.pos.x - w.orbitCenter.x);
                hitR(p, w, now, from, w.pos);
            } else {
                w.radius += (w.radiusTarget - w.radius) / 6.0;
                w.angle += w.clockwise * 1.1 / Math.max(2.5, w.radius);
                Vec3 desired = w.orbitCenter.add(Math.cos(w.angle) * w.radius, 0,
                        Math.sin(w.angle) * w.radius);
                w.velocity = desired.subtract(w.pos);
                if (FengQiangSkill.blocksProjectile(p.level(), w.pos, desired)) {
                    w.velocity = Vec3.ZERO; w.windWallWait = 4;
                } else if (!blocked(p.level(), p, w.pos, desired)) w.pos = desired;
                hitR(p, w, now, from, w.pos);
            }
        } else if (w.mode == WheelMode.RETURNING) {
            if (w.retryWait > 0) { w.retryWait--; renderWheel(p.level(), w.pos, now); return; }
            Vec3 target = p.getEyePosition(); Vec3 desired = target.subtract(w.pos);
            if (desired.lengthSqr() <= 1.6) { catchWheel(p, data, s, false); return; }
            Vec3 steered = horizontal(w.velocity).scale(.35).add(desired.normalize().scale(.65));
            w.velocity = steered.normalize().scale(1.25);
            Vec3 next = w.pos.add(w.velocity);
            if (FengQiangSkill.blocksProjectile(p.level(), w.pos, next)) {
                w.retryWait = 10; w.velocity = Vec3.ZERO;
            } else if (blocked(p.level(), p, w.pos, next)) {
                w.returnFailures++;
                if (w.returnFailures >= 3) { safeRecover(data, s); return; }
                w.retryWait = 20; w.velocity = Vec3.ZERO;
            } else w.pos = next;
            hitPath(p, w, from, w.pos, true, now);
        } else {
            if (w.hoverTicks > 0) {
                w.hoverTicks--; w.velocity = Vec3.ZERO;
                if (w.hoverTicks == 0 && w.travelLeft <= 0) w.mode = WheelMode.RETURNING;
            }
            else {
                double speed = w.boostTicks-- > 0 ? 1.25 : Math.max(.25, w.velocity.length());
                w.velocity = w.velocity.normalize().scale(Math.min(speed, w.travelLeft));
                Vec3 next = w.pos.add(w.velocity);
                if (FengQiangSkill.blocksProjectile(p.level(), w.pos, next)) {
                    w.velocity = Vec3.ZERO; w.travelLeft = 0; w.hoverTicks = 60;
                } else if (!MapSystem.contains(next.x, next.z) || blocked(p.level(), p, w.pos, next)) {
                    w.velocity = Vec3.ZERO; w.travelLeft = 0; w.hoverTicks = 60;
                }
                else { w.pos = next; w.distance += from.distanceTo(w.pos); w.travelLeft -= from.distanceTo(w.pos); }
            }
            hitPath(p, w, from, w.pos, false, now);
            if (w.travelLeft <= 0 && w.hoverTicks <= 0) w.hoverTicks = 60;
        }
        if (w.pos.distanceToSqr(p.position()) > 22 * 22) w.mode = WheelMode.RETURNING;
        updateWheelVisual(w, from, now);
        renderWheel(p.level(), w.pos, now);
    }

    private static boolean blocked(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS;
    }

    private static void safeRecover(PlayerData data, State s) {
        discardWheelVisual(s.wheel);
        s.wheel = null; data.setBuff("zhiyuehuixian_recast", 0); data.setBuff("manyuexuntian", 0);
    }

    private static void hitPath(ServerPlayer p, Wheel w, Vec3 from, Vec3 to, boolean returning, long now) {
        Set<UUID> hits = returning ? w.returnHits : w.outHits;
        float qDamage = returning ? 48 + (w.qLevel - 1) * 24 + ad(p) * 1.20F
                : 72 + (w.qLevel - 1) * 32 + ad(p) * 1.80F;
        for (LivingEntity target : targets(p, from, to, 1.0)) {
            if (target instanceof com.myriad.game.DefenseTowerEntity) continue;
            if (hits.add(target.getUUID())) DamageCalc.dealSkillDamage(p, target, qDamage, DamageCalc.Kind.PHYSICAL, 0);
            if (now < w.wDamageUntil && now >= w.wTargetReady.getOrDefault(target.getUUID(), 0L)) {
                float damage = 56 + (w.wLevel - 1) * 24 + ad(p) * 1.12F;
                DamageCalc.dealSkillDamage(p, target, damage, DamageCalc.Kind.PHYSICAL, 0);
                w.wTargetReady.put(target.getUUID(), now + 8);
            }
        }
    }

    private static void hitR(ServerPlayer p, Wheel w, long now, Vec3 from, Vec3 to) {
        for (LivingEntity target : targets(p, from, to, 1.15)) {
            if (target instanceof com.myriad.game.DefenseTowerEntity) continue;
            long ready = w.rHits.getOrDefault(target.getUUID(), 0L);
            if (now < ready) continue;
            w.rHits.put(target.getUUID(), now + 30);
            DamageCalc.dealSkillDamage(p, target, 88 + (w.rLevel - 1) * 40 + ad(p) * 1.0F,
                    DamageCalc.Kind.PHYSICAL, 3);
        }
    }

    private static Iterable<LivingEntity> targets(ServerPlayer p, Vec3 from, Vec3 to, double radius) {
        AABB box = new AABB(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z),
                Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).inflate(radius);
        return p.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && e.isAlive() && !ally(p, e)
                        && (e.getBoundingBox().inflate(radius).contains(from)
                        || e.getBoundingBox().inflate(radius).clip(from, to).isPresent()));
    }

    private static ItemDisplay spawnWheelVisual(ServerLevel level, Vec3 pos, Vec3 direction) {
        if (!(EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof ItemDisplay display)) {
            return null;
        }
        display.getSlot(0).set(new ItemStack(ModItems.WANYUE_RING.get()));
        display.setItemTransform(ItemDisplayContext.FIXED);
        display.setBillboardConstraints(BillboardConstraints.FIXED);
        display.setGlowColorOverride(0xFF8DDEFF);
        display.setPos(pos.x, pos.y, pos.z);
        orientWheel(display, direction);
        level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(display));
        return display;
    }

    /** 缺月轮贴图的本地 +X 轴定义为刃口前方，并逐 tick 对准真实位移方向。 */
    private static void orientWheel(ItemDisplay display, Vec3 direction) {
        if (display == null || direction.lengthSqr() < 1.0E-6) return;
        Vector3f target = new Vector3f((float)direction.x, (float)direction.y, (float)direction.z).normalize();
        Quaternionf aim = new Quaternionf().rotationTo(1.0F, 0.0F, 0.0F,
                target.x, target.y, target.z);
        display.setYRot(0); display.setXRot(0);
        display.setTransformation(new Transformation(new Vector3f(), aim,
                new Vector3f(1.35F, 1.35F, 1.35F), new Quaternionf()));
    }

    private static void updateWheelVisual(Wheel wheel, Vec3 previous, long now) {
        if (wheel.visual == null || !wheel.visual.isAlive()) return;
        Vec3 movement = wheel.pos.subtract(previous);
        if (movement.lengthSqr() > 1.0E-6) wheel.visualDirection = movement.normalize();
        else if (wheel.velocity.lengthSqr() > 1.0E-6) wheel.visualDirection = wheel.velocity.normalize();
        orientWheel(wheel.visual, wheel.visualDirection);
        wheel.visual.setPos(wheel.pos.x, wheel.pos.y, wheel.pos.z);
    }

    private static void discardWheelVisual(Wheel wheel) {
        if (wheel != null && wheel.visual != null && wheel.visual.isAlive()) wheel.visual.discard();
        if (wheel != null) wheel.visual = null;
    }

    private static void renderWheel(ServerLevel level, Vec3 center, long now) {
        if ((now & 1) != 0) return;
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12.0 + now * .18;
            level.sendParticles(i % 3 == 0 ? SILVER : MOON,
                    center.x + Math.cos(a) * .55, center.y + Math.sin(a) * .55, center.z,
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 2, .1, .1, .1, .01);
    }

    private static void catchWheel(ServerPlayer p, PlayerData data, State s, boolean chaseCatch) {
        Wheel wheel = s.wheel; if (wheel == null) return;
        if (wheel.distance >= 8) data.setCooldown("zhiyuehuixian",
                Math.max(0, data.getCooldown("zhiyuehuixian") - 40));
        s.empowered = true; s.empoweredChase = chaseCatch; data.setBuff("wanyue_empowered", 80);
        discardWheelVisual(wheel);
        s.wheel = null; data.setBuff("zhiyuehuixian_recast", 0); data.setBuff("manyuexuntian", 0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, .9F, 1.75F);
    }

    /** 普攻系统调用：月轮在外时从月轮位置发射。 */
    public static Vec3 basicOrigin(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.wheel != null ? s.wheel.pos : p.getEyePosition();
    }

    /** 普通离体月轮普攻为 90%；强化月华的独立倍率按命中目标消费。 */
    public static float consumeBasicMultiplier(ServerPlayer p, LivingEntity target) {
        State s = state(p); float value = s.wheel == null || s.wheel.mode == WheelMode.ORBIT ? 1F : .90F;
        Float empowered = EMPOWERED_HIT_MULTIPLIERS.remove(target.getUUID());
        if (empowered != null) value = empowered;
        return value;
    }

    public static boolean hasEmpoweredBasic(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.empowered;
    }

    /** 接轮强化普攻：沿面朝方向发射速度 0.5 格/tick、射程 20 格的穿透月华。 */
    public static boolean fireEmpoweredBasic(ServerPlayer p, float attackMultiplier) {
        State s = STATES.get(p.getUUID());
        if (s == null || !s.empowered) return false;
        Vec3 direction = horizontal(p.getLookAngle());
        if (direction.lengthSqr() < 1.0E-6) return false;
        PlayerData data = PlayerDataProvider.get(p);
        boolean chaseEmpowered = s.empoweredChase;
        float multiplier = 1.18F + Math.min(15, data == null ? 1 : data.getLevel()) * .01F
                + (chaseEmpowered ? .20F : 0.0F);
        Vec3 start = p.getEyePosition().add(direction.normalize().scale(.8));
        s.moonwaves.add(new Moonwave(start, direction.normalize().scale(chaseEmpowered ? .75 : .5),
                multiplier, Math.clamp(attackMultiplier, .6F, 1.0F), chaseEmpowered));
        s.empowered = false; s.empoweredChase = false;
        if (data != null) data.setBuff("wanyue_empowered", 0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, .85F, 1.75F);
        renderMoonwaveBurst(p.level(), start, chaseEmpowered);
        return true;
    }

    private static void tickMoonwaves(ServerPlayer p, State s) {
        if (s.moonwaves.isEmpty()) return;
        long now = p.level().getGameTime();
        s.moonwaves.removeIf(wave -> {
            Vec3 from = wave.pos;
            Vec3 to = from.add(wave.velocity);
            if (FengQiangSkill.blocksProjectile(p.level(), from, to)) {
                renderMoonwaveBurst(p.level(), from, wave.golden);
                return true;
            }
            wave.pos = to;
            wave.distance += from.distanceTo(to);
            for (LivingEntity target : targets(p, from, to, 1.5)) {
                if (!wave.hits.add(target.getUUID())) continue;
                float penetrationScale = Math.max(.33F, 1.0F - (wave.hits.size() - 1) * .33F);
                EMPOWERED_HIT_MULTIPLIERS.put(target.getUUID(), wave.multiplier * penetrationScale);
                DamageCalc.markBasicAttack(p, target, wave.attackMultiplier);
                target.hurt(target.damageSources().playerAttack(p), 1.0F);
                DamageCalc.clearBasicAttack(p, target);
                EMPOWERED_HIT_MULTIPLIERS.remove(target.getUUID());
            }
            renderMoonwave(p.level(), wave.pos, now, wave.golden);
            return wave.distance >= 20.0;
        });
    }

    private static void renderMoonwaveBurst(ServerLevel level, Vec3 center, boolean golden) {
        for (int i = 0; i < 24; i++) renderMoonwaveParticle(level, center, i, 24, 1.05, golden);
    }

    private static void renderMoonwave(ServerLevel level, Vec3 center, long now, boolean golden) {
        for (int i = 0; i < 16; i++) renderMoonwaveParticle(level, center,
                i + (int)(now & 1), 16, 1.05, golden);
        level.sendParticles(iColor(now), center.x, center.y, center.z, 2, .05, .05, .05, .01);
        if (golden) level.sendParticles(GOLD, center.x, center.y, center.z, 2, .12, .02, .12, .01);
    }

    /** 强化月华圆环始终横置于世界 XZ 平面，沿飞行方向平移但不随朝向竖起。 */
    private static void renderMoonwaveParticle(ServerLevel level, Vec3 center,
            int index, int count, double radius, boolean golden) {
        double angle = Math.PI * 2.0 * index / count;
        Vec3 point = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        level.sendParticles(golden && index % 4 == 0 ? GOLD : index % 2 == 0 ? MOON : SILVER,
                point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }

    private static DustParticleOptions iColor(long now) { return (now & 1) == 0 ? MOON : SILVER; }

    public static boolean wheelOut(ServerPlayer p) { State s = STATES.get(p.getUUID()); return s != null && s.wheel != null; }
    public static boolean canBasicAttack(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null || s.wheel == null || s.wheel.pos.distanceToSqr(p.position()) <= 22 * 22;
    }

    private static void recoverCharges(ServerPlayer p, PlayerData data, State s, WanyueSkills skill, long now) {
        while (s.wCharges < 2 && !s.wReady.isEmpty() && now >= s.wReady.peekFirst()) {
            s.wReady.removeFirst(); s.wCharges++;
        }
        syncCharges(data, s, skill, now);
    }

    private static void syncCharges(PlayerData data, State s, WanyueSkills skill, long now) {
        data.setBuff(W_CHARGE, s.wCharges);
        int remaining = s.wReady.isEmpty() ? 0 : (int)Math.max(0, s.wReady.peekFirst() - now);
        data.setBuff(W_REMAINING, remaining);
        data.setBuff(W_TOTAL, remaining <= 0 ? 0 : Math.max(1, s.wRechargeTotal));
    }

    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    private static int reducedCooldown(ServerPlayer p, int base) {
        double cdr = Mth.clamp(p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION), 0, 60);
        return Math.max(1, (int)(base * (1 - cdr / 100.0)));
    }
    private static float ad(ServerPlayer p) { return (float)p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK); }
    private static Vec3 horizontal(Vec3 value) {
        Vec3 h = new Vec3(value.x, 0, value.z); return h.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : h.normalize();
    }
    private static Vec3 requestedOrLook(ServerPlayer p) { return REQUESTED.getOrDefault(p.getUUID(), horizontal(p.getLookAngle())); }
    private static Vec3 rotate(Vec3 v, double a) { double c = Math.cos(a), s = Math.sin(a); return new Vec3(v.x*c-v.z*s, 0, v.x*s+v.z*c); }
    private static double segmentDistanceSqr(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a); double len = ab.lengthSqr(); if (len < 1e-8) return p.distanceToSqr(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / len, 0, 1); return p.distanceToSqr(a.add(ab.scale(t)));
    }
    private static boolean ally(ServerPlayer p, LivingEntity target) {
        GameManager manager = GameManager.get(); if (manager == null) return false;
        Faction a = manager.factionOf(p), b = LaneSystem.factionOf(target); return a != null && a == b;
    }

    public static void setRequestedDirection(ServerPlayer p, float x, float z) {
        Vec3 d = new Vec3(x, 0, z); if (d.lengthSqr() > 1e-6) REQUESTED.put(p.getUUID(), d.normalize());
    }
    public static void clearRequestedDirection(ServerPlayer p) { REQUESTED.remove(p.getUUID()); }
    public static void clearFor(UUID id) {
        State state = STATES.remove(id);
        if (state != null) discardWheelVisual(state.wheel);
        REQUESTED.remove(id);
    }

    private static final class State {
        Wheel wheel; Dash dash; int wCharges = 2, wRechargeTotal = 180; final ArrayDeque<Long> wReady = new ArrayDeque<>();
        boolean empowered, empoweredChase; final List<Moonwave> moonwaves = new ArrayList<>();
    }
    private static final class Dash {
        final Vec3 step; int ticks; final int level;
        Dash(Vec3 step, int ticks, int level) { this.step = step; this.ticks = ticks; this.level = level; }
    }
    private static final class Wheel {
        Vec3 pos, velocity, orbitCenter = Vec3.ZERO; WheelMode mode; final int qLevel;
        ItemDisplay visual; Vec3 visualDirection = new Vec3(1, 0, 0);
        double travelLeft, distance, radius = 5, radiusTarget = 5, angle, clockwise = 1;
        int hoverTicks, boostTicks, detachedTicks, orbitEntryTicks, retryWait, returnFailures, windWallWait,
                wLevel = 1, rLevel = 1;
        final long createdAt; long expiresAt, orbitUntil, wDamageUntil;
        final Set<UUID> outHits = new HashSet<>(), returnHits = new HashSet<>();
        final Map<UUID, Long> rHits = new HashMap<>(), wTargetReady = new HashMap<>();
        Wheel(Vec3 pos, Vec3 velocity, WheelMode mode, int qLevel, long now) {
            this.pos = pos; this.velocity = velocity; this.mode = mode; this.qLevel = qLevel;
            this.createdAt = now; this.expiresAt = now + 140;
        }
    }
    private static final class Moonwave {
        Vec3 pos; final Vec3 velocity; final float multiplier, attackMultiplier; final boolean golden; double distance;
        final Set<UUID> hits = new HashSet<>();
        Moonwave(Vec3 pos, Vec3 velocity, float multiplier, float attackMultiplier, boolean golden) {
            this.pos = pos; this.velocity = velocity; this.multiplier = multiplier;
            this.attackMultiplier = attackMultiplier; this.golden = golden;
        }
    }
}
