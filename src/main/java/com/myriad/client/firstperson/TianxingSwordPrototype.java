package com.myriad.client.firstperson;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** First landmark prototype. All authored poses are right-handed and expressed in camera space. */
public final class TianxingSwordPrototype {
    private record Pose(Vector3f grip, Quaternionf rotation) {
        Pose toward(Pose other, double amount) {
            float t = (float) Math.clamp(amount, 0, 1);
            var blend = new Quaternionf(rotation).slerp(other.rotation, t);
            // Blending roll must never lift a low recovery path across the crosshair.
            // Interpolate the shaft explicitly, then retain the twist of the quaternion blend.
            var axis = rotation.transformPositiveY(new Vector3f())
                    .lerp(other.rotation.transformPositiveY(new Vector3f()), t).normalize();
            var adjust = new Quaternionf().rotationTo(blend.transformPositiveY(new Vector3f()), axis);
            return new Pose(new Vector3f(grip).lerp(other.grip, t), adjust.mul(blend));
        }
    }

    private static final Vector3f SHOULDER = new Vector3f(.66f, -.87f, -.22f);
    private static final Vector3f FORWARD = new Vector3f(0, 0, -1);
    private static final float SCALE = .90f;
    private static Vector3f v(float x, float y, float z) { return new Vector3f(x, y, z); }

    private static Pose guard(Vector3f grip, Vector3f shaft, Vector3f edge) {
        return new Pose(grip, orientation(shaft, edge));
    }

    private static final Pose REST = guard(v(.80f,-.49f,-1.11f), v(-.24f,.665f,-.708f), v(.95f,.18f,-.14f));
    private static final Pose LEFT_GUARD = guard(v(-.69f,-.67f,-1.42f), v(-.78f,.05f,-.62f), v(-.24f,-.95f,.16f));
    private static final Pose RIGHT_GUARD = guard(v(.51f,-.53f,-1.31f), v(.83f,-.23f,-.51f), v(.18f,-.97f,.14f));
    private static final Pose LOW_LEFT = guard(v(-.52f,-.69f,-1.43f), v(-.77f,-.17f,-.61f), v(-.25f,.95f,.10f));
    private static final Pose LOW_CENTRE = guard(v(-.06f,-.81f,-1.36f), v(.28f,.21f,-.94f), v(.88f,.30f,.22f));
    private static final Pose LOW_RIGHT = guard(v(.68f,-.69f,-1.35f), v(.57f,.16f,-.81f), v(.86f,.28f,.41f));

