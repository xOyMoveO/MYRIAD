package com.myriad.character.skills.yike;

import com.myriad.Myriad;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.combat.KnockbackBuffer;
import com.myriad.combat.SilenceSystem;
import com.myriad.game.TrainingMode;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Server-owned artillery state. Deadlines use world ticks, never local action progress. */
public final class YikeStance {
    private static final Identifier MOVEMENT = Identifier.fromNamespaceAndPath("myriad", "yike_stance_movement");
    private static final Identifier CHARGE = Identifier.fromNamespaceAndPath("myriad", "skill_charge_lock");
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, String> EXIT_REASONS = new HashMap<>();
    private static final double TURN_PER_TICK = Math.toRadians(90.0 / 20);

    private YikeStance() {}

    private static final class State {
        final Cast deployment;
        final ServerLevel level;
        final AbilitySkill q;
        final boolean mobile;
        final long until, originalQReady, impulse;
        int shots, lastShotCast = -1;
        long nextShot, pausedUntil, lastAimTick;
        boolean fired;
        Vec3 aim;

        State(Cast c, boolean mobile) {
            deployment = c;
            level = c.player.level();
            this.mobile = mobile;
            shots = artilleryForm(mobile).shells;
            until = c.now() + DURATION_TICKS;
            q = YikeRework.q(c.player);
            originalQReady = c.now() + (q == null ? 0 : c.data.getCooldown(q.getId()));
            nextShot = c.now();
            impulse = KnockbackBuffer.impulseToken(c.player);
            lastAimTick = c.now();
            aim = c.player.getLookAngle().normalize();
        }
    }

    public static boolean active(ServerPlayer p) { return STATES.containsKey(p.getUUID()); }
    public static boolean mobile(ServerPlayer p) { var s = STATES.get(p.getUUID()); return s != null && s.mobile; }
    public static Vec3 aim(ServerPlayer p) { var s = STATES.get(p.getUUID()); return s == null ? p.getLookAngle() : s.aim; }
    public static int shots(ServerPlayer p) { var s = STATES.get(p.getUUID()); return s == null ? 0 : s.shots; }
    public static String exitReason(ServerPlayer p) { return EXIT_REASONS.getOrDefault(p.getUUID(), ""); }
    public static int remaining(ServerPlayer p) {
        var s = STATES.get(p.getUUID());
        return s == null ? 0 : (int) Math.max(0, s.until - p.level().getGameTime());
    }
    public static int nextShotTicks(ServerPlayer p) {
        var s = STATES.get(p.getUUID());
        if (s == null) return 0;
        long now = p.level().getGameTime();
        long ready = Math.max(s.nextShot, s.pausedUntil);
        if (!s.fired) {
            var q = YikeRework.q(p);
            ready = Math.max(ready, s.originalQReady);
            if (q != null) ready = Math.max(ready, now + PlayerDataProvider.get(p).getCooldown(q.getId()));
        }
        return (int) Math.max(0, ready - now);
    }
    /** Static shot rules also consumed by the build snapshot. */
    public enum ShotForm {
        NORMAL(16, 30, 1.5, 1, 0, 0, 0),
        FIXED(26, 34, 1.5, .65f, .025f, 5, 22),
        MOBILE(20, 30, 1, .55f, .025f, 4, 27);

        public final double range, speed, radius;
        public final float baseMultiplier, multiplierPerRank;
        public final int shells, nextShotTicks;

        ShotForm(double range, double speed, double radius, float baseMultiplier,
                float multiplierPerRank, int shells, int nextShotTicks) {
            this.range = range;
            this.speed = speed;
            this.radius = radius;
            this.baseMultiplier = baseMultiplier;
            this.multiplierPerRank = multiplierPerRank;
            this.shells = shells;
            this.nextShotTicks = nextShotTicks;
        }

        public float multiplier(int stanceRank) {
            return baseMultiplier + multiplierPerRank * (stanceRank - 1);
        }
    }

