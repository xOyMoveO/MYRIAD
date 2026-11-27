package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;

import static com.myriad.character.skills.xuanchen.rework.InkCastAnimation.*;

final class InkHandMotion {
    record Pose(Vec3 grip, Vec3 axis, Vec3 palm, double twist, double palmRoll) {
        Pose mix(Pose next, double t) {
            return new Pose(grip.lerp(next.grip, t), axis.lerp(next.axis, t), palm.lerp(next.palm, t),
                    twist + (next.twist - twist) * t, palmRoll + (next.palmRoll - palmRoll) * t);
        }
    }

    private record Action(Pose ready, Pose cast, Pose end, double wind, double hold, double duration) {}
    private record Clip(int ability, int phase, double start, double expires, Pose from,
                        double sampledAt, double prior, double progress) {}

    private static final Vec3 HIDDEN = new Vec3(-.67, -1.31, -.89);
    private static final double SETTLE = 3.6;
    static final Pose IDLE = new Pose(new Vec3(.557, -.449, -.935), new Vec3(.20, .97, -.12), HIDDEN, -.15, 0);

    private static Pose p(double x, double y, double z, double sx, double sy, double sz,
                          double lx, double ly, double lz, double turn, double palm) {
        return new Pose(new Vec3(x, y, z), new Vec3(sx, sy, sz), new Vec3(lx, ly, lz), turn, palm);
    }

    private static final Pose BIND_DRAW = p(.62,-.61,-1.02,.30,.63,-.71, -.46,-.54,-1.02, -.52,-.3);
    private static final Pose BIND_CAST = p(.58,-.56,-1.12,.32,.66,-.68, -.18,-.36,-1.56, -.44,.2);

    private static final Pose SHIELD_HOLD = p(.55,-.46,-1.14,.22,.89,-.40, -.39,-.45,-1.16, -.20,1.1);
    private static final Pose SHIELD_SEND = p(.53,-.45,-1.17,.23,.88,-.41, -.26,-.32,-1.56, -.26,1.75);
    private static final Pose SHIELD_SELF = p(.54,-.47,-1.08,.27,.84,-.47, -.08,-.57,-.94, -.44,-.55);

    private static final Pose GATE_AIM = p(.52,-.43,-1.14,.14,.94,-.31, -.35,-.39,-1.38, -.42,.65);
    private static final Pose GATE_PUSH = p(.66,-.41,-1.19,.28,.92,-.27, -.58,-.33,-1.48, -.40,1.7);

    private static final Pose GUARD_AIM = p(.54,-.59,-1.03,.30,.67,-.68, -.44,-.43,-1.36, -.56,1.6);
    private static final Pose GUARD_PRESS = p(.54,-.65,-1.10,.32,.60,-.73, -.31,-.63,-1.36, -.62,-.75);

    private static final Pose ARRAY_BEGIN = p(.56,-.52,-1.11,-.20,.58,-.79, -.40,-.57,-1.16, -.58,.4);
    private static final Pose ARRAY_GATHER = p(.38,-.49,-1.19,-.35,.44,-.83, -.14,-.46,-1.30, -.74,.92);
    private static final Pose ARRAY_OPEN = p(.70,-.41,-1.20,.50,.77,-.40, -.68,-.38,-1.45, -.12,1.8);

    private static final Pose LOCK_AIM = p(.61,-.60,-1.05,.33,.58,-.74, -.59,-.42,-1.38, -.62,.1);
    private static final Pose LOCK_BAR = p(.61,-.61,-1.09,.33,.55,-.77, -.16,-.44,-1.40, -.60,.15);
    private static final Pose LOCK_FOLD = p(.60,-.62,-1.07,.35,.54,-.76, -.26,-.63,-1.16, -.68,-.9);

    private static final Action BIND = new Action(BIND_DRAW, BIND_CAST, BIND_CAST, 1.15, 2.25, 8);
    private static final Action SEND = new Action(SHIELD_HOLD, SHIELD_SEND, SHIELD_SEND, 1.35, 3.4, 8.5);
    private static final Action SELF_SEND = new Action(SHIELD_HOLD, SHIELD_SELF, SHIELD_SELF, 1.25, 3.15, 8);
    private static final Action GATE = new Action(GATE_AIM, GATE_PUSH, GATE_PUSH, 1.25, 3.65, 9);
    private static final Action GUARD = new Action(GUARD_AIM, GUARD_PRESS, GUARD_PRESS, 1.30, 3.35, 10);
    private static final Action ARRAY = new Action(ARRAY_GATHER, ARRAY_OPEN, ARRAY_OPEN, 1.40, 4, 10);
    private static final Action LOCK = new Action(LOCK_AIM, LOCK_BAR, LOCK_FOLD, 1.15, 3.85, 11);

    private static final Action[] BASICS = {
        new Action(
            p(.56,-.50,-1.06,.26,.81,-.53, -.43,-.54,-1.09, -.32,-.2),
            p(.55,-.48,-1.11,.25,.82,-.51, -.29,-.42,-1.46, -.28,.36),
            p(.56,-.51,-1.07,.28,.79,-.55, -.37,-.50,-1.32, -.32,.55), 1.05, 2.45, 6),
        new Action(
            p(.59,-.50,-1.06,.29,.80,-.52, -.60,-.46,-1.16, -.52,.32),
            p(.61,-.49,-1.09,.31,.79,-.52, -.15,-.44,-1.46, -.56,.54),
            p(.59,-.50,-1.06,.29,.80,-.52, -.24,-.50,-1.37, -.52,-.15), 1.18, 2.60, 6.6),
        new Action(
            p(.56,-.51,-1.06,.27,.79,-.54, -.46,-.67,-1.16, -.36,1.30),
            p(.54,-.47,-1.10,.27,.82,-.51, -.28,-.40,-1.54, -.26,1.88),
            p(.56,-.50,-1.07,.27,.80,-.53, -.34,-.45,-1.41, -.32,1.32), 1.25, 2.90, 7.4)
    };
    private static final int PREPARE = -1;

