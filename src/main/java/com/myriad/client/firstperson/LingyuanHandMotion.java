package com.myriad.client.firstperson;

import net.minecraft.world.phys.Vec3;

import static com.myriad.character.skills.lingyuan.rework.LingyuanHandCues.*;

/** Authored local poses; only server cues choose a move or advance a held phase. */
final class LingyuanHandMotion {
    record Pose(Vec3 grip, Vec3 axis, Vec3 palm, double palmRoll, double point) {
        Pose mix(Pose to, double t) {
            return new Pose(grip.lerp(to.grip, t), axis.lerp(to.axis, t), palm.lerp(to.palm, t),
                    palmRoll + (to.palmRoll - palmRoll) * t, point + (to.point - point) * t);
        }
    }
    private record Move(Pose ready, Pose beat, Pose follow, double peak, double drift, double length) {}
    private record Clip(int skill, int phase, double start, double until, Pose from,
                        double sampledAt, double previous, double progress, Move move) {}
    private static final Vec3 HIDDEN = new Vec3(-.69, -1.34, -.91);
    static final Pose IDLE = sword(.513,-.478,-.953,.53,.83,-.16);
    private static final Pose THRUST = p(.40,-.56,-1.66,.06,.19,-.98, -.78,-.80,-.99,-.3,0);
    private static final Pose DASH_READY = sword(.68,-.65,-1.04,.16,.25,-.95);
    private static final Pose DASH_DOWN = sword(.54,-.70,-1.43,.21,.07,-.97);
    private static final Pose MANTLE_IN = p(.62,-.68,-1.25,-.59,.16,-.79, -.53,-.65,-1.26,.21,.85);
    private static final Pose MANTLE_OUT = p(.54,-.60,-1.35,-.53,.26,-.81, -.18,-.46,-1.51,1.0,1);
    private static final Pose MANTLE_HOOK = p(.65,-.57,-1.33,-.36,.43,-.83, -.54,-.49,-1.32,1.4,.8);
    private static final Pose ARRAY_AIM = p(.61,-.66,-1.09,.31,.33,-.89, -.39,-.48,-1.36,.40,1);
    private static final Pose ARRAY_PRESS = p(.60,-.77,-1.34,.20,.20,-.95, -.28,-.72,-1.63,-.43,1);
    private static final Pose RAIN_AIM = p(.63,-.61,-1.08,.42,.53,-.73, -.48,-.53,-1.25,1.6,0);
    private static final Pose RAIN_RISE = p(.71,-.55,-1.26,.44,.68,-.58, -.37,-.36,-1.48,1.88,.6);
    private static final Pose RAIN_PRESS = p(.70,-.66,-1.31,.43,.48,-.77, -.34,-.60,-1.60,-.68,1);
    private static final Pose HEAVEN_UP = p(.65,-.36,-1.10,.26,.93,-.25, -.54,-.38,-1.34,.58,1);
    private static final Pose HEAVEN_DOWN = p(.40,-.70,-1.51,.15,-.50,-.86, -.49,-.71,-1.42,-.64,.45);
    private static final Pose HEAVEN_SETTLE = sword(.52,-.75,-1.40,.19,-.35,-.92);

    private static final Move SHEATHE_GUARD = new Move(MANTLE_IN,MANTLE_OUT,MANTLE_HOOK,2,5,10);
    private static final Move STAMP = new Move(ARRAY_AIM,ARRAY_PRESS,ARRAY_PRESS,1.55,4,9);
    private static final Move RAIN = new Move(RAIN_RISE,RAIN_PRESS,RAIN_PRESS,3,5,11);
    private static final Move DASH_END = new Move(DASH_DOWN,DASH_DOWN,DASH_DOWN,1.3,2.4,6);
    private static final Move LAND = new Move(HEAVEN_DOWN,HEAVEN_SETTLE,HEAVEN_SETTLE,1.2,3.4,9);

    // Motion follows the blade's edge, with the tip/guard staying below the reticle at mid-swing.
    private static final Pose CUT_RIGHT = sword(.80,-.46,-1.23,.58,.63,-.62);
    private static final Pose CUT_LEFT = sword(-.26,-.69,-1.38,-.49,.18,-.85);
    private static final Pose CUT_UP = sword(.66,-.45,-1.34,.48,.65,-.59);
    private static final Pose CUT_OUT = sword(-.41,-.56,-1.43,-.66,.06,-.74);
    private static final Move[] BASIC_MOVES = {
        new Move(CUT_RIGHT,sword(.17,-.63,-1.44,-.34,.27,-.90),CUT_LEFT,2.3,3.7,8),
        new Move(CUT_LEFT,sword(.24,-.60,-1.45,.27,.30,-.91),CUT_UP,2.2,3.7,8),
        new Move(sword(.78,-.59,-1.27,.61,.17,-.77),sword(.12,-.62,-1.48,-.20,.12,-.97),
                CUT_OUT,2.35,4.1,9)
    };

    private Clip clip;
    private int combo = -1;
    private double lastBasic = Double.NEGATIVE_INFINITY;

