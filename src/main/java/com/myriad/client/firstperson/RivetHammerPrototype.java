package com.myriad.client.firstperson;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The impact hammer landmark. Local game ticks, camera-space blocks, right-hand authorship. */
public final class RivetHammerPrototype {
    private record Pose(Vector3f grip, Quaternionf turn) {
        Pose toward(Pose target, double weight) {
            float t = (float)Math.clamp(weight, 0, 1);
            return new Pose(new Vector3f(grip).lerp(target.grip, t), new Quaternionf(turn).slerp(target.turn, t));
        }
    }

    private static final Vector3f SHOULDER = v(.76f, -.91f, -.25f);
    private static final float SCALE = .68f;
    private static final Pose REST = guard(v(.715f,-.46f,-1.10f), v(.18f,.78f,-.60f), v(-.78f,.27f,.57f));
    private static final Pose LEFT_GUARD = guard(v(-.62f,-.60f,-1.34f), v(-.71f,.16f,-.68f), v(-.27f,-.95f,.20f));
    private static final Pose RIGHT_GUARD = guard(v(.55f,-.53f,-1.32f), v(.72f,.24f,-.65f), v(.27f,-.95f,.18f));
    private static final Pose LOW_CENTRE = guard(v(.02f,-.72f,-1.38f), v(.27f,.18f,-.95f), v(-.88f,-.28f,-.20f));
    private static final Pose LOW_RIGHT = guard(v(.64f,-.65f,-1.25f), v(.40f,.18f,-.90f), v(-.84f,.11f,-.36f));
    private static Vector3f v(float x, float y, float z) { return new Vector3f(x,y,z); }

