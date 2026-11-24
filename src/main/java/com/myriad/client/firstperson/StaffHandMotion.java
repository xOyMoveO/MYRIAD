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

    static final Pose FROST_IDLE = p(.60,-.61,-1.27, .23,.66,-.73, -.67,-1.32,-.91, .14,0);
    static final Pose HERB_IDLE = p(.61,-.62,-1.28, .32,.62,-.72, -.67,-1.32,-.91, -.16,0);

    private static final Pose LOCK = p(.58,-.62,-1.27, .19,.64,-.74, -.37,-.47,-1.45, .26,.12);
    private static final Pose COMPRESS = p(.40,-.66,-1.30, -.13,.59,-.80, -.17,-.50,-1.27, .50,.39);
    private static final Pose PLANT = p(.43,-.77,-1.50, -.08,.67,-.73, -.30,-.64,-1.53, .15,-.54);
    private static final Pose ICE_AIM = p(.64,-.68,-1.19, .36,.54,-.76, -.53,-.53,-1.46, .14,.85);
    private static final Pose SEED_AIM = p(.62,-.65,-1.24, .24,.64,-.73, -.42,-.48,-1.44, -.14,-.85);
    private static final Pose GROVE_AIM = p(.67,-.72,-1.19, .33,.51,-.79, -.45,-.54,-1.43, -.22,.42);

    private static final Move BRACE = frost(
            p(.52,-.62,-1.25, -.15,.75,-.65, -.50,-.55,-1.24, .08,.40),
            p(.43,-.55,-1.40, -.29,.70,-.65, -.30,-.43,-1.49, .42,1.16),
            p(.44,-.57,-1.42, -.27,.68,-.68, -.32,-.50,-1.46, .32,1.06), 9);
    private static final Move ZERO = frost(
            p(.41,-.54,-1.30, -.11,.78,-.62, -.20,-.46,-1.27, .44,.26), PLANT, PLANT, 10);
    private static final Move LANCE = frost(
            p(.70,-.69,-.98, .11,.18,-.98, -.52,-.68,-1.24, .23,-.50),
            p(.34,-.61,-1.62, .02,.17,-.98, -.62,-.70,-1.34, -.17,.20),
            p(.42,-.59,-1.39, .07,.29,-.95, -.61,-.73,-1.28, -.04,.14), 9);
    private static final Move EXPANSE = frost(ICE_AIM,
            p(.57,-.70,-1.53, .31,.36,-.87, -.78,-.57,-1.52, .82,-.45),
            p(.77,-.66,-1.28, .51,.50,-.69, -.85,-.61,-1.29, .62,-.15), 11);
    private static final Move BASTION = frost(
            p(.36,-.60,-1.31, -.16,.81,-.57, -.23,-.55,-1.32, .43,.28),
            p(.79,-.47,-1.39, .26,.81,-.53, -.79,-.39,-1.39, -.12,1.57),
            p(.76,-.52,-1.40, .27,.76,-.59, -.74,-.45,-1.35, -.04,1.34), 12);

    private static final Move DEW = herb(
            p(.59,-.63,-1.26, .24,.64,-.73, -.47,-.63,-1.27, -.20,1.21),
            p(.55,-.60,-1.34, .18,.67,-.72, -.23,-.40,-1.64, -.07,1.77),
            p(.55,-.62,-1.31, .21,.63,-.75, -.35,-.43,-1.55, -.03,1.28), 9);
    private static final Move DEW_IN = herb(DEW.ready,
            p(.52,-.63,-1.22, .15,.69,-.70, -.03,-.55,-1.08, -.12,1.58),
            p(.57,-.63,-1.22, .22,.65,-.72, -.31,-.67,-1.04, -.14,1.14), 9);
    private static final Move SEED = herb(
            p(.62,-.75,-1.12, .23,.38,-.89, -.59,-.71,-1.11, -.38,-.37),
            p(.43,-.54,-1.52, .16,.61,-.77, -.38,-.44,-1.50, .19,1.20),
            p(.55,-.58,-1.40, .31,.69,-.65, -.51,-.50,-1.40, .26,1.50), 9);
    private static final Move ARRAY = herb(SEED_AIM,
            p(.70,-.65,-1.33, .37,.64,-.67, -.34,-.67,-1.60, -.18,-1.02),
            p(.68,-.66,-1.31, .32,.62,-.71, -.39,-.68,-1.49, -.15,-.82), 10);
    private static final Move THREAD = herb(
            p(.61,-.64,-1.34, .29,.60,-.74, -.27,-.53,-1.23, .20,.20),
            p(.61,-.62,-1.39, .21,.63,-.75, -.20,-.42,-1.69, .06,.52),
            p(.60,-.62,-1.26, .25,.64,-.73, -.39,-.55,-1.19, -.18,-.70), 10);
    private static final Move THREAD_IN = herb(THREAD.ready,
            p(.57,-.62,-1.23, .23,.66,-.72, -.13,-.54,-1.18, .08,.93),
            p(.60,-.65,-1.23, .28,.61,-.73, -.34,-.66,-1.03, -.13,.21), 10);
    private static final Move GROVE = herb(GROVE_AIM,
            p(.69,-.54,-1.35, .32,.66,-.70, -.34,-.34,-1.57, .17,1.92),
            p(.71,-.53,-1.36, .40,.67,-.63, -.57,-.38,-1.51, .36,1.61), 12);
    private static final Move CANOPY = herb(
            p(.38,-.64,-1.31, -.31,.49,-.82, -.20,-.55,-1.28, -.51,1.03),
            p(.75,-.49,-1.40, .43,.66,-.63, -.72,-.37,-1.48, .50,1.80),
            p(.78,-.55,-1.39, .50,.63,-.60, -.76,-.44,-1.43, .62,1.45), 12);

    private static final Move[] ICE_BASICS = {
        frost(FROST_IDLE, p(.51,-.63,-1.51, .15,.51,-.85, -.67,-1.32,-.91, .30,0),
                p(.54,-.63,-1.38, .20,.57,-.80, -.67,-1.32,-.91, .20,0), 6),
        frost(FROST_IDLE, p(.69,-.61,-1.50, .37,.54,-.76, -.67,-1.32,-.91, -.28,0),
                p(.74,-.63,-1.41, .51,.57,-.63, -.67,-1.32,-.91, -.43,0), 7),
        frost(FROST_IDLE, p(.48,-.48,-1.43, .14,.77,-.62, -.67,-1.32,-.91, .22,0),
                p(.48,-.73,-1.46, .12,.48,-.87, -.67,-1.32,-.91, .46,0), 8)
    };

    private static final Move[] HERB_BASICS = {
        herb(HERB_IDLE, p(.57,-.61,-1.32, .22,.67,-.71, -.29,-.48,-1.45, .01,1.55),
                p(.58,-.63,-1.32, .26,.64,-.72, -.37,-.56,-1.36, -.13,1.34), 7),
        herb(HERB_IDLE, p(.66,-.61,-1.32, .36,.61,-.70, -.49,-.46,-1.50, -.42,.41),
                p(.67,-.62,-1.33, .37,.61,-.70, -.26,-.54,-1.47, -.37,.90), 8),
        herb(HERB_IDLE, p(.54,-.57,-1.28, .19,.74,-.65, -.23,-.54,-1.41, .24,1.45),
                p(.54,-.54,-1.32, .23,.75,-.62, -.30,-.37,-1.48, .37,1.85), 9)
    };

    private static Move frost(Pose ready, Pose beat, Pose follow, double length) {
        return new Move(ready, beat, follow, .95, Math.min(3.3, length * .48), length);
    }

    private static Move herb(Pose ready, Pose beat, Pose follow, double length) {
        return new Move(ready, beat, follow, 1.4, Math.min(5, length * .56), length);
    }

    private static Move volley(int shot) {
        double y = shot == 1 ? -.54 : shot == 2 ? -.68 : -.61;
        Pose beat = p(.53,y,-1.53, .12,.52,-.84, -.42,y+.10,-1.43, .28+shot*.13,.20);
        Pose follow = p(.59,y-.04,-1.37, .21,.58,-.79, -.47,y+.02,-1.32, .18,.04);
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
