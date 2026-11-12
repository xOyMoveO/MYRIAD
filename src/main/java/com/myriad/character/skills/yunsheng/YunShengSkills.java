package com.myriad.character.skills.yunsheng;

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
import com.myriad.combat.WindWallSystem;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.PlacementUtil;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** 灵契·云笙：灵契辅助、视野界标、共鸣减伤与群体牵引。 */
public final class YunShengSkills extends Skill {
    public static final int BEACON_PLACEMENT = 7;
    public static final int CHORUS_PLACEMENT = 8;
    public enum Type { PASSIVE, Q, W, E, R }

    private static final DustParticleOptions JADE = new DustParticleOptions(0x7EF5E8, 1.25F);
    private static final DustParticleOptions PALE = new DustParticleOptions(0xE7FFF7, .78F);
    private static final Map<UUID, CraneState> CRANES = new HashMap<>();
    private static final Map<UUID, BeaconState> BEACONS = new HashMap<>();
    private static final Map<UUID, PendingChorus> PENDING_CHORUS = new HashMap<>();
    private static final Map<UUID, PullState> PULLS = new HashMap<>();
    private static final Map<UUID, Long> SLOW_IMMUNITY = new HashMap<>();
    private static final Map<UUID, Long> RESONANCE = new HashMap<>();
    private static final Map<UUID, ContractSequence> CONTRACTS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> AURA_TARGETS = new HashMap<>();

    private final Type type;