    private static Pose guard(Vector3f grip, Vector3f shaft, Vector3f strikingDirection) {
        var y = new Vector3f(shaft).normalize();
        // The asymmetric steel die is on model -X. Its axis follows the stroke tangent.
        var x = new Vector3f(strikingDirection).negate();
        x.fma(-x.dot(y), y).normalize();
        var basis = new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,new Vector3f(x).cross(y));
        return new Pose(grip,basis.getNormalizedRotation(new Quaternionf()));
    }

    private record Stroke(float slope, float tilt, float from, float to, Vector3f ready, Vector3f control,
                          Vector3f finish, Pose guard, double liftTicks, double sweepTicks) {
        Pose at(double phase) {
            double a = Math.toRadians(slope), b = Math.toRadians(tilt);
            var lateral = v((float)Math.cos(a), (float)Math.sin(a), 0);
            var near = v((float)(-Math.sin(a)*Math.cos(b)), (float)(Math.cos(a)*Math.cos(b)), -(float)Math.sin(b));
            double theta = Math.toRadians(from + (to-from)*phase);
            var shaft = new Vector3f(near).mul((float)Math.cos(theta)).fma((float)Math.sin(theta),lateral);
            var die = new Vector3f(lateral).mul((float)Math.cos(theta)).fma(-(float)Math.sin(theta),near)
                    .mul(Math.signum(to-from));
            // Shoulder drives the wrist around a body-side orbit. The head catches up after the grip.
            float t = (float)(phase + .12*Math.sin(Math.PI*phase));
            float w0 = (1-t)*(1-t), w1 = 2*t*(1-t), w2 = t*t;
            var r0 = new Vector3f(ready).sub(SHOULDER);
            var r1 = new Vector3f(control).sub(SHOULDER);
            var r2 = new Vector3f(finish).sub(SHOULDER);
            float radius = r0.length()*w0 + r1.length()*w1 + r2.length()*w2;
            var grip = r0.normalize().mul(w0).fma(w1,r1.normalize()).fma(w2,r2.normalize()).normalize(radius).add(SHOULDER);
            return RivetHammerPrototype.guard(grip,shaft,die);
        }
    }

    private static final Stroke[] STROKES = {
            new Stroke(9,52,68,-68,v(.57f,-.27f,-1.25f),v(.10f,-.38f,-1.65f),v(-.47f,-.54f,-1.42f),LEFT_GUARD,2.18,4.8),
            new Stroke(-13,49,-66,69,v(-.65f,-.53f,-1.42f),v(.02f,-.24f,-1.72f),v(.48f,-.39f,-1.43f),RIGHT_GUARD,1.95,5.0),
            new Stroke(79,70,64,-48,v(.40f,-.04f,-1.35f),v(.19f,-.15f,-1.78f),v(.25f,-.13f,-1.55f),LOW_RIGHT,2.4,5.8)
    };

    private int cut = -1;
    private double start, lift, sweep, guardEnd, chainEnd;
    private Pose entry = REST;
    private Vector3f entryVelocity = new Vector3f(), spin = new Vector3f();

    public void reset() { cut = -1; }
    public int cut() { return cut; }
    public double sweepStart() { return start + lift; }
    public double sweepEnd() { return sweepStart() + sweep; }
    public double contactOpen() { return sweepStart() + sweep*(cut == 2 ? .45 : .40); }
    public double contactClose() { // The die has already dropped past the target's torso in the last third of a slam.
        return sweepStart() + sweep*(cut == 2 ? .65 : .78); }
    public double guardUntil() { return guardEnd; }
    public double settledAt() { return guardEnd + (cut == 0 ? 17 : 13); }
    public boolean active(double now) { return cut >= 0 && now >= start && now < settledAt(); }

    public void attack(double now, double cadence) { attack(now,cadence,now); }
    void attack(double now, double cadence, double oldWeaponTime) {
        if (!Double.isFinite(now) || cut >= 0 && now == start) return;
        var old = pose(oldWeaponTime-.04);
        entry = pose(oldWeaponTime);
        entryVelocity = new Vector3f(entry.grip).sub(old.grip).mul(25);
        var delta = old.turn.difference(entry.turn,new Quaternionf()).normalize();
        if (delta.w < 0) delta.set(-delta.x,-delta.y,-delta.z,-delta.w);
        spin = v(delta.x,delta.y,delta.z);
        float sine = spin.length();
        if (sine > 1e-6f) spin.mul((float)(50*Math.atan2(sine,delta.w)/sine));
        cut = cut >= 0 && now >= start && now <= chainEnd ? (cut+1)%3 : 0;
        double interval = Double.isFinite(cadence) ? Math.clamp(cadence,1,72) : 20;
        double scale = Math.min(1, interval / 7.0);
        start = now;
        lift = STROKES[cut].liftTicks*scale;
        sweep = STROKES[cut].sweepTicks*scale;
        guardEnd = cut == 2 ? sweepEnd()+9 : Math.max(sweepEnd()+9, start+interval+4);
        chainEnd = start + Math.max(20,interval+12);
    }

    private Pose pose(double now) {
        if (!active(now)) return REST;
        var stroke = STROKES[cut];
        if (now < sweepStart()) {
            double age = now-start, t = age/lift, carry = age*(1-t)*(1-t);
            var turn = new Quaternionf(entry.turn);
            float velocity = spin.length();
            if (velocity > 1e-6f) turn.rotateAxis((float)(velocity*carry),spin.x/velocity,spin.y/velocity,spin.z/velocity);
            var from = new Pose(new Vector3f(entry.grip).fma((float)carry,entryVelocity), turn);
            return from.toward(stroke.at(0), ease(t));
        }
        if (now <= sweepEnd()) {
            double t = (now-sweepStart())/sweep;
            double split = .62;
            return stroke.at(t < split ? t*t/split : 1-(1-t)*(1-t)/(1-split));
        }
        if (now < guardEnd) {
            double age = now-sweepEnd();
            double extra = .085*Math.exp(-.5*age)*Math.sin(.98*age);
            var falling = stroke.at(1+extra);
            falling.grip.y -= (float)(.082*(1-Math.exp(-2*age))*Math.exp(-.33*age));
            return falling.toward(stroke.guard,ease(age/9));
        }
        double back = now-guardEnd;
        if (cut == 0) {
            if (back < 5) return LEFT_GUARD.toward(LOW_CENTRE,ease(back/5));
            if (back < 10) return LOW_CENTRE.toward(LOW_RIGHT,ease((back-5)/5));
            return LOW_RIGHT.toward(REST,ease((back-10)/7));
        }
        if (back < 5) return stroke.guard.toward(LOW_RIGHT,ease(back/5));
        return LOW_RIGHT.toward(REST,ease((back-5)/8));
    }

    private static double ease(double v) { double t=Math.clamp(v,0,1); return t*t*(3-2*t); }
    double microWeight(double t) {
        if (!active(t)) return 1;
        if (t < sweepStart()) return 1-ease((t-start)/lift);
        return ease((t-sweepEnd())/6);
    }
    public Matrix4f item(double tick, boolean left) { return item(tick,left,0); }
    Matrix4f item(double tick, boolean left, double vibration) {
        var pose = pose(tick);
        var grip = new Vector3f(pose.grip);
        var q = new Quaternionf(pose.turn).rotateZ((float)(.027*vibration));
        if (left) {
            grip.x = -grip.x;
            // Reflect the world X and the model's thickness Z. The die on -X stays the striking face.
            q.set(q.x,-q.y,-q.z,q.w).rotateY((float)Math.PI);
        }
        return new Matrix4f().translation(grip).rotate(q).scale(SCALE).translate(.5f,.5f-5.5f/16,.5f);
    }
    public static Vector3f strikingFace(Matrix4f item) {
        return item.transformPosition(v(-10f/16-.5f,23f/16-.5f,-.5f));
    }
}
