package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;

import static com.myriad.character.ability.StaffHandCues.*;

/** Camera-space choreography. Timelines never advance a combat action. */
final class StaffHandMotion {
    record Pose(Vec3 grip, Vec3 axis, Vec3 palm, double roll, double palmRoll) {
        Pose toward(Pose to, double w) {
            return new Pose(grip.lerp(to.grip, w), axis.lerp(to.axis, w), palm.lerp(to.palm, w),
                    roll + (to.roll - roll) * w, palmRoll + (to.palmRoll - palmRoll) * w);
        }
    }

    private record Move(Pose ready, Pose beat, Pose follow, double snap, double drift, double length) {}

    private static Pose p(double x, double y, double z, double ax, double ay, double az,
                          double lx, double ly, double lz, double roll, double palmRoll) {
        return new Pose(new Vec3(x,y,z), new Vec3(ax,ay,az), new Vec3(lx,ly,lz), roll, palmRoll);
    }

    static final Pose FROST_IDLE = p(.485,-.485,-1.020, .03,.94,-.34, -.67,-1.32,-.91, -.24,0);
    static final Pose HERB_IDLE = p(.555,-.495,-1.028, .13,.95,-.26, -.67,-1.32,-.91, -.22,0);

    private static final Pose LOCK = p(.54,-.56,-1.11, .19,.76,-.62, -.37,-.47,-1.38, -.06,.12);
    private static final Pose COMPRESS = p(.38,-.60,-1.16, -.12,.69,-.71, -.19,-.50,-1.22, .22,.39);
    private static final Pose PLANT = p(.41,-.72,-1.38, -.08,.74,-.67, -.30,-.62,-1.44, -.04,-.54);
    private static final Pose ICE_AIM = p(.60,-.62,-1.06, .35,.66,-.66, -.53,-.53,-1.38, -.16,.85);
    private static final Pose SEED_AIM = p(.62,-.60,-1.10, .30,.72,-.62, -.45,-.49,-1.38, -.32,-.85);
    private static final Pose GROVE_AIM = p(.66,-.66,-1.06, .38,.62,-.68, -.48,-.55,-1.37, -.38,.42);

    private static final Move BRACE = frost(
            p(.49,-.56,-1.10, -.14,.83,-.54, -.50,-.55,-1.18, -.14,.40),
            p(.41,-.51,-1.26, -.28,.77,-.57, -.31,-.43,-1.41, .16,1.16),
            p(.42,-.53,-1.28, -.26,.76,-.59, -.33,-.50,-1.39, .08,1.06), 9);
    private static final Move ZERO = frost(
            p(.39,-.50,-1.16, -.11,.84,-.53, -.21,-.46,-1.22, .16,.26), PLANT, PLANT, 10);
    private static final Move LANCE = frost(
            p(.66,-.64,-.96, .11,.24,-.96, -.52,-.66,-1.18, -.06,-.50),
            p(.34,-.57,-1.50, .02,.22,-.97, -.61,-.68,-1.30, -.30,.20),
            p(.41,-.55,-1.26, .07,.38,-.92, -.60,-.70,-1.23, -.20,.14), 9);
    private static final Move EXPANSE = frost(ICE_AIM,
            p(.54,-.65,-1.38, .31,.46,-.83, -.76,-.56,-1.44, .46,-.45),
            p(.72,-.60,-1.15, .49,.61,-.62, -.82,-.60,-1.24, .28,-.15), 11);
    private static final Move BASTION = frost(
            p(.35,-.55,-1.16, -.16,.86,-.48, -.24,-.54,-1.25, .14,.28),
            p(.74,-.45,-1.25, .26,.87,-.42, -.76,-.39,-1.32, -.34,1.57),
            p(.71,-.49,-1.26, .27,.83,-.49, -.72,-.45,-1.29, -.28,1.34), 12);

