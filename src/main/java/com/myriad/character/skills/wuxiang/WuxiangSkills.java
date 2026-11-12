package com.myriad.character.skills.wuxiang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.client.particle.ParticleEffects;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.ModEntityTracker;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 镜魇·无相：三方位镜痕、双充能突进、镜像换位、绕身斩和三镜门终结。 */
public final class WuxiangSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    private enum Sector { FRONT(1, 0xF2B84B), LEFT(2, 0x43E6D0), RIGHT(4, 0xA56CFF);
        final int bit; final int color;
        Sector(int bit, int color) { this.bit = bit; this.color = color; }
    }

    public static final String Q_CHARGE_COUNTER = "skill_charge_tajingliuguang";
    private static final int MIRROR_DASH_BONUS_TICKS = 5;
    private static final DustParticleOptions MIRROR = new DustParticleOptions(0x91E7E4, 1.0F);
    private static final DustParticleOptions DARK = new DustParticleOptions(0x15202B, .9F);
    private static final DustParticleOptions SILVER = new DustParticleOptions(0xEAFBFF, 1.1F);
    /** 三相以世界北向为正相基准，绝不随目标或施法者视角旋转。 */
    private static final Vec3 FIXED_ASPECT_FORWARD = new Vec3(0, 0, -1);
    private static final Map<UUID, Map<UUID, MarkState>> MARKS = new HashMap<>();
    private static final Map<UUID, ChargeState> CHARGES = new HashMap<>();
    private static final Map<UUID, DashState> DASHES = new HashMap<>();
    private static final Map<UUID, MirrorState> MIRRORS = new HashMap<>();
    private static final Map<UUID, UUID> MIRROR_OWNERS = new HashMap<>();
    private static final Map<UUID, ArcState> ARCS = new HashMap<>();
    private static final Map<UUID, EWindow> E_WINDOWS = new HashMap<>();
    private static final Map<UUID, DomainState> DOMAINS = new HashMap<>();
    private static final Map<UUID, Vec3> REQUESTED_DIRECTIONS = new HashMap<>();
    private static final List<LineSlash> LINE_SLASHES = new ArrayList<>();

    private final Type type;

    public WuxiangSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "wuxiang_passive";
            case Q -> "tajingliuguang";
            case W -> "liuxingyiwei";
            case E -> "nizhaohuifeng";
            case R -> "baixiangwugui";
        }, switch (type) {
            case PASSIVE -> 0;
            case Q -> 180;
            case W -> 360;
            case E -> 280;
            case R -> 2000;
        }, switch (type) {
            case PASSIVE -> 0.0F;
            case Q -> 64.8F;
            case W -> 100.8F;
            case E -> 108.0F;
            case R -> 252.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getMaxCharges() { return type == Type.Q ? 2 : 1; }
    @Override public int getCooldownTicks(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case Q -> Math.max(140, 180 - (lv - 1) * 10);
            case W -> Math.max(280, 360 - (lv - 1) * 20);
            case E -> Math.max(200, 280 - (lv - 1) * 20);
            case R -> Math.max(1, 2000 - (lv - 1) * 300);
            default -> 0;
        };
    }

    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "5秒内从世界固定的正相、左魇、右魇三个120度方位命中英雄，造成14+2×角色等级+35%物攻真实伤害并回复1层Q";
            case Q -> "双充能突进5格，路径斩击造成" + (15 + (lv - 1) * 9) + "+55%物攻；命中新方位缩短充能2秒";
            case W -> "留下6秒、3点完整度镜像，最多换位2次；镜线造成" + (11 + (lv - 1) * 6) + "+30%物攻";
            case E -> "镜刃造成" + (18 + (lv - 1) * 9) + "+60%物攻；再施放绕身并造成" + (12 + (lv - 1) * 7) + "+35%物攻";
            case R -> "展开5秒镜域；闪过三个方位镜门后造成" + (62 + (lv - 1) * 30) + "+65%物攻+10%已损生命真实伤害";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        // 无相存在充能与多段施放，实际入口统一由 handleCast 完成。
    }

    /** SkillSystem 的无相专用入口；处理多段技能、动态法力和自定义充能。 */
    public static void handleCast(ServerPlayer player, PlayerData data, int slot, Skill rawSkill,
            boolean noCooldown, boolean noMana) {
        if (!(rawSkill instanceof WuxiangSkills skill)) return;
        long now = player.level().getGameTime();
        DomainState currentDomain = DOMAINS.get(player.getUUID());
        boolean domainFlash = slot == CharacterType.SLOT_Q && currentDomain != null && now < currentDomain.until;
        boolean wRecast = slot == CharacterType.SLOT_W && canSwap(player, now);
        boolean eRecast = slot == CharacterType.SLOT_E && validEWindow(player, now) != null;
        float mana = domainFlash || eRecast ? 0F : wRecast ? mirrorManaCost(player, now) : skill.getManaCost();

        if (!canUse(player, data, slot, skill, now, noCooldown, domainFlash, wRecast, eRecast)) return;
        if (!noMana && data.getMana() < mana) {
            player.sendSystemMessage(Component.translatable("message.myriad.not_enough_mana"));
            return;
        }
        if (!noMana && mana > 0) data.spendMana(mana);

        switch (skill.type) {
            case Q -> { if (domainFlash) castDomainFlash(player, data); else castQ(player, data, noCooldown); }
            case W -> castW(player, data, wRecast);
            case E -> castE(player, data, eRecast);
            case R -> castR(player, data);
            default -> { }
        }

        if (!noCooldown && !domainFlash && !wRecast && !eRecast && skill.type != Type.Q) {
            data.setCooldown(skill.getId(), reducedCooldown(player, skill.getCooldownTicks(data.skillLevel(skill.getId()))));
        }
        
        com.myriad.character.SkillPresentation.cast(player, skill);
        PlayerDataProvider.syncToClient(player);
    }

    private static boolean canUse(ServerPlayer player, PlayerData data, int slot, WuxiangSkills skill,
            long now, boolean noCooldown, boolean domainFlash, boolean wRecast, boolean eRecast) {
        if (domainFlash) {
            DomainState domain = DOMAINS.get(player.getUUID());
            if (domain == null || now < domain.nextFlashAt) {
                player.sendSystemMessage(Component.translatable("message.myriad.skill_cd",
                        Math.max(1, (int)Math.ceil((domain == null ? 0 : domain.nextFlashAt - now) / 20.0))));
                return false;
            }
            if (selectGate(player, domain) == null) {
                player.sendSystemMessage(Component.translatable("message.myriad.wuxiang_no_gate"));
                return false;
            }
            return true;
        }
        if (wRecast || eRecast) return true;
        if (!noCooldown && skill.type != Type.Q && data.getCooldown(skill.getId()) > 0) {
            player.sendSystemMessage(Component.translatable("message.myriad.skill_cd",
                    (data.getCooldown(skill.getId()) + 19) / 20));
            return false;
        }
        if (skill.type == Type.Q && !noCooldown && charges(player).charges <= 0) {
            player.sendSystemMessage(Component.translatable("message.myriad.wuxiang_no_q_charge"));
            return false;
        }
        if (skill.type == Type.W && MIRRORS.containsKey(player.getUUID())) {
            breakMirror(player.getUUID(), player.level(), false);
        }
        if (skill.type == Type.E && lookedEnemyTarget(player, 13, false) == null) {
            // E 空放仍消耗资源并进入冷却，符合高失败成本设计。
            return true;
        }
        if (skill.type == Type.R && lookedEnemyTarget(player, 12, true) == null) {
            player.sendSystemMessage(Component.translatable("message.myriad.wuxiang_r_need_mark"));
            return false;
        }
        return true;
    }

    private static void castQ(ServerPlayer player, PlayerData data, boolean noCooldown) {
        ChargeState charges = charges(player);
        long now = player.level().getGameTime();
        if (!noCooldown) {
            charges.charges--;
            int lv = Math.max(1, data.skillLevel("tajingliuguang"));
            com.myriad.character.SequentialChargeTimers.enqueue(charges.readyAt, now,
                    Math.max(140, 180 - (lv - 1) * 10));
        }
        Vec3 direction = requestedOrLook(player);
        DashState dash = new DashState(direction, 5, Math.max(1, data.skillLevel("tajingliuguang")));
        DASHES.put(player.getUUID(), dash);
        dashBurst(player.level(), player.position().add(0, .85, 0), direction, false);
        // 与影袭使用同一套 motion 链路：服务端权威速度 + 客户端逐 tick 推进，避免输入覆盖速度。
        PacketDistributor.sendToPlayer(player,
                new DashPayload((float)direction.x, 0F, (float)direction.z, dash.ticks));
        player.setDeltaMovement(direction.x, player.getDeltaMovement().y, direction.z);
        dash.ticks--;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, .65F, 1.65F);
    }

    private static void castW(ServerPlayer player, PlayerData data, boolean recast) {
        long now = player.level().getGameTime();
        if (!recast) {
            Mannequin mirror = spawnMirror(player.level(), player);
            if (mirror == null) return;
            MirrorState state = new MirrorState(mirror, mirror.position(), now + 120);
            MIRRORS.put(player.getUUID(), state);
            MIRROR_OWNERS.put(mirror.getUUID(), player.getUUID());
            data.setBuff("liuxingyiwei", 120);
            data.setBuff("liuxingyiwei_recast", 120);
            mirrorAssemble(player.level(), state.position);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .7F, .72F);
            return;
        }
        MirrorState state = MIRRORS.get(player.getUUID());
        if (state == null) return;
        state.pendingAt = now + 3;
        state.pendingFrom = player.position();
        state.pendingTo = state.position;
        state.freeSwap = false;
        data.setBuff("liuxingyiwei_recast", 0);
        swapTelegraph(player.level(), state.pendingFrom, state.pendingTo);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                com.myriad.registry.ModSounds.foley("ice"), SoundSource.PLAYERS, .45F, 1.8F);
    }

    private static void castE(ServerPlayer player, PlayerData data, boolean recast) {
        long now = player.level().getGameTime();
        if (!recast) {
            LivingEntity target = lookedEnemyTarget(player, 13, false);
            Vec3 bladeEnd = target == null
                    ? player.getEyePosition().add(player.getLookAngle().scale(13))
                    : target.getBoundingBox().getCenter();
            drawMirrorBlade(player.level(), player.getEyePosition(), bladeEnd);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, .7F, 1.35F);
            if (target == null) return;
            int lv = Math.max(1, data.skillLevel("nizhaohuifeng"));
            float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
            DamageCalc.dealSkillDamage(player, target, 72 + (lv - 1) * 36 + ad * 2.40F, Kind.PHYSICAL, 2);
            if (com.myriad.combat.SkillTargets.alive(target)) recordSector(player, target, sectorFromPosition(player.position(), target));
            E_WINDOWS.put(player.getUUID(), new EWindow(target.getUUID(), now + 60, lv));
            data.setBuff("nizhaohuifeng", 60);
            data.setBuff("nizhaohuifeng_recast", 60);
            mirrorSeal(player.level(), target, 0);
            return;
        }
        EWindow window = validEWindow(player, now);
        if (window == null) return;
        Entity raw = player.level().getEntity(window.target);
        if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target)) return;
        Vec3 radial = horizontal(player.position().subtract(target.position()));
        Vec3 requested = REQUESTED_DIRECTIONS.get(player.getUUID());
        Vec3 screenLeft = new Vec3(player.getLookAngle().z, 0, -player.getLookAngle().x);
        int side = requested != null && requested.dot(screenLeft) < 0 ? -1 : 1;
        ARCS.put(player.getUUID(), new ArcState(target.getUUID(), Math.atan2(radial.z, radial.x),
                side * Math.toRadians(120), now, now + 8, window.level));
        arcStart(player.level(), player.position(), target.position(), side);
        E_WINDOWS.remove(player.getUUID());
        data.setBuff("nizhaohuifeng", 0);
        data.setBuff("nizhaohuifeng_recast", 0);
    }

    private static void castR(ServerPlayer player, PlayerData data) {
        LivingEntity target = lookedEnemyTarget(player, 12, true);
        if (target == null) return;
        long now = player.level().getGameTime();
        DOMAINS.put(player.getUUID(), new DomainState(target.getUUID(), target.position(), now + 100,
                Math.max(1, data.skillLevel("baixiangwugui"))));
        data.setBuff("baixiangwugui", 100);
        domainOpen(player.level(), target);
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.PLAYERS, .9F, .65F);
    }

    private static void castDomainFlash(ServerPlayer player, PlayerData data) {
        DomainState domain = DOMAINS.get(player.getUUID());
        Gate gate = selectGate(player, domain);
        if (domain == null || gate == null) return;
        Entity raw = player.level().getEntity(domain.target);
        if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target)) return;
        Vec3 from = player.position();
        gateCollapse(player.level(), gate.position, gate.sector);
        dashBurst(player.level(), from.add(0, .85, 0), horizontal(gate.position.subtract(from)), true);
        player.teleportTo(gate.position.x, gate.position.y, gate.position.z);
        player.resetFallDistance();
        domain.usedBits |= gate.sector.bit;
        domain.nextFlashAt = player.level().getGameTime() + 9;
        int lv = domain.level;
        float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        DamageCalc.dealSkillDamage(player, target, 64 + (lv - 1) * 24 + ad * 1.0F, Kind.PHYSICAL, 3);
        if (com.myriad.combat.SkillTargets.alive(target)) recordSector(player, target, gate.sector);
        drawRibbon(player.level(), from.add(0, .8, 0), player.position().add(0, .8, 0),
                sectorDust(gate.sector), 10);
        aspectImpact(player.level(), target, gate.sector, false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .7F, 1.55F);
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.WUXIANG) return;
        ServerLevel level = player.level();
        long now = level.getGameTime();
        tickCharges(player, data, now);
        tickMarks(player, data, now);
        tickDash(player, data, level, now);
        tickMirror(player, data, level, now);
        tickEWindow(player, level, now);
        tickArc(player, data, level, now);
        tickDomain(player, data, level, now);
        tickLineSlashes(player, level, now);
    }

    private static void tickCharges(ServerPlayer player, PlayerData data, long now) {
        ChargeState state = charges(player);
        boolean changed = false;
        while (!state.readyAt.isEmpty() && state.readyAt.get(0) <= now) {
            state.readyAt.remove(0);
            state.charges = Math.min(2, state.charges + 1);
            changed = true;
        }
        int shownCooldown = state.readyAt.isEmpty() ? 0 : (int)Math.max(1, state.readyAt.get(0) - now);
        data.setCooldown("tajingliuguang", shownCooldown);
        if (data.getBuff(Q_CHARGE_COUNTER) != state.charges) {
            data.setBuff(Q_CHARGE_COUNTER, state.charges);
            changed = true;
        }
        if (changed) PlayerDataProvider.syncToClient(player);
    }

    private static void tickDash(ServerPlayer player, PlayerData data, ServerLevel level, long now) {
        DashState dash = DASHES.get(player.getUUID());
        if (dash == null) return;
        if (dash.ticks-- <= 0) {
            finishDash(player);
            return;
        }
        Vec3 step = dash.direction;
        if (!level.noCollision(player, player.getBoundingBox().move(step))) {
            finishDash(player);
            return;
        }
        player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
        Vec3 at = player.position();
        Vec3 side = new Vec3(-dash.direction.z, 0, dash.direction.x).scale(.24);
        mote(level, at.add(side).add(0, .72, 0), .35F, .95F, .9F);
        mote(level, at.subtract(side).add(0, 1.08, 0), .72F, .42F, 1F);
        if ((dash.ticks & 1) == 0) level.sendParticles(DARK, at.x, at.y + .9, at.z, 1, .08, .2, .08, 0);

        MirrorState mirror = MIRRORS.get(player.getUUID());
        if (!dash.crossedMirror && mirror != null && mirror.mannequin.isAlive()
                && distanceToSegment(mirror.position, player.position(), player.position().add(step)) <= 1.35) {
            dash.crossedMirror = true;
            dash.ticks += MIRROR_DASH_BONUS_TICKS;
            // 服务端延长剩余推进后必须重发客户端 motion 时长，否则客户端会在原始时长
            // 结束冲刺并被本地移动输入覆盖，表现为穿镜却没有获得额外 5 tick 位移。
            PacketDistributor.sendToPlayer(player,
                    new DashPayload((float)dash.direction.x, 0F, (float)dash.direction.z, dash.ticks));
            damageMirror(player.getUUID(), level, false);
            mirrorCross(level, mirror.position, dash.direction);
            level.playSound(null, mirror.position.x, mirror.position.y, mirror.position.z,
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, .6F, 1.75F);
        }
        AABB hitBox = player.getBoundingBox().expandTowards(step).inflate(.6);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox, e -> validEnemy(player, e))) {
            if (target == player || !com.myriad.combat.SkillTargets.alive(target) || ally(player, target)
                    || dash.hit.contains(target.getUUID())) continue;
            dash.hit.add(target.getUUID());
            float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
            DamageCalc.dealSkillDamage(player, target, 60 + (dash.level - 1) * 36 + ad * 2.20F, Kind.PHYSICAL, 0);
            if (com.myriad.combat.SkillTargets.alive(target)) {
                RecordResult result = recordSector(player, target, sectorFromPosition(player.position(), target));
                if (result == RecordResult.NEW) accelerateQ(player, 40);
            }
        }
    }

    private static void finishDash(ServerPlayer player) {
        DASHES.remove(player.getUUID());
        dashBurst(player.level(), player.position().add(0, .8, 0), horizontal(player.getLookAngle()), false);
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
        PacketDistributor.sendToPlayer(player, new DashPayload(0F, 0F, 0F, 0));
    }

    private static void tickMirror(ServerPlayer player, PlayerData data, ServerLevel level, long now) {
        MirrorState state = MIRRORS.get(player.getUUID());
        if (state == null) return;
        if (!state.mannequin.isAlive() || now >= state.until || player.distanceToSqr(state.position) > 256) {
            breakMirror(player.getUUID(), level, false);
            data.setBuff("liuxingyiwei", 0);
            data.setBuff("liuxingyiwei_recast", 0);
            return;
        }
        state.mannequin.setPos(state.position.x, state.position.y, state.position.z);
        state.mannequin.clearFire();
        if (now % 4 == 0) {
            ring(level, state.position.add(0, .08, 0), .72, MIRROR, 8, false, player);
            for (int i = 0; i < state.integrity; i++) {
                double angle = now * .08 + Math.PI * 2 * i / Math.max(1, state.integrity);
                mote(level, state.position.add(Math.cos(angle) * .62, .65 + i * .38, Math.sin(angle) * .62),
                        .58F, .96F, .94F);
            }
        }
        if (state.pendingAt > 0 && now >= state.pendingAt) executeSwap(player, data, state, level, now);
    }

    private static void executeSwap(ServerPlayer player, PlayerData data, MirrorState state, ServerLevel level, long now) {
        Vec3 from = state.pendingFrom;
        Vec3 to = state.pendingTo;
        state.pendingAt = 0;
        if (!safeTeleport(level, player, to) || from.distanceToSqr(to) > 256) return;
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        state.position = from;
        state.mannequin.setPos(from.x, from.y, from.z);
        state.swaps--;
        data.setBuff("liuxingyiwei_recast", state.swaps > 0
                ? (int)Math.max(1, state.until - now) : 0);
        LINE_SLASHES.add(new LineSlash(player.getUUID(), from, to, to, now + 5,
                Math.max(1, data.skillLevel("liuxingyiwei")), state.hitTargets));
        mirrorAssemble(level, from);
        mirrorAssemble(level, to);
        drawRibbon(level, from.add(0, .8, 0), to.add(0, .8, 0), MIRROR, 14);
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, .75F, 1.25F);
    }

    private static void tickLineSlashes(ServerPlayer owner, ServerLevel level, long now) {
        LINE_SLASHES.removeIf(line -> {
            if (!line.owner.equals(owner.getUUID()) || now < line.hitAt) return false;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(line.a, line.b).inflate(1.0), e -> validEnemy(owner, e))) {
                if (line.hit.contains(target.getUUID()) || distanceToSegment(target.position(), line.a, line.b) > .75) continue;
                line.hit.add(target.getUUID());
                float ad = (float)owner.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
                DamageCalc.dealSkillDamage(owner, target, 44 + (line.level - 1) * 24 + ad * 1.20F, Kind.PHYSICAL, 0);
                if (validEnemy(owner, target)) {
                    recordSector(owner, target, sectorFromPosition(line.sectorOrigin, target));
                }
            }
            drawRibbon(level, line.a.add(0, 1, 0), line.b.add(0, 1, 0), SILVER, 16);
            slashCross(level, line.b.add(0, 1, 0), horizontal(line.b.subtract(line.a)), SILVER);
            level.playSound(null, line.b.x, line.b.y, line.b.z, SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, .7F, 1.55F);
            return true;
        });
    }

    private static void tickArc(ServerPlayer player, PlayerData data, ServerLevel level, long now) {
        ArcState arc = ARCS.get(player.getUUID());
        if (arc == null) return;
        Entity raw = level.getEntity(arc.target);
        if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target) || player.distanceToSqr(target) > 196) {
            ARCS.remove(player.getUUID());
            return;
        }
        double progress = Mth.clamp((now - arc.started + 1) / (double)Math.max(1, arc.ends - arc.started), 0, 1);
        double angle = arc.startAngle + arc.deltaAngle * progress;
        Vec3 desired = target.position().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0);
        if (!safeTeleport(level, player, desired)) {
            ARCS.remove(player.getUUID());
            return;
        }
        Vec3 before = player.position();
        player.teleportTo(desired.x, desired.y, desired.z);
        player.resetFallDistance();
        Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(Math.signum(arc.deltaAngle));
        mote(level, desired.add(0, .75, 0), .38F, .95F, .9F);
        mote(level, desired.subtract(tangent.scale(.38)).add(0, 1.05, 0), .66F, .44F, 1F);
        level.sendParticles(DARK, desired.x, desired.y + .9, desired.z, 1, .05, .18, .05, 0);
        if (progress < 1) return;
        ARCS.remove(player.getUUID());
        float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        DamageCalc.dealSkillDamage(player, target, 48 + (arc.level - 1) * 28 + ad * 1.40F, Kind.PHYSICAL, 2);
        if (com.myriad.combat.SkillTargets.alive(target)) recordSector(player, target, sectorFromPosition(player.position(), target));
        drawRibbon(level, before.add(0, .9, 0), target.getBoundingBox().getCenter(), MIRROR, 10);
        slashCross(level, target.getBoundingBox().getCenter(), horizontal(target.position().subtract(player.position())), MIRROR);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .85F, 1.45F);
    }

    private static void tickDomain(ServerPlayer player, PlayerData data, ServerLevel level, long now) {
        DomainState domain = DOMAINS.get(player.getUUID());
        if (domain == null) return;
        Entity raw = level.getEntity(domain.target);
        if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target) || now >= domain.until
                || target.distanceToSqr(domain.center) > 64 || player.isDeadOrDying()) {
            endDomain(player, data, false);
            return;
        }
        if (now % 5 == 0) {
            ring(level, domain.center.add(0, .08, 0), 6, DARK, 18, false, player);
            for (Sector sector : Sector.values()) {
                Vec3 gate = gatePosition(target, sector);
                if ((domain.usedBits & sector.bit) == 0) drawGate(level, gate, target, sector, now);
                else {
                    level.sendParticles(DARK, gate.x, gate.y + .7, gate.z, 2, .22, .32, .22, .01);
                }
            }
        }
    }

    private static void tickMarks(ServerPlayer player, PlayerData data, long now) {
        Map<UUID, MarkState> ownerMarks = MARKS.get(player.getUUID());
        if (ownerMarks == null) return;
        ownerMarks.entrySet().removeIf(entry -> {
            MarkState mark = entry.getValue();
            if (mark.bits == 0 && now >= mark.lockedUntil) return true;
            if (mark.bits != 0 && now >= mark.expires) return true;
            Entity raw = player.level().getEntity(entry.getKey());
            if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target)) return true;
            if (mark.bits != 0 && now % 5 == 0) drawMarks(player.level(), target, mark.bits, now);
            return false;
        });
        int best = ownerMarks.values().stream().mapToInt(m -> Integer.bitCount(m.bits)).max().orElse(0);
        data.setBuff("wuxiang_passive", best);
    }

    /** 普攻事件入口：有效命中的敌方单位（包括野怪）记录方位。 */
    public static void onBasicAttack(ServerPlayer attacker, LivingEntity target) {
        if (!validEnemy(attacker, target)) return;
        recordSector(attacker, target, sectorFromPosition(attacker.position(), target));
    }

    /** E 绕行期间 35% 减伤；R 闪镜的短促位移窗口提供 50% 减伤。 */
    public static void onIncomingDamage(ServerPlayer victim, LivingIncomingDamageEvent event) {
        if (event.getAmount() <= 0) return;
        if (ARCS.containsKey(victim.getUUID())) event.getContainer().setNewDamage(event.getAmount() * .65F);
        DomainState domain = DOMAINS.get(victim.getUUID());
        if (domain != null && victim.level().getGameTime() < domain.nextFlashAt
                && domain.nextFlashAt - victim.level().getGameTime() >= 6) {
            event.getContainer().setNewDamage(event.getAmount() * .50F);
        }
    }

    /** 镜像承受每次敌方英雄攻击时扣 1 点完整度，并吞掉对假人的实际伤害。 */
    public static boolean interceptMirrorDamage(LivingEntity entity, LivingIncomingDamageEvent event) {
        UUID ownerId = MIRROR_OWNERS.get(entity.getUUID());
        if (ownerId == null) return false;
        event.getContainer().setNewDamage(0F);
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer attacker && !attacker.getUUID().equals(ownerId)) {
            ServerLevel level = (ServerLevel)entity.level();
            damageMirror(ownerId, level, true);
        }
        return true;
    }

    private static RecordResult recordSector(ServerPlayer owner, LivingEntity target, Sector sector) {
        long now = owner.level().getGameTime();
        Map<UUID, MarkState> all = MARKS.computeIfAbsent(owner.getUUID(), id -> new HashMap<>());
        MarkState state = all.computeIfAbsent(target.getUUID(), id -> new MarkState());
        if (now < state.lockedUntil) return RecordResult.LOCKED;
        if (state.bits != 0 && now >= state.expires) state.bits = 0;
        boolean fresh = (state.bits & sector.bit) == 0;
        state.bits |= sector.bit;
        state.expires = now + 100;
        aspectImpact(owner.level(), target, sector, fresh);
        PlayerData data = PlayerDataProvider.get(owner);
        data.setBuff("wuxiang_passive", Integer.bitCount(state.bits));
        owner.level().playSound(null, target.getX(), target.getY() + 1, target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .42F,
                sector == Sector.FRONT ? .82F : sector == Sector.LEFT ? 1.08F : 1.32F);
        if (state.bits != 7) return fresh ? RecordResult.NEW : RecordResult.REPEATED;

        DomainState domain = DOMAINS.get(owner.getUUID());
        boolean ultimate = domain != null && domain.target.equals(target.getUUID()) && now < domain.until;
        float ad = (float)owner.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        if (ultimate) {
            float missing = Math.max(0, com.myriad.game.MonsterManager.modMaxHealth(target)
                    - com.myriad.game.MonsterManager.modHealth(target));
            DamageCalc.dealSkillDamage(owner, target, 248 + (domain.level - 1) * 120 + ad * 2.60F + missing * .40F, Kind.TRUE, 3);
            MirrorState mirror = MIRRORS.get(owner.getUUID());
            if (mirror != null && mirror.mannequin.isAlive()) {
                mirror.swaps = Math.max(1, mirror.swaps);
                mirror.freeSwap = true;
                mirror.until = Math.max(mirror.until, now + 20);
                data.setBuff("liuxingyiwei_recast", (int)Math.max(1, mirror.until - now));
            }
            endDomain(owner, data, true);
        } else {
            DamageCalc.dealSkillDamage(owner, target, 56 + data.getLevel() * 8 + ad * 1.40F, Kind.TRUE, 4);
            restoreQ(owner);
        }
        state.bits = 0;
        state.lockedUntil = now + 140;
        data.setBuff("wuxiang_passive", 0);
        triAspectShatter(owner.level(), target);
        PlayerDataProvider.syncToClient(owner);
        return RecordResult.PROC;
    }

    private static void restoreQ(ServerPlayer player) {
        ChargeState state = charges(player);
        if (state.charges >= 2) return;
        state.charges++;
        if (!state.readyAt.isEmpty()) state.readyAt.removeLast();
        PlayerData data = PlayerDataProvider.get(player);
        data.setBuff(Q_CHARGE_COUNTER, state.charges);
    }

    public static int runeRecharge(ServerPlayer player, double fraction) {
        ChargeState state = charges(player);
        if (state.readyAt.isEmpty()) return 0;
        long now = player.level().getGameTime();
        int remaining = (int)Math.max(0, state.readyAt.getFirst() - now);
        if (fraction > 0) com.myriad.character.SequentialChargeTimers.moveFirstTo(state.readyAt,
                now + Math.max(1, (int)Math.ceil(remaining * (1 - fraction))));
        return remaining;
    }

    private static void accelerateQ(ServerPlayer player, int ticks) {
        ChargeState state = charges(player);
        if (state.readyAt.isEmpty()) return;
        long now = player.level().getGameTime();
        com.myriad.character.SequentialChargeTimers.moveFirstTo(state.readyAt,
                Math.max(now + 1, state.readyAt.get(0) - ticks));
    }

    private static Gate selectGate(ServerPlayer player, DomainState domain) {
        if (domain == null) return null;
        Entity raw = player.level().getEntity(domain.target);
        if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target)) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Gate best = null;
        double bestScore = .25;
        for (Sector sector : Sector.values()) {
            if ((domain.usedBits & sector.bit) != 0) continue;
            Vec3 position = gatePosition(target, sector);
            if (!safeTeleport(player.level(), player, position)) continue;
            Vec3 to = position.add(0, 1, 0).subtract(eye);
            double score = to.lengthSqr() < 1.0E-6 ? 1 : look.dot(to.normalize());
            if (score > bestScore) { bestScore = score; best = new Gate(sector, position); }
        }
        return best;
    }

    private static Vec3 gatePosition(LivingEntity target, Sector sector) {
        return target.position().add(sectorDirection(sector).scale(3));
    }

    private static LivingEntity lookedEnemyTarget(ServerPlayer caster, double range, boolean requireMark) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        Map<UUID, MarkState> marks = MARKS.getOrDefault(caster.getUUID(), Map.of());
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                        p -> validEnemy(caster, p)).stream()
                .filter(p -> caster.hasLineOfSight(p)
                        && (!requireMark || marks.get(p.getUUID()) != null
                                && marks.get(p.getUUID()).bits != 0
                                && caster.level().getGameTime() < marks.get(p.getUUID()).expires)
                        && com.myriad.game.VisionSystem.canSee(caster, p))
                .map(p -> new TargetScore(p, targetScore(eye, look, p, range)))
                .filter(s -> s.score >= 0)
                .min(Comparator.comparingDouble(s -> s.score))
                .map(TargetScore::target).orElse(null);
    }

    private static double targetScore(Vec3 eye, Vec3 look, LivingEntity target, double range) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
        double along = to.dot(look);
        if (along < 0 || along > range || to.subtract(look.scale(along)).length() > 1.15) return -1;
        return along;
    }

    private static Sector sectorFromPosition(Vec3 attacker, LivingEntity target) {
        Vec3 toAttacker = horizontal(attacker.subtract(target.position()));
        Sector best = Sector.FRONT;
        double bestDot = -Double.MAX_VALUE;
        for (Sector sector : Sector.values()) {
            double dot = sectorDirection(sector).dot(toAttacker);
            if (dot > bestDot) {
                bestDot = dot;
                best = sector;
            }
        }
        return best;
    }

    private static Vec3 sectorDirection(Sector sector) {
        double angle = sector == Sector.FRONT ? 0
                : sector == Sector.LEFT ? -Math.PI * 2 / 3 : Math.PI * 2 / 3;
        return rotate(FIXED_ASPECT_FORWARD, angle);
    }

    private static boolean canSwap(ServerPlayer player, long now) {
        MirrorState state = MIRRORS.get(player.getUUID());
        return state != null && state.mannequin.isAlive() && now < state.until && state.swaps > 0
                && state.pendingAt == 0 && player.distanceToSqr(state.position) <= 256
                && safeTeleport(player.level(), player, state.position);
    }

    private static float mirrorManaCost(ServerPlayer player, long now) {
        MirrorState state = MIRRORS.get(player.getUUID());
        return state != null && state.freeSwap ? 0F : 12F;
    }

    private static EWindow validEWindow(ServerPlayer player, long now) {
        EWindow window = E_WINDOWS.get(player.getUUID());
        if (window == null || now >= window.until) { E_WINDOWS.remove(player.getUUID()); return null; }
        Entity raw = player.level().getEntity(window.target);
        return raw instanceof LivingEntity target && com.myriad.combat.SkillTargets.alive(target) && player.distanceToSqr(target) <= 196 ? window : null;
    }

    private static ChargeState charges(ServerPlayer player) {
        return CHARGES.computeIfAbsent(player.getUUID(), id -> new ChargeState());
    }

    private static int reducedCooldown(ServerPlayer player, int base) {
        double cdr = Mth.clamp(player.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION), 0, 60);
        return Math.max(1, (int)(base * (1 - cdr / 100.0)));
    }

    public static void setRequestedDirection(ServerPlayer player, float x, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(z)) return;
        Vec3 direction = new Vec3(x, 0, z);
        if (direction.lengthSqr() > 1.0E-6) REQUESTED_DIRECTIONS.put(player.getUUID(), direction.normalize());
    }

    public static void clearRequestedDirection(ServerPlayer player) { REQUESTED_DIRECTIONS.remove(player.getUUID()); }

    private static Vec3 requestedOrLook(ServerPlayer player) {
        Vec3 requested = REQUESTED_DIRECTIONS.get(player.getUUID());
        return requested == null ? horizontal(player.getLookAngle()) : requested;
    }

    private static Mannequin spawnMirror(ServerLevel level, ServerPlayer player) {
        Mannequin mirror = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mirror == null) return null;
        mirror.setPos(player.getX(), player.getY(), player.getZ());
        mirror.setYRot(player.getYRot());
        mirror.setYHeadRot(player.getYHeadRot());
        mirror.setNoGravity(true);
        mirror.setInvulnerable(false);
        mirror.setHealth(mirror.getMaxHealth());
        mirror.setCustomName(Component.translatable("entity.myriad.wuxiang_mirror", 3));
        mirror.setCustomNameVisible(true);
        mirror.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        for (EquipmentSlot slot : EquipmentSlot.values()) mirror.setItemSlot(slot, player.getItemBySlot(slot).copy());
        level.addFreshEntity(ModEntityTracker.markGenerated(mirror));
        return mirror;
    }

    private static void damageMirror(UUID ownerId, ServerLevel level, boolean hostile) {
        MirrorState state = MIRRORS.get(ownerId);
        if (state == null) return;
        state.integrity--;
        if (state.mannequin.isAlive()) {
            state.mannequin.setCustomName(Component.translatable("entity.myriad.wuxiang_mirror", Math.max(0, state.integrity)));
        }
        mirrorCrack(level, state.position.add(0, 1, 0), state.integrity);
        if (hostile) level.playSound(null, state.position.x, state.position.y, state.position.z,
                SoundEvents.GLASS_HIT, SoundSource.PLAYERS, .7F, 1.15F + (3 - state.integrity) * .15F);
        if (state.integrity <= 0) breakMirror(ownerId, level, true);
    }

    private static void breakMirror(UUID ownerId, net.minecraft.world.level.Level level, boolean shattered) {
        MirrorState state = MIRRORS.remove(ownerId);
        if (state == null) return;
        MIRROR_OWNERS.remove(state.mannequin.getUUID());
        if (state.mannequin.isAlive()) state.mannequin.discard();
        if (level instanceof ServerLevel server) {
            if (server.getPlayerByUUID(ownerId) instanceof ServerPlayer owner) {
                PlayerData data = PlayerDataProvider.get(owner);
                data.setBuff("liuxingyiwei", 0);
                data.setBuff("liuxingyiwei_recast", 0);
                PlayerDataProvider.syncToClient(owner);
            }
        }
        if (shattered && level instanceof ServerLevel server) shatter(server, state.position.add(0, 1, 0));
    }

    private static void endDomain(ServerPlayer owner, PlayerData data, boolean success) {
        DomainState state = DOMAINS.remove(owner.getUUID());
        data.setBuff("baixiangwugui", 0);
        if (state != null) {
            if (success) {
                owner.level().playSound(null, state.center.x, state.center.y, state.center.z,
                        SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1F, .72F);
                domainImplode(owner.level(), state.center);
            } else {
                owner.level().playSound(null, state.center.x, state.center.y, state.center.z,
                        com.myriad.registry.ModSounds.foley("ice"), SoundSource.PLAYERS, .5F, 1.5F);
                ring(owner.level(), state.center.add(0, .12, 0), 5.8, DARK, 18, false, owner);
            }
        }
    }

    private static boolean safeTeleport(net.minecraft.world.level.Level level, ServerPlayer player, Vec3 position) {
        if (!(level instanceof ServerLevel server) || !server.hasChunkAt(BlockPos.containing(position))) return false;
        Vec3 delta = position.subtract(player.position());
        return server.noCollision(player, player.getBoundingBox().move(delta));
    }

    private static boolean validEnemy(ServerPlayer caster, LivingEntity target) {
        return target != caster && com.myriad.combat.SkillTargets.alive(target)
                && !MIRROR_OWNERS.containsKey(target.getUUID())
                && !target.isSpectator() && !ally(caster, target);
    }

    private static boolean ally(ServerPlayer caster, LivingEntity other) {
        if (other == caster || com.myriad.game.JiarenManager.isFriendlyDummy(caster, other)) return true;
        GameManager manager = GameManager.get();
        Faction a = manager == null ? null : manager.factionOf(caster);
        Faction b = com.myriad.game.LaneSystem.factionOf(other);
        return a != null && a == b;
    }

    private static Vec3 horizontal(Vec3 value) {
        Vec3 flat = new Vec3(value.x, 0, value.z);
        return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    private static Vec3 rotate(Vec3 vector, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vec3(vector.x * cos - vector.z * sin, 0, vector.x * sin + vector.z * cos);
    }

    private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(point.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr()), 0, 1);
        return point.distanceTo(a.add(ab.scale(t)));
    }

    private static void drawMarks(ServerLevel level, LivingEntity target, int bits, long now) {
        for (Sector sector : Sector.values()) {
            if ((bits & sector.bit) == 0) continue;
            drawSectorArc(level, target, sector, 1.18, 8, false);
        }
    }

    /** 在目标脚下绘制一个固定方向的 1/3 圆环，分别对应三相的 120°区域。 */
    private static void drawSectorArc(ServerLevel level, LivingEntity target, Sector sector,
            double radius, int points, boolean strong) {
        Vec3 center = target.position().add(0, .08, 0);
        Vec3 direction = sectorDirection(sector);
        double centerAngle = Math.atan2(direction.z, direction.x);
        double halfArc = Math.PI / 3;
        DustParticleOptions color = sectorDust(sector);
        int samples = Math.max(3, points);
        for (int i = 0; i <= samples; i++) {
            double angle = centerAngle - halfArc + halfArc * 2 * i / samples;
            Vec3 point = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            level.sendParticles(color, point.x, point.y, point.z, strong ? 2 : 1,
                    strong ? .025 : .01, .008, strong ? .025 : .01, 0);
        }
        if (strong) {
            Vec3 middle = center.add(direction.scale(radius));
            ParticleEffects.burst(level, middle, 7, .24,
                    red(sector.color), green(sector.color), blue(sector.color));
        }
    }

    private static void drawMirrorBlade(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 direction = horizontal(to.subtract(from));
        Vec3 side = new Vec3(-direction.z, 0, direction.x).scale(.1);
        drawLine(level, from, to, MIRROR, 14, false, null);
        drawLine(level, from.add(side), to.add(side), DARK, 9, false, null);
        drawDiamond(level, to, direction, 0.42, MIRROR);
        level.sendParticles(ParticleTypes.ENCHANT, to.x, to.y, to.z, 4, .12, .12, .12, .01);
    }

    private static void shatter(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, 14, .65, .65, .65, .1);
        ParticleEffects.burst(level, center, 12, .65, .62F, .96F, .94F);
    }

    private static void tickEWindow(ServerPlayer player, ServerLevel level, long now) {
        EWindow window = E_WINDOWS.get(player.getUUID());
        if (window == null) return;
        Entity raw = level.getEntity(window.target);
        if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target) || now >= window.until) {
            E_WINDOWS.remove(player.getUUID());
            PlayerData data = PlayerDataProvider.get(player);
            data.setBuff("nizhaohuifeng", 0);
            data.setBuff("nizhaohuifeng_recast", 0);
            return;
        }
        if (now % 5 == 0) mirrorSeal(level, target, window.until - now);
    }

    private static DustParticleOptions sectorDust(Sector sector) {
        return new DustParticleOptions(sector.color, 1.02F);
    }

    private static float red(int color) { return ((color >> 16) & 255) / 255F; }
    private static float green(int color) { return ((color >> 8) & 255) / 255F; }
    private static float blue(int color) { return (color & 255) / 255F; }

    private static void mote(ServerLevel level, Vec3 position, float r, float g, float b) {
        ParticleEffects.mote(level, position, r, g, b);
    }

    private static void dashBurst(ServerLevel level, Vec3 center, Vec3 direction, boolean strong) {
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        int spokes = strong ? 5 : 3;
        for (int i = -spokes; i <= spokes; i++) {
            Vec3 p = center.add(direction.scale(i * .13)).add(side.scale(Math.sin(i * 1.7) * .28));
            mote(level, p, i % 2 == 0 ? .35F : .68F, i % 2 == 0 ? .95F : .44F, i % 2 == 0 ? .9F : 1F);
        }
        ring(level, center.add(0, -.72, 0), strong ? .75 : .48, MIRROR, strong ? 10 : 7, false, null);
    }

    private static void mirrorAssemble(ServerLevel level, Vec3 base) {
        Vec3 center = base.add(0, 1.05, 0);
        drawDiamond(level, center, new Vec3(0, 0, 1), .72, MIRROR);
        ring(level, base.add(0, .08, 0), .72, DARK, 10, false, null);
        ParticleEffects.burst(level, center, 8, .42, .55F, .94F, .92F);
    }

    private static void swapTelegraph(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 a = from.add(0, .75, 0), b = to.add(0, .75, 0);
        Vec3 side = new Vec3(-(b.z - a.z), 0, b.x - a.x);
        if (side.lengthSqr() > 1.0E-6) side = side.normalize().scale(.18);
        drawLine(level, a.add(side), b.add(side), MIRROR, 10, false, null);
        drawLine(level, a.subtract(side), b.subtract(side), DARK, 10, false, null);
    }

    private static void mirrorCross(ServerLevel level, Vec3 center, Vec3 direction) {
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        for (int i = -2; i <= 2; i++) {
            Vec3 a = center.add(side.scale(i * .22)).add(0, .25, 0);
            Vec3 b = center.add(direction.scale(1.15)).add(side.scale(i * .1)).add(0, 1.35, 0);
            drawLine(level, a, b, i == 0 ? SILVER : MIRROR, 5, false, null);
        }
    }

    private static void mirrorSeal(ServerLevel level, LivingEntity target, long remaining) {
        double phase = level.getGameTime() * .16;
        Vec3 center = target.position().add(0, 1.05, 0);
        for (int i = 0; i < 6; i++) {
            double a = phase + Math.PI * 2 * i / 6;
            Vec3 p = center.add(Math.cos(a) * .72, Math.sin(a * 2) * .18, Math.sin(a) * .72);
            mote(level, p, .35F, .95F, .9F);
        }
        if (remaining <= 15) level.sendParticles(DARK, center.x, center.y, center.z, 2, .25, .45, .25, .01);
    }

    private static void arcStart(ServerLevel level, Vec3 from, Vec3 target, int side) {
        Vec3 radial = horizontal(from.subtract(target));
        for (int i = 0; i <= 8; i++) {
            double angle = side * Math.toRadians(120) * i / 8.0;
            Vec3 p = target.add(rotate(radial, angle).scale(3)).add(0, .12 + i * .04, 0);
            mote(level, p, .4F, .92F, .92F);
        }
    }

    private static void domainOpen(ServerLevel level, LivingEntity target) {
        Vec3 center = target.position();
        ring(level, center.add(0, .08, 0), 6, DARK, 32, false, null);
        ring(level, center.add(0, .12, 0), 3, MIRROR, 18, false, null);
        for (Sector sector : Sector.values()) {
            Vec3 gate = gatePosition(target, sector);
            drawGate(level, gate, target, sector, level.getGameTime());
            drawLine(level, center.add(0, .18, 0), gate.add(0, .18, 0), sectorDust(sector), 7, false, null);
        }
    }

    private static void drawGate(ServerLevel level, Vec3 gate, LivingEntity target, Sector sector, long now) {
        Vec3 outward = horizontal(gate.subtract(target.position()));
        Vec3 side = new Vec3(-outward.z, 0, outward.x);
        double pulse = .68 + Math.sin(now * .15 + sector.ordinal() * 2) * .07;
        Vec3 bottomLeft = gate.subtract(side.scale(pulse)).add(0, .08, 0);
        Vec3 top = gate.add(0, 2.15, 0);
        Vec3 bottomRight = gate.add(side.scale(pulse)).add(0, .08, 0);
        DustParticleOptions color = sectorDust(sector);
        drawLine(level, bottomLeft, top, color, 3, false, null);
        drawLine(level, top, bottomRight, color, 3, false, null);
        mote(level, gate.add(0, 1.05, 0), red(sector.color), green(sector.color), blue(sector.color));
    }

    private static void gateCollapse(ServerLevel level, Vec3 gate, Sector sector) {
        ParticleEffects.burst(level, gate.add(0, 1, 0), 12, .72,
                red(sector.color), green(sector.color), blue(sector.color));
        drawDiamond(level, gate.add(0, 1, 0), new Vec3(0, 0, 1), .55, sectorDust(sector));
    }

    private static void aspectImpact(ServerLevel level, LivingEntity target, Sector sector, boolean fresh) {
        drawSectorArc(level, target, sector, fresh ? 1.26 : 1.18, fresh ? 14 : 8, fresh);
    }

    private static void triAspectShatter(ServerLevel level, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        for (Sector sector : Sector.values()) {
            Vec3 edge = center.add(sectorDirection(sector).scale(1.8));
            drawLine(level, edge, center, sectorDust(sector), 7, false, null);
        }
        ParticleEffects.burst(level, center, 18, .82, .86F, .98F, 1F);
        shatter(level, center);
    }

    private static void domainImplode(ServerLevel level, Vec3 center) {
        ring(level, center.add(0, .12, 0), 5.8, DARK, 20, false, null);
        ring(level, center.add(0, .45, 0), 3.4, MIRROR, 16, false, null);
        ring(level, center.add(0, .85, 0), 1.4, SILVER, 12, false, null);
        ParticleEffects.burst(level, center.add(0, 1, 0), 24, 1.15, .82F, .98F, 1F);
        shatter(level, center.add(0, 1, 0));
    }

    private static void mirrorCrack(ServerLevel level, Vec3 center, int integrity) {
        int spokes = Math.max(3, 7 - integrity);
        for (int i = 0; i < spokes; i++) {
            double angle = Math.PI * 2 * i / spokes + integrity * .4;
            Vec3 end = center.add(Math.cos(angle) * .55, Math.sin(angle * 1.7) * .45, Math.sin(angle) * .55);
            drawLine(level, center, end, integrity <= 0 ? SILVER : MIRROR, 3, false, null);
        }
    }

    private static void drawRibbon(ServerLevel level, Vec3 a, Vec3 b, DustParticleOptions color, int points) {
        Vec3 direction = horizontal(b.subtract(a));
        Vec3 side = new Vec3(-direction.z, 0, direction.x).scale(.12);
        drawLine(level, a.add(side), b.add(side), color, points, false, null);
        drawLine(level, a.subtract(side), b.subtract(side), DARK, Math.max(4, points / 2), false, null);
    }

    private static void slashCross(ServerLevel level, Vec3 center, Vec3 direction, DustParticleOptions color) {
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        drawLine(level, center.subtract(side.scale(.75)).add(0, -.45, 0),
                center.add(side.scale(.75)).add(0, .45, 0), color, 7, false, null);
        drawLine(level, center.subtract(side.scale(.62)).add(0, .42, 0),
                center.add(side.scale(.62)).add(0, -.42, 0), DARK, 6, false, null);
    }

    private static void drawDiamond(ServerLevel level, Vec3 center, Vec3 facing, double size,
            DustParticleOptions color) {
        Vec3 flat = horizontal(facing);
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 top = center.add(0, size, 0), right = center.add(side.scale(size)),
                bottom = center.add(0, -size, 0), left = center.subtract(side.scale(size));
        drawLine(level, top, right, color, 2, false, null);
        drawLine(level, right, bottom, color, 2, false, null);
        drawLine(level, bottom, left, color, 2, false, null);
        drawLine(level, left, top, color, 2, false, null);
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, DustParticleOptions color,
            int points, boolean privateView, ServerPlayer viewer) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            if (privateView && viewer != null) level.sendParticles(viewer, color, false, false, x, center.y, z, 1, 0, 0, 0, 0);
            else level.sendParticles(color, x, center.y, z, 1, 0, 0, 0, 0);
        }
    }

    private static void drawLine(ServerLevel level, Vec3 a, Vec3 b, DustParticleOptions color,
            int points, boolean privateView, ServerPlayer viewer) {
        for (int i = 0; i <= points; i++) {
            Vec3 p = a.lerp(b, i / (double)points);
            if (privateView && viewer != null) level.sendParticles(viewer, color, false, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            else level.sendParticles(color, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    public static void clearFor(UUID id) {
        MARKS.remove(id);
        MARKS.values().forEach(map -> map.remove(id));
        CHARGES.remove(id);
        DASHES.remove(id);
        ARCS.remove(id);
        E_WINDOWS.remove(id);
        DOMAINS.remove(id);
        REQUESTED_DIRECTIONS.remove(id);
        LINE_SLASHES.removeIf(line -> line.owner.equals(id));
        MirrorState state = MIRRORS.remove(id);
        if (state != null) {
            MIRROR_OWNERS.remove(state.mannequin.getUUID());
            if (state.mannequin.isAlive()) state.mannequin.discard();
        }
    }

    private enum RecordResult { NEW, REPEATED, PROC, LOCKED }
    private static final class MarkState { int bits; long expires; long lockedUntil; }
    private static final class ChargeState { int charges = 2; final List<Long> readyAt = new ArrayList<>(); }
    private static final class DashState {
        final Vec3 direction; int ticks; final int level; boolean crossedMirror; final Set<UUID> hit = new HashSet<>();
        DashState(Vec3 direction, int ticks, int level) { this.direction = direction; this.ticks = ticks; this.level = level; }
    }
    private static final class MirrorState {
        final Mannequin mannequin; Vec3 position; long until; int integrity = 3; int swaps = 2;
        long pendingAt; Vec3 pendingFrom; Vec3 pendingTo; boolean freeSwap; final Set<UUID> hitTargets = new HashSet<>();
        MirrorState(Mannequin mannequin, Vec3 position, long until) { this.mannequin = mannequin; this.position = position; this.until = until; }
    }
    private record EWindow(UUID target, long until, int level) { }
    private record ArcState(UUID target, double startAngle, double deltaAngle, long started, long ends, int level) { }
    private static final class DomainState {
        final UUID target; final Vec3 center; final long until; final int level; int usedBits; long nextFlashAt;
        DomainState(UUID target, Vec3 center, long until, int level) { this.target = target; this.center = center; this.until = until; this.level = level; }
    }
    private record Gate(Sector sector, Vec3 position) { }
    private record TargetScore(LivingEntity target, double score) { }
    private record LineSlash(UUID owner, Vec3 a, Vec3 b, Vec3 sectorOrigin, long hitAt, int level, Set<UUID> hit) { }
}
