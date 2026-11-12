package com.myriad.character.skills.guanlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import com.myriad.game.PlacementUtil;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.network.NetworkSetup.StarPullMotionPayload;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** 星罗·观澜：星轨叠算、双充能落星、星盘、双点星线与坠星区域。 */
public final class GuanLanSkills extends Skill {
    public static final int METEOR_PLACEMENT = 9;
    public static final int STAR_LINE_PLACEMENT = 10;
    private static final double ULTIMATE_CAST_RANGE = 12.0;
    private static final double ULTIMATE_FIELD_RADIUS = 6.5;
    private static final double ULTIMATE_METEOR_SPREAD = 5.5;
    private static final double ULTIMATE_STAR_RADIUS = 4.0;
    private static final double ULTIMATE_FINAL_STAR_RADIUS = 5.5;
    public static final int ULTIMATE_PLACEMENT = 11;
    private static final String Q_CHARGE_COUNTER = "skill_charge_liuxingluozi";
    public enum Type { PASSIVE, Q, W, E, R }

    private static final DustParticleOptions VIOLET = new DustParticleOptions(0x7656E8, 1.2F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFD86A, .9F);
    private static final DustParticleOptions BLUE = new DustParticleOptions(0x65B9FF, 1.0F);
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x59E4E8, 1.05F);
    private static final DustParticleOptions DEEP_BLUE = new DustParticleOptions(0x297AC8, .85F);
    private static final DustParticleOptions BROWN = new DustParticleOptions(0x8A6544, .75F);
    private static final Map<UUID, Integer> STAR_TRACKS = new HashMap<>();
    private static final Map<UUID, ChargeState> Q_CHARGES = new HashMap<>();

    public static int runeRecharge(ServerPlayer player, double fraction) {
        ChargeState charge = Q_CHARGES.get(player.getUUID());
        if (charge == null || charge.nextRegen <= 0) return 0;
        long now = player.level().getGameTime();
        int remaining = (int)Math.max(0, charge.nextRegen - now);
        if (fraction > 0) charge.nextRegen = now + Math.max(1, (int)Math.ceil(remaining * (1 - fraction)));
        return remaining;
    }
    private static final Map<UUID, ArrayList<PendingMeteor>> METEORS = new HashMap<>();
    private static final Map<UUID, ArrayList<Ripple>> RIPPLES = new HashMap<>();
    private static final Map<UUID, StarDisk> DISKS = new HashMap<>();
    private static final Map<UUID, Vec3> FIRST_LINE_POINT = new HashMap<>();
    private static final Map<UUID, StarLine> LINES = new HashMap<>();
    private static final Map<UUID, StarStorm> STORMS = new HashMap<>();
    private final Type type;

    public GuanLanSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "guanlan_passive";
            case Q -> "liuxingluozi";
            case W -> "nizhuanxingpan";
            case E -> "tianmufengxian";
            case R -> "zhoutianzhuixing";
        }, switch (type) {
            case PASSIVE -> 0; case Q -> 1; case W -> 300; case E -> 420; case R -> 1700;
        }, switch (type) {
            case PASSIVE -> 0.0F; case Q -> 72.0F; case W -> 126.0F; case E -> 180.0F; case R -> 378.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getMaxCharges() { return type == Type.Q ? 2 : 1; }
    @Override public int getCooldownTicks(int level) {
        if (type == Type.R) return Math.max(500, 1700 - Math.max(0, level - 1) * 200);
        return super.getCooldownTicks(level);
    }
    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "技能命中叠4层；满层后的下一次技能额外造成20+30%法强并返还15%最大法力";
            case Q -> "双充能；爆炸造成" + (28 + (lv - 1) * 10) + "+55%法强，减速"
                    + (25 + ((lv - 1) / 2) * 5) + "%";
            case W -> "星盘持续" + (3 + Math.min(1, (lv - 1) / 3)) + "秒，首次进入造成"
                    + (24 + (lv - 1) * 9) + "+35%法强";
            case E -> "星线造成" + (40 + (lv - 1) * 14) + "+50%法强，禁锢"
                    + String.format("%.1f", .8 + Math.min(.4, (lv - 1) * .1)) + "秒";
            case R -> "12格内选点，约6.5格区域落下" + (9 + lv - 1) + "枚星陨；普通星陨半径4格、终结星陨半径5.5格，每枚"
                    + (45 + (lv - 1) * 10) + "+35%法强";
        };
    }

    @Override public void cast(ServerPlayer player, PlayerData data) {
        int lv = Math.max(1, data.skillLevel(getId()));
        if (type == Type.Q) {
            ChargeState charges = charges(player);
            charges.charges--;
            if (charges.nextRegen <= 0) charges.nextRegen = player.level().getGameTime() + 120;
            data.setBuff(Q_CHARGE_COUNTER, charges.charges);
            data.setPlacementType(METEOR_PLACEMENT);
        } else if (type == Type.W) {
            Vec3 look = horizontal(player.getLookAngle());
            Vec3 center = player.position().add(look.scale(3));
            long duration = 60 + Math.min(20, ((lv - 1) / 3) * 20L);
            long activeFrom = player.level().getGameTime() + 20;
            DISKS.put(player.getUUID(), new StarDisk(center, activeFrom, activeFrom + duration, lv,
                    player.getAttributeValue(ModAttributes.SPELL_POWER)));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("water"),
                    SoundSource.PLAYERS, .55F, 1.7F);
        } else if (type == Type.E) {
            FIRST_LINE_POINT.remove(player.getUUID());
            data.setPlacementType(STAR_LINE_PLACEMENT);
        } else if (type == Type.R) {
            data.setPlacementType(ULTIMATE_PLACEMENT);
        }
        PlayerDataProvider.syncToClient(player);
    }

    public static boolean canCast(ServerPlayer player, int slot) {
        return slot != CharacterType.SLOT_Q || charges(player).charges > 0;
    }

    public static void confirmPlacement(ServerPlayer player, PlayerData data) {
        int placement = data.getPlacementType();
        if (placement == METEOR_PLACEMENT) {
            Vec3 point = PlacementUtil.pointOnGround(player, 18);
            if (point == null) return;
            long now = player.level().getGameTime();
            Vec3 origin = point.add((player.getRandom().nextDouble() * 2 - 1) * 1.75, 5,
                    (player.getRandom().nextDouble() * 2 - 1) * 1.75);
            METEORS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).add(
                    new PendingMeteor(point, origin, now, now + 7,
                    Math.max(1, data.skillLevel("liuxingluozi")),
                    player.getAttributeValue(ModAttributes.SPELL_POWER)));
            data.setPlacementType(0);
        } else if (placement == STAR_LINE_PLACEMENT) {
            Vec3 point = PlacementUtil.pointOnGround(player, 18);
            if (point == null) return;
            Vec3 first = FIRST_LINE_POINT.get(player.getUUID());
            if (first == null) {
                FIRST_LINE_POINT.put(player.getUUID(), point);
                return;
            }
            Vec3 delta = point.subtract(first);
            if (delta.horizontalDistanceSqr() < 1) return;
            if (delta.horizontalDistance() > 10) point = first.add(horizontal(delta).scale(10));
            int lv = Math.max(1, data.skillLevel("tianmufengxian"));
            LINES.put(player.getUUID(), new StarLine(first, point, player.level().getGameTime() + 80, lv,
                    player.getAttributeValue(ModAttributes.SPELL_POWER)));
            FIRST_LINE_POINT.remove(player.getUUID());
            data.setPlacementType(0);
        } else if (placement == ULTIMATE_PLACEMENT) {
            Vec3 point = PlacementUtil.pointOnGround(player, ULTIMATE_CAST_RANGE);
            if (point == null) return;
            int lv = Math.max(1, data.skillLevel("zhoutianzhuixing"));
            STORMS.put(player.getUUID(), new StarStorm(point, player.level().getGameTime() + 20,
                    9 + lv - 1, lv, player.getAttributeValue(ModAttributes.SPELL_POWER)));
            data.setPlacementType(0);
        }
        PlayerDataProvider.syncToClient(player);
    }

    public static void cancelPlacement(ServerPlayer player, PlayerData data) {
        int placement = data.getPlacementType();
        if (placement >= METEOR_PLACEMENT && placement <= ULTIMATE_PLACEMENT) {
            FIRST_LINE_POINT.remove(player.getUUID());
            data.setPlacementType(0);
            String skill = placement == METEOR_PLACEMENT ? "liuxingluozi"
                    : placement == STAR_LINE_PLACEMENT ? "tianmufengxian" : "zhoutianzhuixing";
            data.setCooldown(skill, 0);
            PlayerDataProvider.syncToClient(player);
        }
    }

    public static void tick(ServerPlayer player, PlayerData data) {
        ServerLevel level = player.level(); long now = level.getGameTime();
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.GUANLAN) return;
        ChargeState charge = charges(player);
        if (charge.charges < 2 && charge.nextRegen > 0 && now >= charge.nextRegen) {
            charge.charges++;
            charge.nextRegen = charge.charges < 2 ? now + 120 : 0;
        }
        if (data.getBuff(Q_CHARGE_COUNTER) != charge.charges) {
            data.setBuff(Q_CHARGE_COUNTER, charge.charges);
            PlayerDataProvider.syncToClient(player);
        }
        data.setBuff("guanlan_passive", STAR_TRACKS.getOrDefault(player.getUUID(), 0));
        if (STAR_TRACKS.getOrDefault(player.getUUID(), 0) >= 4 && now % 3 == 0) {
            passiveStarlight(level, player);
        }
        tickPreview(player, data, level);
        tickMeteors(player, level, now); tickRipples(player, level, now);
        tickDisk(player, level, now); tickLine(player, level, now); tickStorm(player, level, now);
    }

    private static void tickPreview(ServerPlayer p, PlayerData data, ServerLevel level) {
        if (p.tickCount % 3 != 0) return;
        int placement = data.getPlacementType();
        double range = placement == ULTIMATE_PLACEMENT ? ULTIMATE_CAST_RANGE : 18;
        if (placement < METEOR_PLACEMENT || placement > ULTIMATE_PLACEMENT) return;
        Vec3 point = PlacementUtil.pointOnGround(p, range); if (point == null) return;
        Vec3 first = FIRST_LINE_POINT.get(p.getUUID());
        if (placement == STAR_LINE_PLACEMENT && first != null) lineTo(level, p, first, point, GOLD, 24);
        else ringTo(level, p, point,
                placement == METEOR_PLACEMENT ? 2 : ULTIMATE_FIELD_RADIUS, VIOLET, 42);
    }

    private static void tickMeteors(ServerPlayer owner, ServerLevel level, long now) {
        ArrayList<PendingMeteor> meteors = METEORS.get(owner.getUUID());
        if (meteors == null) return;
        meteors.removeIf(m -> {
            if (now >= m.impactAt) {
                explodeMeteor(owner, m);
                RIPPLES.computeIfAbsent(owner.getUUID(), id -> new ArrayList<>())
                        .add(new Ripple(m.center, now, now + 10, 3.4));
                return true;
            }
            double progress = Math.max(0, Math.min(1,
                    (now - m.startAt) / (double)Math.max(1, m.impactAt - m.startAt)));
            Vec3 position = m.origin.lerp(m.center.add(0, .45, 0), progress);
            solidSphere(level, position, .68, BLUE, BROWN, 48);
            ParticleEffects.burst(level, position, 8, .78, .18F, .62F, 1F);
            return false;
        });
        if (meteors.isEmpty()) METEORS.remove(owner.getUUID());
    }

    private static void tickRipples(ServerPlayer owner, ServerLevel level, long now) {
        ArrayList<Ripple> ripples = RIPPLES.get(owner.getUUID());
        if (ripples == null) return;
        ripples.removeIf(ripple -> {
            if (now >= ripple.until) return true;
            double progress = (now - ripple.started) / (double)Math.max(1, ripple.until - ripple.started);
            double radius = .25 + ripple.maxRadius * progress;
            ring(level, ripple.center, radius, CYAN, 42);
            if (radius > .6) ring(level, ripple.center.add(0, .05, 0), radius * .72, DEEP_BLUE, 30);
            return false;
        });
        if (ripples.isEmpty()) RIPPLES.remove(owner.getUUID());
    }

    private static void explodeMeteor(ServerPlayer owner, PendingMeteor m) {
        ServerLevel level = owner.level();
        for (LivingEntity e : enemies(level, owner, m.center, 2)) {
            dealStarDamage(owner, e, (float)(112 + (m.level - 1) * 40 + m.sp * 2.20), 0);
            int slow = 25 + ((m.level - 1) / 2) * 5;
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, slow >= 35 ? 1 : 0, false, false, false));
        }
        level.sendParticles(BLUE, m.center.x, m.center.y + .3, m.center.z, 55, 1.5, .4, 1.5, .08);
        level.sendParticles(ParticleTypes.END_ROD, m.center.x, m.center.y + .5, m.center.z, 18, .8, .5, .8, .06);
        ParticleEffects.burst(level, m.center.add(0, .4, 0), 38, 1.45, .16F, .70F, 1F);
        level.playSound(null, m.center.x, m.center.y, m.center.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, .55F, 1.45F);
    }

    private static void tickDisk(ServerPlayer owner, ServerLevel level, long now) {
        StarDisk d = DISKS.get(owner.getUUID()); if (d == null) return;
        if (now >= d.until) { DISKS.remove(owner.getUUID()); return; }
        if (now < d.activeFrom) {
            if (now % 2 == 0) {
                double remain = Math.max(0, d.activeFrom - now) / 20.0;
                ring(level, d.center, .45 + remain * 2.55, CYAN, 34);
                ParticleEffects.burst(level, d.center.add(0, .25, 0), 12, 2.7 * remain,
                        .15F, .58F, 1F);
            }
            return;
        }
        if (!d.activated) {
            d.activated = true;
            level.playSound(null, d.center.x, d.center.y, d.center.z, com.myriad.registry.ModSounds.foley("water"),
                    SoundSource.PLAYERS, .7F, 1.55F);
        }
        if (now % 3 == 0) drawStarDisk(level, d.center, 3);
        for (ServerPlayer p : level.players()) if (ally(owner, p) && p.distanceToSqr(d.center) <= 9)
            p.addEffect(new MobEffectInstance(MobEffects.SPEED, 8, 0, false, false, false));
        for (LivingEntity e : enemies(level, owner, d.center, 3)) {
            if (d.triggered.add(e.getUUID()))
                dealStarDamage(owner, e, (float)(96 + (d.level - 1) * 36 + d.sp * 1.40), 1);
            Vec3 delta = d.center.subtract(e.position());
            if (delta.horizontalDistanceSqr() > .12) {
                Vec3 pull = horizontal(delta).scale(.18);
                Vec3 velocity = e.getDeltaMovement();
                e.setDeltaMovement(velocity.x * .35 + pull.x, velocity.y,
                        velocity.z * .35 + pull.z);
                e.hurtMarked = true;
                if (e instanceof ServerPlayer target) {
                    PacketDistributor.sendToPlayer(target, new StarPullMotionPayload(
                            (float)d.center.x, (float)d.center.z, .18F, .35F, 2));
                }
            }
        }
    }

    private static void tickLine(ServerPlayer owner, ServerLevel level, long now) {
        StarLine line = LINES.get(owner.getUUID()); if (line == null) return;
        if (now >= line.until) { LINES.remove(owner.getUUID()); return; }
        if (now % 2 == 0) drawStarLine(level, line.a, line.b);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(line.a, line.b).inflate(1.2),
                x -> x.isAlive() && x != owner && !ally(owner, x))) {
            if (line.hit.contains(e.getUUID()) || distanceToSegment(e.position(), line.a, line.b) > .75) continue;
            line.hit.add(e.getUUID());
            dealStarDamage(owner, e, (float)(160 + (line.level - 1) * 56 + line.sp * 2.0), 2);
            int root = (int)((.8 + Math.min(.4, (line.level - 1) * .1)) * 20);
            e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, root, 255, false, false, false));
        }
    }

    private static void tickStorm(ServerPlayer owner, ServerLevel level, long now) {
        StarStorm s = STORMS.get(owner.getUUID()); if (s == null) return;
        if (now % 3 == 0) {
            ring(level, s.center, ULTIMATE_FIELD_RADIUS, CYAN, 48);
            ParticleEffects.ring(level, s.center.add(0, .12, 0),
                    ULTIMATE_FIELD_RADIUS - .4, 32, .1F, .45F, 1F);
        }
        if (s.remaining > 0 && now >= s.nextSpawn) {
            boolean last = s.remaining == 1;
            Vec3 impact;
            if (last) {
                impact = s.center;
            } else {
                double angle = owner.getRandom().nextDouble() * Math.PI * 2;
                double radius = Math.sqrt(owner.getRandom().nextDouble()) * ULTIMATE_METEOR_SPREAD;
                impact = s.center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            }
            Vec3 origin = impact.add((owner.getRandom().nextDouble() * 2 - 1) * 2.2, 8,
                    (owner.getRandom().nextDouble() * 2 - 1) * 2.2);
            s.falling.add(new FallingStar(origin, impact, now, now + (last ? 10 : 8), last));
            s.remaining--;
            s.nextSpawn = now + 5;
        }
        s.falling.removeIf(star -> {
            if (now >= star.impactAt) {
                impactStormStar(owner, level, s, star);
                return true;
            }
            double progress = (now - star.started) / (double)Math.max(1, star.impactAt - star.started);
            Vec3 position = star.origin.lerp(star.impact.add(0, star.last ? 1.2 : .65, 0), progress);
            if (star.last) {
                solidSphere(level, position, 3, CYAN, DEEP_BLUE, 96);
            } else {
                hollowIrregularSphere(level, position, 1, CYAN, DEEP_BLUE, 44, now);
            }
            level.sendParticles(ParticleTypes.END_ROD, position.x, position.y, position.z,
                    star.last ? 9 : 4, star.last ? 1.6 : .55, star.last ? 1.6 : .55,
                    star.last ? 1.6 : .55, .025);
            return false;
        });
        if (s.remaining <= 0 && s.falling.isEmpty()) STORMS.remove(owner.getUUID());
    }

    private static void impactStormStar(ServerPlayer owner, ServerLevel level, StarStorm storm, FallingStar star) {
        double radius = star.last ? ULTIMATE_FINAL_STAR_RADIUS : ULTIMATE_STAR_RADIUS;
        for (LivingEntity e : enemies(level, owner, star.impact, radius)) {
            dealStarDamage(owner, e, (float)(180 + (storm.level - 1) * 40 + storm.sp * 1.40), 3);
            if (star.last) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 255, false, false, false));
        }
        ring(level, star.impact, radius, CYAN, star.last ? 64 : 38);
        level.sendParticles(star.last ? CYAN : DEEP_BLUE, star.impact.x, star.impact.y + .35, star.impact.z,
                star.last ? 80 : 36, radius * .55, .45, radius * .55, .08);
        ParticleEffects.burst(level, star.impact.add(0, .45, 0), star.last ? 72 : 30,
                radius, .12F, .72F, 1F);
        level.playSound(null, star.impact.x, star.impact.y, star.impact.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, star.last ? .85F : .5F, star.last ? 1.25F : 1.65F);
    }

    private static void passiveStarlight(ServerLevel level, ServerPlayer player) {
        double phase = level.getGameTime() * .28;
        for (int i = 0; i < 8; i++) {
            double angle = phase + i * Math.PI * 2 / 8;
            double radius = .65 + (i % 3) * .22;
            Vec3 point = player.position().add(Math.cos(angle) * radius,
                    .25 + (i % 4) * .42, Math.sin(angle) * radius);
            ParticleEffects.mote(level, point, i % 2 == 0 ? .12F : .2F,
                    i % 2 == 0 ? .85F : .55F, 1F);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    private static void drawStarDisk(ServerLevel level, Vec3 center, double radius) {
        ring(level, center, radius, BLUE, 42);
        ring(level, center.add(0, .035, 0), radius * .72, CYAN, 32);
        Vec3[] points = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double angle = -Math.PI / 2 + i * Math.PI * 2 / 5;
            points[i] = center.add(Math.cos(angle) * radius * .82, .11,
                    Math.sin(angle) * radius * .82);
        }
        int[] order = {0, 2, 4, 1, 3, 0};
        for (int i = 0; i < order.length - 1; i++) {
            line(level, points[order[i]], points[order[i + 1]], i % 2 == 0 ? CYAN : DEEP_BLUE, 15);
            ParticleEffects.trail(level, points[order[i]], points[order[i + 1]], .35,
                    .1F, .65F, 1F);
        }
    }

    private static void drawStarLine(ServerLevel level, Vec3 a, Vec3 b) {
        for (int layer = 0; layer <= 2; layer++) {
            Vec3 lift = new Vec3(0, .12 + layer, 0);
            line(level, a.add(lift), b.add(lift), layer == 1 ? CYAN : DEEP_BLUE, 30);
        }
        Vec3 delta = b.subtract(a);
        for (int i = 0; i <= 10; i++) {
            Vec3 base = a.lerp(b, i / 10.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, base.x, base.y + 1.05, base.z,
                    2, .08, .75, .08, .025);
            ParticleEffects.mote(level, base.add(0, .25 + (i % 4) * .45, 0), .08F, .68F, 1F);
        }
        endpointStar(level, a);
        endpointStar(level, b);
    }

    private static void endpointStar(ServerLevel level, Vec3 endpoint) {
        for (int i = 0; i < 5; i++) {
            double angle = level.getGameTime() * .2 + i * Math.PI * 2 / 5;
            Vec3 ray = endpoint.add(Math.cos(angle) * .65, .4 + (i % 2) * .8,
                    Math.sin(angle) * .65);
            level.sendParticles(BLUE, ray.x, ray.y, ray.z, 2, .18, .3, .18, .05);
            level.sendParticles(ParticleTypes.END_ROD, ray.x, ray.y, ray.z, 1, .1, .16, .1, .06);
            ParticleEffects.mote(level, ray, .12F, .74F, 1F);
        }
    }

    private static void solidSphere(ServerLevel level, Vec3 center, double radius,
            DustParticleOptions primary, DustParticleOptions accent, int count) {
        int safe = Math.max(12, Math.min(112, count));
        for (int i = 0; i < safe; i++) {
            Vec3 direction = randomUnit(level);
            double r = radius * Math.cbrt(level.getRandom().nextDouble());
            Vec3 point = center.add(direction.scale(r));
            level.sendParticles(i % 5 == 0 ? accent : primary, point.x, point.y, point.z,
                    1, 0, 0, 0, 0);
            if (i % 7 == 0) ParticleEffects.mote(level, point, .08F, .65F, 1F);
        }
    }

    private static void hollowIrregularSphere(ServerLevel level, Vec3 center, double radius,
            DustParticleOptions primary, DustParticleOptions accent, int count, long phase) {
        int safe = Math.max(16, Math.min(72, count));
        double rotation = phase * .17;
        for (int i = 0; i < safe; i++) {
            double y = 1 - 2 * (i + .5) / safe;
            double ringRadius = Math.sqrt(Math.max(0, 1 - y * y));
            double angle = i * Math.PI * (3 - Math.sqrt(5)) + rotation;
            double jitter = .84 + ((i * 37) % 9) / 28.0;
            Vec3 point = center.add(Math.cos(angle) * ringRadius * radius * jitter,
                    y * radius * jitter, Math.sin(angle) * ringRadius * radius * jitter);
            level.sendParticles(i % 4 == 0 ? accent : primary, point.x, point.y, point.z,
                    1, 0, 0, 0, 0);
            if (i % 6 == 0) ParticleEffects.mote(level, point, .06F, .78F, 1F);
        }
    }

    private static Vec3 randomUnit(ServerLevel level) {
        Vec3 value;
        do {
            value = new Vec3(level.getRandom().nextDouble() * 2 - 1,
                    level.getRandom().nextDouble() * 2 - 1,
                    level.getRandom().nextDouble() * 2 - 1);
        } while (value.lengthSqr() < 1e-5 || value.lengthSqr() > 1);
        return value.normalize();
    }

    private static void dealStarDamage(ServerPlayer owner, LivingEntity target, float damage, int sourceSlot) {
        int stacks = STAR_TRACKS.getOrDefault(owner.getUUID(), 0);
        DamageCalc.dealSkillDamage(owner, target, damage, Kind.MAGIC, sourceSlot);
        PlayerData data = PlayerDataProvider.get(owner);
        if (stacks >= 4) {
            DamageCalc.dealSkillDamage(owner, target, (float)(80 + owner.getAttributeValue(ModAttributes.SPELL_POWER) * 1.20), Kind.MAGIC, 4);
            double max = owner.getAttributeValue(ModAttributes.MAX_MANA);
            data.setMana((float)Math.min(max, data.getMana() + max * .15));
            STAR_TRACKS.put(owner.getUUID(), 0); data.setBuff("guanlan_passive", 0);
        } else {
            STAR_TRACKS.put(owner.getUUID(), stacks + 1); data.setBuff("guanlan_passive", stacks + 1);
        }
        PlayerDataProvider.syncToClient(owner);
    }

    private static ChargeState charges(ServerPlayer p) { return Q_CHARGES.computeIfAbsent(p.getUUID(), id -> new ChargeState()); }
    private static ArrayList<LivingEntity> enemies(ServerLevel l, ServerPlayer o, Vec3 c, double r) {
        return new ArrayList<>(l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> com.myriad.combat.SkillTargets.alive(e) && e != o && !ally(o, e) && e.distanceToSqr(c) <= r * r));
    }
    private static boolean ally(ServerPlayer o, LivingEntity e) {
        return com.myriad.combat.SkillTargets.ally(o, e);
    }
    private static Vec3 horizontal(Vec3 v) { Vec3 h = new Vec3(v.x, 0, v.z); return h.lengthSqr() < 1e-6 ? new Vec3(0,0,1) : h.normalize(); }
    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) { Vec3 ab=b.subtract(a); double t=Math.max(0,Math.min(1,p.subtract(a).dot(ab)/Math.max(1e-6,ab.lengthSqr()))); return p.distanceTo(a.add(ab.scale(t))); }
    private static void ring(ServerLevel l, Vec3 c, double r, DustParticleOptions d, int n) { for(int i=0;i<n;i++){double a=Math.PI*2*i/n;l.sendParticles(d,c.x+Math.cos(a)*r,c.y+.08,c.z+Math.sin(a)*r,1,0,0,0,0);} }
    private static void ringTo(ServerLevel l, ServerPlayer p, Vec3 c, double r, DustParticleOptions d, int n) { for(int i=0;i<n;i++){double a=Math.PI*2*i/n;l.sendParticles(p,d,false,false,c.x+Math.cos(a)*r,c.y+.08,c.z+Math.sin(a)*r,1,0,0,0,0);} }
    private static void line(ServerLevel l, Vec3 a, Vec3 b, DustParticleOptions d, int n) { for(int i=0;i<=n;i++){Vec3 p=a.lerp(b,i/(double)n);l.sendParticles(d,p.x,p.y+.15,p.z,1,0,0,0,0);} }
    private static void lineTo(ServerLevel l, ServerPlayer o, Vec3 a, Vec3 b, DustParticleOptions d, int n) { for(int i=0;i<=n;i++){Vec3 p=a.lerp(b,i/(double)n);l.sendParticles(o,d,false,false,p.x,p.y+.15,p.z,1,0,0,0,0);} }
    public static void clearFor(UUID id) { STAR_TRACKS.remove(id);Q_CHARGES.remove(id);METEORS.remove(id);RIPPLES.remove(id);DISKS.remove(id);FIRST_LINE_POINT.remove(id);LINES.remove(id);STORMS.remove(id); }
    private static final class ChargeState { int charges=2; long nextRegen; }
    private record PendingMeteor(Vec3 center,Vec3 origin,long startAt,long impactAt,int level,double sp){}
    private record Ripple(Vec3 center,long started,long until,double maxRadius){}
    private static final class StarDisk { final Vec3 center;final long activeFrom,until;final int level;final double sp;boolean activated;final Set<UUID> triggered=new HashSet<>();StarDisk(Vec3 c,long a,long u,int l,double s){center=c;activeFrom=a;until=u;level=l;sp=s;} }
    private static final class StarLine { final Vec3 a,b;final long until;final int level;final double sp;final Set<UUID> hit=new HashSet<>();StarLine(Vec3 a,Vec3 b,long u,int l,double s){this.a=a;this.b=b;until=u;level=l;sp=s;} }
    private static final class StarStorm { final Vec3 center;long nextSpawn;int remaining;final int level;final double sp;final ArrayList<FallingStar> falling=new ArrayList<>();StarStorm(Vec3 c,long n,int r,int l,double s){center=c;nextSpawn=n;remaining=r;level=l;sp=s;} }
    private record FallingStar(Vec3 origin,Vec3 impact,long started,long impactAt,boolean last){}
}
