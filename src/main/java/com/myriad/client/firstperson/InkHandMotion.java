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
    static final Pose IDLE = new Pose(new Vec3(.56, -.53, -1.23), new Vec3(.20, .74, -.64), HIDDEN, .1, 0);

    private static Pose p(double x, double y, double z, double sx, double sy, double sz,
                          double lx, double ly, double lz, double turn, double palm) {
        return new Pose(new Vec3(x, y, z), new Vec3(sx, sy, sz), new Vec3(lx, ly, lz), turn, palm);
    }

    private static final Pose BIND_DRAW = p(.65,-.66,-1.16,.25,.53,-.79, -.46,-.56,-1.06, -.25,-.3);
    private static final Pose BIND_CAST = p(.61,-.61,-1.26,.28,.56,-.75, -.16,-.35,-1.66, -.16,.2);

    private static final Pose SHIELD_HOLD = p(.57,-.49,-1.30,.16,.85,-.56, -.39,-.46,-1.22, .18,1.1);
    private static final Pose SHIELD_SEND = p(.55,-.48,-1.33,.17,.84,-.57, -.25,-.31,-1.66, .12,1.75);
    private static final Pose SHIELD_SELF = p(.56,-.50,-1.23,.21,.78,-.60, -.06,-.59,-.98, -.12,-.55);

    private static final Pose GATE_AIM = p(.53,-.45,-1.30,.07,.92,-.38, -.34,-.39,-1.46, -.13,.65);
    private static final Pose GATE_PUSH = p(.69,-.43,-1.35,.23,.90,-.37, -.60,-.32,-1.57, -.12,1.7);

    private static final Pose GUARD_AIM = p(.56,-.64,-1.17,.26,.57,-.72, -.43,-.43,-1.44, -.3,1.6);
    private static final Pose GUARD_PRESS = p(.56,-.70,-1.24,.28,.50,-.81, -.30,-.65,-1.44, -.38,-.75);

    private static final Pose ARRAY_BEGIN = p(.58,-.56,-1.27,-.28,.46,-.84, -.40,-.59,-1.22, -.36,.4);
    private static final Pose ARRAY_GATHER = p(.38,-.53,-1.35,-.44,.31,-.84, -.13,-.47,-1.37, -.57,.92);
    private static final Pose ARRAY_OPEN = p(.72,-.44,-1.37,.48,.71,-.60, -.70,-.38,-1.54, .24,1.8);

    private static final Pose LOCK_AIM = p(.64,-.65,-1.20,.29,.47,-.83, -.61,-.42,-1.46, -.38,.1);
    private static final Pose LOCK_BAR = p(.64,-.66,-1.24,.29,.44,-.85, -.14,-.44,-1.48, -.36,.15);
    private static final Pose LOCK_FOLD = p(.63,-.67,-1.22,.31,.43,-.85, -.25,-.65,-1.22, -.45,-.9);

    private static final Action BIND = new Action(BIND_DRAW, BIND_CAST, BIND_CAST, 1.15, 2.25, 8);
    private static final Action SEND = new Action(SHIELD_HOLD, SHIELD_SEND, SHIELD_SEND, 1.35, 3.4, 8.5);
    private static final Action SELF_SEND = new Action(SHIELD_HOLD, SHIELD_SELF, SHIELD_SELF, 1.25, 3.15, 8);
    private static final Action GATE = new Action(GATE_AIM, GATE_PUSH, GATE_PUSH, 1.25, 3.65, 9);
    private static final Action GUARD = new Action(GUARD_AIM, GUARD_PRESS, GUARD_PRESS, 1.30, 3.35, 10);
    private static final Action ARRAY = new Action(ARRAY_GATHER, ARRAY_OPEN, ARRAY_OPEN, 1.40, 4, 10);
    private static final Action LOCK = new Action(LOCK_AIM, LOCK_BAR, LOCK_FOLD, 1.15, 3.85, 11);

    private static final Action[] BASICS = {
        new Action(
            p(.59,-.53,-1.23,.19,.74,-.65, -.43,-.56,-1.14, .12,-.2),
            p(.58,-.51,-1.27,.18,.75,-.63, -.28,-.42,-1.54, .16,.36),
            p(.59,-.54,-1.23,.21,.71,-.66, -.36,-.51,-1.39, .12,.55), 1.05, 2.45, 6),
        new Action(
            p(.62,-.53,-1.23,.22,.73,-.64, -.62,-.47,-1.21, -.16,.32),
            p(.64,-.52,-1.25,.24,.72,-.64, -.13,-.45,-1.54, -.22,.54),
            p(.62,-.53,-1.23,.22,.73,-.64, -.22,-.51,-1.44, -.16,-.15), 1.18, 2.60, 6.6),
        new Action(
            p(.59,-.54,-1.23,.20,.72,-.65, -.46,-.69,-1.22, .08,1.30),
            p(.56,-.50,-1.26,.20,.75,-.63, -.27,-.40,-1.63, .18,1.88),
            p(.58,-.53,-1.24,.20,.73,-.64, -.33,-.45,-1.49, .12,1.32), 1.25, 2.90, 7.4)
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
        if (active(now)) return;
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
        boolean readied = active(now) && clip.ability == ability;
        if (!readied) entry = entry.mix(action.ready, .80);
        clip = new Clip(ability, phase, now, now + action.duration, entry, now, 0, 0);
    }

    void abort(double now) {
        if (active(now) && clip.phase != STOP) settle(now);
        flashAbility = -1;
        notified = Double.NEGATIVE_INFINITY;
    }

    private void settle(double now) {
        clip = new Clip(clip.ability, STOP, now, now + 3.6, pose(now), now, 0, 0);
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
        double sinceLast = now - clip.sampledAt;
        return clip.prior + (clip.progress - clip.prior) * Math.clamp(sinceLast * .5, 0, 1);
    }

    Pose pose(double now) {
        if (!active(now)) return IDLE;
        double age = age(now);
        if (clip.phase == AIM) return ease(clip.from, action(clip.ability, RELEASE).ready, age / 2.4);
        if (clip.phase == WINDUP) {
            Pose gather = ease(ARRAY_BEGIN, ARRAY_GATHER, progress(now));
            return ease(clip.from, gather, age / 2);
        }
        if (clip.phase == STOP) return ease(clip.from, IDLE, age / 3.6);
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