    private static Pose p(double x, double y, double z, double ax, double ay, double az,
                          double lx, double ly, double lz, double palmRoll, double point) {
        return new Pose(new Vec3(x,y,z), new Vec3(ax,ay,az), new Vec3(lx,ly,lz), palmRoll, point);
    }
    private static Pose sword(double x,double y,double z,double ax,double ay,double az) {
        return new Pose(new Vec3(x,y,z), new Vec3(ax,ay,az), HIDDEN, 0, 0);
    }

    void reset() { clip = null; combo = -1; lastBasic = Double.NEGATIVE_INFINITY; }
    int combo() { return combo; }
    private static boolean held(int phase) { return phase == AIM || phase == DASH || phase == GATHER || phase == CHOP; }

    void abort(double now) {
        if (active(now) && clip.phase != STOP)
            clip = new Clip(clip.skill, STOP, now, now+3.5, pose(now), now, 0, 0, null);
    }

    void cue(int skill, int phase, double now, int life, double value) {
        if (phase == STOP) {
            if (active(now) && clip.skill == skill && held(clip.phase)) abort(now);
            return;
        }
        if (phase == FINISH && (!active(now) || clip.phase != DASH)) return;
        if (phase == BASIC) {
            combo = now - lastBasic > Math.max(22, value * 1.6) ? 0 : (combo+1)%3;
            lastBasic = now;
        }
        Pose from = pose(now);
        if (held(phase)) {
            boolean same = active(now) && clip.phase == phase && clip.skill == skill;
            clip = new Clip(skill,phase,same?clip.start:now,now+life,same?clip.from:from,
                    now,same?progress(now):0,Math.clamp(value,0,1),null);
            return;
        }
        Move move = phase == BASIC ? BASIC_MOVES[combo] : phase == FINISH ? DASH_END : phase == IMPACT ? LAND :
                switch (skill) { case 3 -> SHEATHE_GUARD; case 4 -> volley((int)value); case 5 -> STAMP;
                    case 6 -> RAIN; default -> null; };
        if (move != null) clip = new Clip(skill,phase,now,now+move.length,from,now,0,0,move);
    }

    private static Move volley(int shot) {
        double y = shot == 1 ? -.42 : shot == 2 ? -.60 : -.50;
        var ready = p(.64,-.60,-1.23,.31,.66,-.72, -.53,-.61,-1.25,.32,1);
        var flick = p(.62,-.58,-1.29,.29,.64,-.74, -.25,y,-1.61,shot==1?.93:shot==2?-.44:.35,1);
        var follow = p(.64,-.61,-1.27,.30,.62,-.75, -.39,y-.07,-1.39,.33,.88);
        return new Move(ready,flick,follow,1,2.45,shot==2?7:5.5);
    }

    private boolean active(double now) { return clip != null && now < clip.until; }

    private double progress(double now) {
        double span = clip.phase == CHOP ? 1 : 2;
        return clip.previous + (clip.progress-clip.previous)*Math.clamp((now-clip.sampledAt)/span,0,1);
    }

    Pose pose(double now) {
        if (!active(now)) return IDLE;
        double age = Math.max(0,now-clip.start);
        if (clip.phase == STOP) return ease(clip.from,IDLE,age/3.5);
        if (held(clip.phase)) {
            double phase = progress(now);
            Pose target = switch (clip.phase) {
                case AIM -> clip.skill == 5 ? ARRAY_AIM : RAIN_AIM;
                case DASH -> ease(DASH_READY,THRUST,age/1.5);
                case GATHER -> ease(IDLE,HEAVEN_UP,.22+.78*phase);
                case CHOP -> HEAVEN_UP.mix(HEAVEN_DOWN,phase*phase);
                default -> IDLE;
            };
            return ease(ease(clip.from,target,age/(clip.phase==CHOP?1:2.1)),IDLE,(now-clip.until+1.4)/1.4);
        }
        Move m = clip.move;
        if (age <= m.peak) {
            double t = age/m.peak;
            return clip.from.mix(m.ready,t).mix(m.ready.mix(m.beat,t),t);
        }
        if (age < m.drift) return ease(m.beat,m.follow,(age-m.peak)/(m.drift-m.peak));
        return ease(m.follow,IDLE,(age-m.drift)/(m.length-m.drift));
    }

    double edgeWeight(double now) {
        if (!active(now)) return 0;
        double age = Math.max(0,now-clip.start);
        if (clip.phase == CHOP) return Math.clamp(progress(now)*5,0,1);
        if (clip.phase != BASIC) return 0;
        return Math.clamp(age/.75,0,1)*Math.clamp((clip.move.drift+.5-age)/.85,0,1);
    }

    double edgeSample(double now) {
        if (edgeWeight(now)==0 || clip.phase!=BASIC) return now;
        return Math.clamp(now,clip.start+.85,clip.start+clip.move.drift-.14);
    }

    private static Pose ease(Pose from,Pose to,double fraction) {
        double t=Math.clamp(fraction,0,1);
        return from.mix(to,t*t*(3-2*t));
    }
}
