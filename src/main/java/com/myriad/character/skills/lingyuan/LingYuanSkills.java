package com.myriad.character.skills.lingyuan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
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
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 御剑宗师·凌渊：剑罡、飞剑、剑势格挡、破绽与归宗剑阵均由服务端维护。 */
public final class LingYuanSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    private final Type type;
    private static final Map<UUID, Long> SWORD_FORM_UNTIL = new HashMap<>();
    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final Map<UUID, Long> FLAWS = new HashMap<>();
    private static final Map<UUID, List<SwordProjectile>> SWORD_PROJECTILES = new HashMap<>();
    /** 凌渊 UUID -> 最近一次造成伤害的目标 UUID。 */
    private static final Map<UUID, UUID> LAST_ATTACK_TARGET = new HashMap<>();
    /** 凌渊 UUID -> 最近一次实际造成伤害的游戏刻。 */
    private static final Map<UUID, Long> LAST_ATTACK_TIME = new HashMap<>();
    /** 自动飞剑伤害不刷新“玩家最近造成伤害”的 10 秒追踪窗口。 */
    private static final Set<UUID> AUTO_SWORD_DAMAGE = new HashSet<>();
    /** 每把常驻飞剑均有独立冷却与飞行阶段。 */
    private static final Map<UUID, List<FlyingSwordState>> FLYING_SWORD_STATES = new HashMap<>();
    /** 同一凌渊的常驻飞剑依次出击，任意两把起飞至少间隔 10 tick。 */
    private static final Map<UUID, Long> NEXT_FLYING_SWORD_LAUNCH_AT = new HashMap<>();
    private static final long FLYING_SWORD_LAUNCH_INTERVAL = 16L;
    private static final double PASSIVE_OUTBOUND_SPEED = .065;
    private static final double PASSIVE_SLASH_SPEED = .13;
    private static final double PASSIVE_RETURN_SPEED = .04;
    /** 被动飞剑只允许最多四把同时处于飞出/斩击阶段，避免高剑数时攻击频率失控。 */
    private static final int PASSIVE_ACTIVE_SWORD_LIMIT = 4;
    /** 万剑诀弹体使用原始飞行数据。 */
    private static final double SWORD_PROJECTILE_SPEED = 1.50;
    private static final double SWORD_PROJECTILE_RANGE = 12.0;
    /** 万剑归宗：飞向选点后化形，并在剑阵内舞动 6 秒。 */
    private static final Map<UUID, UltimateState> ULTIMATE_STATES = new HashMap<>();
    /** ItemDisplay 不会保留可读取的 Transformation；缓存各模型的缩放和局部翻转，以便飞行时重算完整三维朝向。 */
    private static final Map<UUID, SwordVisualStyle> SWORD_VISUAL_STYLES = new HashMap<>();
    private static final int ULTIMATE_DURATION_TICKS = 120;
    private static final double ULTIMATE_CAST_RANGE = 15.0;
    /** 万剑归宗剑阵半径：以选定落点为中心，覆盖 10 格范围。 */
    private static final double ULTIMATE_DANCE_RADIUS = 10.0;
    private static final double ULTIMATE_ARRIVAL_SPEED = .10;
    private static final double ULTIMATE_SWORD_SPEED = .60;
    private static final double ULTIMATE_RETURN_SPEED = .06;
    private static final long ULTIMATE_TARGET_HIT_COOLDOWN = 40L;
    private static final float ULTIMATE_REPEAT_DAMAGE_MULTIPLIER = .30F;
    private static final net.minecraft.resources.Identifier SWORD_UNITY_ID =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("myriad", "lingyuan_sword_unity");

    public LingYuanSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "lingyuan_passive";
            case Q -> "poyunshi";
            case W -> "huaxingweijian";
            case E -> "wanjianjue";
            case R -> "sanqianguizong";
        }, switch (type) {
            case PASSIVE -> 0;
            case Q -> 200;
            case W -> 320;
            case E -> 160;
            case R -> 500;
        }, switch (type) {
            case PASSIVE, R -> 0.0F;
            case Q -> 72.0F;
            case W -> 108.0F;
            case E -> 90.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getCooldownTicks(int level) {
        return switch (type) {
            case Q -> Math.max(120, 200 - Math.max(0, level - 1) * 20);
            case W -> Math.max(240, 320 - Math.max(0, level - 1) * 20);
            case E -> Math.max(80, 160 - Math.max(0, level - 1) * 20);
            case R -> Math.max(300, 500 - Math.max(0, level - 1) * 50);
            default -> 0;
        };
    }

    @Override public String levelEffects(int level) {
        return switch (type) {
            case PASSIVE -> "每次普攻获得10点剑罡；剑罡每达到100点凝成1把飞剑，最多4把同时出击";
            case Q -> "Lv." + level + "：突进6格，伤害 " + (10 + 10 * (level - 1)) + "+60%物攻";
            case W -> "Lv." + level + "：剑势1.5秒，减伤并获得控制免疫";
            case E -> "Lv." + level + "：万剑伤害 " + (12 + 6 * (level - 1)) + "+"
                    + (30 + (level - 1)) + "%物攻/剑，附加破绽";
            case R -> "Lv." + level + "：飞剑立体舞动6秒；同剑同目标2秒CD，后续伤害降70%；人剑合一6秒";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        int lv = Math.max(1, data.skillLevel(getId()));
        double ad = player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
        if (type == Type.Q) {
            Vec3 dir = player.getLookAngle().multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0E-4) return;
            dir = dir.normalize();
            DASHES.put(player.getUUID(), new Dash(dir, player.position()));
            // 参考影袭：只推进水平方向 motion，绝不直接传送坐标或改写 Y 坐标。
            player.setDeltaMovement(dir.x * .92, player.getDeltaMovement().y, dir.z * .92);
            // 和影袭相同地同时通知客户端推进，避免玩家实体的服务端速度被客户端输入覆盖。
            PacketDistributor.sendToPlayer(player, new DashPayload((float) dir.x, 0.0F, (float) dir.z, 7));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .8F, 1.2F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, .65F, 1.45F);
        } else if (type == Type.W) {
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false));
            SWORD_FORM_UNTIL.put(player.getUUID(), level.getGameTime() + 30L);
            data.setBuff("huaxingweijian", 30);
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 72, .8, 1.0, .8, .08);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 32, .65, .9, .65, .04);
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 24, .55, .75, .55, .05);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, .8F, 1.15F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, .35F, 1.6F);
        } else if (type == Type.E) {
            Vec3 dir = player.getLookAngle().normalize();
            List<SwordProjectile> swords = SWORD_PROJECTILES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
            Map<UUID, Integer> volleyHits = new HashMap<>();
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(-32 + i * 16);
                Vec3 swordDir = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), dir.y,
                        dir.x * Math.sin(a) + dir.z * Math.cos(a)).normalize();
                Vec3 origin = player.position().add(0, player.getBbHeight() * .72, 0).add(swordDir.scale(.55));
                swords.add(new SwordProjectile(origin, swordDir, lv, (float) ad, volleyHits,
                        spawnVolleySwordVisual(level, origin, swordDir)));
            }
            level.sendParticles(ParticleTypes.CRIT, player.getX() + dir.x * 1.2, player.getY() + 1, player.getZ() + dir.z * 1.2, 24, .4, .4, .4, .04);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, .75F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, .8F, .92F);
        } else if (type == Type.R) {
            int swords = data.getFlyingSwords();
            if (swords <= 0) return;
            Vec3 center = ultimateTargetPoint(player);
            startUltimate(player, data, center, swords, (float) (100.0 + ad * .80));
            data.setFlyingSwords(0);
            data.setBuff("sanqianguizong", ULTIMATE_DURATION_TICKS);
            PlayerDataProvider.syncToClient(player);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(),
                    36, .8, 1.0, .8, .08);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, .7F, 1.5F);
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, .45F, 1.45F);
        }
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        tickDash(player, data);
        tickSwordProjectiles(player, data);
        long now = player.level().getGameTime();
        Long until = SWORD_FORM_UNTIL.get(player.getUUID());
        if (until != null && player.level().getGameTime() >= until) {
            SWORD_FORM_UNTIL.remove(player.getUUID());
            data.setBuff("huaxingweijian", 0);
        }
        if (data.getBuff("huaxingweijian") > 0) {
            player.removeEffect(MobEffects.SLOWNESS);
            player.removeEffect(MobEffects.WEAKNESS);
            player.removeEffect(MobEffects.LEVITATION);
            tickSwordFormAura(player, now);
        }
        AttributeInstance attackSpeed = player.getAttribute(ModAttributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            if (data.getBuff("sanqianguizong") > 0 && !attackSpeed.hasModifier(SWORD_UNITY_ID)) {
                attackSpeed.addTransientModifier(new AttributeModifier(SWORD_UNITY_ID, .5,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (data.getBuff("sanqianguizong") <= 0 && attackSpeed.hasModifier(SWORD_UNITY_ID)) {
                attackSpeed.removeModifier(SWORD_UNITY_ID);
            }
        }
        // 脱战满 10 秒后才开始衰减；此后每秒固定减少 10 点剑罡。
        if (now - data.getLastCombatGameTime() > 200 && now % 20 == 0) data.setSwordQi(data.getSwordQi() - 10);
        tickFlyingSword(player, data, now);
        tickUltimate(player, now);
    }

    /** 普攻命中后的剑心通明：独立结算直线穿透伤害，不读取或复用任何原版横扫伤害。 */
    public static void onBasicAttack(ServerPlayer player, LivingEntity struck, float normalDamage) {
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) return;
        addSwordQi(player, data, 10);
        // 剑罡仍由普攻命中获得；旧版“主目标身后 2.5 格穿透并对后续目标造成 50%伤害”已移除。
    }

    /** 剑势格挡首段伤害：完全免伤、生成三把飞剑，W 剩余冷却减半。 */
    public static void onSwordFormBlocked(ServerPlayer player, Entity source) {
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null || data.getBuff("huaxingweijian") <= 0) return;
        data.setBuff("huaxingweijian", 0);
        data.setFlyingSwords(data.getFlyingSwords() + 3);
        int cd = data.getCooldown("huaxingweijian");
        if (cd > 0) data.setCooldown("huaxingweijian", Math.max(1, cd / 2));
        ServerLevel level = player.level();
        Vec3 p = source == null ? player.position() : source.position();
        level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 30, .5, .8, .5, .08);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1F, .75F);
        PlayerDataProvider.syncToClient(player);
    }

    /** 清理玩家遗留的飞剑实体展示、投射物和剑阵状态。 */
    public static void clearFor(UUID playerId) {
        SWORD_FORM_UNTIL.remove(playerId);
        DASHES.remove(playerId);
        LAST_ATTACK_TARGET.remove(playerId);
        LAST_ATTACK_TIME.remove(playerId);
        AUTO_SWORD_DAMAGE.remove(playerId);
        NEXT_FLYING_SWORD_LAUNCH_AT.remove(playerId);

        List<SwordProjectile> projectiles = SWORD_PROJECTILES.remove(playerId);
        if (projectiles != null) {
            for (SwordProjectile sword : projectiles) discardSwordVisual(sword);
        }
        List<FlyingSwordState> flying = FLYING_SWORD_STATES.remove(playerId);
        if (flying != null) {
            for (FlyingSwordState sword : flying) discardFlyingSwordVisual(sword);
        }
        UltimateState ultimate = ULTIMATE_STATES.remove(playerId);
        if (ultimate != null) discardUltimate(ultimate);
    }

    /** 破绽对凌渊普攻、飞剑及技能伤害统一增伤。 */
    public static boolean isFlawed(LivingEntity target) {
        Long until = FLAWS.get(target.getUUID());
        if (until == null || target.level().getGameTime() >= until) {
            FLAWS.remove(target.getUUID());
            return false;
        }
        return true;
    }

    public static void rememberAttackTarget(ServerPlayer player, LivingEntity target) {
        if (!AUTO_SWORD_DAMAGE.contains(player.getUUID())
                && target != null && com.myriad.combat.SkillTargets.alive(target) && !ally(player, target)) {
            LAST_ATTACK_TARGET.put(player.getUUID(), target.getUUID());
            LAST_ATTACK_TIME.put(player.getUUID(), player.level().getGameTime());
        }
    }

    private static void tickDash(ServerPlayer player, PlayerData data) {
        Dash dash = DASHES.get(player.getUUID());
        if (dash == null) return;
        ServerLevel level = player.level();
        Vec3 previous = dash.last;
        dash.last = player.position();
        for (LivingEntity target : com.myriad.combat.DashHitbox.targets(player, previous, player.position(), 1.35, dash.hit)) {
            if (!dash.hit.add(target.getUUID())) continue;
            int lv = Math.max(1, data.skillLevel("poyunshi"));
            DamageCalc.dealSkillDamage(player, target, (float) (40 + 40 * (lv - 1)
                    + player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK) * 2.4), Kind.PHYSICAL, 0);
        }
        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 3, .12, .15, .12, .02);
        if (--dash.ticks <= 0) {
            DASHES.remove(player.getUUID());
            boolean hero = false;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(2.5),
                    e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !ally(player, e))) hero |= target instanceof ServerPlayer;
            if (hero) addSwordQi(player, data, 20);
        } else player.setDeltaMovement(dash.dir.x * .92, player.getDeltaMovement().y, dash.dir.z * .92);
    }

    /** 剑势持续期间的护体灵光；实体飞剑的收缩与竖立由 tickFlyingSword 统一维护。 */
    private static void tickSwordFormAura(ServerPlayer player, long now) {
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(),
                3, .48, .75, .48, .025);
        if (now % 3 == 0) {
            double phase = now * .22;
            for (int i = 0; i < 6; i++) {
                double angle = phase + Math.PI * 2 * i / 6.0;
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX() + Math.cos(angle) * .85,
                        player.getY() + .35 + (i % 3) * .45,
                        player.getZ() + Math.sin(angle) * .85,
                        1, 0, 0, 0, 0);
            }
        }
    }

    private static Vec3 ultimateTargetPoint(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        for (double distance = .25; distance <= ULTIMATE_CAST_RANGE; distance += .25) {
            Vec3 point = start.add(direction.scale(distance));
            BlockPos pos = BlockPos.containing(point);
            if (level.isLoaded(pos) && level.getBlockState(pos).isSolid()) {
                return new Vec3(pos.getX() + .5, pos.getY() + 1.0, pos.getZ() + .5);
            }
        }
        return start.add(direction.scale(ULTIMATE_CAST_RANGE));
    }

    private static void startUltimate(ServerPlayer player, PlayerData data, Vec3 center,
            int swordCount, float fullDamage) {
        UUID playerId = player.getUUID();
        UltimateState previous = ULTIMATE_STATES.remove(playerId);
        if (previous != null) discardUltimate(previous);

        List<FlyingSwordState> held = FLYING_SWORD_STATES.remove(playerId);
        List<UltimateSword> swords = new ArrayList<>();
        for (int i = 0; i < swordCount; i++) {
            double angle = Math.PI * 2 * i / Math.max(1, swordCount);
            Vec3 fallback = player.position().add(Math.cos(angle) * 2.0, 1.05, Math.sin(angle) * 2.0);
            FlyingSwordState heldSword = held != null && i < held.size() ? held.get(i) : null;
            Vec3 start = heldSword != null && heldSword.pos != null ? heldSword.pos : fallback;
            if (heldSword != null) discardFlyingSwordVisual(heldSword);
            Vec3 arrival = center.add(Math.cos(angle) * .7, .75 + (i % 3) * .25, Math.sin(angle) * .7);
            double side = i % 2 == 0 ? 1.0 : -1.0;
            swords.add(new UltimateSword(start, arrival,
                    curveControl(start, arrival, side, 1.8 + (i % 3) * .35)));
        }
        if (held != null) {
            for (int i = swordCount; i < held.size(); i++) discardFlyingSwordVisual(held.get(i));
        }
        NEXT_FLYING_SWORD_LAUNCH_AT.remove(playerId);
        float damage = fullDamage * (swordCount < 4 ? .5F : 1.0F);
        ULTIMATE_STATES.put(playerId, new UltimateState(center, damage, swords));
    }

    private static void tickUltimate(ServerPlayer player, long now) {
        UltimateState state = ULTIMATE_STATES.get(player.getUUID());
        if (state == null) return;
        ServerLevel level = player.level();
        if (!player.isAlive()) {
            discardUltimate(state);
            ULTIMATE_STATES.remove(player.getUUID());
            return;
        }

        if (!state.dancing) {
            boolean allArrived = true;
            for (UltimateSword sword : state.swords) {
                if (sword.progress >= 1.0) continue;
                sword.progress = Math.min(1.0, sword.progress + ULTIMATE_ARRIVAL_SPEED);
                sword.pos = quadraticBezier(sword.start, sword.control, sword.arrival, sword.progress);
                level.sendParticles(ParticleTypes.END_ROD, sword.pos.x, sword.pos.y, sword.pos.z,
                        2, .025, .025, .025, .02);
                level.sendParticles(ParticleTypes.CRIT, sword.pos.x, sword.pos.y, sword.pos.z,
                        1, .015, .015, .015, .01);
                if (sword.progress < 1.0) {
                    allArrived = false;
                } else {
                    sword.pos = sword.arrival;
                }
            }
            if (!allArrived) return;

            state.dancing = true;
            state.danceUntil = now + ULTIMATE_DURATION_TICKS;
            for (UltimateSword sword : state.swords) {
                sword.destination = randomDancePoint(level, state.center);
                Vec3 direction = sword.destination.subtract(sword.pos);
                sword.visual = spawnUltimateSwordVisual(level, sword.pos,
                        direction.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : direction.normalize());
            }
            level.sendParticles(ParticleTypes.END_ROD, state.center.x, state.center.y + 1, state.center.z,
                    64, ULTIMATE_DANCE_RADIUS * .55, 1.5, ULTIMATE_DANCE_RADIUS * .55, .06);
            level.playSound(null, state.center.x, state.center.y, state.center.z,
                    SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, .8F, 1.25F);
            return;
        }

        if (now >= state.danceUntil) {
            discardUltimate(state);
            ULTIMATE_STATES.remove(player.getUUID());
            return;
        }

        for (UltimateSword sword : state.swords) {
            Vec3 movement = sword.destination.subtract(sword.pos);
            if (movement.lengthSqr() < .20) {
                sword.destination = randomDancePoint(level, state.center);
                movement = sword.destination.subtract(sword.pos);
            }
            Vec3 step = movement.length() <= ULTIMATE_SWORD_SPEED ? movement
                    : movement.normalize().scale(ULTIMATE_SWORD_SPEED);
            sword.pos = sword.pos.add(step);
            if (sword.visual != null && sword.visual.isAlive()) {
                sword.visual.setPos(sword.pos.x, sword.pos.y, sword.pos.z);
                // 三维飞行每 tick 都按真实移动方向重算俯仰与偏航，剑尖始终朝向速度方向。
                if (step.lengthSqr() > 1.0E-4) orientSword(sword.visual, step.normalize());
            }
            level.sendParticles(ParticleTypes.END_ROD, sword.pos.x, sword.pos.y, sword.pos.z,
                    1, .012, .012, .012, .008);

            AABB contact = new AABB(sword.pos, sword.pos).inflate(.65);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, contact,
                    entity -> entity != player && entity.isAlive() && !ally(player, entity))) {
                long nextHit = sword.targetHitCooldowns.getOrDefault(target.getUUID(), Long.MIN_VALUE);
                if (now < nextHit) continue;
                boolean repeatedHit = !sword.hitTargets.add(target.getUUID());
                float damage = state.damage * (repeatedHit ? ULTIMATE_REPEAT_DAMAGE_MULTIPLIER : 1.0F);
                DamageCalc.dealSkillDamage(player, target, damage, Kind.PHYSICAL, 3);
                sword.targetHitCooldowns.put(target.getUUID(), now + ULTIMATE_TARGET_HIT_COOLDOWN);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * .5,
                        target.getZ(), 7, .18, .28, .18, .04);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .3F, 1.6F);
            }
        }
    }

    private static Vec3 randomDancePoint(ServerLevel level, Vec3 center) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        double radius = Math.sqrt(level.getRandom().nextDouble()) * ULTIMATE_DANCE_RADIUS;
        // 在剑阵圆柱内同时随机水平位置与高度，使飞剑会明显上冲、俯冲而非平面盘旋。
        return center.add(Math.cos(angle) * radius, .35 + level.getRandom().nextDouble() * 4.65,
                Math.sin(angle) * radius);
    }

    private static void discardUltimate(UltimateState state) {
        for (UltimateSword sword : state.swords) {
            if (sword.visual != null) SWORD_VISUAL_STYLES.remove(sword.visual.getUUID());
            if (sword.visual != null && sword.visual.isAlive()) sword.visual.discard();
        }
    }

    private static void tickFlyingSword(ServerPlayer player, PlayerData data, long now) {
        ServerLevel level = player.level();
        int count = data.getFlyingSwords();
        List<FlyingSwordState> states = FLYING_SWORD_STATES.computeIfAbsent(
                player.getUUID(), ignored -> new ArrayList<>());
        while (states.size() < count) {
            int index = states.size();
            // 每把仍按自身命中时刻独立计算 40 tick 冷却；实际起飞顺序由 10 tick 全体节拍控制。
            states.add(new FlyingSwordState(now + 8L, index % 2 == 0 ? 1.0 : -1.0));
        }
        while (states.size() > count) {
            FlyingSwordState removed = states.remove(states.size() - 1);
            discardFlyingSwordVisual(removed);
        }
        if (states.isEmpty()) {
            FLYING_SWORD_STATES.remove(player.getUUID());
            NEXT_FLYING_SWORD_LAUNCH_AT.remove(player.getUUID());
            return;
        }
        UUID recentTargetId = LAST_ATTACK_TARGET.get(player.getUUID());
        long nextLaunchAt = NEXT_FLYING_SWORD_LAUNCH_AT.getOrDefault(player.getUUID(), now);
        boolean swordForm = data.getBuff("huaxingweijian") > 0;
        int activeAttackers = 0;
        for (FlyingSwordState state : states) {
            if (state.phase == FlyingSwordState.OUTBOUND || state.phase == FlyingSwordState.SLASHING) {
                activeAttackers++;
            }
        }
        for (int index = 0; index < states.size(); index++) {
            FlyingSwordState sword = states.get(index);
            double orbitAngle = now * .14 + Math.PI * 2 * index / states.size();
            Vec3 orbitPos = player.position().add(Math.cos(orbitAngle) * 2.0, 1.05,
                    Math.sin(orbitAngle) * 2.0);
            if (swordForm) {
                double contraction = Math.min(1.0, Math.max(0.0,
                        (30.0 - data.getBuff("huaxingweijian")) / 10.0));
                double guardRadius = 2.0 - contraction * 1.1;
                double guardAngle = now * .10 + Math.PI * 2 * index / states.size();
                Vec3 guardPos = player.position().add(Math.cos(guardAngle) * guardRadius,
                        .65 + (index % 3) * .38, Math.sin(guardAngle) * guardRadius);
                if (!sword.guarding || sword.visual == null || !sword.visual.isAlive()) {
                    discardFlyingSwordVisual(sword);
                    sword.visual = spawnSlashSwordVisual(level, guardPos, new Vec3(0, 1, 0));
                }
                sword.guarding = true;
                sword.phase = FlyingSwordState.ORBIT;
                sword.targetId = null;
                sword.pos = guardPos;
                sword.visual.setPos(guardPos.x, guardPos.y, guardPos.z);
                orientSword(sword.visual, new Vec3(0, 1, 0));
                continue;
            } else if (sword.guarding) {
                discardFlyingSwordVisual(sword);
                sword.guarding = false;
                sword.phase = FlyingSwordState.ORBIT;
                sword.targetId = null;
                sword.nextAttackAt = Math.max(sword.nextAttackAt, now + 2L);
            }
            if (sword.phase == FlyingSwordState.ORBIT) {
                sword.pos = orbitPos;
                Vec3 tangent = new Vec3(-Math.sin(orbitAngle), 0, Math.cos(orbitAngle));
                if (sword.visual == null || !sword.visual.isAlive()) {
                    sword.visual = spawnOrbitSwordVisual(level, orbitPos, tangent);
                } else {
                    sword.visual.setPos(orbitPos.x, orbitPos.y, orbitPos.z);
                    orientSword(sword.visual, tangent);
                }
                Entity entity = recentTargetId == null ? null : level.getEntity(recentTargetId);
                if (activeAttackers < PASSIVE_ACTIVE_SWORD_LIMIT
                        && now >= sword.nextAttackAt && now >= nextLaunchAt
                        && entity instanceof LivingEntity target
                        && canSwordTrack(player, target, now)) {
                    // 离开环绕位时实体化为剑光，接近目标后再重新显形下斩。
                    discardFlyingSwordVisual(sword);
                    sword.targetId = target.getUUID();
                    sword.phase = FlyingSwordState.OUTBOUND;
                    sword.progress = 0;
                    sword.start = orbitPos;
                    sword.control = curveControl(orbitPos,
                            swordApproachPoint(player, target, sword.curveSide), sword.curveSide, 2.2);
                    activeAttackers++;
                    nextLaunchAt = now + FLYING_SWORD_LAUNCH_INTERVAL;
                    NEXT_FLYING_SWORD_LAUNCH_AT.put(player.getUUID(), nextLaunchAt);
                }
            } else if (sword.phase == FlyingSwordState.OUTBOUND) {
                Entity entity = sword.targetId == null ? null : level.getEntity(sword.targetId);
                if (!(entity instanceof LivingEntity target) || !canSwordTrack(player, target, now)) {
                    beginSwordReturn(sword, player);
                    continue;
                }
                // 先飞到目标斜上方，不在接近阶段造成伤害。
                Vec3 destination = swordApproachPoint(player, target, sword.curveSide);
                sword.progress = Math.min(1.0, sword.progress + PASSIVE_OUTBOUND_SPEED);
                sword.pos = quadraticBezier(sword.start, sword.control, destination, sword.progress);
                level.sendParticles(ParticleTypes.END_ROD, sword.pos.x, sword.pos.y, sword.pos.z,
                        2, .025, .025, .025, .025);
                level.sendParticles(ParticleTypes.CRIT, sword.pos.x, sword.pos.y, sword.pos.z,
                        1, .015, .015, .015, .01);
                if (sword.progress >= .98 || sword.pos.distanceToSqr(destination) <= .65) {
                    sword.pos = destination;
                    sword.visual = spawnAutoAttackSwordVisual(level, sword.pos,
                            slantedDownwardAim(player, target, sword.pos));
                    sword.start = sword.pos;
                    sword.progress = 0;
                    sword.phase = FlyingSwordState.SLASHING;
                    level.sendParticles(ParticleTypes.END_ROD, destination.x, destination.y, destination.z,
                            6, .10, .12, .10, .025);
                }
            } else if (sword.phase == FlyingSwordState.SLASHING) {
                Entity entity = sword.targetId == null ? null : level.getEntity(sword.targetId);
                if (!(entity instanceof LivingEntity target) || !canSwordTrack(player, target, now)) {
                    beginSwordReturn(sword, player);
                    continue;
                }
                // 实体剑从目标上方向下斩落，只有斩击到达目标身体时才结算伤害。
                Vec3 destination = target.position().add(0, Math.max(.25, target.getBbHeight() * .35), 0);
                sword.progress = Math.min(1.0, sword.progress + PASSIVE_SLASH_SPEED);
                sword.pos = sword.start.lerp(destination, sword.progress);
                // 保持约 45° 斜向下，同时随目标移动更新水平朝向，避免剑身变成竖直下落。
                Vec3 direction = slantedDownwardAim(player, target, sword.pos);
                if (sword.visual != null && sword.visual.isAlive()) {
                    sword.visual.setPos(sword.pos.x, sword.pos.y, sword.pos.z);
                    if (direction.lengthSqr() > 1.0E-4) orientSword(sword.visual, direction.normalize());
                }
                level.sendParticles(ParticleTypes.CRIT, sword.pos.x, sword.pos.y, sword.pos.z,
                        2, .04, .04, .04, .02);
                if (sword.progress >= 1.0 || sword.pos.distanceToSqr(destination) <= .08) {
                    float damage = (float) (player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK) * .40);
                    AUTO_SWORD_DAMAGE.add(player.getUUID());
                    try {
                        DamageCalc.dealSkillDamage(player, target, damage, Kind.PHYSICAL, 4);
                    } finally {
                        AUTO_SWORD_DAMAGE.remove(player.getUUID());
                    }
                    sword.nextAttackAt = now + 40;
                    level.sendParticles(ParticleTypes.CRIT, destination.x, destination.y, destination.z,
                            8, .16, .24, .16, .04);
                    level.playSound(null, destination.x, destination.y, destination.z,
                            SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .30F, 1.7F);
                    // 命中后实体立即化光，再沿曲线返回。
                    discardFlyingSwordVisual(sword);
                    beginSwordReturn(sword, player);
                }
            } else if (sword.phase == FlyingSwordState.RETURNING) {
                Vec3 destination = player.position().add(0, 1.05, 0);
                sword.progress = Math.min(1.0, sword.progress + PASSIVE_RETURN_SPEED);
                sword.pos = quadraticBezier(sword.start, sword.control, destination, sword.progress);
                level.sendParticles(ParticleTypes.END_ROD, sword.pos.x, sword.pos.y, sword.pos.z,
                        2, .018, .018, .018, .015);
                if (sword.progress >= 1.0 || sword.pos.distanceToSqr(destination) <= .15) {
                    sword.phase = FlyingSwordState.ORBIT;
                    sword.targetId = null;
                    sword.pos = orbitPos;
                }
            }
        }
    }

    private static void beginSwordReturn(FlyingSwordState sword, ServerPlayer player) {
        discardFlyingSwordVisual(sword);
        sword.phase = FlyingSwordState.RETURNING;
        sword.progress = 0;
        sword.start = sword.pos;
        sword.control = curveControl(sword.pos, player.position().add(0, 1.05, 0),
                -sword.curveSide, 1.7);
    }

    /** 仅追踪 20 格内、且凌渊最近 10 秒内实际伤害过敌人的目标。 */
    private static boolean canSwordTrack(ServerPlayer player, LivingEntity target, long now) {
        long lastAttack = LAST_ATTACK_TIME.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        return com.myriad.combat.SkillTargets.alive(target) && !ally(player, target)
                && target.distanceToSqr(player) <= 20.0 * 20.0
                && lastAttack != Long.MIN_VALUE && now - lastAttack <= 200;
    }

    private static Vec3 swordApproachPoint(ServerPlayer player, LivingEntity target, double side) {
        Vec3 toward = target.position().subtract(player.position()).multiply(1, 0, 1);
        Vec3 lateral = new Vec3(-toward.z, 0, toward.x);
        if (lateral.lengthSqr() < 1.0E-4) lateral = new Vec3(1, 0, 0);
        return target.position().add(lateral.normalize().scale(.75 * side)).add(0, target.getBbHeight() + 2.0, 0);
    }

    private static Vec3 slantedDownwardAim(ServerPlayer player, LivingEntity target, Vec3 swordPos) {
        Vec3 horizontal = target.position().subtract(swordPos).multiply(1, 0, 1);
        if (horizontal.lengthSqr() < 1.0E-4) {
            horizontal = target.position().subtract(player.position()).multiply(1, 0, 1);
        }
        if (horizontal.lengthSqr() < 1.0E-4) horizontal = new Vec3(0, 0, 1);
        return horizontal.normalize().scale(.72).add(0, -.70, 0).normalize();
    }

    private static Vec3 curveControl(Vec3 start, Vec3 end, double side, double bend) {
        Vec3 delta = end.subtract(start);
        Vec3 horizontal = new Vec3(-delta.z, 0, delta.x);
        if (horizontal.lengthSqr() < 1.0E-4) horizontal = new Vec3(1, 0, 0);
        return start.add(end).scale(.5).add(horizontal.normalize().scale(bend * side)).add(0, .8, 0);
    }

    private static Vec3 quadraticBezier(Vec3 start, Vec3 control, Vec3 end, double t) {
        double u = 1.0 - t;
        return start.scale(u * u).add(control.scale(2 * u * t)).add(end.scale(t * t));
    }

    /** 万剑诀的五枚实体飞剑：沿各自扇形方向飞行，命中第一个敌人后消散。 */
    private static void tickSwordProjectiles(ServerPlayer player, PlayerData data) {
        List<SwordProjectile> swords = SWORD_PROJECTILES.get(player.getUUID());
        if (swords == null || swords.isEmpty()) return;
        ServerLevel level = player.level();
        swords.removeIf(sword -> {
            Vec3 next = sword.pos.add(sword.dir.scale(SWORD_PROJECTILE_SPEED));
            AABB box = new AABB(sword.pos, next).inflate(.75);
            LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !ally(player, e)).stream().findFirst().orElse(null);
            if (target != null) {
                float full = (float) (48 + 24 * (sword.level - 1)
                        + sword.ad * (1.20 + .04 * (sword.level - 1)));
                int hitNumber = sword.volleyHits.merge(target.getUUID(), 1, Integer::sum);
                DamageCalc.dealSkillDamage(player, target, hitNumber == 1 ? full : full * .20F, Kind.PHYSICAL, 2);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
                FLAWS.put(target.getUUID(), level.getGameTime() + 60);
                addSwordQi(player, data, 5);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * .6,
                        target.getZ(), 16, .25, .3, .25, .06);
                discardSwordVisual(sword);
                return true;
            }
            if (next.distanceToSqr(sword.origin) > SWORD_PROJECTILE_RANGE * SWORD_PROJECTILE_RANGE) {
                discardSwordVisual(sword);
                return true;
            }
            sword.pos = next;
            if (sword.visual != null && sword.visual.isAlive()) sword.visual.setPos(next.x, next.y, next.z);
            level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 2, .03, .03, .03, .02);
            return false;
        });
        if (swords.isEmpty()) SWORD_PROJECTILES.remove(player.getUUID());
    }

    private static ItemDisplay spawnSwordVisual(ServerLevel level, Vec3 pos, Vec3 dir) {
        return spawnSwordVisual(level, pos, dir, 0.0F, 2.1F);
    }

    /** 常驻环绕飞剑使用较小缩放，避免多把飞剑遮挡玩家本体。 */
    private static ItemDisplay spawnOrbitSwordVisual(ServerLevel level, Vec3 pos, Vec3 dir) {
        return spawnSwordVisual(level, pos, dir, 0.0F, 1.5F);
    }

    /** 万剑诀专用：由统一的本地 -Y 剑尖轴对准弹体前方。 */
    private static ItemDisplay spawnVolleySwordVisual(ServerLevel level, Vec3 pos, Vec3 dir) {
        return spawnSwordVisual(level, pos, dir, 0.0F, 2.1F);
    }

    /** 自动飞剑下斩专用：去掉影杀飞行模型的 +45° 局部滚转，保留钻石剑可见的斜置剑身。 */
    private static ItemDisplay spawnSlashSwordVisual(ServerLevel level, Vec3 pos, Vec3 dir) {
        return spawnSwordVisual(level, pos, dir, 0.0F, 2.1F);
    }

    /** 万剑归宗剑阵：与万剑诀共用剑尖在前的三维朝向。 */
    private static ItemDisplay spawnUltimateSwordVisual(ServerLevel level, Vec3 pos, Vec3 dir) {
        return spawnSwordVisual(level, pos, dir, 0.0F, 2.1F);
    }

    /** 自动追击的实体下斩飞剑：缩放与环绕一致，并由调用方实时传入斜向下目标方向。 */
    private static ItemDisplay spawnAutoAttackSwordVisual(ServerLevel level, Vec3 pos, Vec3 dir) {
        return spawnSwordVisual(level, pos, dir, 0.0F, 1.5F);
    }

    private static ItemDisplay spawnSwordVisual(ServerLevel level, Vec3 pos, Vec3 dir,
            float localRollDegrees, float scale) {
        if (EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof ItemDisplay display) {
            display.getSlot(0).set(new ItemStack(ModItems.LINGYUAN_SWORD.get()));
            display.setItemTransform(ItemDisplayContext.FIXED);
            display.setBillboardConstraints(BillboardConstraints.FIXED);
            SWORD_VISUAL_STYLES.put(display.getUUID(), new SwordVisualStyle(localRollDegrees, scale));
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(scale, scale, scale),
                    new Quaternionf().rotateZ((float) Math.toRadians(localRollDegrees))));
            // 直接以四元数把模型本地剑轴对齐真实三维速度，避免 Entity pitch 在俯冲时产生的轴向错位。
            orientSword(display, dir);
            display.setGlowColorOverride(0xFF75D9FF);
            display.setPos(pos.x, pos.y, pos.z);
            level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(display));
            return display;
        }
        return null;
    }

    private static void orientSword(ItemDisplay display, Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6) return;
        SwordVisualStyle style = SWORD_VISUAL_STYLES.get(display.getUUID());
        if (style == null) return;
        Vector3f direction = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
        // Durandal 的剑尖位于模型本地 -Y；直接让这一轴朝向实际飞行方向。
        Quaternionf aim = new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, direction.x, direction.y, direction.z);
        display.setYRot(0.0F);
        display.setXRot(0.0F);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0), aim, new Vector3f(style.scale(), style.scale(), style.scale()),
                new Quaternionf().rotateZ((float) Math.toRadians(style.localRollDegrees()))));
    }

    private static void discardSwordVisual(SwordProjectile sword) {
        if (sword.visual != null) SWORD_VISUAL_STYLES.remove(sword.visual.getUUID());
        if (sword.visual != null && sword.visual.isAlive()) sword.visual.discard();
    }

    private static void discardFlyingSwordVisual(FlyingSwordState sword) {
        if (sword.visual != null) SWORD_VISUAL_STYLES.remove(sword.visual.getUUID());
        if (sword.visual != null && sword.visual.isAlive()) sword.visual.discard();
        sword.visual = null;
    }

    private record SwordVisualStyle(float localRollDegrees, float scale) {
    }

    private static void addSwordQi(ServerPlayer player, PlayerData data, int amount) {
        data.setSwordQi(data.getSwordQi() + amount);
        while (data.getSwordQi() >= 100 && data.getFlyingSwords() < 12) {
            data.setSwordQi(data.getSwordQi() - 100);
            data.setFlyingSwords(data.getFlyingSwords() + 1);
        }
        PlayerDataProvider.syncToClient(player);
    }

    private static void hitRadius(ServerLevel level, ServerPlayer player, Vec3 center, double radius, float damage, boolean magic) {
        AABB box = new AABB(center, center).inflate(radius, 2.5, radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && com.myriad.combat.SkillTargets.alive(e) && !ally(player, e))) {
            DamageCalc.dealSkillDamage(player, target, damage, magic ? Kind.MAGIC : Kind.PHYSICAL, 4);
        }
    }

    private static boolean ally(ServerPlayer player, LivingEntity other) {
        return com.myriad.combat.SkillTargets.ally(player, other);
    }

    private static final class Dash {
        final Vec3 dir;
        Vec3 last;
        int ticks = 6;
        final Set<UUID> hit = new HashSet<>();
        Dash(Vec3 dir, Vec3 last) { this.dir = dir; this.last = last; }
    }

    private static final class SwordProjectile {
        final Vec3 origin;
        Vec3 pos;
        final Vec3 dir;
        final int level;
        final float ad;
        final Map<UUID, Integer> volleyHits;
        final ItemDisplay visual;
        SwordProjectile(Vec3 pos, Vec3 dir, int level, float ad, Map<UUID, Integer> volleyHits, ItemDisplay visual) {
            this.origin = pos;
            this.pos = pos;
            this.dir = dir;
            this.level = level;
            this.ad = ad;
            this.volleyHits = volleyHits;
            this.visual = visual;
        }
    }

    private static final class FlyingSwordState {
        static final int ORBIT = 0;
        static final int OUTBOUND = 1;
        static final int SLASHING = 2;
        static final int RETURNING = 3;
        Vec3 pos;
        Vec3 start;
        Vec3 control;
        UUID targetId;
        long nextAttackAt;
        double progress;
        final double curveSide;
        int phase = ORBIT;
        ItemDisplay visual;
        boolean guarding;
        FlyingSwordState(long nextAttackAt, double curveSide) {
            this.nextAttackAt = nextAttackAt;
            this.curveSide = curveSide;
        }
    }

    private static final class UltimateState {
        final Vec3 center;
        final float damage;
        final List<UltimateSword> swords;
        boolean dancing;
        long danceUntil;

        UltimateState(Vec3 center, float damage, List<UltimateSword> swords) {
            this.center = center;
            this.damage = damage;
            this.swords = swords;
        }
    }

    private static final class UltimateSword {
        final Vec3 start;
        final Vec3 arrival;
        final Vec3 control;
        final Map<UUID, Long> targetHitCooldowns = new HashMap<>();
        final Set<UUID> hitTargets = new HashSet<>();
        Vec3 pos;
        Vec3 destination;
        double progress;
        ItemDisplay visual;

        UltimateSword(Vec3 start, Vec3 arrival, Vec3 control) {
            this.start = start;
            this.arrival = arrival;
            this.control = control;
            this.pos = start;
        }
    }
}
