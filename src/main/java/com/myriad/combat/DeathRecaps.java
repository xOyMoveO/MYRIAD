package com.myriad.combat;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.game.*;
import com.myriad.network.DeathRecapPayload;
import com.myriad.network.DeathRecapPayload.*;
import com.myriad.player.PlayerDataProvider;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Real-time, per-life receipt tape, independent of telemetry and match rewards. Server thread only. */
public final class DeathRecaps {
    private DeathRecaps() {}
    private static final Map<UUID, Tape> TAPES = new HashMap<>();
    private static final Map<DamageContext, Context> CONTEXTS = new WeakHashMap<>();
    private record Typed(UUID target, DamageSource source, DamageCalc.Kind kind) {}
    private static final ThreadLocal<Typed> TYPED = new ThreadLocal<>();
    private record Context(Tape tape, Source source, int kind, long at) {}
    private static final Map<UUID, Context> LAST_HP = new HashMap<>();
    private record Damage(long at, Source source, int kind, float health, float shield) {}
    private record Cc(long from, long until, Source source, int kind) {}
    private static final class Tape {
        final String role, dimension;
        final Faction faction;
        final ArrayList<Damage> damage = new ArrayList<>();
        final ArrayList<Cc> controls = new ArrayList<>();
        long lostDamage = Long.MIN_VALUE, lostControl = Long.MIN_VALUE;
        long died = Long.MIN_VALUE;
        DamageSource lastHpSource;
        Context fatalContext;
        boolean frozen;
        Tape(ServerPlayer p) {
            role = PlayerDataProvider.get(p).getCharacterId(); faction = GameManager.get().factionOf(p);
            dimension = p.level().dimension().identifier().toString();
        }
        boolean same(ServerPlayer p) {
            return Objects.equals(role, PlayerDataProvider.get(p).getCharacterId()) && faction == GameManager.get().factionOf(p)
                    && dimension.equals(p.level().dimension().identifier().toString());
        }
    }
    private static boolean participant(ServerPlayer p) {
        var m = GameManager.get();
        return ModState.isEnabled() && !TrainingMode.isEnabled(p) && m != null && m.isParticipant(p.getUUID()) && m.getPhase() == GameManager.Phase.PLAYING
                && m.factionOf(p) != null && FirstGroupSkills.active(PlayerDataProvider.get(p).getCharacterId());
    }
    private static Tape tape(ServerPlayer p) {
        var old = TAPES.get(p.getUUID());
        if (old != null && !old.same(p)) { clear(p); old = null; }
        if (old == null) { old = new Tape(p); TAPES.put(p.getUUID(), old); }
        return old;
    }
    private static boolean canRecord(ServerPlayer p) {
        return participant(p) && p.isAlive() && !p.isSpectator() && !HeroRespawnSystem.isWaiting(p);
    }
    private static String clip(String s, int max) { return s == null ? "" : s.substring(0, Math.min(max, s.length())); }
    private static Source source(DamageSource damage, LivingEntity victim) {
        var attacker = damage.getEntity();
        if (attacker instanceof ServerPlayer p) {
            boolean basic = !DamageCalc.isSkillHit(victim);
            String skill = basic ? "" : AbilityRuntime.sourceSkill();
            var context = DamageCalc.peekSkillHit(victim);
            if (!basic && (skill == null || skill.isBlank()) && context != null && context.slot() >= 0 && context.slot() <= 4) {
                var d = PlayerDataProvider.get(p);
                var resolved = FirstGroupSkills.resolve(com.myriad.character.CharacterType.byId(d.getCharacterId()), context.slot(), d.getSkillLevels());
                if (resolved != null) skill = resolved.getId();
            }
            return new Source(p.getUUID(), clip(p.getName().getString(), 96), clip(PlayerDataProvider.get(p).getCharacterId(), 64), clip(skill, 128), 0, basic);
        }
        int category = attacker instanceof LivingEntity living ? LaneSystem.isTower(living) ? 1 : LaneSystem.isManaged(living) ? 2
                : MonsterManager.isMonster(living) ? 3 : 4 : 5;
        return new Source(attacker == null ? DeathRecapPayload.ENVIRONMENT : attacker.getUUID(),
                clip(attacker == null ? damage.getMsgId() : attacker.getType().getDescriptionId(), 96), "", "", category, true);
    }
    private static int kind(DamageSource source, LivingEntity target) {
        var typed = TYPED.get();
        if (typed != null && typed.target.equals(target.getUUID()) && typed.source == source) return typed.kind.ordinal();
        var c = DamageCalc.peekSkillHit(target);
        if (c != null) return c.kind().ordinal();
        if (NpcCombat.isMagic(source) || source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) return 1;
        return source.getEntity() == null ? 3 : 0;
    }
    /** Metadata only: preserves the original hurt call, reductions and hit/skill semantics. */
    public static void typedHurt(LivingEntity target, DamageSource source, float damage, DamageCalc.Kind kind) {
        var old = TYPED.get(); TYPED.set(new Typed(target.getUUID(), source, kind));
        try { target.hurt(source, damage); }
        finally { if (old == null) TYPED.remove(); else TYPED.set(old); }
    }
    public static void incoming(DamageContext event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !canRecord(p)) return;
        var tape = tape(p); if (tape.frozen || tape.died != Long.MIN_VALUE) return;
        CONTEXTS.put(event, new Context(tape, source(event.getSource(), p), kind(event.getSource(), p), p.level().getGameTime()));
    }
    public static void shield(DamageContext event, float absorbed) {
        var c = CONTEXTS.get(event);
        if (c != null) add(c, 0, absorbed);
    }
    public static void afterHealth(DamageContext transaction, float actual) {
        var context = CONTEXTS.remove(transaction);
        if (context == null || !(transaction.getEntity() instanceof ServerPlayer player)
                || TAPES.get(player.getUUID()) != context.tape || context.tape.frozen) return;
        if (actual > 0) {
            context.tape.lastHpSource = transaction.getSource();
            LAST_HP.put(player.getUUID(), context);
            add(context, actual, 0);
        }
    }
    private static void add(Context c, float health, float shield) {
        if (!Float.isFinite(health) || !Float.isFinite(shield) || health + shield <= 0 || c.tape.frozen) return;
        var t = c.tape; prune(t, c.at);
        for (int i = t.damage.size() - 1; i >= 0 && t.damage.get(i).at == c.at; i--) {
            var d = t.damage.get(i);
            if (d.source.equals(c.source) && d.kind == c.kind) {
                t.damage.set(i, new Damage(c.at, c.source, c.kind, sum(d.health, health), sum(d.shield, shield))); return;
            }
        }
        t.damage.add(new Damage(c.at, c.source, c.kind, health, shield));
        if (t.damage.size() > DeathRecapPayload.MAX_HITS) t.lostDamage = Math.max(t.lostDamage, t.damage.removeFirst().at);
    }
    private static float sum(float a, float b) { return (float)Math.min(Float.MAX_VALUE, (double)a + b); }
    public static void control(ServerPlayer owner, ServerPlayer target, String skill, int kind, long now) {
        control(owner, target, skill, kind, now, 1);
    }
    public static void control(ServerPlayer owner, ServerPlayer target, String skill, int kind, long now, int ticks) {
        if (ticks <= 0) return;
        if (!canRecord(target) || !participant(owner) || owner.level() != target.level()
                || GameManager.get().factionOf(owner) == GameManager.get().factionOf(target)) return;
        var t = tape(target); if (t.frozen || t.died != Long.MIN_VALUE) return;
        prune(t, now);
        var s = new Source(owner.getUUID(), clip(owner.getName().getString(), 96), clip(PlayerDataProvider.get(owner).getCharacterId(), 64), clip(skill, 128), 0, false);
        for (int i = t.controls.size() - 1; i >= 0; i--) {
            var c = t.controls.get(i);
            if (c.kind == kind && c.source.equals(s) && c.until >= now) {
                t.controls.set(i, new Cc(c.from, Math.max(c.until, now + ticks), s, kind)); return;
            }
        }
        t.controls.add(new Cc(now, now + ticks, s, kind));
        if (t.controls.size() > DeathRecapPayload.MAX_CONTROLS) t.lostControl = Math.max(t.lostControl, t.controls.removeFirst().until);
    }
    private static void prune(Tape t, long now) {
        t.damage.removeIf(d -> d.at < now - DeathRecapPayload.WINDOW);
        t.controls.removeIf(c -> c.until <= now - DeathRecapPayload.WINDOW);
    }
    public static void endControls(ServerPlayer player, long now) {
        var tape = TAPES.get(player.getUUID());
        if (tape != null && !tape.frozen)
            tape.controls.replaceAll(control -> new Cc(control.from, Math.min(control.until, now), control.source, control.kind));
    }
    /** Called exactly once after a valid formal death was accepted, never for training revivals. */
    public static void died(ServerPlayer p, DamageSource lethal) {
        if (!participant(p)) return;
        var t = tape(p); if (t.frozen || t.died != Long.MIN_VALUE) return;
        t.died = p.level().getGameTime();
        var contact = LAST_HP.get(p.getUUID());
        if (t.lastHpSource == lethal && contact != null && contact.at == t.died) t.fatalContext = contact;
    }
    private static void freeze(ServerPlayer p, Tape t) {
        if (t.frozen) return;
        t.frozen = true; prune(t, t.died);
        Context fatal = t.fatalContext;
        var hits = t.damage.stream().filter(d -> d.at <= t.died).sorted(Comparator.comparingLong(Damage::at))
                .map(d -> new Hit((int)(t.died - d.at), d.source, d.kind, d.health, d.shield,
                        fatal != null && d.at == fatal.at && d.kind == fatal.kind && d.source.equals(fatal.source) && d.health > 0)).toList();
        t.fatalContext = null; t.lastHpSource = null;
        var cc = t.controls.stream().map(c -> new Cc(Math.max(c.from, t.died - DeathRecapPayload.WINDOW), Math.min(c.until, t.died + 1), c.source, c.kind))
                .filter(c -> c.until > c.from).sorted(Comparator.comparingLong(Cc::from))
                .map(c -> new Control((int)(t.died - c.from), (int)(c.until - c.from), c.source, c.kind)).toList();
        send(p, new DeathRecapPayload(true, t.lostDamage >= t.died - DeathRecapPayload.WINDOW
                || t.lostControl > t.died - DeathRecapPayload.WINDOW, hits, cc));
    }
    public static void tick(MinecraftServer server) {
        CONTEXTS.clear();
        for (var p : server.getPlayerList().getPlayers()) {
            var t = TAPES.get(p.getUUID()); if (t == null) continue;
            if (!participant(p) || !t.same(p)) { clear(p); continue; }
            if (t.died != Long.MIN_VALUE && !t.frozen) freeze(p, t);
            else if (!t.frozen) prune(t, p.level().getGameTime());
        }
        LAST_HP.clear();
    }
    /** New life starts an empty tape; the client's previous immutable recap remains available. */
    public static void lifeStarted(UUID player) { forget(player); }
    public static void clear(ServerPlayer p) { forget(p.getUUID()); send(p, DeathRecapPayload.empty()); }
    public static void forget(UUID player) {
        var old = TAPES.remove(player); LAST_HP.remove(player);
        CONTEXTS.values().removeIf(c -> c.tape == old);
    }
    private static void send(ServerPlayer player, DeathRecapPayload payload) {
        if (player.connection != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, DeathRecapPayload.TYPE))
            com.myriad.network.Packets.sendToPlayer(player, payload);
    }
    public static void clearAll() { TAPES.clear(); CONTEXTS.clear(); LAST_HP.clear(); }
}