    public YunShengSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "yunsheng_passive";
            case Q -> "zhihechuanyin";
            case W -> "dingqisuo";
            case E -> "huiyinjiebiao";
            case R -> "zhongshengguizong";
        }, switch (type) {
            case PASSIVE -> 0;
            case Q -> 140;
            case W -> 280;
            case E -> 440;
            case R -> 1600;
        }, switch (type) {
            case PASSIVE -> 0.0F;
            case Q -> 72.0F;
            case W -> 126.0F;
            case E -> 180.0F;
            case R -> 360.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getCooldownTicks(int level) {
        return type == Type.R ? Math.max(20, 1600 - Math.max(0, level - 1) * 200)
                : super.getCooldownTicks(level);
    }

    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "8格友方获得5%技能急速；同目标连续受两次技能保护后获得一次20%减伤";
            case Q -> "友方恢复 " + (20 + (lv - 1) * 6) + "+30%法强 法力；敌方受到 "
                    + (22 + (lv - 1) * 7) + "+45%法强 魔法伤害";
            case W -> "灵契2.5秒；敌人先定身 " + String.format("%.2f", .8 + ((lv - 1) / 2) * .15)
                    + " 秒，友方获得20%移速与一次缓慢免疫";
            case E -> "界标持续8秒，首次触发伤害 " + (25 + (lv - 1) * 10) + "+40%法强";
            case R -> "5格范围治疗 " + (40 + (lv - 1) * 12) + "+50%法强；敌方受到 "
                    + (80 + (lv - 1) * 18) + "+70%法强并沉默1.5秒";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        int lv = Math.max(1, data.skillLevel(getId()));
        double sp = player.getAttributeValue(ModAttributes.SPELL_POWER);
        if (type == Type.Q || type == Type.W) {
            LivingEntity target = lookedTarget(level, player, type == Type.Q ? 18 : 16);
            if (target == null) return;
            if (type == Type.Q) {
                UUID key = UUID.randomUUID();
                Vec3 start = player.getEyePosition().add(player.getLookAngle().scale(.45));
                Vec3 direct = target.getBoundingBox().getCenter().subtract(start);
                Vec3 flat = new Vec3(direct.x, 0, direct.z);
                if (flat.lengthSqr() < 1.0E-6) flat = new Vec3(0, 0, 1);
                flat = flat.normalize();
                Vec3 side = new Vec3(-flat.z, 0, flat.x);
                double sign = level.getRandom().nextBoolean() ? 1.0 : -1.0;
                Vec3 controlOffset = side.scale(sign * (1.5 + level.getRandom().nextDouble() * 2.2))
                        .add(0, 1.0 + level.getRandom().nextDouble() * 1.8, 0);
                long flightTicks = Math.max(12, (long)Math.ceil(direct.length() / .62) + 7);
                CRANES.put(key, new CraneState(player.getUUID(), target.getUUID(), start,
                        controlOffset, level.getGameTime(), level.getGameTime() + flightTicks, lv, sp));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PARROT_FLY,
                        SoundSource.PLAYERS, .75F, 1.55F);
            } else if (ally(player, target)) {
                target.addEffect(new MobEffectInstance(MobEffects.SPEED, 50, 0, false, false, false));
                SLOW_IMMUNITY.put(target.getUUID(), level.getGameTime() + 50);
                registerProtectedTarget(player, target, level.getGameTime());
                ring(level, target.position().add(0, .15, 0), 1.0, JADE, 24);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, .7F, 1.35F);
            } else {
                int root = 16 + ((lv - 1) / 2) * 3;
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, root, 255, false, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1, false, false, false));
                ring(level, target.position().add(0, .15, 0), 1.0, PALE, 24);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE,
                        SoundSource.PLAYERS, .8F, 1.35F);
            }
        } else if (type == Type.E) {
            data.setPlacementType(BEACON_PLACEMENT);
            PlayerDataProvider.syncToClient(player);
        } else if (type == Type.R) {
            data.setPlacementType(CHORUS_PLACEMENT);
            PlayerDataProvider.syncToClient(player);
        }
    }

    public static boolean hasTarget(ServerPlayer player, int slot) {
        if (slot != CharacterType.SLOT_Q && slot != CharacterType.SLOT_W) return true;
        return lookedTarget(player.level(), player, slot == CharacterType.SLOT_Q ? 18 : 16) != null;
    }

    public static void confirmPlacement(ServerPlayer player, PlayerData data) {
        int type = data.getPlacementType();
        if (type == BEACON_PLACEMENT) {
            Vec3 point = PlacementUtil.pointOnGround(player, 18);
            if (point == null) return;
            int lv = Math.max(1, data.skillLevel("huiyinjiebiao"));
            BEACONS.put(player.getUUID(), new BeaconState(point, player.level().getGameTime() + 160, lv,
                    player.getAttributeValue(ModAttributes.SPELL_POWER)));
            data.setPlacementType(0);
            data.setBuff("huiyinjiebiao", 160);
            PlayerDataProvider.syncToClient(player);
            player.level().playSound(null, point.x, point.y, point.z, com.myriad.registry.ModSounds.foley("wind"),
                    SoundSource.PLAYERS, .75F, 1.45F);
        } else if (type == CHORUS_PLACEMENT) {
            Vec3 point = PlacementUtil.pointOnGround(player, 10);
            if (point == null) return;
            int lv = Math.max(1, data.skillLevel("zhongshengguizong"));
            PENDING_CHORUS.put(player.getUUID(), new PendingChorus(point, player.level().getGameTime() + 16, lv,
                    player.getAttributeValue(ModAttributes.SPELL_POWER)));
            data.setPlacementType(0);
            data.setBuff("zhongshengguizong", 16);
            PlayerDataProvider.syncToClient(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("wind"),
                    SoundSource.PLAYERS, .75F, 1.18F);
        }
    }

    public static void cancelPlacement(ServerPlayer player, PlayerData data) {
        int placement = data.getPlacementType();
        if (placement == BEACON_PLACEMENT || placement == CHORUS_PLACEMENT) {
            data.setPlacementType(0);
            data.setCooldown(placement == BEACON_PLACEMENT ? "huiyinjiebiao" : "zhongshengguizong", 0);
            PlayerDataProvider.syncToClient(player);
        }
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long immune = SLOW_IMMUNITY.get(player.getUUID());
        if (immune != null) {
            if (now >= immune) SLOW_IMMUNITY.remove(player.getUUID());
            else if (player.hasEffect(MobEffects.SLOWNESS)) {
                player.removeEffect(MobEffects.SLOWNESS);
                SLOW_IMMUNITY.remove(player.getUUID());
            }
        }
        RESONANCE.entrySet().removeIf(e -> e.getValue() <= now);
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.YUNSHENG) return;
        tickAura(player, level);
        tickCranes(player, level, now);
        tickBeacon(player, data, level, now);
        tickChorus(player, data, level, now);
        tickPull(player, level, now);
        tickPlacementPreview(player, data, level);
    }

    /** 选点阶段的有效范围仅发送给施法者，不向队友或敌人泄露预选落点。 */
    private static void tickPlacementPreview(ServerPlayer player, PlayerData data, ServerLevel level) {
        if (player.tickCount % 3 != 0) return;
        int placement = data.getPlacementType();
        if (placement == BEACON_PLACEMENT) {
            Vec3 point = PlacementUtil.pointOnGround(player, 18);
            if (point == null) return;
            int lv = Math.max(1, data.skillLevel("huiyinjiebiao"));
            double radius = 5 + ((lv - 1) / 3) * .5;
            ringTo(level, player, point, radius, JADE, 36);
            ringTo(level, player, point, Math.max(.7, radius * .55), PALE, 22);
        } else if (placement == CHORUS_PLACEMENT) {
            Vec3 point = PlacementUtil.pointOnGround(player, 10);
            if (point == null) return;
            ringTo(level, player, point, 5, PALE, 40);
            ringTo(level, player, point, 2.5, JADE, 24);
        }
    }

    private static void tickAura(ServerPlayer owner, ServerLevel level) {
        Identifier modifierId = auraId(owner.getUUID());
        Set<UUID> current = new HashSet<>();
        for (ServerPlayer other : level.players()) {
            if (!ally(owner, other) || other.distanceTo(owner) > 8) continue;
            current.add(other.getUUID());
            var cdr = other.getAttribute(ModAttributes.COOLDOWN_REDUCTION);
            if (cdr != null && cdr.getModifier(modifierId) == null) {
                cdr.addTransientModifier(new AttributeModifier(modifierId, 5.0, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        Set<UUID> previous = AURA_TARGETS.put(owner.getUUID(), current);
        if (previous != null) for (UUID id : previous) if (!current.contains(id)) {
            ServerPlayer other = level.getServer().getPlayerList().getPlayer(id);
            if (other != null) {
                var cdr = other.getAttribute(ModAttributes.COOLDOWN_REDUCTION);
                if (cdr != null) cdr.removeModifier(modifierId);
            }
        }
    }

    private static void tickCranes(ServerPlayer owner, ServerLevel level, long now) {
        CRANES.entrySet().removeIf(entry -> {
            CraneState crane = entry.getValue();
            if (!crane.owner.equals(owner.getUUID())) return false;
            Entity raw = level.getEntity(crane.target);
            if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target) || now >= crane.expiresAt) return true;
            Vec3 aim = target.getBoundingBox().getCenter();
            Vec3 delta = aim.subtract(crane.pos);
            if (delta.length() <= .7 || now >= crane.arriveAt) {
                if (WindWallSystem.blocksProjectile(level, crane.pos, aim)
                        || com.myriad.character.skills.feiyu.FeiYuSkills.blocksProjectile(level, owner, crane.pos, aim)) return true;
                if (ally(owner, target)) {
                    if (target instanceof ServerPlayer allyPlayer) {
                        PlayerData targetData = PlayerDataProvider.get(allyPlayer);
                        float restore = (float)(20 + (crane.level - 1) * 6 + crane.sp * .30);
                        targetData.setMana((float)Math.min(allyPlayer.getAttributeValue(ModAttributes.MAX_MANA),
                                targetData.getMana() + restore));
                        PlayerDataProvider.syncToClient(allyPlayer);
                    }
                    revealAround(level, owner, target.position(), 4, 60);
                    registerProtectedTarget(owner, target, now);
                } else {
                    DamageCalc.dealSkillDamage(owner, target,
                            (float)(88 + (crane.level - 1) * 28 + crane.sp * 1.80), Kind.MAGIC, 0);
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false, false));
                }
                level.sendParticles(JADE, aim.x, aim.y, aim.z, 22, .35, .35, .35, .035);
                level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.AMETHYST_CLUSTER_HIT,
                        SoundSource.PLAYERS, .75F, 1.45F);
                return true;
            }
            double progress = Math.min(1.0, (now - crane.startedAt + 1.0)
                    / Math.max(1.0, crane.arriveAt - crane.startedAt));
            Vec3 control = crane.start.lerp(aim, .5).add(crane.controlOffset);
            double inv = 1.0 - progress;
            Vec3 next = crane.start.scale(inv * inv)
                    .add(control.scale(2.0 * inv * progress)).add(aim.scale(progress * progress));
            if (WindWallSystem.blocksProjectile(level, crane.pos, next)
                    || com.myriad.character.skills.feiyu.FeiYuSkills.blocksProjectile(level, owner, crane.pos, next)) return true;
            crane.pos = next;
            level.sendParticles(PALE, crane.pos.x, crane.pos.y, crane.pos.z, 2, .06, .06, .06, .005);
            level.sendParticles(ParticleTypes.END_ROD, crane.pos.x, crane.pos.y, crane.pos.z, 1, 0, 0, 0, 0);
            return false;
        });
    }

    private static void tickBeacon(ServerPlayer owner, PlayerData data, ServerLevel level, long now) {
        BeaconState beacon = BEACONS.get(owner.getUUID());
        if (beacon == null) return;
        if (now >= beacon.until) {
            BEACONS.remove(owner.getUUID());
            data.setBuff("huiyinjiebiao", 0);
            return;
        }
        double radius = 5 + ((beacon.level - 1) / 3) * .5;
        if ((now & 3) == 0) ring(level, beacon.center, radius, JADE, 32);
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(beacon.center, beacon.center).inflate(radius + 6),
                e -> com.myriad.combat.SkillTargets.alive(e) && e != owner && !ally(owner, e))) {
            // 友方共享视野以短时显形近似：界标自身半径外额外延伸6格。
            enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 8, 0, false, false, false));
            if (enemy.position().distanceTo(beacon.center) <= radius && beacon.triggered.add(enemy.getUUID())) {
                DamageCalc.dealSkillDamage(owner, enemy,
                        (float)(100 + (beacon.level - 1) * 40 + beacon.sp * 1.60), Kind.MAGIC, 2);
                enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
                level.playSound(null, enemy.getX(), enemy.getY(), enemy.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                        SoundSource.PLAYERS, .7F, 1.4F);
            }
        }
    }

    private static void tickChorus(ServerPlayer owner, PlayerData data, ServerLevel level, long now) {
        PendingChorus pending = PENDING_CHORUS.get(owner.getUUID());
        if (pending == null) return;
        ring(level, pending.center, 5, PALE, 36);
        level.sendParticles(JADE, pending.center.x, pending.center.y + .3, pending.center.z, 6, 1.6, .25, 1.6, .02);
        if (now < pending.until) return;
        PENDING_CHORUS.remove(owner.getUUID());
        data.setBuff("zhongshengguizong", 0);
        List<UUID> pulled = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(pending.center, pending.center).inflate(5), LivingEntity::isAlive)) {
            if (ally(owner, target)) {
                float heal = (float)(40 + (pending.level - 1) * 12 + pending.sp * .50);
                float effective = Math.min(heal, target.getMaxHealth() - target.getHealth());
                com.myriad.combat.CombatTelemetry.heal(target, heal, "yunsheng_skill");
                
                registerProtectedTarget(owner, target, now);
                if (target != owner) pulled.add(target.getUUID());
            } else {
                DamageCalc.dealSkillDamage(owner, target,
                        (float)(320 + (pending.level - 1) * 72 + pending.sp * 2.80), Kind.MAGIC, 3);
                com.myriad.combat.SilenceSystem.apply(target, 30);
            }
        }
        PULLS.put(owner.getUUID(), new PullState(pending.center, now + 12, pulled));
        level.sendParticles(JADE, pending.center.x, pending.center.y + .5, pending.center.z, 90, 4.5, .6, 4.5, .08);
        level.playSound(null, pending.center.x, pending.center.y, pending.center.z, SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, .8F, 1.35F);
    }

    private static void tickPull(ServerPlayer owner, ServerLevel level, long now) {
        PullState pull = PULLS.get(owner.getUUID());
        if (pull == null) return;
        if (now >= pull.until) { PULLS.remove(owner.getUUID()); return; }
        for (UUID id : pull.targets) {
            Entity raw = level.getEntity(id);
            if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target)) continue;
            Vec3 delta = pull.center.subtract(target.position());
            if (delta.horizontalDistanceSqr() > .25) {
                Vec3 h = new Vec3(delta.x, 0, delta.z).normalize().scale(.42);
                target.setDeltaMovement(h.x, Math.max(target.getDeltaMovement().y, .12), h.z);
            }
        }
    }

    public static boolean isSilenced(ServerPlayer player) {
        return com.myriad.combat.SilenceSystem.isSilenced(player);
    }
    /** Remove only this target's silence; do not clear its own fields/contracts or allied bonuses. */
    public static void cleanseSilence(ServerPlayer player) {
        com.myriad.combat.SilenceSystem.cleanse(player);
    }

    /** 共鸣只抵消下一次有效伤害，随后立即消耗。 */
    public static void reduceResonanceDamage(LivingEntity victim, LivingIncomingDamageEvent event) {
        Long until = RESONANCE.get(victim.getUUID());
        if (until == null || until <= victim.level().getGameTime() || event.getAmount() <= 0) return;
        event.getContainer().setNewDamage(event.getAmount() * .80F);
        RESONANCE.remove(victim.getUUID());
        if (victim instanceof ServerPlayer player) {
            PlayerDataProvider.get(player).setBuff("yunsheng_passive", 0);
            PlayerDataProvider.syncToClient(player);
        }
    }

    private static void registerProtectedTarget(ServerPlayer owner, LivingEntity target, long now) {
        if (!ally(owner, target)) return;
        ContractSequence sequence = CONTRACTS.computeIfAbsent(owner.getUUID(), id -> new ContractSequence());
        if (target.getUUID().equals(sequence.target) && now - sequence.lastCast <= 100) sequence.count++;
        else { sequence.target = target.getUUID(); sequence.count = 1; }
        sequence.lastCast = now;
        if (sequence.count >= 2) {
            sequence.count = 0;
            RESONANCE.put(target.getUUID(), now + 60);
            if (target instanceof ServerPlayer player) {
                PlayerDataProvider.get(player).setBuff("yunsheng_passive", 60);
                PlayerDataProvider.syncToClient(player);
            }
        }
    }

    private static void revealAround(ServerLevel level, ServerPlayer owner, Vec3 center, double radius, int ticks) {
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center, center).inflate(radius),
                e -> com.myriad.combat.SkillTargets.alive(e) && e != owner && !ally(owner, e))) {
            enemy.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, false));
        }
    }

    private static LivingEntity lookedTarget(ServerLevel level, ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        LivingEntity best = null;
        double bestAlong = range + 1;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                e -> e != caster && com.myriad.combat.SkillTargets.alive(e))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > range) continue;
            double off = to.subtract(look.scale(along)).length();
            if (off <= Math.max(.85, target.getBbWidth() * .7) && along < bestAlong && caster.hasLineOfSight(target)) {
                best = target;
                bestAlong = along;
            }
        }
        return best;
    }

    private static boolean ally(ServerPlayer caster, LivingEntity other) {
        return com.myriad.combat.SkillTargets.ally(caster, other);
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, DustParticleOptions dust, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(dust, center.x + Math.cos(a) * radius, center.y + .1,
                    center.z + Math.sin(a) * radius, 1, 0, .015, 0, 0);
        }
    }

    private static void ringTo(ServerLevel level, ServerPlayer player, Vec3 center, double radius,
            DustParticleOptions dust, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(player, dust, false, false,
                    center.x + Math.cos(a) * radius, center.y + .1,
                    center.z + Math.sin(a) * radius, 1, 0, .01, 0, 0);
        }
    }

    private static Identifier auraId(UUID owner) {
        return Identifier.fromNamespaceAndPath("myriad", "yunsheng_aura_" + owner.toString().replace('-', '_'));
    }

    public static void clearFor(ServerPlayer owner) {
        UUID ownerId = owner.getUUID();
        CRANES.values().removeIf(c -> c.owner.equals(ownerId));
        BEACONS.remove(ownerId);
        PENDING_CHORUS.remove(ownerId);
        PULLS.remove(ownerId);
        CONTRACTS.remove(ownerId);
        Set<UUID> recipients = AURA_TARGETS.remove(ownerId);
        if (recipients != null) for (UUID targetId : recipients) {
            ServerPlayer target = owner.level().getServer().getPlayerList().getPlayer(targetId);
            if (target != null) {
                var cdr = target.getAttribute(ModAttributes.COOLDOWN_REDUCTION);
                if (cdr != null) cdr.removeModifier(auraId(ownerId));
            }
        }
        com.myriad.combat.SilenceSystem.clearFor(ownerId);
        SLOW_IMMUNITY.remove(ownerId);
        RESONANCE.remove(ownerId);
    }

    private static final class CraneState {
        final UUID owner, target; final Vec3 start, controlOffset; Vec3 pos;
        final long startedAt, arriveAt, expiresAt; final int level; final double sp;
        CraneState(UUID owner, UUID target, Vec3 start, Vec3 controlOffset, long startedAt,
                long arriveAt, int level, double sp) {
            this.owner = owner; this.target = target; this.start = start; this.pos = start;
            this.controlOffset = controlOffset; this.startedAt = startedAt; this.arriveAt = arriveAt;
            this.expiresAt = arriveAt + 20; this.level = level; this.sp = sp;
        }
    }
    private static final class BeaconState {
        final Vec3 center; final long until; final int level; final double sp; final Set<UUID> triggered = new HashSet<>();
        BeaconState(Vec3 center, long until, int level, double sp) { this.center = center; this.until = until; this.level = level; this.sp = sp; }
    }
    private record PendingChorus(Vec3 center, long until, int level, double sp) { }
    private record PullState(Vec3 center, long until, List<UUID> targets) { }
    private static final class ContractSequence { UUID target; int count; long lastCast; }
}
