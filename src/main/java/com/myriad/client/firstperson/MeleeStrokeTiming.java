package com.myriad.client.firstperson;

/** Time in client ticks, anchored to submission of one recovered attack. Pure presentation. */
public final class MeleeStrokeTiming {
    public static final double HIT = .85;
    public static final double END_OF_CUT = 2.35;

    private MeleeStrokeTiming() {}

    public static double recovery(double cadence) {
        return Double.isFinite(cadence) ? Math.clamp(cadence * 1.20 + 3, 6, 72) : 12;
    }

    /** Map authored path coordinates onto input, HIT, end of cut and return to guard. */
    public static double age(double path, double hit, double end, double duration) {
        if (path <= hit) return path / hit * HIT;
        if (path <= end) return HIT + (path - hit) / (end - hit) * (END_OF_CUT - HIT);
        return END_OF_CUT + (path - end) / (1 - end) * (duration - END_OF_CUT);
    }

    public static double path(double age, double hit, double end, double duration) {
        if (age <= HIT) return hit * age / HIT;
        if (age <= END_OF_CUT) return hit + (end - hit) * (age - HIT) / (END_OF_CUT - HIT);
        return end + (1 - end) * (age - END_OF_CUT) / (duration - END_OF_CUT);
    }
}
