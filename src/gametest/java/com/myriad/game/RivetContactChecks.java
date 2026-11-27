package com.myriad.game;

import com.myriad.client.firstperson.RivetHammerContact;

/** Pure timing checks; callable from the Rivet GameTest and a standalone JVM. */
final class RivetContactChecks {
    private static void equal(String step, double actual, double expected) {
        if (!(Math.abs(actual - expected) < 1e-8))
            throw new AssertionError(step + ": expected " + expected + ", got " + actual);
    }

    private static RivetHammerContact swing() {
        var hammer = new RivetHammerContact();
        hammer.start(100, 104.1, 106);
        return hammer;
    }

    static void verify() {
        var miss = swing();
        equal("whiff follows the whole swing", miss.animationTime(108), 108);
        equal("whiff never shakes the head", miss.vibration(104.4), 0);

        var early = swing();
        early.receive(100, 100.1);
        early.start(100, 104.1, 106); // A repeated callback for the same start preserves its receipt.
        equal("early receipt queued", early.impactAge(104), -1);
        equal("entry", early.impactAge(104.1), 0);
        early.receive(100, 104.4);
        early.receive(101, 104.6);
        equal("duplicates preserve first impact", early.impactAge(104.75), .65);
        equal("75 ms at ten percent speed", early.animationTime(105.6), 104.25);
        equal("ordinary speed resumes", early.animationTime(107), 105.65);
        equal("vibration stops", early.vibration(105.6), 0);

        var late = swing();
        equal("prior frame", late.animationTime(105.25), 105.25);
        late.receive(101, 105); // Integer tick callback after a fractional render sample.
        equal("late confirmation does not rewind the rendered pose", late.animationTime(105.25), 105.25);
        equal("late confirmation starts once", late.animationTime(106.25), 105.35);

        var after = swing();
        after.receive(101, 106.01);
        equal("receipt after window", after.animationTime(107), 107);
        var stale = swing();
        stale.receive(98, 100);
        equal("stamp before eligible press", stale.impactAge(104.5), -1);
        stale.receive(107, 104.6);
        equal("stamp from future", stale.impactAge(105), -1);

        var overlap = swing();
        overlap.receive(100, 101);
        overlap.start(103, 107, 109);
        overlap.receive(100, 103.3);
        overlap.receive(103, 104); // Both actions could have caused this stamp.
        equal("old and ambiguous receipts never transfer", overlap.impactAge(107.25), -1);
        overlap.receive(108, 108);
        equal("unambiguous stamp in new swing", overlap.impactAge(108.25), .25);
        var interrupted = swing();
        interrupted.receive(100, 101);
        interrupted.cancel();
        interrupted.start(102, 105, 106);
        interrupted.receive(103, 103);
        equal("interrupted swing cannot be reassigned", interrupted.impactAge(105), -1);
        overlap.reset();
        overlap.receive(108, 109);
        equal("reset discards pending or active recoil", overlap.impactAge(109), -1);

        for (int fps : new int[] {30, 60, 144}) {
            var cadence = swing();
            cadence.receive(101, 101);
            double dt = 20.0 / fps;
            for (double t = 101; t < 108; t += dt) {
                double workLost = .9 * Math.min(1.5, Math.max(0, t - 104.1));
                equal("weapon time at " + fps + " FPS", cadence.animationTime(t), t - workLost);
                equal("same-frame secondary sample", cadence.animationTime(t), t - workLost);
                if (Math.abs(cadence.vibration(t)) > 1)
                    throw new AssertionError("unbounded hammer vibration");
            }
        }
        var held = swing();
        held.receive(100, 100);
        if (Math.abs(held.vibration(104.2875)) < .7)
            throw new AssertionError("confirmed contact should produce a decaying head vibration");
    }

    public static void main(String[] args) {
        verify();
        System.out.println("Rivet contact boundary and cadence checks passed.");
    }
}