    private Clip clip;
    private int combo = -1;
    private double fired = Double.NEGATIVE_INFINITY;
    private double notified = Double.NEGATIVE_INFINITY;
    private int flashAbility = -1;

    void reset() {
        clip = null;
        combo = -1;
        fired = notified = Double.NEGATIVE_INFINITY;
        flashAbility = -1;
    }

    void prepare(int ability, double now, double cadence) {
        if (active(now) && clip.phase != STOP) return;
        int slot = ability == 0 ? 8 + next(now, cadence) : ability;
        if (slot != 2 && slot != 3 && slot < 8) return;
        clip = new Clip(slot, PREPARE, now, now + 4.5, pose(now), now, 0, 0);
    }

    void cue(int ability, int phase, double now, int life, double progress, double cadence) {
        if (phase == ACTIVATE) {
            if (!active(now) || clip.ability == ability) {
                notified = now;
                flashAbility = ability;
            }
            return;
        }
        if (phase == STOP) {
            if (active(now) && clip.ability == ability && (clip.phase == AIM || clip.phase == WINDUP)) settle(now);
            return;
        }
        if (phase == BASIC) {
            combo = next(now, cadence);
            fired = now;
            ability = 8 + combo;
        }
        Pose entry = pose(now);
        if (phase == AIM || phase == WINDUP) {
            boolean refreshing = active(now) && clip.phase == phase && clip.ability == ability;
            double begin = refreshing ? clip.start : now;
            double prior = refreshing ? progress(now) : 0;
            clip = new Clip(ability, phase, begin, now + life, refreshing ? clip.from : entry, now, prior, progress);
            return;
        }
        var action = action(ability, phase);
        if (action == null) return;
        boolean fromCurrent = active(now) && (clip.ability == ability || clip.phase == STOP) || expiredHold(now);
        if (!fromCurrent) entry = entry.mix(action.ready, .80);
        clip = new Clip(ability, phase, now, now + action.duration, entry, now, 0, 0);
    }

    void abort(double now) {
        if (active(now) && clip.phase != STOP) settle(now);
        flashAbility = -1;
        notified = Double.NEGATIVE_INFINITY;
    }

    private void settle(double now) {
        clip = new Clip(clip.ability, STOP, now, now + SETTLE, pose(now), now, 0, 0);
    }

    boolean active(double now) { return clip != null && now >= clip.start && now < clip.expires; }
    int phase(double now) { return active(now) ? clip.phase : STOP; }
    int ability(double now) { return active(now) ? clip.ability : -1; }
    int combo() { return combo; }
    int flashAbility() { return flashAbility; }
    double age(double now) { return active(now) ? now - clip.start : -1; }
    double flashAge(double now) { return now - notified; }

    double progress(double now) {
        if (!active(now) || clip.phase != WINDUP) return 0;
        return sampledProgress(now);
    }

    private double sampledProgress(double now) {
        double sinceLast = now - clip.sampledAt;
        return clip.prior + (clip.progress - clip.prior) * Math.clamp(sinceLast * .5, 0, 1);
    }

    private boolean expiredHold(double now) {
        return clip != null && now >= clip.expires && now < clip.expires + SETTLE
                && (clip.phase == AIM || clip.phase == WINDUP);
    }

    private Pose heldPose(double now) {
        double age = now - clip.start;
        if (clip.phase == AIM) return ease(clip.from, action(clip.ability, RELEASE).ready, age / 2.4);
        Pose gather = ease(ARRAY_BEGIN, ARRAY_GATHER, sampledProgress(now));
        return ease(clip.from, gather, age / 2);
    }

    Pose pose(double now) {
        if (expiredHold(now)) return ease(heldPose(clip.expires), IDLE, (now - clip.expires) / SETTLE);
        if (!active(now)) return IDLE;
        double age = now - clip.start;
        if (clip.phase == AIM || clip.phase == WINDUP) return heldPose(now);
        if (clip.phase == STOP) return ease(clip.from, IDLE, age / SETTLE);
        var action = action(clip.ability, clip.phase);
        if (clip.phase == PREPARE) {
            Pose ready = ease(clip.from, action.ready, age / 1.15);
            return ease(ready, IDLE, (age - 2.4) / 2.1);
        }
        if (age <= action.wind) return ease(clip.from, action.cast, age / action.wind);
        if (age <= action.hold) return ease(action.cast, action.end, (age - action.wind) / (action.hold - action.wind));
        return ease(action.end, IDLE, (age - action.hold) / (action.duration - action.hold));
    }

    private int next(double now, double cadence) {
        return now - fired > Math.max(28, cadence + 14) ? 0 : (combo + 1) % 3;
    }

    private static Pose ease(Pose from, Pose to, double amount) {
        double t = Math.clamp(amount, 0, 1);
        return from.mix(to, t * t * (3 - 2 * t));
    }

    private static Action action(int ability, int phase) {
        return switch (ability) {
            case 2 -> BIND;
            case 3 -> phase == SELF ? SELF_SEND : SEND;
            case 4 -> GATE;
            case 5 -> GUARD;
            case 6 -> ARRAY;
            case 7 -> LOCK;
            case 8, 9, 10 -> BASICS[ability - 8];
            default -> null;
        };
    }
}
