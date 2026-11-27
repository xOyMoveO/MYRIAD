package com.myriad.client.firstperson;

import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.network.LuoxiaBowPosePayload.Phase;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Local archer's frame. The server supplies work and actual releases; samples never advance either. */
public final class LuoxiaBowPrototype {
    static final float[] DRAW = {0, .13f, .52f, 1};
    // Pixel distance from the center of the handle to the center of the serving in each model.
    static final float[] SERVING = {6.78f / 16, 9.435219f / 16, 12.446193f / 16, 14.979545f / 16};
    static int span(float draw) {
        return draw <= DRAW[1] ? 0 : draw <= DRAW[2] ? 1 : 2;
    }

    static float fraction(float draw, int i) {
        return Math.clamp((draw - DRAW[i]) / (DRAW[i + 1] - DRAW[i]), 0, 1);
    }

    static float servingX(float draw) {
        int i = span(draw);
        return Math.fma(fraction(draw, i), SERVING[i + 1] - SERVING[i], SERVING[i]);
    }

    private static final float DEG = (float)(Math.PI / 180);
    private static final float BOW_SCALE = .73f;
    private static final float SHELF = .058f;
    private static final float REST_CANT = 56;
    private static final float AIM_DISTANCE = 20;
    private LuoxiaBowPosePayload cue;
    private double changed, lastSeen, fullAt = Double.NaN;
    private float drawFrom, drawTo, cantFrom = REST_CANT, raiseFrom;
    private double raiseAt, cantAt;
    /** The basic draw's world flash shares Dusk Mark's presentation slot; release flashes have detail 0. */
    public static boolean replacesWorldCharge(AbilityVfxPayload p, int ownerId, boolean hasOwnFirstPersonBow) {
        return hasOwnFirstPersonBow && p.owner() == ownerId && p.theme() == 14 && p.variant() == 2
                && p.kind() == com.myriad.character.ability.AbilityVfxGeometry.CHARGE
                && p.angle() == 1 && p.life() == 3 && p.anchor() < 0;
    }

    private static boolean loaded(Phase phase) {
        return phase == Phase.DRAW || phase == Phase.WINDUP || phase == Phase.LOCK || phase == Phase.RELOAD;
    }
    public void reset() {
        cue = null; fullAt = Double.NaN; drawFrom = drawTo = raiseFrom = 0;
        cantFrom = REST_CANT;
    }

    public void accept(LuoxiaBowPosePayload next, double now) {
        if (next.phase() == Phase.RESET) { reset(); return; }
        if (next.phase() == Phase.STEP || next.phase() == Phase.CHAIN) return;
        boolean same = cue != null && cue.token() == next.token() && (cue.skill() <= 1) == (next.skill() <= 1);
        if (next.phase() == Phase.EARLY && (!same || cue.phase() == Phase.CANCEL)) return;
        if (same && next.tick() < cue.tick() && !(cue.skill() == -1 && cue.work() == 0 && cue.phase() == Phase.DRAW)) return;
        if (same && next.phase() == Phase.RELEASE && cue.phase() == Phase.RELEASE
                && cue.work() == next.work()) return;
        boolean wasDrawing = cue != null && live(now) && loaded(cue.phase());
        boolean drawing = loaded(next.phase());
        float previousDraw = draw(now);
        if (!same || wasDrawing != drawing) {
            raiseFrom = raise(now);
            raiseAt = now;
        }
        if (!same || cue.skill() != next.skill() || wasDrawing != drawing) {
            cantFrom = cant(now);
            cantAt = now;
        }
        float progress = drawing && next.maximum() > 0 ? Math.clamp(next.work()/next.maximum(),0,1) : 0;
        // Seat the arrow, then load the limbs quickly at first and more slowly towards full draw.
        float goal = drawing ? .13f + .87f * (1 - (1-progress)*(1-progress)) : 0;
        if (!same || !drawing || goal != drawTo || !wasDrawing) {
            drawFrom = previousDraw;
            drawTo = goal;
            changed = now;
        }
        if (progress < 1 || !drawing) fullAt = Double.NaN;
        else if (!same || !wasDrawing || !Double.isFinite(fullAt)) fullAt = now + 1.5;
        cue = next; lastSeen = now;
    }

