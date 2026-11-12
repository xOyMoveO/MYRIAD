package com.myriad.character.skills.qingluo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 丹心·青萝：治疗、护盾、牵丝伤害转移与灵木领域。 */
public final class QingLuoSkills extends Skill {
    public static final int HUXIN_PLACEMENT = 5;
    public enum Type { PASSIVE, Q, W, E, R }

    private final Type type;
    private static final Map<UUID, Long> PASSIVE_TARGET_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> Q_REGEN_UNTIL = new HashMap<>();
    private static final Map<UUID, ShieldState> SHIELDS = new HashMap<>();
    private static final Map<UUID, LinkState> LINKS = new HashMap<>();
    private static final Map<UUID, GroveState> GROVES = new HashMap<>();
    private static final Map<UUID, Long> GROVE_INVULNERABLE = new HashMap<>();
    private static final Map<UUID, OrbState> ORBS = new HashMap<>();
    private static final Map<UUID, PendingCast> PENDING_W = new HashMap<>();
    private static final Map<UUID, PendingCast> PENDING_R = new HashMap<>();
    private static final DustParticleOptions JADE_DUST = new DustParticleOptions(0x52E879, 1.18F);
    private static final DustParticleOptions JADE_LIGHT = new DustParticleOptions(0xB9FF9A, .72F);
    private static final DustParticleOptions GROVE_CORE = new DustParticleOptions(0x35F06A, 1.65F);