    public static final int DURATION_TICKS = 120;
    public static final int PAUSE_FOR_W_TICKS = 11;
    private static ShotForm artilleryForm(boolean mobile) { return mobile ? ShotForm.MOBILE : ShotForm.FIXED; }
    private static ShotForm shotForm(ServerPlayer p) {
        var state = STATES.get(p.getUUID());
        return state == null ? ShotForm.NORMAL : artilleryForm(state.mobile);
    }
    public static float multiplier(ServerPlayer p) {
        var s = STATES.get(p.getUUID());
        return s == null ? ShotForm.NORMAL.baseMultiplier : artilleryForm(s.mobile).multiplier(s.deployment.rank);
    }
    public static double shotRange(ServerPlayer p) { return shotForm(p).range; }
    /** Blocks per second; callers convert to their projectile's tick step. */
    public static double shotSpeed(ServerPlayer p) { return shotForm(p).speed; }
    public static double shotRadius(ServerPlayer p) { return shotForm(p).radius; }

    /** Called only by the paid deployment release, after its 9/7 local-action ticks. */
    public static void enter(Cast c, boolean mobile) {
        if (!c.committed() || c.phase() != AbilityRuntime.Phase.COMMITTED || active(c.player)) return;
        State s = new State(c, mobile);
        EXIT_REASONS.remove(c.player.getUUID());
        STATES.put(c.player.getUUID(), s);
        refreshMovement(c.player);
        YikeVfx.event(c, mobile ? "mobile" : "fixed", c.player.getEyePosition(),
                c.player.getEyePosition().add(s.aim), mobile ? .45f : .65f, 120);
    }

    /** Rechecked at Q request and immediately before its single resource commit. */
    public static boolean canFire(Cast c) {
        var s = STATES.get(c.player.getUUID());
        if (s == null) return true;
        if (!usable(c.player, s)) return false;
        var q = YikeRework.q(c.player);
        return q != null && c.skill == q && c.data.skillLevel(q.getId()) > 0
                && s.shots > 0 && nextShotTicks(c.player) <= (s.fired && !c.accepted() ? 5 : 0)
                && (TrainingMode.isNoMana(c.player) || c.data.getMana() >= q.manaCost(c.rank));
    }

    /** Q owns mana payment; this consumes exactly one shell for the same committed cast. */
    public static void shotCommitted(Cast c) {
        var s = STATES.get(c.player.getUUID());
        if (s == null || !c.committed() || c.phase() != AbilityRuntime.Phase.COMMITTED
                || c.skill != YikeRework.q(c.player)
                || s.lastShotCast == c.castId() || s.shots <= 0) return;
        s.lastShotCast = c.castId();
        s.fired = true;
        s.shots--;
        s.nextShot = c.now() + artilleryForm(s.mobile).nextShotTicks;
        if (s.shots == 0) exit(c.player, "empty");
        else if (!canAffordShot(c.player, s)) exit(c.player, "mana");
    }

    /** W calls this only after its legal ground warning has actually been committed. */
    public static void pauseForW(ServerPlayer p) {
        var s = STATES.get(p.getUUID());
        if (s != null) s.pausedUntil = Math.max(s.pausedUntil, p.level().getGameTime() + PAUSE_FOR_W_TICKS);
    }
    public static boolean recastAvailable(Cast c) { return c.skill.slot() == 2 && active(c.player); }
    public static void recast(Cast c) { if (recastAvailable(c)) exit(c.player, "manual"); }

    public static void tick(ServerPlayer p) {
        var s = STATES.get(p.getUUID());
        if (s == null || !usable(p, s)) return;
        long now = p.level().getGameTime();
        if (now > s.lastAimTick) {
            s.aim = turnToward(s.aim, p.getLookAngle().normalize());
            s.lastAimTick = now;
        }
        refreshMovement(p);
    }

    private static boolean usable(ServerPlayer p, State s) {
        if (s.deployment.player != p || s.level != p.level()
                || !AbilityRuntime.valid(p) || !"yike".equals(PlayerDataProvider.get(p).getCharacterId())) {
            clear(p);
            return false;
        }
        if (p.level().getGameTime() >= s.until) { exit(p, "expired"); return false; }
        if (PlayerDataProvider.get(p).getBuff("g1_stun") > 0 || SilenceSystem.isSilenced(p)
                || KnockbackBuffer.impulseToken(p) != s.impulse && KnockbackBuffer.recentlyForced(p)) {
            exit(p, "control");
            return false;
        }
        if (!canAffordShot(p, s)) {
            exit(p, "mana");
            return false;
        }
        return true;
    }