    // Projected screen slope and tilt into depth specify this weapon's cutting plane.
    // The three grip anchors define a curved shoulder orbit independent of the wrist's turn.
    private record Cut(float slopeDegrees, float depthDegrees, float fromDegrees, float toDegrees,
                       Vector3f initial, Vector3f control, Vector3f finish) {
        Pose pose(double fraction) {
            float t = (float)fraction;
            double slope = Math.toRadians(slopeDegrees), depth = Math.toRadians(depthDegrees);
            var across = v((float)Math.cos(slope), (float)Math.sin(slope), 0);
            var up = v(-across.y, across.x, 0).mul((float)Math.cos(depth)).fma((float)Math.sin(depth), FORWARD);
            float theta = (float)Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * t);
            var shaft = new Vector3f(up).mul((float)Math.cos(theta)).fma((float)Math.sin(theta), across);
            var tangent = new Vector3f(across).mul((float)Math.cos(theta)).fma(-(float)Math.sin(theta), up)
                    .mul(Math.signum(toDegrees - fromDegrees));
            float a = (1-t)*(1-t), b = 2*t*(1-t), c = t*t;
            var r0 = new Vector3f(initial).sub(SHOULDER);
            var r1 = new Vector3f(control).sub(SHOULDER);
            var r2 = new Vector3f(finish).sub(SHOULDER);
            float radius = a*r0.length() + b*r1.length() + c*r2.length();
            var grip = r0.normalize().mul(a).fma(b, r1.normalize()).fma(c, r2.normalize())
                    .normalize(radius).add(SHOULDER);
            return new Pose(grip, orientation(shaft, tangent));
        }
    }

    private static final Cut[] CUTS = {
            new Cut(31, 65, 64, -68,
                    v(.54f,-.38f,-1.26f), v(.14f,-.32f,-1.64f), v(-.53f,-.62f,-1.48f)),
            new Cut(-34, 51, -66, 66,
                    v(-.82f,-.76f,-1.48f), v(.02f,-.31f,-1.64f), v(.46f,-.49f,-1.38f)),
            new Cut(12, 41, 63, -60,
                    v(.62f,-.40f,-1.30f), v(.06f,-1.15f,-1.82f), v(-.40f,-.70f,-1.35f))
    };
    private static Quaternionf orientation(Vector3f shaft, Vector3f edge) {
        var y = new Vector3f(shaft).normalize();
        var x = new Vector3f(edge).fma(-edge.dot(y), y).normalize();
        var z = new Vector3f(x).cross(y);
        return new Matrix3f().setColumn(0, x).setColumn(1, y).setColumn(2, z)
                .getNormalizedRotation(new Quaternionf());
    }

    private int cut = -1;
    private double equippedAt = Double.NEGATIVE_INFINITY;
    private float initialEquipDrop;

    // On the first visible frame of a real equip, inherit vanilla's height once. Its later
    // samples also include item-swap recovery, which can outlast the entire authored cut.
    public void equip(double now, float vanillaDrop) {
        equippedAt = now;
        initialEquipDrop = Float.isFinite(vanillaDrop) ? Math.clamp(vanillaDrop, 0, 1) : 0;
    }
    private double start, windup, swing, chainUntil, guardUntil;
    private Pose entry = REST;
    private Vector3f entryVelocity = new Vector3f();
    private Vector3f angularVelocity = new Vector3f();

    public int cut() { return cut; }
    public double sweepStart() { return start + windup; }
    public double sweepEnd() { return sweepStart() + swing; }
    public double returnStart() { return guardUntil; }
    public double settledAt() { return guardUntil + (cut == 1 ? 12 : 17); }
    public void reset() { cut = -1; initialEquipDrop = 0; }
    boolean active(double now) { return cut >= 0 && now >= start && now < settledAt(); }

    public void attack(double now, double cooldownTicks) {
        if (!Double.isFinite(now) || cut >= 0 && start == now) return;
        initialEquipDrop = 0;
        boolean chained = cut >= 0 && now >= start && now <= chainUntil;
        double delta = .04;
        var prior = sample(now - delta);
        var current = sample(now);
        entryVelocity = new Vector3f(current.grip).sub(prior.grip).div((float) delta);
        var turn = prior.rotation.difference(current.rotation, new Quaternionf()).normalize();
        if (turn.w < 0) turn.set(-turn.x, -turn.y, -turn.z, -turn.w);
        angularVelocity = new Vector3f(turn.x, turn.y, turn.z);
        float sine = angularVelocity.length();
        if (sine > 1e-6f) angularVelocity.mul((float) (2 * Math.atan2(sine, turn.w) / (sine * delta)));
        entry = current;
        cut = chained ? (cut + 1) % CUTS.length : 0;
        start = now;
        double cadence = Double.isFinite(cooldownTicks) ? Math.clamp(cooldownTicks, 1, 72) : 12;
        double compression = Math.min(1, cadence / 4.0);
        windup = (cut == 1 ? 2.1 : 1.7) * compression;
        swing = (cut == 2 ? 5.2 : 4.4) * compression;
        // The opposite guard survives the next legal press at the real attack cadence.
        guardUntil = cut == 2 ? sweepEnd() + 5.5 : Math.max(sweepEnd() + 7, start + cadence + 4);
        chainUntil = now + Math.max(16, cadence + 12);
    }

    private Pose sample(double now) {
        if (!active(now)) return REST;
        double age = now - start;
        var stroke = CUTS[cut];
        if (age < windup) {
            double t = age / windup;
            // Carry the incoming linear/angular velocity. Both residuals vanish at the sweep entry.
            double carry = age * (1 - t) * (1 - t);
            double w = angularVelocity.length();
            var turn = new Quaternionf(entry.rotation);
            if (w > 1e-6) turn.rotateAxis((float)(w * carry),
                    angularVelocity.x / (float)w, angularVelocity.y / (float)w, angularVelocity.z / (float)w);
            var moving = new Pose(new Vector3f(entry.grip).fma((float)carry, entryVelocity), turn);
            return moving.toward(stroke.pose(0), t * t * (3 - 2 * t));
        }
        if (now <= sweepEnd()) {
            double u = (now - sweepStart()) / swing;
            double split = .58;
            double phase = u < split ? u*u / split : 1 - (1-u)*(1-u) / (1-split);
            return stroke.pose(phase);
        }
        double release = now - sweepEnd();
        var sideGuard = cut == 0 ? LEFT_GUARD : cut == 1 ? RIGHT_GUARD : LOW_LEFT;
        if (now < guardUntil) {
            double duration = cut == 2 ? 5.5 : 7;
            double overshoot = .033 * Math.exp(-.56 * release) * Math.sin(1.1 * release);
            return stroke.pose(1 + overshoot).toward(sideGuard, ease(release / duration));
        }

        // When the player stops linking attacks, bring the grip along the bottom first.
        // In particular, leave the third stroke below the target until it reaches the right side.
        double elapsed = now - guardUntil;
        if (cut == 1) {
            if (elapsed < 4) return RIGHT_GUARD.toward(LOW_RIGHT, ease(elapsed / 4));
            return LOW_RIGHT.toward(REST, ease((elapsed - 4) / 8));
        }
        if (elapsed < 5) return sideGuard.toward(LOW_CENTRE, ease(elapsed / 5));
        if (elapsed < 10) return LOW_CENTRE.toward(LOW_RIGHT, ease((elapsed - 5) / 5));
        return LOW_RIGHT.toward(REST, ease((elapsed - 10) / 7));
    }

    private static double ease(double u) {
        double t = Math.clamp(u, 0, 1);
        return t*t*(3 - 2*t);
    }

    /** Model (8,8,8) is the actual grip and becomes the origin under display context NONE. */
    public Matrix4f sword(double now, boolean left) {
        Pose pose = sample(now);
        var p = new Vector3f(pose.grip);
        var q = new Quaternionf(pose.rotation);
        if (left) {
            p.x = -p.x;
            q.set(q.x, -q.y, -q.z, q.w);
        }
        // Vanilla approaches the equipped height by at most .4 per game tick.
        double drop = Math.max(0, initialEquipDrop - .4 * Math.max(0, now - equippedAt));
        p.y -= .48f * (float)drop;
        return new Matrix4f().translation(p).rotate(q).scale(SCALE);
    }

    /** Walk, breathing and turn lag fade to zero over the authored cut. */
    double microWeight(double now) {
        if (!active(now)) return 1;
        if (now < sweepStart()) return Math.pow(1 - (now - start) / windup, 2);
        return ease((now - sweepEnd()) / 5.5);
    }
}
