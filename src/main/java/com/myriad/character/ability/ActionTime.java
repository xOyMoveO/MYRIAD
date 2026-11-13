package com.myriad.character.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Local action clocks only. Expiry is world time; overlapping sources use the smallest multiplier.
 * Targets may be actors or independent flight IDs. Callers own team/boss/geometry filtering. */
public final class ActionTime {
    private ActionTime() {}
    private record Source(UUID owner, String key) {}
    private record Rate(double multiplier, long until) {}
    private static final Map<UUID, Map<Source, Rate>> RATES = new HashMap<>();

    public static void apply(UUID target, UUID owner, String key, double multiplier, long until) {
        if (target == null || owner == null || key == null || key.isBlank()
                || !Double.isFinite(multiplier) || multiplier <= 0 || multiplier > 1)
            throw new IllegalArgumentException("Action rate requires a named source and 0 < multiplier <= 1");
        RATES.computeIfAbsent(target, ignored -> new HashMap<>()).put(new Source(owner, key), new Rate(multiplier, until));
    }
    public static void remove(UUID target, UUID owner, String key) {
        var rates = RATES.get(target);
        if (rates != null) { rates.remove(new Source(owner, key)); if (rates.isEmpty()) RATES.remove(target); }
    }
    public static double multiplier(UUID target, long now) {
        var rates = RATES.get(target);
        if (rates == null) return 1;
        rates.values().removeIf(rate -> rate.until <= now);
        if (rates.isEmpty()) { RATES.remove(target); return 1; }
        return rates.values().stream().mapToDouble(Rate::multiplier).min().orElse(1);
    }
    public static void clearTarget(UUID target) { RATES.remove(target); }
    /** Lifecycle cleanup removes both received rates and every rate supplied by this actor. */
    public static void clearOwner(UUID owner) {
        RATES.remove(owner);
        RATES.values().forEach(rates -> rates.keySet().removeIf(source -> source.owner.equals(owner)));
        RATES.values().removeIf(Map::isEmpty);
    }
    public static void clearAll() { RATES.clear(); }

    /** Accumulated work is never converted back into a deadline when the rate changes. */
    public static final class Progress {
        private double completed;
        private long lastTick;
        public Progress(long now) { lastTick = now; }
        public double completed() { return completed; }
        public boolean advance(long now, double multiplier, double required) {
            // Do not catch up missed simulation ticks or advance twice in one server tick.
            if (now > lastTick) { completed = Math.min(required, completed + multiplier); lastTick = now; }
            return completed + 1E-9 >= required;
        }
    }
}
