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
            return new Pose(new Vector3f(grip).lerp(other.grip, t),
                    new Quaternionf(rotation).slerp(other.rotation, t));
        }
    }

    private static final Vector3f SHOULDER = new Vector3f(.66f, -.87f, -.22f);
    private static final Vector3f CENTRE = new Vector3f(.13f, -.27f, -1.02f);
    private static final Vector3f FORWARD = new Vector3f(0, 0, -1);
    private static final Pose REST = new Pose(new Vector3f(.64f, -.50f, -.94f),
            orientation(new Vector3f(-.30f, .73f, -.61f), new Vector3f(.95f, .18f, -.14f)));
    private static final float SCALE = .90f;

    private record Cut(float slope, float fromDegrees, float toDegrees) {
        Pose pose(double fraction) {
            // The projected radial basis defines both the stroke plane and its cutting tangent.
            var lateral = new Vector3f(1, slope, 0).normalize();
            var normal = new Vector3f(FORWARD).cross(lateral);
            float theta = (float) Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * fraction);
            var arm = new Vector3f(CENTRE).sub(SHOULDER).rotateAxis(theta * .68f, normal.x, normal.y, normal.z);
            var grip = arm.add(SHOULDER);
            var shaft = new Vector3f(FORWARD).rotateAxis(theta, normal.x, normal.y, normal.z);
            var edge = new Vector3f(lateral).rotateAxis(theta, normal.x, normal.y, normal.z)
                    .mul(Math.signum(toDegrees - fromDegrees));
            return new Pose(grip, orientation(shaft, edge));
        }
    }

    private static final Cut[] CUTS = {
            new Cut(.63f, 67, -74), new Cut(-.63f, -66, 74), new Cut(0, 72, -80)
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
    private double start, windup, swing, chainUntil;
    private Pose entry = REST;
    private Vector3f entryVelocity = new Vector3f();
    private Vector3f angularVelocity = new Vector3f();

    public int cut() { return cut; }
    public double sweepStart() { return start + windup; }
    public double sweepEnd() { return sweepStart() + swing; }
    public void reset() { cut = -1; initialEquipDrop = 0; }
    boolean active(double now) { return cut >= 0 && now >= start && now < sweepEnd() + 9; }

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
        windup = (chained ? .85 : 1.22) * compression;
        swing = 2.85 * compression;
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
            double split = .68;
            double phase = u < split ? u*u / split : 1 - (1-u)*(1-u) / (1-split);
            return stroke.pose(phase);
        }
        double release = now - sweepEnd();
        // A short underdamped overshoot at the wrist, followed by a critically damped return.
        double extra = .050 * Math.exp(-1.05 * release) * Math.sin(2.1 * release);
        double d = release * 1.07;
        return stroke.pose(1 + extra).toward(REST, 1 - (1 + d) * Math.exp(-d));
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
        return Math.clamp((now - sweepEnd()) / 4, 0, 1);
    }
}
