package com.myriad.character.skills.lige;

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
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.combat.WindWallSystem;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 影弦·离歌：远程普攻、追弦印记、穿透箭、回风步与追踪箭雨。 */
public final class LiGeSkills extends Skill {
    private static final float DAMAGE_MULTIPLIER = .85F;
    public enum Type { PASSIVE, Q, W, E, R }
    private enum ArrowKind { BASIC, BIND, ULTIMATE, SPLIT }

    private static final Identifier WIND_AS = Identifier.fromNamespaceAndPath("myriad", "lige_windstep_as");
    private static final DustParticleOptions CYAN_ARROW = new DustParticleOptions(0x62E6E8, 1.05F);
    private static final DustParticleOptions SHADOW_STRING = new DustParticleOptions(0x6F4BA8, .85F);
    private static final Map<UUID, Map<UUID, ChaseMark>> MARKS = new HashMap<>();
    private static final Map<UUID, ArmedShot> PIERCING_SHOTS = new HashMap<>();
    private static final Set<UUID> SLOW_SHOTS = new HashSet<>();
    private static final Map<UUID, DashState> DASHES = new HashMap<>();
    /** 本次施法时客户端正在按下的移动方向；只在网络处理调用栈内短暂存在。 */
    private static final Map<UUID, Vec3> REQUESTED_DASH_DIRECTIONS = new HashMap<>();
    private static final Map<UUID, Long> ATTACK_SPEED_UNTIL = new HashMap<>();
    private static final Map<UUID, ArrowState> ARROWS = new HashMap<>();
    private static final Map<UUID, BasicArrowState> BASIC_ARROWS = new HashMap<>();
    /** 粒子箭命中进入同步伤害事件时，临时携带原版弓蓄力倍率。 */
    private static final Map<UUID, BarrageState> BARRAGES = new HashMap<>();

    private final Type type;