    private boolean live(double now) { return cue != null && now >= changed && now - lastSeen < 18; }
    private static float ease(double u) { float t = (float)Math.clamp(u,0,1); return t*t*(3-2*t); }
    private static float mix(float a, float b, float t) { return a + (b-a)*t; }
    private float draw(double now) {
        if (!live(now)) return 0;
        double time = cue.phase() == Phase.RELEASE ? 1.05 : loaded(cue.phase()) ? 1.5 : 2.8;
        return mix(drawFrom,drawTo,ease((now-changed)/time));
    }
    private float raise(double now) {
        if (!live(now)) return 0;
        if (loaded(cue.phase())) return mix(raiseFrom,1,ease((now-raiseAt)/3.2));
        return mix(raiseFrom,0,ease((now-raiseAt)/(cue.phase()==Phase.RELEASE ? 6.4 : 3.5)));
    }
    private static float aimCant(int skill) {
        return switch (skill) {
            case 0 -> 38; case 1 -> -24; case 2 -> 49; case 4 -> 33;
            case 5 -> -32; case 6 -> 45; case 7 -> -18; default -> 27;
        };
    }
    private float cant(double now) {
        if (!live(now)) return REST_CANT;
        boolean drawing = loaded(cue.phase());
        return mix(cantFrom, drawing ? aimCant(cue.skill()) : REST_CANT,
                ease((now-cantAt)/(drawing ? 3.2 : cue.phase()==Phase.RELEASE ? 6.4 : 3.5)));
    }
    private static String mesh(int skill, boolean full) {
        return switch (skill) {
            case 0 -> "quick"; case 1 -> full ? "full_draw" : "basic"; case 2 -> "dusk_mark";
            case 4 -> "feather_burst"; case 5 -> "last_sun"; case 6 -> "thousand_feathers";
            case 7 -> "sunset_line"; default -> "basic";
        };
    }

    /** bow and arrow matrices transform asset-local blocks directly into camera space. */
    public record Frame(Matrix4f bow, Vector3f target, float draw, float stringOffset, float serving,
                        int arrows, String mesh, float roll, double releaseAge, boolean left, float readyPulse) {
        public boolean loaded() { return arrows > 0; }
        /** Ferrule on the upper string, just above the arrow nock. Use its live serving and the full-draw limb tip. */
        public Vector3f readyAnchor() {
            return bow.transformPosition(new Vector3f(mix(serving, 8.38f/16, .056f), 11.96f/16*.056f, 0));
        }
        public Matrix4f arrow(int index) {
            var nock = bow.transformPosition(new Vector3f(serving,0,0));
            var backward = new Vector3f(nock).sub(target).normalize();
            var up = bow.transformDirection(new Vector3f(0,1,0)).normalize();
            var q = basis(backward,up);
            float fan = arrows == 3 ? (index-1)*8*DEG*(left?-1:1) : 0;
            return new Matrix4f().translation(nock).rotate(q).rotateY(fan)
                    .scale(1.69f*BOW_SCALE,.61f*BOW_SCALE,.61f*BOW_SCALE);
        }
    }

    public Frame sample(double now, boolean left, float viewYaw, float viewPitch, boolean stepping) {
        return sample(now,left,viewYaw,viewPitch,stepping,0,0,0,0);
    }

