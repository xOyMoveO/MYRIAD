package com.myriad.character.skills.zhechuan;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.PlacementUtil;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 游隙·折川：以自设游标组织往返路线的高机动战士。所有位移均由服务端计算并同步客户端。 */
public final class ZheChuanSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    private enum MoveKind { OUTBOUND, RETURN, RETREAT }

    public static final int Q_PLACEMENT = 70;
    public static final String Q_CHARGE = "skill_charge_tourendingjie";
    private static final String Q_REMAINING = Q_CHARGE + "_remaining";
    private static final String Q_TOTAL = Q_CHARGE + "_total";
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x45E4F2, 1.0F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xEFFDFF, .9F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xF5C85B, 1.05F);
    private static final DustParticleOptions FAINT_CYAN = new DustParticleOptions(0x287A86, .62F);
    private static final DustParticleOptions DARK_GOLD = new DustParticleOptions(0x80662D, .72F);
    private static final DustParticleOptions RED = new DustParticleOptions(0xEF4B4B, 1.0F);
    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Query or shorten only the charge currently recovering; never grant a charge directly. */
    public static int runeRecharge(ServerPlayer p, double fraction) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.ready.isEmpty()) return 0;
        long now = p.level().getGameTime();
        int remaining = (int)Math.max(0, s.ready.peekFirst() - now);
        if (fraction > 0) {
            com.myriad.character.SequentialChargeTimers.moveFirstTo(s.ready,
                    now + Math.max(1, (int)Math.ceil(remaining * (1 - fraction))));
        }
        return remaining;
    }
    private static final Map<UUID, Integer> DISCONNECTED_BONUS = new HashMap<>();
    private final Type type;

    public ZheChuanSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "zhechuan_passive";
            case Q -> "tourendingjie";
            case W -> "xunbiaoguoxi";
            case E -> "chefengnidu";
            case R -> "zhubiaowujinhuilang";
        }, switch (type) {
            case PASSIVE -> 0; case Q -> 180; case W -> 300; case E -> 260; case R -> 2000;
        }, switch (type) {
            case PASSIVE -> 0.0F; case Q -> 57.6F; case W -> 100.8F; case E -> 86.4F; case R -> 288.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getMaxCharges() { return type == Type.Q ? 3 : 1; }
    @Override public int getCooldownTicks(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case Q -> Math.max(135, 180 - (lv - 1) * 15);
            case W -> Math.max(210, 300 - (lv - 1) * 30);
            case E -> Math.max(170, 260 - (lv - 1) * 30);
            case R -> Math.max(1400, 2000 - (lv - 1) * 300);
            default -> 0;
        };
    }
    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "反向折返造成14+2×角色等级+35%额外物攻；每次触发使本局后续回文斩永久+3伤害";
            case Q -> "3层充能；12格内投放游标，落点造成" + (14 + (lv - 1) * 7) + "+30%额外物攻";
            case W -> "沿路径突进至游标，造成" + (24 + (lv - 1) * 10) + "+65%额外物攻";
            case E -> "无路线时后撤；有路线时逆向折返并造成" + (16 + (lv - 1) * 8) + "+45%额外物攻";
            case R -> (10 + lv - 1) + "秒内扩容至5枚游标并重置W/E；连续折返3次解锁终结回浪";
        };
    }
    @Override public void cast(ServerPlayer player, PlayerData data) { }

    public static void handleCast(ServerPlayer p, PlayerData data, int slot, Skill raw,
            boolean noCd, boolean noMana) {
        if (!(raw instanceof ZheChuanSkills skill)) return;
        State s = state(p); long now = p.level().getGameTime();
        if (s.move != null) { message(p, "message.myriad.zhechuan_moving"); return; }
        switch (skill.type) {
            case Q -> beginQ(p, data, s, skill, noCd, noMana, now);
            case W -> castW(p, data, s, skill, noCd, noMana, now);
            case E -> castE(p, data, s, skill, noCd, noMana, now);
            case R -> castR(p, data, s, skill, noCd, noMana, now);
            default -> { }
        }
    }

    private static void beginQ(ServerPlayer p, PlayerData data, State s, ZheChuanSkills skill,
            boolean noCd, boolean noMana, long now) {
        recoverCharges(p, data, s, skill, now);
        if (data.getPlacementType() != 0) return;
        if (!noCd && s.charges <= 0) { message(p, "message.myriad.zhechuan_no_charge"); return; }
        if (!noMana && data.getMana() < skill.getManaCost()) { message(p, "message.myriad.not_enough_mana"); return; }
        s.qNoCd = noCd; s.qNoMana = noMana; s.placementUntil = now + 60;
        data.setPlacementType(Q_PLACEMENT); data.setBuff("tourendingjie_recast", 60);
        PlayerDataProvider.syncToClient(p);
    }

    public static void confirmPlacement(ServerPlayer p, PlayerData data) {
        State s = STATES.get(p.getUUID());
        if (s == null || data.getPlacementType() != Q_PLACEMENT) return;
        long now = p.level().getGameTime(); Vec3 target = PlacementUtil.pointOnGround(p, 12);
        if (target == null || p.position().distanceTo(target) > 13
                || !com.myriad.game.MapSystem.contains(target.x, target.z)) {
            message(p, "message.myriad.zhechuan_invalid_marker"); return;
        }
        ZheChuanSkills q = new ZheChuanSkills(Type.Q); recoverCharges(p, data, s, q, now);
        if ((!s.qNoCd && s.charges <= 0) || (!s.qNoMana && data.getMana() < q.getManaCost())) { cancelPlacement(p, data); return; }
        if (!s.qNoMana) data.spendMana(q.getManaCost());
        com.myriad.character.SkillPresentation.cast(p, q);
        if (!s.qNoCd) {
            s.charges--; int total = reducedCooldown(p, q.getCooldownTicks(Math.max(1, data.skillLevel(q.getId()))));
            s.rechargeTotal = total;
            com.myriad.character.SequentialChargeTimers.enqueue(s.ready, now, total);
        }
        s.flights.add(new MarkerFlight(p.getEyePosition().add(0, -.25, 0), target, now, now + 5,
                Math.max(1, data.skillLevel(q.getId())), extraAd(p)));
        finishPlacement(data, s); syncCharges(data, s, now); PlayerDataProvider.syncToClient(p);
    }

    public static void cancelPlacement(ServerPlayer p, PlayerData data) {
        State s = STATES.get(p.getUUID()); if (s == null || data.getPlacementType() != Q_PLACEMENT) return;
        finishPlacement(data, s); PlayerDataProvider.syncToClient(p);
    }
    private static void finishPlacement(PlayerData data, State s) {
        data.setPlacementType(0); data.setBuff("tourendingjie_recast", 0); s.placementUntil = 0;
    }

    private static void castW(ServerPlayer p, PlayerData data, State s, ZheChuanSkills skill,
            boolean noCd, boolean noMana, long now) {
        if (!ready(data, skill, noCd) || !mana(p, data, skill, noMana)) return;
        Marker marker = aimedMarker(p, s, now, 16);
        if (marker == null) { message(p, "message.myriad.zhechuan_aim_marker"); return; }
        if (s.rUntil > now && s.rVisited.contains(marker.id)) { message(p, "message.myriad.zhechuan_marker_used"); return; }
        spend(data, skill, noCd, noMana, p);
        Vec3 start = p.position(); int ticks = Mth.clamp((int)Math.ceil(start.distanceTo(marker.pos) / 1.8), 4, 8);
        drawPrivateRoutePreview(p, start.add(0, .12, 0), marker.pos.add(0, .12, 0), CYAN, now);
        drawPrivateMarkerOutline(p, marker, now);
        squareRingVisible(p, marker.pos.add(0, .09, 0), .9, WHITE, 4);
        verticalLineVisible(p, marker.pos.add(0, .08, 0), 1.45, CYAN, 5);
        startMove(p, s, new Move(MoveKind.OUTBOUND, start, marker.pos, ticks,
                Math.max(1, data.skillLevel(skill.getId())), marker.id, null));
    }

    private static void castE(ServerPlayer p, PlayerData data, State s, ZheChuanSkills skill,
            boolean noCd, boolean noMana, long now) {
        if (!ready(data, skill, noCd) || !mana(p, data, skill, noMana)) return;
        if (s.route != null && s.route.until > now && p.position().distanceTo(s.route.end) > 1.0) {
            message(p, "message.myriad.zhechuan_route_departed"); return;
        }
        spend(data, skill, noCd, noMana, p);
        if (s.route != null && s.route.until > now) {
            Route route = s.route; int ticks = Mth.clamp((int)Math.ceil(p.position().distanceTo(route.start) / 1.8), 4, 8);
            startMove(p, s, new Move(MoveKind.RETURN, p.position(), route.start, ticks,
                    Math.max(1, data.skillLevel(skill.getId())), -1, route));
        } else {
            Vec3 start = p.position(); Vec3 look = horizontal(p.getLookAngle()); Vec3 target = start.subtract(look.scale(3.5));
            s.retreat = new Marker(s.nextId++, start, now + 100, true);
            retreatStartVisual(p, start, look);
            startMove(p, s, new Move(MoveKind.RETREAT, start, target, 5,
                    Math.max(1, data.skillLevel(skill.getId())), -1, null));
        }
    }

    private static void castR(ServerPlayer p, PlayerData data, State s, ZheChuanSkills skill,
            boolean noCd, boolean noMana, long now) {
        if (!ready(data, skill, noCd) || !mana(p, data, skill, noMana)) return;
        int duration = corridorDurationTicks(data.skillLevel(skill.getId()));
        spend(data, skill, noCd, noMana, p); s.rUntil = now + duration; s.rVisited.clear();
        s.returnStacks = 0; s.finisherUsed = false; s.charges = Math.min(3, s.charges + 2);
        data.setCooldown("xunbiaoguoxi", 0); data.setCooldown("chefengnidu", 0);
        for (Marker m : s.markers) m.until = Math.max(m.until, s.rUntil + 20);
        if (s.retreat != null) s.retreat.until = Math.max(s.retreat.until, s.rUntil);
        data.setBuff("zhubiaowujinhuilang", duration); syncCharges(data, s, now);
        ServerLevel l = p.level(); ringVisible(p, p.position().add(0, .1, 0), 4, GOLD, 32);
        squareRingVisible(p, p.position().add(0, .14, 0), 2.7, GOLD, 5);
        for (Marker marker : s.markers) verticalLineVisible(p, marker.pos.add(0, .08, 0), 1.8, GOLD, 6);
        if (s.retreat != null) verticalLineVisible(p, s.retreat.pos.add(0, .08, 0), 1.25, GOLD, 4);
        l.playSound(null, p.blockPosition(), ModSounds.ZHECHUAN_CORRIDOR.get(), SoundSource.PLAYERS, .75F, 1F);
         PlayerDataProvider.syncToClient(p);
    }

    /** 无尽回廊：1级10秒，之后每级增加1秒。 */
    private static int corridorDurationTicks(int level) {
        return (10 + Math.max(1, level) - 1) * 20;
    }

    public static void tick(ServerPlayer p, PlayerData data) {
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.ZHECHUAN) return;
        State s = state(p); long now = p.level().getGameTime();
        recoverCharges(p, data, s, new ZheChuanSkills(Type.Q), now);
        if (s.placementUntil > 0) {
            if (now >= s.placementUntil) cancelPlacement(p, data);
            else {
                data.setBuff("tourendingjie_recast", (int)(s.placementUntil - now));
                drawQPlacementPreview(p, now);
            }
        }
        tickFlights(p, data, s, now); tickMarkers(p, data, s, now); tickMove(p, data, s, now);
        tickGoMarks(p, s, now); tickPalindromeEffects(p, s, now); tickEchoes(p, s, now);
        if (s.route != null) {
            if (now >= s.route.until) { s.route = null; data.setBuff("chefengnidu_recast", 0); }
            else {
                data.setBuff("chefengnidu_recast", (int)(s.route.until - now));
                if (now % 3 == 0) lineVisible(p, s.route.start.add(0, .1, 0), s.route.end.add(0, .1, 0),
                        s.route.until - now <= 10 ? RED : FAINT_CYAN, 8);
            }
        }
        if (s.rUntil > 0 && now >= s.rUntil) { s.rUntil = 0; s.rVisited.clear(); s.returnStacks = 0; data.setBuff("zhubiaowujinhuilang", 0); }
        else if (s.rUntil > now) data.setBuff("zhubiaowujinhuilang", (int)(s.rUntil - now));
        if (s.rUntil > now && s.returnStacks >= 3 && !s.finisherUsed) drawFinisherReady(p, now);
        data.setBuff("zhechuan_markers", activeMarkerCount(s, now)); data.setBuff("zhechuan_return_stacks", s.returnStacks);
        data.setBuff("zhechuan_palindrome_bonus", s.palindromeBonus);
        for (int i = 0; i < 5; i++) {
            int remain = i < s.markers.size() ? (int)Math.max(0, s.markers.get(i).until - now) : 0;
            data.setBuff("zhechuan_marker_" + (i + 1), remain);
        }
        data.setBuff("zhechuan_retreat", s.retreat == null ? 0 : (int)Math.max(0, s.retreat.until - now));
        syncCharges(data, s, now);
        PlayerDataProvider.syncToClient(p);
    }

    private static void tickFlights(ServerPlayer p, PlayerData data, State s, long now) {
        Iterator<MarkerFlight> it = s.flights.iterator();
        while (it.hasNext()) {
            MarkerFlight f = it.next(); double t = Mth.clamp((now - f.started) / (double)Math.max(1, f.ends - f.started), 0, 1);
            Vec3 pos = f.from.lerp(f.to, t).add(0, Math.sin(Math.PI * t) * 1.1, 0);
            sendVisible(p, pos, CYAN, 1, .035);
            if ((now & 1) == 0) {
                Vec3 previous = f.from.lerp(f.to, Math.max(0, t - .18));
                lineVisible(p, previous.add(0, .05, 0), pos.add(0, .05, 0), WHITE, 2);
            }
            if (now < f.ends) continue;
            it.remove(); Marker placed = addMarker(p, s, f.to, now); segmentedRippleVisible(p, f.to.add(0, .06, 0), 1.6, CYAN);
            markerPlantBurst(p, placed);
            for (LivingEntity target : enemies(p, f.to, 1.8)) DamageCalc.dealSkillDamage(p, target,
                    56 + (f.level - 1) * 28 + f.extraAd * 1.20F, DamageCalc.Kind.PHYSICAL, 0);
            p.level().playSound(null, f.to.x, f.to.y, f.to.z, ModSounds.ZHECHUAN_THROW.get(),
                    SoundSource.PLAYERS, .8F, .95F + p.getRandom().nextFloat() * .1F);
        }
    }

    private static void tickMarkers(ServerPlayer p, PlayerData data, State s, long now) {
        s.markers.removeIf(m -> m.until <= now); if (s.retreat != null && s.retreat.until <= now) s.retreat = null;
        for (Marker m : s.markers) markerVisual(p, s, m, now);
        if (s.retreat != null) markerVisual(p, s, s.retreat, now);
        Marker aimed = s.move == null && data.getPlacementType() == 0 ? aimedMarker(p, s, now, 16) : null;
        if (aimed != null) {
            drawPrivateRoutePreview(p, p.position().add(0, .12, 0), aimed.pos.add(0, .12, 0), WHITE, now);
            drawPrivateMarkerOutline(p, aimed, now);
        }
    }

    private static void tickMove(ServerPlayer p, PlayerData data, State s, long now) {
        Move m = s.move; if (m == null) return;
        if (p.hasEffect(MobEffects.SLOWNESS) && p.getEffect(MobEffects.SLOWNESS).getAmplifier() >= 200) {
            finishMove(p, data, s, false, now); return;
        }
        Vec3 before = p.position();
        if (m.previous != null && before.distanceToSqr(m.previous) > .0001) {
            // Only actual server-observed movement contributes hits and the recorded route.
            if (before.distanceTo(m.previous) > 2.5) { finishMove(p, data, s, false, now); return; }
            if (m.kind != MoveKind.RETREAT) hitMovePath(p, data, s, m, m.previous, before, now);
            if (m.kind == MoveKind.OUTBOUND) m.travelled.add(before);
        }
        m.previous = before;
        Vec3 waypoint = m.target;
        if (m.kind == MoveKind.RETURN && m.route != null) {
            while (m.returnIndex >= 0 && before.distanceTo(m.route.points.get(m.returnIndex)) < .2) m.returnIndex--;
            if (m.returnIndex >= 0) waypoint = m.route.points.get(m.returnIndex);
        }
        if (before.distanceTo(m.target) < .08 && (m.kind != MoveKind.RETURN || m.returnIndex < 0)) {
            finishMove(p, data, s, true, now); return;
        }
        if (--m.ticks <= 0) { finishMove(p, data, s, false, now); return; }
        Vec3 step = waypoint.subtract(before);
        double allowedStep = m.stepLength;
        boolean finalWaypoint = m.kind != MoveKind.RETURN || m.returnIndex <= 0;
        // 循标与折返在最终落点前平滑减速，避免最后一帧仍携带整步高速 motion。
        // 中途的折返轨迹点不减速，防止沿原路线返回时逐点停顿。
        if (finalWaypoint && step.length() < Math.max(.45, m.stepLength * 1.75)) {
            allowedStep = Math.min(allowedStep, Math.max(.035, step.length() * .55));
        }
        if (step.length() > allowedStep) step = step.normalize().scale(allowedStep);
        m.direction = horizontal(step);
        Vec3 next = before.add(step);
        boolean clear = com.myriad.game.MapSystem.contains(next.x, next.z);
        int checks = Math.max(1, (int)Math.ceil(step.length() / .2));
        for (int i = 1; clear && i <= checks; i++)
            clear = p.level().noCollision(p, p.getBoundingBox().move(step.scale(i / (double)checks)));
        if (!clear) {
            finishMove(p, data, s, false, now); return;
        }
        p.setDeltaMovement(step.x, step.y, step.z); p.hurtMarked = true;
        PacketDistributor.sendToPlayer(p, new DashPayload((float)step.x, (float)step.y, (float)step.z, 2));
        Vec3 center = before.add(step.scale(.5));
        if (m.kind == MoveKind.RETREAT) {
            retreatTrail(p, center, m.direction, m.ticks);
        } else {
            DustParticleOptions main = m.kind == MoveKind.RETURN ? GOLD : CYAN;
            DustParticleOptions edge = m.kind == MoveKind.RETURN ? WHITE : FAINT_CYAN;
            drawBladeRibbon(p, before, before.add(step), m.direction, main, edge, m.ticks);
        }
    }

    private static void hitMovePath(ServerPlayer p, PlayerData data, State s, Move m, Vec3 a, Vec3 b, long now) {
        AABB box = new AABB(a, b).inflate(1.2);
        for (LivingEntity target : com.myriad.combat.DashHitbox.targets(p, a, b, 1.5, m.hit)) {
            if (m.hit.contains(target.getUUID())) continue;
            m.hit.add(target.getUUID());
            float damage = m.kind == MoveKind.OUTBOUND
                    ? 96 + (m.level - 1) * 40 + extraAd(p) * 2.60F
                    : 64 + (m.level - 1) * 32 + extraAd(p) * 1.80F;
            DamageCalc.dealSkillDamage(p, target, damage, DamageCalc.Kind.PHYSICAL, m.kind == MoveKind.OUTBOUND ? 1 : 2);
            pathHitSlash(p, target, m.direction, m.kind == MoveKind.RETURN ? GOLD : CYAN);
            if (target instanceof ServerPlayer) {
                if (m.kind == MoveKind.OUTBOUND) {
                    s.goMarks.put(target.getUUID(), new GoMark(m.direction, now + 60, m.routeId));
                    halfArrowVisible(p, target.position().add(0, 1.05, 0), m.direction, CYAN);
                }
                else tryPassive(p, data, s, target, m, now);
            }
        }
    }

    private static void tryPassive(ServerPlayer p, PlayerData data, State s, LivingEntity target, Move m, long now) {
        GoMark go = s.goMarks.get(target.getUUID()); if (go == null || go.until < now || m.route == null) return;
        if (go.routeId != m.route.id) return;
        Vec3 back = m.direction; double dot = Mth.clamp(go.direction.dot(back), -1, 1);
        if (Math.toDegrees(Math.acos(dot)) < 150 || s.passiveCd.getOrDefault(target.getUUID(), 0L) > now) return;
        DamageCalc.dealSkillDamage(p, target,
                56 + data.getLevel() * 8 + extraAd(p) * 1.40F + s.palindromeBonus,
                DamageCalc.Kind.PHYSICAL, 4);
        s.palindromeBonus += 3;
        s.passiveCd.put(target.getUUID(), now + 120); s.returnStacks = Math.min(3, s.returnStacks + 1);
        s.goMarks.remove(target.getUUID());
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, 25, 0, false, false, true));
        palindromeClosure(p, target.position().add(0, 1.0, 0), go.direction, back);
        s.palindromeEffects.add(new PalindromeEffect(target.position().add(0, 1.0, 0),
                go.direction, back, now, now + 12));
        p.level().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.ZHECHUAN_PALINDROME.get(),
                SoundSource.PLAYERS, .8F, 1F);
    }

    private static void finishMove(ServerPlayer p, PlayerData data, State s, boolean success, long now) {
        Move m = s.move; s.move = null;
        boolean preciseEndpoint = success && m != null
                && (m.kind == MoveKind.OUTBOUND || m.kind == MoveKind.RETURN);
        if (preciseEndpoint) {
            // 折返窗口依赖玩家仍站在记录终点附近，因此服务端与本地客户端同步清除
            // 最后一帧残余速度；RETREAT 等普通移动仍沿用全局的平滑惯性规则。
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
            PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, -1));
        } else {
            PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0));
        }
        if (!success) p.setDeltaMovement(Vec3.ZERO);
        if (m == null) return;
        if (!success) {
            s.route = null; s.goMarks.clear(); data.setBuff("chefengnidu_recast", 0);
            PlayerDataProvider.syncToClient(p); return;
        }
        if (m.kind == MoveKind.OUTBOUND) {
            s.route = new Route(m.start, p.position(), horizontal(m.target.subtract(m.start)), now + 60,
                    m.routeId, List.copyOf(m.travelled));
            data.setBuff("chefengnidu_recast", 60); Marker marker = markerById(s, m.markerId);
            if (marker != null) marker.until = Math.max(marker.until, now + 120);
            halfReturnGlyph(p, m.start.add(0, .09, 0), m.direction, GOLD);
            if (marker != null && marker.retreat) s.retreat = null;
            if (s.rUntil > now) { s.rVisited.add(m.markerId); data.setCooldown("chefengnidu", 0); }
        } else if (m.kind == MoveKind.RETURN) {
            int w = data.getCooldown("xunbiaoguoxi"); data.setCooldown("xunbiaoguoxi", s.rUntil > now ? 1 : (int)Math.floor(w * .65));
            if (s.rUntil > now && m.route != null) s.echoes.add(new RouteEcho(m.route.start, m.route.end, now + 24));
            s.route = null; data.setBuff("chefengnidu_recast", 0);
            if (s.rUntil > now && s.returnStacks >= 3 && !s.finisherUsed) {
                s.finisherUsed = true; float damage = 260 + (Math.max(1, data.skillLevel("zhubiaowujinhuilang")) - 1) * 100 + extraAd(p) * 2.80F;
                for (LivingEntity e : enemies(p, p.position(), 4)) { DamageCalc.dealSkillDamage(p, e, damage, DamageCalc.Kind.PHYSICAL, 3); e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1)); }
                hundredReturnFinisher(p, p.position().add(0, .12, 0));
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ZHECHUAN_FINISHER.get(),
                        SoundSource.PLAYERS, .85F, 1F);
            }
        }
        PlayerDataProvider.syncToClient(p);
    }

    private static void startMove(ServerPlayer p, State s, Move move) {
        if (move.kind == MoveKind.OUTBOUND) {
            s.route = null; s.goMarks.clear(); move.routeId = ++s.nextRouteId;
        }
        move.previous = p.position(); move.travelled.add(p.position());
        move.returnIndex = move.route == null ? -1 : move.route.points.size() - 1;
        move.ticks = 40;
        s.move = move; Vec3 step = move.target.subtract(move.start).scale(1.0 / move.totalTicks); move.stepLength = Math.min(1.8, step.length());
        // The next authoritative tick validates the first segment before motion starts.
        PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0));
        p.setDeltaMovement(Vec3.ZERO); p.hurtMarked = true;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                (move.kind == MoveKind.RETURN ? ModSounds.ZHECHUAN_RETURN : ModSounds.ZHECHUAN_OUTBOUND).get(),
                SoundSource.PLAYERS, .65F, .95F + p.getRandom().nextFloat() * .1F);
    }

    public static void onIncomingDamage(ServerPlayer p, LivingIncomingDamageEvent event) {
        State s = STATES.get(p.getUUID()); if (s == null || s.move == null || event.getAmount() <= 0) return;
        float scale = s.move.kind == MoveKind.RETREAT ? .80F : .75F;
        event.getContainer().setNewDamage(event.getAmount() * scale);
    }

    private static Marker addMarker(ServerPlayer p, State s, Vec3 pos, long now) {
        int max = s.rUntil > now ? 5 : 3;
        while (s.markers.size() >= max) markerShatter(p, s.markers.remove(0).pos);
        Marker marker = new Marker(s.nextId++, pos, now + 240, false);
        s.markers.add(marker);
        return marker;
    }
    private static Marker aimedMarker(ServerPlayer p, State s, long now, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle().normalize(); Marker best = null; double bestScore = Double.MAX_VALUE;
        List<Marker> all = new ArrayList<>(s.markers); if (s.retreat != null && s.retreat.until > now) all.add(s.retreat);
        for (Marker m : all) {
            Vec3 delta = m.pos.add(0, .45, 0).subtract(eye); double along = delta.dot(look);
            if (along < 0 || along > range || p.position().distanceTo(m.pos) > range + 1) continue;
            double off = delta.subtract(look.scale(along)).length(); if (off <= 1.8 && off + along * .015 < bestScore) { best = m; bestScore = off + along * .015; }
        }
        return best;
    }
    private static Marker markerById(State s, int id) { for (Marker m : s.markers) if (m.id == id) return m; return s.retreat != null && s.retreat.id == id ? s.retreat : null; }
    private static int activeMarkerCount(State s, long now) { int n = 0; for (Marker m : s.markers) if (m.until > now) n++; return n + (s.retreat != null && s.retreat.until > now ? 1 : 0); }
    private static void recoverCharges(ServerPlayer p, PlayerData data, State s, ZheChuanSkills q, long now) { while (!s.ready.isEmpty() && s.ready.peekFirst() <= now) { s.ready.removeFirst(); s.charges = Math.min(3, s.charges + 1); } syncCharges(data, s, now); }
    private static void syncCharges(PlayerData data, State s, long now) { data.setBuff(Q_CHARGE, s.charges); long remain = s.ready.isEmpty() ? 0 : Math.max(0, s.ready.peekFirst() - now); data.setBuff(Q_REMAINING, (int)remain); data.setBuff(Q_TOTAL, Math.max(1, s.rechargeTotal)); }
    private static boolean ready(PlayerData data, ZheChuanSkills skill, boolean noCd) { if (!noCd && data.getCooldown(skill.getId()) > 0) return false; return true; }
    private static boolean mana(ServerPlayer p, PlayerData data, ZheChuanSkills skill, boolean noMana) { if (!noMana && data.getMana() < skill.getManaCost()) { message(p, "message.myriad.not_enough_mana"); return false; } return true; }
    private static void spend(PlayerData data, ZheChuanSkills skill, boolean noCd, boolean noMana, ServerPlayer p) { com.myriad.character.SkillPresentation.cast(p, skill); if (!noMana) data.spendMana(skill.getManaCost()); if (!noCd) data.setCooldown(skill.getId(), reducedCooldown(p, skill.getCooldownTicks(Math.max(1, data.skillLevel(skill.getId()))))); }
    private static int reducedCooldown(ServerPlayer p, int base) { double c = Mth.clamp(p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION), 0, 60); return Math.max(1, (int)Math.ceil(base * (1 - c / 100))); }
    private static float extraAd(ServerPlayer p) { return (float)Math.max(0, p.getAttributeValue(ModAttributes.PHYSICAL_ATTACK) - CharacterType.ZHECHUAN.initialAd()); }
    private static Vec3 horizontal(Vec3 v) { Vec3 h = new Vec3(v.x, 0, v.z); return h.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : h.normalize(); }
    private static boolean ally(ServerPlayer p, LivingEntity e) { return com.myriad.combat.SkillTargets.ally(p, e); }
    private static List<LivingEntity> enemies(ServerPlayer p, Vec3 center, double radius) { return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), e -> com.myriad.combat.SkillTargets.alive(e) && !ally(p, e)); }
    private static double distanceSegment(Vec3 p, Vec3 a, Vec3 b) { Vec3 ab = b.subtract(a); if (ab.lengthSqr() < 1e-8) return p.distanceTo(a); double t = Mth.clamp(p.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1); return p.distanceTo(a.add(ab.scale(t))); }
    /** Q选点只对施法者显示；落点、伤害半径和抛物线都来自服务端最终选点算法。 */
    private static void drawQPlacementPreview(ServerPlayer p, long now) {
        Vec3 target = PlacementUtil.pointOnGround(p, 12);
        boolean valid = target != null && p.position().distanceTo(target) <= 13
                && com.myriad.game.MapSystem.contains(target.x, target.z);
        if (target == null) target = p.getEyePosition().add(p.getLookAngle().normalize().scale(12));
        DustParticleOptions color = valid ? CYAN : RED;
        segmentedRingPrivate(p, target.add(0, .055, 0), 1.6, color, 24, 3);
        squareRingPrivate(p, target.add(0, .075, 0), .48, valid ? WHITE : RED, 3);
        drawMarkerBladePrivate(p, target, (int)(now / 4 % 3), color, .85);
        drawParabolaPrivate(p, p.getEyePosition().add(0, -.25, 0), target, color);
        if (!valid) {
            privateLine(p, target.add(-.65, .08, -.65), target.add(.65, .08, .65), RED, 5);
            privateLine(p, target.add(-.65, .08, .65), target.add(.65, .08, -.65), RED, 5);
        }
    }

    private static void drawParabolaPrivate(ServerPlayer p, Vec3 from, Vec3 to, DustParticleOptions color) {
        for (int i = 0; i <= 10; i += 2) {
            double t = i / 10.0;
            Vec3 point = from.lerp(to, t).add(0, Math.sin(Math.PI * t) * 1.1, 0);
            privateParticle(p, point, color);
        }
    }

    /** 三类刃标分别使用直刃、折刃和双叉刃轮廓；R期间转为金框，已访问标记变暗金。 */
    private static void markerVisual(ServerPlayer owner, State s, Marker marker, long now) {
        long remain = marker.until - now;
        if (remain <= 10 && (now & 3) < 2) return;
        if ((now & 1) != 0) return;
        Vec3 c = marker.pos;
        if (marker.retreat) {
            diamondVisible(owner, c.add(0, .12, 0), .34, WHITE, 3);
            verticalLineVisible(owner, c.add(0, .08, 0), .48, FAINT_CYAN, 2);
            return;
        }
        boolean corridor = s.rUntil > now;
        DustParticleOptions color = corridor
                ? (s.rVisited.contains(marker.id) ? DARK_GOLD : GOLD) : CYAN;
        drawMarkerBladeVisible(owner, c, Math.floorMod(marker.id - 1, 3), color, 1.0);
        segmentedRippleVisible(owner, c.add(0, .045, 0), .62, color);
        if (corridor) squareRingVisible(owner, c.add(0, .065, 0), .72, color, 3);
    }

    private static void drawMarkerBladeVisible(ServerPlayer owner, Vec3 c, int type,
            DustParticleOptions color, double height) {
        Vec3 base = c.add(0, .08, 0), top = base.add(0, height, 0);
        verticalLineVisible(owner, base, height, color, 4);
        if (type == 0) {
            lineVisible(owner, base.add(-.28, .28, 0), base.add(.28, .28, 0), WHITE, 3);
        } else if (type == 1) {
            lineVisible(owner, top, top.add(.32, -.2, 0), color, 2);
            lineVisible(owner, base.add(-.25, .28, 0), base.add(.18, .36, 0), WHITE, 2);
        } else {
            lineVisible(owner, top.add(0, -.18, 0), top.add(-.28, .1, 0), color, 2);
            lineVisible(owner, top.add(0, -.18, 0), top.add(.28, .1, 0), color, 2);
            lineVisible(owner, base.add(-.24, .25, 0), base.add(.24, .25, 0), WHITE, 2);
        }
    }

    private static void drawMarkerBladePrivate(ServerPlayer viewer, Vec3 c, int type,
            DustParticleOptions color, double height) {
        Vec3 base = c.add(0, .08, 0), top = base.add(0, height, 0);
        privateLine(viewer, base, top, color, 4);
        if (type == 0) privateLine(viewer, base.add(-.28, .25, 0), base.add(.28, .25, 0), WHITE, 3);
        else if (type == 1) {
            privateLine(viewer, top, top.add(.3, -.18, 0), color, 2);
            privateLine(viewer, base.add(-.23, .26, 0), base.add(.18, .34, 0), WHITE, 2);
        } else {
            privateLine(viewer, top.add(0, -.16, 0), top.add(-.27, .09, 0), color, 2);
            privateLine(viewer, top.add(0, -.16, 0), top.add(.27, .09, 0), color, 2);
        }
    }

    private static void drawPrivateRoutePreview(ServerPlayer p, Vec3 from, Vec3 to,
            DustParticleOptions color, long now) {
        Vec3 direction = horizontal(to.subtract(from)); Vec3 side = new Vec3(-direction.z, 0, direction.x);
        int points = Math.max(4, (int)Math.ceil(from.distanceTo(to) * 1.4));
        for (int i = (int)(now & 1); i <= points; i += 2) {
            Vec3 q = from.lerp(to, i / (double)points).add(side.scale(i % 4 == 0 ? .08 : -.08));
            privateParticle(p, q, color);
        }
    }

    private static void drawPrivateMarkerOutline(ServerPlayer p, Marker marker, long now) {
        DustParticleOptions color = marker.retreat ? WHITE : GOLD;
        squareRingPrivate(p, marker.pos.add(0, .07, 0), .78 + Math.sin(now * .2) * .06, color, 4);
        drawMarkerBladePrivate(p, marker.pos, marker.retreat ? 0 : Math.floorMod(marker.id - 1, 3), color, 1.12);
    }

    private static void retreatStartVisual(ServerPlayer p, Vec3 start, Vec3 look) {
        for (int i = 1; i <= 3; i++) {
            Vec3 c = start.add(look.scale(i * .42)).add(0, .06, 0);
            segmentedRippleVisible(p, c, .35 + i * .18, i == 3 ? CYAN : FAINT_CYAN);
        }
    }

    private static void retreatTrail(ServerPlayer p, Vec3 c, Vec3 direction, int tick) {
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        sendVisible(p, c.add(side.scale(.35)).add(0, .18, 0), WHITE, 1, 0);
        sendVisible(p, c.subtract(side.scale(.35)).add(0, .18, 0), FAINT_CYAN, 1, 0);
        if ((tick & 1) == 0) segmentedRippleVisible(p, c.add(direction.scale(.55)).add(0, .04, 0), .48, FAINT_CYAN);
    }

    private static void drawBladeRibbon(ServerPlayer p, Vec3 from, Vec3 to, Vec3 direction,
            DustParticleOptions main, DustParticleOptions edge, int tick) {
        if ((tick & 1) == 0) texturedEffect(p, from.lerp(to, .5).add(0, .65, 0), false, main == GOLD, .7);
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        lineVisible(p, from.add(side.scale(.16)).add(0, .22, 0), to.add(side.scale(.16)).add(0, .22, 0), main, 3);
        lineVisible(p, from.subtract(side.scale(.16)).add(0, .08, 0), to.subtract(side.scale(.16)).add(0, .08, 0), edge, 2);
        Vec3 center = from.lerp(to, .55).add(0, .48, 0);
        double sign = (tick & 1) == 0 ? 1 : -1;
        lineVisible(p, center.subtract(side.scale(.62 * sign)).add(0, -.35, 0),
                center.add(side.scale(.62 * sign)).add(0, .35, 0), main, 4);
    }

    private static void pathHitSlash(ServerPlayer p, LivingEntity target, Vec3 direction,
            DustParticleOptions color) {
        Vec3 center = target.position().add(0, target.getBbHeight() * .55, 0);
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        lineVisible(p, center.subtract(side.scale(.7)).add(0, -.38, 0),
                center.add(side.scale(.7)).add(0, .38, 0), color, 6);
    }

    private static void tickGoMarks(ServerPlayer p, State s, long now) {
        Iterator<Map.Entry<UUID, GoMark>> it = s.goMarks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GoMark> entry = it.next(); GoMark mark = entry.getValue();
            if (mark.until <= now) { it.remove(); continue; }
            if ((now & 1) == 0 && p.level().getEntity(entry.getKey()) instanceof LivingEntity target && com.myriad.combat.SkillTargets.alive(target))
                drawGoMark(p, target, mark, now);
        }
        s.passiveCd.entrySet().removeIf(e -> e.getValue() <= now);
    }

    /** 去痕：带明确朝向的半箭纹、双层短刃和逐渐收缩的青色方环。 */
    private static void drawGoMark(ServerPlayer p, LivingEntity target, GoMark mark, long now) {
        Vec3 c = target.position().add(0, target.getBbHeight() * .64, 0);
        Vec3 d = horizontal(mark.direction), side = new Vec3(-d.z, 0, d.x);
        halfArrowVisible(p, c, d, CYAN);
        lineVisible(p, c.subtract(side.scale(.38)).subtract(d.scale(.25)).add(0, -.22, 0),
                c.add(side.scale(.38)).add(d.scale(.18)).add(0, .22, 0), WHITE, 3);
        lineVisible(p, c.add(side.scale(.34)).subtract(d.scale(.18)).add(0, -.18, 0),
                c.subtract(side.scale(.34)).add(d.scale(.25)).add(0, .18, 0), FAINT_CYAN, 3);
        double pulse = .42 + .07 * Math.sin(now * .42);
        squareRingVisible(p, c.add(0, -.48, 0), pulse, FAINT_CYAN, 2);
        Vec3 forwardSpark = c.add(d.scale(.78 + .08 * Math.sin(now * .3)));
        sendVisible(p, forwardSpark, WHITE, 1, 0);
    }

    private static void halfArrowVisible(ServerPlayer p, Vec3 c, Vec3 direction, DustParticleOptions color) {
        Vec3 d = horizontal(direction), side = new Vec3(-d.z, 0, d.x);
        Vec3 tip = c.add(d.scale(.62));
        lineVisible(p, c.subtract(d.scale(.42)), tip, color, 4);
        lineVisible(p, tip, tip.subtract(d.scale(.35)).add(side.scale(.28)), color, 2);
    }

    private static void palindromeClosure(ServerPlayer p, Vec3 c, Vec3 outbound, Vec3 returning) {
        texturedEffect(p, c, true, true, 1.4);
        texturedEffect(p, c, false, false, 1.15);
        halfArrowVisible(p, c.add(0, .08, 0), outbound, CYAN);
        halfArrowVisible(p, c.add(0, -.08, 0), returning, GOLD);
        squareRingVisible(p, c, 1.05, GOLD, 4);
        squareRingVisible(p, c, .48, WHITE, 3);
        Vec3 side = new Vec3(-horizontal(outbound).z, 0, horizontal(outbound).x);
        lineVisible(p, c.subtract(side.scale(.95)).add(0, -.45, 0), c.subtract(side.scale(1.55)).add(0, .45, 0), GOLD, 4);
        lineVisible(p, c.add(side.scale(.95)).add(0, .45, 0), c.add(side.scale(1.55)).add(0, -.45, 0), WHITE, 4);
    }

    /** 回文斩在12tick内持续闭合、翻转并向两侧斩开，避免只闪现一帧。 */
    private static void tickPalindromeEffects(ServerPlayer p, State s, long now) {
        Iterator<PalindromeEffect> it = s.palindromeEffects.iterator();
        while (it.hasNext()) {
            PalindromeEffect fx = it.next();
            if (fx.until <= now) { it.remove(); continue; }
            double progress = Mth.clamp((now - fx.started) / 11.0, 0, 1);
            Vec3 out = horizontal(fx.outbound), back = horizontal(fx.returning);
            Vec3 side = new Vec3(-out.z, 0, out.x);
            double offset = (1.0 - progress) * .85;
            halfArrowVisible(p, fx.center.subtract(side.scale(offset)).add(0, .12, 0), out, CYAN);
            halfArrowVisible(p, fx.center.add(side.scale(offset)).add(0, -.12, 0), back, GOLD);
            double outer = .62 + progress * .72;
            squareRingVisible(p, fx.center, outer, progress < .55 ? CYAN : GOLD, 3);
            squareRingVisible(p, fx.center, Math.max(.28, outer * .46), WHITE, 2);
            if (now == fx.started + 4 || now == fx.started + 8) {
                lineVisible(p, fx.center.subtract(side.scale(.7)).add(0, -.5, 0),
                        fx.center.subtract(side.scale(2.15)).add(0, .55, 0), GOLD, 7);
                lineVisible(p, fx.center.add(side.scale(.7)).add(0, .55, 0),
                        fx.center.add(side.scale(2.15)).add(0, -.5, 0), WHITE, 7);
                ringVisible(p, fx.center, 1.65 + progress, progress < .7 ? CYAN : GOLD, 18);
            }
        }
    }

    private static void halfReturnGlyph(ServerPlayer p, Vec3 c, Vec3 direction, DustParticleOptions color) {
        Vec3 d = horizontal(direction), side = new Vec3(-d.z, 0, d.x);
        Vec3 back = c.subtract(d.scale(.62));
        lineVisible(p, back.subtract(side.scale(.58)), back.add(side.scale(.58)), color, 4);
        lineVisible(p, back.subtract(side.scale(.58)), c.subtract(side.scale(.58)), color, 3);
        lineVisible(p, c.subtract(side.scale(.58)), c.add(d.scale(.18)), WHITE, 3);
    }

    private static void tickEchoes(ServerPlayer p, State s, long now) {
        Iterator<RouteEcho> it = s.echoes.iterator();
        while (it.hasNext()) {
            RouteEcho echo = it.next(); if (echo.until <= now) { it.remove(); continue; }
            if (now % 3 != 0) continue;
            lineVisible(p, echo.start.add(0, .14, 0), echo.end.add(0, .14, 0), DARK_GOLD, 7);
            squareRingVisible(p, echo.start.lerp(echo.end, .5).add(0, .12, 0), .45, FAINT_CYAN, 2);
        }
    }

    private static void drawFinisherReady(ServerPlayer p, long now) {
        double a = now * .22; Vec3 c = p.position().add(0, .85, 0);
        for (int i = 0; i < 2; i++) {
            double q = a + i * Math.PI; Vec3 blade = c.add(Math.cos(q) * .72, i * .22, Math.sin(q) * .72);
            Vec3 tangent = new Vec3(-Math.sin(q), 0, Math.cos(q));
            lineVisible(p, blade.subtract(tangent.scale(.28)), blade.add(tangent.scale(.28)), i == 0 ? GOLD : WHITE, 3);
        }
    }

    private static void hundredReturnFinisher(ServerPlayer p, Vec3 c) {
        texturedEffect(p, c.add(0, .8, 0), true, true, 2.6);
        squareRingVisible(p, c, 4.0, GOLD, 8);
        squareRingVisible(p, c.add(0, .42, 0), 2.8, WHITE, 7);
        squareRingVisible(p, c.add(0, .82, 0), 1.65, GOLD, 6);
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3; Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            lineVisible(p, c.add(d.scale(.5)).add(0, .15, 0), c.add(d.scale(4.2)).add(0, .6, 0), i == 1 ? WHITE : GOLD, 10);
        }
    }

    private static void markerPlantBurst(ServerPlayer p, Marker marker) {
        texturedEffect(p, marker.pos.add(0, .45, 0), true, false, .65);
        drawMarkerBladeVisible(p, marker.pos, Math.floorMod(marker.id - 1, 3), WHITE, 1.15);
        verticalLineVisible(p, marker.pos.add(0, .08, 0), 1.35, CYAN, 6);
    }

    private static void markerShatter(ServerPlayer p, Vec3 c) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2 / 8; Vec3 end = c.add(Math.cos(a) * .75, .15 + (i % 3) * .18, Math.sin(a) * .75);
            lineVisible(p, c.add(0, .4, 0), end, i % 2 == 0 ? CYAN : WHITE, 2);
        }
        p.level().playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, .45F, 1.7F);
    }

    private static void segmentedRippleVisible(ServerPlayer owner, Vec3 c, double r, DustParticleOptions d) {
        for (int i = 0; i < 20; i++) if (i % 5 < 3) {
            double a = Math.PI * 2 * i / 20; sendVisible(owner, new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r), d, 1, 0);
        }
    }

    private static void ringVisible(ServerPlayer owner, Vec3 c, double r, DustParticleOptions d, int n) { for (int i = 0; i < n; i++) { double a = Math.PI * 2 * i / n; sendVisible(owner, new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r), d, 1, 0); } }
    private static void squareRingVisible(ServerPlayer owner, Vec3 c, double r, DustParticleOptions d, int pointsPerSide) { Vec3[] q = {c.add(-r,0,-r),c.add(r,0,-r),c.add(r,0,r),c.add(-r,0,r)}; for (int i=0;i<4;i++) lineVisible(owner,q[i],q[(i+1)%4],d,pointsPerSide); }
    private static void diamondVisible(ServerPlayer owner, Vec3 c, double r, DustParticleOptions d, int pointsPerSide) { Vec3[] q = {c.add(0,0,-r),c.add(r,0,0),c.add(0,0,r),c.add(-r,0,0)}; for(int i=0;i<4;i++) lineVisible(owner,q[i],q[(i+1)%4],d,pointsPerSide); }
    private static void verticalLineVisible(ServerPlayer owner, Vec3 base, double height, DustParticleOptions d, int points) { lineVisible(owner, base, base.add(0,height,0), d, points); }
    private static void lineVisible(ServerPlayer owner, Vec3 a, Vec3 b, DustParticleOptions d, int n) { for (int i = 0; i <= n; i++) sendVisible(owner, a.lerp(b, i / (double)Math.max(1,n)), d, 1, 0); }
    private static void segmentedRingPrivate(ServerPlayer p, Vec3 c, double r, DustParticleOptions d, int points, int drawPerGroup) { for (int i=0;i<points;i++) if(i%5<drawPerGroup){double a=Math.PI*2*i/points;privateParticle(p,new Vec3(c.x+Math.cos(a)*r,c.y,c.z+Math.sin(a)*r),d);} }
    private static void squareRingPrivate(ServerPlayer p, Vec3 c, double r, DustParticleOptions d, int pointsPerSide) { Vec3[] q={c.add(-r,0,-r),c.add(r,0,-r),c.add(r,0,r),c.add(-r,0,r)};for(int i=0;i<4;i++)privateLine(p,q[i],q[(i+1)%4],d,pointsPerSide); }
    private static void privateLine(ServerPlayer p, Vec3 a, Vec3 b, DustParticleOptions d, int n) { for(int i=0;i<=n;i++)privateParticle(p,a.lerp(b,i/(double)Math.max(1,n)),d); }
    private static void texturedEffect(ServerPlayer owner, Vec3 pos, boolean glyph, boolean gold, double size) {
        var type = (glyph ? com.myriad.registry.ModParticles.ZHECHUAN_RETURN
                : com.myriad.registry.ModParticles.ZHECHUAN_BLADE).get();
        for (ServerPlayer viewer : owner.level().players()) {
            if (viewer.distanceToSqr(pos) > 1600) continue;
            if (viewer != owner && !com.myriad.game.VisionSystem.canSeePosition(viewer, pos)) continue;
            owner.level().sendParticles(viewer, type, false, false, pos.x, pos.y, pos.z,
                    0, glyph ? 0 : .45, gold ? 1 : 0, size, 1);
        }
    }
    private static void privateParticle(ServerPlayer p, Vec3 pos, DustParticleOptions particle) { p.level().sendParticles(p,particle,false,false,pos.x,pos.y,pos.z,1,0,0,0,0); }
    private static void sendVisible(ServerPlayer owner, Vec3 pos, DustParticleOptions particle, int count, double spread) { for (ServerPlayer viewer : owner.level().players()) if (viewer == owner || com.myriad.game.VisionSystem.canSeePosition(viewer, pos)) owner.level().sendParticles(viewer, particle, false, false, pos.x, pos.y, pos.z, count, spread, spread, spread, 0); }
    private static void message(ServerPlayer p, String key) { p.sendSystemMessage(Component.translatable(key)); }
    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> {
            State s = new State();
            s.palindromeBonus = DISCONNECTED_BONUS.getOrDefault(id, 0);
            DISCONNECTED_BONUS.remove(id);
            return s;
        });
    }
    public static void clearFor(ServerPlayer p) { clearFor(p.getUUID()); PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0)); }
    public static void clearFor(UUID id) { STATES.remove(id); DISCONNECTED_BONUS.remove(id); }
    public static void clearAll() { STATES.clear(); DISCONNECTED_BONUS.clear(); }
    public static void disconnect(ServerPlayer p) {
        State state = STATES.remove(p.getUUID());
        if (state != null) DISCONNECTED_BONUS.put(p.getUUID(), state.palindromeBonus);
    }

    /** 死亡只清空本轮技能临时状态；回文斩累计增伤在本局内保留。 */
    public static void onDeath(ServerPlayer p) {
        State previous = STATES.get(p.getUUID());
        int matchBonus = previous == null ? 0 : previous.palindromeBonus;
        State fresh = new State(); fresh.palindromeBonus = matchBonus;
        STATES.put(p.getUUID(), fresh);
        PacketDistributor.sendToPlayer(p, new DashPayload(0, 0, 0, 0));
    }

    private static final class State { int nextRouteId, charges = 3, rechargeTotal = 180, nextId = 1, returnStacks, palindromeBonus; long placementUntil, rUntil; boolean qNoCd, qNoMana, finisherUsed; final ArrayDeque<Long> ready = new ArrayDeque<>(); final List<Marker> markers = new ArrayList<>(); final List<MarkerFlight> flights = new ArrayList<>(); final List<RouteEcho> echoes = new ArrayList<>(); final List<PalindromeEffect> palindromeEffects = new ArrayList<>(); final Set<Integer> rVisited = new HashSet<>(); final Map<UUID, GoMark> goMarks = new HashMap<>(); final Map<UUID, Long> passiveCd = new HashMap<>(); Marker retreat; Move move; Route route; }
    private static final class Marker { final int id; final Vec3 pos; long until; final boolean retreat; Marker(int id, Vec3 pos, long until, boolean retreat) { this.id = id; this.pos = pos; this.until = until; this.retreat = retreat; } }
    private record MarkerFlight(Vec3 from, Vec3 to, long started, long ends, int level, float extraAd) { }
    private record GoMark(Vec3 direction, long until, int routeId) { }
    private record Route(Vec3 start, Vec3 end, Vec3 outbound, long until, int id, List<Vec3> points) { }
    private record RouteEcho(Vec3 start, Vec3 end, long until) { }
    private record PalindromeEffect(Vec3 center, Vec3 outbound, Vec3 returning, long started, long until) { }
    private static final class Move { final MoveKind kind; final Vec3 start, target; Vec3 direction, previous; final List<Vec3> travelled = new ArrayList<>(); int routeId, returnIndex; final int totalTicks, level, markerId; final Route route; final Set<UUID> hit = new HashSet<>(); int ticks; double stepLength; Move(MoveKind kind, Vec3 start, Vec3 target, int ticks, int level, int markerId, Route route) { this.kind = kind; this.start = start; this.target = target; this.direction = horizontal(target.subtract(start)); this.ticks = ticks; this.totalTicks = ticks; this.level = level; this.markerId = markerId; this.route = route; } }
}
