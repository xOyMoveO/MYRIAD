package com.myriad.combat;

/** One press, one attempt; the original predicted ready tick is the hard expiry. */
public final class MeleeInputWindow {
    private long deadline = Long.MIN_VALUE;
    public boolean pending() { return deadline != Long.MIN_VALUE; }

    public boolean offer(long now, int remaining, int window) {
        if (pending() || remaining <= 0 || remaining > window) return false;
        deadline = now + remaining;
        return true;
    }

    public boolean consume(long now, boolean ready) {
        if (!pending()) return false;
        boolean fire = now <= deadline && ready;
        if (fire || now >= deadline) clear();
        return fire;
    }

    public void clear() { deadline = Long.MIN_VALUE; }
}