    static Quaternionf basis(Vector3f xAxis, Vector3f up) {
        var x = new Vector3f(xAxis).normalize();
        var z = new Vector3f(x).cross(up);
        if (z.lengthSquared()<1e-6f) z.set(x).cross(0,0,1);
        z.normalize();
        var y = new Vector3f(z).cross(x);
        return new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z).getNormalizedRotation(new Quaternionf());
    }

    private static Vector3f aimRay(float yaw, float pitch, float viewYaw, float viewPitch) {
        double d = Math.toRadians(yaw-viewYaw), p = Math.toRadians(pitch), v = Math.toRadians(viewPitch);
        return new Vector3f((float)(Math.sin(d)*Math.cos(p)),
                (float)(Math.sin(v)*Math.cos(p)*Math.cos(d)-Math.cos(v)*Math.sin(p)),
                (float)(-Math.cos(v)*Math.cos(p)*Math.cos(d)-Math.sin(v)*Math.sin(p)));
    }

    Frame sample(double now, boolean left, float viewYaw, float viewPitch, boolean stepping,
                 double step, double walking, double yawLag, double pitchLag) {
        boolean live = live(now), drawing = live && loaded(cue.phase());
        int skill = live ? cue.skill() : -1;
        float draw = draw(now), raise = raise(now), roll = cant(now);
        double releaseAge = live && cue.phase()==Phase.RELEASE && now-changed < 6.4 ? now-changed : -1;
        boolean full = drawing && Double.isFinite(fullAt) && now >= fullAt;
        double fullAge = full ? now-fullAt : 0;
        float tension = full ? (float)(.0036*Math.sin(2.7*fullAge)*ease(fullAge/1.2)) : 0;
        double latch = full ? Math.sin(2.8*fullAge)*Math.exp(-1.6*fullAge) : 0;
        double kick = skill == 1 || skill == 5 || skill == 7 ? 1.12 : skill == 0 ? .66 : .87;
        double pulse = releaseAge >= 0 ? kick*Math.sin(1.31*releaseAge)*Math.exp(-.78*releaseAge) : 0;
        double stringTime = Math.max(0,releaseAge-1.05);
        float freeString = releaseAge >= 0 ? (float)(-.115*kick*Math.sin(3.8*stringTime)*Math.exp(-1.18*stringTime)) : 0;
        float offset = freeString + tension + (float)(latch*.009);
        float serving = servingX(draw)+offset;
        float quiet = 1-.94f*raise;
        double breath = Math.sin(now*.082);
        float x = mix(.68f,.365f,raise), y = mix(-.475f,-.225f,raise), z = mix(-1.39f,-1.33f,raise);
        x += (.024*walking*Math.sin(step)+.0014*(left?-yawLag:yawLag))*quiet;
        y += (.008*breath+.018*walking*Math.sin(2*step)-.0008*pitchLag)*quiet;
        y -= (float)(.032*pulse + (stepping ? .045 : 0));
        z -= (float)(.115*pulse + .012*latch);
        var grip = new Vector3f(x,y,z);
        boolean locked = live && (cue.phase()==Phase.LOCK || skill==7 && releaseAge>=0);
        var target = locked ? aimRay(cue.yaw(),cue.pitch(),viewYaw,viewPitch).mul(AIM_DISTANCE)
                : new Vector3f(0,0,-AIM_DISTANCE);
        var rightTarget = new Vector3f(target);
        if (left) rightTarget.x *= -1;
        // Idle shows the feather inlays broadside. On draw the shaft runs from the serving
        // over the signed shelf and converges on the same distant aim point for either hand.
        var forward = new Vector3f(rightTarget).sub(grip).normalize();
        var restDirection = new Vector3f(-.58f,-.03f,-.815f).normalize();
        var backward = restDirection.lerp(forward,raise).negate().normalize();
        var up = new Vector3f(-(float)Math.sin(roll*DEG),(float)Math.cos(roll*DEG),0);
        var turn = basis(backward,up).rotateY(-(float)Math.atan2(SHELF,serving)*raise);
        turn.rotateZ((float)(-3.8*DEG*pulse));
        if (left) {
            grip.x *= -1;
            // Reflect camera X and the thin local Z axis, retaining the serving and the limb tips.
            turn.set(turn.x,-turn.y,-turn.z,turn.w).rotateY((float)Math.PI);
        }
        var bow = new Matrix4f().translation(grip).rotate(turn).scale(BOW_SCALE);
        // A filled basic-draw work bar is ready. Heartbeats at maximum work retain the first latch time.
        float readyPulse = -1;
        if (full && cue.phase() == Phase.DRAW && skill <= 1) {
            readyPulse = fullAge >= 4 ? 0 : (float)Math.pow(Math.sin(Math.PI*fullAge/4), 2);
        }
        return new Frame(bow,target,draw,offset,serving,drawing ? skill==6 ? 3 : 1 : 0,
                mesh(skill,full),roll,releaseAge,left,readyPulse);
    }
}
