package com.myriad.client.firstperson;

/** Local weapon time, in level ticks. The only confirmation is the owner's BASIC_CONTACT receipt. */
public final class RivetHammerContact {
    static final double HOLD_TICKS = 1.5; // 75 ms at 20 ticks per second
    static final double HOLD_RATE = .10;
    private double start = Double.NEGATIVE_INFINITY;
    private double opens, closes;
    private double previousLatestStamp = Double.NEGATIVE_INFINITY;
    private long newestReceipt = Long.MIN_VALUE;
    private long confirmedTick;
    private double scheduled = Double.NaN;
    private double impact = Double.NaN;
    private double lastSample = Double.NEGATIVE_INFINITY;

    public void start(double now, double opensAt, double closesAt) {
        if (now == start) return;
        cancel();
        start = now;
        opens = opensAt;
        closes = closesAt;
        scheduled = impact = Double.NaN;
        lastSample = now;
    }

    /** Caller checks role, owner and the personal BASIC_CONTACT kind before delivery. */
    public void receive(long serverTick, double now) {
        if (!Double.isFinite(start) || !Double.isNaN(scheduled)) return;
        double received = Math.max(now, lastSample);
        // Allow one tick of clock quantization. Do not replay a stale stamp onto the next swing.
        if (received > closes || serverTick < Math.floor(start) - 1 || serverTick > Math.floor(received) + 1
                || serverTick <= previousLatestStamp || serverTick <= newestReceipt) return;
        confirmedTick = serverTick;
        scheduled = Math.max(opens, received);
        sample(received);
    }

    private void sample(double now) {
        lastSample = Math.max(lastSample, now);
        if (Double.isNaN(impact) && now >= scheduled) beginImpact(scheduled, confirmedTick);
    }

    // The separate acceptance adapter can observe this transition without driving the timeline.
    private void beginImpact(double at, long serverTick) {
        impact = at;
        newestReceipt = serverTick;
    }

    public double animationTime(double now) {
        sample(now);
        if (Double.isNaN(impact) || now <= impact) return now;
        double held = Math.min(now - impact, HOLD_TICKS);
        return now - held * (1 - HOLD_RATE);
    }

    public double impactAge(double now) {
        sample(now);
        double age = now - impact;
        return age >= 0 && age < HOLD_TICKS ? age : -1;
    }

    public double vibration(double now) {
        double age = impactAge(now);
        if (age < 0) return 0;
        double fraction = age / HOLD_TICKS;
        return Math.sin(4 * Math.PI * fraction) * (1 - fraction) * (1 - fraction);
    }

    /** Interrupt the weapon in the same world. Keep stale stamps fenced off the next swing. */
    public void cancel() {
        // The packet has no swing sequence. Stamps shared by adjacent swing windows are ambiguous.
        if (Double.isFinite(start)) previousLatestStamp = Math.max(previousLatestStamp, Math.floor(closes) + 1);
        start = Double.NEGATIVE_INFINITY;
        scheduled = impact = Double.NaN;
    }

    public void reset() {
        start = previousLatestStamp = lastSample = Double.NEGATIVE_INFINITY;
        newestReceipt = Long.MIN_VALUE;
        scheduled = impact = Double.NaN;
    }
}