    public LiGeSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "lige_passive";
            case Q -> "lieyujian";
            case W -> "huifengbu";
            case E -> "yingxianfu";
            case R -> "baixianheyi";
        }, switch (type) {
            case PASSIVE -> 0;
            case Q -> 100;
            case W -> 260;
            case E -> 320;
            case R -> 1500;
        }, switch (type) {
            case PASSIVE -> 0.0F;
            case Q -> 54.0F;
            case W -> 90.0F;
            case E -> 126.0F;
            case R -> 288.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }

    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "射手普攻按80%物攻结算；手持影弦弓按住右键拉弓、松开射出无实体粒子箭；攻速加快拉弓，蓄力影响箭速、射程与伤害；对20格外玩家仅造成5%伤害。"
                    + "同目标普攻叠3层追弦，每层伤害+4%；满层命中回复10%已损生命";
            case Q -> "立即刷新普攻；接下来3次普攻额外造成 " + (18 + (lv - 1) * 9)
                    + "+" + (45 + (lv - 1) * 5)
                    + "%物攻，每次命中后分裂3箭，各造成该次普攻25%伤害";
            case W -> "向移动方向位移5格，2秒攻速+" + (35 + (lv - 1) * 4) + "%；下次普攻减速";
            case E -> "束缚箭造成 " + (28 + (lv - 1) * 11) + "+50%物攻；近墙眩晕，否则减速";
            case R -> "4秒内发射 " + (8 + lv - 1) + " 支追踪箭；每支 "
                    + (35 + (lv - 1) * 12) + "+55%物攻";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        int lv = Math.max(1, data.skillLevel(getId()));
        float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        long now = level.getGameTime();
        if (type == Type.Q) {
            PIERCING_SHOTS.put(player.getUUID(), new ArmedShot(lv, ad, 3));
            com.myriad.combat.BasicAttackSystem.refreshCooldown(player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END,
                    SoundSource.PLAYERS, .72F, 1.38F);
        } else if (type == Type.W) {
            Vec3 direction = REQUESTED_DASH_DIRECTIONS.getOrDefault(player.getUUID(), Vec3.ZERO);
            // 没按移动键时才按面朝方向位移；侧移、后退、斜向均以当前按键为准。
            if (direction.lengthSqr() < .001) direction = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z);
            if (direction.lengthSqr() < .001) return;
            direction = direction.normalize();
            DASHES.put(player.getUUID(), new DashState(direction, 6));
            PacketDistributor.sendToPlayer(player, new DashPayload((float)direction.x, 0, (float)direction.z, 6),
                    new CustomPacketPayload[0]);
            var attackSpeed = player.getAttribute(ModAttributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.removeModifier(WIND_AS);
                attackSpeed.addTransientModifier(new AttributeModifier(WIND_AS, .35 + (lv - 1) * .04,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            ATTACK_SPEED_UNTIL.put(player.getUUID(), now + 40);
            SLOW_SHOTS.add(player.getUUID());
            data.setBuff("huifengbu", 40);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW,
                    SoundSource.PLAYERS, .72F, 1.55F);
        } else if (type == Type.E) {
            LivingEntity target = lookedEnemy(level, player, 15);
            if (target == null) return;
            ARROWS.put(UUID.randomUUID(), new ArrowState(player.getUUID(), target.getUUID(),
                    player.getEyePosition().add(player.getLookAngle().scale(.5)),
                    112 + (lv - 1) * 44 + ad * 2.0F, lv, ArrowKind.BIND, now + 60));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
                    SoundSource.PLAYERS, .85F, .85F);
        } else if (type == Type.R) {
            BARRAGES.put(player.getUUID(), new BarrageState(now + 80, now, 8 + lv - 1, lv, ad));
            data.setBuff("baixianheyi", 80);
            level.sendParticles(SHADOW_STRING, player.getX(), player.getY() + 1, player.getZ(), 45, .7, .8, .7, .04);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_QUICK_CHARGE_3,
                    SoundSource.PLAYERS, .9F, .72F);
        }
    }

    public static boolean hasTarget(ServerPlayer player, int slot) {
        return slot != CharacterType.SLOT_E || lookedEnemy(player.level(), player, 15) != null;
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.LIGE) return;
        ServerLevel level = player.level();
        long now = level.getGameTime();
        DashState dash = DASHES.get(player.getUUID());
        if (dash != null) {
            if (dash.remaining-- <= 0) DASHES.remove(player.getUUID());
            else {
                player.setDeltaMovement(dash.direction.x * .9, player.getDeltaMovement().y, dash.direction.z * .9);
                level.sendParticles(SHADOW_STRING, player.getX(), player.getY() + .2, player.getZ(), 4, .25, .12, .25, .015);
            }
        }
        Long asUntil = ATTACK_SPEED_UNTIL.get(player.getUUID());
        if (asUntil != null && now >= asUntil) {
            ATTACK_SPEED_UNTIL.remove(player.getUUID());
            var attackSpeed = player.getAttribute(ModAttributes.ATTACK_SPEED);
            if (attackSpeed != null) attackSpeed.removeModifier(WIND_AS);
            data.setBuff("huifengbu", 0);
        }
        tickBarrage(player, data, level, now);
        tickBasicArrows(player, level);
        tickArrows(player, level, now);
        Map<UUID, ChaseMark> marks = MARKS.get(player.getUUID());
        if (marks != null) {
            marks.entrySet().removeIf(e -> now - e.getValue().lastHit > 40);
            if (marks.isEmpty()) {
                MARKS.remove(player.getUUID());
                data.setBuff("lige_passive", 0);
            } else {
                int visibleStacks = marks.values().stream()
                        .mapToInt(mark -> mark.stacks)
                        .max()
                        .orElse(0);
                data.setBuff("lige_passive", visibleStacks);
            }
        }
    }

    private static void tickBasicArrows(ServerPlayer owner, ServerLevel level) {
        BASIC_ARROWS.entrySet().removeIf(entry -> {
            BasicArrowState arrow = entry.getValue();
            if (!arrow.owner.equals(owner.getUUID())) return false;
            if (arrow.remaining <= 0.0 || arrow.velocity.lengthSqr() < 1.0E-5) return true;

            Vec3 intended = arrow.pos.add(arrow.velocity);
            HitResult blockHit = level.clip(new ClipContext(arrow.pos, intended,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 end = blockHit.getType() == HitResult.Type.MISS ? intended : blockHit.getLocation();
            if (WindWallSystem.blocksProjectile(level, arrow.pos, end)
                    || com.myriad.character.skills.feiyu.FeiYuSkills.blocksProjectile(level, owner, arrow.pos, end)) {
                return true;
            }

            LivingEntity hit = null;
            double nearest = Double.MAX_VALUE;
            AABB swept = new AABB(arrow.pos, end).inflate(.55);
            for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, swept,
                    entity -> entity != owner && entity.isAlive() && !ally(owner, entity))) {
                java.util.Optional<Vec3> contact = candidate.getBoundingBox().inflate(.28).clip(arrow.pos, end);
                if (contact.isEmpty()) continue;
                double distance = arrow.pos.distanceToSqr(contact.get());
                if (distance < nearest) {
                    nearest = distance;
                    hit = candidate;
                }
            }

            Vec3 trailEnd = hit == null ? end : hit.getBoundingBox().getCenter();
            double traveled = arrow.pos.distanceTo(trailEnd);
            int points = Math.max(2, (int)Math.ceil(traveled * 3.0));
            for (int i = 1; i <= points; i++) {
                Vec3 point = arrow.pos.lerp(trailEnd, i / (double)points);
                level.sendParticles(CYAN_ARROW, point.x, point.y, point.z, 1, .018, .018, .018, 0);
                if ((i & 1) == 0) level.sendParticles(ParticleTypes.END_ROD,
                        point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }

            if (hit != null) {
                try {
                    DamageCalc.markBasicAttack(owner, hit, arrow.damageMultiplier);
                    hit.hurt(hit.damageSources().playerAttack(owner), 1.0F);
                    DamageCalc.clearBasicAttack(owner, hit);
                } finally {
                    DamageCalc.clearBasicAttack(owner, hit);
                }
                Vec3 impact = hit.getBoundingBox().getCenter();
                level.sendParticles(CYAN_ARROW, impact.x, impact.y, impact.z, 18, .28, .28, .28, .035);
                level.playSound(null, impact.x, impact.y, impact.z,
                        SoundEvents.ARROW_HIT, SoundSource.PLAYERS, .75F, 1.1F);
                return true;
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                level.sendParticles(CYAN_ARROW, end.x, end.y, end.z, 7, .12, .12, .12, .02);
                return true;
            }

            arrow.pos = intended;
            arrow.remaining -= traveled;
            arrow.velocity = arrow.velocity.scale(.99).add(0, -.05, 0);
            return false;
        });
    }

    private static void tickBarrage(ServerPlayer owner, PlayerData data, ServerLevel level, long now) {
        BarrageState barrage = BARRAGES.get(owner.getUUID());
        if (barrage == null) return;
        if (now >= barrage.until || barrage.shotsRemaining <= 0) {
            BARRAGES.remove(owner.getUUID());
            data.setBuff("baixianheyi", 0);
            return;
        }
        if (now < barrage.nextShot) return;
        LivingEntity target = selectBarrageTarget(level, owner, barrage);
        barrage.nextShot = now + Math.max(4, 80 / Math.max(1, 8 + barrage.level - 1));
        if (target == null) return;
        barrage.shotsRemaining--;
        barrage.hits.merge(target.getUUID(), 1, Integer::sum);
        ARROWS.put(UUID.randomUUID(), new ArrowState(owner.getUUID(), target.getUUID(),
                owner.getEyePosition().add(0, .2, 0),
                140 + (barrage.level - 1) * 48 + barrage.ad * 2.20F,
                barrage.level, ArrowKind.ULTIMATE, now + 60));
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, .55F, 1.3F + owner.getRandom().nextFloat() * .25F);
    }

    private static void tickArrows(ServerPlayer owner, ServerLevel level, long now) {
        ARROWS.entrySet().removeIf(entry -> {
            ArrowState arrow = entry.getValue();
            if (!arrow.owner.equals(owner.getUUID())) return false;
            Entity raw = level.getEntity(arrow.target);
            if (!(raw instanceof LivingEntity target) || !com.myriad.combat.SkillTargets.alive(target) || now >= arrow.expiresAt) return true;
            Vec3 aim = target.getBoundingBox().getCenter();
            Vec3 delta = aim.subtract(arrow.pos);
            if (delta.length() <= .75) {
                if (WindWallSystem.blocksProjectile(level, arrow.pos, aim)
                        || com.myriad.character.skills.feiyu.FeiYuSkills.blocksProjectile(level, owner, arrow.pos, aim)) return true;
                float damage = arrow.damage;
                if (arrow.kind == ArrowKind.BASIC) {
                    DamageCalc.markBasicAttack(owner, target);
                    target.hurt(target.damageSources().playerAttack(owner), 1.0F);
                    DamageCalc.clearBasicAttack(owner, target);
                } else {
                    if (arrow.kind == ArrowKind.ULTIMATE && target.getHealth() / target.getMaxHealth() < .35F) damage *= 1.20F;
                    DamageCalc.dealSkillDamage(owner, target, damage * DAMAGE_MULTIPLIER, Kind.PHYSICAL,
                            arrow.kind == ArrowKind.BIND ? 2 : arrow.kind == ArrowKind.ULTIMATE ? 3 : 4);
                }
                if (arrow.kind == ArrowKind.BIND) {
                    if (hasNearbyWall(level, target, 4)) {
                        int stun = 24 + Math.min(6, (arrow.level - 1) * 2);
                        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stun, 255, false, false, false));
                    } else target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false, false));
                }
                level.sendParticles(CYAN_ARROW, aim.x, aim.y, aim.z, 18, .28, .28, .28, .035);
                level.playSound(null, aim.x, aim.y, aim.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, .75F, 1.1F);
                return true;
            }
            Vec3 forward = delta.normalize();
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            arrow.phase += .5;
            Vec3 next = arrow.pos.add(forward.scale(1.05)).add(side.scale(Math.sin(arrow.phase) * .055));
            if (WindWallSystem.blocksProjectile(level, arrow.pos, next)
                    || com.myriad.character.skills.feiyu.FeiYuSkills.blocksProjectile(level, owner, arrow.pos, next)) return true;
            arrow.pos = next;
            level.sendParticles(CYAN_ARROW, arrow.pos.x, arrow.pos.y, arrow.pos.z, 2, .035, .035, .035, .004);
            level.sendParticles(ParticleTypes.END_ROD, arrow.pos.x, arrow.pos.y, arrow.pos.z, 1, 0, 0, 0, 0);
            return false;
        });
    }

    /**
     * 原版弓式普攻粒子箭：按松开瞬间的视角直射，不锁定目标，也不生成原版 Arrow 实体。
     * @param power 原版弓蓄力倍率（0..1），同时影响初速、射程和伤害
     */
    public static boolean fireBasicArrow(ServerPlayer owner, float power, float damageMultiplier) {
        Vec3 direction = owner.getLookAngle();
        if (direction.lengthSqr() < 1.0E-6) return false;
        float clampedPower = net.minecraft.util.Mth.clamp(power, .1F, 1.0F);
        Vec3 origin = owner.getEyePosition().add(owner.getLookAngle().scale(.55));
        BASIC_ARROWS.put(UUID.randomUUID(), new BasicArrowState(owner.getUUID(), origin,
                direction.normalize().scale(3.0 * clampedPower), 40.0 * clampedPower,
                net.minecraft.util.Mth.clamp(damageMultiplier, .6F, 1.0F)));
        owner.level().sendParticles(SHADOW_STRING, origin.x, origin.y, origin.z,
                8, .08, .08, .08, .025);
        return true;
    }

    /** 普攻事件中的追弦、Q穿透和回风步减速均在最终普攻伤害建立后处理。 */
    public static void onBasicAttack(ServerPlayer attacker, LivingEntity target, LivingIncomingDamageEvent event) {
        long now = attacker.level().getGameTime();
        Map<UUID, ChaseMark> ownerMarks = MARKS.computeIfAbsent(attacker.getUUID(), id -> new HashMap<>());
        ChaseMark mark = ownerMarks.computeIfAbsent(target.getUUID(), id -> new ChaseMark());
        if (now - mark.lastHit > 40) mark.stacks = 0;
        mark.stacks = Math.min(3, mark.stacks + 1);
        mark.lastHit = now;
        event.getContainer().setNewDamage(event.getAmount() * (1F + mark.stacks * .04F));
        if (mark.stacks >= 3) {
            float missing = Math.max(0, attacker.getMaxHealth() - attacker.getHealth());
            if (missing > 0) com.myriad.combat.CombatTelemetry.heal(attacker, missing * .10F, "passive");
        }
        if (SLOW_SHOTS.remove(attacker.getUUID())) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false, false));
        }
        ArmedShot armed = PIERCING_SHOTS.get(attacker.getUUID());
        if (armed != null) {
            float bonus = 18 + (armed.level - 1) * 9
                    + armed.ad * (.45F + .05F * (armed.level - 1));
            event.getContainer().setNewDamage(event.getAmount() + bonus);
            fireSplitArrows(attacker, target, event.getAmount());
            if (armed.remaining <= 1) PIERCING_SHOTS.remove(attacker.getUUID());
            else PIERCING_SHOTS.put(attacker.getUUID(),
                    new ArmedShot(armed.level, armed.ad, armed.remaining - 1));
        }
        PlayerData data = PlayerDataProvider.get(attacker);
        data.setBuff("lige_passive", mark.stacks);
        // 远距压制只保留骚扰价值：超过20格命中玩家时，整次普攻（含Q强化）仅造成5%。
        if (target instanceof ServerPlayer && attacker.distanceToSqr(target) > 20.0 * 20.0) {
            event.getContainer().setNewDamage(event.getAmount() * .05F);
        }
        event.getContainer().setNewDamage(event.getAmount() * DAMAGE_MULTIPLIER);
    }

    private static void fireSplitArrows(ServerPlayer attacker, LivingEntity first, float empoweredAttackDamage) {
        ServerLevel level = attacker.level();
        Vec3 splitOrigin = first.getBoundingBox().getCenter();
        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class,
                first.getBoundingBox().inflate(6.0),
                e -> e != attacker && e != first && com.myriad.combat.SkillTargets.alive(e) && !ally(attacker, e))) {
            candidates.add(enemy);
        }
        // 敌方玩家优先，其次选择离命中点最近的目标。
        candidates.sort(Comparator
                .comparing((LivingEntity e) -> !(e instanceof ServerPlayer))
                .thenComparingDouble(e -> e.distanceToSqr(first)));
        float childDamage = Math.max(0.0F, empoweredAttackDamage);
        int fired = 0;
        for (LivingEntity enemy : candidates) {
            if (fired >= 3) break;
            ARROWS.put(UUID.randomUUID(), new ArrowState(attacker.getUUID(), enemy.getUUID(),
                    splitOrigin, childDamage, 1, ArrowKind.SPLIT, level.getGameTime() + 50));
            fired++;
        }
        level.sendParticles(CYAN_ARROW, splitOrigin.x, splitOrigin.y, splitOrigin.z,
                24, .35, .35, .35, .055);
        if (fired > 0) level.playSound(null, splitOrigin.x, splitOrigin.y, splitOrigin.z,
                SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, .7F, 1.55F);
    }

    private static LivingEntity selectBarrageTarget(ServerLevel level, ServerPlayer owner, BarrageState barrage) {
        return level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(20),
                        e -> e != owner && com.myriad.combat.SkillTargets.alive(e) && !ally(owner, e)
                                && barrage.hits.getOrDefault(e.getUUID(), 0) < 4 && owner.hasLineOfSight(e))
                .stream().min(Comparator.<LivingEntity>comparingDouble(e -> e.getHealth() / e.getMaxHealth())
                        .thenComparingDouble(e -> e.distanceToSqr(owner))).orElse(null);
    }

    private static LivingEntity lookedEnemy(ServerLevel level, ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition(); Vec3 look = caster.getLookAngle().normalize();
        LivingEntity best = null; double bestAlong = range + 1;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                e -> e != caster && com.myriad.combat.SkillTargets.alive(e) && !ally(caster, e))) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > range) continue;
            if (to.subtract(look.scale(along)).length() <= Math.max(.9, target.getBbWidth() * .75)
                    && along < bestAlong && caster.hasLineOfSight(target)) {
                best = target; bestAlong = along;
            }
        }
        return best;
    }

    private static boolean hasNearbyWall(ServerLevel level, LivingEntity target, int radius) {
        BlockPos center = target.blockPosition();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (x * x + z * z > radius * radius || (Math.abs(x) <= 1 && Math.abs(z) <= 1)) continue;
            for (int y = 0; y <= 2; y++) {
                BlockPos pos = center.offset(x, y, z);
                if (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) return true;
            }
        }
        return false;
    }

    private static boolean ally(ServerPlayer caster, LivingEntity other) {
        if (other == caster || com.myriad.game.JiarenManager.isFriendlyDummy(caster, other)) return true;
        GameManager manager = GameManager.get();
        Faction a = manager == null ? null : manager.factionOf(caster);
        Faction b = com.myriad.game.LaneSystem.factionOf(other);
        return a != null && a == b;
    }

    public static void clearFor(UUID ownerId) {
        MARKS.remove(ownerId); PIERCING_SHOTS.remove(ownerId); SLOW_SHOTS.remove(ownerId);
        DASHES.remove(ownerId); ATTACK_SPEED_UNTIL.remove(ownerId); BARRAGES.remove(ownerId);
        ARROWS.values().removeIf(a -> a.owner.equals(ownerId));
        BASIC_ARROWS.values().removeIf(a -> a.owner.equals(ownerId));
        REQUESTED_DASH_DIRECTIONS.remove(ownerId);
    }

    public static void setRequestedDashDirection(ServerPlayer player, float x, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(z)) return;
        Vec3 direction = new Vec3(x, 0, z);
        if (direction.lengthSqr() > 1.0E-6) {
            REQUESTED_DASH_DIRECTIONS.put(player.getUUID(), direction.normalize());
        }
    }

    public static void clearRequestedDashDirection(ServerPlayer player) {
        REQUESTED_DASH_DIRECTIONS.remove(player.getUUID());
    }

    public static void removeModifiers(ServerPlayer player) {
        var attackSpeed = player.getAttribute(ModAttributes.ATTACK_SPEED);
        if (attackSpeed != null) attackSpeed.removeModifier(WIND_AS);
    }

    private static final class ChaseMark { int stacks; long lastHit; }
    private record ArmedShot(int level, float ad, int remaining) { }
    private static final class DashState { final Vec3 direction; int remaining; DashState(Vec3 d, int r) { direction = d; remaining = r; } }
    private static final class ArrowState {
        final UUID owner, target; Vec3 pos; final float damage; final int level; final ArrowKind kind; final long expiresAt; double phase;
        ArrowState(UUID owner, UUID target, Vec3 pos, float damage, int level, ArrowKind kind, long expiresAt) {
            this.owner = owner; this.target = target; this.pos = pos; this.damage = damage; this.level = level; this.kind = kind; this.expiresAt = expiresAt;
        }
    }
    private static final class BasicArrowState {
        final UUID owner;
        Vec3 pos;
        Vec3 velocity;
        double remaining;
        final float damageMultiplier;

        BasicArrowState(UUID owner, Vec3 pos, Vec3 velocity, double remaining, float damageMultiplier) {
            this.owner = owner;
            this.pos = pos;
            this.velocity = velocity;
            this.remaining = remaining;
            this.damageMultiplier = damageMultiplier;
        }
    }
    private static final class BarrageState {
        final long until; long nextShot; int shotsRemaining; final int level; final float ad; final Map<UUID, Integer> hits = new HashMap<>();
        BarrageState(long until, long nextShot, int shotsRemaining, int level, float ad) {
            this.until = until; this.nextShot = nextShot; this.shotsRemaining = shotsRemaining; this.level = level; this.ad = ad;
        }
    }
}