    private static boolean canAffordShot(ServerPlayer p, State state) {
        var data = PlayerDataProvider.get(p);
        int rank = data.skillLevel(state.q.getId());
        return TrainingMode.isNoMana(p) || data.getMana() >= state.q.manaCost(rank);
    }

    /** Preserve the strongest self movement limit, rather than multiplying .7 by Q/W's .5. */
    public static void refreshMovement(ServerPlayer p) {
        var s = STATES.get(p.getUUID());
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (s == null) { speed.removeModifier(MOVEMENT); return; }
        double factor = s.mobile ? .7 : 0;
        var charge = speed.getModifier(CHARGE);
        if (charge != null) {
            double chargeFactor = Math.max(0, 1 + charge.amount());
            factor = chargeFactor == 0 ? 1 : Math.min(factor, chargeFactor) / chargeFactor;
        }
        var old = speed.getModifier(MOVEMENT);
        if (old == null || Math.abs(old.amount() - (factor - 1)) > 1E-9) {
            speed.removeModifier(MOVEMENT);
            if (factor < 1) speed.addTransientModifier(new AttributeModifier(MOVEMENT, factor - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        p.setSprinting(false);
        // Root never cancels a stance; it only removes mobile movement. Do not erase forced motion.
        if ((!s.mobile || PlayerDataProvider.get(p).getBuff("g1_root") > 0)
                && !KnockbackBuffer.recentlyForced(p)) {
            Vec3 velocity = p.getDeltaMovement();
            p.setDeltaMovement(0, Math.min(0, velocity.y), 0);
            p.syncVelocity=true;
        }
    }

    public static void beforeMove(net.minecraft.world.entity.player.Player subject) {
        if (subject instanceof ServerPlayer p) {
            var s = STATES.get(p.getUUID());
            if (s != null && usable(p, s)) refreshMovement(p);
        }
    }

    public static void exit(ServerPlayer p, String reason) {
        var s = STATES.remove(p.getUUID());
        if (s == null) return;
        EXIT_REASONS.put(p.getUUID(), reason);
        finish(p, s);
        // Cancel an unpaid Q without touching already-launched projectiles or independent W warnings.
        AbilityRuntime.cancel(p);
        PlayerDataProvider.syncToClient(p);
    }

    /** Lifecycle owner cancels pending casts/tasks separately; never recreate it during cleanup. */
    public static void clear(ServerPlayer p) {
        EXIT_REASONS.remove(p.getUUID());
        var s = STATES.remove(p.getUUID());
        if (s != null) finish(p, s);
        else refreshMovement(p);
    }

    private static void finish(ServerPlayer p, State s) {
        refreshMovement(p);
        var q = s.q;
        if (s.fired && q != null && !TrainingMode.isNoCooldown(p)) {
            var d = PlayerDataProvider.get(p);
            double reduction = Math.clamp(p.getAttributeValue(ModAttributes.COOLDOWN_REDUCTION), 0, 60);
            int full = Math.max(1, (int) (q.getCooldownTicks(Math.max(1, d.skillLevel(q.getId()))) * (1 - reduction / 100)));
            d.setCooldown(q.getId(), Math.max(d.getCooldown(q.getId()), full));
        }
        YikeVfx.event(s.deployment, "exit", p.getEyePosition(), p.getEyePosition().add(s.aim), 0, 1);
    }

    private static Vec3 turnToward(Vec3 from, Vec3 to) {
        double angle = Math.acos(Math.clamp(from.dot(to), -1, 1));
        if (angle <= TURN_PER_TICK) return to;
        Vec3 tangent = to.subtract(from.scale(from.dot(to)));
        // The 180-degree case has no unique tangent; pick a stable perpendicular without snapping.
        if (tangent.lengthSqr() < 1E-12) {
            tangent = from.cross(Math.abs(from.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        return from.scale(Math.cos(TURN_PER_TICK)).add(tangent.normalize().scale(Math.sin(TURN_PER_TICK))).normalize();
    }
}