    private static final Move DEW = herb(
            p(.60,-.58,-1.12, .30,.72,-.62, -.50,-.63,-1.22, -.36,1.21),
            p(.57,-.56,-1.20, .25,.75,-.61, -.29,-.42,-1.56, -.24,1.77),
            p(.57,-.57,-1.17, .28,.71,-.64, -.39,-.45,-1.48, -.20,1.28), 9);
    private static final Move DEW_IN = herb(DEW.ready,
            p(.56,-.58,-1.10, .26,.76,-.59, -.14,-.56,-1.04, -.28,1.58),
            p(.59,-.58,-1.10, .30,.73,-.61, -.36,-.67,-1.02, -.30,1.14), 9);
    private static final Move SEED = herb(
            p(.63,-.69,-1.02, .28,.48,-.83, -.61,-.71,-1.08, -.52,-.37),
            p(.46,-.51,-1.38, .22,.70,-.68, -.42,-.45,-1.42, .02,1.20),
            p(.57,-.54,-1.25, .36,.76,-.54, -.53,-.51,-1.33, .08,1.50), 9);
    private static final Move ARRAY = herb(SEED_AIM,
            p(.69,-.60,-1.20, .42,.72,-.55, -.38,-.66,-1.52, -.34,-1.02),
            p(.67,-.61,-1.17, .38,.71,-.59, -.43,-.67,-1.42, -.32,-.82), 10);
    private static final Move THREAD = herb(
            p(.61,-.59,-1.18, .34,.69,-.64, -.34,-.53,-1.20, .02,.20),
            p(.61,-.57,-1.24, .28,.71,-.64, -.27,-.43,-1.59, -.12,.52),
            p(.60,-.57,-1.12, .32,.72,-.61, -.43,-.55,-1.16, -.34,-.70), 10);
    private static final Move THREAD_IN = herb(THREAD.ready,
            p(.60,-.57,-1.10, .32,.74,-.59, -.22,-.55,-1.14, -.10,.93),
            p(.61,-.60,-1.10, .35,.70,-.62, -.39,-.66,-1.02, -.30,.21), 10);
    private static final Move GROVE = herb(GROVE_AIM,
            p(.69,-.50,-1.22, .38,.74,-.55, -.39,-.36,-1.50, -.02,1.92),
            p(.70,-.49,-1.22, .45,.74,-.50, -.59,-.40,-1.44, .16,1.61), 12);
    private static final Move CANOPY = herb(
            p(.45,-.59,-1.17, -.16,.63,-.76, -.34,-.56,-1.24, -.58,1.03),
            p(.75,-.47,-1.26, .47,.73,-.49, -.74,-.39,-1.41, .30,1.80),
            p(.77,-.52,-1.25, .53,.70,-.48, -.77,-.45,-1.37, .42,1.45), 12);

    private static final Move[] ICE_BASICS = {
        frost(FROST_IDLE, p(.48,-.57,-1.34, .15,.63,-.76, -.67,-1.32,-.91, -.04,0),
                p(.51,-.57,-1.22, .20,.70,-.68, -.67,-1.32,-.91, -.14,0), 6),
        frost(FROST_IDLE, p(.65,-.55,-1.33, .36,.66,-.66, -.67,-1.32,-.91, -.52,0),
                p(.69,-.57,-1.24, .48,.68,-.55, -.67,-1.32,-.91, -.62,0), 7),
        frost(FROST_IDLE, p(.46,-.45,-1.27, .14,.85,-.51, -.67,-1.32,-.91, -.08,0),
                p(.46,-.67,-1.30, .12,.60,-.79, -.67,-1.32,-.91, .12,0), 8)
    };

    private static final Move[] HERB_BASICS = {
        herb(HERB_IDLE, p(.58,-.56,-1.18, .28,.75,-.60, -.35,-.49,-1.38, -.18,1.55),
                p(.59,-.58,-1.17, .32,.72,-.61, -.42,-.56,-1.30, -.30,1.34), 7),
        herb(HERB_IDLE, p(.66,-.56,-1.18, .41,.70,-.58, -.52,-.47,-1.42, -.56,.41),
                p(.67,-.57,-1.18, .42,.70,-.58, -.32,-.54,-1.40, -.52,.90), 8),
        herb(HERB_IDLE, p(.56,-.53,-1.15, .26,.80,-.54, -.30,-.54,-1.35, .04,1.45),
                p(.56,-.50,-1.18, .30,.80,-.52, -.36,-.39,-1.41, .16,1.85), 9)
    };

    private static Move frost(Pose ready, Pose beat, Pose follow, double length) {
        return new Move(ready, beat, follow, .95, Math.min(3.3, length * .48), length);
    }

    private static Move herb(Pose ready, Pose beat, Pose follow, double length) {
        return new Move(ready, beat, follow, 1.4, Math.min(5, length * .56), length);
    }

