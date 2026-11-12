package com.myriad.character.skills.feiyu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.MapSystem;
import com.myriad.game.VisionSystem;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.network.NetworkSetup.ParabolicMotionPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 惊鸿·绯羽：服务器权威舞阶、二段连舞、精准折锋、踏虹坠月与五拍凤舞。 */
public final class FeiYuSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    private enum MoveKind { Q1, Q2, W2, E1, E2, R, PHOENIX }
    private enum Action { BASIC, AIR_BASIC, Q1, Q2, W1, W2, E1, E2 }

    public static final String STAGE_COUNTER = "feiyu_stage";
    public static final String SCORE_TIME = "feiyu_score_time";
    public static final String R_BEAT_COUNTER = "feiyu_r_beat";
    public static final String R_CHAPTER_COUNTER = "feiyu_r_chapter";
    public static final String R_PERFECT_COUNTER = "feiyu_r_perfect";
    public static final String R_DIRECTION_COUNTER = "feiyu_r_direction";
    public static final String R_DIRECTIONS_COUNTER = "feiyu_r_directions";
    public static final String R_WINDOW_TIME = "feiyu_r_window";
    public static final String RECOVERY_TIME = "feiyu_recovery";
    private static final int RAINBOW_TICKS = 18;
    private static final int RAINBOW_RECAST_UNLOCK_TICKS = 4;
    private static final int R_BEAT_LINK_TICKS = 30;
    private static final int R_PERFECT_WINDOW_TICKS = 12;
    private static final int R_SEQUENCE_MAX_TICKS = R_BEAT_LINK_TICKS * 5 + 20;
    private static final Identifier DANCE_SPEED = Identifier.fromNamespaceAndPath("myriad", "feiyu_dance_speed");
    private static final Identifier W_SLOW = Identifier.fromNamespaceAndPath("myriad", "feiyu_round_dance_slow");
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(0xF0445E, 1.05F);
    private static final DustParticleOptions CYAN_GOLD = new DustParticleOptions(0x58E6D2, 1.0F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD56A, 1.12F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xECFAFF, 1.05F);
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Vec3> REQUESTED = new HashMap<>();
    private final Type type;

    public FeiYuSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "feiyu_passive";
            case Q -> "liuxiashuangyanfan";
            case W -> "yuanguangzhefengwu";
            case E -> "tahongzhuiyue";
            case R -> "jiuxiaofengwuliancheng";
        }, switch (type) {
            case PASSIVE -> 0; case Q -> 160; case W -> 300; case E -> 340; case R -> 2200;
        }, switch (type) {
            case PASSIVE -> 0.0F; case Q -> 64.8F; case W -> 108.0F; case E -> 129.6F; case R -> 288.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getCooldownTicks(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case Q -> Math.max(130, 160 - (lv - 1) * 10);
            case W -> Math.max(240, 300 - (lv - 1) * 20);
            case E -> Math.max(280, 340 - (lv - 1) * 20);
            case R -> Math.max(1400, 2200 - (lv - 1) * 400);
            default -> 0;
        };
    }

    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "不同招式命中提升舞阶；凰阶解锁大招，每阶提供1%移速";
            case Q -> "掠影 " + (17 + (lv - 1) * 8) + "+50%物攻；命中后燕返 " + (12 + (lv - 1) * 8) + "+38%物攻";
            case W -> "圆光 " + (21 + (lv - 1) * 7) + "+45%物攻；精准格挡后折锋 " + (16 + (lv - 1) * 7) + "+42%物攻";
            case E -> "踏虹 " + (15 + (lv - 1) * 7) + "+32%物攻；坠月 " + (24 + (lv - 1) * 10) + "+50%物攻";
            case R -> "每拍有1.5秒衔接时间，最后0.60秒输入为完美拍；五拍累计3次完美触发终结；每拍 "
                    + (11 + (lv - 1) * 4) + "+20%物攻；终结 " + (60 + (lv - 1) * 28) + "+60%物攻";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) { }

    public static void handleCast(ServerPlayer p, PlayerData data, int slot, Skill raw, boolean noCd, boolean noMana) {
        if (!(raw instanceof FeiYuSkills skill)) return;
        State s = state(p); long now = p.level().getGameTime();
        boolean recast = slot == CharacterType.SLOT_Q && s.qTarget != null && now < s.qUntil
                || slot == CharacterType.SLOT_W && s.wReturnUntil > now
                || slot == CharacterType.SLOT_E && s.eRecastUntil > now
                || slot == CharacterType.SLOT_R && s.ultimate != null;
        if (s.finisher != null) {
            p.sendSystemMessage(Component.translatable("message.myriad.feiyu_dancing"));
            return;
        }
        if (s.ultimate != null && slot != CharacterType.SLOT_R) {
            p.sendSystemMessage(Component.translatable("message.myriad.feiyu_dancing")); return;
        }
        if (!recast && !noCd && data.getCooldown(skill.getId()) > 0) {
            p.sendSystemMessage(Component.translatable("message.myriad.skill_cd", (data.getCooldown(skill.getId()) + 19) / 20)); return;
        }
        if (slot == CharacterType.SLOT_R && s.ultimate == null && s.stage < 6) {
            p.sendSystemMessage(Component.translatable("message.myriad.feiyu_need_phoenix")); return;
        }
        if (!recast && !noMana && data.getMana() < skill.getManaCost()) {
            p.sendSystemMessage(Component.translatable("message.myriad.not_enough_mana")); return;
        }
        if (!recast && skill.type == Type.E) {
            s.preparedRainbow = safeRainbow(p, requestedOrLook(p));
            if (s.preparedRainbow == null) {
                p.sendSystemMessage(Component.translatable("message.myriad.feiyu_no_safe_path"));
                return;
            }
        }
        if (s.recoveryUntil > now && s.lastCastSlot >= 0 && s.lastCastSlot != slot) {
            // 只有通过全部施法校验的不同技能，才能由服务端确认取消收势。
            s.recoveryUntil = 0;
            data.setBuff(RECOVERY_TIME, 0);
        }
        if (!recast && !noMana) data.spendMana(skill.getManaCost());
        int lv = Math.max(1, data.skillLevel(skill.getId()));
        if (!recast && skill.type == Type.Q) {
            s.qPendingCooldown = noCd ? 0 : reducedCooldown(p, skill.getCooldownTicks(lv));
        }
        switch (skill.type) {
            case Q -> castQ(p, data, s, lv, now);
            case W -> castW(p, data, s, lv, now);
            case E -> castE(p, data, s, lv, now);
            case R -> castR(p, data, s, lv, now);
            default -> { }
        }
        s.lastCastSlot = slot;
        if (!recast && !noCd && skill.type != Type.Q) {
            data.setCooldown(skill.getId(), reducedCooldown(p, skill.getCooldownTicks(lv)));
        }
        
        com.myriad.character.SkillPresentation.cast(p, skill);
        PlayerDataProvider.syncToClient(p);
    }

    private static void castQ(ServerPlayer p, PlayerData data, State s, int lv, long now) {
        if (s.qTarget != null && now < s.qUntil) {
            Entity raw = p.level().getEntity(s.qTarget); s.qTarget = null; s.qUntil = 0; data.setBuff("liuxiashuangyanfan_recast", 0);
            if (!(raw instanceof ServerPlayer target) || !com.myriad.combat.SkillTargets.alive(target)) {
                commitQCooldown(data, s); return;
            }
            if (!VisionSystem.canSee(p, target)) { commitQCooldown(data, s); return; }
            Vec3 radial = horizontal(p.position().subtract(target.position()));
            Vec3 input = requestedOrLook(p), left = new Vec3(p.getLookAngle().z, 0, -p.getLookAngle().x);
            int side = input.dot(left) >= 0 ? 1 : -1;
            s.move = Move.arc(MoveKind.Q2, lv, target.getUUID(), Math.atan2(radial.z, radial.x), side * Math.toRadians(100), now, 6);
            s.move.arcCenter = target.position();
            return;
        }
        boolean fromRainbow = s.move != null && s.move.kind == MoveKind.E1;
        double inheritedY = fromRainbow ? p.getDeltaMovement().y : 0;
        Vec3 direction = requestedOrLook(p);
        if (fromRainbow) {
            s.eSecondUnlockAt = 0;
            s.eRecastUntil = now + 40;
            data.setBuff("tahongzhuiyue_recast", 40);
        }
        startLinear(p, s, MoveKind.Q1, direction, .9, 5, lv, CRIMSON);
        if (fromRainbow) {
            s.move.direction = new Vec3(direction.x, inheritedY, direction.z);
            p.setDeltaMovement(direction.x * .9, inheritedY * .9, direction.z * .9);
            PacketDistributor.sendToPlayer(p, new DashPayload(
                    (float)direction.x, (float)inheritedY, (float)direction.z, 5));
        }
    }

    private static void castW(ServerPlayer p, PlayerData data, State s, int lv, long now) {
        if (s.wReturnUntil > now) {
            s.wReturnUntil = 0; data.setBuff("yuanguangzhefengwu_recast", 0);
            startLinear(p, s, MoveKind.W2, requestedOrLook(p), 1.0, 3, lv, GOLD); return;
        }
        s.wUntil = now + 12; s.parryUntil = now + 4; s.parryUsed = false;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(W_SLOW);
            speed.addTransientModifier(new AttributeModifier(W_SLOW, -.20,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        hitRadius(p, 3, 84 + (lv - 1) * 28, 1.80F, Action.W1, false);
        ring(p.level(), p.position().add(0, .9, 0), 3, WHITE, 26);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .9F, 1.45F);
        s.wRecoveryPending = true;
    }

    private static void castE(ServerPlayer p, PlayerData data, State s, int lv, long now) {
        if (s.eRecastUntil > now) {
            s.eSecondUnlockAt = 0; s.eRecastUntil = 0; data.setBuff("tahongzhuiyue_recast", 0);
            Vec3 landing = moonLanding(p);
            boolean precise = landing != null;
            if (landing == null) landing = groundBelow(p, p.position());
            if (landing == null) return;
            s.move = Move.linear(MoveKind.E2, Vec3.ZERO, 1.0, 24, lv);
            s.move.landing = landing;
            s.move.cleanLanding = precise;
            return;
        }
        Trajectory trajectory = s.preparedRainbow != null ? s.preparedRainbow : safeRainbow(p, requestedOrLook(p));
        s.preparedRainbow = null;
        if (trajectory == null) return;
        Vec3 delta = trajectory.delta;
        s.eSecondUnlockAt = now + RAINBOW_RECAST_UNLOCK_TICKS;
        s.move = Move.parabola(lv, delta, trajectory.height, now, RAINBOW_TICKS);
        PacketDistributor.sendToPlayer(p, new ParabolicMotionPayload((float)delta.x, 0F, (float)delta.z,
                (float)trajectory.height, RAINBOW_TICKS));
        ribbonStep(p.level(), p.position(), requestedOrLook(p));
    }

    private static void castR(ServerPlayer p, PlayerData data, State s, int lv, long now) {
        if (s.ultimate == null) {
            setStage(p, data, s, 0, 0); s.ultimate = new Ultimate(lv, now, p.position());
            p.level().playSound(null, p.blockPosition(), SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.PLAYERS, 1F, .8F);
            data.setBuff(R_BEAT_COUNTER, 0); data.setBuff(R_CHAPTER_COUNTER, 0); data.setBuff(R_PERFECT_COUNTER, 0);
            data.setBuff(R_DIRECTION_COUNTER, 0); data.setBuff(R_DIRECTIONS_COUNTER, 0);
            data.setBuff(R_WINDOW_TIME, R_BEAT_LINK_TICKS); return;
        }
        Ultimate u = s.ultimate;
        long remaining = u.centerAt - now;
        if (s.move != null || remaining < 0) { endUltimate(p, data, s, false); return; }
        Vec3 dir = requestedOrLook(p); int sector = directionSector(dir); boolean repeated = sector == u.lastSector;
        u.pendingSector = sector;
        u.pendingPerfect = remaining <= R_PERFECT_WINDOW_TICKS;
        u.pendingRepeated = repeated;
        data.setBuff(R_DIRECTION_COUNTER, sector + 1);
        startLinear(p, s, MoveKind.R, dir, .875, 4, u.level, rColor(u.beats));
    }

    public static void tick(ServerPlayer p, PlayerData data) {
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.FEIYU) return;
        State s = state(p); ServerLevel level = p.level(); long now = level.getGameTime();
        boolean controlled = hardControlled(p);
        if (controlled && s.finisher != null) cancelPhoenixFinisher(p, s);
        if (controlled && !s.wasHardControlled) {
            setStage(p, data, s, Math.max(0, s.stage - 2), now + 12);
            if (s.ultimate != null) endUltimate(p, data, s, false);
        }
        s.wasHardControlled = controlled;
        tickStage(p, data, s, now); tickWindows(data, s, now); tickMove(p, data, s, level, now); tickW(p, data, s, level, now); tickUltimate(p, data, s, now);
        if (s.eRecastUntil > now && now % 2 == 0) {
            Vec3 landing = moonLanding(p);
            Vec3 preview = landing != null ? landing : p.position();
            DustParticleOptions tint = landing != null ? CYAN_GOLD : CRIMSON;
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12;
                Vec3 point = preview.add(Math.cos(angle) * 3, .08, Math.sin(angle) * 3);
                level.sendParticles(p, tint, false, false, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
            level.sendParticles(p, WHITE, false, false, preview.x, preview.y + .1, preview.z, 1, 0, 0, 0, 0);
        }
        if (s.recoveryUntil > now && now % 2 == 0) squareRing(level, p.position().add(0, .08, 0), .75, GOLD);
    }

    private static void tickStage(ServerPlayer p, PlayerData data, State s, long now) {
        if (s.stage > 0 && now >= s.scoreUntil) {
            if (now >= s.nextDecay) { setStage(p, data, s, s.stage - 1, now + 12); s.nextDecay = now + 12; }
        }
        data.setBuff(SCORE_TIME, s.stage <= 0 ? 0 : (int)Math.max(1, s.scoreUntil - now));
    }

    private static void tickWindows(PlayerData data, State s, long now) {
        if (s.qTarget != null && now >= s.qUntil) {
            s.qTarget = null; data.setBuff("liuxiashuangyanfan_recast", 0); commitQCooldown(data, s);
        }
        if (s.wReturnUntil > 0 && now >= s.wReturnUntil) { s.wReturnUntil = 0; data.setBuff("yuanguangzhefengwu_recast", 0); }
        if (s.eRecastUntil > 0 && now >= s.eRecastUntil) { s.eRecastUntil = 0; data.setBuff("tahongzhuiyue_recast", 0); }
    }

    private static void tickMove(ServerPlayer p, PlayerData data, State s, ServerLevel level, long now) {
        Move m = s.move; if (m == null) return;
        if (hardControlled(p)) {
            s.move = null;
            if (m.kind == MoveKind.Q1 || m.kind == MoveKind.Q2) commitQCooldown(data, s);
            PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0));
            p.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (m.kind == MoveKind.E2 && m.landing != null) {
            Vec3 remaining = m.landing.subtract(p.position());
            if (remaining.length() < .18) { finishMove(p, data, s, m); return; }
            m.direction = remaining.scale(Math.min(1, 1.25 / remaining.length()));
            PacketDistributor.sendToPlayer(p, new DashPayload((float)m.direction.x,
                    (float)m.direction.y, (float)m.direction.z, 2));
        }
        ServerPlayer q2Primary = null;
        boolean q2ShouldHit = false;
        if (m.kind == MoveKind.Q2) {
            Entity raw = level.getEntity(m.target); if (!(raw instanceof ServerPlayer target) || !com.myriad.combat.SkillTargets.alive(target)
                    || !VisionSystem.canSee(p, target)) { finishMove(p, data, s, m); return; }
            Vec3 targetCenter = target.position();
            Vec3 centerStep = targetCenter.subtract(m.arcCenter);
            if (centerStep.length() > .35) centerStep = centerStep.normalize().scale(.35);
            m.arcCenter = m.arcCenter.add(centerStep);
            double progress = (m.totalTicks - m.ticks + 1) / (double)m.totalTicks;
            double angle = m.startAngle + m.deltaAngle * progress;
            Vec3 desired = m.arcCenter.add(Math.cos(angle) * 2.8, 0, Math.sin(angle) * 2.8);
            Vec3 wanted = new Vec3(desired.x - p.getX(), 0, desired.z - p.getZ());
            if (wanted.length() > .92) wanted = wanted.normalize().scale(.92);
            // 保留短步的真实速度，不再归一化成每 tick 满速；同时轻度混合上一帧，避免折线抖动。
            if (m.direction == null || m.direction.lengthSqr() < 1.0E-6) m.direction = wanted;
            else m.direction = m.direction.scale(.28).add(wanted.scale(.72));
            if (m.direction.length() > .92) m.direction = m.direction.normalize().scale(.92);
            PacketDistributor.sendToPlayer(p, new DashPayload(
                    (float)m.direction.x, 0, (float)m.direction.z, 2));
            // 燕返围绕首段命中的英雄展开；只要没有在前半程撞墙或丢失视野，中段稳定出伤。
            q2Primary = target;
            q2ShouldHit = !m.primaryHitDone && progress >= .5;
        } else if (m.kind == MoveKind.E1 || m.kind == MoveKind.PHOENIX) {
            if (s.eSecondUnlockAt > 0 && now >= s.eSecondUnlockAt) {
                s.eSecondUnlockAt = 0;
                s.eRecastUntil = m.started + m.totalTicks + 40;
                data.setBuff("tahongzhuiyue_recast", (int)Math.max(1, s.eRecastUntil - now));
                PlayerDataProvider.syncToClient(p);
            }
            int age = (int)(now - m.started), total = Math.max(1, m.totalTicks);
            double t0 = age / (double)total, t1 = (age + 1) / (double)total;
            double y = m.delta.y / total
                    + 4 * m.arcHeight * (t1 * (1 - t1) - t0 * (1 - t0));
            m.direction = new Vec3(m.delta.x / total, y, m.delta.z / total);
            if (m.kind == MoveKind.PHOENIX && s.finisher != null) {
                phoenixCharge(level, s.finisher.center, p.position().add(0, 1, 0), age);
            }
        }
        Vec3 step = m.kind == MoveKind.E1 || m.kind == MoveKind.PHOENIX
                ? m.direction : m.direction.scale(m.speed);
        if (!MapSystem.contains(p.getX() + step.x, p.getZ() + step.z)
                || !level.noCollision(p, p.getBoundingBox().move(step))) {
            m.cleanLanding = false;
            finishMove(p, data, s, m);
            return;
        }
        if (q2ShouldHit && q2Primary != null) {
            m.primaryHitDone = true;
            m.hit.add(q2Primary.getUUID());
            m.heroHit.add(q2Primary.getUUID());
            DamageCalc.dealSkillDamage(p, q2Primary,
                    48 + (m.level - 1) * 32 + ad(p) * 1.52F, Kind.PHYSICAL, 0);
            record(p, data, s, q2Primary, Action.Q2, 0);
            FeiYuEffects.burst(level, q2Primary.getBoundingBox().getCenter(), 10, .45,
                    1F, .22F, .36F);
        }
        p.setDeltaMovement(step.x, step.y, step.z); m.ticks--;
        trail(level, p.position().add(0, .9, 0), m.color());
        AABB moveHitBox = m.kind == MoveKind.E1
                ? p.getBoundingBox().inflate(2.6, 3.5, 2.6)
                : p.getBoundingBox().inflate(2.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                moveHitBox, target -> target != p && com.myriad.combat.SkillTargets.alive(target) && !ally(p, target))) {
            boolean inHitRadius;
            if (m.kind == MoveKind.E1) {
                double dx = target.getX() - p.getX(), dz = target.getZ() - p.getZ();
                inHitRadius = dx * dx + dz * dz <= 2.6 * 2.6
                        && Math.abs(target.getBoundingBox().getCenter().y
                        - p.getBoundingBox().getCenter().y) <= 3.5;
            } else inHitRadius = target.distanceToSqr(p) <= 4.0;
            if (m.hit.contains(target.getUUID()) || !inHitRadius) continue;
            if (m.kind == MoveKind.PHOENIX) continue;
            if (target instanceof ServerPlayer hero
                    && (m.kind == MoveKind.Q1 || m.kind == MoveKind.Q2 || m.kind == MoveKind.R)
                    && !VisionSystem.canSee(p, hero)) continue;
            if (m.kind == MoveKind.E2) continue;
            m.hit.add(target.getUUID());
            if (target instanceof ServerPlayer) m.heroHit.add(target.getUUID());
            float ad = ad(p); float damage = switch (m.kind) {
                case Q1 -> 68 + (m.level - 1) * 32 + ad * 2.0F;
                case Q2 -> 48 + (m.level - 1) * 32 + ad * 1.52F;
                case W2 -> 64 + (m.level - 1) * 28 + ad * 1.68F;
                case E1 -> 60 + (m.level - 1) * 28 + ad * 1.28F;
                case E2 -> 96 + (m.level - 1) * 40 + ad * 2.0F;
                case R -> 44 + (m.level - 1) * 16 + ad * .80F;
                case PHOENIX -> 0;
            };
            if (m.kind == MoveKind.E2 && target.distanceToSqr(p) <= 1.44) damage *= 1.2F;
            DamageCalc.dealSkillDamage(p, target, damage, Kind.PHYSICAL, switch (m.kind) { case Q1, Q2 -> 0; case W2 -> 1; case E1, E2 -> 2; default -> 3; });
            if (m.kind == MoveKind.E1) {
                FeiYuEffects.burst(level, target.getBoundingBox().getCenter(), 7, .32,
                        .30F, .92F, .88F);
            }
            if (m.kind != MoveKind.R) {
                record(p, data, s, target, actionFor(m.kind), m.kind == MoveKind.W2 ? 1 : 0);
            }
        }
        if (m.ticks <= 0) finishMove(p, data, s, m);
    }

    private static void finishMove(ServerPlayer p, PlayerData data, State s, Move m) {
        s.move = null; PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0));
        p.setDeltaMovement(0, p.getDeltaMovement().y, 0); long now = p.level().getGameTime();
        if (m.kind == MoveKind.Q1 && !m.heroHit.isEmpty()) {
            s.qTarget = m.heroHit.iterator().next(); s.qUntil = now + 30; data.setBuff("liuxiashuangyanfan_recast", 30);
        } else if (m.kind == MoveKind.Q1) {
            commitQCooldown(data, s);
        } else if (m.kind == MoveKind.Q2) {
            commitQCooldown(data, s);
        } else if (m.kind == MoveKind.E1) {
            s.eSecondUnlockAt = 0;
            s.eRecastUntil = now + 40; data.setBuff("tahongzhuiyue_recast", 40); recovery(p, data, s, now);
        } else if (m.kind == MoveKind.E2) {
            Vec3 ground = groundBelow(p, p.position());
            if (ground == null || Math.abs(ground.y - p.getY()) > .35) {
                recovery(p, data, s, now); return;
            }
            p.setDeltaMovement(Vec3.ZERO);
            if (m.landing == null || m.landing.distanceTo(p.position()) > .3) m.cleanLanding = false;
            e2Impact(p, data, s, m);
            if (!m.heroHit.isEmpty()) data.setCooldown("liuxiashuangyanfan", Math.max(0, data.getCooldown("liuxiashuangyanfan") - 60));
            if (m.heroHit.size() >= 2) bonusStage(p, data, s, 1);
            moonImpact(p.level(), p.position()); recovery(p, data, s, now);
        } else if (m.kind == MoveKind.R) {
            finishBeat(p, data, s, m, now);
        } else if (m.kind == MoveKind.PHOENIX) {
            PhoenixFinisher finisher = s.finisher;
            s.finisher = null;
            if (finisher != null) phoenixFall(p, p.position(), finisher.level);
        } else recovery(p, data, s, now);
    }

    private static void tickW(ServerPlayer p, PlayerData data, State s, ServerLevel level, long now) {
        if (s.wUntil <= now) {
            var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(W_SLOW);
            if (s.wRecoveryPending) { s.wRecoveryPending = false; recovery(p, data, s, now); }
            return;
        }
        if (s.parryLockUntil >= now) p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
        if ((now & 1) == 0) ring(level, p.position().add(0, .9, 0), 3, WHITE, 14);
        if (!s.parryUsed && now <= s.parryUntil) {
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                    p.getBoundingBox().inflate(3), Entity::isAlive)) {
                Entity owner = projectile.getOwner();
                if (owner == p || owner instanceof ServerPlayer sp && ally(p, sp)) continue;
                projectile.discard();
                activateParry(p, s, now);
                break;
            }
        }
    }

    private static void tickUltimate(ServerPlayer p, PlayerData data, State s, long now) {
        Ultimate u = s.ultimate; if (u == null || s.move != null) return;
        if (hardControlled(p) || now > u.centerAt || now > u.started + R_SEQUENCE_MAX_TICKS) {
            endUltimate(p, data, s, false);
            return;
        }
        data.setBuff(R_WINDOW_TIME, (int)Math.max(1, u.centerAt - now));
    }

    private static void finishBeat(ServerPlayer p, PlayerData data, State s, Move m, long now) {
        Ultimate u = s.ultimate; if (u == null) return;
        if (m.heroHit.isEmpty()) { endUltimate(p, data, s, false); return; }
        u.beats++; u.points.add(p.position()); if (!u.pendingRepeated) { u.chapters++; u.lastSector = u.pendingSector; }
        if (u.pendingPerfect) u.perfect++;
        data.setBuff(R_BEAT_COUNTER, u.beats); data.setBuff(R_CHAPTER_COUNTER, u.chapters); data.setBuff(R_PERFECT_COUNTER, u.perfect);
        u.usedDirections |= 1 << u.pendingSector;
        data.setBuff(R_DIRECTION_COUNTER, u.pendingSector + 1); data.setBuff(R_DIRECTIONS_COUNTER, u.usedDirections);
        featherBurst(p.level(), p.position().add(0, 1, 0), u.beats);
        if (u.beats >= 5) { endUltimate(p, data, s, u.perfect >= 3); return; }
        u.centerAt = now + R_BEAT_LINK_TICKS;
        data.setBuff(R_WINDOW_TIME, R_BEAT_LINK_TICKS); PlayerDataProvider.syncToClient(p);
    }

    private static void endUltimate(ServerPlayer p, PlayerData data, State s, boolean finish) {
        Ultimate u = s.ultimate; s.ultimate = null; s.move = null; PacketDistributor.sendToPlayer(p, new DashPayload(0,0,0,0));
        data.setBuff(R_BEAT_COUNTER, 0); data.setBuff(R_CHAPTER_COUNTER, 0); data.setBuff(R_PERFECT_COUNTER, 0);
        data.setBuff(R_DIRECTION_COUNTER, 0); data.setBuff(R_DIRECTIONS_COUNTER, 0); data.setBuff(R_WINDOW_TIME, 0);
        if (finish && u != null) {
            Vec3 center = u.points.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / Math.max(1, u.points.size()));
            beginPhoenixFinisher(p, s, center, u.level); data.setCooldown("liuxiashuangyanfan", 0);
        }
        PlayerDataProvider.syncToClient(p);
    }

    public static void onBasicAttack(ServerPlayer p, LivingEntity target) {
        PlayerData data = PlayerDataProvider.get(p); if (data == null || !com.myriad.combat.SkillTargets.alive(target) || ally(p, target)) return;
        State s = state(p); Action action = s.move != null && s.move.kind == MoveKind.E1 ? Action.AIR_BASIC : Action.BASIC;
        record(p, data, s, target, action, action == Action.AIR_BASIC && target instanceof ServerPlayer ? 1 : 0);
    }

    public static void onIncomingDamage(ServerPlayer victim, LivingIncomingDamageEvent event) {
        State s = STATES.get(victim.getUUID()); if (s == null) return; long now = victim.level().getGameTime();
        if (s.ultimate != null) event.getContainer().setNewDamage(event.getAmount() * .70F);
        else if (s.finisher != null) event.getContainer().setNewDamage(event.getAmount() * .60F);
        if (!s.parryUsed && now <= s.parryUntil && event.getAmount() > 0 && event.getSource().getEntity() instanceof ServerPlayer) {
            event.getContainer().setNewDamage(event.getAmount() * .30F);
            activateParry(victim, s, now);
        }
    }

    private static void activateParry(ServerPlayer victim, State s, long now) {
        if (s.parryUsed) return;
        s.parryUsed = true;
        s.parryLockUntil = now + 2;
        s.wReturnUntil = now + 16;
        PlayerData data = PlayerDataProvider.get(victim);
        data.setBuff("yuanguangzhefengwu_recast", 16);
        parryBurst(victim.level(), victim.position().add(0, 1, 0));
        victim.level().playSound(null, victim.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, .75F, 1.75F);
        PlayerDataProvider.syncToClient(victim);
    }

    /** W 精准折锋成功后的短暂定身同时免除该次击退。 */
    public static boolean cancelParryKnockback(ServerPlayer victim) {
        State s = STATES.get(victim.getUUID());
        return s != null && victim.level().getGameTime() <= s.parryLockUntil;
    }

    /** 与风墙自定义飞行物线段检测并列：命中 W 精准窗即消耗本次格挡。 */
    public static boolean blocksProjectile(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
        long now = level.getGameTime();
        Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        for (ServerPlayer defender : level.players()) {
            State s = STATES.get(defender.getUUID());
            if (s == null || s.parryUsed || now > s.parryUntil || ally(defender, owner)) continue;
            Vec3 center = defender.position().add(0, defender.getBbHeight() * .5, 0);
            double t = lengthSqr < 1.0E-6 ? 0 : Mth.clamp(center.subtract(from).dot(segment) / lengthSqr, 0, 1);
            if (from.add(segment.scale(t)).distanceToSqr(center) > 9.0) continue;
            activateParry(defender, s, now);
            return true;
        }
        return false;
    }

    private static void record(ServerPlayer p, PlayerData data, State s, LivingEntity target, Action action, int bonus) {
        if (!com.myriad.combat.SkillTargets.alive(target) || action == s.lastAction) return; s.lastAction = action;
        int cap = target instanceof ServerPlayer ? 6 : 3;
        setStage(p, data, s, Math.min(cap, s.stage + 1 + bonus), p.level().getGameTime() + 60);
        featherBurst(p.level(), p.position().add(0, 1.2, 0), s.stage);
    }

    private static void bonusStage(ServerPlayer p, PlayerData data, State s, int amount) {
        setStage(p, data, s, Math.min(6, s.stage + amount), p.level().getGameTime() + 60);
    }

    private static void setStage(ServerPlayer p, PlayerData data, State s, int stage, long scoreUntil) {
        s.stage = Mth.clamp(stage, 0, 6); s.scoreUntil = scoreUntil; s.nextDecay = scoreUntil + 12;
        data.setBuff(STAGE_COUNTER, s.stage); data.setBuff(SCORE_TIME, s.stage == 0 ? 0 : 60);
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) {
            speed.removeModifier(DANCE_SPEED); if (s.stage > 0) speed.addTransientModifier(new AttributeModifier(DANCE_SPEED, s.stage * .01, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        PlayerDataProvider.syncToClient(p);
    }

    private static void startLinear(ServerPlayer p, State s, MoveKind kind, Vec3 dir, double speed, int ticks, int lv, DustParticleOptions color) {
        s.move = Move.linear(kind, horizontal(dir), speed, ticks, lv); s.move.tint = color;
        PacketDistributor.sendToPlayer(p, new DashPayload((float)s.move.direction.x, 0, (float)s.move.direction.z, ticks));
        p.setDeltaMovement(s.move.direction.scale(speed));
    }

    private static int hitRadius(ServerPlayer p, double radius, float base, float ratio, Action action, boolean bonusMulti) {
        int hit = 0; PlayerData data = PlayerDataProvider.get(p); State s = state(p); float damage = base + ad(p) * ratio;
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(radius), target -> target != p && com.myriad.combat.SkillTargets.alive(target) && !ally(p, target)))
            if (target.distanceToSqr(p) <= radius * radius) {
            DamageCalc.dealSkillDamage(p,target,damage,Kind.PHYSICAL, 1); record(p,data,s,target,action,0); hit++;
        }
        if (bonusMulti && hit >= 2) bonusStage(p,data,s,1); return hit;
    }

    private static void e2Impact(ServerPlayer p, PlayerData data, State s, Move move) {
        float baseDamage = 96 + (move.level - 1) * 40 + ad(p) * 2.0F;
        for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(3), target -> target != p && com.myriad.combat.SkillTargets.alive(target) && !ally(p, target))) {
            double distanceSqr = target.distanceToSqr(p);
            if (distanceSqr > 9.0) continue;
            float damage = move.cleanLanding && distanceSqr <= 1.44 ? baseDamage * 1.2F : baseDamage;
            DamageCalc.dealSkillDamage(p, target, damage, Kind.PHYSICAL, 2);
            move.hit.add(target.getUUID());
            if (target instanceof ServerPlayer) move.heroHit.add(target.getUUID());
            record(p, data, s, target, Action.E2, 0);
        }
    }

    private static void phoenixFall(ServerPlayer p, Vec3 center, int lv) {
        ServerLevel level = p.level(); float ad = ad(p);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(3.5), target -> target != p && com.myriad.combat.SkillTargets.alive(target) && !ally(p, target)))
            if (target.distanceToSqr(center) <= 12.25) {
            float damage = 240 + (lv - 1) * 112 + ad * 2.40F;
            if (target.getHealth() < target.getMaxHealth() * .30F) damage +=
                    (target.getMaxHealth() - target.getHealth()) * .06F;
            DamageCalc.dealSkillDamage(p,target,damage,Kind.PHYSICAL, 3);
        }
        ring(level,center.add(0,.10,0),1.5,WHITE,20);
        ring(level,center.add(0,.12,0),2.5,CRIMSON,28);
        ring(level,center.add(0,.14,0),3.5,GOLD,36);
        squareRing(level, center.add(0, .16, 0), 3.5, GOLD);
        FeiYuEffects.send(level, ParticleTypes.FLAME, center.x, center.y + .8, center.z,
                28, 1.25, .65, 1.25, .045);
        FeiYuEffects.send(level, ParticleTypes.END_ROD, center.x, center.y + 1.0, center.z,
                18, .8, 1.1, .8, .06);
        FeiYuEffects.burst(level,center.add(0,1,0),44,2.3,1F,.32F,.18F);
        level.playSound(null, center.x, center.y, center.z,
                com.myriad.registry.ModSounds.foley("wind"), SoundSource.PLAYERS, 1.25F, .55F);
    }

    private static void beginPhoenixFinisher(ServerPlayer p, State s, Vec3 center, int level) {
        Vec3 delta = center.subtract(p.position());
        s.finisher = new PhoenixFinisher(center, level);
        s.move = Move.parabola(MoveKind.PHOENIX, level, delta, 2.4, p.level().getGameTime(), 9);
        PacketDistributor.sendToPlayer(p, new ParabolicMotionPayload((float)delta.x, (float)delta.y,
                (float)delta.z, 2.4F, 9));
        phoenixOutline(p.level(), center.add(0, .08, 0), GOLD);
        p.level().playSound(null, center.x, center.y, center.z,
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, .9F, 1.35F);
    }

    private static void cancelPhoenixFinisher(ServerPlayer p, State s) {
        s.finisher = null;
        s.move = null;
        PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0));
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void phoenixCharge(ServerLevel level, Vec3 center, Vec3 caster, int age) {
        if ((age & 1) == 0) phoenixOutline(level, center.add(0, .08, 0), age < 5 ? GOLD : CRIMSON);
        double radius = Math.max(.25, 1.6 - age * .15);
        ring(level, caster, radius, age < 5 ? WHITE : GOLD, 12);
        line(level, caster, center.add(0, .2, 0), age < 5 ? CYAN_GOLD : CRIMSON, 5);
        FeiYuEffects.send(level, GOLD, center.x, center.y + 3.4 - age * .32, center.z,
                4, .35, .12, .35, .015);
    }

    private static void phoenixOutline(ServerLevel level, Vec3 c, DustParticleOptions color) {
        Vec3 head = c.add(0, 0, -2.6), chest = c.add(0, 0, -.5), tail = c.add(0, 0, 2.8);
        line(level, head, chest, WHITE, 4);
        line(level, chest, tail, color, 6);
        line(level, chest, c.add(-3.4, 0, -1.5), color, 7);
        line(level, c.add(-3.4, 0, -1.5), c.add(-2.0, 0, .8), CRIMSON, 4);
        line(level, chest, c.add(3.4, 0, -1.5), color, 7);
        line(level, c.add(3.4, 0, -1.5), c.add(2.0, 0, .8), CRIMSON, 4);
        line(level, tail, c.add(-1.3, 0, 3.8), GOLD, 3);
        line(level, tail, c.add(1.3, 0, 3.8), GOLD, 3);
    }

    private static void recovery(ServerPlayer p, PlayerData data, State s, long now) {
        s.recoveryUntil = now + 6;
        data.setBuff(RECOVERY_TIME, 6);
        PlayerDataProvider.syncToClient(p);
    }
    private static Trajectory safeRainbow(ServerPlayer p, Vec3 requested) {
        Vec3 delta = horizontal(requested).scale(7.0);
        for (double height : new double[]{2.2, 1.5, .8}) {
            boolean safe = true;
            for (int i = 1; i <= RAINBOW_TICKS; i++) {
                double t = i / (double)RAINBOW_TICKS;
                Vec3 offset = new Vec3(delta.x * t, 4 * height * t * (1 - t), delta.z * t);
                if (!MapSystem.contains(p.getX() + offset.x, p.getZ() + offset.z)
                        || !p.level().noCollision(p, p.getBoundingBox().move(offset))) {
                    safe = false;
                    break;
                }
            }
            if (safe) return new Trajectory(delta, height);
        }
        return null;
    }

    /** Preview and cast share server crosshair selection; backtrack blocked landings. */
    private static Vec3 moonLanding(ServerPlayer p) {
        Vec3 aimed = com.myriad.game.PlacementUtil.pointOnGround(p, 7);
        if (aimed == null) return null;
        Vec3 horizontal = new Vec3(aimed.x - p.getX(), 0, aimed.z - p.getZ());
        if (horizontal.length() > 5) horizontal = horizontal.normalize().scale(5);
        for (int i = 20; i >= 0; i--) {
            Vec3 ground = groundBelow(p, p.position().add(horizontal.scale(i / 20.0)));
            if (ground == null) continue;
            Vec3 delta = ground.subtract(p.position());
            boolean safe = true;
            int steps = Math.max(1, (int)Math.ceil(delta.length() / .2));
            for (int j = 1; j <= steps; j++) {
                if (!p.level().noCollision(p, p.getBoundingBox().move(delta.scale(j / (double)steps)))) {
                    safe = false; break;
                }
            }
            if (safe) return ground;
        }
        return null;
    }

    private static Vec3 groundBelow(ServerPlayer p, Vec3 point) {
        if (!MapSystem.contains(point.x, point.z)) return null;
        Vec3 from = new Vec3(point.x, p.getY() + .1, point.z);
        var hit = p.level().clip(new net.minecraft.world.level.ClipContext(from, from.add(0, -12, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
        Vec3 ground = hit.getLocation().add(0, .02, 0);
        return p.level().noCollision(p, p.getBoundingBox().move(ground.subtract(p.position()))) ? ground : null;
    }
    private static void commitQCooldown(PlayerData data, State s) {
        if (s.qPendingCooldown > 0) data.setCooldown("liuxiashuangyanfan", s.qPendingCooldown);
        s.qPendingCooldown = 0;
    }
    private static boolean hardControlled(ServerPlayer p) { return p.hasEffect(MobEffects.SLOWNESS) && p.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 200; }
    private static Action actionFor(MoveKind kind) { return switch(kind) { case Q1->Action.Q1; case Q2->Action.Q2; case W2->Action.W2; case E1->Action.E1; case E2->Action.E2; case R,PHOENIX->Action.Q1; }; }
    private static float ad(ServerPlayer p) { return (float)p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK); }
    private static int reducedCooldown(ServerPlayer p,int base){double c=Mth.clamp(p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION),0,60);return Math.max(1,(int)(base*(1-c/100)));}
    private static State state(ServerPlayer p){return STATES.computeIfAbsent(p.getUUID(),id->new State());}
    public static void setRequestedDirection(ServerPlayer p,float x,float z){Vec3 d=new Vec3(x,0,z);if(d.lengthSqr()>1e-6)REQUESTED.put(p.getUUID(),d.normalize());}
    public static void clearRequestedDirection(ServerPlayer p){REQUESTED.remove(p.getUUID());}
    private static Vec3 requestedOrLook(ServerPlayer p){return REQUESTED.getOrDefault(p.getUUID(),horizontal(p.getLookAngle()));}
    private static Vec3 horizontal(Vec3 v){Vec3 f=new Vec3(v.x,0,v.z);return f.lengthSqr()<1e-6?new Vec3(0,0,1):f.normalize();}
    private static int directionSector(Vec3 d){double a=(Math.atan2(d.z,d.x)+Math.PI*2)%(Math.PI*2);return (int)Math.floor((a+Math.PI/8)/(Math.PI/4))&7;}
    private static boolean ally(ServerPlayer p,LivingEntity e){return com.myriad.combat.SkillTargets.ally(p,e);}

    private static void trail(ServerLevel l,Vec3 p,DustParticleOptions c){FeiYuEffects.send(l,c,p.x,p.y,p.z,2,.1,.18,.1,0);}
    private static void ring(ServerLevel l,Vec3 c,double r,DustParticleOptions d,int n){n=FeiYuEffects.samples(n);for(int i=0;i<n;i++){double a=Math.PI*2*i/n;FeiYuEffects.send(l,d,c.x+Math.cos(a)*r,c.y,c.z+Math.sin(a)*r,1,0,0,0,0);}}
    private static void squareRing(ServerLevel l,Vec3 c,double r,DustParticleOptions d){Vec3[]q={c.add(-r,0,-r),c.add(r,0,-r),c.add(r,0,r),c.add(-r,0,r)};for(int i=0;i<4;i++)line(l,q[i],q[(i+1)%4],d,3);}
    private static void line(ServerLevel l,Vec3 a,Vec3 b,DustParticleOptions d,int n){n=FeiYuEffects.samples(n);for(int i=0;i<=n;i++){Vec3 p=a.lerp(b,i/(double)n);FeiYuEffects.send(l,d,p.x,p.y,p.z,1,0,0,0,0);}}
    private static void featherBurst(ServerLevel l,Vec3 c,int stage){float[][] colors={{.3F,.95F,.9F},{.25F,.55F,1F},{.62F,.32F,1F},{1F,.2F,.35F},{1F,.72F,.2F},{1F,.95F,.72F}};int count=Math.max(1,Math.min(6,stage));for(int i=0;i<count;i++){double a=Math.PI*(.2+.6*i/Math.max(1,count-1));Vec3 p=c.add(Math.cos(a)*.8,Math.sin(a)*.45,0);FeiYuEffects.mote(l,p,colors[i][0],colors[i][1],colors[i][2]);}}
    private static void ribbonStep(ServerLevel l,Vec3 c,Vec3 d){for(int i=1;i<=3;i++){Vec3 p=c.add(d.scale(i*1.8)).add(0,Math.sin(Math.PI*i/4)*1.5,0);squareRing(l,p,.3,CYAN_GOLD);}}
    private static void moonImpact(ServerLevel l,Vec3 c){ring(l,c.add(0,.12,0),3,CYAN_GOLD,24);FeiYuEffects.burst(l,c.add(0,.5,0),14,1,.4F,.92F,.9F);}
    private static void parryBurst(ServerLevel l,Vec3 c){squareRing(l,c,.9,GOLD);FeiYuEffects.send(l,ParticleTypes.CRIT,c.x,c.y,c.z,12,.45,.65,.45,.1);}
    private static DustParticleOptions rColor(int beat){return new DustParticleOptions(new int[]{0xF0445E,0x58E6D2,0x8B72FF,0xFFD56A,0xECFAFF}[Math.min(4,beat)],1.08F);}

    public static void clearFor(UUID id){STATES.remove(id);REQUESTED.remove(id);}

    private static final class State {int stage,qPendingCooldown,lastCastSlot=-1;long scoreUntil,nextDecay,recoveryUntil;Action lastAction;UUID qTarget;long qUntil,wUntil,parryUntil,wReturnUntil,parryLockUntil,eRecastUntil,eSecondUnlockAt;boolean parryUsed,wasHardControlled,wRecoveryPending;Move move;Ultimate ultimate;Trajectory preparedRainbow;PhoenixFinisher finisher;}
    private static final class Move {final MoveKind kind;Vec3 direction;double speed;int ticks,totalTicks,level;long started;UUID target;double startAngle,deltaAngle,arcHeight;Vec3 delta,arcCenter,landing;DustParticleOptions tint;boolean cleanLanding=true,primaryHitDone;final Set<UUID>hit=new HashSet<>(),heroHit=new HashSet<>();
        private Move(MoveKind k,int lv){kind=k;level=lv;}
        static Move linear(MoveKind k,Vec3 d,double s,int t,int lv){Move m=new Move(k,lv);m.direction=d;m.speed=s;m.ticks=t;m.totalTicks=t;return m;}
        static Move arc(MoveKind k,int lv,UUID target,double start,double delta,long now,int ticks){Move m=new Move(k,lv);m.target=target;m.startAngle=start;m.deltaAngle=delta;m.started=now;m.ticks=ticks;m.totalTicks=ticks;m.speed=1;return m;}
        static Move parabola(int lv,Vec3 delta,double height,long now,int ticks){return parabola(MoveKind.E1,lv,delta,height,now,ticks);}
        static Move parabola(MoveKind kind,int lv,Vec3 delta,double height,long now,int ticks){Move m=new Move(kind,lv);m.delta=delta;m.arcHeight=height;m.started=now;m.ticks=ticks;m.totalTicks=ticks;m.direction=Vec3.ZERO;return m;}
        DustParticleOptions color(){return tint!=null?tint:switch(kind){case Q1->CRIMSON;case Q2,E1->CYAN_GOLD;case W2,E2->GOLD;case R->WHITE;case PHOENIX->GOLD;};}}
    private static final class Ultimate {final int level;final long started;long centerAt;int beats,chapters,perfect,lastSector=-1,pendingSector,usedDirections;boolean pendingPerfect,pendingRepeated;final List<Vec3>points=new ArrayList<>();Ultimate(int lv,long startTick,Vec3 start){level=lv;started=startTick;centerAt=startTick+R_BEAT_LINK_TICKS;points.add(start);}}
    private record Trajectory(Vec3 delta, double height) { }
    private record PhoenixFinisher(Vec3 center, int level) { }
}