    public QingLuoSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "qingluo_passive";
            case Q -> "qingtengyu";
            case W -> "huxindanzhen";
            case E -> "qiansiduoe";
            case R -> "wanmuchunhui";
        }, switch (type) {
            case PASSIVE -> 0;
            case Q -> 160;
            case W -> 360;
            case E -> 400;
            case R -> 1400;
        }, switch (type) {
            case PASSIVE -> 0.0F;
            case Q -> 90.0F;
            case W -> 162.0F;
            case E -> 144.0F;
            case R -> 324.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getCooldownTicks(int level) {
        return type == Type.E ? Math.max(280, 400 - (Math.max(1, level) - 1) * 10) : super.getCooldownTicks(level);
    }
    @Override public String levelEffects(int level) {
        return switch (type) {
            case PASSIVE -> "低于60%生命的友方受技能保护时，回复3%已损生命并获得2秒10%移速（同目标4秒冷却）";
            case Q -> "治疗 " + (10 + (level - 1) * 8) + "+45%法强；敌方伤害 " + (12 + (level - 1) * 8) + "+45%法强";
            case W -> "半径4格丹阵，护盾 " + (10 + (level - 1) * 12) + "+45%法强，持续5秒";
            case E -> "牵丝4秒，转移 " + (20 + (level - 1) * 3) + "% 伤害至友方护盾";
            case R -> "半径8格领域，持续 " + (6 + Math.max(0, level - 1)) + " 秒，每秒治疗10+40%法强并回复4法力";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        int lv = Math.max(1, data.skillLevel(getId()));
        double sp = player.getAttributeValue(ModAttributes.SPELL_POWER);
        if (type == Type.Q) {
            for (int i = 0; i < 3; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2; double radius = 1.1 + level.getRandom().nextDouble() * .9;
                OrbState orb = new OrbState(player.getUUID(), player.position().add(Math.cos(angle) * radius, .8 + level.getRandom().nextDouble() * .7, Math.sin(angle) * radius), level.getGameTime() + i * 4L,
                        level.getGameTime() + 80, lv, sp);
                ORBS.put(UUID.randomUUID(), orb);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .65F, 1.45F);
            level.playSound(null, player.getX(), player.getY() + .8, player.getZ(), com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, .42F, 1.72F);
        } else if (type == Type.W) {
            data.setPlacementType(HUXIN_PLACEMENT);
            PlayerDataProvider.syncToClient(player);
        } else if (type == Type.E) {
            ServerPlayer ally = lookedAlly(level, player, 18.0);
            if (ally == null || ally == player) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("请瞄准18格内的友方施放牵丝渡厄"));
                return;
            }
            LINKS.put(player.getUUID(), new LinkState(ally.getUUID(), level.getGameTime() + 80,
                    (float)(20 + (lv - 1) * 3)));
            data.setBuff("qiansiduoe", 80);
            triggerPassive(player, ally, level);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, .7F, 1.35F);
        } else if (type == Type.R) {
            PENDING_R.put(player.getUUID(), new PendingCast(player.position(), level.getGameTime() + 20, lv));
            data.setBuff("wanmuchunhui", 20);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 22, 255, false, false, false));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, .9F, .9F);
        }
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Q_REGEN_UNTIL.entrySet().removeIf(e -> {
            net.minecraft.world.entity.Entity entity = level.getEntity(e.getKey());
            LivingEntity target = entity instanceof LivingEntity living ? living : null;
            if (target == null || now >= e.getValue()) return true;
            if (now % 20 == 0) heal(target, Math.max(1F, target.getMaxHealth() * .02F));
            return false;
        });
        tickOrbs(player, level, now);
        if (data.getPlacementType() == HUXIN_PLACEMENT && now % 3 == 0) {
            Vec3 preview = placementPoint(player, 12); if (preview != null) for (int i = 0; i < 28; i++) { double a = Math.PI * 2 * i / 28.0;
                level.sendParticles(player, JADE_DUST, false, false, preview.x + Math.cos(a) * 4, preview.y + .08, preview.z + Math.sin(a) * 4, 1, 0, 0, 0, 0); }
        }
        PendingCast pendingW = PENDING_W.get(player.getUUID());
        if (pendingW != null && now >= pendingW.until) { PENDING_W.remove(player.getUUID()); activateW(player, data, pendingW.center, pendingW.level); }
        if (pendingW != null) { for (int i = 0; i < 12; i++) { double a = now * .22 + i * .52;
            level.sendParticles(JADE_LIGHT, player.getX() + Math.cos(a) * 1.8, player.getY() + .2 + (i % 3) * .25, player.getZ() + Math.sin(a) * 1.8, 1, 0, 0, 0, .02);
            level.sendParticles(JADE_LIGHT, pendingW.center.x + Math.cos(a) * 2.2, pendingW.center.y + .12, pendingW.center.z + Math.sin(a) * 2.2, 1, 0, .04, 0, .02); }}
        PendingCast pendingR = PENDING_R.get(player.getUUID());
        if (pendingR != null) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 255, false, false, false));
            level.sendParticles(JADE_LIGHT, player.getX(), player.getY() + 1, player.getZ(), 6, .45, .65, .45, .01);
            if (now >= pendingR.until) { PENDING_R.remove(player.getUUID()); activateR(player, data, pendingR.level); }
        }
        tickShield(level, now);
        tickLink(player, data, level, now);
        GroveState grove = GROVES.get(player.getUUID());
        if (grove == null) return;
        if (now >= grove.until) { GROVES.remove(player.getUUID()); data.setBuff(grove.ultimate ? "wanmuchunhui" : "huxindanzhen", 0); return; }
        if (grove.ultimate) spawnGroveCoreAndWave(level, grove.center, now);
        if (now % 4 == 0) {
            int points = grove.ultimate ? 32 : 10;
            double radius = grove.ultimate ? 8 : 4;
            for (int i = 0; i < points; i++) { double a = Math.PI * 2 * i / points + now * .03;
                level.sendParticles(JADE_DUST, grove.center.x + Math.cos(a) * radius, grove.center.y + .08,
                        grove.center.z + Math.sin(a) * radius, 1, 0, .03, 0, 0); }
        }
        if (grove.ultimate && now % 20 == 0) {
            double sp = player.getAttributeValue(ModAttributes.SPELL_POWER);
            level.playSound(null, grove.center.x, grove.center.y, grove.center.z,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .32F, 1.18F);
            for (ServerPlayer other : level.players()) {
                if (other.position().distanceTo(grove.center) > 8) continue;
                if (ally(player, other)) {
                    heal(other, (float)(10 + sp * .40 + (grove.level - 1) * 4));
                    for (int i = 0; i < 10; i++) { double a = now * .25 + i * .63;
                        level.sendParticles(JADE_LIGHT, other.getX() + Math.cos(a) * (i / 10.0), other.getY() + .2 + i * .12,
                                other.getZ() + Math.sin(a) * (i / 10.0), 1, 0, 0, 0, .01); }
                    PlayerData od = PlayerDataProvider.get(other);
                    od.setMana((float)Math.min(other.getAttributeValue(ModAttributes.MAX_MANA), od.getMana() + 4));
                    triggerPassive(player, other, level);
                    if (!grove.protectedTargets.contains(other.getUUID()) && other.getHealth() / other.getMaxHealth() < .25F) {
                        grove.protectedTargets.add(other.getUUID());
                        GROVE_INVULNERABLE.put(other.getUUID(), now + 20);
                    }
                } else {
                    other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 0, false, false, false));
                }
            }
        } else if (!grove.ultimate && now % 10 == 0) {
            for (ServerPlayer other : level.players()) if (ally(player, other) && other.position().distanceTo(grove.center) <= 4) {
                applyShield(level, player, other, (float)(10 + (grove.level - 1) * 12
                        + player.getAttributeValue(ModAttributes.SPELL_POWER) * .45), 30);
            }
        }
    }

    /** 万木回春：空中 5 格的实心灵力核心，每秒发出一轮向半径 8 格扩散并下沉的绿光波。 */
    private static void spawnGroveCoreAndWave(ServerLevel level, Vec3 center, long now) {
        Vec3 core = center.add(0, 5, 0);
        if ((now & 1) == 0) {
            for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                if (x * x + y * y + z * z > 10) continue;
                level.sendParticles(GROVE_CORE, core.x + x * .22, core.y + y * .22, core.z + z * .22,
                        1, .015, .015, .015, .003);
            }
        }
        double progress = (now % 20L) / 19.0;
        double radius = .3 + 7.7 * progress;
        double height = core.y - 4.7 * progress;
        int points = 56;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points + now * .035;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(i % 3 == 0 ? JADE_LIGHT : JADE_DUST, x, height, z,
                    1, .03, .045, .03, .012);
        }
    }

    public static boolean interceptDamage(ServerPlayer victim, net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        long now = victim.level().getGameTime();
        Long until = GROVE_INVULNERABLE.get(victim.getUUID());
        if (until != null && until > now) { event.getContainer().setNewDamage(0); return true; }
        return false;
    }

    /** 牵丝：青萝承伤时最多将比例伤害消耗友方现有护盾。 */
    public static void onQingLuoIncoming(ServerPlayer victim, net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        LinkState link = LINKS.get(victim.getUUID());
        if (link == null || link.until <= victim.level().getGameTime()) { LINKS.remove(victim.getUUID()); return; }
        ServerPlayer ally = victim.level().getServer().getPlayerList().getPlayer(link.targetId);
        if (ally == null || ally.isDeadOrDying() || !ally(victim, ally)) { LINKS.remove(victim.getUUID()); return; }
        float redirected = event.getAmount() * link.percent / 100F;
        float consumed = ModShieldSystem.consume(ally, redirected);
        if (consumed > 0) {
            event.getContainer().setNewDamage(Math.max(0, event.getAmount() - consumed));
            victim.level().playSound(null, ally.getX(), ally.getY() + .8, ally.getZ(),
                    SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, .5F, 1.42F);
        }
    }

    public static void clearFor(UUID id) {
        LINKS.remove(id); GROVES.remove(id); SHIELDS.remove(id); Q_REGEN_UNTIL.remove(id); GROVE_INVULNERABLE.remove(id);
        PASSIVE_TARGET_COOLDOWNS.keySet().removeIf(key -> key.equals(id));
    }

    private static void tickLink(ServerPlayer player, PlayerData data, ServerLevel level, long now) {
        LinkState link = LINKS.get(player.getUUID());
        if (link == null) return;
        ServerPlayer target = level.getServer().getPlayerList().getPlayer(link.targetId);
        if (target == null || now >= link.until || !com.myriad.combat.SkillTargets.alive(target) || !ally(player, target)) { LINKS.remove(player.getUUID()); data.setBuff("qiansiduoe", 0); return; }
        Vec3 delta = player.position().subtract(target.position());
        if (target.hasEffect(MobEffects.SLOWNESS) || target.hasEffect(MobEffects.WEAKNESS) || target.hasEffect(MobEffects.BLINDNESS)) {
            target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10, 0, false, false, false));
            if (delta.horizontalDistance() > 2.0) target.setDeltaMovement(delta.normalize().scale(.42));
        }
        if (now % 3 == 0) {
            Vec3 midpoint = player.position().lerp(target.position(), .5).add(0, 1, 0);
            level.sendParticles(ParticleTypes.ENCHANT, midpoint.x, midpoint.y, midpoint.z, 3, .5, .15, .5, .01);
        }
    }

    private static void tickShield(ServerLevel level, long now) {
        SHIELDS.entrySet().removeIf(e -> {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(e.getKey());
            ShieldState shield = e.getValue();
            if (target == null || now >= shield.until) return true;
            if (shield.hadShield && ModShieldSystem.value(target) <= .01F) {
                target.removeEffect(MobEffects.SLOWNESS);
                shield.hadShield = false;
                level.sendParticles(ParticleTypes.WAX_ON, target.getX(), target.getY() + 1, target.getZ(), 10, .3, .4, .3, .02);
                level.playSound(null, target.getX(), target.getY() + .8, target.getZ(),
                        SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, .55F, .78F);
            }
            return false;
        });
    }

    private static void applyShield(ServerLevel level, ServerPlayer caster, LivingEntity target, float amount, int duration) {
        float beforeShield = ModShieldSystem.value(target);
        ModShieldSystem.ensureAtLeast(target, "qingluo_huxindanzhen", amount, duration);
        
        target.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0, false, false, false));
        SHIELDS.put(target.getUUID(), new ShieldState(level.getGameTime() + duration,
                ModShieldSystem.value(target) > .01F));
        if (target instanceof ServerPlayer player) { PlayerData td = PlayerDataProvider.get(player); td.setBuff("huxindanzhen", duration); PlayerDataProvider.syncToClient(player); }
    }

    private static void triggerPassive(ServerPlayer caster, LivingEntity target, ServerLevel level) {
        if (!ally(caster, target) || target.getHealth() / target.getMaxHealth() >= .60F) return;
        long now = level.getGameTime();
        Long until = PASSIVE_TARGET_COOLDOWNS.get(target.getUUID());
        if (until != null && until > now) return;
        PASSIVE_TARGET_COOLDOWNS.put(target.getUUID(), now + 80);
        heal(target, (target.getMaxHealth() - target.getHealth()) * .03F);
        target.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 0, false, false, false));
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1, target.getZ(), 4, .22, .25, .22, .01);
        level.playSound(null, target.getX(), target.getY() + 1, target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .38F, 1.7F);
    }

    private static void heal(LivingEntity target, float amount) {
        if (amount <= 0 || target.getHealth() >= target.getMaxHealth()) return;
        float actual = Math.min(amount, target.getMaxHealth() - target.getHealth());
        com.myriad.combat.CombatTelemetry.heal(target, actual, "qingluo_skill");
        if (target instanceof ServerPlayer player) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new NetworkSetup.HealFloatPayload(Math.max(1, Math.round(actual))));
    }

    private static ServerPlayer lookedAlly(ServerLevel level, ServerPlayer caster, double range) {
        LivingEntity entity = lookedTarget(level, caster, range);
        return entity instanceof ServerPlayer player && ally(caster, player) ? player : null;
    }

    /** E 的前置校验：必须明确指向一名友方英雄，避免空放后消耗法力和冷却。 */
    public static boolean hasLinkTarget(ServerPlayer caster) {
        return lookedAlly((ServerLevel) caster.level(), caster, 18.0) != null;
    }

    /** 护心丹阵的选点确认：服务端重新按当前视线取不超过 12 格的地面落点。 */
    public static void confirmW(ServerPlayer player, PlayerData data) {
        Vec3 point = placementPoint(player, 12);
        if (point == null) return;
        data.setPlacementType(0);
        PENDING_W.put(player.getUUID(), new PendingCast(point, player.level().getGameTime() + 30,
                Math.max(1, data.skillLevel("huxindanzhen"))));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 32, 255, false, false, false));
        player.level().playSound(null, player.getX(), player.getY() + .8, player.getZ(),
                com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, .6F, 1.32F);
        PlayerDataProvider.syncToClient(player);
    }

    public static void cancelW(ServerPlayer player, PlayerData data) {
        if (data.getPlacementType() == HUXIN_PLACEMENT) {
            data.setPlacementType(0); data.setCooldown("huxindanzhen", 0);
            PlayerDataProvider.syncToClient(player);
        }
    }

    private static void activateW(ServerPlayer caster, PlayerData data, Vec3 center, int lv) {
        ServerLevel level = caster.level(); int duration = 100 + ((lv - 1) / 2) * 10;
        GROVES.put(caster.getUUID(), new GroveState(center, level.getGameTime() + duration, false, true, lv));
        float shield = (float)(10 + (lv - 1) * 12 + caster.getAttributeValue(ModAttributes.SPELL_POWER) * .45);
        for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(16),
                e -> com.myriad.combat.SkillTargets.alive(e) && ally(caster, e) && e.position().distanceTo(center) <= 4)) {
            applyShield(level, caster, ally, shield, duration); triggerPassive(caster, ally, level);
        }
        level.sendParticles(JADE_DUST, center.x, center.y + .12, center.z, 45, 1.8, .16, 1.8, .03);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, .75F, 1.1F);
        level.playSound(null, center.x, center.y, center.z, com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, .55F, 1.45F);
    }

    private static void activateR(ServerPlayer player, PlayerData data, int lv) {
        ServerLevel level = player.level(); int duration = (6 + Math.max(0, lv - 1)) * 20;
        GROVES.put(player.getUUID(), new GroveState(player.position(), level.getGameTime() + duration, true, false, lv));
        data.setBuff("wanmuchunhui", duration);
        level.sendParticles(JADE_DUST, player.getX(), player.getY() + .2, player.getZ(), 100, 8, .3, 8, .04);
        level.playSound(null, player.getX(), player.getY() + .8, player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, .62F, 1.18F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, .7F, .92F);
    }

    private static void tickOrbs(ServerPlayer caster, ServerLevel level, long now) {
        ORBS.entrySet().removeIf(entry -> {
            OrbState orb = entry.getValue(); if (!orb.owner.equals(caster.getUUID())) return false; if (now < orb.releaseAt) return false;
            if (now >= orb.expiresAt) return true;
            LivingEntity target = selectOrbTarget(caster, level, orb.pos);
            if (target == null) return true;
            Vec3 aim = target.getBoundingBox().getCenter().add(0, .25, 0);
            Vec3 step = aim.subtract(orb.pos); double distance = step.length();
            if (distance < .7) {
                boolean friendlyHit = ally(caster, target);
                if (friendlyHit) { heal(target, (float)(10 + (orb.level - 1) * 8 + orb.sp * .45));
                    if (target.getHealth() / target.getMaxHealth() < .35F) Q_REGEN_UNTIL.put(target.getUUID(), now + 40);
                    triggerPassive(caster, target, level); }
                else DamageCalc.dealSkillDamage(caster, target, (float)(48 + (orb.level - 1) * 32 + orb.sp * 1.80), Kind.MAGIC, 0);
                level.sendParticles(JADE_LIGHT, aim.x, aim.y, aim.z, 16, .3, .4, .3, .03);
                level.playSound(null, aim.x, aim.y, aim.z,
                        friendlyHit ? com.myriad.registry.ModSounds.foley("wood") : SoundEvents.AMETHYST_CLUSTER_HIT,
                        SoundSource.PLAYERS, friendlyHit ? .55F : .72F, friendlyHit ? 1.82F : 1.18F);
                return true;
            }
            Vec3 forward = step.normalize(); Vec3 side = new Vec3(-forward.z, 0, forward.x);
            orb.pos = orb.pos.add(forward.scale(.55)).add(side.scale(orb.bend * .13));
            level.sendParticles(JADE_DUST, orb.pos.x, orb.pos.y, orb.pos.z, 2, .08, .08, .08, .01); return false;
        });
    }

    private static LivingEntity selectOrbTarget(ServerPlayer caster, ServerLevel level, Vec3 from) {
        LivingEntity lowest = caster; double ratio = caster.getHealth() / Math.max(1, caster.getMaxHealth());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(15), e -> e != caster && com.myriad.combat.SkillTargets.alive(e) && ally(caster, e))) {
            double r = e.getHealth() / Math.max(1, e.getMaxHealth()); if (r < ratio) { ratio = r; lowest = e; }
        }
        if (lowest != null && ratio < .80) return lowest;
        LivingEntity enemy = null; double d = 225;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(15), e -> e != caster && com.myriad.combat.SkillTargets.alive(e) && !ally(caster, e))) {
            double nd = e.distanceToSqr(caster); if (nd < d) { d = nd; enemy = e; }
        }
        return enemy != null ? enemy : lowest;
    }

    private static Vec3 placementPoint(ServerPlayer player, int range) {
        return PlacementUtil.pointOnGround(player, range);
    }

    private static LivingEntity lookedTarget(ServerLevel level, ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition(); Vec3 look = caster.getLookAngle().normalize();
        LivingEntity best = null; double bestDist = range * range;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(range),
                e -> e != caster && com.myriad.combat.SkillTargets.alive(e))) {
            Vec3 to = candidate.getBoundingBox().getCenter().subtract(eye);
            double dist = to.lengthSqr();
            if (dist <= bestDist && dist > .001 && look.dot(to.normalize()) > .82) { best = candidate; bestDist = dist; }
        }
        return best;
    }

    private static ServerPlayer nearestAlly(ServerLevel level, ServerPlayer caster, double range) {
        ServerPlayer best = null; double bestDist = range * range;
        for (ServerPlayer other : level.players()) if (other != caster && ally(caster, other)) {
            double dist = other.distanceToSqr(caster); if (dist < bestDist) { best = other; bestDist = dist; }
        }
        return best;
    }

    private static boolean ally(ServerPlayer caster, LivingEntity other) {
        return com.myriad.combat.SkillTargets.ally(caster, other);
    }

    private static final class ShieldState { final long until; boolean hadShield; ShieldState(long until, boolean hadShield) { this.until = until; this.hadShield = hadShield; } }
    private static final class OrbState { final UUID owner; Vec3 pos; final long releaseAt, expiresAt; final int level; final double sp; final double bend; OrbState(UUID o,Vec3 p,long r,long e,int l,double s){owner=o;pos=p;releaseAt=r;expiresAt=e;level=l;sp=s;bend=(Math.random()-.5)*1.8;} }
    private record PendingCast(Vec3 center, long until, int level) { }
    private record LinkState(UUID targetId, long until, float percent) { }
    private static final class GroveState { final Vec3 center; final long until; final boolean ultimate; final boolean shieldZone; final int level; final Set<UUID> protectedTargets = new HashSet<>(); GroveState(Vec3 c,long u,boolean ult,boolean shield,int l){center=c;until=u;ultimate=ult;shieldZone=shield;level=l;} }
}