    private static Move volley(int shot) {
        double y = shot == 1 ? -.49 : shot == 2 ? -.62 : -.55;
        Pose beat = p(.50,y,-1.36, .12,.64,-.76, -.44,y+.10,-1.36, -.06+shot*.13,.20);
        Pose follow = p(.55,y-.04,-1.21, .21,.70,-.68, -.48,y+.02,-1.26, -.16,.04);
        return frost(FROST_IDLE, beat, follow, shot == 2 ? 7 : 5.5);
    }

    private record Clip(int ability, int signal, double start, double until, Pose from,
                        Move move, double sample, double oldProgress, double progress) {}
    private Clip clip;
    private int combo = -1;
    private double fired = Double.NEGATIVE_INFINITY;
    private final boolean frost;
    private final Pose idle;

    StaffHandMotion(boolean frost) {
        this.frost = frost;
        idle = frost ? FROST_IDLE : HERB_IDLE;
    }

    void reset() {
        clip = null;
        combo = -1;
        fired = Double.NEGATIVE_INFINITY;
    }

    int combo() { return combo; }

    void cue(int ability, int signal, double now, int life, double value, double cooldown) {
        if (signal == STOP) {
            if (clip != null && clip.ability == ability && held(clip.signal)) abort(now);
            return;
        }
        if (signal == BASIC) {
            combo = now - fired > Math.max(22, cooldown * 1.6) ? 0 : (combo + 1) % 3;
            fired = now;
        }
        Move move = getMove(ability, signal, value);
        if (move == null) return;
        Pose from = pose(now);
        if (held(signal)) {
            boolean refresh = clip != null && clip.ability == ability && clip.signal == signal && now < clip.until;
            double progress = refresh ? progress(now) : 0;
            clip = new Clip(ability, signal, refresh ? clip.start : now, now + life,
                    refresh ? clip.from : from, move, now, progress, Math.clamp(value, 0, 1));
        } else {
            clip = new Clip(ability, signal, now, now + move.length, from, move, now, 0, 0);
        }
    }

    void abort(double now) {
        if (clip == null || clip.signal == STOP || now >= clip.until) return;
        clip = new Clip(clip.ability, STOP, now, now + 3.5, pose(now), null, now, 0, 0);
    }

    Pose pose(double now) {
        if (clip == null || now >= clip.until) return idle;
        double age = Math.max(0, now - clip.start);
        if (clip.signal == STOP) return ease(clip.from, idle, age / 3.5);
        Move m = clip.move;
        if (held(clip.signal)) {
            Pose target = clip.signal == HOLD ? ease(LOCK, COMPRESS, progress(now)) : m.ready;
            return ease(ease(clip.from, target, age / 2.5), idle, (now - clip.until + 1.75) / 1.75);
        }
        if (age <= m.snap) {
            double t = Math.clamp(age / m.snap, 0, 1);
            // Quadratic through a short anticipation pose; no client-side fake release on click.
            return clip.from.toward(m.ready, t).toward(m.ready.toward(m.beat, t), t);
        }
        if (age < m.drift) return ease(m.beat, m.follow, (age - m.snap) / (m.drift - m.snap));
        return ease(m.follow, idle, (age - m.drift) / (m.length - m.drift));
    }

    private static boolean held(int signal) { return signal == AIM || signal == HOLD; }

    private double progress(double now) {
        return clip.oldProgress + (clip.progress - clip.oldProgress) * Math.clamp((now - clip.sample) / 2, 0, 1);
    }

    private Move getMove(int ability, int signal, double value) {
        if (signal == BASIC) return (frost ? ICE_BASICS : HERB_BASICS)[combo];
        if (frost) return switch (ability) {
            case 2 -> volley((int) Math.clamp(value, 0, 2));
            case 3 -> BRACE;
            case 4 -> signal == AIM ? frost(LOCK, LOCK, LOCK, 8) : ZERO;
            case 5 -> LANCE;
            case 6 -> EXPANSE;
            case 7 -> BASTION;
            default -> null;
        };
        return switch (ability) {
            case 2 -> signal == SELF ? DEW_IN : DEW;
            case 3 -> SEED;
            case 4 -> ARRAY;
            case 5 -> signal == SELF ? THREAD_IN : THREAD;
            case 6 -> GROVE;
            case 7 -> CANOPY;
            default -> null;
        };
    }

    private static Pose ease(Pose from, Pose to, double fraction) {
        double t = Math.clamp(fraction, 0, 1);
        return from.toward(to, t * t * (3 - 2 * t));
    }
}
