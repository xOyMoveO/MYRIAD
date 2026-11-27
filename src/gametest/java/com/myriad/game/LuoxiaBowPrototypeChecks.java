package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.client.firstperson.LuoxiaBowPrototype;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.network.LuoxiaBowPosePayload.Phase;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Test anchor positions on the same authored bow models that supply the four first-person shapes. */
final class LuoxiaBowPrototypeChecks {
    private static final float[] DRAWS = {0, .13f, .52f, 1};
    private static final UUID ARCHER = new UUID(1, 2);
    private static final java.util.Set<String> ANCHORS = java.util.Set.of("handle", "serving", "upper_nock", "lower_nock");
    private record Shape(Vector3f grip, Vector3f serving, Vector3f upper, Vector3f lower) { }

    private static Shape readShape(int stage) {
        String model = stage == 0 ? "guixia_bow" : "guixia_bow_pulling_" + (stage - 1);
        String path = "/assets/myriad/models/item/" + model + ".json";
        try (var in = LuoxiaBowPrototypeChecks.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing bow model " + path);
            var elements = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("elements");
            var anchors = new java.util.HashMap<String, Vector3f>();
            for (var value : elements) {
                var e = value.getAsJsonObject();
                var name = e.get("name").getAsString();
                if (!ANCHORS.contains(name)) continue;
                var a = e.getAsJsonArray("from");
                var b = e.getAsJsonArray("to");
                var centre = new Vector3f();
                for (int i = 0; i < 3; i++)
                    centre.setComponent(i, (a.get(i).getAsFloat() + b.get(i).getAsFloat()) / 32f - .5f);
                anchors.put(name, centre);
            }
            if (anchors.size() != 4) throw new IllegalStateException("Incomplete bow anchors in " + model);
            return new Shape(anchors.get("handle"), anchors.get("serving"), anchors.get("upper_nock"), anchors.get("lower_nock"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read bow model " + model, e);
        }
    }

    private static Vector3f serving(Shape[] shapes, float draw, float vibration) {
        int end = 1;
        while (end < 3 && draw > DRAWS[end]) end++;
        float t = (draw - DRAWS[end - 1]) / (DRAWS[end] - DRAWS[end - 1]);
        return new Vector3f(shapes[end - 1].serving).lerp(shapes[end].serving, t).add(vibration, 0, 0);
    }

    private static LuoxiaBowPosePayload cue(int token, int skill, Phase phase, float work, int tick) {
        return new LuoxiaBowPosePayload(ARCHER, token, skill, phase, work, 11, 11, 24, -9, tick);
    }

    private static AbilityVfxPayload effect(int anchor, int theme, int variant, int kind, int angle, int life) {
        return new AbilityVfxPayload(42, anchor, theme, variant, kind, Vec3.ZERO, new Vec3(0, 0, 1),
                .22f, angle, life, 100, false);
    }

    private static void verifyWorldChargeScope(GameTestHelper h) {
        int charge = AbilityVfxGeometry.CHARGE;
        var hold = effect(-1, 14, 2, charge, 1, 3);
        h.assertTrue(LuoxiaBowPrototype.replacesWorldCharge(hold, 42, true),
                "The owner's held full-draw bow supplies the matching first-person charge cue");
        h.assertTrue(!LuoxiaBowPrototype.replacesWorldCharge(hold, 42, false)
                        && !LuoxiaBowPrototype.replacesWorldCharge(hold, 43, true),
                "Third-person and other archers' full-draw cues stay in the world");
        for (var cue : java.util.List.of(effect(-1, 14, 2, charge, 0, 5),
                effect(-1, 14, 2, charge, 0, 3), effect(-1, 14, 2, charge, 1, 5),
                effect(23, 14, 2, charge, 1, 3), effect(-1, 13, 2, charge, 1, 3),
                effect(-1, 14, 7, AbilityVfxGeometry.BEAM, 1, 3), effect(-1, 14, 7, charge, 1, 3),
                effect(-1, 14, 5, charge, 1, 3), effect(-1, 14, 6, charge, 1, 3))) {
            h.assertTrue(!LuoxiaBowPrototype.replacesWorldCharge(cue, 42, true),
                    "Release, other skills, anchored effects, and other themes keep their own world cue: " + cue);
        }
    }

    // Compare the visible line through both limb tips, including the perspective of their depths.
    private static double cant(LuoxiaBowPrototype.Frame frame, Shape shape) {
        var a = frame.bow().transformPosition(new Vector3f(shape.upper));
        var b = frame.bow().transformPosition(new Vector3f(shape.lower));
        return Math.toDegrees(Math.atan2(a.x / -a.z - b.x / -b.z, a.y / -a.z - b.y / -b.z));
    }

    private static void verifyReadyMarker(GameTestHelper h, Shape fullBow) {
        var motion = new LuoxiaBowPrototype();
        motion.accept(cue(7, 1, Phase.DRAW, 8, 1), 1);
        h.assertTrue(motion.sample(2, false, 0, 0, false).readyPulse() == -1,
                "Partial draw hides the full-draw marker");
        motion.accept(cue(7, 1, Phase.DRAW, 11, 3), 3);
        h.assertTrue(motion.sample(3.5, false, 0, 0, false).readyPulse() == -1,
                "The marker waits for the limbs to reach full draw");
        float peak = 0;
        for (int i = 0; i <= 40; i++) {
            float pulse = motion.sample(4.5 + i * .2, false, 0, 0, false).readyPulse();
            h.assertTrue(pulse >= 0 && pulse <= 1, "Full draw keeps the marker visible within its brightness range");
            peak = Math.max(peak, pulse);
        }
        var right = motion.sample(13, false, 0, 0, false);
        var anchor = right.readyAnchor();
        var nock = right.arrow(0).getTranslation(new Vector3f());
        var upper = right.bow().transformPosition(new Vector3f(fullBow.upper));
        var string = upper.sub(nock);
        var offset = new Vector3f(anchor).sub(nock);
        float t = offset.dot(string) / string.lengthSquared();
        h.assertTrue(t > 0 && t < .35f && offset.distance(new Vector3f(string).mul(t)) < .012f,
                "Marker hugs the upper string near the serving");
        h.assertTrue(anchor.z < 0 && Math.hypot(anchor.x, anchor.y) / -anchor.z > .1,
                "The mounted marker clears the centre sightline");
        var mirrored = motion.sample(13, true, 0, 0, false).readyAnchor();
        h.assertTrue(mirrored.distance(-anchor.x, anchor.y, anchor.z) < 1e-4,
                "Left main hand mirrors the marker with the bow");
        h.assertTrue(peak > .25f && right.readyPulse() == 0, "The ready flash settles to the low held marker");
        motion.accept(cue(7, 1, Phase.DRAW, 11, 14), 14);
        for (int i = 0; i <= 6; i++)
            h.assertTrue(motion.sample(14 + i, false, 0, 0, false).readyPulse() == 0,
                    "A full-draw heartbeat cannot restart the flash");
        motion.accept(cue(7, 1, Phase.EARLY, 1, 21), 21);
        h.assertTrue(motion.sample(21, false, 0, 0, false).readyPulse() == -1, "Early let-go hides the marker");
    }

    static void verify(GameTestHelper h) {
        verifyWorldChargeScope(h);
        Shape[] shapes = {readShape(0), readShape(1), readShape(2), readShape(3)};
        for (int i = 0; i < shapes.length; i++) {
            var s = shapes[i];
            h.assertTrue(s.grip.length() < 1e-5f && s.upper.y > 0 && s.lower.y < 0,
                    "The grip is the fixed origin shared by all four authored stages");
            if (i > 0) h.assertTrue(s.upper.distance(s.lower) < shapes[i - 1].upper.distance(shapes[i - 1].lower)
                            && s.serving.x > shapes[i - 1].serving.x,
                    "The limb tips flex inward as the string serving is drawn back");
        }
        var motion = new LuoxiaBowPrototype();
        var idle = motion.sample(0, false, 0, 0, false);
        h.assertTrue(!idle.loaded() && idle.arrows() == 0 && idle.draw() == 0 && idle.readyPulse() == -1,
                "The idle bow carries no arrow or ready marker");
        verifyReadyMarker(h, shapes[3]);
        double idleCant = cant(idle, shapes[0]);
        double leftCant = cant(motion.sample(0, true, 0, 0, false), shapes[0]);
        h.assertTrue(Math.abs(idleCant) > 25 && Math.abs(idleCant + leftCant) < .01,
                "Both idle bows have a visible, geometrically mirrored cant");
        float[] pull = new float[12];
        for (int work = 0; work <= 11; work++) {
            motion.accept(cue(1, 1, Phase.DRAW, work, work), work);
            double at = work + .8;
            var right = motion.sample(at, false, 0, 0, false);
            var left = motion.sample(at, true, 0, 0, false);
            var modelServing = serving(shapes, right.draw(), right.stringOffset());
            var atString = right.bow().transformPosition(new Vector3f(modelServing));
            var atArrow = right.arrow(0).getTranslation(new Vector3f());
            pull[work] = right.serving();
            h.assertTrue(right.loaded() && right.arrows() == 1 && atString.distance(atArrow) < 1e-4f,
                    "The real string serving and arrow tail must coincide at work=" + work);
            var mirrored = left.arrow(0).getTranslation(new Vector3f());
            h.assertTrue(mirrored.distance(-atArrow.x, atArrow.y, atArrow.z) < 1e-4f
                            && right.bow().determinant() > 0 && left.bow().determinant() > 0,
                    "Both main hands must mirror the nocked arrow with positive model winding");
            h.assertTrue(right.bow().equals(motion.sample(at, false, 0, 0, false).bow(), 1e-6f),
                    "Sampling the other hand must not advance a draw or vibration");
        }
        h.assertTrue(pull[3] - pull[0] > pull[11] - pull[8], "Early draw covers more string distance for equal work");
        var full = motion.sample(17, false, 0, 0, false);
        h.assertTrue(Math.abs(cant(full, shapes[3])) > 18, "Full draw retains a visible cant off the central sightline");
        float maxTravel = 0;
        float minTension = Float.POSITIVE_INFINITY, maxTension = Float.NEGATIVE_INFINITY;
        var stringAtRest = full.arrow(0).getTranslation(new Vector3f());
        for (int i = 1; i <= 24; i++) {
            var frame = motion.sample(17 + i / 12.0, false, 0, 0, false);
            maxTravel = Math.max(maxTravel, frame.arrow(0).getTranslation(new Vector3f()).distance(stringAtRest));
            minTension = Math.min(minTension, frame.stringOffset());
            maxTension = Math.max(maxTension, frame.stringOffset());
        }
        h.assertTrue(full.draw() > .99f && maxTravel > 1e-5f && maxTravel < .02f,
                "Full draw holds tension with bounded local movement: " + maxTravel);
        h.assertTrue(minTension < 0 && maxTension > 0, "The held serving vibrates on both sides of its loaded position");

        motion.accept(cue(1, 1, Phase.RELEASE, 11, 20), 20);
        var fired = motion.sample(20.5, false, 0, 0, false);
        h.assertTrue(!fired.loaded() && fired.arrows() == 0 && fired.releaseAge() >= 0 && fired.readyPulse() == -1,
                "Only confirmed release starts the empty-bow rebound");
        h.assertTrue(fired.bow().m32() < full.bow().m32() - .01f && fired.draw() < full.draw(),
                "The grip springs forward as the limbs unload");
        float firstOscillation = motion.sample(21.5, false, 0, 0, false).stringOffset();
        float secondOscillation = motion.sample(22.2, false, 0, 0, false).stringOffset();
        h.assertTrue(firstOscillation * secondOscillation < 0, "Released string crosses the slack position");
        motion.reset();
        motion.accept(cue(2, 1, Phase.DRAW, 3, 30), 30);
        motion.sample(31, false, 0, 0, false);
        motion.accept(cue(2, 1, Phase.CANCEL, 3, 32), 32);
        var cancelled = motion.sample(32.5, false, 0, 0, false);
        h.assertTrue(!cancelled.loaded() && cancelled.arrows() == 0 && cancelled.releaseAge() < 0
                        && cancelled.stringOffset() == 0 && cancelled.readyPulse() == -1,
                "Cancelling a partial draw removes the arrow without a firing impulse");
        motion.reset();
        motion.accept(cue(3, 7, Phase.LOCK, 11, 40), 40);
        var lockedRight = motion.sample(42, false, 0, 0, false);
        var lockedLeft = motion.sample(42, true, 0, 0, false);
        var rightAxis = lockedRight.arrow(0).transformDirection(new Vector3f(-1, 0, 0)).normalize();
        var leftAxis = lockedLeft.arrow(0).transformDirection(new Vector3f(-1, 0, 0)).normalize();
        h.assertTrue(lockedRight.loaded() && lockedLeft.loaded() && lockedRight.readyPulse() == -1
                        && lockedLeft.readyPulse() == -1 && rightAxis.z < 0 && leftAxis.z < 0
                        && rightAxis.x > 0 && leftAxis.x > 0 && lockedRight.target().equals(lockedLeft.target(), 1e-6f)
                        && rightAxis.dot(leftAxis) > .97,
                "Switching main hands keeps both arrows aimed towards the same off-axis lock");
        for (var frame : new LuoxiaBowPrototype.Frame[]{lockedRight, lockedLeft}) {
            var arrow = frame.arrow(0);
            var toTarget = new Vector3f(frame.target()).sub(arrow.getTranslation(new Vector3f())).normalize();
            h.assertTrue(toTarget.dot(arrow.transformDirection(new Vector3f(-1, 0, 0)).normalize()) > .99999f,
                    "The actual arrow mesh axis converges on the world lock from its own serving");
        }
        System.out.println("[MYRIAD-LUOXIA-PROTOTYPE] PASS: real serving and nock, draw stages, mirrored hands, full draw, release/cancel and locked aim");
    }
}
