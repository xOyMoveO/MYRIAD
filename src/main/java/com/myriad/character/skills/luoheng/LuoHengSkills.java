package com.myriad.character.skills.luoheng;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.MapSystem;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 天算·洛衡：服务器权威三节点、手动选边、换位和动态三垣杀阵。 */
public final class LuoHengSkills extends Skill {
    public enum Type { PASSIVE, Q, W, E, R }
    public static final int Q_PLACEMENT = 50;
    public static final int W_SELECTION = 51;
    private static final String Q_CHARGE = "skill_charge_luochouyixing";
    private static final String Q_REMAINING = Q_CHARGE + "_remaining";
    private static final String Q_TOTAL = Q_CHARGE + "_total";
    private static final DustParticleOptions CYAN = new DustParticleOptions(0x55EAF2, 1.0F);
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xF1FDFF, .82F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(0xF6B84A, 1.05F);
    private static final DustParticleOptions RED = new DustParticleOptions(0xEA5A44, .95F);
    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Query or shorten only the charge currently recovering; never grant a charge directly. */
    public static int runeRecharge(ServerPlayer p, double fraction) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.qReady.isEmpty()) return 0;
        long now = p.level().getGameTime();
        int remaining = (int)Math.max(0, s.qReady.peekFirst() - now);
        if (fraction > 0) {
            com.myriad.character.SequentialChargeTimers.moveFirstTo(s.qReady,
                    now + Math.max(1, (int)Math.ceil(remaining * (1 - fraction))));
        }
        return remaining;
    }
    private final Type type;

    public LuoHengSkills(Type type) {
        super(switch (type) {
            case PASSIVE -> "luoheng_passive";
            case Q -> "luochouyixing";
            case W -> "gouchenxian";
            case E -> "yigonghuanshu";
            case R -> "sanyuantianluo";
        }, switch (type) {
            case PASSIVE -> 0; case Q -> 140; case W -> 160; case E -> 360; case R -> 2000;
        }, switch (type) {
            case PASSIVE -> 0.0F; case Q -> 64.8F; case W -> 100.8F; case E -> 162.0F; case R -> 360.0F;
        });
        this.type = type;
    }

    @Override public boolean isPassive() { return type == Type.PASSIVE; }
    @Override public boolean isUltimate() { return type == Type.R; }
    @Override public int getMaxCharges() { return type == Type.Q ? 3 : 1; }
    @Override public int getCooldownTicks(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case Q -> Math.max(110, 140 - (lv - 1) * 10);
            case W -> Math.max(130, 160 - (lv - 1) * 10);
            case E -> Math.max(240, 360 - (lv - 1) * 40);
            case R -> Math.max(1400, 2000 - (lv - 1) * 300);
            default -> 0;
        };
    }
    @Override public String levelEffects(int level) {
        int lv = Math.max(1, level);
        return switch (type) {
            case PASSIVE -> "三枚天算筹构成三条边；5秒内以三条不同边命中会触发闭环验算";
            case Q -> "3层充能；落点造成 " + (12 + (lv - 1) * 6) + "+25%法强魔法伤害";
            case W -> "冻结所选边并贯穿，造成 " + (25 + (lv - 1) * 10) + "+55%法强魔法伤害";
            case E -> "与算筹换位；两端造成 " + (18 + (lv - 1) * 8) + "+30%法强魔法伤害";
            case R -> "动态三角边造成 " + (30 + (lv - 1) * 15) + "+20%法强，收式造成 "
                    + (70 + (lv - 1) * 30) + "+65%法强";
        };
    }
    @Override public void cast(ServerPlayer player, PlayerData data) { }

    public static void handleCast(ServerPlayer p, PlayerData data, int slot, Skill raw,
            boolean noCd, boolean noMana) {
        if (!(raw instanceof LuoHengSkills skill)) return;
        State s = state(p); long now = p.level().getGameTime();
        if ((s.rWindupUntil > now || s.rClosingUntil > now) && (skill.type == Type.Q || skill.type == Type.E)) {
            message(p, "message.myriad.luoheng_array_locked"); return;
        }
        switch (skill.type) {
            case Q -> beginQ(p, data, s, skill, noCd, noMana, now);
            case W -> castW(p, data, s, skill, noCd, noMana, now);
            case E -> castE(p, data, s, skill, noCd, noMana, now);
            case R -> castR(p, data, s, skill, noCd, noMana, now);
            default -> { }
        }
    }

    private static void beginQ(ServerPlayer p, PlayerData data, State s, LuoHengSkills skill,
            boolean noCd, boolean noMana, long now) {
        recoverQ(p, data, s, skill, now);
        if (data.getPlacementType() != 0) return;
        if (!noCd && s.qCharges <= 0) { message(p, "message.myriad.luoheng_no_charge"); return; }
        if (!noMana && data.getMana() < skill.getManaCost()) { message(p, "message.myriad.not_enough_mana"); return; }
        s.pendingNode = firstMissing(s);
        if (s.pendingNode < 0) {
            s.pendingNode = aimedNode(p, s, 14, false);
            if (s.pendingNode < 0) s.pendingNode = shortestNode(s);
        }
        s.qNoCd = noCd; s.qNoMana = noMana; s.qPlacementUntil = now + 80;
        data.setPlacementType(Q_PLACEMENT);
        data.setBuff("luochouyixing_recast", 80);
        PlayerDataProvider.syncToClient(p);
    }

    public static void confirmPlacement(ServerPlayer p, PlayerData data) {
        State s = STATES.get(p.getUUID());
        if (s == null || data.getPlacementType() != Q_PLACEMENT || s.pendingNode < 0) return;
        long now = p.level().getGameTime(); Vec3 target = groundTarget(p, 12);
        if (!validNodePoint(p, s, s.pendingNode, target, true)) {
            message(p, "message.myriad.luoheng_invalid_node"); return;
        }
        LuoHengSkills q = new LuoHengSkills(Type.Q);
        recoverQ(p, data, s, q, now);
        if ((!s.qNoCd && s.qCharges <= 0) || (!s.qNoMana && data.getMana() < q.getManaCost())) {
            cancelPlacement(p, data); return;
        }
        if (!s.qNoMana) data.spendMana(q.getManaCost());
        com.myriad.character.SkillPresentation.cast(p, q);
        if (!s.qNoCd) {
            s.qCharges--;
            int total = reducedCooldown(p, q.getCooldownTicks(Math.max(1, data.skillLevel(q.getId()))));
            if (s.rUntil > now) total = Math.max(1, (int)Math.ceil(total * .6));
            s.qRechargeTotal = total;
            com.myriad.character.SequentialChargeTimers.enqueue(s.qReady, now, total);
        }
        int index = s.pendingNode; Node node = s.nodes[index];
        Vec3 from = node.pos == null ? p.getEyePosition().add(0, -.3, 0) : node.pos;
        if (s.rUntil > now) beginRebuild(s, now);
        node.pos = target; node.expiresAt = now + 400; node.unstableUntil = now + 6;
        float landingDamage=48+(Math.max(1,data.skillLevel(q.getId()))-1)*24+sp(p)*1.0F;
        s.nodeFlights.add(new NodeFlight(index,from,target,now,now+6,landingDamage));
        finishPlacement(data, s); syncQ(data, s, now); PlayerDataProvider.syncToClient(p);
    }

    public static void cancelPlacement(ServerPlayer p, PlayerData data) {
        State s = STATES.get(p.getUUID());
        if (s == null || data.getPlacementType() != Q_PLACEMENT) return;
        finishPlacement(data, s); PlayerDataProvider.syncToClient(p);
    }
    private static void finishPlacement(PlayerData data, State s) {
        data.setPlacementType(0); data.setBuff("luochouyixing_recast", 0);
        s.pendingNode = -1; s.qPlacementUntil = 0;
    }

    private static void castW(ServerPlayer p, PlayerData data, State s, LuoHengSkills skill,
            boolean noCd, boolean noMana, long now) {
        if (s.wStage == 0) {
            if (!noCd && data.getCooldown(skill.getId()) > 0) { cooldownMessage(p, data, skill); return; }
            if (deployedStable(s, now) < 2) { message(p, "message.myriad.luoheng_need_two_nodes"); return; }
            data.setPlacementType(W_SELECTION);
            int aimed=aimedNode(p,s,18,true);
            if (aimed >= 0) {
                s.wStart = aimed; s.wStage = 2; s.wUntil = now + 80;
                data.setBuff(skill.getId() + "_recast", 80);
                data.setBuff("luoheng_w_start", s.wStart + 1);
                message(p, "message.myriad.luoheng_choose_end");
            } else {
                s.wStage = 1; s.wUntil = now + 80; data.setBuff(skill.getId() + "_recast", 80);
                message(p, "message.myriad.luoheng_choose_start");
            }
            PlayerDataProvider.syncToClient(p); return;
        }
        int node = aimedNode(p, s, 18, true);
        if (node < 0) { message(p, "message.myriad.luoheng_aim_node"); return; }
        if (s.wStage == 1) {
            s.wStart = node; s.wStage = 2; s.wUntil = now + 80;
            data.setBuff(skill.getId() + "_recast", 80);
            data.setBuff("luoheng_w_start", node + 1);
            message(p, "message.myriad.luoheng_choose_end"); PlayerDataProvider.syncToClient(p); return;
        }
        if (node == s.wStart) { message(p, "message.myriad.luoheng_need_other_node"); return; }
        if (!noMana && data.getMana() < skill.getManaCost()) { message(p, "message.myriad.not_enough_mana"); return; }
        Node a = s.nodes[s.wStart], b = s.nodes[node];
        if (!stable(a, now) || !stable(b, now)) { failW(data, s, noCd); return; }
        com.myriad.character.SkillPresentation.cast(p, skill); if (!noMana) data.spendMana(skill.getManaCost());
        if (!noCd) data.setCooldown(skill.getId(), reducedCooldown(p,
                skill.getCooldownTicks(Math.max(1, data.skillLevel(skill.getId())))));
        Vec3 clipped = clipEnd(p.level(), p, a.pos, b.pos);
        s.lines.add(new FrozenLine(a.pos, clipped, edgeId(s.wStart, node),
                Math.max(1, data.skillLevel(skill.getId())), sp(p), now + 5));
        resetW(data, s); p.level().playSound(null, p.blockPosition(), com.myriad.registry.ModSounds.foley("stone"),
                SoundSource.PLAYERS, .8F, 1.55F); PlayerDataProvider.syncToClient(p);
    }

    private static void castE(ServerPlayer p, PlayerData data, State s, LuoHengSkills skill,
            boolean noCd, boolean noMana, long now) {
        if (s.eUntil > now) return;
        if (!noCd && data.getCooldown(skill.getId()) > 0) { cooldownMessage(p, data, skill); return; }
        int node = aimedNode(p, s, 16, false);
        if (node < 0) { message(p, "message.myriad.luoheng_aim_node"); return; }
        if (!noMana && data.getMana() < skill.getManaCost()) { message(p, "message.myriad.not_enough_mana"); return; }
        com.myriad.character.SkillPresentation.cast(p, skill); if (!noMana) data.spendMana(skill.getManaCost());
        s.eNode = node; s.eUntil = now + 5; s.eOrigin = p.position(); s.eNoCd = noCd; s.eNoMana = noMana;
        s.eLevel = Math.max(1, data.skillLevel(skill.getId()));
        data.setBuff("yigonghuanshu_channel", 5); PlayerDataProvider.syncToClient(p);
    }

    private static void castR(ServerPlayer p, PlayerData data, State s, LuoHengSkills skill,
            boolean noCd, boolean noMana, long now) {
        if (!noCd && data.getCooldown(skill.getId()) > 0) { cooldownMessage(p, data, skill); return; }
        if (!validTriangle(s, now)) { message(p, "message.myriad.luoheng_invalid_triangle"); return; }
        if (!noMana && data.getMana() < skill.getManaCost()) { message(p, "message.myriad.not_enough_mana"); return; }
        com.myriad.character.SkillPresentation.cast(p, skill); if (!noMana) data.spendMana(skill.getManaCost());
        s.rWindupUntil = now + 16; s.rLevel = Math.max(1, data.skillLevel(skill.getId()));
        s.rNoCd = noCd; s.rNoMana = noMana; s.rSnapshot = positions(s);
        data.setBuff("sanyuantianluo_channel", 16); PlayerDataProvider.syncToClient(p);
    }

    public static void tick(ServerPlayer p, PlayerData data) {
        if (CharacterType.byId(data.getCharacterId()) != CharacterType.LUOHENG) return;
        State s = state(p); ServerLevel level = p.level(); long now = level.getGameTime();
        recoverQ(p, data, s, new LuoHengSkills(Type.Q), now);
        expireAndRecoverNodes(p, s, now);
        trackStableAim(p, s, now);
        if (data.getPlacementType() == Q_PLACEMENT) tickPlacement(p, data, s, now);
        tickNodeFlights(p, s, now); tickW(p, data, s, now); tickE(p, data, s, now); tickR(p, data, s, now);
        renderNodes(p, s, now); syncState(data, s, now);
        if ((now & 3) == 0) PlayerDataProvider.syncToClient(p);
    }

    private static void tickPlacement(ServerPlayer p, PlayerData data, State s, long now) {
        if (now > s.qPlacementUntil) { cancelPlacement(p, data); return; }
        Vec3 target = groundTarget(p, 12); boolean valid = validNodePoint(p, s, s.pendingNode, target, true);
        ringPrivate(p.level(), p, target.add(0, .05, 0), 1.1, valid ? WHITE : RED, 24);
        drawLinePrivate(p.level(), p, p.getEyePosition().add(0, -.4, 0), target, valid ? CYAN : RED, 12);
    }

    private static void tickNodeFlights(ServerPlayer owner, State s, long now) {
        s.nodeFlights.removeIf(f -> {
            if (now >= f.until) {
                damageCircle(owner,f.to,1.8,f.damage,0,new HashSet<>());
                owner.level().playSound(null,BlockPos.containing(f.to),SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.PLAYERS,.8F,1.35F+f.node*.12F);
                return true;
            }
            double t = Mth.clamp((now - f.started) / (double)Math.max(1, f.until - f.started), 0, 1);
            Vec3 pos = f.from.lerp(f.to, t).add(0, Math.sin(Math.PI * t) * 1.6, 0);
            sendVisible(owner, pos.add(0,.3,0), s.rUntil > now ? GOLD : CYAN, 2, .03);
            return false;
        });
    }

    private static void tickW(ServerPlayer p, PlayerData data, State s, long now) {
        if (s.wStage > 0 && now > s.wUntil) { failW(data, s, false); PlayerDataProvider.syncToClient(p); }
        s.lines.removeIf(line -> {
            if (now < line.fireAt) { drawLineVisible(p, line.a, line.b, WHITE, 22); return false; }
            double length = line.a.distanceTo(line.b); double traveled = (now - line.fireAt + 1) * 2.5;
            Vec3 end = line.a.lerp(line.b, Math.min(1, traveled / Math.max(.01, length)));
            Vec3 start = line.a.lerp(line.b, Math.max(0, (traveled - 2.5) / Math.max(.01, length)));
            drawLineVisible(p, start, end, CYAN, 8);
            for (LivingEntity target : targets(p, line.a, end, 1.05)) if (line.hit.add(target.getUUID())) {
                DamageCalc.dealSkillDamage(p, target, 100 + (line.level - 1) * 40 + line.sp * 2.20F, DamageCalc.Kind.MAGIC, 1);
                slow(target, 25, 0); markEdge(p, s, target, line.edge, now);
            }
            return traveled >= length + 2.5;
        });
    }

    private static void tickE(ServerPlayer p, PlayerData data, State s, long now) {
        if (s.eUntil <= 0) return;
        Node node = s.eNode < 0 ? null : s.nodes[s.eNode];
        if (hardControlled(p) || node == null || node.pos == null) { failE(p, data, s); return; }
        ringVisible(p, p.position().add(0, .08, 0), 1.0, WHITE, 18);
        ringVisible(p, node.pos.add(0, .08, 0), 1.0, CYAN, 18);
        p.setDeltaMovement(p.getDeltaMovement().multiply(.65, 1, .65));
        if (now < s.eUntil) return;
        Vec3 target = node.pos, origin = s.eOrigin;
        if (horizontalDistance(p.position(), target) > 16.5 || !safeTeleport(p.level(), p, target)
                || !validNodePoint(p, s, s.eNode, origin, false)) { failE(p, data, s); return; }
        beginRebuild(s, now); node.pos = origin; node.expiresAt = now + 280; node.unstableUntil = now + 10;
        p.teleportTo(target.x, target.y, target.z);
        Set<UUID> hit = new HashSet<>(); float damage = 72 + (s.eLevel - 1) * 32 + sp(p) * 1.20F;
        damageCircle(p, origin, 2, damage, 20, hit); damageCircle(p, target, 2, damage, 20, hit);
        if (!s.eNoCd) data.setCooldown("yigonghuanshu", reducedCooldown(p,
                new LuoHengSkills(Type.E).getCooldownTicks(s.eLevel)));
        s.eUntil = 0; s.eNode = -1; data.setBuff("yigonghuanshu_channel", 0);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 1.45F);
        PlayerDataProvider.syncToClient(p);
    }

    private static void tickR(ServerPlayer p, PlayerData data, State s, long now) {
        if (s.rWindupUntil > 0) {
            double t = 1 - (s.rWindupUntil - now) / 16.0;
            DustParticleOptions c = t > .55 ? GOLD : WHITE;
            drawTriangle(p, s.rSnapshot, c, 36);
            if (hardControlled(p) || !validTriangle(s, now)) { failR(p, data, s); return; }
            if (now >= s.rWindupUntil) {
                s.rWindupUntil = 0; s.rUntil = now + 120; s.rHitReady.clear(); s.rSnapshot = positions(s);
                data.setBuff("sanyuantianluo", 120);
                if (!s.rNoCd) data.setCooldown("sanyuantianluo", reducedCooldown(p,
                        new LuoHengSkills(Type.R).getCooldownTicks(s.rLevel)));
                
                p.level().playSound(null, p.blockPosition(), com.myriad.registry.ModSounds.foley("stone"),
                        SoundSource.PLAYERS, 1, .9F);
            }
        }
        if (s.rUntil > now) {
            List<Vec3> current = positions(s);
            if (current.size() != 3) { s.rUntil=0; data.setBuff("sanyuantianluo",0); return; }
            drawTriangle(p, current, GOLD, 42);
            for (int edge = 0; edge < 3; edge++) {
                int[] pair = edgePair(edge); Vec3 a = current.get(pair[0]), b = current.get(pair[1]);
                hitREdge(p, s, edge, a, b, now, 30);
                if (now < s.rebuildUntil && s.rSnapshot.size() == 3) {
                    Vec3 oa = s.rSnapshot.get(pair[0]), ob = s.rSnapshot.get(pair[1]);
                    double t = 1 - (s.rebuildUntil - now) / 2.0;
                    hitREdge(p, s, edge, oa.lerp(a, t), ob.lerp(b, t), now, 15);
                }
            }
            if (now >= s.rebuildUntil) s.rSnapshot = current;
            if (now + 1 >= s.rUntil) {
                s.rUntil = 0; s.rClosingUntil = now + 8; s.closeTriangle = current;
                s.closeTargets.clear();
                AABB box = triangleBox(current).inflate(1);
                for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
                        e -> e != p && com.myriad.combat.SkillTargets.alive(e) && !ally(p, e) && pointInTriangle(e.position(), current))) {
                    s.closeTargets.add(e.getUUID());
                }
            }
        }
        if (s.rClosingUntil > now && s.closeTriangle.size() == 3) {
            Vec3 center = centroid(s.closeTriangle); double t = 1 - (s.rClosingUntil - now) / 8.0;
            List<Vec3> folded = s.closeTriangle.stream().map(v -> v.lerp(center, t)).toList(); drawTriangle(p, folded, GOLD, 36);
        } else if (s.rClosingUntil > 0 && now >= s.rClosingUntil) {
            float base = 280 + (s.rLevel - 1) * 120 + sp(p) * 2.60F;
            for (UUID id : s.closeTargets) if (p.level().getEntity(id) instanceof LivingEntity target && com.myriad.combat.SkillTargets.alive(target)) {
                int marks = activeMarkCount(s, id, now); DamageCalc.dealSkillDamage(p, target, base * (1 + marks * .10F), DamageCalc.Kind.MAGIC, 3);
                if (marks == 3) triggerLoop(p, s, target, now);
            }
            Vec3 center = centroid(s.closeTriangle); sendVisible(p, center.add(0,.4,0),
                    ParticleTypes.END_ROD,45,1.2);
            for (int i = 0; i < 3; i++) if (s.nodes[i].pos != null) s.nodes[i].expiresAt = now + 80 + i * 8L;
            s.rClosingUntil = 0; s.closeTargets.clear(); s.closeTriangle = List.of(); data.setBuff("sanyuantianluo", 0);
        }
    }

    private static void hitREdge(ServerPlayer p, State s, int edge, Vec3 a, Vec3 b, long now, int interval) {
        for (LivingEntity target : targets(p, a, b, 1.0)) {
            String key = target.getUUID() + ":" + edge; if (now < s.rHitReady.getOrDefault(key, 0L)) continue;
            s.rHitReady.put(key, now + interval);
            DamageCalc.dealSkillDamage(p, target, 120 + (s.rLevel - 1) * 60 + sp(p) * .80F, DamageCalc.Kind.MAGIC, 3);
            slow(target, 15, 0); markEdge(p, s, target, edge, now);
        }
    }

    private static void markEdge(ServerPlayer p, State s, LivingEntity target, int edge, long now) {
        if (!(target instanceof ServerPlayer)) return;
        EdgeMarks marks = s.marks.computeIfAbsent(target.getUUID(), ignored -> new EdgeMarks());
        marks.until[edge] = now + 100;
        if (activeMarkCount(s, target.getUUID(), now) == 3) triggerLoop(p, s, target, now);
    }
    private static void triggerLoop(ServerPlayer p, State s, LivingEntity target, long now) {
        EdgeMarks marks = s.marks.computeIfAbsent(target.getUUID(), ignored -> new EdgeMarks());
        if (now < marks.loopReady) return;
        DamageCalc.dealSkillDamage(p, target, 128 + sp(p) * .88F, DamageCalc.Kind.MAGIC, 4); slow(target, 20, 1);
        marks.until[0] = marks.until[1] = marks.until[2] = 0; marks.loopReady = now + 160;
        sendVisible(p,target.position().add(0,1,0),GOLD,30,.55);
    }

    private static void renderNodes(ServerPlayer owner, State s, long now) {
        if ((now & 1) != 0) return;
        for (int i = 0; i < 3; i++) {
            Node node = s.nodes[i];
            if (node.pos == null) {
                double a = now * .12 + i * Math.PI * 2 / 3; Vec3 pos = owner.position().add(Math.cos(a) * .8, .35 + i * .12, Math.sin(a) * .8);
                drawNodePrivate(owner.level(), owner, pos, i, i == 0 ? CYAN : WHITE);
            } else drawNodeVisible(owner, node.pos.add(0, .2, 0), i, node.unstableUntil > now ? RED : CYAN);
        }
    }

    private static void drawNodePrivate(ServerLevel level, ServerPlayer viewer, Vec3 c, int id, DustParticleOptions color) {
        int points = id == 0 ? 8 : id == 1 ? 12 : 10;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points; double r = id == 0 ? .34 * (1 + .35 * Math.cos(2 * a)) : .38;
            double x = id == 2 && i % 2 == 0 ? Math.cos(a) * .46 : Math.cos(a) * r;
            double z = id == 2 && i % 2 == 0 ? Math.sin(a) * .46 : Math.sin(a) * r;
            level.sendParticles(viewer,color,false,false,c.x+x,c.y,c.z+z,1,0,0,0,0);
        }
    }
    private static void drawNodeVisible(ServerPlayer owner, Vec3 c, int id, DustParticleOptions color) {
        for(ServerPlayer viewer:owner.level().players())if(viewer==owner||com.myriad.game.VisionSystem.canSeePosition(viewer,c))drawNodePrivate(owner.level(),viewer,c,id,color);
    }

    private static void syncState(PlayerData data, State s, long now) {
        int deployed = 0; for (Node n : s.nodes) if (n.pos != null) deployed++;
        data.setBuff("luoheng_nodes", deployed);
        for (int i = 0; i < 3; i++) data.setBuff("luoheng_node_" + (i + 1),
                s.nodes[i].pos == null ? 0 : (int)Math.max(1, s.nodes[i].expiresAt - now));
        data.setBuff("luoheng_w_start", s.wStage == 2 ? s.wStart + 1 : 0);
        data.setBuff("sanyuantianluo", s.rUntil > now ? (int)(s.rUntil - now) : 0);
        List<Vec3> positions=positions(s);
        if (positions.size() == 3) {
            data.setBuff("luoheng_triangle_area", (int)Math.round(triangleArea(positions)));
            for(int edge=0;edge<3;edge++){int[] pair=edgePair(edge);data.setBuff("luoheng_edge_"+(edge+1),(int)Math.round(horizontalDistance(positions.get(pair[0]),positions.get(pair[1]))*10));}
        } else {
            data.setBuff("luoheng_triangle_area",0);
            for(int edge=1;edge<=3;edge++)data.setBuff("luoheng_edge_"+edge,0);
        }
        data.setBuff("luoheng_rebuild",s.rebuildUntil>now?(int)(s.rebuildUntil-now):0);
    }

    private static void recoverQ(ServerPlayer p, PlayerData data, State s, LuoHengSkills q, long now) {
        while (!s.qReady.isEmpty() && now >= s.qReady.peekFirst()) { s.qReady.removeFirst(); s.qCharges = Math.min(3, s.qCharges + 1); }
        syncQ(data, s, now);
    }
    private static void syncQ(PlayerData data, State s, long now) {
        data.setBuff(Q_CHARGE, s.qCharges);
        data.setBuff(Q_REMAINING, s.qReady.isEmpty() ? 0 : (int)Math.max(0, s.qReady.peekFirst() - now));
        data.setBuff(Q_TOTAL, s.qRechargeTotal);
    }

    private static void expireAndRecoverNodes(ServerPlayer p, State s, long now) {
        boolean arrayActive=s.rWindupUntil>now||s.rUntil>now||s.rClosingUntil>now;
        for (Node n : s.nodes) if (n.pos != null && ((!arrayActive&&now >= n.expiresAt)
                || horizontalDistance(p.position(), n.pos) > 24 || !MapSystem.contains(n.pos.x, n.pos.z)
                || !p.level().hasChunkAt(BlockPos.containing(n.pos)) || !safeGround(p.level(), n.pos))) n.clear();
    }
    private static boolean validNodePoint(ServerPlayer p, State s, int index, Vec3 pos, boolean edges) {
        if (pos == null || horizontalDistance(p.position(), pos) > 12.4 || !MapSystem.contains(pos.x, pos.z)
                || !safeGround(p.level(), pos)) return false;
        for (int i = 0; i < 3; i++) if (i != index && s.nodes[i].pos != null) {
            double d = horizontalDistance(pos, s.nodes[i].pos); if (d < 1.5 || (edges && d > 18)) return false;
        }
        return true;
    }
    private static boolean safeGround(ServerLevel level, Vec3 pos) {
        BlockPos feet = BlockPos.containing(pos); if (!level.hasChunkAt(feet)) return false;
        return level.getBlockState(feet.below()).isCollisionShapeFullBlock(level, feet.below())
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
    private static boolean safeTeleport(ServerLevel level, ServerPlayer p, Vec3 pos) {
        if (!safeGround(level, pos) || !MapSystem.contains(pos.x, pos.z)) return false;
        return level.noCollision(p, p.getBoundingBox().move(pos.subtract(p.position())));
    }
    private static Vec3 groundTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().normalize().scale(range));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos at = hit.getBlockPos().relative(hit.getDirection()); return Vec3.atBottomCenterOf(at);
        }
        Vec3 candidate = p.position().add(horizontal(p.getLookAngle()).scale(Math.min(8, range)));
        BlockPos.MutableBlockPos scan = BlockPos.containing(candidate).mutable();
        int baseY=scan.getY();
        for (int y = baseY + 4; y >= baseY - 8; y--) {
            scan.setY(y); if (p.level().getBlockState(scan).isCollisionShapeFullBlock(p.level(), scan)) return Vec3.atBottomCenterOf(scan.above());
        }
        return candidate;
    }
    private static int aimedNode(ServerPlayer p, State s, double range, boolean stableOnly) {
        Vec3 eye = p.getEyePosition(), dir = p.getLookAngle().normalize(); int best = -1; double bestPerp = 1.8;
        for (int i = 0; i < 3; i++) { Node n = s.nodes[i]; if (n.pos == null || (stableOnly && !stable(n, p.level().getGameTime()))) continue;
            Vec3 center = n.pos.add(0, .5, 0), rel = center.subtract(eye); double along = rel.dot(dir);
            if (along < 0 || along > range) continue; double perp = center.distanceTo(eye.add(dir.scale(along)));
            if (perp < bestPerp) { bestPerp = perp; best = i; }
        } return best;
    }
    private static void trackStableAim(ServerPlayer p,State s,long now){
        int aimed=aimedNode(p,s,18,true);
        if(aimed>=0&&aimed==s.stableAimNode)s.stableAimTicks=Math.min(20,s.stableAimTicks+1);
        else{s.stableAimNode=aimed;s.stableAimTicks=aimed<0?0:1;}
    }
    private static int firstMissing(State s) { for (int i = 0; i < 3; i++) if (s.nodes[i].pos == null) return i; return -1; }
    private static int shortestNode(State s) { int best = 0; for (int i = 1; i < 3; i++) if (s.nodes[i].expiresAt < s.nodes[best].expiresAt) best = i; return best; }
    private static int deployedStable(State s, long now) { int n = 0; for (Node node : s.nodes) if (stable(node, now)) n++; return n; }
    private static boolean stable(Node n, long now) { return n.pos != null && now >= n.unstableUntil; }
    private static boolean validTriangle(State s, long now) {
        for (Node n : s.nodes) if (!stable(n, now)) return false; List<Vec3> p = positions(s);
        if (triangleArea(p) < 4) return false;
        for (int[] pair : List.of(new int[]{0,1}, new int[]{1,2}, new int[]{2,0})) { double d=horizontalDistance(p.get(pair[0]),p.get(pair[1])); if(d<1.5||d>18)return false; }
        return true;
    }
    private static List<Vec3> positions(State s) { List<Vec3> result = new ArrayList<>(); for (Node n : s.nodes) if (n.pos != null) result.add(n.pos); return result; }
    private static double triangleArea(List<Vec3> p) { if (p.size()!=3)return 0; return Math.abs((p.get(1).x-p.get(0).x)*(p.get(2).z-p.get(0).z)-(p.get(2).x-p.get(0).x)*(p.get(1).z-p.get(0).z))*.5; }
    private static int edgeId(int a, int b) { int x=Math.min(a,b), y=Math.max(a,b); return x==0&&y==1?0:x==1&&y==2?1:2; }
    private static int[] edgePair(int edge) { return switch(edge){case 0->new int[]{0,1};case 1->new int[]{1,2};default->new int[]{2,0};}; }

    private static Vec3 clipEnd(ServerLevel level, ServerPlayer p, Vec3 a, Vec3 b) {
        BlockHitResult hit = level.clip(new ClipContext(a.add(0,.45,0), b.add(0,.45,0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType()==HitResult.Type.BLOCK ? hit.getLocation().add(0,-.45,0) : b;
    }
    private static Iterable<LivingEntity> targets(ServerPlayer p, Vec3 a, Vec3 b, double radius) {
        AABB box = new AABB(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y)+2,Math.max(a.z,b.z)).inflate(radius);
        return p.level().getEntitiesOfClass(LivingEntity.class, box, e->e!=p&&com.myriad.combat.SkillTargets.alive(e)&&!ally(p,e)&&distanceToSegment(e.getBoundingBox().getCenter(),a,b)<=radius+e.getBbWidth()*.5);
    }
    private static void damageCircle(ServerPlayer p, Vec3 c, double radius, float damage, int slowTicks, Set<UUID> hit) {
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c,c).inflate(radius,2,radius), e->e!=p&&com.myriad.combat.SkillTargets.alive(e)&&!ally(p,e))) {
            if (horizontalDistance(e.position(),c)<=radius+e.getBbWidth()*.5 && hit.add(e.getUUID())) { DamageCalc.dealSkillDamage(p,e,damage,DamageCalc.Kind.MAGIC, slowTicks == 0 ? 0 : 2); if(slowTicks>0)slow(e,slowTicks,0); }
        }
    }
    private static boolean ally(ServerPlayer caster, LivingEntity other) {
        return com.myriad.combat.SkillTargets.ally(caster, other);
    }
    private static void slow(LivingEntity e,int ticks,int amplifier){e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,ticks,amplifier,false,false,false));}
    private static boolean hardControlled(ServerPlayer p){return p.hasEffect(MobEffects.SLOWNESS)&&p.getEffect(MobEffects.SLOWNESS).getAmplifier()>=200;}
    private static float sp(ServerPlayer p){return (float)p.getAttributeValue(ModAttributes.SPELL_POWER);}
    private static int reducedCooldown(ServerPlayer p,int base){double cdr=Mth.clamp(p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION),0,60);return Math.max(1,(int)Math.ceil(base*(1-cdr/100.0)));}
    private static Vec3 horizontal(Vec3 v){Vec3 h=new Vec3(v.x,0,v.z);return h.lengthSqr()<1e-6?new Vec3(0,0,1):h.normalize();}
    private static double horizontalDistance(Vec3 a,Vec3 b){double x=a.x-b.x,z=a.z-b.z;return Math.sqrt(x*x+z*z);}
    private static double distanceToSegment(Vec3 point,Vec3 a,Vec3 b){Vec3 ab=b.subtract(a);double t=Mth.clamp(point.subtract(a).dot(ab)/Math.max(1e-6,ab.lengthSqr()),0,1);return point.distanceTo(a.add(ab.scale(t)));}
    private static void resetW(PlayerData data,State s){s.wStage=0;s.wStart=-1;s.wUntil=0;if(data.getPlacementType()==W_SELECTION)data.setPlacementType(0);data.setBuff("gouchenxian_recast",0);data.setBuff("luoheng_w_start",0);}
    private static void failW(PlayerData data,State s,boolean noCd){resetW(data,s);}
    private static void failE(ServerPlayer p,PlayerData data,State s){if(!s.eNoMana)data.setMana(data.getMana()+45);s.eUntil=0;s.eNode=-1;data.setBuff("yigonghuanshu_channel",0);PlayerDataProvider.syncToClient(p);}
    private static void failR(ServerPlayer p,PlayerData data,State s){if(!s.rNoMana)data.setMana(data.getMana()+50);s.rWindupUntil=0;data.setBuff("sanyuantianluo_channel",0);PlayerDataProvider.syncToClient(p);}
    private static void beginRebuild(State s,long now){if(s.rUntil>now){s.rSnapshot=positions(s);s.rebuildUntil=now+2;}}
    private static int activeMarkCount(State s,UUID id,long now){EdgeMarks m=s.marks.get(id);if(m==null)return 0;int c=0;for(long u:m.until)if(u>now)c++;return c;}
    private static AABB triangleBox(List<Vec3> p){AABB b=new AABB(p.get(0),p.get(0));return b.minmax(new AABB(p.get(1),p.get(1))).minmax(new AABB(p.get(2),p.get(2)));}
    private static boolean pointInTriangle(Vec3 q,List<Vec3> p){double a=triangleArea(p);double a1=triangleArea(List.of(q,p.get(1),p.get(2))),a2=triangleArea(List.of(p.get(0),q,p.get(2))),a3=triangleArea(List.of(p.get(0),p.get(1),q));return Math.abs(a-(a1+a2+a3))<.2;}
    private static Vec3 centroid(List<Vec3> p){return p.get(0).add(p.get(1)).add(p.get(2)).scale(1.0/3);}
    private static void drawTriangle(ServerPlayer owner,List<Vec3> p,DustParticleOptions color,int points){if(p.size()!=3)return;drawLineVisible(owner,p.get(0),p.get(1),color,points/3);drawLineVisible(owner,p.get(1),p.get(2),color,points/3);drawLineVisible(owner,p.get(2),p.get(0),color,points/3);}
    private static void drawLineVisible(ServerPlayer owner,Vec3 a,Vec3 b,DustParticleOptions color,int points){for(int i=0;i<=points;i++){Vec3 x=a.lerp(b,i/(double)Math.max(1,points)).add(0,.25,0);sendVisible(owner,x,color,1,0);}}
    private static void ringVisible(ServerPlayer owner,Vec3 c,double r,DustParticleOptions color,int points){for(int i=0;i<points;i++){double a=Math.PI*2*i/points;sendVisible(owner,new Vec3(c.x+Math.cos(a)*r,c.y,c.z+Math.sin(a)*r),color,1,0);}}
    private static void ringPrivate(ServerLevel level,ServerPlayer viewer,Vec3 c,double r,DustParticleOptions color,int points){for(int i=0;i<points;i++){double a=Math.PI*2*i/points;level.sendParticles(viewer,color,false,false,c.x+Math.cos(a)*r,c.y,c.z+Math.sin(a)*r,1,0,0,0,0);}}
    private static void drawLinePrivate(ServerLevel level,ServerPlayer viewer,Vec3 a,Vec3 b,DustParticleOptions color,int points){for(int i=0;i<=points;i++){Vec3 x=a.lerp(b,i/(double)points);level.sendParticles(viewer,color,false,false,x.x,x.y,x.z,1,0,0,0,0);}}
    private static void sendVisible(ServerPlayer owner,Vec3 pos,ParticleOptions particle,int count,double spread){for(ServerPlayer viewer:owner.level().players())if(viewer==owner||com.myriad.game.VisionSystem.canSeePosition(viewer,pos))owner.level().sendParticles(viewer,particle,false,false,pos.x,pos.y,pos.z,count,spread,spread,spread,0);}
    private static void cooldownMessage(ServerPlayer p,PlayerData data,Skill skill){p.sendSystemMessage(Component.translatable("message.myriad.skill_cd",(data.getCooldown(skill.getId())+19)/20));}
    private static void message(ServerPlayer p,String key){p.sendSystemMessage(Component.translatable(key));}
    private static State state(ServerPlayer p){return STATES.computeIfAbsent(p.getUUID(),id->new State());}
    public static void clearFor(UUID id){STATES.remove(id);}
    public static void cancelW(ServerPlayer p,PlayerData data){State s=STATES.get(p.getUUID());if(s!=null&&s.wStage>0){failW(data,s,false);PlayerDataProvider.syncToClient(p);}}

    private static final class State {
        final Node[] nodes={new Node(),new Node(),new Node()}; final ArrayDeque<Long> qReady=new ArrayDeque<>(); int qCharges=3,qRechargeTotal=140;
        int pendingNode=-1;long qPlacementUntil;boolean qNoCd,qNoMana;int wStage,wStart=-1,stableAimNode=-1,stableAimTicks;long wUntil;final List<FrozenLine> lines=new ArrayList<>();
        int eNode=-1,eLevel;long eUntil;Vec3 eOrigin;boolean eNoCd,eNoMana;int rLevel;long rWindupUntil,rUntil,rClosingUntil,rebuildUntil;boolean rNoCd,rNoMana;
        List<Vec3> rSnapshot=List.of(),closeTriangle=List.of();final Set<UUID> closeTargets=new HashSet<>();final Map<String,Long> rHitReady=new HashMap<>();
        final Map<UUID,EdgeMarks> marks=new HashMap<>();final List<NodeFlight> nodeFlights=new ArrayList<>();
    }
    private static final class Node {Vec3 pos;long expiresAt,unstableUntil;void clear(){pos=null;expiresAt=unstableUntil=0;}}
    private static final class EdgeMarks {final long[] until=new long[3];long loopReady;}
    private record NodeFlight(int node,Vec3 from,Vec3 to,long started,long until,float damage){}
    private static final class FrozenLine {final Vec3 a,b;final int edge,level;final float sp;final long fireAt;final Set<UUID> hit=new HashSet<>();FrozenLine(Vec3 a,Vec3 b,int edge,int level,float sp,long fireAt){this.a=a;this.b=b;this.edge=edge;this.level=level;this.sp=sp;this.fireAt=fireAt;}}
}
